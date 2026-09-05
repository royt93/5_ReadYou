# 🌌 Epic 10: Ý Tưởng Đỉnh Cao & Mở Rộng Hệ Sinh Thái (Frontier & Ecosystem)

> **Mục tiêu Epic:** Định vị RSS Cat Hub như một nền tảng tri thức vượt thời đại: trang bị bầy AI Agent nghiên cứu chuyên sâu, kết nối mạng xã hội phi tập trung Web3, trực quan hóa vũ trụ tri thức 3D và hiện diện trên cả Ô tô (Android Auto) lẫn Đồng hồ (Wear OS).

---

### [FRONT-01] AI Multi-Agent Deep Dive — Báo Cáo Nghiên Cứu Đa Chiều Chuẩn McKinsey
- **Type:** Frontier AI / Agentic Intelligence
- **Priority:** `P1 (High)`
- **Estimation:** `8 Story Points`
- **Location:** `app/src/main/java/com/mckimquyen/reader/domain/ai/agents/` (Gói mới)
- **Bối cảnh:** Đối với những tin tức kinh tế, địa chính trị hoặc công nghệ phức tạp (ví dụ: biến động lãi suất ngân hàng trung ương, đột phá bán dẫn, khủng hoảng chuỗi cung ứng), một bài báo thông thường chỉ đưa ra một góc nhìn phiến diện. Các lãnh đạo và chuyên gia cần một bản báo cáo phân tích toàn diện đa chiều.
- **User Story:**
  > Là nhà đầu tư hoặc chuyên gia phân tích,  
  > Tôi muốn bấm nút "Nghiên cứu chuyên sâu (Deep Dive)" trên bất kỳ bài báo nào,  
  > Để bầy AI Agent tự động điều tra bối cảnh lịch sử, thu thập các quan điểm trái chiều và xuất ra một báo cáo nghiên cứu 1 trang chuyên nghiệp.
- **Acceptance Criteria:**
  - **Given** người dùng mở một bài báo phức tạp
  - **When** bấm nút "🔬 AI Deep Dive"
  - **Then** hệ thống khởi chạy chuỗi 3 Agent phân tích:
    1. *Context Agent:* Phân tích nguyên nhân cội rễ và diễn biến trong quá khứ
    2. *Critical Thinking Agent:* Tổng hợp các phản biện và góc nhìn đối lập từ các trường phái khác nhau
    3. *Synthesis Agent:* Lập bảng ma trận Tác động ngắn hạn & Dài hạn (Impact Matrix)
  - **And** xuất ra giao diện báo cáo đẹp như tài liệu tư vấn chiến lược McKinsey, có thể xuất thành file PDF 1 trang.

---

### [FRONT-02] Tích Hợp Mạng Xã Hội Phi Tập Trung Web3 (Nostr, Bluesky AT Protocol & Mastodon)
- **Type:** Decentralized Web / Web3
- **Priority:** `P2 (Medium)`
- **Estimation:** `8 Story Points`
- **Location:** `app/src/main/java/com/mckimquyen/reader/infrastructure/decentralized/` (Gói mới)
- **Bối cảnh:** Làn sóng chuyển dịch khỏi các mạng xã hội truyền thống (vốn bị kiểm duyệt gắt gao và thuật toán quảng cáo độc hại) sang các mạng phi tập trung như Nostr (dùng mã hóa công khai npub), Bluesky (AT Protocol) và Mastodon (ActivityPub).
- **User Story:**
  > Là người yêu thích tự do ngôn luận và mạng phi tập trung,  
  > Tôi muốn theo dõi trực tiếp các tài khoản Nostr, Bluesky hoặc Mastodon yêu thích ngay trong app,  
  > Để tôi đọc được tin tức gốc từ các tác giả uy tín mà không cần cài thêm các app mạng xã hội gây nghiện.
