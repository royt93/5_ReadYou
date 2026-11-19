# FRONT-02z — Nostr: thử relay hai khi relay đầu không có bài nào hiện được (thiết kế, chờ owner duyệt spec)

**Ngày:** 2026-11-19 · **Nhánh:** `dev` · **Nền:** FRONT-02x (lọc chuỗi tự trả lời), FRONT-02y (`fetch` trả mọi sự kiện, cắt 20 sau lọc), `NostrRelayClient.fetch`, `NostrFeedMapper`

> Trạng thái: owner đã chốt trong brainstorm (2026-11-19, đổi ý so với FRONT-02y): (1) `fetch` tự hỏi mapper qua **một hàm thuần "bài hiện được" dùng chung**; (2) **chỉ dùng relay đầu tiên có bài hiện được, không gộp** hai relay; (3) nếu **cả hai** relay có sự kiện nhưng không bài nào hiện được thì `fetch` trả **rỗng** như "không có bài" (xem trước báo lỗi như hiện nay). Có **một tinh chỉnh** xin owner xem khi duyệt (mục "Tinh chỉnh": bài trống). Owner dặn bỏ qua quảng cáo. Spec chờ owner duyệt trước khi lập plan. Chưa có plan, chưa có mã.

## Mục tiêu
`NostrRelayClient.fetch` lặp qua các relay và **dừng ở relay đầu mà `result.notes.isNotEmpty()`**. Từ FRONT-02y, `notes` chứa mọi sự kiện hợp lệ (cả trả lời người khác), nên nếu relay đầu chỉ có trả lời người khác (không bài nào hiện được sau lọc) thì relay hai **không bao giờ được thử**: xem trước báo `web_feed_fetch_failed`, đồng bộ ném `IOException("Nostr relays returned no notes")`, dù relay hai có bài hiện được. Mục tiêu: dừng ở relay đầu có **ít nhất một bài hiện được**. Chỉ JVM; **không** giao diện, **không** chuỗi, **không** Room (v22), DataStore, manifest, `app/build.gradle`, dependency; **không đổi REQ** gửi relay; **không đổi chữ ký** `fetch` và `NostrFetchResult`.

## Sự thật đã đọc (2026-11-19)
- `NostrRelayClient.fetch` (dòng ~33 đến 43): `for (relay in relays) { if (requireSecure && !relay.startsWith("wss://")) continue; val result = readRelay(relay, authorHex); if (result.notes.isNotEmpty()) return result.copy(profileName = result.profileName ?: firstProfile); firstProfile = firstProfile ?: result.profileName }; return NostrFetchResult(emptyList(), firstProfile)`.
- Hai nơi gọi `fetch`: `LocalRssSv.queryNostrArticles` (`fetch(authorHex).notes`, rỗng thì `IOException`) và `SubscribeViewModel` (`runCatching { fetch(authorHex) }`, rồi `toArticles`, rỗng thì `web_feed_fetch_failed`).
- `NostrFeedMapper.keepOriginalsAndOwnThreads(events)` (private) là nơi duy nhất quyết định bài nào hiện (không phải trả lời, hoặc trả lời một bài cũng được giữ, không phụ thuộc thứ tự, chặn vòng lặp). `toArticles` = `keepOriginalsAndOwnThreads(events)` rồi sắp mới nhất trước, `take(MAX_ARTICLES_SHOWN)`, rồi `mapNotNull` **bỏ bài có `NostrKeys.noteId(id) == null` hoặc tiêu đề rỗng** (bài toàn khoảng trắng như fixture `EMPTY`).
- `NostrRelayClient` và `NostrFeedMapper` **cùng gói** `infrastructure.social.nostr`.
- Test sẵn có: `fetch_triesTheSecondRelay_whenTheFirstRefusesTheUpgrade`, `fetch_triesTheSecondRelay_whenTheFirstHasNoValidNotes` (relay đầu chỉ có sự kiện của người khác nên `notes` rỗng sau `isValid`), `fetch_returnsEmpty_whenBothRelaysFail`, `fetch_timesOut_whenARelayNeverSendsEose_andThenTriesTheNextOne`, `fetch_refusesARelayThatIsNotWss_whenSecurityIsRequired`; `MockWebServer` xếp hàng một phản hồi cho mỗi kết nối, nên số kết nối đếm được qua `server.requestCount`.
- Sự kiện ký thật có sẵn: `PLAIN`, `RICH`, `REPLY` (trả lời id `aaaa…` **không có trong tập**, nên là "trả lời người khác"), `OLDER`, `EMPTY` (bài trống), `FUTURE`, `LONG`, `QUOTES`, `MANY` (30). `REPLY` đủ để dựng "relay đầu chỉ có trả lời người khác".

