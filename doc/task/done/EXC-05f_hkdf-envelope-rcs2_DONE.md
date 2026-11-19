# EXC-05f (partial; EXC-05 remains in progress): gói đồng bộ RCS2 dùng HKDF, vẫn đọc được RCS1

**Date:** 2026-11-19
**Branch:** `dev`
**Device:** chỉ **Pixel 7 Pro `2B051FDH3006MU`** (Android 17), để đo lại thời gian. Không có máy thứ hai.
**Scope:** mã production đổi **đúng một tệp**: `domain/p2psync/SyncEnvelopeCrypto.kt`. Test đổi đúng một tệp: `SyncEnvelopeCryptoTest.kt`. Không đổi giao thức NSD/token, `PairingSecret`, `LocalSyncViewModel`, giao diện, chuỗi, Room (v20 trong EXC-05; DB thật là v22), DataStore, manifest, `app/build.gradle`; không dependency mới (HMAC có sẵn trong JCA).
**Spec:** `docs/superpowers/specs/2026-11-19-exc-05f-hkdf-envelope-rcs2-design.md`. **Không có plan riêng** (spec duyệt xong làm thẳng: test đỏ, mã, đột biến, JVM đầy đủ, đo thiết bị).

## Vì sao làm (đo thật trên Pixel)
EXC-05e đo từ lúc bắt đầu đến xem trước **4,45 đến 4,83 s**, chưa đạt 3 giây của EXC-05 gốc. Trong đó **PBKDF2 310.000 vòng tốn 1,71 đến 1,85 s khi mã hóa và 1,51 đến 1,57 s khi giải mã** (3 lượt đo trên Pixel bằng test sẵn có `measuresRealPbkdf2CostOnThisDevice`). Secret ghép đôi là 128 bit ngẫu nhiên do máy phát sinh (`PairingSecret.generate()`), nên không có mật khẩu yếu để làm chậm: chuẩn dùng cho khóa đã mạnh là HKDF.

## Việc đã làm
- `encrypt` **luôn ghi `RCS2`**: nhãn `RCS2` + version 2 + salt 16 B + nonce 12 B + AES-256-GCM (tag 128-bit). Khóa = **HKDF-SHA256 (RFC 5869)**: `PRK = HMAC-SHA256(salt, 16 byte secret)`, khóa = `HMAC-SHA256(PRK, "RCS2 envelope key" || 0x01)` (32 byte). Cùng độ dài header với RCS1, nên `OVERHEAD_BYTES` và giới hạn 8 MiB không đổi.
- `decrypt` chọn đường suy khóa **chỉ theo nhãn + version**: `RCS2` đi HKDF, `RCS1` đi PBKDF2 cũ (mã giữ nguyên, đặt tên `deriveKeyRcs1`), nhãn hay version lạ bị loại **trước khi suy khóa**. Nhãn và version nằm trong AAD của GCM, nên không thể đổi nhãn RCS2 thành RCS1 để ép đường chậm mà không làm hỏng tag.
- **Vector độc lập với mã Kotlin:** gói RCS1 và RCS2 cố định được tạo bằng một chương trình Java riêng (chỉ gọi JCA), và khóa HKDF được **tính lại bằng Python** (`hmac`, `hashlib`): hai kết quả giống hệt (`a54479391d05cc10142a153e0806a0a66e55d843b023b19d7870ac776e7bcc3b`). Test đọc đúng cả hai vector.

