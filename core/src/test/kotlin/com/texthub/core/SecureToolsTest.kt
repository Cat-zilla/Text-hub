package com.texthub.core

import com.texthub.core.crypto.AesCtrHmac
import com.texthub.core.crypto.AesGcmPayload
import com.texthub.core.crypto.RawKeyGcm
import com.texthub.core.crypto.RsaHybrid
import com.texthub.core.crypto.RsaKeyGen
import com.texthub.core.crypto.RsaPem
import com.texthub.core.model.Classification
import com.texthub.core.model.Direction
import com.texthub.core.model.ToolCategory
import com.texthub.core.model.ToolException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round four: the dedicated AES-128/192/256 tools, AES-CTR, raw-key AES-GCM and the RSA hybrid,
 * plus the on-device key generator.
 */
class SecureToolsTest {

    private val password = "correct horse battery staple"

    private fun enc(id: String, text: String, params: Map<String, String> = emptyMap()): String {
        val p = ToolRegistry.get(id)
        return p.process(text, p.defaultParams() + params, Direction.ENCODE)
    }

    private fun dec(id: String, text: String, params: Map<String, String> = emptyMap()): String {
        val p = ToolRegistry.get(id)
        return p.process(text, p.defaultParams() + params, Direction.DECODE)
    }

    private fun failure(id: String, text: String, params: Map<String, String>, direction: Direction): String {
        val p = ToolRegistry.get(id)
        val error = runCatching { p.process(text, p.defaultParams() + params, direction) }.exceptionOrNull()
        assertTrue("$id should have failed", error is ToolException)
        return error!!.message ?: ""
    }

    // ------------------------------------------------ dedicated key-size AES tools

    @Test fun generalGcmToolEncryptsAtEveryKeySizeAndDetectsItOnDecrypt() {
        val text = "Key size is a setting, not a tool ✅"
        for (bits in listOf(128, 192, 256)) {
            val payload = enc("aes", text, mapOf("password" to password, "keySize" to bits.toString()))
            // The key size is not stored: decryption derives each size and the GCM tag picks one.
            assertEquals("size $bits", text, dec("aes", payload, mapOf("password" to password)))
            assertEquals(bits / 8, AesGcmPayload.keySizeOf(payload, password.toCharArray())!!)
        }
        // A payload written at 128 bits reads back even when the setting says 256.
        val payload128 = enc("aes", text, mapOf("password" to password, "keySize" to "128"))
        assertEquals(text, dec("aes", payload128, mapOf("password" to password, "keySize" to "256")))
    }

    @Test fun thereIsExactlyOneAesToolPerMode() {
        // Key size is a parameter of the AES tools, not a separate tool - registry test asserts this.
        val aesTools = ToolRegistry.all.map { it.meta.id }.filter { it.startsWith("aes") }
        assertEquals(listOf("aes", "aescbc", "aesctr", "aesrawkey"), aesTools)
        for (id in listOf("aes", "aescbc")) {
            val choices = ToolRegistry.get(id).meta.params.first { it.key == "keySize" }.choices.map { it.id }
            assertEquals(listOf("256", "192", "128"), choices)
        }
    }

    @Test fun generalCbcToolRoundTripsAtEveryKeySizeAndVerifiesTheTag() {
        val text = "CBC with a key-size setting 🔐"
        for (bits in listOf(128, 192, 256)) {
            val payload = enc("aescbc", text, mapOf("password" to password, "keySize" to bits.toString()))
            assertEquals("size $bits", text, dec("aescbc", payload, mapOf("password" to password)))
        }
        val payload = enc("aescbc", "tamper me", mapOf("password" to password, "keySize" to "256"))
        val tampered = payload.mapIndexed { i, c -> if (i == 40) (if (c == 'A') 'B' else 'A') else c }.joinToString("")
        assertTrue(failure("aescbc", tampered, mapOf("password" to password), Direction.DECODE).isNotEmpty())
        assertTrue(failure("aescbc", payload, mapOf("password" to "wrong"), Direction.DECODE).isNotEmpty())
    }

    @Test fun keySizeIsSearchableOnTheAesTools() {
        val found = ToolRegistry.search("aes").map { it.id }
        assertTrue(found.containsAll(listOf("aes", "aescbc", "aesctr", "aesrawkey")))
        // The key-size options are advertised in the tool metadata, so they show up in the app.
        for (id in listOf("aes", "aescbc", "aesctr")) {
            val labels = ToolRegistry.get(id).meta.params
                .first { it.key == "keySize" }.choices.map { it.label }
            assertTrue("$id must offer 128/192/256", labels.any { it.contains("128") } &&
                labels.any { it.contains("192") } && labels.any { it.contains("256") })
        }
    }

