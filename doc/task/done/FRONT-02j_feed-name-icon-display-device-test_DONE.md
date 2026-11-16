# FRONT-02j (Test máy thật: tên và icon nguồn Bluesky/Nostr hiện đúng trên màn hình Pixel)

**Date:** 2026-11-16  
**Branch:** `dev` (không push)  
**Commit test:** `25c03a93`  
**Scope:** chỉ thêm test androidTest, **không đổi mã production**, manifest, chuỗi, Room (v22), DataStore, `app/build.gradle`; không dependency mới. Đây là test, không phải tính năng người dùng.  
**Spec / plan:** `docs/superpowers/specs/2026-11-16-front-02j-feed-name-icon-display-device-test-design.md`, `docs/superpowers/plans/2026-11-16-front-02j-feed-name-icon-display-device-test.md`.

## Điều test chứng minh
Trên **Pixel 7 Pro (`2B051FDH3006MU`, Android 17)**, trong một `ComponentActivity` thường (không `MainActivity`, không quảng cáo, không Hilt, không tọa độ), hai thứ thật: `FeedIcon` của app và một `Text` cùng kiểu, `maxLines = 1`, `overflow = Ellipsis` như `FeedItem`. Icon lấy từ máy chủ giả `http://127.0.0.1` qua bộ tải ảnh thật của app (Coil).
1. Tên Bluesky hiện đúng `Bluesky · Alice A` (tên hồ sơ đã làm sạch).
2. Tên Nostr hiện đúng `Nostr · Probe`.
3. Tên dài (đủ 40 điểm mã) **bị cắt** một dòng (`hasVisualOverflow`), tên ngắn **không** bị cắt.
4. Icon từ máy chủ giả được **tải và vẽ thật**: điểm giữa ô icon chụp từ màn hình là đỏ, máy chủ bị hỏi ít nhất một lần; khi chưa có icon thì ô đó **không** đỏ.
5. Nguồn không có icon hiện **chữ cái đầu** và **không** hỏi máy chủ ảnh.

## Kết quả đo thật
- **Lần chạy đầu: 5/5 xanh.** **20/20 lượt xanh**, mỗi lượt khoảng 5 giây. Không chập chờn (khác FRONT-02d).
- **Mutation trên máy: 4/4 bị bắt**, mã khôi phục giống hệt (băm trùng, `git status` sạch): `FeedIcon` bỏ nhánh tải ảnh (`data = null`) → test 4 đỏ; bỏ chữ cái đầu → test 5 đỏ; `maxLines` không giới hạn (nằm trong **tệp test**, kiểm chất lượng test, không kiểm mã production) → test 3 đỏ; luôn vẽ chữ cái → test 4 đỏ.
- **Dữ liệu máy:** băm DataStore trước và sau **giống hệt** (`ed9f6887…`) ở cả vòng 20 lượt, đột biến và lượt xác nhận cuối; khôi phục byte-exact 6 tệp mỗi lần; app gốc được build lại và cài lại sau đột biến. Test không ghi Room, không ghi DataStore.
- JVM full suite `--rerun-tasks`: **3.720** test, 0 lỗi, 0 bỏ qua (không đổi so với 02i, vì chỉ thêm androidTest). `assembleDevDebug assembleDevDebugAndroidTest` thành công. Lint không quét `androidTest`.

## Điều chỉnh khi thực thi
- Mutant `m4` lần đầu (`if (true) {`) **không biên dịch được** (Kotlin báo kiểu `String?`), nên chưa chứng minh gì; đổi thành `if (iconUrl == null || true) {` và chạy lại riêng, bị bắt.
- Pixel lần đầu có app `loyalty.skyjoy.vn.dev` của owner ở foreground: tôi dừng và hỏi; owner tự thoát về launcher, rồi mới chạy tiếp.
- Test cần bỏ một import thừa (`onNode` là hàm của rule, không cần import).
- `user_rotation` của Pixel hiện là **0** (FRONT-02e từng để lại 1): đã đổi, không do tôi sửa. Test này **không xoay** màn hình.
- Công cụ riêng cho Pixel (`px_*`) nằm ngoài kho; công cụ Tecno cũ **không** được chạy.

## Những gì CHƯA kiểm (nói thẳng)
- **Không phải `FeedItem` nguyên bản hay danh sách nguồn thật** (thiếu `hiltViewModel`); chỉ hai composable thật và `Text` cùng kiểu. Bố cục dòng, huy hiệu, bấm giữ, kéo chưa kiểm.
- Dark mode, chế độ e-ink, chữ lớn, nhiều ngôn ngữ chưa kiểm.
- Icon từ máy chủ **http** giả: **không** chứng minh quy tắc "chỉ https của máy chủ ảnh Bluesky" trên máy (quy tắc đó chỉ có test JVM).
- Không mạng thật; không kiểm icon lỗi tải (404 hay hết giờ), hình ảnh hỏng chưa được xem.
- Một máy (Pixel 7 Pro), không so với Tecno hay máy khác.
