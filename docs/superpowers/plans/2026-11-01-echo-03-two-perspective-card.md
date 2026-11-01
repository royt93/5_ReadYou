# ECHO-03 Two-Headline Card Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the reader export a 9:16 image that puts the article being read above one headline from another saved feed that ECHO-02 found, and share it with `#RSSCatHub #Debate` in the share text.

**Architecture:** A pure `DualPerspectiveSpec` (+ truncator) feeds a pure `android.graphics` `DualCardRenderer` (1080x1920). `DualCardViewModel` mirrors `QuoteCardViewModel` (Idle/Rendering/Ready/Error, bitmap recycling) and reuses `QuoteCardSharer`, which gains an optional share text and file prefix with unchanged defaults. `ContrastingCoverageCard` rows get an "export" button; `ReadingViewModel` holds the sheet state and `ReadingPage` renders it in the existing `BottomDrawer`.

**Tech Stack:** Kotlin, `android.graphics`, Jetpack Compose Material3 1.0.1, Hilt, JUnit4, MockK, Robolectric, AndroidX instrumentation.

## Global Constraints

- Branch `dev` only; no worktrees. Commit after every task; end each commit message with `Co-Authored-By: Claude Code <noreply@anthropic.com>`. Never commit `.idea/*`. Push only when the owner asks.
- Room stays at version 20. No AI, network, API key, ad, IAP or money feature.
- UI and image wording says "two perspectives / two headlines". The words "rebuttal" and "debate" never appear on the image or in UI strings; `#Debate` appears only in the share text (`EXTRA_TEXT`).
- The card is not a fact-check; the image carries the line "Two headlines from my reading archive. Not a fact-check."
- Compose Material3 is 1.0.1 (no `ModalBottomSheet`; the reader uses its own `BottomDrawer`).
- Hilt: exactly one `@Inject` constructor per `@HiltViewModel`.
- `ReadingViewModel` tests inject `defaultDispatcher =` the test dispatcher so `init` observers never hop to `Dispatchers.Default`.
- No magic numbers: sizes, colors and limits are named constants.
- Device lock: Pixel 7 Pro serial `2B051FDH3006MU` only (`adb -s 2B051FDH3006MU ...`). Never use `connectedDevDebugAndroidTest`.
- Before smoke, back up Room `databases/Reader`, `Reader-wal`, `Reader-shm` AND `files/datastore/settings.preferences_pb`; require each non-empty and `PRAGMA integrity_check` = `ok` on a copy. Seed the second feed ONLY in a DB copy. Restore all four with `adb shell "run-as com.mckimquyen.reader sh -c 'cat > PATH'" < local`, verify `cmp` on every file, read Room from the device with all three files, never delete the DataStore.
- Before every UI tap check `topResumedActivity`. Close a full-screen `AdActivity` only with the labelled control "Tiếp tục đến ứng dụng" found in a FRESH `uiautomator dump` (`rm /sdcard/ui.xml` first); never tap ad creative or coordinates from an old dump; report that ads appeared.
- Gradle swallows test stdout: read real failures from `app/build/test-results/testDevDebugUnitTest/*.xml`.

---

## File map

- Create `app/src/main/java/com/mckimquyen/reader/domain/steelman/DualPerspectiveSpec.kt` and `DualPerspectiveTruncator.kt`.
- Create `app/src/main/java/com/mckimquyen/reader/infrastructure/quote/DualCardRenderer.kt`.
- Modify `app/src/main/java/com/mckimquyen/reader/infrastructure/quote/QuoteCardSharer.kt` (`shareText`, `filePrefix`).
- Create `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/steelman/DualCardViewModel.kt`, `DualCardUiState.kt`, `DualCardSheetContent.kt`.
- Modify `ui/page/home/read/steelman/ContrastingCoverageCard.kt`, `Content.kt`, `ReadingPage.kt`, `ReadingViewModel.kt`.
- Modify `app/src/main/res/values*/strings.xml` (38 files).
- Tests mirror each file under `app/src/test/...`; add `app/src/androidTest/.../DualCardDeviceTest.kt`.
- Docs: `doc/task/done/ECHO-03_dual-perspective-quote-card_DONE.md`, `doc/feature.md`, `doc/task/todo/16_ECHOCHAMBER_AND_BIAS_COMPASS.md`; `git rm doc/task/todo/ECHO-03_dual-perspective-quote-card.md`.

## Interfaces

```kotlin
data class DualPerspectiveSpec(
    val topTitle: String, val topSource: String, val topDate: String,
    val bottomTitle: String, val bottomSource: String, val bottomDate: String,
    val cue: String,
)
object DualPerspectiveTruncator {
    const val MAX_HEADLINE_CHARS = 140
    data class Result(val text: String, val wasCut: Boolean)
    fun truncate(raw: String): Result
}
object DualCardRenderer { const val CARD_WIDTH = 1080; const val CARD_HEIGHT = 1920; fun render(spec: DualPerspectiveSpec): Bitmap }
fun interface DualBitmapRenderer { suspend fun render(spec: DualPerspectiveSpec): Bitmap }
sealed interface DualCardUiState { Idle; Rendering; Ready(bitmap, wasCut); Error(messageRes) }
```

`QuoteCardSharer.writePng(bitmap, template, nowMillis)` is unchanged; add `writePng(bitmap, filePrefix, nowMillis)` and `buildShareIntent(file, chooserTitle, shareText: String? = null)`.

---

### Task 1: Pure spec and truncator

**Files:** create `DualPerspectiveSpec.kt`, `DualPerspectiveTruncator.kt`, `app/src/test/java/com/mckimquyen/reader/domain/steelman/DualPerspectiveTruncatorTest.kt`.

- [ ] **Step 1: Write the failing test.**

```kotlin
package com.mckimquyen.reader.domain.steelman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DualPerspectiveTruncatorTest {
    @Test fun shortHeadline_isKept_andNotMarkedCut() {
        val r = DualPerspectiveTruncator.truncate("  Starlink warning  ")
        assertEquals("Starlink warning", r.text)
        assertFalse(r.wasCut)
    }

    @Test fun longHeadline_isCutAtWordBoundary_withEllipsis() {
        val r = DualPerspectiveTruncator.truncate("word ".repeat(100))
        assertTrue(r.wasCut)
        assertTrue(r.text.endsWith("…"))
        assertTrue(r.text.length <= DualPerspectiveTruncator.MAX_HEADLINE_CHARS + 1)
        assertFalse(r.text.dropLast(1).endsWith(" "))
    }

    @Test fun headlineExactlyAtLimit_isNotCut() {
        val text = "a".repeat(DualPerspectiveTruncator.MAX_HEADLINE_CHARS)
        val r = DualPerspectiveTruncator.truncate(text)
        assertEquals(text, r.text)
        assertFalse(r.wasCut)
    }

    @Test fun blankAndWhitespaceInside_areCollapsed() {
        assertEquals("", DualPerspectiveTruncator.truncate("   ").text)
        assertEquals("a b c", DualPerspectiveTruncator.truncate("a \n  b\t c").text)
    }

    @Test fun singleHugeWord_isHardCut() {
        val r = DualPerspectiveTruncator.truncate("x".repeat(500))
        assertTrue(r.wasCut)
        assertEquals(DualPerspectiveTruncator.MAX_HEADLINE_CHARS + 1, r.text.length)
    }
}
```

