# 🛡️ Epic 11: Tự Động Hóa Không Chạm, Chợ Nguồn Tin & Bảo Mật Tuyệt Đối (Autonomous, Community & Privacy)

> **Mục tiêu Epic:** Nâng tầm trải nghiệm người dùng lên đỉnh cao: Tự động hóa tạo bản tin Podcast sáng lúc 6:30 không cần chạm tay, giải quyết bài toán tìm nguồn tin qua Chợ Nguồn Tin Khám Phá (Curated Hub) và trang bị lá chắn quyền riêng tư tối thượng (DoH & Chặn Tracker).

---

### [STRAT-01] Zero-Click Autonomous News Agent & Morning Podcast DJ
- **Type:** Automation / AI Agent
- **Priority:** `P1 (High)`
- **Estimation:** `8 Story Points`
- **Location:** `app/src/main/java/com/mckimquyen/reader/domain/agent/autonomous/` (Gói mới), [`domain/sv/SyncWorker.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/domain/sv/SyncWorker.kt)
- **Bối cảnh:** Buổi sáng thức dậy, người dùng bận rộn đánh răng, ăn sáng, lái xe đi làm. Họ không có thời gian mở app, lọc tin, bấm từng nút tóm tắt hay chọn bài để nghe.
- **User Story:**
  > Là người bận rộn thức dậy lúc 7:00 sáng,  
  > Tôi muốn khi tôi cắm tai nghe hoặc bước lên ô tô, bản tin Podcast đối thoại 5 phút tổng hợp các tin nóng nhất đã được chuẩn bị sẵn và tự động phát,  
  > Mà tôi không cần phải mở khóa điện thoại hay chạm vào màn hình dù chỉ một lần.
- **Acceptance Criteria:**
  - **Given** người dùng đặt lịch "Phát hành lúc 07:00 sáng"
  - **When** đồng hồ điểm 06:30 sáng, WorkManager chạy ngầm với điều kiện máy đang cắm sạc/pin > 50%
  - **Then** AI Agent tự động chọn 10 bài viết quan trọng nhất theo thói quen đọc của người dùng
  - **And** gọi Gemini sinh kịch bản bản tin radio buổi sáng 2 MC tự nhiên, vui vẻ
  - **And** nạp sẵn vào MediaSession và đẩy 1 thông báo "Sẵn sàng phát" lên màn hình khóa
  - **And** khi phát hiện cắm tai nghe Bluetooth hoặc kết nối Android Auto lúc 07:00, âm thanh tự động bắt đầu phát.

---

### [STRAT-02] Chợ Nguồn Tin Khám Phá & Bộ Sưu Tập Cộng Đồng (Curated Feed Marketplace)
- **Type:** Growth / Content Discovery
- **Priority:** `P2 (Medium)`
- **Estimation:** `5 Story Points`
- **Location:** `app/src/main/java/com/mckimquyen/reader/ui/page/marketplace/` (Gói mới)
- **Bối cảnh:** Trở ngại lớn nhất của 90% người dùng mới khi dùng RSS là: *"Tôi thích đọc tin nhưng không biết kiếm link RSS ở đâu"*. Các app khác bắt người dùng phải tự copy/paste URL thủ công rất phiền phức.
- **User Story:**
  > Là người mới dùng RSS hoặc muốn tìm thêm nguồn tin mới chất lượng,  
  > Tôi muốn mở "Chợ Nguồn Tin" để duyệt các bộ sưu tập được tuyển chọn sẵn theo chủ đề (AI, Tài chính, Lập trình, Báo chí Việt Nam),  
  > Để tôi có thể đăng ký hàng loạt nguồn tin hay chỉ bằng 1 chạm.
- **Acceptance Criteria:**
  - **Given** tab "Khám Phá" trên thanh điều hướng
  - **When** người dùng mở xem các danh mục:
    - 🤖 *Công Nghệ & AI Tuyển Chọn* (OpenAI, DeepMind, TechCrunch, The Verge)
    - 📈 *Kinh Tế & Đầu Tư* (VnEconomy, CafeF, Bloomberg, Financial Times)
    - 🇻🇳 *Thời Sự Việt Nam* (VnExpress, Tuổi Trẻ, Thanh Niên, Dân Trí)
    - 🎨 *Thiết Kế & Sáng Tạo* (Smashing Mag, Behance, Muzli)
  - **Then** người dùng có thể xem trước các bài viết gần nhất của từng nguồn
  - **And** có nút "Đăng ký toàn bộ danh mục" (1-tap subscribe to bundle) tự động tạo nhóm và thêm feeds vào app.

---

### [STRAT-03] Lá Chắn Quyền Riêng Tư: DNS-over-HTTPS (DoH) & Chặn Tracker Ngầm
- **Type:** Security & Privacy / Anti-Tracking
- **Priority:** `P2 (Medium)`
- **Estimation:** `5 Story Points`
- **Location:** [`infrastructure/di/OkHttpClientModule.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/di/OkHttpClientModule.kt), [`infrastructure/rss/RssHelper.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/rss/RssHelper.kt)
- **Bối cảnh:** Nhiều nhà mạng hoặc wifi công cộng có thể theo dõi thói quen đọc báo của người dùng thông qua DNS query dạng plain-text. Ngoài ra, nhiều trang báo mạng nhúng các pixel theo dõi (tracking pixels 1x1 GIF) và các tham số theo dõi URL (`utm_source`, `fbclid`, `gclid`) để theo dõi hành vi người đọc.
- **User Story:**
  > Là người coi trọng bảo mật và quyền riêng tư trực tuyến,  
  > Tôi muốn toàn bộ kết nối tải tin được mã hóa qua DNS-over-HTTPS và tự động thanh lọc các mã theo dõi ngầm,  
  > Để nhà mạng và bên thứ ba không thể thu thập hồ sơ thói quen đọc tin của tôi.
- **Acceptance Criteria:**
  - **Given** tùy chọn "Lá Chắn Riêng Tư" trong Cài Đặt Bảo Mật
  - **When** người dùng kích hoạt
  - **Then** OkHttpClient sử dụng DNS-over-HTTPS (hỗ trợ chọn Cloudflare `1.1.1.1` hoặc AdGuard DoH)
  - **And** tự động gọt bỏ toàn bộ tracking parameters (`utm_*`, `fbclid`, `_ga`, `gclid`) trên tất cả URL bài viết
  - **And** bộ lọc HTML tự động loại bỏ các thẻ ảnh tracking pixel 1x1 và script quảng cáo bẩn trước khi hiển thị.
