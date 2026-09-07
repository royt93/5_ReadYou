# 🌟 Epic 05: Tính Năng Độc Quyền & Killer Features (Market Differentiators)

> **Mục tiêu Epic:** Tạo ra những tính năng độc bản (Unfair Advantage) mà chưa có bất kỳ app RSS nào (kể cả Feedly, Inoreader, Read You gốc) trên thị trường sở hữu, biến RSS Cat Hub thành ứng dụng đọc tin AI số 1 trên Play Store. Epic này đã được tách thành các file task riêng theo `doc/task/_TEMPLATE_TASK.md` — file này chỉ còn là mục lục, không chứa chi tiết task.

---

## Danh sách Task (14 task)

| Task | Tên | Priority | Status | Link |
|---|---|---|---|---|
| EXC-01 | AI Daily Smart Digest — Bản Tin Sáng Tổng Hợp Đa Nguồn Bằng AI | P1 (High) | 📋 Todo | [EXC-01_ai-daily-smart-digest.md](EXC-01_ai-daily-smart-digest.md) |
| EXC-02 | AI Podcast Studio — Biến Tin Tức Thành Cuộc Trò Chuyện Audio 2 Người | P2 (Medium) | 📋 Todo | [EXC-02_ai-podcast-studio.md](EXC-02_ai-podcast-studio.md) |
| EXC-03 | Chế Độ Đọc Bionic Reading (Tăng Tốc Độ Đọc Cho Não Bộ) | P2 (Medium) | 📋 Todo | [EXC-03_bionic-reading-mode.md](EXC-03_bionic-reading-mode.md) |
| EXC-04 | AI Clickbait Buster & Bộ Nhận Diện Thiên Lệch Tin Tức | P2 (Medium) | 📋 Todo | [EXC-04_ai-clickbait-bias-buster.md](EXC-04_ai-clickbait-bias-buster.md) |
| EXC-05 | Đồng Bộ Thiết Bị P2P Nội Mạng Wi-Fi Không Cần Máy Chủ | P3 (Low) | 📋 Todo | [EXC-05_local-p2p-sync.md](EXC-05_local-p2p-sync.md) |
| EXC-06 | "AI Deep Read" — Trò Chuyện & Hỏi Đáp Tương Tác Với Bài Báo | P2 (Medium) | ✅ Done | [../done/EXC-06_ai-deep-read-chat_DONE.md](../done/EXC-06_ai-deep-read-chat_DONE.md) |
| EXC-07 | Chế Độ Đọc Song Ngữ & Dịch Đoạn Tức Thì (Bilingual Side-by-Side) | P2 (Medium) | 📋 Todo | [EXC-07_bilingual-side-by-side-reader.md](EXC-07_bilingual-side-by-side-reader.md) |
| EXC-08 | Second Brain Graph — Đồ Thị Tri Thức Cá Nhân Hợp Nhất | P1 (High) | 📋 Todo | [EXC-08_second-brain-graph.md](EXC-08_second-brain-graph.md) |
| EXC-09 | Original Source DNA — Truy Vết Nguồn Gốc & Cây Sao Chép Của Một Cụm Tin | P1 (High) | 📋 Todo | [EXC-09_original-source-dna.md](EXC-09_original-source-dna.md) |
| EXC-10 | Retraction Radar — Phát Hiện Bài Báo Bị Sửa Âm Thầm Hoặc Bị Gỡ Bỏ | P1 (High) | 📋 Todo | [EXC-10_retraction-radar.md](EXC-10_retraction-radar.md) |
| EXC-11 | Temporal "Ask My Archive" — Hỏi Đáp Kho Lưu Trữ RSS Theo Mốc Thời Gian | P2 (Medium) | 📋 Todo | [EXC-11_temporal-ask-my-archive.md](EXC-11_temporal-ask-my-archive.md) |
| EXC-12 | AI Feed Autopilot — Tự Học Hành Vi Đọc & Gợi Ý Unsubscribe Feed Gây Nhiễu | P2 (Medium) | 📋 Todo | [EXC-12_ai-feed-autopilot.md](EXC-12_ai-feed-autopilot.md) |
| EXC-13 | Second Screen Mirror — Quét QR Mirror Bài Đang Đọc Sang Máy Tính Qua LAN | P3 (Low) | 📋 Todo | [EXC-13_second-screen-mirror.md](EXC-13_second-screen-mirror.md) |
| EXC-14 | Feed Zero Triage Mode — Chế Độ Vuốt Kiểu Tinder Xử Lý Toàn Bộ Bài Chưa Đọc | P2 (Medium) | 📋 Todo | [EXC-14_feed-zero-triage-mode.md](EXC-14_feed-zero-triage-mode.md) |

**Ghi chú:** EXC-06 đã được audit và xác nhận triển khai đầy đủ trong code (commit `d64bfee`, có unit/widget/integration test) nên đã chuyển sang `doc/task/done/` thay vì giữ ở `todo/`. EXC-08 đến EXC-14 là 7 tính năng độc quyền mới, ưu tiên tái sử dụng hạ tầng AI on-device đã có (`infrastructure/ai/clustering/StoryClusteringEngine.kt`, `infrastructure/ai/search/SemanticSearchEngine.kt`, `infrastructure/ai/ArticleMindMapExtractor.kt`, `infrastructure/ai/ArticleDeepReadEngine.kt`) thay vì xây dựng lại từ đầu.
