# [KNOW-06] AI Request Gateway thống nhất

- **Type:** Architecture / Reliability
- **Priority:** `P1 (High)`
- **Estimation:** `5 Story Points`
- **Epic:** [06. NEXT_GEN_AI_KNOWLEDGE — AI Thế Hệ Mới & Quản Trị Tri Thức](06_NEXT_GEN_AI_KNOWLEDGE.md)
- **Location:** [`app/src/main/java/com/mckimquyen/reader/infrastructure/ai/GeminiSummaryService.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/ai/GeminiSummaryService.kt), [`app/src/main/java/com/mckimquyen/reader/infrastructure/ai/GeminiConfig.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/ai/GeminiConfig.kt), [`app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModel.kt`](../../../app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModel.kt)

## Vấn đề thực tế (đã audit lại, điều chỉnh so với giả định ban đầu)
Đã đọc trực tiếp toàn bộ `infrastructure/ai/` để xác minh premise ban đầu ("mỗi engine tự gọi Gemini API riêng lẻ"). Kết quả audit: **premise đó không hoàn toàn đúng** — `StoryClusteringEngine` (KNOW-01) và `SemanticSearchEngine` (KNOW-02) là các engine heuristic **100% offline**, không hề gọi Gemini API hay bất kỳ network call nào (đã grep xác nhận không có `Gemini`/`OkHttp`/`Retrofit` trong 2 file này). `ArticleDeepReadEngine.kt` và `ArticleMindMapExtractor.kt` cũng là các `object` heuristic thuần Kotlin, chỉ dùng để (a) sinh câu hỏi gợi ý / trả lời offline fallback, và (b) parse JSON response từ Gemini — bản thân chúng không thực hiện HTTP call.

**Vấn đề thực sự nằm ở bên trong `GeminiSummaryService.kt`** — đây là **class duy nhất** thực hiện gọi Gemini REST API (qua `callGeminiRaw()`, dòng 216-241), nhưng logic retry/failover giữa nhiều API key bị **lặp lại y hệt 3 lần** trong 3 hàm public khác nhau:
- `extractHighlights()` (dòng 53-99): vòng lặp `for ((index, key) in keys.withIndex())` với try/catch phân loại `tryNext` theo `InvalidApiKey`/`RateLimited`/`Http`.
- `generateMindMap()` (dòng 105-149): **copy gần như nguyên văn** vòng lặp trên.
- `askArticleQuestion()` (dòng 155-204): **copy gần như nguyên văn** vòng lặp trên lần thứ 3.

Ngoài trùng lặp code, còn thiếu:
- Không có **timeout tường minh** theo từng loại request (dựa hoàn toàn vào timeout mặc định của `OkHttpClient` được inject — không thấy cấu hình riêng cho AI call, vốn cần lâu hơn network call thông thường như tải favicon/RSS).
- Không có **cancellation-aware retry**: `callGeminiRaw()` dùng `okHttpClient.newCall(request).execute()` đồng bộ (blocking call bên trong `Dispatchers.IO`) — nếu coroutine bị hủy (ví dụ người dùng thoát `ReadingPage` giữa lúc đang chờ Gemini trả lời), request vẫn tiếp tục chạy ngầm tới khi timeout thay vì bị hủy ngay.
- Không có **single-flight**: nếu người dùng bấm nhanh 2 lần vào nút "Tóm tắt AI" hoặc gửi 2 câu hỏi Deep Read liên tiếp trước khi câu đầu trả lời xong, `GeminiSummaryService` sẽ gửi 2 request Gemini độc lập song song, tốn quota/tiền API không cần thiết thay vì gộp lại chờ chung 1 kết quả hoặc hủy request cũ.
- `MAX_INPUT_CHARS`, `TAG`, `mask()`, `mapHttpError()` là các helper dùng chung nhưng nằm rải rác cùng logic nghiệp vụ, không tách thành 1 tầng gateway độc lập dễ test/mở rộng khi có thêm use case AI mới trong tương lai (ví dụ KNOW-07 Smart Collections nếu sau này cần gọi Gemini để tinh chỉnh truy vấn).

## User Story
> Là lập trình viên bảo trì các tính năng AI của app (tóm tắt, mind map, deep read Q&A, và các tính năng AI tương lai),
> Tôi muốn có một tầng gateway gọi Gemini API duy nhất, tập trung logic retry/timeout/failover/single-flight,
> Để không phải copy-paste lại logic key failover mỗi khi thêm 1 use case AI mới, và tránh lãng phí quota API do gọi trùng lặp.

