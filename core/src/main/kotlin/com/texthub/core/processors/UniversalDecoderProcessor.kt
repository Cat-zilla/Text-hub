package com.texthub.core.processors

import com.texthub.core.TextProcessor
import com.texthub.core.ToolRegistry
import com.texthub.core.detector.Candidate
import com.texthub.core.detector.Confidence
import com.texthub.core.detector.Diagnosis
import com.texthub.core.detector.SecretKind
import com.texthub.core.detector.UniversalDecoder
import com.texthub.core.model.Classification
import com.texthub.core.model.Direction
import com.texthub.core.model.ParamKind
import com.texthub.core.model.ParamSpec
import com.texthub.core.model.ToolCategory
import com.texthub.core.model.ToolInfo
import com.texthub.core.model.ToolMeta

/**
 * The Universal Decoder: paste data, and Text Hub says what it recognises, how sure it is, and what
 * it would take to open it.
 *
 * It is a dispatcher, not a second implementation: detection lives in [UniversalDecoder] and every
 * operation it performs is delegated to the registered tool for that format, so the results, the
 * validation and the error messages are the ones the individual tools already produce.
 *
 * The tool's output is the decoded text when a layer could be decoded - so Copy, Share and "use as
 * input" behave like every other tool - and an explanation when it could not. The app additionally
 * renders the structured [Diagnosis] (detected format, confidence, reason, chain) above the result.
 */
class UniversalDecoderProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = UniversalDecoder.TOOL_ID,
        name = "Universal Decoder",
        glyph = "UD",
        // A category of its own, declared first in the enum, so the tool leads the picker list.
        category = ToolCategory.SMART,
        classification = Classification.DETECTION,
        encodeLabel = "Analyze",
        decodeLabel = "Analyze",
        // One operation, no direction and no swap: "Analyze" is the whole tool.
        oneWay = true,
        // It leads every list; reordering favourites never moves it.
        pinnedFirst = true,
        params = listOf(
            ParamSpec(
                key = UniversalDecoder.PARAM_SECRET,
                label = "Password / key",
                kind = ParamKind.PASSWORD,
                defaultValue = "",
                sensitive = true,
                hint = "Only for encrypted input",
                helper = "Used only when the detected payload is encrypted, and only the kind of " +
                    "secret that payload needs. An RSA payload takes the complete PEM key block " +
                    "(or one of your saved key pairs). Text Hub never guesses, tries or " +
                    "brute-forces a password or a key, and it is never stored.",
            ),
            ParamSpec(
                key = UniversalDecoder.PARAM_HASH_CANDIDATE,
                label = "Text to compare (hash)",
                kind = ParamKind.TEXT,
                defaultValue = "",
                advanced = true,
                hint = "Optional",
                helper = "If the input turns out to be a one-way hash, this text is hashed with the " +
                    "same algorithm and the result is compared - that is the only thing a digest " +
                    "allows.",
            ),
            ParamSpec(
                key = UniversalDecoder.PARAM_MAX_DEPTH,
                label = "Maximum layers",
                kind = ParamKind.NUMBER,
                defaultValue = UniversalDecoder.DEFAULT_MAX_DEPTH.toString(),
                min = 1,
                max = UniversalDecoder.MAX_MAX_DEPTH,
                advanced = true,
                helper = "How many nested layers may be unwrapped in one go (Base64 inside Base64 " +
                    "inside an encrypted payload...). Cycles are detected and stop the chain.",
            ),
            ParamSpec(
                key = UniversalDecoder.PARAM_PREFER,
                label = "Manual override (tool id)",
                kind = ParamKind.TEXT,
                defaultValue = "",
                advanced = true,
                // A forced tool belongs to this session only. Remembering it would silently send the
                // next analysis - in this run or on the next launch - to a tool the user picked for
                // something else, which is exactly the trap the override must never become.
                sessionOnly = true,
                hint = "Detect automatically",
                validator = { value ->
                    // The override is either empty (detect from the input) or the id of a tool that
                    // actually exists; anything else would be a setting that silently does nothing.
                    if (value.isBlank() || ToolRegistry.all.any { it.meta.id == value.trim() }) {
                        null
                    } else {
                        "That is not one of the tools in Text Hub."
                    }
                },
                helper = "Filled in by the \u201CDetect automatically\u201D control on the result " +
                    "card, which opens the same searchable tool list. Empty means Text Hub decides " +
                    "from the input; a value forces that tool for the next analysis.",
            ),
        ),
        info = ToolInfo(
            summary = "Paste encoded, ciphered or encrypted text and Text Hub will say which format " +
                "it recognises, how confident it is, and what is needed to open it. Anything it can " +
                "decode reliably is decoded for you by the ordinary tool for that format.",
            requiresKey = false,
            useCases = listOf(
                "Not knowing which of the 74 tools a pasted value belongs to",
                "Unwrapping several layers (Base64 inside a URL inside an encrypted payload)",
                "Checking what a payload is before typing a password into anything",
            ),
            warnings = listOf(
                "It identifies supported Text Hub formats; it cannot decrypt arbitrary unknown " +
                    "encrypted data, and it never guesses a password or a key.",
                "Detection is evidence-based: only structural evidence is treated as high " +
                    "confidence, and a format that is only guessed is offered as a candidate.",
                "It performs no cryptography of its own - every step is the existing tool for that " +
                    "format, with that tool's own validation.",
            ),
            convention = "Report: what was detected, why, at what confidence, the chain of layers " +
                "applied, and either the result or exactly which secret or parameter is missing.",
        ),
        keywords = listOf(
            "universal", "decoder", "detect", "detection", "identify", "what is this", "auto",
            "analyze", "analyse", "unknown", "smart", "signature", "formats",
        ),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        if (input.isBlank()) return ""

        val diagnosis = UniversalDecoder.analyze(input, params)
        val comparison = params[UniversalDecoder.PARAM_HASH_CANDIDATE]
            .orEmpty()
            .takeIf { it.isNotBlank() }
            ?.let { candidate ->
                val verdict = UniversalDecoder.compareHash(input, candidate, params)
                verdict.ifBlank { null }
            }

        return when (diagnosis) {
            is Diagnosis.Decoded -> diagnosis.text
            else -> report(diagnosis, comparison)
        }
    }

    /** The human-readable report, used as the tool's output when there is no decoded text. */
    private fun report(diagnosis: Diagnosis, comparison: String?): String = buildString {
        appendLine(reportHeadline(diagnosis))
        appendLine()
        when (diagnosis) {
            is Diagnosis.Decoded -> Unit
            is Diagnosis.NeedsSecret -> {
                appendLine("Additional information required:")
                appendLine(diagnosis.candidate.secretKind?.label ?: "key")
                appendLine()
                appendLine(secretInstruction(diagnosis.candidate.secretKind))
            }
            is Diagnosis.Choose -> {
                appendLine("Possible formats")
                diagnosis.candidates.forEach { appendLine(candidateLine(it)) }
                appendLine()
                appendLine(diagnosis.note)
            }
            is Diagnosis.HashOnly -> {
                appendLine("This is a one-way hash: it cannot be decrypted.")
                diagnosis.candidates.forEach { appendLine(candidateLine(it)) }
                if (comparison != null) {
                    appendLine()
                    appendLine("Comparison with the supplied text:")
                    appendLine(comparison)
                } else {
                    appendLine()
                    appendLine(diagnosis.note)
                }
            }
            is Diagnosis.NotSupported -> {
                diagnosis.candidates.forEach { appendLine(candidateLine(it)) }
                appendLine()
                appendLine(diagnosis.note)
            }
            is Diagnosis.Failed -> {
                appendLine("Decryption failed")
                appendLine(diagnosis.message)
            }
            is Diagnosis.Unrecognized -> appendLine(diagnosis.note)
        }
        if (diagnosis.steps.isNotEmpty() && diagnosis !is Diagnosis.Decoded) {
            appendLine()
            appendLine(chain(diagnosis))
        }
    }

    private fun reportHeadline(diagnosis: Diagnosis): String = when (diagnosis) {
        is Diagnosis.Decoded -> "Detected and decoded"
        is Diagnosis.NeedsSecret -> "Detected: ${diagnosis.candidate.label}"
        is Diagnosis.Choose -> "Nothing decoded: the format is not certain"
        is Diagnosis.HashOnly -> "Detected: one-way hash"
        is Diagnosis.NotSupported -> "Recognised, but not something Text Hub decodes"
        is Diagnosis.Failed -> "Detected: ${diagnosis.candidate.label}"
        is Diagnosis.Unrecognized -> "No supported format recognised"
    }

    private fun candidateLine(candidate: Candidate): String =
        "• ${candidate.label} — ${candidate.confidence.label} confidence\n    ${candidate.reason}"

    private fun chain(diagnosis: Diagnosis): String = buildString {
        appendLine("Detected chain")
        diagnosis.steps.forEachIndexed { index, step ->
            appendLine("${index + 1}. ${step.label} — ${step.confidence.label} confidence")
            step.note?.let { appendLine("    $it") }
        }
    }

    /**
     * How to supply the missing secret, in words that fit its kind: a PEM key is pasted whole (or
     * picked from the saved key pairs), a password is typed. Never repeats any secret.
     */
    private fun secretInstruction(kind: SecretKind?): String = when (kind) {
        SecretKind.PRIVATE_KEY ->
            "Paste the complete RSA private key (the whole -----BEGIN PRIVATE KEY----- block) into " +
                "the key field, or choose one of your saved key pairs, and analyze again. Text Hub " +
                "will not guess or try keys; the authentication tag decides whether it is right."
        SecretKind.PUBLIC_KEY ->
            "Provide the complete RSA public key (the whole -----BEGIN PUBLIC KEY----- block) in " +
                "the key field and analyze again."
        else ->
            "Enter it in the Password / key field and analyze again. Text Hub will not guess it, " +
                "and the authentication tag or MAC decides whether it is right."
    }

    private fun confidenceLabel(stepConfidence: Confidence): String = stepConfidence.label

    @Suppress("unused")
    private fun secretLabel(kind: SecretKind?): String = kind?.label ?: "key"
}
