# Text Hub 1.6.1 — release report

**Version:** 1.6.1 (versionCode 10) · **Tools:** 74 · **Tests:** 418 (379 core + 39 app), all green
**Round:** 11 — *Detection correctness: the registry matrix, and the end of the "cipher settings" message*
**Build:** Gradle 8.2 / AGP 8.1.4 / Kotlin 1.9.22 / JDK 17 · `compileSdk 34`, `minSdk 24`, `targetSdk 34`

---

## 1. The report that started the round

> "A Base64 string pasted into the Universal Decoder reports *'These cipher settings are not valid.
> Please check the supplied key and parameters.'*"

An encoded string has no cipher, no key and no cipher settings. The message had to be impossible,
not merely reworded, so the round began by finding out how a *decoding* path could ever produce it.

### The cause

An **unforeseen error** — anything a processor threw that was not a `ToolException` — was reported to
the user as a problem with the *cipher settings*, because that was the one generic sentence the error
table had. Two consequences followed from that single line:

* a defect inside a decoding path (a Punycode code point out of range, a plugboard letter indexing an
  array out of range) surfaced as a complaint about a password the user had never typed;
* the sentence was attached to paths that have no cipher settings at all, which is exactly what the
  user saw.

### The fix

* **The generic message no longer exists.** `Errors.cipherParams()` was deleted; every cipher failure
  now names what is actually wrong — a key that needs letters, a text whose length is not a multiple
  of the block size, a key size that does not match the payload, an invalid plugboard — and
  `ProcessingEngine` reports an unforeseen failure as exactly that
  (`Errors.unexpectedFailure()`), mentioning no cipher, key or parameter.
* **The two crashes the new fuzz tests found were fixed at their source**: the Punycode decoder
  validates its code points before building the string, and the Enigma plugboard accepts letters
  `A–Z` only (`Char.isLetter()` is true for `é`, which indexed the wiring array out of range).
* **A deterministic candidate can no longer reach a cipher check even in principle.**
  `ToolMeta.cipherParameters` is empty for every tool that is not an encryption tool, and the
  dispatcher hands the typed secret only to operations whose registered tool declares key material.
* **The manual override can no longer become a trap.** The forced tool is applied to the analysis in
  front of the user, is shown on the result card with a clear action, and is declared
  `sessionOnly = true`, so it is never written to disk and a stale override cannot hijack a later
  paste. `ToolMeta.rememberedParams()` also drops empty values, so nothing blank is written either.

---

## 2. Detection is a registry matrix, not a list

`DetectionIndex` is built from `ToolRegistry.all` — there is no second tool list anywhere, and no
`if tool == Base64`. Each tool declares how its own input looks, in its own metadata
(`ToolMeta.detection`): alphabet, characters it must contain, prefix/suffix, minimum length, length
rule, and the parameters the payload itself records (envelope version, key size, JWE algorithm).
A tool that declares nothing is still considered:

| Participation | How | Tools |
| --- | --- | --- |
| `hint` | Matched against the tool's own declared hints, then validated by running it | 32 metas |
| `generic` | Deterministic tools with no hints: decode, then re-encode; accepted only if the round trip returns and the result is readable | case, leet, linetools, reverse, hexdump, … |
| `symmetric` | Transformations that read readable text either way (ROT13, Atbash): no input can be evidence, so they are never candidates and stay available through the override | rot13, atbash, … |
| `keyed` | Needs key material: only payloads it recognises structurally are reported | aes, aescbc, chacha, aesctr, aesrawkey, rsa, caesarbrute, … |
| `one-way` | Produces an answer rather than a decoding (key material, MACs, checksums, reports) | rsakeygen, hmac, pbkdf2, checksum, textdiff, textstats, regex |
| `dispatcher` | The Universal Decoder itself — never a candidate, so it cannot recurse | universal |

`DetectionIndex.accountedFor()` reports this for all 74 registered tools, and
`UniversalDecoderDispatchTest` asserts that the matrix covers the registry exactly and that a
deterministic tool cannot fall outside it unless it declares itself symmetric. A tool registered in a
later version therefore takes part in detection immediately, without touching the decoder.

**Every candidate is validated by running that tool** through `ProcessingEngine`. A hint that matched
but whose tool refuses the input, or whose output is not readable text, produces no candidate at all.

---

## 3. Candidate metadata comes from the registry

