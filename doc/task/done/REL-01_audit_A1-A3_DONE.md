# REL-01 audit A1 đến A3 (chỉ đọc, chưa sửa mã)

**Ngày:** 2026-11-21, nhánh `dev`. **Phạm vi:** cấu hình release, migration Room, crash tiềm ẩn. **Không** gồm A4 (chính sách store) và A5 (lint), và không gồm phần ad (owner đang migrate).
**Cách đọc:** mỗi mục ghi mức độ và bằng chứng (tệp, lệnh đã chạy). "Chưa chứng minh" nghĩa là tôi thấy dấu hiệu nhưng không có bằng chứng lỗi thật.

## Tóm tắt

| Mức | Số | Việc |
|---|---|---|
| Critical | 0 | Không thấy lỗi chặn phát hành trong phạm vi này |
| High | 1 | R8: hai lỗi thật ở bản release (Retrofit, Gson), **đã sửa bằng keep rule và chứng minh trên Pixel** (xem H1 và `doc/feature.md`) |
| Medium | 3 | Migration 1→2 đến 5→6 không có test và không có snapshot (M1); Gson dựa vào lớp không được keep (M2); sao lưu đám mây mang khóa API và mật khẩu WebDAV chữ thường (M3) |
| Low | 2 | `!!` còn lại (L1), `usesCleartextTraffic="true"` (L2) |

## A1 Cấu hình release

