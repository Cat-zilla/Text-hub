# Text Hub 1.6.0 — implementation report (round 10)

**Version:** 1.6.0 (versionCode 9) · **Tools:** 74 · **Tests:** 392, all green
(`:core:test` 360 + `:app:testDebugUnitTest` 32) · **Lint:** `:app:lintDebug`, 0 errors ·
**APKs:** release + debug built, both verified with `aapt2` and `apksigner`.

This round added the **Universal Decoder** (a new first tool, category *Smart Tools*), fixed the
favourites drag-and-drop properly, and split the release into a **public source archive with no
signing material** and a **private signing archive**.

---

## 1. Deliverables

| Artefact | Where | Size / hashes |
| --- | --- | --- |
| Signed release APK | `apk/TextHub-1.6.0-release.apk` | 9 608 348 B, md5 `0c0b61e748d80f7986bb38f70a540b76` |
| Debug APK | `apk/TextHub-1.6.0-debug.apk` | 14 586 225 B, md5 `bd67dc0dec3462c76cbc9ba0f1a924d2` |
| Public source archive | `~/TextHub-1.6.0-source.zip` | 158 files (102 Kotlin, 18 test files), both APKs, all docs — no key material (verified three ways) |
| Private signing archive | `~/TextHub-1.6.0-signing-files.zip` | `texthub-release.jks`, `keystore.properties`, `SIGNING.md`, the signing configuration — **never uploaded, never inside the source archive** |

The 1.5.1 APKs were removed from `apk/` so no superseded build ships alongside this one.

---

## 2. What was added and changed

### 2.1 The Universal Decoder (requirements 1–12)

* `core/src/main/kotlin/com/texthub/core/detector/Diagnosis.kt` — `Confidence` (HIGH/LIKELY/POSSIBLE),
  `SecretKind`, `Candidate` (with `actionable`, `requiresSecret`, `suggestedParams`, `hashAlgorithm`,
  `direction`), `Step`, and the sealed `Diagnosis` (`Decoded`, `NeedsSecret`, `Choose`, `HashOnly`,
  `NotSupported`, `Failed`, `Unrecognized`).
* `core/src/main/kotlin/com/texthub/core/detector/UniversalDecoder.kt` — the two-stage detector and
  the chain walker. Stage 1: magic/version bytes, envelope layouts, PEM, OpenSSL `Salted__`, JWE
  compact form, JSON, escapes, URL, Morse, binary, Braille, punycode, Roman numerals. Stage 2:
  character-set candidates (Base-N family, hex, byte lists, digests, A1Z26, code points). Depth
  limited (default 4, maximum 8) with cycle detection and a manual `prefer` override.
* `core/src/main/kotlin/com/texthub/core/processors/UniversalDecoderProcessor.kt` — the registered
  tool (`universal`, glyph `UD`, category `SMART`, classification `DETECTION`, `oneWay`,
  `pinnedFirst`), including the plain-text report for the output field.
* Crypto envelope inspectors were added to the existing payload classes (`AesGcmPayload`,
  `AesCbcHmac`, `ChaCha20Poly1305`, `AesCtrHmac`, `RawKeyGcm`, `RsaPem`) — inspection only, no
  second implementation of any algorithm.
* `ToolCategory.SMART` (first enum constant, so the picker groups it first),
  `Classification.DETECTION` and `ToolMeta.pinnedFirst`; `ToolRegistry.all` sorts on the flag.
* App side: `app/src/main/kotlin/com/texthub/app/ui/AnalysisCard.kt` (detected format, confidence,
  reason, chain, candidates, the needed secret, the override row and *Analyse result again*),
  `HubUiState.analysis`, `HubViewModel.analyseResultAgain()`, and the override picker in `HubApp`
  that reuses the ordinary `ToolPickerSheet`.

**It is a dispatcher, not a second implementation.** Every step is executed by the registered tool
for that format through `ProcessingEngine`, so validation, error messages and parameter enforcement
are the ones those tools already produce. It performs no cryptography of its own, does not claim to
be secure, is not classified as encryption, and cannot be moved out of first place: its position is
metadata (`pinnedFirst`) rather than anything a user action can reach.

**It never guesses.** A secret is requested only when the payload itself says it is encrypted, only
of the kind the payload needs (password, raw key, RSA private key), and it is never tried,
brute-forced or defaulted. Authentication failures, truncations, wrong key sizes, bad versions,
impossible block sizes and malformed headers all end in a sentence such as "Decryption failed /
Authentication check failed" — never in output. Digests are reported as one-way (`HashOnly`), and
the only useful thing to do with one — comparing it against a candidate text — is offered instead.

