# FRONT-02v — Test máy thật: bấm giữ dòng nguồn, ngăn kéo tùy chọn, sửa địa chỉ (thiết kế, chờ owner duyệt spec)

**Ngày:** 2026-11-18 · **Nhánh:** `dev` · **Nền:** FRONT-02p (sửa mã), 02t (`FeedAddressEditDeviceTest`), 02u (`ChangeUrlDialogDeviceTest`: hộp thoại thật), FRONT-02l (`FeedItemDeviceTest`: `FeedItem` thật + `TestOwner`)

> Trạng thái: owner đã chốt trong brainstorm (2026-11-18): (1) đi **cả chuỗi người dùng làm**: bấm giữ dòng nguồn, ngăn kéo hiện, bấm giữ dòng địa chỉ, sửa, xác nhận; (2) nếu Compose báo **bận**: dừng, đo, hỏi owner (không tự đổi đồng hồ); (3) 5 test. Owner dặn **bỏ qua mọi việc liên quan quảng cáo** (không điều tra, không đổi cấu hình; ghi số thật). Spec chờ owner duyệt trước khi lập plan. Chưa có plan, chưa có mã.

## Mục tiêu
02u kiểm hộp thoại "Đổi địa chỉ" thật nhưng mở nó bằng ViewModel và không có ngăn kéo; 02t kiểm Room. Còn thiếu, trên máy thật: (a) **bấm giữ `FeedItem`** mở ngăn kéo đúng nguồn; (b) **bấm giữ dòng địa chỉ trong ngăn kéo** mở hộp thoại; (c) dây thứ hai của nút Xác nhận: `hideDrawer(scope)` **đóng ngăn kéo** (02u không có ngăn kéo nên không kiểm được). Chỉ thêm test, **không đổi mã production**, manifest, chuỗi, Room (v22), DataStore, `app/build.gradle`; không dependency mới; không mạng thật.

## Sự thật đã đọc (2026-11-18)
- `FeedItem` (`FeedItem.kt:35`): tham số `feedOptionViewModel: FeedOptionViewModel = hiltViewModel()`; trên dòng có `combinedClickable(onClick = onClick, onLongClick = { performHapticFeedback(KEYBOARD_TAP); feedOptionViewModel.showDrawer(scope, feed.id) })`, `scope = rememberCoroutineScope()`.
- `FeedOptionViewModel.showDrawer(scope, feedId)` (`FeedOptionVM.kt:56`): `scope.launch { fetchFeed(feedId); drawerState.show() }`; `fetchFeed` đọc `rssService.get().findFeedById(feedId)` (Room thật) rồi đặt `feed` và `selectedGroupId` vào state. `drawerState` là `ModalBottomSheetState(Hidden)` nằm **trong** `FeedOptionUiState`.
- `FeedOptionDrawer` (`FeedOptionDrawer.kt:43`): `feedOptionViewModel: FeedOptionViewModel = hiltViewModel()`, `content` là nội dung nằm trong `BottomDrawer` (`ModalBottomSheetLayout`); trong `FeedsPage.kt:382` nó được đặt dưới dạng `FeedOptionDrawer()` cạnh danh sách. `DeleteFeedDialog` và `ClearFeedDialog` cũng gọi `hiltViewModel()`.
- Trong ngăn kéo: `FeedOptionView(link = feed.url, onFeedUrlLongClick = { if (rssService.get().update) { haptic; showFeedUrlDialog() } })`; dòng địa chỉ là `Text` với `combinedClickable(onClick = onFeedUrlClick, onLongClick = onFeedUrlLongClick)`. `rssService.get().update` là `true` với tài khoản Local (đã đọc ở 02p; kiểm lại trong plan).
- `ChangeUrlDialog.onConfirm = { changeFeedUrl(); hideDrawer(scope) }`, `scope` là `rememberCoroutineScope()` của `FeedOptionDrawer`.
- `FeedItemDeviceTest` đã chạy trên máy: dựng `FeedItem` thật trong `ComponentActivity` với `TestOwner : ViewModelStoreOwner, HasDefaultViewModelProviderFactory` cấp `FeedOptionViewModel(app.rssSv, Dispatchers.Main, app.ioDispatcher)`, `CompositionLocalProvider(LocalViewModelStoreOwner provides owner)` và `AppTheme(useDarkTheme = false)`. `hiltViewModel()` rơi về factory mặc định của chủ sở hữu (vì không phải `NavBackStackEntry`), và **cùng một chủ sở hữu cho cùng một instance**.
- Báo cáo 02p/02q: gọi `showDrawer` trong **JVM** làm đồng hồ Compose bận (`AppNotIdleException` ở các test chạy sau cùng JVM). **Chưa từng đo trên máy.** Owner chọn: chạy đồng hồ mặc định, nếu bận thì dừng, đo, hỏi.
- `FeedOptionView` có `LaunchedEffect(Unit)` gọi `onGroupClick(groups.first().id)` nếu `groups` không rỗng và `selectedGroupId` rỗng; `selectedGroup` chỉ ghi Room khi `feed != null` (kiểm trong plan rằng với nguồn đã nạp nó **không** ghi, nhờ `selectedGroupId = feed.groupId` khác rỗng).
- `LocalOpenLink`, `LocalOpenLinkSpecificBrowser` có giá trị mặc định (`compositionLocalOf`), nên không cần cung cấp. Nút "Bật thông báo" gọi `rememberNotificationPermissionRequest()` (chỉ khi bấm; test không bấm).

