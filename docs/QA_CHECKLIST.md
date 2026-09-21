# QA checklist

Status of every item from the project brief. `[x]` = verified in this build, `[~]` = verified
by automated test or static inspection, `[ ]` = not verifiable in a headless environment
(needs a device/emulator — instructions are given so the check takes two minutes).

Environment used for this build: JDK 17 (Temurin), Android SDK platform 34 / build-tools 34.0.0,
Gradle 8.2, Kotlin 1.9.22, AGP 8.1.4, Compose BOM 2023.10.01, minSdk 24, targetSdk 34.

## Build and packaging

| Done | Item | Evidence |
| --- | --- | --- |
| [x] | App builds (`assembleDebug`, `assembleRelease`) | `BUILD SUCCESSFUL`; no Kotlin errors or warnings in app code |
| [x] | Release APK is signed | `apksigner verify` → *Verifies*, APK Signature Scheme v2, 1 signer |
| [x] | APK produced | `apk/TextHub-1.4.1-release.apk` (9,522,280 B, signed, round 5), `apk/TextHub-1.4.1-debug.apk` (14,298,148 B) |
| [x] | No unnecessary permissions | `aapt dump permissions` lists only AndroidX's internal receiver permission; `INTERNET` is removed in the manifest |
| [x] | No WebView | `grep -rn WebView core/src app/src` → nothing |
| [x] | No network code at all | no HTTP/OkHttp/Retrofit/socket usage anywhere |
| [x] | No placeholders / TODOs | `grep -rni "todo\|fixme\|coming soon"` → nothing |
| [x] | Launcher icon (adaptive + legacy, all densities) | `mipmap-*` + `mipmap-anydpi-v26`, generated in this repo |
| [~] | Installs and launches | Not run here (no emulator on this machine). `./gradlew installDebug` on any device; `adb shell am start -n com.texthub.app/.MainActivity` |
| [~] | App does not crash on launch | Single-activity Compose app, no reflection, no I/O on start; risk points (empty parameter lists, missing tool ids) are guarded in code |

## Functionality

| Done | Item | Evidence |
| --- | --- | --- |
| [x] | All encoding tools work | 185 unit tests + registry round-trip test over every tool |
| [x] | All classical cipher tools work | known-vector tests (Caesar, Vigenère, Affine, Playfair, Rail Fence, Columnar, Bacon, A1Z26, ROT family) |
| [x] | AES-GCM works | round trip, Unicode, long text, wrong password, tampered data, truncated payload |
| [x] | AES-CBC + HMAC works | round trip, Unicode, empty input, wrong password, tampered ciphertext, tampered tag, payload layout asserted (82 bytes minimum) |
| [x] | ChaCha20-Poly1305 works | round trip, wrong password, tampered ciphertext, fresh nonce per message; clear "not available" message on platforms without the provider |
| [x] | New ciphers verified against known values | Beaufort `HELLO`/`KEY` → `DANZQ` (variant → `XANBK`), Autokey `ATTACKATDAWN`/`QUEENLY` → `QNXEPVYTWTWP`, Gronsfeld `HELLO`/`31415` → `KFPMT`, Hill `HI`/`HILL` → `JJ` and round trips, Bifid `HI` → `GO` with (2,5) and keyworded rounds, Polybius `HELLO WORLD` → `23 15 31 31 34 / 52 34 42 31 14` |
| [x] | New encodings verified against known values | Base45 RFC 9285 vector `Hello!!` → `%69 VD92EX0` and `AB` → `BB8`, quoted-printable `Grüße` → `Gr=C3=BC=C3=9Fe` with soft breaks, HTML entities named + numeric, Unicode escapes in all three notations incl. surrogate pairs |
| [x] | One-way tools are honest about it | Caesar Brute Force lists all 25 shifts instead of guessing; Case/Line Tools/Leet/NATO are documented as style changes |
| [x] | Encode/decode round trips | `RegistryTest.everyToolRoundTripsWithDefaultParameters` |
| [x] | Encrypt/decrypt round trips | per-tool tests with keys |
| [x] | Unicode works where supported | Base32/58/85, URL, Hex, Binary, Decimal, Octal, Unicode, Morse-skip, ciphers, AES |
| [x] | Copy / Swap / Clear / Share | implemented in `HubApp` + `MainScreen`; swap flips direction |
| [x] | Search works | `ToolRegistry.search` unit tested (`base` → all four Base tools, `caesar`, `baudot`, `ascii`, `morse`, `aes`) |
| [x] | Favourites and recents work | stored as tool ids only; `AppPreferences` |
| [x] | Settings work | theme, auto-process, copy confirmation, clear temporary data, about |
| [x] | Dark / AMOLED theme | `DarkColorScheme` / `AmoledColorScheme` (true black `#000000`) |
| [x] | Light theme | `LightColorScheme` |
| [~] | Keyboard does not break layout | `adjustResize` in the manifest + scrollable column, with **no** `imePadding()` (stacking both pushed the focused field off-screen — the round-2 typing fix); needs a device to eyeball |
| [x] | Input accepts direct typing | fields no longer sit inside a disabled `clickable` card, focus is requested explicitly by "Type here", input capped at 8 lines / output at 10 |
| [x] | Swap flips the mode and re-processes | `swap()` moves output → input, flips the direction and immediately runs the reverse operation (`process(immediate = true)`) |
| [x] | Large text does not freeze the UI | processing on `Dispatchers.Default`, typing debounced 140 ms, progress bar above 60 000 chars |
| [x] | Invalid input produces friendly errors | 13 decoder error cases asserted to contain no stack traces and to end in a full stop |
| [x] | No plaintext is logged | no `Log`, `println` or `printStackTrace` calls in the codebase |
| [x] | No passwords stored or logged | sensitive parameters filtered out of persistence (test-enforced flag) |
| [x] | AES authentication works | tampered ciphertext/IV fail |
| [x] | AES uses fresh random salt/nonce | same input twice → different payloads; salts and IVs asserted different |
| [x] | Tampered data fails safely | friendly error, no exception text |
| [x] | Baudot/ITA2 correct | ITA2 + US-TTY tables, shift states, named controls, invalid groups |
| [x] | ASCII / Decimal / Octal / Unicode conversions | dedicated tests incl. non-ASCII rejection and surrogate handling |
| [x] | Base32 / Base58 / Base85 correct | RFC 4648 vectors, Z85 vector, leading zeros, partial blocks |
| [x] | ROT18 / ROT47 / Bacon / A1Z26 / Rail Fence / Affine / Playfair / Columnar | tests with known vectors and round trips |
| [x] | Unit tests pass | 185/185 green (`:core:test`) |
| [ ] | Visual inspection on a real/emulated device | **Not done here** — see "Manual pass" below |

