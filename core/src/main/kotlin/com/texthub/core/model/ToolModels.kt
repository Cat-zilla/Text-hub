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
    /** The Universal Decoder: it detects formats and dispatches, it performs no cryptography. */
    DETECTION("Format detection (no crypto of its own)", false),
}

/**
 * What a registered tool actually *does*, read off its [Classification] and its parameters instead
 * of being declared a second time somewhere else.
 *
 * The Universal Decoder decides everything from this: whether a candidate may be handed a secret,
 * whether it can ask for cipher parameters, whether it is one-way, and whether it may be probed
 * automatically at all. Because it is derived, a tool that is added to the registry is classified by
 * the same rules as every existing one - and an encoding can never end up in a key or cipher path.
 */
enum class OperationKind(val label: String) {
    /** Deterministic encoding: the same input always gives the same output and nothing is secret. */
    ENCODING("Encoding"),

    /** Text transformation (reverse, case, line tools...): reversible, nothing secret. */
    TRANSFORM("Transformation"),

    /** Historical cipher with a key: never presented as modern encryption. */
    CLASSICAL_CIPHER("Classical cipher"),

    /** Modern authenticated encryption: needs its key material and refuses anything malformed. */
    ENCRYPTION("Encryption"),

    /** One-way digest, MAC or checksum: there is nothing to decrypt. */
    HASH("One-way hash"),

    /** Key material rather than a message (key pair generation). */
    KEY_MATERIAL("Key material"),

    /** A dispatcher: it identifies formats and hands the work to other registered tools. */
    DISPATCH("Format detection"),
    ;

    /** True when no key, password or cipher parameter takes part in the operation. */
    val deterministic: Boolean get() = this == ENCODING || this == TRANSFORM

    /** True when the operation cannot run without key material the user supplies. */
    val needsKeyMaterial: Boolean get() = this == ENCRYPTION || this == CLASSICAL_CIPHER

    /** True when the operation only ever produces an answer (a digest, a verdict, a report). */
    val oneWay: Boolean get() = this == HASH || this == KEY_MATERIAL
}

/** The operation kind a tool of this classification performs. */
val Classification.operationKind: OperationKind
    get() = when (this) {
        Classification.ENCODING,
        Classification.HISTORICAL_ENCODING,
        Classification.OBFUSCATION,
        -> OperationKind.ENCODING
        Classification.TEXT_TRANSFORM, Classification.REVERSIBLE_TRANSFORM -> OperationKind.TRANSFORM
        Classification.CLASSICAL_CIPHER -> OperationKind.CLASSICAL_CIPHER
        Classification.AUTHENTICATED_ENCRYPTION -> OperationKind.ENCRYPTION
        Classification.CRYPTOGRAPHIC_HASH,
        Classification.MESSAGE_AUTHENTICATION,
        Classification.CHECKSUM,
        -> OperationKind.HASH
        Classification.KEY_MATERIAL -> OperationKind.KEY_MATERIAL
        Classification.DETECTION -> OperationKind.DISPATCH
    }

/**
 * What a tool can say about pasted data **before** anything is run, declared in its own metadata.
 *
 * A hint is only a filter and a wording: matching one never decodes anything by itself. The
 * Universal Decoder first asks every registered tool whether its hint fits, then runs the tools that
 * fitted through their own implementations and reports what the implementations did. That is why a
 * hint may be cheap and generous - the validation decides, not the hint - and why a tool that
 * declares no hint at all is still considered (see `DetectionIndex`, which falls back to running it).
 */
data class DetectionHint(
    /** Characters the input may consist of (whitespace is always allowed). Blank = anything. */
    val alphabet: String = "",
    /** At least one of these must be present (blank = no requirement). */
    val mustContain: String = "",
    /** The trimmed input must start with this. */
    val prefix: String = "",
    /** The trimmed input must end with this. */
    val suffix: String = "",
    /** Ignore case when comparing [prefix] and [suffix]. */
    val ignoreCase: Boolean = true,
    val minLength: Int = 0,
    /** The length without whitespace must be a multiple of this (0 = no rule). */
    val multipleOf: Int = 0,
    /** A more precise length rule, for formats that can be unpadded (`length % 4 != 1`). */
    val lengthRule: ((Int) -> Boolean)? = null,
    /** How the format is named when this hint matches. */
    val label: String = "",
    /** Refines the label from the input itself (a variant, a digest size, a domain). */
    val labelFor: ((String) -> String)? = null,
    /** One user-facing sentence naming the evidence. */
    val evidence: String = "",
    /** Set for a digest-shaped hint: which digest this value's length suggests. */
    val hashAlgorithmFor: ((String) -> String?)? = null,
    /** A sentence about what the successful decode produced, appended to the evidence. */
    val validatedFor: ((String, String) -> String?)? = null,
    /** Refines the evidence sentence from the input itself. */
    val evidenceFor: ((String) -> String)? = null,
    /** Settings the payload records, so the user is never asked to contradict them. */
    val paramsFor: ((String) -> Map<String, String>)? = null,
    /**
     * True when the evidence is a header, a prefix or a version byte rather than a character set.
     * Only structural evidence may be reported as high confidence without being decoded.
     */
    val structural: Boolean = false,
    /**
     * Lets the format recognise itself in its own words (its own header check). Used by the formats
     * that carry a magic value, so the knowledge stays with the implementation.
     */
    val recognise: ((String) -> Boolean)? = null,
    /**
     * True for a format that is recognised by *syntax* rather than by being transformed: a document
     * that is already formatted is still that document, so "the tool accepted it" is the validation,
     * not "the tool changed it".
     */
    val acceptsSameOutput: Boolean = false,
    /**
     * The strongest marker this format has (Base64 padding, a trailing `=`), which raises a
     * validated match to high confidence. Deliberately about *structure*, not about length.
     */
    val strongWhen: ((String) -> Boolean)? = null,
    /** False when this format can be named but not processed as a whole (an IDN domain, key material). */
    val runnable: Boolean = true,
    /** Refines [runnable] from the input (a single label can be decoded, a whole domain cannot). */
    val runnableFor: ((String) -> Boolean)? = null,
    /**
     * What to tell the user when the format is recognised but cannot be processed here (key material,
     * an algorithm this build does not support). Empty means "say the generic sentence".
     */
    val unrunnableNote: String = "",
)