## Phạm vi trung thực
- **Chứng minh:** trên Tecno, **bấm giữ `FeedItem` thật** mở ngăn kéo thật đúng nguồn đó; bấm giữ dòng địa chỉ trong ngăn kéo mở hộp thoại với địa chỉ cũ; sửa rồi "Xác nhận" ghi đúng hai cột vào Room thật, đóng hộp thoại **và đóng ngăn kéo**; "Hủy" không lưu gì.
- **Không chứng minh:** `FeedsPage` (danh sách nhóm và nguồn, cuộn, mở rộng nhóm), vuốt ngăn kéo bằng tay, bàn phím thật, nút Xóa nguồn và Xóa bài (ghi và xóa dữ liệu), đổi nhóm, bật thông báo, nguồn WEB và NOSTR, Fever và Google Reader, đồng bộ (02t đã làm).
- Chỉ **Tecno KJ7 `115333744A005844`** (Android 14, 1080x2436). Không tọa độ màn hình (bấm giữ bằng thao tác Compose lên node), không MockK, không `connected*`, không xoay, không mạng thật, không `MainActivity`, không gọi `showDrawer()` trực tiếp (chính cú bấm giữ gọi nó).

## Thiết kế
Một tệp test mới `FeedDrawerLongPressDeviceTest.kt` trong `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/` (cùng gói `FeedItemDeviceTest`). Dùng lại `BlueskyGuardRig` (02r/02t) để gieo và đọc Room; **không sửa rig** (kiểm trong plan).

