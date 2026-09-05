# ⚖️ EPIC 16: EchoChamber Destroyer & Bias Radar (Phá Vỡ Buồng Vang & Đấu Trường Phản Biện)

> **Mục tiêu:** Giúp độc giả thoát khỏi bẫy buồng vang thông tin (Echo Chamber) và định kiến xác nhận (Confirmation Bias). AI tự động đo lường độ thiên vị/cảm xúc của bài báo, cung cấp khung luận điểm phản biện đanh thép (Steelman Rebuttal), và mở sàn tranh luận trực tiếp bằng giọng nói với AI.  
> **Cơ chế Monetization (AdmobApplovinWrapper:1.1.5):** Native Advanced Ads hiển thị mượt mà giữa bài đọc và khung phản biện; Rewarded Ads mở khóa tính năng "Vào Sàn Đấu Tranh Luận Giọng Nói (Debate with AI)".

---

## 📋 Danh Sách User Stories & Tasks Chi Tiết

### 1. `TASK-ECH-01`: La Bàn Thiên Kiến & Đồng Hồ Cảm Xúc (Bias Radar & Sentiment Gauge)
- **ID:** `TASK-ECH-01`
- **Loại:** `Feature` | **Độ ưu tiên:** `P1 (High)` | **Story Points:** `5 SP`
- **Mô tả:** Phân tích văn bản bài viết để đánh giá trên 3 trục tọa độ: Cảm tính vs Khách quan (Objectivity Score), Xu hướng Lạc quan vs Bi quan (Market Sentiment), và Mức độ Thổi phồng PR (Hype Score). Hiển thị đồng hồ đo trực quan nhỏ gọn ngay dưới tiêu đề bài viết.
- **Tiêu chí chấp nhận (Gherkin):**
  ```gherkin
  Given bài viết có ngôn từ mang tính quảng bá PR quá mức
  When người dùng mở bài đọc
  Then thanh đo Hype Score hiển thị mức 85% kèm nhãn cảnh báo nhẹ màu cam: "Bài viết chứa nhiều yếu tố quảng cáo/thổi phồng"
  And người dùng bấm vào xem chi tiết các đoạn văn bản bị đánh giá thiên kiến
  ```

---

### 2. `TASK-ECH-02`: Khung Luận Điểm Phản Biện Thép (Steelman Rebuttal Box)
- **ID:** `TASK-ECH-02`
- **Loại:** `Feature` | **Độ ưu tiên:** `P1 (High)` | **Story Points:** `8 SP`
- **Mô tả:** Thay vì tấn công bài viết bằng luận điểm yếu (Strawman), AI xây dựng luận điểm phản biện mạnh mẽ nhất có thể (Steelman Argument). Khung này nằm ở cuối bài, trình bày góc nhìn đối lập với các lập luận chặt chẽ và nguồn tin tham khảo uy tín.
- **Tiêu chí chấp nhận (Gherkin):**
  ```gherkin
  Given người dùng đọc xong một bài viết ca ngợi một công nghệ mới
  When cuộn xuống phần cuối bài
  Then hiển thị khung màu xanh xám "Góc Nhìn Phản Biện (Steelman Perspective)"
  And liệt kê 3 rủi ro cốt lõi và luận chứng phản biện mà tác giả bài viết đã bỏ qua
  ```

---

### 3. `TASK-ECH-03`: Thẻ So Sánh Đối Kháng Viral (Dual-Perspective Quote Card 9:16)
- **ID:** `TASK-ECH-03`
- **Loại:** `Feature` | **Độ ưu tiên:** `P2 (Medium)` | **Story Points:** `5 SP`
- **Mô tả:** Cho phép xuất hình ảnh 9:16 chia đôi màn hình: Nửa trên là trích dẫn cốt lõi của bài báo, nửa dưới là luận điểm phản biện sắc sảo của AI. Định dạng cực kỳ bắt mắt để người dùng chia sẻ lên Threads, X, Facebook tạo cuộc tranh luận nảy lửa.
- **Tiêu chí chấp nhận (Gherkin):**
  ```gherkin
  Given người dùng xem khung Steelman Rebuttal
  When bấm nút "Xuất Thẻ Tranh Biện (Debate Card)"
  Then hệ thống tạo ảnh chia đôi với đồ họa tương phản (Đen - Neon / Trắng - Đỏ)
  And mở Android Share Sheet kèm hashtag #RSSCatHub #Debate
  ```

---

### 4. `TASK-ECH-04`: Sàn Đấu Tranh Luận Giọng Nói (Voice AI Debate Arena) & Rewarded Ads
- **ID:** `TASK-ECH-04`
- **Loại:** `Feature` | **Độ ưu tiên:** `P1 (High)` | **Story Points:** `8 SP`
- **Mô tả:** Một phòng đấu trí trực tiếp nơi người dùng có thể dùng microphone nói ra quan điểm của mình, và AI sẽ ngay lập tức đối đáp lại bằng giọng nói để phản biện theo thời gian thực (tối đa 3 hiệp). Tính năng cao cấp này được mở khóa miễn phí khi người dùng xem 1 lượt Rewarded Video từ `AdmobApplovinWrapper`.
- **Tiêu chí chấp nhận (Gherkin):**
  ```gherkin
  Given người dùng muốn tranh luận với AI về đề tài bài báo
  When bấm nút "Vào Sàn Tranh Luận Giọng Nói"
  Then ứng dụng kích hoạt AdmobApplovinWrapper Rewarded Ad
  When xem xong quảng cáo, màn hình Voice Arena mở ra với hiệu ứng sóng âm tương tác
  ```

---

## 📊 Tổng Kết Epic 16
- **Số lượng User Stories:** 4 tasks
- **Tổng Story Points:** 26 SP
- **Tác động:** Tạo sự khác biệt hoàn toàn với mọi app RSS truyền thống; tăng thời gian On-Screen Time (thời lượng trong app) và tăng mạnh doanh thu Native/Rewarded Ads.
