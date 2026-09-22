# Tool reference

Every one of the **73 methods** available in Text Hub, with its classification, key
requirement, parameters and a worked example. The app shows this information in the ⓘ sheet of each tool.

Legend — **Class:** `ENC` encoding · `HIST` historical character encoding · `CLS` classical
cipher · `OBF` obfuscation · `TRF` text transformation · `REV` reversible transformation ·
`SEC` authenticated encryption · `HASH` cryptographic hash (one-way) · `MAC` message
authentication code · `CKSUM` checksum (integrity only) · `KEYGEN` key material (not encryption).

| Tool | Class | Key | Parameters |
| --- | --- | --- | --- |
| AES-GCM Encryption | SEC | Password | Key size (AES-128 / AES-192 / AES-256, default AES-256); the size is recorded in the message and must match on decrypt |
| AES-CBC + HMAC | SEC | Password | Key size (AES-128 / AES-192 / AES-256, default AES-256); recorded in the message, enforced on decrypt |
| ChaCha20-Poly1305 | SEC | Password | – (Android 9+; 256-bit key) |
| AES-CTR + HMAC | SEC | Password | Key size 128 / 192 / 256 (recorded and enforced); random counter block per message |
| AES-GCM with your own key | SEC | Raw key | Key pasted as Base64 or hex (16, 24 or 32 bytes); no key derivation |
| RSA-OAEP + AES-GCM | SEC | RSA key (PEM) | Public key to encrypt, private key to decrypt; hybrid, any text length |
| RSA Key Pair Generator | KEYGEN | – | 2048 / 3072 / 4096 bits; prints both PEM blocks and a fingerprint |
| Hash (one-way) | HASH | – | Algorithm, output format (hex, HEX, Base64); optional expected digest to verify against |
| HMAC (SHA-1 / SHA-256 / SHA-512) | MAC | Secret key | Algorithm, tag format, message to verify the tag against |
| PBKDF2 Password Hash | HASH | Password | Algorithm (SHA-1 / SHA-256 / SHA-512); 210 000 iterations, 16-byte random salt |
| Checksum (CRC-32 / Adler-32) | CKSUM | – | Kind, output format (hex / decimal), optional expected value to verify against |
| Caesar Cipher | CLS | Shift | Shift −25…25 (stepper) |
| Vigenère Cipher | CLS | Keyword | Secret key |
| Atbash | CLS | – | – |
| Substitution Cipher | CLS | 26-letter alphabet | Substitution alphabet |
| ROT13 | OBF | – | – |
| ROT18 | OBF | – | – |
| ROT47 | OBF | – | – |
| Affine Cipher | CLS | A, B | A (1…25, coprime with 26), B (0…25) |
| Playfair Cipher | CLS | Keyword | Grid (5×5 I/J or 6×6 alnum), filler |
| Rail Fence Cipher | CLS | Rails | Rails 2…20, mode (all chars / letters only) |
| Columnar Transposition | CLS | Keyword | Padding (none / pad with X) |
| Bacon's Cipher | CLS | – | Alphabet convention (26-letter / 24-letter) |
| A1Z26 | CLS | – | Number separator, word separator |
| Beaufort Cipher | CLS | Keyword | Form (Beaufort K−P / Variant P−K) |
| Autokey Cipher | CLS | Primer | Primer keyword; the plaintext continues the key |
| Gronsfeld Cipher | CLS | Digits | Digit key (0–9 shifts) |
| Hill Cipher (2×2) | CLS | 4-letter matrix | Padding (pad with X / reject odd length) |
| Bifid Cipher | CLS | Optional keyword | Period 2…40 (block size) |
| Polybius Square | CLS | Optional keyword | Grid (5×5 I/J or 6×6 letters+digits), separator |
| Hill Cipher (3×3) | CLS | 9-letter matrix | Key (9 letters, invertible mod 26), padding (pad with X / reject) |
| Porta Cipher | CLS | Keyword | Secret key (reciprocal: the same operation deciphers) |
| Trifid Cipher | CLS | Keyword | Keyword, period ≥ 3, cube alphabet (letters or merged I/J + space) |
| Scytale | CLS | Diameter | Diameter 2…20 (a rail-fence style wrap on a cylinder) |
| ADFGX / ADFGVX | CLS | Two keywords | Alphabet (ADFGX / ADFGVX), square key, column key — both keys are needed to decode |
| Enigma Machine | CLS | Machine settings | Reflector B/C, three rotors from I–V, ring settings, start positions, plugboard |
| Vigenère / Beaufort (custom) | CLS | Keyword | Variant (Vigenère / Beaufort / Variant Beaufort), key, alphabet (26 or 36 characters) |
| Caesar Brute Force | TRF | – | – (lists all 25 shifts; analysis helper) |
| Case Converter | TRF | – | 11 styles: UPPER, lower, Title, Sentence, tOGGLE, camel, Pascal, snake, kebab, CONSTANT, dot |
| Line Tools | TRF | – | Sort A→Z / Z→A / by length / numeric, de-duplicate, drop empty, trim, reverse, number, join; case sensitivity |
| JSON Formatter | TRF | – | Indent 2 spaces / indent tabs / minify / list key paths; parse errors name the line |
| Regex Tester | TRF | – | Pattern, find / replace / split / highlight, replacement text, flags (ignore case, multiline, dot-all) |
| Text Diff | TRF | – | Two versions separated by a separator line; case sensitivity; reports unchanged, removed and added lines |
| Text Statistics | TRF | – | Characters, UTF-16 units, code points, UTF-8 bytes, words, unique words, sentences, paragraphs |
| JWT Inspector | TRF | – | Decodes header and payload and reports `alg` and `exp`; never verifies a signature |
| Leetspeak (1337) | OBF | – | Level (light / heavy) |
| Base64 | ENC | – | Standard / URL-safe |
| Base32 | ENC | – | Standard RFC 4648 / Base32 Hex |
| Base58 | ENC | – | Bitcoin / Ripple / Flickr |
| Base85 / Ascii85 | ENC | – | Ascii85 / Ascii85 z / Z85 |
| URL Encoding | ENC | – | RFC 3986 / form |
| Hexadecimal | ENC | – | Continuous / space / comma |
| Binary | ENC | – | 8-bit groups / continuous |
| ASCII | ENC | – | Decimal / binary / hex |
| Decimal | ENC | – | Space / comma / newline |
| Octal | ENC | – | Space separated / continuous |
| Unicode Code Points | ENC | – | U+ / plain hex / decimal; separator |
| Morse Code | ENC | – | Word separator (default `/`) |
| Baudot / ITA2 | HIST | – | ITA2 / US TTY; group formatting; unsupported-character behaviour |
| Base45 | ENC | – | RFC 9285 alphabet (QR-code friendly, used by EU DCC) |
| Quoted-Printable | ENC | – | RFC 2045; 76-character soft line breaks |
| HTML Entities | ENC | – | Encode: all / minimal; decode: named, decimal, hex, unknown kept |
| Unicode Escapes | ENC | – | JavaScript `\uFFFF`, braced `\u{1F600}`, Python `\U0001F600` |
| Base91 | ENC | – | basE91 alphabet (Joachim Henke); whitespace ignored when decoding |
| Punycode | ENC | – | RFC 3492 IDN conversion; the `xn--` prefix belongs to DNS, not to the encoding |
| Braille Bits | ENC | – | One braille cell per hexadecimal nibble, four cells per 16-bit code unit |
| Roman Numerals | ENC | – | 1…3999 in standard subtractive notation; tokens that are not numbers stay as they are |
| UTF-16 Units | ENC | – | 16-bit code units (surrogates included) as hex, decimal or `\uXXXX` escapes |
| UTF-32 Code Points | ENC | – | One number per code point as hex, decimal or `\U00000000` escapes |
| Hex Dump | ENC | – | xxd-style 16-byte rows: offset, hex bytes, `|ASCII|` gutter |
| NATO Phonetic | TRF | – | Alphabet: NATO (Alfa…) / old ICAO (Able…) |
| Reverse Text | TRF | – | Characters / each line / word order / line order |
| XOR | REV | Key | Output format (hex / Base64) |

