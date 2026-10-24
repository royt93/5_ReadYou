# [IDEA-02] Home Screen Unread Articles Widget (RemoteViews) — Design Spec

- **Ngày:** 2026-10-23
- **Trạng thái:** Đã thực thi Inline Execution và kiểm chứng (2026-10-24); notifier cuối đã host-tested, chưa retest device; chưa commit/push.
- **Quy tắc thực thi:** Không tự commit/push khi chưa được yêu cầu. Không thêm dependency ngoài danh sách hiện có trong `app/build.gradle`.

---

## 1. Bối cảnh và vấn đề thực tế

Độc giả muốn xem nhanh tiêu đề tin tức chưa đọc ngay từ màn hình chính (Android Home Launcher) mà không cần mở ứng dụng, và có thể mở bài đọc chỉ bằng 1 chạm hoặc đánh dấu đã đọc bài ngay trên widget.

Hiện tại repo `com.mckimquyen.reader` chưa có widget nào. Yêu cầu gốc nhắc tới Jetpack Glance, nhưng Glance yêu cầu các thư viện Compose/AndroidX mới hơn bản hiện tại (`1.2.0-beta02`, `core-ktx 1.7.0`, `material3 1.0.1`), có nguy cơ kéo nâng phiên bản dây chuyền và gây lỗi diện rộng. Vì vậy, người dùng đã duyệt phương án dùng **RemoteViews chuẩn của Android platform** kết hợp Material You qua tài nguyên màu động hệ thống (API 31+).

---

## 2. Mục tiêu và Tiêu chí chấp nhận (DoD)

1. **Hiển thị danh sách:** Widget kích thước khởi tạo 4×2 hoặc 4×3 (cho phép resize theo cả 2 chiều, tối thiểu 2×2), hiển thị danh sách cuộn tối đa `5` bài chưa đọc mới nhất (`queryUnreadArticleList` nhẹ từ FIX-17, không nạp `fullContent`/`rawDescription`).
2. **Mở bài đọc:** Chạm vào một bài trên widget sẽ mở trực tiếp bài đó trong `MainActivity` qua `ExtraName.ARTICLE_ID` (`HomeEntry.kt:93`), đồng nhất với luồng thông báo của app.
3. **Đánh dấu đã đọc:** Mỗi hàng bài viết có nút "Đã đọc" riêng; bấm nút sẽ gọi `markAsReadByArticleId(accountId, articleId, isUnread = false)` trên thread nền, tự động loại bài đó khỏi widget và cập nhật danh sách.
4. **Tự động cập nhật:** Widget tự cập nhật dữ liệu khi `SyncWorker` đồng bộ bài mới thành công, khi app đánh dấu đã đọc trong app (hoặc sau khi bấm nút trên widget).
5. **Dynamic Color (Material You):** Sử dụng các tài nguyên màu động hệ thống `@android:color/system_accent1_*` và `@android:color/system_neutral1_*` trên Android 12+ (API 31+); có fallback màu tĩnh trang nhã trên API 26–30. Hỗ trợ dark mode qua thư mục tài nguyên `-night`.
6. **Đa ngôn ngữ đầy đủ:** Mọi chuỗi mới phục vụ widget (`widget_name`, `widget_desc`, `widget_empty`, `widget_mark_read`) phải được bổ sung đủ trên cả **38 locale** được duy trì trong `app/src/main/res/values-*` (kiểm chứng bằng `StringResourceParityTest`).
7. **Bảo mật và an toàn luồng:** Mọi thao tác I/O với database (Room) chạy trên `Dispatchers.IO` (dùng `BroadcastReceiver.goAsync()` cho broadcast receiver); không block Main thread. `AppWidgetProvider` chỉ nhận broadcast hệ thống, `RemoteViewsService` đặt `exported="false"`.

---

## 3. Không thuộc phạm vi (Out of Scope)

