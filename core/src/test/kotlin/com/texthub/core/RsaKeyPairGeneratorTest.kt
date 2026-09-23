package com.texthub.core

import com.texthub.core.crypto.RsaHybrid
import com.texthub.core.crypto.RsaKeyGen
import com.texthub.core.crypto.RsaPem
import com.texthub.core.model.Direction
import com.texthub.core.model.Errors
import com.texthub.core.model.ProcessOutcome
import com.texthub.core.model.ToolException
import com.texthub.core.processors.RsaKeyGenProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.MessageDigest
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey

/**
 * The RSA Key Pair Generator's contract, on both sides of the 1.6.4 fix.
 *
 * Before this round the tool minted a key pair as a *side effect of being opened*: it was marked
 * as needing no input, so selecting it (and every later state change) ran the generator through
 * the app's automatic processing path, and both halves sat in one generic output box. The
 * generator now declares `explicitActionOnly`, the app refuses every automatic path for it, and
 * the pair is presented as separate Public key / Private key / Key information sections built by
 * parsing the processor's own output. These tests pin all of that: the explicit-action rule, the
 * cryptographic correctness of the pair, the section parse, and the copy/leak properties.
 *
 * Assertions are structural on purpose: no test failure message ever carries key material.
 */
class RsaKeyPairGeneratorTest {

    private fun processor() = ToolRegistry.get(RsaKeyGenProcessor.TOOL_ID)

    private fun generate(bits: Int = 2048) = RsaKeyGen.generatePair(bits)

    /** The call the app's explicit action makes, through the ordinary processing engine. */
    private fun generateThroughEngine(params: Map<String, String> = emptyMap()): ProcessOutcome =
        ProcessingEngine.run(processor(), "", processor().defaultParams() + params, Direction.ENCODE)

    // ------------------------------------------------------------- the explicit-action rule

    @Test
    fun theGeneratorMayOnlyRunThroughItsOwnAction() {
        val meta = processor().meta
        assertTrue(
            "the key generator must declare explicitActionOnly after the 1.6.4 fix",
            meta.explicitActionOnly,
        )
        assertFalse("the app must never run it automatically", meta.shouldRunAutomatically)
        assertEquals(RsaKeyGenProcessor.TOOL_ID, meta.id)
    }

    @Test
    fun everyOtherToolKeepsItsAutomaticBehaviour() {
        val exceptions = ToolRegistry.all.filter { !it.meta.shouldRunAutomatically }
        assertEquals(
            "the key pair generator is the only explicit-action tool",
            listOf(RsaKeyGenProcessor.TOOL_ID),
            exceptions.map { it.meta.id },
        )
    }

    @Test
    fun theToolStillNeedsNoInput() {
        // "needs no input" (the empty input box is fine) is a different property from "runs by
        // itself"; the 1.6.4 fix changed the second, not the first.
        assertTrue(processor().meta.inputOptional)
    }

    // ----------------------------------------------------------- cryptographic correctness

    @Test
    fun aGeneratedPairIsStructurallyValidPem() {
        val pair = generate()
        // Both parsers throw unless the block is complete, well-formed and of the declared kind.
        val public = RsaPem.publicKey(pair.publicPem)
        val private = RsaPem.privateKey(pair.privatePem)
        assertTrue(public is RSAPublicKey)
        assertTrue(private is RSAPrivateCrtKey)
    }

    @Test
    fun theHalvesAreAMatchingPair() {
        val pair = generate()
        val public = RsaPem.publicKey(pair.publicPem) as RSAPublicKey
        val private = RsaPem.privateKey(pair.privatePem) as RSAPrivateCrtKey
        assertEquals("the moduli of the two halves must be identical", public.modulus, private.modulus)
        assertEquals("the public exponent must match the CRT parameters", public.publicExponent, private.publicExponent)
    }

    @Test
    fun theReportedKeySizeMatchesTheGeneratedKey() {
        for (bits in intArrayOf(2048, 3072)) {
            val pair = generate(bits)
            assertEquals(bits, pair.bits)
            val modulusBits = (RsaPem.publicKey(pair.publicPem) as RSAPublicKey).modulus.bitLength()
            assertTrue(
                "the generated modulus must be at least $bits bits",
                modulusBits >= bits,
            )
        }
    }

    @Test
    fun theFingerprintIsDeterministicAndCoversOnlyThePublicKey() {
        val pair = generate()
        val public = RsaPem.publicKey(pair.publicPem)
        // Deterministic for the same public key.
        assertEquals(pair.fingerprint, RsaKeyGen.fingerprint(public))
        // And exactly the SHA-256 of the public key's DER encoding - no private material involved.
        val expected = MessageDigest.getInstance("SHA-256")
            .digest(public.encoded)
            .joinToString(":") { "%02X".format(it.toInt() and 0xFF) }
        assertEquals(expected, pair.fingerprint)
    }

    @Test
    fun anExplicitSecondGenerationCreatesANewPair() {
        val first = generate()
        val second = generate()
        assertNotEquals("a new generation must mint a new pair", first.fingerprint, second.fingerprint)
        assertNotEquals(first.publicPem, second.publicPem)
        // Both remain complete and usable.
        assertNotNull(RsaPem.publicKey(second.publicPem))
    }

