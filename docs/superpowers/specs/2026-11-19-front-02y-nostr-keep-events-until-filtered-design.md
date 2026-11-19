# FRONT-02y — Nostr: cắt 20 bài sau khi lọc, không phải trước (thiết kế, chờ owner duyệt spec)

**Ngày:** 2026-11-19 · **Nhánh:** `dev` · **Nền:** FRONT-02x (chuỗi tự trả lời; để lại giới hạn này), `NostrRelayClient`, `NostrFeedMapper`

> Trạng thái: owner đã chốt trong brainstorm (2026-11-19): (1) **giữ tối đa 50 sự kiện đã tải** trong `fetch`, để `toArticles` cắt 20 **sau** khi lọc; (2) **không** tăng yêu cầu gửi relay (REQ vẫn `limit=50`); (3) `fetch` vẫn **dừng ở relay đầu có bài**, không thử relay hai. Owner dặn bỏ qua quảng cáo. Spec chờ owner duyệt trước khi lập plan. Chưa có plan, chưa có mã.

## Mục tiêu
`NostrRelayClient.readRelay` sắp mọi sự kiện kind 1 đã tải (cả trả lời người khác) rồi `take(MAX_NOTES_KEPT = 20)`, **trước** khi `NostrFeedMapper.toArticles` lọc. Tác giả hay trả lời người khác nên có thể làm các bài và chuỗi tự trả lời bị đẩy ra ngoài 20 trước khi bộ lọc của FRONT-02x chạy. Mục tiêu: bỏ cắt sớm đó. Chỉ JVM; **không** giao diện, **không** chuỗi, **không** Room (v22), DataStore, manifest, `app/build.gradle`, dependency; **không đổi mạng**: REQ vẫn `limit=50`, vẫn đọc tối đa 50 khung, vẫn 8 giây, vẫn 64 KiB mỗi khung.

## Sự thật đã đọc (2026-11-19)
- `NostrRelayClient.readRelay` (dòng 111): `notes = found.values.sortedByDescending { it.createdAt }.take(MAX_NOTES_KEPT)`. `found` chỉ chứa sự kiện đã qua `isValid(authorHex)` (đúng id, chữ ký, đúng tác giả, kind 1). Đọc dừng sau `MAX_EVENTS_READ = 50` khung `EVENT` đúng subscription (cả kind 0 lẫn kind 1 đều tính), nên `found` ≤ 50.
- `NostrFeedMapper.toArticles` đã `take(NostrRelayClient.MAX_NOTES_KEPT)` **sau** khi lọc (FRONT-02x), nên bước cắt ở relay là thừa và chỉ gây mất bài.
- `fetch` lặp qua relay, dừng ở relay đầu mà `result.notes.isNotEmpty()`; quyết định của owner là giữ nguyên.
- `.notes` chỉ được dùng ở `LocalRssSv.queryNostrArticles` (`.notes` rồi `isEmpty()` và `toArticles`) và `SubscribeViewModel` (`events = it.notes`); không chỗ nào giả định ≤ 20.
- Hằng `MAX_NOTES_KEPT` được tham chiếu ở: `NostrFeedMapper` (cắt 20), `NostrRelayClientTest` (dòng ~116), `NostrFeedMapperTest` (dòng ~92), `NostrFeedMapperThreadTest` (dòng ~109). `MAX_EVENTS_READ` được dùng ở `NostrRelayClientTest` (2 chỗ) và trong chính `NostrRelayClient`.
- Bộ nhớ: ≤ 50 sự kiện, mỗi khung ≤ 64 KiB; hiện `found` đã giữ chừng đó trước khi cắt, nên không thêm đỉnh bộ nhớ đáng kể.

## Phạm vi trung thực
- **Làm:** (a) `readRelay` trả **mọi** sự kiện hợp lệ đã tải (≤ 50), mới nhất trước, không cắt; (b) đổi tên hằng `MAX_NOTES_KEPT` thành `MAX_ARTICLES_SHOWN` (vẫn 20, nay chỉ nghĩa "số bài hiện ra", đặt ở nơi dùng nó); (c) sửa test đang khóa con số 20 ở `fetch`; (d) thêm test chứng minh chuỗi sau 25 trả lời người khác vẫn hiện.
- **Không làm:** tăng `limit` REQ hay `MAX_EVENTS_READ`; thử relay hai khi relay đầu chỉ có trả lời người khác; kéo logic lọc vào `NostrRelayClient`; đổi `NostrFetchResult`; sửa `LocalRssSv`/`SubscribeViewModel`.
- **Không kiểm trên máy hay relay thật:** chỉ JVM (relay giả có WebSocket đã có sẵn trong `NostrRelayClientTest`).

## Thiết kế
1. **`NostrRelayClient.readRelay`:** `notes = found.values.sortedByDescending { it.createdAt }` (bỏ `.take(...)`). Giữ nguyên mọi thứ khác (REQ, hằng `MAX_EVENTS_READ`, đóng socket, hồ sơ).
2. **Hằng:** bỏ `NostrRelayClient.MAX_NOTES_KEPT`; thêm `NostrFeedMapper.MAX_ARTICLES_SHOWN = 20` (công khai, có chú thích: số bài tối đa hiện cho một nguồn Nostr, áp dụng sau khi lọc). `toArticles` dùng hằng này. Mọi test tham chiếu hằng cũ đổi sang hằng mới.
3. **Hợp đồng `NostrFetchResult.notes`:** từ nay là "mọi sự kiện hợp lệ đã đọc, mới nhất trước, tối đa `MAX_EVENTS_READ`"; ghi chú KDoc ở `NostrFetchResult`.
4. **Hành vi người dùng:** tác giả hay trả lời người khác thấy chuỗi của họ hiện lại (trong 50 sự kiện gần nhất); nguồn đã đăng ký có thể thấy thêm bài chuỗi cũ hơn ở lần đồng bộ sau (bài đã lưu không đổi vì so theo link). Không đổi số lần gọi mạng.

