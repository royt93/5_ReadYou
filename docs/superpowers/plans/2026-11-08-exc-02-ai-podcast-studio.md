# EXC-02: AI Podcast Studio (Single-Article Edition) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn any currently-open article in `ReadingPage` into a lively 2-host spoken dialogue (Alex & Sam) via Gemini with strict privacy boundaries (≤250 chars snippet, title only, explicit consent), playing via the existing dual-voice `CommuteAudioPlayer` with an animated Canvas playback waveform, honest transparency badges, and zero interference with the user's stored morning CommuteCast bulletin.

**Architecture:** A new domain model `PodcastStudioEpisode` (wrapping `CommuteEpisode` with a `PodcastScriptSource` enum: `GEMINI` vs `HEURISTIC`); a dedicated `PodcastStudioScriptService` routing single-article prompts through `AiRequestGateway` with failover and offline heuristic fallback; a `PodcastStudioViewModel` owning in-memory-only episode dispatch (asserting `CommuteEpisodeStore.save()` is NEVER called) and conflict confirmation; and a `PodcastStudioSheet` (a full-width `Dialog` anchored to the bottom, same pattern as `CommuteCastDialog`) with a speaker-colored Canvas waveform, active dialogue tracking, and honest voice/script badges.

**Tech Stack:** Kotlin 2.1.0, Jetpack Compose / Material3 1.0.1, Android TTS via `CommuteAudioPlayer`, DataStore Preferences, Robolectric 4.12.2 / JUnit4 / MockK, native Android SQLite runtime for instrumentation on Tecno BG6 `118743744X002560`.

## Global Constraints

- **Language:** Vietnamese communication with the user; exact code symbols and file paths preserved.
- **Branch:** `dev` only; no worktree; no git push; never suggest or prompt PR creation.
- **Git commits:** End every git commit message with `Co-Authored-By: Claude Code <noreply@anthropic.com>`. Do NOT commit any `.idea/` changes. Never chain git commit after a build with `&&` unless the build exit code has been checked (`build_exit=0`).
- **Device lock:** Tecno BG6 `118743744X002560` is the ONLY target device for this session. Never touch KJ7 `115333744A005844`, A50s, S24U, or Pixel. Run `adb devices` before any device command. Do NOT use the MCP screenshot tool (hard-wired to KJ7); use `adb -s 118743744X002560 exec-out screencap -p` instead.
- **Device safety:** Never run `connectedDevDebugAndroidTest`. Take a fresh backup of Room (3 files) and `files/datastore/settings.preferences_pb` before any device smoke; restore after smoke and verify bit-for-bit equality with `cmp`. Never delete the DataStore. Close full-screen ads only after a fresh uiautomator dump and foreground check using the labelled control.
- **Room database:** Version stays 20; no schema migration; no new entities; no new third-party dependencies; no new system push notifications.
- **Privacy boundary:** Data sent to Gemini is strictly `title` plus `shortDescription` truncated to at most 250 characters. `fullContent`, URL, feed name/URL, and account identifiers are NEVER sent. First-time use prompts for explicit consent; declining uses the on-device heuristic generator.
- **Inherited player limits:** Single-article episodes are kept in-memory only; `CommuteEpisodeStore.save()` is NEVER called. `CommuteAudioPlayer.handleUtteranceStart` unconditionally starts the lofi `ambientLoop` and `CommuteMediaSessionService`, which is accepted and stated in the DONE report.
- **Strings:** All new string resources must be added to all 38 locale files (`values/strings.xml`, `values-vi/`, and the 36 other locale dirs; English fallback except hand-authored English and Vietnamese). To dodge the `PluralsCandidate` lint warning, never place a plural noun immediately after `%d` (use e.g. `%1$d-line script` instead of `%1$d lines`).
- **Testing rigor:** Strict TDD for every task (failing test first, confirm red for the right reason by reading XML/logs, minimal code, green, mutation check where appropriate, focused commit). Always write Gradle output to a file and read test XML counts. Baseline lint is 2,463 warnings with 0 errors; maintain 0 errors and 0 EXC-02 hits.

---

## File Structure

