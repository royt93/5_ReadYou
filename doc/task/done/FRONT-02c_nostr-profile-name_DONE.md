# FRONT-02c (Dùng tên hồ sơ Nostr làm tên nguồn tin)

**Date:** 2026-11-15  
**Branch:** `dev` (không push)  
**Scope:** khi dán `npub1…` vào ô **Thêm nguồn**, app hỏi thêm hồ sơ Nostr công khai (loại 0) **trong cùng một kết nối** và đặt tên nguồn là `Nostr · <tên>` nếu tìm được; không có hồ sơ thì giữ `Nostr · npub1…` như trước. Chỉ đọc, không đăng nhập, không giữ khóa. Room **giữ v22**, không chuỗi mới, không dependency mới.  
**Spec / plan:** `docs/superpowers/specs/2026-11-15-front-02c-nostr-profile-name-design.md`, `docs/superpowers/plans/2026-11-15-front-02c-nostr-profile-name.md`.  
**Trạng thái task gốc:** vẫn nằm trong `doc/task/todo/` (owner chọn).

## Nói thẳng về phạm vi

- **Tên tự khai, KHÔNG xác minh.** Chủ tài khoản đặt tên tùy ý, nên có thể giả tên người nổi tiếng. App không kiểm `nip05` và không xác minh danh tính. Tiền tố `Nostr · ` luôn được giữ để người dùng thấy đây là tên tự khai trên Nostr.
- **Chỉ lấy tên.** Không ảnh đại diện, không mô tả, không địa chỉ thanh toán.
- **Chỉ đặt tên lúc tìm và xem trước.** Đồng bộ nền **không** đổi tên nguồn đã lưu (người dùng có thể đã đổi tên tay), và hồ sơ đổi sau đó không được cập nhật. Có test hồi quy giữ điều này.
- **Riêng tư:** relay thêm một bộ lọc trong cùng `REQ`; chúng đã biết `npub` đang đọc nên không lộ thêm gì ngoài việc hỏi hồ sơ.
- Quy tắc tên: `display_name`, rỗng thì `name`; mọi ký tự điều khiển hoặc khoảng trắng đổi thành một dấu cách rồi gộp; tối đa **40 điểm mã Unicode** (không cắt giữa emoji); trống thì coi như không có tên. `U+200B` (ký tự định dạng, không phải điều khiển hay khoảng trắng) **không** bị bỏ.
- Hồ sơ không hợp lệ (sai id, sai chữ ký, sai tác giả) bị bỏ như ghi chú sai. Nhiều hồ sơ: lấy cái **mới nhất mà có tên dùng được**.

## Đã làm (`infrastructure/social/nostr/`)

- `NostrProfileName.fromContent(json)`: đọc tên an toàn từ `content` hồ sơ (hàm thuần).
- `NostrEvent.PROFILE_KIND = 0` và `isValidProfile(pubkey)`: kiểm hồ sơ riêng; **`isValid` của ghi chú không bị nới** (hồ sơ không bao giờ lọt làm ghi chú; có test và đột biến cho ranh giới này).
- `NostrFetchResult(notes, profileName)`; `NostrRelayClient.fetch` gửi **một `REQ` hai bộ lọc** (`kinds:[1] limit 50` và `kinds:[0] limit 1`), cùng subscription `f2`, cùng EOSE, timeout, giới hạn khung và thứ tự đóng socket như trước. Mọi nơi gọi đã đổi theo.
- `NostrFeedMapper.feedName(npub, profileName)` (hàm một tham số giữ nguyên); `SubscribeViewModel.search()` dùng tên cho bản xem trước.

## Kiểm chứng (JVM)

