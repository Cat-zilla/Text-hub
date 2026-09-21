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
import com.texthub.core.util.toDecimal
import com.texthub.core.util.utf8Bytes
import com.texthub.core.util.utf8String

class DecimalProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "decimal",
        name = "Decimal",
        glyph = "DEC",
        category = ToolCategory.ENCODING,
        classification = Classification.ENCODING,
        encodeLabel = "Text to Decimal",
        decodeLabel = "Decimal to Text",
        params = listOf(
            ParamSpec(
                key = "separator",
                label = "Separator",
                kind = ParamKind.CHOICE,
                defaultValue = "space",
                choices = listOf(
                    Choice("space", "Space"),
                    Choice("comma", "Comma"),
                    Choice("newline", "New line"),
                ),
            ),
        ),
        info = ToolInfo(
            summary = "Writes the UTF-8 bytes of the text as decimal numbers from 0 to 255. This " +
                "is a byte-based representation, not a per-character code point list.",
            requiresKey = false,
            useCases = listOf(
                "Low level debugging",
                "Numeric puzzles",
                "Comparing byte sequences",
            ),
            warnings = listOf(
                "The values are UTF-8 bytes (0-255), which is different from Unicode code points.",
                "Encoding, not encryption.",
            ),
        ),
        keywords = listOf("decimal", "bytes", "numbers", "denary"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val separator = when (params["separator"]) {
            "comma" -> ","
            "newline" -> "\n"
            else -> " "
        }
        if (direction == Direction.ENCODE) return input.utf8Bytes().toDecimal(separator)

        val tokens = input.split(Regex("[\\s,;]+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return ""
        val out = ByteArray(tokens.size)
        tokens.forEachIndexed { i, token ->
            if (token.any { it !in '0'..'9' }) throw Errors.decimal()
            val v = token.toIntOrNull() ?: throw Errors.decimal()
            if (v !in 0..255) throw Errors.decimal()
            out[i] = v.toByte()
        }
        return out.utf8String()
    }
}
