# 🎬 Epic 09: Tin Tức Thị Giác & Cảnh Báo Khẩn (Visual Reels & Media) — Epic Index

> **Mục tiêu Epic:** Trẻ hóa trải nghiệm đọc tin bằng định dạng thẻ lướt dọc trực quan (News Reels / Story), tích hợp xem video trong app không quảng cáo và hệ thống cảnh báo khẩn cấp theo từ khóa trọng yếu.

Epic này đã được tách thành các file task riêng theo `doc/task/_TEMPLATE_TASK.md`. File này chỉ còn là mục lục — xem chi tiết từng task ở file tương ứng.

## Danh sách task

| Task | Tiêu đề | Priority | Trạng thái | File |
|---|---|---|---|---|
| REEL-01 | Giao Diện Thẻ Lướt Dọc "News Reels" Dạng TikTok / Instagram Story | P2 | 📋 Todo | [`todo/REEL-01_news-reels-tiktok-scroll.md`](REEL-01_news-reels-tiktok-scroll.md) |
| REEL-02 | Trình Xem Video RSS & YouTube PiP Không Quảng Cáo | P2 | 📋 Todo | [`todo/REEL-02_youtube-video-pip.md`](REEL-02_youtube-video-pip.md) |
| REEL-03 | Chó Săn Cảnh Báo Từ Khóa Khẩn Cấp (Keyword Alert Watchdog) | P1 | ✅ Done (commit `f366bb9`) | [`done/REEL-03_watchdog-keyword-alert_DONE.md`](../done/REEL-03_watchdog-keyword-alert_DONE.md) |
| REEL-04 | Watchdog Persistence Chưa Atomic (race condition + mất dữ liệu khi lỗi parse) | P1 | 📋 Todo | [`todo/REEL-04_watchdog-atomic-persistence.md`](REEL-04_watchdog-atomic-persistence.md) |
| REEL-05 | Watchdog Matcher Theo Batch, Tối Ưu Hiệu Năng | P2 | 📋 Todo | [`todo/REEL-05_watchdog-batch-matching-perf.md`](REEL-05_watchdog-batch-matching-perf.md) |
| REEL-06 | Watchdog Alert Inbox — Màn Hình Lịch Sử Cảnh Báo Từ Khóa | P2 | 📋 Todo | [`todo/REEL-06_watchdog-alert-inbox.md`](REEL-06_watchdog-alert-inbox.md) |

## Ghi chú audit (2026-09-06)

- **REEL-03** đã được implement đầy đủ và commit (`f366bb9`), bao gồm engine matching, persistence, notification `IMPORTANCE_HIGH`, badge đỏ trên article card, sheet quản lý từ khóa, và test 3 tầng (unit/widget/integration). Chi tiết Completion Report + điểm audit khách quan: [`doc/task/done/REEL-03_watchdog-keyword-alert_DONE.md`](../done/REEL-03_watchdog-keyword-alert_DONE.md).
- **REEL-01** và **REEL-02** vẫn chưa có bất kỳ implementation nào trong code (xác nhận qua tìm kiếm toàn repo) — giữ nguyên nội dung gốc, bổ sung Loop Prompt để thực thi sau.
- **REEL-04, REEL-05, REEL-06** là 3 task mới phát sinh từ audit REEL-03: một lỗ hổng data-integrity ưu tiên cao (REEL-04), một tối ưu hiệu năng (REEL-05), và một tính năng mở rộng UX (REEL-06 — Alert Inbox với excerpt/snooze/quiet-hours).
