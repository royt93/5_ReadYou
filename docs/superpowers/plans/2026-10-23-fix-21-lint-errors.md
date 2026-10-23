# [FIX-21] Sửa 2 lỗi `lintDevDebug` — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Loại bỏ hoàn toàn 2 lỗi lint (`MissingOnPlayFromSearch` tại `PodcastPlaybackService` và `ProduceStateDoesNotAssignValue` tại `SmartCollectionDetailPage`) trên lệnh `./gradlew lintDevDebug`. Giữ nguyên hành vi runtime.

**Architecture:** (1) Thêm `override fun onPlayFromSearch(query: String?, extras: Bundle?) = podcast.play()` vào `MediaSessionCompat.Callback` ẩn danh của `PodcastPlaybackService`. (2) Chuyển `produceState` trong `SmartCollectionDetailPage` sang `remember(collectionId) { viewModel.observeArticles(collectionId) }.collectAsState(initial = emptyList())`. (3) Thêm test đặc tả Compose + Robolectric cho `SmartCollectionDetailPage` trước khi sửa.

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX MediaSessionCompat, Robolectric (`@Config(sdk = [34])`), MockK, Android Lint, Gradle Java 17.

## Global Constraints

- Spec đã duyệt: `docs/superpowers/specs/2026-10-23-fix-21-lint-errors-design.md`.
- Tuyệt đối không tự commit/push. Mỗi bước commit theo mẫu workflow được thay bằng review diff tại chỗ.
- Không tạo git worktree, không dùng thiết bị thật/emulator. Giữ device lock Pixel 7 Pro cho các yêu cầu device riêng trong tương lai.
- Không sửa/tác động tới 2402 warnings của lint (chủ yếu là `Untranslatable` từ thư viện ngoài). Không đổi `build.gradle` để tắt lint hoặc thêm baseline.
- Tuyệt đối không dùng `@SuppressLint` để che lỗi.
- Hai thay đổi đều bảo toàn hành vi runtime; test hành vi sẽ **pass cả trước và sau khi sửa**. Tín hiệu RED hợp lệ duy nhất là chính lệnh `./gradlew lintDevDebug` (hiện tại fail với 2 errors). Báo cáo và plan không được gọi test hành vi là RED.
- Test mới cho `SmartCollectionDetailPage` phải chứng minh được: (a) hiển thị danh sách bài đúng, (b) cập nhật khi collection đổi, (c) không re-collect khi recompose.
- Không chạm `.idea/deviceManager.xml`.

## Files và trách nhiệm

- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/podcast/PodcastPlaybackService.kt:60-69` — override `onPlayFromSearch`.
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/collection/SmartCollectionDetailPage.kt:22-26,45-47` — đổi sang `remember` + `collectAsState`.
- Create: `app/src/test/java/com/mckimquyen/reader/ui/page/collection/SmartCollectionDetailPageTest.kt` — test đặc tả Compose + Robolectric cho detail page.
- Modify: `doc/feature.md`, tạo báo cáo `doc/task/done/FIX-21_lint-errors_DONE.md`.

---

### Task 1: Thêm test đặc tả cho SmartCollectionDetailPage

**Files:**
- Create: `app/src/test/java/com/mckimquyen/reader/ui/page/collection/SmartCollectionDetailPageTest.kt`

**Interfaces:**
- Consumes: `SmartCollectionDetailPage`, `SmartCollectionsViewModel`, `SmartCollectionDao.observeArticlesInCollection`, `ArticleWithFeed`, `Article`, `Feed`.
- Produces: Test harness chứng minh flow articles được hiển thị và không re-collect sau recompose.

- [x] **Step 1: Viết test đặc tả (chạy PASS trên mã hiện tại)**

Tạo file `app/src/test/java/com/mckimquyen/reader/ui/page/collection/SmartCollectionDetailPageTest.kt`:

```kotlin
package com.mckimquyen.reader.ui.page.collection

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.collection.SmartCollection
import com.mckimquyen.reader.domain.model.collection.SmartCollectionWithCount
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.SmartCollectionDao
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SmartCollectionDetailPageTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun sampleArticleWithFeed(id: String, title: String): ArticleWithFeed =
        ArticleWithFeed(
            article = Article(
                id = id,
                date = Date(),
                title = title,
                author = "Author",
                rawDescription = "raw",
                shortDescription = "short",
                fullContent = null,
                img = null,
                link = "https://example.com/$id",
                feedId = "feed-1",
                accountId = 0,
            ),
            feed = Feed(
                id = "feed-1",
                name = "Tech News",
                url = "https://example.com/feed",
                groupId = "group-1",
                accountId = 0,
            ),
        )

    @Test
    fun detailPage_emptyState_showsEmptyArticlesMessage() {
        val dao = mockk<SmartCollectionDao>()
        every { dao.observeCollectionsWithCount(any()) } returns MutableStateFlow(
            listOf(
                SmartCollectionWithCount(
                    collection = SmartCollection(id = "col-1", accountId = 0, name = "My AI", query = "AI"),
                    articleCount = 0,
                )
            )
        )
        every { dao.observeArticlesInCollection("col-1") } returns MutableStateFlow(emptyList())
        val viewModel = SmartCollectionsViewModel(app, dao, mockk(relaxed = true))

        composeTestRule.setContent {
            SmartCollectionDetailPage(
                navController = NavHostController(app),
                collectionId = "col-1",
                viewModel = viewModel,
            )
        }

        composeTestRule.onNodeWithText("My AI").assertIsDisplayed()
        composeTestRule.onNodeWithText("AI").assertIsDisplayed()
        val emptyMessage = app.getString(R.string.smart_collection_empty_articles)
        composeTestRule.onNodeWithText(emptyMessage).assertIsDisplayed()
    }

    @Test
    fun detailPage_populatedState_showsArticleTitlesAndFeedName() {
        val dao = mockk<SmartCollectionDao>()
        every { dao.observeCollectionsWithCount(any()) } returns MutableStateFlow(
            listOf(
                SmartCollectionWithCount(
                    collection = SmartCollection(id = "col-1", accountId = 0, name = "My AI", query = "AI"),
                    articleCount = 2,
                )
            )
        )
        every { dao.observeArticlesInCollection("col-1") } returns MutableStateFlow(
            listOf(
                sampleArticleWithFeed("art-1", "AI breakthrough announced"),
                sampleArticleWithFeed("art-2", "New chip architecture"),
            )
        )
        val viewModel = SmartCollectionsViewModel(app, dao, mockk(relaxed = true))

        composeTestRule.setContent {
            SmartCollectionDetailPage(
                navController = NavHostController(app),
                collectionId = "col-1",
                viewModel = viewModel,
            )
        }

        composeTestRule.onNodeWithText("AI breakthrough announced").assertIsDisplayed()
        composeTestRule.onNodeWithText("New chip architecture").assertIsDisplayed()
    }

    @Test
    fun detailPage_emissionRecomposesWithoutRecreatingObservedFlow() {
        val dao = mockk<SmartCollectionDao>()
        every { dao.observeCollectionsWithCount(any()) } returns MutableStateFlow(
            listOf(
                SmartCollectionWithCount(
                    collection = SmartCollection(id = "col-1", accountId = 0, name = "My AI", query = "AI"),
                    articleCount = 0,
                )
            )
        )
        val articlesFlow = MutableStateFlow(emptyList<ArticleWithFeed>())
        every { dao.observeArticlesInCollection("col-1") } answers {
            articlesFlow
        }
        val viewModel = SmartCollectionsViewModel(app, dao, mockk(relaxed = true))
        val emptyMessage = app.getString(R.string.smart_collection_empty_articles)

        composeTestRule.setContent {
            SmartCollectionDetailPage(
                navController = NavHostController(app),
                collectionId = "col-1",
                viewModel = viewModel,
            )
        }
        composeTestRule.onNodeWithText(emptyMessage).assertIsDisplayed()

        // Flow emission thay empty state bằng bài, gây recomposition thật của detail page.
        articlesFlow.value = listOf(sampleArticleWithFeed("art-1", "Observed update"))
        composeTestRule.waitUntil(timeoutMillis = UI_UPDATE_TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText("Observed update").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Observed update").assertIsDisplayed()

        // Nếu bỏ remember(collectionId), recomposition tạo Flow mới và gọi DAO lần hai.
        verify(exactly = 1) { dao.observeArticlesInCollection("col-1") }
    }

    private companion object {
        const val UI_UPDATE_TIMEOUT_MS = 5000L
    }
}
```

- [x] **Step 2: Chạy test trên mã hiện tại để xác nhận PASS**

Chạy:
```bash
./gradlew testDevDebugUnitTest --tests '*SmartCollectionDetailPageTest' --rerun
```

Kỳ vọng: **BUILD SUCCESSFUL**, 3 tests pass, 0 failures. Xác nhận test đặc tả hoạt động đúng trên mã gốc.

