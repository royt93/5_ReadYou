# [FIX-17] Article List CursorWindow Stability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent `SQLiteBlobTooBigException` in bulk article reads by loading lightweight article/feed projections and fetching full bodies only for bounded, selected IDs.

**Architecture:** Add metadata-only article projection types and DAO queries for recent and unread lists. Add an account-scoped body lookup with a named bind limit and chunking; migrate each caller according to whether it needs text: Home/semantic/clustering/Second Brain stay metadata-only, while Reels, TTS, CommuteCast, Daily Edition, Digest and offline precache fetch full text only for their selected candidates. Keep stored content and user-visible text unchanged.

**Tech Stack:** Kotlin, Room/KAPT, SQLite, Coroutines, JUnit4, Robolectric, MockK, `./gradlew`.

## Global Constraints

- Do not truncate, compress, delete or rewrite stored article bodies.
- Bulk projections must omit `rawDescription` and `fullContent`.
- Every body lookup includes `accountId`; an empty ID list returns empty without `IN ()`.
- ID queries use a named maximum below SQLite's bind-variable limit and chunk larger lists; keep caller order when joining results.
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

  suspend fun ArticleDao.queryArticleBodies(accountId: Int, articleIds: List<String>): List<ArticleBody>
  ```
  Keep `ArticleListItem` to metadata fields used by current consumers. Never add `rawDescription`/`fullContent` to it. If Room `@Relation` does not work reliably for a projection in this repo's Room version, use an explicit joined projection containing only feed fields those callers use; demonstrate with a Room test.
- Body query limit: `ArticleDao.MAX_BODY_IDS_PER_QUERY = 900` (named; below common SQLite 999 bind limit, leaving one bind for account ID). Caller helper chunks IDs at this bound, de-duplicates input IDs, queries each chunk, and reassembles in original ID order. Add a helper in `domain/repository` only if needed to avoid repeating chunking; keep one implementation.

- [ ] **Step 1: Write failing Room test**
  - In-memory `AndroidDatabase` v18 with migrations `MIGRATION_14_15`, `MIGRATION_15_16`, `MIGRATION_16_17`, `MIGRATION_17_18`; account A/B, separate groups/feeds.
  - Insert 20 articles into account A, 3 into B. Use a named test body constant of 140,000 characters in both `rawDescription` and `fullContent`; insert a shorter article between them by date.
  - Call the new light `queryRecentArticlesWithFeed` with limit 20 and light `queryLatestUnread` with limit 20. Assert both complete, expected ID order, account isolation and feed name. Assert each projected article exposes expected metadata but there is no body field in projection API.
  - Call `queryArticleBodies(A, [largeA, short, missing, otherAccountId])`: assert the two A rows return exact original strings, missing/other-account IDs are absent, and caller can map them back in requested order.
  - Empty IDs return empty. A list with `MAX_BODY_IDS_PER_QUERY + 5` IDs returns expected rows across multiple chunks; include one ID containing comma/colon to prove the body lookup does not parse delimiters.
  - **Expected RED:** compilation fails because projections/queries do not exist.

- [ ] **Step 2: Run focused RED test**
  - Run: `./gradlew testDevDebugUnitTest --tests "*ArticleDaoLightweightReadTest"`
  - Expected: compile/test fails for missing projection/query API (not for the large-body exception).

- [ ] **Step 3: Implement projection and DAO**
  - Add Room-compatible projection data classes with exact metadata fields above; use `@ColumnInfo` names only where property names differ from schema.
  - Change `queryRecentArticlesWithFeed` to explicitly select only metadata columns and return `List<ArticleListItemWithFeed>`.
  - Change `queryLatestUnread` to explicitly select only metadata columns and return `List<ArticleListItem>`.
  - Add a private SQL query for body rows:
    ```sql
    SELECT id AS articleId, rawDescription, fullContent
    FROM article
    WHERE accountId = :accountId AND id IN (:articleIds)
    ```
    Guard `articleIds.isEmpty()` before executing. Implement chunking with the named 900-ID bound and preserve unique input order.
  - Ensure mapping includes `shortDescription`, `aiSummary`, `readAt`, audio fields, unread/star/read-later flags as needed by existing consumer code, but no text body.

- [ ] **Step 4: Run focused tests GREEN**
  - Run the same command; expected all projection, account, body, order, empty-ID, large-ID and large-row assertions pass with no `SQLiteBlobTooBigException`.

- [ ] **Step 5: Commit**
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
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/model/article/ArticleVideoExtractor.kt` only if its API needs no change (prefer no change)
- Modify/create: `app/src/test/java/com/mckimquyen/reader/ui/page/reels/ReelsViewModelTest.kt`

**Interfaces:**
- Consumes: light rows and `queryArticleBodies` (Task 1).
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
- Consumes: light unread rows from `queryLatestUnread`, `queryArticleBodies(accountId, ids)` and the one stable body-attachment helper established in Task 1.
- Produces: consumers keep complete text and old selection/order/queue limits, while only fetching bodies for IDs actually used.

- [ ] **Step 1: Write failing consumer tests (RED)**
  - `ReadNewsController`: receives 10 light unread candidates, fetches bodies only for attempted candidates (skip blank article as before), uses `fullContent ?: rawDescription`, keeps title/feed and marks the same articles read after completion.
  - `CommuteWorker`/content-selection boundary: metadata candidate pool remains capped at `CommuteContentSelector.MAX_CANDIDATES_QUERY_LIMIT`; after selector chooses IDs, body lookup requests only those IDs and `CommuteEpisodePreparer` receives full text.
  - `CommuteCastViewModel`: selected article text remains intact after DAO conversion.
  - `DailyEditionWorker` and `DailyDigestSv`: their selector and summary receive the same full text for consumed articles, and ignore unselected bodies.
  - `OfflinePrecacheService`: caches only the same selected unread candidates and does not fetch article bodies if it does not consume them; if it does use body in current implementation, preserve exact current precedence and assert targeted ID lookup.
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
