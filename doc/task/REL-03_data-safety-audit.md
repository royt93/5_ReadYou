# [REL-03] Danh sách khai báo Data safety từ mã thật (bỏ ad, IAP, consent, VIP theo lời owner)

- **Ngày:** 2026-11-21, nhánh `dev`, HEAD `d578c5d6`. **Cách làm:** đọc mã và bản APK release gần nhất; không chạy mạng, không kiểm Play Console.
- **Phạm vi bỏ qua (owner sẽ làm ở phase migrate ad SDK):** quyền và dữ liệu của SDK quảng cáo (`AD_ID`, `ACCESS_ADSERVICES_*`, AppLovin, AdMob, UMP/consent), IAP, VIP.
- **Cảnh báo:** đây là **bản nháp để owner đối chiếu** với biểu mẫu Play Console; tôi không biết nội dung biểu mẫu đã nộp trước đó. Chỗ ghi "chưa kiểm" cần owner xác nhận.

## 1. Quyền khai báo (manifest, đã gộp trong APK release)

| Quyền | Dùng để | Ghi chú |
|---|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | tải nguồn RSS, Gemini, Bluesky, Nostr, WebDAV, cập nhật | |
| `ACCESS_LOCAL_NETWORK` (API 37) | ghép đôi và đồng bộ P2P trong LAN (`_rsscathub._tcp`) | cần mô tả lý do trong biểu mẫu quyền |
| `POST_NOTIFICATIONS` | cảnh báo từ khóa, nhắc kèo Oracle, bản tin | có rào API 33 |
| `WAKE_LOCK`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | phát CommuteCast, đọc to | 3 dịch vụ `mediaPlayback` |
| `BLUETOOTH_CONNECT` | tự phát khi nối tai nghe (STRAT-01) | |

## 2. Dữ liệu rời máy (đã đọc mã)

| Điểm đến | Gửi gì | Khi nào | Bằng chứng |
|---|---|---|---|
| **Google Gemini** (`generativelanguage.googleapis.com`) | tóm tắt/Deep Read/Mind Map: nội dung bài (đã cắt); quiz: **chỉ tiêu đề + 250 ký tự mô tả**; digest: tiêu đề + tối đa **2.000 ký tự** mỗi bài; CommuteCast: kịch bản; **khóa API nằm trong query `?key=`** | khi người dùng chạy tính năng AI (và nền, cho digest/quiz) | `AiRequestGateway.kt:196`, `DailyDigestService.kt:23,38`, `GeminiSummaryService.kt`, `CommuteScriptService.kt:102` |
| **Nostr relay** `wss://nos.lol`, `wss://relay.primal.net` | khóa công khai (npub) của tác giả đang theo dõi, yêu cầu `REQ` | khi thêm hoặc đồng bộ nguồn Nostr | `NostrRelayClient.kt:121` |
| **Bluesky** `public.api.bsky.app` | handle hoặc DID đang theo dõi | khi thêm hoặc đồng bộ nguồn Bluesky | `BlueskyApiClient.kt:136` |
| ~~**GitHub** `api.github.com`~~ | ~~kiểm tra bản mới (`releases/latest`)~~ | **không còn gọi từ 2026-11-23**: `UPDATE_CHECK_ENABLED = false` (`ExtFlavor.kt`), `AppSv.checkUpdate` trả `null` trước khi tạo request; kiểm bằng `AppSvUpdateCheckDisabledTest` (không gọi `getReleaseLatest`). Vẫn còn đường mở trình duyệt tới github.com khi người dùng bấm liên kết (không phải yêu cầu của app) | `AppSv.kt`, `RApp.kt` |
| **WebDAV do người dùng nhập** | cả OPML (nguồn và nhóm) và Basic auth (mật khẩu) | khi người dùng bấm Sao lưu/Khôi phục | `WebDavBackupManager.kt` |
| **Máy chủ RSS, FreshRSS, Miniflux, Nextcloud do người dùng nhập** | tên đăng nhập và mật khẩu (Google Reader API), yêu cầu đọc | khi đồng bộ | `GoogleReaderApi.kt` |
| **DNS-over-HTTPS** (Cloudflare `1.1.1.1`, AdGuard `94.140.14.14`) | tên miền các nguồn đang tải | **chỉ khi người dùng chọn**, mặc định "System" | `DohProviderPref.kt` |
| **Đồng bộ P2P LAN** | OPML và trạng thái bài (mã hóa đầu-cuối bằng khóa ghép đôi) | giữa hai máy trong cùng mạng | EXC-05 |
| **Google favicon** `www.google.com/s2/favicons?domain=<host>` | **tên miền** của từng nguồn RSS vừa thêm (không phải bài đọc); app chỉ lưu URL này vào Room, ảnh được tải khi hiển thị biểu tượng nguồn | khi đồng bộ lần đầu một nguồn chưa có biểu tượng (`feed.icon == null`; không áp dụng cho nguồn Nostr, Bluesky) | `RssHelper.queryRssIcon` (`RssHelper.kt:334`), gọi từ `AbstractRssRepository.syncSingleFeed` (dòng 196). **Mới ghi 2026-11-23, trước đó chưa có trong bảng này** |
| **GitHub raw** `raw.githubusercontent.com/ReadYouApp/ReadYou/main/fastlane/...` (6 ảnh chụp màn hình của dự án upstream) | yêu cầu HTTP tải ảnh (địa chỉ IP, không có dữ liệu người dùng) | mỗi lần người dùng mở màn hình **Giới thiệu** (`AboutActivity`, WebView `loadDataWithBaseURL`, JavaScript tắt) | `AboutActivity.kt:154-167`. **Mới ghi 2026-11-23.** Trỏ vào repo upstream, nếu upstream đổi hoặc xóa ảnh thì màn Giới thiệu hiện ảnh vỡ; không chặn phát hành |
| **Máy chủ tiện ích** (`img.youtube.com`, `images.unsplash.com`, ảnh bìa bài) | yêu cầu tải ảnh, tải ảnh từ nguồn RSS | khi xem bài | `ImageLoaderModule.kt` |

