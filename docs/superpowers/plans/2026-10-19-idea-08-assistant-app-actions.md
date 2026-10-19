# [IDEA-08] Assistant / App Actions Hands-Free Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user start reading the latest unread article aloud without opening the app (launcher shortcut, Assistant App Action declaration, or `rsscathub://read-news`), mark each article read when it finishes, and move on with a "Next" button on the media notification.

**Architecture:** A transparent `ReadNewsActivity` receives the `rsscathub://read-news` link and calls a Hilt singleton `ReadNewsController`, which loads a bounded queue of unread articles, plays one through the existing `TtsManager`, marks it read when `TtsManager.completions` fires, and advances on `next()`. `TtsManager` gains a completion hook; `TtsForegroundService` gains a "Next" action. A static `shortcuts.xml` declares the shortcut and the `actions.intent.GET_THING` capability.

**Tech Stack:** Kotlin 2.1.0, Hilt, Room, Android `TextToSpeech`, `MediaSessionCompat`, `NotificationCompat`, JUnit4, MockK, Robolectric 4.14.1 (`ShadowTextToSpeech`).

## Global Constraints

- Use an in-app entry point (transparent activity + static shortcut), not the `MediaSession` voice-search path; do not change `CommuteMediaSessionService`.
- Entry is `ReadNewsActivity` with `android.intent.action.VIEW` on `rsscathub://read-news`, plus a static shortcut bound to the built-in intent `actions.intent.GET_THING`.
- "Read on" is a **Next** action on the existing TTS media notification, not speech recognition.
- The controller never auto-advances: after an article ends it is marked read and the controller waits for Next.
- Unread means `isUnread = 1` for the current account (`ArticleDao.queryLatestUnread`).
- A queue of at most `READ_NEWS_QUEUE_LIMIT = 10` article ids is fetched once per start.
- No new permissions (`POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` already exist).
- Marking read uses `ArticleDao.markAsReadByArticleId` only; IDEA-01's `readAt` is not written.
- The deep link is exported: the controller must not read anything from the intent.
- Article text comes from Room only (`fullContent ?: rawDescription`), never from the network, so the hands-free path works offline.
- Existing behaviour is preserved: `ttsState` still returns to `IDLE` on every `onDone`, and `play()` still starts the foreground service.
- No magic numbers; sizes, limits and ids are named constants.
- Localize every new string across all 38 maintained locale files (`values` + 37 `values-*`); keep `StringResourceParityTest` green; Vietnamese keeps full diacritics.
- Live Android work only on the Tecno: `adb -s 115333744A005844`; never run Gradle `connected*` tasks. Check the foreground window with `dumpsys` before every action, take a screenshot after every step, never chain several taps in one command, and stop immediately if a full-screen ad (`com.google.android.gms.ads.AdActivity`) covers the UI, then wait for the user's "done". Banner ads that do not cover the UI are skipped. Do not tap the VIP trial-code button or change app settings that were not requested.
- Keep `.idea/**` untouched. No ad, VIP or IAP behaviour is changed (deferred by the owner).
- Do **not** claim that Google Assistant invokes the App Action: Assistant loads App Actions only for Play-installed builds linked in the Play Console (or through the App Actions Test Tool in Android Studio); the capability is declared and its XML validated, nothing more.

## Known limits (record in the completion report)

- The natural-language command and the `GET_THING` App Action cannot be exercised with adb; only the XML declaration is validated.
- The queue lives in process memory; if the process is killed, a later Next does nothing until the user starts again.
- Long articles are spoken in chunks; completion fires only after the last chunk.
- A notification "Next" shown while no queue exists does nothing.

---

