# ECHO-01 Local Hype & Sentiment Signals Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Detect promotional/Hype wording and lexical tone locally from Reader's body text, display two slim inline gauges beneath metadata only when noteworthy, and open a detail sheet of flagged passages in the existing BottomDrawer.

**Architecture:** Pure Kotlin `ArticleSignalAnalyzer` converts body HTML to plain text, normalizes, detects language (reusing EXC-04 markers), computes a 0..100 Hype score and EXC-04 lexical tone, and extracts up to 8 flagged paragraphs (<=240 chars each). `ReadingViewModel` runs analysis asynchronously on `Dispatchers.Default` using the existing body content and stores memory-only `EchoSignalState`. `Content.kt` displays two compact progress bars under `Metadata`; clicking opens `EchoSignalDetailsSheet` hosted in `ReadingPage`'s `BottomDrawer`. The shared `Headline signals` toggle is a CompositionLocal (`LocalHeadlineSignalsEnabled`, provided in `HomeEntry.kt`), so it is enforced at render time in `Content`; the ViewModel stays toggle-agnostic.

**Tech Stack:** Kotlin JVM, Jetpack Compose Material 3 (LinearProgressIndicator, Card, Surface), Material `BottomDrawer` (`ModalBottomSheetLayout`), JUnit4, Robolectric, Android Compose instrumentation tests on Pixel 7 Pro. No Gemini/network/Room/dependencies.

## Global Constraints

- Branch `dev` only; no worktrees.
- Pixel 7 Pro serial `2B051FDH3006MU` is the only authorized hardware target (`adb -s 2B051FDH3006MU ...`). Never use `connectedDevDebugAndroidTest` (broadcasts to every connected device).
- Observe R4: full-screen `AdActivity` -> stop immediately, notify owner, wait for `done`.
- Offline heuristic only: no Gemini, no API, no network, no default/BYOK keys read or needed.
- No political-bias claim, accuracy judgment, or author-intent inference; lexical signals only.
- `HeadlineTone.UNCLEAR` means insufficient lexical evidence, never neutral.
- Show gauges only when Hype >= 40 or tone != `UNCLEAR`; ordinary articles show no block.
- Hype >= 70 turns the Hype bar orange and adds a gentle warning label.
- Flagged passages cap: at most 8 paragraphs, each at most 240 chars (surrogate-safe).
- Reuses the existing `Headline signals` toggle via `LocalHeadlineSignalsEnabled`; no new preference.
- All 14 `echo_*` strings added to all 38 maintained `strings.xml` in Task 3.

---

## File Structure

- Create `app/src/main/java/com/mckimquyen/reader/domain/headline/ArticleSignalAnalyzer.kt`: body HTML to plain text via `SnapshotNormalizer.toPlainText`, 12k bound, paragraph splitting, Hype score, tone reuse, up to 8 flagged passages.
- Modify `app/src/main/java/com/mckimquyen/reader/domain/headline/HeadlineLexicon.kt`: add Hype promotional phrases/intensifiers for EN, VI, and experimental ES/FR/DE.
- Create `app/src/main/java/com/mckimquyen/reader/domain/headline/ArticleSignal.kt`: models `ArticleSignalResult`, `FlaggedPassage`, and `HypeCueCategory`.
- Create `app/src/main/java/com/mckimquyen/reader/ui/page/headline/EchoSignalsState.kt`: `EchoSignalState` (Idle/Analyzing/Ready/None) and helper.
- Modify `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModel.kt`: trigger body analysis when content changes; store `echoSignalState`; sheet open/dismiss; cancel prior jobs.
- Create `app/src/main/java/com/mckimquyen/reader/ui/page/headline/EchoSignalGauges.kt`: two slim progress bars under `Metadata`, clickable, with accessibility descriptions and warning styling.
- Create `app/src/main/java/com/mckimquyen/reader/ui/page/headline/EchoSignalDetailsSheet.kt`: sheet title, two bars, honest disclaimer, up to 8 flagged passages, close button.
- Modify `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/Content.kt`: render `EchoSignalGauges` under metadata; pass click to callback.
- Modify `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingPage.kt`: wire `showEchoSheet` into `isAnySheetOpen`, `BackHandler`, `BottomDrawer.sheetContent` and outside click.
- Add unit/Compose tests under `app/src/test` and instrumentation under `app/src/androidTest`.

