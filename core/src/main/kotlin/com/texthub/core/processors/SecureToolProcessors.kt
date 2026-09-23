package com.texthub.core.processors

import com.texthub.core.TextProcessor
import com.texthub.core.crypto.AesCbcHmac
import com.texthub.core.crypto.AesCtrHmac
import com.texthub.core.crypto.PayloadKeySize
import com.texthub.core.crypto.AesGcmPayload
import com.texthub.core.crypto.JweFormat
import com.texthub.core.crypto.OpenSslEnc
import com.texthub.core.crypto.RawKeyGcm
import com.texthub.core.crypto.RsaPem
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
import com.texthub.core.detector.jweLabelFor
import com.texthub.core.detector.pemLabelFor
import com.texthub.core.model.DetectionHint

/**
 * AES-CTR with Encrypt-then-MAC. A stream-cipher mode that is common in older protocols; the
 * counter block is random per message and the result is authenticated with a separate HMAC key.
 */
class AesCtrProcessor : TextProcessor, PayloadKeySize {

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
                helper = "The MAC key is always 256 bits and never derived from the AES key. The " +
                    "key size is recorded in the message and must match on decrypt.",
            ),
            passwordSpec("Use AES-GCM unless a system you talk to expects CTR."),
        ),
        detection = listOf(
            DetectionHint(
                label = "Text Hub AES-CTR + HMAC payload",
                recognise = { text -> AesCtrHmac.looksLikeEnvelope(text) },
                evidence = "The Base64 decodes to the Text Hub envelope: version byte 0x04, salt, " +
                    "counter block and a 32-byte HMAC tag.",
                structural = true,
            ),
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
                AesCtrHmac.encrypt(
                    plaintext = input,
                    password = password,
                    keySizeBytes = keySizeOf(params),
                    prefixKeySizeInPayload = true,
                )
            } else {
                val trimmed = input.trim()
                if (OpenSslEnc.looksLikeSalted(trimmed)) throw Errors.opensslPointsHere()
                if (JweFormat.looksLikeJwe(trimmed)) {
                    throw Errors.jwePointsHere("AES-GCM with your own key (for alg=dir tokens) or RSA-OAEP + AES-GCM (for RSA tokens)")
                }
                AesCtrHmac.decrypt(
                    payloadBase64 = trimmed,
                    password = password,
                    expectedKeySizeBytes = keySizeOf(params),
                )
            }
        } finally {
            password.fill('\u0000')
        }
    }


    private fun keySizeOf(params: Map<String, String>): Int =
        (params["keySize"] ?: "256").toIntOrNull()?.div(8) ?: 32

    /** Answered by the CTR envelope reader itself; see [PayloadKeySize]. */
    override fun keySizeOf(payloadBase64: String, password: CharArray): Int? =
        AesCtrHmac.keySizeOf(payloadBase64, password)
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
                required = true,
                hint = "16, 24 or 32 bytes as Base64 or hex",
                sensitive = true,
                helper = "Paste a raw key produced elsewhere. Nothing about it is stored or logged.",
                validator = { value -> if (value.isBlank()) null else runCatching { RawKeyGcm.parseKey(value) }.exceptionOrNull()?.message },
            ),
            ParamSpec(
                key = "format",
                label = "Format",
                kind = ParamKind.CHOICE,
                defaultValue = "auto",
                choices = listOf(
                    Choice("auto", "Detect automatically"),
                    Choice("texthub", "Text Hub (raw-key GCM)"),
                    Choice("jwe", "JWE compact (JSON Web Encryption)"),
                ),
                helper = "JWE tokens have five dot-separated parts and name their own algorithm, so " +
                    "they are recognised without guessing. Text Hub payloads start with a 0x05 byte.",
            ),
            ParamSpec(
                key = "jweEnc",
                label = "JWE content encryption",
                kind = ParamKind.CHOICE,
                defaultValue = "A256GCM",
                advanced = true,
                choices = listOf(
                    Choice("A256GCM", "A256GCM (AES-256-GCM)"),
                    Choice("A192GCM", "A192GCM (AES-192-GCM)"),
                    Choice("A128GCM", "A128GCM (AES-128-GCM)"),
                    Choice("A128CBC-HS256", "A128CBC-HS256 (AES-CBC + HMAC-SHA256)"),
                    Choice("A192CBC-HS384", "A192CBC-HS384 (AES-CBC + HMAC-SHA384)"),
                    Choice("A256CBC-HS512", "A256CBC-HS512 (AES-CBC + HMAC-SHA512)"),
                ),
                helper = "Only used when Text Hub writes a JWE token. When reading one, the token's own " +
                    "enc header decides, and the key length has to match it.",
            ),
        ),
        detection = listOf(
            DetectionHint(
                label = "Text Hub AES-GCM payload written with a raw key",
                recognise = { text -> RawKeyGcm.looksLikeEnvelope(text) },
                evidence = "The Base64 decodes to version byte 0x05 and records an AES key length.",
                structural = true,
                paramsFor = { text ->
                    RawKeyGcm.keyLengthOf(text)?.let { mapOf("keySize" to (it * 8).toString()) } ?: emptyMap()
                },
            ),
            DetectionHint(
                labelFor = { text -> jweLabelFor(text) ?: "JWE compact token (alg=dir)" },
                recognise = { text -> JweFormat.header(text)?.alg == "dir" },
                evidenceFor = { text ->
                    val header = JweFormat.header(text)
                    "Five dot-separated Base64URL parts whose protected header names " +
                        "alg=${header?.alg} and enc=${header?.enc}. The content key is not wrapped, so " +
                        "the key you paste is the key itself."
                },
                structural = true,
                paramsFor = { text ->
                    val header = JweFormat.header(text)
                    buildMap {
                        put("format", "jwe")
                        header?.enc?.let { put("jweEnc", it) }
                    }
                },
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
        val format = params["format"] ?: "auto"
        return if (direction == Direction.ENCODE) {
            if (input.isEmpty()) throw Errors.emptyInput()
            if (format == "jwe") {
                JweFormat.encrypt(input, RawKeyGcm.parseKey(key), jweEnc(params))
            } else {
                RawKeyGcm.encrypt(input, key)
            }
        } else {
            val trimmed = input.trim()
            val isJwe = when (format) {
                "jwe" -> true
                "texthub" -> false
                else -> JweFormat.looksLikeJwe(trimmed)
            }
            if (isJwe) {
                // The token's own header decides enc, IV and tag length; the key must match it.
                JweFormat.decrypt(trimmed, RawKeyGcm.parseKey(key))
            } else {
                RawKeyGcm.decrypt(trimmed, key)
            }
        }
    }

    private fun jweEnc(params: Map<String, String>): JweFormat.Enc {
        val id = params["jweEnc"] ?: "A256GCM"
        return JweFormat.Enc.values().firstOrNull { it.id == id } ?: JweFormat.Enc.A256GCM
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
                required = true,
                hint = "Paste a public key to encrypt, or your private key to decrypt",
                sensitive = true,
                helper = "Accepts the -----BEGIN PUBLIC KEY----- and -----BEGIN PRIVATE KEY----- blocks.",
                validator = { value ->
                    when {
                        value.isBlank() -> null
                        value.contains("-----BEGIN PRIVATE KEY-----") -> runCatching { RsaPem.privateKey(value) }.exceptionOrNull()?.message
                        value.contains("-----BEGIN PUBLIC KEY-----") -> runCatching { RsaPem.publicKey(value) }.exceptionOrNull()?.message
                        else -> "Paste a complete PEM block, including the BEGIN and END lines."
                    }
                },
            ),
            ParamSpec(
                key = "format",
                label = "Format",
                kind = ParamKind.CHOICE,
                defaultValue = "auto",
                choices = listOf(
                    Choice("auto", "Detect automatically"),
                    Choice("texthub", "Text Hub (RSA-OAEP + AES-GCM)"),
                    Choice("jwe", "JWE compact (JSON Web Encryption)"),
                ),
                helper = "A JWE token names its own algorithm in the header (alg=RSA-OAEP or " +
                    "RSA-OAEP-256), so a token encrypted for your key opens here. Text Hub payloads " +
                    "start with a version byte.",
            ),
        ),
        detection = listOf(
            DetectionHint(
                labelFor = { text -> pemLabelFor(text) },
                recognise = { text -> RsaPem.inspect(text).let { it.hasPublic || it.hasPrivate } },
                evidence = "The text contains a complete PEM block, which is key material rather than " +
                    "ciphertext.",
                structural = true,
                runnable = false,
                unrunnableNote = "That is key material, not data to decode. Paste it into " +
                    "RSA-OAEP + AES-GCM's Key field together with the ciphertext you want to open.",
            ),
            DetectionHint(
                label = "Text Hub RSA-OAEP + AES-GCM payload",
                recognise = { text -> RsaHybrid.looksLikeEnvelope(text) },
                evidence = "The Base64 decodes to version byte 0x11, an RSA-wrapped AES key and a GCM block.",
                structural = true,
            ),
            DetectionHint(
                labelFor = { text -> jweLabelFor(text) ?: "JWE compact token (RSA-OAEP)" },
                recognise = { text ->
                    JweFormat.header(text)?.alg?.let { it == "RSA-OAEP" || it == "RSA-OAEP-256" } == true
                },
                evidenceFor = { text ->
                    val header = JweFormat.header(text)
                    "Five dot-separated Base64URL parts whose protected header names " +
                        "alg=${header?.alg} and enc=${header?.enc}. The content key is wrapped with RSA, " +
                        "so the matching private key opens it."
                },
                structural = true,
                paramsFor = { text ->
                    val header = JweFormat.header(text)
                    buildMap {
                        put("format", "jwe")
                        header?.enc?.let { put("jweEnc", it) }
                    }
                },
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
            val trimmed = input.trim()
            val isJwe = when (params["format"] ?: "auto") {
                "jwe" -> true
                "texthub" -> false
                else -> JweFormat.looksLikeJwe(trimmed)
            }
            if (isJwe) {
                // The token carries its own enc; the content key is unwrapped with this RSA key.
                JweFormat.decryptWithUnwrap(trimmed) { encryptedKey, alg ->
                    RsaHybrid.unwrapJweCek(encryptedKey, alg, key)
                }
            } else {
                RsaHybrid.decrypt(trimmed, key)
            }
        }
    }

}

/**
 * Generates an RSA key pair on the device. Nothing is uploaded and nothing is stored: the PEM
 * blocks appear in the result box and are gone when the app is closed unless you save them.
 */
class RsaKeyGenProcessor : TextProcessor {

    companion object {
        /** The registered tool id, shared with the UI that renders the key sections. */
        const val TOOL_ID = "rsakeygen"
    }

    override val meta = ToolMeta(
        id = TOOL_ID,
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
        oneWay = true,
        // Key material is created only when the user presses Generate: opening the tool, changing
        // the key size or any other state change must never silently mint (or replace) a pair.
        explicitActionOnly = true,
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
    required = true,
    hint = "Enter a strong password",
    sensitive = true,
    helper = "$helper Use a long, unique password: it cannot be recovered if it is lost.",
)
