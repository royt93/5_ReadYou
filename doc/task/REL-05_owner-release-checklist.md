# [REL-05] Việc của owner trước khi phát hành bản mới (Android + AdMob)

Cập nhật 2026-11-24. Phần này tôi không làm được thay: Play Console, AdMob Console. Không có giá trị bí mật trong file này (ID, hash, khoá nằm ở `myKeyStore`, repo private).

Quyết định của owner đã chốt:
- Giữ AppLovin trong APK (không exclude `applovin-sdk`), khai Data safety cho cả hai SDK.
- Giữ interstitial ở "Next Article" (xem `audit_gemini.md`, mục rủi ro hạn mức chung).

## 1. UMP (đồng ý quảng cáo): publish message thật

Máy chỉ thử được bằng message thử. Việc owner làm trên AdMob Console:
1. Privacy & messaging > European regulations: tạo và **publish** message cho đúng App ID (chọn đúng app Android).
2. Privacy & messaging > US states regulations: publish message và bật các bang cần.
3. Kiểm "Ad choices" trong app: Settings > Privacy > Ad choices (đã có mã và test, chưa chạm trên máy).
4. Sau khi publish, thử trên máy ở EEA bằng `umpDebugGeography` (chỉ bản debug) và xác nhận form hiện, chọn "Consent" rồi "Manage".

## 2. Data safety (Play Console), cần owner điền

Nguồn dữ liệu: `doc/task/REL-03_data-safety-audit.md`, mục 5 và mục 7. Tóm tắt phần quảng cáo, cho cả hai SDK vì AppLovin còn trong APK:
- Thiết bị hoặc ID khác (Advertising ID, App Set ID): thu thập, chia sẻ với bên thứ ba, mục đích Quảng cáo/Tiếp thị.
- Quảng cáo hoặc tương tác (impression, doanh thu): chia sẻ với Google và AppLovin, mục đích Quảng cáo.
- Chẩn đoán/Hiệu năng: SDK quảng cáo có thể gửi.
- Khai quyền `AD_ID` (đã có trong manifest) ở mục "Advertising ID" của App content.
- Không khẳng định "mã hoá khi truyền cho mọi dữ liệu" khi `usesCleartextTraffic` còn `true` (SEC-01 còn mở).
- Tôi chưa có bản chụp biểu mẫu hiện tại, và không bắt được gói tin thật của SDK. Owner đối chiếu từng mục với biểu mẫu thật.

## 3. AppLovin (đang giữ trong APK)

- Key SDK và 4 unit AppLovin trong `app.properties` là giá trị cũ, **chưa xác minh**. Vào AppLovin Dashboard kiểm khoá còn hợp lệ và 4 unit đúng app. Khi `IS_ENABLE_ADMOB=true` (hiện tại) AppLovin không chạy, nên sai ở đây không gây lỗi ngay, nhưng nếu ai bật `false` mà key sai thì mất ad.
- Nếu muốn bỏ ~3 MB: exclude `applovin-sdk` build được nhưng `AdManager` vẫn có mã gọi lớp AppLovin ở nhánh AppLovin; chưa chạy R8 release và máy thật cho phương án này. Owner đã chọn giữ.

## 0. Việc phải làm TRƯỚC khi upload AAB (từ audit độc lập 2026-11-24, kết luận GO có điều kiện, 7.5/10)