- Full suite `--rerun-tasks`: **3.627 test, 0 bỏ qua, 0 lỗi** (thêm 47 so với 3.580).
- Test mới: `NostrProfileNameTest` 17, `NostrProfileEventTest` 11, `NostrRelayProfileTest` 12, thêm vào `NostrFeedMapperTest` 2, `LocalRssSvNostrSyncTest` 1, `SubscribeViewModelNostrProfileTest` 4. Các lớp relay/mạng/rò rỉ cũ vẫn xanh sau khi đổi kiểu trả về.
- Chạy bốn lần liên tiếp dưới tải CPU (20 vòng lặp bận): 47 test relay xanh 4/4.
- TDD thật: mỗi task có bước đỏ đọc lý do (thiếu hàm), rồi mã tối thiểu, rồi xanh.
- Mutation (backup + `cmp`, tệp luôn khôi phục giống hệt): Task 1: 14 và 2 lần chạy lại; Task 2: 9; Task 3: 11; Task 4: 8. Kết quả cuối: mọi đột biến đều bị test bắt, **trừ hai đột biến không đổi hành vi** (không bắt được và không cần bắt): `isWhitespace` bị bỏ (`m06`, guard thừa nên đã gỡ khỏi mã) và `profileName.isNullOrEmpty()` thay cho `== null` ở `feedName` (`m03` Task 4, tương đương vì `fromContent` không bao giờ trả chuỗi rỗng; không chạy lại). Các đột biến sống sót khác làm lộ test yếu, đã thêm test rồi chạy lại và bị bắt: `m07` (`U+2007`/`U+202F`), `m13` (`trimEnd`), `m05` Task 3 (sắp xếp theo `created_at`), `m08` Task 4 (hủy tìm kiếm). Thêm test: cắt rơi ngay sau dấu cách.
- Lint: 0 lỗi, 2.465 cảnh báo (bằng cũ), 0 vấn đề trong tệp mới. `assembleDevDebug assembleDevDebugAndroidTest` thành công. `app/schemas` và `app/src/main/res` không đổi.

## Kiểm chứng trên máy (Pixel 7 Pro `2B051FDH3006MU`, 2026-11-15)

- **Máy khác kế hoạch.** Kế hoạch ghi Samsung A50s, nhưng A50s không còn trên adb lúc kiểm; tôi hỏi và bạn cho phép Pixel 7 Pro. Các số liệu này **không so sánh được** với Tecno KJ7 hay A50s.
- Cổng chỉ đọc: launcher ở foreground, mở khóa, không instrumentation. Sao lưu byte-exact (Room + `-shm` + `-wal`, DataStore 20 byte, `shared_prefs`).
- Helper tạm (**không commit**, đã xóa) chạy bằng `am instrument`, **không chạm màn hình**, không MockK, dựng `OkHttpClient` sao chép cấu hình `OkHttpClientModule` (DoH, interceptor), gọi **relay thật**. Log chỉ ghi số lượng và độ dài tên. Kết quả 2/2 OK:
  - Khóa có hồ sơ: **6 ghi chú đã xác minh**, **có tên, dài 4 điểm mã**, 1,47 giây.
  - Khóa không có hồ sơ: 1 ghi chú, **không có tên**, 1,49 giây (rơi về tên cũ).
- Khôi phục: Room, `-shm`, `-wal`, DataStore, `shared_prefs` đều `BYTE IDENTICAL`; băm DataStore trên máy `ed9f6887…` trùng bản sao lưu; đã gỡ `com.mckimquyen.reader.test`; cây mã sạch.

## Chưa kiểm (nói thẳng)

- **Tên thật hiển thị trong hộp thoại trên màn hình**: không chạm màn hình, chỉ kiểm bằng test view model và test hộp thoại trên máy tính.
- Relay khác hai relay cố định; hồ sơ đổi theo thời gian; hồ sơ bị xóa.
- `nos.lol` ngày 2026-11-15 báo lỗi bắt tay (handshake) từ máy Mac cho cả yêu cầu một bộ lọc lẫn hai bộ lọc, nên bằng chứng "hai bộ lọc trong một `REQ`" đến từ `relay.primal.net`, không từ `nos.lol`. Fallback sang relay còn lại đã được test.
- TalkBack; ngôn ngữ ngoài EN/VI; máy yếu (A50s) khi kiểm chữ ký thêm một hồ sơ.

## Giới hạn

- Tên chỉ đặt một lần lúc tìm; không tự cập nhật; có thể giả.
- Hai relay có thể có hồ sơ cũ hoặc không có.
- Một khóa không có hồ sơ vẫn hiện `Nostr · npub1…`.
- `U+200B` trong tên không bị loại.

## Commits trên `dev`

- `854ec392` quyết định owner vào spec · `789d1057` kế hoạch
- `398728f3` đọc tên hồ sơ · `81b7168e` kiểm hồ sơ kind 0
- `9f01a242` một REQ hai bộ lọc · `e1ce315f` tên vào bản xem trước
