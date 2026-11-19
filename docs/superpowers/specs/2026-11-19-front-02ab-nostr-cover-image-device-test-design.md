# FRONT-02ab — Test máy thật: ảnh bìa Nostr theo công tắc ảnh danh sách (thiết kế, chờ owner duyệt spec)

**Ngày:** 2026-11-19 · **Nhánh:** `dev` · **Nền:** FRONT-02aa (`NostrFeedMapper.coverImageUrl`, chỉ có test JVM), `ArticleItem` (vẽ ảnh khi `img != null` và `LocalFlowArticleListImage` bật)

> Trạng thái: owner đã chốt (2026-11-19): kiểm trên **Tecno KJ7**; ảnh từ **máy chủ giả trên máy**, bài Nostr đi qua **mapper thật**, vẽ bằng **`ArticleItem` thật**. Spec chờ owner duyệt trước khi lập plan. Chưa có plan, chưa có mã.

## Mục tiêu
FRONT-02aa mới chỉ chứng minh trên JVM rằng `Article.img` được điền. Test này chứng minh trên máy thật chuỗi: **bài Nostr có link ảnh → `toArticles` → `ArticleItem` → bộ tải ảnh Coil có hỏi đúng địa chỉ đó khi công tắc ảnh BẬT, và không hỏi gì khi TẮT**. Chỉ thêm test trong `androidTest`; **không đổi mã production**, manifest, chuỗi, Room (v22), DataStore, `app/build.gradle`; không dependency mới; **không ra mạng ngoài**.

## Sự thật đã đọc (2026-11-19)
- `ArticleItem.kt:227`: ảnh chỉ vẽ khi `article.img != null && LocalFlowArticleListImage.current.value`. Mặc định công tắc là `ON` (`FlowArticleListImagePref.default`).
- `BaseAsyncImage` dùng `rememberAsyncImagePainter` (Coil 2.1.0) với bộ tải mặc định của `Context.imageLoader` = bộ tải singleton của Coil; app cấp qua `RApp.newImageLoader()`. `contentDescription` để rỗng, nên **không có nút ngữ nghĩa** để khẳng định "ảnh đã hiện".
- Mapper chỉ nhận `https`; `MockWebServer` (`mockwebserver3`, có sẵn trong `androidTestImplementation`) chỉ nói `http` nếu không dựng chứng chỉ. Vì vậy **không** dùng nó cho ảnh.
- `ArticleItemTest` (androidTest) đã dựng `ArticleItem` bằng `createComposeRule()`, không cần Hilt, không chạm Room.

## Thiết kế
Một tệp mới `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/flow/NostrCoverImageDeviceTest.kt` (cùng gói với `ArticleItem` và `ArticleItemTest`).
1. **Bộ tải ảnh giả:** `ImageLoader.Builder(context).components { add(Interceptor) }.build()`; bộ chặn ghi lại `chain.request.data` rồi trả `SuccessResult` với một `ColorDrawable` (không có mạng, không đọc đĩa). Đặt bằng `Coil.setImageLoader(...)` trước `setContent`; `@After` gọi `Coil.reset()` để trả về bộ tải thật của app. Mỗi test có danh sách yêu cầu riêng.
2. **Bài:** dựng `NostrEvent` hình thức (`sig` toàn 0, như test mapper JVM), cho qua `NostrFeedMapper.toArticles`, bọc `ArticleWithFeed`, vẽ bằng `ArticleItem` trong `CompositionLocalProvider(LocalFlowArticleListImage provides ...)`.
3. **Chờ:** `compose.waitUntil` trên số yêu cầu đã ghi (không `Thread.sleep`, không tăng timeout). Ca "không tải": chờ cho Compose rảnh (`waitForIdle`) rồi khẳng định 0 yêu cầu; vì "không có gì xảy ra" không có tín hiệu dương, ca này **cặp** với một ca BẬT chạy trong cùng lớp để chứng minh bộ chặn đang hoạt động.
4. **Test (5):**
   - `switchOn_aNoteWithAnHttpsImageLink_asksForThatImage`: BẬT, link `https://img.example.test/a.jpg` → đúng 1 yêu cầu, đúng địa chỉ đó.
   - `switchOff_aNoteWithAnHttpsImageLink_asksForNothing`: TẮT → 0 yêu cầu.
   - `switchOn_aNoteWithoutALink_asksForNothing`: BẬT, chữ không có link → 0.
   - `switchOn_anHttpImageLink_asksForNothing`: BẬT, `http://…jpg` → 0 (không tải qua http).
   - `switchOn_onlyTheFirstOfTwoImageLinks_isAsked`: hai link → đúng 1 yêu cầu, link đầu.
