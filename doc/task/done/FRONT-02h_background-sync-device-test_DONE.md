# FRONT-02h (Test máy thật: đồng bộ nền của nguồn BLUESKY và NOSTR trên Room thật)

**Date:** 2026-11-16  
**Branch:** `dev` (không push)  
**Scope:** chỉ thêm test, **không đổi mã production**, manifest, chuỗi, Room (v22), DataStore, `app/build.gradle`; không dependency mới. Đây là test, không phải tính năng người dùng.  
**Spec / plan:** `docs/superpowers/specs/2026-11-16-front-02h-background-sync-device-test-design.md`, `docs/superpowers/plans/2026-11-16-front-02h-background-sync-device-test.md`.

## Điều test chứng minh

Trên **Tecno KJ7 (`115333744A005844`, Android 14)**, với **Room thật** và DAO thật, một nguồn thử `BLUESKY` và một nguồn thử `NOSTR` (nhóm tạm riêng, id bắt đầu `front02h_probe_`) đi qua **ba bước** của một vòng đồng bộ nền: `LocalRssSv.syncSingleFeed` → `smartFilterManager.applyRules` → `articleDao.insertListIfNotExist`. Hai máy chủ giả (`MockWebServer` cho Bluesky API, `NostrFakeRelay` cho relay) chạy trên `127.0.0.1`. `LocalRssSv` được dựng tay từ các field `@Inject` công khai của `RApp` (DAO thật, `RssHelper`, `NotificationHelper`, `WorkManager`) cộng các lớp có `@Inject constructor` nông, chỉ hai client mạng được thay. Tám test:

1. `blueskySource_writesOnlyItsOwnPosts_withTheRightLinks`: ghi đúng bài chính chủ, không ghi bài đăng lại, link `https://bsky.app/profile/<did>/post/<rkey>`.
2. `blueskySource_isNeverRenamedNorGivenANewIcon_byTheSync`: tên và icon trong Room **không đổi** dù hồ sơ giả đưa tên và avatar khác.
3. `nostrSource_writesItsNote_withAnNjumpLink_andIsNeverRenamed`: ghi ghi chú, link `https://njump.me/note1…`, tên và icon không đổi.
4. `aSecondSync_ofTheSameAnswer_insertsNothingNew`: lần hai không chèn trùng, cả hai loại.
5. `blueskyServerError_isASyncFailure_andWritesNoArticle` (HTTP 500).
6. `blueskyNotFound_isASyncFailure_andWritesNoArticle` (HTTP 400 `Profile not found`).
7. `blueskyAnswerWithOnlyRepostsAndNoOwnPosts_isASyncFailure_notAnEmptyFeed`.
8. `afterCleanUp_nothingOfTheProbeIsLeftAnywhere`: không còn dòng `front02h_probe_%` ở `feed`, `group`, `feed_health_record`, `article`; số dòng `group` trở về như trước.

## Nói thẳng về phạm vi

- **KHÔNG gọi `sync()` hay `SyncWorker.doWork`.** Chúng quét **mọi** nguồn của máy bằng mạng thật, tải trước nội dung, lập chỉ mục, nhắc kèo và có thể đăng thông báo. Test lặp lại đúng ba lời gọi mà `sync()` thực hiện cho **một** nguồn; đó là **bằng chứng gián tiếp**, không phải chính đường `sync()`.
- **Không chứng minh:** vòng lặp mọi nguồn, `precache`, chỉ mục ngữ nghĩa, nhắc kèo, thông báo, `WorkManager` lên lịch, `retractionRadar`/`watchdogManager`/`smartCollectionMatcher` trong đường đồng bộ thật, mạng thật cho hai nguồn (đã có ở 02b/02c và 02g), hiển thị trên màn hình.
- **Nhánh quét toàn bộ của Radar:** `fetchNewArticles` chỉ quét lại toàn bộ khi `latestLink != null`, bật Radar và `scansFullFeedForRadar`. Test (4) đồng bộ lần hai nên **có thể** đi qua nhánh đó. Cờ Radar trong DataStore của Tecno **không có khóa riêng** (khóa `retraction_radar` không xuất hiện trong tệp DataStore sao lưu, đã kiểm chỉ sự tồn tại của tên khóa), nghĩa là dùng mặc định `true` (`ExtDataStore.kt:87`: `?: true`). Vì vậy nhánh quét toàn bộ **có thể đã chạy** ở lần đồng bộ thứ hai; test không kiểm điều đó riêng.
- **Lịch đồng bộ thật của chính máy:** tài khoản Tecno (đọc từ bản sao lưu, `syncInterval = 30` phút, `syncOnStart = 0`) nên mỗi lần `RApp` khởi động xếp lịch một `SyncWorker` định kỳ thật. Một worker như vậy có thể chạy `sync()` bằng mạng thật trong lúc test, hoặc sau khi cài app, ghi vào Room thật. Điều này không do test gây ra (mọi lần cài `RApp` ở 02e và 02g cũng vậy) và bản sao lưu byte-exact rồi khôi phục chính là thứ chặn mọi thay đổi đó, nhưng nghĩa là số dòng toàn bảng **không** được so trước/sau; chỉ so dòng của nhóm tạm và số dòng `group`.
- `SmartFilterManager` và `WatchdogManager` dựng tay khởi chạy coroutine nền đọc `SharedPreferences` thật (**chỉ đọc**). Test không gọi `checkAndNotify` hay `notify`.
- Một máy (Tecno KJ7, Android 14); không so được với Pixel hay máy khác.

