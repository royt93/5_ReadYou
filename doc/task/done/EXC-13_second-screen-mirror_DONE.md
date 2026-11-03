# EXC-13 — Second Screen Mirror (một chiều, qua LAN) — DONE

**Hoàn tất:** 2026-11-03 · **Nhánh:** `dev` · **Trạng thái push:** chưa push

## Phạm vi — nói thẳng
Todo gốc mô tả QR, mirror vị trí cuộn, WebSocket và tái dùng hạ tầng NSD của EXC-05. Bản làm nhỏ hơn có chủ đích, theo quyết định của owner:
- **Một chiều, chỉ đọc.** Trình duyệt máy tính chỉ xem bài đang đọc và tự đổi khi người dùng chuyển bài; không điều khiển ngược.
- **URL kèm token + nút Sao chép/Chia sẻ, không QR.** Máy tính hiếm khi có camera để quét; bộ sinh QR phải tự viết hoặc thêm thư viện.
- **`ServerSocket` JDK tự viết, không thêm thư viện** (không NanoHTTPD, Ktor, ZXing). Cập nhật bằng **Server-Sent Events**, không WebSocket.
- **Văn bản thuần:** tiêu đề, nguồn, ngày, đoạn văn. Không HTML gốc, ảnh, script. Trang web chữ cố định tiếng Anh vì trình duyệt máy tính không theo ngôn ngữ của app.
- **Chưa làm:** QR, đồng bộ vị trí cuộn, TLS, NSD/mDNS, điều khiển ngược, ảnh/bảng/định dạng. Không đổi Room (vẫn v20), không thêm quyền, không AI/API key/quảng cáo/IAP.
- Mục "Second Screen" nằm trong menu ⋮ của trang đọc, mở một sheet nội tuyến (không `ModalBottomSheet`). Đóng sheet **không** tắt phiên: người dùng vẫn đọc tiếp và trình duyệt vẫn theo bài.
- Lệch spec có ghi: thêm trạng thái `Expired` vào `MirrorUiState` (để người dùng biết vì sao phiên dừng) và hai hằng `MAX_PATH_CHARS`, `MAX_TRACKED_IPS`.

## Cấu trúc mã
- `domain/mirror/` (Kotlin thuần, test trên JVM): `MirrorLimits`, `MirrorToken` (128 bit `SecureRandom`, so sánh hằng thời gian, hết hạn 30 phút), `MirrorRateLimiter` (20 yêu cầu/phút/IP, tối đa 3 luồng, bảng IP có trần), `MirrorRequestParser` (chỉ `GET`, giới hạn dòng, ký tự và kích thước), `MirrorRouter` (khớp chính xác 3 đường, mọi trường hợp lỗi cùng một `404 Not found`), `MirrorSnapshot`/`MirrorSnapshotBuilder` (cắt trần đoạn/ký tự/byte, bỏ ký tự điều khiển và surrogate lẻ, JSON tự escape `<`, `>`, `&`), `MirrorHtml` (trang tĩnh dựng bằng `textContent`), `LanAddress` (chỉ 10/8, 172.16/12, 192.168/16), `MirrorStartResult`.
- `infrastructure/mirror/`: `MirrorServer` + `MirrorHttp` (socket bind **đúng địa chỉ được cho**, cổng do OS cấp, mọi socket có timeout, đầu yêu cầu có trần, header bảo mật và CSP), `MirrorEngine`, `LanAddressProvider` (đọc `LinkProperties`, chỉ Wi-Fi hoặc Ethernet, không thêm quyền; callback mạng được gỡ khi đóng luồng).
- `ui/page/home/read/mirror/`: `SecondScreenViewModel` (một `@Inject` constructor, dispatcher tiêm vào), `MirrorUiState`, `SecondScreenSheetContent`.
- `ReadingViewModel` chỉ thêm cờ `showSecondScreenSheet` và hai hàm; `ReadingPage` nối sheet, `BackHandler`, đẩy bài và vòng đời; `TopBar` thêm mục menu. `MirrorModule` (Hilt): một server cho mỗi ViewModel.
- 15 chuỗi `second_screen_*` ở 38 locale (EN và VI viết tay, còn lại tiếng Anh); `StringResourceParityTest` 6/6.

## Giới hạn bảo mật — nói thẳng
1. **HTTP thường, không mã hoá.** Token 128 bit chỉ ngăn người *đoán* địa chỉ, không ngăn người *nghe lén*: ai ở cùng Wi-Fi nhìn thấy URL đều đọc được bài. Sheet luôn hiện cảnh báo này khi phiên bật. Không dùng ở Wi-Fi công cộng.
2. Mạng khách sạn/quán cà phê thường bật "client isolation" nên máy tính không thấy điện thoại; app không phát hiện chắc chắn được.
3. Server chỉ phục vụ đúng bài đang mở. Không bao giờ gửi cài đặt, DataStore, API key, danh sách feed hay bài khác.

