# FRONT-02o — Nhớ DID của nguồn Bluesky mới để chặn đăng ký trùng cả khi chủ tài khoản đổi handle (thiết kế, chờ owner duyệt spec)

**Ngày:** 2026-11-17 · **Nhánh:** `dev` · **Nền:** FRONT-02n (`otherNameAddresses`, khối kiểm sau `Found`), FRONT-02g (`queryBlueskyArticles` đọc `sourceUrl ?: url`), `Feed.sourceUrl`, `FeedDao.queryByLink`

> Trạng thái: owner đã chốt (2026-11-17): làm việc này, hướng dữ liệu **đổi `sourceUrl` của nguồn Bluesky MỚI sang dạng DID (`bsky:did:plc:…`), giữ `url` là handle**. Spec chờ owner duyệt trước khi lập plan. Chưa có plan, chưa có mã. **Chỉ JVM, không chạm thiết bị nào** (Pixel đang bận app khác của owner).

## Mục tiêu
FRONT-02n chặn trùng handle với DID khi API trả lời, nhưng **không** chặn khi chủ tài khoản đã đổi handle sau khi đăng ký: nguồn cũ lưu handle cũ `A`, API nay trả handle mới `B` và DID `D`; hai địa chỉ kiểm `{D, B}` không khớp nguồn cũ (lưu `A`). Nếu nguồn mới lưu **DID** ở `sourceUrl` thì sau khi đổi handle, DID vẫn khớp.

## Sự thật đã đọc trong code (2026-11-17)
- Nguồn Bluesky mới hiện lưu `url = bsky:<tên gõ>` **và** `sourceUrl = bsky:<tên gõ>` (hai cột cùng giá trị; `SubscribeViewModel.kt:~273`).
- Đồng bộ: `LocalRssSv.queryBlueskyArticles` đọc `(feed.sourceUrl ?: feed.url).removePrefix("bsky:")` rồi `blueskyApiClient.fetch(actor)`. `BlueskyApiClient.read` gọi `getProfile` rồi `getAuthorFeed` bằng **DID**, nên fetch bằng DID chạy được; test `LocalRssSvBlueskySyncTest` (dòng 79) đã có ca `url`/`sourceUrl` dạng DID.
- `url` hiện ra cho người dùng ở `FeedOptionDrawer` (dòng 94, 123: hiện và mở link) và `FeedHealthPage` (dòng 168); `FeedOptionVM.changeFeedUrl` **chỉ đổi `url`**, giữ nguyên `sourceUrl`. Vì đồng bộ ưu tiên `sourceUrl`, **sửa địa chỉ trong menu không đổi nguồn đang được đồng bộ** (tật có sẵn với nguồn Bluesky, không do spec này gây ra, nhưng spec này làm nó rõ hơn: `url` handle, `sourceUrl` DID).
- `isFeedExist(url)` = `feedDao.queryByLink(accountId, url)` (so `url`, đúng từng ký tự). **Không có truy vấn theo `sourceUrl`**: phải thêm một hàm DAO.
- Cột `sourceUrl` đã có từ migration cũ (`ALTER TABLE feed ADD COLUMN sourceUrl`); **không cần cột mới, không migration, không đổi schema JSON**.
- `Feed.equals/hashCode` có `sourceUrl`.

## Phạm vi trung thực
- **Chỉ nguồn đăng ký MỚI** (sau thay đổi này) mới có DID ở `sourceUrl`. **Nguồn cũ không được sửa, không tự nâng cấp** (không migration dữ liệu): nguồn cũ đã đổi handle vẫn **không** chặn được. Nói thẳng trong báo cáo: giới hạn của 02n chỉ thu hẹp dần theo thời gian, không biến mất ngay.
- **Hàm DAO mới** `queryBySourceUrl(accountId, sourceUrl)` là **một câu truy vấn**, không đổi bảng, không đổi `app/schemas`. **Cần owner xác nhận**: owner từng dặn "không Room mới"; spec hiểu là không đổi schema/migration; nếu owner hiểu rộng hơn (không thêm cả hàm DAO) thì phương án là đọc cả danh sách nguồn qua hàm có sẵn rồi lọc trong bộ nhớ (chậm hơn với nhiều nguồn, không đụng DAO).
- Dùng chuỗi **sẵn có** `already_subscribed`; không chuỗi, locale, manifest, dependency; không Gemini, ad, IAP, VIP.
- Chỉ tài khoản **Local** (như nhánh Bluesky hiện tại).
- Không sửa `changeFeedUrl` hay giao diện sửa địa chỉ (ngoài phạm vi); ghi vào giới hạn.
- Không chạm thiết bị. Nếu sau này cần máy: **chỉ Pixel 7 Pro `2B051FDH3006MU`**.

