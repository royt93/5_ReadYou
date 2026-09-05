# 🎬 Epic 09: Tin Tức Thị Giác & Cảnh Báo Khẩn (Visual Reels & Media)

> **Mục tiêu Epic:** Trẻ hóa trải nghiệm đọc tin bằng định dạng thẻ lướt dọc trực quan (News Reels / Story), tích hợp xem video trong app không quảng cáo và hệ thống cảnh báo khẩn cấp theo từ khóa trọng yếu.

---

### [REEL-01] Giao Diện Thẻ Lướt Dọc "News Reels" Dạng TikTok / Instagram Story
- **Type:** UI/UX Innovation / Gen Z Engagement
- **Priority:** `P2 (Medium)`
- **Estimation:** `8 Story Points`
- **Location:** `app/src/main/java/com/mckimquyen/reader/ui/page/reels/` (Gói mới)
- **Bối cảnh:** Rất nhiều độc giả trẻ tuổi cảm thấy danh sách bài viết truyền thống (dạng list đơn điệu) gây nhàm chán. Họ thích lướt xem các thẻ tóm tắt thị giác với ảnh lớn toàn màn hình, vuốt dọc để chuyển tin tức như TikTok hoặc Instagram Reels.
- **User Story:**
  > Là người thích xem tin tức dạng thị giác nhanh,  
  > Tôi muốn chuyển sang chế độ "News Reels" toàn màn hình,  
  > Để tôi chỉ cần vuốt dọc lên để lướt qua các tin tức nóng nhất kèm ảnh nền đẹp và 2 câu tóm tắt cốt lõi.
- **Acceptance Criteria:**
  - **Given** người dùng chọn chế độ xem "Reels" trên thanh điều hướng BottomBar
  - **When** màn hình hiển thị thẻ tin toàn màn hình với ảnh bìa chất lượng cao làm nền gradient
  - **Then** hiển thị tiêu đề in đậm, tên nguồn báo, thời gian và 3 gạch đầu dòng tóm tắt chính của bài
  - **And** vuốt dọc lên để chuyển sang tin kế tiếp với hiệu ứng chuyển trang 3D mượt mà
  - **And** vuốt sang phải để mở toàn văn bài báo đầy đủ.

---

### [REEL-02] Trình Xem Video RSS & YouTube PiP Không Quảng Cáo (Picture-in-Picture)
- **Type:** Media / Video RSS
- **Priority:** `P2 (Medium)`
- **Estimation:** `5 Story Points`
- **Location:** `app/src/main/java/com/mckimquyen/reader/infrastructure/media/video/` (Gói mới)
- **Bối cảnh:** Rất nhiều kênh tin tức đính kèm video hoặc kênh YouTube có cấp feed RSS. Khi bấm vào video, người dùng bị văng sang app YouTube hoặc xem trên webview nặng nề nhiều quảng cáo rác.
- **User Story:**
  > Là người theo dõi các kênh tin tức video (TED Talks, VTV24, Kurzgesagt, Bloomberg),  
  > Tôi muốn xem video ngay trong ứng dụng với khung hình nhỏ nổi (Picture-in-Picture),  
  > Để tôi vừa có thể xem video vừa tiếp tục lướt danh sách bài đọc khác.
- **Acceptance Criteria:**
  - **Given** bài viết có nhúng video YouTube hoặc thẻ video MP4
  - **When** người dùng ấn phát video
  - **Then** video phát mượt mà không quảng cáo rác thông qua Native Player / ExoPlayer
  - **And** khi người dùng vuốt back hoặc chuyển trang, video tự động thu nhỏ thành cửa sổ nổi Picture-in-Picture (PiP) ở góc màn hình.

---

### [REEL-03] Chó Săn Cảnh Báo Từ Khóa Khẩn Cấp (Keyword Alert Watchdog)
- **Type:** Automation / High-Priority Alert
- **Priority:** `P1 (High)`
- **Estimation:** `5 Story Points`
- **Location:** `app/src/main/java/com/mckimquyen/reader/domain/watchdog/` (Gói mới)
- **Bối cảnh:** Nhà đầu tư, chuyên gia hoặc người dùng quan tâm đặc biệt đến các sự kiện nhạy cảm (ví dụ: mã cổ phiếu `$VIC`, `$FPT`, `Bitcoin`, tên công ty đối thủ, bão lũ, biến động giá vàng). Họ không thể chờ đến lúc rảnh mới mở app mà cần được báo ngay khi có tin liên quan.
- **User Story:**
  > Là nhà đầu tư chứng khoán hoặc chuyên gia theo dõi thị trường,  
  > Tôi muốn đặt từ khóa cảnh báo ưu tiên cao (ví dụ "Lãi suất", "Nghị định 10"),  
  > Để ngay khi bất kỳ báo nào đăng bài chứa từ khóa này, điện thoại sẽ phát chuông báo động khẩn cấp.
- **Acceptance Criteria:**
  - **Given** mục "Cảnh Báo Từ Khóa" (Watchdog) trong Settings
  - **When** người dùng nhập từ khóa: ví dụ `VN-Index`, `Bitcoin`, `Giá vàng`
  - **Then** trong chu kỳ sync, nếu phát hiện bài viết mới chứa từ khóa này
  - **And** hệ thống phát thông báo mức độ ưu tiên cao (`NotificationManager.IMPORTANCE_HIGH`) kèm âm thanh cảnh báo riêng biệt
  - **And** gắn cờ đỏ nổi bật trên thẻ bài viết trong app.
