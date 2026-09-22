package com.texthub.core.processors

import com.texthub.core.TextProcessor
import com.texthub.core.codec.Base45Codec
import com.texthub.core.codec.HtmlEntityCodec
import com.texthub.core.codec.QuotedPrintableCodec
import com.texthub.core.codec.UnicodeEscapeCodec
import com.texthub.core.model.Choice
import com.texthub.core.model.Classification
import com.texthub.core.model.Direction
import com.texthub.core.model.Errors
import com.texthub.core.model.ParamKind
import com.texthub.core.model.ParamSpec
import com.texthub.core.model.ToolCategory
import com.texthub.core.model.ToolException
import com.texthub.core.model.ToolInfo
import com.texthub.core.model.ToolMeta
import com.texthub.core.util.isAsciiDigit
import com.texthub.core.util.isAsciiLetter
import com.texthub.core.util.utf8Bytes
import com.texthub.core.util.utf8String

/** Base45 (RFC 9285) - the compact encoding used inside QR codes. */
class Base45Processor : TextProcessor {

    override val meta = ToolMeta(
        id = "base45",
        name = "Base45",
        glyph = "45",
        category = ToolCategory.ENCODING,
        classification = Classification.ENCODING,
        encodeLabel = "Encode",
        decodeLabel = "Decode",
        info = ToolInfo(
            summary = "Base45 packs two bytes into three of 45 allowed characters (digits, A-Z " +
                "and a few symbols). It is designed to survive QR code alphanumeric mode, which is " +
                "why vaccination certificates use it.",
            requiresKey = false,
            useCases = listOf(
                "Compact payloads inside QR codes",
                "Compatibility with EU DCC style data",
            ),
            warnings = listOf("Encoding, not encryption."),
        ),
        keywords = listOf("base45", "rfc9285", "qr", "dcc"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String =
        if (direction == Direction.ENCODE) {
            Base45Codec.encode(input.utf8Bytes())
        } else {
            Base45Codec.decode(input).utf8String()
        }
}

/** Quoted-printable (RFC 2045), still used by email clients. */
class QuotedPrintableProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "quotedprintable",
        name = "Quoted-Printable",
        glyph = "QP",
        category = ToolCategory.ENCODING,
        classification = Classification.ENCODING,
        encodeLabel = "Encode",
        decodeLabel = "Decode",
        info = ToolInfo(
            summary = "Quoted-printable keeps ordinary text readable and escapes only what has to " +
                "be escaped, using =XX for bytes outside the safe range and soft line breaks for " +
                "long lines. It is the standard way email bodies carry non-ASCII text.",
            requiresKey = false,
            useCases = listOf(
                "Reading raw email source",
                "Encoding text for MIME parts",
            ),
            warnings = listOf("Encoding, not encryption."),
        ),
        keywords = listOf("quoted printable", "qp", "mime", "rfc2045", "email"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String =
        if (direction == Direction.ENCODE) QuotedPrintableCodec.encode(input)
        else QuotedPrintableCodec.decode(input)
}

/** HTML / XML entities. */
class HtmlEntityProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "htmlentities",
        name = "HTML Entities",
        glyph = "&lt;",
        category = ToolCategory.ENCODING,
        classification = Classification.ENCODING,
        encodeLabel = "Encode",
        decodeLabel = "Decode",
        params = listOf(
            ParamSpec(
                key = "mode",
                label = "Escape level",
                kind = ParamKind.CHOICE,
                defaultValue = "all",
                choices = listOf(
                    Choice("all", "Named entities where possible (&copy;, &mdash;)"),
                    Choice("minimal", "Only safe characters (&amp; &lt; &gt; &quot;)"),
                ),
            ),
        ),
        info = ToolInfo(
            summary = "HTML entities let text be embedded safely in markup: reserved characters " +
                "become named entities such as &amp; and anything else can be written numerically " +
                "as &#233; or &#xE9;.",
            requiresKey = false,
            useCases = listOf(
                "Escaping text before putting it in HTML",
                "Reading escaped markup or web scrapes",
            ),
            warnings = listOf("Encoding, not encryption."),
        ),
        keywords = listOf("html", "entities", "escape", "xml", "amp"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String =
        if (direction == Direction.ENCODE) HtmlEntityCodec.encode(input, params["mode"] ?: "all")
        else HtmlEntityCodec.decode(input)
}

/** \uXXXX style escape sequences used in source code. */
class UnicodeEscapeProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "unicodeescape",
        name = "Unicode Escapes",
        glyph = "\\u",
        category = ToolCategory.ENCODING,
        classification = Classification.ENCODING,
        encodeLabel = "Text to Escapes",
        decodeLabel = "Escapes to Text",
        params = listOf(
            ParamSpec(
                key = "mode",
                label = "Notation",
                kind = ParamKind.CHOICE,
                defaultValue = "js",
                choices = listOf(
                    Choice("js", "JavaScript \\uXXXX + surrogate pairs"),
                    Choice("braced", "\\uXXXX and \\u{1F600}"),
                    Choice("python", "Python \\uXXXX and \\U0001F600"),
                ),
            ),
        ),
        info = ToolInfo(
            summary = "Converts text into the escape sequence form used inside source code and " +
                "JSON, and converts escapes back into text. Characters above U+FFFF are written as " +
                "surrogate pairs (or \\u{...} / \\U........ depending on the notation you pick).",
            requiresKey = false,
            useCases = listOf(
                "Pasting safe strings into code or JSON",
                "Reading escaped log lines and config files",
            ),
            warnings = listOf("Encoding, not encryption."),
        ),
        keywords = listOf("unicode", "escape", "js", "json", "u0041", "backslash"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String =
        if (direction == Direction.ENCODE) {
            UnicodeEscapeCodec.encode(input, params["mode"] ?: UnicodeEscapeCodec.MODE_JS)
        } else {
            UnicodeEscapeCodec.decode(input)
        }
}

/** NATO / ICAO spelling alphabet ("Alpha Bravo Charlie"). */
class NatoPhoneticProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "nato",
        name = "NATO Phonetic",
        glyph = "ALFA",
        category = ToolCategory.ENCODING,
        classification = Classification.TEXT_TRANSFORM,
        encodeLabel = "Text to Phonetic",
        decodeLabel = "Phonetic to Text",
        params = listOf(
            ParamSpec(
                key = "alphabet",
                label = "Spelling alphabet",
                kind = ParamKind.CHOICE,
                defaultValue = "nato",
                choices = listOf(
                    Choice("nato", "NATO / ICAO (Alfa, Bravo, Charlie)"),
                    Choice("old", "Pre-1956 US (Able, Baker, Charlie)"),
                ),
            ),
        ),
        info = ToolInfo(
            summary = "The NATO spelling alphabet names each letter with a word that survives radio " +
                "noise (Alfa, Bravo, Charlie...). Digits use their English names.",
            requiresKey = false,
            useCases = listOf(
                "Spelling a code or reference out loud",
                "Amateur radio and aviation practice",
            ),
            warnings = listOf("Not a cipher - it hides nothing and protects nothing."),
        ),
        keywords = listOf("nato", "phonetic", "spelling", "aviation", "alfa", "bravo"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val table = if (params["alphabet"] == "old") OLD else NATO
        if (direction == Direction.ENCODE) {
            val words = ArrayList<String>()
            val unsupported = StringBuilder()
            for (ch in input) {
                when {
                    // Only ASCII A-Z maps to the table: Char.isLetter() would also accept ü, न, ٣
                    // and index far outside the 26 entries.
                    ch.isAsciiLetter() -> words.add(table[ch.uppercaseChar() - 'A'])
                    ch.isAsciiDigit() -> words.add(DIGITS[ch - '0'])
                    ch.isWhitespace() -> words.add("/")
                    ch.isLetterOrDigit() -> if (!unsupported.contains(ch)) unsupported.append(ch)
                }
            }
            if (unsupported.isNotEmpty()) throw Errors.natoUnsupported(unsupported.toString())
            return words.joinToString(" ")
        }
        val reverse = table.withIndex().associate { (i, word) -> word.lowercase() to ('A'.code + i).toChar() }
        val sb = StringBuilder()
        for (token in input.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }) {
            when {
                token == "/" -> sb.append(' ')
                token.toIntOrNull() != null && token.length == 1 -> sb.append(token)
                DIGITS.indexOfFirst { it.equals(token, ignoreCase = true) } >= 0 ->
                    sb.append(DIGITS.indexOfFirst { it.equals(token, ignoreCase = true) })
                reverse.containsKey(token.lowercase()) -> sb.append(reverse[token.lowercase()])
                else -> throw ToolException("Unrecognised phonetic word: \"$token\". Use the words from the selected alphabet.")
            }
        }
        return sb.toString()
    }