## Interfaces

Task 1 produces:

```kotlin
enum class HypeCueCategory { PROMOTIONAL_PHRASE, INTENSIFIER, ABSOLUTE_CLAIM, EXCLAMATION }

data class FlaggedPassage(
    val text: String,
    val score: Int,
    val cues: List<HypeCueCategory>,
)

data class ArticleSignalResult(
    val language: HeadlineLanguage,
    val hypeScore: Int,
    val tone: HeadlineTone,
    val flaggedPassages: List<FlaggedPassage>,
) {
    val isNoteworthy: Boolean get() = hypeScore >= 40 || tone != HeadlineTone.UNCLEAR
    val hasHypeWarning: Boolean get() = hypeScore >= 70
}

object ArticleSignalAnalyzer {
    const val MAX_ANALYZED_BODY_CHARS = 12_000
    const val MAX_FLAGGED_PASSAGES = 8
    const val MAX_PASSAGE_CHARS = 240
    const val HYPE_NOTEWORTHY_THRESHOLD = 40
    const val HYPE_WARNING_THRESHOLD = 70

    fun analyze(htmlOrPlainText: String, languageOverride: HeadlineLanguage? = null): ArticleSignalResult?
}
```

Task 2 produces:

```kotlin
sealed interface EchoSignalState {
    data object Idle : EchoSignalState
    data object Analyzing : EchoSignalState
    data class Ready(val result: ArticleSignalResult) : EchoSignalState
    data object None : EchoSignalState
}
```

Task 3 produces:

```kotlin
@Composable
fun EchoSignalGauges(
    result: ArticleSignalResult?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
)

@Composable
fun EchoSignalDetailsSheet(
    result: ArticleSignalResult,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
)
```

---

### Task 1: Pure JVM body analyzer and hype lexicon

**Files:** Modify `HeadlineLexicon.kt`; create `ArticleSignal.kt`, `ArticleSignalAnalyzer.kt`, and `ArticleSignalAnalyzerTest.kt`.

- [x] **Step 1: Write failing analyzer tests**

Create `app/src/test/java/com/mckimquyen/reader/domain/headline/ArticleSignalAnalyzerTest.kt` covering:
- Promotional PR body scores >= 70 and has `hasHypeWarning == true`.
- Factual BBC-style report scores < 40 and with UNCLEAR tone produces `isNoteworthy == false`.
- Score clamped to 0..100; length normalization prevents long factual text from accumulating Hype.
- Tone positive/negative/UNCLEAR matches body keywords.
- Hype is independent from tone (promotional text can be positive, negative, or unclear).
- Up to 8 passages, sorted score descending, each truncated at 240 chars without surrogate split.
- Unsupported language or plain text < 30 chars returns `null`.
- Input > 12,000 chars is capped safely.

- [x] **Step 2: Run targeted test red**

Run: `./gradlew testDevDebugUnitTest --tests "*ArticleSignalAnalyzerTest"`
Expected: compilation failure for missing `ArticleSignalAnalyzer` and related types.

- [x] **Step 3a: Expose a minimal internal API on `HeadlineAnalyzer` (private members are NOT reusable today)**

`detectLanguage`, `toneEvidence`, `normalize`, `tokens` and `LanguageLexicon` are all `private` in `HeadlineAnalyzer.kt` (lines 54-124), so `ArticleSignalAnalyzer` cannot call them. Add these `internal` members and keep every existing behavior byte-identical:

