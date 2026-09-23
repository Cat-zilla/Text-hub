package com.texthub.core.detector

import com.texthub.core.ProcessingEngine
import com.texthub.core.TextProcessor
import com.texthub.core.ToolRegistry
import com.texthub.core.crypto.digestAlgorithmOf
import com.texthub.core.defaultParams
import com.texthub.core.model.DetectionHint
import com.texthub.core.model.Direction
import com.texthub.core.model.OperationKind
import com.texthub.core.model.ToolMeta
import java.util.concurrent.CancellationException

/**
 * The registry, as the Universal Decoder sees it.
 *
 * There is **no list of tools here**. [entries] is built from [ToolRegistry.all], and every entry is
 * asked the same three questions:
 *
 *  1. *Does the input look like something you read?* - answered by the [DetectionHint]s the tool
 *     declares in its own metadata (a character set, a prefix, a length rule, a header check) or, for
 *     a tool that declares none, by the generic probe at the bottom of this file.
 *  2. *Can you actually process it?* - answered by **running the tool itself** through
 *     [ProcessingEngine]. A hint that matched but whose tool refuses the input, or leaves it
 *     unchanged, produces no candidate at all: nothing is reported that was not validated with the
 *     existing implementation.
 *  3. *How strong is the evidence?* - a header or a magic value is high confidence; a character set
 *     plus a successful decode is "likely"; a character set alone is only "possible" and is never
 *     decoded automatically.
 *
 * Because of that, a tool that is added to the registry later takes part in detection immediately:
 * with hints of its own if it declares them, and with the generic probe if it does not. The
 * Universal Decoder still implements no format of its own - every result comes from the registered
 * tool, with that tool's own validation and error messages.
 */
object DetectionIndex {

    /** One registered tool and the hints it declared for itself. */
    class Entry(val processor: TextProcessor) {
        val meta: ToolMeta get() = processor.meta
        val hints: List<DetectionHint> get() = processor.meta.detection
    }

    /** Every registered tool, in registry order (the Universal Decoder itself leads). */
    val entries: List<Entry> by lazy { ToolRegistry.all.map { Entry(it) } }

    /**
     * Why each registered tool does - or does not - take part in detection. This is what makes the
     * coverage checkable, and reportable, without a list of tools anywhere:
     *
     *  * `"hint"` - the tool describes its own input and is probed with that description;
     *  * `"generic"` - the tool declares nothing, so it is probed by being run;
     *  * `"symmetric"` - a reversible transformation that produces readable text in both
     *    directions (ROT13, Atbash, reverse). No input can be evidence for it, so it is never a
     *    candidate and stays reachable through the manual override; a *new* deterministic tool
     *    cannot land here by accident - it has to declare itself symmetric;
     *  * `"keyed"` - the tool needs key material, so only payloads it can recognise structurally
     *    are reported;
     *  * `"one-way"` - the tool produces an answer (a digest, key material), which cannot be
     *    decoded back;
     *  * `"dispatcher"` - the Universal Decoder itself.
     */
    fun accountedFor(): Map<String, String> = entries.associate { entry ->
        val meta = entry.meta
        meta.id to when {
            meta.operationKind == OperationKind.DISPATCH -> "dispatcher"
            meta.detection.isNotEmpty() -> "hint"
            genericProbes.any { it.meta.id == meta.id } -> "generic"
            meta.operationKind.deterministic -> "symmetric"
            meta.operationKind.needsKeyMaterial -> "keyed"
            meta.operationKind.oneWay -> "one-way"
            else -> "other"
        }
    }

    /** Tools that are probed generically because they declare no hints of their own. */
    private val genericProbes: List<Entry> by lazy {
        entries.filter { entry ->
            entry.hints.isEmpty() &&
                entry.meta.operationKind.deterministic &&
                !entry.meta.symmetric &&
                entry.meta.supportsSwap &&
                !entry.meta.oneWay &&
                !entry.meta.inputOptional
        }
    }

    /**
     * Characters that make a string look encoded rather than written: if a format whose alphabet
     * includes letters decodes to bytes that are *not* text, and the input holds none of these, the
     * match is dropped instead of being reported as a possibility. It is what stops an ordinary word
     * of the right length from being offered as Base64.
     */
    private const val SIGNAL_CHARACTERS = "+/=_-~%\$:;<>#@!*^|\\[]{}"

    /** Above this size the cheap checks still run, but nothing is validated by decoding it all. */
    private const val FULL_VALIDATION_LIMIT = 512 * 1024

