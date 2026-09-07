# [REEL-05] Watchdog Matcher Theo Batch, Tối Ưu Hiệu Năng

- **Type:** Performance
- **Priority:** `P2 (Medium)`
- **Estimation:** `3 Story Points`
- **Epic:** [09. REEL — Tin Tức Thị Giác & Cảnh Báo Khẩn (Visual Reels & Media)](09_VISUAL_REELS_AND_MEDIA.md)
- **Location:**
  - [`app/src/main/java/com/mckimquyen/reader/domain/watchdog/WatchdogEngine.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/watchdog/WatchdogEngine.kt)
  - [`app/src/main/java/com/mckimquyen/reader/infrastructure/watchdog/WatchdogManager.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/watchdog/WatchdogManager.kt#L126-L161)

## Vấn đề thực tế
- **Không pre-compile/normalize keyword:** `WatchdogEngine.matchesText()` (`WatchdogEngine.kt:58-79`) build lại `Regex(...)` mới **mỗi lần gọi**, cho **mỗi keyword × mỗi field (title/desc/content) × mỗi bài viết**. Với danh sách nhiều từ khóa và một batch sync lớn (hàng chục/hàng trăm bài viết mới), số lần compile Regex tăng theo cấp số nhân không cần thiết — Regex compile là thao tác tốn CPU, không nên lặp lại cho cùng một keyword.
- **Không giới hạn phần nội dung cần quét:** `matchArticle()` (`WatchdogEngine.kt:28-53`) luôn `lowercase()` toàn bộ `fullContent` của bài viết (có thể rất dài với bài full-text) cho mỗi lần gọi `match()`, dù trên thực tế phần lớn tín hiệu quan trọng nằm ở tiêu đề/mô tả đầu bài.
- **Ghi liên tục thay vì gộp 1 lần:** `WatchdogManager.checkAndNotify()` (`WatchdogManager.kt:144-161`) gọi `incrementMatchCount(matchedKeyword.id)` **ngay trong vòng lặp** cho từng bài viết khớp — mỗi lần gọi là một lần đọc `_keywords.value`, map toàn bộ danh sách, và ghi đè toàn bộ JSON xuống `SharedPreferences` (`saveKeywords`). Nếu một batch sync có 5 bài viết khớp 1 keyword, sẽ có 5 lần ghi I/O riêng biệt thay vì gộp thành 1 lần cập nhật cuối batch.

## User Story
> Là người dùng có nhiều nguồn tin đồng bộ thường xuyên với danh sách từ khóa theo dõi dài,
> Tôi muốn việc quét từ khóa trong mỗi chu kỳ sync diễn ra nhanh và tiết kiệm pin/CPU,
> Để tính năng Watchdog không làm chậm hoặc tốn tài nguyên máy khi có nhiều bài viết mới cùng lúc.

## Acceptance Criteria (Gherkin)
- **Given** danh sách từ khóa đang theo dõi
- **When** `WatchdogEngine` chuẩn bị quét một batch bài viết
- **Then** mỗi keyword chỉ được normalize/compile pattern (Regex hoặc cấu trúc tương đương) **một lần duy nhất cho cả batch**, không compile lại cho từng bài viết.
- **And** phần nội dung bài viết được quét phải có giới hạn hợp lý (ví dụ chỉ quét N ký tự đầu của `fullContent`, có thể cấu hình hằng số), tránh xử lý toàn văn bản dài không cần thiết trong vòng lặp match.
- **Given** một batch sync có nhiều bài viết khớp cùng một hoặc nhiều từ khóa
- **When** `checkAndNotify()` xử lý xong toàn bộ batch
- **Then** chỉ thực hiện **một lần ghi persistence duy nhất** cập nhật tất cả `matchCount` đã tăng trong batch đó (không ghi I/O riêng lẻ cho từng match).
- **And** có benchmark/unit test đo số lần gọi ghi persistence (mock/spy) chứng minh giảm từ O(n match) xuống O(1) mỗi batch.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [REEL-05] "Watchdog Matcher Theo Batch, Tối Ưu Hiệu Năng" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/REEL-05_watchdog-batch-matching-perf.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location (WatchdogEngine.kt, WatchdogManager.kt), xác nhận vấn đề còn tồn tại (không giả định) — xác nhận lại việc Regex được build lại mỗi lần gọi matchesText() và việc incrementMatchCount ghi I/O riêng lẻ trong vòng lặp checkAndNotify.
2. Implement fix đúng theo Acceptance Criteria: cache/pre-compile Regex theo keyword (ví dụ tính lại khi danh sách keyword thay đổi, lưu trong map keyword->CompiledPattern), giới hạn ký tự quét trong fullContent, và gộp incrementMatchCount thành một batch update cuối checkAndNotify (ví dụ đếm số match theo id trong Map<String,Int> rồi apply 1 lần). Không phá vỡ hành vi/API public đang được HomeViewModel, AbstractRssRepository, và test hiện có (WatchdogEngineTest, WatchdogManagerTest, WatchdogIntegrationTest) sử dụng — nếu đổi signature, cập nhật toàn bộ call site và test liên quan. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main).
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [REEL-05] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [REEL-05] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
4. Build kiểm tra: `./gradlew assembleDevDebug` (hoặc `lintDevDebug` nếu chỉ đổi resource/string).
5. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm, nếu có test giả/mock rỗng/logic chưa đúng thì điểm phải phản ánh đúng thực tế. Đảm bảo kết quả matching KHÔNG đổi hành vi so với trước (regression) — mọi test cũ trong WatchdogEngineTest/WatchdogManagerTest phải vẫn pass.
2. Bổ sung **unit test** cho mọi nhánh logic mới (`app/src/test/...`), phủ cả edge case (rỗng, lỗi mạng, dữ liệu null, giới hạn biên) — bắt buộc có test đếm số lần ghi persistence trong 1 batch nhiều match.
3. Bổ sung **widget/Compose UI test** cho mọi component UI mới hoặc thay đổi hành vi UI (`app/src/androidTest/...`) nếu có (task này chủ yếu là logic nội bộ, có thể không cần UI test mới).
4. Bổ sung **integration test** cho luồng end-to-end liên quan (DB + repository + worker nếu có liên quan) — cập nhật WatchdogIntegrationTest nếu hành vi/side-effect thay đổi.
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, thao tác thủ công đúng luồng vừa sửa (trigger sync với nhiều bài khớp từ khóa), ghi lại bằng chứng cụ thể (log logcat hoặc mô tả kết quả quan sát được) chứng minh hoạt động đúng — không suy đoán, không báo cáo khống.
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` hoặc `inprogress/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.
```