    // ----------------------------------------------------------------- AES-CTR

    @Test fun aesCtrRoundTripsAtEveryKeySize() {
        val text = "CTR mode stream 🔁"
        for (size in listOf("128", "192", "256")) {
            val params = mapOf("password" to password, "keySize" to size)
            val payload = enc("aesctr", text, params)
            assertEquals("size $size", text, dec("aesctr", payload, mapOf("password" to password)))
            assertEquals(size.toInt() / 8, AesCtrHmac.keySizeOf(payload, password.toCharArray())!!)
        }
    }

    @Test fun aesCtrRejectsWrongPasswordsAndTampering() {
        val params = mapOf("password" to password)
        val payload = enc("aesctr", "secret", params)
        assertTrue(failure("aesctr", payload, mapOf("password" to "nope"), Direction.DECODE).isNotEmpty())
        val tampered = payload.dropLast(8) + "AAAAAAA="
        assertTrue(failure("aesctr", tampered, params, Direction.DECODE).isNotEmpty())
        assertTrue(failure("aesctr", "not base64!!", params, Direction.DECODE).contains("encrypted data"))
    }

    @Test fun aesCtrIsNotDeterministic() {
        val params = mapOf("password" to password)
        assertNotEquals(enc("aesctr", "same text", params), enc("aesctr", "same text", params))
    }

    // ------------------------------------------------------------- raw key AES

    @Test fun rawKeyToolAcceptsBase64AndHexKeys() {
        val keyBytes = ByteArray(32) { (it * 7 + 3).toByte() }
        val base64 = com.texthub.core.codec.Base64Codec.encode(keyBytes)
        val hex = keyBytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        val text = "my own key 🔑"

        val fromBase64 = enc("aesrawkey", text, mapOf("key" to base64))
        assertEquals(text, dec("aesrawkey", fromBase64, mapOf("key" to hex)))
        // Hex is case-insensitive, so an upper-case hex key must work too.
        assertEquals(text, dec("aesrawkey", fromBase64, mapOf("key" to hex.uppercase())))
        assertEquals(32, RawKeyGcm.keyLengthOf(fromBase64)!!)
    }

    @Test fun rawKeyToolChecksKeyLengthAndValue() {
        val key16 = ByteArray(16) { 1 }
        val key32 = ByteArray(32) { 1 }
        val payload16 = enc("aesrawkey", "text", mapOf("key" to com.texthub.core.codec.Base64Codec.encode(key16)))
        val payload32 = enc("aesrawkey", "text", mapOf("key" to com.texthub.core.codec.Base64Codec.encode(key32)))

        // Right length, wrong value.
        val other32 = ByteArray(32) { 2 }
        assertTrue(
            failure(
                "aesrawkey",
                payload32,
                mapOf("key" to com.texthub.core.codec.Base64Codec.encode(other32)),
                Direction.DECODE,
            ).isNotEmpty()
        )
        // Wrong length is reported as a key problem, not as a decryption failure.
        val wrongLength = failure(
            "aesrawkey",
            payload16,
            mapOf("key" to com.texthub.core.codec.Base64Codec.encode(key32)),
            Direction.DECODE,
        )
        assertTrue(wrongLength.contains("usable AES key"))
        // A 15-byte key is refused before anything is encrypted.
        val short = failure("aesrawkey", "text", mapOf("key" to "0123456789abcdef0123456789abcd"), Direction.ENCODE)
        assertTrue(short.contains("usable AES key"))
        assertTrue(failure("aesrawkey", "text", emptyMap(), Direction.ENCODE).contains("key"))
    }

    // -------------------------------------------------------------- RSA hybrid

    private val pair by lazy { RsaKeyGen.generate(2048) }

    @Test fun rsaHybridRoundTripsIncludingLongText() {
        assertTrue(RsaHybrid.isAvailable())
        val short = "RSA hybrid 🔏"
        val payload = enc("rsa", short, mapOf("key" to pair))
        assertEquals(short, dec("rsa", payload, mapOf("key" to pair)))
        assertNotNull(RsaHybrid.keySizeOf(payload))
        assertEquals(2048, RsaHybrid.keySizeOf(payload)!!)

        // RSA alone could only carry a few hundred bytes - the hybrid layer must not care.
        val long = "Zeile mit Umlauten ü und Emoji 😀\n".repeat(120)
        assertEquals(long, dec("rsa", enc("rsa", long, mapOf("key" to pair)), mapOf("key" to pair)))
    }

