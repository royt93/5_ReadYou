# FRONT-02ad — Nostr hiện mọi trả lời, kèm link tới bài gốc (thiết kế, chờ owner duyệt spec)

**Ngày:** 2026-11-19 · **Nhánh:** `dev` · **Nền:** FRONT-02x (bộ lọc chuỗi, sẽ bị xóa), 02y (cắt 20 sau lọc), 02z (`visibleNotes`), 02ac (link bấm được)

> Trạng thái: owner đã chốt trong brainstorm (2026-11-19): (1) **hiện mọi trả lời**, cả trả lời chính mình lẫn người khác; (2) mỗi trả lời có **một dòng cuối "↩ link njump của bài gốc"**, không chữ mới (không chuỗi dịch), không tải thêm gì; (3) trả lời **tranh chỗ ngang nhau** với bài thường trong 20 bài (mới nhất trước); (4) **xóa bộ lọc chuỗi** (`keepOriginalsAndOwnThreads`) và test của nó, thay bằng test cho luật mới. Spec chờ owner duyệt trước khi lập plan. Chưa có plan, chưa có mã.

## Mục tiêu
Từ FRONT-02x, mọi trả lời bài người khác bị bỏ vì đọc riêng không có ngữ cảnh. Owner nay chọn hiện hết, nhưng cho người đọc cách xem bài gốc: link bấm được (nhờ FRONT-02ac) tới trang njump của bài được trả lời. Không đổi REQ, không tải bài của người lạ, không kiểm chữ ký thêm.

## Sự thật đã đọc (2026-11-19)
- `NostrFeedMapper.visibleNotes` = `keepOriginalsAndOwnThreads(events)` + `noteId` hợp lệ + tiêu đề không rỗng; `toArticles` sắp mới nhất trước, cắt `MAX_ARTICLES_SHOWN = 20`. `NostrRelayClient.fetch` dùng `visibleNotes` để quyết định relay nào "có bài hiện được" (02z).
- `NostrEvent.isReply` (có thẻ `e` không phải `mention`) chỉ được `keepOriginalsAndOwnThreads` dùng; `repliedToId` (reply > root > thẻ cuối không đánh dấu; chỉ id 64 chữ hex thường) được mapper dùng. Test của chúng: `NostrEventThreadTest` (11 test), `NostrEventTest.isReply_isTrueOnlyWithAnETag`, `NostrFeedMapperThreadTest` (18 test, phần lớn kiểm bộ lọc).
- `NostrKeys.noteId(hex)` trả `note1…` (bech32) hoặc null nếu id hỏng; `NJUMP_BASE = "https://njump.me/"`.
- Sự kiện ký thật chỉ có 38 cái; `REPLY` là trả lời bài `aaaa…` không có trong tập, **hiện sẽ hiện được** nên các test relay 02z dùng `REPLY` làm "chỉ có trả lời người lạ" phải viết lại.
- Chữ bài gốc không đổi: tiêu đề tính từ `escapeToHtml(content)` (không có dòng link), nên tiêu đề và `shortDescription` không đổi.

## Thiết kế
- `visibleNotes(events)` = **bài có `noteId` hợp lệ và tiêu đề không rỗng** (không lọc trả lời). `keepOriginalsAndOwnThreads` bị **xóa**.
- Hàm thuần công khai `NostrFeedMapper.replyTargetUrl(event): String?` = `NJUMP_BASE + noteId(event.repliedToId)` khi `repliedToId` hợp lệ, ngược lại `null` (trả lời mà không biết bài gốc thì không có dòng link, bài vẫn hiện).
- `toArticles`: `rawDescription` = `escapeToHtml(content)` + (nếu có `replyTargetUrl`) `<br>↩ <a href="URL">URL</a>` với URL đã thoát. Dòng `↩` là **ký hiệu**, không chuỗi dịch. `title`, `shortDescription`, `link` giữ như cũ (tính từ chữ bài).
- `NostrEvent.isReply` bị **xóa** nếu `grep` ở plan xác nhận không còn nơi dùng ngoài test của nó (xóa hơn thêm); `repliedToId` giữ. Test `isReply_*` và các test chuỗi của `NostrEventThreadTest` chỉ còn những cái kiểm `repliedToId` (đổi tên cho đúng).
- Hằng có tên (R5): `REPLY_MARK = "↩"`. Cập nhật KDoc `MAX_ARTICLES_SHOWN`, `visibleNotes`, `NostrFetchResult`.
- Không đổi UI, chuỗi, Room (v22), DataStore, manifest, `app/build.gradle`; không dependency; không đổi REQ/relay, `fetch`, `LocalRssSv`, `SubscribeViewModel`.

