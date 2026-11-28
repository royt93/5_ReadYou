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

## Kiểm tra cỡ chữ hệ thống và hàng chip trên Pixel (2026-11-28)

- **Cỡ chữ hệ thống:** backup byte-exact trước; đặt font scale 1.3, app ở "dùng ngôn ngữ thiết bị" (vi-VN). Feeds và Settings đều hiển thị chữ lớn rõ rệt và không cắt; sheet Oracle mở được. Đặt lại font scale 1.0 ở cuối.
- **Đã xác minh nguyên nhân không nhất quán:** khi app ở "dùng ngôn ngữ thiết bị", font scale 1.3 được áp dụng; khi đặt ngôn ngữ riêng trong app (thử ở tiếng Ba Lan), app hiển thị cỡ chữ nhỏ lại. Mã `attachBaseContext` của MainActivity, SplashActivity và AboutActivity đặt `configuration.fontScale = 1.0f` chỉ trong nhánh `languagePref != 0`; nhánh thiết bị thoát sớm. Sau đó owner quyết định bỏ ép (xem mục các quyết định); mã đã đổi.
- **Oracle / Triage ở chữ lớn:** cỡ chữ 1.3 vẫn hiển thị sheet Oracle tiếng Malayalam với câu hỏi, nút Yes/No, chip cược 50/100/500; chip xuống hàng gọn, nút đặt cược còn thấy. Triage Malayalam cũng hiện ba nút tròn và nhãn riêng.
- **Các locale có nhãn số nhiều (bản `88b771a5`):** ở ru, `15 минут`/`30 минут`; ở pl, `15 minut`/`30 minut` đều một dòng. Dạng nhiều như `60 минут` nằm ngoài khung chip và có thể cuộn ngang để tới; ảnh trước chip cuối vẫn thấy đủ. Trước plurals, ru chip 60 phút bị bóp, chữ rơi từng dòng; dùng `LazyRow` giờ chip 15/30/60 nằm cùng một hàng cuộn. Chụp Pixel tại `~/exc-flaky-evidence/audit-20261128-pixel/final/ru-zen.png`, `pl-zen.png`, `pl-zen-scroll.png`.
- **Chưa kiểm TTS nghe thật:** tôi đã mở sheet Zen ở ru/pl nhưng không bấm nút phát âm; chỉ xác nhận các nhãn chip.
- Quảng cáo toàn màn hình có nhãn "Reklama testowa" xuất hiện bốn lần. Tôi đóng bằng nút "Tiếp tục đến ứng dụng". Một lần ở Malayalam tôi chạm nhầm creative trước khi kiểm tra xong nhãn và Play Store mở; thoát ra, không cài gì. Wi‑Fi tắt tạm khi đo bố cục và bật lại sau.
- Pixel đã khôi phục byte-exact về backup `backup-fontscale` đầu lượt: locale app rỗng, font scale 1.0, Wi‑Fi bật, app ở launcher, gate OK. Tôi không gửi lệnh tới Tecno hay S24 Ultra.

## AAB prodRelease dựng từ `bc9fcffd` (2026-11-28 17:30)

