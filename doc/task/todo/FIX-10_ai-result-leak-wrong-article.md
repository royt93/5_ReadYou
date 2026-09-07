# [FIX-10] Kết quả AI Summary/Deep Read/Mind Map Có Thể Rơi Nhầm Sang Bài Khác

- **Type:** Bug / Data Integrity Defect
- **Priority:** `P0 (Blocker)`
- **Estimation:** `3 Story Points`
- **Epic:** 01. FIX — Sửa Lỗi, Ổn Định & Ad Unit
- **Location:** [`ui/page/home/read/ReadingViewModel.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModel.kt#L192), [`ReadingViewModel.kt#L243`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModel.kt#L243), [`infrastructure/ai/GeminiSummaryService.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/ai/GeminiSummaryService.kt)

## Vấn đề thực tế
Trong `ReadingViewModel.kt`, hàm tóm tắt AI (`requestSummary`, coroutine tại dòng 192: `viewModelScope.launch { ... summaryService.extractHighlights(...) ... _readingUiState.update { it.copy(summaryState = SummaryState.Success(highlights)) } }`) và Mind Map (`requestMindMap`, coroutine tại dòng 243: tương tự với `mindMapState`) đều launch một coroutine mới trên `viewModelScope` **không lưu `Job` để huỷ**, và **không kèm `articleId` để validate khi kết quả trả về**. So sánh với `initData(articleId, ...)` (dòng 63-77) và `openDeepRead()` (dòng 270-301) — 2 hàm này **có** cơ chế `fetchJob?.cancel()` trước khi launch job mới, hoặc so sánh `currentActive.session.articleId != articleId` trước khi ghi đè state. Ngược lại, `requestSummary`/`requestMindMap` không có bảo vệ tương tự: nếu người dùng mở bài A, bấm Tóm tắt AI (request chậm do mạng yếu), rồi chuyển sang bài B trước khi bài A trả kết quả, callback chậm của bài A vẫn ghi đè `summaryState`/`mindMapState` trong `_readingUiState` hiện tại — lúc này UI đang hiển thị bài B nhưng lại nhận tóm tắt của bài A, dữ liệu hiển thị sai bài.

## User Story
> Là người dùng đọc nhiều bài báo liên tiếp,
> Tôi muốn kết quả AI Summary/Deep Read/Mind Map luôn khớp đúng với bài báo tôi đang xem,
> Để tôi không bị hiểu nhầm thông tin tóm tắt/sơ đồ tư duy của một bài báo khác.

## Acceptance Criteria (Gherkin)
- **Given** người dùng đang xem bài báo A và bấm Tóm tắt AI (hoặc Mind Map)
- **When** request AI cho bài A chưa trả lời kịp và người dùng đã chuyển sang bài báo B (hoặc đóng sheet/rời trang đọc)
- **Then** job coroutine đang chờ cho bài A phải được huỷ (`cancel()`) ngay khi chuyển bài/đóng sheet, KHÔNG tiếp tục chạy ngầm
- **And** nếu vì lý do nào đó kết quả bài A vẫn về sau khi đã chuyển bài, code phải so khớp `articleId` được capture lúc request với `articleId` hiện tại trước khi `update { it.copy(summaryState = ...) }`/`mindMapState`, nếu không khớp thì bỏ qua kết quả đó
- **And** hành vi tương tự (capture `articleId`, cancel job cũ) áp dụng nhất quán cho cả `requestSummary`, `requestMindMap` và `openDeepRead`/gửi câu hỏi Deep Read
- **And** khi chuyển bài, `summaryState`/`mindMapState`/`deepReadState` của bài mới phải reset về `Idle` (không giữ lại state cũ của bài trước).

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [FIX-10] "Kết quả AI Summary/Deep Read/Mind Map Có Thể Rơi Nhầm Sang Bài Khác" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/FIX-10_ai-result-leak-wrong-article.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định) — đọc toàn bộ ReadingViewModel.kt, xác nhận requestSummary/requestMindMap có capture articleId + lưu Job để cancel hay chưa, so sánh với cách initData()/openDeepRead() đã xử lý (fetchJob?.cancel(), so khớp session.articleId).
2. Implement fix đúng theo Acceptance Criteria — mẫu tham khảo trong chính file: dùng 1 biến Job riêng cho mỗi loại request AI (ví dụ summaryJob, mindMapJob) gọi `?.cancel()` trước khi launch job mới VÀ trước khi initData() load bài mới; capture `val requestArticleId = articleWithFeed.article.id` trước `viewModelScope.launch`, so khớp `if (_readingUiState.value.articleWithFeed?.article?.id != requestArticleId) return@launch` trước khi update state. Reset summaryState/mindMapState/deepReadState về Idle trong initData() khi load bài mới. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task FIX-10 trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task FIX-10 trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
4. Build kiểm tra: `./gradlew assembleDevDebug`.
5. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.
```

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm, nếu có test giả/mock rỗng/logic chưa đúng thì điểm phải phản ánh đúng thực tế.
2. Bổ sung **unit test** cho mọi nhánh logic mới (`app/src/test/...`), phủ cả edge case — ví dụ dùng `TestDispatcher`/delay giả lập: request Tóm tắt cho bài A, ngay sau đó gọi `initData(bài B)`, xác nhận `summaryState` cuối cùng không bị ghi đè bởi kết quả trễ của bài A.
3. Bổ sung **widget/Compose UI test** cho mọi component UI mới hoặc thay đổi hành vi UI (`app/src/androidTest/...`) nếu có thay đổi UI quan sát được.
4. Bổ sung **integration test** cho luồng end-to-end liên quan (DB + repository + worker nếu có liên quan).
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, thao tác thủ công mở bài A → bấm Tóm tắt AI → nhanh chóng chuyển sang bài B trước khi có kết quả → xác nhận bài B không hiển thị tóm tắt của bài A (ghi lại log logcat hoặc mô tả quan sát cụ thể) — không suy đoán, không báo cáo khống.
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` hoặc `inprogress/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.
