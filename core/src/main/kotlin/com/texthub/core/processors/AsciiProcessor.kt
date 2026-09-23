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
import com.texthub.core.util.isAscii
import com.texthub.core.util.utf8Bytes
import com.texthub.core.model.DetectionHint

/**
 * ASCII conversions. ASCII is a 7-bit character set (values 0-127) and is *not* the same
 * as UTF-8/Unicode, so non-ASCII input is rejected with a friendly message rather than
 * silently mangled.
 */
class AsciiProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "ascii",
        name = "ASCII",
        glyph = "ASC",
        category = ToolCategory.ENCODING,
        classification = Classification.ENCODING,
        encodeLabel = "Text to ASCII",
        decodeLabel = "ASCII to Text",
        params = listOf(
            ParamSpec(
                key = "mode",
                label = "Mode",
                kind = ParamKind.CHOICE,
                defaultValue = "decimal",
                choices = listOf(
                    Choice("decimal", "Decimal (65)"),
                    Choice("binary", "Binary (01000001)"),
                    Choice("hex", "Hex (41)"),
                ),
            ),
        ),
        detection = listOf(
            DetectionHint(
                alphabet = "0123456789 ,;:|\t\n",
                minLength = 3,
                label = "ASCII values",
                recognise = { text ->
                    val tokens = text.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
                    tokens.size >= 2 && tokens.all { token -> token.toIntOrNull()?.let { it in 0..127 } == true }
                },
                evidence = "Numbers in the 0-127 range, which is the ASCII table.",
                paramsFor = { mapOf("mode" to "decimal") },
            ),
        ),
        info = ToolInfo(
            summary = "ASCII is a 7-bit character encoding with 128 values (0-127). This tool " +
                "converts between ASCII text and decimal, binary or hexadecimal code values.",
            requiresKey = false,
            useCases = listOf(
                "Looking up character codes",
                "Teaching material for character encodings",
                "Converting small control-character sequences",
            ),
            warnings = listOf(
                "ASCII is a 7-bit character set, not Unicode. Text with accented letters, emoji " +
                    "or other non-ASCII characters cannot be represented.",
                "Encoding, not encryption.",
            ),
        ),
        keywords = listOf("ascii", "char", "code", "ansi"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val mode = params["mode"] ?: "decimal"
        if (direction == Direction.ENCODE) {
            if (!isAscii(input)) throw Errors.ascii()
            val bytes = input.utf8Bytes()
            return when (mode) {
                "binary" -> bytes.joinToString(" ") { b ->
                    val v = b.toInt() and 0xFF
                    (7 downTo 0).joinToString("") { if ((v ushr it) and 1 == 1) "1" else "0" }
                }
                "hex" -> bytes.joinToString(" ") { b ->
                    val v = b.toInt() and 0xFF
                    v.toString(16).uppercase().padStart(2, '0')
                }
                else -> bytes.joinToString(" ") { (it.toInt() and 0xFF).toString() }
            }
        }

        val tokens = input.split(Regex("[\\s,;]+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return ""
        val sb = StringBuilder()
        for (token in tokens) {
            val value = when (mode) {
                "binary" -> {
                    if (token.any { it != '0' && it != '1' } || token.length !in 1..8) throw Errors.binary()
                    token.toInt(2)
                }
                "hex" -> {
                    if (token.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) throw Errors.hex()
                    token.toInt(16)
                }
                else -> {
                    if (token.any { it !in '0'..'9' }) throw Errors.asciiValue()
                    token.toInt()
                }
            }
            if (value !in 0..127) throw Errors.asciiValue()
            sb.append(value.toChar())
        }
        return sb.toString()
    }
}