### Task 1: `TtsManager` completion hook (TDD, Robolectric)

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/audio/TtsManager.kt`
- Modify: `app/src/test/java/com/mckimquyen/reader/infrastructure/audio/TtsManagerTest.kt`

**Interfaces:**
- Produces on `TtsManager`:
  ```kotlin
  private val _completions = MutableSharedFlow<Unit>(extraBufferCapacity = COMPLETION_BUFFER)
  val completions: SharedFlow<Unit> = _completions.asSharedFlow()   // emits once when the LAST utterance of a play() call finishes
  companion object { const val COMPLETION_BUFFER = 1 }
  ```
  Behaviour: `play()` records the id of the last utterance it queued (`TTS_ID` for single text, `TTS_ID_<lastIndex>` for chunked text) in a private `lastUtteranceId`; `onDone(id)` emits only when `id == lastUtteranceId`; `pause()`, `stop()` and `shutdown()` clear `lastUtteranceId` so a late `onDone` after them emits nothing. `ttsState` behaviour is unchanged.

- [ ] **Step 1: Write tests (RED)** — extend `TtsManagerTest` (already `@RunWith(RobolectricTestRunner::class)`, `@Config(manifest = Config.NONE)`; add `application = Application::class` and `sdk = [34]`). Robolectric creates the `TextToSpeech` but never calls `onInit`, so call `manager.onInit(TextToSpeech.SUCCESS)` in the new tests (verified: it sets `isInitialized` and installs the utterance listener) and drive the listener with `shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance()).utteranceProgressListener`. Use `runTest` with `Dispatchers.Unconfined` and collect `completions` into a list with `backgroundScope.launch(UnconfinedTestDispatcher(testScheduler))`.
  - `completions_emitsOnceForSingleChunkText`: `play("short text", ...)`, call `listener.onDone("TTS_ID")` → exactly one emission.
  - `completions_emitsOnlyForLastChunkOfLongText`: `play("x".repeat(TextToSpeech.getMaxSpeechInputLength() * 2), ...)` yields at least 3 chunks (`chunked(max - 100)`); call `onDone("TTS_ID_0")` → no emission; call `onDone` for the last index (`"TTS_ID_${lastIndex}"`, computed as `text.chunked(max - 100).lastIndex`) → one emission.
  - `completions_doesNotEmitAfterPause`: `play(...)`, `pause()`, then `onDone("TTS_ID")` → no emission.
  - `completions_doesNotEmitAfterStop`: same with `stop()`.
  - `completions_doesNotEmitForUnknownUtteranceId`: `play(...)`, `onDone("OTHER")` → no emission.
  - `ttsState_stillReturnsToIdleOnDone`: `play(...)`, `onDone("TTS_ID")` → `ttsState.value == TtsState.IDLE` (regression guard).

- [ ] **Step 2: Run, confirm RED** — `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.audio.TtsManagerTest"` → compilation failure (`completions`).

- [ ] **Step 3: Implement**
  - Imports `MutableSharedFlow`, `SharedFlow`, `asSharedFlow`.
  - Add the `_completions`/`completions` fields and `COMPLETION_BUFFER` constant.
  - Add `private var lastUtteranceId: String? = null`.
  - In `play()` single-chunk branch set `lastUtteranceId = "TTS_ID"` before `speak`; in the chunked branch set `lastUtteranceId = "TTS_ID_${chunks.lastIndex}"`.
  - In `onDone(utteranceId)`: keep `_ttsState.value = TtsState.IDLE`, then `if (utteranceId != null && utteranceId == lastUtteranceId) { lastUtteranceId = null; _completions.tryEmit(Unit) }`.
  - In `pause()`, `stop()` and `shutdown()` add `lastUtteranceId = null` (before returning, including the early `return` paths guarded by `isInitialized`: set it first).

- [ ] **Step 4: Run, confirm GREEN** (same command; the 5 existing tests plus 6 new).
- [ ] **Step 5: Commit** — `feat(tts): emit a completion event when the last utterance finishes` (+ trailer).

---

### Task 2: `ReadNewsController` (TDD)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/sv/ReadNewsController.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/domain/sv/ReadNewsOutcome.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/domain/sv/ReadNewsSpeaker.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/di/ReadNewsModule.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/domain/sv/ReadNewsControllerTest.kt`

