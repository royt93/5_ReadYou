# [EXC-01] "AI Daily Smart Digest" — Bản Tin Sáng Tổng Hợp Đa Nguồn Bằng AI

- **Type:** Exclusive Killer Feature / AI Innovation
- **Priority:** `P1 (High)`
- **Estimation:** `8 Story Points`
- **Epic:** "05. Tính Năng Độc Quyền & Killer Features (Market Differentiators)"
- **Location:** `app/src/main/java/com/mckimquyen/reader/ui/page/digest/` (Gói mới), [`infrastructure/ai/GeminiSummaryService.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/ai/GeminiSummaryService.kt)

## Vấn đề thực tế
Các app hiện nay chỉ tóm tắt từng bài riêng lẻ. Người dùng thức dậy với 150 bài báo mới và không có thời gian đọc từng bài. "AI Daily Smart Digest" gom 20 bài hot nhất trong 24 giờ qua từ tất cả các chuyên mục, phân loại chủ đề (Kinh tế, Công nghệ, Thế giới, Đời sống) và xuất bản một bản tin thời sự cô đọng duy nhất dài 2 phút, có trích dẫn link nguồn từng bài. `ui/page/digest/` chưa tồn tại trong repo — đây là tính năng hoàn toàn mới, cần tận dụng `GeminiSummaryService.kt` đã có sẵn hạ tầng gọi Gemini.

## User Story
> Là người bận rộn thức dậy lúc 7:00 sáng,
> Tôi muốn mở app và chỉ cần ấn "Tạo Bản Tin Sáng 2 Phút",
> Để AI tổng hợp toàn bộ diễn biến tin tức quan trọng nhất trên thế giới vào một bài tổng quan mạch lạc duy nhất.

## Acceptance Criteria (Gherkin)
- **Given** người dùng có nhiều feed tin tức với hơn 30 bài mới chưa đọc
- **When** bấm vào nút "✨ Tạo Bản Tin Sáng" trên TopBar của `FlowPage`
- **Then** AI Gemini phân tích tiêu đề và tóm tắt của 20 bài nổi bật nhất
- **And** tạo ra một bài báo tổng hợp với cấu trúc chuyên nghiệp:
  - 💡 **Tiêu Điểm Hôm Nay** (3 tin chấn động nhất)
  - 📈 **Kinh Doanh & Công Nghệ**
  - 🌍 **Thế Giới 24 Giờ Qua**
- **And** mỗi sự kiện đều có tag clickable link trỏ về bài báo gốc trong app
- **And** bản tin được tự động lưu vào tab riêng để đọc lại bất cứ lúc nào.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [EXC-01] "AI Daily Smart Digest — Bản Tin Sáng Tổng Hợp Đa Nguồn Bằng AI" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/EXC-01_ai-daily-smart-digest.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [EXC-01] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [EXC-01] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
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