    companion object {
        val NATO = arrayOf(
            "Alfa", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Golf", "Hotel",
            "India", "Juliett", "Kilo", "Lima", "Mike", "November", "Oscar", "Papa",
            "Quebec", "Romeo", "Sierra", "Tango", "Uniform", "Victor", "Whiskey",
            "X-ray", "Yankee", "Zulu",
        )
        val OLD = arrayOf(
            "Able", "Baker", "Charlie", "Dog", "Easy", "Fox", "George", "How",
            "Item", "Jig", "King", "Love", "Mike", "Nan", "Oboe", "Peter",
            "Queen", "Roger", "Sugar", "Tare", "Uncle", "Victor", "William",
            "X-ray", "Yoke", "Zebra",
        )
        val DIGITS = arrayOf("Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine")
    }

}

/** Leetspeak (1337) substitution - a look, not a protection. */
class LeetProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "leet",
        name = "Leetspeak (1337)",
        glyph = "1337",
        category = ToolCategory.TRANSFORM,
        classification = Classification.OBFUSCATION,
        encodeLabel = "To Leet",
        decodeLabel = "From Leet",
        params = listOf(
            ParamSpec(
                key = "level",
                label = "Intensity",
                kind = ParamKind.CHOICE,
                defaultValue = "light",
                choices = listOf(
                    Choice("light", "Light (a e i o s t)"),
                    Choice("heavy", "Heavy (also b g l z)"),
                ),
            ),
        ),
        info = ToolInfo(
            summary = "Leetspeak replaces letters with similar looking digits and symbols (e to 3, " +
                "o to 0, s to 5). It is a style, not a cipher - and the reverse direction is a " +
                "best effort, because the same digit can stand for several letters.",
            requiresKey = false,
            useCases = listOf(
                "Stylising text for fun",
                "Making text harder to pick up in casual searches",
            ),
            warnings = listOf(
                "The level is a presentation choice that is not stored in the text, so decoding with another level maps the digits and letters differently.",
                "Obfuscation only, and not reversible in every case: decoding maps 1 to l, 3 to e, " +
                    "4 to a, 5 to s, 7 to t, 0 to o.",
            ),
        ),
        keywords = listOf("leet", "1337", "h4x", "obfuscate", "style"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val heavy = params["level"] == "heavy"
        if (direction == Direction.ENCODE) {
            val sb = StringBuilder(input.length)
            for (raw in input) {
                val lower = raw.lowercaseChar()
                val replacement = LEET[lower]?.takeIf { heavy || it !in HEAVY_ONLY }
                if (replacement != null) sb.append(replacement) else sb.append(raw)
            }
            return sb.toString()
        }
        // Several letters share a digit (i and l both become "1"), so decoding maps each digit
        // to one fixed letter: the alphabetically first one.
        val reverse: Map<Char, Char> = LEET.entries
            .groupBy { entry -> entry.value.single() }
            .mapValues { (_, entries) -> entries.minByOrNull { entry -> entry.key.code }?.key ?: 'l' }
        val sb = StringBuilder(input.length)
        for (c in input) sb.append(reverse[c] ?: c)
        return sb.toString()
    }

    companion object {
        private val LEET: Map<Char, String> = mapOf(
            'a' to "4", 'b' to "8", 'e' to "3", 'g' to "6", 'i' to "1", 'l' to "1",
            'o' to "0", 's' to "5", 't' to "7", 'z' to "2",
        )
        private val HEAVY_ONLY = setOf("8", "6", "1", "2")
    }
}
