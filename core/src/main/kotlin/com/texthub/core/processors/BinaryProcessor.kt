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
import com.texthub.core.util.toBinary
import com.texthub.core.util.utf8Bytes
import com.texthub.core.util.utf8String

class BinaryProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "binary",
        name = "Binary",
        glyph = "BIN",
        category = ToolCategory.ENCODING,
        classification = Classification.ENCODING,
        encodeLabel = "Text to Binary",
        decodeLabel = "Binary to Text",
        params = listOf(
            ParamSpec(
                key = "format",
                label = "Formatting",
                kind = ParamKind.CHOICE,
                defaultValue = "space",
                choices = listOf(
                    Choice("space", "8-bit groups (space)"),
                    Choice("continuous", "Continuous"),
                ),
            ),
        ),
        info = ToolInfo(
            summary = "Binary shows the text as bits. Each UTF-8 byte becomes an 8-bit group, so " +
                "characters outside ASCII take more than one group.",
            requiresKey = false,
            useCases = listOf(
                "Learning how text is stored",
                "Bit-level puzzles and CTFs",
                "Checking byte-level encodings",
            ),
            warnings = listOf("Encoding, not encryption."),
        ),
        keywords = listOf("binary", "bits", "zeros", "ones", "base2"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        return if (direction == Direction.ENCODE) {
            val separator = if (params["format"] == "continuous") "" else " "
            input.utf8Bytes().toBinary(separator)
        } else {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return ""
            val bytes = if (trimmed.any { it.isWhitespace() }) {
                val groups = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
                ByteArray(groups.size) { g ->
                    val token = groups[g]
                    if (token.length > 8) throw Errors.binaryGroup()
                    if (token.any { it != '0' && it != '1' }) throw Errors.binary()
                    token.toInt(2).toByte()
                }
            } else {
                if (trimmed.any { it != '0' && it != '1' }) throw Errors.binary()
                if (trimmed.length % 8 != 0) throw Errors.binaryGroup()
                ByteArray(trimmed.length / 8) { i ->
                    trimmed.substring(i * 8, i * 8 + 8).toInt(2).toByte()
                }
            }
            bytes.utf8String()
        }
    }
}
