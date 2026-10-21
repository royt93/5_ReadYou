# [FIX-17] Article List CursorWindow Stability — Design Specification

- **Status:** Written spec awaiting owner review
- **Date:** 2026-10-21
- **Priority:** P1 (High)
- **Epic:** 01. FIX — Sửa Lỗi, Ổn Định & Ad Unit

---

## 1. Problem & Goal

On emulator `emulator-5554`, opening the 355-article Planet Money feed after importing a 34-article NPR news feed left the article list empty. `logcat` showed `SQLiteBlobTooBigException: Row too big to fit into CursorWindow` from `ArticleDao.queryRecentArticlesWithFeed`, whose SQL is `SELECT * FROM article ... LIMIT :limit`. The failing row had about 100 KB of `fullContent`; its size alone is below a CursorWindow, but collecting 15 rows reached the platform window limit. `queryLatestUnread` has the same `SELECT *` shape and failed in `CommuteWorker` on the same database. The error is caught by callers, so UI/worker presents an empty result rather than a visible crash.

FIX-17 must keep complete article text for features that need it, while avoiding loading full bodies into bulk list queries. It must preserve current ordering, account scoping, feed relations, result limits, and consumer behavior.

## 2. Accepted Design

**Lightweight bulk reads plus selective body fetches.** DAO list APIs return only fields needed for listing/scoring (`id`, `date`, `title`, `author`, `shortDescription`, `img`, `link`, `feedId`, `accountId`, read/star flags, metadata and feed relation). They do not select `rawDescription` or `fullContent`. Add a body-only DAO API keyed by account and article IDs to fetch complete `rawDescription`/`fullContent` only for the small subset that will actually be read or summarized. Batch/chunk explicit ID lists within SQLite's bind-parameter limit. Do not truncate or alter stored article text.

Use caller needs:
- Home story clustering and semantic search: current algorithms use title/short description; use light rows only.
- Reels: select recent metadata, then fetch full bodies for the bounded reel candidate IDs before summarizing/extracting video.
- `SemanticEmbeddingIndexer`: pass light rows; embedding source is title + short description.
- `SecondBrainSv`: pass light rows; graph uses title + short description.
- `queryLatestUnread` consumers: return light unread candidates. `ReadNewsController` fetches full body only for up to `READ_NEWS_QUEUE_LIMIT` chosen IDs; `CommuteWorker` fetches bodies only after `CommuteContentSelector` chooses the small set to synthesize; `DailyEditionWorker`, `DailyDigestSv`, and `OfflinePrecacheService` retain behavior and fetch bodies only if they actually consume them.

No fallback that reloads all rows with full bodies. Missing IDs in a body lookup are skipped without corrupting order. Preserve title, feed name and all body precedence rules.

## 3. Data / Interfaces

- Add lightweight projection models (for example `ArticleListItem` and `ArticleListItemWithFeed`) in `domain/model/article/`; choose exact names during implementation after checking current `ArticleWithFeed` usage. These projections must not contain `rawDescription` or `fullContent`.
- Change `ArticleDao.queryRecentArticlesWithFeed(accountId, limit)` and `queryLatestUnread(accountId, limit)` to return lightweight models (or replace them with explicitly named light-query methods and migrate every caller; do not leave unsafe `SELECT *` aliases callable by bulk consumers).
- Add `ArticleDao.queryArticleBodies(accountId, articleIds)` returning complete body records (`articleId`, `rawDescription`, `fullContent`) for only requested IDs. Apply ID chunking in a small repository/DAO helper if caller sets can exceed a safe bind count; preserve input order at the caller when joining results.
- Keep Room schema version unchanged: no persisted schema change is needed.

## 4. Error Handling and Compatibility

- Account scope must be included in every body lookup; IDs from another account return no body.
- Empty ID list returns empty without SQL `IN ()`.
- Large ID collections are chunked below a named SQLite bind-parameter limit; no magic count.
- Every old consumer retains complete content wherever it previously used content; no excerpt cap is introduced.
- UI list paging queries that already select explicit metadata columns are not broadened or rewritten unless the failing path proves they participate.
- The observed database and backup files on test devices are test data only; no hand-edited SQLite cleanup is part of the fix.

## 5. Testing and Verification

### Room / DAO regression
- Robolectric in-memory Room v18 test inserts enough articles to exceed CursorWindow when bodies are materialized (include at least one body >100 KB and multiple large bodies), then calls both lightweight bulk queries. Assert they complete and return all expected IDs/account scope/order without returning body fields.
- Call `queryArticleBodies` for a subset and assert exact `rawDescription`/`fullContent`, order reconstruction, account isolation, unknown IDs ignored, empty IDs safe, and ID chunking over the named maximum.
- Keep a regression assertion on SQL projection/query behavior so later changes cannot reintroduce full body columns into bulk list queries.

### Consumer behavior
- Unit tests for mapping light article + body record back into existing domain objects or the minimal consumer model.
- `ReadNewsController`: selects at most 10 unread articles; fetches bodies only for candidates it will speak; blank text remains skipped; title/feed/body selection unchanged.
- `CommuteWorker`/content selection boundary: query only bounded metadata candidate pool, fetch full bodies only for selected articles; synthesis inputs retain full text.
- `ReelsViewModel`: body-backed summary and video extraction still receive full body for selected 50-item page.
- Home clustering, semantic search/indexing and Second Brain remain correct using metadata-only fields.

### Build / live verification
- `./gradlew testDevDebugUnitTest assembleDevDebug compileDevDebugAndroidTestKotlin` passes.
- On Tecno `115333744A005844` or a clean emulator database: seed/import public feed with large article bodies, open article feed and Home, run CommuteCast/Reels and TTS paths; verify lists are not silently empty and no `SQLiteBlobTooBigException` is emitted for the two repaired bulk queries.
- Do not use the old emulator database with 100 KB NPR `fullContent` unless specifically reproducing the regression; preserve its backup.
- Do not claim unrelated CommuteCast `CursorWindow` errors are fixed unless the corresponding query is covered.

## 6. Not In Scope

- Truncating, compressing, deleting or reformatting stored article bodies.
- Changing article sync/provider parsing, feed limits, Room schema or CursorWindow platform behavior.
- General database cleanup, broad performance redesign, pagination UX changes, or fixing unrelated `SELECT *` queries not in the verified call graph.
- Podcast playback, ads, VIP or IAP behavior.
