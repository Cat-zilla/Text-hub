package com.texthub.core.crypto

import com.texthub.core.codec.Base64Codec
import com.texthub.core.model.Errors
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Reading and writing PEM key blocks.
 *
 * Only the two modern containers are accepted, because they are what every current tool produces:
 * `-----BEGIN PUBLIC KEY-----` (X.509 SubjectPublicKeyInfo) and
 * `-----BEGIN PRIVATE KEY-----` (PKCS#8). The older PKCS#1 blocks are detected and reported
 * clearly instead of failing with a confusing message.
 */
object RsaPem {

    const val PUBLIC_BEGIN = "-----BEGIN PUBLIC KEY-----"
    const val PUBLIC_END = "-----END PUBLIC KEY-----"
    const val PRIVATE_BEGIN = "-----BEGIN PRIVATE KEY-----"
    const val PRIVATE_END = "-----END PRIVATE KEY-----"
    private const val PKCS1_PUBLIC = "-----BEGIN RSA PUBLIC KEY-----"
    private const val PKCS1_PRIVATE = "-----BEGIN RSA PRIVATE KEY-----"
    private const val LINE = 64

    data class Presence(val hasPublic: Boolean, val hasPrivate: Boolean, val pkcs1Only: Boolean)

    fun inspect(text: String): Presence {
        val hasPublic = text.contains(PUBLIC_BEGIN) && text.contains(PUBLIC_END)
        val hasPrivate = text.contains(PRIVATE_BEGIN) && text.contains(PRIVATE_END)
        val pkcs1 = !hasPublic && !hasPrivate &&
            (text.contains(PKCS1_PUBLIC) || text.contains(PKCS1_PRIVATE))
        return Presence(hasPublic, hasPrivate, pkcs1)
    }

    fun hasPublicKey(text: String): Boolean = inspect(text).hasPublic
    fun hasPrivateKey(text: String): Boolean = inspect(text).hasPrivate

    /** The X.509 public key from the first public PEM block. */
    fun publicKey(text: String): PublicKey {
        val body = body(text, PUBLIC_BEGIN, PUBLIC_END) ?: throw Errors.rsaNeedPublic()
        return try {
            KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(body))
        } catch (e: Exception) {
            throw Errors.rsaKey()
        }
    }

    /** The PKCS#8 private key from the first private PEM block. */
    fun privateKey(text: String): PrivateKey {
        val body = body(text, PRIVATE_BEGIN, PRIVATE_END) ?: throw Errors.rsaNeedPrivate()
        return try {
            KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(body))
        } catch (e: Exception) {
            throw Errors.rsaKey()
        }
    }

    fun toPem(begin: String, end: String, der: ByteArray): String {
        val base64 = Base64Codec.encode(der)
        val wrapped = base64.chunked(LINE).joinToString("\n")
        return "$begin\n$wrapped\n$end"
    }

    private fun body(text: String, begin: String, end: String): ByteArray? {
        val start = text.indexOf(begin)
        if (start < 0) {
            if (inspect(text).pkcs1Only) throw Errors.rsaPkcs1()
            return null
        }
        val from = start + begin.length
        val stop = text.indexOf(end, from)
        if (stop < 0) return null
        val base64 = text.substring(from, stop).filterNot { it.isWhitespace() }
        if (base64.isEmpty()) return null
        return try {
            Base64Codec.decode(base64)
        } catch (e: Exception) {
            throw Errors.rsaKey()
        }
    }
}

/**
 * Hybrid public-key encryption: RSA-OAEP wraps a fresh AES-256 key, and AES-256-GCM encrypts the
 * message with it.
 *
 * ```
 * 0    1     version    = 0x11
 * 1    1     algorithm  = 0x01 (RSA-OAEP + AES-256-GCM)
 * 2    2     wrapped key length, big endian (256 / 384 / 512 bytes)
 * 4    k     RSA-OAEP encrypted 32 byte AES key
 * 4+k  12    IV / nonce
 * 16+k n     ciphertext || 16 byte GCM authentication tag
 * ```
 *
 * RSA on its own can only encrypt a short block (190 bytes with a 2048-bit key), so the AES key
 * is the only thing that is actually encrypted with RSA - the same construction every messaging
 * protocol uses. Both primitives come straight from the platform provider; nothing is
 * re-implemented here. Any text length is supported.
 */
