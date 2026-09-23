package com.texthub.core.processors

import com.texthub.core.util.asciiLetters
import com.texthub.core.util.asciiLettersAndDigits
import com.texthub.core.util.isAsciiDigit
import com.texthub.core.util.isAsciiLetter
import com.texthub.core.TextProcessor
import com.texthub.core.model.Classification
import com.texthub.core.model.Direction
import com.texthub.core.model.Errors
import com.texthub.core.model.ParamKind
import com.texthub.core.model.ParamSpec
import com.texthub.core.model.ToolCategory
import com.texthub.core.model.ToolInfo
import com.texthub.core.model.ToolMeta

/**
 * Beaufort cipher (and its "variant" form).
 *
 *  - Beaufort:         C = (K - P) mod 26, decrypt with the same operation.
 *  - Variant Beaufort: C = (P - K) mod 26, which is a Vigenère with reversed key.
 */
class BeaufortProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "beaufort",
        name = "Beaufort Cipher",
        glyph = "BFT",
        category = ToolCategory.CLASSICAL,
        classification = Classification.CLASSICAL_CIPHER,
        encodeLabel = "Encrypt",
        decodeLabel = "Decrypt",
        symmetric = true,
        params = listOf(
            ParamSpec(
                key = "key",
                label = "Secret key",
                kind = ParamKind.TEXT,
                defaultValue = "",
                required = true,
                hint = "Letters only, e.g. KEY",
                sensitive = true,
            ),
            ParamSpec(
                key = "variant",
                label = "Form",
                kind = ParamKind.CHOICE,
                defaultValue = "beaufort",
                choices = listOf(
                    com.texthub.core.model.Choice("beaufort", "Beaufort (K − P)"),
                    com.texthub.core.model.Choice("variant", "Variant Beaufort (P − K)"),
                ),
            ),
        ),
        info = ToolInfo(
            summary = "The Beaufort cipher is a reciprocal polyalphabetic cipher: the key letter is " +
                "subtracted from the plaintext letter instead of being added to it, so encrypting and " +
                "decrypting use exactly the same operation.",
            requiresKey = true,
            useCases = listOf(
                "Classical cryptography lessons",
                "Reciprocal cipher demonstrations",
            ),
            warnings = listOf("A classical cipher - not modern secure encryption."),
            convention = "Letters only consume key characters; spaces, digits, punctuation and case are preserved.",
        ),
        keywords = listOf("beaufort", "reciprocal", "polyalphabetic", "vigenere"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val key = (params["key"] ?: "").asciiLetters()
        if (key.isEmpty()) throw Errors.missingKey()
        val variant = params["variant"] == "variant"
        val shifts = key.map { it - 'A' }
        var i = 0
        val sb = StringBuilder(input.length)
        for (c in input) {
            when (c) {
                in 'a'..'z' -> {
                    val k = shifts[i % shifts.size]
                    val p = c - 'a'
                    val v = when {
                        // Beaufort is reciprocal (K - P both ways); Variant Beaufort encrypts with
                        // P - K and therefore decrypts with P + K.
                        variant && direction == Direction.ENCODE -> (p - k).mod26()
                        variant -> (p + k) % 26
                        else -> (k - p).mod26()
                    }
                    sb.append(('a'.code + v).toChar()); i++
                }
                in 'A'..'Z' -> {
                    val k = shifts[i % shifts.size]
                    val p = c - 'A'
                    val v = when {
                        // Beaufort is reciprocal (K - P both ways); Variant Beaufort encrypts with
                        // P - K and therefore decrypts with P + K.
                        variant && direction == Direction.ENCODE -> (p - k).mod26()
                        variant -> (p + k) % 26
                        else -> (k - p).mod26()
                    }
                    sb.append(('A'.code + v).toChar()); i++
                }
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun Int.mod26(): Int = ((this % 26) + 26) % 26
}

/**
 * Autokey (autoclave) cipher: the key stream starts with a primer and then continues with the
 * plaintext itself, which removes the repeating pattern of a short Vigenère key.
 */
class AutokeyProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "autokey",
        name = "Autokey Cipher",
        glyph = "AUT",
        category = ToolCategory.CLASSICAL,
        classification = Classification.CLASSICAL_CIPHER,
        encodeLabel = "Encrypt",
        decodeLabel = "Decrypt",
        params = listOf(
            ParamSpec(
                key = "key",
                label = "Primer key",
                kind = ParamKind.TEXT,
                defaultValue = "",
                required = true,
                hint = "e.g. QUEENLY",
                sensitive = true,
            ),
        ),
        info = ToolInfo(
            summary = "Autokey extends the Vigenère idea: after the primer key is used up, the " +
                "plaintext itself becomes the key, so the keystream never repeats.",
            requiresKey = true,
            useCases = listOf(
                "Classical cryptography lessons",
                "Comparing repeating and non-repeating key streams",
            ),
            warnings = listOf(
                "A classical cipher - not modern secure encryption. It is broken by " +
                    "known-plaintext and crib-dragging attacks.",
            ),
            convention = "Only letters advance the keystream; punctuation and case are preserved.",
        ),
        keywords = listOf("autokey", "autoclave", "primer", "vigenere"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val primer = (params["key"] ?: "").asciiLetters()
        if (primer.isEmpty()) throw Errors.missingKey()
        val keyStream = ArrayList<Int>(primer.length + input.length)
        primer.forEach { keyStream.add(it - 'A') }

        val sb = StringBuilder(input.length)
        var i = 0
        for (c in input) {
            when (c) {
                in 'a'..'z' -> {
                    val p = c - 'a'
                    val k = keyStream[i]
                    val v = if (direction == Direction.ENCODE) (p + k) % 26 else ((p - k) % 26 + 26) % 26
                    // Autokey: from the primer onwards the plaintext letters themselves are the key.
                    keyStream.add(if (direction == Direction.ENCODE) p else v)
                    sb.append(('a'.code + v).toChar()); i++
                }
                in 'A'..'Z' -> {
                    val p = c - 'A'
                    val k = keyStream[i]
                    val v = if (direction == Direction.ENCODE) (p + k) % 26 else ((p - k) % 26 + 26) % 26
                    keyStream.add(if (direction == Direction.ENCODE) p else v)
                    sb.append(('A'.code + v).toChar()); i++
                }
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}

/** Gronsfeld cipher: a Vigenère whose key is a sequence of digits (0-9). */
class GronsfeldProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "gronsfeld",
        name = "Gronsfeld Cipher",
        glyph = "GRO",
        category = ToolCategory.CLASSICAL,
        classification = Classification.CLASSICAL_CIPHER,
        encodeLabel = "Encrypt",
        decodeLabel = "Decrypt",
        params = listOf(
            ParamSpec(
                key = "key",
                label = "Digit key",
                kind = ParamKind.TEXT,
                defaultValue = "31415",
                hint = "Digits 0-9 only",
                sensitive = true,
                helper = "Each digit is the shift for the next letter.",
            ),
        ),
        info = ToolInfo(
            summary = "Gronsfeld is a Vigenère variant that uses digits as the key, so the shift " +
                "for every letter is between 0 and 9.",
            requiresKey = true,
            useCases = listOf(
                "Classical cryptography lessons",
                "Numeric-key substitution practice",
            ),
            warnings = listOf("A classical cipher - not modern secure encryption."),
        ),
        keywords = listOf("gronsfeld", "digits", "numeric key", "vigenere"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val digits = (params["key"] ?: "").filter { it in '0'..'9' }.map { it - '0' }
        if (digits.isEmpty()) throw Errors.missingKey()
        var i = 0
        val sb = StringBuilder(input.length)
        for (c in input) {
            when (c) {
                in 'a'..'z' -> {
                    val k = digits[i % digits.size]
                    val v = if (direction == Direction.ENCODE) (c - 'a' + k) % 26 else ((c - 'a' - k) % 26 + 26) % 26
                    sb.append(('a'.code + v).toChar()); i++
                }
                in 'A'..'Z' -> {
                    val k = digits[i % digits.size]
                    val v = if (direction == Direction.ENCODE) (c - 'A' + k) % 26 else ((c - 'A' - k) % 26 + 26) % 26
                    sb.append(('A'.code + v).toChar()); i++
                }
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}

/**
 * Hill cipher with a 2x2 key matrix. The determinant must be coprime with 26 so that the
 * matrix is invertible modulo 26; otherwise the key is rejected with a friendly message.
 */
class HillCipherProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "hill",
        name = "Hill Cipher (2×2)",
        glyph = "HILL",
        category = ToolCategory.CLASSICAL,
        classification = Classification.CLASSICAL_CIPHER,
        encodeLabel = "Encrypt",
        decodeLabel = "Decrypt",
        params = listOf(
            ParamSpec(
                key = "key",
                label = "Key matrix (4 letters)",
                kind = ParamKind.TEXT,
                defaultValue = "HILL",
                hint = "e.g. HILL",
                sensitive = true,
                helper = "Read row by row: ab / cd. Its determinant must be coprime with 26.",
            ),
            ParamSpec(
                key = "padding",
                label = "Padding",
                kind = ParamKind.CHOICE,
                defaultValue = "pad",
                choices = listOf(
                    com.texthub.core.model.Choice("pad", "Pad with X"),
                    com.texthub.core.model.Choice("none", "No padding (reject odd length)"),
                ),
            ),
        ),
        info = ToolInfo(
            summary = "The Hill cipher encrypts blocks of two letters at a time using matrix " +
                "multiplication modulo 26. The 2×2 key matrix must be invertible modulo 26.",
            requiresKey = true,
            useCases = listOf(
                "Linear algebra in cryptography lessons",
                "Demonstrating block ciphers",
            ),
            warnings = listOf(
                "A classical cipher - not modern secure encryption. Known-plaintext attacks " +
                    "recover the key from a few letter pairs.",
            ),
            convention = "Letters only; X is used to pad an odd number of letters.",
        ),
        keywords = listOf("hill", "matrix", "linear", "block cipher"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val letters = (params["key"] ?: "").uppercase().filter { it in 'A'..'Z' }
        if (letters.length != 4) throw Errors.hillSize(4)
        val a = letters[0] - 'A'
        val b = letters[1] - 'A'
        val c = letters[2] - 'A'
        val d = letters[3] - 'A'
        val det = ((a * d - b * c) % 26 + 26) % 26
        val detInv = com.texthub.core.util.modInverse(det, 26)
            ?: throw com.texthub.core.model.ToolException(
                "This key matrix cannot be used: its determinant ($det) shares a factor with 26. " +
                    "Pick a key whose determinant is 1, 3, 5, 7, 9, 11, 15, 17, 19, 21, 23 or 25 (mod 26)."
            )

        val clean = input.uppercase().filter { it in 'A'..'Z' }
        if (clean.isEmpty()) return ""
        val pad = params["padding"] != "none"
        val text = if (clean.length % 2 == 0) clean else {
            if (!pad) throw com.texthub.core.model.ToolException(
                "The Hill cipher works on pairs of letters. Add one more letter or switch padding to X."
            )
            clean + "X"
        }

        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val p1 = text[i] - 'A'
            val p2 = text[i + 1] - 'A'
            if (direction == Direction.ENCODE) {
                out.append(('A'.code + ((a * p1 + b * p2) % 26)).toChar())
                out.append(('A'.code + ((c * p1 + d * p2) % 26)).toChar())
            } else {
                // inverse = detInv * [ d -b ; -c a ]
                val n1 = (detInv * (d * p1 - b * p2)).mod26()
                val n2 = (detInv * (-c * p1 + a * p2)).mod26()
                out.append(('A'.code + n1).toChar())
                out.append(('A'.code + n2).toChar())
            }
            i += 2
        }
        return out.toString()
    }

    private fun Int.mod26(): Int = ((this % 26) + 26) % 26
}

/**
 * Bifid cipher: a Polybius square combined with a transposition, using a period (block size).
 */
class BifidProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "bifid",
        name = "Bifid Cipher",
        glyph = "BIF",
        category = ToolCategory.CLASSICAL,
        classification = Classification.CLASSICAL_CIPHER,
        encodeLabel = "Encrypt",
        decodeLabel = "Decrypt",
        params = listOf(
            ParamSpec(
                key = "key",
                label = "Square key",
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
                min = 2,
                max = 40,
                helper = "Larger periods mix the letters more strongly.",
            ),
        ),
        info = ToolInfo(
            summary = "Bifid writes each letter as a row and column number in a Polybius square, " +
                "then reads the numbers back in a different order to produce the ciphertext.",
            requiresKey = false,
            useCases = listOf(
                "Classical cryptography lessons",
                "Combining substitution with transposition",
            ),
            warnings = listOf("A classical cipher - not modern secure encryption."),
            convention = "5×5 square with I/J combined; the keyword fills the square first.",
        ),
        keywords = listOf("bifid", "polybius", "transposition", "period"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val square = PolybiusSquare.build(params["key"] ?: "", alnum = false)
        val period = (params["period"]?.toIntOrNull() ?: 5).coerceAtLeast(2)
        val clean = input.uppercase().filter { it in square }
        if (clean.isEmpty()) return ""

        val out = StringBuilder(clean.length)
        var start = 0
        while (start < clean.length) {
            val end = minOf(start + period, clean.length)
            val block = clean.substring(start, end)
            val rows = IntArray(block.length)
            val cols = IntArray(block.length)
            block.forEachIndexed { i, ch ->
                val idx = square.indexOf(ch)
                rows[i] = (idx / 5) + 1
                cols[i] = (idx % 5) + 1
            }
            val numbers = IntArray(block.length * 2)
            for (i in block.indices) numbers[i] = rows[i]
            for (i in block.indices) numbers[block.length + i] = cols[i]

            if (direction == Direction.ENCODE) {
                // Read the rows-then-columns stream back as fresh row/column pairs.
                var k = 0
                while (k < numbers.size) {
                    val r = numbers[k] - 1
                    val c = numbers[k + 1] - 1
                    out.append(square[r * 5 + c])
                    k += 2
                }
            } else {
                // Decrypt: write each ciphertext letter as its row/column pair (interleaved), then
                // read the first half of that stream as plaintext rows and the second as columns.
                val flat = IntArray(block.length * 2)
                for (i in block.indices) {
                    val idx = square.indexOf(block[i])
                    flat[2 * i] = (idx / 5) + 1
                    flat[2 * i + 1] = (idx % 5) + 1
                }
                for (i in block.indices) {
                    val rr = flat[i] - 1
                    val cc = flat[block.length + i] - 1
                    out.append(square[rr * 5 + cc])
                }
            }
            start = end
        }
        return out.toString()
    }
}

/** Shared Polybius square construction (5×5 with I/J merged, or 6×6 letters + digits). */
object PolybiusSquare {

    private const val ALPHA_25 = "ABCDEFGHIKLMNOPQRSTUVWXYZ"
    private const val ALPHA_36 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    fun build(key: String, alnum: Boolean): String {
        val base = if (alnum) ALPHA_36 else ALPHA_25
        val seen = LinkedHashSet<Char>()
        for (raw in key.uppercase()) {
            val c = if (!alnum && raw == 'J') 'I' else raw
            if (c in base) seen.add(c)
        }
        for (c in base) seen.add(c)
        return seen.joinToString("")
    }

    fun size(alnum: Boolean): Int = if (alnum) 6 else 5
}

/**
 * Polybius square coordinates: classic "tap code" style row/column pairs.
 */
class PolybiusProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "polybius",
        name = "Polybius Square",
        glyph = "POLY",
        category = ToolCategory.CLASSICAL,
        classification = Classification.CLASSICAL_CIPHER,
        encodeLabel = "Text to Coordinates",
        decodeLabel = "Coordinates to Text",
        params = listOf(
            ParamSpec(
                key = "grid",
                label = "Grid",
                kind = ParamKind.CHOICE,
                defaultValue = "5",
                choices = listOf(
                    com.texthub.core.model.Choice("5", "5×5, I/J combined"),
                    com.texthub.core.model.Choice("alnum", "6×6, letters + digits"),
                ),
            ),
            ParamSpec(
                key = "separator",
                label = "Separator",
                kind = ParamKind.CHOICE,
                defaultValue = "space",
                choices = listOf(
                    com.texthub.core.model.Choice("space", "Space (34 15 31)"),
                    com.texthub.core.model.Choice("none", "None (341531)"),
                    com.texthub.core.model.Choice("dash", "Dash (34-15-31)"),
                ),
            ),
            ParamSpec(
                key = "key",
                label = "Square key",
                kind = ParamKind.TEXT,
                defaultValue = "",
                hint = "Optional keyword",
                sensitive = true,
            ),
        ),
        info = ToolInfo(
            summary = "A Polybius square maps each letter to a row and column number, which is " +
                "how tap code is transmitted. Letters are shown as digit pairs such as 34 for 'H'.",
            requiresKey = false,
            useCases = listOf(
                "Tap code and prisoner-of-war signalling",
                "Introducing coordinate based ciphers",
            ),
            warnings = listOf("A classical cipher - not modern secure encryption."),
            convention = "Rows and columns are numbered 1-based from the top left.",
        ),
        keywords = listOf("polybius", "tap code", "coordinates", "square"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val alnum = params["grid"] == "alnum"
        val size = PolybiusSquare.size(alnum)
        val square = PolybiusSquare.build(params["key"] ?: "", alnum)
        val separator = when (params["separator"]) {
            "none" -> ""
            "dash" -> "-"
            else -> " "
        }

        if (direction == Direction.ENCODE) {
            val parts = ArrayList<String>()
            for (word in input.uppercase().split(Regex("\\s+")).filter { it.isNotEmpty() }) {
                val coords = StringBuilder()
                for (c in word) {
                    val ch = if (!alnum && c == 'J') 'I' else c
                    val idx = square.indexOf(ch)
                    if (idx < 0) continue
                    val row = idx / size + 1
                    val col = idx % size + 1
                    if (coords.isNotEmpty() && separator.isNotEmpty()) coords.append(separator)
                    coords.append(row).append(col)
                }
                if (coords.isNotEmpty()) parts.add(coords.toString())
            }
            // Words are separated by a slash so decoding can put the spaces back.
            return if (separator.isEmpty()) parts.joinToString("/") else parts.joinToString(" / ")
        }

        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        val groups = trimmed.split('/')
        val sb = StringBuilder()
        for (group in groups) {
            val digits = group.filter { it.isDigit() }
            if (digits.isEmpty()) continue
            if (digits.length % 2 != 0) {
                throw com.texthub.core.model.ToolException(
                    "Coordinates must come in pairs, for example 23 15 31. One group here has an " +
                        "odd number of digits."
                )
            }
            var i = 0
            while (i < digits.length) {
                val row = digits[i] - '0'
                val col = digits[i + 1] - '0'
                if (row < 1 || row > size || col < 1 || col > size) {
                    throw com.texthub.core.model.ToolException(
                        "Coordinate $row$col is outside the ${size}×${size} grid. Rows and columns " +
                            "run from 1 to $size."
                    )
                }
                sb.append(square[(row - 1) * size + (col - 1)])
                i += 2
            }
            sb.append(' ')
        }
        return sb.toString().trim()
    }
}

/**
 * Caesar brute force: shows every possible shift so an unknown Caesar ciphertext can be read
 * off directly. This is a practical analysis helper rather than a cipher.
 */
class CaesarBruteForceProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "caesarbrute",
        name = "Caesar Brute Force",
        glyph = "BRUTE",
        category = ToolCategory.CLASSICAL,
        classification = Classification.TEXT_TRANSFORM,
        encodeLabel = "Show all shifts",
        decodeLabel = "Show all shifts",
        symmetric = true,
        info = ToolInfo(
            summary = "Prints all 25 possible Caesar shifts of the input, one per line, so you " +
                "can spot the readable one instead of guessing the shift.",
            requiresKey = false,
            useCases = listOf(
                "Breaking an unknown Caesar cipher",
                "Showing why a 25-key cipher is trivially broken",
            ),
            warnings = listOf(
                "This does not protect anything - it demonstrates that Caesar has only 25 keys.",
            ),
            convention = "Each line is numbered with the shift that produced it.",
        ),
        keywords = listOf("caesar", "bruteforce", "all shifts", "crack"),
        oneWay = true,
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        if (input.isEmpty()) return ""
        val sb = StringBuilder()
        for (shift in 1..25) {
            sb.append(shift.toString().padStart(2, ' ')).append(": ")
            for (c in input) {
                sb.append(
                    when (c) {
                        in 'a'..'z' -> (((c - 'a' - shift) % 26 + 26) % 26 + 'a'.code).toChar()
                        in 'A'..'Z' -> (((c - 'A' - shift) % 26 + 26) % 26 + 'A'.code).toChar()
                        else -> c
                    }
                )
            }
            sb.append('\n')
        }
        return sb.toString().trimEnd('\n')
    }
}
