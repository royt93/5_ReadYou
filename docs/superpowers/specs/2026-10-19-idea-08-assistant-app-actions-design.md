# [IDEA-08] Assistant / App Actions Hands-Free Design Specification

- **Status:** Approved
- **Date:** 2026-10-19
- **Priority:** P3 (Low) — 3 Story Points
- **Epic:** 04. IDEAS — Ý Tưởng Trải Nghiệm & Tăng Trưởng

---

## 1. Problem & Goal

The app already reads articles aloud through `TtsManager`, but only from inside the Reader. IDEA-08 lets the user start reading the latest unread article without opening the app: through a launcher shortcut, an Assistant App Action declared in `shortcuts.xml`, or a plain `rsscathub://read-news` link. After the article finishes it is marked read, and a "Next" button on the media notification moves to the next unread article.

## 2. Accepted Decisions

| Decision | Rationale |
|---|---|
| Use an in-app entry point (transparent activity + static shortcut), not the `MediaSession` voice-search path. | The existing `CommuteMediaSessionService` serves CommuteCast episodes, not unread articles; reusing it risks regressions. |
| Entry is `ReadNewsActivity` with `android.intent.action.VIEW` on `rsscathub://read-news`, plus a static shortcut bound to the built-in intent `actions.intent.GET_THING`. | One code path for adb, shortcut, deep link and Assistant. |
| "Read on" is a **Next** action on the existing TTS media notification, not speech recognition. | No microphone permission, works from the lock screen, headset and Bluetooth. |
| The controller never auto-advances. After an article ends it is marked read and the controller waits for Next. | Matches the brief ("if the user confirms") and avoids marking many articles read unintentionally. |
| Unread means `isUnread = 1` for the current account (`ArticleDao.queryLatestUnread`). The brief's `isRead = false` is the same condition. | Reuses the existing query and index. |
| A queue of at most `READ_NEWS_QUEUE_LIMIT = 10` article ids is fetched once per start. | Bounded work; avoids reading a backlog the user did not ask for. |
| No new permissions. `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK` already exist. | The brief requires no unneeded permissions. |
| Marking read uses `ArticleDao.markAsReadByArticleId` only. IDEA-01's `readAt` is not written. | `readAt` is recorded only when the user opens an article in the Reader. |

## 3. Entry Point

- `ReadNewsActivity` (`@AndroidEntryPoint`, `exported="true"`, theme `Theme.Reader.Transparent`, `excludeFromRecents`, `noHistory`). It handles `ACTION_VIEW` with `scheme = rsscathub`, `host = read-news`; any other intent finishes immediately.
- `onCreate` calls `ReadNewsController.start()` and finishes at once, without building UI. It shows a `Toast` only for the outcomes in section 5.
- `res/xml/shortcuts.xml` (referenced from `SplashActivity` with `android.app.shortcuts`): a static shortcut `read_latest_news` with a short and a long label, an `intent` for `ACTION_VIEW rsscathub://read-news` targeting `ReadNewsActivity`, and a `capability-binding` to `actions.intent.GET_THING` through a `capability` of the same intent with the `thing.name` parameter. All labels come from string resources.
- The deep link is exported, so the controller must not trust anything in the intent: the intent carries no parameters that are read.

## 4. Controller

`ReadNewsController` (`@Singleton`, constructor-injected `ArticleDao`, `TtsManager`, `@ApplicationContext Context` only for `currentAccountId` and strings, `@IODispatcher`, `@DefaultDispatcher`):

- `start(): ReadNewsOutcome`
  - Loads up to `READ_NEWS_QUEUE_LIMIT` unread articles of the current account.
  - Empty result: returns `NoUnread` and speaks the localized "No new articles" text.
  - Otherwise converts the first article's HTML to plain text on `@DefaultDispatcher` (`HtmlCompat`), skips articles whose text is blank, and calls `TtsManager.play(text, title, feedName)`. If every candidate is blank it returns `NoReadableText`.