- [ ] **Step 2: Red.** `./gradlew testDevDebugUnitTest --tests "*DualPerspectiveTruncatorTest"` → unresolved reference.
- [ ] **Step 3: Implement.**

```kotlin
package com.mckimquyen.reader.domain.steelman

/** Data for one two-headline card. Dates are already formatted by the caller. */
data class DualPerspectiveSpec(
    val topTitle: String,
    val topSource: String,
    val topDate: String,
    val bottomTitle: String,
    val bottomSource: String,
    val bottomDate: String,
    val cue: String,
)
```

```kotlin
package com.mckimquyen.reader.domain.steelman

/** Keeps a headline short enough to sit on the card; cuts at a word boundary when it can. */
object DualPerspectiveTruncator {
    const val MAX_HEADLINE_CHARS = 140
    private const val ELLIPSIS = "…"
    private val WHITESPACE = Regex("\\s+")

    data class Result(val text: String, val wasCut: Boolean)

    fun truncate(raw: String): Result {
        val clean = raw.trim().replace(WHITESPACE, " ")
        if (clean.length <= MAX_HEADLINE_CHARS) return Result(clean, false)
        val head = clean.take(MAX_HEADLINE_CHARS)
        val lastSpace = head.lastIndexOf(' ')
        val cut = if (lastSpace > 0) head.substring(0, lastSpace) else head
        return Result(cut.trimEnd() + ELLIPSIS, true)
    }
}
```

- [ ] **Step 4: Green.** Same command; expect 5/5 from the XML.
- [ ] **Step 5: Commit.** `git add app/src/main/java/com/mckimquyen/reader/domain/steelman/DualPerspective* app/src/test/java/com/mckimquyen/reader/domain/steelman/DualPerspectiveTruncatorTest.kt && git commit -m "feat(echo-03): add two-headline spec and truncator"` (+ trailer).

---

### Task 2: `DualCardRenderer` (1080x1920, android.graphics)

**Files:** create `DualCardRenderer.kt`, `app/src/test/java/com/mckimquyen/reader/infrastructure/quote/DualCardRendererTest.kt`.

- [ ] **Step 1: Write the failing test.**

```kotlin
package com.mckimquyen.reader.infrastructure.quote

import android.graphics.Bitmap
import com.mckimquyen.reader.domain.steelman.DualPerspectiveSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DualCardRendererTest {
    private val spec = DualPerspectiveSpec(
        topTitle = "Musk says SpaceX restricted Ukraine's Starlink connection",
        topSource = "BBC News", topDate = "25 Oct 2026",
        bottomTitle = "Scientists warn Starlink constellation disrupts research",
        bottomSource = "Science Wire", bottomDate = "1 Nov 2026", cue = "warn",
    )

    private fun nonBackground(bitmap: Bitmap, x0: Int, y0: Int, x1: Int, y1: Int): Int {
        val background = bitmap.getPixel(x0, y0)
        var count = 0
        for (x in x0..x1) for (y in y0..y1) if (bitmap.getPixel(x, y) != background) count++
        return count
    }

    @Test fun render_hasExactNineBySixteenSize() {
        val bitmap = DualCardRenderer.render(spec)
        assertEquals(1080, bitmap.width)
        assertEquals(1920, bitmap.height)
    }

    @Test fun halves_haveDifferentBackgrounds() {
        val bitmap = DualCardRenderer.render(spec)
        assertNotEquals(bitmap.getPixel(8, 8), bitmap.getPixel(8, DualCardRenderer.CARD_HEIGHT - 8))
    }

    @Test fun bothHalves_drawHeadlinePixels() {
        val bitmap = DualCardRenderer.render(spec)
        val half = DualCardRenderer.CARD_HEIGHT / 2
        assertTrue(nonBackground(bitmap, 90, 200, 990, half - 200) > 0)
        assertTrue(nonBackground(bitmap, 90, half + 200, 990, DualCardRenderer.CARD_HEIGHT - 300) > 0)
    }

    @Test fun footer_drawsDisclaimerPixels() {
        val bitmap = DualCardRenderer.render(spec)
        val y1 = DualCardRenderer.CARD_HEIGHT - 20
        assertTrue(nonBackground(bitmap, 90, y1 - 120, 990, y1) > 0)
    }

    @Test fun veryLongHeadlinesAndEmptyFields_doNotCrash() {
        val long = spec.copy(topTitle = "word ".repeat(300), bottomTitle = "word ".repeat(300))
        assertEquals(1920, DualCardRenderer.render(long).height)
        val empty = DualPerspectiveSpec("", "", "", "", "", "", "")
        assertEquals(1080, DualCardRenderer.render(empty).width)
    }

    @Test fun unicodeHeadlines_doNotCrash() {
        val unicode = spec.copy(topTitle = "تحذير 警告 ⚠️ Starlink", bottomTitle = "Cảnh báo về Starlink 🚀")
        assertEquals(1080, DualCardRenderer.render(unicode).width)
    }

    @Test fun longHeadline_staysInsideSideMargins() {
        val bitmap = DualCardRenderer.render(spec.copy(topTitle = "word ".repeat(300)))
        for (y in 200..800) {
            val edge = bitmap.getPixel(5, y)
            for (x in 0 until 40) assertEquals("lề trái bị vẽ tràn tại ($x,$y)", edge, bitmap.getPixel(x, y))
        }
    }
}
```

- [ ] **Step 2: Red.** `./gradlew testDevDebugUnitTest --tests "*DualCardRendererTest"` → unresolved reference.
- [ ] **Step 3: Implement.** The image text must not contain "rebuttal" or "debate".

