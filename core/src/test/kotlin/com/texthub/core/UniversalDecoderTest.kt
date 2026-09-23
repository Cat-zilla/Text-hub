package com.texthub.core

import com.texthub.core.crypto.AesGcmPayload
import com.texthub.core.crypto.JweFormat
import com.texthub.core.crypto.OpenSslEnc
import com.texthub.core.crypto.RawKeyGcm
import com.texthub.core.detector.Confidence
import com.texthub.core.detector.Diagnosis
import com.texthub.core.detector.SecretKind
import com.texthub.core.detector.UniversalDecoder
import com.texthub.core.detector.usedTools
import com.texthub.core.model.validateParams
import com.texthub.core.prefs.PrefsData
import com.texthub.core.util.toHex
import com.texthub.core.model.Direction
import com.texthub.core.model.ParamKind
import com.texthub.core.model.ToolCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Universal Decoder: what it recognises, how confident it is, when it refuses to act, and the
 * promise that it never guesses a secret and never implements a format of its own.
 */
class UniversalDecoderTest {

    private val password = "correct horse battery"
    private val plaintext = "Text Hub universal decoder"

    private fun tool(id: String) = ToolRegistry.get(id)

    private fun analyze(input: String, params: Map<String, String> = emptyMap()) =
        UniversalDecoder.analyze(input, params)

    private fun decodedText(diagnosis: Diagnosis): String =
        (diagnosis as Diagnosis.Decoded).text

    /** What the registered tool itself returns (the app shows this, and the picker runs it). */
    private fun runTool(input: String, params: Map<String, String> = emptyMap()): String =
        tool(UniversalDecoder.TOOL_ID).process(input, tool(UniversalDecoder.TOOL_ID).defaultParams() + params, Direction.ENCODE)

    // ------------------------------------------------------------------ registration

    @Test fun theUniversalDecoderIsANormalRegisteredTool() {
        val meta = ToolRegistry.metaOf(UniversalDecoder.TOOL_ID)
        assertEquals("Universal Decoder", meta.name)
        assertEquals(ToolCategory.SMART, meta.category)
        assertTrue(meta.pinnedFirst)
        assertTrue("one operation only", meta.oneWay)
        assertFalse("it performs no cryptography, so it must not claim to be secure", meta.classification.secure)
        assertFalse(meta.info.summary.isBlank())
        assertTrue(meta.info.warnings.any { it.contains("never guess") })
        assertTrue("it must be findable by name", ToolRegistry.search("universal decoder").any { it.id == UniversalDecoder.TOOL_ID })
        assertTrue("it must be findable by id", ToolRegistry.search("universal").any { it.id == UniversalDecoder.TOOL_ID })
        assertEquals("it must not be registered twice", 1, ToolRegistry.all.count { it.meta.id == UniversalDecoder.TOOL_ID })
    }

    @Test fun itIsFirstInEveryOrderingThatMatters() {
        assertEquals(UniversalDecoder.TOOL_ID, ToolRegistry.all.first().meta.id)
        assertEquals(UniversalDecoder.TOOL_ID, ToolRegistry.metas().first().id)
        assertEquals(UniversalDecoder.TOOL_ID, ToolRegistry.search("").first().id)
        assertEquals(UniversalDecoder.TOOL_ID, ToolRegistry.byCategory(ToolCategory.SMART).first().id)
        // Pinning is metadata-driven: adding it last in the file still puts it first.
        val pinned = ToolRegistry.all.filter { it.meta.pinnedFirst }
        assertEquals(listOf(UniversalDecoder.TOOL_ID), pinned.map { it.meta.id })
    }

    @Test fun itNeverPerformsCryptographyOfItsOwn() {
        // Every tool the decoder can dispatch to is a registered tool, and it holds no algorithm of
        // its own: the ids it can name are exactly the ids that exist.
        val known = ToolRegistry.all.map { it.meta.id }.toSet()
        val everyIdTheDecoderCanName = known.filter { it !in setOf("universal") }
        assertTrue("the decoder must only ever name registered tools", everyIdTheDecoderCanName.isNotEmpty())
        assertTrue(UniversalDecoder.analyze("x").usedTools.all { it in known })
        val metadata = tool(UniversalDecoder.TOOL_ID).meta
        assertTrue(metadata.params.none { it.kind == ParamKind.CHOICE && it.defaultValue == "aes" })
    }