1. **Play Console > App content > Foreground service permissions:** khai loại **Media playback** (đọc to bài, podcast, CommuteCast). Bản này thêm `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`, `BLUETOOTH_CONNECT`, `ACCESS_LOCAL_NETWORK` và 3 service `foregroundServiceType=mediaPlayback`. Thiếu khai báo có thể chặn review. Owner đã nhận khai trước khi upload.
2. **Data safety:** thêm Advertising ID, văn bản bài báo gửi tới Gemini (chỉ khi người dùng tự nhập key), máy chủ WebDAV do người dùng cấu hình (có mật khẩu), thiết bị Bluetooth, mạng LAN.
3. **Nâng cấp DB v6 lên v22:** đã có `RoomUpgradeFromV6ToV22Test` (Room mở DB v6 bằng migration production và xác thực schema 22). Chưa nâng cấp được từ một APK bản store thật; nếu có máy dùng bản store, cài đè bản mới và đếm lại feed và bài.
4. **Build đúng task:** `bundleProdRelease` (flavor `prod`). Hai lần build gần nhất của tôi trả "BUILD SUCCESSFUL" trong 13 giây nhưng KHÔNG tạo file AAB (trạng thái up-to-date cũ); chỉ `--rerun-tasks` mới ra file. Sau khi build, kiểm `app/build/outputs/bundle/prodRelease/app-prod-release.aab` có tồn tại và dùng `keytool -printcert -jarfile` đọc chữ ký. Flavor `dev` cùng `applicationId`. Nếu thiếu `KEYSTORE_FILE` thì Gradle bỏ qua cấu hình ký và ra AAB không ký, nên kiểm chữ ký sau khi build (`keytool -printcert -jarfile app-prod-release.aab`, prefix `67:B2:E6:CD`).
5. **Cờ `-PallowTestAdIds=true` KHÔNG được dùng** khi build để upload.

## Điều đã biết, chưa sửa ở bản này (ghi để sửa bản sau)

- `androidx.activity.ComponentActivity` khai `exported="true"` không có bộ lọc intent trong manifest chính (thêm 2026-09-06 cho bộ test Android 17); nên chuyển sang manifest debug.
- File mapping R8 trong AAB là 264 MiB (`-dontobfuscate` cộng keep rộng cho compose, okhttp3, retrofit2). Google không nêu giới hạn riêng cho mapping Java (800 MB là của debug symbols native); nếu Play từ chối, thu gọn keep rule.
- Mã đổi VIP và `VIP_INTEGRITY_SECRET` nằm sẵn trong dex (VIP đã được owner hoãn).
- `allowBackup="true"`: Room và DataStore `settings` (có `customGeminiApiKey`) vào cloud backup.
- ĐÃ SỬA 2026-11-24: App Open bị chặn khi quay lại từ chooser chia sẻ và Custom Tabs (`AppOpenGuard.leaveForOtherApp`), `ComponentActivity` bỏ khỏi manifest chính, `AboutActivity` exported=false, mapping R8 264 → 127 MiB (bỏ `-keep class androidx.compose.**`).
- Test `CommuteCastViewModelTest` đã fail 1 lần trong full JVM. Điều tra 2026-11-24: KHÔNG tái hiện được (lớp chạy riêng 60/60 xanh kể cả dưới tải CPU, full JVM 4 lần xanh, 400 vòng lặp trong một JVM xanh). Chưa có nguyên nhân gốc. Giả thuyết chưa chứng minh: test dùng `Dispatchers.IO`/`Default` thật và `runBlocking` trên DataStore (`CommuteCastViewModel.kt` quanh `prepareOrPlay`, `ExtDataStore.kt:207-221`) nên `advanceUntilIdle()` không chờ được; DataStore là singleton chung giữa các class test. Lần chạy tải nặng cũng làm `SubscribeDialogTypingTest` fail 1 lần ("component is not displayed"), cùng kiểu lỗi thời gian.

## 4. Kiểm lần cuối trước khi đưa lên store

1. Dựng bản **release ký thật** (cần `keystore.jks` trong `myKeyStore`), không cài lên máy test nếu chưa có hash cert đó. Máy owner: hash cert release đã có cho A50s, Pixel, Tecno KJ7.
2. Lần đầu cài bản ký thật: mở app và kiểm logcat `This request is sent from a test device.` ngay lần chạy đầu. Nếu thấy `Use RequestConfiguration` thì **dừng ngay**, không bấm vào ad.
3. Thử trial 1 ngày: chỉ chạy khi cài từ Play (Internal testing), cần Install Referrer; build debug và sideload bỏ qua.
4. Đưa lên **Internal testing** trước, rồi mới Production.
5. Bản dịch hai chuỗi VIP và hai chuỗi Ad choices là dịch máy, chưa người duyệt.

## 5. Giới hạn đã biết

