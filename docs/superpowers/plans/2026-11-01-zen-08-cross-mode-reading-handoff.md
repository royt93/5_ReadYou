# ZEN-08 Cross-mode Reading Handoff Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Continue approximately at the same reading position when moving between normal reading, RSVP, and TTS, using existing persisted `ArticleReadPosition.progress` without Room migration.

**Architecture:** Add pure `ReadingPositionMapper` to map body-item fraction to list fraction, RSVP token index, and paragraph offset. Hoist one `BodyItemRange` from `ReadingPage` into `Content`; normal reading remains the source of live body fraction, RSVP accepts a start fraction and returns a stop fraction on dismiss, and TTS receives a start fraction and slices plain text at the closest paragraph boundary. Keep existing Room/WebDAV position format and `TtsManager` unchanged.

**Tech Stack:** Kotlin, Jetpack Compose, Room v20 existing `ArticleReadPositionDao`, `RsvpViewModel`, `HtmlCompat`, JUnit4, MockK, Robolectric Compose, AndroidX instrumentation.

## Global Constraints

- Branch `dev` only; no worktrees.
- Room schema remains version 20; do not add migration or change `ArticleReadPosition` / WebDAV sync contract.
- No AI, network, API key, ad, IAP, money feature, or new UI strings.
- Do not change `TtsManager`, `RsvpTokenizer`, or `HtmlToComposable`.
- Keep progress approximate, derived from `bodyRange`, not from metadata/ad/quiz list items.
- Read/DB writes remain on IO; CPU text conversion remains on the existing `defaultDispatcher`.
- Every `@HiltViewModel` has exactly one `@Inject` constructor.
- Name thresholds/constants; no business magic numbers.
- Device target locked to Pixel 7 Pro `2B051FDH3006MU`; never use `connectedDevDebugAndroidTest`.
- Before smoke, back up Room `Reader`, `Reader-wal`, `Reader-shm` and `files/datastore/settings.preferences_pb`; verify non-empty, `PRAGMA integrity_check`=`ok` on a copy; read DB only with all three Room files; restore both Room and DataStore using `adb shell "run-as com.mckimquyen.reader sh -c 'cat > PATH'" < local`; verify `cmp` for all four files. Never delete DataStore.
- Before every UI tap, check foreground; for full-screen `AdActivity`, dump fresh hierarchy after `rm /sdcard/ui.xml`, tap only labelled `Tiếp tục đến ứng dụng`, never ad creative or stale coordinates, and report ads.
- Gradle swallows test stdout: inspect actual failures/counts from `app/build/test-results/testDevDebugUnitTest/*.xml`.

---

## File map

- Create `app/src/main/java/com/mckimquyen/reader/domain/readposition/ReadingPositionMapper.kt`: pure conversions and named thresholds.
- Create `app/src/test/java/com/mckimquyen/reader/domain/readposition/ReadingPositionMapperTest.kt`: boundary and empty-input behavior.
- Modify `app/src/main/java/com/mckimquyen/reader/ui/page/rsvp/RsvpViewModel.kt`: optional start fraction, preserve one-time load semantics, expose fraction at dismissal through current `uiState` (no persistence here).
- Modify `app/src/test/java/com/mckimquyen/reader/ui/page/rsvp/RsvpViewModelTest.kt`: start mapping and clamping.
- Modify `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/Content.kt`: accept shared `BodyItemRange`; stop allocating private range.
- Modify `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingPage.kt`: own/remember `BodyItemRange`, map list position to body fraction, handle RSVP open/dismiss and TTS start fraction.
- Modify `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModel.kt`: accept start fraction for TTS and pure slice after `HtmlCompat` conversion.
- Create `app/src/test/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModelPositionHandoffTest.kt`: TTS paragraph slicing and beginning/end fallbacks.
- Create `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/read/ReadingPositionHandoffDeviceTest.kt`: real renderer mapping, RSVP start/stop callback and TTS slice behavior at component boundary.
- Create `doc/task/done/ZEN-08_cross-mode-reading-handoff_DONE.md`; update `doc/feature.md` and row in `doc/task/todo/08_ZEN_FOCUS_AND_SPEED_READING.md`; remove `doc/task/todo/ZEN-08_cross-mode-reading-handoff.md`.

