package com.texthub.core.processors

import com.texthub.core.TextProcessor
import com.texthub.core.model.Choice
import com.texthub.core.model.Classification
import com.texthub.core.model.Direction
import com.texthub.core.model.Errors
import com.texthub.core.model.ParamKind
import com.texthub.core.model.ParamSpec
import com.texthub.core.model.ToolCategory
import com.texthub.core.model.ToolException
import com.texthub.core.model.ToolInfo
import com.texthub.core.model.ToolMeta
import com.texthub.core.util.Json

/**
 * Formats or minifies JSON. The parser is a small self-contained one so the app keeps its promise
 * of no third-party libraries and no network access.
 */
class JsonProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "json",
        name = "JSON Formatter",
        glyph = "{ }",
        category = ToolCategory.TRANSFORM,
        classification = Classification.TEXT_TRANSFORM,
        encodeLabel = "Format / minify",
        decodeLabel = "Format / minify",
        symmetric = true,
        params = listOf(
            ParamSpec(
                key = "mode",
                label = "Mode",
                kind = ParamKind.CHOICE,
                defaultValue = "pretty",
                choices = listOf(
                    Choice("pretty", "Indent (2 spaces)"),
                    Choice("compact", "Minify (one line)"),
                    Choice("tabs", "Indent (tabs)"),
                    Choice("keys", "List the keys it contains"),
                ),
            ),
        ),
        info = ToolInfo(
            summary = "Re-indents JSON with two spaces or tabs, minifies it onto one line, or " +
                "lists every key path it contains. Invalid JSON is reported with the position of " +
                "the problem, and nothing is ever sent anywhere.",
            requiresKey = false,
            useCases = listOf(
                "Making an API response readable",
                "Shrinking a JSON payload before pasting it elsewhere",
                "Finding the field names in a large document",
            ),
            warnings = listOf(
                "A formatting transformation: it does not encrypt, sign or validate the data.",
            ),
            convention = "Numbers keep their written form, so no precision is lost while reformatting.",
        ),
        keywords = listOf("json", "format", "pretty print", "minify", "keys", "parser"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        if (input.isBlank()) throw Errors.emptyInput()
        val value = Json.parse(input)
        return when (params["mode"] ?: "pretty") {
            "compact" -> Json.write(value, indent = null)
            "tabs" -> Json.write(value, indent = "\t")
            "keys" -> Json.keyPaths(value).joinToString("\n")
            else -> Json.write(value, indent = "  ")
        }
    }
}

