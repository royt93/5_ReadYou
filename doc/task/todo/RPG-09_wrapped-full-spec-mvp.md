# [RPG-09] "Wrapped" Chỉ Là Share Text — Thiếu Radar Chart, Ảnh 1080x1920, Nhắc Lịch Chủ Nhật Như Đặc Tả Gốc

- **Type:** Feature Gap
- **Priority:** `P2 (Medium)`
- **Estimation:** `8 Story Points`
- **Epic:** [12. BRAIN RPG — Game Hóa Đọc Bài, Trắc Nghiệm AI & Wrapped](12_GAMIFIED_BRAIN_RPG_AND_WRAPPED.md)
- **Location:** [`ui/page/rpg/BrainRpgPage.kt`](../../../app/src/main/java/com/mckimquyen/reader/ui/page/rpg/BrainRpgPage.kt#L112-L129) (`onShare`), [`ui/page/rpg/BrainRpgPage.kt`](../../../app/src/main/java/com/mckimquyen/reader/ui/page/rpg/BrainRpgPage.kt#L451-L517) (`WeeklyWrappedCard`)

## Vấn đề thực tế
`ui/page/rpg/BrainRpgPage.kt` — tính năng chia sẻ "Brain Wrapped" hiện tại (`onShare`, dòng 116-126) chỉ tạo một chuỗi text đơn giản gồm level/XP/streak/quiz-accuracy rồi gọi `Intent.ACTION_SEND` với `type = "text/plain"`. So với đặc tả gốc (`RPG-04`), còn thiếu hoàn toàn:
- Thống kê theo tuần (hiện chỉ hiển thị số liệu tổng/all-time, không có bộ lọc theo tuần).
- Biểu đồ mạng nhện đa giác (Brain Radar Chart) theo category.
- Ảnh xuất theo tỉ lệ 1080×1920 (story-ready) — hiện không render bitmap/Compose canvas nào, chỉ share text thuần.
- Banner/nhắc lịch tự động vào sáng Chủ Nhật (hiện không có banner nào xuất hiện tự động, người dùng phải tự vào trang Brain RPG).
- Danh hiệu dạng "Top X% ..." hay logo/QR code trong ảnh chia sẻ.

## User Story
> Là người dùng đã tích lũy XP/streak trong tuần,
> Tôi muốn nhận một banner nhắc nhở vào Chủ Nhật và chia sẻ được một tấm ảnh trực quan (không chỉ text) lên mạng xã hội,
> Để trải nghiệm "khoe thành tích" hấp dẫn hơn và tạo động lực viral thực sự như Spotify Wrapped.

## Acceptance Criteria (Gherkin)
> Có thể triển khai theo 2 giai đoạn — MVP trước, đầy đủ sau — miễn là mỗi giai đoạn có AC rõ ràng và được note trong Completion Report giai đoạn nào đã hoàn thành.

**Giai đoạn MVP (bắt buộc để đóng task ở mức tối thiểu chấp nhận được):**
- **Given** người dùng đã có ít nhất 1 XP trong tuần hiện tại
- **When** người dùng bấm "Chia sẻ lên Story" trong `WeeklyWrappedCard`
- **Then** ứng dụng render một `Composable` riêng (không phải Card hiển thị trong danh sách) theo tỉ lệ khung hình 1080×1920 (9:16), dùng `androidx.compose.ui.graphics.layer` hoặc `ComposeView` off-screen + `Bitmap` capture (pattern tương tự đã dùng cho tính năng chia sẻ khác trong app nếu có, ví dụ `IDEA-03_quote-card-social-share.md`) để xuất ra file ảnh PNG
- **And** ảnh xuất ra bao gồm tối thiểu: tổng XP tuần này (không phải all-time), streak hiện tại, chủ đề đọc nhiều nhất (category có XP cao nhất trong tuần), logo app
- **And** Android Share Sheet mở ra với ảnh PNG đính kèm (`FileProvider`, không dùng share text thuần nữa)

**Giai đoạn đầy đủ (đúng 100% đặc tả RPG-04, có thể tách task con nếu cần):**
- **And** ảnh có biểu đồ radar đa giác theo category (dùng thư viện chart hiện có trong app nếu có, hoặc vẽ bằng Canvas Compose thủ công)
- **And** có mã QR trỏ về link tải app trong ảnh
- **And** vào 8:00 sáng Chủ Nhật hàng tuần, xuất hiện banner nổi bật trong app nhắc "Bản Tóm Tắt Trí Tuệ Tuần Này Của Bạn" (dùng WorkManager định kỳ hoặc kiểm tra ngày khi mở app, KHÔNG dùng notification nếu app chưa có permission phù hợp — ưu tiên in-app banner trước)
- **And** danh hiệu động dạng "Top X% ..." dựa trên so sánh XP tuần với ngưỡng nội bộ do team định nghĩa (không cần backend thật nếu chưa có, có thể dùng ngưỡng cứng hợp lý ở phiên bản đầu)

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [RPG-09] ""Wrapped" Chỉ Là Share Text — Thiếu Radar Chart, Ảnh 1080x1920, Nhắc Lịch Chủ Nhật" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/RPG-09_wrapped-full-spec-mvp.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định) — kiểm tra onShare trong BrainRpgPage.kt còn dùng Intent.ACTION_SEND text/plain hay đã có render ảnh chưa. Kiểm tra xem có task/feature chia sẻ ảnh nào khác trong app đã có sẵn pattern (ví dụ tìm kiếm "FileProvider", "createBitmap", "captureToImage" trong codebase) để tái sử dụng thay vì viết lại từ đầu.
2. Ưu tiên implement Giai đoạn MVP trước theo Acceptance Criteria — đây là điều kiện tối thiểu để coi task có tiến triển thật. Chỉ làm tiếp Giai đoạn đầy đủ (radar chart, QR, banner Chủ Nhật) nếu còn ngân sách/thời gian trong vòng lặp hiện tại; nếu không, dừng ở MVP và ghi rõ trong Completion Report phần nào đã làm/chưa làm, tạo lại task con cho phần còn thiếu nếu cần.
3. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng (render bitmap, ghi file) chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity vào ViewModel/singleton ngoài phạm vi cho phép, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) cho mọi text mới trong ảnh/banner.
4. Với thay đổi kiến trúc/thiết kế quan trọng (bắt buộc cho task này vì thêm cơ chế render ảnh mới), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task RPG-09 trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm, MVP hay full>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task RPG-09 trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm, MVP hay full>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
5. Build kiểm tra: `./gradlew assembleDevDebug`.
6. Lặp lại tới khi Acceptance Criteria của giai đoạn đã chọn thỏa mãn 100%.
```

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria (giai đoạn đã chọn) + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm; nếu chỉ hoàn thành MVP thì điểm chỉ nên phản ánh đúng phạm vi MVP, không chấm như đã đạt full spec.
2. Bổ sung **unit test** cho mọi nhánh logic mới (`app/src/test/...`) — ví dụ: tính "chủ đề đọc nhiều nhất trong tuần", tính danh hiệu "Top X%" nếu có.
3. Bổ sung **widget/Compose UI test** cho `Composable` render ảnh Wrapped mới (`app/src/androidTest/...`) — xác nhận kích thước ảnh xuất ra đúng tỉ lệ 1080x1920.
4. Bổ sung **integration test** cho luồng chia sẻ end-to-end (render bitmap → FileProvider → Share Sheet intent được tạo đúng).
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, vào trang Brain RPG, bấm chia sẻ, xác nhận Share Sheet mở ra với ảnh PNG đính kèm đúng nội dung XP/streak tuần hiện tại — ghi lại bằng chứng cụ thể (screenshot hoặc mô tả logcat).
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` hoặc `inprogress/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm, giai đoạn đã hoàn thành MVP/full).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.