    /** Every candidate for [text], strongest first. */
    fun probe(text: String): List<Candidate> {
        val input = text.trim()
        if (input.isEmpty()) return emptyList()
        // A value of exactly digest length is a *possible* digest and nothing more: the same
        // characters decode as hexadecimal too, so nothing about it may be reported above
        // "possible" (see the digest rule in the decoder).
        val digestShaped = digestAlgorithmOf(input) != null
        val found = mutableListOf<Candidate>()
        for (entry in entries) {
            if (entry.meta.operationKind == OperationKind.DISPATCH) continue
            // Candidate isolation: one tool whose hint misbehaves must not abort detection for
            // every other tool - it only loses its own candidacy (see [candidateIsolated]).
            entry.hints.forEach { hint ->
                candidateIsolated { match(entry, hint, input, digestShaped) }?.let { found += it }
            }
        }
        // Tools that declare no hint of their own are still considered: they are probed by running
        // them, in the order the registry lists them.
        for (entry in genericProbes) {
            candidateIsolated { generic(entry, input) }?.let { found += it }
        }
        return found
            .distinctBy { candidate -> candidate.toolId to candidate.suggestedParams }
            .sortedWith(
                compareByDescending<Candidate> { it.confidence.rank }
                    .thenByDescending { it.actionable },
            )
    }

    /**
     * Runs one candidate's detection and turns an unexpected failure into "no candidate".
     *
     * This is the first ring of the candidate isolation the Universal Decoder promises: a defect in
     * a single hint lambda or a single registered tool must cost that tool its own candidacy, never
     * the whole detection pass and never the process. `ToolException` is deliberately not special -
     * a hint that throws it is broken in the same way as one that throws anything else.
     */
    private inline fun <T> candidateIsolated(block: () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }

    /** Test seam for [candidateIsolated]: the isolation contract, checked directly. */
    internal inline fun <T> isolatedCandidate(block: () -> T): T? = candidateIsolated(block)

    /** The digests [text] could be, one candidate per algorithm. Used by the decoder's hash rule. */
    fun digestCandidates(text: String): List<Candidate> =
        probe(text).filter { it.hashAlgorithm != null }

    // ------------------------------------------------------------------ matching

    private fun match(entry: Entry, hint: DetectionHint, text: String, digestShaped: Boolean): Candidate? {
        if (!fits(hint, text)) return null
        val meta = entry.meta
        val label = hint.labelFor?.invoke(text) ?: hint.label.ifBlank { meta.name }
        val evidence = hint.evidenceFor?.invoke(text) ?: hint.evidence
        val params = hint.paramsFor?.invoke(text).orEmpty()
        val runnable = hint.runnable && (hint.runnableFor?.invoke(text) ?: true)

        // A header, a magic value or a version byte decides on its own: that is structural evidence.
        if (hint.structural) {
            return registeredCandidate(
                toolId = meta.id,
                label = label,
                confidence = Confidence.HIGH,
                reason = evidence,
                actionable = runnable,
                suggestedParams = params,
                note = if (runnable) null else hint.unrunnableNote.ifBlank { null },
            )
        }

        // A format that can only be *named* here (a digest, key material, a whole domain) is reported
        // at "possible" with the sentence its own metadata supplies: nothing is decoded, so nothing
        // is claimed beyond the shape.
        if (!runnable) {
            return registeredCandidate(
                toolId = meta.id,
                label = label,
                confidence = Confidence.POSSIBLE,
                reason = evidence,
                actionable = false,
                suggestedParams = params,
                hashAlgorithm = hint.hashAlgorithmFor?.invoke(text),
                note = hint.unrunnableNote.ifBlank { null },
            )
        }
        // Everything else has to survive the tool itself.
        val validation = validate(entry, hint, text, params) ?: return null
        val strong = hint.strongWhen?.invoke(text) == true
        val confidence = when {
            digestShaped -> Confidence.POSSIBLE
            validation.readable && strong -> Confidence.HIGH
            validation.readable -> Confidence.LIKELY
            else -> Confidence.POSSIBLE
        }
        // A character set that also contains ordinary letters needs one more sign that this is not
        // just a word: padding, a symbol, or a decode that produced readable text.
        // A word made only of letters is never offered as an alphabet-based encoding; a value that
        // also carries digits or symbols still is, at "possible".
        if (!validation.readable && !strong &&
            hint.alphabet.any { it.isLetter() } && text.none { it in SIGNAL_CHARACTERS } &&
            text.all { it.isLetter() }
        ) {
            return null
        }
        return registeredCandidate(
            toolId = meta.id,
            label = label,
            confidence = confidence,
            reason = listOfNotNull(evidence.ifBlank { null }, validation.detail).joinToString(" "),
            actionable = runnable,
            suggestedParams = params,
            hashAlgorithm = hint.hashAlgorithmFor?.invoke(text),
            note = if (runnable) null else hint.unrunnableNote.ifBlank { null },
        )
    }

