# FRONT-02ad (Nostr: hiện mọi trả lời, kèm link tới bài gốc)

**Date:** 2026-11-19
**Branch:** `dev` (không push)
**Scope:** mã production đổi **đúng ba tệp**: `NostrFeedMapper.kt` (bỏ `keepOriginalsAndOwnThreads`, thêm `replyTargetUrl`, `REPLY_MARK`, dòng link, KDoc), `NostrEvent.kt` (xóa `isReply`; `repliedToId` giữ), `NostrFetchResult.kt` (chỉ KDoc). Không đổi `NostrRelayClient`, `LocalRssSv`, `SubscribeViewModel`, giao diện, chuỗi, Room (v22), DataStore, manifest, `app/build.gradle`; không dependency mới; không đổi REQ/relay. **Chỉ JVM**, không chạm thiết bị nào.
**Spec / plan:** `docs/superpowers/specs/2026-11-19-front-02ad-nostr-show-all-replies-design.md`, `docs/superpowers/plans/2026-11-19-front-02ad-nostr-show-all-replies.md`.

## Việc đã làm
Từ FRONT-02x, mọi trả lời bài người khác bị bỏ. Theo lựa chọn của bạn, nay **mọi trả lời đều hiện** (cả trả lời chính mình lẫn người khác), tranh chỗ ngang nhau với bài thường trong 20 bài (mới nhất trước). Mỗi trả lời có **dòng cuối `↩ <link njump>`** trỏ tới bài được trả lời (đọc theo NIP-10: thẻ `reply`, rồi `root`, rồi thẻ cuối kiểu cũ). Dòng này là ký hiệu, không có chuỗi dịch mới. Link do app **tự dựng** từ id 64 chữ hex thường đã kiểm (`https://njump.me/note1…`), **không** lấy từ chữ bài; thoát như mọi chữ; trang đọc (FRONT-02ac) làm cho nó bấm được. Tiêu đề, `shortDescription` và `Article.link` không đổi (tính từ chữ bài, không từ dòng link). Trả lời mà thẻ `e` hỏng vẫn hiện, không có dòng link. Bài chỉ nhắc tới bài khác (mention) và bài thường không có dòng link. Bài trống vẫn bị bỏ.

## Kiểm chứng (đo thật)
- **TDD:** 10 test mới (`NostrRepliesTest`) đỏ vì lỗi biên dịch, rồi (sau khi thêm mã nhưng còn bộ lọc cũ) **đỏ đúng năm test dự đoán**, rồi xanh 10/10 sau khi bỏ bộ lọc chuỗi.
- **Mutation JVM 9/9 khớp sau khi sửa bảng dự đoán** (`/tmp/ad_check.py` báo `ALL-MATCH`; mã khôi phục giống hệt `cmp`; `git diff` production rỗng): m1 lại bỏ trả lời; m2 bỏ dòng link; m3 link dùng id của chính trả lời; m4 bỏ `↩`; m5 mọi bài đều có dòng link; m6 bài có thẻ `e` mà không tìm được bài gốc bị bỏ; m7 bỏ `<br>`; m8 bỏ khoảng trắng sau `↩`; m9 hiện lại bài trống.
- **Một dự đoán sai, đã đọc lý do (không phải test yếu):** m2, m4, m7, m8 làm đỏ **thêm** `LocalRssSvNostrSyncTest.anOwnThread_andAnAnswerToAStranger_areSynced` (test đồng bộ kiểm cả dòng `↩ link`); bảng đã sửa. Ngoài ra m1, m5, m9 làm đỏ thêm nhiều test sẵn có (kiểm kiểu "ít nhất").
- **Phát sinh ngoài plan:** `LocalRssSvNostrSyncTest.anOwnThread_isSyncedWhole` đỏ (còn kỳ vọng "trả lời người lạ bị bỏ"). Đổi tên thành `anOwnThread_andAnAnswerToAStranger_areSynced` và sửa kỳ vọng; không xóa test.
- JVM `testDevDebugUnitTest --rerun-tasks assembleDevDebug assembleDevDebugAndroidTest`: **3.886 test, 0 lỗi, 0 bỏ qua** (3.885 + 11 mới − 10 xóa = 3.886, đúng kỳ vọng), BUILD SUCCESSFUL.

## Test đã xóa (10) và lý do
Xóa vì kiểm đúng luật bị bỏ ("trả lời người lạ bị loại, đi theo chuỗi"):
- `NostrFeedMapperThreadTest`: `aReplyToSomeoneElse_isDropped`, `aReplyToADroppedNote_isDropped`, `aLoopOfReplies_endsAndDropsBoth`, `aNoteReplyingToItself_isDropped`, `aThreadBehindManyAnswersToOthers_isStillShown`, `visibleNotes_dropsRepliesToOthersAndBlankNotes`, `aReplyWhoseTargetIdIsBroken_isDropped`, `aReplyNamingATargetInCapitals_isDropped` (8).
- `NostrEventTest.isReply_isTrueOnlyWithAnETag` (1; `isReply` đã bị xóa).
- `NostrRelayClientTest.fetch_triesTheSecondRelay_whenTheFirstHasOnlyRepliesToOthers` (1; trùng với test bài trống sau khi `REPLY` thành bài hiện được).
Sửa (không xóa): các test `NostrEventThreadTest` bỏ khẳng định `isReply` và đổi tên theo `repliedToId`; `NostrFeedMapperTest.repliesAndBlankNotesAreSkipped` thành `aBlankNote_isSkipped_butAReplyIsShown`; bốn test relay 02z dùng `EMPTY` (bài trống) thay `REPLY`; `visibleNotes_isExactlyWhatToArticlesTurnsIntoArticles` thêm bài trả lời người lạ; thêm `fetch_stopsAtTheFirstRelay_whenItHasOnlyAReply`.

## Giới hạn (nói thẳng)
- **Nguồn Nostr đã đăng ký sẽ nhận thêm bài trả lời chưa đọc ở lần đồng bộ kế tiếp** (tối đa 20 mới nhất; bài đã lưu không đổi vì so theo link).
- **Tác giả hay trả lời người khác thì 20 bài có thể toàn trả lời**, bài thường bị đẩy ra (đúng điều bạn chọn).
- **Mất phòng thủ vòng lặp trả lời của FRONT-02x:** không còn đi theo chuỗi nên không cần; chuỗi tự trả lời của tác giả vẫn hiện vì mọi trả lời đều hiện.
- Link bài gốc có thể trỏ tới bài của người lạ **đã bị xóa** trên njump; app không tải bài gốc, không kiểm chữ ký bài của người lạ.
- Bài đã lưu giữ `rawDescription` cũ: dòng `↩` chỉ có ở bài mới.
- **Chưa kiểm** máy thật hay relay thật; chỉ JVM với relay giả.
- Task gốc FRONT-02 vẫn ở `doc/task/todo/`: còn đăng nhập, đăng bài (ngoài phạm vi).
