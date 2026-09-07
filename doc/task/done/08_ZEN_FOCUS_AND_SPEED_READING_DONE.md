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

---

## ⚠️ Audit lại (phát hiện sau khi DONE)

> **Ngày audit:** 2026-09-06. Epic này được implement ở commit `a129f71` (`feat(zen): implement Epic 08 RSVP Speed Reading, Zen Focus Audio Synthesizer, and Scheduled Daily Edition`) và ban đầu được coi là hoàn thành 100% cho cả 3 task ZEN-01/02/03. Audit code thực tế (đọc trực tiếp source, không dựa vào mô tả) phát hiện **3 lỗi logic khiến các nhánh Acceptance Criteria quan trọng nhất của mỗi task KHÔNG hoạt động đúng trong thực tế**, dù test đi kèm (`RsvpTokenizerTest`, `ZenSpeedReadingIntegrationTest`) đều pass — vì test không cover đúng các nhánh lỗi này. Đã tách file task riêng cho từng task cũ (`ZEN-01`, `ZEN-02`, `ZEN-03` trong `doc/task/todo/`) và tạo 3 task fix mới `ZEN-04`, `ZEN-05`, `ZEN-06`.

### Điểm đánh giá lại (khách quan, thay thế điểm tự chấm ban đầu nếu có)

| Task | Điểm tự chấm ban đầu (suy luận từ trạng thái DONE) | Điểm thực tế sau audit | Lý do |
|---|---|---|---|
| ZEN-01 (RSVP) | ~9-10/10 (đã move sang done) | **6/10** | ORP, WPM tùy chỉnh, delay theo dấu câu hoạt động đúng. Nhưng nhánh "tạm dừng lâu hơn ở cuối đoạn văn" — một Acceptance Criteria rõ ràng — hoàn toàn chết logic do lỗi thứ tự xử lý trong `RsvpTokenizer.cleanHtml()`/`tokenize()` (`isParagraphBreak` không bao giờ `true`). Xem [ZEN-04](../todo/ZEN-04_rsvp-paragraph-pause-fix.md). |
| ZEN-02 (Ambient Soundscapes) | ~9-10/10 (đã move sang done) | **6.5/10** | Synthesizer tổng hợp 5 loại âm thanh runtime hoạt động, có volume slider, sleep timer, audio ducking khi mất focus tạm thời — thực chất vượt kỳ vọng về mặt kỹ thuật (0 asset, không cần OPUS file như đặc tả gốc, nhưng đây là lệch đặc tả có chủ đích tốt). Tuy nhiên có 2 lỗi đồng bộ trạng thái: bỏ qua kết quả `requestAudioFocus()`, và `isPlaying` không phản ánh đúng khi AudioTrack write lỗi khiến thread dừng ngầm — UI có thể hiển thị sai trạng thái "đang phát". Xem [ZEN-06](../todo/ZEN-06_zen-audio-state-sync-fix.md). |
| ZEN-03 (Scheduled Daily Edition) | ~9-10/10 (đã move sang done) | **4/10** | Đây là lỗi nghiêm trọng nhất: phần cốt lõi của đặc tả — "đúng 07:00 và 20:00 theo giờ người dùng chọn" — không hề hoạt động. `ZenDailyEditionManager` có `StateFlow` `morningTime`/`eveningTime` nhưng KHÔNG có setter nào ghi giá trị mới; `DailyEditionWorker.enqueueDailyWork()` chỉ enqueue `PeriodicWorkRequestBuilder(12, TimeUnit.HOURS)` chạy lặp mỗi 12 giờ kể từ lúc bật tính năng, hoàn toàn không neo theo giờ trong ngày. Người dùng không có cách nào thực sự đổi giờ, và giờ chạy thực tế ngẫu nhiên theo thời điểm bật app. Xem [ZEN-05](../todo/ZEN-05_daily-edition-schedule-time-fix.md). |
| **Epic 08 tổng thể** | Coi như DONE hoàn toàn | **~5.5/10** | Khung sườn kỹ thuật (3 package mới, UI, DI, notification, worker) đầy đủ và biên dịch được, nhưng cả 3 nhánh hành vi "lõi" nhất của từng task (paragraph pause, đúng giờ đã chọn, đồng bộ trạng thái phát) đều sai. Epic không nên được coi là hoàn thành tới khi ZEN-04/05/06 được fix và có test cover đúng các nhánh này. |

### Gap chi tiết (đối chiếu code, không suy đoán)

1. **[ZEN-04] RSVP paragraph-pause chết logic** — `app/src/main/java/com/mckimquyen/reader/ui/page/rsvp/RsvpTokenizer.kt`: `cleanHtml()` collapse `\s+` (bao gồm `\n\n`) thành 1 space TRƯỚC KHI `tokenize()` cố `split("\n\n", "\r\n\r\n")`, khiến `paragraphs.size` luôn = 1 và `isParagraphBreak`/`isParagraphEnd` không bao giờ `true`. Task fix: [`doc/task/todo/ZEN-04_rsvp-paragraph-pause-fix.md`](../todo/ZEN-04_rsvp-paragraph-pause-fix.md).
2. **[ZEN-05] Lịch Zen Daily Edition sai giờ đã chọn** — `app/src/main/java/com/mckimquyen/reader/domain/zen/ZenDailyEditionManager.kt` không có setter cho `morningTime`/`eveningTime`; `app/src/main/java/com/mckimquyen/reader/domain/sv/DailyEditionWorker.kt` (`enqueueDailyWork()`) dùng `PeriodicWorkRequestBuilder(12, TimeUnit.HOURS)` cố định, không đọc giờ user cấu hình. Task fix: [`doc/task/todo/ZEN-05_daily-edition-schedule-time-fix.md`](../todo/ZEN-05_daily-edition-schedule-time-fix.md).
3. **[ZEN-06] Zen Audio không đồng bộ trạng thái thật** — `app/src/main/java/com/mckimquyen/reader/infrastructure/audio/ambient/ZenAudioManager.kt` (`play()`) bỏ qua giá trị trả về của `requestAudioFocus()`; `ZenSoundSynthesizer.kt` (`runSynthesisLoop()`) không set `isPlaying = false` khi `audioTrack.write()` lỗi khiến vòng lặp `break`, và không có callback nào đồng bộ ngược lên `ZenAudioManager._isPlaying`. Task fix: [`doc/task/todo/ZEN-06_zen-audio-state-sync-fix.md`](../todo/ZEN-06_zen-audio-state-sync-fix.md).

### Task mới liên quan (ý tưởng mở rộng, không phải bug)
- [`doc/task/todo/ZEN-07_adaptive-rsvp-trainer.md`](../todo/ZEN-07_adaptive-rsvp-trainer.md) — Adaptive RSVP Trainer.
- [`doc/task/todo/ZEN-08_cross-mode-reading-handoff.md`](../todo/ZEN-08_cross-mode-reading-handoff.md) — Cross-mode Reading Handoff.

### Kết luận
Epic 08 KHÔNG nên coi là "hoàn thành" theo đúng Definition of Done (`doc/task/README.md`) cho tới khi ZEN-04, ZEN-05, ZEN-06 được fix, có test cover đúng nhánh lỗi, và audit lại đạt > 9/10 theo quy trình End-Loop Signal chuẩn. File epic index đã được cập nhật tại [`doc/task/todo/08_ZEN_FOCUS_AND_SPEED_READING.md`](../todo/08_ZEN_FOCUS_AND_SPEED_READING.md) để phản ánh đúng trạng thái từng task con.
