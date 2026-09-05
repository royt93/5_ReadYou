# 🌟 Epic 05: Tính Năng Độc Quyền & Killer Features (Market Differentiators)

> **Mục tiêu Epic:** Tạo ra những tính năng độc bản (Unfair Advantage) mà chưa có bất kỳ app RSS nào (kể cả Feedly, Inoreader, Read You gốc) trên thị trường sở hữu, biến RSS Cat Hub thành ứng dụng đọc tin AI số 1 trên Play Store.

---

### [EXC-01] "AI Daily Smart Digest" — Bản Tin Sáng Tổng Hợp Đa Nguồn Bằng AI
- **Type:** Exclusive Killer Feature / AI Innovation
- **Priority:** `P1 (High)`
- **Estimation:** `8 Story Points`
- **Location:** `app/src/main/java/com/mckimquyen/reader/ui/page/digest/` (Gói mới), [`infrastructure/ai/GeminiSummaryService.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/ai/GeminiSummaryService.kt)
- **Sự khác biệt vượt trội:** Các app hiện nay chỉ tóm tắt từng bài riêng lẻ. Người dùng thức dậy với 150 bài báo mới và không có thời gian đọc từng bài. "AI Daily Smart Digest" gom 20 bài hot nhất trong 24 giờ qua từ tất cả các chuyên mục, phân loại chủ đề (Kinh tế, Công nghệ, Thế giới, Đời sống) và xuất bản một bản tin thời sự cô đọng duy nhất dài 2 phút, có trích dẫn link nguồn từng bài.
- **User Story:**
  > Là người bận rộn thức dậy lúc 7:00 sáng,  
  > Tôi muốn mở app và chỉ cần ấn "Tạo Bản Tin Sáng 2 Phút",  
  > Để AI tổng hợp toàn bộ diễn biến tin tức quan trọng nhất trên thế giới vào một bài tổng quan mạch lạc duy nhất.
- **Acceptance Criteria:**
  - **Given** người dùng có nhiều feed tin tức với hơn 30 bài mới chưa đọc
  - **When** bấm vào nút "✨ Tạo Bản Tin Sáng" trên TopBar của `FlowPage`
  - **Then** AI Gemini phân tích tiêu đề và tóm tắt của 20 bài nổi bật nhất
  - **And** tạo ra một bài báo tổng hợp với cấu trúc chuyên nghiệp:
    - 💡 **Tiêu Điểm Hôm Nay** (3 tin chấn động nhất)
    - 📈 **Kinh Doanh & Công Nghệ**
    - 🌍 **Thế Giới 24 Giờ Qua**
  - **And** mỗi sự kiện đều có tag clickable link trỏ về bài báo gốc trong app
  - **And** bản tin được tự động lưu vào tab riêng để đọc lại bất cứ lúc nào.

---

### [EXC-02] "AI Podcast Studio" — Biến Tin Tức Thành Cuộc Trò Chuyện Audio 2 Người
- **Type:** Exclusive Killer Feature / Audio Innovation
- **Priority:** `P2 (Medium)`
- **Estimation:** `8 Story Points`
- **Location:** [`infrastructure/audio/TtsManager.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/audio/TtsManager.kt), [`infrastructure/ai/`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/ai/)
- **Sự khác biệt vượt trội:** Nghe TTS thông thường chỉ là đọc văn bản đơn điệu rất buồn ngủ. "AI Podcast Studio" đưa nội dung 3-5 bài báo vào prompt biến thành kịch bản đối thoại sinh động giữa 2 MC (Alex & Sam: "Chào Sam, cậu đã nghe tin Google vừa ra mắt Gemini thế hệ mới chưa?" — "Ồ, mình vừa xem xong, quả là một bước ngoặt..."). Hệ thống TTS sử dụng 2 voice pitch khác nhau để luân phiên thể hiện 2 MC.
- **User Story:**
  > Là người lái xe đi làm hoặc chạy bộ buổi sáng,  
  > Tôi muốn nghe tin tức dưới dạng một chương trình podcast trò chuyện tự nhiên dí dỏm,  
  > Để việc cập nhật tin tức trở nên cuốn hút và thú vị như nghe đài phát thanh.
- **Acceptance Criteria:**
  - **Given** người dùng chọn 1 hoặc nhiều bài viết trong danh sách
  - **When** chọn menu "🎙️ Phát Podcast Đối Thoại"
  - **Then** Gemini tạo ra kịch bản đối thoại hai người có cảm xúc, chuyển ý tự nhiên
  - **And** `TtsManager` chia kịch bản theo nhân vật và đổi giọng (Tone/Pitch) tương ứng
  - **And** hiển thị thanh Player sóng âm (waveform animation) đồng bộ khi giọng đọc phát.

