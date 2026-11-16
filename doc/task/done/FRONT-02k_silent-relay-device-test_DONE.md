# FRONT-02k (Test máy thật: relay Nostr nhận yêu cầu rồi im lặng, đồng bộ nền báo lỗi gọn rồi hồi phục)

**Date:** 2026-11-16  
**Branch:** `dev` (không push)  
**Commit test:** `4d8ffaf1`  
**Scope:** chỉ thêm test androidTest (2 test vào `BackgroundSyncDeviceTest` của FRONT-02h, nay thành 10), **không đổi mã production**, manifest, chuỗi, Room (v22), DataStore, `app/build.gradle`; không dependency mới. Đây là test, không phải tính năng người dùng.  
**Spec / plan:** `docs/superpowers/specs/2026-11-16-front-02k-silent-relay-device-test-design.md`, `docs/superpowers/plans/2026-11-16-front-02k-silent-relay-device-test.md`.

## Điều test chứng minh
Trên **Pixel 7 Pro (`2B051FDH3006MU`, Android 17)**, với **Room thật** và máy chủ giả trên `127.0.0.1`:
1. **Relay nhận yêu cầu rồi im lặng** (`holdAnswerUntil`): relay đã nhận đúng **1** REQ (im lặng thật, không phải lỗi kết nối); ba bước đồng bộ kết thúc **trong hạn** (không sớm hơn nửa hạn 5 giây, không muộn hơn gấp ba), báo lỗi (`outcome == null`), **không ghi bài**, **tên nguồn không đổi**.
2. **Relay trả lời lại:** lần đồng bộ kế tiếp ghi **đúng 1 bài** (link `https://njump.me/note1…`), tên nguồn không đổi: lần im lặng trước **không** để lại trạng thái kẹt.
Thêm quy tắc `Timeout` 60 giây cho cả lớp: một lần đồng bộ treo không được làm treo cả lượt `am instrument` trên máy dùng chung.

## Kết quả đo thật
- **Lần chạy đầu: 10/10 xanh** (8 test cũ của 02h chạy lại đủ xanh với `Timeout`). **20/20 lượt xanh**, mỗi lượt khoảng 15 giây (ghi lại 13,7 đến 15,6 giây). Không chập chờn.
- **Mutation trên máy (batch bọc sao lưu và khôi phục Room):** 3/3 bị bắt, mã khôi phục giống hệt (băm trùng, `git status` sạch):
  - `m1` bỏ `throw` khi relay không trả bài (`LocalRssSv.queryNostrArticles`) → **cả hai** test mới đỏ ở `assertNull(outcome)` (đúng dự đoán của plan: kết quả rỗng thành `FeedWithArticle` rỗng, không phải `null`).
  - `m2` hạn chờ nhân 4 (`NostrRelayClient`) → chỉ test 1 đỏ (cận trên thời gian); test 2 vẫn xanh (không có cận thời gian), đúng như dự kiến.
  - `m3` hạn chờ 1 ms → 4 test đỏ: hai test mới **và hai test Nostr cũ** (`nostrSource_writesItsNote…`, `aSecondSync…`). Đây là mutant không riêng, ghi rõ.
- **Dữ liệu máy:** băm DataStore trước và sau **giống hệt** (`ed9f6887…`) ở cả vòng 20 lượt, batch đột biến và lượt xác nhận cuối; khôi phục byte-exact 6 tệp mỗi lần, kể cả sau batch; app gốc được build lại và cài lại sau đột biến.
- JVM full suite `--rerun-tasks`: **3.720** test, 0 lỗi, 0 bỏ qua (không đổi, vì chỉ thêm androidTest). `assembleDevDebug assembleDevDebugAndroidTest` thành công. Lint không quét `androidTest`.

## Điều chỉnh khi thực thi
- Rig `BackgroundSyncRig`: `private companion object` thành `companion object` để test thấy `SOCIAL_TIMEOUT_MS`; hai hằng số còn lại giữ `private`. Chỉ androidTest.
- Test có **cận dưới** thời gian (≥ nửa hạn) cùng `requestsReceived() == 1`, để một lỗi kết nối tức thì không bị tính là "im lặng".
- Công cụ riêng `px_k_*` nằm ngoài kho; công cụ Tecno không được chạy.
- `user_rotation` của Pixel vẫn là **0**; test này không xoay màn hình.

## Những gì CHƯA kiểm (nói thẳng)
- **Mutant treo vĩnh viễn** (bỏ `withTimeout`) **không chạy trên máy** vì rủi ro treo máy dùng chung; chỉ có bằng chứng JVM (`NostrRelayClientTest`, `NostrRelayNetworkTest`).
- Không chứng minh con số **8 giây** của sản phẩm (test dùng 5 giây của rig).
- Không kiểm relay treo lúc bắt tay, hai relay (cái đầu im, cái sau tốt), relay trả lời một nửa rồi im: chỉ có test JVM.
- Không mạng thật, không `sync()`/`SyncWorker.doWork`, không hiển thị trên màn hình.
- Một máy (Pixel 7 Pro, Android 17).
