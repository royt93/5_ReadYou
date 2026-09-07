# 📄 Template chuẩn cho 1 file task (doc/task/todo/*.md)

> File này là khuôn mẫu bắt buộc cho MỌI task file mới trong `doc/task/todo/`. Mỗi task = 1 file riêng, đặt tên `<PREFIX-NN>_<slug-ngan-gon-tieng-anh>.md` (ví dụ `FIX-09_gemini-key-hardcoded.md`), giữ nguyên prefix đã dùng trong epic gốc (FIX, ENH, NEW, IDEA, EXC, KNOW, ING, ZEN, REEL, FRONT, AUTO, RPG, ORACLE, DJ, BOUNTY, ECHO...).

---

```markdown
# [<PREFIX-NN>] <Tiêu đề ngắn gọn>

- **Type:** <Bug / Performance / Architecture / New Feature / Idea / Killer Feature>
- **Priority:** `P0 (Blocker)` | `P1 (High)` | `P2 (Medium)` | `P3 (Low)`
- **Estimation:** `X Story Points`
- **Epic:** <tên epic gốc, ví dụ "01. FIX — Sửa Lỗi, Ổn Định & Ad Unit">
- **Location:** [`path/File.kt`](path/File.kt#Lxx) ...

## Vấn đề thực tế
<Mô tả cụ thể, dẫn chứng file:line, KHÔNG chung chung.>

## User Story
> Là <vai trò>,
> Tôi muốn <mục tiêu>,
> Để <giá trị>.

## Acceptance Criteria (Gherkin)
- **Given** ...
- **When** ...
- **Then** ...
- **And** ...

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

​```
Bạn đang thực hiện task [<PREFIX-NN>] "<tiêu đề>" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/<tên-file-này> trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [<PREFIX-NN>] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [<PREFIX-NN>] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
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
​```

```

---

## Quy tắc bổ sung khi audit/split epic cũ

1. Nếu task cũ trong `todo/0N_*.md` đã có sẵn "Vấn đề thực tế / User Story / Acceptance Criteria" — GIỮ NGUYÊN nội dung đó, chỉ bổ sung 2 mục `🔁 Loop Prompt` và `🏁 Tín hiệu kết thúc loop` theo mẫu trên, không viết lại từ đầu.
2. Nếu qua audit phát hiện task cũ **thực ra đã DONE trong code** (có implementation + test tương ứng): tạo file trong `doc/task/done/<PREFIX-NN>_<slug>_DONE.md` thay vì `todo/`, thêm phần "Completion Report" (điểm tự chấm dựa trên chất lượng thực tế quan sát được, commit liên quan nếu tìm được qua `git log`, danh sách test đã có, gap còn sót nếu có).
3. Sau khi tách hết task trong 1 file epic `todo/0N_*.md` thành các file riêng, THAY nội dung file epic gốc bằng 1 "Epic Index" ngắn: mục tiêu epic + bảng liệt kê tên/trạng thái/link các file task con (không xoá file epic gốc, giữ làm mục lục).
4. KHÔNG tự ý đổi số liệu ở `doc/task/README.md` — sẽ được cập nhật tập trung sau.
