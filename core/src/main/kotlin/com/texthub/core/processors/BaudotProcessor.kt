package com.texthub.core.processors

import com.texthub.core.TextProcessor
import com.texthub.core.codec.BaudotTables
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

/**
 * Baudot / International Telegraph Alphabet No. 2 (ITA2).
 *
 * Real 5-bit stateful encoding: the letter page (LTRS, 11111) and figure page (FIGS, 11011)
 * are switched explicitly by the corresponding shift codes, exactly like a teleprinter.
 * Characters that have no code in the selected variant are reported (or skipped) instead of
 * being silently replaced with an arbitrary binary value.
 */
class BaudotProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "baudot",
        name = "Baudot / ITA2",
        glyph = "ITA",
        category = ToolCategory.ENCODING,
        classification = Classification.HISTORICAL_ENCODING,
        encodeLabel = "Text to Baudot",
        decodeLabel = "Baudot to Text",
        params = listOf(
            ParamSpec(
                key = "variant",
                label = "Code standard",
                kind = ParamKind.CHOICE,
                defaultValue = "ita2",
                choices = listOf(
                    Choice("ita2", "ITA2 (International)"),
                    Choice("ustty", "US TTY (American)"),
                ),
                helper = "The figure page differs between ITA2 and US TTY; the tables are never mixed.",
            ),
            ParamSpec(
                key = "format",
                label = "Group formatting",
                kind = ParamKind.CHOICE,
                defaultValue = "space",
                choices = listOf(
                    Choice("space", "Space separated"),
                    Choice("continuous", "Continuous"),
                    Choice("line", "One group per line"),
                ),
            ),
            ParamSpec(
                key = "unknown",
                label = "Unsupported characters",
                kind = ParamKind.CHOICE,
                defaultValue = "error",
                choices = listOf(
                    Choice("error", "Show an error"),
                    Choice("skip", "Skip them"),
                ),
            ),
        ),
        detection = listOf(
            DetectionHint(
                alphabet = "01",
                minLength = 10,
                label = "Baudot/ITA2 (5-bit groups)",
                recognise = { text ->
                    val digits = text.count { it == '0' || it == '1' }
                    digits >= 10 && digits % 5 == 0
                },
                evidence = "Only 0 and 1 are present and they group into 5-bit Baudot/ITA2 characters.",
            ),
        ),
        info = ToolInfo(
            summary = "Baudot / ITA2 is a historical 5-bit character encoding from the teleprinter " +
                "era. It uses two pages - letters and figures - switched with the LTRS (11111) " +
                "and FIGS (11011) codes.",
            requiresKey = false,
            useCases = listOf(
                "Amateur radio RTTY practice",
                "Studying historical telegraphy",
                "Decoding legacy 5-bit tape",
            ),
            warnings = listOf(
                "Baudot/ITA2 is a historical character encoding system used in telegraphy. " +
                    "It is an encoding method, not encryption.",
                "Control characters (NUL, BEL, ENQ, CR, LF) keep their real code values so the " +
                    "text round trips exactly.",
            ),
            convention = "Encoder also accepts the names <NUL>, <BEL>, <ENQ>, <CR> and <LF>.",
        ),
        keywords = listOf("baudot", "ita2", "rtty", "teleprinter", "5-bit", "teletype"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val variant = BaudotTables.variantFor(params["variant"] ?: "ita2")
        val separator = when (params["format"]) {
            "continuous" -> ""
            "line" -> "\n"
            else -> " "
        }
        val skipUnknown = params["unknown"] == "skip"
        return if (direction == Direction.ENCODE) encode(input, variant, separator, skipUnknown)
        else decode(input, variant)
    }

    private fun encode(text: String, variant: BaudotTables.Variant, separator: String, skipUnknown: Boolean): String {
        val letters = BaudotTables.LETTERS
        val figures = BaudotTables.figures(variant)
        val codes = ArrayList<Int>()
        var lettersMode = true

        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '<') {
                val end = text.indexOf('>', i)
                if (end > i) {
                    val named = BaudotTables.namedControl(text.substring(i, end + 1))
                    if (named != null) {
                        val idx = if (named == BaudotTables.SP) 4 else indexOf(letters, named)
                        if (idx >= 0) codes.add(idx)
                        i = end + 1
                        continue
                    }
                }
            }
            val ch = c.uppercaseChar()
            var idx = indexOf(if (lettersMode) letters else figures, ch)
            if (idx < 0) {
                // try the other page
                val otherIdx = indexOf(if (lettersMode) figures else letters, ch)
                if (otherIdx >= 0 && otherIdx != BaudotTables.LTRS_CODE && otherIdx != BaudotTables.FIGS_CODE) {
                    codes.add(if (lettersMode) BaudotTables.FIGS_CODE else BaudotTables.LTRS_CODE)
                    lettersMode = !lettersMode
                    idx = otherIdx
                }
            }
            if (idx < 0) {
                if (skipUnknown) { i++; continue }
                throw Errors.baudotUnsupported(BaudotTables.displayName(ch))
            }
            codes.add(idx)
            i++
        }
        return codes.joinToString(separator) { it.toBinaryString() }
    }

    private fun decode(input: String, variant: BaudotTables.Variant): String {
        val groups = parseGroups(input)
        val letters = BaudotTables.LETTERS
        val figures = BaudotTables.figures(variant)
        val sb = StringBuilder()
        var lettersMode = true
        for (code in groups) {
            when (code) {
                BaudotTables.LTRS_CODE -> lettersMode = true
                BaudotTables.FIGS_CODE -> lettersMode = false
                0 -> Unit // NUL: idle state of the teleprinter
                else -> sb.append(if (lettersMode) letters[code] else figures[code])
            }
        }
        return sb.toString()
    }

    private fun parseGroups(input: String): List<Int> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return emptyList()
        return if (trimmed.any { it.isWhitespace() }) {
            trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }.map { token ->
                if (token.length != 5 || token.any { it != '0' && it != '1' }) throw Errors.baudotGroup()
                token.toInt(2)
            }
        } else {
            if (trimmed.any { it != '0' && it != '1' }) throw Errors.baudotGroup()
            if (trimmed.length % 5 != 0) throw Errors.baudotGroup()
            trimmed.chunked(5).map { it.toInt(2) }
        }
    }

    private fun indexOf(page: Array<Char>, c: Char): Int {
        // Codes 27 (FIGS) and 31 (LTRS) are shift controls and never match a character.
        for (i in page.indices) {
            if (i == BaudotTables.FIGS_CODE || i == BaudotTables.LTRS_CODE) continue
            if (page[i] == c) return i
        }
        return -1
    }

    private fun Int.toBinaryString(): String =
        (4 downTo 0).joinToString("") { if ((this ushr it) and 1 == 1) "1" else "0" }
}
