# [FIX-17] Article List CursorWindow Stability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent `SQLiteBlobTooBigException` in bulk article reads by loading lightweight article/feed projections and fetching full bodies only for bounded, selected IDs.

**Architecture:** Add metadata-only article projection types and DAO queries for recent and unread lists. Add an account-scoped body lookup with a named bind limit and chunking; migrate each caller according to whether it needs text: Home/semantic/clustering/Second Brain stay metadata-only, while Reels, TTS, CommuteCast, Daily Edition, Digest and offline precache fetch full text only for their selected candidates. Keep stored content and user-visible text unchanged.

**Tech Stack:** Kotlin, Room/KAPT, SQLite, Coroutines, JUnit4, Robolectric, MockK, `./gradlew`.

## Implementation refinement (approved 2026-10-21)

Native characterization disproves the assumption that many 140 KB rows alone must overflow: SQLite refills its window. Both old queries fail on a single 3 MiB body. A single-ID full-body SELECT is also unsafe, so body loading uses lossless bounded byte slices. OfflinePrecache does consume cached fullContent and must retain reuse behavior; only DailyEdition is count-only.

## Global Constraints

- Do not truncate, compress, delete or rewrite stored article bodies.
- Bulk projections must omit `rawDescription` and `fullContent`.
- Every body lookup includes `accountId`; an empty ID list returns empty without `IN ()`.
- Selected IDs are read individually; body retrieval uses 64 KiB SQL BLOB slices per column under a transaction, then reassembles UTF-8 bytes exactly. No direct full-body SELECT and no multi-body cursor.
- Preserve ordering, account scope, feed relation, limit and current text precedence (`fullContent ?: rawDescription`).
- Do not change Room schema version.
- Do not hand-edit device/emulator databases.
- Do not fix unrelated `SELECT *` queries unless evidence shows they are in this verified call graph.
- No ad/VIP/IAP changes.

---

