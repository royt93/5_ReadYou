# FRONT-02g (Nguồn Bluesky đọc bằng API công khai riêng)

**Date:** 2026-11-16  
**Branch:** `dev` (không push)  
**Scope:** dán `@handle` hoặc link `bsky.app/profile/…` vào ô **Thêm nguồn** thì app đọc **API công khai của Bluesky** (tên hiển thị, ảnh đại diện làm icon nguồn, tối đa 20 bài chính chủ) thay cho RSS cũ (khoảng 7 bài, chỉ chữ). API lỗi, quá hạn hoặc không có bài dùng được thì **quay về RSS cũ** trên đúng địa chỉ trước đây. Chỉ đọc, không đăng nhập, không lưu khóa. Room **giữ v22**, không chuỗi, không dependency, không manifest.  
**Spec / plan:** `docs/superpowers/specs/2026-11-16-front-02g-bluesky-api-design.md`, `docs/superpowers/plans/2026-11-16-front-02g-bluesky-api.md`.  
**Trạng thái task gốc:** FRONT-02 vẫn nằm trong `doc/task/todo/`.

## Nói thẳng về phạm vi

- **Tên hiển thị và ảnh đại diện do chủ tài khoản tự đặt, không xác minh.** Có thể giả người nổi tiếng. Tiền tố `Bluesky · ` luôn được giữ.
- **Chỉ bài chính chủ** (không đăng lại, không trả lời). Đo trên API thật: bỏ đăng lại làm số bài ít hơn nhiều so với 50 mục trả về (tài khoản mẫu ra 12 bài chính chủ trong 50 mục).
- **Không tải gì trong bài.** Ảnh, video, trích dẫn, link ngoài chỉ thành **một dòng chữ**; không tải ảnh, không mở link, không đọc `fullsize/thumb/playlist`. Toàn văn không được tải (`parseFullContent` trả rỗng).
- **Đồng bộ nền không đổi tên hay icon** của nguồn đã lưu (người dùng có thể đã đổi tên tay). Tên chỉ đặt lúc xem trước.
- **Nguồn RSS cũ không tự chuyển sang API.** Muốn đổi phải bỏ và đăng ký lại. Nguồn cũ (RSS) và nguồn mới (API) của cùng tài khoản có địa chỉ khác nhau nên có thể đăng ký trùng; chấp nhận.
- **Chỉ tài khoản Local**, như Nostr. Tài khoản khác đi luồng cũ.
- **"Không tìm thấy tài khoản" (HTTP 400, thông điệp `Profile not found`) là một lỗi**, không quay về RSS và không viết lại ô; mọi mã lỗi khác (429, 500, mạng, quá hạn, JSON hỏng) là lỗi mạng và quay về RSS.
- Bluesky có thể giới hạn tốc độ hoặc đổi `public.api.bsky.app`; khi đó nguồn mới quay về RSS (còn hoạt động). Mỗi lần xem trước tốn **hai** yêu cầu mạng (RSS cũ: một).
- Không gửi gì ngoài `actor`, `limit`, `filter`; log không ghi handle, tên, nội dung hay địa chỉ ảnh.

## Đã làm (`infrastructure/social/bluesky/`)

- `BlueskyFeedMapper` (hàm thuần): địa chỉ `bsky:<handle|did>` (handle chữ thường, DID giữ nguyên), tên `Bluesky · <tên>` (làm sạch như tên hồ sơ Nostr, tối đa 40 điểm mã), chọn bài chính chủ (loại `reason`, `reply`, tác giả lạ, trùng), thoát HTML, ngày kẹp về lúc đồng bộ, icon chỉ nhận HTTPS tới tên miền Bluesky, `isActor` (một nơi quyết định "handle hay DID"), `actorFromRssUrl`.
- `BlueskyApiClient`: hai GET (`getProfile`, rồi `getAuthorFeed` theo **DID**, không theo handle gõ tay), 8 giây, thân tối đa 512 KiB (đọc từng khối, **không** phân tích phần bị cắt), **không theo chuyển hướng**, hủy là hủy cuộc gọi HTTP thật, `NotFound` tách khỏi `Failed`.
- `Feed.BLUESKY_SOURCE_TYPE`; nhánh trong `LocalRssSv.queryFeedArticles`; bỏ qua BLUESKY ở `RssHelper` (toàn văn, favicon) và `OfflinePrecacheService`; `subscribe()` ép `isFullContent=false`.
- `SubscribeViewModel.search()`: nhánh API sau resolver, chỉ Local, chặn trùng, hiện "Đang tìm", `NotFound` báo lỗi, mọi kết quả khác rơi về RSS **không bằng `when` toàn diện** (mock `relaxed` trả đối tượng giả, không được làm sập).

## Kiểm chứng (JVM)