### New Files
- `app/src/main/java/com/mckimquyen/reader/domain/model/podcast/PodcastStudioModels.kt` — Domain models: `PodcastScriptSource` enum and `PodcastStudioEpisode` wrapper.
- `app/src/main/java/com/mckimquyen/reader/domain/sv/PodcastStudioScriptService.kt` — Prompt building, Gemini invocation via `AiRequestGateway`, and single-article heuristic fallback.
- `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/podcaststudio/PodcastStudioUiState.kt` — State machine for consent, conflict, loading, ready, and error.
- `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/podcaststudio/PodcastStudioViewModel.kt` — Orchestrates script generation, player state binding, and conflict dialogs.
- `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/podcaststudio/PodcastStudioWaveform.kt` — Canvas-drawn playback visualizer animating bars with speaker accent colors.
- `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/podcaststudio/PodcastStudioSheet.kt` — Modal bottom sheet displaying dialogue, waveform, and honest badges.
- `app/src/androidTest/java/com/mckimquyen/reader/infrastructure/podcast/PodcastStudioDeviceTest.kt` — On-device instrumentation verifying TTS voice selection and episode binding.

### Modified Files
- `app/src/main/java/com/mckimquyen/reader/ui/ext/ExtDataStore.kt` — Add `PodcastStudioGeminiConsent` key and Context getter/setter.
- `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/TopBar.kt` — Add "🎙️ Podcast Studio" overflow menu item.
- `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingPage.kt` — Wire TopBar click to open `PodcastStudioSheet`.
- `app/src/main/res/values*/strings.xml` — 8 new string keys across all 38 locale files.

---

## Task Decomposition

### Task 1: Domain Models and DataStore Consent Key

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/podcast/PodcastStudioModels.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/ext/ExtDataStore.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/model/podcast/PodcastStudioModelsTest.kt`

**Interfaces:**
- Produces:
  - `enum class PodcastScriptSource { GEMINI, HEURISTIC }`
  - `data class PodcastStudioEpisode(val episode: CommuteEpisode, val source: PodcastScriptSource)`
  - `object PodcastStudioGeminiConsent : DataStoreKeys<Boolean>()` using `booleanPreferencesKey("podcast_studio_gemini_consent")`; absence of the key means undecided
  - `val Context.podcastStudioGeminiConsent: Boolean?` (returns `this.dataStore.get(DataStoreKeys.PodcastStudioGeminiConsent)`, which is already `null` when never written; no `?: default`)
  - `suspend fun Context.setPodcastStudioGeminiConsent(agreed: Boolean)` (`this.dataStore.put(DataStoreKeys.PodcastStudioGeminiConsent, agreed)`; note `DataStore.put` is `edit { withContext(Dispatchers.IO) { ... } }`, so tests must wait for the write with a bounded polling helper, not read it back immediately)

- [ ] **Step 1: Write failing unit test for models and DataStore consent.**
  - Test that `PodcastStudioEpisode` holds `CommuteEpisode` and source.
  - Test that `podcastStudioGeminiConsent` defaults to `null` (undecided), persists `true` and `false`, and reads back correctly.

- [ ] **Step 2: Run test and verify it fails.**
  ```bash
  ./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.reader.domain.model.podcast.PodcastStudioModelsTest'
  ```
  Expected: FAIL with compilation error (unresolved references).

- [ ] **Step 3: Implement domain models and DataStore key.**
  - In `PodcastStudioModels.kt`, define `PodcastScriptSource` and `PodcastStudioEpisode`.
  - In `ExtDataStore.kt`, declare `PodcastStudioGeminiConsent` under `DataStoreKeys`, add getter `podcastStudioGeminiConsent: Boolean?`, and suspend helper.

- [ ] **Step 4: Run test and verify it passes.**
  ```bash
  ./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.reader.domain.model.podcast.PodcastStudioModelsTest'
  ```
  Expected: BUILD SUCCESSFUL, 0 failures.

- [ ] **Step 5: Mutation check.**
  - Invert default or omit key, verify test fails, restore.

- [ ] **Step 6: Commit.**
  ```bash
  git add app/src/main/java/com/mckimquyen/reader/domain/model/podcast/PodcastStudioModels.kt app/src/main/java/com/mckimquyen/reader/ui/ext/ExtDataStore.kt app/src/test/java/com/mckimquyen/reader/domain/model/podcast/PodcastStudioModelsTest.kt
  git commit -m "feat(exc-02): add podcast studio domain models and gemini consent datastore key"
  ```

---

### Task 2: Single-Article Script Service (Gemini + Offline Heuristic)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/sv/PodcastStudioScriptService.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/sv/PodcastStudioScriptServiceTest.kt`

