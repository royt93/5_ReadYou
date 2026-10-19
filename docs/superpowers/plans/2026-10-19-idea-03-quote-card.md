# [IDEA-03] Quote Card Social Share Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user select article text in the Reader, tap "Share quote" in the selection menu, pick one of three card templates in a sheet, and share a 1080×1350 PNG through the system chooser.

**Architecture:** A custom `TextToolbar` wraps the platform toolbar and adds a "Share quote" item; tapping it copies the selection, reads it, restores the previous clipboard and opens a sheet. A pure `QuoteCardRenderer` draws the card with `android.graphics.Canvas` and `StaticLayout`. `QuoteCardViewModel` renders on `Dispatchers.Default`; `QuoteCardSharer` writes the PNG under `cacheDir/quote_cards/` and shares it through the existing `FileProvider`. The sheet content lives inside the app's existing `BottomDrawer`.

**Tech Stack:** Kotlin 2.1.0, Jetpack Compose (UI 1.7.8), Material 3 1.0.1 (no `ModalBottomSheet`), Material 2 `ModalBottomSheetLayout` via `BottomDrawer`, Hilt, JUnit4, MockK, Robolectric 4.14.1 with `GraphicsMode.NATIVE`, Compose UI test.

## Global Constraints

- Add the action through a custom `LocalTextToolbar` around the Reader `SelectionContainer`.
- Draw the card as an `android.graphics.Bitmap` with `Canvas` and `StaticLayout`, 1080×1350 px, `ARGB_8888`.
- Limit the quote to 280 characters (`QUOTE_MAX_CHARS`), cut on a word boundary and append "…". The sheet tells the user when text was cut.
- Three templates: Minimalist, Gradient Material, Dark Studio.
- Share the PNG through the existing `FileProvider` (`${applicationId}.fileprovider`) from `cacheDir/quote_cards/`. `file_paths.xml` already exposes `cache-path name="cache" path="."`; do not change it.
- Restore the user's previous clipboard immediately after the selected text has been read, before the sheet opens.
- Metadata (title, feed, date) stays inside `DisableSelection`; only article body text can be quoted.
- Sheet content renders inside the existing `BottomDrawer`; `material3:1.0.1` has no `ModalBottomSheet`.
- Rendering runs on `Dispatchers.Default`; file writes run on `@IODispatcher`; ViewModels never hold `Context` or `Activity`.
- No magic numbers: sizes, colours, margins, limits and retention are named constants.
- Localize every new string across all 38 maintained locale files (`values` + 37 `values-*`); keep `StringResourceParityTest` green; Vietnamese keeps full diacritics.
- Live Android work only on the Tecno: `adb -s 115333744A005844`; never run Gradle `connected*` tasks. Check the foreground window with `dumpsys` before every tap, take a screenshot after every step, and stop immediately if a full-screen ad (`com.google.android.gms.ads.AdActivity`) covers the UI, then wait for the user's "done". Banner ads that do not cover the UI are skipped. Do not tap the VIP trial-code button or change app settings that were not requested.
- Keep `.idea/**` untouched. No ad, VIP or IAP behaviour is changed (deferred by the owner).

## Known limits (record in the completion report)

- Android 12+ may show a "pasted/read clipboard" notice when the selected text is read back from the clipboard; platform behaviour.
- The custom toolbar replaces the platform text menu inside the Reader only; items contributed by other apps (Translate, Web search) are not shown there.
- The renderer draws plain text; emoji and complex scripts depend on the device fonts.

---

### Task 1: Pure text logic — `QuoteSelection` and `QuoteTextTruncator` (TDD)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/quote/QuoteSelection.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/quote/QuoteTextTruncator.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/domain/model/quote/QuoteSelectionTest.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/domain/model/quote/QuoteTextTruncatorTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  object QuoteSelection { fun sanitize(raw: String?): String? }   // null when blank

  data class Truncated(val text: String, val wasCut: Boolean)
  object QuoteTextTruncator {
      const val QUOTE_MAX_CHARS = 280
      const val ELLIPSIS = "…"
      fun truncate(text: String, max: Int = QUOTE_MAX_CHARS): Truncated
  }
  ```

- [ ] **Step 1: Write tests (RED)**
  - `QuoteSelectionTest`: `null` → null; `""` → null; `"   \n\t "` → null; `"  hello   world  "` → `"hello world"`; `"line1\n\nline2\r\nline3"` → `"line1 line2 line3"`; text containing a non-breaking space `"a b"` → `"a b"`.
  - `QuoteTextTruncatorTest`: text of exactly 280 chars → `Truncated(text, false)`; 281 chars made of 10-char words → cut at the last word boundary at or before 280, result ends with `"…"`, `wasCut = true`, and `result.length <= 281` (280 chars + ellipsis only if the cut lands exactly at 280; otherwise shorter); a single 400-char word with no spaces → hard cut at 280 followed by `"…"`; a text whose 280th char is the high half of a surrogate pair (`"a".repeat(279) + "😀" + "tail"`) → the result never ends with a lone high surrogate (assert `!Character.isHighSurrogate(result.text.removeSuffix("…").last())`); short text → unchanged and `wasCut = false`; trailing spaces before the cut point are trimmed before the ellipsis (`"word ".repeat(100)` result does not contain `" …"`).

- [ ] **Step 2: Run, confirm RED**
  `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.model.quote.*"` → compilation failure.

