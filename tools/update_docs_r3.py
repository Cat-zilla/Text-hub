#!/usr/bin/env python3
"""Round 3 documentation pass: 69 tools, AES key sizes, new payload notes.

Idempotent-ish: every replacement asserts on a unique anchor so a failed match is loud.
"""
import io
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def patch(path: str, pairs: list[tuple[str, str]]) -> None:
    p = ROOT / path
    s = p.read_text(encoding="utf-8")
    for old, new in pairs:
        if new in s and old not in s:
            print(f"  skip (already applied): {path} :: {old[:60]!r}")
            continue
        assert old in s, f"anchor not found in {path}: {old[:80]!r}"
        s = s.replace(old, new, 1)
    p.write_text(s, encoding="utf-8")
    print(f"patched {path}")


# ---------------------------------------------------------------- TOOLS.md ----
CryptoRows = """| Hash (MD5 / SHA-1 / SHA-256 / SHA-512) | HASH | – | Algorithm, output format (hex, HEX, Base64); optional expected digest to verify against |
| HMAC (SHA-1 / SHA-256 / SHA-512) | MAC | Secret key | Algorithm, tag format, message to verify the tag against |
| PBKDF2 Password Hash | HASH | Password | Algorithm (SHA-1 / SHA-256 / SHA-512); 210 000 iterations, 16-byte random salt |
| Checksum (CRC-32 / Adler-32) | CKSUM | – | Kind, output format (hex / decimal), optional expected value to verify against |"""

ClassicalRows = """| Hill Cipher (3×3) | CLS | 9-letter matrix | Key (9 letters, invertible mod 26), padding (pad with X / reject) |
| Porta Cipher | CLS | Keyword | Secret key (reciprocal: the same operation deciphers) |
| Trifid Cipher | CLS | Keyword | Keyword, period ≥ 3, cube alphabet (letters or merged I/J + space) |
| Scytale | CLS | Diameter | Diameter 2…20 (a rail-fence style wrap on a cylinder) |
| ADFGX / ADFGVX | CLS | Two keywords | Alphabet (ADFGX / ADFGVX), square key, column key — both keys are needed to decode |
| Enigma Machine | CLS | Machine settings | Reflector B/C, three rotors from I–V, ring settings, start positions, plugboard |
| Vigenère / Beaufort (custom) | CLS | Keyword | Variant (Vigenère / Beaufort / Variant Beaufort), key, alphabet (26 or 36 characters) |"""

EncodingRows = """| Base91 | ENC | – | basE91 alphabet (Joachim Henke); whitespace ignored when decoding |
| Punycode | ENC | – | RFC 3492 IDN conversion; the `xn--` prefix belongs to DNS, not to the encoding |
| Braille Bits | ENC | – | One braille cell per hexadecimal nibble, four cells per 16-bit code unit |
| Roman Numerals | ENC | – | 1…3999 in standard subtractive notation; tokens that are not numbers stay as they are |
| UTF-16 Units | ENC | – | 16-bit code units (surrogates included) as hex, decimal or `\\uXXXX` escapes |
| UTF-32 Code Points | ENC | – | One number per code point as hex, decimal or `\\U00000000` escapes |
| Hex Dump | ENC | – | xxd-style 16-byte rows: offset, hex bytes, `|ASCII|` gutter |"""

TransformRows = """| JSON Formatter | TRF | – | Indent 2 spaces / indent tabs / minify / list key paths; parse errors name the line |
| Regex Tester | TRF | – | Pattern, find / replace / split / highlight, replacement text, flags (ignore case, multiline, dot-all) |
| Text Diff | TRF | – | Two versions separated by a separator line; case sensitivity; reports unchanged, removed and added lines |
| Text Statistics | TRF | – | Characters, UTF-16 units, code points, UTF-8 bytes, words, unique words, sentences, paragraphs |
| JWT Inspector | TRF | – | Decodes header and payload and reports `alg` and `exp`; never verifies a signature |"""

