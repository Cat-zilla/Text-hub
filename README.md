# Text Hub

**A tiny, extremely polished Swiss-army knife for text — 73 encoding, cipher, hash and encryption tools in one offline Android app.**

Text Hub is a native Kotlin/Jetpack Compose application. Pick a method, type or paste text,
optionally set its parameters, and read the result — copy it, swap it back through the tool, or
share it. Everything happens on the device: no account, no network permission, no analytics, no
advertising, no WebView.

```
Text Hub
Encode • Decode • Transform
```

| | |
| --- | --- |
| Version | 1.5.1 (versionCode 8) |
| Tools | 73, in 5 categories (audited: see §9) |
| Tests | 329 unit tests, all green (`:core:test` + `:app:testDebugUnitTest`) |
| Platform | Android 7.0+ (minSdk 24), targetSdk 34, compileSdk 34 |
| Language / UI | Kotlin 1.9.22, Jetpack Compose (BOM 2023.10.01), Material 3 |
| Build | Gradle 8.2, Android Gradle Plugin 8.1.4, JDK 17 |
| Permissions | none (no `INTERNET`, nothing else) |
| Runtime dependencies | AndroidX/Compose only — no third-party crypto, JSON or networking library |
| Developer | Catzilla |

---

## What's new in 1.5.1

**Favourites reordering was rebuilt - the row no longer jumps while you drag it.** The old gesture
reordered the list *during* the drag: it moved the row one slot at a time as soon as half a row had
been covered, ran the list's own placement animation at the same time as the visual translation, and
used a step that ignored the gap between rows. The animation and the translation fought each other
and the half-row threshold flipped between two slots from one frame to the next, which is exactly
the up-and-down wobble that was reported. Now:

* the lifted row follows the finger exactly, with a haptic tick when it is picked up;
* **the order does not change while the drag is in progress** - the slot the row would land on is
  highlighted instead, and the list follows the finger if you drag near the top or bottom edge;
* the step used for the arithmetic is the real row height plus the gap, measured from the rows
  themselves, and a full 60% of a row has to be covered before the highlighted slot changes (so a
  shaky finger cannot make it flicker);
* releasing commits the move **once**, so the stored order is written one time per drag instead of
  on every frame;
* dragging is offered only on the plain favourites list - while a search is active the rows are a
  subset, and a drop would otherwise move the wrong entry in the stored order.

The drag arithmetic is unit-tested (`DragReorderTest`, 12 tests) including a sweep that asserts the
target position can never move backwards as the finger travels - the mathematical signature of the
old bug.

**Other bugs found and fixed in the same pass**

| Fixed | What was wrong |
| --- | --- |
| Dragging in a *filtered* favourites list could reorder the wrong tools | The drag now only exists on the unfiltered list; the hint text says so by disappearing with the search box in use |
| A pasted parameter that no longer exists (removed choice, number now out of range) was restored as a broken setting | Remembered parameters are validated against the tool as it is today; anything that no longer fits is dropped and the default is used |
| Typing or pasting a very large text counted characters/words/lines on the UI thread | Above 20 000 characters the count runs on a background thread, and the result's statistics are measured in the same background step as the processing |
| A1Z26 turned any non-ASCII letter into a nonsense number (ü became 188) because `Char.isLetter()` matches thousands of characters | Only the 26 ASCII letters have a position; everything else passes through unchanged, and the info sheet says so |
| A1Z26 written with a different separator could not be read back | Decoding accepts every separator people actually type (space, hyphen, comma, dot, slash, pipe) and refuses values outside 1-26 |
| The "nothing here" message in the picker blamed an empty search even when filters were empty | The two cases now say what is actually true |

Test total: **329** (`:core:test` 317 + `:app:testDebugUnitTest` 12), all green; `:app:lintDebug`
clean.

---

## What's new in 1.5.0

This release is the *final fix, compatibility and QA* round. Nothing was redesigned: the toolset,
the payload formats, the privacy model and the look stay as they were, and the changes below are
the ones the review actually justified.

**1. Temporary data can be cleared - and Favourites are never touched.** Settings now ends with a
standalone *Temporary data* card that shows how much disposable data the app holds (in B / KB / MB),
explains what is stored, and offers *Clear*. A confirmation lists exactly what will be removed
(remembered tool settings, the recent-tools list) and what will be kept (**your favourites and their
order**, theme, accent colour, selected tool). The size on screen is recomputed immediately after
clearing. Nothing secret was ever stored, and clearing never touches text, passwords, keys or
favourites.

**2. The Favourites section can be reordered by dragging.** Long-press a favourite to lift it - the
row is drawn raised and slightly enlarged - then drag it anywhere in the list. Each move is written
to preferences immediately, so the order survives a restart. The main list, search results,
categories and recents keep their fixed order and cannot be dragged.

**3. Controls are generated from what a tool can actually do.** The action button carries the
tool's own verb in the current direction ("Encrypt", "Decode", "Hash", "Compare"), never a generic
"Process"; swap is hidden wherever the result cannot be pushed back (digests, checksums,
comparisons, analysis); a direction switch appears only when it changes something, and symmetric and
single-operation tools say so in one line instead. The second pass also removed the two genuinely
duplicated buttons left in the main screen (the output card had two *Clear* buttons, and *Type here*
only refocused the field above it) and moved the last two "wall of settings" cases - Enigma's
Ringstellung and the regex flags - under the collapsed section.

