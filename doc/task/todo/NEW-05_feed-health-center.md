# [NEW-05] Trung Tâm Sức Khỏe Nguồn Tin (Feed Health Center)

- **Type:** New Feature / Reliability
- **Priority:** `P1 (High)`
- **Estimation:** `5 Story Points`
- **Epic:** 03. NEW — Tính Năng Mới Chuẩn RSS (New Core Features)
- **Location:** [`domain/sv/AbstractRssRepository.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/domain/sv/AbstractRssRepository.kt), [`domain/sv/LocalRssSv.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/domain/sv/LocalRssSv.kt), `ui/page/setting/feedhealth/` (package mới)

## Vấn đề thực tế
**Bối cảnh:** Hiện tại khi một feed lỗi (HTTP 404/timeout, parser lỗi định dạng XML/JSON, SSL...), `sync()` trong `AbstractRssRepository.kt` / `LocalRssSv.kt` chỉ âm thầm bỏ qua hoặc log lỗi nội bộ — người dùng không có bất kỳ chỗ nào trong UI để biết feed nào đang "chết", lỗi từ khi nào, loại lỗi gì. Hệ quả: người dùng tưởng nguồn tin đang hoạt động bình thường trong khi thực ra hàng tuần/tháng không nhận bài mới, chỉ phát hiện tình cờ khi tự kiểm tra thủ công trên trình duyệt.

## User Story
> Là người quản lý nhiều chục đến hàng trăm nguồn RSS,
> Tôi muốn có một màn hình "Feed Health Center" hiển thị rõ tình trạng đồng bộ của từng feed (thời điểm sync thành công gần nhất, độ trễ, loại lỗi gần nhất) và cho phép thử lại ngay,
> Để tôi tự phát hiện và dọn dẹp các feed đã chết/lỗi thay vì âm thầm mất bài mới mà không hay biết.

## Acceptance Criteria (Gherkin)
- **Given** người dùng vào mục quản lý nguồn (Feeds) và mở "Feed Health Center"
- **When** màn hình tải danh sách feed
- **Then** mỗi feed hiển thị: thời điểm sync thành công gần nhất (relative time, ví dụ "2 giờ trước"), độ trễ trung bình (latency) của lần sync gần nhất, loại lỗi gần nhất nếu có (phân loại rõ: HTTP error/Parser error/Timeout/Network unreachable) kèm thời điểm xảy ra
- **And** feed đang lỗi được sắp xếp lên đầu danh sách hoặc có chỉ báo trực quan (badge màu đỏ/cam) để dễ nhận diện
- **When** người dùng bấm nút "Thử lại ngay" trên một feed đang lỗi
- **Then** app kích hoạt sync riêng cho feed đó ngay lập tức (không đợi chu kỳ `SyncWorker` định kỳ) và cập nhật trạng thái (thành công/lỗi mới) ngay khi hoàn tất, không cần thoát màn hình
- **And** dữ liệu trạng thái sync (thời điểm, độ trễ, loại lỗi) được lưu bền vững qua Room để còn nguyên sau khi tắt/mở lại app
- **And** toàn bộ text UI mới có đủ bản dịch trong `strings.xml` của cả 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de).

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [NEW-05] "Trung Tâm Sức Khỏe Nguồn Tin (Feed Health Center)" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/NEW-05_feed-health-center.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location (đặc biệt cách sync/lỗi hiện được xử lý trong AbstractRssRepository.kt và LocalRssSv.kt, cấu trúc bảng Feed trong domain/repository/FeedDao.kt và domain/model/feed/), xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria:
   - Thiết kế lưu trữ trạng thái sync per-feed (thời điểm thành công gần nhất, latency, loại lỗi + thời điểm lỗi) — thêm cột vào entity Feed hiện có hoặc bảng phụ mới qua Room migration, tuỳ đánh giá kiến trúc phù hợp hơn.
   - Cập nhật logic sync trong AbstractRssRepository/LocalRssSv để ghi nhận các trạng thái này, phân loại lỗi rõ ràng (HTTP/Parser/Timeout/Network).
   - Xây UI mới trong package `ui/page/setting/feedhealth/` (ViewModel + Composable) hiển thị danh sách feed kèm trạng thái, sắp xếp feed lỗi lên đầu, nút "Thử lại ngay" trigger sync đơn lẻ cho từng feed.
   Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) cho mọi text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [NEW-05] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [NEW-05] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
4. Build kiểm tra: `./gradlew assembleDevDebug` (hoặc `lintDevDebug` nếu chỉ đổi resource/string).
5. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.
```

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm, nếu có test giả/mock rỗng/logic chưa đúng thì điểm phải phản ánh đúng thực tế.
2. Bổ sung **unit test** cho mọi nhánh logic mới (`app/src/test/...`), phủ cả edge case (rỗng, lỗi mạng, dữ liệu null, giới hạn biên), gồm cả logic phân loại lỗi (HTTP/Parser/Timeout/Network) và tính latency.
3. Bổ sung **widget/Compose UI test** cho mọi component UI mới hoặc thay đổi hành vi UI (`app/src/androidTest/...`), gồm hiển thị badge lỗi và hành vi nút "Thử lại ngay".
4. Bổ sung **integration test** cho luồng end-to-end liên quan (DB + repository + worker nếu có liên quan) — xác nhận trạng thái sync được ghi và đọc đúng qua Room.
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, thao tác thủ công đúng luồng vừa sửa (bao gồm giả lập một feed lỗi, ví dụ URL sai, để xác nhận Feed Health Center hiển thị đúng và nút "Thử lại ngay" hoạt động), ghi lại bằng chứng cụ thể (log logcat hoặc mô tả kết quả quan sát được) chứng minh hoạt động đúng — không suy đoán, không báo cáo khống.
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` hoặc `inprogress/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.
