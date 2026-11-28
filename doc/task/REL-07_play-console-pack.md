# [REL-07] Gói nội dung cho Play Console (bản nháp để owner dán và xác nhận)

Soạn 2026-11-28 từ mã tại `bc9fcffd` và `REL-03_data-safety-audit.md`. **Tôi không có quyền vào Play Console và không thấy biểu mẫu đã nộp**; đây là nội dung đề xuất, từng ô cần owner đối chiếu với câu hỏi thật hiện trên màn hình. Không có gì ở đây đã được nộp.

## 1. Khai báo Foreground service (loại Media playback)

Manifest của AAB (đo bằng bundletool) khai 3 dịch vụ, cả ba `foregroundServiceType=mediaPlayback` và có quyền `FOREGROUND_SERVICE_MEDIA_PLAYBACK`:

| Dịch vụ | Người dùng bắt đầu bằng | Nội dung |
|---|---|---|
| `TtsForegroundService` | bấm nút đọc to ở trang đọc bài | đọc bài bằng giọng tổng hợp, có nút dừng ở thông báo |
| `PodcastPlaybackService` | bấm phát ở thanh podcast của bài | phát tập podcast của bài |
| `CommuteMediaSessionService` | bấm phát ở sheet CommuteCast, hoặc nút Play trên thông báo, hoặc tai nghe Bluetooth (tự phát, tuỳ chọn, mặc định tắt) | phát bản tin sáng hai MC, có điều khiển khoá màn hình |

Mô tả gợi ý: "Âm thanh chỉ phát khi người dùng bấm phát. Dịch vụ chạy nền để âm thanh không dừng khi tắt màn hình hoặc chuyển ứng dụng, có thông báo với nút dừng, và tự dừng khi hết nội dung."
- `CommuteMediaSessionService` có `exported="true"` (để hệ thống và tai nghe điều khiển phiên media); các dịch vụ còn lại `exported="false"`.
- **Chưa xác nhận:** Play có thể đòi video minh hoạ cho khai báo này; tôi không quay được. Tôi cũng chưa nghe được âm thanh TTS thật (chỉ thấy tầng hệ thống).

## 2. Data safety (đề xuất, owner xác nhận từng ô)

**Không có máy chủ của nhà phát triển, không analytics riêng, không đăng nhập tài khoản.**

| Loại dữ liệu | Thu thập / chia sẻ | Với ai | Mục đích | Bắt buộc? |
|---|---|---|---|---|
| Mã định danh thiết bị (Advertising ID, App set ID) | chia sẻ | Google (AdMob) | quảng cáo, chống gian lận | quảng cáo cá nhân hoá phụ thuộc đồng ý (UMP) |
| Địa chỉ IP, tương tác với quảng cáo, chẩn đoán | chia sẻ | Google (AdMob) | quảng cáo, phân tích, chống gian lận | như trên |
| Nội dung người dùng (tiêu đề và đoạn trích bài khi chạy tính năng AI) | chia sẻ | Google (Gemini) | tính năng AI do người dùng bật, có hộp thoại đồng ý | tuỳ chọn |
| Tên đăng nhập và mật khẩu WebDAV, Fever, Google Reader | gửi tới **máy chủ do chính người dùng nhập**, không tới nhà phát triển | máy chủ của người dùng | sao lưu, đồng bộ | tuỳ chọn |

- Chi tiết SDK quảng cáo theo trang công bố của Google ([Play data disclosure](https://developers.google.com/admob/android/privacy/play-data-disclosure)); khớp quyền trong manifest (`AD_ID`, `ACCESS_ADSERVICES_*`, `BIND_GET_INSTALL_REFERRER_SERVICE`).
- **Mã hoá khi truyền: không nên khai "tất cả dữ liệu được mã hoá khi truyền".** Lý do trong mã: `usesCleartextTraffic="true"` (nhiều nguồn RSS chỉ có HTTP). Chứng chỉ nay được xác thực theo mặc định cho RSS, ảnh, Bluesky, Nostr, Fever, Google Reader, WebDAV; chỉ máy chủ người dùng bật công tắc mới được miễn (đã thấy trên máy thật).
- **Rủi ro Play quét mã:** hàm `trustAllCerts()` trong `OkHttpClientModule.kt` còn tồn tại (dùng cho máy chủ tự ký do người dùng cho phép, kèm `@SuppressLint("CustomX509TrustManager")`). Play có thể vẫn báo "TrustManager không an toàn" dù không còn đường dùng mặc định. Tôi chưa biết Play có báo hay không.
- Xoá dữ liệu: dữ liệu nằm trên máy, gỡ ứng dụng là xoá; sao lưu đám mây theo cơ chế Android (một số tệp nhạy cảm đã loại khỏi sao lưu: `pairing_store.xml`, `webdav_backup_prefs.xml`, `loitp_admob.xml`).
- Quyền `ACCESS_LOCAL_NETWORK`: đồng bộ P2P giữa hai máy trong cùng mạng LAN, không quét thiết bị khác.

## 3. AdMob Console (việc của owner)

- Publish UMP: "European regulations" đã publish và đã thấy trên máy (2026-11-24); "US states" và Anh chưa kiểm. Ở vùng Mỹ UMP không hiện form (owner đã chấp nhận); tôi không đánh giá được khía cạnh pháp lý.
- Kiểm `app-ads.txt` (tôi chưa kiểm).

## 4. Đưa lên Internal testing

1. AAB dựng lại từ HEAD sau khi tăng `versionCode` lên `20261128` (đã commit `3fbabb0d`); kiểm chữ ký, ID quảng cáo, manifest (xem REL-06 mục AAB).
2. Tạo bản phát hành Internal testing, tải AAB, thêm người thử.
3. Lần cài đầu từ Play: kiểm logcat có `This request is sent from a test device` (nếu máy chưa có hash test-device cho chứng chỉ release, quảng cáo thật sẽ phục vụ; hash đã có cho A50s, Pixel 7 Pro, Tecno KJ7).
4. Thử trial 1 ngày (chỉ chạy được khi cài từ Play, qua Install Referrer) và nâng cấp từ bản đã có trên Play (nếu có).
5. Khi Internal testing ổn mới tính Closed/Production; khuyến nghị trong REL-06 vẫn là **chưa Production**.