    @Test
    fun repeatedGenerationIsSafe() {
        repeat(3) { generate() }
    }

    @Test
    fun thePairStillWorksWithTheRsaEncryptionTool() {
        val pair = generate()
        val text = RsaKeyGen.format(pair)
        val plaintext = "round trip \uD83D\uDD10"
        val payload = RsaHybrid.encrypt(plaintext, text)
        assertEquals(plaintext, RsaHybrid.decrypt(payload, text))
    }

    // ---------------------------------------------------------- the sections and copy halves

    @Test
    fun theProcessorOutputParsesIntoTheThreeSections() {
        val outcome = generateThroughEngine(mapOf("size" to "2048"))
        assertNull("generation through the engine must not fail: ${outcome.error}", outcome.error)
        val keys = RsaKeyGen.parse(outcome.output)
        assertNotNull("the app builds its sections from this parse", keys)
        keys!!
        assertEquals(2048, keys.bits)
        assertTrue(keys.fingerprint.contains(':'))
        // PEM format is exactly what the metadata promises (X.509 public, PKCS#8 private).
        assertTrue(keys.publicPem.startsWith(RsaPem.PUBLIC_BEGIN))
        assertTrue(keys.publicPem.endsWith(RsaPem.PUBLIC_END))
        assertTrue(keys.privatePem.startsWith(RsaPem.PRIVATE_BEGIN))
        assertTrue(keys.privatePem.endsWith(RsaPem.PRIVATE_END))
    }

    @Test
    fun eachCopyCarriesOnlyItsOwnHalf() {
        val pair = generate()
        // The property the two Copy actions rely on: copying one section can never leak the other.
        assertFalse(pair.publicPem.contains(RsaPem.PRIVATE_BEGIN))
        assertFalse(pair.publicPem.contains(RsaPem.PRIVATE_END))
        assertFalse(pair.privatePem.contains(RsaPem.PUBLIC_BEGIN))
        assertFalse(pair.privatePem.contains(RsaPem.PUBLIC_END))
        // The summary (bits, fingerprint, advice) is in neither copy.
        assertFalse(pair.publicPem.contains("fingerprint"))
        assertFalse(pair.privatePem.contains("fingerprint"))
    }

    @Test
    fun theFormatAndParseRoundTripIsLossless() {
        val pair = generate()
        assertEquals(pair, RsaKeyGen.parse(RsaKeyGen.format(pair)))
    }

    @Test
    fun theParserRefusesAnythingThatIsNotGeneratorOutput() {
        assertNull("empty input", RsaKeyGen.parse(""))
        assertNull("ordinary text", RsaKeyGen.parse("hello"))
        assertNull("malformed Base64", RsaKeyGen.parse("SGVsbG8=!"))
        assertNull("a public key alone", RsaKeyGen.parse(generate().publicPem))
        assertNull("a private key alone", RsaKeyGen.parse(generate().privatePem))
        assertNull("an RSA payload of the encryption tool", RsaKeyGen.parse(RsaHybrid.encrypt("x", RsaKeyGen.format(generate()))))
        assertNull("a truncated generator output", RsaKeyGen.parse(generate().let { it.publicPem + "\n\n" + it.privatePem }))
    }

    // ------------------------------------------------------------------ failures and safety

    @Test
    fun anUnsupportedSizeIsARecoverableToolError() {
        try {
            RsaKeyGen.generatePair(1024)
            fail("an unsupported size must be refused")
        } catch (expected: ToolException) {
            assertFalse(expected.message.isNullOrBlank())
        }
        // And through the engine it arrives as an ordinary error result, never a crash.
        val outcome = generateThroughEngine(mapOf("size" to "1024"))
        assertNotNull(outcome.error)
        assertEquals(Errors.rsaSize().message, outcome.error)
    }

    @Test
    fun theEnginePathStillConvertsUnexpectedFailuresSafely() {
        // The 1.6.2 boundary applies to this tool like every other: a Throwable becomes the
        // friendly message, cancellation passes through.
        val outcome = ProcessingEngine.run(
            object : com.texthub.core.TextProcessor {
                override val meta = processor().meta.copy(id = "rsakeygen-test-double")
                override fun process(input: String, params: Map<String, String>, direction: Direction): String =
                    throw StackOverflowError("deep recursion")
            },
            "",
            emptyMap(),
            Direction.ENCODE,
        )
        assertEquals(Errors.unexpectedFailure().message, outcome.error)
    }

    @Test
    fun errorMessagesNeverCarryKeyMaterial() {
        // The failure wordings may *name* the PEM headers (that is guidance, not material), but
        // none of them may carry a key body: a long Base64 run is what actual key material looks
        // like, and an error that echoed the pasted key would leak it into banners and reports.
        val keyBody = Regex("[A-Za-z0-9+/]{60,}")
        for (error in listOf(
            Errors.rsaSize(),
            Errors.rsaKey(),
            Errors.rsaNeedPublic(),
            Errors.rsaNeedPrivate(),
            Errors.rsaFormat(),
            Errors.rsaDecrypt(),
        )) {
            val text = error.message!!
            assertFalse(
                "an error message must not contain a key body",
                keyBody.containsMatchIn(text),
            )
        }
    }
}