Examples = """Hill (3x3)    ACT (key GYBNQKURP)        -> POH
Hill (3x3)    HELLO                      -> HELLOX   (one X of padding is part of the ciphertext)
Porta         ABC (key MNO)              -> TUW
Trifid        ABC (key ABC, period 3)    -> AAF
Scytale       HELLOWORLD (diameter 3)    -> HLODEORLWL
Enigma        AAAAA  (rotors I-II-III, UKW-B, rings AAA, positions AAA)
                                         -> BDZGO
Enigma        AAAAA  (same, rings BBB)   -> EWTYX
Enigma        ATTACK (positions AAZ, plugboard "AM TG")
                                         -> RXWKBV
Vigenere cust. ATTACKATDAWN (key LEMON, Vigenere variant) -> LXFOPVEFRNHR
Punycode      münchen                    -> mnchen-3ya
Base91        a                          -> GB
Roman         1969                       -> MCMLXIX
Braille       A                          -> U+2800 U+2800 U+2804 U+2801
UTF-16        A😀 (hex units)            -> 0041 D83D DE00
UTF-32        A😀 (hex)                  -> 00000041 0001F600
Hex dump      Hello                      -> 00000000: 48 65 6c 6c 6f                                  |Hello|
SHA-256       abc                        -> ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
SHA-256       abc (Base64 output)        -> ungWv48Bz+pBQUDeXa4iI7ADYaOWF3qctBD/YfIAFa0=
HMAC-SHA256   "The quick brown fox jumps over the lazy dog" (key "key")
                                         -> f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8
CRC-32        "The quick brown fox jumps over the lazy dog" -> 0x414FA339
Adler-32      "The quick brown fox jumps over the lazy dog" -> 0x5BDC0FDA
PBKDF2        "correct horse"            -> pbkdf2-sha256$210000$<salt>$<hash>   (salt and hash are Base64)
JSON          {"a":1,"b":[true,null,"x"]} (indent 2 spaces)
                                         -> {
                                              "a": 1,
                                              "b": [
                                                true,
                                                null,
                                                "x"
                                              ]
                                            }
Regex         "Order 1234 shipped, order 5678 pending" (pattern order\\s+(\\d+), ignore case)
                                         -> 2 matches, first group $1=1234
Regex         "Order 1234 shipped, order 5678 pending" (pattern \\d+, replace with ****)
                                         -> Order **** shipped, order **** pending
Regex         a1b2c (pattern \\d, split)    -> a\\nb\\nc
Text diff     "one/two/three/---/one/2/three/four" (separator line ---)
                                         -> reports "unchanged", "- two", "+ 2", "+ four"
Text stats    "Hello world. Hello there!\\n\\nSecond paragraph."
                                         -> Words: 6, Unique words: 5, Sentences: 3, Paragraphs: 2, hello × 2
JWT           header {"alg":"HS256"}…    -> header and payload decoded, alg shown with a note that
                                            the signature is never verified"""

