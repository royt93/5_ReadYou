# FRONT-02n — Chặn đăng ký trùng một tài khoản Bluesky khi một bên gõ handle, một bên gõ DID (thiết kế, chờ owner duyệt spec)

**Ngày:** 2026-11-16 · **Nhánh:** `dev` · **Nền:** FRONT-02i (`SubscribeViewModel.search()`, `BlueskyFeedMapper.feedUrl/rssUrl`), FRONT-02g (`BlueskyApiClient`, `BlueskyFetchResult`)

> Trạng thái: owner đã chốt (2026-11-16): làm việc này (việc không cần Pixel) và cách làm: **kiểm SAU khi API trả lời, đủ 4 địa chỉ** (DID và handle chuẩn, mỗi tên cả dạng API và RSS). Spec chờ owner duyệt trước khi lập plan. Chưa có plan, chưa có mã. **Chỉ JVM, không chạm thiết bị nào** (Pixel đang bận app khác của owner).

## Mục tiêu
FRONT-02i chặn trùng **cùng dạng tên** (handle với handle, DID với DID) và để lại một giới hạn: một người đã đăng ký bằng handle `alice.bsky.social`, rồi dán `did:plc:abc…` của chính người đó (hoặc ngược lại) vẫn đăng ký được lần hai. Lần này API đã được gọi nên **biết cả hai tên của cùng một người** (`BlueskyFetchResult.did` và `.handle`): dùng chúng để kiểm các địa chỉ còn lại, **không thêm lần gọi mạng nào**.

## Sự thật đã đọc trong code (2026-11-16)
- `SubscribeViewModel.search()` nhánh Bluesky: (1) kiểm `isFeedExist(feedUrl)` và `isFeedExist(oldRssUrl)` **trước** khi gọi API (02i); (2) gọi `blueskyApiClient.fetch(blueskyActor)`; (3) `NotFound` → lỗi, **không** rơi về RSS; (4) `Found` với có bài → xem trước, **`feed.url = feedUrl`** tức địa chỉ theo **tên người dùng đã gõ**; (5) `Found` rỗng hoặc `Failed` → rơi về luồng RSS cũ.
- `BlueskyApiClient.read(actor)`: `getProfile` rồi **`did = profile.optString("did")` chỉ nhận khi `startsWith("did:")` và `BlueskyFeedMapper.isActor(did)`**, sai thì `Failed`. Sau đó `getAuthorFeed` bằng **DID** (không dùng tên đã gõ). `handle = profile.optString("handle")`: **lấy thẳng từ máy chủ, không kiểm**, có thể là chuỗi rỗng hoặc `handle.invalid` (Bluesky dùng giá trị này cho tài khoản mà handle không còn xác minh được) hoặc chuỗi lạ.
- `BlueskyFeedMapper.feedUrl(actor)` và `rssUrl(actor)`: handle chữ thường, DID giữ nguyên; `isActor(text)` = regex `^[A-Za-z0-9][A-Za-z0-9:._-]{0,252}$`. `actorFromRssUrl`.
- Nguồn đã lưu ở hai dạng: `bsky:<tên>` (API) và `https://bsky.app/profile/<tên>/rss` (RSS cũ), với `<tên>` là **đúng tên người dùng đã gõ lúc đăng ký** (handle chữ thường hoặc DID).
- `isFeedExist(url)` so **đúng bằng** chuỗi, phân biệt hoa thường, trong tài khoản hiện tại.

## Phạm vi trung thực
- Chặn **hai chiều** handle với DID của cùng một người, trong cả hai dạng lưu (API và RSS cũ). **Chỉ khi API trả `Found`** (có đủ cả hai tên). Nếu API **lỗi** hoặc trả `Found` rỗng và rơi về RSS thì **không** chặn được handle với DID (không có tên thứ hai): ghi vào giới hạn.
- Bây giờ người dùng thấy "Đang tìm" rồi mới thấy "Đã đăng ký" ở ca handle với DID (khác 02i, nơi báo trước khi gọi API): đã chọn, vì cách duy nhất biết hai tên là gọi API (owner không muốn thêm lần gọi riêng).
- **Handle từ máy chủ không đáng tin:** chỉ dùng khi `isActor(handle)` đúng **và** không bắt đầu bằng `did:` **và** không phải `handle.invalid`. Nếu không hợp lệ thì chỉ kiểm theo DID (bỏ qua handle).
- Không đổi mã production ngoài `SubscribeViewModel.search()` (nhánh Bluesky); có thể thêm **một hàm thuần nhỏ** vào `BlueskyFeedMapper` trả danh sách địa chỉ cần kiểm từ `(actor đã gõ, did, handle)`. Dùng chuỗi **sẵn có** `already_subscribed`: **không** chuỗi mới, locale, Room, DataStore, manifest, dependency; không Gemini, ad, IAP, VIP.
- Không chặn: nguồn RSS từ OPML hoặc địa chỉ dạng khác (02i đã ghi), cặp trùng đã có sẵn, nguồn đã đổi handle sau khi đăng ký (nguồn cũ lưu handle cũ, người đó đổi handle thì handle mới **không** khớp, nhưng DID vẫn khớp nếu nguồn cũ lưu DID).
- **Hạn chế quan trọng:** một nguồn cũ lưu bằng handle `A`, rồi chủ tài khoản **đổi handle sang `B`**: API trả `handle = B`, `did = D`. Kiểm 4 địa chỉ `{D, B}` × `{API, RSS}` **không** thấy nguồn cũ (lưu `A`). Spec này **không** giải quyết ca đó (cần lưu DID lúc đăng ký, là thay đổi dữ liệu, ngoài phạm vi); ghi vào giới hạn.
- Không chạm thiết bị. Nếu sau này cần máy: **chỉ Pixel 7 Pro `2B051FDH3006MU`**.

