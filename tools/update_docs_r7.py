"""Round 7 documentation refresh: version 1.5.0, the round-7 change set, the new test total,
parameter discipline and external compatibility. Run once."""
import io

V = "1.5.0"
VC = 7
TESTS = 307
REL_BYTES = "9,562,652"
REL_MD5 = "91dc659f5f4d2bc6125304956f432827"
DBG_BYTES = "14,362,548"
DBG_MD5 = "b9fd9a9a79f0383828416e7122ba576f"

# ------------------------------------------------------------------ README
p = "README.md"
s = io.open(p, encoding="utf-8").read()

s = s.replace("| Version | 1.4.2 (versionCode 6) |", "| Version | %s (versionCode %d) |" % (V, VC))
s = s.replace("| Tests | 260 unit tests, all green (`:core:test`) |",
              "| Tests | %d unit tests, all green (`:core:test`) |" % TESTS)

whats_new = """## What's new in 1.5.0

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

**3. Controls are generated from what a tool can actually do.** Single-operation tools no longer
show an Apply button that would repeat the same operation, swap is hidden wherever the result cannot
be pushed back (digests, checksums, comparisons, analysis), a direction switch appears only when it
means something, and one-way tools use one label. The second pass removed the two genuinely
duplicated buttons left in the main screen (the output card had two *Clear* buttons, and *Type here*
only refocused the field above it).

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

Test total: **%d** (`:core:test`, all green).

---

## What's new in 1.4.2
""" % TESTS

old_head = "## What's new in 1.4.2\n"
assert old_head in s
s = s.replace(old_head, whats_new, 1)

s = s.replace("""| `apk/TextHub-1.4.2-release.apk` (signed) | 9,523,872 B | `b7cbed1bb55112d4248eadeb403d05a0` |
| `apk/TextHub-1.4.2-debug.apk` | 14,301,140 B | `6715cc312ca54649f174b2f3eece9bf3` |

Both report `versionName 1.4.2`, `versionCode 6`, `minSdk 24`, `targetSdk 34`, and declare **no
permissions** other than Android's own `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.
`TextHub-1.4.2-source-and-keys.zip` contains the full source together with the release keystore,
so updates can be signed with the same key.""",
"""| `apk/TextHub-%s-release.apk` (signed) | %s B | `%s` |
| `apk/TextHub-%s-debug.apk` | %s B | `%s` |

Both report `versionName %s`, `versionCode %d`, `minSdk 24`, `targetSdk 34`, and declare **no
permissions** other than Android's own `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.
`TextHub-%s-source.zip` contains the complete source project, documentation, Gradle configuration,
tests, resources, icons, themes and the release keystore, so updates can be signed with the same
key.""" % (V, REL_BYTES, REL_MD5, V, DBG_BYTES, DBG_MD5, V, VC, V), 1)

# Parameter discipline + external formats: extend the encryption chapter, right after the key-size rules.
anchor = """Full specification, validation rules, the compatibility contract and the security reasoning:
[`docs/ENCRYPTION_FORMAT.md`](docs/ENCRYPTION_FORMAT.md)."""
addition = anchor + """

**Every setting that affects the result is recorded or refused - nothing is guessed.** Key size,
mode, padding, character encoding, IV/nonce and its format, tag length and AAD are parameters, not
separate tools; the ones that change the bytes on the wire are stored in the payload, and the ones
that cannot be stored (an OpenSSL password, a JWE recipient key, a Base58 alphabet) are enforced the
moment they are used: a payload that does not decode to text with the selected variant is refused
with a sentence pointing at the setting, never returned as mojibake. `ParamDisciplineAuditTest`
re-runs that rule across the whole registry.

**Files from other tools are accepted when their settings are identifiable.** *AES-CBC + HMAC* can
read OpenSSL `enc` output (with or without `-pbkdf2`, any iteration count, `-md` MD5/SHA-256/
SHA-512, `-nosalt` and explicit `-S` salts), and *RSA-OAEP + AES-GCM* can read JWE compact tokens
(`alg = dir` with A256GCM, and A128CBC-HS256). When something is missing - no salt, an unknown KDF,
a truncated block - the error says which piece is missing. No compatibility is claimed that is not
covered by a test: `ExternalFormatsTest` carries real `openssl enc` output and real JWE tokens."""
assert anchor in s
s = s.replace(anchor, addition, 1)
io.open(p, "w", encoding="utf-8").write(s)
print("README updated")

