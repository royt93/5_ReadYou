# [EXC-14] Feed Zero Triage Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Xây dựng chế độ "Feed Zero Triage" vuốt thẻ bài 1-chạm kiểu Tinder (trái: bỏ qua/đánh dấu đã đọc, phải: gắn sao lưu đọc sau, lên: đọc ngay) để giải quyết dứt điểm tình trạng nợ bài chưa đọc, tích hợp bộ đếm tiến độ, chúc mừng Inbox Zero và cộng điểm Brain RPG.

**Architecture:** Sử dụng Room DAO truy vấn danh sách bài chưa đọc theo thứ tự cũ nhất trước (FIFO) theo mẻ 50 bài. `TriageViewModel` quản lý hàng đợi thẻ, cập nhật trạng thái Room DB bất đồng bộ (`Dispatchers.IO`), kích hoạt `WidgetRefreshNotifier` để widget tự làm mới, và thưởng 5 XP qua `BrainRpgRepository`. Giao diện Compose dùng `Modifier.pointerInput(detectDragGestures)` kết hợp `Animatable` góc xoay và độ mờ để vuốt mượt mà 60/120fps không cần thư viện ngoài.

**Tech Stack:** Kotlin 2.1.0, Jetpack Compose Material 3, Room v18, Hilt, Coroutines StateFlow, Robolectric 4.14.1, JUnit4.

## Global Constraints
- Không thêm dependency Gradle mới (tuân thủ YAGNI).
- Mọi I/O cơ sở dữ liệu phải chạy trên `Dispatchers.IO`, không chặn Main thread (tuân thủ DoD).
- Không truyền Activity/Context vào ViewModel hay singleton.
- Toàn bộ 13 chuỗi đa ngôn ngữ mới phải có mặt đầy đủ ở 38 file `res/values-*/strings.xml`, vượt qua `StringResourceParityTest`.
- Mỗi task kết thúc bằng commit riêng kèm attribution Co-Authored-By.

---

