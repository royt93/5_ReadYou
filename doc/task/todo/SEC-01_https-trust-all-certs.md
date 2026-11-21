# [SEC-01] HTTPS không kiểm chứng chứng chỉ (trust-all là mặc định)

- **Type:** Security
- **Priority:** `P1 (High)`
- **Estimation:** `5 Story Points`
- **Epic:** 01. FIX — Sửa Lỗi, Ổn Định & Ad Unit (nhóm bảo mật, cùng họ FIX-09)
- **Status:** ⏸️ **Deferred** (owner chọn 2026-11-21: "defer, làm sau", sau phase migrate ad SDK)
- **Location:** [`OkHttpClientModule.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/di/OkHttpClientModule.kt) (`cachingHttpClient`, `trustAllCerts`, `provideOkHttpClient`), [`ProviderAPI.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/rss/provider/ProviderAPI.kt), [`GoogleReaderApi.kt`](../../../app/src/main/java/com/mckimquyen/reader/infrastructure/rss/provider/googleReader/GoogleReaderApi.kt)

## Vấn đề thực tế (đã đọc mã, 2026-11-21)
- `cachingHttpClient(..., trustAllCerts: Boolean = true)` đặt mặc định `true` từ commit `71dde14b` (2024-04-24). Khi `true`, `trustAllCerts()` cài một `X509TrustManager` rỗng (`checkServerTrusted` không làm gì, `getAcceptedIssuers` trả mảng rỗng) và `hostnameVerifier { _, _ -> true }`.
- `provideOkHttpClient` (singleton Hilt) chỉ truyền `cacheDirectory` và `cacheSize` nên **không tắt** nó. Grep không thấy chỗ nào đặt `trustAllCerts = false`. `ProviderAPI` và `GoogleReaderApi` gọi `cachingHttpClient()` trực tiếp, cũng mặc định `true`.
- Hệ quả: mọi lời gọi HTTPS dùng client này chấp nhận chứng chỉ giả và tên máy chủ sai. Gồm Gemini (khóa nằm trong query `?key=`), WebDAV (Basic auth), Bluesky, Nostr `wss`, kiểm tra cập nhật GitHub, FreshRSS/Miniflux/Nextcloud, tải bài. Người đứng giữa (ví dụ Wi-Fi công cộng) đọc và sửa được nội dung, kể cả khóa API và mật khẩu WebDAV.
- Có thể có chủ ý: nguồn RSS tự lưu trữ thường dùng chứng chỉ tự ký. Chưa có ghi chép quyết định nào trong `doc/` hay `docs/`.
- Google Play có cảnh báo cho `TrustManager` và `HostnameVerifier` không an toàn. **Chưa kiểm** với Play Console xem bản update có bị cảnh báo hay chặn không.
- Kết hợp với `WebDavBackupManager.restore()` (không giới hạn dung lượng, không xác nhận trước khi ghi), kẻ đứng giữa đưa được OPML tùy ý vào Room.

## User Story
> Là người dùng đọc tin và nhập khóa API riêng,
> Tôi muốn ứng dụng chỉ nói chuyện với đúng máy chủ mà tôi tin,
> Để khóa Gemini và mật khẩu WebDAV của tôi không bị người đứng giữa đọc được.

## Phương án (chưa chọn, cần owner duyệt thiết kế)
1. **Bật kiểm chứng mặc định, tự ký là tùy chọn riêng** (đề xuất): `trustAllCerts` mặc định `false`; công tắc "tin chứng chỉ tự ký" theo từng nguồn RSS, WebDAV và Google Reader. Ưu: đóng lỗ hổng đúng gốc, tránh cảnh báo Play. Nhược: nguồn tự ký đang dùng sẽ lỗi tới khi bật công tắc; thêm giao diện và chuỗi cho 37 ngôn ngữ.
2. **Chỉ bật kiểm chứng cho dịch vụ công cộng** (Gemini, Bluesky, Nostr, GitHub), giữ trust-all cho RSS, WebDAV, Google Reader. Ưu: ít vỡ nhất, chặn lộ khóa Gemini. Nhược: WebDAV (mật khẩu) và RSS vẫn bị đứng giữa; mã trust-all vẫn nằm trong APK nên Play vẫn có thể cảnh báo.
3. **Giữ nguyên, ghi rủi ro** vào Data safety. Nhược: lỗ hổng còn nguyên, rủi ro bị Play cảnh báo hoặc chặn.

## Acceptance Criteria (Gherkin)
- **Given** nguồn HTTPS có chứng chỉ hợp lệ **When** đồng bộ **Then** thành công như trước.
- **Given** máy chủ trình chứng chỉ tự ký và công tắc tin tự ký **tắt** **When** kết nối **Then** thất bại với lỗi chứng chỉ, không gửi tiêu đề `Authorization` hay nội dung.
- **Given** cùng máy chủ và công tắc **bật** **When** kết nối **Then** thành công, và chỉ cho đúng nguồn đó.
- **Given** tên máy chủ không khớp chứng chỉ **When** kết nối **Then** thất bại (không còn `hostnameVerifier { true }`).
- **And** Gemini và các dịch vụ công cộng không bao giờ dùng trust-all.
- **And** không còn `X509TrustManager` rỗng trong APK release (kiểm bằng dex).

## Ghi chú cho người thực thi
- Cần test JVM với `MockWebServer` có chứng chỉ tự ký (`HandshakeCertificates`) cho cả hai nhánh; đột biến đổi mặc định về `true` phải bị bắt.
- Chạy bài kiểm bản release R8 của REL-02 sau khi đổi (client dựng khác đi).
- Không làm trước khi owner xong phase migrate ad SDK, và không đụng mã ad.

## 🔁 Loop Prompt (dùng cho `/loop` hoặc agent thực thi task này)

```
Bạn đang thực hiện task [SEC-01] "HTTPS không kiểm chứng chứng chỉ" trong repo RSS Cat Hub (com.mckimquyen.reader, xem CLAUDE.md). Đọc kỹ "Vấn đề thực tế" và "Acceptance Criteria", và hỏi owner chọn một trong ba phương án trước khi sửa.

Mỗi vòng lặp:
1. Đọc OkHttpClientModule.kt, ProviderAPI.kt, GoogleReaderApi.kt, xác nhận vấn đề còn tồn tại (không giả định).
2. TDD: viết test đỏ trước với MockWebServer + chứng chỉ tự ký, rồi sửa. Chỉ nhánh dev, không nhánh phụ.
3. Build: ./gradlew assembleDevDebug, test JVM với --rerun-tasks (kiểm tuổi XML).
4. Lặp tới khi Acceptance Criteria thỏa.
```

## 🏁 Tín hiệu kết thúc loop (End-Loop Signal)
1. Audit `git diff` theo Acceptance Criteria, chấm thang 10; chỉ commit nếu > 9/10.
2. Test JVM cho mọi nhánh mới, đột biến bị bắt, full JVM `--rerun-tasks` xanh.
3. Chạy bản release R8 trên Pixel (gate, sao lưu byte-exact, khôi phục) theo REL-02 mục 3.
4. Cập nhật `doc/feature.md`; commit không attribution AI (R6); push khi owner yêu cầu.