```kotlin
internal fun detectBodyLanguage(text: String): HeadlineLanguage? = detectLanguage(text)

/** Lexical tone for arbitrary text, with the same thresholds and UNCLEAR semantics as titles. */
internal fun toneOf(text: String, language: HeadlineLanguage): HeadlineTone {
    val normalized = normalize(text)
    val (positive, negative) = toneEvidence(normalized, tokens(normalized), language.lexicon())
    return when {
        positive >= MIN_TONE_EVIDENCE && positive - negative >= TONE_MARGIN -> HeadlineTone.POSITIVE
        negative >= MIN_TONE_EVIDENCE && negative - positive >= TONE_MARGIN -> HeadlineTone.NEGATIVE
        else -> HeadlineTone.UNCLEAR
    }
}
```

Refactor `analyze()` to call `toneOf` so there is one source of truth. Add `HeadlineAnalyzerTest` cases proving `analyze()` output is unchanged and `toneOf` agrees with it. The existing 17 `HeadlineAnalyzerTest` cases and the performance test must stay green.

- [x] **Step 3b: Add Hype phrases to HeadlineLexicon**

Add curated promotional/hype phrases and absolute-claim words for EN and VI, plus experimental sets for ES/FR/DE:
- EN: "game-changing", "revolutionary", "world-class", "breakthrough", "best ever", "market leader", "guaranteed", "unparalleled", "must-have", "seamlessly", "exclusive", "groundbreaking", "disrupting".
- VI: "đột phá", "cách mạng", "hàng đầu thế giới", "số 1", "đỉnh cao", "cam kết 100%", "chưa từng có", "hoàn hảo", "vượt trội", "thần kỳ", "độc quyền".
- ES/FR/DE: compact experimental phrase lists mirroring the above.

- [x] **Step 4: Implement ArticleSignalAnalyzer**

1. Convert HTML using `SnapshotNormalizer.toPlainText(html)`.
2. Take at most 12,000 characters; if last char is high surrogate, drop it.
3. Detect language with `HeadlineAnalyzer.detectBodyLanguage`; if `null` or `UNSUPPORTED`, return `null`.
4. Split body into paragraphs on `\n+`.
5. Score each paragraph using: exact promotional phrases (weight 25), absolute claims (weight 15), intensifiers (weight 8), repeated `!` (weight 10), uppercase ratio (weight 10). Normalize per 100 words so paragraph length doesn't distort.
6. Compute overall Hype: weighted average of top paragraphs plus density of PR cues across the full text. Clamp 0..100.
7. Tone: `HeadlineAnalyzer.toneOf(body, language)` (same thresholds and UNCLEAR semantics as titles; do not fork the numbers).
8. Retain paragraphs with paragraph score >= 35, up to 8, sorted by score desc, trimmed at 240 chars.
9. Return `ArticleSignalResult(language, hypeScore, tone, flaggedPassages)`.

- [x] **Step 5: Run analyzer tests green**

Run: `./gradlew testDevDebugUnitTest --tests "*ArticleSignalAnalyzerTest" --rerun`
Expected: all analyzer tests pass.

