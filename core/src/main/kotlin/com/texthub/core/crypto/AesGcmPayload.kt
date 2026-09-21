package com.texthub.core.crypto

import com.texthub.core.codec.Base64Codec
import com.texthub.core.model.Errors
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM password encryption.
 *
 * Payload layout (all of it is Base64 encoded as a whole):
 *
 * ```
 * offset  size  field
 * 0       1     version            = 0x01
 * 1       1     KDF id             = 0x01 (PBKDF2-HMAC-SHA256, 210 000 iterations)
 * 2       16    salt               (random, per message)
 * 18      12    IV / nonce         (random, per message, never reused with the same key)
 * 30      n     ciphertext || 16 byte GCM authentication tag
 * ```
 *
 * The password itself is never stored, transmitted or logged; it only exists while a
 * single operation runs. Losing the password means the data cannot be recovered.
 */
object AesGcmPayload {

    private const val VERSION: Byte = 0x01
    private const val KDF_PBKDF2_HMAC_SHA256: Byte = 0x01
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    /** Supported AES key sizes in bytes; 16 = AES-128, 24 = AES-192, 32 = AES-256. */
    val KEY_SIZES = intArrayOf(16, 24, 32)
    private const val TAG_BITS = 128
    private const val ITERATIONS = 210_000

    private const val HEADER_LEN = 2
    private const val PREFIX_LEN = HEADER_LEN + SALT_LEN + IV_LEN

    private val secureRandom = SecureRandom()

    /**
     * Encrypts with the requested AES key size. The key size is **not** stored in the payload:
     * every size is tried on decrypt, which keeps old payloads readable and stays compatible with
     * the format documented in docs/ENCRYPTION_FORMAT.md.
     */
    fun encrypt(plaintext: String, password: CharArray, keySizeBytes: Int = 32): String {
        val keyLength = if (keySizeBytes in KEY_SIZES) keySizeBytes else 32
        val salt = ByteArray(SALT_LEN)
        val iv = ByteArray(IV_LEN)
        secureRandom.nextBytes(salt)
        secureRandom.nextBytes(iv)

        val key = deriveKey(password, salt, keyLength)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val payload = ByteArray(PREFIX_LEN + ciphertext.size)
        payload[0] = VERSION
        payload[1] = KDF_PBKDF2_HMAC_SHA256
        System.arraycopy(salt, 0, payload, HEADER_LEN, SALT_LEN)
        System.arraycopy(iv, 0, payload, HEADER_LEN + SALT_LEN, IV_LEN)
        System.arraycopy(ciphertext, 0, payload, PREFIX_LEN, ciphertext.size)
        return Base64Codec.encode(payload)
    }

    fun decrypt(payloadBase64: String, password: CharArray): String {
        val payload = try {
            Base64Codec.decode(payloadBase64.trim())
        } catch (e: Exception) {
            throw Errors.aesFormat()
        }
        if (payload.size < PREFIX_LEN + 16) throw Errors.aesFormat()
        if (payload[0] != VERSION) throw Errors.aesFormat()
        if (payload[1] != KDF_PBKDF2_HMAC_SHA256) throw Errors.aesFormat()

        val salt = payload.copyOfRange(HEADER_LEN, HEADER_LEN + SALT_LEN)
        val iv = payload.copyOfRange(HEADER_LEN + SALT_LEN, PREFIX_LEN)
        val ciphertext = payload.copyOfRange(PREFIX_LEN, payload.size)

        // The key size is not part of the payload, so each supported size is tried in turn. Only
        // the correct one passes the GCM tag check, so this cannot silently accept wrong data.
        for (keyLength in KEY_SIZES) {
            val key = deriveKey(password, salt, keyLength)
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
                return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
            } catch (e: GeneralSecurityException) {
                // Try the next key size; a wrong size always fails authentication.
            }
        }
        throw Errors.aes()
    }

    /** True when the payload decrypts with the given password at any supported key size. */
    fun keySizeOf(payloadBase64: String, password: CharArray): Int? {
        val payload = try {
            Base64Codec.decode(payloadBase64.trim())
        } catch (e: Exception) {
            return null
        }
        if (payload.size < PREFIX_LEN + 16) return null
        if (payload[0] != VERSION || payload[1] != KDF_PBKDF2_HMAC_SHA256) return null
        val salt = payload.copyOfRange(HEADER_LEN, HEADER_LEN + SALT_LEN)
        val iv = payload.copyOfRange(HEADER_LEN + SALT_LEN, PREFIX_LEN)
        val ciphertext = payload.copyOfRange(PREFIX_LEN, payload.size)
        for (keyLength in KEY_SIZES) {
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt, keyLength), GCMParameterSpec(TAG_BITS, iv))
                cipher.doFinal(ciphertext)
                return keyLength
            } catch (e: GeneralSecurityException) {
                // keep looking
            }
        }
        return null
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, keyLengthBytes: Int): SecretKeySpec {
        val raw = Pbkdf2.derive(password, salt, ITERATIONS, keyLengthBytes)
        return SecretKeySpec(raw, "AES")
    }
}
