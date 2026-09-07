# [RPG-05] Quiz "AI" Thực Chất Là Giả — Distractor Tĩnh & Đáp Án Đúng Đoán Được Qua Độ Dài

- **Type:** Bug / Product Integrity
- **Priority:** `P1 (High)`
- **Estimation:** `5 Story Points`
- **Epic:** [12. BRAIN RPG — Game Hóa Đọc Bài, Trắc Nghiệm AI & Wrapped](12_GAMIFIED_BRAIN_RPG_AND_WRAPPED.md)
- **Location:** [`domain/sv/QuizGeneratorService.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/sv/QuizGeneratorService.kt#L28-L135), [`domain/model/rpg/QuizQuestion.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/model/rpg/QuizQuestion.kt), [`app/src/test/java/com/mckimquyen/reader/domain/sv/QuizGeneratorServiceTest.kt`](../../../app/src/test/java/com/mckimquyen/reader/domain/sv/QuizGeneratorServiceTest.kt)

## Vấn đề thực tế
`QuizGeneratorService.generateQuiz()` (dòng 28-135) KHÔNG dùng AI/LLM để sinh câu hỏi như tuyên bố trong tính năng "AI Micro-Quiz Engine". Thực tế:
- Chỉ chọn ngẫu nhiên 1 trong 3 mẫu câu hỏi template cố định (`qType = rng.nextInt(3)`).
- Các đáp án sai (`distractors`, dòng 51-55, 65-69, 75-79, 89-93, 103-107, 113-117) là **chuỗi tĩnh hardcode**, hoàn toàn không liên quan nội dung bài viết, và **lặp lại y hệt ở mọi câu hỏi cùng loại** bất kể bài đọc là gì (ví dụ luôn có "Kế hoạch sáp nhập tài chính toàn cầu năm 2030" dù bài viết nói về sức khỏe hay AI).
- Đáp án đúng (`correctOption`) thường là `cleanTitle.take(65)...` hoặc `keySentence.take(65)...` — trong phần lớn trường hợp bài viết có tiêu đề/câu trích dài hơn 57 ký tự (độ dài các distractor tĩnh), khiến đáp án đúng gần như luôn là lựa chọn dài nhất trong danh sách 4 đáp án — người dùng có thể đoán đúng chỉ bằng cách chọn câu dài nhất, không cần đọc bài.

Điều này mâu thuẫn trực tiếp với tuyên bố "AI Micro-Quiz Engine" trong Epic 12 / Completion Report DONE (9.9/10) — tính năng cốt lõi tạo giá trị giáo dục thực chất chỉ là giả lập.

## User Story
> Là người dùng làm bài quiz cuối bài để kiểm tra mức độ hiểu bài,
> Tôi muốn các đáp án sai (distractor) thực sự liên quan đến chủ đề bài viết và không thể đoán ra đáp án đúng chỉ bằng độ dài câu chữ,
> Để bài quiz có giá trị kiểm tra hiểu biết thật sự, không phải trò chơi đoán mò.

## Acceptance Criteria (Gherkin)
- **Given** một bài viết bất kỳ đã được phân loại chủ đề (category)
- **When** hệ thống sinh quiz cho bài viết đó
- **Then** cả 3 distractor phải được rút ra/biến đổi từ ngữ cảnh thực của bài viết hoặc từ các bài khác cùng chủ đề (category) đã có trong kho dữ liệu, KHÔNG dùng chuỗi tĩnh cố định giống nhau cho mọi bài viết khác chủ đề nhau
- **And** với 2 bài viết khác nhau thuộc cùng category, danh sách distractor sinh ra phải khác nhau (không trùng lặp y hệt)
- **And** đáp án đúng KHÔNG được luôn là lựa chọn có độ dài ký tự lớn nhất trong 4 đáp án — viết unit test thống kê trên tối thiểu 20 mẫu bài viết khác nhau, tỷ lệ "đáp án đúng là câu dài nhất" phải < 40% (gần với xác suất ngẫu nhiên 25% của 4 lựa chọn)
- **And** `QuizGeneratorServiceTest` phải có test khẳng định 2 lần gọi `generateQuiz` với nội dung bài khác nhau (cùng category) trả về `distractors` khác nhau

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [RPG-05] "Quiz AI Thực Chất Là Giả — Distractor Tĩnh & Đáp Án Đúng Đoán Được Qua Độ Dài" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/RPG-05_quiz-distractors-context-aware.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định) — kiểm tra lại QuizGeneratorService.kt xem distractor đã được tham số hóa theo nội dung/category chưa, và đáp án đúng có còn thiên lệch về độ dài không.
2. Implement fix đúng theo Acceptance Criteria. Cân nhắc 2 hướng khả thi (không bắt buộc chọn 1 trong 2, có thể kết hợp):
   a. Rút distractor từ chính nội dung bài viết: lấy các câu/cụm từ khác trong bài (không phải câu đúng), biến đổi nhẹ (đảo ngữ, phủ định, thay số liệu) để tạo lựa chọn sai nhưng liên quan ngữ cảnh.
   b. Rút distractor từ các bài viết khác đã lưu trong Room DB cùng category (qua ArticleDao/query theo category đã detect) — dùng tiêu đề/câu trích của bài khác làm distractor hợp lý hơn chuỗi tĩnh vô nghĩa.
   Đồng thời chuẩn hóa độ dài các lựa chọn (cắt về cùng khoảng ký tự, hoặc random hóa độ dài đáp án đúng/sai) để không còn tín hiệu "đáp án dài nhất luôn đúng". KHÔNG cần tích hợp LLM/API AI thật nếu không có ngân sách — nhưng phải xoá bỏ hoàn toàn distractor tĩnh cố định và đảm bảo AC về tỷ lệ đoán-qua-độ-dài.
3. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng (query Room) chạy Dispatchers.IO/Default (không block Main), localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới hoặc string mẫu mới.
4. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task RPG-05 trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task RPG-05 trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
5. Build kiểm tra: `./gradlew assembleDevDebug`.
6. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.
```

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm, nếu distractor vẫn còn tĩnh một phần hoặc test thống kê chỉ chạy trên < 20 mẫu thì điểm phải phản ánh đúng thực tế.
2. Bổ sung **unit test** cho mọi nhánh logic mới (`app/src/test/...`) — bắt buộc gồm: test distractor khác nhau giữa 2 bài cùng category, test thống kê tỷ lệ "đáp án đúng là câu dài nhất" trên ≥20 mẫu, test edge case bài viết rỗng/quá ngắn/không có category rõ ràng.
3. Bổ sung **widget/Compose UI test** cho `BrainQuizCard` nếu hành vi hiển thị đáp án thay đổi (`app/src/androidTest/...`).
4. Bổ sung **integration test** cho luồng end-to-end nếu distractor được rút từ Room DB (query category + sinh quiz).
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, đọc thử ít nhất 3 bài viết thuộc category khác nhau, xác nhận bằng logcat/quan sát rằng distractor sinh ra liên quan ngữ cảnh và khác nhau giữa các bài — không suy đoán, không báo cáo khống.
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` hoặc `inprogress/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.
