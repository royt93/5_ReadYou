# 🎮 EPIC 12: Brain RPG & Knowledge Wrapped (Game Hóa Việc Đọc & Viral Retention)

> **Mục tiêu:** Biến trải nghiệm đọc tin RSS từ thụ động thành một trò chơi nhập vai phát triển bản thân (Duolingo for Reading). Người dùng tích lũy XP theo danh mục kiến thức, làm bài kiểm tra hiểu biết AI 10 giây cuối bài đọc, nhận thẻ "Brain Wrapped" hàng tuần để viral mạng xã hội.  
> **Cơ chế Monetization (AdmobApplovinWrapper:1.1.5):** Tận dụng tối đa vòng lặp xem quảng cáo Rewarded Video (x2 XP, cứu streak đọc, hồi sinh cây kỹ năng bị mài mòn) và Interstitial tự nhiên khi thăng cấp nhân vật.

---

## 📋 Danh Sách User Stories & Tasks Chi Tiết

### 1. `TASK-RPG-01`: Hệ Thống Điểm Kinh Nghiệm (XP) & Cây Kỹ Năng Tri Thức (Knowledge Skill Tree)
- **ID:** `TASK-RPG-01`
- **Loại:** `Feature` | **Độ ưu tiên:** `P1 (High)` | **Story Points:** `5 SP`
- **Mô tả:** Thiết kế kiến trúc Room DB lưu trữ bảng `user_progress` (XP, cấp độ, streak ngày đọc) và `skill_node` (các nhánh kỹ năng: Tech & AI, Macroeconomics, Health & Biohacking, Philosophy, Design...). Khi người đọc cuộn hết 80% độ dài bài viết và ở lại tối thiểu 30 giây, hệ thống tự động cộng XP theo chủ đề được AI phân loại.
- **Tiêu chí chấp nhận (Gherkin):**
  ```gherkin
  Given người dùng đang đọc một bài viết thuộc chuyên mục "AI & Công nghệ"
  When người dùng cuộn đọc qua 80% bài viết với thời lượng trên 30 giây
  Then hệ thống bắn sự kiện hiệu ứng vi mô (+50 XP Tech) bay nhẹ ở góc màn hình
  And cập nhật cấp độ và thanh tiến trình trong Room DB không gây giật lag khung hình
  ```

---

### 2. `TASK-RPG-02`: Trắc Nghiệm Hiểu Bài Nhanh Cuối Bài (AI Micro-Quiz Engine)
- **ID:** `TASK-RPG-02`
- **Loại:** `Feature` | **Độ ưu tiên:** `P1 (High)` | **Story Points:** `8 SP`
- **Mô tả:** Ở cuối mỗi bài viết, hiển thị một card trắc nghiệm tương tác gồm 1 câu hỏi nhanh 4 đáp án do AI sinh ra dựa trên bài đọc. Trả lời đúng nhận ngay **x3 XP (+150 XP)** và mở huy hiệu "Master Reader". Trả lời sai cho phép xem 1 video Rewarded Ad để làm lại câu hỏi giữ chuỗi streak hoàn hảo.
- **Tiêu chí chấp nhận (Gherkin):**
  ```gherkin
  Given người dùng đọc đến cuối bài viết
  When card "Thử thách 10 giây của AI" hiển thị với câu hỏi và 4 đáp án
  And người dùng chọn đáp án đúng
  Then hiển thị pháo hoa Confetti Lottie và cộng 150 XP vào cây kỹ năng
  When người dùng chọn sai
  Then hiển thị nút "Xem Video mở quyền thử lại ngay" kết nối AdmobApplovinWrapper Rewarded Video
  ```

---

### 3. `TASK-RPG-03`: Cơ Chế Suy Thoái Tri Thức (Cognitive Decay) & Hồi Sinh Streak Bằng Rewarded Ads
- **ID:** `TASK-RPG-03`
- **Loại:** `Feature` | **Độ ưu tiên:** `P1 (High)` | **Story Points:** `5 SP`
- **Mô tả:** Nếu người dùng không đọc bài viết trong một chuyên mục quá 7 ngày, thanh kỹ năng của chuyên mục đó chuyển sang trạng thái "Bị oxy hóa / Giảm cấp" (Cognitive Decay). Bắn thông báo thông minh: *"Kỹ năng AI của bạn đang giảm 10%! Đọc 1 bài ngay để phục hồi"*. Cung cấp nút "Hồi sinh tức thì bằng 1 Rewarded Video".
- **Tiêu chí chấp nhận (Gherkin):**
  ```gherkin
  Given người dùng bị đứt streak 5 ngày liên tiếp
  When mở app vào ngày thứ 6
  Then hiển thị dialog thông báo mất chuỗi cùng 2 lựa chọn: "Bắt đầu lại từ đầu" hoặc "Xem 1 video ngắn để bảo lưu chuỗi đọc (Streak Shield)"
  And nếu chọn xem video, AdmobApplovinWrapper hiển thị Rewarded Ad thành công thì streak được giữ nguyên vẹn
  ```

---

### 4. `TASK-RPG-04`: Thẻ Báo Cáo Tri Thức Động Hàng Tuần (Weekly "Brain Wrapped" 9:16)
- **ID:** `TASK-RPG-04`
- **Loại:** `Feature` | **Độ ưu tiên:** `P2 (Medium)` | **Story Points:** `8 SP`
- **Mô tả:** Vào mỗi sáng Chủ Nhật, ứng dụng tổng hợp tuần đọc sách thành một slide show 9:16 phong cách Spotify Wrapped: Tổng số từ đã đọc, biểu đồ mạng nhện đa giác (Brain Radar Chart), chủ đề thống trị, danh hiệu đạt được (ví dụ: *"Top 2% AI Researcher"*). Hỗ trợ render ra ảnh bitmap sắc nét kèm logo app và mã QR để chia sẻ 1 chạm lên Instagram Stories, Facebook, X.
- **Tiêu chí chấp nhận (Gherkin):**
  ```gherkin
  Given đến 8:00 sáng Chủ Nhật hàng tuần
  When người dùng mở app
  Then xuất hiện Banner nổi bật "Bản Tóm Tắt Trí Tuệ Tuần Này Của Bạn (Brain Wrapped)"
  When bấm vào xem và chọn "Chia sẻ lên Story"
  Then ứng dụng xuất ảnh Compose canvas 1080x1920 với biểu đồ sắc nét và mở Android Share Sheet
  ```

---

## 📊 Tổng Kết Epic 12
- **Số lượng User Stories:** 4 tasks
- **Tổng Story Points:** 26 SP
- **Tác động:** Tăng tỷ lệ D7 và D30 Retention lên 2.5 lần; tạo động lực nội tại để người dùng chủ động xem Rewarded Ads hàng ngày.
