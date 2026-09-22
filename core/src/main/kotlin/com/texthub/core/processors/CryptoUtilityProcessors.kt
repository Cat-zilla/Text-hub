package com.texthub.core.processors

import com.texthub.core.TextProcessor
import com.texthub.core.codec.Base64Codec
import com.texthub.core.codec.Checksums
import com.texthub.core.crypto.Digests
import com.texthub.core.crypto.Hmacs
import com.texthub.core.crypto.Pbkdf2Hash
import com.texthub.core.model.Choice
import com.texthub.core.model.Classification
import com.texthub.core.model.Direction
import com.texthub.core.model.Errors
import com.texthub.core.model.ParamKind
import com.texthub.core.model.ParamSpec
import com.texthub.core.model.ToolCategory
import com.texthub.core.model.ToolException
import com.texthub.core.model.ToolInfo
import com.texthub.core.model.ToolMeta
import com.texthub.core.util.toHex

/**
 * Cryptographic hashes. Hashing is one-way - it cannot be undone, and it is not encryption.
 * The digest direction prints the fingerprint; the reverse direction checks a value you already
 * have against a text, which is how a download checksum is used.
 */
class HashProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "hash",
        name = "Hash (one-way)",
        glyph = "#",
        category = ToolCategory.CRYPTO,
        classification = Classification.CRYPTOGRAPHIC_HASH,
        encodeLabel = "Hash",
        decodeLabel = "Verify",
        params = listOf(
            ParamSpec(
                key = "algorithm",
                label = "Algorithm",
                kind = ParamKind.CHOICE,
                defaultValue = "SHA-256",
                choices = Digests.ALGORITHMS.map {
                    Choice(it, if (it == "MD5" || it == "SHA-1") "$it (broken for security, checksums only)" else it)
                },
            ),
            ParamSpec(
                key = "format",
                label = "Output",
                kind = ParamKind.CHOICE,
                defaultValue = "hex",
                choices = listOf(
                    Choice("hex", "Hex (lowercase)"),
                    Choice("hexUpper", "Hex (UPPERCASE)"),
                    Choice("base64", "Base64"),
                ),
            ),
            ParamSpec(
                key = "compare",
                label = "Text to verify against",
                kind = ParamKind.TEXT,
                defaultValue = "",
                hint = "Used by Verify",
                helper = "Verify hashes this text and compares it with the fingerprint in the input box.",
            ),
        ),
        info = ToolInfo(
            summary = "SHA-256, SHA-512, SHA-3 and friends: a fingerprint of your text, always the " +
                "same length, impossible to reverse. MD5 and SHA-1 are included for checking old " +
                "download checksums and are labelled as broken for anything security related.",
            requiresKey = false,
            useCases = listOf(
                "Checking that a file or text arrived unchanged",
                "Producing a short fingerprint to quote in a document",
            ),
            warnings = listOf(
                "A hash is one-way: there is nothing to decrypt, and no password involved.",
                "Never use MD5 or SHA-1 where an attacker chooses the input.",
                "A hash does not protect secrecy - it reveals a fingerprint of the exact text.",
            ),
            convention = "Digests are computed over the UTF-8 bytes of the input.",
        ),
        keywords = listOf("hash", "sha256", "sha512", "sha3", "md5", "fingerprint", "digest", "checksum"),
        resultIsFinal = true,
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val algorithm = params["algorithm"] ?: "SHA-256"
        if (direction == Direction.ENCODE) {
            if (input.isEmpty()) throw Errors.emptyInput()
            val digest = Digests.hash(input, algorithm)
            return when (params["format"] ?: "hex") {
                "base64" -> Base64Codec.encode(digest)
                "hexUpper" -> digest.toHex(upper = true)
                else -> digest.toHex(upper = false)
            }
        }
        val expected = input.trim().replace(Regex("\\s+"), "").lowercase()
        if (expected.isEmpty()) throw Errors.emptyInput()
        val target = params["compare"] ?: ""
        if (target.isEmpty()) {
            throw ToolException(
                "Type the text you want to check into \"Text to verify against\" - a hash cannot " +
                    "be reversed, so the original text has to be hashed again for comparison."
            )
        }
        val actual = Digests.hash(target, algorithm).toHex(upper = false)
        val normalized = expected.removePrefix("0x")
        return if (normalized == actual) {
            "Match. The text hashes to $actual with $algorithm."
        } else {
            "No match.\nExpected: $normalized\nActual:   $actual ($algorithm)"
        }
    }
}

