import io

# ============================================================ 1. strict-text helper
p = "core/src/main/kotlin/com/texthub/core/util/TextBits.kt"
s = io.open(p, encoding="utf-8").read()
s = s.rstrip() + '''

/**
 * True when the bytes are valid UTF-8 (the text survives a round trip through UTF-8 encoding).
 *
 * Used by the text-oriented decoders: showing replacement characters for a payload that was
 * written with a different alphabet or format is a silently wrong answer, so those tools refuse
 * the result and explain what to change instead.
 */
fun ByteArray.isValidUtf8(): Boolean = String(this, Charsets.UTF_8).toByteArray(Charsets.UTF_8).contentEquals(this)

/** The bytes as text, or a friendly failure when they are not text at all. */
fun ByteArray.asTextOrFail(message: String): String =
    if (isValidUtf8()) String(this, Charsets.UTF_8) else throw com.texthub.core.model.ToolException(message)
'''
io.open(p, "w", encoding="utf-8").write(s)
print("isValidUtf8 helper added")

# ============================================================ 2. UTF-16 / UTF-32 format validation
p = "core/src/main/kotlin/com/texthub/core/codec/MoreCodecs.kt"
s = io.open(p, encoding="utf-8").read()

old = '''            else -> {
                val digits = text.replace(Regex("\\\\s+"), "")
                if (digits.length % 4 != 0) throw Errors.unicodeEscape()
                digits.chunked(4).map { group ->
                    val value = group.toIntOrNull(16) ?: throw Errors.unicodeEscape()
                    value
                }
            }'''
new = '''            else -> hexUnits(text, width = 4, example = "0041 0042")'''
assert old in s
s = s.replace(old, new, 1)

old = '''            else -> {
                val digits = text.replace(Regex("\\\\s+"), "")
                if (digits.length % 8 != 0) throw Errors.unicodeEscape()
                digits.chunked(8).map { group ->'''
new = '''            else -> throwIfNotHexUnits(text, width = 8, example = "00000041 00000042").let {
                val digits = text.replace(Regex("\\\\s+"), "")
                if (digits.length % 8 != 0) throw Errors.unicodeEscape()
                digits.chunked(8).map { group ->'''
assert old in s
s = s.replace(old, new, 1)

# close the utf32 else-branch correctly: find the original ending of that block
old = '''                    val value = group.toIntOrNull(16) ?: throw Errors.unicodeEscape()
                    value
                }
            }
        }
        if (points.isEmpty()) throw Errors.unicodeEscape()'''
new = '''                    val value = group.toIntOrNull(16) ?: throw Errors.unicodeEscape()
                    value
                }
            }
        }
        if (points.isEmpty()) throw Errors.unicodeEscape()'''
assert old in s

# add the shared helpers at the end of the file
s = s.rstrip() + '''

/**
 * Parses hexadecimal UTF-16 / UTF-32 units. Every token must be exactly [width] hex digits, which
 * is what these formats look like everywhere (and what this app writes). A decimal payload pasted
 * with the format still set to Hex therefore fails loudly instead of silently producing the wrong
 * characters, which is what used to happen: 3-digit decimal tokens were read as hex.
 */
private fun hexUnits(text: String, width: Int, example: String): List<Int> {
    throwIfNotHexUnits(text, width, example)
    val digits = text.replace(Regex("\\\\s+"), "")
    if (digits.length % width != 0) throw Errors.unicodeEscape()
    return digits.chunked(width).map { group -> group.toIntOrNull(16) ?: throw Errors.unicodeEscape() }
}

private fun throwIfNotHexUnits(text: String, width: Int, example: String) {
    val tokens = Regex("\\\\S+").findAll(text).map { it.value }.toList()
    if (tokens.isEmpty()) throw Errors.unicodeEscape()
    val looksHex = tokens.all { token -> token.length == width && token.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' } }
    if (!looksHex) {
        throw com.texthub.core.model.ToolException(
            "These are not $width-digit hexadecimal units, so the Format setting does not match the " +
                "payload. Expected something like \\"$example\\"; if the values are plain numbers, " +
                "set Format to Decimal."
        )
    }
}
'''
io.open(p, "w", encoding="utf-8").write(s)
print("utf16/utf32 strict hex parsing added")
