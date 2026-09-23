package com.texthub.core.crypto

import com.texthub.core.codec.Base64Codec
import com.texthub.core.model.Errors
import java.security.MessageDigest
import javax.crypto.Cipher
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * JWE compact serialisation (RFC 7516 / RFC 7518) - the `header.key.iv.ciphertext.tag` token shape
 * used by JOSE libraries, JWT tooling and API gateways.
 *
 * The token says what it needs: the protected header (base64url JSON) carries `alg` and `enc`, so
 * the content encryption key length, the IV length and the tag length are all determined by the
 * payload rather than guessed. Text Hub reads:
 *
 * | `alg` | meaning |
 * | --- | --- |
 * | `dir` | the key you paste *is* the content encryption key |
 * | `RSA-OAEP`, `RSA-OAEP-256` | the content encryption key is wrapped with the RSA key you paste |
 *
 * and supports every standard AES content encryption mode: `A128GCM`, `A192GCM`, `A256GCM`,
 * `A128CBC-HS256`, `A192CBC-HS384`, `A256CBC-HS512`. Anything else is refused with a sentence that
 * names what the token uses.
 *
 * Text Hub can also *write* compact tokens (`alg=dir`), so a token produced here opens in any
 * standards-compliant JOSE implementation.
 */
object JweFormat {

    enum class Mode { GCM, CBC_HMAC }

    /** Content encryption algorithms from RFC 7518 §5. */
    enum class Enc(
        val id: String,
        val cekLength: Int,
        val ivLength: Int,
        val tagLength: Int,
        val mode: Mode,
        val hmac: String? = null,
    ) {
        A128GCM("A128GCM", 16, 12, 16, Mode.GCM),
        A192GCM("A192GCM", 24, 12, 16, Mode.GCM),
        A256GCM("A256GCM", 32, 12, 16, Mode.GCM),
        A128CBC_HS256("A128CBC-HS256", 32, 16, 16, Mode.CBC_HMAC, "HmacSHA256"),
        A192CBC_HS384("A192CBC-HS384", 48, 16, 24, Mode.CBC_HMAC, "HmacSHA384"),
        A256CBC_HS512("A256CBC-HS512", 64, 16, 32, Mode.CBC_HMAC, "HmacSHA512"),
    }

    val supportedIds: String = Enc.values().joinToString(", ") { it.id }

    data class Header(val alg: String, val enc: String, val kid: String?)

    private val random = SecureRandom()

    // ------------------------------------------------------------------ detection + header

    /** True when the text has the shape of a compact JWE and its header names alg and enc. */
    /**
     * The key-wrapping algorithms Text Hub implements. A token that uses anything else is *named*
     * (algorithm and content encryption included) rather than guessed at; the reader never pretends
     * it can unwrap what it cannot.
     */
    val SUPPORTED_ALGS: Set<String> = setOf("dir", "RSA-OAEP", "RSA-OAEP-256")

    /** True when this build can open a token with that key-wrapping algorithm. */
    fun isSupportedAlg(alg: String): Boolean = alg in SUPPORTED_ALGS

    fun looksLikeJwe(text: String): Boolean = header(text) != null

    /** The protected header, or null when this is not a readable compact JWE. */
    fun header(text: String): Header? {
        val parts = split(text) ?: return null
        val json = decodePart(parts[0])?.toString(Charsets.UTF_8) ?: return null
        if (!json.trimStart().startsWith("{")) return null
        val alg = jsonString(json, "alg") ?: return null
        val enc = jsonString(json, "enc") ?: return null
        return Header(alg, enc, jsonString(json, "kid"))
    }

    private fun split(text: String): List<String>? {
        val trimmed = text.trim()
        val parts = trimmed.split('.')
        if (parts.size != 5) return null
        if (parts.any { it.isEmpty() && it !== parts[1] }) return null
        return parts
    }

    private fun decodePart(part: String): ByteArray? = try {
        Base64Codec.decode(part)
    } catch (e: Exception) {
        null
    }

    /** Minimal reader for a flat JSON object of strings - the JWE header is exactly that. */
    private fun jsonString(json: String, key: String): String? {
        var index = 0
        while (true) {
            val at = json.indexOf("\"$key\"", index)
            if (at < 0) return null
            val afterKey = json.indexOf(':', at + key.length + 2)
            if (afterKey < 0) return null
            val open = json.indexOf('"', afterKey + 1)
            if (open < 0) return null
            val close = json.indexOf('"', open + 1)
            if (close < 0) return null
            val candidate = json.substring(open + 1, close)
            // Make sure we matched a key and not this text inside another value.
            val before = json.substring(0, at).trimEnd()
            if (before.endsWith("{") || before.endsWith(",")) return candidate
            index = close
        }
    }

    // ------------------------------------------------------------------ decrypt

    /**
     * Decrypts a token whose content encryption key is the pasted key (`alg: dir`).
     * The key length must match what the token's `enc` requires - a mismatch is named, never
     * silently stretched or truncated.
     */
    fun decrypt(token: String, key: ByteArray): String {
        val header = header(token) ?: throw Errors.jweFormat()
        if (header.alg != "dir") throw Errors.jweAlg(header.alg, "AES-GCM with your own key")
        val spec = specOf(header.enc)
        if (key.size != spec.cekLength) throw Errors.jweKeyLength(header.enc, spec.cekLength * 8, key.size * 8)
        return decrypt(token, header, spec, key) { key }
    }

