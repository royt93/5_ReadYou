# FRONT-02u (Test máy thật: hộp thoại "Đổi địa chỉ" nguồn)

**Date:** 2026-11-18
**Branch:** `dev` (không push)
**Scope:** chỉ thêm một tệp test `app/src/androidTest/.../subs/ChangeUrlDialogDeviceTest.kt`. **Không đổi mã production**, manifest, chuỗi, Room (v22), DataStore, `app/build.gradle`, dependency. Không mạng thật. Rig 02r/02t không đổi.
**Spec / plan:** `docs/superpowers/specs/2026-11-18-front-02u-change-url-dialog-device-test-design.md`, `docs/superpowers/plans/2026-11-18-front-02u-change-url-dialog-device-test.md`.

## Điều test chứng minh
Trên **Tecno KJ7 (`115333744A005844`, Android 14)**, hộp thoại thật `ChangeUrlDialog` nối vào `FeedOptionViewModel` thật đúng như `FeedOptionDrawer` nối (chữ vào `inputNewUrl`, Hủy gọi `hideFeedUrlDialog`, Xác nhận gọi `changeFeedUrl`), Room thật của máy:
1. `opening_showsTheCurrentAddressInTheField`: mở hộp thoại thì ô hiện đúng địa chỉ cũ; Room chưa đổi.
2. `replacingTheAddressAndConfirming_savesBothColumns_andClosesTheDialog`: thay chữ bằng **một** thao tác, bấm "Xác nhận": hộp thoại đóng, Room có `url` và `sourceUrl` đều là địa chỉ mới, mọi trường khác (icon, thông báo, tải đủ nội dung...) giữ nguyên.
3. `cancelling_closesTheDialog_andSavesNothing`: bấm "Hủy": hộp thoại đóng, Room y như cũ, địa chỉ chưa lưu bị quên.
4. `anEmptyField_locksTheConfirmButton_andSavesNothing`: ô trống thì nút "Xác nhận" bị khóa, bấm không lưu gì.
5. `afterCleanUp_nothingOfTheProbeIsLeftAnywhere_andTheRealDataIsUntouched`: không sót dòng thử, dữ liệu thật của máy không đổi.

Cách làm: nạp nguồn bằng reflection (`_feedOptionUiState`), không `showDrawer`, không `MainActivity`; nút tìm bằng chữ lấy từ `activity.getString(R.string.confirm / cancel)` (máy có hai ngôn ngữ), không tọa độ.

## Số đo trên Tecno (không gõ lại, không tăng timeout, không sleep)
| Vòng | Lượt | Xanh | Đỏ | Đỏ có quảng cáo toàn màn hình (`AdActivity`) | Xanh có quảng cáo |
|---|---|---|---|---|---|
| thử | 1 | 1 | 0 | 0 | 0 |
| vòng 1 | 20 | 11 | 9 | 9 | 0 |
| vòng 2 | 20 | 17 | 3 | 3 | 0 |
| **Cộng** | **41** | **29** | **12** | **12** | **0** |

- **Cả 12 lượt đỏ giống hệt nhau:** cùng test (`replacingTheAddress…`), cùng lỗi "Never saw: the address field of the dialog | describe failed: IllegalStateException" (Compose báo "No compose hierarchies found") và cả 12 có `AdActivity` trên cùng. Lượt đỏ chạy lâu hơn (~12 giây) lượt xanh (~8 giây). **Không lượt nào là lỗi hộp thoại, lỗi gõ chữ hay "chữ vào rồi mất".** 0 trong 29 lượt xanh có quảng cáo.
- Tỉ lệ đỏ do quảng cáo (12/41, ~29%) **cao hơn nhiều** 02s (3/40), 02e và 02r (0/20 mỗi test), 02t (0/41). Chưa biết vì sao test này bị nhiều hơn (không điều tra theo lời owner: "bỏ qua các loại ad"). Một giả thuyết chưa kiểm: test này chạy lâu hơn (~8 giây xanh) nên lâu hơn trong cửa sổ có quảng cáo xuất hiện.
- Quy tắc "từ 2 đỏ trong 40 thì dừng" đã kích hoạt sau vòng 1; đã đọc nguyên nhân từng lượt và hỏi owner; owner chọn "chạy như cũ và ghi số thật, đột biến chạy lại khi bị quảng cáo". Không đổi cấu hình quảng cáo, không đổi test để né.
- Xoay `1 0` và băm DataStore `9ca1c202050c258b4aac9a063e27def350d737fb` không đổi sau mọi vòng; sao lưu/khôi phục byte-exact OK; `user_rotation=0`; gói test gỡ cuối mỗi vòng.

