# [IDEA-08] Assistant / App Actions Hands-Free — Completion Report

- **Status:** Implemented and verified on local `dev`; not pushed. **The Assistant voice command and the `GET_THING` App Action were NOT exercised** (see Limits).
- **Completed:** 2026-10-19
- **Audit:** 8.5/10. Entry point, controller, completion hook, Next action, manifest/shortcut declaration and localization are covered by unit, XML and device tests, and a real cold-start smoke test on the Tecno read two articles aloud. Not covered: the actual Assistant invocation, the empty-queue path on a real device, and golden/performance profiling.
- **Commits:**
  - `3e04081a` — design spec; `75c95f91` — implementation plan
  - `94161ecf` — `TtsManager` completion event
  - `63552f7e` — `ReadNewsController`
  - `15f43245` — `ReadNewsActivity`, manifest entry and static shortcut
  - `88e3e494` — Next action on the TTS media notification, `TtsManager.speakNotice`
  - `e440921f` — localization (38 locales)
  - `64596aff` — instrumentation tests
  - `fc872d5f` — fix found on the device: speech requested before the engine is ready

## Implementation

- `ReadNewsActivity` (exported, transparent, `excludeFromRecents`, `noHistory`) accepts only `ACTION_VIEW rsscathub://read-news`, hands the work to `ReadNewsController` in `@ApplicationScope` and finishes immediately. It reads nothing from the intent. `res/xml/shortcuts.xml` declares a static shortcut `read_latest_news` and a capability for the built-in intent `actions.intent.GET_THING` pointing at the same link; `SplashActivity` references it. No permissions were added (the test asserts the exact set of seven existing permissions).
- `ReadNewsController` (`@Singleton`) loads up to `READ_NEWS_QUEUE_LIMIT = 10` unread articles of the current account through `ArticleDao.queryLatestUnread`, plays the first with readable text (`fullContent ?: rawDescription`, HTML stripped, never fetched from the network), and waits. It never auto-advances. When an article finishes it is marked read with `markAsReadByArticleId`; `next()` plays the following article; at the end it speaks "No more new articles". `hasQueue` drives the Next button. IDEA-01's `readAt` is not written.
- `TtsManager` gained `completions` (emitted once, only for the last chunk of a long text, never after pause/stop) and `speakNotice` (a short spoken message that does not overwrite the resume state). `TtsForegroundService` gained `ACTION_NEXT`, a "Next" notification action and a media-session `skipToNext`, shown only while a queue exists; the action list is a small pure object (`TtsNotificationActions`).
- 6 new strings in all 38 maintained locales.

## Verification

- `./gradlew testDevDebugUnitTest assembleDevDebug compileDevDebugAndroidTestKotlin`: **BUILD SUCCESSFUL**, 1094 unit tests, 0 failures/errors/skipped (1052 before this feature, 42 new). A first full run printed `BUILD FAILED in 16s` whose reason I could not read; an immediate identical rerun succeeded, and I have no explanation for the first failure.
- New tests: 14 for `TtsManager` (completion, notice, pending-before-init), 15 for the controller, 5 XML tests on the merged declaration (activity, intent filter, shortcut, capability, permissions), 3 pure activity tests, 5 for the notification actions. `StringResourceParityTest`: 6/6. The merged manifest keeps the activity and the shortcuts meta-data, and `aapt2 dump` shows `shortcuts.xml` compiled into the APK.
- Tecno (`115333744A005844`, `adb -s` only), isolated runner: `ReadNewsInstrumentationTest` **3/3 PASS** — real Room database through the controller (start, completion marks the first article read and leaves the second untouched, `next` plays the second), the empty state, and the deep link resolving to `ReadNewsActivity` with the shortcut registered.
- Live smoke on the Tecno, cold start (app force-stopped first):
  - `dumpsys shortcut` lists `read_latest_news` with the labels "Read news" / "Read the latest unread news"; `cmd package resolve-activity` for `rsscathub://read-news` returns `ReadNewsActivity`.
  - `adb shell am start -a android.intent.action.VIEW -d rsscathub://read-news`: the launcher stayed in the foreground (no app UI), a media session went to `PLAYING`, and the shade showed a media notification titled with the newest unread article ("Companies exploiting welfa…", BBC News) with pause and **next** buttons.
  - Tapping Next in the shade switched to the next article ("'Postcode lottery' driving ch…"); logcat shows `onStart`/`onDone` for each article. In the database both spoken articles were then read, the third was untouched, and the unread count went from 112 to 110. The database was restored afterwards (112 unread, schema 17).
- Defect found on the device and fixed (`fc872d5f`): on a cold start, `play()` ran before `TextToSpeech` finished initialising and was silently dropped (`TtsManager play aborted: not initialized`), so nothing was spoken the first time. `TtsManager` now keeps the latest request and speaks it when `onInit` succeeds; pause/stop/shutdown cancel it and an init failure drops it. Six tests reproduce and guard this. The audible speech itself was not heard by a person; evidence is the `onStart`/`onDone` events, the `PLAYING` media session and the notification.
- `lintDevDebug` was not run (known local SDK 37 metadata issue); no lint pass is claimed. `connectedDevDebugAndroidTest` was not run.

## Limits and known issues

- **Assistant was not tested.** "Ok Google, read RSS Cat Hub news" and the `GET_THING` App Action cannot be triggered with adb. Assistant loads App Actions only for builds installed from Google Play and linked in the Play Console, or through the App Actions Test Tool in Android Studio. The capability is declared in the documented format and its XML is validated, nothing more. The `thing.name` keywords (`news`, `latest news`, `unread news`) are an unverified guess.
- The empty-queue path ("No new articles" spoken and toast) and the end-of-queue notice were covered by unit and Room tests but not exercised on the device.
- Marking an article read happens only after it is fully heard; skipping with Next before it finishes leaves it unread, which is intended.
- The queue lives in process memory; if the process is killed a later Next does nothing until the user starts again. Long articles are spoken in chunks and completion fires after the last chunk.
- The foreground service needs `POST_NOTIFICATIONS` for the notification to be visible on Android 13+; on the Tecno it was granted.
- Not covered: golden screenshots, Macrobenchmark/Perfetto profiling, network conditioning, ad/IAP behaviour (deferred by the owner).