**Interfaces:**
- Consumes:
  - `AiRequestGateway.execute(useCase = "podcast_studio", requestBody, keys)`
  - `CommuteScriptService.parseDialoguesFromJson(rawText)`
  - `Article(title, shortDescription)`
- Produces:
  - `@Singleton class PodcastStudioScriptService @Inject constructor(...)`
  - `suspend fun generate(article: Article, useGemini: Boolean, languageTag: String): PodcastStudioEpisode`
  - `fun buildPrompt(title: String, snippet: String, languageTag: String): String`
  - `fun sanitizeSnippet(raw: String): String` (max 250 chars)

- [ ] **Step 1: Write failing unit tests for prompt building, sanitization, and generation.**
  - `snippetTruncatedToAtMost250Characters`: string with 1000 characters is cut to 250.
  - `sensitiveFieldsNeverIncludedInPrompt`: verifies no URL, accountId, or feed info in prompt.
  - `useGeminiFalseCallsHeuristicDirectly`: mock gateway, verify 0 gateway calls and `source == HEURISTIC`.
  - `gatewaySuccessReturnsGeminiSource`: returns valid JSON, parser produces dialogues, `source == GEMINI`.
  - `gatewayFailureFallsBackToHeuristic`: gateway throws IOException, service catches it and returns `source == HEURISTIC` without crashing.
  - `emptyArticleProducesValidFallbackEpisode`: empty title and description produce safe fallback.

- [ ] **Step 2: Run test and verify it fails.**
  ```bash
  ./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.reader.domain.sv.PodcastStudioScriptServiceTest'
  ```
  Expected: FAIL with compilation error.

- [ ] **Step 3: Implement PodcastStudioScriptService.**
  - Annotate `@Singleton` with `@Inject constructor(@ApplicationContext context: Context, gateway: AiRequestGateway, commuteScriptService: CommuteScriptService)`.
  - Build prompt asking for strict JSON array of `[{"speaker":"ALEX"|"SAM","text":"..."}]` deep-diving into the single article.
  - Wrap payload in Gemini REST body format `{"contents":[{"parts":[{"text": prompt}]}], "generationConfig":{"temperature":0.7}}`.
  - In `generate()`, if `!useGemini` or `!GeminiConfig.hasAnyKey()` -> immediately run `generateHeuristicSingleArticle()`.
  - Otherwise call `gateway.execute(useCase = "podcast_studio", requestBody = body)`; on success parse via `commuteScriptService.parseDialoguesFromJson(text)` -> `PodcastStudioEpisode(..., GEMINI)`.
  - Catch any `Exception` (except `CancellationException`) -> log and fallback to `generateHeuristicSingleArticle(article, languageTag)` -> `PodcastStudioEpisode(..., HEURISTIC)`.

- [ ] **Step 4: Run test and verify it passes.**
  ```bash
  ./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.reader.domain.sv.PodcastStudioScriptServiceTest'
  ```
  Expected: BUILD SUCCESSFUL, 0 failures.

- [ ] **Step 5: Mutation check.**
  - Change snippet limit from 250 to 500, verify `snippetTruncatedToAtMost250Characters` fails, restore.

- [ ] **Step 6: Commit.**
  ```bash
  git add app/src/main/java/com/mckimquyen/reader/domain/sv/PodcastStudioScriptService.kt app/src/test/java/com/mckimquyen/reader/domain/sv/PodcastStudioScriptServiceTest.kt
  git commit -m "feat(exc-02): add single-article podcast studio script service with gemini failover"
  ```

---