- Ad bằng đơn vị thật: đã chạy rewarded trên Pixel (máy đã đăng ký hash) với build ký thật, nhãn "Quảng cáo thử nghiệm", 0 tín hiệu `Use RequestConfiguration` (2026-11-24, HEAD 2d915178). Chưa thử banner, interstitial, App Open với đơn vị thật.
- Nâng cấp dữ liệu thật (Pixel, 2026-11-24): cài bản R8 đè lên bản 20261122, giữ 2164 bài, 9 feed, Room v22. Chooser chia sẻ và Custom Tabs không bật App Open (log `suppressAppOpenTemporarily`, đối chứng Home thì hiện). Bản R8 mới không crash ở home, Settings, CommuteCast, danh sách, trang đọc, menu, chooser. Đã mở thêm màn hình thứ hai, hộp thoại đồng ý AI, tìm kiếm (kể cả khớp ngữ nghĩa AI), Privacy & Security, cài đặt AI: 0 crash. Đã mở thêm thẻ trích dẫn (render PNG và chooser ảnh): 0 crash. So sánh class hai APK R8 (17 566 class biến mất, 94/97 class tên là file Compose bị R8 gộp), DTO Gson (Fever, GoogleReader, Highlights, Digest, Episode, WebDAV) đều có `@Keep` hoặc keep field rule, có test `ProguardRulesTest` giữ điều này. Đã mở thêm (R8, Pixel, 18:35): sơ đồ tư duy, AI Deep Read, AI Deep Dive, Podcast Studio (chỉ tới hộp thoại đồng ý, không bấm đồng ý, không gọi Gemini): 0 crash. Đã thấy tiếp (R8, Pixel, 18:44): trình sửa Highlight, trang Sổ tay tri thức, trang Sao lưu WebDAV: 0 crash. Widget: chỉ kiểm provider còn đăng ký và 8 class còn sau R8, chưa đặt lên màn hình chính nên chưa thấy nó vẽ.
- Chưa có máy API 26-28.
- Interstitial và rewarded dùng chung hạn mức: xem `audit_gemini.md`.

## 6. Audit chức năng và giao diện trên Pixel (2026-11-24 tối), chưa nên phát hành

Owner huỷ đợt phát hành vì nhiều lỗi chức năng. Tôi đã rút lại kết luận "publish được" ở các mục trên. Bằng chứng nằm ở `~/exc-flaky-evidence/audit-20261124-pixel/` (ảnh, màu đo từng điểm ảnh, log `roy93~Flow`).

Đã sửa và có bằng chứng trên máy:
- **Nền không đồng bộ:** Settings `#FDEDEB`, nhiều trang con `#FFFBFF`. Nguyên nhân: mỗi trang tự đặt `containerColor`. Thêm `settingsPageContainerColor()` trong `BaseScaffold.kt`, 33 trang dùng nó, đo lại trên Pixel đều `#FDEDEB`. Test `SettingsPageBackgroundTest`. About (Activity riêng) trước đó không bọc `AppTheme`, đã bọc.
- **Trang "Bản tin AI hằng ngày":** không thanh trên, không nút back, tiêu đề dính đỉnh. Đã bọc `BaseScaffold` có nút back, bấm nút back thật về đúng danh sách.
- **Reels, "Vuốt phải để đọc":** vuốt bắt đầu ở cạnh trái (x=20) bị cử chỉ Back của hệ thống chiếm, đẩy user ra Home. Tái hiện được trên Pixel (điều hướng cử chỉ). Sửa: thêm nút "Đọc bài viết" trên thẻ, giữ vuốt phải, nhãn thành "Vuốt phải hoặc chạm Đọc để mở", loại trừ dải 32x200dp ở cạnh trái khỏi Back (giới hạn Android 200dp). Test `ReelsOpenArticleTest`. Trên máy: chạm nút, vuốt từ cạnh trái, vuốt từ giữa đều mở bài (log `reels: open via=button|swipe`).
- **Bản dịch:** 22 locale có trên 60% chuỗi y hệt tiếng Anh (16.375 chuỗi). Đã dịch máy toàn bộ (chưa người duyệt, danh sách nghi ngờ ở `i18n-machine-translation-doubts.md`). Xoá 40 chuỗi chết, trong đó 3 chuỗi `mark_as_read_*` là câu vô nghĩa chép vào 37 file. Test `StringResourcesHealthTest` chặn chuỗi chết, bản dịch rỗng, bản dịch đổi placeholder.
- **E-Ink:** khi công tắc E-Ink ở Màu & kiểu bật, toàn app chuyển nền trắng và xám. Đây là một nguyên nhân thật của "nền lúc trắng lúc màu". Đã thêm log `setting: eink on=...` để dò.