    @Test fun rsaHybridRejectsWrongKeysAndTampering() {
        val payload = enc("rsa", "secret", mapOf("key" to pair))
        val otherPair = RsaKeyGen.generate(2048)
        assertTrue(failure("rsa", payload, mapOf("key" to otherPair), Direction.DECODE).contains("private key"))
        val tampered = payload.dropLast(6) + "AAAAAA"
        assertTrue(failure("rsa", tampered, mapOf("key" to pair), Direction.DECODE).isNotEmpty())
        assertTrue(failure("rsa", "not-a-payload", mapOf("key" to pair), Direction.DECODE).contains("RSA message"))
    }

    @Test fun rsaPemHandlingExplainsWhatIsMissing() {
        val publicOnly = RsaPem.toPem(
            RsaPem.PUBLIC_BEGIN,
            RsaPem.PUBLIC_END,
            java.security.KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
                .generateKeyPair().public.encoded,
        )
        // Encrypting with a private-key-only text says exactly what is needed instead of failing silently.
        val message = failure("rsa", "text", mapOf("key" to publicOnly), Direction.DECODE)
        assertTrue(message.contains("private key"))
        assertTrue(failure("rsa", "text", mapOf("key" to ""), Direction.ENCODE).contains("public key"))
        // A PKCS#1 block is recognised and explained.
        val pkcs1 = "-----BEGIN RSA PRIVATE KEY-----\nMIIBOgIBAAJBAK\n-----END RSA PRIVATE KEY-----"
        assertTrue(failure("rsa", "text", mapOf("key" to pkcs1), Direction.DECODE).contains("PKCS#1"))
        assertTrue(failure("rsa", "text", mapOf("key" to "hello"), Direction.DECODE).contains("private key"))
    }

    @Test fun rsaKeyGeneratorProducesUsablePemBlocks() {
        val processor = ToolRegistry.get("rsakeygen")
        assertTrue("key generation needs no input", processor.meta.inputOptional)
        assertEquals(ToolCategory.SECURE, processor.meta.category)
        assertEquals(Classification.KEY_MATERIAL, processor.meta.classification)
        assertFalse(processor.meta.classification.secure)

        val output = processor.process("", mapOf("size" to "2048"), Direction.ENCODE)
        assertTrue(output.startsWith(RsaPem.PUBLIC_BEGIN))
        assertTrue(output.contains(RsaPem.PRIVATE_BEGIN))
        assertTrue(output.contains("2048-bit RSA key pair"))
        assertTrue(output.contains("fingerprint (SHA-256)"))
        assertTrue(RsaPem.hasPublicKey(output) && RsaPem.hasPrivateKey(output))

        // The generated pair actually works with the RSA tool, pasted as one block of text.
        val payload = enc("rsa", "generated on device", mapOf("key" to output))
        assertEquals("generated on device", dec("rsa", payload, mapOf("key" to output)))
        // Two runs must not produce the same key.
        assertNotEquals(output, processor.process("", mapOf("size" to "2048"), Direction.ENCODE))
    }

    @Test fun everySecureToolHasASecretParameter() {
        val secure = ToolRegistry.all.filter { it.meta.classification.secure }.map { it.meta.id }.toSet()
        assertEquals(setOf("aes", "aescbc", "chacha", "aesctr", "aesrawkey", "rsa"), secure)
        ToolRegistry.all.filter { it.meta.classification.secure }.forEach { processor ->
            assertTrue(
                "${processor.meta.id} must ask for a password or key",
                processor.meta.params.any { it.sensitive },
            )
        }
    }

    @Test fun secureToolIdentifiersAreDistinctAndSearchable() {
        val ids = ToolRegistry.all.map { it.meta.id }
        assertEquals(ids.size, ids.distinct().size)
        assertTrue("aes" in ToolRegistry.search("aes256").map { it.id })
        assertTrue("aesrawkey" in ToolRegistry.search("raw key").map { it.id })
        assertTrue("rsakeygen" in ToolRegistry.search("keygen").map { it.id })
        assertTrue("aesctr" in ToolRegistry.search("counter").map { it.id })
        assertTrue("aesctr" in ToolRegistry.search("ctr").map { it.id })
    }
}