**Không thấy** trong mã: SDK phân tích (Firebase, Crashlytics, Sentry, Mixpanel, Amplitude...), và mọi điểm gửi tới OpenAI, DeepSeek hay Groq (grep `api.openai.com`, `api.deepseek.com`, `groq`, `chat/completions` ngoài màn hình cài đặt: 0 kết quả).

**Màn hình AI lệch với thực tế (cần quyết, ảnh hưởng khai báo):** `AiSettingsPage.kt:223` cho chọn 4 nhà cung cấp (Gemini, OpenAI, DeepSeek, Groq) và lưu lựa chọn vào `DataStoreKeys.AiProvider`, nhưng **không có mã nào đọc `aiProvider` ngoài chính màn hình đó**, và nút Lưu (dòng 294 đến 295) chỉ ghi **`CustomGeminiApiKey`**, bất kể chip nào đang chọn. `resolveApiKeys()` (`GeminiSummaryService.kt:70`) chỉ dùng khóa Gemini. Hệ quả: người dùng chọn OpenAI, dán khóa OpenAI, bấm Lưu thì **khóa OpenAI được lưu vào ô khóa Gemini**, rồi được gửi tới `generativelanguage.googleapis.com` kèm `?key=`, tức là **gửi một khóa OpenAI cho Google**. Nội dung bài vẫn đi tới Gemini bằng các khóa chung. Chưa thử trên máy; đọc mã. **Đã giảm nhẹ 2026-11-21:** ba chip OpenAI, DeepSeek, Groq bị làm mờ và không bấm được, và lựa chọn lưu bởi bản cũ hiển thị là Gemini (xem `doc/feature.md` mục REL-03 và ticket `todo/AI-01_byok-other-providers.md`); việc nối thật được defer.

## 3. Dữ liệu lưu trên máy và sao lưu

| Dữ liệu | Nơi lưu | Mã hóa | Sao lưu đám mây (`allowBackup=true`) |
|---|---|---|---|
| Bài viết, nguồn, nhóm, ví Oracle, trạng thái RPG | Room `Reader` | không | **có** |
| Cài đặt và **khóa API riêng của người dùng (Gemini; ô OpenAI/DeepSeek/Groq tồn tại trong DataStore nhưng không được UI ghi, xem trên)** | DataStore `settings.preferences_pb` | **không** | **có** (M3a, giữ nguyên theo quyết định) |
| Mật khẩu WebDAV | `webdav_backup_prefs.xml` | **không** (chữ thường) | **không** (đã loại ở `88c6afe5`) |
| Khóa ghép đôi P2P | `pairing_store.xml` | có (Keystore) | **không** (đã loại từ EXC-05b) |
| Tập CommuteCast, vị trí podcast | `commute_cast_prefs`, `podcast_positions` | không | có |

## 4. Điểm bảo mật cần khai trung thực hoặc sửa

| # | Điểm | Mức | Trạng thái |
|---|---|---|---|
| 1 | **HTTPS không kiểm chứng chứng chỉ** (trust-all mặc định) | High | **SEC-01, deferred** theo owner; Play có thể cảnh báo `TrustManager`/`HostnameVerifier` |
| 2 | `usesCleartextTraffic="true"` (cho phép HTTP) | Low | giữ (nhiều nguồn RSS chỉ có HTTP) |
| 3 | Khóa API riêng nằm trong DataStore, được sao lưu đám mây, không mã hóa | Medium | giữ; **khai báo** trong Data safety |
| 4 | Khóa Gemini nằm trong URL (`?key=`), có thể xuất hiện trong nhật ký proxy | Low | chuẩn của API Gemini |
| 5 | 6 khóa Gemini chung nhúng trong APK chỉ XOR+Base64 (FIX-09) | đã quyết giữ nguyên | owner chọn 2026-11-14 |
| 6 | Khóa ký app public trên GitHub | **Critical** | chờ owner kiểm Play Console (Play App Signing); ngoài phạm vi tài liệu này |

