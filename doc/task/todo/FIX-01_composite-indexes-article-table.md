# [FIX-01] Composite Indexes trên bảng `article` trong Room Database

- **Type:** Bug / Performance Defect
- **Priority:** `P0 (Blocker)`
- **Estimation:** `3 Story Points`
- **Epic:** 01. FIX — Sửa Lỗi, Ổn Định & Ad Unit
- **Location:** [`domain/model/article/Article.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/domain/model/article/Article.kt), [`infrastructure/db/AndroidDb.kt`](file:///Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou/app/src/main/java/com/mckimquyen/reader/infrastructure/db/AndroidDb.kt)

## Vấn đề thực tế
Bảng `article` hiện chỉ có index đơn trên `feedId` và `accountId`. Hầu hết các câu query trong `ArticleDao` đều lọc theo `accountId + isUnread + date` hoặc `accountId + feedId + isUnread + date`. Khi số lượng bài báo vượt quá 5,000+, SQLite buộc phải scan và sort bằng temporary table, gây lag giật khung hình ở `FlowPage` và tốn pin.

## User Story
> Là người dùng đọc tin tức,
> Tôi muốn danh sách bài viết hiển thị tức thì không bị giật lag khi cuộn,
> Để tôi có trải nghiệm mượt mà ngay cả khi có hàng nghìn bài báo đã lưu.

## Acceptance Criteria (Gherkin)
- **Given** database phiên bản 6 đang hoạt động
- **When** app nâng cấp lên database phiên bản 7
- **Then** migration `MIGRATION_6_7` được kích hoạt tạo các index:
  - `index_article_account_unread_date` trên `(accountId, isUnread, date DESC)`
  - `index_article_account_feed_unread_date` trên `(accountId, feedId, isUnread, date DESC)`
  - `index_article_account_starred_date` trên `(accountId, isStarred, date DESC)`
- **And** các query PagingSource trong `ArticleDao` đạt tốc độ < 10ms trên tập dữ liệu 20,000 bài.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [FIX-01] "Composite Indexes trên bảng article trong Room Database" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md để hiểu kiến trúc). Đọc kỹ "Vấn đề thực tế" + "Acceptance Criteria" trong file doc/task/todo/FIX-01_composite-indexes-article-table.md trước khi bắt đầu.

Mỗi vòng lặp:
1. Đọc code liên quan tại phần Location, xác nhận vấn đề còn tồn tại (không giả định) — kiểm tra Article.kt có @Index nào đã tồn tại, AndroidDb.kt có MIGRATION_6_7 chưa.
2. Implement fix/feature đúng theo Acceptance Criteria. Tuân thủ CLAUDE.md: phân tầng domain/infrastructure/ui, mọi I/O nặng chạy Dispatchers.IO/Default (không block Main), không lưu Context/Activity/LazyListState vào ViewModel hay singleton, localize đủ 6 ngôn ngữ (en, vi, zh-rCN, ja, fr, de) nếu có text UI mới. Room schema export ra app/schemas phải khớp version mới.
3. Với thay đổi kiến trúc/thiết kế quan trọng (không bắt buộc cho fix nhỏ, 1-2 dòng), tham khảo ý kiến độc lập từ 2 AI agent khác trước khi chốt:
   - `codex exec -s workspace-write "Review approach cho task FIX-01 trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có."`
   - `claude -p "Review approach cho task FIX-01 trong repo RSS Cat Hub: <tóm tắt ngắn cách bạn định làm>. Chỉ ra rủi ro/cách tốt hơn nếu có." --allowedTools "Read Grep Glob"`
   Đối chiếu góp ý, chỉ áp dụng nếu hợp lý — không áp dụng máy móc, không để agent ngoài tự sửa code của bạn.
4. Build kiểm tra: `./gradlew assembleDevDebug` (hoặc `lintDevDebug` nếu chỉ đổi resource/string).
5. Lặp lại tới khi Acceptance Criteria thỏa mãn 100%.
```

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
Chỉ dừng loop khi hoàn tất TẤT CẢ bước sau, đúng thứ tự, KHÔNG bỏ bước:
1. **Audit code changes**: tự review lại toàn bộ `git diff` so với Acceptance Criteria + Definition of Done trong doc/task/README.md. Chấm điểm khách quan trên thang **10** — không tự thổi điểm, nếu có test giả/mock rỗng/logic chưa đúng thì điểm phải phản ánh đúng thực tế.
2. Bổ sung **unit test** cho mọi nhánh logic mới (`app/src/test/...`), phủ cả edge case (rỗng, lỗi mạng, dữ liệu null, giới hạn biên) — ví dụ test migration trên SQLite in-memory kiểm tra `sqlite_master` có đủ 3 index.
3. Bổ sung **widget/Compose UI test** cho mọi component UI mới hoặc thay đổi hành vi UI (`app/src/androidTest/...`).
4. Bổ sung **integration test** cho luồng end-to-end liên quan (DB + repository + worker nếu có liên quan).
5. Chạy **smoke test trên device/emulator thật**: `./gradlew installDevDebug`, thao tác thủ công đúng luồng vừa sửa, ghi lại bằng chứng cụ thể (log logcat hoặc mô tả kết quả quan sát được) chứng minh hoạt động đúng — không suy đoán, không báo cáo khống.
6. Nếu điểm audit **> 9/10 VÀ** mọi test bước 2-4 pass **VÀ** smoke test bước 5 xác nhận hoạt động đúng:
   → `git add` các file liên quan → `git commit` với message rõ ràng, đúng Conventional Commits → **`git push`** lên remote nhánh hiện tại. Kết thúc loop, cập nhật trạng thái task (di chuyển file từ `doc/task/todo/` hoặc `inprogress/` sang `doc/task/done/`, đổi tên thêm hậu tố `_DONE` và viết Completion Report ngắn: điểm số, commit hash, danh sách test đã thêm).
7. Nếu điểm **≤ 9/10** hoặc bất kỳ điều kiện bước 2-5 chưa đạt: quay lại bước 1 của vòng lặp Loop Prompt, KHÔNG commit/push.

---
> **Ghi chú audit (2026-09-06):** Đã xác minh trong code hiện tại — `Article.kt` đã có 3 `Index` này (dòng 14-16) và `AndroidDb.kt` đã có `MIGRATION_6_7` (dòng 205, version = 7). Task này **có vẻ đã được implement** — trước khi bắt đầu loop, hãy kiểm tra lại `git log`/`git diff` xem đã có commit liên quan chưa; nếu code đã đúng 100% Acceptance Criteria và có test đi kèm, di chuyển file này sang `doc/task/done/` với hậu tố `_DONE` thay vì chạy lại loop.