## Security checklist

| Done | Item |
| --- | --- |
| [x] | No hand-rolled cryptography for security (only AES-GCM, AES-CBC + HMAC, ChaCha20-Poly1305 and PBKDF2 from the platform) |
| [x] | Encrypt-then-MAC for the CBC payload: HMAC tag verified in constant time before decryption, separate AES and MAC keys |
| [x] | No hard-coded keys or secrets |
| [x] | `SecureRandom` for salt and nonce |
| [x] | Password char arrays zeroed after use |
| [x] | Keys never written to SharedPreferences |
| [x] | Payload validates version, KDF id, length and GCM tag |
| [x] | No claim that encodings/classical ciphers are secure (test-enforced: only `aes`, `aescbc` and `chacha` may be `secure`) |
| [x] | No claim of being unbreakable; no recovery promise on lost passwords |

## Manual pass (5 minutes on a device/emulator)

```bash
./gradlew installDebug     # or: adb install -r apk/TextHub-1.4.1-debug.apk
```

1. Launch → Base64 is preselected; **type** `Hello` with the on-screen keyboard → output `SGVsbG8=`
   (auto-process on). Typing must move the caret; the field must stay visible above the keyboard.
2. Tap the tool card → search `baud` → pick Baudot / ITA2 → input `HELLO` → `10100 00001 10010 10010 11000`.
3. Switch to *Baudot to Text*, paste the groups back → `HELLO`.
4. Press **Swap** (labelled *Swap & Decode*) → the output moves into the input, the direction flips
   and the text is decoded in the same tap: `SGVsbG8=` → `Hello`.
5. Pick AES-GCM → type a sentence, set a password, Encrypt → press Swap → Decrypt → original text.
6. Change one character in the payload → Decrypt → friendly "Unable to decrypt…" message.
7. Rotate to landscape, open the keyboard, and set the system font size to largest — the layout
   should stay scrollable with every button reachable.
8. Settings → AMOLED → background is pure black; Light → light background; System → follows the OS.
9. Star two tools, reopen the picker → they appear under *Favourites*.
10. Airplane mode on → repeat any step → everything still works.

## Round 6 fixes (version 1.4.2)

