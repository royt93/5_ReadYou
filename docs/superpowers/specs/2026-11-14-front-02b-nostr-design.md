# FRONT-02b — Theo dõi tài khoản Nostr bằng `npub` công khai (thiết kế)

**Ngày:** 2026-11-14 · **Nhánh:** `dev` · **Task gốc:** `doc/task/todo/FRONT-02_decentralized-web3-social.md` (nửa còn lại; Mastodon và Bluesky đã xong, xem `doc/task/done/FRONT-02_decentralized-web3-social_DONE.md`) · **Nền:** ô Thêm nguồn (`SubscribeViewModel.search`), `SocialFeedUrlResolver`, `RssDescriptionParser.titleFromBody`, đường đồng bộ nguồn WEB trong `LocalRssSv.queryFeedArticles`, OkHttp 5.0.0-alpha.11 của app

## Mục tiêu
Người dùng dán một `npub1…` vào ô **Thêm nguồn** sẵn có; app xem trước các ghi chú mới nhất của tài khoản đó rồi đăng ký như mọi nguồn khác. Bài hiện theo thứ tự thời gian, không thuật toán, không đăng nhập, không giữ khóa.

## Quyết định của owner (2026-11-14)
- **Kiểm chữ ký:** tự viết bằng Kotlin thuần, **không thêm thư viện** (Bech32 + BIP-340 trên `BigInteger`).
- **Cách nhập:** chỉ `npub` công khai (không `nprofile`, không link hồ sơ).
- **Relay:** 2 relay cố định, thử lần lượt.
- **Link bài:** `https://njump.me/note1…`, chỉ mở khi người dùng bấm; không gọi njump.me khi đồng bộ.
- Thiết bị kiểm: **chỉ Tecno KJ7 `115333744A005844`**; không bấm theo tọa độ (memory `never-tap-by-stale-coordinates`).
- Phạm vi chung đã chốt cho FRONT-02: chỉ đọc, không đăng nhập, không lưu khóa/mật khẩu.

## Sự thật đã đo (không suy đoán)
- Thử từ máy Mac ngày 2026-11-14 bằng WebSocket thật: `nos.lol`, `relay.primal.net`, `offchain.pub` trả ghi chú và `EOSE` trong khoảng 0,7 đến 1,4 giây; `relay.damus.io` lỗi hoặc hết giờ (kể cả thử `curl` NIP-11 trả 521). Hai lần thử khác nhau cho cùng kết quả về damus.
- Mỗi ghi chú có đúng các trường `content, created_at, id, kind, pubkey, sig, tags`; `id` 64 ký tự hex, `pubkey` 64, `sig` 128. **Không có tiêu đề và không có link web.**
- App đã có OkHttp 5.0.0-alpha.11 (có `newWebSocket`), nhưng **chưa dùng WebSocket ở đâu** và **chưa có** Bech32, secp256k1, BouncyCastle hay thư viện Nostr. Có sẵn `MessageDigest("SHA-256")` ở vài nơi.
- `Feed.sourceType` là chữ (`RSS_SOURCE_TYPE`, `WEB_SOURCE_TYPE`, `READ_LATER_SOURCE_TYPE`…), nên thêm `"NOSTR"` **không cần đổi Room** (giữ v22).
- `LocalRssSv.queryFeedArticles` đã rẽ nhánh theo `sourceType` cho nguồn WEB; `AbstractRssRepository` bỏ qua phần tải toàn văn khi `sourceType != RSS_SOURCE_TYPE`.
- Ô Thêm nguồn chạy `formatUrl()` trên chữ gõ trước mọi tra cứu: `npub1…` sẽ thành `https://npub1…` và hỏng, nên bộ nhận dạng phải chạy **trước** bước đó (cùng bài học của Mastodon/Bluesky).

## Phạm vi trung thực
- **Chỉ đọc, công khai.** Không đăng nhập, không lưu khóa, không đăng bài, không xem tin nhắn riêng.
- **`nsec` (khóa bí mật) không bao giờ được nhận hay gửi đi:** chữ bắt đầu `nsec1` coi như không phải Nostr, không đi qua mạng, không vào log. (Người dùng có thể dán nhầm; app không giữ lại chữ đó.)
- Chỉ ghi chú loại 1 (bài đăng chữ). **Bỏ** ghi chú trả lời (có thẻ `e`), chia sẻ lại (loại 6), ảnh, tên hồ sơ (loại 0).
- Tên nguồn mặc định: `Nostr · npub1abcd…wxyz` (tám ký tự đầu và bốn ký tự cuối của `npub`). Người dùng đổi tên được như mọi nguồn. Không lấy hồ sơ để giữ ít dữ liệu nhất.
- **Riêng tư nói thẳng:** relay thấy địa chỉ mạng của người dùng và `npub` đang đọc. Không có cách đọc Nostr mà không hỏi relay. Mỗi lần đồng bộ hỏi tối đa 2 relay cố định. Không có công tắc consent riêng vì không gửi dữ liệu người dùng tới AI hay bên thứ ba ngoài relay; điều này được ghi vào báo cáo.
- Không dependency mới, không Room v23, không chuỗi mới (nên không phải sửa 38 ngôn ngữ), không Gemini/quảng cáo/IAP/tiền.
- Chỉ **2 relay cố định**, không màn cài đặt relay.

