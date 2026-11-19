# FRONT-02ac (Nostr: link https trong bài bấm được ở trang đọc)

**Date:** 2026-11-19
**Branch:** `dev` (không push)
**Scope:** mã production đổi **đúng một tệp**: `NostrFeedMapper.kt` (hàm mới `linkSpans`, hằng `MAX_LINK_URL_LENGTH = 2048`, `escapeToHtml` bọc link thành thẻ `a`, hàm thoát cũ đổi tên `escapePlain`, nội dung không đổi). Không đổi trang đọc, `openURL`, giao diện, chuỗi, Room (v22), DataStore, manifest, `app/build.gradle`; không dependency mới; không đổi REQ/relay. **Chỉ JVM**, không chạm thiết bị nào.
**Spec / plan:** `docs/superpowers/specs/2026-11-19-front-02ac-nostr-tappable-links-design.md`, `docs/superpowers/plans/2026-11-19-front-02ac-nostr-tappable-links.md`.

## Việc đã làm
Trước đây link trong bài Nostr chỉ là chữ thường. Nay link `https` trong chữ bài thành thẻ `<a href>` trong `rawDescription`; trang đọc đã sẵn cách vẽ link bấm được và mở bằng `openURL` (tôn trọng cài đặt trình duyệt, cắt tham số theo dõi theo cài đặt có sẵn). **Không tải gì khi mở bài** (kể cả ảnh), nên không lộ IP khi chỉ đọc. Luật link: bắt đầu bằng `https://` (không phân biệt hoa thường), chạy tới khoảng trắng, cắt dấu câu cuối (`. , ; : ! ? ) ] } ' "`), phải còn ít nhất một ký tự sau `https://`, tối đa 2048 ký tự (dài hơn thì vẫn là chữ). Địa chỉ trong `href` và chữ hiển thị được thoát bằng đúng hàm thoát cũ, nên chữ bài không thể thoát khỏi thuộc tính hay chèn thẻ.

## Kiểm chứng (đo thật)
- **TDD:** 11 test mới (`NostrTappableLinksTest`) đỏ vì lỗi biên dịch (chưa có `linkSpans`), rồi xanh sau khi thêm mã; các lớp Nostr, sync, `SubscribeViewModelNostr*` xanh. Test dùng Jsoup đọc `abs:href` đúng như trang đọc.
- **Mutation JVM 9/9 khớp dự đoán ngay lần đầu** (`/tmp/aac_check.py` báo `ALL-MATCH`; mã khôi phục giống hệt `cmp`; `git diff` production rỗng): l1 nhận `http`; l2 không cắt dấu câu; l3 bỏ giới hạn độ dài; l4 không thoát `href`; l5 không bọc thẻ; l6 phân biệt hoa thường; l7 tiền tố `https:/`; l8 bỏ `)` khỏi dấu câu; l9 giới hạn `>=`.
- JVM `testDevDebugUnitTest --rerun-tasks assembleDevDebug assembleDevDebugAndroidTest`: **3.885 test, 0 lỗi, 0 bỏ qua** (3.874 + 11), BUILD SUCCESSFUL.
- Diff production đúng một tệp; test thêm đúng một tệp mới.

## Giới hạn (nói thẳng)
- **Link do tác giả bài chọn**, có thể là trang lừa đảo hoặc theo dõi; người đọc **tự chịu khi bấm**. App không tải gì khi mở bài.
- Link kết thúc bằng `)` (ví dụ Wikipedia) bị cắt mất `)`.
- Link `http`, `javascript:`, `data:`, `mailto:`, `nostr:` vẫn là chữ.
- **Chỉ bài mới** có link bấm được; bài đã lưu giữ `rawDescription` cũ (so theo link khi đồng bộ).
- `xhttps://…` (dính chữ phía trước) vẫn tạo link ở phần `https://…`.
- Không có ảnh tự hiện ở trang đọc (đúng lựa chọn của bạn); xem ảnh phải bấm link.
- **Chưa kiểm** bấm link trên máy thật hay mở trình duyệt; chỉ JVM.
- Task gốc FRONT-02 vẫn ở `doc/task/todo/`: còn trả lời bài người khác, đăng nhập, đăng bài.
