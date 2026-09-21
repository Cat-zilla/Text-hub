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

/** International Morse code (ITU-R M.1677-1). Letters separated by spaces, words by "/". */
class MorseProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "morse",
        name = "Morse Code",
        glyph = "MOR",
        category = ToolCategory.ENCODING,
        classification = Classification.ENCODING,
        encodeLabel = "Text to Morse",
        decodeLabel = "Morse to Text",
        params = listOf(
            ParamSpec(
                key = "wordSeparator",
                label = "Word separator",
                kind = ParamKind.TEXT,
                defaultValue = "/",
                helper = "Used between words when encoding.",
            ),
        ),
        info = ToolInfo(
            summary = "Morse code represents letters, digits and punctuation as short (dot) and " +
                "long (dash) signals. Letters are separated by spaces and words by a slash.",
            requiresKey = false,
            useCases = listOf(
                "Learning or practising Morse code",
                "Puzzles and geocaching",
                "Light or sound signalling practice",
            ),
            warnings = listOf(
                "Morse supports letters, digits and common punctuation only; other characters " +
                    "are skipped when encoding.",
                "Encoding, not encryption.",
            ),
        ),
        keywords = listOf("morse", "dit", "dah", "telegraph", "signal"),
    )

    companion object {
        val TABLE: Map<Char, String> = linkedMapOf(
            'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".",
            'F' to "..-.", 'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---",
            'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---",
            'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-",
            'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--",
            'Z' to "--..",
            '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-",
            '5' to ".....", '6' to "-....", '7' to "--...", '8' to "---..", '9' to "----.",
            '.' to ".-.-.-", ',' to "--..--", '?' to "..--..", '\'' to ".----.",
            '!' to "-.-.--", '/' to "-..-.", '(' to "-.--.", ')' to "-.--.-",
            '&' to ".-...", ':' to "---...", ';' to "-.-.-.", '=' to "-...-",
            '+' to ".-.-.", '-' to "-....-", '_' to "..--.-", '"' to ".-..-.",
            '$' to "...-..-", '@' to ".--.-.",
        )

        private val REVERSE: Map<String, Char> = TABLE.entries.associate { it.value to it.key }
    }

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val wordSep = params["wordSeparator"]?.takeIf { it.isNotBlank() } ?: "/"
        if (direction == Direction.ENCODE) {
            val out = ArrayList<String>()
            for (word in input.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }) {
                val letters = ArrayList<String>()
                for (c in word.uppercase()) {
                    TABLE[c]?.let { letters.add(it) }
                }
                if (letters.isNotEmpty()) out.add(letters.joinToString(" "))
            }
            return out.joinToString(" $wordSep ")
        }

        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        val sb = StringBuilder()
        val tokens = trimmed.split(Regex("\\s+"))
        var pendingSpace = false
        for (token in tokens) {
            if (token == wordSep) {
                if (sb.isNotEmpty()) pendingSpace = true
                continue
            }
            val ch = REVERSE[token]
                ?: throw Errors.morse()
            if (pendingSpace && sb.isNotEmpty()) {
                sb.append(' ')
                pendingSpace = false
            }
            sb.append(ch)
        }
        return sb.toString()
    }
}