## Kiểm chứng

- **Máy thật (Tecno KJ7):** lượt thử đầu **đỏ 2/8** (`nostrSource_...` và `aSecondSync_...`). Nguyên nhân là **lỗi của rig, không phải của app**: feed Nostr thử được lưu `nostr:<hex>` trong khi app lưu và đọc `nostr:<npub>` (bech32, `NostrKeys.npubToHex`); `queryNostrArticles` ném `IOException` và nguồn bị coi là lỗi đồng bộ. Sửa rig dùng `NostrFeedMapper.feedUrl(npub)`, chạy lại: **8/8 xanh** (2,6 giây). Sau đó **20/20 lượt xanh** (`OK (8 tests)`), cổng chỉ đọc trước mỗi lượt, sao lưu và khôi phục **byte-exact 6 tệp**, băm DataStore `9ca1c202…` trùng, đã gỡ `com.mckimquyen.reader.test`, xoay `1 0`.
- **Mutation chạy trên máy** (đổi tạm mã production, build, cài, chạy, khôi phục, `cmp`; `git diff app/src/main` = 0 dòng sau đó):

| Đột biến | Test đỏ |
|---|---|
| `m01` bỏ nhánh `BLUESKY` trong `queryFeedArticles` | 5/8 |
| `m02` bỏ nhánh `NOSTR` | 2/8 (`nostrSource_...`, `aSecondSync_...`) |
| `m03` bỏ guard `articles.isEmpty()` của Bluesky | 1/8 (`blueskyAnswerWithOnlyReposts...`) |
| `m04` đổi tên feed và ghi vào Room (`feedDao.update`) trong `queryBlueskyArticles` | 1/8 (`blueskySource_isNeverRenamed...`) |
| `m05` `insertListIfNotExist` luôn chèn | 1/8 (`aSecondSync_...`) |

  Hai lượt `base` (đầu và cuối) xanh 8/8. **Đột biến `m04` ban đầu trong plan** chỉ đổi tên trên đối tượng trong bộ nhớ và sẽ sống sót vì Room không đổi; tôi đã đổi thành ghi thật vào Room (`feedDao.update(feed.copy(name = ...))`) trước khi chạy, nên nó thật sự kiểm "không đổi tên trong Room".
- **JVM full suite** `--rerun-tasks`: **3.708 test, 0 lỗi, 0 bỏ qua** (480 lớp); `assembleDevDebug assembleDevDebugAndroidTest` thành công. **Số 3.705 ở báo cáo FRONT-02g đã được sửa thành 3.708.** Giải thích kiểm được bằng cách đếm `@Test` của năm lớp Bluesky ở từng commit: `fedfc82c` có 78 test (3.627 + 78 = 3.705, số đo lúc đó), `b9667e0a` (gộp quy tắc actor) thêm 3 test thành 81 (3.627 + 81 = 3.708, số đo hôm nay). Báo cáo 02g ghi "81 test mới" cạnh số 3.705, hai con số thuộc hai thời điểm khác nhau; đã sửa. Lưu ý: đếm `@Test` toàn dự án (3.691) **không** khớp số test chạy (có lớp tham số hóa), nên giải thích dựa trên hiệu hai lần đếm cùng một năm lớp, không dựa trên tổng. `git diff` của `app/src/main`, `app/schemas`, manifest, `app/build.gradle` rỗng. Lint không quét `androidTest` nên **không kiểm** các tệp test mới; chỉ biên dịch được kiểm.

## Chưa kiểm (nói thẳng)

- `AbstractRssRepository.sync` và `SyncWorker.doWork` thật; mạng thật trong đường đồng bộ; thông báo; hiển thị tên và icon trên màn hình.
- Nhánh quét toàn bộ của Radar có đúng khi bật hay không (xem trên); bảng `article_snapshot`/`article_radar_state` không được đếm riêng (chúng có khóa ngoại CASCADE tới `article`, nên nhóm tạm xóa sẽ dọn).
- Chập chờn của test thiết bị: 20/20 xanh, chưa có lượt đỏ nào sau khi sửa rig, nhưng **không chứng minh** hết chập chờn (FRONT-02d từng đỏ 2 đến 9% với gốc chưa rõ).

## Giới hạn

- Hai nguồn thử chỉ đại diện; một vòng đồng bộ thật có hàng chục nguồn chạy song song theo nhóm 6.
- Máy chủ giả luôn trả dữ liệu hợp lệ trừ ba ca lỗi đã nêu; không thử phản hồi cực lớn, chậm, cắt giữa chừng (đã có ở test JVM `BlueskyApiClientTest`).
- Không có test cho "relay Nostr không trả lời" (spec (d) nhắc; chưa làm vì `NostrFakeRelay.holdAnswerUntil` chặn luồng).

## Commits trên `dev`

`2dde1b60` spec · `005518d8` plan · (commit test + tài liệu: xem `git log`)
