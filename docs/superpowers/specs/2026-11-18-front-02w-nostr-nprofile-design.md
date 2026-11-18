# FRONT-02w — Nhận `nprofile` khi thêm nguồn Nostr (thiết kế, chờ owner duyệt spec)

**Ngày:** 2026-11-18 · **Nhánh:** `dev` · **Nền:** FRONT-02b (`npub`), FRONT-02c (tên hồ sơ), `NostrKeys`, `Bech32`, `SubscribeViewModel.search()`

> Trạng thái: owner đã chốt trong brainstorm (2026-11-18): (1) việc đầu tiên trong ba việc Nostr còn lại là **`nprofile`** (ảnh và trả lời để sau); (2) **nới giới hạn độ dài riêng cho `nprofile`** (tối đa 1000 ký tự), `npub` và `note1` giữ 90; (3) **bỏ qua hẳn gợi ý relay** trong `nprofile`, chỉ dùng hai relay cố định; (4) **chỉ nhận `nprofile`** (có thể có tiền tố `nostr:`), **không** nhận link `njump.me/...`. Owner dặn bỏ qua quảng cáo. Spec chờ owner duyệt trước khi lập plan. Chưa có plan, chưa có mã.

## Mục tiêu
Dán `nprofile1…` (hoặc `nostr:nprofile1…`) vào ô **Thêm nguồn** thì app hiểu đó là **cùng một người** như `npub` và chạy đúng luồng `npub` đang chạy (xem trước, "Đã đăng ký" khi trùng, lưu `nostr:<npub>`). Chỉ **đọc** khóa công khai; không đăng nhập, không đăng bài, không giữ khóa riêng. **Không** giao diện mới, **không** chuỗi mới, **không** Room (v22), DataStore, manifest, `app/build.gradle`; không dependency mới; không gọi mạng thêm; không đổi nguồn đã lưu.

## Sự thật đã đọc (2026-11-18)
- `NostrKeys.npubToHex(input)` bỏ tiền tố `nostr:` rồi `Bech32.decode(...)`, chỉ nhận HRP `npub` và payload đúng 32 byte. `Bech32.decode` **từ chối chuỗi dài hơn 90 ký tự** (`MAX_LENGTH = 90`, BIP-173); test `bech32_rejectsValidButOverlongStrings` khóa điều đó cho `npub`.
- NIP-19: `nprofile` là Bech32 của một danh sách **TLV** (loại 1 byte, độ dài 1 byte, giá trị): loại **0** = khóa công khai 32 byte, loại **1** = gợi ý relay (ASCII, có thể lặp). NIP-19 nói chuỗi Bech32 **nên** dài tối đa **5000 ký tự** (không theo giới hạn 90 của BIP-173).
- **Vector chính thức** (đã giải mã và kiểm checksum độc lập bằng Python ngày 2026-11-18): `nprofile1qqsrhuxx8l9ex335q7he0f09aej04zpazpl0ne2cgukyawd24mayt8gpp4mhxue69uhhytnc9e3k7mgpz4mhxue69uhkg6nzv9ejuumpv34kytnrdaksjlyr9p` (**131 ký tự**) giải mã thành khóa `3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d`, relay `wss://r.x.com` và `wss://djbas.sadkb.com`. Đây là bằng chứng rằng `nprofile` thật vượt 90 ký tự.
- `SubscribeViewModel.search()` (khoảng dòng 128 đến 160): chặn `nsec` bằng `isSecretKey(typed)`, rồi (chỉ tài khoản Local) `NostrKeys.npubToHex(typed)`; nếu ra khóa thì dựng `npub` chuẩn, `feedUrl = nostr:<npub>`, kiểm `isFeedExist`, rồi `nostrRelayClient.fetch(authorHex)`. `LocalRssSv.queryNostrArticles` đọc `nostr:<npub>` đã lưu qua `npubToHex` (nguồn đã lưu luôn dạng `npub`, không bao giờ `nprofile`).
- `NostrRelayClient.fetch(authorHex)` chỉ nhận khóa hex 64 ký tự; **không có đường nào truyền địa chỉ relay vào** (hai relay cố định `wss://nos.lol`, `wss://relay.primal.net`).
- `SubscribeViewModelNostrSourceTest` là test **đọc mã nguồn** (chuỗi `npubToHex(` làm điểm neo trong `search()`): phải được cập nhật nếu đổi tên lời gọi.
- Máy: không cần. Toàn bộ là mã thuần (Bech32, NIP-19, ViewModel) kiểm bằng JVM.

