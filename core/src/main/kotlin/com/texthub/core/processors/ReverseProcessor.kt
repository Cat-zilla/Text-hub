package com.texthub.core.processors

import com.texthub.core.TextProcessor
import com.texthub.core.model.Choice
import com.texthub.core.model.Classification
import com.texthub.core.model.Direction
import com.texthub.core.model.ParamKind
import com.texthub.core.model.ParamSpec
import com.texthub.core.model.ToolCategory
import com.texthub.core.model.ToolInfo
import com.texthub.core.model.ToolMeta

class ReverseProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "reverse",
        name = "Reverse Text",
        glyph = "REV",
        category = ToolCategory.TRANSFORM,
        classification = Classification.TEXT_TRANSFORM,
        encodeLabel = "Reverse",
        decodeLabel = "Reverse back",
        symmetric = true,
        params = listOf(
            ParamSpec(
                key = "mode",
                label = "Mode",
                kind = ParamKind.CHOICE,
                defaultValue = "chars",
                choices = listOf(
                    Choice("chars", "Reverse characters"),
                    Choice("lines", "Reverse each line"),
                    Choice("words", "Reverse word order"),
                    Choice("lineOrder", "Reverse line order"),
                ),
            ),
        ),
        info = ToolInfo(
            summary = "Reverses text at the character, word or line level. Useful for quick " +
                "checks, palindrome games and formatting fixes.",
            requiresKey = false,
            useCases = listOf(
                "Checking palindromes",
                "Flipping lists of values",
                "Reversing log lines",
            ),
            warnings = listOf("A plain text transformation - it does not hide anything."),
            convention = "Reversing characters works on Unicode code points, so emoji stay intact.",
        ),
        keywords = listOf("reverse", "backwards", "flip", "mirror"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        return when (params["mode"]) {
            "lines" -> input.split("\n").joinToString("\n") { it.reversedCodePoints() }
            "words" -> {
                // Reverse the order of the words while leaving whitespace runs untouched.
                val parts = Regex("\\s+|\\S+").findAll(input).map { it.value }.toList()
                val words = parts.filter { it.isNotBlank() }
                var w = words.size - 1
                parts.joinToString("") { part -> if (part.isBlank()) part else words[w--] }
            }
            "lineOrder" -> input.split("\n").reversed().joinToString("\n")
            else -> input.reversedCodePoints()
        }
    }

    /** Reverses by code point so surrogate pairs (emoji) are not torn apart. */
    private fun String.reversedCodePoints(): String {
        val cps = ArrayList<Int>()
        var i = 0
        while (i < length) {
            val cp = Character.codePointAt(this, i)
            cps.add(cp)
            i += Character.charCount(cp)
        }
        val sb = StringBuilder()
        for (k in cps.size - 1 downTo 0) sb.appendCodePoint(cps[k])
        return sb.toString()
    }
}
