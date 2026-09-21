package com.texthub.core.codec

import com.texthub.core.model.Errors

/**
 * Small, dependency-free Base64 implementation.
 *
 * Why not java.util.Base64? It only exists from Android API 26; Text Hub supports API 24+.
 */
object Base64Codec {

    private const val STANDARD = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private const val URL_SAFE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun encode(data: ByteArray, urlSafe: Boolean = false, padding: Boolean = true): String {
        val table = if (urlSafe) URL_SAFE else STANDARD
        val sb = StringBuilder(((data.size + 2) / 3) * 4)
        var i = 0
        while (i < data.size) {
            val remaining = data.size - i
            val b0 = data[i].toInt() and 0xFF
            val b1 = if (remaining > 1) data[i + 1].toInt() and 0xFF else 0
            val b2 = if (remaining > 2) data[i + 2].toInt() and 0xFF else 0
            val triple = (b0 shl 16) or (b1 shl 8) or b2
            sb.append(table[(triple ushr 18) and 0x3F])
            sb.append(table[(triple ushr 12) and 0x3F])
            sb.append(if (remaining > 1) table[(triple ushr 6) and 0x3F] else '=')
            sb.append(if (remaining > 2) table[triple and 0x3F] else '=')
            i += 3
        }
        var out = sb.toString()
        if (!padding) out = out.trimEnd('=')
        return out
    }

    /**
     * Decodes Base64. Whitespace is ignored and missing padding is tolerated, but any
     * character outside the (union of the) alphabets is rejected with a friendly error.
     *
     * If padding *is* present it must be correct (trailing only, at most two characters,
     * and a total length that is a multiple of four).
     */
    fun decode(input: String): ByteArray {
        val cleaned = StringBuilder()
        for (c in input) {
            if (c.isWhitespace()) continue
            if (c == '=' || c in STANDARD || c in URL_SAFE) cleaned.append(c) else throw Errors.base64()
        }
        var s = cleaned.toString()
        val eq = s.indexOf('=')
        if (eq >= 0) {
            if (s.substring(eq).any { it != '=' }) throw Errors.base64()
            val padCount = s.length - eq
            if (padCount > 2) throw Errors.base64()
            s = s.substring(0, eq)
            val total = s.length + padCount
            if (total % 4 != 0 || total == 0) throw Errors.base64()
        }
        if (s.isEmpty()) return ByteArray(0)

        val rem = s.length % 4
        if (rem == 1) throw Errors.base64()
        val outLen = (s.length / 4) * 3 + when (rem) {
            2 -> 1
            3 -> 2
            else -> 0
        }
        val out = ByteArray(outLen)
        var o = 0
        var i = 0
        while (i + 1 < s.length) {
            val c0 = valueOf(s[i])
            val c1 = valueOf(s[i + 1])
            val c2 = if (i + 2 < s.length) valueOf(s[i + 2]) else -1
            val c3 = if (i + 3 < s.length) valueOf(s[i + 3]) else -1
            val triple = (c0 shl 18) or (c1 shl 12) or (if (c2 >= 0) c2 shl 6 else 0) or (if (c3 >= 0) c3 else 0)
            if (o < outLen) out[o++] = (triple ushr 16).toByte()
            if (o < outLen) out[o++] = (triple ushr 8).toByte()
            if (o < outLen) out[o++] = triple.toByte()
            i += 4
        }
        return out
    }

    private fun valueOf(c: Char): Int = when (c) {
        in 'A'..'Z' -> c - 'A'
        in 'a'..'z' -> c - 'a' + 26
        in '0'..'9' -> c - '0' + 52
        '+' , '-' -> 62
        '/' , '_' -> 63
        else -> -1
    }
}