## Thành phần
Tất cả trong `infrastructure/social/nostr/`, thuần Kotlin trừ `NostrRelayClient`:

1. **`Bech32`**: giải mã và mã hóa bech32 (BIP-173) với kiểm checksum; từ chối chuỗi lẫn hoa thường, ký tự lạ, checksum sai. **`NostrKeys`**: `npubToHex(input): String?` (đúng tiền tố `npub`, đúng 32 byte, ngược lại `null`), `noteId(hex): String` (mã hóa `note1…`). `nsec1…` luôn ra `null`.
2. **`Bip340`**: `verify(pubkeyHex, messageHex, sigHex): Boolean` trên secp256k1 bằng `BigInteger`, theo BIP-340 (điểm có `y` chẵn, hash gắn nhãn `BIP0340/challenge`). Chỉ kiểm dữ liệu công khai, **không có hàm ký, không giữ khóa bí mật**.
3. **`NostrEvent`**: phân tích một tin JSON (`org.json`, cần Robolectric trên JVM) và `isValid(expectedPubkeyHex)`: đủ trường đúng kiểu và độ dài; `kind == 1`; `id` đúng bằng SHA-256 của chuỗi chuẩn hóa `[0, pubkey, created_at, kind, tags, content]` (JSON nén, không khoảng trắng); chữ ký đúng; `pubkey` đúng bằng `npub` đang theo dõi. Sai bất kỳ điều kiện nào ⇒ bỏ bài đó.
4. **`NostrRelayClient`**: mở WebSocket bằng `OkHttpClient` của app (nên đi qua DoH `AppDns` của STRAT-03), gửi một `["REQ", <id>, {"kinds":[1],"authors":[<hex>],"limit":50}]`, đọc tới `EOSE`, rồi gửi `CLOSE` và đóng. Hằng số có tên: danh sách relay `[wss://nos.lol, wss://relay.primal.net]`, `RELAY_TIMEOUT_MS = 8_000`, `MAX_MESSAGE_BYTES = 64 * 1024`, `MAX_EVENTS_READ = 50`, `MAX_NOTES_KEPT = 20`. Thử relay thứ nhất; nếu lỗi, hết giờ hoặc không có bài hợp lệ thì thử relay thứ hai. Tin quá `MAX_MESSAGE_BYTES` bị bỏ. Luôn đóng socket (cả khi lỗi, hủy coroutine, hết giờ). Chỉ nhận `wss://`.
5. **`NostrFeedMapper`**: ghi chú hợp lệ ⇒ `Article`. Bỏ ghi chú có thẻ `e` (trả lời). Sắp theo `created_at` giảm dần, bỏ trùng `id`, giữ tối đa `MAX_NOTES_KEPT`. **Nội dung là chữ không đáng tin**: thoát HTML (`&`, `<`, `>`, `"`) trước khi gán vào `rawDescription`, để các bước sau không hiểu nó như HTML; giữ xuống dòng. Tiêu đề = `RssDescriptionParser.titleFromBody(...)` (80 ký tự đầu, `…` nếu cắt); ghi chú trống ⇒ bỏ. `date` = `created_at`, **kẹp không quá giờ hiện tại** (relay không được đặt bài ở tương lai để chặn đầu danh sách). `link` = `https://njump.me/note1…`. `shortDescription` cắt theo `MAX_SHORT_DESCRIPTION_LENGTH` sẵn có.
6. **Nối vào app**:
   - `SubscribeViewModel.search()`: trước `formatUrl()`, nếu chữ gõ (đã cắt khoảng trắng) là `npub` hợp lệ thì dùng đường Nostr: `Feed(sourceType = NOSTR, url = "nostr:<npub>", sourceUrl = "nostr:<npub>", name = "Nostr · npub1abcd…wxyz")`; lấy ghi chú qua `NostrRelayClient` để xem trước; "đã đăng ký" kiểm bằng `nostr:<npub>`. Không phải `npub` ⇒ luồng cũ y nguyên. Cách đặt xem trước (dùng lại nhánh `webPreview` hay một nhánh riêng) **phải đọc `search()` và luồng xác nhận (`confirmingWebFeed`) ngay trước khi sửa**; không đoán.
   - `LocalRssSv.queryFeedArticles`: thêm nhánh `feed.sourceType == Feed.NOSTR_SOURCE_TYPE` cạnh nhánh WEB; lỗi relay ném `IOException` như nhánh WEB để cơ chế báo lỗi nguồn sẵn có xử lý.
   - `Feed`: thêm hằng `NOSTR_SOURCE_TYPE = "NOSTR"` (không đổi cột).
   - Phải kiểm và chặn các đường tự gọi mạng theo `Feed.url` hoặc `Article.link` (tải toàn văn, `OfflinePrecacheService`, tải ảnh nền): với `sourceType == NOSTR` **không được** tự tải `njump.me` hay `nostr:…`. Tệp nào đang giả định URL là `http(s)` phải được đọc và test.
