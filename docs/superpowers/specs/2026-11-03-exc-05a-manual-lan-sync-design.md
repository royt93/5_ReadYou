# EXC-05a — Đồng bộ thủ công một chiều qua LAN: Design

**Ngày:** 2026-11-03 · **Nhánh:** `dev` · **Trạng thái:** chờ owner duyệt spec

## Mục tiêu
Chuyển thủ công một gói nhỏ chứa feed và trạng thái đã đọc/đánh sao giữa hai bản RSS Cat Hub trong cùng LAN, **mã hoá đầu-cuối**, không server Internet/cloud. Máy nguồn phát gói; máy nhận nhập secret, xem trước, rồi chủ động áp dụng.

## EXC-05 gốc và phần chưa hoàn tất
Todo `doc/task/todo/EXC-05_local-p2p-sync.md` yêu cầu **hai Android tự tìm nhau qua NSD**, đồng bộ tự động hai chiều trong 3 giây, E2EE. Tecno `115333744A005844` là máy duy nhất được khoá trong phiên; A50s `R58MA6WYRPE` offline, Pixel `2B051FDH3006MU` không được dùng. Vì vậy **không thể** kiểm end-to-end giữa hai Android thật.
- Lát cắt này được đặt tên **EXC-05a**, không đánh dấu EXC-05 gốc hoàn tất.
- Dùng Tecno + Mac cùng LAN để kiểm giao thức, mã hoá/giải mã, preview, apply, và rollback; Mac đóng vai peer qua script Python/curl.
- Chưa có NSD, tự động, hai chiều hay xung đột concurrent. Làm các phần ấy sau khi có hai Android thật và thiết kế riêng.

## Quyết định của owner (AskUserQuestion)
- **Thủ công, một chiều mỗi lượt**, không tự nhận diện hay NSD.
- Secret **ngẫu nhiên 128-bit**, hiển thị một lần với Copy/Share; receiver nhập secret. AES-GCM + PBKDF2-HMAC-SHA256, 310.000 vòng.
- Gói gồm **feed metadata + trạng thái đã đọc/đánh sao/readAt**; không nội dung bài, ghi chú, vị trí đọc, account credentials, API key, DataStore.
- Merge trạng thái **monotonic**: `isUnread = old AND incoming`; `isStarred = old OR incoming`; `readAt = min(non-null old, incoming)`.
- Feed trùng giữ metadata máy nhận; feed mới vào group mặc định. Article chỉ ghép nếu đã có ở cả hai máy theo `(normalized feed URL, article.link)`; article chưa tải trên receiver bị bỏ qua, không tải mạng và không tạo bài giả.
- Payload lỗi/URL sai/duplicate key/quá giới hạn: **reject toàn gói trước khi sửa DB**. Apply là transaction nguyên khối; lỗi DB rollback.

## Sự thật từ code (đã đọc)
- `AndroidDb` Room **v20**, entities gồm `Account`, `Feed`, `Group`, `Article`, `ArticleReadPosition`, `ArticleHighlightNote`, v.v. Task này không cần schema mới.
- `Article.id` Local RSS sinh UUID ngẫu nhiên (`LocalRssSv`), nên không thể đồng bộ bằng id. `Article` có `link`, `feedId`, `isUnread`, `isStarred`, `readAt`; chỉ metadata trạng thái được áp.
- `FeedDao.queryByLink(accountId, url)` và group mặc định đã tồn tại; cần đọc đầy đủ DAO/transaction để chọn API apply đúng account Local.
- `article_read_position` có `progress`, `lastReadTime`, `isSynced` nhưng **ngoài phạm vi**.
- `OPMLDataSource` parse feed/group nhưng không chứa trạng thái read/star; không thể dùng OPML làm gói sync.
- EXC-13 có `MirrorServer`, `MirrorHttp`, token, parser, router, giới hạn tốc độ và `LanAddressProvider`; E2EE mới là phần EXC-05a. EXC-13 dùng HTTP thường và URL token nhưng token không mã hoá; EXC-05a phải đưa **chỉ ciphertext** vào response.