### 2.2 Favourites drag and reordering (requirements 13–19)

* `core/.../prefs/PrefsModel.kt` — `moveFavoriteBefore(order, dragged, anchor)` and
  `moveFavoriteInDisplayedOrder(order, displayed, dragged, anchor)`: a drag is stored **by id**
  (the dragged row, and the row it is dropped in front of), never by counting screen positions.
  `PrefsData.moveFavoriteBefore` uses the same function, so the picker and the store cannot disagree.
* `app/.../ui/DragReorder.kt` — `resolveDragDrop(stored, displayed, draggedId, dragOffset, step)`
  returns the highlighted slot **and** the stored order from one calculation;
  `shiftRows(size, start, target)` moves every crossed row exactly one row aside;
  `settleOffsetPx(offset, rowsMoved, step)` is the leftover distance that is animated away after the
  drop. The existing `dropTargetIndex` (with its 40 % dead zone) and `autoScrollDelta` are unchanged.
* `app/.../ui/ToolPickerSheet.kt` — the favourites list now: follows the finger exactly, slides the
  rows aside to open the gap, keeps its order until release, commits **once**, animates the leftover
  distance so the row settles into the position it was stored at, resets cleanly on cancel, starts
  from a clean state on a second gesture, and disables dragging while a search is active. No scaling,
  no placement animation fighting the translation, no permanent offset.
* `app/.../viewmodel/HubViewModel.kt` + `AppPreferences.moveFavoriteBefore` — the drag result is
  written immediately and the picker shows exactly that order.

### 2.3 Tool ordering (requirement 2, 18)

`ToolRegistry.all = registered.sortedWith(compareByDescending { it.meta.pinnedFirst })`, so the
decoder is first in `all`, `metas()`, `search("")` and `byCategory(SMART)`, and the picker groups by
`category.ordinal` with `SMART` declared first. Favourites are a separate, freely reorderable order;
the decoder can be favourited and dragged inside it without moving in the main list. Pinned by test:
`UniversalDecoderTest.itIsFirstInEveryOrderingThatMatters` and
`theFirstPositionSurvivesFavouritesAndTemporaryData` (favourite → reorder → clear temporary data →
reorder again → still first, and `SMART.ordinal == 0`).

### 2.4 Privacy (requirement 12)