- [x] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/domain/headline app/src/test/java/com/mckimquyen/reader/domain/headline
git commit -m "feat(echo-01): analyze body hype and tone signals locally"
```

Findings recorded during execution:
- `SnapshotNormalizer.toPlainText` collapses all whitespace, so paragraph breaks vanish; paragraphs are now marked (U+0001 at block tags / line breaks) before normalizing. `toPlainText` itself is unchanged because radar hashing depends on it.
- Substring matching produced false positives on real BBC items ("disrupt UK democracy", "number one" in tennis, "Islamic Revolutionary Guard"). Fixes: whole-word phrase matching, removed over-generic cues ("disrupt", "number one", "proven to", "hoàn hảo", "duy nhất", "tuyệt đối"), a 40-word density floor, and a corroboration rule (a lone cue can never reach the noteworthy threshold).
- Real-data check on the 176 BBC articles in the Pixel backup: analyzed 175, max Hype 39, 0 warnings, tones {UNCLEAR 173, POSITIVE 2}. This shows a low false-positive rate on ordinary news; it does NOT measure recall on genuinely promotional copy (no such corpus exists locally), so recall is only covered by synthetic fixtures.


### Task 2: State and ViewModel asynchronous analysis

**Files:** Create `EchoSignalsState.kt`; modify `ReadingViewModel.kt`; create `ReadingViewModelEchoTest.kt`.

- [x] **Step 1: Write failing ViewModel tests**

Create `app/src/test/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModelEchoTest.kt` verifying:
- When article loaded and content rendered, `echoSignalState` transitions from Analyzing to Ready.
- Noteworthy article sets `Ready(result)`; non-noteworthy article sets `None`.
- Changing article cancels prior analysis job.
- Blank content sets `Idle`.
- `openEchoSheet()` sets `showEchoSheet = true`; `dismissEchoSheet()` resets it to `false`.
- Analysis always runs for supported text; the shared toggle is NOT visible to the ViewModel (it is a CompositionLocal provided at `HomeEntry.kt:198`), so gating happens in the UI in Task 5.

- [x] **Step 2: Run targeted test red**

Run: `./gradlew testDevDebugUnitTest --tests "*ReadingViewModelEchoTest"`
Expected: compilation failure for missing `echoSignalState` on ViewModel.

- [x] **Step 3: Implement EchoSignalState and ViewModel wiring**

Add:
- `echoSignalState: StateFlow<EchoSignalState>` on `ReadingViewModel`.
- `echoJob: Job?` cancelled on article switch, content change, or dismiss.
- In `internalRenderFullContent` and `renderDescriptionContent`, launch analysis on `Dispatchers.Default`:
  ```kotlin
  val result = withContext(defaultDispatcher) { ArticleSignalAnalyzer.analyze(body) }
  _echoSignalState.value = if (result != null && result.isNoteworthy) EchoSignalState.Ready(result) else EchoSignalState.None
  ```
- Add `showEchoSheet: Boolean = false` to `ReadingUiState`.
- Add `openEchoSheet()` and `dismissEchoSheet()`.

- [x] **Step 4: Run ViewModel tests green**

Run: `./gradlew testDevDebugUnitTest --tests "*ReadingViewModelEchoTest" --tests "*ReadingViewModel*" --rerun`
Expected: all echo and existing ReadingViewModel tests pass.

- [x] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/page/headline/EchoSignalsState.kt app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModel.kt app/src/test/java/com/mckimquyen/reader/ui/page/home/read
git commit -m "feat(echo-01): manage echo signal state in ReadingViewModel"
```

Deviation recorded: instead of calling the analyzer from `internalRenderFullContent` and `renderDescriptionContent` separately, a single observer in `init` watches `(articleId, content)` with `distinctUntilChanged`. It covers every render path (description, full content, article switch, errors that replace content) and cancels stale work. An article switch immediately resets to `Idle` and closes the echo sheet, so one article's signals cannot leak onto the next. `openEchoSheet()` only opens when the state is `Ready`.


### Task 3: Localization (14 keys x 38 locales)

**Files:** All 38 `app/src/main/res/values*/strings.xml`.

- [x] **Step 1: Define strings in values and values-vi**

Keys to add:
1. `echo_hype_label`: "Hype: %1$d%%" / "Độ thổi phồng: %1$d%%"
2. `echo_hype_warning`: "Strong promotional language detected" / "Phát hiện nhiều từ ngữ quảng cáo, cường điệu"
3. `echo_tone_label`: "Tone: %1$s" / "Cảm xúc: %1$s"
4. `echo_tone_positive`: "🟢 Positive" / "🟢 Tích cực"
5. `echo_tone_unclear`: "⚪ Unclear" / "⚪ Chưa rõ"
6. `echo_tone_negative`: "🔴 Negative" / "🔴 Tiêu cực"
7. `echo_details_title`: "Body Language Signals" / "Tín hiệu ngôn từ bài viết"
8. `echo_disclaimer`: "These are local lexical signals based on wording patterns, not fact-checks, credibility scores, or claims of political bias. 'Unclear' means insufficient evidence, not neutral." / "Đây là tín hiệu từ ngữ cục bộ dựa trên mẫu câu chữ; không phải kiểm chứng sự thật, đánh giá độ tin cậy hay phán đoán thiên kiến chính trị. 'Chưa rõ' phản ánh thiếu dấu hiệu, không phải trung lập."
9. `echo_flagged_passages_title`: "Flagged passages (%1$d)" / "Đoạn văn được đánh dấu (%1$d)"
10. `echo_cue_promotional`: "Promotional phrase" / "Cụm từ quảng bá"
11. `echo_cue_intensifier`: "Exaggeration" / "Từ ngữ cường điệu"
12. `echo_cue_absolute`: "Absolute claim" / "Tuyên bố tuyệt đối"
13. `echo_cue_exclamation`: "Punctuation/case" / "Dấu câu/chữ hoa"
14. `echo_no_passages`: "No specific paragraph crossed the hype threshold." / "Không có đoạn văn riêng lẻ nào vượt ngưỡng thổi phồng."

