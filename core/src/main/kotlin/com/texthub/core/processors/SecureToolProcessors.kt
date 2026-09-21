package com.texthub.core.processors

import com.texthub.core.TextProcessor
import com.texthub.core.crypto.AesCbcHmac
import com.texthub.core.crypto.AesCtrHmac
import com.texthub.core.crypto.AesGcmPayload
import com.texthub.core.crypto.RawKeyGcm
import com.texthub.core.crypto.RsaHybrid
import com.texthub.core.crypto.RsaKeyGen
import com.texthub.core.model.Choice
import com.texthub.core.model.Classification
import com.texthub.core.model.Direction
import com.texthub.core.model.Errors
import com.texthub.core.model.ParamKind
import com.texthub.core.model.ParamSpec
import com.texthub.core.model.ToolCategory
import com.texthub.core.model.ToolInfo
import com.texthub.core.model.ToolMeta

/**
 * AES-CTR with Encrypt-then-MAC. A stream-cipher mode that is common in older protocols; the
 * counter block is random per message and the result is authenticated with a separate HMAC key.
 */
class AesCtrProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "aesctr",
        name = "AES-CTR + HMAC",
        glyph = "CTR",
        category = ToolCategory.SECURE,
        classification = Classification.AUTHENTICATED_ENCRYPTION,
        encodeLabel = "Encrypt",
        decodeLabel = "Decrypt",
        params = listOf(
            ParamSpec(
                key = "keySize",
                label = "AES key size",
                kind = ParamKind.CHOICE,
                defaultValue = "256",
                choices = listOf(
                    Choice("256", "AES-256 (recommended)"),
                    Choice("192", "AES-192"),
                    Choice("128", "AES-128"),
                ),
                helper = "The MAC key is always 256 bits and never derived from the AES key.",
            ),
            passwordSpec("Use AES-GCM unless a system you talk to expects CTR."),
        ),
        info = ToolInfo(
            summary = "AES in CTR mode with a fresh random counter block per message and an " +
                "HMAC-SHA256 tag over the whole message (Encrypt-then-MAC). CTR alone provides " +
                "no integrity and repeats dangerously if the counter block is ever reused, so " +
                "both protections are part of this tool rather than options.",
            requiresKey = true,
            useCases = listOf(
                "Interoperating with a protocol that specifies AES-CTR",
                "Encrypting data that must not change length (CTR adds no padding)",
            ),
            warnings = listOf(
                "AES-GCM is the better default; CTR is offered for compatibility.",
                "Losing the password makes the data unrecoverable.",
            ),
            convention = "Payload: version + KDF id + salt + counter block + ciphertext + HMAC tag, " +
                "Base64 encoded. The counter block is random and never reused with the same key.",
        ),
        keywords = listOf("aes", "ctr", "counter", "hmac", "stream", "encrypt", "authenticated"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val password = (params["password"] ?: "").toCharArray()
        if (password.isEmpty()) throw Errors.missingPassword()
        return try {
            if (direction == Direction.ENCODE) {
                if (input.isEmpty()) throw Errors.emptyInput()
                AesCtrHmac.encrypt(input, password, keySizeBytes = keySizeOf(params))
            } else {
                AesCtrHmac.decrypt(input.trim(), password)
            }
        } finally {
            password.fill('\u0000')
        }
    }


    private fun keySizeOf(params: Map<String, String>): Int =
        (params["keySize"] ?: "256").toIntOrNull()?.div(8) ?: 32
}

/**
 * AES-GCM with a key you supply instead of a password: no key derivation, no salt, the key is
 * used exactly as pasted. For keys that come from somewhere else.
 */
class AesRawKeyProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "aesrawkey",
        name = "AES-GCM with your own key",
        glyph = "KEY",
        category = ToolCategory.SECURE,
        classification = Classification.AUTHENTICATED_ENCRYPTION,
        encodeLabel = "Encrypt",
        decodeLabel = "Decrypt",
        params = listOf(
            ParamSpec(
                key = "key",
                label = "AES key",
                kind = ParamKind.TEXT,
                defaultValue = "",
                hint = "16, 24 or 32 bytes as Base64 or hex",
                sensitive = true,
                helper = "Paste a raw key produced elsewhere. Nothing about it is stored or logged.",
            ),
        ),
        info = ToolInfo(
            summary = "Encrypts with a key you already have - no password, no key derivation. " +
                "The key must be 16, 24 or 32 bytes written as Base64 or hex, and it is used " +
                "exactly as pasted with AES-GCM, so every message is still authenticated.",
            requiresKey = true,
            useCases = listOf(
                "Using a key from a password manager or a key file",
                "Decrypting data produced by another tool that uses a raw AES key",
            ),
            warnings = listOf(
                "There is no key stretching here: a weak or short key is not made stronger.",
                "Anyone who has the key can decrypt the message - keep it out of your clipboard history.",
            ),
            convention = "Payload: version + key length + IV + ciphertext with GCM tag, Base64 " +
                "encoded. The key length is stored, the key itself never is.",
        ),
        keywords = listOf("aes", "gcm", "raw key", "key", "encrypt", "authenticated", "byok"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val key = params["key"] ?: ""
        if (key.isBlank()) throw Errors.missingKey()
        return if (direction == Direction.ENCODE) {
            if (input.isEmpty()) throw Errors.emptyInput()
            RawKeyGcm.encrypt(input, key)
        } else {
            RawKeyGcm.decrypt(input.trim(), key)
        }
    }

}

