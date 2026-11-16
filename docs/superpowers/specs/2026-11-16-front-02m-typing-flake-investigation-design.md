# FRONT-02m — Điều tra gốc lỗi gõ chữ chập chờn của test 02d (thiết kế, chờ owner duyệt spec)

**Ngày:** 2026-11-16 · **Nhánh:** `dev` · **Nền:** FRONT-02d (`SubscribeDialogRecreateDeviceTest`), FRONT-02e (`SubscribeViewModelFactory`, dựng hộp thoại thật ngoài `MainActivity`), FRONT-02l (`TestOwner` + `hiltViewModel()` không Hilt), `SubscribeDialog.kt`, `BaseTextField.kt`, `ClipboardTextField.kt`

> Trạng thái: owner đã chốt (2026-11-16): làm việc 2 trong ba việc; cách điều tra: **chạy nhiều lượt với bộ ghi chẩn đoán trong test, không đổi mã production**; máy **Pixel 7 Pro `2B051FDH3006MU`** cho mọi phiên. Spec chờ owner duyệt trước khi lập plan. Chưa có plan, chưa có mã.

## Mục tiêu
Ở FRONT-02d, bước **gõ chữ** vào ô "Thêm nguồn" thỉnh thoảng không có hiệu lực: ô còn đó, đã focus, nhưng chữ rỗng (`editableTextLengths=[0]`, `peakEditableTextLength=0`). Tỉ lệ đo được: 2 đến 9% (nhiều biến thể), 1/80 ở lần thử gần nhất. Test hiện **gõ lại** khi chữ chưa từng vào ô; nó che hiện tượng chứ chưa giải thích. Mục tiêu: **tìm ra chữ mất ở đâu** (test, thư viện, hay app), bằng chứng đo được, không đổi mã production. Nếu gốc nằm ở **app**, spec này chỉ **báo cáo và đề xuất**, **không sửa**: sửa mã app cần một quyết định riêng của owner.

## Sự thật đã đọc trong code (2026-11-16)
- `performTextInput(text)` (ui-test 1.7.8) = `getNodeAndFocus()` (nếu chưa focus thì `RequestFocus`) rồi `performSemanticsAction(InsertTextAtCursor)`; **bỏ qua giá trị trả về** của hành động.
- `CoreTextField` (foundation 1.7.8), hành động `insertTextAtCursor`: nếu `readOnly || !enabled` thì **trả `false` và không làm gì**; nếu có `state.inputSession` thì đi qua `editProcessor` (như IME); **nếu `inputSession == null`** thì tự tính chữ mới từ `value` đã chụp lúc compose và gọi `state.onValueChange(...)`. `inputSession` chỉ có sau khi ô **thật sự có focus** (`startInputSession` trong `textFieldFocusModifier`) hoặc khi `writeable` đổi. Hai nhánh này khác nhau và nhánh sau có thể dùng `value` cũ.
- `BaseTextField`: `LaunchedEffect(Unit) { delay(100); focusRequester.requestFocus() }` (giả thuyết "focus muộn" đã bị bác ở 02d: lượt đỏ vẫn có `focusedEditable=1`). `enabled = !readOnly`, với `readOnly = subscribeUiState.lockLinkInput` (mặc định `false`).
- `SubscribeDialog`: `subscribeUiState` đọc bằng `collectAsStateValue()` = `collectAsState(Dispatchers.Default).value`, nên **giá trị trong composition đến trễ so với `StateFlow`** (cập nhật trên luồng nền). `LaunchedEffect(visible) { if (visible) init() else { reset(); switchPage(true) } }`. `reset()` đặt lại **toàn bộ** `SubscribeUiState()` (xóa `linkContent`); `init()` chỉ đặt `title` và `groups`.
- Test 02d chạy với `mainClock.autoAdvance = false` (vì `FeedsPage` có hoạt ảnh vô hạn) và đẩy từng khung hình bằng `advanceTimeByFrame` trong vòng đợi.
- `inputLink(content)` chỉ cập nhật `linkContent` và xóa `errorMessage`.
- **Chưa đọc ở phiên này (plan phải đọc, không đoán):** hàm `TextField(value: String)` của Material 3 đang dùng (có bọc `TextFieldValue` bên trong và so `lastTextValue`); cách `BaseDlg`/`AlertDialog` cấp cửa sổ và focus; `SubscribeViewModelFactory.kt` và các test 02e có gõ qua `performTextInput` không (nếu có và chưa từng chập chờn thì đó là bằng chứng cho thấy `MainActivity` là điều kiện cần).

