# FRONT-02l (Test máy thật: `FeedItem` nguyên bản hiện đúng dòng nguồn Bluesky/Nostr trên Pixel)

**Date:** 2026-11-16  
**Branch:** `dev` (không push)  
**Commit test:** `1b22cade`  
**Scope:** chỉ thêm **một** tệp androidTest, **không đổi mã production**, manifest, chuỗi, Room (v22), DataStore, `app/build.gradle`; không dependency mới (không `hilt-android-testing`). Đây là test, không phải tính năng người dùng.  
**Spec / plan:** `docs/superpowers/specs/2026-11-16-front-02l-real-feed-item-device-test-design.md`, `docs/superpowers/plans/2026-11-16-front-02l-real-feed-item-device-test.md`.

## Điều test chứng minh
Trên **Pixel 7 Pro (`2B051FDH3006MU`, Android 17)**, trong một `ComponentActivity` thường (không `MainActivity`, không quảng cáo, không Hilt, không tọa độ), **`FeedItem` nguyên bản** của app (không composable thử, khác FRONT-02j):
1. Nguồn Bluesky hiện tên `Bluesky · Alice A`; nguồn Nostr hiện `Nostr · Probe`.
2. Tên dài (đủ 40 điểm mã) bị cắt một dòng, tên ngắn không bị cắt.
3. Icon từ máy chủ giả được tải và vẽ thật (điểm giữa ô icon chụp từ màn hình là đỏ); không có icon thì **không** hỏi máy chủ ảnh.
4. Nguồn không icon hiện chữ cái đầu.
5. Huy hiệu số chưa đọc hiện `7`, biến mất khi `0` hoặc `null`.
6. Bấm vào dòng gọi `onClick` đúng một lần; đặt `isExpanded = false` thì dòng biến mất khỏi cây.

**Cách dựng không cần Hilt, không đổi mã production:** `FeedItem` gọi `hiltViewModel()` ở tham số mặc định. Đọc mã nguồn thật của `lifecycle-viewmodel(-compose)` **2.9.2** và bytecode `hilt-navigation-compose 1.0.0`: `hiltViewModel()` lấy `LocalViewModelStoreOwner`; owner không phải `NavBackStackEntry` thì factory là `null` và dùng `defaultViewModelProviderFactory` của chính owner. Test cung cấp `TestOwner` (ViewModelStore riêng, factory tạo `FeedOptionViewModel` thật từ `RssSv` và `ioDispatcher` của `RApp`). `@After` gọi `viewModelStore.clear()`.

## Kết quả đo thật
- **Lần chạy đầu: 7/7 xanh.** **20/20 lượt xanh**, mỗi lượt khoảng 7 giây. Không chập chờn.
- **Mutation trên máy: 5/5 bị bắt**, đúng test dự kiến, mã khôi phục giống hệt (băm trùng, `git status` sạch): huy hiệu tắt → test huy hiệu; `maxLines` không giới hạn → test cắt tên; bỏ `onClick()` → test bấm; luôn hiện (`|| true`) → test bấm/ẩn; `FeedIcon` bỏ tải ảnh → test icon. Hai mẫu `m1` và `m4` được viết dạng `&& false` và `|| true` từ đầu để chắc chắn biên dịch (bài học 02j).
- **Dữ liệu máy:** băm DataStore trước và sau **giống hệt** (`ed9f6887…`) ở mọi vòng; khôi phục byte-exact 6 tệp mỗi lần, kể cả sau batch đột biến; app gốc được build lại và cài lại sau đột biến. `FeedOptionViewModel` thật **chỉ đọc** danh sách nhóm từ Room thật; test không ghi Room và không bấm giữ.
- JVM full suite `--rerun-tasks`: **3.720** test, 0 lỗi, 0 bỏ qua (không đổi, vì chỉ thêm androidTest). `assembleDevDebug assembleDevDebugAndroidTest` thành công. Lint không quét `androidTest`.

## Điều chỉnh khi thực thi
- Spec ghi lifecycle 2.6.2 (theo `build.gradle`), nhưng cây phụ thuộc thật của APK là **2.9.2**; chữ ký (`defaultViewModelProviderFactory` bắt buộc, `defaultViewModelCreationExtras` có mặc định) lấy theo 2.9.2.
- Cây ngữ nghĩa của `FeedItem` bị `combinedClickable` gộp nên mọi truy vấn dùng `useUnmergedTree = true`.
- Công cụ riêng `px_l_*` nằm ngoài kho; công cụ Tecno không được chạy. `user_rotation` Pixel vẫn là **0**.

## Những gì CHƯA kiểm (nói thẳng)
- **Danh sách nguồn thật** (`FeedsPage`: nhiều nhóm, cuộn, `GroupItem`), chỉ một `FeedItem` đơn lẻ.
- **Bấm giữ và ngăn kéo `FeedOptionDrawer`** (cần thêm môi trường, có ghi dữ liệu khi người dùng chỉnh).
- Dark mode, chế độ e-ink, chữ lớn, nhiều ngôn ngữ.
- Icon **https** của Bluesky trên máy (máy chủ giả là http; quy tắc đó chỉ có test JVM); icon lỗi tải; mạng thật.
- Một máy (Pixel 7 Pro), không so với Tecno hay máy khác.
