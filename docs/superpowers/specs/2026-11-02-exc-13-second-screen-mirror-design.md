# EXC-13 — Second Screen Mirror (một chiều, qua LAN): Design

**Ngày:** 2026-11-02 · **Nhánh:** `dev` · **Trạng thái:** chờ owner duyệt spec

## Mục tiêu
Cho người dùng đang đọc bài trên điện thoại xem **cùng bài đó trên trình duyệt máy tính** trong cùng mạng Wi-Fi, không cần tài khoản, không cần cài thêm app, không qua server trung gian. Máy tính chỉ **xem**; không điều khiển ngược.

## Khác todo gốc (nói thẳng)
Todo mô tả QR, mirror theo vị trí cuộn, WebSocket, và gợi ý tái dùng hạ tầng NSD của EXC-05. Bản này nhỏ hơn có chủ đích, theo quyết định của owner (AskUserQuestion):
- **Một chiều, chỉ đọc** (không điều khiển ngược). Phạm vi: bài đang đọc + cập nhật khi chuyển bài.
- **Không QR** ở lần đầu: máy tính hiếm khi có camera để quét, và bộ sinh QR phải tự viết (Reed-Solomon dễ sai) hoặc thêm thư viện. Thay bằng **URL kèm token + nút Sao chép/Chia sẻ**.
- **Server là `ServerSocket` của JDK tự viết, không thêm thư viện** (không NanoHTTPD, Ktor, ZXing). Cập nhật bằng **Server-Sent Events** (một chiều, ít mã hơn WebSocket).
- **Văn bản thuần**: tiêu đề, nguồn, ngày, đoạn văn. Không HTML gốc, không ảnh, không script.
- **Bảo mật:** token dài ngẫu nhiên, 1 phiên, hết hạn, giới hạn client và tốc độ, tự tắt. Không TLS (nói rõ ở "Giới hạn").
- Không NSD/mDNS, không `infrastructure/p2p/` (EXC-05 chưa có), không đổi Room (vẫn v20), không AI/API key/quảng cáo/IAP.
- **Chưa làm: mirror vị trí cuộn.** Trang web có thanh cuộn riêng của trình duyệt. Lý do: vị trí cuộn của điện thoại không ánh xạ được sang bố cục trình duyệt có độ rộng khác nhau; đoạn văn hiện tại có thể bổ sung sau.

## Sự thật từ code (đã đọc)
- App chỉ có quyền `INTERNET` (`AndroidManifest.xml`), `usesCleartextTraffic="true"` là cấu hình cho **client** của app; không đổi nó. Server của ta nhận kết nối vào, không liên quan cấu hình đó.
- Không có mã `ServerSocket`/`HttpServer`/mirror nào trong `app/src/main`. Chỉ có `WebFeedScraper`, `LocalRssSv`, `FeedErrorClassifier` dùng `Socket` làm client.
- Không có thư viện HTTP server hay QR trong classpath; có okhttp 5.0.0-alpha.11 (client), okio 3.2.0.
- `ReadingPage` đã có `isAnySheetOpen`, `BackHandler`, `readingUiState.articleWithFeed`; vị trí cuộn dùng `ReadingPositionMapper` (ZEN-08).
- Android 11 trên A50s: `mResumedActivity` (không có `topResumedActivity`).

