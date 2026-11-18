# FRONT-02u — Test máy thật: hộp thoại "Đổi địa chỉ" nguồn (thiết kế, chờ owner duyệt spec)

**Ngày:** 2026-11-18 · **Nhánh:** `dev` · **Nền:** FRONT-02p (sửa mã), FRONT-02t (`FeedAddressEditDeviceTest`: ViewModel + Room thật, gọi thẳng `inputNewUrl`/`changeFeedUrl`), FRONT-02s (gõ một lần, không gõ lại), FRONT-02m (lỗi gõ chập chờn ~1%)

> Trạng thái: owner đã chốt trong brainstorm (2026-11-18): (1) dựng **chỉ hộp thoại thật** (`ChangeUrlDialog`), nối y như `FeedOptionDrawer`, **không** dựng ngăn kéo; (2) thay chữ trong ô bằng **một thao tác** (`performTextReplacement`), không gõ lại; (3) 4 test gồm cả ô trống. Owner dặn **bỏ qua mọi việc liên quan quảng cáo**. Spec chờ owner duyệt trước khi lập plan. Chưa có plan, chưa có mã.

## Mục tiêu
FRONT-02t chứng minh `changeFeedUrl()` ghi đúng Room, nhưng gọi thẳng ViewModel. Chưa có bằng chứng trên máy rằng **hộp thoại thật** đưa đúng chữ vào ViewModel, nút "Xác nhận" gọi `changeFeedUrl()`, nút "Hủy" không lưu, và ô trống khóa nút "Xác nhận". Chỉ thêm test, **không đổi mã production**, manifest, chuỗi, Room (v22), DataStore, `app/build.gradle`; không dependency mới; không mạng thật.

## Sự thật đã đọc (2026-11-18)
- `ChangeUrlDialog` (`ui/component/ChangeUrlDlg.kt`) chỉ bọc `TextFieldDlg` với tiêu đề `R.string.change_url`, gợi ý `R.string.feed_url_placeholder` (`translatable="false"`, `http://example.com/feed.rss`).
- `TextFieldDlg` dựng `BaseDlg` (chỉ vẽ `AlertDialog` khi `visible`), `ClipboardTextField` (ô nhập, `BaseTextField` tự xin focus sau 100 ms), nút xác nhận `TextButton(enabled = value.isNotBlank(), onClick = { clearFocus(); onConfirm(value) })` với chữ `R.string.confirm`, nút hủy gọi `onDismissRequest` với chữ `R.string.cancel`. Nút xác nhận **khóa khi `value` trống hoặc toàn khoảng trắng**.
- `FeedOptionDrawer.kt:175-188` nối hộp thoại: `visible = changeUrlDialogVisible`, `value = newUrl`, `onValueChange = inputNewUrl`, `onDismissRequest = hideFeedUrlDialog`, `onConfirm = { changeFeedUrl(); hideDrawer(scope) }`.
- `FeedOptionViewModel`: `showFeedUrlDialog()` đặt `changeUrlDialogVisible = true` và `newUrl = feed.url`; `hideFeedUrlDialog()` đặt `false` và `newUrl = ""`; `changeFeedUrl()` ghi Room (02p) rồi đặt `changeUrlDialogVisible = false`.
- Cách dựng ViewModel thật không qua Hilt, nạp feed bằng reflection (`_feedOptionUiState`), và rig Room/máy chủ giả đã có từ 02t (`FeedAddressEditDeviceTest`, `BlueskyGuardRig`). Cách tìm và gõ vào ô của hộp thoại (một lần, `hasSetTextAction()`, `SemanticsProperties.EditableText`, `compose.waitUntil`) đã có từ 02s.

