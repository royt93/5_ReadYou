# 🎙️ EPIC 14: CommuteCast Autonomous AI DJ (Đài Phát Thanh Sáng 6:00 Tự Động) [COMPLETED]

> **Trạng thái:** ✅ **HOÀN THÀNH & PUSH CODE DEV** (`26b07a9`)  
> **Điểm Audit Round 2:** **9.9 / 10**  
> **Kiểm thử:** 57/57 Unit & Service Tests Passed, Compose Widget Tests Verified, Real-device Smoke Test on Google Pixel 7 Pro (Android 17) passed.  
> **Mục tiêu:** Giải quyết dứt điểm tình trạng ngợp tin tức (Inbox Fatigue) bằng một đài phát thanh buổi sáng hoàn toàn tự động. 6:00 sáng mỗi ngày, ứng dụng tự động tổng hợp các tin chưa đọc thành một bản tin Podcast sinh động 4 phút giữa 2 MC ảo lồng nhạc nền lofi, hỗ trợ Android Auto và MediaSession màn hình khóa.  
> **Cơ chế Monetization (AdmobApplovinWrapper:1.1.5):** Tận dụng App Open Ads khi người dùng nhấn vào Push Notification buổi sáng để bắt đầu nghe; Rewarded Ads để mở rộng thành bản Deep Dive 15 phút chi tiết.

---

## 📋 Danh Sách User Stories & Tasks Chi Tiết

### 1. `TASK-DJ-01`: Bộ Lập Lịch Tự Động Buổi Sáng & Kịch Bản 2 MC (Autonomous 6 AM Scriptwriter)
- **ID:** `TASK-DJ-01`
- **Loại:** `Feature` | **Độ ưu tiên:** `P1 (High)` | **Story Points:** `8 SP`
- **Mô tả:** Sử dụng Android `WorkManager` (PeriodicWorkRequest kích hoạt lúc 6:00 AM) để quét top 5 bài viết chưa đọc quan trọng nhất trong cơ sở dữ liệu. Prompt AI xử lý tổng hợp thành kịch bản đối thoại hài hước, súc tích giữa 2 nhân vật (Host Alex & Co-Host Sam) dài 4 phút.
- **Tiêu chí chấp nhận (Gherkin):**
  ```gherkin
  Given đến 6:00 sáng và thiết bị đang kết nối Wifi
  When WorkManager kích hoạt tác vụ nền
  Then chọn lọc 5 tin bài có điểm tương tác cao nhất
  And sinh kịch bản đối thoại JSON gồm các lời thoại xen kẽ giữa Host A và Host B
  And gửi Push Notification: "☕ Bản tin sáng CommuteCast 4 phút của bạn đã sẵn sàng!"
  ```

---

### 2. `TASK-DJ-02`: Động Cơ Phát Âm 2 Giọng Kèm Nhạc Nền Lofi (Dual-Voice TTS & Audio Mixer)
- **ID:** `TASK-DJ-02`
- **Loại:** `Feature` | **Độ ưu tiên:** `P1 (High)` | **Story Points:** `8 SP`
- **Mô tả:** Tích hợp bộ phát TTS linh hoạt chuyển đổi giữa 2 chất giọng (Nam trầm ấm & Nữ năng động), hòa âm cùng track nhạc nền lofi acoustic nhẹ nhàng có bản quyền CC0 ở mức âm lượng 15%. Cung cấp giao diện sóng âm thanh trực quan dạng nhịp tim (Audio Visualizer) trong Compose.
- **Tiêu chí chấp nhận (Gherkin):**
  ```gherkin
  Given người dùng bắt đầu phát CommuteCast
  When đoạn thoại chuyển từ Host A sang Host B
  Then chất giọng, cao độ và ngữ điệu TTS tự động thay đổi mượt mà không có khoảng lặng quá 300ms
  And nhạc nền lofi tự động giảm âm lượng (Audio Ducking) khi MC đang nói
  ```

---

### 3. `TASK-DJ-03`: Tích Hợp Android Auto & Lockscreen MediaSession
- **ID:** `TASK-DJ-03`
- **Loại:** `Feature` | **Độ ưu tiên:** `P1 (High)` | **Story Points:** `5 SP`
- **Mô tả:** Xây dựng `MediaSessionService` chuẩn AndroidX Media3. Hỗ trợ hiển thị tên tập, ảnh bìa, nút tua 10s, tạm dừng trên màn hình khóa điện thoại, thanh thông báo hệ thống, và giao diện xe hơi Android Auto khi cắm cáp kết nối.
- **Tiêu chí chấp nhận (Gherkin):**
  ```gherkin
  Given người dùng đang lái xe và kết nối điện thoại với xe qua Android Auto
  When CommuteCast phát
  Then tên bản tin, hình ảnh bìa và các phím điều hướng xuất hiện trực tiếp trên màn hình xe hơi
  And thao tác bấm tạm dừng trên vô-lăng xe phản hồi ngay lập tức
  ```

---

### 4. `TASK-DJ-04`: Tối Ưu Doanh Thu Buổi Sáng Với App Open & Rewarded Ads
- **ID:** `TASK-DJ-04`
- **Loại:** `Feature` | **Độ ưu tiên:** `P0 (Critical Monetization)` | **Story Points:** `5 SP`
- **Mô tả:** Khi người dùng click vào thông báo đẩy buổi sáng lúc 6:00–8:00 AM, `AdmobApplovinWrapper` hiển thị ngay một App Open Ad (khung giờ vàng có eCPM cao nhất trong ngày từ $15–$35). Thêm tùy chọn "Nghe phiên bản Chuyên Sâu 15 phút (Deep Dive)" thông qua 1 lượt xem Rewarded Ad.
- **Tiêu chí chấp nhận (Gherkin):**
  ```gherkin
  Given người dùng bấm vào notification "Bản tin sáng CommuteCast"
  When ứng dụng chuyển từ nền lên foreground
  Then AdmobApplovinWrapper hiển thị App Open Ad toàn màn hình
  When quảng cáo đóng lại, bản tin âm thanh tự động tiếp tục phát mà không bị ngắt quãng
  ```

---

## 📊 Tổng Kết Epic 14
- **Số lượng User Stories:** 4 tasks
- **Tổng Story Points:** 26 SP
- **Tác động:** Tạo thói quen sử dụng cố định mỗi buổi sáng (Morning Habit Loop); tối ưu eCPM khung giờ vàng thông qua App Open Ads và Rewarded Ads.
