package com.texthub.core.processors

import com.texthub.core.TextProcessor
import com.texthub.core.model.Choice
import com.texthub.core.model.Classification
import com.texthub.core.model.Direction
import com.texthub.core.model.Errors
import com.texthub.core.model.ParamKind
import com.texthub.core.model.ParamSpec
import com.texthub.core.model.ToolCategory
import com.texthub.core.model.ToolInfo
import com.texthub.core.model.ToolMeta

/**
 * Unicode code point conversion. Works on real code points (not UTF-16 units), so
 * characters above U+FFFF - emoji, for example - are handled correctly and lone
 * surrogate halves are rejected.
 */
class UnicodeProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "unicode",
        name = "Unicode Code Points",
        glyph = "U+",
        category = ToolCategory.ENCODING,
        classification = Classification.ENCODING,
        encodeLabel = "Text to Code Points",
        decodeLabel = "Code Points to Text",
        params = listOf(
            ParamSpec(
                key = "mode",
                label = "Mode",
                kind = ParamKind.CHOICE,
                defaultValue = "uplus",
                choices = listOf(
                    Choice("uplus", "U+ notation (U+0041)"),
                    Choice("hex", "Plain hex (0041)"),
                    Choice("decimal", "Decimal (65)"),
                ),
            ),
            ParamSpec(
                key = "separator",
                label = "Separator",
                kind = ParamKind.CHOICE,
                defaultValue = "space",
                choices = listOf(
                    Choice("space", "Space"),
                    Choice("none", "None"),
                    Choice("comma", "Comma"),
                ),
            ),
        ),
        info = ToolInfo(
            summary = "Lists the Unicode code point of every character, including characters " +
                "beyond U+FFFF such as emoji. This is different from UTF-8 byte listings.",
            requiresKey = false,
            useCases = listOf(
                "Finding the code point of a symbol",
                "Writing escape sequences such as \\u0041",
                "Checking whether a character is a surrogate or a real code point",
            ),
            warnings = listOf("Encoding / notation, not encryption."),
        ),
        keywords = listOf("unicode", "codepoint", "code point", "utf", "emoji", "u+"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val mode = params["mode"] ?: "uplus"
        val separator = when (params["separator"]) {
            "none" -> ""
            "comma" -> ","
            else -> " "
        }
        if (direction == Direction.ENCODE) {
            val parts = ArrayList<String>()
            var i = 0
            while (i < input.length) {
                val cp = Character.codePointAt(input, i)
                parts.add(
                    when (mode) {
                        "decimal" -> cp.toString()
                        "hex" -> cp.toString(16).uppercase().padStart(4, '0')
                        else -> "U+" + cp.toString(16).uppercase().padStart(4, '0')
                    }
                )
                i += Character.charCount(cp)
            }
            return parts.joinToString(separator)
        }

        val tokens = input.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return ""
        val sb = StringBuilder()
        for (token in tokens) {
            val cleaned = token.removePrefix("U+").removePrefix("u+").removePrefix("\\u").removePrefix("0x")
            if (cleaned.isEmpty()) throw Errors.unicode()
            val cp = when (mode) {
                "decimal" -> {
                    if (cleaned.any { it !in '0'..'9' }) throw Errors.unicode()
                    cleaned.toIntOrNull() ?: throw Errors.unicode()
                }
                else -> {
                    if (cleaned.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) throw Errors.unicode()
                    cleaned.toIntOrNull(16) ?: throw Errors.unicode()
                }
            }
            if (cp < 0 || cp > 0x10FFFF) throw Errors.unicode()
            if (cp in 0xD800..0xDFFF) throw Errors.unicode()
            sb.appendCodePoint(cp)
        }
        return sb.toString()
    }
}