### Task 1: Add lightweight projection types and account-scoped body DAO

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/article/ArticleListItem.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/article/ArticleListItemWithFeed.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/article/ArticleBody.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/repository/ArticleDao.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/domain/repository/ArticleDaoLightweightReadTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class ArticleListItem(
      val id: String, val date: Date, val title: String, val author: String?,
      val shortDescription: String, val img: String?, val link: String,
      val feedId: String, val accountId: Int, val isUnread: Boolean,
      val isStarred: Boolean, val isReadLater: Boolean, val updateAt: Date?,
      val aiSummary: String?, val readAt: Long?,
      val audioUrl: String?, val audioMime: String?, val audioDurationSec: Int?,
  )
  data class ArticleListItemWithFeed(@Embedded val article: ArticleListItem,
      @Relation(parentColumn = "feedId", entityColumn = "id") val feed: Feed)
  data class ArticleBody(val articleId: String, val rawDescription: String, val fullContent: String?)

  suspend fun ArticleDao.queryArticleBody(accountId: Int, articleId: String): ArticleBody?
  ```
  Keep `ArticleListItem` to metadata fields used by current consumers. Never add `rawDescription`/`fullContent` to it. If Room `@Relation` does not work reliably for a projection in this repo's Room version, use an explicit joined projection containing only feed fields those callers use; demonstrate with a Room test.
- Body query bound: `ARTICLE_BODY_CHUNK_BYTES = 64 * 1024`. SQL first returns raw/full byte lengths, then `substr(CAST(rawDescription AS BLOB), :offset, :size)` and the equivalent fullContent slice. Accumulate ByteArrays before decoding UTF-8; preserve null fullContent. One transaction covers lengths and every slice. Missing ID returns null. No bind-list helper is needed.

- [x] **Step 1: Write failing Room test**
  - In-memory `AndroidDatabase` v18 with migrations `MIGRATION_14_15`, `MIGRATION_15_16`, `MIGRATION_16_17`, `MIGRATION_17_18`; account A/B, separate groups/feeds.
  - Insert 20 articles into account A, 3 into B. Use a named test body constant of 140,000 characters in both `rawDescription` and `fullContent`; insert a shorter article between them by date.
  - Call the new light `queryRecentArticlesWithFeed` with limit 20 and light `queryLatestUnread` with limit 20. Assert both complete, expected ID order, account isolation and feed name. Assert each projected article exposes expected metadata but there is no body field in projection API.
  - Call `queryArticleBody(A, [largeA, short, missing, otherAccountId])`: assert the two A rows return exact original strings, missing/other-account IDs are absent, and caller can map them back in requested order.
  - Test multiple 64 KiB slices including an ID containing comma/colon; exact UTF-8 round trip must hold even across a multibyte character split.
  - **Expected RED:** first run existing query APIs with a 3 MiB body and native Robolectric SQLite; both throw SQLiteBlobTooBigException. The many-140-KB-rows characterization should pass. After observing RED, switch assertions to new lightweight APIs.

- [x] **Step 2: Run focused RED test**
  - Run: `./gradlew testDevDebugUnitTest --tests "*ArticleDaoLightweightReadTest"`
  - Expected: existing bulk queries fail with SQLiteBlobTooBigException; no compiler errors. Record this evidence before creating API.

- [x] **Step 3: Implement projection and DAO**
  - Add Room-compatible projection data classes with exact metadata fields above; use `@ColumnInfo` names only where property names differ from schema.
  - Add `queryRecentArticleListWithFeed` returning `List<ArticleListItemWithFeed>` with explicit columns. Keep old method until callers migrate in Tasks 2–4, then remove it.
  - Add `queryUnreadArticleList` returning `List<ArticleListItem>`. Keep old method until callers migrate in Tasks 2–4, then remove it.
  - Add account-scoped length and slice queries, returning `ArticleBodyLengths` and `ArticleBodyChunk`:
    ```sql
    SELECT length(CAST(rawDescription AS BLOB)) AS rawBytes,
           length(CAST(fullContent AS BLOB)) AS fullBytes
    FROM article WHERE accountId = :accountId AND id = :articleId
    ```
    ```sql
    SELECT substr(CAST(rawDescription AS BLOB), :offset, :size) AS raw,
           substr(CAST(fullContent AS BLOB), :offset, :size) AS full
    FROM article WHERE accountId = :accountId AND id = :articleId
    ```
    `queryArticleBody` runs under `@Transaction`, reads slices starting at SQLite offset 1, and reconstructs all bytes before UTF-8 decoding. A disappearing row must throw, not return partial content. Test Unicode split across boundaries, null/empty fullContent and missing/foreign account.
  - Ensure mapping includes `shortDescription`, `aiSummary`, `readAt`, audio fields, unread/star/read-later flags as needed by existing consumer code, but no text body.

- [x] **Step 4: Run focused tests GREEN**
  - Run the same command; expected all projection, account, body, order, empty-ID, large-ID and large-row assertions pass with no `SQLiteBlobTooBigException`.

- [x] **Step 5: Commit**
  - `git add app/src/main/java/com/mckimquyen/reader/domain/model/article/ArticleListItem.kt app/src/main/java/com/mckimquyen/reader/domain/model/article/ArticleListItemWithFeed.kt app/src/main/java/com/mckimquyen/reader/domain/model/article/ArticleBody.kt app/src/main/java/com/mckimquyen/reader/domain/repository/ArticleDao.kt app/src/test/java/com/mckimquyen/reader/domain/repository/ArticleDaoLightweightReadTest.kt`
  - Commit: `fix(db): add lightweight article list reads and selective body lookup`

---

### Task 2: Migrate metadata-only consumers

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/ai/search/SemanticEmbeddingIndexer.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/sv/SecondBrainSv.kt`
- Modify: `app/src/test/java/com/mckimquyen/reader/infrastructure/ai/search/SemanticEmbeddingIndexerTest.kt`
- Modify: `app/src/test/java/com/mckimquyen/reader/domain/sv/SecondBrainSvTest.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/ui/page/home/HomeViewModelLightQueryTest.kt` (no direct HomeViewModel unit test currently exists).

**Interfaces:**
- Consumes: `ArticleListItemWithFeed` from Task 1.
- Produces: all metadata-only consumers use the lightweight DAO result, and their algorithms consume `title` + `shortDescription` only.

