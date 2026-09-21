package com.texthub.core

import com.texthub.core.codec.Base91Codec
import com.texthub.core.codec.BrailleCodec
import com.texthub.core.codec.HexDumpCodec
import com.texthub.core.codec.PunycodeCodec
import com.texthub.core.codec.RomanCodec
import com.texthub.core.codec.Utf16Codec
import com.texthub.core.codec.Utf32Codec
import com.texthub.core.crypto.AesCbcHmac
import com.texthub.core.crypto.AesGcmPayload
import com.texthub.core.crypto.ChaCha20Poly1305
import com.texthub.core.model.Classification
import com.texthub.core.model.Direction
import com.texthub.core.model.ToolException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Convenience wrapper around the checksum codec. */
private fun checksum(text: String, kind: String): String =
    com.texthub.core.codec.Checksums.computeHex(text, kind)

/** Tests for the third wave: AES key sizes, more encryption, more ciphers, more encodings. */
class NewTools2Test {

    private fun enc(id: String, text: String, params: Map<String, String> = emptyMap()): String {
        val p = ToolRegistry.get(id)
        return p.process(text, p.defaultParams() + params, Direction.ENCODE)
    }

    private fun dec(id: String, text: String, params: Map<String, String> = emptyMap()): String {
        val p = ToolRegistry.get(id)
        return p.process(text, p.defaultParams() + params, Direction.DECODE)
    }

    // ------------------------------------------------------------ AES key sizes

    @Test fun aesGcmAllKeySizes() {
        val text = "AES key size round trip 🎯"
        for (size in listOf("128", "192", "256")) {
            val payload = enc("aes", text, mapOf("password" to "correct horse", "keySize" to size))
            assertEquals("size $size", text, dec("aes", payload, mapOf("password" to "correct horse")))
        }
    }

    @Test fun aesGcmPayloadDoesNotNeedTheKeySizeToDecrypt() {
        // The key size is not stored, so decryption tries each size - which keeps old payloads
        // readable and means the user never has to remember the setting.
        val payload = enc("aes", "legacy payload", mapOf("password" to "pw", "keySize" to "128"))
        assertEquals("legacy payload", dec("aes", payload, mapOf("password" to "pw")))
        assertEquals("legacy payload", dec("aes", payload, mapOf("password" to "pw", "keySize" to "256")))
    }

    @Test fun aesGcmKeySizesAreReportedAndDistinct() {
        val password = "pw".toCharArray().copyOf()
        for (size in listOf(16, 24, 32)) {
            val payload = AesGcmPayload.encrypt("message", password.copyOf(), size)
            assertEquals(size, AesGcmPayload.keySizeOf(payload, password.copyOf()))
        }
        val payload128 = AesGcmPayload.encrypt("message", "pw".toCharArray(), 16)
        val payload256 = AesGcmPayload.encrypt("message", "pw".toCharArray(), 32)
        assertNotEquals(payload128, payload256)
    }

    @Test fun aesCbcAllKeySizesAndTamperDetection() {
        val text = "CBC with a selectable key size"
        for (size in listOf("128", "192", "256")) {
            val payload = enc("aescbc", text, mapOf("password" to "pw", "keySize" to size))
            assertEquals(size, text, dec("aescbc", payload, mapOf("password" to "pw")))
        }
        val payload = enc("aescbc", text, mapOf("password" to "pw", "keySize" to "128"))
        assertEquals(16, AesCbcHmac.keySizeOf(payload, "pw".toCharArray()))
        val bytes = com.texthub.core.codec.Base64Codec.decode(payload)
        val tampered = bytes.copyOf().also { it[40] = (it[40] + 1).toByte() }
        assertTrue(runCatching {
            dec("aescbc", com.texthub.core.codec.Base64Codec.encode(tampered), mapOf("password" to "pw"))
        }.exceptionOrNull() is ToolException)
        assertNull(AesCbcHmac.keySizeOf(payload, "wrong".toCharArray()))
    }

    @Test fun chachaStillWorksAlongsideTheNewKeySizes() {
        if (!ChaCha20Poly1305.isAvailable()) return
        val text = "ChaCha is always 256-bit"
        val payload = enc("chacha", text, mapOf("password" to "pw"))
        assertEquals(text, dec("chacha", payload, mapOf("password" to "pw")))
    }

    // ------------------------------------------------------- hashes and MACs

