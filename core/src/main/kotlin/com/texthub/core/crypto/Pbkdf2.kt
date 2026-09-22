package com.texthub.core.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * PBKDF2 with HMAC-SHA256 (RFC 8018).
 *
 * Implemented directly on top of [Mac] because Android only guarantees
 * "PBKDF2WithHmacSHA256" in SecretKeyFactory from newer API levels, while HmacSHA256 is
 * available everywhere. This also keeps the KDF identical on Android and on the JVM, so the
 * unit tests can verify it against the platform implementation.
 *
 * The password char array is cleared after use and is never logged.
 */
object Pbkdf2 {

    /** The digest used by HMAC in the KDF. SHA-256 is what Text Hub uses for its own payloads. */
    enum class Digest(val jceName: String, val label: String) {
        SHA256("HmacSHA256", "SHA-256"),
        SHA512("HmacSHA512", "SHA-512"),
        SHA1("HmacSHA1", "SHA-1"),
        MD5("HmacMD5", "MD5"),
    }

    fun derive(password: CharArray, salt: ByteArray, iterations: Int, keyLengthBytes: Int): ByteArray =
        derive(Digest.SHA256, password, salt, iterations, keyLengthBytes)

    /**
     * PBKDF2 with a selectable HMAC digest. Directories such as the OpenSSL `enc` format derive
     * their key with the digest the file was written with, which is why the digest is a parameter.
     */
    fun derive(
        digest: Digest,
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        keyLengthBytes: Int,
    ): ByteArray {
        require(iterations > 0) { "iterations must be positive" }
        require(keyLengthBytes > 0) { "keyLength must be positive" }
        val mac = Mac.getInstance(digest.jceName)
        mac.init(SecretKeySpec(password.concatToString().toByteArray(Charsets.UTF_8), digest.jceName))

        val hLen = mac.macLength
        val l = (keyLengthBytes + hLen - 1) / hLen
        val r = keyLengthBytes - (l - 1) * hLen
        val out = ByteArray(keyLengthBytes)
        var offset = 0

        val u = ByteArray(hLen)
        val t = ByteArray(hLen)

        for (block in 1..l) {
            // U1 = PRF(P, S || INT_32_BE(block))
            mac.update(salt)
            mac.update(byteArrayOf((block ushr 24).toByte(), (block ushr 16).toByte(), (block ushr 8).toByte(), block.toByte()))
            mac.doFinal(u, 0)
            System.arraycopy(u, 0, t, 0, hLen)

            for (i in 2..iterations) {
                mac.update(u)
                mac.doFinal(u, 0)
                for (j in 0 until hLen) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
            }
            val toCopy = if (block == l) r else hLen
            System.arraycopy(t, 0, out, offset, toCopy)
            offset += toCopy
        }
        return out
    }
}