---

### [EXC-03] Chế Độ Đọc Bionic Reading (Tăng Tốc Độ Đọc Cho Não Bộ)
- **Type:** Exclusive Reading UX
- **Priority:** `P2 (Medium)`
- **Estimation:** `3 Story Points`
- **Location:** [`ui/component/reader/AnnotatedString.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/component/reader/AnnotatedString.kt), [`ui/page/home/read/Content.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/page/home/read/Content.kt)
- **Sự khác biệt vượt trội:** Phương pháp Bionic Reading làm nổi bật (in đậm) các chữ cái đầu tiên của mỗi từ (ví dụ: "**Bio**nic **Rea**ding **gi**úp **m**ắt **d**i **chu**yển **nh**anh **h**ơn"). Não bộ tự động hoàn thiện phần còn lại, giúp người đọc tăng tốc độ từ 200 từ/phút lên 400-500 từ/phút, đặc biệt hữu ích cho người mắc chứng ADHD hoặc cần đọc nhanh lượng tài liệu lớn.
- **User Story:**
  > Là người cần đọc nhiều bài nghiên cứu và tin tức mỗi ngày,  
  > Tôi muốn bật chế độ Bionic Reading trong màn hình đọc,  
  > Để tôi đọc nhanh gấp đôi mà mắt ít bị mỏi hơn.
- **Acceptance Criteria:**
  - **Given** người dùng đang ở giao diện đọc bài (`ReadingPage`)
  - **When** bật toggle "Bionic Reading" trong thanh công cụ Style
  - **Then** toàn bộ đoạn văn bản được parse thành `AnnotatedString` in đậm 30-50% số ký tự đầu của mỗi từ
  - **And** hiệu năng xử lý văn bản không làm đơ trang (chuyển đổi dưới 50ms).

---

### [EXC-04] AI Clickbait Buster & Bộ Nhận Diện Thiên Lệch Tin Tức (Bias / Sentiment Indicator)
- **Type:** Exclusive AI Feature / Quality of Life
- **Priority:** `P2 (Medium)`
- **Estimation:** `5 Story Points`
- **Location:** [`ui/page/home/flow/ArticleItem.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/page/home/flow/ArticleItem.kt), [`infrastructure/ai/GeminiSummaryService.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/ai/GeminiSummaryService.kt)
- **Sự khác biệt vượt trội:** Các trang báo mạng thường giật tít câu view kiểu "Sự thật chấn động về...", "Ai cũng sốc khi biết điều này...". Tính năng Clickbait Buster phát hiện tít giật gân và tự động hiển thị câu trả lời cốt lõi ngay bên dưới tiêu đề, giúp người dùng không bị lừa bấm vào bài rác.
- **User Story:**
  > Là người ghét các tiêu đề giật gân câu click rẻ tiền,  
  > Tôi muốn app chỉ rõ nội dung cốt lõi của bài báo trước khi tôi phải bấm vào,  
  > Để tôi tiết kiệm thời gian và tránh tâm lý bức xúc.
- **Acceptance Criteria:**
  - **Given** danh sách bài báo trên `FlowPage`
  - **When** bài báo có tiêu đề dạng mồi chài (Clickbait Score > 70%)
  - **Then** hiển thị huy hiệu nhỏ "🎯 Giải Mã Tít" tóm tắt sự thật trong 1 câu ngắn
  - **And** hiển thị thang đo cảm xúc tin tức: Tích cực (Xanh), Trung lập (Xám), Cảnh báo/Tiêu cực (Đỏ).

---

### [EXC-05] Đồng Bộ Thiết Bị P2P Nội Mạng Wi-Fi Không Cần Máy Chủ (Local Peer-to-Peer Sync)
- **Type:** Exclusive Privacy Feature
- **Priority:** `P3 (Low)`
- **Estimation:** `5 Story Points`
- **Location:** `app/src/main/java/com/mckimquyen/reader/infrastructure/p2p/` (Gói mới)
- **Sự khác biệt vượt trội:** Người dùng có cả điện thoại và máy tính bảng Android (hoặc 2 điện thoại) nhưng không muốn dùng tài khoản cloud bên thứ 3 vì lo ngại lộ thông tin riêng tư. Tính năng này dùng Google Nearby Connections hoặc mDNS/NSD trên mạng LAN Wi-Fi để đồng bộ trạng thái đã đọc và feed trực tiếp giữa 2 máy khi cùng ở nhà.
- **User Story:**
  > Là người sử dụng cả máy tính bảng để đọc sách ở nhà và điện thoại khi ra ngoài,  
  > Tôi muốn hai máy tự động đồng bộ bài đọc khi cùng kết nối vào mạng Wi-Fi gia đình,  
  > Hoàn toàn không qua bất kỳ máy chủ trung gian nào trên internet.
