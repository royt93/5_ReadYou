# FRONT-02 — Theo dõi tài khoản Mastodon và Bluesky bằng RSS công khai (thiết kế, bản thu hẹp)

**Ngày:** 2026-11-14 · **Nhánh:** `dev` · **Task gốc:** `doc/task/todo/FRONT-02_decentralized-web3-social.md` · **Nền:** ô Thêm nguồn (`SubscribeViewModel.search`), `NewsletterUrlResolver`, `RssHelper` (Rome + OkHttp, đã có `followRedirects(true)` và DoH qua `AppDns`)

## Mục tiêu
Người dùng dán một tài khoản Mastodon hoặc Bluesky vào ô **Thêm nguồn** sẵn có; app tự đổi thành địa chỉ RSS công khai của tài khoản đó rồi xem trước và đăng ký như mọi nguồn tin khác. Bài hiện theo thứ tự thời gian, không thuật toán, không đăng nhập.

## Quyết định của owner (2026-11-14)
- **Phạm vi:** Mastodon và Bluesky dùng RSS có sẵn. **Nostr để sau.**
- **Cách nhập:** dán vào ô Thêm nguồn sẵn có, app tự nhận ra (không thêm màn mới).
- **Hiển thị bài:** bài không có tiêu đề thì lấy vài dòng đầu của nội dung làm tiêu đề, nội dung đầy đủ giữ nguyên khi mở.
- **Sửa chung:** việc lấy dòng đầu làm tiêu đề áp cho **mọi** nguồn RSS thiếu tiêu đề (không riêng mạng xã hội).
- Thiết bị kiểm: **chỉ Tecno KJ7 `115333744A005844`**; không bấm theo tọa độ (memory `never-tap-by-stale-coordinates`).

## Sự thật đã đo (không suy đoán)
- RSS công khai có thật, thử bằng `curl` ngày 2026-11-14: `https://mastodon.social/@Mastodon.rss` trả `200 application/rss+xml` (20 bài); `https://bsky.app/profile/bsky.app/rss` trả **302 chuyển hướng** tới `/profile/did:plc:…/rss` rồi `200` (7 bài, chỉ chữ, không ảnh). `.atom` của Mastodon không dùng (một lần thử trả rỗng).
- **Cả hai nguồn trả bài KHÔNG có `<title>`.** Đo bằng chính thư viện Rome 2.1.0 mà app dùng: `title=NULL` cho mọi bài của cả hai.
- **Hậu quả trong app hiện tại:** `RssHelper.article` gọi `syndEntry.title.toString()` (dòng ~170). Với tiêu đề null, Kotlin gọi `toString()` trên giá trị null của Java nên ném `NullPointerException` (đo thực tế: bài thiếu tiêu đề biến mất khỏi `searchFeed`). Thử bằng một bài kiểm tra tạm với `searchFeed`: nguồn có 2 bài, bài thiếu tiêu đề bị bỏ, chỉ bài có tiêu đề còn lại (`entries=1`). Ở đường **đồng bộ**, `queryRssXml` ánh xạ từng bài **không có lưới an toàn từng bài** và bọc cả danh sách trong một `try`, nên theo cách đọc mã (chưa đo) một bài hỏng làm cả lần đồng bộ của nguồn trả về rỗng.
- Ô Thêm nguồn chạy `formatUrl()` trên chữ vừa gõ trước mọi tra cứu: `@bsky.app` sẽ thành `https://@bsky.app` và hỏng, nên bộ nhận dạng phải chạy **trước** bước này.

## Phạm vi trung thực
- **Chỉ đọc, công khai.** Không đăng nhập, không lưu mật khẩu hay khóa, không xem tài khoản riêng tư, không đăng bài, không theo dõi bằng giao thức liên hợp.
- **Nostr chưa làm** (không có RSS, cần WebSocket tới relay và giải mã `npub`); một trong ba mạng mà task gốc nêu. Task gốc ở lại `todo/`.
- **Gõ trần `bsky.app` (không có `@`) không được nhận là Bluesky**: `example.com` trần không phân biệt được trang web với tài khoản, nên bắt buộc có `@` hoặc link `…/profile/…`. (Đính chính so với lời mô tả ở phần 1 của buổi brainstorm.)
- Bluesky RSS chỉ có chữ, không ảnh, khoảng 7 bài gần nhất; Mastodon khoảng 20 bài.
- Chỉ **tài khoản**; hashtag (`/tags/…`), danh sách, `with_replies`, `media` **không** làm.
- Không chuỗi mới (nên không phải sửa 38 ngôn ngữ); ô nhập không có dòng gợi ý các dạng này vì cần chuỗi mới.
- Không dependency mới, không Room v23, không quảng cáo/mua bán/tiền, không Gemini.

