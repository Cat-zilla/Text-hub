package com.texthub.core.processors

import com.texthub.core.TextProcessor
import com.texthub.core.codec.Base85Codec
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

class Base85Processor : TextProcessor {

    override val meta = ToolMeta(
        id = "base85",
        name = "Base85 / Ascii85",
        glyph = "85",
        category = ToolCategory.ENCODING,
        classification = Classification.ENCODING,
        encodeLabel = "Encode",
        decodeLabel = "Decode",
        params = listOf(
            ParamSpec(
                key = "variant",
                label = "Variant",
                kind = ParamKind.CHOICE,
                defaultValue = "ascii85",
                choices = listOf(
                    Choice("ascii85", "Ascii85"),
                    Choice("ascii85z", "Ascii85 (z compression)"),
                    Choice("z85", "Z85 (ZeroMQ)"),
                ),
                helper = "Z85 needs a length that is a multiple of 4 bytes; Ascii85 handles any length.",
            ),
        ),
        info = ToolInfo(
            summary = "Base85 packs 4 bytes into 5 printable characters, so it is more compact " +
                "than Base64. Ascii85 is the variant used inside PostScript and PDF files.",
            requiresKey = false,
            useCases = listOf(
                "Compact printable representation of binary data",
                "PostScript / PDF content streams",
            ),
            warnings = listOf("Encoding, not encryption."),
        ),
        keywords = listOf("base85", "ascii85", "z85", "btoa"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val variant = Base85Codec.variantFor(params["variant"] ?: "ascii85")
        return if (direction == Direction.ENCODE) {
            Base85Codec.encode(input.utf8Bytes(), variant)
        } else {
            Base85Codec.decode(input.trim(), variant).utf8String()
        }
    }
}