# ------------------------------------------------------------------ ENCRYPTION_FORMAT.md
p = "docs/ENCRYPTION_FORMAT.md"
s = io.open(p, encoding="utf-8").read().rstrip() + """

---

## 12. Parameter discipline (1.5.0)

A parameter is either recorded in the payload, or it is part of the input the user has to supply,
and there is no third case:

| Kind of setting | Examples | What happens on decryption |
| --- | --- | --- |
| Recorded in the payload | key size (`kdfId`), envelope version, RSA algorithm id, key length for a pasted key | Read from the payload and enforced; a conflicting setting is refused with the size the message needs |
| Must be supplied | password, RSA/PEM private key, AAD (an authentication input, never stored) | Missing or wrong values fail authentication; AAD is never auto-filled |
| Defines the transform | Base58 alphabet, Base85 variant, Bacon's variant, Scytale diameter, Baudot alphabet, A1Z26 separators, case/leet/regex mode | Cannot be recovered from the data by construction. The tool says so in its info sheet, and a result that is provably wrong for text (invalid UTF-8, out-of-range units) is refused |

`ParamDisciplineAuditTest` enforces this for all 73 tools: encoding with the defaults and decoding
with one setting changed must return the original text or fail with a friendly sentence - a
confidently wrong answer is a test failure. Tools where a changed setting legitimately changes the
answer are listed explicitly in the test, with the reason, next to the warning the user sees.

## 13. Compatibility with other tools (1.5.0)

| Source | Accepted by | Notes |
| --- | --- | --- |
| `openssl enc -aes-256-cbc` (OpenSSL 3 default: SHA-256, 10 000 iterations) | AES-CBC + HMAC, Format = OpenSSL enc | 16-byte `Salted__` header is parsed; the password is the only secret |
| `openssl enc -md md5` (OpenSSL 1.x behaviour) | same, KDF = legacy | Legacy derivation is a single `EVP_BytesToKey` pass - OpenSSL 1.x had no iteration count |
| `openssl enc -pbkdf2 -iter N` | same, KDF = PBKDF2 | The iteration count is read from the file and reported; it is not a password-strength statement |
| `openssl enc -base64` wrapped output | same | Whitespace inside the Base64 is ignored |
| JWE compact (`alg=dir`, `enc=A256GCM`) | RSA-OAEP + AES-GCM | The protected header supplies the IV, tag and encoding |
| JWE compact (`enc=A128CBC-HS256`) | same, with the content key | Encrypt-then-MAC with the JWE key split (first half HMAC, second half AES) |

Not claimed: JWE with wrapped keys (`RSA-OAEP`, `A256KW`), other `enc` algorithms, and OpenSSL
files whose cipher is not AES-256-CBC. The error message names the part that is missing instead of
saying "this was not encrypted by Text Hub".
"""
io.open(p, "w", encoding="utf-8").write(s)
print("ENCRYPTION_FORMAT.md updated")

# ------------------------------------------------------------------ PRIVACY.md
p = "docs/PRIVACY.md"
s = io.open(p, encoding="utf-8").read().rstrip() + """

---

## Temporary data (1.5.0)

The only disposable data the app holds is:

* remembered tool settings (parameter values per tool id),
* the recent-tools list.

Settings shows the current size of that store in B / KB / MB and offers a single *Clear* action with
a confirmation that lists what will be removed and what will be kept. Everything else is
configuration the user chose and is **kept**: favourites and their order, theme, accent colour and
the currently selected tool. Text, passwords and keys are never stored in the first place, so
clearing cannot delete - or preserve - them; they exist only in memory for the duration of the
action.
"""
io.open(p, "w", encoding="utf-8").write(s)
print("PRIVACY.md updated")

