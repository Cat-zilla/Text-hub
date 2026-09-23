package com.texthub.core.processors

import com.texthub.core.TextProcessor
import com.texthub.core.model.Choice
import com.texthub.core.model.Classification
import com.texthub.core.model.Direction
import com.texthub.core.model.ParamKind
import com.texthub.core.model.ParamSpec
import com.texthub.core.model.ToolCategory
import com.texthub.core.model.ToolInfo
import com.texthub.core.model.ToolMeta
import com.texthub.core.util.parseHex
import com.texthub.core.util.toHex
import com.texthub.core.util.utf8Bytes
import com.texthub.core.util.utf8String
import com.texthub.core.detector.DetectionIndex
import com.texthub.core.model.DetectionHint

class HexProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "hex",
        name = "Hexadecimal",
        glyph = "HEX",
        category = ToolCategory.ENCODING,
        classification = Classification.ENCODING,
        encodeLabel = "Text to Hex",
        decodeLabel = "Hex to Text",
        params = listOf(
            ParamSpec(
                key = "format",
                label = "Formatting",
                kind = ParamKind.CHOICE,
                defaultValue = "space",
                choices = listOf(
                    Choice("continuous", "Continuous"),
                    Choice("space", "Space separated"),
                    Choice("comma", "Comma separated"),
                ),
            ),
        ),
        detection = listOf(
            DetectionHint(
                alphabet = "0123456789abcdefABCDEF",
                minLength = 4,
                multipleOf = 2,
                label = "Hexadecimal data",
                evidenceFor = { text ->
                    val compact = text.filterNot { it.isWhitespace() }
                    "${compact.length} hexadecimal digits with an even number of them."
                },
                validatedFor = { _, output ->
                    if (DetectionIndex.isReadableText(output)) {
                        "They decode to readable text."
                    } else {
                        "They decode to bytes that are not valid text, so this may be a digest or " +
                            "binary data rather than encoded text."
                    }
                },
                paramsFor = { mapOf("format" to "continuous") },
            ),
        ),
        info = ToolInfo(
            summary = "Hexadecimal shows each byte of the text as two base-16 digits. Text is " +
                "converted using UTF-8, so Unicode characters become several bytes.",
            requiresKey = false,
            useCases = listOf(
                "Inspecting the bytes behind a string",
                "Colour codes, hashes and binary dumps",
                "Debugging encoding problems",
            ),
            warnings = listOf("Encoding, not encryption."),
        ),
        keywords = listOf("hex", "hexadecimal", "base16", "bytes"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val separator = when (params["format"]) {
            "comma" -> ","
            "space" -> " "
            else -> ""
        }
        return if (direction == Direction.ENCODE) {
            input.utf8Bytes().toHex(separator = separator)
        } else {
            parseHex(input).utf8String()
        }
    }
}
