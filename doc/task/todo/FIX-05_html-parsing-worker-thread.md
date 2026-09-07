# [FIX-05] Đẩy tác vụ parse HTML của AI Summary và TTS sang Worker Thread

- **Type:** Performance / ANR Prevention
- **Priority:** `P1 (High)`
- **Estimation:** `1 Story Point`
- **Epic:** 01. FIX — Sửa Lỗi, Ổn Định & Ad Unit
- **Location:** [`ui/page/home/read/ReadingViewModel.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModel.kt#L137), [`ReadingViewModel.kt#L173`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModel.kt#L173)

## Vấn đề thực tế
Trước khi coroutine được launch, `HtmlCompat.fromHtml(...)` được gọi đồng bộ ngay trên Main thread. Với bài báo dài nhiều HTML tags, việc parse trên Main thread làm đơ giao diện (jank / micro-freeze).

## User Story
> Là người dùng bấm nút ✨ Tóm tắt AI hoặc 🎧 Nghe bài báo,
> Tôi muốn ứng dụng phản hồi ngay lập tức không bị đơ giật giao diện.

## Acceptance Criteria
- **Given** người dùng mở một bài báo dài 10,000 từ
- **When** người dùng ấn Tóm tắt hoặc Nghe đọc
- **Then** việc làm sạch HTML thành plain-text chạy hoàn toàn trên `Dispatchers.Default`
- **And** Main thread giữ tốc độ render 60/120 FPS ổn định.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [FIX-05] "Đẩy tác vụ parse HTML của AI Summary và TTS sang Worker Thread" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/FIX-05_html-parsing-worker-thread.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định) — hiện ReadingViewModel.kt có 6 lời gọi `HtmlCompat.fromHtml` (dòng 144-145, 180-181, 231-232, 272-273, 347-348 tại thời điểm audit gần nhất); kiểm tra từng lời gọi có nằm trong block `withContext(Dispatchers.Default)`/`launch(Dispatchers.Default)` hay đang chạy ngay trên Main trước khi launch coroutine.
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task FIX-05 trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task FIX-05 trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
4. Build kiểm tra: `./gradlew assembleDevDebug` (hoặc `lintDevDebug` nếu chỉ đổi resource/string).
5. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.
```

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm, nếu có test giả/mock rỗng/logic chưa đúng thì điểm phải phản ánh đúng thực tế.
2. Bổ sung **unit test** cho mọi nhánh logic mới (`app/src/test/...`), phủ cả edge case (rỗng, lỗi mạng, dữ liệu null, giới hạn biên) — ví dụ test hàm parse chạy đúng trên `Dispatchers.Default` bằng `TestDispatcher`.
3. Bổ sung **widget/Compose UI test** cho mọi component UI mới hoặc thay đổi hành vi UI (`app/src/androidTest/...`).
4. Bổ sung **integration test** cho luồng end-to-end liên quan (DB + repository + worker nếu có liên quan).
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, thao tác thủ công đúng luồng vừa sửa (mở bài dài, bấm Tóm tắt/Nghe), ghi lại bằng chứng cụ thể (log logcat hoặc mô tả kết quả quan sát được, ví dụ Profiler không thấy jank trên Main) chứng minh hoạt động đúng — không suy đoán, không báo cáo khống.
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` hoặc `inprogress/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.

---
> **Ghi chú audit (2026-09-06):** Task này **CHƯA được implement** — `doc/task/inprogress/SPRINT_01_POWERHOUSE.md` vẫn liệt kê FIX-05 ở trạng thái `⏳ Ready` (chưa làm), và code hiện tại tại `ReadingViewModel.kt` vẫn còn nhiều lời gọi `HtmlCompat.fromHtml` — cần audit kỹ từng vị trí xem đã nằm trong coroutine `Dispatchers.Default` hay chưa trước khi implement.
