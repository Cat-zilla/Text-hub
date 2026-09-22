package com.texthub.core.model

/**
 * How a method is classified. The UI always surfaces this so the user is never misled
 * into thinking an encoding or classical cipher is modern encryption.
 */
enum class Classification(val label: String, val secure: Boolean) {
    ENCODING("Encoding", false),
    HISTORICAL_ENCODING("Historical character encoding", false),
    CLASSICAL_CIPHER("Classical cipher", false),
    OBFUSCATION("Obfuscation", false),
    TEXT_TRANSFORM("Text transformation", false),
    REVERSIBLE_TRANSFORM("Reversible transformation", false),
    CRYPTOGRAPHIC_HASH("Cryptographic hash (one-way)", false),
    MESSAGE_AUTHENTICATION("Message authentication code", false),
    CHECKSUM("Checksum (integrity only)", false),
    AUTHENTICATED_ENCRYPTION("Authenticated encryption", true),
    KEY_MATERIAL("Key material (not encryption)", false),
}

enum class ToolCategory(val label: String) {
    SECURE("Secure Encryption"),
    CRYPTO("Hashing & Authentication"),
    CLASSICAL("Classical Ciphers"),
    ENCODING("Encoding"),
    TRANSFORM("Transformations"),
}

/** Direction of the operation. Labels are per-tool (Encode/Decode, Encrypt/Decrypt, Transform). */
enum class Direction { ENCODE, DECODE }

/** How a parameter should be rendered/collected in the UI. */
enum class ParamKind {
    /** Single line plain text (keys, alphabets, separators). */
    TEXT,

    /** Secret value - must never be persisted or logged by the app. */
    PASSWORD,

    /** Integer with stepper controls. */
    NUMBER,

    /** Dropdown / segmented choice. */
    CHOICE,

    /** Monospace multi-symbol text (substitution alphabet). */
    ALPHABET,

    /** Several lines of text, for example a PEM key block. */
    MULTILINE,
}

data class Choice(val id: String, val label: String)

data class ParamSpec(
    val key: String,
    val label: String,
    val kind: ParamKind,
    val defaultValue: String = "",
    val choices: List<Choice> = emptyList(),
    val min: Int = Int.MIN_VALUE,
    val max: Int = Int.MAX_VALUE,
    val hint: String? = null,
    val helper: String? = null,
    /** When true the app never writes this value to disk. Always true for [ParamKind.PASSWORD]. */
    val sensitive: Boolean = false,
    /**
     * True for settings that are only needed in special situations (an external payload format, a
     * digest, an explicit IV, a separator). The UI keeps these collapsed under
     * "Additional encryption settings" so the common path stays short; they are still *required*
     * for the situations they describe, which is why they live on the tool rather than in a
     * separate tool.
     */
    val advanced: Boolean = false,
    /** True when the tool cannot run without a value (no usable default). */
    val required: Boolean = false,
    /** Optional extra check for this single value; returns a user-facing sentence or null. */
    val validator: ((String) -> String?)? = null,
) {
    /** The choice entry that matches [value], or null when the value is not offered. */
    fun choiceFor(value: String): Choice? = choices.firstOrNull { it.id == value }
}

data class ToolInfo(
    /** Short description of what the method does. */
    val summary: String,
    val requiresKey: Boolean,
    val useCases: List<String>,
    val warnings: List<String> = emptyList(),
    /** Extra "how it works" line, e.g. the alphabet convention used. */
    val convention: String? = null,
)

