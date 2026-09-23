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
 * 1       1     KDF id             = 0x02 (PBKDF2-HMAC-SHA256, 210 000 iterations, 256-bit key)
 *                                   0x01 = legacy: 210 000 iterations, key length not recorded
 * 2       16    salt               (random, per message)
 * 18      12    IV / nonce         (random, per message, never reused with the same key)
 * 30      n     ciphertext || 16 byte GCM authentication tag
 * ```
 *
 * Since 1.4.2 the KDF id records the 256-bit key size, so decryption knows which key to build
 * instead of trying every length. Payloads written before that use KDF id `0x01`, where the size
 * was not recorded; those are still readable (the size is detected from the GCM tag) and the app
 * reports which size they really use.
 *
 * The password itself is never stored, transmitted or logged; it only exists while a
 * single operation runs. Losing the password means the data cannot be recovered.
 */
object AesGcmPayload {

    /** Envelope version. Public so the Universal Decoder can identify the format structurally. */
    const val VERSION: Byte = 0x01
    /** Legacy: PBKDF2-HMAC-SHA256, 210 000 iterations, key length not recorded in the payload. */
    private const val KDF_PBKDF2_HMAC_SHA256: Byte = 0x01

    /** PBKDF2-HMAC-SHA256, 210 000 iterations, 256-bit key (AES-256). */
    private const val KDF_PBKDF2_HMAC_SHA256_AES256: Byte = 0x02
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
     * Encrypts with the requested AES key size.
     *
     * @param prefixKeySizeInPayload when true (the app does this) the key size is recorded in the
     *   KDF id field, so decryption uses exactly that size. Direct callers that only need the
     *   historical layout can pass false; those payloads keep KDF id `0x01` and are decrypted by
     *   detecting the size from the tag.
     */
    /**
     * Structural check used by the Universal Decoder: the version byte, a known KDF id and the
     * documented minimum length of this layout. Being structurally plausible is **not** proof of
     * authenticity - only [decrypt] can decide that, and it needs the password.
     */
    fun looksLikeEnvelope(payloadBase64: String): Boolean {
        val bytes = try {
            Base64Codec.decode(payloadBase64.trim())
        } catch (e: Exception) {
            return false
        }
        if (bytes.size < PREFIX_LEN + 16) return false
        if (bytes[0] != VERSION) return false
        return bytes[1] == KDF_PBKDF2_HMAC_SHA256 || bytes[1] == KDF_PBKDF2_HMAC_SHA256_AES256
    }

    fun encrypt(
        plaintext: String,
        password: CharArray,
        keySizeBytes: Int = 32,
        prefixKeySizeInPayload: Boolean = false,
    ): String {
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
        payload[1] = if (prefixKeySizeInPayload && keyLength == 32) {
            KDF_PBKDF2_HMAC_SHA256_AES256
        } else {
            KDF_PBKDF2_HMAC_SHA256
        }
        System.arraycopy(salt, 0, payload, HEADER_LEN, SALT_LEN)
        System.arraycopy(iv, 0, payload, HEADER_LEN + SALT_LEN, IV_LEN)
        System.arraycopy(ciphertext, 0, payload, PREFIX_LEN, ciphertext.size)
        return Base64Codec.encode(payload)
    }

    /**
     * Decrypts a payload.
     *
     * @param expectedKeySizeBytes when given, only that key size is used - a payload written with a
     *   different size fails authentication instead of decrypting "with the wrong key". The app
     *   passes the value of the Key size setting here.
     */
    fun decrypt(payloadBase64: String, password: CharArray, expectedKeySizeBytes: Int? = null): String {
        val payload = try {
            Base64Codec.decode(payloadBase64.trim())
        } catch (e: Exception) {
            throw Errors.aesFormat()
        }
        if (payload.size < PREFIX_LEN + 16) throw Errors.aesFormat()
        if (payload[0] != VERSION) throw Errors.aesFormat()
        val recordedSize = try {
            recordedKeySize(payload[1])
        } catch (e: UnsupportedKdf) {
            throw Errors.aesFormat()
        }

        val salt = payload.copyOfRange(HEADER_LEN, HEADER_LEN + SALT_LEN)
        val iv = payload.copyOfRange(HEADER_LEN + SALT_LEN, PREFIX_LEN)
        val ciphertext = payload.copyOfRange(PREFIX_LEN, payload.size)

        // Which key size to use:
        //   * modern payloads record it (KDF id 0x02 = 256-bit) -> use exactly that size;
        //   * legacy payloads recorded nothing -> detect it from the tag, trying the requested size
        //     first when there is one.
        // In both cases a size that does not match the Key size setting is *refused* with a message
        // that names the size the message actually needs, instead of quietly decrypting anyway.
        val requested = expectedKeySizeBytes?.takeIf { it in KEY_SIZES }
        if (recordedSize != null) {
            if (requested != null && requested != recordedSize) {
                throw Errors.aesKeySizeMismatch(recordedSize * 8)
            }
            return attempt(password, salt, iv, ciphertext, recordedSize) ?: throw Errors.aes()
        }
        val order: List<Int> = if (requested != null) {
            listOf(requested) + KEY_SIZES.filter { it != requested }
        } else {
            KEY_SIZES.toList()
        }
        for (keyLength in order) {
            val plaintext = attempt(password, salt, iv, ciphertext, keyLength) ?: continue
            if (requested != null && keyLength != requested) {
                throw Errors.aesKeySizeMismatch(keyLength * 8)
            }
            return plaintext
        }
        throw Errors.aes()
    }

    /** One decryption attempt: the plaintext, or null when the tag does not authenticate. */
    private fun attempt(
        password: CharArray,
        salt: ByteArray,
        iv: ByteArray,
        ciphertext: ByteArray,
        keyLength: Int,
    ): String? = try {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            deriveKey(password, salt, keyLength),
            GCMParameterSpec(TAG_BITS, iv),
        )
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    } catch (e: GeneralSecurityException) {
        null
    }

    /** Key size in bytes this payload actually needs, or null when nothing authenticates it. */
    fun keySizeOf(payloadBase64: String, password: CharArray): Int? {
        val payload = try {
            Base64Codec.decode(payloadBase64.trim())
        } catch (e: Exception) {
            return null
        }
        if (payload.size < PREFIX_LEN + 16) return null
        if (payload[0] != VERSION) return null
        val recorded = try {
            recordedKeySize(payload[1])
        } catch (e: UnsupportedKdf) {
            return null
        }
        val salt = payload.copyOfRange(HEADER_LEN, HEADER_LEN + SALT_LEN)
        val iv = payload.copyOfRange(HEADER_LEN + SALT_LEN, PREFIX_LEN)
        val ciphertext = payload.copyOfRange(PREFIX_LEN, payload.size)
        val sizes = if (recorded != null) intArrayOf(recorded) else KEY_SIZES
        for (keyLength in sizes) {
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

    /** The key length a KDF id stands for, or null when the payload did not record one. */
    private fun recordedKeySize(kdfId: Byte): Int? = when (kdfId) {
        KDF_PBKDF2_HMAC_SHA256_AES256 -> 32
        KDF_PBKDF2_HMAC_SHA256 -> null
        else -> throw UnsupportedKdf()
    }

    private class UnsupportedKdf : RuntimeException()

    private fun deriveKey(password: CharArray, salt: ByteArray, keyLengthBytes: Int): SecretKeySpec {
        val raw = Pbkdf2.derive(password, salt, ITERATIONS, keyLengthBytes)
        return SecretKeySpec(raw, "AES")
    }
}
