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
import com.texthub.core.util.utf8String

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
        info = ToolInfo(
            summary = "Base58 is a text encoding that removes easily confused characters " +
                "(0, O, I, l) from the alphabet. Leading zero bytes are preserved as leading '1' " +
                "characters.",
            requiresKey = false,
            useCases = listOf(
                "Cryptocurrency addresses",
                "Short links and human-transcribable identifiers",
            ),
            warnings = listOf("Encoding, not encryption."),
        ),
        keywords = listOf("base58", "bitcoin", "ripple", "flickr"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val alphabet = Base58Codec.alphabetFor(params["variant"] ?: "bitcoin")
        return if (direction == Direction.ENCODE) {
            Base58Codec.encode(input.utf8Bytes(), alphabet)
        } else {
            Base58Codec.decode(input.trim(), alphabet).utf8String()
        }
    }
}
