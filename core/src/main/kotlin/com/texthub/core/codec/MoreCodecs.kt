package com.texthub.core.codec

import com.texthub.core.model.Errors

/**
 * Punycode (RFC 3492) - the encoding used by internationalised domain names (IDN).
 *
 * Pure ASCII text is returned unchanged apart from a trailing separator, and text with non-ASCII
 * characters keeps the ASCII part readable and encodes the rest as a short delta sequence.
 */
object PunycodeCodec {

    private const val BASE = 36
    private const val TMIN = 1
    private const val TMAX = 26
    private const val SKEW = 38
    private const val DAMP = 700
    private const val INITIAL_BIAS = 72
    private const val INITIAL_N = 128
    private const val MAX_CODE_POINT = 0x10FFFF

    fun encode(text: String): String {
        val codePoints = text.codePoints().toArray().toList()
        val basic = codePoints.filter { it < INITIAL_N }
        val out = StringBuilder()
        basic.forEach { out.appendCodePoint(it) }
        if (basic.isNotEmpty()) out.append('-')

        var handled = basic.size
        var n = INITIAL_N
        var delta = 0L
        var bias = INITIAL_BIAS
        while (handled < codePoints.size) {
            val next = codePoints.filter { it >= n }.minOrNull() ?: break
            delta += (next - n).toLong() * (handled + 1)
            n = next
            for (cp in codePoints) {
                if (cp < n) delta++
                if (cp == n) {
                    var q = delta
                    var k = BASE
                    while (true) {
                        val t = threshold(k, bias)
                        if (q < t) break
                        out.append(digit(t + ((q - t) % (BASE - t)).toInt()))
                        q = (q - t) / (BASE - t)
                        k += BASE
                    }
                    out.append(digit(q.toInt()))
                    bias = adapt(delta, handled + 1, handled == basic.size)
                    delta = 0
                    handled++
                }
            }
            delta++
            n++
        }
        return out.toString()
    }

    fun decode(text: String): String {
        val input = text.trim()
        if (input.isEmpty()) return ""
        val lastSeparator = input.lastIndexOf('-')
        val out = ArrayList<Int>()
        var position = 0
        if (lastSeparator >= 0) {
            input.substring(0, lastSeparator).forEach { out.add(it.code) }
            position = lastSeparator + 1
        }
        var n = INITIAL_N
        var i = 0L
        var bias = INITIAL_BIAS
        while (position < input.length) {
            val oldi = i
            var w = 1L
            var k = BASE
            while (true) {
                if (position >= input.length) throw Errors.punycode()
                val digit = decodeDigit(input[position++])
                if (digit < 0) throw Errors.punycode()
                i += digit * w
                if (i > Int.MAX_VALUE.toLong()) throw Errors.punycode()
                val t = threshold(k, bias)
                if (digit < t) break
                w *= (BASE - t)
                if (w > Int.MAX_VALUE.toLong()) throw Errors.punycode()
                k += BASE
            }
            bias = adapt(i - oldi, out.size + 1, oldi == 0L)
            val step = (i / (out.size + 1)).toInt()
            // Both the code point and the running total are checked here rather than after the
            // list has been built: an out-of-range value would otherwise become an
            // IllegalArgumentException from appendCodePoint, which is not a ToolException and so
            // used to be reported to the user as a problem with their cipher settings.
            if (step < 0 || n > MAX_CODE_POINT - step) throw Errors.punycode()
            n += step
            if (!isScalarValue(n)) throw Errors.punycode()
            i %= (out.size + 1)
            if (i > out.size) throw Errors.punycode()
            out.add(i.toInt(), n)
            i++
        }
        return buildString { out.forEach { appendCodePoint(it) } }
    }

    /** True for a Unicode scalar value: in range and not a lone surrogate. */
    private fun isScalarValue(codePoint: Int): Boolean =
        codePoint in 0..MAX_CODE_POINT && codePoint !in 0xD800..0xDFFF

    private fun threshold(k: Int, bias: Int): Int = when {
        k <= bias + TMIN -> TMIN
        k >= bias + TMAX -> TMAX
        else -> k - bias
    }

    private fun adapt(delta: Long, numPoints: Int, firstTime: Boolean): Int {
        var d = if (firstTime) delta / DAMP else delta / 2
        d += d / numPoints
        var k = 0
        while (d > ((BASE - TMIN) * TMAX) / 2) {
            d /= (BASE - TMIN)
            k += BASE
        }
        return k + (((BASE - TMIN + 1) * d) / (d + SKEW)).toInt()
    }

    private fun digit(value: Int): Char = when {
        value < 26 -> ('a'.code + value).toChar()
        value < 36 -> ('0'.code + value - 26).toChar()
        else -> throw Errors.punycode()
    }

    private fun decodeDigit(c: Char): Int = when {
        c in 'a'..'z' -> c - 'a'
        c in 'A'..'Z' -> c - 'A'
        c in '0'..'9' -> c - '0' + 26
        else -> -1
    }
}

