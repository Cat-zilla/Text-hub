package com.texthub.core.codec

/**
 * HTML entity encoding.
 *
 * When encoding, everything outside printable ASCII is escaped numerically (&#233;) and the
 * five HTML special characters use their named entities, so the result is safe to paste into
 * markup. Decoding understands named entities, decimal (&#233;) and hexadecimal (&#xE9;)
 * numeric references.
 */
object HtmlEntityCodec {

    private val NAMED = linkedMapOf(
        "amp" to '&', "lt" to '<', "gt" to '>', "quot" to '"', "apos" to '\'',
        "nbsp" to '\u00A0', "copy" to '\u00A9', "reg" to '\u00AE', "trade" to '\u2122',
        "deg" to '\u00B0', "plusmn" to '\u00B1', "micro" to '\u00B5', "para" to '\u00B6',
        "sect" to '\u00A7', "middot" to '\u00B7', "laquo" to '\u00AB', "raquo" to '\u00BB',
        "frac12" to '\u00BD', "frac14" to '\u00BC', "frac34" to '\u00BE',
        "times" to '\u00D7', "divide" to '\u00F7', "ndash" to '\u2013', "mdash" to '\u2014',
        "lsquo" to '\u2018', "rsquo" to '\u2019', "ldquo" to '\u201C', "rdquo" to '\u201D',
        "bull" to '\u2022', "hellip" to '\u2026', "prime" to '\u2032',
        "larr" to '\u2190', "uarr" to '\u2191', "rarr" to '\u2192', "darr" to '\u2193',
        "harr" to '\u2194', "euro" to '\u20AC', "pound" to '\u00A3', "yen" to '\u00A5',
        "cent" to '\u00A2', "curren" to '\u00A4', "infin" to '\u221E', "ne" to '\u2260',
        "le" to '\u2264', "ge" to '\u2265', "sum" to '\u2211', "prod" to '\u220F',
        "radic" to '\u221A', "alpha" to '\u03B1', "beta" to '\u03B2', "gamma" to '\u03B3',
        "delta" to '\u03B4', "pi" to '\u03C0', "sigma" to '\u03C3', "omega" to '\u03C9',
        "check" to '\u2713', "star" to '\u2605', "hearts" to '\u2665', "sung" to '\u266A',
    )

    private val REVERSE: Map<Char, String> = NAMED.entries.associate { (name, ch) -> ch to "&$name;" }

    /** Special characters that must always be escaped, even in "minimal" mode. */
    private val MUST_ESCAPE = mapOf('&' to "&amp;", '<' to "&lt;", '>' to "&gt;", '"' to "&quot;")

    const val MODE_MINIMAL = "minimal"
    const val MODE_ALL = "all"

    fun encode(text: String, mode: String): String {
        val sb = StringBuilder(text.length + 16)
        var i = 0
        while (i < text.length) {
            val cp = Character.codePointAt(text, i)
            i += Character.charCount(cp)
            val asChar = if (cp <= 0xFFFF) cp.toChar() else null
            when {
                asChar != null && asChar in MUST_ESCAPE -> sb.append(MUST_ESCAPE[asChar])
                asChar != null && mode == MODE_ALL && REVERSE.containsKey(asChar) -> sb.append(REVERSE[asChar])
                cp in 0x20..0x7E -> sb.append(cp.toChar())
                cp == '\n'.code -> sb.append('\n')
                cp == '\t'.code -> sb.append('\t')
                else -> sb.append("&#").append(cp).append(';')
            }
        }
        return sb.toString()
    }

    fun decode(input: String): String {
        val sb = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c != '&') {
                sb.append(c)
                i++
                continue
            }
            val end = input.indexOf(';', i + 1)
            if (end < 0 || end - i > 12) {
                sb.append(c)
                i++
                continue
            }
            val body = input.substring(i + 1, end)
            val resolved: String? = when {
                body.startsWith("#x") || body.startsWith("#X") ->
                    body.substring(2).toIntOrNull(16)?.let { cpToString(it) }
                body.startsWith("#") ->
                    body.substring(1).toIntOrNull()?.let { cpToString(it) }
                else -> NAMED[body.lowercase()]?.toString()
            }
            if (resolved == null) {
                sb.append(c)
                i++
            } else {
                sb.append(resolved)
                i = end + 1
            }
        }
        return sb.toString()
    }

    private fun cpToString(cp: Int): String? {
        if (cp < 0 || cp > 0x10FFFF || cp in 0xD800..0xDFFF) return null
        return StringBuilder().appendCodePoint(cp).toString()
    }
}
