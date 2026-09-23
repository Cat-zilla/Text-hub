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
import com.texthub.core.util.toOctal
import com.texthub.core.util.utf8Bytes
import com.texthub.core.util.utf8String
import com.texthub.core.model.DetectionHint

class OctalProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "octal",
        name = "Octal",
        glyph = "OCT",
        category = ToolCategory.ENCODING,
        classification = Classification.ENCODING,
        encodeLabel = "Text to Octal",
        decodeLabel = "Octal to Text",
        params = listOf(
            ParamSpec(
                key = "format",
                label = "Formatting",
                kind = ParamKind.CHOICE,
                defaultValue = "space",
                choices = listOf(
                    Choice("space", "Space separated"),
                    Choice("continuous", "Continuous"),
                ),
                helper = "Continuous output uses three digits per byte.",
            ),
        ),
        detection = listOf(
            DetectionHint(
                alphabet = "01234567 ,;:|\t\n",
                minLength = 6,
                label = "Octal bytes",
                recognise = { text ->
                    val tokens = text.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
                    tokens.size >= 3 && tokens.all { token -> token.length in 3..4 }
                },
                evidence = "Every value is a 3-digit octal number, the usual way octal bytes are written.",
            ),
        ),
        info = ToolInfo(
            summary = "Octal writes each UTF-8 byte as a base-8 number. Older Unix tools and file " +
                "permission notation use the same base.",
            requiresKey = false,
            useCases = listOf(
                "Legacy Unix tooling",
                "Escaped string literals (\\101 is 'A')",
            ),
            warnings = listOf("Encoding, not encryption."),
        ),
        keywords = listOf("octal", "base8", "bytes"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        if (direction == Direction.ENCODE) {
            return if (params["format"] == "continuous") {
                input.utf8Bytes().toOctal(separator = "", pad = true)
            } else {
                input.utf8Bytes().toOctal(separator = " ", pad = true)
            }
        }

        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        val tokens = if (trimmed.any { it.isWhitespace() }) {
            trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
        } else {
            if (trimmed.any { it !in '0'..'7' }) throw Errors.octal()
            if (trimmed.length % 3 != 0) throw Errors.octal()
            trimmed.chunked(3)
        }
        val out = ByteArray(tokens.size)
        tokens.forEachIndexed { i, token ->
            if (token.isEmpty() || token.length > 3 || token.any { it !in '0'..'7' }) throw Errors.octal()
            val v = token.toInt(8)
            if (v !in 0..255) throw Errors.octal()
            out[i] = v.toByte()
        }
        return out.utf8String()
    }
}
