# FRONT-02 (Theo dõi tài khoản Mastodon và Bluesky bằng RSS công khai, bản thu hẹp)

**Date:** 2026-11-14  
**Branch:** `dev` (không push)  
**Scope:** dán `@tên@máy_chủ`, `@tên.miền` hoặc link hồ sơ Mastodon/Bluesky vào ô **Thêm nguồn** sẵn có; app tự đổi thành địa chỉ RSS công khai, xem trước và đăng ký như mọi nguồn khác. Chỉ đọc, không đăng nhập. Room **giữ v22**, không chuỗi mới, không dependency mới.  
**Spec / plan:** `docs/superpowers/specs/2026-11-14-front-02-social-feeds-design.md`, `docs/superpowers/plans/2026-11-14-front-02-social-feeds.md`.  
**Trạng thái task gốc:** vẫn nằm trong `doc/task/todo/` (owner chọn), vì **Nostr chưa làm**.

## Nói thẳng về phạm vi

- **Nostr chưa làm.** Task gốc nêu ba mạng; làm hai (Mastodon, Bluesky). Nostr không có RSS, cần WebSocket tới relay và giải mã `npub`.
- **Có một lỗi có từ trước, nay đã sửa, áp cho mọi nguồn RSS.** Bài không có `<title>` bị **bỏ im lặng** khi xem trước (`RssHelper.article` gọi `title.toString()` trên giá trị null ⇒ NullPointerException). Mastodon và Bluesky đều trả bài không có tiêu đề, nên không sửa thì tính năng này không có bài nào. Nay tiêu đề lấy 80 ký tự đầu của nội dung (bỏ thẻ HTML, cắt theo ký tự Unicode, thêm `…`).
- **Đường đồng bộ cũng được sửa:** `queryRssXml` từng không có lưới an toàn từng bài, nên theo cách đọc mã một bài hỏng làm cả lần đồng bộ của nguồn rỗng. Nay mỗi bài có `runCatching`, log chỉ ghi **tên lớp** lỗi.
- **Các nguồn RSS cũ đang mất bài vì thiếu tiêu đề sẽ bỗng có bài** (thường là điều tốt, nhưng là đổi hành vi).
- Bluesky RSS chỉ có chữ, khoảng 7 bài gần nhất; Mastodon khoảng 20 bài. Chỉ **tài khoản**, không hashtag, danh sách, `with_replies`, `media`.
- Gõ trần `bsky.app` (không `@`) **không** được nhận là Bluesky, vì không phân biệt được với một website.

## Đã làm

- `infrastructure/social/SocialFeedUrlResolver.kt` (hàm thuần): `@tên@máy_chủ` và `https://máy_chủ/@tên` thành `https://máy_chủ/@tên.rss`; `@tên.miền` và `https://bsky.app/profile/<handle|did>[/rss]` thành `https://bsky.app/profile/<id>/rss`. Loại trừ youtube, youtu.be, medium, substack, tiktok, instagram, x, twitter, threads (cả tên miền con); từ chối `http://`, cổng, `user:pass@`, đoạn đường dẫn thừa, ký tự lạ. Kết quả luôn `https`.
- `RssDescriptionParser.titleFromBody(html, maxLength = 80)` + `RssHelper.article` dùng nó khi tiêu đề trống; bài trống cả hai thì chỉ bài đó bị bỏ.
- `RssHelper.queryRssXml`: `mapNotNull` + `runCatching` từng bài.
- `SubscribeViewModel.search()`: bộ nhận dạng chạy **trước** `formatUrl()` (nếu không `@bsky.app` thành `https://@bsky.app`); "đã đăng ký" kiểm bằng địa chỉ RSS; nếu địa chỉ đoán không đọc được thì **quay về luồng cũ trên chữ gốc người dùng gõ** (`formatUrl`, newsletter, quét web).

## Kiểm chứng (JVM)

