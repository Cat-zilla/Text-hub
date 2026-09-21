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
import com.texthub.core.util.asciiLetters
import com.texthub.core.util.modInverse

/** Shared modular matrix maths for the Hill ciphers (2x2 and 3x3). */
internal object MatrixMod26 {

    /** Inverse of a square matrix modulo 26, or null when the determinant is not invertible. */
    fun inverse(matrix: IntArray, size: Int): IntArray? {
        val det = determinant(matrix, size).mod26()
        val detInv = modInverse(det, 26) ?: return null
        val cofactors = IntArray(size * size)
        for (row in 0 until size) {
            for (col in 0 until size) {
                val sign = if ((row + col) % 2 == 0) 1 else -1
                cofactors[row * size + col] = sign * minor(matrix, size, row, col)
            }
        }
        val inverse = IntArray(size * size)
        for (row in 0 until size) {
            for (col in 0 until size) {
                // adjugate = transposed cofactor matrix
                val value = cofactors[col * size + row]
                val normalized = (value.mod26() * detInv).mod26()
                inverse[row * size + col] = normalized
            }
        }
        return inverse
    }

    /** Multiplies a matrix (size x size) by a vector of length size, modulo 26. */
    fun multiply(matrix: IntArray, size: Int, vector: IntArray): IntArray {
        val out = IntArray(size)
        for (row in 0 until size) {
            var sum = 0
            for (col in 0 until size) sum += matrix[row * size + col] * vector[col]
            out[row] = sum.mod26()
        }
        return out
    }

    private fun determinant(m: IntArray, size: Int): Int = when (size) {
        1 -> m[0]
        2 -> m[0] * m[3] - m[1] * m[2]
        3 -> m[0] * (m[4] * m[8] - m[5] * m[7]) -
            m[1] * (m[3] * m[8] - m[5] * m[6]) +
            m[2] * (m[3] * m[7] - m[4] * m[6])
        else -> throw Errors.cipherParams()
    }

    private fun minor(m: IntArray, size: Int, skipRow: Int, skipCol: Int): Int {
        val values = ArrayList<Int>(size * size)
        for (row in 0 until size) {
            if (row == skipRow) continue
            for (col in 0 until size) {
                if (col == skipCol) continue
                values.add(m[row * size + col])
            }
        }
        return determinant(values.toIntArray(), size - 1)
    }

    private fun Int.mod26(): Int = ((this % 26) + 26) % 26
}

/**
 * Hill cipher with a 3x3 key matrix (nine letters). The Hill cipher generalises to any block
 * size, and 3x3 mixes each letter into three others instead of two.
 */
class Hill3Processor : TextProcessor {

    override val meta = ToolMeta(
        id = "hill3",
        name = "Hill Cipher (3×3)",
        glyph = "H3",
        category = ToolCategory.CLASSICAL,
        classification = Classification.CLASSICAL_CIPHER,
        encodeLabel = "Encrypt",
        decodeLabel = "Decrypt",
        params = listOf(
            ParamSpec(
                key = "key",
                label = "Key matrix (9 letters)",
                kind = ParamKind.TEXT,
                defaultValue = "GYBNQKURP",
                hint = "e.g. GYBNQKURP",
                sensitive = true,
                helper = "Read row by row: abc / def / ghi. Its determinant must be coprime with 26.",
            ),
            ParamSpec(
                key = "padding",
                label = "Padding",
                kind = ParamKind.CHOICE,
                defaultValue = "pad",
                choices = listOf(
                    Choice("pad", "Pad with X"),
                    Choice("none", "No padding (reject other lengths)"),
                ),
            ),
        ),
        info = ToolInfo(
            summary = "The 3×3 Hill cipher encrypts blocks of three letters by matrix " +
                "multiplication modulo 26. Each ciphertext letter depends on three plaintext " +
                "letters, which hides single-letter frequency better than the 2×2 version.",
            requiresKey = true,
            useCases = listOf(
                "Demonstrating block ciphers and linear algebra",
                "A harder Hill exercise than the 2×2 case",
            ),
            warnings = listOf(
                "A classical cipher - not modern secure encryption. Known plaintext recovers the " +
                    "matrix quickly.",
            ),
            convention = "Letters only; X pads a length that is not a multiple of three.",
        ),
        keywords = listOf("hill", "3x3", "matrix", "block cipher", "linear"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val letters = (params["key"] ?: "").uppercase().filter { it in 'A'..'Z' }
        if (letters.length != 9) throw Errors.hillSize(9)
        val matrix = IntArray(9) { letters[it] - 'A' }
        val inverse = MatrixMod26.inverse(matrix, 3)
            ?: throw Errors.hillKey()

        val clean = input.uppercase().filter { it in 'A'..'Z' }
        if (clean.isEmpty()) return ""
        val padded = when (clean.length % 3) {
            0 -> clean
            else -> if (params["padding"] == "none") {
                throw Errors.cipherParams()
            } else {
                clean + "X".repeat(3 - clean.length % 3)
            }
        }

        val active = if (direction == Direction.ENCODE) matrix else inverse
        val out = StringBuilder(padded.length)
        var i = 0
        while (i < padded.length) {
            val block = IntArray(3) { padded[i + it] - 'A' }
            val result = MatrixMod26.multiply(active, 3, block)
            result.forEach { out.append(('A'.code + it).toChar()) }
            i += 3
        }
        return out.toString()
    }
}

/**
 * Porta cipher: a reciprocal polyalphabetic cipher with only thirteen alphabets, invented by
 * Giambattista della Porta and published in 1563.
 */
class PortaProcessor : TextProcessor {

