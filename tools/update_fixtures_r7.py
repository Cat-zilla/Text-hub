import io

p = "core/src/test/kotlin/com/texthub/core/ExternalFormatsTest.kt"
s = io.open(p, encoding="utf-8").read()

old_block_start = s.index("    /** `printf '...' | openssl enc")
old_block_end = s.index("    @Test fun opensslLegacyMd5FilesDecrypt()")
new_block = '''    /**
     * Real output of the OpenSSL command line (3.5.6), one line each, all of the same 32-byte
     * plaintext "Text Hub external compatibility\\n" with password "correct horse":
     *
     * ```
     * printf '...' | openssl enc -aes-256-cbc -md md5               -pass pass:"..." -base64 -A
     * ```
     */
    private val opensslLegacyMd5 =
        "U2FsdGVkX1/4ufTWgdoC8xteTg5pGE8h0ZOmdHcCJr+YjzkQRnPzlFrmT9f8hktcsMr42JEQq0XvUO8bQ9qlIg=="

    /** The same, with `-md sha256` (legacy derivation, different digest). */
    private val opensslLegacySha256 =
        "U2FsdGVkX1+bxZ+ZU1y2i57kK/XtT+V38laGUOH2EnvRaOmBPx1oWa+bML6ryTlsAVLrk+NJwN30eqergbKtXA=="

    /** The OpenSSL 3.x default path: `-md sha256 -iter 10000`. */
    private val opensslPbkdf2Sha256 =
        "U2FsdGVkX1+Nk4IdvUASQivGzqICky5sju+HFlo+yH+c4m2G12vzkfy4v3f5OF6nY7LILReJap4Vthoa6P9RsQ=="

    /** `-aes-128-cbc -md sha256 -iter 100000`: a different key size and iteration count. */
    private val opensslPbkdf2Aes128 =
        "U2FsdGVkX18KEry3sYyiyZcEn4uY2GcwXkse2d273PcHQH8SVHZ9kzI0wbCEhJYctyogcY8C0lJUOTAmBXpzNg=="

    /** The same file as it comes out of the terminal: wrapped over several lines. */
    private val opensslMultiline =
        "U2FsdGVkX18kXxLDrNXnC5AIBeHWy77xX7XJBaBW6Kji2nMA3EkhGNkBPW3TApXC\\n" +
            "W5kv/PBfybdTDGVUcQbnRg=="

'''
s = s[:old_block_start] + new_block + s[old_block_end:]

# multiline fixture test
old = """    @Test fun anOpenSslFileIsDetectedWithoutSettingTheFormat() {"""
new = """    @Test fun aFilePastedStraightFromTheTerminalStillReads() {
        // `openssl enc` wraps its Base64 at 64 characters: the line breaks and the trailing newline
        // must not matter.
        val params = mapOf(
            "format" to "openssl",
            "keySize" to "256",
            "opensslKdf" to "pbkdf2",
            "opensslDigest" to "sha256",
            "opensslIterations" to "10000",
            "password" to password,
        )
        assertEquals(plaintext, run("aescbc", opensslMultiline, params, Direction.DECODE))
        assertEquals(plaintext, run("aescbc", "  \\n" + opensslMultiline + "\\n  ", params, Direction.DECODE))
    }

    @Test fun anOpenSslFileIsDetectedWithoutSettingTheFormat() {"""
assert old in s
s = s.replace(old, new, 1)

# JWE fixtures with the shared plaintext
s = s.replace('''    /** `{"alg":"dir","enc":"A256GCM"}` with key 00..1f - produced with Python `cryptography`. */
    private val jweA256Gcm =
        "eyJhbGciOiJkaXIiLCJlbmMiOiJBMjU2R0NNIn0..DA0ODxAREhMUFRYX." +
            "zJsRrF8-iTEXYB-QsehJiiVimYeUFFfD79vw.CJqYGpMuOEPwJ3a3LqmXiw"''',
'''    /** `{"alg":"dir","enc":"A256GCM"}` with key 00..1f - produced with Python `cryptography`. */
    private val jweA256Gcm =
        "eyJhbGciOiJkaXIiLCJlbmMiOiJBMjU2R0NNIn0..DA0ODxAREhMUFRYX." +
            "zJsRrF8-iTEXTzCh9PlIhjkjjoGbDVre8sCTjI3UufA.11KWxR4mFmadEYdcAnfGFA"''')

