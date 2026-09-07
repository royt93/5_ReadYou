# [ECHO-01] La Bàn Thiên Kiến & Đồng Hồ Cảm Xúc (Bias Radar & Sentiment Gauge)

- **Type:** New Feature / AI Innovation
- **Priority:** `P1 (High)`
- **Estimation:** `5 Story Points`
- **Epic:** "16. EchoChamber Destroyer & Bias Radar (Phá Vỡ Buồng Vang & Đấu Trường Phản Biện)"
- **Location:** `app/src/main/java/com/mckimquyen/reader/infrastructure/ai/` (module phân tích mới, ví dụ `BiasRadarAnalyzer.kt`), [`infrastructure/ai/GeminiSummaryService.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/ai/GeminiSummaryService.kt), [`infrastructure/ai/GeminiConfig.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/ai/GeminiConfig.kt), [`ui/page/home/read/Metadata.kt`](../../../app/src/main/java/com/mckimquyen/reader/ui/page/home/read/Metadata.kt), `ui/page/home/read/` (nơi hiển thị đồng hồ đo dưới tiêu đề bài viết)

## Vấn đề thực tế
Người đọc RSS hiện tại tiếp nhận nội dung mà không có bất kỳ tín hiệu nào cảnh báo về mức độ thiên kiến, cảm tính hay thổi phồng PR của bài viết — dẫn tới rủi ro rơi vào buồng vang thông tin (echo chamber) mà không hay biết. Trang đọc bài (`ReadingPage.kt` / `Content.kt` trong `ui/page/home/read/`) hiện chỉ hiển thị nội dung gốc và (nếu có) tóm tắt AI qua `SummarySheet.kt`, chưa có bất kỳ chỉ số đo lường khách quan/thiên kiến nào. Cần một module phân tích mới dùng hạ tầng Gemini sẵn có (`GeminiSummaryService.kt`, `GeminiConfig.kt`) để chấm điểm bài viết trên 3 trục và hiển thị trực quan ngay dưới tiêu đề.

## User Story
> Là độc giả muốn đọc tin tức tỉnh táo,
> Tôi muốn nhìn thấy ngay một đồng hồ đo nhỏ gọn cho biết bài viết đang thiên về cảm tính, lạc quan/bi quan hay bị thổi phồng PR đến mức nào,
> Để tôi chủ động cảnh giác và tự đánh giá độ tin cậy của nội dung trước khi tin theo.

## Acceptance Criteria (Gherkin)
- **Given** bài viết có ngôn từ mang tính quảng bá PR quá mức
- **When** người dùng mở bài đọc
- **Then** thanh đo Hype Score hiển thị mức 85% kèm nhãn cảnh báo nhẹ màu cam: "Bài viết chứa nhiều yếu tố quảng cáo/thổi phồng"
- **And** người dùng bấm vào xem chi tiết các đoạn văn bản bị đánh giá thiên kiến

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [ECHO-01] "La Bàn Thiên Kiến & Đồng Hồ Cảm Xúc (Bias Radar & Sentiment Gauge)" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/ECHO-01_bias-radar-sentiment-gauge.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [ECHO-01] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [ECHO-01] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
4. Build kiểm tra: `./gradlew assembleDevDebug` (hoặc `lintDevDebug` nếu chỉ đổi resource/string).
5. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm, nếu có test giả/mock rỗng/logic chưa đúng thì điểm phải phản ánh đúng thực tế.
2. Bổ sung **unit test** cho mọi nhánh logic mới (`app/src/test/...`), phủ cả edge case (rỗng, lỗi mạng, dữ liệu null, giới hạn biên).
3. Bổ sung **widget/Compose UI test** cho mọi component UI mới hoặc thay đổi hành vi UI (`app/src/androidTest/...`).
4. Bổ sung **integration test** cho luồng end-to-end liên quan (DB + repository + worker nếu có liên quan).
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, thao tác thủ công đúng luồng vừa sửa, ghi lại bằng chứng cụ thể (log logcat hoặc mô tả kết quả quan sát được) chứng minh hoạt động đúng — không suy đoán, không báo cáo khống.
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` hoặc `inprogress/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.
```