## Interfaces

`ReadingPositionMapper` produces these signatures:

```kotlin
object ReadingPositionMapper {
    const val RESTART_BELOW_FRACTION = 0.02f
    const val TREAT_AS_FINISHED_FRACTION = 0.98f
    const val MIN_RSVP_WORDS_TO_SCROLL = 20

    fun bodyFractionFromList(firstVisibleIndex: Int, bodyStart: Int, bodyCount: Int): Float
    fun listIndexFromBodyFraction(fraction: Float, bodyStart: Int, bodyCount: Int): Int
    fun listFractionFromBodyFraction(fraction: Float, bodyStart: Int, bodyCount: Int, totalItems: Int): Float
    fun tokenIndexFromFraction(fraction: Float, tokenCount: Int): Int
    fun fractionFromTokenIndex(index: Int, tokenCount: Int): Float
    fun paragraphStartOffset(plainText: String, fraction: Float): Int
}
```

`RsvpViewModel.loadContent(content: String, startFraction: Float = 0f)` remains source-compatible. `ReadingViewModel.togglePlayAudio(startFraction: Float = 0f)` remains source-compatible. `Content(..., bodyRange: BodyItemRange = remember { BodyItemRange() })` keeps existing call sites compiling; `ReadingPage` passes its single shared range explicitly.

---

### Task 1: Pure position mapper

**Files:** mapper + unit test listed above.

- [ ] **Step 1: Add failing mapper tests.** Create test with these cases:

```kotlin
@RunWith(JUnit4::class)
class ReadingPositionMapperTest {
    @Test fun maps_body_fraction_to_body_list_index() {
        assertEquals(4, ReadingPositionMapper.listIndexFromBodyFraction(0.5f, 2, 6))
    }
    @Test fun maps_body_fraction_back_to_global_list_fraction() {
        assertEquals(0.4f, ReadingPositionMapper.listFractionFromBodyFraction(0.5f, 2, 6, 10), 0.0001f)
    }
    @Test fun maps_fraction_to_token_and_back() {
        assertEquals(4, ReadingPositionMapper.tokenIndexFromFraction(0.5f, 9))
        assertEquals(0.5f, ReadingPositionMapper.fractionFromTokenIndex(4, 9), 0.0001f)
    }
    @Test fun clamps_fraction_and_empty_ranges() {
        assertEquals(0, ReadingPositionMapper.listIndexFromBodyFraction(-1f, 3, 2))
        assertEquals(0f, ReadingPositionMapper.bodyFractionFromList(5, 3, 0), 0f)
        assertEquals(0, ReadingPositionMapper.tokenIndexFromFraction(0.5f, 0))
        assertEquals(0f, ReadingPositionMapper.fractionFromTokenIndex(0, 0), 0f)
    }
    @Test fun maps_positions_before_and_after_body_to_edges() {
        assertEquals(0f, ReadingPositionMapper.bodyFractionFromList(1, 3, 4), 0f)
        assertEquals(1f, ReadingPositionMapper.bodyFractionFromList(8, 3, 4), 0f)
    }
    @Test fun paragraphOffset_uses_nearest_preceding_blank_line_boundary() {
        val text = "First paragraph.\n\nSecond paragraph.\n\nThird paragraph."
        assertEquals(text.indexOf("Second"), ReadingPositionMapper.paragraphStartOffset(text, 0.5f))
    }
    @Test fun paragraphOffset_handles_no_break_empty_text_and_fraction_edges() {
        assertEquals(0, ReadingPositionMapper.paragraphStartOffset("One line only", 0.8f))
        assertEquals(0, ReadingPositionMapper.paragraphStartOffset("", 0.5f))
        assertEquals(0, ReadingPositionMapper.paragraphStartOffset("A\n\nB", 0f))
        assertEquals("A\n\nB".length, ReadingPositionMapper.paragraphStartOffset("A\n\nB", 1f))
    }
}
```

