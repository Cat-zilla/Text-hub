package com.texthub.core

import com.texthub.core.model.Direction
import com.texthub.core.model.ToolException
import com.texthub.core.processors.AsciiProcessor
import com.texthub.core.processors.BinaryProcessor
import com.texthub.core.processors.DecimalProcessor
import com.texthub.core.processors.HexProcessor
import com.texthub.core.processors.OctalProcessor
import com.texthub.core.processors.UnicodeProcessor
import com.texthub.core.processors.UrlProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextEncodingsTest {

    private val url = UrlProcessor()
    private val hex = HexProcessor()
    private val binary = BinaryProcessor()
    private val ascii = AsciiProcessor()
    private val decimal = DecimalProcessor()
    private val octal = OctalProcessor()
    private val unicode = UnicodeProcessor()

    private fun enc(p: TextProcessor, text: String, params: Map<String, String> = emptyMap()) =
        p.process(text, p.defaultParams() + params, Direction.ENCODE)

    private fun dec(p: TextProcessor, text: String, params: Map<String, String> = emptyMap()) =
        p.process(text, p.defaultParams() + params, Direction.DECODE)

    // ------------------------------------------------------------------- URL

    @Test fun url_spaces() {
        assertEquals("Hello%20World", enc(url, "Hello World"))
        assertEquals("Hello World", dec(url, "Hello%20World"))
    }

    @Test fun url_specialCharacters() {
        val text = "a+b=c&d?e#f/g~h_i.j-k*l'm(n)"
        assertEquals(text, dec(url, enc(url, text)))
        assertEquals("100%25%20%26%20more", enc(url, "100% & more"))
    }

    @Test fun url_unicode() {
        assertEquals("%C3%A9", enc(url, "é"))
        assertEquals("%E2%86%92", enc(url, "→"))
        assertEquals("é → 😀", dec(url, enc(url, "é → 😀")))
    }

    @Test fun url_formVariant() {
        assertEquals("Hello+World%21", enc(url, "Hello World!", mapOf("variant" to "form")))
        assertEquals("Hello World!", dec(url, "Hello+World%21", mapOf("variant" to "form")))
    }

    @Test fun url_invalidEscape() {
        assertTrue(runCatching { dec(url, "Hello%2") }.exceptionOrNull() is ToolException)
        assertTrue(runCatching { dec(url, "Hello%ZZ") }.exceptionOrNull() is ToolException)
    }

    // ------------------------------------------------------------------- Hex

    @Test fun hex_ascii() {
        assertEquals("48656C6C6F", enc(hex, "Hello", mapOf("format" to "continuous")))
        assertEquals("48 65 6C 6C 6F", enc(hex, "Hello"))
        assertEquals("Hello", dec(hex, "48 65 6C 6C 6F"))
        assertEquals("Hello", dec(hex, "48656c6c6f"))
    }

    @Test fun hex_unicode() {
        val text = "→😀"
        val encoded = enc(hex, text, mapOf("format" to "continuous"))
        assertEquals("E28692F09F9880", encoded)
        assertEquals(text, dec(hex, encoded))
    }

    @Test fun hex_invalid() {
        assertTrue(runCatching { dec(hex, "4G") }.exceptionOrNull() is ToolException)
        assertTrue(runCatching { dec(hex, "486") }.exceptionOrNull() is ToolException)
    }

    // ---------------------------------------------------------------- Binary

    @Test fun binary_ascii() {
        assertEquals("01001000 01100101", enc(binary, "He"))
        assertEquals("He", dec(binary, "01001000 01100101"))
        assertEquals("A", enc(binary, "A").let { dec(binary, "01000001") })
    }

    @Test fun binary_unicodeUsesUtf8Bytes() {
        val text = "é"
        assertEquals("11000011 10101001", enc(binary, text))
        assertEquals(text, dec(binary, enc(binary, text)))
    }

    @Test fun binary_continuous() {
        assertEquals("0100100001100101", enc(binary, "He", mapOf("format" to "continuous")))
        assertEquals("He", dec(binary, "0100100001100101"))
    }

    @Test fun binary_invalid() {
        assertTrue(runCatching { dec(binary, "0100100A") }.exceptionOrNull() is ToolException)
        assertTrue(runCatching { dec(binary, "0100100") }.exceptionOrNull() is ToolException)
    }

    // ----------------------------------------------------------------- ASCII

    @Test fun ascii_knownValues() {
        assertEquals("65", enc(ascii, "A"))
        assertEquals("72 105", enc(ascii, "Hi"))
        assertEquals("Hi", dec(ascii, "72 105"))
    }

    @Test fun ascii_modes() {
        assertEquals("01000001", enc(ascii, "A", mapOf("mode" to "binary")))
        assertEquals("41", enc(ascii, "A", mapOf("mode" to "hex")))
        assertEquals("A", dec(ascii, "01000001", mapOf("mode" to "binary")))
        assertEquals("A", dec(ascii, "41", mapOf("mode" to "hex")))
    }

    @Test fun ascii_rejectsNonAsciiInput() {
        val e = runCatching { enc(ascii, "héllo") }.exceptionOrNull()
        assertTrue(e is ToolException)
        assertEquals("This text contains characters outside standard ASCII.", e?.message)
    }

    @Test fun ascii_rejectsOutOfRangeValues() {
        assertTrue(runCatching { dec(ascii, "128") }.exceptionOrNull() is ToolException)
        assertTrue(runCatching { dec(ascii, "300") }.exceptionOrNull() is ToolException)
    }

    // --------------------------------------------------------------- Decimal

    @Test fun decimal_ascii() {
        assertEquals("72 101 108 108 111", enc(decimal, "Hello"))
        assertEquals("Hello", dec(decimal, "72 101 108 108 111"))
    }

    @Test fun decimal_separators() {
        assertEquals("72,101", enc(decimal, "He", mapOf("separator" to "comma")))
        assertEquals("He", dec(decimal, "72,101"))
        assertEquals("He", dec(decimal, "72\n101"))
    }

    @Test fun decimal_unicode() {
        val text = "→"
        assertEquals("226 134 146", enc(decimal, text))
        assertEquals(text, dec(decimal, enc(decimal, text)))
    }

    @Test fun decimal_invalid() {
        assertTrue(runCatching { dec(decimal, "72 999") }.exceptionOrNull() is ToolException)
        assertTrue(runCatching { dec(decimal, "72 abc") }.exceptionOrNull() is ToolException)
    }

    // ----------------------------------------------------------------- Octal

    @Test fun octal_ascii() {
        assertEquals("110 145 154 154 157", enc(octal, "Hello"))
        assertEquals("Hello", dec(octal, "110 145 154 154 157"))
    }

    @Test fun octal_continuous() {
        assertEquals("110145154154157", enc(octal, "Hello", mapOf("format" to "continuous")))
        assertEquals("Hello", dec(octal, "110145154154157"))
    }

    @Test fun octal_invalid() {
        assertTrue(runCatching { dec(octal, "110 189") }.exceptionOrNull() is ToolException)
        assertTrue(runCatching { dec(octal, "1101") }.exceptionOrNull() is ToolException)
    }

    // --------------------------------------------------------------- Unicode

    @Test fun unicode_basicMultilingual() {
        assertEquals("U+0041 U+00E9", enc(unicode, "Aé"))
        assertEquals("Aé", dec(unicode, "U+0041 U+00E9"))
    }

    @Test fun unicode_emojiBeyondBmp() {
        assertEquals("U+0041 U+1F600", enc(unicode, "A😀"))
        assertEquals("A😀", dec(unicode, "U+0041 U+1F600"))
    }

    @Test fun unicode_modes() {
        assertEquals("0041", enc(unicode, "A", mapOf("mode" to "hex")))
        assertEquals("65", enc(unicode, "A", mapOf("mode" to "decimal")))
        assertEquals("A", dec(unicode, "0041", mapOf("mode" to "hex")))
        assertEquals("A", dec(unicode, "65", mapOf("mode" to "decimal")))
        assertEquals("A", dec(unicode, "U+0041", mapOf("mode" to "hex")))
    }

    @Test fun unicode_rejectsInvalidCodePoints() {
        assertTrue(runCatching { dec(unicode, "U+110000") }.exceptionOrNull() is ToolException)
        assertTrue(runCatching { dec(unicode, "U+D800") }.exceptionOrNull() is ToolException)
        assertTrue(runCatching { dec(unicode, "U+ZZZZ") }.exceptionOrNull() is ToolException)
    }

    @Test fun unicode_roundTripMixed() {
        val text = "Hello 世界 😀 🇮🇳"
        assertEquals(text, dec(unicode, enc(unicode, text)))
    }
}
