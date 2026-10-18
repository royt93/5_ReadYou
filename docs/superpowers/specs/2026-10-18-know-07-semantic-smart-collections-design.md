# [KNOW-07] Semantic Smart Collections Specification & Architecture Design

- **Status:** Approved
- **Date:** 2026-10-18
- **Epic:** 06. NEXT_GEN_AI_KNOWLEDGE — AI Thế Hệ Mới & Quản Trị Tri Thức
- **Authors:** Senior Developer & System Architect

---

## 1. Overview & Problem Statement

`SemanticSearchEngine` (KNOW-02) provides 64-dimensional semantic search and concept clustering for articles, augmented by persistent vector caching via `ArticleEmbeddingRecord` (KNOW-05). However, search queries are ephemeral: results disappear when clearing the search bar, requiring users to repeatedly type the same query to monitor evolving topics.

**KNOW-07 (Semantic Smart Collections)** enables users to save any natural-language semantic query into a persistent "Smart Collection". After each RSS sync cycle, newly received or updated articles are matched against all saved Smart Collections using `SemanticSearchEngine`, and matching articles are linked automatically.

---

## 2. Core Decisions & Constraints

1. **Incremental Matching Only:** Newly created Smart Collections do not backfill historical articles. Only newly inserted or modified articles during subsequent sync cycles are matched.
2. **Fixed Threshold:** Uses `SemanticSearchEngine.DEFAULT_MIN_SCORE_THRESHOLD = 0.22f`.
3. **Dedicated Screen & Navigation:**
   - Dedicated route `RouteName.SMART_COLLECTIONS` ("smart_collections") for collection management.
   - Dedicated route `RouteName.SMART_COLLECTION_DETAIL` ("smart_collections/{collectionId}") for viewing matched articles.
   - Quick save entry point on `FlowPage` during active semantic search via a "Lưu thành bộ sưu tập" chip/button.
   - Flow actions menu entry point to access Smart Collections.
4. **Isolated Synchronization:** Matching occurs inside `SyncWorker` immediately after successful RSS ingestion. Any matching failures are caught with `runCatching` and logged via `Log.w("RLog", ...)` to ensure core RSS sync is never broken or aborted.
5. **No Magic Numbers:** All scores, limits, and keys use strictly typed constants (`const val`).
6. **Thread Safety & Lifecycle:** Room and vector operations run strictly on `Dispatchers.IO` / `Dispatchers.Default`. Compose states avoid holding Activity or ViewModel contexts.

---

## 3. Data Architecture & Database Schema (Room v16)

Nâng cấp schema Room từ **v15** lên **v16** thông qua `MIGRATION_15_16`.

### 3.1 Table `smart_collection`

```sql
CREATE TABLE IF NOT EXISTS `smart_collection` (
    `id` TEXT NOT NULL,
    `accountId` INTEGER NOT NULL,
    `name` TEXT NOT NULL,
    `query` TEXT NOT NULL,
    `minScoreThreshold` REAL NOT NULL,
    `createdAt` INTEGER NOT NULL,
    PRIMARY KEY(`id`),
    FOREIGN KEY(`accountId`) REFERENCES `account`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS `index_smart_collection_accountId` ON `smart_collection` (`accountId`);
```

Kotlin Model:
```kotlin
@Keep
@Entity(
    tableName = "smart_collection",
    foreignKeys = [
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["accountId"]),
    ]
)
data class SmartCollection(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val accountId: Int,
    val name: String,
    val query: String,
    val minScoreThreshold: Float = SemanticSearchEngine.DEFAULT_MIN_SCORE_THRESHOLD,
    val createdAt: Long = System.currentTimeMillis(),
)
```

### 3.2 Table `smart_collection_article` (Cross-reference / Junction Table)

```sql
CREATE TABLE IF NOT EXISTS `smart_collection_article` (
    `collectionId` TEXT NOT NULL,
    `articleId` TEXT NOT NULL,
    `score` REAL NOT NULL,
    `matchedAt` INTEGER NOT NULL,
    PRIMARY KEY(`collectionId`, `articleId`),
    FOREIGN KEY(`collectionId`) REFERENCES `smart_collection`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
    FOREIGN KEY(`articleId`) REFERENCES `article`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS `index_smart_collection_article_collectionId` ON `smart_collection_article` (`collectionId`);
CREATE INDEX IF NOT EXISTS `index_smart_collection_article_articleId` ON `smart_collection_article` (`articleId`);
```

