package com.texthub.core.codec

import com.texthub.core.model.Errors
import java.util.zip.Adler32

/**
 * Small non-cryptographic integrity checks. A checksum detects accidental corruption such as a
 * mistyped digit - it says nothing about tampering, because anyone can recompute it.
 */
object Checksums {

    val KINDS = listOf("CRC-32", "Adler-32")

    fun compute(text: String, kind: String): Long {
        val bytes = text.toByteArray(Charsets.UTF_8)
        return when (kind) {
            "Adler-32" -> {
                val adler = Adler32()
                adler.update(bytes)
                adler.value
            }
            else -> {
                val crc = java.util.zip.CRC32()
                crc.update(bytes)
                crc.value
            }
        }
    }

    /** Returns the value as the conventional unsigned decimal string. */
    fun computeDecimal(text: String, kind: String): String = compute(text, kind).toString()

    /** Returns the value in the usual fixed-width hexadecimal form (8 digits, uppercase). */
    fun computeHex(text: String, kind: String): String =
        java.lang.Long.toHexString(compute(text, kind)).padStart(8, '0').uppercase()
}

/**
 * Base91 (Joachim Henke's alphabet). Sixteen bits of input become either two or three printable
 * characters, which makes it denser than Base85 while staying copy-paste safe.
 */
object Base91Codec {

    private const val ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!\"#\$%&'()*+,-./:;<=>?@[]^_`{|}~"

    private val REVERSE = IntArray(128) { -1 }.also { table ->
        ALPHABET.forEachIndexed { index, c -> table[c.code] = index }
    }

    fun encode(data: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (byte in data) {
            buffer = buffer or ((byte.toInt() and 0xFF) shl bits)
            bits += 8
            if (bits > 13) {
                var value = buffer and 8191
                if (value > 88) {
                    buffer = buffer ushr 13
                    bits -= 13
                } else {
                    value = buffer and 16383
                    buffer = buffer ushr 14
                    bits -= 14
                }
                sb.append(ALPHABET[value % 91]).append(ALPHABET[value / 91])
            }
        }
        if (bits > 0) {
            sb.append(ALPHABET[buffer % 91])
            if (bits > 7 || buffer / 91 > 0) sb.append(ALPHABET[buffer / 91])
        }
        return sb.toString()
    }

    fun decode(input: String): ByteArray {
        val text = input.filterNot { it.isWhitespace() }
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        var value = -1
        for (raw in text) {
            val index = if (raw.code < 128) REVERSE[raw.code] else -1
            if (index < 0) throw Errors.base91()
            if (value < 0) {
                value = index
            } else {
                value += index * 91
                buffer = buffer or (value shl bits)
                bits += if ((value and 8191) > 88) 13 else 14
                while (bits > 7) {
                    out.write(buffer and 0xFF)
                    buffer = buffer ushr 8
                    bits -= 8
                }
                value = -1
            }
        }
        if (value >= 0) {
            // A final single character contributes its 6-bit value.
            buffer = buffer or (value shl bits)
            val remaining = minOf(bits + 6, 8)
            if (remaining >= 8) out.write(buffer and 0xFF)
        }
        return out.toByteArray()
    }
}
