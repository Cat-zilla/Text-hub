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
import com.texthub.core.model.DetectionHint

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
        detection = listOf(
            DetectionHint(
                alphabet = "0123456789-.,;:/| \t\n\r",
                minLength = 3,
                label = "A1Z26",
                // "1.2.3" is far more likely to be a version number than a code: dotted input needs a
                // longer run before it counts as evidence.
                recognise = { text ->
                    val numbers = Regex("\\d+").findAll(text).map { it.value }.toList()
                    val values = numbers.mapNotNull { it.toIntOrNull() }
                    val separators = text.filterNot { it.isDigit() }.trim()
                    numbers.size >= 3 && values.size == numbers.size &&
                        values.all { it in 1..26 } &&
                        numbers.none { it.startsWith("0") && it.length > 1 } &&
                        !(separators.all { it == '.' } && numbers.size < 4)
                },
                evidenceFor = { text ->
                    val numbers = Regex("\\d+").findAll(text).map { it.value }.toList()
                    val separators = text.filterNot { it.isDigit() }.trim()
                    "All ${numbers.size} numbers are between 1 and 26 - the positions of the letters " +
                        "A-Z - separated by \"$separators\"."
                },
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
            warnings = listOf(
                "A simple substitution/encoding-style transformation, not secure encryption.",
                "Decoding always returns capital letters: a number carries no case information.",
            ),
            convention = "Numbers are separated by the chosen separator; words are separated by / or |. " +
                "Only A-Z have a number, so other characters (accents, other scripts, punctuation) " +
                "are passed through unchanged, and decoding accepts any of the usual separators.",
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
        // Decoding accepts every separator people actually type (spaces, tabs, newlines, dashes,
        // slashes, pipes) as well as the two that are selected, because a separator is presentation
        // rather than data: a mismatched setting must not silently change the answer.
        val numberSeparators = setOf(' ', '\t', '\n', numberSep.trim().firstOrNull() ?: '-', '-')
        val wordSeparators = setOf('/', '|', (if (params["wordSeparator"] == "bar") "|" else "/").first())

        if (direction == Direction.ENCODE) {
            val sb = StringBuilder()
            var prevWasNumber = false
            var prevWasSpace = false
            for (c in input) {
                when {
                    // Only the 26 ASCII letters have a position in this alphabet. `Char.isLetter()`
                    // is true for thousands of other characters (ü, श, ٣ ...), which used to be
                    // turned into nonsense numbers such as 188; those are passed through unchanged.
                    c in 'A'..'Z' || c in 'a'..'z' -> {
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

        // Numbers become letters; see the two separator sets at the top of this function. Punctuation
        // stays as written, and a number outside 1-26 is refused instead of guessed at.
        val sb = StringBuilder()
        var atWordBreak = false
        for (m in Regex("\\d+|[^\\d]+").findAll(input)) {
            val token = m.value
            if (token.all { it.isDigit() }) {
                val value = token.toIntOrNull() ?: throw Errors.a1z26()
                if (value !in 1..26) throw Errors.a1z26()
                if (atWordBreak && sb.isNotEmpty()) sb.append(' ')
                atWordBreak = false
                sb.append(('A'.code + value - 1).toChar())
            } else {
                val wordBreak = token.any { it in wordSeparators }
                val literal = token.filterNot { it.isDigit() || it in numberSeparators || it in wordSeparators }
                if (literal.isNotEmpty()) {
                    if (atWordBreak && sb.isNotEmpty()) sb.append(' ')
                    sb.append(literal)
                    atWordBreak = false
                }
                if (wordBreak) atWordBreak = true
            }
        }
        return sb.toString().trimEnd()
    }
}
