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
 * 0    1   version = 0x04
 * 1    1   KDF id  = 0x01 (PBKDF2-HMAC-SHA256, 210 000 iterations)
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
 * This is offered for interoperability with systems that speak CTR; use AES-GCM unless you need
 * that interoperability.
 */
object AesCtrHmac {

    private const val VERSION: Byte = 0x04
    private const val KDF_ID: Byte = 0x01
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

    fun encrypt(plaintext: String, password: CharArray, keySizeBytes: Int = 32): String {
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
        payload[1] = KDF_ID
        System.arraycopy(salt, 0, payload, HEADER_LEN, SALT_LEN)
        System.arraycopy(counter, 0, payload, HEADER_LEN + SALT_LEN, COUNTER_LEN)
        System.arraycopy(ciphertext, 0, payload, PREFIX_LEN, ciphertext.size)
        val tag = hmac(macKey, payload, 0, PREFIX_LEN + ciphertext.size)
        System.arraycopy(tag, 0, payload, PREFIX_LEN + ciphertext.size, MAC_LEN)
        return Base64Codec.encode(payload)
    }

    fun decrypt(payloadBase64: String, password: CharArray): String {
        val payload = try {
            Base64Codec.decode(payloadBase64.trim())
        } catch (e: Exception) {
            throw Errors.aesFormat()
        }
        if (payload.size < PREFIX_LEN + MAC_LEN) throw Errors.aesFormat()
        if (payload[0] != VERSION || payload[1] != KDF_ID) throw Errors.aesFormat()

        val salt = payload.copyOfRange(HEADER_LEN, HEADER_LEN + SALT_LEN)
        val counter = payload.copyOfRange(HEADER_LEN + SALT_LEN, PREFIX_LEN)
        val ciphertext = payload.copyOfRange(PREFIX_LEN, payload.size - MAC_LEN)
        val expectedTag = payload.copyOfRange(payload.size - MAC_LEN, payload.size)

        for (keyLength in KEY_SIZES) {
            val keys = Pbkdf2.derive(password, salt, ITERATIONS, keyLength + 32)
            val encKey = keys.copyOfRange(0, keyLength)
            val macKey = keys.copyOfRange(keyLength, keyLength + 32)

            // Encrypt-then-MAC: authenticate first, decrypt only when the tag matches.
            if (!MessageDigest.isEqual(expectedTag, hmac(macKey, payload, 0, payload.size - MAC_LEN))) continue

            return try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey, "AES"), IvParameterSpec(counter))
                String(cipher.doFinal(ciphertext), Charsets.UTF_8)
            } catch (e: GeneralSecurityException) {
                throw Errors.aes()
            }
        }
        throw Errors.aes()
    }

    /** Key size in bytes that authenticates this payload with this password, or null. */
    fun keySizeOf(payloadBase64: String, password: CharArray): Int? {
        val payload = try {
            Base64Codec.decode(payloadBase64.trim())
        } catch (e: Exception) {
            return null
        }
        if (payload.size < PREFIX_LEN + MAC_LEN) return null
        if (payload[0] != VERSION || payload[1] != KDF_ID) return null
        val salt = payload.copyOfRange(HEADER_LEN, HEADER_LEN + SALT_LEN)
        val expectedTag = payload.copyOfRange(payload.size - MAC_LEN, payload.size)
        for (keyLength in KEY_SIZES) {
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