- [x] **Step 2: Propagate to all 38 locales with EN fallback**

Write a Python script to insert the block into every maintained `strings.xml`.

- [x] **Step 3: Run parity test green**

Run: `./gradlew testDevDebugUnitTest --tests "*StringResourceParityTest" --rerun processDevDebugResources`
Expected: 6/6 parity tests pass.

- [x] **Step 4: Commit**

```bash
git add app/src/main/res
git commit -m "feat(echo-01): add echo signal strings across 38 locales"
```

Result: 14 `echo_*` keys in all 38 `strings.xml` (EN and VI hand-written, others English fallback); `StringResourceParityTest` 6/6.

### Task 4: Inline gauges and detail sheet Composables

**Files:** Create `EchoSignalGauges.kt`, `EchoSignalDetailsSheet.kt`, and `EchoSignalUiTest.kt`.

- [x] **Step 1: Write failing Compose UI tests**

Create `app/src/test/java/com/mckimquyen/reader/ui/page/headline/EchoSignalUiTest.kt`:
- Gauges hidden when result is null or not noteworthy (`isNoteworthy == false`).
- Gauges visible with Hype progress and tone label when noteworthy.
- Hype bar styled with warning color when >= 70; normal primary when < 70.
- Clicking either gauge invokes `onClick`.
- Sheet renders title, both bars, disclaimer text, and list of flagged passages.
- Sheet close button invokes `onClose`.
- Tag constants: `ECHO_GAUGES_TAG`, `ECHO_HYPE_BAR_TAG`, `ECHO_TONE_BAR_TAG`, `ECHO_SHEET_TAG`, `ECHO_CLOSE_TAG`, `ECHO_PASSAGE_PREFIX`.

- [x] **Step 2: Run UI test red**

Run: `./gradlew testDevDebugUnitTest --tests "*EchoSignalUiTest"`
Expected: compilation failure for missing composables.

- [x] **Step 3: Implement EchoSignalGauges**

- Layout: Column with Hype row + Tone row.
- Hype row: icon, localized label + percent, slim `LinearProgressIndicator` (height 6dp, rounded), warning icon + label if >= 70.
- Tone row: icon, localized label, slim indicator, semantic chip.
- Wrap in accessible semantic `Surface` with `role = Role.Button`, `contentDescription`, and `clickable { onClick() }`.

- [x] **Step 4: Implement EchoSignalDetailsSheet**

- Column with header (title + close TextButton), disclaimer banner (`Surface` with low alpha outline), Hype + Tone summary cards, LazyColumn of `FlaggedPassage` cards (truncated text, score badge, cue tags).

- [x] **Step 5: Run UI tests green**

Run: `./gradlew testDevDebugUnitTest --tests "*EchoSignalUiTest" --rerun`
Expected: all gauge and sheet tests pass.

