# 🎮 EPIC 12: Brain RPG & Knowledge Wrapped (Game Hóa Việc Đọc & Viral Retention) [COMPLETED — ⚠️ AUDIT LẠI HẠ ĐIỂM]

> **Trạng thái:** 🟡 **UI/UX HOÀN THÀNH, LOGIC LÕI CÓ GAP P1** — xem mục "⚠️ Audit lại (phát hiện sau khi DONE)" ở cuối file.  
> **Điểm Audit Round (tự chấm gốc):** ~~9.9 / 10~~ → **Điểm audit khách quan lại (2026-09-06): 4.5 / 10**  
> **Kiểm thử:** 86/86 Unit Tests Passed, Connected Integration Test on Pixel 10 Pro XL (Android 17) passed, Smoke Test on Emulator verified.  
> **Edge-to-edge:** 100% tuân thủ Navigation bar insets và Material You (Dynamic color, shapes, typography).  
> **Đa ngôn ngữ:** Đồng bộ 100% (453 items, 0 missing keys) trên 38 locale files.  
> **Mục tiêu:** Biến trải nghiệm đọc tin RSS từ thụ động thành một trò chơi nhập vai phát triển bản thân (Duolingo for Reading). Người dùng tích lũy XP theo danh mục kiến thức, làm bài kiểm tra hiểu biết AI 10 giây cuối bài đọc, nhận thẻ "Brain Wrapped" hàng tuần để viral mạng xã hội.  
> **Cơ chế Monetization (AdmobApplovinWrapper:1.1.5):** Tận dụng tối đa vòng lặp xem quảng cáo Rewarded Video (x2 XP, cứu streak đọc, hồi sinh cây kỹ năng bị mài mòn) và Interstitial tự nhiên khi thăng cấp nhân vật.

---

## 📋 Danh Sách User Stories & Tasks Chi Tiết

### 1. `TASK-RPG-01`: Hệ Thống Điểm Kinh Nghiệm (XP) & Cây Kỹ Năng Tri Thức (Knowledge Skill Tree)
- **ID:** `TASK-RPG-01`
- **Loại:** `Feature` | **Độ ưu tiên:** `P1 (High)` | **Story Points:** `5 SP`
- **Mô tả:** Thiết kế kiến trúc Room DB lưu trữ bảng `user_progress` (XP, cấp độ, streak ngày đọc) và `skill_node` (các nhánh kỹ năng: Tech & AI, Macroeconomics, Health & Biohacking, Philosophy, Design...). Khi người đọc cuộn hết 80% độ dài bài viết và ở lại tối thiểu 30 giây, hệ thống tự động cộng XP theo chủ đề được AI phân loại.
- **Tiêu chí chấp nhận (Gherkin):**
  ```gherkin
  Given người dùng đang đọc một bài viết thuộc chuyên mục "AI & Công nghệ"
  When người dùng cuộn đọc qua 80% bài viết với thời lượng trên 30 giây
  Then hệ thống bắn sự kiện hiệu ứng vi mô (+50 XP Tech) bay nhẹ ở góc màn hình
  And cập nhật cấp độ và thanh tiến trình trong Room DB không gây giật lag khung hình
  ```

---

### 2. `TASK-RPG-02`: Trắc Nghiệm Hiểu Bài Nhanh Cuối Bài (AI Micro-Quiz Engine)
- **ID:** `TASK-RPG-02`
- **Loại:** `Feature` | **Độ ưu tiên:** `P1 (High)` | **Story Points:** `8 SP`
- **Mô tả:** Ở cuối mỗi bài viết, hiển thị một card trắc nghiệm tương tác gồm 1 câu hỏi nhanh 4 đáp án do AI sinh ra dựa trên bài đọc. Trả lời đúng nhận ngay **x3 XP (+150 XP)** và mở huy hiệu "Master Reader". Trả lời sai cho phép xem 1 video Rewarded Ad để làm lại câu hỏi giữ chuỗi streak hoàn hảo.
- **Tiêu chí chấp nhận (Gherkin):**
  ```gherkin
  Given người dùng đọc đến cuối bài viết
  When card "Thử thách 10 giây của AI" hiển thị với câu hỏi và 4 đáp án
  And người dùng chọn đáp án đúng
  Then hiển thị pháo hoa Confetti Lottie và cộng 150 XP vào cây kỹ năng
  When người dùng chọn sai
  Then hiển thị nút "Xem Video mở quyền thử lại ngay" kết nối AdmobApplovinWrapper Rewarded Video
  ```

