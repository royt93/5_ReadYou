# FRONT-02t — Test máy thật: sửa địa chỉ nguồn rồi đồng bộ (thiết kế, chờ owner duyệt spec)

**Ngày:** 2026-11-18 · **Nhánh:** `dev` · **Nền:** FRONT-02p (sửa mã, chỉ JVM), FRONT-02q (nâng cấp DID khi đồng bộ), FRONT-02r (`BlueskyGuardRig`, Room thật trên máy), FRONT-02l (`FeedItemDeviceTest` dựng `FeedOptionViewModel` thật không qua Hilt)

> Trạng thái: owner đã chốt trong brainstorm (2026-11-18): phạm vi **ViewModel thật + Room thật + một lần đồng bộ với máy chủ giả**, **không** gõ qua hộp thoại, **không** bấm giữ, **không** ca Nostr. Spec chờ owner duyệt trước khi lập plan. Chưa có plan, chưa có mã.

## Mục tiêu
FRONT-02p chỉ có test JVM (`FeedOptionViewModelTest` dùng MockK, `LocalRssSv` giả). Chưa có bằng chứng trên **SQLite thật của Android** rằng: sau khi `changeFeedUrl()` lưu, Room thật giữ đúng hai cột `url` và `sourceUrl`, và lần đồng bộ kế **hỏi máy chủ về địa chỉ mới**. Chỉ thêm test, **không đổi mã production**, manifest, chuỗi, Room (v22), DataStore, `app/build.gradle`; không dependency mới; không mạng thật.

## Sự thật đã đọc (2026-11-18)
- `FeedOptionViewModel.changeFeedUrl()` (`FeedOptionVM.kt:216`): đọc `feed` và `newUrl` từ state, rồi `rssService.get().updateFeed(feed.copy(url = address, sourceUrl = if (feed.sourceUrl != null) address else null))`, rồi đóng hộp thoại. `updateFeed` chỉ là `feedDao.update(feed)`.
- Đồng bộ Bluesky (`LocalRssSv.queryBlueskyArticles`) lấy `actor = (sourceUrl ?: url)` bỏ tiền tố `bsky:`, gọi API, và nếu `(sourceUrl ?: url) != bsky:<DID>` thì nâng cấp `sourceUrl` lên DID bằng `updateSourceUrlIfUnchanged` (02q).
- **Tương tác 02p và 02q cần chứng minh trên máy:** nguồn Bluesky mới có `sourceUrl = bsky:<DID cũ>`; sửa địa chỉ thành `bsky:<handle mới>` ghi cả hai cột là handle mới (mất DID, đã ghi trong báo cáo 02p); lần đồng bộ kế hỏi API bằng **handle mới**, rồi 02q đưa `sourceUrl` lên **DID của hồ sơ mới**, còn `url` giữ handle mới.
- Cách dựng ViewModel không qua Hilt đã có: `FeedOptionViewModel(app.rssSv, Dispatchers.Main, app.ioDispatcher)` (`FeedItemDeviceTest.kt:73`).
- Báo cáo 02p ghi: `showDrawer` làm đồng hồ Compose bận và treo các test Compose chạy sau. Test này **không gọi `showDrawer`**.
- `FeedOptionViewModel.init` chạy `pullGroups().collect` trên `ioDispatcher` (đọc Room, không ghi).
- `BlueskyGuardRig` (02r) có máy chủ giả đếm `getProfile`/`getAuthorFeed`, `seed`, `stored`, `syncOnce`, `cleanUp`, `probeRowCount`; hiện **không ghi lại `actor` của từng yêu cầu**.

## Phạm vi trung thực
- **Chứng minh:** trên SQLite thật, `changeFeedUrl()` thật ghi đúng cột cho nguồn có và không có `sourceUrl`; mọi trường khác của `Feed` giữ nguyên; lần đồng bộ thật kế tiếp (qua `LocalRssSv` thật) dùng địa chỉ mới; 02q nâng cấp lại lên DID mới.
- **Không chứng minh:** bấm giữ dòng địa chỉ, `FeedOptionDrawer`, `ChangeUrlDialog` (gõ và bấm xác nhận), nguồn WEB và NOSTR (hai loại cũng ghi hai cột, chỉ có test JVM), Fever và Google Reader (kiểm `update` nằm ở giao diện), chặn địa chỉ trùng hay sai (02p cố ý không kiểm).
- Chỉ **Tecno KJ7 `115333744A005844`** (Android 14, 1080x2436). Không tọa độ, không MockK, không `connected*`, không xoay, không mạng thật.

## Thiết kế
Một tệp test mới `FeedAddressEditDeviceTest.kt` trong `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/` (cùng gói với rig 02r), và một thay đổi nhỏ **chỉ trong androidTest** ở `BlueskyGuardRig`.