Chưa chứng minh:
- Lỗi "nút back không work" mà owner thấy: chưa tái hiện được. Back qua phím hệ thống, nút trên màn hình ở Digest và Reels đều chạy.
- "Nhiều tính năng hỏng logic": chưa tìm thêm tính năng nào hỏng ngoài các mục trên. Tính năng gọi Gemini (Digest, AI, Podcast) chưa chạy vì không gọi Gemini.
- Chưa mở OPML, widget trên màn hình chính, Thống kê đọc với dữ liệu thật.
- Dịch 22 locale là dịch máy.
- Tap của tôi từng rơi vào một ad (Chrome mở một trang cửa hàng). Log chỉ có dòng "test device", 0 tín hiệu `Use RequestConfiguration`, nhưng không chứng minh được không có click. Owner nên xem báo cáo AdMob.

Cách dò thủ công: `adb logcat -s 'roy93~Flow'` in điều hướng (`nav: screen route=...`), mở bài từ Reels, đổi E-Ink, bắt đầu sync. SDK quảng cáo ghi riêng ở `roy93~Ad*`. `AppLog` không ghi tiêu đề, URL, khoá; test chặn điều đó.

## 7. Audit mã bốn mảng bằng agent, kiểm lại từng dòng (2026-11-25)

Bốn agent đọc mã (dữ liệu và đồng bộ, đọc và âm thanh, điều hướng và cử chỉ, AI và VIP và Settings) trả 56 phát hiện. Tôi chỉ tin phát hiện nào tôi tự đọc lại mã hoặc chạy được. Mỗi sửa có test, và test được thử bằng đột biến (đưa lỗi cũ lại, test phải đỏ).

Đã sửa (commit `23b7fc86`, `2465ab44`, `36d2dbe4`, `af9a686b`, `31f8a36f`):
- **Block list tài khoản** lưu sai (`joinToString { }` chèn ", ") và hộp thoại ghi lại giá trị cũ. Dữ liệu cũ bị hỏng được làm sạch khi đọc.
- **Link mặc định** `OpenLinkPref`: nhánh dự phòng khác `default`.
- **TLS:** các cuộc gọi Gemini (mang khoá trong URL) dùng client `@SecureHttp` xác thực chứng chỉ. Client chung của feed vẫn tin mọi chứng chỉ (còn mở, xem dưới). Fever, Google Reader và WebDAV (nơi gửi mật khẩu) giờ cũng xác thực chứng chỉ theo mặc định (`credentialHttpClientFrom`, `credentialApiClient`, `WebDavBackupManager.clientFor`); công tắc "Tin chứng chỉ tự ký" theo từng máy chủ (host[:port]) nằm ở hộp thoại thêm tài khoản, trang kết nối tài khoản và trang WebDAV, lưu trong `SelfSignedTrust`. Máy chủ tự ký đã cấu hình trước bản này sẽ lỗi TLS cho tới khi bật công tắc. **Đã xem trên Pixel (2026-11-25, bản `2d490366`):** công tắc hiện đúng, tắt mặc định, không vỡ layout ở trang WebDAV (`~/exc-flaky-evidence/audit-20261125-pixel/tls/webdav.png`). **Hộp thoại thêm Fever/Google Reader và trang kết nối chưa xem được trên máy:** nút "Thêm tài khoản" bị comment trong `AccountsPage.kt` từ commit `71dde14b`, `ADD_ACCOUNTS` không có đường vào, nên người dùng không tạo được tài khoản Fever/Google Reader. Owner chọn giữ ẩn (2026-11-25); mã Fever/Google Reader, công tắc TLS và sửa id có `/` hiện chỉ được chứng minh bằng JVM, chưa ai dùng được.
- **Màn đọc:** tải lại mỗi lần quay về; Next bỏ qua cài đặt đồng bộ vị trí; gắn sao có thể đặt bài cũ lên màn bài mới; sơ đồ tư duy gọi Gemini lại; RSVP chạy ngầm sau Back.
- **Điều hướng:** dải loại trừ cử chỉ Back ở Reels còn nằm lại mọi màn (lỗi do tôi tạo ra); chạm thông báo Morning Radio không mở player; Triage cố định tài khoản 1; màn Điều khoản không bao giờ hiện cho người cài mới.
- **Dữ liệu Fever:** `REPLACE` xoá vị trí đọc và 5 bảng con khi server gửi lại bài (chứng minh trên SQLite thật); trường `unread_item_ids` hoặc `saved_item_ids` vắng làm mất mọi dấu sao; sync một lần không duy nhất.
- **RSS:** feed trả HTTP 404 hoặc 500 bị ghi là fetch khoẻ rỗng; feed ghim bài cũ lên đầu làm mất bài mới; một lô có hai link giống nhau chèn hai hàng; sync báo thành công khi mọi feed lỗi (nay `retry`).