/**
 * UTF-16 code unit view: each 16-bit unit of the text is shown separately, which is how most
 * programming languages store strings. Astral characters appear as a surrogate pair.
 */
object Utf16Codec {

    const val FORMAT_HEX = "hex"
    const val FORMAT_DECIMAL = "decimal"
    const val FORMAT_CSHARP = "cstring"

    fun encode(text: String, format: String): String = when (format) {
        FORMAT_DECIMAL -> text.map { (it.code and 0xFFFF).toString() }.joinToString(" ")
        FORMAT_CSHARP -> text.map { (it.code and 0xFFFF).toString(16).uppercase().padStart(4, '0') }
            .joinToString("") { "\\u$it" }
        else -> text.map { (it.code and 0xFFFF).toString(16).uppercase().padStart(4, '0') }.joinToString(" ")
    }

    fun decode(text: String, format: String): String {
        val units = when (format) {
            FORMAT_DECIMAL -> decimalUnits(text)
            FORMAT_CSHARP -> Regex("\\\\u([0-9a-fA-F]{1,4})").findAll(text)
                .map { it.groupValues[1].toInt(16) }.toList()
            else -> hexUnits(text, width = 4, example = "0041 0042")
        }
        if (units.isEmpty()) throw Errors.unicodeEscape()
        val sb = StringBuilder()
        for (unit in units) {
            if (unit !in 0..0xFFFF) throw Errors.unicodeEscape()
            sb.append(unit.toChar())
        }
        return sb.toString()
    }
}

/** UTF-32 code point view: every Unicode code point on its own, so astral characters are one value. */
object Utf32Codec {

    const val FORMAT_HEX = "hex"
    const val FORMAT_DECIMAL = "decimal"
    const val FORMAT_CSHARP = "unicode"

    fun encode(text: String, format: String): String = when (format) {
        FORMAT_DECIMAL -> text.codePoints().toArray().joinToString(" ") { it.toString() }
        FORMAT_CSHARP -> text.codePoints().toArray().joinToString("") {
            "\\U" + it.toString(16).uppercase().padStart(8, '0')
        }
        else -> text.codePoints().toArray().joinToString(" ") { it.toString(16).uppercase().padStart(8, '0') }
    }

    fun decode(text: String, format: String): String {
        val points = when (format) {
            FORMAT_DECIMAL -> decimalUnits(text)
            FORMAT_CSHARP -> Regex("\\\\U([0-9a-fA-F]{1,8})").findAll(text)
                .map { it.groupValues[1].toInt(16) }.toList()
            else -> hexUnits(text, width = 8, example = "00000041 00000042")
        }
        if (points.isEmpty()) throw Errors.unicodeEscape()
        points.forEach { if (it !in 0..0x10FFFF) throw Errors.unicodeEscape() }
        return buildString { points.forEach { appendCodePoint(it) } }
    }
}

/**
 * Braille cells (Unicode U+2800 … U+28FF) used as an eight-bit display.
 *
 * The Unicode Braille Patterns block maps dot 1 … dot 8 onto bits 0 … 7, so a cell is a perfect
 * eight-bit lamp panel. Each 16-bit code unit of the text is shown as four cells, one hexadecimal
 * nibble each, which makes the result exact and reversible - and deliberately *not* literary
 * (contracted) Braille.
 */
object BrailleCodec {

    private const val BASE = 0x2800

    fun encode(text: String): String {
        val sb = StringBuilder()
        text.forEach { c ->
            val value = c.code
            // Four cells per code unit, most significant nibble first.
            sb.append((BASE + ((value shr 12) and 0xF)).toChar())
            sb.append((BASE + ((value shr 8) and 0xF)).toChar())
            sb.append((BASE + ((value shr 4) and 0xF)).toChar())
            sb.append((BASE + (value and 0xF)).toChar())
        }
        return sb.toString()
    }

    fun decode(text: String): String {
        val cells = text.filter { it.code in BASE..(BASE + 0xFF) }.map { it.code - BASE }
        if (cells.isEmpty()) throw Errors.braille()
        if (cells.size % 4 != 0) throw Errors.braille()
        return cells.chunked(4)
            .map { group -> (group[0] shl 12) or (group[1] shl 8) or (group[2] shl 4) or group[3] }
            .joinToString("") { codeUnit -> codeUnit.toChar().toString() }
    }
}

/** Roman numerals, validated strictly on the way back so nonsense is rejected politely. */
object RomanCodec {

    private val VALUES = listOf(
        1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC",
        50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I",
    )

    private val LETTERS = "IVXLCDM"

    fun toRoman(value: Int): String {
        if (value !in 1..3999) throw Errors.romanRange()
        var remaining = value
        val sb = StringBuilder()
        for ((amount, symbol) in VALUES) {
            while (remaining >= amount) {
                sb.append(symbol)
                remaining -= amount
            }
        }
        return sb.toString()
    }