- [ ] **Step 3: Implement**

`QuoteSelection.kt`:
```kotlin
package com.mckimquyen.reader.domain.model.quote

object QuoteSelection {
    private val WHITESPACE_RUN = Regex("[\\s\\u00A0]+")

    /** Chuẩn hoá đoạn bôi đen: gộp khoảng trắng/xuống dòng, trả null nếu rỗng. */
    fun sanitize(raw: String?): String? =
        raw?.replace(WHITESPACE_RUN, " ")?.trim()?.takeIf { it.isNotEmpty() }
}
```

`QuoteTextTruncator.kt`:
```kotlin
package com.mckimquyen.reader.domain.model.quote

data class Truncated(val text: String, val wasCut: Boolean)

object QuoteTextTruncator {
    const val QUOTE_MAX_CHARS = 280
    const val ELLIPSIS = "…"

    fun truncate(text: String, max: Int = QUOTE_MAX_CHARS): Truncated {
        if (text.length <= max) return Truncated(text, false)
        var end = max
        // Không cắt giữa cặp surrogate.
        if (Character.isHighSurrogate(text[end - 1])) end--
        val lastSpace = text.lastIndexOf(' ', end - 1)
        val cut = if (lastSpace > 0) lastSpace else end
        return Truncated(text.substring(0, cut).trimEnd() + ELLIPSIS, true)
    }
}
```

- [ ] **Step 4: Run, confirm GREEN** (same command).
- [ ] **Step 5: Commit**
  `git add app/src/main/java/com/mckimquyen/reader/domain/model/quote app/src/test/java/com/mckimquyen/reader/domain/model/quote && git commit -m "feat(domain): add quote selection sanitizer and truncator"` plus the Co-Authored-By trailer.

---

### Task 2: `QuoteCardRenderer` and templates (TDD, Robolectric native graphics)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/quote/QuoteCardSpec.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/quote/QuoteCardTemplate.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/quote/QuoteCardRenderer.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/infrastructure/quote/QuoteCardRendererTest.kt`

**Interfaces:**
- Consumes: `QuoteTextTruncator.truncate` (Task 1).
- Produces:
  ```kotlin
  data class QuoteCardSpec(val quote: String, val feedName: String, val author: String?, val articleTitle: String)

  enum class QuoteCardTemplate(val storageName: String) { MINIMALIST("minimalist"), GRADIENT_MATERIAL("gradient"), DARK_STUDIO("dark") }

  object QuoteCardRenderer {
      const val CARD_WIDTH = 1080
      const val CARD_HEIGHT = 1350
      const val WATERMARK = "RSS Cat Hub"
      fun render(spec: QuoteCardSpec, template: QuoteCardTemplate): Bitmap   // pure, no Android context
  }
  ```
  The quote is truncated inside `render`; `QuoteTextTruncator.truncate(spec.quote).wasCut` is what the sheet uses for its note.

