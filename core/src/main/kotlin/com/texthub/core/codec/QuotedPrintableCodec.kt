package com.texthub.core.codec

import com.texthub.core.model.Errors
import com.texthub.core.util.hexDigit
import com.texthub.core.util.utf8Bytes

/**
 * Quoted-Printable (RFC 2045), the encoding email bodies and headers still use.
 *
 *  - bytes 33-126 except '=' are literal,
 *  - '=' is always escaped as =3D,
 *  - space and tab are escaped when they end a line,
 *  - anything else, including all non-ASCII bytes, becomes =XX,
 *  - lines are soft-wrapped with '=' at exactly 76 characters.
 */
object QuotedPrintableCodec {

    private const val LINE_LIMIT = 76
    private const val HEX = "0123456789ABCDEF"

    fun encode(text: String): String {
        val sb = StringBuilder()
        var lineLength = 0
        val bytes = text.utf8Bytes()

        fun push(chunk: String) {
            if (lineLength + chunk.length > LINE_LIMIT - 1) {
                sb.append('=').append('\n')
                lineLength = 0
            }
            sb.append(chunk)
            lineLength += chunk.length
        }

        for ((index, b) in bytes.withIndex()) {
            val v = b.toInt() and 0xFF
            val nextIsBreak = index + 1 >= bytes.size ||
                bytes[index + 1].toInt() == '\n'.code ||
                bytes[index + 1].toInt() == '\r'.code
            when {
                v == '\n'.code -> {
                    sb.append('\n')
                    lineLength = 0
                }
                v == '\r'.code -> Unit // normalised away
                v in 33..126 && v != '='.code -> push(v.toChar().toString())
                (v == ' '.code || v == '\t'.code) && !nextIsBreak -> push(v.toChar().toString())
                else -> push("=" + HEX[v ushr 4] + HEX[v and 0x0F])
            }
        }
        return sb.toString()
    }

    fun decode(input: String): String {
        val normalized = input.replace("\r\n", "\n").replace("\r", "\n")
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < normalized.length) {
            val c = normalized[i]
            when {
                c == '=' -> {
                    if (i + 1 < normalized.length && normalized[i + 1] == '\n') {
                        i += 2 // soft line break
                        continue
                    }
                    if (i + 2 >= normalized.length) throw Errors.quotedPrintable()
                    val hi = hexDigit(normalized[i + 1])
                    val lo = hexDigit(normalized[i + 2])
                    if (hi < 0 || lo < 0) throw Errors.quotedPrintable()
                    out.write((hi shl 4) or lo)
                    i += 3
                }
                else -> {
                    for (b in c.toString().utf8Bytes()) out.write(b.toInt() and 0xFF)
                    i++
                }
            }
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }
}