## Worked examples

```
Base64        Hello                     -> SGVsbG8=
Base32        foobar                    -> MZXW6YTBOI======
Base58        Hello Bitcoin 123         -> gTazoqFhoKXj7FVgdKhuJeE   (Bitcoin alphabet)
Base85        Man                       -> 9jqo                    (Ascii85, partial group)
Base85        "Man "                    -> 9jqo^                   (Ascii85, full group)
URL           Hello World               -> Hello%20World
Hexadecimal   Hello                     -> 48 65 6C 6C 6F
Binary        He                        -> 01001000 01100101
ASCII         A                         -> 65
Decimal       Hello                     -> 72 101 108 108 111
Octal         Hello                     -> 110 145 154 154 157
Unicode       A😀                       -> U+0041 U+1F600
Morse         HELLO                     -> .... . .-.. .-.. ---
Baudot (ITA2) HELLO                     -> 10100 00001 10010 10010 11000
Baudot (ITA2) 123                       -> 11011 10111 10011 00001
ROT13         Hello, World!             -> Uryyb, Jbeyq!
ROT18         Hello123                  -> Uryyb678
ROT47         Hello                     -> w6==@
Atbash        Hello                     -> Svool
Caesar (+3)   Hello, World!             -> Khoor, Zruog!
Vigenère      ATTACKATDAWN (key LEMON)  -> LXFOPVEFRNHR
XOR (key key) Hello                     -> 230015070A   (hex output by default)
Affine (5,8)  AFFINECIPHER              -> IHHWVCSWFRCP
Playfair      "Hide the gold in the tree stump" (key "playfair example")
                                        -> BMODZBXDNABEKUDMUIXMMOUVIF
Columnar      HELLOWORLD (key BAD)      -> EORHLODLWL
Rail Fence    WEAREDISCOVEREDFLEEATONCE (3 rails)
                                        -> WECRLTEERDSOEEFEAOCAIVDEN
Bacon         HELLO                     -> AABBB AABAA ABABB ABABB ABBBA   (A=0, B=1)
A1Z26         HELLO                     -> 8-5-12-12-15
AES-GCM       "secret text" + password  -> Base64 payload, 76 chars for 11 input bytes
                                          AQE2DkzSBw7UR1ArPgtZ... (see ENCRYPTION_FORMAT.md)
AES-CBC+HMAC  "hello" + password         -> 84-byte payload -> Base64 (0x02 prefix)
ChaCha20       "hello" + password         -> Base64 (0x03 prefix), 12-byte nonce + Poly1305 tag
Base45        Hello!!                    -> %69 VD92EX0            (RFC 9285 test vector)
Base45        AB                         -> BB8
Quoted-Print. Hello=World                -> Hello=3DWorld
Quoted-Print. Grüße                      -> Gr=C3=BC=C3=9Fe
HTML entities Tom & Jerry <b>"bold"</b>  -> Tom &amp; Jerry &lt;b&gt;&quot;bold&quot;&lt;/b&gt;
HTML entities café © 😀                  -> caf&#233; &copy; &#128512;
Unicode esc.  A😀 (JavaScript)           -> A\uD83D\uDE00
Unicode esc.  A😀 (braced)               -> A\u{1F600}
Unicode esc.  A😀 (Python)               -> A\U0001F600
NATO          HELLO                      -> Hotel Echo Lima Lima Oscar
NATO          123                        -> One Two Three
Case          "Hello World" -> snake_case -> hello_world
Line tools    "banana Apple cherry Apple" (de-duplicate, sort A→Z)
                                         -> Apple\nbanana\ncherry
Leetspeak     Hello (light)              -> H3ll0
Leetspeak     Bello (heavy)              -> 83110
Beaufort      HELLO (key KEY)            -> DANZQ      (reciprocal: decode DANZQ -> HELLO)
Beaufort      HELLO (key KEY, variant)   -> XANBK      (Variant form P − K)
Autokey       ATTACKATDAWN (primer QUEENLY) -> QNXEPVYTWTWP
Gronsfeld     HELLO (key 31415)          -> KFPMT
Hill          HI (key HILL)              -> JJ         (odd length is padded with X)
Bifid         HI (period 5)              -> GO
Polybius      HELLO WORLD                -> 23 15 31 31 34 / 52 34 42 31 14
Polybius      A1 (6×6 grid)              -> 11 54
Caesar brute  Khoor                      -> 25 lines, one per shift; " 3: Hello"
Hill (3x3)    ACT (key GYBNQKURP)        -> POH
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
Regex         "Order 1234 shipped, order 5678 pending" (pattern order\s+(\d+), ignore case)
                                         -> 2 matches, first group $1=1234
Regex         "Order 1234 shipped, order 5678 pending" (pattern \d+, replace with ****)
                                         -> Order **** shipped, order **** pending
Regex         a1b2c (pattern \d, split)    -> a\nb\nc
Text diff     "one/two/three/---/one/2/three/four" (separator line ---)
                                         -> reports "unchanged", "- two", "+ 2", "+ four"
Text stats    "Hello world. Hello there!\n\nSecond paragraph."
                                         -> Words: 6, Unique words: 5, Sentences: 3, Paragraphs: 2, hello × 2
JWT           header {"alg":"HS256"}…    -> header and payload decoded, alg shown with a note that
                                            the signature is never verified
AES-CTR+HMAC  "hi" + password          -> Base64 (0x04): salt, counter block, ciphertext, HMAC tag
AES-GCM rawkey key = 32 zero bytes (Base64 "AAAA...=")  -> 16-byte key: "AAA...=" (16 bytes)
RSA-OAEP      "hi" + public PEM        -> 0x11 payload: RSA-wrapped 32-byte AES key + GCM ciphertext
RSA keygen    2048-bit                 -> public PEM, private PEM, "fingerprint (SHA-256): …"
```