- `next(): ReadNewsOutcome` marks the current article read when it was fully heard, then plays the next queued article; at the end of the queue it returns `QueueFinished`, speaks the localized "No more new articles" text and stops.
- It observes `TtsManager.completions`; when the current article completes, it marks it read and keeps the queue position so `next()` can continue. A second `start()` while a queue is active replaces it.
- State is process-memory only. The controller never stores an `Activity` or `Compose` object.

## 5. TTS Completion Hook and Notification

- `TtsManager` gains `completions: SharedFlow<Unit>` (extra buffer capacity 1) that emits once when the **last** utterance of a `play()` call finishes. Long text is split into chunks with ids `TTS_ID_<index>`; the manager records the id of the last chunk and emits only for it. Single-chunk text uses `TTS_ID`. `pause()`, `stop()` and `shutdown()` do not emit.
- Existing behaviour is preserved: `ttsState` still goes back to `IDLE` on every `onDone`, and `play()` still starts the foreground service.
- `TtsForegroundService` adds `ACTION_NEXT` and a "Next" notification action and media-session `skipToNext` callback, shown only while `ReadNewsController` has a queue. Next calls `ReadNewsController.next()`.
- Outcomes and what the user sees: `Started` (no message, audio plays), `NoUnread` (Toast and spoken "No new articles"), `NoReadableText` (Toast), `QueueFinished` (Toast and spoken "No more new articles"), `Error` (Toast; the error is logged, never thrown to the Assistant).

## 6. Localization

New strings for the shortcut short and long label, "No new articles", "No more new articles", "Could not read the news" and the "Next" action label are added to `values/strings.xml` and all 37 maintained locale directories. `StringResourceParityTest` stays green. Vietnamese keeps full diacritics.

## 7. Testing and Verification

### Unit and instrumentation
- `TtsManager`: completion is emitted once for a single-chunk text, only for the last chunk of a multi-chunk text, and not after `pause()`/`stop()`.
- `ReadNewsController` with a fake `TtsManager` and in-memory `ArticleDao`: empty queue, blank-text skip, first article played with title and feed name, completion marks read, `next()` marks read and plays the next, end of queue, account scoping, queue limit, `start()` replacing an active queue.
- Manifest/XML validation: a JVM test parses `shortcuts.xml` and the manifest to assert the shortcut id, the `rsscathub://read-news` data, the exported activity and the absence of new permissions.
- Robolectric widget-free activity test: `ReadNewsActivity` with an unrelated intent finishes without calling the controller.
- Instrumentation on the Tecno (`adb -s 115333744A005844` only, isolated `EInkUiTestRunner`): starting `ReadNewsActivity` with the deep link calls the controller and finishes.

### Real device (Tecno)
- `adb shell am start -a android.intent.action.VIEW -d rsscathub://read-news` starts real text-to-speech and posts the media notification; the foreground activity is checked before every action and a screenshot is taken after every step; stop immediately if a full-screen ad covers the UI and wait for "done".
- Notification "Next" advances to the next unread article; after finishing, the finished article is read in the database.
- With no unread articles the empty message is shown and spoken.
- `dumpsys shortcut` shows the static shortcut registered; long-pressing the app icon shows it.

### What cannot be verified here
- The natural-language command "Ok Google, read RSS Cat Hub news" and the `GET_THING` App Action cannot be exercised with adb. Assistant loads App Actions only for builds installed from Google Play with Actions linked in the Play Console, or through the App Actions Test Tool in Android Studio. This feature declares the capability in the documented format and validates the XML, but no claim is made that Assistant invokes it.
- Full unit suite, `assembleDevDebug` and `compileDevDebugAndroidTestKotlin` must pass. Lint is affected by a known unrelated SDK 37 metadata failure; do not claim lint passed unless that changes.

## 8. Not In Scope

- Speech recognition inside the app or a hands-free spoken "read on".
- Android Auto, Wear OS, widgets, notification or scheduling features.
- Reading from a chosen feed or topic (`thing.name` is declared but not used to filter).
- Changes to ad, VIP or IAP code (deferred by the owner).