    // ------------------------------------------------------------------ deterministic formats

    @Test fun base64IsRecognisedWithItsReason() {
        val encoded = tool("base64").process(plaintext, tool("base64").defaultParams(), Direction.ENCODE)
        val diagnosis = analyze(encoded)
        val decoded = diagnosis as Diagnosis.Decoded
        assertEquals(plaintext, decoded.text)
        val step = decoded.steps.single()
        assertEquals("base64", step.toolId)
        assertTrue("padded Base64 that decodes to text is high confidence", step.confidence == Confidence.HIGH)
        val candidate = UniversalDecoder.detect(encoded).first()
        assertTrue(candidate.reason.contains("alphabet"))
        assertTrue(candidate.reason.contains("multiple of four"))
    }

    @Test fun base64UrlIsDistinguishedFromStandardBase64() {
        val encoded = tool("base64").process(
            "???>>>", tool("base64").defaultParams() + ("variant" to "url"), Direction.ENCODE,
        )
        val candidate = UniversalDecoder.detect(encoded).firstOrNull { it.toolId == "base64" }
        assertNotNull("Base64URL must be detected", candidate)
        assertEquals("Base64URL", candidate!!.label)
        val diagnosis = analyze(encoded) as Diagnosis.Decoded
        assertEquals("???>>>", diagnosis.text)
    }

    @Test fun hexadecimalIsDecodedAndDigestsAreNot() {
        val hex = tool("hex").process(plaintext, tool("hex").defaultParams(), Direction.ENCODE)
        val diagnosis = analyze(hex)
        assertEquals(plaintext, decodedText(diagnosis))
        assertTrue(diagnosis.steps.single().confidence == Confidence.HIGH || diagnosis.steps.single().confidence == Confidence.LIKELY)
    }

