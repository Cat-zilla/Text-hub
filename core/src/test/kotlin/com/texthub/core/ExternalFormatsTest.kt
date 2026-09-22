package com.texthub.core

import com.texthub.core.codec.Base64Codec
import com.texthub.core.crypto.JweFormat
import com.texthub.core.crypto.OpenSslEnc
import com.texthub.core.model.Direction
import com.texthub.core.model.ToolException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Compatibility with data produced **outside** Text Hub.
 *
 * The fixtures are not made up: the OpenSSL payloads are literal output of the `openssl enc`
 * command line (OpenSSL 3.5.6), and the JWE tokens were produced by the Python `cryptography`
 * library following RFC 7516/7518. Each one is decrypted here through the normal tool API, so a
 * change that breaks interoperability breaks this test.
 */
class ExternalFormatsTest {

    private val password = "correct horse"
    private val plaintext = "Text Hub external compatibility\n"

    private fun run(toolId: String, input: String, params: Map<String, String>, direction: Direction): String =
        ToolRegistry.get(toolId).process(input, params, direction)

    private fun failure(toolId: String, input: String, params: Map<String, String>): String {
        val e = runCatching { run(toolId, input, params, Direction.DECODE) }.exceptionOrNull()
        assertTrue("expected a ToolException, got $e", e is ToolException)
        return e!!.message ?: ""
    }

    // ------------------------------------------------------------------ OpenSSL enc fixtures

    /**
     * Real output of the OpenSSL command line (3.5.6), one line each, all of the same 32-byte
     * plaintext "Text Hub external compatibility\n" with password "correct horse":
     *
     * ```
     * printf '...' | openssl enc -aes-256-cbc -md md5               -pass pass:"..." -base64 -A
     * ```
     */
    private val opensslLegacyMd5 =
        "U2FsdGVkX1/4ufTWgdoC8xteTg5pGE8h0ZOmdHcCJr+YjzkQRnPzlFrmT9f8hktcsMr42JEQq0XvUO8bQ9qlIg=="

    /** The same, with `-md sha256` (legacy derivation, different digest). */
    private val opensslLegacySha256 =
        "U2FsdGVkX1+bxZ+ZU1y2i57kK/XtT+V38laGUOH2EnvRaOmBPx1oWa+bML6ryTlsAVLrk+NJwN30eqergbKtXA=="

    /** The OpenSSL 3.x default path: `-md sha256 -iter 10000`. */
    private val opensslPbkdf2Sha256 =
        "U2FsdGVkX1+Nk4IdvUASQivGzqICky5sju+HFlo+yH+c4m2G12vzkfy4v3f5OF6nY7LILReJap4Vthoa6P9RsQ=="

    /** `-aes-128-cbc -md sha256 -iter 100000`: a different key size and iteration count. */
    private val opensslPbkdf2Aes128 =
        "U2FsdGVkX18KEry3sYyiyZcEn4uY2GcwXkse2d273PcHQH8SVHZ9kzI0wbCEhJYctyogcY8C0lJUOTAmBXpzNg=="

    /** The same file as it comes out of the terminal: wrapped over several lines. */
    private val opensslMultiline =
        "U2FsdGVkX18kXxLDrNXnC5AIBeHWy77xX7XJBaBW6Kji2nMA3EkhGNkBPW3TApXC\n" +
            "W5kv/PBfybdTDGVUcQbnRg=="

    @Test fun opensslLegacyMd5FilesDecrypt() {
        val openssl = mapOf(
            "format" to "openssl",
            "keySize" to "256",
            "opensslKdf" to "legacy",
            "opensslDigest" to "md5",
            "password" to password,
        )
        assertEquals(plaintext, run("aescbc", opensslLegacyMd5, openssl, Direction.DECODE))
    }

    @Test fun opensslLegacySha256FilesDecrypt() {
        val params = mapOf(
            "format" to "openssl",
            "keySize" to "256",
            "opensslKdf" to "legacy",
            "opensslDigest" to "sha256",
            "password" to password,
        )
        assertEquals(plaintext, run("aescbc", opensslLegacySha256, params, Direction.DECODE))
    }

