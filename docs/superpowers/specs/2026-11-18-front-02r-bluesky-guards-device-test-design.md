# FRONT-02r — Test máy thật: chặn trùng nguồn Bluesky và nâng cấp DID khi đồng bộ (thiết kế, chờ owner duyệt spec)

**Ngày:** 2026-11-18 · **Nhánh:** `dev` · **Nền:** FRONT-02i, 02n, 02o, 02q (chỉ có test JVM), FRONT-02h (`BackgroundSyncRig`, Room thật trên máy), FRONT-02e (`subscribeViewModelFactory`)

> Trạng thái: owner đã chốt trong brainstorm (2026-11-18): (1) phạm vi **02q + 02i + 02n + 02o**, **bỏ 02p**; (2) gieo nguồn thử vào **Room thật**, nhóm tạm tiền tố `front02r_probe_`, dọn sau mỗi test, sao lưu và khôi phục byte-exact; (3) test **gọi `inputLink(...)` và `search()` trực tiếp**, không gõ qua giao diện; (4) thiết bị **chỉ Tecno KJ7**. Spec chờ owner duyệt trước khi lập plan. Chưa có plan, chưa có mã.

## Mục tiêu
Bốn việc đã làm ở đợt Bluesky mới có test JVM (Room trong bộ nhớ của Robolectric) mà chưa có bằng chứng trên **SQLite thật của Android**: chặn đăng ký trùng một tài khoản Bluesky (02i, 02n, 02o) và nâng cấp nguồn cũ lên lưu DID khi đồng bộ thành công (02q). Chỉ thêm test, **không đổi mã production**, manifest, chuỗi, Room (v22), DataStore, `app/build.gradle`; không dependency mới; không mạng thật.

## Sự thật đã đọc trong code (2026-11-18)
- Chặn trùng nằm ở `SubscribeViewModel.search()` (nhánh Bluesky, khoảng dòng 213 đến 263): hỏi `isFeedExist(bsky:<tên gõ>)` và `isFeedExist(<RSS cũ>)` **trước** khi gọi API; sau khi API trả `Found`, hỏi `otherNameAddresses(...)` bằng `isFeedExist` và `isFeedSourceExist(bsky:<DID>)`.
- `isFeedExist` so cột `url` (`FeedDao.queryByLink`), `isFeedSourceExist` so cột `sourceUrl` (`FeedDao.queryBySourceUrl`), cả hai lọc theo `accountId` hiện tại, so **đúng cả chuỗi** (phân biệt hoa thường).
- Nguồn đăng ký mới lưu `url = bsky:<tên gõ>` và `sourceUrl = bsky:<DID>` (02o).
- Nâng cấp 02q ở `LocalRssSv.queryBlueskyArticles`: sau khi API trả bài, nếu `(sourceUrl ?: url) != bsky:<DID>` thì gọi `feedDao.updateSourceUrlIfUnchanged(id, sourceUrlCũ, bsky:<DID>)`; lỗi ở bước này không làm mất bài; `url` không bao giờ đổi.
- Hạ tầng máy có sẵn: `BackgroundSyncRig` (02h) dựng `LocalRssSv` từ DAO thật của `RApp` với `BlueskyApiClient` trỏ máy chủ giả, gieo nhóm tạm và dọn bằng cách xóa nhóm (cascade xóa nguồn và bài), có `probeRowCount()` và `counts()`. `BlueskyDeviceFixtures` có `DID`, `HANDLE`, `PROFILE_BODY`, `feedBody(...)`.
- `subscribeViewModelFactory` (02e) dựng `SubscribeViewModel` thật nhưng dùng `BlueskyApiClient` **thật** (`API_BASE` công khai): không dùng được cho test này nếu không có biến thể trỏ máy chủ giả.
- FRONT-02m: gõ chữ vào ô qua giao diện chập chờn khoảng 1 đến 1,5% lượt (gốc chưa rõ). FRONT-02d: `MainActivity` bị quảng cáo toàn màn hình chèn vào khoảng 25% lượt (10 trong 11 lượt đỏ có `AdActivity`). Vì vậy test này không dùng `MainActivity` và không gõ qua giao diện.