## Tinh chỉnh (xin owner xem)
Nếu "hiện được" chỉ là `keepOriginalsAndOwnThreads(...).isNotEmpty()`, một relay chỉ có **bài trống** (như `EMPTY`) vẫn được coi là có bài và chặn relay hai, trong khi `toArticles` sẽ cho ra **không bài nào**. Cách làm đề xuất:
- Hàm công khai `NostrFeedMapper.visibleNotes(events): List<NostrEvent>` = các sự kiện **sẽ thành bài**: qua bộ lọc chuỗi **và** có `noteId` hợp lệ **và** tiêu đề không rỗng (cùng điều kiện mà `toArticles` đang dùng để bỏ bài).
- `toArticles` dùng chính `visibleNotes` **trước** khi sắp xếp và cắt 20 (một chỗ duy nhất cho luật). **Thay đổi hành vi nhỏ, có chủ ý:** trước đây 20 bài mới nhất được cắt rồi mới bỏ bài trống (nên 2 bài trống trong 20 mới nhất làm chỉ còn 18 bài hiện); nay bài trống không chiếm chỗ (luôn đủ tới 20 bài thật nếu có). Test cũ `repliesAndBlankNotesAreSkipped` và các test 20 bài không đổi kết quả (chúng không có bài trống trong 20 mới nhất). Test mới khóa hành vi này.
- Nếu owner **không muốn** đổi hành vi cắt 20, phương án thay: `visibleNotes` chỉ là bộ lọc chuỗi và chấp nhận rằng relay chỉ-có-bài-trống chặn relay hai (hiếm hơn nhiều, nhưng không đúng ý "không bài nào hiện").

## Phạm vi trung thực
- **Làm:** (a) `NostrFeedMapper.visibleNotes(events)` công khai, thuần; `toArticles` dùng nó; (b) `fetch` dừng ở relay đầu mà `visibleNotes(result.notes).isNotEmpty()`; (c) cả hai relay có sự kiện nhưng không bài nào hiện được thì `fetch` trả `NostrFetchResult(emptyList(), firstProfile)`.
- **Không làm:** gộp sự kiện hai relay; đổi REQ hay số khung; đổi `NostrFetchResult`; sửa `LocalRssSv`, `SubscribeViewModel`, `NostrEvent`; giao diện, chuỗi; thử relay thứ ba (chỉ có hai).
- **Không kiểm trên máy hay relay thật:** chỉ JVM (relay giả có WebSocket đã có sẵn trong `NostrRelayClientTest`).

## Thiết kế
1. **`NostrFeedMapper.visibleNotes(events)`** (công khai, có KDoc): `keepOriginalsAndOwnThreads(events)` rồi giữ sự kiện có `NostrKeys.noteId(id) != null` và `RssDescriptionParser.titleFromBody(escapeToHtml(content))` không rỗng. `toArticles` = `visibleNotes(events)` rồi `sortedByDescending { createdAt }`, `take(MAX_ARTICLES_SHOWN)`, `map` dựng `Article` (không còn `mapNotNull` bỏ bài; `noteId` và tiêu đề tính một lần cho mỗi bài, dùng chung hàm tính để không chép hai lần).
2. **`NostrRelayClient.fetch`:** trong vòng lặp, `if (NostrFeedMapper.visibleNotes(result.notes).isNotEmpty()) return result.copy(profileName = result.profileName ?: firstProfile)`; vẫn `firstProfile = firstProfile ?: result.profileName`; cuối cùng `NostrFetchResult(emptyList(), firstProfile)`. **Trả `result` nguyên (chưa lọc)**: mọi sự kiện hợp lệ của relay đó; `toArticles` lọc và cắt 20 như cũ.
3. **Thời gian và mạng:** vẫn tối đa 2 relay, mỗi relay tối đa 8 giây; lần hỏi relay hai chỉ xảy ra thêm khi relay đầu **có sự kiện nhưng không bài nào hiện được** (ca hiếm; xấu nhất 16 giây ở ca đó). Các ca thường không đổi.
4. **Hành vi người dùng:** tác giả mà relay đầu chỉ có trả lời người khác nay có xem trước và đồng bộ từ relay hai thay vì báo lỗi oan. Cả hai relay đều chỉ có trả lời người khác (hoặc bài trống): vẫn báo lỗi như hiện nay.