Kotlin Model:
```kotlin
@Keep
@Entity(
    tableName = "smart_collection_article",
    primaryKeys = ["collectionId", "articleId"],
    foreignKeys = [
        ForeignKey(
            entity = SmartCollection::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Article::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["collectionId"]),
        Index(value = ["articleId"]),
    ]
)
data class SmartCollectionArticle(
    val collectionId: String,
    val articleId: String,
    val score: Float,
    val matchedAt: Long = System.currentTimeMillis(),
)
```

### 3.3 Composite / View Model for Collection with Article Count

```kotlin
data class SmartCollectionWithCount(
    @Embedded
    val collection: SmartCollection,
    val articleCount: Int,
)
```

### 3.4 DAO Interface `SmartCollectionDao`

```kotlin
@Dao
interface SmartCollectionDao {
    @Query("""
        SELECT c.*, COUNT(ca.articleId) AS articleCount
        FROM smart_collection c
        LEFT JOIN smart_collection_article ca ON c.id = ca.collectionId
        WHERE c.accountId = :accountId
        GROUP BY c.id
        ORDER BY c.createdAt DESC
    """)
    fun observeCollectionsWithCount(accountId: Int): Flow<List<SmartCollectionWithCount>>

    @Query("SELECT * FROM smart_collection WHERE accountId = :accountId")
    suspend fun getCollectionsByAccount(accountId: Int): List<SmartCollection>

    @Query("SELECT * FROM smart_collection WHERE id = :id LIMIT 1")
    suspend fun getCollectionById(id: String): SmartCollection?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCollection(collection: SmartCollection)

    @Query("DELETE FROM smart_collection WHERE id = :id")
    suspend fun deleteCollection(id: String)

    @Query("UPDATE smart_collection SET name = :name, query = :query WHERE id = :id")
    suspend fun updateCollection(id: String, name: String, query: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMatches(matches: List<SmartCollectionArticle>): List<Long>

    @Query("""
        SELECT a.*, f.id AS feed_id, f.name AS feed_name, f.url AS feed_url, f.groupId AS feed_groupId,
               f.accountId AS feed_accountId, f.icon AS feed_icon, f.isNotification AS feed_isNotification,
               f.isFullContent AS feed_isFullContent, f.sourceType AS feed_sourceType, f.sourceUrl AS feed_sourceUrl,
               ca.score AS matchScore, ca.matchedAt AS matchTime
        FROM smart_collection_article ca
        INNER JOIN article a ON ca.articleId = a.id
        INNER JOIN feed f ON a.feedId = f.id
        WHERE ca.collectionId = :collectionId
        ORDER BY ca.matchedAt DESC, a.date DESC
    """)
    fun observeArticlesInCollection(collectionId: String): Flow<List<ArticleWithFeed>>

    @Query("DELETE FROM smart_collection_article WHERE collectionId = :collectionId AND articleId = :articleId")
    suspend fun removeArticleFromCollection(collectionId: String, articleId: String)
}
```

---

## 4. Ingestion & Matching Engine

### 4.1 Service Component: `SmartCollectionMatcher`

File: `app/src/main/java/com/mckimquyen/reader/domain/sv/SmartCollectionMatcher.kt`
- Injected into `SyncWorker`.
- Function `matchNewArticles(accountId: Int, candidateArticles: List<ArticleWithFeed>)`:
  1. If `candidateArticles.isEmpty()`, exit immediately.
  2. Load all `smart_collection` records for `accountId` from `SmartCollectionDao`.
  3. If collections are empty, exit immediately.
  4. Ensure candidate article embeddings are cached via `semanticSearchEngine.cacheEmbeddings(candidateArticles)`.
  5. For each collection:
     - Run `semanticSearchEngine.rank(collection.query, candidateArticles, collection.minScoreThreshold)`.
     - Collect matching articles into `SmartCollectionArticle(collectionId, articleId, score, matchedAt)`.
  6. Bulk insert matches into `smart_collection_article` via `SmartCollectionDao.insertMatches()`.

