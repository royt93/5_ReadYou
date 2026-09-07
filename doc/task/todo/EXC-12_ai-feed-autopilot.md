# [EXC-12] AI Feed Autopilot — Tự Học Hành Vi Đọc & Gợi Ý Unsubscribe Feed Gây Nhiễu

- **Type:** Exclusive Killer Feature / On-Device Behavioral AI
- **Priority:** `P2 (Medium)`
- **Estimation:** `8 Story Points`
- **Epic:** "05. Tính Năng Độc Quyền & Killer Features (Market Differentiators)"
- **Location:** `app/src/main/java/com/mckimquyen/reader/infrastructure/ai/autopilot/` (Gói mới), [`domain/repository/ArticleDao.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/repository/ArticleDao.kt), [`domain/repository/FeedDao.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/repository/FeedDao.kt), [`domain/sv/AbstractRssRepository.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/sv/AbstractRssRepository.kt)

## Cơ hội / Động lực
Feedly và Inoreader chỉ có thuật toán ranking bài viết cố định (dựa trên độ phổ biến/tương tác cộng đồng, thường đẩy dữ liệu người dùng lên cloud để tính). RSS Cat Hub có lợi thế 100% on-device: `ArticleDao`/`FeedDao` đã lưu đầy đủ trạng thái đọc/chưa đọc, thời gian đọc, và bài bị bỏ qua (skip) theo từng feed. Tuy nhiên hiện tại không có bất kỳ phân tích hành vi nào — người dùng tự phải nhận ra và unsubscribe feed gây nhiễu (đăng nhiều nhưng ít khi đọc). Đây là cơ hội xây dựng một bộ học hành vi đơn giản, minh bạch, hoàn toàn on-device (không cần model AI nặng, không gửi dữ liệu đọc lên cloud) để tính "điểm nhiễu" cho từng feed và chủ động gợi ý unsubscribe — một khác biệt bản chất về triết lý privacy-first so với đối thủ.

## User Story
> Là người theo dõi hàng chục feed nhưng dần cảm thấy quá tải,
> Tôi muốn app tự nhận ra feed nào tôi hầu như không bao giờ đọc dù đăng bài liên tục,
> Để tôi được gợi ý bỏ theo dõi những feed đó mà không phải tự rà soát thủ công.

## Acceptance Criteria (Gherkin)
- **Given** một feed đã tồn tại tối thiểu 14 ngày và có tối thiểu 20 bài viết mới trong khoảng thời gian đó
- **When** engine autopilot chạy định kỳ (ví dụ mỗi tuần, nền, không block UI) tính "điểm nhiễu" dựa trên tỷ lệ đọc thực tế (số bài đã mở/đọc hết ÷ tổng số bài nhận được), tỷ lệ bị vuốt bỏ qua (skip) liên tục, và thời gian đọc trung bình mỗi bài
- **Then** nếu điểm nhiễu vượt ngưỡng cấu hình được (mặc định: tỷ lệ đọc < 5% trong ≥ 30 bài liên tiếp), feed đó được đưa vào danh sách gợi ý tại màn hình "🤖 AI Autopilot Suggestions"
- **And** mỗi gợi ý hiển thị lý do cụ thể bằng số liệu thực tế (ví dụ: "Bạn đã bỏ qua 47/50 bài gần nhất từ feed này")
- **And** người dùng có 3 lựa chọn cho mỗi gợi ý: "Unsubscribe ngay", "Bỏ qua gợi ý này" (không hỏi lại feed đó trong 30 ngày), hoặc "Giữ lại & không gợi ý nữa" — app KHÔNG BAO GIỜ tự động unsubscribe mà không có xác nhận của người dùng
- **And** toàn bộ tính toán và lưu trữ điểm nhiễu diễn ra hoàn toàn on-device, không có bất kỳ request mạng nào phục vụ tính năng này.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [EXC-12] "AI Feed Autopilot — Tự Học Hành Vi Đọc & Gợi Ý Unsubscribe Feed Gây Nhiễu" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Cơ hội / Động lực" + "Acceptance Criteria" trong file doc/task/todo/EXC-12_ai-feed-autopilot.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location (đặc biệt ArticleDao.kt, FeedDao.kt để biết dữ liệu đọc/skip hiện có ở đâu), xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria, đảm bảo KHÔNG BAO GIỜ tự động unsubscribe mà không xác nhận người dùng và KHÔNG gửi dữ liệu hành vi đọc lên bất kỳ endpoint mạng nào. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [EXC-12] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [EXC-12] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
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
