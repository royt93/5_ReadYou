# [REL-02] Kế hoạch thực thi phát hành bản update (thay thế phần "việc còn lại" của REL-01)

- **Type:** Release
- **Priority:** `P0 (Blocker)`
- **Ngày lập:** 2026-11-21, nhánh `dev` (một nhánh duy nhất, không nhánh phụ). HEAD khi lập: `42b4b35b`. **Cập nhật 2026-11-22**, HEAD `44686937`.
- **Thiết bị kiểm:** từ 2026-11-22 chỉ **Tecno KJ7** `115333744A005844` (Android 14, SDK 34), theo lời owner "dùng tecno cho mọi phiên"; không gửi lệnh tới Pixel. Các mục kiểm trên Pixel (Android 17) ở bản trước giữ nguyên làm bằng chứng lịch sử. Tecno **dùng chung** với một phiên Claude khác (LensLauncher): mỗi lệnh ghi phải gate và đếm `ActiveInstrumentation` ngay trước.
- **Quan hệ với REL-01:** REL-01 (`REL-01_release-update-plan.md`) là kế hoạch gốc; audit A1 đến A3 xong (`done/REL-01_audit_A1-A3_DONE.md`). REL-02 chỉ giữ phần **chưa xong**, theo thứ tự thực hiện.

## 0. Tóm tắt cho owner (2026-11-23, HEAD `ee975df2` + doc; chi tiết ở các mục dưới và `doc/feature.md`)

**Mã trên `dev` đã sẵn sàng về phía tôi.** Việc **chặn phát hành** và việc chỉ bạn làm được:
1. **Khóa ký app public trên GitHub** (Critical): kiểm Play Console có Play App Signing không rồi chọn hướng (`~/exc-flaky-evidence/REL-04_release_pack_NOT_FOR_GIT.md` mục 5). Tôi không làm gì khi chưa có lời bạn.
2. **Migrate ad SDK** xong và vào `dev`; sau đó tôi chạy cổng cuối (full JVM, lint đã vá, bản release R8 trên A50s, so quyền và dung lượng).
3. **Duyệt bản dịch máy** hộp thoại WebDAV (37 ngôn ngữ), nhất là `lzh`, `my`, `kn`, `ml`.
4. `versionCode` store phải nhỏ hơn `20261122`.

**Đã chứng minh trên máy thật (A50s Android 11, bản release R8):** kiểm tra cập nhật qua Retrofit chạy tới phản hồi (HTTP 403 từ GitHub, không lỗi R8); đọc tập CommuteCast qua Gson chạy (`episode parsed: true dialogues=2`, dùng tập giả tạm rồi xóa); hộp thoại xác nhận WebDAV hiển thị đúng ở `ar`, `fa`, `iw`, `kn`, `my`, `ml`, `lzh`, nút Hủy không gửi yêu cầu; update 20260906 lên 20261122 giữ dữ liệu. **Chưa chứng minh:** máy API 26 đến 28, bản ký bằng khóa thật, 403 của kiểm tra cập nhật chưa tra nguyên nhân, chất lượng bản dịch.

**Đã tắt theo lựa chọn của bạn:** kiểm tra cập nhật nội bộ (`UPDATE_CHECK_ENABLED = false`), vì nó trỏ tới repo upstream và không thể báo bản mới của app này.

**Cố ý không làm (đã ghi ticket):** SEC-01 (HTTPS không kiểm chứng chứng chỉ), AI-01 (OpenAI, DeepSeek, Groq).

## 1. Đã xong (có bằng chứng, xem `doc/feature.md`)

| Việc | Kết quả | Commit |
|---|---|---|
| Lint API-level chạy thật (vá tạm tệp SDK) | 14 lỗi `NewApi` tìm ra, sửa còn 0 | `fb00893e` |
| NSD `setAttribute(String)` thay bản `byte[]` chỉ có từ SDK 37 | JVM + Pixel NSD thật 3/3 | `fb00893e` |
| OPML: mục hỏng bị bỏ qua thay vì làm hỏng cả lần nhập | 7 test, đột biến 4/4 | `88c6afe5` |
| Sao lưu: loại `webdav_backup_prefs.xml` (mật khẩu WebDAV chữ thường) | 5 test | `88c6afe5` |
| R8: hai lỗi thật ở bản release (Retrofit `ParameterizedType`, Gson `LinkedTreeMap`) | keep rule + 4 test JVM + chạy bản release trên Pixel | `42b4b35b` |
| Khôi phục OPML hai lần không nhân đôi nhóm (đo trên Room thật) | 6 test, đột biến 4/4 | `ee6f7acc` |
| Khôi phục WebDAV giới hạn 10 MiB (công bố và đếm byte) | 6 test, đột biến 3/3 | `d578c5d6` |
| Ba chip AI không dùng bị làm mờ, lựa chọn cũ hiển thị là Gemini | 6 test JVM + 3 test máy (Pixel 3/3, Tecno 4 lượt xanh) | `a5a34516` |
| Hộp thoại xác nhận trước khi khôi phục WebDAV, dịch máy 37 ngôn ngữ | 12 test, đột biến bị bắt (bản dịch **chưa có người duyệt**) | `7e9783a3` |
| Update 20260906 lên 20261122 trên Tecno: migration Room 20 lên 22, dữ liệu nguyên vẹn, DataStore chỉ thêm 2 khóa | integrity ok, foreign key 0 | `15bd125e` |
| NSD trên Tecno Android 14 (`setAttribute(String)`), Wi-Fi bật | 3/3 lượt xanh | `44686937` |
| Full JVM | **4.030 test, 0 lỗi** (`--rerun-tasks`, XML mới; lần chạy đầu đỏ 1 test chập chờn `SettingsProviderLoggingTest`, lần hai xanh) | |
| Phiên bản trong repo | `20261122` / `2026.11.22` (owner đổi) | `0181b282` |

