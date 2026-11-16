# FRONT-02h — Test máy thật: đồng bộ nền của nguồn BLUESKY và NOSTR trên Room thật (thiết kế, chờ owner duyệt spec)

**Ngày:** 2026-11-16 · **Nhánh:** `dev` · **Nền:** FRONT-02b/02c (Nostr), FRONT-02g (Bluesky API), FRONT-02e (relay giả `NostrFakeRelay`, dựng `SubscribeViewModel` từ `RApp`), `LocalRssSvNostrSyncTest`, `LocalRssSvBlueskySyncTest` (JVM, dịch vụ giả)

> Trạng thái: owner đã chốt trong brainstorm (2026-11-16): (1) Room **thật** của Tecno, chỉ đồng bộ **một** nguồn thử rồi xóa; (2) máy chủ **giả** cho cả Bluesky và relay Nostr, chạy trong tiến trình test; (3) tự dựng `LocalRssSv`, lấy DAO và dịch vụ thật từ `RApp`, chỉ thay hai client; (4) an toàn dữ liệu: sao lưu byte-exact + id riêng + dọn đúng id + đếm dòng trước/sau. Spec chờ owner duyệt trước khi lập plan. Chưa có plan, chưa có mã.

## Mục tiêu
Hiện chỉ có test JVM với dịch vụ giả chứng minh nguồn `BLUESKY` và `NOSTR` đồng bộ nền ghi bài và không đổi tên/icon. Thêm bằng chứng trên **máy thật**: Room thật, DAO thật, ba bước thật của đường đồng bộ, cho **một nguồn thử mỗi loại**. Chỉ thêm test, **không đổi mã production**.

## Sự thật đã đọc trong code (2026-11-16)
- Đường đồng bộ nền: `SyncWorker.doWork` → `rssService.get().sync(this)` → `AbstractRssRepository.sync` (`AbstractRssRepository.kt:89`). `sync()` lặp **mọi** feed của tài khoản (`feedDao.queryAll(accountId)`, chia nhóm 6, `syncFeed` song song), sau đó `smartFilterManager.applyRules` → `articleDao.insertListIfNotExist` → `retractionRadar.recordBaselines` → `watchdogManager.checkAndNotify` → thông báo → `smartCollectionMatcher`. `SyncWorker` còn chạy `precacheLatestUnread`, `semanticEmbeddingIndexer`, `oracleClaimExtractor`, `oracleDueReminder`.
- **Vì vậy `sync()` và `SyncWorker.doWork` KHÔNG được gọi trong test này:** chúng quét mọi nguồn thật của máy bằng mạng thật và có thể đăng thông báo.
- `syncSingleFeed(feed)` là hàm **công khai** (`AbstractRssRepository.kt:192`): đọc bài cuối cùng, `fetchNewArticles` (chọn nhánh BLUESKY/NOSTR trong `LocalRssSv.queryFeedArticles`), trả `FeedWithArticle?` (`null` khi lỗi). Nó **không** ghi bài; bước ghi là `articleDao.insertListIfNotExist` trong `sync()`. Nhưng nó **có ghi một dòng `feed_health_record`** (`feedHealthDao.upsert`, cả khi thành công lẫn khi lỗi, khóa là `feedId`), và đọc `retractionRadar.isEnabled()` (một cờ DataStore thật) trong `fetchNewArticles`. Bảng `feed_health_record` **không** có khóa ngoại tới `feed` (đọc `FeedHealthRecord.kt`), nên xóa nhóm tạm **không** dọn nó: phải xóa riêng theo `feedId`, và phải được đếm trong kiểm "không dấu vết".
- Khóa ngoại: `feed.groupId` → `group.id` (CASCADE); `article.feedId` → `feed.id` (CASCADE). Xóa nhóm tạm xóa luôn feed và bài thử.
- `BlueskyApiClient` có constructor `internal` nhận `baseUrl`; `NostrRelayClient` có constructor `internal` nhận danh sách relay; cả hai gọi được từ `androidTest` (FRONT-02e đã làm với `NostrRelayClient`).
- `RApp` (`@HiltAndroidApp`, là `Application` của tiến trình test) có sẵn các field `@Inject` công khai: `androidDatabase`, `rssHelper`, `notificationHelper`, `workManager`, `ioDispatcher`, `okHttpClient`, `localRssSv`, ... (`RApp.kt:76-129`). `localRssSv` thật cầm client mạng thật nên **không dùng được** cho test này.
- Dựng tay được vì `@Inject constructor` nông: `WatchdogEngine()`, `WebFeedScraper(WebFeedHttpClient(), WebFeedParser())`, `SmartFilterManager(context)`, `ArticleHistoryRepository(dao)`, `RetractionRadarService(context, dao, history)`.
- **Chưa biết lấy ở đâu (plan phải kiểm bằng biên dịch, và dừng hỏi nếu không lấy được):** `SmartCollectionMatcher` (cần `SmartCollectionDao`, `SemanticSearchEngine(ArticleEmbeddingDao)`, `ArticleDao`, dispatcher mặc định), `WatchdogManager` (cần `applicationScope` và `NotificationHelper`), `WidgetRefreshNotifier` (chỉ thấy bản cài ở `HomeViewModel`, `TriageViewModel`; có thể cần một bản vô hại tự viết trong test).

