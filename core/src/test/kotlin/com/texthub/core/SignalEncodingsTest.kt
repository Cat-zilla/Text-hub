package com.texthub.core

import com.texthub.core.model.Direction
import com.texthub.core.model.ToolException
import com.texthub.core.processors.BaudotProcessor
import com.texthub.core.processors.MorseProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalEncodingsTest {

    private val morse = MorseProcessor()
    private val baudot = BaudotProcessor()

    private fun enc(p: TextProcessor, text: String, params: Map<String, String> = emptyMap()) =
        p.process(text, p.defaultParams() + params, Direction.ENCODE)

    private fun dec(p: TextProcessor, text: String, params: Map<String, String> = emptyMap()) =
        p.process(text, p.defaultParams() + params, Direction.DECODE)

    // ----------------------------------------------------------------- Morse

    @Test fun morse_letters() {
        assertEquals(".... . .-.. .-.. ---", enc(morse, "HELLO"))
        assertEquals("HELLO", dec(morse, ".... . .-.. .-.. ---"))
    }

    @Test fun morse_numbers() {
        assertEquals("... --- ...", enc(morse, "SOS"))
        assertEquals(".---- ..--- ...-- ....-", enc(morse, "1234"))
        assertEquals("1234", dec(morse, ".---- ..--- ...-- ....-"))
    }

    @Test fun morse_punctuation() {
        assertEquals(".-.-.-", enc(morse, "."))
        assertEquals("--..--", enc(morse, ","))
        assertEquals("..--..", enc(morse, "?"))
        assertEquals("-.-.--", enc(morse, "!"))
        assertEquals("---...", enc(morse, ":"))
        assertEquals(".-.-.", enc(morse, "+"))
    }

    @Test fun morse_wordSeparator() {
        assertEquals(".... . .-.. .-.. --- / .-- --- .-. .-.. -..", enc(morse, "HELLO WORLD"))
        assertEquals("HELLO WORLD", dec(morse, ".... . .-.. .-.. --- / .-- --- .-. .-.. -.."))
    }

    @Test fun morse_invalidSymbols() {
        val e = runCatching { dec(morse, ".... ..---..-..") }.exceptionOrNull()
        assertTrue(e is ToolException)
        assertEquals("Invalid Morse input. Use dots, dashes, spaces and / only.", e?.message)
    }

    @Test fun morse_roundTripSentence() {
        val text = "HELLO WORLD 123"
        assertEquals(text, dec(morse, enc(morse, text)))
    }

    // ---------------------------------------------------------------- Baudot

    @Test fun baudot_letters() {
        // H=10100 E=00001 L=10010 L=10010 O=11000
        assertEquals("10100 00001 10010 10010 11000", enc(baudot, "HELLO"))
        assertEquals("HELLO", dec(baudot, "10100 00001 10010 10010 11000"))
    }

    @Test fun baudot_figures() {
        // FIGS (11011) then the digit codes
        assertEquals("11011 10111 10011 00001", enc(baudot, "123"))
        assertEquals("123", dec(baudot, "11011 10111 10011 00001"))
    }

    @Test fun baudot_shiftState() {
        val encoded = enc(baudot, "A1B2")
        assertEquals("A1B2", dec(baudot, encoded))
        // A (letters) then FIGS 1 then letters B then FIGS 2
        assertTrue(encoded.contains("11011"))
        assertTrue(encoded.contains("11111"))
    }

    @Test fun baudot_spaces() {
        assertEquals("00011 00100 10010", enc(baudot, "A L"))
        assertEquals("A L", dec(baudot, "00011 00100 10010"))
    }

    @Test fun baudot_roundTrip() {
        for (text in listOf("HELLO WORLD", "MEET AT 9 PM", "SOS 123")) {
            assertEquals(text, dec(baudot, enc(baudot, text)))
        }
    }

    @Test fun baudot_formats() {
        val continuous = enc(baudot, "HI", mapOf("format" to "continuous"))
        assertEquals("1010000110", continuous)
        assertEquals("HI", dec(baudot, continuous))
        val lines = enc(baudot, "HI", mapOf("format" to "line"))
        assertEquals("10100\n00110", lines)
    }

    @Test fun baudot_variantsDiffer() {
        // 0x05 is BEL in US-TTY but apostrophe in ITA2; 0x09 is $ in US-TTY but ENQ in ITA2.
        val ita2 = dec(baudot, "11011 00101", mapOf("variant" to "ita2"))
        val ustty = dec(baudot, "11011 00101", mapOf("variant" to "ustty"))
        assertEquals("'", ita2)
        assertEquals("\u0007", ustty)
    }

    @Test fun baudot_unsupportedCharacter() {
        val e = runCatching { enc(baudot, "hello…") }.exceptionOrNull()
        assertTrue(e is ToolException)
        assertTrue(e!!.message!!.startsWith("Baudot/ITA2 cannot represent"))
        // With "skip" the unsupported character is dropped instead.
        assertEquals(enc(baudot, "HELLO"), enc(baudot, "hello…", mapOf("unknown" to "skip")))
    }

    @Test fun baudot_invalidGroup() {
        assertTrue(runCatching { dec(baudot, "101 00001") }.exceptionOrNull() is ToolException)
        assertTrue(runCatching { dec(baudot, "10102") }.exceptionOrNull() is ToolException)
        assertTrue(runCatching { dec(baudot, "101001") }.exceptionOrNull() is ToolException)
    }

    @Test fun baudot_namedControls() {
        assertEquals("00010 01000", enc(baudot, "<LF><CR>"))
        assertEquals("\n\r", dec(baudot, "00010 01000"))
    }
}
