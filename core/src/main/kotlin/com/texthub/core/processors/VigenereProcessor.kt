package com.texthub.core.processors

import com.texthub.core.util.asciiLetters
import com.texthub.core.util.asciiLettersAndDigits
import com.texthub.core.util.isAsciiDigit
import com.texthub.core.util.isAsciiLetter
import com.texthub.core.TextProcessor
import com.texthub.core.model.Classification
import com.texthub.core.model.Direction
import com.texthub.core.model.Errors
import com.texthub.core.model.ParamKind
import com.texthub.core.model.ParamSpec
import com.texthub.core.model.ToolCategory
import com.texthub.core.model.ToolInfo
import com.texthub.core.model.ToolMeta

/**
 * Vigenère cipher. Spaces and punctuation do not consume key characters, which is the
 * behaviour most textbooks and classroom tools use.
 */
class VigenereProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "vigenere",
        name = "Vigenère Cipher",
        glyph = "VIG",
        category = ToolCategory.CLASSICAL,
        classification = Classification.CLASSICAL_CIPHER,
        encodeLabel = "Encrypt",
        decodeLabel = "Decrypt",
        params = listOf(
            ParamSpec(
                key = "key",
                label = "Secret key",
                kind = ParamKind.TEXT,
                defaultValue = "",
                hint = "Letters only",
                sensitive = true,
                helper = "Example: KEY",
            ),
        ),
        info = ToolInfo(
            summary = "Vigenère encrypts text by shifting each letter by a different amount taken " +
                "from a repeating keyword. It is a polyalphabetic classical cipher.",
            requiresKey = true,
            useCases = listOf(
                "Classical cryptography lessons",
                "Historical cipher puzzles",
            ),
            warnings = listOf(
                "This is a classical cipher and should NOT be treated as modern secure encryption. " +
                    "It is easily broken with frequency analysis.",
            ),
            convention = "Only letters consume key characters; spaces, digits and punctuation are " +
                "copied unchanged and letter case is preserved.",
        ),
        keywords = listOf("vigenere", "polyalphabetic", "key", "classic"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        // ASCII letters only: a non-ASCII letter has no place in an A-Z table.
        val key = (params["key"] ?: "").asciiLetters()
        if (key.isEmpty()) throw Errors.missingKey()
        val shifts = key.map { it - 'A' }
        var keyIndex = 0
        val sb = StringBuilder(input.length)
        for (c in input) {
            when (c) {
                in 'a'..'z' -> {
                    val s = shifts[keyIndex % shifts.size]
                    val v = if (direction == Direction.ENCODE) (c - 'a' + s) % 26 else (c - 'a' - s + 26) % 26
                    sb.append(('a'.code + v).toChar())
                    keyIndex++
                }
                in 'A'..'Z' -> {
                    val s = shifts[keyIndex % shifts.size]
                    val v = if (direction == Direction.ENCODE) (c - 'A' + s) % 26 else (c - 'A' - s + 26) % 26
                    sb.append(('A'.code + v).toChar())
                    keyIndex++
                }
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}
