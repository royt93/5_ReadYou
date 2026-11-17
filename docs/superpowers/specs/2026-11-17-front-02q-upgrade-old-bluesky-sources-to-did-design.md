# FRONT-02q — Nâng cấp nguồn Bluesky cũ lên lưu DID khi đồng bộ thành công (thiết kế, chờ owner duyệt spec)

**Ngày:** 2026-11-17 · **Nhánh:** `dev` · **Nền:** FRONT-02o (nguồn mới lưu `sourceUrl = bsky:<DID>`), FRONT-02p (menu sửa địa chỉ ghi cả hai cột), `LocalRssSv.queryBlueskyArticles`, `FeedDao`

> Trạng thái: owner đã chốt (2026-11-17): (1) làm việc này; (2) nâng cấp **khi đồng bộ thành công**, dùng DID mà API đã trả về, **không thêm lần gọi mạng**; (3) ghi bằng **một hàm DAO `UPDATE` có điều kiện** (thứ hai sau `queryBySourceUrl` của 02o). Spec chờ owner duyệt trước khi lập plan. Chưa có plan, chưa có mã. **Chỉ JVM, không chạm thiết bị nào** (Pixel đang bận app khác của owner).

## Mục tiêu
FRONT-02o chỉ nhớ DID cho nguồn **đăng ký mới**. Nguồn đăng ký trước đó có `sourceUrl = url = bsky:<handle>`, nên nếu chủ tài khoản đổi handle thì FRONT-02n/02o không nhận ra nó là trùng. Mỗi lần đồng bộ thành công, API đã trả DID; ghi DID đó vào `sourceUrl` của nguồn cũ để thu hẹp giới hạn của 02o, **không cần hỏi mạng thêm**.

## Sự thật đã đọc trong code (2026-11-17)
- `LocalRssSv.queryBlueskyArticles(feed)` (`LocalRssSv.kt:124-142`): lấy `actor = (feed.sourceUrl ?: feed.url).removePrefix("bsky:")`, gọi `blueskyApiClient.fetch(actor)`; chỉ khi `Found` có `found.result.did` (đã được `BlueskyApiClient` kiểm `startsWith("did:") && isActor`). Khi API lỗi, `NotFound` hoặc không có bài thì **ném `IOException`**.
- **Trước giờ đồng bộ chỉ ghi bài.** `syncSingleFeed` ghi một dòng `feed_health_record`; `sync()` ghi bài, thông báo, `updateAt` của tài khoản. **Không có chỗ nào ghi hàng `feed`** từ đồng bộ, ngoại trừ `queryRssIcon` (RSS thường; Bluesky và Nostr **bị loại sớm**, `RssHelper.kt:~330`).
- **`sync()` không ghi `Feed` trong bộ nhớ xuống Room** (đã đọc vòng `sync()`, `AbstractRssRepository.kt:~89-125`: chỉ `insertListIfNotExist` bài, `notify`, `smartCollectionMatcher`). Vì vậy một câu `UPDATE` có điều kiện không bị một lần ghi `Feed` cũ đè lại.
- `sourceUrl` **không hiện ra cho người dùng** (`url` mới hiện ở `FeedOptionDrawer`/`FeedHealthPage`); chỉ `LocalRssSv` (đồng bộ) và `SubscribeViewModel` đọc nó.
- `LocalRssSv` giữ `feedDao` (`private val feedDao: FeedDao`) và truyền cho lớp cha: gọi được hàm DAO mới ngay trong `queryBlueskyArticles`.
- `FeedDao` đã có các `@Query("UPDATE feed SET … WHERE …")` (khuôn có sẵn); 02o thêm `queryBySourceUrl`.
- Menu sửa địa chỉ (02p) ghi `url` và `sourceUrl` bằng `feedDao.update(feed)` (ghi cả hàng).