## Thiết kế
### Giao thức
- **Send:** chọn account **Local hiện tại**; tạo snapshot allowlist; validate/cap; serialize JSON version 1; sinh secret 16 byte `SecureRandom` cho session; KDF PBKDF2-HMAC-SHA256 (310.000 rounds, 256-bit); AES/GCM/NoPadding với nonce 12 byte, tag 128-bit. Envelope binary: `magic "RCS1" (4 bytes) | salt (16 bytes) | nonce (12 bytes) | ciphertext+tag`; mã hoá Base64 URL-safe để gói copy qua script khi cần. Secret 128-bit chữ hex (32 ký tự) không truyền trong HTTP; người dùng tự chuyển qua clipboard/share/kênh họ tin.
- **Receive:** nhập URL session + secret; lấy ciphertext qua endpoint LAN được bảo vệ bằng token phiên (tái dùng server EXC-13); giải mã và xác thực GCM **trước** parse JSON; validate toàn bộ schema, URL, số lượng, kích thước và duplicate key; dựng preview thuần, không ghi DB; `Apply` mới gọi transaction.
- AES-GCM bảo vệ nội dung payload khỏi nghe lén/sửa đổi trong LAN; token endpoint chỉ cản đoán URL. URL/địa chỉ IP, thời điểm, byte length vẫn thấy được; ai có secret cũng đọc được gói. Secret hết hạn cùng phiên, không log/persist, xoá khỏi state khi stop/timeout/rời màn/nền.
- Không đổi endpoint/format EXC-13 đang phục vụ mirror: thêm endpoint mới trong router/engine với handler riêng, chỉ ciphertext. Hoặc tạo `SyncSessionServer` dùng chung lõi `MirrorServer` nhưng interface phải **không** có route trả plaintext; thêm security test chứng minh cả body raw package và secret không lọt ra HTTP. Không duplicate một server stack khác.

### Envelope và giới hạn
- `SyncLimits`: `MAX_ENVELOPE_BYTES = 8 MiB`, `MAX_ARTICLE_STATES = 20_000`, `MAX_FEEDS = 5_000`, `MAX_FEED_URL_CHARS = 2_048`, `MAX_ARTICLE_LINK_CHARS = 4_096`, `MAX_NAME_CHARS = 300`, `PBKDF2_ITERATIONS = 310_000`, `SECRET_BYTES = 16`, `SALT_BYTES = 16`, `GCM_NONCE_BYTES = 12`, `GCM_TAG_BITS = 128`.
- Header version/magic sai, base64 hỏng, secret sai, tag sai, JSON lỗi, field lạ/thiếu/kiểu sai, feed/article duplicate, key không duy nhất, vượt cap → typed error; không trả message phân biệt ra mạng (client hiện lỗi đã localize).
- JSON allowlist version 1:

```json
{
  "version": 1,
  "sourceId": "32-lowercase-hex",
  "createdAt": 0,
  "feeds": [{"url":"https://example.org/rss","name":"News","groupName":"World"}],
  "articles": [{"feedUrl":"https://example.org/rss","link":"https://example.org/story","isUnread":false,"isStarred":true,"readAt":0}]
}
```

Không có `accountId`, `feedId`, `articleId` (UUID), title, content, image, `aiSummary`, notes, settings, DataStore, API key, password, account/backend token.

### URL, feed và article identity
- `FeedUrlNormalizer.normalize`: chỉ scheme `http` hoặc `https`; host bắt buộc; bỏ user-info (reject, không strip); lowercase scheme/host; bỏ fragment; bỏ port mặc định 80/443; path rỗng thành `/`; bỏ slash cuối trừ root; giữ query string (một số RSS dùng query làm feed ID); reject control chars và URL vượt `MAX_FEED_URL_CHARS`. Feed URL private/LAN được giữ nguyên, nhưng quá trình import **không fetch** URL.
- Duplicate normalized feed URL trong gói → reject toàn bộ gói. Duplicate `(normalizedFeedUrl, normalizedArticleLink)` → reject toàn bộ gói.
- Receiver account là **Local account đang chọn**; không có Local account → không cho bắt đầu Receive/Apply. Account không bao giờ nằm trong gói.
- Feed trùng: giữ feed nhận (tên/icon/group); feed mới tạo trong group mặc định nhận, dùng tên gửi; nếu tên đã trùng trong group, nối ` (2)`, ` (3)`… mà không đổi URL.
- Article lookup trong mỗi feed tương ứng theo `link` chính xác (không tự strip query/fragment khỏi link bài, vì có thể đổi identity); chỉ article có sẵn được cập nhật. Article không tìm thấy tăng `unmatchedArticleCount`, không import article mới, không tải mạng.
- Merge trên entity hiện tại, chỉ sửa 3 field: `isUnread = existing.isUnread && incoming.isUnread`; `isStarred = existing.isStarred || incoming.isStarred`; `readAt = minOf(non-null timestamps)` (nếu chỉ một non-null thì giữ giá trị đó; cả hai null → null). Giữ nguyên `title`, body, `date`, `feedId`, `accountId`, `aiSummary`, audio fields, FTS/Room metadata.

### Preview + transaction
- `SyncPackageValidator` tạo typed object trước khi repository đọc DB; chỉ nhận phiên bản 1 và **chỉ** các key allowlist (field lạ reject, không silently ignore).
- `LocalSyncPlanner.plan(package, receiverSnapshot)` pure: `feedsToAdd`, `duplicateFeeds`, `matchedArticles`, `unmatchedArticles`, `articlesToMarkRead`, `articlesToStar`, `readAtChanges`; không write.
- UI preview hiện các số trên, ghi rõ trạng thái chỉ tăng (không thể đồng bộ mark-unread/bỏ sao) và feed/bài không match. Người dùng phải nhấn `Apply` riêng.
- `LocalSyncRepository.apply(plan, localAccountId)` chạy trong **một `@Transaction`**: tạo feed mới vào group mặc định, áp updates. Mọi id/URL được validate và thuộc account Local hiện tại. DB error làm transaction rollback; UI giữ nguyên và cho retry. Không gọi `RssSv` backend/account cloud.