`./gradlew bundleProdRelease`, không `-PallowTestAdIds`. File `app/build/outputs/bundle/prodRelease/app-prod-release.aab`, 51.491.648 byte, mtime khớp thời điểm build (không phải file cũ), SHA-256 `2459772f9f2100d505aeaca4e38a27d1174e34317ac85942423816face3b9106`.
- **Chữ ký:** `jarsigner -verify` ra "jar verified"; chứng chỉ `CN=mckimquyen`, SHA-256 `67:B2:E6:CD:C2:D4:...:8B:03:82`, RSA, hạn tới 2049-05-26.
- **ID quảng cáo:** 4 ad unit thật nằm trong dex (mỗi ID 1 lần), App ID thật nằm trong manifest; 0 unit ID mẫu của Google. Chuỗi `3940256099942544` còn trong `classes2.dex` chỉ là hằng so sánh của `AdValidationHelper`.
- **Manifest** (bundletool): `com.mckimquyen.reader`, versionCode 20261124, versionName 2026.11.24, minSdk 26, targetSdk 37, **không** `debuggable`; có `AD_ID`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`; `usesCleartextTraffic="true"`.
- **16 KB:** 4 thư viện native arm64 đều căn `0x4000`.
- **Chưa làm:** không cài bản ký thật lên máy nào ở lượt này; `versionCode` vẫn là bản 2026-11-24 và đã có 85 commit từ đó, nên **tăng `versionCode` trước khi tải lên Play** (Play từ chối trùng mã).

## AAB prodRelease dựng lại từ `4757b888` (2026-11-28 19:09), versionCode 20261128

Thay cho bản dựng từ `bc9fcffd`. `bundleProdRelease`, không `-PallowTestAdIds`; 51.516.523 byte, mtime khớp lúc build; SHA-256 `08645542a853b735e3118b49ab7fd03d488ae1a7a6d5947f2db81be1a94bf73f`.
- `jarsigner -verify`: "jar verified"; chứng chỉ SHA-256 `67:B2:E6:CD:...:8B:03:82`. Zip không lỗi.
- 4 ad unit thật có trong dex (mỗi ID 1 lần), App ID thật trong manifest. Manifest: `versionCode 20261128`, `versionName 2026.11.28`, minSdk 26, targetSdk 37, không `debuggable`.
- 4 thư viện native arm64 căn `0x4000` (16 KB).
- **Cài bản ký thật lên Pixel (2026-11-28):** kiểm `universal.apk` từ AAB (`bundletool`) thấy đủ 8/8 key mới (`commute_fb_*`, `commute_empty_*`, `a11y_volume`, `rpg_xp_gained`) và bản dịch tiếng Nga trong `resources.arsc`. Cài `RSS Cat Hub-2026.11.28-389dd6bf.apk` (`prodRelease`, ký khoá thật `67:B2:E6:CD...`) sau khi gỡ bản debug và backup trước; logcat `Ads` có 3 dòng `This request is sent from a test device.`, `Use RequestConfiguration` = 0, đồng ý điều khoản và vào Feeds bình thường. Sau đó đã gỡ bản release, cài lại bản debug và khôi phục byte-exact theo `backup-commute-fallback`. Chưa thử trial 1 ngày và nâng cấp từ bản store.
- **Kiểm bản debug mới nhất trên Pixel (tiếng Nga, font 1.3):** mở CommuteCast và bấm tạo tập mới ("3 мин"), toàn bộ lời thoại dự phòng ("Доброе утро!...") và thẻ Deep Dive ("CommuteCast AI DJ · 15 мин", "Глубокий разбор...") hiển thị đúng tiếng Nga, không còn chữ Việt. Ảnh tại `~/exc-flaky-evidence/audit-20261128-pixel/final4/`.

## Việc của owner (tôi không làm được)

1. **Play Console**: khai Foreground service loại Media playback; Data safety (Advertising ID, văn bản bài gửi Gemini khi người dùng tự nhập key, WebDAV có mật khẩu, Bluetooth, mạng LAN); không khẳng định "mã hoá mọi dữ liệu khi truyền" khi `usesCleartextTraffic` còn `true`.
2. **AdMob Console**: publish UMP (EEA và US states) cho đúng App ID.
3. **Dựng và tải AAB**: `bundleProdRelease` (không `-PallowTestAdIds=true`), kiểm file AAB thực sự được tạo (hai lần trước build "thành công" mà không ra file), kiểm chữ ký (`keytool -printcert -jarfile`, prefix `67:B2:E6:CD`), cài bản ký thật lần đầu rồi kiểm logcat không có `Use RequestConfiguration`.
4. **Đưa lên Internal testing trước**, sau đó mới Production.
5. Tự nghe TTS một lần; nếu có thể, thử trên một máy API 26 đến 28.

## Phát hiện trên Pixel (2026-11-28)

- Cài bản `5f955943` đè lên bản debug cũ trên Pixel, mở ở tiếng Ba Lan: Feeds, danh sách bài, huy hiệu "Prognoza" và sheet Oracle (đã chèn một claim thử vào bảng `oracle_market` rồi khôi phục byte-exact; không có claim thật trong DB). Sheet hiện đúng nhãn Ba Lan; hai nút "Tak/No" và ba chip điểm nằm gọn.
- Tôi thử cỡ chữ hệ thống `1.3` để ép nhãn dài hơn: ở lần đo đó (đang đặt tiếng Ba Lan) **không có tác dụng**. Đọc mã cho thấy lý do: `MainActivity`, `SplashActivity` và `AboutActivity` đặt `configuration.fontScale = 1.0f` **trong nhánh khi người dùng đã chọn một ngôn ngữ riêng** trong app (`languagePref != 0`); khi chọn "dùng ngôn ngữ thiết bị" thì `attachBaseContext` thoát sớm và không ép. Vậy cùng một người dùng đặt chữ lớn vì thị lực sẽ thấy chữ lớn khi dùng ngôn ngữ thiết bị, nhưng **không** thấy khi chọn một ngôn ngữ cụ thể trong app: một hành vi không nhất quán, nhiều khả năng ngoài ý muốn (tôi chưa kiểm trường hợp "ngôn ngữ thiết bị" trên máy). Giá trị `font_scale` của Pixel đã trả về `1.0`.
- Quảng cáo toàn màn hình hiện lên ở hầu hết các lần mở app (4 trên 4 lần mở, với nhãn "Reklama testowa"/quảng cáo thử); một lần tôi nhấn nhầm vào creative ở tiếng Malayalam (nhãn kiểm tra không khớp danh sách tôi đặt) nên mở Play Store, đã thoát, không cài gì. Pixel đã được khôi phục byte-exact về bản sao đầu phiên, kể cả ngôn ngữ (tiếng Anh) và Wi-Fi (bật).
- Màn Triage ở tiếng Malayalam (nhãn dài) hiện ba nút tròn với nhãn dưới mỗi nút, không bị ép: `TriageBottomBar` không cần sửa.
- **Fever và Google Reader vẫn chưa kiểm trên máy**: tôi dựng sẵn máy chủ Fever giả và khoá DES (đã kiểm chéo Java và OpenSSL cho cùng ciphertext), nhưng để vào được phải chèn tài khoản vào DB và đổi tài khoản hiện hành trong DataStore, mà DataStore chứa cả cài đặt người dùng. Owner chọn bỏ qua; ghi lại để khỏi ai tưởng đã kiểm.

## Các quyết định owner đã chọn

- Giữ ẩn nút thêm Fever và Google Reader; vì thế luồng tài khoản và sync của hai dịch vụ vẫn chưa được kiểm trên máy.
- Cỡ chữ (2026-11-28, owner đổi quyết định): bỏ ép `fontScale = 1.0` khi người dùng chọn ngôn ngữ riêng; `MainActivity`, `SplashActivity`, `AboutActivity` nay theo cỡ chữ hệ thống ở cả hai nhánh. Test nguồn `FontScaleNotPinnedSourceTest` (đã phá thử: thêm lại dòng ép thì đỏ). Đã kiểm trên Pixel ở cỡ 1.3 với ngôn ngữ riêng (tiếng Ba Lan, tiếng Nga): chữ lớn thật (cao 97 px so với 82 px ở 1.0), Feeds, Settings, danh sách bài, menu ⋮ và màn Triage (nút Пропустить/Читать/Сохранить) không bị cắt. Oracle ở nhánh ngôn ngữ riêng đã kiểm lại ở mục dưới.
- **Lỗi tìm thấy khi kiểm** (tiếng Nga, 1.3): chip "15 мин" của CommuteCast bị bóp, chữ rơi từng dòng. Đã sửa bằng `FlowRow` (`2a500155`, test `CommuteBudgetChipsSourceTest`, mutation đỏ); trên máy chip xuống hàng hai gọn. Ảnh: `~/exc-flaky-evidence/audit-20261128-pixel/final2/ru-13-commute-after.png`.
- **Oracle và Zen ở tiếng Ba Lan, font 1.3 (Pixel):** sheet Oracle đủ chữ, "Tak"/"No" một hàng, chip "Punkty: 500" xuống hàng hai gọn, nút đặt cược vừa. Chèn một claim thử vào `oracle_market` để thấy huy hiệu, đã khôi phục (0 hàng). Chip hẹn giờ "15 minut"/"30 minut" một dòng. **Lỗi tìm thấy**: nút "Czytaj na głos ..." của Zen cao cố định 56 dp nên dòng thứ hai bị cắt; đã sửa bằng `heightIn(min = 56.dp)`, test `ZenPlayButtonHeightSourceTest`, thấy đủ hai dòng trên máy. Ảnh: `~/exc-flaky-evidence/audit-20261128-pixel/final3/`. Nhãn số nhiều ar vẫn chưa xem trên màn hình.
- **Chuỗi cứng**: quét 64 ứng viên, thay 8 chỗ bằng resource có sẵn (`809d45b3`). Còn nhãn `contentDescription` ("Country", "Navigate", "RSS Source", "URL", "Already Added", "Checked", "Volume"), "🎙️ Deep Dive Edition", "💡 Note:", toast "+$awarded XP! 🎉", "+300 XP", tiêu đề tập dự phòng "CommuteCast Morning Edition" vẫn tiếng Anh ở mọi locale; cần thêm chuỗi và dịch 37 locale. `RsvpCalibrationContent` có đoạn văn mẫu en/vi gõ cứng, chưa đọc mã dùng nó nên chưa kết luận.
- **Chưa sửa, ngoài phạm vi**: `CommuteCastSheet.kt` còn chuỗi tiếng Việt cứng ("Đoạn x / y", "Đang phát...", "Đã dừng", "• Đang phát", "CommuteCast Deep Dive (15 phút)", "Nghe Deep Dive (Đặc quyền VIP)"...). Chúng hiện tiếng Việt ở mọi locale. Cần đưa vào `strings.xml` (37 locale) nếu owner muốn.
- Owner chưa chọn giới hạn locale phát hành. Hiện `resourceConfigurations` vẫn gồm 37 locale; 393 nhãn còn nguyên tiếng Anh ở một số locale (xem mục bản dịch).
- Tên tính năng "Feed Autopilot"/"Second Brain" đã dịch ở uk, pl, it, in; chưa có quyết định giữ tiếng Anh ở tất cả locale.

## Mô tả cách làm việc của phiên này (để owner biết bằng chứng đến từ đâu)

Mỗi sửa lỗi theo thứ tự: viết test đỏ, sửa, xanh, phá lại (mutation) để thấy test đỏ lại, rồi chạy toàn bộ JVM bỏ cache và lint. Trên máy: luôn kiểm cổng (launcher, mở khoá), backup byte-exact trước, khôi phục sau, và chỉ gửi lệnh tới một máy do owner chọn: Tecno cho tới 2026-11-27, Pixel từ 2026-11-28 ("lưu ý dùng pixel cho mọi phiên"); không đụng máy còn lại. Những chỗ tôi sai và đã sửa lại: nhấn nhầm toạ độ vài lần (đo lại bằng `uiautomator dump`), một lần thay chuỗi Pháp ngoài ý muốn (phát hiện qua diff và hoàn nguyên), và một test ngưỡng đặt sai (80 dp, hạ xuống 50 dp theo số đo thật).