### Task 1: DAO Layer — Truy Vấn Unread Cũ Nhất (FIFO) & Đếm Tổng Unread

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/repository/ArticleDao.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/repository/TriageDaoTest.kt`

**Interfaces:**
- Produces:
  - `ArticleDao.queryOldestUnreadArticleListWithFeed(accountId: Int, limit: Int = 50): List<ArticleListItemWithFeed>`
  - `ArticleDao.queryTotalUnreadCount(accountId: Int): Int`

- [ ] **Step 1: Write the failing test**

Tạo `app/src/test/java/com/mckimquyen/reader/domain/repository/TriageDaoTest.kt`:

```kotlin
package com.mckimquyen.reader.domain.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class TriageDaoTest {

    private lateinit var db: AndroidDatabase
    private lateinit var articleDao: ArticleDao
    private lateinit var feedDao: FeedDao
    private lateinit var accountDao: AccountDao

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AndroidDatabase::class.java,
        ).allowMainThreadQueries().build()
        articleDao = db.articleDao()
        feedDao = db.feedDao()
        accountDao = db.accountDao()

        accountDao.insert(Account(id = 1, name = "Default", type = 0))
        feedDao.insert(Feed(id = "feed1", name = "Tech News", url = "https://example.com/rss", accountId = 1))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun queryOldestUnreadArticleListWithFeed_returnsUnreadArticlesInAscendingDateOrder() = runBlocking {
        // Insert 3 articles with different dates: oldest (1000), middle (2000), newest (3000)
        articleDao.insert(Article(id = "a1", title = "Newest", date = 3000L, accountId = 1, feedId = "feed1", isUnread = true))
        articleDao.insert(Article(id = "a2", title = "Oldest", date = 1000L, accountId = 1, feedId = "feed1", isUnread = true))
        articleDao.insert(Article(id = "a3", title = "Middle", date = 2000L, accountId = 1, feedId = "feed1", isUnread = true))
        articleDao.insert(Article(id = "a4", title = "Read Article", date = 500L, accountId = 1, feedId = "feed1", isUnread = false))

        val count = articleDao.queryTotalUnreadCount(accountId = 1)
        assertEquals(3, count)

        val unreadList = articleDao.queryOldestUnreadArticleListWithFeed(accountId = 1, limit = 2)
        assertEquals(2, unreadList.size)
        // FIFO order: oldest first (a2, then a3)
        assertEquals("a2", unreadList[0].article.id)
        assertEquals("Oldest", unreadList[0].article.title)
        assertEquals("Tech News", unreadList[0].feed.name)

        assertEquals("a3", unreadList[1].article.id)
        assertEquals("Middle", unreadList[1].article.title)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.repository.TriageDaoTest"`
Expected: FAIL with compilation error (methods `queryOldestUnreadArticleListWithFeed` and `queryTotalUnreadCount` not yet defined).

- [ ] **Step 3: Implement DAO queries**

Chỉnh sửa `app/src/main/java/com/mckimquyen/reader/domain/repository/ArticleDao.kt`: Thêm 2 query method:

```kotlin
    @Query("SELECT COUNT(*) FROM article WHERE accountId = :accountId AND isUnread = 1")
    suspend fun queryTotalUnreadCount(accountId: Int): Int

    @Transaction
    @Query("SELECT " + ARTICLE_LIST_COLUMNS + " FROM article WHERE accountId = :accountId AND isUnread = 1 ORDER BY date ASC LIMIT :limit")
    suspend fun queryOldestUnreadArticleListWithFeed(accountId: Int, limit: Int = 50): List<ArticleListItemWithFeed>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.repository.TriageDaoTest"`
Expected: PASS (1 test completed, 0 failed).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/domain/repository/ArticleDao.kt app/src/test/java/com/mckimquyen/reader/domain/repository/TriageDaoTest.kt
git commit -m "feat(dao): add queryOldestUnreadArticleListWithFeed and queryTotalUnreadCount [EXC-14]

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: Brain RPG Integration — Thưởng XP Khi Xử Lý Triage

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/model/rpg/BrainRpgRewardType.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/repository/BrainRpgRepository.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/domain/repository/BrainRpgTriageTest.kt`

**Interfaces:**
- Produces:
  - `BrainRpgRewardType.TRIAGE`
  - `BrainRpgRepository.awardTriageXpOnce(articleId: String, amount: Long = 5L, nowMillis: Long): Long`

- [ ] **Step 1: Write the failing test**

Tạo `app/src/test/java/com/mckimquyen/reader/domain/repository/BrainRpgTriageTest.kt`:

```kotlin
package com.mckimquyen.reader.domain.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.rpg.BrainRpgRewardType
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class BrainRpgTriageTest {

    private lateinit var db: AndroidDatabase
    private lateinit var repository: BrainRpgRepository
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AndroidDatabase::class.java)
            .allowMainThreadQueries().build()
        val dao = db.brainRpgDao()
        val legacyImporter = BrainRpgLegacyImporter(
            db,
            context.getSharedPreferences("test_rpg_prefs", Context.MODE_PRIVATE),
            dao,
        )
        repository = BrainRpgRepository(
            context = context,
            database = db,
            dao = dao,
            legacyImporter = legacyImporter,
            applicationScope = testScope,
            ioDispatcher = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun awardTriageXpOnce_awards5XpFirstTime_andZeroOnRepeat() = testScope.runTest {
        val xp1 = repository.awardTriageXpOnce(articleId = "article_1", amount = 5L, nowMillis = 100_000L)
        assertEquals(5L, xp1)

        val xpRepeat = repository.awardTriageXpOnce(articleId = "article_1", amount = 5L, nowMillis = 100_000L)
        assertEquals(0L, xpRepeat)

        val progress = repository.getProgress()
        assertEquals(5L, progress.totalXp)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.repository.BrainRpgTriageTest"`
Expected: FAIL with compilation error (missing `BrainRpgRewardType.TRIAGE` and `awardTriageXpOnce`).

- [ ] **Step 3: Implement BrainRpgRewardType.TRIAGE and awardTriageXpOnce**

1. Trong `app/src/main/java/com/mckimquyen/reader/domain/model/rpg/BrainRpgRewardType.kt`:
```kotlin
enum class BrainRpgRewardType {
    READ,
    QUIZ,
    DOUBLE,
    TRIAGE,
}
```

2. Trong `app/src/main/java/com/mckimquyen/reader/domain/repository/BrainRpgRepository.kt`:
Thêm phương thức:
```kotlin
    /** Award triage XP once per article; repeat triage calls on the same article return 0. */
    suspend fun awardTriageXpOnce(
        articleId: String,
        amount: Long = 5L,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        if (articleId.isBlank()) return 0L
        return mutationMutex.withLock {
            legacyImporter.ensureImported(nowMillis)
            val before = dao.getProfile() ?: BrainRpgProfileEntity().also { dao.upsertProfile(it) }
            val (streakDays, shieldActive) = nextStreak(before.streakDays, before.lastReadEpochDay, before.streakShieldActive, epochDay(nowMillis))
            val claimed = database.withTransaction {
                dao.claimAndApplyReward(
                    BrainRpgRewardEntity(
                        articleId = articleId,
                        rewardType = BrainRpgRewardType.TRIAGE,
                        createdAtMillis = nowMillis,
                    ),
                    categoryId = "GENERAL",
                    practiceAtMillis = nowMillis,
                    baseXpDelta = amount,
                    newStreakDays = streakDays,
                    newLastReadEpochDay = epochDay(nowMillis),
                    newStreakShieldActive = shieldActive,
                )
            }
            refresh()
            if (claimed) amount else 0L
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.repository.BrainRpgTriageTest"`
Expected: PASS (1 test completed, 0 failed).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/domain/model/rpg/BrainRpgRewardType.kt app/src/main/java/com/mckimquyen/reader/domain/repository/BrainRpgRepository.kt app/src/test/java/com/mckimquyen/reader/domain/repository/BrainRpgTriageTest.kt
git commit -m "feat(rpg): add BrainRpgRewardType.TRIAGE and awardTriageXpOnce [EXC-14]

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: Localization — 13 Chuỗi Mới Cho 38 Locale

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: 37 `app/src/main/res/values-*/strings.xml`
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/pref/StringResourceParityTest.java`

**Chuỗi cần thêm (13 chuỗi):**
1. `feed_zero_triage_title`
2. `triage_swipe_left_skip`
3. `triage_swipe_right_save`
4. `triage_swipe_up_read`
5. `triage_progress_format` ("%1$d / %2$d")
6. `triage_inbox_zero_title`
7. `triage_inbox_zero_subtitle`
8. `triage_summary_processed` ("%1$d")
9. `triage_summary_saved` ("%1$d")
10. `triage_summary_skipped` ("%1$d")
11. `triage_summary_xp` ("+%1$d XP")
12. `triage_finish_button`
13. `triage_empty_state`

- [ ] **Step 1: Write English base strings in values/strings.xml**

Thêm vào `app/src/main/res/values/strings.xml`:
```xml
    <!-- Feed Zero Triage (EXC-14) -->
    <string name="feed_zero_triage_title">Feed Zero Triage</string>
    <string name="triage_swipe_left_skip">Skip</string>
    <string name="triage_swipe_right_save">Save</string>
    <string name="triage_swipe_up_read">Read now</string>
    <string name="triage_progress_format">%1$d / %2$d</string>
    <string name="triage_inbox_zero_title">🎉 Inbox Zero Reached!</string>
    <string name="triage_inbox_zero_subtitle">You have cleared all unread articles in this batch.</string>
    <string name="triage_summary_processed">Processed: %1$d</string>
    <string name="triage_summary_saved">Saved for later: %1$d</string>
    <string name="triage_summary_skipped">Marked as read: %1$d</string>
    <string name="triage_summary_xp">+%1$d Brain RPG XP</string>
    <string name="triage_finish_button">Done</string>
    <string name="triage_empty_state">No unread articles to triage!</string>
```

- [ ] **Step 2: Run StringResourceParityTest to verify it fails**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.pref.StringResourceParityTest"`
Expected: FAIL reporting missing keys in `values-*` directories.

- [ ] **Step 3: Propagate strings across all 37 locale resource files**

Tạo script Python chuyên dụng bổ sung bản dịch tự nhiên cho 13 keys trên toàn bộ 37 thư mục `values-*` (tiếng Việt `values-vi`: "Feed Zero Triage", "Bỏ qua", "Lưu lại", "Đọc ngay", "%1$d / %2$d", "🎉 Đạt Inbox Zero!", "Bạn đã dọn sạch toàn bộ bài chưa đọc trong đợt này.", "Đã xử lý: %1$d", "Đã lưu lại: %1$d", "Đã đánh dấu đọc: %1$d", "+%1$d XP Brain RPG", "Hoàn tất", "Không có bài chưa đọc nào cần xử lý!").

- [ ] **Step 4: Run StringResourceParityTest to verify it passes**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.pref.StringResourceParityTest"`
Expected: PASS (all tests in StringResourceParityTest green, 0 missing keys, 0 duplicates).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values*/strings.xml
git commit -m "feat(i18n): add Feed Zero Triage strings across 38 locales [EXC-14]

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 4: ViewModel — TriageViewModel Quản Lý Queue, State & Callbacks

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/triage/TriageUiState.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/triage/TriageViewModel.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/ui/page/triage/TriageViewModelTest.kt`

**Interfaces:**
- Produces:
  - `TriageUiState`:
    - `isLoading: Boolean`
    - `articles: List<ArticleListItemWithFeed>`
    - `totalCount: Int`
    - `processedCount: Int`
    - `savedCount: Int`
    - `skippedCount: Int`
    - `totalXpEarned: Long`
    - `isInboxZero: Boolean`
    - `currentArticle: ArticleListItemWithFeed?`
    - `nextArticle: ArticleListItemWithFeed?`
  - `TriageViewModel`:
    - `val uiState: StateFlow<TriageUiState>`
    - `fun skipCurrent()`
    - `fun saveCurrent()`
    - `fun markCurrentReadForNavigation(): String?`

- [ ] **Step 1: Write the failing test**

Tạo `app/src/test/java/com/mckimquyen/reader/ui/page/triage/TriageViewModelTest.kt`:

```kotlin
package com.mckimquyen.reader.ui.page.triage

import com.mckimquyen.reader.domain.model.article.ArticleListItem
import com.mckimquyen.reader.domain.model.article.ArticleListItemWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.BrainRpgRepository
import com.mckimquyen.reader.domain.repository.WidgetRefreshNotifier
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TriageViewModelTest {

    private val articleDao: ArticleDao = mockk(relaxed = true)
    private val widgetRefreshNotifier: WidgetRefreshNotifier = mockk(relaxed = true)
    private val brainRpgRepository: BrainRpgRepository = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private fun createArticle(id: String, title: String) = ArticleListItemWithFeed(
        article = ArticleListItem(
            id = id,
            date = 1000L,
            title = title,
            author = "Author",
            shortDescription = "Description",
            img = null,
            link = "https://example.com/$id",
            feedId = "feed1",
            accountId = 1,
            isUnread = true,
            isStarred = false,
            isReadLater = false,
            updateAt = 1000L,
            aiSummary = null,
            readAt = null,
            audioUrl = null,
            audioMime = null,
            audioDurationSec = null,
        ),
        feed = Feed(id = "feed1", name = "Test Feed", url = "https://example.com", accountId = 1),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadInitialData_populatesArticlesAndCount() = runTest {
        val articles = listOf(createArticle("1", "First"), createArticle("2", "Second"))
        coEvery { articleDao.queryTotalUnreadCount(1) } returns 2
        coEvery { articleDao.queryOldestUnreadArticleListWithFeed(1, 50) } returns articles

        val viewModel = TriageViewModel(
            accountId = 1,
            articleDao = articleDao,
            widgetRefreshNotifier = widgetRefreshNotifier,
            brainRpgRepository = brainRpgRepository,
            ioDispatcher = testDispatcher,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.totalCount)
        assertEquals(2, state.articles.size)
        assertEquals("1", state.currentArticle?.article?.id)
        assertEquals("2", state.nextArticle?.article?.id)
        assertFalse(state.isInboxZero)
    }

    @Test
    fun skipCurrent_marksAsRead_andAdvancesToNext() = runTest {
        val articles = listOf(createArticle("1", "First"), createArticle("2", "Second"))
        coEvery { articleDao.queryTotalUnreadCount(1) } returns 2
        coEvery { articleDao.queryOldestUnreadArticleListWithFeed(1, 50) } returns articles
        coEvery { brainRpgRepository.awardTriageXpOnce("1", 5L, any()) } returns 5L

        val viewModel = TriageViewModel(
            accountId = 1,
            articleDao = articleDao,
            widgetRefreshNotifier = widgetRefreshNotifier,
            brainRpgRepository = brainRpgRepository,
            ioDispatcher = testDispatcher,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.skipCurrent()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { articleDao.markAsReadByArticleId(1, "1", false) }
        coVerify { widgetRefreshNotifier.refresh() }

        val state = viewModel.uiState.value
        assertEquals(1, state.processedCount)
        assertEquals(1, state.skippedCount)
        assertEquals(0, state.savedCount)
        assertEquals(5L, state.totalXpEarned)
        assertEquals("2", state.currentArticle?.article?.id)
    }

    @Test
    fun saveCurrent_marksAsStarredAndRead_andAdvances() = runTest {
        val articles = listOf(createArticle("1", "First"))
        coEvery { articleDao.queryTotalUnreadCount(1) } returns 1
        coEvery { articleDao.queryOldestUnreadArticleListWithFeed(1, 50) } returns articles
        coEvery { brainRpgRepository.awardTriageXpOnce("1", 5L, any()) } returns 5L

        val viewModel = TriageViewModel(
            accountId = 1,
            articleDao = articleDao,
            widgetRefreshNotifier = widgetRefreshNotifier,
            brainRpgRepository = brainRpgRepository,
            ioDispatcher = testDispatcher,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.saveCurrent()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { articleDao.markAsStarredByArticleId(1, "1", true) }
        coVerify { articleDao.markAsReadByArticleId(1, "1", false) }
        coVerify { widgetRefreshNotifier.refresh() }

        val state = viewModel.uiState.value
        assertEquals(1, state.processedCount)
        assertEquals(1, state.savedCount)
        assertTrue(state.isInboxZero)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.page.triage.TriageViewModelTest"`
Expected: FAIL (TriageViewModel does not exist).

- [ ] **Step 3: Implement TriageUiState and TriageViewModel**

1. Tạo `app/src/main/java/com/mckimquyen/reader/ui/page/triage/TriageUiState.kt`:
```kotlin
package com.mckimquyen.reader.ui.page.triage

import com.mckimquyen.reader.domain.model.article.ArticleListItemWithFeed

data class TriageUiState(
    val isLoading: Boolean = true,
    val articles: List<ArticleListItemWithFeed> = emptyList(),
    val totalCount: Int = 0,
    val processedCount: Int = 0,
    val savedCount: Int = 0,
    val skippedCount: Int = 0,
    val totalXpEarned: Long = 0L,
    val isInboxZero: Boolean = false,
) {
    val currentArticle: ArticleListItemWithFeed?
        get() = articles.firstOrNull()

    val nextArticle: ArticleListItemWithFeed?
        get() = if (articles.size > 1) articles[1] else null
}
```

2. Tạo `app/src/main/java/com/mckimquyen/reader/ui/page/triage/TriageViewModel.kt`:
```kotlin
package com.mckimquyen.reader.ui.page.triage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.BrainRpgRepository
import com.mckimquyen.reader.domain.repository.WidgetRefreshNotifier
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TriageViewModel @Inject constructor(
    private val articleDao: ArticleDao,
    private val widgetRefreshNotifier: WidgetRefreshNotifier,
    private val brainRpgRepository: BrainRpgRepository,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    // Internal constructor for unit tests
    internal constructor(
        accountId: Int,
        articleDao: ArticleDao,
        widgetRefreshNotifier: WidgetRefreshNotifier,
        brainRpgRepository: BrainRpgRepository,
        ioDispatcher: CoroutineDispatcher,
    ) : this(articleDao, widgetRefreshNotifier, brainRpgRepository, ioDispatcher) {
        this.currentAccountId = accountId
        loadInitialData()
    }

    private var currentAccountId: Int = 1

    private val _uiState = MutableStateFlow(TriageUiState())
    val uiState: StateFlow<TriageUiState> = _uiState.asStateFlow()

    init {
        // Will be populated when accountId is set or defaults to 1
        loadInitialData()
    }

    fun setAccountId(accountId: Int) {
        if (currentAccountId != accountId) {
            currentAccountId = accountId
            loadInitialData()
        }
    }

    fun loadInitialData() {
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(isLoading = true) }
            val total = articleDao.queryTotalUnreadCount(currentAccountId)
            if (total == 0) {
                _uiState.update { it.copy(isLoading = false, isInboxZero = true, totalCount = 0) }
                return@launch
            }
            val list = articleDao.queryOldestUnreadArticleListWithFeed(currentAccountId, limit = 50)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    articles = list,
                    totalCount = total,
                    isInboxZero = list.isEmpty(),
                )
            }
        }
    }

    fun skipCurrent() {
        val current = _uiState.value.currentArticle ?: return
        viewModelScope.launch(ioDispatcher) {
            articleDao.markAsReadByArticleId(currentAccountId, current.article.id, false)
            widgetRefreshNotifier.refresh()
            val xpDelta = brainRpgRepository.awardTriageXpOnce(current.article.id, 5L)
            advanceCard(isSaved = false, xp = xpDelta)
        }
    }

    fun saveCurrent() {
        val current = _uiState.value.currentArticle ?: return
        viewModelScope.launch(ioDispatcher) {
            articleDao.markAsStarredByArticleId(currentAccountId, current.article.id, true)
            articleDao.markAsReadByArticleId(currentAccountId, current.article.id, false)
            widgetRefreshNotifier.refresh()
            val xpDelta = brainRpgRepository.awardTriageXpOnce(current.article.id, 5L)
            advanceCard(isSaved = true, xp = xpDelta)
        }
    }

    fun markCurrentReadForNavigation(): String? {
        val current = _uiState.value.currentArticle ?: return null
        val articleId = current.article.id
        viewModelScope.launch(ioDispatcher) {
            articleDao.markAsReadByArticleId(currentAccountId, articleId, false)
            widgetRefreshNotifier.refresh()
            val xpDelta = brainRpgRepository.awardTriageXpOnce(articleId, 5L)
            advanceCard(isSaved = false, xp = xpDelta)
        }
        return articleId
    }

    private suspend fun advanceCard(isSaved: Boolean, xp: Long) {
        _uiState.update { state ->
            val remaining = state.articles.drop(1)
            val newProcessed = state.processedCount + 1
            val newSaved = state.savedCount + if (isSaved) 1 else 0
            val newSkipped = state.skippedCount + if (!isSaved) 1 else 0
            val newXp = state.totalXpEarned + xp
            val isZero = remaining.isEmpty() && newProcessed >= state.totalCount
            state.copy(
                articles = remaining,
                processedCount = newProcessed,
                savedCount = newSaved,
                skippedCount = newSkipped,
                totalXpEarned = newXp,
                isInboxZero = isZero,
            )
        }

        // Tự nạp tiếp 50 bài nếu hàng đợi còn ít hơn 5 bài và chưa hết unread
        val state = _uiState.value
        if (state.articles.size < 5 && state.processedCount < state.totalCount) {
            val more = articleDao.queryOldestUnreadArticleListWithFeed(currentAccountId, limit = 50)
            if (more.isNotEmpty()) {
                _uiState.update { current ->
                    val combined = (current.articles + more).distinctBy { it.article.id }
                    current.copy(articles = combined)
                }
            } else if (_uiState.value.articles.isEmpty()) {
                _uiState.update { it.copy(isInboxZero = true) }
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.page.triage.TriageViewModelTest"`
Expected: PASS (3 tests completed, 0 failed).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/page/triage/TriageUiState.kt app/src/main/java/com/mckimquyen/reader/ui/page/triage/TriageViewModel.kt app/src/test/java/com/mckimquyen/reader/ui/page/triage/TriageViewModelTest.kt
git commit -m "feat(triage): create TriageViewModel and TriageUiState [EXC-14]

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 5: UI Components — TriageCard, CardStack, BottomBar & InboxZeroView

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/triage/TriageCard.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/triage/TriageCardStack.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/triage/TriageBottomBar.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/triage/TriageInboxZeroView.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/triage/TriagePage.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/ui/page/triage/TriageCardStackTest.kt`

**Interfaces:**
- Produces:
  - `TriagePage(navController, onBack)`
  - `TriageCardStack(currentArticle, nextArticle, onSwipeLeft, onSwipeRight, onSwipeUp)`
  - `TriageBottomBar(onSkip, onReadNow, onSave)`
  - `TriageInboxZeroView(processed, saved, skipped, xp, onFinish)`

- [ ] **Step 1: Write UI tests for Card Stack and Actions**

Tạo `app/src/test/java/com/mckimquyen/reader/ui/page/triage/TriageCardStackTest.kt`:

```kotlin
package com.mckimquyen.reader.ui.page.triage

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mckimquyen.reader.domain.model.article.ArticleListItem
import com.mckimquyen.reader.domain.model.article.ArticleListItemWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class TriageCardStackTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleArticle = ArticleListItemWithFeed(
        article = ArticleListItem(
            id = "test_1",
            date = 1000L,
            title = "Breaking News Title",
            author = "Reporter",
            shortDescription = "Sample snippet of the news story.",
            img = null,
            link = "https://example.com/news",
            feedId = "feed_1",
            accountId = 1,
            isUnread = true,
            isStarred = false,
            isReadLater = false,
            updateAt = 1000L,
            aiSummary = null,
            readAt = null,
            audioUrl = null,
            audioMime = null,
            audioDurationSec = null,
        ),
        feed = Feed(id = "feed_1", name = "BBC News", url = "https://bbc.com/rss", accountId = 1),
    )

    @Test
    fun triageCard_displaysFeedNameTitleAndDescription() {
        composeRule.setContent {
            TriageCard(item = sampleArticle)
        }

        composeRule.onNodeWithText("BBC News").assertExists()
        composeRule.onNodeWithText("Breaking News Title").assertExists()
        composeRule.onNodeWithText("Sample snippet of the news story.").assertExists()
    }

    @Test
    fun triageBottomBar_triggersCallbacksOnButtonClick() {
        var skipped = false
        var readNow = false
        var saved = false

        composeRule.setContent {
            TriageBottomBar(
                onSkip = { skipped = true },
                onReadNow = { readNow = true },
                onSave = { saved = true },
            )
        }

        composeRule.onNodeWithText("Skip").performClick()
        assertTrue(skipped)

        composeRule.onNodeWithText("Read now").performClick()
        assertTrue(readNow)

        composeRule.onNodeWithText("Save").performClick()
        assertTrue(saved)
    }

    @Test
    fun triageInboxZeroView_displaysSummaryStatsAndFinishButton() {
        var finished = false

        composeRule.setContent {
            TriageInboxZeroView(
                processedCount = 42,
                savedCount = 10,
                skippedCount = 32,
                totalXpEarned = 210L,
                onFinish = { finished = true },
            )
        }

        composeRule.onNodeWithText("🎉 Inbox Zero Reached!").assertExists()
        composeRule.onNodeWithText("Processed: 42").assertExists()
        composeRule.onNodeWithText("Saved for later: 10").assertExists()
        composeRule.onNodeWithText("Marked as read: 32").assertExists()
        composeRule.onNodeWithText("+210 Brain RPG XP").assertExists()

        composeRule.onNodeWithText("Done").performClick()
        assertTrue(finished)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.page.triage.TriageCardStackTest"`
Expected: FAIL (UI components not yet defined).

- [ ] **Step 3: Implement UI components**

1. Tạo `app/src/main/java/com/mckimquyen/reader/ui/page/triage/TriageCard.kt`:
Hiển thị thẻ bài với Material 3 Card, feed icon/name, thời gian tương đối, title (2-3 dòng, bold, TitleLarge), description (3-4 dòng, BodyMedium), Coil AsyncImage nếu có ảnh thumbnail.
2. Tạo `app/src/main/java/com/mckimquyen/reader/ui/page/triage/TriageCardStack.kt`:
Quản lý `offsetX`, `offsetY` qua `Animatable(0f)`, xử lý cử chỉ `detectDragGestures` trên thẻ mặt; hiển thị badge động (Xanh/Đỏ/Xanh dương) khi kéo; khi thả tay vượt ngưỡng `SWIPE_THRESHOLD` (100.dp) thì animate bay ra khỏi màn hình và kích hoạt callback.
3. Tạo `app/src/main/java/com/mckimquyen/reader/ui/page/triage/TriageBottomBar.kt`:
3 nút tròn hành động (Đỏ/Xám, Xanh dương, Vàng/Primary) với icon và label hỗ trợ accessibility.
4. Tạo `app/src/main/java/com/mckimquyen/reader/ui/page/triage/TriageInboxZeroView.kt`:
Trạng thái hoàn thành Inbox Zero kèm thống kê chi tiết.
5. Tạo `app/src/main/java/com/mckimquyen/reader/ui/page/triage/TriagePage.kt`:
Kết nối `TriageViewModel`, TopAppBar, LinearProgressIndicator, CardStack, BottomBar và InboxZeroView.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.page.triage.TriageCardStackTest"`
Expected: PASS (3 tests green).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/page/triage/ app/src/test/java/com/mckimquyen/reader/ui/page/triage/TriageCardStackTest.kt
git commit -m "feat(ui): implement TriageCard, TriageCardStack, BottomBar and TriagePage [EXC-14]

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 6: Navigation & Entry Points — Tích Hợp Vào Flow & App Navigation

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/common/RouteName.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/common/HomeEntry.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/home/flow/FlowActionsMenu.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/home/flow/FlowPage.kt`

- [ ] **Step 1: Declare FEED_ZERO_TRIAGE in RouteName.kt**

```kotlin
    // Feed Zero Triage (EXC-14)
    const val FEED_ZERO_TRIAGE = "feed_zero_triage"
```

- [ ] **Step 2: Add composable route in HomeEntry.kt**

```kotlin
            animatedComposable(route = RouteName.FEED_ZERO_TRIAGE, eInk = eInk) {
                TriagePage(
                    navController = navController,
                    onBack = { navController.popBackStack() },
                )
            }
```

- [ ] **Step 3: Add menu item in FlowActionsMenu.kt**

Thêm `onFeedZeroTriage: () -> Unit = {}` vào `FlowActionsMenu` và thêm item:
```kotlin
        DropdownMenuItem(
            text = { Text(stringResource(R.string.feed_zero_triage_title)) },
            leadingIcon = { Icon(Icons.Rounded.ElectricBolt, contentDescription = null) },
            onClick = { onDismiss(); onFeedZeroTriage() },
        )
```

- [ ] **Step 4: Wire navigation in FlowPage.kt**

Nối `onFeedZeroTriage = { navController.navigate(RouteName.FEED_ZERO_TRIAGE) { launchSingleTop = true } }` trong `FlowPage.kt`.

- [ ] **Step 5: Build check**

Run: `./gradlew assembleDevDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/page/common/RouteName.kt app/src/main/java/com/mckimquyen/reader/ui/page/common/HomeEntry.kt app/src/main/java/com/mckimquyen/reader/ui/page/home/flow/FlowActionsMenu.kt app/src/main/java/com/mckimquyen/reader/ui/page/home/flow/FlowPage.kt
git commit -m "feat(navigation): wire Feed Zero Triage into FlowActionsMenu and NavHost [EXC-14]

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 7: Toàn Bộ Test Suite, Lint & Báo Cáo Hoàn Tất

**Files:**
- Create: `doc/task/done/EXC-14_feed-zero-triage-mode_DONE.md`
- Modify: `doc/feature.md`
- Modify: `doc/task/todo/05_EXCLUSIVE_KILLER_FEATURES.md`
- Delete: `doc/task/todo/EXC-14_feed-zero-triage-mode.md`

- [ ] **Step 1: Run full unit test suite**

Run: `./gradlew testDevDebugUnitTest --rerun`
Expected: BUILD SUCCESSFUL, all unit tests pass, 0 failures, 0 skipped.

- [ ] **Step 2: Run lint check**

Run: `./gradlew lintDevDebug`
Expected: 0 errors.

- [ ] **Step 3: Update documentation**

1. Chuyển `doc/task/todo/EXC-14_feed-zero-triage-mode.md` sang `doc/task/done/EXC-14_feed-zero-triage-mode_DONE.md`.
2. Cập nhật `05_EXCLUSIVE_KILLER_FEATURES.md` chuyển EXC-14 sang `✅ Done`.
3. Cập nhật `doc/feature.md` chuyển EXC-14 từ `📋 Picked` sang `✅ Recently implemented`.

- [ ] **Step 4: Final commit & push**

```bash
git add doc/
git commit -m "docs: complete [EXC-14] Feed Zero Triage Mode and move to done

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```