patch(
    "docs/TOOLS.md",
    [
        (
            "Every method available in Text Hub, with its classification, key requirement, parameters and a\nworked example.",
            "Every one of the **69 methods** available in Text Hub, with its classification, key\nrequirement, parameters and a worked example.",
        ),
        (
            "`SEC` authenticated encryption.",
            "`SEC` authenticated encryption · `HASH` cryptographic hash (one-way) · `MAC` message\nauthentication code · `CKSUM` checksum (integrity only).",
        ),
        (
            "| AES-GCM Encryption | SEC | Password | – |",
            "| AES-GCM Encryption | SEC | Password | Key size (AES-128 / AES-192 / AES-256, default AES-256) |",
        ),
        (
            "| AES-CBC + HMAC | SEC | Password | – |",
            "| AES-CBC + HMAC | SEC | Password | Key size (AES-128 / AES-192 / AES-256, default AES-256) |",
        ),
        (
            "| ChaCha20-Poly1305 | SEC | Password | – (Android 9+) |",
            "| ChaCha20-Poly1305 | SEC | Password | – (Android 9+; 256-bit key) |\n" + CryptoRows,
        ),
        (
            "| Polybius Square | CLS | Optional keyword | Grid (5×5 I/J or 6×6 letters+digits), separator |",
            "| Polybius Square | CLS | Optional keyword | Grid (5×5 I/J or 6×6 letters+digits), separator |\n" + ClassicalRows,
        ),
        (
            "| Unicode Escapes | ENC | – | JavaScript `\\uFFFF`, braced `\\u{1F600}`, Python `\\U0001F600` |",
            "| Unicode Escapes | ENC | – | JavaScript `\\uFFFF`, braced `\\u{1F600}`, Python `\\U0001F600` |\n" + EncodingRows,
        ),
        (
            "| Line Tools | TRF | – | Sort A→Z / Z→A / by length / numeric, de-duplicate, drop empty, trim, reverse, number, join; case sensitivity |",
            "| Line Tools | TRF | – | Sort A→Z / Z→A / by length / numeric, de-duplicate, drop empty, trim, reverse, number, join; case sensitivity |\n" + TransformRows,
        ),
        (
            'Caesar brute  Khoor                      -> 25 lines, one per shift; " 3: Hello"',
            'Caesar brute  Khoor                      -> 25 lines, one per shift; " 3: Hello"\n' + Examples,
        ),
        (
            "* Hill, Bifid and Polybius carry letters (and digits on the 6×6 grid) only, so punctuation is\n  dropped; Hill pads an odd number of letters with X. Polybius writes words separated by `/` so\n  spaces come back on decoding.",
            "* Hill, Bifid and Polybius carry letters (and digits on the 6×6 grid) only, so punctuation is\n"
            "  dropped; Hill pads an odd number of letters with X. Polybius writes words separated by `/` so\n"
            "  spaces come back on decoding. Hill 3×3 pads a length that is not a multiple of three in the\n"
            "  same way, so `HELLO` decodes back as `HELLOX` — the padding is part of the ciphertext.\n"
            "* ADFGX/ADFGVX keeps letters and digits only: spaces, punctuation and case are removed by\n"
            "  design, and both keywords are required to read a message back.\n"
            "* Trifid works on its 27-cell cube, so it carries letters (plus `.`, and a space on the merged\n"
            "  alphabet) only.\n"
            "* Enigma is reciprocal and never substitutes a letter for itself: *Decode* is the identical\n"
            "  operation with the same settings. Non-letters pass through untouched and do not advance the\n"
            "  rotors, so `Hello, World! 123` comes back with the punctuation, digits and case intact but\n"
            "  every letter changed.\n"
            "* Hex Dump, UTF-16 Units, UTF-32 Code Points, Braille Bits, Base91, Punycode and Roman Numerals\n"
            "  round trip exactly. The decoders expect the format that was used to encode (UTF-16 escapes\n"
            "  accept both `\\uXXXX` and `\\xXX` on the way in), and Roman numerals reject malformed input\n"
            "  such as `IIII` instead of guessing.",
        ),
        (
            '* That any of its output is "unbreakable".',
            '* That any of its output is "unbreakable".\n'
            "* That a hash, an HMAC, a PBKDF2 hash or a checksum is encryption. SHA-1, SHA-256 and SHA-512 are\n"
            "  **one-way** functions, HMAC authenticates a message, PBKDF2 slows down password guessing and\n"
            "  CRC-32/Adler-32 merely catch accidental corruption. None of them hides text.\n"
            "* That CRC-32 or Adler-32 detect a deliberate modification: a checksum is not tamper-proof, it\n"
            "  is an integrity check for accidents.\n"
            "* That MD5 or SHA-1 are still collision resistant. Both are labelled broken for signatures and\n"
            "  passwords in the app; they are offered because APIs and legacy files still contain them.\n"
            "* That the Enigma machine — or any classical cipher — protects a secret today. Enigma was\n"
            "  broken at Bletchley Park in the 1940s; it ships here for education and recreation.\n"
            "* That the JWT Inspector validated a token. It decodes and displays; it never verifies a\n"
            "  signature and says so in its output.\n"
            "* That formatting JSON, testing a regex, diffing two versions or counting words changes the\n"
            "  meaning of your data — those tools report and reformat only.",
        ),
    ],
)

