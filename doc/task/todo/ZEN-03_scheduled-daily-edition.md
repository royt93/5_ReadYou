# [ZEN-03] Phát Hành Tạp Chí Định Giờ (Scheduled Daily Edition / Anti-Distraction)

- **Type:** Notification / Digital Wellbeing
- **Priority:** `P2 (Medium)`
- **Estimation:** `3 Story Points`
- **Epic:** [08. ZEN — Đọc Siêu Tốc RSVP & Tập Trung Tuyệt Đối](08_ZEN_FOCUS_AND_SPEED_READING.md)
- **Location:** [`infrastructure/android/NotificationHelper.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/android/NotificationHelper.kt), [`domain/sv/DailyEditionWorker.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/domain/sv/DailyEditionWorker.kt), [`domain/zen/ZenDailyEditionManager.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/domain/zen/ZenDailyEditionManager.kt)

## Vấn đề thực tế
Nhận thông báo tinh tinh liên tục mỗi khi có bài viết mới làm đứt gãy sự tập trung làm việc của người dùng trong ngày.

> **Ghi chú audit (2026-09-06):** Task này ĐÃ được implement (xem `DailyEditionWorker.kt`, `ZenDailyEditionManager.kt`, `NotificationHelper.kt`, commit `a129f71`) và ban đầu được tuyên bố DONE trong `doc/task/done/08_ZEN_FOCUS_AND_SPEED_READING_DONE.md`. Audit lại phát hiện phần quan trọng nhất của đặc tả — "đúng 07:00 và 20:00" theo giờ **người dùng chọn** — KHÔNG được implement: `ZenDailyEditionManager` expose `morningTime`/`eveningTime` nhưng không có setter thực sự, và `DailyEditionWorker.enqueueDailyWork()` chỉ lên lịch periodic cố định mỗi 12 giờ kể từ lúc bật, không đọc giờ đã cấu hình. Xem task fix riêng **[ZEN-05]**.

## User Story
> Là người coi trọng sự tập trung trong giờ làm việc,
> Tôi muốn ứng dụng gom toàn bộ bài viết trong ngày và chỉ thông báo duy nhất 1-2 lần vào khung giờ tôi chọn (ví dụ: 7:00 sáng và 20:00 tối),
> Để tôi không bị thông báo quấy rầy suốt cả ngày.

## Acceptance Criteria (Gherkin)
- **Given** tùy chọn "Bản Tin Định Giờ" trong Settings
- **When** người dùng chọn giờ phát hành: 07:00 và 20:00
- **Then** app tắt toàn bộ thông báo lẻ tẻ trong ngày
- **And** đúng 07:00 và 20:00, WorkManager gửi 1 thông báo tổng hợp duy nhất: "📰 Ấn phẩm buổi sáng: 24 bài viết mới đang chờ bạn"
- **And** bấm vào thông báo mở thẳng danh mục bài viết nổi bật của ấn phẩm đó.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [ZEN-03] "Phát Hành Tạp Chí Định Giờ (Scheduled Daily Edition / Anti-Distraction)" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/ZEN-03_scheduled-daily-edition.md trước khi bắt đầu.

LƯU Ý: task này đã có implementation một phần (`DailyEditionWorker.kt`, `ZenDailyEditionManager.kt`) nhưng KHÔNG lên lịch đúng giờ user chọn — bug này có task fix riêng [ZEN-05]. Kiểm tra trạng thái ZEN-05 trước khi bắt đầu để tránh làm trùng; nếu ZEN-05 đã fix xong, chỉ còn việc rà soát các nhánh Acceptance Criteria còn thiếu (VD: tắt thông báo lẻ tẻ, deep-link mở đúng danh mục bài nổi bật).

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [ZEN-03] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [ZEN-03] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
4. Build kiểm tra: `./gradlew assembleDevDebug` (hoặc `lintDevDebug` nếu chỉ đổi resource/string).
5. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.
```

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm, nếu có test giả/mock rỗng/logic chưa đúng thì điểm phải phản ánh đúng thực tế.
2. Bổ sung **unit test** cho mọi nhánh logic mới (`app/src/test/...`), phủ cả edge case (rỗng, lỗi mạng, dữ liệu null, giới hạn biên).
3. Bổ sung **widget/Compose UI test** cho mọi component UI mới hoặc thay đổi hành vi UI (`app/src/androidTest/...`).
4. Bổ sung **integration test** cho luồng end-to-end liên quan (DB + repository + worker nếu có liên quan).
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, thao tác thủ công đúng luồng vừa sửa, ghi lại bằng chứng cụ thể (log logcat hoặc mô tả kết quả quan sát được) chứng minh hoạt động đúng — không suy đoán, không báo cáo khống.
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` hoặc `inprogress/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.
