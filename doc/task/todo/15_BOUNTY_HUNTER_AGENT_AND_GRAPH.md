# 🕵️ EPIC 15: AI Bounty Hunter Agent & Obsidian Graph (Trợ Lý Điều Tra Sâu & Mạng Nhện Tri Thức)

> **Mục tiêu:** Nâng tầm ứng dụng đọc tin thành trung tâm tình báo tri thức cá nhân (Second Brain). Người dùng có thể bôi đen một thực thể/khái niệm/công ty trong bài viết và chọn "Gửi Agent Điều Tra". Agent sẽ tự động cào sâu các nguồn GitHub, Reddit, tin tức liên quan để lập "Hồ Sơ Điều Tra (Executive Dossier)" và kết nối vào biểu đồ mạng nhện tri thức tương tác kiểu Obsidian.  
> **Cơ chế Monetization (AdmobApplovinWrapper:1.1.5):** Cung cấp 1 lượt điều tra miễn phí mỗi ngày. Mỗi lượt cử Agent điều tra sâu tiếp theo yêu cầu 1 lượt xem Rewarded Video (mô hình Token Sink được người dùng đón nhận tích cực).

---

## 📋 Danh Sách User Stories & Tasks Chi Tiết

### 1. `TASK-BOU-01`: Kích Hoạt Agent Điều Tra 1-Chạm Trên Màn Hình Đọc (Contextual Agent Trigger)
- **ID:** `TASK-BOU-01`
- **Loại:** `Feature` | **Độ ưu tiên:** `P1 (High)` | **Story Points:** `5 SP`
- **Mô tả:** Trong giao diện đọc bài viết (`ReadingPage.kt`), khi người dùng bôi đen một cụm từ (ví dụ: tên công ty khởi nghiệp, một thuật toán mới, hoặc một dự luật), thanh menu ngữ cảnh hiển thị thêm nút đặc biệt: **"🕵️ Gửi Agent Điều Tra"**. Bấm nút sẽ mở BottomSheet theo dõi tiến độ quét thông tin thời gian thực của Agent.
- **Tiêu chí chấp nhận (Gherkin):**
  ```gherkin
  Given người dùng đang bôi đen cụm từ "DeepSeek-R1" trong bài viết
  When nhấn nút "Gửi Agent Điều Tra"
  Then mở BottomSheet hiển thị hoạt ảnh Radar quét dữ liệu
  And Agent bắt đầu gửi truy vấn thu thập dữ liệu đa nguồn ngầm
  ```

---

### 2. `TASK-BOU-02`: Trình Tạo Hồ Sơ Báo Cáo Phân Tích (Executive Intelligence Dossier)
- **ID:** `TASK-BOU-02`
- **Loại:** `Feature` | **Độ ưu tiên:** `P1 (High)` | **Story Points:** `8 SP`
- **Mô tả:** Agent xử lý dữ liệu và xuất ra một tài liệu Dossier chuẩn bao gồm 4 phần:
  1. *Executive Summary:* Tóm tắt bản chất trong 3 gạch đầu dòng.
  2. *Timeline Sự Kiện:* Các cột mốc quan trọng trong quá khứ liên quan đến chủ đề.
  3. *Key Players:* Những nhân vật, công ty, hoặc repo liên quan.
  4. *Tranh Luận Cộng Đồng:* Góc nhìn trái chiều từ Reddit/Hacker News.
  Hỗ trợ xuất sang file Markdown hoặc PDF để chia sẻ.
- **Tiêu chí chấp nhận (Gherkin):**
  ```gherkin
  Given Agent đã thu thập đủ dữ liệu
  When quá trình tổng hợp hoàn tất
  Then hiển thị tài liệu Dossier định dạng giao diện thẻ tab Material 3 chuyên nghiệp
  And cho phép người dùng lưu vào thư viện yêu thích hoặc xuất ra Markdown
  ```

---

### 3. `TASK-BOU-03`: Biểu Đồ Mạng Nhện Tri Thức Kiểu Obsidian (Interactive 2D Knowledge Graph)
- **ID:** `TASK-BOU-03`
- **Loại:** `Feature` | **Độ ưu tiên:** `P2 (Medium)` | **Story Points:** `8 SP`
- **Mô tả:** Xây dựng màn hình Canvas Compose biểu diễn các bài viết và thực thể dưới dạng các nút (nodes) và đường liên kết (edges). Người dùng có thể zoom, pan, chạm vào một nút để xem tất cả các bài viết trong máy có liên quan đến thực thể đó (tương tự đồ thị tri thức của Obsidian / Roam Research).
- **Tiêu chí chấp nhận (Gherkin):**
  ```gherkin
  Given người dùng mở tab "Bản Đồ Tri Thức (Knowledge Graph)"
  When chạm vào nút "Artificial Intelligence"
  Then các nút liên kết (OpenAI, Anthropic, GPU, Room DB) sáng lên
  And danh sách bài viết tương ứng được lọc ra ở nửa dưới màn hình
  ```

---

### 4. `TASK-BOU-04`: Mô Hình Monetization Token-Sink Bằng Rewarded Ads
- **ID:** `TASK-BOU-04`
- **Loại:** `Feature` | **Độ ưu tiên:** `P0 (Critical Monetization)` | **Story Points:** `5 SP`
- **Mô tả:** Mỗi người dùng miễn phí có 1 "Energy Token" mỗi ngày (tương đương 1 lượt chạy Bounty Hunter). Khi hết token, nút "Gửi Agent Điều Tra" sẽ có nhãn: *"Xem 1 video ngắn để nạp Năng Lượng Agent"*. Tích hợp chặt chẽ với `AdmobApplovinWrapper.showRewardedVideo()`.
- **Tiêu chí chấp nhận (Gherkin):**
  ```gherkin
  Given người dùng đã sử dụng hết token miễn phí trong ngày
  When nhấn "Gửi Agent Điều Tra" lần thứ hai
  Then hiển thị popup: "Cử Agent cần thêm năng lượng. Xem 1 video tài trợ để bắt đầu cuộc điều tra chuyên sâu?"
  When xem xong video quảng cáo, Agent lập tức tiến hành quét dữ liệu
  ```

---

## 📊 Tổng Kết Epic 15
- **Số lượng User Stories:** 4 tasks
- **Tổng Story Points:** 26 SP
- **Tác động:** Biến app thành công cụ năng suất không thể thay thế cho học sinh, sinh viên, kỹ sư, nhà đầu tư; tạo ra tỷ lệ xem Rewarded Ad tự nguyện cực kỳ cao.