## Thành phần
1. **`SocialFeedUrlResolver`** (`infrastructure/social/`, hàm thuần, không Android, không mạng): `fun resolve(input: String): String?`. Trả địa chỉ RSS `https` hoặc `null` (không phải mạng xã hội, giữ luồng cũ). Quy tắc, kiểm tra theo thứ tự:
   - Cắt khoảng trắng đầu cuối. Rỗng ⇒ `null`.
   - **Mastodon dạng `@tên@máy_chủ`** (đúng hai dấu `@`, có `@` đầu): `https://<máy_chủ>/@<tên>.rss`.
   - **Mastodon dạng link** `https://<máy_chủ>/@<tên>` (đúng một đoạn đường dẫn bắt đầu bằng `@`, không có đoạn sau, bỏ qua `/` cuối và `?`/`#`): `https://<máy_chủ>/@<tên>.rss`. **Danh sách loại trừ máy chủ không phải Mastodon** (cũng dùng `/@tên`): `youtube.com`, `youtu.be`, `medium.com`, `substack.com`, `tiktok.com`, `instagram.com`, `x.com`, `twitter.com`, `threads.net` (cả tên miền con) ⇒ `null`.
   - **Bluesky dạng `@tên.miền`** (đúng một `@` đầu, phần sau là tên miền có ít nhất một dấu chấm, **không** chứa `@` thứ hai): `https://bsky.app/profile/<tên.miền>/rss`.
   - **Bluesky dạng link** `https://bsky.app/profile/<handle hoặc did>` (đúng hai đoạn `profile`, `<id>`): `https://bsky.app/profile/<id>/rss`. `https://bsky.app/profile/<id>/rss` đã đúng dạng thì giữ nguyên.
   - Tên chỉ nhận `[A-Za-z0-9_.-]`; máy chủ và handle Bluesky nhận chữ, số, `.` và `-`; DID nhận thêm `:`. Mọi dạng khác (ký tự lạ, `http://`, có cổng/`user:pass@`, đoạn đường dẫn thừa, máy chủ không có dấu chấm) ⇒ `null`. Kết quả luôn là `https`, tên miền chuyển chữ thường.
2. **Cắm vào `SubscribeViewModel.search()`**: ngay đầu hàm, **trước** `formatUrl()`: nếu `SocialFeedUrlResolver.resolve(linkContent)` ra địa chỉ thì thay `linkContent` bằng địa chỉ đó (giữ cho "đã đăng ký" và xem trước dùng đúng địa chỉ RSS). Nếu `searchFeed` địa chỉ này thất bại thì **rơi về luồng cũ** (`newsletterUrlResolver`, quét web) trên **chữ gốc người dùng gõ**, không làm tệ hơn hiện nay.
3. **Sửa lỗi mất bài thiếu tiêu đề** trong `RssHelper.article`: tiêu đề trống/`null` ⇒ lấy văn bản thuần của nội dung (bỏ thẻ HTML, gộp khoảng trắng) và cắt tối đa `FALLBACK_TITLE_MAX_LENGTH = 80` **theo ký tự Unicode** (không cắt giữa emoji), thêm `…` nếu bị cắt. Nếu cả tiêu đề lẫn nội dung đều trống ⇒ ném lỗi như cũ (chỉ bài đó bị bỏ). Hàm thuần mới `RssDescriptionParser.titleFromBody(html, max)`.
4. **Lưới an toàn từng bài ở đường đồng bộ**: `queryRssXml` chuyển `map` thành `mapNotNull` có `runCatching` từng bài, như `searchFeed` đang làm; chỉ ghi log **tên lớp** lỗi, không ghi nội dung hay URL. Một bài hỏng không còn làm rỗng cả nguồn.
5. **Không đổi** `Feed`, `Article`, Room, DataStore, manifest, chuỗi tài nguyên.

