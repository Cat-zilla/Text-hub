package com.texthub.core

import com.texthub.core.codec.Base45Codec
import com.texthub.core.codec.Base32Codec
import com.texthub.core.codec.HtmlEntityCodec
import com.texthub.core.codec.QuotedPrintableCodec
import com.texthub.core.codec.UnicodeEscapeCodec
import com.texthub.core.model.Direction
import com.texthub.core.model.ToolException
import com.texthub.core.util.utf8Bytes
import com.texthub.core.util.utf8String
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for the second wave of tools: more ciphers, more encodings, more encryption. */
class NewToolsTest {

    private fun enc(id: String, text: String, params: Map<String, String> = emptyMap()): String {
        val p = ToolRegistry.get(id)
        return p.process(text, p.defaultParams() + params, Direction.ENCODE)
    }

    private fun dec(id: String, text: String, params: Map<String, String> = emptyMap()): String {
        val p = ToolRegistry.get(id)
        return p.process(text, p.defaultParams() + params, Direction.DECODE)
    }

    // ----------------------------------------------------------- Base45 / Base32

    @Test fun base45RfcVector() {
        // RFC 9285 example: "Hello!!" -> "%69 VD92EX0"
        assertEquals("%69 VD92EX0", Base45Codec.encode("Hello!!".utf8Bytes()))
        assertEquals("Hello!!", Base45Codec.decode("%69 VD92EX0").utf8String())
    }

    @Test fun base45ShortInput() {
        assertEquals("BB8", Base45Codec.encode("AB".utf8Bytes()))
        assertEquals("AB", Base45Codec.decode("BB8").utf8String())
        assertEquals("AB", dec("base45", enc("base45", "AB")))
    }

    @Test fun base45UnicodeRoundTrip() {
        val text = "Grüße 😀"
        assertEquals(text, dec("base45", enc("base45", text)))
    }

    @Test fun base45Invalid() {
        // Lower case is not part of the alphabet, and a lone final character is not valid Base45
        assertTrue(runCatching { dec("base45", "abc") }.exceptionOrNull() is ToolException)
        assertTrue(runCatching { dec("base45", "A") }.exceptionOrNull() is ToolException)
    }

    @Test fun base32Crockford() {
        val text = "Crockford base32"
        val encoded = enc("base32", text, mapOf("variant" to "crockford"))
        assertEquals(text, dec("base32", encoded, mapOf("variant" to "crockford")))
        // The Crockford alphabet leaves out I, L, O and U so they cannot be confused with digits
        assertTrue(encoded.none { it == 'I' || it == 'L' || it == 'O' || it == 'U' })
        assertEquals("foobar", Base32Codec.decode("CSQPYRK1E8======", Base32Codec.VARIANT_CROCKFORD).utf8String())
        // ...and when decoding, the letters people type by mistake are accepted: I/L read as 1, O as 0
        assertEquals("foobar", Base32Codec.decode("CSQPYRKIE8", Base32Codec.VARIANT_CROCKFORD).utf8String())
        assertEquals("foobar", Base32Codec.decode("CSQPYRKLE8", Base32Codec.VARIANT_CROCKFORD).utf8String())
        // Hyphens and lower case are tolerated too, as the Crockford spec suggests
        assertEquals("foobar", Base32Codec.decode("csqpy-rk1e8", Base32Codec.VARIANT_CROCKFORD).utf8String())
    }

    // ---------------------------------------------------- Quoted-printable / HTML

    @Test fun quotedPrintableBasics() {
        assertEquals("Hello=3DWorld", QuotedPrintableCodec.encode("Hello=World"))
        assertEquals("Hello=World", QuotedPrintableCodec.decode("Hello=3DWorld"))
        assertEquals("Gr=C3=BC=C3=9Fe", QuotedPrintableCodec.encode("Grüße"))
        assertEquals("Grüße", QuotedPrintableCodec.decode("Gr=C3=BC=C3=9Fe"))
    }

