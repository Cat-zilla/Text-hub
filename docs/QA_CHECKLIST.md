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
| [x] | APK produced | `apk/TextHub-1.6.0-release.apk` (9,608,348 B, signed) and `apk/TextHub-1.6.0-debug.apk` (14,586,225 B); superseded builds are removed from `apk/` |
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
| [x] | Release APK (`apk/TextHub-<version>-release.apk`) signed with the same key; `aapt2 dump permissions` shows no `INTERNET` |

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

---

## Round 7 - final fix, compatibility and QA (1.5.0)

Automated (all in `:core:test`, 317 tests):

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

### Follow-up: 1.5.1 drag check (device)

| # | Step | Expected |
| --- | --- | --- |
| 1 | Long-press a favourite and hold still | The row lifts (raised, slightly larger) with a haptic tick; nothing else moves |
| 2 | Drag it slowly up and down without leaving the row | The row follows the finger smoothly; the highlighted slot changes only after about a full row of travel; there is no jumping |
| 3 | Drag it to the bottom edge and hold | The list scrolls slowly while the row stays under the finger |
| 4 | Release | The row settles into the highlighted slot once, and the new order survives a restart |
| 5 | Type something in the picker search while on Favourites | The drag handle disappears and rows cannot be dragged until the search is cleared |

---

## Round 10 - Universal Decoder, tool ordering, favourite drag, release split (1.6.0)

Automated (`:core:test` 360 + `:app:testDebugUnitTest` 32 = **392 tests, 0 failures** at 1.6.0; **379 + 39 = 418** at 1.6.1):

| Check | Test |
| --- | --- |
| The Universal Decoder is a normal registered tool and is first in every ordering | `UniversalDecoderTest.theUniversalDecoderIsANormalRegisteredTool`, `itIsFirstInEveryOrderingThatMatters` |
| Its first position survives favouriting, reordering, un-favouriting and clearing temporary data | `UniversalDecoderTest.theFirstPositionSurvivesFavouritesAndTemporaryData` (also pins `ToolCategory.SMART.ordinal == 0`) |
| It performs no cryptography of its own and dispatches to registered tools only | `UniversalDecoderTest.itNeverPerformsCryptographyOfItsOwn` |
| Every deterministic format decodes through its own tool | `UniversalDecoderTest.everyDeterministicEncodingDecodesThroughItsOwnTool`, `base64IsRecognisedWithItsReason`, `base64UrlIsDistinguishedFromStandardBase64`, `hexadecimalIsDecodedAndDigestsAreNot`, `punycodeLabelsWithTheDnsPrefixAreDetectedAndDecoded` |
| All Text Hub envelopes (AES-GCM, CBC+HMAC, ChaCha20, CTR+HMAC, raw key, RSA, OpenSSL, JWE) and the secret each one needs | `everyTextHubEnvelopeIsIdentifiedWithItsOwnTool`, `rawKeyAndRsaPayloadsAskForTheRightKindOfSecret`, `openSslFilesAreIdentifiedAndOpenedWithTheExistingTool`, `jweTokensAreIdentifiedAndOpened`, `keyMaterialIsRecognisedAsKeyMaterial` |
| A wrong password, a truncated payload, a bad envelope, a wrong key size and an auth failure never produce output | `aWrongPasswordFailsLoudlyAndNeverReturnsGarbage`, `aTruncatedEnvelopeFailsInsteadOfReturningGarbage`, `malformedPayloadsFailWithAFriendlySentence`, `theKeySizeInThePayloadIsEnforcedNotAskedFor`, `parametersOfTheTargetToolAreStillEnforced` |
| Digests are one-way and can only be compared | `aSha256ShapedValueIsACandidateAndNotACertainty`, `everyDigestLengthIsRecognisedAsOneWay`, `aHashCanBeCheckedAgainstCandidateTextButNeverDecrypted` |
| Ambiguity is offered as candidates, never decoded as a guess | `severalCandidatesAreOfferedInsteadOfAGuess`, `unsupportedJweAlgorithmsAreNamedNotGuessed` |
| Nested layers, depth limit, cycle protection, analyse-again | `nestedEncodingsAreUnwrappedInOrder`, `anEncryptedInnerLayerIsOpenedWhenTheSecretIsAvailable`, `nestingStopsAtTheDepthLimit`, `aSelfReferentialEncodingCannotLoopForever`, `analysingTheResultAgainUnwrapsTheNextLayer` |
| The manual override forces the chosen tool, is refused inline when unknown, and never traps the user | `aManualOverrideForcesTheChosenToolInsteadOfTheDetectedOne`, `anUnknownOverrideIsRefusedInlineAndIgnoredByTheAnalysis` |
| Empty, random, malformed and large inputs are safe; large input is analysed off the UI thread | `emptyAndUnrecognisableInputsAreExplainedNotGuessed`, `largeInputIsHandledWithoutBlowingUp` |
| No secret, password or plaintext ever appears in a diagnosis or a report | `secretsNeverAppearInTheDiagnosisText` |
| A drag stores exactly the position the user released on (A B C D E, every row to every position) | `DragReorderTest.everyRowOfFiveCanBeMovedToEveryOtherPosition`, `theDisplayedOrderAfterEveryDropIsTheStoredOrder` |
| A drag on a filtered favourites list moves the row the user grabbed, never another | `DragReorderTest.aFilteredFavouritesListStillMovesTheRowTheUserGrabbed`, `aDragInsideAFilteredListNeverLosesAFavourite`, `PrefsModelTest.aDragIsStoredByIDSoFilteredFavouritesCannotShiftIt` |
| Rows slide exactly one row aside; the row follows the finger and settles without a permanent offset | `DragReorderTest.theRowsBetweenTheDragAndTheTargetSlideOneRowAside`, `everyShiftedRowMovesExactlyOnceRegardlessOfTheDistance`, `theRowIsDrawnWhereTheFingerIsUntilItSettles`, `theLeftoverDistanceIsOnlyTheFractionInsideTheSlot` |
| Rapid consecutive drags, cancelled drags, single-row lists and clamping | `twoQuickDragsInARowBothLand`, `aGestureThatIsNotADragChangesNothing`, `aDragOnAOneRowListDoesNothing`, `aReleasePastTheEndsIsClampedToTheList` |
| The stored order is what a fresh read returns, and clearing temporary data keeps it | `PrefsModelTest.theSameDragThroughTheStoreKeepsFavouritesAndWritesTheOrder` |
| The public archive has no signing material | `tools/make_release_archives.py` (name, suffix and content scan), run in this round |
| Only a dispatcher may leave its secret optional | `ControlRulesTest.onlyADispatcherMayLeaveItsSecretOptional` |

