# EXC-04 — AI Clickbait Buster & Headline Sentiment Indicator — DONE

**Hoàn tất:** 2026-10-31 · **Nhánh:** `dev` · **Trạng thái push:** chưa push

## Tính năng
Chấm điểm giật tít (0–100) và xác định sắc thái cảm xúc (🟢 Tích cực / ⚪ Chưa rõ / 🔴 Tiêu cực) của bài viết hoàn toàn cục bộ trên thiết bị bằng luật từ vựng, chạy trên tiêu đề ngắn (tối đa 240 ký tự), không gọi Gemini/AI/mạng, không đọc thân bài (giữ FIX-17), không đụng schema Room.
- **Danh sách (`ArticleItem`):** hiển thị huy hiệu `🎯 Giật tít [điểm]%` chỉ khi điểm > 70%; hiển thị kèm sắc thái cảm xúc (`🟢 Tích cực`, `⚪ Chưa rõ`, `🔴 Tiêu cực`) với ngôn ngữ được hỗ trợ. Ngôn ngữ lạ/không nhận diện được thì không hiển thị huy hiệu nào.
- **Màn hình đọc (`Content`):** hiển thị sắc thái cảm xúc phía trên tiêu đề/metadata; huy hiệu giật tít không lặp lại ở đây.
- **Cài đặt:** toggle bật/tắt trong *Cài đặt → Quyền riêng tư & Bảo mật*, mặc định BẬT; tắt sẽ ẩn toàn bộ huy hiệu giật tít và cảm xúc.
- **Đa ngôn ngữ:** bổ sung 11 khóa chuỗi đầy đủ cho 38 locale. Bản dịch tiếng Anh và tiếng Việt được soạn tay; tiếng Tây Ban Nha, Pháp, Đức mang tính thử nghiệm; các locale còn lại dùng bản sao tiếng Anh (cùng cách STRAT-03/EXC-10).

## Quyết định của owner (AskUserQuestion)
1. Dùng **heuristic cục bộ trên thiết bị**, không gọi Gemini (loại bỏ hoàn toàn chi phí API, không phụ thuộc key mặc định trong FIX-09).
2. Triển khai **cả hai tính năng trong EXC-04** (clickbait + cảm xúc); module phân tích này sẽ được tái dùng/mở rộng cho ECHO-01 sau.
3. Hỗ trợ bước đầu 5 ngôn ngữ: **Anh, Việt, Tây Ban Nha, Pháp, Đức** (3 ngôn ngữ sau gắn nhãn thử nghiệm, ngưỡng thận trọng).
4. Sắc thái cảm xúc luôn hiển thị 1 trong 3 trạng thái với ngôn ngữ được hỗ trợ; trạng thái màu xám được gọi trung thực là **"Chưa rõ"** (`UNCLEAR`), phản ánh việc không đủ dấu hiệu từ điển chứ không khẳng định bài viết trung lập tuyệt đối.
5. Loại bỏ yêu cầu "tóm tắt sự thật trong 1 câu" của spec gốc vì heuristic từ vựng không thể diễn giải nội dung bài viết đáng tin cậy.

## Cấu trúc mã
- `app/src/main/java/com/mckimquyen/reader/domain/headline/`:
  - `HeadlineAnalyzer.kt`: interface, quy tắc nhận diện ngôn ngữ thận trọng (ưu tiên dấu tiếng Việt, phân biệt tiếng Pháp/Tây Ban Nha), chấm điểm giật tít có trần 0..100, xác định sắc thái bằng chênh lệch từ điển, ngưỡng > 70.
  - `HeadlineLexicon.kt`: từ điển mồi chài, từ ngữ cường điệu, mẫu câu hỏi tò mò, từ điển cảm xúc tích cực/tiêu cực cho 5 ngôn ngữ.
- `app/src/main/java/com/mckimquyen/reader/infrastructure/pref/HeadlineSignalsPref.kt`: Preference DataStore, mặc định BẬT.
- `app/src/main/java/com/mckimquyen/reader/ui/page/headline/`:
  - `HeadlineSignalBadges.kt`: composable hiển thị huy hiệu giật tít và cảm xúc với nhãn văn bản + icon + màu + `contentDescription` trợ năng.
  - `HeadlineSignalsState.kt`: composition local `LocalHeadlineSignalsEnabled` và hàm helper memoize.
- Điểm mở rộng UI: `ArticleItem.kt`, `Content.kt`, `HomeEntry.kt`, `PrivacySettingsPage.kt`, `PrivacySettingsViewModel.kt`.