- [ ] **Step 1: Write tests (RED)** — annotate the class `@RunWith(RobolectricTestRunner::class)`, `@Config(sdk = [34], manifest = Config.NONE)`, `@GraphicsMode(GraphicsMode.Mode.NATIVE)` (verified to draw real pixels and `StaticLayout`):
  - `render_returnsExactSizeForEveryTemplate`: for each `QuoteCardTemplate.entries`, `bitmap.width == 1080`, `bitmap.height == 1350`.
  - `render_backgroundsDifferBetweenTemplates`: `getPixel(8, 8)` for MINIMALIST, GRADIENT_MATERIAL and DARK_STUDIO are pairwise different.
  - `render_gradientChangesTopToBottom`: for GRADIENT_MATERIAL `getPixel(8, 8) != getPixel(8, 1342)`.
  - `render_drawsQuoteTextPixels`: count of pixels in the central text area (x 90..990, y 300..900) that differ from `getPixel(90, 300)`'s background is `> 0` for every template.
  - `render_drawsFooterPixels`: pixels in the footer band (y 1180..1330) differing from the band's background `> 0` (author, feed and watermark).
  - `render_longQuoteStaysInsideCard`: a 5,000-character quote renders without exception and the outer margin columns x 0..39 contain only the template's background colour at y 300..900 (compare to `getPixel(5, y)` of the same row).
  - `render_handlesArabicChineseAndEmoji`: specs with `"مرحبا بالعالم"`, `"你好，世界"` and `"Hello 😀"` render without exception and draw text pixels.
  - `render_noAuthorOmitsAuthorButStillDrawsFeed`: two renders (author null vs "Jane") both succeed and the bitmaps are not pixel-identical.

- [ ] **Step 2: Run, confirm RED** — `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.quote.QuoteCardRendererTest"` → compilation failure.

- [ ] **Step 3: Implement**
  - `QuoteCardSpec.kt` and `QuoteCardTemplate.kt` as in Interfaces.
  - `QuoteCardRenderer.kt` — all numbers are named `private const val`s at the top of the object, for example `MARGIN = 90`, `QUOTE_TOP = 260`, `QUOTE_TEXT_SIZE = 56f`, `QUOTE_MARK_SIZE = 220f`, `FOOTER_TOP = 1170`, `FOOTER_TEXT_SIZE = 34f`, `WATERMARK_TEXT_SIZE = 30f`, `LINE_SPACING_MULT = 1.25f`, and per-template colour constants (`MINIMALIST_BG = 0xFFFAF7F2`, `MINIMALIST_TEXT = 0xFF1B1B1B`, `GRADIENT_START = 0xFF4F6BED`, `GRADIENT_END = 0xFF9C5BE0`, `GRADIENT_TEXT = 0xFFFFFFFF`, `DARK_BG = 0xFF121212`, `DARK_TEXT = 0xFFF2F2F2`, accent colours). Steps inside `render`:
    1. `Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)` and a `Canvas`.
    2. Background: solid colour for MINIMALIST and DARK_STUDIO; `LinearGradient(0f, 0f, 0f, CARD_HEIGHT.toFloat(), START, END, Shader.TileMode.CLAMP)` for GRADIENT_MATERIAL.
    3. Opening quote mark `“` drawn with a `Paint` in the accent colour.
    4. `val truncated = QuoteTextTruncator.truncate(spec.quote)`; build `StaticLayout.Builder.obtain(text, 0, text.length, textPaint, CARD_WIDTH - 2 * MARGIN).setLineSpacing(0f, LINE_SPACING_MULT).setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR).setMaxLines(MAX_QUOTE_LINES).setEllipsize(TextUtils.TruncateAt.END).build()`; save/translate to `(MARGIN, QUOTE_TOP)`, draw, restore. `MAX_QUOTE_LINES = 14` guarantees the text never reaches the footer.
    5. Footer: a thin divider line, then the author (if not blank) and feed name drawn with `StaticLayout` single-line ellipsized layouts at `FOOTER_TOP`, and the `WATERMARK` right-aligned at the bottom.
    6. Return the bitmap.
  - No `Context`, no resources; everything uses `android.graphics` only.

- [ ] **Step 4: Run, confirm GREEN** (same command).
- [ ] **Step 5: Commit** — `feat(quote): add QuoteCardRenderer with three templates` (+ trailer).

---

