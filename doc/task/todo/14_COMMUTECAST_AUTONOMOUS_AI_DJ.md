# 🎙️ EPIC 14 — Index: CommuteCast Autonomous AI DJ (Đài Phát Thanh Sáng 6:00 Tự Động)

> **Mục tiêu epic:** Giải quyết tình trạng ngợp tin tức (Inbox Fatigue) bằng một đài phát thanh buổi sáng tự động — 6:00 sáng mỗi ngày, app tổng hợp các tin chưa đọc thành bản tin đối thoại sinh động giữa 2 MC ảo (Alex & Sam), có nhạc nền lofi, hỗ trợ Android Auto/lockscreen, và tối ưu doanh thu qua App Open Ads + Rewarded Ads cho bản Deep Dive.
>
> **Trạng thái thực tế (đã audit lại 2026-09-06):** Code đã được implement và push (`26b07a9`), từng tự chấm 9.9/10 và đánh dấu hoàn thành trong `doc/task/done/14_COMMUTECAST_AUTONOMOUS_AI_DJ_DONE.md`. Audit lại phát hiện **3/4 hạng mục chính có gap nghiêm trọng** (persistence, dual-voice giả, Android Auto/Media3 chưa tồn tại) — điểm đã được điều chỉnh xuống **5.5/10**. Xem đầy đủ lý do trong mục "⚠️ Audit lại (phát hiện sau khi DONE)" của file completion report đó.

## 📋 Danh sách task (4 task gốc + 4 task gap phát hiện qua audit)

| Task | Tiêu đề | Priority | Trạng thái | File |
|---|---|---|---|---|
| DJ-01 | Bộ Lập Lịch Tự Động Buổi Sáng & Kịch Bản 2 MC | P1 | 🟡 Todo (implement phần lớn, thiếu tiêu chí "điểm tương tác") | [`todo/DJ-01_daily-scheduler-dual-mc-script.md`](DJ-01_daily-scheduler-dual-mc-script.md) |
| DJ-02 | Động Cơ Phát Âm 2 Giọng Kèm Nhạc Nền Lofi (Dual-Voice TTS & Audio Mixer) | P1 | 📋 Todo (chưa đạt AC — chỉ 1 giọng đổi pitch, không có lofi) | [`todo/DJ-02_dual-voice-tts-lofi-audio-mixer.md`](DJ-02_dual-voice-tts-lofi-audio-mixer.md) |
| DJ-03 | Tích Hợp Android Auto & Lockscreen MediaSession | P1 | 📋 Todo (0% implement — không có Media3/MediaSessionService nào trong code) | [`todo/DJ-03_android-auto-lockscreen-mediasession.md`](DJ-03_android-auto-lockscreen-mediasession.md) |
| DJ-04 | Tối Ưu Doanh Thu Buổi Sáng Với App Open & Rewarded Ads | P0 | ✅ Done (rewarded ad gating cho Deep Dive + notification deep-link hoạt động đúng) | [`done/DJ-04_app-open-rewarded-ads-monetization_DONE.md`](../done/DJ-04_app-open-rewarded-ads-monetization_DONE.md) |
| DJ-05 | Episode Không Được Persist — Mất Nội Dung Khi Process Bị Kill | **P0** | ✅ Done | [`done/DJ-05_persist-episode-notification-ready-state_DONE.md`](../done/DJ-05_persist-episode-notification-ready-state_DONE.md) |
| DJ-06 | "Dual-Voice TTS" Thực Chất Chỉ 1 Giọng Đổi Pitch — Sai Sự Thật So Với Tuyên Bố | P1 | ✅ Done | [`done/DJ-06_real-dual-voice-tts-or-honest-labeling_DONE.md`](../done/DJ-06_real-dual-voice-tts-or-honest-labeling_DONE.md) |
| DJ-07 | Thiếu Audio Mixing/Lofi Nền Và Tích Hợp Media3/Android Auto | P2 | ✅ Done | [`done/DJ-07_lofi-audio-mixing-media3-integration_DONE.md`](../done/DJ-07_lofi-audio-mixing-media3-integration_DONE.md) |
| DJ-08 | Chọn Nội Dung Chưa Theo Ngân Sách Thời Gian Người Dùng | P2 | 🆕 Todo (gap phát hiện qua audit) | [`todo/DJ-08_time-budget-aware-content-selection.md`](DJ-08_time-budget-aware-content-selection.md) |

- **Completion report gốc (đã bổ sung mục audit):** [`doc/task/done/14_COMMUTECAST_AUTONOMOUS_AI_DJ_DONE.md`](../done/14_COMMUTECAST_AUTONOMOUS_AI_DJ_DONE.md)
- **Commit implement gốc:** `26b07a9` (`feat(commute): implement Epic 14 CommuteCast AI DJ ...`)
- **Commit đánh dấu done (nay đã lỗi thời, xem audit):** `6af4ef9` (`docs(task): mark Epic 14 CommuteCast as completed in doc/task/done`)