**4. Parameters are recorded, validated and enforced - never guessed.** A new audit
(`ParamDisciplineAuditTest`) encodes every tool with its defaults, decodes with one setting changed,
and requires either the original text or a friendly refusal. It found two real defects, both fixed:
Base58/Base85 decoding with the wrong variant returned confident mojibake instead of failing, and
UTF-16/UTF-32 decoding with `format = decimal` silently read decimal tokens as hexadecimal. Tools
whose settings genuinely define the transform (Bacon's 24/26-letter variants, A1Z26 separators,
Scytale's diameter, Baudot's alphabet, case/leet/regex, the Base58/Base85 variants) are listed as
documented exceptions and say so in their info sheet.

**5. External formats.** OpenSSL `enc` files (legacy MD5 and SHA-256, PBKDF2 with configurable
iterations, wrapped or unwrapped) and JWE compact tokens (`dir` + A256GCM, `A128CBC-HS256`) are
accepted with their settings, and the errors explain what was missing rather than claiming the data
"was not made by Text Hub".

**6. Additional Encryption.** Every encryption tool keeps the common case on top - password, key
size, a format choice - and moves mode, padding, character encoding, IV/nonce and its format,
input/output format, tag length, AAD and AAD format into a collapsed *Additional encryption
settings* section with defaults that match the ordinary case, inline validation for impossible
combinations, and a *Reset* action.

**7. Full second pass over all 73 tools** (`ToolReviewPassTwoTest`, `ParamDisciplineAuditTest`,
plus the earlier audits): control visibility, parameter validation, empty input, Unicode, round
trips, error friendliness, security claims, ids and searchability, payload compatibility. Payloads
frozen from 1.4.1 and 1.4.2 are decrypted in the tests to prove no update strands data.

Test total: **317** (`:core:test`, all green).

---

## What's new in 1.4.2

**Fixed: the AES Key size setting is now honoured on decryption.** Previously an AES-128 message
decrypted with the AES-256 setting (and any other combination) succeeded, because the setting was
only read when encrypting and decryption guessed the key size. Encryption now records the size in
the payload's `kdfId` byte (`0x02` = 256-bit; `0x01` remains "legacy, size not recorded") and
decryption derives exactly that key. A mismatch is **refused** with a sentence that names the size
the message needs, so changing the setting can never silently succeed. Messages written by
1.3.0–1.4.1 still decrypt, and report which size they actually use.

**The whole toolset was audited (all 73 tools).** The audit added in this round found and fixed
three real defects, and two documentation ones:

| Found | Fix |
| --- | --- |
| *Spelling alphabet* crashed with an `ArrayIndexOutOfBoundsException` on any non-ASCII letter or digit (`Grüße`, `नमस्ते`, `٣`): it used `Char.isLetter()`, which is true for thousands of characters, to index a 26-entry table | Only ASCII `A–Z`/`0–9` are spelled; anything else is refused with a friendly sentence that lists the characters it cannot spell |
| Sixteen tools could not be found by typing their own id (`utf16`, `railfence`, `aescbc`, `textdiff`, …) because ids were missing from the search index | The search index now contains every id plus a punctuation-free spelling of the name, so `railfence` *and* `rail fence` both work |
| A non-ASCII letter in a cipher key was silently turned into a bogus A–Z shift (Vigenère, Beaufort, Autokey, Playfair, ADFGX, custom alphabets) | Keys are filtered to ASCII letters (and digits where the alphabet has them), so no invalid index can be produced |
| The reference and README called two tools by names the app never uses | Documentation now matches the registry exactly, and a test enforces it |

New regression tests lock all of this down: `ToolAuditTest` sweeps every tool's parameters,
defaults, sensitive flags, behaviour on ten unusual inputs in both directions, searchability by id
and name, claim discipline, error friendliness and key-size enforcement; `ToolAuditReportTest`
writes `core/build/tool-audit.txt`, a per-tool report of the whole registry. Test total: **260**.

---

## Table of contents