- [ ] **Step 1: Add failing tests**
  - Home story clustering receives correct IDs/title/shortDescription/feed and uses the lightweight query; semantic search candidates still return the same article titles/order.
  - `SemanticEmbeddingIndexer` caches embeddings derived from each light row's title + shortDescription and preserves `CacheUpdateStats`.
  - `SecondBrainSv` builds the same graph node IDs/titles/concepts from light rows.
  - Tests should fail at compilation where return types are still `ArticleWithFeed`.
- [ ] **Step 2: Run focused tests (RED)**
  - `./gradlew testDevDebugUnitTest --tests "*HomeViewModel*" --tests "*SemanticEmbeddingIndexer*" --tests "*SecondBrain*"`
- [ ] **Step 3: Implement caller type updates**
  - Replace imports/types to use `ArticleListItemWithFeed`.
  - Keep Home fingerprint and semantic document input sourced from `title` + `shortDescription`; do not request body rows.
  - Keep Second Brain's concept/tag derivation and ordering unchanged.
- [ ] **Step 4: Run focused tests (GREEN)** and `./gradlew compileDevDebugKotlin -q`.
- [ ] **Step 5: Commit** — `fix(article): use lightweight projections for metadata consumers`.

---

### Task 3: Migrate Reels while preserving full-text behavior

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/reels/ReelsViewModel.kt`
- Modify/create: `app/src/test/java/com/mckimquyen/reader/ui/page/reels/ReelsViewModelTest.kt`

**Interfaces:**
- Consumes: light rows and `queryArticleBody` (Task 1).
- Produces: Reels still summarizes/extracts video from `fullContent ?: rawDescription` for its bounded `REEL_LIMIT = 50` candidates without a bulk full-body query.

- [ ] **Step 1: Write failing tests (RED)**
  - DAO returns 3 light rows; body lookup returns two long bodies; assert `ReelsViewModel` passes full text to summary/video extraction for those IDs, uses existing fallback for missing body, preserves article/feed/title/date ordering, and never asks bodies for IDs outside the page candidate IDs.
  - Add a >100 KB article fixture and assert Reels data still loads (Room test from Task 1 proves the DB query safety).
- [ ] **Step 2: Run `./gradlew testDevDebugUnitTest --tests "*ReelsViewModelTest"` and confirm failure**
- [ ] **Step 3: Implement**
  - Fetch 50 light candidates using `queryRecentArticlesWithFeed`.
  - Call the body helper only for those candidate IDs.
  - Join body records by article ID; construct the minimal body-bearing object needed by current `buildItem`, without changing content precedence.
- [ ] **Step 4: Run test GREEN**, then `./gradlew compileDevDebugKotlin -q`.
- [ ] **Step 5: Commit** — `fix(reels): load full article bodies only for selected candidates`.

---

### Task 4: Migrate unread/body consumers (TTS, Commute, digest, edition, precache)

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/sv/ReadNewsController.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/sv/CommuteWorker.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/component/commute/CommuteCastViewModel.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/sv/DailyEditionWorker.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/sv/DailyDigestSv.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/rss/OfflinePrecacheService.kt`
- Modify: `app/src/test/java/com/mckimquyen/reader/domain/sv/ReadNewsControllerTest.kt`
- Modify: `app/src/test/java/com/mckimquyen/reader/domain/sv/CommuteEpisodePreparerTest.kt` and `app/src/test/java/com/mckimquyen/reader/ui/component/commute/CommuteCastViewModelTest.kt`
- Modify: `app/src/test/java/com/mckimquyen/reader/domain/sv/DailyDigestSvTest.kt` and `app/src/test/java/com/mckimquyen/reader/infrastructure/rss/OfflinePrecacheServiceTest.kt`
- Create: focused `DailyEditionWorker` consumer test only if existing Zen daily-edition tests do not cover its DAO/body mapping.

**Interfaces:**
- Consumes: light unread rows from `queryLatestUnread`, `queryArticleBody(accountId, ids)` and the one stable body-attachment helper established in Task 1.
- Produces: consumers keep complete text and old selection/order/queue limits, while only fetching bodies for IDs actually used.

