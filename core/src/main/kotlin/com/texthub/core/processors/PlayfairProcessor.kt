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
 * Playfair cipher.
 *
 * Conventions (documented in the app):
 *  - 5×5 grid, I and J share a cell (J becomes I).
 *  - Non letters are removed and the text is upper-cased.
 *  - A filler letter is inserted between two identical letters inside a digraph.
 *  - The message is padded with the filler letter if it has an odd length.
 *  - Decryption removes a filler that sits between two identical letters and a trailing
 *    filler that was used for padding.
 */
class PlayfairProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "playfair",
        name = "Playfair Cipher",
        glyph = "PLAY",
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
                hint = "e.g. playfair example",
                sensitive = true,
            ),
            ParamSpec(
                key = "grid",
                label = "Grid",
                kind = ParamKind.CHOICE,
                defaultValue = "ij",
                choices = listOf(
                    Choice("ij", "5×5, I/J combined"),
                    Choice("alnum", "6×6, letters + digits"),
                ),
            ),
            ParamSpec(
                key = "filler",
                label = "Filler character",
                kind = ParamKind.TEXT,
                defaultValue = "X",
                hint = "X",
            ),
        ),
        info = ToolInfo(
            summary = "Playfair encrypts pairs of letters (digraphs) using a 5×5 key square built " +
                "from a keyword. Letters in the same row, column or rectangle are transformed with " +
                "different rules.",
            requiresKey = true,
            useCases = listOf(
                "Classical cipher study",
                "Historical cryptography puzzles",
            ),
            warnings = listOf("A historical/classical cipher - not modern secure encryption."),
            convention = "5×5 grid with I/J combined, non-letters removed, filler inserted between " +
                "doubled letters, odd length padded with the filler.",
        ),
        keywords = listOf("playfair", "digraph", "keyword square"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val key = (params["key"] ?: "").uppercase().filter { it.isLetter() || it.isDigit() }
        if (key.isEmpty()) throw Errors.playfair()
        val alnum = params["grid"] == "alnum"
        val fillerRaw = (params["filler"] ?: "X").uppercase().filter { it.isLetter() }
        val filler = fillerRaw.firstOrNull() ?: throw Errors.playfair()
        val square = buildSquare(key, alnum)
        val size = if (alnum) 6 else 5

        if (direction == Direction.ENCODE) {
            val prepared = prepare(input, alnum, filler)
            val out = StringBuilder()
            var i = 0
            while (i < prepared.length) {
                val a = prepared[i]
                val b = prepared[i + 1]
                val pa = square.indexOf(a)
                val pb = square.indexOf(b)
                val ra = pa / size
                val ca = pa % size
                val rb = pb / size
                val cb = pb % size
                if (ra == rb) {
                    out.append(square[ra * size + (ca + 1) % size])
                    out.append(square[rb * size + (cb + 1) % size])
                } else if (ca == cb) {
                    out.append(square[((ra + 1) % size) * size + ca])
                    out.append(square[((rb + 1) % size) * size + cb])
                } else {
                    out.append(square[ra * size + cb])
                    out.append(square[rb * size + ca])
                }
                i += 2
            }
            return out.toString()
        }

        val text = input.uppercase().filter { it in square }
        if (text.length % 2 != 0) throw Errors.cipherParams()
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val a = text[i]
            val b = text[i + 1]
            val pa = square.indexOf(a)
            val pb = square.indexOf(b)
            if (pa < 0 || pb < 0) throw Errors.cipherParams()
            val ra = pa / size
            val ca = pa % size
            val rb = pb / size
            val cb = pb % size
            if (ra == rb) {
                out.append(square[ra * size + (ca + size - 1) % size])
                out.append(square[rb * size + (cb + size - 1) % size])
            } else if (ca == cb) {
                out.append(square[((ra + size - 1) % size) * size + ca])
                out.append(square[((rb + size - 1) % size) * size + cb])
            } else {
                out.append(square[ra * size + cb])
                out.append(square[rb * size + ca])
            }
            i += 2
        }
        return stripFiller(out.toString(), filler)
    }

    private fun buildSquare(key: String, alnum: Boolean): String {
        val base = if (alnum) "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789" else "ABCDEFGHIKLMNOPQRSTUVWXYZ"
        val seen = LinkedHashSet<Char>()
        for (c in key) {
            val ch = if (!alnum && c == 'J') 'I' else c
            if (ch in base) seen.add(ch)
        }
        for (c in base) seen.add(c)
        return seen.joinToString("")
    }

    /** Upper-cases, keeps only representable characters and inserts/pads the filler. */
    private fun prepare(text: String, alnum: Boolean, filler: Char): String {
        val sb = StringBuilder()
        for (c in text.uppercase()) {
            val ch = if (!alnum && c == 'J') 'I' else c
            if (ch.isLetter() && !alnum) sb.append(ch)
            else if (alnum && (ch.isLetter() || ch.isDigit())) sb.append(ch)
        }
        if (sb.isEmpty()) throw Errors.playfair()
        val out = StringBuilder()
        var i = 0
        while (i < sb.length) {
            val a = sb[i]
            if (i + 1 >= sb.length) {
                out.append(a).append(filler)
                break
            }
            val b = sb[i + 1]
            if (a == b) {
                out.append(a).append(filler)
                i += 1
            } else {
                out.append(a).append(b)
                i += 2
            }
        }
        if (out.length % 2 != 0) out.append(filler)
        return out.toString()
    }

    private fun stripFiller(text: String, filler: Char): String {
        if (text.length < 2) return text
        val keep = BooleanArray(text.length) { true }
        for (i in 1 until text.length - 1) {
            if (text[i] == filler && text[i - 1] == text[i + 1]) keep[i] = false
        }
        val last = text.length - 1
        if (text[last] == filler) keep[last] = false
        val sb = StringBuilder()
        for (i in text.indices) if (keep[i]) sb.append(text[i])
        return sb.toString()
    }
}