## Kiểm chứng
- **Unit:** 2.052 test, 0 lỗi, 0 skip (nền trước task 1.953). Thêm 99 test: `MirrorTokenTest` 5, `MirrorRateLimiterTest` 7, `MirrorRequestParserTest` 8, `MirrorRouterTest` 5, `MirrorHtmlTest` 4, `MirrorSnapshotBuilderTest` 11, `MirrorSnapshotJsonTest` 3, `LanAddressTest` 5, `MirrorServerTest` 19 (socket thật trên loopback), `SecondScreenViewModelTest` 20, `SecondScreenSheetTest` 9, `ReadingViewModelSecondScreenTest` 3. Bộ mirror chạy 3 lần liên tiếp, 99/99 mỗi lần, không chập chờn.
- **Lint:** 0 lỗi, 2.463 cảnh báo (không đổi), không cảnh báo nào của EXC-13. **Build:** `assembleDevDebug` và `assembleDevDebugAndroidTest` pass; đồ thị Hilt hợp lệ.
- **Mutation-check:** mọi test hồi quy được kiểm bằng cách phá mã thật rồi xem test đỏ: so sánh token (chấp nhận tiền tố), regex router (cho dấu `/` cuối), `innerHTML`, 405 thành 400, escape `<`, vòng giảm theo byte, cắt giữa cặp surrogate, dải 172.16/12, bind `0.0.0.0`, không nhả slot luồng, không có timeout socket, `stop()` không đóng luồng, cho `start` khi đang chạy, `publish` khi tắt, hẹn giờ hết hạn không bị huỷ, cảnh báo chỉ hiện khi `Running`, bỏ chốt "có bài" trong `openSecondScreen`. **Một số đột biến ban đầu không bị test bắt**, nên tôi viết thêm test rồi chạy lại tới khi đỏ: so sánh token (ca chuỗi con của token chưa có), nhả slot luồng khi đóng, timeout socket, `stop()` đóng luồng đang mở, hẹn giờ hết hạn bị huỷ khi dừng hoặc khởi động lại, và cảnh báo phải hiện cả ở `Starting`/`NoNetwork` chứ không chỉ khi `Running`. Lần chạy đầu của `stop()` còn có một đột biến thứ hai (không đóng gì cả) mới làm test đỏ. Một lỗi thật tìm ra bằng test: trả `431` rồi đóng ngay làm TCP reset xoá mất câu trả lời, đã sửa bằng cách đọc bỏ phần còn lại có trần.
- **Chỉ kiểm bằng smoke, không có test JVM:** việc `showSecondScreenSheet` nằm trong `isAnySheetOpen` của `ReadingPage` (nhấn Back chỉ đóng sheet).
- **Instrumentation** `MirrorServerDeviceTest` 1/1 bằng `am instrument -e class ...` (không dùng `connectedDevDebugAndroidTest`) trên **Tecno KJ7 `115333744A005844`**.

## Smoke thật (Tecno KJ7 Android 14 `192.168.93.151` + Mac Chrome `192.168.93.9`, cùng Wi-Fi)
Đã thấy:
- Menu ⋮ có "Second Screen"; sheet mở, công tắc bật → URL `http://192.168.93.151:<cổng>/m/<32 hex>`, cảnh báo không mã hoá, `Viewers: 0`, hạn dùng đúng 30 phút, nút Sao chép/Chia sẻ.
- Từ Mac bằng `curl`: trang 200, `/state` 200 có đúng tiêu đề/nguồn/1 đoạn; `/` 404; token sai 404 với **cùng thân `Not found`** như khi thiếu token; dấu `/` cuối, `?x=1`, `/../state` đều 404; `POST` và `HEAD` 405; header `Cache-Control: no-store`, `X-Content-Type-Options`, `Referrer-Policy`, CSP có mặt; trang có `textContent`, không có `innerHTML`.
- **Chrome trên Mac mở URL và hiển thị đúng bài** (tiêu đề, "BBC News · 10/21/2026", đoạn văn). App đổi thành `Viewers: 1`, rồi `2` khi thêm luồng.
- **Chuyển bài:** bấm Next Article trên điện thoại, luồng sự kiện trên Mac nhận bài mới (`v2`) sau khoảng **0,9 giây** (đồng hồ hai máy khác nhau nên chỉ là số tương đối).
- **Tối đa 3 luồng:** mở 4 luồng song song, ba luồng 200 và luồng thứ tư 503; app hiện `Viewers: 3`.
- **Vòng đời:** nhấn Back khi sheet mở chỉ đóng sheet, trang đọc còn và server còn trả 200; đóng bài thì cổng đóng; đưa app xuống nền (HOME) thì cổng đóng; token của phiên cũ không dùng lại được, mỗi lần bật ra URL mới.
- Quảng cáo toàn màn hình xuất hiện lúc mở app (một lần); nút có nhãn "Continue to app" từ dump mới, đã bấm.

**Chưa thấy / không kiểm được:**
- Chỉ một điện thoại và một trình duyệt: chưa thử Safari/Firefox, nhiều máy khác nhau, hay client isolation của router.
- Hết hạn thật sau 30 phút (chỉ có test đơn vị với thời gian ảo) và mất mạng thật (chỉ test đơn vị) chưa thử trên máy.
- Trang web trong trình duyệt chưa thử với bài rất dài hoặc nhiều đoạn (400 đoạn, 512 KiB) trên mạng thật; TalkBack chưa thử.
- Bài chỉ có 1 đoạn nên chưa thấy cuộn của trang web.
- Nút Sao chép/Chia sẻ có mặt trên sheet nhưng tôi chưa bấm; `Toast` và chooser chưa quan sát.
- Hộp tìm kiếm của danh sách BBC bị mở một lần do một lần chạm lệch trong lúc smoke; đã đóng, không ảnh hưởng dữ liệu.

## Dữ liệu máy
Trước smoke tôi sao lưu Room (3 file) và `settings.preferences_pb` (194 byte) của Tecno, `integrity_check` ok, 152 bài/1 feed/9 đã đọc. Sau smoke khôi phục cả bốn file và `cmp` **giống hệt**, `integrity_check` ok, không còn gói `com.mckimquyen.reader.test`, không còn cổng nào của app mở. Không bao giờ xoá DataStore.
Ghi chú thiết bị: phiên bắt đầu với Samsung A50s (`R58MA6WYRPE`) nhưng nó biến mất khỏi `adb devices` giữa chừng; theo yêu cầu "tiếp tục với tecno đi" của owner, phần instrumentation và smoke chạy trên Tecno. Không máy nào khác bị chạm.
