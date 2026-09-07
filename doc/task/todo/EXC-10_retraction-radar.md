# [EXC-10] Retraction Radar — Phát Hiện Bài Báo Bị Sửa Âm Thầm Hoặc Bị Gỡ Bỏ

- **Type:** Exclusive Killer Feature / Media Literacy & Trust
- **Priority:** `P1 (High)`
- **Estimation:** `8 Story Points`
- **Epic:** "05. Tính Năng Độc Quyền & Killer Features (Market Differentiators)"
- **Location:** `app/src/main/java/com/mckimquyen/reader/infrastructure/articlehistory/` (Gói mới), [`infrastructure/db/AndroidDb.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/db/AndroidDb.kt) (version hiện tại = 7, cần bump + migration), [`domain/repository/ArticleDao.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/repository/ArticleDao.kt), [`domain/sv/SyncWorker.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/sv/SyncWorker.kt)

## Cơ hội / Động lực
Nhiều tòa soạn sửa tiêu đề/nội dung bài đã đăng mà không thông báo ("stealth edit"), hoặc gỡ bài hoàn toàn khi có tranh cãi/pháp lý — độc giả RSS thông thường không bao giờ biết điều này vì app chỉ lưu 1 bản snapshot mới nhất (hoặc không lưu gì, chỉ hiển thị link). Đây là cơ hội độc quyền: chưa có app RSS phổ biến nào (Feedly, Inoreader, Read You gốc) cung cấp tính năng theo dõi lịch sử chỉnh sửa bài báo. Cần một package mới `infrastructure/articlehistory/` lưu snapshot tiêu đề + nội dung mỗi lần `SyncWorker` đồng bộ, và so sánh với snapshot trước đó để phát hiện thay đổi/gỡ bỏ. `AndroidDb.kt` hiện ở `version = 7`, cần thêm bảng mới + `Migration` tương ứng (không được phá schema cũ).

## User Story
> Là người coi trọng tính minh bạch của báo chí,
> Tôi muốn được cảnh báo khi một bài báo tôi đã đọc bị chỉnh sửa nội dung hoặc bị gỡ bỏ hoàn toàn,
> Để tôi biết được điều gì đã thay đổi và tự đánh giá độ tin cậy của nguồn tin đó.

## Acceptance Criteria (Gherkin)
- **Given** một bài viết đã được đồng bộ lần đầu và lưu snapshot tiêu đề + nội dung (hash + full text) trong bảng lịch sử mới
- **When** `SyncWorker` chạy đồng bộ lần tiếp theo và phát hiện bài viết cùng ID/link có tiêu đề hoặc nội dung khác với snapshot gần nhất (so sánh hash)
- **Then** lưu thêm 1 bản snapshot mới kèm timestamp, đánh dấu bài viết có cờ "đã chỉnh sửa"
- **And** nếu bài viết không còn xuất hiện trong feed nguồn nữa (bị gỡ) sau N chu kỳ sync liên tiếp, đánh dấu cờ "đã gỡ bỏ"
- **And** trên `ArticleItem`/`ReadingPage` hiển thị huy hiệu "✏️ Đã chỉnh sửa" hoặc "🗑️ Đã gỡ bỏ", bấm vào mở màn hình diff hiển thị rõ phần tiêu đề/nội dung cũ (gạch ngang, đỏ) và mới (xanh) kiểu diff văn bản
- **And** người dùng có thể tắt tính năng này trong Settings (mặc định bật) để tiết kiệm dung lượng DB nếu không cần
- **And** có cơ chế dọn dẹp snapshot cũ (ví dụ giữ tối đa N phiên bản/bài hoặc X ngày) để tránh phình DB vô hạn.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [EXC-10] "Retraction Radar — Phát Hiện Bài Báo Bị Sửa Âm Thầm Hoặc Bị Gỡ Bỏ" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Cơ hội / Động lực" + "Acceptance Criteria" trong file doc/task/todo/EXC-10_retraction-radar.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location (đặc biệt AndroidDb.kt để biết version hiện tại và ArticleDao.kt/SyncWorker.kt để biết luồng sync), xác nhận vấn đề còn tồn tại (không giả định).
2. Implement fix/feature đúng theo Acceptance Criteria. Với thay đổi Room schema: PHẢI viết Migration tường minh (không dùng fallbackToDestructiveMigration), export schema đúng vào app/schemas, không phá dữ liệu người dùng hiện có. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task [EXC-10] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task [EXC-10] trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
4. Build kiểm tra: `./gradlew assembleDevDebug` (hoặc `lintDevDebug` nếu chỉ đổi resource/string).
5. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.
```

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm, nếu có test giả/mock rỗng/logic chưa đúng thì điểm phải phản ánh đúng thực tế.
2. Bổ sung **unit test** cho mọi nhánh logic mới (`app/src/test/...`), phủ cả edge case (rỗng, lỗi mạng, dữ liệu null, giới hạn biên), bao gồm test riêng cho Room `Migration`.
3. Bổ sung **widget/Compose UI test** cho mọi component UI mới hoặc thay đổi hành vi UI (`app/src/androidTest/...`).
4. Bổ sung **integration test** cho luồng end-to-end liên quan (DB + repository + worker nếu có liên quan) — bắt buộc với task này vì có thay đổi schema DB và luồng SyncWorker.
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, thao tác thủ công đúng luồng vừa sửa, ghi lại bằng chứng cụ thể (log logcat hoặc mô tả kết quả quan sát được) chứng minh hoạt động đúng — không suy đoán, không báo cáo khống.
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` hoặc `inprogress/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.
```