## Phạm vi trung thực
- Test lặp lại **đúng ba bước** của một vòng đồng bộ cho **một** nguồn: `syncSingleFeed` → `smartFilterManager.applyRules` → `articleDao.insertListIfNotExist`. Nó đọc cờ Radar thật nhưng nhánh "quét toàn bộ" của `fetchNewArticles` chỉ chạy khi `latestLink != null` và Radar bật; test chạy lần đầu trên feed chưa có bài (`latestLink == null`) nên **không** chứng minh nhánh đó. **Không** chứng minh vòng lặp mọi nguồn, `precache`, chỉ mục ngữ nghĩa, nhắc kèo, thông báo, `WorkManager` lên lịch, `smartCollectionMatcher`, `watchdogManager`, `retractionRadar`.
- Máy chủ giả, **không mạng thật**: bằng chứng mạng thật của hai nguồn đã có ở FRONT-02b/02c (Nostr) và FRONT-02g (Bluesky).
- **Không** `MainActivity` (quảng cáo xen kẽ ~18 giây), không tọa độ, không MockK trên máy, không Pixel (kể cả đọc).
- Không đổi mã production, Room (v22), manifest, chuỗi, DataStore; không dependency mới (`mockwebserver3` đã có ở `androidTest`); không Gemini, ad, IAP, VIP.

## Thiết kế
Các tệp mới trong `app/src/androidTest/java/com/mckimquyen/reader/domain/sv/`:
1. `BackgroundSyncRig.kt`: dựng `LocalRssSv` từ `RApp` với `BlueskyApiClient` và `NostrRelayClient` trỏ vào hai máy chủ giả; tạo nhóm tạm và hai feed thử (id cố định bắt đầu `front02h_probe_`), gồm tên và icon cố định; hàm `syncOnce(feed)` thực hiện ba bước trên và trả số bài đã chèn; hàm `cleanUp()` xóa nhóm tạm (CASCADE xóa feed và bài) và xóa riêng các dòng `feed_health_record` của hai feed thử.
2. `BackgroundSyncDeviceTest.kt`: các test dưới đây.

Các test dự kiến (6):
- **(a) Bluesky ghi bài:** máy chủ giả trả một hồ sơ và N mục (gồm bài chính chủ, đăng lại, trả lời); sau `syncOnce`, Room có đúng số bài chính chủ, `feedId` đúng, link dạng `https://bsky.app/profile/<did>/post/<rkey>`; **tên và icon của feed không đổi** dù hồ sơ giả đưa tên và avatar khác.
- **(b) Nostr ghi bài:** relay giả trả ghi chú và hồ sơ đã ký; Room có đúng số ghi chú hợp lệ, link `https://njump.me/note1…`; **tên không đổi** dù hồ sơ có tên khác.
- **(c) Chạy hai lần:** lần hai không chèn trùng (`insertListIfNotExist` trả danh sách rỗng).
- **(d) Máy chủ giả lỗi:** `syncSingleFeed` trả `null`, Room không có bài mới, tên không đổi; kiểm riêng Bluesky 500 và relay không trả lời.
- **(e) Bluesky `NotFound`:** coi như lỗi đồng bộ, không ghi gì.
- **(f) Không dấu vết:** số dòng `feed`, `article` và `feed_health_record` **ngoài** nhóm tạm bằng nhau trước và sau; sau `cleanUp()`, không còn dòng nào có id `front02h_probe_` ở cả ba bảng (hai bảng đầu qua CASCADE khi xóa nhóm tạm, `feed_health_record` xóa riêng theo `feedId`).