    @Test fun quotedPrintableSoftBreaksAndRoundTrip() {
        val long = "The quick brown fox jumps over the lazy dog, and then keeps going for a while " +
            "so that the encoder has to insert a soft line break somewhere in this text."
        val encoded = QuotedPrintableCodec.encode(long)
        assertTrue("long lines must be soft wrapped", encoded.contains("=\n"))
        assertEquals(long, QuotedPrintableCodec.decode(encoded))
        val unicode = "Ünïcödé — naïve café 😀"
        assertEquals(unicode, dec("quotedprintable", enc("quotedprintable", unicode)))
    }

    @Test fun quotedPrintableInvalid() {
        assertTrue(runCatching { dec("quotedprintable", "abc=ZZ") }.exceptionOrNull() is ToolException)
    }

    @Test fun htmlEntitiesRoundTrip() {
        val text = "Tom & Jerry <b>\"bold\"</b> — café © 😀"
        val encoded = HtmlEntityCodec.encode(text, HtmlEntityCodec.MODE_ALL)
        assertTrue(encoded.contains("&amp;"))
        assertTrue(encoded.contains("&lt;"))
        assertTrue(encoded.contains("&quot;"))
        assertTrue(encoded.contains("&mdash;"))
        assertTrue(encoded.contains("&#233;"))
        assertTrue(encoded.contains("&#128512;"))
        assertEquals(text, HtmlEntityCodec.decode(encoded))
    }

    @Test fun htmlEntitiesMinimalMode() {
        assertEquals("&amp;&lt;&gt;&quot;", HtmlEntityCodec.encode("&<>\"", HtmlEntityCodec.MODE_MINIMAL))
        assertEquals("&#233;", HtmlEntityCodec.encode("é", HtmlEntityCodec.MODE_MINIMAL))
    }

    @Test fun htmlEntitiesDecodeForms() {
        assertEquals("Aé😀", HtmlEntityCodec.decode("A&#233;&#x1F600;"))
        assertEquals("© ® ™", HtmlEntityCodec.decode("&copy; &reg; &trade;"))
        assertEquals("100% & more", HtmlEntityCodec.decode("100% &amp; more"))
        // Unknown entities survive untouched instead of being lost.
        assertEquals("&notanentity;", HtmlEntityCodec.decode("&notanentity;"))
    }

    // ------------------------------------------------------------ Unicode escapes

    @Test fun unicodeEscapesRoundTrip() {
        val text = "A→😀 \\n"
        for (mode in listOf("js", "braced", "python")) {
            val encoded = UnicodeEscapeCodec.encode(text, mode)
            assertEquals("mode $mode", text, UnicodeEscapeCodec.decode(encoded))
        }
    }

    @Test fun unicodeEscapeForms() {
        assertTrue(UnicodeEscapeCodec.encode("😀", "js").contains("\\uD83D\\uDE00"))
        assertTrue(UnicodeEscapeCodec.encode("😀", "braced").contains("\\u{1F600}"))
        assertTrue(UnicodeEscapeCodec.encode("😀", "python").contains("\\U0001F600"))
        assertEquals("A", UnicodeEscapeCodec.decode("\\u0041"))
        assertEquals("A", UnicodeEscapeCodec.decode("\\x41"))
        assertEquals("😀", UnicodeEscapeCodec.decode("\\uD83D\\uDE00"))
        assertEquals("😀", UnicodeEscapeCodec.decode("\\u{1F600}"))
        assertEquals("😀", UnicodeEscapeCodec.decode("\\U0001F600"))
        assertEquals("line\nbreak", UnicodeEscapeCodec.decode("line\\nbreak"))
    }

    @Test fun unicodeEscapeInvalid() {
        assertTrue(runCatching { dec("unicodeescape", "\\uZZZZ") }.exceptionOrNull() is ToolException)
        assertTrue(runCatching { dec("unicodeescape", "\\u{110000}") }.exceptionOrNull() is ToolException)
    }

    // ------------------------------------------------------- NATO / leet / case

