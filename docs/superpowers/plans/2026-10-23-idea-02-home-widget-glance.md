# [IDEA-02] Home Screen Unread Articles Widget (RemoteViews) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Thêm widget màn hình chính Android bằng `RemoteViews` (không Glance, không dependency mới) hiển thị tối đa 5 bài chưa đọc mới nhất của tài khoản hiện tại, chạm bài để mở `ReadingPage` qua `ExtraName.ARTICLE_ID`, và có nút "Đã đọc" để đánh dấu đã đọc ngay từ widget.

**Architecture:** `ArticleWidgetProvider` (AppWidgetProvider) + `ArticleWidgetService` (RemoteViewsService/Factory) đọc 5 bài chưa đọc qua `ArticleDao` (FIX-17 `queryUnreadArticleList` hoặc `queryRecentArticleListWithFeed`) và `FeedDao` thông qua `@EntryPoint` `WidgetEntryPoint`. nút "Đã đọc" được xử lý ở trampoline activity (xem mục Thay đổi kiến trúc). Màu động Material You qua `@android:color/system_*` trên API 31+ và fallback tĩnh cho API 26–30. Chuỗi giao diện hỗ trợ đủ 38 locale.

**Thay đổi kiến trúc khi thực thi (2026-10-23, owner chọn):** Với `RemoteViews` danh sách, nút con trong hàng chỉ dùng được `fillInIntent` trên MỘT `PendingIntent` template cho cả list, nên không gắn `PendingIntent.getBroadcast` riêng từng hàng như bản đầu. Template là `PendingIntent.getActivity` tới `ArticleWidgetActionActivity` (trampoline trong suốt, `exported=false`, `excludeFromRecents`, `noHistory`, theme `Theme.Reader.Transparent` sẵn có). Mỗi view con gắn `setOnClickFillInIntent` với extra `EXTRA_ACTION` = `OPEN_ARTICLE` hoặc `MARK_READ` cùng `EXTRA_ARTICLE_ID`/`EXTRA_ACCOUNT_ID`. Activity: `OPEN_ARTICLE` → `MainActivity` với `ARTICLE_ID` + `NEW_TASK or CLEAR_TASK` rồi `finish()`; `MARK_READ` → đánh dấu đã đọc nền (`@ApplicationScope` scope qua `EntryPoint`, `Dispatchers.IO`), `ArticleWidgetUpdater.notifyDataChanged`, `finish()`. `ArticleWidgetActionActivity` bị loại khỏi kế hoạch.

**Tech Stack:** Kotlin, Android RemoteViews/AppWidget, Hilt EntryPointAccessors, Room DAO, Robolectric 4.14.1, JUnit4, MockK, Gradle Java 17.

## Global Constraints

- Spec đã duyệt: `docs/superpowers/specs/2026-10-23-idea-02-home-widget-glance-design.md`.
- Tuyệt đối không tự commit/push. Mỗi bước commit theo mẫu workflow được thay bằng review diff tại chỗ.
- Không thêm dependency Gradle mới (không thêm Glance, không đổi Compose/AndroidX version).
- Giữ device lock Pixel 7 Pro (`2B051FDH3006MU`); dừng ngay khi phát hiện quảng cáo toàn màn hình.
- 4 chuỗi mới (`widget_article_name`, `widget_article_desc`, `widget_article_empty`, `widget_article_mark_read`) phải có đủ trên cả 38 locale trong `res/values-*` (kiểm chứng bằng `StringResourceParityTest`).
- R5: Không hardcode magic numbers nghiệp vụ (dùng `const val WIDGET_MAX_ARTICLES = 5`), null-safety không `!!`, coroutine đóng/huỷ đúng cách, không block Main thread.
- Không đụng chạm các task ad/IAP/money và FIX-09. Không chạm `.idea/deviceManager.xml`.

## Files và trách nhiệm

