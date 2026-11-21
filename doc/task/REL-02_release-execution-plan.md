# [REL-02] Kế hoạch thực thi phát hành bản update (thay thế phần "việc còn lại" của REL-01)

- **Type:** Release
- **Priority:** `P0 (Blocker)`
- **Ngày lập:** 2026-11-21, nhánh `dev` (một nhánh duy nhất, không nhánh phụ). HEAD khi lập: `42b4b35b`.
- **Thiết bị kiểm:** chỉ Pixel 7 Pro `2B051FDH3006MU` (Android 17, SDK 37). Không gửi lệnh tới Tecno.
- **Quan hệ với REL-01:** REL-01 (`REL-01_release-update-plan.md`) là kế hoạch gốc; audit A1 đến A3 xong (`done/REL-01_audit_A1-A3_DONE.md`). REL-02 chỉ giữ phần **chưa xong**, theo thứ tự thực hiện.

## 1. Đã xong (có bằng chứng, xem `doc/feature.md`)

| Việc | Kết quả | Commit |
|---|---|---|
| Lint API-level chạy thật (vá tạm tệp SDK) | 14 lỗi `NewApi` tìm ra, sửa còn 0 | `fb00893e` |
| NSD `setAttribute(String)` thay bản `byte[]` chỉ có từ SDK 37 | JVM + Pixel NSD thật 3/3 | `fb00893e` |
| OPML: mục hỏng bị bỏ qua thay vì làm hỏng cả lần nhập | 7 test, đột biến 4/4 | `88c6afe5` |
| Sao lưu: loại `webdav_backup_prefs.xml` (mật khẩu WebDAV chữ thường) | 5 test | `88c6afe5` |
| R8: hai lỗi thật ở bản release (Retrofit `ParameterizedType`, Gson `LinkedTreeMap`) | keep rule + 4 test JVM + chạy bản release trên Pixel | `42b4b35b` |
| Full JVM | **4.000 test, 0 lỗi** (`--rerun-tasks`, XML mới) | |
| Phiên bản trong repo | `20261122` / `2026.11.22` (owner đổi) | `0181b282` |

## 2. Chưa xong, theo thứ tự cổng

### Cổng 0: những thứ chỉ owner cung cấp được (chặn các cổng sau)
- [ ] **Số bản đang trên store** (`versionCode`, `versionName`, Play Console). Bản update phải có `versionCode` lớn hơn. Repo đang `20261122`; tôi không biết số store.
- [ ] **Bước 2 của owner: migrate ad SDK xong và đã vào `dev`.** Mọi kiểm release bên dưới phải chạy **sau** mốc này, vì SDK mới có thể mang rule R8, quyền và lớp reflection riêng. Tôi không sửa tệp ad.
- [ ] **APK/AAB bản đã xuất bản (cùng khóa ký)** nếu muốn test cập nhật từ bản cũ. Không có thì bước đó chỉ kiểm được từ `0.9.x` nếu có APK, nếu không thì bỏ.

### Cổng 1: cố định mã (tôi làm được, sau Cổng 0 mục 2)
- [ ] Chạy lại **đúng bài kiểm bản release R8** của REL-01 trên bản có SDK quảng cáo mới (xem mục 3). Lý do: JVM không chạy R8, lỗi chỉ lộ trên máy.
- [ ] Rà `proguard-rules.pro` sau khi SDK mới vào: rule trỏ gói không còn (`me.ash.reader`, `data.provider`) vẫn nằm đó (vô hại, ghi nhận trong audit H1). Chỉ dọn khi owner duyệt.
- [ ] Digest (`DailyDigestContent`) và `WebDavPositionRecord`: rule đã thêm nhưng **chưa chạy trên bản release** (Pixel không có digest, không có cấu hình WebDAV). Tạo một digest trên Pixel (cần Gemini) hoặc chấp nhận rủi ro đã ghi.
- [ ] Lint ổn định: quyết định cách chạy (vá tạm `api-versions.xml` mỗi lần, hoặc chấp nhận). Không sửa tệp SDK vĩnh viễn.

