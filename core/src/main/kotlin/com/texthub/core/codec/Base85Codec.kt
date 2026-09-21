package com.texthub.core.codec

import com.texthub.core.model.Errors

/**
 * Base85 support:
 *  - "ascii85"  : Adobe/btoa Ascii85 (4 bytes -> 5 printable chars, partial groups supported)
 *  - "ascii85z" : Ascii85 with the "z" shortcut for four zero bytes
 *  - "z85"      : ZeroMQ Z85 (requires input length to be a multiple of 4 bytes)
 */
object Base85Codec {

    const val Z85_ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ.-:+=^!/*?&<>()[]{}@%$#"

    enum class Variant(val id: String, val label: String) {
        ASCII85("ascii85", "Ascii85"),
        ASCII85_Z("ascii85z", "Ascii85 (z compression)"),
        Z85("z85", "Z85 (ZeroMQ)"),
    }

    fun variantFor(id: String): Variant = Variant.values().firstOrNull { it.id == id } ?: Variant.ASCII85

    fun encode(data: ByteArray, variant: Variant): String {
        if (variant == Variant.Z85) {
            if (data.isEmpty()) return ""
            if (data.size % 4 != 0) throw Errors.base85Length()
            val sb = StringBuilder((data.size / 4) * 5)
            var i = 0
            while (i < data.size) {
                var value = 0L
                for (j in 0..3) value = (value shl 8) or (data[i + j].toLong() and 0xFF)
                for (j in 4 downTo 0) {
                    val div = pow85(j)
                    sb.append(Z85_ALPHABET[((value / div) % 85).toInt()])
                }
                i += 4
            }
            return sb.toString()
        }

        val sb = StringBuilder()
        var i = 0
        while (i < data.size) {
            val remaining = data.size - i
            if (variant == Variant.ASCII85_Z && remaining >= 4 &&
                data[i] == 0.toByte() && data[i + 1] == 0.toByte() &&
                data[i + 2] == 0.toByte() && data[i + 3] == 0.toByte()
            ) {
                sb.append('z')
                i += 4
                continue
            }
            var value = 0L
            for (j in 0..3) value = (value shl 8) or (if (j < remaining) (data[i + j].toLong() and 0xFF) else 0L)
            val charsToEmit = remaining.coerceAtMost(4) + 1
            for (j in 4 downTo 5 - charsToEmit) {
                sb.append((33 + ((value / pow85(j)) % 85).toInt()).toChar())
            }
            i += 4
        }
        return sb.toString()
    }

    fun decode(input: String, variant: Variant): ByteArray {
        if (variant == Variant.Z85) {
            val out = ArrayList<Byte>(input.length * 4 / 5 + 4)
            var i = 0
            while (i < input.length) {
                if (input.length - i < 5) throw Errors.base85()
                var value = 0L
                for (j in 0..4) {
                    val idx = Z85_ALPHABET.indexOf(input[i + j])
                    if (idx < 0) throw Errors.base85()
                    value = value * 85 + idx
                }
                if (value > 0xFFFFFFFFL) throw Errors.base85()
                out.add(((value ushr 24) and 0xFF).toByte())
                out.add(((value ushr 16) and 0xFF).toByte())
                out.add(((value ushr 8) and 0xFF).toByte())
                out.add((value and 0xFF).toByte())
                i += 5
            }
            return ByteArray(out.size) { out[it] }
        }

        val out = ArrayList<Byte>(input.length * 4 / 5 + 4)
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c.isWhitespace() || c == '<' || c == '~' || c == '>') { i++; continue }
            if (c == 'z') {
                if (variant != Variant.ASCII85_Z) throw Errors.base85()
                repeat(4) { out.add(0) }
                i++
                continue
            }
            val tuple = StringBuilder()
            var j = i
            while (j < input.length && tuple.length < 5) {
                val ch = input[j]
                if (ch.isWhitespace()) { j++; continue }
                if (ch.code < 33 || ch.code > 117) throw Errors.base85()
                tuple.append(ch)
                j++
            }
            if (tuple.isEmpty()) break
            // A short final group is completed with 'u' (84), the Ascii85 padding digit,
            // so the value lines up with the encoder's zero padding.
            var value = 0L
            for (k in 0 until 5) {
                val digit = if (k < tuple.length) tuple[k].code - 33 else 84
                value = value * 85 + digit
            }
            if (value > 0xFFFFFFFFL) throw Errors.base85()
            val bytesOut = tuple.length - 1
            for (b in 0 until bytesOut) {
                out.add(((value ushr (8 * (3 - b))) and 0xFF).toByte())
            }
            i = j
        }
        return ByteArray(out.size) { out[it] }
    }

    private fun pow85(n: Int): Long {
        var v = 1L
        repeat(n) { v *= 85 }
        return v
    }
}