**Interfaces:**
- Consumes: `TtsManager.completions` (Task 1), `ArticleDao.queryLatestUnread(accountId, limit)`, `ArticleDao.markAsReadByArticleId(accountId, articleId, isUnread)`, `Context.currentAccountId`.
- Produces:
  ```kotlin
  enum class ReadNewsOutcome { STARTED, NO_UNREAD, NO_READABLE_TEXT, QUEUE_FINISHED, ERROR }

  /** Cổng nói để controller không phụ thuộc trực tiếp TtsManager (dễ test). */
  interface ReadNewsSpeaker {
      val completions: Flow<Unit>
      fun play(text: String, title: String, subtitle: String)
      fun speakNotice(text: String)
      fun stop()
  }
  // Production binding in ReadNewsModule: wraps TtsManager (play -> ttsManager.play, speakNotice -> play(text, "", ""), stop -> ttsManager.stop()).

  @Singleton
  class ReadNewsController @Inject constructor(
      @ApplicationContext private val context: Context,
      private val articleDao: ArticleDao,
      private val speaker: ReadNewsSpeaker,
      @IODispatcher private val ioDispatcher: CoroutineDispatcher,
      @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
      @ApplicationScope applicationScope: CoroutineScope,   // existing qualifier in infrastructure/di/ApplicationScope.kt
  ) {
      val hasQueue: StateFlow<Boolean>                 // true while a queue exists and has a next article
      suspend fun start(): ReadNewsOutcome
      suspend fun next(): ReadNewsOutcome
      companion object { const val READ_NEWS_QUEUE_LIMIT = 10 }
  }
  ```
  Spoken notice texts come from `context.getString(R.string.read_news_no_unread)` and `R.string.read_news_queue_finished` (strings added in Task 5; add the two English lines to `values/strings.xml` in this task so the build compiles).

- [ ] **Step 1: Write tests (RED)** — Robolectric (for `Context`/strings and `HtmlCompat`), `StandardTestDispatcher` for both dispatchers, MockK `ArticleDao`, a hand-written `FakeSpeaker` that records `played` (text, title, subtitle), `notices` and `stopped`, and exposes `completions` as a `MutableSharedFlow<Unit>`. Create `ArticleWithFeed`-free `Article` fixtures (`id`, `title`, `rawDescription`, `fullContent`, `feedId`, `accountId = 1`, `isUnread = true`); feed names come from `articleDao.queryById(id)?.feed?.name` — mock `queryById` to return `ArticleWithFeed(article, Feed(name = "BBC"...))`. Tests:
  - `start_withNoUnread_returnsNoUnreadAndSpeaksNotice`: `queryLatestUnread` returns empty → `NO_UNREAD`, `speaker.notices` contains "No new articles", nothing played.
  - `start_playsFirstArticleWithTitleAndFeedName`: two articles → `STARTED`, `played.first()` has the plain text of `fullContent` (HTML tags stripped, e.g. `"<p>Hello <b>world</b></p>"` → contains "Hello world"), title and `"BBC"`.
  - `start_fallsBackToRawDescriptionWhenNoFullContent`.
  - `start_skipsBlankArticleAndPlaysNext`: first article's text is blank after stripping, second is readable → plays the second.
  - `start_allBlank_returnsNoReadableText`.
  - `start_requestsAtMostQueueLimitForCurrentAccount`: `coVerify { articleDao.queryLatestUnread(1, ReadNewsController.READ_NEWS_QUEUE_LIMIT) }` (account from `context.currentAccountId`, default 1).
  - `completion_marksCurrentArticleReadAndKeepsQueue`: build the controller with `applicationScope = backgroundScope`, after `start()` emit on `speaker.completions`, advance → `coVerify { articleDao.markAsReadByArticleId(1, "a1", false) }`; no second article is played; `hasQueue.value == true` while `a2` remains.
  - `next_marksCurrentReadIfFinishedAndPlaysNext`: after completion of `a1`, `next()` → `STARTED` and `played` has `a2`; `a1` was marked exactly once.
  - `next_withoutCompletion_doesNotMarkCurrentRead`: `next()` before completion → `a1` is NOT marked read, `a2` plays (user skipped).
  - `next_atEndOfQueue_returnsQueueFinishedSpeaksNoticeAndStops`: one-article queue → after completion `next()` → `QUEUE_FINISHED`, notice "No more new articles" spoken, `hasQueue.value == false`.
  - `next_withoutQueue_returnsQueueFinished`.
  - `start_whileQueueActive_replacesIt`: start twice; second `start()` re-queries and plays the new first article; the old position is discarded.
  - `start_whenDaoThrows_returnsErrorAndDoesNotCrash`: `queryLatestUnread` throws `IOException` → `ERROR`; cancellation is rethrown (`CancellationException` is not swallowed).
  - `completion_markFailure_isLoggedNotThrown`: `markAsReadByArticleId` throws → controller keeps working (next `next()` still plays).

