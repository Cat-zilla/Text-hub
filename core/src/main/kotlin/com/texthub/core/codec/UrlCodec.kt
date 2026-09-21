package com.texthub.core.codec

import com.texthub.core.model.Errors
import com.texthub.core.util.hexDigit
import com.texthub.core.util.utf8Bytes

/**
 * Percent encoding following RFC 3986.
 *
 * Two variants:
 *  - "standard": every byte outside the unreserved set (A-Z a-z 0-9 - _ . ~) becomes %XX.
 *  - "form"    : same, but a space becomes '+' (application/x-www-form-urlencoded style).
 *
 * Unicode is encoded as UTF-8 bytes, so "é" becomes %C3%A9 and "→" becomes %E2%86%92.
 */
object UrlCodec {

    private val UNRESERVED = buildSet {
        for (c in 'a'..'z') add(c)
        for (c in 'A'..'Z') add(c)
        for (c in '0'..'9') add(c)
        add('-'); add('_'); add('.'); add('~')
    }

    fun encode(text: String, form: Boolean): String {
        val sb = StringBuilder(text.length + 16)
        // Iterate by code point so surrogate pairs (emoji and friends) stay intact.
        var i = 0
        while (i < text.length) {
            val cp = Character.codePointAt(text, i)
            i += Character.charCount(cp)
            val str = StringBuilder().appendCodePoint(cp).toString()
            if (cp < 0x80) {
                val ch = cp.toChar()
                when {
                    ch in UNRESERVED -> sb.append(ch)
                    form && ch == ' ' -> sb.append('+')
                    else -> appendEscaped(sb, str)
                }
            } else {
                appendEscaped(sb, str)
            }
        }
        return sb.toString()
    }

    private fun appendEscaped(sb: StringBuilder, raw: String) {
        for (b in raw.utf8Bytes()) {
            val v = b.toInt() and 0xFF
            sb.append('%')
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0F])
        }
    }

    fun decode(input: String, form: Boolean): String {
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c == '%' -> {
                    if (i + 2 >= input.length) throw Errors.url()
                    val hi = hexDigit(input[i + 1])
                    val lo = hexDigit(input[i + 2])
                    if (hi < 0 || lo < 0) throw Errors.url()
                    out.write((hi shl 4) or lo)
                    i += 3
                }
                form && c == '+' -> {
                    out.write(' '.code)
                    i++
                }
                else -> {
                    for (b in c.toString().utf8Bytes()) out.write(b.toInt() and 0xFF)
                    i++
                }
            }
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    private const val HEX = "0123456789ABCDEF"
}
