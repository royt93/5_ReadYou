# FRONT-02t (Test máy thật: sửa địa chỉ nguồn rồi đồng bộ)

**Date:** 2026-11-18
**Branch:** `dev` (không push)
**Scope:** chỉ thêm test trong `app/src/androidTest` (tệp mới `FeedAddressEditDeviceTest.kt`; `BlueskyGuardRig.kt` sửa nhẹ). **Không đổi mã production**, manifest, chuỗi, Room (v22), DataStore, `app/build.gradle`, dependency. Không mạng thật.
**Spec / plan:** `docs/superpowers/specs/2026-11-18-front-02t-feed-address-edit-device-test-design.md`, `docs/superpowers/plans/2026-11-18-front-02t-feed-address-edit-device-test.md`.

## Điều test chứng minh
Trên **Tecno KJ7 (`115333744A005844`, Android 14)**, với Room thật, `FeedOptionViewModel` thật và máy chủ Bluesky giả trên `127.0.0.1`:
1. `aNewBlueskySource_changesBothColumns_andTheNextSyncAsksForTheNewAddress`: nguồn Bluesky có DID cũ; sửa địa chỉ thành handle mới thì **cả `url` và `sourceUrl`** là địa chỉ mới (FRONT-02p); lần đồng bộ kế hỏi máy chủ **chỉ về tài khoản mới** (`profileActors() == [handle mới]`), rồi `sourceUrl` lên **DID mới** (FRONT-02q), `url` giữ địa chỉ gõ, có bài, không yêu cầu nào ra ngoài `127.0.0.1`.
2. `anRssSource_changesOnlyItsUrl`: RSS thường chỉ đổi `url`, `sourceUrl` vẫn rỗng.
3. `everyOtherField_isKept`: `id`, `name`, `icon`, `groupId`, `accountId`, `isNotification`, `isFullContent`, `sourceType` giữ nguyên (nguồn gieo có icon, thông báo và tải đủ nội dung bật).
4. `afterCleanUp_nothingOfTheProbeIsLeftAnywhere_andTheRealDataIsUntouched`: không sót dòng thử, dữ liệu thật của máy không đổi.

Cách làm: nạp nguồn vào state bằng reflection (`_feedOptionUiState`), gọi `showFeedUrlDialog`, `inputNewUrl`, `changeFeedUrl` (đúng thứ tự người dùng làm), chờ hộp thoại đóng (nó đóng sau khi `updateFeed` xong). **Không** gọi `showDrawer` (báo cáo 02p: làm đồng hồ Compose bận), **không** `MainActivity`, **không** gõ qua giao diện.

## Số đo trên Tecno (không gõ lại, không tăng timeout, không sleep)
| Vòng | Lượt | Xanh | Đỏ | Có `AdActivity` |
|---|---|---|---|---|
| thử | 1 | 1 | 0 | 0 |
| vòng 1 | 20 | 20 | 0 | 0 |
| vòng 2 | 20 | 20 | 0 | 0 |
| **Cộng** | **41** | **41** | **0** | **0** |

0 lượt đỏ trong 41: cận trên 95% của tỉ lệ đỏ khoảng 7% (không loại trừ mức nhỏ). Xoay `1 0` và băm DataStore `9ca1c202050c258b4aac9a063e27def350d737fb` không đổi sau mọi vòng; sao lưu/khôi phục byte-exact OK; `user_rotation=0`; gói test gỡ cuối mỗi vòng.

## Mutation chạy trên máy (đổi tạm, build, cài, chạy, khôi phục, `cmp`)
| Đột biến | Test đỏ (đo được) | Dòng đỏ |
|---|---|---|
| base (đầu và cuối) | không (`OK (4 tests)`) | |
| t1 chỉ ghi `url` | `aNewBlueskySource_…` | 134 (`saved.sourceUrl`) |
| t2 luôn ghi `sourceUrl` | `anRssSource_changesOnlyItsUrl` | 154 (`assertNull`) |
| t3 giữ `sourceUrl` cũ | `aNewBlueskySource_…` | 134 (cùng điểm chết với t1) |
| t4 bỏ nâng cấp DID (`LocalRssSv`) | `aNewBlueskySource_…` | **141 (`synced.sourceUrl`, sau đồng bộ)** |
| t5 đổi `name` | `everyOtherField_isKept` | 176 |

Mỗi đột biến làm đúng một test dự kiến đỏ và không test nào khác. t1 và t3 cùng điểm chết nên test không phân biệt hai lỗi đó (nói thẳng). Mã khôi phục giống hệt (`cmp`), `git diff app/src/main` rỗng.

## Kiểm khác (đo lại)
- JVM `testDevDebugUnitTest --rerun-tasks`: lần 1 **3.797 test, 1 đỏ** (`OracleBettingViewModelTest.close_clearsTheState_andTheFailure`, `expected null, but was OracleBetUiState(phase=CAN_BET…)`); chạy riêng lớp đó **3/3 xanh**; lần 2 cả bộ **3.797 test, 0 lỗi, 0 bỏ qua**. Vậy test Oracle **chập chờn** (không liên quan thay đổi này, vốn chỉ chạm `androidTest`); **chưa điều tra gốc**, ghi để sau. `assembleDevDebug assembleDevDebugAndroidTest` thành công. Lint không quét `androidTest`.
- Chỉ đổi trong `app/src/androidTest`: `BlueskyGuardRig.kt` (ghi lại `actor` của yêu cầu `getProfile`; `seed` nhận thêm `icon`, `isNotification`, `isFullContent` có mặc định; 02r không đổi hành vi) và `FeedAddressEditDeviceTest.kt`.

## Không kiểm (nói thẳng)
Bấm giữ dòng địa chỉ, `FeedOptionDrawer`, `ChangeUrlDialog` (gõ và bấm xác nhận), nguồn WEB và NOSTR (chỉ test JVM), Fever và Google Reader, chặn địa chỉ sai hay trùng (02p cố ý không kiểm), `MainActivity`. Một máy (Tecno), không so Pixel.

## Điều cần biết
- Test đồng bộ qua `LocalRssSv` dựng từ DAO thật (như 02r), không qua `SyncWorker`.
- Hệ quả 02p vẫn đúng: sửa địa chỉ Bluesky làm mất DID đã nhớ cho tới lần đồng bộ thành công kế tiếp (02q đưa lại DID); test 1 chứng minh vòng đó trên máy.
- Test Oracle chập chờn trong JVM (xem trên) là việc riêng, chưa làm.