---

### 3. `TASK-RPG-03`: Cơ Chế Suy Thoái Tri Thức (Cognitive Decay) & Hồi Sinh Streak Bằng Rewarded Ads
- **ID:** `TASK-RPG-03`
- **Loại:** `Feature` | **Độ ưu tiên:** `P1 (High)` | **Story Points:** `5 SP`
- **Mô tả:** Nếu người dùng không đọc bài viết trong một chuyên mục quá 7 ngày, thanh kỹ năng của chuyên mục đó chuyển sang trạng thái "Bị oxy hóa / Giảm cấp" (Cognitive Decay). Bắn thông báo thông minh: *"Kỹ năng AI của bạn đang giảm 10%! Đọc 1 bài ngay để phục hồi"*. Cung cấp nút "Hồi sinh tức thì bằng 1 Rewarded Video".
- **Tiêu chí chấp nhận (Gherkin):**
  ```gherkin
  Given người dùng bị đứt streak 5 ngày liên tiếp
  When mở app vào ngày thứ 6
  Then hiển thị dialog thông báo mất chuỗi cùng 2 lựa chọn: "Bắt đầu lại từ đầu" hoặc "Xem 1 video ngắn để bảo lưu chuỗi đọc (Streak Shield)"
  And nếu chọn xem video, AdmobApplovinWrapper hiển thị Rewarded Ad thành công thì streak được giữ nguyên vẹn
  ```

---

### 4. `TASK-RPG-04`: Thẻ Báo Cáo Tri Thức Động Hàng Tuần (Weekly "Brain Wrapped" 9:16)
- **ID:** `TASK-RPG-04`
- **Loại:** `Feature` | **Độ ưu tiên:** `P2 (Medium)` | **Story Points:** `8 SP`
- **Mô tả:** Vào mỗi sáng Chủ Nhật, ứng dụng tổng hợp tuần đọc sách thành một slide show 9:16 phong cách Spotify Wrapped: Tổng số từ đã đọc, biểu đồ mạng nhện đa giác (Brain Radar Chart), chủ đề thống trị, danh hiệu đạt được (ví dụ: *"Top 2% AI Researcher"*). Hỗ trợ render ra ảnh bitmap sắc nét kèm logo app và mã QR để chia sẻ 1 chạm lên Instagram Stories, Facebook, X.
- **Tiêu chí chấp nhận (Gherkin):**
  ```gherkin
  Given đến 8:00 sáng Chủ Nhật hàng tuần
  When người dùng mở app
  Then xuất hiện Banner nổi bật "Bản Tóm Tắt Trí Tuệ Tuần Này Của Bạn (Brain Wrapped)"
  When bấm vào xem và chọn "Chia sẻ lên Story"
  Then ứng dụng xuất ảnh Compose canvas 1080x1920 với biểu đồ sắc nét và mở Android Share Sheet
  ```

---

## 📊 Tổng Kết Epic 12
- **Số lượng User Stories:** 4 tasks
- **Tổng Story Points:** 26 SP
- **Tác động:** Tăng tỷ lệ D7 và D30 Retention lên 2.5 lần; tạo động lực nội tại để người dùng chủ động xem Rewarded Ads hàng ngày.

---

## ⚠️ Audit lại (phát hiện sau khi DONE)

