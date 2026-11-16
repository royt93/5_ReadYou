# FRONT-02g — Nguồn Bluesky đọc bằng API công khai riêng (thiết kế, chờ owner duyệt spec)

**Ngày:** 2026-11-16 · **Nhánh:** `dev` · **Nền:** FRONT-02 (Bluesky qua RSS), FRONT-02b/02c (Nostr: `nostr:<npub>`, `Feed.NOSTR_SOURCE_TYPE`, `NostrFeedMapper`, `LocalRssSv.queryNostrArticles`)

> Trạng thái: owner đã chốt trong brainstorm (2026-11-16): chữ + tên hiển thị + ảnh đại diện (ảnh đại diện làm icon nguồn); ảnh trong bài **chỉ là link, không tải**; **chỉ bài chính chủ viết** (bỏ đăng lại và trả lời); địa chỉ riêng `bsky:<handle>` với loại nguồn `BLUESKY`, **không đổi Room**. Spec chờ owner duyệt trước khi lập plan. Chưa có plan, chưa có mã.

## Mục tiêu
Hiện nay dán `@handle` hay link `bsky.app/profile/…` được đổi thành RSS `https://bsky.app/profile/<h>/rss` (khoảng 7 bài, chỉ chữ, không tên hiển thị, không ảnh đại diện). Đổi sang API công khai của Bluesky để có nhiều bài hơn, tên hiển thị và ảnh đại diện. Chỉ đọc, không đăng nhập, không lưu khóa.

## Sự thật đã đo (2026-11-16, API thật, chỉ đếm và tên trường)
- `GET https://public.api.bsky.app/xrpc/app.bsky.actor.getProfile?actor=<handle|did>`: HTTP 200 trong 0,37 giây; có `did`, `handle`, `displayName`, `description`, `avatar` (địa chỉ ảnh), `banner`, số đếm. Tài khoản không có: **HTTP 400** `{"error":"InvalidRequest","message":"Profile not found"}` (cả handle lạ lẫn `did:plc:` giả).
- `GET …/app.bsky.feed.getAuthorFeed?actor=…&limit=50&filter=posts_no_replies`: HTTP 200 trong 0,93 giây, khoảng 140 KB cho 30 mục. Mỗi mục có `post` (`uri`, `cid`, `author`, `record.text`, `record.createdAt`, `record.langs`, `embed`, đếm) và có thể có `reason` (đăng lại).
- Trong 50 mục: `bsky.app` có **12** bài chính chủ (không có `reason`), `jay.bsky.team` có **23**. Bỏ đăng lại làm số bài ít hơn nhiều so với con số 50.
- Loại `embed` thấy được: ảnh, video, link ngoài, trích dẫn bài khác, không có.
- `filter=posts_no_replies` đã bỏ trả lời; **đăng lại** vẫn có mặt (`reason`), phải bỏ ở phía app.

## Phạm vi trung thực
- Chữ + tên hiển thị + ảnh đại diện. **Không** tải ảnh, video hay nội dung trích dẫn trong bài; **không** hashtag, danh sách, luồng trả lời, thông báo.
- Không xác minh danh tính: tên hiển thị và ảnh đại diện **do chủ tài khoản tự đặt**, có thể giả người khác.
- Không dependency mới, không Room v23, không DataStore, manifest, chuỗi mới; không Gemini, ad, IAP, VIP, tiền.
- Chỉ tài khoản Local (như Nostr).
- API công khai không cần đăng nhập, nhưng Bluesky có thể giới hạn tốc độ hoặc đổi API; ghi vào giới hạn.

## Thiết kế
**1. Nhận dạng và đường đi.**
- `SocialFeedUrlResolver` giữ cho Mastodon. Với Bluesky (`@tên.miền`, `https://bsky.app/profile/<handle|did>[/rss]`), `SubscribeViewModel.search()` đi nhánh API trước; nếu API **lỗi mạng hay trả không có bài** thì **quay về RSS cũ** (khác Nostr, vì Bluesky có RSS dự phòng). Tài khoản không tồn tại (HTTP 400) thì báo lỗi như hiện nay, không thử RSS.
- Địa chỉ nguồn lưu là `bsky:<handle|did>` (chữ thường cho handle, giữ nguyên DID), `sourceType = Feed.BLUESKY_SOURCE_TYPE = "BLUESKY"`; không đụng cột Room.
- `isFeedExist("bsky:<h>")` chặn đăng ký trùng. Nguồn Bluesky đã đăng ký bằng RSS **không** tự chuyển sang API (muốn đổi phải bỏ và đăng ký lại; ghi vào giới hạn). FRONT-02i kiểm thêm địa chỉ RSS cũ.