# --------------------------------------------------- docs/ENCRYPTION_FORMAT.md ----
patch(
    "docs/ENCRYPTION_FORMAT.md",
    [
        (
            "| Cipher | AES-256 in GCM mode (`AES/GCM/NoPadding`) |",
            "| Cipher | AES-128 / AES-192 / AES-256 in GCM mode (`AES/GCM/NoPadding`); AES-256 is the default |",
        ),
        (
            "| Key derivation | PBKDF2 with HMAC-SHA256, 210 000 iterations, 32-byte output |",
            "| Key derivation | PBKDF2 with HMAC-SHA256, 210 000 iterations; output length is the selected AES key size (16 / 24 / 32 bytes) |",
        ),
        (
            "| 1 | 1 | `kdfId` | `0x01` = PBKDF2-HMAC-SHA256, 210 000 iterations, 32-byte key |",
            "| 1 | 1 | `kdfId` | `0x01` = PBKDF2-HMAC-SHA256, 210 000 iterations; key length follows the chosen AES key size (the key size itself is **not** stored — see §3.1) |",
        ),
        (
            "## 4. Validation rules on decrypt",
            """### 3.1 Key size (added in version 1.3.0)

The key-size setting (AES-128, AES-192, AES-256) selects how many PBKDF2 bytes are used as the AES
key. It is deliberately **not** written into the payload:

```
key = PBKDF2-HMAC-SHA256(password, salt, 210000, 16 | 24 | 32 bytes)
```

* The payload layout is unchanged, so every `version = 0x01` payload written by earlier versions of
  the app still decrypts.
* On decrypt the tool derives a key of each supported length (32, 24, then 16 bytes, most likely
  first) and lets the GCM tag decide: only the correct key length authenticates. This is the same
  trick that makes a wrong password fail — the tag check is the arbiter, and it costs at most three
  PBKDF2 derivations (with a 100 ms+ KDF this stays well under a second on a phone).
* The 16/24/32-byte outputs are prefixes of one another, so no additional KDF work is needed beyond
  the longest derivation in practice; the implementation derives per length for clarity.
* `keySizeOf()` on the processor reports which length a payload actually needs, so the information
  sheet can state the key size of the last operation.
* The same rule applies to AES-CBC + HMAC (§6.1): the PBKDF2 output is the AES key of the chosen
  length followed by a separate 32-byte HMAC key.

## 4. Validation rules on decrypt""",
        ),
        (
            "byte 1        kdfId          0x01  (PBKDF2-HMAC-SHA256, 210 000 iterations, 64 bytes)",
            "byte 1        kdfId          0x01  (PBKDF2-HMAC-SHA256, 210 000 iterations, key length + 32 bytes)",
        ),
        (
            "* PBKDF2 output is **split**: bytes 0–31 are the AES key, bytes 32–63 are the HMAC key. The two\n  purposes never share key material.",
            "* PBKDF2 output is **split**: the first `keyLength` bytes (16, 24 or 32) are the AES key, the\n  following 32 bytes are the HMAC key. The two purposes never share key material.",
        ),
        (
            "* Never reuse an (key, IV) pair: always draw a fresh 12-byte IV per message.",
            "* Never reuse an (key, IV) pair: always draw a fresh 12-byte IV per message.\n"
            "* Never add a key-size field to `version = 0x01`: the size is inferred from the tag check, so an\n"
            "  old payload and a new payload differ only in the length of the derived key.",
        ),
    ],
)