    @Test fun natoPhonetic() {
        assertEquals("Hotel Echo Lima Lima Oscar", enc("nato", "HELLO"))
        assertEquals("HELLO", dec("nato", "Hotel Echo Lima Lima Oscar"))
        assertEquals("One Two Three", enc("nato", "123"))
        assertEquals("123", dec("nato", "One Two Three"))
        assertEquals("Alfa Bravo", enc("nato", "ab"))
        assertEquals("Able Baker", enc("nato", "ab", mapOf("alphabet" to "old")))
        assertEquals("AB", dec("nato", "Able Baker", mapOf("alphabet" to "old")))
        assertTrue(runCatching { dec("nato", "Banana") }.exceptionOrNull() is ToolException)
    }

    @Test fun leetspeak() {
        assertEquals("H3ll0", enc("leet", "Hello"))
        assertEquals("h3ll0 w0rld", enc("leet", "hello world"))
        // Heavy mode also replaces B and G; 'l' is always a plain letter because it is not a digit
        assertEquals("83110", enc("leet", "Bello", mapOf("level" to "heavy")))
        // Light mode keeps the confusable replacements (1, 8, 6 and 2) out of the way
        assertEquals("B3ll0", enc("leet", "Bello", mapOf("level" to "light")))
        // i and l share the digit 1, so decoding maps every 1 back to the first of the two, 'i'
        assertEquals("heiio", dec("leet", "he11o"))
        assertEquals("eate", dec("leet", "3473"))
    }

    @Test fun caseConverterStyles() {
        assertEquals("HELLO WORLD", enc("case", "hello world", mapOf("mode" to "upper")))
        assertEquals("hello world", enc("case", "HELLO WORLD", mapOf("mode" to "lower")))
        assertEquals("Hello World", enc("case", "hello world", mapOf("mode" to "title")))
        assertEquals("Hello world. Next one.", enc("case", "hello world. next one.", mapOf("mode" to "sentence")))
        assertEquals("hELLO wORLD", enc("case", "Hello World", mapOf("mode" to "toggle")))
        assertEquals("helloWorld", enc("case", "Hello World", mapOf("mode" to "camel")))
        assertEquals("HelloWorld", enc("case", "hello world", mapOf("mode" to "pascal")))
        assertEquals("hello_world", enc("case", "Hello World", mapOf("mode" to "snake")))
        assertEquals("hello-world", enc("case", "Hello World", mapOf("mode" to "kebab")))
        assertEquals("HELLO_WORLD", enc("case", "hello world", mapOf("mode" to "constant")))
        assertEquals("hello.world", enc("case", "Hello World", mapOf("mode" to "dot")))
    }

    @Test fun lineTools() {
        val list = "banana\nApple\ncherry\nApple\n\n"
        // Sorting is case insensitive by default and blank lines stay at the bottom
        assertEquals("Apple\nApple\nbanana\ncherry\n\n", enc("linetools", list, mapOf("operation" to "sortAsc")))
        assertEquals("cherry\nbanana\nApple\nApple\n\n", enc("linetools", list, mapOf("operation" to "sortDesc")))
        // Shortest first (Apple 5, banana 6, cherry 6) with blank lines kept at the bottom
        assertEquals("Apple\nApple\nbanana\ncherry\n\n", enc("linetools", list, mapOf("operation" to "sortLength")))
        // Case sensitive sorting puts capitals first, as a plain ASCII sort does
        assertEquals(
            "Apple\nApple\nbanana\ncherry\n\n",
            enc("linetools", list, mapOf("operation" to "sortAsc", "caseSensitive" to "sensitive")),
        )
        assertEquals("banana\nApple\ncherry\n", enc("linetools", list, mapOf("operation" to "dedupe")))
        assertEquals("1. a\n2. b\n3. c", enc("linetools", "a\nb\nc", mapOf("operation" to "number")))
        // Numeric sort reads the values, so 2 comes before 10
        assertEquals("2\n10\n33\n", enc("linetools", "33\n2\n10\n", mapOf("operation" to "numeric")))
        assertEquals("a b c", enc("linetools", "a\nb\nc", mapOf("operation" to "join")))
        assertEquals("cherry\nApple\nbanana", enc("linetools", "banana\nApple\ncherry", mapOf("operation" to "reverse")))
    }

