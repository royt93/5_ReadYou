# 🚀 Epic 03: Tính Năng Mới Chuẩn RSS (New Core Features)

> **Mục tiêu Epic:** Mở rộng hệ sinh thái kết nối RSS, hỗ trợ toàn diện các dịch vụ self-hosted phổ biến nhất và mang lại khả năng đọc tin offline không phụ thuộc mạng.

---

### [NEW-01] Tích Hợp Đầy Đủ Chuẩn Google Reader API (FreshRSS, Miniflux, Nextcloud News)
- **Type:** New Feature / Ecosystem
- **Priority:** `P1 (High)`
- **Estimation:** `8 Story Points`
- **Location:** [`domain/sv/RssSv.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/domain/sv/RssSv.kt#L22), [`infrastructure/rss/provider/googleReader/`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/rss/provider/googleReader/)
- **Bối cảnh:** Hiện tại code `GoogleReader` mới chỉ có file DTO và trong `RssSv.kt` đang bị comment out, ép fallback về `LocalRssSv`. Đa số người dùng RSS chuyên nghiệp trên thế giới hiện nay tự host FreshRSS, Miniflux, Nextcloud News, BazQux – tất cả đều chạy theo chuẩn Google Reader API v1 (`/accounts/ClientLogin`, `/reader/api/0/stream/contents/`).
- **User Story:**
  > Là người dùng sở hữu máy chủ FreshRSS hoặc Miniflux riêng,  
  > Tôi muốn đăng nhập tài khoản của mình trên RSS Cat Hub,  
  > Để trạng thái đã đọc và bài viết yêu thích của tôi được đồng bộ hai chiều giữa máy tính và điện thoại.
- **Acceptance Criteria:**
  - **Given** người dùng chọn "Thêm tài khoản Google Reader API"
  - **When** nhập Endpoint URL, Username, Password / API Token
  - **Then** app xác thực thành công và tải đầy đủ danh sách chuyên mục, feeds và bài viết
  - **And** khi người dùng đánh dấu đã đọc hoặc gắn sao (starred) trên điện thoại, trạng thái được đồng bộ ngược lên server ngay lập tức
  - **And** mở khóa nút chọn FreshRSS/Miniflux trong giao diện `AddAccountsPage.kt`.

---

### [NEW-02] Tự Động Sao Lưu & Đồng Bộ OPML Qua WebDAV & Google Drive
- **Type:** New Feature / Data Privacy
- **Priority:** `P2 (Medium)`
- **Estimation:** `5 Story Points`
- **Location:** [`domain/sv/OpmlSv.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/domain/sv/OpmlSv.kt), [`ui/page/setting/SettingsPage.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/page/setting/SettingsPage.kt)
- **Bối cảnh:** Người dùng tài khoản Local hiện tại nếu đổi máy hoặc cài lại app sẽ mất toàn bộ danh sách feed đã cất công gom góp nhiều năm nếu không nhớ export OPML thủ công.
- **User Story:**
  > Là người dùng quan tâm đến quyền riêng tư và an toàn dữ liệu,  
  > Tôi muốn ứng dụng tự động sao lưu cấu hình nguồn tin lên WebDAV (Nextcloud cá nhân) hoặc Google Drive cá nhân,  
  > Để khi tôi chuyển sang máy mới, toàn bộ feeds và nhóm tin tự động khôi phục nguyên vẹn.
- **Acceptance Criteria:**
  - **Given** người dùng cấu hình tài khoản WebDAV hoặc Google Drive trong Settings
  - **When** người dùng thêm/xóa feed hoặc định kỳ hàng tuần
  - **Then** app tự động xuất file `rss_hub_backup.opml` lên thư mục đám mây cá nhân
  - **And** cung cấp nút "Khôi phục từ đám mây" chỉ với 1 chạm khi mới cài app.

---

### [NEW-03] Bộ Lọc Quy Tắc Thông Minh (Smart Feed Filter & Rule Engine)
- **Type:** New Feature / Productivity
- **Priority:** `P2 (Medium)`
- **Estimation:** `5 Story Points`
- **Location:** [`domain/repository/FeedDao.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/domain/repository/FeedDao.kt), [`domain/sv/AbstractRssRepository.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/domain/sv/AbstractRssRepository.kt)
- **Bối cảnh:** Nhiều trang báo RSS xuất bản hàng trăm bài mỗi ngày gồm nhiều nội dung rác (quảng cáo, tuyển dụng, tin giật gân, chủ đề không quan tâm). Người dùng bị ngợp thông tin (information overload).
- **User Story:**
  > Là người bận rộn đọc tin có chọn lọc,  
  > Tôi muốn thiết lập quy tắc tự động ẩn hoặc tự động đánh dấu đã đọc bài viết chứa từ khóa tôi không muốn thấy,  
  > Để bảng tin của tôi luôn sạch và chỉ chứa nội dung giá trị.
- **Acceptance Criteria:**
  - **Given** giao diện "Quy tắc bộ lọc" (Filter Rules) trong Settings
  - **When** người dùng thêm quy tắc:
    - Nếu Tiêu đề chứa `[Quảng cáo]`, `Tài trợ` -> Tự động đánh dấu đã đọc
    - Nếu Tác giả là `X` hoặc Tiêu đề chứa `AI`, `Android` -> Tự động gắn sao (Star)
  - **Then** khi `SyncWorker` tải bài viết mới về, hệ thống quy tắc lập tức được áp dụng trước khi hiển thị ra màn hình `FlowPage`.

---

### [NEW-04] Chế Độ Đọc Ngoại Tuyến Toàn Diện (Full Offline Pre-Caching)
- **Type:** New Feature / Offline First
- **Priority:** `P2 (Medium)`
- **Estimation:** `5 Story Points`
- **Location:** [`infrastructure/rss/RssHelper.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/rss/RssHelper.kt), [`domain/sv/SyncWorker.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/domain/sv/SyncWorker.kt)
- **Bối cảnh:** Khi người dùng đi máy bay hoặc ở vùng sóng yếu (hầm xe buýt, tàu điện ngầm), bài viết chỉ có tiêu đề tóm tắt ngắn, không tải được toàn văn bài viết và hình ảnh.
- **User Story:**
  > Là người hay di chuyển hoặc đọc sách trên máy bay,  
  > Tôi muốn app tự động tải sẵn toàn văn bài viết và hình ảnh khi có Wi-Fi,  
  > Để tôi có thể đọc thoải mái ngay cả khi tắt hoàn toàn dữ liệu di động.
- **Acceptance Criteria:**
  - **Given** tùy chọn "Tự động tải nội dung ngoại tuyến qua Wi-Fi" được bật
  - **When** `SyncWorker` chạy lúc đang sạc và có kết nối Wi-Fi
  - **Then** tự động parse Readability full-content và nạp trước ảnh vào Coil Disk Cache cho tối đa 50 bài viết chưa đọc mới nhất
  - **And** khi ở chế độ máy bay (Airplane mode), mở bài đọc hiển thị đầy đủ 100% chữ và ảnh không lỗi.
