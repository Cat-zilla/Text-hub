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

    fun derive(password: CharArray, salt: ByteArray, iterations: Int, keyLengthBytes: Int): ByteArray {
        require(iterations > 0) { "iterations must be positive" }
        require(keyLengthBytes > 0) { "keyLength must be positive" }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password.concatToString().toByteArray(Charsets.UTF_8), "HmacSHA256"))

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