data class ToolMeta(
    val id: String,
    val name: String,
    /** Short glyph used as the tool icon (kept simple + consistent). */
    val glyph: String,
    val category: ToolCategory,
    val classification: Classification,
    /** Label for the forward direction, e.g. "Encode" / "Encrypt" / "Transform". */
    val encodeLabel: String,
    /** Label for the reverse direction. */
    val decodeLabel: String,
    val params: List<ParamSpec> = emptyList(),
    val info: ToolInfo = ToolInfo("", false, emptyList()),
    val keywords: List<String> = emptyList(),
    /** True when both directions use the same transformation (ROT13, ROT47, Atbash...). */
    val symmetric: Boolean = false,
    /**
     * True when the tool works without any input text (key generation, for example).
     * The main screen then processes an empty input box instead of refusing to run.
     */
    val inputOptional: Boolean = false,
    /**
     * True when the tool runs in one direction only: a digest, a checksum, a comparison, a brute
     * force list, key generation. Such a tool must not offer a direction switch, and its result
     * cannot meaningfully be pushed back through it, so the main screen hides Swap as well.
     */
    val oneWay: Boolean = false,
    /**
     * True when the result is an answer rather than a message: a digest, a verification verdict, a
     * measurement, an inspection report. The direction switch can still be meaningful (Hash vs
     * Verify), but pushing such a result back through the tool is not, so Swap is hidden.
     */
    val resultIsFinal: Boolean = false,
) {
    /**
     * True when the tool has two different directions worth switching between. A symmetric tool
     * (ROT13, Atbash) computes the same thing both ways and a one-way tool has no reverse at all,
     * so neither shows the direction switch - the UI only offers controls the tool can honour.
     */
    val hasDirectionChoice: Boolean
        get() = !oneWay && !symmetric && encodeLabel != decodeLabel

    /** Swap pushes the result back through the tool, which only makes sense for a reversible one. */
    val supportsSwap: Boolean get() = !oneWay && !resultIsFinal

    /** Reset is offered as soon as there is more than one thing to reset. */
    val canResetParams: Boolean get() = params.size > 1

    /** Settings shown inline; the rest live under "Additional encryption settings". */
    val primaryParams: List<ParamSpec> get() = params.filter { !it.advanced }

    val advancedParams: List<ParamSpec> get() = params.filter { it.advanced }

    /**
     * Everything the search box matches against: the tool name, its **id** (so typing `utf16`,
     * `aescbc` or `railfence` finds the tool), a punctuation-free spelling of the name (so
     * `rail fence` and `railfence` both work), the classification, the category and the keywords.
     */
    val searchIndex: String =
        (
            listOf(name, id, name.replace(Regex("[^A-Za-z0-9]+"), ""), classification.label, category.label) +
                keywords
            ).joinToString(" ").lowercase()
}

/** Result of a processing request. Never contains secrets beyond what the user typed. */
data class ProcessOutcome(
    val output: String = "",
    val error: String? = null,
    val durationMs: Long = 0L,
    val inputChars: Int = 0,
) {
    val isSuccess: Boolean get() = error == null
}

data class TextStats(val chars: Int, val words: Int, val lines: Int)

fun computeStats(text: String): TextStats {
    if (text.isEmpty()) return TextStats(0, 0, 0)
    val chars = Character.codePointCount(text, 0, text.length)
    var words = 0
    var inWord = false
    for (c in text) {
        val ws = c.isWhitespace()
        if (!ws && !inWord) {
            words++
            inWord = true
        } else if (ws) {
            inWord = false
        }
    }
    var lines = 1
    for (c in text) if (c == '\n') lines++
    return TextStats(chars, words, lines)
}

/** A parameter value the tool cannot use, with a sentence that says what to change. */
data class ParamIssue(val key: String, val message: String)

/**
 * Checks the parameter values of a tool and returns one friendly sentence per problem.
 *
 * This is deliberately about *parameters*, not about the input text: it lets the UI mark the
 * offending field ("Shift must be between 1 and 25") instead of showing one generic banner.
 * Anything that needs the input text is reported by the processor itself.
 */
fun ToolMeta.validateParams(params: Map<String, String>): List<ParamIssue> {
    val issues = mutableListOf<ParamIssue>()
    for (spec in this.params) {
        val value = params[spec.key] ?: spec.defaultValue
        if (spec.required && value.isBlank()) {
            issues += ParamIssue(spec.key, "Enter " + spec.label.lowercase() + " to continue.")
            continue
        }
        when (spec.kind) {
            ParamKind.NUMBER -> {
                val number = value.trim().toIntOrNull()
                if (number == null) {
                    issues += ParamIssue(spec.key, spec.label + " must be a whole number.")
                } else if (spec.min != Int.MIN_VALUE && number < spec.min) {
                    issues += ParamIssue(spec.key, spec.label + " must be at least " + spec.min + ".")
                } else if (spec.max != Int.MAX_VALUE && number > spec.max) {
                    issues += ParamIssue(spec.key, spec.label + " must be at most " + spec.max + ".")
                }
            }
            ParamKind.CHOICE -> {
                if (value.isNotBlank() && spec.choiceFor(value) == null) {
                    issues += ParamIssue(spec.key, spec.label + " has to be one of the offered options.")
                }
            }
            else -> Unit
        }
        val validator = spec.validator
        if (validator != null && !(spec.required && value.isBlank())) {
            val message = validator(value)
            if (message != null) issues += ParamIssue(spec.key, message)
        }
    }
    return issues
}
