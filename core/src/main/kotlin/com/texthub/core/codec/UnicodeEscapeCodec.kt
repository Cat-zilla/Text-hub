package com.texthub.core.codec

import com.texthub.core.model.Errors
import com.texthub.core.util.hexDigit

/**
 * Escape sequences used in source code.
 *
 *  - "js"    : \uXXXX, and surrogate pairs for characters above U+FFFF (\uD83D\uDE00)
 *  - "code"  : \uXXXX for the basic plane, \u{1F600} beyond it (C++, Rust, modern JS)
 *  - "cp"    : \uXXXX, \U0001F600 (Python)
 *
 * Decoding accepts every form above plus \xXX and \0-style short escapes for ASCII.
 */
object UnicodeEscapeCodec {

    const val MODE_JS = "js"
    const val MODE_BRACED = "braced"
    const val MODE_PYTHON = "python"

    fun encode(text: String, mode: String): String {
        val sb = StringBuilder(text.length * 2)
        var i = 0
        while (i < text.length) {
            val cp = Character.codePointAt(text, i)
            i += Character.charCount(cp)
            when {
                cp == '\\'.code -> sb.append("\\\\")
                cp == '\n'.code -> sb.append("\\n")
                cp == '\r'.code -> sb.append("\\r")
                cp == '\t'.code -> sb.append("\\t")
                cp in 0x20..0x7E -> sb.append(cp.toChar())
                cp <= 0xFFFF -> sb.append(hex(cp, 4))
                mode == MODE_BRACED -> sb.append("\\u{").append(cp.toString(16).uppercase()).append('}')
                mode == MODE_PYTHON -> sb.append("\\U").append(cp.toString(16).uppercase().padStart(8, '0'))
                else -> {
                    // UTF-16 surrogate pair
                    val chars = Character.toChars(cp)
                    sb.append(hex(chars[0].code, 4)).append(hex(chars[1].code, 4))
                }
            }
        }
        return sb.toString()
    }

    fun decode(input: String): String {
        val sb = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c != '\\' || i + 1 >= input.length) {
                sb.append(c)
                i++
                continue
            }
            when (val next = input[i + 1]) {
                '\\' -> { sb.append('\\'); i += 2 }
                'n' -> { sb.append('\n'); i += 2 }
                'r' -> { sb.append('\r'); i += 2 }
                't' -> { sb.append('\t'); i += 2 }
                'b' -> { sb.append('\b'); i += 2 }
                'f' -> { sb.append('\u000C'); i += 2 }
                'u' -> {
                    if (i + 2 < input.length && input[i + 2] == '{') {
                        val close = input.indexOf('}', i + 3)
                        if (close < 0) throw Errors.unicodeEscape()
                        val value = input.substring(i + 3, close).toIntOrNull(16) ?: throw Errors.unicodeEscape()
                        sb.appendCodePoint(validCodePoint(value))
                        i = close + 1
                    } else {
                        if (i + 5 >= input.length) throw Errors.unicodeEscape()
                        val value = parseHex(input, i + 2, 4)
                        if (value in 0xD800..0xDBFF && i + 11 < input.length + 1 &&
                            input.getOrNull(i + 6) == '\\' && input.getOrNull(i + 7) == 'u'
                        ) {
                            val low = parseHex(input, i + 8, 4)
                            if (low in 0xDC00..0xDFFF) {
                                val cp = Character.toCodePoint(value.toChar(), low.toChar())
                                sb.appendCodePoint(cp)
                                i += 12
                                continue
                            }
                        }
                        sb.appendCodePoint(validCodePoint(value))
                        i += 6
                    }
                }
                'U' -> {
                    if (i + 9 >= input.length) throw Errors.unicodeEscape()
                    val value = parseHex(input, i + 2, 8)
                    sb.appendCodePoint(validCodePoint(value))
                    i += 10
                }
                'x' -> {
                    if (i + 3 >= input.length) throw Errors.unicodeEscape()
                    if (i + 5 < input.length && isHex(input[i + 2]) && isHex(input[i + 3]) &&
                        isHex(input.getOrNull(i + 4)) && isHex(input.getOrNull(i + 5))
                    ) {
                        // \x{...} style is not standard; treat two digits as one byte
                        sb.append(parseHex(input, i + 2, 2).toChar()); i += 4
                    } else {
                        sb.append(parseHex(input, i + 2, 2).toChar()); i += 4
                    }
                }
                '0' -> { sb.append('\u0000'); i += 2 }
                else -> {
                    if (isHex(next) && i + 3 < input.length && isHex(input[i + 2]) && isHex(input[i + 3])) {
                        sb.append(parseHex(input, i + 1, 3).toChar()); i += 4
                    } else {
                        sb.append(c); i++
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun hex(value: Int, digits: Int): String =
        "\\u" + value.toString(16).uppercase().padStart(digits, '0')

    private fun parseHex(text: String, start: Int, count: Int): Int {
        var value = 0
        for (k in 0 until count) {
            val d = hexDigit(text[start + k])
            if (d < 0) throw Errors.unicodeEscape()
            value = (value shl 4) or d
        }
        return value
    }

    private fun isHex(c: Char?): Boolean = c != null && hexDigit(c) >= 0

    private fun validCodePoint(cp: Int): Int {
        if (cp < 0 || cp > 0x10FFFF || cp in 0xD800..0xDFFF) throw Errors.unicodeEscape()
        return cp
    }
}
