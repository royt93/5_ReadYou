# [REL-01] Kế hoạch phát hành bản update lên store

- **Type:** Release
- **Priority:** `P0 (Blocker)`
- **Ngày lập:** 2026-11-21 (nhánh `dev`, chỉ một nhánh, không nhánh phụ)
- **Thiết bị kiểm:** chỉ Pixel 7 Pro `2B051FDH3006MU` (Android 17, SDK 37). Không gửi lệnh tới Tecno.
- **Gốc kế hoạch (lời owner):** 1) audit toàn bộ tính năng tìm rủi ro, impl ticket chưa xong, sửa bug; 2) owner tự tích hợp migrate ad SDK; 3) publish bản update (app đã từng xuất bản).

## Trạng thái hiện tại (đã đo, không đoán)

| Mục | Giá trị | Nguồn |
|---|---|---|
| `versionCode` / `versionName` trong repo | `20260906` / `2026.09.06`, không đổi từ ít nhất 2026-10-15 | `app/build.gradle` dòng 30 đến 31 |
| Số bản đang trên store | **CHƯA BIẾT** (tag git mới nhất `0.9.12` năm 2024, không phản ánh store) | cần owner cho |
| Room | `version = 22`, chuỗi `MIGRATION_1_2` đến `MIGRATION_21_22`, **không** có `fallbackToDestructiveMigration` | `AndroidDb.kt` |
| Snapshot schema | chỉ `5.json` đến `22.json` (không có v1 đến v4) | `app/schemas` |
| Test migration (theo tên tệp) | có 6→7 đến 11→12 và 14→15 đến 21→22; **không thấy tên** cho 12→13, 13→14 và 1→5 (tìm theo tên, có thể sót) | `app/src/test/.../infrastructure/db` |
| JVM | 3.984 test, 0 lỗi (`--rerun-tasks`, 2026-11-21) | `doc/feature.md` |
| Lint | 0 lỗi, `NewApi` 0, **chỉ khi vá tạm** `api-versions.xml` của SDK 37 (`37.0` thành `37`); chạy bình thường vẫn chết vì `NumberFormatException: "37.0"` | FRONT-02am và mục lint trong `doc/feature.md` |
| Crash JVM 134 | 2/60 lượt full JVM, chỉ ở test, không phải lỗi app | `doc/feature.md` |
| `assembleProdRelease` | xanh ở `b7d4aafd`; **chưa chạy lại** sau NSD và theme (`fb00893e`) | `doc/feature.md` |
| R8 / shrink | `minifyEnabled true`, `shrinkResources true` | `app/build.gradle` dòng 116 đến 117 |
| Ký | `keystore.properties` có trên máy; thiếu file thì không ký được | `CLAUDE.md` |

## Ticket mở: quyết định phạm vi bản này

| Nhóm | Ticket | Quyết định |
|---|---|---|
| Ad / IAP / VIP | ORACLE-04, BOUNTY-04, ECHO-04 (rewarded), IDEA-04 | **Ngoài bản này.** Owner đã defer và tự lo migrate ad SDK ở bước 2 |
| Gemini key | FIX-09 | **Giữ nguyên** theo quyết định owner 2026-11-14 |
| Cần Gemini thật | ORACLE-01, ORACLE-02, ORACLE-03, FRONT-01, RPG-02 (phần nợ) | **Ngoài bản này.** Bản thu hẹp đã có, phần còn lại không kiểm được (không có key riêng) |
| Cần thiết bị | EXC-05 (P2P hai máy) | **Chưa làm được:** cần máy thứ hai, owner chỉ cho dùng Pixel |
| Cần xe / DHU | FRONT-04 | Thử riêng ở giai đoạn C, thất bại thì ghi nhận, không chặn release |

Nguyên tắc: không đưa tính năng chưa kiểm được vào bản phát hành.

## Giai đoạn A: audit rủi ro (chỉ đọc, báo cáo theo mức độ, sửa khi owner duyệt)

- [ ] **A1 Cấu hình release:** `versionCode`/`versionName` (cần số store); R8 và `shrinkResources` giữ lớp Room, Hilt, Gson, Gemini; `resourceConfigurations` khớp các thư mục `values-*` (xem `CLAUDE.md`); permission đã merge của manifest (đã thấy `ACCESS_LOCAL_NETWORK`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, cần liệt kê đủ); `foregroundServiceType` khớp quyền.
- [ ] **A2 Dữ liệu người dùng khi update:** bổ sung test migration cho các bước không thấy test (12→13, 13→14); đối chiếu chuỗi migration với snapshot v5 đến v22; kiểm DataStore giữ nguyên qua update; đường nâng cấp từ bản đang trên store (cần APK/AAB cũ cùng chữ ký, xem mục "Cần từ owner").
- [ ] **A3 Crash tiềm ẩn (R5):** `!!`, `lateinit`, listener/timer/stream chưa `dispose` hoặc `cancel`; `NoSuchMethodError` kiểu NSD đã gặp (lint không còn che sau khi vá SDK).
- [ ] **A4 Chính sách store:** Data safety (dữ liệu gửi lên Gemini, Nostr/Bluesky), quyền `ACCESS_LOCAL_NETWORK`, mô tả quyền mạng LAN, Gemini key mặc định nhúng trong APK (FIX-09, giữ nguyên, ghi trong hồ sơ rủi ro).
- [ ] **A5 Lint:** quyết định cách chạy lint ổn định (vá tạm `api-versions.xml` mỗi lần, hoặc chấp nhận hạn chế); ghi vào quy trình, không sửa tệp SDK vĩnh viễn.

