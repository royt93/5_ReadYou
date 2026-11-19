# FRONT-02x — Nguồn Nostr: hiện chuỗi bài của chính tác giả (thiết kế, chờ owner duyệt spec)

**Ngày:** 2026-11-18 · **Nhánh:** `dev` · **Nền:** FRONT-02b (nguồn Nostr, bỏ mọi trả lời), FRONT-02w (`nprofile`), `NostrEvent`, `NostrFeedMapper`

> Trạng thái: owner đã chốt trong brainstorm (2026-11-18): (1) hiện **chỉ chuỗi bài của chính tác giả** (tác giả trả lời bài trước của họ); trả lời bài của người khác vẫn bị bỏ; (2) **mỗi bài trong chuỗi là một bài riêng**, theo thời gian (không gộp); (3) nhận biết bằng **kiểm id**: bài được trả lời phải nằm trong các bài đã tải và đã kiểm chữ ký của cùng tác giả (không tin thẻ `p`). Có **một tinh chỉnh** ở thiết kế dưới đây (bài đích cũng phải là bài được giữ), xin owner xem khi duyệt. Spec chờ owner duyệt trước khi lập plan. Chưa có plan, chưa có mã.

## Mục tiêu
Hiện nay mọi bài có thẻ `e` đều bị coi là "trả lời" và bị bỏ (`NostrEvent.isReply` rồi `NostrFeedMapper.toArticles` lọc). Chuỗi bài dài của chính tác giả (bài 2, 3… nối bài 1) vì vậy mất mảnh. Mục tiêu: giữ các mảnh của chuỗi **tự trả lời**, vẫn bỏ trả lời bài của người khác. Chỉ JVM; **không** giao diện mới, **không** chuỗi mới, **không** Room (v22), DataStore, manifest, `app/build.gradle`; không dependency mới; không thêm lệnh gọi mạng; không đổi truy vấn relay (vẫn `kinds=[1]`, `authors=[khóa]`, `limit=50`, giữ 20 bài).

## Sự thật đã đọc (2026-11-18)
- `NostrEvent.isReply` = `tags.any { it.firstOrNull() == "e" }`; chỉ `NostrFeedMapper.toArticles` dùng nó (`.filterNot { it.isReply }`). Test khóa hành vi cũ: `NostrEventTest.isReply_isTrueOnlyWithAnETag`, `NostrFeedMapperTest.repliesAndBlankNotesAreSkipped` (dùng `NostrTestEvents.REPLY`: thẻ `["e","aaaa…","","reply"]` trỏ id không có trong tập).
- NIP-10 (đọc từ nostr-protocol/nips ngày 2026-11-18): thẻ `["e", <id>, <relay>, <marker>, <pubkey>]`, marker `reply` (bài đang được trả lời), `root` (gốc chuỗi), `mention` (chỉ nhắc, không phải trả lời); trả lời thẳng vào gốc chỉ có một thẻ `root`; kiểu cũ **không dấu**: thẻ đầu là gốc, thẻ cuối là bài được trả lời, thẻ giữa là nhắc; một thẻ `e` không dấu là trả lời thẳng bài đó; `q` là trích dẫn, không phải trả lời.
- `NostrRelayClient` chỉ nhận sự kiện hợp lệ (`isValid(authorHex)`: đúng id, chữ ký, đúng tác giả) và giữ tối đa 20 bài mới nhất; `toArticles` nhận danh sách `NostrEvent` đã kiểm (không kiểm lại chữ ký). `toArticles` được gọi ở `SubscribeViewModel` (xem trước) và `LocalRssSv.queryNostrArticles` (đồng bộ). Bài đã lưu không đổi vì `insertListIfNotExist` so theo `link` (`https://njump.me/<note1…>`).
- **Điều quan trọng:** relay trả `limit=50` sự kiện (kind 1) của tác giả, nhưng `NostrRelayClient.fetch` chỉ trả `take(MAX_NOTES_KEPT = 20)` bài mới nhất sau khi sắp xếp, bao gồm cả trả lời. Một chuỗi dài hay trả lời nhiều có thể làm 20 chỗ đó đầy bằng trả lời người khác trước khi `toArticles` được lọc. Xem "Rủi ro".

## Phạm vi trung thực
- **Làm:** (a) `NostrEvent` hiểu NIP-10: biết một bài là trả lời hay chỉ nhắc, và **id bài được trả lời** khi đọc được; (b) `NostrFeedMapper.toArticles` giữ bài **không phải trả lời** và bài **trả lời một bài mà chính nó được giữ** (cùng tập, cùng tác giả); (c) đổi hai test cũ khóa hành vi "bỏ mọi trả lời".
- **Không làm:** hiện trả lời bài của người khác; gộp chuỗi thành một bài; dùng thẻ `p`; đổi giới hạn 50/20, relay, REQ; giao diện, chuỗi mới; đánh dấu "bài 2/3"; sửa bài đã lưu; xử lý bài đích nằm ngoài tập tải.
- **Không kiểm trên máy hay mạng thật:** chỉ JVM (đã có test relay giả cho `NostrRelayClient`, không đổi).

