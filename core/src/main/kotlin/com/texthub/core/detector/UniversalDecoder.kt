package com.texthub.core.detector

import com.texthub.core.ProcessingEngine
import com.texthub.core.ToolRegistry
import com.texthub.core.crypto.digestAlgorithmOf
import com.texthub.core.crypto.PayloadKeySize
import com.texthub.core.defaultParams
import com.texthub.core.model.Direction
import com.texthub.core.model.ProcessOutcome
import com.texthub.core.model.operationKind
import com.texthub.core.util.hexDigit
import java.util.concurrent.CancellationException

/**
 * The Universal Decoder: it looks at pasted data, says what it recognises **and how sure it is**,
 * and then hands the work to the existing tool for that format.
 *
 * Four rules shape everything in this file:
 *
 *  1. **No format is implemented here, and no tool is named here.** Detection asks the registry
 *     ([DetectionIndex]) which tools fit the input, and every decode, decrypt and validation runs
 *     through the registered tool that already does it ([ToolRegistry] + [ProcessingEngine]), so a
 *     payload that the *AES-GCM Encryption* tool refuses is refused here in exactly the same words.
 *  2. **An operation is what its tool says it is.** Whether a candidate may be handed a secret, may
 *     ask for cipher parameters or can only produce an answer is read from the registered tool's own
 *     metadata, never from the detection rule that produced it - which is why a deterministic
 *     encoding can never enter a key- or cipher-parameter path.
 *  3. **Nothing is guessed.** A secret is never tried, never brute-forced and never assumed. If a
 *     payload needs a password, the analysis stops and asks for it.
 *  4. **Confidence is earned.** Only structural evidence (a magic header, a version byte, a
 *     self-describing envelope) is [Confidence.HIGH]; a character set that also decodes is
 *     [Confidence.LIKELY]; a character set on its own is [Confidence.POSSIBLE] and is never decoded
 *     automatically.
 */
object UniversalDecoder {

    /** The registered tool id of the Universal Decoder itself. */
    const val TOOL_ID = "universal"

    const val DEFAULT_MAX_DEPTH = 4

    /** Hard ceiling for nested decoding, so a crafted input cannot spin forever. */
    const val MAX_MAX_DEPTH = 8

    /** Parameter keys of the Universal Decoder tool. */
    const val PARAM_SECRET = "secret"
    const val PARAM_HASH_CANDIDATE = "hashCandidate"
    const val PARAM_MAX_DEPTH = "maxDepth"
    const val PARAM_PREFER = "prefer"

    // ------------------------------------------------------------------ entry points

