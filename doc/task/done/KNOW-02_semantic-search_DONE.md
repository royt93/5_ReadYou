# [KNOW-02] On-Device Semantic Search (Tìm Kiếm Ngữ Nghĩa Bằng Vector Embeddings)

- **Type:** AI / Search Engine
- **Priority:** `P2 (Medium)`
- **Estimation:** `8 Story Points`
- **Epic:** [06. NEXT_GEN_AI_KNOWLEDGE — AI Thế Hệ Mới & Quản Trị Tri Thức](../todo/06_NEXT_GEN_AI_KNOWLEDGE.md)
- **Location:** [`app/src/main/java/com/mckimquyen/reader/infrastructure/ai/search/SemanticSearchEngine.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/ai/search/SemanticSearchEngine.kt), [`app/src/main/java/com/mckimquyen/reader/infrastructure/ai/search/SemanticSearchResult.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/ai/search/SemanticSearchResult.kt), [`app/src/main/java/com/mckimquyen/reader/ui/page/home/HomeViewModel.kt`](../../../app/src/main/java/com/mckimquyen/reader/ui/page/home/HomeViewModel.kt#L160-L166)

## Vấn đề thực tế (bối cảnh gốc)
Tìm kiếm văn bản truyền thống (LIKE hoặc FTS) bắt buộc người dùng phải nhớ chính xác từ khóa. Nếu người dùng gõ "công nghệ năng lượng sạch", app sẽ bỏ sót các bài viết chứa "tấm pin mặt trời", "tuabin gió", "nhiên liệu hydro".

## User Story
> Là người cần tra cứu thông tin theo khái niệm và ý nghĩa,
> Tôi muốn tìm kiếm bài viết bằng câu hỏi tự nhiên,
> Để tìm ra chính xác các bài liên quan dù tiêu đề không chứa đúng từ khóa đó.

## Acceptance Criteria (gốc)
- **Given** kho lưu trữ bài viết đã được sinh vector embedding (sử dụng On-Device MediaPipe Text Embedder hoặc mô hình nhúng siêu nhẹ)
- **When** người dùng gõ câu truy vấn tự nhiên vào thanh tìm kiếm
- **Then** hệ thống tính toán khoảng cách cosine similarity và trả về kết quả xếp hạng theo mức độ liên quan ngữ nghĩa trong vòng < 50ms
- **And** hoạt động 100% offline trên thiết bị, bảo mật tuyệt đối không gửi lịch sử tìm kiếm lên internet.

---

## ✅ Completion Report

**Trạng thái:** Đã implement trong code, xác nhận qua đọc trực tiếp source + test hiện có (audit ngày 2026-09-06).

**Commit liên quan:** `a4a078e` — `feat(ai): add On-Device Semantic Search (Loop 13) with full test coverage` (xem `git log --oneline`).

### Đã làm gì
- `SemanticSearchEngine.kt` (`infrastructure/ai/search/`): **không** dùng MediaPipe Text Embedder như đặc tả gốc gợi ý, mà tự cài đặt một embedding nhẹ 64 chiều thủ công (`EMBEDDING_DIM = 64`):
  - 30 chiều đầu chiếu vào 10 "concept cluster" song ngữ Việt/Anh được định nghĩa cứng trong code (CLEAN_ENERGY, ARTIFICIAL_INTELLIGENCE, SEMICONDUCTOR, FINANCE_MARKETS, CRYPTOCURRENCY, ELECTRIC_VEHICLES, HEALTH_BIOTECH, SPACE_AEROSPACE, CYBERSECURITY, DEFENSE_GEOPOLITICS).
  - 34 chiều còn lại băm sub-word character n-gram (3-gram) theo hash bucket.
  - Vector được chuẩn hóa L2, cosine similarity = tích vô hướng.
  - Điểm cuối là hybrid: `cosineSim * 0.45 + conceptBonus * 0.35 + tokenOverlap * 0.20`.
- Gắn vào luồng thật: `HomeViewModel.fetchArticles()` gọi `semanticSearchEngine.rank(searchContent, candidates)` trên tối đa 200 bài gần nhất khi cờ `flowSemanticSearch` bật và có nội dung tìm kiếm; `inputSearchContent()` gọi `fetchArticles()` mỗi lần người dùng gõ.
- Có domain model `SemanticSearchResult` (article + score + matchedConcepts) phục vụ UI hiển thị kết quả xếp hạng.
- Hoạt động 100% offline (không có call mạng nào trong `SemanticSearchEngine`), đúng yêu cầu bảo mật của đặc tả gốc.

### Test đã có
- Unit test: `app/src/test/java/com/mckimquyen/reader/infrastructure/ai/search/SemanticSearchEngineTest.kt` — 6 `@Test`.
- Integration test: `app/src/androidTest/java/com/mckimquyen/reader/infrastructure/ai/search/SemanticSearchIntegrationTest.kt` — 1 `@Test`.

### Điểm audit khách quan: **6/10**

Thuật toán hybrid hợp lý cho một giải pháp "không dùng mô hình ML thật", có test cơ bản, hoạt động offline đúng yêu cầu bảo mật. Không đạt điểm cao vì:

### Gap còn tồn tại (chưa xử lý)
1. **Không dùng MediaPipe Text Embedder / mô hình nhúng thật** như đặc tả gốc đề xuất — thay vào đó là embedding thủ công dựa trên bảng concept-cluster cứng (hardcoded, chỉ ~10 chủ đề) + hash n-gram. Khả năng khái quát hóa ngữ nghĩa thấp hơn nhiều so với embedding model thật; các khái niệm ngoài 10 cluster đã liệt kê gần như không được "hiểu" về mặt ngữ nghĩa, chỉ còn dựa vào token overlap.
2. **Re-embed toàn bộ tập bài (tối đa 200 bài) mỗi lần gọi `rank()`** — vòng lặp `for (art in articles) { ... embed(docText) ... }` trong `rank()` tính lại vector cho từng bài từ đầu, không cache/persist embedding theo `articleId`. Vì `HomeViewModel.inputSearchContent()` gọi `fetchArticles()` mỗi lần người dùng gõ 1 ký tự, nghĩa là **200 embedding được tính lại cho mỗi keystroke** — lãng phí CPU nghiêm trọng, đặc biệt trên thiết bị yếu, dù mỗi embedding riêng lẻ khá rẻ (không dùng model nặng) nên độ trễ thực tế vẫn có thể nằm trong ngưỡng "< 50ms" nêu trong AC gốc, nhưng thiết kế không scale nếu tăng số bài hoặc độ phức tạp embedding trong tương lai.
3. Không có input debounce rõ ràng trước khi gọi `fetchArticles()` từ ô tìm kiếm (cần xác nhận thêm ở tầng UI `FlowPage`/search bar) — kết hợp với gap #2 làm trầm trọng thêm chi phí tính toán lặp lại.

→ Gap #2 (và một phần #1: chuẩn bị hạ tầng cache để dễ nâng cấp sang embedding model thật sau này) được tách thành task theo dõi riêng: **[KNOW-05] Persistent semantic embedding index** (`doc/task/todo/KNOW-05_persistent-embedding-index.md`).