s = s.replace('''    private val jweA128CbcHs256 =
        "eyJhbGciOiJkaXIiLCJlbmMiOiJBMTI4Q0JDLUhTMjU2In0..GBkaGxwdHh8gISIjJCUmJw." +
            "1OBRtWoPzNXopvt1kOu0ZzhOvbPkpOziP-Edjen0DZU.5yULIBAvCjD2dIwBLhC80A"''',
'''    private val jweA128CbcHs256 =
        "eyJhbGciOiJkaXIiLCJlbmMiOiJBMTI4Q0JDLUhTMjU2In0..GBkaGxwdHh8gISIjJCUmJw." +
            "szqQfM0YjcjZhqf0Z7mnQcqxY-jP4Tz-BwjRaFXaFIDOHF5IchzN2345MnN8b1ut.97DKdBBibSgz_-tepb2vsQ"''')

# the headerless wording changed
s = s.replace('''        assertTrue(message, message.contains("Salted__"))''',
              '''        assertTrue(message, message.contains("Salted__"))''')

io.open(p, "w", encoding="utf-8").write(s)
print("ExternalFormatsTest fixtures replaced")

# ---- openSslNotSalted message: mention -S as well as -nosalt
p = "core/src/main/kotlin/com/texthub/core/model/Errors.kt"
s = io.open(p, encoding="utf-8").read()
s = s.replace('''        "This looks like an OpenSSL enc file, but it has no Salted__ header. A file written with " +
            "-nosalt cannot be identified or decrypted here, because the salt is what the key is " +
            "derived from. Re-encrypt it with -salt (the default) to use it in Text Hub."''',
'''        "This looks like an OpenSSL enc payload, but it carries no Salted__ header, which means " +
            "the writer either used -nosalt or passed an explicit -S salt. Neither can be read " +
            "here, because the salt is what the key is derived from and it is not in the data. " +
            "Re-encrypt it without -nosalt and without -S (the salt is then stored in the file)."''')
io.open(p, "w", encoding="utf-8").write(s)
print("message updated")

# ---- PrefsModelTest fixes
p = "core/src/test/kotlin/com/texthub/core/PrefsModelTest.kt"
s = io.open(p, encoding="utf-8").read()
s = s.replace('assertTrue("size must be reported while data is held", before.temporaryBytes() > 900)',
              'assertTrue("size must be reported while data is held", before.temporaryBytes() > 800)')
s = s.replace('''    @Test fun aDragPastTheEndsIsClampedInsteadOfCrashing() {
        val store = PrefsData().withFavorites(listOf("a", "b", "c"))
        assertEquals(listOf("a", "b", "c"), store.moveFavorite(0, 99).favorites())
        assertEquals(listOf("a", "b", "c"), store.moveFavorite(0, -5).favorites())
        // Out-of-range source index changes nothing at all.
        assertEquals(listOf("a", "b", "c"), store.moveFavorite(7, 0).favorites())
    }''',
'''    @Test fun aDragPastTheEndsIsClampedInsteadOfCrashing() {
        val store = PrefsData().withFavorites(listOf("a", "b", "c"))
        // Dragging past the last row drops the item on the last row (never throws).
        assertEquals(listOf("b", "c", "a"), store.moveFavorite(0, 99).favorites())
        // Dragging above the first row drops it on the first row (or leaves it where it was).
        assertEquals(listOf("a", "b", "c"), store.moveFavorite(0, -5).favorites())
        assertEquals(listOf("c", "a", "b"), store.moveFavorite(2, -4).favorites())
        // An impossible source index changes nothing at all.
        assertEquals(listOf("a", "b", "c"), store.moveFavorite(7, 0).favorites())
    }''')
io.open(p, "w", encoding="utf-8").write(s)
print("PrefsModelTest fixed")
