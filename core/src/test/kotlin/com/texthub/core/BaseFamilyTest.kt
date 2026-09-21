package com.texthub.core

import com.texthub.core.codec.Base32Codec
import com.texthub.core.codec.Base58Codec
import com.texthub.core.codec.Base85Codec
import com.texthub.core.model.Direction
import com.texthub.core.model.ToolException
import com.texthub.core.processors.Base32Processor
import com.texthub.core.processors.Base58Processor
import com.texthub.core.processors.Base85Processor
import com.texthub.core.processors.Base64Processor
import com.texthub.core.util.utf8Bytes
import com.texthub.core.util.utf8String
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseFamilyTest {

    private val base64 = Base64Processor()
    private val base32 = Base32Processor()
    private val base58 = Base58Processor()
    private val base85 = Base85Processor()

    private fun enc(p: TextProcessor, text: String, params: Map<String, String> = emptyMap()) =
        p.process(text, p.defaultParams() + params, Direction.ENCODE)

    private fun dec(p: TextProcessor, text: String, params: Map<String, String> = emptyMap()) =
        p.process(text, p.defaultParams() + params, Direction.DECODE)

    // ---------------------------------------------------------------- Base64

    @Test fun base64_ascii() {
        assertEquals("SGVsbG8=", enc(base64, "Hello"))
        assertEquals("Hello", dec(base64, "SGVsbG8="))
    }

    @Test fun base64_unicode() {
        val text = "héllo → 😀"
        val encoded = enc(base64, text)
        assertEquals(java.util.Base64.getEncoder().encodeToString(text.utf8Bytes()), encoded)
        assertEquals(text, dec(base64, encoded))
    }

    @Test fun base64_empty() {
        assertEquals("", enc(base64, ""))
        assertEquals("", dec(base64, ""))
    }

    @Test fun base64_urlSafe() {
        val text = "???>>>~~~"
        val encoded = enc(base64, text, mapOf("variant" to "url"))
        assertTrue(!encoded.contains('+') && !encoded.contains('/'))
        assertEquals(text, dec(base64, encoded, mapOf("variant" to "url")))
    }

    @Test fun base64_invalidDecode() {
        val e = runCatching { dec(base64, "SGVsbG8*") }.exceptionOrNull()
        assertTrue(e is ToolException)
        assertEquals("Invalid Base64 input. Please check the characters and padding.", e?.message)
    }

    @Test fun base64_acceptsMissingPadding() {
        assertEquals("Hello", dec(base64, "SGVsbG8"))
    }

    @Test fun base64_whitespaceTolerant() {
        assertEquals("Hello", dec(base64, "SGVs bG8=\n"))
    }

    // ---------------------------------------------------------------- Base32

    @Test fun base32_rfcVectors() {
        assertEquals("MY======", Base32Codec.encode("f".utf8Bytes()))
        assertEquals("MZXQ====", Base32Codec.encode("fo".utf8Bytes()))
        assertEquals("MZXW6===", Base32Codec.encode("foo".utf8Bytes()))
        assertEquals("MZXW6YQ=", Base32Codec.encode("foob".utf8Bytes()))
        assertEquals("MZXW6YTB", Base32Codec.encode("fooba".utf8Bytes()))
        assertEquals("MZXW6YTBOI======", Base32Codec.encode("foobar".utf8Bytes()))
    }

    @Test fun base32_roundTripUnicode() {
        val text = "Grüße 42 °C"
        assertEquals(text, dec(base32, enc(base32, text)))
    }

    @Test fun base32_caseInsensitiveDecode() {
        assertEquals("foobar", dec(base32, "mzxw6ytboi======"))
    }

    @Test fun base32_hexVariant() {
        val text = "Hello base32"
        val encoded = enc(base32, text, mapOf("variant" to "hex"))
        assertEquals(text, dec(base32, encoded, mapOf("variant" to "hex")))
        // The extended hex alphabet starts with the digits.
        assertTrue(encoded.all { it in '0'..'9' || it in 'A'..'V' || it == '=' })
        val standard = enc(base32, text)
        assertTrue(encoded != standard)
    }

    @Test fun base32_invalidDecode() {
        assertTrue(runCatching { dec(base32, "MZXW6Y1B") }.exceptionOrNull() is ToolException) // '1' not in alphabet
        assertTrue(runCatching { dec(base32, "MZXW6Y") }.exceptionOrNull() is ToolException) // bad length
    }

    // ---------------------------------------------------------------- Base58

    @Test fun base58_leadingZeroBytes() {
        val data = byteArrayOf(0, 0, 1, 2, 3)
        val encoded = Base58Codec.encode(data)
        assertTrue(encoded.startsWith("11"))
        org.junit.Assert.assertArrayEquals(data, Base58Codec.decode(encoded))
    }

    @Test fun base58_roundTrip() {
        val text = "Hello Bitcoin 123"
        assertEquals(text, dec(base58, enc(base58, text)))
    }

    @Test fun base58_unicodeRoundTrip() {
        val text = "₿ → 😀 ünïcödé"
        assertEquals(text, dec(base58, enc(base58, text)))
    }

    @Test fun base58_invalidCharacters() {
        val e = runCatching { dec(base58, "0OIl") }.exceptionOrNull()
        assertTrue(e is ToolException)
        assertEquals("Invalid Base58 input. Please check the characters.", e?.message)
    }

    @Test fun base58_alternateAlphabets() {
        val text = "Ripple and Flickr alphabets"
        for (variant in listOf("bitcoin", "ripple", "flickr")) {
            assertEquals(text, dec(base58, enc(base58, text, mapOf("variant" to variant)), mapOf("variant" to variant)))
        }
    }

    // ---------------------------------------------------------------- Base85

    @Test fun base85_ascii85KnownVector() {
        assertEquals("9jqo^", Base85Codec.encode("Man ".utf8Bytes(), Base85Codec.Variant.ASCII85))
        assertEquals("Man ", Base85Codec.decode("9jqo^", Base85Codec.Variant.ASCII85).utf8String())
    }

    @Test fun base85_partialBlocks() {
        for (text in listOf("A", "AB", "ABC", "ABCD", "ABCDE", "Hello, World!")) {
            assertEquals(text, Base85Codec.decode(Base85Codec.encode(text.utf8Bytes(), Base85Codec.Variant.ASCII85), Base85Codec.Variant.ASCII85).utf8String())
        }
    }

    @Test fun base85_zCompression() {
        val data = byteArrayOf(0, 0, 0, 0, 65, 66, 67, 68)
        val encoded = Base85Codec.encode(data, Base85Codec.Variant.ASCII85_Z)
        assertTrue(encoded.startsWith("z"))
        org.junit.Assert.assertArrayEquals(data, Base85Codec.decode(encoded, Base85Codec.Variant.ASCII85_Z))
    }

    @Test fun base85_z85KnownVector() {
        // ZeroMQ spec: the four bytes 0x86 0x4F 0xD2 0x6F encode to "Hello".
        val data = byteArrayOf(0x86.toByte(), 0x4F, 0xD2.toByte(), 0x6F)
        assertEquals("Hello", Base85Codec.encode(data, Base85Codec.Variant.Z85))
        org.junit.Assert.assertArrayEquals(data, Base85Codec.decode("Hello", Base85Codec.Variant.Z85))
    }

    @Test fun base85_z85RequiresMultipleOfFour() {
        val e = runCatching { enc(base85, "Hello", mapOf("variant" to "z85")) }.exceptionOrNull()
        assertTrue(e is ToolException)
        assertTrue(e!!.message!!.contains("multiple of 4 bytes"))
    }

    @Test fun base85_unicodeRoundTrip() {
        val text = "Ünïcödé 😀 works"
        val encoded = enc(base85, text)
        assertEquals(text, dec(base85, encoded))
    }

    @Test fun base85_invalidInput() {
        assertTrue(runCatching { dec(base85, "9jqo\u00DC^") }.exceptionOrNull() is ToolException)
    }
}