## Kiểm chứng (đo thật)
- **TDD:** các test RCS2 đỏ trước trên mã cũ (đỏ đúng ba: đọc vector RCS2, ghi RCS2, tốc độ), rồi xanh sau khi thêm mã. Cả lớp `SyncEnvelopeCryptoTest` (**17 test**) và mọi lớp `p2psync` (domain, UI, infrastructure) xanh.
- **Đột biến JVM 9 nhãn (+1 nhãn thêm), tất cả khớp sau khi sửa dự đoán và test:** h1 đổi `info` thành salt, h2 bỏ byte khối `0x01`, h3 đổi chỗ salt và secret trong Extract (cả ba đỏ đúng hai test: đọc vector RCS2 và tốc độ); h4 decrypt luôn dùng PBKDF2, h6 bỏ nhãn khỏi AAD khi giải mã, h7 bỏ nhãn khỏi AAD khi mã hóa, h8 mã hóa ghi nhãn RCS1 với khóa RCS2 (bốn nhãn này làm đỏ 39 đến 44 test ở cả `SyncEnvelopeCryptoTest` và các lớp ViewModel, vì mọi vòng khứ hồi hỏng); h5 bỏ kiểm version RCS2, h5b bỏ kiểm version RCS1, h9 bỏ kiểm nhãn hợp lệ (cả ba đỏ đúng `anUnknownLabelOrVersionIsRejectedBeforeAnyKeyDerivation`). Mã khôi phục giống hệt (`cmp`), `git diff` production rỗng.
- **Hai chỗ test yếu lộ ra, đã sửa (không bỏ qua):** (1) h5 (bỏ kiểm version) **sống sót lần đầu** vì thẻ GCM vẫn loại gói sai version sau khi tốn một lần suy khóa; thêm test `anUnknownLabelOrVersionIsRejectedBeforeAnyKeyDerivation` (gói nhãn lạ, RCS1 sai version, RCS2 sai version đều phải bị loại nhanh hơn một lần suy khóa cũ ít nhất 20 lần), rồi chạy lại h5, h5b thì đỏ đúng. (2) Test đó lần đầu chưa có ca RCS2 sai version, nên h5 chỉ bị bắt nửa vời; đã thêm. Dự đoán của mô hình tay cũng sai ở h1 đến h3 (đỏ thêm test tốc độ vì khóa sai làm `AEADBadTagException`) và h5 (mô hình nói không đỏ vì AAD che; thực tế lộ ra điểm yếu về thời gian); bảng đã sửa theo lý do đó.
- **Test cũ phải đổi:** `oversizeEnvelopeIsRejectedBeforeAnyKeyDerivation` đo bằng một lần PBKDF2; vì khóa RCS2 gần bằng 0 nên nay dùng một gói **RCS1** làm thước đo (đúng ý: gói quá lớn phải bị loại trước khi tốn một lần suy khóa chậm).
- **Đo ngưỡng tỉ lệ:** JDK trên máy dev: PBKDF2 310.000 vòng 222 ms, HKDF 3 µs, tỉ lệ ~68.000 lần; ngưỡng test 20 lần.
- JVM `testDevDebugUnitTest --rerun-tasks assembleDevDebug assembleDevDebugAndroidTest`: **3.917 test, 0 lỗi, 0 bỏ qua** (3.908 + 9), BUILD SUCCESSFUL.
- **Đo lại trên Pixel (RCS2), 12 lượt, 12/12 xanh** (mỗi lượt đóng SkyJoy rồi cổng chỉ đọc `GATE OK` ngay trước, sao lưu byte-exact, khôi phục `RESTORE OK`, băm DataStore trùng `ed9f68879fc775bb5fb1b5f24206a6c1a924bcee`, quyền `ACCESS_LOCAL_NETWORK` thu hồi, APK test gỡ): **bắt đầu đến xem trước 1.262 đến 1.698 ms, trung vị 1.490 ms** (trước: 4.452 đến 4.833 ms). Chia nhỏ: bên phát sẵn sàng 211 đến 322 ms (trước 1.819 đến 2.018); tìm đến xem trước 1.039 đến 1.372 ms (trước 2.744 đến 2.863). **Dưới 3 giây trên một máy.**

## Giới hạn và thay đổi tương thích (nói thẳng)
- **App cũ không đọc được gói mới.** Máy phát đã cập nhật chỉ ghi `RCS2`; máy nhận **chưa cập nhật** (chỉ đọc RCS1) sẽ loại gói ngay ở bước nhãn và hiện lỗi gói như với gói hỏng. **Cả hai máy phải cập nhật app.** Máy nhận mới vẫn đọc được gói RCS1 từ máy phát cũ.
- **An toàn của HKDF dựa vào việc secret luôn là 128 bit ngẫu nhiên do máy phát sinh.** `PairingSecret.fromHex` ép đúng 32 ký tự hex nhưng **không** ép "ngẫu nhiên": một mã yếu do người dùng gõ sẽ được chấp nhận ở máy nhận. Trong app, mọi secret mã hóa đến từ `PairingStore` hoặc `PairingSecret.generate()` (đã đọc `LocalSyncViewModel`: không có đường mã hóa bằng mã người gõ), nhưng nếu sau này có đường cho phép người dùng tự chọn mã thì **phải quay lại PBKDF2**. Đã ghi vào KDoc của `SyncEnvelopeCrypto`.
- **Không phải hai máy:** thời gian 1,5 s vẫn đo trên **một máy** (hai ViewModel cùng máy), chưa có độ trễ radio giữa hai máy; tiêu chí 3 giây giữa hai máy thật chưa kiểm chứng được.
- Chưa kiểm app cũ nhận gói RCS2 trên máy thật (chỉ lập luận và test JVM với nhãn lạ).
- Task gốc EXC-05 còn: hai máy thật, tự phát nền, ACK.
