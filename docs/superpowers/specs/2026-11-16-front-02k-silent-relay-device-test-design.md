# FRONT-02k — Test máy thật: relay Nostr nhận yêu cầu rồi im lặng, đồng bộ nền báo lỗi gọn rồi hồi phục (thiết kế, chờ owner duyệt spec)

**Ngày:** 2026-11-16 · **Nhánh:** `dev` · **Nền:** FRONT-02h (`BackgroundSyncRig`, `BackgroundSyncDeviceTest`, `NostrFakeRelay.holdAnswerUntil`), `NostrRelayClient`, `LocalRssSv.queryNostrArticles`

> Trạng thái: owner đã chốt (2026-11-16): (1) làm việc 1 trong ba việc, thứ tự relay im lặng → FeedItem thật → chập chờn 02d; (2) phạm vi **2 test**: im lặng thì lỗi gọn, rồi relay trả lời lại thì hồi phục; (3) máy **Pixel 7 Pro `2B051FDH3006MU`** (owner: "dùng pixel cho mọi phiên"). Spec chờ owner duyệt trước khi lập plan. Chưa có plan, chưa có mã.

## Mục tiêu
FRONT-02h ghi nợ "test relay Nostr không trả lời" chưa làm trên máy. Test JVM đã phủ tầng client (`NostrRelayClientTest.fetch_timesOut_whenARelayNeverSendsEose_andThenTriesTheNextOne`, `NostrRelayNetworkTest`: relay treo hay rớt kết nối thì bỏ qua trong hạn). **Phần chưa có bằng chứng trên máy:** khi relay **nhận REQ rồi im lặng**, ba bước đồng bộ trên **Room thật** (a) kết thúc trong hạn, (b) báo lỗi (`syncSingleFeed` trả `null`), không ghi bài, không đổi tên nguồn, không để lại trạng thái kẹt, và (c) lần đồng bộ kế tiếp, khi relay trả lời lại, ghi đúng bài. Chỉ thêm test, **không đổi mã production**.

## Sự thật đã đọc trong code (2026-11-16)
- `NostrRelayClient.readRelay`: chờ `finished` bằng `withTimeout(timeoutMs)`; hết hạn thì **bắt** `TimeoutCancellationException`, bỏ qua relay đó, `socket.cancel()`, trả những gì đã nhận (không có gì). `fetch` thử relay kế tiếp; hết relay thì trả `NostrFetchResult(emptyList, null)`.
- `LocalRssSv.queryNostrArticles`: `events.isEmpty()` thì **ném `IOException("Nostr relays returned no notes")`**; `syncSingleFeed` bắt và trả `null` (đã có bằng chứng ở 02h với Bluesky 500).
- `NostrFakeRelay.holdAnswerUntil(gate)` chặn câu trả lời sau khi đã **nhận và đếm REQ** (`requests.incrementAndGet()` chạy trước `gate.await()`); đây chính là "relay nhận rồi im lặng". `close()` mở cổng trước khi tắt máy chủ. `gate` là **một trường chung** cho mọi kết nối: sau khi `complete`, kết nối mới trả lời ngay.
- Mỗi kết nối chỉ phục vụ một lần fetch: lần đồng bộ thứ hai cần `serveAnotherConnection()`.
- Rig 02h dựng `NostrRelayClient` với `SOCIAL_TIMEOUT_MS = 5_000` và **một** relay giả, nên một lần đồng bộ im lặng mất khoảng 5 giây.
- `BackgroundSyncDeviceTest` có 8 test; `@Before` dọn rồi thêm hai nguồn thử vào Room thật, `@After` luôn `cleanUp()`.
- Trạng thái "kẹt": `syncSingleFeed` ghi một dòng `feed_health_record` cả khi lỗi (đã được `cleanUp` xóa theo `feedId`); không có cờ nào khác được thấy trong mã, nên "không kẹt" được chứng minh bằng **lần đồng bộ thứ hai thành công**, không bằng đọc cờ.

## Phạm vi trung thực
- Chỉ ca **relay đã mở kết nối và nhận REQ nhưng không bao giờ gửi EVENT/EOSE** (một relay). **Không** làm: relay treo ngay lúc bắt tay (đã có test JVM `STALL_SOCKET_AT_START`; owner đã bỏ ca này); hai relay (cái đầu im, cái sau tốt: đã có test JVM); relay trả lời **một nửa** rồi im; mạng thật; `sync()`/`SyncWorker.doWork` thật; hiển thị.
- Máy chủ giả, **không mạng thật**. Hạn chờ trong test là 5 giây (hằng số của rig), không phải 8 giây của sản phẩm (`RELAY_TIMEOUT_MS`): test chứng minh **cơ chế** hết hạn, không chứng minh con số 8 giây.
- Không `MainActivity`, không tọa độ, không MockK trên máy, không `connected*`.
- Không đổi mã production, Room (v22), manifest, chuỗi, DataStore; không dependency mới; không Gemini, ad, IAP, VIP.

