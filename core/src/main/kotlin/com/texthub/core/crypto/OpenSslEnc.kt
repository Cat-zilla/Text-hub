package com.texthub.core.crypto

import com.texthub.core.codec.Base64Codec
import com.texthub.core.model.Errors
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The `openssl enc` file format, so data written by the OpenSSL command line (or by any tool that
 * produces the same layout) can be read here, and data written here can be read there.
 *
 * Layout:
 *
 * ```
 * base64( "Salted__" || salt(8) || ciphertext )
 * ```
 *
 * with AES-CBC and PKCS#7 padding. The salt is in the file; the key **and** IV are derived from
 * the password:
 *
 * * `Key derivation = PBKDF2` - what OpenSSL 3.x does (and what `-iter` selects):
 *   `key || iv = PBKDF2-HMAC-digest(password, salt, iterations, keyLen + 16)`, 10 000 iterations
 *   by default.
 * * `Key derivation = Legacy` - what OpenSSL 1.1.x did (no `-iter`): the historic
 *   `EVP_BytesToKey`, `D(i) = H^iterations(D(i-1) || password || salt)`, first block first.
 *
 * Neither variant authenticates the ciphertext: this format has no MAC, so a wrong password
 * normally fails with a padding error but tampering cannot be detected the way GCM detects it.
 * That is a property of the format, and the tool says so in its information sheet.
 *
 * Nothing here is invented: both derivations were checked against output of the `openssl` command
 * line (see `ExternalFormatsTest`).
 */
object OpenSslEnc {

    /** The 8 magic bytes every salted OpenSSL `enc` file starts with. */
    const val MAGIC = "Salted__"
    private const val SALT_LEN = 8
    private const val IV_LEN = 16
    private const val HEADER_LEN = MAGIC.length + SALT_LEN

    enum class Kdf(val label: String) {
        /** OpenSSL 3.x: PBKDF2-HMAC with the chosen digest and iteration count. */
        PBKDF2("PBKDF2 (OpenSSL 3.x)"),

        /** OpenSSL 1.x: the historic EVP_BytesToKey chain. */
        LEGACY("Legacy EVP_BytesToKey (OpenSSL 1.x)"),
    }

    /** Digests the OpenSSL command line accepts through `-md`. */
    enum class Digest(val id: String, val label: String, val jceName: String, val hmac: Pbkdf2.Digest) {
        SHA256("sha256", "SHA-256", "SHA-256", Pbkdf2.Digest.SHA256),
        SHA512("sha512", "SHA-512", "SHA-512", Pbkdf2.Digest.SHA512),
        SHA1("sha1", "SHA-1", "SHA-1", Pbkdf2.Digest.SHA1),
        MD5("md5", "MD5", "MD5", Pbkdf2.Digest.MD5),
    }

    val KEY_SIZES = intArrayOf(16, 24, 32)

    private val random = SecureRandom()

    /** True when the text decodes to a salted OpenSSL file. Detection is structural, not a guess. */
    fun looksLikeSalted(text: String): Boolean {
        val bytes = decodeOrNull(text) ?: return false
        if (bytes.size < HEADER_LEN + 16) return false
        return String(bytes, 0, MAGIC.length, Charsets.US_ASCII) == MAGIC
    }

    private fun decodeOrNull(text: String): ByteArray? = try {
        Base64Codec.decode(text)
    } catch (e: Exception) {
        null
    }

