# Text Hub 1.5.0 — final fix, compatibility & QA round

> **Superseded.** 1.5.0 was replaced by **1.5.1**, which fixed the favourites drag-and-drop
> glitch reported after this release and swept a few more bugs (see
> [`RELEASE_REPORT_1.5.1.md`](RELEASE_REPORT_1.5.1.md)). The 1.5.0 APKs and archive listed below
> were removed from the workspace, so only the 1.5.1 files are shipped now; this report is kept as
> the record of what that round contained.

**Version:** 1.5.0 (versionCode 7) · **Tools:** 73 · **Tests:** 317 (`:core:test`, all green)

This round changed no architecture, no visual language, no payload format and no privacy rule. It
fixed what the review justified, added the compatibility work that could be verified, and proved
both with tests.

---

## 1. Delivered artifacts

| Artifact | Size | md5 |
| --- | --- | --- |
| `apk/TextHub-1.5.0-release.apk` (signed, update-compatible with 1.4.2) | 9,562,700 B | `a1a1262657e1b34dbe90c9c5074ec5bf` |
| `apk/TextHub-1.5.0-debug.apk` | 14,465,306 B | `6ac7cc6f2af7e119c5cd6902db83c403` |
| `TextHub-1.5.0-source.zip` (complete project, tests, docs, keystore, both APKs) | 150 files, ~24 MB | see the file itself |

Both APKs: `com.texthub.app` (debug: `.debug`), `versionName 1.5.0`, `versionCode 7`, minSdk 24,
targetSdk 34, **no permissions** except Android's own `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`,
signed with the shipping key (`CN=Text Hub, O=TextHub, C=IN`, SHA-256
`cc69d4d0…b4182982b1`) so it installs over 1.4.2 as an update.

## 2. Requirement-by-requirement result

| # | Requirement | Result |
| --- | --- | --- |
| 1 | Clear temporary data | Standalone *Temporary data* card in Settings, separate from the privacy card. Shows the live size (B/KB/MB), lists what is held, and clears behind a confirmation that names what is removed (remembered tool settings, recents) and what is kept (**favourites and their order**, theme, accent, selected tool). The size is recomputed immediately after clearing. Favourites, keys, passwords and text are never touched - the store never contains them. |
| 2 | Drag to reorder favourites only | Long-press lifts a favourite (scaled, elevated, raised above the list) and drag positions are computed from the measured row height. Each move is written to preferences at once, so the order survives a restart. Only the Favourites section is a `FavoritesToolList`; main list, search, categories and recents cannot be dragged. |
| 3 | No redundant controls | The action button now carries the tool's own verb ("Encrypt", "Decode", "Hash", "Compare") instead of a generic "Process". Swap is gone from every tool whose result is an answer rather than a message (digest, HMAC, PBKDF2, checksum, stats, diff, regex, JWT, brute force, key generation). A direction switch only exists where the two directions differ. The output card's duplicate *Clear* and the pointless *Type here* button were removed, and Enigma's Ringstellung plus the regex flags moved under the collapsed section. |
| 4 | Strict parameter compatibility | Key size (recorded in `kdfId`), envelope version, RSA algorithm id and raw-key length are read from the payload and enforced; a mismatch is refused with a sentence naming the size the message needs. Nothing is auto-detected silently. `ParamDisciplineAuditTest` sweeps all 73 tools: encoding with the defaults and decoding with one setting changed must return the original text or fail with a friendly sentence. |
| 5 | External compatibility | OpenSSL `enc` files (legacy MD5/SHA-256 single-pass, PBKDF2 with any iteration count, wrapped or `-A` output, `-nosalt` and explicit `-S` salts refused with an explanation) decrypt in *AES-CBC + HMAC*; JWE compact tokens (`alg=dir`, `alg=RSA-OAEP`) decrypt in *AES-GCM with your own key* and *RSA-OAEP + AES-GCM* for all six standard AES content-encryption algorithms, and Text Hub writes tokens other implementations read. Ambiguity produces an explanation of what is missing, never "not from Text Hub". |
| 6 | Additional Encryption | Advanced settings live under one collapsed *Additional encryption settings* heading, with defaults that match the ordinary case, helper text on every entry, inline validation, and never hide a required value. Values that a format fixes (GCM tag length, CBC padding, IV length) are documented as fixed rather than exposed as fake controls; nothing unsupported is offered. |
| 7 | Full 73-tool review, second pass | `ToolReviewPassTwoTest` (13), `ControlRulesTest` (10) and `ParamDisciplineAuditTest` (4) now check control visibility, label discipline, primary/advanced split, required and sensitive handling, reset behaviour, inline validation, empty input, Unicode, friendly errors, documented payload formats, ids/searchability and legacy payload compatibility for the whole registry. |
| 8 | Regression tests | 317 tests, 0 failures. Payloads frozen from 1.4.1/1.4.2 decrypt; a 128-bit payload is refused by the 256-bit setting; the wrong Base58/Base85 variant, wrong UTF-16/32 format and A1Z26 separator changes are all rejected or proven harmless; favourites survive clearing and keep their order; temp size drops to 0 B. |
| 9 | UX | Reset action wherever a tool has more than one setting; advanced settings visually separated and collapsed; errors shown inline next to the field they belong to; the whole screen scrolls with `imePadding` so the keyboard never covers the controls; labels and layout identical across tools. |
| 10 | Verification & deliverables | `:core:test` green (317 tests), `:app:lintDebug` clean (0 errors), release and debug APKs rebuilt from the final tree, archive and signature verified with `aapt2`/`apksigner`, version info confirmed in the APK badging and in Settings. **No emulator or KVM is available in this environment**, so installation and first launch could not be exercised on a device - see §4. |