## 1b. Đã ghi vào ticket, **không làm trong bản này** (owner defer)

| Ticket | Nội dung | Mức |
|---|---|---|
| `todo/SEC-01_https-trust-all-certs.md` | HTTPS không kiểm chứng chứng chỉ (`trustAllCerts` mặc định `true`); Play có thể cảnh báo; **chưa kiểm Play Console** | High |
| `todo/AI-01_byok-other-providers.md` | OpenAI, DeepSeek, Groq chỉ có chip (đã làm mờ), chưa nối thật | Medium |

## 1c. Còn mở ngoài kế hoạch kỹ thuật (chỉ owner xử lý)

| Việc | Ghi chú |
|---|---|
| **Khóa ký app đang public trên GitHub** (`app/keystores.jks`, `keystore.properties`, lịch sử git) | **Critical, chặn phát hành**; chờ owner kiểm Play Console có Play App Signing không. Chi tiết ở `~/exc-flaky-evidence/REL-03_secrets_audit_NOT_FOR_GIT.md` (ngoài repo). Tôi không đụng keystore theo lời owner |
| Khóa Gemini `AIza…` nằm thô trong `.claude/settings.local.json` (đã push) | owner tự xoay khóa Gemini |
| Duyệt bản dịch máy 37 ngôn ngữ của hộp thoại WebDAV | nhất là `lzh`, Miến Điện, Kannada, Malayalam |

## 2. Chưa xong, theo thứ tự cổng

### Cổng 0: những thứ chỉ owner cung cấp được (chặn các cổng sau)
- [ ] **Số bản đang trên store** (`versionCode`, `versionName`, Play Console). Bản update phải có `versionCode` lớn hơn. Repo đang `20261122`; tôi không biết số store.
- [ ] **Bước 2 của owner: migrate ad SDK xong và đã vào `dev`.** Mọi kiểm release bên dưới phải chạy **sau** mốc này, vì SDK mới có thể mang rule R8, quyền và lớp reflection riêng. Tôi không sửa tệp ad.
- [ ] **APK/AAB bản đã xuất bản (cùng khóa ký)** nếu muốn test cập nhật từ bản cũ. Không có thì bước đó chỉ kiểm được từ `0.9.x` nếu có APK, nếu không thì bỏ.

### Cổng 1: cố định mã (tôi làm được, sau Cổng 0 mục 2)
- [ ] Chạy lại **đúng bài kiểm bản release R8** trên bản có SDK quảng cáo mới (xem mục 3), **trên Tecno** (Android 14) và nếu còn thời gian trên Pixel. Lý do: JVM không chạy R8, lỗi chỉ lộ trên máy. Owner chọn đợi xong migrate ad SDK rồi chạy **một lần** trên bản cuối.
- [ ] Rà `proguard-rules.pro` sau khi SDK mới vào: rule trỏ gói không còn (`me.ash.reader`, `data.provider`) vẫn nằm đó (vô hại, ghi nhận trong audit H1). Chỉ dọn khi owner duyệt.
- [ ] Digest (`DailyDigestContent`) và `WebDavPositionRecord`: rule đã thêm nhưng **chưa chạy trên bản release** (Pixel không có digest, không có cấu hình WebDAV). Tạo một digest trên Pixel (cần Gemini) hoặc chấp nhận rủi ro đã ghi.
- [ ] Lint ổn định: quyết định cách chạy (vá tạm `api-versions.xml` mỗi lần, hoặc chấp nhận). Không sửa tệp SDK vĩnh viễn.