## Thiết kế
Một hàm thuần trong `BlueskyFeedMapper` (tên đề nghị `duplicateAddresses`), ví dụ:

```kotlin
/** Every address under which an already stored source of this account could look: both names, both forms. The typed form is excluded (already checked). */
fun otherNameAddresses(typedActor: String, did: String, handle: String): List<String>
```
- Đầu vào: tên đã gõ (`blueskyActor`), `did` và `handle` từ `Found`.
- Chuẩn hóa mỗi tên bằng đúng cách của `feedUrl`/`rssUrl` (handle chữ thường, DID giữ nguyên); chỉ nhận `handle` khi hợp lệ (xem Phạm vi); bỏ **tên trùng với tên đã gõ** (đã kiểm ở 02i); trả `feedUrl(name)` và `rssUrl(name)` của các tên còn lại, không trùng lặp.
- Trong `search()`: ngay sau khi `outcome is Found` và **trước khi** dựng `Feed`/xem trước, hỏi `rssService.get().isFeedExist(address)` cho từng địa chỉ trả về; có **bất kỳ** cái `true` thì báo "Đã đăng ký" (đúng chuỗi, `lockLinkInput = false`, `title` như 02i) và `return@launch`. Không gọi `isFeedExist` khi không cần (không có tên thứ hai hợp lệ).
- Không đổi thứ tự hay hành vi của 02i (kiểm hai địa chỉ **trước** khi gọi API giữ nguyên).

## Kiểm chứng
- **Test hàm thuần** (JVM, `BlueskyFeedMapperTest`): handle gõ ⇒ trả địa chỉ của DID (API và RSS); DID gõ ⇒ trả địa chỉ của handle chữ thường; handle hoa thường lẫn lộn ⇒ chuẩn hóa chữ thường; handle `handle.invalid`, rỗng, bắt đầu bằng `did:` hoặc không hợp lệ ⇒ bỏ qua (chỉ còn tên DID, hoặc rỗng khi tên gõ đã là DID); tên trùng tên gõ ⇒ không lặp; DID chữ hoa/thường được giữ.
- **Test `SubscribeViewModelBlueskyTest`** (khuôn có sẵn, `mockk`): đã có nguồn lưu theo **DID** (API) ⇒ dán **handle** báo "Đã đăng ký" sau khi gọi API đúng 1 lần, **không** thêm nguồn; đã có nguồn lưu theo **DID** ở dạng **RSS** ⇒ như trên; đã có nguồn lưu theo **handle** ⇒ dán **DID** báo "Đã đăng ký"; handle `handle.invalid` ⇒ không dùng handle, chỉ kiểm theo DID; **không** có nguồn nào ⇒ vẫn đăng ký bình thường (ca xanh); API **lỗi** ⇒ rơi về RSS như cũ, **không** chặn handle với DID (nói rõ đây là giới hạn, có test ghi lại hành vi); số lần gọi `isFeedExist` đúng như kỳ vọng (không hỏi thừa).
- Các test hiện có của 02i (cùng dạng tên, không gọi API) và `SubscribeViewModelSocialTest`, `SubscribeViewModelNostrTest` phải xanh **nguyên vẹn**.
- **Mutation JVM** (backup + `cmp`, mỗi mẫu `old` độc nhất): bỏ kiểm DID; bỏ kiểm handle; bỏ dạng RSS; bỏ lọc `handle.invalid`; không chuẩn hóa chữ thường; kiểm **trước** khi gọi API (sai vị trí); bỏ lọc tên trùng tên gõ. Mỗi mutant phải có test đỏ.
- Full suite `--rerun-tasks` (kỳ vọng 3.731 + test mới, ghi số đo thật cùng cách như 02g đến 02m), lint 0 lỗi, `assembleDevDebug` thành công; `app/schemas`, `app/src/main/res`, manifest, `app/build.gradle` không đổi.

## Rủi ro đã biết
- **Handle không đáng tin** (máy chủ trả lời): ví dụ trả handle của người khác để làm người dùng không đăng ký được tài khoản thật. Mức độ thấp (chỉ chặn đăng ký, không làm hại dữ liệu), nhưng vẫn là lý do phải kiểm bằng `isActor` và `handle.invalid`; ghi vào giới hạn: **một máy chủ độc có thể chặn một lần đăng ký**.
- Người dùng thấy "Đang tìm" rồi mới báo trùng (độ trễ một lần gọi API).
- Hai nguồn cũ có thể đã trùng từ trước: không xử lý.

## Giới hạn đã biết
- Không chặn khi API lỗi hoặc `Found` rỗng (rơi về RSS).
- Không chặn nguồn đã lưu theo handle **cũ** khi chủ tài khoản đã đổi handle.
- Không chặn OPML hay dạng địa chỉ khác.
- Không gộp hay dọn cặp trùng có sẵn.
- Chỉ JVM, chưa kiểm trên máy.
