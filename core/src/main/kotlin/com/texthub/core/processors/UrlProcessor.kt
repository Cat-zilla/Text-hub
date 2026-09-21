package com.texthub.core.processors

import com.texthub.core.TextProcessor
import com.texthub.core.codec.UrlCodec
import com.texthub.core.model.Choice
import com.texthub.core.model.Classification
import com.texthub.core.model.Direction
import com.texthub.core.model.ParamKind
import com.texthub.core.model.ParamSpec
import com.texthub.core.model.ToolCategory
import com.texthub.core.model.ToolInfo
import com.texthub.core.model.ToolMeta

class UrlProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "url",
        name = "URL Encoding",
        glyph = "URL",
        category = ToolCategory.ENCODING,
        classification = Classification.ENCODING,
        encodeLabel = "Encode",
        decodeLabel = "Decode",
        params = listOf(
            ParamSpec(
                key = "variant",
                label = "Style",
                kind = ParamKind.CHOICE,
                defaultValue = "standard",
                choices = listOf(
                    Choice("standard", "RFC 3986 (%20 for space)"),
                    Choice("form", "Form (+ for space)"),
                ),
            ),
        ),
        info = ToolInfo(
            summary = "Percent encoding replaces characters that are not allowed in a URL with a " +
                "% followed by two hexadecimal digits. Unicode text is encoded as UTF-8 first.",
            requiresKey = false,
            useCases = listOf(
                "Building query strings",
                "Inspecting or fixing a mangled link",
                "Escaping user input before putting it in a URL",
            ),
            warnings = listOf("Encoding, not encryption."),
        ),
        keywords = listOf("url", "percent", "uri", "escape", "query"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val form = params["variant"] == "form"
        return if (direction == Direction.ENCODE) UrlCodec.encode(input, form)
        else UrlCodec.decode(input, form)
    }
}