All of the values above were produced by the shipped implementation and are reproducible with
`./gradlew :core:test` plus the registry round-trip test.

## Reversibility

* Symmetric transformations (ROT13, ROT18, ROT47, Atbash, Reverse) ignore the direction switch:
  applying them twice returns the original text.
* Encodings always round trip exactly, including Unicode — the encoders operate on UTF-8 bytes
  and the decoders reconstruct UTF-8.
* Classical ciphers preserve spaces, digits, punctuation and letter case, except where the
  algorithm removes them by definition (Playfair keeps letters only, Bacon letters only).
* AES-GCM, AES-CBC+HMAC and ChaCha20-Poly1305 output is non-deterministic: the salt and nonce
  are random, so the same input never produces the same payload twice.
* Caesar Brute Force is one-way by design (it lists every possible plaintext instead of guessing),
  and Case Converter / Line Tools / Leetspeak / NATO Phonetic are lossy in the same way a
  style change is: converting back gives the text, not necessarily the original formatting.
* Hill, Bifid and Polybius carry letters (and digits on the 6×6 grid) only, so punctuation is
  dropped; Hill pads an odd number of letters with X. Polybius writes words separated by `/` so
  spaces come back on decoding. Hill 3×3 pads a length that is not a multiple of three in the
  same way, so `HELLO` decodes back as `HELLOX` — the padding is part of the ciphertext.