- [x] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/page/headline app/src/test/java/com/mckimquyen/reader/ui/page/headline
git commit -m "feat(echo-01): build inline gauges and detail sheet composables"
```

Findings: `stringResource` is @Composable and cannot run inside `joinToString`; labels are resolved into a `val` first. The gauge block is a single merged TalkBack button, so its child nodes (warning copy) exist only in the unmerged semantics tree; tests use `useUnmergedTree = true` for those and separately assert the merged description, `Role.Button` and the 48dp minimum touch target. 13 Compose tests.

### Task 5: Wire into Content.kt and ReadingPage.kt

**Files:** Modify `Content.kt` and `ReadingPage.kt`; add integration tests.

- [x] **Step 1: Add Content and ReadingPage integration tests**

Create `app/src/test/java/com/mckimquyen/reader/ui/page/home/read/ContentEchoIntegrationTest.kt` verifying:
- `EchoSignalGauges` is composed inside `Content` below `Metadata`; with `LocalHeadlineSignalsEnabled provides false` no gauge node exists even when `echoResult` is noteworthy (this is where the toggle is enforced).
- Clicking gauges invokes `onOpenEchoSheet`.
- In `ReadingPage`, `showEchoSheet = true` opens the sheet in `BottomDrawer`; dismissal resets state.

- [x] **Step 2: Run integration tests red**

Run: `./gradlew testDevDebugUnitTest --tests "*ContentEchoIntegrationTest"`
Expected: missing parameter / sheet branch.

- [x] **Step 3: Wire Content.kt**

- Add parameter `echoResult: ArticleSignalResult? = null` and `onOpenEchoSheet: () -> Unit = {}` to `Content`.
- In `item` containing `Metadata`, right after `HeadlineSignalBadges`, render:
  ```kotlin
  if (headlineSignalsEnabled && echoResult?.isNoteworthy == true) {
      EchoSignalGauges(
          result = echoResult,
          onClick = onOpenEchoSheet,
          modifier = Modifier.padding(bottom = 8.dp),
      )
  }
  ```

- [x] **Step 4: Wire ReadingPage.kt**

- Read `val echoState by readingViewModel.echoSignalState.collectAsState()`.
- Add `readingUiState.showEchoSheet` to `isAnySheetOpen`.
- Add dismiss to outside-click and BackHandler:
  ```kotlin
  if (readingUiState.showEchoSheet) {
      readingViewModel.dismissEchoSheet()
  }
  ```
- In `BottomDrawer.sheetContent`:
  ```kotlin
  if (readingUiState.showEchoSheet && echoState is EchoSignalState.Ready) {
      EchoSignalDetailsSheet(
          result = (echoState as EchoSignalState.Ready).result,
          onClose = { readingViewModel.dismissEchoSheet() },
      )
  }
  ```
- Pass to `Content`:
  ```kotlin
  echoResult = (echoState as? EchoSignalState.Ready)?.result,
  onOpenEchoSheet = { readingViewModel.openEchoSheet() },
  ```

- [x] **Step 5: Run integration tests green**

Run: `./gradlew testDevDebugUnitTest --tests "*ContentEchoIntegrationTest" --rerun`
Expected: all integration tests pass.

- [x] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/page/home/read app/src/test/java/com/mckimquyen/reader/ui/page/home/read
git commit -m "feat(echo-01): wire echo signals into reader content and drawer"
```