### Task 3: `QuoteCardSharer` and `QuoteTextToolbar` core (TDD)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/quote/QuoteCardSharer.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/ui/component/quote/QuoteTextToolbar.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/infrastructure/quote/QuoteCardSharerTest.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/ui/component/quote/QuoteTextToolbarTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  class QuoteCardSharer @Inject constructor(@ApplicationContext private val context: Context, @IODispatcher private val ioDispatcher: CoroutineDispatcher) {
      suspend fun writePng(bitmap: Bitmap, template: QuoteCardTemplate, nowMillis: Long = System.currentTimeMillis()): File
      fun buildShareIntent(file: File, chooserTitle: String): Intent
      companion object {
          const val DIR_NAME = "quote_cards"
          const val RETENTION_MS = 24L * 60 * 60 * 1000
          const val MIME_PNG = "image/png"
      }
  }

  /**
   * Gói TextToolbar nền tảng. Toolbar Android không cho thêm mục tuỳ biến (đã kiểm bằng javap
   * AndroidTextToolbar), nên lớp này giữ nguyên menu gốc và phơi ra trạng thái để một chip
   * "Chia sẻ trích dẫn" (QuoteShareChip, Task 5) hiện cạnh menu.
   */
  class QuoteTextToolbar(
      private val delegate: TextToolbar,
      private val onShareQuote: () -> Unit,
  ) : TextToolbar {
      var anchorRect: Rect? by mutableStateOf(null)          // rect của lần showMenu gần nhất
          private set
      var shareAvailable: Boolean by mutableStateOf(false)    // true khi có onCopyRequested
          private set
      override val status: TextToolbarStatus                  // = delegate.status
      override fun showMenu(rect: Rect, onCopyRequested: (() -> Unit)?, onPasteRequested: (() -> Unit)?,
                            onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?)
      override fun hide()                                     // delegate.hide(), xoá anchorRect/shareAvailable/copy
      /** Gọi copy đã lưu rồi onShareQuote(); no-op khi chưa có đoạn chọn. */
      fun triggerShare()
  }
  ```
  The Compose 1.7.8 `TextToolbar` interface has exactly `showMenu(rect, onCopyRequested, onPasteRequested, onCutRequested, onSelectAllRequested)`, `hide()` and `status` (verified with `javap`).

- [ ] **Step 1: Write tests (RED)**
  - `QuoteCardSharerTest` (Robolectric, `@GraphicsMode(NATIVE)`, real `ApplicationProvider` context, `Dispatchers.Unconfined`):
    - `writePng_createsPngUnderQuoteCardsDir`: result file's parent is `cacheDir/quote_cards`, the first 8 bytes equal the PNG signature `89 50 4E 47 0D 0A 1A 0A`, `BitmapFactory.decodeFile` gives the same width/height as the input.
    - `writePng_usesDifferentNamesForSameTemplateAtDifferentTimes`: two calls with different `nowMillis` produce two distinct files.
    - `writePng_prunesFilesOlderThanRetention`: create `old.png` in the directory with `lastModified = now - RETENTION_MS - 1`, then write a new file; `old.png` no longer exists, the new file does.
    - `buildShareIntent_hasActionTypeStreamAndReadPermission`: `intent.action == Intent.ACTION_CHOOSER`; the wrapped `EXTRA_INTENT` has action `ACTION_SEND`, type `image/png`, `EXTRA_STREAM` URI with authority `"${packageName}.fileprovider"` and path ending with the file name, `FLAG_GRANT_READ_URI_PERMISSION`, and a non-null `clipData`.
  - `QuoteTextToolbarTest` (plain JVM, fake `TextToolbar` delegate recording calls):
    - `showMenu_forwardsStandardCallbacksToDelegate`: the delegate receives the same four lambdas and the rect.
    - `showMenu_setsShareAvailableOnlyWhenCopyExists`: `shareAvailable` is false with `onCopyRequested = null`, true otherwise; `anchorRect` equals the given rect.
    - `triggerShare_invokesCopyThenShare_inOrder`: record call order in a list; expect `["copy", "share"]`.
    - `triggerShare_isNoOpWithoutCopy`: no share callback when no copy lambda was supplied.
    - `hide_forwardsToDelegateAndClearsState`: `anchorRect == null`, `shareAvailable == false`, delegate `hide()` called once.
    - `status_reflectsDelegate`.

- [ ] **Step 2: Run, confirm RED.**

- [ ] **Step 3: Implement**
  - `QuoteCardSharer`: `writePng` runs `withContext(ioDispatcher)`: `val dir = File(context.cacheDir, DIR_NAME).apply { mkdirs() }`; prune `dir.listFiles()` where `lastModified() < nowMillis - RETENTION_MS`; create `File(dir, "quote_${template.storageName}_${nowMillis}_${Random.nextInt(0, 100_000)}.png")`; `FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }` with `PNG_QUALITY = 100`; return the file. `buildShareIntent` uses `FileProvider.getUriForFile(context, context.getFileProvider(), file)` (existing extension in `ui/ext/ExtContext.kt`), builds the `ACTION_SEND` intent with `setType(MIME_PNG)`, `putExtra(EXTRA_STREAM, uri)`, `clipData = ClipData.newRawUri("", uri)`, `addFlags(FLAG_GRANT_READ_URI_PERMISSION)`, and returns `Intent.createChooser(send, chooserTitle)` plus `addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)` only because it is started with an application context.
  - `QuoteTextToolbar`: `showMenu` stores the four lambdas in private fields, sets `anchorRect = rect` and `shareAvailable = onCopyRequested != null`, then calls `delegate.showMenu(rect, onCopyRequested, onPasteRequested, onCutRequested, onSelectAllRequested)` unchanged. `hide()` clears the fields and state then calls `delegate.hide()`. `triggerShare()` runs the stored copy lambda (if any) and then `onShareQuote()`. `status` returns `delegate.status`.
- [ ] **Step 4: Run, confirm GREEN.**
- [ ] **Step 5: Commit** — `feat(quote): add card sharer and selection toolbar wrapper` (+ trailer).

---

### Task 4: `QuoteCardViewModel` (TDD)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/quote/QuoteCardViewModel.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/quote/QuoteCardUiState.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/di/QuoteModule.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/ui/page/home/read/quote/QuoteCardViewModelTest.kt`