## Kiểm chứng
- **`NostrRelayClientTest`:** `fetch_keepsTheTwentyNewest_newestFirst` đổi thành `fetch_keepsEveryValidEventItRead_newestFirst` (30 bài mẫu `MANY` đều trả về, mới nhất trước); thêm `fetch_returnsAtMostFiftyEvents` (relay giả gửi 55 sự kiện hợp lệ, `fetch` trả đúng 50: giới hạn đọc, không phải giới hạn cắt); test hiện có `fetch_stopsReadingAfterFiftyFrames` và `fetch_countsAnOversizedFrameAmongTheFiftyItMayRead` vẫn xanh. Test REQ chính xác (dòng ~80) **không đổi**.
- **`NostrFeedMapperTest` / `NostrFeedMapperThreadTest`:** đổi hằng sang `MAX_ARTICLES_SHOWN`; thêm test mapper: danh sách 40 sự kiện gồm 25 trả lời người khác (mới hơn) và một chuỗi tự trả lời cũ hơn thì chuỗi hiện đủ và tổng ≤ 20.
- **Ca đồng bộ (`LocalRssSvNostrSyncTest`):** relay giả trả 30 sự kiện (25 trả lời người khác mới hơn, một chuỗi 3 mảnh cũ hơn) thì đồng bộ ghi cả chuỗi (trước sửa, `fetch` thật sẽ đã cắt chuỗi; ở tầng này MockK trả `NostrFetchResult` có sẵn nên ca này chỉ khóa hành vi của mapper; bằng chứng tầng relay là test `NostrRelayClientTest`).
- **Đột biến JVM** (đổi tạm, chạy các lớp Nostr và sync, khôi phục, `cmp`, so sánh tự động tập test đỏ; **dự đoán, lệch thì đọc lý do**): (y1) thêm lại `.take(20)` ở relay → test "giữ mọi sự kiện" đỏ; (y2) cắt ở 49 → `fetch_returnsAtMostFiftyEvents` đỏ; (y3) bỏ `sortedByDescending` → test thứ tự đỏ; (y4) mapper bỏ `take(MAX_ARTICLES_SHOWN)` → `atMostTwentyArticles` (cả hai lớp mapper) đỏ; (y5) đổi `MAX_ARTICLES_SHOWN` thành 21 → các test hằng/20 đỏ; (y6) thêm `.take(10)` ở mapper → test 20 bài đỏ.
- JVM full suite đo lại (đỏ chập chờn: chạy riêng 3 lần + chạy lại cả bộ, ghi thẳng); `assembleDevDebug assembleDevDebugAndroidTest` thành công.
- Báo cáo `doc/task/done/FRONT-02y_nostr-keep-events-until-filtered_DONE.md`; cập nhật `doc/feature.md` (dòng FRONT-02x ghi giới hạn đã khắc phục và dòng 02y mới) và task gốc FRONT-02 (giữ trong `todo/`).

## Rủi ro đã biết
- **Đổi tên hằng chạm nhiều test:** dễ sót; biên dịch báo lỗi nếu sót.
- **Bộ nhớ:** tối đa 50 khung có thể lên vài MB nếu mọi khung gần 64 KiB; `found` đã giữ chừng đó trước đây, nên không đổi đỉnh bộ nhớ.
- **Bài đã lưu không đổi, nhưng nguồn cũ có thể thấy thêm bài cũ hơn** (trong 50 sự kiện gần nhất) ở lần đồng bộ sau.
- **Chuỗi vẫn có thể cụt:** nếu mảnh đầu nằm ngoài 50 sự kiện gần nhất, các mảnh sau bị bỏ (an toàn). Chưa kiểm trên relay thật.
- **Trường hợp hiếm giữ nguyên:** relay đầu chỉ có trả lời người khác thì không thử relay hai (owner chọn giữ).

## Giới hạn đã biết
- Chỉ JVM; không kiểm trên relay thật hay máy thật.
- Không tăng `limit` REQ; chuỗi dài hơn 50 sự kiện gần nhất vẫn cụt.

## Điều chỉnh khi thực thi
- **Không có test "55 sự kiện hợp lệ":** chỉ có 38 sự kiện kind 1 ký thật (8 lẻ + 30 trong `MANY`). Giới hạn đọc 50 chứng minh bằng số khung (khung rác cũng tính): 12 rác + 38 hợp lệ = 50 khung đọc hết; 13 rác = 51 khung thì khung hợp lệ cuối không được đọc.
- **Bỏ ca đồng bộ MockK** (không đi qua `readRelay`, trùng test mapper).
- **Đột biến:** thêm `y7` (mapper cắt 20 trước khi lọc, đúng lỗi gốc); `y2` dùng cắt 30 thay cho 49 (cắt 49 là đột biến tương đương vì chỉ có 38 sự kiện hợp lệ); dự đoán y5 sai một chỗ (hằng 21 cũng làm đỏ test 30 bài mẫu), đã sửa bảng.
- **Kết quả:** +4 test, mutation 7/7, JVM 3.852, 0 lỗi; đúng ba tệp production.
