package com.texthub.core.processors

import com.texthub.core.TextProcessor
import com.texthub.core.codec.Base58Codec
import com.texthub.core.model.Choice
import com.texthub.core.model.Classification
import com.texthub.core.model.Direction
import com.texthub.core.model.ParamKind
import com.texthub.core.model.ParamSpec
import com.texthub.core.model.ToolCategory
import com.texthub.core.model.ToolInfo
import com.texthub.core.model.ToolMeta
import com.texthub.core.util.utf8Bytes
import com.texthub.core.util.asTextOrFail
import com.texthub.core.model.DetectionHint

class Base58Processor : TextProcessor {

    override val meta = ToolMeta(
        id = "base58",
        name = "Base58",
        glyph = "58",
        category = ToolCategory.ENCODING,
        classification = Classification.ENCODING,
        encodeLabel = "Encode",
        decodeLabel = "Decode",
        params = listOf(
            ParamSpec(
                key = "variant",
                label = "Alphabet",
                kind = ParamKind.CHOICE,
                defaultValue = "bitcoin",
                choices = listOf(
                    Choice("bitcoin", "Bitcoin"),
                    Choice("ripple", "Ripple"),
                    Choice("flickr", "Flickr"),
                ),
                helper = "The Bitcoin alphabet is the most widely used variant.",
            ),
        ),
        detection = listOf(
            DetectionHint(
                alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz",
                minLength = 4,
                label = "Base58",
                evidence = "Every character is in the Base58 alphabet, which leaves out 0, O, I and l " +
                    "(and + and /). Base58 does not record which alphabet wrote it, so the Bitcoin " +
                    "alphabet is assumed.",
                paramsFor = { mapOf("variant" to "bitcoin") },
            ),
        ),
        info = ToolInfo(
            summary = "Base58 is a text encoding that removes easily confused characters " +
                "(0, O, I, l) from the alphabet. Leading zero bytes are preserved as leading '1' " +
                "characters.",
            requiresKey = false,
            useCases = listOf(
                "Cryptocurrency addresses",
                "Short links and human-transcribable identifiers",
            ),
            warnings = listOf(
                "Base58 stores no marker for the alphabet: decoding with the wrong Variant cannot always be detected, so keep the setting that was used to encode.","Encoding, not encryption."),
        ),
        keywords = listOf("base58", "bitcoin", "ripple", "flickr"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val alphabet = Base58Codec.alphabetFor(params["variant"] ?: "bitcoin")
        return if (direction == Direction.ENCODE) {
            Base58Codec.encode(input.utf8Bytes(), alphabet)
        } else {
            // The alphabet in use cannot be recovered from a Base58 string, so a wrong Variant can
            // otherwise return confident nonsense. A result that is not valid text is always wrong
            // for a text tool, so it is refused with a pointer to the setting.
            Base58Codec.decode(input.trim(), alphabet).asTextOrFail(
                "This Base58 payload does not decode to text with the selected Variant. Base58 itself " +
                    "does not record which alphabet it was written with, so check the Variant " +
                    "(Standard / Ripple / Flickr) and try again."
            )
        }
    }
}
