package com.texthub.core.crypto

import com.texthub.core.codec.Base64Codec
import com.texthub.core.model.Errors
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-CBC with Encrypt-then-MAC authentication (HMAC-SHA256).
 *
 * Payload layout, all Base64 encoded as a whole:
 *
 * ```
 * 0   1   version = 0x02
 * 1   1   KDF id  = 0x01 (PBKDF2-HMAC-SHA256, 210 000 iterations, 64 bytes split in two)
 * 2   16  salt
 * 18  16  IV
 * 34  n   ciphertext (PKCS#5/PKCS#7 padded)
 * 34+n 32 HMAC-SHA256 over bytes 0..(34+n-1)
 * ```
 *
 * The 64 byte PBKDF2 output is split into a 32 byte AES key and a 32 byte MAC key, so the two
 * purposes use independent keys. Verification uses [MessageDigest.isEqual] (constant time) and
 * happens *before* decryption, which is what makes Encrypt-then-MAC safe.
 */
object AesCbcHmac {

    private const val VERSION: Byte = 0x02
    private const val KDF_ID: Byte = 0x01
    private const val SALT_LEN = 16
    private const val IV_LEN = 16
    private const val MAC_LEN = 32
    private const val HEADER_LEN = 2
    private const val PREFIX_LEN = HEADER_LEN + SALT_LEN + IV_LEN
    private const val DERIVED_LEN = 64
    private const val ITERATIONS = 210_000

    /** Supported AES key sizes in bytes; 16 = AES-128, 24 = AES-192, 32 = AES-256. */
    val KEY_SIZES = intArrayOf(16, 24, 32)

    private val random = SecureRandom()

    fun encrypt(plaintext: String, password: CharArray, keySizeBytes: Int = 32): String {
        val keyLength = if (keySizeBytes in KEY_SIZES) keySizeBytes else 32
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }
        // The MAC key is always 32 bytes and never derived from the AES key: the two are separate
        // halves of one PBKDF2 output.
        val keys = Pbkdf2.derive(password, salt, ITERATIONS, keyLength + 32)
        val encKey = keys.copyOfRange(0, keyLength)
        val macKey = keys.copyOfRange(keyLength, keyLength + 32)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(encKey, "AES"), IvParameterSpec(iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val payload = ByteArray(PREFIX_LEN + ciphertext.size + MAC_LEN)
        payload[0] = VERSION
        payload[1] = KDF_ID
        System.arraycopy(salt, 0, payload, HEADER_LEN, SALT_LEN)
        System.arraycopy(iv, 0, payload, HEADER_LEN + SALT_LEN, IV_LEN)
        System.arraycopy(ciphertext, 0, payload, PREFIX_LEN, ciphertext.size)
        val mac = hmac(macKey, payload, 0, PREFIX_LEN + ciphertext.size)
        System.arraycopy(mac, 0, payload, PREFIX_LEN + ciphertext.size, MAC_LEN)
        return Base64Codec.encode(payload)
    }