/** Tests a regular expression against the input and shows every match, group and replacement. */
class RegexProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "regex",
        name = "Regex Tester",
        glyph = ".*",
        category = ToolCategory.TRANSFORM,
        classification = Classification.TEXT_TRANSFORM,
        encodeLabel = "Test / replace",
        decodeLabel = "Test / replace",
        symmetric = true,
        params = listOf(
            ParamSpec(
                key = "pattern",
                label = "Pattern",
                kind = ParamKind.TEXT,
                defaultValue = "\\w+",
                hint = "Regular expression",
                helper = "Java regular expression syntax, e.g. (\\d{4})-(\\d{2})",
            ),
            ParamSpec(
                key = "operation",
                label = "Operation",
                kind = ParamKind.CHOICE,
                defaultValue = "find",
                choices = listOf(
                    Choice("find", "List matches and groups"),
                    Choice("replace", "Replace matches"),
                    Choice("split", "Split the text"),
                    Choice("highlight", "Show one match per position"),
                ),
            ),
            ParamSpec(
                key = "replacement",
                label = "Replacement",
                kind = ParamKind.TEXT,
                defaultValue = "",
                hint = "For replace, e.g. [$1]",
            ),
            ParamSpec(
                key = "flags",
                label = "Flags",
                kind = ParamKind.CHOICE,
                defaultValue = "none",
                choices = listOf(
                    Choice("none", "None"),
                    Choice("ignoreCase", "Ignore case"),
                    Choice("multiline", "Multiline (^ and $ per line)"),
                    Choice("both", "Ignore case + multiline"),
                    Choice("dotall", "Dot matches newline too"),
                ),
            ),
        ),
        info = ToolInfo(
            summary = "A local regular-expression workbench: it lists matches with their groups, " +
                "applies replacements, splits text, or reports every match position. Nothing is " +
                "sent anywhere, so it is safe for data you would not paste into a website.",
            requiresKey = false,
            useCases = listOf(
                "Checking a pattern before using it in code",
                "Extracting fields from logs",
                "Bulk replacing with capture groups",
            ),
            warnings = listOf("A text transformation, not encryption."),
            convention = "Java regex syntax. Groups are reported as $1, $2 … in the replacement.",
        ),
        keywords = listOf("regex", "regular expression", "pattern", "replace", "split", "match"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val pattern = params["pattern"] ?: ""
        if (pattern.isEmpty()) throw Errors.regex()
        var options = 0
        when (params["flags"] ?: "none") {
            "ignoreCase" -> options = options or RegexOption.IGNORE_CASE.value
            "multiline" -> options = options or RegexOption.MULTILINE.value
            "both" -> options = options or RegexOption.IGNORE_CASE.value or RegexOption.MULTILINE.value
            "dotall" -> options = options or RegexOption.DOT_MATCHES_ALL.value
        }
        val regex = try {
            Regex(pattern, RegexOption.entries.filter { options and it.value != 0 }.toSet())
        } catch (e: Exception) {
            throw Errors.regex()
        }
        return when (params["operation"] ?: "find") {
            "replace" -> regex.replace(input, params["replacement"] ?: "")
            "split" -> regex.split(input).joinToString("\n")
            "highlight" -> regex.findAll(input).joinToString("\n") { match ->
                val line = input.take(match.range.first).count { it == '\n' } + 1
                val column = match.range.first - (input.lastIndexOf('\n', match.range.first) + 1) + 1
                "line $line, column $column: ${match.value}"
            }
            else -> {
                val matches = regex.findAll(input).toList()
                if (matches.isEmpty()) {
                    "No matches for $pattern"
                } else {
                    buildString {
                        append(matches.size).append(if (matches.size == 1) " match\n" else " matches\n")
                        matches.take(500).forEachIndexed { index, match ->
                            append(index + 1).append(": ").append(match.value.ifEmpty { "(empty)" })
                            if (match.groupValues.size > 1) {
                                append("\n   groups:")
                                match.groupValues.drop(1).forEachIndexed { groupIndex, value ->
                                    append(" $").append(groupIndex + 1).append("=")
                                    append(value.ifEmpty { "(empty)" })
                                }
                            }
                            append('\n')
                        }
                        if (matches.size > 500) append("… ").append(matches.size - 500).append(" more\n")
                    }.trimEnd('\n')
                }
            }
        }
    }
}

/** A line based diff, in the usual `-`/`+` style. Useful for comparing two versions of a text. */
class TextDiffProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "textdiff",
        name = "Text Diff",
        glyph = "Δ",
        category = ToolCategory.TRANSFORM,
        classification = Classification.TEXT_TRANSFORM,
        encodeLabel = "Compare",
        decodeLabel = "Compare",
        symmetric = true,
        params = listOf(
            ParamSpec(
                key = "separator",
                label = "Separator line",
                kind = ParamKind.TEXT,
                defaultValue = "---",
                hint = "Line that splits the two versions",
            ),
            ParamSpec(
                key = "ignoreCase",
                label = "Ignore case",
                kind = ParamKind.CHOICE,
                defaultValue = "no",
                choices = listOf(Choice("no", "Case sensitive"), Choice("yes", "Ignore case")),
            ),
        ),
        info = ToolInfo(
            summary = "Compares two texts line by line and marks removed lines with `-`, added " +
                "lines with `+` and unchanged lines with a space, followed by a small summary.",
            requiresKey = false,
            useCases = listOf(
                "Spotting what changed between two drafts",
                "Comparing a config before and after an edit",
            ),
            warnings = listOf("A text transformation, not encryption."),
            convention = "Put the older version first, the separator line, then the newer version.",
        ),
        keywords = listOf("diff", "compare", "changes", "lines", "difference"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val separator = (params["separator"] ?: "---").trim().ifEmpty { "---" }
        val lines = input.lines()
        val split = lines.indexOfFirst { it.trim() == separator }
        if (split < 0) {
            throw ToolException(
                "Add a separator line containing \"$separator\" between the two versions you " +
                    "want to compare."
            )
        }
        val before = lines.subList(0, split)
        val after = lines.subList(split + 1, lines.size)
        val ignoreCase = params["ignoreCase"] == "yes"
        val left = before.map { if (ignoreCase) it.lowercase() else it }
        val right = after.map { if (ignoreCase) it.lowercase() else it }

        val out = StringBuilder()
        var added = 0
        var removed = 0
        var same = 0

        if (left.size <= 900 && right.size <= 900) {
            // Longest common subsequence table, small enough to be instant.
            val dp = Array(left.size + 1) { IntArray(right.size + 1) }
            for (i in left.size - 1 downTo 0) {
                for (j in right.size - 1 downTo 0) {
                    dp[i][j] = if (left[i] == right[j]) dp[i + 1][j + 1] + 1
                    else maxOf(dp[i + 1][j], dp[i][j + 1])
                }
            }
            var i = 0
            var j = 0
            while (i < left.size && j < right.size) {
                when {
                    left[i] == right[j] -> {
                        out.append("  ").append(before[i]).append('\n'); i++; j++; same++
                    }
                    dp[i + 1][j] >= dp[i][j + 1] -> {
                        out.append("- ").append(before[i]).append('\n'); i++; removed++
                    }
                    else -> {
                        out.append("+ ").append(after[j]).append('\n'); j++; added++
                    }
                }
            }
            while (i < left.size) {
                out.append("- ").append(before[i]).append('\n'); i++; removed++
            }
            while (j < right.size) {
                out.append("+ ").append(after[j]).append('\n'); j++; added++
            }
        } else {
            // Very large inputs fall back to a set comparison instead of the quadratic table.
            val rightSet = right.toHashSet()
            val leftSet = left.toHashSet()
            before.forEach {
                val key = if (ignoreCase) it.lowercase() else it
                if (key in rightSet) {
                    out.append("  ").append(it).append('\n'); same++
                } else {
                    out.append("- ").append(it).append('\n'); removed++
                }
            }
            after.forEach {
                val key = if (ignoreCase) it.lowercase() else it
                if (key !in leftSet) {
                    out.append("+ ").append(it).append('\n'); added++
                }
            }
        }

        out.append('\n').append("$same unchanged, $added added, $removed removed")
        return out.toString()
    }
}

