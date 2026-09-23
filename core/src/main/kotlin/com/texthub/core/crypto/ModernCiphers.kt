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
 * AES in CBC mode with Encrypt-then-MAC authentication (HMAC-SHA256).
 *
 * Payload layout, all Base64 encoded as a whole:
 *
 * ```
 * 0    1   version   = 0x02
 * 1    1   KDF id    = 0x02 (PBKDF2-HMAC-SHA256, 210 000 iterations, 256-bit AES key + 256-bit MAC key)
 *                     0x01 = legacy: the AES key length was not recorded in the payload
 * 2    16  salt
 * 18   16  IV
 * 34   n   ciphertext (PKCS#5/PKCS#7 padded)
 * 34+n 32  HMAC-SHA256 over bytes 0..(34+n-1)
 * ```
 *
 * The PBKDF2 output is split into the AES key and an independent MAC key, so the two purposes
 * never share key material. Verification uses [MessageDigest.isEqual] (constant time) and happens
 * *before* decryption, which is what makes Encrypt-then-MAC safe.
 *
 * Since 1.4.2 the KDF id records a 256-bit key, so decryption knows exactly which key to build.
 * The Key size setting is enforced: a message written with another key size is refused with a
 * message naming the size it actually needs, instead of being decrypted with the wrong key.
 */
object AesCbcHmac {

    /** Envelope version. Public so the Universal Decoder can identify the format structurally. */
    const val VERSION: Byte = 0x02

    /** PBKDF2-HMAC-SHA256, 210 000 iterations, 256-bit AES key followed by a 256-bit MAC key. */
    private const val KDF_ID_AES256: Byte = 0x02

    /** Legacy: the AES key length was not recorded in the payload. */
    private const val KDF_ID_LEGACY: Byte = 0x01

    private const val SALT_LEN = 16
    private const val IV_LEN = 16
    private const val MAC_LEN = 32
    private const val HEADER_LEN = 2
    private const val PREFIX_LEN = HEADER_LEN + SALT_LEN + IV_LEN
    private const val ITERATIONS = 210_000

    /** Supported AES key sizes in bytes; 16 = AES-128, 24 = AES-192, 32 = AES-256. */
    val KEY_SIZES = intArrayOf(16, 24, 32)

    private val random = SecureRandom()

    /**
     * @param prefixKeySizeInPayload when true (the app does this) a 256-bit key is recorded in the
     *   KDF id field so decryption uses exactly that size.
     */
    /**
     * Structural check used by the Universal Decoder: version byte, known KDF id, a ciphertext that
     * is a whole number of AES blocks and a 32-byte MAC. Authentication still has to succeed.
     */
    fun looksLikeEnvelope(payloadBase64: String): Boolean {
        val bytes = try {
            Base64Codec.decode(payloadBase64.trim())
        } catch (e: Exception) {
            return false
        }
        if (bytes.size < PREFIX_LEN + MAC_LEN + 16) return false
        if (bytes[0] != VERSION) return false
        if (bytes[1] != KDF_ID_AES256 && bytes[1] != KDF_ID_LEGACY) return false
        return (bytes.size - PREFIX_LEN - MAC_LEN) % 16 == 0
    }