Manual (no emulator in this environment):

| # | Step | Expected |
| --- | --- | --- |
| 1 | Launch the app | The tool list opens on **Universal Decoder**, top of the list, category *Smart Tools* |
| 2 | Restart, search "base64", visit other categories, favourite a tool, clear temporary data | The Universal Decoder is still first and cannot be dragged in the main list |
| 3 | Favourite the Universal Decoder, reorder it in Favourites | The favourites order changes; its main-list position does not |
| 4 | Paste `SGVsbG8sIFdvcmxkIQ==` | Analysis card: "Decoded with Base64", High confidence, one layer, result `Hello, World!` |
| 5 | Paste a 64-character hex value | Possible SHA-256, nothing decoded, candidates listed; typing the same text in *Text to compare* reports a match |
| 6 | Encrypt "hello" with the AES-GCM tool, paste the payload into the decoder | "Recognised, and it is encrypted. Enter the password to open it" — the tool does not try anything |
| 7 | Enter the wrong password, then the right one | "Decryption failed / Authentication check failed", then the plaintext |
| 8 | Tap the *Detected automatically* row and pick AES-GCM by hand | The analysis uses the forced tool; the ✕ next to it returns to automatic detection |
| 9 | Paste Base64 of Base64 of "hello", tap *Analyse result again* | The chain shows both layers; the second pass has nothing left to unwrap |
| 10 | Drag a favourite through the whole list in one gesture, release at the very bottom | The row follows the finger, the rows in between slide aside, and the row settles exactly where it was released |
| 11 | Drag and release, then immediately drag another row | The second drag starts clean: no leftover offset, no jump |
| 12 | Cancel a drag (keep the finger still and lift after a scroll gesture) | The order is unchanged |
| 13 | Search inside Favourites | The drag handle disappears; rows cannot be dragged until the search is cleared |

---

## Round 11 - detection matrix and the end of the "cipher settings" message (1.6.1)

Automated (**379** `:core:test` + **39** `:app:testDebugUnitTest` = **418 tests, 0 failures**):

