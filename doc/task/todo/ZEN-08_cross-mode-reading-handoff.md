# [ZEN-08] Cross-mode Reading Handoff

- **Type:** New Feature / Idea
- **Priority:** `P2 (Medium)`
- **Estimation:** `5 Story Points`
- **Epic:** [08. ZEN — Đọc Siêu Tốc RSVP & Tập Trung Tuyệt Đối](08_ZEN_FOCUS_AND_SPEED_READING.md)
- **Location:** [`ui/page/home/read/ReadingPage.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingPage.kt), [`ui/page/home/read/ReadingViewModel.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModel.kt), [`ui/page/rsvp/RsvpViewModel.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/page/rsvp/RsvpViewModel.kt), [`infrastructure/audio/TtsManager.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/audio/TtsManager.kt)

## Vấn đề thực tế
Hiện tại app có 3 chế độ đọc độc lập, không chia sẻ trạng thái vị trí đọc với nhau: đọc thường trong `ReadingPage`, RSVP tốc độ cao (`RsvpReaderDialog`/`RsvpViewModel`, xem ZEN-01), và nghe TTS qua `TtsManager`. Nếu người dùng đang đọc thường giữa bài, chuyển sang RSVP để đọc nhanh phần còn lại, hoặc chuyển sang nghe TTS lúc lái xe, vị trí đọc dở không được đồng bộ — người dùng phải tự nhớ và tua lại thủ công. Không có bất kỳ cơ chế lưu "vị trí đọc theo % hoặc theo token/word index của từng bài" dùng chung giữa 3 chế độ.

## User Story
> Là người đọc một bài dài trên nhiều bối cảnh khác nhau trong ngày (đọc thường lúc rảnh, RSVP lúc vội, nghe TTS lúc lái xe),
> Tôi muốn app tự nhớ chính xác tôi đã đọc/nghe tới đâu trong bài, bất kể tôi dùng chế độ nào lần trước,
> Để khi chuyển chế độ đọc, tôi tiếp tục đúng ngay tại vị trí đã dừng, không bị mất chỗ hay phải đọc lại từ đầu.

## Acceptance Criteria (Gherkin)
- **Given** người dùng đang đọc một bài ở chế độ thường (`ReadingPage`) và cuộn tới khoảng 40% nội dung
- **When** người dùng chuyển sang chế độ RSVP cho cùng bài đó (qua nút "⚡ Đọc Siêu Tốc")
- **Then** `RsvpViewModel` phải khởi động phiên RSVP bắt đầu từ token tương ứng gần nhất với vị trí ~40% đã đọc (ánh xạ tỉ lệ cuộn/scroll-offset sang token index của `RsvpTokenizer.tokenize()`), không bắt đầu lại từ đầu bài
- **And** tương tự khi chuyển từ RSVP sang đọc thường: `ReadingPage` phải cuộn tới đúng vị trí gần nhất tương ứng với token RSVP đang dừng
- **And** khi chuyển sang nghe TTS (`TtsManager`), việc phát audio phải bắt đầu từ đoạn văn bản gần nhất tương ứng với vị trí đã đọc/RSVP dở (nếu `TtsManager` hỗ trợ start-offset theo văn bản; nếu công nghệ TTS hiện tại chỉ hỗ trợ phát từ đầu, ghi rõ giới hạn kỹ thuật và làm best-effort ở mức đoạn văn gần nhất)
- **And** vị trí đọc dở được lưu bền vững theo từng `articleId` (Room, mở rộng bảng liên quan tới `ArticleDao`/entity `article`, hoặc bảng progress mới), tồn tại qua việc thoát app và mở lại
- **And** khi bài viết đã đọc xong 100% ở bất kỳ chế độ nào, vị trí lưu phải phản ánh đúng trạng thái "đã đọc hết", không tính là còn dở.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [ZEN-08] "Cross-mode Reading Handoff" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/ZEN-08_cross-mode-reading-handoff.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location (`ReadingPage.kt`, `ReadingViewModel.kt`, `RsvpViewModel.kt`, `RsvpTokenizer.kt`, `TtsManager.kt`, và schema Room hiện có cho `article`/`ArticleDao`), xác nhận hiện chưa có cơ chế lưu/đồng bộ vị trí đọc dùng chung giữa 3 chế độ (không giả định).
2. Implement feature đúng theo Acceptance Criteria:
   - Thiết kế mô hình lưu trữ vị trí đọc chung (ví dụ % offset hoặc word/token index quy đổi được giữa 3 chế độ) gắn với `articleId`, ưu tiên mở rộng Room entity hiện có hoặc thêm bảng mới qua migration đúng chuẩn Room (nhớ export schema `app/schemas`).
   - Cập nhật `ReadingViewModel` để ghi vị trí đọc khi cuộn/rời trang.
   - Cập nhật `RsvpViewModel`/`RsvpTokenizer` để hỗ trợ khởi động từ token index cho trước và ghi lại vị trí khi dừng/thoát.
   - Cập nhật `TtsManager` (hoặc lớp gọi nó) để best-effort bắt đầu phát gần đúng vị trí đã lưu; nếu giới hạn kỹ thuật không cho phép start-offset chính xác, ghi rõ trong code comment và Completion Report sau này.
   - Đảm bảo mọi truy vấn/ghi DB chạy trên `Dispatchers.IO`.
3. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới (ví dụ thông báo "tiếp tục từ vị trí đã lưu").
4. Với thay đổi kiến trúc/thiết kế quan trọng (bắt buộc cho task này vì có migration Room + thay đổi luồng 3 ViewModel khác nhau), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [ZEN-08] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [ZEN-08] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
5. Build kiểm tra: `./gradlew assembleDevDebug` (hoặc `lintDevDebug` nếu chỉ đổi resource/string).
6. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.
```

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm, nếu có test giả/mock rỗng/logic chưa đúng thì điểm phải phản ánh đúng thực tế.
2. Bổ sung **unit test** cho mọi nhánh logic mới (`app/src/test/...`), phủ cả edge case (rỗng, lỗi mạng, dữ liệu null, giới hạn biên).
3. Bổ sung **widget/Compose UI test** cho mọi component UI mới hoặc thay đổi hành vi UI (`app/src/androidTest/...`).
4. Bổ sung **integration test** cho luồng end-to-end liên quan (DB + repository + worker nếu có liên quan) — đặc biệt test round-trip lưu/đọc vị trí qua Room migration.
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, thao tác thủ công đúng luồng vừa sửa, ghi lại bằng chứng cụ thể (log logcat hoặc mô tả kết quả quan sát được) chứng minh hoạt động đúng — không suy đoán, không báo cáo khống.
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` hoặc `inprogress/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.
