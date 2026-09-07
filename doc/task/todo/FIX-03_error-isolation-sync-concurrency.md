# [FIX-03] Bọc Error Isolation và điều chỉnh Concurrency khi đồng bộ RSS Feed

- **Type:** Crash / Reliability Bug
- **Priority:** `P0 (Blocker)`
- **Estimation:** `3 Story Points`
- **Epic:** 01. FIX — Sửa Lỗi, Ổn Định & Ad Unit
- **Location:** [`domain/sv/AbstractRssRepository.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/domain/sv/AbstractRssRepository.kt#L83-L96)

## Vấn đề thực tế
`AbstractRssRepository.sync()` chia nhóm `chunked(16)` và chạy 16 coroutine song song. Nếu chỉ 1 feed bị lỗi (500, SSL Handshake, Timeout, XML lỗi định dạng), `awaitAll()` ném exception làm hủy toàn bộ 15 feed còn lại và làm `SyncWorker` thất bại giữa chừng. Đồng thời 16 kết nối đồng thời dễ bị web server chặn IP (Rate Limit 429).

## User Story
> Là người dùng đã đăng ký 50+ nguồn tin,
> Tôi muốn quá trình đồng bộ tiếp tục hoàn tất các nguồn bình thường kể cả khi có 1-2 website nguồn bị sập,
> Để tôi không bị gián đoạn đọc tin tức.

## Acceptance Criteria
- **Given** người dùng kích hoạt đồng bộ nền
- **When** một nguồn tin trả về HTTP 500 hoặc rớt mạng
- **Then** hàm `syncFeed(feed)` bắt gọn lỗi bằng `runCatching`, ghi log cảnh báo và trả về `Result.failure`
- **And** các feed khác trong danh sách vẫn được tải và lưu vào Room bình thường
- **And** giảm chunk concurrency xuống 6 kết nối song song để tránh bị cloudflare/server chặn.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [FIX-03] "Bọc Error Isolation và điều chỉnh Concurrency khi đồng bộ RSS Feed" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/FIX-03_error-isolation-sync-concurrency.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định) — kiểm tra chunked(N) hiện là bao nhiêu, có runCatching quanh syncFeed chưa.
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task FIX-03 trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task FIX-03 trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
4. Build kiểm tra: `./gradlew assembleDevDebug` (hoặc `lintDevDebug` nếu chỉ đổi resource/string).
5. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.
```

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm, nếu có test giả/mock rỗng/logic chưa đúng thì điểm phải phản ánh đúng thực tế.
2. Bổ sung **unit test** cho mọi nhánh logic mới (`app/src/test/...`), phủ cả edge case (rỗng, lỗi mạng, dữ liệu null, giới hạn biên) — ví dụ 1 feed lỗi 500 không làm hỏng các feed khác trong batch.
3. Bổ sung **widget/Compose UI test** cho mọi component UI mới hoặc thay đổi hành vi UI (`app/src/androidTest/...`).
4. Bổ sung **integration test** cho luồng end-to-end liên quan (DB + repository + worker nếu có liên quan) — test `SyncWorker` với danh sách feed hỗn hợp thành công/lỗi.
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, thao tác thủ công đúng luồng vừa sửa, ghi lại bằng chứng cụ thể (log logcat hoặc mô tả kết quả quan sát được) chứng minh hoạt động đúng — không suy đoán, không báo cáo khống.
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` hoặc `inprogress/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.

---
> **Ghi chú audit (2026-09-06):** Đã xác minh code hiện tại — `AbstractRssRepository.kt` đã dùng `.chunked(6)` (dòng 85) và `runCatching` (dòng 152). Task này **có vẻ đã được implement** (khớp báo cáo `doc/task/done/01_FOUNDATION_STABILITY_DONE.md`, FIX-03). Trước khi chạy loop, xác nhận lại toàn bộ Acceptance Criteria + test tương ứng đã tồn tại; nếu đạt, di chuyển file này sang `doc/task/done/` thay vì implement lại. Lưu ý: `git status` hiện có thay đổi chưa commit ở `AbstractRssRepository.kt` và `LocalRssSv.kt` — kiểm tra kỹ `git diff` trước khi kết luận.