Lưu ý kiểm tra tính hiệu lực của test thứ ba (`doesNotReObserve...`): trên mã gốc `produceState(collectionId)` khởi chạy producer đúng một lần cho cùng key nên `verify(exactly = 1)` cũng pass; test này là **guard cho thay đổi mới** (nếu quên `remember`, `observeArticles` sẽ chạy lại mỗi recompose), không phải bằng chứng lỗi cũ. Sau Task 2 Step 3, chạy một **mutation kiểm tra tạm** (không commit): bỏ `remember(collectionId)` để gọi `viewModel.observeArticles(collectionId)` trực tiếp, chạy lại test thứ ba và xác nhận nó **FAIL** (`verify` phát hiện >1 lần gọi); sau đó khôi phục `remember` và chạy lại để xác nhận PASS. Nếu mutation không làm test fail, sửa test (ví dụ buộc recompose bằng cách đọc state trong chính composable được kiểm) trước khi tin vào guard này.

---

### Task 2: Sửa 2 lỗi trong mã nguồn

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/podcast/PodcastPlaybackService.kt:10,61-68`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/collection/SmartCollectionDetailPage.kt:22-26,45-47`

- [x] **Step 1: Sửa PodcastPlaybackService**

Trong `app/src/main/java/com/mckimquyen/reader/infrastructure/podcast/PodcastPlaybackService.kt`:

1. Thêm import `android.os.Bundle`:
```kotlin
import android.os.Bundle
```

2. Bổ sung `override fun onPlayFromSearch(query: String?, extras: Bundle?) = podcast.play()` vào Callback:

Trước:
```kotlin
        session = MediaSessionCompat(this, TAG).apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = podcast.play()
                override fun onPause() = podcast.pause()
                override fun onStop() = podcast.stop()
                override fun onFastForward() = podcast.skip(PodcastPlaybackController.SKIP_INTERVAL_SEC)
                override fun onRewind() = podcast.skip(-PodcastPlaybackController.SKIP_INTERVAL_SEC)
                override fun onSeekTo(pos: Long) = podcast.seekTo((pos / MILLIS_PER_SECOND).toInt())
            })
            isActive = true
        }
```

Sau:
```kotlin
        session = MediaSessionCompat(this, TAG).apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = podcast.play()
                override fun onPlayFromSearch(query: String?, extras: Bundle?) = podcast.play()
                override fun onPause() = podcast.pause()
                override fun onStop() = podcast.stop()
                override fun onFastForward() = podcast.skip(PodcastPlaybackController.SKIP_INTERVAL_SEC)
                override fun onRewind() = podcast.skip(-PodcastPlaybackController.SKIP_INTERVAL_SEC)
                override fun onSeekTo(pos: Long) = podcast.seekTo((pos / MILLIS_PER_SECOND).toInt())
            })
            isActive = true
        }
```

- [x] **Step 2: Sửa SmartCollectionDetailPage**

Trong `app/src/main/java/com/mckimquyen/reader/ui/page/collection/SmartCollectionDetailPage.kt`:

1. Bỏ import `produceState`, thêm `remember`:

Trước:
```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
```

Sau:
```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
```

2. Sửa dòng 45-47:

Trước:
```kotlin
    val collections by viewModel.collectionsWithCount.collectAsState()
    val collection = collections.firstOrNull { it.collection.id == collectionId }?.collection
    val articles by produceState(initialValue = emptyList(), collectionId) {
        viewModel.observeArticles(collectionId).collect { value = it }
    }
```

Sau:
```kotlin
    val collections by viewModel.collectionsWithCount.collectAsState()
    val collection = collections.firstOrNull { it.collection.id == collectionId }?.collection
    val articlesFlow = remember(collectionId) { viewModel.observeArticles(collectionId) }
    val articles by articlesFlow.collectAsState(initial = emptyList())
```

- [x] **Step 3: Chạy lại test đặc tả**

Chạy:
```bash
./gradlew testDevDebugUnitTest --tests '*SmartCollectionDetailPageTest' --rerun
```

Kỳ vọng: **BUILD SUCCESSFUL**, 3 tests pass, 0 failures.

---

### Task 3: Chạy lintDevDebug kiểm chứng 0 error, full tests và hoàn tất báo cáo

**Files:**
- Modify: `doc/feature.md`
- Create: `doc/task/done/FIX-21_lint-errors_DONE.md`

- [x] **Step 1: Chạy lintDevDebug để chứng minh 2 errors đã hết**

Chạy:
```bash
./gradlew lintDevDebug
```