**Interfaces:**
- Consumes: `QuoteCardRenderer.render`, `QuoteTextTruncator`, `QuoteCardSharer`, `QuoteCardSpec`, `QuoteCardTemplate`.
- Produces:
  ```kotlin
  sealed interface QuoteCardUiState {
      data object Idle : QuoteCardUiState
      data object Rendering : QuoteCardUiState
      data class Ready(val bitmap: Bitmap, val template: QuoteCardTemplate, val wasCut: Boolean) : QuoteCardUiState
      data class Error(@StringRes val messageRes: Int) : QuoteCardUiState
  }

  fun interface QuoteBitmapRenderer { fun render(spec: QuoteCardSpec, template: QuoteCardTemplate): Bitmap }

  @HiltViewModel
  class QuoteCardViewModel @Inject constructor(
      private val sharer: QuoteCardSharer,
      @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
      private val renderer: QuoteBitmapRenderer,
  ) : ViewModel() {
      val state: StateFlow<QuoteCardUiState>
      val selectedTemplate: StateFlow<QuoteCardTemplate>
      fun start(spec: QuoteCardSpec)
      fun selectTemplate(template: QuoteCardTemplate)
      suspend fun prepareShareIntent(chooserTitle: String): Intent?     // writes PNG, null on error (state becomes Error)
      fun dismiss()
  }
  ```
  A Hilt `@Module` (`QuoteModule`, `@InstallIn(SingletonComponent::class)`) provides `QuoteBitmapRenderer` as `QuoteBitmapRenderer(QuoteCardRenderer::render)` (file `app/src/main/java/com/mckimquyen/reader/infrastructure/di/QuoteModule.kt`, created in this task). Tests pass their own fake renderer.
  Error strings used: `R.string.quote_card_error` (added in Task 6; add the English line to `values/strings.xml` now so the build compiles).

- [ ] **Step 1: Write tests (RED)** — Robolectric or plain JVM with `StandardTestDispatcher`, a fake `QuoteBitmapRenderer` that returns a tiny `Bitmap` (use `@GraphicsMode(NATIVE)`), a mocked `QuoteCardSharer`:
  - starts as `Idle`; `start(spec)` → `Rendering` then `Ready(template = MINIMALIST, wasCut = false)`;
  - `start` with a 400-character quote → `Ready.wasCut == true`;
  - `selectTemplate(DARK_STUDIO)` re-renders with that template and `selectedTemplate` updates;
  - selecting a second template while the first render is suspended cancels the first (use a `CompletableDeferred` inside the fake renderer; the late result does not overwrite);
  - renderer throws → `Error(R.string.quote_card_error)`;
  - the previous bitmap is `recycle()`d when replaced (`bitmap.isRecycled`) and when `dismiss()` is called, state returns to `Idle`;
  - `prepareShareIntent` calls `sharer.writePng` and `sharer.buildShareIntent` and returns the intent; if `writePng` throws `IOException` it returns null and state is `Error`; it returns null without calling the sharer when state is not `Ready`.

- [ ] **Step 2: Run, confirm RED.**
- [ ] **Step 3: Implement** — mirror `OnThisDayViewModel`: a `renderJob?.cancel()` before each render, `viewModelScope.launch(defaultDispatcher)`, `try/catch` rethrowing `CancellationException`, `Log.e` then `Error`; when replacing `Ready`, `recycle()` the old bitmap *after* the new state is published; `onCleared()` recycles the current bitmap. `prepareShareIntent` is `withContext` free (it calls the sharer, which already switches to IO).
- [ ] **Step 4: Run, confirm GREEN.**
- [ ] **Step 5: Commit** — `feat(quote): add QuoteCardViewModel and state` (+ trailer).

