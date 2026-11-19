# FRONT-02aa (Nostr: ảnh bìa cho bài có link ảnh)

**Date:** 2026-11-19
**Branch:** `dev` (không push)
**Scope:** mã production đổi **đúng một tệp**: `NostrFeedMapper.kt` (hàm mới `coverImageUrl`, hằng `MAX_COVER_URL_LENGTH = 2048`, `toArticles` điền `Article.img`). Không đổi giao diện, chuỗi, Room (v22), DataStore, manifest, `app/build.gradle`, `OfflinePrecacheService`; không dependency mới; không đổi REQ/relay. **Chỉ JVM**, không chạm thiết bị nào.
**Spec / plan:** `docs/superpowers/specs/2026-11-19-front-02aa-nostr-cover-image-design.md`, `docs/superpowers/plans/2026-11-19-front-02aa-nostr-cover-image.md`.

## Việc đã làm
Bài Nostr có link ảnh trong chữ thì danh sách hiện ảnh bìa như các nguồn RSS (`ArticleItem` chỉ vẽ ảnh khi công tắc ảnh danh sách bật). Luật: link **đầu tiên** trong chữ bài **như tác giả gõ** (không phải HTML đã thoát), chỉ `https`, kết thúc bằng `jpg`, `jpeg`, `png`, `gif`, `webp` (không phân biệt hoa thường), cho phép `?query` hoặc `#fragment`, kết thúc ở khoảng trắng hoặc hết chữ; link dài hơn 2048 ký tự bị bỏ qua (lấy link hợp lệ kế tiếp). Chỉ địa chỉ được lấy, không tải gì ở tầng này.

## Kiểm chứng (đo thật)
- **TDD:** 12 test mới (`NostrCoverImageTest`) đỏ vì lỗi biên dịch (chưa có `coverImageUrl`), rồi xanh sau khi thêm mã; các lớp Nostr, sync, `SubscribeViewModelNostr*` xanh.
- **Mutation JVM 9/9 khớp dự đoán ngay lần đầu** (`/tmp/aa_check.py` báo `ALL-MATCH`; mã khôi phục giống hệt `cmp`; `git diff` production rỗng): c1 nhận `http` (chỉ `anHttpLink_isNotACover`); c2 nhận mọi đuôi (chỉ `aLinkThatIsNotAnImage_isNotACover`); c3 lấy link cuối (chỉ `theFirstImageLink_wins`); c4 bỏ giới hạn độ dài (hai test độ dài); c5 `toArticles` không đặt `img` (ba test); c6 lấy từ HTML đã thoát (hai test `&` và xuống dòng); c7 bỏ điều kiện kết thúc (chỉ `aLinkThatIsNotAnImage_isNotACover`); c8 bỏ không-phân-biệt-hoa-thường (chỉ `everyImageExtension_isRecognised_inAnyCase`); c9 giới hạn `<` thay `<=` (chỉ `aLinkOfExactlyTheLimit…`).
- JVM `testDevDebugUnitTest --rerun-tasks assembleDevDebug assembleDevDebugAndroidTest`: **3.874 test, 0 lỗi, 0 bỏ qua** (3.862 + 12), BUILD SUCCESSFUL.
- Diff production đúng một tệp; test thêm đúng một tệp mới.

## Giới hạn (nói thẳng)
- **Riêng tư:** khi công tắc ảnh danh sách **bật**, mở danh sách làm app tải ảnh từ **máy chủ do tác giả bài chọn**, máy chủ đó thấy địa chỉ IP của người đọc. Công tắc tắt thì không tải gì ở danh sách.
- **Triage và Reels** hiện ảnh khi người dùng đang xem, **không** theo công tắc ảnh danh sách (đúng như mọi nguồn RSS hiện nay).
- **Không tải sẵn offline:** `OfflinePrecacheService` đã bỏ qua nguồn Nostr từ trước nên ảnh Nostr không bị tải nền; không đổi gì ở đó.
- Link không có đuôi ảnh (nhiều CDN) và link dính dấu câu ngay sau đuôi (ví dụ `a.jpg.`) bị bỏ sót; chỉ ảnh đầu tiên; không `http`, không thẻ `imeta` (NIP-92), không video, không ảnh trong trang đọc.
- Ảnh chỉ có ở **bài mới** (bài đã lưu so theo link nên không đổi).
- Regex có thể chậm (bậc hai) với chữ rất dài chứa hàng loạt `https://` nối nhau; mỗi bài tối đa 64 KiB, chấp nhận.
- **Chưa kiểm** máy thật, máy chủ ảnh thật, hay công tắc ảnh trên màn hình; chỉ JVM.
- Task gốc FRONT-02 vẫn ở `doc/task/todo/`: còn trả lời bài người khác, đăng nhập, đăng bài (và ảnh trong trang đọc).