- `app/src/main/res/values/strings.xml` + 37 file `values-*/strings.xml`: 4 chuỗi mới cho 38 locale.
- `app/src/main/res/xml/article_widget_info.xml`: Cấu hình AppWidget (4×2 cell, resize, preview, layout).
- `app/src/main/res/layout/widget_article_layout.xml`: Layout tổng của widget (container, header, ListView, empty view).
- `app/src/main/res/layout/widget_article_item.xml`: Layout từng hàng bài viết (tiêu đề, tên feed, nút Đã đọc).
- `app/src/main/res/values/colors_widget.xml` + `res/values-v31/colors_widget.xml` + các biến thể `-night`: Màu sắc Material You động/tĩnh.
- `app/src/main/java/com/mckimquyen/reader/ui/widget/WidgetEntryPoint.kt`: Hilt `@EntryPoint` cung cấp `ArticleDao` và `FeedDao`.
- `app/src/main/java/com/mckimquyen/reader/ui/widget/ArticleWidgetUpdater.kt`: Helper làm mới widget, gọi từ `SyncWorker` và action receiver.
- `app/src/main/java/com/mckimquyen/reader/ui/widget/ArticleWidgetActionActivity.kt`: Trampoline trong suốt xử lý chạm hàng (mở bài) và nút "Đã đọc".
- `app/src/main/java/com/mckimquyen/reader/ui/widget/ArticleWidgetService.kt`: RemoteViewsService + Factory nạp 5 bài chưa đọc và gắn fill-in intents.
- `app/src/main/java/com/mckimquyen/reader/ui/widget/ArticleWidgetProvider.kt`: AppWidgetProvider kết nối layout, adapter và click template.
- `app/src/main/AndroidManifest.xml`: Khai báo receiver provider, service (permission `BIND_REMOTEVIEWS`), và action receiver.
- `app/src/main/java/com/mckimquyen/reader/domain/sv/SyncWorker.kt`: Gọi `ArticleWidgetUpdater.notifyDataChanged` khi sync thành công.
- Tests trong `app/src/test/java/com/mckimquyen/reader/ui/widget/`:
  - `ArticleWidgetActionHandlerTest.kt`: Test logic xử lý hành động (mở bài / đánh dấu đã đọc) tách khỏi Activity.
  - `ArticleWidgetServiceTest.kt`: Test factory nạp 5 bài, map tiêu đề, feed name, và gắn fill-in intent.
  - `ArticleWidgetProviderTest.kt`: Test provider gán đúng RemoteViews adapter, empty view và pending intent.

---

### Task 1: Chuỗi đa ngôn ngữ (38 locale) và tài nguyên giao diện XML

**Files:**
- Modify: `app/src/main/res/values/strings.xml` + 37 thư mục `res/values-*/strings.xml`
- Create: `app/src/main/res/values/colors_widget.xml`
- Create: `app/src/main/res/values-v31/colors_widget.xml`
- Create: `app/src/main/res/values-night/colors_widget.xml`
- Create: `app/src/main/res/values-night-v31/colors_widget.xml`
- Create: `app/src/main/res/layout/widget_article_layout.xml`
- Create: `app/src/main/res/layout/widget_article_item.xml`
- Create: `app/src/main/res/xml/article_widget_info.xml`

- [x] **Step 1: Viết script Python bổ sung 4 chuỗi dịch cho 38 locale và chạy**

4 chuỗi:
- `widget_article_name`: Tiêu đề widget ("Unread articles" / "Bài viết chưa đọc")
- `widget_article_desc`: Mô tả widget ("Quickly glance at your latest unread articles." / "Xem nhanh danh sách bài viết chưa đọc.")
- `widget_article_empty`: Trạng thái rỗng ("All caught up! No unread articles." / "Đã đọc hết! Không có bài viết mới.")
- `widget_article_mark_read`: Nút hành động ("Mark as read" / "Đã đọc")

Script tự động đọc `app/src/main/res/values-*/strings.xml`, chèn 4 thẻ `<string>` tương ứng trước `</resources>`, giữ đúng format.

- [x] **Step 2: Chạy `StringResourceParityTest` để xác nhận cả 38 locale đồng bộ**

