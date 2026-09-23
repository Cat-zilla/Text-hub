package com.texthub.core

import com.texthub.core.codec.Base32Codec
import com.texthub.core.codec.Base58Codec
import com.texthub.core.codec.Base64Codec
import com.texthub.core.detector.Candidate
import com.texthub.core.detector.Confidence
import com.texthub.core.detector.DetectionIndex
import com.texthub.core.detector.Diagnosis
import com.texthub.core.detector.UniversalDecoder
import com.texthub.core.detector.registeredCandidate
import com.texthub.core.model.Direction
import com.texthub.core.model.Errors
import com.texthub.core.model.OperationKind
import com.texthub.core.model.ProcessOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Universal Decoder as the app uses it: through the registered tool, the registry and the
 * processing engine - not through the detector's internals.
 *
 * [UniversalDecoderTest] checks what the detector concludes; this file checks what the user gets.
 * The difference matters: a detector that is right while the tool it dispatches to silently fails
 * is still a broken tool, and the promise that nothing about an encoding can reach the cipher
 * settings has to hold on the path the UI actually takes.
 *
 * The coverage checks are built from [ToolRegistry] and [DetectionIndex] rather than from a list of
 * ids, so a tool that is registered later is covered here without touching this file.
 */
class UniversalDecoderDispatchTest {

    private val unexpectedWording = Errors.unexpectedFailure().message

    private fun processor(id: String) = ToolRegistry.get(id)

    /** Everything the app does when the user taps Analyze: registry lookup, defaults, engine. */
    private fun analyze(input: String, params: Map<String, String> = emptyMap()): ProcessOutcome {
        val universal = processor(UniversalDecoder.TOOL_ID)
        return ProcessingEngine.run(universal, input, universal.defaultParams() + params, Direction.ENCODE)
    }

    /** The same call the app makes for any other tool, with that tool's own default parameters. */
    private fun runRegistered(toolId: String, input: String, direction: Direction = Direction.DECODE): ProcessOutcome {
        val tool = processor(toolId)
        return ProcessingEngine.run(tool, input, tool.defaultParams(), direction)
    }

    private fun diagnosis(input: String, params: Map<String, String> = emptyMap()): Diagnosis =
        UniversalDecoder.analyze(input, params)

    private fun isRegistered(id: String): Boolean = ToolRegistry.all.any { it.meta.id == id }

    // ------------------------------------------------------ §17: the production path, end to end

    @Test fun encodingThenAnalysingThenDecodingRoundTripsThroughTheRegisteredTool() {
        val plain = "Hello TextHub"
        val encoded = Base64Codec.encode(plain.toByteArray(Charsets.UTF_8))

        // 1. Analyze: the registered tool must decode it and hand back the plain text.
        val outcome = analyze(encoded)
        assertNull("the tool must not report an error: ${outcome.error}", outcome.error)
        assertEquals(plain, outcome.output)

        // 2. The diagnosis behind that output must name Base64, and nothing else.
        val found = diagnosis(encoded)
        assertTrue("expected a decoded result, got ${found::class.simpleName}", found is Diagnosis.Decoded)
        assertEquals(1, found.steps.size)
        assertEquals("base64", found.steps.first().toolId)
        assertTrue(found.steps.first().confidence >= Confidence.LIKELY)

        // 3. And the tool the step names really is the one that decoded it.
        val direct = runRegistered("base64", encoded)
        assertNull(direct.error)
        assertEquals(plain, direct.output)
    }

    @Test fun aDetectedEncodingNeverRequiresASecretAndNeverValidatesCipherSettings() {
        val encoded = Base64Codec.encode("Hello TextHub".toByteArray(Charsets.UTF_8))

        val found = UniversalDecoder.detect(encoded).firstOrNull { it.toolId == "base64" }
        assertNotNull("Base64 must be offered as a candidate", found)
        val candidate = found!!
        assertFalse("an encoding never needs a secret", candidate.requiresSecret)
        assertFalse("an encoding never needs cipher parameters", candidate.requiresCipherParameters)
        assertFalse("an encoding is not a one-way operation", candidate.oneWay)
        assertNull("an encoding has no secret kind", candidate.secretKind)
        assertNull("an encoding has no secret parameter", candidate.secretParam)
        assertEquals(OperationKind.ENCODING, candidate.operation)

        // The consequence: there is nothing left for a cipher check to look at. The tool this
        // candidate names has no key material and no cipher parameters at all.
        val meta = ToolRegistry.metaOf("base64")
        assertTrue("base64 takes no cipher parameters", meta.cipherParameters.isEmpty())
        assertFalse("and no key material at all", meta.needsKeyMaterial)
    }