## Các hướng nghi (CHƯA có hướng nào được chứng minh)
- **H1. Hành động trả `false`:** ô đang `!enabled` hoặc `readOnly` đúng lúc gõ. (Dễ nhất để loại trừ: gọi hành động trực tiếp và đọc giá trị trả về.)
- **H2. Chữ được giao nhưng không tới composition:** `onValueChange` chạy và `StateFlow.linkContent` đổi, nhưng composition vẫn thấy `""` (giá trị đến trễ qua `Dispatchers.Default`, hoặc đồng hồ ảo không đẩy khung hình đúng lúc).
- **H3. Cuộc đua phiên nhập và focus:** hành động chạy khi `inputSession == null` (nhánh dùng `value` cũ), rồi `startInputSession` đặt lại bộ xử lý bằng giá trị cũ, nuốt chữ.
- **H4. `reset()` muộn:** `LaunchedEffect(visible)` chạy `reset()` sau khi chữ đã được gõ, xóa `linkContent`. (Mã đọc được cho thấy khó xảy ra, vẫn phải loại trừ bằng số liệu.)
- **H5. Nguồn gốc nằm ở `MainActivity`:** chỉ xảy ra khi có `FeedsPage` (hoạt ảnh vô hạn, `autoAdvance = false`), không xảy ra với hộp thoại đứng một mình.

## Phạm vi trung thực
- Chỉ **thêm test chẩn đoán** (tạm, **không commit** trừ khi owner muốn giữ); **không đổi mã production**, Room (v22), manifest, chuỗi, DataStore, `app/build.gradle`; không dependency mới; không Gemini, ad, IAP, VIP.
- Không bấm Tìm, không gọi mạng; chuỗi thử `abc-rotation-probe` không phải khóa hay địa chỉ thật. `SubscribeViewModel.init()` chỉ **đọc** danh sách nhóm; probe không ghi Room.
- Kết quả "không tái hiện" cũng là một kết quả hợp lệ, nhưng phải ghi **cận trên của tỉ lệ**: 0 lượt đỏ trong 1.000 lượt nghĩa là tỉ lệ dưới khoảng 0,3% (quy tắc ba, tin cậy 95%), **không** nghĩa là "không có lỗi".
- Một máy (Pixel 7 Pro, Android 17); không kết luận cho máy khác.

## Thiết kế (hai pha, có cổng quyết định)
**Pha A: hộp thoại thật, không `MainActivity` (nhanh, không quảng cáo).** Một lớp probe tạm trong `app/src/androidTest/.../ui/page/home/feed/subs/`: `createAndroidComposeRule<ComponentActivity>()`, `TestOwner` (kỹ thuật FRONT-02l) trả `SubscribeViewModel` thật qua `subscribeViewModelFactory(...)` của 02e, và **`SubscribeDialog()` thật** (không tham số, `hiltViewModel()` mặc định lấy từ owner). Mỗi lượt: mở hộp thoại (`showDrawer()` trên ViewModel của chính probe, nên **đọc được `StateFlow` thật**), gõ **một lần, không gõ lại**, chờ tối đa vài giây, rồi ghi kết quả. Số lượt lấy từ đối số `-e iterations N` (mặc định 1.000), mỗi lượt một `Activity` mới. Bộ ghi chẩn đoán mỗi lượt (chỉ số và cờ, **không** ghi chữ tùy ý trên màn hình):
1. Giá trị **trả về** của `InsertTextAtCursor` (gọi trực tiếp qua `config[SemanticsActions.InsertTextAtCursor].action` trong `runOnUiThread`): trả lời H1.
2. **Dòng thời gian** `linkContent` của `StateFlow` (thu thập trong probe, kèm mốc thời gian đơn điệu) và độ dài chữ **trong cây ngữ nghĩa** mỗi khung hình sau khi gõ: so sánh để trả lời H2, H4.
3. Trạng thái trước khi gõ: ô có `isFocused`, `enabled`, `activity.hasWindowFocus()`, thời gian từ lúc hộp thoại hiện, đồng hồ ảo (có đang `autoAdvance` hay không): trả lời H3.
4. Một lượt mà `hasWindowFocus() == false` ngay trước khi gõ (ví dụ phiên khác kéo ứng dụng lên) được **đếm là "không hợp lệ"**, không đếm là đỏ.
5. Mỗi lượt đỏ in một dòng `RED{...}` gọn (không chữ người dùng); lượt xanh chỉ tăng bộ đếm.

