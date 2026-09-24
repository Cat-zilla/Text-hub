# Text Hub 1.6.6 — release report

**Version:** 1.6.6 (versionCode 15) · **Tools:** 74 (unchanged)
**Round:** 16 — *Universal Decoder: complete RSA key material, key-type awareness, saved-key vault integration*
**Build:** Gradle 8.2 / AGP 8.1.4 / Kotlin 1.9.22 / JDK 17 · `compileSdk 34`, `minSdk 24`, `targetSdk 34`

---

## 1. The report that started the round

> "When the Universal Decoder detects RSA-encrypted data it asks for an RSA key, but the key input
> does not allow a complete RSA key: the key is much longer than the field allows."

## 2. Root cause (traced, not assumed)

The complete path was inspected: Universal Decoder → RSA candidate → required-secret UI → Compose
state → `HubViewModel.setParam` → `validateParams` → `ProcessingEngine.run` → `RsaProcessor` →
`RsaPem`.

* **There is no character limit anywhere in the pipeline.** No `maxLength`, no input
  transformation, no `ParamSpec` cap, no validator, no ViewModel truncation, no engine limit, no
  clipboard cap. The parameter map is a plain `Map<String, String>`; a 4096-bit PEM passes through
  it byte for byte (pinned by `a4096BitPrivatePemIsNotTruncatedAnywhereOnTheWay`).
* **The limitation is the editor the secret was given.** The Universal Decoder declares one
  generic secret parameter (`secret`, `ParamKind.PASSWORD`) for *every* kind of secret, and the
  app renders `PASSWORD` as the password editor: `singleLine = true`, masked with
  `PasswordVisualTransformation`, IME declared non-multi-line. For a PEM that means: a single
  horizontally scrolling line of dots; the soft keyboard cannot enter a line break and, on the
  common IMEs, converts or drops the newlines of clipboard text committed through the IME; nothing
  of the key is visible, so a partial or altered paste cannot be seen. The core decodes fine when
  the whole PEM arrives - the *field* was never designed for one.
* A second, smaller cause of confusion: the field, its report ("Enter it in the Password / key
  field") and its helper text never said *which* key was needed.

So the fix is a presentation and wording change on the same parameter, not a new limit and not a
new parser.

## 3. The fix

* **RSA key editor for the same parameter.** When the current analysis says the detected format
  needs an RSA key (`Diagnosis.secretKind.isRsaKey`), or the field already holds a PEM block, the
  Universal Decoder's secret field is rendered with the app's existing multi-line PEM editor
  (`HubTextField`, monospace, `minHeight 120dp`, `maxLines 8`, vertically scrolling) - the same
  editor the RSA tool's own key field uses. No masking, no length limit, no transformation; the
  value is stored verbatim (line breaks included) in the same `secret` parameter, so the state,
  validation, engine and processor paths are unchanged. Passwords, AES keys, hex and Base64 keys
  keep the password editor exactly as before.
* **Key-type awareness.** `SecretKind` gained `PUBLIC_KEY`; `secretKindOf(meta, direction)` derives
  private vs public from the direction the RSA candidate is driven in (the decoder only decrypts →
  *RSA private key*). A `Diagnosis.secretKind` accessor tells the UI which secret the current
  analysis is about, for `NeedsSecret`, `Failed` (a wrong key is still an RSA key) and `Decoded`.
  The field caption, placeholder, helper text and the tool's textual report all name the key.
* **Clear.** A *Clear* action empties only the key parameter; the input text, the analysis and the
  vault are untouched (`clearAnalysisSecret`, pure and tested).
* **Use saved key.** Lists the existing encrypted collection (`RsaKeyCollection.keys()` metadata:
  name, size, SHA-256 fingerprint, save date - never contents). Choosing one calls the existing
  `RsaKeyCollection.load(name)` for that single record, puts its private PEM into the same `secret`
  parameter, labels the field "Using saved key: <name>" and analyses once. Nothing is written back,
  the generator's active pair is untouched, nothing is copied to the clipboard or logged. Any manual
  edit of the field afterwards makes it "Using manually entered key". No brute force: the decoder
  has no access to the collection at all, and the test with three saved records pins zero vault
  decryptions and zero writes during analysis.
* **No automatic persistence.** The secret parameter remains `sensitive` (never in preferences) and
  the saved-key name is session state; `selectTool`, `resetParams`, `restoreDefaults` and
  `clearTemporaryData` drop both. Restore Defaults continues to leave the vault alone.

## 4. Performance

The secret parameter has no validator (pinned), so pasting a large PEM never runs the RSA parser
per keystroke; `setParam` stays a map copy plus the cheap parameter checks, and the 140 ms debounce
and the 60 000-character heavy-input path of 1.6.2 are untouched. The only PEM check the UI does is
a prefix comparison (`looksLikePemBlock`), used solely to keep the editor from collapsing while the
input changes.

## 5. Security review

* Reports, errors and diagnosis reasons never contain the key body (`keyMaterialNeverAppearsInReportsOrErrors`).
* Failure wording is the RSA tool's own (`Errors.rsaKey / rsaNeedPrivate / rsaDecrypt / rsaPkcs1`) -
  no stack traces, no "cipher settings".
* Accessibility: the field carries its label only; the actions carry action descriptions only; the
  picker entries merge name/size/fingerprint - never key contents.
* Nothing new is written to disk; no new permissions; no INTERNET permission; no network code.

## 6. Files changed

* `core/.../detector/Diagnosis.kt` — `SecretKind.PUBLIC_KEY`, `isRsaKey`, direction-aware `secretKindOf`, `Diagnosis.secretKind`
* `core/.../processors/UniversalDecoderProcessor.kt` — key-specific instruction in the report and helper text
* `app/.../viewmodel/HubViewModel.kt` — `analysisSavedKeyName`, derived `analysisSecretKind` / `analysisUsesRsaKeyEditor`, pure `applyAnalysisSecret` / `clearAnalysisSecret`, `useSavedRsaKeyForAnalysis`, `clearAnalysisKey`
* `app/.../ui/MainScreen.kt` — `RsaKeyParameterField`, `SavedRsaKeyPicker`
* `app/.../ui/HubApp.kt`, `app/.../ui/AnalysisCard.kt`, `app/src/main/res/values/strings.xml`
* `app/build.gradle.kts` — 1.6.6 / versionCode 15
* Tests: `core/src/test/.../UniversalDecoderRsaKeyTest.kt` (12), `app/src/test/.../viewmodel/AnalysisKeyStateTest.kt` (7)
* Docs: `UNIVERSAL_DECODER.md`, `QA_CHECKLIST.md` (round 16), this report, `README.md`

## 7. Not changed

DetectionIndex, candidate isolation, the 1.6.2 recoverable-failure boundary, Punycode/Base58 fixes,
long-input protections, drag/reorder, favourites, haptics, accessibility work, Restore Defaults
semantics, the RSA Key Pair Generator, the named-key vault (AES-256-GCM, Android Keystore alias),
RSA-OAEP SHA-256, JWE behaviour and the tool processing architecture.

## 8. Remaining limitations

* On-device UI / Keystore / TalkBack verification was not possible in this environment (no
  emulator); the manual steps are listed in `QA_CHECKLIST.md`, round 16.
* The Universal Decoder still evaluates the analysis twice per run (once for the text report,
  once for the structured card) - pre-existing behaviour, cheap for RSA, left untouched on purpose.