```kotlin
package com.mckimquyen.reader.infrastructure.quote

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import com.mckimquyen.reader.domain.steelman.DualPerspectiveSpec
import com.mckimquyen.reader.domain.steelman.DualPerspectiveTruncator

/** Draws the 1080x1920 two-headline card with android.graphics only; pure, needs no Context. */
object DualCardRenderer {
    const val CARD_WIDTH = 1080
    const val CARD_HEIGHT = 1920
    const val WATERMARK = "RSS Cat Hub"
    const val DISCLAIMER = "Two headlines from my reading archive. Not a fact-check."

    private const val HALF_HEIGHT = CARD_HEIGHT / 2
    private const val MARGIN = 90
    private const val CONTENT_WIDTH = CARD_WIDTH - 2 * MARGIN
    private const val LABEL_TOP_OFFSET = 110f
    private const val HEADLINE_TOP_OFFSET = 220f
    private const val META_BOTTOM_OFFSET = 190f
    private const val LABEL_SIZE = 36f
    private const val HEADLINE_SIZE = 66f
    private const val META_SIZE = 38f
    private const val CUE_SIZE = 34f
    private const val LINE_SPACING_MULT = 1.2f
    private const val MAX_HEADLINE_LINES = 7
    private const val FOOTER_BASELINE_OFFSET = 70f
    private const val DISCLAIMER_SIZE = 30f
    private const val WATERMARK_SIZE = 30f
    private const val DISCLAIMER_LINES = 2
    private const val DISCLAIMER_TOP_OFFSET = 150f

    private const val TOP_BG = 0xFF0B0B0F.toInt()
    private const val TOP_TEXT = 0xFFF5F5F5.toInt()
    private const val TOP_ACCENT = 0xFF39FF14.toInt()
    private const val TOP_MUTED = 0xFF9A9AA5.toInt()
    private const val BOTTOM_BG = 0xFFFFFFFF.toInt()
    private const val BOTTOM_TEXT = 0xFF14141A.toInt()
    private const val BOTTOM_ACCENT = 0xFFE5173F.toInt()
    private const val BOTTOM_MUTED = 0xFF6B6B75.toInt()

    private const val LABEL_TOP = "NOW READING"
    private const val LABEL_BOTTOM = "ANOTHER SAVED SOURCE"
    private const val CUE_PREFIX = "wording: "

    private data class Palette(val bg: Int, val text: Int, val accent: Int, val muted: Int)

    private val TOP = Palette(TOP_BG, TOP_TEXT, TOP_ACCENT, TOP_MUTED)
    private val BOTTOM = Palette(BOTTOM_BG, BOTTOM_TEXT, BOTTOM_ACCENT, BOTTOM_MUTED)

    fun render(spec: DualPerspectiveSpec): Bitmap {
        val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawHalf(canvas, 0f, TOP, LABEL_TOP, spec.topTitle, "${spec.topSource} · ${spec.topDate}", null)
        drawHalf(
            canvas, HALF_HEIGHT.toFloat(), BOTTOM, LABEL_BOTTOM, spec.bottomTitle,
            "${spec.bottomSource} · ${spec.bottomDate}", spec.cue.takeIf { it.isNotBlank() },
        )
        drawFooter(canvas)
        return bitmap
    }

    private fun drawHalf(canvas: Canvas, top: Float, p: Palette, label: String, title: String, meta: String, cue: String?) {
        val fill = Paint().apply { color = p.bg }
        canvas.drawRect(0f, top, CARD_WIDTH.toFloat(), top + HALF_HEIGHT, fill)
        drawLine(canvas, label, p.accent, LABEL_SIZE, true, top + LABEL_TOP_OFFSET)

        val text = DualPerspectiveTruncator.truncate(title).text
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.text
            textSize = HEADLINE_SIZE
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, CONTENT_WIDTH)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, LINE_SPACING_MULT)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setMaxLines(MAX_HEADLINE_LINES)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(MARGIN.toFloat(), top + HEADLINE_TOP_OFFSET)
        layout.draw(canvas)
        canvas.restore()

        val metaTop = top + HALF_HEIGHT - META_BOTTOM_OFFSET
        drawLine(canvas, meta, p.muted, META_SIZE, false, metaTop)
        if (cue != null) drawLine(canvas, CUE_PREFIX + cue, p.accent, CUE_SIZE, true, metaTop + META_SIZE * 1.4f)
    }

    private fun drawFooter(canvas: Canvas) {
        val disclaimer = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BOTTOM_MUTED
            textSize = DISCLAIMER_SIZE
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val layout = StaticLayout.Builder.obtain(DISCLAIMER, 0, DISCLAIMER.length, disclaimer, CONTENT_WIDTH)
            .setMaxLines(DISCLAIMER_LINES)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(MARGIN.toFloat(), CARD_HEIGHT - DISCLAIMER_TOP_OFFSET)
        layout.draw(canvas)
        canvas.restore()

        val watermark = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BOTTOM_ACCENT
            textSize = WATERMARK_SIZE
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(WATERMARK, (CARD_WIDTH - MARGIN).toFloat(), CARD_HEIGHT - FOOTER_BASELINE_OFFSET, watermark)
    }

    private fun drawLine(canvas: Canvas, text: String, color: Int, size: Float, bold: Boolean, top: Float) {
        if (text.isBlank()) return
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, CONTENT_WIDTH)
            .setMaxLines(1)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .build()
        canvas.save()
        canvas.translate(MARGIN.toFloat(), top)
        layout.draw(canvas)
        canvas.restore()
    }
}
```

The `bothHalves_drawHeadlinePixels` test samples y in `200..half-200` and `half+200..CARD_HEIGHT-300`; if a band misses the text because of the layout offsets above, adjust only the test band, not the visual constants, and record the actual band in the commit message.

- [ ] **Step 4: Green.** Run the test; read the XML; expect 7/7. If `longHeadline_staysInsideSideMargins` fails, the cause is `CONTENT_WIDTH`/`MARGIN`; fix the renderer.
- [ ] **Step 5: Commit.** `feat(echo-03): draw the 9:16 two-headline card` (+ trailer).

---

### Task 3: `QuoteCardSharer` share text and file prefix

**Files:** modify `QuoteCardSharer.kt`, `QuoteCardSharerTest.kt`.

- [ ] **Step 1: Add failing tests** to `QuoteCardSharerTest`:

```kotlin
    @Test
    fun writePng_withCustomPrefix_usesThatPrefixInTheFileName() = runBlocking {
        val file = sharer.writePng(bitmap(), "dual", nowMillis = NOW)
        assertTrue(file.name.startsWith("dual_"))
        assertEquals(File(context.cacheDir, QuoteCardSharer.DIR_NAME), file.parentFile)
    }

    @Test
    fun buildShareIntent_withShareText_addsExtraText() = runBlocking {
        val file = sharer.writePng(bitmap(), "dual", nowMillis = NOW)
        val chooser = sharer.buildShareIntent(file, "Share card", "#RSSCatHub #Debate")
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals("#RSSCatHub #Debate", send.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun buildShareIntent_withoutShareText_hasNoExtraText() = runBlocking {
        val file = sharer.writePng(bitmap(), QuoteCardTemplate.MINIMALIST, nowMillis = NOW)
        val chooser = sharer.buildShareIntent(file, "Share quote card")
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(null, send.getStringExtra(Intent.EXTRA_TEXT))
    }
```

- [ ] **Step 2: Red.** `./gradlew testDevDebugUnitTest --tests "*QuoteCardSharerTest"` → no `writePng(Bitmap, String, Long)` / extra argument.
- [ ] **Step 3: Implement** in `QuoteCardSharer`:

```kotlin
    suspend fun writePng(
        bitmap: Bitmap,
        template: QuoteCardTemplate,
        nowMillis: Long = System.currentTimeMillis(),
    ): File = writePng(bitmap, "quote_${template.storageName}", nowMillis)

    suspend fun writePng(
        bitmap: Bitmap,
        filePrefix: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): File = withContext(ioDispatcher) {
        val dir = File(context.cacheDir, DIR_NAME).apply { mkdirs() }
        dir.listFiles()?.forEach { if (it.lastModified() < nowMillis - RETENTION_MS) it.delete() }
        val file = File(dir, "${filePrefix}_${nowMillis}_${Random.nextInt(0, NAME_RANDOM_BOUND)}.png")
        FileOutputStream(file).use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)) { "PNG compress failed" }
        }
        file
    }

    fun buildShareIntent(file: File, chooserTitle: String, shareText: String? = null): Intent {
        val uri = FileProvider.getUriForFile(context, context.getFileProvider(), file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_PNG
            putExtra(Intent.EXTRA_STREAM, uri)
            if (shareText != null) putExtra(Intent.EXTRA_TEXT, shareText)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
```

Delete the old single `writePng` body (it moves into the prefix overload). The existing `QuoteCardViewModelTest` mocks `sharer.writePng(any(), QuoteCardTemplate.MINIMALIST, any())` and `buildShareIntent(file, "title")`; because `buildShareIntent` gains a defaulted third parameter, MockK stubs on a 2-argument call must still match, so run that test too.
- [ ] **Step 4: Green.** `./gradlew testDevDebugUnitTest --tests "*QuoteCardSharerTest" --tests "*QuoteCardViewModelTest"`; expect all pass. If `QuoteCardViewModelTest` breaks on the new default parameter, change only its stub to `buildShareIntent(file, "title", any())` and note that in the commit message.
- [ ] **Step 5: Commit.** `feat(echo-03): let the shared card carry share text and a file prefix` (+ trailer).

---

### Task 4: Strings in 38 locales

**Files:** `app/src/main/res/values*/strings.xml`.

- [ ] **Step 1: Add the 8 strings.** Save as `/tmp/echo03_strings.py`, run `python3 /tmp/echo03_strings.py` from the repo root (safe to re-run).

```python
# -*- coding: utf-8 -*-
import glob

EN = {
    'echo_dual_export': 'Export card',
    'echo_dual_export_desc': 'Export a two-headline card with %1$s',
    'echo_dual_title': 'Share two headlines',
    'echo_dual_share': 'Share',
    'echo_dual_cut_note': 'A headline was shortened',
    'echo_dual_error': 'Could not create the card. Tap to retry.',
    'echo_dual_chooser_title': 'Share two-headline card',
    'echo_dual_share_text': '#RSSCatHub #Debate',
}
VI = {
    'echo_dual_export': 'Xuất thẻ',
    'echo_dual_export_desc': 'Xuất thẻ hai tiêu đề với %1$s',
    'echo_dual_title': 'Chia sẻ hai tiêu đề',
    'echo_dual_share': 'Chia sẻ',
    'echo_dual_cut_note': 'Một tiêu đề đã được rút gọn',
    'echo_dual_error': 'Không tạo được thẻ. Chạm để thử lại.',
    'echo_dual_chooser_title': 'Chia sẻ thẻ hai tiêu đề',
    'echo_dual_share_text': '#RSSCatHub #Debate',
}

def esc(v):
    return v.replace('&', '&amp;').replace("'", "\\'").replace('"', '\\"')

files = glob.glob('app/src/main/res/values*/strings.xml')
for f in files:
    values = VI if f.endswith('values-vi/strings.xml') else EN
    text = open(f, encoding='utf-8').read()
    missing = {k: v for k, v in values.items() if f'<string name="{k}"' not in text}
    if not missing:
        continue
    assert text.count('</resources>') == 1, f
    block = '\n    <!-- ECHO-03 two-headline card -->\n' + ''.join(
        f'    <string name="{k}">{esc(v)}</string>\n' for k, v in missing.items())
    open(f, 'w', encoding='utf-8').write(text.replace('</resources>', block + '</resources>'))
print(len(files), 'locale files')
```

`echo_dual_share_text` is the one string that holds `#Debate`; it is share text, never drawn on the image or shown as UI. Expected output `38 locale files`; `grep -L echo_dual_chooser_title app/src/main/res/values*/strings.xml | wc -l` must print `0`.
- [ ] **Step 2: Verify.** `./gradlew testDevDebugUnitTest --tests "*StringResourceParityTest"`; read the XML, expect 6/6.
- [ ] **Step 3: Guard test** `app/src/test/java/com/mckimquyen/reader/infrastructure/quote/DualCardWordingTest.kt`:

```kotlin
package com.mckimquyen.reader.infrastructure.quote

import org.junit.Assert.assertFalse
import org.junit.Test

class DualCardWordingTest {
    @Test fun imageText_neverSaysRebuttalOrDebate() {
        val drawn = listOf(DualCardRenderer.DISCLAIMER, DualCardRenderer.WATERMARK).joinToString(" ").lowercase()
        assertFalse(drawn.contains("rebuttal"))
        assertFalse(drawn.contains("debate"))
    }
}
```
- [ ] **Step 4: Commit.** `feat(echo-03): add two-headline card strings across 38 locales` (+ trailer).

---

### Task 5: `DualCardViewModel` and `DualCardSheetContent`

**Files:** create `DualCardUiState.kt`, `DualBitmapRenderer.kt` (in `infrastructure/quote/`), `DualCardViewModel.kt`, `DualCardSheetContent.kt`; tests `DualCardViewModelTest.kt`, `DualCardSheetContentTest.kt`.

- [ ] **Step 1: Write the failing ViewModel test** (mirror `QuoteCardViewModelTest`: `StandardTestDispatcher`, `@RunWith(RobolectricTestRunner::class) @Config(sdk=[34], manifest=Config.NONE) @GraphicsMode(NATIVE)`):