## Phạm vi trung thực
- **Chứng minh:** trên SQLite thật của Android, với DAO thật và `LocalRssSv` thật, các câu truy vấn chặn trùng và câu `UPDATE ... WHERE sourceUrl IS ...` của nâng cấp DID cho đúng kết quả, và `search()` thật phản ứng đúng (báo trùng hoặc dựng xem trước, đúng số lần hỏi máy chủ).
- **Không chứng minh:** 02p (menu sửa địa chỉ), mạng thật của Bluesky, nút "Đăng ký" thật và hộp thoại trên màn hình (test đọc state ViewModel), `MainActivity`/`HomeEntry`, `SyncWorker` thật, nguồn đã đổi handle từ trước 02q (02q không nâng cấp được), nguồn nhập từ OPML.
- Chạy chỉ trên **Tecno KJ7 `115333744A005844`** (Android 14, 1080x2436). Không tọa độ, không MockK, không `connected*`, không xoay màn hình (test không dùng `UiAutomation.setRotation`).

## Thiết kế
Hai tệp mới trong `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/` (cùng gói với `SubscribeViewModelFactory`), và một thay đổi **chỉ trong androidTest**:

1. **`BlueskyGuardRig`** (tệp mới): dựng một máy chủ Bluesky giả (`MockWebServer`, như `BackgroundSyncRig`), một `BlueskyApiClient` trỏ vào nó (constructor `internal`, truy cập được từ `androidTest`), một `SubscribeViewModel` thật qua `ViewModelProvider` của `ComponentActivity` với các dịch vụ thật đọc từ `RApp` và `BlueskyApiClient` giả (một biến thể của factory 02e nhận `BlueskyApiClient`; thêm tham số có giá trị mặc định để `SubscribeViewModelFactory` cũ không đổi hành vi), và `LocalRssSv` từ `LocalRssSvFactory.build` (02h) cho các test đồng bộ. Cung cấp: gieo nguồn thử (`seed(feed)`), đọc lại nguồn (`feedByTitle`/`queryBySourceUrl`), đếm yêu cầu tới máy chủ giả (`apiRequests()`), `cleanUp()` (xóa nhóm tạm `front02r_probe_group`, cascade), `probeRowCount()`.
2. **`BlueskyGuardsDeviceTest`** (tệp mới): các test dưới đây. Mỗi test gieo một nguồn thử `front02r_probe_*` trong nhóm tạm của tài khoản hiện hành, rồi gọi `viewModel.inputLink(...)` và `viewModel.search()` (hoặc `rig.syncOnce(feed)`), chờ kết quả bằng `waitUntil` trên state (không `Thread.sleep`).
3. **Quy tắc máy:** `@After` luôn chạy `cleanUp()` rồi kiểm `probeRowCount() == 0`; một test không được để sót dòng nào. Tài khoản hiện hành phải là Local (nếu không, test báo lỗi rõ, không đoán).

