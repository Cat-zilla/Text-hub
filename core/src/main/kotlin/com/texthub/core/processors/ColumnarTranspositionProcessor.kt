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
 * Columnar transposition.
 *
 * The column order comes from the alphabetical rank of the key characters; repeated key
 * characters are resolved deterministically left to right (a stable sort), which is the
 * convention used by most textbook implementations.
 */
class ColumnarTranspositionProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "columnar",
        name = "Columnar Transposition",
        glyph = "COL",
        category = ToolCategory.CLASSICAL,
        classification = Classification.CLASSICAL_CIPHER,
        encodeLabel = "Encrypt",
        decodeLabel = "Decrypt",
        params = listOf(
            ParamSpec(
                key = "key",
                label = "Keyword",
                kind = ParamKind.TEXT,
                defaultValue = "",
                required = true,
                hint = "e.g. ZEBRA",
                sensitive = true,
            ),
            ParamSpec(
                key = "padding",
                label = "Padding",
                kind = ParamKind.CHOICE,
                defaultValue = "none",
                choices = listOf(
                    Choice("none", "None (irregular columns)"),
                    Choice("pad", "Pad with X to fill the grid"),
                ),
            ),
        ),
        info = ToolInfo(
            summary = "Columnar transposition writes the message in rows under a keyword and then " +
                "reads the columns out in the alphabetical order of the keyword letters.",
            requiresKey = true,
            useCases = listOf(
                "Classical transposition exercises",
                "Comparing transposition with substitution",
            ),
            warnings = listOf("A classical transposition cipher - not modern secure encryption."),
            convention = "Repeated key characters keep their left-to-right order.",
        ),
        keywords = listOf("columnar", "transposition", "keyword", "columns"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val key = params["key"]?.trim() ?: ""
        if (key.isEmpty()) throw Errors.columnar()
        val pad = params["padding"] == "pad"
        val padChar = 'X'
        val order = columnOrder(key)

        if (direction == Direction.ENCODE) {
            var text = input
            if (pad) {
                val rem = text.length % key.length
                if (rem != 0) text += padChar.toString().repeat(key.length - rem)
            }
            if (text.isEmpty()) return ""
            val cols = key.length
            val rows = (text.length + cols - 1) / cols
            val sb = StringBuilder()
            for (col in order) {
                for (r in 0 until rows) {
                    val idx = r * cols + col
                    if (idx < text.length) sb.append(text[idx])
                }
            }
            return sb.toString()
        }

        if (input.isEmpty()) return ""
        val cols = key.length
        val rows = (input.length + cols - 1) / cols
        val remainder = input.length % cols
        val columnLengths = IntArray(cols) { c ->
            if (remainder == 0) rows else if (c < remainder) rows else rows - 1
        }
        val grid = Array(cols) { StringBuilder() }
        var pos = 0
        for (col in order) {
            val len = columnLengths[col]
            grid[col].append(input.substring(pos, pos + len))
            pos += len
        }
        val sb = StringBuilder(input.length)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (r < grid[c].length) sb.append(grid[c][r])
            }
        }
        var out = sb.toString()
        if (pad) {
            var remove = 0
            while (remove < cols && out.endsWith(padChar) && out.length > remove + 1) {
                remove++
                out = out.dropLast(1)
            }
        }
        return out
    }

    /** Returns grid column indices ordered by (key character, original position). */
    private fun columnOrder(key: String): List<Int> =
        key.mapIndexed { index, c -> index to c.uppercaseChar() }
            .sortedWith(compareBy({ it.second }, { it.first }))
            .map { it.first }
}
