# Text Hub 1.6.8 — release report

**Version:** 1.6.8 (versionCode 17) · **Tools:** 74 (unchanged)
**Round:** 18 — *Settings simplification & UX cleanup* (information architecture only; no new
settings, no behaviour change)
**Build:** Gradle 8.2 / AGP 8.1.4 / Kotlin 1.9.22 / JDK 17 · `compileSdk 34`, `minSdk 24`, `targetSdk 34`

---

## 1. What changed

The 1.6.7 Settings screen showed every preference at once (six cards, ~30 controls). 1.6.8 keeps
every one of them, with the same keys, defaults and behaviour, and reorganises them behind a root
list of six destinations with progressive disclosure. Nothing below the settings UI was touched:
no change to the ViewModel, preferences store, theme, registry, Universal Decoder, RSA crypto,
vault, Keystore alias, drag/reorder, favourites, haptics or reset semantics.

### Structure (max. two levels)

```
Settings
├─ Appearance            "Theme, colours, text size and interface"
│    Theme · Dynamic colour · Accent · Text size · Interface ›
│    └─ Interface        Layout density · UI animation · Show tool icons · Monospace output
├─ Accessibility         "Text size, motion, contrast and touch targets"
│    Text & display:     Large text · High contrast · Text labels for icons · Reduce animations
│    Interaction & haptics: Larger touch targets · Haptic feedback
├─ Privacy & security    "Sensitive data and private-key protection"
│    Privacy:            local-only statement · Clear on leaving tool · Clear on background · Sensitive-data warnings
│    Private keys:       Confirm before copying · Hide previews
├─ Data & reset          "Reset preferences, favourites and saved keys"
│    Reset:              Restore app preferences · Reset remembered tool settings · Reset favourites
│    Delete data (red):  Clear saved RSA keys · Clear everything
├─ Advanced              "Processing and diagnostic options"
│    Processing:         Process automatically · Show processing time
│    Interface:          Show copy confirmation
│    Diagnostics ›
│    └─ Diagnostics      intro line · Show tool ID · Show detection details · Show validation details · Reset tool-specific settings
└─ About                 "Version, licences and app information"
```

The root screen has no switches: six navigation rows (icon, title, one-line summary, chevron).
Sub-pages use one small group label per group, no dividers, no cards inside cards; navigation rows
and switch rows are visibly different (chevron vs. switch) and both are single 48dp+ controls with
merged semantics.

### Settings moved / grouped (all keys and defaults unchanged)

| Setting | 1.6.7 | 1.6.8 |
| --- | --- | --- |
| Layout density, Show tool icons, Monospace output, UI animation | Appearance (flat) | Appearance › Interface |
| Haptic feedback | Accessibility (after a divider) | Accessibility › Interaction & haptics |
| Large text, High contrast, Icon labels, Reduce animations | Accessibility (flat) | Accessibility › Text & display |
| Larger touch targets | Accessibility | Accessibility › Interaction & haptics |
| Clear on switch / background, Sensitive warnings | Security & privacy (flat) | Privacy & security › Privacy |
| Confirm private-key copy, Hide private previews | Security & privacy (flat) | Privacy & security › Private keys |
| Restore / Reset tool settings / Reset favourites | Data & reset (one list) | Data & reset › Reset |
| Clear saved RSA keys / Clear everything | Data & reset (same list) | Data & reset › Delete data (red group) |
| Process automatically, Show processing time | Advanced (flat) | Advanced › Processing |
| Show copy confirmation | Advanced | Advanced › Interface |
| Show tool ID / detection / validation details, Reset tool-specific settings | Advanced (flat) | Advanced › Diagnostics |

Redundancy review: *Large text ⇄ Text size* and *Reduce animations ⇄ UI animation* remain the same
stored value shown in two places (the pair is asserted by test); their subtitles now say so in plain
words. *Icon labels* (text instead of icon buttons) and *Show tool icons* (monogram in lists) are
different concepts and stay independent. *Sensitive-data warnings* is the only warning switch.

Removed text: the three long paragraphs (privacy body, history body, storage note) that repeated
each other; the single spec statement ("Text Hub processes supported text locally…") is shown on the
Privacy page and About. No implementation terminology (AES, Keystore, vault paths) appears in any
setting text.

### Navigation

`SettingsNavigation` (pure data, `ui/settings/SettingsPages.kt`) holds the current page; back — the
arrow or the system gesture — goes up one parent, and leaves Settings from the root. Opening
Settings always starts at the root.

