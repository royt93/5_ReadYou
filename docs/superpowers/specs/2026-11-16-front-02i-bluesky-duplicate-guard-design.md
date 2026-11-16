# FRONT-02i — Chặn đăng ký trùng một tài khoản Bluesky (RSS cũ và API mới) (thiết kế, chờ owner duyệt spec)

**Ngày:** 2026-11-16 · **Nhánh:** `dev` · **Nền:** FRONT-02 (Bluesky qua RSS), FRONT-02g (Bluesky qua API), `SubscribeViewModel.search()`, `SocialFeedUrlResolver`, `BlueskyFeedMapper`

> Trạng thái: owner đã chốt trong brainstorm (2026-11-16): (1) dùng chuỗi **sẵn có** `already_subscribed`, không thêm chuỗi, không thêm locale; (2) chặn **hai chiều**, **cùng dạng tên** (handle với handle, DID với DID), **không hỏi mạng**; (3) kiểm **cả hai địa chỉ ngay đầu nhánh Bluesky, trước khi gọi API**. Spec chờ owner duyệt trước khi lập plan. Chưa có plan, chưa có mã.

## Mục tiêu
Báo cáo FRONT-02g ghi một giới hạn: nguồn cũ (RSS) và nguồn mới (API) của **cùng một tài khoản** có địa chỉ khác nhau nên người dùng có thể đăng ký hai lần và thấy bài trùng. Sửa bằng cách báo "Đã đăng ký" cho tài khoản đã có ở **một trong hai dạng**. Chỉ JVM, không chạm thiết bị.

## Sự thật đã đọc trong code (2026-11-16)
- Nguồn **RSS** của Bluesky lưu `Feed.url` = địa chỉ RSS mà resolver tạo: `RssHelper.searchFeed(feedLink)` đặt `url = feedLink` (`RssHelper.kt:48-55`). Resolver (`SocialFeedUrlResolver.blueskyLink`/`fromHandle`) luôn trả `https://bsky.app/profile/<actor>/rss`; **handle chữ thường**, **DID giữ nguyên chữ hoa/thường**.
- Nguồn **API** lưu `Feed.url = bsky:<actor>` (`BlueskyFeedMapper.feedUrl`: handle chữ thường, DID giữ nguyên).
- `AbstractRssRepository.isFeedExist(url)` = `feedDao.queryByLink(currentAccountId, url).isNotEmpty()`: so **đúng bằng** chuỗi địa chỉ, trong tài khoản hiện tại (`AbstractRssRepository.kt:377`). Truy vấn là `WHERE accountId = :accountId and url = :url` và cột `url` **không** khai báo `COLLATE NOCASE` (đã đọc schema bảng `feed` trên bản sao lưu), nên so sánh **phân biệt hoa thường**: việc dùng chữ thường cho handle (resolver và `feedUrl`) là điều làm hai dạng khớp nhau.
- Trong `SubscribeViewModel.search()`, nhánh Bluesky chạy sau `SocialFeedUrlResolver.resolve(typed)`; biến `socialUrl` đã là địa chỉ RSS chuẩn hóa. Nhánh hiện chỉ kiểm `isFeedExist(bsky:<actor>)` (`SubscribeViewModel.kt:215`); luồng RSS cũ phía dưới kiểm `isFeedExist(linkContent)` (dòng 296) với địa chỉ RSS, nhưng chỉ khi API lỗi và rơi về RSS.
- Hệ quả hiện nay: đã đăng ký RSS rồi dán lại → nhánh API chỉ kiểm `bsky:`, không thấy nguồn RSS → gọi API → đăng ký thêm nguồn API (**trùng**). Ngược lại đã đăng ký API rồi, nếu API lỗi lần sau thì rơi về RSS và kiểm RSS, không thấy nguồn API → **trùng**.
- `actorFromRssUrl(socialUrl)` đã tách `actor` hợp lệ (chữ hoặc số đứng đầu, ≤253 ký tự) từ địa chỉ RSS.

## Phạm vi trung thực
- Chặn theo **tên cùng dạng**: `alice.bsky.social` với `alice.bsky.social` (không phân biệt hoa thường ở handle vì cả hai dạng đều chuẩn hóa chữ thường), `did:plc:X` với `did:plc:X` (phân biệt hoa thường). **Không** chặn handle so với DID của cùng một người (cần hỏi mạng để đổi tên; owner đã chọn không làm). Ca đó vẫn trùng.
- Chỉ tài khoản **Local** (như nhánh Bluesky hiện tại); tài khoản khác đi luồng cũ không đổi.
- Chỉ chặn lúc **đăng ký mới** (`search()`); **không** gộp hay xóa các cặp trùng đã tồn tại trong máy; **không** tự chuyển nguồn RSS cũ sang API.
- Không đổi mã production ngoài `SubscribeViewModel.search()`; không chuỗi, locale, Room, DataStore, manifest, dependency mới; không Gemini, ad, IAP, VIP.
- Không chạm thiết bị (chỉ JVM). Nếu sau này cần máy: **chỉ Tecno KJ7**, cấm Pixel.

