# [EXC-13] Second Screen Mirror — Quét QR Mirror Bài Đang Đọc Sang Máy Tính Qua LAN

- **Type:** Exclusive Killer Feature / Local P2P UX
- **Priority:** `P3 (Low)`
- **Estimation:** `8 Story Points`
- **Epic:** "05. Tính Năng Độc Quyền & Killer Features (Market Differentiators)"
- **Location:** `app/src/main/java/com/mckimquyen/reader/infrastructure/mirror/` (Gói mới), [`ui/page/home/read/ReadingPage.kt`](../../../app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingPage.kt) (điểm gắn trạng thái "đang đọc bài nào")

## Cơ hội / Động lực
Người dùng đọc bài dài (báo cáo phân tích, bài nghiên cứu) trên điện thoại nhưng màn hình nhỏ bất tiện khi đang ngồi trước máy tính. Không có app RSS nào cung cấp cách "ném" bài đang đọc sang màn hình lớn hơn mà không cần tài khoản cloud hay app đồng hành riêng. Tính năng này tận dụng cùng triết lý privacy-first như EXC-05 (Local P2P Sync): quét QR code hiển thị trên máy tính (một trang web nhẹ tự chạy local server) để pair 2 thiết bị trong cùng mạng LAN, sau đó mirror theo thời gian thực nội dung/vị trí cuộn của bài đang đọc trên điện thoại sang trình duyệt máy tính — hoàn toàn không qua cloud, không cần tài khoản.

## User Story
> Là người đang đọc một bài phân tích dài trên điện thoại khi ngồi cạnh máy tính,
> Tôi muốn quét mã QR để mirror ngay bài đang đọc lên màn hình máy tính lớn hơn,
> Để tôi đọc thoải mái hơn mà không cần đăng nhập tài khoản hay cài thêm app nào trên máy tính.

## Acceptance Criteria (Gherkin)
- **Given** điện thoại và máy tính cùng kết nối chung 1 mạng Wi-Fi/LAN
- **When** người dùng mở "🖥️ Second Screen" trong `ReadingPage`, ứng dụng khởi chạy 1 local HTTP server nhẹ trên điện thoại và hiển thị mã QR chứa địa chỉ IP:port nội bộ
- **Then** người dùng dùng camera/trình duyệt máy tính quét QR (hoặc nhập URL) để mở trang web view hiển thị nội dung bài đang đọc
- **And** khi người dùng cuộn/chuyển bài trên điện thoại, trang web trên máy tính tự động cập nhật theo thời gian thực (độ trễ < 1 giây) qua kết nối WebSocket/socket nội bộ trong LAN
- **And** kết nối tự ngắt và server tự tắt khi người dùng rời màn hình đọc hoặc đóng tính năng, không chạy nền tốn pin khi không dùng
- **And** không có bất kỳ dữ liệu nào gửi ra ngoài phạm vi mạng LAN hiện tại (không có server trung gian trên internet).

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [EXC-13] "Second Screen Mirror — Quét QR Mirror Bài Đang Đọc Sang Máy Tính Qua LAN" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Cơ hội / Động lực" + "Acceptance Criteria" trong file doc/task/todo/EXC-13_second-screen-mirror.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định). Nếu EXC-05 (Local P2P Sync) đã có hạ tầng NSD/discovery trong infrastructure/p2p/, cân nhắc tái sử dụng thay vì viết lại từ đầu.
2. Implement fix/feature đúng theo Acceptance Criteria, đảm bảo server nội bộ tự tắt khi rời màn hình để không tốn pin/rò rỉ tài nguyên. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [EXC-13] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [EXC-13] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
4. Build kiểm tra: `./gradlew assembleDevDebug` (hoặc `lintDevDebug` nếu chỉ đổi resource/string).
5. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.
```

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm, nếu có test giả/mock rỗng/logic chưa đúng thì điểm phải phản ánh đúng thực tế.
2. Bổ sung **unit test** cho mọi nhánh logic mới (`app/src/test/...`), phủ cả edge case (rỗng, lỗi mạng, dữ liệu null, giới hạn biên).
3. Bổ sung **widget/Compose UI test** cho mọi component UI mới hoặc thay đổi hành vi UI (`app/src/androidTest/...`).
4. Bổ sung **integration test** cho luồng end-to-end liên quan (DB + repository + worker nếu có liên quan) — bao gồm test khởi động/tắt local server đúng vòng đời màn hình.
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, thao tác thủ công đúng luồng vừa sửa (bao gồm test thực tế với trình duyệt máy tính trong cùng LAN), ghi lại bằng chứng cụ thể (log logcat hoặc mô tả kết quả quan sát được) chứng minh hoạt động đúng — không suy đoán, không báo cáo khống.
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` hoặc `inprogress/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.
```
