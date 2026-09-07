# [ZEN-07] Adaptive RSVP Trainer

- **Type:** New Feature / Idea
- **Priority:** `P2 (Medium)`
- **Estimation:** `5 Story Points`
- **Epic:** [08. ZEN — Đọc Siêu Tốc RSVP & Tập Trung Tuyệt Đối](08_ZEN_FOCUS_AND_SPEED_READING.md)
- **Location:** [`app/src/main/java/com/mckimquyen/reader/ui/page/rsvp/`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/page/rsvp/) (mở rộng), gói mới `domain/rsvp/` cho logic calibration

## Vấn đề thực tế
Chế độ RSVP hiện tại (ZEN-01) chỉ cho phép người dùng tự chọn WPM cố định (250-900) bằng tay, không có phản hồi khách quan nào cho biết tốc độ đó có phù hợp với khả năng hiểu (comprehension) thực tế của người dùng hay không. Người dùng dễ chọn tốc độ quá cao (đọc được chữ nhưng không hiểu nội dung) hoặc quá thấp (lãng phí tiềm năng tốc độ đọc). Không có cơ chế nào trong `RsvpViewModel.kt` hiện tại đo lường mức độ hiểu bài hay tự động điều chỉnh tốc độ.

## User Story
> Là người mới dùng chế độ đọc RSVP,
> Tôi muốn app tự động đo khả năng đọc-hiểu thực tế của tôi qua một bài calibration ngắn, rồi tự đề xuất/điều chỉnh WPM và độ trễ ở dấu câu phù hợp,
> Để tôi không phải tự đoán tốc độ phù hợp, và luôn đọc ở "vùng tối ưu" giữa tốc độ và mức hiểu bài.

## Acceptance Criteria (Gherkin)
- **Given** người dùng lần đầu bật chế độ RSVP (hoặc chủ động chọn "Hiệu chỉnh tốc độ đọc" trong `ZenSettingsPage`/`RsvpReaderDialog`)
- **When** app hiển thị một đoạn văn ngắn calibration (~150-250 từ, nội dung trung tính không phụ thuộc kiến thức chuyên ngành) ở một WPM khởi điểm (ví dụ 300)
- **Then** sau khi đọc xong, app hiển thị 2-3 câu hỏi trắc nghiệm ngắn kiểm tra mức độ hiểu nội dung vừa đọc
- **And** dựa trên số câu trả lời đúng, thuật toán tự động đề xuất WPM tối ưu cho người dùng đó (tăng WPM nếu hiểu 100%, giữ nguyên hoặc giảm nếu hiểu dưới ngưỡng, ví dụ < 70%)
- **And** thuật toán cũng điều chỉnh độ trễ bổ sung tại dấu câu/cuối đoạn (`calculateExtraDelayMs` trong `RsvpTokenizer`) tương ứng với mức hiểu đo được — hiểu thấp thì tăng delay ở dấu câu để có thêm thời gian xử lý
- **And** kết quả calibration (WPM đề xuất, độ trễ đề xuất) được lưu lại (DataStore, theo mẫu các `*Pref.kt` hiện có) và áp dụng làm mặc định cho các lần đọc RSVP tiếp theo, người dùng vẫn có thể ghi đè thủ công
- **And** người dùng có thể chạy lại calibration bất cứ lúc nào để cập nhật lại theo tiến bộ đọc của họ.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [ZEN-07] "Adaptive RSVP Trainer" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/ZEN-07_adaptive-rsvp-trainer.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location (`ui/page/rsvp/RsvpViewModel.kt`, `RsvpTokenizer.kt`, `RsvpReaderDialog.kt`, và các `*Pref.kt`/`Settings.kt` mẫu trong `infrastructure/pref/`), xác nhận hiện chưa có bất kỳ cơ chế calibration/comprehension-check nào (không giả định).
2. Implement feature đúng theo Acceptance Criteria:
   - Thiết kế nội dung calibration + câu hỏi (có thể là bộ đoạn văn + câu hỏi tĩnh đóng gói sẵn trong resource, không cần gọi AI/network).
   - Thêm state/luồng calibration vào `RsvpViewModel` hoặc ViewModel riêng mới trong `domain/rsvp/` + UI tương ứng.
   - Thêm `RsvpCalibrationPref.kt` (theo đúng mẫu `*Pref.kt` hiện có) lưu WPM đề xuất + delay đề xuất, đăng ký vào `Settings.kt` + `SettingsProvider`.
   - Áp dụng giá trị calibration làm mặc định khi mở RSVP lần sau, cho phép override thủ công.
3. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) cho toàn bộ text UI mới (câu hỏi calibration, hướng dẫn, kết quả).
4. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [ZEN-07] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [ZEN-07] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
5. Build kiểm tra: `./gradlew assembleDevDebug` (hoặc `lintDevDebug` nếu chỉ đổi resource/string).
6. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.
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