## Thiết kế
### Thành phần (`infrastructure/mirror/` mới; logic thuần ở `domain/mirror/`)
- `MirrorSnapshot(title, source, dateMillis, paragraphs, version)`: giá trị bất biến đã làm sạch; `MirrorSnapshotBuilder` (thuần) chuyển nội dung bài thành đoạn văn thuần, **cắt trần** (`MAX_PARAGRAPHS`, `MAX_PARAGRAPH_CHARS`, `MAX_SNAPSHOT_BYTES`) để một bài khổng lồ không làm đầy bộ nhớ.
- `MirrorToken` (thuần): sinh 128 bit từ `SecureRandom`, mã hoá hex; so sánh **hằng thời gian**; hết hạn sau `TOKEN_TTL_MILLIS = 30 phút` (đồng hồ tiêm vào để test).
- `MirrorRequestParser` (thuần): đọc **chỉ dòng yêu cầu và header cần**, giới hạn `MAX_REQUEST_BYTES`, `MAX_HEADER_LINES`; chỉ chấp nhận `GET`; từ chối còn lại với 405/400/414.
- `MirrorRateLimiter` (thuần): tối đa `MAX_REQUESTS_PER_MINUTE = 20` mỗi IP, tối đa `MAX_CLIENTS = 3` luồng SSE cùng lúc.
- `MirrorRouter` (thuần): `GET /m/<token>` → trang HTML tĩnh; `GET /m/<token>/state` → JSON bài hiện tại; `GET /m/<token>/events` → SSE (`event: article` khi đổi bài). Mọi đường khác hoặc token sai/hết hạn → **404 giống nhau** (không phân biệt "token sai" và "không tồn tại") để không lộ thông tin.
- `MirrorHtml` (thuần, hằng chuỗi): trang tĩnh tự đủ, dựng DOM bằng `textContent` (không `innerHTML`), CSS nội tuyến, tự tối/sáng theo `prefers-color-scheme`; nội dung bài chỉ vào qua JSON.
- `MirrorServer` (hạ tầng): `ServerSocket` bind **đúng địa chỉ IPv4 Wi-Fi/LAN** (không `0.0.0.0`), cổng ngẫu nhiên do hệ điều hành cấp (`port 0`), thread pool giới hạn trên `Dispatchers.IO`, mọi socket có `soTimeout`; `start()` trả `MirrorEndpoint(url)` hoặc lỗi có kiểu; `stop()` đóng mọi socket và pool, idempotent.
- `LanAddress` (hạ tầng): chọn địa chỉ IPv4 riêng (10.x, 172.16–31.x, 192.168.x) của giao diện đang lên; không có → trạng thái "không có Wi-Fi", **không mở server**.
- `MirrorSv`/`SecondScreenViewModel` (một `@Inject` constructor, dispatcher tiêm vào): giữ `MirrorUiState` (`Off | NoNetwork | Starting | Running(url, clients, expiresAt) | Error`), nhận `MirrorSnapshot` mới và đẩy tới server.
- UI: mục "Second Screen" trong `ReadingPage` mở một sheet nội tuyến (không `ModalBottomSheet`, Material3 1.0.1) có: công tắc bật/tắt, URL, nút **Sao chép** và **Chia sẻ**, số trình duyệt đang xem, giờ hết hạn, cảnh báo "không mã hoá, chỉ dùng Wi-Fi tin cậy". Mọi chuỗi mới ở 38 locale (EN và VI viết tay, còn lại tiếng Anh).

### Giới hạn (hằng có tên, giá trị khởi điểm; plan có thể chỉnh nhưng phải giữ tên)
| Hằng | Giá trị | Ý nghĩa |
|---|---|---|
| `TOKEN_BYTES` | 16 | 128 bit từ `SecureRandom` |
| `TOKEN_TTL_MILLIS` | 30 phút | phiên hết hạn |
| `MAX_CLIENTS` | 3 | luồng SSE đồng thời |
| `MAX_REQUESTS_PER_MINUTE` | 20 | mỗi IP, cửa sổ trượt 60 giây |
| `MAX_REQUEST_BYTES` | 8 KiB | tổng dòng yêu cầu + header |
| `MAX_HEADER_LINES` | 32 | quá thì 400 |
| `SOCKET_TIMEOUT_MILLIS` | 10 giây | đọc yêu cầu; luồng SSE dùng nhịp `: keepalive` mỗi 15 giây |
| `MAX_PARAGRAPHS` | 400 | đoạn văn mỗi bài |
| `MAX_PARAGRAPH_CHARS` | 4.000 | ký tự mỗi đoạn |
| `MAX_SNAPSHOT_BYTES` | 512 KiB | JSON gửi đi; vượt thì cắt bớt đoạn cuối và báo "đã rút gọn" |
| `WORKER_THREADS` | 4 | pool kết nối (3 SSE + 1 yêu cầu thường) |

### Vòng đời và tắt server (tiêu chí an toàn)
Server tắt khi: người dùng tắt công tắc; rời `ReadingPage` (`ViewModel.onCleared` và `DisposableEffect`); app xuống nền (`ProcessLifecycleOwner` ON_STOP); token hết hạn; mất Wi-Fi (callback mạng). Hết hạn thì trình duyệt thấy một dòng "phiên đã kết thúc". Không dùng foreground service, không chạy nền.