    /**
     * Decrypts an OpenSSL `enc` payload.
     *
     * @param digest the `-md` value the file was written with (SHA-256 unless the user says
     *   otherwise); @param kdf which key derivation the writer used; @param iterations the
     *   `-iter` count (ignored by the legacy chain beyond its own repetition count).
     */
    fun decrypt(
        payload: String,
        password: CharArray,
        keySizeBytes: Int,
        kdf: Kdf,
        digest: Digest,
        iterations: Int,
    ): String {
        val bytes = decodeOrNull(payload) ?: throw Errors.openssl()
        if (!looksLikeSalted(payload)) throw Errors.opensslNotSalted()
        val salt = bytes.copyOfRange(MAGIC.length, HEADER_LEN)
        val ciphertext = bytes.copyOfRange(HEADER_LEN, bytes.size)
        val keyLength = if (keySizeBytes in KEY_SIZES) keySizeBytes else 32
        val material = derive(kdf, digest, iterations, password, salt, keyLength + IV_LEN)
        val key = material.copyOfRange(0, keyLength)
        val iv = material.copyOfRange(keyLength, keyLength + IV_LEN)
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            // Wrong password, wrong digest or wrong key derivation all end up here. The message
            // names everything the user can change instead of saying "decryption failed".
            throw Errors.opensslFailed(kdf.label, digest.label, effectiveIterations(kdf, iterations), keyLength * 8)
        } finally {
            key.fill(0)
            iv.fill(0)
            material.fill(0)
        }
    }

    /** Writes a salted OpenSSL `enc` file that `openssl enc -d` reads back. */
    fun encrypt(
        plaintext: String,
        password: CharArray,
        keySizeBytes: Int,
        kdf: Kdf,
        digest: Digest,
        iterations: Int,
    ): String {
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val keyLength = if (keySizeBytes in KEY_SIZES) keySizeBytes else 32
        val material = derive(kdf, digest, iterations, password, salt, keyLength + IV_LEN)
        val key = material.copyOfRange(0, keyLength)
        val iv = material.copyOfRange(keyLength, keyLength + IV_LEN)
        try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val out = ByteArray(HEADER_LEN + ciphertext.size)
            MAGIC.toByteArray(Charsets.US_ASCII).copyInto(out)
            salt.copyInto(out, MAGIC.length)
            ciphertext.copyInto(out, HEADER_LEN)
            return Base64Codec.encode(out)
        } finally {
            key.fill(0)
            iv.fill(0)
            material.fill(0)
        }
    }

    /**
     * The iteration count that actually applies. PBKDF2 uses the number in the settings; the legacy
     * chain is a single pass, because OpenSSL 1.x had no `-iter` flag (the parameter says so in the
     * UI, so this is documented behaviour rather than a hidden substitution).
     */
    fun effectiveIterations(kdf: Kdf, configured: Int): Int =
        if (kdf == Kdf.PBKDF2) configured.coerceAtLeast(1) else 1

    /** key || iv for the OpenSSL layouts. */
    private fun derive(
        kdf: Kdf,
        digest: Digest,
        iterations: Int,
        password: CharArray,
        salt: ByteArray,
        length: Int,
    ): ByteArray = when (kdf) {
        Kdf.PBKDF2 -> Pbkdf2.derive(digest.hmac, password, salt, effectiveIterations(kdf, iterations), length)
        Kdf.LEGACY -> evpBytesToKey(digest, effectiveIterations(kdf, iterations), password, salt, length)
    }

    /**
     * The historic OpenSSL key derivation (EVP_BytesToKey):
     *
     * ```
     * D(0) = empty
     * D(i) = H^iterations( D(i-1) || password || salt )
     * key || iv = D(1) || D(2) || ...
     * ```
     */
    private fun evpBytesToKey(
        digest: Digest,
        iterations: Int,
        password: CharArray,
        salt: ByteArray,
        length: Int,
    ): ByteArray {
        val md = MessageDigest.getInstance(digest.jceName)
        val passwordBytes = password.concatToString().toByteArray(Charsets.UTF_8)
        val out = ByteArray(length)
        var written = 0
        var previous: ByteArray? = null
        while (written < length) {
            md.reset()
            previous?.let { md.update(it) }
            md.update(passwordBytes)
            md.update(salt)
            var block = md.digest()
            for (i in 2..iterations) {
                md.reset()
                md.update(block)
                block = md.digest()
            }
            val toCopy = minOf(block.size, length - written)
            block.copyInto(out, written, 0, toCopy)
            written += toCopy
            previous = block
        }
        passwordBytes.fill(0)
        return out
    }
}
