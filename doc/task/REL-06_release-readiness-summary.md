# [REL-06] Tóm tắt sẵn sàng phát hành (một trang)

Cập nhật 2026-11-28, nhánh `dev`; mã kiểm trên máy là bản `5f955943`. Đây là bản đọc nhanh để owner quyết định. Chi tiết và bằng chứng nằm ở `REL-05_owner-release-checklist.md` (từng ngày, từng máy) và `i18n-machine-translation-doubts.md` (10 đợt kiểm bản dịch). File này không thay chúng.

## Khuyến nghị

**Chưa nên phát hành lên Production.** Có thể đưa lên **Internal testing** sau khi owner làm xong các việc ở mục "Việc của owner". Lý do chính: bản dịch chưa người bản ngữ duyệt, TTS chưa nghe thật, chưa có máy API 26 đến 28, và phần quảng cáo bằng đơn vị thật mới thử một phần.

Tôi không nói "an toàn tuyệt đối". Tôi nói: những gì kiểm được bằng một máy Tecno và bộ test JVM thì đã kiểm, những gì không kiểm được thì ghi rõ ở dưới.

## Đã chứng minh trên máy thật (Tecno KJ7 Android 14 trong phần lớn phiên; Pixel 7 Pro Android 17 từ 2026-11-28; trước đó S24 Ultra)

- **Chứng chỉ TLS**: client chung (RSS, ảnh, Bluesky, Nostr) và Fever, Google Reader, WebDAV nay xác thực chứng chỉ; chỉ miễn cho host:port mà người dùng bật công tắc. Đã thấy: không bật thì máy chủ tự ký không nhận request nào; bật thì feed, ảnh và WebDAV (backup `PUT`, restore `GET`) chạy; tắt lại thì bị chặn ngay.
- **Một lỗi crash tìm ra và đã sửa** (`NetworkOnMainThreadException` khi bật công tắc ở trang Sức khỏe nguồn tin); test JVM không bắt được, chỉ máy thật mới thấy.
- **Tìm kiếm**: hàng tiêu đề kết quả ngữ nghĩa cao 454 dp làm tìm xong tưởng không có kết quả; đã sửa.
- **Chip bị ép** (hẹn giờ ngủ ở tiếng Ba Lan và tiếng Nga): lỗi có sẵn từ trước; đã sửa. Hàng chip điểm cược và nút kết quả của Oracle cũng đã đổi sang `FlowRow`: trên Pixel (2026-11-28), ở tiếng Ba Lan ba chip "Punkty: 50/100/500" vẫn vừa một hàng, và ở tiếng Malayalam (nhãn dài nhất) chip thứ ba **xuống hàng thứ hai gọn gàng thay vì bị bóp**, đúng ý đồ. Hàng chip mẫu thẻ trích dẫn chưa xem trên màn hình (cần bôi chữ trong bài).
- **Luồng chính**: cài mới (Welcome, Điều khoản, Agree, vào Feeds), mở bài đầu tiên không nhảy về Feeds, đọc bài, Reels, TTS (dịch vụ khởi động và dừng sạch), tìm kiếm.
- **Đổi ngôn ngữ**: process khởi động lại ngay, nhãn ngày đúng (đo trên Pixel trước khi bị cấm). Đã xem layout Feeds ở 10 locale và Settings ở 15 locale trên Tecno (uk, pl, it, in, pt, ru, es, zh-CN, ja, fr, cộng vi, de, ar, hi, eu, sr), không thấy chữ bị cắt; các locale còn lại chưa xem trên màn hình.
- **Dịch vụ TTS tự dừng** khi đọc xong một bài (đo được 35 ms sau `onDone`; đo trên Pixel trước khi bị cấm, trên Tecno chỉ thấy bấm dừng thì dịch vụ về 0).

## Chỉ chứng minh bằng JVM, chưa thấy trên máy

- Fever và Google Reader: nút thêm tài khoản bị ẩn từ `71dde14b` theo lựa chọn của owner, nên chưa ai tạo được tài khoản; mã, công tắc TLS và sửa id có `/` chỉ có test.
- Bluesky và Nostr qua chứng chỉ lỗi: chỉ test với máy chủ TLS dựng trong JVM (cả hai chỉ gọi tới máy chủ cố định, không trỏ được về máy chủ thử).
- Hàng chip mẫu thẻ trích dẫn, và dạng số nhiều ở sl, cs, ar.
- Toàn bộ **4315 test JVM xanh**, lint sạch, nhưng đây không thay cho thử trên máy.

