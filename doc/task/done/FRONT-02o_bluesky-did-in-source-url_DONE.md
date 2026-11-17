# FRONT-02o (Nhớ DID của nguồn Bluesky mới để chặn đăng ký trùng cả khi chủ tài khoản đổi handle)

**Date:** 2026-11-17  
**Branch:** `dev` (không push)  
**Commit:** `f4fe8105` (hàm DAO và repo + test DAO), `35b4e54c` (lưu DID, kiểm theo DID, test ViewModel và test repo)  
**Scope:** **chỉ JVM, không chạm thiết bị nào**. Đổi mã production ở ba tệp, **19 dòng thêm, 2 dòng bỏ**: `FeedDao.queryBySourceUrl` (một truy vấn), `AbstractRssRepository.isFeedSourceExist`, `SubscribeViewModel.search()` (nhánh Bluesky). **Không đổi schema Room**: `app/schemas/.../22.json` giữ nguyên (băm `45d76f04…` trước và sau; `git status` sạch ở `app/schemas`), không migration, không cột mới. Không chuỗi, locale, manifest, `app/build.gradle`, dependency; không Gemini, ad, IAP, VIP.  
**Owner đã cho phép** đúng một hàm truy vấn DAO mới (2026-11-17), hiểu "không Room mới" là không đổi schema.  
**Spec / plan:** `docs/superpowers/specs/2026-11-17-front-02o-bluesky-did-in-source-url-design.md`, `docs/superpowers/plans/2026-11-17-front-02o-bluesky-did-in-source-url.md`.

## Việc đã làm
Trước đây một nguồn Bluesky mới lưu `url = sourceUrl = bsky:<tên gõ>`. Nếu chủ tài khoản đổi handle, nguồn cũ vẫn mang handle cũ nên 02n (kiểm theo tên) không nhận ra nó.

Bây giờ nguồn Bluesky **đăng ký mới** lưu:
- `url = bsky:<tên gõ>` (như cũ, người dùng vẫn thấy handle dễ đọc);
- **`sourceUrl = bsky:<DID>`** (DID bền hơn handle). Đồng bộ vốn đã đọc `sourceUrl ?: url` và gọi API bằng DID được, nên đồng bộ **không đổi**.

Khi đăng ký, sau khi API trả `Found`, ngoài kiểm của 02n app hỏi thêm `isFeedSourceExist(bsky:<DID>)`: có nguồn nào có `sourceUrl` đúng DID đó trong **tài khoản hiện tại** thì báo "Đã đăng ký" (chuỗi sẵn có), dù `url` của nguồn đó là handle cũ.