Whether a candidate needs a secret, **which kind** of secret it needs, which parameter carries it,
whether it needs cipher parameters and whether it is one-way are derived from the registered tool's
own metadata (`Candidate.operation`, `requiresSecret`, `secretKind`, `secretParam`,
`requiresCipherParameters`, `oneWay`), never from the rule that found it. An encoding therefore
cannot ask for a password and a digest cannot look decryptable — and `registeredCandidate()` refuses
to build a candidate at all for a tool id that is not registered, instead of failing in the user's
hands.

Confidence stays evidence-based: **High** only for structural evidence (magic bytes, version ids,
envelope layout, PEM, JWE compact form, OpenSSL `Salted__`), **Likely** for evidence that validates
and decodes to readable text, **Possible** for a character set or a length alone. A digest-shaped
value is reported as *Possible* MD5/SHA-1/SHA-256/SHA-512 and can only be compared with a candidate
text (`HashOnly`, never "decrypt").

---

## 4. Tests

Both suites were executed on the delivered tree (`gradle --no-daemon --no-build-cache --rerun-tasks
:core:test :app:testDebugUnitTest`):

| Suite | Classes | Tests | Failures |
| --- | --- | --- | --- |
| `:core:test` | 18 | **379** | 0 |
| `:app:testDebugUnitTest` | 1 | **39** | 0 |
| **Total** | 19 | **418** | **0** |

New in this round — `UniversalDecoderDispatchTest` (19 tests) walks the **production path** (registered
tool → registry → processing engine) rather than the detector's internals:

* `encodingThenAnalysingThenDecodingRoundTripsThroughTheRegisteredTool` — the §17 regression:
  encode "Hello TextHub" → analyse ⇒ decoded, the step names `base64`, and the tool that performed it
  is the same registered tool;
* `aDecodedEncodingNeverMentionsCipherSettingsOrPasswords`,
  `noPathThroughTheDecoderEverReportsCipherSettingsAsTheProblem` — the reported bug, as a rule over a
  corpus that stands for every tool family;
* `aDetectedEncodingNeverRequiresASecretAndNeverValidatesCipherSettings`,
  `anEncodingShapedInputIsNeverAskedForAPassword`, `everyCandidateTheDetectorOffersNamesARegisteredTool`;
* `everyDetectedEncodingDecodesThroughItsOwnRegisteredTool` (registry-driven sample table);
* `everyRegisteredToolIsAccountedForByTheDetector`, `theMatrixCoversWhatTheDetectorClaimsToCover`,
  `aCandidateCanOnlyNameARegisteredTool`, `theDetectorsOwnToolIsNeverOfferedAsACandidate`;
* `everyToolThatTakesASecretDeclaresItAsAnActualParameter`,
  `everyRegisteredToolTakesAwkwardParametersWithoutAnUnexplainedFailure` (parameter torture over every
  encoding, transformation and classical cipher — this is what found the Enigma plugboard crash),
  `theDecoderItselfNeverFailsWithAnUnexpectedError`;
* `aManualOverrideDispatchesToThatToolAndNeverBlamesTheCipherSettings`,
  `anUnknownManualOverrideIsIgnoredInsteadOfBreakingTheAnalysis`,
  `theManualOverrideIsNeverRememberedForTheNextSession`.

`DragReorderTest` grew from 32 to 39 tests for the drag polish, all arithmetic on the same pure
helpers the UI uses: the same auto-scroll speed at 60 Hz and 120 Hz, a capped catch-up after a
stalled frame, a slow drag that does not move before 60 % of a row is covered, a fast drag landing
under the finger, hovering on a boundary without flicker, and the offset absorbing exactly what was
scrolled. The reorder algorithm itself was **not** touched.

Lint: `:app:lintDebug` **BUILD SUCCESSFUL** (report: `app/build/reports/lint-results-debug.html`).

---

## 5. Favourite drag polish (§20–§21)

Animation only — the resolution, the ordering and the persistence are the 1.6.0 code:

* **Following the finger now costs a redraw, not a recomposition.** The drag offset is mutated on
  pointer events and read inside the row's `graphicsLayer` block (the draw phase). The list of row
  shifts moved behind `derivedStateOf`, so rows are recomposed only when a row genuinely changes
  slot instead of on every pointer sample.
