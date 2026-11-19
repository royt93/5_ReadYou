# FRONT-02ae (test máy thật: bấm link trong bài Nostr đến Reader)

**Date:** 2026-11-19
**Branch:** `dev` (không push)
**Device:** chỉ **Tecno KJ7 `115333744A005844`** (Android 14). Pixel đang nối nhưng không dùng.
**Scope:** chỉ thêm **một tệp test** `app/src/androidTest/.../ui/component/reader/NostrLinkTapDeviceTest.kt` (5 test). **Không đổi mã production**, giao diện, chuỗi, Room (v22), DataStore, manifest, `app/build.gradle`; không dependency mới; không mạng; không mở trình duyệt; không ghi Room/DataStore.
**Spec / plan:** `docs/superpowers/specs/2026-11-19-front-02ae-nostr-link-tap-device-test-design.md`, `docs/superpowers/plans/2026-11-19-front-02ae-nostr-link-tap-device-test.md`.

## Việc đã làm
FRONT-02ac (link bấm được) và FRONT-02ad (dòng `↩ link`) mới chỉ có test JVM. Nay trên máy thật: bài Nostr qua `NostrFeedMapper.toArticles` **thật**, hiện bằng `Reader` **thật** (Jsoup + Compose), rồi chạm vào link. **Vị trí chạm lấy từ bố cục chữ thật** (`SemanticsActions.GetTextLayoutResult`, hộp của ký tự đầu của link), không đoán tọa độ. Năm test: chạm link `https` thì `onLinkClick` nhận đúng link; chạm dòng `↩` của bài trả lời thì nhận link njump của bài gốc; chạm link `http` (chữ thường) thì không gọi; link có dấu chấm cuối thì địa chỉ nhận được không có dấu chấm; hai link thì chạm link thứ hai nhận link thứ hai.

## Kiểm chứng (đo thật)
- **1 lượt thử xanh**, rồi **20 lượt: 20 xanh, 0 đỏ, 0 lượt có `AdActivity`**. Mỗi vòng có cổng `GATE OK`, sao lưu và khôi phục byte-exact (`RESTORE OK`); băm DataStore **trước và sau trùng** (`9ca1c202050c258b4aac9a063e27def350d737fb`); xoay sau mỗi lượt `1 0`, không rò.
- **Đột biến trên máy 4/4 khớp dự đoán ngay lần đầu** (`/tmp/aae_check.py` báo `ALL-MATCH`; mã khôi phục giống hệt `cmp`; `git diff` production rỗng): a1 link thường không bọc thẻ `a` (đỏ đúng bốn test: link, dấu chấm, hai link, và `tappingAnHttpLink` vì nửa sau cần link đầu bấm được); b1 bỏ dòng `↩` (đỏ đúng test dòng `↩`); c1 nhận `http` (đỏ đúng test `http`); d1 dòng `↩` dùng id của chính bài (đỏ đúng test dòng `↩`). Batch đầu và cuối đều xanh. Mô hình tay trước khi chạy đã đoán đúng cả bốn, gồm cả việc a1 làm đỏ test `http`.
- JVM `testDevDebugUnitTest --rerun-tasks assembleDevDebug assembleDevDebugAndroidTest`: **3.886 test, 0 lỗi, 0 bỏ qua** (không đổi, vì chỉ thêm test máy), BUILD SUCCESSFUL.
- Diff `app` đúng một tệp test.

## Điều chỉnh khi thực thi
- Bản nháp đầu của test không biên dịch được (thiếu import `getOrNull`, `click`, và `onNode` là hàm của rule); đã sửa theo mẫu `FeedItemDeviceTest` trước khi chép vào plan.
- Dự đoán đột biến a1 ban đầu thiếu `tappingAnHttpLink_givesNothing`; đã sửa trong plan bằng suy luận (không phải số đo).

## Giới hạn (nói thẳng)
- **Chỉ chứng minh đến `onLinkClick`**: không gọi `openURL`, không mở trình duyệt, không `ReadingPage` thật, không cuộn, không chọn chữ, không E-Ink, không relay thật.
- **Test không thể đỏ trước khi viết mã** (hành vi đã có từ FRONT-02ac/02ad). Bằng chứng test có răng là **bốn đột biến trên máy**.
- Một máy (Tecno); không so với Pixel. Lint không quét `androidTest`.
- Task gốc FRONT-02 vẫn ở `doc/task/todo/`: còn đăng nhập, đăng bài (ngoài phạm vi).