## Quy tắc an toàn thiết bị (Tecno KJ7 `115333744A005844`, chỉ máy này)
1. **Cổng chỉ đọc** (`/Users/loitran/.claude/tmp-f29/gate.sh`) in `GATE OK` **trong một lệnh riêng** trước mỗi lần khởi chạy; nếu dừng (máy ngủ, khóa, app khác ở foreground, xoay lệch), **dừng và hỏi owner**, không tự thao tác. Tecno có thể mất kết nối adb: là lỗi kết nối, không phải lỗi mã.
2. **Sao lưu byte-exact** 6 tệp (`backup.py`) rồi khôi phục (`restore.py`), băm DataStore đối chiếu; không xóa DataStore. Lưu ý Room thật có WAL: `backup.py` đã sao lưu cả `-wal` và `-shm`.
3. Id thử cố định; `@After` dọn **kể cả khi test đỏ**; so số dòng `feed`, `article`, `feed_health_record` trước/sau.
4. `am instrument --user 0 -w -r -e class …`, không `connected*`; chỉ force-stop `com.mckimquyen.reader`; không chạm `MainActivity`.
5. Phiên Claude khác từng dùng chung máy: nếu cổng cho thấy app lạ ở foreground, dừng.

## Kiểm chứng
- Đột biến chạy trên máy (đổi tạm mã production, build, cài, chạy, khôi phục, `cmp`): bỏ nhánh `BLUESKY` trong `queryFeedArticles` → (a) đỏ; thêm đổi tên feed trong `queryBlueskyArticles` → (a) đỏ; bỏ `articles.isEmpty()` guard của Bluesky → (d) đỏ; bỏ nhánh `NOSTR` → (b) đỏ; thay `insertListIfNotExist` bằng `insert` ghi đè → (c) đỏ.
- Chạy lặp **20 lượt** có cổng, sao lưu, khôi phục; chập chờn thì điều tra gốc, không gõ lại bừa.
- JVM full suite không đổi (3.705); `assembleDevDebug assembleDevDebugAndroidTest` thành công; lint không quét `androidTest` (nói thẳng trong báo cáo).

## Rủi ro đã biết
- Test ghi vào **Room thật** của máy (`feed`, `article`, `feed_health_record`); dọn thất bại giữa chừng để lại nhóm, feed và dòng sức khỏe có id `front02h_probe_`; vì vậy có sao lưu và khôi phục byte-exact và dọn trong `@After`.
- Một phụ thuộc của `LocalRssSv` có thể không lấy được từ `RApp`; khi đó plan dừng và hỏi (không đổi mã production để lách).
- `WatchdogManager`/`SmartCollectionMatcher` dựng tay có thể khởi động luồng nền hay đọc `SharedPreferences` thật; cần kiểm trước.
- Cổng Tecno có thể dừng vì máy ngủ hoặc mất adb.
- Chập chờn kiểu FRONT-02d chưa rõ gốc có thể tái xuất.

## Giới hạn đã biết
- Không chứng minh `SyncWorker.doWork`, `sync()`, `WorkManager` thật, mạng thật trong đường đồng bộ, thông báo, hay hiển thị trên màn hình.
- Một máy (Tecno KJ7, Android 14), không so được với Pixel hay A50s.

## Điều chỉnh khi thực thi
- **Ba phụ thuộc "chưa biết" đều lấy được** từ `RApp` hoặc `@Inject constructor` nông; `WidgetRefreshNotifier` là `fun interface`, dựng bằng lambda `WidgetRefreshNotifier { }`.
- **Địa chỉ Nostr** của feed thử phải là `nostr:<npub>` (bech32) qua `NostrFeedMapper.feedUrl`, **không** `nostr:<hex>`: lượt chạy đầu đỏ 2/8 vì lỗi này của rig.
- **`NostrFakeRelay.serveAnotherConnection()`** thêm vào (mỗi kết nối chỉ phục vụ một lần fetch); test 02e không đổi.
- **Không so số dòng toàn bảng trước/sau:** tài khoản Tecno có `syncInterval = 30` nên một `SyncWorker` thật có thể thêm dòng của nguồn khác trong lúc test; chỉ so `group` và dòng `front02h_probe_%`.
- **Đột biến `m04`** ghi thật vào Room (`feedDao.update(feed.copy(name = ...))`), vì chỉ đổi đối tượng trong bộ nhớ sẽ không bị test (so tên trong Room) bắt.
- **Chưa làm:** test "relay Nostr không trả lời".
