package com.texthub.core

import com.texthub.core.model.Direction
import com.texthub.core.model.ToolException
import com.texthub.core.processors.A1Z26Processor
import com.texthub.core.processors.AffineProcessor
import com.texthub.core.processors.AtbashProcessor
import com.texthub.core.processors.BaconProcessor
import com.texthub.core.processors.CaesarProcessor
import com.texthub.core.processors.ColumnarTranspositionProcessor
import com.texthub.core.processors.PlayfairProcessor
import com.texthub.core.processors.RailFenceProcessor
import com.texthub.core.processors.Rot13Processor
import com.texthub.core.processors.Rot18Processor
import com.texthub.core.processors.Rot47Processor
import com.texthub.core.processors.ReverseProcessor
import com.texthub.core.processors.SubstitutionProcessor
import com.texthub.core.processors.VigenereProcessor
import com.texthub.core.processors.XorProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassicalCipherTest {

    private val caesar = CaesarProcessor()
    private val rot13 = Rot13Processor()
    private val rot18 = Rot18Processor()
    private val rot47 = Rot47Processor()
    private val atbash = AtbashProcessor()
    private val vigenere = VigenereProcessor()
    private val xor = XorProcessor()
    private val substitution = SubstitutionProcessor()
    private val bacon = BaconProcessor()
    private val a1z26 = A1Z26Processor()
    private val railFence = RailFenceProcessor()
    private val affine = AffineProcessor()
    private val playfair = PlayfairProcessor()
    private val columnar = ColumnarTranspositionProcessor()
    private val reverse = ReverseProcessor()

    private fun enc(p: TextProcessor, text: String, params: Map<String, String> = emptyMap()) =
        p.process(text, p.defaultParams() + params, Direction.ENCODE)

    private fun dec(p: TextProcessor, text: String, params: Map<String, String> = emptyMap()) =
        p.process(text, p.defaultParams() + params, Direction.DECODE)

    // ---------------------------------------------------------------- Caesar

    @Test fun caesar_positiveShift() {
        assertEquals("Khoor, Zruog!", enc(caesar, "Hello, World!"))
        assertEquals("Hello, World!", dec(caesar, "Khoor, Zruog!"))
    }

    @Test fun caesar_negativeShift() {
        assertEquals("Ebiil, Tloia!", enc(caesar, "Hello, World!", mapOf("shift" to "-3")))
        assertEquals("Hello, World!", dec(caesar, "Ebiil, Tloia!", mapOf("shift" to "-3")))
    }

    @Test fun caesar_largeShiftIsNormalised() {
        assertEquals(enc(caesar, "Hello", mapOf("shift" to "29")), enc(caesar, "Hello", mapOf("shift" to "3")))
        assertEquals("Hello", dec(caesar, enc(caesar, "Hello", mapOf("shift" to "-55")), mapOf("shift" to "-55")))
    }

    @Test fun caesar_preservesCaseNumbersAndPunctuation() {
        val text = "ABC xyz 123 !@#"
        assertEquals(text, dec(caesar, enc(caesar, text, mapOf("shift" to "7")), mapOf("shift" to "7")))
        assertTrue(enc(caesar, "ABC xyz", mapOf("shift" to "7")).let { it.startsWith("HIJ") })
    }

    // ------------------------------------------------------------------ ROTs

    @Test fun rot13_roundTrip() {
        assertEquals("Uryyb, Jbeyq!", enc(rot13, "Hello, World!"))
        assertEquals("Hello, World!", dec(rot13, "Uryyb, Jbeyq!"))
    }

    @Test fun rot18_lettersAndDigits() {
        assertEquals("Uryyb678", enc(rot18, "Hello123"))
        assertEquals("Hello123", dec(rot18, "Uryyb678"))
    }

    @Test fun rot47_printableRange() {
        assertEquals("w6==@", enc(rot47, "Hello"))
        assertEquals("Hello", dec(rot47, "w6==@"))
        assertEquals("~!@#$%^&*()", dec(rot47, enc(rot47, "~!@#$%^&*()")))
    }

    @Test fun rot47_leavesNonAsciiUntouched() {
        assertEquals("w6==@ é 😀", enc(rot47, "Hello é 😀"))
    }

    // ---------------------------------------------------------------- Atbash

    @Test fun atbash_roundTrip() {
        assertEquals("Svool", enc(atbash, "Hello"))
        assertEquals("Hello", dec(atbash, "Svool"))
        val text = "The quick brown fox, 42!"
        assertEquals(text, dec(atbash, enc(atbash, text)))
    }

    // -------------------------------------------------------------- Vigenère

    @Test fun vigenere_knownVector() {
        assertEquals("LXFOPVEFRNHR", enc(vigenere, "ATTACKATDAWN", mapOf("key" to "LEMON")))
        assertEquals("ATTACKATDAWN", dec(vigenere, "LXFOPVEFRNHR", mapOf("key" to "LEMON")))
    }

    @Test fun vigenere_preservesSpacesPunctuationAndCase() {
        val text = "Attack at dawn, now!"
        val cipher = enc(vigenere, text, mapOf("key" to "lemon"))
        assertEquals(text, dec(vigenere, cipher, mapOf("key" to "LEMON")))
        // Only letters change: the "shape" of spaces, digits and punctuation is preserved.
        assertEquals(
            text.replace(Regex("[A-Za-z]"), "X"),
            cipher.replace(Regex("[A-Za-z]"), "X")
        )
    }

    @Test fun vigenere_missingKey() {
        val e = runCatching { enc(vigenere, "text") }.exceptionOrNull()
        assertTrue(e is ToolException)
        assertEquals("Please enter a key before processing.", e?.message)
    }

    // -------------------------------------------------------------------- XOR

    @Test fun xor_roundTripAndKnownVector() {
        assertEquals("230015070A", enc(xor, "Hello", mapOf("key" to "key")))
        assertEquals("Hello", dec(xor, "230015070A", mapOf("key" to "key")))
    }

    @Test fun xor_unicode() {
        val text = "Ünïcödé 😀"
        val cipher = enc(xor, text, mapOf("key" to "s3cret"))
        assertEquals(text, dec(xor, cipher, mapOf("key" to "s3cret")))
    }

    @Test fun xor_base64Variant() {
        val text = "Round trip me"
        val cipher = enc(xor, text, mapOf("key" to "k", "format" to "base64"))
        assertEquals(text, dec(xor, cipher, mapOf("key" to "k", "format" to "base64")))
    }

    @Test fun xor_missingKey() {
        assertTrue(runCatching { enc(xor, "text", mapOf("key" to "")) }.exceptionOrNull() is ToolException)
    }

    // ---------------------------------------------------------- Substitution

    @Test fun substitution_knownMapping() {
        assertEquals("QWE", enc(substitution, "ABC"))
        assertEquals("ABC", dec(substitution, "QWE"))
    }

    @Test fun substitution_roundTripPreservesCaseAndPunctuation() {
        val text = "The Quick Brown Fox, 7!"
        val cipher = enc(substitution, text)
        assertEquals(text, dec(substitution, cipher))
    }

    @Test fun substitution_duplicateLetters() {
        val e = runCatching { enc(substitution, "ABC", mapOf("alphabet" to "AACDEFGHIJKLMNOPQRSTUVWXYZ")) }.exceptionOrNull()
        assertTrue(e is ToolException)
        assertEquals("The substitution alphabet must contain unique characters.", e?.message)
    }

    @Test fun substitution_wrongLength() {
        val e = runCatching { enc(substitution, "ABC", mapOf("alphabet" to "ABCDE")) }.exceptionOrNull()
        assertTrue(e is ToolException)
        assertTrue(e!!.message!!.contains("exactly 26 characters"))
    }

    @Test fun substitution_nonLetters() {
        assertTrue(runCatching { enc(substitution, "ABC", mapOf("alphabet" to "ABCDEFGHIJKLMNOPQRSTUVWXY1")) }.exceptionOrNull() is ToolException)
    }

    // ------------------------------------------------------------------ Bacon

    @Test fun bacon_knownVectors() {
        assertEquals("AAAAA", enc(bacon, "A"))
        assertEquals("AAAAB", enc(bacon, "B"))
        assertEquals("BBAAB", enc(bacon, "Z"))
        assertEquals("A", dec(bacon, "AAAAA"))
        assertEquals("Z", dec(bacon, "BBAAB"))
    }

    @Test fun bacon_roundTrip() {
        val text = "HELLO WORLD"
        assertEquals(text, dec(bacon, enc(bacon, text)))
    }

    @Test fun bacon_24LetterVariant() {
        // In the 24 letter variant J and V share the code of I and U.
        assertEquals("A", dec(bacon, "AAAAA", mapOf("variant" to "24")))
        val text = "JUST A TEST"
        assertEquals("IUST A TEST", dec(bacon, enc(bacon, text, mapOf("variant" to "24")), mapOf("variant" to "24")))
        assertEquals("Z", dec(bacon, "BABBB", mapOf("variant" to "24")))
    }

    @Test fun bacon_invalidInput() {
        assertTrue(runCatching { dec(bacon, "AAAA") }.exceptionOrNull() is ToolException)
        assertTrue(runCatching { dec(bacon, "AAABC") }.exceptionOrNull() is ToolException)
    }

    // ------------------------------------------------------------------ A1Z26

    @Test fun a1z26_alphabet() {
        assertEquals("8-5-12-12-15", enc(a1z26, "HELLO"))
        assertEquals("HELLO", dec(a1z26, "8-5-12-12-15"))
    }

    @Test fun a1z26_separators() {
        assertEquals("1 2 3", enc(a1z26, "ABC", mapOf("separator" to "space")))
        assertEquals("1,2,3", enc(a1z26, "ABC", mapOf("separator" to "comma")))
        assertEquals("ABC", dec(a1z26, "1 2 3"))
        assertEquals("ABC", dec(a1z26, "1,2,3", mapOf("separator" to "comma")))
    }

    @Test fun a1z26_wordBoundaries() {
        assertEquals("8-5-12-12-15 / 23-15-18-12-4", enc(a1z26, "HELLO WORLD"))
        assertEquals("HELLO WORLD", dec(a1z26, "8-5-12-12-15 / 23-15-18-12-4"))
    }

    @Test fun a1z26_invalidRange() {
        assertTrue(runCatching { dec(a1z26, "8-27-1") }.exceptionOrNull() is ToolException)
        assertTrue(runCatching { dec(a1z26, "8-0") }.exceptionOrNull() is ToolException)
    }

    // ------------------------------------------------------------- Rail Fence

    @Test fun railFence_knownVector() {
        assertEquals(
            "WECRLTEERDSOEEFEAOCAIVDEN",
            enc(railFence, "WEAREDISCOVEREDFLEEATONCE", mapOf("rails" to "3"))
        )
        assertEquals(
            "WEAREDISCOVEREDFLEEATONCE",
            dec(railFence, "WECRLTEERDSOEEFEAOCAIVDEN", mapOf("rails" to "3"))
        )
    }

    @Test fun railFence_multipleRailCounts() {
        val text = "THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG"
        for (rails in 2..7) {
            assertEquals(text, dec(railFence, enc(railFence, text, mapOf("rails" to rails.toString())), mapOf("rails" to rails.toString())))
        }
    }

    @Test fun railFence_preserveFormattingMode() {
        val text = "Meet me at the old bridge, 7pm!"
        val params = mapOf("rails" to "4", "mode" to "letters")
        assertEquals(text, dec(railFence, enc(railFence, text, params), params))
    }

    @Test fun railFence_invalidRails() {
        assertTrue(runCatching { enc(railFence, "text", mapOf("rails" to "1")) }.exceptionOrNull() is ToolException)
    }

    // ----------------------------------------------------------------- Affine

    @Test fun affine_knownVector() {
        assertEquals("IHHWVCSWFRCP", enc(affine, "AFFINECIPHER"))
        assertEquals("AFFINECIPHER", dec(affine, "IHHWVCSWFRCP"))
    }

    @Test fun affine_roundTrip() {
        val text = "Attack At Dawn, 42!"
        for (a in listOf(1, 3, 5, 7, 9, 11, 15, 17, 19, 21, 23, 25)) {
            val params = mapOf("a" to a.toString(), "b" to "8")
            assertEquals(text, dec(affine, enc(affine, text, params), params))
        }
    }

    @Test fun affine_nonCoprimeKeyRejected() {
        val e = runCatching { enc(affine, "TEXT", mapOf("a" to "2")) }.exceptionOrNull()
        assertTrue(e is ToolException)
        assertTrue(e!!.message!!.contains("coprime with 26"))
        assertTrue(runCatching { dec(affine, "TEXT", mapOf("a" to "13")) }.exceptionOrNull() is ToolException)
    }

    // --------------------------------------------------------------- Playfair

    @Test fun playfair_knownVector() {
        val params = mapOf("key" to "playfair example")
        val cipher = enc(playfair, "Hide the gold in the tree stump", params)
        assertEquals("BMODZBXDNABEKUDMUIXMMOUVIF", cipher)
        assertEquals("HIDETHEGOLDINTHETREESTUMP", dec(playfair, cipher, params))
    }

    @Test fun playfair_iJMerged() {
        val params = mapOf("key" to "keyword")
        // J is treated as I, so "JAZZ" decodes back to "IAZZ".
        val cipher = enc(playfair, "JAZZ", params)
        assertEquals("IAZZ", dec(playfair, cipher, params))
        assertTrue(cipher.length % 2 == 0)
    }

    @Test fun playfair_repeatedLettersGetFiller() {
        val params = mapOf("key" to "monarchy", "filler" to "X")
        val text = "BALLOON"
        val cipher = enc(playfair, text, params)
        assertEquals("BALLOON", dec(playfair, cipher, params))
        assertTrue(cipher.length % 2 == 0)
    }

    @Test fun playfair_roundTripLongText() {
        val params = mapOf("key" to "playfair example")
        val text = "The quick brown fox jumps over the lazy dog"
        val cipher = enc(playfair, text, params)
        assertEquals(
            text.uppercase().filter { it.isLetter() }.replace("J", "I"),
            dec(playfair, cipher, params)
        )
    }

    @Test fun playfair_sixBySixGrid() {
        val params = mapOf("key" to "secret", "grid" to "alnum")
        val text = "MEET AT 9 PM"
        val cipher = enc(playfair, text, params)
        assertEquals("MEETAT9PM", dec(playfair, cipher, params))
    }

    @Test fun playfair_missingKey() {
        assertTrue(runCatching { enc(playfair, "text") }.exceptionOrNull() is ToolException)
    }

    // ------------------------------------------------- Columnar Transposition

    @Test fun columnar_knownVector() {
        val params = mapOf("key" to "BAD")
        assertEquals("EORHLODLWL", enc(columnar, "HELLOWORLD", params))
        assertEquals("HELLOWORLD", dec(columnar, "EORHLODLWL", params))
    }

    @Test fun columnar_repeatedKeyCharactersAreDeterministic() {
        val params = mapOf("key" to "BANANA")
        val text = "WE ARE DISCOVERED FLEE AT ONCE"
        val cipher = enc(columnar, text, params)
        assertEquals(text, dec(columnar, cipher, params))
        // Same key always produces the same ordering.
        assertEquals(cipher, enc(columnar, text, params))
    }

    @Test fun columnar_withPadding() {
        val params = mapOf("key" to "ZEBRA", "padding" to "pad")
        val text = "WE ARE DISCOVERED FLEE AT ONCE"
        assertEquals(text, dec(columnar, enc(columnar, text, params), params))
    }

    @Test fun columnar_roundTripUnicode() {
        val params = mapOf("key" to "ключ")
        val text = "Сообщение с пробелами и 😀"
        assertEquals(text, dec(columnar, enc(columnar, text, params), params))
    }

    @Test fun columnar_missingKey() {
        assertTrue(runCatching { enc(columnar, "text") }.exceptionOrNull() is ToolException)
    }

    // ---------------------------------------------------------------- Reverse

    @Test fun reverse_unicode() {
        assertEquals("😀olleH", enc(reverse, "Hello😀"))
        assertEquals("Hello😀", dec(reverse, "😀olleH"))
    }

    @Test fun reverse_newlines() {
        val text = "one\ntwo\nthree"
        assertEquals("three\ntwo\none", enc(reverse, text, mapOf("mode" to "lineOrder")))
        assertEquals("eno\nowt\neerht", enc(reverse, text, mapOf("mode" to "lines")))
    }

    @Test fun reverse_words() {
        assertEquals("world   Hello", enc(reverse, "Hello   world", mapOf("mode" to "words")))
        assertEquals("dog lazy the", enc(reverse, "the lazy dog", mapOf("mode" to "words")))
    }
}