## Mutation chạy trên máy (5 đột biến; đổi tạm, build, cài, chạy, khôi phục, `cmp`)
Lần chạy đầu bị quảng cáo đè (test 2 đỏ ở bước chờ ô trong cả `base`, `u2`, `u3`, `u4`, `u5`): **kết quả đó không dùng**. Chạy lại đúng các nhãn đó (tối đa 3 lần mỗi nhãn, chỉ chạy lại khi log có chữ ký quảng cáo; `u4` cần 3 lần). Kết quả cuối, bản ghi sạch (không chữ ký quảng cáo), so tự động:

| Đột biến | Đổi gì | Test đỏ (đo được) | Chỗ đỏ |
|---|---|---|---|
| base | không | không (`OK (5 tests)`, đầu và cuối) | |
| u1 | `TextFieldDlg`: `onValueChange = {}` | test 2, 3, 4 | "Never saw: the replaced text…" (`newUrlLength` vẫn là chữ cũ) |
| u2 | nút Xác nhận không gọi `onConfirm` | test 2 | "Never saw: the dialog to close after Confirm" |
| u3 | nút Hủy gọi `onConfirm(value)` | test 3 | "Cancel must leave the saved source exactly as it was" (Room đã bị ghi) |
| u4 | bỏ `enabled = value.isNotBlank()` | test 4 | "Failed to assert … (is not enabled)" |
| u5 | (test) `onDismissRequest = {}` | test 3 | "Never saw: the dialog to close after Cancel" |

Mỗi đột biến làm đúng tập test dự kiến đỏ. Mã `TextFieldDlg.kt` và tệp test khôi phục giống hệt (`cmp`), `git diff app/src/main` rỗng.

## Kiểm khác (đo lại)
- JVM `testDevDebugUnitTest --rerun-tasks`: **3.797 test, 0 lỗi, 0 bỏ qua** (lần chạy đầu). `assembleDevDebug assembleDevDebugAndroidTest` thành công. Lint không quét `androidTest`.
- Chỉ thêm `ChangeUrlDialogDeviceTest.kt` trong `app/src/androidTest`.

## Không kiểm (nói thẳng)
Bấm giữ dòng địa chỉ (cách mở hộp thoại), `FeedOptionDrawer` và `hideDrawer` (dây thứ hai của `onConfirm`), bàn phím thật (`performTextReplacement` không qua bàn phím), đồng bộ sau khi sửa (02t đã làm), nguồn WEB và NOSTR, Fever và Google Reader. Test không bọc `AppTheme` (drawer thật nằm trong theme của app). Một máy (Tecno), không so Pixel. Lỗi gõ chữ chập chờn của 02m (~1%) không thấy lượt nào trong 29 lượt xanh, nhưng quảng cáo làm mất 12 lượt nên chưa đủ mẫu để nói gì.

## Điều cần biết
- **Test này không đáng tin như 02t** khi chạy lặp trên Tecno: ~29% lượt mất vì quảng cáo toàn màn hình. Khi đọc kết quả phải xem có `AdActivity` hay không (watcher `/tmp/kj7_watch.sh`). Bằng chứng nằm ở các lượt không có quảng cáo (29 xanh) và đợt đột biến chạy lại.
- Gốc quảng cáo (SDK App Open) đã nêu ở báo cáo 02s, chưa kiểm bằng thí nghiệm; owner dặn bỏ qua.
