package com.texthub.core

import com.texthub.core.crypto.RsaHybrid
import com.texthub.core.crypto.RsaKeyGen
import com.texthub.core.crypto.RsaPem
import com.texthub.core.detector.Diagnosis
import com.texthub.core.detector.SecretKind
import com.texthub.core.detector.UniversalDecoder
import com.texthub.core.detector.secretKind
import com.texthub.core.detector.secretKindOf
import com.texthub.core.keys.RsaKeyCollection
import com.texthub.core.keys.VaultCipher
import com.texthub.core.keys.VaultStorage
import com.texthub.core.model.Direction
import com.texthub.core.model.ParamKind
import com.texthub.core.model.validateParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.6.6 - the Universal Decoder and complete RSA key material.
 *
 * The reported problem: an RSA payload is detected, the decoder asks for the private key, but the
 * key field could not take a complete PEM. These tests pin the whole core path - the same map the
 * app's state holds, through the registered Universal Decoder tool, the processing engine and the
 * existing RSA processor - so that a complete, multi-line, several-kilobyte PEM arrives unchanged
 * and opens the payload. They also pin what must *not* change: passwords, AES/hex/Base64 keys,
 * detection before any key exists, and the fact that the decoder never touches the saved-key
 * collection.
 *
 * No assertion message carries key material.
 */
class UniversalDecoderRsaKeyTest {

    private val plaintext = "Universal Decoder + complete RSA key"

    private val universal = ToolRegistry.get(UniversalDecoder.TOOL_ID)
    private val rsa = ToolRegistry.get("rsa")

    private fun pair(bits: Int = 2048): RsaKeyGen.Generated = RsaKeyGen.generatePair(bits)

    private fun ciphertextFor(keys: RsaKeyGen.Generated): String = RsaHybrid.encrypt(plaintext, keys.publicPem)

    /** Exactly what the app does: the tool's defaults plus the typed values, through the engine. */
    private fun runUniversal(input: String, secret: String): com.texthub.core.model.ProcessOutcome =
        ProcessingEngine.run(
            universal,
            input,
            universal.defaultParams() + (UniversalDecoder.PARAM_SECRET to secret),
            Direction.ENCODE,
        )

    private fun analyze(input: String, secret: String = ""): Diagnosis =
        UniversalDecoder.analyze(input, universal.defaultParams() + (UniversalDecoder.PARAM_SECRET to secret))

    // ------------------------------------------------------------- 1, 2, 3, 5, 6: the key survives

    @Test fun aCompleteMultiLinePrivatePemOpensAnRsaPayloadThroughTheDecoder() {
        val keys = pair()
        val payload = ciphertextFor(keys)
        assertTrue("the generated PEM is multi-line", keys.privatePem.lines().size > 10)

        val diagnosis = analyze(payload, keys.privatePem)
        assertTrue("expected Decoded, got ${diagnosis::class.simpleName}", diagnosis is Diagnosis.Decoded)
        assertEquals(plaintext, (diagnosis as Diagnosis.Decoded).text)
        assertEquals(listOf("rsa"), diagnosis.steps.map { it.toolId })

        // And through the registered tool + engine, which is the app's actual path.
        val outcome = runUniversal(payload, keys.privatePem)
        assertNull(outcome.error)
        assertEquals(plaintext, outcome.output)
    }

    @Test fun a4096BitPrivatePemIsNotTruncatedAnywhereOnTheWay() {
        val keys = pair(4096)
        val payload = ciphertextFor(keys)
        assertTrue("a 4096-bit PKCS#8 PEM is several kilobytes", keys.privatePem.length > 3000)
        // The parameter map is the state the app holds: a plain String, no cap of any kind.
        val params = universal.defaultParams() + (UniversalDecoder.PARAM_SECRET to keys.privatePem)
        assertEquals(keys.privatePem.length, params.getValue(UniversalDecoder.PARAM_SECRET).length)
        assertTrue(universal.meta.validateParams(params).isEmpty())
        assertEquals(plaintext, ProcessingEngine.run(universal, payload, params, Direction.ENCODE).output)
    }