## Phạm vi trung thực
- **Chỉ nguồn BLUESKY**, và chỉ khi địa chỉ đồng bộ hiện tại **chưa phải DID**: `(feed.sourceUrl ?: feed.url)` không bằng `feedUrl(found.did)`. Khi đã là DID thì **không ghi gì** (không ghi mỗi lần đồng bộ).
- **Chỉ đổi cột `sourceUrl`**: **không** đổi `url`, `name`, `icon`, `groupId`, `isNotification`, `isFullContent`. Đây là điều owner dặn ("một đồng bộ không được đổi tên nguồn"): bản nâng cấp **không đụng** tên hay nhóm.
- **Ghi có điều kiện** (`UPDATE feed SET sourceUrl = :new WHERE id = :id AND sourceUrl IS :old`): nếu người dùng vừa sửa địa chỉ (02p) hoặc cùng lúc có một lần ghi khác thì điều kiện sai và **không ghi gì**. `IS` (không phải `=`) để khớp cả khi `old` là `NULL`.
- **Lỗi ghi không làm hỏng đồng bộ**: bọc `runCatching`, **ném lại `CancellationException`**, lỗi khác chỉ bỏ qua (nâng cấp là phụ, bài vẫn được ghi).
- **Không nâng cấp được nguồn đã đổi handle** trước khi chạm vào nó: API trả `NotFound` cho handle cũ nên đồng bộ đã lỗi, không có DID. Nguồn đó **vẫn trùng được** khi đăng ký lại; ghi vào giới hạn. Chỉ **phòng ngừa** cho nguồn còn đồng bộ được.
- Chỉ tài khoản Local (đường `LocalRssSv`; tài khoản khác không qua nhánh Bluesky).
- Dùng `Log` nhưng **không in handle, DID, URL hay tên** (quy tắc dữ liệu cá nhân của repo); tốt nhất không log gì.
- Không chuỗi, locale, Room schema (**không đổi `app/schemas`**, không migration, không cột mới, không chỉ mục), manifest, `app/build.gradle`, dependency; không Gemini, ad, IAP, VIP. Không đổi `queryNostrArticles`, `SubscribeViewModel`, `FeedOptionVM`.
- Không chạm thiết bị. Nếu sau này cần máy: **chỉ Pixel 7 Pro `2B051FDH3006MU`**.

## Thiết kế
1. **DAO** (`FeedDao.kt`), một hàm mới cạnh `queryBySourceUrl`:
```kotlin
@Query("UPDATE feed SET sourceUrl = :newSourceUrl WHERE id = :id AND sourceUrl IS :expectedSourceUrl")
suspend fun updateSourceUrlIfUnchanged(id: String, expectedSourceUrl: String?, newSourceUrl: String): Int
```
Trả số hàng đổi (`0` hoặc `1`) để test được.
2. **`LocalRssSv.queryBlueskyArticles`**: sau khi có `found` và **sau khi** đã chắc có bài (`articles.isNotEmpty()`, tức đồng bộ thành công), nếu `current = feed.sourceUrl ?: feed.url` khác `target = BlueskyFeedMapper.feedUrl(found.result.did)` thì gọi:
```kotlin
runCatching { feedDao.updateSourceUrlIfUnchanged(feed.id, feed.sourceUrl, target) }
    .onFailure { if (it is CancellationException) throw it }
```
Ghi **sau** kiểm "không có bài" để lần đồng bộ lỗi **không** ghi gì.
3. Không đổi gì khác.

## Kiểm chứng
- **Test DAO** (Room trong bộ nhớ, khuôn `FeedDaoSourceUrlTest`): ghi khi `sourceUrl` khớp; **không ghi** khi `sourceUrl` đã khác (ca người dùng vừa sửa); khớp được khi `sourceUrl` là `NULL` (dùng `IS`); không ghi hàng của feed khác; **không đổi** `url`, `name`, `icon`, `groupId`, `accountId`, `isNotification`, `isFullContent`, `sourceType`; trả `1` hoặc `0` đúng.
- **Test `LocalRssSvBlueskySyncTest`** (mock): nguồn cũ (`url = sourceUrl = handle`) đồng bộ thành công ⇒ gọi `updateSourceUrlIfUnchanged(id, "bsky:<handle>", "bsky:<DID>")` **đúng 1 lần**; nguồn đã lưu DID ⇒ **không gọi**; `sourceUrl == null` (và `url` là handle) ⇒ gọi với `expected = null`; API lỗi, `NotFound`, hoặc không có bài ⇒ **không gọi**; ghi **ném lỗi** ⇒ đồng bộ vẫn trả bài; ghi ném `CancellationException` ⇒ **ném lại**; nguồn không phải Bluesky ⇒ **không gọi**; bài, `name`, `icon` của kết quả **không đổi**.
- **Test đồng bộ thật trên Room trong bộ nhớ** (một ca, không mock DAO): nguồn cũ trong Room, API giả trả `Found` ⇒ sau đồng bộ `sourceUrl` trong Room là DID, `url` vẫn là handle; **kiểm cuối cùng bằng 02n/02o**: dán handle mới (API trả cùng DID) báo "Đã đăng ký" nhờ `isFeedSourceExist`. (Ca xuyên module, nếu khó dựng thì ghi giới hạn.)
- **Mutation JVM** (backup + `cmp`, mẫu độc nhất, script Python): bỏ điều kiện "chưa phải DID" (ghi mỗi lần); bỏ `IS` (dùng `=`); ghi **trước** khi kiểm có bài; ghi `url` thay `sourceUrl`; dùng `feed.url` thay `feed.sourceUrl` làm giá trị chờ; bỏ `runCatching`; nuốt `CancellationException`; ghi cả khi không phải Bluesky. Mỗi mutant phải có test đỏ.
- Full suite `--rerun-tasks` (kỳ vọng 3.776 + test mới, ghi số đo thật), lint 0 lỗi, `assembleDevDebug` thành công; **`app/schemas` không đổi** (kiểm băm `22.json`), `app/src/main/res`, manifest, `app/build.gradle` không đổi.

