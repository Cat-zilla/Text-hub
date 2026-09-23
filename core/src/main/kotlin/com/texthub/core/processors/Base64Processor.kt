package com.texthub.core.processors

import com.texthub.core.TextProcessor
import com.texthub.core.codec.Base64Codec
import com.texthub.core.model.Choice
import com.texthub.core.model.Classification
import com.texthub.core.model.Direction
import com.texthub.core.model.ParamKind
import com.texthub.core.model.ParamSpec
import com.texthub.core.model.ToolCategory
import com.texthub.core.model.ToolInfo
import com.texthub.core.model.ToolMeta
import com.texthub.core.util.utf8Bytes
import com.texthub.core.util.utf8String
import com.texthub.core.model.DetectionHint

class Base64Processor : TextProcessor {

    override val meta = ToolMeta(
        id = "base64",
        name = "Base64",
        glyph = "64",
        category = ToolCategory.ENCODING,
        classification = Classification.ENCODING,
        encodeLabel = "Encode",
        decodeLabel = "Decode",
        params = listOf(
            ParamSpec(
                key = "variant",
                label = "Encoding variant",
                kind = ParamKind.CHOICE,
                defaultValue = "standard",
                choices = listOf(
                    Choice("standard", "Standard"),
                    Choice("url", "URL-safe"),
                ),
                helper = "URL-safe uses - and _ instead of + and /.",
            ),
        ),
        detection = listOf(
            DetectionHint(
                alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=",
                minLength = 8,
                lengthRule = { it % 4 != 1 },
                label = "Base64",
                evidenceFor = { text ->
                    val compact = text.filterNot { it.isWhitespace() }
                    "Every character belongs to the Base64 alphabet" +
                        (if (compact.length % 4 == 0) " and the length is a multiple of four." else ".") +
                        (if (compact.endsWith("=")) " It ends with padding." else "")
                },
                strongWhen = { it.trim().endsWith("=") },
                validatedFor = { _, output -> if (output.isNotBlank()) "It decodes to readable text." else null },
                paramsFor = { mapOf("variant" to "standard") },
            ),
            DetectionHint(
                alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_=",
                mustContain = "-_",
                minLength = 8,
                lengthRule = { it % 4 != 1 },
                label = "Base64URL",
                evidence = "The characters include \"-\" or \"_\", which only the URL-safe Base64 " +
                    "alphabet uses, and the length fits Base64.",
                strongWhen = { it.trim().endsWith("=") },
                validatedFor = { _, output -> if (output.isNotBlank()) "It decodes to readable text." else null },
                paramsFor = { mapOf("variant" to "url") },
            ),
        ),
        info = ToolInfo(
            summary = "Base64 represents binary data using 64 printable characters so it can be " +
                "stored or sent as plain text. It is an encoding format, not encryption - anyone " +
                "can decode it instantly.",
            requiresKey = false,
            useCases = listOf(
                "Embedding binary data inside JSON, XML or HTML",
                "Sending attachments over text-only channels",
                "Basic data-URL payloads",
            ),
            warnings = listOf("Encoding, not encryption. It does not protect information from being read."),
        ),
        keywords = listOf("base64", "b64", "radix", "mime"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val urlSafe = params["variant"] == "url"
        return if (direction == Direction.ENCODE) {
            Base64Codec.encode(input.utf8Bytes(), urlSafe = urlSafe)
        } else {
            Base64Codec.decode(input).utf8String()
        }
    }
}
