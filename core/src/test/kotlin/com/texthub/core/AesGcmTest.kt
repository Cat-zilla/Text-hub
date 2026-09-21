package com.texthub.core

import com.texthub.core.codec.Base64Codec
import com.texthub.core.crypto.AesGcmPayload
import com.texthub.core.crypto.Pbkdf2
import com.texthub.core.model.Direction
import com.texthub.core.model.ToolException
import com.texthub.core.processors.AesProcessor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class AesGcmTest {

    private val aes = AesProcessor()

    private fun encrypt(text: String, password: String) =
        aes.process(text, mapOf("password" to password), Direction.ENCODE)

    private fun decrypt(payload: String, password: String) =
        aes.process(payload, mapOf("password" to password), Direction.DECODE)

    @Test fun roundTrip() {
        val text = "The quick brown fox jumps over the lazy dog"
        assertEquals(text, decrypt(encrypt(text, "correct horse battery staple"), "correct horse battery staple"))
    }

    @Test fun unicodeRoundTrip() {
        val text = "Grüße aus Agra — नमस्ते 😀 🇮🇳"
        assertEquals(text, decrypt(encrypt(text, "पासवर्द123"), "पासवर्द123"))
    }

    @Test fun emptyInputIsRejected() {
        val e = runCatching { encrypt("", "password") }.exceptionOrNull()
        assertTrue(e is ToolException)
    }

    @Test fun missingPasswordIsRejected() {
        assertTrue(runCatching { encrypt("hello", "") }.exceptionOrNull() is ToolException)
        assertTrue(runCatching { decrypt("abcd", "") }.exceptionOrNull() is ToolException)
    }

    @Test fun freshSaltAndNonceEveryTime() {
        // Same plaintext + same password must still produce different payloads.
        val a = encrypt("identical message", "same password")
        val b = encrypt("identical message", "same password")
        assertNotEquals(a, b)
        assertEquals("identical message", decrypt(a, "same password"))
        assertEquals("identical message", decrypt(b, "same password"))

        val pa = Base64Codec.decode(a)
        val pb = Base64Codec.decode(b)
        val saltA = pa.copyOfRange(2, 18)
        val saltB = pb.copyOfRange(2, 18)
        val ivA = pa.copyOfRange(18, 30)
        val ivB = pb.copyOfRange(18, 30)
        assertTrue(!saltA.contentEquals(saltB))
        assertTrue(!ivA.contentEquals(ivB))
    }

    @Test fun wrongPasswordFails() {
        val payload = encrypt("secret text", "right password")
        val e = runCatching { decrypt(payload, "wrong password") }.exceptionOrNull()
        assertTrue(e is ToolException)
        assertEquals("Unable to decrypt. The password or encrypted data may be incorrect.", e?.message)
    }

    @Test fun tamperedCiphertextFailsAuthentication() {
        val payload = encrypt("secret text", "password123")
        val bytes = Base64Codec.decode(payload).copyOf()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1] + 1).toByte()
        val tampered = Base64Codec.encode(bytes)
        val e = runCatching { decrypt(tampered, "password123") }.exceptionOrNull()
        assertTrue(e is ToolException)
    }

    @Test fun tamperedIvFailsAuthentication() {
        val payload = encrypt("secret text", "password123")
        val bytes = Base64Codec.decode(payload).copyOf()
        bytes[20] = (bytes[20] + 1).toByte()
        assertTrue(runCatching { decrypt(Base64Codec.encode(bytes), "password123") }.exceptionOrNull() is ToolException)
    }

    @Test fun truncatedPayloadFails() {
        val payload = encrypt("secret text", "password123")
        val bytes = Base64Codec.decode(payload)
        val truncated = Base64Codec.encode(bytes.copyOf(bytes.size / 2))
        assertTrue(runCatching { decrypt(truncated, "password123") }.exceptionOrNull() is ToolException)
    }

    @Test fun notAPayloadGivesFriendlyError() {
        val e = runCatching { decrypt("SGVsbG8gd29ybGQ=", "password123") }.exceptionOrNull()
        assertTrue(e is ToolException)
        assertTrue(e!!.message!!.contains("does not look like Text Hub encrypted data"))
    }

    @Test fun payloadStructureIsDocumented() {
        val payload = Base64Codec.decode(encrypt("hello", "pw"))
        assertEquals(0x01.toByte(), payload[0]) // version
        assertEquals(0x01.toByte(), payload[1]) // KDF id: PBKDF2-HMAC-SHA256
        // 2 header + 16 salt + 12 IV + 5 bytes plaintext + 16 byte GCM tag
        assertEquals(2 + 16 + 12 + 5 + 16, payload.size)
    }

    @Test fun longTextRoundTrip() {
        val text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. ".repeat(50)
        assertEquals(text, decrypt(encrypt(text, "氷"), "氷"))
    }

    @Test fun pbkdf2MatchesPlatformImplementation() {
        val salt = "0123456789abcdef".toByteArray()
        val password = "password".toCharArray()
        val iterations = 1000
        val mine = Pbkdf2.derive(password, salt, iterations, 32)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val expected = factory.generateSecret(PBEKeySpec(password, salt, iterations, 256)).encoded
        assertArrayEquals(expected, mine)
    }

    @Test fun pbkdf2DifferentSaltsGiveDifferentKeys() {
        val k1 = Pbkdf2.derive("pw".toCharArray(), "salt1111".toByteArray(), 100, 32)
        val k2 = Pbkdf2.derive("pw".toCharArray(), "salt2222".toByteArray(), 100, 32)
        assertTrue(!k1.contentEquals(k2))
    }

    @Test fun encryptsDirectlyThroughPayloadApi() {
        val payload = AesGcmPayload.encrypt("direct api", "pw".toCharArray())
        assertEquals("direct api", AesGcmPayload.decrypt(payload, "pw".toCharArray()))
    }
}