## Kiểm chứng
- **`NostrRelayClientTest` (JVM):** relay đầu chỉ có `REPLY` thì thử relay hai và trả bài của relay hai (kiểm `server.requestCount == 2` và id trả về); relay đầu có `PLAIN` thì **không** hỏi relay hai (`requestCount == 1`); cả hai relay chỉ có `REPLY` thì `notes` rỗng; relay đầu chỉ có `EMPTY` (bài trống) thì thử relay hai; tên hồ sơ: relay đầu có hồ sơ nhưng chỉ có `REPLY`, relay hai có bài và không có hồ sơ thì `profileName` là của relay đầu; cả hai chỉ có `REPLY` mà relay đầu có hồ sơ thì `fetch` trả rỗng kèm tên hồ sơ (`firstProfile`); các test sẵn có về relay hai vẫn xanh. **Sự kiện hồ sơ ký thật** có sẵn: `PROFILE`, `PROFILE_NEWER`, ... trong `NostrTestEvents`.
- **`NostrFeedMapperTest` / `NostrFeedMapperThreadTest` (JVM):** `visibleNotes` trả đúng tập sự kiện mà `toArticles` biến thành bài (so id) trên các kịch bản chuỗi, bài trống, trả lời người khác, vòng lặp; thêm test "bài trống không chiếm chỗ trong 20 bài" (22 bài thật mới nhất + bài trống mới hơn cho đủ 20 bài hiện); `toArticles` vẫn xanh mọi test cũ.
- **Đột biến JVM** (đổi tạm, chạy các lớp Nostr và sync, khôi phục, `cmp`, so sánh tự động tập test đỏ; **dự đoán, lệch thì đọc lý do**): (z1) `fetch` quay lại `result.notes.isNotEmpty()` → test "relay đầu chỉ có REPLY" và "chỉ có EMPTY" đỏ; (z2) `fetch` luôn hỏi cả hai relay → test "relay đầu có PLAIN thì không hỏi relay hai" đỏ; (z3) `fetch` gộp sự kiện hai relay → test không gộp đỏ (id của relay hai lẫn vào); (z4) bỏ `firstProfile` → test tên hồ sơ đỏ; (z5) `visibleNotes` bỏ bước lọc chuỗi → test mapper và test relay đầu chỉ có REPLY đỏ; (z6) `visibleNotes` bỏ lọc bài trống → test bài trống và test relay đầu chỉ có EMPTY đỏ; (z7) `toArticles` không dùng `visibleNotes` (lọc riêng cũ) → test "visibleNotes khớp toArticles" hoặc test bài trống không chiếm chỗ đỏ.
- JVM full suite đo lại (đỏ chập chờn: chạy riêng 3 lần + chạy lại cả bộ, ghi thẳng); `assembleDevDebug assembleDevDebugAndroidTest` thành công.
- Báo cáo `doc/task/done/FRONT-02z_nostr-try-second-relay_DONE.md`; cập nhật `doc/feature.md` (dòng FRONT-02y ghi giới hạn "không thử relay hai" đã khắc phục và dòng 02z mới) và task gốc FRONT-02 (giữ trong `todo/`).

## Rủi ro đã biết
- **`NostrRelayClient` phụ thuộc `NostrFeedMapper`** (cùng gói; chấp nhận được, ghi trong KDoc).
- **Thay đổi hành vi nhỏ của `toArticles`** (bài trống không chiếm chỗ): có chủ ý, có test, cần owner duyệt (mục "Tinh chỉnh").
- **Xấu nhất 16 giây** ở ca hiếm khi relay đầu chậm và chỉ có trả lời người khác.
- **Không gộp hai relay:** chuỗi nằm rải hai relay vẫn có thể cụt (owner chọn không gộp).
- **`visibleNotes` chạy thêm một lần mỗi relay** trên tối đa 50 sự kiện; chi phí không đáng kể (lần đầu tính tiêu đề dùng `Html.fromHtml`, cùng chi phí với `toArticles` hiện có).

## Giới hạn đã biết
- Chỉ JVM; không kiểm relay thật hay máy thật.
- Chuỗi dài hơn 50 sự kiện gần nhất vẫn cụt (REQ không đổi).

## Điều chỉnh khi thực thi
- Làm đúng plan (bảng "Điều chỉnh so với spec" của plan, 2026-11-19): Task 1 mapper (`visibleNotes`), Task 2 điều kiện dừng của `fetch`; hai tệp production, không đổi chữ ký `fetch`.
- **Mutation:** ba dự đoán của plan quá hẹp (z3, z4, z7 làm đỏ thêm test sẵn có có liên quan); đã đọc lý do, sửa bảng plan và `z_check.py`; không có đột biến sống sót.
- **JVM đo lại:** 3.862 test, 0 lỗi (đúng kỳ vọng 3.852 + 10).