- [ ] **Step 2: Run, confirm RED** — `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.sv.ReadNewsControllerTest"`.

- [ ] **Step 3: Implement**
  - `ReadNewsOutcome` and `ReadNewsSpeaker` as in Interfaces.
  - `ReadNewsModule` (`@Module @InstallIn(SingletonComponent::class)`): `@Provides @Singleton fun provideReadNewsSpeaker(ttsManager: TtsManager): ReadNewsSpeaker = object : ReadNewsSpeaker { override val completions = ttsManager.completions; override fun play(text, title, subtitle) = ttsManager.play(text, title, subtitle); override fun speakNotice(text: String) = ttsManager.play(text, "", ""); override fun stop() = ttsManager.stop() }`.
  - `ReadNewsController`:
    - State (all guarded by a `Mutex` named `queueLock`): `queue: List<String>`, `position: Int`, `currentFinished: Boolean`, `private val _hasQueue = MutableStateFlow(false)`.
    - `start()`: `withContext(ioDispatcher)` load `articleDao.queryLatestUnread(accountId, READ_NEWS_QUEUE_LIMIT)`; empty → speak `R.string.read_news_no_unread`, clear queue, return `NO_UNREAD`; else store ids, `position = -1`, call the shared `playFrom(0)` helper; catch `Exception` (rethrow `CancellationException`) → log, return `ERROR`.
    - `playFrom(index)` (private): loops from `index` over `queue`: for each id `articleDao.queryById(id)` → text = `plainText(article.fullContent ?: article.rawDescription)` computed on `defaultDispatcher`; blank → continue; else set `position`, `currentFinished = false`, `speaker.play(text, title, feedName)`, `_hasQueue.value = position < queue.lastIndex`, return `STARTED`; if the loop ends without readable text return `NO_READABLE_TEXT` (first call) or `QUEUE_FINISHED` (called from `next()`).
    - `next()`: if there is no queue return `QUEUE_FINISHED`; if `currentFinished` is true mark the current article read (already marked on completion; skip a second call); `playFrom(position + 1)`; when it returns `QUEUE_FINISHED` or runs out, speak `R.string.read_news_queue_finished`, `speaker.stop()` is NOT called (the notice replaces the speech), set `_hasQueue.value = false` and clear the queue.
    - `onCompletionObserved()` (private): if a queue exists and the current article has not been marked: `markAsReadByArticleId(accountId, id, false)` on `ioDispatcher` inside `try/catch` (log failures), set `currentFinished = true`.
    - `init { applicationScope.launch { speaker.completions.collect { onCompletionObserved() } } }`: the controller owns exactly one collector in the injected `@ApplicationScope` (the same qualifier `RApp`, `ZenDailyEditionManager` and `WatchdogManager` already use). `private fun onCompletionObserved()` is exercised in tests by passing `backgroundScope` as `applicationScope` and emitting on the fake `completions` flow.
    - `plainText(html)`: `HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().replace(WHITESPACE_RUN, " ").trim()`.

- [ ] **Step 4: Run, confirm GREEN.**
- [ ] **Step 5: Commit** — `feat(news): add ReadNewsController for hands-free unread reading` (+ trailer).

---

### Task 3: `ReadNewsActivity`, manifest and shortcut (TDD)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/readnews/ReadNewsActivity.kt`
- Create: `app/src/main/res/xml/shortcuts.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/test/java/com/mckimquyen/reader/ui/page/readnews/ReadNewsActivityTest.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/ui/page/readnews/ReadNewsManifestTest.kt`