    /**
     * Decrypts a token whose content encryption key is wrapped with an RSA key. [unwrap] turns the
     * token's encrypted-key part into the content encryption key.
     */
    fun decryptWithUnwrap(token: String, unwrap: (encryptedKey: ByteArray, alg: String) -> ByteArray): String {
        val header = header(token) ?: throw Errors.jweFormat()
        if (header.alg != "RSA-OAEP" && header.alg != "RSA-OAEP-256") {
            throw Errors.jweAlg(header.alg, "RSA-OAEP + AES-GCM")
        }
        val spec = specOf(header.enc)
        return decrypt(token, header, spec, null) { encryptedKey -> unwrap(encryptedKey, header.alg) }
    }

    private fun decrypt(
        token: String,
        header: Header,
        spec: Enc,
        directKey: ByteArray?,
        unwrap: (ByteArray) -> ByteArray,
    ): String {
        val parts = split(token) ?: throw Errors.jweFormat()
        val iv = decodePart(parts[2]) ?: throw Errors.jweFormat()
        val ciphertext = decodePart(parts[3]) ?: throw Errors.jweFormat()
        val tag = decodePart(parts[4]) ?: throw Errors.jweFormat()
        if (iv.size != spec.ivLength) throw Errors.jweFormat()
        if (tag.size != spec.tagLength) throw Errors.jweFormat()

        val cek = try {
            directKey ?: unwrap(decodePart(parts[1]) ?: throw Errors.jweFormat())
        } catch (e: GeneralSecurityException) {
            throw Errors.rsaDecrypt()
        }
        if (cek.size != spec.cekLength) throw Errors.jweKeyLength(header.enc, spec.cekLength * 8, cek.size * 8)

        val aad = parts[0].toByteArray(Charsets.US_ASCII)
        return try {
            when (spec.mode) {
                Mode.GCM -> {
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(cek, "AES"), GCMParameterSpec(spec.tagLength * 8, iv))
                    cipher.updateAAD(aad)
                    String(cipher.doFinal(ciphertext + tag), Charsets.UTF_8)
                }
                Mode.CBC_HMAC -> {
                    val macKeyLength = spec.cekLength / 2
                    val macKey = cek.copyOfRange(0, macKeyLength)
                    val encKey = cek.copyOfRange(macKeyLength, spec.cekLength)
                    // Verify before decrypting: an unauthenticated CBC decryption is never exposed.
                    val mac = Mac.getInstance(spec.hmac!!)
                    mac.init(SecretKeySpec(macKey, spec.hmac))
                    mac.update(aad)
                    mac.update(iv)
                    mac.update(ciphertext)
                    mac.update(byteArrayOf((aad.size.toLong() * 8 shr 56).toByte(), (aad.size.toLong() * 8 shr 48).toByte(),
                        (aad.size.toLong() * 8 shr 40).toByte(), (aad.size.toLong() * 8 shr 32).toByte(),
                        (aad.size.toLong() * 8 shr 24).toByte(), (aad.size.toLong() * 8 shr 16).toByte(),
                        (aad.size.toLong() * 8 shr 8).toByte(), (aad.size.toLong() * 8).toByte()))
                    val expected = mac.doFinal().copyOfRange(0, spec.tagLength)
                    if (!MessageDigest.isEqual(expected, tag)) throw Errors.jweAuth()
                    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey, "AES"), IvParameterSpec(iv))
                    String(cipher.doFinal(ciphertext), Charsets.UTF_8)
                }
            }
        } catch (e: GeneralSecurityException) {
            throw Errors.jweAuth()
        } finally {
            cek.fill(0)
        }
    }

    // ------------------------------------------------------------------ encrypt

    /**
     * Writes a compact JWE with `alg=dir` and the requested content encryption algorithm, so the
     * result opens in any RFC 7516 implementation.
     */
    fun encrypt(plaintext: String, key: ByteArray, enc: Enc = Enc.A256GCM, kid: String? = null): String {
        if (key.size != enc.cekLength) throw Errors.jweKeyLength(enc.id, enc.cekLength * 8, key.size * 8)
        val protected = buildString {
            append("{\"alg\":\"dir\",\"enc\":\"").append(enc.id).append('"')
            if (kid != null) append(",\"kid\":\"").append(kid).append('"')
            append('}')
        }
        val protectedPart = Base64Codec.encode(protected.toByteArray(Charsets.UTF_8), urlSafe = true, padding = false)
        val iv = ByteArray(enc.ivLength).also { random.nextBytes(it) }
        val aad = protectedPart.toByteArray(Charsets.US_ASCII)

        val body: ByteArray
        val tag: ByteArray
        if (enc.mode == Mode.GCM) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(enc.tagLength * 8, iv))
            cipher.updateAAD(aad)
            val all = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            body = all.copyOfRange(0, all.size - enc.tagLength)
            tag = all.copyOfRange(all.size - enc.tagLength, all.size)
        } else {
            val macKeyLength = enc.cekLength / 2
            val macKey = key.copyOfRange(0, macKeyLength)
            val encKey = key.copyOfRange(macKeyLength, enc.cekLength)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(encKey, "AES"), IvParameterSpec(iv))
            body = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val mac = Mac.getInstance(enc.hmac!!)
            mac.init(SecretKeySpec(macKey, enc.hmac))
            mac.update(aad)
            mac.update(iv)
            mac.update(body)
            val bitLength = aad.size.toLong() * 8
            mac.update(ByteArray(8) { i -> (bitLength shr (56 - 8 * i)).toByte() })
            tag = mac.doFinal().copyOfRange(0, enc.tagLength)
        }
        return listOf(
            protectedPart,
            "",
            Base64Codec.encode(iv, urlSafe = true, padding = false),
            Base64Codec.encode(body, urlSafe = true, padding = false),
            Base64Codec.encode(tag, urlSafe = true, padding = false),
        ).joinToString(".")
    }

    private fun specOf(enc: String): Enc =
        Enc.values().firstOrNull { it.id == enc } ?: throw Errors.jweEnc(enc, supportedIds)
}
