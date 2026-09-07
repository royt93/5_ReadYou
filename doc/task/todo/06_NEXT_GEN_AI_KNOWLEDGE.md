# 🧠 Epic 06: AI Thế Hệ Mới & Quản Trị Tri Thức (Next-Gen AI & Knowledge)

> **Mục tiêu Epic:** Biến ứng dụng đọc tin thụ động thành cỗ máy quản trị tri thức cá nhân (Second Brain), giải quyết triệt để nạn ngập lụt tin tức trùng lặp và hỗ trợ tìm kiếm bằng ý nghĩa ngữ nghĩa.

> Đây là **Epic Index** — mục lục các task con, mỗi task 1 file riêng theo `doc/task/_TEMPLATE_TASK.md`. Trạng thái dưới đây được cập nhật qua audit trực tiếp code (2026-09-06), không suy đoán.

---

## Bảng task

| Task | Tiêu đề | Trạng thái | Priority | File |
|---|---|---|---|---|
| KNOW-01 | AI Deduplication & Story Clustering | ✅ Done (audit: 6.5/10, còn gap) | P1 | [`done/KNOW-01_dedup-clustering_DONE.md`](../done/KNOW-01_dedup-clustering_DONE.md) |
| KNOW-02 | On-Device Semantic Search | ✅ Done (audit: 6/10, còn gap) | P2 | [`done/KNOW-02_semantic-search_DONE.md`](../done/KNOW-02_semantic-search_DONE.md) |
| KNOW-03 | Sổ Tay Highlight & Xuất Notion/Obsidian/Markdown | 📋 Todo | P2 | [`todo/KNOW-03_notebook-highlight-export.md`](KNOW-03_notebook-highlight-export.md) |
| KNOW-04 | Incremental/Cached Story Clustering | 📋 Todo | P1 | [`todo/KNOW-04_incremental-cached-clustering.md`](KNOW-04_incremental-cached-clustering.md) |
| KNOW-05 | Persistent Semantic Embedding Index | 📋 Todo | P1 | [`todo/KNOW-05_persistent-embedding-index.md`](KNOW-05_persistent-embedding-index.md) |
| KNOW-06 | AI Request Gateway thống nhất | 📋 Todo | P1 | [`todo/KNOW-06_unified-ai-request-gateway.md`](KNOW-06_unified-ai-request-gateway.md) |
| KNOW-07 | Semantic Smart Collections | 📋 Todo | P2 | [`todo/KNOW-07_semantic-smart-collections.md`](KNOW-07_semantic-smart-collections.md) |

## Ghi chú audit nhanh

- **KNOW-01 & KNOW-02** đã có implementation thật + unit test + androidTest (commit `d38cc39`, `a4a078e`), gắn vào luồng thật qua `HomeViewModel`. Tuy nhiên cả hai đều tính toán lại toàn bộ (O(n²) clustering, re-embed toàn bộ candidates) mỗi lần `fetchArticles()` chạy — không cache theo `articleId`. Đây là lý do sinh ra KNOW-04 và KNOW-05.
- **KNOW-03** vẫn hoàn toàn chưa implement (grep xác nhận không có `ui/page/notebook/`, không có "Notion"/"Obsidian" trong code liên quan tính năng). Được nâng độ ưu tiên thực thi vì có thể tái dùng hạ tầng Deep Read (`ArticleDeepReadEngine`) và Mind Map (`ArticleMindMapExtractor`) đã có sẵn.
- **KNOW-06** phát sinh từ audit `GeminiSummaryService.kt`: không phải các engine khác tự gọi Gemini riêng lẻ như giả định ban đầu (StoryClustering/SemanticSearch hoàn toàn offline), mà là 3 hàm trong chính `GeminiSummaryService` (`extractHighlights`, `generateMindMap`, `askArticleQuestion`) lặp lại y hệt logic failover theo key — cần gộp thành 1 gateway dùng chung, thêm timeout tường minh, cancellation-aware retry, single-flight.
- **KNOW-07** là tính năng mới xây trên hạ tầng `SemanticSearchEngine` (KNOW-02) đã có, nên triển khai sau KNOW-05 để tránh chi phí re-embed lặp lại mỗi chu kỳ sync.