Chưa sửa, đã xác nhận bằng đọc mã (cần quyết định hoặc rủi ro cao):
- **Client chung `trustAllCerts = true`** cho feed và ảnh (không mang mật khẩu; ba nơi gửi mật khẩu đã tách ra, xem mục TLS trên): người chặn giữa có thể chèn nội dung feed. Chưa đổi vì feed tự ký sẽ lỗi TLS; cần quyết định sản phẩm.
- **Sáu khoá Gemini nhúng trong APK** (XOR + hằng số cùng lớp): ghi sẵn là rủi ro chấp nhận, ai giải nén APK đều lấy được.
- Id bài của Google Reader/FreshRSS có dấu `/` làm route điều hướng lỗi (PLAUSIBLE, phụ thuộc server).

Mỗi mục trên là phát hiện của agent. Mục nào chưa ghi "đã sửa" thì tôi chưa tự chạy lại, nên chưa tính là đã chứng minh.

## 8. Kiểm trên máy thật Samsung S24 Ultra (2026-11-25, bản debug từ `50dade18`, chỉ Android + AdMob)

Máy: S24 Ultra SM-S928B, Android 17, user 0. Cài mới (app chưa có trên máy nên không có dữ liệu cũ để mất), cài lại một lần để thử "người cài mới". Bằng chứng: `~/exc-flaky-evidence/s24u-20261125/` (ảnh, log `roy93~Flow`, bản sao DB). 0 `FATAL EXCEPTION`, 0 `Use RequestConfiguration` từ app này (một dòng như thế trong logcat là của app `com.bookmark.money`, không phải của app này).

Đã chạy được trên máy:
- **Người cài mới thấy màn Chào mừng và Điều khoản**, bấm Đồng ý, mở lại thì không bị hỏi nữa (log `nav: screen route=startup` rồi `feeds`).
- **Reels:** thẻ có nút "Đọc bài viết"; nút mở được bài; vuốt từ cạnh trái mở được bài; Back về Reels đúng. Dải loại trừ cử chỉ Back tồn tại khi ở Reels (`mSystemGestureExclusion=(0,870,96,1470)`) và **biến mất sau khi rời Reels** (`SkRegion()`, log `edge-exclusion-cleared`).
- **Nền đồng bộ:** Settings, Privacy, Đồng bộ cục bộ, Hỏi kho lưu trữ, Feed Autopilot, AI, Brain RPG, Thống kê đọc, Sổ tay, Bản tin AI hằng ngày đều đo `#F0F1F1` (màu động của Samsung), trước đó lệch nhau ở bản Pixel.
- **Bản tin AI hằng ngày** có thanh trên và nút back; bấm nút back trên màn hình về đúng danh sách.
- **Thông báo Morning Radio:** gửi intent cùng cờ và extra (`NEW_TASK|CLEAR_TASK`, `extra_start_commute`) thì sheet CommuteCast tự mở và phát (log `commute: open-from-notification`). Đây là thông báo thật không được bật vì app chưa có quyền thông báo, nên tôi dùng intent tương đương, không phải bấm vào thông báo thật.
- **Triage:** mở với tài khoản 1, 115 bài; bấm đúp "Đọc ngay" không tạo hai màn chồng (một lần Back là về Triage).
- **Đồng bộ:** thêm hai nguồn tin, 103 rồi 142 bài, `feed_health_record` hai nguồn đều `lastErrorType = NONE`. Dán URL trả HTTP 500 vào ô đăng ký: app hiện lỗi cho người dùng, log `HttpError(statusCode=500)`.
- **Ngôn ngữ:** chọn tiếng Séc, giao diện đổi sang Séc (đã trả lại "theo thiết bị"). Phát hiện: chuỗi `version` còn tiếng Anh trong 22 locale. Nguyên nhân: đợt dịch đầu chỉ lấy chuỗi từ hai chữ trở lên. Đã dịch bù ~2.400 chuỗi một hay hai từ (dịch máy, chưa người duyệt).