1. **Dựng:** `ComponentActivity`; `TestOwner` sao chép từ `FeedItemDeviceTest` (lớp đó `private`, không tách chung để không động tới 02l đã đo); `setContent { CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { AppTheme(useDarkTheme = false) { FeedOptionDrawer { FeedItem(feed = feed, isEnded = { true }, isExpanded = { true }) } } } }` với `feed` đọc từ Room sau khi gieo. Không truyền `feedOptionViewModel` tường minh: mọi `hiltViewModel()` ra cùng instance của `owner` (kiểm bằng `assertSame` trên `owner`'s store trong plan).
2. **Bấm giữ dòng nguồn:** `onNode(hasText(feed.name), useUnmergedTree = true).performTouchInput { longClick() }`, rồi `waitUntil` ngăn kéo hiện: `viewModel.feedOptionUiState.value.drawerState.isVisible` (đọc trên luồng chính qua `runOnUiThread`) và dòng địa chỉ có chữ `feed.url`.
3. **Bấm giữ dòng địa chỉ:** `onNode(hasText(feed.url)).performTouchInput { longClick() }` (chọn node nằm trong ngăn kéo, vì `FeedItem` không hiện `url`); chờ ô nhập của hộp thoại (`hasSetTextAction()`).
4. **Sửa và xác nhận:** như 02u (`performTextReplacement` một lần; nút tìm bằng `activity.getString(R.string.confirm / cancel)`).
5. **Đo ngăn kéo đóng:** chờ `!drawerState.isVisible`; `ModalBottomSheetState` dùng animation nên chờ bằng `waitUntil` mặc định (không `sleep`).
6. **5 test:**
   - `longPressOnTheSourceRow_opensTheDrawerForThatSource`
   - `longPressOnTheAddress_opensTheChangeDialogWithTheCurrentAddress`
   - `editingTheAddressAndConfirming_savesBothColumns_andClosesTheDialogAndTheDrawer`
   - `cancellingTheEdit_savesNothing`
   - `afterCleanUp_nothingOfTheProbeIsLeftAnywhere_andTheRealDataIsUntouched`
7. **Dọn:** `@After` `owner.viewModelStore.clear()` rồi `rig.cleanUp()` và `rig.close()`. `@Before` `check(Local)`.

## Quy tắc an toàn thiết bị
Như 02t/02u: cổng `/tmp/kj7_gate.sh` in `GATE OK` **trong một lệnh riêng** trước mỗi lần khởi chạy; sao lưu/khôi phục byte-exact (`/tmp/kj7_backup.py`, `/tmp/kj7_restore.py`); băm DataStore `9ca1c202050c258b4aac9a063e27def350d737fb` trùng; xoay `1 0` sau mỗi lượt; `am instrument --user 0 -w -r -e class …`; không `connected*`; không xóa DataStore; gỡ gói test cuối vòng. Ghi tạm vào Room thật chỉ bằng dòng tiền tố `front02r_probe_`. Script đo `/tmp/kj7_watch.sh`.

## Kiểm chứng
- **1 lượt, rồi 20, rồi 40 lượt** có cổng, sao lưu, khôi phục. Không gõ lại, không tăng timeout, không `sleep`, **không tự đổi đồng hồ Compose**. Mọi lượt đỏ phải đọc nguyên nhân. Quảng cáo: bỏ qua theo owner (watcher vẫn ghi `AdActivity`; số liệu ghi thật). **Nếu Compose báo bận** (`ComposeNotIdleException`/`AppNotIdleException`, thời gian chờ ở `waitForIdle`): **dừng, đo (số lần, ở bước nào), hỏi owner**. Quy tắc cũ: từ 2 lượt đỏ không do quảng cáo trong 40: dừng, điều tra, hỏi.
- **Đột biến chạy trên máy** (đổi tạm mã production, build, cài, chạy, khôi phục, `cmp`, `git diff app/src/main` rỗng): (v1) `FeedItem` bỏ `showDrawer` → test 1 đỏ; (v2) `FeedOptionDrawer` bỏ `hideDrawer(scope)` ở `onConfirm` → test 3 đỏ ở bước ngăn kéo đóng; (v3) `FeedOptionDrawer` không nối `onFeedUrlLongClick` (`{}`) → test 2 đỏ; (v4) `onFeedUrlLongClick` mở hộp thoại ở `onFeedUrlClick` thay vì bấm giữ (đổi trong `FeedOptionView`) → test 2 đỏ; (v5) `showFeedUrlDialog` đặt `newUrl = ""` → test 2 đỏ. So sánh tự động tập test đỏ; đợt bị quảng cáo đè phải **chạy lại đúng nhãn đó** (tối đa 3 lần) như 02u.
- JVM full suite đo lại (đỏ chập chờn: chạy riêng 3 lần + chạy lại cả bộ, ghi thẳng); `assembleDevDebug assembleDevDebugAndroidTest` thành công; lint không quét `androidTest`.
- Báo cáo `doc/task/done/FRONT-02v_feed-drawer-long-press-device-test_DONE.md`; cập nhật `doc/feature.md` (dòng 02p, 02t, 02u: đã kiểm cả chuỗi bấm giữ trên máy).

## Rủi ro đã biết
- **Compose bận do `ModalBottomSheet`** (đã thấy ở JVM, chưa đo trên máy): nếu xảy ra, dừng và hỏi.
- **`longClick()` trên node trong `ModalBottomSheetLayout`/`combinedClickable`**: có thể không bấm tới nếu node bị che; là bằng chứng mới, không tự đổi cách bấm.
- **Dòng địa chỉ có trong cây ngay cả khi ngăn kéo ẩn** (nội dung ngăn kéo được dựng nhưng nằm ngoài màn hình): test chỉ bấm sau khi `drawerState.isVisible`.
- **Quảng cáo toàn màn hình** (02u: 29% lượt): test dài hơn nên có thể cao hơn; ghi số thật.
- **`FeedOptionView.LaunchedEffect`** có thể gọi `selectedGroup` (ghi Room); kiểm trong plan bằng khẳng định "mọi trường khác giữ nguyên".
- **Test ghi tạm vào Room thật:** như 02r, 02t, 02u.

## Giới hạn đã biết
- Một máy (Tecno KJ7, Android 14); không so Pixel.
- Không `FeedsPage`, không vuốt tay, không bàn phím thật.

## Điều chỉnh khi thực thi
- **Máy:** Tecno không kết nối lúc chạy; owner cho phép Pixel 7 Pro (`2B051FDH3006MU`) cho việc này. Dựng lại cổng/sao lưu/khôi phục cho Pixel (`/tmp/px_*`). Kết quả là của Pixel.
- **Gói:** test nằm ở gói `ui.page.home.feed` (cùng `FeedItemDeviceTest`), watcher đổi `CLS` cho đúng.
- **v4:** đổi thành `onLongClick = {}` ở `FeedOptionView` (đổi chỗ `onClick` có thể bật ứng dụng ngoài); v5 đo được chỉ làm test 2 đỏ (test 3 và 4 thay hết chữ trong ô).
- **Compose không bận** trên Pixel với đồng hồ mặc định (41 lượt): không cần xử lý riêng.
- **Cổng dừng giữa đợt đột biến** vì ứng dụng khác mở trên Pixel; kết quả đợt đó không dùng, chạy lại v2 đến v5 và base sau khi owner đưa máy về màn hình chính.
- **Kết quả:** 41/41 xanh, 5/5 đột biến, JVM 3.797, 0 lỗi.