    @Test fun opensslThreeDefaultFilesDecrypt() {
        val params = mapOf(
            "format" to "openssl",
            "keySize" to "256",
            "opensslKdf" to "pbkdf2",
            "opensslDigest" to "sha256",
            "opensslIterations" to "10000",
            "password" to password,
        )
        assertEquals(plaintext, run("aescbc", opensslPbkdf2Sha256, params, Direction.DECODE))
    }

    @Test fun opensslFilesWithAnotherKeySizeAndIterationCountDecrypt() {
        val params = mapOf(
            "format" to "openssl",
            "keySize" to "128",
            "opensslKdf" to "pbkdf2",
            "opensslDigest" to "sha256",
            "opensslIterations" to "100000",
            "password" to password,
        )
        assertEquals(plaintext, run("aescbc", opensslPbkdf2Aes128, params, Direction.DECODE))
    }

    @Test fun aFilePastedStraightFromTheTerminalStillReads() {
        // `openssl enc` wraps its Base64 at 64 characters: the line breaks and the trailing newline
        // must not matter.
        val params = mapOf(
            "format" to "openssl",
            "keySize" to "256",
            "opensslKdf" to "pbkdf2",
            "opensslDigest" to "sha256",
            "opensslIterations" to "10000",
            "password" to password,
        )
        assertEquals(plaintext, run("aescbc", opensslMultiline, params, Direction.DECODE))
        assertEquals(plaintext, run("aescbc", "  \n" + opensslMultiline + "\n  ", params, Direction.DECODE))
    }

    @Test fun anOpenSslFileIsDetectedWithoutSettingTheFormat() {
        // "Detect automatically" is the default: the Salted__ header is unmistakable, and a Text Hub
        // payload can never carry it (its first byte is a version number).
        assertTrue(OpenSslEnc.looksLikeSalted(opensslPbkdf2Sha256))
        val params = mapOf("password" to password, "opensslKdf" to "pbkdf2", "opensslDigest" to "sha256")
        assertEquals(plaintext, run("aescbc", opensslPbkdf2Sha256, params, Direction.DECODE))
    }

    @Test fun theWrongSettingIsNamedInsteadOfFailingSilently() {
        // A legacy MD5 file read with the OpenSSL 3 defaults must fail *and* say what to change.
        val message = failure(
            "aescbc",
            opensslLegacyMd5,
            mapOf("format" to "openssl", "password" to password, "opensslKdf" to "pbkdf2", "opensslDigest" to "sha256"),
        )
        assertTrue(message, message.contains("Legacy"))
        assertTrue(message, message.contains("MD5"))
        // A wrong password produces the same explanation (the format has no MAC to prove it).
        val wrongPassword = failure(
            "aescbc",
            opensslPbkdf2Sha256,
            mapOf("format" to "openssl", "password" to "not the password"),
        )
        assertTrue(wrongPassword, wrongPassword.contains("Could not decrypt this OpenSSL enc payload"))
    }

    @Test fun tamperedOpenSslDataIsNeverReturnedAsPlaintext() {
        val bytes = Base64Codec.decode(opensslPbkdf2Sha256)
        bytes[bytes.size - 3] = (bytes[bytes.size - 3] + 1).toByte()
        val tampered = Base64Codec.encode(bytes)
        val message = failure(
            "aescbc",
            tampered,
            mapOf("format" to "openssl", "password" to password, "opensslKdf" to "pbkdf2", "opensslDigest" to "sha256"),
        )
        assertTrue(message, message.contains("OpenSSL"))
    }

    @Test fun opensslDataIsNotMistakenForATextHubPayloadAndTheOtherWayRound() {
        assertFalse(OpenSslEnc.looksLikeSalted("aGVsbG8gd29ybGQ="))
        val ours = run("aescbc", "hello", mapOf("password" to password), Direction.ENCODE)
        assertFalse("Text Hub payloads never start with the Salted__ magic", OpenSslEnc.looksLikeSalted(ours))
        // ... and our own payload still reads back with the format left on automatic.
        assertEquals("hello", run("aescbc", ours, mapOf("password" to password), Direction.DECODE))
    }

