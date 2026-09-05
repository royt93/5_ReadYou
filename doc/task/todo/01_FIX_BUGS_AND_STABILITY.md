# 🛠️ Epic 01: Sửa Lỗi & Củng Cố Độ Ổn Định (Fix Bugs & Stability)

> **Mục tiêu Epic:** Loại bỏ hoàn toàn các nguy cơ crash ngầm, lỗi memory leak, tối ưu tốc độ truy vấn SQLite/Room và tuân thủ chặt chẽ chính sách Google Play Store.

---

### [FIX-01] Composite Indexes trên bảng `article` trong Room Database
- **Type:** Bug / Performance Defect
- **Priority:** `P0 (Blocker)`
- **Estimation:** `3 Story Points`
- **Location:** [`domain/model/article/Article.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/domain/model/article/Article.kt), [`infrastructure/db/AndroidDb.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/db/AndroidDb.kt)
- **Vấn đề thực tế:** Bảng `article` hiện chỉ có index đơn trên `feedId` và `accountId`. Hầu hết các câu query trong `ArticleDao` đều lọc theo `accountId + isUnread + date` hoặc `accountId + feedId + isUnread + date`. Khi số lượng bài báo vượt quá 5,000+, SQLite buộc phải scan và sort bằng temporary table, gây lag giật khung hình ở `FlowPage` và tốn pin.
- **User Story:**
  > Là người dùng đọc tin tức,  
  > Tôi muốn danh sách bài viết hiển thị tức thì không bị giật lag khi cuộn,  
  > Để tôi có trải nghiệm mượt mà ngay cả khi có hàng nghìn bài báo đã lưu.
- **Acceptance Criteria (Gherkin):**
  - **Given** database phiên bản 6 đang hoạt động
  - **When** app nâng cấp lên database phiên bản 7
  - **Then** migration `MIGRATION_6_7` được kích hoạt tạo các index:
    - `index_article_account_unread_date` trên `(accountId, isUnread, date DESC)`
    - `index_article_account_feed_unread_date` trên `(accountId, feedId, isUnread, date DESC)`
    - `index_article_account_starred_date` trên `(accountId, isStarred, date DESC)`
  - **And** các query PagingSource trong `ArticleDao` đạt tốc độ < 10ms trên tập dữ liệu 20,000 bài.

---