    @Test fun theKeyReachesTheRsaProcessorUnchanged() {
        val keys = pair()
        val payload = ciphertextFor(keys)
        // Windows line endings and surrounding blank lines are what a real paste often carries;
        // the decoder must hand exactly that to the RSA tool, and the RSA tool must accept it.
        val pasted = "\n" + keys.privatePem.replace("\n", "\r\n") + "\r\n\n"
        val direct = ProcessingEngine.run(rsa, payload, rsa.defaultParams() + ("key" to pasted), Direction.DECODE)
        val viaDecoder = runUniversal(payload, pasted)
        assertNull(direct.error)
        assertEquals(direct.output, viaDecoder.output)
        assertEquals(plaintext, viaDecoder.output)
    }

    @Test fun aPublicPemIsAcceptedAsKeyMaterialAndAnsweredHonestly() {
        // The decoder only decrypts, so a public key cannot open anything - but it must be taken
        // as a complete key, validated by the existing parser, and refused with the RSA tool's
        // own recoverable sentence (never truncated, never a generic settings error).
        val keys = pair()
        val payload = ciphertextFor(keys)
        assertNotNull(RsaPem.publicKey(keys.publicPem))
        val diagnosis = analyze(payload, keys.publicPem)
        assertTrue(diagnosis is Diagnosis.Failed)
        val message = (diagnosis as Diagnosis.Failed).message
        assertEquals(com.texthub.core.model.Errors.rsaNeedPrivate().message, message)
        assertFalse(message.contains("cipher settings"))
    }

    @Test fun everyRsaFormatTheRsaToolAcceptsIsAcceptedByTheDecoder() {
        // No second parser: whatever RsaPem accepts as a private key opens through the decoder.
        val keys = pair()
        val payload = ciphertextFor(keys)
        val variants = listOf(
            keys.privatePem,
            keys.privatePem.replace("\n", "\r\n"),
            keys.privatePem.replace("\n", " "),                  // IME that turned newlines into spaces
            keys.privatePem.replace("\n", ""),                   // single-line paste
            "Some note first\n" + keys.privatePem + "\ntrailing", // surrounded by other text
            keys.publicPem + "\n" + keys.privatePem,             // both halves, as a generator prints them
        )
        variants.forEachIndexed { index, variant ->
            assertNotNull("variant $index parses with the existing parser", RsaPem.privateKey(variant))
            assertEquals("variant $index opens through the decoder", plaintext, runUniversal(payload, variant).output)
        }
    }

    // ---------------------------------------------------------------- 7: invalid keys are recoverable

    @Test fun anInvalidOrMismatchedRsaKeyIsARecoverableFailureWithoutKeyMaterial() {
        val keys = pair()
        val other = pair()
        val payload = ciphertextFor(keys)
        val broken = keys.privatePem.replaceRange(200, 260, "x".repeat(60))
        val cases = mapOf(
            "wrong key" to other.privatePem,
            "malformed body" to broken,
            "incomplete" to keys.privatePem.take(keys.privatePem.length / 2),
            "PKCS#1 header" to "-----BEGIN RSA PRIVATE KEY-----\nMIIE\n-----END RSA PRIVATE KEY-----",
            "not a key" to "hello",
        )
        cases.forEach { (name, secret) ->
            val diagnosis = analyze(payload, secret)
            assertTrue("$name should fail recoverably, got ${diagnosis::class.simpleName}", diagnosis is Diagnosis.Failed)
            val message = (diagnosis as Diagnosis.Failed).message
            assertFalse("$name: no cipher-settings wording", message.contains("cipher settings", ignoreCase = true))
            assertFalse("$name: no stack trace", message.contains("Exception"))
            // A message never carries the key or its body lines.
            // (The BEGIN/END header lines are not key material; the RSA tool names them on purpose.)
            secret.lines().filter { it.length > 20 && !it.startsWith("-----") }.forEach { line ->
                assertFalse("$name: message must not echo the key", message.contains(line))
            }
            // Through the registered tool the failure is the report text (the 1.6.2 recoverable
            // boundary: never an exception, never an engine-level error), and it carries the RSA
            // tool's own sentence.
            val outcome = runUniversal(payload, secret)
            assertNull("$name: no engine error", outcome.error)
            assertTrue("$name: the report explains the failure", outcome.output.contains(message))
        }
    }

