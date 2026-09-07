# [KNOW-01] AI Deduplication & Story Clustering (Gom Cụm Tin Tức Trùng Lặp)

- **Type:** AI / Information Architecture
- **Priority:** `P1 (High)`
- **Estimation:** `8 Story Points`
- **Epic:** [06. NEXT_GEN_AI_KNOWLEDGE — AI Thế Hệ Mới & Quản Trị Tri Thức](../todo/06_NEXT_GEN_AI_KNOWLEDGE.md)
- **Location:** [`app/src/main/java/com/mckimquyen/reader/infrastructure/ai/clustering/StoryClusteringEngine.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/ai/clustering/StoryClusteringEngine.kt), [`app/src/main/java/com/mckimquyen/reader/ui/page/home/HomeViewModel.kt`](../../../app/src/main/java/com/mckimquyen/reader/ui/page/home/HomeViewModel.kt#L147-L158)

## Vấn đề thực tế (bối cảnh gốc)
Khi có một sự kiện thời sự hoặc công nghệ nóng (ví dụ: Apple ra mắt iPhone mới, bầu cử, thiên tai), 20 tờ báo cùng đưa tin về 1 chủ đề, khiến bảng tin `FlowPage` bị ngập lụt hàng chục bài viết có nội dung tương tự nhau.

## User Story
> Là độc giả theo dõi nhiều nguồn tin,
> Tôi muốn các bài viết cùng nói về một sự kiện được AI tự động gom thành 1 Thẻ Sự Kiện (Story Card) duy nhất,
> Để tôi nắm bắt toàn cảnh sự kiện từ nhiều góc nhìn mà không phải cuộn qua 20 bài trùng lặp.

## Acceptance Criteria (gốc)
- **Given** chu kỳ đồng bộ tải về nhiều bài viết trong vòng 24 giờ
- **When** thuật toán so khớp ngữ nghĩa phát hiện độ tương đồng nội dung > 75%
- **Then** gom các bài viết đó vào một cụm sự kiện chung
- **And** trên `FlowPage` chỉ hiển thị 1 thẻ đại diện với tiêu đề tổng quát nhất kèm huy hiệu (ví dụ: "🔥 8 nguồn tin cùng đưa tin")
- **And** khi chạm vào thẻ, bung danh sách các góc nhìn từ các báo khác nhau (Tuổi Trẻ, VnExpress, BBC, Reuters).

---

## ✅ Completion Report

**Trạng thái:** Đã implement trong code, xác nhận qua đọc trực tiếp source + test hiện có (audit ngày 2026-09-06).

**Commit liên quan:** `d38cc39` — `feat(ai): add AI Deduplication & Story Clustering (Loop 12) with full test coverage` (xem `git log --oneline`).

### Đã làm gì
- `StoryClusteringEngine.kt` (`infrastructure/ai/clustering/`): thuật toán phân cụm dùng Disjoint-Set Union (Union-by-Rank + Path Compression), so khớp từng cặp bài viết trong `timeWindowHours` (mặc định 48h) dựa trên tổ hợp trọng số: Jaccard + Overlap Coefficient trên token tiêu đề, bigram tiêu đề, thực thể viết hoa/số (regex Unicode), và mô tả ngắn. Ngưỡng gộp cụm mặc định `DEFAULT_SIMILARITY_THRESHOLD = 0.45f`.
- Kết quả trả về `StoryClusterResult` gồm `clusters` (mỗi cụm có `leadArticle` chọn theo độ dài tiêu đề+mô tả rồi theo thời gian mới nhất, `keywords` trích xuất theo tần suất, `sourceCount` = số feed khác nhau), `leadClusterMap`, `nonLeadIds` (để ẩn các bài không phải lead khỏi danh sách chính).
- Gắn vào luồng thật: `HomeViewModel.fetchArticles()` gọi `clusteringEngine.cluster(recentArticles)` trên 150 bài gần nhất mỗi khi fetch (khi tính năng `flowStoryClustering` bật và không có search), rồi map vào `PagingData` để `FlowPage` hiển thị.
- UI: có `StoryCluster` domain model (`domain/model/cluster/`) phục vụ hiển thị Story Card + badge số nguồn tin, mở rộng danh sách góc nhìn khi chạm vào (`openCluster()`, `markClusterAsRead()` trong `HomeViewModel`).

### Test đã có
- Unit test: `app/src/test/java/com/mckimquyen/reader/infrastructure/ai/clustering/StoryClusteringEngineTest.kt` — 8 `@Test`, phủ các case: gộp cụm đúng ngưỡng similarity, tách cụm ngoài time window, keyword extraction, DSU union nhiều bài, v.v.
- Integration test: `app/src/androidTest/java/com/mckimquyen/reader/infrastructure/ai/clustering/StoryClusteringIntegrationTest.kt` — 1 `@Test` cấp instrumented, kiểm tra luồng end-to-end với dữ liệu thật hơn.

### Điểm audit khách quan: **6.5/10**

Thuật toán tương đồng (`calculateSimilarity()`) được thiết kế khá kỹ (multi-signal: word Jaccard/Overlap, bigram, entity, description), có test bao phủ cơ bản. Tuy nhiên đây **không phải** một hệ thống production-ready hoàn chỉnh — không đạt 10/10 vì các gap sau:

### Gap còn tồn tại (chưa xử lý)
1. **O(n²) không cache, tính lại toàn bộ mỗi lần fetch** — `cluster()` so sánh mọi cặp bài trong danh sách 150 bài (`for i in 0..n, for j in i+1..n`) mỗi khi `HomeViewModel.fetchArticles()` được gọi (mỗi lần pull-to-refresh, mở app, đổi filter). Với 150 bài là ~11,175 phép so sánh mỗi lần, không có cache fingerprint/token theo từng bài đã xử lý trước đó → lãng phí CPU, không scale nếu tăng giới hạn bài hoặc tần suất sync.
2. **`similarityScore` bị hardcode cố định `0.85f`** (dòng 111 `StoryClusteringEngine.kt`) — mặc dù `calculateSimilarity()` tính điểm tương đồng thực giữa từng cặp bài rất chi tiết, giá trị này **không được dùng lại** để gán vào `StoryCluster.similarityScore`; UI hiển thị con số tương đồng giả không phản ánh đúng cụm đó thực sự khớp bao nhiêu %.
3. Không có invalidation/incremental logic: mỗi lần có bài mới, toàn bộ 150 bài được phân cụm lại từ đầu thay vì chỉ xử lý bài mới rồi merge vào cụm đã có.
4. Ngưỡng `> 75%` trong Acceptance Criteria gốc không khớp với `DEFAULT_SIMILARITY_THRESHOLD = 0.45f` thực tế trong code — ngưỡng thấp hơn nhiều so với đặc tả ban đầu (có thể là điều chỉnh có chủ đích sau thử nghiệm, nhưng không có ghi chú giải thích trong code/commit).

→ Các gap 1–3 được tách thành task theo dõi riêng: **[KNOW-04] Incremental/cached clustering** (`doc/task/todo/KNOW-04_incremental-cached-clustering.md`).