Chưa kiểm được trên máy này:
- Fever, Google Reader (không có server).
- Quảng cáo thật: bản debug chỉ dùng đơn vị thử `ca-app-pub-3940256099942544/*`.
- Thông báo thật Morning Radio, TTS, âm thanh, widget, nút "Next article" và gắn sao có đua nhau ở máy chậm.
- API 26-28; lỗi "nút back không work" của owner vẫn chưa tái hiện.
- Tìm kiếm không phải đường đồng bộ: HTTP 500 ở đường đồng bộ được chứng minh bằng test JVM, không phải bằng máy.

### Sửa thêm sau mục 7 (2026-11-25)
- **Chuỗi đọc dùng ngày địa phương** (`StreakDay`), trước đây ngày UTC. Test nhiều múi giờ (Việt Nam UTC+7, Phoenix UTC-7), đột biến bắt được. Chuỗi đã lưu theo ngày UTC có thể lệch tối đa một ngày một lần sau khi cập nhật.
- **Dấu "đã hỏi cứu chuỗi hôm nay"** chỉ ghi khi người dùng đã trả lời (cứu thành công, bắt đầu lại, hoặc để sau). Cứu thất bại vì quảng cáo hỏng không ghi, để người dùng thử lại; app bị kill khi hộp thoại đang hiện thì hôm đó vẫn được hỏi lại.
- **Vòng phân trang Fever** dừng khi con trỏ `since_id` không tăng và sau tối đa 100 trang mỗi lần sync (5.000 bài; lần sau tiếp tục). `lastArticleId` vẫn chỉ ghi ở cuối sync, nên nếu app bị kill giữa chừng thì lần sau đọc lại từ mốc cũ, nhưng nay đọc lại không còn xoá dữ liệu con của bài.
- **Tìm kiếm ở danh sách bài** không còn bị xoá khi mở bài rồi quay lại (cờ tìm kiếm dùng `rememberSaveable`, khởi tạo từ truy vấn trong ViewModel).
- **Khôi phục vị trí đọc** đợi bài đọc xong trước khi cuộn, không còn cuộn vào ba hàng placeholder lúc đang tải.
- **TTS:** trạng thái giữ `PLAYING` giữa các đoạn của bài dài (trước đây thành `IDLE` ở mỗi đoạn); khi "Next" hết bài thì service dừng và thông báo ongoing biến mất; CommuteCast trả audio focus khi TTS lỗi.
- **Audit nút back bằng đọc mã (chưa tái hiện được lỗi của owner):** 61 nút `FeedbackIconButton`, chỉ một nút back không có hành động là bản xem trước trong `FeedsPagePreview` (chủ ý). `FeedsPage` có `BackHandler(true)` luôn bật và gọi `moveTaskToBack`, nên Back ở màn chính đưa app xuống nền thay vì thoát (hợp lệ). Chuỗi `BackHandler` của màn đọc bao đủ chín cờ sheet. Không tìm thấy bẫy Back trong mã.

