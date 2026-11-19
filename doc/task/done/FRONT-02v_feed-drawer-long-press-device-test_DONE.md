# FRONT-02v (Test máy thật: bấm giữ dòng nguồn, ngăn kéo tùy chọn, sửa địa chỉ)

**Date:** 2026-11-18
**Branch:** `dev` (không push)
**Scope:** chỉ thêm một tệp test `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/FeedDrawerLongPressDeviceTest.kt`. **Không đổi mã production**, manifest, chuỗi, Room (v22), DataStore, `app/build.gradle`, dependency. Không mạng thật. Rig 02r/02t/02u không đổi.
**Spec / plan:** `docs/superpowers/specs/2026-11-18-front-02v-feed-drawer-long-press-device-test-design.md`, `docs/superpowers/plans/2026-11-18-front-02v-feed-drawer-long-press-device-test.md`.

## Máy đã dùng (nói thẳng)
**Pixel 7 Pro (`2B051FDH3006MU`, Android 17), không phải Tecno.** Khi tới Task 2, Tecno KJ7 không còn kết nối (cổng báo "KJ7 missing/offline"). Tôi hỏi bạn và bạn **cho phép dùng Pixel cho việc này**. Script cũ của Pixel đã bị dọn khỏi `/tmp`, nên tôi dựng lại cổng, sao lưu và khôi phục cho Pixel từ mẫu Tecno (`/tmp/px_gate.sh`, `/tmp/px_backup.py`, `/tmp/px_restore.py`; cổng kiểm launcher của Pixel, 1440x3120, xoay `1 0`). Không đụng thiết bị nào khác. **Bằng chứng này là của Pixel, không phải Tecno**: các báo cáo 02t và 02u là Tecno.

## Điều test chứng minh
Bằng các mảnh màn hình thật: `FeedItem` thật, `FeedOptionDrawer` thật, `ChangeUrlDialog` thật, một `FeedOptionViewModel` thật (cấp qua `ViewModelStoreOwner` cho mọi `hiltViewModel()`), Room thật của máy:
1. `longPressOnTheSourceRow_opensTheDrawerForThatSource`: bấm giữ dòng nguồn thì ngăn kéo hiện, đúng nguồn đó; Room chưa đổi.
2. `longPressOnTheAddress_opensTheChangeDialogWithTheCurrentAddress`: bấm giữ dòng địa chỉ trong ngăn kéo thì hộp thoại mở với địa chỉ cũ.
3. `editingTheAddressAndConfirming_savesBothColumns_andClosesTheDialogAndTheDrawer`: thay chữ một thao tác, bấm "Xác nhận": Room có `url` và `sourceUrl` là địa chỉ mới, mọi trường khác giữ nguyên, hộp thoại đóng **và ngăn kéo đóng** (dây `hideDrawer` mà 02u chưa kiểm được).
4. `cancellingTheEdit_savesNothing`: bấm "Hủy": hộp thoại đóng, Room y như cũ, ngăn kéo **vẫn mở** (đúng hành vi: chỉ "Xác nhận" gọi `hideDrawer`).
5. `afterCleanUp_nothingOfTheProbeIsLeftAnywhere_andTheRealDataIsUntouched`: không sót dòng thử, dữ liệu thật không đổi.

Bấm giữ là thao tác Compose lên chính node (`performTouchInput { longClick() }`), **không tọa độ màn hình**. Test không bao giờ gọi `showDrawer()` (chính cú bấm giữ gọi nó) và **không đổi đồng hồ Compose**.

## Số đo trên Pixel (không gõ lại, không tăng timeout, không sleep)
| Vòng | Lượt | Xanh | Đỏ | Compose "bận" | Có `AdActivity` |
|---|---|---|---|---|---|
| thử | 1 | 1 | 0 | 0 | 0 |
| vòng 1 | 20 | 20 | 0 | 0 | 0 |
| vòng 2 | 20 | 20 | 0 | 0 | 0 |
| **Cộng** | **41** | **41** | **0** | **0** | **0** |

- **Compose không bận lần nào**: nỗi lo từ JVM (`ModalBottomSheet` làm đồng hồ bận, báo cáo 02p/02q) **không xảy ra trên máy thật Pixel** với đồng hồ mặc định. Chưa chứng minh trên Tecno.
- 0 trong 41 lượt đỏ: cận trên 95% của tỉ lệ đỏ khoảng 7% (không loại trừ mức nhỏ).
- Băm DataStore `ed9f68879fc775bb5fb1b5f24206a6c1a924bcee` trước và sau mọi vòng (của Pixel); xoay `1 0` sau mọi lượt; sao lưu/khôi phục byte-exact OK; gói test được gỡ cuối mỗi vòng.