- Không dùng Jetpack Glance hay thêm bất kỳ dependency Gradle mới nào.
- Không thêm nút đánh dấu sao hay toggle đọc sau trên widget (người dùng đã chọn tinh gọn: chỉ xem, mở bài và đánh dấu đã đọc).
- Không thêm màn hình cấu hình riêng cho từng instance widget (dùng chung `currentAccountId` từ DataStore của app).
- Không đụng chạm tới các task quảng cáo/IAP/money và FIX-09.

---

## 4. Kiến trúc và Thành phần

### 4.1. Gói mã nguồn
Toàn bộ mã mới đặt tại gói `app/src/main/java/com/mckimquyen/reader/ui/widget/`:

1. **`ArticleWidgetProvider.kt`** — Kế thừa `AppWidgetProvider`.
   - Lắng nghe broadcast hệ thống: `onUpdate`, `onReceive`.
   - Dựng `RemoteViews` tổng thể của widget (`widget_article_layout.xml`): gán tiêu đề widget, gán adapter danh sách trỏ tới `ArticleWidgetService`, gán empty view (`widget_empty_view`), và gán `PendingIntent` template (`PendingIntent.getActivity`) mở `MainActivity` với `ExtraName.ARTICLE_ID`.
   - Nhận custom action `ACTION_UPDATE_WIDGET` để kích hoạt `notifyAppWidgetViewDataChanged`.

2. **`ArticleWidgetService.kt`** — Kế thừa `RemoteViewsService`.
   - Trả về `ArticleWidgetFactory`.
   - `ArticleWidgetFactory`:
     - Truy cập `ArticleDao` và `currentAccountId` thông qua Hilt `@EntryPoint` (`WidgetEntryPoint`).
     - Trong `onDataSetChanged()`: gọi `articleDao.queryUnreadArticleList(accountId, limit = 5)` (chạy đồng bộ trong worker thread của factory, an toàn).
     - Trong `getViewAt(position)`: dựng `RemoteViews` cho từng hàng (`widget_article_item.xml`): gán tiêu đề bài, tên feed, thời gian tương đối; gắn `fillInIntent` cho chạm bài mở đọc; gắn `PendingIntent` (hoặc `fillInIntent` riêng) cho nút "Đã đọc".

3. **`ArticleWidgetActionReceiver.kt`** — Kế thừa `BroadcastReceiver`.
   - Xử lý action `ACTION_MARK_AS_READ`: lấy `articleId` và `accountId` từ intent, gọi `goAsync()` và chuyển sang coroutine `Dispatchers.IO` để gọi `articleDao.markAsReadByArticleId(accountId, articleId, isUnread = false)`.
   - Sau khi ghi DB, gọi `ArticleWidgetUpdater.notifyDataChanged(context)` để làm mới widget ngay lập tức.

4. **`ArticleWidgetUpdater.kt`** — Object tiện ích.
   - Hàm `notifyDataChanged(context: Context)`: gửi broadcast nội bộ hoặc gọi trực tiếp `AppWidgetManager.notifyAppWidgetViewDataChanged` cho toàn bộ widget ids của `ArticleWidgetProvider`.
   - Được gọi từ:
     - Cuối `SyncWorker.doWork()` khi sync thành công (dòng ~50).
     - `ArticleWidgetActionReceiver` sau khi đánh dấu đã đọc.

5. **`WidgetEntryPoint.kt`** — Interface `@EntryPoint` đặt trong `@InstallIn(SingletonComponent::class)`.
   - Khai báo cung cấp `ArticleDao` để `ArticleWidgetService` và `ArticleWidgetActionReceiver` lấy instance từ `ApplicationContext` qua `EntryPointAccessors`.

---

### 4.2. Khai báo Manifest (`AndroidManifest.xml`)

```xml
<receiver
    android:name=".ui.widget.ArticleWidgetProvider"
    android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/article_widget_info" />
</receiver>

<service
    android:name=".ui.widget.ArticleWidgetService"
    android:permission="android.permission.BIND_REMOTEVIEWS"
    android:exported="false" />

<receiver
    android:name=".ui.widget.ArticleWidgetActionReceiver"
    android:exported="false" />
```

