package com.texthub.core.processors

import com.texthub.core.TextProcessor
import com.texthub.core.model.Classification
import com.texthub.core.model.Direction
import com.texthub.core.model.ToolCategory
import com.texthub.core.model.ToolInfo
import com.texthub.core.model.ToolMeta

/** ROT18 = ROT13 for letters + ROT5 for digits. Symmetrical. */
class Rot18Processor : TextProcessor {

    override val meta = ToolMeta(
        id = "rot18",
        name = "ROT18",
        glyph = "18",
        category = ToolCategory.CLASSICAL,
        classification = Classification.OBFUSCATION,
        encodeLabel = "Encode",
        decodeLabel = "Decode",
        symmetric = true,
        info = ToolInfo(
            summary = "ROT18 applies ROT13 to letters and ROT5 to digits, so both text and numbers " +
                "are rotated. It is its own inverse.",
            requiresKey = false,
            useCases = listOf(
                "Obfuscating text that contains numbers",
                "Forum-style spoiler hiding",
            ),
            warnings = listOf("Obfuscation only - it is trivial to reverse and is not encryption."),
            convention = "Letters rotate by 13, digits by 5. Everything else is unchanged.",
        ),
        keywords = listOf("rot18", "rot13", "rot5", "rotate"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val sb = StringBuilder(input.length)
        for (c in input) {
            sb.append(
                when (c) {
                    in 'a'..'z' -> ((c - 'a' + 13) % 26 + 'a'.code).toChar()
                    in 'A'..'Z' -> ((c - 'A' + 13) % 26 + 'A'.code).toChar()
                    in '0'..'9' -> ((c - '0' + 5) % 10 + '0'.code).toChar()
                    else -> c
                }
            )
        }
        return sb.toString()
    }
}
