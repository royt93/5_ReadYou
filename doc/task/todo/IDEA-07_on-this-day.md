# [IDEA-07] "Hôm Nay Năm Ngoái Đã Đọc" (On This Day)

- **Type:** User Engagement / Nostalgia Retention
- **Priority:** `P3 (Low)`
- **Estimation:** `3 Story Points`
- **Epic:** 04. IDEAS — Ý Tưởng Trải Nghiệm & Tăng Trưởng (Ideas & Engagement)
- **Location:** `app/src/main/java/com/mckimquyen/reader/domain/repository/ArticleDao.kt`, `app/src/main/java/com/mckimquyen/reader/ui/page/home/` (section/màn hình mới)

## Vấn đề thực tế
Bảng `article` trong Room đã lưu sẵn `date` (thời điểm xuất bản/đọc) và `isRead` cho từng bài viết, nhưng app hiện không khai thác dữ liệu lịch sử này để tạo cảm giác hoài niệm. Người dùng không có cách nào xem lại nhanh những gì họ đã đọc đúng ngày này các năm trước, dù chi phí triển khai chỉ là một truy vấn theo khoảng ngày (date range query) trên dữ liệu đã có sẵn — không cần thêm bảng hay đồng bộ mới.

## User Story
> Là độc giả đã dùng app lâu năm và tích lũy lịch sử đọc,
> Tôi muốn xem lại các bài báo hoặc điểm nhấn mình đã đọc đúng ngày này 1 năm trước,
> Để tôi có trải nghiệm hoài niệm thú vị và có động lực quay lại app thường xuyên hơn.

## Acceptance Criteria (Gherkin)
- **Given** người dùng có bài viết đã đọc (`isRead = true`) với `date` trùng ngày/tháng hiện tại nhưng thuộc năm trước (hoặc các năm trước, nếu có nhiều năm dữ liệu)
- **When** người dùng mở app vào ngày hôm nay
- **Then** hiển thị section/card "Hôm nay năm ngoái đã đọc" (ví dụ trên màn hình Feed hoặc trong Settings/Statistics) liệt kê tối đa 5 bài viết phù hợp, sắp xếp mới nhất trước
- **And** truy vấn chỉ dựa trên khoảng ngày (day/month match, không cần full-text search hay AI), chạy trên `Dispatchers.IO`, không block Main Thread
- **And** nếu không có bài viết nào phù hợp trong ngày đó, section tự ẩn (không hiển thị placeholder rỗng gây khó chịu)
- **And** bấm vào 1 bài trong danh sách sẽ mở lại đúng bài viết đó trong màn hình đọc.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [IDEA-07] ""Hôm Nay Năm Ngoái Đã Đọc" (On This Day)" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/IDEA-07_on-this-day.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [IDEA-07] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [IDEA-07] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
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