* ADFGX/ADFGVX keeps letters and digits only: spaces, punctuation and case are removed by
  design, and both keywords are required to read a message back.
* Trifid works on its 27-cell cube, so it carries letters (plus `.`, and a space on the merged
  alphabet) only.
* There is one AES tool per mode, not per key size: *AES-GCM Encryption*, *AES-CBC + HMAC* and
  *AES-CTR + HMAC* have a **Key size** setting (AES-128 / AES-192 / AES-256). The size is recorded
  in the message and the setting must match it on decrypt — a mismatch is refused with a message
  that names the size the message needs, instead of silently decrypting with a different key.
* Payloads written before version 1.4.2 did not record the key size. They still decrypt, and the
  app tells you which size to select if your setting is wrong.
* A key of the wrong size for a message is also named explicitly by *AES-GCM with your own key*
  ("this message was encrypted with a 128-bit AES key, but the key you pasted is 256 bits") and by
  *RSA-OAEP + AES-GCM* ("this message was encrypted for a 3072-bit RSA key…").
* RSA-OAEP cannot encrypt more than a short block on its own, so this tool wraps a fresh 256-bit
  AES key with the public key and encrypts the text with AES-256-GCM. Only the matching private
  key can read the result, and nothing else can be decrypted with it.
* The RSA Key Pair Generator produces key material, not ciphertext. It is one-way by nature:
  generating again always produces a different pair.