## Phạm vi trung thực
- **Làm:** (a) `Bech32.decode` nhận độ dài tối đa tùy chọn (mặc định **90**); (b) `NostrKeys` đọc `nprofile` (TLV) và trả khóa hex; (c) một hàm chung "khóa từ ô nhập" nhận `npub` **hoặc** `nprofile`, dùng trong `SubscribeViewModel.search()`; (d) `isSecretKey` vẫn chặn trước mọi đường (kể cả `nostr:nsec…`).
- **Không làm:** gợi ý relay (đọc để **bỏ qua**, không dùng, không lưu, không log); `nevent`, `naddr`, `note1` như nguồn; link `njump.me/...` hay URL dạng khác; ảnh, trả lời; đăng nhập, đăng bài; sửa nguồn Nostr đã lưu; đổi `LocalRssSv.queryNostrArticles` (nguồn lưu luôn là `npub`).
- **Không kiểm trên máy:** không đụng thiết bị; không mạng thật. Không kiểm "dán thật vào ô trên màn hình".

## Thiết kế
1. **`Bech32.decode(text, maxLength = DEFAULT_MAX_LENGTH)`** (`internal`, 90 mặc định). Chỉ đổi dòng kiểm độ dài; mọi kiểm khác (ký tự in được, hoa/thường lẫn lộn, checksum, đệm bit) giữ nguyên. `npub`, `note1` gọi như cũ (mặc định 90).
2. **`NostrKeys`:** hằng số có tên `NPROFILE_HRP = "nprofile"`, `NPROFILE_MAX_LENGTH = 1000`, `TLV_TYPE_PUBKEY = 0`, `TLV_TYPE_RELAY = 1`. Hàm `nprofileToHex(input: String): String?`: bỏ tiền tố `nostr:` (dùng lại `withoutUriPrefix`), `Bech32.decode(…, NPROFILE_MAX_LENGTH)`, HRP phải là `nprofile`, đọc TLV từ đầu đến cuối **không bỏ sót byte**: mỗi mục cần đủ 2 byte tiêu đề và đủ độ dài; loại 0 phải **đúng 32 byte** và **chỉ một** khóa (hai khóa, dù trùng hay khác nhau, → `null`); loại 1 và loại lạ **được đọc và bỏ qua** (không dùng giá trị); TLV cụt → `null`; không có khóa → `null`. Trả hex chữ thường 64 ký tự.
3. **`NostrKeys.authorHexFrom(input: String): String?`** = `npubToHex(input) ?: nprofileToHex(input)`. `SubscribeViewModel.search()` gọi hàm này thay cho `npubToHex`; phần còn lại (dựng `npub` chuẩn từ hex, `feedUrl`, `isFeedExist`, `fetch`) **không đổi**, nên `nprofile` và `npub` của cùng một người đi vào **cùng** `nostr:<npub>`.
4. **An toàn:** `isSecretKey` giữ nguyên và vẫn chạy **trước** mọi bộ phân giải; `nprofile` không chứa khóa bí mật nhưng người dán có thể dán nhầm, nên không thêm đường nào bỏ qua kiểm đó. Không log khóa hay relay.
5. **Cập nhật test đọc mã nguồn:** `SubscribeViewModelNostrSourceTest` dùng `npubToHex(` làm điểm neo; đổi neo thành `authorHexFrom(` (và kiểm rõ rằng thứ tự "trước `formatUrl()`", "chặn `nsec` trước mọi bộ phân giải", "lỗi Nostr không rơi sang web" vẫn đúng).

