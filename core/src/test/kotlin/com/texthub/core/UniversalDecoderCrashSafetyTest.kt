package com.texthub.core

import com.texthub.core.detector.DetectionIndex
import com.texthub.core.detector.UniversalDecoder
import com.texthub.core.model.Direction
import com.texthub.core.model.Errors
import com.texthub.core.model.ProcessOutcome
import com.texthub.core.model.ToolException
import com.texthub.core.model.ToolMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The crash-safety contract of the Universal Decoder, on the exact path the app takes per keystroke.
 *
 * 1.6.1 could crash the whole app from the input box: the processing engine - the layer that is
 * supposed to convert *any* processor failure into a friendly message - caught only `Exception`, so
 * a `StackOverflowError` (the platform regex engine, on a device) or an `OutOfMemoryError` escaped
 * into the typing coroutine and killed the process, while every JVM unit test still passed. One
 * detection hint that threw also aborted detection for every other tool at once. These tests pin the
 * fixed behaviour: no input, and no single broken candidate, may take the decoder - or the app -
 * down, and nothing may be reported as a cipher-settings problem.
 */
class UniversalDecoderCrashSafetyTest {

    private val unexpectedWording = Errors.unexpectedFailure().message

    /** Everything the app does on every text change while the Universal Decoder is selected. */
    private fun appKeystrokePath(text: String, params: Map<String, String> = emptyMap()): ProcessOutcome {
        val universal = ToolRegistry.get(UniversalDecoder.TOOL_ID)
        return ProcessingEngine.run(universal, text, universal.defaultParams() + params, Direction.ENCODE)
    }

    /** Inputs the field must survive, from single characters to hostile sizes and shapes. */
    private val typedInputs = listOf(
        // the reported crash reproductions
        "a", "abc", "hello", "123",
        // empty and whitespace
        "", " ", "   ", " \n\t \r\n",
        // punctuation and symbols
        "!", "!!!", "@#\$%^&*()_+-=[]{}|;':\",./<>?`~", ".,;:!?'\"()[]{}<>@#$%^&*-_=+~`|/\\",
        // arbitrary Unicode, including astral planes and combining marks
        "🦄🌍🎯", "Ωμέγα", "日本語のテキスト", "مرحبا", "e\u0301\u0302\u0303", "\u0000\u0001\u0002",
        "😀".repeat(3000),
        // newlines and very long lines
        "line one\nline two\rline three\rdone",
        "x".repeat(100_000),
        buildString { repeat(2000) { append("a fairly long line of ordinary text\n") } },
        // malformed payload shapes
        "SGVsbG8gd29ybGQ=!", "SGVsbG8", "=", "====", "AAAA",
        "xn--", "xn--", "xn--80ak6aa92e!!!",
        "ENIGMA I", " plugged: B-C rotors: I II III",
        "U2FsdGVkX1", "eyJhbGciOiJIUzI1NiJ9", "a.b.c", ".....",
        "-----BEGIN", "-----BEGIN RSA PRIVATE KEY-----",
        // prose that must never be decoded by guessing
        "this is an ordinary sentence with words in it and nothing else",
    )

    @Test
    fun typingAnythingNeverThrowsAndNeverCrashWordings() {
        for (text in typedInputs) {
            // The engine call is what the app runs per keystroke; if it threw, the app crashed.
            val outcome = appKeystrokePath(text)
            val error = outcome.error
            if (error != null) {
                assertTrue(
                    "input ${text.take(24)} produced a stack-trace-like error: $error",
                    !error.contains("Exception"),
                )
                assertNotCipherWording("engine", text, error)
            }
            val diagnosis = UniversalDecoder.analyze(text)
            assertNotNull("analyze(${text.take(24)}) must produce a diagnosis", diagnosis)
        }
    }

    @Test
    fun typingProgressivelyNeverThrows() {
        val typed = "Hello world 123 ++/"
        for (prefixLength in 1..typed.length) {
            val outcome = appKeystrokePath(typed.take(prefixLength))
            val error = outcome.error
            if (error != null) assertNotCipherWording("typing", typed.take(prefixLength), error)
        }
    }

    @Test
    fun repeatedTypingAndDeletingNeverThrows() {
        // The reported crash showed up while the user was typing and deleting; each state of a
        // type/delete cycle must go through the app path on its own.
        var text = ""
        val append = "SGVssG8hbf#"
        repeat(3) {
            for (ch in append) {
                text += ch
                val outcome = appKeystrokePath(text)
                val error = outcome.error
                if (error != null) assertNotCipherWording("type/delete", text, error)
            }
            repeat(append.length / 2) {
                text = text.dropLast(1)
                val outcome = appKeystrokePath(text)
                val error = outcome.error
                if (error != null) assertNotCipherWording("type/delete", text, error)
            }
        }
    }