## Thiết kế
Trong nhánh `if (blueskyActor != null)` của `search()`, thay lần kiểm `isFeedExist(feedUrl)` hiện có bằng kiểm **hai địa chỉ** của cùng tài khoản:

```kotlin
val apiUrl = BlueskyFeedMapper.feedUrl(blueskyActor)   // bsky:<actor>
val rssUrl = socialUrl!!                                // https://bsky.app/profile/<actor>/rss, đã chuẩn hóa bởi resolver
if (rssService.get().isFeedExist(apiUrl) || rssService.get().isFeedExist(rssUrl)) { /* "Đã đăng ký", return@launch */ }
```
Hai ý quan trọng:
1. `socialUrl` **không null** ở trong nhánh này (vì `blueskyActor` được tách từ chính nó); mã thật không dùng `!!` (quy tắc R5: tránh force-unwrap), mà dùng một biến đã kiểm (ví dụ trả về cặp `Pair(actor, rssUrl)` từ một hàm nhỏ trong `BlueskyFeedMapper`, hoặc kiểm `socialUrl ?: return`). Kiểu chính xác để plan quyết định sau khi đọc mã.
2. Lần kiểm này nằm **trước** khi hiện "Đang tìm" và trước khi gọi API, nên một tài khoản đã đăng ký không tốn lần gọi mạng nào, và ca "API lỗi rồi rơi về RSS" cũng được chặn từ đầu (vì đã chặn trước khi tới luồng RSS).

Chuỗi báo lỗi, `lockLinkInput = false`, `title` giữ **giống hệt** nhánh "đã đăng ký" hiện tại (cùng `R.string.already_subscribed`).

## Kiểm chứng
- Test JVM mới ở `SubscribeViewModelBlueskyTest` (khuôn sẵn có, `mockk` `isFeedExist` theo địa chỉ):
  - đã có nguồn **RSS** `https://bsky.app/profile/alice.bsky.social/rss` ⇒ dán `@alice.bsky.social` báo "Đã đăng ký", **không** gọi API, **không** gọi `rssHelper.searchFeed`;
  - đã có nguồn **API** `bsky:alice.bsky.social` ⇒ như cũ (test hiện có vẫn xanh);
  - đã có nguồn API, **API lỗi** (`Failed`) ⇒ vẫn báo "Đã đăng ký" từ đầu, **không** rơi về RSS;
  - handle **hoa thường lẫn lộn** (`@Alice.Bsky.Social`) khớp nguồn đã lưu chữ thường (cả hai dạng);
  - **DID** giữ chữ hoa/thường: nguồn lưu `bsky:did:plc:AbC123` khớp link `…/profile/did:plc:AbC123`, **không** khớp `…/profile/did:plc:abc123`;
  - **không** chặn khi handle khác handle, và khi nguồn đã lưu là của handle khác;
  - tài khoản không phải Local: không kiểm hai địa chỉ Bluesky (không đổi);
  - Mastodon: không đổi (`isFeedExist` chỉ gọi với địa chỉ RSS của Mastodon, đúng một lần).
- Hai test Bluesky RSS cũ (`SubscribeViewModelSocialTest`) và `theAlreadySubscribedCheck_usesTheRssAddress` (Mastodon) phải xanh nguyên vẹn.
- Mutation (JVM, backup + `cmp`): bỏ lần kiểm RSS (chỉ còn API); bỏ lần kiểm API (chỉ còn RSS); đảo thứ tự thành sau khi gọi API; lấy `socialUrl` thay bằng địa chỉ dựng lại từ handle chưa chuẩn hóa; bỏ điều kiện Local. Mỗi mutant phải có test đỏ.
- Full suite `--rerun-tasks` (kỳ vọng 3.708 + test mới, 0 lỗi), lint 0 lỗi, `assembleDevDebug` thành công; `app/schemas`, `app/src/main/res`, manifest, `app/build.gradle` không đổi.

## Rủi ro đã biết
- `isFeedExist` so **đúng bằng** chuỗi; nếu nguồn RSS cũ được lưu bằng một dạng địa chỉ khác (ví dụ link có `/rss` viết hoa, có tham số truy vấn, hoặc `http://`), lần kiểm sẽ không thấy. Resolver luôn sinh một dạng cố định nên nguồn tạo qua ô Thêm nguồn khớp; nguồn nhập từ **OPML** hoặc dán tay địa chỉ RSS đầy đủ của Bluesky có thể khác dạng và vẫn trùng. Ghi vào giới hạn.
- Thêm một lần truy vấn Room mỗi lần dán một tài khoản Bluesky (rẻ).
- Người dùng có thể đã có cả hai nguồn trùng từ trước: không xử lý.

## Giới hạn đã biết
- Không chặn handle so với DID của cùng một người.
- Không chặn nguồn RSS đã nhập qua OPML hay địa chỉ dạng khác.
- Không gộp hay dọn các cặp trùng đã có.