## Kiểm chứng (đo thật)
- **DAO:** +6 test Room trong bộ nhớ (`FeedDaoSourceUrlTest`): khớp đúng `sourceUrl`; không khớp theo `url`; không lẫn tài khoản; khớp đúng cả chuỗi (không từng phần); phân biệt hoa/thường; `null` không khớp.
- **Repo:** +3 test với `LocalRssSv` thật trên Room thật (`AbstractRssRepositorySourceExistTest`): đúng tài khoản hiện tại; không thấy nguồn của tài khoản khác; không nhầm `url` với `sourceUrl`.
- **ViewModel:** +7 test (`SubscribeViewModelBlueskyTest` 32 → 39): lưu DID ở `sourceUrl` và giữ handle ở `url`; **ca mục tiêu** (nguồn đã lưu DID, gõ handle mới ⇒ báo trùng, không dựng xem trước); gõ DID khi nguồn đã lưu DID; nguồn cũ (handle ở cả hai cột) **không** chặn được sau khi đổi handle, có test ghi lại giới hạn này; DID khác không bị chặn; đúng một lần hỏi, và không hỏi khi API lỗi hay khi là link Mastodon.
- **Bước đỏ:** 5 test đỏ đúng lý do (lưu DID, ca đổi handle, gõ DID, đếm một lần hỏi, và `aDifferentDid` vì `sourceUrl` chưa là DID); 2 xanh ngay (nguồn cũ, API lỗi hoặc không phải Bluesky). Mỗi cái đã đọc lý do trước khi sửa mã.
- **Test cũ đổi nghĩa như plan dự báo:** `aHandle_isReadThroughTheApi_andShowsTheDisplayNameAndTheAvatar` khẳng định `sourceUrl == "bsky:alice.bsky.social"`, nay là DID; đã sửa kỳ vọng.
- **Mutation JVM: 13/13 bị bắt cuối cùng.** DAO 4/4 ngay (bỏ lọc tài khoản, đọc `url` thay `sourceUrl`, `LIKE`, `NOCASE`). `search()` 5/5 (lưu tên gõ thay DID, bỏ kiểm, `&&` thay `||`, sai cột, dùng handle thay DID). Repo 3/3 (sai tài khoản, luôn `false`, dùng truy vấn theo `url`).
- **Một mutant sống sót, xử lý đúng bản chất:** `o5` (repo gọi `queryBySourceUrl(0, …)` thay vì tài khoản hiện tại) **sống sót** sau test DAO vì test DAO không gọi hàm repo, và test ViewModel dùng mock nên không thấy. Đã ghi rõ trong thân commit `f4fe8105`; thêm test repo với Room thật, chạy lại đúng mutant này: bị bắt (2 test đỏ).
- **Full suite `--rerun-tasks`:** **3.767** test, 0 lỗi, 0 bỏ qua (3.751 + 16: 6 DAO + 3 repo + 7 ViewModel; cộng từ mọi tệp XML như 02g đến 02n). Lint **0 lỗi, 2.465 cảnh báo** (không đổi). `assembleDevDebug` thành công.

## Những gì CHƯA chặn (nói thẳng)
- **Nguồn đã đăng ký trước 02o:** ban đầu không được nâng cấp (`sourceUrl = url = handle cũ`); **từ FRONT-02q (2026-11-17) được nâng cấp khi còn đồng bộ thành công** (xem `FRONT-02q_upgrade-old-bluesky-sources-to-did_DONE.md`); nguồn đã đổi handle trước đó thì vẫn không. Nội dung ban đầu: Nếu chủ tài khoản của nguồn **cũ** đã đổi handle thì vẫn trùng được. Giới hạn của 02n chỉ **thu hẹp dần theo thời gian** khi nguồn mới được thêm, không biến mất ngay. Có test ghi lại.
- **API lỗi hoặc `Found` rỗng** (rơi về RSS): vẫn không chặn.
- **OPML** và địa chỉ dạng khác: nguồn nhập từ OPML không có DID ở `sourceUrl`. Cặp trùng có sẵn: không xử lý.
- ~~**Menu "sửa địa chỉ nguồn" (`changeFeedUrl`) chỉ đổi `url`, không đổi `sourceUrl`.**~~ **Đã sửa ở FRONT-02p (2026-11-17), xem `FRONT-02p_edit-feed-address_DONE.md`.** Nội dung ban đầu: Vì đồng bộ ưu tiên `sourceUrl`, sửa địa chỉ trong menu **không đổi nguồn đang được đồng bộ** của một nguồn Bluesky. Tật có sẵn (trước đây hai cột cùng giá trị nên không lộ rõ); nay `url` là handle và `sourceUrl` là DID nên dễ thấy hơn. **Không sửa ở đây** (ngoài phạm vi); nên cân nhắc sửa riêng.
- **Test thiết bị `BackgroundSyncRig` (02h)** tự dựng nguồn thử với `sourceUrl` là handle, không đi qua `search()`; không đổi (không chạm máy). Vẫn đúng vì đồng bộ chấp nhận cả hai dạng.
- Truy vấn theo `sourceUrl` quét theo tài khoản (đã có chỉ mục `accountId`), không có chỉ mục riêng cho `sourceUrl` (thêm sẽ đổi schema); nhanh với vài trăm nguồn.
- Một máy chủ độc trả DID giả chỉ có thể chặn **một lần** đăng ký (như đã nêu ở 02n).
- **Chưa kiểm trên máy** (chỉ JVM, đúng phạm vi đã chốt).
