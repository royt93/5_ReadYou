# FRONT-02x (Nguồn Nostr hiện chuỗi bài của chính tác giả)

**Date:** 2026-11-19
**Branch:** `dev` (không push)
**Scope:** mã production đổi **đúng hai tệp**: `NostrEvent.kt` và `NostrFeedMapper.kt`. Không đổi giao diện, chuỗi, Room (v22), DataStore, manifest, `app/build.gradle`, `NostrRelayClient`, `LocalRssSv`, `SubscribeViewModel`; không dependency mới; không thêm lệnh gọi mạng. **Chỉ JVM, không chạm thiết bị nào.**
**Spec / plan:** `docs/superpowers/specs/2026-11-18-front-02x-nostr-own-threads-design.md`, `docs/superpowers/plans/2026-11-18-front-02x-nostr-own-threads.md`.

## Việc đã làm
Trước đây mọi bài Nostr có thẻ `e` đều bị coi là "trả lời" và bị bỏ, nên chuỗi bài dài của chính tác giả (bài 2, 3… nối bài 1) mất mảnh. Bây giờ:
- `NostrEvent.isReply` đọc theo NIP-10: thẻ `e` chỉ có dấu `mention` **không** phải trả lời (bài đó là bài thường).
- `NostrEvent.repliedToId`: id bài được trả lời. Ưu tiên thẻ `reply` (nhiều thẻ thì lấy cuối), rồi `root`, rồi kiểu cũ không dấu (thẻ không dấu cuối cùng). Chỉ nhận id hex 64 ký tự **chữ thường**; sai thì không có đích (bài vẫn là trả lời, nên bị bỏ).
- `NostrFeedMapper.toArticles` giữ bài **không phải trả lời**, hoặc bài trả lời một bài trong cùng danh sách mà **chính bài đó cũng được giữ**. Trả lời bài người khác bị bỏ, và những bài chỉ nối vào đó cũng bị bỏ (đọc sẽ cụt ngữ cảnh). Vòng lặp trả lời bị bỏ cả vòng. Kết quả không phụ thuộc thứ tự đầu vào hay ngày.
- Mỗi mảnh trong chuỗi vẫn là **một bài riêng**, cùng cách lưu, cùng link njump, tiêu đề là 80 ký tự đầu của chính nó. Không tin thẻ `p`.

## Kiểm chứng (đo thật)
- **TDD:** Task 1 đỏ vì lỗi biên dịch (chưa có `repliedToId`); Task 2 đỏ đúng 6 test về chuỗi trước khi sửa mapper; Task 3 (ca đồng bộ) không có bước đỏ (đã ghi trước), bằng chứng là đột biến x1 làm nó đỏ.
- **Test mới: 26** (`NostrEventThreadTest` 11, `NostrFeedMapperThreadTest` 14, `LocalRssSvNostrSyncTest` +1). Hai test cũ khóa hành vi "bỏ trả lời" vẫn xanh vì fixture `REPLY` trỏ id không có trong tập.
- **Mô hình tham chiếu Python** của luật giữ bài (cả bản có bộ nhớ đệm) khớp bộ kiểm chứng điểm bất động trên **hơn 40.000 hoán vị** của 7 đồ thị.
- **Mutation JVM: 11/11 như dự kiến sau khi sửa bảng** (`x11` là đột biến tương đương, không test nào đỏ, đúng dự đoán): x1 giữ lại bỏ mọi trả lời; x2 giữ mọi trả lời; x3 bỏ điều kiện "đích cũng được giữ"; x4 bỏ chặn vòng lặp; x5 coi `mention` là trả lời; x6 `root` trước `reply`; x7 thẻ không dấu đầu; x8 bỏ kiểm hex; x9 phụ thuộc thứ tự; x10 thẻ `reply` đầu; x11 tương đương. Mã hai tệp khôi phục giống hệt (`cmp`), `git diff` main rỗng.
- **Dự đoán của plan sai 5 chỗ, đều đã đọc lý do thật rồi sửa (không có đột biến sống sót):** x1 làm đỏ thêm M7 và M8 (không có bài chuỗi thì các test đó cũng đỏ); x2 làm đỏ thêm ca đồng bộ L1; x3 làm đỏ thêm hai test vòng lặp (bỏ điều kiện đích được giữ làm vòng lặp trả lời nhau được giữ); x8 ban đầu **không** làm M12 đỏ (id `not-an-id` vốn không có trong tập nên không cần kiểm hex), nên tôi **thêm test mới** `aReplyNamingATargetInCapitals_isDropped`; và x9 gốc (ghi đệm kết luận giữa vòng lặp) hóa ra là **đột biến tương đương** (mỗi bài chỉ trỏ tới một bài nên một lần đi là một đường, không phụ thuộc điểm bắt đầu; đã đo 0 trên mọi thứ tự ở 6 đồ thị), nên tôi **đơn giản hóa mã** (ghi đệm mọi kết luận) và đổi x9 thành đột biến thật sự phụ thuộc thứ tự, cùng test mới `aReplyDatedBeforeItsTarget_isStillKept`.
- JVM `testDevDebugUnitTest --rerun-tasks`: **3.848 test, 0 lỗi, 0 bỏ qua** (3.822 + 26). `assembleDevDebug assembleDevDebugAndroidTest` thành công. Lint không đổi (không chuỗi, không tệp res).

## Giới hạn (nói thẳng)
- **Giới hạn 20 bài của `NostrRelayClient.fetch` áp dụng TRƯỚC bộ lọc** (đã giữ nguyên theo lựa chọn của bạn): `fetch` sắp mọi sự kiện, tính cả trả lời người khác, rồi cắt 20 bài mới nhất. Tác giả hay trả lời người khác có thể làm chuỗi tự trả lời của họ bị đẩy ra ngoài trước khi bộ lọc chạy. Muốn khắc phục phải đổi `fetch` và test của nó (giữ tối đa 50 sự kiện, để `toArticles` cắt 20 sau khi lọc): chưa làm.
- Mảnh đầu chuỗi nằm ngoài 50 khung gần nhất thì các mảnh sau bị bỏ (an toàn: chỉ thiếu mảnh).
- Không có dấu "bài 2/3"; mỗi mảnh hiện riêng, có thể đọc thấy "Tiếp theo…" thiếu ngữ cảnh (đã chấp nhận khi chọn "mỗi bài một bài riêng").
- Nguồn đã đăng ký sẽ thấy **thêm** bài chuỗi ở lần đồng bộ sau (bài đã lưu không đổi vì so theo link); trả lời bài người khác vẫn không hiện.
- NIP-10 kiểu cũ có chỗ mơ hồ: chọn thẻ không dấu cuối cùng làm đích; ca lạ có thể bị bỏ (an toàn) thay vì hiện nhầm.
- **Chưa kiểm** relay thật, mạng thật, máy thật hay màn hình; chỉ JVM.
- Task gốc FRONT-02 vẫn ở `doc/task/todo/`: còn ảnh Nostr, trả lời bài người khác, đăng nhập, đăng bài.

> **Cập nhật 2026-11-19 (FRONT-02ad):** luật "trả lời bài người khác bị bỏ" và bộ lọc chuỗi của báo cáo này đã **được thay**: nay mọi trả lời đều hiện, kèm dòng cuối `↩ link njump` (xem [`FRONT-02ad_nostr-show-all-replies_DONE.md`](FRONT-02ad_nostr-show-all-replies_DONE.md)). Phần còn lại của báo cáo giữ nguyên như lịch sử.
