# Text Hub 1.5.1 — drag-and-drop fix and bug sweep

**Version:** 1.5.1 (versionCode 8) · **Tools:** 73 · **Tests:** 329, all green

This is a follow-up to 1.5.0 for the reported problem with the favourites drag. It changes no
payload format, no privacy rule and no tool behaviour other than the A1Z26 fixes listed below.

---

## 1. Why the row wobbled, and what replaced it

The old gesture did four things that together produced the up-and-down jumping:

1. **It reordered the list during the drag.** Every time the finger passed a threshold the stored
   order changed, so the item the gesture was attached to changed place mid-gesture.
2. **It animated and translated the same row at once.** The row carried
   `Modifier.animateItemPlacement()` (which animates a slot change) *and* a
   `graphicsLayer { translationY = dragOffset }` (which moves it by hand). The two movements
   fought each other on every frame.
3. **The step size was wrong.** The offset was divided by the measured row height, but the list
   also has a 4 dp gap between rows, so a "one position" move was computed from a distance that
   was always too short - the row advanced early and then had to be corrected.
4. **The threshold had no dead zone.** `roundToInt()` on that wrong step flipped between two
   positions whenever the finger sat near a boundary, and each flip re-ran the placement animation.

The replacement:

* the lifted row follows the finger exactly (`translationY`, 2% scale, elevation, haptic tick);
* **the order does not change while dragging.** The row that would be displaced is highlighted
  instead, and the floating row is drawn above the list (`zIndex`);
* the step is the measured row height **plus the gap**, and the target position only changes after
  60% of a row of travel, so a shaky finger cannot make it flicker;
* near the top or bottom edge the list follows the finger at a bounded speed (12 dp per frame) and
  the visual offset is corrected by exactly the scrolled distance, so the row stays under the
  finger;
* releasing commits the move **once**, so preferences are written one time per drag instead of
  every frame;
* the drag exists only on the unfiltered favourites list, because while a search is active the
  visible rows are a subset and a drop would move the wrong stored entry.

The arithmetic lives in `app/src/main/kotlin/com/texthub/app/ui/DragReorder.kt` and is covered by
`DragReorderTest` (12 tests), including a sweep that drags a row down one pixel at a time and
asserts the target position never moves backwards - the exact signature of the old wobble.

## 2. Other bugs found in the sweep

| Bug | Impact | Fix |
| --- | --- | --- |
| Dragging inside a filtered favourites list | The displayed rows are a subset, so indices did not match the stored order and the wrong tools could be reordered | Dragging is disabled while a query is active (and the hint disappears) |
| Restoring remembered parameters | A value that the tool would now reject (a removed choice, a number out of range) was restored and handed to the processor | Restored values are validated against the current tool; anything that fails is dropped and the default is used |
| Statistics of a large input | Character/word/line counts ran on the UI thread on every keystroke; a multi-megabyte paste could stutter | Above 20 000 characters the count runs off the main thread, and a large result's statistics are measured in the same background step as the processing |
| A1Z26 encoding of non-ASCII letters | `Char.isLetter()` matches thousands of characters, so `ü` became the number 188, which then failed to decode | Only A-Z have a position; other characters pass through unchanged and the info sheet says so |
| A1Z26 decoding with a different separator | A payload written with spaces returned nonsense when the separator setting said hyphen | Decoding accepts every separator people type (space, hyphen, comma, dot, slash, pipe) and refuses values outside 1-26 |
| Picker empty-state text | "Nothing matched <query>" was shown when there were simply no favourites | The two situations now say what is true |

## 3. Delivered artifacts

| Artifact | Size | md5 |
| --- | --- | --- |
| `apk/TextHub-1.5.1-release.apk` (signed, installs over 1.5.0) | 9,566,976 B | `9f129cee14f3e126f29881184b598414` |
| `apk/TextHub-1.5.1-debug.apk` | 14,371,016 B | `c62dc3ef9d80c4ae727bbc213af478e6` |
| `TextHub-1.5.1-source.zip` (complete project, tests, docs, keystore, both APKs) | 97 Kotlin files, ~24 MB | see the file itself |

## 4. Verification

* `:core:test` — 317 tests, 0 failures.
* `:app:testDebugUnitTest` — 12 tests, 0 failures (the new drag arithmetic).
* `:app:lintDebug` — 0 errors (5 dependency-version notices).
* `apksigner verify` — v2 signature valid, signed with the shipping key
  (`CN=Text Hub, O=TextHub, C=IN`), so 1.5.1 installs over 1.5.0 as an update.
* `aapt2 dump badging` — `versionName 1.5.1`, `versionCode 8`, minSdk 24, targetSdk 34, launchable
  `com.texthub.app.MainActivity`, no network permission.

**No emulator or device is available in this environment**, so the gesture was not exercised by
hand here: the fix is the removal of the four mechanical causes described in §1 plus the unit tests
on the arithmetic that decides where a row lands. The manual steps in `docs/QA_CHECKLIST.md`
(*Round 7*, steps 2-3) remain the device-side check.

## 5. Still true from 1.5.0

Temporary-data clearing that never touches favourites, favourites-only dragging, capability-driven
controls, enforced encryption parameters, OpenSSL `enc` and JWE compatibility, the *Additional
encryption settings* section, and the full 73-tool review. See
`docs/RELEASE_REPORT_1.5.0.md`.