| [x] | **Reported bug fixed:** the AES Key size setting was ignored on decryption, so an AES-128 message decrypted with the AES-256 setting (and vice versa). The size is now recorded in the payload's `kdfId` (`0x02` = 256-bit) and enforced: a mismatch is refused with a message that names the size the message needs |
| [x] | Legacy payloads (`kdfId = 0x01`, written by 1.3.0–1.4.1) still decrypt, and report which size they actually need so the right setting can be selected — no existing data is stranded |
| [x] | Wrong-size keys are named by *AES-GCM with your own key* and *RSA-OAEP + AES-GCM* too, instead of a generic failure |
| [x] | **Audit found real bugs, all fixed:** `nato` crashed with an `ArrayIndexOutOfBoundsException` on any non-ASCII letter or digit (`ü`, `न`, `٣` — it indexed a 26-entry table with `Char.isLetter`); sixteen tools were not findable by their own id in search (`utf16`, `railfence`, `aescbc`, …); non-ASCII letters in a cipher key were silently used as bogus A–Z shifts (Vigenère, Beaufort, Autokey, Playfair, ADFGX, custom alphabets) |
| [x] | Documentation drift fixed: the reference and README now use the registry's exact tool names (`Hash (one-way)`, `Leetspeak (1337)`) — a new test asserts every tool appears in `docs/TOOLS.md` and `README.md` |
| [x] | New `ToolAuditTest` sweeps all 73 tools (parameters, defaults, sensitive flags, ten unusual inputs × both directions, searchability, claim discipline, error friendliness, key-size enforcement); `ToolAuditReportTest` writes `core/build/tool-audit.txt` with a per-tool report |
| [x] | `:core:test` → **260 tests, 0 failures** |
| [x] | Release APK `apk/TextHub-1.4.2-release.apk` signed with the same key; `aapt2 dump permissions` shows no `INTERNET` |

## Round 5 change (version 1.4.1)

| [x] | The six dedicated AES tools (AES-128/192/256-GCM and AES-128/192/256-CBC + HMAC) were removed: the key size is already a **Key size** setting on *AES-GCM Encryption* and *AES-CBC + HMAC*, and both detect it again on decrypt. Fewer, clearer tools — same payload format, no payload becomes unreadable |
| [x] | Registry down to **73 tools**, `:core:test` still **246 tests, 0 failures**; a new test asserts the AES ids are exactly `aes`, `aescbc`, `aesctr`, `aesrawkey` and that 128/192/256 are offered as key-size choices on each of them |
| [x] | Unused single-key-size parameters removed from `AesGcmPayload`, `AesCbcHmac` and `AesCtrHmac`, so no dead capability is left behind |

## Round 4 checks (version 1.4.0)

| [x] | `:core:test` → **246 tests, 0 failures** (round 1: 148, round 2: 185, round 3: 231) |
| [x] | AES key size is a setting on *AES-GCM Encryption* and *AES-CBC + HMAC*: round trip at 128/192/256, a payload written at one size decrypts with any setting, and a registry test proves there is exactly one AES tool per mode |
| [x] | AES-CTR + HMAC: round trip at 128/192/256, wrong password fails, one flipped byte fails the tag, two runs of the same text differ (random counter block) |
| [x] | Raw-key AES-GCM: Base64 and hex keys agree, wrong key value fails, wrong key length reports a key problem, payload header carries the key *length* only |
| [x] | RSA-OAEP hybrid: 2048-bit pair generated on device encrypts and decrypts (including ~2 kB of text), a different private key fails, tampering fails, PEM/PKCS#1 mistakes produce specific messages |
| [x] | Key generator: output contains both PEM blocks, the pair works with the RSA tool, two runs differ, the class is `KEY_MATERIAL` (not secure) and needs no input text |
| [x] | Only the twelve real encryption tools claim the secure flag (test-enforced); every one of them requires a sensitive password or key parameter |
| [x] | Accent colour: eight options in Settings, only primary/secondary roles are recoloured, neutrals (including AMOLED black) are untouched, the choice persists across restarts |
| [x] | Long PEM keys are edited in a multi-line monospace field instead of one endless line |
| [x] | Launcher icon replaced: adaptive vector foreground/background plus a monochrome layer for themed icons, and regenerated PNGs for API 24–25 in all five densities |
| [x] | About screen shows Developer: **Catzilla** |
| [x] | Release APK `apk/TextHub-1.4.0-release.apk` (9,523,848 B, md5 `4a1bd7b3ec8744c07df3293b8d8c5ac1`) — `apksigner verify` passes, `aapt2 dump permissions` shows no `INTERNET` |
| [x] | Debug APK `apk/TextHub-1.4.0-debug.apk` (14,300,004 B, md5 `1aa6e3caf59603c708fc01e86fd4fcad`); both report `versionName 1.4.0`, `versionCode 4`, `minSdk 24`, `targetSdk 34` |
| [ ] | On-device pass (no emulator in this environment): open the tool picker, switch accents, generate a key pair and check the icon on the home screen |

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

## Known limitations

* R8 shrinking is disabled by default in this workspace because the sandbox has 2 GB of RAM;
  enable it with `-PenableR8=true` (see README). The ProGuard rules are already in place.
* The release APK is signed with the bundled demo keystore — replace it before publishing.
* No on-device screenshot pass could be produced in this environment; the checklist above is the
  script for that final review.
