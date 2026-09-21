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
)

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
) {
    val searchIndex: String =
        (listOf(name, classification.label, category.label) + keywords).joinToString(" ").lowercase()
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
