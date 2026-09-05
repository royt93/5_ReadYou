# 💡 Epic 04: Ý Tưởng Trải Nghiệm & Tăng Trưởng (Ideas & Engagement)

> **Mục tiêu Epic:** Tăng tỷ lệ gắn kết và tương tác hàng ngày (Daily Active Users) thông qua thống kê thói quen đọc, tương tác Widget màn hình chính và hiệu ứng lan truyền xã hội (Viral Quote Card).

---

### [IDEA-01] Bảng Thống Kê Thói Quen Đọc Sách & Biểu Đồ Hoạt Động (Reading Habits & Heatmap)
- **Type:** User Engagement / Gamification
- **Priority:** `P2 (Medium)`
- **Estimation:** `5 Story Points`
- **Location:** `app/src/main/java/com/mckimquyen/reader/ui/page/statistics/` (Gói mới)
- **Bối cảnh:** Những người thích đọc tin tức thường muốn theo dõi tiến độ đọc, số bài đọc mỗi tuần, chủ đề đọc nhiều nhất và chuỗi ngày đọc liên tục (reading streak).
- **User Story:**
  > Là độc giả thích xây dựng thói quen đọc hàng ngày,  
  > Tôi muốn xem biểu đồ hoạt động dạng GitHub Heatmap và thống kê số bài đã đọc,  
  > Để tôi duy trì cảm hứng đọc tin tức và phát triển thói quen tốt.
- **Acceptance Criteria:**
  - **Given** người dùng mở trang "Thống kê đọc" trong Settings
  - **When** màn hình tải dữ liệu từ Room
  - **Then** hiển thị biểu đồ nhiệt (Heatmap grid 365 ngày) thể hiện mật độ đọc bài theo ngày
  - **And** thống kê: Tổng số bài đã đọc, Tốc độ đọc trung bình (từ/phút), Top 5 nguồn tin được đọc nhiều nhất
  - **And** tính toán chuỗi ngày đọc liên tục (Streak: ví dụ 7 ngày, 30 ngày) kèm huy hiệu động viên.

---

### [IDEA-02] Widget Màn Hình Chính Tương Tác Bằng Jetpack Glance (Material You)
- **Type:** User Engagement / Widget
- **Priority:** `P2 (Medium)`
- **Estimation:** `5 Story Points`
- **Location:** `app/src/main/java/com/mckimquyen/reader/ui/widget/` (Gói mới)
- **Bối cảnh:** Rất nhiều độc giả muốn xem nhanh tiêu đề tin tức ngay từ màn hình chính (Home Screen Launcher) mà không cần phải mở app.
- **User Story:**
  > Là người thích cập nhật tin tức nhanh trong ngày,  
  > Tôi muốn đặt Widget danh sách bài viết trên màn hình chính,  
  > Để tôi có thể cuộn xem tin mới và bấm dấu sao hoặc mở bài đọc chỉ bằng một chạm.
- **Acceptance Criteria:**
  - **Given** widget kích thước 4x2 hoặc 4x3 trên Android Home Launcher
  - **When** chu kỳ đồng bộ chạy có bài viết mới
  - **Then** Widget tự động cập nhật danh sách bài viết mới nhất
  - **And** giao diện widget tự động đổi màu theo Material You Dynamic Color của hình nền
  - **And** có nút bấm trực tiếp trên widget để đánh dấu đã đọc hoặc chuyển nhanh sang bài kế tiếp.

---

### [IDEA-03] Trình Tạo Ảnh Trích Dẫn "Quote Card" Chia Sẻ Mạng Xã Hội
- **Type:** Viral Growth / Social Sharing
- **Priority:** `P3 (Low)`
- **Estimation:** `3 Story Points`
- **Location:** [`ui/page/home/read/Content.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/page/home/read/Content.kt), [`ui/page/home/read/ReadingPage.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingPage.kt)
- **Bối cảnh:** Khi đọc được một câu nói hay hoặc một đoạn phân tích sâu sắc, người dùng thường có thói quen chụp màn hình để đăng lên Facebook, X (Twitter), Threads, LinkedIn.
- **User Story:**
  > Là người thích chia sẻ kiến thức lên mạng xã hội,  
  > Tôi muốn bôi đen một đoạn văn hay trong bài báo và ấn "Tạo thiệp trích dẫn",  
  > Để ứng dụng tạo ra bức ảnh đẹp sang trọng có trích dẫn, tên tác giả, nguồn báo và watermark RSS Cat Hub.
- **Acceptance Criteria:**
  - **Given** người dùng chọn văn bản trong màn hình đọc bài
  - **When** menu hành động hiện lên và chọn "Chia sẻ trích dẫn"
  - **Then** xuất hiện BottomSheet cho phép chọn template thẻ (Minimalist, Gradient Material, Dark Studio)
  - **And** xuất ra file ảnh PNG chất lượng cao sẵn sàng chia sẻ trực tiếp sang Threads/Facebook/X.

---