### Lỗi điều hướng tái hiện được trên Pixel (2026-11-25), có thể là "nút back/điều hướng không work"
- **Triệu chứng:** sau khi cài mới và bấm Đồng ý, bài đầu tiên mở ra rồi **0,4 giây sau tự nhảy về màn Feeds** (log `reading/{articleId}` rồi `feeds` không có thao tác nào). Lỗi chỉ ở phiên đầu sau khi cài mới, và dường như hết ở lần mở bài sau, nên rất dễ bị bỏ sót.
- **Nguyên nhân:** `NavHost(startDestination = if (context.isFirstLaunch) STARTUP else FEEDS)` được tính lại ở mỗi lần recompose. Sau khi người dùng bấm Đồng ý, cờ `isFirstLaunch` thành `false`, điểm bắt đầu đổi từ STARTUP sang FEEDS, `NavHost` dựng lại đồ thị và đặt lại back stack, nên màn đang mở bị đưa về Feeds. Màn Điều khoản tôi mở lại ở lượt trước (`FirstLaunchPolicy`) là thứ đã làm lỗi này lộ ra: trước đó cờ luôn `false` nên không bao giờ đổi.
- **Sửa:** `val startDestination = remember { ... }`, chọn một lần. Trên Pixel sau sửa: `startup`, `feeds`, `flow`, `reading` và ở lại màn đọc. Test `NavigationFixesSourceTest` bắt được khi đưa lại cách cũ.
- **Giới hạn:** tôi chưa chứng minh đây đúng là lỗi owner gặp. Nó khớp "điều hướng không work" với người dùng mới, nhưng owner có thể đã gặp lỗi khác.

### Tìm kiếm trong danh sách bài (Pixel, 2026-11-25)
- Tìm "Lib", mở một bài kết quả, bấm Back: ô tìm kiếm bị đóng và xoá truy vấn, vì click bài đặt `onSearch = false` trước khi điều hướng và effect xoá truy vấn khi cờ này tắt. Owner chọn giữ truy vấn và ô tìm kiếm; nay chỉ nút X của ô và Back khi ô đang mở mới đóng nó.
- Trong lúc kiểm tra, quay lại từ bài làm `focusRequester.requestFocus()` ném `IllegalStateException` (hộp tìm kiếm không còn được compose vì cuộn ra ngoài); bao `try/catch`. Trên Pixel: 0 lỗi `FocusRequester`, 0 crash, ô còn "Man" và kết quả còn nguyên sau khi mở bài rồi Back.

## 9. Kiểm trên Pixel 7 Pro (2026-11-25 tối, bản debug từ `d274272c`)

Máy: Pixel `2B051FDH3006MU`, Android 17, Wi-Fi bật, id quảng cáo thử của Google. Bằng chứng: `~/exc-flaky-evidence/audit-20261125-pixel/` (ảnh và log `roy93~Flow`). 0 `FATAL EXCEPTION`, 0 `Use RequestConfiguration` (chỉ thấy quảng cáo thử "Nice job!" và "Flood-It!" có nhãn "Quảng cáo thử nghiệm").

Đã chạy được trên máy:
- **Người cài mới** thấy Chào mừng và Điều khoản, bấm Đồng ý vào Feeds. Sau Đồng ý, bài đầu tiên mở ra **không còn nhảy về Feeds** (lỗi `startDestination` ở mục trên, đã tái hiện trước sửa và hết sau sửa).
- **Tìm kiếm:** mở bài kết quả rồi Back thì ô tìm kiếm còn nguyên truy vấn và kết quả. Một `IllegalStateException` của `FocusRequester` lộ ra khi quay lại, đã bao `try/catch` và kiểm lại, 0 lỗi.
- **Triage:** "Lưu lại" ghi `triage: save`; bấm đúp "Đọc ngay" chỉ ghi một `read-now` và mở một màn đọc, một lần Back là về Triage (đếm `1/136` rồi `2/136`).
- **Dịch tiếng Séc:** hàng "Version" của Settings nay là "Verze" (trước chỉ có tiếng Anh), các hàng mới dịch ("Zeptat se mého archivu", "Autopilot kanálů", "Zdrojový kód (tato verze)", "Připojte se ještě dnes ke komunitě BetaTesting!") hiển thị đúng. Đã trả ngôn ngữ về "theo thiết bị".
- **Chọn ngôn ngữ** đổi giao diện ngay sau khi bấm Có, quay lại Settings đúng.

