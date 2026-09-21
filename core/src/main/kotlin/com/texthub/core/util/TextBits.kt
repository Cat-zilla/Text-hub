package com.texthub.core.util

private val HEX_UPPER = "0123456789ABCDEF".toCharArray()
private val HEX_LOWER = "0123456789abcdef".toCharArray()

fun String.utf8Bytes(): ByteArray = this.encodeToByteArray()

fun ByteArray.utf8String(): String = this.decodeToString()

fun ByteArray.toHex(upper: Boolean = true, separator: String = ""): String {
    val table = if (upper) HEX_UPPER else HEX_LOWER
    val sb = StringBuilder(size * (2 + separator.length))
    for (i in indices) {
        if (i > 0 && separator.isNotEmpty()) sb.append(separator)
        val v = this[i].toInt() and 0xFF
        sb.append(table[v ushr 4]).append(table[v and 0x0F])
    }
    return sb.toString()
}

fun ByteArray.toBinary(separator: String = " "): String {
    val sb = StringBuilder(size * (8 + separator.length))
    for (i in indices) {
        if (i > 0 && separator.isNotEmpty()) sb.append(separator)
        val v = this[i].toInt() and 0xFF
        for (b in 7 downTo 0) sb.append(if ((v ushr b) and 1 == 1) '1' else '0')
    }
    return sb.toString()
}

fun ByteArray.toDecimal(separator: String = " "): String =
    joinToString(separator) { (it.toInt() and 0xFF).toString() }

fun ByteArray.toOctal(separator: String = " ", pad: Boolean = true): String =
    joinToString(separator) {
        val v = (it.toInt() and 0xFF).toString(8)
        if (pad) v.padStart(3, '0') else v
    }

fun hexDigit(c: Char): Int = when (c) {
    in '0'..'9' -> c - '0'
    in 'a'..'f' -> c - 'a' + 10
    in 'A'..'F' -> c - 'A' + 10
    else -> -1
}

/** Parses a hex string, ignoring whitespace and optional separators. */
fun parseHex(text: String, allowSeparators: Boolean = true): ByteArray {
    val digits = StringBuilder()
    for (c in text) {
        when {
            hexDigit(c) >= 0 -> digits.append(c)
            c.isWhitespace() -> Unit
            allowSeparators && (c == ',' || c == ':' || c == '-' || c == '_') -> Unit
            c == 'x' || c == 'X' -> Unit // tolerate 0x prefixes
            else -> throw com.texthub.core.model.Errors.hex()
        }
    }
    if (digits.length % 2 != 0) throw com.texthub.core.model.Errors.oddHex()
    val out = ByteArray(digits.length / 2)
    var i = 0
    while (i < digits.length) {
        out[i / 2] = ((hexDigit(digits[i]) shl 4) or hexDigit(digits[i + 1])).toByte()
        i += 2
    }
    return out
}

fun isAscii(text: String): Boolean = text.all { it.code <= 0x7F }

fun isPrintableAscii(c: Char): Boolean = c.code in 0x20..0x7E

/** Letters only, lowercased - used by most classical cipher key handling. */
fun String.lettersOnly(): String = this.filter { it.isLetter() }

fun String.lettersLower(): String = this.filter { it.isLetter() }.lowercase()

fun Int.modPositive(m: Int): Int = ((this % m) + m) % m

fun gcd(a: Int, b: Int): Int {
    var x = kotlin.math.abs(a)
    var y = kotlin.math.abs(b)
    while (y != 0) {
        val t = x % y
        x = y
        y = t
    }
    return x
}

/** Modular inverse via extended Euclid; returns null when it does not exist. */
fun modInverse(a: Int, m: Int): Int? {
    var t = 0
    var newT = 1
    var r = m
    var newR = a.modPositive(m)
    while (newR != 0) {
        val q = r / newR
        val tmpT = t - q * newT
        t = newT
        newT = tmpT
        val tmpR = r - q * newR
        r = newR
        newR = tmpR
    }
    if (r != 1) return null
    return t.modPositive(m)
}