    /** Parses a strictly valid Roman numeral between I and MMMCMXCIX. */
    fun fromRoman(text: String): Int {
        val roman = text.uppercase()
        if (roman.isEmpty() || roman.any { it !in LETTERS }) throw Errors.roman()
        var total = 0
        var previous = 0
        for (i in roman.indices.reversed()) {
            val value = valueOf(roman[i])
            if (value == 0) throw Errors.roman()
            total += if (value < previous) -value else value
            previous = maxOf(previous, value)
        }
        if (total !in 1..3999) throw Errors.romanRange()
        // Only canonical forms are accepted: IIII, VX and friends are rejected.
        if (toRoman(total) != roman) throw Errors.roman()
        return total
    }

    fun looksRoman(token: String): Boolean =
        token.isNotEmpty() && token.all { it.uppercaseChar() in LETTERS }

    private fun valueOf(c: Char): Int = when (c) {
        'I' -> 1
        'V' -> 5
        'X' -> 10
        'L' -> 50
        'C' -> 100
        'D' -> 500
        'M' -> 1000
        else -> 0
    }
}

/** Classic `hexdump -C` style output, with the ASCII gutter, and a tolerant reader for it. */
object HexDumpCodec {

    fun encode(text: String): String {
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.isEmpty()) return ""
        val sb = StringBuilder()
        var offset = 0
        while (offset < bytes.size) {
            val count = minOf(16, bytes.size - offset)
            sb.append(offset.toString(16).padStart(8, '0')).append(": ")
            for (i in 0 until 16) {
                if (i < count) {
                    sb.append((bytes[offset + i].toInt() and 0xFF).toString(16).padStart(2, '0')).append(' ')
                } else {
                    sb.append("   ")
                }
            }
            sb.append('|')
            for (i in 0 until count) {
                val value = bytes[offset + i].toInt() and 0xFF
                sb.append(if (value in 32..126) value.toChar() else '.')
            }
            sb.append("|\n")
            offset += count
        }
        return sb.toString().trimEnd('\n')
    }

    /** Reads a dump back. Lines without a valid offset or byte column are skipped. */
    fun decode(text: String): String {
        val out = java.io.ByteArrayOutputStream()
        var rows = 0
        for (rawLine in text.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val body = line.substringAfter(':', "")
            if (body.isEmpty()) continue
            // Bytes are the hex pairs up to the ASCII gutter or the first non-hex token.
            val hexPart = body.substringBefore('|')
            val tokens = hexPart.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            var read = 0
            for (token in tokens) {
                if (token.length > 2 || token.toIntOrNull(16) == null) break
                out.write(token.toInt(16))
                read++
            }
            if (read > 0) rows++
        }
        if (rows == 0) throw Errors.hexDump()
        return out.toByteArray().toString(Charsets.UTF_8)
    }
}

/**
 * Parses hexadecimal UTF-16 / UTF-32 units. Every token must be exactly [width] hex digits, which
 * is what these formats look like everywhere (and what this app writes). A decimal payload left on
 * Format = Hex therefore fails loudly instead of quietly producing the wrong characters, which is
 * what used to happen when 3-digit decimal tokens were read as hex.
 */
private fun hexUnits(text: String, width: Int, example: String): List<Int> {
    val tokens = Regex("\\S+").findAll(text).map { it.value }.toList()
    if (tokens.isEmpty()) throw Errors.unicodeEscape()
    val looksHex = tokens.all { token ->
        token.length == width && token.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
    }
    if (!looksHex) {
        throw com.texthub.core.model.ToolException(
            "These are not $width-digit hexadecimal units, so the Format setting does not match " +
                "the payload. Expected something like \"$example\"; if the values are plain " +
                "numbers, set Format to Decimal."
        )
    }
    return tokens.map { it.toIntOrNull(16) ?: throw Errors.unicodeEscape() }
}

/**
 * Parses decimal UTF-16 / UTF-32 units. Every part must be a plain number without leading zeros -
 * which is how decimal code-point lists are written everywhere, including here. A hexadecimal
 * payload (whose units are zero padded and often contain letters) is therefore refused with a
 * pointer to the format setting instead of being read as decimal numbers.
 */
private fun decimalUnits(text: String): List<Int> {
    val tokens = Regex("\\S+").findAll(text).map { it.value }.toList()
    if (tokens.isEmpty()) throw Errors.unicodeEscape()
    if (tokens.any { token -> token.any { !it.isDigit() } }) {
        throw com.texthub.core.model.ToolException(
            "This payload is not a list of decimal numbers, so the Format setting does not match it. " +
                "If it contains letters or \\u escapes, choose the matching format instead of Decimal."
        )
    }
    if (tokens.any { it.length > 1 && it.startsWith("0") }) {
        throw com.texthub.core.model.ToolException(
            "These are zero-padded units, which is how hexadecimal code points look (for example " +
                "\"0068\"). Set Format to Hex to read this payload."
        )
    }
    return tokens.map { it.toIntOrNull() ?: throw Errors.unicodeEscape() }
}