Chạy:
```bash
./gradlew testDevDebugUnitTest --tests '*StringResourceParityTest' --rerun
```
Kỳ vọng: **BUILD SUCCESSFUL**, parity test pass 100%, không thiếu key nào.

- [x] **Step 3: Tạo các file màu `colors_widget.xml` cho Dynamic Color và fallback**

- `values/colors_widget.xml`: Màu tĩnh trang nhã (fallback cho Android 8–11).
- `values-v31/colors_widget.xml`: `@android:color/system_neutral1_900`, `@android:color/system_accent1_100`... (Material You Android 12+).
- `values-night/` và `values-night-v31/`: Biến thể cho chế độ tối.

- [x] **Step 4: Tạo layouts `widget_article_layout.xml`, `widget_article_item.xml` và XML cấu hình `article_widget_info.xml`**

- `widget_article_layout.xml`: Gồm header nhỏ (icon app, tiêu đề `@string/widget_article_name`), `ListView` (`@id/widget_article_list`), và `TextView` rỗng (`@id/widget_empty_view`).
- `widget_article_item.xml`: Hàng bài viết gồm tiêu đề bài (`@id/widget_item_title`), tên feed (`@id/widget_item_feed`), và nút/icon ImageButton hoặc TextView "Đã đọc" (`@id/widget_item_mark_read`).
- `article_widget_info.xml`: Khai báo 4×2, resizeMode horizontal|vertical, initialLayout.

- [x] **Step 5: Kiểm tra biên dịch tài nguyên qua assembleDevDebug**

Chạy:
```bash
./gradlew assembleDevDebug
```
Kỳ vọng: **BUILD SUCCESSFUL**, toàn bộ tài nguyên XML hợp lệ.

---

### Task 2: Hilt EntryPoint, Action Handler/Activity và Widget Updater

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/ui/widget/WidgetEntryPoint.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/ui/widget/ArticleWidgetUpdater.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/ui/widget/ArticleWidgetActionHandler.kt` (logic thuần, test được)
- Create: `app/src/main/java/com/mckimquyen/reader/ui/widget/ArticleWidgetActionActivity.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/ui/widget/ArticleWidgetActionHandlerTest.kt`

- [x] **Step 1: Viết test failing (RED) cho `ArticleWidgetActionHandler`**

Logic tách khỏi Activity để test được (Activity không dựng được dễ trong JVM): `ArticleWidgetActionHandler(articleDao, notifyWidget: () -> Unit)` có `suspend fun markRead(accountId: Int, articleId: String)` và hàm thuần `fun parse(action: String?, articleId: String?, accountId: Int): WidgetAction?` (null khi thiếu id/action lạ).

Tạo `ArticleWidgetActionHandlerTest.kt`:
- `markRead(1, "art-1")` gọi `articleDao.markAsReadByArticleId(1, "art-1", isUnread = false)` đúng 1 lần rồi gọi `notifyWidget` sau đó (dùng `coVerifyOrder`).
- `parse` trả `null` khi `articleId` rỗng/null hoặc action không hợp lệ; trả `OpenArticle`/`MarkRead` đúng với dữ liệu hợp lệ.

Chạy test:
```bash
./gradlew testDevDebugUnitTest --tests '*ArticleWidgetActionHandlerTest' --rerun
```
Kỳ vọng: **FAIL** (chưa có class implementation).

- [x] **Step 2: Tạo `WidgetEntryPoint.kt`** (bản cuối thêm `@ApplicationScope fun applicationScope(): CoroutineScope`; `ArticleDao`/`FeedDao` giữ nguyên)

```kotlin
package com.mckimquyen.reader.ui.widget

import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.FeedDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun articleDao(): ArticleDao
    fun feedDao(): FeedDao
}
```

- [x] **Step 3: Tạo `ArticleWidgetUpdater.kt`**

```kotlin
package com.mckimquyen.reader.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.mckimquyen.reader.R