## Phạm vi trung thực
- **Chứng minh:** trên Tecno, hộp thoại thật hiện đúng địa chỉ cũ khi mở; chữ thay vào ô tới `FeedOptionViewModel` thật; "Xác nhận" lưu đúng hai cột vào Room thật và đóng hộp thoại; "Hủy" đóng mà không lưu; ô trống làm "Xác nhận" bị khóa và không lưu gì.
- **Không chứng minh:** bấm giữ dòng địa chỉ (mở hộp thoại), `FeedOptionDrawer` và `hideDrawer` (dây thứ hai của `onConfirm`, test không có ngăn kéo), bàn phím thật (`performTextReplacement` không đi qua bàn phím), đồng bộ sau khi sửa (02t đã làm), nguồn WEB và NOSTR, Fever và Google Reader.
- Chỉ **Tecno KJ7 `115333744A005844`** (Android 14, 1080x2436). Không tọa độ, không MockK, không `connected*`, không xoay, không mạng thật, không `showDrawer`, không `MainActivity`.

## Thiết kế
Một tệp test mới `ChangeUrlDialogDeviceTest.kt` trong `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/`. Không sửa rig (đã đủ từ 02t).

1. **Dựng:** `FeedOptionViewModel` thật như 02t (`ViewModelProvider(activity, factory)` trên luồng chính), nạp nguồn Bluesky vào state bằng reflection, **không** `showDrawer`. `compose.runOnUiThread { activity.setContent { … ChangeUrlDialog(visible = state.changeUrlDialogVisible, value = state.newUrl, onValueChange = viewModel::inputNewUrl, onDismissRequest = viewModel::hideFeedUrlDialog, onConfirm = { viewModel.changeFeedUrl() }) } }`; trạng thái đọc từ `feedOptionUiState.collectAsState()` (cùng cách drawer đọc).
2. **Mở hộp thoại:** `viewModel.showFeedUrlDialog()`; chờ ô nhập (`hasSetTextAction()`) xuất hiện bằng `compose.waitUntil`.
3. **Thay chữ:** `onNode(hasSetTextAction()).performTextReplacement(NEW_ADDRESS)` **một lần**; chờ ô có đúng chữ mới. **Không** gõ lại.
4. **Bấm nút:** nút tìm bằng chữ (`hasText(activity.getString(R.string.confirm))` kết hợp `hasClickAction()`), không tọa độ.
5. **Test (4):**
   - `opening_showsTheCurrentAddressInTheField`: sau `showFeedUrlDialog()` ô có đúng `feed.url` cũ; Room chưa đổi.
   - `replacingTheAddressAndConfirming_savesBothColumns_andClosesTheDialog`: thay chữ, bấm "Xác nhận": hộp thoại biến mất, Room có `url == sourceUrl == địa chỉ mới` (nguồn Bluesky gieo có DID), các trường khác giữ nguyên.
   - `cancelling_closesTheDialog_andSavesNothing`: thay chữ, bấm "Hủy": hộp thoại biến mất, hàng Room y như trước.
   - `anEmptyField_locksTheConfirmButton_andSavesNothing`: thay chữ bằng chuỗi trống: nút "Xác nhận" `assertIsNotEnabled()`; bấm không có tác dụng (hộp thoại còn mở), Room không đổi.
6. **Dọn:** `@After` `rig.cleanUp()`; test dọn dẹp riêng như 02t. `@Before` `check(Local)`.

## Quy tắc an toàn thiết bị
Như 02t: cổng `/tmp/kj7_gate.sh` in `GATE OK` **trong một lệnh riêng** trước mỗi lần khởi chạy; sao lưu/khôi phục byte-exact (`/tmp/kj7_backup.py`, `/tmp/kj7_restore.py`); băm DataStore `9ca1c202050c258b4aac9a063e27def350d737fb` trùng; xoay `1 0` sau mỗi lượt; `am instrument --user 0 -w -r -e class …`; không `connected*`; không xóa DataStore; gỡ gói test cuối vòng. Ghi tạm vào Room thật chỉ bằng dòng tiền tố `front02r_probe_`. Script đo `/tmp/kj7_watch.sh`.