### Cổng 2: cổng kiểm cuối (tuần tự, không hai Gradle chung cây, chạy sau Cổng 1)
1. [ ] `git status` sạch (ngoài `.idea/workspace.xml`), đang ở `dev`, `origin/dev` đồng bộ.
2. [ ] `./gradlew testDevDebugUnitTest --rerun-tasks`: 0 lỗi, kiểm tuổi XML (bẫy XML cũ sau crash JVM 134, tỷ lệ 2/60).
3. [ ] Lint (cách đã chọn): 0 lỗi, `NewApi` 0.
4. [ ] `./gradlew assembleProdRelease` và `./gradlew bundleProdRelease` (AAB nộp store): BUILD SUCCESSFUL; `apksigner verify` đọc được chứng chỉ **thật** (không phải khóa debug).
5. [ ] So APK/AAB mới với bản trước: kích thước, danh sách quyền (`aapt2 dump permissions`), `resourceConfigurations` (37 thư mục, đã khớp), dex không lộ chuỗi trace.
6. [ ] Bản release trên Pixel theo mục 3 (R8, Retrofit, Gson, quảng cáo thử).
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
3. Pixel: gate (`tools/device/pixel_gate.py`), sao lưu byte-exact (`device_backup.py backup`), **đối chiếu manifest với bản đã xác minh** (bẫy: `run-as` bị từ chối trên bản release nên sao lưu cho `absent`), cài đè (cùng `versionCode` với bản debug để khỏi `INSTALL_FAILED_VERSION_DOWNGRADE`).
4. Quan sát **chủ động** hai đường mã: kiểm tra cập nhật (Retrofit) và đọc tập CommuteCast đã lưu (Gson). R8 gỡ `android.util.Log`, nên dùng `System.err` cho probe tạm.
5. Quảng cáo toàn màn hình: dump mới, nút có nhãn "Tiếp tục đến ứng dụng", kiểm foreground trước và sau (quy tắc `auto-close-ads-during-smoke`).
6. Cài lại bản debug đúng `versionCode`, `device_backup.py restore`, 8/8 `BYTE IDENTICAL`.

## 4. Rủi ro còn mở (nói thẳng)

| # | Rủi ro | Mức | Cách xử lý |
|---|---|---|---|
| 1 | SDK ad mới mang rule R8, quyền hoặc lớp reflection chưa kiểm | High | Cổng 1 chạy lại bài kiểm mục 3 |
| 2 | Số `versionCode` store chưa biết | High | Cổng 0 |
| 3 | Pixel là SDK 37: đường NSD `setAttribute(String)` và màu thanh điều hướng trên Android 12 đến 16, và API 26 đến 28 **chưa kiểm** (không có máy cũ) | Medium | ghi vào hồ sơ; cân nhắc rollout nhỏ trước |
| 4 | Digest và WebDAV chưa chạy ở bản release | Medium | Cổng 1 hoặc chấp nhận |
| 5 | Lint API-level chỉ chạy khi vá tệp SDK | Medium | Cổng 1: quyết cách chạy |
| 6 | DataStore (khóa API riêng) vẫn được sao lưu đám mây | Medium | khai báo Data safety (M3a) |
| 7 | Migration Room 1 đến 5 không có test và không có snapshot; cập nhật thật từ bản store chưa kiểm | Medium | cần APK cũ cùng khóa ký; bỏ nếu không còn người dùng v1 đến v5 |
| 8 | `-dontobfuscate` bật: bản release dễ đọc ngược | Low | ghi nhận, có chủ ý |
| 9 | Crash JVM 134 (2/60), chỉ ở test | Low | không chặn |
| 10 | Bản release thật (khóa thật, ad ID thật) chưa chạy trên máy vì sẽ mất dữ liệu Pixel | Medium | owner thử qua cài mới trên máy phụ, hoặc rollout nhỏ |

## 5. Quy tắc xuyên suốt
- Chỉ nhánh `dev`; thử nghiệm là sửa tạm không commit, hoàn nguyên và `cmp` byte-identical.
- Commit không attribution AI (R6). Không tạo PR, không nhắc PR.
- Pixel: gate, sao lưu và khôi phục byte-exact quanh mỗi lượt; không xóa DataStore; không bấm theo tọa độ cũ; chỉ `adb -s 2B051FDH3006MU`.
- Không dùng cờ ad/VIP làm lối tắt cho test. Không sửa tệp ad (owner đang migrate).