    // ------------------------------------------------ 8, 9, 10, 11: other secrets are untouched

    @Test fun passwordsAndSymmetricKeysStillWorkExactlyAsBefore() {
        val aes = ToolRegistry.get("aes")
        val password = "correct horse battery staple"
        val aesPayload = aes.process(plaintext, aes.defaultParams() + ("password" to password), Direction.ENCODE)
        assertEquals(plaintext, (analyze(aesPayload, password) as Diagnosis.Decoded).text)
        val asked = analyze(aesPayload)
        assertEquals(SecretKind.PASSWORD, (asked as Diagnosis.NeedsSecret).candidate.secretKind)
        assertFalse(asked.candidate.secretKind!!.isRsaKey)

        val raw = ToolRegistry.get("aesrawkey")
        val keyHex = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"
        val rawPayload = raw.process(plaintext, raw.defaultParams() + ("key" to keyHex), Direction.ENCODE)
        assertEquals(plaintext, (analyze(rawPayload, keyHex) as Diagnosis.Decoded).text)
        val keyB64 = java.util.Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
        val rawPayloadB64 = raw.process(plaintext, raw.defaultParams() + ("key" to keyB64), Direction.ENCODE)
        assertEquals(plaintext, (analyze(rawPayloadB64, keyB64) as Diagnosis.Decoded).text)
        assertEquals(SecretKind.KEY, (analyze(rawPayload) as Diagnosis.NeedsSecret).candidate.secretKind)
    }

    @Test fun theSecretParameterKeepsItsPasswordKindAndNoValidatorParsesOnEveryKeystroke() {
        // The fix is presentation and wording, not a new parameter: the field stays the one
        // sensitive PASSWORD parameter (never persisted), with no length cap and no validator -
        // so typing or pasting a large PEM never runs the RSA parser per keystroke; parsing
        // happens once, in the debounced analysis.
        val spec = universal.meta.params.first { it.key == UniversalDecoder.PARAM_SECRET }
        assertEquals(ParamKind.PASSWORD, spec.kind)
        assertTrue(spec.sensitive)
        assertNull(spec.validator)
        assertTrue(universal.meta.rememberedParams(mapOf(UniversalDecoder.PARAM_SECRET to "x")).isEmpty())
        val huge = "-----BEGIN PRIVATE KEY-----\n" + "A".repeat(200_000) + "\n-----END PRIVATE KEY-----"
        val start = System.nanoTime()
        repeat(200) { universal.meta.validateParams(mapOf(UniversalDecoder.PARAM_SECRET to huge)) }
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("200 validations of a 200 KB key took ${ms}ms", ms < 2000)
    }

    // ------------------------------------------------------ 12: detection is independent of the key

    @Test fun rsaIsDetectedAndTheRequiredKeyIsNamedBeforeAnyKeyExists() {
        val payload = ciphertextFor(pair())
        val diagnosis = analyze(payload)
        assertTrue(diagnosis is Diagnosis.NeedsSecret)
        val candidate = (diagnosis as Diagnosis.NeedsSecret).candidate
        assertEquals("rsa", candidate.toolId)
        assertEquals(SecretKind.PRIVATE_KEY, candidate.secretKind)
        assertEquals(SecretKind.PRIVATE_KEY, diagnosis.secretKind)
        assertTrue(diagnosis.secretKind!!.isRsaKey)
        // The tool's own report says which key, in words, and how to supply it - no key content.
        val report = runUniversal(payload, "").output
        assertTrue(report.contains("RSA private key"))
        assertTrue(report.contains("saved key pairs"))
        // The kind follows the direction the operation is driven in.
        assertEquals(SecretKind.PRIVATE_KEY, secretKindOf(rsa.meta, Direction.DECODE))
        assertEquals(SecretKind.PUBLIC_KEY, secretKindOf(rsa.meta, Direction.ENCODE))
    }

