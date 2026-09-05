# ⚡ Epic 02: Nâng Cấp Hiệu Năng & Trải Nghiệm (Enhance Performance & UX)

> **Mục tiêu Epic:** Tối ưu hóa triệt để tài nguyên thiết bị (CPU, RAM, Pin, Băng thông 4G/5G), hiện đại hóa stack Jetpack Compose và nâng tầm trải nghiệm âm thanh/AI.

---

### [ENH-01] Chuyển đổi thư viện Accompanist cũ sang Native Compose & Material 3
- **Type:** Refactoring / Modernization
- **Priority:** `P1 (High)`
- **Estimation:** `5 Story Points`
- **Location:** [`app/build.gradle`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/build.gradle#L192-L198)
- **Vấn đề thực tế:** Dự án vẫn đang sử dụng Accompanist bản `0.24.7-alpha` (từ 2022) như `swiperefresh`, `systemuicontroller`, `pager`, `flowlayout`. Các thư viện này đã bị Google deprecate và chứa nhiều lỗi không tương thích với Android 15/16 Predictive Back và Edge-to-Edge.
- **User Story:**
  > Là nhà phát triển ứng dụng,  
  > Tôi muốn sử dụng các API chính thức của Compose 1.7+ và Material 3 hiện đại,  
  > Để giảm kích thước APK, tăng độ ổn định và tận dụng tối đa animation cử chỉ hệ thống mới.
- **Acceptance Criteria:**
  - **Given** các màn hình `FeedsPage`, `FlowPage`, `ReadingPage`
  - **When** người dùng thao tác vuốt để làm mới (Pull-to-refresh)
  - **Then** sử dụng Material 3 `PullToRefreshBox` hoặc `pullRefresh` chính thức thay cho Accompanist SwipeRefresh
  - **And** thay thế `systemuicontroller` bằng `ComponentActivity.enableEdgeToEdge()` chuẩn Android 15
  - **And** gỡ bỏ hoàn toàn các dependency Accompanist alpha khỏi `app/build.gradle`.

---

### [ENH-02] Tích hợp Room Full-Text Search (FTS4 / FTS5) cho Tìm Kiếm Bài Báo
- **Type:** Performance / UX Enhancement
- **Priority:** `P1 (High)`
- **Estimation:** `5 Story Points`
- **Location:** [`domain/repository/ArticleDao.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/domain/repository/ArticleDao.kt), [`infrastructure/db/AndroidDb.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/db/AndroidDb.kt)
- **Vấn đề thực tế:** Tìm kiếm bài viết hiện tại dùng câu lệnh `LIKE '%' || :text || '%'` quét toàn bộ bảng trên cả trường `fullContent` khổng lồ. Với cơ sở dữ liệu hàng ngàn bài viết, tìm kiếm có thể mất vài giây và giật lag nghiêm trọng khi gõ từng ký tự.
- **User Story:**
  > Là người dùng cần tra cứu lại một bài viết cũ trong kho lưu trữ,  
  > Tôi muốn kết quả tìm kiếm hiển thị ngay lập tức theo thời gian thực (as-you-type) kèm highlight từ khóa,  
  > Để tôi nhanh chóng tìm thấy thông tin mình cần.
- **Acceptance Criteria:**
  - **Given** cơ sở dữ liệu có hơn 10,000 bài viết
  - **When** người dùng gõ từ khóa vào thanh Search Bar ở `FlowPage`
  - **Then** câu lệnh truy vấn sử dụng bảng ảo FTS `article_fts MATCH :query`
  - **And** thời gian phản hồi tìm kiếm đạt dưới **20ms**
  - **And** hỗ trợ tìm kiếm không dấu (tiếng Việt), tìm cụm từ và đánh trọng số BM25 cho tiêu đề cao hơn nội dung.

---

### [ENH-03] Lưu Trữ Kết Quả Tóm Tắt AI vào Database & Giao Diện Tùy Chỉnh API Key
- **Type:** Feature Enhancement
- **Priority:** `P1 (High)`
- **Estimation:** `5 Story Points`
- **Location:** [`infrastructure/ai/GeminiSummaryService.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/ai/GeminiSummaryService.kt), [`ui/page/home/read/SummarySheet.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/page/home/read/SummarySheet.kt), [`ui/page/setting/SettingsPage.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/page/setting/SettingsPage.kt)
- **Vấn đề thực tế:** Tóm tắt AI hiện tại chỉ lưu tạm trong RAM của `ReadingViewModel`. Khi người dùng đóng app hoặc chuyển bài viết, kết quả tóm tắt bị mất. Nếu mở lại sẽ phải gọi API tóm tắt lần nữa, gây tốn quota và chờ đợi. Ngoài ra, chưa có chỗ cho người dùng nhập key cá nhân (BYOK - Bring Your Own Key).
- **User Story:**
  > Là người dùng đọc báo có AI hỗ trợ,  
  > Tôi muốn các bản tóm tắt đã tạo được lưu vĩnh viễn với bài viết và có thể dùng API key riêng của tôi,  
  > Để tôi không phải tóm tắt lại nhiều lần và không lo bị giới hạn số lần tóm tắt miễn phí.
- **Acceptance Criteria:**
  - **Given** người dùng bấm tóm tắt một bài viết thành công
  - **When** bài báo được lưu vào database
  - **Then** trường `aiSummary` trong bảng `article` được cập nhật và hiển thị lại ngay khi mở lại bài đó mà không cần gọi API
  - **And** thêm màn hình "Cài đặt AI" trong Settings cho phép người dùng:
    1. Nhập API Key riêng (Google Gemini, OpenAI, DeepSeek, Groq)
    2. Chọn độ dài tóm tắt: "3 gạch đầu dòng ngắn", "Bản tin chi tiết", "1 đoạn văn TL;DR".

---

### [ENH-04] Foreground Service & Điều Khiển Màn Hình Khóa Cho Text-to-Speech (TTS)
- **Type:** UX & Background Stability
- **Priority:** `P1 (High)`
- **Estimation:** `5 Story Points`
- **Location:** [`infrastructure/audio/TtsManager.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/audio/TtsManager.kt)
- **Vấn đề thực tế:** `TtsManager` chạy in-process gắn với Application. Khi người dùng tắt màn hình hoặc chuyển sang ứng dụng khác (đi xe, tập gym), hệ điều hành có thể thu hồi tiến trình bất cứ lúc nào. Không có thông báo Media Notification, không thể bấm Play/Pause từ tai nghe Bluetooth hay màn hình khóa.
- **User Story:**
  > Là người nghe tin tức rảnh tay khi di chuyển,  
  > Tôi muốn bài đọc tiếp tục phát khi tắt màn hình và có thể tạm dừng/bỏ qua từ tai nghe hoặc màn hình khóa,  
  > Để tôi có trải nghiệm nghe podcast tin tức hoàn hảo.
- **Acceptance Criteria:**
  - **Given** người dùng đang nghe đọc một bài báo
  - **When** người dùng tắt màn hình điện thoại
  - **Then** giọng đọc vẫn phát liên tục mượt mà nhờ Android Foreground Service (`mediaPlayback`)
  - **And** xuất hiện Media Notification với đầy đủ nút: Play, Pause, Tua lại 15s, Đọc tiếp bài sau
  - **And** tai nghe Bluetooth (nút bấm điều khiển) nhận tín hiệu chuẩn `MediaSessionCompat`
  - **And** hỗ trợ chọn tốc độ phát: 0.75x, 1.0x, 1.25x, 1.5x, 2.0x.

---

### [ENH-05] Trích Xuất Ảnh Thumbnail Thông Minh từ `<enclosure>` và `<media:content>`
- **Type:** UI/UX & Data Quality
- **Priority:** `P2 (Medium)`
- **Estimation:** `3 Story Points`
- **Location:** [`infrastructure/rss/RssHelper.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/rss/RssHelper.kt#L139-L146)
- **Vấn đề thực tế:** Hàm `findImg` hiện tại chỉ quét regex tìm thẻ `<img>` bên trong HTML description. Rất nhiều báo lớn (VnExpress, Tuổi Trẻ, BBC, NYT, Substack, YouTube RSS) đặt ảnh bìa chất lượng cao trong thẻ `<enclosure type="image/jpeg">` hoặc `<media:thumbnail url="...">`. Kết quả là danh sách bài viết bị khuyết ảnh bìa rất nhiều.
- **User Story:**
  > Là người dùng thích giao diện Card / Tạp chí trực quan,  
  > Tôi muốn bài viết luôn hiển thị hình ảnh đại diện sắc nét từ nguồn tin,  
  > Để giao diện trông sống động và hấp dẫn.
- **Acceptance Criteria:**
  - **Given** một nguồn tin RSS có ảnh trong thẻ enclosure hoặc media module
  - **When** RSS feed được phân tích
  - **Then** `RssHelper` ưu tiên lấy ảnh từ `<enclosure>`, `<media:content>`, `<media:thumbnail>` trước khi fallback sang regex HTML
  - **And** loại bỏ các tracking pixel (ảnh 1x1 GIF) hoặc icon biểu cảm nhỏ.

---

### [ENH-06] OkHttp HTTP Caching với ETag & `If-Modified-Since` (`304 Not Modified`)
- **Type:** Network & Battery Optimization
- **Priority:** `P2 (Medium)`
- **Estimation:** `3 Story Points`
- **Location:** [`infrastructure/di/OkHttpClientModule.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/di/OkHttpClientModule.kt), [`domain/sv/AbstractRssRepository.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/domain/sv/AbstractRssRepository.kt)
- **Vấn đề thực tế:** Mỗi chu kỳ WorkManager đồng bộ nền, app tải lại toàn bộ file XML của tất cả các feed dù nội dung không thay đổi, gây tiêu hao vô ích dữ liệu 4G và pin của người dùng.
- **Acceptance Criteria:**
  - **Given** một feed chưa có bài viết mới trên server
  - **When** chu kỳ đồng bộ nền chạy
  - **Then** request gửi header `If-None-Match` (ETag) và `If-Modified-Since`
  - **And** server trả về HTTP `304 Not Modified`, app lập tức bỏ qua không cần tải và parse lại XML
  - **And** tiết kiệm đến **80%** lưu lượng dữ liệu mạng trong các lần sync định kỳ.
