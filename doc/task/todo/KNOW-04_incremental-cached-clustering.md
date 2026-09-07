# [KNOW-04] Incremental/Cached Story Clustering

- **Type:** Performance / Architecture
- **Priority:** `P1 (High)`
- **Estimation:** `5 Story Points`
- **Epic:** [06. NEXT_GEN_AI_KNOWLEDGE — AI Thế Hệ Mới & Quản Trị Tri Thức](06_NEXT_GEN_AI_KNOWLEDGE.md)
- **Location:** [`app/src/main/java/com/mckimquyen/reader/infrastructure/ai/clustering/StoryClusteringEngine.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/ai/clustering/StoryClusteringEngine.kt), [`app/src/main/java/com/mckimquyen/reader/ui/page/home/HomeViewModel.kt`](../../../app/src/main/java/com/mckimquyen/reader/ui/page/home/HomeViewModel.kt#L147-L158)

## Vấn đề thực tế
Audit KNOW-01 ([`doc/task/done/KNOW-01_dedup-clustering_DONE.md`](../done/KNOW-01_dedup-clustering_DONE.md)) xác nhận `StoryClusteringEngine.cluster()` so sánh **mọi cặp bài** trong danh sách đầu vào (vòng lặp lồng `for i in 0 until n, for j in i+1 until n` — `StoryClusteringEngine.kt` dòng 64-76), độ phức tạp O(n²). `HomeViewModel.fetchArticles()` (dòng 147-158) gọi lại `clusteringEngine.cluster(recentArticles)` trên 150 bài **mỗi lần** hàm này chạy — tức mỗi lần mở app, pull-to-refresh, đổi filter/group/feed — dù phần lớn 150 bài đó không đổi so với lần fetch trước. Với 150 bài là ~11,175 phép so sánh mỗi lần, không có cache fingerprint/token nào được giữ lại giữa các lần gọi.

Ngoài ra, `StoryCluster.similarityScore` bị hardcode cố định `0.85f` (`StoryClusteringEngine.kt` dòng 111) dù hàm `calculateSimilarity()` đã tính điểm tương đồng thực giữa từng cặp bài rất chi tiết (Jaccard + Overlap trên token/bigram/entity/description) — giá trị tính được không được lưu lại và tái sử dụng khi build `StoryCluster`, khiến UI hiển thị con số phần trăm tương đồng giả, không phản ánh đúng cụm đó thực sự khớp bao nhiêu.

## User Story
> Là người dùng mở app nhiều lần trong ngày,
> Tôi muốn tính năng gom cụm tin tức không làm chậm/tốn pin mỗi lần refresh,
> Để trải nghiệm mượt mà ngay cả khi có hàng trăm bài trong nguồn tin.

## Acceptance Criteria (Gherkin)
- **Given** danh sách 150 bài gần nhất đã được phân cụm ở lần fetch trước
- **When** `fetchArticles()` chạy lại và chỉ có N bài mới xuất hiện (N << 150)
- **Then** engine chỉ tính toán similarity cho N bài mới so với các cụm/bài đã có (không tính lại toàn bộ O(n²) trên 150 bài)
- **And** dùng time-window hoặc token-blocking (nhóm sơ bộ theo khung giờ hoặc từ khóa chung) để giảm số cặp cần so sánh trước khi tính similarity đầy đủ
- **And** `StoryCluster.similarityScore` được tính từ kết quả thực của `calculateSimilarity()` (ví dụ: trung bình hoặc min similarity giữa leadArticle và các bài còn lại trong cụm), không còn hardcode `0.85f`
- **And** kết quả phân cụm sau khi tối ưu phải giống hệt (hoặc tương đương về mặt cụm) so với chạy `cluster()` full lại từ đầu trên cùng tập dữ liệu — không được làm sai lệch chất lượng gom cụm để đổi lấy tốc độ.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [KNOW-04] "Incremental/Cached Story Clustering" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/KNOW-04_incremental-cached-clustering.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định) — đặc biệt đọc lại StoryClusteringEngine.kt (cluster(), calculateSimilarity()) và HomeViewModel.fetchArticles() để hiểu chính xác luồng gọi hiện tại trước khi sửa.
2. Implement fix/feature đúng theo Acceptance Criteria:
   - Thiết kế cache theo articleId (in-memory LruCache hoặc lưu kèm token/fingerprint đã tokenize trong ViewModel/Singleton engine) để tránh tokenize lại các bài đã xử lý.
   - Thêm bước "blocking" trước khi so similarity đầy đủ: nhóm bài theo time bucket (ví dụ mỗi 6h) và/hoặc theo entity/token chung trước, chỉ so cặp trong cùng nhóm — giảm số cặp cần tính calculateSimilarity() đầy đủ.
   - Sửa cluster() (hoặc thêm hàm mới clusterIncremental()) để nhận thêm state/cache từ lần chạy trước, chỉ xử lý bài mới rồi merge vào cụm cũ qua DSU, không rebuild toàn bộ từ đầu mỗi lần.
   - Tính similarityScore thực cho StoryCluster (dựa trên kết quả calculateSimilarity() giữa leadArticle và các bài trong cụm) thay vì hardcode 0.85f.
   Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi tính toán nặng chạy Dispatchers.Default/IO (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton.
3. Với thay đổi kiến trúc/thiết kế quan trọng (thiết kế cache, blocking strategy), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [KNOW-04] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [KNOW-04] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
4. Build kiểm tra: `./gradlew assembleDevDebug`.
5. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm, nếu có test giả/mock rỗng/logic chưa đúng thì điểm phải phản ánh đúng thực tế.
2. Bổ sung **unit test** cho mọi nhánh logic mới (`app/src/test/...`), phủ cả edge case (rỗng, lỗi mạng, dữ liệu null, giới hạn biên, cache miss/hit, kết quả incremental phải khớp full-recompute).
3. Bổ sung **widget/Compose UI test** cho mọi component UI mới hoặc thay đổi hành vi UI (`app/src/androidTest/...`) — nếu có thay đổi UI hiển thị similarityScore.
4. Bổ sung **integration test** cho luồng end-to-end liên quan (DB + repository + worker nếu có liên quan).
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, thao tác thủ công đúng luồng vừa sửa, ghi lại bằng chứng cụ thể (log logcat hoặc mô tả kết quả quan sát được) chứng minh hoạt động đúng — không suy đoán, không báo cáo khống.
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` hoặc `inprogress/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.
```