- **Acceptance Criteria:**
  - **Given** người dùng nhập khóa công khai Nostr (`npub...`) hoặc handle Bluesky (`@user.bsky.social`)
  - **When** app kết nối tới Nostr Relays qua WebSocket hoặc AT Protocol API
  - **Then** các bài đăng mới nhất (Notes/Posts) được hiển thị sạch sẽ như một kênh tin RSS thông thường
  - **And** không có thuật toán thao túng, hiển thị 100% theo trình tự thời gian thuần khiết.

---

### [FRONT-03] Ngân Hà Tri Thức 3D — Trực Quan Hóa "Bộ Não Số" (3D Knowledge Galaxy)
- **Type:** Visual Innovation / Gamified Knowledge
- **Priority:** `P2 (Medium)`
- **Estimation:** `8 Story Points`
- **Location:** `app/src/main/java/com/mckimquyen/reader/ui/page/galaxy/` (Gói mới)
- **Bối cảnh:** Đọc nhiều bài viết nhưng không thấy được bức tranh tổng thể các kiến thức liên kết với nhau như thế nào. Trực quan hóa tri thức theo dạng mạng lưới ngân hà 3D mang lại cảm giác thành tựu và khích lệ người dùng tích cực học tập.
- **User Story:**
  > Là người ham học hỏi và thích trực quan hóa tư duy,  
  > Tôi muốn xem không gian 3D biểu diễn toàn bộ các bài viết tôi đã đọc như một dải ngân hà,  
  > Để tôi chiêm ngưỡng kho tàng kiến thức của mình và thấy được mối liên hệ thú vị giữa các chủ đề.
- **Acceptance Criteria:**
  - **Given** người dùng mở tab "Vũ Trụ Tri Thức"
  - **When** màn hình Canvas 3D (sử dụng Compose Canvas hoặc OpenGL Shader siêu nhẹ) khởi chạy
  - **Then** mỗi bài viết đã đọc được biểu diễn như 1 vì sao phát sáng; các bài viết cùng chủ đề hoặc cùng tag nối với nhau bằng các đường tơ sáng (Constellations)
  - **And** người dùng có thể dùng 2 ngón tay xoay, thu phóng và chạm vào từng ngôi sao để mở lại bài viết
  - **And** số lượng sao càng nhiều thì ngân hà càng rực rỡ (tăng dopamine tích cực khi đọc sách).

---

### [FRONT-04] Mở Rộng Hệ Sinh Thái: Android Auto (Xe Hơi) & Wear OS (Đồng Hồ)
- **Type:** Multi-Device Ecosystem / Car & Wearable
- **Priority:** `P3 (Low)`
- **Estimation:** `8 Story Points`
- **Location:** `wear/` và `auto/` (Modules mở rộng mới)
- **Bối cảnh:** Thói quen tiêu thụ thông tin của con người diễn ra liên tục: khi chạy bộ/tập gym (dùng đồng hồ thông minh) và khi lái xe đi làm/về nhà (dùng màn hình ô tô Android Auto).
- **User Story:**
  > Là người thường xuyên lái xe và chạy bộ ngoài trời,  
  > Tôi muốn nghe bản tin tin tức buổi sáng ngay trên màn hình xe hơi và điều khiển đọc báo từ đồng hồ đeo tay,  
  > Để việc cập nhật tin tức luôn liền mạch mọi lúc mọi nơi một cách an toàn.
- **Acceptance Criteria:**
  - **Android Auto:**
    - Cung cấp giao diện Media App chuẩn cho xe hơi với danh sách phát "Bản Tin Sáng" và "Podcast Mới Nhất"
    - Nút bấm to bản, hỗ trợ phím bấm vô-lăng và Google Assistant ra lệnh bằng giọng nói: "Phát tin tức tiếp theo".
  - **Wear OS:**
    - Hỗ trợ Wear OS Tile hiển thị 3 tiêu đề tin nóng nhất
    - Nút bấm nhanh để phát Text-to-Speech ra tai nghe Bluetooth kết nối với đồng hồ mà không cần móc điện thoại ra khỏi túi.
