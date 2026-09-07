# [REEL-04] Watchdog Persistence Chưa Atomic (Race Condition + Mất Dữ Liệu Khi Lỗi Parse)

- **Type:** Bug / Data Integrity
- **Priority:** `P1 (High)`
- **Estimation:** `3 Story Points`
- **Epic:** [09. REEL — Tin Tức Thị Giác & Cảnh Báo Khẩn (Visual Reels & Media)](09_VISUAL_REELS_AND_MEDIA.md)
- **Location:** [`app/src/main/java/com/mckimquyen/reader/infrastructure/watchdog/WatchdogManager.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/watchdog/WatchdogManager.kt#L39-L85)

## Vấn đề thực tế
`WatchdogManager` lưu toàn bộ danh sách từ khóa (bao gồm `matchCount`) dưới dạng **một chuỗi JSON duy nhất** trong `SharedPreferences` (key `watchdog_keywords_json`). Mọi thao tác ghi (`addKeyword`, `removeKeyword`, `toggleKeyword`, `incrementMatchCount`) đều thực hiện theo pattern **đọc toàn bộ `_keywords.value` trong RAM → biến đổi → ghi đè toàn bộ** (`WatchdogManager.kt:67-85`, hàm `saveKeywords`), không có bất kỳ cơ chế khóa (lock/mutex) hay giao dịch nguyên tử nào.

- **Race condition:** `AbstractRssRepository.kt:92` gọi `watchdogManager.checkAndNotify(...)` từ `SyncWorker` (background thread) — hàm này lặp qua nhiều bài viết và gọi `incrementMatchCount(id)` **riêng lẻ cho từng match**, mỗi lần đọc `_keywords.value` rồi ghi đè lại. Nếu đúng lúc đó người dùng đang mở `WatchdogSheet` và gọi `addKeyword`/`removeKeyword`/`toggleKeyword` từ UI thread, hai luồng ghi có thể xen kẽ nhau (lost update): thao tác ghi sau sẽ dựa trên snapshot `_keywords.value` cũ, làm mất thay đổi của thao tác trước đó (ví dụ: người dùng vừa xóa 1 từ khóa nhưng sync worker vẫn đang tăng `matchCount` dựa trên danh sách cũ chưa xóa → từ khóa đã xóa bị ghi lại vào SharedPreferences).
- **Nuốt lỗi và xóa sạch dữ liệu:** hàm `loadKeywords()` (`WatchdogManager.kt:39-65`) bọc việc parse JSON trong `try/catch`, nhưng khi `JSONException` xảy ra (dữ liệu JSON hỏng — ví dụ do quá trình ghi bị ngắt giữa chừng, hoặc do race condition ở trên), catch block tại dòng 62-64 thực hiện `_keywords.value = emptyList()` — **xóa toàn bộ state trong RAM thay vì giữ nguyên hoặc cố khôi phục**, khiến toàn bộ từ khóa và lịch sử `matchCount` của người dùng biến mất vĩnh viễn mà không có cảnh báo nào.

## User Story
> Là người dùng đã cấu hình nhiều từ khóa cảnh báo quan trọng,
> Tôi muốn dữ liệu từ khóa của mình không bao giờ bị mất hoặc bị ghi đè sai do sync chạy nền cùng lúc tôi chỉnh sửa,
> Để tôi tin tưởng tuyệt đối vào tính năng cảnh báo khẩn cấp, kể cả khi app gặp sự cố hoặc bị kill giữa chừng.

## Acceptance Criteria (Gherkin)
- **Given** `SyncWorker` đang gọi `incrementMatchCount` trên background thread
- **When** đồng thời người dùng gọi `addKeyword`/`removeKeyword`/`toggleKeyword` từ UI thread
- **Then** không được xảy ra mất dữ liệu (lost update) — mọi thao tác ghi phải tuần tự hóa (đồng bộ hóa bằng `Mutex`/`synchronized`, hoặc chuyển hẳn sang cơ chế transactional như Room/DataStore để tận dụng atomicity sẵn có).
- **And** việc ghi xuống đĩa phải atomic: hoặc dùng `DataStore` (transactional theo thiết kế), hoặc nếu vẫn dùng file/SharedPreferences thì phải ghi qua file tạm rồi rename (write-temp-then-rename), không được để trạng thái nửa-ghi bị đọc lại.
- **Given** file/dữ liệu lưu trữ bị hỏng định dạng (JSON corrupt) khi app khởi động lại
- **When** `loadKeywords()` (hoặc tương đương sau khi refactor) gặp lỗi parse
- **Then** KHÔNG được xóa sạch danh sách từ khóa hiện có trong RAM/đĩa — phải giữ nguyên dữ liệu cũ nếu còn đọc được (ví dụ giữ lại bản backup gần nhất), hoặc chỉ báo lỗi rõ ràng (log/crash report) thay vì âm thầm mất dữ liệu.
- **And** có unit test mô phỏng race condition (nhiều coroutine ghi đồng thời) chứng minh không mất update, và unit test mô phỏng JSON corrupt chứng minh dữ liệu cũ được giữ lại.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [REEL-04] "Watchdog Persistence Chưa Atomic (Race Condition + Mất Dữ Liệu Khi Lỗi Parse)" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/REEL-04_watchdog-atomic-persistence.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location (WatchdogManager.kt), xác nhận vấn đề còn tồn tại (không giả định) — đặc biệt xác nhận lại pattern read-modify-write ở saveKeywords/loadKeywords và cách AbstractRssRepository.kt gọi checkAndNotify từ SyncWorker.
2. Implement fix đúng theo Acceptance Criteria — ưu tiên đánh giá 2 hướng: (a) thêm Mutex/synchronized bao quanh toàn bộ đọc-sửa-ghi trong WatchdogManager + ghi file atomic (temp + rename) nếu giữ SharedPreferences/JSON, hoặc (b) migrate hẳn sang Room/DataStore (đã có sẵn hạ tầng DataStore trong infrastructure/pref/ theo CLAUDE.md) để có transactional write miễn phí. Chọn hướng ít rủi ro nhất, không phá vỡ API public của WatchdogManager (các hàm addKeyword/removeKeyword/toggleKeyword/incrementMatchCount/checkAndNotify/keywords đang được HomeViewModel và AbstractRssRepository sử dụng). Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton.
3. Với thay đổi kiến trúc/thiết kế quan trọng (migrate sang DataStore/Room là thay đổi lớn, bắt buộc phải tham khảo), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [REEL-04] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [REEL-04] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
4. Build kiểm tra: `./gradlew assembleDevDebug` (hoặc `lintDevDebug` nếu chỉ đổi resource/string).
5. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm, nếu có test giả/mock rỗng/logic chưa đúng thì điểm phải phản ánh đúng thực tế.
2. Bổ sung **unit test** cho mọi nhánh logic mới (`app/src/test/...`), phủ cả edge case (rỗng, lỗi mạng, dữ liệu null, giới hạn biên) — bắt buộc có test race condition (coroutine đồng thời) và test JSON corrupt giữ nguyên dữ liệu cũ.
3. Bổ sung **widget/Compose UI test** cho mọi component UI mới hoặc thay đổi hành vi UI (`app/src/androidTest/...`) nếu có thay đổi UI liên quan (thường không cần cho task này trừ khi thêm UI báo lỗi).
4. Bổ sung **integration test** cho luồng end-to-end liên quan (WatchdogManager + SyncWorker/AbstractRssRepository nếu có liên quan).
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, thao tác thủ công đúng luồng vừa sửa (thêm/xóa từ khóa trong lúc trigger sync thủ công), ghi lại bằng chứng cụ thể (log logcat hoặc mô tả kết quả quan sát được) chứng minh hoạt động đúng — không suy đoán, không báo cáo khống.
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` hoặc `inprogress/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.
```
