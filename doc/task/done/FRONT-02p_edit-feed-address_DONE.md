# FRONT-02p (Sửa địa chỉ nguồn trong menu phải đổi cả địa chỉ đồng bộ)

**Date:** 2026-11-17  
**Branch:** `dev` (không push)  
**Commit:** `23fd213e`  
**Scope:** **chỉ JVM, không chạm thiết bị nào**. Mã production đổi **một tệp, 3 dòng thêm, 1 dòng bỏ**: thân `FeedOptionViewModel.changeFeedUrl()`. Không đổi giao diện, `ChangeUrlDialog`, chuỗi, locale, Room, `app/schemas`, manifest, `app/build.gradle`, dependency; không Gemini, ad, IAP, VIP. **Không thêm kiểm tra địa chỉ** (sẽ cần chuỗi mới).  
**Spec / plan:** `docs/superpowers/specs/2026-11-17-front-02p-edit-feed-address-keeps-sync-address-design.md`, `docs/superpowers/plans/2026-11-17-front-02p-edit-feed-address-keeps-sync-address.md`.

## Việc đã làm
Đồng bộ của nguồn WEB, NOSTR, BLUESKY đọc `sourceUrl ?: url`, nhưng menu "sửa địa chỉ nguồn" (nhấn giữ dòng địa chỉ) chỉ ghi `url`. Nên sửa địa chỉ **không đổi nguồn đang được đồng bộ**. Sau FRONT-02o, `url` (handle) và `sourceUrl` (DID) của nguồn Bluesky mới còn khác nhau nên tật này dễ thấy hơn.

Bây giờ khi lưu địa chỉ mới: nguồn **có `sourceUrl`** (WEB, NOSTR, BLUESKY) ghi địa chỉ mới vào **cả `url` lẫn `sourceUrl`**; nguồn RSS thường (`sourceUrl == null`) **không đổi hành vi** (chỉ `url`, `sourceUrl` vẫn rỗng).

## Kiểm chứng (đo thật)
- **8 test mới** (`FeedOptionViewModelTest`, lớp đầu tiên có test cho `FeedOptionViewModel`): RSS thường chỉ đổi `url` và giữ `sourceUrl` rỗng; WEB, NOSTR và Bluesky mới đổi cả hai cột (Bluesky mất DID đã nhớ, có test ghi lại); mọi trường khác (`id`, `name`, `icon`, `groupId`, `accountId`, `isNotification`, `isFullContent`, `sourceType`) giữ nguyên; hộp thoại đóng sau khi lưu; không có feed thì không lưu; địa chỉ lưu nguyên văn (không `trim`; ghi nhận hành vi, không phải yêu cầu).
- **1 ca đồng bộ mới** trong `LocalRssSvBlueskySyncTest` (9 → 10): nguồn có cả hai cột là địa chỉ mới đồng bộ bằng địa chỉ mới.
- **Rủi ro lớn nhất của plan không xảy ra:** `FeedOptionViewModel` dựng được trong JVM và nạp `feed` qua `showDrawer(backgroundScope, …)` không treo. Chỉ có một lỗi biên dịch của chính test (`pullGroups` trả `MutableList`, không phải `List`).
- **Bước đỏ:** 4 test đỏ đúng lý do (WEB, NOSTR, Bluesky, và "mọi trường khác giữ nguyên" vì so cả `Feed`), 4 xanh ngay. Plan dự kiến 3 đỏ; test thứ tư so cả `Feed` nên cũng bắt thiếu `sourceUrl`; đã đọc lý do trước khi sửa.
- **Mutation JVM: 7/7 bị bắt**, tệp khôi phục giống hệt (`cmp SAME`): chỉ đổi `url`; luôn ghi `sourceUrl` kể cả khi rỗng; giữ `sourceUrl` cũ; đảo điều kiện `null`; bỏ dòng đóng hộp thoại; giữ địa chỉ cũ; ghi `sourceUrl` bằng `url` cũ. Không mutant sống sót. Mẫu `q5` lần đầu viết sai thụt lề nên script dừng trước khi chạy (đã đọc lại tệp và sửa; không có kết quả nào bị bỏ qua).
- **Full suite `--rerun-tasks`:** **3.776** test, 0 lỗi, 0 bỏ qua (3.767 + 9: 8 ViewModel + 1 đồng bộ; cộng từ mọi tệp XML như 02g đến 02o). Lint **0 lỗi, 2.465 cảnh báo** (không đổi). `assembleDevDebug` thành công. `app/schemas`, `app/src/main/res`, manifest, `app/build.gradle` không đổi.

## Hệ quả cần biết (đã được owner duyệt ở spec)
- **Địa chỉ sai ở Bluesky và Nostr nay làm đồng bộ của nguồn đó báo lỗi** (ví dụ dán link web vào nguồn Bluesky). Trước đây sửa địa chỉ vô hại với hai loại này (vì đồng bộ bỏ qua `url`). **Không mất bài cũ**; sửa lại địa chỉ đúng là hết.
- **Nguồn Bluesky mới mất DID đã nhớ** sau khi sửa địa chỉ: không còn được FRONT-02o nhận ra theo DID cho tới khi đăng ký lại.
- Một nguồn WEB có `sourceUrl` khác `url` (link gốc trang) sẽ bị gộp về một địa chỉ sau khi sửa.

## Những gì CHƯA làm (nói thẳng)
- **Không kiểm địa chỉ** trong hộp thoại và không có chuỗi báo lỗi mới.
- **Không** chặn sửa thành địa chỉ trùng nguồn khác.
- Fever và Google Reader vẫn không sửa được: kiểm `rssService.get().update` nằm ở `FeedOptionDrawer` (giao diện), không ở `changeFeedUrl`, nên test ViewModel không phủ cờ này.
- **Chưa kiểm trên máy** (chỉ JVM, đúng phạm vi đã chốt); chưa thử nhấn giữ và sửa địa chỉ thật trên màn hình.