## Kiểm chứng
- **Unit test suite:** `./gradlew testDevDebugUnitTest --rerun` đạt **1.475 test, 0 lỗi, 0 skip** (tăng 30 test so với trước EXC-04):
  - `HeadlineAnalyzerTest`: 17 test (nhận diện 5 ngôn ngữ, tiêu đề thường BBC không vượt ngưỡng, dấu chấm câu/viết hoa/từ mồi chài tăng điểm, trần 100, ngưỡng > 70, phân loại cảm xúc, ca bi kịch không cảm xúc không bị coi là tiêu cực, đầu vào rỗng/quá 240 ký tự/Unicode).
  - `HeadlineAnalyzerPerformanceTest`: 1 test tự đo; batch 150 tiêu đề chạy warmed median dưới 50 ms.
  - `HeadlineSignalBadgesTest`: 7 test Compose (ngưỡng 70 ẩn / 71 hiện, 3 sắc thái đều có nhãn chữ, ẩn độc lập, tín hiệu null không hiện).
  - `HeadlineSignalsStateTest`: 3 test (toggle OFF trả null không phân tích, ngôn ngữ lạ trả null).
  - `PrivacyPreferencesTest` (5 test) & `PrivacySettingsViewModelTest` (3 test): toggle mặc định ON, ghi/đọc DataStore hai chiều.
  - `StringResourceParityTest`: 6/6 test pass; 11 khóa chuỗi mới có mặt đồng bộ ở cả 38 thư mục `values*/strings.xml`.
- **Lint:** `./gradlew lintDevDebug --rerun-tasks` đạt **0 lỗi, 2.447 cảnh báo** (không có lỗi hay cảnh báo mới nào ở các file thêm mới).
- **Build:** `assembleDevDebug` và `assembleDevDebugAndroidTest` pass.
- **Android instrumentation test:** `ArticleItemTest` chạy 5/5 pass trực tiếp trên phần cứng Samsung Galaxy A11 (`R9JN61LDLFJ`) qua `am instrument`, xác nhận hiển thị cả 2 badge cho tít giật gân, chỉ hiện tone badge cho tít thường, và ẩn toàn bộ khi toggle OFF.
- **Smoke test thực tế:**
  - Trên **Samsung Galaxy S24 Ultra (`R5CX613VZBR`)**: cài đặt app, nạp feed fixture qua `adb reverse`, mở danh sách bài xác nhận xuất hiện badge `🎯 Giật tít 100%` (màu đỏ) và `⚪ Chưa rõ` (màu xám); mở Reader xác nhận xuất hiện badge `⚪ Chưa rõ` phía trên tiêu đề/metadata và không lặp lại badge giật tít; banner quảng cáo ở đáy không che UI. Khi chuyển màn hình xuất hiện interstitial quảng cáo Google Ads *Flood-It!* che UI, dừng ngay theo R4.
  - Theo chỉ thị owner, chuyển target sang **Samsung Galaxy A11 (`R9JN61LDLFJ`)**: chạy pass toàn bộ 5 test Compose của `ArticleItemTest`. Phần smoke nhập feed fixture bằng bàn phím trên A11 bị lỗi ký tự URL do bàn phím Samsung làm rơi dấu hai chấm/gạch chéo, chưa nạp được feed fixture thứ hai; dữ liệu cài đặt tạm thời đã được dọn sạch bằng `pm clear` và gỡ bỏ test APK; server fixture đã tắt, `adb reverse` đã gỡ.

## Giới hạn đã biết (nói thẳng)
1. **Không có "Giải mã tít":** heuristic chỉ phát hiện và chấm điểm tít giật gân chứ không đọc hiểu để trích xuất câu trả lời cốt lõi như spec gốc mong muốn; đã được owner duyệt loại bỏ.
2. **Tiếng Tây Ban Nha, Pháp, Đức mang tính thử nghiệm:** từ điển cho 3 ngôn ngữ này do máy tổng hợp dựa trên từ chức năng và từ vựng thông dụng, chưa được người bản ngữ kiểm duyệt hay đo lường trên corpus báo chí thực tế; có thể bỏ sót hoặc báo sai ở các tiêu đề phức tạp.
3. **Thang cảm xúc chỉ dựa trên từ khóa bề mặt:** không hiểu được mỉa mai, ngữ cảnh kép hoặc ngữ pháp phủ định (ví dụ: "không có chiến thắng" có thể bị đếm từ "chiến thắng"). Các tiêu đề thời sự trung tính không chứa từ khóa cảm xúc mạnh sẽ hiển thị là `⚪ Chưa rõ`.
4. **Không phân tích thân bài:** chỉ đánh giá câu chữ của tiêu đề; một bài viết giật tít nhưng nội dung chuẩn xác vẫn sẽ bị gắn cờ giật tít, và ngược lại.
5. **33 ngôn ngữ ngoài EN, VI, ES, FR, DE:** đang dùng bản sao tiếng Anh cho các chuỗi giao diện mới; các bài viết bằng ngôn ngữ này sẽ không được gắn huy hiệu (trả về `null`).

## Commits trên nhánh `dev`
- `a184beb4`: `feat(exc-04): add deterministic local headline analyzer`
- `8f8604c0`: `feat(exc-04): add default-on headline signal preference and settings toggle`
- `ffe924b4`: `feat(exc-04): display clickbait and tone badges in list and reader`