- [ ] **Step 1: Write failing consumer tests (RED)**
  - `ReadNewsController`: receives 10 light unread candidates, fetches bodies only for attempted candidates (skip blank article as before), uses `fullContent ?: rawDescription`, keeps title/feed and marks the same articles read after completion.
  - `CommuteWorker`/content-selection boundary: metadata candidate pool remains capped at `CommuteContentSelector.MAX_CANDIDATES_QUERY_LIMIT`; after selector chooses IDs, body lookup requests only those IDs and `CommuteEpisodePreparer` receives full text.
  - `CommuteCastViewModel`: selected article text remains intact after DAO conversion.
  - `DailyEditionWorker` counts metadata only and never fetches body. `DailyDigestSv` must inspect candidates in existing order, load content only where aiSummary does not already suffice, preserve blank-body skipping and fill up to the existing article cap.
  - `OfflinePrecacheService`: load stored fullContent safely for each selected ID; reuse nonblank cache, otherwise parse network. Add narrow `updateFullContent` DAO operation instead of updating an Article reconstructed without bodies, preventing data loss.
  - Include empty unread list, missing body row and one blank body; verify old empty/skip behavior.
- [ ] **Step 2: Run focused tests (RED)**
  - `./gradlew testDevDebugUnitTest --tests "*ReadNewsControllerTest" --tests "*Commute*Test" --tests "*DailyEdition*Test" --tests "*DailyDigest*Test" --tests "*OfflinePrecacheServiceTest"`
- [ ] **Step 3: Implement**
  - Use lightweight unread query first.
  - Identify exact IDs after each consumer's existing selection/queue logic; call the shared body helper for that subset only.
  - Reassemble in the pre-existing candidate order; map missing-body rows to the same prior blank/fallback semantics, never fabricate body text.
  - Keep each query account-scoped; do not call legacy full-body `queryById` in a loop.
- [ ] **Step 4: Run focused tests GREEN**, then `./gradlew compileDevDebugKotlin -q`.
- [ ] **Step 5: Commit** — `fix(article): fetch bodies selectively for unread consumers`.

---

### Task 5: End-to-end regression, complete validation and report

**Files:**
- Modify: all caller tests from Tasks 1–4 as needed
- Create: `app/src/androidTest/java/com/mckimquyen/reader/domain/repository/ArticleListCursorWindowInstrumentationTest.kt` only if the isolated instrumentation runner can construct Room without app/Hilt; otherwise the Robolectric Room regression is the required platform-level proof and do not add a redundant test
- Create: `doc/task/done/FIX-17_article-list-cursorwindow_DONE.md`
- Modify: `doc/task/todo/01_FIX_BUGS_AND_STABILITY.md`
- Modify: `doc/feature.md`
- Delete: `doc/task/todo/FIX-17_article-list-cursorwindow.md`

- [ ] **Step 1: Run targeted Room regression + all affected caller tests**; verify both methods return correct IDs and large full bodies are only fetched by bounded body lookup.
- [ ] **Step 2: Full validation** — `./gradlew testDevDebugUnitTest assembleDevDebug compileDevDebugAndroidTestKotlin`; capture `BUILD SUCCESSFUL`, sum fresh JUnit XML (`tests`, `failures`, `errors`, `skipped`) and report all counts. Do not claim lint passed unless run successfully.
- [ ] **Step 3: Device/emulator smoke** — first list devices and obey the session's device lock. On a clean/approved DB, open Home and Planet Money, Reels, run TTS and CommuteCast if configured; verify visible article lists and full text behavior, then capture logcat filtered for `SQLiteBlobTooBigException`. Do not delete/alter device data except via app UI; backup first if test feed changes are needed. Preserve the prior emulator DB backup `/Users/loitran/podcast_bak_emulator`.
- [ ] **Step 4: Report** — record exact reproduction database shape, test counts, emulator/device serial and observed behavior; mention any unrelated remaining `CursorWindow` query honestly. Add FIX-17 to `doc/feature.md` as implemented, change epic row to ✅ done, `git rm doc/task/todo/FIX-17_article-list-cursorwindow.md`.
- [ ] **Step 5: Commit** — `test(db): prevent CursorWindow overflow in article list reads`, then `docs: finalize FIX-17 CursorWindow stability report`. Do not push unless the user asks.
