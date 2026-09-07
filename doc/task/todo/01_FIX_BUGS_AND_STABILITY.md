# 🛠️ Epic 01: Sửa Lỗi & Củng Cố Độ Ổn Định (Fix Bugs & Stability) — Epic Index

> **Mục tiêu Epic:** Loại bỏ hoàn toàn các nguy cơ crash ngầm, memory leak, race condition, rò rỉ dữ liệu AI sai bài, rủi ro bảo mật key AI, tối ưu tốc độ truy vấn SQLite/Room, tuân thủ chặt chẽ chính sách Google Play Store, và dọn dẹp nợ kỹ thuật (dead code/TODO/hardcode string).
>
> Mỗi task đã được tách thành 1 file riêng theo `doc/task/_TEMPLATE_TASK.md`. File này chỉ còn đóng vai trò **mục lục (index)** — xem chi tiết Vấn đề thực tế / User Story / Acceptance Criteria / Loop Prompt / End-Signal trong từng file con.

---

## 📋 Danh sách Task (FIX-01 → FIX-15)

| Task ID | Tên | Priority | Status | File |
|---|---|:---:|:---:|---|
| FIX-01 | Composite Indexes trên bảng `article` trong Room Database | P0 | todo* | [`FIX-01_composite-indexes-article-table.md`](FIX-01_composite-indexes-article-table.md) |
| FIX-02 | Loại bỏ Compose `LazyListState` khỏi ViewModel & UiState | P0 | todo* | [`FIX-02_remove-lazyliststate-viewmodel.md`](FIX-02_remove-lazyliststate-viewmodel.md) |
| FIX-03 | Bọc Error Isolation và điều chỉnh Concurrency khi đồng bộ RSS Feed | P0 | todo* | [`FIX-03_error-isolation-sync-concurrency.md`](FIX-03_error-isolation-sync-concurrency.md) |
| FIX-04 | Thay thế Endpoint Favicon Heroku đã chết & Sửa lỗi `NoSuchElementException` | P0 | todo* | [`FIX-04_replace-favicon-heroku-endpoint.md`](FIX-04_replace-favicon-heroku-endpoint.md) |
| FIX-05 | Đẩy tác vụ parse HTML của AI Summary và TTS sang Worker Thread | P1 | todo | [`FIX-05_html-parsing-worker-thread.md`](FIX-05_html-parsing-worker-thread.md) |
| FIX-06 | Loại bỏ Force-Null `!!` gây rủi ro NPE ở `FeverRssSv` và `OpmlSv` | P1 | todo | [`FIX-06_force-null-npe-fever-opml.md`](FIX-06_force-null-npe-fever-opml.md) |
| FIX-07 | Kiểm tra Runtime Permission `POST_NOTIFICATIONS` trên Android 13+ (API 33+) | P1 | todo | [`FIX-07_post-notifications-permission.md`](FIX-07_post-notifications-permission.md) |
| FIX-08 | Sửa AdMob Rewarded Ad Unit ID Test trên Release & Tối Ưu Ad Lifecycle | P1 | todo | [`FIX-08_admob-rewarded-test-id-release.md`](FIX-08_admob-rewarded-test-id-release.md) |
| FIX-09 | Gemini API Key Hardcode Trong APK (Chỉ Obfuscate, Không Bảo Mật Thật) | P0, security | todo | [`FIX-09_gemini-key-hardcoded.md`](FIX-09_gemini-key-hardcoded.md) |
| FIX-10 | Kết quả AI Summary/Deep Read/Mind Map Có Thể Rơi Nhầm Sang Bài Khác | P0 | todo | [`FIX-10_ai-result-leak-wrong-article.md`](FIX-10_ai-result-leak-wrong-article.md) |
| FIX-11 | Race Condition Khi Tìm Kiếm Trong `HomeViewModel` | P0 | todo | [`FIX-11_search-race-condition-homeviewmodel.md`](FIX-11_search-race-condition-homeviewmodel.md) |
| FIX-12 | I/O Đồng Bộ Trong `init{}` Block Vi Phạm DoD "Không I/O Trên Main Thread" | P1 | todo | [`FIX-12_main-thread-io-init-blocks.md`](FIX-12_main-thread-io-init-blocks.md) |
| FIX-13 | Dead Code: Hàm `callGemini()` Không Còn Ai Gọi Trong `GeminiSummaryService` | P2, code quality | todo | [`FIX-13_dead-code-callgemini.md`](FIX-13_dead-code-callgemini.md) |
| FIX-14 | Dọn Dẹp TODO Rải Rác & Tab Chết Không Có Hành Vi | P2 | todo | [`FIX-14_dead-todo-ui-cleanup.md`](FIX-14_dead-todo-ui-cleanup.md) |
| FIX-15 | Hardcode Tiếng Anh "Pillar"/"Detail" Trong `MindMapSheet` — Vi Phạm Quy Tắc Localize | P2, localization | todo | [`FIX-15_mindmap-hardcoded-strings.md`](FIX-15_mindmap-hardcoded-strings.md) |

`*` FIX-01 → FIX-04 đã có dấu hiệu **implement xong trong code hiện tại** theo audit ngày 2026-09-06 (khớp với `doc/task/done/01_FOUNDATION_STABILITY_DONE.md`) — mỗi file con tương ứng đã có ghi chú "Ghi chú audit" ở cuối, cần xác nhận lại đầy đủ Acceptance Criteria + test trước khi chính thức chuyển sang `doc/task/done/`. FIX-05 → FIX-15 xác nhận **còn tồn tại** trong code tính đến thời điểm audit.

---

## Ghi chú audit tổng quan (2026-09-06)

- FIX-09 → FIX-15 là các task **mới phát hiện** qua audit code thực tế (không có trong bản epic gốc trước đó), bổ sung theo yêu cầu rà soát mở rộng phạm vi: bảo mật key AI, race condition AI/search, vi phạm DoD I/O-on-Main, dead code, dead UI, và thiếu localize.
- Số liệu tổng SP của epic **không được cập nhật tự động** ở đây — theo quy tắc trong `_TEMPLATE_TASK.md`, `doc/task/README.md` sẽ được cập nhật tập trung riêng, không tự ý sửa từ epic index này.