```kotlin
package com.mckimquyen.reader.ui.page.home.read.steelman

import android.content.Intent
import android.graphics.Bitmap
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.steelman.DualPerspectiveSpec
import com.mckimquyen.reader.infrastructure.quote.DualBitmapRenderer
import com.mckimquyen.reader.infrastructure.quote.QuoteCardSharer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DualCardViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val sharer: QuoteCardSharer = mockk()
    private val spec = DualPerspectiveSpec("Top", "A", "d1", "Bottom", "B", "d2", "warn")

    @Before fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun renderer() = DualBitmapRenderer { Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888) }
    private fun vm(r: DualBitmapRenderer = renderer()) = DualCardViewModel(sharer, testDispatcher, r)

    @Test fun initialState_isIdle() { assertEquals(DualCardUiState.Idle, vm().state.value) }

    @Test fun start_rendersAndEmitsReady() = runTest(testDispatcher) {
        val vm = vm()
        vm.start(spec)
        assertEquals(DualCardUiState.Rendering, vm.state.value)
        advanceUntilIdle()
        assertFalse((vm.state.value as DualCardUiState.Ready).wasCut)
    }

    @Test fun start_withLongHeadline_marksWasCut() = runTest(testDispatcher) {
        val vm = vm()
        vm.start(spec.copy(topTitle = "word ".repeat(200)))
        advanceUntilIdle()
        assertTrue((vm.state.value as DualCardUiState.Ready).wasCut)
    }

    @Test fun rendererFailure_emitsError() = runTest(testDispatcher) {
        val vm = vm(DualBitmapRenderer { throw IllegalStateException("boom") })
        vm.start(spec)
        advanceUntilIdle()
        assertEquals(R.string.echo_dual_error, (vm.state.value as DualCardUiState.Error).messageRes)
    }

    @Test fun dismiss_recyclesBitmapAndReturnsToIdle() = runTest(testDispatcher) {
        val vm = vm()
        vm.start(spec)
        advanceUntilIdle()
        val bitmap = (vm.state.value as DualCardUiState.Ready).bitmap
        vm.dismiss()
        assertTrue(bitmap.isRecycled)
        assertEquals(DualCardUiState.Idle, vm.state.value)
    }

    @Test fun startingAgain_recyclesThePreviousBitmap() = runTest(testDispatcher) {
        val vm = vm()
        vm.start(spec)
        advanceUntilIdle()
        val first = (vm.state.value as DualCardUiState.Ready).bitmap
        vm.start(spec.copy(bottomTitle = "Other"))
        advanceUntilIdle()
        assertTrue(first.isRecycled)
        assertFalse((vm.state.value as DualCardUiState.Ready).bitmap.isRecycled)
    }

    @Test fun prepareShareIntent_writesPngWithDualPrefixAndShareText() = runTest(testDispatcher) {
        val vm = vm()
        vm.start(spec)
        advanceUntilIdle()
        val file = File("dual.png")
        val intent = Intent(Intent.ACTION_CHOOSER)
        coEvery { sharer.writePng(any(), "dual", any()) } returns file
        every { sharer.buildShareIntent(file, "title", "#RSSCatHub #Debate") } returns intent

        val result = vm.prepareShareIntent("title", "#RSSCatHub #Debate")

        assertSame(intent, result)
        coVerify(exactly = 1) { sharer.writePng(any(), "dual", any()) }
    }

    @Test fun prepareShareIntent_whenWriteFails_returnsNullAndEmitsError() = runTest(testDispatcher) {
        val vm = vm()
        vm.start(spec)
        advanceUntilIdle()
        coEvery { sharer.writePng(any(), any<String>(), any()) } throws IOException("disk full")
        assertNull(vm.prepareShareIntent("title", "x"))
        assertEquals(R.string.echo_dual_error, (vm.state.value as DualCardUiState.Error).messageRes)
    }

    @Test fun prepareShareIntent_whenNotReady_doesNotTouchSharer() = runTest(testDispatcher) {
        assertNull(vm().prepareShareIntent("title", "x"))
        coVerify(exactly = 0) { sharer.writePng(any(), any<String>(), any()) }
    }
}
```

- [ ] **Step 2: Red.** `./gradlew testDevDebugUnitTest --tests "*DualCardViewModelTest"` → unresolved references.
- [ ] **Step 3: Implement.**

`infrastructure/quote/DualBitmapRenderer.kt`:

```kotlin
package com.mckimquyen.reader.infrastructure.quote

import android.graphics.Bitmap
import com.mckimquyen.reader.domain.steelman.DualPerspectiveSpec

/** Port so the ViewModel does not call [DualCardRenderer] directly (suspend lets tests fake a slow render). */
fun interface DualBitmapRenderer {
    suspend fun render(spec: DualPerspectiveSpec): Bitmap
}
```

`DualCardUiState.kt`:

```kotlin
package com.mckimquyen.reader.ui.page.home.read.steelman

import android.graphics.Bitmap
import androidx.annotation.StringRes

sealed interface DualCardUiState {
    data object Idle : DualCardUiState
    data object Rendering : DualCardUiState
    data class Ready(val bitmap: Bitmap, val wasCut: Boolean) : DualCardUiState
    data class Error(@StringRes val messageRes: Int) : DualCardUiState
}
```

`DualCardViewModel.kt` (exactly one `@Inject` constructor; the `DualBitmapRenderer` needs a Hilt binding, so check how `QuoteBitmapRenderer` is provided with `grep -rn "QuoteBitmapRenderer" app/src/main/java/com/mckimquyen/reader/infrastructure/di` and add the equivalent `@Provides DualBitmapRenderer = DualBitmapRenderer { DualCardRenderer.render(it) }` next to it):

```kotlin
package com.mckimquyen.reader.ui.page.home.read.steelman

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.steelman.DualPerspectiveSpec
import com.mckimquyen.reader.domain.steelman.DualPerspectiveTruncator
import com.mckimquyen.reader.infrastructure.di.DefaultDispatcher
import com.mckimquyen.reader.infrastructure.quote.DualBitmapRenderer
import com.mckimquyen.reader.infrastructure.quote.QuoteCardSharer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DualCardViewModel @Inject constructor(
    private val sharer: QuoteCardSharer,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    private val renderer: DualBitmapRenderer,
) : ViewModel() {

    private val _state = MutableStateFlow<DualCardUiState>(DualCardUiState.Idle)
    val state: StateFlow<DualCardUiState> = _state.asStateFlow()

    private var renderJob: Job? = null
    private var lastReady: DualCardUiState.Ready? = null

    fun start(spec: DualPerspectiveSpec) {
        renderJob?.cancel()
        _state.value = DualCardUiState.Rendering
        renderJob = viewModelScope.launch {
            try {
                val bitmap = withContext(defaultDispatcher) { renderer.render(spec) }
                val wasCut = DualPerspectiveTruncator.truncate(spec.topTitle).wasCut ||
                    DualPerspectiveTruncator.truncate(spec.bottomTitle).wasCut
                publish(DualCardUiState.Ready(bitmap, wasCut))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "render two-headline card failed", e)
                publish(DualCardUiState.Error(R.string.echo_dual_error))
            }
        }
    }

    /** Writes the PNG and builds the chooser; null when not ready or the write failed (state becomes Error). */
    suspend fun prepareShareIntent(chooserTitle: String, shareText: String): Intent? {
        val ready = _state.value as? DualCardUiState.Ready ?: return null
        return try {
            val file = sharer.writePng(ready.bitmap, FILE_PREFIX)
            sharer.buildShareIntent(file, chooserTitle, shareText)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "share two-headline card failed", e)
            _state.value = DualCardUiState.Error(R.string.echo_dual_error)
            null
        }
    }

    fun dismiss() {
        renderJob?.cancel()
        publish(DualCardUiState.Idle)
    }

    /** Sets the new state first, then recycles the previous bitmap so the UI never draws a recycled one. */
    private fun publish(next: DualCardUiState) {
        val previous = lastReady
        _state.value = next
        lastReady = next as? DualCardUiState.Ready
        if (previous != null && previous.bitmap !== lastReady?.bitmap) previous.bitmap.recycle()
    }

    override fun onCleared() {
        renderJob?.cancel()
        lastReady?.bitmap?.takeIf { !it.isRecycled }?.recycle()
        lastReady = null
        super.onCleared()
    }

    private companion object {
        const val TAG = "DualCardViewModel"
        const val FILE_PREFIX = "dual"
    }
}
```

