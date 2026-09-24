# Text Hub 1.6.7 — release report

**Version:** 1.6.7 (versionCode 16) · **Tools:** 74 (unchanged)
**Round:** 17 — *Settings & UX polish* (a polish/feature round, not a redesign)
**Build:** Gradle 8.2 / AGP 8.1.4 / Kotlin 1.9.22 / JDK 17 · `compileSdk 34`, `minSdk 24`, `targetSdk 34`

---

## 1. What this round does

The Settings screen is reorganised into six cards and gains the switches the specification asked
for, all on top of the existing preference store, theme system, dialog pattern and components.
Nothing was redesigned: the tool registry, processing engine, Universal Decoder, RSA crypto, vault
format, Keystore alias, drag/reorder, favourites, haptics and Restore Defaults semantics are as they
were in 1.6.6.

### Settings layout

| Card | Contents |
| --- | --- |
| **Appearance** | Theme (unchanged) · **Dynamic colour** · Accent (unchanged) · **Layout density** · **Text size** · **UI animation** · **Show tool icons** · **Monospace output** |
| **Security & privacy** | Privacy statement · **Clear sensitive fields when leaving a tool** · **Clear sensitive fields when the app goes to the background** · **Confirm before copying private keys** · **Hide private key previews** · **Sensitive-data warnings** |
| **Accessibility** | **Large text** · **Reduce animations** · **High-contrast controls** · **Always show text labels for icons** · **Larger touch targets** · Haptic feedback (moved here from *Interaction*) |
| **Data & reset** | **Restore app preferences** (existing Restore Defaults) · **Reset remembered tool settings** (existing Clear temporary data) · **Reset favourites** · **Clear saved RSA keys** · **Clear everything** |
| **Advanced** | Process automatically and Show copy confirmation (moved here from *Processing* / *Clipboard*, the sections the spec removed) · **Show processing time** · **Show tool ID** · **Show detection details** · **Show validation details** · **Reset tool-specific settings** |
| **About** | App name, version, version code, build type, tool count, developer, licences, local-only statement |

*Experimental tools*: the registry has no notion of an experimental tool (`ToolMeta` has no such
flag and no tool is hidden), so the setting is omitted rather than shown as a dead control.

### Every new setting and its default

| Setting | Key | Default | Notes |
| --- | --- | --- | --- |
| Dynamic colour | `dynamic_color` | OFF | Android 12+ only; the row is disabled with an explanation below that. The stored accent is untouched while on and returns exactly when switched off. AMOLED keeps its black background over the dynamic palette. |
| Layout density | `layout_density` | `comfortable` | Compact trims card padding (24→16dp) and card/row gaps. Touch targets are not part of these tokens and never shrink. |
| Show tool icons | `show_tool_icons` | ON | Hides the letter monogram in the header and picker list; names and classification chips carry the meaning, semantics unchanged. |
| Monospace output | `monospace_output` | ON | ON is what the output box always was; the input and PEM editors are unaffected. |
| Text size | `large_text` | `system` (false) | Large scales the app's type ramp by 1.15 (sizes *and* line heights, all in sp), stacking on the system font scale — never replacing it. |
| UI animation | `ui_animation` | `full` | Reduced halves, Off snaps. Applied to the decorative motion only (segmented-control colour, favourites row-shift and settle). The drag itself, the processing bar and state changes are untouched. |
| High-contrast controls | `high_contrast` | OFF | Stronger `outline`/`outlineVariant`, firmer muted text, more distinct `surfaceVariant`/`primaryContainer`; theme and accent unchanged. |
| Always show text labels | `icon_labels` | OFF | The icon-only Share / Clear buttons under the output become labelled `TextAction`s with the same callbacks and enabled state. |
| Larger touch targets | `large_touch_targets` | OFF | `TextAction` min height 48→56dp; the output icon buttons 40→48dp. |
| Large text (Accessibility) | — | — | The *same* `large_text` preference as Text size. |
| Reduce animations (Accessibility) | — | — | A view of `ui_animation`: on = not Full. Switching it on from Full gives Reduced; it never downgrades an explicit Off; off returns to Full. |
| Clear sensitive fields when leaving a tool | `clear_secrets_on_tool_switch` | **ON** | ON is the behaviour the app always had (sensitive parameters are never persisted, so a tool switch started them empty). OFF keeps them in a memory-only per-tool map for the session; nothing is ever written. |
| Clear sensitive fields on background | `clear_secrets_on_background` | OFF | On `ON_STOP`: sensitive parameters of the current tool → defaults, the session map is dropped, the saved-key label cleared, and the tool re-runs. The input text, output, the RSA generator's active pair and the saved collection are not touched. |
| Confirm before copying private keys | `confirm_private_key_copy` | ON | A dialog before the private PEM goes to the clipboard. |
| Hide private key previews | `hide_private_key_preview` | ON | The private-key card shows a placeholder until *Reveal* (session state, per pair). While hidden nothing derived from the key is drawn, measured or exposed to accessibility. |
| Sensitive-data warnings | `sensitive_warnings` | ON | Gates the red "Never share it" line under the private key. Warnings are single lines, never repeated. |
| Show processing time | `show_processing_time` | OFF | "Processed in N ms" under the output, from the engine's existing `durationMs`. |
| Show tool ID | `show_tool_id` | OFF | The registry id beside the classification chip. |
| Show detection details | `show_detection_details` | OFF | Each Universal Decoder candidate row adds `id · confidence · direction [· not runnable] [· hash] [· params: names]`. Read from the existing `Candidate`; detection logic unchanged; no payload, no secret. |
| Show validation details | `show_validation_details` | OFF | Under the parameters: "All N parameters are valid" or one line per existing `ParamIssue`. No exceptions, no stack traces. |

