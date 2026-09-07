# [ECHO-03] Thẻ So Sánh Đối Kháng Viral (Dual-Perspective Quote Card 9:16)

- **Type:** New Feature / Growth
- **Priority:** `P2 (Medium)`
- **Estimation:** `5 Story Points`
- **Epic:** "16. EchoChamber Destroyer & Bias Radar (Phá Vỡ Buồng Vang & Đấu Trường Phản Biện)"
- **Location:** `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/` (nút "Xuất Thẻ Tranh Biện" cạnh khung Steelman), module chia sẻ ảnh mới tham khảo cấu trúc tính năng quote-card hiện có ở [`IDEA-03_quote-card-social-share.md`](IDEA-03_quote-card-social-share.md), phụ thuộc trực tiếp vào dữ liệu do ECHO-02 (Steelman Rebuttal) sinh ra

## Vấn đề thực tế
App chưa có cơ chế xuất ảnh chia sẻ dạng "đối kháng" 9:16 kết hợp trích dẫn gốc và luận điểm phản biện AI — một định dạng tối ưu cho việc lan truyền trên mạng xã hội (Threads, X, Facebook) và tạo tranh luận. Tính năng phụ thuộc trực tiếp vào output của khung Steelman Rebuttal (ECHO-02) làm nguồn nội dung nửa dưới thẻ.

## User Story
> Là người dùng thích chia sẻ góc nhìn phản biện lên mạng xã hội,
> Tôi muốn xuất một ảnh 9:16 chia đôi màn hình giữa trích dẫn gốc và luận điểm phản biện của AI,
> Để tôi tạo được nội dung bắt mắt, kích thích thảo luận khi đăng lên Threads/X/Facebook.

## Acceptance Criteria (Gherkin)
- **Given** người dùng xem khung Steelman Rebuttal
- **When** bấm nút "Xuất Thẻ Tranh Biện (Debate Card)"
- **Then** hệ thống tạo ảnh chia đôi với đồ họa tương phản (Đen - Neon / Trắng - Đỏ)
- **And** mở Android Share Sheet kèm hashtag #RSSCatHub #Debate

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [ECHO-03] "Thẻ So Sánh Đối Kháng Viral (Dual-Perspective Quote Card 9:16)" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/ECHO-03_dual-perspective-quote-card.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định). Xác nhận task ECHO-02 (Steelman Rebuttal Box) đã có dữ liệu để dùng làm nguồn nội dung; nếu chưa, chỉ mock tối thiểu để không block tiến độ và ghi rõ giả định.
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [ECHO-03] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [ECHO-03] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
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
```