# ------------------------------------------------------------------ README.md ----
patch(
    "README.md",
    [
        (
            "**A tiny, extremely polished Swiss-army knife for text — 46 encoding, cipher and encryption tools in one offline Android app.**",
            "**A tiny, extremely polished Swiss-army knife for text — 69 encoding, cipher, hash and encryption tools in one offline Android app.**",
        ),
        (
            "46 tools, grouped exactly as they appear in the app.",
            """69 tools, grouped exactly as they appear in the app. New in version 1.3.0: AES key-size choice
(128 / 192 / 256), hashing and authentication, the Enigma machine, Porta, Trifid, Scytale,
ADFGX/ADFGVX, Hill 3×3, a custom-alphabet Vigenère family, Base91, Punycode, Braille, Roman
numerals, UTF-16/UTF-32 views, hex dump, JSON/regex/diff/statistics/JWT utilities.""",
        ),
        (
            "| AES-GCM Encryption | Encrypt / Decrypt | Password | **Authenticated encryption** |",
            "| AES-GCM Encryption | Encrypt / Decrypt | Password + key size (128 / 192 / 256) | **Authenticated encryption** |",
        ),
        (
            "| AES-CBC + HMAC | Encrypt / Decrypt | Password | **Authenticated encryption** (Encrypt-then-MAC) |",
            "| AES-CBC + HMAC | Encrypt / Decrypt | Password + key size (128 / 192 / 256) | **Authenticated encryption** (Encrypt-then-MAC) |",
        ),
        (
            "### Classical ciphers\n\n| Tool | Directions | Requires key | Classification |",
            """### Hashing & authentication

| Tool | Directions | Requires key | Classification |
| --- | --- | --- | --- |
| Hash | Hash / verify | – (optional expected digest) | Cryptographic hash (one-way) — MD5 and SHA-1 marked broken |
| HMAC | Tag / verify | Secret key | Message authentication code |
| PBKDF2 Password Hash | Hash / verify | Password | Cryptographic hash (one-way), 210 000 iterations |
| Checksum | Checksum / verify | – | Checksum (integrity only) — CRC-32 and Adler-32 |

### Classical ciphers

| Tool | Directions | Requires key | Classification |""",
        ),
        (
            "| Polybius Square | Text ↔ Coordinates | Grid (5×5 / 6×6), separator | Classical cipher |",
            "| Polybius Square | Text ↔ Coordinates | Grid (5×5 / 6×6), separator | Classical cipher |\n"
            "| Hill Cipher (3×3) | Encrypt / Decrypt | 9-letter matrix, padding | Classical cipher |\n"
            "| Porta Cipher | Encrypt / Decrypt (reciprocal) | Keyword | Classical cipher |\n"
            "| Trifid Cipher | Encrypt / Decrypt | Keyword, period, cube alphabet | Classical cipher |\n"
            "| Scytale | Encrypt / Decrypt | Diameter (2 … 20) | Classical cipher |\n"
            "| ADFGX / ADFGVX | Encrypt / Decrypt | Square key + column key | Classical cipher |\n"
            "| Enigma Machine | Encrypt / Decrypt (reciprocal) | Rotors, reflector, rings, positions, plugboard | Classical cipher (historical, broken) |\n"
            "| Vigenère / Beaufort (custom) | Encrypt / Decrypt | Variant, key, 26- or 36-character alphabet | Classical cipher |",
        ),
        (
            "| Unicode Escapes | Encode / Decode | JavaScript, braced `\\u{…}`, Python `\\U0001F600` |",
            "| Unicode Escapes | Encode / Decode | JavaScript, braced `\\u{…}`, Python `\\U0001F600` |\n"
            "| Base91 | Encode / Decode | basE91 alphabet (binary-to-text) |\n"
            "| Punycode | Encode / Decode | RFC 3492 IDN conversion |\n"
            "| Braille Bits | Encode / Decode | One cell per hex nibble, four per code unit |\n"
            "| Roman Numerals | Number ↔ numeral | 1 … 3999, strict validation |\n"
            "| UTF-16 Units | Text → units / units → Text | Hex, decimal or `\\uXXXX` escapes |\n"
            "| UTF-32 Code Points | Text → code points / reverse | Hex, decimal or `\\U00000000` escapes |\n"
            "| Hex Dump | Text ↔ dump | 16-byte rows with offset, hex column and `\\|ASCII\\|` gutter |",
        ),
        (
            "| NATO Phonetic | Spell out / read back | NATO (Alfa…) and old ICAO (Able…) alphabets |",
            "| NATO Phonetic | Spell out / read back | NATO (Alfa…) and old ICAO (Able…) alphabets |\n"
            "| JSON Formatter | Format / minify / list keys | Numbers keep their written form; parse errors name the line |\n"
            "| Regex Tester | Find / replace / split / highlight | Flags: ignore case, multiline, dot-all |\n"
            "| Text Diff | Compare two versions | Separator line, case sensitivity, unchanged/removed/added report |\n"
            "| Text Statistics | Count and report | Characters, UTF-16 units, code points, UTF-8 bytes, words, sentences, paragraphs |\n"
            "| JWT Inspector | Decode (never verifies) | Shows header, payload, `alg` and `exp` |",
        ),
        (
            "## 4. Encryption format (AES-256-GCM)",
            "## 4. Encryption format (AES-GCM, 128 / 192 / 256-bit keys)",
        ),
        (
            "* Key derivation: `PBKDF2(password, salt, 210 000 iterations, HMAC-SHA256) → 32-byte AES key`.",
            "* Key derivation: `PBKDF2(password, salt, 210 000 iterations, HMAC-SHA256) → 16, 24 or 32-byte\n  AES key`, according to the key-size setting (AES-128 / AES-192 / AES-256, default AES-256). The\n  key size is **not** stored in the payload: on decrypt the app derives each supported length and\n  lets the GCM (or HMAC) tag decide. Payloads written by version 1.0.0 keep decrypting unchanged.",
        ),
        (
            "`./gradlew :core:test` runs **148 unit tests** (`core/src/test/kotlin/com/texthub/core/`):",
            "`./gradlew :core:test` runs **231 unit tests** (`core/src/test/kotlin/com/texthub/core/`):",
        ),
        (
            "│   ├── processors/              one class per algorithm (46 of them)",
            "│   ├── processors/              one class per algorithm (69 of them)",
        ),
        (
            "| `RegistryTest` | Unique ids, every tool documented/classified, only AES claims security, sensitive params flagged, instant search behaviour, round trip of every tool, Unicode round trips, friendly error messages (no stack traces), direction swap |",
            "| `RegistryTest` | Unique ids, every tool documented/classified, only AES claims security, sensitive params flagged, instant search behaviour, round trip of every tool, Unicode round trips, friendly error messages (no stack traces), direction swap |\n"
            "| `NewToolsTest` (round 2) | Base45, Quoted-Printable, HTML entities, Unicode escapes, NATO, Leetspeak, Beaufort, Autokey, Gronsfeld, Hill 2×2, Bifid, Polybius, line tools, case converter |\n"
            "| `NewTools2Test` (round 3) | AES key sizes (128/192/256) and cross-size decryption, AES-CBC with key sizes, ChaCha availability, hashes (MD5/SHA-1/SHA-256/SHA-512 vectors and Base64 form), HMAC vectors, PBKDF2 format and verification, CRC-32/Adler-32 reference values, Base91, Punycode (`münchen` → `mnchen-3ya`), Roman numerals, UTF-16/UTF-32 views, hex dump layout, Hill 3×3, Porta, Trifid, Scytale, ADFGX, Enigma (BDZGO, EWTYX, RXWKBV, reciprocity, no self-encryption), custom-alphabet Vigenère, JSON, regex, text diff, statistics, JWT |",
        ),
        (
            "* The pre-built APKs from this repository are in [`apk/`](apk/).",
            """* The pre-built APKs from this repository are in [`apk/`](apk/):
  `TextHub-1.3.0-release.apk` (signed, 9.5 MB, md5 `29566bd297bc9279cff47cb1b9f82d8d`) and
  `TextHub-1.3.0-debug.apk` (14.3 MB, md5 `176bead5253acfaf2f38c82d72f85ad0`). Both declare no
  permissions other than Android's own `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.""",
        ),
    ],
)

