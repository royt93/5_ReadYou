# FRONT-02p — Sửa địa chỉ nguồn trong menu phải đổi cả địa chỉ đồng bộ (thiết kế, chờ owner duyệt spec)

**Ngày:** 2026-11-17 · **Nhánh:** `dev` · **Nền:** FRONT-02o (nguồn Bluesky mới: `url` = handle, `sourceUrl` = DID), `FeedOptionVM.changeFeedUrl`, `ChangeUrlDialog`, `LocalRssSv.queryFeedArticles`

> Trạng thái: owner đã chốt (2026-11-17): làm việc này, **phạm vi cả ba loại nguồn có `sourceUrl` (WEB, NOSTR, BLUESKY)**, cách xử lý **đổi cả `url` lẫn `sourceUrl`**. Spec chờ owner duyệt trước khi lập plan. Chưa có plan, chưa có mã. **Chỉ JVM, không chạm thiết bị nào.**

## Mục tiêu
`FeedOptionViewModel.changeFeedUrl()` chỉ ghi `it.copy(url = newUrl)`; mọi đồng bộ của nguồn WEB, NOSTR, BLUESKY đọc **`sourceUrl ?: url`** (`LocalRssSv.kt:80,108,125`) nên **sửa địa chỉ trong menu không đổi nguồn đang được đồng bộ**. Sau 02o, `url` và `sourceUrl` của nguồn Bluesky mới còn khác nhau (handle, DID) nên tật này dễ thấy hơn. Mục tiêu: sửa địa chỉ thì đồng bộ đi theo địa chỉ mới.

## Sự thật đã đọc trong code (2026-11-17)
- `changeFeedUrl()` (`FeedOptionVM.kt:~213`): `rssService.get().updateFeed(it.copy(url = _feedOptionUiState.value.newUrl))`. Không đụng `sourceUrl`.
- Hộp thoại mở bằng **nhấn giữ** dòng địa chỉ trong `FeedOptionDrawer`, và **chỉ khi `rssService.get().update`** (mặc định `true` cho Local; `false` cho Fever và Google Reader). `onFeedUrlClick` thì **mở** `feed.url` trong trình duyệt.
- `ChangeUrlDialog` (`ui/component/ChangeUrlDlg.kt`) nhận chuỗi tự do; **không kiểm** địa chỉ.
- `sourceUrl`: nguồn RSS thường **không có** (`null`); WEB (`sourceUrl = link`, `SubscribeViewModel.kt:~370`), NOSTR (`sourceUrl = feedUrl`), BLUESKY (`sourceUrl = bsky:<DID>` từ 02o, hoặc `bsky:<tên>` với nguồn cũ).
- Đồng bộ: `queryBlueskyArticles` và `queryNostrArticles` ném `IOException` khi địa chỉ không phải tài khoản hợp lệ (`feed.sourceUrl ?: feed.url` bỏ tiền tố rồi gọi API/giải mã npub); WEB gọi `webFeedScraper.refresh(sourceUrl ?: url)`.
- Không có test nào gọi `changeFeedUrl` hoặc `FeedOptionViewModel` (đã `grep` ở `app/src/test`).
- **Hệ quả cần nói thẳng:** hiện tại, với Bluesky và Nostr, sửa địa chỉ **không có tác dụng gì lên đồng bộ**, nên một địa chỉ gõ sai (ví dụ dán một link web vào nguồn Bluesky) **không phá đồng bộ**. Sau khi sửa theo ý owner, địa chỉ sai **sẽ làm đồng bộ của nguồn đó lỗi** (báo lỗi đồng bộ, không ghi bài, không mất bài cũ).