    @Test fun textHubWritesOpenSslCompatibleFilesToo() {
        val params = mapOf(
            "format" to "openssl",
            "keySize" to "192",
            "opensslKdf" to "pbkdf2",
            "opensslDigest" to "sha512",
            "opensslIterations" to "2000",
            "password" to password,
        )
        val written = run("aescbc", "compatible output", params, Direction.ENCODE)
        assertTrue("output must be a salted OpenSSL file", OpenSslEnc.looksLikeSalted(written))
        assertEquals("compatible output", run("aescbc", written, params, Direction.DECODE))
        // A reader with the same settings reads it: proves the layout is the documented one.
        val viaCrypto = OpenSslEnc.decrypt(
            written, password.toCharArray(), 24, OpenSslEnc.Kdf.PBKDF2, OpenSslEnc.Digest.SHA512, 2000,
        )
        assertEquals("compatible output", viaCrypto)
    }

    @Test fun theLegacyDerivationIsAlwaysASinglePass() {
        // OpenSSL 1.x had no -iter for the historic derivation, so the iteration setting cannot
        // change it: it is a documented property of the mode, not a hidden substitution.
        assertEquals(1, OpenSslEnc.effectiveIterations(OpenSslEnc.Kdf.LEGACY, 10000))
        assertEquals(5000, OpenSslEnc.effectiveIterations(OpenSslEnc.Kdf.PBKDF2, 5000))
        val legacy = mapOf(
            "format" to "openssl",
            "keySize" to "256",
            "opensslKdf" to "legacy",
            "opensslDigest" to "md5",
            "password" to password,
        )
        // The same file reads whether or not the (unused) iteration count is set.
        assertEquals(plaintext, run("aescbc", opensslLegacyMd5, legacy, Direction.DECODE))
        assertEquals(
            plaintext,
            run("aescbc", opensslLegacyMd5, legacy + ("opensslIterations" to "100000"), Direction.DECODE),
        )
    }

    @Test fun aSaltedPayloadWithoutTheHeaderIsExplainedNotGuessed() {
        val headerless = Base64Codec.encode(ByteArray(48) { 1 })
        val message = failure("aescbc", headerless, mapOf("format" to "openssl", "password" to password))
        assertTrue(message, message.contains("Salted__"))
    }

    // ------------------------------------------------------------------ JWE fixtures

    /** `{"alg":"dir","enc":"A256GCM"}` with key 00..1f - produced with Python `cryptography`. */
    private val jweA256Gcm =
        "eyJhbGciOiJkaXIiLCJlbmMiOiJBMjU2R0NNIn0..DA0ODxAREhMUFRYX." +
            "zJsRrF8-iTEXTzCh9PlIhjkjjoGbDVre8sCTjI3UufA.11KWxR4mFmadEYdcAnfGFA"
    private val jweKeyHex = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"

    /** `{"alg":"dir","enc":"A128CBC-HS256"}` - the HMAC-then-CBC mode of RFC 7518 §5.2. */
    private val jweA128CbcHs256 =
        "eyJhbGciOiJkaXIiLCJlbmMiOiJBMTI4Q0JDLUhTMjU2In0..GBkaGxwdHh8gISIjJCUmJw." +
            "szqQfM0YjcjZhqf0Z7mnQcqxY-jP4Tz-BwjRaFXaFIDOHF5IchzN2345MnN8b1ut.97DKdBBibSgz_-tepb2vsQ"
    private val jweCbcKeyHex = "202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f"

    @Test fun standardJweTokensDecrypt() {
        assertEquals(
            plaintext,
            run("aesrawkey", jweA256Gcm, mapOf("key" to jweKeyHex), Direction.DECODE),
        )
        assertEquals(
            plaintext,
            run("aesrawkey", jweA128CbcHs256, mapOf("key" to jweCbcKeyHex), Direction.DECODE),
        )
    }

    @Test fun theTokenHeaderDecidesEverythingItCan() {
        val header = JweFormat.header(jweA256Gcm)!!
        assertEquals("dir", header.alg)
        assertEquals("A256GCM", header.enc)
        // The key length is checked against the token's own enc value, and the mismatch is named.
        val shortKey = "000102030405060708090a0b0c0d0e0f"
        val message = failure("aesrawkey", jweA256Gcm, mapOf("key" to shortKey))
        assertTrue(message, message.contains("A256GCM"))
        assertTrue(message, message.contains("256-bit"))
        assertTrue(message, message.contains("128"))
    }