    /**
     * Analyses [input] and, where the evidence allows it, decodes one or more layers.
     *
     * @param params the Universal Decoder's own parameters ([PARAM_SECRET], [PARAM_PREFER],
     *   [PARAM_MAX_DEPTH], [PARAM_HASH_CANDIDATE]).
     */
    fun analyze(input: String, params: Map<String, String> = emptyMap()): Diagnosis {
        if (input.isBlank()) {
            return Diagnosis.Unrecognized(
                steps = emptyList(),
                candidates = emptyList(),
                note = "Nothing to analyse yet - paste some encoded or encrypted text.",
            )
        }
        val secret = params[PARAM_SECRET].orEmpty()
        val prefer = params[PARAM_PREFER]
            ?.trim()
            ?.takeIf { id -> id.isNotEmpty() && ToolRegistry.all.any { it.meta.id == id } }
        val depth = (params[PARAM_MAX_DEPTH]?.toIntOrNull() ?: DEFAULT_MAX_DEPTH)
            .coerceIn(1, MAX_MAX_DEPTH)

        var current = input
        val steps = mutableListOf<Step>()
        val seen = mutableSetOf<String>()

        for (layer in 0 until depth) {
            if (current.isBlank()) break
            // Cycle protection: an encoding that maps text onto itself (an empty decode, a
            // Base64 string that decodes to itself) must not start an endless chain.
            if (!seen.add(current)) break

            val candidates = if (layer == 0 && prefer != null) {
                listOf(manualCandidate(prefer, current))
            } else {
                detect(current)
            }
            if (candidates.isEmpty()) break

            val best = candidates.first()
            val bestActionable = candidates.firstOrNull { it.actionable }
            val hashCandidate = candidates.firstOrNull { it.hashAlgorithm != null }

            // A value that could be a digest is reported as a digest even when it also happens to
            // be legible hexadecimal: "possible SHA-256" is the more useful answer, and it is the
            // one that never pretends to decrypt anything.
            if (hashCandidate != null && (bestActionable == null || bestActionable.confidence == Confidence.POSSIBLE)) {
                if (steps.isEmpty()) {
                    return Diagnosis.HashOnly(
                        steps = emptyList(),
                        candidates = candidates.filter { it.hashAlgorithm != null },
                        note = "This is a one-way hash: it cannot be decrypted. Type the text you want " +
                            "to check against it in \"Text to compare (hash)\" and the matching digest " +
                            "is computed instead.",
                    )
                }
                break
            }

            // Digest and key material: real, but there is nothing to decode.
            if (!best.actionable) {
                if (steps.isEmpty()) return nonActionableDiagnosis(best, candidates)
                break
            }

            if (best.requiresSecret && secret.isBlank()) {
                return Diagnosis.NeedsSecret(steps + best.toStep(), best)
            }

            // Not confident enough to act: show the candidates instead of picking one.
            val ambiguous = candidates.filter {
                it.actionable && it.confidence == best.confidence &&
                    (it.toolId != best.toolId || it.suggestedParams != best.suggestedParams)
            }
            if (best.confidence == Confidence.POSSIBLE || (best.confidence == Confidence.LIKELY && ambiguous.size > 1)) {
                if (steps.isEmpty()) {
                    return Diagnosis.Choose(
                        steps = emptyList(),
                        candidates = candidates.filter { it.actionable },
                        note = possibleNote(best),
                    )
                }
                break
            }

            val resolved = resolveFromPayload(best, current, secret)
            val outcome = runCandidate(resolved, current, secret)
            if (outcome.error != null) {
                if (steps.isEmpty()) {
                    return Diagnosis.Failed(steps = emptyList(), candidate = resolved, message = outcome.error)
                }
                // An outer layer decoded but this one failed: report the failure, never the garbage.
                return Diagnosis.Failed(steps + resolved.toStep(), resolved, outcome.error)
            }
            val text = outcome.output
            if (text.isBlank() && steps.isEmpty()) break
            steps += resolved.toStep()
            if (text == current) break
            current = text
        }

        return if (steps.isEmpty()) {
            Diagnosis.Unrecognized(
                steps = emptyList(),
                candidates = emptyList(),
                note = "No supported format was recognised in this text. Ordinary readable text is " +
                    "not an error: it is simply not decoded on purpose. Try encoded, encrypted or " +
                    "transformed text, or pick the tool you meant with the tool picker.",
            )
        } else {
            Diagnosis.Decoded(steps = steps, text = current)
        }
    }

    /**
     * The input, plus - when the input is a hash and the user typed a candidate - the verdict of the
     * existing *Hash* tool about whether the two match. Reuses that tool's verification rather than
     * recomputing a digest here.
     */
    fun compareHash(input: String, candidateText: String, params: Map<String, String>): String {
        val digest = input.trim()
        val algorithm = digestAlgorithmOf(digest) ?: return ""
        val tool = ToolRegistry.get("hash")
        val toolParams = tool.defaultParams() +
            mapOf(
                "algorithm" to algorithm,
                "format" to (if (digest.any { it in 'A'..'F' }) "hexUpper" else "hex"),
                "compare" to candidateText,
            )
        val outcome = ProcessingEngine.run(tool, digest, toolParams, Direction.DECODE)
        return outcome.error ?: outcome.output
    }

    /**
     * Every detection candidate for [input], strongest first.
     *
     * The list comes from the registry: each registered tool is asked whether its own declared
     * evidence fits the input, the tools that fit are run through their own implementations, and the
     * results are ranked by how strong that evidence is. Used by the tests and the info sheet, and by
     * [analyze] for every layer it unwraps.
     */
    fun detect(input: String): List<Candidate> = DetectionIndex.probe(input)

    // ------------------------------------------------------------------ dispatch

    /**
     * The candidate a manual override produces: the registered tool the user picked, described by
     * its own metadata (so its operation kind, its secret parameter and its one-way-ness are the
     * tool's, not the picker's opinion).
     */
    private fun manualCandidate(toolId: String, input: String): Candidate {
        val meta = ToolRegistry.metaOf(toolId)
        return registeredCandidate(
            toolId = toolId,
            label = meta.name,
            confidence = Confidence.HIGH,
            reason = "You chose ${meta.name} manually, so automatic detection was set aside for " +
                "this step.",
            actionable = input.isNotBlank() && !meta.oneWay,
        )
    }