    @Test fun aDecodedEncodingNeverMentionsCipherSettingsOrPasswords() {
        val encoded = Base64Codec.encode("Hello TextHub".toByteArray(Charsets.UTF_8))
        val outcome = analyze(encoded)
        assertNull(outcome.error)
        val text = outcome.output
        assertFalse("no cipher settings in a decoded result", text.contains("cipher", ignoreCase = true))
        assertFalse("no password prompt for an encoding", text.contains("password", ignoreCase = true))
        assertEquals("Hello TextHub", text)
    }

    @Test fun anEncodingShapedInputIsNeverAskedForAPassword() {
        // The bug this round fixes, as a rule: a value that belongs to a deterministic encoding may
        // not end in "enter the key or password".
        val encodings = listOf(
            Base64Codec.encode("Hello TextHub".toByteArray()),
            "48656c6c6f2054657874487562",
            "Hello%20TextHub%21",
            Base32Codec.encode("Hello TextHub".toByteArray()),
            Base58Codec.encode("Hello TextHub".toByteArray()),
            ".... . .-.. .-.. ---",
        )
        for (input in encodings) {
            val found = diagnosis(input)
            assertFalse(
                "\"${input.take(20)}\" was answered with a request for a secret",
                found is Diagnosis.NeedsSecret,
            )
            val detected = UniversalDecoder.detect(input).firstOrNull()
            assertNotNull("nothing detected in \"${input.take(20)}\"", detected)
            assertFalse(detected!!.requiresSecret)
        }
    }

    @Test fun everyDetectedEncodingDecodesThroughItsOwnRegisteredTool() {
        val samples = linkedMapOf(
            "base64" to Base64Codec.encode("Hello TextHub".toByteArray()),
            "base64url" to Base64Codec.encode("Hello TextHub!?".toByteArray(), urlSafe = true, padding = false),
            "hex" to "48656c6c6f2054657874487562",
            "url" to "Hello%20TextHub%21",
            "base32" to Base32Codec.encode("Hello TextHub".toByteArray()),
            "base58" to Base58Codec.encode("Hello TextHub".toByteArray()),
            "morse" to ".... . .-.. .-.. --- / - . -..- - .... ..- -...",
        )
        var covered = 0
        for ((toolId, sample) in samples) {
            if (!isRegistered(toolId)) continue
            covered++
            val found = UniversalDecoder.detect(sample).firstOrNull { it.toolId == toolId }
            assertNotNull("$toolId was not detected in its own encoding", found)
            val candidate = found!!
            assertFalse("$toolId must not ask for a secret", candidate.requiresSecret)
            assertFalse("$toolId must not need cipher parameters", candidate.requiresCipherParameters)
            assertNull("$toolId must not carry a secret kind", candidate.secretKind)
            assertTrue("$toolId must be runnable", candidate.isRunnable)
            val outcome = runRegistered(toolId, sample)
            assertNull("$toolId failed on its own output: ${outcome.error}", outcome.error)
            assertTrue(
                "$toolId produced something unrelated: ${outcome.output.take(40)}",
                outcome.output.contains("TextHub", ignoreCase = true) ||
                    outcome.output.contains("Text Hub", ignoreCase = true),
            )
        }
        assertTrue("no sample was covered - the registry ids changed", covered >= 5)
    }

    @Test fun anUnknownStringIsReportedAsUnknownRatherThanGuessed() {
        val input = "zw#q~ flurble \u00bf?"
        val outcome = analyze(input)
        assertNull("saying \"I do not know\" is not an error", outcome.error)
        val found = diagnosis(input)
        assertTrue(
            "unrecognisable input must not be decoded: ${found::class.simpleName}",
            found is Diagnosis.Unrecognized || found is Diagnosis.Choose,
        )
        if (found is Diagnosis.Choose) {
            assertTrue("a guess must never be the only candidate", found.candidates.size > 1)
        }
    }

    @Test fun aPasswordTypedIntoTheDecoderChangesNothingForAnEncoding() {
        val encoded = Base64Codec.encode("Hello TextHub".toByteArray())
        for (params in listOf(
            mapOf(UniversalDecoder.PARAM_SECRET to "hunter2"),
            mapOf(UniversalDecoder.PARAM_SECRET to "hunter2", UniversalDecoder.PARAM_MAX_DEPTH to "8"),
        )) {
            val outcome = analyze(encoded, params)
            assertNull(outcome.error)
            assertEquals("the password field must not change a decoding", "Hello TextHub", outcome.output)
        }
    }

