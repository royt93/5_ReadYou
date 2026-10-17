# INGEST-02: Android Share Target — Đọc Sau (Read-Later) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cho phép người dùng lưu bài viết đọc sau từ Chrome, Twitter/X và các app ngoài thông qua Android Share Sheet vào System Feed "Đọc sau" với giao diện xác nhận Compose ModalBottomSheet và tải ngầm toàn văn bằng WorkManager.

**Architecture:** Translucent `ShareTargetActivity` nhận `Intent.ACTION_SEND` (text/plain) -> `UrlExtractor` trích xuất link -> `ShareTargetBottomSheet` xác nhận -> `SystemReadLaterFeedManager` đảm bảo Feed hệ thống tồn tại -> Lưu placeholder `Article` vào Room -> Enqueue `ReadLaterWorker` (WorkManager) tải ngầm toàn văn bằng `RssHelper.parseFullContent` -> Toast thông báo -> `finish()` đóng activity.

**Tech Stack:** Kotlin 2.1.0, Jetpack Compose, Material 3, AndroidX WorkManager, Hilt, Room Database v14, OkHttp 4, Readability4JExtended, JUnit4, MockK, Robolectric.

## Global Constraints

- Device target: Pixel_10_Pro emulator-5554 ONLY (không chạm bất kỳ thiết bị vật lý nào khác theo session lock).
- Magic numbers: Dùng named constants (`MAX_CONTENT_BYTES`, `HTTP_TIMEOUT_SECONDS`, v.v.).
- Null-safety: Không dùng `lateinit` trừ trường hợp `@Inject` / test rule; dùng nullable `?` + safe-call.
- Resource leaks: Đảm bảo đóng `Response.use { }`, hủy coroutine job khi Compose sheet dismiss.
- Localize đủ 6 ngôn ngữ: `en`, `vi`, `zh-rCN`, `ja`, `fr`, `de`.
- TDD bắt buộc: Viết test trước (RED), verify fail, viết minimal code (GREEN), verify pass, commit.

---

### Task 1: UrlExtractor & Security Validator

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/share/UrlExtractor.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/share/UrlExtractorTest.kt`

**Interfaces:**
- Produces: `UrlExtractor.extractValidUrl(text: String?): String?` (trả về URL `http`/`https` hợp lệ đầu tiên, loại bỏ schema nguy hiểm `file`, `javascript`, `content`).

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/mckimquyen/reader/infrastructure/share/UrlExtractorTest.kt
package com.mckimquyen.reader.infrastructure.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlExtractorTest {
    @Test
    fun returnsNullForNullOrEmptyText() {
        assertNull(UrlExtractor.extractValidUrl(null))
        assertNull(UrlExtractor.extractValidUrl(""))
        assertNull(UrlExtractor.extractValidUrl("   "))
    }

    @Test
    fun extractsRawHttpsUrl() {
        val input = "https://example.com/story/123"
        assertEquals("https://example.com/story/123", UrlExtractor.extractValidUrl(input))
    }

    @Test
    fun extractsUrlEmbeddedInShareText() {
        val input = "Check out this great article! https://news.test/post?id=456 and let me know."
        assertEquals("https://news.test/post?id=456", UrlExtractor.extractValidUrl(input))
    }

    @Test
    fun ignoresDangerousSchemes() {
        assertNull(UrlExtractor.extractValidUrl("file:///android_asset/secret.txt"))
        assertNull(UrlExtractor.extractValidUrl("javascript:alert(1)"))
        assertNull(UrlExtractor.extractValidUrl("content://media/external/images"))
    }

    @Test
    fun trimsTrailingPunctuation() {
        val input = "Read this: https://example.test/page."
        assertEquals("https://example.test/page", UrlExtractor.extractValidUrl(input))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.share.UrlExtractorTest"`
Expected: Compilation failure or FAIL (Unresolved reference: UrlExtractor)

- [ ] **Step 3: Write minimal implementation**

```kotlin
// app/src/main/java/com/mckimquyen/reader/infrastructure/share/UrlExtractor.kt
package com.mckimquyen.reader.infrastructure.share

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.regex.Pattern

object UrlExtractor {
    private val URL_PATTERN = Pattern.compile("https?://[^\\s<>\"']+")

    fun extractValidUrl(text: String?): String? {
        if (text.isNull prematureBlank()) return null
        val matcher = URL_PATTERN.matcher(text)
        while (matcher.find()) {
            val raw = matcher.group().trimEnd('.', ',', ';', '!', '?')
            val parsed = raw.toHttpUrlOrNull()
            if (parsed != null && (parsed.scheme == "http" || parsed.scheme == "https")) {
                return raw
            }
        }
        return null
    }

    private fun String?.prematureBlank(): Boolean = this == null || this.trim().isEmpty()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.share.UrlExtractorTest"`