## Phạm vi trung thực
- **Chứng minh (JVM):** trả lời (kể cả của chính mình) hiện thành bài với dòng `↩ link njump`; link đúng id bài gốc (reply > root > cũ); trả lời không biết bài gốc vẫn hiện, không có dòng link; bài thường không có dòng link; bài mention-only không có dòng link; bài trống vẫn bị bỏ; `fetch` vẫn dừng ở relay đầu có bài hiện được (nay trả lời cũng là bài hiện được), relay đầu chỉ có bài trống vẫn thử relay hai.
- **Không chứng minh:** bấm link trên máy thật; relay thật; bài gốc có thật hay đã bị xóa trên njump.
- **Thay đổi hành vi (nói thẳng):** (a) **nguồn Nostr đã đăng ký sẽ nhận thêm các bài trả lời chưa đọc ở lần đồng bộ kế tiếp** (tối đa 20 mới nhất, bài đã lưu không đổi vì so theo link); (b) tác giả hay trả lời người khác thì 20 bài có thể toàn trả lời, bài thường bị đẩy ra (đúng điều owner chọn); (c) mất phòng thủ vòng lặp trả lời của 02x vì không còn đi theo chuỗi; (d) chuỗi tự trả lời của chính tác giả không còn "được giữ cả chuỗi nhờ luật riêng" nhưng vẫn hiện vì mọi trả lời đều hiện.
- **Riêng tư:** link bài gốc do **người lạ** (tác giả bài được trả lời) không được tải; chỉ là link người đọc có thể bấm. App không tải gì khi mở bài. Link njump là địa chỉ cố định `https://njump.me/note1…` do app tự dựng từ id đã kiểm dạng hex, **không** lấy từ chữ bài.
- **Không làm:** tải nội dung bài gốc, kiểm chữ ký bài của người lạ, hiện ngữ cảnh trích dẫn, đăng nhập, đăng bài, công tắc ẩn trả lời.

## Kiểm chứng
- TDD: test mới đỏ trước, đúng lý do: mapper chưa có `replyTargetUrl` (lỗi biên dịch) và test "trả lời người lạ vẫn hiện" đỏ vì `visibleNotes` còn lọc; rồi xóa bộ lọc và test cũ, xanh. Mapper test chạy Robolectric.
- Mutation JVM với bảng dự đoán: trả lời bị lọc lại (khôi phục lọc), bỏ dòng link, link dùng id sai (id của chính trả lời), link không thoát, dòng link thêm cả cho bài thường, bài mention-only có dòng link, trả lời không biết bài gốc bị bỏ, mất `<br>`, bỏ `REPLY_MARK`; mỗi đột biến làm **đúng** test dự định đỏ (suy luận từng test trước khi chốt).
- Viết lại các test relay 02z (A1, A3, A5, A6, A7): dùng `EMPTY` (bài trống) thay `REPLY` làm "relay đầu không có bài hiện được"; A4 (đã dùng `EMPTY`) trở thành trùng, gộp.
- JVM full `--rerun-tasks` đo lại số test (số trước 3.885, **sẽ giảm** vì xóa test chuỗi; ghi số đo được và danh sách test xóa). Diff production: `NostrFeedMapper.kt`, `NostrEvent.kt` (xóa `isReply`), KDoc `NostrFetchResult.kt`.
- Không đụng thiết bị (chỉ JVM).

## Rủi ro đã biết
- **Xóa nhiều test (ước ~25):** có chủ ý theo lựa chọn của owner; báo cáo liệt kê từng test xóa và lý do.
- **Nguồn đã đăng ký sẽ có thêm bài trả lời chưa đọc:** có thể gây bất ngờ; nói thẳng trong báo cáo.
- **Bài gốc ngoài 50 sự kiện không còn là vấn đề** (không cần tìm bài gốc), nhưng link bài gốc có thể trỏ tới bài người lạ đã xóa.
- **Xung đột cách hiểu với FRONT-02x/02y/02z:** báo cáo và `doc/feature.md` ghi rõ 02x (bộ lọc chuỗi) bị thay bằng 02ad; ghi đè ghi chú "trả lời người khác vẫn bị bỏ".

## Giới hạn đã biết
- Chỉ JVM; không kiểm máy thật hay relay thật.
- Trả lời không biết bài gốc (thẻ `e` hỏng) vẫn hiện, không có link.