### Task 3: Localized Strings for All 38 Locales

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-vi/strings.xml`
- Modify: `app/src/main/res/values-*/strings.xml` (all remaining 36 locale directories)
- Test: `app/src/test/java/com/mckimquyen/reader/StringResourceParityTest.kt`

**Strings to add (8 keys; the two voice-mode badges are reused):**
1. `podcast_studio_title`: "Podcast Studio" (VI: "Podcast Studio")
2. `podcast_studio_menu`: "🎙️ Podcast Studio" (VI: "🎙️ Podcast Studio")
3. `podcast_studio_consent_title`: "Generate dialogue with AI?" (VI: "Tạo kịch bản bằng AI?")
4. `podcast_studio_consent_message`: "This sends only this article's title and a short preview (under 250 characters) to Google Gemini to write a 2-host spoken overview. Full article text, links, and your reading history are never sent." (VI: "Chỉ gửi tiêu đề và phần tóm tắt ngắn dưới 250 ký tự của bài này tới Google Gemini để biên soạn cuộc trò chuyện 2 người. Toàn văn bài báo, liên kết và lịch sử đọc không bao giờ được gửi.")
5. `podcast_studio_consent_agree`: "Agree and use AI" (VI: "Đồng ý dùng AI")
6. `podcast_studio_consent_decline`: "Use offline script" (VI: "Dùng kịch bản ngoại tuyến")
7. `podcast_studio_conflict_title`: "Replace currently playing audio?" (VI: "Dừng âm thanh đang phát?")
8. `podcast_studio_conflict_message`: "Starting this article podcast will pause your currently playing bulletin." (VI: "Bắt đầu phát podcast bài này sẽ dừng bản tin bạn đang nghe.")
9. `podcast_studio_badge_source_gemini`: "AI generated" (VI: "Kịch bản AI")
10. `podcast_studio_badge_source_heuristic`: "Offline script" (VI: "Kịch bản ngoại tuyến")

*(Note: voice-mode badge strings already exist in the repo as `commute_voice_mode_dual` and `commute_voice_mode_simulated`; reuse them, add no duplicates.)*

- [ ] **Step 1: Add strings to EN and VI first.**
- [ ] **Step 2: Run parity test to see 36 missing locales fail.**
  ```bash
  ./gradlew testDevDebugUnitTest --tests '*StringResourceParityTest'
  ```
  Expected: FAIL with missing keys in other locales.

- [ ] **Step 3: Propagate strings with English fallback to remaining 36 locales.**
  - Use Python script pattern from Task 5 of EXC-12.
  - Verify every file has all 8 keys.
  - Phrasing check: zero occurrences of `%d` followed by plural words.

- [ ] **Step 4: Run parity test and verify all pass.**
  ```bash
  ./gradlew testDevDebugUnitTest --tests '*StringResourceParityTest'
  ```
  Expected: 6/6 tests pass.

- [ ] **Step 5: Commit.**
  ```bash
  git add app/src/main/res/values*/strings.xml
  git commit -m "feat(exc-02): add podcast studio strings across all 38 locales"
  ```

---

### Task 4: Podcast Studio ViewModel & Conflict/Consent State Machine

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/podcaststudio/PodcastStudioUiState.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/podcaststudio/PodcastStudioViewModel.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/ui/page/home/read/podcaststudio/PodcastStudioViewModelTest.kt`

**Interfaces:**
- Consumes:
  - `PodcastStudioScriptService`
  - `CommuteAudioPlayer`
  - `Context.podcastStudioGeminiConsent`
  - `CommuteEpisodeStore` (injected solely to verify it is NEVER called)
- Produces:
  - `sealed interface PodcastStudioUiState`:
    - `data object Idle : PodcastStudioUiState`
    - `data object AwaitingConsent : PodcastStudioUiState`
    - `data object ConfirmConflict : PodcastStudioUiState`
    - `data object Generating : PodcastStudioUiState`
    - `data class Ready(val episode: CommuteEpisode, val source: PodcastScriptSource, val playerState: CommutePlayerState) : PodcastStudioUiState`
    - `data class Error(val message: String) : PodcastStudioUiState`
  - Methods: `initiate(article)`, `onConsent(agreed)`, `onConfirmConflict()`, `onDismissConflict()`, `togglePlayPause()`, `skipNext()`, `skipPrevious()`, `seekTo(index)`, `dismissSheet()`

- [ ] **Step 1: Write failing unit tests for ViewModel.**
  - `undecidedConsentEntersAwaitingConsentState`.
  - `decliningConsentGeneratesWithUseGeminiFalseAndPersistsChoice`.
  - `agreeingConsentGeneratesWithUseGeminiTrueAndPersistsChoice`.
  - `whenAudioPlayerAlreadyPlayingPromptsConflictBeforeReplacing`.
  - `confirmingConflictReplacesEpisodeAndStartsPlayback`.
  - `assertEpisodeStoreSaveIsNeverInvoked`: Mock `CommuteEpisodeStore`, verify 0 calls to `save()`.
  - `dismissingSheetPausesAudioWithoutDestroyingPlayer`.