`prepareShareIntent` calls `sharer.writePng(ready.bitmap, FILE_PREFIX)` with the default `nowMillis`; the test stubs `writePng(any(), "dual", any())`, which matches.

- [ ] **Step 4: Sheet test, then implement.** `DualCardSheetContentTest.kt` (`@Config(sdk=[34], application=Application::class) @GraphicsMode(NATIVE)`, `createComposeRule()`, one `setContent` per test):

```kotlin
package com.mckimquyen.reader.ui.page.home.read.steelman

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DualCardSheetContentTest {
    @get:Rule val compose = createComposeRule()

    private fun ready(wasCut: Boolean = false) =
        DualCardUiState.Ready(Bitmap.createBitmap(8, 14, Bitmap.Config.ARGB_8888), wasCut)

    @Test fun ready_showsPreviewAndShare_withoutCutNote() {
        compose.setContent { DualCardSheetContent(ready(), onShare = {}, onRetry = {}) }
        compose.onNodeWithTag(DUAL_SHEET_PREVIEW_TAG).assertIsDisplayed()
        compose.onNodeWithTag(DUAL_SHEET_SHARE_TAG).assertIsDisplayed()
        compose.onNodeWithTag(DUAL_SHEET_CUT_NOTE_TAG).assertDoesNotExist()
    }

    @Test fun ready_withCut_showsCutNote() {
        compose.setContent { DualCardSheetContent(ready(wasCut = true), onShare = {}, onRetry = {}) }
        compose.onNodeWithTag(DUAL_SHEET_CUT_NOTE_TAG).assertIsDisplayed()
    }

    @Test fun clickingShare_invokesCallback() {
        var shares = 0
        compose.setContent { DualCardSheetContent(ready(), onShare = { shares++ }, onRetry = {}) }
        compose.onNodeWithTag(DUAL_SHEET_SHARE_TAG).performClick()
        assertEquals(1, shares)
    }

    @Test fun rendering_disablesShare() {
        compose.setContent { DualCardSheetContent(DualCardUiState.Rendering, onShare = {}, onRetry = {}) }
        compose.onNodeWithTag(DUAL_SHEET_SHARE_TAG).assertIsNotEnabled()
    }

    @Test fun error_showsRetry_andRetryInvokesCallback() {
        var retries = 0
        compose.setContent {
            DualCardSheetContent(DualCardUiState.Error(com.mckimquyen.reader.R.string.echo_dual_error), onShare = {}, onRetry = { retries++ })
        }
        compose.onNodeWithTag(DUAL_SHEET_ERROR_TAG).assertIsDisplayed()
        compose.onNodeWithTag(DUAL_SHEET_RETRY_TAG).performClick()
        assertEquals(1, retries)
    }
}
```

`DualCardSheetContent.kt` (copy the layout of `QuoteCardSheetContent`: scrolling column with `navigationBarsPadding()`, preview image with `aspectRatio(DualCardRenderer.CARD_WIDTH.toFloat() / DualCardRenderer.CARD_HEIGHT)` capped by `PREVIEW_MAX_SCREEN_HEIGHT_FRACTION = 0.45f`, no template chips):

```kotlin
package com.mckimquyen.reader.ui.page.home.read.steelman

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R
import com.mckimquyen.reader.infrastructure.quote.DualCardRenderer

const val DUAL_SHEET_PREVIEW_TAG = "dual_sheet_preview"
const val DUAL_SHEET_CUT_NOTE_TAG = "dual_sheet_cut_note"
const val DUAL_SHEET_SHARE_TAG = "dual_sheet_share"
const val DUAL_SHEET_ERROR_TAG = "dual_sheet_error"
const val DUAL_SHEET_RETRY_TAG = "dual_sheet_retry"

private val PREVIEW_MAX_WIDTH = 240.dp
private const val PREVIEW_MAX_SCREEN_HEIGHT_FRACTION = 0.45f
private val PREVIEW_CORNER = 12.dp
private val SECTION_GAP = 12.dp
private val SHARE_HEIGHT = 48.dp
private const val CARD_ASPECT = DualCardRenderer.CARD_WIDTH.toFloat() / DualCardRenderer.CARD_HEIGHT

@Composable
fun DualCardSheetContent(
    state: DualCardUiState,
    onShare: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val previewMaxHeight = (LocalConfiguration.current.screenHeightDp * PREVIEW_MAX_SCREEN_HEIGHT_FRACTION).dp
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
    ) {
        Text(
            text = stringResource(R.string.echo_dual_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        when (state) {
            is DualCardUiState.Ready -> Image(
                bitmap = state.bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .heightIn(max = previewMaxHeight)
                    .widthIn(max = PREVIEW_MAX_WIDTH)
                    .aspectRatio(CARD_ASPECT)
                    .clip(RoundedCornerShape(PREVIEW_CORNER))
                    .testTag(DUAL_SHEET_PREVIEW_TAG),
            )
            is DualCardUiState.Error -> Column(
                modifier = Modifier.testTag(DUAL_SHEET_ERROR_TAG),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
            ) {
                Text(stringResource(state.messageRes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                Button(onClick = onRetry, modifier = Modifier.testTag(DUAL_SHEET_RETRY_TAG)) { Text(stringResource(R.string.retry)) }
            }
            else -> CircularProgressIndicator(modifier = Modifier.size(32.dp))
        }
        if ((state as? DualCardUiState.Ready)?.wasCut == true) {
            Text(
                text = stringResource(R.string.echo_dual_cut_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(DUAL_SHEET_CUT_NOTE_TAG),
            )
        }
        Button(
            onClick = onShare,
            enabled = state is DualCardUiState.Ready,
            modifier = Modifier.fillMaxWidth().height(SHARE_HEIGHT).padding(horizontal = 8.dp).testTag(DUAL_SHEET_SHARE_TAG),
        ) { Text(stringResource(R.string.echo_dual_share)) }
    }
}
```

- [ ] **Step 5: Green.** `./gradlew testDevDebugUnitTest --tests "*DualCardViewModelTest" --tests "*DualCardSheetContentTest"`; read XML; expect 9 + 5 pass. Then `./gradlew compileDevDebugKotlin` (verifies the Hilt binding).
- [ ] **Step 6: Commit.** `feat(echo-03): add DualCardViewModel and sheet content` (+ trailer).

---

### Task 6: Export button on the ECHO-02 card, ReadingViewModel and ReadingPage wiring

**Files:** modify `ContrastingCoverageCard.kt` (+ its test), `Content.kt`, `ReadingPage.kt`, `ReadingViewModel.kt`; create `ReadingViewModelDualCardTest.kt`.

- [ ] **Step 1: Failing Compose tests** appended to `ContrastingCoverageCardTest` (existing fixture `readyState` with items `a1`, `a2`):