Use imports `org.junit.Assert.assertEquals`, `org.junit.Test`, `org.junit.runner.RunWith`, `org.junit.runners.JUnit4`.

- [ ] **Step 2: Verify red.** Run `./gradlew testDevDebugUnitTest --tests "*ReadingPositionMapperTest"`; unresolved `ReadingPositionMapper` expected.
- [ ] **Step 3: Implement minimal pure mapper.** Define constants above. Treat body item indices as `[bodyStart, bodyStart + bodyCount - 1]`; count ≤1 maps to start. Fractions clamp to `[0f, 1f]`; token indices use rounded `(count - 1) * fraction`, count≤0→0; inverse uses `index / (count - 1)`, count≤1→0. Global list fraction maps target list index divided by `(totalItems - 1)`, total≤1→0. Paragraph offset chooses nearest preceding blank-line boundary at/under `plainText.length * clampedFraction`, with 0/length at fraction endpoints. No Android dependencies.
- [ ] **Step 4: Verify green.** Run mapper test; expected all 7 pass.
- [ ] **Step 5: Commit.** `git add app/src/main/java/com/mckimquyen/reader/domain/readposition app/src/test/java/com/mckimquyen/reader/domain/readposition && git commit -m "feat(zen-08): add pure cross-mode reading position mapper"` (include required Co-Authored-By trailer).

### Task 2: RSVP starts at mapped fraction

**Files:** `RsvpViewModel.kt`, `RsvpViewModelTest.kt`.

- [ ] **Step 1: Add failing tests.** Add:

```kotlin
@Test fun loadContent_startsAtRequestedFraction() {
    viewModel.loadContent((1..9).joinToString(" ") { "word$it" }, startFraction = 0.5f)
    assertEquals(4, viewModel.uiState.value.currentIndex)
}
@Test fun loadContent_clampsStartFraction_andDefaultsToStart() {
    viewModel.loadContent("One Two Three", startFraction = 3f)
    assertEquals(2, viewModel.uiState.value.currentIndex)
    viewModel.loadContent("One Two Three")
    assertEquals(0, viewModel.uiState.value.currentIndex)
}
```

- [ ] **Step 2: Red.** Run `./gradlew testDevDebugUnitTest --tests "*RsvpViewModelTest"`; compile or assertion failure expected.
- [ ] **Step 3: Implement.** Add trailing `startFraction: Float = 0f`; tokenize as before, then set `currentIndex = ReadingPositionMapper.tokenIndexFromFraction(startFraction, tokens.size)`. Keep playback paused and completion false on load. Do not alter `seekTo`, `play`, or saved calibration behavior.
- [ ] **Step 4: Green.** Re-run RsvpViewModelTest and `RsvpTokenizerTest`; all pass.
- [ ] **Step 5: Commit.** `git add app/src/main/java/com/mckimquyen/reader/ui/page/rsvp/RsvpViewModel.kt app/src/test/java/com/mckimquyen/reader/ui/page/rsvp/RsvpViewModelTest.kt && git commit -m "feat(zen-08): start RSVP at shared reading fraction"` (trailer required).

### Task 3: TTS paragraph slicing

**Files:** `ReadingViewModel.kt`, new `ReadingViewModelPositionHandoffTest.kt`.

