# [REEL-06] Watchdog Alert Inbox — Màn Hình Lịch Sử Cảnh Báo Từ Khóa

- **Type:** New Feature / UX
- **Priority:** `P2 (Medium)`
- **Estimation:** `5 Story Points`
- **Epic:** [09. REEL — Tin Tức Thị Giác & Cảnh Báo Khẩn (Visual Reels & Media)](09_VISUAL_REELS_AND_MEDIA.md)
- **Location:** `app/src/main/java/com/mckimquyen/reader/ui/page/watchdog/` (Gói mới, hoặc mở rộng `ui/component/watchdog/WatchdogSheet.kt` hiện có)

## Vấn đề thực tế
Watchdog hiện tại (`WatchdogManager`, `WatchdogEngine`, `WatchdogSheet` — xem `doc/task/done/REEL-03_watchdog-keyword-alert_DONE.md`) chỉ dừng lại ở việc **bắn notification tức thời** khi phát hiện bài viết khớp từ khóa (`NotificationHelper.notifyWatchdogAlert`) và tăng bộ đếm `matchCount` trên từng `WatchdogKeyword`. Không có nơi nào trong app cho phép người dùng:
- Xem lại **lịch sử các cảnh báo đã kích hoạt** (bài nào, khớp từ khóa nào, khi nào) — `matchCount` chỉ là một con số cộng dồn, không lưu chi tiết từng lần match.
- Xem **đoạn trích văn bản (matched excerpt)** cho biết chính xác vì sao bài viết bị gắn cờ (hiện tại `WatchdogBadge` trên `ArticleItem.kt` chỉ hiển thị badge, không có excerpt).
- Đánh dấu **đã đọc/chưa đọc** cho từng cảnh báo.
- **Snooze** (tạm ẩn) một từ khóa trong khoảng thời gian nhất định.
- Đặt **quiet-hours riêng theo từng từ khóa** (ví dụ không báo động ban đêm cho từ khóa "Bitcoin" nhưng vẫn báo động 24/7 cho từ khóa liên quan thiên tai).

Đây là task hoàn toàn mới, xác nhận qua audit code: không có bất kỳ package `ui/page/watchdog/`, entity lưu lịch sử match, hay UI snooze/quiet-hours nào trong repo.

## User Story
> Là người dùng đã cấu hình nhiều từ khóa cảnh báo khẩn cấp,
> Tôi muốn có một màn hình riêng liệt kê toàn bộ lịch sử các bài viết đã kích hoạt cảnh báo kèm đoạn trích khớp, có thể đánh dấu đã đọc và tạm ẩn (snooze) từ khóa gây phiền,
> Để tôi không bỏ lỡ cảnh báo quan trọng nhưng cũng không bị làm phiền quá mức vào những khung giờ tôi không muốn.

## Acceptance Criteria (Gherkin)
- **Given** một bài viết mới khớp từ khóa Watchdog trong chu kỳ sync
- **When** `WatchdogManager` xử lý match đó
- **Then** một bản ghi lịch sử (Alert entry) được lưu lại gồm: id bài viết, tiêu đề, tên feed, từ khóa khớp, **đoạn trích văn bản chứa từ khóa** (matched excerpt — cắt ngắn quanh vị trí khớp, có highlight từ khóa), thời điểm phát hiện, trạng thái đã đọc/chưa đọc (mặc định chưa đọc).
- **Given** người dùng mở màn hình "Watchdog Alert Inbox" (từ Settings hoặc từ `WatchdogSheet` hiện có)
- **When** màn hình hiển thị
- **Then** liệt kê toàn bộ lịch sử cảnh báo theo thời gian giảm dần, phân biệt trực quan đã đọc/chưa đọc, cho phép tap để mở bài viết gốc, và có nút đánh dấu tất cả đã đọc.
- **Given** người dùng đang xem chi tiết một từ khóa
- **When** người dùng chọn "Snooze" (ví dụ 1 giờ / 8 giờ / đến sáng mai) hoặc bật "Quiet Hours" riêng cho từ khóa đó (ví dụ 22:00–07:00)
- **Then** trong khoảng thời gian snooze/quiet-hours, các match của từ khóa đó **vẫn được ghi vào lịch sử** nhưng KHÔNG bắn `NotificationManager.IMPORTANCE_HIGH` notification (tránh làm phiền), và tự động khôi phục báo động bình thường sau khi hết thời gian.
- **And** cấu hình snooze/quiet-hours được lưu bền vững (persist qua restart app), tuân thủ cùng nguyên tắc atomic persistence với task `[REEL-04]`.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [REEL-06] "Watchdog Alert Inbox — Màn Hình Lịch Sử Cảnh Báo Từ Khóa" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/REEL-06_watchdog-alert-inbox.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan: domain/watchdog/WatchdogEngine.kt, infrastructure/watchdog/WatchdogManager.kt, domain/model/watchdog/WatchdogKeyword.kt, ui/component/watchdog/WatchdogSheet.kt, ui/page/home/flow/ArticleItem.kt (WatchdogBadge), infrastructure/android/NotificationHelper.kt — xác nhận vấn đề còn tồn tại (không giả định), hiểu rõ luồng hiện tại trước khi mở rộng.
2. Implement feature đúng theo Acceptance Criteria: cân nhắc thêm entity lịch sử match mới (Room entity/DAO nếu phù hợp với kiến trúc domain/repository hiện có của app, hoặc mở rộng persistence layer đang refactor ở task REEL-04 nếu task đó đã xong trước — kiểm tra trạng thái REEL-04 trong doc/task/ trước khi quyết định tái sử dụng hay làm song song), package UI mới ui/page/watchdog/ theo đúng pattern các trang khác trong ui/page/ (Compose, ViewModel Hilt). Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) cho toàn bộ text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (task này có thêm entity/Room migration mới nên gần như luôn cần), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [REEL-06] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [REEL-06] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
4. Build kiểm tra: `./gradlew assembleDevDebug` (hoặc `lintDevDebug` nếu chỉ đổi resource/string).
5. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm, nếu có test giả/mock rỗng/logic chưa đúng thì điểm phải phản ánh đúng thực tế.
2. Bổ sung **unit test** cho mọi nhánh logic mới (`app/src/test/...`), phủ cả edge case (rỗng, lỗi mạng, dữ liệu null, giới hạn biên) — bắt buộc test excerpt extraction, snooze hết hạn tự khôi phục, quiet-hours qua nửa đêm (ví dụ 22:00–07:00).
3. Bổ sung **widget/Compose UI test** cho mọi component UI mới hoặc thay đổi hành vi UI (`app/src/androidTest/...`) — màn hình Alert Inbox, trạng thái đã đọc/chưa đọc, nút snooze.
4. Bổ sung **integration test** cho luồng end-to-end liên quan (sync worker phát hiện match → lưu lịch sử → hiển thị Inbox → snooze chặn notification nhưng vẫn ghi lịch sử).
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, thao tác thủ công đúng luồng vừa sửa, ghi lại bằng chứng cụ thể (log logcat hoặc mô tả kết quả quan sát được) chứng minh hoạt động đúng — không suy đoán, không báo cáo khống.
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` hoặc `inprogress/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.
```