## Kiểm chứng
- JVM, TDD đỏ→xanh (đọc lý do đỏ từ XML **sau khi** `.exit` tồn tại), mutation có backup và `cmp`:
  - `SocialFeedUrlResolver`: từng dạng đúng (hai kiểu Mastodon, hai kiểu Bluesky, DID, đã có `/rss`), chữ hoa/thường, khoảng trắng, `/` cuối, `?`/`#`; **từng dạng sai** phải trả `null` (không có `@`, ba dấu `@`, `@abc` không dấu chấm, `http://`, có cổng, `user:pass@`, đoạn thừa như `/@user/123`, ký tự lạ, chuỗi rỗng); **mọi máy chủ trong danh sách loại trừ** (`youtube.com/@x`, `medium.com/@x`, `substack.com/@x`, tên miền con); email `a@b.com` (không `@` đầu) ⇒ `null`.
  - Tiêu đề thay thế: bài thiếu tiêu đề lấy đúng dòng đầu; HTML và khoảng trắng; đúng 80 ký tự không thêm `…`, 81 ký tự thì cắt và thêm `…`; **emoji ở biên 80** không bị vỡ; tiêu đề có sẵn **không** bị thay; bài trống cả hai bị bỏ; `searchFeed` và đồng bộ đều giữ được bài thiếu tiêu đề.
  - Đồng bộ: một bài hỏng giữa nguồn chỉ làm mất bài đó (test với `MockWebServer`, nhiều bài); log không chứa URL hay nội dung.
  - Cắm vào ô Thêm nguồn (test nguồn và test view model): bộ nhận dạng chạy **trước** `formatUrl`; rơi về luồng cũ khi địa chỉ đoán không đọc được; "đã đăng ký" dùng địa chỉ RSS.
  - Cả suite `--rerun-tasks`, lint, `assembleDevDebug`; lược đồ không đổi.
- androidTest trên Tecno bằng `adb -s 115333744A005844 shell am instrument` (không `connected*`): **gọi mạng thật** tới Mastodon và Bluesky qua `RssHelper.searchFeed` với địa chỉ do bộ nhận dạng sinh ra; in số bài và tiêu đề; kiểm tiêu đề không rỗng. Bài kiểm tra mạng thật là helper tạm **không đưa vào repo** (mạng đổi theo ngày).
- Máy thật chỉ **Tecno KJ7**: cổng thiết bị chỉ đọc, sao lưu byte-exact Room/`-shm`/`-wal`/DataStore/`shared_prefs`, khôi phục và kiểm băm sau cùng. Không chạm màn hình (chỉ `am instrument`, đọc DB).
- **Không kiểm được:** gõ chữ thật vào ô Thêm nguồn trên màn hình (chỉ kiểm bằng test trên máy tính, vì tôi không bấm theo tọa độ); ảnh trong bài Bluesky (RSS không có); máy chủ Mastodon khác `mastodon.social`; tài khoản bị khóa hoặc đã xóa; Nostr.

## Giới hạn đã biết
- Phụ thuộc vào việc Mastodon và Bluesky giữ RSS công khai; nếu họ tắt, nguồn sẽ báo không đọc được.
- Một máy chủ Mastodon tắt RSS hoặc chặn bot sẽ không thêm được.
- Bluesky chỉ vài bài gần nhất và chỉ chữ; không có luồng, trả lời hay ảnh.
- Tiêu đề thay thế chỉ là cắt dòng đầu, có thể cắt giữa câu.
- Các nguồn đang bị mất bài vì thiếu tiêu đề sẽ bỗng có bài (thường là điều tốt).

## Điều chỉnh khi thực thi
- Nhánh quay về trong `search()` còn **cập nhật ô nhập** về `formatUrl(chữ gốc)` (không chỉ đổi biến cục bộ), để luồng cũ thấy đúng địa chỉ như trước.
- `titleFromBody`: hai chỗ chặn đầu vào thừa (đột biến tương đương) đã gỡ; hành vi giữ nguyên.
- Test log của đường đồng bộ thu hẹp vào nội dung bài và dòng "Skip malformed RSS entry errorType=": log sẵn có đã in đường dẫn đã che nên test theo đường dẫn tài khoản không đứng vững.
- Probe mạng thật dùng `OkHttpClient` thường (`followRedirects(true)`), không qua Hilt, nên không có DoH `AppDns`.
- Full suite lần đầu có 1 lỗi timeout ở `OracleEvidenceViewModelTest` (tệp không đổi); chạy riêng và full lần hai xanh (3.440/0). Nguyên nhân chưa xác định.

