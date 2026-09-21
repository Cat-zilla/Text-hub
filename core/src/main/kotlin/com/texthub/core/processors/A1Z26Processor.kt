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

/** A1Z26: A=1, B=2 ... Z=26. */
class A1Z26Processor : TextProcessor {

    override val meta = ToolMeta(
        id = "a1z26",
        name = "A1Z26",
        glyph = "A1",
        category = ToolCategory.CLASSICAL,
        classification = Classification.CLASSICAL_CIPHER,
        encodeLabel = "Text to Numbers",
        decodeLabel = "Numbers to Text",
        params = listOf(
            ParamSpec(
                key = "separator",
                label = "Separator",
                kind = ParamKind.CHOICE,
                defaultValue = "hyphen",
                choices = listOf(
                    Choice("hyphen", "Hyphen -"),
                    Choice("space", "Space"),
                    Choice("comma", "Comma"),
                    Choice("dot", "Dot"),
                ),
            ),
            ParamSpec(
                key = "wordSeparator",
                label = "Word separator",
                kind = ParamKind.CHOICE,
                defaultValue = "slash",
                choices = listOf(
                    Choice("slash", "Slash /"),
                    Choice("bar", "Bar |"),
                ),
                helper = "Keeps word boundaries visible when encoding.",
            ),
        ),
        info = ToolInfo(
            summary = "A1Z26 replaces each letter with its position in the alphabet: A becomes 1, " +
                "B becomes 2 and so on up to Z = 26.",
            requiresKey = false,
            useCases = listOf(
                "Simple number codes and puzzles",
                "Teaching letter positions",
            ),
            warnings = listOf("A simple substitution/encoding-style transformation, not secure encryption."),
            convention = "Numbers are separated by the chosen separator; words are separated by / or |.",
        ),
        keywords = listOf("a1z26", "alphabet numbers", "letter numbers"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val numberSep = when (params["separator"]) {
            "space" -> " "
            "comma" -> ","
            "dot" -> "."
            else -> "-"
        }
        val wordSep = if (params["wordSeparator"] == "bar") " | " else " / "

        if (direction == Direction.ENCODE) {
            val sb = StringBuilder()
            var prevWasNumber = false
            var prevWasSpace = false
            for (c in input) {
                when {
                    c.isLetter() -> {
                        val n = (c.uppercaseChar() - 'A') + 1
                        if (prevWasNumber) sb.append(numberSep)
                        sb.append(n)
                        prevWasNumber = true
                        prevWasSpace = false
                    }
                    c.isWhitespace() -> {
                        if (!prevWasSpace && sb.isNotEmpty()) sb.append(wordSep)
                        prevWasNumber = false
                        prevWasSpace = true
                    }
                    else -> {
                        sb.append(c)
                        prevWasNumber = false
                        prevWasSpace = false
                    }
                }
            }
            return sb.toString().trim()
        }

        // Numbers become letters. Number separators are dropped, a word separator becomes a
        // single space and any other punctuation is kept as written.
        val sepChar = numberSep.single()
        val effectiveSep = if (sepChar == ' ' || input.contains(sepChar)) sepChar else ' '
        val sb = StringBuilder()
        for (m in Regex("\\d+|\\D+").findAll(input)) {
            val token = m.value
            if (token.all { it.isDigit() }) {
                val v = token.toIntOrNull() ?: throw Errors.a1z26()
                if (v !in 1..26) throw Errors.a1z26()
                sb.append(('A'.code + v - 1).toChar())
            } else {
                val hasWordBreak = token.any { it == '/' || it == '|' } ||
                    (effectiveSep != ' ' && token.any { it.isWhitespace() })
                val literal = token.filter { it != '/' && it != '|' && it != effectiveSep && !it.isWhitespace() }
                if (literal.isNotEmpty()) sb.append(literal)
                if (hasWordBreak) sb.append(' ')
            }
        }
        return sb.toString().trimEnd()
    }
}
