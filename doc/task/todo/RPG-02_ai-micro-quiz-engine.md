# [RPG-02] Trắc Nghiệm Hiểu Bài Nhanh Cuối Bài (AI Micro-Quiz Engine)

- **Type:** Feature
- **Priority:** `P1 (High)`
- **Estimation:** `8 Story Points`
- **Epic:** [12. BRAIN RPG — Game Hóa Đọc Bài, Trắc Nghiệm AI & Wrapped](12_GAMIFIED_BRAIN_RPG_AND_WRAPPED.md)
- **Location:** [`domain/model/rpg/QuizQuestion.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/model/rpg/QuizQuestion.kt), [`domain/sv/QuizGeneratorService.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/sv/QuizGeneratorService.kt), [`ui/component/rpg/BrainQuizCard.kt`](../../../app/src/main/java/com/mckimquyen/reader/ui/component/rpg/BrainQuizCard.kt)

> **Trạng thái:** ✅ Đã triển khai (xem [`doc/task/done/12_GAMIFIED_BRAIN_RPG_AND_WRAPPED_DONE.md`](../../done/12_GAMIFIED_BRAIN_RPG_AND_WRAPPED_DONE.md)), nhưng audit lại phát hiện quiz KHÔNG thực sự dùng AI để sinh câu hỏi — xem task fix nối tiếp bắt buộc: [`RPG-05`](RPG-05_quiz-distractors-context-aware.md).

## Vấn đề thực tế / Mô tả
Ở cuối mỗi bài viết, hiển thị một card trắc nghiệm tương tác gồm 1 câu hỏi nhanh 4 đáp án do AI sinh ra dựa trên bài đọc. Trả lời đúng nhận ngay **x3 XP (+150 XP)** và mở huy hiệu "Master Reader". Trả lời sai cho phép xem 1 video Rewarded Ad để làm lại câu hỏi giữ chuỗi streak hoàn hảo.

## User Story
> Là người dùng muốn kiểm tra mức độ hiểu bài của mình,
> Tôi muốn trả lời một câu hỏi trắc nghiệm nhanh ngay sau khi đọc xong bài viết,
> Để tôi vừa được thử thách trí nhớ vừa nhận thêm XP thưởng, tạo động lực đọc kỹ hơn.

## Acceptance Criteria (Gherkin)
```gherkin
Given người dùng đọc đến cuối bài viết
When card "Thử thách 10 giây của AI" hiển thị với câu hỏi và 4 đáp án
And người dùng chọn đáp án đúng
Then hiển thị pháo hoa Confetti Lottie và cộng 150 XP vào cây kỹ năng
When người dùng chọn sai
Then hiển thị nút "Xem Video mở quyền thử lại ngay" kết nối AdmobApplovinWrapper Rewarded Video
```

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [RPG-02] "Trắc Nghiệm Hiểu Bài Nhanh Cuối Bài (AI Micro-Quiz Engine)" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/RPG-02_ai-micro-quiz-engine.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định) — QuizGeneratorService.kt hiện tại sinh câu hỏi bằng template string cố định (KHÔNG gọi AI/LLM nào), distractor là chuỗi tĩnh giống nhau ở mọi bài. Đây là gap chính đã tách thành task RPG-05 — không làm trùng, tham chiếu chéo.
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task RPG-02 trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task RPG-02 trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
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

---
> **Ghi chú audit (2026-09-06):** UI (`BrainQuizCard.kt`) và luồng cộng XP/Rewarded Ad-retry đã hoạt động đúng đặc tả. Tuy nhiên phần "AI sinh câu hỏi" (`QuizGeneratorService.generateQuiz`) thực chất không gọi AI/LLM nào — chỉ chọn 1 trong 3 mẫu câu hỏi cố định và gắn 3 distractor tĩnh giống hệt nhau ở mọi bài viết (xem `QuizGeneratorService.kt` dòng 51-55, 65-69, 75-79, 89-93, 103-107, 113-117), đồng thời đáp án đúng thường dài hơn distractor nên có thể đoán ra mà không cần đọc bài. Đây là sai lệch nghiêm trọng nhất so với tuyên bố "AI Micro-Quiz Engine" — bắt buộc fix ở RPG-05 trước khi coi task này là hoàn thành đúng nghĩa.
