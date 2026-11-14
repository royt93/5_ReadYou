# FRONT-02b (Theo dõi tài khoản Nostr bằng `npub` công khai, bản thu hẹp)

**Date:** 2026-11-14  
**Branch:** `dev` (không push)  
**Scope:** dán `npub1…` vào ô **Thêm nguồn**; app hỏi hai relay công khai, **tự kiểm chữ ký từng ghi chú**, cho xem trước tối đa 20 ghi chú mới nhất rồi đăng ký như mọi nguồn khác. Chỉ đọc, không đăng nhập, không giữ khóa. Room **giữ v22**, không chuỗi mới, không dependency mới.  
**Spec / plan:** `docs/superpowers/specs/2026-11-14-front-02b-nostr-design.md`, `docs/superpowers/plans/2026-11-14-front-02b-nostr.md`.  
**Trạng thái task gốc:** vẫn nằm trong `doc/task/todo/` (owner chọn), vì nhiều ý của task gốc không làm (xem "Không làm").

## Nói thẳng về phạm vi

- **Nostr là nửa còn lại của FRONT-02**; Mastodon và Bluesky đã xong ở `FRONT-02_decentralized-web3-social_DONE.md`.
- **Chỉ `npub` công khai.** Không `nprofile`, không link hồ sơ, không tên hồ sơ, không ảnh, không trả lời, không đăng nhập, không đăng bài.
- **`nsec` (khóa bí mật) bị chặn trước mọi đường:** ô nhập bị xóa, hiện lỗi, không gọi relay, không gọi RSS, không qua DNS. Thông báo lỗi dùng lại chuỗi `web_feed_invalid_url` ("Dùng URL HTTPS công khai…"): **không chính xác tuyệt đối** nhưng tránh phải thêm chuỗi và dịch 38 ngôn ngữ.
- **Chỉ tài khoản Local.** Fever/Google Reader sẽ đẩy `nostr:…` lên máy chủ từ xa; với các tài khoản đó `npub` không được nhận và luồng cũ chạy y nguyên.
- **Riêng tư nói thẳng:** relay thấy địa chỉ mạng của người dùng và `npub` đang đọc. Không có cách đọc Nostr mà không hỏi relay. Mỗi lần đồng bộ hỏi tối đa 2 relay cố định (`nos.lol`, `relay.primal.net`). Không có công tắc consent riêng vì không gửi gì cho AI hay bên thứ ba ngoài relay.
- **Relay lỗi hoặc rỗng ⇒ báo lỗi, KHÔNG rơi về luồng web** (nếu rơi về, `npub` sẽ bị gửi đi như một địa chỉ web).
- Link bài là `https://njump.me/note1…` (trang bên thứ ba), chỉ mở khi người dùng bấm; app không gọi njump.me khi đồng bộ.
- Tên nguồn mặc định `Nostr · npub1mlc…7a0a` (8 ký tự đầu, 4 cuối), người dùng đổi được.

## Đã làm (`infrastructure/social/nostr/`)

- `Bech32` + `NostrKeys`: Bech32 (BIP-173) và NIP-19 `npub`/`note1`; `nsec` nhận diện để chặn.
- `Bip340`: kiểm chữ ký Schnorr secp256k1 bằng `BigInteger`, **chỉ kiểm, không ký, không giữ khóa**.
- `NostrEvent`: đọc chặt chẽ và `isValid`: `kind == 1`, `id` đúng SHA-256 của chuỗi chuẩn hóa NIP-01, chữ ký đúng, `pubkey` đúng `npub` đang theo dõi, hex chữ thường đúng độ dài.
- `NostrRelayClient`: OkHttp WebSocket, 2 relay cố định, `wss://` bắt buộc, timeout 8 giây, khung tối đa 64 KiB (đo theo byte UTF-8), đọc tối đa 50 khung, giữ 20 bài mới nhất, luôn đóng socket; không log.
- `NostrFeedMapper`: bỏ trả lời (thẻ `e`), bỏ trùng, kẹp ngày không quá hiện tại, **thoát HTML** nội dung (`& < > " '`), xuống dòng thành `<br>`, tiêu đề 80 ký tự đầu bằng `titleFromBody`.
- `Feed.NOSTR_SOURCE_TYPE = "NOSTR"` (chỉ chữ, **không đổi cột Room**), nhánh `LocalRssSv.queryFeedArticles` cạnh nhánh WEB, `NostrRelayClient` được Hilt tiêm (`@Singleton @Inject`).
- **Chặn tải web cho nguồn NOSTR:** `RssHelper.parseFullContent` trả rỗng không mở kết nối, `queryRssIcon` bỏ qua (không nhờ favicon), `OfflinePrecacheService` bỏ qua cả toàn văn lẫn ảnh.
- `SubscribeViewModel.search()`: chặn `nsec`, nhận `npub` **trước** `formatUrl()`, "đã đăng ký" kiểm bằng `nostr:<npub chữ thường>`, xem trước qua luồng RSS sẵn có (không dùng `webPreview` vì chuỗi của nó nói về "trang web"); `subscribe()` ép `isFullContent = false` cho nguồn Nostr.