object ArticleWidgetUpdater {
    fun notifyDataChanged(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
        val componentName = ComponentName(context, ArticleWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (appWidgetIds.isNotEmpty()) {
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_article_list)
        }
    }
}
```

- [x] **Step 4: Tạo `ArticleWidgetActionHandler.kt` và `ArticleWidgetActionActivity.kt`**

- `ArticleWidgetActionHandler` như Step 1 (suspend `markRead` ghi DAO rồi `notifyWidget()`).
- `ArticleWidgetActionActivity : ComponentActivity` (KHÔNG `setContent`, không UI):
  - Hằng số `EXTRA_ACTION`, `EXTRA_ARTICLE_ID`, `EXTRA_ACCOUNT_ID`, `ACTION_OPEN_ARTICLE`, `ACTION_MARK_READ` trong `companion object`.
  - `onCreate`: `parse(...)`; null → `finish()`.
  - `OpenArticle` → `startActivity(Intent(this, MainActivity::class.java).putExtra(ExtraName.ARTICLE_ID, id).addFlags(NEW_TASK or CLEAR_TASK))`, `finish()`.
  - `MarkRead` → lấy `ArticleDao` và `@ApplicationScope CoroutineScope` qua `EntryPointAccessors.fromApplication(...)` (thêm `fun applicationScope()` vào `WidgetEntryPoint` với `@ApplicationScope`), `scope.launch(Dispatchers.IO) { handler.markRead(...) }`, rồi `finish()` ngay (việc ghi chạy trong application scope, không phụ thuộc vòng đời Activity).

- [x] **Step 5: Chạy test xác nhận GREEN**

Chạy:
```bash
./gradlew testDevDebugUnitTest --tests '*ArticleWidgetActionHandlerTest' --rerun
```
Kỳ vọng: **BUILD SUCCESSFUL**, test pass 1/1.

---

### Task 3: RemoteViewsService và Factory nạp bài chưa đọc

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/ui/widget/ArticleWidgetService.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/ui/widget/ArticleWidgetServiceTest.kt`

- [x] **Step 1: Viết test failing (RED) cho `ArticleWidgetFactory`**

Tạo `ArticleWidgetServiceTest.kt`:
- Mock `ArticleDao` trả về 5 bài chưa đọc (`ArticleListItem`).
- Mock `FeedDao` trả về tên feed tương ứng.
- Gọi `factory.onDataSetChanged()`.
- Assert `factory.count == 5`.
- Assert `factory.getViewAt(0)` trả về `RemoteViews` chứa đúng tiêu đề, tên feed và có hai fillInIntent mang `EXTRA_ACTION` đúng (`OPEN_ARTICLE`/`MARK_READ`) và `EXTRA_ARTICLE_ID`.

Chạy test:
```bash
./gradlew testDevDebugUnitTest --tests '*ArticleWidgetServiceTest' --rerun
```
Kỳ vọng: **FAIL** (chưa có class implementation).

- [x] **Step 2: Tạo `ArticleWidgetService.kt` và `ArticleWidgetFactory`**

- `ArticleWidgetService`: kế thừa `RemoteViewsService`, trả về `ArticleWidgetFactory(applicationContext, intent)`.
- `ArticleWidgetFactory`:
  - `const val WIDGET_MAX_ARTICLES = 5`.
  - Trong `onDataSetChanged()`:
    - Lấy `currentAccountId` từ `context.currentAccountId`.
    - Gọi `articleDao.queryUnreadArticleList(accountId, limit = WIDGET_MAX_ARTICLES)` qua `runBlocking(Dispatchers.IO)`.
    - Tải tên feed cho các bài qua `feedDao.queryById(it.feedId)`.
  - Trong `getViewAt(position)`:
    - Lấy bài tại `position`.
    - Dựng `RemoteViews(context.packageName, R.layout.widget_article_item)`.
    - `setTextViewText(R.id.widget_item_title, article.title)`.
    - `setTextViewText(R.id.widget_item_feed, feedName)`.
    - Gắn hai `fillInIntent` (cùng một template `getActivity` do provider đặt):
      ```kotlin
      fun fill(action: String) = Intent().apply {
          putExtra(ArticleWidgetActionActivity.EXTRA_ACTION, action)
          putExtra(ArticleWidgetActionActivity.EXTRA_ARTICLE_ID, article.id)
          putExtra(ArticleWidgetActionActivity.EXTRA_ACCOUNT_ID, article.accountId)
      }
      setOnClickFillInIntent(R.id.widget_item_container, fill(ArticleWidgetActionActivity.ACTION_OPEN_ARTICLE))
      setOnClickFillInIntent(R.id.widget_item_mark_read, fill(ArticleWidgetActionActivity.ACTION_MARK_READ))
      ```
      Không dùng `setOnClickPendingIntent` cho view trong hàng (không hoạt động trong collection).

