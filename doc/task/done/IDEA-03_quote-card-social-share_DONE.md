# [IDEA-03] Quote Card Social Share — Completion Report

- **Status:** Implemented and verified on local `dev`; not pushed.
- **Completed:** 2026-10-19
- **Audit:** 9.0/10. Selection hook, sanitizer/truncator, three-template renderer, share file/URI, ViewModel, sheet and localization are covered by unit, widget and device tests, and verified on the Tecno. Gaps are listed under Limits: the clipboard restore was not observed on the device, the chooser was opened but nothing was sent, and no golden/performance profiling was run.
- **Commits:**
  - `19ef6609`, `c404efc0` — design spec and its BottomDrawer alignment
  - `f0f46eec` — implementation plan
  - `56b0d1b8` — quote selection sanitizer and truncator
  - `9c674a7a` — `QuoteCardRenderer` with three templates
  - `bf993be4` — card sharer and selection toolbar wrapper
  - `89c8d404` — `QuoteCardViewModel` and state
  - `334e2361` — sheet content and Reader state
  - `aa7407c2` — share chip anchored to the selection toolbar
  - `382c7a7b` — wiring into the Reader
  - `18d0b5a4` — localization (38 locales)
  - `6f3042b8` — instrumentation tests
  - `4ca34385`, `3e9a083a` — drawer fixes found on the device

## Implementation

- `QuoteSelection.sanitize` collapses whitespace and line breaks and returns null for blank text. `QuoteTextTruncator` limits the quote to 280 characters (`QUOTE_MAX_CHARS`), cuts on a word boundary, never splits a surrogate pair and appends "…".
- `QuoteCardRenderer` draws a 1080×1350 ARGB_8888 `Bitmap` with `Canvas` and `StaticLayout` for Minimalist, Gradient Material and Dark Studio. It is a pure function (no `Context`), draws the quote, author, feed name and the "RSS Cat Hub" watermark, keeps text inside the margins and uses first-strong text direction for Arabic/Hebrew.
- `QuoteTextToolbar` wraps the platform `TextToolbar` and keeps Copy / Select all. The Android toolbar has no hook for extra items (checked with `javap`), so `QuoteShareChip` shows a "Share quote" chip in a `Popup` placed below the selection (or above when there is no room, clamped to the window; the placement is a pure function with unit tests).
- Tapping the chip copies the selection, reads it, restores the previous clipboard and opens the sheet. `QuoteCardViewModel` renders on `@DefaultDispatcher`, cancels the previous render when the template changes and recycles replaced bitmaps. `QuoteCardSharer` writes the PNG to `cacheDir/quote_cards/` (files older than 24 h are pruned) and shares it through the existing `FileProvider` with `FLAG_GRANT_READ_URI_PERMISSION` and `ClipData`; `file_paths.xml` was not changed.
- The sheet content (`QuoteCardSheetContent`) lives in the app's existing `BottomDrawer`, because `material3:1.0.1` has no `ModalBottomSheet`.
- 8 new strings in all 38 maintained locales.

## Verification

- `./gradlew testDevDebugUnitTest assembleDevDebug compileDevDebugAndroidTestKotlin`: **PASS**, 1052 unit tests, 0 failures/errors/skipped (999 before this feature, 53 new).
- New unit/widget tests cover the sanitizer, truncator, renderer (size, template colours, text and footer pixels, margins, Arabic/Chinese/emoji), sharer (valid PNG, unique names, pruning, intent and URI), toolbar wrapper, chip placement, ViewModel states and cancellation, Reader state and the sheet content.
- `StringResourceParityTest`: 6/6 PASS; all locale XML files parse; every locale has the 8 keys.
- Rendered all three templates to PNG and inspected them; background, quote, footer and watermark are correct.
- Tecno (`115333744A005844`, `adb -s` only, isolated `EInkUiTestRunner`): `QuoteCardInstrumentationTest` **4/4 PASS** — the FileProvider URI decodes as 1080×1350, the sheet clears the system bar insets, the sheet inside the real `BottomDrawer` shows the Share button without dragging, and a real long-press selection works through the wrapped toolbar and copies the selected text.
- Live smoke on the Tecno, screenshots after each step and the foreground activity checked before each tap:
  - Long-press on "average" kept the platform menu (Copy / Select all) and showed the "Share quote" chip below the selection.
  - Tapping the chip opened the sheet with the card for "average", BBC News and the watermark.
  - Switching to Gradient and Dark Studio changed the preview as expected.
  - Tapping Share opened the system chooser (`com.transsion.resolver/.ChooserActivity`) titled with the PNG file name; it was dismissed without sending anything. `cache/quote_cards/quote_dark_..._57352.png` (25 KB) was present in the app cache; it was deleted after the test.
  - The shared drawer still opens and closes the "Article Highlights" AI sheet normally.
- Two defects were found on the device and fixed, each after a test reproduced it: the Share button was hidden under the 3-button navigation bar (`4ca34385`), and the drawer opened half-expanded so the template chips and Share button sat below the screen edge until dragged (`3e9a083a`, `skipHalfExpanded = true`).
- `lintDevDebug` was not run (known local Android SDK 37 metadata issue); no lint pass is claimed. `connectedDevDebugAndroidTest` was not run.

## Limits and known issues

- **Clipboard restore was not observed on the device.** The code stores the previous clip and restores it right after reading the selection, but `cmd clipboard` is not available on the Tecno, so a marker could not be set and read back. It is covered only by the code path and the instrumentation test that checks the selected text reaches the clipboard.
- Android 12+ may show a "pasted/read clipboard" notice when the selection is read back; this is platform behaviour.
- The custom toolbar replaces the platform menu in the Reader only; items contributed by other apps (Translate, Web search) are not shown there.
- `skipHalfExpanded = true` applies to the shared Reader drawer. The Article Highlights sheet was checked and still looks right; the Mind Map and Deep Read sheets were not opened after this change.
- The share chip's anchor uses the platform toolbar rectangle; on devices whose toolbar sits elsewhere the chip is clamped inside the window but may overlap other text.
- Nothing was posted to a social network; only the system chooser was opened.
- Not covered: golden screenshots, Macrobenchmark/Perfetto profiling, network conditioning, ad/IAP behaviour (deferred by the owner).
