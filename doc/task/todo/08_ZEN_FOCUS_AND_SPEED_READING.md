# 🧘 Epic 08: Đọc Siêu Tốc & Tập Trung Tuyệt Đối (Zen Focus & Speed Reading)

> **Mục tiêu Epic:** Mang lại không gian đọc sách tĩnh tại, loại bỏ hoàn toàn sự xao nhãng từ thông báo và hỗ trợ phương pháp đọc chớp mắt siêu tốc.

Đây là **Epic Index** — mục lục các task con của Epic 08. Nội dung chi tiết từng task (Vấn đề thực tế / User Story / Acceptance Criteria / Loop Prompt / End-Loop Signal) nằm trong file riêng của từng task, theo đúng `doc/task/_TEMPLATE_TASK.md`.

> **Lịch sử:** 3 task gốc (ZEN-01, ZEN-02, ZEN-03) được implement ở commit `a129f71` và ban đầu bị move thẳng sang `doc/task/done/08_ZEN_FOCUS_AND_SPEED_READING_DONE.md` coi là hoàn thành 100%, không tách file riêng, không có Completion Report. Ngày 2026-09-06, audit lại phát hiện cả 3 task đều có bug nghiêm trọng ở đúng nhánh Acceptance Criteria cốt lõi (xem mục "⚠️ Audit lại (phát hiện sau khi DONE)" trong [`08_ZEN_FOCUS_AND_SPEED_READING_DONE.md`](../done/08_ZEN_FOCUS_AND_SPEED_READING_DONE.md)). Epic được tách lại thành các file task riêng ở đây, kèm 3 task fix (ZEN-04..06) và 2 task ý tưởng mới (ZEN-07..08).

## Danh sách task

| ID | Tên | Priority | Status | File |
|---|---|---|---|---|
| ZEN-01 | Chế Độ Đọc Chớp Mắt Siêu Tốc RSVP (RSVP) | P2 (Medium) | 🟡 Implemented nhưng có bug con (xem ZEN-04) | [`ZEN-01_rsvp-speed-reading.md`](ZEN-01_rsvp-speed-reading.md) |
| ZEN-02 | Không Gian Âm Thanh Nền Tập Trung (Ambient Soundscapes) | P3 (Low) | 🟡 Implemented nhưng có bug con (xem ZEN-06) | [`ZEN-02_ambient-soundscapes.md`](ZEN-02_ambient-soundscapes.md) |
| ZEN-03 | Phát Hành Tạp Chí Định Giờ (Scheduled Daily Edition) | P2 (Medium) | 🟡 Implemented nhưng có bug con nghiêm trọng (xem ZEN-05) | [`ZEN-03_scheduled-daily-edition.md`](ZEN-03_scheduled-daily-edition.md) |
| ZEN-04 | RSVP paragraph-pause chết logic | P1 (High) | 📋 Todo (bug mới phát hiện qua audit) | [`ZEN-04_rsvp-paragraph-pause-fix.md`](ZEN-04_rsvp-paragraph-pause-fix.md) |
| ZEN-05 | Lịch Zen Daily Edition sai giờ đã chọn | P1 (High) | 📋 Todo (bug mới phát hiện qua audit) | [`ZEN-05_daily-edition-schedule-time-fix.md`](ZEN-05_daily-edition-schedule-time-fix.md) |
| ZEN-06 | Zen Audio không đồng bộ trạng thái thật | P2 (Medium) | 📋 Todo (bug mới phát hiện qua audit) | [`ZEN-06_zen-audio-state-sync-fix.md`](ZEN-06_zen-audio-state-sync-fix.md) |
| ZEN-07 | Adaptive RSVP Trainer | P2 (Medium) | 💭 Idea mới | [`ZEN-07_adaptive-rsvp-trainer.md`](ZEN-07_adaptive-rsvp-trainer.md) |
| ZEN-08 | Cross-mode Reading Handoff | P2 (Medium) | 💭 Idea mới | [`ZEN-08_cross-mode-reading-handoff.md`](ZEN-08_cross-mode-reading-handoff.md) |

## Tham chiếu
- Completion report gốc (đã bổ sung audit): [`doc/task/done/08_ZEN_FOCUS_AND_SPEED_READING_DONE.md`](../done/08_ZEN_FOCUS_AND_SPEED_READING_DONE.md)
- Template chuẩn task: [`doc/task/_TEMPLATE_TASK.md`](../_TEMPLATE_TASK.md)