- [ ] **Step 2: Run test and verify it fails.**
  ```bash
  ./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.reader.ui.page.home.read.podcaststudio.PodcastStudioViewModelTest'
  ```
  Expected: FAIL with compilation error.

- [ ] **Step 3: Implement PodcastStudioUiState and PodcastStudioViewModel.**
  - Inject `@ApplicationContext context: Context`, `scriptService`, `audioPlayer`, `ioDispatcher`.
  - Implement full state machine with coroutines.
  - Collect `audioPlayer.playerState` when in `Ready` state and update UI.
  - Ensure zero calls to `CommuteEpisodeStore`.

- [ ] **Step 4: Run test and verify it passes.**
  ```bash
  ./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.reader.ui.page.home.read.podcaststudio.PodcastStudioViewModelTest'
  ```
  Expected: BUILD SUCCESSFUL, 0 failures.

- [ ] **Step 5: Mutation check.**
  - Add a fake call to `audioPlayer.pause()` in place of `playEpisode()`, verify tests fail, restore.

- [ ] **Step 6: Commit.**
  ```bash
  git add app/src/main/java/com/mckimquyen/reader/ui/page/home/read/podcaststudio/PodcastStudioUiState.kt app/src/main/java/com/mckimquyen/reader/ui/page/home/read/podcaststudio/PodcastStudioViewModel.kt app/src/test/java/com/mckimquyen/reader/ui/page/home/read/podcaststudio/PodcastStudioViewModelTest.kt
  git commit -m "feat(exc-02): add podcast studio view model and state machine"
  ```

---

### Task 5: Canvas Waveform Visualizer Component

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/podcaststudio/PodcastStudioWaveform.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/ui/page/home/read/podcaststudio/PodcastStudioWaveformTest.kt`

**Interfaces:**
- Produces:
  - `@Composable fun PodcastStudioWaveform(isPlaying: Boolean, speaker: CommuteSpeaker, modifier: Modifier)`
  - Constants: `WAVEFORM_TAG = "podcast_studio_waveform"`, `BAR_COUNT = 24`

- [ ] **Step 1: Write failing Compose UI test for Waveform.**
  - Test that `PodcastStudioWaveform` renders with `WAVEFORM_TAG`.
  - Test that semantic content description reflects playing vs paused state.
  - Test that speaker changes tint/color appropriately without crashing.

- [ ] **Step 2: Run test and verify it fails.**
  ```bash
  ./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.reader.ui.page.home.read.podcaststudio.PodcastStudioWaveformTest'
  ```
  Expected: FAIL with compilation error.

- [ ] **Step 3: Implement PodcastStudioWaveform.**
  - Use `Canvas` with 24 rounded vertical bars.
  - When `isPlaying == true`, animate bar heights using `rememberInfiniteTransition` with sinusoidal offsets.
  - When `isPlaying == false`, freeze bar heights at a resting baseline.
  - Alex accent: `MaterialTheme.colorScheme.primary`; Sam accent: `MaterialTheme.colorScheme.tertiary`.
  - Add semantic label: "Playback visualizer, [playing/paused], speaking: [Alex/Sam]".

- [ ] **Step 4: Run test and verify it passes.**
  ```bash
  ./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.reader.ui.page.home.read.podcaststudio.PodcastStudioWaveformTest'
  ```
  Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit.**
  ```bash
  git add app/src/main/java/com/mckimquyen/reader/ui/page/home/read/podcaststudio/PodcastStudioWaveform.kt app/src/test/java/com/mckimquyen/reader/ui/page/home/read/podcaststudio/PodcastStudioWaveformTest.kt
  git commit -m "feat(exc-02): add dynamic canvas playback waveform component"
  ```

---

### Task 6: Podcast Studio Bottom Sheet Composable & Dialogs

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/podcaststudio/PodcastStudioSheet.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/ui/page/home/read/podcaststudio/PodcastStudioSheetTest.kt`