* **The edge auto-scroll is frame-rate independent.** `autoScrollForFrame(stepPerFramePx,
  frameSeconds)` scales the per-frame distance by the real frame time (capped at four frames), so a
  120 Hz screen follows at the same speed as a 60 Hz one and a stalled frame cannot shoot the list
  away from the finger.
* No scaling, no spring, no new offset state: the settle step, the dead zone (60 % of a row before a
  slot changes) and the single stored commit are unchanged, and `displayed == stored` still holds for
  every drop.

---

## 6. Build verification

| Check | Result |
| --- | --- |
| `:app:assembleDebug` | `apk/TextHub-1.6.1-debug.apk` — 14,474,511 B, md5 `765302347e1f41c0ea60089703644008` |
| `:app:assembleRelease` | `apk/TextHub-1.6.1-release.apk` — 9,628,480 B, md5 `2c9569f6d0ddc968e2872eaa5170aa58` |
| `aapt2 dump badging` | `com.texthub.app`, versionCode **10**, versionName **1.6.1**, minSdk 24, targetSdk 34, compileSdk 34, label *Text Hub*, launcher `com.texthub.app.MainActivity` |
| `aapt2 dump permissions` | exactly one: `com.texthub.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. **No `INTERNET`**, nothing else |
| `apksigner verify --print-certs` (release) | Verifies; APK Signature Scheme **v2**, RSA-2048, certificate SHA-256 `cc69d4d050ecfa9f7a94b96e9dee305fc44b7db8ca17bbe1b193cdb4182982b1` — the existing shipping keystore, **no new keystore was generated** |
| Release signing | Credential-free Gradle configuration (Gradle property / `keystore.properties` / `TEXTHUB_*` environment variables); the `signingReady` gate keeps the build working without credentials by producing an unsigned artefact |

No emulator or device is available in this environment, so the strongest available verification is
the one used: the unit suites for behaviour (registry order, pinned-first, dispatch, validation), and
`aapt2`/`apksigner` for the artefact itself. On-device feel (how the drag follows a real finger) is
covered by the arithmetic tests plus the manual steps in `docs/QA_CHECKLIST.md` §Round 11.

---

## 7. Deliverables

| Artefact | Contents |
| --- | --- |
| `apk/TextHub-1.6.1-release.apk` | Signed release build (see §6 for its certificate) |
| `apk/TextHub-1.6.1-debug.apk` | Debug build for comparison |
| `~/TextHub-1.6.1-source.zip` | Public source archive: the complete project — source, tests, resources, icons, themes, Gradle configuration, documentation, run scripts and both APKs — with **no signing material** (excluded by name, by extension, and by an independent scan of the finished archive for private-key headers and credential literals) |
| `~/TextHub-1.6.1-signing-files.zip` | Private signing archive (keystore, credentials file, signing configuration, `SIGNING.md`): never uploaded, never included in the public archive. Its size and hash are recorded in the release message, not inside the archives |

The public archive's own size and hash are recorded in the release message that accompanies it,
because the archive contains this report.

---

## 8. Known limitations (unchanged, and deliberate)

* The Universal Decoder identifies and processes **supported Text Hub formats** where the format can
  be determined reliably, using the existing registered tool implementations. It cannot decrypt
  arbitrary unknown encrypted data.
* It never guesses, tries, brute-forces or defaults a password or a key, and it never presents
  random bytes as plaintext. An authentication failure is reported as "Decryption failed /
  Authentication check failed".
* Hashes are one-way: they can be compared with a candidate text, never reversed.
* Encoding, classical ciphers and XOR are described as what they are — reversible transformations,
  never as secure encryption. AES-GCM is authenticated encryption with a vetted implementation, and
  a lost password cannot be recovered.
* Favourites reordering, parameters and appearance are the only things stored; **no history**, no
  plaintext, no keys, no passwords. Everything runs on the device: no account, no analytics, no
  advertising, no `INTERNET` permission, no WebView.

---

*See also:* [`docs/UNIVERSAL_DECODER.md`](UNIVERSAL_DECODER.md) for how detection works,
[`docs/TOOLS.md`](TOOLS.md) for the tool reference, [`docs/ENCRYPTION_FORMAT.md`](ENCRYPTION_FORMAT.md)
for the payload formats, [`docs/QA_CHECKLIST.md`](QA_CHECKLIST.md) for the verification steps.