## Chưa kiểm được (không có bằng chứng nào)

- **Âm thanh TTS**: chỉ thấy tầng hệ thống (track `CONTENT_TYPE_SPEECH` ở trạng thái `started`, AudioFlinger `ACTIVE`, gain `-27 dB` khi tôi tăng âm lượng loa từ 0 lên 6). Tôi không nghe được qua adb; owner cần tự nghe một lần.
- **API 26 đến 28**: không có máy.
- **Quảng cáo thật**: mới chạy rewarded bằng đơn vị thật trên Pixel trong một phiên trước (nhãn "Quảng cáo thử nghiệm", 0 tín hiệu `Use RequestConfiguration`); banner, interstitial và App Open với đơn vị thật chưa thử. Trên Tecno trong các lượt gần đây chỉ gặp quảng cáo thử của Google có nhãn "Test Ad" và 0 `Use RequestConfiguration`.
- **Nâng cấp từ một bản store thật** và **Play Console, UMP, Data safety**: ngoài khả năng của tôi.

## Bản dịch: rủi ro lớn nhất còn lại

37 locale đều là dịch máy, chưa người bản ngữ duyệt. Tôi đã chạy kiểm độc lập bằng agent cho cả 37 locale qua nhiều đợt và quét máy móc các mẫu lỗi, tìm và sửa vài trăm lỗi (chuỗi sai ngôn ngữ, thông báo hoàn tất bị dịch thành mệnh lệnh, cảnh báo xoá bị rơi chữ, cảnh báo bảo mật chứng chỉ bị đảo nghĩa ở Azerbaijan, HTML của Điều khoản hỏng ở ar, bg, sl, tiếng Việt còn nhiều nhãn tiếng Anh).
- **Phải nói rõ giới hạn**: agent cũng là mô hình ngôn ngữ, có thể cùng thiên lệch với bản dịch gốc; với chữ hiếm (kn, ml, my) và Văn ngôn (lzh) độ tin cậy thấp; agent chỉ đọc chuỗi khác bản gốc nên nhãn còn nguyên tiếng Anh không được xem (đã quét riêng 393 chuỗi và dịch 314).
- **Còn nhiều mục "gượng" hoặc thuật ngữ** chưa sửa. Chi tiết trong `i18n-machine-translation-doubts.md`, mục "Chưa sửa" của từng đợt.
- Văn bản pháp lý (`tos_content`) đã sửa lỗi HTML, URL và vài câu sai nhưng **vẫn là dịch máy**; với một ứng dụng thu thập dữ liệu quảng cáo, nên nhờ người bản ngữ đọc ít nhất tiếng Việt, Nhật, Ba Lan và Nga trước khi phát hành.

**Phương án giảm rủi ro nếu không có người duyệt**: chỉ phát hành các ngôn ngữ đã kiểm kỹ nhất (en, vi, và vài ngôn ngữ lớn), bỏ phần còn lại khỏi `resourceConfigurations` trong `app/build.gradle` (phải khớp thư mục `values-*`). Người dùng ngôn ngữ bị bỏ sẽ thấy tiếng Anh. Đây là quyết định sản phẩm của owner; tôi không tự làm.

## Việc của owner (tôi không làm được)

1. **Play Console**: khai Foreground service loại Media playback; Data safety (Advertising ID, văn bản bài gửi Gemini khi người dùng tự nhập key, WebDAV có mật khẩu, Bluetooth, mạng LAN); không khẳng định "mã hoá mọi dữ liệu khi truyền" khi `usesCleartextTraffic` còn `true`.
2. **AdMob Console**: publish UMP (EEA và US states) cho đúng App ID.
3. **Dựng và tải AAB**: `bundleProdRelease` (không `-PallowTestAdIds=true`), kiểm file AAB thực sự được tạo (hai lần trước build "thành công" mà không ra file), kiểm chữ ký (`keytool -printcert -jarfile`, prefix `67:B2:E6:CD`), cài bản ký thật lần đầu rồi kiểm logcat không có `Use RequestConfiguration`.
4. **Đưa lên Internal testing trước**, sau đó mới Production.
5. Tự nghe TTS một lần; nếu có thể, thử trên một máy API 26 đến 28.

