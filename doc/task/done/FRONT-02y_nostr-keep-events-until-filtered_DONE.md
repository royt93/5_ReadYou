# FRONT-02y (Nostr: cắt 20 bài sau khi lọc, không phải trước)

**Date:** 2026-11-19
**Branch:** `dev` (không push)
**Scope:** mã production đổi **đúng ba tệp**: `NostrRelayClient.kt` (bỏ cắt 20 và hằng cũ), `NostrFeedMapper.kt` (hằng mới `MAX_ARTICLES_SHOWN = 20`), `NostrFetchResult.kt` (chỉ KDoc). Không đổi giao diện, chuỗi, Room (v22), DataStore, manifest, `app/build.gradle`, `LocalRssSv`, `SubscribeViewModel`, `NostrEvent`; không dependency mới. **Không đổi mạng**: REQ vẫn `limit=50`, vẫn đọc tối đa 50 khung, 8 giây, 64 KiB mỗi khung. **Chỉ JVM, không chạm thiết bị nào.**
**Spec / plan:** `docs/superpowers/specs/2026-11-19-front-02y-nostr-keep-events-until-filtered-design.md`, `docs/superpowers/plans/2026-11-19-front-02y-nostr-keep-events-until-filtered.md`.

## Việc đã làm
Điểm yếu còn lại của FRONT-02x: `NostrRelayClient.fetch` sắp mọi sự kiện đã tải (cả trả lời người khác) rồi cắt còn 20 **trước** khi bộ lọc chạy, nên tác giả hay trả lời người khác làm chuỗi tự trả lời của họ bị đẩy ra ngoài. Nay:
- `fetch` trả **mọi** sự kiện hợp lệ đã đọc (tối đa số khung đọc, 50), mới nhất trước.
- `NostrFeedMapper.toArticles` vẫn cắt 20 bài hiện ra, **sau** khi lọc. Hằng đổi tên `MAX_NOTES_KEPT` thành `NostrFeedMapper.MAX_ARTICLES_SHOWN` (vẫn 20), vì nay nó chỉ nghĩa "số bài hiện ra".
- Giữ nguyên: `fetch` dừng ở relay đầu có bài (không thử relay hai); REQ gửi relay; chuỗi dài hơn 50 sự kiện gần nhất vẫn cụt.

## Kiểm chứng (đo thật)
- **TDD:** Task 1: ba test relay mới đỏ đúng (`fetch` còn cắt 20), rồi bỏ `.take(...)` thì xanh. Task 2: test dùng hằng mới đỏ vì lỗi biên dịch, rồi thêm hằng thì xanh.
- **Chỉ có 38 sự kiện kind 1 ký thật** của tác giả mẫu (8 lẻ + 30 trong `MANY`), nên **không làm được** test "55 sự kiện hợp lệ" như spec ghi. Giới hạn đọc 50 được chứng minh **bằng số khung** (khung rác cũng tính): 12 khung rác + 38 hợp lệ = đúng 50 khung nên đọc hết 38 (`fetch_readsFiftyFramesAndKeepsEveryValidOne`); 13 khung rác = 51 khung nên khung hợp lệ cuối **không** được đọc (`fetch_doesNotReadTheFiftyFirstFrame`). Hai test sẵn có về 50 khung vẫn xanh.
- **Test mới/đổi:** `fetch_keepsEveryValidEventItRead_newestFirst` (thay `fetch_keepsTheTwentyNewest_newestFirst`), hai test khung nói trên, `theArticleLimit_isTwentyArticles`, `aThreadBehindManyAnswersToOthers_isStillShown`; hai test mapper cũ dùng hằng mới. **+4 test** so với trước.
- **Mutation JVM: 7/7 như dự kiến sau khi sửa bảng** (y1 cắt lại 20; y2 cắt 30; y3 bỏ sắp xếp; y4 mapper bỏ cắt; y5 hằng 21; y6 mapper cắt 10; y7 mapper cắt 20 **trước** khi lọc, chính là lỗi gốc). Mã hai tệp khôi phục giống hệt (`cmp`), `git diff` main rỗng. **Một dự đoán sai, đã đọc lý do:** y5 (hằng 21) làm đỏ thêm `atMostTwentyArticles_areKept_newestFirst` (với 30 sự kiện mẫu, 21 bài khác 20, nên test đó cũng bắt được; plan tưởng chỉ `theArticleLimit_isTwentyArticles` bắt). Không có đột biến sống sót.
- `aThreadBehindManyAnswersToOthers_isStillShown` xanh **cả trước lẫn sau** sửa (mapper đã lọc rồi mới cắt từ FRONT-02x); bằng chứng nó bắt lỗi là đột biến y7.
- JVM `testDevDebugUnitTest --rerun-tasks`: **3.852 test, 0 lỗi, 0 bỏ qua** (3.848 + 4). `assembleDevDebug assembleDevDebugAndroidTest` thành công. Lint không đổi (không chuỗi, không tệp res).

## Giới hạn (nói thẳng)
- **Chuỗi dài hơn 50 sự kiện gần nhất vẫn cụt** (REQ vẫn `limit=50`, theo lựa chọn của bạn); tăng `limit` sẽ đổi lưu lượng mạng và test REQ chính xác, chưa làm.
- **Relay đầu chỉ có trả lời người khác thì không thử relay hai** (bạn chọn giữ): `fetch` thấy có bài là dừng, trường hợp hiếm khiến xem trước báo lỗi dù relay hai có bài hiện được.
- Nguồn đã đăng ký có thể thấy **thêm bài chuỗi cũ hơn** ở lần đồng bộ sau (bài đã lưu không đổi vì so theo link).
- Bộ nhớ: tối đa 50 khung, mỗi khung tối đa 64 KiB; `found` đã giữ chừng đó trước đây nên không đổi đỉnh bộ nhớ.
- **Chưa kiểm** relay thật hay máy thật; chỉ JVM với relay giả. Ca đồng bộ MockK **không thêm** (nó không đi qua `readRelay` nên trùng test mapper).
- Task gốc FRONT-02 vẫn ở `doc/task/todo/`: còn ảnh Nostr, trả lời bài người khác, đăng nhập, đăng bài.