Không kiểm được trên máy này, hoặc kiểm một phần:
- Bàn phím Telex tiếng Việt biến "Energy" thành "Ểngy", làm một lần tìm kiếm của tôi sai; đây là hành vi của bàn phím, không phải của app.
- Chưa mở Reels trên Pixel ở đợt này (đã chạy được ở S24U), chưa nghe TTS, chưa thử Fever, Google Reader và thông báo thật.
- Dịch tiếng Đức xem trên Pixel 2026-11-25 (`~/exc-flaky-evidence/audit-20261125-pixel/locale/`): giao diện đổi sang Đức, công tắc TLS và WebDAV hiện đúng tiếng Đức, không vỡ layout. **Nhưng 14 locale vẫn còn nhiều chuỗi y hệt tiếng Anh** (đếm chuỗi từ hai chữ trở lên giống bản gốc, tổng 6.603): uk 552, pl 537, it 533, in 531, ar 521, pt-BR 518, pt 518, ru 492, es 478, zh-TW 450, de 371, ja 369, fr 368, zh-CN 365. Trên máy thấy rõ ở tiếng Đức: "Rate app", "Get more apps", "Share this app" cùng mô tả của chúng. **Đã dịch 6.508 chuỗi cho 14 locale này (2026-11-25, dịch máy, chưa người duyệt; chuỗi nghi ngờ ở `i18n-machine-translation-doubts.md`, mục Đợt 2). Xem lại trên Pixel sau đợt dịch: tiếng Đức hiện "App bewerten", "Weitere Apps entdecken", "Alle"; tiếng Ả Rập đảo chiều RTL đúng. **Phát hiện một lỗi bố cục khi xem tiếng Ả Rập:** dòng phụ của thẻ "الكل" ở màn Feeds bị cắt đáy do `Banner` đặt cố định `height(88.dp)`; đã đổi thành chiều cao tối thiểu (`96f8efe5`), ảnh trước/sau ở `~/exc-flaky-evidence/audit-20261125-pixel/locale2/` (`ar_card_crop.png`, `ar_card_after.png`). Đợt 3 (`16856f04`) dịch thêm 1.199 chuỗi một chữ (`version`, `retry`...). Các locale khác (uk, pl, it, in, pt, ru, es, zh, ja, fr) chưa xem trên máy.**
- **TTS và đổi ngôn ngữ, xem trên Pixel 2026-11-26 (`~/exc-flaky-evidence/audit-20261125-pixel/tts/`):** (1) Bấm ▶ trên bài: `togglePlayAudio`, `TtsManager play`, dịch vụ nền khởi động, media session hoạt động, giọng đọc xong (`onDone`). Nhưng sau khi đọc xong một bài, dịch vụ và thông báo ongoing không gạt được vẫn còn hơn một phút (`service_stays_after_done.txt`). Đã sửa: dịch vụ tự dừng khi đọc xong mà không có hàng đợi tin. **Chưa nghe được âm thanh qua adb**, chỉ thấy trạng thái, log và `audio_hw_aoc_route: speaker 1`. (2) Đổi ngôn ngữ tại chỗ **không khởi động lại process** (pid 3631 trước và sau; `triggerRebirth` chạy trong `scope.launch` sau `delay(300)` nên bị huỷ khi activity tạo lại), nên nhãn ngày "اليوم" cũ còn lại khi chuyển sang English. Đã sửa: gọi `triggerRebirth(context.applicationContext)` ngay sau khi ghi locale; trên Pixel thấy tiến trình `:phoenix` rồi process mới và nhãn đổi đúng ("Gestern" khi đổi sang Đức). **TTS sau sửa (bản `59f60417`, Pixel, 2026-11-26):** `onStart` 00:04:45 rồi `onDone` 00:04:53; 35 ms sau, media session `TtsForegroundService` bị gỡ (`removeSession`), `dumpsys activity services` và `media_session` đều 0, nút trở lại ▶, 0 crash. Các bản ghi `tts_playback_channel` còn trong `dumpsys notification` chỉ ở `mArchive` (lịch sử đã gỡ).
- Dịch tiếng Đức và các locale khác ngoài Séc chưa xem trên máy.
- Dữ liệu 1585 bài ban đầu của Pixel đã mất theo lựa chọn của owner ngày 2026-11-24; hiện có 137 bài BBC.