## Thiết kế
1. **`NostrEvent`** (tên và kiểu cụ thể do plan chốt sau khi đọc mã; ý nghĩa như sau):
   - `isReply`: có **ít nhất một thẻ `e` không phải `mention`**. Thẻ `e` mà marker (phần tử thứ 4) là `mention` thì **không** tính là trả lời. Bài chỉ có thẻ `mention` hoặc không có thẻ `e` là bài thường. Một thẻ `e` có id hỏng (không phải hex chữ thường 64 ký tự) vẫn tính là trả lời (để bài như thế **không** thành bài thường).
   - `repliedToId: String?`: id bài được trả lời theo NIP-10, **chỉ khi là hex chữ thường 64 ký tự**, ngược lại `null`. Thứ tự ưu tiên: thẻ có marker `reply`; nếu không có thì thẻ marker `root`; nếu không có thì (kiểu cũ) **thẻ `e` cuối cùng không dấu và không phải `mention`**. Nhiều thẻ `reply` thì lấy thẻ **cuối** (rõ ràng, có test).
2. **`NostrFeedMapper.toArticles`:** bài **được giữ** nếu `!isReply`, hoặc nếu `isReply` và `repliedToId != null` và bài có id đó (trong cùng danh sách đầu vào, sau khi bỏ trùng) **cũng được giữ** (đệ quy có nhớ, **có chặn vòng lặp**: đang xét thì coi là không giữ). Bài không giữ thì bỏ như hiện nay. Sau đó giữ nguyên các bước hiện có: bỏ trùng theo id, sắp mới nhất trước, `take(MAX_NOTES_KEPT)`, bỏ bài rỗng, thoát HTML, tiêu đề, link `njump`.
   - **Vì sao "đích cũng phải được giữ" (tinh chỉnh):** nếu mảnh B trả lời người khác (bị bỏ) rồi mảnh C trả lời B, chỉ kiểm "B có trong tập" sẽ hiện C mà không có B, đúng loại câu cụt ngữ cảnh mà owner không muốn. Với quy tắc này, C bị bỏ theo B.
   - **Thứ tự:** việc giữ hay bỏ **không** phụ thuộc thứ tự đầu vào hay thời gian (một bài trả lời có thể có `created_at` nhỏ hơn bài đích; không giả định).
3. **Nơi gọi không đổi:** `SubscribeViewModel` và `LocalRssSv.queryNostrArticles` vẫn gọi `toArticles(events, ...)`; không đổi chữ ký.
4. **Bài đã lưu** không đổi; nguồn đã đăng ký sẽ **thấy thêm** bài chuỗi ở lần đồng bộ sau (bài mới theo link).

## Kiểm chứng
- **`NostrEventTest` (JVM):** `isReply`: không thẻ `e`, thẻ `e` không dấu, `reply`, `root`, thẻ chỉ `mention`, hỗn hợp `mention` và `reply`; `repliedToId`: marker `reply`; chỉ `root`; `root` và `reply` cùng có (lấy `reply`); không dấu một thẻ; không dấu nhiều thẻ (lấy cuối, bỏ `mention`); `mention` đứng cuối bị bỏ qua; id sai độ dài, chữ hoa, rỗng, thẻ `e` thiếu phần tử → `null` nhưng `isReply` vẫn đúng; nhiều thẻ `reply` (lấy cuối). Test cũ `isReply_isTrueOnlyWithAnETag` được viết lại theo ngữ nghĩa mới và vẫn khóa các ca cũ.
- **`NostrFeedMapperTest` (JVM):** chuỗi tự trả lời (A gốc, B trả lời A, C trả lời B) giữ cả ba, sắp mới nhất trước; trả lời bài người khác (id không có trong tập) bị bỏ; trả lời mà id đích có trong tập nhưng chính đích là trả lời bài người khác thì bị bỏ (C trả lời B, B trả lời X lạ); `mention`-only được giữ như bài thường; vòng lặp (B trả lời C, C trả lời B) không treo và cả hai bị bỏ; bài trùng id giữ một; thứ tự đầu vào đảo vẫn cho cùng kết quả; `take(20)` vẫn giữ 20; tiêu đề mỗi mảnh là 80 ký tự đầu của chính mảnh. Test cũ `repliesAndBlankNotesAreSkipped` đổi theo ngữ nghĩa mới (trả lời bài không có trong tập vẫn bị bỏ).
- **`SubscribeViewModelNostr*Test` và `LocalRssSvNostrSyncTest`:** vẫn xanh (không đổi chữ ký); thêm một ca đồng bộ: nguồn có một chuỗi tự trả lời thì cả chuỗi được ghi.
- **Đột biến JVM** (đổi tạm, chạy các lớp Nostr, khôi phục, `cmp`, so sánh tự động tập test đỏ): (x1) `toArticles` không lọc trả lời → test "bỏ trả lời người khác" đỏ; (x2) giữ mọi trả lời → như x1; (x3) bỏ kiểm "đích cũng được giữ" → test C/B/X đỏ; (x4) bỏ chặn vòng lặp → test vòng lặp treo hoặc đỏ (dùng giới hạn thời gian của test, không để treo bộ chạy); (x5) tính `mention` là trả lời → test mention đỏ; (x6) bỏ ưu tiên `reply` hơn `root` → test hỗn hợp đỏ; (x7) lấy thẻ không dấu **đầu** thay vì cuối → test kiểu cũ đỏ; (x8) bỏ kiểm id hex 64 → test id hỏng đỏ; (x9) phụ thuộc thứ tự đầu vào → test đảo thứ tự đỏ.
- JVM full suite đo lại (đỏ chập chờn: chạy riêng 3 lần + chạy lại cả bộ, ghi thẳng); `assembleDevDebug assembleDevDebugAndroidTest` thành công.
- Báo cáo `doc/task/done/FRONT-02x_nostr-own-threads_DONE.md`; cập nhật `doc/feature.md` (dòng FRONT-02b và dòng 02x mới) và task gốc FRONT-02 (còn ảnh Nostr, đăng nhập, đăng bài).