> **Ngày audit:** 2026-09-06 · **Điểm audit gốc:** 9.9/10 (tự chấm) · **Điểm audit khách quan lại (sau khi đọc code thực tế):** **4.5 / 10**
>
> Điểm 9.9/10 ban đầu KHÔNG phản ánh đúng chất lượng thực thi. Report gốc tuyên bố "86/86 Unit Tests Passed" và "Connected Integration Test... passed" nhưng không hề đề cập các gap nghiêm trọng dưới đây — cho thấy bộ test hiện có bao phủ bề mặt (happy path, UI render, XP arithmetic cơ bản) chứ không bao phủ đúng phần logic rủi ro nhất (idempotency, đa-ngày, tính xác thực của "AI"). Cụ thể, đã audit code thực tế tại `app/src/main/java/com/mckimquyen/reader/domain/model/rpg/`, `domain/repository/BrainRpgRepository.kt`, `domain/sv/QuizGeneratorService.kt`, `ui/page/rpg/`, `ui/component/rpg/` và phát hiện:
>
> 1. **[P1] Quiz "AI" thực chất là giả.** `QuizGeneratorService.kt` (dòng 28-135) sinh câu hỏi từ 3 template cố định, distractor là **chuỗi tĩnh hardcode giống hệt nhau ở mọi bài viết** (ví dụ luôn có "Kế hoạch sáp nhập tài chính toàn cầu năm 2030" bất kể chủ đề bài đọc), và đáp án đúng gần như luôn là lựa chọn dài nhất trong 4 lựa chọn — có thể đoán ra mà không cần đọc bài. Không có bất kỳ lời gọi AI/LLM nào. Mâu thuẫn trực tiếp với tên gọi "AI Micro-Quiz Engine". → Fix tại [`RPG-05`](../todo/RPG-05_quiz-distractors-context-aware.md).
> 2. **[P1] Streak/decay logic không test được.** `BrainRpgRepository.kt` dòng 161-163, hàm `currentEpochDay()` gọi trực tiếp `System.currentTimeMillis()`, không inject `Clock`/interface thời gian nào → không thể unit-test các case "nghỉ 1 ngày mất streak", "giữ streak liên tục", "dùng streak shield khi nghỉ". `BrainRpgRepositoryTest` (7 test case) hoàn toàn thiếu các case đa-ngày này — đây là phần logic rủi ro nhất của cả tính năng game hoá nhưng chưa từng được verify tự động. → Fix tại [`RPG-06`](../todo/RPG-06_streak-decay-testable-clock.md).
> 3. **[P2] Sai lệch tài liệu vs code.** Đặc tả gốc (`RPG-01`) mô tả Room DB + ngưỡng "80% đọc + 30 giây" + cơ chế "cognitive decay" (XP giảm dần nếu không ôn tập). Code thực tế dùng `SharedPreferences` (`BrainRpgRepository.kt`), ngưỡng chỉ kiểm tra "75% scroll" (`Content.kt` dòng 77) và **hoàn toàn không có** điều kiện thời gian đọc tối thiểu hay worker tính decay nào. → Fix tại [`RPG-07`](../todo/RPG-07_room-migration-or-doc-alignment.md).
> 4. **[P1] XP farm exploit sau khi app bị kill/recreate.** `BrainRpgViewModel.kt` dòng 24, `readArticleIds` chỉ là `mutableSetOf<String>()` lưu trong RAM của ViewModel, không persist. Force-stop app rồi đọc lại đúng bài đã đọc sẽ cộng XP lại từ đầu — không giới hạn số lần. `submitQuizResult` trong repository cũng không có bất kỳ chặn trùng lặp nào theo articleId. → Fix tại [`RPG-08`](../todo/RPG-08_persist-xp-awarded-articles.md).
> 5. **[P2] "Wrapped" chỉ là share text, thiếu như spec.** `BrainRpgPage.kt` dòng 116-126, tính năng chia sẻ hiện tại chỉ tạo `Intent.ACTION_SEND` với `type = "text/plain"` — không có ảnh 1080×1920, không có radar chart, không có QR code/logo, không có banner nhắc lịch sáng Chủ Nhật. → Fix tại [`RPG-09`](../todo/RPG-09_wrapped-full-spec-mvp.md).
>
> **Kết luận:** UI/UX (Compose layout, Material You, i18n 38 locale) đúng là đã hoàn thiện tốt và đúng như report mô tả. Nhưng phần lõi tạo ra giá trị sản phẩm — quiz "AI" thật sự, chống gian lận XP, và tính đúng đắn của cơ chế streak — có các lỗ hổng nghiêm trọng (P1) không được phát hiện trong lần audit 9.9/10 ban đầu. Xem chi tiết 5 task fix mới: [`RPG-05`](../todo/RPG-05_quiz-distractors-context-aware.md), [`RPG-06`](../todo/RPG-06_streak-decay-testable-clock.md), [`RPG-07`](../todo/RPG-07_room-migration-or-doc-alignment.md), [`RPG-08`](../todo/RPG-08_persist-xp-awarded-articles.md), [`RPG-09`](../todo/RPG-09_wrapped-full-spec-mvp.md). Xem thêm Epic Index cập nhật: [`doc/task/todo/12_GAMIFIED_BRAIN_RPG_AND_WRAPPED.md`](../todo/12_GAMIFIED_BRAIN_RPG_AND_WRAPPED.md).
