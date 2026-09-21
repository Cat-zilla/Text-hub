package com.texthub.core.processors

import com.texthub.core.TextProcessor
import com.texthub.core.model.Classification
import com.texthub.core.model.Direction
import com.texthub.core.model.ToolCategory
import com.texthub.core.model.ToolInfo
import com.texthub.core.model.ToolMeta

/** ROT47 rotates the 94 printable ASCII characters (33-126). Symmetrical. */
class Rot47Processor : TextProcessor {

    override val meta = ToolMeta(
        id = "rot47",
        name = "ROT47",
        glyph = "47",
        category = ToolCategory.CLASSICAL,
        classification = Classification.OBFUSCATION,
        encodeLabel = "Encode",
        decodeLabel = "Decode",
        symmetric = true,
        info = ToolInfo(
            summary = "ROT47 rotates every printable ASCII character (codes 33 to 126) by 47 " +
                "positions, so letters, digits and punctuation are all transformed.",
            requiresKey = false,
            useCases = listOf(
                "Obfuscating strings that include punctuation",
                "Reversible text scrambling for tests",
            ),
            warnings = listOf(
                "Obfuscation, not secure encryption.",
                "Characters outside the printable ASCII range (including accented letters and " +
                    "emoji) are left unchanged.",
            ),
            convention = "Operates on printable ASCII 33-126 only.",
        ),
        keywords = listOf("rot47", "ascii", "rotate"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val sb = StringBuilder(input.length)
        for (c in input) {
            val code = c.code
            sb.append(if (code in 33..126) ((code - 33 + 47) % 94 + 33).toChar() else c)
        }
        return sb.toString()
    }
}