Expected: PASS (5 tests pass)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/infrastructure/share/UrlExtractor.kt \
        app/src/test/java/com/mckimquyen/reader/infrastructure/share/UrlExtractorTest.kt
git commit -m "feat(share): add UrlExtractor with scheme validation"
```

---

### Task 2: SystemReadLaterFeedManager

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/sv/SystemReadLaterFeedManager.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/model/feed/Feed.kt` (thêm constant `READ_LATER_FEED_ID` & `READ_LATER_SOURCE_TYPE`)
- Test: `app/src/test/java/com/mckimquyen/reader/domain/sv/SystemReadLaterFeedManagerTest.kt`

**Interfaces:**
- Consumes: `FeedDao`, `GroupDao`
- Produces: `SystemReadLaterFeedManager.ensureReadLaterFeed(accountId: Int, fallbackTitle: String): Feed`

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/mckimquyen/reader/domain/sv/SystemReadLaterFeedManagerTest.kt
package com.mckimquyen.reader.domain.sv

import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.repository.FeedDao
import com.mckimquyen.reader.domain.repository.GroupDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SystemReadLaterFeedManagerTest {
    private val feedDao = mockk<FeedDao>()
    private val groupDao = mockk<GroupDao>()
    private val manager = SystemReadLaterFeedManager(feedDao, groupDao)

    @Test
    fun returnsExistingFeedIfAlreadyPresent() = runBlocking {
        val existing = Feed(
            id = Feed.READ_LATER_FEED_ID,
            name = "Read Later",
            url = Feed.READ_LATER_URL,
            groupId = "group-1",
            accountId = 1,
            sourceType = Feed.READ_LATER_SOURCE_TYPE
        )
        coEvery { feedDao.queryById(Feed.READ_LATER_FEED_ID) } returns existing

        val result = manager.ensureReadLaterFeed(accountId = 1, fallbackTitle = "Read Later")
        assertEquals(existing, result)
        coVerify(exactly = 0) { feedDao.insert(any()) }
    }

    @Test
    fun createsNewFeedUsingFirstGroupWhenMissing() = runBlocking {
        coEvery { feedDao.queryById(Feed.READ_LATER_FEED_ID) } returns null
        coEvery { groupDao.queryAll(1) } returns listOf(Group(id = "default-group", name = "Default", accountId = 1))
        coEvery { feedDao.insert(any()) } returns Unit

        val result = manager.ensureReadLaterFeed(accountId = 1, fallbackTitle = "Đọc sau")
        assertEquals(Feed.READ_LATER_FEED_ID, result.id)
        assertEquals("Đọc sau", result.name)
        assertEquals("default-group", result.groupId)
        assertEquals(Feed.READ_LATER_SOURCE_TYPE, result.sourceType)
        coVerify(exactly = 1) { feedDao.insert(match { it.id == Feed.READ_LATER_FEED_ID }) }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.sv.SystemReadLaterFeedManagerTest"`
Expected: FAIL (unresolved symbols)

- [ ] **Step 3: Update Feed.kt constants and implement SystemReadLaterFeedManager**

Update `Feed.kt`:
```kotlin
// In Feed.kt companion object:
const val RSS_SOURCE_TYPE = "RSS"
const val WEB_SOURCE_TYPE = "WEB"
const val READ_LATER_SOURCE_TYPE = "READ_LATER"
const val READ_LATER_FEED_ID = "system_feed_read_later"
const val READ_LATER_URL = "https://local.rsscathub/read-later"
```

Implement `SystemReadLaterFeedManager.kt`:
```kotlin
package com.mckimquyen.reader.domain.sv

import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.repository.FeedDao
import com.mckimquyen.reader.domain.repository.GroupDao
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemReadLaterFeedManager @Inject constructor(
    private val feedDao: FeedDao,
    private val groupDao: GroupDao,
) {
    suspend fun ensureReadLaterFeed(accountId: Int, fallbackTitle: String): Feed {
        val existing = feedDao.queryById(Feed.READ_LATER_FEED_ID)
        if (existing != null) return existing

        val groups = groupDao.queryAll(accountId)
        val targetGroupId = if (groups.isNotEmpty()) {
            groups.first().id
        } else {
            val newGroup = Group(id = UUID.randomUUID().toString(), name = fallbackTitle, accountId = accountId)
            groupDao.insert(newGroup)
            newGroup.id
        }

        val feed = Feed(
            id = Feed.READ_LATER_FEED_ID,
            name = fallbackTitle,
            url = Feed.READ_LATER_URL,
            groupId = targetGroupId,
            accountId = accountId,
            sourceType = Feed.READ_LATER_SOURCE_TYPE,
            sourceUrl = Feed.READ_LATER_URL,
        )
        feedDao.insert(feed)
        return feed
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.sv.SystemReadLaterFeedManagerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/domain/model/feed/Feed.kt \
        app/src/main/java/com/mckimquyen/reader/domain/sv/SystemReadLaterFeedManager.kt \
        app/src/test/java/com/mckimquyen/reader/domain/sv/SystemReadLaterFeedManagerTest.kt
git commit -m "feat(share): add SystemReadLaterFeedManager and Feed constants"
```

---

### Task 3: ReadLaterWorker & Ingestion Service

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/share/ReadLaterWorker.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/share/ReadLaterWorkerTest.kt`

**Interfaces:**
- Consumes: `RssHelper.parseFullContent`, `ArticleDao`
- Produces: `ReadLaterWorker.doWork()` (cập nhật bài viết với fullContent và shortDescription từ Readability)

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/mckimquyen/reader/infrastructure/share/ReadLaterWorkerTest.kt
package com.mckimquyen.reader.infrastructure.share

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

class ReadLaterWorkerTest {
    private val context = mockk<Context>(relaxed = true)
    private val workerParams = mockk<WorkerParameters>(relaxed = true)
    private val articleDao = mockk<ArticleDao>()
    private val rssHelper = mockk<RssHelper>()

    @Test
    fun returnsFailureWhenArticleIdOrUrlMissing() = runBlocking {
        coEvery { workerParams.inputData } returns workDataOf()
        val worker = ReadLaterWorker(context, workerParams, articleDao, rssHelper)
        assertEquals(ListenableWorker.Result.failure(), worker.doWork())
    }

    @Test
    fun updatesArticleWithFullContentOnSuccess() = runBlocking {
        val article = Article(
            id = "article-123",
            date = Date(),
            title = "Sample Story",
            rawDescription = "",
            shortDescription = "Sample",
            link = "https://example.com/story",
            feedId = "system_feed_read_later",
            accountId = 1,
            isReadLater = true
        )
        coEvery { workerParams.inputData } returns workDataOf(
            ReadLaterWorker.KEY_ARTICLE_ID to "article-123",
            ReadLaterWorker.KEY_URL to "https://example.com/story",
            ReadLaterWorker.KEY_TITLE to "Sample Story"
        )
        coEvery { articleDao.queryById("article-123") } returns article
        coEvery { rssHelper.parseFullContent("https://example.com/story", "Sample Story", any()) } returns "<p>Clean readable text</p>"
        coEvery { articleDao.update(any()) } returns Unit

        val worker = ReadLaterWorker(context, workerParams, articleDao, rssHelper)
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        coVerify(exactly = 1) {
            articleDao.update(match { it.id == "article-123" && it.fullContent == "<p>Clean readable text</p>" })
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.share.ReadLaterWorkerTest"`
Expected: FAIL

- [ ] **Step 3: Implement ReadLaterWorker**

```kotlin
// app/src/main/java/com/mckimquyen/reader/infrastructure/share/ReadLaterWorker.kt
package com.mckimquyen.reader.infrastructure.share

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class ReadLaterWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val workerParams: WorkerParameters,
    private val articleDao: ArticleDao,
    private val rssHelper: RssHelper,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val articleId = inputData.getString(KEY_ARTICLE_ID) ?: return@withContext Result.failure()
        val url = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
        val fallbackTitle = inputData.getString(KEY_TITLE).orEmpty()

        val article = articleDao.queryById(articleId) ?: return@withContext Result.failure()
        val cleanContent = runCatching {
            rssHelper.parseFullContent(url, fallbackTitle.ifBlank { article.title }, sourceType = Feed.WEB_SOURCE_TYPE)
        }.getOrDefault("")

        if (cleanContent.isNotBlank()) {
            article.fullContent = cleanContent
            articleDao.update(article)
        }
        Result.success()
    }

    companion object {
        const val KEY_ARTICLE_ID = "key_article_id"
        const val KEY_URL = "key_url"
        const val KEY_TITLE = "key_title"
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.share.ReadLaterWorkerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/infrastructure/share/ReadLaterWorker.kt \
        app/src/test/java/com/mckimquyen/reader/infrastructure/share/ReadLaterWorkerTest.kt
git commit -m "feat(share): add ReadLaterWorker with full content extraction"
```

---

### Task 4: Strings, Themes & Localization

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-vi/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `app/src/main/res/values-ja/strings.xml`
- Modify: `app/src/main/res/values-fr/strings.xml`
- Modify: `app/src/main/res/values-de/strings.xml`
- Modify: `app/src/main/res/values/themes.xml`

- [ ] **Step 1: Add strings to values/strings.xml**

```xml
    <!-- Share Target / Read Later -->
    <string name="read_later_feed_title">Read Later</string>
    <string name="share_target_save_btn">Save to Read Later</string>
    <string name="share_target_cancel_btn">Cancel</string>
    <string name="share_target_saved_toast">Saved to Read Later</string>
    <string name="share_target_invalid_url">No valid link found</string>
    <string name="share_target_fetching_preview">Fetching preview…</string>
```

- [ ] **Step 2: Add localized strings to all 5 translations**

- `values-vi`: "Đọc sau", "Lưu vào Đọc sau", "Hủy", "Đã lưu vào Đọc sau", "Không tìm thấy liên kết hợp lệ", "Đang tải xem trước…"
- `values-zh-rCN`: "稍后阅读", "保存到稍后阅读", "取消", "已保存到稍后阅读", "未找到有效链接", "正在获取预览…"
- `values-ja`: "後で読む", "後で読むに保存", "キャンセル", "後で読むに保存しました", "有効なリンクが見つかりません", "プレビューを取得中…"
- `values-fr`: "À lire plus tard", "Enregistrer pour plus tard", "Annuler", "Enregistré pour plus tard", "Aucun lien valide trouvé", "Chargement de l’aperçu…"
- `values-de`: "Später lesen", "Für später speichern", "Abbrechen", "Für später gespeichert", "Kein gültiger Link gefunden", "Vorschau wird geladen…"

- [ ] **Step 3: Add Theme.Reader.Transparent to themes.xml**

```xml
    <style name="Theme.Reader.Transparent" parent="Theme.Reader">
        <item name="android:windowBackground">@android:color/transparent</item>
        <item name="android:windowIsTranslucent">true</item>
        <item name="android:windowNoTitle">true</item>
        <item name="android:backgroundDimEnabled">true</item>
    </style>
```

- [ ] **Step 4: Verify build with lint/resources**

Run: `./gradlew assembleDevDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values*/strings.xml app/src/main/res/values/themes.xml
git commit -m "feat(share): add localized strings and translucent theme for share target"
```

---

### Task 5: ShareTargetBottomSheet & ViewModel

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/share/ShareTargetViewModel.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/share/ShareTargetBottomSheet.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/ui/page/share/ShareTargetViewModelTest.kt`

**Interfaces:**
- Consumes: `UrlExtractor`, `SystemReadLaterFeedManager`, `ArticleDao`, `WorkManager`
- Produces: `ShareTargetViewModel.initFromShareText(text: String?)`, `ShareTargetViewModel.saveArticle(onSaved: () -> Unit)`

- [ ] **Step 1: Write the failing ViewModel test**

```kotlin
// app/src/test/java/com/mckimquyen/reader/ui/page/share/ShareTargetViewModelTest.kt
package com.mckimquyen.reader.ui.page.share

import androidx.work.WorkManager
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.sv.SystemReadLaterFeedManager
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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShareTargetViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val feedManager = mockk<SystemReadLaterFeedManager>()
    private val articleDao = mockk<ArticleDao>()
    private val workManager = mockk<WorkManager>(relaxed = true)

    @Before
    fun setUp() { Dispatchers.setMain(testDispatcher) }
    @After
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun extractsUrlAndSetsTargetState() {
        val vm = ShareTargetViewModel(feedManager, articleDao, workManager)
        vm.initFromShareText("Read https://news.test/article-123 now")
        assertEquals("https://news.test/article-123", vm.uiState.value.targetUrl)
    }

    @Test
    fun saveArticleInsertsPlaceholderAndEnqueuesWork() = runTest {
        val feed = Feed("system_feed_read_later", "Read Later", "https://local.rsscathub/read-later", "group-1", 1)
        coEvery { feedManager.ensureReadLaterFeed(any(), any()) } returns feed
        coEvery { articleDao.insert(any()) } returns Unit

        val vm = ShareTargetViewModel(feedManager, articleDao, workManager)
        vm.initFromShareText("https://news.test/article-123")

        var saved = false
        vm.saveArticle(accountId = 1, defaultTitle = "Read Later") { saved = true }
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(vm.uiState.value.savedArticleId)
        assertEquals(true, saved)
        coVerify(exactly = 1) { articleDao.insert(match { it.link == "https://news.test/article-123" && it.isReadLater }) }
        coVerify(exactly = 1) { workManager.enqueue(any<androidx.work.WorkRequest>()) }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.page.share.ShareTargetViewModelTest"`
Expected: FAIL

- [ ] **Step 3: Implement ShareTargetViewModel and ShareTargetBottomSheet**

Implement `ShareTargetViewModel.kt`:
```kotlin
package com.mckimquyen.reader.ui.page.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.sv.SystemReadLaterFeedManager
import com.mckimquyen.reader.infrastructure.share.ReadLaterWorker
import com.mckimquyen.reader.infrastructure.share.UrlExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID
import javax.inject.Inject

data class ShareTargetUiState(
    val targetUrl: String? = null,
    val title: String = "",
    val isLoading: Boolean = false,
    val savedArticleId: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class ShareTargetViewModel @Inject constructor(
    private val feedManager: SystemReadLaterFeedManager,
    private val articleDao: ArticleDao,
    private val workManager: WorkManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ShareTargetUiState())
    val uiState: StateFlow<ShareTargetUiState> = _uiState.asStateFlow()

    fun initFromShareText(text: String?) {
        val url = UrlExtractor.extractValidUrl(text)
        if (url == null) {
            _uiState.update { it.copy(errorMessage = "invalid_url") }
        } else {
            _uiState.update { it.copy(targetUrl = url, title = url) }
        }
    }

    fun saveArticle(accountId: Int, defaultTitle: String, onSaved: () -> Unit) {
        val url = _uiState.value.targetUrl ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val feed = feedManager.ensureReadLaterFeed(accountId, defaultTitle)
            val articleId = UUID.randomUUID().toString()
            val article = Article(
                id = articleId,
                date = Date(),
                title = _uiState.value.title.ifBlank { url },
                rawDescription = "",
                shortDescription = url,
                link = url,
                feedId = feed.id,
                accountId = accountId,
                isUnread = true,
                isReadLater = true
            )
            articleDao.insert(article)

            val workRequest = OneTimeWorkRequestBuilder<ReadLaterWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(
                    ReadLaterWorker.KEY_ARTICLE_ID to articleId,
                    ReadLaterWorker.KEY_URL to url,
                    ReadLaterWorker.KEY_TITLE to article.title
                ))
                .build()
            workManager.enqueue(workRequest)

            _uiState.update { it.copy(isLoading = false, savedArticleId = articleId) }
            onSaved()
        }
    }
}
```

Implement `ShareTargetBottomSheet.kt`:
```kotlin
package com.mckimquyen.reader.ui.page.share

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareTargetBottomSheet(
    url: String,
    isLoading: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Rounded.BookmarkAdd,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.read_later_feed_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = url,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading
                ) {
                    Text(text = stringResource(R.string.share_target_cancel_btn))
                }
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(text = stringResource(R.string.share_target_save_btn))
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.page.share.ShareTargetViewModelTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/page/share/ShareTargetViewModel.kt \
        app/src/main/java/com/mckimquyen/reader/ui/page/share/ShareTargetBottomSheet.kt \
        app/src/test/java/com/mckimquyen/reader/ui/page/share/ShareTargetViewModelTest.kt
git commit -m "feat(share): add ShareTargetViewModel and ShareTargetBottomSheet"
```

---

### Task 6: ShareTargetActivity & AndroidManifest Registration

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/share/ShareTargetActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/androidTest/java/com/mckimquyen/reader/ui/page/share/ShareTargetIntegrationTest.kt`

**Interfaces:**
- Consumes: `android.intent.action.SEND`, `ShareTargetViewModel`, `ShareTargetBottomSheet`
- Produces: Android component entry point for Share Sheet

- [ ] **Step 1: Write the failing integration test**

```kotlin
// app/src/androidTest/java/com/mckimquyen/reader/ui/page/share/ShareTargetIntegrationTest.kt
package com.mckimquyen.reader.ui.page.share

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareTargetIntegrationTest {
    @Test
    fun launchesActivityWithShareIntent() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Interesting read: https://example.com/test-article")
            setClass(ApplicationProvider.getApplicationContext(), ShareTargetActivity::class.java)
        }
        val scenario = ActivityScenario.launch<ShareTargetActivity>(intent)
        assertNotNull(scenario)
        scenario.close()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mckimquyen.reader.ui.page.share.ShareTargetIntegrationTest`
Expected: FAIL (Activity not declared or not found)

- [ ] **Step 3: Implement ShareTargetActivity and register in AndroidManifest.xml**

Implement `ShareTargetActivity.kt`:
```kotlin
package com.mckimquyen.reader.ui.page.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.mckimquyen.reader.R
import com.mckimquyen.reader.ui.ext.currentAccountId
import com.mckimquyen.reader.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ShareTargetActivity : ComponentActivity() {
    private val viewModel: ShareTargetViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            viewModel.initFromShareText(text)
        } else {
            finish()
            return
        }

        setContent {
            AppTheme {
                val state by viewModel.uiState.collectAsState()
                if (state.errorMessage != null) {
                    Toast.makeText(this, getString(R.string.share_target_invalid_url), Toast.LENGTH_SHORT).show()
                    finish()
                } else if (state.targetUrl != null) {
                    ShareTargetBottomSheet(
                        url = state.targetUrl ?: "",
                        isLoading = state.isLoading,
                        onSave = {
                            viewModel.saveArticle(
                                accountId = currentAccountId,
                                defaultTitle = getString(R.string.read_later_feed_title),
                                onSaved = {
                                    Toast.makeText(this, getString(R.string.share_target_saved_toast), Toast.LENGTH_SHORT).show()
                                    finish()
                                }
                            )
                        },
                        onCancel = { finish() }
                    )
                }
            }
        }
    }
}
```

Update `AndroidManifest.xml`:
```xml
        <activity
            android:name=".ui.page.share.ShareTargetActivity"
            android:exported="true"
            android:theme="@style/Theme.Reader.Transparent"
            android:excludeFromRecents="true"
            android:noHistory="true">
            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="text/plain" />
            </intent-filter>
        </activity>
```

- [ ] **Step 4: Run integration test to verify it passes**

Run: `./gradlew connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mckimquyen.reader.ui.page.share.ShareTargetIntegrationTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/page/share/ShareTargetActivity.kt \
        app/src/main/AndroidManifest.xml \
        app/src/androidTest/java/com/mckimquyen/reader/ui/page/share/ShareTargetIntegrationTest.kt
git commit -m "feat(share): register ShareTargetActivity in AndroidManifest and verify via integration test"
```

---

### Task 7: End-to-End Smoke Test & Documentation

**Files:**
- Modify: `doc/task/todo/INGEST-02_android-share-target-read-later.md` -> Move to `doc/task/done/INGEST-02_android-share-target-read-later_DONE.md`
- Modify: `doc/feature.md`

- [ ] **Step 1: Install and smoke test live on emulator-5554**

Run:
```bash
./gradlew installDevDebug
adb -s emulator-5554 shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "https://example.com/test-read-later" -n com.mckimquyen.reader.dev/com.mckimquyen.reader.ui.page.share.ShareTargetActivity
```
Verify:
1. ModalBottomSheet shows URL and buttons.
2. Tap "Lưu vào Đọc sau" (Save).
3. Toast "Đã lưu vào Đọc sau" shows up, Sheet closes.
4. Launch app, go to Feeds tab -> System Feed "Đọc sau" exists with the article.

- [ ] **Step 2: Run all regression tests and lint**

Run:
```bash
./gradlew testDevDebugUnitTest
./gradlew lintDevDebug
```
Expected: All tests pass, 0 lint errors.

- [ ] **Step 3: Update documentation and finish task**

Update `doc/feature.md`: Move INGEST-02 to `✅ Implemented`.
Move task doc to `doc/task/done/INGEST-02_android-share-target-read-later_DONE.md` with completion audit score.

- [ ] **Step 4: Commit and push**

```bash
git add doc/feature.md doc/task/
git commit -m "docs(ingest): complete INGEST-02 android share target"
git push origin dev
```