- **Test mới: 81** (`BlueskyFeedMapperTest` 29, `BlueskyApiClientTest` 25, `SubscribeViewModelBlueskyTest` 15, `LocalRssSvBlueskySyncTest` 9, `BlueskyNoWebFetchTest` 3). Full suite `--rerun-tasks`: **3.708 test, 0 lỗi, 0 bỏ qua** (480 lớp; trước đó 3.627; 3.627 + 81 = 3.708). Số 3.705 từng được ghi ở đây là số đo **trước** commit gộp quy tắc actor (`b9667e0a`, thêm 3 test: 3.627 + 78); đã đo lại 2026-11-16 ở FRONT-02h.
- Lint: 0 lỗi, **2.465 cảnh báo** (bằng cũ), **0 mục trong `bluesky/`**. `assembleDevDebug assembleDevDebugAndroidTest` thành công. `app/schemas`, `app/src/main/res`, manifest, `app/build.gradle` **không đổi**.
- **Mutation** (backup + `cmp`, mã luôn khôi phục giống hệt): Task 1: 17 đột biến, sau đó chạy lại 2 đột biến sống sót; Task 2: 17, sau đó chạy lại 15 (hai guard thừa đã gỡ nên không chạy lại); Task 3: 6; Task 4: 13, sau đó chạy lại 1; gộp quy tắc actor: 6, sau đó chạy lại 1. Mỗi batch có thêm một lượt `base` xanh. **Cuối cùng mọi đột biến đều bị test bắt.** Đột biến sống sót đã làm lộ test yếu, đã thêm test rồi chạy lại: bỏ loại trùng `rkey` và bỏ lọc `reason` (Task 1); giới hạn đọc, hủy cuộc gọi HTTP thật, kiểm `did:`, tên, avatar, feed báo "Profile not found" (Task 2); địa chỉ không tiền tố cho `actorFromRssUrl` (Task 4); DID của hồ sơ mang ký tự truy vấn (gộp regex). Hai guard thừa đã gỡ: `isRedirect` và `contentLength` (trùng với `followRedirects(false)` và `readLimited`).
- **Sai lầm của chính tôi, đã sửa:** `call.execute()` chặn luồng, không hủy được lúc hết thời gian (test treo 10 giây); đổi sang `enqueue` + `suspendCancellableCoroutine`. `ACTOR_REGEX` cũ nhận `..`; siết ký tự đầu. Plan thiếu import `toListItem` và đếm sai số tệp test (11 chứ không phải 12 tệp); sửa theo tệp thật.

## Kiểm chứng trên máy (Tecno KJ7, Android 14, 2026-11-16)

Helper `androidTest` tạm (đã xóa, **không commit**) gọi **API thật** qua `OkHttpClient` của app (DoH, interceptor), không `MainActivity`, không quảng cáo, không bấm, chỉ ghi số lượng. Cổng chỉ đọc trước mỗi lượt (launcher `com.transsion.hilauncher` trên cùng, mở khóa, không instrumentation, xoay `1 0`, 1080x2436). Sao lưu và khôi phục byte-exact **6 tệp**, băm DataStore `9ca1c202…` trùng, đã gỡ `com.mckimquyen.reader.test`, xoay vẫn `1 0`.

- Tài khoản có thật: `found`, 50 mục trả về, **12 bài chính chủ**, cả 12 có một dòng cho nội dung đính kèm, có tên hiển thị (7 điểm mã), có ảnh đại diện, 1,96 giây.
- Tài khoản không tồn tại: `notFound` trong 0,30 giây (không quay về RSS).
- Lần chạy đầu của helper đỏ vì **lỗi của helper** (JUnit đòi hàm `void`), không phải mã Bluesky; sửa rồi chạy lại 2/2.
- Pixel **không chạm** theo lệnh của owner.

## Chưa kiểm (nói thẳng)

- **Hiển thị thật** của tên và icon trong hộp thoại và danh sách trên màn hình; tải ảnh đại diện thật (chỉ lưu địa chỉ).
- Giới hạn tốc độ của Bluesky khi đồng bộ nhiều nguồn; API đổi hình dạng sau này.
- Đồng bộ nền thật của một nguồn `BLUESKY` trên máy (chỉ có test JVM với dịch vụ giả).
- Hiển thị bài có `embed` lạ; TalkBack; ngôn ngữ ngoài EN/VI.
- Hai nguồn trùng (RSS cũ + API mới) không được cảnh báo.

## Giới hạn

- Hai yêu cầu mạng cho mỗi xem trước.
- Tên và ảnh tự khai, không xác minh; chỉ đặt một lần lúc xem trước.
- Nguồn cũ không tự chuyển sang API.
- Quay về RSS khi API lỗi có thể che việc API hỏng lâu dài (không có chỉ báo cho người dùng).

## Commits trên `dev`

`ada2dbcc` spec · `e24fbb4a` plan · `0e17150e` ánh xạ bài · `b29b908f` API client · `79337367` loại nguồn và đồng bộ nền · `fedfc82c` nhánh `search()` và quay về RSS · `b9667e0a` gộp quy tắc actor.
