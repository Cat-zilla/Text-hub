package com.texthub.core.processors

import com.texthub.core.TextProcessor
import com.texthub.core.model.Classification
import com.texthub.core.model.Direction
import com.texthub.core.model.ToolCategory
import com.texthub.core.model.ToolInfo
import com.texthub.core.model.ToolMeta

/** ROT13: Caesar with a shift of 13. Symmetrical - encode and decode are identical. */
class Rot13Processor : TextProcessor {

    override val meta = ToolMeta(
        id = "rot13",
        name = "ROT13",
        glyph = "13",
        category = ToolCategory.CLASSICAL,
        classification = Classification.OBFUSCATION,
        encodeLabel = "Encode",
        decodeLabel = "Decode",
        symmetric = true,
        info = ToolInfo(
            summary = "ROT13 replaces each letter with the letter 13 places after it. Because the " +
                "alphabet has 26 letters, applying it twice returns the original text.",
            requiresKey = false,
            useCases = listOf(
                "Hiding spoilers or puzzle solutions",
                "Quick reversible obfuscation",
            ),
            warnings = listOf("Obfuscation only - anyone can reverse it instantly. It is not encryption."),
            convention = "Only letters are rotated; digits, spaces, punctuation and case are preserved.",
        ),
        keywords = listOf("rot13", "rotate13", "caesar13"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val sb = StringBuilder(input.length)
        for (c in input) {
            sb.append(
                when (c) {
                    in 'a'..'z' -> ((c - 'a' + 13) % 26 + 'a'.code).toChar()
                    in 'A'..'Z' -> ((c - 'A' + 13) % 26 + 'A'.code).toChar()
                    else -> c
                }
            )
        }
        return sb.toString()
    }
}