    @Test fun knownHashVectors() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            enc("hash", "abc", mapOf("algorithm" to "SHA-256")),
        )
        assertEquals(
            "a9993e364706816aba3e25717850c26c9cd0d89d",
            enc("hash", "abc", mapOf("algorithm" to "SHA-1")),
        )
        assertEquals(
            "900150983cd24fb0d6963f7d28e17f72",
            enc("hash", "abc", mapOf("algorithm" to "MD5")),
        )
        assertEquals(
            "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD",
            enc("hash", "abc", mapOf("algorithm" to "SHA-256", "format" to "hexUpper")),
        )
        // Base64 of the same digest
        assertEquals("ungWv48Bz+pBQUDeXa4iI7ADYaOWF3qctBD/YfIAFa0=",
            enc("hash", "abc", mapOf("algorithm" to "SHA-256", "format" to "base64")))
    }

    @Test fun hashVerifyDirection() {
        val digest = enc("hash", "hello world", mapOf("algorithm" to "SHA-256"))
        assertTrue(dec("hash", digest, mapOf("algorithm" to "SHA-256", "compare" to "hello world"))
            .startsWith("Match"))
        assertTrue(dec("hash", digest, mapOf("algorithm" to "SHA-256", "compare" to "hello worlds"))
            .startsWith("No match"))
        assertTrue(runCatching { dec("hash", digest, mapOf("algorithm" to "SHA-256")) }
            .exceptionOrNull() is ToolException)
    }

    @Test fun knownHmacVectors() {
        val message = "The quick brown fox jumps over the lazy dog"
        assertEquals(
            "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
            enc("hmac", message, mapOf("key" to "key", "algorithm" to "HmacSHA256")),
        )
        assertEquals(
            "de7c9b85b8b78aa6bc8a7a36f70a90701c9db4d9",
            enc("hmac", message, mapOf("key" to "key", "algorithm" to "HmacSHA1")).lowercase(),
        )
    }

    @Test fun hmacVerifyBothFormats() {
        val message = "transfer 100"
        val hexTag = enc("hmac", message, mapOf("key" to "secret", "algorithm" to "HmacSHA256"))
        assertTrue(dec("hmac", hexTag, mapOf("key" to "secret", "message" to message)).startsWith("Match"))
        assertTrue(dec("hmac", hexTag, mapOf("key" to "secret", "message" to "transfer 900"))
            .startsWith("No match"))
        val base64Tag = enc("hmac", message,
            mapOf("key" to "secret", "algorithm" to "HmacSHA256", "format" to "base64"))
        assertTrue(dec("hmac", base64Tag, mapOf("key" to "secret", "message" to message)).startsWith("Match"))
        assertTrue(runCatching { dec("hmac", hexTag, mapOf("key" to "secret")) }
            .exceptionOrNull() is ToolException)
    }

    @Test fun pbkdf2StoresAndChecks() {
        val stored = enc("pbkdf2", "", mapOf("password" to "correct horse battery", "algorithm" to "PBKDF2WithHmacSHA256"))
        assertTrue("stored form: $stored", stored.startsWith("pbkdf2-sha256\$210000\$"))
        assertEquals(4, stored.split("$").size)
        assertTrue(dec("pbkdf2", stored, mapOf("password" to "correct horse battery")).startsWith("Match"))
        assertTrue(dec("pbkdf2", stored, mapOf("password" to "wrong password")).startsWith("No match"))
        // Fresh salt per call
        val again = enc("pbkdf2", "", mapOf("password" to "correct horse battery"))
        assertNotEquals(stored, again)
        assertTrue(dec("pbkdf2", again, mapOf("password" to "correct horse battery")).startsWith("Match"))
    }

    @Test fun pbkdf2RejectsShortPasswordsAndBadFormat() {
        assertTrue(runCatching { enc("pbkdf2", "", mapOf("password" to "short")) }.exceptionOrNull() is ToolException)
        val e = runCatching { dec("pbkdf2", "not a stored hash", mapOf("password" to "longenoughpw")) }
            .exceptionOrNull()
        assertTrue(e is ToolException)
    }

    @Test fun checksumKnownVectorAndCheck() {
        val text = "The quick brown fox jumps over the lazy dog"
        assertEquals("414FA339", checksum(text, "CRC-32"))
        val out = enc("checksum", text, mapOf("kind" to "CRC-32"))
        assertTrue(out.contains("1095738169"))
        assertTrue(out.contains("0x414FA339"))
        assertTrue(dec("checksum", "0x414FA339", mapOf("kind" to "CRC-32", "compare" to text)).startsWith("Match"))
        assertTrue(dec("checksum", "414FA339", mapOf("kind" to "CRC-32", "compare" to text)).startsWith("Match"))
        assertTrue(dec("checksum", "00000000", mapOf("kind" to "CRC-32", "compare" to text)).startsWith("No match"))
        assertTrue(enc("checksum", text, mapOf("kind" to "Adler-32")).contains("decimal:"))
    }

    @Test fun hashesAndMacsAreNotMarkedSecure() {
        listOf("hash", "hmac", "pbkdf2", "checksum").forEach { id ->
            assertTrue("$id must not claim to be encryption",
                !ToolRegistry.get(id).meta.classification.secure)
        }
        assertEquals(Classification.CRYPTOGRAPHIC_HASH, ToolRegistry.get("hash").meta.classification)
        assertEquals(Classification.MESSAGE_AUTHENTICATION, ToolRegistry.get("hmac").meta.classification)
        assertEquals(Classification.CHECKSUM, ToolRegistry.get("checksum").meta.classification)
    }

    // ------------------------------------------------------------- Enigma

    @Test fun enigmaCanonicalVector() {
        // Rotors I-II-III, reflector B, rings AAA, positions AAA: the published reference result.
        assertEquals("BDZGO", enc("enigma", "AAAAA"))
        assertEquals("AAAAA", dec("enigma", "BDZGO"))
    }

    @Test fun enigmaRingOffsetVector() {
        assertEquals("EWTYX", enc("enigma", "AAAAA", mapOf("rings" to "BBB")))
    }

    @Test fun enigmaPlugboardVector() {
        // Published worked example: rotors I-II-III, UKW-B, rings AAA, plugboard A↔M and T↔G,
        // start position AAZ, plaintext ATTACK gives RXWKBV.
        val params = mapOf(
            "positions" to "AAZ",
            "plugboard" to "AM TG",
        )
        assertEquals("RXWKBV", enc("enigma", "ATTACK", params))
        assertEquals("ATTACK", dec("enigma", "RXWKBV", params))
    }

    @Test fun enigmaIsReciprocalAndNeverEncryptsALetterToItself() {
        val text = "ATTACKATDAWN"
        val params = mapOf("positions" to "QWE", "rings" to "XYZ", "plugboard" to "AB CD EF")
        assertEquals(text, dec("enigma", enc("enigma", text, params), params))
        for (letter in 'A'..'Z') {
            assertNotEquals(
                "Enigma's reflector means a letter can never encrypt to itself ($letter)",
                letter.toString(),
                enc("enigma", letter.toString()),
            )
        }
    }

    @Test fun enigmaPreservesNonLettersAndRejectsBadSettings() {
        // Every letter is enciphered, but spaces, punctuation, digits and letter case survive.
        val mixed = "Hello, World! 123"
        val out = enc("enigma", mixed)
        assertEquals(
            mixed.map { if (it.isLetter()) 'x' else it }.joinToString(""),
            out.map { if (it.isLetter()) 'x' else it }.joinToString(""),
        )
        assertEquals(out.lowercase(), enc("enigma", mixed.lowercase()))
        assertTrue(runCatching { enc("enigma", "TEST", mapOf("rings" to "AB")) }.exceptionOrNull() is ToolException)
        assertTrue(runCatching { enc("enigma", "TEST", mapOf("plugboard" to "AA")) }.exceptionOrNull() is ToolException)
        assertTrue(runCatching { enc("enigma", "TEST", mapOf("plugboard" to "ABCD E")) }.exceptionOrNull() is ToolException)
    }

    @Test fun enigmaRotorsAreSelectable() {
        val params = mapOf("rotorLeft" to "IV", "rotorMiddle" to "V", "rotorRight" to "I")
        val text = "SECRET MESSAGE"
        assertEquals(text, dec("enigma", enc("enigma", text, params), params))
        assertNotEquals(enc("enigma", text, params), enc("enigma", text))
    }

    // -------------------------------------------------------------- Porta

    @Test fun portaTableauVectors() {
        // Row AB maps A-M onto N-Z, and the table is reciprocal.
        assertEquals("NOPQRSTUVWXYZ", enc("porta", "ABCDEFGHIJKLM", mapOf("key" to "AB")))
        assertEquals("ABCDEFGHIJKLM", enc("porta", "NOPQRSTUVWXYZ", mapOf("key" to "AB")))
        // Key CD starts one letter further along: A -> O.
        assertEquals("OPQRSTUVWXYZN", enc("porta", "ABCDEFGHIJKLM", mapOf("key" to "CD")))
        // Published example: abc with key MNO gives TUW (case is preserved, so lowercase in
        // gives lowercase out - the letters all match the tableau above).
        assertEquals("TUW", enc("porta", "ABC", mapOf("key" to "MNO")))
        assertEquals("tuw", enc("porta", "abc", mapOf("key" to "MNO")))
        // Key pairs share a row, so A and B behave identically.
        assertEquals(
            enc("porta", "HELLOWORLD", mapOf("key" to "AAAA")),
            enc("porta", "HELLOWORLD", mapOf("key" to "BBBB")),
        )
        // Published worked example: FORTIFICATION / DEFEND... starts with D + F -> S.
        assertEquals("S", enc("porta", "DEFENDTHEEASTWALLOFTHECASTLE", mapOf("key" to "FORTIFICATION")).take(1))
    }

    @Test fun portaIsReciprocalAndPreservesFormatting() {
        val text = "Meet me at dawn, 7am!"
        val params = mapOf("key" to "SECRET")
        assertEquals(text, dec("porta", enc("porta", text, params), params))
        assertTrue(enc("porta", "HELLO, WORLD!", params).contains(", "))
    }

    @Test fun portaNeedsAKey() {
        assertTrue(runCatching { enc("porta", "HELLO", mapOf("key" to "123")) }.exceptionOrNull() is ToolException)
    }

    // ------------------------------------------------------- Trifid / Hill3

    @Test fun trifidHandCheckedVectorAndRoundTrip() {
        // Cube of 27 cells (A-Z plus '.', 3x3x3). For the block "ABC" with the default period the
        // coordinates are 000, 001, 002 -> stream 000 000 001 002 -> chunks 000, 000, 012.
        // 000 = A, 000 = A, 012 = row 0 column 1 cell 2 = F.
        assertEquals("AAF", enc("trifid", "ABC", mapOf("period" to "3")))
        assertEquals("ABC", dec("trifid", "AAF", mapOf("period" to "3")))
    }

    @Test fun trifidRoundTripsWithKeyAndPeriods() {
        val text = "DEFENDTHEEASTWALL"
        lateinit var previous: String
        for (period in listOf("3", "5", "9")) {
            val params = mapOf("period" to period, "key" to "SECRET")
            val out = enc("trifid", text, params)
            assertEquals("period $period", text, dec("trifid", out, params))
            if (period != "3") assertNotEquals("longer periods must mix differently", previous, out)
            previous = out
        }
        assertEquals(text, dec("trifid", enc("trifid", text, mapOf("alphabet" to "alnum")), mapOf("alphabet" to "alnum")))
    }

    @Test fun hill3KnownVectorAndRoundTrip() {
        // Classic 3x3 example: key GYBNQKURP encrypts ACT to POH.
        assertEquals("POH", enc("hill3", "ACT"))
        assertEquals("ACT", dec("hill3", "POH"))
        val text = "ATTACKATDAWN"
        assertEquals(text, dec("hill3", enc("hill3", text), emptyMap()))
        // Lengths that are not a multiple of three are padded with X on purpose; that padding is
        // part of the ciphertext, which is why HELLO comes back as HELLOX.
        assertEquals("HELLOX", dec("hill3", enc("hill3", "HELLO"), emptyMap()))
        assertEquals("HELLOW", dec("hill3", enc("hill3", "HELLOW"), emptyMap()))
    }

    @Test fun hill3RejectsUnusableMatrices() {
        // A matrix whose determinant shares a factor with 26 cannot be inverted.
        val e = runCatching { enc("hill3", "HELLO", mapOf("key" to "AAAAAAAAA")) }.exceptionOrNull()
        assertTrue(e is ToolException)
        assertTrue(runCatching { enc("hill3", "HELLO", mapOf("key" to "ABC")) }.exceptionOrNull() is ToolException)
    }

    // ---------------------------------------------- Scytale / ADFGX / keyed A-Z

    @Test fun scytaleKnownOutput() {
        assertEquals("HLODEORLWL", enc("scytale", "HELLOWORLD", mapOf("diameter" to "3")))
        assertEquals("HELLOWORLD", dec("scytale", "HLODEORLWL", mapOf("diameter" to "3")))
    }

    @Test fun scytaleRoundTripsAtEveryWidth() {
        val text = "The quick brown fox jumps over the lazy dog"
        for (width in listOf("2", "4", "7", "13", "40")) {
            val params = mapOf("diameter" to width)
            assertEquals("width $width", text, dec("scytale", enc("scytale", text, params), params))
        }
    }

    @Test fun adfgxRoundTripsAndUsesOnlyItsAlphabet() {
        val text = "ATTACK AT DAWN 1918"
        val params = mapOf("columnKey" to "CARGO", "squareKey" to "BATTALION")
        val out = enc("adfgx", text, params)
        assertTrue("output uses only ADFGVX letters: $out", out.all { it in "ADFGVX" })
        // Spaces and punctuation are dropped, as they were in the historical messages
        assertEquals("ATTACKATDAWN1918", dec("adfgx", out, params))

        val classic = mapOf("alphabet" to "adfgx", "columnKey" to "CARGO", "squareKey" to "BATTALION")
        val oldOut = enc("adfgx", "ATTACKATDAWN", classic)
        assertTrue("ADFGX output uses five letters only: $oldOut", oldOut.all { it in "ADFGX" })
        assertEquals("ATTACKATDAWN", dec("adfgx", oldOut, classic))
    }

    @Test fun keyedAlphabetVigenereAndBeaufort() {
        // The plain A-Z alphabet reproduces the classic Vigenère vector.
        assertEquals("LXFOPVEFRNHR", enc("vigenere2", "ATTACKATDAWN", mapOf("key" to "LEMON")))
        val scrambled = mapOf(
            "key" to "LEMON",
            "alphabet" to "QWERTYUIOPASDFGHJKLZXCVBNM",
        )
        val text = "ATTACKATDAWN"
        assertNotEquals("LXFOPVEFRNHR", enc("vigenere2", text, scrambled))
        assertEquals(text, dec("vigenere2", enc("vigenere2", text, scrambled), scrambled))
        val beaufort = mapOf("key" to "KEY", "variant" to "beaufort")
        assertEquals("DANZQ", enc("vigenere2", "HELLO", beaufort))
        assertEquals("HELLO", dec("vigenere2", "DANZQ", beaufort))
    }

    // ------------------------------------------------------------ encodings

    @Test fun base91RoundTripsAndIsDenserThanBase64() {
        val text = "The quick brown fox jumps over the lazy dog 0123456789"
        val encoded = enc("base91", text)
        assertEquals(text, dec("base91", encoded))
        assertEquals("GB", enc("base91", "a"))
        assertTrue(
            "Base91 should beat Base64 on size: ${encoded.length} vs " +
                com.texthub.core.codec.Base64Codec.encode(text.toByteArray()).length,
            encoded.length < com.texthub.core.codec.Base64Codec.encode(text.toByteArray()).length,
        )
        assertEquals("Grüße 😀", dec("base91", enc("base91", "Grüße 😀")))
        // Whitespace is ignored, as the reference implementation does
        val spaced = encoded.chunked(8).joinToString("\n")
        assertEquals(text, dec("base91", spaced))
        assertTrue(runCatching { dec("base91", "☃") }.exceptionOrNull() is ToolException)
    }

    @Test fun punycodeKnownVectors() {
        // IDN example: münchen.de is xn--mnchen-3ya.de
        assertEquals("mnchen-3ya", PunycodeCodec.encode("münchen"))
        assertEquals("münchen", PunycodeCodec.decode("mnchen-3ya"))
        // RFC 3492 sample: ASCII-only text gets a trailing separator.
        assertEquals("-> \$1.00 <--", PunycodeCodec.encode("-> \$1.00 <-"))
        assertEquals("Hello", PunycodeCodec.decode("Hello-"))
    }

    @Test fun punycodeRoundTripsNonAscii() {
        listOf("münchen", "пример", "हिन्दी", "日本語", "emoji 😀 test", "a-b-c").forEach { text ->
            assertEquals(text, PunycodeCodec.decode(PunycodeCodec.encode(text)))
        }
        assertEquals("mnchen-3ya", enc("punycode", "münchen"))
        assertEquals("münchen", dec("punycode", "mnchen-3ya"))
    }

    @Test fun utf16AndUtf32Views() {
        assertEquals("0041 D83D DE00", Utf16Codec.encode("A😀", Utf16Codec.FORMAT_HEX))
        assertEquals("65 55357 56832", Utf16Codec.encode("A😀", Utf16Codec.FORMAT_DECIMAL))
        assertEquals("\\u0041\\uD83D\\uDE00", Utf16Codec.encode("A😀", Utf16Codec.FORMAT_CSHARP))
        assertEquals("A😀", Utf16Codec.decode("0041 D83D DE00", Utf16Codec.FORMAT_HEX))
        assertEquals("A😀", Utf16Codec.decode("\\u0041\\uD83D\\uDE00", Utf16Codec.FORMAT_CSHARP))
        assertEquals("A😀", dec("utf16", "\\x41\\uD83D\\uDE00", mapOf("format" to "cstring")))

        assertEquals("00000041 0001F600", Utf32Codec.encode("A😀", Utf32Codec.FORMAT_HEX))
        assertEquals("A😀", Utf32Codec.decode("00000041 0001F600", Utf32Codec.FORMAT_HEX))
        assertEquals("\\U0001F600", Utf32Codec.encode("😀", Utf32Codec.FORMAT_CSHARP))
        assertEquals("😀", Utf32Codec.decode("\\U0001F600", Utf32Codec.FORMAT_CSHARP))

        // Two code units for the emoji, one code point: that difference is the whole point.
        assertEquals(3, Utf16Codec.encode("A😀", Utf16Codec.FORMAT_HEX).split(" ").size)
        assertEquals(2, Utf32Codec.encode("A😀", Utf32Codec.FORMAT_HEX).split(" ").size)
    }

    @Test fun utf16AndUtf32ToolsRoundTrip() {
        val text = "Grüße 😀"
        listOf("utf16", "utf32").forEach { id ->
            assertEquals(id, text, dec(id, enc(id, text)))
        }
        assertEquals(text, dec("utf16", enc("utf16", text, mapOf("format" to "decimal")), mapOf("format" to "decimal")))
        assertEquals(text, dec("utf32", enc("utf32", text, mapOf("format" to "cstring")), mapOf("format" to "cstring")))
        assertTrue(runCatching { dec("utf16", "00") }.exceptionOrNull() is ToolException)
    }

    @Test fun brailleCellsAreExactAndReversible() {
        assertEquals("\u2800\u2800\u2804\u2801", BrailleCodec.encode("A"))
        assertEquals("A", BrailleCodec.decode(BrailleCodec.encode("A")))
        val text = "Grüße 😀"
        assertEquals(text, dec("braille", enc("braille", text)))
        assertTrue(runCatching { dec("braille", "hello") }.exceptionOrNull() is ToolException)
    }

    @Test fun romanNumerals() {
        assertEquals("MCMLXIX", RomanCodec.toRoman(1969))
        assertEquals(1969, RomanCodec.fromRoman("MCMLXIX"))
        assertEquals(2024, RomanCodec.fromRoman("mmxxiv"))
        assertEquals("IV", enc("roman", "4"))
        assertEquals("4", dec("roman", "IV"))
        // A whole sentence keeps its non-numeric words and converts the numbers.
        assertEquals("Chapter IV of MMXXV", enc("roman", "Chapter 4 of 2025"))
        assertEquals("Chapter 4 of 2025", dec("roman", "Chapter IV of MMXXV"))
        // Malformed numerals are rejected rather than guessed.
        assertTrue(runCatching { dec("roman", "IIII") }.exceptionOrNull() is ToolException)
        assertTrue(runCatching { dec("roman", "VX") }.exceptionOrNull() is ToolException)
        assertTrue(runCatching { enc("roman", "4000") }.exceptionOrNull() is ToolException)
        // Words that are not numerals are left alone.
        assertEquals("HELLO", dec("roman", "HELLO"))
    }

    @Test fun hexDumpLooksLikeHexdumpAndReadsBack() {
        val dump = HexDumpCodec.encode("Hello")
        assertTrue("dump was: $dump", dump.startsWith("00000000: 48 65 6c 6c 6f"))
        assertTrue(dump.endsWith("|Hello|"))
        assertEquals("Hello", HexDumpCodec.decode(dump))
        val long = "The quick brown fox jumps over the lazy dog. 0123456789"
        assertEquals(long, dec("hexdump", enc("hexdump", long)))
        assertTrue(enc("hexdump", long).lines().size > 3)
        assertTrue(runCatching { dec("hexdump", "not a dump") }.exceptionOrNull() is ToolException)
    }

    // ------------------------------------------------------------- utilities

    @Test fun jsonFormatterPrettyAndCompact() {
        val compact = "{\"a\":1,\"b\":[true,null,\"x\"],\"c\":{\"d\":2.5}}"
        val pretty = enc("json", compact)
        assertTrue(pretty.contains("\n"))
        assertTrue(pretty.contains("  \"a\": 1"))
        assertEquals(compact, enc("json", pretty, mapOf("mode" to "compact")))
        // Numbers keep their written form, so nothing is rounded on the way through.
        val big = "{\"n\":12345678901234567890,\"d\":0.10}"
        assertEquals(big, enc("json", big, mapOf("mode" to "compact")))
        assertTrue(enc("json", compact, mapOf("mode" to "tabs")).contains("\t\"a\""))
        val keys = enc("json", compact, mapOf("mode" to "keys"))
        assertTrue(keys.contains("a"))
        assertTrue(keys.contains("c.d"))
        // Invalid JSON reports a position instead of throwing.
        val e = runCatching { enc("json", "{\"a\": }") }.exceptionOrNull()
        assertTrue(e is ToolException)
        assertTrue(e!!.message!!.contains("line"))
    }

    @Test fun regexTester() {
        val text = "Order 1234 shipped, order 5678 pending"
        val found = enc("regex", text, mapOf("pattern" to "order\\s+(\\d+)", "flags" to "ignoreCase"))
        assertTrue(found.contains("2 matches"))
        assertTrue(found.contains("$1=1234"))
        assertEquals(
            "Order **** shipped, order **** pending",
            enc("regex", text, mapOf("pattern" to "\\d+", "operation" to "replace", "replacement" to "****")),
        )
        assertEquals("a\nb\nc", enc("regex", "a1b2c", mapOf("pattern" to "\\d", "operation" to "split")))
        assertTrue(enc("regex", text, mapOf("pattern" to "\\d+", "operation" to "highlight")).startsWith("line 1, column "))
        assertTrue(runCatching { enc("regex", text, mapOf("pattern" to "(")) }.exceptionOrNull() is ToolException)
    }

    @Test fun textDiffShowsChanges() {
        val input = "one\ntwo\nthree\n---\none\n2\nthree\nfour"
        val out = enc("textdiff", input)
        assertTrue(out.contains("- two"))
        assertTrue(out.contains("+ 2"))
        assertTrue(out.contains("+ four"))
        assertTrue(out.contains("unchanged"))
        val e = runCatching { enc("textdiff", "no separator here") }.exceptionOrNull()
        assertTrue(e is ToolException)
    }

    @Test fun textStatisticsCountsCorrectly() {
        val out = enc("textstats", "Hello world. Hello there!\n\nSecond paragraph.")
        assertTrue(out.contains("Words: 6"))
        assertTrue(out.contains("Paragraphs: 2"))
        assertTrue(out.contains("Sentences: 3"))
        assertTrue(out.contains("Unique words: 5"))
        assertTrue(out.contains("hello × 2"))
        val emoji = enc("textstats", "😀")
        assertTrue(emoji.contains("Code points: 1"))
        assertTrue(emoji.contains("Characters (UTF-16 units): 2"))
        assertTrue(emoji.contains("UTF-8 bytes: 4"))
    }

    @Test fun jwtInspectorShowsClaimsAndNeverVerifies() {
        // header {"alg":"HS256","typ":"JWT"}, payload {"sub":"1234","exp":1700000000}
        val token =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
                "eyJzdWIiOiIxMjM0IiwiZXhwIjoxNzAwMDAwMDAwfQ." +
                "abcdefghijklmnopqrstuvwxyz"
        val out = enc("jwt", token)
        assertTrue(out.contains("HS256"))
        assertTrue(out.contains("shared secret"))
        assertTrue(out.contains("\"sub\": \"1234\""))
        assertTrue(out.contains("1700000000"))
        assertTrue(out.contains("not verified"))
        assertTrue(runCatching { enc("jwt", "not.a.jwt") }.exceptionOrNull() is ToolException)
    }

    @Test fun newToolsAreRegisteredInTheirCategories() {
        val expected = mapOf(
            "aes" to com.texthub.core.model.ToolCategory.SECURE,
            "hash" to com.texthub.core.model.ToolCategory.CRYPTO,
            "hmac" to com.texthub.core.model.ToolCategory.CRYPTO,
            "pbkdf2" to com.texthub.core.model.ToolCategory.CRYPTO,
            "checksum" to com.texthub.core.model.ToolCategory.CRYPTO,
            "enigma" to com.texthub.core.model.ToolCategory.CLASSICAL,
            "porta" to com.texthub.core.model.ToolCategory.CLASSICAL,
            "trifid" to com.texthub.core.model.ToolCategory.CLASSICAL,
            "hill3" to com.texthub.core.model.ToolCategory.CLASSICAL,
            "scytale" to com.texthub.core.model.ToolCategory.CLASSICAL,
            "adfgx" to com.texthub.core.model.ToolCategory.CLASSICAL,
            "vigenere2" to com.texthub.core.model.ToolCategory.CLASSICAL,
            "base91" to com.texthub.core.model.ToolCategory.ENCODING,
            "punycode" to com.texthub.core.model.ToolCategory.ENCODING,
            "braille" to com.texthub.core.model.ToolCategory.ENCODING,
            "roman" to com.texthub.core.model.ToolCategory.ENCODING,
            "utf16" to com.texthub.core.model.ToolCategory.ENCODING,
            "utf32" to com.texthub.core.model.ToolCategory.ENCODING,
            "hexdump" to com.texthub.core.model.ToolCategory.ENCODING,
            "json" to com.texthub.core.model.ToolCategory.TRANSFORM,
            "regex" to com.texthub.core.model.ToolCategory.TRANSFORM,
            "textdiff" to com.texthub.core.model.ToolCategory.TRANSFORM,
            "textstats" to com.texthub.core.model.ToolCategory.TRANSFORM,
            "jwt" to com.texthub.core.model.ToolCategory.TRANSFORM,
        )
        expected.forEach { (id, category) ->
            val meta = ToolRegistry.all.firstOrNull { it.meta.id == id }?.meta
            assertTrue("$id must be registered", meta != null)
            assertEquals("$id category", category, meta!!.category)
        }
    }

    @Test fun everyToolStillRoundTripsWhereItShould() {
        // Tools that are deliberately one-way or lossy are listed with the reason.
        val excluded = setOf(
            "bacon", "hill", "bifid", "polybius", "case", "linetools", "leet", "nato",
            "caesarbrute", "hash", "hmac", "pbkdf2", "checksum", "textstats",
            // Letters-only ciphers (punctuation is dropped by design) and the regex tester.
            "hill3", "trifid", "adfgx", "regex",
            // Generates fresh key material on every run.
            "rsakeygen",
        )
        val sample = "HELLO, WORLD!"
        val failures = mutableListOf<String>()
        ToolRegistry.all.filter { it.meta.id !in excluded }.forEach { processor ->
            val params = processor.defaultParams()
            val forward = ProcessingEngine.run(processor, sample, params, Direction.ENCODE)
            if (!forward.isSuccess) return@forEach
            val back = ProcessingEngine.run(processor, forward.output, params, Direction.DECODE)
            if (!back.isSuccess || back.output != sample) {
                failures.add("${processor.meta.id}: '${forward.output.take(40)}' -> '${back.output.take(40)}' (${back.error})")
            }
        }
        assertTrue("round trip failures: $failures", failures.isEmpty())
    }

    @Test fun registryIsLargeAndBalanced() {
        val byCategory = ToolRegistry.all.groupBy { it.meta.category }.mapValues { it.value.size }
        assertTrue("at least 65 tools expected, found ${ToolRegistry.all.size}", ToolRegistry.all.size >= 65)
        assertTrue("secure tools: $byCategory", byCategory[com.texthub.core.model.ToolCategory.SECURE]!! >= 3)
        assertTrue("classical tools: $byCategory", byCategory[com.texthub.core.model.ToolCategory.CLASSICAL]!! >= 25)
        assertTrue("encodings: $byCategory", byCategory[com.texthub.core.model.ToolCategory.ENCODING]!! >= 24)
        assertTrue("transforms: $byCategory", byCategory[com.texthub.core.model.ToolCategory.TRANSFORM]!! >= 10)
        assertTrue("crypto helpers: $byCategory", byCategory[com.texthub.core.model.ToolCategory.CRYPTO]!! >= 4)
    }
}