- [x] **Step 3: Chạy test xác nhận GREEN**

Chạy:
```bash
./gradlew testDevDebugUnitTest --tests '*ArticleWidgetServiceTest' --rerun
```
Kỳ vọng: **BUILD SUCCESSFUL**, test pass 1/1.

---

### Task 4: ArticleWidgetProvider, Manifest và Hook SyncWorker

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/ui/widget/ArticleWidgetProvider.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/sv/SyncWorker.kt:63`
- Create: `app/src/test/java/com/mckimquyen/reader/ui/widget/ArticleWidgetProviderTest.kt`

- [x] **Step 1: Viết test failing (RED) cho `ArticleWidgetProvider`**

Tạo `ArticleWidgetProviderTest.kt`:
- Gọi `provider.onUpdate(context, appWidgetManager, appWidgetIds)`.
- Assert `RemoteViews` được gán adapter trỏ tới `ArticleWidgetService`.
- Assert template là `PendingIntent` activity trỏ tới `ArticleWidgetActionActivity` được gán cho ListView.
- Assert empty view được liên kết (`setEmptyView`).

Chạy test:
```bash
./gradlew testDevDebugUnitTest --tests '*ArticleWidgetProviderTest' --rerun
```
Kỳ vọng: **FAIL** (chưa có class implementation).

- [x] **Step 2: Tạo `ArticleWidgetProvider.kt`**

- Kế thừa `AppWidgetProvider`.
- Trong `onUpdate`:
  - Lặp qua từng `appWidgetId`:
    - Tạo `RemoteViews(context.packageName, R.layout.widget_article_layout)`.
    - Đặt adapter: `setRemoteAdapter(R.id.widget_article_list, Intent(context, ArticleWidgetService::class.java))`.
    - Đặt empty view: `setEmptyView(R.id.widget_article_list, R.id.widget_empty_view)`.
    - Tạo PendingIntent template tới trampoline (Activity này tự mở `MainActivity` với `NEW_TASK or CLEAR_TASK` theo quyết định của owner, tránh giới hạn `HomeEntry` chỉ đọc extras lúc khởi tạo):
      ```kotlin
      val template = PendingIntent.getActivity(
          context, 0,
          Intent(context, ArticleWidgetActionActivity::class.java),
          PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
      )
      setPendingIntentTemplate(R.id.widget_article_list, template)
      ```
      Template PHẢI mutable (`FLAG_MUTABLE`) để hệ thống gộp `fillInIntent`; ghi chú lý do trong code. Chỉ Activity nội bộ `exported=false` nhận intent nên chấp nhận được.
    - Header click: chạm tiêu đề widget mở MainActivity (trang chủ).
    - `appWidgetManager.updateAppWidget(appWidgetId, views)`.

- [x] **Step 3: Khai báo Manifest (`AndroidManifest.xml`)**

Khai báo 3 component mới (provider, service, trampoline activity):
- `ArticleWidgetProvider`: `<receiver android:name=".ui.widget.ArticleWidgetProvider" android:exported="true">` với intent-filter `APPWIDGET_UPDATE` và meta-data `article_widget_info`.
- `ArticleWidgetService`: `<service android:name=".ui.widget.ArticleWidgetService" android:permission="android.permission.BIND_REMOTEVIEWS" android:exported="false" />`.
- `ArticleWidgetActionActivity`: `<activity android:name=".ui.widget.ArticleWidgetActionActivity" android:exported="false" android:excludeFromRecents="true" android:noHistory="true" android:theme="@style/Theme.Reader.Transparent" />`.

- [x] **Step 4: Hook làm mới widget trong `SyncWorker.kt`**

Tại dòng ~63 trong `SyncWorker.kt` (sau khi đồng bộ bài mới thành công):
```kotlin
ArticleWidgetUpdater.notifyDataChanged(context)
```

- [x] **Step 4b: Rủi ro đã xác minh — mở bài khi app đang chạy nền (BẮT BUỘC xử lý, không giả định)**

Đọc mã nguồn cho thấy luồng mở bài từ intent ngoài **chỉ hoạt động ở lần khởi tạo Compose đầu tiên**:
- `HomeEntry.kt:92-94`: `openArticleId` được khởi tạo một lần từ `findActivity()?.intent?.extras?.getString(ExtraName.ARTICLE_ID)` trong `rememberSaveable`, sau đó `intent?.replaceExtras(null)` xoá extras.
- `MainActivity.onNewIntent` (dòng 84-87) chỉ gọi `setIntent(intent)`; `HomeEntry` **không** quan sát intent mới.
- Hệ quả: nếu app đang chạy nền (process còn sống) khi người dùng chạm bài trên widget, `HomeEntry` sẽ không đọc lại `ARTICLE_ID` nên **không mở đúng bài**. Luồng thông báo hiện có dùng cùng cơ chế nên có thể cùng giới hạn; chưa được kiểm chứng.

**Quyết định của owner (2026-10-23): giữ scope nhỏ, dùng `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK`; chấp nhận reset back stack về flow rồi mở bài. Không sửa `HomeEntry`/`MainActivity`.**

Việc cần làm trong task này, theo thứ tự:
1. Viết test hoặc đoạn kiểm tra tái hiện: widget `PendingIntent` template dùng `Intent(context, MainActivity::class.java)` với `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK` để buộc Activity khởi tạo lại và đọc extras từ đầu (hướng tối thiểu, không đổi `HomeEntry`/`MainActivity`). Ghi rõ đây là giải pháp tránh sửa luồng điều hướng chung.
2. Smoke trên Pixel (Task 5) PHẢI kiểm cả hai trường hợp: app đã bị kill hoàn toàn và app đang ở nền; ghi lại kết quả thực tế từng trường hợp. Nếu `CLEAR_TASK` làm mất trạng thái điều hướng không chấp nhận được hoặc vẫn không mở đúng bài, dừng và báo người dùng quyết định (ví dụ sửa `HomeEntry` để quan sát `onNewIntent`) thay vì tự mở rộng phạm vi.
3. Không dùng `FLAG_ACTIVITY_CLEAR_TOP` một mình như bản nháp trước vì không đảm bảo mở lại extras.

Đồng thời, đổi PendingIntent template trong Step 2 sang cờ ở mục (1) và ghi chú trong code rằng đây là đường tránh giới hạn của `HomeEntry`.

- [x] **Step 5: Chạy test xác nhận GREEN**

Chạy:
```bash
./gradlew testDevDebugUnitTest --tests '*ArticleWidgetProviderTest' --rerun
```
Kỳ vọng: **BUILD SUCCESSFUL**, test pass 1/1.

---

### Task 5: Kiểm chứng toàn diện, Smoke test Pixel 7 Pro và Tài liệu

**Files:**
- Modify: `doc/feature.md`
- Create: `doc/task/done/IDEA-02_home-widget-glance_DONE.md`
- Delete: `doc/task/todo/IDEA-02_home-widget-glance.md`

- [x] **Step 1: Chạy parity test 38 locale**

```bash
./gradlew testDevDebugUnitTest --tests '*StringResourceParityTest' --rerun
```
Kỳ vọng: **BUILD SUCCESSFUL**, pass 100%.

- [x] **Step 2: Chạy toàn bộ suite unit test với `--rerun`**

```bash
./gradlew testDevDebugUnitTest --rerun
```
Kết quả chạy thật: **1275 unit tests**, 0 failures/errors/skipped (đếm XML mới, 216 class; không dùng `JAVA_TOOL_OPTIONS`). Trong đó có 21 test widget mới: ActionHandler 5, PinHelper 4, Provider 4, Service 3, notifier (RSS 3 + TTS 1 + Home cluster 1) và locale parity 6 là suite riêng, nên không cộng nhầm parity (đã tính trong full XML: xác nhận bằng XML class).

- [x] **Step 3: Chạy build APK và compile androidTest**

```bash
./gradlew assembleDevDebug compileDevDebugAndroidTestKotlin
```
Kỳ vọng: **BUILD SUCCESSFUL**.

- [x] **Step 4: Chạy `lintDevDebug` kiểm tra 0 lỗi mới**

```bash
./gradlew lintDevDebug
```
Kỳ vọng: **BUILD SUCCESSFUL**, 0 errors (các cảnh báo cũ không tăng thêm lỗi).

- [x] **Step 5: Smoke test thực tế trên Pixel 7 Pro (`2B051FDH3006MU`) — đã thực hiện trước bổ sung WidgetRefreshNotifier; notifier mới chỉ host-tested**

- Cài đặt bản dev debug mới nhất lên Pixel (`./gradlew installDevDebug`).
- Mở app RSS Cat Hub, xác nhận bài viết chưa đọc đang có trong feed.
- Kiểm tra widget component khả dụng qua shell/dumpsys:
  ```bash
  adb -s 2B051FDH3006MU shell dumpsys appwidget
  ```
- Ghim widget ra màn hình chính (yêu cầu người dùng hỗ trợ ghim hoặc gửi broadcast add widget nếu launcher hỗ trợ).
- Chụp màn hình (dừng ngay nếu có quảng cáo toàn màn hình).
- Thử nghiệm:
  1. Danh sách bài chưa đọc hiển thị trên widget.
  2. Bấm vào bài → mở đúng bài trong `ReadingPage`.
  3. Bấm nút "Đã đọc" trên widget → bài biến mất khỏi danh sách widget, DB cập nhật `isUnread = false`.
- Dọn dẹp sau smoke nếu có bài test.

- [x] **Step 6: Hoàn tất báo cáo và cập nhật `doc/feature.md`**

- Tạo `doc/task/done/IDEA-02_home-widget-glance_DONE.md`: hoàn tất; ghi host evidence, smoke và giới hạn notifier.
- Cập nhật `doc/feature.md`: hoàn tất; IDEA-02 nằm trong `## ✅ Recently implemented`, epic row chuyển `✅ Done`.
- Xoá `doc/task/todo/IDEA-02_home-widget-glance.md`: hoàn tất; không tạo duplicate archive file.
- Kiểm tra diff cuối: `git diff --check` pass; `.idea/deviceManager.xml` không bị chạm. Không commit/push.

---

## Tự kiểm plan

- **Độ chính xác kỹ thuật:** Sử dụng RemoteViews chuẩn, `minSdk 26` tương thích, không thêm dependency.
- **Data flow:** `Context.currentAccountId` → `ArticleDao.queryUnreadArticleList` + `FeedDao.queryById` → RemoteViews.
- **Click flow:** Template `getActivity` + fillInIntent tới trampoline, sau đó mở `MainActivity` với `ExtraName.ARTICLE_ID` (khớp `HomeEntry.kt:93`). Nút "Đã đọc" dùng fillInIntent tới `ArticleWidgetActionActivity`, ghi DB trong `@ApplicationScope` trên `Dispatchers.IO`.
- **Sync hook:** Tự động gọi `ArticleWidgetUpdater.notifyDataChanged` từ `SyncWorker`.
- **Ràng buộc:** 38 locale đầy đủ (Task 1), R5 tuân thủ, không commit/push tự động.