---

### Task 5: UI — sheet content, share chip and Reader wiring (TDD)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/quote/QuoteCardSheetContent.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/ui/component/quote/QuoteShareChip.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/Content.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingPage.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModel.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/ui/page/home/read/quote/QuoteCardSheetContentTest.kt`

**Interfaces:**
- Consumes: Tasks 1–4.
- Produces:
  ```kotlin
  const val QUOTE_SHEET_PREVIEW_TAG = "quote_sheet_preview"
  const val QUOTE_SHEET_CUT_NOTE_TAG = "quote_sheet_cut_note"
  const val QUOTE_SHEET_SHARE_TAG = "quote_sheet_share"
  const val QUOTE_SHEET_ERROR_TAG = "quote_sheet_error"
  const val QUOTE_SHEET_TEMPLATE_TAG_PREFIX = "quote_sheet_template_"

  @Composable
  fun QuoteCardSheetContent(
      state: QuoteCardUiState,
      selected: QuoteCardTemplate,
      onSelectTemplate: (QuoteCardTemplate) -> Unit,
      onShare: () -> Unit,
      onRetry: () -> Unit,
      modifier: Modifier = Modifier,
  )

  @Composable
  fun QuoteShareChip(toolbar: QuoteTextToolbar, modifier: Modifier = Modifier)

  // ReadingViewModel
  // ReadingUiState gains: val showQuoteSheet: Boolean = false, val quoteSpec: QuoteCardSpec? = null
  fun openQuoteSheet(quote: String)       // builds the spec from the loaded article; no-op if sanitize == null or no article
  fun dismissQuoteSheet()
  ```

- [ ] **Step 1: Write widget tests (RED)** — `QuoteCardSheetContentTest` (Robolectric `createComposeRule`, `@Config(sdk = [34], application = Application::class)`, `@GraphicsMode(NATIVE)`):
  - `Ready` state shows the preview node, three template chips with their localized names ("Minimalist", "Gradient", "Dark Studio"), and the Share button; no cut note when `wasCut = false`.
  - `Ready(wasCut = true)` shows the cut note ("Quote was shortened").
  - clicking a template chip calls `onSelectTemplate` with that template; clicking Share calls `onShare` once.
  - `Rendering` shows a progress indicator and disables Share.
  - `Error` shows the error tag with the message and a Retry button that calls `onRetry`.
  - The selected chip exposes `selected = true` semantics (`SemanticsProperties.Selected`).
  Add `ReadingViewModel` tests to the existing `ReadingViewModelReadAtTest`-style new file `ReadingViewModelQuoteTest`: `openQuoteSheet("  hello  world ")` sets `showQuoteSheet = true` and `quoteSpec.quote == "hello world"` with the loaded article's feed name, author and title; `openQuoteSheet("   ")` and `openQuoteSheet` with no loaded article change nothing; `dismissQuoteSheet()` clears both fields.

- [ ] **Step 2: Run, confirm RED.**