### Nội dung được và không được gửi
Chỉ: tiêu đề, tên nguồn, ngày, đoạn văn thuần của **đúng bài đang mở**. Không bao giờ: cài đặt, DataStore, API key, danh sách feed, danh sách bài khác, ghi chú, hay bất kỳ đường dẫn nào ngoài ba đường trên. Không thư mục tĩnh, không `..` (router so khớp chính xác).

### Lỗi
Cổng/địa chỉ không mở được, không có mạng, không bind được → `Error`/`NoNetwork` với thông báo, **không crash**; yêu cầu hỏng/quá lớn/chậm → đóng kết nối, không ảnh hưởng client khác.

## Giới hạn nói thẳng
1. **HTTP thường, không TLS.** Ai ở cùng Wi-Fi có thể nghe lén nếu quan sát được URL; token chỉ ngăn người *đoán* địa chỉ, không ngăn người *nghe*. Không dùng ở Wi-Fi công cộng. Cảnh báo này hiện trên màn hình.
2. Chỉ một chiều, chỉ văn bản thuần: bài nhiều ảnh/bảng sẽ nghèo; không đồng bộ vị trí cuộn.
3. Mạng khách sạn/quán cà phê thường bật "client isolation" nên máy tính không thấy điện thoại; không thể phát hiện chắc chắn từ phía app.
4. Chỉ kiểm bằng **một điện thoại (A50s)**: trình duyệt trên Mac truy cập qua Wi-Fi thật là cách kiểm duy nhất, và nó **không thay thế** kiểm bằng nhiều thiết bị thật (độ trễ, nhiều client khác nhau). Đo độ trễ chỉ tương đối.
5. Android có thể giết tiến trình khi ở nền; server tắt khi xuống nền theo thiết kế nên không bị ảnh hưởng.
6. Todo gốc yêu cầu QR và cập nhật vị trí cuộn; **chưa làm** và ghi vào DONE doc.

## Kiểm thử
- Thuần: `MirrorTokenTest` (độ dài, ngẫu nhiên, hằng thời gian, hết hạn biên), `MirrorRequestParserTest` (GET hợp lệ; method khác; dòng quá dài; header thừa; thiếu CRLF; byte lạ), `MirrorRateLimiterTest` (cửa sổ trượt, mỗi IP, tối đa client), `MirrorRouterTest` (token đúng/sai/hết hạn cho cùng một phản hồi 404; không có đường ngoài ba đường; `..`/mã hoá phần trăm), `MirrorSnapshotBuilderTest` (cắt trần, ký tự điều khiển, Unicode), `MirrorHtmlTest` (không chứa `innerHTML`, không script ngoài, không tham chiếu tài nguyên ngoài).
- Hạ tầng (Robolectric/JVM, `ServerSocket` thật trên loopback): khởi/tắt, tắt idempotent, đóng mọi kết nối khi `stop`, client chậm không chặn client khác, quá 3 SSE bị từ chối, quá giới hạn tốc độ bị 429, bind lỗi báo `Error`.
- ViewModel: trạng thái `Off/NoNetwork/Starting/Running/Error`, tắt khi `onCleared`, tắt khi hết hạn, đẩy bài mới tới server.
- Compose/Robolectric: sheet hiện URL, nút Sao chép/Chia sẻ, cảnh báo không mã hoá, công tắc tắt thì server dừng.
- Instrumentation trên A50s `R58MA6WYRPE`: khởi server thật, `HttpURLConnection` tới URL từ cùng tiến trình trả đúng nội dung.
- Smoke: từ **Mac** gọi `curl` và trình duyệt tới URL trên Wi-Fi thật; token sai trả 404; sau khi rời bài, kết nối bị từ chối. Quảng cáo toàn màn hình đóng bằng nút có nhãn từ dump mới, kiểm `mResumedActivity` trước mỗi lần chạm; sao lưu Room (3 file) và DataStore trước khi ghi dữ liệu thật, khôi phục và `cmp`, không bao giờ xoá DataStore.

## Ngoài phạm vi
Điều khiển ngược, QR, mDNS/NSD, TLS, WebSocket, mirror ảnh chụp màn hình, `MediaProjection`, đồng bộ vị trí cuộn, nhiều bài cùng lúc, xác thực người dùng, foreground service, thư viện mới, đổi Room, AI, API key, quảng cáo, IAP.
