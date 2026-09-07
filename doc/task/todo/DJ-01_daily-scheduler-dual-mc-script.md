# [DJ-01] Bộ Lập Lịch Tự Động Buổi Sáng & Kịch Bản 2 MC (Autonomous 6 AM Scriptwriter)

- **Type:** New Feature
- **Priority:** `P1 (High)`
- **Estimation:** `8 Story Points`
- **Epic:** [14. CommuteCast Autonomous AI DJ](14_COMMUTECAST_AUTONOMOUS_AI_DJ.md)
- **Location:** [`domain/sv/CommuteWorker.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/sv/CommuteWorker.kt), [`domain/sv/CommuteScriptService.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/sv/CommuteScriptService.kt)

## Vấn đề thực tế
Sử dụng Android `WorkManager` (PeriodicWorkRequest kích hoạt lúc 6:00 AM) để quét top 5 bài viết chưa đọc quan trọng nhất trong cơ sở dữ liệu. Prompt AI xử lý tổng hợp thành kịch bản đối thoại hài hước, súc tích giữa 2 nhân vật (Host Alex & Co-Host Sam) dài 4 phút.

> **⚠️ Audit note (2026-09-06):** Task này ĐÃ có implementation thực tế (`CommuteWorker.enqueueDailyWork` tính initial delay tới 6:00 AM, `CommuteScriptService.generateScript` gọi Gemini + fallback heuristic sinh 2 MC) và có unit test (`CommuteScriptServiceTest`), xem completion report `doc/task/done/14_COMMUTECAST_AUTONOMOUS_AI_DJ_DONE.md`. Tuy nhiên còn 2 điểm CHƯA khớp Acceptance Criteria gốc, không được coi là "đóng" hoàn toàn:
> 1. `CommuteWorker.kt` dòng 84 gọi `articleDao.queryLatestUnread(accountId, limit = 5)` — DAO này (`ArticleDao.kt` dòng 586-596) sắp xếp `ORDER BY date DESC`, tức là lấy bài **mới nhất**, KHÔNG phải "5 tin bài có điểm tương tác cao nhất" như Gherkin yêu cầu — hiện không có khái niệm điểm tương tác nào được tính.
> 2. Nội dung episode do worker sinh ra chỉ được lưu vào `CommuteAudioPlayer` singleton RAM (không persist), nên nếu process bị kill trước khi user mở app, notification dẫn tới nội dung trống — xem gap độc lập [`DJ-05`](DJ-05_persist-episode-notification-ready-state.md).
>
> Task này giữ nguyên trong `todo/` (chưa chuyển `done/`) cho tới khi bổ sung tiêu chí chọn bài theo mức độ quan trọng/tương tác thay vì chỉ theo thời gian.

## User Story
> Là người dùng bận rộn mỗi sáng,
> Tôi muốn ứng dụng tự động tổng hợp các tin quan trọng nhất thành một bản tin hội thoại ngắn gọn lúc 6h sáng,
> Để tôi tiết kiệm thời gian đọc lướt hàng chục bài viết trước khi ra khỏi nhà.

## Acceptance Criteria (Gherkin)
- **Given** đến 6:00 sáng và thiết bị đang kết nối Wifi
- **When** WorkManager kích hoạt tác vụ nền
- **Then** chọn lọc 5 tin bài có điểm tương tác cao nhất
- **And** sinh kịch bản đối thoại JSON gồm các lời thoại xen kẽ giữa Host A và Host B
- **And** gửi Push Notification: "☕ Bản tin sáng CommuteCast 4 phút của bạn đã sẵn sàng!"

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [DJ-01] "Bộ Lập Lịch Tự Động Buổi Sáng & Kịch Bản 2 MC" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" (bao gồm audit note) + "Acceptance Criteria" trong file doc/task/todo/DJ-01_daily-scheduler-dual-mc-script.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location (CommuteWorker.kt, CommuteScriptService.kt, ArticleDao.kt), xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria: bổ sung tiêu chí "điểm tương tác" (ví dụ: kết hợp recency + số lượt đọc/lưu/chia sẻ nếu có, hoặc tối thiểu ưu tiên bài từ nhiều feed khác nhau thay vì thuần recency) khi chọn 5 bài cho CommuteCast. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng, tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [DJ-01] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [DJ-01] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
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
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.
