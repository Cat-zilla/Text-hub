package com.texthub.core.processors

import com.texthub.core.TextProcessor
import com.texthub.core.model.Classification
import com.texthub.core.model.Direction
import com.texthub.core.model.ToolCategory
import com.texthub.core.model.ToolInfo
import com.texthub.core.model.ToolMeta

/** Atbash: A <-> Z, B <-> Y. Symmetrical. */
class AtbashProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "atbash",
        name = "Atbash",
        glyph = "ATB",
        category = ToolCategory.CLASSICAL,
        classification = Classification.CLASSICAL_CIPHER,
        encodeLabel = "Encode",
        decodeLabel = "Decode",
        symmetric = true,
        info = ToolInfo(
            summary = "Atbash mirrors the alphabet: A becomes Z, B becomes Y and so on. Applying " +
                "it twice restores the original text.",
            requiresKey = false,
            useCases = listOf(
                "Classical cipher study",
                "Simple reversible obfuscation",
            ),
            warnings = listOf("A classical cipher with a single fixed key - it provides no real security."),
            convention = "Letter case, digits, spaces and punctuation are preserved.",
        ),
        keywords = listOf("atbash", "mirror", "reverse alphabet", "hebrew"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val sb = StringBuilder(input.length)
        for (c in input) {
            sb.append(
                when (c) {
                    in 'a'..'z' -> ('a'.code + (25 - (c - 'a'))).toChar()
                    in 'A'..'Z' -> ('A'.code + (25 - (c - 'A'))).toChar()
                    else -> c
                }
            )
        }
        return sb.toString()
    }
}
