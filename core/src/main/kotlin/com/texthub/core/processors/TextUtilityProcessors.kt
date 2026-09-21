package com.texthub.core.processors

import com.texthub.core.TextProcessor
import com.texthub.core.model.Choice
import com.texthub.core.model.Classification
import com.texthub.core.model.Direction
import com.texthub.core.model.ParamKind
import com.texthub.core.model.ParamSpec
import com.texthub.core.model.ToolCategory
import com.texthub.core.model.ToolInfo
import com.texthub.core.model.ToolMeta

/** Case and identifier style conversion. */
class CaseConverterProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "case",
        name = "Case Converter",
        glyph = "Aa",
        category = ToolCategory.TRANSFORM,
        classification = Classification.TEXT_TRANSFORM,
        encodeLabel = "Convert",
        decodeLabel = "Convert back",
        params = listOf(
            ParamSpec(
                key = "mode",
                label = "Target style",
                kind = ParamKind.CHOICE,
                defaultValue = "upper",
                choices = listOf(
                    Choice("upper", "UPPER CASE"),
                    Choice("lower", "lower case"),
                    Choice("title", "Title Case"),
                    Choice("sentence", "Sentence case"),
                    Choice("toggle", "tOGGLE cASE"),
                    Choice("camel", "camelCase"),
                    Choice("pascal", "PascalCase"),
                    Choice("snake", "snake_case"),
                    Choice("kebab", "kebab-case"),
                    Choice("constant", "CONSTANT_CASE"),
                    Choice("dot", "dot.case"),
                ),
            ),
        ),
        info = ToolInfo(
            summary = "Changes the capitalisation of text, including programming identifier styles " +
                "such as camelCase, snake_case and kebab-case, which are derived from the word " +
                "boundaries in your text.",
            requiresKey = false,
            useCases = listOf(
                "Turning a heading into a variable or file name",
                "Fixing text typed with the caps lock on",
            ),
            warnings = listOf("A plain text transformation - it does not hide anything."),
            convention = "Identifier styles split on spaces, underscores, hyphens and case changes.",
        ),
        keywords = listOf("case", "upper", "lower", "title", "camel", "snake", "kebab", "sentence"),
    )

    /** Sorts while keeping blank lines at the bottom instead of letting them float to the top. */
    private fun sortWithBlanksLast(lines: List<String>, comparator: Comparator<String>): String =
        sortWithBlanksLast(lines) { a, b -> comparator.compare(a, b) }

    private fun sortWithBlanksLast(lines: List<String>, compare: (String, String) -> Int): String =
        lines.sortedWith { a, b ->
            val aBlank = a.isBlank()
            val bBlank = b.isBlank()
            when {
                aBlank && bBlank -> 0
                aBlank -> 1
                bBlank -> -1
                else -> compare(a, b)
            }
        }.joinToString("\n")

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val mode = params["mode"] ?: "upper"
        return when (mode) {
            "upper" -> input.uppercase()
            "lower" -> input.lowercase()
            "title" -> input.split(" ").joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
            "sentence" -> sentenceCase(input)
            "toggle" -> input.map { c ->
                when {
                    c.isLowerCase() -> c.uppercaseChar()
                    c.isUpperCase() -> c.lowercaseChar()
                    else -> c
                }
            }.joinToString("")
            else -> words(input).let { parts ->
                when (mode) {
                    "camel" -> parts.mapIndexed { i, w -> if (i == 0) w.lowercase() else w.capitalizeWord() }.joinToString("")
                    "pascal" -> parts.joinToString("") { it.capitalizeWord() }
                    "snake" -> parts.joinToString("_") { it.lowercase() }
                    "kebab" -> parts.joinToString("-") { it.lowercase() }
                    "constant" -> parts.joinToString("_") { it.uppercase() }
                    "dot" -> parts.joinToString(".") { it.lowercase() }
                    else -> input
                }
            }
        }
    }

    private fun sentenceCase(text: String): String {
        val sb = StringBuilder(text.length)
        var newSentence = true
        for (c in text) {
            when {
                c == '.' || c == '!' || c == '?' || c == '\n' -> {
                    sb.append(c)
                    newSentence = true
                }
                newSentence && c.isLetter() -> {
                    sb.append(c.uppercaseChar())
                    newSentence = false
                }
                else -> {
                    sb.append(c.lowercaseChar())
                }
            }
        }
        return sb.toString()
    }

    /** Splits on whitespace, underscores, hyphens and camelCase boundaries. */
    private fun words(text: String): List<String> =
        Regex("[^\\s_\\-./]+").findAll(text.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2"))
            .map { it.value }
            .filter { it.isNotBlank() }
            .toList()

    private fun String.capitalizeWord(): String =
        lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

/** Line oriented clean-up tools: sorting, de-duplication and numbering. */
class LineToolsProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "linetools",
        name = "Line Tools",
        glyph = "≡",
        category = ToolCategory.TRANSFORM,
        classification = Classification.TEXT_TRANSFORM,
        encodeLabel = "Apply",
        decodeLabel = "Apply",
        symmetric = true,
        params = listOf(
            ParamSpec(
                key = "operation",
                label = "Operation",
                kind = ParamKind.CHOICE,
                defaultValue = "sortAsc",
                choices = listOf(
                    Choice("sortAsc", "Sort A → Z"),
                    Choice("sortDesc", "Sort Z → A"),
                    Choice("sortLength", "Sort by length"),
                    Choice("numeric", "Sort numerically"),
                    Choice("dedupe", "Remove duplicate lines"),
                    Choice("removeEmpty", "Remove empty lines"),
                    Choice("trim", "Trim each line"),
                    Choice("reverse", "Reverse line order"),
                    Choice("number", "Number the lines"),
                    Choice("join", "Join into one line"),
                ),
            ),
            ParamSpec(
                key = "caseSensitive",
                label = "Case sensitive",
                kind = ParamKind.CHOICE,
                defaultValue = "insensitive",
                choices = listOf(
                    Choice("insensitive", "Ignore case"),
                    Choice("sensitive", "Respect case"),
                ),
            ),
        ),
        info = ToolInfo(
            summary = "Everyday line work: sorting alphabetically, numerically or by length, " +
                "removing duplicates and blank lines, trimming, numbering, reversing and joining.",
            requiresKey = false,
            useCases = listOf(
                "Cleaning up lists, logs and CSVs",
                "Removing duplicates before comparing two lists",
            ),
            warnings = listOf("A plain text transformation - it does not hide anything."),
        ),
        keywords = listOf("sort", "lines", "dedupe", "duplicate", "trim", "number", "unique"),
    )

    /** Sorts while keeping blank lines at the bottom instead of letting them float to the top. */
    private fun sortWithBlanksLast(lines: List<String>, comparator: Comparator<String>): String =
        sortWithBlanksLast(lines) { a, b -> comparator.compare(a, b) }

    private fun sortWithBlanksLast(lines: List<String>, compare: (String, String) -> Int): String =
        lines.sortedWith { a, b ->
            val aBlank = a.isBlank()
            val bBlank = b.isBlank()
            when {
                aBlank && bBlank -> 0
                aBlank -> 1
                bBlank -> -1
                else -> compare(a, b)
            }
        }.joinToString("\n")

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        if (input.isEmpty()) return ""
        val op = params["operation"] ?: "sortAsc"
        val caseSensitive = params["caseSensitive"] == "sensitive"
        val lines = input.split("\n")
        val comparator: Comparator<String> = when {
            caseSensitive -> compareBy { it }
            else -> compareBy { it.lowercase() }
        }
        fun sorted(ascending: Boolean): String = sortWithBlanksLast(lines) { a, b ->
            if (ascending) comparator.compare(a, b) else comparator.compare(b, a)
        }

        return when (op) {
            "sortAsc" -> sorted(true)
            "sortDesc" -> sorted(false)
            "sortLength" -> sortWithBlanksLast(lines, compareBy({ it.length }, { it.lowercase() }))
            "numeric" -> sortWithBlanksLast(
                lines,
                compareBy { it.trim().toDoubleOrNull() ?: Double.MAX_VALUE },
            )
            "dedupe" -> {
                val seen = HashSet<String>()
                val out = ArrayList<String>()
                for (line in lines) {
                    val key = if (caseSensitive) line else line.lowercase()
                    if (seen.add(key)) out.add(line)
                }
                out.joinToString("\n")
            }
            "removeEmpty" -> lines.filter { it.isNotBlank() }.joinToString("\n")
            "trim" -> lines.joinToString("\n") { it.trim() }
            "reverse" -> lines.reversed().joinToString("\n")
            "number" -> lines.mapIndexed { i, line -> "${i + 1}. $line" }.joinToString("\n")
            "join" -> lines.joinToString(" ") { it.trim() }.trim().replace(Regex("\\s+"), " ")
            else -> input
        }
    }
}