---

### 4.3. Tài nguyên XML

1. **`app/src/main/res/xml/article_widget_info.xml`**:
   - `minWidth="250dp"`, `minHeight="110dp"`.
   - `targetCellWidth="4"`, `targetCellHeight="2"`.
   - `resizeMode="horizontal|vertical"`.
   - `updatePeriodMillis="0"` (chỉ cập nhật dựa trên sự kiện sync/action để tiết kiệm pin).
   - `previewLayout="@layout/widget_article_layout"`.
   - `initialLayout="@layout/widget_article_layout"`.

2. **Giao diện Layout (`res/layout/`)**:
   - `widget_article_layout.xml`: Container bo góc, thanh tiêu đề nhỏ (tên app + nút mở app), ListView (`widget_article_list`), và TextView rỗng (`widget_empty_view`).
   - `widget_article_item.xml`: Hàng bài viết gồm tiêu đề (tối đa 2 dòng, ellipsize), tên feed + thời gian (1 dòng), và nút icon/text "Đã đọc".

3. **Màu sắc Material You**:
   - Thư mục `res/values/colors_widget.xml`: Màu fallback tĩnh dựa trên theme app cho API 26–30.
   - Thư mục `res/values-v31/colors_widget.xml`: Màu động `@android:color/system_accent1_*` và `@android:color/system_neutral1_*`.
   - Thư mục `res/values-night/` và `res/values-night-v31/`: Biến thể nền tối tương ứng.

---

## 5. Danh sách chuỗi mới (38 locale)

4 chuỗi cần dịch và bổ sung vào `res/values-*/strings.xml`:
1. `widget_article_name`: "Unread articles" / "Bài viết chưa đọc"
2. `widget_article_desc`: "Quickly glance at your latest unread articles." / "Xem nhanh danh sách bài viết chưa đọc."
3. `widget_article_empty`: "All caught up! No unread articles." / "Đã đọc hết! Không có bài viết mới."
4. `widget_article_mark_read`: "Mark as read" / "Đã đọc"

Được kiểm tra nghiêm ngặt bằng `StringResourceParityTest` qua cả 38 locale.

---

## 6. Chiến lược kiểm thử (TDD & Verification)

1. **Unit tests (`app/src/test/java/.../ui/widget/`):**
   - `ArticleWidgetFactoryTest`: Giả lập `ArticleDao` trả về danh sách bài, kiểm tra số lượng mục, dữ liệu tiêu đề và fill-in intent được gán đúng.
   - `ArticleWidgetActionReceiverTest`: Giả lập broadcast đánh dấu đã đọc, kiểm tra gọi đúng `markAsReadByArticleId` trên DAO với `isUnread = false` và gọi lệnh refresh widget.
2. **Resource Parity test:**
   - Chạy `StringResourceParityTest` kiểm tra không thiếu chuỗi nào trong 38 locale.
3. **Full test suite:**
   - `./gradlew testDevDebugUnitTest --rerun` chứng minh toàn bộ unit test (cũ + mới) pass 100%, 0 failures.
4. **Build & Lint:**
   - `./gradlew assembleDevDebug compileDevDebugAndroidTestKotlin` thành công.
   - `./gradlew lintDevDebug` xác nhận không phát sinh thêm error mới nào (0 errors).
5. **Smoke test trên thiết bị thật (Pixel 7 Pro):**
   - Cài APK mới (`./gradlew installDevDebug`).
   - Mở app, xác nhận feed BBC có bài chưa đọc.
   - Ghim widget ra màn hình chính qua launcher hoặc lệnh kiểm tra component (dừng ngay nếu phát hiện quảng cáo che màn hình).
   - Kiểm tra: danh sách bài hiển thị trên widget, bấm vào bài mở đúng `ReadingPage`, bấm nút "Đã đọc" loại bài khỏi widget.
