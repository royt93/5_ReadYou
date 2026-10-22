# [FIX-17] Article List CursorWindow Stability — Completion Report

- **Status:** Implemented and verified on local `dev`; not pushed.
- **Completed:** 2026-10-21
- **Audit:** 8/10. The regression is reproduced and fixed at the DAO layer, affected callers are covered by unit tests, and 22 instrumentation tests pass on both Tecno and Pixel 7 Pro. Not proven: the exact reason the original ~101 KB emulator article overflowed; a 3 MiB native Room fixture independently reproduces the old query failure.
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
- `./gradlew testDevDebugUnitTest assembleDevDebug compileDevDebugAndroidTestKotlin`: **BUILD SUCCESSFUL**, **1231 unit tests**, 0 failures/errors/skipped (1217 before FIX-17), `StringResourceParityTest` 6/6. Confirmed twice consecutively after configuring the test JVM.
- **MockK self-attach fix:** MockK 1.13.5 could not reliably load ByteBuddy dynamically on this JBR/macOS host (`Could not self-attach to current VM using external process`). `app/build.gradle` now resolves the existing `byte-buddy-agent` from the unit-test runtime classpath and starts only `testDevDebugUnitTest` with that agent. The configured default command then passed the full suite; no `JAVA_TOOL_OPTIONS` needed.
- **Transient full-suite assertion run:** before the JVM configuration was in place, one agent-enabled run had 22 failures in four Robolectric test classes (11 `ReadNewsControllerTest`, 6 `ReelsViewModelTest`, 4 `ReadingStatisticsViewModelTest`, 1 `OnThisDayViewModelTest`). The failures involved unexpected current account ID and empty mocked TTS/Reels candidates. All four classes passed twice in isolation, the pre-FIX-17 baseline passed 1217/1217 in a clean worktree, and two subsequent configured full runs passed 1231/1231. Root cause of that isolated run remains unproven; no tests were skipped or deleted.
- **Instrumentation on the Tecno (`115333744A005844`, isolated `EInkUiTestRunner`) and Pixel 7 Pro (`2B051FDH3006MU`, same runner):** `ArticleListCursorWindowInstrumentationTest` 3/3 (a real `SELECT *` control throws `SQLiteBlobTooBigException` on a 3 MiB body while both new list queries return it; multi-megabyte Unicode body round-trips; `ReadNewsController` speaks a 2.5 MB body ending in its marker), `FullContentSafetyIntegrationTest` 1/1, `StoryClusteringIntegrationTest` 1/1, `CommuteContentSelectionIntegrationTest` 3/3, `ReelsAndVideoIntegrationTest` 6/6, `SemanticSearchIntegrationTest` 8/8 on each target (22/22 per target). The first Tecno run failed one assertion because the test fixture expected an extra normalized space; the fixture was corrected, production code was not changed.
- **Normal APK smoke:** BBC News article lists opened on Tecno and Pixel; Reels opened on Tecno. On Tecno, `adb shell am start -a android.intent.action.VIEW -d rsscathub://read-news` started TTS (`TtsManager onStart`, media session `PLAYING`), which was paused from the media card. Tecno logcat after these steps contained 0 `SQLiteBlobTooBigException`, 0 `Row too big`, 0 `FATAL EXCEPTION`. Tecno DB before and after: v18, `integrity_check` ok, 152 articles, identical to `/Users/loitran/fix17_bak_tecno_20261021_163548`. Pixel had no `SQLiteBlobTooBigException` in the tested Home/BBC flow; its DB had been backed up before test and was not modified by smoke actions.

## Limits and known issues

- The Tecno database contains only BBC News (largest bodies are small), so the smoke run exercised the new code paths but not an oversized body in the live app; the oversized-body evidence comes from the Room and instrumentation tests.
- CommuteCast, Daily Digest, Daily Edition and offline precache were covered by unit tests and the instrumentation selection test, not exercised end to end on the device. Audible speech was not heard by a person.
- Other `SELECT *` queries that were not in this call graph were left alone.
- `lintDevDebug` was not run; no lint pass is claimed.
- During the Tecno smoke test the notification shade showed private messages; nothing in them was opened or touched, only the media card pause button was pressed.