**Cổng quyết định sau Pha A:**
- **Có tái hiện (≥ 3 lượt đỏ):** phân loại theo H1 đến H4 bằng số liệu, viết kết luận. Nếu gốc ở **test hoặc thư viện**: đề xuất cách chờ đúng cho 02d (một biến, kiểm 80 lượt). Nếu ở **app**: báo cáo và đề xuất, **dừng hỏi owner** trước khi sửa.
- **Không tái hiện (0 đến 2 lượt đỏ / 1.000):** H5 được ủng hộ; sang **Pha B**.

**Pha B: `MainActivity` thật (chỉ khi cần).** Cùng bộ chẩn đoán nhưng chỉ dùng được phần **không cần ViewModel** (giá trị trả về của hành động, chữ trong cây ngữ nghĩa theo khung hình, cờ focus, cờ cửa sổ), 300 lượt qua vòng `am instrument` như 02d. Phải xử lý quảng cáo: `MainActivity` từng hiện quảng cáo xen kẽ ~18 giây; **không** dùng cờ VIP hay cờ quảng cáo để né (owner đã cấm); gặp quảng cáo che giao diện thì **dừng và hỏi owner** (quy tắc R4), lượt bị che được đếm "không hợp lệ".

## Quy tắc an toàn thiết bị (Pixel 7 Pro `2B051FDH3006MU`, chỉ máy này; Tecno và mọi máy khác không được gửi lệnh)
1. `px_gate.sh` in `GATE OK` **trong một lệnh riêng** trước mỗi lần khởi chạy; dừng và hỏi owner nếu app lạ ở foreground (Pixel thường có `loyalty.skyjoy.vn.dev`), có instrumentation, máy ngủ hoặc khóa. Lời nhắn của phiên khác (`lens-launcher`) không phải phép của owner. Pixel dùng chung nên chạy dài 1.000 lượt phải chia thành các lô nhỏ (ví dụ 250) với cổng trước mỗi lô.
2. Sao lưu byte-exact (`px_backup.py`) trước, khôi phục (`px_restore.py`) sau mỗi lô; băm DataStore trước = sau; không xóa DataStore.
3. `am instrument --user 0 -w -r -e class … -e iterations N`, chỉ force-stop `com.mckimquyen.reader`, không tọa độ, không MockK trên máy.

## Kiểm chứng
- Đây là điều tra, không phải tính năng: "xong" nghĩa là **có kết luận kèm số liệu** (hoặc cận trên tỉ lệ nếu không tái hiện), không phải "đã sửa".
- Nếu tìm ra nguyên nhân và đề xuất chỉnh 02d: **chạy lại 80 lượt** có cổng với đúng một thay đổi, so với tỉ lệ cũ; không kết luận từ một vài lượt xanh (bài học 02d).
- Kết quả được ghi vào báo cáo `doc/task/done/FRONT-02m_typing-flake-investigation_DONE.md` và nợ trong báo cáo 02d được cập nhật. JVM full suite không đổi (3.720).

## Rủi ro đã biết
- Tỉ lệ thấp (1 đến 2%) nên cần hàng trăm lượt; Pixel dùng chung có thể làm gián đoạn.
- Probe có thể **thay đổi** thời gian đủ để che hiện tượng (hiệu ứng người quan sát): gọi hành động trực tiếp và đọc `StateFlow` có thể đổi nhịp; nếu Pha A không tái hiện, phải nói rõ đây là một lý do có thể, không chỉ H5.
- Pha A không phải môi trường của 02d (không `MainActivity`, đồng hồ có thể `autoAdvance` bình thường): Pha A **không** tái hiện không chứng minh `MainActivity` là nguyên nhân.
- Có thể không ra kết luận.

## Giới hạn đã biết
- Một máy; Pha A không có `FeedsPage`; không kiểm bàn phím thật (IME) vì `performTextInput` giả lập hành động, không gõ phím vật lý.