All keys live in `PrefsKeys`, are read/written through one typed value (`UiSettings`, pure Kotlin
in `:core`), and are listed in `KEPT_WHEN_CLEARING`.

### Reset semantics

| Action | Removes | Keeps |
| --- | --- | --- |
| Restore app preferences | every preference incl. all 1.6.7 settings, remembered params, recents, last tool | favourites + order, **the vault** |
| Reset remembered tool settings / Reset tool-specific settings (same action, two entry points) | `params_*`, recents | everything else incl. all 1.6.7 settings, **the vault** |
| Reset favourites | `favorites_order` (+ legacy key) | everything else, **the vault** |
| Clear saved RSA keys | every record of the encrypted collection (`RsaKeyCollection.deleteAll()`) | all preferences; the generator's active pair stays on screen (becomes unsaved) |
| Clear everything | all of the above + the active pair, the text on screen and every transient secret | nothing of Text Hub's; nothing outside the app |

Every action has a "will be removed / will be kept" dialog with Cancel; *Clear saved RSA keys* uses a
red *Delete keys* button and spells out that decryption with a deleted key becomes impossible;
*Clear everything* is two dialogs, the second specifically about private keys. All are safe when
there is nothing to remove (the rows say "No favourites to remove" / "No saved key pairs").

**Restore Defaults never wipes the vault** — unchanged from 1.6.x and now also pinned in
`UiSettingsTest.restoringDefaultsResetsEverySettingAndKeepsFavourites` together with the fact that
`restoredToDefaults()` cannot reach the vault at all (it only filters the preference map).

### Security

* No secrets in logs (the project still has no `Log`/`println`/`printStackTrace`), in
  accessibility (the hidden private key is a placeholder text, not a transformed key), or in error
  strings (detection/validation details print ids, enum names and parameter *names*).
* Vault crypto, file format and Keystore alias untouched. `deleteAll()` is the existing
  `records.clear(); persist()` path used by `delete(name)`, on the same sealed file.
* No new permissions (manifest still declares none; `INTERNET` still removed by `tools:node`).
* The session-only secret map exists only while "clear when leaving a tool" is OFF, is cleared by
  the background-clear, by every reset and by turning the setting back ON, and is never persisted.

### Accessibility

* Settings rows stay whole-row `toggleable`/`selectable` controls with merged semantics; new
  segmented choices reuse `SegmentedControl` (48dp segments); action rows get an explicit
  `contentDescription` ("Reset: Reset favourites").
* Disabled rows (Dynamic colour below Android 12) are disabled in semantics, not just faded.
* Large text uses sp so the system scale still applies; High-contrast changes colours only.

### UX polish outside Settings

* `TextAction` takes a colour, so destructive actions are consistently drawn in the error colour.
* Private-key card: hidden placeholder, *Reveal/Hide* action, copy confirmation.
* Card padding and gaps follow one density token set (`Density`), so screens stay consistent.
* Output footer: labelled actions when icon labels are on; larger targets when requested.

## 2. Tests

New / extended (all pure JVM, `:core:test`):

| Class | Tests | What |
| --- | --- | --- |
| `UiSettingsTest` | 14 | defaults, empty store, unknown values, round trip, other prefs untouched, key list complete (18), stored values are only booleans/ids, restore keeps favourites only, clear-temporary keeps settings, favourites-only reset, no-op on empty, reduce-animations ⇄ animation mode, duration scaling, enum ids |
| `SensitiveFieldClearingTest` | 6 | over the real registry: only sensitive params reset, idempotent, unknown keys untouched, session copy = non-empty secrets, no secret survives clearing in any tool, every PASSWORD param is sensitive |
| `RsaKeyVaultTest` | +2 | `deleteAll` removes and persists, reports count, safe when empty |

