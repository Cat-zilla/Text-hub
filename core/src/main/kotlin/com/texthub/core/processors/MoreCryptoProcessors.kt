package com.texthub.core.processors

import com.texthub.core.TextProcessor
import com.texthub.core.crypto.AesCbcHmac
import com.texthub.core.crypto.JweFormat
import com.texthub.core.crypto.OpenSslEnc
import com.texthub.core.crypto.ChaCha20Poly1305
import com.texthub.core.model.Choice
import com.texthub.core.model.Classification
import com.texthub.core.model.Direction
import com.texthub.core.model.Errors
import com.texthub.core.model.ParamKind
import com.texthub.core.model.ParamSpec
import com.texthub.core.model.ToolCategory
import com.texthub.core.model.ToolException
import com.texthub.core.model.ToolInfo
import com.texthub.core.model.ToolMeta

/**
 * AES-256-CBC with HMAC-SHA256 authentication (Encrypt-then-MAC).
 * Offered for interoperability with systems that expect CBC rather than GCM.
 */
class AesCbcProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "aescbc",
        name = "AES-CBC + HMAC",
        glyph = "CBC",
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
                helper = "The MAC key is always 256 bits and is never derived from the AES key. " +
                    "The key size is recorded in the message and must match on decrypt.",
            ),
            ParamSpec(
                key = "password",
                label = "Password",
                kind = ParamKind.PASSWORD,
                required = true,
                defaultValue = "",
                hint = "Enter a strong password",
                sensitive = true,
                helper = "Same rules as AES-GCM: the password is never stored and cannot be recovered.",
            ),
            ParamSpec(
                key = "format",
                label = "Format",
                kind = ParamKind.CHOICE,
                defaultValue = "auto",
                choices = listOf(
                    Choice("auto", "Detect automatically"),
                    Choice("texthub", "Text Hub (AES-CBC + HMAC)"),
                    Choice("openssl", "OpenSSL enc"),
                ),
                helper = "Text Hub payloads are authenticated with HMAC. OpenSSL enc files (they start " +
                    "with Salted__ / U2FsdGVkX1) are AES-CBC with PKCS#7 padding and no MAC. " +
                    "Detection is structural: the two formats cannot be confused.",
            ),
            ParamSpec(
                key = "opensslKdf",
                label = "Key derivation",
                kind = ParamKind.CHOICE,
                defaultValue = "pbkdf2",
                advanced = true,
                choices = listOf(
                    Choice("pbkdf2", "PBKDF2 (OpenSSL 3.x)"),
                    Choice("legacy", "Legacy EVP_BytesToKey (OpenSSL 1.x)"),
                ),
                helper = "OpenSSL enc files only. OpenSSL 3.x derives the key with PBKDF2; OpenSSL " +
                    "1.1 and earlier used the historic EVP_BytesToKey chain.",
            ),
            ParamSpec(
                key = "opensslDigest",
                label = "OpenSSL digest",
                kind = ParamKind.CHOICE,
                defaultValue = "sha256",
                advanced = true,
                choices = listOf(
                    Choice("sha256", "SHA-256 (OpenSSL 3 default)"),
                    Choice("sha512", "SHA-512"),
                    Choice("sha1", "SHA-1"),
                    Choice("md5", "MD5 (old default)"),
                ),
                helper = "The -md value the file was written with. It is not stored in the file, so it " +
                    "has to be chosen: SHA-256 for OpenSSL 3.x, MD5 for older files.",
            ),
            ParamSpec(
                key = "opensslIterations",
                label = "OpenSSL iterations",
                kind = ParamKind.NUMBER,
                defaultValue = "10000",
                min = 1,
                max = 10_000_000,
                advanced = true,
                helper = "The -iter value (10000 is the OpenSSL 3 default). Ignored by the legacy " +
                    "derivation, which is always a single pass.",
            ),
        ),
        info = ToolInfo(
            summary = "AES in CBC mode (128, 192 or 256-bit key) with a separate HMAC-SHA256 tag (Encrypt-then-MAC). The " +
                "PBKDF2 output is split into an encryption key and a MAC key, and the tag is " +
                "verified before the data is decrypted.",
            requiresKey = true,
            useCases = listOf(
                "Exchanging data with systems that require CBC mode",
                "A second authenticated option alongside AES-GCM",
            ),
            warnings = listOf(
                "Use AES-GCM unless you specifically need CBC compatibility.",
                "Losing the password makes the data unrecoverable - there is no backdoor.",
            ),
            convention = "Payload: version + KDF id + salt + IV + ciphertext + HMAC tag, Base64 " +
                "encoded. The KDF id records the key size, so decryption knows which key to build.",
        ),
        keywords = listOf("aes", "cbc", "hmac", "encrypt", "authenticated"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val password = (params["password"] ?: "").toCharArray()
        if (password.isEmpty()) throw Errors.missingPassword()
        try {
            val format = params["format"] ?: "auto"
            val openssl = OpenSslOptions.of(params)
            return if (direction == Direction.ENCODE) {
                if (input.isEmpty()) throw Errors.emptyInput()
                if (format == "openssl") {
                    OpenSslEnc.encrypt(input, password, keySizeOf(params), openssl.kdf, openssl.digest, openssl.iterations)
                } else {
                    AesCbcHmac.encrypt(
                        plaintext = input,
                        password = password,
                        keySizeBytes = keySizeOf(params),
                        prefixKeySizeInPayload = true,
                    )
                }
            } else {
                val trimmed = input.trim()
                val isOpenSsl = when (format) {
                    "openssl" -> true
                    "texthub" -> false
                    else -> OpenSslEnc.looksLikeSalted(trimmed)
                }
                when {
                    isOpenSsl -> OpenSslEnc.decrypt(
                        payload = trimmed,
                        password = password,
                        keySizeBytes = keySizeOf(params),
                        kdf = openssl.kdf,
                        digest = openssl.digest,
                        iterations = openssl.iterations,
                    )
                    JweFormat.looksLikeJwe(trimmed) ->
                        throw Errors.jwePointsHere("AES-GCM with your own key (for alg=dir tokens) or RSA-OAEP + AES-GCM (for RSA tokens)")
                    else -> AesCbcHmac.decrypt(
                        payloadBase64 = trimmed,
                        password = password,
                        expectedKeySizeBytes = keySizeOf(params),
                    )
                }
            }
        } finally {
            password.fill('\u0000')
        }
    }

    private fun keySizeOf(params: Map<String, String>): Int =
        (params["keySize"] ?: "256").toIntOrNull()?.div(8) ?: 32
}