```kotlin
    @Test
    fun clickingExportButton_firesExportCallbackWithThatArticle() {
        var exported = ""
        compose.setContent {
            ContrastingCoverageCard(
                state = readyState, onSelectPhrase = {}, onOpenArticle = {},
                onExportArticle = { exported = it.articleId },
            )
        }
        compose.onNodeWithTag("${ECHO_CONTRAST_EXPORT_PREFIX}a2").performClick()
        assertEquals("a2", exported)
    }

    @Test
    fun noExportCallback_hidesExportButtons() {
        compose.setContent {
            ContrastingCoverageCard(state = readyState, onSelectPhrase = {}, onOpenArticle = {})
        }
        compose.onAllNodesWithTag("${ECHO_CONTRAST_EXPORT_PREFIX}a1").assertCountEquals(0)
    }
```

- [ ] **Step 2: Red.** `./gradlew testDevDebugUnitTest --tests "*ContrastingCoverageCardTest"` → unresolved `onExportArticle` / `ECHO_CONTRAST_EXPORT_PREFIX`.
- [ ] **Step 3: Implement the button.** In `ContrastingCoverageCard.kt`: add `const val ECHO_CONTRAST_EXPORT_PREFIX = "echo_contrast_export_"`; add trailing parameter `onExportArticle: ((ContrastingArticle) -> Unit)? = null` to `ContrastingCoverageCard`, `ContrastingCoverageSection` and `ContrastingArticleRow` (default `null` keeps every existing call compiling); inside the row, below the meta `Row`, when `onExportArticle != null` add:

```kotlin
        if (onExportArticle != null) {
            val exportDesc = stringResource(R.string.echo_dual_export_desc, item.feedName)
            TextButton(
                onClick = { onExportArticle(item) },
                modifier = Modifier
                    .semantics { contentDescription = exportDesc }
                    .testTag("$ECHO_CONTRAST_EXPORT_PREFIX${item.articleId}"),
            ) { Text(stringResource(R.string.echo_dual_export)) }
        }
```

with `import androidx.compose.material3.TextButton`. Green: rerun the card test (7 tests).
- [ ] **Step 4: Failing ReadingViewModel test** `app/src/test/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModelDualCardTest.kt`. Copy the fixture of `ReadingViewModelPositionHandoffTest` (same mocks, `Dispatchers.setMain`, Log/HtmlCompat static mocks, `defaultDispatcher = workerDispatcher`, article id `art_dual_1`, title `"Musk says SpaceX restricted Ukraine's Starlink connection"`, feed name `"BBC News"`) and add:

```kotlin
    private val other = ContrastingArticle("a2", "Scientists warn Starlink disrupts research", "Science Wire", Date(1_000L), "warn")

    @Test fun openDualCard_buildsSpecFromCurrentArticleAndChosenOne() = runTest(mainDispatcher) {
        val vm = newVm(); vm.initData(ARTICLE_ID, autoTtsEnabled = false); advanceUntilIdle()

        vm.openDualCard(other)

        val state = vm.readingUiState.value
        assertTrue(state.showDualCardSheet)
        val spec = state.dualCardSpec!!
        assertEquals("Musk says SpaceX restricted Ukraine's Starlink connection", spec.topTitle)
        assertEquals("BBC News", spec.topSource)
        assertEquals("Scientists warn Starlink disrupts research", spec.bottomTitle)
        assertEquals("Science Wire", spec.bottomSource)
        assertEquals("warn", spec.cue)
    }

    @Test fun openDualCard_withoutLoadedArticle_doesNothing() = runTest(mainDispatcher) {
        val vm = newVm()
        vm.openDualCard(other)
        assertFalse(vm.readingUiState.value.showDualCardSheet)
    }

    @Test fun dismissDualCard_clearsSheetAndSpec() = runTest(mainDispatcher) {
        val vm = newVm(); vm.initData(ARTICLE_ID, autoTtsEnabled = false); advanceUntilIdle()
        vm.openDualCard(other)
        vm.dismissDualCard()
        assertFalse(vm.readingUiState.value.showDualCardSheet)
        assertNull(vm.readingUiState.value.dualCardSpec)
    }
```

Imports add `com.mckimquyen.reader.domain.steelman.ContrastingArticle`, `org.junit.Assert.{assertEquals, assertFalse, assertNull, assertTrue}`.
- [ ] **Step 5: Red**, then implement in `ReadingViewModel.kt`: add to `ReadingUiState` `val showDualCardSheet: Boolean = false` and `val dualCardSpec: DualPerspectiveSpec? = null`; add

```kotlin
    /** ECHO-03: opens the two-headline card sheet for the article being read and one ECHO-02 result. */
    fun openDualCard(other: ContrastingArticle) {
        val current = _readingUiState.value.articleWithFeed ?: return
        val formatter = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
        _readingUiState.update {
            it.copy(
                showDualCardSheet = true,
                dualCardSpec = DualPerspectiveSpec(
                    topTitle = current.article.title,
                    topSource = current.feed.name,
                    topDate = formatter.format(current.article.date),
                    bottomTitle = other.title,
                    bottomSource = other.feedName,
                    bottomDate = formatter.format(other.date),
                    cue = other.cue,
                ),
            )
        }
    }

    fun dismissDualCard() {
        _readingUiState.update { it.copy(showDualCardSheet = false, dualCardSpec = null) }
    }
```

with imports `DualPerspectiveSpec` and `ContrastingArticle`. Green: `./gradlew testDevDebugUnitTest --tests "*ReadingViewModelDualCardTest" --tests "*ReadingViewModel*"`; expect all pass.
- [ ] **Step 6: Wire `Content.kt` and `ReadingPage.kt`.**
  - `Content.kt`: add trailing parameter `onExportContrastArticle: ((ContrastingArticle) -> Unit)? = null` (after `bodyRange`) and pass `onExportArticle = onExportContrastArticle` to `ContrastingCoverageSection`; import `ContrastingArticle`.
  - `ReadingPage.kt`: `val dualCardViewModel: DualCardViewModel = hiltViewModel()`, `val dualCardState by dualCardViewModel.state.collectAsState()`, `val dualChooserTitle = stringResource(R.string.echo_dual_chooser_title)`, `val dualShareText = stringResource(R.string.echo_dual_share_text)`; add `readingUiState.showDualCardSheet` to `isAnySheetOpen`; add `readingViewModel.dismissDualCard()` to the drawer-hidden sync block; add `if (readingUiState.showDualCardSheet) readingViewModel.dismissDualCard() else if ...` as the first `BackHandler` branch (before `showEchoSheet`); add `LaunchedEffect(readingUiState.dualCardSpec) { val spec = readingUiState.dualCardSpec; if (spec != null) dualCardViewModel.start(spec) else dualCardViewModel.dismiss() }`; add a `DualCardSheetContent(state = dualCardState, onShare = { quoteScope.launch { dualCardViewModel.prepareShareIntent(dualChooserTitle, dualShareText)?.let { context.startActivity(it) } } }, onRetry = { readingUiState.dualCardSpec?.let { dualCardViewModel.start(it) } })` branch as the first branch of the `sheetContent` chain (before the echo branch); pass `onExportContrastArticle = { readingViewModel.openDualCard(it) }` to `Content(...)`.
