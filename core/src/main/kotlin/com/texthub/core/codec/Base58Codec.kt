package com.texthub.core.codec

import com.texthub.core.model.Errors

/**
 * Base58 (big-integer style, as used by Bitcoin and friends).
 * Leading zero bytes are preserved as leading '1' characters.
 */
object Base58Codec {

    const val BITCOIN = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    const val RIPPLE = "rpshnaf39wBUDNEGHJKLM4PQRST7VWXYZ2bcdeCg65jkm8oFqi1tuvAxyz"
    const val FLICKR = "123456789abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ"

    fun alphabetFor(id: String): String = when (id) {
        "ripple" -> RIPPLE
        "flickr" -> FLICKR
        else -> BITCOIN
    }

    fun encode(data: ByteArray, alphabet: String = BITCOIN): String {
        if (data.isEmpty()) return ""
        var leadingZeros = 0
        while (leadingZeros < data.size && data[leadingZeros] == 0.toByte()) leadingZeros++

        val digits = ByteArray(data.size * 138 / 100 + 1)
        var digitsLen = 0
        var i = leadingZeros
        while (i < data.size) {
            var carry = data[i].toInt() and 0xFF
            var j = 0
            while (j < digitsLen || carry != 0) {
                if (j == digitsLen) digitsLen++
                carry += (digits[j].toInt() and 0xFF) shl 8
                digits[j] = (carry % 58).toByte()
                carry /= 58
                j++
            }
            i++
        }
        val sb = StringBuilder()
        repeat(leadingZeros) { sb.append(alphabet[0]) }
        for (k in 0 until digitsLen) sb.append(alphabet[digits[digitsLen - 1 - k].toInt() and 0xFF])
        return sb.toString()
    }

    fun decode(input: String, alphabet: String = BITCOIN): ByteArray {
        if (input.isEmpty()) return ByteArray(0)
        for (c in input) if (c !in alphabet) throw Errors.base58()

        var leadingOnes = 0
        while (leadingOnes < input.length && input[leadingOnes] == alphabet[0]) leadingOnes++

        val bytes = ByteArray(input.length * 733 / 1000 + 1)
        var bytesLen = 0
        var i = leadingOnes
        while (i < input.length) {
            var carry = alphabet.indexOf(input[i])
            var j = 0
            while (j < bytesLen || carry != 0) {
                if (j == bytesLen) bytesLen++
                carry += 58 * (bytes[j].toInt() and 0xFF)
                bytes[j] = (carry and 0xFF).toByte()
                carry = carry shr 8
                j++
            }
            i++
        }
        val result = ByteArray(leadingOnes + bytesLen)
        var pos = 0
        repeat(leadingOnes) { result[pos++] = 0 }
        for (k in 0 until bytesLen) result[pos++] = bytes[bytesLen - 1 - k]
        return result
    }
}