## Kiểm chứng (JVM)

- Full suite `--rerun-tasks`: **3.548 test, 0 bỏ qua, 0 lỗi** (thêm 108 so với 3.440 của FRONT-02). Lần chạy đầu có **1 lỗi** `MirrorServerTest.aClosedStreamFreesItsSlotSoAFourthBrowserCanJoinLater` (`condition not met`) ở mã **không đụng** (không commit Nostr nào sửa mã mirror); chạy riêng 3 lần xanh 19/19 và chạy full lần hai xanh. Test chập chờn về thời gian, **nguyên nhân chưa xác định** (đã gặp một test tương tự ở FRONT-02).
- Test mới: `NostrKeysTest` 20, `Bip340Test` 8 (có đủ **19 vector chính thức** của BIP-340), `NostrEventTest` 20, `NostrRelayClientTest` 20 (MockWebServer3 có WebSocket), `NostrFeedMapperTest` 12, `LocalRssSvNostrSyncTest` 7, `NostrNoWebFetchTest` 4, `SubscribeViewModelNostrTest` 13, `SubscribeViewModelNostrSourceTest` 4. Các lớp cũ (`SubscribeViewModel*`, `LocalRssSv*`, `RetractionRadarSyncTest`, `WebFeedRoomEndToEndTest`, `OfflinePrecacheServiceTest`) xanh.
- Sự kiện mẫu được ký bởi một bộ ký Python **độc lập** (khóa thử lấy từ vector BIP-340, không phải khóa của ai); chuỗi chuẩn hóa NIP-01 và chữ ký được kiểm chéo giữa hai cài đặt. Trước khi code, bộ nguyên mẫu Python cũng đã kiểm id và chữ ký của **120 ghi chú thật** từ hai relay: 120/120 đúng.
- TDD thật: mỗi task có bước đỏ (đọc lý do từ log/XML sau khi `.exit` có mặt).
- Mutation (backup + `cmp`, tệp luôn khôi phục giống hệt): Task 1: 12; Task 2: 17; Task 3: 18; Task 4: 19; Task 5: 22; Task 6: 10; Task 7: 14 đột biến. Mọi đột biến cuối cùng đều bị bắt, **trừ một cái đã chứng minh không thể phân biệt bằng test**: bỏ kiểm `s >= n` trong `Bip340` (cần chữ ký hợp lệ với `s - n` nhỏ hơn 2^129, xác suất khoảng 2^-127); giữ lại cho đúng chuẩn và ghi bằng chú thích `ponytail:`.
- Mutation làm lộ lỗi thật trong code và plan, đều đã sửa kèm test: (1) NIP-01 băm ký tự điều khiển **thô**, không thoát `\u00XX` như bản nháp ghi; (2) `NostrRelayClient` gọi `socket.cancel()` ngay sau `send(CLOSE)` làm rơi khung CLOSE, nên đổi sang đóng lịch sự khi relay đã trả lời; (3) khung quá lớn không được tính vào giới hạn 50 khung; (4) tên nguồn dùng npub chữ hoa khi người dùng dán chữ hoa; (5) một số test yếu đã được siết.
- Lint `lintDevDebug`: 0 lỗi, 2.465 cảnh báo (bằng baseline), **0** vấn đề trong tệp Nostr. `assembleDevDebug assembleDevDebugAndroidTest` thành công. `app/schemas` và `app/src/main/res` không đổi. Hilt/Dagger sinh lại sạch (`kaptDevDebugKotlin --rerun-tasks`).