Findings: the section entry point `EchoSignalSection` was added to `EchoSignalGauges.kt` to enforce the shared `LocalHeadlineSignalsEnabled` at render time (the ViewModel cannot see it). Tests separately prove: (a) noteworthy + enabled shows gauges, (b) noteworthy + toggle-off hides gauges (this is where the toggle is enforced), (c) each non-ready state shows nothing (tested one `setContent` per test to avoid Compose's rule re-composition restriction), (d) ready-but-ordinary shows nothing, (e) click invokes callback once. 7 integration tests; 218 across the combined group.

### Task 6: Full verification, hardware smoke on Pixel 7 Pro, and documentation

**Files:** `app/src/androidTest`, `doc/task/done/ECHO-01_bias-radar-sentiment-gauge_DONE.md`, `doc/task/todo/16_ECHOCHAMBER_AND_BIAS_COMPASS.md`, `doc/feature.md`, remove `ECHO-01_bias-radar-sentiment-gauge.md`.

- [x] **Step 1: Write instrumentation device test**

Create `app/src/androidTest/java/com/mckimquyen/reader/ui/page/headline/EchoSignalDeviceTest.kt` with `createAndroidComposeRule<ComponentActivity>`:
- Noteworthy result renders gauges with tags on real Pixel display.
- Clicking gauge opens sheet with disclaimer and passages.
- Ordinary result renders no gauge.

- [x] **Step 2: Run full unit suite and lint**

Run:
```bash
./gradlew testDevDebugUnitTest --rerun
./gradlew lintDevDebug --rerun-tasks
./gradlew assembleDevDebug assembleDevDebugAndroidTest
```
Record test counts, 0 errors, warning counts.

- [x] **Step 3: Run instrumentation on Pixel 7 Pro**

Verify device: `adb devices` shows `2B051FDH3006MU`.
Install latest debug APK + test APK.
Run only the targeted test via `am instrument`:
```bash
adb -s 2B051FDH3006MU shell am instrument -w -e class com.mckimquyen.reader.ui.page.headline.EchoSignalDeviceTest com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner
```
Expected: OK.

- [x] **Step 4: Manual smoke test on Pixel 7 Pro**

1. Back up Room DB before smoke.
2. Check `topResumedActivity` before every touch. Stop if `AdActivity` appears (R4).
3. Open an ordinary article (e.g. Storm Boelo BBC news): verify no Hype gauge appears (ordinary articles stay clean).
4. Open an article with promotional wording: verify Hype gauge appears under Metadata.
5. Tap Hype gauge: verify BottomDrawer opens showing Hype score, disclaimer, and flagged passages.
6. Toggle `Headline signals` OFF in Privacy Settings: verify both EXC-04 and ECHO-01 disappear.
7. Restore Room backup; verify integrity and counts.

- [x] **Step 5: Completion report and documentation**

Create `doc/task/done/ECHO-01_bias-radar-sentiment-gauge_DONE.md` with:
- Scope: Hype/PR + tone only, offline heuristic, no political bias claims.
- Test counts, lint output, instrumentation results.
- Limitations documented honestly.
- Update `16_ECHOCHAMBER_AND_BIAS_COMPASS.md` and `doc/feature.md`.
- Remove `doc/task/todo/ECHO-01_bias-radar-sentiment-gauge.md`.

- [x] **Step 6: Commit**

```bash
git add app/src doc/ docs/superpowers/plans/2026-11-01-echo-01-local-bias-radar.md
git commit -m "feat(echo-01): complete local hype and sentiment signals"
```

Findings: smoke trên Pixel 7 Pro phát hiện một bug có sẵn từ EXC-04: `HomeEntry` đọc preference một lần lúc khởi động, nên công tắc tắt không có tác dụng ngay; sửa bằng `DataStore.headlineSignalsFlow()` reactive. Smoke thật: bài PR đạt 100% (cam + cảnh báo), bài thường không hiện thanh nào, tắt công tắc thì cả badge lẫn thanh đều biến mất. Sự cố: lúc khôi phục công tắc, người làm đã xoá nhầm DataStore khiến cài đặt của owner trên Pixel bị mất (Room nguyên vẹn); quy tắc mới ghi vào memory: sao lưu DataStore cùng Room, không bao giờ `rm`. Tự đóng quảng cáo thành công. 20 instrumentation tests.

## Final Verification Checklist

- [ ] Pure JVM tests: Hype score monotonic, length-normalized, 0..100 clamped, tone separate, up to 8 passages, 12k bound, surrogate-safe.
- [ ] No political bias claims; disclaimer present in UI.
- [ ] Only noteworthy articles show gauges (ordinary articles stay clean).
- [ ] Hype >= 70 styled with warning color.
- [ ] Shared `Headline signals` toggle hides both EXC-04 and ECHO-01.
- [ ] 14 `echo_*` strings across all 38 locales, parity 6/6.
- [ ] Full unit suite 1,526+ pass, 0 failures. Lint 0 errors.
- [ ] Instrumentation pass on Pixel 7 Pro `2B051FDH3006MU`.
- [ ] Smoke verified on device; Room restored.
