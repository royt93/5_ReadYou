# [IDEA-08] Assistant / App Actions Hands-Free ("Ok Google, đọc tin RSS Cat Hub")

- **Type:** Voice / Ecosystem Integration
- **Priority:** `P3 (Low)`
- **Estimation:** `3 Story Points`
- **Epic:** 04. IDEAS — Ý Tưởng Trải Nghiệm & Tăng Trưởng (Ideas & Engagement)
- **Location:** `app/src/main/java/com/mckimquyen/reader/infrastructure/audio/TtsManager.kt`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/` (shortcuts.xml mới)

## Vấn đề thực tế
App đã có sẵn `infrastructure/audio/TtsManager.kt` để đọc bài viết bằng giọng nói (text-to-speech), nhưng chức năng này chỉ có thể kích hoạt thủ công từ trong app. Không có cách nào để người dùng ra lệnh thoại (Google Assistant / "Ok Google") để nghe tin mới nhất mà không cần mở app trước, trong khi hạ tầng TTS cần thiết đã tồn tại và chỉ thiếu lớp tích hợp Android App Actions.

## User Story
> Là người dùng bận rộn (đang lái xe, nấu ăn, tập thể dục),
> Tôi muốn ra lệnh "Ok Google, đọc tin RSS Cat Hub" để nghe ngay bài viết mới nhất chưa đọc,
> Để tôi cập nhật tin tức rảnh tay mà không cần cầm điện thoại mở app.

## Acceptance Criteria (Gherkin)
- **Given** người dùng đã cài đặt và đăng nhập ít nhất 1 nguồn RSS trong app, có bài viết chưa đọc (`isRead = false`)
- **When** người dùng nói "Ok Google, đọc tin RSS Cat Hub" (hoặc câu lệnh tương đương khai báo qua `shortcuts.xml` / App Actions built-in intent `actions.intent.GET_THING` hoặc custom capability)
- **Then** hệ thống kích hoạt app ở chế độ nền/foreground tối thiểu và phát TTS bài viết chưa đọc mới nhất thông qua `TtsManager` đã có
- **And** sau khi đọc xong 1 bài, tự động đánh dấu đã đọc và có thể tiếp tục đọc bài kế tiếp nếu người dùng xác nhận ("đọc tiếp")
- **And** khai báo App Action đúng chuẩn Google (shortcuts.xml + `actions.xml` hoặc `AppActionsTestTool` xác thực được), không yêu cầu quyền không cần thiết
- **And** nếu không có bài viết nào chưa đọc, phản hồi thoại/hiển thị thông báo rõ ràng ("Không có tin mới") thay vì crash hoặc im lặng.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [IDEA-08] "Assistant / App Actions Hands-Free ("Ok Google, đọc tin RSS Cat Hub")" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/IDEA-08_assistant-app-actions.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [IDEA-08] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [IDEA-08] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
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
```