## Kiểm chứng (Tecno KJ7 `115333744A005844`, 2026-11-14)

- Cổng chỉ đọc: một máy đích (Pixel cũng đang cắm nhưng **không chạm**), không instrumentation, launcher ở foreground, màn hình mở khóa. Sao lưu byte-exact (Room + `-shm` + `-wal`, DataStore 194 byte, `shared_prefs`; integrity ok, 212 bài).
- Helper tạm `NostrRelayProbe` (**không commit**, đã xóa) chạy bằng `am instrument`, **không chạm màn hình**, dựng `OkHttpClient` **sao chép đúng cấu hình `OkHttpClientModule`** (DoH `AppDns`, `ConditionalCacheInterceptor`, `UserAgentInterceptor`, `trustAllCerts`); đây là bản sao, không phải đối tượng Hilt thật. Gọi **relay thật** với một `npub` công khai (ví dụ của chính NIP-19). Kết quả 1/1 OK: **6 ghi chú đã xác minh chữ ký trong 1,15 giây**, 5 bài sau khi bỏ 1 trả lời, 0 tiêu đề rỗng, 5/5 link `njump.me/note1…`. Log chỉ ghi số lượng.
- Khôi phục: Room, `-shm`, `-wal`, DataStore, `shared_prefs` đều `BYTE IDENTICAL`; băm DataStore trên máy `9ca1c202…` trùng bản sao lưu; đã gỡ `com.mckimquyen.reader.test`; cây mã sạch. Máy đang chạy bản dev của HEAD `ddc55161` (có probe đã gỡ khỏi cây mã nhưng bản cài là bản có chứa mã Nostr).

## Chưa kiểm (nói thẳng)

- **Gõ thật `npub` vào ô Thêm nguồn trên màn hình** (không được bấm theo tọa độ); xem trước và đăng ký chỉ kiểm bằng test view model và test nguồn.
- **Mở link `njump.me`** trên máy; trang đọc bài Nostr (nội dung đã thoát HTML hiển thị ra sao trong trình đọc).
- Đồng bộ nền thật qua `SyncWorker` cho một nguồn NOSTR đã đăng ký.
- Hai relay còn lại ngoài `nos.lol`/`relay.primal.net` (đo ngày 2026-11-14: `relay.damus.io` lỗi), tài khoản không có ghi chú, relay chặn bot, mạng yếu.
- Hiệu năng `BigInteger` trên máy yếu (A50s); TalkBack; ngôn ngữ ngoài EN/VI.

## Giới hạn

- Phụ thuộc hai relay công khai; hai relay có thể thiếu bài so với mạng Nostr đầy đủ; không thấy bài đã bị xóa ở relay khác.
- Không tên hồ sơ, ảnh, trả lời, `nprofile`.
- Nút "toàn văn" bị tắt lúc đăng ký; nếu người dùng bật lại trong tùy chọn nguồn thì trang đọc sẽ trống (`parseFullContent` trả rỗng cho NOSTR).
- Đồng bộ LAN (P2P) **bỏ qua** nguồn `nostr:…` vì `FeedUrlNormalizer` chỉ nhận `http/https`; xuất OPML ghi `xmlUrl = nostr:npub…` (app khác không đọc được).
- Thông báo lỗi `nsec` dùng chuỗi "Use a public HTTPS URL…" (không chính xác tuyệt đối).
- Kiểm chữ ký tự viết bằng `BigInteger` chậm hơn thư viện native; đủ cho vài chục ghi chú mỗi lần.
- Các giới hạn (2 relay, 20 bài, 8 giây, 64 KiB, 50 khung) là hằng cố định trong mã; chưa có công tắc hay màn cài đặt cho người dùng.

## Không làm (so với task gốc)

AT Protocol API riêng cho Bluesky (đã dùng RSS), tên hồ sơ/ảnh/trả lời Nostr, đăng nhập, đăng bài, màn cài đặt relay.

## Commits trên `dev`

- `a76ba090` docs: thiết kế · `98414204` docs: kế hoạch
- `ebf1d84d` Bech32 và NIP-19 · `db28f6b8` BIP-340 · `e3d57e2d` event
- `e2bfbb75` relay · `3d081d69` mapper · `d8cd53b2` đồng bộ và chặn tải web
- `ddc55161` ô Thêm nguồn
