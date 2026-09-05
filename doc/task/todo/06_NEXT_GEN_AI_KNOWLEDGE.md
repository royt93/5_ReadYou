# 🧠 Epic 06: AI Thế Hệ Mới & Quản Trị Tri Thức (Next-Gen AI & Knowledge)

> **Mục tiêu Epic:** Biến ứng dụng đọc tin thụ động thành cỗ máy quản trị tri thức cá nhân (Second Brain), giải quyết triệt để nạn ngập lụt tin tức trùng lặp và hỗ trợ tìm kiếm bằng ý nghĩa ngữ nghĩa.

---

### [KNOW-01] AI Deduplication & Story Clustering (Gom Cụm Tin Tức Trùng Lặp)
- **Type:** AI / Information Architecture
- **Priority:** `P1 (High)`
- **Estimation:** `8 Story Points`
- **Location:** `app/src/main/java/com/mckimquyen/reader/domain/ai/clustering/` (Gói mới)
- **Bối cảnh:** Khi có một sự kiện thời sự hoặc công nghệ nóng (ví dụ: Apple ra mắt iPhone mới, bầu cử, thiên tai), 20 tờ báo cùng đưa tin về 1 chủ đề, khiến bảng tin `FlowPage` bị ngập lụt hàng chục bài viết có nội dung tương tự nhau.
- **User Story:**
  > Là độc giả theo dõi nhiều nguồn tin,  
  > Tôi muốn các bài viết cùng nói về một sự kiện được AI tự động gom thành 1 Thẻ Sự Kiện (Story Card) duy nhất,  
  > Để tôi nắm bắt toàn cảnh sự kiện từ nhiều góc nhìn mà không phải cuộn qua 20 bài trùng lặp.
- **Acceptance Criteria:**
  - **Given** chu kỳ đồng bộ tải về nhiều bài viết trong vòng 24 giờ
  - **When** thuật toán so khớp ngữ nghĩa phát hiện độ tương đồng nội dung > 75%
  - **Then** gom các bài viết đó vào một cụm sự kiện chung
  - **And** trên `FlowPage` chỉ hiển thị 1 thẻ đại diện với tiêu đề tổng quát nhất kèm huy hiệu (ví dụ: "🔥 8 nguồn tin cùng đưa tin")
  - **And** khi chạm vào thẻ, bung danh sách các góc nhìn từ các báo khác nhau (Tuổi Trẻ, VnExpress, BBC, Reuters).

---

### [KNOW-02] On-Device Semantic Search (Tìm Kiếm Ngữ Nghĩa Bằng Vector Embeddings)
- **Type:** AI / Search Engine
- **Priority:** `P2 (Medium)`
- **Estimation:** `8 Story Points`
- **Location:** `app/src/main/java/com/mckimquyen/reader/infrastructure/ai/search/` (Gói mới)
- **Bối cảnh:** Tìm kiếm văn bản truyền thống (LIKE hoặc FTS) bắt buộc người dùng phải nhớ chính xác từ khóa. Nếu người dùng gõ "công nghệ năng lượng sạch", app sẽ bỏ sót các bài viết chứa "tấm pin mặt trời", "tuabin gió", "nhiên liệu hydro".
- **User Story:**
  > Là người cần tra cứu thông tin theo khái niệm và ý nghĩa,  
  > Tôi muốn tìm kiếm bài viết bằng câu hỏi tự nhiên,  
  > Để tìm ra chính xác các bài liên quan dù tiêu đề không chứa đúng từ khóa đó.
- **Acceptance Criteria:**
  - **Given** kho lưu trữ bài viết đã được sinh vector embedding (sử dụng On-Device MediaPipe Text Embedder hoặc mô hình nhúng siêu nhẹ)
  - **When** người dùng gõ câu truy vấn tự nhiên vào thanh tìm kiếm
  - **Then** hệ thống tính toán khoảng cách cosine similarity và trả về kết quả xếp hạng theo mức độ liên quan ngữ nghĩa trong vòng < 50ms
  - **And** hoạt động 100% offline trên thiết bị, bảo mật tuyệt đối không gửi lịch sử tìm kiếm lên internet.

---

### [KNOW-03] Hệ Thống Sổ Tay Highlight & Xuất Sơ Đồ Tư Duy (Mindmap to Notion/Obsidian)
- **Type:** Productivity / Knowledge Management
- **Priority:** `P2 (Medium)`
- **Estimation:** `5 Story Points`
- **Location:** `app/src/main/java/com/mckimquyen/reader/ui/page/notebook/` (Gói mới)
- **Bối cảnh:** Người đọc chuyên sâu thường muốn lưu lại các câu trích dẫn đắt giá, thêm ghi chú cá nhân và tổng hợp các kiến thức đã học vào các công cụ quản lý tri thức như Notion, Obsidian, Logseq.
- **User Story:**
  > Là người học tập và nghiên cứu suốt đời qua RSS,  
  > Tôi muốn bôi đậm highlight nhiều màu trong bài báo và xuất toàn bộ ghi chú sang Notion hoặc Markdown,  
  > Để tích hợp mượt mà vào kho tri thức Second Brain của tôi.
- **Acceptance Criteria:**
  - **Given** người dùng bôi đen một đoạn văn bản trong `ReadingPage`
  - **When** chọn màu highlight (Vàng, Xanh lam, Hồng) và gõ ghi chú phản biện
  - **Then** highlight được lưu vĩnh viễn và hiển thị đồng bộ khi mở lại bài báo
  - **And** cung cấp trang "Sổ Tay Tri Thức" quản lý tập trung toàn bộ trích dẫn đã lưu
  - **And** hỗ trợ nút xuất 1-chạm: Xuất ra Markdown/Mermaid Mindmap hoặc đồng bộ trực tiếp lên Notion qua Notion API.