## Rủi ro đã biết
- **Lần đầu đồng bộ ghi vào bảng `feed` từ nền đồng bộ**, trong khi trước giờ chỉ ghi bài: rủi ro cao hơn các việc trước. Giảm bằng **một cột**, **có điều kiện**, **lỗi không lan**, **ghi một lần** (sau đó điều kiện sai nên không ghi nữa).
- **Hai đồng bộ song song** của cùng nguồn (nhóm 6 nguồn chạy `async`, nhưng mỗi nguồn một lần): ghi trùng giá trị vô hại; điều kiện `IS` chặn ghi lần hai.
- **Một máy chủ độc** trả DID khác của chính nó: `BlueskyApiClient` đã kiểm dạng DID nhưng **không kiểm DID có thuộc về handle**; nguồn bị gắn DID của kẻ khác sẽ đồng bộ **bài của kẻ khác** từ lần sau (vì đồng bộ ưu tiên `sourceUrl`). **Đính chính (2026-11-17, khi lập plan):** khi hỏi owner tôi đã nói rủi ro này là "02o chưa có"; **nói sai**. FRONT-02o đã ghi `sourceUrl = bsky:<DID>` lấy từ chính câu trả lời API cho mọi nguồn **đăng ký mới** (`SubscribeViewModel.kt`), và đồng bộ đọc `sourceUrl` trước `url`, nên **rủi ro đã tồn tại từ 02o** cho nguồn mới. FRONT-02q chỉ **mở rộng cùng một rủi ro sang nguồn cũ**, không tạo loại rủi ro mới. Quyết định chấp nhận của owner vì thế cũng áp cho 02o. Giảm: chỉ nâng cấp khi **bài trả về là của đúng DID đó** (đã đúng: `toArticles(profileDid = found.did)` chỉ giữ bài của DID đó), nhưng vẫn không chứng minh DID ↔ handle. Owner đã chấp nhận (2026-11-17); xem đính chính ở trên.
- Người dùng không thấy `sourceUrl` đổi (không hiện ở giao diện).

## Câu hỏi mở (cần owner trả lời khi duyệt spec)
**Chấp nhận rủi ro "máy chủ độc gắn DID khác" không?** API công khai của Bluesky (`public.api.bsky.app`) là máy chủ của Bluesky, không phải máy chủ do người dùng chọn; kẻ tấn công cần **chiếm hoặc giả mạo** máy chủ đó (hoặc đứng giữa kênh HTTPS đã kiểm chứng chứng chỉ). Rủi ro thấp nhưng khác không. Nếu owner không chấp nhận, phương án thay thế là **không nâng cấp** (giữ nguyên giới hạn của 02o).

## Giới hạn đã biết
- Nguồn cũ **đã đổi handle** thì không nâng cấp được (đồng bộ lỗi), vẫn trùng khi đăng ký lại.
- Chỉ nâng cấp **khi nguồn còn đồng bộ thành công**; nguồn không bao giờ đồng bộ (tắt, lỗi mạng kéo dài) không được nâng cấp.
- OPML, địa chỉ dạng khác, cặp trùng có sẵn: không xử lý.
- Chỉ JVM, chưa kiểm trên máy.