/** Counts and character facts about the input: sizes, words, lines and reading time. */
class TextStatsProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "textstats",
        name = "Text Statistics",
        glyph = "Σ",
        category = ToolCategory.TRANSFORM,
        classification = Classification.TEXT_TRANSFORM,
        encodeLabel = "Measure",
        decodeLabel = "Measure",
        symmetric = true,
        params = emptyList(),
        info = ToolInfo(
            summary = "Counts characters, code points, UTF-8 bytes, words, lines, sentences and " +
                "paragraphs, lists the most common words (stop words excluded) and estimates " +
                "reading and speaking time.",
            requiresKey = false,
            useCases = listOf(
                "Checking a character limit before posting",
                "Seeing how an emoji affects the count",
            ),
            warnings = listOf("A measurement, not encryption."),
            convention = "Word counts split on whitespace; reading time assumes 200 words per minute.",
        ),
        keywords = listOf("count", "statistics", "characters", "words", "reading time", "bytes"),
    )

    private val stopWords = setOf(
        "the", "a", "an", "and", "or", "but", "if", "of", "to", "in", "on", "for", "with", "as",
        "is", "are", "was", "were", "be", "been", "it", "its", "this", "that", "these", "those",
        "at", "by", "from", "you", "your", "i", "we", "they", "he", "she", "not", "no", "so",
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val words = input.split(Regex("\\s+")).filter { it.isNotBlank() }
        val sentences = input.split(Regex("[.!?]+")).count { it.isNotBlank() }
        val paragraphs = input.split(Regex("\\n\\s*\\n")).count { it.isNotBlank() }
        val codePoints = input.codePoints().count().toInt()
        val bytes = input.toByteArray(Charsets.UTF_8).size
        val unique = words.map { it.lowercase().trim('.', ',', '!', '?', ';', ':', '"', '\'', '(', ')') }
            .filter { it.isNotEmpty() }
            .distinct()
            .size
        val longest = words.maxByOrNull { it.length }
        val readingMinutes = words.size / 200.0
        val speakingMinutes = words.size / 130.0

        val frequency = words.map { it.lowercase().trim('.', ',', '!', '?', ';', ':', '"', '\'', '(', ')') }
            .filter { it.length > 2 && it !in stopWords }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(8)

        return buildString {
            append("Characters (UTF-16 units): ").append(input.length).append('\n')
            append("Code points: ").append(codePoints).append('\n')
            append("UTF-8 bytes: ").append(bytes).append('\n')
            append("Words: ").append(words.size).append('\n')
            append("Unique words: ").append(unique).append('\n')
            append("Lines: ").append(if (input.isEmpty()) 0 else input.lines().size).append('\n')
            append("Sentences: ").append(sentences).append('\n')
            append("Paragraphs: ").append(paragraphs).append('\n')
            append("Longest word: ").append(longest ?: "-").append('\n')
            append("Reading time: ").append(String.format("%.1f", readingMinutes)).append(" min")
            append(" (200 wpm)\n")
            append("Speaking time: ").append(String.format("%.1f", speakingMinutes)).append(" min")
            append(" (130 wpm)")
            if (frequency.isNotEmpty()) {
                append("\n\nMost frequent words:")
                frequency.forEach { (word, count) -> append("\n  ").append(word).append(" × ").append(count) }
            }
            if (input.length != codePoints) {
                append("\n\nNote: this text contains characters outside the Basic Multilingual Plane, ")
                append("so the UTF-16 count is larger than the number of code points.")
            }
        }
    }
}

