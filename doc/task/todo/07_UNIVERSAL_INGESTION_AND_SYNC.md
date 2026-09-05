# 🌐 Epic 07: Đa Kênh & Biến Mọi Web Thành RSS (Universal Ingestion)

> **Mục tiêu Epic:** Phá vỡ rào cản chỉ đọc nguồn có RSS sẵn, cho phép người dùng theo dõi bất kỳ trang web nào trên internet và lưu bài đọc sau (Read Later) trực tiếp từ các trình duyệt khác.

---

### [INGEST-01] Web-to-RSS Generator (Biến Bất Kỳ Website Nào Thành RSS Feed)
- **Type:** Core Engine / Web Scraping
- **Priority:** `P1 (High)`
- **Estimation:** `8 Story Points`
- **Location:** `app/src/main/java/com/mckimquyen/reader/infrastructure/rss/scraper/` (Gói mới)
- **Bối cảnh:** Hàng triệu trang blog cá nhân, website thông báo tuyển dụng, trang tin nội bộ trường học/cơ quan không hề có link RSS/Atom. Người dùng vẫn phải vào web thủ công để F5 kiểm tra tin mới.
- **User Story:**
  > Là người cần theo dõi các website không hỗ trợ RSS,  
  > Tôi muốn dán đường link website bất kỳ vào app để tự tạo một nguồn theo dõi riêng,  
  > Để mỗi khi website đó có bài viết mới, app sẽ tự động thông báo và hiển thị như một feed RSS bình thường.
- **Acceptance Criteria:**
  - **Given** người dùng dán URL của một trang blog/tin tức không có RSS
  - **When** app phân tích cấu trúc DOM trang web bằng Jsoup / Readability
  - **Then** tự động nhận diện danh sách bài viết dựa trên thẻ lặp lại (article, heading, timestamp)
  - **And** sinh ra một Virtual RSS Feed lưu trong Room và tự động cập nhật định kỳ như feed RSS chuẩn.

---

### [INGEST-02] Android Share Target — Đọc Sau (Read-Later) Từ Chrome & Mạng Xã Hội
- **Type:** System Integration / Read Later
- **Priority:** `P2 (Medium)`
- **Estimation:** `5 Story Points`
- **Location:** [`app/src/main/AndroidManifest.xml`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/AndroidManifest.xml), `ui/page/share/` (Gói mới)
- **Bối cảnh:** Khi người dùng đang lướt Chrome, Twitter/X, Facebook, Threads và bắt gặp một bài viết dài hay nhưng chưa có thời gian đọc, họ cần một nút lưu bài nhanh giống như Pocket hoặc Instapaper.
- **User Story:**
  > Là người hay lướt web trên điện thoại,  
  > Tôi muốn bấm nút "Chia sẻ" trong Chrome và chọn "Lưu vào RSS Cat Hub",  
  > Để bài báo được tự động trích xuất toàn văn, lưu vào mục "Đọc sau" và sẵn sàng đọc offline.
- **Acceptance Criteria:**
  - **Given** người dùng đang ở trình duyệt Chrome hoặc bất kỳ app nào
  - **When** bấm nút Chia sẻ (Share Sheet của Android) và chọn RSS Cat Hub
  - **Then** app nhận URL thông qua `android.intent.action.SEND`
  - **And** tải ngầm toàn văn bài viết và hình ảnh bằng Readability trong < 2 giây
  - **And** hiển thị thông báo nhẹ (Toast/Snackbar) "Đã lưu vào Đọc sau".

---

### [INGEST-03] Bản Tin Email & Newsletter Ingestion Bridge
- **Type:** Integration / Content Source
- **Priority:** `P2 (Medium)`
- **Estimation:** `5 Story Points`
- **Location:** `infrastructure/newsletter/` (Gói mới)
- **Bối cảnh:** Xu hướng các chuyên gia và tác giả hàng đầu chuyển sang viết bản tin email (Substack, Ghost, Beehiiv). Hộp thư Gmail/Outlook của người dùng bị quá tải và xao nhãng bởi email công việc.
- **User Story:**
  > Là người đăng ký nhiều bản tin Substack/Medium chất lượng cao,  
  > Tôi muốn đọc các bản tin email này trong cùng một giao diện đọc sách yên tĩnh của RSS Cat Hub,  
  > Để tách bạch việc đọc tri thức khỏi hộp thư công việc ồn ào.
- **Acceptance Criteria:**
  - **Given** người dùng thêm một kênh Substack/Medium/Ghost
  - **When** nhập link profile hoặc email newsletter feed
  - **Then** app tự động giải mã các bài viết mới từ newsletter
  - **And** hiển thị bài viết sạch đẹp, lược bỏ toàn bộ chữ ký email và nút bấm hủy đăng ký thừa thãi.
