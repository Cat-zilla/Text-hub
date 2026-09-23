package com.texthub.core.processors

import com.texthub.core.TextProcessor
import com.texthub.core.codec.Base91Codec
import com.texthub.core.codec.BrailleCodec
import com.texthub.core.codec.HexDumpCodec
import com.texthub.core.codec.PunycodeCodec
import com.texthub.core.codec.RomanCodec
import com.texthub.core.codec.Utf16Codec
import com.texthub.core.codec.Utf32Codec
import com.texthub.core.model.Choice
import com.texthub.core.model.Classification
import com.texthub.core.model.Direction
import com.texthub.core.model.Errors
import com.texthub.core.model.ParamKind
import com.texthub.core.model.ParamSpec
import com.texthub.core.model.ToolCategory
import com.texthub.core.model.ToolInfo
import com.texthub.core.model.ToolMeta
import com.texthub.core.detector.ACE_PREFIX
import com.texthub.core.detector.aceLabels
import com.texthub.core.detector.domainLabels
import com.texthub.core.detector.tokensAre
import com.texthub.core.model.DetectionHint

/** Base91 - a denser printable encoding than Base85, used by some binary-to-text pipelines. */
class Base91Processor : TextProcessor {

    override val meta = ToolMeta(
        id = "base91",
        name = "Base91",
        glyph = "B91",
        category = ToolCategory.ENCODING,
        classification = Classification.ENCODING,
        encodeLabel = "Encode",
        decodeLabel = "Decode",
        params = emptyList(),
        detection = listOf(
            DetectionHint(
                alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789" +
                    "!#$%&()*+,./:;<=>?@[]^_`{|}~\"",
                minLength = 6,
                label = "Base91",
                evidence = "Every character belongs to the Base91 alphabet, which packs two characters " +
                    "into roughly 13 bits.",
            ),
        ),
        info = ToolInfo(
            summary = "Base91 packs roughly 13 bits into every two characters, so it is denser " +
                "than Base64 and stays copy-paste safe. It is an encoding: reversible, not secret.",
            requiresKey = false,
            useCases = listOf(
                "Embedding binary data in text with less overhead than Base64",
                "Comparing encoding densities",
            ),
            warnings = listOf("Encoding, not encryption - anyone can decode it."),
            convention = "Standard Base91 alphabet (Joachim Henke), whitespace ignored when decoding.",
        ),
        keywords = listOf("base91", "density", "binary to text", "encode"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String =
        if (direction == Direction.ENCODE) {
            Base91Codec.encode(input.toByteArray(Charsets.UTF_8))
        } else {
            Base91Codec.decode(input).toString(Charsets.UTF_8)
        }
}

/** Punycode (RFC 3492), the encoding behind internationalised domain names such as münchen.de. */
class PunycodeProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "punycode",
        name = "Punycode",
        glyph = "PUN",
        category = ToolCategory.ENCODING,
        classification = Classification.ENCODING,
        encodeLabel = "Encode",
        decodeLabel = "Decode",
        params = emptyList(),
        detection = listOf(
            DetectionHint(
                labelFor = { text ->
                    if (domainLabels(text).size == 1) "Punycode (ACE) label" else "Internationalised domain name"
                },
                recognise = { text -> aceLabels(text).isNotEmpty() },
                evidenceFor = { text ->
                    val labels = aceLabels(text)
                    if (labels.size == 1) {
                        "The label carries the \"$ACE_PREFIX\" ACE prefix used for internationalised " +
                            "domain names."
                    } else {
                        "The label \"${labels.first().take(24)}\" carries the \"$ACE_PREFIX\" ACE prefix; " +
                            "each label of a domain is decoded on its own."
                    }
                },
                structural = true,
                runnableFor = { text -> domainLabels(text).size == 1 },
                unrunnableNote = "That is a whole internationalised domain name, and the ACE prefix " +
                    "belongs to one label at a time. Paste the single label you want to read (for " +
                    "example xn--mnchen-3ya) and Text Hub decodes it.",
            ),
        ),
        info = ToolInfo(
            summary = "Punycode turns non-ASCII text into an ASCII-only form, which is how domain " +
                "names with accents or non-Latin scripts are transmitted (xn--mnchen-3ya.de).",
            requiresKey = false,
            useCases = listOf(
                "Reading and writing internationalised domain names",
                "Understanding an IDN label before trusting a link",
            ),
            warnings = listOf(
                "Encoding, not encryption. Punycode is also how lookalike domain names are built, " +
                    "so check a decoded label before opening a link.",
            ),
            convention = "RFC 3492. The xn-- prefix is added by the DNS rather than by this codec, " +
                "so decoding accepts labels with or without it.",
        ),
        keywords = listOf("punycode", "idn", "domain", "internationalised", "rfc3492"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        if (direction == Direction.ENCODE) return PunycodeCodec.encode(input)
        // Domain names carry the "xn--" ACE prefix, which the DNS adds rather than this codec. A
        // label pasted straight out of a browser or a certificate must therefore still decode, so
        // the prefix is stripped per label before decoding.
        val cleaned = input.split('.').joinToString(".") { label ->
            if (label.startsWith(ACE_PREFIX, ignoreCase = true)) label.substring(ACE_PREFIX.length) else label
        }
        return PunycodeCodec.decode(cleaned)
    }

}

/** Braille cell display: every code unit shown as four 8-dot cells. */
class BrailleProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "braille",
        name = "Braille Bits",
        glyph = "⠿",
        category = ToolCategory.ENCODING,
        classification = Classification.ENCODING,
        encodeLabel = "To Braille cells",
        decodeLabel = "From Braille cells",
        params = emptyList(),
        detection = listOf(
            DetectionHint(
                minLength = 2,
                label = "Braille (Unicode)",
                recognise = { text ->
                    val braille = text.count { it.code in 0x2800..0x28FF }
                    braille >= 2 && braille * 2 >= text.length
                },
                evidenceFor = { text ->
                    val braille = text.count { it.code in 0x2800..0x28FF }
                    "$braille characters come from the Unicode Braille block (U+2800-U+28FF)."
                },
                strongWhen = { text ->
                    text.all { it.code in 0x2800..0x28FF || it == ' ' || it == '\n' || it == '\r' || it == '\t' }
                },
            ),
        ),
        info = ToolInfo(
            summary = "Shows each 16-bit code unit as four Braille cells, one hexadecimal nibble " +
                "each (dots 1-4 are the high nibble, dots 5-8 the low nibble). The Unicode Braille " +
                "block maps its eight dots onto eight bits, so this is exact and reversible.",
            requiresKey = false,
            useCases = listOf(
                "A compact visual bit display that survives copy and paste",
                "Fun with the Braille Patterns block",
            ),
            warnings = listOf(
                "This is a bit display, not contracted literary Braille, and not encryption.",
            ),
            convention = "Unicode Braille Patterns U+2800 + value; dots 1…8 are bits 0…7 of each nibble cell.",
        ),
        keywords = listOf("braille", "bits", "dots", "unicode", "display"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String =
        if (direction == Direction.ENCODE) BrailleCodec.encode(input) else BrailleCodec.decode(input)
}

/** Roman numerals: numbers to numerals and back, strictly validated. */
class RomanProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "roman",
        name = "Roman Numerals",
        glyph = "XII",
        category = ToolCategory.ENCODING,
        classification = Classification.ENCODING,
        encodeLabel = "To Roman",
        decodeLabel = "To numbers",
        params = emptyList(),
        detection = listOf(
            DetectionHint(
                alphabet = "IVXLCDMivxlcdm",
                minLength = 2,
                label = "Roman numerals",
                evidence = "Only the Roman numeral letters I, V, X, L, C, D and M are present, and they " +
                    "parse as a number.",
            ),
        ),
        info = ToolInfo(
            summary = "Converts numbers between 1 and 3999 to Roman numerals and back. Tokens " +
                "that are not numbers (or not valid numerals) are left untouched, and malformed " +
                "numerals such as IIII are rejected rather than guessed.",
            requiresKey = false,
            useCases = listOf(
                "Reading old dates, chapters and clock faces",
                "Checking whether a numeral like MCMXCIV is well formed",
            ),
            warnings = listOf("A number format, not an encoding of text and certainly not encryption."),
            convention = "Standard subtractive notation; values outside 1–3999 have no classical form.",
        ),
        keywords = listOf("roman", "numerals", "mcmxciv", "numbers", "convert"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val tokens = input.split(Regex("(?<=\\s)|(?=\\s)"))
        return tokens.joinToString("") { token ->
            if (token.isBlank()) return@joinToString token
            if (direction == Direction.ENCODE) {
                val value = token.toIntOrNull()
                if (value != null) RomanCodec.toRoman(value) else token
            } else {
                if (RomanCodec.looksRoman(token)) RomanCodec.fromRoman(token).toString() else token
            }
        }
    }
}