    private val rows = listOf(
        "NOPQRSTUVWXYZABCDEFGHIJKLM", // A B
        "OPQRSTUVWXYZNMABCDEFGHIJKL", // C D
        "PQRSTUVWXYZNOLMABCDEFGHIJK", // E F
        "QRSTUVWXYZNOPKLMABCDEFGHIJ", // G H
        "RSTUVWXYZNOPQJKLMABCDEFGHI", // I J
        "STUVWXYZNOPQRIJKLMABCDEFGH", // K L
        "TUVWXYZNOPQRSHIJKLMABCDEFG", // M N
        "UVWXYZNOPQRSTGHIJKLMABCDEF", // O P
        "VWXYZNOPQRSTUFGHIJKLMABCDE", // Q R
        "WXYZNOPQRSTUVEFGHIJKLMABCD", // S T
        "XYZNOPQRSTUVWDEFGHIJKLMABC", // U V
        "YZNOPQRSTUVWXCDEFGHIJKLMAB", // W X
        "ZNOPQRSTUVWXYBCDEFGHIJKLMA", // Y Z
    )

    override val meta = ToolMeta(
        id = "porta",
        name = "Porta Cipher",
        glyph = "PTA",
        category = ToolCategory.CLASSICAL,
        classification = Classification.CLASSICAL_CIPHER,
        encodeLabel = "Encrypt",
        decodeLabel = "Decrypt",
        symmetric = true,
        params = listOf(
            ParamSpec(
                key = "key",
                label = "Secret keyword",
                kind = ParamKind.TEXT,
                defaultValue = "PORTA",
                hint = "Letters only, e.g. PORTA",
                sensitive = true,
                helper = "Key letters pair up (A≡B, C≡D, …), so there are thirteen cipher alphabets.",
            ),
        ),
        info = ToolInfo(
            summary = "The Porta cipher is a reciprocal polyalphabetic cipher: the key selects one " +
                "of thirteen alphabets that map the first half of the alphabet onto the second " +
                "half, so the same operation encrypts and decrypts.",
            requiresKey = true,
            useCases = listOf(
                "Classical cryptography lessons",
                "Comparing 13-alphabet ciphers with Vigenère's 26",
            ),
            warnings = listOf(
                "A classical cipher - not modern secure encryption. The periodic keyword falls to " +
                    "frequency analysis.",
            ),
            convention = "Only letters advance the key; spaces, digits, punctuation and case are preserved.",
        ),
        keywords = listOf("porta", "della porta", "polyalphabetic", "reciprocal", "13 alphabets"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val key = (params["key"] ?: "").uppercase().filter { it in 'A'..'Z' }
        if (key.isEmpty()) throw Errors.porta()
        var index = 0
        val sb = StringBuilder(input.length)
        for (c in input) {
            val upper = c.uppercaseChar()
            if (upper !in 'A'..'Z') {
                sb.append(c)
                continue
            }
            val row = rows[(key[index % key.length] - 'A') / 2]
            val plainIndex = upper - 'A'
            // Each row is stored as 26 entries: the thirteen letters A-M map onto, followed by the
            // reciprocal half, so N-Z comes back through the same table.
            val mapped = row[plainIndex]
            sb.append(if (c.isUpperCase()) mapped else mapped.lowercaseChar())
            index++
        }
        return sb.toString()
    }
}

/**
 * Trifid cipher: a 3x3x3 cube version of Bifid. Each letter becomes three coordinates, which are
 * written out in one stream and read back in a different order.
 */
class TrifidProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "trifid",
        name = "Trifid Cipher",
        glyph = "TRI",
        category = ToolCategory.CLASSICAL,
        classification = Classification.CLASSICAL_CIPHER,
        encodeLabel = "Encrypt",
        decodeLabel = "Decrypt",
        params = listOf(
            ParamSpec(
                key = "key",
                label = "Cube keyword",
                kind = ParamKind.TEXT,
                defaultValue = "",
                hint = "Optional keyword",
                sensitive = true,
            ),
            ParamSpec(
                key = "period",
                label = "Period (block size)",
                kind = ParamKind.NUMBER,
                defaultValue = "5",
                min = 3,
                max = 40,
                helper = "How many letters are mixed together at a time.",
            ),
            ParamSpec(
                key = "alphabet",
                label = "Cube",
                kind = ParamKind.CHOICE,
                defaultValue = "letters",
                choices = listOf(
                    Choice("letters", "A-Z + '.' (27 cells)"),
                    Choice("merged", "I/J merged + '.' + space (27 cells)"),
                ),
            ),
        ),
        info = ToolInfo(
            summary = "Trifid writes every letter as three coordinates in a cube, concatenates the " +
                "coordinates and then reads them back in three parts, so a change to one letter " +
                "spreads across the whole block.",
            requiresKey = false,
            useCases = listOf(
                "Classical cryptography lessons",
                "Combining substitution with transposition",
            ),
            warnings = listOf("A classical cipher - not modern secure encryption."),
            convention = "The cube holds 27 cells: A-Z plus '.', or the I/J-merged set with a " +
                "space; longer periods mix harder.",
        ),
        keywords = listOf("trifid", "cube", "three coordinates", "transposition", "period"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val merged = params["alphabet"] == "merged"
        val size = 3 // a cube is always 3x3x3 = 27 cells
        val cube = buildCube(params["key"] ?: "", merged)
        val period = (params["period"]?.toIntOrNull() ?: 5).coerceAtLeast(3)
        val clean = input.uppercase().filter { it in cube }
        if (clean.isEmpty()) return ""

        val out = StringBuilder(clean.length)
        var start = 0
        while (start < clean.length) {
            val end = minOf(start + period, clean.length)
            val block = clean.substring(start, end)
            val n = block.length
            if (direction == Direction.ENCODE) {
                val stream = IntArray(n * 3)
                block.forEachIndexed { i, ch ->
                    val idx = cube.indexOf(ch)
                    stream[i] = idx / (size * size)
                    stream[n + i] = (idx / size) % size
                    stream[2 * n + i] = idx % size
                }
                var k = 0
                while (k < n * 3) {
                    out.append(cube[stream[k] * size * size + stream[k + 1] * size + stream[k + 2]])
                    k += 3
                }
            } else {
                // Write every ciphertext letter as three interleaved coordinates, then read the
                // stream back as three separate parts.
                val stream = IntArray(n * 3)
                block.forEachIndexed { i, ch ->
                    val idx = cube.indexOf(ch)
                    stream[3 * i] = idx / (size * size)
                    stream[3 * i + 1] = (idx / size) % size
                    stream[3 * i + 2] = idx % size
                }
                for (i in 0 until n) {
                    val a = stream[i]
                    val b = stream[n + i]
                    val c = stream[2 * n + i]
                    out.append(cube[a * size * size + b * size + c])
                }
            }
            start = end
        }
        return out.toString()
    }