Kỳ vọng: **BUILD SUCCESSFUL in ~2m**, exit code 0.
Kiểm tra báo cáo XML:
```bash
python3 - <<'PY'
import xml.etree.ElementTree as ET
r = ET.parse('app/build/reports/lint-results-devDebug.xml').getroot()
issues = r.findall('issue')
errors = [i for i in issues if i.get('severity') in ('Error', 'Fatal')]
print('total_issues=', len(issues))
print('errors_count=', len(errors))
for e in errors:
    print('REMAINING_ERROR=', e.get('id'), e.get('message'))
assert len(errors) == 0, f"Vẫn còn {len(errors)} lỗi lint"
print('LINT_VERIFICATION=PASSED_0_ERRORS')
PY
```

- [x] **Step 2: Chạy toàn bộ unit test với cờ `--rerun`**

```bash
./gradlew testDevDebugUnitTest --rerun
```

Kỳ vọng: **BUILD SUCCESSFUL**, tổng số test = 1254 (1251 cũ + 3 test mới), 0 failures/errors/skipped, không dùng `JAVA_TOOL_OPTIONS`. Đếm từ các file XML mới sinh.

- [x] **Step 3: Chạy build APK và compile androidTest**

```bash
./gradlew assembleDevDebug compileDevDebugAndroidTestKotlin
```

Kỳ vọng: **BUILD SUCCESSFUL**, cả hai task đều hoàn thành exit code 0.

- [x] **Step 4: Viết báo cáo `doc/task/done/FIX-21_lint-errors_DONE.md`**

Nội dung báo cáo:
- Tóm tắt: sửa 2 lỗi `lintDevDebug` gồm `MissingOnPlayFromSearch` và `ProduceStateDoesNotAssignValue`.
- Kết quả lint trước: 2 errors, 2402 warnings, BUILD FAILED.
- Kết quả lint sau: 0 errors, 2402 warnings (giữ nguyên, không sửa), BUILD SUCCESSFUL.
- Chi tiết sửa:
  - `PodcastPlaybackService`: override `onPlayFromSearch(query, extras)` gọi `podcast.play()`.
  - `SmartCollectionDetailPage`: chuyển `produceState` sang `remember(collectionId) { observeArticles }.collectAsState(emptyList())`.
- Test: `SmartCollectionDetailPageTest` 3/3 pass; full suite 1254 tests pass; assembleDevDebug và compileDevDebugAndroidTestKotlin pass.
- Giới hạn: 2402 warnings không đổi; lỗi đọc `api-versions.xml` của SDK 37 được ghi nhận; Android Auto voice search chưa có hardware/car smoke test; không commit/push.

- [x] **Step 5: Cập nhật `doc/feature.md`**

Chuyển mục FIX-21 vào `## ✅ Recently implemented`:
```markdown
- **FIX-21 — Sửa 2 lỗi lintDevDebug** (`doc/task/done/FIX-21_lint-errors_DONE.md`): sửa `MissingOnPlayFromSearch` trong `PodcastPlaybackService` (override `onPlayFromSearch` gọi `podcast.play()`) và `ProduceStateDoesNotAssignValue` trong `SmartCollectionDetailPage` (dùng `remember(collectionId)` + `collectAsState`). `lintDevDebug` chạy thật: 2 error → 0 error (2402 warnings giữ nguyên), BUILD SUCCESSFUL. 1254 unit tests pass (`--rerun`), `assembleDevDebug` và androidTest compile pass. Không dùng `@SuppressLint`. Chưa commit/push.
```

Dọn mục FIX-21 ở `## 📋 Picked`.

- [x] **Step 6: Kiểm tra diff cuối**

```bash
git diff --check
git status -sb
git diff --stat
```

Xác nhận diff sạch sẽ, đúng phạm vi, không chạm `.idea/deviceManager.xml`.

---

## Tự kiểm plan

- **Spec coverage:** Sửa `onPlayFromSearch` (Task 2 Step 1), sửa `produceState` với `remember` (Task 2 Step 2), test đặc tả Compose + recomposition guard (Task 1), chạy lint chứng minh 0 error (Task 3 Step 1), full suite/build (Task 3 Step 2-3), cập nhật báo cáo và index (Task 3 Step 4-5).
- **Placeholder scan:** Không có placeholder, việc chưa hoàn thành hoặc chuỗi tạm. Mọi đoạn code và command đều cụ thể, chạy được.
- **Type consistency:** Sử dụng đúng `MediaSessionCompat.Callback`, `Bundle`, `SmartCollectionDetailPage`, `remember`, `collectAsState`, `observeArticlesInCollection`.
- **Ràng buộc:** Không commit/push tự động, không chạm thiết bị, không tạo worktree, không dùng `@SuppressLint`.