* AES-GCM with your own key does no key stretching - unlike the password tools, a weak key stays
  weak, which is exactly why the password tools exist.
* Enigma is reciprocal and never substitutes a letter for itself: *Decode* is the identical
  operation with the same settings. Non-letters pass through untouched and do not advance the
  rotors, so `Hello, World! 123` comes back with the punctuation, digits and case intact but
  every letter changed.
* Hex Dump, UTF-16 Units, UTF-32 Code Points, Braille Bits, Base91, Punycode and Roman Numerals
  round trip exactly. The decoders expect the format that was used to encode (UTF-16 escapes
  accept both `\uXXXX` and `\xXX` on the way in), and Roman numerals reject malformed input
  such as `IIII` instead of guessing.

## Things Text Hub deliberately will not claim

* That Base64 (or any Base-N encoding, Hex, Binary, ASCII, Decimal, Octal, Unicode, Morse,
  Baudot/ITA2, URL encoding) is encryption.
* That Caesar, Vigenère, Atbash, Substitution, Affine, Playfair, Rail Fence, Columnar,
  Bacon, A1Z26, ROT-family or XOR are secure.
* That Beaufort, Autokey, Gronsfeld, Hill, Bifid or Polybius are any safer than the other
  classical ciphers — all of them fall to frequency analysis or known-plaintext attack.
* That any of its output is "unbreakable".
* That RSA-OAEP replaces a password. It removes the need to share a secret in advance, but it also
  means the private key becomes the single thing that must never leak. Text Hub does not manage,
  escrow or recover keys.
* That a key generated here is stored anywhere: nothing is written to disk. Losing the text means
  losing the key.
* That a hash, an HMAC, a PBKDF2 hash or a checksum is encryption. SHA-1, SHA-256 and SHA-512 are
  **one-way** functions, HMAC authenticates a message, PBKDF2 slows down password guessing and
  CRC-32/Adler-32 merely catch accidental corruption. None of them hides text.
* That CRC-32 or Adler-32 detect a deliberate modification: a checksum is not tamper-proof, it
  is an integrity check for accidents.
* That MD5 or SHA-1 are still collision resistant. Both are labelled broken for signatures and
  passwords in the app; they are offered because APIs and legacy files still contain them.
* That the Enigma machine — or any classical cipher — protects a secret today. Enigma was
  broken at Bletchley Park in the 1940s; it ships here for education and recreation.
* That the JWT Inspector validated a token. It decodes and displays; it never verifies a
  signature and says so in its output.
* That formatting JSON, testing a regex, diffing two versions or counting words changes the
  meaning of your data — those tools report and reformat only.

---

## Common and advanced settings (1.5.0)

Every tool shows the settings its everyday use needs - a password, a key size, a format - and
collects the rest under *Additional encryption settings* / *Additional settings*, collapsed by
default. The section is the same everywhere, so a setting learned in one tool is in the same place
in the next one. Advanced parameters always carry a one-line explanation, impossible combinations
are refused inline while typing, and any tool with more than one setting has a *Reset* action.