    /** The cheap membership checks: no tool is run before its hint has narrowed the input down. */
    private fun fits(hint: DetectionHint, text: String): Boolean {
        val trimmed = text.trim()
        val compact = trimmed.filterNot { it.isWhitespace() }
        if (hint.prefix.isNotEmpty() && !trimmed.startsWith(hint.prefix, hint.ignoreCase)) return false
        if (hint.suffix.isNotEmpty() && !trimmed.endsWith(hint.suffix, hint.ignoreCase)) return false
        if (hint.minLength > 0 && compact.length < hint.minLength) return false
        if (hint.multipleOf > 0 && compact.length % hint.multipleOf != 0) return false
        if (hint.lengthRule?.invoke(compact.length) == false) return false
        if (hint.alphabet.isNotEmpty() && compact.any { it !in hint.alphabet }) return false
        if (hint.mustContain.isNotEmpty() && compact.none { it in hint.mustContain }) return false
        return hint.recognise?.invoke(trimmed) ?: true
    }

    /** What running the registered tool on the input produced. */
    private data class Validation(val readable: Boolean, val detail: String?)

    /**
     * Runs the tool's own decoding direction. Returns null when the tool refused the input or left it
     * exactly as it was - both mean there is nothing to report for this format.
     *
     * Above [FULL_VALIDATION_LIMIT] nothing is validated by decoding it: the cheap character-set
     * checks still run (a structural hint still reports), but a value that size is not run through
     * every matching tool on every change of the text - that is what [validatesFully] documents,
     * and what keeps a large paste from being decoded dozens of times per keystroke.
     */
    private fun validate(
        entry: Entry,
        hint: DetectionHint,
        text: String,
        params: Map<String, String>,
    ): Validation? {
        if (!validatesFully(text)) return null
        val meta = entry.meta
        val outcome = ProcessingEngine.run(
            entry.processor,
            text,
            entry.processor.defaultParams() + params,
            Direction.DECODE,
        )
        if (outcome.error != null) return null
        val output = outcome.output
        if (output.isEmpty()) return null
        if (output == text && !hint.acceptsSameOutput) return null
        val readable = isReadableText(output)
        return Validation(readable, hint.validatedFor?.invoke(text, output))
    }

    /**
     * The generic probe: a tool that declares no hints is still considered. It has to decode the
     * input *and* re-encode that result to exactly what was pasted before it is reported, which is
     * why a probe can never turn ordinary text into a made-up result. Ordinary prose is skipped
     * entirely: without a declared marker there is no evidence to justify guessing at it.
     */
    private fun generic(entry: Entry, text: String): Candidate? {
        if (text.length > 64 * 1024) return null
        if (looksLikeProse(text)) return null
        val meta = entry.meta
        val params = entry.processor.defaultParams()
        val decoded = ProcessingEngine.run(entry.processor, text, params, Direction.DECODE)
        val output = decoded.output
        if (decoded.error != null || output.isEmpty() || output == text) return null
        if (!isReadableText(output)) return null
        val reEncoded = ProcessingEngine.run(entry.processor, output, params, Direction.ENCODE)
        if (reEncoded.error != null || reEncoded.output != text) return null
        return registeredCandidate(
            toolId = meta.id,
            label = meta.name,
            confidence = Confidence.LIKELY,
            reason = "${meta.name} decodes this and reproduces exactly the text you pasted, which is " +
                "the strongest check available for a format that carries no marker of its own.",
            suggestedParams = params.filterKeys { key: String -> params[key] != entry.processor.defaultParams()[key] },
        )
    }

    /** True for text that reads like something a person wrote: it has words and a space between them. */
    private fun looksLikeProse(text: String): Boolean =
        isReadableText(text) && text.contains(' ') && text.length < 4096

    /**
     * True when decoded bytes look like text a person would paste: mostly printable, no stray
     * control characters. This is what separates "Base64 of a sentence" from "Base64 of a JPEG".
     */
    fun isReadableText(text: String): Boolean {
        if (text.isEmpty()) return false
        if (text.any { it.code < 0x20 && it != '\n' && it != '\r' && it != '\t' }) return false
        if (text.contains('\uFFFD')) return false
        val printable = text.count { it.isLetterOrDigit() || it.isWhitespace() || it in PUNCTUATION }
        return printable.toDouble() / text.length >= 0.75
    }

    /** True when the input is small enough to be decoded and encoded again for validation. */
    fun validatesFully(text: String): Boolean = text.length <= FULL_VALIDATION_LIMIT

    private const val PUNCTUATION = ".,;:!?'\"()[]{}<>@#\$%^&*-_=+~`|/\\"
}