object RsaHybrid {

    /** Envelope version. Public so the Universal Decoder can identify the format structurally. */
    const val VERSION: Byte = 0x11
    private const val ALGORITHM: Byte = 0x01
    private const val AES_KEY_BYTES = 32
    private const val IV_LEN = 12
    private const val TAG_BITS = 128
    private const val HEADER_LEN = 4
    private const val TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"

    private val random = SecureRandom()

    /**
     * Unwraps a content encryption key of a JWE token (RFC 7516) with the private key.
     * `alg` is the token's own `alg` value: RSA-OAEP uses SHA-1, RSA-OAEP-256 uses SHA-256.
     */
    /**
     * Structural check used by the Universal Decoder: version byte, algorithm byte, and a wrapped-key
     * length that fits the rest of the payload. The GCM tag is still verified on decryption.
     */
    fun looksLikeEnvelope(payloadBase64: String): Boolean {
        val bytes = try {
            Base64Codec.decode(payloadBase64.trim())
        } catch (e: Exception) {
            return false
        }
        if (bytes.size < HEADER_LEN + IV_LEN + 16) return false
        if (bytes[0] != VERSION || bytes[1] != ALGORITHM) return false
        val wrappedLength = ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
        return wrappedLength > 0 && HEADER_LEN + wrappedLength + IV_LEN + 16 <= bytes.size
    }

    fun unwrapJweCek(wrapped: ByteArray, alg: String, keyText: String): ByteArray {
        val privateKey = RsaPem.privateKey(keyText)
        val transformation = when (alg) {
            "RSA-OAEP" -> "RSA/ECB/OAEPWithSHA-1AndMGF1Padding"
            "RSA-OAEP-256" -> TRANSFORMATION
            else -> throw Errors.jweAlg(alg, "RSA-OAEP + AES-GCM")
        }
        return try {
            val cipher = Cipher.getInstance(transformation)
            cipher.init(Cipher.DECRYPT_MODE, privateKey)
            cipher.doFinal(wrapped)
        } catch (e: GeneralSecurityException) {
            throw Errors.rsaDecrypt()
        }
    }

    fun isAvailable(): Boolean = try {
        Cipher.getInstance(TRANSFORMATION)
        true
    } catch (e: GeneralSecurityException) {
        false
    }

    fun encrypt(plaintext: String, keyText: String): String {
        val publicKey = RsaPem.publicKey(keyText)
        val aesKey = ByteArray(AES_KEY_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }

        val wrapped = rsaEncrypt(aesKey, publicKey)
        val ciphertext = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        } catch (e: GeneralSecurityException) {
            throw Errors.aes()
        }