### [IDEA-04] Chuỗi Thưởng Xem Quảng Cáo & Đọc Tin Nhận VIP (Gamified Ad Streaks)
- **Type:** Monetization / User Retention
- **Priority:** `P2 (Medium)`
- **Estimation:** `3 Story Points`
- **Location:** [`ui/page/setting/vip/VipManagementPage.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/page/setting/vip/VipManagementPage.kt)
- **Bối cảnh:** Hiện tại chỉ có 1 nút bấm "Xem ad nhận 3 ngày VIP". Người dùng xem 1 lần rồi thôi. Nếu biến thành chuỗi nhiệm vụ hàng ngày: "Check-in đọc báo + Xem 1 ad mỗi ngày để cộng dồn chuỗi VIP", tỷ lệ người dùng quay lại app hàng ngày (DAU) và số lượt hiển thị Rewarded Ad (AppLovin/AdMob eCPM cao nhất) sẽ tăng vọt 300%.
- **User Story:**
  > Là người dùng miễn phí muốn dùng VIP lâu dài,  
  > Tôi muốn check-in xem 1 quảng cáo ngắn mỗi sáng để cộng dồn ngày VIP và duy trì chuỗi đọc,  
  > Để tôi vừa có động lực đọc báo mỗi ngày vừa được hưởng trọn vẹn quyền lợi VIP.
- **Acceptance Criteria:**
  - **Given** người dùng mở màn hình Quản Lý VIP
  - **When** bấm nút "Check-in nhận VIP" và xem hết 1 video quảng cáo
  - **Then** app cộng thêm 24h hoặc 3 ngày VIP vào tài khoản thông qua `AdManager`
  - **And** hiển thị thanh tiến độ chuỗi ngày (Streak 1/7 ngày, 7/7 ngày thưởng thêm 7 ngày VIP)
  - **And** gửi thông báo nhắc nhẹ vào giờ người dùng thường đọc báo nếu chưa check-in.

---

### [IDEA-05] Trình Phát Podcast & Audio RSS Player Tích Hợp
- **Type:** Media / Audio RSS
- **Priority:** `P2 (Medium)`
- **Estimation:** `5 Story Points`
- **Location:** `app/src/main/java/com/mckimquyen/reader/infrastructure/audio/player/` (Gói mới)
- **Bối cảnh:** Hàng ngàn nguồn RSS thực chất là podcast hoặc bản tin âm thanh có đính kèm file MP3/AAC trong thẻ `<enclosure url="..." type="audio/mpeg">`. Hiện tại app bỏ qua hoặc chỉ hiển thị như bài viết chữ thông thường, không phát được âm thanh gốc.
- **User Story:**
  > Là người thích nghe podcast tin tức (VnExpress Podcast, BBC World Service, Dan Carlin),  
  > Tôi muốn ứng dụng nhận diện nguồn podcast và cung cấp trình phát âm thanh chuyên nghiệp,  
  > Để tôi có thể vừa đọc báo chữ vừa nghe podcast trong cùng 1 ứng dụng duy nhất.
- **Acceptance Criteria:**
  - **Given** feed có chứa thẻ audio enclosure
  - **When** người dùng mở bài viết
  - **Then** hiển thị Mini Player nổi ở đáy màn hình với thanh thời lượng (Seekbar)
  - **And** hỗ trợ phát nền khi tắt màn hình, tua 15s trước/sau và điều chỉnh tốc độ 1x-2x
  - **And** có chế độ hẹn giờ tắt (Sleep Timer: 15, 30, 45 phút) cho người thích nghe trước khi ngủ.

---

### [IDEA-06] Chế Độ Tối Ưu Cho Màn Hình Giấy Điện Tử E-Ink (E-Paper Mode)
- **Type:** Accessibility / Specialized UX
- **Priority:** `P3 (Low)`
- **Estimation:** `3 Story Points`
- **Location:** [`ui/page/setting/color/ColorAndStylePage.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/page/setting/color/ColorAndStylePage.kt), [`ui/page/home/read/Content.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/page/home/read/Content.kt)
- **Bối cảnh:** Rất nhiều độc giả trung thành của RSS sử dụng máy đọc sách Android chạy màn hình E-Ink (Onyx Boox, Meebook, Xiaomi InkPalm). Màn hình E-Ink có tần số quét thấp, màu xám nhạt và bóng mờ khi cuộn mượt.
- **User Story:**
  > Là người đọc tin tức trên máy đọc sách E-Ink,  
  > Tôi muốn có chế độ hiển thị đơn sắc thuần túy (đen trắng tuyệt đối) và chuyển trang theo từng trang (Tap to turn page),  
  > Để màn hình không bị bóng mờ và pin máy đọc sách dùng được cả tuần.
- **Acceptance Criteria:**
  - **Given** người dùng bật chế độ "Tối ưu hóa E-Ink" trong Cài đặt giao diện
  - **When** vào màn hình đọc bài
  - **Then** toàn bộ hình ảnh và giao diện chuyển về thang độ tương phản cao (Pure Black & White)
  - **And** tắt toàn bộ hiệu ứng chuyển động (animations = 0ms)
  - **And** cho phép chạm vào 1/3 mép trái/phải màn hình hoặc bấm phím âm lượng để sang trang tiếp theo.