Đầu ra: báo cáo `doc/task/done/REL-01_audit_DONE.md` (mức Critical, High, Medium, Low, kèm bằng chứng `file:dòng`).

## Giai đoạn B: sửa lỗi và hoàn thiện (chỉ cái audit tìm ra và owner duyệt)

- [ ] Mỗi lỗi: test đỏ trước (TDD), sửa, test xanh, đột biến bị bắt, `doc/feature.md` ghi lại (R2, R5).
- [ ] Không thêm tính năng mới. Không đụng ad (owner đang migrate ở bước 2).
- [ ] **Đóng băng mã ad:** phần của owner (bước 2) phải xong và được merge vào `dev` **trước** khi build release cuối; tôi không sửa tệp ad.

## Giai đoạn C: thử nghiệm riêng (không chặn release)

- [ ] **FRONT-04 Android Auto (DHU)** trên Pixel nếu hỗ trợ head unit server. Lần trước DHU chỉ tới TLS (Tecno), chưa ra màn xe. Thất bại thì ghi nhận.
- [ ] EXC-05 hai máy: không làm trong bản này (thiếu máy thứ hai).

## Giai đoạn D: cổng kiểm cuối (chạy sau khi bước 2 của owner xong, theo thứ tự)

Mọi lệnh Gradle chạy tuần tự, không hai Gradle chung cây.

1. [ ] `git status` sạch, đang ở `dev`, `origin/dev` đồng bộ.
2. [ ] `./gradlew testDevDebugUnitTest --rerun-tasks`: 0 lỗi, kiểm tuổi XML (tránh XML cũ sau crash JVM).
3. [ ] Lint (cách đã chọn ở A5): 0 lỗi, `NewApi` 0.
4. [ ] `./gradlew assembleProdRelease` và `bundleProdRelease` (AAB nộp store): BUILD SUCCESSFUL, `apksigner verify` đọc được chứng chỉ.
5. [ ] So APK/AAB mới với bản trước: kích thước, danh sách quyền, `resourceConfigurations`, dex không lộ chuỗi trace.
6. [ ] Pixel (gate, sao lưu Room, DataStore, pairing byte-exact, khôi phục): chạy bản **release** đã ký, đường mở app, đồng bộ, đọc bài, quiz, Zen, P2P tự phát hiện, rồi so DataStore.
7. [ ] Test cập nhật từ bản cũ (nếu có APK/AAB cũ cùng chữ ký): cài bản cũ, thêm dữ liệu, cài đè bản mới, kiểm Room và DataStore còn nguyên.
8. [ ] Ghi toàn bộ vào `doc/feature.md` kèm bằng chứng; đóng REL-01.

## Giai đoạn E: publish (owner thực hiện trên Play Console)

- [ ] `versionCode` mới lớn hơn bản trên store, `versionName` mới.
- [ ] Nộp AAB từ giai đoạn D; ghi chú phát hành (release notes) từ `doc/feature.md`; Data safety khớp A4.
- [ ] Rollout theo giai đoạn (ví dụ 5 đến 10 phần trăm, tăng dần), theo dõi crash và ANR trong Play Console.
- [ ] Giữ lại bản trước để quay lui nếu có sự cố.

## Cần từ owner (chặn một số bước)

1. **`versionCode` và `versionName` của bản đang trên store** (Play Console). Không có thì không đặt được số mới.
2. **APK/AAB bản đã xuất bản** (hoặc bản cùng chữ ký) nếu muốn bước D7. Không đổi chữ ký trên Pixel (sẽ buộc gỡ app và mất dữ liệu).
3. **Mốc xong của bước 2 (migrate ad SDK)**, để tôi chạy giai đoạn D.
4. Quyết định A5 (cách chạy lint) và duyệt từng sửa ở giai đoạn B.

## Quy tắc xuyên suốt

- Chỉ nhánh `dev`; experiment là sửa tạm không commit, hoàn nguyên và `cmp` byte-identical.
- Commit không attribution AI (R6). Không tạo PR, không nhắc PR.
- Pixel: gate, sao lưu và khôi phục byte-exact quanh mỗi lượt; không xóa DataStore; quảng cáo toàn màn hình thì đóng theo quy tắc `auto-close-ads-during-smoke`; không bấm theo tọa độ cũ.
- Không dùng cờ ad/VIP làm lối tắt cho test.
