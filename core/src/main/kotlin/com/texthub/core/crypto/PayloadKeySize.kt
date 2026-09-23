package com.texthub.core.crypto

/**
 * A keyed tool whose payloads may predate the recorded key size can answer one question about them:
 * *which key size does this payload actually authenticate with?*
 *
 * It exists so the Universal Decoder never has to special-case a format. When a candidate needs a
 * password and its tool declares a key-size setting that the payload did not record, the decoder
 * asks the tool itself through this interface - the format answering a question about its own
 * envelope, using the same reader it uses for decryption, rather than a second implementation.
 *
 * Tools that always record their key size (or have no such setting) simply do not implement it.
 */
interface PayloadKeySize {

    /**
     * The AES key size in bytes that opens [payloadBase64] with [password], or null when the payload
     * records the size itself, is not a payload of this format, or cannot be authenticated.
     * The password is used for the attempt only and is never stored.
     */
    fun keySizeOf(payloadBase64: String, password: CharArray): Int?
}