    @Test
    fun analysingWithASecretTypedNeverThrows() {
        for (text in listOf("U2FsdGVkX18StlY2G8uY4Qp9HhJZhDqfx5GjKsdfJjfHlkc=", "hello", "a", "!")) {
            val outcome = appKeystrokePath(text, mapOf("secret" to "hunter2"))
            val error = outcome.error
            if (error != null) assertNotCipherWording("secret", text, error)
            UniversalDecoder.analyze(text, mapOf("secret" to "hunter2"))
        }
    }

    @Test
    fun anUnexpectedFailureIsNeverWordedAsCipherSettings() {
        assertNotCipherWording("wording", "n/a", unexpectedWording)
        assertTrue(unexpectedWording?.contains("could not process") == true)
    }

    // ----------------------------------------------------------- the engine crash boundary

    private class ThrowingProcessor(private val thrown: Throwable) : TextProcessor {
        override val meta = ToolMeta(
            id = "throwing-test-only",
            name = "Throwing (test)",
            glyph = "T",
            category = com.texthub.core.model.ToolCategory.TRANSFORM,
            classification = com.texthub.core.model.Classification.REVERSIBLE_TRANSFORM,
            encodeLabel = "Throw",
            decodeLabel = "Throw",
        )

        override fun process(input: String, params: Map<String, String>, direction: Direction): String =
            throw thrown
    }

    @Test
    fun aRuntimeExceptionFromAToolBecomesAFriendlyError() {
        val outcome = ProcessingEngine.run(ThrowingProcessor(IllegalStateException("defect")), "text", emptyMap(), Direction.ENCODE)
        assertTrue("a failed run has no output", outcome.output.isEmpty())
        assertEquals(unexpectedWording, outcome.error)
    }

    @Test
    fun aStackOverflowErrorFromAToolDoesNotKillTheProcess() {
        // On a device this is the realistic shape of the 1.6.1 crash (recursive regex matching);
        // it is an Error, not an Exception, and used to escape the engine entirely.
        val outcome = ProcessingEngine.run(ThrowingProcessor(StackOverflowError("deep recursion")), "text", emptyMap(), Direction.ENCODE)
        assertEquals(unexpectedWording, outcome.error)
    }

    @Test
    fun anOutOfMemoryErrorFromAToolDoesNotKillTheProcess() {
        val outcome = ProcessingEngine.run(ThrowingProcessor(OutOfMemoryError("heap")), "text", emptyMap(), Direction.ENCODE)
        assertEquals(unexpectedWording, outcome.error)
    }

    @Test
    fun aToolExceptionStillKeepsItsOwnMessage() {
        val message = "Invalid Base64 input. Please check the characters and padding."
        val outcome = ProcessingEngine.run(ThrowingProcessor(ToolException(message)), "text", emptyMap(), Direction.ENCODE)
        assertEquals(message, outcome.error)
    }

    // ----------------------------------------------------------- candidate isolation

    @Test
    fun oneBrokenCandidateCostsOnlyItself() {
        val kept = "kept"
        val isolated = DetectionIndex.isolatedCandidate { kept }
        assertEquals(kept, isolated)

        assertNull(DetectionIndex.isolatedCandidate<String> { throw RuntimeException("candidate defect") })
        assertNull(DetectionIndex.isolatedCandidate<String> { throw StackOverflowError("candidate defect") })
    }

    @Test
    fun detectionSurvivesEveryInputShape() {
        for (text in typedInputs) {
            // probe() runs every registered tool's hint and every generic probe; a throw here
            // used to abort detection for all tools at once.
            val candidates = DetectionIndex.probe(text)
            assertTrue("candidates must be ranked strongest first", isRanked(candidates))
        }
    }

    private fun isRanked(candidates: List<com.texthub.core.detector.Candidate>): Boolean =
        candidates.zipWithNext().all { (a, b) -> a.confidence.rank >= b.confidence.rank }

    private fun assertNotCipherWording(where: String, input: String, error: String?) {
        val blamesCipher = listOf("cipher settings", "cipher setting").any { error?.contains(it, ignoreCase = true) == true }
        assertTrue(
            "$where(${input.take(24)}) reported a cipher-settings problem for a non-cipher failure: $error",
            !blamesCipher,
        )
    }
}