/** UTF-16 code units - what most programming languages actually store in a string. */
class Utf16Processor : TextProcessor {

    override val meta = ToolMeta(
        id = "utf16",
        name = "UTF-16 Units",
        glyph = "U16",
        category = ToolCategory.ENCODING,
        classification = Classification.ENCODING,
        encodeLabel = "To UTF-16 units",
        decodeLabel = "From UTF-16 units",
        params = listOf(
            ParamSpec(
                key = "format",
                label = "Format",
                kind = ParamKind.CHOICE,
                defaultValue = Utf16Codec.FORMAT_HEX,
                choices = listOf(
                    Choice(Utf16Codec.FORMAT_HEX, "Hex units (0041 0042)"),
                    Choice(Utf16Codec.FORMAT_DECIMAL, "Decimal values (65 66)"),
                    Choice(Utf16Codec.FORMAT_CSHARP, "Escapes (\\u0041\\u0042)"),
                ),
            ),
        ),
        detection = listOf(
            DetectionHint(
                alphabet = "0123456789abcdefABCDEF ,;:|\t\n",
                minLength = 9,
                label = "UTF-16 code units (hex)",
                recognise = { text -> tokensAre(text, width = 4) },
                evidence = "Every value is a 4-digit hexadecimal unit, which is how UTF-16 is written here.",
                paramsFor = { mapOf("format" to "hex") },
            ),
        ),
        info = ToolInfo(
            summary = "Every 16-bit UTF-16 code unit of your text, including the surrogate pairs " +
                "that make an emoji two units. This is the layer where most \"length\" bugs live.",
            requiresKey = false,
            useCases = listOf(
                "Debugging string length and substring problems",
                "Seeing why an emoji counts as two characters in Java, C# or Kotlin",
            ),
            warnings = listOf("Encoding, not encryption."),
            convention = "Decoding expects the format you encoded with; escapes accept \\uXXXX and \\xXX.",
        ),
        keywords = listOf("utf-16", "surrogate", "code unit", "string length", "emoji"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val format = params["format"] ?: Utf16Codec.FORMAT_HEX
        return if (direction == Direction.ENCODE) {
            Utf16Codec.encode(input, format)
        } else {
            Utf16Codec.decode(normalizeEscapes(input), format)
        }
    }

    private fun normalizeEscapes(text: String): String = text
        .replace(Regex("\\\\x([0-9a-fA-F]{2})"), "\\\\u00$1")
}

/** UTF-32 code points - each Unicode character as one number, so an emoji stays a single value. */
class Utf32Processor : TextProcessor {