5. Không MockK, không `connected*`, không tọa độ, không `setRotation`, không Hilt, **không ghi Room/DataStore** (Compose thuần).

## Phạm vi trung thực
- **Chứng minh:** trên Tecno, chuỗi `toArticles` → `ArticleItem` → Coil thật (chỉ thay bộ tải bằng bộ chặn) hỏi đúng địa chỉ khi công tắc BẬT và không hỏi khi TẮT/không link/`http`.
- **Không chứng minh:** ảnh **hiện** trên màn hình (không có nút ngữ nghĩa), tải qua mạng thật (OkHttp, chứng chỉ, SSRF, kích thước ảnh), bộ tải thật của app (đã bị thay trong test), `HomeEntry`/danh sách thật, Triage/Reels, relay thật, công tắc ảnh đổi từ màn Cài đặt.
- Chỉ **Tecno KJ7 `115333744A005844`** (Android 14). Một máy.

## Quy tắc an toàn thiết bị
Như FRONT-02s/02u trên Tecno: cổng `/tmp/kj7_gate.sh` in `GATE OK` **trong một lệnh riêng** trước mỗi lần khởi chạy; sao lưu và khôi phục **byte-exact** (`/tmp/kj7_backup.py`, `/tmp/kj7_restore.py`); băm DataStore `9ca1c202050c258b4aac9a063e27def350d737fb` phải trùng; `am instrument --user 0 -w -r -e class …`; không `connected*`; không xóa DataStore; gỡ `com.mckimquyen.reader.test` sau vòng. Khóa Tecno cho cả phiên (Pixel đang nối nhưng **không** dùng).

## Kiểm chứng
- **20 lượt** trên Tecno, mỗi vòng có cổng/sao lưu/khôi phục; không gõ lại, không tăng timeout, không `sleep`. Lượt đỏ phải đọc nguyên nhân; lượt đỏ vì quảng cáo toàn màn hình (`AdActivity`) chỉ ghi số thật (owner bảo bỏ qua quảng cáo), không điều tra.
- **Đột biến trên máy** (đổi tạm mã production, build, cài, chạy, khôi phục, `cmp`): (a) mapper không đặt `img` → hai test BẬT có link đỏ (`…asksForThatImage`, `…onlyTheFirst…`); (b) `ArticleItem` bỏ `&& articleListImage.value` → `switchOff…` đỏ; (c) mapper nhận `http` → `anHttpImageLink…` đỏ. Mỗi đột biến phải làm **đúng** test dự định đỏ (bảng dự đoán, suy luận từng test).
- JVM full không đổi (đo lại lúc thực thi, số trước 3.874); `assembleDevDebug assembleDevDebugAndroidTest` thành công.
- Báo cáo: số lượt, lượt đỏ và nguyên nhân, bảng đột biến, băm DataStore trước và sau, xoay sau mỗi lượt, những gì không kiểm.

## Rủi ro đã biết
- **Quảng cáo toàn màn hình** chèn vào Activity test (đo ở 02s: khoảng 7%): ghi số thật, không chặn kết luận.
- **`Coil.setImageLoader` là trạng thái toàn tiến trình:** `@After` phải `Coil.reset()`; test khác chạy sau trong cùng tiến trình sẽ dùng bộ tải thật.
- **Khẳng định "0 yêu cầu" yếu nếu bộ chặn hỏng:** đã cặp với ca BẬT trong cùng lớp; thêm đột biến (a) và (b) chứng minh bộ chặn bắt được.
- **Giao diện Coil có thể đổi chữ ký `Interceptor`/`SuccessResult` (2.1.0):** plan phải đọc API thật trước khi chép mã.

## Giới hạn đã biết
- Không chứng minh ảnh hiện thật hay mạng thật; chỉ "có hỏi đúng địa chỉ".
