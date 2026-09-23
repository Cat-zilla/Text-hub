package com.texthub.core.detector

import com.texthub.core.crypto.JweFormat
import com.texthub.core.crypto.RsaPem
import com.texthub.core.util.hexDigit

/**
 * The syntax tests and wordings that more than one tool's detection hint needs.
 *
 * They live together so that a hint can stay a *declaration* - "the values are 4-digit hexadecimal
 * units", "the header says alg=dir" - instead of turning into a block of parsing code, and so that
 * two tools cannot drift apart on what an escape sequence or a JWT part is.
 */

/** `\u0041`, `\u{1F600}`, `\U0001F600` and `\x41`. */
internal val ESCAPE_PATTERN =
    Regex("\\\\u\\{[0-9a-fA-F]{1,6}}|\\\\U[0-9a-fA-F]{8}|\\\\u[0-9a-fA-F]{4}|\\\\x[0-9a-fA-F]{2}")

/** A named, decimal or hexadecimal HTML character reference: `&amp;`, `&#65;`, `&#x41;`. */
internal val ENTITY_PATTERN = Regex("&(#\\d{1,7}|#x[0-9a-fA-F]{1,6}|[a-zA-Z]{2,8});")

/** The `=XX` escapes of quoted-printable. */
internal val QP_ESCAPE_PATTERN = Regex("=[0-9A-Fa-f]{2}")

/** The ACE prefix the DNS adds to an internationalised domain label. */
internal const val ACE_PREFIX = "xn--"

/** Every label of a domain (or of a pasted list of labels), in order. */
internal fun domainLabels(text: String): List<String> = text.trim()
    .split('.', ' ', '\n')
    .filter { it.isNotBlank() }

/** The labels of [text] that carry the ACE prefix. */
internal fun aceLabels(text: String): List<String> =
    domainLabels(text).filter { it.startsWith(ACE_PREFIX, ignoreCase = true) }

/** True when every value of a separated list is a hexadecimal number of exactly [width] digits. */
internal fun tokensAre(text: String, width: Int): Boolean {
    val tokens = text.split(Regex("[\\s,;:|]+")).filter { it.isNotEmpty() }
    return tokens.size >= 2 && tokens.all { token -> token.length == width && token.all { hexDigit(it) >= 0 } }
}

/** The parts of a three-part JWT, or null when the text is not one. */
internal fun jwtParts(text: String): List<String>? {
    val parts = text.trim().split('.')
    return parts.takeIf { it.size == 3 && it[0].startsWith("eyJ") }
}

/** How a JWE token is named in a report: algorithm and content encryption, both from the header. */
internal fun jweLabelFor(text: String): String? = JweFormat.header(text)?.let { header ->
    "JWE compact token (alg=${header.alg}, enc=${header.enc})"
}

/** True for a JWE token whose key-wrapping algorithm this build does not implement. */
internal fun unsupportedJwe(text: String): Boolean =
    JweFormat.header(text)?.let { !JweFormat.isSupportedAlg(it.alg) } == true

/** Why an unsupported JWE token is only named: the algorithm, in the token's own words. */
internal fun unsupportedJweBecause(text: String): String {
    val header = JweFormat.header(text) ?: return "This does not look like a readable JWE token."
    return "Five dot-separated Base64URL parts whose protected header names alg=${header.alg} and " +
        "enc=${header.enc}. Text Hub reads JWE tokens with alg=dir or alg=RSA-OAEP; this token uses " +
        "${header.alg}. Nothing is guessed about it."
}

/** How key material is named when it is recognised but cannot be processed as a message. */
internal fun pemLabelFor(text: String): String {
    val presence = RsaPem.inspect(text)
    return when {
        presence.hasPrivate && presence.hasPublic -> "RSA key pair (PEM)"
        presence.hasPrivate -> "RSA private key (PEM)"
        presence.hasPublic -> "RSA public key (PEM)"
        else -> "RSA key material (PEM)"
    }
}
