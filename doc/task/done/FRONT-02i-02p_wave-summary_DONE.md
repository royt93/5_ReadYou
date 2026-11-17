# Tổng kết đợt FRONT-02i đến FRONT-02q (tên tệp giữ `02p` cho khỏi gãy liên kết)

**Ngày:** 2026-11-17 · **Nhánh:** `dev` (không push) · **Commit đầu → cuối:** `fe8135a8` … `3c9b7d47` (37 commit, gồm spec, plan, mã, test, báo cáo)

## Một câu cho mỗi việc
| Việc | Kết quả | Bằng chứng |
|---|---|---|
| **02i** Chặn đăng ký trùng một tài khoản Bluesky (RSS cũ và API mới) | **Xong, có sửa mã.** Cùng dạng tên (handle với handle, DID với DID) thì báo "Đã đăng ký" trước khi gọi mạng. | +12 test, mutation 10/10 |
| **02j** Tên và icon nguồn hiện đúng trên Pixel | **Xong, chỉ thêm test.** | 5 test, 20/20 lượt xanh, mutation 4/4 |
| **02k** Relay Nostr nhận yêu cầu rồi im lặng | **Xong, chỉ thêm test.** | +2 test, 20/20 lượt xanh, mutation 3/3 |
| **02l** `FeedItem` nguyên bản trên Pixel, không Hilt | **Xong, chỉ thêm test.** | 7 test, 20/20 lượt xanh, mutation 5/5 |
| **02m** Điều tra lỗi gõ chữ chập chờn của 02d | **Đóng, không có gốc.** Tái hiện ~1 đến 1,5% (4/400 rồi 3/199) ngoài `MainActivity`; chưa biết ai xóa chữ. | bộ phân loại 11 test JVM, mutation 10/10 |
| **02n** Chặn trùng khi một bên gõ handle, một bên gõ DID | **Xong, có sửa mã.** Dùng cả hai tên mà API trả về, không thêm lần gọi mạng. | +20 test, mutation 20/20 |
| **02o** Nhớ DID của nguồn Bluesky mới | **Xong, có sửa mã.** Chặn cả ca chủ tài khoản đổi handle (với nguồn đăng ký mới). | +16 test, mutation 13/13 |
| **02p** Menu sửa địa chỉ nguồn đổi cả địa chỉ đồng bộ | **Xong, có sửa mã.** Sửa tật có sẵn mà 02o làm lộ rõ. | +9 test, mutation 7/7 |

## 02q (thêm sau khi viết bản này)
**02q** Nâng cấp nguồn Bluesky cũ lên lưu DID khi đồng bộ thành công: **xong, có sửa mã** (`LocalRssSv.kt`, một hàm DAO `updateSourceUrlIfUnchanged`, không đổi schema). +21 test, mutation 12/12, 11 lớp liên quan 165 test xanh.

**Cập nhật cuối ngày 2026-11-17:** full suite đỏ sau 02q có gốc là test `FeedOptionViewModelTest` của **02p** (gọi `showDrawer`); đã sửa ở `3dd97401`. Đồng thời sửa một lỗi phụ: `ReelsViewModelTest` đỏ chập chờn vì hai lớp test khởi động `RApp` thật (`9fe2b102`). **Full suite hiện xanh: 3.797 test, 0 lỗi, 3 lần thật không cache.** Chi tiết và những gì chưa giải thích ở báo cáo 02q.

(Lịch sử lúc viết: sau 02q full suite đỏ ba lần, nguyên nhân khi đó chưa biết.)

## Thay đổi mã production, theo từng việc
Tổng cả đợt **5 tệp, 62 dòng thêm, 3 dòng bỏ**. 02j, 02k, 02l, 02m **không đổi** mã production.

| Việc | Tệp | Cỡ |
|---|---|---|
| 02i | `SubscribeViewModel.kt`, `BlueskyFeedMapper.kt` | 10 dòng thêm, 1 đổi |
| 02n | `BlueskyFeedMapper.kt` (hàm `otherNameAddresses`), `SubscribeViewModel.kt` (khối 13 dòng) | cộng vào các tệp trên |
| 02o | `FeedDao.kt` (một truy vấn), `AbstractRssRepository.kt`, `SubscribeViewModel.kt` | 19 dòng thêm, 2 dòng bỏ |
| 02p | `FeedOptionVM.kt` | 3 dòng thêm, 1 dòng bỏ |

**Không đổi** cả đợt: manifest, chuỗi và locale, `app/build.gradle`, dependency, **schema Room** (`app/schemas` rỗng khi so `git diff`, không migration), DataStore; không Gemini, ad, IAP, VIP.