/** HMAC: a keyed fingerprint that proves a message came from someone who holds the key. */
class HmacProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "hmac",
        name = "HMAC",
        glyph = "MAC",
        category = ToolCategory.CRYPTO,
        classification = Classification.MESSAGE_AUTHENTICATION,
        encodeLabel = "Sign",
        decodeLabel = "Verify",
        params = listOf(
            ParamSpec(
                key = "key",
                label = "Shared secret key",
                kind = ParamKind.PASSWORD,
                required = true,
                defaultValue = "",
                hint = "Both sides must use the same key",
                sensitive = true,
                helper = "The key is never stored. A tag can only be reproduced with the same key.",
            ),
            ParamSpec(
                key = "algorithm",
                label = "Algorithm",
                kind = ParamKind.CHOICE,
                defaultValue = "HmacSHA256",
                choices = Hmacs.ALGORITHMS.map { Choice(it, it.removePrefix("Hmac")) },
            ),
            ParamSpec(
                key = "format",
                label = "Output",
                kind = ParamKind.CHOICE,
                defaultValue = "hex",
                choices = listOf(Choice("hex", "Hex (lowercase)"), Choice("base64", "Base64")),
            ),
            ParamSpec(
                key = "message",
                label = "Message to verify",
                kind = ParamKind.TEXT,
                defaultValue = "",
                hint = "Used by Verify",
                helper = "Verify recomputes the tag for this message and compares it with the input.",
            ),
        ),
        info = ToolInfo(
            summary = "HMAC mixes a shared secret key into a hash, producing a tag that changes " +
                "if either the message or the key changes. It proves integrity and that the sender " +
                "holds the key - it does not hide the message.",
            requiresKey = true,
            useCases = listOf(
                "Checking that a message was not altered in transit",
                "Learning the difference between a hash and a MAC",
            ),
            warnings = listOf(
                "A MAC is not encryption: the message stays readable, only its integrity is protected.",
                "Anyone with the key can produce the same tag, so the key must stay secret.",
            ),
            convention = "Tags are compared in constant time, so a wrong tag cannot be found byte by byte.",
        ),
        keywords = listOf("hmac", "mac", "authentication", "integrity", "sign", "verify"),
        resultIsFinal = true,
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val key = params["key"] ?: ""
        if (key.isEmpty()) throw Errors.missingKey()
        val algorithm = params["algorithm"] ?: "HmacSHA256"
        if (direction == Direction.ENCODE) {
            if (input.isEmpty()) throw Errors.emptyInput()
            val tag = Hmacs.compute(input, key, algorithm)
            return when (params["format"] ?: "hex") {
                "base64" -> Base64Codec.encode(tag)
                else -> tag.toHex(upper = false)
            }
        }
        val message = params["message"] ?: ""
        if (message.isEmpty()) {
            throw ToolException(
                "Type the original message into \"Message to verify\" - a tag can only be checked " +
                    "against the message it was created for."
            )
        }
        val tag = input.trim().replace(Regex("\\s+"), "")
        // Accept either the hex or the Base64 form this tool produces.
        val ok = if (tag.isNotEmpty() && tag.all { com.texthub.core.util.hexDigit(it) >= 0 }) {
            Hmacs.verify(message, key, algorithm, tag)
        } else {
            val decoded = try {
                Base64Codec.decode(tag)
            } catch (e: Exception) {
                throw Errors.hmacFormat()
            }
            Hmacs.verify(message, key, algorithm, decoded.toHex(upper = false))
        }
        return if (ok) {
            "Match. The tag is valid for this message and key ($algorithm)."
        } else {
            "No match. Either the message or the key is different from the one that produced this tag."
        }
    }
}

/** PBKDF2 password hashing: keeps a password checkable without keeping the password. */
class Pbkdf2Processor : TextProcessor {

