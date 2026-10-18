# [KNOW-07] Semantic Smart Collections

- **Type:** New Feature / AI
- **Priority:** `P2 (Medium)`
- **Estimation:** `8 Story Points`
- **Epic:** [06. NEXT_GEN_AI_KNOWLEDGE — AI Thế Hệ Mới & Quản Trị Tri Thức](06_NEXT_GEN_AI_KNOWLEDGE.md)
- **Location:** [`app/src/main/java/com/mckimquyen/reader/infrastructure/ai/search/SemanticSearchEngine.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/ai/search/SemanticSearchEngine.kt), `app/src/main/java/com/mckimquyen/reader/ui/page/home/` (Group/Feed filter UI hiện có), `domain/repository/` (DAO mới), `infrastructure/pref/` hoặc Room entity mới cho collection

## Vấn đề thực tế
`SemanticSearchEngine` (KNOW-02, đã done — xem [`doc/task/done/KNOW-02_semantic-search_DONE.md`](../done/KNOW-02_semantic-search_DONE.md)) hiện chỉ hỗ trợ tìm kiếm **một lần** khi người dùng gõ query vào ô tìm kiếm — kết quả biến mất khi xoá query, và không tự động cập nhật khi có bài mới về sau. Người dùng muốn theo dõi liên tục một chủ đề cụ thể (ví dụ "tin về AI tại Việt Nam", "chính sách năng lượng tái tạo châu Âu") hiện phải gõ lại từ khóa tìm kiếm mỗi lần mở app — không có khái niệm "collection" lưu lại truy vấn ngữ nghĩa và tự bổ sung bài phù hợp theo thời gian, khác với các `Group`/`Feed` tĩnh hiện có (vốn chỉ lọc theo nguồn, không lọc theo ý nghĩa nội dung).

## User Story
> Là người theo dõi sát một chủ đề cụ thể trải dài trên nhiều nguồn tin,
> Tôi muốn lưu một câu truy vấn ngôn ngữ tự nhiên thành một "bộ sưu tập" cố định,
> Để mỗi lần đồng bộ có bài mới, app tự động thêm các bài phù hợp với chủ đề đó vào bộ sưu tập mà tôi không cần tìm kiếm lại thủ công.

## Acceptance Criteria (Gherkin)
- **Given** người dùng đang ở kết quả tìm kiếm ngữ nghĩa (Semantic Search) với 1 câu truy vấn tự nhiên
- **When** người dùng chọn "Lưu thành Bộ sưu tập" và đặt tên (hoặc dùng gợi ý tên tự động từ concept đã match)
- **Then** truy vấn được lưu lại (Room entity mới, ví dụ `SmartCollectionEntity`: id, tên, câu truy vấn gốc, ngưỡng điểm liên quan tối thiểu, thời điểm tạo)
- **And** mỗi khi `SyncWorker` hoàn tất một chu kỳ đồng bộ bài mới, hệ thống chạy `SemanticSearchEngine.rank()` (hoặc engine đã nâng cấp ở KNOW-05) trên các bài **mới** đối chiếu với từng Smart Collection đã lưu, tự động gắn bài đạt ngưỡng điểm vào collection tương ứng
- **And** người dùng có thể xem danh sách Smart Collection như một mục điều hướng riêng (tương tự Group/Feed hiện có trong `home/feed`), mở ra xem toàn bộ bài đã match theo thời gian
- **And** người dùng có thể sửa/xoá Smart Collection đã tạo; xoá collection không xoá bài viết gốc, chỉ xoá liên kết
- **And** việc chạy semantic match cho nhiều Smart Collection mỗi lần sync không được làm chậm đáng kể tổng thời gian sync (nên tận dụng cache embedding từ KNOW-05 thay vì tính lại từ đầu).

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [KNOW-07] "Semantic Smart Collections" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/KNOW-07_semantic-smart-collections.md trước khi bắt đầu. LƯU Ý: task này phụ thuộc vào hạ tầng SemanticSearchEngine (KNOW-02, đã done) và lý tưởng nên triển khai SAU khi KNOW-05 (Persistent semantic embedding index) hoàn tất để tránh phải re-embed toàn bộ bài mỗi lần match Smart Collection — nếu KNOW-05 chưa xong, có thể tạm dùng SemanticSearchEngine.rank() hiện tại (chấp nhận chi phí tính toán cao hơn) nhưng phải ghi rõ trong code/PR rằng cần refactor lại khi KNOW-05 xong.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận trạng thái hiện tại (không giả định) — đặc biệt đọc SemanticSearchEngine.kt, cấu trúc Group/Feed hiện có trong domain/model và ui/page/home/feed để thiết kế Smart Collection nhất quán với pattern điều hướng đã có, và SyncWorker (infrastructure/) để biết chỗ chèn logic match sau mỗi chu kỳ sync.
2. Implement fix/feature đúng theo Acceptance Criteria:
   - Room entity + DAO mới cho SmartCollection (id, tên, query, threshold, createdAt) và bảng liên kết collection-article (many-to-many, không xoá bài gốc khi xoá collection) — thêm migration Room đúng chuẩn, export schema vào app/schemas.
   - UI: nút "Lưu thành Bộ sưu tập" từ màn hình kết quả Semantic Search hiện có; màn hình danh sách Smart Collections (tương tự Group/Feed); màn hình chi tiết xem bài đã match theo thời gian; sửa/xoá collection.
   - Logic chạy sau mỗi chu kỳ sync: với mỗi Smart Collection đã lưu, chạy semantic match trên các bài MỚI (không phải toàn bộ kho) đối chiếu threshold, ghi liên kết vào bảng nối.
   Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng (Room, semantic match) chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) cho mọi text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (schema Room mới, chèn logic vào SyncWorker), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [KNOW-07] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [KNOW-07] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
4. Build kiểm tra: `./gradlew assembleDevDebug`.
5. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm, nếu có test giả/mock rỗng/logic chưa đúng thì điểm phải phản ánh đúng thực tế.
2. Bổ sung **unit test** cho mọi nhánh logic mới (`app/src/test/...`), phủ cả edge case (rỗng, query không match bài nào, threshold biên, xoá collection giữa lúc đang match).
3. Bổ sung **widget/Compose UI test** cho mọi component UI mới hoặc thay đổi hành vi UI (`app/src/androidTest/...`).
4. Bổ sung **integration test** cho luồng end-to-end liên quan (DB + repository + SyncWorker chạy match Smart Collection đúng sau sync).
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, thao tác thủ công đúng luồng vừa sửa (tạo collection từ search, đồng bộ bài mới, xác nhận bài mới xuất hiện đúng trong collection), ghi lại bằng chứng cụ thể (log logcat hoặc mô tả kết quả quan sát được) chứng minh hoạt động đúng — không suy đoán, không báo cáo khống.
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` hoặc `inprogress/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.
```