- Full suite `--rerun-tasks`: **3.440 test, 0 bỏ qua, 0 lỗi** (thêm 88 so với 3.352 của STRAT-01). Lần chạy đầu có **1 lỗi** `OracleEvidenceViewModelTest.aWalletChange_whileTheCardIsOpen_doesNotSearchAgain` (`Timed out waiting for 5000 ms`), ở tệp FRONT-02 không đụng; chạy riêng thì xanh, chạy full lần hai xanh 3.440/0. Coi là test chập chờn về thời gian, **nguyên nhân chưa xác định**.
- Test mới: `SocialFeedUrlResolverTest` 44, `RssTitleFromBodyTest` 17, `RssHelperNoTitleTest` 12, `SubscribeViewModelSocialTest` 8, `SubscribeViewModelSocialSourceTest` 5. Hai test cũ `SubscribeViewModelWebFeedTest` (7) và `SubscribeViewModelNewsletterTest` (1) vẫn xanh.
- TDD: mỗi task viết test đỏ, đọc lý do từ XML sau khi `.exit` tồn tại, rồi mới sửa mã.
- Mutation (backup + `cmp`): mọi đột biến cuối cùng đều bị bắt. Đột biến sống sót làm lộ test yếu và đã bổ sung: Task 1 (link Bluesky không phải hồ sơ, DID phân biệt hoa/thường, phương thức DID lạ), Task 3 (tiêu đề thay thế phải lấy từ nội dung đã làm sạch, không phải HTML thô), Task 4 (nhánh quay về phải `formatUrl` chữ gốc và không log chữ gốc). Hai chỗ chặn thừa (đột biến tương đương) đã bị gỡ khỏi `titleFromBody`.
- Lint `lintDevDebug`: 0 lỗi, 2.465 cảnh báo (bằng baseline), 0 vấn đề trong tệp FRONT-02. `assembleDevDebug assembleDevDebugAndroidTest` thành công. `app/schemas` không đổi; `app/src/main/res` không đổi.

## Kiểm chứng (Tecno KJ7 `115333744A005844`, 2026-11-14)

- Cổng chỉ đọc: chỉ một máy, không instrumentation, launcher ở foreground, màn hình mở khóa. Sao lưu byte-exact (Room + `-shm` + `-wal`, DataStore 194 byte, `shared_prefs`; integrity ok, 212 bài).
- Helper tạm `SocialFeedsNetworkProbe` (**không commit**, đã xóa) chạy bằng `am instrument`, **không chạm màn hình**, gọi **mạng thật** qua `RssHelper.searchFeed` và `queryRssXml` với địa chỉ do bộ nhận dạng sinh ra. Kết quả: 2/2 OK.
  - `@bsky.app` thành `https://bsky.app/profile/bsky.app/rss`: `searchFeed` 7 bài (qua chuyển hướng 302), `queryRssXml` 7 bài, tiêu đề không rỗng (ví dụ "I heard Bluesky gets all its breaking news from Flavor Flav [contains quote post…").
  - `@Mastodon@mastodon.social` thành `https://mastodon.social/@Mastodon.rss`: `searchFeed` 20 bài, `queryRssXml` 20 bài, tiêu đề không rỗng.
- Probe dùng `OkHttpClient` thường với `followRedirects(true)` (không qua Hilt nên **không có DoH `AppDns`** của app); đường DoH không được kiểm ở đây.
- Khôi phục: Room, `-shm`, `-wal`, DataStore, `shared_prefs` đều `BYTE IDENTICAL`; băm DataStore trên máy `9ca1c202…` trùng bản sao lưu; đã gỡ `com.mckimquyen.reader.test`; cây mã sạch (`git diff --stat -- app/src/main` rỗng). Máy đang chạy bản dev của HEAD `d33bddf6`.

## Phát hiện khi làm

- Cả hai nguồn trả mọi bài **không có `<title>`** (đo bằng Rome 2.1.0); Bluesky chuyển hướng 302 trước khi 200.
- Bài thiếu tiêu đề bị mất im lặng từ trước, không chỉ với mạng xã hội.
- Plan viết test log bằng đường dẫn tài khoản; log có sẵn đã in đường dẫn đã che, nên test được thu hẹp vào nội dung bài và dòng "Skip malformed RSS entry errorType=" (đúng một dòng, không URL, không tài khoản).

## Chưa kiểm (nói thẳng)

- **Gõ thật vào ô Thêm nguồn trên màn hình**: không làm (không được bấm theo tọa độ). Chỉ kiểm bằng test view model, test nguồn và probe mạng thật; chưa thấy màn xem trước và đăng ký trên máy.
- Máy chủ Mastodon khác `mastodon.social`; tài khoản bị khóa hoặc đã xóa; máy chủ chặn bot hoặc tắt RSS.
- Ảnh trong bài Bluesky (RSS không có); TalkBack; ngôn ngữ ngoài EN/VI.
- **Nostr.**
- Đường DoH (`AppDns`) với hai nguồn này.

## Giới hạn

- Phụ thuộc Mastodon và Bluesky giữ RSS công khai; nếu tắt, nguồn báo không đọc được (rơi về luồng cũ).
- Tiêu đề thay thế chỉ là cắt dòng đầu, có thể cắt giữa câu.
- Bluesky vài bài gần nhất, chỉ chữ; không có luồng, trả lời hay ảnh.

## Commits trên `dev`

- `b3ed0549` docs: thiết kế · `e8ba1f1e` docs: kế hoạch
- `cd1e680f` bộ nhận dạng địa chỉ · `376a27dd` tiêu đề từ nội dung
- `701ed95d` giữ bài không tiêu đề và lưới an toàn từng bài
- `d33bddf6` cắm vào ô Thêm nguồn
