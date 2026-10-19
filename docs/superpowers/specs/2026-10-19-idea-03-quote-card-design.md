# [IDEA-03] Quote Card Social Share Design Specification

- **Status:** Approved
- **Date:** 2026-10-19
- **Priority:** P3 (Low) — 3 Story Points
- **Epic:** 04. IDEAS — Ý Tưởng Trải Nghiệm & Tăng Trưởng

---

## 1. Problem & Goal

Readers who find a good passage take raw screenshots to post on social networks. The app has no way to turn a selected passage into a shareable image. IDEA-03 adds a "Share quote" action to the Reader text-selection menu that opens a sheet where the user picks a card template and shares a PNG.

## 2. Accepted Decisions

| Decision | Rationale |
|---|---|
| Add the action through a custom `LocalTextToolbar` around the Reader `SelectionContainer`. | Matches the acceptance criterion: select text, the action menu shows "Share quote". |
| Draw the card as an `android.graphics.Bitmap` with `Canvas` and `StaticLayout`, 1080×1350 px, `ARGB_8888`. | Pure function, independent of screen layout, runs off the main thread and is testable with Robolectric. The sheet preview shows the same bitmap, so preview and export always match. |
| Limit the quote to 280 characters (`QUOTE_MAX_CHARS`), cut on a word boundary and append "…". | Keeps the card readable on social networks. The sheet tells the user when text was cut. |
| Three templates: Minimalist, Gradient Material, Dark Studio. | Required by the acceptance criteria. |
| Share the PNG through the existing `FileProvider` (`${applicationId}.fileprovider`) from `cacheDir/quote_cards/`. | `file_paths.xml` already exposes `cache-path name="cache" path="."`, so no provider change is needed. |
| Restore the user's previous clipboard after the sheet closes. | The toolbar action needs the selected text, which is obtained by asking Compose to copy it. |

## 3. Selection Hook

- In `Content.kt` wrap the Reader `SelectionContainer` with `CompositionLocalProvider(LocalTextToolbar provides QuoteTextToolbar(...))`.
- `QuoteTextToolbar` delegates to the platform toolbar it replaces, keeps Copy/Cut/Paste/Select all and adds a "Share quote" item only when `onCopyRequested` is available. The exact `TextToolbar` interface is checked against the resolved Compose version before coding.
- Tapping the item: remember the current primary clip, call `onCopyRequested()`, read the clip text, pass it to `QuoteSelection.sanitize`, restore the previous clip, then open the sheet. An empty result opens nothing.
- `QuoteSelection.sanitize(raw: String): String?` is pure: trims, collapses runs of whitespace and line breaks into single spaces, returns null when blank.
- Metadata (title, feed, date) stays inside `DisableSelection`, so only article body text can be quoted.

## 4. Rendering

- `QuoteCardSpec(quote: String, feedName: String, author: String?, articleTitle: String)` and `enum QuoteCardTemplate { MINIMALIST, GRADIENT_MATERIAL, DARK_STUDIO }` with named colour, size and margin constants.
- `QuoteCardRenderer.render(spec, template): Bitmap` and `QuoteTextTruncator.truncate(text, max): Truncated(text, wasCut)` are pure.
- Layout: large opening quote mark, quote text in a `StaticLayout` (`TextDirectionHeuristics.FIRSTSTRONG_LTR` so Arabic/Hebrew read right-to-left), a footer with author (when present), feed name and the "RSS Cat Hub" watermark. Text is clipped to the card area and never drawn outside the bitmap.
- Rendering runs on `Dispatchers.Default`.

## 5. Sheet, ViewModel and Sharing

- `QuoteCardSheet` (`ModalBottomSheet`): preview image, three template chips, a note when the quote was cut, a Share button and an error message with retry.
- `QuoteCardViewModel` (Hilt) holds the selected template and state `Idle`, `Rendering`, `Ready(bitmap, wasCut)`, `Error`. Changing the template cancels the previous render job. The ViewModel never holds `Context` or `Activity`; it receives them as arguments or through injected helpers. The bitmap is recycled when replaced or when the ViewModel is cleared.
- `QuoteCardSharer` writes the PNG to `cacheDir/quote_cards/quote_<random>.png` on `@IODispatcher`, deletes files older than a named retention period when it writes a new one, gets the URI with `FileProvider.getUriForFile`, and builds `ACTION_SEND` with `type = "image/png"`, `EXTRA_STREAM`, `ClipData` and `FLAG_GRANT_READ_URI_PERMISSION`, wrapped in `Intent.createChooser`. Failures to write or start the chooser surface as the sheet error state.

## 6. Localization

Add strings for the sheet title, the three template names, Share, "Quote was shortened", and the render/share error to `values/strings.xml` and all 37 maintained locale directories. Preserve `StringResourceParityTest`. Vietnamese keeps full diacritics.

## 7. Testing and Verification

### Unit
- `QuoteSelection.sanitize`: blank, whitespace-only, multi-line, long text.
- `QuoteTextTruncator`: exact limit, one over, cut on a word boundary, no cut for short text, a single very long word, surrogate pairs not split.
- `QuoteCardRenderer` (Robolectric): size is 1080×1350 for all templates; sampled background pixels differ between templates; text pixels exist inside the card; long text does not draw outside the bitmap; Arabic and CJK samples render without exception.
- `QuoteCardViewModel`: state transitions, template change cancels the earlier render, error path, bitmap recycled.
- `QuoteCardSharer`: file is created under `quote_cards`, PNG signature is valid, URI authority is `${packageName}.fileprovider`, intent action/type/flags, old files pruned.

### Compose and instrumentation
- Widget tests: sheet shows three templates, selecting one changes the preview state, the note appears only when cut, Share invokes the callback.
- Instrumentation on the Tecno (`adb -s 115333744A005844` only, isolated `EInkUiTestRunner`): sheet clears status and navigation insets; `FileProvider` URI written by `QuoteCardSharer` opens and decodes as a 1080×1350 bitmap through `ContentResolver`.
- Real-app smoke on the Tecno: confirm the custom toolbar still allows long-press selection in a real article, shows "Share quote", opens the sheet, switches templates and reaches the system chooser. Check the foreground window with `dumpsys` before every tap, take a screenshot after every step and stop immediately if a full-screen ad covers the UI, then wait for the user's "done". Banners that do not cover the UI are skipped.

### Verification limits
- Full unit suite, `assembleDevDebug` and `compileDevDebugAndroidTestKotlin` must pass. Lint is affected by a known unrelated SDK 37 metadata failure; do not claim lint passed unless that changes.
- Golden screenshots, Macrobenchmark/Perfetto profiling and network conditioning are not part of this feature. No ad, VIP or IAP behaviour is touched.
- Android 12+ may show a "pasted/read clipboard" notice when the app reads the copied selection; this is a platform behaviour and is noted in the completion report.

## 8. Not In Scope

- Custom fonts, background images, text editing or colour pickers.
- Saving cards to a gallery or in-app history.
- Posting directly through social network SDKs.
- Quoting from the Notebook screen.
- Changes to ad, VIP or IAP code (deferred by the owner).