- [ ] **Step 1: Add failing Robolectric tests.** Create fixture following `ReadingViewModelHtmlWorkerThreadTest`: `@RunWith(RobolectricTestRunner::class)`, `@Config(sdk=[34], application=Application::class)`, mock `RssSv`, `RssHelper`, `TtsManager`, `GeminiSummaryService`, `ZenAudioManager`; set Main to `StandardTestDispatcher`, inject same test scheduler as `defaultDispatcher`, provide `ttsManager.ttsState = MutableStateFlow(TtsState.IDLE)`, static-mock `HtmlCompat.fromHtml` to strip HTML, and always `resetMain`/`unmockkStatic` in `@After`. Test default fraction plays full text; fraction 0.5 plays from second paragraph for fixture `"<p>First paragraph.</p><p>Second paragraph.</p><p>Third paragraph.</p>"` after HtmlCompat returns `"First paragraph.\n\nSecond paragraph.\n\nThird paragraph."`; fraction 0.99 plays from beginning.
- [ ] **Step 2: Red.** Run `./gradlew testDevDebugUnitTest --tests "*ReadingViewModelPositionHandoffTest"`; expected missing parameter/method behavior.
- [ ] **Step 3: Implement.** Change `togglePlayAudio(startFraction: Float = 0f)` and `playCurrentContent(startFraction: Float = 0f)`; when stopped, stop TTS unchanged. After existing `HtmlCompat` work on `defaultDispatcher`, apply the 2% / 98% rule; otherwise `plainText.substring(ReadingPositionMapper.paragraphStartOffset(plainText, startFraction))`. Then call existing `ttsManager.play(...)` unchanged. TTS does not write progress.
- [ ] **Step 4: Green.** Run new test and existing `ReadingViewModelHtmlWorkerThreadTest`; verify text parsing still occurs on worker dispatcher.
- [ ] **Step 5: Commit.** `git add app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModel.kt app/src/test/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModelPositionHandoffTest.kt && git commit -m "feat(zen-08): resume TTS from nearest paragraph"` (trailer required).

### Task 4: Hoist body range and connect normal reader ↔ RSVP ↔ TTS

**Files:** `Content.kt`, `ReadingPage.kt`, relevant existing Content tests.

- [ ] **Step 1: Add focused failing helper tests for dismissal threshold** to `ReadingPositionMapperTest`:

```kotlin
@Test fun shouldReturnToReader_onlyAfterMinimumRsvpAdvance() {
    assertFalse(ReadingPositionMapper.shouldHandoffFromRsvp(startIndex = 10, currentIndex = 29))
    assertTrue(ReadingPositionMapper.shouldHandoffFromRsvp(startIndex = 10, currentIndex = 30))
}
```

- [ ] **Step 2: Red.** Run mapper test; expected unresolved helper.
- [ ] **Step 3: Add `shouldHandoffFromRsvp(startIndex, currentIndex)` using `MIN_RSVP_WORDS_TO_SCROLL`; add the specified helper test.**
- [ ] **Step 4: Wire `BodyItemRange`.** `ReadingPage` remembers one `BodyItemRange`; pass it to `Content`. Add trailing `bodyRange: BodyItemRange = remember { BodyItemRange() }` in `Content`, remove its private `remember { BodyItemRange() }`, and retain current `bodyRange.clear()`/`recordRange()`/`ReadingCompletionTracker` use. `onRsvpReading` calculates body fraction from `listState.firstVisibleItemIndex`, `bodyRange.start`, `bodyRange.count`; if `<0.02` or `>=0.98`, pass 0, else pass that fraction to RSVP. Because `RsvpReaderDialog` currently calls `loadContent(content)` in `LaunchedEffect(content)`, add trailing `startFraction: Float = 0f` and call `loadContent(content, startFraction)`.
- [ ] **Step 5: Wire dismissal callback.** Keep RSVP start index in `remember(showRsvpDialog, articleId)` state, or compute `startIndex = tokenIndexFromFraction(startFraction, state.tokens.size)` before opening. On dialog dismiss, read `rsvpViewModel.uiState.value.currentIndex`; if `shouldHandoffFromRsvp(startIndex, currentIndex)`, calculate fraction with `fractionFromTokenIndex`, map to target body list index, `listState.scrollToItem(targetIndex)` in remembered coroutine scope, then call `readingViewModel.saveReadPosition(articleId, listFractionFromBodyFraction(fraction,...))`. Preserve `onDismiss` and do not save if body range is empty. Ensure manual dismiss and system/back dismiss use same callback and `showRsvpDialog` becomes false exactly once.
- [ ] **Step 6: Wire TTS.** On `TopBar.onPlayAudio`, calculate current body fraction from list/body range and call `readingViewModel.togglePlayAudio(fraction)`. Existing auto-TTS callers keep default start fraction 0; TTS never writes back progress.
- [ ] **Step 7: Run existing UI and VM tests.** `./gradlew testDevDebugUnitTest --tests "*Content*" --tests "*ReadingViewModel*" --tests "*Rsvp*" --rerun`; fix compilation and read failures from XML. Ensure every ReadingViewModel unit test injects a test `defaultDispatcher` where its init observer may run, preventing Main access after `resetMain()`.
- [ ] **Step 8: Run compile.** `./gradlew compileDevDebugKotlin`; expected BUILD SUCCESSFUL.
- [ ] **Step 9: Commit.** Stage `Content.kt`, `ReadingPage.kt`, `RsvpReaderDialog.kt`, mapper and relevant tests; `git commit -m "feat(zen-08): connect reader, RSVP and TTS positions"` (trailer required).

