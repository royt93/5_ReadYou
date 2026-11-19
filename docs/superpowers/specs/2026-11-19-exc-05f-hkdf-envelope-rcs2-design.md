# EXC-05f — Gói đồng bộ RCS2 dùng HKDF thay PBKDF2, vẫn đọc được RCS1 (thiết kế, chờ owner duyệt spec)

**Ngày:** 2026-11-19 · **Nhánh:** `dev` · **Nền:** EXC-05a (`SyncEnvelopeCrypto`, gói `RCS1`), 05d/05e (tự nhận, đo thời gian)

> Trạng thái: owner đã chốt hướng (2026-11-19): **gói mới `RCS2` dùng HKDF-SHA256 (không làm chậm), vẫn đọc được `RCS1` cũ**. Đây là **đổi mã hóa** nên spec chờ owner duyệt trước khi lập plan. Chưa có plan, chưa có mã. Chỉ JVM; đo lại trên Pixel sau khi xong (một máy).

## Mục tiêu
05e đo trên Pixel: từ lúc bắt đầu đến xem trước **4,45 đến 4,83 s**. Trong đó **PBKDF2 310.000 vòng tốn 1,7 đến 1,85 s khi mã hóa và 1,5 đến 1,57 s khi giải mã** (đo thật 3 lượt trên Pixel). Bỏ phần đó đưa phía nhận từ ~2,8 s xuống ~1,3 s và tổng xuống ~2,3 s, dưới 3 giây của EXC-05 gốc, mà không đổi AES-GCM 256-bit, tag 128-bit, nonce, hay mô hình hai máy.

## Vì sao HKDF an toàn ở đây (và khi nào thì không)
PBKDF2 làm **chậm** việc đoán một mật khẩu **yếu**. Secret ghép đôi là **128 bit ngẫu nhiên** (`PairingSecret.generate()`: `SecureRandom`, 16 byte, 32 ký tự hex), nên không có gì để "làm chậm đoán": 2^128 khả năng. Với một khóa đã mạnh, chuẩn dùng là **HKDF** (RFC 5869: Extract rồi Expand) để suy khóa AES-256 từ secret + salt, gần như không tốn thời gian. **Điều kiện bắt buộc:** secret luôn phải là 128-bit ngẫu nhiên. `PairingSecret.fromHex` (người dùng gõ mã ở màn hình nhận, và đọc lại từ `PairingStore`) vẫn ép đúng 32 ký tự hex = 128 bit; nhưng **không ép "ngẫu nhiên"**: một người **tự gõ mã yếu** như `0000...0001` vẫn được chấp nhận ở đầu nhận. Mã đó chỉ đến từ máy phát (luôn `generate()`) trong luồng bình thường; **máy phát không bao giờ nhận mã người gõ** (ghép mới sinh mã ở máy phát, người dùng chỉ gõ nó ở máy nhận). Vì vậy kẻ tấn công chỉ có thể thử secret yếu nếu **chính người dùng tự chọn mã yếu ở máy phát**, điều app không cho. Spec ghi điều này thành khẳng định có test (xem Kiểm chứng) chứ không chỉ lập luận.

## Sự thật đã đọc (2026-11-19)
- `SyncEnvelopeCrypto` (domain, thuần JVM): gói `RCS1` = `MAGIC("RCS1" 4B)` + `VERSION(1B)` + `salt(16B)` + `nonce(12B)` + `ciphertext+tag(16B)`; khóa = `PBKDF2WithHmacSHA256(secret.hex làm mật khẩu, salt, 310.000 vòng, 256 bit)`; `AES/GCM/NoPadding`; AAD = `MAGIC + VERSION`. Nơi dùng: `LocalSyncViewModel` (mã hóa ở dòng 106 và 343; giải mã ở dòng 180), kích thước tối thiểu ở `SyncSessionTransport` và `MirrorServer` (`OVERHEAD_BYTES`), test.
- `SyncEnvelopeCryptoTest` (9 test) kiểm vòng mã hóa, salt/nonce mới mỗi lần, sai secret, đổi từng vùng byte, độ dài sai, "quá lớn bị loại **trước** khi suy khóa" (so thời gian với một lần PBKDF2: **sẽ cần viết lại** vì HKDF gần bằng 0), không lộ bản rõ, giới hạn 8 MiB.
- Mọi secret mà `LocalSyncViewModel` mã hóa đến từ `PairingStore` (`pairingStore.secretOf`) hoặc `PairingSecret.generate()` (ghép mới, dòng 104 và 251); không có đường nào mã hóa bằng mã người gõ.
- Hai máy phải cùng hiểu định dạng: **app cũ chỉ đọc `RCS1`**.