**Interfaces:**
- Consumes: `ReadNewsController.start()`, `ReadNewsOutcome` (Task 2), `@ApplicationScope CoroutineScope` (existing).
- Produces:
  ```kotlin
  object ReadNewsLink { const val SCHEME = "rsscathub"; const val HOST = "read-news" }

  @AndroidEntryPoint
  class ReadNewsActivity : ComponentActivity() { /* handles ACTION_VIEW rsscathub://read-news, then finish() */ }
  ```
  Outcome to message mapping (`Toast`): `NO_UNREAD` → `R.string.read_news_no_unread`, `NO_READABLE_TEXT` → `R.string.read_news_error`, `ERROR` → `R.string.read_news_error`, `STARTED` and `QUEUE_FINISHED` → none. Add `read_news_error` English to `values/strings.xml` now.

- [ ] **Step 1: Write tests (RED)**
  - `ReadNewsManifestTest` (plain JVM; parse `app/src/main/AndroidManifest.xml` and `app/src/main/res/xml/shortcuts.xml` with `DocumentBuilderFactory`, path relative to the module directory `src/main/...`, same approach as `StringResourceParityTest`): the manifest declares `<activity android:name=".ui.page.readnews.ReadNewsActivity">` with `android:exported="true"`, a transparent theme, `android:excludeFromRecents="true"`, `android:noHistory="true"`, an intent-filter with action `android.intent.action.VIEW`, categories `DEFAULT` and `BROWSABLE`, and `<data android:scheme="rsscathub" android:host="read-news">`; `SplashActivity` has `<meta-data android:name="android.app.shortcuts" android:resource="@xml/shortcuts">`; no `<uses-permission>` was added: assert the set of `<uses-permission>` names equals exactly `{INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS, WAKE_LOCK, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK, com.google.android.gms.permission.AD_ID}` (the current seven, verified in the manifest).
  - `shortcuts.xml` assertions in the same class: root `<shortcuts>`; one `<shortcut android:shortcutId="read_latest_news" android:enabled="true">`; its `<intent android:action="android.intent.action.VIEW" android:data="rsscathub://read-news" android:targetPackage="com.mckimquyen.reader" android:targetClass="com.mckimquyen.reader.ui.page.readnews.ReadNewsActivity">`; a `<capability-binding android:key="actions.intent.GET_THING">` with a `<parameter-binding android:key="thing.name" android:value="@array/read_news_thing_names">`; `shortcutShortLabel` and `shortcutLongLabel` reference `@string/` resources.
  - `ReadNewsActivityTest` (plain JVM/Robolectric, no Hilt): test the pure functions `ReadNewsActivity.messageFor(outcome): Int?` and `ReadNewsActivity.isReadNewsIntent(intent): Boolean` (true only for `ACTION_VIEW` with scheme `rsscathub` and host `read-news`; false for null data, other scheme, other host, `ACTION_MAIN`). Cover: all five outcomes map as listed; the four negative intents return false.

- [ ] **Step 2: Run, confirm RED.**

