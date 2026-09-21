package com.texthub.core.crypto

import com.texthub.core.model.Errors
import com.texthub.core.util.toHex
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * One-way cryptographic digests from the platform provider.
 *
 * A hash is *not* encryption: it cannot be reversed. Cracking a hash means guessing the input and
 * hashing it again, which is why passwords must be stored with [Pbkdf2Hash] rather than a plain
 * digest.
 */
object Digests {

    /** MD5 and SHA-1 are provided for checking old file checksums only - never for security. */
    val ALGORITHMS = listOf("MD5", "SHA-1", "SHA-256", "SHA-384", "SHA-512", "SHA3-256", "SHA3-512")

    fun available(algorithm: String): Boolean = try {
        MessageDigest.getInstance(algorithm)
        true
    } catch (e: java.security.NoSuchAlgorithmException) {
        false
    }

    fun hash(text: String, algorithm: String): ByteArray =
        try {
            MessageDigest.getInstance(algorithm).digest(text.toByteArray(Charsets.UTF_8))
        } catch (e: java.security.NoSuchAlgorithmException) {
            throw Errors.cipherParams()
        }

    fun hashHex(text: String, algorithm: String): String = hash(text, algorithm).toHex(upper = false)
}

/** Keyed message authentication: HMAC over the message with a shared secret key. */
object Hmacs {

    val ALGORITHMS = listOf("HmacMD5", "HmacSHA1", "HmacSHA256", "HmacSHA384", "HmacSHA512")

    fun compute(message: String, key: String, algorithm: String): ByteArray =
        try {
            val mac = Mac.getInstance(algorithm)
            mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), algorithm))
            mac.doFinal(message.toByteArray(Charsets.UTF_8))
        } catch (e: java.security.NoSuchAlgorithmException) {
            throw Errors.cipherParams()
        } catch (e: java.security.InvalidKeyException) {
            throw Errors.missingKey()
        }

    /**
     * Verifies a tag. Comparison is constant time, so a wrong tag cannot be found byte by byte.
     * When [message] is not available and only the digest was kept, this only works because the
     * caller still has the message - which is the point of a MAC.
     */
    fun verify(message: String, key: String, algorithm: String, expectedHex: String): Boolean {
        val trimmed = expectedHex.trim().lowercase()
        if (trimmed.isEmpty() || trimmed.length % 2 != 0) throw Errors.hmacFormat()
        val expected = ByteArray(trimmed.length / 2) { i ->
            val hi = com.texthub.core.util.hexDigit(trimmed[i * 2])
            val lo = com.texthub.core.util.hexDigit(trimmed[i * 2 + 1])
            if (hi < 0 || lo < 0) throw Errors.hmacFormat()
            ((hi shl 4) or lo).toByte()
        }
        return MessageDigest.isEqual(expected, compute(message, key, algorithm))
    }
}

/**
 * PBKDF2 password hashing with a stored salt and iteration count - the correct way to keep a
 * password checkable without keeping the password.
 *
 * Stored format (all fields separated by `$`):
 *
 * ```
 * pbkdf2-sha256$210000$<base64 salt>$<base64 hash>
 * ```
 *
 * Verification re-derives the hash from the supplied password with the stored salt and iteration
 * count and compares in constant time. Salts are 16 random bytes per password.
 */
object Pbkdf2Hash {

    private const val ITERATIONS = 210_000
    private const val SALT_LEN = 16
    private const val KEY_LEN_BITS = 256
    private const val PREFIX = "pbkdf2-sha256"

    val ALGORITHMS = mapOf(
        "PBKDF2WithHmacSHA1" to "pbkdf2-sha1",
        "PBKDF2WithHmacSHA256" to "pbkdf2-sha256",
        "PBKDF2WithHmacSHA512" to "pbkdf2-sha512",
    )

    private val random = SecureRandom()

    fun store(password: CharArray, algorithm: String = "PBKDF2WithHmacSHA256"): String {
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val tag = PREFIX.takeIf { algorithm == "PBKDF2WithHmacSHA256" }
            ?: ALGORITHMS.getValue(algorithm)
        val hash = derive(password, salt, ITERATIONS, algorithm)
        return "$tag\$$ITERATIONS\$${b64(salt)}\$${b64(hash)}"
    }

    /** Returns true when [password] reproduces the stored hash. Never reveals which part failed. */
    fun verify(password: CharArray, stored: String): Boolean {
        val parts = stored.trim().split("$")
        if (parts.size != 4) throw Errors.pbkdf2Format()
        val algorithm = ALGORITHMS.entries.firstOrNull { it.value == parts[0] }?.key
            ?: throw Errors.pbkdf2Format()
        val iterations = parts[1].toIntOrNull()?.takeIf { it in 1..10_000_000 } ?: throw Errors.pbkdf2Format()
        val salt = unb64(parts[2])
        val expected = unb64(parts[3])
        if (salt.isEmpty() || expected.isEmpty()) throw Errors.pbkdf2Format()
        val actual = derive(password, salt, iterations, algorithm, expected.size * 8)
        return MessageDigest.isEqual(expected, actual)
    }

    /** Formats the stored string with grouped lines so it is easier to read aloud or copy. */
    fun describe(stored: String): String {
        val parts = stored.split("$")
        if (parts.size != 4) return stored
        return buildString {
            append(parts[0]).append(" (one-way password hash)\n")
            append("iterations: ").append(parts[1]).append('\n')
            append("salt (Base64): ").append(parts[2]).append('\n')
            append("hash (Base64): ").append(parts[3])
        }
    }

    private fun derive(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        algorithm: String,
        keyBits: Int = KEY_LEN_BITS,
    ): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, keyBits)
        return try {
            SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded
        } catch (e: java.security.NoSuchAlgorithmException) {
            throw Errors.cipherParams()
        } finally {
            spec.clearPassword()
        }
    }

    private fun b64(data: ByteArray): String = com.texthub.core.codec.Base64Codec.encode(data).trimEnd('=')

    private fun unb64(text: String): ByteArray = try {
        val padding = (4 - text.length % 4) % 4
        com.texthub.core.codec.Base64Codec.decode(text + "=".repeat(padding))
    } catch (e: Exception) {
        throw Errors.pbkdf2Format()
    }
}
