package com.texthub.core.model

/**
 * Any user-correctable problem is reported as a [ToolException] carrying a friendly,
 * localised-ready message. The UI shows this text directly - never a stack trace.
 */
class ToolException(message: String) : RuntimeException(message)

object Errors {
    fun base64() = ToolException("Invalid Base64 input. Please check the characters and padding.")
    fun base32() = ToolException("Invalid Base32 input. Please check the alphabet and padding.")
    fun base58() = ToolException("Invalid Base58 input. Please check the characters.")
    fun base85() = ToolException("Invalid Base85 input. Check the characters used by the selected variant.")
    fun base45() = ToolException("Invalid Base45 input. Base45 allows digits, A-Z and the symbols space \$%*+-./:")
    fun quotedPrintable() = ToolException("Invalid quoted-printable input. Escapes must look like =3D or =C3=A9.")
    fun unicodeEscape() = ToolException("Invalid escape sequence. Use forms like \\u0041, \\u{1F600} or \\U0001F600.")
    fun base85Length() = ToolException("Z85 requires the data length to be a multiple of 4 bytes. Use Ascii85 for arbitrary text.")
    fun hex() = ToolException("Invalid hexadecimal input. Use characters 0–9 and A–F.")
    fun oddHex() = ToolException("Hex input has an odd number of digits. Each byte needs two digits.")
    fun binary() = ToolException("Binary input must contain only 0 and 1.")
    fun binaryGroup() = ToolException("Binary groups must be 1–8 bits long.")
    fun decimal() = ToolException("Invalid decimal input. Use byte values from 0 to 255 separated by the selected separator.")
    fun octal() = ToolException("Invalid octal input. Use digits 0–7 only.")
    fun url() = ToolException("Invalid URL-encoded input. Check the percent escapes (for example %20).")
    fun unicode() = ToolException("Invalid Unicode code point. Use values from U+0000 to U+10FFFF.")
    fun morse() = ToolException("Invalid Morse input. Use dots, dashes, spaces and / only.")
    fun baudot() = ToolException("Invalid Baudot/ITA2 sequence. Check the code groups and shift characters.")
    fun baudotGroup() = ToolException("Invalid Baudot/ITA2 group. Each group must be exactly 5 bits (0 or 1).")
    fun baudotUnsupported(chars: String) =
        ToolException("Baudot/ITA2 cannot represent: $chars")
    fun ascii() = ToolException("This text contains characters outside standard ASCII.")
    fun asciiValue() = ToolException("Invalid ASCII input. Values must be from 0 to 127.")
    fun missingKey() = ToolException("Please enter a key before processing.")
    fun missingPassword() = ToolException("Please enter a password before processing.")
    fun substitutionLength(expected: Int) =
        ToolException("The substitution alphabet must contain exactly $expected characters.")
    fun substitutionDuplicate() = ToolException("The substitution alphabet must contain unique characters.")
    fun substitutionAlpha() = ToolException("The substitution alphabet may only contain letters.")
    fun cipherParams() = ToolException("These cipher settings are not valid. Please check the supplied key and parameters.")
    fun aes() = ToolException("Unable to decrypt. The password or encrypted data may be incorrect.")
    fun aesFormat() = ToolException("This does not look like Text Hub encrypted data. Paste the complete Base64 payload.")
    fun a1z26() = ToolException("Invalid A1Z26 input. Use numbers from 1 to 26 separated by the selected separator.")
    fun affine() = ToolException("Invalid Affine key. A must be coprime with 26 (for example 3, 5, 7, 9, 11, 15, 17, 19, 21, 23, 25).")
    fun railFence() = ToolException("Invalid rail count. Use at least 2 rails.")
    fun playfair() = ToolException("Playfair requires a key with at least one letter.")
    fun columnar() = ToolException("Columnar transposition requires a key with at least one letter.")
    fun bacon() = ToolException("Invalid Bacon input. Use groups of A and B, five symbols per letter.")
    fun emptyInput() = ToolException("Nothing to process yet - type or paste some text.")
    fun base91() = ToolException("Invalid Base91 input. Use only the characters of the Base91 alphabet.")
    fun punycode() = ToolException("This is not a valid Punycode label. Check the ACE prefix and the digits.")
    fun braille() = ToolException("Invalid Braille input. Expected Braille pattern characters (⠁ to ⣿).")
    fun roman() = ToolException("This is not a valid Roman numeral. Use the standard forms such as IV, IX, XL, CM.")
    fun romanRange() = ToolException("Roman numerals in this tool work from 1 to 3999.")
    fun hexDump() = ToolException("Could not read that as a hex dump. Expected lines like 00000000: 48 65 6c 6c 6f  Hello.")
    fun json() = ToolException("This is not valid JSON. Check the brackets, commas and quotes.")
    fun regex() = ToolException("That regular expression is not valid. Check the pattern syntax.")
    fun hmacFormat() = ToolException("Invalid HMAC input. Paste the tag produced by this tool, in the same format.")
    fun pbkdf2Format() =
        ToolException("This does not look like a stored PBKDF2 hash. Paste the whole line, for example pbkdf2-sha256\u0024210000\u0024salt\u0024hash.")
    fun enigma() = ToolException("Invalid Enigma settings. Use three rotor letters, three ring letters and three position letters, for example AAA.")
    fun enigmaPlugboard() = ToolException("The plugboard takes letter pairs such as AB CD EF, each letter used once, up to 10 pairs.")
    fun porta() = ToolException("The Porta cipher needs a key with at least one letter.")
    fun trifid() = ToolException("The Trifid cipher needs a period of at least 3 and text made of letters or full stops.")
    fun hillKey() =
        ToolException("This key matrix cannot be used: its determinant is not coprime with 26, so the matrix has no inverse. Try another key.")
    fun hillSize(expected: Int) =
        ToolException("The key matrix needs exactly $expected letters, for example HILL.")
    fun scytale() = ToolException("The Scytale needs a diameter of at least 2.")
    fun adfgx() = ToolException("ADFGX/ADFGVX input must use only the letters of the selected alphabet (A D F G X or A D F G V X).")
    fun adfgxKey() = ToolException("ADFGX/ADFGVX needs a square keyword and a column key.")
    fun jwt() = ToolException("This does not look like a JWT. Expected three dot-separated parts (header.payload.signature).")
    fun checksumFormat() = ToolException("Invalid checksum input. Use the value produced by this tool.")
    fun rawKey() = ToolException("This is not a usable AES key. Paste 16, 24 or 32 bytes as Base64 or as hex.")
    fun rawKeyFormat() = ToolException("This does not look like a key from this tool. Paste the complete Base64 payload.")
    fun rsaKey() = ToolException("This is not a readable RSA key. Paste a complete PEM block, including the BEGIN and END lines.")
    fun rsaNeedPublic() = ToolException("Encrypting needs the recipient's public key: paste the -----BEGIN PUBLIC KEY----- block.")
    fun rsaNeedPrivate() = ToolException("Decrypting needs your private key: paste the -----BEGIN PRIVATE KEY----- block.")
    fun rsaDecrypt() = ToolException("Unable to decrypt. The private key does not match this message, or the data was changed.")
    fun rsaSize() = ToolException("Choose an RSA key size of 2048, 3072 or 4096 bits.")
    fun rsaPkcs1() = ToolException("This is an older PKCS#1 RSA key. Convert it to PKCS#8 / X.509 first, or generate a fresh pair with the RSA Key Pair Generator.")
    fun rsaFormat() = ToolException("This does not look like an RSA message from this tool. Paste the complete Base64 payload.")
    fun rsaUnsupported() = ToolException("RSA-OAEP is not available on this device, so this tool cannot run here.")
    fun aesKeySizeMismatch(declaredBits: Int) = ToolException(
        "This message was encrypted with a $declaredBits-bit AES key, but the key size selected here is different. " +
            "Set AES key size to $declaredBits bits and try again."
    )
    fun rawKeySizeMismatch(storedBits: Int, providedBits: Int) = ToolException(
        "This message was encrypted with a $storedBits-bit AES key, but the key you pasted is $providedBits bits. " +
            "Use a $storedBits-bit key."
    )
    fun rsaKeySizeMismatch(payloadBits: Int, keyBits: Int) = ToolException(
        "This message was encrypted for a $payloadBits-bit RSA key, but the key you pasted is $keyBits bits. " +
            "Paste the private key that belongs to the public key it was encrypted to."
    )
    fun natoUnsupported(chars: String) = ToolException(
        "The spelling alphabet can only spell the 26 English letters and the digits 0-9. These characters have no word: $chars"
    )
    fun tooLarge(limit: Int) = ToolException("Input is very large (over $limit characters). Try processing a smaller piece of text.")
}