7. **Không đổi**: `Article`, Room, DataStore, manifest, chuỗi tài nguyên.

## Luồng dữ liệu
`npub` gõ vào ô ⇒ `NostrKeys.npubToHex` ⇒ (hex) ⇒ `NostrRelayClient` hỏi relay 1 (rồi 2 nếu cần) ⇒ danh sách tin thô ⇒ `NostrEvent.isValid` lọc ⇒ `NostrFeedMapper` ⇒ `Article` xem trước ⇒ người dùng đăng ký ⇒ lưu `Feed(NOSTR)`. Đồng bộ nền: cùng đường từ `LocalRssSv.queryFeedArticles`, chèn bài mới theo `link` như mọi nguồn.

## Lỗi
- Relay lỗi, hết giờ, đóng sớm hoặc không có bài hợp lệ ⇒ thử relay kế; cả hai hỏng ⇒ lỗi "không đọc được" như nguồn lỗi bình thường (xem trước báo lỗi, đồng bộ nền ghi lỗi nguồn).
- Bài sai `id`, sai chữ ký, sai `pubkey`, thiếu trường ⇒ chỉ mất bài đó.
- `npub` sai checksum hoặc sai độ dài ⇒ không phải Nostr, rơi về luồng cũ (sẽ báo không đọc được như mọi chữ lạ).
- Log chỉ ghi loại lỗi và số bài; **không** ghi `npub`, nội dung bài, địa chỉ relay hay tin thô.

## Kiểm chứng
- JVM, TDD đỏ→xanh (đọc lý do đỏ từ XML **sau khi** `.exit` tồn tại), mutation có backup và `cmp`:
  - `Bech32`/`NostrKeys`: ví dụ chuẩn của BIP-173 và NIP-19, checksum sai, lẫn hoa/thường, `nsec`, độ dài sai, `note1` khứ hồi.
  - `Bip340`: bộ vector chính thức của BIP-340 (đúng và sai), chữ ký sửa một bit, `pubkey` không nằm trên đường cong, `r` hoặc `s` quá lớn.
  - `NostrEvent`: `id` sai, chữ ký sai, `pubkey` lạ, thiếu trường, sai kiểu, `kind` khác 1, ký tự Unicode trong `content`.
  - `NostrRelayClient` bằng `MockWebServer3` có WebSocket: đọc tới `EOSE`; relay 1 chết thì sang relay 2; tin quá lớn bị bỏ; quá `MAX_EVENTS_READ` thì dừng; luôn đóng socket; hết giờ; relay gửi bài sai chữ ký thì bị lọc.
  - `NostrFeedMapper`: `<script>` bị thoát, tiêu đề 80 ký tự và emoji ở biên, bỏ trả lời, bỏ trùng, ngày kẹp không quá hiện tại, link `njump.me/note1…`.
  - Nối vào ô Thêm nguồn (test view model + test nguồn): `npub` nhận **trước** `formatUrl`; `nsec` không đi qua mạng; chữ khác giữ luồng cũ; hai test cũ (`SubscribeViewModelWebFeedTest`, `SubscribeViewModelNewsletterTest`) và các test Mastodon/Bluesky vẫn xanh; nguồn NOSTR không kích hoạt tải toàn văn hay precache.
  - Test nguồn: không `Log.` mang `npub`/nội dung trong mã Nostr mới.
- Cả suite `--rerun-tasks` (nền 3.440 test), lint (nền 2.465 cảnh báo, 0 trong tệp mới), `assembleDevDebug assembleDevDebugAndroidTest`; lược đồ và `app/src/main/res` không đổi.
- androidTest trên Tecno bằng `adb -s 115333744A005844 shell am instrument` (không `connected*`): helper tạm **không commit** gọi **relay thật** với một `npub` công khai đã biết, in số bài hợp lệ, số bị bỏ và tiêu đề. Cổng thiết bị chỉ đọc trước, sao lưu byte-exact Room/`-shm`/`-wal`/DataStore/`shared_prefs`, khôi phục và so băm DataStore. **Không chạm màn hình.**
- **Không kiểm được (ghi thẳng vào báo cáo):** gõ `npub` thật vào ô trên màn hình; mở link `njump.me` trên máy; relay khác hai relay cố định; ảnh, trả lời, tên hồ sơ; việc relay đổi hành vi theo ngày; tài khoản không có ghi chú nào; hiệu năng `BigInteger` trên máy yếu (A50s) ngoài Tecno.

## Giới hạn đã biết
- Phụ thuộc vào hai relay công khai; nếu cả hai tắt, nguồn báo không đọc được.
- Hai relay có thể thiếu bài so với mạng Nostr đầy đủ (không có phần hỏi lan rộng).
- Không thấy bài đã bị xóa ở relay khác, không có ảnh, trả lời hay tên hồ sơ.
- Tiêu đề thay thế chỉ là cắt dòng đầu, có thể cắt giữa câu.
- Kiểm chữ ký tự viết bằng `BigInteger` chậm hơn thư viện native; chỉ đủ cho vài chục ghi chú mỗi lần.
- Link `njump.me` là trang bên thứ ba; app chỉ mở khi người dùng bấm.
