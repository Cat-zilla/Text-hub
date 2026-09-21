package com.texthub.core.codec

import com.texthub.core.model.Errors

/**
 * Base32 flavours:
 *  - "standard"  : RFC 4648 alphabet A-Z2-7
 *  - "hex"       : RFC 4648 extended hex alphabet 0-9A-V
 *  - "crockford" : Crockford base32 (no I, L, O or U), tolerant when decoding
 */
object Base32Codec {

    private const val STANDARD = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private const val HEX = "0123456789ABCDEFGHIJKLMNOPQRSTUV"
    private const val CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    const val VARIANT_STANDARD = "standard"
    const val VARIANT_HEX = "hex"
    const val VARIANT_CROCKFORD = "crockford"

    fun alphabet(variant: String): String = when (variant) {
        VARIANT_HEX -> HEX
        VARIANT_CROCKFORD -> CROCKFORD
        else -> STANDARD
    }

    /** Crockford decoding treats I and L as 1 and O as 0. */
    private fun normalize(c: Char, variant: String): Char {
        if (variant != VARIANT_CROCKFORD) return c
        return when (c) {
            'I', 'L', 'i', 'l' -> '1'
            'O', 'o' -> '0'
            else -> c
        }
    }

    fun encode(data: ByteArray, variant: String = VARIANT_STANDARD, padding: Boolean = true): String {
        val table = alphabet(variant)
        val sb = StringBuilder(((data.size + 4) / 5) * 8)
        var i = 0
        while (i < data.size) {
            val remaining = data.size - i
            var bits = 0L
            var bitCount = 0
            for (j in 0 until 5) {
                bits = (bits shl 8) or (if (j < remaining) (data[i + j].toLong() and 0xFF) else 0L)
                bitCount += 8
            }
            // emit ceil(remaining*8/5) characters
            val charsToEmit = when (remaining) {
                1 -> 2
                2 -> 4
                3 -> 5
                4 -> 7
                else -> 8
            }
            val shiftStart = 40
            for (c in 0 until 8) {
                if (c < charsToEmit) {
                    val shift = shiftStart - 5 - (c * 5)
                    val idx = ((bits ushr shift) and 0x1F).toInt()
                    sb.append(table[idx])
                } else if (padding) {
                    sb.append('=')
                }
            }
            i += 5
        }
        var out = sb.toString()
        if (!padding) out = out.trimEnd('=')
        return out
    }

    /** Bit-accumulator decoder: validates the alphabet, padding position and group length. */
    fun decode(input: String, variant: String = VARIANT_STANDARD): ByteArray {
        val table = alphabet(variant)
        val s = StringBuilder()
        for (raw in input) {
            if (raw.isWhitespace()) continue
            // Crockford Base32 officially ignores hyphens (handy for grouped serial numbers).
            if (raw == '-' && variant == VARIANT_CROCKFORD) continue
            if (raw == '=') { s.append(raw); continue }
            val u = normalize(raw, variant).uppercaseChar()
            if (u in table) s.append(u) else throw Errors.base32()
        }
        val eq = s.indexOf('=')
        if (eq >= 0 && s.substring(eq).any { it != '=' }) throw Errors.base32()
        val body = (if (eq >= 0) s.substring(0, eq) else s.toString()).toString()

        val out = ArrayList<Byte>(body.length * 5 / 8 + 1)
        var buffer = 0
        var bits = 0
        for (c in body) {
            buffer = (buffer shl 5) or table.indexOf(c)
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.add(((buffer ushr bits) and 0xFF).toByte())
            }
        }
        // Leftover bits must be 0..4; 5/6/7 leftovers mean a truncated/invalid final group.
        if (bits !in 0..4) throw Errors.base32()
        return ByteArray(out.size) { out[it] }
    }
}
