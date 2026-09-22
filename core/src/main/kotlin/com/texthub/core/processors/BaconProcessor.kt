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

/** Bacon's cipher: each letter becomes a five symbol group of A and B. */
class BaconProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "bacon",
        name = "Bacon's Cipher",
        glyph = "BAC",
        category = ToolCategory.CLASSICAL,
        classification = Classification.CLASSICAL_CIPHER,
        encodeLabel = "Encode",
        decodeLabel = "Decode",
        params = listOf(
            ParamSpec(
                key = "variant",
                label = "Alphabet convention",
                kind = ParamKind.CHOICE,
                defaultValue = "26",
                choices = listOf(
                    Choice("26", "26-letter (I/J and U/V distinct)"),
                    Choice("24", "24-letter (I=J and U=V)"),
                ),
            ),
        ),
        info = ToolInfo(
            summary = "Bacon's cipher hides a message inside five-symbol groups of A and B (or " +
                "any two symbols). It is a steganographic classical cipher.",
            requiresKey = false,
            useCases = listOf(
                "Steganography exercises",
                "Classical cipher study",
            ),
            warnings = listOf(
                "The 24-letter and 26-letter variants share the same A/B output, so the variant cannot be read back from a payload: choose the one the message was written with.","A classical cipher - not modern secure encryption."),
            convention = "A = 0, B = 1, five symbols per letter. Groups are separated by spaces and words by /.",
        ),
        keywords = listOf("bacon", "baconian", "steganography", "ab"),
    )

    companion object {
        private const val ALPHA_26 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        /** 24 letter convention: J merges into I and V merges into U. */
        private const val ALPHA_24 = "ABCDEFGHIKLMNOPQRSTUWXYZ"
    }

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val variant24 = params["variant"] == "24"
        val alphabet = if (variant24) ALPHA_24 else ALPHA_26
        if (direction == Direction.ENCODE) {
            val words = input.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            return words.joinToString(" / ") { word ->
                word.uppercase()
                    .filter { normalize(it, variant24) in alphabet }
                    .map { c ->
                        val idx = alphabet.indexOf(normalize(c, variant24))
                        (4 downTo 0).joinToString("") { b -> if ((idx ushr b) and 1 == 1) "B" else "A" }
                    }
                    .joinToString(" ")
            }
        }

        val tokens = input.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return ""
        val sb = StringBuilder()
        var pendingSpace = false
        for (token in tokens) {
            if (token.all { it == '/' }) {
                pendingSpace = true
                continue
            }
            val group = token.uppercase().replace('0', 'A').replace('1', 'B')
            if (group.length != 5 || group.any { it != 'A' && it != 'B' }) throw Errors.bacon()
            var value = 0
            for (c in group) value = (value shl 1) + if (c == 'B') 1 else 0
            if (value >= alphabet.length) throw Errors.bacon()
            if (pendingSpace && sb.isNotEmpty()) sb.append(' ')
            pendingSpace = false
            sb.append(alphabet[value])
        }
        return sb.toString()
    }

    private fun normalize(c: Char, variant24: Boolean): Char {
        val u = c.uppercaseChar()
        if (!variant24) return u
        return when (u) {
            'J' -> 'I'
            'V' -> 'U'
            else -> u
        }
    }
}
