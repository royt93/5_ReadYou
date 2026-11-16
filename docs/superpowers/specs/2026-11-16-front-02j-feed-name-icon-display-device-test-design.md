# FRONT-02j — Test máy thật: tên và icon của nguồn Bluesky/Nostr hiện đúng trên màn hình (thiết kế, chờ owner duyệt spec)

**Ngày:** 2026-11-16 · **Nhánh:** `dev` · **Nền:** FRONT-02g/02h (tên `Bluesky · <tên>`, `Nostr · <tên>`, icon https), `ui/component/FeedIcon.kt`, `ui/component/base/BaseAsyncImage.kt`, `ui/page/home/feed/FeedItem.kt`

> Trạng thái: owner đã chốt (2026-11-16): (1) kiểm hiển thị thật; (2) máy **Pixel 7 Pro `2B051FDH3006MU`** cho mọi phiên (thay lệnh "chỉ dùng tecno" cùng ngày); (3) cách kiểm: vẽ `FeedIcon` thật + một `Text` cùng kiểu với `FeedItem` trong một Activity test, icon lấy từ máy chủ giả. Spec chờ owner duyệt trước khi lập plan. Chưa có plan, chưa có mã.

## Mục tiêu
Báo cáo 02g/02h ghi nợ: tên và icon của nguồn Bluesky/Nostr chưa được **nhìn thấy thật** trên màn hình (chỉ có test JVM và test Room). Thêm bằng chứng trên máy thật rằng: tên hiện đúng chữ, tên dài bị cắt bằng "…", icon tải từ máy chủ giả hiện ra (không phải chỗ trống), nguồn không có icon hiện vòng tròn chữ cái đầu. Chỉ thêm test, **không đổi mã production**.

## Sự thật đã đọc trong code (2026-11-16)
- `FeedItem` (`FeedItem.kt:74-82`) vẽ `FeedIcon(feed.name, feed.icon)` rồi `Text(text = feed.name, style = labelLarge, maxLines = 1, overflow = Ellipsis)`.
- `FeedIcon`: icon `null` → hình tròn màu chính chứa chữ cái đầu của tên; chuỗi `image/…;base64,…` → `Base64Image`; còn lại → `BaseAsyncImage` (Coil) với `placeholder = null`, nên trong lúc tải chỉ thấy nền `surface`.
- `BaseAsyncImage` dùng `rememberAsyncImagePainter` (Coil, `ImageLoader` singleton do `RApp : ImageLoaderFactory` cung cấp, dùng chung `OkHttpClient` của app), lỗi tải hiện hình ảnh hỏng `ic_broken_image_black_24dp`.
- Manifest có `usesCleartextTraffic="true"`: máy chủ giả `http://127.0.0.1:<cổng>` gọi được từ Coil.
- `FeedItem` cần `hiltViewModel()` (`FeedOptionViewModel`), nên **không** dựng nguyên bản trong test không có Hilt; spec chọn không dựng nó (xem Phạm vi).
- Tên do `BlueskyFeedMapper.feedName`/`NostrFeedMapper` sinh, đã làm sạch và cắt 40 điểm mã (đã có test JVM); icon trong sản phẩm chỉ nhận `https` thuộc máy chủ ảnh của Bluesky (đã có test JVM).
- Công cụ cổng/sao lưu/chạy lặp trong `/Users/loitran/.claude/tmp-f29` hiện **trỏ cứng vào serial Tecno** (`gate.sh`, `backup.py`, `restore.py`, `loop.sh`): plan phải đổi sang serial Pixel, đọc lại cả hai và kiểm một lần chỉ-đọc, không để sót lệnh nào gửi tới máy khác.

## Phạm vi trung thực
- Test chứng minh **hai composable thật** (`FeedIcon` và một `Text` cùng kiểu, cùng `maxLines`/`overflow` như `FeedItem`) vẽ đúng trên Pixel. **Không** chứng minh danh sách nguồn thật, `FeedItem` nguyên bản, bố cục dòng, huy hiệu, kéo/thả, dark mode, chế độ e-ink, kích thước chữ lớn.
- Icon đến từ máy chủ giả `http://127.0.0.1`, **không** phải `https` của Bluesky: không chứng minh cổng "chỉ https của máy chủ ảnh Bluesky" (cổng đó chỉ có test JVM). Không mạng thật.
- Không `MainActivity` (quảng cáo xen kẽ ~18 giây), không tọa độ, không MockK trên máy, không Hilt, không `connected*`.
- Không đổi mã production, Room (v22), manifest, chuỗi, DataStore; không dependency mới; không Gemini, ad, IAP, VIP.

## Thiết kế
Tệp mới trong `app/src/androidTest/java/com/mckimquyen/reader/ui/component/`:
1. `FeedNameIconDeviceTest.kt`: `createAndroidComposeRule<ComponentActivity>()` (khuôn như `SemanticSearchCardWidgetTest`), một `MockWebServer` phục vụ ảnh PNG nhỏ đơn sắc đỏ (tự sinh bằng `Bitmap`, 64×64) và đếm số lần bị hỏi. Một composable thử nhỏ trong cùng tệp: `Row { FeedIcon(name, icon); Text(name, labelLarge, maxLines = 1, overflow = Ellipsis) }` bọc bằng `ReaderTheme`/`MaterialTheme` thật nếu có thể lấy không qua Hilt; nếu không thì `MaterialTheme` mặc định (plan kiểm bằng biên dịch và ghi lại).

