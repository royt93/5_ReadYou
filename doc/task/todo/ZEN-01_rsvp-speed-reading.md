# [ZEN-01] Chế Độ Đọc Chớp Mắt Siêu Tốc RSVP (Rapid Serial Visual Presentation)

- **Type:** Cognitive UX / Speed Reading
- **Priority:** `P2 (Medium)`
- **Estimation:** `5 Story Points`
- **Epic:** [08. ZEN — Đọc Siêu Tốc RSVP & Tập Trung Tuyệt Đối](08_ZEN_FOCUS_AND_SPEED_READING.md)
- **Location:** [`app/src/main/java/com/mckimquyen/reader/ui/page/rsvp/`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/page/rsvp/)

## Vấn đề thực tế
Mắt người khi đọc sách thông thường phải liên tục đảo qua lại giữa các dòng (saccades), làm chậm tốc độ đọc (trung bình 200-250 từ/phút) và nhanh mỏi mắt. Công nghệ RSVP (tương tự Spritz) nhấp nháy từng từ tại một tiêu điểm cố định.

> **Ghi chú audit (2026-09-06):** Task này ĐÃ được implement (xem `RsvpReaderDialog.kt`, `RsvpTokenizer.kt`, `RsvpViewModel.kt`, commit `a129f71`) và ban đầu được tuyên bố DONE trong `doc/task/done/08_ZEN_FOCUS_AND_SPEED_READING_DONE.md`. Tuy nhiên audit lại phát hiện logic "tạm dừng lâu hơn ở cuối đoạn văn" (`isParagraphBreak`) không hoạt động do lỗi thứ tự xử lý trong `cleanHtml()`/`tokenize()` — xem task fix riêng **[ZEN-04]**. File task này được giữ lại ở `todo/` (theo yêu cầu tách epic) để làm nguồn tham chiếu Acceptance Criteria gốc; phần còn thiếu/lỗi được theo dõi tại ZEN-04.

## User Story
> Là người cần đọc lướt nhanh bài báo 3,000 từ trong vòng 3 phút giờ giải lao,
> Tôi muốn mở chế độ đọc RSVP để mắt nhìn vào một điểm duy nhất và đọc với tốc độ 500-800 từ/phút,
> Để tôi tiết kiệm thời gian đọc mà vẫn nắm bắt trọn vẹn thông điệp.

## Acceptance Criteria (Gherkin)
- **Given** người dùng đang ở bài viết trong `ReadingPage`
- **When** bấm nút "⚡ Đọc Siêu Tốc (RSVP)"
- **Then** màn hình hiển thị hộp chữ tiêu điểm cố định làm nổi bật chữ cái tâm (Optimal Recognition Point - ORP) bằng màu đỏ
- **And** các từ lướt qua với tốc độ tùy chỉnh từ 250 đến 900 từ/phút (WPM)
- **And** tự động tạm dừng nhẹ ở các dấu chấm, dấu phẩy để não bộ kịp xử lý thông tin.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [ZEN-01] "Chế Độ Đọc Chớp Mắt Siêu Tốc RSVP (Rapid Serial Visual Presentation)" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/ZEN-01_rsvp-speed-reading.md trước khi bắt đầu.

LƯU Ý: task này đã có implementation (`app/src/main/java/com/mckimquyen/reader/ui/page/rsvp/`). Trước khi code, kiểm tra lại toàn bộ Acceptance Criteria còn thiếu gì so với code hiện tại (đối chiếu cả task [ZEN-04] nếu đã tồn tại — đó là bug con của chính task này) rồi mới bổ sung/sửa.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [ZEN-01] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [ZEN-01] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
4. Build kiểm tra: `./gradlew assembleDevDebug` (hoặc `lintDevDebug` nếu chỉ đổi resource/string).
5. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.
```

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
