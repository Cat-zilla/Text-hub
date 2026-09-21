package com.texthub.core.processors

import com.texthub.core.TextProcessor
import com.texthub.core.model.Classification
import com.texthub.core.model.Direction
import com.texthub.core.model.ParamKind
import com.texthub.core.model.ParamSpec
import com.texthub.core.model.ToolCategory
import com.texthub.core.model.ToolInfo
import com.texthub.core.model.ToolMeta
import com.texthub.core.util.modPositive

class CaesarProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "caesar",
        name = "Caesar Cipher",
        glyph = "CAE",
        category = ToolCategory.CLASSICAL,
        classification = Classification.CLASSICAL_CIPHER,
        encodeLabel = "Encrypt",
        decodeLabel = "Decrypt",
        params = listOf(
            ParamSpec(
                key = "shift",
                label = "Shift",
                kind = ParamKind.NUMBER,
                defaultValue = "3",
                min = -25,
                max = 25,
                helper = "A + 3 = D. Negative values shift backwards.",
            ),
        ),
        info = ToolInfo(
            summary = "The Caesar cipher moves every letter a fixed number of places through the " +
                "alphabet. Only letters are shifted - digits, spaces, punctuation and letter case " +
                "are preserved.",
            requiresKey = true,
            useCases = listOf(
                "Learning how substitution ciphers work",
                "Simple obfuscation and puzzles",
            ),
            warnings = listOf(
                "Caesar is a classical substitution cipher intended primarily for learning and " +
                    "simple obfuscation. It offers no real security - there are only 25 keys.",
            ),
            convention = "Only A-Z and a-z are shifted; everything else is copied unchanged.",
        ),
        keywords = listOf("caesar", "shift", "rotate", "classic"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val raw = params["shift"]?.toIntOrNull() ?: 3
        var shift = raw.modPositive(26)
        if (direction == Direction.DECODE) shift = (26 - shift) % 26
        val sb = StringBuilder(input.length)
        for (c in input) {
            sb.append(
                when (c) {
                    in 'a'..'z' -> ((c - 'a' + shift) % 26 + 'a'.code).toChar()
                    in 'A'..'Z' -> ((c - 'A' + shift) % 26 + 'A'.code).toChar()
                    else -> c
                }
            )
        }
        return sb.toString()
    }
}