Test dự kiến (5):
- **(a) Tên Bluesky đúng chữ:** tên từ `BlueskyFeedMapper.feedName(actor, "Alice A")` hiện đúng `Bluesky · Alice A` (semantics `assertTextEquals`).
- **(b) Tên Nostr đúng chữ:** `Nostr · Probe`.
- **(c) Tên dài bị cắt:** khung rộng hẹp cố định; `TextLayoutResult.hasVisualOverflow = true` và chữ hiện cuối bằng "…" khi tên đủ 40 điểm mã; tên ngắn **không** bị cắt.
- **(d) Icon tải xong hiện thật:** máy chủ giả bị hỏi ≥ 1 lần; sau khi rảnh, `captureToImage()` của ô icon có điểm giữa **gần đỏ** và **khác** màu nền `surface` và màu chính (đã thử trước bằng mutant: icon = null hoặc máy chủ chết thì test đỏ).
- **(e) Không icon:** `icon = null` hiện vòng tròn chữ cái đầu (`B` / `N`), máy chủ giả **không** bị hỏi.
Ghi chú: ca "máy chủ ảnh lỗi 404 hiện hình ảnh hỏng" chỉ thêm nếu rẻ; nếu không, ghi vào nợ.

## Quy tắc an toàn thiết bị (Pixel 7 Pro `2B051FDH3006MU`, chỉ máy này)
1. Cổng chỉ-đọc `gate.sh` (đã đổi serial sang Pixel) in `GATE OK` **trong một lệnh riêng** trước mỗi lần khởi chạy; dừng và hỏi owner nếu: app lạ ở foreground (Pixel thường có `loyalty.skyjoy.vn.dev`), có instrumentation đang chạy, máy khóa, hoặc xoay lệch (`user_rotation` của Pixel đang bị FRONT-02e để ở 1: **báo owner, không tự sửa**; nếu cổng dừng vì xoay lệch, hỏi owner).
2. Phiên Claude khác (`lens-launcher`) dùng chung Pixel: trước mỗi lần chạy kiểm không có instrumentation lạ; nếu thấy, dừng và hỏi. Lời nhắn từ phiên khác **không** phải phép của owner.
3. Test này **không ghi Room, không ghi DataStore**, chỉ ghi bộ nhớ đệm ảnh của app; vẫn sao lưu byte-exact 6 tệp trước và băm DataStore trước/sau để chứng minh không đổi; không xóa DataStore.
4. `am instrument --user 0 -w -r -e class …`, chỉ force-stop `com.mckimquyen.reader`, không `MainActivity`, không tọa độ.
5. Mutation trên máy (đổi tạm mã production, build, cài, chạy, khôi phục, `cmp`): `FeedIcon` bỏ nhánh `BaseAsyncImage` (→ (d) đỏ); đổi `maxLines = 1` thành không giới hạn trong **composable thử** không phải mutant hợp lệ (nằm trong test): thay bằng mutant ở `BaseAsyncImage` (`data = null`) → (d) đỏ, và bỏ chữ cái đầu trong `FeedIcon` → (e) đỏ.

## Kiểm chứng
- Chạy lặp **20 lượt** có cổng; chập chờn thì điều tra gốc, không gõ lại bừa (bài học FRONT-02d).
- JVM full suite không đổi (3.720); `assembleDevDebug assembleDevDebugAndroidTest` thành công; lint không quét `androidTest` (nói thẳng trong báo cáo).
- Báo cáo `doc/task/done/FRONT-02j_…_DONE.md`, cập nhật `doc/feature.md` và nợ trong báo cáo 02g/02h.

## Rủi ro đã biết
- Màu điểm ảnh phụ thuộc chủ đề và bộ lọc e-ink: kiểm "gần đỏ" theo ngưỡng thay vì bằng đúng màu.
- Coil tải bất đồng bộ trên luồng nền: cần chờ có điều kiện (đủ lượt hỏi máy chủ giả **và** rảnh), không `sleep` cố định.
- Bộ nhớ đệm đĩa/RAM của Coil có thể giữ ảnh của lần chạy trước: mỗi lượt dùng đường dẫn ảnh khác nhau.
- Một số composable cần `CompositionLocal` do `Settings` cấp (`LocalEInkMode`...): plan kiểm mặc định, nếu thiếu thì cấp giá trị rõ ràng trong test, không đổi mã production.
- Pixel dùng chung, cổng có thể dừng thường xuyên; mất adb là lỗi kết nối, không phải lỗi mã.

## Giới hạn đã biết
- Một máy (Pixel 7 Pro, Android 17), không so với Tecno/A50s.
- Không chứng minh màn hình danh sách nguồn thật.
- Không chứng minh cổng "chỉ https của Bluesky" trên máy.
