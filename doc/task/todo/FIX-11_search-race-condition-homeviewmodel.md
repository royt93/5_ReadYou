# [FIX-11] Race Condition Khi Tìm Kiếm Trong `HomeViewModel`

- **Type:** Bug / Concurrency Defect
- **Priority:** `P0 (Blocker)`
- **Estimation:** `2 Story Points`
- **Epic:** 01. FIX — Sửa Lỗi, Ổn Định & Ad Unit
- **Location:** [`ui/page/home/HomeViewModel.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/page/home/HomeViewModel.kt#L147-L203)

## Vấn đề thực tế
`HomeViewModel.inputSearchContent(content: String)` (dòng 200-203) cập nhật `searchContent` rồi gọi `fetchArticles()` ngay lập tức trên **mỗi ký tự người dùng gõ**. `fetchArticles()` (dòng 147-198) launch một coroutine mới bằng `viewModelScope.launch(ioDispatcher) { ... }` **không lưu `Job` và không huỷ job trước đó**, bên trong thực hiện nhiều truy vấn I/O (clustering `articleDao.queryRecentArticlesWithFeed`, semantic search `semanticSearchEngine.rank`, dựng `Pager` mới) trước khi `_homeUiState.update { it.copy(pagingData = ...) }`. Vì không có debounce lẫn cancel, gõ nhanh 1 từ khoá 5 ký tự sẽ tạo ra 5 coroutine độc lập chạy song song; do tốc độ I/O không đảm bảo thứ tự hoàn thành, kết quả của truy vấn ký tự thứ 2 (cũ, ít ký tự hơn) có thể hoàn thành **sau** và ghi đè lên kết quả đúng của truy vấn ký tự thứ 5 (mới nhất) trong `_homeUiState`, khiến người dùng thấy danh sách kết quả tìm kiếm sai/lỗi thời so với những gì họ vừa gõ.

## User Story
> Là người dùng gõ từ khoá tìm kiếm bài báo,
> Tôi muốn danh sách kết quả luôn phản ánh đúng từ khoá cuối cùng tôi vừa gõ,
> Để tôi không bị nhầm lẫn bởi kết quả tìm kiếm cũ xuất hiện chồng lên kết quả mới.

## Acceptance Criteria (Gherkin)
- **Given** người dùng đang gõ nhanh một chuỗi tìm kiếm nhiều ký tự
- **When** mỗi ký tự được gõ trước khi truy vấn của ký tự trước đó kịp hoàn thành
- **Then** chỉ có **duy nhất 1** coroutine tìm kiếm được thực thi trọn vẹn tại một thời điểm — tận dụng `collectLatest` trên một `Flow<String>` (debounce hợp lý, ví dụ 250-300ms, cho nội dung search) hoặc huỷ tường minh (`searchJob?.cancel()`) job trước đó trước khi launch job mới
- **And** truy vấn của các ký tự trung gian bị huỷ (`cancel()`), không tiếp tục chạy ngầm tốn tài nguyên I/O
- **And** kết quả cuối cùng hiển thị trong `_homeUiState.pagingData`/`_semanticSearchResults`/`_clusterResult` luôn khớp với nội dung `searchContent` mới nhất tại thời điểm hoàn thành
- **And** hành vi `changeFilter()` (đổi group/feed/filter, cũng gọi `fetchArticles()`) không bị debounce trễ (áp dụng cơ chế cancel-job-cũ nhưng không cần delay debounce như khi gõ text).

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [FIX-11] "Race Condition Khi Tìm Kiếm Trong HomeViewModel" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/FIX-11_search-race-condition-homeviewmodel.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định) — đọc HomeViewModel.kt đầy đủ, xác nhận fetchArticles()/inputSearchContent()/changeFilter() hiện có cơ chế cancel/debounce hay chưa.
2. Implement fix đúng theo Acceptance Criteria. Cách tiếp cận gợi ý: chuyển `searchContent` thành một `MutableStateFlow<String>` riêng nếu chưa có, dùng `.debounce(300).distinctUntilChanged().collectLatest { fetchArticlesInternal(it) }` trong 1 coroutine launch tại init{}/constructor thay vì gọi fetchArticles() trực tiếp trong inputSearchContent(); hoặc đơn giản hơn — giữ 1 biến `private var fetchJob: Job? = null` trong HomeViewModel, `fetchJob?.cancel()` ngay đầu `fetchArticles()` trước khi `fetchJob = viewModelScope.launch(ioDispatcher) { ... }`. Với `inputSearchContent` cân nhắc thêm debounce (ví dụ dùng `kotlinx.coroutines.flow` + `delay`) để tránh spam truy vấn DB mỗi phím gõ. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task FIX-11 trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm — collectLatest+debounce hay Job cancellation thủ công>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task FIX-11 trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
4. Build kiểm tra: `./gradlew assembleDevDebug`.
5. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.
```

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm, nếu có test giả/mock rỗng/logic chưa đúng thì điểm phải phản ánh đúng thực tế.
2. Bổ sung **unit test** cho mọi nhánh logic mới (`app/src/test/...`), phủ cả edge case (rỗng, lỗi mạng, dữ liệu null, giới hạn biên) — dùng `TestDispatcher`/`runTest` mô phỏng gõ liên tục "a" → "ab" → "abc" với độ trễ I/O giả lập không theo thứ tự, xác nhận kết quả cuối cùng khớp "abc" chứ không bị "ab" ghi đè.
3. Bổ sung **widget/Compose UI test** cho mọi component UI mới hoặc thay đổi hành vi UI (`app/src/androidTest/...`) nếu có thay đổi UI quan sát được (ví dụ ô tìm kiếm).
4. Bổ sung **integration test** cho luồng end-to-end liên quan (DB + repository + worker nếu có liên quan).
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, thao tác thủ công gõ nhanh 1 chuỗi tìm kiếm nhiều ký tự trong `HomePage`, xác nhận kết quả cuối cùng đúng và không bị nhấp nháy/ghi đè sai, ghi lại bằng chứng cụ thể (log logcat hoặc mô tả kết quả quan sát được) — không suy đoán, không báo cáo khống.
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` hoặc `inprogress/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.