- [ ] **Step 7: Verify.** `./gradlew compileDevDebugKotlin` then `./gradlew testDevDebugUnitTest --tests "*Content*" --tests "*ReadingViewModel*" --tests "*steelman*" --tests "*DualCard*" --rerun`; read XML, expect 0 failures.
- [ ] **Step 8: Commit.** `feat(echo-03): export a two-headline card from the contrasting coverage card` (+ trailer).

---

### Task 7: Instrumentation, full verification, Pixel smoke, docs

- [ ] **Step 1: Instrumentation test** `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/read/steelman/DualCardDeviceTest.kt`:

```kotlin
package com.mckimquyen.reader.ui.page.home.read.steelman

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.steelman.DualPerspectiveSpec
import com.mckimquyen.reader.infrastructure.quote.DualCardRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DualCardDeviceTest {
    @Test
    fun renderer_producesNineBySixteenCardWithTwoDistinctHalves_onARealDevice() {
        val spec = DualPerspectiveSpec("Top headline", "BBC News", "1 Nov", "Bottom headline", "Science Wire", "1 Nov", "warn")
        val bitmap = DualCardRenderer.render(spec)
        assertEquals(1080, bitmap.width)
        assertEquals(1920, bitmap.height)
        assertNotEquals(bitmap.getPixel(8, 8), bitmap.getPixel(8, bitmap.height - 8))
    }
}
```

- [ ] **Step 2: Full verification** (sequential): `./gradlew testDevDebugUnitTest --rerun`, `./gradlew lintDevDebug --rerun-tasks`, `./gradlew assembleDevDebug assembleDevDebugAndroidTest`. Read unit counts/failures from all `app/build/test-results/testDevDebugUnitTest/*.xml` and lint counts from `app/build/reports/lint-results-devDebug.xml`; record the real numbers. Repeat the unit suite 3 times to catch flaky tests; any failure must be explained from its XML stack, not ignored.
- [ ] **Step 3: Instrumentation on the Pixel.** `adb devices -l` must list `2B051FDH3006MU` with model `Pixel_7_Pro`. Resolve APKs with `find` (the file name has spaces), `adb -s 2B051FDH3006MU install -r -d -t ...` both, then `adb -s 2B051FDH3006MU shell am instrument -w -e class com.mckimquyen.reader.ui.page.home.read.steelman.DualCardDeviceTest com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner`; uninstall `com.mckimquyen.reader.test` afterwards.
- [ ] **Step 4: Backup.** `adb -s 2B051FDH3006MU shell am force-stop com.mckimquyen.reader`; `adb exec-out run-as com.mckimquyen.reader cat databases/Reader > /tmp/echo03-bak/Reader` (and `-wal`, `-shm`, `files/datastore/settings.preferences_pb`). Require non-empty; copy the three Room files to `/tmp/echo03-chk` and run `sqlite3 /tmp/echo03-chk/Reader "PRAGMA integrity_check;"` → `ok`.
- [ ] **Step 5: Seed the DB COPY only.** In `/tmp/echo03-seed` copy the three Room files, `sqlite3 Reader "PRAGMA wal_checkpoint(TRUNCATE);"`, then:

```sql
INSERT OR REPLACE INTO feed (id, name, url, groupId, accountId, isNotification, isFullContent)
VALUES ('1$f_test', 'Science Wire', 'https://example.com/rss', '1$read_you_app_default_group', 1, 0, 0);
INSERT OR REPLACE INTO article (id, date, title, author, rawDescription, shortDescription, fullContent, img, link, feedId, accountId, isUnread, isStarred, isReadLater)
VALUES ('1$a_contrast_smoke', strftime('%s','now')*1000, 'Scientists warn Starlink constellation disrupts astronomical research', 'Staff', '', 'New satellite launches raise concerns among global observatories.', NULL, NULL, 'https://example.com/starlink-warn', '1$f_test', 1, 1, 0, 0);
```

Check `PRAGMA integrity_check`/`foreign_key_check`, zero out `-wal` and `-shm`, push the three files with the `run-as ... sh -c 'cat > ...'` form, and read them back with `cmp`.
- [ ] **Step 6: Smoke.** Launch with `monkey`; close any full-screen `AdActivity` using the rules in Global Constraints and report it. Open the BBC article "Musk says SpaceX restricted Ukraine's Starlink connection as service restored" (use the "Tất cả" list and scroll; a fresh dump before every tap), scroll to the "Góc nhìn khác" card, confirm the "Xuất thẻ" button on the Science Wire row, tap it, confirm the sheet shows a preview, confirm the preview has two halves (take a screenshot and look at it), tap "Chia sẻ" and confirm the Android share sheet opens, then back out without sending. Confirm that turning off "Tín hiệu giật tít & cảm xúc tiêu đề" hides both the card and the button (re-open the article), then restore.
- [ ] **Step 7: Restore (always).** Force-stop, push all four backed-up files back with `run-as ... sh -c 'cat > PATH'`, read each back and `cmp`, run `integrity_check` on a copy of the three Room files, confirm `com.mckimquyen.reader.test` is not installed, delete only host temp dirs.
- [ ] **Step 8: Docs.** Write `doc/task/done/ECHO-03_dual-perspective-quote-card_DONE.md` (honest scope: two headlines, not a rebuttal, no AI; limits: reads as "this source refutes that one", button only with two or more feeds, hashtag may be dropped by some apps; exact commits, test counts, lint, instrumentation, smoke observations and what was not observed, ads that appeared, data safety). Add an ECHO-03 entry under "Recently implemented" in `doc/feature.md`. Change the ECHO-03 row of `doc/task/todo/16_ECHOCHAMBER_AND_BIAS_COMPASS.md` to done and link `../done/ECHO-03_dual-perspective-quote-card_DONE.md`. `git rm doc/task/todo/ECHO-03_dual-perspective-quote-card.md`.
- [ ] **Step 9: Commit.** `git add app/src doc docs && git commit -m "feat(echo-03): complete two-headline card from the local archive"` (+ trailer).

## Final verification checklist

- [ ] Truncator cuts at word boundary, never mid-limit, keeps exact-limit headlines; renderer is exactly 1080x1920 with two different halves and survives empty/long/Unicode input.
- [ ] `QuoteCardSharer` defaults unchanged; `EXTRA_TEXT` only when `shareText` is given; `QuoteCardViewModelTest` still green.
- [ ] No "rebuttal" or "debate" drawn on the image or in any UI string except the `#Debate` share text.
- [ ] `DualCardViewModel` has one `@Inject` constructor, injected `defaultDispatcher`, recycles bitmaps on replace/dismiss/clear.
- [ ] Export button appears only when `onExportArticle` is provided and the ECHO-02 card shows; hidden when `Headline signals` is off.
- [ ] Sheet participates in `isAnySheetOpen`, dismiss sync and `BackHandler`.
- [ ] 38 locales have all 8 `echo_dual_*` keys; `StringResourceParityTest` 6/6; Room still v20.
- [ ] Full unit suite (3 runs), lint, builds and Pixel instrumentation pass; smoke reported truthfully including what was not observed.
- [ ] Room (3 files) and DataStore restored and byte-compared after smoke.