Test (mỗi cái một hành vi người dùng thấy; kiểm cả đường **không** gọi máy chủ khi không cần):
- **02i** `sameAccountAsApiSource_isAlreadySubscribed_withoutAskingTheServer`: có nguồn `bsky:alice…`, gõ lại → `errorMessage` là "Đã đăng ký", `feed == null`, **0 yêu cầu** tới máy chủ giả.
- **02i** `sameAccountAsOldRssSource_isAlreadySubscribed`: có nguồn RSS cũ, gõ `@handle` → báo trùng.
- **02n** `typedAsDid_whenSavedAsHandle_isAlreadySubscribed`: nguồn lưu handle; gõ DID của chính nó; sau khi API trả `Found` (hồ sơ giả có `did` và `handle`) → báo trùng.
- **02n** `typedAsHandle_whenSavedAsDid_isAlreadySubscribed`: ngược lại.
- **02o** `newSource_isStoredWithTheDidInSourceUrl_andTheTypedNameInUrl`: không trùng; sau `search()` có xem trước với `feed.url == bsky:<tên gõ>` và `feed.sourceUrl == bsky:<DID>`.
- **02o** `renamedHandle_isCaughtThroughTheStoredDid`: nguồn có `url = bsky:<handle cũ>`, `sourceUrl = bsky:<DID>`; gõ **handle mới** của cùng DID → báo trùng (ca mục tiêu của 02o).
- **02o** `aDifferentDid_isNotBlocked`: nguồn của DID khác, cùng tên gần giống → **không** bị chặn, có xem trước (tránh chặn nhầm).
- **02q** `oldSource_isUpgradedToItsDid_whenASyncSucceeds_andKeepsItsUrlAndItsPosts`: nguồn `sourceUrl = url = bsky:<handle>`; một lần đồng bộ thành công → `sourceUrl == bsky:<DID>`, `url` không đổi, bài được ghi.
- **02q** `aFailedSync_leavesTheSourceUrlAlone`: máy chủ giả trả 500 → `sourceUrl` không đổi, không bài.
- **02q** `aSecondSync_doesNotRewriteAnUpgradedSource`: lần đồng bộ thứ hai khi `sourceUrl` đã là DID → không ghi lại (`updateSourceUrlIfUnchanged` trả `Int` số dòng đổi, 0 hoặc 1; test đọc lại cột `sourceUrl` và kiểm không đổi, vì lần đồng bộ nằm trong `LocalRssSv` không lộ giá trị trả về).
- **Dọn dẹp** `afterCleanUp_nothingOfTheProbeIsLeftAnywhere` (như 02h).

## Quy tắc an toàn thiết bị
Như FRONT-02e trên Tecno: cổng chỉ đọc `/tmp/kj7_gate.sh` (launcher `com.transsion.hilauncher` trên cùng, mở khóa, tỉnh, không instrumentation, `accelerometer_rotation=1`, `user_rotation=0`, 1080x2436) in `GATE OK` **trong một lệnh riêng** trước mỗi lần khởi chạy; sao lưu và khôi phục **byte-exact 6 tệp** (`/tmp/kj7_backup.py`, `/tmp/kj7_restore.py`), băm DataStore `9ca1c202050c258b4aac9a063e27def350d737fb` phải trùng sau mỗi vòng; `am instrument --user 0 -w -r -e class ... com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner` (không `connected*`); không xóa DataStore; chỉ force-stop `com.mckimquyen.reader`; sau mỗi vòng gỡ `com.mckimquyen.reader.test`. Test ghi tạm vào Room thật của Tecno (212 bài, 1 nguồn thật): chỉ bằng dòng có tiền tố `front02r_probe_`, và việc khôi phục byte-exact luôn chạy cuối vòng.

## Kiểm chứng
- Chạy lặp **20 lượt** rồi **40 lượt** có cổng, sao lưu, khôi phục; mọi lượt đỏ phải đọc nguyên nhân trước khi dùng làm bằng chứng, không gõ lại, không tăng timeout, không `sleep`.
- **Đột biến chạy trên máy** (đổi tạm mã production, build, cài, chạy, khôi phục, `cmp`): (1) `search()` bỏ kiểm `isFeedExist(oldRssUrl)` → test RSS cũ đỏ; (2) bỏ `otherNameAddresses` → hai test 02n đỏ; (3) bỏ `isFeedSourceExist(bsky:<DID>)` → ca đổi handle đỏ; (4) lưu `sourceUrl` bằng tên gõ thay DID → test lưu DID đỏ; (5) bỏ `updateSourceUrlIfUnchanged` → test nâng cấp đỏ; (6) nâng cấp cả khi đồng bộ lỗi → test lỗi đỏ; (7) `isFeedSourceExist` đọc cột `url` → ca đổi handle đỏ. Mỗi đột biến phải làm **đúng test dự định** đỏ; đột biến sống sót ⇒ test yếu, thêm khẳng định.
- JVM full suite không đổi (**3.797** theo báo cáo 02q, ba lần chạy thật không cache; đo lại lúc thực thi, không dùng số này làm bằng chứng); `assembleDevDebug assembleDevDebugAndroidTest` thành công; lint không quét `androidTest` (nói thẳng).
- Báo cáo: số lượt, lượt đỏ và nguyên nhân, bảng đột biến, băm DataStore trước và sau, `user_rotation` trước và sau, những gì không kiểm.