- [ ] **Step 3: Implement**
  - Activity code:
    ```kotlin
    @AndroidEntryPoint
    class ReadNewsActivity : ComponentActivity() {
        @Inject lateinit var controller: ReadNewsController
        @Inject @ApplicationScope lateinit var appScope: CoroutineScope
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            if (!isReadNewsIntent(intent)) { finish(); return }
            // finish() ngay; việc đọc chạy trong @ApplicationScope để không bị huỷ theo Activity.
            appScope.launch {
                val outcome = controller.start()
                messageFor(outcome)?.let { res -> withContext(Dispatchers.Main) { Toast.makeText(applicationContext, res, Toast.LENGTH_SHORT).show() } }
            }
            finish()
        }
        companion object {
            internal fun isReadNewsIntent(intent: Intent?): Boolean = ...
            @StringRes internal fun messageFor(outcome: ReadNewsOutcome): Int? = ...
        }
    }
    ```
    The class uses `@Inject lateinit var` exactly as the other `@AndroidEntryPoint` classes in this repository do (`TtsForegroundService`), which the Hilt field-injection rule in CLAUDE.md permits; `RApp` already injects `@ApplicationScope applicationScope`, so no new scope is created.
  - `res/xml/shortcuts.xml`:
    ```xml
    <?xml version="1.0" encoding="utf-8"?>
    <shortcuts xmlns:android="http://schemas.android.com/apk/res/android">
        <capability android:name="actions.intent.GET_THING">
            <intent android:action="android.intent.action.VIEW"
                android:data="rsscathub://read-news"
                android:targetPackage="com.mckimquyen.reader"
                android:targetClass="com.mckimquyen.reader.ui.page.readnews.ReadNewsActivity">
                <parameter android:name="thing.name" android:key="thing_name" />
            </intent>
        </capability>
        <shortcut android:shortcutId="read_latest_news" android:enabled="true"
            android:icon="@mipmap/ic_launcher" android:shortcutShortLabel="@string/read_news_shortcut_short"
            android:shortcutLongLabel="@string/read_news_shortcut_long">
            <intent android:action="android.intent.action.VIEW"
                android:data="rsscathub://read-news"
                android:targetPackage="com.mckimquyen.reader"
                android:targetClass="com.mckimquyen.reader.ui.page.readnews.ReadNewsActivity" />
            <capability-binding android:key="actions.intent.GET_THING">
                <parameter-binding android:key="thing.name" android:value="@array/read_news_thing_names" />
            </capability-binding>
        </shortcut>
    </shortcuts>
    ```
    The `dev` and `prod` flavors share `applicationId com.mckimquyen.reader` (no suffix; verified in `app/build.gradle`), so `targetPackage="com.mckimquyen.reader"` is correct for both. The launcher icon is `@mipmap/ic_launcher` (exists as `mipmap-anydpi-v26/ic_launcher.xml`).
  - `values/strings.xml`: `read_news_shortcut_short` "Read news", `read_news_shortcut_long` "Read the latest unread news", `read_news_error` "Could not read the news", and `<string-array name="read_news_thing_names"><item>news</item><item>latest news</item><item>unread news</item></string-array>` (kept untranslated in values/ only — it is a matching keyword list, `translatable="false"`).
  - Manifest: add the activity block next to `ShareTargetActivity` with `android:theme="@style/Theme.Reader.Transparent"`, `exported`, `excludeFromRecents`, `noHistory`, and the VIEW/DEFAULT/BROWSABLE filter with the `rsscathub` data; add `<meta-data android:name="android.app.shortcuts" android:resource="@xml/shortcuts" />` inside `SplashActivity`.

- [ ] **Step 4: Run, confirm GREEN**, then `./gradlew assembleDevDebug -q` and verify the merged manifest with `./gradlew :app:processDevDebugManifest` that the activity and shortcut meta-data survive merging.
- [ ] **Step 5: Commit** — `feat(news): add ReadNewsActivity entry point and static shortcut` (+ trailer).

---