/**
 * Hybrid public-key encryption: RSA-OAEP (SHA-256) wraps a random AES-256 key and AES-256-GCM
 * encrypts the message, so any length of text can be handled and the result is authenticated.
 */
class RsaProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "rsa",
        name = "RSA-OAEP + AES-GCM",
        glyph = "RSA",
        category = ToolCategory.SECURE,
        classification = Classification.AUTHENTICATED_ENCRYPTION,
        encodeLabel = "Encrypt",
        decodeLabel = "Decrypt",
        params = listOf(
            ParamSpec(
                key = "key",
                label = "RSA key (PEM)",
                kind = ParamKind.MULTILINE,
                defaultValue = "",
                hint = "Paste a public key to encrypt, or your private key to decrypt",
                sensitive = true,
                helper = "Accepts the -----BEGIN PUBLIC KEY----- and -----BEGIN PRIVATE KEY----- blocks.",
            ),
        ),
        info = ToolInfo(
            summary = "Public-key encryption: anyone with the public key can encrypt a message, " +
                "but only the matching private key can read it. A fresh AES-256 key is wrapped " +
                "with RSA-OAEP and the text itself is encrypted with AES-256-GCM, so any length " +
                "of text works and tampering is detected.",
            requiresKey = true,
            useCases = listOf(
                "Sending something to a person or server whose public key you have",
                "Storing text that only your private key can open",
            ),
            warnings = listOf(
                "RSA cannot encrypt more than a short block on its own - that is why this tool " +
                    "uses hybrid encryption, the same construction as TLS and PGP.",
                "Never share a private key. A lost private key cannot be replaced: messages " +
                    "encrypted to it become unreadable.",
            ),
            convention = "Payload: version + algorithm id + RSA-wrapped AES key + IV + " +
                "ciphertext with GCM tag, Base64 encoded. OAEP uses SHA-256 with MGF1 as " +
                "provided by the platform.",
        ),
        keywords = listOf("rsa", "oaep", "public key", "private key", "pem", "hybrid", "encrypt", "asymmetric"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        if (!RsaHybrid.isAvailable()) throw Errors.rsaUnsupported()
        val key = params["key"] ?: ""
        if (key.isBlank()) {
            throw if (direction == Direction.ENCODE) Errors.rsaNeedPublic() else Errors.rsaNeedPrivate()
        }
        return if (direction == Direction.ENCODE) {
            if (input.isEmpty()) throw Errors.emptyInput()
            RsaHybrid.encrypt(input, key)
        } else {
            RsaHybrid.decrypt(input.trim(), key)
        }
    }

}

/**
 * Generates an RSA key pair on the device. Nothing is uploaded and nothing is stored: the PEM
 * blocks appear in the result box and are gone when the app is closed unless you save them.
 */
class RsaKeyGenProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "rsakeygen",
        name = "RSA Key Pair Generator",
        glyph = "GEN",
        category = ToolCategory.SECURE,
        classification = Classification.KEY_MATERIAL,
        encodeLabel = "Generate",
        decodeLabel = "Generate",
        symmetric = true,
        inputOptional = true,
        params = listOf(
            ParamSpec(
                key = "size",
                label = "Key size",
                kind = ParamKind.CHOICE,
                defaultValue = "2048",
                choices = listOf(
                    Choice("2048", "2048-bit (fast, widely accepted)"),
                    Choice("3072", "3072-bit"),
                    Choice("4096", "4096-bit (slowest)"),
                ),
                helper = "Bigger keys are slower to generate and use; 2048 bits is still the common choice.",
            ),
        ),
        info = ToolInfo(
            summary = "Creates a new RSA key pair with the platform's own generator and prints " +
                "both halves as PEM blocks, together with a fingerprint of the public key. No " +
                "network, no storage: copy the text somewhere safe before leaving the screen.",
            requiresKey = false,
            useCases = listOf(
                "Making a key pair to use with the RSA-OAEP tool",
                "Checking that the fingerprint of an existing pair matches what a contact sent",
            ),
            warnings = listOf(
                "A private key is as sensitive as a password. Never paste it into a chat or a web page.",
                "This tool generates key material, it is not encryption itself.",
                "Generating 4096-bit keys can take several seconds on a phone.",
            ),
            convention = "Public key in X.509 (-----BEGIN PUBLIC KEY-----) and private key in " +
                "PKCS#8 (-----BEGIN PRIVATE KEY-----), 64 characters per line.",
        ),
        keywords = listOf("rsa", "key pair", "generate", "pem", "public key", "private key", "keygen"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val bits = (params["size"] ?: "2048").toIntOrNull() ?: 2048
        return RsaKeyGen.generate(bits)
    }

}

/** Shared password field definition. */
private fun passwordSpec(helper: String) = ParamSpec(
    key = "password",
    label = "Password",
    kind = ParamKind.PASSWORD,
    defaultValue = "",
    hint = "Enter a strong password",
    sensitive = true,
    helper = "$helper Use a long, unique password: it cannot be recovered if it is lost.",
)