    /**
     * Fills in what the payload itself says, so the user is never asked to contradict it. Only
     * parameters that *are* in the payload are read; a legacy payload that records no key size is
     * opened with the size its authentication tag turns out to need, and that is reported.
     *
     * The question "which key size does this authenticate with?" is asked of the format's own
     * implementation through [PayloadKeySize], so no format is special-cased here.
     *
     * Candidate isolation: this reads the *pasted payload* with the *typed secret* through the
     * format's own reader, so a defect there must cost this candidate its extra setting and nothing
     * more - the candidate still goes to the registered tool, whose own outcome (or whose refusal)
     * is what the user sees. It must never abort the whole analysis.
     */
    private fun resolveFromPayload(candidate: Candidate, input: String, secret: String): Candidate = try {
        resolveFromPayloadChecked(candidate, input, secret)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        candidate
    }

    private fun resolveFromPayloadChecked(candidate: Candidate, input: String, secret: String): Candidate {
        if (secret.isEmpty() || candidate.secretKind != SecretKind.PASSWORD) return candidate
        val sizeKey = candidate.suggestedParams.keys.firstOrNull { it.equals("keySize", ignoreCase = true) }
            ?: ToolRegistry.metaOf(candidate.toolId).cipherParameters
                .firstOrNull { it.key.equals("keySize", ignoreCase = true) }
                ?.key
            ?: return candidate
        if (candidate.suggestedParams.containsKey(sizeKey)) return candidate
        val probe = ToolRegistry.get(candidate.toolId) as? PayloadKeySize ?: return candidate
        val payload = input.trim()
        val size = probe.keySizeOf(payload, secret.toCharArray()) ?: return candidate
        return candidate.copy(
            suggestedParams = candidate.suggestedParams + (sizeKey to (size * 8).toString()),
            reason = candidate.reason + " The payload records no key size; it authenticates with a " +
                "${size * 8}-bit key, so that size is used.",
        )
    }

    /** Runs the registered tool for [candidate] - the Universal Decoder has no algorithms of its own. */
    private fun runCandidate(candidate: Candidate, input: String, secret: String): ProcessOutcome {
        val tool = ToolRegistry.get(candidate.toolId)
        val params = LinkedHashMap(tool.defaultParams())
        params.putAll(candidate.suggestedParams)
        // Only an operation that declares key material may be handed the typed secret. Encodings,
        // transformations and hashes never see it, which is what keeps a pasted encoded string out
        // of every key- or cipher-parameter check, whatever produced the candidate.
        if (candidate.operation.needsKeyMaterial && candidate.secretParam != null && secret.isNotEmpty()) {
            params[candidate.secretParam] = secret
        }
        return ProcessingEngine.run(tool, input, params, candidate.direction)
    }

    // ------------------------------------------------------------------ helpers

    private fun Candidate.toStep() = Step(label = label, toolId = toolId, confidence = confidence)

    private fun nonActionableDiagnosis(best: Candidate, candidates: List<Candidate>): Diagnosis = when {
        best.hashAlgorithm != null -> Diagnosis.HashOnly(
            steps = emptyList(),
            candidates = candidates.filter { it.hashAlgorithm != null },
            note = "This is a one-way hash: it cannot be decrypted. Type the text you want to check " +
                "against it in \"Text to compare\" and the matching digest is computed instead.",
        )
        // The format itself explains what it is and what to do with it (key material, an algorithm
        // this build does not implement): the sentence comes from the format's own metadata.
        best.note != null -> Diagnosis.NotSupported(
            steps = emptyList(),
            candidates = candidates,
            note = best.note!!,
        )
        else -> Diagnosis.NotSupported(
            steps = emptyList(),
            candidates = candidates,
            note = "Text Hub recognises this, but it is not something it can decode or decrypt.",
        )
    }

    private fun possibleNote(best: Candidate): String = when (best.confidence) {
        Confidence.POSSIBLE ->
            "Only the character set (and possibly the length) points at a format, which is not enough " +
                "to decode automatically. Choose the one you meant."
        else ->
            "More than one format fits this input equally well, so nothing was decoded automatically. " +
                "Pick the one you meant."
    }

    /** True when [text] is hexadecimal digits and nothing else (used by the formatting helpers). */
    internal fun isHex(text: String): Boolean =
        text.isNotEmpty() && text.all { hexDigit(it) >= 0 }
}
