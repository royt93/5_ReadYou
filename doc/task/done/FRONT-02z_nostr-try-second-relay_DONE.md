# FRONT-02z (Nostr: thử relay hai khi relay đầu không có bài nào hiện được)

**Date:** 2026-11-19
**Branch:** `dev` (không push)
**Scope:** mã production đổi **đúng hai tệp**: `NostrFeedMapper.kt` (hàm mới `visibleNotes`, `toArticles` dùng nó) và `NostrRelayClient.kt` (điều kiện dừng của `fetch`). Không đổi giao diện, chuỗi, Room (v22), DataStore, manifest, `app/build.gradle`, `LocalRssSv`, `SubscribeViewModel`, `NostrEvent`, `NostrFetchResult`; không dependency mới. **Không đổi mạng**: REQ giữ nguyên, tối đa 2 relay, mỗi relay tối đa 8 giây. **Chỉ JVM**, không chạm thiết bị nào.
**Spec / plan:** `docs/superpowers/specs/2026-11-19-front-02z-nostr-try-second-relay-design.md`, `docs/superpowers/plans/2026-11-19-front-02z-nostr-try-second-relay.md`.

## Nguyên nhân
Từ FRONT-02y, `fetch` trả cả trả lời người khác. Nhưng `fetch` vẫn dừng ở relay đầu mà `result.notes.isNotEmpty()`. Nếu relay đầu chỉ có trả lời người khác (hoặc bài trống) thì không bài nào hiện được, mà relay hai không bao giờ được thử: xem trước báo lỗi và đồng bộ ném `IOException`, dù relay hai có bài hiện được.

## Việc đã làm
- Một chỗ duy nhất cho luật "bài hiện được": `NostrFeedMapper.visibleNotes(events)` (qua bộ lọc chuỗi của chính tác giả, có `noteId` hợp lệ, tiêu đề không rỗng).
- `toArticles` dùng `visibleNotes` **trước** khi sắp xếp và cắt 20.
- `fetch` dừng ở relay đầu mà `visibleNotes(result.notes)` không rỗng, trả `result` nguyên (chưa lọc). Cả hai relay không có bài hiện được thì trả rỗng kèm tên hồ sơ đầu tiên, nên xem trước vẫn báo lỗi như hiện nay.
- **Thay đổi hành vi nhỏ, có chủ ý, bạn đã duyệt:** bài trống (toàn khoảng trắng) không còn chiếm chỗ trong 20 bài.

## Kiểm chứng (đo thật)
- **TDD:** Task 1: ba test mapper (V1 đến V3) đỏ vì lỗi biên dịch, rồi xanh sau khi thêm `visibleNotes`. Task 2: bảy test relay (A1 đến A7); trước khi sửa đỏ đúng sáu (A1, A3, A4, A5, A6, A7), A2 xanh sẵn (lưới an toàn cho "dừng ở relay đầu"), sau khi sửa xanh hết.
- **Mutation JVM 7/7 khớp sau khi sửa bảng dự đoán** (`/tmp/z_check.py` báo `ALL-MATCH`; mã hai tệp khôi phục giống hệt, `cmp`; `git diff` main rỗng): z1 quay lại `isNotEmpty()` (A1, A3, A4, A5, A6, A7); z2 luôn hỏi cả hai relay (chỉ A2); z3 gộp hai relay; z4 bỏ tên hồ sơ đầu; z5 bỏ bộ lọc chuỗi; z6 bỏ lọc bài trống; z7 `toArticles` không dùng `visibleNotes`.
- **Ba dự đoán sai, đã đọc lý do (không phải test yếu, không đột biến nào sống sót):** z3 làm đỏ thêm A2, A4, A5 và `NostrRelayProfileTest.fallsBackToTheSecondRelay_whenFirstHasOnlyAProfile_andPrefersSecondName` (gộp làm lộ cả bài lẫn tên sai chỗ); z4 làm đỏ thêm sáu test hồ sơ cũ (relay chỉ có hồ sơ); z7 làm đỏ thêm V1 và `repliesAndBlankNotesAreSkipped` (vì `toArticles` không còn tự lọc bài trống). Bảng trong plan đã sửa theo số đo.
- JVM `testDevDebugUnitTest --rerun-tasks assembleDevDebug assembleDevDebugAndroidTest`: **3.862 test, 0 lỗi, 0 bỏ qua** (3.852 + 3 test mapper + 7 test relay), BUILD SUCCESSFUL.
- Diff production đúng hai tệp; test đổi đúng hai tệp (`NostrFeedMapperThreadTest`, `NostrRelayClientTest`).

## Giới hạn (nói thẳng)
- **Không gộp hai relay:** chuỗi nằm rải hai relay vẫn có thể cụt (bạn chọn không gộp).
- **Ca hiếm có thể tốn tới 16 giây** (hai relay, mỗi relay tối đa 8 giây) cho xem trước.
- **Cả hai relay chỉ có trả lời người khác** (hoặc bài trống) thì vẫn báo lỗi như cũ.
- Chuỗi dài hơn 50 sự kiện gần nhất vẫn cụt (REQ không đổi).
- `visibleNotes` chạy thêm một lần cho mỗi relay trên tối đa 50 sự kiện; chi phí không đáng kể.
- **Chưa kiểm** relay thật hay máy thật; chỉ JVM với relay giả.
- Task gốc FRONT-02 vẫn ở `doc/task/todo/`: còn ảnh Nostr, trả lời bài người khác, đăng nhập, đăng bài.