**2. Lấy dữ liệu** (`infrastructure/social/bluesky/BlueskyApiClient`, Hilt `@Singleton`, dùng `OkHttpClient` chung của app):
- Hai GET tuần tự: `getProfile` rồi `getAuthorFeed` (`limit=50`, `filter=posts_no_replies`). Chỉ HTTPS tới `public.api.bsky.app`; `actor` chỉ chứa chữ, số, `.`, `-`, `:` (kiểm trước khi gửi); không chuyển hướng ra ngoài host.
- Giới hạn: thời gian chờ mỗi GET, **kích thước phản hồi** (đọc tối đa vài trăm KB, bỏ phần dư), tối đa bài giữ (như Nostr: 20).
- Trả `BlueskyFetchResult(posts, displayName, avatarUrl)`; HTTP 400 `Profile not found` là một kết quả riêng, không phải lỗi mạng.

**3. Ánh xạ** (`BlueskyFeedMapper`, hàm thuần):
- Bài chính chủ = mục **không có `reason`** và `post.author.did` bằng DID của hồ sơ (chống lẫn bài người khác); bỏ bài rỗng.
- `Article`: tiêu đề = 80 điểm mã Unicode đầu của chữ (như `titleFromBody`); nội dung = chữ **đã thoát HTML**; `link` = địa chỉ bài `https://bsky.app/profile/<handle>/post/<rkey>` (suy từ `uri`), `date` = `createdAt`.
- Có `embed` thì ghi **một dòng chữ** nêu loại và, nếu là link ngoài, địa chỉ link đó đã thoát; **không** tải gì.
- Tên nguồn = tên hiển thị đã làm sạch (như `NostrProfileName`: bỏ ký tự điều khiển, gộp khoảng trắng, tối đa 40 điểm mã) với tiền tố `Bluesky · `; không có thì `Bluesky · @handle`.
- `Feed.icon` = địa chỉ `avatar` nếu là HTTPS tới tên miền `*.bsky.app`/`*.bsky.social` (kiểm), ngược lại không đặt.

**4. Lưu và đồng bộ nền.**
- `AbstractRssRepository.subscribe` (bản Local dùng, không bị ghi đè trong `LocalRssSv`) chèn `Feed` nguyên văn, nên `icon` ghi lúc đăng ký được giữ. `SubscribeViewModel.subscribe()` hiện chỉ đặt lại `groupId`, `isNotification`, `isFullContent` (đã đọc), nên **không xóa `icon`**; phải có test chứng minh. `syncSingleFeed` chỉ gọi `queryRssIcon` khi `feed.icon == null`, nên sync nền **không ghi đè** icon; nếu không có avatar, nó sẽ gọi `queryRssIcon`, nên cần nhánh bỏ qua BLUESKY như NOSTR.
- `LocalRssSv.queryFeedArticles`: thêm nhánh `BLUESKY` cạnh `NOSTR`; **không đổi tên hay icon** nguồn khi đồng bộ nền (người dùng có thể đã đổi tên).
- `RssHelper.parseFullContent` trả rỗng và `queryRssIcon` trả ngay cho BLUESKY; `OfflinePrecacheService` bỏ qua BLUESKY; `subscribe()` ép `isFullContent = false` cho BLUESKY.

**5. Lỗi và quay về RSS.** Mạng lỗi/hết giờ/không có bài từ API thì chạy `rssHelper.searchFeed` trên địa chỉ RSS như cũ (đường FRONT-02 hiện có, không đổi); tài khoản không tồn tại thì báo `web_feed_fetch_failed`/lỗi hiện có. Hủy tìm kiếm không bị báo là lỗi (bài học FRONT-02b).

