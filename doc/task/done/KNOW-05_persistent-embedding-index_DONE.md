# [KNOW-05] Persistent Semantic Embedding Index

- **Type:** Performance / Architecture
- **Priority:** `P1 (High)`
- **Estimation:** `5 Story Points`
- **Epic:** [06. NEXT_GEN_AI_KNOWLEDGE — AI Thế Hệ Mới & Quản Trị Tri Thức](06_NEXT_GEN_AI_KNOWLEDGE.md)
- **Location:** [`app/src/main/java/com/mckimquyen/reader/infrastructure/ai/search/SemanticSearchEngine.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/ai/search/SemanticSearchEngine.kt), [`app/src/main/java/com/mckimquyen/reader/ui/page/home/HomeViewModel.kt`](../../../app/src/main/java/com/mckimquyen/reader/ui/page/home/HomeViewModel.kt#L160-L166), `domain/repository/ArticleDao.kt`, `infrastructure/db/AndroidDb.kt`

## Vấn đề thực tế
Audit KNOW-02 ([`doc/task/done/KNOW-02_semantic-search_DONE.md`](../done/KNOW-02_semantic-search_DONE.md)) xác nhận `SemanticSearchEngine.rank()` (dòng 89-147) tính lại `embed(docText)` cho **từng bài trong danh sách candidates** (tối đa 200 bài, `HomeViewModel.kt` dòng 160-166) mỗi lần hàm được gọi, không cache/persist embedding theo `articleId`. `HomeViewModel.inputSearchContent()` (dòng 200-203) gọi `fetchArticles()` — và do đó gọi lại `semanticSearchEngine.rank()` với toàn bộ 200 bài — **mỗi lần người dùng gõ 1 ký tự** vào ô tìm kiếm. Kết quả: 200 embedding được tính lại từ đầu cho mỗi keystroke, dù nội dung của 200 bài đó không đổi giữa các lần gõ liên tiếp — chỉ có câu truy vấn thay đổi.

## User Story
> Là người dùng tìm kiếm bài viết bằng ngôn ngữ tự nhiên,
> Tôi muốn kết quả tìm kiếm ngữ nghĩa phản hồi tức thời khi tôi gõ,
> Để không bị giật/lag hay hao pin khi engine phải tính toán lại toàn bộ tập dữ liệu mỗi ký tự tôi gõ.

## Acceptance Criteria (Gherkin)
- **Given** một bài viết mới được đồng bộ về qua `SyncWorker`
- **When** quá trình sync hoàn tất lưu bài viết vào Room
- **Then** embedding 64 chiều của bài viết đó (tiêu đề + mô tả ngắn) được tính **một lần** và lưu lại gắn với `articleId` (Room entity mới hoặc cột serialize trong bảng Article hiện có, hoặc file cache theo `articleId`)
- **And** khi người dùng gõ câu truy vấn tìm kiếm, `SemanticSearchEngine.rank()` chỉ tính embedding cho **query** (1 lần/lần gõ) và đọc lại embedding đã cache của các bài viết thay vì tính lại
- **And** nếu bài viết đã có embedding cache còn hợp lệ (nội dung không đổi) thì không tính lại khi sync các lần sau
- **And** đo và ghi lại thời gian phản hồi tìm kiếm trước/sau tối ưu để chứng minh cải thiện hiệu năng (đặc biệt trên danh sách 150-200 bài).

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [KNOW-05] "Persistent Semantic Embedding Index" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/KNOW-05_persistent-embedding-index.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định) — đặc biệt đọc lại SemanticSearchEngine.embed()/rank(), HomeViewModel.fetchArticles()/inputSearchContent(), và luồng sync (SyncWorker, AbstractRssRepository, ArticleDao) để chọn đúng chỗ chèn logic tính embedding khi sync.
2. Implement fix/feature đúng theo Acceptance Criteria:
   - Thiết kế lưu trữ embedding: ưu tiên Room entity mới (ví dụ ArticleEmbeddingEntity: articleId, FloatArray serialize thành ByteArray/String, contentHash để biết khi nào cần tính lại) — thêm migration Room đúng chuẩn, export schema vào app/schemas.
   - Chèn bước tính + lưu embedding vào luồng sync bài mới (không tính đồng bộ chặn UI — chạy Dispatchers.Default/IO, có thể qua WorkManager nếu khối lượng lớn).
   - Sửa SemanticSearchEngine.rank() để đọc embedding đã cache theo articleId thay vì gọi embed() lại cho mỗi bài trong danh sách; chỉ gọi embed() cho query.
   - Xử lý cold-start: bài viết cũ (đã có trước khi feature này ra mắt, hoặc bài chưa kịp có cache) cần có fallback — tính embedding on-the-fly lần đầu rồi lưu cache cho lần sau, không được crash hoặc bỏ sót bài trong kết quả tìm kiếm.
   Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng (Room, tính embedding) chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity vào ViewModel hay singleton.
3. Với thay đổi kiến trúc/thiết kế quan trọng (schema Room mới, chèn logic vào luồng sync), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [KNOW-05] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [KNOW-05] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
4. Build kiểm tra: `./gradlew assembleDevDebug`.
5. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.

## ✅ Completion Report (2026-09-27)

**Điểm audit: 10/10** — Acceptance Criteria đạt 100%, mọi finding từ vòng review đã được xử lý.

### Thiết kế
- `ArticleEmbeddingRecord` (Room entity `article_embedding`): `articleId` PK, `contentHash` (SHA-256 **kèm `EMBEDDING_VERSION`** để tự vô hiệu cache khi thuật toán embedding đổi), `embedding` (64 float CSV), `updatedAt`. FK `article(id)` `ON DELETE/UPDATE CASCADE` → xoá bài tự xoá vector, không còn orphan.
- `ArticleEmbeddingDao`: chỉ 4 method có caller thật (`insertOrUpdateAll`, `getByArticleIds`, `getByArticleId`, `count`).
- Room `version = 11` + `MIGRATION_10_11`; **không tạo index phụ** (PK đã được SQLite index) — khớp chính xác `app/schemas/.../11.json`.
- `SemanticSearchEngine.resolveEmbeddings()`: đúng **1 bulk read + 1 hash pass + tối đa 1 write** cho mỗi lần gọi, trả luôn map `articleId → vector` để `rank()` không truy vấn lại lần hai. `distinctBy` chống trùng id. `Mutex` bao cả đọc-tính-ghi nên nhiều coroutine không tính trùng. Vector hỏng/sai chiều → `toFloatArray()` trả null và được tính lại. Write bọc `runCatching` để bài bị xoá giữa lúc đọc-ghi không làm crash lúc đang gõ.
- `SemanticEmbeddingIndexer` được gọi trong `SyncWorker.doWork()` sau khi sync thành công → index dựng **ngay sau sync**, không dồn vào keystroke đầu tiên.

### Bằng chứng hiệu năng (Pixel 7 Pro `2B051FDH3006MU`, Room file thật, n=200)
```
KNOW-05 benchmark n=200 coldMs=316 warmSamplesMs=[179, 148, 149, 126, 151, 137, 146] warmMedianMs=148
```
Cold 316ms → warm median **148ms** (~2.1x), mọi keystroke ≤179ms. Warm search ghi **0** row.

### Tests
- Unit: `SemanticSearchEngineTest` (28), `SemanticEmbeddingIndexerTest` (5), `Migration10to11Test` (5). Full suite: **413 pass / 0 fail**.
- Integration + widget trên device: `SemanticSearchIntegrationTest` (8), `SemanticSearchBenchmarkTest` (3), `SemanticSearchCardWidgetTest` (3). Full androidTest: **85 pass / 0 fail**.
- Phủ: cache hit/miss/stale/corrupt/sai chiều, dedup id trùng, đồng thời 4 coroutine, cascade delete, hash boundary + truncation, empty/blank/limit=0, bulk-read (không N+1), warm search không ghi lại.

### Smoke test nâng cấp DB (quan trọng)
Smoke test **bắt được lỗi thật** mà unit test không thấy: migration tạo `CREATE UNIQUE INDEX` trong khi entity khai báo index non-unique → `IllegalStateException: Migration didn't properly handle: article_embedding` crash mọi máy nâng cấp từ v10 (fresh install không lộ).
Cách xác minh: cài bản trước thay đổi → mở app (tạo DB v10) → cài bản mới đè lên.
Sau khi sửa (bỏ index phụ): `MainActivity` resumed, process sống, logcat sạch (không `Migration didn't properly handle`, không `FATAL`, không `SQLiteConstraint`). Dump DB từ máy xác nhận `user_version=11`, bảng `article_embedding` đúng 4 cột, index duy nhất là `sqlite_autoindex_article_embedding_1` (PK).
Đã thêm test `Migration10to11Test.migration10to11_declaresNoExtraIndexAndKeepsForeignKeyOnArticle` để chặn tái diễn.

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm, nếu có test giả/mock rỗng/logic chưa đúng thì điểm phải phản ánh đúng thực tế.
2. Bổ sung **unit test** cho mọi nhánh logic mới (`app/src/test/...`), phủ cả edge case (rỗng, dữ liệu null, cache miss, cache stale do content đổi, giới hạn biên).
3. Bổ sung **widget/Compose UI test** cho mọi component UI mới hoặc thay đổi hành vi UI (`app/src/androidTest/...`) nếu có.
4. Bổ sung **integration test** cho luồng end-to-end liên quan (Room migration + DAO + sync worker tính và lưu embedding đúng).
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, thao tác thủ công đúng luồng vừa sửa (sync bài mới → tìm kiếm ngữ nghĩa nhiều ký tự liên tiếp), ghi lại bằng chứng cụ thể (log logcat hoặc mô tả kết quả quan sát được) chứng minh hoạt động đúng và nhanh hơn — không suy đoán, không báo cáo khống.
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` hoặc `inprogress/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.
```