    fun decrypt(payloadBase64: String, password: CharArray): String {
        val payload = try {
            Base64Codec.decode(payloadBase64.trim())
        } catch (e: Exception) {
            throw Errors.aesFormat()
        }
        if (payload.size < PREFIX_LEN + MAC_LEN + 16) throw Errors.aesFormat()
        if (payload[0] != VERSION || payload[1] != KDF_ID) throw Errors.aesFormat()

        val salt = payload.copyOfRange(HEADER_LEN, HEADER_LEN + SALT_LEN)
        val iv = payload.copyOfRange(HEADER_LEN + SALT_LEN, PREFIX_LEN)
        val ciphertext = payload.copyOfRange(PREFIX_LEN, payload.size - MAC_LEN)
        val expectedMac = payload.copyOfRange(payload.size - MAC_LEN, payload.size)

        // Try each supported key size: the MAC key is the 32 bytes that follow the AES key.
        for (keyLength in KEY_SIZES) {
            val keys = Pbkdf2.derive(password, salt, ITERATIONS, keyLength + 32)
            val encKey = keys.copyOfRange(0, keyLength)
            val macKey = keys.copyOfRange(keyLength, keyLength + 32)

            // Encrypt-then-MAC: verify first, decrypt only when the tag matches.
            val actualMac = hmac(macKey, payload, 0, payload.size - MAC_LEN)
            if (!MessageDigest.isEqual(expectedMac, actualMac)) continue

            return try {
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey, "AES"), IvParameterSpec(iv))
                String(cipher.doFinal(ciphertext), Charsets.UTF_8)
            } catch (e: GeneralSecurityException) {
                throw Errors.aes()
            }
        }
        throw Errors.aes()
    }

    /** True when the payload authenticates with the given password at any supported key size. */
    fun keySizeOf(payloadBase64: String, password: CharArray): Int? {
        val payload = try {
            Base64Codec.decode(payloadBase64.trim())
        } catch (e: Exception) {
            return null
        }
        if (payload.size < PREFIX_LEN + MAC_LEN + 16) return null
        if (payload[0] != VERSION || payload[1] != KDF_ID) return null
        val salt = payload.copyOfRange(HEADER_LEN, HEADER_LEN + SALT_LEN)
        val expectedMac = payload.copyOfRange(payload.size - MAC_LEN, payload.size)
        for (keyLength in KEY_SIZES) {
            val keys = Pbkdf2.derive(password, salt, ITERATIONS, keyLength + 32)
            val macKey = keys.copyOfRange(keyLength, keyLength + 32)
            if (MessageDigest.isEqual(expectedMac, hmac(macKey, payload, 0, payload.size - MAC_LEN))) {
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

/**
 * ChaCha20-Poly1305 (RFC 8439) authenticated encryption.
 *
 * Payload layout (Base64 encoded as a whole):
 *
 * ```
 * 0   1   version = 0x03
 * 1   1   KDF id  = 0x01 (PBKDF2-HMAC-SHA256, 210 000 iterations, 32 bytes)
 * 2   16  salt
 * 18  12  nonce
 * 30  n   ciphertext || 16 byte Poly1305 tag
 * ```
 *
 * Support depends on the platform provider. Android 9 (API 28) and newer - and the JDK used for
 * the unit tests - provide it. When the provider is missing, the tool reports that clearly
 * instead of silently falling back to something weaker.
 */
object ChaCha20Poly1305 {

    private const val VERSION: Byte = 0x03
    private const val KDF_ID: Byte = 0x01
    private const val SALT_LEN = 16
    private const val NONCE_LEN = 12
    private const val TAG_BITS = 128
    private const val HEADER_LEN = 2
    private const val PREFIX_LEN = HEADER_LEN + SALT_LEN + NONCE_LEN
    private const val ITERATIONS = 210_000

    private val random = SecureRandom()

    class Unsupported : RuntimeException(
        "ChaCha20-Poly1305 is not available on this Android version. Use AES-GCM, which is " +
            "supported everywhere."
    )

    fun isAvailable(): Boolean = try {
        Cipher.getInstance(TRANSFORMATION)
        true
    } catch (e: GeneralSecurityException) {
        false
    }

    fun encrypt(plaintext: String, password: CharArray): String {
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val nonce = ByteArray(NONCE_LEN).also { random.nextBytes(it) }
        val key = Pbkdf2.derive(password, salt, ITERATIONS, 32)

        val cipher = try {
            Cipher.getInstance(TRANSFORMATION)
        } catch (e: GeneralSecurityException) {
            throw Unsupported()
        }
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "ChaCha20"), IvParameterSpec(nonce))
        val ciphertext = try {
            cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        } catch (e: GeneralSecurityException) {
            throw Unsupported()
        }

        val payload = ByteArray(PREFIX_LEN + ciphertext.size)
        payload[0] = VERSION
        payload[1] = KDF_ID
        System.arraycopy(salt, 0, payload, HEADER_LEN, SALT_LEN)
        System.arraycopy(nonce, 0, payload, HEADER_LEN + SALT_LEN, NONCE_LEN)
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
        if (payload[0] != VERSION || payload[1] != KDF_ID) throw Errors.aesFormat()

        val salt = payload.copyOfRange(HEADER_LEN, HEADER_LEN + SALT_LEN)
        val nonce = payload.copyOfRange(HEADER_LEN + SALT_LEN, PREFIX_LEN)
        val ciphertext = payload.copyOfRange(PREFIX_LEN, payload.size)
        val key = Pbkdf2.derive(password, salt, ITERATIONS, 32)

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "ChaCha20"), IvParameterSpec(nonce))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            throw Errors.aes()
        }
    }

    private const val TRANSFORMATION = "ChaCha20-Poly1305"
}