# ------------------------------------------------------------------ QA_CHECKLIST.md
p = "docs/QA_CHECKLIST.md"
s = io.open(p, encoding="utf-8").read().rstrip() + """

---

## Round 7 - final fix, compatibility and QA (1.5.0)

Automated (all in `:core:test`, %d tests):

| Check | Test |
| --- | --- |
| Clearing temporary data keeps favourites, theme and order | `PrefsModelTest.temporaryDataNeverTouchesTheFavouriteList`, `...clearTemporaryDataRemovesOnlyTemporaryData` |
| Favourite order survives a move in every direction and is clamped at the ends | `PrefsModelTest.dragMovesAFavouriteToTheDroppedPosition` and neighbours |
| AES key size mismatch is refused; legacy payloads decrypt | `SecureToolsTest.theKeySizeIsRecordedForModernPayloadsAndLegacyOnesStillRead`, `ToolReviewPassTwoTest.payloadsWrittenByEarlierVersionsStillDecrypt` |
| External formats (OpenSSL `enc`, JWE) decrypt, and unusable ones explain what is missing | `ExternalFormatsTest` (19 tests) |
| Every parameter either round-trips or fails with a friendly sentence | `ParamDisciplineAuditTest` (4 tests) |
| Only useful controls are visible; settings validate; empty input is safe | `ToolReviewPassTwoTest` (13 tests) |
| All 73 tools still round-trip and search correctly | `RegistryTest`, `ToolAuditTest`, `ToolAuditReportTest` |

Manual (an emulator is not available in this environment - see the build notes):

| # | Step | Expected |
| --- | --- | --- |
| 1 | Install the release APK, launch it | Opens on the tool picker, dark AMOLED theme |
| 2 | Favourite three tools, long-press and drag one to the top, kill the app, reopen | New order is shown |
| 3 | Drag a favourite in the main list or in search results | Nothing moves - only Favourites reorders |
| 4 | Settings → Temporary data | Size shown; *Clear* asks first; afterwards the size is 0 B and favourites are intact |
| 5 | Open AES-GCM, encrypt at 128-bit, switch to 256-bit, decrypt | Refused with a sentence naming the 128-bit key |
| 6 | Paste an OpenSSL `enc -base64` file into AES-CBC + HMAC (Format = OpenSSL enc) | Decrypts; the iteration count is reported for PBKDF2 files |
| 7 | Paste a truncated Base64 block | Friendly sentence naming what is missing |
| 8 | Open a digest tool (SHA-256) | One operation only: no direction switch, no swap, no duplicate Apply |
| 9 | Open AES-GCM with a long password, expand *Additional encryption settings* | Panel scrolls on a small screen, keyboard never hides the buttons |
| 10 | Press *Reset* on a tool with many settings | Documented defaults return, inline errors clear |
""" % TESTS
io.open(p, "w", encoding="utf-8").write(s)
print("QA_CHECKLIST.md updated")

# ------------------------------------------------------------------ TOOLS.md
p = "docs/TOOLS.md"
s = io.open(p, encoding="utf-8").read().rstrip() + """

---

## Common and advanced settings (1.5.0)

Every tool shows the settings its everyday use needs - a password, a key size, a format - and
collects the rest under *Additional encryption settings* / *Additional settings*, collapsed by
default. The section is the same everywhere, so a setting learned in one tool is in the same place
in the next one. Advanced parameters always carry a one-line explanation, impossible combinations
are refused inline while typing, and any tool with more than one setting has a *Reset* action.
"""
io.open(p, "w", encoding="utf-8").write(s)
print("TOOLS.md updated")
