package com.texthub.core.processors

import com.texthub.core.TextProcessor
import com.texthub.core.codec.Base32Codec
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

class Base32Processor : TextProcessor {

    override val meta = ToolMeta(
        id = "base32",
        name = "Base32",
        glyph = "32",
        category = ToolCategory.ENCODING,
        classification = Classification.ENCODING,
        encodeLabel = "Encode",
        decodeLabel = "Decode",
        params = listOf(
            ParamSpec(
                key = "variant",
                label = "Alphabet",
                kind = ParamKind.CHOICE,
                defaultValue = "standard",
                choices = listOf(
                    Choice("standard", "Standard (RFC 4648)"),
                    Choice("hex", "Base32 Hex"),
                    Choice("crockford", "Crockford (no I, L, O, U)"),
                ),
            ),
        ),
        detection = listOf(
            DetectionHint(
                alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567=",
                minLength = 8,
                multipleOf = 8,
                label = "Base32",
                evidence = "Only Base32 characters (A-Z and 2-7) are present and the length is a " +
                    "multiple of eight.",
                strongWhen = { it.trim().endsWith("=") },
                paramsFor = { mapOf("variant" to "standard") },
            ),
        ),
        info = ToolInfo(
            summary = "Base32 encodes data using 32 characters (A-Z and 2-7). It is case-insensitive " +
                "and more robust than Base64 when humans have to read or type the result. Crockford " +
                "base32 removes I, L, O and U, and accepts them as 1, 1, 0 and V when decoding.",
            requiresKey = false,
            useCases = listOf(
                "One-time-password secrets",
                "Case-insensitive identifiers and file names",
                "Checksums that people read aloud",
            ),
            warnings = listOf("Encoding, not encryption."),
        ),
        keywords = listOf("base32", "rfc4648", "radix32"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val variant = params["variant"] ?: Base32Codec.VARIANT_STANDARD
        return if (direction == Direction.ENCODE) {
            Base32Codec.encode(input.utf8Bytes(), variant)
        } else {
            Base32Codec.decode(input, variant).utf8String()
        }
    }
}