## Số liệu cuối (đo thật)
- JVM full suite `--rerun-tasks`: **3.776** test, 0 lỗi, 0 bỏ qua (3.720 sau 02i → 3.731 sau 02m → 3.751 sau 02n → 3.767 sau 02o → 3.776 sau 02p).
- Lint (đo sau 02p): **0 lỗi, 2.465 cảnh báo** (không đổi so với trước đợt). Lint không quét `androidTest`. `assembleDevDebug` thành công.
- Trên Pixel 7 Pro (02j đến 02m): DataStore giống hệt trước và sau ở **mọi** vòng chạy; khôi phục byte-exact sau mỗi vòng và sau mỗi batch đột biến.
- **Mutation toàn đợt:** hầu hết mutant bị bắt ngay. **Các mutant sống sót đều được xử lý đúng bản chất**, không cái nào bị bỏ qua: 02n bỏ `distinctBy` là **mã thừa** (đã xóa) và bỏ `startsWith("did:")` là **thiếu test** (đã thêm test); 02o repo dùng sai tài khoản sống sót ở test DAO nên đã ghi rõ trong thân commit rồi **thêm test repo** và chạy lại, bị bắt. Lần nào đoán sai (ví dụ cho rằng cả hai mutant của 02n là mã thừa) đều đã sửa sau khi đọc kết quả.

## Những điều CHƯA chặn hoặc CHƯA kiểm (gom từ các báo cáo)
- **Trùng nguồn Bluesky (02i, 02n, 02o):** API lỗi hoặc `Found` rỗng (rơi về RSS) thì không có tên thứ hai nên không chặn; **nguồn đăng ký trước 02o** vẫn có handle ở cả hai cột nên ca đổi handle của chúng vẫn trùng (giới hạn chỉ thu hẹp dần); nguồn nhập từ OPML hoặc địa chỉ dạng khác; cặp trùng có sẵn; một máy chủ độc trả handle hoặc DID giả chỉ có thể chặn **một** lần đăng ký.
- **02p:** địa chỉ sai ở Bluesky và Nostr nay làm đồng bộ báo lỗi (trước đây vô hại; không mất bài cũ); nguồn Bluesky mới mất DID đã nhớ sau khi sửa; không kiểm địa chỉ trong hộp thoại (cần chuỗi mới); Fever và Google Reader vẫn không sửa được (giao diện chặn); chưa thử trên màn hình.
- **02j, 02l:** danh sách nguồn thật, bấm giữ và ngăn kéo, dark mode, e-ink, chữ lớn, icon **https** của Bluesky trên máy (máy chủ giả là http), icon lỗi tải.
- **02k:** con số 8 giây của sản phẩm; relay treo lúc bắt tay, hai relay, relay trả một nửa (chỉ có test JVM); mutant treo vĩnh viễn không chạy trên máy.
- **02m:** gốc lỗi gõ chữ; chưa chạy chế độ `auto`; chưa đo bên trong ViewModel (bạn chọn không thêm nhật ký chẩn đoán).
- **Chung:** 02i, 02n, 02o, 02p **chỉ JVM, chưa kiểm trên máy**; mạng thật, `sync()` và `SyncWorker` thật; một máy (Pixel 7 Pro, Android 17), không so với máy khác.

## Điều cần owner biết
1. **Pixel đang mở app `loyalty.skyjoy.vn.dev`** của bạn (cổng kiểm gần nhất vẫn báo dừng); vòng đo 02m dừng vì thế, tôi không chạm máy.
2. **Hai tệp probe 02m đã commit** (bạn chọn giữ): `TypingFlakeProbeTest.kt` và bản sao `TypingFlakeClassifier.kt` ở androidTest (phải trùng bản ở `src/test`). Mặc định 5 lượt nên chạy tình cờ không tốn thời gian. Bản mới đã chạy trên máy ở vòng đo thứ hai (199 lượt).
3. **Pixel `user_rotation`** hiện là 0 (FRONT-02e từng để 1); không do tôi sửa.
4. **Các tệp `.idea`** vẫn nằm ngoài commit theo dặn.

## Việc tiếp có thể làm (theo thứ tự rủi ro tăng dần)
1. Mở lại 02m chỉ khi bạn đồng ý đo bên trong ViewModel (nhật ký tạm, không commit) hoặc chạy chế độ `auto` (~40 phút Pixel).
2. Thêm kiểm địa chỉ trong hộp thoại sửa địa chỉ nguồn (cần quyết chuỗi báo lỗi; hiện bạn đã dặn không chuỗi mới).
3. Test bấm giữ `FeedItem` và ngăn kéo `FeedOptionDrawer` (cần môi trường thêm, có thể ghi dữ liệu).
4. Nâng cấp nguồn Bluesky cũ lên lưu DID (cần quyết cách làm: tự động khi đồng bộ thành công, hay không làm).
5. Các mục còn lại của FRONT-02 trong `doc/task/todo/`.