1. **`BlueskyGuardRig`:** thêm danh sách `profileActors()` (danh sách `actor` mà máy chủ giả nhận ở `getProfile`, đọc từ tham số truy vấn). Không đổi hành vi cũ; 12 test của 02r phải vẫn xanh.
2. **Dựng ViewModel:** `FeedOptionViewModel(app.rssSv, Dispatchers.Main, app.ioDispatcher)` tạo trong test (không Hilt). Nạp nguồn vào `feedOptionUiState` bằng cách ghi state qua reflection như `FeedOptionViewModelTest.open` (trường `_feedOptionUiState`), **không `showDrawer`**; kiểm tên trường bằng đọc mã trước khi chép.
3. **Gieo nguồn thử** vào Room thật bằng `rig.seed` (nhóm tạm `front02r_probe_group`, id `front02r_probe_*`), rồi gọi `inputNewUrl(...)`, `changeFeedUrl()`, chờ bằng `waitUntil` trên `rig.stored(id)` (không `sleep`), đọc lại hàng từ Room.
4. **Test (4):**
   - `aNewBlueskySource_changesBothColumns_andTheNextSyncAsksForTheNewAddress`: gieo `url=bsky:alice…`, `sourceUrl=bsky:<DID cũ>`; hồ sơ giả đặt cho handle mới với DID mới (`rig.profile(didMoi, handleMoi)`); sửa thành `bsky:<handleMoi>`: Room có `url == sourceUrl == bsky:<handleMoi>`; một lần `rig.syncOnce(feed)` → `profileActors()` chứa **`handleMoi` và không chứa handle cũ**, `sourceUrl == bsky:<didMoi>`, `url` giữ `bsky:<handleMoi>`, có bài.
   - `anRssSource_changesOnlyItsUrl`: `sourceType` RSS, `sourceUrl = null`: sau sửa `url` mới, `sourceUrl` vẫn `null`.
   - `everyOtherField_isKept`: so từng trường (`id`, `name`, `icon`, `groupId`, `accountId`, `isNotification`, `isFullContent`, `sourceType`) trước và sau.
   - `afterCleanUp_nothingOfTheProbeIsLeftAnywhere`: như 02r.
5. **`@After`:** `rig.cleanUp()` rồi kiểm `probeRowCount() == 0` và `blockedRequests() == 0` (không yêu cầu nào ra ngoài `127.0.0.1`); `viewModelStore.clear()` nếu có.
6. **`@Before`:** `check(currentAccountType == Local)` như 02r.

## Quy tắc an toàn thiết bị
Như 02r: cổng `/tmp/kj7_gate.sh` in `GATE OK` **trong một lệnh riêng** trước mỗi lần khởi chạy; sao lưu/khôi phục byte-exact (`/tmp/kj7_backup.py`, `/tmp/kj7_restore.py`); băm DataStore `9ca1c202050c258b4aac9a063e27def350d737fb` phải trùng; xoay `1 0` sau mỗi lượt; `am instrument --user 0 -w -r -e class … com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner`; không `connected*`; không xóa DataStore; gỡ `com.mckimquyen.reader.test` cuối vòng. Test ghi tạm vào Room thật chỉ bằng dòng tiền tố `front02r_probe_`; khôi phục byte-exact chạy cuối vòng. Script đo dùng `/tmp/kj7_watch.sh` (đo cả `AdActivity`).

## Kiểm chứng
- **20 lượt rồi 40 lượt** có cổng, sao lưu, khôi phục; mọi lượt đỏ phải đọc nguyên nhân (có `AdActivity` không); không gõ lại, không tăng timeout, không `sleep`. Quy tắc: lượt đỏ **không** do quảng cáo → dừng và hỏi owner.
- **Đột biến chạy trên máy** (đổi tạm `FeedOptionVM.kt`, build, cài, chạy, khôi phục, `cmp`): (t1) chỉ ghi `url` → test Bluesky đỏ; (t2) luôn ghi `sourceUrl = address` kể cả khi rỗng → test RSS đỏ; (t3) giữ `sourceUrl` cũ → test Bluesky đỏ; (t4) đột biến trong `LocalRssSv` bỏ nâng cấp DID (02q) → chỉ phần "sourceUrl lên DID mới" của test Bluesky đỏ. Mỗi đột biến phải làm **đúng** test dự định đỏ (so sánh tự động).
- JVM full suite đo lại (không dùng số cũ); `assembleDevDebug assembleDevDebugAndroidTest` thành công; lint không quét `androidTest` (nói thẳng).
- Báo cáo `doc/task/done/FRONT-02t_…_DONE.md`, cập nhật `doc/feature.md` (dòng 02p ghi "đã kiểm trên máy bằng 02t, chưa kiểm giao diện").

## Rủi ro đã biết
- **Ghi vào Room thật:** như 02r (nhóm tạm, `DELETE` cascade, `probeRowCount()==0`, khôi phục byte-exact).
- **Reflection vào `_feedOptionUiState`:** nếu đổi tên trường, test vỡ có lý do rõ (như test JVM đã làm).
- **`FeedOptionViewModel.init` đọc nhóm bằng `ioDispatcher`:** chỉ đọc, không ghi.
- **Quảng cáo (~7% ở 02s):** 02e và 02r đo 0/20 nên kỳ vọng thấp; phải đo lại cho test này.
- **Tương tác 02p và 02q làm `sourceUrl` đổi hai lần:** test phải kiểm cả hai thời điểm (ngay sau lưu và sau đồng bộ) để không nhầm.

## Giới hạn đã biết
- Một máy (Tecno KJ7, Android 14); không so với Pixel.
- Không chứng minh giao diện sửa địa chỉ; chỉ ViewModel, Room và đồng bộ.