    /** Builds a 3x3x3 cube: 27 cells, filled from the keyword and then by the base alphabet. */
    private fun buildCube(key: String, merged: Boolean): String {
        val base = if (merged) "ABCDEFGHIKLMNOPQRSTUVWXYZ. " else "ABCDEFGHIJKLMNOPQRSTUVWXYZ."
        val seen = LinkedHashSet<Char>()
        for (raw in key.uppercase()) {
            val c = if (merged && raw == 'J') 'I' else raw
            if (c in base) seen.add(c)
        }
        for (c in base) seen.add(c)
        return seen.joinToString("")
    }
}

/** Scytale: the ancient Spartan transposition, where text is wound around a rod of fixed width. */
class ScytaleProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "scytale",
        name = "Scytale",
        glyph = "SCY",
        category = ToolCategory.CLASSICAL,
        classification = Classification.CLASSICAL_CIPHER,
        encodeLabel = "Wrap",
        decodeLabel = "Unwrap",
        params = listOf(
            ParamSpec(
                key = "diameter",
                label = "Rod diameter",
                kind = ParamKind.NUMBER,
                defaultValue = "3",
                min = 2,
                max = 40,
                helper = "How many letters are written per turn of the rod.",
            ),
        ),
        info = ToolInfo(
            summary = "The Scytale wraps text around a rod of fixed width, so letters are read down " +
                "the rod instead of across the page. It is pure transposition: the letters are " +
                "rearranged, never replaced.",
            requiresKey = false,
            useCases = listOf(
                "The oldest recorded military cipher",
                "Demonstrating transposition without substitution",
            ),
            warnings = listOf("A classical cipher - not modern secure encryption."),
            convention = "The rod width is the only secret; a wrong width produces scrambled but intact letters.",
        ),
        keywords = listOf("scytale", "sparta", "transposition", "rod", "diameter"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val width = (params["diameter"]?.toIntOrNull() ?: 3).coerceAtLeast(2)
        if (input.isEmpty()) return ""
        return if (direction == Direction.ENCODE) wrap(input, width) else unwrap(input, width)
    }

