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
| DJ-05 | Episode Không Được Persist — Mất Nội Dung Khi Process Bị Kill | **P0** | 🆕 Todo (gap phát hiện qua audit) | [`todo/DJ-05_persist-episode-notification-ready-state.md`](DJ-05_persist-episode-notification-ready-state.md) |
| DJ-06 | "Dual-Voice TTS" Thực Chất Chỉ 1 Giọng Đổi Pitch — Sai Sự Thật So Với Tuyên Bố | P1 | 🆕 Todo (gap phát hiện qua audit) | [`todo/DJ-06_real-dual-voice-tts-or-honest-labeling.md`](DJ-06_real-dual-voice-tts-or-honest-labeling.md) |
| DJ-07 | Thiếu Audio Mixing/Lofi Nền Và Tích Hợp Media3/Android Auto | P2 | 🆕 Todo (gap phát hiện qua audit) | [`todo/DJ-07_lofi-audio-mixing-media3-integration.md`](DJ-07_lofi-audio-mixing-media3-integration.md) |
| DJ-08 | Chọn Nội Dung Chưa Theo Ngân Sách Thời Gian Người Dùng | P2 | 🆕 Todo (gap phát hiện qua audit) | [`todo/DJ-08_time-budget-aware-content-selection.md`](DJ-08_time-budget-aware-content-selection.md) |

- **Completion report gốc (đã bổ sung mục audit):** [`doc/task/done/14_COMMUTECAST_AUTONOMOUS_AI_DJ_DONE.md`](../done/14_COMMUTECAST_AUTONOMOUS_AI_DJ_DONE.md)
- **Commit implement gốc:** `26b07a9` (`feat(commute): implement Epic 14 CommuteCast AI DJ ...`)
- **Commit đánh dấu done (nay đã lỗi thời, xem audit):** `6af4ef9` (`docs(task): mark Epic 14 CommuteCast as completed in doc/task/done`)

## Ghi chú
- Epic gốc trước đây gộp cả 4 task vào 1 file duy nhất nằm ở `doc/task/done/14_COMMUTECAST_AUTONOMOUS_AI_DJ_DONE.md` (không có bản riêng trong `todo/`). File này (`todo/14_COMMUTECAST_AUTONOMOUS_AI_DJ.md`) được tạo mới làm Epic Index sau khi tách từng task thành file riêng theo `doc/task/_TEMPLATE_TASK.md`.
- DJ-01 và DJ-02/DJ-03 vẫn nằm ở `todo/` dù đã có implementation một phần, vì chưa đáp ứng đủ Acceptance Criteria gốc — xem audit note trong từng file task.
- DJ-04 là task duy nhất trong 4 task gốc được xác nhận đạt chất lượng "done" thực sự qua audit code, nên đặt tại `done/`.