## 2. Files changed

* `app/src/main/kotlin/com/texthub/app/ui/settings/SettingsPages.kt` — **new**: `SettingsPage`,
  `SettingRow` (page + preference key for every control), `SettingsAction`, `SettingsNavigation`.
* `app/src/main/kotlin/com/texthub/app/ui/SettingsScreen.kt` — paged body; new `NavRow`,
  `GroupLabel`; existing `SwitchRow`, `ChoiceRow`, `ActionRow`, `AccentPicker`, `ThemeOptionRow`,
  `InfoRow`, `TwoListDialog` reused unchanged.
* `app/src/main/kotlin/com/texthub/app/ui/HubApp.kt` — settings page state + back handling.
* `app/src/main/res/values/strings.xml` — summaries, group labels, shorter descriptions; three
  redundant paragraphs removed.
* `app/src/test/kotlin/com/texthub/app/ui/settings/SettingsPagesTest.kt` — **new**, 11 tests.
* `app/build.gradle.kts` (1.6.8 / 17), `tools/make_release_archives.py`, `README.md`,
  `docs/QA_CHECKLIST.md` (Round 18), this report.

Not touched: `HubViewModel`, `AppPreferences`, `:core` (all of it), theme, MainScreen, RSA, vault.

## 3. Tests

* **New:** `SettingsPagesTest` (11) — root lists exactly the six destinations in order; nothing
  deeper than two levels; no empty page; sub-pages carry ≥3 controls (no over-nesting); every
  stored key (18 `UiSettings` + theme, accent, auto-process, copy confirmation, haptics) is reachable
  from a row; rows sharing a key are exactly the two documented pairs and always span Appearance/
  Accessibility; placement matches the specification; diagnostics never sit on Advanced or the root;
  keys/defaults unchanged (saved 1.6.7 values re-read identically); reset actions keep their order,
  destructive flags and confirmation counts (Clear everything = 2); back walks up the parents and
  then out; every page's depth equals its number of back steps.
* **Unchanged:** all 1.6.7 tests — 465 core (incl. `UiSettingsTest`, `SensitiveFieldClearingTest`,
  `RsaKeyVaultTest.deleteAll*`, UD crash/isolation, Base58, PEM) and 64 app (drag/reorder, analysis
  key state).
* **Totals:** **540 tests, 0 failures** — 465 `:core:test` + 75 `:app:testDebugUnitTest` (64 + 11 new). No test weakened or removed.

## 4. Build & verification

* `:core:test :app:testDebugUnitTest` — SUCCESS; `:app:lintDebug` — **0 errors**, 5 warnings (all the
  pre-existing `GradleDependency` "newer version available" notes); `:app:assembleDebug`,
  `:app:assembleRelease` — SUCCESS (run as separate daemon invocations for the 2 GB sandbox).
* Badging: `package: name='com.texthub.app' versionCode='17' versionName='1.6.8'`; the only
  `uses-permission` is the AGP-injected `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` — **no INTERNET**.
* Release signature: unchanged shipping key, cert SHA-256
  `CC:69:D4:D0:50:EC:FA:9F:7A:94:B9:6E:9D:EE:30:5F:C4:4B:7D:B8:CA:17:BB:E1:B1:93:CD:B4:18:29:82:B1`.
  Keystore + `keystore.properties` were copied into the repo root for the build only and removed
  afterwards; not committed, not in the source zip.
* Sizes: release APK 9,733,564 B (1.6.7: 9,726,076), debug APK 14,657,493 B.
* SHA-256 of every artefact: `release-1.6.8/SHA256SUMS.txt`.

## 5. Device verification status

No emulator/device in this environment; the on-device pass (TalkBack reading of navigation vs.
switch rows, large text without clipping, larger targets without overlap, back gesture, dark/light/
dynamic) is scripted as **Round 18** in `docs/QA_CHECKLIST.md` and has not been run.

## 6. Limitations

* Sub-page transitions are instant (no slide animation) — deliberately, to stay within the existing
  components and the UI-animation setting.
* *Reset tool-specific settings* appears twice on purpose (Data & reset, Diagnostics); both open the
  same confirmation and call the same operation.

## 7. Workspace

No files were deleted in this round beyond build outputs. The 1.6.7 delivery folder and the 1.6.6
signing archive are preserved; the shipping keystore was neither rotated nor replaced.