    // ------------------------------------------------------------ extra ciphers

    @Test fun beaufort() {
        // Reciprocal: the same operation encrypts and decrypts.
        assertEquals("DANZQ", enc("beaufort", "HELLO", mapOf("key" to "KEY")))
        assertEquals("HELLO", dec("beaufort", "DANZQ", mapOf("key" to "KEY")))
        val text = "Meet me at dawn, 7am!"
        val params = mapOf("key" to "lemon")
        assertEquals(text, dec("beaufort", enc("beaufort", text, params), params))
    }

    @Test fun beaufortVariantForm() {
        // Variant Beaufort is C = P - K, so H-K = X, E-E = A, L-Y = N, L-K = B, O-E = K
        val params = mapOf("key" to "KEY", "variant" to "variant")
        assertEquals("XANBK", enc("beaufort", "HELLO", params))
        assertEquals("HELLO", dec("beaufort", "XANBK", params))
    }

    @Test fun autokeyKnownVector() {
        assertEquals("QNXEPVYTWTWP", enc("autokey", "ATTACKATDAWN", mapOf("key" to "QUEENLY")))
        assertEquals("ATTACKATDAWN", dec("autokey", "QNXEPVYTWTWP", mapOf("key" to "QUEENLY")))
    }

    @Test fun autokeyPreservesPunctuationAndCase() {
        val text = "Attack at dawn!"
        val params = mapOf("key" to "queenly")
        assertEquals(text, dec("autokey", enc("autokey", text, params), params))
    }

    @Test fun gronsfeld() {
        // H+3=K, E+1=F, L+4=P, L+1=M, O+5=T
        assertEquals("KFPMT", enc("gronsfeld", "HELLO", mapOf("key" to "31415")))
        assertEquals("HELLO", dec("gronsfeld", "KFPMT", mapOf("key" to "31415")))
        assertTrue(runCatching { enc("gronsfeld", "HELLO", mapOf("key" to "")) }.exceptionOrNull() is ToolException)
    }

    @Test fun hillCipher() {
        // Key HILL = [[7,8],[11,11]], determinant 15 (coprime with 26).
        assertEquals("JJ", enc("hill", "HI", mapOf("key" to "HILL")))
        assertEquals("HI", dec("hill", "JJ", mapOf("key" to "HILL")))
        val text = "MeetMeAtDawn"
        val params = mapOf("key" to "HILL")
        assertEquals(text.uppercase(), dec("hill", enc("hill", text, params), params))
    }

    @Test fun hillCipherPaddingAndValidation() {
        // Odd length is padded with X and the padding is deliberate, so it survives the trip.
        val params = mapOf("key" to "HILL", "padding" to "pad")
        assertEquals("HELLOX", dec("hill", enc("hill", "HELLO", params), params))
        assertTrue(runCatching { enc("hill", "HELLO", mapOf("key" to "HILL", "padding" to "none")) }
            .exceptionOrNull() is ToolException)
        // [[2,4],[6,8]] has determinant 8*2-4*6 = -8 -> shares a factor with 26
        val e = runCatching { enc("hill", "HELLO", mapOf("key" to "BEBE")) }.exceptionOrNull()
        assertTrue(e is ToolException)
        assertTrue(e!!.message!!.contains("determinant"))
        assertTrue(runCatching { enc("hill", "HELLO", mapOf("key" to "ABC")) }.exceptionOrNull() is ToolException)
    }

