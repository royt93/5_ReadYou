# [ECHO-02] Khung Luận Điểm Phản Biện Thép (Steelman Rebuttal Box)

- **Type:** New Feature / AI Innovation
- **Priority:** `P1 (High)`
- **Estimation:** `8 Story Points`
- **Epic:** "16. EchoChamber Destroyer & Bias Radar (Phá Vỡ Buồng Vang & Đấu Trường Phản Biện)"
- **Location:** `app/src/main/java/com/mckimquyen/reader/infrastructure/ai/` (module mới, ví dụ `SteelmanRebuttalEngine.kt`, theo mẫu của [`ArticleDeepReadEngine.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/ai/ArticleDeepReadEngine.kt)), [`ui/page/home/read/Content.kt`](../../../app/src/main/java/com/mckimquyen/reader/ui/page/home/read/Content.kt), [`ui/page/home/read/ReadingViewModel.kt`](../../../app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModel.kt), phụ thuộc kết quả từ ECHO-01 (điểm thiên kiến làm input ngữ cảnh)

## Vấn đề thực tế
App hiện không cung cấp bất kỳ góc nhìn phản biện nào cho bài viết — người đọc chỉ tiếp nhận một chiều quan điểm của tác giả gốc, củng cố thêm hiệu ứng buồng vang. Cần một khung "Steelman Rebuttal" ở cuối bài đọc (`ui/page/home/read/Content.kt`), do AI sinh ra dựa trên luận điểm mạnh nhất (không phải strawman yếu ớt) của phía đối lập, kèm nguồn tham khảo uy tín. Hạ tầng gọi AI đã có sẵn qua các engine tương tự (`ArticleDeepReadEngine.kt`, `ArticleMindMapExtractor.kt`) làm mẫu tham khảo cấu trúc.

## User Story
> Là độc giả muốn tư duy đa chiều thay vì chỉ tin một phía,
> Tôi muốn cuộn xuống cuối bài và thấy ngay khung phản biện AI trình bày luận điểm đối lập chặt chẽ nhất kèm nguồn dẫn chứng,
> Để tôi có đủ thông tin cân bằng trước khi hình thành quan điểm cá nhân.

## Acceptance Criteria (Gherkin)
- **Given** người dùng đọc xong một bài viết ca ngợi một công nghệ mới
- **When** cuộn xuống phần cuối bài
- **Then** hiển thị khung màu xanh xám "Góc Nhìn Phản Biện (Steelman Perspective)"
- **And** liệt kê 3 rủi ro cốt lõi và luận chứng phản biện mà tác giả bài viết đã bỏ qua

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [ECHO-02] "Khung Luận Điểm Phản Biện Thép (Steelman Rebuttal Box)" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/ECHO-02_steelman-rebuttal-box.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [ECHO-02] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [ECHO-02] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
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
