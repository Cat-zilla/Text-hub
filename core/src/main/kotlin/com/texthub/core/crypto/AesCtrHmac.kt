package com.texthub.core.crypto

import com.texthub.core.codec.Base64Codec
import com.texthub.core.model.Errors
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES in CTR mode with Encrypt-then-MAC authentication (HMAC-SHA256).
 *
 * Payload layout, Base64 encoded as a whole:
 *
 * ```
 * 0    1   version         = 0x04
 * 1    1   KDF id          = 0x02 (PBKDF2-HMAC-SHA256, 210 000 iterations, 256-bit AES key + MAC key)
 *                           0x01 = legacy: the AES key length was not recorded in the payload
 * 2    16  salt
 * 18   16  initial counter block (random, per message)
 * 34   n   ciphertext (CTR, no padding needed)
 * 34+n 32  HMAC-SHA256 over bytes 0..(34+n-1)
 * ```
 *
 * CTR turns AES into a stream cipher: it needs a *unique* counter block per message, never a
 * repeating one, and it offers no integrity by itself. Both gaps are closed here by drawing a
 * fresh random counter block per message and authenticating the result with a separate HMAC key
 * that comes from the second half of the PBKDF2 output. The tag is verified (constant time)
 * before a single byte is decrypted.
 *
 * Since 1.4.2 the KDF id records a 256-bit key and the Key size setting is enforced on decrypt.
 *
 * This is offered for interoperability with systems that speak CTR; use AES-GCM unless you need
 * that interoperability.
 */
object AesCtrHmac {

    private const val VERSION: Byte = 0x04

    /** PBKDF2-HMAC-SHA256, 210 000 iterations, 256-bit AES key followed by a 256-bit MAC key. */
    private const val KDF_ID_AES256: Byte = 0x02

    /** Legacy: the AES key length was not recorded in the payload. */
    private const val KDF_ID_LEGACY: Byte = 0x01

    private const val SALT_LEN = 16
    private const val COUNTER_LEN = 16
    private const val MAC_LEN = 32
    private const val HEADER_LEN = 2
    private const val PREFIX_LEN = HEADER_LEN + SALT_LEN + COUNTER_LEN
    private const val ITERATIONS = 210_000
    private const val TRANSFORMATION = "AES/CTR/NoPadding"

    /** Supported AES key sizes in bytes; 16 = AES-128, 24 = AES-192, 32 = AES-256. */
    val KEY_SIZES = intArrayOf(16, 24, 32)

    private val random = SecureRandom()

