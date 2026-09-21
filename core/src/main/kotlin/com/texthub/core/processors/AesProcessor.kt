package com.texthub.core.processors

import com.texthub.core.TextProcessor
import com.texthub.core.crypto.AesGcmPayload
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
 * Modern authenticated encryption: AES-GCM (128, 192 or 256-bit key) with a key derived from the
 * user's password using PBKDF2-HMAC-SHA256 (210 000 iterations) and a fresh random salt + nonce
 * per message.
 */
class AesProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "aes",
        name = "AES-GCM Encryption",
        glyph = "AES",
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
                helper = "All three are authenticated; the key size is remembered in the payload " +
                    "implicitly, so any of them can be decrypted later.",
            ),
            ParamSpec(
                key = "password",
                label = "Password",
                kind = ParamKind.PASSWORD,
                defaultValue = "",
                hint = "Enter a strong password",
                sensitive = true,
                helper = "Use a long, unique password. If it is lost, the data cannot be recovered.",
            ),
        ),
        info = ToolInfo(
            summary = "AES-GCM is a modern authenticated encryption method, available with a " +
                "128, 192 or 256-bit key. The key is derived from your password with " +
                "PBKDF2-HMAC-SHA256 (210,000 iterations) using a random salt, and every message " +
                "gets a fresh random nonce.",
            requiresKey = true,
            useCases = listOf(
                "Storing a note you must not leave in plain text",
                "Sending text through a channel you do not fully trust",
            ),
            warnings = listOf(
                "Use a strong password and protect it carefully - there is no recovery if it is lost.",
                "The encrypted output contains everything needed to decrypt it except the password.",
            ),
            convention = "Payload: version + KDF id + salt + IV + ciphertext (with GCM tag), " +
                "Base64 encoded. The key size is tried on decrypt, so AES-128/192/256 payloads all " +
                "read back without a setting to remember.",
        ),
        keywords = listOf("aes", "aes128", "aes192", "aes256", "gcm", "encrypt", "secure", "password", "authenticated"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val password = (params["password"] ?: "").toCharArray()
        if (password.isEmpty()) throw Errors.missingPassword()
        return try {
            if (direction == Direction.ENCODE) {
                if (input.isEmpty()) throw Errors.emptyInput()
                AesGcmPayload.encrypt(input, password, keySizeBytes = keySizeOf(params))
            } else {
                AesGcmPayload.decrypt(input.trim(), password)
            }
        } finally {
            password.fill('\u0000')
        }
    }

    private fun keySizeOf(params: Map<String, String>): Int =
        (params["keySize"] ?: "256").toIntOrNull()?.div(8) ?: 32
}
