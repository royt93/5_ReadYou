# FRONT-02aa — Ảnh bìa cho bài Nostr trong danh sách (thiết kế, chờ owner duyệt spec)

**Ngày:** 2026-11-19 · **Nhánh:** `dev` · **Nền:** FRONT-02b/02x/02y/02z (mapper `NostrFeedMapper`), công tắc ảnh danh sách `FlowArticleListImagePref`

> Trạng thái: owner đã chốt trong brainstorm (2026-11-19): (1) **chỉ ảnh bìa trong danh sách**, theo công tắc ảnh có sẵn; (2) địa chỉ ảnh lấy **từ chữ bài, link https, đuôi ảnh**; (3) **không tải sẵn offline** cho Nostr, Triage và Reels giữ như các nguồn khác. Chưa có plan, chưa có mã.

## Mục tiêu
Bài Nostr có link ảnh trong chữ thì danh sách hiện ảnh bìa như các nguồn RSS. Chỉ **mapper** đổi: điền `Article.img`.

## Sự thật đã đọc (2026-11-19)
- `Article.img: String?` đã có. `ArticleItem.kt:227` chỉ vẽ ảnh khi `img != null` **và** `LocalFlowArticleListImage` bật (công tắc ảnh danh sách có sẵn).
- `NostrFeedMapper.toArticles` hiện không đặt `img`. Bluesky cũng không tải ảnh (`[image]`).
- `OfflinePrecacheService` đã `continue` với nguồn `NOSTR` và `BLUESKY` (dòng 39), nên **không** tải ảnh nền cho Nostr, không cần đổi.
- Triage (`TriageCard`) và Reels (`ReelsUiState`) dùng `article.img` khi người dùng đang xem, giống mọi nguồn RSS: ảnh Nostr cũng hiện ở đó, **không** theo công tắc ảnh danh sách (như RSS). Nói thẳng trong báo cáo.
- `RssHelper.isValidThumbnailUrl` chặn `data:` và địa chỉ không http(s); Nostr cần **chặt hơn**: chỉ `https`.
- Chữ bài là dữ liệu không tin cậy (đã được `escapeToHtml` khi lưu `rawDescription`); địa chỉ ảnh lấy từ chữ gốc `event.content`, không từ HTML đã thoát.

## Thiết kế
- Hàm thuần công khai `NostrFeedMapper.coverImageUrl(content: String): String?`: tìm link đầu tiên trong chữ bài dạng `https://…` kết thúc bằng `.jpg`, `.jpeg`, `.png`, `.gif`, `.webp` (không phân biệt hoa thường; cho phép `?query` sau đuôi), dừng ở khoảng trắng; trả `null` nếu không có. Không `http`, không `data:`, không link không có đuôi ảnh.
- `toArticles` đặt `img = coverImageUrl(event.content)`.
- Hằng có tên (R5): danh sách đuôi ảnh, tiền tố `https://`, độ dài tối đa của địa chỉ (từ chối link quá dài để không phình DB).
- Không đổi UI, chuỗi, Room (v22; `img` đã có cột), DataStore, manifest, `app/build.gradle`; không dependency; không đổi REQ/relay.

## Phạm vi trung thực
- **Chứng minh (JVM):** `coverImageUrl` đúng với link ảnh, bỏ `http`, bỏ link không phải ảnh, lấy link ảnh **đầu tiên**, chịu được `?query`, bỏ link quá dài; `toArticles` điền `img`.
- **Không chứng minh:** ảnh thật hiện trên máy (không thiết bị), máy chủ ảnh thật, công tắc ảnh bật/tắt trên màn hình (đã có từ trước).
- **Rủi ro riêng tư (nói thẳng):** mở danh sách khi công tắc ảnh bật làm app tải ảnh từ **máy chủ do tác giả bài chọn**, máy chủ đó thấy địa chỉ IP của người đọc. Công tắc ảnh tắt thì không tải gì. Triage và Reels hiện ảnh khi người dùng đang xem, không theo công tắc danh sách (như RSS).
- **Không làm:** ảnh trong trang đọc, thẻ `imeta` (NIP-92), video, `http`, tải sẵn offline, ảnh đại diện tác giả.

## Kiểm chứng
- TDD: test `NostrFeedMapperTest` (hoặc lớp mới `NostrCoverImageTest`) đỏ trước (lỗi biên dịch vì chưa có `coverImageUrl`), rồi xanh; Robolectric nếu cần.
- Mutation JVM: bỏ lọc `https` (nhận `http`), bỏ lọc đuôi (nhận mọi link), lấy link cuối thay vì đầu, bỏ giới hạn độ dài, `toArticles` không đặt `img`; mỗi đột biến làm đúng test dự định đỏ (bảng dự đoán, kiểm bằng suy luận từng test).
- JVM full `--rerun-tasks` đo lại số test (số trước 3.862, không dùng làm bằng chứng).
- Diff production: đúng **một** tệp `NostrFeedMapper.kt`.

## Rủi ro đã biết
- Link ảnh không có đuôi (CDN) bị bỏ sót; chấp nhận (owner chọn đuôi ảnh).
- Link ảnh có thể trỏ tới ảnh khổng lồ hoặc không tồn tại: tải ảnh do thư viện ảnh có sẵn (Coil) lo; không thêm gì.
- Đổi tiêu đề/ảnh bài đã lưu: bài đã lưu không đổi vì so theo link; ảnh chỉ có ở bài mới.

## Giới hạn đã biết
- Chỉ JVM; không kiểm máy thật hay mạng thật.
- Chỉ ảnh đầu tiên trong chữ bài.

## Điều chỉnh khi thực thi
- Làm đúng plan: một tệp production (`NostrFeedMapper.kt`), test trong lớp mới `NostrCoverImageTest` (12 test, dựng sự kiện hình thức vì sự kiện mẫu không có link ảnh).
- Link quá dài: bỏ link đó và lấy link hợp lệ kế tiếp; ranh giới đúng 2048 được giữ, 2049 bị bỏ.
- **Mutation:** 9/9 khớp dự đoán, không sửa bảng.
- **JVM đo lại:** 3.874 test, 0 lỗi (đúng kỳ vọng 3.862 + 12).
