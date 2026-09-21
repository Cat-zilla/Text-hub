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
import com.texthub.core.util.parseHex
import com.texthub.core.util.toHex
import com.texthub.core.util.utf8Bytes
import com.texthub.core.util.utf8String

/**
 * Reversible XOR transformation over UTF-8 bytes.
 *
 * The result can contain arbitrary byte values, which is why the output is rendered as
 * hex (or Base64) instead of raw text. It is deterministic: the same key always produces
 * the same result, and applying it twice restores the input.
 */
class XorProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "xor",
        name = "XOR",
        glyph = "XOR",
        category = ToolCategory.TRANSFORM,
        classification = Classification.REVERSIBLE_TRANSFORM,
        encodeLabel = "Encrypt",
        decodeLabel = "Decrypt",
        params = listOf(
            ParamSpec(
                key = "key",
                label = "Key",
                kind = ParamKind.TEXT,
                defaultValue = "",
                hint = "Any text",
                sensitive = true,
            ),
            ParamSpec(
                key = "format",
                label = "Output format",
                kind = ParamKind.CHOICE,
                defaultValue = "hex",
                choices = listOf(
                    Choice("hex", "Hexadecimal"),
                    Choice("base64", "Base64"),
                ),
                helper = "The decoded input must use the same format.",
            ),
        ),
        info = ToolInfo(
            summary = "XOR combines the UTF-8 bytes of the text with the bytes of a repeating key. " +
                "The same operation both encrypts and decrypts.",
            requiresKey = true,
            useCases = listOf(
                "Simple data masking (checksums, obfuscation)",
                "Learning how byte operations work",
            ),
            warnings = listOf(
                "Reversible XOR transformation - not suitable for protecting sensitive data. " +
                    "A repeating-key XOR is trivially broken.",
            ),
            convention = "Uses UTF-8 bytes so Unicode text is handled consistently.",
        ),
        keywords = listOf("xor", "exclusive or", "mask", "obfuscate"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val key = params["key"] ?: ""
        if (key.isEmpty()) throw Errors.missingKey()
        val keyBytes = key.utf8Bytes()
        val useBase64 = params["format"] == "base64"

        if (direction == Direction.ENCODE) {
            val data = input.utf8Bytes()
            val out = ByteArray(data.size) { i -> (data[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte() }
            return if (useBase64) com.texthub.core.codec.Base64Codec.encode(out) else out.toHex()
        }

        val data = if (useBase64) {
            com.texthub.core.codec.Base64Codec.decode(input.trim())
        } else {
            parseHex(input.trim())
        }
        val out = ByteArray(data.size) { i -> (data[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte() }
        return out.utf8String()
    }
}