# ------------------------------------------------------------- PRIVACY.md -----
privacy_note = """

## Round 3 additions (version 1.3.0)

The 23 tools added in 1.3.0 change nothing about data handling:

* Hashes, HMAC, PBKDF2, checksums, the Enigma machine, Porta, Trifid, Scytale, ADFGX/ADFGVX, Hill
  3×3, Base91, Punycode, Braille, Roman numerals, UTF-16/UTF-32 views, hex dump, JSON formatting,
  regex, text diff, statistics and the JWT inspector all run in the same local core library. There
  is still no network code and still no `INTERNET` permission.
* Passwords and keys typed into the new tools (HMAC key, PBKDF2 password, AES key size) are marked
  sensitive and are filtered out before preferences are written, exactly like the AES password.
* The JWT Inspector decodes a token that you paste. It never contacts a server, never verifies a
  signature and never stores the token.
* Nothing about a hash, an HMAC or a checksum is written to disk; only the non-secret parameter
  choices (algorithm, output format, mode) are remembered.
"""

qa_note = """

## Round 3 checks (version 1.3.0)

| [x] | `:core:test` → **231 tests, 0 failures** (round 1: 148, round 2: 185). JUnit XML in `core/build/test-results/test/` |
| [x] | AES key size: encrypt at 256/192/128 and decrypt the other way round; a payload from 1.0.0 still decrypts (payload format unchanged) |
| [x] | AES-CBC + HMAC derives `keyLength + 32` bytes so the MAC key is independent at every key size |
| [x] | Reference vectors pinned in tests: Enigma `AAAAA→BDZGO` / rings BBB `→EWTYX` / `ATTACK→RXWKBV`, Porta `ABC→TUW`, Trifid `AAF`, Hill 3×3 `ACT→POH`, Scytale `HELLOWORLD→HLODEORLWL`, SHA-256("abc"), HMAC-SHA256 fox vector, CRC-32 `0x414FA339`, Adler-32 `0x5BDC0FDA`, Punycode `münchen→mnchen-3ya`, Base91 `a→GB` |
| [x] | Enigma is reciprocal and never maps a letter to itself (asserted by test); non-letters pass through without advancing the rotors |
| [x] | Hashes/MACs/checksums are classified as one-way and `secure = false`; the registry test still allows only `aes`, `aescbc` and `chacha` to claim authenticated encryption |
| [x] | Tool list scrolling: the picker sheet hoists its `LazyListState`, scrolls to the top when the query or category changes, keeps the list in a `weight(1f)` slot inside a 92 %-height sheet and uses `skipPartiallyExpanded = true`, so a 69-tool list can no longer be clipped or left scrolled past its end |
| [x] | Release APK `apk/TextHub-1.3.0-release.apk` (9,494,816 B, md5 `29566bd297bc9279cff47cb1b9f82d8d`) — `apksigner verify` passes (v2), `aapt2 dump permissions` shows no `INTERNET` |
| [x] | Debug APK `apk/TextHub-1.3.0-debug.apk` (14,268,402 B, md5 `176bead5253acfaf2f38c82d72f85ad0`); both APKs report `versionName 1.3.0`, `versionCode 3`, `minSdk 24`, `targetSdk 34` |
| [ ] | On-device pass of the new tools (no emulator is available in this environment — the checklist above is the script for that review) |
"""

patch(
    "docs/PRIVACY.md",
    [("## Limits and honest caveats", privacy_note.strip() + "\n\n## Limits and honest caveats")],
)
patch(
    "docs/QA_CHECKLIST.md",
    [("## Known limitations", qa_note.strip() + "\n\n## Known limitations")],
)

print("docs done")