### Task 4: "Next" notification action (TDD)

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/audio/TtsForegroundService.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/infrastructure/audio/TtsForegroundServiceNextTest.kt`

**Interfaces:**
- Consumes: `ReadNewsController.hasQueue`, `ReadNewsController.next()` (Task 2).
- Produces: `TtsForegroundService.ACTION_NEXT = "com.mckimquyen.reader.tts.ACTION_NEXT"`; the pure object `TtsNotificationActions` (`actionNames(isPlaying, hasQueue)`, `compactIndices(hasQueue)`) that decides which notification actions exist; a companion `fun next(context: Context)` that builds the `ACTION_NEXT` intent. The service adds the Next action in `buildNotification` only when `controller.hasQueue.value` is true, handles `ACTION_NEXT` in `onStartCommand` by launching `controller.next()` on a service-owned `CoroutineScope(SupervisorJob() + Dispatchers.Default)` that is cancelled in `onDestroy()`, and overrides `onSkipToNext()` in its `MediaSession` callback with the same call.

- [ ] **Step 1: Write tests (RED)** — Robolectric service test with `Robolectric.buildService(TtsForegroundService::class.java)` is brittle with Hilt; instead extract the decision into a tiny pure object `TtsNotificationActions` in the same file: `fun shouldShowNext(hasQueue: Boolean): Boolean = hasQueue` and `fun actionNames(isPlaying: Boolean, hasQueue: Boolean): List<String>` returning the ordered action ids `["toggle", "next"?, "stop"]` (`next` only when `hasQueue`). Tests: `actionNames(true, false) == ["toggle", "stop"]`; `actionNames(true, true) == ["toggle", "next", "stop"]`; `actionNames(false, true) == ["toggle", "next", "stop"]`; compact-view indices `compactIndices(hasQueue) == listOf(0, 1)` without queue and `listOf(0, 1, 2)` with queue. Also assert `ACTION_NEXT` is a distinct string from the three existing actions.

- [ ] **Step 2: Run, confirm RED.**

- [ ] **Step 3: Implement**
  - Add `TtsNotificationActions` and `ACTION_NEXT`; inject `@Inject lateinit var controller: ReadNewsController` (the service is already `@AndroidEntryPoint`).
  - `buildNotification(title, subtitle, isPlaying)`: build the toggle and stop actions as now; when `controller.hasQueue.value` insert a Next action (`android.R.drawable.ic_media_next`, label `getString(R.string.read_news_next)`, `PendingIntent.getService(... ACTION_NEXT ...)` with `FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT`, request code `3`) between them and widen `setShowActionsInCompactView` to `TtsNotificationActions.compactIndices(hasQueue)`.
  - `onStartCommand`: `ACTION_NEXT -> serviceScope.launch { controller.next() }`; the notification is refreshed after `next()` completes by calling `manager.notify(NOTIFICATION_ID, buildNotification(...))` so Next disappears at the end of the queue.
  - `onCreate` MediaSession callback: add `override fun onSkipToNext() { serviceScope.launch { controller.next() } }` and include `PlaybackStateCompat.ACTION_SKIP_TO_NEXT` in `updatePlaybackState` only when `controller.hasQueue.value`.
  - `onDestroy`: `serviceScope.cancel()`.
  - Add `read_news_next` English ("Next") to `values/strings.xml`.

- [ ] **Step 4: Run, confirm GREEN**, then `./gradlew compileDevDebugKotlin -q`.
- [ ] **Step 5: Commit** — `feat(news): add Next action to the TTS media notification` (+ trailer).

---

### Task 5: Localization across all 38 locales

**Files:**
- Modify: `app/src/main/res/values/strings.xml` and every `app/src/main/res/values-*/strings.xml` that has a `strings.xml` (37 files).

**Keys (English source, already added in earlier tasks; verify none is duplicated):**
- `read_news_shortcut_short` = "Read news"
- `read_news_shortcut_long` = "Read the latest unread news"
- `read_news_no_unread` = "No new articles"
- `read_news_queue_finished` = "No more new articles"
- `read_news_error` = "Could not read the news"
- `read_news_next` = "Next"
(`read_news_thing_names` is a `translatable="false"` array and is not localized.)

- [ ] **Step 1:** Confirm `values/strings.xml` has the six keys exactly once.
- [ ] **Step 2:** Write a throw-away Python script (`# -*- coding: utf-8 -*-` first, not committed; same approach as the earlier i18n scripts) with real translations for the 37 locales (`ar az bg ca cs-rCZ da de-rDE es-rES eu-rES fa fr-rFR hi-rIN hu in-rID it-rIT iw ja kn lzh ml my nb-rNO nl nn pl-rPL pt-rBR pt ro ru-rRU sl sr sv tr uk vi zh-rCN zh-rTW`), asserting the locale set equals the directories on disk and that no key already exists, escaping `'` as `\'`, inserting before `</resources>`. Vietnamese: "Đọc tin", "Đọc tin chưa đọc mới nhất", "Không có tin mới", "Không còn tin mới", "Không thể đọc tin", "Tiếp theo".
- [ ] **Step 3:** Validate every file parses and `rg -L "read_news_next" app/src/main/res/values*/strings.xml` prints nothing.
- [ ] **Step 4:** `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.pref.StringResourceParityTest" processDevDebugResources -q` → PASS.
- [ ] **Step 5: Commit** — `feat(i18n): localize hands-free news reading across all 38 maintained locales` (+ trailer).

---

### Task 6: Device verification on the Tecno, full suite and docs

**Files:**
- Create: `app/src/androidTest/java/com/mckimquyen/reader/ui/page/readnews/ReadNewsInstrumentationTest.kt`
- Create: `doc/task/done/IDEA-08_assistant-app-actions_DONE.md`
- Modify: `doc/feature.md`
- Delete: `doc/task/todo/IDEA-08_assistant-app-actions.md`