    override val meta = ToolMeta(
        id = "utf32",
        name = "UTF-32 Code Points",
        glyph = "U32",
        category = ToolCategory.ENCODING,
        classification = Classification.ENCODING,
        encodeLabel = "To code points",
        decodeLabel = "From code points",
        params = listOf(
            ParamSpec(
                key = "format",
                label = "Format",
                kind = ParamKind.CHOICE,
                defaultValue = Utf32Codec.FORMAT_HEX,
                choices = listOf(
                    Choice(Utf32Codec.FORMAT_HEX, "Hex (0001F600)"),
                    Choice(Utf32Codec.FORMAT_DECIMAL, "Decimal (128512)"),
                    Choice(Utf32Codec.FORMAT_CSHARP, "Escapes (\\U0001F600)"),
                ),
            ),
        ),
        detection = listOf(
            DetectionHint(
                alphabet = "0123456789abcdefABCDEF ,;:|\t\n",
                minLength = 17,
                label = "Unicode code points (hex)",
                recognise = { text -> tokensAre(text, width = 8) },
                evidence = "Every value is an 8-digit hexadecimal code point.",
                paramsFor = { mapOf("format" to "hex") },
            ),
        ),
        info = ToolInfo(
            summary = "One number per Unicode code point, astral characters included, which is " +
                "what you want when comparing \"characters\" rather than UTF-16 code units.",
            requiresKey = false,
            useCases = listOf(
                "Counting real characters in text with emoji",
                "Producing \\U escapes for source code",
            ),
            warnings = listOf("Encoding, not encryption."),
            convention = "Code points are printed with eight hexadecimal digits so the columns line up.",
        ),
        keywords = listOf("utf-32", "code point", "character count", "emoji", "unicode"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val format = params["format"] ?: Utf32Codec.FORMAT_HEX
        return if (direction == Direction.ENCODE) {
            Utf32Codec.encode(input, format)
        } else {
            Utf32Codec.decode(input, format)
        }
    }
}

/** `hexdump -C` style dump, useful for looking at exactly what bytes a text produces. */
class HexDumpProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "hexdump",
        name = "Hex Dump",
        glyph = "DUMP",
        category = ToolCategory.ENCODING,
        classification = Classification.ENCODING,
        encodeLabel = "To hex dump",
        decodeLabel = "From hex dump",
        params = emptyList(),
        info = ToolInfo(
            summary = "The classic sixteen-bytes-per-line dump with offsets and an ASCII gutter, " +
                "exactly like `hexdump -C`. Reading a dump back recovers the original text.",
            requiresKey = false,
            useCases = listOf(
                "Seeing which bytes a character actually produces (BOM, line endings, accents)",
                "Pasting a dump from a terminal or a hex editor for a quick read",
            ),
            warnings = listOf("Encoding, not encryption."),
            convention = "Offsets are hexadecimal; dots stand for bytes that are not printable ASCII.",
        ),
        keywords = listOf("hexdump", "bytes", "offset", "hex editor", "dump"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String =
        if (direction == Direction.ENCODE) HexDumpCodec.encode(input) else HexDumpCodec.decode(input)
}