## Thiết kế
- **Định dạng mới `RCS2`:** `MAGIC("RCS2")` + `VERSION(1B)=2` + `salt(16B)` + `nonce(12B)` + `ciphertext+tag`. Cùng độ dài header với `RCS1` (nên `OVERHEAD_BYTES`, giới hạn 8 MiB, kiểm kích thước không đổi). AAD = `MAGIC + VERSION` của **chính gói** (RCS2 buộc vào RCS2: kẻ tấn công không thể đổi nhãn RCS2 thành RCS1 để ép dùng PBKDF2 mà không làm hỏng tag, vì AAD đổi).
- **Khóa RCS2:** `HKDF-SHA256`: `PRK = HMAC-SHA256(key = salt, data = secretBytes)`; `OKM = HMAC-SHA256(key = PRK, data = info || 0x01)` với `info = "RCS2 envelope key"` (ASCII), lấy 32 byte (đúng một khối, nên một vòng Expand là đủ). `secretBytes` = 16 byte giải mã từ hex (không dùng chuỗi hex làm mật khẩu như RCS1). Hằng có tên (R5): `RCS2_INFO`.
- **`encrypt` luôn ghi `RCS2`.** `decrypt` đọc theo `MAGIC`: `RCS2` đi đường HKDF; `RCS1` đi đường PBKDF2 cũ (giữ nguyên mã); magic khác thì loại. Không có đường "hạ cấp" do người gửi chọn: một máy nhận cũ (chỉ đọc RCS1) sẽ loại gói RCS2 ngay ở bước magic và hiện lỗi gói như hôm nay với gói hỏng.
- **Không đổi:** AES-256-GCM, tag 128-bit, độ dài salt/nonce, giới hạn 8 MiB, `PairingSecret`, giao thức NSD/token, Room (v20), DataStore, manifest, `app/build.gradle`; không dependency (HMAC có sẵn trong JCA).
- Không đổi UI hay chuỗi.

## Phạm vi trung thực
- **Chứng minh (JVM):** vòng mã hóa và giải mã RCS2; RCS1 cũ vẫn giải mã được (vector cố định tạo bởi **mã Python độc lập** `hashlib.pbkdf2_hmac` + AES-GCM chuẩn… **không có thư viện `cryptography` trong Python ở máy này**, nên vector RCS1 được tạo bằng chính mã RCS1 **trước khi sửa** (commit hiện tại) và đóng băng thành hằng trong test; vector HKDF được kiểm bằng **mô hình Python độc lập** (`hmac` + `hashlib`, chỉ chuẩn thư viện) cho đúng 32 byte khóa); sai secret, đổi từng vùng byte, tag, AAD buộc RCS2 vào RCS2, độ dài, quá lớn, không lộ bản rõ; RCS2 nhanh hơn rõ rệt RCS1.
- **Không chứng minh:** tương thích với app cũ trên máy thật (app cũ **không đọc được** RCS2: nói thẳng); an toàn khi người dùng tự đặt mã yếu ở máy phát (không có đường đó trong app; xem trên); thời gian trên máy thật chỉ đo lại ở một máy Pixel.
- **Nói thẳng (thay đổi tương thích):** sau khi cập nhật, **máy phát mới chỉ ghi RCS2**; máy nhận chưa cập nhật **không đọc được** gói đó (chỉ hiện lỗi gói). Cần **cả hai máy** cập nhật app. Ngược lại máy nhận mới vẫn đọc gói RCS1 từ máy phát cũ.

## Kiểm chứng
- TDD: test đỏ trước (đúng lý do), rồi mã, rồi xanh. Mọi test cũ của `SyncEnvelopeCryptoTest` giữ ý nghĩa; test "quá lớn bị loại trước khi suy khóa" viết lại (so với thời gian một lần giải mã thật vẫn đúng: oversize phải nhanh hơn một lần giải mã RCS1; kiểm trên đường RCS1 giữ nguyên PBKDF2).
- Test mới: RCS2 vòng khứ hồi; encrypt ghi `RCS2`; RCS1 đóng băng vẫn giải mã; magic lạ bị loại; **đổi `RCS2` thành `RCS1` trong header làm giải mã thất bại** (AAD/khóa khác); salt đổi thì khóa đổi (hai gói cùng secret có tiền tố khác); RCS2 giải mã nhanh hơn RCS1 ít nhất 20 lần (đo trên máy dev, tỉ lệ không phải mili giây tuyệt đối; đo thật trước khi chốt ngưỡng).
- Đột biến JVM (bảng dự đoán, suy luận từng test): dùng `salt` làm `info`; bỏ `info`; HKDF Expand dùng `secret` thay `PRK`; encrypt ghi `RCS1` nhãn nhưng khóa HKDF; decrypt RCS2 đi đường PBKDF2; bỏ kiểm magic; bỏ AAD; mỗi đột biến làm đúng test dự định đỏ.
- JVM full `--rerun-tasks` đo lại (số trước 3.908). Diff production: `SyncEnvelopeCrypto.kt` (+ có thể `LocalSyncViewModel.kt` chỉ khi cần, dự kiến không). Test thiết bị: chạy lại `AutoReceiveTwoPhonesDeviceTest` trên Pixel (15 lượt) để đo lại thời gian, cổng + sao lưu byte-exact như 05e.

## Rủi ro đã biết
- **Đổi mã hóa:** sai một chi tiết HKDF làm hai máy không đọc nhau (đã chặn bằng vector độc lập + test chéo RCS1/RCS2). Không tự chế mã: chỉ HKDF chuẩn RFC 5869 bằng HMAC có sẵn.
- **Máy chưa cập nhật mất khả năng nhận từ máy mới:** nói thẳng ở báo cáo; có thể gây bất ngờ khi chỉ cập nhật một máy.
- **Người dùng tự gõ mã yếu ở đầu nhận** không hại gì cho mã hóa của gói (khóa dùng secret của máy phát); nhưng một máy **nhận** có thể được đưa secret yếu rồi giả làm máy phát cho chính nó: không phải mối đe dọa mới.
- **Hiệu ứng lên bài test cũ** dùng thời gian PBKDF2 (`oversizeEnvelopeIsRejectedBeforeAnyKeyDerivation`): sẽ phải đổi.

## Giới hạn đã biết
- Chỉ JVM cho mã hóa; thời gian trên Pixel đo một máy. Tiêu chí 3 giây giữa **hai** máy vẫn chưa đo được (cần máy thứ hai).