### Task 5: Integration tests for RSVP return and content wiring

**Files:** `ReadingPositionHandoffTest.kt` (new Robolectric Compose) and `ReadingPositionHandoffDeviceTest.kt` (new androidTest).

- [ ] **Step 1: Add instrumentation test first.** Test mapper on renderer boundary with 9 RSVP words: `loadContent(..., startFraction=0.5f)`, assert `currentIndex == 4`; after `seekTo(6)`, map token fraction to body index; assert index is in `[bodyStart, bodyStart + bodyCount - 1]`. No Room/DB or external network.
- [ ] **Step 2: Add Robolectric Compose test first.** Use `@Config(sdk=[34], application=Application::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)` and `createAndroidComposeRule<ComponentActivity>()`; test `Content` receives shared `BodyItemRange`, body range is populated after composition, and mapping fraction targets only body items. One `setContent` per test; for any no-card/no-node assertion use `onAllNodesWithTag(...).assertCountEquals(0)`; scroll a lazy child into view before clicking.
- [ ] **Step 3: Run tests red then implement only test scaffolding required.** Robolectric: `./gradlew testDevDebugUnitTest --tests "*ReadingPositionHandoffTest"`; device test compiles as part of `assembleDevDebugAndroidTest`.
- [ ] **Step 4: Run focused tests.** `./gradlew testDevDebugUnitTest --tests "*ReadingPositionHandoffTest" --tests "*ReadingPositionMapperTest" --tests "*RsvpViewModelTest" --tests "*ReadingViewModelPositionHandoffTest"`.
- [ ] **Step 5: Commit.** `git add app/src/test/java/com/mckimquyen/reader/ui/page/home/read app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/read && git commit -m "test(zen-08): cover cross-mode reading handoff"` (trailer required).

### Task 6: Full verification and Pixel smoke

- [ ] **Step 1: Full test/lint/build.** Run sequentially:

```bash
./gradlew testDevDebugUnitTest --rerun
./gradlew lintDevDebug --rerun-tasks
./gradlew assembleDevDebug assembleDevDebugAndroidTest
```

Read unit counts/failures from all XML under `app/build/test-results/testDevDebugUnitTest/`; read lint error/warning counts from `app/build/reports/lint-results-devDebug.xml`. Record actual values, do not infer.

- [ ] **Step 2: Confirm only locked device.** `adb devices -l`; use only `adb -s 2B051FDH3006MU ...`. Confirm model `Pixel 7 Pro` before installation.
- [ ] **Step 3: Install and run instrumentation.** Resolve APK with `find` (APK filename has spaces); install app and test APK, run exactly:

```bash
adb -s 2B051FDH3006MU shell am instrument -w -e class com.mckimquyen.reader.ui.page.home.read.ReadingPositionHandoffDeviceTest com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner
```

Uninstall `com.mckimquyen.reader.test` after pass.

- [ ] **Step 4: Backup before smoke.** Force-stop first. `adb exec-out run-as com.mckimquyen.reader cat databases/Reader > /tmp/zen08/Reader` and repeat for `Reader-wal`, `Reader-shm`, and `files/datastore/settings.preferences_pb`. Require every file non-empty. Copy all Room files to a temp directory and run `sqlite3 /tmp/zen08-check/Reader "PRAGMA integrity_check;"` → `ok`; inspect article count on copy. Do not remove datastore.