## 3. Defects found and fixed in this round

| # | Defect | Fix |
| --- | --- | --- |
| 1 | A1Z26 decoding with a non-matching separator setting silently re-wrote word boundaries | Decoding now accepts the separators people actually type (hyphen, space, comma, dot, slash, pipe) and refuses values outside 1–26 instead of guessing |
| 2 | Base58 decoding with the wrong alphabet returned confident mojibake | A result that is not valid text is refused with a sentence naming the variant setting |
| 3 | Base85 decoding with the wrong variant did the same | Same treatment, naming the variant |
| 4 | UTF-16/UTF-32 decoding with `format = decimal` read decimal tokens as hexadecimal | Units are validated strictly: 4/8-digit hex, or unpadded decimal - a mismatched payload now explains which format to choose |
| 5 | The output card had two *Clear* buttons | One remains, as the X next to Share |
| 6 | *Type here* only refocused the field directly above it | Removed; the field itself takes focus |
| 7 | Hard-coded, untranslatable button labels ("Process Text", "Use result as input") | Tool-specific verbs from the registry plus string resources (`action_swap_into`, `action_use_as_input`, plurals for counts) |
| 8 | `android:enforceNavigationBarContrast` tripped a lint error on API < 29 | Scoped with `tools:targetApi="29"`; lint is clean again |
| 9 | 19 unused strings/colour resources and a non-plural "%d tools" string | Removed / converted to a proper plural |

Earlier rounds of this same release fixed the OpenSSL legacy derivation (single `EVP_BytesToKey`
pass), the parameter-discipline findings and the duplicated controls; those fixes are all covered
by tests in the same suite.

## 4. Verification performed (and its limits)

Verified with tooling only, because this environment has no emulator, no KVM and no device:

* `:core:test` - 317 tests, 0 failures, across 16 test classes.
* `:app:lintDebug` - 0 errors, 5 dependency-version notices (deliberately pinned versions).
* `apksigner verify` - v2 signature valid, one signer, certificate matches the shipping keystore.
* `aapt2 dump badging` - package/version/minSdk/targetSdk as expected, launchable activity
  `com.texthub.app.MainActivity`, no network permission in the merged manifest.
* Archive and dex inspection - `classes.dex` parses (17 238 classes, 65 433 methods), resources
  linked, no `lib/` payload.

Not verified here: real-device installation, first launch, and the ten manual steps in
`docs/QA_CHECKLIST.md` §*Round 7* (drag behaviour, keyboard interaction on a small screen). Those
need a device or an emulator; everything they exercise is behind the automated tests above.

## 5. Known limitations

* OpenSSL files with `-nosalt` or an explicit `-S` salt store no salt, so they cannot be read;
  the error says exactly that instead of guessing. Ciphers other than AES-256/192/128-CBC are
  refused by name.
* JWE key management is limited to `dir`, `RSA-OAEP` and `RSA-OAEP-256`; `A128KW`, `ECDH-ES`,
  `PBES2` and non-AES content encryption are refused with the algorithm named.
* Settings that a format cannot carry (Base58 alphabet, Base85 variant, Bacon's variant, Scytale
  diameter, Baudot alphabet, A1Z26 separators, case/leet/regex modes) cannot be recovered from
  data; the tools say so in their info sheets and refuse results that are provably wrong.
* Base58/Base85 results that happen to be valid UTF-8 under the wrong setting cannot be detected
  as wrong - no implementation can, since the alphabet is not stored.
* Fixed by the payload formats and therefore not configurable: GCM tag length, CBC padding, IV
  length, PBKDF2 iteration count (210 000) for Text Hub's own payloads.
* No emulator in this environment: installation and first launch were not exercised on a device.