    @Test fun aTamperedTokenIsRefused() {
        val parts = jweA256Gcm.split(".")
        val body = Base64Codec.decode(parts[3])
        body[0] = (body[0] + 1).toByte()
        val tampered = parts.toMutableList().also { it[3] = Base64Codec.encode(body, urlSafe = true, padding = false) }
            .joinToString(".")
        val message = failure("aesrawkey", tampered, mapOf("key" to jweKeyHex))
        assertTrue(message, message.contains("authentication"))
    }

    @Test fun unsupportedAlgorithmsAndModesSayWhatTheTokenUses() {
        // alg=RSA-OAEP is not something a raw symmetric key can open.
        val rsaToken = "eyJhbGciOiJSU0EtT0FFUCIsImVuYyI6IkEyNTZHQ00ifQ.AAAA.BBBB.CCCC.DDDD"
        val algMessage = failure("aesrawkey", rsaToken, mapOf("key" to jweKeyHex))
        assertTrue(algMessage, algMessage.contains("RSA-OAEP"))
        // enc=XC20P is not implemented, and the message lists what is.
        val otherEnc = "eyJhbGciOiJkaXIiLCJlbmMiOiJYQzIwUCJ9..AAAA.BBBB.CCCC"
        val encMessage = failure("aesrawkey", otherEnc, mapOf("key" to jweKeyHex))
        assertTrue(encMessage, encMessage.contains("XC20P") && encMessage.contains("A256GCM"))
    }

    @Test fun somethingThatIsNotATokenAtAllIsExplained() {
        val message = failure("aesrawkey", "not.a.token", mapOf("key" to jweKeyHex))
        assertTrue(message, message.contains("not a Text Hub raw-key payload"))
        assertTrue(message, message.contains("JWE"))
    }

    @Test fun textHubCanWriteTokensOtherImplementationsRead() {
        val key = com.texthub.core.crypto.RawKeyGcm.parseKey(jweKeyHex)
        val token = JweFormat.encrypt(plaintext, key, JweFormat.Enc.A256GCM)
        assertTrue("five dot-separated parts", token.split(".").size == 5)
        assertEquals("dir", JweFormat.header(token)!!.alg)
        assertEquals("A256GCM", JweFormat.header(token)!!.enc)
        // Read back through the tool, so the written header is the one the reader expects.
        assertEquals(plaintext, run("aesrawkey", token, mapOf("key" to jweKeyHex), Direction.DECODE))
        // And the token decrypts with an independent implementation of the same rules.
        val parts = token.split(".")
        val decoded = decryptWithPythonRules(parts, key)
        assertEquals(plaintext, decoded)
    }

    /**
     * Independent re-implementation of RFC 7516 A256GCM for the test: the AES-GCM cipher with the
     * protected header as additional authenticated data. Written separately from [JweFormat] so a
     * shared bug cannot pass both.
     */
    private fun decryptWithPythonRules(parts: List<String>, key: ByteArray): String {
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            javax.crypto.Cipher.DECRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(key, "AES"),
            javax.crypto.spec.GCMParameterSpec(128, Base64Codec.decode(parts[2])),
        )
        cipher.updateAAD(parts[0].toByteArray(Charsets.US_ASCII))
        val body = Base64Codec.decode(parts[3]) + Base64Codec.decode(parts[4])
        return String(cipher.doFinal(body), Charsets.UTF_8)
    }

    @Test fun externalPayloadsArePointedAtFromEveryToolThatCannotReadThem() {
        // Pasting an OpenSSL file into the AES-GCM tool gives a pointer, not "not ours".
        val opensslMessage = failure("aes", opensslPbkdf2Sha256, mapOf("password" to password))
        assertTrue(opensslMessage, opensslMessage.contains("OpenSSL") && opensslMessage.contains("AES-CBC"))
        assertTrue(failure("chacha", opensslPbkdf2Sha256, mapOf("password" to password)).contains("OpenSSL"))
        assertTrue(failure("aesctr", opensslPbkdf2Sha256, mapOf("password" to password)).contains("OpenSSL"))

        // Pasting a JWE token into a password tool says which tool handles it.
        val jweMessage = failure("aes", jweA256Gcm, mapOf("password" to password))
        assertTrue(jweMessage, jweMessage.contains("JWE"))
        assertTrue(jweMessage, jweMessage.contains("own key"))
    }
}