Unchanged and re-verified: no permissions beyond Android's internal
`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (checked with `aapt2 dump permissions`), no network code,
no WebView, no analytics. The analysis, the report and the step notes never contain the secret, the
password or the plaintext (`UniversalDecoderTest.secretsNeverAppearInTheDiagnosisText`). The
override and the analysis live in memory only; `prefer` is a normal, non-secret parameter, and
clearing temporary data resets it along with every other remembered parameter — favourites and
their order are kept.

### 2.5 Documentation (requirement 23)

* `docs/UNIVERSAL_DECODER.md` — detection stages, confidence rules, encryption and key limits, hash
  behaviour, nested decoding, the manual override, ordering, limits and tests, using the wording
  "identifies and processes supported Text Hub formats where the format can be determined reliably".
* `docs/TOOLS.md` — 74 tools, the `DET` class, the Universal Decoder row and sections on the tool
  and on favourites ordering.
* `README.md` — 1.6.0 header, a "What's new in 1.6.0" section, the Smart tools category, updated
  test counts, the new layout entries, the credential-free signing instructions and the limitation
  that the decoder cannot decrypt arbitrary unknown data.

---

## 3. Requirement checklist

| # | Requirement | Verified by |
| --- | --- | --- |
| 1 | Standalone Universal Decoder: paste → analyse → decode when reliable, otherwise detect and ask for the one secret | `UniversalDecoderTest` (37 cases incl. `aWrongPasswordFailsLoudlyAndNeverReturnsGarbage`, `rawKeyAndRsaPayloadsAskForTheRightKindOfSecret`) |
| 2 | Always first, surviving restart/search/category/temp-clear/favourites/reorder; not movable, not duplicated, searchable, own metadata | `itIsFirstInEveryOrderingThatMatters`, `theFirstPositionSurvivesFavouritesAndTemporaryData`, `theUniversalDecoderIsANormalRegisteredTool` |
| 3 | Reuses existing tools, no second implementation | `itNeverPerformsCryptographyOfItsOwn`; `ProcessingEngine` dispatch in `UniversalDecoder.runCandidate` |
| 4 | Two detection stages | `detect()` (structure) then candidates; `base64IsRecognisedWithItsReason`, `hexadecimalIsDecodedAndDigestsAreNot`, `severalCandidatesAreOfferedInsteadOfAGuess` |
| 5 | High / Likely / Possible, never certainty from length alone | `aSha256ShapedValueIsACandidateAndNotACertainty`, `everyDigestLengthIsRecognisedAsOneWay` |
| 6 | Never guess, try or brute-force a secret; enforce payload parameters | `theKeySizeInThePayloadIsEnforcedNotAskedFor`, `aLegacyPayloadWithoutARecordedKeySizeIsStillOpened`, `parametersOfTheTargetToolAreStillEnforced` |
| 7 | Authentication and validation before output | `aTruncatedEnvelopeFailsInsteadOfReturningGarbage`, `malformedPayloadsFailWithAFriendlySentence`, envelope tests for all six formats |
| 8 | Hashes are one-way; comparison instead | `aHashCanBeCheckedAgainstCandidateTextButNeverDecrypted`, `HashOnly` diagnosis |
| 9 | Nested decoding with a chain, analyse-again, depth and cycle limits | `nestedEncodingsAreUnwrappedInOrder`, `nestingStopsAtTheDepthLimit`, `aSelfReferentialEncodingCannotLoopForever`, `analysingTheResultAgainUnwrapsTheNextLayer` |
| 10 | Manual override through the existing picker | `aManualOverrideForcesTheChosenToolInsteadOfTheDetectedOne`, `anUnknownOverrideIsRefusedInlineAndIgnoredByTheAnalysis` |
| 11 | Existing UX language; extra controls only when needed | Analysis card appears only for this tool and only with input; override row always visible, secret field is the normal password field |
| 12 | Local only, no logs, temp-data rules respected | §2.4, `aapt2 dump permissions`, `secretsNeverAppearInTheDiagnosisText` |
| 13 | Release position equals the stored order | `theDisplayedOrderAfterEveryDropIsTheStoredOrder`, `everyRowOfFiveCanBeMovedToEveryOtherPosition`, single `resolveDragDrop` source |
| 14 | Drag coordinates mapped from the displayed list back to the stored favourites | `aFilteredFavouritesListStillMovesTheRowTheUserGrabbed`, `anAnchorThatIsNotOnScreenMovesNothing`, `PrefsModelTest.aDragIsStoredByIDSoFilteredFavouritesCannotShiftIt` |
| 15 | Smooth follow, rows move aside, no jump/flicker/double movement/scaling/permanent offset | `theRowsBetweenTheDragAndTheTargetSlideOneRowAside`, `everyShiftedRowMovesExactlyOnceRegardlessOfTheDistance`, `theRowIsDrawnWhereTheFingerIsUntilItSettles`, `theLeftoverDistanceIsOnlyTheFractionInsideTheSlot` |
| 16 | Dragging disabled while filtering/searching unless the mapping is provably correct | `reorderable = query.isBlank()`; the move itself is id-based, so even the favourites list (a subsequence) is exact |
| 17 | Persistence and verification across restart/add/remove/clear/search/category | `PrefsModelTest` (store re-read, clear keeps the order), `twoQuickDragsInARowBothLand` |
| 18 | Main-list priority independent of favourites | `theFirstPositionSurvivesFavouritesAndTemporaryData` |
| 19 | Full drag test matrix | `DragReorderTest` (32 cases): first↔second, middle→top/bottom, bottom→top/middle, top→bottom, every item to every target in A B C D E, filtered lists, rapid drags, cancellation, displayed == persisted |
| 20 | Universal Decoder test matrix | `UniversalDecoderTest` (38 cases), listed in §4 |
| 21 | Full suite green | 392 tests, 0 failures |
| 22 | Build verification + two archives | §5 |
| 23 | Documentation without over-claiming | `docs/UNIVERSAL_DECODER.md`, `docs/TOOLS.md`, `README.md` |
| 24 | Final checklist + this report | `docs/QA_CHECKLIST.md` §1.6.0 |

---

## 4. Tests

| Suite | Tests | Notes |
| --- | --- | --- |
| `:core:test` | **360** in 16 classes | 317 before round 10; `UniversalDecoderTest` adds 38, `PrefsModelTest` +4, plus the new audit rules |
| `:app:testDebugUnitTest` | **32** | 12 before; `DragReorderTest` grew to 32 with the drop-mapping matrix |
| **Total** | **392** | 0 failures, 0 errors |

`UniversalDecoderTest` covers: registration/pinning/duplication/search; no cryptography of its own;
every deterministic encoding decoded through its own tool (Base64, Base64URL, Base32, Base58, Base85,
Base45, Base91, hex, binary, URL, Unicode escapes, HTML entities, quoted-printable, Morse, Baudot,
Braille, Roman, A1Z26, punycode, UTF-16/32, JSON); all six Text Hub envelopes plus OpenSSL and JWE
(`dir` and `RSA-OAEP`); wrong password, truncated payload, invalid envelope, wrong key size, missing
secret and authentication failure; digests one-way and comparison-only; ambiguity as candidates;
nested chains with depth and cycle control; empty, random, malformed and oversized input; large
input through the off-UI-thread path; and no secret, password or plaintext in any diagnosis text.

The audit suites were extended rather than weakened: `RegistryTest` and `NewTools2Test` list the new
tool among the one-way tools that have no round trip, and the password rules in `ControlRulesTest`
and `ToolReviewPassTwoTest` now say *why* a dispatcher may leave its secret field optional — the
exemption is tied to `Classification.DETECTION` and to the tool's own information sheet, and a new
test (`onlyADispatcherMayLeaveItsSecretOptional`) makes sure no second tool can inherit it.

---

## 5. Build verification and archives

Commands (JDK 17, Gradle 8.2, Android SDK 34):

```bash
./gradlew :core:test :app:testDebugUnitTest    # 392 tests, 0 failures
./gradlew :app:lintDebug                       # 0 errors (abortOnError = true)
./gradlew :app:assembleDebug :app:assembleRelease
python3 tools/make_release_archives.py         # both archives + secret scan
```

Verification of the built APK:

```
aapt2 dump badging   → package: com.texthub.app, versionCode 9, versionName 1.6.0, minSdk 24, targetSdk 34
aapt2 dump permissions → only com.texthub.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION (no INTERNET)
apksigner verify --print-certs → verifies (v2), CN=Text Hub, RSA 2048,
    SHA-256 CC:69:D4:D0:50:EC:FA:9F:7A:94:B9:6E:9D:EE:30:5F:C4:4B:7D:B8:CA:17:BB:E1:B1:93:CD:B4:18:29:82:B1