enum class ToolCategory(val label: String) {
    /**
     * Declared first on purpose: the picker groups tools by this enum's order, so the Universal
     * Decoder's section is the first thing in the list. It is a normal registered tool, not a
     * separate UI-only item.
     */
    SMART("Smart Tools"),
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
    /**
     * True for a value that belongs to the current session only: the app must not write it to disk
     * and must start with it empty next time. Used by the Universal Decoder's manual override, which
     * would otherwise force the same tool on every later analysis.
     */
    val sessionOnly: Boolean = false,
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
     * True for tools that always lead the picker list, above every category. The Universal Decoder
     * sets it: it is a normal registered tool, but the main Tools list is ordered by the registry
     * (with pinned tools first) and never by the favourites order, so nothing the user reorders can
     * move it. Favourites keep their own independent order.
     */
    val pinnedFirst: Boolean = false,
    /**
     * True when the result is an answer rather than a message: a digest, a verification verdict, a
     * measurement, an inspection report. The direction switch can still be meaningful (Hash vs
     * Verify), but pushing such a result back through the tool is not, so Swap is hidden.
     */
    val resultIsFinal: Boolean = false,
    /**
     * How this tool's input can be recognised, declared by the tool itself. A tool may offer several
     * shapes (AES-CBC reads both its own envelope and an OpenSSL file). A tool without a hint is
     * still considered by the Universal Decoder: it falls back to running the tool and believing it
     * only when the input decodes and re-encodes to exactly what was pasted.
     */
    val detection: List<DetectionHint> = emptyList(),
    /**
     * True for a tool that may create its result **only** when the user presses its own action
     * (the RSA key pair generator). Selecting the tool, changing a setting, restoring defaults or
     * any other state change must not run it, so generated key material is never silently created
     * or replaced behind the user's back. The app skips every automatic path for such a tool and
     * shows its explicit action instead.
     */
    val explicitActionOnly: Boolean = false,
) {
    /** What this tool does, derived from its classification so it can never be declared wrongly. */
    val operationKind: OperationKind get() = classification.operationKind

    /** False for a tool that must only run through its own explicit action ([explicitActionOnly]). */
    val shouldRunAutomatically: Boolean get() = !explicitActionOnly

    /** True when the tool cannot run without something only the user knows. */
    val needsKeyMaterial: Boolean get() = params.any { it.sensitive }

    /** The parameter that carries the secret this tool needs, or null when it needs none. */
    val secretSpec: ParamSpec? get() = params.firstOrNull { it.sensitive }

    /**
     * The parameters of this tool that a payload can be expected to carry (non-secret, required).
     * The Universal Decoder uses this to tell "this operation needs cipher parameters the payload
     * did not record" from "this operation only needs the secret".
     */
    val cipherParameters: List<ParamSpec>
        get() = if (operationKind != OperationKind.ENCRYPTION) {
            // Only a cipher has cipher parameters. An encoding, a transformation or a hash has
            // settings, but they are not cipher settings, and nothing may ever ask the user to
            // "check the cipher settings" for one of those tools.
            emptyList()
        } else {
            params.filter { !it.sensitive }
        }

    /**
     * The subset of [values] the app may write to disk: never a secret, never a value that only
     * belongs to the current session. Kept here, with the rest of the metadata, so the rule is
     * testable without an Android device.
     */
    fun rememberedParams(values: Map<String, String>): Map<String, String> = values.filter { (key, value) ->
        val spec = params.firstOrNull { it.key == key }
        spec != null && !spec.sensitive && !spec.sessionOnly && value.isNotEmpty()
    }

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

    /** The current non-empty values of this tool's sensitive parameters (never persisted anywhere). */
    fun sensitiveValues(values: Map<String, String>): Map<String, String> =
        values.filter { (key, value) -> value.isNotEmpty() && params.any { it.key == key && it.sensitive } }

    /**
     * The same values with every sensitive parameter back at its default and every other value
     * untouched - what "clear sensitive fields" means. Unknown keys are left alone.
     */
    fun withSensitiveCleared(values: Map<String, String>): Map<String, String> =
        values.mapValues { (key, value) ->
            val spec = params.firstOrNull { it.key == key }
            if (spec != null && spec.sensitive) spec.defaultValue else value
        }

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
