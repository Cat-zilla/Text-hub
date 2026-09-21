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

/**
 * Rail Fence (zigzag) transposition.
 *
 * "Transform all characters" moves every character, including spaces and punctuation.
 * "Letters only" keeps non-letters pinned to their original position and transposes just
 * the letters - handy when you want spaces to survive the round trip.
 */
class RailFenceProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "railfence",
        name = "Rail Fence Cipher",
        glyph = "RAIL",
        category = ToolCategory.CLASSICAL,
        classification = Classification.CLASSICAL_CIPHER,
        encodeLabel = "Encrypt",
        decodeLabel = "Decrypt",
        params = listOf(
            ParamSpec(
                key = "rails",
                label = "Number of rails",
                kind = ParamKind.NUMBER,
                defaultValue = "3",
                min = 2,
                max = 20,
            ),
            ParamSpec(
                key = "mode",
                label = "Mode",
                kind = ParamKind.CHOICE,
                defaultValue = "all",
                choices = listOf(
                    Choice("all", "Transform all characters"),
                    Choice("letters", "Preserve formatting (letters only)"),
                ),
            ),
        ),
        info = ToolInfo(
            summary = "Rail Fence writes the text in a zigzag across a number of rails and then " +
                "reads the rows one after another. It is a transposition cipher - the letters are " +
                "moved, not replaced.",
            requiresKey = false,
            useCases = listOf(
                "Classical cipher exercises",
                "Demonstrating transposition vs substitution",
            ),
            warnings = listOf("A classical cipher - not modern secure encryption."),
        ),
        keywords = listOf("rail fence", "zigzag", "transposition"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val rails = params["rails"]?.toIntOrNull() ?: 3
        if (rails < 2) throw Errors.railFence()
        if (input.isEmpty()) return ""
        return if (params["mode"] == "letters") processLetters(input, rails, direction)
        else processAll(input, rails, direction)
    }

    private fun pattern(length: Int, rails: Int): IntArray {
        val pattern = IntArray(length)
        var row = 0
        var step = 1
        for (i in 0 until length) {
            pattern[i] = row
            if (row == 0) step = 1
            else if (row == rails - 1) step = -1
            row += step
        }
        return pattern
    }

    private fun processAll(input: String, rails: Int, direction: Direction): String {
        if (rails >= input.length) return input
        val pattern = pattern(input.length, rails)
        return if (direction == Direction.ENCODE) {
            val rows = Array(rails) { StringBuilder() }
            for (i in input.indices) rows[pattern[i]].append(input[i])
            rows.joinToString("")
        } else {
            val counts = IntArray(rails)
            for (r in pattern) counts[r]++
            val rows = Array(rails) { StringBuilder() }
            var idx = 0
            for (r in 0 until rails) {
                rows[r].append(input, idx, idx + counts[r])
                idx += counts[r]
            }
            val pos = IntArray(rails)
            val out = StringBuilder(input.length)
            for (r in pattern) out.append(rows[r][pos[r]++])
            out.toString()
        }
    }

    private fun processLetters(input: String, rails: Int, direction: Direction): String {
        val indices = input.indices.filter { input[it].isLetter() }
        if (indices.size <= 1 || rails >= indices.size) return input
        val letters = indices.map { input[it] }
        val pattern = pattern(letters.size, rails)
        val moved = if (direction == Direction.ENCODE) {
            val rows = Array(rails) { StringBuilder() }
            for (i in letters.indices) rows[pattern[i]].append(letters[i])
            rows.joinToString("")
        } else {
            val counts = IntArray(rails)
            for (r in pattern) counts[r]++
            val joined = letters.joinToString("")
            val rows = Array(rails) { StringBuilder() }
            var idx = 0
            for (r in 0 until rails) {
                rows[r].append(joined.substring(idx, idx + counts[r]))
                idx += counts[r]
            }
            val pos = IntArray(rails)
            val out = StringBuilder(letters.size)
            for (r in pattern) out.append(rows[r][pos[r]++])
            out.toString()
        }
        val chars = input.toCharArray()
        indices.forEachIndexed { i, pos -> chars[pos] = moved[i] }
        return String(chars)
    }
}
