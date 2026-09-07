# [FIX-13] Dead Code: Hàm `callGemini()` Không Còn Ai Gọi Trong `GeminiSummaryService`

- **Type:** Bug / Code Quality
- **Priority:** `P2 (Medium)`
- **Estimation:** `1 Story Point`
- **Epic:** 01. FIX — Sửa Lỗi, Ổn Định & Ad Unit
- **Location:** [`infrastructure/ai/GeminiSummaryService.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/ai/GeminiSummaryService.kt#L244)

## Vấn đề thực tế
`GeminiSummaryService.kt` dòng 244 định nghĩa `private fun callGemini(apiKey: String, requestBody: String): String = callGeminiRaw(apiKey, requestBody)` — một wrapper 1 dòng gọi thẳng `callGeminiRaw()`. Kiểm tra toàn bộ file: cả 3 nơi thực sự gọi API (dòng 80, 130, 181) đều gọi trực tiếp `callGeminiRaw(key, requestBody)`, **không có bất kỳ lời gọi nào tới `callGemini()`**. Đây là dead code còn sót lại từ một lần refactor trước đó (rename `callGemini` → `callGeminiRaw` nhưng quên xoá hàm cũ, hoặc tạo hàm mới rồi quên dọn hàm cũ). Đáng chú ý: các báo cáo audit trước đây trong `doc/task/done/` (ví dụ điểm tự chấm "9.8-9.9/10" của các Loop AI trước) đã không phát hiện ra dead code đơn giản này, cho thấy quy trình tự-audit ở các task trước có thể chưa đủ kỹ (chỉ chạy build pass + test pass, chưa thực sự rà soát code thừa/dead code).

## User Story
> Là nhà phát triển bảo trì codebase,
> Tôi muốn không có hàm chết (dead code) gây nhiễu khi đọc hiểu luồng gọi AI,
> Để giảm rủi ro nhầm lẫn maintain nhầm hàm không dùng và giữ codebase sạch.

## Acceptance Criteria (Gherkin)
- **Given** `GeminiSummaryService.kt` hiện có hàm `callGemini()` (dòng 244) không được gọi ở bất kỳ đâu trong codebase
- **When** rà soát lại (`grep -rn "callGemini\b" app/src/`, loại trừ `callGeminiRaw`)
- **Then** hàm `callGemini()` được xoá khỏi file (hoặc, nếu có lý do giữ lại làm public API cho việc khác — phải nêu rõ lý do trong comment và có ít nhất 1 call-site thực tế; mặc định là xoá)
- **And** build vẫn thành công, không có warning "unused function" liên quan còn sót
- **And** task audit tương lai (ghi trong `doc/task/README.md` hoặc quy trình review) cần bổ sung bước rà soát dead code (ví dụ chạy Android Studio "Inspect Code" hoặc lint unused-code check) thay vì chỉ dựa vào build/test pass.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [FIX-13] "Dead Code: Hàm callGemini() Không Còn Ai Gọi Trong GeminiSummaryService" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/FIX-13_dead-code-callgemini.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định) — chạy `grep -rn "callGemini(" app/src/main/java/com/mckimquyen/reader/` để liệt kê toàn bộ call-site, xác nhận callGemini() (không phải callGeminiRaw) thực sự không có nơi gọi.
2. Implement fix đúng theo Acceptance Criteria — xoá hàm `callGemini()` nếu xác nhận dead code. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui.
3. Bước tham khảo AI khác là TUỲ CHỌN cho task nhỏ này (xoá 1 hàm dead code, rủi ro thấp) — có thể bỏ qua nếu thay đổi rõ ràng an toàn.
4. Build kiểm tra: `./gradlew assembleDevDebug` (hoặc `lintDevDebug`).
5. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.
```

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm.
2. Bổ sung **unit test**: không bắt buộc thêm test mới cho việc xoá dead code, nhưng phải chạy lại toàn bộ test suite hiện có của `GeminiSummaryService` (nếu có trong `app/src/test/...`) để xác nhận không có test nào phụ thuộc vào hàm bị xoá; nếu có, cập nhật/xoá test đó tương ứng.
3. Bổ sung **widget/Compose UI test**: không áp dụng (không có thay đổi UI).
4. Bổ sung **integration test**: không áp dụng trừ khi phát hiện dead code khác liên quan trong lúc rà soát — nếu có, ghi chú lại (không tự ý xoá thêm ngoài phạm vi task, tạo task mới nếu cần).
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, thao tác thủ công bấm Tóm tắt AI/Deep Read/Mind Map xác nhận vẫn hoạt động bình thường sau khi xoá hàm chết (đảm bảo không xoá nhầm hàm đang dùng), ghi lại bằng chứng cụ thể (log logcat hoặc mô tả kết quả quan sát được) — không suy đoán, không báo cáo khống.
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` hoặc `inprogress/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.