    @Test fun theDiagnosisKeepsSayingRsaKeyAfterAFailedAttemptAndAfterSuccess() {
        val keys = pair()
        val payload = ciphertextFor(keys)
        assertEquals(SecretKind.PRIVATE_KEY, analyze(payload, pair().privatePem).secretKind)
        assertEquals(SecretKind.PRIVATE_KEY, analyze(payload, keys.privatePem).secretKind)
        assertNull(analyze("SGVsbG8gVGV4dEh1Yg==").secretKind)
    }

    // ------------------------------ 16, 18: the decoder never scans, never tries, never saves keys

    private class CountingCipher : VaultCipher {
        var unseals = 0
        override fun seal(plain: ByteArray): ByteArray = plain.reversedArray()
        override fun unseal(blob: ByteArray): ByteArray { unseals++; return blob.reversedArray() }
    }

    private class MemoryStorage : VaultStorage {
        var bytes: ByteArray? = null
        var writes = 0
        override fun read(): ByteArray? = bytes
        override fun write(bytes: ByteArray) { this.bytes = bytes; writes++ }
    }

    @Test fun theDecoderNeverTriesSavedKeysAndNeverSavesAPastedOne() {
        val cipher = CountingCipher()
        val storage = MemoryStorage()
        val collection = RsaKeyCollection(storage, cipher)
        val a = pair(); val b = pair(); val c = pair()
        collection.save("Project Alpha", a, 1L)
        collection.save("Project Beta", b, 2L)
        collection.save("Website", c, 3L)
        val writesAfterSetup = storage.writes
        val snapshot = storage.bytes!!.copyOf()

        val payload = ciphertextFor(b)
        // Without a key: asks, does not search the collection.
        assertTrue(analyze(payload) is Diagnosis.NeedsSecret)
        // With a pasted key: opens with exactly that key, nothing is saved.
        assertEquals(plaintext, (analyze(payload, b.privatePem) as Diagnosis.Decoded).text)
        // With a wrong pasted key: fails, does not fall back to the collection.
        assertTrue(analyze(payload, a.privatePem) is Diagnosis.Failed)

        assertEquals("the decoder decrypted no vault record", 0, cipher.unseals)
        assertEquals("the decoder wrote nothing", writesAfterSetup, storage.writes)
        assertTrue(snapshot.contentEquals(storage.bytes!!))

        // The explicit selection path: loading one named record decrypts one record, and the key
        // it yields is the key that opens the payload - and only it.
        val chosen = collection.load("Project Beta")!!
        assertEquals(1, cipher.unseals)
        assertEquals(plaintext, (analyze(payload, chosen.privatePem) as Diagnosis.Decoded).text)
        assertTrue(analyze(payload, collection.load("Website")!!.privatePem) is Diagnosis.Failed)
        assertEquals(writesAfterSetup, storage.writes)
        assertEquals(3, collection.keys().size)
    }

    // ---------------------------------------------------------- 19: no key material leaks anywhere

    @Test fun keyMaterialNeverAppearsInReportsOrErrors() {
        val keys = pair()
        val payload = ciphertextFor(keys)
        val body = keys.privatePem.lines().filter { !it.startsWith("-----") }
        val texts = listOf(
            runUniversal(payload, keys.privatePem).output,
            runUniversal(payload, pair().privatePem).output,
            runUniversal(payload, keys.privatePem.dropLast(40)).output,
            analyze(payload, keys.privatePem.take(100)).let { (it as? Diagnosis.Failed)?.message.orEmpty() + it.steps.joinToString() },
            (analyze(payload) as Diagnosis.NeedsSecret).candidate.reason,
        )
        texts.forEach { text ->
            body.forEach { line -> assertFalse(text.contains(line)) }
        }
    }
}