    /** Writes the text row-wise and reads it column-wise. */
    private fun wrap(text: String, width: Int): String {
        val rows = (text.length + width - 1) / width
        val sb = StringBuilder(text.length)
        for (col in 0 until width) {
            for (row in 0 until rows) {
                val index = row * width + col
                if (index < text.length) sb.append(text[index])
            }
        }
        return sb.toString()
    }

    /** Reverses [wrap]: reads column-wise to rebuild the rows. */
    private fun unwrap(text: String, width: Int): String {
        val length = text.length
        val rows = (length + width - 1) / width
        val fullColumns = if (length % width == 0) width else length % width
        val grid = arrayOfNulls<Char>(length)
        var cursor = 0
        for (col in 0 until width) {
            val rowsInColumn = if (length % width == 0 || col < fullColumns) rows else rows - 1
            for (row in 0 until rowsInColumn) {
                grid[row * width + col] = text[cursor++]
            }
        }
        return grid.filterNotNull().joinToString("")
    }
}

/**
 * ADFGX and ADFGVX: a Polybius square (or cube-6 square) followed by a columnar transposition.
 * The names come from the six letters the Morse code produces, which made the messages hard to
 * mistake on the radio.
 */
class AdfgxProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "adfgx",
        name = "ADFGX / ADFGVX",
        glyph = "ADF",
        category = ToolCategory.CLASSICAL,
        classification = Classification.CLASSICAL_CIPHER,
        encodeLabel = "Encrypt",
        decodeLabel = "Decrypt",
        params = listOf(
            ParamSpec(
                key = "alphabet",
                label = "Square",
                kind = ParamKind.CHOICE,
                defaultValue = "adfgvx",
                choices = listOf(
                    Choice("adfgvx", "ADFGVX - 6×6 letters + digits"),
                    Choice("adfgx", "ADFGX - 5×5 letters (I/J combined)"),
                ),
            ),
            ParamSpec(
                key = "squareKey",
                label = "Square keyword",
                kind = ParamKind.TEXT,
                defaultValue = "BATTALION",
                hint = "Fills the square",
                sensitive = true,
                helper = "Used when encrypting. Paste the same square to follow the original derivation.",
            ),
            ParamSpec(
                key = "columnKey",
                label = "Column key",
                kind = ParamKind.TEXT,
                defaultValue = "CARGO",
                hint = "Word for the transposition",
                sensitive = true,
            ),
        ),
        info = ToolInfo(
            summary = "ADFGVX substitutes each character with two letters from a small alphabet " +
                "(A, D, F, G, V, X) using a keyworded square, then scrambles the result with a " +
                "columnar transposition under a second keyword. It was used by the German army in " +
                "1918 and broken the same year.",
            requiresKey = true,
            useCases = listOf(
                "Historical network cipher demonstration",
                "Seeing substitution and transposition combined",
            ),
            warnings = listOf(
                "A classical cipher - not modern secure encryption. The six-letter alphabet leaks " +
                    "structure, which is exactly how it was broken.",
            ),
            convention = "Letters and digits only - spaces, punctuation and case are dropped, as " +
                "in the historical messages. Encrypt uses both keywords; decrypt needs both too.",
        ),
        keywords = listOf("adfgx", "adfgvx", "first world war", "polybius", "columnar"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val adfgvx = params["alphabet"] != "adfgx"
        val symbols = if (adfgvx) "ADFGVX" else "ADFGX"
        val size = if (adfgvx) 6 else 5
        val columnKey = (params["columnKey"] ?: "").asciiLetters()
        if (columnKey.isEmpty()) throw Errors.adfgxKey()
        val square = buildSquare(params["squareKey"] ?: "", adfgvx)

        if (direction == Direction.ENCODE) {
            val body = StringBuilder()
            for (raw in input.uppercase()) {
                val c = if (!adfgvx && raw == 'J') 'I' else raw
                val index = square.indexOf(c)
                if (index < 0) continue
                body.append(symbols[index / size]).append(symbols[index % size])
            }
            if (body.isEmpty()) return ""
            // The transposition never changes the length, so no padding is needed and the
            // symbol stream stays exactly as long as the substitution produced it.
            return transpose(body.toString(), columnKey, decode = false)
        }

        val transposed = input.uppercase().filter { it in symbols }
        if (transposed.isEmpty()) return ""
        if (transposed.length % 2 != 0) throw Errors.adfgx()
        val digits = transpose(transposed, columnKey, decode = true)
        val sb = StringBuilder(digits.length / 2)
        var i = 0
        while (i + 1 < digits.length) {
            val row = symbols.indexOf(digits[i])
            val col = symbols.indexOf(digits[i + 1])
            if (row < 0 || col < 0) throw Errors.adfgx()
            sb.append(square[row * size + col])
            i += 2
        }
        return sb.toString()
    }

    private fun buildSquare(key: String, adfgvx: Boolean): String {
        val base = if (adfgvx) {
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        } else {
            "ABCDEFGHIKLMNOPQRSTUVWXYZ"
        }
        val seen = LinkedHashSet<Char>()
        for (raw in key.uppercase()) {
            val c = if (!adfgvx && raw == 'J') 'I' else raw
            if (c in base) seen.add(c)
        }
        for (c in base) seen.add(c)
        return seen.joinToString("")
    }

    /**
     * Columnar transposition that never changes the length: the cells that exist in the last row
     * are simply skipped when a column is shorter, so the inverse is exact.
     */
    private fun transpose(text: String, key: String, decode: Boolean): String {
        val columns = key.length
        val rows = (text.length + columns - 1) / columns
        val order = key.withIndex().sortedWith(compareBy({ it.value }, { it.index })).map { it.index }
        val out = StringBuilder(text.length)
        if (!decode) {
            for (col in order) {
                for (row in 0 until rows) {
                    val index = row * columns + col
                    if (index < text.length) out.append(text[index])
                }
            }
        } else {
            val grid = CharArray(text.length)
            var cursor = 0
            for (col in order) {
                for (row in 0 until rows) {
                    val index = row * columns + col
                    if (index < text.length) grid[index] = text[cursor++]
                }
            }
            grid.forEach { out.append(it) }
        }
        return out.toString()
    }
}