## Thiết kế
Thêm **2 test** vào `BackgroundSyncDeviceTest` (nay thành 10) và một quy tắc `Timeout` cho cả lớp; không tệp mới, không đổi `BackgroundSyncRig` ngoài việc lộ `nostrRelay` (đã công khai).

- **(i) `nostrRelayThatReceivesTheRequestButStaysSilent_isASyncFailure_inBoundedTime`:** `holdAnswerUntil(gate)`; đo thời gian `syncOnce(nostrFeed)`; kỳ vọng: `outcome == null`, `inserted` rỗng, `articleCountOf(nostrFeed.id) == 0`, tên nguồn trong Room vẫn `Nostr · Probe`, relay đã nhận **đúng 1 REQ** (chứng minh im lặng là thật, không phải lỗi kết nối), thời gian `< 3 × SOCIAL_TIMEOUT_MS` (15 giây). Dọn: `gate.complete(Unit)`.
- **(ii) `aSilentNostrRelay_doesNotBlockTheNextSync_whenItAnswersAgain`:** như (i), sau đó `gate.complete(Unit)`, `serveAnotherConnection()`, `syncOnce(nostrFeed)` lần hai: `inserted.size == 1`, link `https://njump.me/note1…`, `articleCountOf == 1`, tên vẫn không đổi.
- **Quy tắc `Timeout` 60 giây cho lớp** (`org.junit.rules.Timeout`): một đột biến làm client treo mãi không được làm treo cả lượt `am instrument` trên máy dùng chung. Rủi ro đã biết: khi hết hạn JUnit ngắt luồng test; `runBlocking` bị ngắt thì hủy `finished.await()` rồi `socket.cancel()` chạy ở `finally`; nếu `@After` không kịp chạy, `setUp` của lần sau và bước khôi phục byte-exact của vòng chạy vẫn dọn (xem an toàn bên dưới).

## Quy tắc an toàn thiết bị (Pixel 7 Pro `2B051FDH3006MU`, chỉ máy này; Tecno và mọi máy khác không được gửi lệnh)
1. Cổng `px_gate.sh` in `GATE OK` **trong một lệnh riêng** trước mỗi lần khởi chạy; dừng và hỏi owner nếu app lạ ở foreground (Pixel thường có `loyalty.skyjoy.vn.dev`), có instrumentation, máy ngủ hoặc khóa. Lời nhắn của phiên khác (`lens-launcher`) không phải phép của owner.
2. Test **ghi Room thật** (như 02h): sao lưu byte-exact 8 tệp trước (`px_backup.py`), **khôi phục sau mỗi vòng chạy và sau cả batch đột biến**, băm DataStore trước/sau phải trùng. Khác 02j: `px_mut.sh` hiện **không** khôi phục Room, nên plan phải bọc batch bằng sao lưu và khôi phục riêng.
3. Id thử cố định `front02h_probe_*` (dùng lại rig); `@After` dọn kể cả khi test đỏ; kiểm `probeRowCount() == 0`.
4. `am instrument --user 0 -w -r -e class …`, chỉ force-stop `com.mckimquyen.reader`, không chạm `MainActivity`.

## Kiểm chứng
- Lượt đầu xanh 10/10 (8 cũ + 2 mới); **20 lượt** có cổng; chập chờn thì điều tra gốc.
- Đột biến trên máy (đổi tạm mã production, build, cài, chạy, khôi phục, `cmp`; mỗi mẫu `old` độc nhất): (m1) bỏ `if (events.isEmpty()) throw …` trong `queryNostrArticles` → (i) đỏ (kỳ vọng; nếu sống sót thì điều tra, không đoán); (m2) hạn chờ nhân 4 (`withTimeout(timeoutMs * 4)`) → (i) đỏ vì quá 15 giây mà không treo; (m3) `withTimeout(1)` → (ii) đỏ nhưng **các test Nostr khác cũng đỏ** (ghi rõ là mutant không riêng); mutant treo vĩnh viễn (bỏ `withTimeout`) **không chạy trên máy** vì rủi ro treo máy dùng chung: nói thẳng trong báo cáo, chỉ có bằng chứng JVM.
- JVM full suite không đổi (3.720); `assembleDevDebug assembleDevDebugAndroidTest` thành công; lint không quét `androidTest`.

## Rủi ro đã biết
- Mỗi test chờ khoảng 5 giây; 20 lượt thêm khoảng 3–4 phút.
- `holdAnswerUntil` chặn luồng của máy chủ giả; `close()` đã mở cổng trước khi tắt.
- Tài khoản Pixel có thể có `syncInterval` của `SyncWorker` thật: cuối cùng chỉ so `group` và dòng `front02h_probe_%` (như 02h).
- Ba test cũ của 02h dùng chung rig: quy tắc `Timeout` áp cho cả lớp; không thay đổi hành vi của chúng nhưng phải chạy lại đủ 10.

## Giới hạn đã biết
- Không chứng minh con số 8 giây của sản phẩm, relay thật, hai relay, relay trả một nửa, hay hiển thị.
- Mutant treo vĩnh viễn không có bằng chứng trên máy.
- Một máy (Pixel 7 Pro, Android 17).