## Kiểm chứng
- JVM TDD đỏ→xanh, mutation có backup và `cmp`: mapper (bài chính chủ, bỏ đăng lại, bỏ DID khác, bài rỗng, tiêu đề 80 điểm mã và emoji ở biên, thoát HTML, embed một dòng, tên hiển thị làm sạch, tên rỗng rơi về handle, icon chỉ nhận HTTPS `*.bsky.app/social`), client với `MockWebServer` (HTTP 200, 400 `Profile not found`, 500, treo/hết giờ, phản hồi quá lớn, chuyển hướng ra host lạ bị chặn, JSON hỏng), `SubscribeViewModel` (nhánh API, quay về RSS khi API lỗi, 400 báo lỗi không thử RSS, hủy không là lỗi, nguồn đã có), `LocalRssSv` (đồng bộ nền đọc API, không đổi tên/icon), `RssHelper` và `OfflinePrecacheService` bỏ qua BLUESKY, `FeedUrlNormalizer` (chỉ nhận `http`/`https`, đã đọc) bỏ `bsky:` ở đồng bộ LAN, kèm một test.
- Máy thật **chỉ Tecno KJ7**: helper tạm (không commit) gọi **API thật** bằng `am instrument`, **không chạm màn hình**, không MockK, chỉ ghi số lượng, độ dài tên, loại embed (không ghi handle, tên, nội dung, địa chỉ ảnh); sao lưu và khôi phục byte-exact; công cụ ở `/Users/loitran/.claude/tmp-f29`.
- **Không kiểm được:** hiển thị thật tên và icon trong hộp thoại và danh sách (không chạm màn hình), tải ảnh đại diện thật qua Coil, hành vi khi Bluesky giới hạn tốc độ, đổi API về sau, quảng cáo che màn hình khi chạy trên `MainActivity` (owner hoãn ad/VIP).

## Rủi ro đã biết
- Bluesky có thể đổi hoặc giới hạn `public.api.bsky.app`; khi đó nguồn mới quay về RSS (còn hoạt động).
- Mỗi lần xem trước tốn hai yêu cầu mạng (RSS cũ: một).
- Phản hồi 140 KB cho 30 mục; giới hạn kích thước phải đủ rộng để không cắt bài hợp lệ nhưng đủ nhỏ để chặn phản hồi độc.
- `Feed.icon` là địa chỉ ảnh do người lạ đặt: chỉ nhận HTTPS tới tên miền Bluesky đã liệt kê; vẫn là nội dung tự khai.
- Nguồn RSS cũ và nguồn API mới của cùng tài khoản có địa chỉ khác nhau nên có thể đăng ký trùng (một RSS, một API); chấp nhận, ghi vào giới hạn. **Cập nhật: FRONT-02i đã chặn một phần (cùng dạng tên).**

## Giới hạn đã biết
- Không tải ảnh/video trong bài, không xem luồng trả lời, không hashtag hay danh sách.
- Tên hiển thị và ảnh đại diện tự khai, không xác minh.
- Nguồn đã đăng ký bằng RSS không tự chuyển sang API.
- Tên chỉ đặt lúc xem trước; sync nền không đổi tên hay icon.

## Điều chỉnh khi thực thi
- **`call.execute()` chặn luồng** không hủy được khi hết thời gian hay hủy; dùng `enqueue` + `suspendCancellableCoroutine`, và hủy gọi `call.cancel()` thật.
- **Quy tắc "handle hay DID"** nằm ở một nơi (`BlueskyFeedMapper.isActor`): ký tự đầu phải là chữ hoặc số (loại `..`, `.x`), tối đa 253 ký tự; client và `actorFromRssUrl` cùng dùng. DID của hồ sơ trả về cũng phải qua `isActor`, vì nó đi vào yêu cầu thứ hai.
- **Hai guard thừa đã gỡ:** `response.isRedirect` (đã có `followRedirects(false)`) và `contentLength` (đã có `readLimited`).
- **Nhánh `search()` không dùng `when` toàn diện** trên `BlueskyOutcome`: mock `relaxed` trả đối tượng giả; cái gì không phải `Found`/`NotFound` thì rơi về RSS.
- **Nhánh "không tìm thấy"** không viết lại ô nhập (khác lỗi kiểu FRONT-02b), giữ nguyên chữ đã gõ.
- **Máy thật:** Tecno KJ7; helper tạm gọi API thật qua `OkHttpClient` của app, không `MainActivity`.
