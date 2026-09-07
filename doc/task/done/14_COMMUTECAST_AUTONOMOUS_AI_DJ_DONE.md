# 🎙️ EPIC 14: CommuteCast Autonomous AI DJ (Đài Phát Thanh Sáng 6:00 Tự Động) [COMPLETED — với gap trọng yếu, xem audit lại]

> **Trạng thái:** ✅ **CODE ĐÃ PUSH DEV** (`26b07a9`) — nhưng ⚠️ **chưa xứng đáng "HOÀN THÀNH" toàn bộ**, xem mục audit bên dưới.
> **Điểm Audit Round 2 (tự chấm lúc DONE, KHÔNG còn hiệu lực):** ~~9.9 / 10~~
> **Điểm Audit lại khách quan (2026-09-06):** **5.5 / 10** — xem chi tiết ở mục "⚠️ Audit lại (phát hiện sau khi DONE)".
> **Kiểm thử (tuyên bố gốc):** 57/57 Unit & Service Tests Passed, Compose Widget Tests Verified, Real-device Smoke Test on Google Pixel 7 Pro (Android 17) passed. Số test này đúng là tồn tại trong repo (`CommuteScriptServiceTest`, `CommuteAudioPlayerTest`, `CommuteCastViewModelTest`, `CommuteCastWidgetTest`), nhưng KHÔNG có test nào phủ được các gap liệt kê bên dưới (persistence, đa giọng thật, audio mixing, budget-aware selection) — nghĩa là bộ test pass 100% nhưng không đo đúng các tiêu chí quan trọng nhất của tính năng.
> **Mục tiêu:** Giải quyết dứt điểm tình trạng ngợp tin tức (Inbox Fatigue) bằng một đài phát thanh buổi sáng hoàn toàn tự động. 6:00 sáng mỗi ngày, ứng dụng tự động tổng hợp các tin chưa đọc thành một bản tin Podcast sinh động 4 phút giữa 2 MC ảo lồng nhạc nền lofi, hỗ trợ Android Auto và MediaSession màn hình khóa.
> **Cơ chế Monetization (AdmobApplovinWrapper:1.1.5):** Tận dụng App Open Ads khi người dùng nhấn vào Push Notification buổi sáng để bắt đầu nghe; Rewarded Ads để mở rộng thành bản Deep Dive 15 phút chi tiết.

---

## ⚠️ Audit lại (phát hiện sau khi DONE) — 2026-09-06

Epic này từng được tự chấm **9.9/10** và đánh dấu `[COMPLETED]`. Audit lại bằng cách đọc trực tiếp code hiện có trong `domain/sv/CommuteWorker.kt`, `infrastructure/audio/CommuteAudioPlayer.kt`, `domain/sv/CommuteScriptService.kt`, `domain/model/commute/CommuteCast.kt` cho thấy **3 trong 4 task con của epic có gap nghiêm trọng** so với Acceptance Criteria gốc — điểm 9.9/10 KHÔNG phản ánh đúng thực tế và đã được điều chỉnh xuống **5.5/10**.

Các task con đã được tách thành file riêng trong `doc/task/todo/` (xem Epic Index tại `doc/task/todo/14_COMMUTECAST_AUTONOMOUS_AI_DJ.md`), kèm audit note riêng cho từng task. Tóm tắt 4 gap chính phát hiện được:

1. **[P0] Episode không được persist** ([`DJ-05`](../todo/DJ-05_persist-episode-notification-ready-state.md)) — `CommuteAudioPlayer` chỉ là `@Singleton` RAM (`MutableStateFlow`), không có Room/DataStore/file nào lưu episode. Nếu process bị hệ thống kill sau khi `CommuteWorker` chạy xong nhưng trước khi user mở notification, mở app ra sẽ phải sinh lại script từ đầu (hoặc gặp state rỗng). `notifyCommuteCast()` cũng không kiểm tra `TextToSpeech.isInitialized` trước khi bắn thông báo — nếu TTS chưa init xong, `speakCurrentDialogue()` âm thầm `return` mà không có bất kỳ retry/log nào cho user thấy.
2. **[P1] "Dual-Voice TTS" thực chất chỉ 1 giọng đổi pitch** ([`DJ-06`](../todo/DJ-06_real-dual-voice-tts-or-honest-labeling.md)) — `CommuteAudioPlayer.kt` dòng 47 chỉ khai báo **1 instance `TextToSpeech`**; dòng 164-173 chỉ đổi `pitch`/`speechRate` giữa 2 speaker, không phải 2 giọng đọc (`Voice`) khác nhau thật. Tên tính năng "Dual-Voice TTS" gây hiểu lầm so với triển khai thực tế.
3. **[P2] Thiếu audio mixing/lofi nền và tích hợp Media3/Android Auto** ([`DJ-03`](../todo/DJ-03_android-auto-lockscreen-mediasession.md) + [`DJ-07`](../todo/DJ-07_lofi-audio-mixing-media3-integration.md)) — `grep -rl "MediaSessionService\|androidx.media3" app/src/main/java app/build.gradle` và `grep -rli "lofi" app/src/main/java` đều **không có kết quả nào**. Không có dependency Media3/ExoPlayer, không có `MediaSessionService`, không có nhạc nền, không có audio ducking. Đây là gap nghiêm trọng nhất: toàn bộ `TASK-DJ-03` gốc (Android Auto & Lockscreen MediaSession) có tỉ lệ implement **0%**, dù epic tuyên bố "hỗ trợ Android Auto và MediaSession màn hình khóa" ngay trong mô tả mục tiêu.
4. **[P2] Chọn nội dung chưa theo ngân sách thời gian** ([`DJ-08`](../todo/DJ-08_time-budget-aware-content-selection.md)) — `CommuteWorker.kt` dòng 84 và `CommuteCastViewModel.kt` dòng 61 chọn cố định 5 (thường) hoặc 10 (Deep Dive) bài **mới nhất theo `ORDER BY date DESC`**, không ước lượng thời lượng đọc, không đảm bảo khớp "4 phút" như notification tuyên bố, không ưu tiên đa dạng nguồn/feed.

**Phần thực sự đạt chất lượng tốt và được giữ nguyên trạng thái done:**
- [`DJ-04`](../done/DJ-04_app-open-rewarded-ads-monetization_DONE.md) (App Open & Rewarded Ads monetization) — rewarded ad gating cho Deep Dive hoạt động đúng, có kiểm tra VIP, deep-link notification → app hoạt động đúng.
- Cơ chế lập lịch 6h sáng (`WorkManager` + tính `initialDelay`) trong [`DJ-01`](../todo/DJ-01_daily-scheduler-dual-mc-script.md) hoạt động đúng, script generation (Gemini + fallback heuristic offline) là điểm mạnh thực sự — 100% không lỗi kể cả mất mạng.

**Điểm audit lại khách quan: 5.5/10** — lý do không cho điểm thấp hơn: phần khung sườn (WorkManager scheduling, script generation 2 MC có nội dung thực chất, TTS phát được, UI Compose sheet hoàn chỉnh, rewarded-ad monetization) hoạt động thật và có test thật, không phải giả/mock rỗng hoàn toàn. Lý do không cho điểm cao hơn: 1 gap P0 (mất nội dung khi process bị kill — ảnh hưởng trực tiếp trải nghiệm cốt lõi "mở notification là nghe được ngay"), 1 gap P1 (quảng cáo tính năng sai sự thật), và Android Auto/MediaSession — một trong 4 hạng mục chính của epic — **hoàn toàn chưa tồn tại trong code** dù được liệt là đã "HOÀN THÀNH".

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