### [FIX-02] Loại bỏ Compose `LazyListState` khỏi ViewModel & UiState
- **Type:** Architecture Defect / Memory Leak Risk
- **Priority:** `P0 (Blocker)`
- **Estimation:** `3 Story Points`
- **Location:** [`ui/page/home/read/ReadingViewModel.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModel.kt#L74), [`ui/page/home/flow/FlowViewModel.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/page/home/flow/FlowViewModel.kt#L55)
- **Vấn đề thực tế:** `ReadingUiState` và `FlowUiState` đều chứa `LazyListState = LazyListState()`. Tại dòng 74 của `ReadingViewModel` có ghi nhận lỗi `NullPointerException: LayoutNode.getNeedsOnPositionedDispatch$ui_release()`. Việc lưu `LazyListState` trong ViewModel vi phạm kiến trúc Compose, giữ tham chiếu LayoutNode cũ khi Activity xoay màn hình.
- **User Story:**
  > Là nhà phát triển,  
  > Tôi muốn ViewModel chỉ chứa pure data state và tách riêng Compose UI State,  
  > Để loại bỏ nguy cơ crash NullPointerException khi xoay màn hình hoặc chuyển tab.
- **Acceptance Criteria:**
  - **Given** người dùng đang đọc bài báo hoặc cuộn feed
  - **When** người dùng xoay màn hình hoặc chuyển app ra background rồi quay lại
  - **Then** `LazyListState` được khởi tạo bằng `rememberLazyListState()` trong Composable
  - **And** sự kiện `scrollToTop` được điều khiển thông qua `SharedFlow<Unit>` một chiều từ ViewModel.

---

### [FIX-03] Bọc Error Isolation và điều chỉnh Concurrency khi đồng bộ RSS Feed
- **Type:** Crash / Reliability Bug
- **Priority:** `P0 (Blocker)`
- **Estimation:** `3 Story Points`
- **Location:** [`domain/sv/AbstractRssRepository.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/domain/sv/AbstractRssRepository.kt#L83-L96)
- **Vấn đề thực tế:** `AbstractRssRepository.sync()` chia nhóm `chunked(16)` và chạy 16 coroutine song song. Nếu chỉ 1 feed bị lỗi (500, SSL Handshake, Timeout, XML lỗi định dạng), `awaitAll()` ném exception làm hủy toàn bộ 15 feed còn lại và làm `SyncWorker` thất bại giữa chừng. Đồng thời 16 kết nối đồng thời dễ bị web server chặn IP (Rate Limit 429).
- **User Story:**
  > Là người dùng đã đăng ký 50+ nguồn tin,  
  > Tôi muốn quá trình đồng bộ tiếp tục hoàn tất các nguồn bình thường kể cả khi có 1-2 website nguồn bị sập,  
  > Để tôi không bị gián đoạn đọc tin tức.
- **Acceptance Criteria:**
  - **Given** người dùng kích hoạt đồng bộ nền
  - **When** một nguồn tin trả về HTTP 500 hoặc rớt mạng
  - **Then** hàm `syncFeed(feed)` bắt gọn lỗi bằng `runCatching`, ghi log cảnh báo và trả về `Result.failure`
  - **And** các feed khác trong danh sách vẫn được tải và lưu vào Room bình thường
  - **And** giảm chunk concurrency xuống 6 kết nối song song để tránh bị cloudflare/server chặn.

---

### [FIX-04] Thay thế Endpoint Favicon Heroku đã chết & Sửa lỗi `NoSuchElementException`
- **Type:** Bug / Privacy & Network Defect
- **Priority:** `P0 (Blocker)`
- **Estimation:** `2 Story Points`
- **Location:** [`infrastructure/rss/RssHelper.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/rss/RssHelper.kt#L154-L160)
- **Vấn đề thực tế:** `RssHelper.queryRssIcon()` gọi `https://besticon-demo.herokuapp.com/allicons.json`. Heroku đã hủy gói miễn phí từ lâu, endpoint này chập chờn hoặc không phản hồi. Đồng thời code gọi `favicon?.icons?.first { it.width >= 20 }` ném `NoSuchElementException` khi website không có icon thỏa mãn.
- **User Story:**
  > Là người dùng thêm một nguồn RSS mới,  
  > Tôi muốn icon đại diện của website luôn hiển thị đẹp mắt và tải nhanh,  
  > Không làm chậm quá trình thêm nguồn hay gây crash.
- **Acceptance Criteria:**
  - **Given** người dùng subscribe một feed mới
  - **When** app truy vấn icon favicon
  - **Then** app sử dụng chuỗi fallback:
    1. Parse trực tiếp thẻ `<link rel="icon">` hoặc `<link rel="apple-touch-icon">` từ trang chủ
    2. Fallback sang Google Favicon Service: `https://www.google.com/s2/favicons?domain={host}&sz=128`
    3. Fallback sang DuckDuckGo Favicon Service
  - **And** dùng `firstOrNull` thay vì `first` để tránh `NoSuchElementException`.

---

### [FIX-05] Đẩy tác vụ parse HTML của AI Summary và TTS sang Worker Thread
- **Type:** Performance / ANR Prevention
- **Priority:** `P1 (High)`
- **Estimation:** `1 Story Point`
- **Location:** [`ui/page/home/read/ReadingViewModel.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModel.kt#L137), [`ReadingViewModel.kt#L173`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModel.kt#L173)
- **Vấn đề thực tế:** Trước khi coroutine được launch, `HtmlCompat.fromHtml(...)` được gọi đồng bộ ngay trên Main thread. Với bài báo dài nhiều HTML tags, việc parse trên Main thread làm đơ giao diện (jank / micro-freeze).
- **User Story:**
  > Là người dùng bấm nút ✨ Tóm tắt AI hoặc 🎧 Nghe bài báo,  
  > Tôi muốn ứng dụng phản hồi ngay lập tức không bị đơ giật giao diện.
- **Acceptance Criteria:**
  - **Given** người dùng mở một bài báo dài 10,000 từ
  - **When** người dùng ấn Tóm tắt hoặc Nghe đọc
  - **Then** việc làm sạch HTML thành plain-text chạy hoàn toàn trên `Dispatchers.Default`
  - **And** Main thread giữ tốc độ render 60/120 FPS ổn định.

---

### [FIX-06] Loại bỏ Force-Null `!!` gây rủi ro NPE ở `FeverRssSv` và `OpmlSv`
- **Type:** Bug / Crash Prevention
- **Priority:** `P1 (High)`
- **Estimation:** `2 Story Points`
- **Location:** [`domain/sv/FeverRssSv.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/domain/sv/FeverRssSv.kt#L65), [`domain/sv/OpmlSv.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/domain/sv/OpmlSv.kt#L41)
- **Vấn đề thực tế:** Code gọi `accountDao.queryById(...)!!` và `groupDao.queryById(...)!!`. Nếu tài khoản bị xóa giữa chừng hoặc database đang khởi tạo, app sẽ crash ngay lập tức.
- **Acceptance Criteria:**
  - **Given** database chưa kịp tải hoặc account bị xóa
  - **When** các phương thức của `FeverRssSv` hoặc `OpmlSv` được thực thi
  - **Then** code sử dụng safe call `?.` kết hợp `throw IllegalStateException("Account not found")` có bọc xử lý lỗi thân thiện.

---

### [FIX-07] Kiểm tra Runtime Permission `POST_NOTIFICATIONS` trên Android 13+ (API 33+)
- **Type:** Compatibility / OS Standard
- **Priority:** `P1 (High)`
- **Estimation:** `2 Story Points`
- **Location:** [`infrastructure/android/NotificationHelper.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/android/NotificationHelper.kt)
- **Vấn đề thực tế:** Từ Android 13, việc gọi `notify()` mà chưa được người dùng cấp quyền `android.permission.POST_NOTIFICATIONS` sẽ bị hệ thống âm thầm chặn. Đồng thời mã hiện tại dùng `Random().nextInt() + article.id.hashCode()` tạo ID ngẫu nhiên không quản lý được.
- **Acceptance Criteria:**
  - **Given** thiết bị chạy Android 13 trở lên
  - **When** người dùng bật thông báo cho một feed trong Settings
  - **Then** app hiển thị hộp thoại xin quyền `POST_NOTIFICATIONS` theo chuẩn Material 3
  - **And** notification ID được sinh theo mã hash cố định của bài viết để có thể cập nhật hoặc huỷ khi đã đọc.

---

### [FIX-08] Sửa AdMob Rewarded Ad Unit ID Test trên Release & Tối Ưu Ad Lifecycle
- **Type:** Monetization / Policy Bug
- **Priority:** `P1 (High)`
- **Estimation:** `1 Story Point`
- **Location:** [`app/build.gradle`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/build.gradle#L101), [`RApp.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/RApp.kt)
- **Vấn đề thực tế:** Trong cấu hình `release` của `app/build.gradle`, `ADMOB_REWARDED_ID` đang trỏ vào Google Test ID (`ca-app-pub-3940256099942544/5224354917`). Nếu cờ `IS_ENABLE_ADMOB` được bật lên `true`, người dùng release xem ad test gây mất 100% doanh thu rewarded ad và vi phạm chính sách AdMob. Đồng thời cần kiểm tra frequency cap và pre-warm ad mượt mà.
- **User Story:**
  > Là chủ sở hữu ứng dụng,  
  > Tôi muốn hệ thống quảng cáo (AppLovin MAX + AdMob) hoạt động chuẩn chỉ, an toàn theo chính sách Google,  
  > Để mang lại nguồn thu ổn định từ quảng cáo banner và rewarded ad mà không làm phiền quá mức trải nghiệm đọc của người dùng.
- **Acceptance Criteria:**
  - **Given** build type là `release`
  - **When** `IS_ENABLE_ADMOB = true`
  - **Then** build script yêu cầu ID thật và không dùng test ID
  - **And** các vị trí banner và rewarded ad tích hợp trong `ReadingPage` và `VipManagementPage` giữ nguyên hoạt động ổn định qua `AdmobApplovinWrapper:1.1.5`.
