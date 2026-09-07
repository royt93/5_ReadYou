# [ORACLE-01] Bộ Trích Xuất Kèo Dự Báo Từ Tin Tức (AI Predictive Claim Extractor)

- **Type:** New Feature
- **Priority:** `P1 (High)`
- **Estimation:** `5 Story Points`
- **Epic:** [13. ORACLE — The Oracle Feed (Thị Trường Dự Đoán Tin Tức)](13_THE_ORACLE_PREDICTION_MARKET.md)
- **Location:** [`app/src/main/java/com/mckimquyen/reader/infrastructure/ai/ArticleHighlightsExtractor.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/ai/ArticleHighlightsExtractor.kt), [`app/src/main/java/com/mckimquyen/reader/infrastructure/ai/GeminiSummaryService.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/ai/GeminiSummaryService.kt), [`app/src/main/java/com/mckimquyen/reader/domain/model/article`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/domain/model/article)

## Vấn đề thực tế
Khi RSS bài viết được nạp vào, prompt AI chạy ngầm phân tích xem bài viết có chứa các mốc sự kiện/dự báo tương lai có thể kiểm chứng được không (ví dụ: *"OpenAI dự kiến ra mắt GPT-5 vào tháng 11"*, *"Tesla cam kết bàn giao Cybercab vào năm 2026"*). Nếu có, sinh ra 1 card dự đoán chuẩn: Câu hỏi, Ngày hết hạn, và Tiêu chí giải quyết (Resolution Criteria). Repo đã có tiền lệ chạy AI phân tích ngầm trên bài viết theo mô hình extractor (`ArticleHighlightsExtractor.kt`, `ArticleMindMapExtractor.kt`, `GeminiSummaryService.kt` trong `infrastructure/ai/`) — task này cần một extractor tương tự chuyên biệt cho các "predictive claim".

## User Story
> Là người đọc tin tức công nghệ/crypto/tài chính,
> Tôi muốn AI tự động phát hiện các khẳng định dự báo tương lai trong bài viết,
> Để tôi biết bài nào có thể tham gia đặt cược dự đoán mà không phải tự đọc kỹ từng bài.

## Acceptance Criteria (Gherkin)
- **Given** bài viết công nghệ chứa thông tin về sự kiện ra mắt sản phẩm sắp tới
- **When** AI phân tích nội dung hoàn tất
- **Then** tạo ra một bản ghi trong bảng `oracle_market` với câu hỏi nhị phân (Yes/No) và ngày đáo hạn
- **And** hiển thị huy hiệu "Kèo Dự Đoán" phát sáng lấp lánh trên tiêu đề bài viết

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [ORACLE-01] "Bộ Trích Xuất Kèo Dự Báo Từ Tin Tức (AI Predictive Claim Extractor)" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/ORACLE-01_ai-predictive-claim-extractor.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [ORACLE-01] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [ORACLE-01] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
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