### 4.2 Integration into `SyncWorker`

Inside `SyncWorker.doWork()`:
```kotlin
if (syncResult.isSuccessfulSync()) {
    // ...
    // Run incremental Smart Collection matching for newly inserted articles
    runCatching {
        smartCollectionMatcher.matchNewArticles(accountId, newlyInsertedArticles)
    }.onFailure {
        Log.w("RLog", "SmartCollection matching failed", it)
    }
}
```

---

## 5. UI & Presentation Layer

### 5.1 Route & Destinations (`RouteName.kt` & `HomeEntry.kt`)
- `RouteName.SMART_COLLECTIONS = "smart_collections"`
- `RouteName.SMART_COLLECTION_DETAIL = "smart_collections/{collectionId}"`

### 5.2 FlowPage Quick-Save Entry Point
When `onSearch == true && homeUiState.searchContent.isNotBlank()`:
- Add a Compose chip or button: `"Lưu thành Bộ sưu tập"` (Icons.Rounded.BookmarkAdd / Add).
- Clicking opens `SaveSmartCollectionDialog`:
  - Name textfield (pre-filled with clean query or concept).
  - Query textfield (disabled or editable, defaulting to `homeUiState.searchContent`).
  - Save action -> Inserts into `SmartCollectionDao`, shows toast `"Đã lưu bộ sưu tập thông minh"`.

### 5.3 FlowActionsMenu Navigation
In `FlowActionsMenu.kt`, add an item:
- Icon: `Icons.Rounded.AutoAwesome` or `Icons.Rounded.CollectionsBookmark`.
- Label: `"Bộ sưu tập thông minh"` (localized across 6 languages).
- OnClick: Navigate to `RouteName.SMART_COLLECTIONS`.

### 5.4 Screen: `SmartCollectionsPage.kt`
- TopAppBar: Back button, Title `"Bộ sưu tập thông minh"`, Add button (+).
- Content: LazyColumn showing cards with collection name, query chip, matched article count badge, and a more-vert menu (Rename, Delete).
- Empty State: Clean graphic / text encouraging users to save topics from search.
- Clicking a collection navigates to `RouteName.SMART_COLLECTION_DETAIL`.

### 5.5 Screen: `SmartCollectionDetailPage.kt`
- TopAppBar: Back button, Title = Collection Name, Subtitle = Query.
- Content: LazyColumn of `ArticleItem` (reusing existing UI components from `FlowPage`), with match score indicator chip.
- Clicking an article navigates to `ReadingPage`.

---

## 6. Testing & Quality Assurance Plan

1. **Unit Tests (`app/src/test/`):**
   - `SmartCollectionDaoTest`: Robolectric in-memory DB testing CRUD, cascade deletion (deleting collection leaves article intact; deleting article removes junction row), and flow emission.
   - `RoomMigration15To16Test`: Validates schema migration from v15 to v16, ensuring index and foreign key constraints are created properly.
   - `SmartCollectionMatcherTest`: MockK tests validating that only articles scoring >= threshold are linked, and zero calls are made when candidate list is empty.
2. **Widget & Compose UI Tests (`app/src/androidTest/` or Robolectric Compose):**
   - `SmartCollectionsPageTest`: Verifies collection listing, card clicks, and empty-state rendering.
   - `SaveSmartCollectionDialogTest`: Verifies dialog input validation and confirm button invocation.
3. **Integration & Interruption Test:**
   - Simulated process kill during sync matching does not corrupt database state.
   - Re-running matching on duplicate items behaves idempotently (`OnConflictStrategy.IGNORE`).
4. **Performance & Memory Leak:**
   - Ensures memory footprint remains constant by relying on the 64-dimensional float vector cache from KNOW-05 without computing embeddings repeatedly.

---

## 7. Localization

All user-visible strings localized in 6 supported locales:
- `en`: English
- `vi`: Vietnamese
- `zh-rCN`: Simplified Chinese
- `ja`: Japanese
- `fr`: French
- `de`: German