## Acceptance Criteria (Gherkin)
- **Given** `GeminiSummaryService` cần gọi Gemini cho 1 trong các use case (highlights, mindmap, deep read Q&A, summarize)
- **When** hàm tương ứng được gọi
- **Then** toàn bộ logic thử lần lượt từng API key (failover), phân loại lỗi để quyết định `tryNext`, và log chuẩn hoá được xử lý bởi **1 hàm/class gateway dùng chung duy nhất** (ví dụ `AiRequestGateway` hoặc hàm private `executeWithFailover()`), không còn 3 vòng lặp copy-paste trong `extractHighlights()`/`generateMindMap()`/`askArticleQuestion()`
- **And** mỗi request Gemini có timeout tường minh cấu hình riêng (không dùng chung timeout mặc định của `OkHttpClient` cho toàn app), đủ dài cho input lớn nhưng có giới hạn rõ ràng để không treo UI vô thời hạn
- **And** khi coroutine gọi hàm AI bị hủy (ví dụ `viewModelScope` bị cancel do rời màn hình), request HTTP đang chạy phải được hủy theo (cancellation-aware) chứ không tiếp tục chạy ngầm
- **And** nếu có 2 lời gọi gateway trùng nhau (cùng article + cùng use case) xảy ra gần như đồng thời, gateway áp dụng single-flight: request thứ 2 chờ chung kết quả của request thứ 1 đang chạy thay vì bắn thêm 1 HTTP call mới
- **And** hành vi fallback sang offline heuristic khi hết key/lỗi mạng (đã có, hoạt động đúng) không bị thay đổi/hỏng sau khi refactor.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [KNOW-06] "AI Request Gateway thống nhất" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/KNOW-06_unified-ai-request-gateway.md trước khi bắt đầu. LƯU Ý: đây là refactor tập trung vào GeminiSummaryService.kt — KHÔNG phải viết lại StoryClusteringEngine/SemanticSearchEngine (2 engine đó không gọi Gemini, không liên quan task này).

Mỗi vòng lặp:
1. Đọc lại toàn bộ GeminiSummaryService.kt, xác nhận 3 vòng lặp failover trùng lặp còn tồn tại (không giả định) và xác nhận cấu hình OkHttpClient hiện tại (infrastructure/di/OkHttpClientModule.kt) để biết timeout mặc định đang áp dụng.
2. Implement fix/feature đúng theo Acceptance Criteria:
   - Trích xuất logic "thử lần lượt từng key, phân loại lỗi, quyết định tryNext, log" thành 1 hàm private chung (ví dụ `private suspend fun <T> executeWithFailover(useCase: String, requestBody: String, parse: (String) -> T): T?`) hoặc 1 class `AiRequestGateway` riêng trong infrastructure/ai/, rồi cho extractHighlights()/generateMindMap()/askArticleQuestion() gọi vào đó thay vì lặp code.
   - Thêm timeout tường minh cho call Gemini (ví dụ dùng OkHttpClient.newBuilder().callTimeout(...) tạo riêng 1 client con cho AI call, không đổi timeout toàn cục của app).
   - Đảm bảo callGeminiRaw() cancellation-aware: có thể cần chuyển từ execute() đồng bộ sang cách tích hợp với coroutine cancellation (ví dụ dùng suspendCancellableCoroutine + Call.enqueue()/cancel() khi coroutine bị hủy) thay vì execute() chặn không hủy được.
   - Thêm single-flight: dùng key = hash(useCase + title + relevant params), lưu Map<String, Deferred<T>> đang chạy (đồng bộ hoá bằng Mutex hoặc computeIfAbsent an toàn coroutine) để request trùng chờ chung kết quả thay vì gọi lại.
   Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO (không block Main), không lưu Context/Activity vào ViewModel hay singleton, không phá vỡ hành vi fallback offline hiện có (ArticleHighlightsExtractor/ArticleMindMapExtractor/ArticleDeepReadEngine).
3. Với thay đổi kiến trúc/thiết kế quan trọng (thiết kế gateway, cancellation, single-flight), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [KNOW-06] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [KNOW-06] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
4. Build kiểm tra: `./gradlew assembleDevDebug`.
5. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm, nếu có test giả/mock rỗng/logic chưa đúng thì điểm phải phản ánh đúng thực tế.
2. Bổ sung **unit test** cho mọi nhánh logic mới (`app/src/test/...`), phủ cả edge case (key rỗng, tất cả key lỗi, lỗi mạng, cancellation giữa chừng, 2 request trùng nhau đồng thời phải single-flight đúng).
3. Bổ sung **widget/Compose UI test** cho mọi component UI mới hoặc thay đổi hành vi UI (`app/src/androidTest/...`) nếu có thay đổi UI liên quan (ví dụ loading state khi single-flight).
4. Bổ sung **integration test** cho luồng end-to-end liên quan (gọi thật qua ReadingViewModel với GeminiSummaryService đã refactor, đảm bảo 4 use case cũ vẫn hoạt động đúng).
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, thao tác thủ công đúng luồng vừa sửa (mở AI Summary, Mind Map, Deep Read Q&A), ghi lại bằng chứng cụ thể (log logcat hoặc mô tả kết quả quan sát được) chứng minh hoạt động đúng — không suy đoán, không báo cáo khống.
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` hoặc `inprogress/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.
```