    @Test fun bifid() {
        // Standard square, period >= 2: H=(2,3) I=(2,4) -> rows 2,2 cols 3,4 -> pairs (2,2)=G (3,4)=O
        assertEquals("GO", enc("bifid", "HI", mapOf("period" to "5")))
        assertEquals("HI", dec("bifid", "GO", mapOf("period" to "5")))
        val text = "DEFENDTHEEASTWALL"
        val params = mapOf("period" to "5")
        assertEquals(text, dec("bifid", enc("bifid", text, params), params))
        val keyed = mapOf("period" to "7", "key" to "SECRET")
        assertEquals(text, dec("bifid", enc("bifid", text, keyed), keyed))
    }

    @Test fun polybius() {
        // A=11 B=12 ... H=23 I=24 K=25
        assertEquals("11 12 13", enc("polybius", "ABC"))
        assertEquals("23 24", enc("polybius", "HI"))
        assertEquals("ABC", dec("polybius", "11 12 13"))
        assertEquals("HI", dec("polybius", "23 24"))
        // J is folded into I in the 5x5 grid
        assertEquals("24", enc("polybius", "J"))
        assertEquals("111213", enc("polybius", "ABC", mapOf("separator" to "none")))
        assertEquals("ABC", dec("polybius", "11-12-13", mapOf("separator" to "dash")))
        // The 6x6 grid maps '1' to row 5 column 4
        assertEquals("11 54", enc("polybius", "A1", mapOf("grid" to "alnum")))
        assertEquals("A1", dec("polybius", "11 54", mapOf("grid" to "alnum")))
        // Words survive the trip through the slash separator
        val both = mapOf("grid" to "5")
        assertEquals("HELLO WORLD", dec("polybius", enc("polybius", "HELLO WORLD", both), both))
        assertEquals("23 15 31 31 34 / 52 34 42 31 14", enc("polybius", "HELLO WORLD"))
        assertTrue(runCatching { dec("polybius", "11 99") }.exceptionOrNull() is ToolException)
        assertTrue(runCatching { dec("polybius", "111") }.exceptionOrNull() is ToolException)
    }

    @Test fun caesarBruteForce() {
        val out = enc("caesarbrute", "Khoor")
        val lines = out.lines()
        assertEquals(25, lines.size)
        assertTrue("shift 3 must show the plaintext", lines.contains(" 3: Hello"))
        // Wrapping is handled in both directions
        assertEquals(" 1: Zbssb", enc("caesarbrute", "Acttc").lines().first())
    }

    // --------------------------------------------------------------- more crypto

    @Test fun aesCbcHmacRoundTrip() {
        val text = "The quick brown fox jumps over the lazy dog"
        val payload = enc("aescbc", text, mapOf("password" to "correct horse"))
        assertEquals(text, dec("aescbc", payload, mapOf("password" to "correct horse")))
    }

    @Test fun aesCbcUnicodeAndEmpty() {
        val text = "Grüße — नमस्ते 😀"
        assertEquals(text, dec("aescbc", enc("aescbc", text, mapOf("password" to "pw")), mapOf("password" to "pw")))
        assertTrue(runCatching { enc("aescbc", "", mapOf("password" to "pw")) }.exceptionOrNull() is ToolException)
        assertTrue(runCatching { enc("aescbc", "x", mapOf("password" to "")) }.exceptionOrNull() is ToolException)
    }