Totals: **529 tests, 0 failures** — 465 `:core:test` (was 443) + 64 `:app:testDebugUnitTest`
(unchanged: `DragReorderTest` 39, `DragDropPlacementTest` 18, `AnalysisKeyStateTest` 7). No
existing test was weakened or removed.

Regression coverage still in place: Universal Decoder crash protections and candidate isolation,
Base58 cap, long-input protections, drag/reorder matrix, haptics preference, Restore Defaults keeps
favourites, RSA explicit-only, vault round-trip, full-length PEM through the decoder.

## 3. Build & verification

* `gradle :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease`
  — SUCCESS (the 2 GB sandbox needed the assembly split into two daemon runs after a metaspace
  restart; tests and lint were complete before that).
* Lint: **0 errors**. Remaining warnings are the pre-existing `GradleDependency` "newer version
  available" notes and Compose `AutoboxingStateCreation` informationals. The unused legacy section
  strings were removed and the one `PluralsCandidate` converted to a real plural.
* APK badging: `package: name='com.texthub.app' versionCode='16' versionName='1.6.7'`; the only
  `uses-permission` is the AGP-injected `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`; **no INTERNET**.
* Signing: release APK signed with the unchanged shipping key (alias `texthub`, cert SHA-256
  `CC:69:D4:D0:50:EC:FA:9F:7A:94:B9:6E:9D:EE:30:5F:C4:4B:7D:B8:CA:17:BB:E1:B1:93:CD:B4:18:29:82:B1`);
  keystore and `keystore.properties` were copied into the repo root for the build and removed
  afterwards; they are not in Git and not in the source zip.
* Sizes and SHA-256 sums: see `release-1.6.7/SHA256SUMS.txt` (filled in below at packaging time).

| Artefact | Size |
| --- | --- |
| `TextHub-1.6.7-release.apk` (signed, R8) | 9,726,076 bytes (1.6.6: 9,683,372) |
| `TextHub-1.6.7-debug.apk` | 14,638,589 bytes (1.6.6: 14,574,845) |

## 4. Device verification status

No emulator or device is available in this environment (2 GB RAM, no KVM). Everything above the
Compose layer is unit-tested; the on-device pass (dynamic colour, TalkBack reading of the
placeholder and the labelled actions, the background clear on Home, the two-step *Clear everything*)
is scripted as **Round 17** in `docs/QA_CHECKLIST.md` and remains to be run on hardware.

## 5. Limitations & honest notes

* Dynamic colour needs Android 12; below that the row is disabled and explains why.
* "Reduce animations" cannot express "Off" on its own (it maps to Reduced from Full and preserves an
  existing Off); the Appearance control is the full three-way choice.
* Layout density and larger touch targets are applied to the shared tokens (`SectionCard`,
  `TextAction`, output icon buttons); individual hard-coded paddings elsewhere were left alone rather
  than swept in a redesign.
* The session-only secret cache (setting OFF) is intentionally lost on process death.

## 6. Workspace cleanup

Inventory before cleanup: the repo (`/home/user/Text-hub`, with `app/build`, `core/build`, `.gradle`
from this build and the two copies of the shipping keystore placed there for signing), the 1.6.6
delivery folder `release-1.6.6/` and two 1.6.6 APK copies at the repo root.

| Removed (generated, superseded) | Preserved | Uncertain / left alone |
| --- | --- | --- |
| `Text-hub/TextHub-1.6.6-{release,debug}.apk` (repo-root copies) | all source, tests, docs, gradle files | — |
| `release-1.6.6/TextHub-1.6.6-{release,debug}.apk`, `-source.zip`, `SHA256SUMS.txt` | `release-1.6.6/TextHub-1.6.6-signing-files.zip` → moved to `archive-1.6.6/` (signing material; byte-identical keystore to the 1.6.7 zip, never deleted) | — |
| `app/build`, `core/build`, `.gradle` (build outputs) | `release-1.6.7/` (new delivery) | — |
| `texthub-release.jks`, `keystore.properties` copied into the repo root for the build (removed again; never committed) | `Text-hub/TextHub-1.6.7-{release,debug}.apk` at the repo root (input of the archive script, same bytes as in `release-1.6.7/`) | — |

The shipping keystore was neither rotated nor replaced; its certificate fingerprint is unchanged.