- [ ] **Step 1: Instrumentation test** — `ReadNewsInstrumentationTest` (`AndroidJUnit4`, isolated `EInkUiTestRunner`, which uses a plain `Application` and no Hilt, so the test builds `ReadNewsController` by hand): construct an in-memory `AndroidDatabase`, insert account/group/feed rows and two unread articles (HTML bodies), build `ReadNewsController(context, database.articleDao(), FakeSpeaker, Dispatchers.IO, Dispatchers.Default)`, call `start()` and assert `STARTED` and that the speaker received the first article's plain text with the feed name; emit a completion and assert the article is read in the database (`isUnread == false`) and the second is untouched; call `next()` and assert the second plays; with an empty database assert `NO_UNREAD`. A second test checks that `PackageManager.queryIntentActivities(Intent(ACTION_VIEW, Uri.parse("rsscathub://read-news")))` resolves to `ReadNewsActivity` and that the shortcut is present in `ShortcutManager.manifestShortcuts` with id `read_latest_news` (`getSystemService(ShortcutManager::class.java).manifestShortcuts`).
- [ ] **Step 2: Run on the Tecno only**
  ```bash
  ./gradlew -PtestRunner=com.mckimquyen.reader.EInkUiTestRunner assembleDevDebug assembleDevDebugAndroidTest
  adb -s 115333744A005844 install -r "app/build/outputs/apk/dev/debug/<apk>.apk"
  adb -s 115333744A005844 install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
  adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.reader.ui.page.readnews.ReadNewsInstrumentationTest com.mckimquyen.reader.test/com.mckimquyen.reader.EInkUiTestRunner
  ```
  Expected `OK (2 tests)`. Do not run `connectedDevDebugAndroidTest`.
- [ ] **Step 3: Full suite and builds** — `./gradlew testDevDebugUnitTest assembleDevDebug compileDevDebugAndroidTestKotlin`; sum the XML results (1052 before this feature; expect more, 0 failures/errors/skipped). Do not claim lint passed.
- [ ] **Step 4: Live smoke on the Tecno** — rebuild without `-PtestRunner`, install, then check the foreground window before every action and take a screenshot after each:
  1. `adb -s 115333744A005844 shell dumpsys shortcut com.mckimquyen.reader | grep -A4 read_latest_news` shows the static shortcut.
  2. Open the app once to confirm unread articles exist (86 are on the device); note the first unread title.
  3. Send the deep link without opening the app UI: `adb -s 115333744A005844 shell am start -a android.intent.action.VIEW -d rsscathub://read-news`; confirm the app UI does not appear, the media notification "…" shows the article title and feed, and speech is audible (check `dumpsys media_session` / `dumpsys audio` for an active TTS stream and screenshot the notification shade).
  4. Tap "Next" in the notification; the notification title changes to the next unread article. Query the database copy (`run-as ... cat databases/Reader`, same technique as the earlier smoke tests) to confirm the first article is marked read only if it had finished; record what was observed.
  5. Dismiss with Stop.
  6. Empty state: mark all articles read through a database backup/restore approach or temporarily use another account with no unread articles; confirm the "No new articles" toast. Restore the database afterwards from the backup taken before the test.
  7. If a full-screen ad covers the UI, stop, tell the user, wait for "done"; banners are skipped.
  Record clearly that the "Ok Google" phrase and the App Action itself were not exercised.
- [ ] **Step 5: Docs** — write `doc/task/done/IDEA-08_assistant-app-actions_DONE.md` (status, commit hashes from `git log`, implementation, measured verification numbers, the Known limits above and the explicit statement that Assistant invocation was not verified); in `doc/feature.md` add IDEA-08 under "Recently implemented" with the real test counts and that statement; `git rm doc/task/todo/IDEA-08_assistant-app-actions.md`.
- [ ] **Step 6: Commit** — `test(news): add hands-free reading instrumentation tests` and `docs: finalize IDEA-08 Assistant App Actions completion report` (+ trailer). Do not push unless the user asks.