## 5. Gợi ý trả lời biểu mẫu Data safety (cần owner xác nhận)

- **Dữ liệu thu thập và chia sẻ:** nội dung bài do người dùng chọn chạy AI gửi tới Google (Gemini). Tên đăng nhập và mật khẩu WebDAV hoặc Google Reader gửi tới **máy chủ người dùng tự nhập**, không tới nhà phát triển.
- **Nhà phát triển không có máy chủ:** không thu thập dữ liệu về máy chủ riêng; không có phân tích; không có đăng nhập tài khoản.
- **Mã hóa khi truyền:** **không khẳng định "tất cả dữ liệu được mã hóa khi truyền"** chừng nào SEC-01 chưa sửa (trust-all) và `usesCleartextTraffic="true"`.
- **Xóa dữ liệu:** dữ liệu nằm cục bộ; gỡ app xóa; sao lưu đám mây theo cơ chế Android.
- **Quyền `ACCESS_LOCAL_NETWORK`:** nêu lý do đồng bộ P2P trong LAN, không quét thiết bị ngoài.
- **Phần ad (AD_ID, ACCESS_ADSERVICES, UMP):** xem mục 7 (đã đo trên APK sau migrate, 2026-11-23); biểu mẫu do owner điền.

## 6. Chưa kiểm
- Biểu mẫu Data safety hiện đang khai trên Play Console (tôi không có quyền truy cập).
- Các chuỗi mô tả quyền và chính sách quyền riêng tư (`PRIVACY_POLICY_URL` trỏ tới Notion): chưa đọc nội dung trang.
- Nhật ký mạng thật: chưa bắt gói tin, chỉ đọc mã; có thể có thư viện gửi thêm.
- Dữ liệu SDK quảng cáo: bỏ theo lời owner.

## 7. Dữ liệu và quyền của SDK quảng cáo 1.8.5 (đo 2026-11-23 trên APK sau migrate)

**Quyền thêm hoặc đổi so với trước migrate** (so `apk_facts.txt` của `baseline-pre-adsdk-e890c8cb-debugsigned-fixed` và `post-adsdk-2`): thêm `android.permission.VIBRATE` và `com.android.vending.CHECK_LICENSE`; bỏ `com.applovin.array.apphub.permission.BIND_APPHUB_SERVICE`. Đã có từ trước: `com.google.android.gms.permission.AD_ID`, `ACCESS_ADSERVICES_AD_ID`, `ACCESS_ADSERVICES_ATTRIBUTION`, `ACCESS_ADSERVICES_TOPICS`, `BIND_GET_INSTALL_REFERRER_SERVICE`.

| Dữ liệu / dịch vụ | Gửi đi đâu | Khi nào | Nguồn |
|---|---|---|---|
| Mã quảng cáo (Advertising ID) và dữ liệu thiết bị/quảng cáo | Google (AdMob), AppLovin (nếu mediation) | khi có yêu cầu quảng cáo; chỉ sau khi UMP cho phép `canRequestAds` | SDK, log `consent canRequestAds=true`, `resolveGaid ok` |
| Chuỗi đồng ý (UMP / TCF, GPP cho Hoa Kỳ) | Google, đối tác quảng cáo | EEA, Anh, Thuỵ Sĩ, một số bang Mỹ | `requestConsentInfoUpdate` |
| Install Referrer (mốc cài) | Google Play, **cục bộ** để cấp trial 1 ngày | lần chạy đầu của bản release | SDK Step 9 |
| Số liệu hiển thị quảng cáo (doanh thu, impression) | Google, AppLovin | khi có quảng cáo | `paidEventListener`; app **chỉ ghi log ở debug**, không gửi đi đâu |
| App Set ID | AppLovin (có thể chạy trước consent, `AppLovinInitProvider`, accepted risk E-8 của SDK) | lúc khởi động | SDK `CLAUDE.md` |

- **Không** có nhà phát triển thu thập: không có Firebase, không analytics riêng; `analyticsReporter` của SDK để trống.
- Chuỗi VIP: mã VIP người dùng nhập được kiểm **cục bộ** (ECDSA), không gửi lên máy chủ nào; nhưng SDK **yêu cầu có mạng** lúc nhập.
- **Không khẳng định "mã hoá khi truyền cho mọi dữ liệu"** (SEC-01 còn mở, `usesCleartextTraffic="true"`).
- **Chưa kiểm:** tôi không có bản chụp biểu mẫu Data safety hiện tại; SDK có thể gửi thêm thứ không thấy trong mã (không bắt gói tin; A50s tắt ad vì whitelist VIP nên cũng không quan sát được lưu lượng quảng cáo).