    fun encrypt(
        plaintext: String,
        password: CharArray,
        keySizeBytes: Int = 32,
        prefixKeySizeInPayload: Boolean = false,
    ): String {
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
        payload[1] = if (prefixKeySizeInPayload && keyLength == 32) KDF_ID_AES256 else KDF_ID_LEGACY
        System.arraycopy(salt, 0, payload, HEADER_LEN, SALT_LEN)
        System.arraycopy(iv, 0, payload, HEADER_LEN + SALT_LEN, IV_LEN)
        System.arraycopy(ciphertext, 0, payload, PREFIX_LEN, ciphertext.size)
        val mac = hmac(macKey, payload, 0, PREFIX_LEN + ciphertext.size)
        System.arraycopy(mac, 0, payload, PREFIX_LEN + ciphertext.size, MAC_LEN)
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
        if (payload.size < PREFIX_LEN + MAC_LEN + 16) throw Errors.aesFormat()
        if (payload[0] != VERSION) throw Errors.aesFormat()
        val recordedSize = when (payload[1]) {
            KDF_ID_AES256 -> 32
            KDF_ID_LEGACY -> null
            else -> throw Errors.aesFormat()
        }

        val salt = payload.copyOfRange(HEADER_LEN, HEADER_LEN + SALT_LEN)
        val iv = payload.copyOfRange(HEADER_LEN + SALT_LEN, PREFIX_LEN)
        val ciphertext = payload.copyOfRange(PREFIX_LEN, payload.size - MAC_LEN)
        val expectedMac = payload.copyOfRange(payload.size - MAC_LEN, payload.size)

        // Which key size to use, and refusing a mismatch instead of decrypting anyway: modern
        // payloads record 256-bit, legacy ones are detected from the MAC tag.
        val requested = expectedKeySizeBytes?.takeIf { it in KEY_SIZES }
        if (recordedSize != null) {
            if (requested != null && requested != recordedSize) {
                throw Errors.aesKeySizeMismatch(recordedSize * 8)
            }
            return attempt(password, salt, iv, ciphertext, expectedMac, payload, recordedSize)
                ?: throw Errors.aes()
        }
        val order: List<Int> = if (requested != null) {
            listOf(requested) + KEY_SIZES.filter { it != requested }
        } else {
            KEY_SIZES.toList()
        }
        for (keyLength in order) {
            val plaintext = attempt(password, salt, iv, ciphertext, expectedMac, payload, keyLength) ?: continue
            if (requested != null && keyLength != requested) {
                throw Errors.aesKeySizeMismatch(keyLength * 8)
            }
            return plaintext
        }
        throw Errors.aes()
    }

    /**
     * One decryption attempt. Encrypt-then-MAC is respected: the tag is verified (constant time)
     * before anything is decrypted, so a wrong key size cannot reach the cipher.
     */
    private fun attempt(
        password: CharArray,
        salt: ByteArray,
        iv: ByteArray,
        ciphertext: ByteArray,
        expectedMac: ByteArray,
        payload: ByteArray,
        keyLength: Int,
    ): String? {
        val keys = Pbkdf2.derive(password, salt, ITERATIONS, keyLength + 32)
        val encKey = keys.copyOfRange(0, keyLength)
        val macKey = keys.copyOfRange(keyLength, keyLength + 32)
        if (!MessageDigest.isEqual(expectedMac, hmac(macKey, payload, 0, payload.size - MAC_LEN))) return null
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey, "AES"), IvParameterSpec(iv))
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
        if (payload.size < PREFIX_LEN + MAC_LEN + 16) return null
        if (payload[0] != VERSION) return null
        val recorded = when (payload[1]) {
            KDF_ID_AES256 -> 32
            KDF_ID_LEGACY -> null
            else -> return null
        }
        val salt = payload.copyOfRange(HEADER_LEN, HEADER_LEN + SALT_LEN)
        val expectedMac = payload.copyOfRange(payload.size - MAC_LEN, payload.size)
        val sizes = if (recorded != null) intArrayOf(recorded) else KEY_SIZES
        for (keyLength in sizes) {
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

object ChaCha20Poly1305 {

    /** Envelope version. Public so the Universal Decoder can identify the format structurally. */
    const val VERSION: Byte = 0x03
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

    /** Structural check: version byte, KDF id and the documented minimum length for this layout. */
    fun looksLikeEnvelope(payloadBase64: String): Boolean {
        val bytes = try {
            Base64Codec.decode(payloadBase64.trim())
        } catch (e: Exception) {
            return false
        }
        if (bytes.size < PREFIX_LEN + 16) return false
        if (bytes[0] != VERSION) return false
        return bytes[1] == KDF_ID
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