- **Acceptance Criteria:**
  - **Given** 2 thiết bị Android cùng cài RSS Cat Hub và cùng bắt chung 1 mạng Wi-Fi
  - **When** mở app trên thiết bị thứ hai
  - **Then** hai máy tự nhận diện nhau qua giao thức Local NSD (Network Service Discovery)
  - **And** đồng bộ danh sách đã đọc và danh sách bài gắn sao trong vòng 3 giây
  - **And** dữ liệu được mã hóa đầu cuối (E2EE) qua khóa trao đổi nội bộ.

---

### [EXC-06] "AI Deep Read" — Trò Chuyện & Hỏi Đáp Tương Tác Với Bài Báo (Chat with Article)
- **Type:** Exclusive Killer Feature / Conversational AI
- **Priority:** `P2 (Medium)`
- **Estimation:** `5 Story Points`
- **Location:** [`ui/page/home/read/ReadingPage.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingPage.kt), [`infrastructure/ai/GeminiSummaryService.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/ai/GeminiSummaryService.kt)
- **Sự khác biệt vượt trội:** Đọc một bài phân tích chuyên sâu về kinh tế/chính trị/công nghệ thường có thuật ngữ khó hiểu hoặc số liệu phức tạp. Thay vì chỉ tóm tắt một chiều, người dùng có thể mở khung chat với bài báo: "Giải thích khái niệm X trong bài bằng ví dụ đời thường", "Tại sao tác giả lại phản đối đề xuất này?", "Liệt kê các rủi ro được nêu trong bài".
- **User Story:**
  > Là người thích nghiên cứu sâu kiến thức,  
  > Tôi muốn đặt câu hỏi trực tiếp cho AI về những điểm tôi chưa hiểu trong bài báo,  
  > Để tôi nắm bắt kiến thức trọn vẹn và đa chiều chỉ trong vài giây.
- **Acceptance Criteria:**
  - **Given** người dùng đang ở giao diện đọc bài (`ReadingPage`)
  - **When** bấm vào biểu tượng "💬 Hỏi đáp AI" trên BottomBar
  - **Then** xuất hiện BottomSheet chat tương tác với bài báo kèm 3 gợi ý câu hỏi thông minh được sinh tự động
  - **And** Gemini trả lời chính xác dựa trên ngữ cảnh toàn văn bài báo mà không bịa đặt (grounded answers)
  - **And** người dùng có thể gõ thêm câu hỏi tiếp nối tự do.

---

### [EXC-07] Chế Độ Đọc Song Ngữ & Dịch Đoạn Tức Thì (Bilingual Side-by-Side)
- **Type:** Exclusive Reader Feature / Localization
- **Priority:** `P2 (Medium)`
- **Estimation:** `5 Story Points`
- **Location:** [`ui/component/reader/Reader.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/component/reader/Reader.kt), [`infrastructure/ai/`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/ai/)
- **Sự khác biệt vượt trội:** Độc giả Việt Nam đọc nhiều nguồn tiếng Anh (TechCrunch, The Verge, Reuters, BBC). Tính năng này cho phép: 1) Chạm vào bất kỳ đoạn văn nào để bung bản dịch tiếng Việt ngay dưới đoạn đó (inline translation), hoặc 2) Chế độ song ngữ cột đôi trên máy tính bảng/màn hình gập.
- **User Story:**
  > Là người học ngoại ngữ hoặc thích đọc tin tức quốc tế,  
  > Tôi muốn xem bản dịch tiếng Việt mượt mà ngay cạnh đoạn văn gốc tiếng Anh/tiếng Nhật,  
  > Để tôi nâng cao vốn từ vựng và hiểu chính xác 100% nội dung bài báo.
- **Acceptance Criteria:**
  - **Given** một bài báo bằng tiếng nước ngoài (khác ngôn ngữ app)
  - **When** người dùng chạm vào một đoạn văn bất kỳ
  - **Then** đoạn văn hiển thị bản dịch tiếng Việt trôi chảy ngay bên dưới với độ trễ < 500ms (sử dụng Gemini hoặc On-Device ML Kit Translation)
  - **And** có nút chuyển đổi toàn bài sang chế độ song ngữ dòng xen kẽ (Interleaved bilingual).
