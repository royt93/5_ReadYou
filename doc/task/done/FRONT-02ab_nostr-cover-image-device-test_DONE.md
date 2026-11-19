# FRONT-02ab (test máy thật: ảnh bìa Nostr theo công tắc ảnh danh sách)

**Date:** 2026-11-19
**Branch:** `dev` (không push)
**Device:** chỉ **Tecno KJ7 `115333744A005844`** (Android 14). Pixel đang nối nhưng không dùng.
**Scope:** chỉ thêm **một tệp test** `app/src/androidTest/.../ui/page/home/flow/NostrCoverImageDeviceTest.kt` (5 test). **Không đổi mã production**, giao diện, chuỗi, Room (v22), DataStore, manifest, `app/build.gradle`; không dependency mới; không ra mạng ngoài; không ghi Room/DataStore.
**Spec / plan:** `docs/superpowers/specs/2026-11-19-front-02ab-nostr-cover-image-device-test-design.md`, `docs/superpowers/plans/2026-11-19-front-02ab-nostr-cover-image-device-test.md`.

## Việc đã làm
FRONT-02aa chỉ có test JVM. Nay trên máy thật: bài Nostr đi qua `NostrFeedMapper.toArticles` **thật** rồi `ArticleItem` **thật**, còn bộ tải ảnh Coil được thay bằng bộ chỉ ghi lại địa chỉ bị hỏi (không mạng, không đĩa). Năm test: công tắc BẬT + link https thì hỏi đúng link đó; công tắc TẮT thì không hỏi gì; không có link thì không hỏi; link `http` thì không hỏi; hai link thì chỉ hỏi link đầu.

## Kiểm chứng (đo thật)
- **1 lượt thử xanh**, rồi **20 lượt: 20 xanh, 0 đỏ, 0 lượt có `AdActivity`**. Mỗi vòng có cổng `GATE OK`, sao lưu và khôi phục byte-exact (`RESTORE OK`); băm DataStore **trước và sau trùng** (`9ca1c202050c258b4aac9a063e27def350d737fb`); xoay sau mỗi lượt `1 0`, không rò.
- **Đột biến trên máy 4/4 khớp dự đoán ngay lần đầu** (`/tmp/aab_check.py` báo `ALL-MATCH`; mã hai tệp production khôi phục giống hệt `cmp`; `git diff` production rỗng): a1 mapper không đặt `img` (đỏ đúng hai test BẬT có link); b1 `ArticleItem` bỏ điều kiện công tắc (đỏ đúng `switchOff…`); c1 mapper nhận `http` (đỏ đúng `anHttpImageLink…`); d1 mapper lấy link cuối (đỏ đúng `onlyTheFirstOfTwoImageLinks…`). Batch đầu và cuối đều xanh (`base`).
- JVM `testDevDebugUnitTest --rerun-tasks assembleDevDebug assembleDevDebugAndroidTest`: **3.874 test, 0 lỗi, 0 bỏ qua** (không đổi, vì chỉ thêm test máy), BUILD SUCCESSFUL.
- Diff `app` đúng một tệp test.

## Điều chỉnh khi thực thi (đã đọc API Coil 2.1.0 bằng `javap`)
- `Coil.reset()` không công khai: test lưu bộ tải thật của app trong `@Before` và trả lại bằng `Coil.setImageLoader` trong `@After`.
- Khẳng định theo **địa chỉ khác nhau** (`distinct()`), không theo số yêu cầu, vì Compose có thể gửi lại cùng yêu cầu.
- `waitUntil` dùng hằng `WAIT_MS = 15_000` (mặc định của Compose chỉ 1 giây); đây là test mới, không phải tăng timeout để cứu lượt đỏ.

## Giới hạn (nói thẳng)
- **Test không thể đỏ trước khi viết mã** (hành vi đã có từ FRONT-02aa). Bằng chứng test có răng là **bốn đột biến trên máy**, không phải bước "đỏ trước".
- **Chỉ chứng minh "có hỏi đúng địa chỉ"**, không chứng minh ảnh **hiện** trên màn hình (ảnh không có nút ngữ nghĩa, `contentDescription` rỗng) và không chứng minh tải qua mạng thật (OkHttp, chứng chỉ, kích thước ảnh).
- Bộ tải ảnh của app bị **thay** trong test; bộ tải thật không được kiểm. Không kiểm danh sách thật (`HomeEntry`), Triage, Reels, relay thật, đổi công tắc từ màn Cài đặt.
- Một máy (Tecno); không so với Pixel. Lint không quét `androidTest`.
- **Riêng tư vẫn như FRONT-02aa:** công tắc ảnh bật thì máy chủ ảnh của tác giả thấy IP người đọc.
- Task gốc FRONT-02 vẫn ở `doc/task/todo/`: còn trả lời bài người khác, ảnh trong trang đọc, đăng nhập, đăng bài.