    // ------------------------------------------- the message this whole round started from (§1)

    @Test fun noPathThroughTheDecoderEverReportsCipherSettingsAsTheProblem() {
        val spoken = mutableListOf<String>()
        for (input in inputCorpus()) {
            for (params in listOf(
                emptyMap(),
                mapOf(UniversalDecoder.PARAM_SECRET to "not-the-password"),
            )) {
                analyze(input, params).error?.let { spoken += it }
                spoken += messagesOf(diagnosis(input, params))
            }
        }
        val cipherTalk = spoken.filter { it.contains("cipher", ignoreCase = true) }
        assertTrue("a decoding path reported a cipher-settings problem: $cipherTalk", cipherTalk.isEmpty())
    }

    @Test fun theDecoderItselfNeverFailsWithAnUnexpectedError() {
        for (input in inputCorpus()) {
            val outcome = analyze(input)
            assertNull("unexpected failure for \"${input.take(24)}\": ${outcome.error}", outcome.error)
        }
    }

    @Test fun aManualOverrideDispatchesToThatToolAndNeverBlamesTheCipherSettings() {
        // §15: the override goes straight to the tool the user picked. Whatever it answers must be
        // about that tool and this input - never about cipher settings that no one selected, and
        // never an unexpected failure.
        val base64 = Base64Codec.encode("Hello TextHub".toByteArray())
        val forced = mutableListOf<String>()
        for (meta in ToolRegistry.all.map { it.meta }) {
            for (secret in listOf("", "not-the-password")) {
                val params = linkedMapOf(UniversalDecoder.PARAM_PREFER to meta.id)
                if (secret.isNotEmpty()) params[UniversalDecoder.PARAM_SECRET] = secret
                analyze(base64, params).error?.let { forced += "${meta.id}: $it" }
                val found = diagnosis(base64, params)
                if (found is Diagnosis.Failed) forced += "${meta.id}: ${found.message}"
                if (found is Diagnosis.NeedsSecret) {
                    assertEquals("the override must name the tool it forced", meta.id, found.candidate.toolId)
                }
            }
        }
        assertTrue(
            "forcing a tool reported the wrong problem: " + forced.filter { it.contains("cipher", true) },
            forced.none { it.contains("cipher", ignoreCase = true) },
        )
        assertTrue(
            "forcing a tool produced an unexplained failure: " + forced.filter { it.contains("could not process") },
            forced.none { it.contains("could not process") },
        )
    }