## Kiểm chứng
- **JVM (không máy):** `NostrKeysTest` (hoặc tệp `NostrNprofileTest` mới) thêm: vector NIP-19 chính thức (131 ký tự) → khóa `3bf0c63f…`; cùng khóa mã hóa thành `npub` và `nprofile` đều ra cùng hex; `nprofile` không relay (chỉ TLV loại 0); tiền tố `nostr:` và `NOSTR:` hoa thường; chữ hoa toàn bộ; khoảng trắng đầu cuối; loại 0 không đúng 32 byte → `null`; hai khóa (cùng hoặc khác nhau) → `null`; không có khóa (chỉ relay) → `null`; TLV cụt (độ dài vượt phần còn lại, thiếu byte độ dài) → `null`; loại lạ giữa các mục được bỏ qua; chuỗi dài hơn 1000 → `null`; `nprofile` 91 đến 1000 ký tự được nhận; **`npub` dài hơn 90 vẫn bị từ chối** (không nới nhầm cho `npub`); HRP khác (`npub`, `nevent`, `naddr`, `nsec`) đưa vào `nprofileToHex` → `null`; `isSecretKey("nostr:nsec1…")` vẫn đúng. `Bech32Test`: giới hạn tùy chỉnh (chuỗi 91 ký tự bị 90 từ chối, bị 1000 chấp nhận).
- **`SubscribeViewModel` (JVM, như các test hiện có):** dán `nprofile` → xem trước đúng như `npub` (cùng `nostr:<npub>`); dán `nprofile` của người đã đăng ký bằng `npub` → "Đã đăng ký" **không** gọi relay; `nostrRelayClient.fetch` chỉ nhận **hex** (không nhận chuỗi relay nào trong `nprofile`: kiểm bằng test có `nprofile` mang relay lạ và xác nhận lời gọi `fetch` chỉ có một tham số hex).
- **Đột biến JVM** (đổi tạm, chạy lớp test liên quan, khôi phục, `cmp`): (n1) nới giới hạn cho **mọi** HRP → test "`npub` 91 ký tự bị từ chối" đỏ; (n2) bỏ kiểm "khóa đúng 32 byte" → test độ dài đỏ; (n3) cho phép hai khóa → test hai khóa đỏ; (n4) dừng đọc TLV sau mục đầu → test loại lạ giữa các mục / cụt đỏ; (n5) nhận khóa khi TLV cụt (không kiểm byte thừa) → test cụt đỏ; (n6) `authorHexFrom` chỉ gọi `npubToHex` → test `nprofile` đỏ; (n7) bỏ tiền tố `nostr:` → test tiền tố đỏ; (n8) dùng giá trị relay (thử gọi `fetch` với relay) không cần vì không có đường; thay bằng kiểm cấu trúc: `NostrRelayClient.fetch` vẫn chỉ có tham số `authorHex: String`. Mỗi đột biến phải làm **đúng** test dự định đỏ (so sánh tự động).
- JVM full suite đo lại (đỏ chập chờn: chạy riêng 3 lần + chạy lại cả bộ, ghi thẳng); `assembleDevDebug assembleDevDebugAndroidTest` thành công; lint không quét `androidTest` (không đổi); `StringResourceParityTest` không đổi (không chuỗi mới).
- Báo cáo `doc/task/done/FRONT-02w_nostr-nprofile_DONE.md`; cập nhật `doc/feature.md` (dòng FRONT-02b/02c ghi "nhận thêm `nprofile`" và dòng 02w mới); task gốc FRONT-02 vẫn ở `todo/` (còn ảnh, trả lời).

## Rủi ro đã biết
- **Sửa `Bech32.decode` ảnh hưởng mọi `npub`/`note1`:** tham số có mặc định 90 và test khóa hành vi cũ; chạy lại toàn bộ test Nostr và đo JVM đầy đủ.
- **TLV là định dạng tự do:** loại lạ phải bỏ qua (NIP-19 cho phép mở rộng), nhưng độ dài tổng phải khớp chính xác; từ chối khi sai để không đọc nhầm dữ liệu.
- **`nprofile` giả có khóa đúng nhưng người dùng không biết là ai:** giống `npub`; không có kiểm danh tính (app không xác minh người, chỉ đọc bài ký đúng khóa đó, như 02b).
- **Gợi ý relay bị bỏ qua:** người dán `nprofile` của người chỉ ở relay khác sẽ thấy xem trước trống (đúng như `npub` hiện nay); giới hạn đã biết, không phải lỗi.
- **Test đọc mã nguồn dễ vỡ khi đổi tên:** đã nêu ở thiết kế mục 5.

## Giới hạn đã biết
- Chỉ JVM; không kiểm trên màn hình hay máy thật.
- Không link `njump.me/...`, không `nevent`/`naddr`, không ảnh, không trả lời.

## Điều chỉnh khi thực thi
- **`Bech32.MAX_LENGTH` đổi thành `DEFAULT_MAX_LENGTH`** (công khai trong `internal object`) để test và đột biến trỏ vào một chỗ.
- **Test neo:** `SubscribeViewModelNostrSourceTest` có 4 chỗ dùng `npubToHex(` (không phải 6); neo đổi sang `authorHexFrom(` và thêm test `theNostrAnchorExists` vì `substringAfter` trả nguyên chuỗi khi mất neo (test cũ xanh giả).
- **Đột biến n8 của spec** (nhận relay) đổi thành kiểm cấu trúc: `NostrRelayClient.fetch` chỉ có một tham số `String`; đột biến `n10` chứng minh test đó không xanh giả. Thêm `n13`, `n16`.
- **Dự đoán tập test đỏ sai 4 chỗ** (n1, n3, n16; n11 chứa thêm một test đọc mã nguồn): đã đọc lý do và sửa bảng; không có đột biến sống sót.
- **Kết quả:** 25 test mới, mutation 13/13, JVM 3.822 test, 0 lỗi; đúng ba tệp production.