/**
 * Decodes the header and payload of a JSON Web Token so you can see what a token claims.
 * Text Hub never verifies signatures here and never sends the token anywhere.
 */
class JwtProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "jwt",
        name = "JWT Inspector",
        glyph = "JWT",
        category = ToolCategory.TRANSFORM,
        classification = Classification.TEXT_TRANSFORM,
        encodeLabel = "Inspect",
        decodeLabel = "Inspect",
        symmetric = true,
        params = emptyList(),
        info = ToolInfo(
            summary = "Shows the header and payload of a JSON Web Token, the algorithm it claims " +
                "and when it expires. The signature is never checked, because checking it needs " +
                "the secret or public key and that belongs on a server.",
            requiresKey = false,
            useCases = listOf(
                "Seeing what a token claims about you before you use it",
                "Spotting a token that says \"alg\": \"none\"",
            ),
            warnings = listOf(
                "Reading a token does not prove it is genuine. Never trust claims from a token " +
                    "without verifying the signature where the token is actually used.",
                "Do not paste production tokens into websites - do it here, off-line, instead.",
            ),
            convention = "Header and payload are Base64URL decoded and re-indented; the signature part is only measured.",
        ),
        keywords = listOf("jwt", "token", "claims", "header", "payload", "base64url"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val parts = input.trim().split(".")
        if (parts.size < 2) throw Errors.jwt()
        val header = decodePart(parts[0], "header")
        val payload = decodePart(parts[1], "payload")
        val signature = if (parts.size > 2) parts[2] else ""
        val algorithm = Regex("\"alg\"\\s*:\\s*\"([^\"]*)\"")
            .find(header)?.groupValues?.get(1) ?: "unknown"
        val expires = Regex("\"exp\"\\s*:\\s*(\\d+)").find(payload)?.groupValues?.get(1)?.toLongOrNull()

        return buildString {
            append("Algorithm: ").append(algorithm)
            when {
                algorithm.equals("none", ignoreCase = true) ->
                    append("  ← unsigned token, anyone can edit this payload")
                algorithm.startsWith("HS") ->
                    append("  (HMAC with a shared secret)")
                algorithm.startsWith("RS") || algorithm.startsWith("ES") ->
                    append("  (asymmetric signature)")
            }
            append('\n')
            if (signature.isEmpty()) {
                append("Signature: missing\n")
            } else {
                append("Signature: ").append(signature.length).append(" Base64URL characters ")
                append("(not verified here)\n")
            }
            if (expires != null) {
                append("Expires: ").append(expires).append(" (unix seconds)\n")
            } else {
                append("Expires: no exp claim found\n")
            }
            append("\nHeader\n").append(header).append("\n\nPayload\n").append(payload)
        }
    }

    private fun decodePart(part: String, label: String): String {
        if (part.isEmpty()) throw Errors.jwt()
        val normalized = part.replace('-', '+').replace('_', '/')
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        val bytes = try {
            com.texthub.core.codec.Base64Codec.decode(padded)
        } catch (e: Exception) {
            throw Errors.jwt()
        }
        val text = bytes.toString(Charsets.UTF_8)
        return try {
            Json.write(Json.parse(text), indent = "  ")
        } catch (e: ToolException) {
            throw ToolException("The $label of this token is not valid JSON, so the token is malformed.")
        }
    }
}