strings on classes2.dex → the "Universal Decoder" tool and the SMART category are in the release build
```

The application id, version code, version name and signing certificate are unchanged from 1.5.1, so
1.6.0 installs as an update.

The published archive was verified by *unpacking it into a clean directory and building from it*:

```
unzip TextHub-1.6.0-source.zip → ./gradlew :core:test            → 360 tests, 0 failures (inputs
                                                                    identical to the verified run:
                                                                    Gradle reported FROM-CACHE)
                               → ./gradlew :app:assembleRelease  → app-release-unsigned.apk
```

That second line is the documented fallback in action: the archive contains no keystore and no
credentials, so a fresh checkout builds an unsigned release instead of failing.

**Archives.** `tools/make_release_archives.py` builds both and checks the public one three times:
by file name, by suffix (`*.jks`, `*.keystore`, `*.p12`, `*.pfx`, `*.pem`, `*.key`, …) and by
scanning every file in the finished archive for private-key headers and credential assignments. The
private archive holds the keystore, `keystore.properties`, `docs/SIGNING.md` and the signing
configuration; it is never referenced from the public archive and must never be uploaded.

**No password is stored in a source-controlled file**: `app/build.gradle.kts` reads the credentials
from Gradle properties, the environment or a local `keystore.properties` (which `.gitignore` and the
archive script both exclude) and builds an unsigned release variant when none of them is present.

---

## 6. What could not be verified here

* **No emulator or device.** The build environment has no KVM, so the UI was verified by compiling,
  linting, unit-testing the arithmetic and the state machine, and inspecting the packaged APK with
  `aapt2`/`apksigner`. A manual pass on a device — drag a favourite through the whole list, paste a
  payload into the decoder, try a wrong password — is still worth doing; the steps are in
  `docs/QA_CHECKLIST.md`.
* **Animation feel.** The drag arithmetic, the drop mapping and the settle distance are unit tested,
  but "does it feel smooth" needs a finger on real hardware.

## 7. Known limitations (unchanged unless stated)

* The Universal Decoder only knows the formats Text Hub supports. It reports "No supported format
  was recognised" for anything else instead of guessing, and it cannot decrypt arbitrary unknown
  encrypted data or recover a lost password.
* Classical ciphers are identified by character set only; nothing here cryptanalyses them.
* The decoder is the one tool whose password field may be empty, because its input decides whether a
  secret is needed at all. When a payload does need one, the analysis stops and names it.
* ChaCha20-Poly1305 still requires Android 9+; older devices are told so and pointed at AES-GCM.