1. [Feature overview](#1-feature-overview)
2. [Supported tools](#2-supported-tools)
3. [Encoding vs. cipher vs. hash vs. cryptography](#3-encoding-vs-cipher-vs-hash-vs-cryptography)
4. [Encryption and payload formats](#4-encryption-and-payload-formats)
5. [Architecture](#5-architecture)
6. [How to add a new processor](#6-how-to-add-a-new-processor)
7. [Build and run](#7-build-and-run)
8. [Signing and updates](#8-signing-and-updates)
9. [Testing](#9-testing)
10. [Privacy behaviour](#10-privacy-behaviour)
11. [Security notes](#11-security-notes)
12. [Theming, accent colours and accessibility](#12-theming-accent-colours-and-accessibility)
13. [Project layout](#13-project-layout)
14. [Known limitations](#14-known-limitations)
15. [Licensing and third-party notices](#15-licensing-and-third-party-notices)

---

## 1. Feature overview

| Area | What Text Hub does |
| --- | --- |
| Tool selection | Searchable bottom sheet with categories, instant search, favourites and recents. The list scrolls to the top when the query or filter changes, and a 73-tool list stays fully reachable |
| Direction | Per-tool mode switch: *Encode/Decode*, *Encrypt/Decrypt* or *Transform* — irrelevant controls are hidden |
| Parameters | Only the settings the selected tool needs (key size, shift, keys, alphabets, separators, variants, modes …) |
| Processing | Live (debounced) or on button press; heavy input is processed off the main thread with a progress indicator |
| Output | Monospace result area with copy, swap, clear, share and live statistics |
| Swap | Exchanges input and output **and** flips the direction, then re-processes immediately — one tap for a full round trip |
| Errors | Friendly sentences only, never a stack trace |
| Info | Per-tool sheet: what it does, whether it needs a key, whether it is secure, use cases, warnings, conventions |
| Themes | System / Dark / AMOLED (true black) / Light **plus eight accent colours** |
| Privacy | 100 % local, works in airplane mode, no permissions at all |
| Icons | Adaptive launcher icon (padlock whose body is a block of text) with a monochrome layer for themed icons |

---

## 2. Supported tools

73 tools, grouped exactly as they appear in the app.

### Secure encryption (7)

| Tool | Directions | Requires | Classification |
| --- | --- | --- | --- |
| AES-GCM Encryption | Encrypt / Decrypt | Password + key size (128 / 192 / 256) | **Authenticated encryption** |
| AES-CBC + HMAC | Encrypt / Decrypt | Password + key size (128 / 192 / 256) | **Authenticated encryption** (Encrypt-then-MAC) |
| ChaCha20-Poly1305 | Encrypt / Decrypt | Password | **Authenticated encryption** (Android 9+; 256-bit) |
| AES-CTR + HMAC | Encrypt / Decrypt | Password + key size | **Authenticated encryption** (stream mode, authenticated) |
| AES-GCM with your own key | Encrypt / Decrypt | Raw key as Base64 or hex (16 / 24 / 32 bytes) | **Authenticated encryption** (no key derivation) |
| RSA-OAEP + AES-GCM | Encrypt / Decrypt | RSA public key (encrypt) or private key (decrypt) | **Authenticated encryption** (hybrid, any text length) |
| RSA Key Pair Generator | Generate | Key size (2048 / 3072 / 4096) | Key material — **not** encryption |

### Hashing & authentication (4)

| Tool | Directions | Requires | Classification |
| --- | --- | --- | --- |
| Hash (one-way) | Hash / verify | Algorithm (MD5, SHA-1, SHA-256, SHA-512), output format | Cryptographic hash (one-way) — MD5 and SHA-1 marked broken |
| HMAC | Tag / verify | Shared secret key, algorithm | Message authentication code |
| PBKDF2 Password Hash | Hash / verify | Password (≥ 8 characters) | Cryptographic hash (one-way), 210 000 iterations |
| Checksum | Checksum / verify | Kind (CRC-32 / Adler-32), output format | Checksum (integrity only) |

### Classical ciphers (27)

| Tool | Key | Notes |
| --- | --- | --- |
| Caesar Cipher | Shift (−25 … 25) | Case preserved, non-letters untouched |
| Vigenère Cipher | Keyword | Letters only consume key characters |
| Atbash | – | Reciprocal (A↔Z) |
| Substitution Cipher | 26-letter alphabet | Duplicates and wrong length rejected by name |
| ROT13 / ROT18 / ROT47 | – | Obfuscation, not encryption |
| Affine Cipher | A (coprime with 26), B | `A` validated with extended Euclid |
| Playfair Cipher | Keyword, grid, filler | 5×5 (I/J merged) or 6×6 letters + digits |
| Rail Fence Cipher | Rails 2 … 20, mode | All characters, or letters only |
| Columnar Transposition | Keyword, padding | Stable column ranking, optional X padding |
| Bacon's Cipher | Alphabet convention | 26-letter or 24-letter (I=J, U=V) |
| A1Z26 | Separators | Text ↔ numbers |
| Beaufort Cipher | Keyword, form | Beaufort (K−P) or Variant (P−K), reciprocal |
| Autokey Cipher | Primer keyword | Plaintext continues the key |
| Gronsfeld Cipher | Digit key | Numeric shifts 0–9 |
| Hill Cipher (2×2) | 4-letter matrix, padding | Determinant checked for invertibility |
| Hill Cipher (3×3) | 9-letter matrix, padding | Same rules, three-letter blocks |
| Bifid Cipher | Optional keyword, period | 5×5 square |
| Polybius Square | Grid, separator, optional keyword | 5×5 or 6×6 (letters + digits) |
| Trifid Cipher | Keyword, period ≥ 3, cube | 27-cell cube, letters or merged I/J + space |
| Porta Cipher | Keyword | Reciprocal; letter pairs share a row |
| Scytale | Rod diameter 2 … 20 | Wrap on a cylinder |
| ADFGX / ADFGVX | Square key + column key | Letters and digits only; both keys needed to decode |
| Enigma Machine | Reflector B/C, rotors I–V, rings, positions, plugboard | Historical and **broken**; reciprocal, never encrypts a letter to itself |
| Vigenère / Beaufort (custom) | Variant, key, 26- or 36-character alphabet | Same family, your alphabet |
| Caesar Brute Force | – | Lists all 25 shifts (analysis helper, one-way) |

### Encodings (25)

| Tool | Variants / notes |
| --- | --- |
| Base64 | Standard, URL-safe |
| Base32 | RFC 4648, Base32 Hex |
| Base58 | Bitcoin, Ripple, Flickr |
| Base85 / Ascii85 | Ascii85, Ascii85 with `z`, Z85 |
| URL Encoding | RFC 3986 (`%20`), form style (`+`) |
| Hexadecimal | Continuous, space, comma |
| Binary | 8-bit groups, continuous |
| ASCII | Decimal, binary, hex |
| Decimal | Space, comma, newline |
| Octal | Space separated, continuous |
| Unicode Code Points | U+, plain hex, decimal |
| Morse Code | Configurable word separator |
| Baudot / ITA2 | ITA2 and US TTY, group formatting, unsupported-character policy |
| Base45 | RFC 9285 (used by EU DCC QR codes) |
| Quoted-Printable | RFC 2045, 76-character soft wrapping |
| HTML Entities | All or minimal escaping; named, decimal and hex on decode |
| Unicode Escapes | JavaScript `\uFFFF`, braced `\u{1F600}`, Python `\U0001F600` |
| NATO Phonetic | NATO (Alfa…) and old ICAO (Able…) |
| Base91 | basE91 alphabet; whitespace ignored on decode |
| Punycode | RFC 3492 IDN conversion |
| Braille Bits | One cell per hex nibble, four cells per 16-bit unit |
| Roman Numerals | 1 … 3999, strict validation |
| UTF-16 Units | Hex, decimal or `\uXXXX` escapes (surrogates visible) |
| UTF-32 Code Points | Hex, decimal or `\U00000000` escapes |
| Hex Dump | 16-byte rows: offset, hex column, `\|ASCII\|` gutter |

### Transformations (10)

| Tool | Notes |
| --- | --- |
| Reverse Text | Characters, each line, word order, line order |
| XOR | UTF-8 bytes, hex or Base64 output — **reversible transformation, not secure** |
| Case Converter | UPPER, lower, Title, Sentence, tOGGLE, camel, Pascal, snake, kebab, CONSTANT, dot |
| Line Tools | Sort (A→Z, Z→A, length, numeric), de-duplicate, trim, drop empty, reverse, number, join |
| Leetspeak (1337) | Light and heavy substitution |
| JSON Formatter | Indent 2 spaces / tabs, minify, list key paths; parse errors name the line |
| Regex Tester | Find, replace, split, highlight; ignore case, multiline, dot-all |
| Text Diff | Two versions separated by a separator line; reports unchanged / removed / added |
| Text Statistics | Characters, UTF-16 units, code points, UTF-8 bytes, words, unique words, sentences, paragraphs |
| JWT Inspector | Decodes header and payload, shows `alg` and `exp` — **never verifies a signature** |

Every tool has an ⓘ information sheet with its summary, key requirement, classification, use cases,
warnings and conventions. `docs/TOOLS.md` has the same reference with worked examples.

---

## 3. Encoding vs. cipher vs. hash vs. cryptography

Text Hub labels every tool, and the labels are enforced by tests:

| Label | Meaning | Examples here |
| --- | --- | --- |
| **Encoding** | A reversible representation of the same data. **No secrecy whatsoever** — anyone can decode it. | Base64, Base32, Base58, Base85, Base91, Base45, URL, Hex, Binary, ASCII, Decimal, Octal, Unicode, Morse, Baudot/ITA2, Punycode, Braille, Quoted-Printable, HTML entities, Unicode escapes |
| **Historical character encoding** | An encoding tied to a past technology (5-bit teleprinter codes). Still not encryption. | Baudot / ITA2 |
| **Classical cipher** | A hand cipher from before computers. Reversible, but broken by frequency analysis or known plaintext today. | Caesar, Vigenère, Playfair, Hill, Enigma and the rest |
| **Obfuscation** | Makes text unreadable at a glance; carries no security claim at all. | ROT13, ROT18, ROT47, Leetspeak |
| **Reversible transformation** | A byte-level transformation with a key that is *not* a secure cipher. | XOR |
| **Cryptographic hash (one-way)** | Fingerprint. Cannot be reversed, but is not encryption. | MD5 (broken), SHA-1 (broken), SHA-256, SHA-512, PBKDF2 |
| **Message authentication code** | Proves a message came from someone holding the key and was not altered. Hides nothing. | HMAC-SHA-1/256/512 |
| **Checksum** | Detects accidental corruption only; not tamper-proof. | CRC-32, Adler-32 |
| **Authenticated encryption** | The only tools that actually protect a secret. | AES-GCM, AES-CBC + HMAC, ChaCha20-Poly1305, AES-CTR + HMAC, AES-GCM with your own key, RSA-OAEP + AES-GCM |
| **Key material** | Produces keys, is not encryption itself. | RSA Key Pair Generator |

A registry test asserts that **only** those six authenticated tools may carry the *secure* flag, and
that each of them requires a password or key parameter. Nothing in the app claims Base64, ROT13,
XOR, Caesar, a hash or a checksum is encryption, and nothing claims any output is "unbreakable".

---

## 4. Encryption and payload formats

Only platform-provided primitives are used (`javax.crypto`, `java.security`); no cryptographic
algorithm is implemented by hand anywhere in this project.

| Tool | Construction | Key derivation |
| --- | --- | --- |
| AES-GCM | `AES/GCM/NoPadding`, 128-bit tag | PBKDF2-HMAC-SHA256, 210 000 iterations, key length from the key-size setting |
| AES-CBC + HMAC | `AES/CBC/PKCS5Padding` + HMAC-SHA256 tag over the ciphertext (Encrypt-then-MAC, tag verified first) | PBKDF2 output split: AES key + independent 32-byte MAC key |
| ChaCha20-Poly1305 | RFC 8439 from the platform provider (Android 9+) | PBKDF2-HMAC-SHA256, 210 000 iterations, 32-byte key |
| AES-CTR + HMAC | `AES/CTR/NoPadding` with a random counter block per message + HMAC-SHA256 tag | PBKDF2 output split: AES key + independent 32-byte MAC key |
| AES-GCM with your own key | `AES/GCM/NoPadding`, key used exactly as pasted | none — the key length is stored in the header, the key never is |
| RSA-OAEP + AES-GCM | RSA-OAEP (SHA-256) wraps a fresh 32-byte AES key; the text is encrypted with AES-256-GCM | hybrid: public key encrypts the payload key, so any text length works |

**Payloads.** Every encrypted result is a single Base64 string with a version byte, so old data
stays readable:

| Version | Tool | Layout |
| --- | --- | --- |
| `0x01` | AES-GCM | kdfId · salt(16) · IV(12) · ciphertext ‖ GCM tag(16) |
| `0x02` | AES-CBC + HMAC | kdfId · salt(16) · IV(16) · ciphertext · HMAC tag(32) |
| `0x03` | ChaCha20-Poly1305 | kdfId · salt(16) · nonce(12) · ciphertext ‖ Poly1305 tag(16) |
| `0x04` | AES-CTR + HMAC | kdfId · salt(16) · counter block(16) · ciphertext · HMAC tag(32) |
| `0x05` | AES-GCM with your own key | key length · IV(12) · ciphertext ‖ GCM tag(16) |
| `0x11` | RSA-OAEP + AES-GCM | algorithm id · wrapped-key length · RSA-wrapped key · IV(12) · ciphertext ‖ GCM tag(16) |

**Key sizes are settings, not separate tools — and they are enforced.** *AES-GCM Encryption*,
*AES-CBC + HMAC* and *AES-CTR + HMAC* offer AES-128, AES-192 and AES-256. The size is recorded in
the payload's `kdfId` byte, and decryption uses exactly that size:

* a message written with AES-128 is **refused** by the AES-256 setting, with a message naming the
  size it needs (*"…encrypted with a 128-bit AES key, but the key size selected here is
  different"*). Changing the setting cannot silently succeed;
* payloads written before 1.4.2 did not record the size. They still decrypt, and the app reports
  which size they actually use so the user can select it — no data is stranded;
* wrong-size keys are named just as explicitly by *AES-GCM with your own key* and
  *RSA-OAEP + AES-GCM* (which reports the RSA key size a message was encrypted for).

Full specification, validation rules, the compatibility contract and the security reasoning:
[`docs/ENCRYPTION_FORMAT.md`](docs/ENCRYPTION_FORMAT.md).

**Every setting that affects the result is recorded or refused - nothing is guessed.** Key size,
mode, padding, character encoding, IV/nonce and its format, tag length and AAD are parameters, not
separate tools; the ones that change the bytes on the wire are stored in the payload, and the ones
that cannot be stored (an OpenSSL password, a JWE recipient key, a Base58 alphabet) are enforced the
moment they are used: a payload that does not decode to text with the selected variant is refused
with a sentence pointing at the setting, never returned as mojibake. `ParamDisciplineAuditTest`
re-runs that rule across the whole registry.

**Files from other tools are accepted when their settings are identifiable.** *AES-CBC + HMAC* can
read OpenSSL `enc` output (with or without `-pbkdf2`, any iteration count, `-md` MD5/SHA-256/
SHA-512, an explicit `-S` salt, and `-nosalt` output refused with an explanation). *AES-GCM with
your own key* reads JWE compact tokens with `alg = dir`, and *RSA-OAEP + AES-GCM* reads tokens with
`alg = RSA-OAEP` / `RSA-OAEP-256` - in both cases any of the six standard AES content-encryption
algorithms (`A128GCM`, `A192GCM`, `A256GCM`, `A128CBC-HS256`, `A192CBC-HS384`, `A256CBC-HS512`).
When something is missing - no salt, an unknown `alg` or `enc`, a truncated block - the error says
which piece is missing instead of claiming the data came from somewhere else. No compatibility is
claimed that is not covered by a test: `ExternalFormatsTest` carries real `openssl enc` output and
real JWE tokens.

`RSA Key Pair Generator` creates a 2048/3072/4096-bit pair on the device, prints both PEM blocks
(X.509 public, PKCS#8 private) and a SHA-256 fingerprint of the public key. Nothing is written to
disk: copy the text yourself if you want to keep it.

---

## 5. Architecture

Two Gradle modules with a hard boundary between algorithms and UI:

```
:core   plain JVM library (no Android dependency)
        every algorithm, every processor, all unit tests
:app    Android application (Kotlin + Jetpack Compose, Material 3)
        tool picker, main screen, settings, theme, view model
```

**`:core` is Android-free on purpose:** algorithms and crypto run against the *real* JDK
(`javax.crypto`, `java.util`), not against `android.jar` stubs, so unit tests exercise the exact
code that ships. It also means the whole tool set is testable on any machine with a JDK.

Key pieces:

* `TextProcessor` — the one interface every tool implements: `meta` (id, name, glyph, category,
  classification, direction labels, parameters, info sheet, keywords) and
  `process(input, params, direction)`.
* `ToolRegistry` — the single list of 73 processors, plus search and lookup. Adding a tool means
  writing one class and adding one line.
* `ProcessingEngine.run(...)` — executes a processor and converts *any* failure into a friendly,
  human-readable message. Raw exceptions never reach the UI.
* `model/` — `ToolMeta`, `ParamSpec` (`TEXT`, `PASSWORD`, `NUMBER`, `CHOICE`, `ALPHABET`,
  `MULTILINE`), `Classification` (with the `secure` flag), `ToolCategory`, `Direction` and 59
  ready-made friendly error messages in `Errors.kt`.
* `codec/`, `crypto/`, `util/` — hand-written codecs where the platform cannot help (Base32/58/85/91,
  Punycode, Braille, hex dump …), platform-backed crypto (`AesGcmPayload`, `AesCbcHmac`,
  `AesCtrHmac`, `ChaCha20Poly1305`, `RawKeyGcm`, `RsaHybrid`, `Pbkdf2`, `Digests`, `Checksums`) and
  a dependency-free JSON parser/writer.
* `:app` — `HubViewModel` (single `StateFlow` of `HubUiState`), `MainScreen`, `ToolPickerSheet`,
  `ToolInfoSheet`, `SettingsScreen`, `prefs/AppPreferences` and `ui/theme` (colours, accent system,
  typography, shapes, spacing).

Processing never touches the main thread: input above the size threshold runs on
`Dispatchers.Default` behind a progress indicator. Processors are pure functions of
(text, params, direction).

---

## 6. How to add a new processor

```kotlin
// core/src/main/kotlin/com/texthub/core/processors/MyToolProcessor.kt
class MyToolProcessor : TextProcessor {

    override val meta = ToolMeta(
        id = "mytool",                       // unique, lowercase, also the search key
        name = "My Tool",
        glyph = "MT",
        category = ToolCategory.ENCODING,
        classification = Classification.ENCODING,   // never claim security you do not have
        encodeLabel = "Encode",
        decodeLabel = "Decode",
        params = listOf(
            ParamSpec(key = "variant", label = "Variant", kind = ParamKind.CHOICE,
                defaultValue = "a", choices = listOf(Choice("a", "Variant A"))),
        ),
        info = ToolInfo(
            summary = "What it does, in one or two sentences.",
            requiresKey = false,
            useCases = listOf("Why someone would reach for this"),
            warnings = listOf("Encoding, not encryption."),
            convention = "Any convention a reader must know.",
        ),
        keywords = listOf("mytool", "synonym", "search word"),
    )

    override fun process(input: String, params: Map<String, String>, direction: Direction): String =
        throw Errors.emptyInput()            // or the real implementation
}
```

Then register it: `ToolRegistry.all` → add `MyToolProcessor(),`. Nothing else changes — the picker,
search, favourites, the info sheet, the parameter editors and the round-trip tests all pick it up
automatically. Rules for new tools:

1. Throw `ToolException` (via `Errors`) for user-correctable problems — never a stack trace.
2. Never log input, output, keys or ciphertext; there is no logging in this project at all.
3. Mark key/password parameters `sensitive = true` so they are never persisted.
4. Add at least one test with a reference vector, and cover the error path.

---

## 7. Build and run

Requirements: **JDK 17**, **Android SDK platform 34** + **build-tools 34.0.0**. Gradle 8.2 is used
through the wrapper.
There is also `tools/install-toolchain.sh`, which installs a JDK, Gradle and the SDK from scratch on
a bare machine (useful for CI or a fresh sandbox), and `tools/make_legacy_icons.py`, which
regenerates the pre-adaptive launcher PNGs.

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/Android/Sdk      # or set sdk.dir in local.properties

./gradlew :core:test             # 246 unit tests
./gradlew :app:assembleDebug     # debug APK
./gradlew :app:assembleRelease   # signed release APK
./gradlew :app:installDebug      # install on a connected device

# Optional: R8 shrinking (needs ~2 GB of RAM for the build JVM)
./gradlew :app:assembleRelease -PenableR8=true -Dorg.gradle.jvmargs=-Xmx2g
```

Notes:

* R8 shrinking is **opt-in** so the project builds on small machines; ProGuard rules are already in
  `app/proguard-rules.pro`. With shrinking enabled the APK drops to roughly half its size.
* `gradle.properties` is deliberately conservative (`-Xmx640m`, serial GC, one worker) so builds
  succeed on 2 GB boxes. On a larger machine raise `org.gradle.jvmargs` and `org.gradle.workers.max`.
* Lint runs with `abortOnError = true`; warnings are part of the build.

### Pre-built APKs

| File | Size | md5 |
| --- | --- | --- |
| `apk/TextHub-1.5.1-release.apk` (signed) | 9,566,976 B | `9f129cee14f3e126f29881184b598414` |
| `apk/TextHub-1.5.1-debug.apk` | 14,371,016 B | `c62dc3ef9d80c4ae727bbc213af478e6` |

Both report `versionName 1.5.1`, `versionCode 8`, `minSdk 24`, `targetSdk 34`, and declare **no
permissions** other than Android's own `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.
`TextHub-1.5.1-source.zip` contains the complete source project, documentation, Gradle configuration,
tests, resources, icons, themes and the release keystore, so updates can be signed with the same
key.

---

## 8. Signing and updates

The release keystore ships with the project (`texthub-release.jks`, alias `texthub`) and
`app/build.gradle.kts` already points at it, so `assembleRelease` produces an update-ready APK.

* Certificate SHA-256: `CC:69:D4:D0:50:EC:FA:9F:7A:94:B9:6E:9D:EE:30:5F:C4:4B:7D:B8:CA:17:BB:E1:B1:93:CD:B4:18:29:82:B1`
* Valid until 2056-09-13 (RSA 2048-bit, `SHA256withRSA`), application ID `com.texthub.app`

**Two rules for every update:** raise `versionCode` and never change `applicationId` or the signing
key. Android then treats the new build as an update and keeps user preferences in place.

```bash
./gradlew :core:test
./gradlew :app:assembleRelease
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
adb install -r app/build/outputs/apk/release/app-release.apk
```

Back up the keystore in at least two secure places before publishing anything: without it, no
update can ever be shipped to that installation. Passwords, backup advice (including a
password-manager Base64 trick), manual signing and how to switch to your own key are documented in
[`docs/SIGNING.md`](docs/SIGNING.md).

---

## 9. Testing

`./gradlew :core:test` runs **260 unit tests** in `core/src/test/kotlin/com/texthub/core/`:

| Test class | Tests | Coverage |
| --- | --- | --- |
| `BaseFamilyTest` | 24 | Base64/32/58/85: ASCII, Unicode, empty input, RFC 4648 vectors, partial blocks, `z` compression, Z85 vectors, leading zero bytes, invalid input, URL-safe variant, alternate alphabets |
| `TextEncodingsTest` | 28 | URL (spaces, symbols, Unicode, invalid escapes), Hex, Binary, ASCII (incl. non-ASCII rejection), Decimal, Octal, Unicode code points (BMP, emoji above U+FFFF, surrogates) |
| `SignalEncodingsTest` | 16 | Morse (letters, digits, punctuation, invalid symbols, word separators); Baudot/ITA2 (letters, figures, shifts, spaces, named controls, round trips, invalid groups, ITA2 vs US TTY) |
| `ClassicalCipherTest` | 50 | Caesar, ROT13/18/47, Atbash, Vigenère (`LEMON`/`ATTACKATDAWN`), XOR, Substitution, Bacon, A1Z26, Rail Fence, Affine, Playfair, Columnar, Reverse and more |
| `AesGcmTest` | 15 | Round trip, Unicode, empty input, wrong password, tampered ciphertext/IV, truncation, payload layout, fresh salt/nonce per message, PBKDF2 cross-checked against `SecretKeyFactory` |
| `NewToolsTest` | 37 | Base45 (RFC 9285 vector), Quoted-Printable, HTML entities, Unicode escapes, NATO, Leetspeak, Beaufort, Autokey, Gronsfeld, Hill 2×2, Bifid, Polybius, line tools, case converter |
| `NewTools2Test` | 46 | AES key sizes, hashes (MD5/SHA-1/256/512 + Base64 form), HMAC vectors, PBKDF2 format and verification, CRC-32/Adler-32, Base91, Punycode (`münchen` → `mnchen-3ya`), Roman numerals, UTF-16/UTF-32 views, hex dump, Hill 3×3, Porta, Trifid, Scytale, ADFGX, Enigma (`BDZGO`, `EWTYX`, `RXWKBV`, reciprocity), custom-alphabet Vigenère, JSON, regex, diff, statistics, JWT |
| `SecureToolsTest` | 15 | AES-GCM and AES-CBC at 128/192/256 through the `keySize` setting (round trip, size detected on decrypt, one AES tool per mode), AES-CTR (all sizes, tamper, wrong password, non-determinism), raw-key AES-GCM (Base64 and hex keys, wrong length/value), RSA hybrid (2048-bit round trip, ~2 kB of text, wrong private key, tampering, PEM/PKCS#1 mistakes), key generation |
| `RegistryTest` | 15 | Unique ids, every tool documented and classified, only the six authenticated tools may claim security, sensitive parameters flagged, search behaviour, round trip of every tool, Unicode round trips, friendly error messages (no stack traces), direction swap |
| `ToolAuditTest` | 12 | Sweeps **every** registered tool: choice/number parameters, default maps, sensitive flags, friendliness for ten unusual inputs in both directions, searchability by id and name, security-claim discipline, documentation coverage, key-size enforcement, and regression tests for the bugs found by the audit |
| `ToolAuditReportTest` | 1 | Writes `core/build/tool-audit.txt`: a per-tool report (id, category, parameters, behaviour on a sample, round trip) for reviewing the whole registry at once |

`RegistryTest` iterates over the *whole* registry, so a newly added tool is immediately covered by
round-trip, classification, documentation and error-message checks. JUnit XML reports land in
`core/build/test-results/test/`.

Outside the sandbox, one manual pass is worth doing on a device: open the picker and scroll to the
end, switch accents, generate a key pair with RSA and check the icon on the home screen. The
device script lives in [`docs/QA_CHECKLIST.md`](docs/QA_CHECKLIST.md), and [`docs/RELEASE_REPORT_1.5.1.md`](docs/RELEASE_REPORT_1.5.1.md) records what this release changed, what was verified with tooling and what could not be verified without a device.

---

## 10. Privacy behaviour

* **No permissions.** The manifest declares no `uses-permission` entries — the `INTERNET`
  permission is explicitly stripped with `tools:node="remove"`, so no build of the app can hold it,
  even if someone later adds a network call.
* **No network code.** No `HttpURLConnection`, OkHttp, Retrofit or WebView anywhere; the app works
  in airplane mode.
* **No analytics, ads, trackers or accounts.**
* **No text is persisted.** Input, output, keys and passwords live in memory only.
* **What is stored** (in `texthub_preferences.xml`): theme, accent colour, the auto-process and
  copy-confirmation switches, the last used tool, favourites and recents (tool ids only), and
  non-secret parameter values such as a Caesar shift, a separator or a chosen variant. Parameters
  flagged `sensitive` (passwords, raw keys, RSA keys) are filtered out before saving —
  `HubViewModel.persistParams()`.
* **History does not exist.** Text Hub deliberately keeps no history of processed text; the
  settings screen says so.
* **Clear temporary data** (Settings → Privacy) removes remembered parameters, favourites and
  recents. Appearance settings survive.

Details: [`docs/PRIVACY.md`](docs/PRIVACY.md).

---

## 11. Security notes

* **No logging.** There is no `android.util.Log`, `println` or `printStackTrace` in the project.
  Plaintext, keys, passwords and ciphertext are never written anywhere.
* **No homemade cryptography.** Real encryption and hashing come from the platform provider
  (Conscrypt on Android, the JDK in tests). Hand-written code is limited to encodings and classical
  ciphers, which are labelled as such.
* **Randomness** comes from `java.security.SecureRandom` only, and every message gets a fresh salt,
  nonce or counter block.
* **Password handling:** passwords and keys are held as `CharArray`/`String` only for the duration
  of one operation and are zeroed afterwards; derived keys are never persisted.
* **No secrets on disk:** no password, key or plaintext is written to storage, backup or logs.
* **Key loss is permanent.** There is no recovery path for a lost password — by design — and a lost
  RSA private key makes every message encrypted to it unreadable. Nothing is escrowed.
* **Raw-key AES-GCM has no key stretching.** A key that is weak when pasted stays weak; the
  password-based tools exist precisely because of that.
* **Honest claims.** Only the six authenticated tools are labelled secure. MD5 and SHA-1 are
  labelled broken, Enigma is labelled broken, checksums are labelled integrity-only, and nothing is
  called "unbreakable". The JWT Inspector states in its own output that it does not verify
  signatures.
* **Not audited.** Text Hub has not undergone an independent security review. Treat it as a
  well-built utility, not as a hardened secure messenger.

---

## 12. Theming, accent colours and accessibility

**Themes.** System (follows the OS), Dark (default look, deep slate), AMOLED (pure black, also for
dark mode), Light. One restrained design language, Material 3 typography and a shared spacing
scale.

**Accent colours.** Settings → Appearance offers eight swatches — Teal, Blue, Indigo, Violet, Rose,
Amber, Green, Graphite. Only the primary/secondary roles are recoloured while the neutrals stay
fixed, so contrast holds in every combination. The selection is shown with a check mark *and* a
border, never by colour alone.

**Accessibility and UX rules.**

* Every interactive element has a text label or a `contentDescription`; icon-only buttons have a
  description and a 48 dp touch target.
* Font scaling is respected: Material 3 type scale plus scrollable columns, so 200 % font sizes do
  not clip content.
* The input field accepts direct typing with the on-screen keyboard, disables autocorrect and
  spellcheck dictionaries, and keeps the caret where you left it.
* Keyboard behaviour: `windowSoftInputMode=adjustResize` plus an `imePadding()` container keeps the
  button row reachable; the input header has a hide-keyboard action.
* The result area is read-only monospace text; long input is processed off the main thread.
* Content is capped at 720 dp wide and centred on tablets and in landscape.
* The tool picker keeps its scroll position across recompositions, scrolls to the top when the
  query or filter changes, and its list is fully reachable at any list length.

---

## 13. Project layout

```
TextHub/
├── apk/
│   ├── TextHub-1.5.1-release.apk        signed release build
│   └── TextHub-1.5.1-debug.apk          debug build
├── core/                                plain JVM library: algorithms + tests
│   └── src/
│       ├── main/kotlin/com/texthub/core/
│       │   ├── TextProcessor.kt         the interface every tool implements
│       │   ├── ToolRegistry.kt          all 73 tools, search, ProcessingEngine
│       │   ├── model/                   ToolMeta, ParamSpec, Classification, Errors
│       │   ├── codec/                   Base32/45/58/64/85/91, Punycode, Braille,
│       │   │                            Baudot tables, hex dump, checksums, …
│       │   ├── crypto/                  AesGcmPayload, AesCbcHmac, AesCtrHmac,
│       │   │                            ChaCha20Poly1305, RawKeyGcm, RsaHybrid,
│       │   │                            Pbkdf2, Digests
│       │   ├── processors/              one class per algorithm (73 tools)
│       │   └── util/                    dependency-free JSON, text helpers
│       └── test/kotlin/com/texthub/core/  246 unit tests in 9 classes
├── app/                                 Android app (Kotlin + Compose)
│   └── src/main/
│       ├── kotlin/com/texthub/app/
│       │   ├── MainActivity.kt
│       │   ├── ui/                      HubApp, MainScreen, ToolPickerSheet,
│       │   │                            ToolInfoSheet, SettingsScreen, components,
│       │   │                            theme/ (colours, accents, type, spacing)
│       │   ├── viewmodel/HubViewModel.kt   single StateFlow of HubUiState
│       │   └── prefs/AppPreferences.kt     harmless UI preferences only
│       └── res/                         strings, themes, adaptive launcher icon
├── docs/
│   ├── ENCRYPTION_FORMAT.md             payload specification + compatibility contract
│   ├── PRIVACY.md                       what is stored, what is never stored
│   ├── QA_CHECKLIST.md                  verification checklist for each round
│   ├── SIGNING.md                       keystore, fingerprints, shipping updates
│   └── TOOLS.md                         full tool reference with worked examples
├── tools/
│   ├── install-toolchain.sh             JDK + Gradle + Android SDK on a bare machine
│   ├── make_legacy_icons.py             regenerates the pre-adaptive launcher PNGs
│   └── update_docs_r3.py, update_docs_r4.py   documentation passes, kept for reference
├── texthub-release.jks                  release keystore (see docs/SIGNING.md)
├── build.gradle.kts, settings.gradle.kts, gradle.properties
└── README.md
```

---

## 14. Known limitations

* **R8 shrinking is opt-in** (`-PenableR8=true`) because the default build target here is a small
  machine. ProGuard rules are in place; expect roughly half the APK size with it on.
* **The release keystore is bundled** with the project so updates can be signed with the same key.
  Never publish the zip containing it, and back the keystore up before you ship anything.
* **No on-device UI run is automated here.** The algorithms are covered by 246 unit tests and the
  app builds clean, but the checklist in `docs/QA_CHECKLIST.md` is the script for a final manual
  pass on real hardware (emulators are not available in the build environment).
* **ChaCha20-Poly1305 needs Android 9+.** On older devices the tool says so and points at AES-GCM
  instead of silently falling back to something weaker.
* **Classical ciphers drop what their alphabets cannot carry** (Playfair, Bacon, Hill, Trifid,
  ADFGX and friends keep letters — and sometimes digits — only). Each tool documents this in its
  info sheet.
* **RSA-OAEP cannot encrypt long text on its own**, which is exactly why the hybrid construction is
  used; the private key is the single point of failure for reading messages.
* **Text Hub is not audited software**, and it is not a replacement for a password manager or a
  messenger built for secrecy.

---

## 15. Licensing and third-party notices

The project ships no third-party runtime libraries other than AndroidX/Jetpack Compose (Apache
License 2.0), Kotlin (Apache License 2.0) and the Android SDK (Apache License 2.0); their licences
are listed in the app under *Settings → About → Open-source licences*.

No licence has been applied to this repository yet — pick one before publishing (Apache-2.0 and MIT
are both fine for a project shaped like this).

---

*Text Hub — an offline text utility. Encoding, not encryption; keys are yours to keep.*