### UI và vòng đời
- Một màn "Local Sync" dùng `BaseScaffold` + `FeedbackIconButton(Icons.Rounded.ArrowBack)`, edge-to-edge insets, Material You.
- Hai luồng rõ: **Send** (sinh gói, bật server, hiện secret một lần + URL/token, expiry 5 phút, Copy/Share, nút Stop) và **Receive** (nhập URL + secret; decrypt → preview → Apply/Cancel). Không background/WorkManager.
- Server stop khi user bấm Stop, rời màn, app `ProcessLifecycleOwner ON_STOP`, mạng mất, session hết hạn 5 phút. Không service.
- Strings mới đủ 38 locale EN/VI thủ công, phần còn lại tiếng Anh; không `PluralsCandidate`, không `UnusedResources`.

## Giới hạn nói thẳng
1. **Không đạt acceptance criteria EXC-05 gốc**: không NSD, không tự động, không 2 chiều, không sync nền, không thử trong 3 giây hai máy. Đây chỉ là lát cắt EXC-05a; giữ todo EXC-05 gốc mở hoặc tạo trạng thái `In progress`, không chuyển sang done.
2. Chỉ smoke thật bằng Tecno + Mac giả peer: kiểm LAN/ciphertext/preview/transaction, **không** chứng minh hai Android thật cùng phiên bản hoặc sự tương thích Android-to-Android. Cần A50s online/máy thứ hai sau này.
3. HTTP thường: payload là ciphertext nhưng endpoint token/URL/địa chỉ/độ dài lộ; secret phải chuyển qua kênh tin cậy. Secret bị lộ trong hạn phiên cho phép đọc gói. Không dùng Wi-Fi công cộng.
4. Gộp monotonic không thể truyền mark-unread/bỏ sao; người dùng cần hiểu trước Apply.
5. Không có pending state: article chưa có trên receiver bị bỏ qua vĩnh viễn trong lượt đó; phải sync lại sau khi bài được tải nếu muốn trạng thái truyền sang.
6. Mã ghép 128 bit dài, nhập tay bất tiện; không PAKE/QR trong lát cắt này.
7. 310k PBKDF2 có thể chậm trên Tecno; phải đo derive time và ghi số thật. Nếu UI chờ lâu, chạy KDF trên Default/IO, không Main.

## Kiểm thử
- Thuần: `PairingSecretTest` (128-bit, hex, timeout, không lặp); `SyncEnvelopeCryptoTest` (GCM roundtrip, nonce/salt random mỗi lần, secret sai/tag/ciphertext sửa → reject, cap trước allocation, wrong magic/version); `FeedUrlNormalizerTest` (HTTP(S), host lowercase, default port/fragment/path/query, user-info/control/size reject); `SyncPackageValidatorTest` (allowlist, schema/version, duplicates, caps, null/wrong-type, URL credentials); `LocalSyncPlannerTest` (feed new/duplicate, article key, monotonic merge, unmatched); `SyncPayloadCodecTest` (UTF-8/JSON exact fields, no forbidden keys).
- Room Robolectric in-memory: Local account only; thêm feed vào default group; feed trùng giữ name/group nhận; article update chỉ 3 field; article không match không insert; group/account khác không bị sửa; transaction rollback khi DAO giữa chừng throw; `ArticleReadPosition`, ArticleBody, DataStore không đổi.
- Network loopback: server chỉ trả envelope ciphertext; gói JSON/secret không xuất hiện trong response/body/log; token sai, replay hết hạn, rate-limit; server stop khi timeout/network loss/onStop.
- ViewModel/Compose Robolectric: send state, receive, secret copy/share, preview, cancel no write, apply only after confirm, decrypt error no write, BackHandler, hết hạn/mất mạng, background stop.
- Instrumentation trên Tecno `115333744A005844` (server/socket/crypto thật, không `connectedDevDebugAndroidTest`). Smoke Tecno + Mac script: sender encrypt → receiver decrypt → preview → apply → đọc Room xác nhận; wrong secret / tampered envelope không thay DB. Backup Room 3 file + DataStore trước, restore `cmp` 4 file, **không xoá DataStore**.

## Ngoài phạm vi
NSD/mDNS, tự tìm, tự động khi mở app, đồng bộ hai chiều/concurrent conflict, feed polling, pending state cho article thiếu, nội dung bài/media, notes/highlights/read positions, account/backend credentials, DataStore/API keys, cloud, TLS (HTTP chỉ chở ciphertext), QR, Room migration, AI, ads/IAP.