### Cổng 2: cổng kiểm cuối (tuần tự, không hai Gradle chung cây, chạy sau Cổng 1)
1. [ ] `git status` sạch (ngoài `.idea/workspace.xml`), đang ở `dev`, `origin/dev` đồng bộ.
2. [ ] `./gradlew testDevDebugUnitTest --rerun-tasks`: 0 lỗi, kiểm tuổi XML (bẫy XML cũ sau crash JVM 134, tỷ lệ 2/60).
3. [ ] Lint (vá tạm `api-versions.xml`, trả lại bằng `trap`, kiểm sha `9fd47aaa…`): 0 lỗi, `NewApi` 0. **Đã đạt tại HEAD `1b346efc` (2026-11-22)**; chạy lại sau khi SDK ad mới vào.
4. [ ] `./gradlew assembleProdRelease` và `./gradlew bundleProdRelease` (AAB nộp store): BUILD SUCCESSFUL; `apksigner verify` đọc được chứng chỉ **thật** (không phải khóa debug).
5. [ ] So APK/AAB mới với bản trước: kích thước, danh sách quyền (`aapt2 dump permissions`), `resourceConfigurations` (37 thư mục, đã khớp), dex không lộ chuỗi trace.
6. [ ] Bản release trên Tecno theo mục 3 (R8, Retrofit, Gson, quảng cáo thử). Lượt trên Pixel của bản trước đã chứng minh rule Retrofit và Gson (`doc/feature.md`, mục REL-01 R8).
7. [ ] Test cập nhật từ bản cũ (nếu có APK/AAB cũ cùng chữ ký).
8. [ ] Ghi vào `doc/feature.md`, đóng REL-02.

### Cổng 3: publish (owner thực hiện trên Play Console)
- [ ] `versionCode` mới > số store; `versionName` mới.
- [ ] Nộp **AAB** từ Cổng 2 bước 4; release notes từ `doc/feature.md`.
- [ ] **Data safety khớp thực tế:**
  - khóa Gemini, OpenAI, DeepSeek riêng của người dùng **nằm trong DataStore và được sao lưu** (M3a, giữ nguyên);
  - mật khẩu WebDAV đã loại khỏi sao lưu (`88c6afe5`);
  - quyền `ACCESS_LOCAL_NETWORK` (P2P LAN), `POST_NOTIFICATIONS`, `BLUETOOTH_CONNECT`;
  - `usesCleartextTraffic="true"` (nguồn RSS HTTP), ghi nhận không đổi;
  - dữ liệu gửi Gemini (tiêu đề + mô tả 250 ký tự cho quiz; nội dung cho tóm tắt).
- [ ] Rollout theo giai đoạn (ví dụ 5 đến 10 phần trăm, tăng dần); theo dõi crash và ANR trong Play Console.
- [ ] Giữ bản trước để quay lui.

## 3. Bài kiểm bản release R8 (lặp lại sau khi SDK ad mới vào)

Lý do: R8 không chạy trong JVM; lượt đầu tìm ra 2 lỗi thật. Cách làm (đã dùng thành công, `doc/feature.md` mục REL-01):
1. Sửa **tạm** `app/build.gradle` (không commit): `release` ký `signingConfigs.debug`, ad ID thử. Sao lưu tệp, `cmp` sau khi hoàn nguyên.
2. `assembleProdRelease`; cất APK ra `/tmp`; **hoàn nguyên `build.gradle` ngay** và `cmp`.
3. Thiết bị (hiện **Tecno**, launcher `com.transsion.hilauncher`): gate (`tools/device/pixel_gate.py <serial> <launcher>`), **đếm `ActiveInstrumentation` = 0 ngay trước mỗi lệnh ghi** (máy dùng chung), sao lưu byte-exact (`device_backup.py backup`), **đối chiếu manifest với bản đã xác minh** (bẫy: `run-as` bị từ chối trên bản release nên sao lưu cho `absent`), cài đè (cùng `versionCode` với bản debug để khỏi `INSTALL_FAILED_VERSION_DOWNGRADE`).
4. Quan sát **chủ động** hai đường mã: kiểm tra cập nhật (Retrofit) và đọc tập CommuteCast đã lưu (Gson). R8 gỡ `android.util.Log`, nên dùng `System.err` cho probe tạm.
5. Quảng cáo toàn màn hình: dump mới, nút có nhãn "Tiếp tục đến ứng dụng", kiểm foreground trước và sau (quy tắc `auto-close-ads-during-smoke`).
6. Cài lại bản debug đúng `versionCode`, `device_backup.py restore`, 8/8 `BYTE IDENTICAL`.

## 4. Rủi ro còn mở (nói thẳng)