- [ ] **Step 5: Smoke normal → RSVP → normal → TTS.** Launch via `monkey`; inspect `topResumedActivity`. If full-screen ad appears, wait for control; remove old dump, take fresh `uiautomator dump`, find labelled `Tiếp tục đến ứng dụng`, check foreground immediately before tapping it. Open a long article, scroll to about 40% of body, tap `⚡` RSVP, verify initial RSVP token belongs near mid-body (not first token), play at least 20 tokens, close dialog, verify normal list scrolls to mapped body item and progress is saved. Tap TTS and verify speech begins at nearest paragraph boundary (listen/observe actual device; do not claim if not verifiable). Check foreground before every tap. Banner ad may remain only if not obscuring UI. Record observed behavior and any ad.

- [ ] **Step 6: Restore regardless of smoke outcome.** Force-stop app. Restore all four backed-up files using:

```bash
adb -s 2B051FDH3006MU shell "run-as com.mckimquyen.reader sh -c 'cat > databases/Reader'" < /tmp/zen08/Reader
adb -s 2B051FDH3006MU shell "run-as com.mckimquyen.reader sh -c 'cat > databases/Reader-wal'" < /tmp/zen08/Reader-wal
adb -s 2B051FDH3006MU shell "run-as com.mckimquyen.reader sh -c 'cat > databases/Reader-shm'" < /tmp/zen08/Reader-shm
adb -s 2B051FDH3006MU shell "run-as com.mckimquyen.reader sh -c 'cat > files/datastore/settings.preferences_pb'" < /tmp/zen08/settings.preferences_pb
```

Read each file back with `adb exec-out run-as ... cat` and verify `cmp`; verify Room `integrity_check` on a copy with all three files. Delete only host temp backup directory, never on device.

- [ ] **Step 7: Commit verification-independent changes only after pass.** Record exact test/lint/build/instrumentation/smoke results in DONE doc. If any test/build/smoke step fails, do not claim it passed; fix or report blocker before completion.

### Task 7: Completion docs

**Files:** create `doc/task/done/ZEN-08_cross-mode-reading-handoff_DONE.md`; modify `doc/feature.md`, `doc/task/todo/08_ZEN_FOCUS_AND_SPEED_READING.md`; remove `doc/task/todo/ZEN-08_cross-mode-reading-handoff.md`.

- [ ] **Step 1: Write DONE report.** Include honest precision limits, no TTS write-back, Room remains v20, exact tests and observed device result, ad appearances, all four restored files verified, test counts, lint warnings, and commits.
- [ ] **Step 2: Update feature index and epic row.** Add ZEN-08 under recently implemented in `doc/feature.md`; change epic row from `💭 Idea mới` to `✅ Done` linking `../done/ZEN-08_cross-mode-reading-handoff_DONE.md`.
- [ ] **Step 3: Remove old todo.** `git rm doc/task/todo/ZEN-08_cross-mode-reading-handoff.md`.
- [ ] **Step 4: Commit.** `git add doc && git commit -m "docs(zen-08): record cross-mode reading handoff completion"` (trailer required).

## Final verification checklist

- [ ] Unit mapper handles empty/one-item boundaries, clamps fraction and paragraph edges.
- [ ] RSVP starts at mapped token, default remains index 0, seek/play calibration behavior unchanged.
- [ ] Normal reader → RSVP uses body range, not metadata/ad/quiz items.
- [ ] RSVP dismissal advances ≥20 words before returning; <20 does not jump; manual/back dismissal shares path.
- [ ] TTS begins at nearest paragraph boundary, with <2% and ≥98% restart-at-start behavior; no TTS progress write-back.
- [ ] All Room v20 and WebDAV contracts unchanged.
- [ ] Full unit suite, lint, debug and androidTest APK builds pass; device instrumentation and smoke truthfully reported.
- [ ] Original Room (all 3 files) and DataStore restored and byte-compared after smoke.