    @Test fun everyRegisteredToolTakesAwkwardParametersWithoutAnUnexplainedFailure() {
        // Parameter torture for the families where a wrong parameter is a *user* mistake and so must
        // be answered with a sentence that explains it: the encodings, the transformations and the
        // classical ciphers. (The encryption tools are covered by their own suites, and their keys
        // are thousands of rounds of PBKDF2, which this test does not need to pay for.)
        val awkward = listOf("", "a", "0", "\u00e9\u00fc")
        val offenders = mutableListOf<String>()
        for (processor in ToolRegistry.all) {
            val meta = processor.meta
            if (meta.id == UniversalDecoder.TOOL_ID) continue
            if (meta.operationKind != OperationKind.ENCODING &&
                meta.operationKind != OperationKind.TRANSFORM &&
                meta.operationKind != OperationKind.CLASSICAL_CIPHER
            ) {
                continue
            }
            for (key in meta.params.map { it.key }) {
                for (value in awkward) {
                    val params = processor.defaultParams() + (key to value)
                    for (direction in listOf(Direction.ENCODE, Direction.DECODE)) {
                        val outcome = ProcessingEngine.run(processor, "Text Hub", params, direction)
                        val error = outcome.error ?: continue
                        if (error == unexpectedWording || error.contains("cipher settings")) {
                            offenders += "${meta.id}.$key=\"$value\" $direction: $error"
                        }
                    }
                }
            }
        }
        assertTrue("these settings produced an unexplained failure:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    // ------------------------------------------------------------------ §18: the registry matrix

    @Test fun everyRegisteredToolIsAccountedForByTheDetector() {
        val accounted = DetectionIndex.accountedFor()
        val registered = ToolRegistry.all.map { it.meta.id }
        assertEquals("every registered tool must be accounted for", registered.toSet(), accounted.keys)
        val allowed = setOf("hint", "generic", "symmetric", "keyed", "one-way", "other", "dispatcher")
        assertTrue("unexpected classification: ${accounted.values.toSet()}", accounted.values.all { it in allowed })
        assertEquals("exactly one dispatcher", 1, accounted.values.count { it == "dispatcher" })

        // The deterministic tools are the ones with the most to lose from being left out of
        // detection, so each one is either probed with its own hints, probed by being run...:
        for (meta in ToolRegistry.all.map { it.meta }) {
            if (!meta.operationKind.deterministic || meta.id == UniversalDecoder.TOOL_ID) continue
            if (accounted[meta.id] == "hint" || accounted[meta.id] == "generic") continue
            // ...or it is one of the transformations that read readable text either way, where no
            // input can be evidence. That has to be a property of the tool, not an exemption list:
            // a new deterministic tool cannot be left out of detection by accident.
            assertEquals(
                "${meta.id} is a deterministic tool that detection would ignore",
                "symmetric",
                accounted[meta.id],
            )
            assertTrue(
                "${meta.id} claims to be symmetric but is not marked as one",
                meta.symmetric,
            )
        }
    }

    @Test fun theMatrixCoversWhatTheDetectorClaimsToCover() {
        val accounted = DetectionIndex.accountedFor().values
        assertTrue("too few tools declare detection hints", accounted.count { it == "hint" } >= 25)
        assertTrue("key material would be invisible to detection", accounted.count { it == "keyed" } >= 6)
        assertTrue("one-way tools would be invisible to detection", accounted.count { it == "one-way" } >= 3)
        // Digests are recognised through the Hash tool's own hint, at both ends of the range.
        for (input in listOf(
            "5d41402abc4b2a76b9719d911017c592",
            "da39a3ee5e6b4b0d3255bfef95601890afd80709",
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e",
        )) {
            assertTrue(
                "a ${input.length}-character digest is not offered as a candidate",
                UniversalDecoder.detect(input).any { it.toolId == "hash" },
            )
        }
    }

    @Test fun aCandidateCanOnlyNameARegisteredTool() {
        val candidate = registeredCandidate(
            toolId = "base64",
            label = "Base64",
            confidence = Confidence.LIKELY,
            reason = "alphabet",
        )
        assertEquals(OperationKind.ENCODING, candidate.operation)
        assertFalse(candidate.requiresSecret)
        assertFalse(candidate.requiresCipherParameters)
        assertTrue(candidate.actionable)
        assertEquals(ToolRegistry.metaOf("base64").name, candidate.toolName)
        assertEquals(Direction.DECODE, candidate.direction)

        // A candidate naming a tool that is not registered would die in the user's hands when they
        // tapped it, so it cannot be built in the first place - in one place, for every caller.
        var threw = false
        try {
            registeredCandidate("not-a-tool", "Nope", Confidence.POSSIBLE, "nothing")
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue("an unregistered tool id must not be able to become a candidate", threw)
    }

    @Test fun everyCandidateTheDetectorOffersNamesARegisteredTool() {
        for (input in inputCorpus()) {
            for (candidate in candidatesOf(diagnosis(input))) {
                assertTrue("${candidate.toolId} is not a registered tool", isRegistered(candidate.toolId))
                // Derived from the registry, so an encoding cannot claim to need a secret even if a
                // detection rule wanted it to.
                if (candidate.operation == OperationKind.ENCODING) {
                    assertFalse(candidate.requiresSecret)
                    assertFalse(candidate.requiresCipherParameters)
                    assertFalse(candidate.oneWay)
                    assertNull(candidate.secretKind)
                }
                // Key material is only ever reported for a tool that really takes some.
                if (candidate.requiresSecret) {
                    assertTrue(
                        "${candidate.toolId} asks for a secret but has none",
                        ToolRegistry.metaOf(candidate.toolId).needsKeyMaterial,
                    )
                }
            }
        }
    }

    @Test fun everyToolThatTakesASecretDeclaresItAsAnActualParameter() {
        for (meta in ToolRegistry.all.map { it.meta }) {
            if (!meta.needsKeyMaterial) continue
            val secret = meta.secretSpec
            assertNotNull("${meta.id} needs key material but names no secret parameter", secret)
            assertTrue("${meta.id}.${secret!!.key} must be sensitive", secret.sensitive)
            assertEquals(
                "${meta.id}.${secret.key} must be declared once",
                1,
                meta.params.count { it.key == secret.key },
            )
        }
    }

    @Test fun theManualOverrideIsNeverRememberedForTheNextSession() {
        // §10/§15: a forced tool applies to the analysis in front of the user. Remembering it would
        // silently send a later paste - on this run or the next launch - to a tool that was picked
        // for something else, which is exactly the trap the override must never become.
        val meta = ToolRegistry.metaOf(UniversalDecoder.TOOL_ID)
        val prefer = meta.params.firstOrNull { it.key == UniversalDecoder.PARAM_PREFER }
        assertNotNull("the manual override must be a parameter of the tool", prefer)
        assertTrue("the manual override must be session-only", prefer!!.sessionOnly)
        assertEquals("", prefer.defaultValue)

        val chosen = mapOf(
            UniversalDecoder.PARAM_PREFER to "base64",
            UniversalDecoder.PARAM_MAX_DEPTH to "4",
            UniversalDecoder.PARAM_SECRET to "hunter2",
            UniversalDecoder.PARAM_HASH_CANDIDATE to "",
        )
        val remembered = meta.rememberedParams(chosen)
        assertFalse("a forced tool must not be written to disk", remembered.containsKey(UniversalDecoder.PARAM_PREFER))
        assertFalse("a secret must not be written to disk", remembered.containsKey(UniversalDecoder.PARAM_SECRET))
        assertFalse("an empty value is not worth remembering", remembered.containsKey(UniversalDecoder.PARAM_HASH_CANDIDATE))
        assertEquals("4", remembered[UniversalDecoder.PARAM_MAX_DEPTH])
    }

    @Test fun anUnknownManualOverrideIsIgnoredInsteadOfBreakingTheAnalysis() {
        val encoded = Base64Codec.encode("Hello TextHub".toByteArray())
        val outcome = analyze(encoded, mapOf(UniversalDecoder.PARAM_PREFER to "not-a-tool"))
        assertNull("a stale override must not turn into an error: ${outcome.error}", outcome.error)
        assertEquals("Hello TextHub", outcome.output)
    }

    @Test fun theDetectorsOwnToolIsNeverOfferedAsACandidate() {
        // A dispatcher that could dispatch to itself would recurse; the registry entry that drives
        // detection is skipped, and the tests above rely on that.
        for (input in inputCorpus()) {
            for (candidate in candidatesOf(diagnosis(input))) {
                assertNotEquals(
                    "the dispatcher must not be a candidate for anything",
                    UniversalDecoder.TOOL_ID,
                    candidate.toolId,
                )
            }
        }
    }

    // ------------------------------------------------------------------------------ helpers

    /** Inputs that stand for every family of tool: encodings, ciphers, digests, payloads, junk. */
    private fun inputCorpus(): List<String> = listOf(
        "48656c6c6f2054657874487562",
        "SGVsbG8gVGV4dEh1Yg==",
        "Hello%20TextHub%21",
        "Hello TextHub",
        "3132333435",
        "1010101010",
        ".... . .-.. .-.. ---",
        "\\u0048\\u0065\\u006c\\u006c\\u006f",
        "Hello&nbsp;TextHub",
        "Hello=20TextHub",
        "xn--bcher-kva.example",
        "{\"tool\":\"TextHub\"}",
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ0IjoiVGV4dEh1YiJ9.c2ln",
        "5d41402abc4b2a76b9719d911017c592",
        "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
        "MCMXCV",
        "aGk=",
        "AAECAwQFBgcICQ==",
        "!!",
        "",
    )

    private fun candidatesOf(diagnosis: Diagnosis): List<Candidate> = when (diagnosis) {
        is Diagnosis.Choose -> diagnosis.candidates
        is Diagnosis.HashOnly -> diagnosis.candidates
        is Diagnosis.NotSupported -> diagnosis.candidates
        is Diagnosis.Unrecognized -> diagnosis.candidates
        is Diagnosis.NeedsSecret -> listOf(diagnosis.candidate)
        is Diagnosis.Failed -> listOf(diagnosis.candidate)
        is Diagnosis.Decoded -> emptyList()
    }

    private fun messagesOf(diagnosis: Diagnosis): List<String> = when (diagnosis) {
        is Diagnosis.Failed -> listOf(diagnosis.message)
        is Diagnosis.Choose -> listOf(diagnosis.note)
        is Diagnosis.HashOnly -> listOf(diagnosis.note)
        is Diagnosis.NotSupported -> listOf(diagnosis.note)
        is Diagnosis.Unrecognized -> listOf(diagnosis.note)
        is Diagnosis.NeedsSecret -> emptyList()
        is Diagnosis.Decoded -> emptyList()
    }
}