| # | Rủi ro | Mức | Cách xử lý |
|---|---|---|---|
| 1 | SDK ad mới mang rule R8, quyền hoặc lớp reflection chưa kiểm | High | Cổng 1 chạy lại bài kiểm mục 3 |
| 2 | Số `versionCode` store chưa biết | High | Cổng 0 |
| 3 | **Cập nhật 2026-11-23:** NSD và chip AI đã chạy xanh trên **A50s API 30** (và Tecno API 34). Màu thanh điều hướng đã đọc qua cờ `LIGHT_NAVIGATION_BARS` trên API 30, nhưng chưa có máy **API 26 đến 28**. **Đã kiểm một phần (trước đó):** NSD `setAttribute(String)` chạy trên **Android 14** (Tecno, 3/3 xanh khi có Wi-Fi). **Chưa kiểm:** màu thanh điều hướng và chủ đề trên **API 26 đến 28** và Android 12 đến 13 (không có máy cũ); NSD mới thử với chính nó trên một mạng, chưa hai máy | Medium | ghi vào hồ sơ; cân nhắc rollout nhỏ trước |
| 4 | Digest và WebDAV chưa chạy ở bản release (**cập nhật 2026-11-23:** kiểm tra cập nhật Retrofit và đọc tập CommuteCast Gson **đã chạy** trên bản release R8 ở A50s; hộp thoại WebDAV hiển thị ở 7 ngôn ngữ; chưa gọi digest hay WebDAV thật) | Medium | Cổng 1 hoặc chấp nhận |
| 5 | Lint API-level chỉ chạy khi vá tệp SDK | Medium | Cổng 1: quyết cách chạy |
| 6 | DataStore (khóa API riêng) vẫn được sao lưu đám mây | Medium | khai báo Data safety (M3a) |
| 7 | Migration Room 1 đến 5 không có test và không có snapshot; cập nhật thật từ bản store chưa kiểm | Medium | cần APK cũ cùng khóa ký; bỏ nếu không còn người dùng v1 đến v5 |
| 8 | `-dontobfuscate` bật: bản release dễ đọc ngược | Low | ghi nhận, có chủ ý |
| 9 | Crash JVM 134 (2/60), chỉ ở test | Low | không chặn |
| 10 | Bản release thật (khóa thật, ad ID thật) chưa chạy trên máy vì khác khóa ký nên cài đè không được và gỡ app sẽ mất dữ liệu | Medium | owner thử qua cài mới trên máy phụ, hoặc rollout nhỏ |
| 11 | **Khóa ký app public trên GitHub** (`keystores.jks`, mật khẩu trong `keystore.properties` và lịch sử git) | **Critical** | chờ owner kiểm Play Console (Play App Signing); chi tiết ngoài repo |
| 12 | HTTPS không kiểm chứng chứng chỉ (SEC-01); Play có thể cảnh báo, **chưa kiểm** | High | defer theo owner; khai Data safety không khẳng định "mã hóa mọi dữ liệu khi truyền" |
| 13 | Bản dịch máy 37 ngôn ngữ của hộp thoại WebDAV chưa có người duyệt | Low | owner duyệt ngôn ngữ biết; test chỉ chứng minh cú pháp |
| 14 | `SettingsProviderLoggingTest` chập chờn (1 lần đỏ trong 2 lượt full JVM, hạn 5 giây); **không tái hiện được** (15 lượt riêng lớp, kể cả dưới tải CPU, đều xanh) | Low | chưa có nguyên nhân; nếu đỏ lại, giữ `app/build/test-results` và log của lượt đó trước khi chạy lại; chạy lại và kiểm tuổi XML, không bỏ qua |

## 5. Quy tắc xuyên suốt
- Chỉ nhánh `dev`; thử nghiệm là sửa tạm không commit, hoàn nguyên và `cmp` byte-identical.
- Commit không attribution AI (R6). Không tạo PR, không nhắc PR.
- Thiết bị: từ 2026-11-23 chỉ **Samsung A50s** `adb -s R58MA6WYRPE` (Android 11, API 30; owner: "dùng a50s cho mọi phiên"), launcher `com.sec.android.app.launcher`; trước đó Tecno `115333744A005844` (2026-11-22) và Pixel. Gate đọc được Android 11 từ `94c4b86a`. Không gửi lệnh tới Tecno (dùng chung với phiên LensLauncher) hay Pixel. (Cũ, Tecno:) Gate và sao lưu byte-exact quanh mỗi lượt; đếm `ActiveInstrumentation` ngay trước mỗi lệnh ghi vì máy dùng chung với phiên LensLauncher; không xóa DataStore; không bấm theo tọa độ cũ; việc làm được bằng adb (bật Wi-Fi, quyền tạm, cài đè, đóng quảng cáo) tự làm và trả về giá trị gốc, không hỏi lại. Vẫn hỏi trước khi xóa dữ liệu, gỡ app, force-push, đổi chế độ repo.
- Không dùng cờ ad/VIP làm lối tắt cho test. Không sửa tệp ad (owner đang migrate).
