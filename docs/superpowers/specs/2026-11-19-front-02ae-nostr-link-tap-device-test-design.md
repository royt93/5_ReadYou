# FRONT-02ae — Test máy thật: bấm link trong bài Nostr đến Reader (thiết kế, chờ owner duyệt spec)

**Ngày:** 2026-11-19 · **Nhánh:** `dev` · **Nền:** FRONT-02ac (link https thành thẻ `a`), FRONT-02ad (dòng `↩ link`), `Reader`/`ParagraphText` (chú thích `URL`, bấm gọi `onLinkClick`), mẫu `EInkReaderInteractionQaTest`

> Trạng thái: owner đã chốt (2026-11-19): kiểm trên **Tecno KJ7**, **chỉ đến Reader thật** (bấm link thì `onLinkClick` nhận đúng địa chỉ); **không** gọi `openURL`, không mở trình duyệt. Spec chờ owner duyệt trước khi lập plan. Chưa có plan, chưa có mã.

## Mục tiêu
02ac và 02ad mới chỉ có test JVM. Test này chứng minh trên máy thật chuỗi: **bài Nostr → `NostrFeedMapper.toArticles` → `rawDescription` → `Reader` (Jsoup + Compose thật) → chạm vào link → `onLinkClick` nhận đúng địa chỉ**. Chỉ thêm test `androidTest`; **không đổi mã production**, manifest, chuỗi, Room (v22), DataStore, `app/build.gradle`; không dependency mới; không mạng; không ghi Room/DataStore.

## Sự thật đã đọc (2026-11-19)
- `ParagraphText` dùng `ClickableText` khi đoạn có chú thích `URL`, và gọi `onLinkClick(chú thích tại vị trí chạm)`; `HtmlToComposable` lấy địa chỉ từ `element.attr("abs:href")` với `baseUrl` là `link` của bài.
- Mẫu có sẵn: `LazyColumn { Reader(link, content, onLinkClick) }` trong `createAndroidComposeRule<ComponentActivity>()` với `AppTheme`, không Hilt, không quảng cáo.
- Chạm giữa nút có thể rơi vào khoảng trống (chữ ngắn, căn trái): test **không đoán tọa độ**; lấy `TextLayoutResult` từ ngữ nghĩa của nút chữ, tính hộp của ký tự đầu link (`getBoundingBox`) và chạm vào chính hộp đó.

## Thiết kế
Một tệp mới `app/src/androidTest/java/com/mckimquyen/reader/ui/component/reader/NostrLinkTapDeviceTest.kt`. Bài dựng bằng `NostrEvent` hình thức (`sig` toàn 0) cho qua `toArticles`; `Reader(link = article.link, content = article.rawDescription, onLinkClick = opened::set)`.
Năm test: (1) link https trong chữ → chạm → nhận đúng link; (2) dòng `↩` của bài trả lời → chạm → nhận link njump của bài gốc (tính bằng `NostrKeys.noteId`); (3) link `http` là chữ thường → chạm vào chỗ đó → **không** gọi `onLinkClick`; (4) link có dấu chấm cuối → nhận link **không** có dấu chấm; (5) hai link → chạm link thứ hai → nhận link thứ hai.
Link trong test dài (không khoảng trắng) hoặc cố định bằng hằng có tên; chờ bằng `waitForIdle`/`runOnIdle`, không `sleep`.

## Phạm vi trung thực
- **Chứng minh:** chuỗi mapper → Jsoup → Reader thật → chạm → địa chỉ đúng, trên Tecno.
- **Không chứng minh:** `openURL` và trình duyệt thật (đã có từ trước), cài đặt trình duyệt, màn đọc thật (`ReadingPage`), cuộn, chọn chữ, E-Ink, relay thật.
- Chỉ **Tecno KJ7 `115333744A005844`** (Android 14). Một máy.

## Quy tắc an toàn thiết bị
Như FRONT-02ab: cổng `/tmp/kj7_gate.sh` in `GATE OK` **trong một lệnh riêng**; sao lưu/khôi phục byte-exact (`/tmp/kj7_backup.py`, `/tmp/kj7_restore.py`); băm DataStore `9ca1c202050c258b4aac9a063e27def350d737fb` phải trùng; `am instrument --user 0 -w -r -e class …`; không `connected*`; gỡ `com.mckimquyen.reader.test` sau vòng; không tọa độ đoán, không `setRotation`, không `sleep`.

## Kiểm chứng
- **20 lượt** trên Tecno; lượt đỏ phải đọc nguyên nhân (quảng cáo chỉ ghi số thật).
- **Đột biến trên máy** (đổi tạm mapper, build, cài, chạy, khôi phục, `cmp`): (a) link thường không bọc thẻ `a` → test 1, 4, 5 đỏ; (b) bỏ dòng `↩` → test 2 đỏ; (c) nhận `http` → test 3 đỏ; (d) dòng `↩` dùng id của chính bài → test 2 đỏ. Dự đoán suy luận từng test (test 2 không dùng nhánh bọc link thường nên xanh ở (a)); kiểm tự động.
- JVM full không đổi (số trước 3.886); `assembleDevDebug assembleDevDebugAndroidTest` thành công.

## Rủi ro đã biết
- Test hiệu lực "xanh sẵn" (hành vi đã có): bằng chứng có răng là bốn đột biến, nói thẳng trong báo cáo.
- `GetTextLayoutResult` có thể không có trên nút `ClickableText` ở bản Compose 1.7.8: plan đọc API thật (nếu thiếu, dùng nút `Text` bao ngoài hoặc dò bằng `performTextInput`-không; dừng và hỏi owner trước khi đổi thiết kế).
- Quảng cáo toàn màn hình: test chạy trên `ComponentActivity`, không có `MainActivity`.

## Giới hạn đã biết
- Không chứng minh mở trình duyệt; một máy.