    @Test fun aesCbcFreshSaltAndTamperDetection() {
        val a = enc("aescbc", "same message", mapOf("password" to "pw"))
        val b = enc("aescbc", "same message", mapOf("password" to "pw"))
        assertTrue("fresh salt/IV must change the payload", a != b)

        val bytes = com.texthub.core.codec.Base64Codec.decode(a)
        assertTrue(runCatching { dec("aescbc", com.texthub.core.codec.Base64Codec.encode(bytes.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }), mapOf("password" to "pw")) }
            .exceptionOrNull() is ToolException)
        // Flipping a ciphertext byte must also fail, because the tag covers the ciphertext.
        val tampered = bytes.copyOf().also { it[40] = (it[40] + 1).toByte() }
        assertTrue(runCatching { dec("aescbc", com.texthub.core.codec.Base64Codec.encode(tampered), mapOf("password" to "pw")) }
            .exceptionOrNull() is ToolException)
        assertTrue(runCatching { dec("aescbc", a, mapOf("password" to "wrong")) }.exceptionOrNull() is ToolException)
    }

    @Test fun aesCbcPayloadLayout() {
        val payload = com.texthub.core.codec.Base64Codec.decode(enc("aescbc", "hello", mapOf("password" to "pw")))
        assertEquals(0x02.toByte(), payload[0])
        assertEquals(0x02.toByte(), payload[1]) // KDF id: 256-bit key recorded
        // 2 + 16 salt + 16 IV + 16 bytes (5 plaintext padded to a block) + 32 byte tag
        assertEquals(2 + 16 + 16 + 16 + 32, payload.size)
    }

    @Test fun chachaRoundTripAndTamper() {
        if (!com.texthub.core.crypto.ChaCha20Poly1305.isAvailable()) {
            // On platforms without the provider the tool must fail with a clear message.
            val e = runCatching { enc("chacha", "text", mapOf("password" to "pw")) }.exceptionOrNull()
            assertTrue(e is ToolException)
            assertTrue(e!!.message!!.contains("not available"))
            return
        }
        val text = "ChaCha20-Poly1305 round trip 🎯"
        val payload = enc("chacha", text, mapOf("password" to "pw"))
        assertEquals(text, dec("chacha", payload, mapOf("password" to "pw")))
        assertTrue(runCatching { dec("chacha", payload, mapOf("password" to "nope")) }.exceptionOrNull() is ToolException)

        val bytes = com.texthub.core.codec.Base64Codec.decode(payload)
        val tampered = bytes.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertTrue(runCatching { dec("chacha", com.texthub.core.codec.Base64Codec.encode(tampered), mapOf("password" to "pw")) }
            .exceptionOrNull() is ToolException)

        val again = enc("chacha", text, mapOf("password" to "pw"))
        assertTrue(again != payload)
    }

    // ------------------------------------------------------------- registry meta

    @Test fun newToolsAreRegisteredAndClassified() {
        val expectedIds = listOf(
            "aescbc", "chacha", "beaufort", "autokey", "gronsfeld", "hill", "bifid", "polybius",
            "caesarbrute", "base45", "quotedprintable", "htmlentities", "unicodeescape", "nato",
            "case", "linetools", "leet",
        )
        val ids = ToolRegistry.all.map { it.meta.id }
        expectedIds.forEach { assertTrue("$it must be registered", it in ids) }
    }

    @Test fun secureToolsAreOnlyAuthenticatedOnes() {
        val secure = ToolRegistry.all.filter { it.meta.classification.secure }.map { it.meta.id }.toSet()
        // Key sizes are settings on the AES tools, not separate tools.
        assertEquals(setOf("aes", "aescbc", "chacha", "aesctr", "aesrawkey", "rsa"), secure)
    }

    @Test fun lettersOnlyCiphersRoundTripAlphabetInput() {
        // Hill, Bifid and Polybius work on letters only, so they are checked with letters only.
        val sample = "DEFENDTHEEASTWAL"
        listOf("hill", "bifid", "polybius").forEach { id ->
            val params = ToolRegistry.get(id).defaultParams()
            assertEquals(id, sample, dec(id, enc(id, sample, params), params))
        }
    }

    @Test fun newToolsRoundTripUnicodeWhereMeaningful() {
        val sample = "Grüße 😀"
        val ids = listOf("base45", "quotedprintable", "htmlentities", "unicodeescape", "aescbc")
        val failures = mutableListOf<String>()
        ids.forEach { id ->
            val params = if (id == "aescbc") mapOf("password" to "pw") else emptyMap()
            val forward = enc(id, sample, params)
            val back = dec(id, forward, params)
            if (back != sample) failures.add("$id: '$back'")
        }
        assertTrue("unicode round trip failures: $failures", failures.isEmpty())
    }
}