/** The OpenSSL `enc` settings, read from an AES tool's parameters. */
internal data class OpenSslOptions(
    val kdf: OpenSslEnc.Kdf,
    val digest: OpenSslEnc.Digest,
    val iterations: Int,
) {
    companion object {
        fun of(params: Map<String, String>): OpenSslOptions = OpenSslOptions(
            kdf = if ((params["opensslKdf"] ?: "pbkdf2") == "legacy") OpenSslEnc.Kdf.LEGACY else OpenSslEnc.Kdf.PBKDF2,
            digest = OpenSslEnc.Digest.values().firstOrNull { it.id == (params["opensslDigest"] ?: "sha256") }
                ?: OpenSslEnc.Digest.SHA256,
            iterations = (params["opensslIterations"] ?: "").toIntOrNull()?.coerceAtLeast(1) ?: 10_000,
        )
    }
}

/** ChaCha20-Poly1305 (RFC 8439), the modern stream cipher alternative to AES. */
class ChaChaProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "chacha",
        name = "ChaCha20-Poly1305",
        glyph = "CHA",
        category = ToolCategory.SECURE,
        classification = Classification.AUTHENTICATED_ENCRYPTION,
        encodeLabel = "Encrypt",
        decodeLabel = "Decrypt",
        params = listOf(
            ParamSpec(
                key = "password",
                label = "Password",
                kind = ParamKind.PASSWORD,
                required = true,
                defaultValue = "",
                hint = "Enter a strong password",
                sensitive = true,
                helper = "Key derivation: PBKDF2-HMAC-SHA256, 210,000 iterations, random salt.",
            ),
        ),
        info = ToolInfo(
            summary = "ChaCha20-Poly1305 is an authenticated stream cipher used by TLS 1.3 and " +
                "WireGuard. It is a good choice on hardware without fast AES instructions.",
            requiresKey = true,
            useCases = listOf(
                "Authenticated encryption on devices without AES acceleration",
                "Interoperating with TLS 1.3 style payloads",
            ),
            warnings = listOf(
                "Needs Android 9 or newer (the platform provider supplies the cipher). On older " +
                    "versions the app will tell you to use AES-GCM instead of weakening anything.",
                "There is no recovery if the password is lost.",
            ),
            convention = "Payload: version + KDF id + salt + nonce + ciphertext (with Poly1305 tag), Base64 encoded.",
        ),
        keywords = listOf("chacha20", "poly1305", "stream cipher", "tls", "encrypt"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val password = (params["password"] ?: "").toCharArray()
        if (password.isEmpty()) throw Errors.missingPassword()
        try {
            if (!ChaCha20Poly1305.isAvailable()) {
                throw ToolException(
                    "ChaCha20-Poly1305 is not available on this Android version. Use AES-GCM, " +
                        "which is supported on every device Text Hub runs on."
                )
            }
            return if (direction == Direction.ENCODE) {
                if (input.isEmpty()) throw Errors.emptyInput()
                ChaCha20Poly1305.encrypt(input, password)
            } else {
                val trimmed = input.trim()
                if (OpenSslEnc.looksLikeSalted(trimmed)) throw Errors.opensslPointsHere()
                if (JweFormat.looksLikeJwe(trimmed)) {
                    throw Errors.jwePointsHere("AES-GCM with your own key (for alg=dir tokens) or RSA-OAEP + AES-GCM (for RSA tokens)")
                }
                ChaCha20Poly1305.decrypt(trimmed, password)
            }
        } catch (e: ChaCha20Poly1305.Unsupported) {
            throw ToolException(
                "ChaCha20-Poly1305 is not available on this Android version. Use AES-GCM instead."
            )
        } finally {
            password.fill('\u0000')
        }
    }
}
