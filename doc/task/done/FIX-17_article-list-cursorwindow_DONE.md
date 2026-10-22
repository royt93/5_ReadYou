# [FIX-17] Article List CursorWindow Stability — Completion Report

- **Status:** Implemented and verified on local `dev`; not pushed.
- **Completed:** 2026-10-21
- **Audit:** 8/10. The regression is reproduced and fixed at the DAO layer, all affected callers are covered by unit tests, and 22 instrumentation tests pass on the Tecno. Not proven: the exact reason the original device query overflowed, and system-clock dependent tests can be flaky when classes share Robolectric/JVM state (see Verification).
- **Commits:** `e8fffab1` spec, `8fbfd200` plan, `dd50557f` DAO primitives, `b7784d61` metadata consumers, `8a2aa979` Reels, `e4661212` unread consumers, `67239d45` progress notes, plus the final report commit.

## Root cause and what was reproduced

`ArticleDao.queryRecentArticlesWithFeed` and `queryLatestUnread` used `SELECT * FROM article`, so every list read pulled `rawDescription` and `fullContent` into a CursorWindow. On `emulator-5554` this left the Planet Money list empty with `Row too big to fit into CursorWindow` while the NPR feed (largest `fullContent` 100,990 chars, largest article row about 101 KB) was in the same database; `CommuteWorker` hit the same exception from `queryLatestUnread`.

A native Room characterization test (Robolectric native SQLite) showed: **one 3 MiB body makes both old queries throw `SQLiteBlobTooBigException`; twenty 140 KB bodies do not.** So a single oversized body is proven to break the old queries, but I did **not** prove why the ~101 KB rows on the emulator overflowed. The fix removes bodies from list reads regardless of that cause.

## Implementation

- `queryRecentArticleListWithFeed` and `queryUnreadArticleList` select explicit metadata columns only (`ArticleListItem`, `ArticleListItemWithFeed`). The two old `SELECT *` methods are deleted; no unsafe alias remains.
- `queryArticleBody(accountId, articleId)` loads one article's `rawDescription` and `fullContent` through 64 KiB `substr(CAST(... AS BLOB))` slices inside one transaction, reassembles the bytes and decodes UTF-8 once, so multibyte characters crossing a slice boundary survive. A row that disappears mid-read throws instead of returning partial text. Null and empty `fullContent` stay distinct; the lookup is account-scoped.
- `queryArticleListWithFeedById` (TTS needs title and feed name) and `updateFullContent(accountId, id, content)` (offline cache writes only that column, never a body-less entity) were added.
- Callers: Home clustering and semantic search, `SemanticEmbeddingIndexer`, `SecondBrainSv`, `CommuteWorker`, `CommuteCastViewModel` and `DailyEditionWorker` use metadata only. Reels reads one body at a time per card. `ReadNewsController` reads a body only for articles it tries to speak. `DailyDigestSv` reads a body only when the summary is absent and stops at `DAILY_DIGEST_MAX_ARTICLES`. `OfflinePrecacheService` reuses stored `fullContent` and updates only that column. No stored text is truncated.

## Verification

- **Red-green:** the native Room test failed with `SQLiteBlobTooBigException` against the old queries before any production change, then passed after (6 DAO tests, run on the host: oversized lists, many medium bodies,  multi-megabyte Unicode, null/empty, account isolation, narrow update).
- `./gradlew testDevDebugUnitTest assembleDevDebug compileDevDebugAndroidTestKotlin`: **BUILD SUCCESSFUL**, **1231 unit tests**, 0 failures/errors/skipped (1217 before FIX-17), `StringResourceParityTest` 6/6.
- MockK 1.13.5's ByteBuddy dynamic self-attach fails on this JBR/macOS environment. `app/build.gradle` now preloads the already-resolved ByteBuddy agent only for `testDevDebugUnitTest`; before that, the default JVM produced hundreds of cascading `NoClassDefFoundError`s before assertions ran.
- **Flaky class isolation:** one full run with the agent failed 22 assertions in `ReadNewsControllerTest` (11), `ReelsViewModelTest` (6), `ReadingStatisticsViewModelTest` (4) and `OnThisDayViewModelTest` (1). The failures suggest shared state: `currentAccountId` was 2 where OnThisDay expected 1; TTS mocks received no expected body; Reels fixtures returned empty results. All four classes passed in two isolated reruns; a clean worktree at the pre-FIX-17 commit passed 1217/1217; two later fresh full runs passed 1231/1231. Root cause remains unproven; the report does not claim these are inherently flaky.
- **Instrumentation on the Tecno (`115333744A005844`, isolated `EInkUiTestRunner`):** `ArticleListCursorWindowInstrumentationTest` 3/3 (a real `SELECT *` control throws `SQLiteBlobTooBigException` on a 3 MiB body while both new list queries return it; multi-megabyte Unicode body round-trips; `ReadNewsController` speaks a 2.5 MB body ending in its marker), `FullContentSafetyIntegrationTest` 1/1, `StoryClusteringIntegrationTest` 1/1, `CommuteContentSelectionIntegrationTest` 3/3, `ReelsAndVideoIntegrationTest` 6/6, `SemanticSearchIntegrationTest` 8/8. The first run failed one assertion because my fixture added an extra space; the test was corrected, production code was not changed.
- **Smoke on the Tecno, normal APK:** the BBC News feed opened with its list; Reels opened and showed a card; `adb shell am start -a android.intent.action.VIEW -d rsscathub://read-news` started TTS (`TtsManager onStart`, media session `PLAYING`), paused from the media card. Logcat after these steps contained 0 `SQLiteBlobTooBigException`, 0 `Row too big`, 0 `FATAL EXCEPTION`. Database before and after: version 18, `integrity_check` ok, 152 articles, identical to the backup taken first (`/Users/loitran/fix17_bak_tecno_20261021_163548`).

## Limits and known issues

- The Tecno database contains only BBC News (largest bodies are small), so the smoke run exercised the new code paths but not an oversized body in the live app; the oversized-body evidence comes from the Room and instrumentation tests.
- CommuteCast, Daily Digest, Daily Edition and offline precache were covered by unit tests and the instrumentation selection test, not exercised end to end on the device. Audible speech was not heard by a person.
- Other `SELECT *` queries that were not in this call graph were left alone.
- `lintDevDebug` was not run; no lint pass is claimed.
- During the Tecno smoke test the notification shade showed private messages; nothing in them was opened or touched, only the media card pause button was pressed.