| Check | Test |
| --- | --- |
| The exact reported bug is gone: an encoding is decoded, and nothing in that path can mention cipher settings | `UniversalDecoderDispatchTest.encodingThenAnalysingThenDecodingRoundTripsThroughTheRegisteredTool`, `aDecodedEncodingNeverMentionsCipherSettingsOrPasswords`, `noPathThroughTheDecoderEverReportsCipherSettingsAsTheProblem` |
| An encoding candidate never requires a secret and never needs cipher parameters (derived from the registry, not from the detection rule) | `aDetectedEncodingNeverRequiresASecretAndNeverValidatesCipherSettings`, `anEncodingShapedInputIsNeverAskedForAPassword`, `everyCandidateTheDetectorOffersNamesARegisteredTool` |
| Every deterministic format decodes through its own registered tool, driven through the production path (registered tool -> registry -> processing engine) | `everyDetectedEncodingDecodesThroughItsOwnRegisteredTool`, `UniversalDecoderTest.everyDeterministicEncodingDecodesThroughItsOwnTool` |
| Every one of the 74 registered tools is accounted for by detection - hinted, generic, symmetric-transform, keyed or one-way - and a new deterministic tool cannot fall outside it | `everyRegisteredToolIsAccountedForByTheDetector`, `theMatrixCoversWhatTheDetectorClaimsToCover` |
| A candidate can only ever name a registered tool (an unregistered id throws where it is built, not in the user's hands) | `aCandidateCanOnlyNameARegisteredTool` |
| The dispatcher itself is never a candidate, so it cannot recurse | `theDetectorsOwnToolIsNeverOfferedAsACandidate` |
| Every tool that needs key material declares a real, sensitive secret parameter | `everyToolThatTakesASecretDeclaresItAsAnActualParameter` |
| The manual override dispatches to the chosen tool and never reports a cipher problem, whatever the input | `aManualOverrideDispatchesToThatToolAndNeverBlamesTheCipherSettings`, `anUnknownManualOverrideIsIgnoredInsteadOfBreakingTheAnalysis` |
| The manual override is never written to disk, and neither is a secret or an empty value | `theManualOverrideIsNeverRememberedForTheNextSession` |
| No registered tool answers an awkward parameter with an unexplained failure (parameter torture over every encoding, transformation and classical cipher) | `everyRegisteredToolTakesAwkwardParametersWithoutAnUnexplainedFailure` |
| The decoder itself never fails with an unexpected error over the whole input corpus | `theDecoderItselfNeverFailsWithAnUnexpectedError` |
| Favourites drag polish: same auto-scroll speed at 60 Hz and 120 Hz, capped catch-up after a stalled frame, no movement before 60% of a row, a fast drag landing under the finger, no flicker while hovering, and the offset absorbing exactly what was scrolled | `DragReorderTest.theAutoScrollMovesAtTheSameSpeedOnEveryFrameRate`, `aStalledFrameCannotShootTheListAwayFromTheFinger`, `aSlowDragDoesNotMoveAnythingUntilTheThresholdIsCrossed`, `aFastDragLandsOnTheRowTheFingerIsOver`, `hoveringOnTheBoundaryDoesNotFlicker`, `scrollingWhileDraggingKeepsTheRowUnderTheFinger` |
| The favourites algorithm itself is untouched and still correct (every row to every position, filtered lists, cancellation, restart persistence) | the whole of `DragReorderTest` and `PrefsModelTest` |

Manual (no emulator in this environment):

| # | Step | Expected |
| --- | --- | --- |
| 1 | Paste `SGVsbG8gVGV4dEh1Yg==` into the Universal Decoder | It decodes to `Hello TextHub`. No mention of cipher settings, passwords or keys anywhere on the card |
| 2 | Type anything into *Password / key* and analyse the same Base64 again | Identical result - a password cannot change a decoding |
| 3 | Force **AES-GCM Encryption** with the *Detected automatically* row, then paste Base64 and analyse | The card asks for the password (or reports the AES error) - never "these cipher settings are not valid" for an encoding, and the forced tool is named |
| 4 | Close and reopen the app, paste plain Base64 | The override did not survive: detection is automatic again |
| 5 | Paste a 32-character hex value, then Base58 text, then Morse | Each is decoded by its own tool; the report names the tool and the reason |
| 6 | Paste a SHA-256 digest | "Detected: one-way hash", candidates only, no decode, *Text to compare* checks it |
| 7 | Paste random text | "No supported format recognised" - never a guess |
| 8 | Drag a favourite quickly up and down, hold it at the bottom edge, then release | The row stays under the finger, the list scrolls at a steady speed, the rows slide aside once each, and the row settles where it was released with no flicker or permanent offset |
| 9 | Drag on a 120 Hz device (if available) | The edge scroll moves at the same speed as on a 60 Hz device |

---

## Round 12 — 1.6.2: Universal Decoder crash safety + downward drag placement

Automated (`:core:test` 390 + `:app:testDebugUnitTest` 57 = 447, 0 failures):

| Check | Test |
| --- | --- |
| Typing `a`, `abc`, `hello`, `123`, empty, whitespace, punctuation, arbitrary Unicode, newlines, 100 KB lines, malformed Base64/Punycode/Enigma/JWT/OpenSSL/PEM, prose — through the exact app path, nothing throws, no cipher-settings wording, no stack traces | `UniversalDecoderCrashSafetyTest.typingAnythingNeverThrowsAndNeverCrashWordings`, `typingProgressivelyNeverThrows`, `repeatedTypingAndDeletingNeverThrows`, `analysingWithASecretTypedNeverThrows`, `detectionSurvivesEveryInputShape` |
| A `RuntimeException`, `StackOverflowError` or `OutOfMemoryError` from a registered tool becomes the friendly error and cannot kill the process; `ToolException` keeps its own message | `aRuntimeExceptionFromAToolBecomesAFriendlyError`, `aStackOverflowErrorFromAToolDoesNotKillTheProcess`, `anOutOfMemoryErrorFromAToolDoesNotKillTheProcess`, `aToolExceptionStillKeepsItsOwnMessage` |
| One broken detection candidate costs only itself; other candidates are still evaluated | `oneBrokenCandidateCostsOnlyItself` |
| Drop placement: release between C/D → `A C B D E`, between D/E → `A C D B E`; one-position and multi-position downward drags; centre/boundary (60 % dead zone); intentional bottom drops; upward drags; adjacent swaps; first/last row; 40-row lists; auto-scroll-active drops; unknown rows | the whole of `DragDropPlacementTest` (18 tests) |
| The stale-step failure mode (the 4 dp gap mistaken for the row step) must clamp to the end — pinned so it cannot return | `DragDropPlacementTest.theStepIncludesTheGapNotTheGapAlone` |
| A scrolled list must read the dragged row's true on-screen position (mid-viewport, not "past the bottom edge") | `aScrolledListNoLongerLooksPastTheBottomEdge`, `aRowVisibleAfterScrollReportsItsTrueViewportPosition`, `autoScrollWhileDraggingStillLandsWhereTheFingerEndedUp` |

Manual (no emulator in this environment — same caveat as rounds 10–11):

| # | Step | Expected |
| --- | --- | --- |
| 1 | Open the Universal Decoder and type `a`, delete it, type `abc`, `hello`, `123`, random punctuation, emoji | No crash, ever. Ordinary words show "No supported format recognised"; nothing ever mentions cipher settings |
| 2 | Paste `SGVsbG8gVGV4dEh1Yg==`, then malformed Base64 (`SGVsbG8`, `====`), then a 500 KB paste | Decoded / friendly error / no crash and no hang |
| 3 | Star 10–15 tools, open the picker's favourites list, drag a row **down** one slot and release between two rows | The row is stored exactly where released — never at the bottom unless released in the bottom region |
| 4 | Repeat step 3 after scrolling the favourites list, and at the top, middle and bottom of the list | Same placement everywhere; the list only auto-scrolls when the row is genuinely near the edge |
| 5 | Drag a row upward one and several slots, and to the very top | Stored where released; only a genuine top-region release moves it to the top |
| 6 | Drag quickly with a stalled frame (scroll fast, then drag) | Feel unchanged from 1.6.1: row follows the finger, no jump to the ends |

---

## Round 13 — 1.6.3: haptics, restore-defaults, decoder states, accessibility

Automated (450 total, 0 failures):

| Check | Test |
| --- | --- |
| Restore defaults keeps exactly the favourites and their order, resets everything else | `PrefsModelTest.restoringDefaultsKeepsExactlyTheFavouritesAndNothingElse` |
| Restore defaults is safe on an empty/partial store | `restoringDefaultsOnAnEmptyStoreChangesNothing` |
| Haptics are an appearance-class preference (kept by "clear temporary data", reset by "restore defaults", default on) | `theHapticPreferenceIsKeptWhenClearingAndResetWhenRestoring` |
| The refined "nothing detected" wording passes the full decoder suite unchanged | whole `:core:test` run |

Manual (no emulator in this environment):

| # | Step | Expected |
| --- | --- | --- |
| 1 | Settings → Interaction → toggle Haptic feedback off, then copy a result and toggle a favourite star | No vibration at all; toggle back on and both give a light tick |
| 2 | System sound/haptics off, haptics on in the app | Still no vibration (the system setting wins) |
| 3 | Settings → Restore defaults, read the dialog, confirm | Theme returns to System, accent to Teal, switches to defaults, recents/tool-settings cleared, snackbar shown; favourites and their order unchanged; text on screen untouched |
| 4 | Open the Universal Decoder with an empty input | Two quiet hint lines with example formats; they disappear while typing |
| 5 | Type `hello` | "No supported format was recognised" with the new explanation; no error styling |
| 6 | TalkBack: swipe through Settings | Each switch reads as one control ("…, on/off") with a full-row toggle target |
| 7 | TalkBack: Encode/Decode segments | The selected segment is announced as selected |
| 8 | TalkBack: a number parameter | Steppers announce localized "Decrease"/"Increase" |
| 9 | Copy / Paste / Clear pills | Same look; slightly larger touch area |

---

## Round 14 — 1.6.4: RSA Key Pair Generator output/state fix

Automated (467 total, 0 failures — `RsaKeyPairGeneratorTest`, 17 tests):

| Check | Test |
| --- | --- |
| The generator may only run through its own action; every other tool stays automatic | `theGeneratorMayOnlyRunThroughItsOwnAction`, `everyOtherToolKeepsItsAutomaticBehaviour` |
| Generated PEM is structurally valid (X.509 / PKCS#8) and the halves are a matching pair | `aGeneratedPairIsStructurallyValidPem`, `theHalvesAreAMatchingPair` |
| Reported size matches the generated modulus; fingerprint is deterministic, public-only | `theReportedKeySizeMatchesTheGeneratedKey`, `theFingerprintIsDeterministicAndCoversOnlyThePublicKey` |
| Second generation mints a new pair; repeated generation is safe; encryption round-trip still works | `anExplicitSecondGenerationCreatesANewPair`, `repeatedGenerationIsSafe`, `thePairStillWorksWithTheRsaEncryptionTool` |
| The processor output parses into the three sections; format/parse round-trip is lossless | `theProcessorOutputParsesIntoTheThreeSections`, `theFormatAndParseRoundTripIsLossless` |
| Each Copy carries only its own half; the parser refuses everything that is not generator output | `eachCopyCarriesOnlyItsOwnHalf`, `theParserRefusesAnythingThatIsNotGeneratorOutput` |
| Failures arrive as recoverable errors; the 1.6.2 engine boundary holds; no key material in error strings | `anUnsupportedSizeIsARecoverableToolError`, `theEnginePathStillConvertsUnexpectedFailuresSafely`, `errorMessagesNeverCarryKeyMaterial` |

Manual (no emulator in this environment — not performed here):

| # | Step | Expected |
| --- | --- | --- |
| 1 | Open the RSA Key Pair Generator | Empty state: "Generate an RSA key pair to get started." — **no key pair appears by itself** |
| 2 | Change the key size, toggle settings, leave and return | Still no automatic generation; nothing regenerates behind your back |
| 3 | Press Generate key pair | Progress shows; then Key information (size + fingerprint), Public key and Private key in separate cards; no generic output box anywhere |
| 4 | Copy public key / Copy private key (TalkBack if available) | Each copies exactly its half (paste to check); described as "Copy public key" / "Copy private key"; confirmation snackbar + light haptic per the 1.6.3 preferences |
| 5 | Press Generate again | A new pair replaces the old one immediately (new fingerprint), no extra dialog |
| 6 | Generate a 4096-bit pair | Progress bar stays visible; UI never freezes |
| 7 | Rotate the screen / use other tools and come back | Pair stays during recomposition; switching tools clears it (never persists) |
| 8 | Confirm: no private-key text in any content description, snackbar or the fingerprint card | Only the fingerprint (public) is shown outside the private card |

---

## Round 15 — 1.6.5: RSA active-pair persistence + named encrypted key collection

Automated (488 total, 0 failures — `RsaKeyVaultTest`, 21 tests):

| Check | Test |
| --- | --- |
| Blank names refused; names trimmed; save/load returns exactly the same pair; fingerprint survives | `aBlankNameIsRefused`, `aNameIsTrimmedBeforeItIsStored`, `saveThenLoadReturnsExactlyTheSamePair`, `theFingerprintSurvivesSaveAndLoadUnchanged` |
| Multiple independent pairs; duplicates never silently overwritten; explicit replace; identical fingerprints = same key | `multiplePairsAreStoredIndependently`, `aDuplicateNameIsNeverSilentlyOverwritten`, `anExplicitReplaceReplacesTheRecord`, `anIdenticalFingerprintIsReportedAsTheSameKey` |
| Delete removes only that record and its persisted sealed bytes | `deletingOnePairLeavesTheOthersUntouched`, `deletionRemovesThePersistedPrivateMaterial` |
| Saved pairs survive a restart (new collection, same storage + same keystore key) | `savedPairsSurviveANewCollectionOverTheSameStorage` |
| Sealed at rest: storage carries neither PEM, only metadata | `storedBlobsDoNotCarryPlaintextPem` |
| Damaged store degrades safely | `aDamagedStoreDegradesToAnEmptyCollectionInsteadOfCrashing` |
| Session: start empty; generate → unsaved; regenerate replaces active only; save links; clear removes; load replaces; deleting the active's record unlinks but keeps the pair | the `RsaKeySession` tests |
| 1.6.4 guarantees intact: explicit-action-only, matching pairs, parse, copy-half isolation | whole `RsaKeyPairGeneratorTest` run |

Manual (no emulator in this environment — not performed here):

| # | Step | Expected |
| --- | --- | --- |
| 1 | Generate a pair, switch to Base64, switch back | The same pair is still displayed (same fingerprint) |
| 2 | Change the key size setting, open Settings and back, rotate | Pair unchanged; nothing regenerates |
| 3 | Save key pair → name it → save again with the same name | Replace question; original stays until confirmed |
| 4 | Save the same pair under a different name | "This key is already saved as …" |
| 5 | Generate again → the saved list still shows the old pair; save the new pair under another name | Two independent records |
| 6 | Load each record with an unsaved active pair | Replace question first; after loading, sections show exactly that record's keys |
| 7 | Clear (with active pair) | Confirmation; empty state; saved list untouched |
| 8 | Delete a saved pair | Confirmation; record gone; the other record survives; deleting the active's record keeps the pair on screen, now unsaved |
| 9 | Kill and reopen the app | Saved list intact; active unsaved pair gone (by design); empty state until Generate/Load |
| 10 | Restore defaults | Saved key pairs survive (dialog says so) |

## Round 16 — 1.6.6: Universal Decoder — complete RSA keys + saved-key integration

Automated (`UniversalDecoderRsaKeyTest`, 12 tests; `AnalysisKeyStateTest`, 7 tests):

| Check | Test |
| --- | --- |
| A complete multi-line private PEM opens an RSA payload through the registered tool + engine | `aCompleteMultiLinePrivatePemOpensAnRsaPayloadThroughTheDecoder` |
| A 4096-bit PEM (several kB) is not truncated anywhere in the parameter/validation/processing path | `a4096BitPrivatePemIsNotTruncatedAnywhereOnTheWay` |
| CRLF / padded / single-line / space-joined / surrounded PEMs reach the RSA processor unchanged and open | `theKeyReachesTheRsaProcessorUnchanged`, `everyRsaFormatTheRsaToolAcceptsIsAcceptedByTheDecoder` |
| A public PEM is accepted as key material and refused with the RSA tool's own sentence | `aPublicPemIsAcceptedAsKeyMaterialAndAnsweredHonestly` |
| Wrong / malformed / incomplete / PKCS#1 / non-key input → recoverable failure, no cipher-settings wording, no key echo | `anInvalidOrMismatchedRsaKeyIsARecoverableFailureWithoutKeyMaterial` |
| Passwords, hex and Base64 raw keys unchanged | `passwordsAndSymmetricKeysStillWorkExactlyAsBefore` |
| The secret parameter stays a sensitive PASSWORD parameter with no validator (no per-keystroke parsing) and is never remembered | `theSecretParameterKeepsItsPasswordKindAndNoValidatorParsesOnEveryKeystroke` |
| RSA detected and "RSA private key" named before any key exists; kind follows direction | `rsaIsDetectedAndTheRequiredKeyIsNamedBeforeAnyKeyExists`, `theDiagnosisKeepsSayingRsaKeyAfterAFailedAttemptAndAfterSuccess` |
| The decoder never decrypts a vault record, never writes to the vault, never tries other saved keys | `theDecoderNeverTriesSavedKeysAndNeverSavesAPastedOne` |
| No key body in reports, errors or reasons | `keyMaterialNeverAppearsInReportsOrErrors` |
| Key stored verbatim; Clear keeps ciphertext + analysis; saved key = exactly that key, labelled; editing → manual; editor follows detected kind | `AnalysisKeyStateTest` (all) |

Manual (no emulator in this environment — not performed here):

| # | Step | Expected |
| --- | --- | --- |
| 1 | Paste an RSA-OAEP + AES-GCM payload into the Universal Decoder | "Recognised, and it is encrypted. Enter the private key to open it."; the key field is the multi-line *RSA private key* editor with *Use saved key* / *Clear* |
| 2 | Long-press → Paste a complete 2048- and 4096-bit private PEM | Whole block visible and scrollable, line breaks kept; decrypts after the debounce; status "Using manually entered key" |
| 3 | Tap Clear | Key gone, ciphertext and analysis card stay, saved list untouched |
| 4 | Tap Use saved key → pick a record | Only name / size / fingerprint / date shown; the payload decrypts; status "Using saved key: <name>" |
| 5 | Pick another saved key | Field and status switch; only the chosen key is used (wrong key → RSA failure message) |
| 6 | Edit the loaded key by one character | Status becomes "Using manually entered key"; the saved record is unchanged in the generator's list |
| 7 | Settings → Restore Defaults | Key field emptied; saved key pairs intact |
| 8 | Kill and reopen the app | Key field empty (never persisted); vault intact |
| 9 | AES-GCM password payload, raw-key payload | Ordinary single-line password field, as before |

## Round 17 — 1.6.7: Settings & UX polish

Automated (`UiSettingsTest`, 14 tests; `SensitiveFieldClearingTest`, 6 tests; `RsaKeyVaultTest` +2):

| Check | Test |
| --- | --- |
| Every new setting has the documented default; an empty store reads as the defaults; unknown stored values fall back | `defaultsMatchTheSettingsSpecification`, `anEmptyStoreReadsAsTheDefaults`, `unknownStoredValuesFallBackToTheDefaults` |
| Every setting survives a round trip; writing them never touches favourites, theme or remembered params | `everySettingSurvivesARoundTrip`, `writingSettingsLeavesOtherPreferencesAlone` |
| Stored values are only `true`/`false`/enum ids — never text or secrets | `storedValuesContainNoTextOrSecretShapedData` |
| Restore app preferences resets every setting and keeps exactly the favourites | `restoringDefaultsResetsEverySettingAndKeepsFavourites` |
| Reset remembered tool settings keeps every setting | `clearingTemporaryDataKeepsEverySetting` |
| Reset favourites removes only favourites (+ legacy key); no-op when empty | `resettingFavouritesRemovesOnlyFavourites`, `resettingFavouritesWhenThereAreNoneIsANoOp` |
| Large text / Reduce animations are one source of truth with Text size / UI animation | `reduceAnimationsIsAViewOfTheAnimationMode`, `animationModesScaleDurationsAsDocumented` |
| Clear sensitive fields resets only sensitive params; unknown keys untouched; idempotent; no secret survives in any tool | `SensitiveFieldClearingTest` (all) |
| Every PASSWORD parameter in the registry is declared sensitive | `everyPasswordParameterInTheRegistryIsSensitive` |
| Clear saved RSA keys empties and persists the collection, reports the count, safe when empty | `deleteAllRemovesEveryRecordAndReportsTheCount`, `deleteAllOnAnEmptyCollectionIsSafe` |

Manual (no emulator in this environment — not performed here):

| # | Step | Expected |
| --- | --- | --- |
| 1 | Settings | Six cards in order: Appearance, Security & privacy, Accessibility, Data & reset, Advanced, About |
| 2 | Appearance → Dynamic colour on (Android 12+) | Palette follows wallpaper; accent swatches still selectable; off again → previous accent returns exactly |
| 3 | Dynamic colour on an Android 11 device | Row disabled with the explanation; accent works as before |
| 4 | Layout density → Compact | Card padding/gaps shrink; Copy/Paste/Clear and switches still ≥48dp |
| 5 | Text size → Large; then Accessibility → Large text | Both reflect the same state; system font scale still stacks on top |
| 6 | UI animation → Off; Accessibility → Reduce animations | Switch shows on; segmented control and favourites row-shift snap; processing bar still shows; drag/reorder still works; Reduce off → Full |
| 7 | Show tool icons off | Monogram gone from the header and the picker list; TalkBack still reads the tool name |
| 8 | Monospace output off | Result box proportional; input unchanged |
| 9 | Security → Clear on leaving tool OFF, type an AES password, switch tool and back | Password still there (session); kill app → gone. Default ON → empty after switching |
| 10 | Clear on background ON, type a password, press Home, return | Password field empty; input text, output, generated RSA pair and saved keys untouched |
| 11 | RSA key generator | Private key shows the hidden placeholder; Reveal shows the PEM; Copy asks first; with both settings off the 1.6.6 behaviour returns |
| 12 | Sensitive-data warnings off | The red "Never share it" line under the private key disappears; nothing else changes |
| 13 | Accessibility → High-contrast | Stronger outlines/selection; theme and accent unchanged. Icon labels → Share/Clear become text. Larger targets → taller actions |
| 14 | Data & reset → each action | Dialog lists removed/kept; Cancel does nothing; confirming with nothing to remove shows the message and no error |
| 15 | Restore app preferences with saved RSA keys present | Every setting back to default, favourites and order kept, saved keys intact |
| 16 | Clear saved RSA keys | One very explicit dialog with a red *Delete keys* button; the active pair on screen stays; snackbar reports the count |
| 17 | Clear everything | Two dialogs; afterwards: defaults, no favourites, no saved keys, empty screen |
| 18 | Advanced toggles | "Processed in N ms" under output; tool id beside the classification chip; candidate rows show `id · confidence · direction`; parameter summary under parameters. Nothing shows a stack trace or a secret |
| 19 | About | App, version 1.6.7, version code 16, build, tool count, local-only statement |

## Round 18 — 1.6.8: Settings simplification (information architecture only)

Automated (`SettingsPagesTest`, 11 app tests): root order, depth ≤ 2, every stored key reachable,
shared-key pairs exactly the two documented ones, placement per page, reset grouping/confirmations,
back navigation. All 1.6.7 tests unchanged.

Manual (no emulator here — not performed):

| # | Step | Expected |
| --- | --- | --- |
| 1 | Open Settings | One card with six rows: Appearance, Accessibility, Privacy & security, Data & reset, Advanced, About — each with a one-line summary and a chevron, no switches |
| 2 | Appearance | Theme radio list + Dynamic colour; Accent card; Text size + an *Interface ›* row. Interface page: density, animation, tool icons, monospace |
| 3 | Accessibility | Two groups: *Text & display* (Large text, High contrast, Text labels, Reduce animations) and *Interaction & haptics* (Larger targets, Haptics). Toggle Large text, go to Appearance → Text size shows Large |
| 4 | Privacy & security | *Privacy* (statement + three switches) and *Private keys* (two switches). No storage/crypto wording anywhere |
| 5 | Data & reset | *Reset* group (Restore, Reset remembered tool settings, Reset favourites) then *Delete data* group in red (Clear saved RSA keys, Clear everything). Every action still confirms; Clear everything still asks twice; Restore leaves saved keys intact |
| 6 | Advanced | *Processing* (auto-process, processing time), *Interface* (copy confirmation), *Diagnostics ›*. Diagnostics page: intro sentence, three switches, Reset tool-specific settings |
| 7 | Back | Hardware/gesture back and the arrow go Diagnostics → Advanced → Settings → main; reopening Settings starts at the root |
| 8 | TalkBack | Navigation rows read "title, summary, button"; switch rows read "title, subtitle, on/off, switch" |
| 9 | Large text + Larger touch targets on | No clipped summaries; rows grow, nothing overlaps |
| 10 | Regression | Universal Decoder, RSA generator/vault, favourites drag — unchanged (no code touched) |

## Round 19 — 1.6.9: Settings visual hierarchy (presentation only; every setting and behaviour kept)

Automated (`SettingsPagesTest`, rewritten and extended to 15 app tests): six root destinations in
order; pages are flat (no second-level screens at all — the 1.6.8 Interface and Diagnostics pages
are folded into their parents as groups); no empty page and no empty group; every row sits in a
group of its own page; groups and rows appear in the specified order on every page; every stored
key reachable; shared-key pairs still exactly the two documented ones; private-key controls
separated from general-privacy controls; diagnostics kept at the quiet end of Advanced; danger zone
holds exactly *Clear everything*; reset actions keep order, destructive flags and confirmation
counts (Clear everything = 2); keys/defaults unchanged (saved values re-read identically); every
destination at most two taps from the root; back returns to the root and then out.
All other tests unchanged — 544 total, 0 failures.

Manual (no emulator here — not performed):

| # | Step | Expected |
| --- | --- | --- |
| 1 | Open Settings | Six quiet rows on the plain background (tonal icon tile, title, one-line summary, chevron). No card around the list, no switches |
| 2 | Settings → Appearance | Five groups with small headings — Theme (segmented control), Colour (Accent row with the current swatch and name ›, Dynamic colour switch), Text (Text size — value ›), Interface (Layout density — value ›, two switches), Motion (UI animation — value ›). Scannable at a glance; no cards |
| 3 | Tap Accent | Radio-style swatch dialog; picking applies immediately and the row shows the new dot + name. With dynamic colour on, the row subtitle says the accent is kept |
| 4 | Tap Text size / Layout density / UI animation | A quiet radio dialog opens with the explanation sentence on top; the row always shows the current value |
| 5 | Accessibility | Four one/two-row groups: Text, Motion, Visual, Interaction; the two cross-referenced settings (Large text, Reduce animations) still say so in their subtitles |
| 6 | Privacy & security | One intro sentence, a plain Privacy group (3 switches), and the Private keys group in a quiet tinted container with a lock icon. Nothing else on the page is tinted |
| 7 | Data & reset | Data group (3 neutral rows with state subtitles: count/storage size), Saved data (Clear saved RSA keys in red), Danger zone in a red-tinted container (only Clear everything). No trailing Reset/Clear buttons — the whole row is the action |
| 8 | Every reset action | Same confirmation dialogs as 1.6.8, unchanged text; Clear everything still two steps; Restore leaves favourites and saved keys intact |
| 9 | Advanced | Processing and Feedback groups first, Diagnostics deliberately quiet (muted heading + intro line, 3 switches), Tool configuration last with the same reset as Data & reset |
| 10 | About | App name, "Version 1.6.9 (18)", tool count as a small identity block; Build / Developer / Licences as quiet label–value rows; local-only statement at the bottom |
| 11 | TalkBack | Switch rows read as one switch ("title, subtitle, on/off"); value rows read "title, current value, button"; dialog options are a radio group; nav rows read "title, summary, button" |
| 12 | Large text + Larger touch targets on | Rows grow, nothing clips or overlaps; value rows keep title left and value right |
| 13 | Back | Arrow and system back go straight from any page to the root, then leave Settings; reopening starts at the root |
| 14 | Density → Compact | Group spacing and row padding tighten; switches and rows still ≥48dp |
| 15 | Regression | Theme still applies instantly; dynamic colour, drag/reorder, RSA vault, Universal Decoder — unchanged (no code touched outside the settings UI) |

## Round 20 — 1.7.0: Corner style, new launcher icon, Buy-me-a-coffee support action

Automated (`CornerStyleTest` core = 13, `HubCornersTest` app = 7, `SupportActionTest` app = 7,
`NoNetworkImplementationTest` app = 6, plus the extended `UiSettingsTest` and `SettingsPagesTest`):

- **Corner style** — default Rounded; Rounded / Slightly rounded / Square all selectable; each
  persists as `corner_style`; an unknown id falls back to Rounded; "Restore app preferences"
  resets it to Rounded; "Reset remembered tool settings", "Reset favourites" and "Clear saved RSA
  keys" do not touch it; "Clear everything" follows the existing preference reset. The three radii
  tables (`radiiOf`) make Rounded equal to the previous app, Slightly reduced strictly smaller on
  every token, and Square zero on every non-pill token; `hubShapesFor` feeds `MaterialTheme.shapes`
  so cards/groups/dialogs/rows follow the preference. Corner style is one row on Appearance
  (Interface group) — no new Settings page (destinations stay six), and it appears on no other page.
- **Support action** — exact URL `https://www.buymeacoffee.com/Catzilla0`; https/absolute/no
  tracking parameters; shown on exactly the Settings root and About, and nowhere else; an external
  `ACTION_VIEW` browser intent; no WebView, no network client, no remote image loading, no
  analytics/telemetry, no logging (scanned over the real sources); graceful when no browser exists.
- **No INTERNET permission** — the manifest still removes it; no other permission was added.
- All existing regression tests unchanged (weakened/deleted: none). Totals: 478 core + 104 app =
  582, 0 failures. `lintDebug` 0 errors.

Build: `assembleDebug` + `assembleRelease` green; release APK signed with the unchanged
`texthub-release.jks` (SHA-256 `CC:69:D4:…:82:B1`); package `com.texthub.app`; `versionName 1.7.0`,
`versionCode 19`; 74 tools; no INTERNET permission; no WebView.

Manual (no emulator here — not performed; do these on a device):

| # | Step | Expected |
| --- | --- | --- |
| 1 | Install the 1.7.0 APK over 1.6.9 | Installs as an update; preferences, favourites and saved keys survive |
| 2 | Settings → Appearance → Corner style | Shows a value row "Corner style — Rounded ›" in the Interface group, above Layout density |
| 3 | Tap it | A quiet radio dialog: Rounded / Slightly rounded / Square. Picking applies and closes; the row shows the new value |
| 4 | Main screen with each style | Cards, buttons, input/output fields, chips and the picker sheet visibly change corner radius; Rounded looks exactly like before |
| 5 | Circular controls | Icon buttons, switches, radio buttons, accent swatch and the drag handle stay circular in every style |
| 6 | Theme + accent sweep | The corner language holds across System/Light/Dark/AMOLED, dynamic colour and every accent |
| 7 | Data & reset → Restore app preferences | Corner style returns to Rounded; the other resets leave it alone |
| 8 | Launcher | Home screen, recents and the round icon all show the new icon; nothing is cropped or distorted |
| 9 | Settings root | "☕ Buy me a coffee — Support the development of Text Hub" sits at the bottom, spaced below the six destinations |
| 10 | About | The same support row sits at the bottom, below the local-only statement |
| 11 | Other pages | Appearance, Accessibility, Privacy & security, Data & reset, Advanced, tools, Universal Decoder, picker — none show the support action |
| 12 | Tap the support row | The user's browser opens `https://www.buymeacoffee.com/Catzilla0`; nothing loads inside Text Hub |
| 13 | TalkBack | Corner style choices announce "Rounded / Slightly rounded / Square"; the support row announces "Buy me a coffee. Opens the donation page in your browser." |
| 14 | Large text + larger touch targets | The new rows grow like every other row; nothing clips or overlaps |

## Round 21 — 1.7.0: icon resize (smaller mark)

| Step | Check | Expected result |
| --- | --- | --- |
| 1 | Regenerate icons (`python3 tools/make_app_icon.py`) with `MARK_FRACTION = 0.58` | Foreground mark bbox 195×181 inside the 72 dp safe zone (x 121–315, y 126–306 on the 432 px canvas) |
| 2 | Legacy mipmaps | Mark spans ≈45% of canvas in every density (was ≈52%); even margins (≈53 px each side at xxxhdpi) |
| 3 | Background plate | Unchanged dark-navy gradient top `(17,31,53)` → bottom `(11,21,36)` |
| 4 | Mark colours | Blue `#1070F0`-ish and white present in the packaged foreground layer |
| 5 | Rebuild + verify | `:core:test` 478 / `:app:testDebugUnitTest` 104 pass, lint 0 errors, signed release `com.texthub.app` v1.7.0(19), cert `cc69d4d0…`, no INTERNET |
| 6 | Packaged APK | Every density + adaptive foreground carry the smaller mark |