## Ghi chú
- Epic gốc trước đây gộp cả 4 task vào 1 file duy nhất nằm ở `doc/task/done/14_COMMUTECAST_AUTONOMOUS_AI_DJ_DONE.md` (không có bản riêng trong `todo/`). File này (`todo/14_COMMUTECAST_AUTONOMOUS_AI_DJ.md`) được tạo mới làm Epic Index sau khi tách từng task thành file riêng theo `doc/task/_TEMPLATE_TASK.md`.
- DJ-01 và DJ-02/DJ-03 vẫn nằm ở `todo/` dù đã có implementation một phần, vì chưa đáp ứng đủ Acceptance Criteria gốc — xem audit note trong từng file task.
- DJ-04 là task duy nhất trong 4 task gốc được xác nhận đạt chất lượng "done" thực sự qua audit code, nên đặt tại `done/`.

## Cập nhật 2026-09-27 — DJ-05 hoàn tất
- **DJ-05 (P0) đã xong.** Episode nay persist qua `CommuteEpisodeStore` (SharedPreferences + JSON, theo tiền lệ `WatchdogManager`, không cần migration Room). Notification chỉ bắn sau khi episode đã lưu, không rỗng, và TTS thật sự sẵn sàng. Smoke test trên Pixel 7 Pro xác nhận `dateMillis` không đổi sau `force-stop` → không gọi lại AI.
- Sửa kèm 4 bug thật ngoài AC: `isPlaying` lạc quan ở 5 hàm; `playEpisode()+pause()` trong Worker phát tiếng rồi tắt; `pause()` không xoá cờ chờ gây tự phát bất ngờ; episode sinh từ UI không được persist.
- Sửa kèm lỗi edge-to-edge: `CommuteCastUi` và `ZenSoundSheet` đổi `navigationBarsPadding()` → `safeDrawingPadding()`.
- **DJ-06 (P1) đã xong.** `CommuteVoiceSelector` chọn 2 voice offline cùng locale của thiết bị khi có đủ (trên Pixel 7 Pro xác nhận thật: `alex=vi-VN-language`, `sam=vi-vn-x-gft-local`, UI hiện "Hai giọng riêng của thiết bị"). Khi chỉ có 1 voice, fallback về đổi pitch nhưng UI nói thật "Chế độ giọng đơn — thiết bị không hỗ trợ đa giọng". Sửa toàn bộ mô tả nam/nữ vô căn cứ vì Android Voice API không có metadata giới tính. Còn lại: DJ-01, DJ-03, DJ-08.

## Cập nhật 2026-09-27 — DJ-07 hoàn tất
- **DJ-07 (P2) đã xong.** Nhạc nền lofi tự sinh (`res/raw/commute_lofi_pad.wav`, CC0, Python stdlib) phát loop qua `CommuteAmbientLoop` (`MediaPlayer`), ducking thật xuống 5% khi MC nói (`handleUtteranceStart`), về 25% giữa các câu (`handleUtteranceDone`). `CommuteMediaSessionService` mới (theo pattern `TtsForegroundService`) cấp `MediaSessionCompat` + notification `MediaStyle` (play/pause/prev/next/stop) cho lockscreen — xác nhận thật trên Pixel 7 Pro qua `dumpsys notification`/`media_session` và gợi ý lockscreen gốc "Xem nội dung nào đang phát".
- **Sửa kèm lỗi edge-to-edge tận gốc** (người dùng yêu cầu giữa loop): mọi `Dialog` toàn màn hình trong app (không riêng CommuteCast) đều bị Compose mặc định `decorFitsSystemWindows=true` giới hạn window trong vùng "stable", khiến `safeDrawingPadding()` luôn đo ra 0. Lập `ui/ext/DialogEdgeToEdge.kt` (`setDecorFitsSystemWindows(false)` + `FLAG_LAYOUT_NO_LIMITS` + `requestApplyInsets`) áp dụng cho cả 5 dialog: `CommuteCastSheet`, `ZenSoundSheet`, `StoryClusterSheet`, `WatchdogSheet`, `RsvpReaderDialog`. Có test `DialogEdgeToEdgeTest` chứng minh trực tiếp cơ chế (không fix: top=0; có fix: top>0 khớp inset thật).
- Epic 14: DJ-04/05/06/07 đã Done. Còn lại: DJ-01 (P1, thiếu tiêu chí điểm tương tác), DJ-03 (P1, Android Auto đầy đủ), DJ-08 (P2, ngân sách thời gian).