    override val meta = ToolMeta(
        id = "pbkdf2",
        name = "PBKDF2 Password Hash",
        glyph = "KDF",
        category = ToolCategory.CRYPTO,
        classification = Classification.CRYPTOGRAPHIC_HASH,
        encodeLabel = "Hash password",
        decodeLabel = "Check password",
        params = listOf(
            ParamSpec(
                key = "password",
                label = "Password",
                kind = ParamKind.PASSWORD,
                required = true,
                defaultValue = "",
                hint = "The password to hash or check",
                sensitive = true,
                helper = "Deliberately slow: 210,000 iterations, a fresh random salt, never stored.",
            ),
            ParamSpec(
                key = "algorithm",
                label = "Hash",
                kind = ParamKind.CHOICE,
                defaultValue = "PBKDF2WithHmacSHA256",
                choices = listOf(
                    Choice("PBKDF2WithHmacSHA256", "PBKDF2-HMAC-SHA256 (recommended)"),
                    Choice("PBKDF2WithHmacSHA512", "PBKDF2-HMAC-SHA512"),
                    Choice("PBKDF2WithHmacSHA1", "PBKDF2-HMAC-SHA1 (legacy)"),
                ),
            ),
        ),
        info = ToolInfo(
            summary = "Turns a password into a stored value that can be checked later but not " +
                "reversed. Each password gets its own random salt and 210,000 PBKDF2 iterations, " +
                "which is what makes off-line guessing slow.",
            requiresKey = true,
            useCases = listOf(
                "Storing a password check for a local app",
                "Understanding why a raw hash is not enough for passwords",
            ),
            warnings = listOf(
                "One-way by design: the password cannot be recovered from the stored value, only checked.",
                "Keep the whole stored line - the salt and iteration count are part of it.",
            ),
            convention = "Stored form: pbkdf2-sha256\$210000\$salt\$hash, all parts Base64 (unpadded).",
        ),
        keywords = listOf("pbkdf2", "password", "salt", "iterations", "kdf", "hash", "store"),
        resultIsFinal = true,
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val password = (params["password"] ?: "").toCharArray()
        if (password.isEmpty()) throw Errors.missingPassword()
        val algorithm = params["algorithm"] ?: "PBKDF2WithHmacSHA256"
        try {
            if (direction == Direction.ENCODE) {
                if (password.size < 8) {
                    throw ToolException(
                        "Use at least 8 characters for a password you are going to store. " +
                            "Longer is better."
                    )
                }
                return Pbkdf2Hash.store(password, algorithm)
            }
            if (input.isBlank()) throw Errors.emptyInput()
            val stored = input.trim().lines().firstOrNull { it.trim().startsWith("pbkdf2-") }
                ?: input.trim()
            val ok = try {
                Pbkdf2Hash.verify(password, stored)
            } catch (e: ToolException) {
                throw Errors.pbkdf2Format()
            }
            return if (ok) {
                "Match. This password produces the stored hash with the stored salt and iteration count."
            } else {
                "No match. This password is not the one that produced the stored hash."
            }
        } finally {
            password.fill('\u0000')
        }
    }
}

/** CRC-32 and Adler-32: quick integrity checks that detect typos, not tampering. */
class ChecksumProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "checksum",
        name = "Checksum (CRC-32 / Adler-32)",
        glyph = "CRC",
        category = ToolCategory.CRYPTO,
        classification = Classification.CHECKSUM,
        encodeLabel = "Compute",
        decodeLabel = "Check",
        params = listOf(
            ParamSpec(
                key = "kind",
                label = "Checksum",
                kind = ParamKind.CHOICE,
                defaultValue = "CRC-32",
                choices = Checksums.KINDS.map { Choice(it, it) },
            ),
            ParamSpec(
                key = "format",
                label = "Output",
                kind = ParamKind.CHOICE,
                defaultValue = "both",
                choices = listOf(
                    Choice("both", "Decimal and hex"),
                    Choice("decimal", "Decimal only"),
                    Choice("hex", "Hex only"),
                ),
            ),
            ParamSpec(
                key = "compare",
                label = "Text to check against",
                kind = ParamKind.TEXT,
                defaultValue = "",
                hint = "Used by Check",
            ),
        ),
        info = ToolInfo(
            summary = "Fast, non-cryptographic checks that catch accidental corruption such as a " +
                "mistyped digit. Anyone can recompute them, so they prove nothing about tampering.",
            requiresKey = false,
            useCases = listOf(
                "Checking a long number was copied correctly",
                "Spotting a corrupted line in a text file",
            ),
            warnings = listOf(
                "Not a hash and not security: an attacker can change the text and the checksum together.",
                "Use HMAC or AES-GCM when you need to detect deliberate changes.",
            ),
            convention = "CRC-32 and Adler-32 as defined by RFC 1950, computed over UTF-8 bytes.",
        ),
        keywords = listOf("crc32", "adler32", "checksum", "integrity", "corruption"),
        resultIsFinal = true,
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String {
        val kind = params["kind"] ?: "CRC-32"
        if (direction == Direction.ENCODE) {
            if (input.isEmpty()) throw Errors.emptyInput()
            val decimal = Checksums.computeDecimal(input, kind)
            val hex = Checksums.computeHex(input, kind)
            return when (params["format"] ?: "both") {
                "decimal" -> decimal
                "hex" -> "0x$hex"
                else -> "decimal: $decimal\nhex:     0x$hex"
            }
        }
        val expected = input.trim()
        if (expected.isEmpty()) throw Errors.emptyInput()
        val target = params["compare"] ?: ""
        if (target.isEmpty()) {
            throw ToolException(
                "Type the text to check into \"Text to check against\" so the checksum can be " +
                    "recomputed and compared."
            )
        }
        val decimal = Checksums.computeDecimal(target, kind)
        val hex = Checksums.computeHex(target, kind)
        val normalized = expected.lowercase().removePrefix("0x").trim()
        val matches = normalized == decimal || normalized == hex.lowercase() ||
            normalized == hex.lowercase().trimStart('0') || normalized == "0x$hex".lowercase()
        return if (matches) {
            "Match. $kind of the text is $decimal (0x$hex)."
        } else {
            "No match.\nExpected: $normalized\nActual:   $decimal (0x$hex)"
        }
    }
}