## Rủi ro đã biết
- **Giới hạn 20 bài của `NostrRelayClient.fetch` áp dụng TRƯỚC khi `toArticles` lọc:** `fetch` sắp mọi sự kiện (cả trả lời) rồi `take(20)`; nếu tác giả đăng nhiều trả lời người khác gần đây, các chuỗi tự trả lời có thể bị đẩy ra ngoài 20 trước khi đến `toArticles`. Cách xử lý đã cân nhắc: **không đổi** (`fetch` và test của nó giữ nguyên), nhận giới hạn này và nói thẳng trong báo cáo. Nếu muốn khắc phục, cần đổi `fetch` (tăng giới hạn giữ, ví dụ giữ mọi sự kiện đã tải rồi để `toArticles` cắt 20 sau khi lọc); đó là một thay đổi lớn hơn về hành vi và test của `NostrRelayClient`, **chưa nằm trong phạm vi**, owner cần hỏi lại nếu muốn.
- **Đổi hành vi nguồn đã đăng ký:** có thêm bài mới ở lần đồng bộ sau (đúng ý), nhưng người dùng có thể thấy nhiều bài hơn so với trước.
- **Mảnh đầu chuỗi nằm ngoài 50 khung gần nhất:** bài trả lời nó bị bỏ (an toàn, chỉ thiếu mảnh).
- **Không có dấu "bài 2/3":** mỗi mảnh hiện riêng; có thể thấy câu như "Tiếp theo…" mà không ngữ cảnh; đã chấp nhận khi chọn "mỗi bài một bài riêng".
- **NIP-10 kiểu cũ có chỗ mơ hồ:** chọn thẻ không dấu cuối cùng (không phải `mention`) làm đích; app khác có thể dùng cách khác; trường hợp lạ có thể bị bỏ (an toàn) hơn là hiện nhầm.

## Giới hạn đã biết
- Chỉ JVM; không kiểm trên máy hay mạng thật.
- Không chứng minh ranh giới 50/20 của relay thật; chỉ test với dữ liệu mẫu.

## Điều chỉnh khi thực thi
- **Không cần chữ ký thật:** `toArticles` không kiểm chữ ký, test dựng sự kiện bằng `NostrEvent.parse` với `sig` toàn số 0; ca đồng bộ cũng đi qua `NostrFetchResult` do MockK trả về.
- **Bộ nhớ đệm của bước đi chuỗi được đơn giản hóa:** mỗi bài chỉ trỏ tới **một** bài, nên một lần đi là một đường và kết luận không phụ thuộc điểm bắt đầu; ghi đệm mọi kết luận (đã đo 0 khác biệt trên mọi thứ tự của 6 đồ thị).
- **Hai test thêm** sau khi mutation cho thấy chỗ hở: `aReplyNamingATargetInCapitals_isDropped` (kiểm hex chữ thường) và `aReplyDatedBeforeItsTarget_isStillKept` (không phụ thuộc ngày).
- **x9 đổi** thành đột biến thật sự phụ thuộc thứ tự (đột biến gốc là tương đương); **dự đoán sai 5 chỗ** (x1, x2, x3, x8, x9) đã đọc lý do và sửa bảng.
- **Kết quả:** 26 test mới, mutation 11/11, JVM 3.848, 0 lỗi; đúng hai tệp production. Giới hạn 20 của `fetch` giữ nguyên theo lựa chọn của owner.
