package com.texthub.core.detector

import com.texthub.core.ToolRegistry
import com.texthub.core.model.Direction
import com.texthub.core.model.OperationKind
import com.texthub.core.model.ParamKind
import com.texthub.core.model.ToolMeta
import com.texthub.core.model.operationKind

/**
 * What the Universal Decoder found, and how sure it is.
 *
 * The levels are deliberately coarse and deliberately humble: anything that is only "this looks
 * like the right character set" is [POSSIBLE] and is never decoded automatically, and nothing is
 * ever called certain.
 */
enum class Confidence(val label: String, val rank: Int) {
    /** Only character-set or length evidence - a candidate, not a conclusion. */
    POSSIBLE("Possible", 0),

    /** The structure fits and the format decodes, but the evidence is not unique. */
    LIKELY("Likely", 1),

    /** A magic header, a prefix, a version byte or a self-describing envelope decided it. */
    HIGH("High", 2),
}

/** What the user has to supply before an encrypted payload can be opened. */
enum class SecretKind(val label: String) {
    /** A password for a password-based envelope (PBKDF2 or the OpenSSL chain). */
    PASSWORD("Password"),

    /** The raw AES key the payload was written with. */
    KEY("AES key (hex or Base64)"),

    /** The RSA private key that can unwrap this payload. */
    PRIVATE_KEY("RSA private key (PEM)"),

    /**
     * The RSA public key an operation encrypts *to*. The Universal Decoder only ever decrypts, so
     * its candidates never ask for one - but the kind exists so that the label can never be wrong
     * for an RSA operation driven the other way.
     */
    PUBLIC_KEY("RSA public key (PEM)");

    /**
     * True for the two kinds that are PEM key material rather than a typed secret. A password and
     * an RSA private key are different inputs: this is what lets the app give PEM material a
     * multi-line editor and a saved-key action without changing how passwords behave.
     */
    val isRsaKey: Boolean get() = this == PRIVATE_KEY || this == PUBLIC_KEY
}

/**
 * One thing Text Hub recognised in the input, and the **existing** tool that would process it.
 *
 * [toolId] always names a registered tool: the Universal Decoder never implements a format of its
 * own, it hands the work to the tool that already does it.
 */
data class Candidate(
    /** Registered tool id that performs this operation. */
    val toolId: String,
    /** Human wording of what was detected, e.g. "Base64" or "AES-GCM payload (Text Hub v1)". */
    val label: String,
    val confidence: Confidence,
    /** Why it was detected, in one sentence the user can check. */
    val reason: String,
    /** True when this candidate can actually be run (hashes and key material cannot). */
    val actionable: Boolean = true,
    /** Settings read out of the payload itself, so the user is not asked to contradict them. */
    val suggestedParams: Map<String, String> = emptyMap(),
    /** Set for digest candidates: MD5, SHA-1, SHA-256, SHA-512. */
    val hashAlgorithm: String? = null,
    /** Which way the tool is driven. Every candidate here is a decoding/decryption. */
    val direction: Direction = Direction.DECODE,
    /** What to say when this is recognised but cannot be processed here. */
    val note: String? = null,
) {
    /** The registered tool this candidate names - the single source of truth for all of the below. */
    private val meta: ToolMeta get() = ToolRegistry.metaOf(toolId)

    /** The tool's display name, for the UI. */
    val toolName: String get() = meta.name

    /**
     * What the operation is. Derived, so a candidate that was produced by a character-set match
     * cannot claim to be encryption, and an encryption candidate cannot pretend to be an encoding.
     */
    val operation: OperationKind get() = meta.operationKind

    /**
     * True when the payload is encrypted and cannot be opened without key material. This is a
     * property of the *registered tool* (it declares a sensitive parameter), never of the detection
     * rule - which is what keeps deterministic encodings out of every key-related path.
     */
    val requiresSecret: Boolean get() = actionable && meta.needsKeyMaterial

    /**
     * The kind of secret the format needs. Only formats whose tool declares key material have one.
     */
    val secretKind: SecretKind? get() = if (requiresSecret) secretKindOf(meta, direction) else null

    /** The parameter name the target tool expects the secret in ("password", "key"). */
    val secretParam: String? get() = if (requiresSecret) meta.secretSpec?.key else null

    /**
     * True when the operation also depends on non-secret settings that the payload did not record
     * (a legacy AES payload without a key size, for example). An encoding, a transformation and a
     * hash are never in this state: they take no cipher parameters at all.
     */
    val requiresCipherParameters: Boolean
        get() = operation == OperationKind.ENCRYPTION && meta.cipherParameters.isNotEmpty()

    /** True for anything that only produces an answer: a digest, a MAC, a checksum, key material. */
    val oneWay: Boolean get() = meta.oneWay || meta.resultIsFinal

    /** True when the registered tool can actually perform this operation on text. */
    val isRunnable: Boolean get() = actionable && (!meta.oneWay || meta.hasDirectionChoice)
}

/**
 * What a tool's secret parameter means in the words the diagnosis uses. Key material is only called
 * an "RSA private key" for the tool that actually needs one; every other key is reported as a key.
 *
 * For the RSA tool the kind follows the [direction] the candidate is driven in: decrypting (the
 * only thing the Universal Decoder does) needs the private key, encrypting would need the public
 * one. The wording is therefore derived from the operation, never hard-coded to "Key".
 */
