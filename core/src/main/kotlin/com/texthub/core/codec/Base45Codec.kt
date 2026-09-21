package com.texthub.core.codec

import com.texthub.core.model.Errors

/**
 * Base45 as specified by RFC 9285 (used by EU Digital COVID Certificates and QR codes).
 *
 * Two bytes become three characters (least significant first); a trailing single byte
 * becomes two characters. The alphabet is 45 characters so the result is QR-code friendly.
 */
object Base45Codec {

    const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ \$%*+-./:"

    fun encode(data: ByteArray): String {
        val sb = StringBuilder((data.size / 2 + 1) * 3)
        var i = 0
        while (i < data.size) {
            if (i + 1 < data.size) {
                val n = ((data[i].toInt() and 0xFF) shl 8) + (data[i + 1].toInt() and 0xFF)
                sb.append(ALPHABET[n % 45])
                sb.append(ALPHABET[(n / 45) % 45])
                sb.append(ALPHABET[n / 45 / 45])
                i += 2
            } else {
                val n = data[i].toInt() and 0xFF
                sb.append(ALPHABET[n % 45])
                sb.append(ALPHABET[n / 45])
                i += 1
            }
        }
        return sb.toString()
    }

    fun decode(input: String): ByteArray {
        // Space is one of the 45 symbols, so only line breaks and tabs are ignored as formatting.
        val clean = input.filterNot { it == '\n' || it == '\r' || it == '\t' }
        if (clean.isEmpty()) return ByteArray(0)
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < clean.length) {
            val remaining = clean.length - i
            val take = when {
                remaining >= 3 -> 3
                remaining == 2 -> 2
                else -> throw Errors.base45()
            }
            var n = 0
            var mul = 1
            for (k in 0 until take) {
                val idx = ALPHABET.indexOf(clean[i + k])
                if (idx < 0) throw Errors.base45()
                n += idx * mul
                mul *= 45
            }
            if (take == 3) {
                if (n > 0xFFFF) throw Errors.base45()
                out.write(n ushr 8)
                out.write(n and 0xFF)
            } else {
                if (n > 0xFF) throw Errors.base45()
                out.write(n)
            }
            i += take
        }
        return out.toByteArray()
    }
}