        val payload = ByteArray(HEADER_LEN + wrapped.size + IV_LEN + ciphertext.size)
        payload[0] = VERSION
        payload[1] = ALGORITHM
        payload[2] = ((wrapped.size shr 8) and 0xFF).toByte()
        payload[3] = (wrapped.size and 0xFF).toByte()
        System.arraycopy(wrapped, 0, payload, HEADER_LEN, wrapped.size)
        System.arraycopy(iv, 0, payload, HEADER_LEN + wrapped.size, IV_LEN)
        System.arraycopy(ciphertext, 0, payload, HEADER_LEN + wrapped.size + IV_LEN, ciphertext.size)
        return Base64Codec.encode(payload)
    }

    fun decrypt(payloadBase64: String, keyText: String): String {
        val privateKey = RsaPem.privateKey(keyText)
        val payload = try {
            Base64Codec.decode(payloadBase64.trim())
        } catch (e: Exception) {
            throw Errors.rsaFormat()
        }
        if (payload.size < HEADER_LEN + IV_LEN + 16) throw Errors.rsaFormat()
        if (payload[0] != VERSION || payload[1] != ALGORITHM) throw Errors.rsaFormat()

        val wrappedLength = ((payload[2].toInt() and 0xFF) shl 8) or (payload[3].toInt() and 0xFF)
        if (wrappedLength <= 0 || HEADER_LEN + wrappedLength + IV_LEN + 16 > payload.size) throw Errors.rsaFormat()

        // A private key of another size can never open this message: say so plainly.
        val keyBits = (privateKey as? java.security.interfaces.RSAPrivateKey)?.modulus?.bitLength()
        val payloadBits = when (wrappedLength) {
            256 -> 2048
            384 -> 3072
            512 -> 4096
            else -> null
        }
        if (keyBits != null && payloadBits != null && keyBits != payloadBits) {
            throw Errors.rsaKeySizeMismatch(payloadBits, keyBits)
        }

        val wrapped = payload.copyOfRange(HEADER_LEN, HEADER_LEN + wrappedLength)
        val iv = payload.copyOfRange(HEADER_LEN + wrappedLength, HEADER_LEN + wrappedLength + IV_LEN)
        val ciphertext = payload.copyOfRange(HEADER_LEN + wrappedLength + IV_LEN, payload.size)

        val aesKey = try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, privateKey)
            cipher.doFinal(wrapped)
        } catch (e: GeneralSecurityException) {
            throw Errors.rsaDecrypt()
        }

        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            throw Errors.rsaDecrypt()
        }
    }

    /** RSA key size in bits that this payload was encrypted for, or null. */
    fun keySizeOf(payloadBase64: String): Int? {
        val payload = try {
            Base64Codec.decode(payloadBase64.trim())
        } catch (e: Exception) {
            return null
        }
        if (payload.size < HEADER_LEN + IV_LEN + 16 || payload[0] != VERSION) return null
        val wrappedLength = ((payload[2].toInt() and 0xFF) shl 8) or (payload[3].toInt() and 0xFF)
        return when (wrappedLength) {
            256 -> 2048
            384 -> 3072
            512 -> 4096
            else -> null
        }
    }

    private fun rsaEncrypt(data: ByteArray, publicKey: PublicKey): ByteArray = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        cipher.doFinal(data)
    } catch (e: GeneralSecurityException) {
        throw Errors.rsaKey()
    }
}

/** Generates an RSA key pair on the device and prints it as PEM blocks. */
object RsaKeyGen {

    val SIZES = intArrayOf(2048, 3072, 4096)

    /** Returns `public PEM`, a blank line, `private PEM`, then a short, non-secret summary. */
    fun generate(bits: Int = 2048): String {
        if (bits !in SIZES) throw Errors.rsaSize()
        val pair: KeyPair = try {
            KeyPairGenerator.getInstance("RSA").apply { initialize(bits, SecureRandom()) }.generateKeyPair()
        } catch (e: GeneralSecurityException) {
            throw Errors.rsaKey()
        }
        val publicPem = RsaPem.toPem(RsaPem.PUBLIC_BEGIN, RsaPem.PUBLIC_END, pair.public.encoded)
        val privatePem = RsaPem.toPem(RsaPem.PRIVATE_BEGIN, RsaPem.PRIVATE_END, pair.private.encoded)
        return buildString {
            append(publicPem).append("\n\n").append(privatePem).append("\n\n")
            append("$bits-bit RSA key pair generated on this device.\n")
            append("Public key fingerprint (SHA-256): ").append(fingerprint(pair.public)).append('\n')
            append("Keep the private key secret: anyone who has it can read every message encrypted to this key.\n")
            append("Encrypt with the PUBLIC key, decrypt with the PRIVATE key.\n")
        }
    }

    /** Colon separated SHA-256 fingerprint of the DER-encoded public key. */
    fun fingerprint(key: PublicKey): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.encoded)
        return digest.joinToString(":") { "%02X".format(it.toInt() and 0xFF) }
    }
}