fun secretKindOf(meta: ToolMeta, direction: Direction = Direction.DECODE): SecretKind = when {
    meta.secretSpec?.kind == ParamKind.PASSWORD -> SecretKind.PASSWORD
    meta.id == "rsa" -> if (direction == Direction.DECODE) SecretKind.PRIVATE_KEY else SecretKind.PUBLIC_KEY
    else -> SecretKind.KEY
}

/**
 * Builds a candidate for a registered tool.
 *
 * Everything the registry already knows is filled in here rather than in each detection rule: the
 * kind of operation, whether it is one-way, whether it needs a secret, which parameter carries that
 * secret and whether the operation also needs cipher parameters. A detection rule only supplies the
 * evidence (label, confidence, reason and whatever the payload itself recorded).
 *
 * Because these are properties of the registered tool, no rule - present or future - can make an
 * encoding ask for a password or make a digest look decryptable.
 */
fun registeredCandidate(
    toolId: String,
    label: String,
    confidence: Confidence,
    reason: String,
    actionable: Boolean = true,
    suggestedParams: Map<String, String> = emptyMap(),
    hashAlgorithm: String? = null,
    direction: Direction = Direction.DECODE,
    note: String? = null,
): Candidate {
    // The id is checked, not looked up with a fallback: a candidate is what the user taps, so one
    // that named an unregistered tool would fail in their hands rather than in a test.
    require(ToolRegistry.all.any { it.meta.id == toolId }) { "not a registered tool: $toolId" }
    val meta = ToolRegistry.metaOf(toolId)
    return Candidate(
        toolId = meta.id,
        label = label.ifBlank { meta.name },
        confidence = confidence,
        reason = reason,
        actionable = actionable,
        suggestedParams = suggestedParams,
        hashAlgorithm = hashAlgorithm,
        direction = direction,
        note = note,
    )
}

/** One layer of the decode chain, in the order it was applied. */
data class Step(
    val label: String,
    val toolId: String,
    val confidence: Confidence,
    /** Extra sentence for this layer, e.g. which key size the payload turned out to need. */
    val note: String? = null,
)

/** The outcome of one analysis. */
sealed class Diagnosis {

    abstract val steps: List<Step>

    /** A layer (or several) was decoded and [text] is the result. */
    data class Decoded(
        override val steps: List<Step>,
        val text: String,
    ) : Diagnosis()

    /** The format was identified but it is encrypted: the user has to supply [candidate.secretKind]. */
    data class NeedsSecret(
        override val steps: List<Step>,
        val candidate: Candidate,
    ) : Diagnosis()

    /**
     * More than one format fits and none of them is certain, so nothing is decoded and the user
     * picks. This is what a 64-character hexadecimal value produces: hex versus a SHA-256 digest.
     */
    data class Choose(
        override val steps: List<Step>,
        val candidates: List<Candidate>,
        val note: String,
    ) : Diagnosis()

    /** The input is a one-way digest: it can be verified against a candidate text, never decrypted. */
    data class HashOnly(
        override val steps: List<Step>,
        val candidates: List<Candidate>,
        val note: String,
    ) : Diagnosis()

    /** Something was identified, but it cannot be opened here (key material, unsupported JWE alg). */
    data class NotSupported(
        override val steps: List<Step>,
        val candidates: List<Candidate>,
        val note: String,
    ) : Diagnosis()

    /** The identified format was tried and refused - a wrong password, a broken tag, bad padding. */
    data class Failed(
        override val steps: List<Step>,
        val candidate: Candidate,
        val message: String,
    ) : Diagnosis()

    /** Nothing recognisable: say so plainly instead of guessing. */
    data class Unrecognized(
        override val steps: List<Step>,
        val candidates: List<Candidate>,
        val note: String,
    ) : Diagnosis()
}

/** Convenience accessors for the UI. */
val Diagnosis.isSuccess: Boolean get() = this is Diagnosis.Decoded

/**
 * The kind of secret this analysis is about, or null when no secret is involved.
 *
 *  * [Diagnosis.NeedsSecret]: the secret the payload is still waiting for.
 *  * [Diagnosis.Failed]: the secret that was tried and refused (a wrong key stays the same *kind*
 *    of key, so the field must not fall back to a password editor after one failed attempt).
 *  * [Diagnosis.Decoded]: the secret a layer of the chain was opened with, if any.
 *
 * Read from the registered tool of the step or candidate, exactly like [Candidate.secretKind]; the
 * detection itself does not depend on it (a payload is recognised before any key exists).
 */
val Diagnosis.secretKind: SecretKind?
    get() = when (this) {
        is Diagnosis.NeedsSecret -> candidate.secretKind
        is Diagnosis.Failed -> candidate.secretKind
        is Diagnosis.Decoded -> steps
            .map { ToolRegistry.metaOf(it.toolId) }
            .firstOrNull { it.needsKeyMaterial }
            ?.let { secretKindOf(it, Direction.DECODE) }
        else -> null
    }
val Diagnosis.confidenceOfLastStep: Confidence?
    get() = steps.lastOrNull()?.confidence
val Diagnosis.usedTools: List<String> get() = steps.map { it.toolId }