**Đã khớp:**
- `resourceConfigurations` (38 mục kể cả `en`) khớp đúng 37 thư mục `values-*` ngôn ngữ trong `app/src/main/res`, không thiếu mục nào (so bằng script, kết quả `not in resourceConfigurations: []`).
- Quyền đã gộp trong APK release gần nhất (`aapt2 dump permissions`): `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `ACCESS_LOCAL_NETWORK`, `POST_NOTIFICATIONS`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `BLUETOOTH_CONNECT`, `com.google.android.gms.permission.AD_ID`, cộng các quyền SDK quảng cáo (`ACCESS_ADSERVICES_*`, AppLovin). Ba dịch vụ `mediaPlayback` đều khai `foregroundServiceType="mediaPlayback"` (manifest dòng 138, 143, 148).
- `debuggable false`, `minifyEnabled true`, `shrinkResources true`, ký bằng `signingConfigs.release` (`app/build.gradle` dòng 116 đến 120).
- `versionCode 20261122` / `versionName "2026.11.22"` (commit `0181b282` của owner, hôm nay). **Chưa biết** số bản trên store: cần owner xác nhận lớn hơn bản đang xuất bản (xem REL-01).

### H1 (High) R8: `-dontobfuscate` và keep rule lỗi thời
- `app/proguard-rules.pro` dòng 23 `-dontobfuscate`: bản release **không đổi tên** lớp. Đây có chủ ý (giảm lỗi phản chiếu), không phải lỗi, nhưng nghĩa là bản release dễ đọc ngược; ghi nhận vào hồ sơ rủi ro, **không sửa**.
- Quy tắc `-keep class me.ash.reader.infrastructure.** { *; }` (dòng 337 trong `configuration.txt`) và `-keep class com.mckimquyen.reader.data.provider.** / data.model.**`: **gói `me.ash.reader` không còn** (chỉ còn một khai báo `package me.ash.reader.ui.component.base` ở `Base64Image.kt:1`), và thư mục `com/mckimquyen/reader/data` **không tồn tại**. Các quy tắc đó không giữ gì, **nhưng** gói `infrastructure` thật (`com.mckimquyen.reader.infrastructure`) thì không được keep.
- **Hệ quả đã kiểm:** `usage.txt` của bản release (R8 gỡ gì) cho thấy `WebDavPositionRecord` và `DailyDigestContent` chỉ bị gỡ các hàm sinh `component*`/`copy*` (hợp lệ), không gỡ trường; `EpisodeDto` không có dòng nào trong `usage.txt`. **Chưa chứng minh** lỗi Gson ở release.
- **Khuyến nghị:** bản release phải được chạy thật trên Pixel (giai đoạn D6): mở digest, tập CommuteCast đã lưu, đồng bộ WebDAV, import `RssSourcesData`. Đây là phép thử duy nhất bắt được lỗi này.

## A2 Dữ liệu người dùng khi update

- Room `version = 22`, chuỗi `MIGRATION_1_2` đến `MIGRATION_21_22` đủ liên tiếp (`AndroidDb.kt` dòng 159 đến 179), **không** có `fallbackToDestructiveMigration` (grep 0). Nếu thiếu đường nâng cấp thì app crash lúc mở DB chứ không xóa dữ liệu âm thầm.
- **Sửa lại một điều tôi nói trước đó:** migration 12→13 và 13→14 **có** test, nằm trong `androidTest` (không phải `test`): `BrainRpgMigration13Test` (5 test, dùng `MIGRATION_12_13`) và `WebFeedMigration14Test` (2 test, dùng `MIGRATION_13_14`). Tôi chỉ tìm ở `app/src/test` lúc đầu nên kết luận "thiếu" là sai.
- Còn lại: **không có test nào** cho 1→2, 2→3, 3→4, 4→5, 5→6 (grep 0 tệp cho từng bước). Snapshot schema chỉ có từ `5.json` đến `22.json`.

### M1 (Medium) Migration cũ 1→6 không có test và không có snapshot
- Bằng chứng: grep `MIGRATION_${a}_${b}` trong `app/src/test` và `app/src/androidTest` cho 0 tệp với a = 1 đến 5; `ls app/schemas/*/` bắt đầu từ `5.json`.
- Rủi ro thực: chỉ người dùng đang ở v1 đến v5 mới đi qua, tức là bản cài trước 2024-04 (snapshot v5 vào repo ngày 2024-04-24). Ít khả năng còn nhiều người, nhưng không có bằng chứng.
- **Khuyến nghị:** không viết test cho đường cũ nếu số liệu store cho thấy không còn người dùng ở phiên bản đó (owner có số liệu). Nếu muốn an toàn thì dựng test dựa vào mã SQL trong `MIGRATION_1_2` đến `MIGRATION_5_6`, chỉ kiểm cú pháp chạy được.
- **Cũng chưa kiểm:** cập nhật **thật** từ bản đang trên store. Cần APK/AAB cũ cùng chữ ký (xem REL-01 mục "Cần từ owner").

## A3 Crash tiềm ẩn (R5)

- **Listener:** đăng ký 7, gỡ 7 (`registerListener`, `addObserver` v.v. so với `unregister*`/`removeObserver`); `registerReceiver(`/`unregisterReceiver(` đều 0 (không dùng receiver động). Không thấy rò rỉ theo số đếm. **Giới hạn:** đếm số dòng, không chứng minh từng đường gọi đều gỡ.
- **Gson:** 5 lớp được dùng với `Gson`, trong đó `EpisodeDto`, `WebDavPositionRecord`, `DailyDigestContent` ở `infrastructure` (không có `-keep`), `ArticleHighlights` và `RssSourcesData` ở `domain.model` (được keep bởi `-keep class com.mckimquyen.reader.domain.model.**`).

### M2 (Medium) Gson trên lớp `infrastructure` không được keep
- `CommuteEpisodeStore.kt:105` `gson.fromJson(json, EpisodeDto::class.java)`, `WebDavReadPositionSyncer.kt:129` `TypeToken<List<WebDavPositionRecord>>`, `DailyDigestService.kt:26` `Gson().toJson(request)` và `DigestViewModel.kt:82` `fromJson(..., DailyDigestContent::class.java)`.
- Vì `-dontobfuscate` nên tên trường **không bị đổi** và Gson vẫn khớp theo tên; rủi ro còn lại là R8 gỡ trường không dùng hoặc constructor. Với `data class` Kotlin, trường được ghi qua constructor nên rủi ro thấp.
- **Chưa chứng minh lỗi.** Phép thử duy nhất: chạy bản **release** trên máy (D6).
- **Khuyến nghị:** thêm `-keep class com.mckimquyen.reader.infrastructure.audio.EpisodeDto { *; }` v.v. chỉ sau khi D6 cho thấy lỗi, không thêm trước (tránh phình keep rule).

### L1 (Low) `!!` còn lại trong mã sản xuất
14 vị trí, đã đọc từng chỗ:
- `OPMLDataSource.kt:51,62` `it.text!!` khi nhập OPML thiếu cả `title` lẫn `text`: ném `NullPointerException`. Gọi trong luồng nhập OPML của người dùng; tệp OPML hỏng có thể làm hỏng cả lần nhập. **Có thể sửa gọn** (`?: skip`), có test.
- `RssHelper.kt:54` `syndFeed.title!!`: cả hai chỗ gọi `searchFeed` đều có bảo vệ (`SubscribeViewModel.kt:327,332,337` dùng `runCatching`, `AddSourcesViewModel.kt:186` dùng `try`), nên chỉ làm mất nguồn đó, **không crash app**.
- `WebView.kt:79`, `ExtContext.kt:125,137` (`getDefaultBrowserInfo()!!`), `AddSourceDetailPage.kt:243,247`, `AccountDetailsPage.kt:92`, `AccountViewModel.kt:92,111`, `TipsAndSupportPage.kt:344,345,353`: phần lớn là giá trị đã kiểm trước đó trong cùng khối; **chưa chứng minh** chỗ nào null được trong thực tế.
- **Khuyến nghị:** sửa `OPMLDataSource.kt:51,62` (thật sự có đường null); các chỗ còn lại chỉ sửa nếu owner muốn cứng hơn (R5 yêu cầu comment lý do hoặc bỏ `!!`).

### L2 (Low) `usesCleartextTraffic="true"` (manifest dòng 42)
Cho phép HTTP không mã hóa. **Có chủ ý khả dĩ** (nhiều nguồn RSS cũ chỉ có HTTP; relay Nostr nội bộ test dùng `ws://`), nhưng là điểm cần ghi vào hồ sơ Data safety/bảo mật. **Không đổi** nếu không có quyết định riêng: tắt sẽ làm hỏng các nguồn HTTP.

### M3 (Medium) Sao lưu đám mây mang theo khóa API và mật khẩu của người dùng
Đã đọc `res/xml/data_extraction_rules.xml` và `backup_rules.xml`: **chỉ** `sharedpref/pairing_store.xml` bị loại khỏi `cloud-backup`, `device-transfer` và `full-backup-content`. Mọi thứ khác theo `allowBackup="true"` (manifest dòng 33) đều được sao lưu, gồm:
- **DataStore** `files/datastore/settings.preferences_pb`: chứa `CustomGeminiApiKey`, `CustomOpenAiApiKey`, `CustomDeepSeekApiKey` (khóa riêng của người dùng, `ui/ext/ExtDataStore.kt:56,59,62`) cùng mọi cài đặt.
- **`webdav_backup_prefs.xml`**: **mật khẩu WebDAV ở dạng chữ thường** (`WebDavBackupManager.kt:52` `.putString(KEY_PASSWORD, password)`, tệp `webdav_backup_prefs`, dòng 132). Không dùng Keystore hay mã hóa.
- Cả Room (bài báo, nguồn, ví Oracle) và các `commute_cast_prefs`, `podcast_positions`, `vip_screen_prefs`.
- **Hệ quả:** khóa API và mật khẩu WebDAV rời máy lên Google Drive của người dùng (theo cơ chế sao lưu của Android), và có thể khôi phục sang máy khác. Khôi phục lên máy khác là hành vi tiện lợi, nhưng với mật khẩu WebDAV chữ thường là rủi ro riêng tư mà Data safety phải khai báo trung thực.
- **Chưa chứng minh** có sự cố thực tế. Không đổi trong REL-01 nếu không có quyết định riêng vì: loại DataStore khỏi sao lưu sẽ làm người dùng mất toàn bộ cài đặt khi đổi máy (đổi hành vi). Hai hướng: (a) ghi trung thực vào Data safety và giữ nguyên; (b) loại `webdav_backup_prefs.xml` khỏi sao lưu (tối thiểu, ít hại) và mã hóa bằng Keystore ở bản sau.

### L3 (Low) `allowBackup="true"`
Gộp vào M3 ở trên.

## Việc tiếp theo đề xuất

1. Owner quyết hướng M3 (khai báo trung thực hoặc loại `webdav_backup_prefs.xml` khỏi sao lưu).
2. Sửa `OPMLDataSource.kt:51,62` (L1) bằng TDD (test OPML thiếu cả `title` và `text` không ném lỗi).
3. Chạy bản release trên Pixel (D6) để kiểm M2 và H1; chỉ thêm keep rule nếu lỗi thật xuất hiện.
4. Chờ owner: số bản trên store, APK/AAB cũ (M1 và test cập nhật từ bản cũ).
