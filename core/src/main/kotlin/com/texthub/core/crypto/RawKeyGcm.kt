package com.texthub.core.crypto

import com.texthub.core.codec.Base64Codec
import com.texthub.core.model.Errors
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-GCM with a key you already have, instead of one derived from a password.
 *
 * Payload layout, Base64 encoded as a whole:
 *
 * ```
 * 0   1   version    = 0x05
 * 1   1   key length = 16, 24 or 32 bytes (AES-128 / AES-192 / AES-256)
 * 2   12  IV / nonce (random, per message)
 * 14  n   ciphertext || 16 byte GCM authentication tag
 * ```
 *
 * This tool is for people who manage keys themselves - a key produced by another system, a
 * password manager, or a key file. There is no key derivation and therefore no salt: the key is
 * used exactly as pasted, which is why the key length is the only thing stored in the header.
 * Nothing about the key is written to disk or to a log.
 */
object RawKeyGcm {

    /** Envelope version. Public so the Universal Decoder can identify the format structurally. */
    const val VERSION: Byte = 0x05
    private const val IV_LEN = 12
    private const val TAG_BITS = 128
    private const val HEADER_LEN = 2
    private const val PREFIX_LEN = HEADER_LEN + IV_LEN

    /** Supported AES key sizes in bytes; 16 = AES-128, 24 = AES-192, 32 = AES-256. */
    val KEY_SIZES = intArrayOf(16, 24, 32)

    private val random = SecureRandom()

    /**
     * Reads a key written as Base64 or as hex (spaces, colons and dashes are ignored). The result
     * must be 16, 24 or 32 bytes long; anything else is refused with a friendly message.
     */
    fun parseKey(text: String): ByteArray {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) throw Errors.missingKey()
        val compact = cleaned.filterNot { it == ' ' || it == '\n' || it == '\r' || it == '\t' || it == ':' || it == '-' }
        val bytes = if (compact.isNotEmpty() && compact.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' } && compact.length % 2 == 0) {
            hexToBytes(compact)
        } else {
            try {
                Base64Codec.decode(cleaned.filterNot { it == '\n' || it == '\r' || it == ' ' })
            } catch (e: Exception) {
                throw Errors.rawKey()
            }
        }
        if (bytes.size !in KEY_SIZES) throw Errors.rawKey()
        return bytes
    }

    /** Key size in bits for the given key text, for display next to the field. */
    fun keyBits(text: String): Int = parseKey(text).size * 8

    fun encrypt(plaintext: String, keyText: String): String {
        val key = parseKey(keyText)
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }

        val cipher = try {
            Cipher.getInstance("AES/GCM/NoPadding")
        } catch (e: GeneralSecurityException) {
            throw Errors.aes()
        }
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val payload = ByteArray(PREFIX_LEN + ciphertext.size)
        payload[0] = VERSION
        payload[1] = key.size.toByte()
        System.arraycopy(iv, 0, payload, HEADER_LEN, IV_LEN)
        System.arraycopy(ciphertext, 0, payload, PREFIX_LEN, ciphertext.size)
        return Base64Codec.encode(payload)
    }

    fun decrypt(payloadBase64: String, keyText: String): String {
        val key = parseKey(keyText)
        val payload = try {
            Base64Codec.decode(payloadBase64.trim())
        } catch (e: Exception) {
            throw Errors.rawKeyFormat()
        }
        if (payload.size < PREFIX_LEN + 16) throw Errors.rawKeyFormat()
        if (payload[0] != VERSION) throw Errors.rawKeyFormat()

        val storedLength = payload[1].toInt() and 0xFF
        if (storedLength !in KEY_SIZES) throw Errors.rawKeyFormat()
        if (storedLength != key.size) {
            // Name both sizes instead of a generic failure.
            throw Errors.rawKeySizeMismatch(storedLength * 8, key.size * 8)
        }

        val iv = payload.copyOfRange(HEADER_LEN, PREFIX_LEN)
        val ciphertext = payload.copyOfRange(PREFIX_LEN, payload.size)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            throw Errors.aes()
        }
    }

    /** Structural check: version byte, a recorded key length and the documented minimum length. */
    fun looksLikeEnvelope(payloadBase64: String): Boolean = keyLengthOf(payloadBase64) != null

    /** Key size in bytes that a payload announces, or null when it is not a raw-key payload. */
    fun keyLengthOf(payloadBase64: String): Int? {
        val payload = try {
            Base64Codec.decode(payloadBase64.trim())
        } catch (e: Exception) {
            return null
        }
        if (payload.size < PREFIX_LEN + 16 || payload[0] != VERSION) return null
        val length = payload[1].toInt() and 0xFF
        return if (length in KEY_SIZES) length else null
    }

    private fun hexToBytes(hex: String): ByteArray {
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            val value = hex.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: throw Errors.rawKey()
            out[i] = value.toByte()
        }
        return out
    }
}
