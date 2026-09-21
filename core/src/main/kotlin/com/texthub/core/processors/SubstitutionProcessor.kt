package com.texthub.core.processors

import com.texthub.core.TextProcessor
import com.texthub.core.model.Classification
import com.texthub.core.model.Direction
import com.texthub.core.model.Errors
import com.texthub.core.model.ParamKind
import com.texthub.core.model.ParamSpec
import com.texthub.core.model.ToolCategory
import com.texthub.core.model.ToolInfo
import com.texthub.core.model.ToolMeta

/** Simple monoalphabetic substitution with a user supplied 26 letter alphabet. */
class SubstitutionProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "substitution",
        name = "Substitution Cipher",
        glyph = "SUB",
        category = ToolCategory.CLASSICAL,
        classification = Classification.CLASSICAL_CIPHER,
        encodeLabel = "Encrypt",
        decodeLabel = "Decrypt",
        params = listOf(
            ParamSpec(
                key = "alphabet",
                label = "Substitution alphabet",
                kind = ParamKind.ALPHABET,
                defaultValue = "QWERTYUIOPASDFGHJKLZXCVBNM",
                hint = "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
                helper = "Write the 26 letters that A-Z should map to, in order.",
            ),
        ),
        info = ToolInfo(
            summary = "Substitution replaces every letter with a fixed partner from a custom " +
                "alphabet. The mapping is one-to-one, so the same letter always becomes the same " +
                "symbol.",
            requiresKey = true,
            useCases = listOf(
                "Cryptogram puzzles",
                "Hand-rolled cipher experiments",
            ),
            warnings = listOf("A classical cipher - it is broken by frequency analysis and is not secure encryption."),
            convention = "The standard order is A-Z; each position in your alphabet maps to the letter at that position.",
        ),
        keywords = listOf("substitution", "monoalphabetic", "cipher alphabet", "cryptogram"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val alphabet = validate(params["alphabet"] ?: "")
        val sb = StringBuilder(input.length)
        for (c in input) {
            when {
                c in 'a'..'z' -> {
                    val i = c - 'a'
                    val out = if (direction == Direction.ENCODE) alphabet[i] else ('a'.code + alphabet.indexOf(c.uppercaseChar())).toChar()
                    sb.append(out.lowercaseChar())
                }
                c in 'A'..'Z' -> {
                    val i = c - 'A'
                    val out = if (direction == Direction.ENCODE) alphabet[i] else ('A'.code + alphabet.indexOf(c)).toChar()
                    sb.append(out)
                }
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /** Validates and normalises the alphabet: exactly 26 unique A-Z letters. */
    private fun validate(raw: String): String {
        val cleaned = raw.uppercase().filter { it in 'A'..'Z' }
        if (cleaned.length != raw.trim().length) throw Errors.substitutionAlpha()
        if (cleaned.length != 26) throw Errors.substitutionLength(26)
        if (cleaned.toSet().size != 26) throw Errors.substitutionDuplicate()
        return cleaned
    }
}
