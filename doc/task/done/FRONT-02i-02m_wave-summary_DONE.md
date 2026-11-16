# Tổng kết đợt FRONT-02i đến FRONT-02m

**Ngày:** 2026-11-16 · **Nhánh:** `dev` (không push) · **Commit đầu → cuối:** `fe8135a8` … `923b1836`

## Một câu cho mỗi việc
| Việc | Kết quả | Bằng chứng |
|---|---|---|
| **02i** Chặn đăng ký trùng một tài khoản Bluesky (RSS cũ và API mới) | **Xong, có sửa mã.** Đăng ký trùng ở một trong hai dạng thì báo "Đã đăng ký", trước khi gọi mạng. | +12 test, mutation 10/10, JVM chạy lại sạch |
| **02j** Tên và icon nguồn hiện đúng trên Pixel | **Xong, chỉ thêm test.** `FeedIcon` thật và `Text` cùng kiểu `FeedItem`. | 5 test, 20/20 lượt xanh, mutation 4/4 |
| **02k** Relay Nostr nhận yêu cầu rồi im lặng | **Xong, chỉ thêm test.** Đồng bộ báo lỗi gọn trong hạn, rồi hồi phục. | +2 test (10 cho cả lớp), 20/20 lượt xanh, mutation 3/3 |
| **02l** `FeedItem` nguyên bản trên Pixel, không Hilt | **Xong, chỉ thêm test.** Tên, cắt tên, icon, huy hiệu, bấm, ẩn dòng. | 7 test, 20/20 lượt xanh, mutation 5/5 |
| **02m** Điều tra lỗi gõ chữ chập chờn của 02d | **Chưa kết luận gốc.** Lỗi tái hiện ~1% (4/400) ngoài `MainActivity`; còn nghi cuộc đua gõ và focus hoặc `reset()` muộn. | bộ phân loại 11 test JVM, mutation 10/10 |

## Thay đổi mã production (toàn đợt)
Chỉ **02i**, tổng **hai tệp, 10 dòng thêm, 1 dòng đổi** (`SubscribeViewModel.kt`, `BlueskyFeedMapper.kt`). 02j, 02k, 02l, 02m **không đổi** mã production, manifest, chuỗi, Room (v22), DataStore, `app/build.gradle`; không dependency mới.

## Số liệu cuối
- JVM full suite `--rerun-tasks`: **3.731** test, 0 lỗi, 0 bỏ qua (đo thật; 3.720 sau 02i, +11 của bộ phân loại 02m).
- Lint (đo sau 02i): 0 lỗi, 2.465 cảnh báo. Lint không quét `androidTest`.
- Trên Pixel 7 Pro: DataStore giống hệt trước và sau ở **mọi** vòng chạy; khôi phục byte-exact sau mỗi vòng và sau mỗi batch đột biến.
- Tổng test trên máy mới thêm: 02j 5, 02k +2, 02l 7 (cộng 14), mỗi bộ chạy lặp 20 lượt xanh.

## Những điều CHƯA chặn hoặc CHƯA kiểm (gom từ các báo cáo)
- **02i:** đăng ký trùng khi một bên là handle và bên kia là DID của cùng người; nguồn RSS từ OPML hoặc địa chỉ dạng khác; các cặp trùng đã có sẵn trong máy.
- **02j, 02l:** danh sách nguồn thật, bấm giữ và ngăn kéo, dark mode, e-ink, chữ lớn, icon **https** của Bluesky trên máy (máy chủ giả là http), icon lỗi tải.
- **02k:** con số 8 giây của sản phẩm; relay treo lúc bắt tay, hai relay, relay trả một nửa (chỉ có test JVM); mutant treo vĩnh viễn không chạy trên máy.
- **02m:** gốc lỗi gõ chữ; 2 lô mất vì lỗi probe, 4 lô chưa chạy, chưa chạy chế độ `auto`, probe bản sửa chưa chạy trên máy.
- Chung: mạng thật, `sync()`/`SyncWorker` thật, một máy (Pixel 7 Pro, Android 17), không so với máy khác.

## Điều cần owner biết
1. **Pixel đang mở app `loyalty.skyjoy.vn.dev`** của bạn; vòng đo 02m dừng vì thế (cổng báo dừng, tôi không chạm máy).
2. **Hai tệp probe chưa commit** trong cây làm việc: `app/src/androidTest/.../subs/TypingFlakeProbeTest.kt` và `TypingFlakeClassifier.kt` (bản sao dùng ở androidTest). Có thể commit hoặc xóa; báo cáo 02m ghi cách mở lại.
3. **Pixel `user_rotation`** hiện là 0 (FRONT-02e từng để 1); không do tôi sửa.

## Việc tiếp có thể làm (không ưu tiên, theo thứ tự rủi ro tăng dần)
1. Mở lại 02m khi Pixel rảnh khoảng 40 phút: 4 lô × 100 lượt cho `manual` và `auto`.
2. Test bấm giữ `FeedItem` và ngăn kéo `FeedOptionDrawer` (cần môi trường thêm, có thể ghi dữ liệu).
3. Chặn trùng handle với DID (cần hỏi mạng).
4. Các mục còn lại của FRONT-02 trong `doc/task/todo/`.
