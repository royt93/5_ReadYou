# FRONT-02n (Chặn đăng ký trùng một tài khoản Bluesky khi gõ handle một lần và DID một lần)

**Date:** 2026-11-17  
**Branch:** `dev` (không push)  
**Commit:** `9b3f8d74` (hàm thuần + test), `d7aa8dc8` (khối trong `search()` + test)  
**Scope:** **chỉ JVM, không chạm thiết bị nào**. Đổi mã production ở hai chỗ: `BlueskyFeedMapper.otherNameAddresses` (hàm mới) và `SubscribeViewModel.search()` (nhánh Bluesky, **13 dòng thêm, 0 dòng xóa**, sau `val found = outcome.result`). Không chuỗi, locale, Room (v22), DataStore, manifest, `app/build.gradle`, dependency mới; không Gemini, ad, IAP, VIP.  
**Spec / plan:** `docs/superpowers/specs/2026-11-16-front-02n-bluesky-handle-did-duplicate-guard-design.md`, `docs/superpowers/plans/2026-11-17-front-02n-bluesky-handle-did-duplicate-guard.md`.

## Việc đã làm
FRONT-02i chặn trùng **cùng dạng tên**. Còn trống: một người đã đăng ký bằng handle `alice.bsky.social`, rồi dán DID của chính người đó (hoặc ngược lại) vẫn đăng ký được lần hai.

Bây giờ, sau khi API trả `Found` (có đủ cả DID và handle), app hỏi Room về các địa chỉ của **tên thứ hai** (dạng API `bsky:<tên>` và dạng RSS cũ `https://bsky.app/profile/<tên>/rss`). Có một địa chỉ đã lưu thì báo "Đã đăng ký" (chuỗi sẵn có `already_subscribed`), không dựng xem trước, không thêm nguồn. **Không thêm lần gọi mạng nào** (API đã được gọi sẵn).

Handle do máy chủ trả về **không đáng tin**, nên hàm lọc: rỗng, `handle.invalid` (không phân biệt hoa thường), không qua `isActor`, hoặc bắt đầu bằng `did:`; DID phải có dạng `did:<method>:<id>` (3 phần không rỗng).

## Kiểm chứng (đo thật)
- **Hàm thuần:** +11 test (`BlueskyFeedMapperTest` 33 → 44).
- **Khối trong `search()`:** +9 test (`SubscribeViewModelBlueskyTest` 23 → 32). Bước đỏ: 6 test đỏ đúng lý do (4 chặn handle với DID hai dạng hai chiều, 1 không dựng xem trước, 1 đếm số lần hỏi `isFeedExist`), 3 xanh ngay (handle không hợp lệ, API lỗi, tài khoản khác). Plan dự kiến số khác vì đếm lệch; đã đọc lý do từng test trước khi sửa mã.
- **Mutation JVM:** hàm thuần 12 mutant, **11 bị bắt ngay**; khối trong `search()` **9/9 bị bắt**. Tệp khôi phục giống hệt (`cmp SAME`) sau mỗi batch.
- **Hai mutant sống sót, xử lý đúng bản chất:**
  - Bỏ `distinctBy`: **mã thừa thật** (hai tên chỉ ra cùng địa chỉ khi `handle == did`, nhưng handle bắt đầu bằng `did:` đã bị loại). **Đã xóa**, không thêm test.
  - Bỏ `startsWith("did:")` trong `isDid`: **thiếu test thật** (`x:y:z` có 3 phần không rỗng và qua `isActor`). Tôi từng đoán cả hai là mã thừa, **đoán sai một cái**; đã thêm test `aColonSeparatedNameThatIsNotADid_isIgnored`, chạy lại đúng mutant này, bị bắt.
- **Một lỗi của chính mã mới, do test phát hiện:** `did:` trần (và `did:plc`) vẫn qua `isActor`, nên bản đầu của hàm coi là DID hợp lệ và sinh địa chỉ rác. Đã siết thành kiểm cấu trúc.
- **Test của 02i đổi nghĩa đúng như plan dự báo:** `aDifferentHandle_isNotBlockedByAnotherAccountsSource` đỏ vì dữ liệu giả trả về alice (API nói đây là alice, mà alice đã đăng ký). Đã sửa **dữ liệu test** (thành bob), không nới thiết kế.
- **Full suite `--rerun-tasks`:** **3.751** test, 0 lỗi, 0 bỏ qua (3.731 + 20: 11 hàm thuần + 9 ViewModel; cộng từ mọi tệp XML như 02g đến 02m). Lint **0 lỗi, 2.465 cảnh báo** (không đổi). `assembleDevDebug` thành công. `app/schemas`, `app/src/main/res`, manifest, `app/build.gradle` không đổi.

## Những gì CHƯA chặn (nói thẳng)
- **API lỗi hoặc `Found` rỗng** (rơi về RSS): không có tên thứ hai nên **không** chặn handle với DID. Có test ghi lại hành vi này (`anApiFailure_fallsBackToRss_andTheOtherNameIsNotAsked_aKnownLimit`).
- **Chủ tài khoản đã đổi handle** sau khi đăng ký: nguồn cũ lưu handle cũ, API trả handle mới và DID, nên hai địa chỉ kiểm không khớp nguồn cũ. **Cập nhật FRONT-02o (2026-11-17):** nguồn đăng ký **mới** nay lưu DID ở `sourceUrl` nên ca này được chặn; nguồn **cũ** thì chưa (xem `FRONT-02o_bluesky-did-in-source-url_DONE.md`).
- **Nguồn RSS từ OPML** hoặc địa chỉ dạng khác; **cặp trùng đã có sẵn** trong máy: không xử lý.
- **Rủi ro bảo mật mức thấp:** một máy chủ độc có thể trả handle giả của người khác để **chặn một lần đăng ký**. Chỉ chặn đăng ký, không làm hại dữ liệu; đã lọc handle không hợp lệ nhưng không loại hẳn được ca này (không có cách khi không hỏi thêm mạng).
- **Trải nghiệm:** với ca handle với DID, người dùng thấy "Đang tìm" rồi mới thấy "Đã đăng ký" (khác 02i, báo trước khi gọi API).
- **Chưa kiểm trên máy** (chỉ JVM, đúng phạm vi đã chốt).

## Ghi chú vận hành
Lần chạy batch đột biến đầu mất 4 mutant vì lỗi dấu nháy trong script shell (`split(':')`); đã chạy lại bằng script Python. Không ảnh hưởng mã.