## Rủi ro đã biết
- **Ghi vào DB thật:** một lượt bị cắt giữa chừng có thể để sót dòng thử; khôi phục byte-exact xử lý, nhưng giữa chừng dữ liệu của owner có thêm dòng `front02r_probe_`. Hạn chế: nhóm tạm riêng, một câu `DELETE` cascade, kiểm `probeRowCount()==0`.
- **Hợp đồng `search()` chạy trên `viewModelScope`:** test phải chờ state (`waitUntil` trên `subscribeUiState`) chứ không kỳ vọng đồng bộ; nếu `search()` bị hủy khi Activity đóng, test đỏ với lý do rõ.
- **`SubscribeViewModel` phụ thuộc `context.currentAccountType`:** phải là Local; kiểm ở `@Before`.
- **Thay đổi factory trong androidTest:** thêm tham số có mặc định cho `blueskyApiClient`; kiểm `SubscribeNostrPreviewRecreateDeviceTest` vẫn xanh (không đổi hành vi mặc định).
- **Chập chờn không rõ nguồn** (như 02m) vẫn có thể xảy ra ở bước nào đó của máy; test không gõ chữ nên tránh được nguồn đã biết đó, nhưng không loại trừ nguồn khác.

## Giới hạn đã biết
- Một máy (Tecno KJ7, Android 14); không so với Pixel.
- Không chứng minh nút và hộp thoại thật; chỉ state ViewModel và dữ liệu Room.
- Không chứng minh 02p.

## Điều chỉnh khi thực thi
- **Không tái dùng `BackgroundSyncRig` (02h) nguyên vẹn:** hồ sơ giả của nó cố định (một DID, một handle); viết `BlueskyGuardRig` nhỏ, **tái dùng** `LocalRssSvFactory.build` và `SyncResult` của 02h và cách dọn nhóm; tiền tố `front02r_probe_`.
- **Factory:** thêm hàm mới `subscribeViewModelFactoryWith(relayUrl, blueskyApi, rssHelper, newsletterUrlResolver)`; hàm cũ `subscribeViewModelFactory(relayUrl)` gọi nó với `null` nên không đổi hành vi (`SubscribeNostrPreviewRecreateDeviceTest` không đụng).
- **Chặn mạng ngoài 127.0.0.1:** khi API không dùng được `search()` rơi về `rssHelper.searchFeed` và `newsletterUrlResolver.resolveToRssUrl` (mạng thật); rig truyền hai đối tượng dùng một client từ chối host khác `127.0.0.1` và đếm.
- **Bài giả phải do đúng DID của hồ sơ viết:** `toArticles` bỏ bài có `author.did` khác DID hồ sơ; `BlueskyDeviceFixtures.post` cố định tác giả nên rig dựng bài riêng theo hồ sơ hiện tại.
- **Gõ DID:** `SocialFeedUrlResolver` không nhận `@did:...`, chỉ link `https://bsky.app/profile/did:...`; test dùng link.
- **12 test, không 10:** thêm `aSyncThatFindsNoOwnPosts…` để đột biến `r07` (nâng cấp trước khi kiểm bài rỗng) có test nhắm đúng; `aFailedSync` (máy chủ 500 ở hồ sơ) lỗi trước khi biết DID nên không bắt được `r07`.
- **Đột biến:** 8 (r01 đến r08) thay vì 7: tách `r02` (chỉ kiểm RSS cũ) khỏi `r01`; cả 8 làm đúng tập test dự định đỏ.
- **Số test JVM:** đo lại **3.797** (không dùng số cũ 3.776).