- [ ] **Step 3: Implement**
  - `ReadingViewModel`: add the two fields to `ReadingUiState` and the two functions (pattern of `dismissSummary`).
  - `Content.kt`: add a parameter `onShareQuote: (String) -> Unit = {}`. Inside `Content`, create `val platformToolbar = LocalTextToolbar.current`, `val clipboard = LocalClipboardManager.current`, and
    ```kotlin
    val quoteToolbar = remember(platformToolbar, clipboard, onShareQuote) {
        QuoteTextToolbar(platformToolbar) {
            val selected = QuoteSelection.sanitize(clipboard.getText()?.text)
            if (selected != null) onShareQuote(selected)
        }
    }
    ```
    Wrap the existing `SelectionContainer` with `CompositionLocalProvider(LocalTextToolbar provides quoteToolbar)` and add `QuoteShareChip(quoteToolbar)` in a `Box` overlay above the `LazyColumn` so the chip appears next to the platform toolbar. Save and restore the clipboard around the share: `QuoteTextToolbar.triggerShare()` runs inside a small helper in `Content.kt` that stores `clipboard.getClip()` before `onCopyRequested()` and calls `clipboard.setClip(previous)` after reading the text (guard `previous == null`). Keep `DisableSelection` around `Metadata` unchanged.
  - `QuoteShareChip`: a Compose `Popup` anchored at `toolbar.anchorRect` (top-left offset above the rect) showing a `SuggestionChip`/`AssistChip` with the localized "Share quote" label and tag `quote_share_chip`; visible only while `toolbar.status == TextToolbarStatus.Shown && toolbar.shareAvailable`. It calls `toolbar.triggerShare()` then `toolbar.hide()`. `QuoteTextToolbar.anchorRect` and `shareAvailable` are already Compose state (Task 3), so the chip recomposes by itself.
  - `ReadingPage.kt`: pass `onShareQuote = { readingViewModel.openQuoteSheet(it) }` into `Content`; extend `isAnySheetOpen` with `showQuoteSheet`; in the `BottomDrawer` `sheetContent` branch order add `readingUiState.showQuoteSheet -> QuoteCardSheetContent(...)` with a `hiltViewModel<QuoteCardViewModel>()`: call `quoteViewModel.start(spec)` in a `LaunchedEffect(readingUiState.quoteSpec)`, collect `state` and `selectedTemplate`, `onShare = { scope.launch { quoteViewModel.prepareShareIntent(chooserTitle)?.let(context::startActivity) } }`, `onRetry = { quoteViewModel.start(spec) }`; dismiss handling in the two existing `LaunchedEffect`/`BackHandler` blocks calls `readingViewModel.dismissQuoteSheet()` and `quoteViewModel.dismiss()`.
  - `QuoteCardSheetContent`: `Column` with the preview (`Image(bitmap.asImageBitmap())` tagged `QUOTE_SHEET_PREVIEW_TAG`, fixed `aspectRatio(1080f/1350f)`), a `Row` of three `FilterChip`s (tag prefix + `template.storageName`, `selected` flag), the cut note, and a `Button` Share. Use only components present in `material3:1.0.1` (`FilterChip`/`AssistChip` exist as experimental there: opt in with `@OptIn(ExperimentalMaterial3Api::class)`).

- [ ] **Step 4: Run, confirm GREEN** — `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.page.home.read.*" --tests "com.mckimquyen.reader.ui.component.quote.*"`, then `./gradlew compileDevDebugKotlin -q`.
- [ ] **Step 5: Commit** — two commits: `feat(quote): add quote card sheet content and share chip` and `feat(reader): wire quote sharing into Reader selection` (+ trailer).

---

### Task 6: Localization across all 38 locales

**Files:**
- Modify: `app/src/main/res/values/strings.xml` and every `app/src/main/res/values-*/strings.xml` that has a `strings.xml` (37 files).

**Keys (English source):**
- `quote_card_title` = "Share quote"
- `quote_card_share` = "Share"
- `quote_card_template_minimalist` = "Minimalist"
- `quote_card_template_gradient` = "Gradient"
- `quote_card_template_dark` = "Dark Studio"
- `quote_card_cut_note` = "Quote was shortened"
- `quote_card_error` = "Could not create the quote card. Tap to retry."
- `quote_card_chooser_title` = "Share quote card"
(`retry` and `back` already exist; reuse them. If `quote_card_error` was added in Task 4, move it into this block without duplicating it.)

- [ ] **Step 1:** Add the keys to `values/strings.xml`.
- [ ] **Step 2:** Write a throw-away Python script (not committed; same approach as the IDEA-07 and IDEA-01 scripts, with `# -*- coding: utf-8 -*-` first) holding real translations for each of the 37 locales (`ar az bg ca cs-rCZ da de-rDE es-rES eu-rES fa fr-rFR hi-rIN hu in-rID it-rIT iw ja kn lzh ml my nb-rNO nl nn pl-rPL pt-rBR pt ro ru-rRU sl sr sv tr uk vi zh-rCN zh-rTW`), asserting the locale set equals the directories on disk and that no key already exists, escaping `'` as `\'`, inserting before `</resources>`. Vietnamese keeps full diacritics (for example "Chia sẻ trích dẫn", "Chia sẻ", "Tối giản", "Chuyển sắc", "Dark Studio", "Đoạn trích đã được rút gọn", "Không thể tạo thẻ trích dẫn. Chạm để thử lại.", "Chia sẻ thẻ trích dẫn").
- [ ] **Step 3:** Validate: every file parses (`python3 -c "import xml.dom.minidom as m; m.parse(path)"`); `rg -L "quote_card_cut_note" app/src/main/res/values*/strings.xml` prints nothing.
- [ ] **Step 4:** `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.pref.StringResourceParityTest" processDevDebugResources -q` → PASS.
- [ ] **Step 5: Commit** — `feat(i18n): localize quote card across all 38 maintained locales` (+ trailer).

