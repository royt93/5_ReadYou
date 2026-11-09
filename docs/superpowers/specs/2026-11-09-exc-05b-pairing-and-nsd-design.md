# EXC-05b — Ghép đôi một lần và tự tìm máy qua NSD: Design

**Ngày:** 2026-11-09 · **Nhánh:** `dev` · **Trạng thái:** chờ owner duyệt spec

## Mục tiêu
Sau khi ghép đôi **một lần**, hai bản RSS Cat Hub trong cùng LAN tự tìm nhau qua NSD và dùng khoá đã lưu cho các lượt đồng bộ sau, không phải nhập URL hay secret nữa. Vẫn **thủ công, một chiều mỗi lượt, người dùng xem trước rồi mới Apply**, như EXC-05a.

Lát này **không** hoàn tất EXC-05 gốc. Không có đồng bộ tự động, hai chiều, hay mốc ba giây (xem Ngoài phạm vi và Giới hạn).

## Bối cảnh và bằng chứng (đã đọc code và chạy thật)
- EXC-05a có sẵn: `PairingSecret` (128-bit), `SyncEnvelopeCrypto` (PBKDF2 310.000 vòng + AES-GCM), `SyncPackageCodec`, `SyncSessionTransport`, `SyncSessionEngine`/`MirrorServer` ở chế độ sync-only, `LocalSyncRepository`, `LocalSyncViewModel`, `LanAddressProvider`.
- Repo **chưa có** dependency PAKE, Tink, BouncyCastle hay Conscrypt (đã kiểm classpath `devDebugRuntimeClasspath`: 0 kết quả). Không có QR/camera, không có Android Keystore, không NSD. Token phiên hiện do `MirrorServer.startSync` tự sinh ngẫu nhiên bên trong (`SyncLimits.SESSION_TTL_MILLIS` = 5 phút; `MirrorLimits.TOKEN_TTL_MILLIS` = 30 phút chỉ thuộc chế độ Mirror).
- **Phát hiện trên Pixel 7 Pro, Android 17 (API 37):** app `targetSdk 37` chỉ khai báo `INTERNET`, không khai báo `ACCESS_LOCAL_NETWORK`. Local Sync báo bắt đầu thành công và `/proc/net/tcp` cho thấy app đang LISTEN ở cổng 42697, nhưng `curl` từ Mac tới URL phiên **timeout sau 10 giây**; một listener đối chứng do `adb shell` dựng trên cùng máy trả HTTP 200 trong 0,15 giây. Theo [tài liệu Android](https://developer.android.com/privacy-and-security/local-network-permission), app target SDK 37 bị chặn LAN mặc định, cả kết nối đi lẫn đến. Dữ kiện này **tương thích** với giả thuyết đó nhưng **chưa chứng minh**: app chưa khai báo quyền nên chưa thể cấp để so sánh. Task đầu tiên của plan phải xác nhận bằng cách khai báo, cấp và thử lại.
- `AndroidManifest` có `allowBackup="true"` cùng hai file quy tắc `backup_rules.xml` và `data_extraction_rules.xml` còn là mẫu rỗng.

## Quyết định của owner (AskUserQuestion)
1. Lát đầu: **ghép đôi một lần + NSD**, chưa tự động, chưa hai chiều.
2. Ghép đôi bằng **secret 128-bit hiện có, nhập một lần**. Mã 6 số kèm PAKE bị loại: cần dependency hoặc tự viết crypto, trái ràng buộc.
3. Lưu khoá bằng **Android Keystore** bọc ciphertext.
4. Máy gửi lưu khoá **ngay khi bấm "Ghép đôi máy mới"**, nhãn "chưa xác nhận"; không thêm route POST.
5. NSD quảng bá **chỉ tên dịch vụ và một nonce ngẫu nhiên mỗi phiên**, không tên thiết bị.
6. **Chấp nhận không có forward secrecy**, ghi rõ giới hạn, có nút "Quên máy này".
7. **Quyền LAN thuộc scope lát này**, dùng chung cho Local Sync, Second Screen và NSD.
8. Khoá ghép đôi nằm ở **tệp riêng, loại khỏi cả cloud backup và device-transfer**.

## Thiết kế

### 1. `LanPermission` (quyền LAN, dùng chung)
- Khai báo `android.permission.ACCESS_LOCAL_NETWORK` trong manifest. Thuộc nhóm `NEARBY_DEVICES`.
- Hàm thuần `LanAccess.required(sdkInt)`: chỉ yêu cầu từ API 37; API thấp coi như đã có quyền, **không** xin runtime (tài liệu nói không khai báo/xin trước khi target 37; app đã target 37 nên khai báo là đúng).
- Trước mọi thao tác LAN (Send, Receive, quảng bá/tìm NSD, bật Second Screen) giao diện kiểm quyền. Chưa có thì xin; từ chối thì hiện thông báo đã bản địa hoá, kèm nút mở cài đặt, **không** cố kết nối.
- Hành vi cho **Second Screen (EXC-13)** thay đổi: nay phải có quyền. Đây là sửa lỗi thực tế, không phải tính năng mới. Phải kiểm chứng riêng trên Pixel.

### 2. `PairingStore` (kho khoá)
- Tệp riêng `pairing_store` (không dùng chung DataStore chính). Mỗi mục: `peerId` (16 byte ngẫu nhiên, hex), nhãn do người dùng đặt, trạng thái `UNCONFIRMED | CONFIRMED`, thời điểm tạo, `ciphertext` của secret.
- Secret 128-bit được bọc bằng khoá **AES-GCM 256-bit không xuất được** trong Android Keystore (`KeyGenParameterSpec`, `PURPOSE_ENCRYPT|DECRYPT`, `BLOCK_MODE_GCM`, không cần xác thực người dùng). Alias cố định. Không dependency mới.
- Giao diện lưu trữ qua interface `SecretWrapper` (bọc/mở) để unit test dùng bản giả; chỉ bản thật chạm Keystore.
- Mục hỏng (giải mã thất bại, khoá Keystore mất): đánh dấu **cần ghép lại**, không ném lỗi ra UI, không dùng được cho đến khi người dùng xoá.
- **Sao lưu:** thêm `<exclude>` cho `pairing_store` trong **cả** `backup_rules.xml` và `data_extraction_rules.xml` (`cloud-backup` và `device-transfer`). Khôi phục trên máy mới không có khoá, người dùng ghép lại.
- "Quên máy này" xoá mục. Không có xoá hàng loạt ngầm.

### 3. Token phiên dẫn xuất (thay token ngẫu nhiên khi dùng khoá đã ghép)
- Mỗi phiên gửi sinh `nonce` 16 byte ngẫu nhiên. `token = HMAC-SHA256(secret, "RCS-session-v1" || nonce)` cắt còn 16 byte, viết hex 32 ký tự. Dùng `javax.crypto.Mac` của JCA; **không** tự viết crypto.
- Nonce nằm trong TXT NSD; **token và secret không bao giờ** nằm trong TXT hay request. Người lạ trong LAN thấy dịch vụ, cổng và nonce, nhưng không tính được token nên mọi request nhận 404 như hiện nay.
- Máy đã ghép tính token từ nonce và secret đã lưu, rồi gọi `GET /s/<token>`. Route `SyncSessionRouter`, bộ giới hạn tốc độ và thời hạn phiên `SyncLimits.SESSION_TTL_MILLIS` (5 phút) giữ nguyên. Luồng nhập secret thủ công của EXC-05a **vẫn còn** như đường lui.
- **Thay đổi engine (không chỉ đổi nguồn token):** hiện `MirrorServer.startSync(bindAddress, ciphertext)` tự sinh token ngẫu nhiên bên trong. Phải thêm một biến thể nhận token (và nonce) từ bên ngoài, ví dụ `startSync(bindAddress, ciphertext, token)`, giữ nguyên biến thể cũ cho luồng nhập secret thủ công. Cả hai nhánh phải có test: nhánh dẫn xuất không bao giờ lộ token ra ngoài `MirrorStartResult`, nhánh ngẫu nhiên không đổi hành vi. Đây là sửa `SyncSessionEngine` (interface) và `MirrorServer`, cần cập nhật các test fake engine hiện có.
- So sánh token dùng hằng thời gian (`MessageDigest.isEqual`) như `MirrorToken` hiện có.

### 4. `PeerDiscovery` (NSD)
- Dịch vụ `_rsscathub._tcp`; TXT chỉ gồm `v=1` và `n=<nonce hex>`. Tên dịch vụ ngẫu nhiên mỗi phiên, **không** chứa tên máy, tên người dùng, số feed hay token.
- Interface mỏng `NsdPlatform` bọc `NsdManager`: nhánh API 34+ dùng `registerServiceInfoCallback`/`getHostAddresses` (API `resolveService` đã bị loại từ 34), nhánh API 26–33 dùng API cũ sau kiểm tra phiên bản. Unit test dùng bản giả; chỉ nhánh API 34+ kiểm được trên máy thật hiện có.
- Địa chỉ phân giải ra **phải** là IPv4 riêng (RFC1918) qua `LanAddress.isPrivateIpv4`, giống `SyncSessionUrl`; địa chỉ khác bị bỏ.
- Huỷ đăng ký và dừng tìm khi: người dùng bấm Dừng, phiên hết hạn, mất mạng, app xuống nền, rời màn.

### 5. Luồng ghép đôi lần đầu và các lần sau
- **Ghép lần đầu (máy gửi):** "Ghép đôi máy mới" sinh secret, hiện cho người dùng (Copy/Share như EXC-05a), lưu mục `UNCONFIRMED` với nhãn do người dùng nhập, rồi bật phiên.
- **Ghép lần đầu (máy nhận):** nhập secret một lần; app tìm máy gửi qua NSD (hoặc nhập URL thủ công), giải mã, xem trước. **Chỉ sau khi giải mã và kiểm GCM thành công** mới lưu mục và đánh dấu `CONFIRMED` phía nhận.
- **Các lần sau:** người dùng chọn máy đã ghép trong danh sách; hai bên dùng khoá đã lưu, không nhập gì. Xem trước và nút Apply riêng như EXC-05a.
- Phía gửi không biết nhận đã ghép thành công (không có ack). Mục `UNCONFIRMED` ở phía gửi chuyển `CONFIRMED` khi người dùng tự xác nhận, hoặc người dùng xoá. Đây là giới hạn đã nêu.

### 6. Giao diện
- Mở rộng màn "Đồng bộ cục bộ": thêm danh sách "Máy đã ghép" (nhãn, trạng thái, nút Quên), nút "Ghép đôi máy mới", và danh sách máy tìm thấy qua NSD khi đang tìm.
- Thông báo quyền LAN bị từ chối, Keystore hỏng, không tìm thấy máy, hết hạn: chuỗi đã bản địa hoá; tránh `%d` đứng trước danh từ số nhiều để không dính `PluralsCandidate`.
- Chuỗi mới có đủ 38 locale (EN và VI viết tay).

## Giới hạn nói thẳng
1. **Không đạt acceptance criteria EXC-05 gốc:** không đồng bộ tự động khi mở app, không hai chiều, không mốc ba giây giữa hai Android.
2. **Hai Android thật chưa kiểm chứng.** Môi trường chỉ có Pixel (Android 17) và Mac (`dns-sd` kiểm quảng bá/phân giải). Mac không giải mã được gói. Tương thích Android–Android và nhánh NSD API 26–33 chỉ kiểm bằng unit test.
3. **Không có forward secrecy.** Secret cố định dùng cho mọi phiên; lộ secret thì mọi gói cũ bị nghe lén trên LAN đều giải mã được. Giảm nhẹ: nút "Quên máy này" và gói chỉ có trạng thái đọc/sao và URL feed.
4. NSD cho người trong LAN biết **có RSS Cat Hub trên mạng**, cổng và nonce. Không lộ tên máy hay nội dung.
5. HTTP thường: URL, địa chỉ, thời điểm và độ dài vẫn thấy được; chỉ nội dung gói được mã hoá.
6. Máy gửi không biết máy nhận ghép thành công (không ack), nên có thể còn mục `UNCONFIRMED`.
7. Khôi phục sang máy mới luôn phải ghép lại (khoá bị loại khỏi backup).
8. Hành vi quyền LAN trên Android 17 **chưa được chứng minh bằng đối chiếu cấp/không cấp**; spec dựa vào tài liệu cộng bằng chứng tương thích đã nêu.

## Kiểm thử
- Thuần/JVM: `LanAccessTest` (ngưỡng API), `SessionTokenDerivationTest` (HMAC khác nonce/secret cho token khác, cùng đầu vào cho cùng token, độ dài/hex, so sánh hằng thời gian, không lộ secret), `PairingStoreTest` với `SecretWrapper` giả (lưu, đọc, trạng thái, xoá, mục hỏng, nhãn rỗng/quá dài bị từ chối), `NsdTxtCodecTest` (chỉ `v` và `n`, từ chối khoá lạ, nonce sai độ dài), `PeerDiscoveryTest` với `NsdPlatform` giả (địa chỉ không phải RFC1918 bị bỏ, huỷ khi dừng/mất mạng).
- Quy tắc backup: test đọc cả hai XML và khẳng định có `<exclude>` cho `pairing_store` ở `cloud-backup`, `device-transfer` và `full-backup-content`.
- Mutation: bỏ exclude, đổi cắt token, bỏ kiểm RFC1918, bỏ huỷ NSD khi dừng; mỗi lần phải có test đỏ.
- ViewModel/Compose (Robolectric): luồng ghép lần đầu, chọn máy đã ghép, quyền bị từ chối không mở socket, Keystore hỏng hiện "cần ghép lại", Forget xoá mục.
- Instrumentation trên thiết bị: bọc/mở khoá bằng **Keystore thật**, `NsdManager` thật đăng ký và phân giải (API 34+), HMAC thật. Không dùng `connected*`.
- Smoke trên Pixel (Android 17): xác nhận quyền LAN bằng cách đối chiếu trước/sau khi cấp (đo `curl` từ Mac), quảng bá NSD thấy bằng `dns-sd`, Local Sync end-to-end tới Mac ở mức vận chuyển ciphertext, Second Screen sau khi có quyền. Backup Room và DataStore trước, khôi phục và `cmp` sau, **không xoá DataStore**.

## Ngoài phạm vi
Đồng bộ tự động, hai chiều/xung đột đồng thời, đồng bộ nền/WorkManager, mốc ba giây, PAKE, QR/camera, forward secrecy, ack giữa hai máy, TLS, đổi định dạng gói EXC-05a, đồng bộ nội dung bài/ghi chú/vị trí đọc, Room migration, dependency mới, quảng cáo/IAP.