## Thiết kế
1. **Lưu DID khi đăng ký** (`SubscribeViewModel.subscribe`/chỗ dựng `Feed` ở nhánh `Found`): `url = feedUrl` (tên gõ, như cũ), **`sourceUrl = BlueskyFeedMapper.feedUrl(found.did)`** (DID, chữ hoa/thường giữ nguyên). Thấy rõ ở `state.feed`, nên test cũ khẳng định `sourceUrl == "bsky:alice.bsky.social"` sẽ **đổi nghĩa** (nay là DID); đọc từng test, đổi dữ liệu kỳ vọng, không nới thiết kế.
2. **Đồng bộ không đổi:** vẫn `sourceUrl ?: url` (có sẵn test DID); không sửa mã đồng bộ.
3. **Chặn theo DID đã lưu:** thêm `FeedDao.queryBySourceUrl(accountId, sourceUrl): List<Feed>` và `AbstractRssRepository.isFeedSourceExist(sourceUrl): Boolean` (cùng cách `isFeedExist`). Trong khối kiểm sau `Found` của 02n: ngoài danh sách `otherNameAddresses` (kiểm `url`), hỏi thêm `isFeedSourceExist(feedUrl(found.did))`. Có `true` thì báo "Đã đăng ký". Kiểm này **chạy cả khi tên gõ là DID** (nguồn cũ lưu DID ở `url` đã được kiểm trước API, nên trùng lặp vô hại, không hỏi thêm mạng).
4. **Không đổi** khối 02i (trước API) và nhánh Nostr.

## Kiểm chứng
- **Test hàm DAO / repository** (Robolectric, Room trong bộ nhớ, đã có khuôn trong repo): `queryBySourceUrl` trả đúng nguồn của **đúng tài khoản**, không trả nguồn tài khoản khác, không khớp từng phần.
- **Test `SubscribeViewModelBlueskyTest`** (mock): đăng ký bằng handle ⇒ `state.feed.url` là handle và `sourceUrl` là DID; đã có nguồn mới lưu `sourceUrl = DID` nhưng `url` là handle **cũ** ⇒ gõ **handle mới** (API trả cùng DID) báo "Đã đăng ký" **dù `url` không khớp** (ca mục tiêu); nguồn cũ (`sourceUrl = url = handle cũ`) ⇒ **không** chặn (giới hạn, có test ghi lại); DID khác ⇒ không chặn.
- **Test đồng bộ có sẵn** (`LocalRssSvBlueskySyncTest`) xanh nguyên vẹn: đồng bộ với `sourceUrl` DID chạy.
- **Mutation JVM** (backup + `cmp`, mẫu độc nhất): bỏ lưu DID (về `feedUrl(tên gõ)`); bỏ kiểm `isFeedSourceExist`; kiểm sai cột (`url` thay `sourceUrl`); bỏ lọc tài khoản trong truy vấn (mutant ở DAO, phải có test DAO bắt).
- Full suite `--rerun-tasks` (kỳ vọng 3.751 + test mới, ghi số đo thật), lint 0 lỗi, `assembleDevDebug` thành công; **`app/schemas` không đổi**, `app/src/main/res`, manifest, `app/build.gradle` không đổi.

## Rủi ro đã biết
- **Hai cột lệch nhau có chủ ý** (`url` handle, `sourceUrl` DID): người sửa địa chỉ trong menu chỉ đổi `url` nên không đổi nguồn đồng bộ (tật có sẵn). Báo cáo ghi rõ; không sửa ở đây.
- Nguồn Bluesky mới sẽ được đồng bộ bằng DID: DID bền hơn handle, nhưng nếu tài khoản bị xóa hoặc chuyển nhà cung cấp (`did:web`) thì API trả NotFound như với handle; không mới.
- **OPML**: xuất/nhập OPML dùng `url`; `sourceUrl` không đi theo, nên nguồn nhập từ OPML không có DID (nằm trong giới hạn "nguồn cũ").
- Đổi nghĩa `sourceUrl` của nguồn Bluesky mới có thể làm một test khác (androidTest `BackgroundSyncRig` tự dựng nguồn với `sourceUrl` handle) lệch với dữ liệu thật; không đổi test thiết bị (không chạm máy), ghi vào giới hạn.

## Giới hạn đã biết
- Nguồn cũ không được nâng cấp: ca đổi handle của nguồn cũ vẫn trùng.
- API lỗi hoặc `Found` rỗng (rơi về RSS): vẫn không chặn.
- OPML, địa chỉ dạng khác, cặp trùng có sẵn: không xử lý.
- Một máy chủ độc trả DID giả chỉ có thể chặn một lần đăng ký (đã thế ở 02n).
- Chỉ JVM, chưa kiểm trên máy.