**Interfaces:**
- Produces:
  - `@Composable fun PodcastStudioSheet(state: PodcastStudioUiState, onDismiss: () -> Unit, onConsent: (Boolean) -> Unit, onConfirmConflict: () -> Unit, onDismissConflict: () -> Unit, onToggle: () -> Unit, onSkipNext: () -> Unit, onSkipPrevious: () -> Unit, onSeek: (Int) -> Unit)`
  - Tags: `PODCAST_STUDIO_SHEET_TAG`, `PODCAST_CONSENT_DIALOG_TAG`, `PODCAST_CONFLICT_DIALOG_TAG`, `PODCAST_PLAY_PAUSE_TAG`

- [ ] **Step 1: Write failing Compose UI tests for sheet and dialogs.**
  - `consentDialogDisplaysAndDispatchesChoice`.
  - `conflictDialogDisplaysAndDispatchesChoice`.
  - `readySheetDisplaysDialogueListWithActiveHighlight`.
  - `voiceModeBadgeReflectsRealDualVsSimulated`.
  - `scriptSourceBadgeReflectsGeminiVsHeuristic`.
  - `playPauseButtonTogglesCallback`.

- [ ] **Step 2: Run test and verify it fails.**
  ```bash
  ./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.reader.ui.page.home.read.podcaststudio.PodcastStudioSheetTest'
  ```
  Expected: FAIL with compilation error.

- [ ] **Step 3: Implement PodcastStudioSheet.**
  - Use `androidx.compose.ui.window.Dialog` with `DialogProperties(usePlatformDefaultWidth = false)` and `DialogEdgeToEdge()` exactly like `CommuteCastDialog` in `ui/component/commute/CommuteCastSheet.kt` (the repo does not use Material3 `ModalBottomSheet`; Material3 is 1.0.1). Do not copy that file's internals; read it for the pattern only.
  - Render `PodcastStudioWaveform` prominently above player controls.
  - Render dialogue lines with scroll-to-active auto-scroll.
  - Render badges: `commute_voice_mode_dual` / `commute_voice_mode_simulated` + `podcast_studio_badge_source_gemini` / `podcast_studio_badge_source_heuristic`.
  - Render `AlertDialog` for consent and conflict states.

- [ ] **Step 4: Run test and verify it passes.**
  ```bash
  ./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.reader.ui.page.home.read.podcaststudio.PodcastStudioSheetTest'
  ```
  Expected: BUILD SUCCESSFUL, 0 failures.

- [ ] **Step 5: Commit.**
  ```bash
  git add app/src/main/java/com/mckimquyen/reader/ui/page/home/read/podcaststudio/PodcastStudioSheet.kt app/src/test/java/com/mckimquyen/reader/ui/page/home/read/podcaststudio/PodcastStudioSheetTest.kt
  git commit -m "feat(exc-02): add podcast studio bottom sheet and modal dialogs"
  ```

---

