# [FIX-17] Tránh CursorWindow overflow khi đọc danh sách bài dài

- **Type:** Bug / Performance
- **Priority:** `P1 (High)`
- **Estimation:** `5 Story Points`
- **Epic:** 01. FIX — Sửa Lỗi, Ổn Định & Ad Unit
- **Location:** [`ArticleDao.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/repository/ArticleDao.kt#L578), [`HomeViewModel.kt`](../../../app/src/main/java/com/mckimquyen/reader/ui/page/home/HomeViewModel.kt#L170), [`ReelsViewModel.kt`](../../../app/src/main/java/com/mckimquyen/reader/ui/page/reels/ReelsViewModel.kt#L53), [`ReadNewsController.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/sv/ReadNewsController.kt#L57), [`CommuteWorker.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/sv/CommuteWorker.kt#L85), [`DailyEditionWorker.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/sv/DailyEditionWorker.kt#L113), [`DailyDigestSv.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/sv/DailyDigestSv.kt#L24), [`SemanticEmbeddingIndexer.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/ai/search/SemanticEmbeddingIndexer.kt#L18), [`SecondBrainSv.kt`](../../../app/src/main/java/com/mckimquyen/reader/domain/sv/SecondBrainSv.kt#L31)

## Vấn đề thực tế

- `ArticleDao.queryRecentArticlesWithFeed()` và `queryLatestUnread()` dùng `SELECT * FROM article`, do đó Room materializes `rawDescription` và `fullContent` cùng với metadata.
- Reproduced trên `emulator-5554` (2026-10-20): sau subscribe feed NPR có 34 items (một `fullContent` dài khoảng 100,990 ký tự) và Planet Money 355 items, mở Planet Money gây toast `Row too big to fit into CursorWindow requiredPos=14, totalRows=15`; logcat stack trỏ `ArticleDao_Impl.lambda$queryRecentArticlesWithFeed$14`. Cũng thấy `CommuteWorker` gặp `SQLiteBlobTooBigException` từ `queryLatestUnread` trên cùng database.
- Lỗi bị bắt ở caller, làm list/worker trả kết quả rỗng thay vì hiển thị lỗi. Bài đơn lẻ lớn hơn cửa sổ cũng có thể gây lỗi; chia page/batch mà vẫn select toàn body không tự giải quyết.

## User Story

> Là người đọc RSS có feed chứa bài rất dài,
> Tôi muốn danh sách và các bộ chọn bài chỉ tải metadata, còn body đầy đủ chỉ được lấy cho các bài thực sự cần đọc/tóm tắt,
> Để Home, feed, Reels, TTS, CommuteCast và các tác vụ chọn bài không lỗi CursorWindow hoặc âm thầm thành rỗng.

## Acceptance Criteria (Gherkin)

- **Given** một database Room với feed có nhiều bài và ít nhất một `fullContent` lớn hơn 100 KB,
  **When** `queryRecentArticlesWithFeed` chạy cho account đó,
  **Then** query trả đúng ID/thứ tự/metadata/feed relation mà không đọc `rawDescription`/`fullContent`, và không ném `SQLiteBlobTooBigException`.
- **Given** unread candidates có body dài,
  **When** `queryLatestUnread` chạy,
  **Then** query trả lightweight metadata trong phạm vi account/limit/order và không select body.
- **Given** consumer TTS hoặc CommuteCast chọn một tập con article IDs,
  **When** consumer cần lời đọc/nội dung tổng hợp,
  **Then** DAO lấy body đầy đủ chỉ cho các ID được chọn, account-scoped; văn bản, title, feed name và precedence `fullContent ?: rawDescription` không đổi.
- **Given** request body IDs rỗng, có ID lạ, ID account khác hoặc số ID vượt bind limit SQLite,
  **When** body lookup chạy,
  **Then** kết quả an toàn (rỗng/loại ID không thuộc account/chunk đúng giới hạn), không tạo SQL `IN ()` và caller giữ đúng thứ tự.
- **Given** Home clustering/semantic/Second Brain dùng title và short description, hoặc Reels cần body cho candidate hữu hạn,
  **When** các luồng này chạy sau refactor,
  **Then** kết quả hiện tại không đổi; Reels vẫn nhận body đầy đủ cho candidates của nó.
- **Given** toàn bộ sửa đổi,
  **When** chạy `./gradlew testDevDebugUnitTest assembleDevDebug compileDevDebugAndroidTestKotlin`,
  **Then** build/test pass và unit/Room tests tái hiện đúng regression trước khi fix.
- **Given** emulator/thiết bị có feed dài,
  **When** mở feed/Home, Reels, TTS và CommuteCast,
  **Then** các danh sách/bộ chọn không âm thầm rỗng vì `CursorWindow`; logcat không có `SQLiteBlobTooBigException` phát ra từ hai query được sửa.

## Thiết kế đã duyệt

- Thay bulk `SELECT *` bằng lightweight projection (`id`, `date`, `title`, `author`, `shortDescription`, `img`, `link`, `feedId`, `accountId`, cờ đọc/đánh dấu, metadata và feed relation cần thiết). Không có `rawDescription`/`fullContent`.
- Thêm body lookup theo `accountId` + danh sách ID, trả nguyên body; chunk ID list theo hằng số named dưới bind limit SQLite. Không cắt nội dung.
- Home clustering, semantic search/indexing, Second Brain dùng metadata (`title` + `shortDescription`). Reels tải body cho page candidates hữu hạn. TTS/CommuteCast/Daily Edition/Digest/Offline Precache chỉ tải body theo đúng candidates sẽ đọc hoặc tiêu thụ.
- Không đổi schema; không sửa dữ liệu DB bằng SQL thủ công; không đưa mọi bài trở lại full-body query làm fallback.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```text
Bạn đang thực hiện task FIX-17 "Tránh CursorWindow overflow khi đọc danh sách bài dài" trong repo RSS Cat Hub (com.mckimquyen.reader). Đọc CLAUDE.md và docs/superpowers/specs/2026-10-21-fix-17-article-list-cursorwindow-design.md cùng Acceptance Criteria trước khi sửa.

1. Reproduce bằng Room test trước: tạo body lớn hơn 100 KB và nhiều hàng lớn; chạy hai DAO bulk query để xác nhận regression. Đảm bảo fail vì SQLiteBlobTooBigException trước khi implement.
2. Đưa projection metadata nhẹ vào DAO, thêm account-scoped body lookup theo IDs với chunk giới hạn rõ ràng; không truncate body.
3. Chuyển mọi caller trong Location: Home clustering/semantic/Second Brain dùng metadata; Reels lấy full body cho bounded candidates; ReadNews/CommuteCast/Daily Edition/Digest/OfflinePrecache lấy body cho subset thực sự cần. Giữ order, account scope, text precedence, title/feed name và hành vi người dùng.
4. Chạy focused DAO/caller tests; sau đó full `./gradlew testDevDebugUnitTest assembleDevDebug compileDevDebugAndroidTestKotlin`.
5. Chạy smoke trên thiết bị đã được owner chỉ định; không tự đổi device. Đọc foreground và screenshot trước mỗi tap; dừng nếu full-screen ad che UI. Không sửa DB thiết bị bằng SQL.
6. Review `git diff`, cập nhật completion report và `doc/feature.md`, xoá task todo chỉ sau khi acceptance criteria/tests/smoke xác thực xong. Commit theo convention; không push nếu owner chưa yêu cầu.

Không sửa ad/IAP/money. Không dùng `SELECT *` hoặc bulk full-body fallback.
```

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)

Chỉ kết thúc khi đạt đủ:
1. DAO Room regression test chứng minh large-body bulk lists pass sau fix, và test body lookup chứng minh nội dung đầy đủ + account scope + empty/unknown/large ID handling.
2. Consumer tests chứng minh Reels/TTS/CommuteCast và các caller khác giữ đúng hành vi/dữ liệu cần.
3. `testDevDebugUnitTest`, `assembleDevDebug`, `compileDevDebugAndroidTestKotlin` pass.
4. Smoke test trên thiết bị được owner chỉ định xác nhận feed dài không rỗng và không có `SQLiteBlobTooBigException` từ hai query này; ghi đúng bằng chứng.
5. Báo cáo DONE, `doc/feature.md` cập nhật, todo chuyển khỏi `todo/`, commit local. Không push nếu chưa được yêu cầu.