---

### Task 7: Device verification on the Tecno, full suite and docs

**Files:**
- Create: `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/read/quote/QuoteCardInstrumentationTest.kt`
- Create: `doc/task/done/IDEA-03_quote-card-social-share_DONE.md`
- Modify: `doc/feature.md`
- Delete: `doc/task/todo/IDEA-03_quote-card-social-share.md`

- [ ] **Step 1: Instrumentation tests** — `QuoteCardInstrumentationTest` (`AndroidJUnit4`, `createAndroidComposeRule<ComponentActivity>()`), three tests:
  1. `sharerUriIsReadableAndDecodesAs1080x1350`: render a card with `QuoteCardRenderer`, `QuoteCardSharer(context, Dispatchers.IO).writePng(...)`, `buildShareIntent`, then `context.contentResolver.openInputStream(uri)` decodes through `BitmapFactory.decodeStream` to 1080×1350 (grants are not needed in-process).
  2. `sheetContentClearsSystemBarInsets`: same inset-polling pattern as `OnThisDayEdgeToEdgeTest` hosting `QuoteCardSheetContent` in a window with `setDecorFitsSystemWindows(false)`; assert the preview top ≥ status inset and the Share button bottom ≤ decor height − navigation inset.
  3. `customToolbarKeepsLongPressSelectionAndCopy`: like `EInkReaderInteractionQaTest.centerLongPressKeepsRealTextSelectionAndCopy` but with `QuoteTextToolbar(fakePlatformToolbar) { shared.set(true) }` provided through `LocalTextToolbar`; long-press real prose, `waitUntil` the fake delegate receives `onCopyRequested`, run `triggerShare()`, assert the clipboard contains the selected text and the share callback ran.
- [ ] **Step 2: Run on the Tecno only**
  ```bash
  ./gradlew -PtestRunner=com.mckimquyen.reader.EInkUiTestRunner assembleDevDebug assembleDevDebugAndroidTest
  adb -s 115333744A005844 install -r "app/build/outputs/apk/dev/debug/<apk>.apk"
  adb -s 115333744A005844 install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
  adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.reader.ui.page.home.read.quote.QuoteCardInstrumentationTest com.mckimquyen.reader.test/com.mckimquyen.reader.EInkUiTestRunner
  ```
  Expected `OK (3 tests)`. The Tecno may lose adb authorization: report a "device not found" as a connection problem, not a code failure. Do not run `connectedDevDebugAndroidTest`.
- [ ] **Step 3: Full suite and builds** — `./gradlew testDevDebugUnitTest assembleDevDebug compileDevDebugAndroidTestKotlin`; sum the XML results (999 before this feature; expect more, 0 failures/errors/skipped). Do not claim lint passed.
- [ ] **Step 4: Live smoke on the Tecno** — rebuild without `-PtestRunner`, install, launch `com.mckimquyen.reader` (no `.dev` suffix). Before every tap run `adb -s 115333744A005844 shell dumpsys activity activities | grep topResumedActivity`; take a screenshot (`adb -s 115333744A005844 exec-out screencap -p`) after every step; do not chain several taps in one command. Steps: open an article; long-press a sentence in the body; confirm the platform toolbar and the "Share quote" chip appear; tap the chip; confirm the sheet shows the preview and three chips; switch each template and confirm the preview changes; tap Share and confirm the system chooser lists targets; dismiss the chooser without sending anything; close the sheet; confirm the clipboard content from before the test is restored (put a known marker into the clipboard first with `adb shell cmd clipboard` or by copying text, and read it back after). If a full-screen ad covers the UI, stop, tell the user, wait for "done"; banners are skipped. Remove cached cards afterwards with `adb -s 115333744A005844 shell run-as com.mckimquyen.reader rm -rf cache/quote_cards`.
- [ ] **Step 5: Docs** — write `doc/task/done/IDEA-03_quote-card-social-share_DONE.md` (status, commit hashes from `git log`, implementation, measured verification numbers, the Known limits above plus the Android 12+ clipboard notice); in `doc/feature.md` add IDEA-03 under "Recently implemented" with the real test counts; `git rm doc/task/todo/IDEA-03_quote-card-social-share.md`.
- [ ] **Step 6: Commit** — `test(quote): add quote card instrumentation tests` and `docs: finalize IDEA-03 Quote Card completion report` (+ trailer). Do not push unless the user asks.
