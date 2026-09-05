# 🔮 EPIC 13: The Oracle Feed (Thị Trường Dự Đoán Tin Tức Kiểu Polymarket Trong RSS)

> **Mục tiêu:** Biến việc đọc tin tức tĩnh thành một thị trường dự đoán xu hướng tương lai bằng điểm ảo "Intel Points". AI tự động nhận diện các khẳng định tương lai từ RSS feed (công nghệ, crypto, bầu cử, thể thao, tài chính), mở kèo dự đoán, và tự động phân xử khi sự kiện diễn ra.  
> **Cơ chế Monetization (AdmobApplovinWrapper:1.1.5):** Người dùng cạn điểm Intel Points sẽ tự nguyện bấm xem 3–5 Rewarded Videos mỗi ngày để nạp thêm điểm cược và mở khóa phân tích xác suất "AI Oracle Edge".

---

## 📋 Danh Sách User Stories & Tasks Chi Tiết

### 1. `TASK-ORA-01`: Bộ Trích Xuất Kèo Dự Báo Từ Tin Tức (AI Predictive Claim Extractor)
- **ID:** `TASK-ORA-01`
- **Loại:** `Feature` | **Độ ưu tiên:** `P1 (High)` | **Story Points:** `5 SP`
- **Mô tả:** Khi RSS bài viết được nạp vào, prompt AI chạy ngầm phân tích xem bài viết có chứa các mốc sự kiện/dự báo tương lai có thể kiểm chứng được không (ví dụ: *"OpenAI dự kiến ra mắt GPT-5 vào tháng 11"*, *"Tesla cam kết bàn giao Cybercab vào năm 2026"*). Nếu có, sinh ra 1 card dự đoán chuẩn: Câu hỏi, Ngày hết hạn, và Tiêu chí giải quyết (Resolution Criteria).
- **Tiêu chí chấp nhận (Gherkin):**
  ```gherkin
  Given bài viết công nghệ chứa thông tin về sự kiện ra mắt sản phẩm sắp tới
  When AI phân tích nội dung hoàn tất
  Then tạo ra một bản ghi trong bảng `oracle_market` với câu hỏi nhị phân (Yes/No) và ngày đáo hạn
  And hiển thị huy hiệu "Kèo Dự Đoán" phát sáng lấp lánh trên tiêu đề bài viết
  ```

---

### 2. `TASK-ORA-02`: Giao Diện Đặt Cược Intel Points & Sàn Giao Dịch Tin Tức (Compose Prediction Sheet)
- **ID:** `TASK-ORA-02`
- **Loại:** `Feature` | **Độ ưu tiên:** `P1 (High)` | **Story Points:** `8 SP`
- **Mô tả:** Xây dựng BottomSheet Material 3 trực quan cho phép người dùng đặt cược 50, 100, 500 Intel Points vào cửa "Sẽ Xảy Ra (Yes)" hoặc "Không Xảy Ra (No)". Hiển thị tỷ lệ cược động (Odds Ratio) dựa trên tỷ lệ phiếu bầu của cộng đồng người đọc nội bộ.
- **Tiêu chí chấp nhận (Gherkin):**
  ```gherkin
  Given người dùng mở card dự đoán của bài viết
  When chọn cửa "Yes" và bấm "Đặt cược 200 Intel Points"
  Then hệ thống trừ điểm trong bảng `user_wallet`, phát âm thanh chip đặt cược
  And hiển thị phần trăm đồng thuận của cộng đồng (ví dụ: 68% Yes - 32% No)
  ```

---

### 3. `TASK-ORA-03`: Agent Tự Động Thẩm Định & Chốt Kèo (Automated Arbiter Agent)
- **ID:** `TASK-ORA-03`
- **Loại:** `Feature` | **Độ ưu tiên:** `P2 (Medium)` | **Story Points:** `8 SP`
- **Mô tả:** Định kỳ mỗi ngày, một tác vụ nền WorkManager kiểm tra các kèo dự đoán đã đến ngày đáo hạn. Agent quét các RSS feed mới hoặc truy vấn tin tức để xác định kết quả thực tế, đóng kèo và tự động chia thưởng điểm Intel Points cho người thắng kèm thông báo đẩy vinh danh.
- **Tiêu chí chấp nhận (Gherkin):**
  ```gherkin
  Given một kèo dự đoán đã tới ngày giải quyết
  When Arbiter Agent tìm thấy bài báo xác nhận sự kiện đã thành công
  Then trạng thái kèo chuyển thành RESOLVED_YES
  And cộng điểm thưởng tỷ lệ tương ứng cho tất cả người dùng chọn đúng
  And gửi Push Notification: "Chúc mừng! Kèo dự đoán của bạn đã thắng +850 Intel Points"
  ```

---

### 4. `TASK-ORA-04`: Kinh Tế Điểm Thưởng & Cỗ Máy In eCPM Rewarded Ads
- **ID:** `TASK-ORA-04`
- **Loại:** `Feature` | **Độ ưu tiên:** `P0 (Critical Monetization)` | **Story Points:** `5 SP`
- **Mô tả:** Xây dựng hệ thống nạp điểm Intel Points thông qua Rewarded Video của `AdmobApplovinWrapper:1.1.5`. Cung cấp 2 tính năng độc quyền:
  1. *Nạp Năng Lượng:* Xem 1 video = +500 Intel Points (tối đa 5 lần/ngày).
  2. *AI Oracle Edge:* Xem 1 video để mở khóa phân tích chuyên sâu của AI về xác suất xảy ra sự kiện dựa trên dữ liệu lịch sử.
- **Tiêu chí chấp nhận (Gherkin):**
  ```gherkin
  Given người dùng còn dưới 50 Intel Points
  When bấm nút "Nạp Thêm Điểm Miễn Phí"
  Then gọi AdmobApplovinWrapper.showRewardedVideo()
  And khi callback onUserEarnedReward kích hoạt, cộng ngay 500 Points vào ví
  And hiển thị Banner cảm ơn nhà tài trợ
  ```

---

## 📊 Tổng Kết Epic 13
- **Số lượng User Stories:** 4 tasks
- **Tổng Story Points:** 26 SP
- **Tác động:** Tạo ra mức độ gắn kết tương tác cực cao (High Replayability); biến người đọc thụ động thành nhà phân tích chủ động; thúc đẩy chỉ số Impression Per User của Rewarded Ad tăng vọt.
