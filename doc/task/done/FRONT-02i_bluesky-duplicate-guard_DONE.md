# FRONT-02i (Chặn đăng ký trùng một tài khoản Bluesky, RSS cũ và API mới)

**Date:** 2026-11-16  
**Branch:** `dev` (không push)  
**Commit mã và test:** `fe8135a8`  
**Scope:** chỉ JVM, **không chạm thiết bị nào**. Chỉ đổi `SubscribeViewModel.search()` (nhánh Bluesky, 3 dòng thêm, 1 dòng đổi) và thêm hàm `BlueskyFeedMapper.rssUrl(actor)`. Không chuỗi, locale, Room (v22), DataStore, manifest, `app/build.gradle`, dependency mới; không Gemini, ad, IAP, VIP.  
**Spec / plan:** `docs/superpowers/specs/2026-11-16-front-02i-bluesky-duplicate-guard-design.md`, `docs/superpowers/plans/2026-11-16-front-02i-bluesky-duplicate-guard.md`.

## Việc đã làm
Trước đây một tài khoản Bluesky có thể đăng ký hai lần: một nguồn RSS cũ (`https://bsky.app/profile/<tên>/rss`) và một nguồn API mới (`bsky:<tên>`). Dán lại một tài khoản đã đăng ký bằng RSS thì app vẫn gọi API và thêm nguồn thứ hai, nên người dùng thấy bài trùng.

Bây giờ, ngay đầu nhánh Bluesky (trước chữ "Đang tìm", trước khi gọi API), app hỏi Room về **cả hai địa chỉ** của tài khoản. Có một trong hai thì báo "Đã đăng ký" (chuỗi sẵn có `already_subscribed`), không gọi mạng.

- Chặn **hai chiều**, **cùng dạng tên**: handle với handle (chữ thường), DID với DID (giữ nguyên hoa thường). **Không hỏi mạng.**
- Chỉ tài khoản Local (như nhánh Bluesky cũ). Mastodon và Nostr không đổi.

## Kiểm chứng (đo thật)
- Test mới: 4 ở `BlueskyFeedMapperTest` (33 test), 8 ở `SubscribeViewModelBlueskyTest` (23 test).
- **Bước đỏ:** ban đầu 3 test đỏ thay vì 4 dự kiến. Test DID xanh sai vì thiếu mock `api.fetch("did:plc:AbC123")`: lần tìm thứ hai lỗi API rồi rơi về luồng RSS cũ và che mất lỗ hổng. Đã sửa **test** (không sửa mã), rồi đúng 4 test đỏ (cả 4 ở chiều RSS), các test còn lại xanh sẵn.
- **Bước xanh:** `BlueskyFeedMapperTest` 33, `SubscribeViewModelBlueskyTest` 23, `SubscribeViewModelNostrTest` 13, `SubscribeViewModelSocialTest` 8, 0 lỗi.
- **Mutation:** 10/10 mutant bị bắt, mã khôi phục giống hệt (`cmp`): chỉ API (5 test đỏ), chỉ RSS (5), `&&` thay `||` (7), RSS dùng địa chỉ API (5), RSS dùng chuỗi gõ vào (5), bỏ chữ thường (3), chữ thường cả DID (5), bỏ `trim` (2), bỏ `/rss` (9), khóa ô nhập khi báo lỗi (5). Không mutant sống sót.
- **Full suite `--rerun-tasks`: 3.720 test, 0 lỗi, 0 bỏ qua** (3.708 + 4 + 8, cộng từ các tệp XML, cùng cách đếm như 02g/02h). Lint: 0 lỗi, 2.465 cảnh báo (không đổi). `assembleDevDebug` thành công.

## Những gì chưa chặn (nói thẳng)
- **Handle so với DID của cùng một người** (cần hỏi mạng để đổi tên; owner chọn không làm): vẫn có thể đăng ký trùng.
- **Nguồn RSS nhập qua OPML** hoặc dán tay địa chỉ RSS dạng khác (ví dụ `http://`, `/RSS`, có tham số): `isFeedExist` so đúng từng ký tự nên không thấy.
- **Cặp trùng đã có sẵn trong máy:** không gộp, không xóa, không cảnh báo.
- Nguồn RSS cũ không tự chuyển sang API.
- Chưa kiểm trên máy thật (không chạm thiết bị).