## Mutation chạy trên máy (5 đột biến; đổi tạm, build, cài, chạy, khôi phục, `cmp`)
| Đột biến | Đổi gì | Test đỏ (đo được) | Chỗ đỏ |
|---|---|---|---|
| base | không | không (`OK (5 tests)`) | |
| v1 | `FeedItem`: bỏ `showDrawer` | 4 test (bước 1 đến 4) | "Never saw: the drawer for the source" (`feedLoaded=false`) |
| v2 | `FeedOptionDrawer`: bỏ `hideDrawer(scope)` sau xác nhận | chỉ test 3 | ngăn kéo không đóng sau Xác nhận |
| v3 | `FeedOptionDrawer`: bỏ nối bấm giữ địa chỉ | test 2, 3, 4 | hộp thoại không mở |
| v4 | `FeedOptionView`: `onLongClick = {}` của dòng địa chỉ | test 2, 3, 4 | hộp thoại không mở |
| v5 | `showFeedUrlDialog`: `newUrl = ""` | chỉ test 2 | "Never saw: the current address in the field" (`newUrlLength=0`) |

- **Đính chính kỳ vọng của plan:** plan dự kiến v5 làm cả test 3 và 4 đỏ. Đo được chỉ test 2 đỏ, vì test 3 và 4 thay **toàn bộ** chữ trong ô bằng `performTextReplacement` nên ô trống lúc mở không ảnh hưởng chúng. Đây là kỳ vọng sai của plan, không phải test yếu: test 2 là test duy nhất kiểm "ô mở ra có địa chỉ cũ", và nó bắt được v5. Đã sửa `/tmp/v_check.py`.
- **Quy trình:** đợt đầu dừng giữa chừng ở v2 vì Pixel đang mở ứng dụng khác (`loyalty.skyjoy.vn.dev`) làm cổng dừng; ở v2 lần đó test 2 đỏ "No compose hierarchies found" (ứng dụng khác chiếm màn hình), kết quả đó **không dùng**. Sau khi bạn đưa Pixel về màn hình chính, tôi chạy lại v2 đến v5 và base, sạch (không dấu hiệu ứng dụng khác hay quảng cáo).
- Mã bốn tệp khôi phục giống hệt (`cmp`), `git diff app/src/main` rỗng.

## Kiểm khác (đo lại)
- JVM `testDevDebugUnitTest --rerun-tasks`: **3.797 test, 0 lỗi, 0 bỏ qua**. `assembleDevDebug assembleDevDebugAndroidTest` thành công. Lint không quét `androidTest`.
- Chỉ thêm `FeedDrawerLongPressDeviceTest.kt` trong `app/src/androidTest`.

## Không kiểm (nói thẳng)
`FeedsPage` (danh sách nhóm và nguồn), vuốt ngăn kéo bằng tay, bàn phím thật (`performTextReplacement` không qua bàn phím), nút Xóa nguồn và Xóa bài, đổi nhóm, bật thông báo, nguồn WEB và NOSTR, Fever và Google Reader, đồng bộ sau khi sửa (02t). **Không chạy trên Tecno** (không kết nối lúc làm); chỉ một máy là Pixel, nên chưa so hai máy. Quảng cáo: không lượt nào có, nên không biết test này có chịu quảng cáo trên Tecno hay không (02u trên Tecno mất ~29% lượt).

## Điều cần biết
- Test dùng tên nguồn do rig đặt (`Bluesky · Probe`) và `hasText` tìm theo chuỗi con; trong 41 lượt không nhập nhằng.
- Công cụ Pixel nằm ở `/tmp` (không trong kho); nếu cần chạy lại trên Pixel phải dựng lại từ mẫu Tecno.

## Chạy lại trên Tecno (2026-11-19)
Tecno KJ7 đã kết nối lại; chạy cùng test (cùng mã, build của commit `f625f4fd`) bằng cổng, sao lưu và khôi phục byte-exact, watcher quảng cáo.

| Vòng | Lượt | Xanh | Đỏ | Đỏ có `AdActivity` | Compose "bận" | Xanh có `AdActivity` |
|---|---|---|---|---|---|---|
| thử | 1 | 0 | 1 | 1 | 0 | 0 |
| vòng 1 | 20 | 20 | 0 | 0 | 0 | 0 |
| vòng 2 | 20 | 19 | 1 | 1 | 0 | 0 |
| **Cộng** | **41** | **39** | **2** | **2** | **0** | **0** |

- **Cả 2 lượt đỏ giống hệt:** test `editingTheAddressAndConfirming_...` (hoặc test chạy cùng lượt) báo "No compose hierarchies found" và có `AdActivity` trên cùng (chuỗi Activity: app, launcher, app, `AdActivity`). Không lượt nào là lỗi hộp thoại, ngăn kéo hay bấm giữ. Theo lời bạn bỏ qua quảng cáo, tôi không điều tra thêm.
- **Compose không bận lần nào trên Tecno** với đồng hồ mặc định: nỗi lo từ JVM về `ModalBottomSheet` không xảy ra trên cả hai máy.
- Tỉ lệ đỏ do quảng cáo 2/41 (~5%) thấp hơn nhiều so với 02u (12/41); 02v có bằng chứng trên **cả Pixel (41/41 xanh) và Tecno (39/41, 2 đỏ do quảng cáo)**.
- Băm DataStore Tecno `9ca1c202…` trước và sau; xoay `1 0` sau mọi lượt; khôi phục byte-exact OK; gói test gỡ cuối mỗi vòng; máy sạch.
- **Không chạy lại mutation trên Tecno** (đã làm trên Pixel; mã production không đổi giữa hai máy).