## Kiểm chứng
- **1 lượt, rồi 20, rồi 40 lượt** có cổng, sao lưu, khôi phục. Không gõ lại, không tăng timeout, không `sleep`. Mọi lượt đỏ phải đọc nguyên nhân trước khi dùng. Lượt đỏ **do lỗi gõ chữ** (02m: chữ không vào ô, hoặc vào rồi mất): ghi nhận, 1 trong 40 chấp nhận được; từ 2 trở lên thì dừng và hỏi owner. Quảng cáo: owner dặn bỏ qua, nên không điều tra; watcher vẫn ghi `AdActivity` nếu có nhưng không là việc cần làm.
- **Đột biến chạy trên máy** (đổi tạm mã production, build, cài, chạy, khôi phục, `cmp`): (u1) `TextFieldDlg` bỏ `onValueChange` (ô không báo chữ mới) → test lưu đỏ; (u2) nút xác nhận không gọi `onConfirm` → test lưu đỏ; (u3) nút Hủy gọi `onConfirm` → test hủy đỏ; (u4) bỏ `enabled = value.isNotBlank()` → test ô trống đỏ; (u5, trong test) bỏ nối `onConfirm` ở test để chứng minh test không xanh giả. Mỗi đột biến phải làm **đúng** tập test dự định đỏ (so sánh tự động); kiểm các mẫu duy nhất trước khi chạy.
- JVM full suite đo lại; `assembleDevDebug assembleDevDebugAndroidTest` thành công; lint không quét `androidTest` (nói thẳng).
- Báo cáo `doc/task/done/FRONT-02u_change-url-dialog-device-test_DONE.md`; cập nhật `doc/feature.md` (dòng 02p và 02t: đã kiểm hộp thoại, vẫn chưa kiểm bấm giữ và ngăn kéo).

## Rủi ro đã biết
- **Lỗi gõ chữ chập chờn (02m, ~1%)** có thể xuất hiện ở bước thay chữ; `performTextReplacement` là một thao tác nên có thể ít hơn, chưa đo. Không gõ lại.
- **`TextFieldDlg` dùng `AlertDialog` (cửa sổ riêng):** 02e và 02s đã thấy được ô nhập của cửa sổ này qua `compose.onAllNodes`, nhưng nút trong hộp thoại chưa được bấm trong test nào: nếu `performClick` không tới được, đó là bằng chứng mới cần điều tra, không tự đổi sang cách bấm khác.
- **Đột biến vào `TextFieldDlg` ảnh hưởng mọi hộp thoại nhập chữ khác** (đổi tên, nhóm mới...): chỉ tạm thời trên máy và luôn khôi phục + `cmp`.
- **Test ghi tạm vào Room thật:** như 02r/02t.

## Giới hạn đã biết
- Một máy (Tecno KJ7, Android 14); không so với Pixel.
- Không chứng minh bấm giữ, ngăn kéo, bàn phím thật.

## Điều chỉnh khi thực thi
- **Chữ nút:** lấy bằng `activity.getString(R.string.confirm / cancel)` (máy có hai ngôn ngữ `en-US,vi-VN`); không bọc `AppTheme`.
- **Test hủy tất định nhờ thứ tự:** chờ hộp thoại đóng rồi mới đọc Room (nếu Hủy gọi nhầm lưu thì hộp chỉ đóng sau khi ghi).
- **u5 đổi nghĩa:** thành "test nối `onDismissRequest = {}`" (u2 đã phủ nút Xác nhận).
- **Quảng cáo:** vòng 1 đỏ 9/20 (cả 9 có `AdActivity`); tổng 12/41 đỏ, cả 12 có quảng cáo, 0 xanh có. Quy tắc "từ 2 đỏ dừng" kích hoạt; owner chọn chạy tiếp và ghi số thật. Đợt đột biến đầu bị quảng cáo đè nên chạy lại các nhãn bị ảnh hưởng (tối đa 3 lần mỗi nhãn) bằng `/tmp/kj7_u_rerun.sh`.
- **Kết quả:** 5/5 đột biến đúng tập test dự kiến; JVM 3.797, 0 lỗi.
