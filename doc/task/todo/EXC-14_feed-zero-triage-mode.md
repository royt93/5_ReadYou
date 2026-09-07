# [EXC-14] Feed Zero Triage Mode — Chế Độ Vuốt Kiểu Tinder Xử Lý Toàn Bộ Bài Chưa Đọc

- **Type:** Exclusive Killer Feature / Inbox-Zero UX
- **Priority:** `P2 (Medium)`
- **Estimation:** `5 Story Points`
- **Epic:** "05. Tính Năng Độc Quyền & Killer Features (Market Differentiators)"
- **Location:** `app/src/main/java/com/mckimquyen/reader/ui/page/triage/` (Gói mới), [`domain/repository/ArticleDao.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/repository/ArticleDao.kt), [`domain/model/rpg/BrainRpg.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/model/rpg/BrainRpg.kt) + [`domain/repository/BrainRpgRepository.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/repository/BrainRpgRepository.kt) (tích hợp XP nếu có)

## Cơ hội / Động lực
Người dùng theo dõi nhiều feed thường tích lũy hàng trăm bài chưa đọc và cảm giác quá tải khiến họ bỏ hẳn việc mở app ("cảm giác nợ đọc"). Cơ chế cuộn danh sách truyền thống của `FlowPage` không giải quyết được vấn đề tốc độ xử lý hàng loạt. Cơ hội ở đây là mượn mô hình tương tác vuốt quen thuộc (Tinder-style) để biến việc xử lý unread thành 1 phiên nhanh, có nhịp điệu, giống "inbox zero" cho email — chưa có app RSS nào áp dụng UX này. App đã có hệ thống Brain RPG với XP/level (`BrainRpg.kt`, `BrainRpgRepository.kt`) — có thể cộng thêm XP nhỏ cho mỗi hành động triage để tăng động lực hoàn thành, tái sử dụng ngay hạ tầng RPG đã có thay vì xây dựng hệ thống điểm thưởng riêng.

## User Story
> Là người có hàng trăm bài chưa đọc dồn ứ và cảm thấy quá tải mỗi khi mở app,
> Tôi muốn vào 1 chế độ vuốt nhanh để xử lý dứt điểm toàn bộ bài chưa đọc trong một phiên ngắn,
> Để tôi đạt được cảm giác "Inbox Zero" và quay lại dùng app đều đặn mà không còn sợ hãi số lượng bài tồn đọng.

## Acceptance Criteria (Gherkin)
- **Given** người dùng có ít nhất 1 bài viết chưa đọc trong bất kỳ feed/nhóm nào
- **When** bấm vào "⚡ Feed Zero Triage" từ `FlowPage` hoặc từ Settings
- **Then** hiển thị giao diện dạng thẻ (card stack) từng bài một, cho phép: vuốt phải = "Giữ lại đọc sau" (đánh dấu Star/Save), vuốt trái = "Bỏ qua" (đánh dấu đã đọc, không lưu), vuốt lên = "Đánh dấu sao & mở đọc ngay"
- **And** mỗi hành động có animation phản hồi tức thì (< 100ms) và hiển thị bộ đếm tiến độ (ví dụ "42/230 bài đã xử lý")
- **And** nếu `BrainRpgRepository` đang khả dụng (tính năng Brain RPG đã bật), mỗi hành động triage cộng một lượng XP nhỏ tương ứng vào category phù hợp, hiển thị popup nhỏ khi lên cấp
- **And** người dùng có thể tạm dừng phiên bất kỳ lúc nào, tiến độ được lưu lại và tiếp tục đúng vị trí khi mở lại tính năng lần sau
- **And** khi xử lý hết toàn bộ bài chưa đọc, hiển thị màn hình chúc mừng "🎉 Inbox Zero đạt được!" kèm tóm tắt phiên (số bài giữ lại/bỏ qua/đánh dấu sao).

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [EXC-14] "Feed Zero Triage Mode — Chế Độ Vuốt Kiểu Tinder Xử Lý Toàn Bộ Bài Chưa Đọc" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Cơ hội / Động lực" + "Acceptance Criteria" trong file doc/task/todo/EXC-14_feed-zero-triage-mode.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location (đặc biệt ArticleDao.kt và BrainRpgRepository.kt/BrainRpg.kt nếu tích hợp XP), xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria. Nếu Brain RPG (package rpg/) chưa tồn tại hoặc bị gỡ khỏi build, làm tính năng triage hoạt động độc lập không phụ thuộc cứng vào nó (graceful fallback, không crash). Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [EXC-14] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [EXC-14] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
4. Build kiểm tra: `./gradlew assembleDevDebug` (hoặc `lintDevDebug` nếu chỉ đổi resource/string).
5. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.
```

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm, nếu có test giả/mock rỗng/logic chưa đúng thì điểm phải phản ánh đúng thực tế.
2. Bổ sung **unit test** cho mọi nhánh logic mới (`app/src/test/...`), phủ cả edge case (rỗng, lỗi mạng, dữ liệu null, giới hạn biên).
3. Bổ sung **widget/Compose UI test** cho mọi component UI mới hoặc thay đổi hành vi UI (`app/src/androidTest/...`) — đặc biệt test hành vi vuốt (swipe gesture) trên card stack.
4. Bổ sung **integration test** cho luồng end-to-end liên quan (DB + repository + worker nếu có liên quan).
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, thao tác thủ công đúng luồng vừa sửa, ghi lại bằng chứng cụ thể (log logcat hoặc mô tả kết quả quan sát được) chứng minh hoạt động đúng — không suy đoán, không báo cáo khống.
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` hoặc `inprogress/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.
```