### Task 7: Wire TopBar and ReadingPage Integration

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/TopBar.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingPage.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/ui/page/home/read/podcaststudio/PodcastStudioReadingIntegrationTest.kt`

- [ ] **Step 1: Write failing integration test.**
  - Verify that menu contains "Podcast Studio" entry.
  - Verify clicking menu triggers sheet opening callback.

- [ ] **Step 2: Run test and verify it fails.**
  ```bash
  ./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.reader.ui.page.home.read.podcaststudio.PodcastStudioReadingIntegrationTest'
  ```
  Expected: FAIL.

- [ ] **Step 3: Wire TopBar and ReadingPage.**
  - In `TopBar.kt`, add `onPodcastStudio: () -> Unit = {}` parameter, add `DropdownMenuItem` with `Icons.Outlined.Podcasts` icon.
  - In `ReadingPage.kt`, instantiate `PodcastStudioViewModel` via `hiltViewModel()`, wire TopBar callback to `viewModel.initiate(article)`, and conditionally compose `PodcastStudioSheet`.

- [ ] **Step 4: Run test and verify it passes.**
  ```bash
  ./gradlew testDevDebugUnitTest --tests 'com.mckimquyen.reader.ui.page.home.read.podcaststudio.*'
  ```
  Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit.**
  ```bash
  git add app/src/main/java/com/mckimquyen/reader/ui/page/home/read/TopBar.kt app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingPage.kt app/src/test/java/com/mckimquyen/reader/ui/page/home/read/podcaststudio/PodcastStudioReadingIntegrationTest.kt
  git commit -m "feat(exc-02): wire podcast studio entry into reading top bar and page"
  ```

---

### Task 8: Full Suite, Lint Baseline, APK Builds & Device Smoke on Tecno BG6

**Files:**
- Create: `app/src/androidTest/java/com/mckimquyen/reader/infrastructure/podcast/PodcastStudioDeviceTest.kt`
- Create: `doc/task/done/EXC-02_ai-podcast-studio_DONE.md`
- Delete: `doc/task/todo/EXC-02_ai-podcast-studio.md`
- Modify: `doc/feature.md`
- Modify: `doc/task/todo/05_EXCLUSIVE_KILLER_FEATURES.md`

- [ ] **Step 1: Create on-device instrumentation test.**
  - Test `PodcastStudioScriptService` heuristic fallback on Android runtime.
  - Test `CommuteVoiceSelector` resolves voice mode on real Android TTS engine.
  - Commit test file.

- [ ] **Step 2: Run full unit test suite from scratch.**
  ```bash
  ./gradlew testDevDebugUnitTest --rerun-tasks
  ```
  Expected: 358+ suites, 2,450+ tests, 0 failures, 0 errors.

- [ ] **Step 3: Run Android Lint.**
  ```bash
  ./gradlew lintDevDebug
  ```
  Expected: 0 errors, exactly 2,463 warnings (0 new EXC-02 issues).

- [ ] **Step 4: Build APKs.**
  ```bash
  ./gradlew assembleDevDebug assembleDevDebugAndroidTest
  ```
  Expected: BUILD SUCCESSFUL, exit code 0.

- [ ] **Step 5: Run instrumentation test on Tecno BG6 (`118743744X002560`).**
  - Verify device attached: `adb devices -l`.
  - Force-stop app first to prevent ad-lockup: `adb -s 118743744X002560 shell am force-stop com.mckimquyen.reader`.
  - Install test APK: `adb -s 118743744X002560 install --user 0 -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk`.
  - Run via `am instrument` with timeout:
    ```bash
    timeout 90 adb -s 118743744X002560 shell am instrument -w -r -e class com.mckimquyen.reader.infrastructure.podcast.PodcastStudioDeviceTest com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner
    ```
  - Expected: `OK (1 test)`.

- [ ] **Step 6: Backup Room and DataStore on BG6 before UI smoke.**
  - `adb -s 118743744X002560 shell am force-stop com.mckimquyen.reader`.
  - Pull `Reader`, `Reader-journal`, and `settings.preferences_pb` to `/tmp/exc02-bg6-backup/`.
  - Verify backup `PRAGMA integrity_check;`.

- [ ] **Step 7: Smoke test on Tecno BG6.**
  - Install latest debug APK: `adb -s 118743744X002560 install --user 0 -r "app/build/outputs/apk/dev/debug/RSS Cat Hub-*.apk"`.
  - Launch app: `adb -s 118743744X002560 shell am start --user 0 -n com.mckimquyen.reader/.infrastructure.android.SplashActivity`.
  - If full-screen ad appears: dump UI, verify `AdActivity`, close via labelled control.
  - Open any article -> tap menu -> tap "🎙️ Podcast Studio".
  - Verify consent dialog appears. Tap "Agree".
  - Verify bottom sheet opens: waveform animates while audio plays, dialogue line highlights, honest voice badge displays.
  - Tap pause -> verify waveform stops animating.
  - Dismiss sheet -> verify audio pauses.
  - Capture screenshots using `adb -s 118743744X002560 exec-out screencap -p`.

- [ ] **Step 8: Restore Room and DataStore on BG6.**
  - Stream backup back to device.
  - Verify `cmp` 100% bit-for-bit match.
  - Verify `PRAGMA integrity_check` is `ok`.

- [ ] **Step 9: Write DONE report and update tracking docs.**
  - Create `doc/task/done/EXC-02_ai-podcast-studio_DONE.md` with audit score, commits, test evidence, and stated limits (in-memory only, media session resume limitation, ambient audio inherited).
  - Remove `doc/task/todo/EXC-02_ai-podcast-studio.md`.
  - Move EXC-02 to Done in `doc/task/todo/05_EXCLUSIVE_KILLER_FEATURES.md`.
  - Update `doc/feature.md`.
  - Commit documentation on `dev`. Do NOT push.