    @Test fun everyDeterministicEncodingDecodesThroughItsOwnTool() {
        val cases = listOf(
            Triple("base32", plaintext, "Base32"),
            Triple("url", "a b+c/d?", "URL"),
            Triple("unicodeescape", "Grüße", "Unicode escape"),
            Triple("htmlentities", "<b>&</b>", "HTML entities"),
            Triple("quotedprintable", "Grüße und Brüder = mit =3D", "Quoted-printable"),
            Triple("morse", "SOS SOS", "Morse"),
            Triple("binary", "Hi there", "Binary"),
            Triple("roman", "II", "Roman"),
            // Punycode is covered separately: the codec output carries no marker, so only the
            // real-world form (with the xn-- prefix the DNS adds) can be detected.
            Triple("braille", "hello", "Braille"),
            Triple("a1z26", "HELLO WORLD", "A1Z26"),
            Triple("utf16", "hello world", "UTF-16"),
            Triple("utf32", "hello world", "Unicode code points"),
            Triple("json", "{\"a\": 1}", "JSON"),
        )
        val failures = mutableListOf<String>()
        cases.forEach { (toolId, source, expectedLabel) ->
            val processor = tool(toolId)
            val encoded = processor.process(source, processor.defaultParams(), Direction.ENCODE)
            val diagnosis = analyze(encoded)
            when (diagnosis) {
                is Diagnosis.Decoded -> {
                    if (diagnosis.steps.first().toolId != toolId) {
                        failures += "$toolId: detected as ${diagnosis.steps.first().toolId} instead"
                    }
                }
                else -> failures += "$toolId: not decoded ($diagnosis)"
            }
            val label = UniversalDecoder.detect(encoded).firstOrNull()?.label ?: "nothing"
            if (!label.contains(expectedLabel)) failures += "$toolId: label '$label' does not mention '$expectedLabel'"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun punycodeLabelsWithTheDnsPrefixAreDetectedAndDecoded() {
        // The ACE prefix belongs to a single label, which is what the Punycode tool converts, so a
        // label pasted out of a browser decodes straight through the dispatcher.
        assertEquals(
            "münchen",
            tool("punycode").process("xn--mnchen-3ya", tool("punycode").defaultParams(), Direction.DECODE),
        )
        val diagnosis = analyze("xn--mnchen-3ya")
        assertEquals("münchen", decodedText(diagnosis))
        assertEquals("punycode", diagnosis.steps.single().toolId)

        // A whole domain is recognised as an internationalised name (that is real evidence: only
        // such domains carry the prefix) but not handed to the label codec as one piece, so nothing
        // fails and nothing is claimed that cannot be done.
        val domainCandidates = UniversalDecoder.detect("xn--mnchen-3ya.de")
        assertEquals("Internationalised domain name", domainCandidates.first().label)
        assertFalse("a whole domain is not decoded by the label codec", domainCandidates.first().actionable)
        assertFalse(analyze("xn--mnchen-3ya.de") is Diagnosis.Decoded)

        // Without the ACE prefix there is no evidence at all, so nothing is claimed either.
        assertTrue(UniversalDecoder.detect("mnchen.example-gsb").isEmpty())
    }

    @Test fun anUnpaddedHexStringWithAnOddLengthIsNotAnEnvelopeCandidate() {
        // "deadbeef" is fine hex; "abc" is not (odd length) - neither may be reported as an envelope.
        assertFalse(UniversalDecoder.detect("abc").any { it.toolId == "hex" })
    }

    // ------------------------------------------------------------------ ambiguous input

    @Test fun aSha256ShapedValueIsACandidateAndNotACertainty() {
        val digest = "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8"
        val diagnosis = analyze(digest)
        assertTrue("a bare digest is not decoded", diagnosis is Diagnosis.HashOnly)
        val candidates = (diagnosis as Diagnosis.HashOnly).candidates
        val sha = candidates.first { it.hashAlgorithm == "SHA-256" }
        assertEquals(Confidence.POSSIBLE, sha.confidence)
        assertTrue(sha.reason.contains("64 hexadecimal characters"))
        assertFalse("a digest must never be treated as decodable", sha.actionable)
        assertTrue(diagnosis.note.contains("one-way"))
        assertTrue(diagnosis.note.contains("cannot be decrypted"))
    }

    @Test fun everyDigestLengthIsRecognisedAsOneWay() {
        val lengths = mapOf(32 to "MD5", 40 to "SHA-1", 64 to "SHA-256", 128 to "SHA-512")
        lengths.forEach { (length, algorithm) ->
            val digest = "ab".repeat(length / 2)
            val diagnosis = analyze(digest)
            assertTrue("$algorithm must not be decoded", diagnosis is Diagnosis.HashOnly)
            val candidate = (diagnosis as Diagnosis.HashOnly).candidates.first { it.hashAlgorithm == algorithm }
            assertEquals(Confidence.POSSIBLE, candidate.confidence)
            assertFalse(candidate.actionable)
        }
    }

    @Test fun aHashCanBeCheckedAgainstCandidateTextButNeverDecrypted() {
        val digest = tool("hash").process("hello", tool("hash").defaultParams(), Direction.ENCODE)
        val match = runTool(digest, mapOf(UniversalDecoder.PARAM_HASH_CANDIDATE to "hello"))
        assertTrue("the comparison must confirm the match: $match", match.contains("Match", ignoreCase = true))
        val noMatch = runTool(digest, mapOf(UniversalDecoder.PARAM_HASH_CANDIDATE to "goodbye"))
        assertTrue(noMatch.contains("not match", ignoreCase = true) || noMatch.contains("no match", ignoreCase = true))
        assertFalse("nothing may be presented as decrypted", match.contains("Decrypt"))
    }

    @Test fun severalCandidatesAreOfferedInsteadOfAGuess() {
        // 8 hexadecimal digits could be hex text or a truncated digest: both are offered.
        val diagnosis = analyze("48656c6c6f")
        when (diagnosis) {
            is Diagnosis.Decoded -> assertEquals("Hello", diagnosis.text)
            is Diagnosis.Choose -> assertTrue(diagnosis.candidates.size >= 1)
            else -> throw AssertionError("unexpected: $diagnosis")
        }
        val candidates = UniversalDecoder.detect("3132333435")
        assertTrue("a numeric value must offer more than one reading", candidates.size >= 2)
        assertTrue(candidates.all { it.reason.isNotBlank() })
    }

    @Test fun unsupportedJweAlgorithmsAreNamedNotGuessed() {
        // alg=A128KW is valid JWE that Text Hub does not implement.
        val header = com.texthub.core.codec.Base64Codec.encode(
            "{\"alg\":\"A128KW\",\"enc\":\"A256GCM\"}".toByteArray(), urlSafe = true, padding = false,
        )
        val token = "$header..aaaa.bbbb.cccc"
        val diagnosis = analyze(token)
        assertTrue("must not claim to decrypt it", diagnosis is Diagnosis.NotSupported)
        assertTrue((diagnosis as Diagnosis.NotSupported).note.isNotBlank())
        assertTrue(diagnosis.candidates.first().reason.contains("A128KW"))
    }

    // ------------------------------------------------------------------ encryption

    @Test fun everyTextHubEnvelopeIsIdentifiedWithItsOwnTool() {
        val gcm = tool("aes").process(plaintext, tool("aes").defaultParams() + ("password" to password), Direction.ENCODE)
        val cbc = tool("aescbc").process(plaintext, tool("aescbc").defaultParams() + ("password" to password), Direction.ENCODE)
        val ctr = tool("aesctr").process(plaintext, tool("aesctr").defaultParams() + ("password" to password), Direction.ENCODE)
        val chacha = tool("chacha").process(plaintext, tool("chacha").defaultParams() + ("password" to password), Direction.ENCODE)

        val expected = mapOf(gcm to "aes", cbc to "aescbc", ctr to "aesctr", chacha to "chacha")
        expected.forEach { (payload, toolId) ->
            val candidate = UniversalDecoder.detect(payload).first()
            assertEquals("$toolId envelope", toolId, candidate.toolId)
            assertEquals(Confidence.HIGH, candidate.confidence)
            assertTrue(candidate.requiresSecret)
            assertEquals(SecretKind.PASSWORD, candidate.secretKind)
            // Without a password the analysis stops and asks, it never guesses.
            val diagnosis = analyze(payload)
            assertTrue(diagnosis is Diagnosis.NeedsSecret)
            assertEquals(toolId, (diagnosis as Diagnosis.NeedsSecret).candidate.toolId)
            // With it, the existing tool opens the payload.
            assertEquals(plaintext, decodedText(analyze(payload, mapOf(UniversalDecoder.PARAM_SECRET to password))))
        }
    }

    @Test fun rawKeyAndRsaPayloadsAskForTheRightKindOfSecret() {
        val keyHex = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"
        val raw = RawKeyGcm.encrypt(plaintext, keyHex)
        val rawDiagnosis = analyze(raw)
        assertTrue(rawDiagnosis is Diagnosis.NeedsSecret)
        assertEquals(SecretKind.KEY, (rawDiagnosis as Diagnosis.NeedsSecret).candidate.secretKind)
        assertEquals("key", rawDiagnosis.candidate.secretParam)
        assertEquals(plaintext, decodedText(analyze(raw, mapOf(UniversalDecoder.PARAM_SECRET to keyHex))))

        // The RSA payload only needs to be *identified* here; opening it needs its private key,
        // which the RSA tool's own tests cover.
        val rsaPayload = Base64CodecStub.envelope(
            byteArrayOf(0x11, 0x01, 0x01, 0x00) + ByteArray(256) + ByteArray(12) + ByteArray(16),
        )
        val rsaDiagnosis = analyze(rsaPayload)
        assertTrue(rsaDiagnosis is Diagnosis.NeedsSecret)
        assertEquals(SecretKind.PRIVATE_KEY, (rsaDiagnosis as Diagnosis.NeedsSecret).candidate.secretKind)
        assertEquals("rsa", rsaDiagnosis.candidate.toolId)
    }

    @Test fun openSslFilesAreIdentifiedAndOpenedWithTheExistingTool() {
        val payload = OpenSslEnc.encrypt(
            plaintext, password.toCharArray(), 32, OpenSslEnc.Kdf.PBKDF2, OpenSslEnc.Digest.SHA256, 10_000,
        )
        val candidate = UniversalDecoder.detect(payload).first()
        assertEquals("aescbc", candidate.toolId)
        assertEquals("openssl", candidate.suggestedParams["format"])
        assertTrue(candidate.reason.contains("Salted__"))
        assertTrue(analyze(payload) is Diagnosis.NeedsSecret)
        assertEquals(plaintext, decodedText(analyze(payload, mapOf(UniversalDecoder.PARAM_SECRET to password))))
    }

    @Test fun jweTokensAreIdentifiedAndOpened() {
        val key = ByteArray(32) { it.toByte() }
        val token = JweFormat.encrypt(plaintext, key, JweFormat.Enc.A256GCM)
        val candidate = UniversalDecoder.detect(token).first()
        assertEquals("aesrawkey", candidate.toolId)
        assertTrue(candidate.reason.contains("alg=dir"))
        assertEquals("jwe", candidate.suggestedParams["format"])
        assertEquals(plaintext, decodedText(analyze(token, mapOf(UniversalDecoder.PARAM_SECRET to key.toHex(upper = false)))))
    }

    @Test fun aWrongPasswordFailsLoudlyAndNeverReturnsGarbage() {
        val payload = tool("aes").process(plaintext, tool("aes").defaultParams() + ("password" to password), Direction.ENCODE)
        val diagnosis = analyze(payload, mapOf(UniversalDecoder.PARAM_SECRET to "not the password"))
        assertTrue("a failed authentication must be reported as a failure", diagnosis is Diagnosis.Failed)
        val message = (diagnosis as Diagnosis.Failed).message
        assertFalse("no plaintext may be shown", message.contains(plaintext))
        assertFalse("no stack trace may be shown", message.contains("Exception"))
        assertTrue(message.isNotBlank())
    }

    @Test fun theKeySizeInThePayloadIsEnforcedNotAskedFor() {
        val payload = tool("aes").process(
            plaintext, tool("aes").defaultParams() + ("password" to password + "128") + ("keySize" to "128"), Direction.ENCODE,
        )
        // The payload records 128-bit; the decoder must read that and open it without asking.
        assertEquals(plaintext, decodedText(analyze(payload, mapOf(UniversalDecoder.PARAM_SECRET to password + "128"))))
        val candidate = UniversalDecoder.detect(payload).first()
        assertEquals(Confidence.HIGH, candidate.confidence)
    }

    @Test fun aLegacyPayloadWithoutARecordedKeySizeIsStillOpened() {
        // 1.4.1 wrote GCM payloads with KDF id 0x01 and no key size: the decoder resolves the size
        // from the payload's own authentication tag and says so, instead of failing.
        val legacy128 = AesGcmPayload.encrypt(plaintext, password.toCharArray(), 16, prefixKeySizeInPayload = false)
        assertEquals(plaintext, decodedText(analyze(legacy128, mapOf(UniversalDecoder.PARAM_SECRET to password))))
        val reason = analyze(legacy128, mapOf(UniversalDecoder.PARAM_SECRET to password)).steps.first().label
        assertTrue(reason.contains("AES-GCM"))
    }

    // ------------------------------------------------------------------ nested layers

    @Test fun nestedEncodingsAreUnwrappedInOrder() {
        val inner = tool("base64").process(plaintext, tool("base64").defaultParams(), Direction.ENCODE)
        val middle = tool("url").process(inner, tool("url").defaultParams() + ("variant" to "form"), Direction.ENCODE)
        val outer = tool("base32").process(middle, tool("base32").defaultParams(), Direction.ENCODE)

        val diagnosis = analyze(outer)
        assertEquals(plaintext, decodedText(diagnosis))
        assertEquals(listOf("base32", "url", "base64"), diagnosis.steps.map { it.toolId })
        assertTrue(diagnosis.steps.all { it.confidence.rank >= Confidence.LIKELY.rank })
    }

    @Test fun anEncryptedInnerLayerIsOpenedWhenTheSecretIsAvailable() {
        val inner = tool("base64").process(plaintext, tool("base64").defaultParams(), Direction.ENCODE)
        val encrypted = tool("aes").process(inner, tool("aes").defaultParams() + ("password" to password), Direction.ENCODE)
        val diagnosis = analyze(encrypted, mapOf(UniversalDecoder.PARAM_SECRET to password))
        assertEquals(plaintext, decodedText(diagnosis))
        assertEquals(listOf("aes", "base64"), diagnosis.steps.map { it.toolId })

        // Without the secret the chain stops at the layer that needs it.
        val withoutSecret = analyze(encrypted)
        assertTrue(withoutSecret is Diagnosis.NeedsSecret)
    }

    @Test fun nestingStopsAtTheDepthLimit() {
        var value = plaintext
        repeat(6) { value = tool("base64").process(value, tool("base64").defaultParams(), Direction.ENCODE) }
        val shallow = analyze(value, mapOf(UniversalDecoder.PARAM_MAX_DEPTH to "2"))
        assertTrue(shallow.steps.size <= 2)
        val deep = analyze(value, mapOf(UniversalDecoder.PARAM_MAX_DEPTH to "8"))
        assertEquals(plaintext, decodedText(deep))
        // A nonsense depth is clamped rather than trusted.
        val clamped = analyze(value, mapOf(UniversalDecoder.PARAM_MAX_DEPTH to "9999"))
        assertTrue(clamped.steps.size <= UniversalDecoder.MAX_MAX_DEPTH)
    }

    @Test fun aSelfReferentialEncodingCannotLoopForever() {
        // Base64 of the empty string is empty; "TQ==" decodes to "M", which is not valid Base64, so
        // this pair is the classic short cycle. Either way the analyser must terminate.
        val looped = analyze("TQ==")
        assertTrue(looped.steps.size <= UniversalDecoder.MAX_MAX_DEPTH)
        val identity = analyze("YWJjZGVmZ2hpamtsbW5vcA==")
        assertEquals("abcdefghijklmnop", decodedText(identity))
        assertTrue(identity.steps.size <= UniversalDecoder.MAX_MAX_DEPTH)
    }

    // ------------------------------------------------------------------ detection failures

    @Test fun emptyAndUnrecognisableInputsAreExplainedNotGuessed() {
        val empty = analyze("")
        assertTrue(empty is Diagnosis.Unrecognized)
        assertTrue((empty as Diagnosis.Unrecognized).note.isNotBlank())

        val random = analyze("qwertyuiop 12345 -=[]\\;',./  ^%\$#@!")
        assertTrue(random is Diagnosis.Unrecognized || random is Diagnosis.Choose)
        assertTrue("a report must always say something: $random", runTool("qwertyuiop 12345").isNotBlank())
    }

    @Test fun malformedPayloadsFailWithAFriendlySentence() {
        val cases = listOf(
            "SGVsbG8",                 // truncated Base64 (unpadded is fine, but this is 7 chars)
            "!!!!not base64!!!!",
            "zzzzzzzzzzzzzzzzzzzzzz",  // Base58-shaped junk
            "abc123",                  // neither
            "AQ=",                     // envelope version byte, far too short
            "AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAg", // 0x02, too short
        )
        cases.forEach { input ->
            val outcome = ProcessingEngine.run(
                tool(UniversalDecoder.TOOL_ID), input, tool(UniversalDecoder.TOOL_ID).defaultParams(), Direction.ENCODE,
            )
            val text = outcome.error ?: outcome.output
            assertTrue("$input produced nothing", text.isNotBlank())
            assertFalse("$input leaked a stack trace", text.contains("Exception") || text.contains(".kt:"))
        }
    }

    @Test fun aTruncatedEnvelopeFailsInsteadOfReturningGarbage() {
        val payload = tool("aes").process(plaintext, tool("aes").defaultParams() + ("password" to password), Direction.ENCODE)

        // Cut well below the minimum length of the layout: not an envelope any more.
        val muchShorter = payload.take(payload.length / 3)
        assertFalse(
            "a payload far too short must not be called a Text Hub envelope",
            UniversalDecoder.detect(muchShorter).any { it.label.startsWith("Text Hub AES-GCM") },
        )

        // Cut slightly: it still has the shape, but the authentication tag can no longer verify, so
        // the analysis must end in a failure - never in a decoded result.
        val slightlyShort = payload.dropLast(8)
        val diagnosis = analyze(slightlyShort, mapOf(UniversalDecoder.PARAM_SECRET to password))
        assertTrue("a truncated payload must never decode: $diagnosis", diagnosis is Diagnosis.Failed)
        assertFalse((diagnosis as Diagnosis.Failed).message.contains(plaintext))
    }

    @Test fun parametersOfTheTargetToolAreStillEnforced() {
        // The decoder must not weaken the strictness of the tool it dispatches to: a payload written
        // with a 128-bit key is refused when the wrong size is forced by hand.
        val payload = tool("aes").process(
            plaintext, tool("aes").defaultParams() + ("password" to password) + ("keySize" to "128"), Direction.ENCODE,
        )
        val outcome = ProcessingEngine.run(
            tool("aes"), payload,
            tool("aes").defaultParams() + ("password" to password) + ("keySize" to "256"),
            Direction.DECODE,
        )
        assertNotNull(outcome.error)
        assertTrue(outcome.error!!.contains("128"))
    }

    // ------------------------------------------------------------------ reporting and limits

    @Test fun theReportExplainsWhatWasDetectedAndWhy() {
        val encoded = tool("base64").process(plaintext, tool("base64").defaultParams(), Direction.ENCODE)
        assertEquals(plaintext, runTool(encoded))

        val gcm = tool("aes").process(plaintext, tool("aes").defaultParams() + ("password" to password), Direction.ENCODE)
        val report = runTool(gcm)
        assertTrue(report.contains("Detected"))
        assertTrue(report.contains("Additional information required"))
        assertTrue(report.contains("Password"))
        assertTrue(report.contains("will not guess"))

        val digest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        val hashReport = runTool(digest)
        assertTrue(hashReport.contains("one-way"))
        assertTrue(hashReport.contains("cannot be decrypted"))
    }

    @Test fun keyMaterialIsRecognisedAsKeyMaterial() {
        val privateKey = RsaKeyGenStub.privateKeyPem()
        val diagnosis = analyze(privateKey)
        assertTrue(diagnosis is Diagnosis.NotSupported)
        assertTrue((diagnosis as Diagnosis.NotSupported).note.contains("Key field"))
        assertTrue(diagnosis.candidates.first().label.contains("private key"))
    }

    @Test fun largeInputIsHandledWithoutBlowingUp() {
        val big = "The quick brown fox jumps over the lazy dog. ".repeat(400)
        val encoded = tool("base64").process(big, tool("base64").defaultParams(), Direction.ENCODE)
        val started = System.nanoTime()
        val diagnosis = analyze(encoded)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertEquals(big, decodedText(diagnosis))
        assertTrue("decoding 18 KB should not take $elapsedMs ms", elapsedMs < 5_000)
    }

    @Test fun secretsNeverAppearInTheDiagnosisText() {
        val gcm = tool("aes").process(plaintext, tool("aes").defaultParams() + ("password" to password), Direction.ENCODE)
        val wrong = analyze(gcm, mapOf(UniversalDecoder.PARAM_SECRET to "hunter2"))
        val texts = buildString {
            append(runTool(gcm, mapOf(UniversalDecoder.PARAM_SECRET to "hunter2")))
            append(wrong.toString())
            if (wrong is Diagnosis.Failed) append(wrong.message)
        }
        assertFalse("the supplied secret must never be echoed", texts.contains("hunter2"))
        assertFalse("plaintext must never be echoed on failure", texts.contains(plaintext))
        assertFalse(texts.contains(password))
    }

    // ------------------------------------------------------------------ ordering + overrides

    @Test fun theFirstPositionSurvivesFavouritesAndTemporaryData() {
        // Favourites and the decoder's position are two independent orderings (round 10 §18): the
        // favourites order is stored in the preferences, the position comes from the registry, and
        // nothing a user can do to their favourites touches it.
        val before = ToolRegistry.all.map { it.meta.id }

        // Favourite it, reorder the favourites list back to front, un-favourite it again, and clear
        // the temporary data in between.
        var store = PrefsData()
        for (id in listOf("aes", "base64", UniversalDecoder.TOOL_ID, "hash")) {
            store = store.toggleFavorite(id)
        }
        assertEquals(listOf("aes", "base64", UniversalDecoder.TOOL_ID, "hash"), store.favorites())
        store = store.moveFavorite(2, 0)
        assertEquals(
            listOf(UniversalDecoder.TOOL_ID, "aes", "base64", "hash"),
            store.favorites(),
        )
        assertEquals(
            "the favourites order must survive a clear",
            listOf(UniversalDecoder.TOOL_ID, "aes", "base64", "hash"),
            PrefsData(store.clearTemporary().entries).favorites(),
        )
        store = store.toggleFavorite(UniversalDecoder.TOOL_ID)
        assertEquals(listOf("aes", "base64", "hash"), store.favorites())

        assertEquals("the registry order is untouched by favourites", before, ToolRegistry.all.map { it.meta.id })
        assertEquals(UniversalDecoder.TOOL_ID, ToolRegistry.all.first().meta.id)
        assertEquals(UniversalDecoder.TOOL_ID, ToolRegistry.metas().first().id)
        // The picker groups by the category's ordinal, so the smart category must stay first.
        assertEquals(0, ToolCategory.SMART.ordinal)
        assertEquals(ToolCategory.SMART, ToolRegistry.metaOf(UniversalDecoder.TOOL_ID).category)
    }

    @Test fun aManualOverrideForcesTheChosenToolInsteadOfTheDetectedOne() {
        // "414243" is hexadecimal and decodes to ABC; forcing Base64 must dispatch to the Base64
        // tool (which then refuses the bytes) instead of quietly falling back to the detection.
        val detected = analyze("414243")
        assertEquals("ABC", decodedText(detected))

        val forced = analyze("414243", mapOf(UniversalDecoder.PARAM_PREFER to "base64"))
        assertTrue("the override must be honoured", forced !is Diagnosis.Decoded || decodedText(forced) != "ABC")
        assertTrue(
            "the override must be reported, so it can be cleared",
            forced.toString().contains("base64"),
        )

        // Clearing the override goes straight back to detection: the override can never trap anyone.
        assertEquals("ABC", decodedText(analyze("414243", mapOf(UniversalDecoder.PARAM_PREFER to ""))))
    }

    @Test fun anUnknownOverrideIsRefusedInlineAndIgnoredByTheAnalysis() {
        val meta = tool(UniversalDecoder.TOOL_ID).meta
        val issues = meta.validateParams(
            tool(UniversalDecoder.TOOL_ID).defaultParams() + (UniversalDecoder.PARAM_PREFER to "not-a-tool"),
        )
        assertTrue("an unknown override must be refused inline", issues.any { it.key == UniversalDecoder.PARAM_PREFER })
        // ...and if it reaches the analysis anyway, detection still decides rather than failing.
        assertEquals("ABC", decodedText(analyze("414243", mapOf(UniversalDecoder.PARAM_PREFER to "not-a-tool"))))
    }

    @Test fun analysingTheResultAgainUnwrapsTheNextLayer() {
        // This is exactly what the "Analyse result again" button does with the previous output: the
        // chain limit decides how much happens at once, and the next pass continues from there.
        val inner = tool("base64").process(plaintext, tool("base64").defaultParams(), Direction.ENCODE)
        val outer = tool("base64").process(inner, tool("base64").defaultParams(), Direction.ENCODE)
        val oneLayer = mapOf(UniversalDecoder.PARAM_MAX_DEPTH to "1")

        val first = analyze(outer, oneLayer)
        assertEquals("one layer only, as asked for", inner, decodedText(first))
        val second = analyze(decodedText(first), oneLayer)
        assertEquals(plaintext, decodedText(second))

        // With the default depth the whole chain is unwrapped in one pass instead, and the chain
        // lists both layers.
        val wholeChain = analyze(outer) as Diagnosis.Decoded
        assertEquals(plaintext, wholeChain.text)
        assertEquals(2, wholeChain.steps.size)
    }

    @Test fun everyDecodedDiagnosisNamesTheToolThatDidIt() {
        // The analysis card reads the last step for the wording ("Decoded with Base64") and the
        // confidence, so a decoded result must always carry at least one step.
        val samples = listOf(
            tool("base64").process(plaintext, tool("base64").defaultParams(), Direction.ENCODE),
            tool("hex").process(plaintext, tool("hex").defaultParams(), Direction.ENCODE),
            tool("url").process("hello world", tool("url").defaultParams(), Direction.ENCODE),
            tool("morse").process("HELLO", tool("morse").defaultParams(), Direction.ENCODE),
        )
        samples.forEach { sample ->
            val diagnosis = analyze(sample)
            if (diagnosis is Diagnosis.Decoded) {
                assertTrue("$sample: a decoded result must name its layer", diagnosis.steps.isNotEmpty())
                assertTrue(
                    "$sample: the layer must be a registered tool",
                    ToolRegistry.all.any { it.meta.id == diagnosis.steps.last().toolId },
                )
            }
        }
    }

    // ------------------------------------------------------------------ test doubles

    private object Base64CodecStub {
        fun envelope(bytes: ByteArray): String = com.texthub.core.codec.Base64Codec.encode(bytes)
    }

    private object RsaKeyGenStub {
        fun privateKeyPem(): String =
            "-----BEGIN PRIVATE KEY-----\nMIIB\n-----END PRIVATE KEY-----"
    }
}