## Phạm vi trung thực
- Chỉ đổi `FeedOptionViewModel.changeFeedUrl()`. **Quy tắc:** nếu `feed.sourceUrl != null` thì ghi **`sourceUrl = newUrl`** cùng với `url = newUrl`; nếu `sourceUrl == null` (RSS thường) thì **không đổi hành vi** (chỉ `url`). Áp dụng đồng đều cho WEB, NOSTR, BLUESKY vì cả ba có `sourceUrl`.
- **Bluesky mới (url handle, sourceUrl DID):** sửa địa chỉ **thay cả hai bằng địa chỉ mới**; **mất DID** đã nhớ (người dùng chủ động chuyển nguồn sang địa chỉ khác, DID cũ không còn đúng). Hệ quả: nguồn đó **không còn được 02o nhận ra theo DID** cho tới khi đăng ký lại; ghi vào giới hạn.
- **Không** thêm kiểm tra địa chỉ trong hộp thoại (đó là quyết định giao diện và có thể cần chuỗi báo lỗi mới; owner đã dặn không chuỗi mới). Địa chỉ sai ở Bluesky/Nostr dẫn tới lỗi đồng bộ, như nguồn WEB hiện nay.
- Không chuỗi, locale, Room, schema (không đổi `app/schemas`), manifest, `app/build.gradle`, dependency; không Gemini, ad, IAP, VIP. Không đổi `isFeedExist` hay nhánh đăng ký.
- Không chạm thiết bị. Nếu sau này cần máy: **chỉ Pixel 7 Pro `2B051FDH3006MU`**.

## Thiết kế
Trong `changeFeedUrl()` (một chỗ):
```kotlin
val address = _feedOptionUiState.value.newUrl
rssService.get().updateFeed(feed.copy(url = address, sourceUrl = if (feed.sourceUrl != null) address else null))
```
Chú thích ngắn nêu lý do (đồng bộ đọc `sourceUrl ?: url`). Không đổi `showFeedUrlDialog` hay giao diện.

## Kiểm chứng
- **Test `FeedOptionViewModelTest`** (JVM, mock `RssSv`/`LocalRssSv`, bắt `Feed` truyền vào `updateFeed` bằng `slot`):
  - nguồn **RSS thường** (`sourceUrl = null`) ⇒ `url` đổi, `sourceUrl` **vẫn `null`** (không đổi hành vi);
  - nguồn **WEB** (`sourceUrl = url`) ⇒ cả hai thành địa chỉ mới;
  - nguồn **BLUESKY mới** (`url` handle, `sourceUrl` DID) ⇒ cả hai thành địa chỉ mới (DID bị thay, có test ghi lại);
  - nguồn **NOSTR** ⇒ cả hai thành địa chỉ mới;
  - các trường khác (`id`, `name`, `groupId`, `accountId`, `sourceType`, `icon`, `isNotification`, `isFullContent`) **không đổi**;
  - hộp thoại đóng sau khi lưu (`changeUrlDialogVisible == false`);
  - `feed == null` ⇒ không gọi `updateFeed`.
- **Test đồng bộ** (có sẵn `LocalRssSvBlueskySyncTest`): thêm một ca ghi lại kết quả của quy tắc: nguồn Bluesky sau khi sửa (hai cột cùng địa chỉ mới) đồng bộ bằng **địa chỉ mới**.
- **Mutation JVM** (backup + `cmp`, mẫu độc nhất, script Python): bỏ nhánh `sourceUrl` (về chỉ `url`); luôn ghi `sourceUrl` kể cả khi `null` (làm hỏng RSS thường); ghi `sourceUrl` bằng `feed.url` cũ thay vì địa chỉ mới; bỏ kiểm `null`. Mỗi mutant phải có test đỏ.
- Full suite `--rerun-tasks` (kỳ vọng 3.767 + test mới, ghi số đo thật), lint 0 lỗi, `assembleDevDebug` thành công; `app/schemas`, `app/src/main/res`, manifest, `app/build.gradle` không đổi.

## Rủi ro đã biết
- **Địa chỉ sai làm hỏng đồng bộ** ở Bluesky và Nostr (xem trên): trước đây sửa địa chỉ vô hại với hai loại này. Người dùng sẽ thấy lỗi đồng bộ của nguồn; **không mất bài cũ**, sửa lại địa chỉ đúng là hết.
- Mất DID đã nhớ ở nguồn Bluesky mới sau khi sửa (giới hạn của 02o nay có thêm một cách tái hiện).
- Chưa có kiểm địa chỉ nên có thể sửa thành địa chỉ trùng nguồn khác: không chặn (như hiện nay).
- Một số nguồn WEB có thể có `sourceUrl` khác `url` (link gốc trang): sửa địa chỉ sẽ gộp hai về một; có thể làm mất link gốc; chấp nhận theo quyết định của owner.

## Giới hạn đã biết
- Không kiểm địa chỉ trong hộp thoại; không chuỗi báo lỗi mới.
- Không nâng cấp nguồn cũ; không tự chuyển nguồn đã sửa.
- Chỉ JVM, chưa kiểm trên máy.