    /**
     * @param prefixKeySizeInPayload when true (the app does this) a 256-bit key is recorded in the
     *   KDF id field so decryption uses exactly that size.
     */
    fun encrypt(
        plaintext: String,
        password: CharArray,
        keySizeBytes: Int = 32,
        prefixKeySizeInPayload: Boolean = false,
    ): String {
        val keyLength = if (keySizeBytes in KEY_SIZES) keySizeBytes else 32
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val counter = ByteArray(COUNTER_LEN).also { random.nextBytes(it) }

        val keys = Pbkdf2.derive(password, salt, ITERATIONS, keyLength + 32)
        val encKey = keys.copyOfRange(0, keyLength)
        val macKey = keys.copyOfRange(keyLength, keyLength + 32)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(encKey, "AES"), IvParameterSpec(counter))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val payload = ByteArray(PREFIX_LEN + ciphertext.size + MAC_LEN)
        payload[0] = VERSION
        payload[1] = if (prefixKeySizeInPayload && keyLength == 32) KDF_ID_AES256 else KDF_ID_LEGACY
        System.arraycopy(salt, 0, payload, HEADER_LEN, SALT_LEN)
        System.arraycopy(counter, 0, payload, HEADER_LEN + SALT_LEN, COUNTER_LEN)
        System.arraycopy(ciphertext, 0, payload, PREFIX_LEN, ciphertext.size)
        val tag = hmac(macKey, payload, 0, PREFIX_LEN + ciphertext.size)
        System.arraycopy(tag, 0, payload, PREFIX_LEN + ciphertext.size, MAC_LEN)
        return Base64Codec.encode(payload)
    }

    /**
     * @param expectedKeySizeBytes when given, the Key size setting must match the payload: a
     *   message written with a different key size is refused with a message naming the right one.
     */
    fun decrypt(payloadBase64: String, password: CharArray, expectedKeySizeBytes: Int? = null): String {
        val payload = try {
            Base64Codec.decode(payloadBase64.trim())
        } catch (e: Exception) {
            throw Errors.aesFormat()
        }
        if (payload.size < PREFIX_LEN + MAC_LEN) throw Errors.aesFormat()
        if (payload[0] != VERSION) throw Errors.aesFormat()
        val recordedSize = when (payload[1]) {
            KDF_ID_AES256 -> 32
            KDF_ID_LEGACY -> null
            else -> throw Errors.aesFormat()
        }

        val salt = payload.copyOfRange(HEADER_LEN, HEADER_LEN + SALT_LEN)
        val counter = payload.copyOfRange(HEADER_LEN + SALT_LEN, PREFIX_LEN)
        val ciphertext = payload.copyOfRange(PREFIX_LEN, payload.size - MAC_LEN)
        val expectedTag = payload.copyOfRange(payload.size - MAC_LEN, payload.size)

        // Which key size to use, and refusing a mismatch instead of decrypting anyway.
        val requested = expectedKeySizeBytes?.takeIf { it in KEY_SIZES }
        if (recordedSize != null) {
            if (requested != null && requested != recordedSize) {
                throw Errors.aesKeySizeMismatch(recordedSize * 8)
            }
            return attempt(password, salt, counter, ciphertext, expectedTag, payload, recordedSize)
                ?: throw Errors.aes()
        }
        val order: List<Int> = if (requested != null) {
            listOf(requested) + KEY_SIZES.filter { it != requested }
        } else {
            KEY_SIZES.toList()
        }
        for (keyLength in order) {
            val plaintext = attempt(password, salt, counter, ciphertext, expectedTag, payload, keyLength) ?: continue
            if (requested != null && keyLength != requested) {
                throw Errors.aesKeySizeMismatch(keyLength * 8)
            }
            return plaintext
        }
        throw Errors.aes()
    }

    /**
     * One decryption attempt. Encrypt-then-MAC: the tag is verified (constant time) before a
     * single byte is decrypted, so a wrong key size cannot reach the cipher.
     */
    private fun attempt(
        password: CharArray,
        salt: ByteArray,
        counter: ByteArray,
        ciphertext: ByteArray,
        expectedTag: ByteArray,
        payload: ByteArray,
        keyLength: Int,
    ): String? {
        val keys = Pbkdf2.derive(password, salt, ITERATIONS, keyLength + 32)
        val encKey = keys.copyOfRange(0, keyLength)
        val macKey = keys.copyOfRange(keyLength, keyLength + 32)
        if (!MessageDigest.isEqual(expectedTag, hmac(macKey, payload, 0, payload.size - MAC_LEN))) return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey, "AES"), IvParameterSpec(counter))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            null
        }
    }

    /** Key size in bytes this payload actually needs, or null when nothing authenticates it. */
    fun keySizeOf(payloadBase64: String, password: CharArray): Int? {
        val payload = try {
            Base64Codec.decode(payloadBase64.trim())
        } catch (e: Exception) {
            return null
        }
        if (payload.size < PREFIX_LEN + MAC_LEN) return null
        if (payload[0] != VERSION) return null
        val recorded = when (payload[1]) {
            KDF_ID_AES256 -> 32
            KDF_ID_LEGACY -> null
            else -> return null
        }
        val salt = payload.copyOfRange(HEADER_LEN, HEADER_LEN + SALT_LEN)
        val expectedTag = payload.copyOfRange(payload.size - MAC_LEN, payload.size)
        val sizes = if (recorded != null) intArrayOf(recorded) else KEY_SIZES
        for (keyLength in sizes) {
            val keys = Pbkdf2.derive(password, salt, ITERATIONS, keyLength + 32)
            val macKey = keys.copyOfRange(keyLength, keyLength + 32)
            if (MessageDigest.isEqual(expectedTag, hmac(macKey, payload, 0, payload.size - MAC_LEN))) {
                return keyLength
            }
        }
        return null
    }

    private fun hmac(key: ByteArray, data: ByteArray, offset: Int, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        mac.update(data, offset, length)
        return mac.doFinal()
    }
}
