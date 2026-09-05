# 🧘 Epic 08: Đọc Siêu Tốc & Tập Trung Tuyệt Đối (Zen Focus & Speed Reading)

> **Mục tiêu Epic:** Mang lại không gian đọc sách tĩnh tại, loại bỏ hoàn toàn sự xao nhãng từ thông báo và hỗ trợ phương pháp đọc chớp mắt siêu tốc.

---

### [ZEN-01] Chế Độ Đọc Chớp Mắt Siêu Tốc RSVP (Rapid Serial Visual Presentation)
- **Type:** Cognitive UX / Speed Reading
- **Priority:** `P2 (Medium)`
- **Estimation:** `5 Story Points`
- **Location:** `app/src/main/java/com/mckimquyen/reader/ui/page/rsvp/` (Gói mới)
- **Bối cảnh:** Mắt người khi đọc sách thông thường phải liên tục đảo qua lại giữa các dòng (saccades), làm chậm tốc độ đọc (trung bình 200-250 từ/phút) và nhanh mỏi mắt. Công nghệ RSVP (tương tự Spritz) nhấp nháy từng từ tại một tiêu điểm cố định.
- **User Story:**
  > Là người cần đọc lướt nhanh bài báo 3,000 từ trong vòng 3 phút giờ giải lao,  
  > Tôi muốn mở chế độ đọc RSVP để mắt nhìn vào một điểm duy nhất và đọc với tốc độ 500-800 từ/phút,  
  > Để tôi tiết kiệm thời gian đọc mà vẫn nắm bắt trọn vẹn thông điệp.
- **Acceptance Criteria:**
  - **Given** người dùng đang ở bài viết trong `ReadingPage`
  - **When** bấm nút "⚡ Đọc Siêu Tốc (RSVP)"
  - **Then** màn hình hiển thị hộp chữ tiêu điểm cố định làm nổi bật chữ cái tâm (Optimal Recognition Point - ORP) bằng màu đỏ
  - **And** các từ lướt qua với tốc độ tùy chỉnh từ 250 đến 900 từ/phút (WPM)
  - **And** tự động tạm dừng nhẹ ở các dấu chấm, dấu phẩy để não bộ kịp xử lý thông tin.

---

### [ZEN-02] Không Gian Âm Thanh Nền Tập Trung (Zen Focus & Ambient Soundscapes)
- **Type:** Audio / Mindfulness UX
- **Priority:** `P3 (Low)`
- **Estimation:** `3 Story Points`
- **Location:** `app/src/main/java/com/mckimquyen/reader/infrastructure/audio/ambient/` (Gói mới)
- **Bối cảnh:** Môi trường xung quanh (quán cà phê ồn, văn phòng, tiếng còi xe) thường làm người đọc phân tâm. Nghe nhạc có lời lại gây xao nhãng việc hiểu văn bản.
- **User Story:**
  > Là người thích đọc sách trong không gian tĩnh lặng,  
  > Tôi muốn bật âm thanh nền thư giãn (tiếng mưa rơi, tiếng lò sưởi, tiếng sóng biển, Lofi) khi đọc bài,  
  > Để tôi chìm đắm hoàn toàn vào dòng suy nghĩ và đọc tập trung hơn.
- **Acceptance Criteria:**
  - **Given** người dùng mở bài đọc
  - **When** bật icon "🎧 Zen Audio" trên thanh công cụ
  - **Then** phát âm thanh nền vòng lặp chất lượng cao (Mưa rào, Quán cà phê Paris, Sóng biển đêm, Tiếng lửa bập bùng, Tiếng ồn trắng)
  - **And** có thanh trượt điều chỉnh âm lượng riêng biệt không ảnh hưởng tới âm lượng hệ thống
  - **And** file âm thanh được nén tối ưu (OPUS format) chiếm < 2MB dung lượng app.

---

### [ZEN-03] Phát Hành Tạp Chí Định Giờ (Scheduled Daily Edition / Anti-Distraction)
- **Type:** Notification / Digital Wellbeing
- **Priority:** `P2 (Medium)`
- **Estimation:** `3 Story Points`
- **Location:** [`infrastructure/android/NotificationHelper.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/android/NotificationHelper.kt), [`domain/sv/SyncWorker.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/domain/sv/SyncWorker.kt)
- **Bối cảnh:** Nhận thông báo tinh tinh liên tục mỗi khi có bài viết mới làm đứt gãy sự tập trung làm việc của người dùng trong ngày.
- **User Story:**
  > Là người coi trọng sự tập trung trong giờ làm việc,  
  > Tôi muốn ứng dụng gom toàn bộ bài viết trong ngày và chỉ thông báo duy nhất 1-2 lần vào khung giờ tôi chọn (ví dụ: 7:00 sáng và 20:00 tối),  
  > Để tôi không bị thông báo quấy rầy suốt cả ngày.
- **Acceptance Criteria:**
  - **Given** tùy chọn "Bản Tin Định Giờ" trong Settings
  - **When** người dùng chọn giờ phát hành: 07:00 và 20:00
  - **Then** app tắt toàn bộ thông báo lẻ tẻ trong ngày
  - **And** đúng 07:00 và 20:00, WorkManager gửi 1 thông báo tổng hợp duy nhất: "📰 Ấn phẩm buổi sáng: 24 bài viết mới đang chờ bạn"
  - **And** bấm vào thông báo mở thẳng danh mục bài viết nổi bật của ấn phẩm đó.