## Phát hiện trên Pixel (2026-11-28)

- Cài bản `5f955943` đè lên bản debug cũ trên Pixel, mở ở tiếng Ba Lan: Feeds, danh sách bài, huy hiệu "Prognoza" và sheet Oracle (đã chèn một claim thử vào bảng `oracle_market` rồi khôi phục byte-exact; không có claim thật trong DB). Sheet hiện đúng nhãn Ba Lan; hai nút "Tak/No" và ba chip điểm nằm gọn.
- Tôi thử cỡ chữ hệ thống `1.3` để ép nhãn dài hơn, **không có tác dụng**: `MainActivity`, `SplashActivity` và `AboutActivity` cố ý đặt `configuration.fontScale = 1.0f`, nên app bỏ qua cỡ chữ của hệ thống. Điều này có nghĩa người dùng đặt chữ lớn vì thị lực **không** thấy chữ lớn hơn trong app; đó là một quyết định sản phẩm đáng owner biết (khả năng tiếp cận). Giá trị `font_scale` của Pixel đã trả về `1.0`.
- Quảng cáo toàn màn hình hiện lên ở hầu hết các lần mở app (4 trên 4 lần mở, với nhãn "Reklama testowa"/quảng cáo thử); một lần tôi nhấn nhầm vào creative ở tiếng Malayalam (nhãn kiểm tra không khớp danh sách tôi đặt) nên mở Play Store, đã thoát, không cài gì. Pixel đã được khôi phục byte-exact về bản sao đầu phiên, kể cả ngôn ngữ (tiếng Anh) và Wi-Fi (bật).
- Màn Triage ở tiếng Malayalam (nhãn dài) hiện ba nút tròn với nhãn dưới mỗi nút, không bị ép: `TriageBottomBar` không cần sửa.
- **Fever và Google Reader vẫn chưa kiểm trên máy**: tôi dựng sẵn máy chủ Fever giả và khoá DES (đã kiểm chéo Java và OpenSSL cho cùng ciphertext), nhưng để vào được phải chèn tài khoản vào DB và đổi tài khoản hiện hành trong DataStore, mà DataStore chứa cả cài đặt người dùng. Owner chọn bỏ qua; ghi lại để khỏi ai tưởng đã kiểm.

## Chưa quyết định, cần owner

- Giữ ẩn nút thêm Fever và Google Reader (đã chọn giữ ẩn), hay mở lại?
- **App có nên theo cỡ chữ hệ thống không?** Hiện nó ép `fontScale = 1.0` ở ba activity; nếu bỏ ép thì chữ lớn sẽ làm lộ nhiều hàng chip và nhãn dài chưa kiểm.
- Có giới hạn số locale phát hành không (xem trên)?
- Giữ tên tính năng "Feed Autopilot", "Second Brain" đã dịch ở uk, pl, it, in hay giữ tiếng Anh ở mọi locale cho nhất quán?

## Mô tả cách làm việc của phiên này (để owner biết bằng chứng đến từ đâu)

Mỗi sửa lỗi theo thứ tự: viết test đỏ, sửa, xanh, phá lại (mutation) để thấy test đỏ lại, rồi chạy toàn bộ JVM bỏ cache và lint. Trên máy: luôn kiểm cổng (launcher, mở khoá), backup byte-exact trước, khôi phục sau, và chỉ gửi lệnh tới một máy do owner chọn: Tecno cho tới 2026-11-27, Pixel từ 2026-11-28 ("lưu ý dùng pixel cho mọi phiên"); không đụng máy còn lại. Những chỗ tôi sai và đã sửa lại: nhấn nhầm toạ độ vài lần (đo lại bằng `uiautomator dump`), một lần thay chuỗi Pháp ngoài ý muốn (phát hiện qua diff và hoàn nguyên), và một test ngưỡng đặt sai (80 dp, hạ xuống 50 dp theo số đo thật).
