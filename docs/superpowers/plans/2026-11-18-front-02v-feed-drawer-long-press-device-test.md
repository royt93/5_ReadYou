# FRONT-02v — Test máy thật: bấm giữ dòng nguồn, ngăn kéo tùy chọn, sửa địa chỉ (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (hoặc subagent-driven-development) để thực thi plan này từng task. Các bước dùng checkbox `- [ ]`.

**Goal:** Chứng minh trên Tecno KJ7, bằng màn hình thật, cả chuỗi người dùng làm: bấm giữ `FeedItem` thì ngăn kéo `FeedOptionDrawer` mở đúng nguồn; bấm giữ dòng địa chỉ trong ngăn kéo mở hộp thoại "Đổi địa chỉ" với địa chỉ cũ; sửa rồi "Xác nhận" ghi đúng hai cột vào Room thật, đóng hộp thoại **và đóng ngăn kéo** (dây `hideDrawer` mà 02u chưa kiểm); "Hủy" không lưu và để ngăn kéo mở.

**Architecture:** Một tệp test mới `FeedDrawerLongPressDeviceTest.kt` (gói `ui.page.home.feed`, cùng gói `FeedItemDeviceTest`) trên `ComponentActivity`. `TestOwner` (chép từ 02l) cấp **một** `FeedOptionViewModel` thật cho mọi `hiltViewModel()`. `setContent` dựng `AppTheme { FeedOptionDrawer { FeedItem(...) } }` đúng như `FeedsPage` đặt drawer cạnh danh sách. Bấm giữ bằng `performTouchInput { longClick() }` lên node (không tọa độ). Dùng lại `BlueskyGuardRig` (02r/02t/02u) để gieo và đọc Room thật; **không sửa rig**.

**Tech Stack:** Compose UI test (`createAndroidComposeRule<ComponentActivity>`), `ViewModelStoreOwner` giả, Room thật của app, `ModalBottomSheetState` (material 1.6.8), `am instrument`, adb, Python 3.9.

## Global Constraints

- Nhánh `dev`; không worktree, không push, không PR, không commit `.idea`.
- Không đổi mã production, manifest, chuỗi, Room (v22), DataStore, `app/build.gradle`; không dependency mới. Chỉ thêm **một tệp** trong `app/src/androidTest` (`FeedDrawerLongPressDeviceTest.kt`). **Mutation tạm** trong `FeedItem.kt`, `FeedOptionDrawer.kt`, `FeedOptionView.kt`, `FeedOptionVM.kt` luôn khôi phục + `cmp` + `git diff app/src/main` rỗng.
- Thiết bị **CHỈ Tecno KJ7 `115333744A005844`**; chỉ `adb -s 115333744A005844`; không `connected*`; chỉ `am instrument --user 0 -w -r -e class … com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner`; không MockK trên máy; không tọa độ màn hình; không mạng thật; **không `setRotation`**; **không gọi `showDrawer()` trực tiếp** (chính cú bấm giữ gọi nó); **không tự đổi đồng hồ Compose** (`autoAdvance`).
- Cổng `/tmp/kj7_gate.sh` phải in `GATE OK` **trong một lệnh RIÊNG** trước mỗi lần khởi chạy. Cổng dừng (app lạ ở foreground, khóa màn, xoay lệch): **dừng và hỏi owner**.
- Sao lưu/khôi phục **byte-exact 6 tệp** (`/tmp/kj7_backup.py`, `/tmp/kj7_restore.py`); băm DataStore `9ca1c202050c258b4aac9a063e27def350d737fb` trùng sau mỗi vòng; xoay `1 0` trước và sau mỗi lượt; không xóa DataStore; gỡ `com.mckimquyen.reader.test` cuối vòng.
- Test ghi tạm vào Room thật chỉ bằng dòng có tiền tố `front02r_probe_`; `@After` luôn `cleanUp()`. Nguồn gieo **không có icon** (icon thật sẽ gọi mạng).
- Gradle chạy NỀN, output ra file, chờ `.exit`/`ALLDONE`; "completed" của lệnh nền chỉ là lệnh khởi chạy.
- **Không gõ lại, không tăng timeout, không `sleep` để chữa lỗi.** Owner dặn **bỏ qua mọi việc liên quan quảng cáo** (không điều tra, không đổi cấu hình; ghi số thật). **Nếu Compose báo bận** (log có `ComposeNotIdle`, `AppNotIdle` hoặc `ComposeTimeoutException` ở `waitForIdle`): **dừng, đo (số lần, ở bước nào), hỏi owner**, không tự đổi cách. Đỏ **không do quảng cáo** từ 2 lượt trong 40: dừng, systematic-debugging, hỏi owner.
- Mutation: một script nền duy nhất, không sửa tệp đột biến cho tới `ALLDONE`; mỗi đột biến phải làm **đúng** tập test dự định đỏ (so sánh tự động); đợt bị quảng cáo đè phải **chạy lại đúng nhãn đó** (tối đa 3 lần), như 02u.
- zsh: không `echo ====`; `grep -r` quote mẫu, không dùng `--include`; sửa tệp bằng python; Python 3.9 cần `# -*- coding: utf-8 -*-` và `open(..., encoding="utf-8")`; macOS `sed -i ''`.
- R5: không magic number (hằng số có tên); test bắt buộc kèm; không báo "done" khi chưa dọn. R2: cập nhật `doc/feature.md`.
- Không dùng số JVM trong plan làm bằng chứng: đo lại lúc thực thi (đỏ chập chờn: chạy riêng 3 lần + chạy lại cả bộ, ghi thẳng).
- Nói thẳng phần không kiểm: `FeedsPage`, vuốt ngăn kéo bằng tay, bàn phím thật, nút Xóa nguồn/Xóa bài, đổi nhóm, bật thông báo, nguồn WEB/NOSTR; lint không quét `androidTest`.

## Điều chỉnh so với spec (đã đọc code thật, 2026-11-18)

1. **`/tmp/kj7_watch.sh` ghép cứng gói `.feed.subs.`** (dòng 5: `CLS=com.mckimquyen.reader.ui.page.home.feed.subs.$1`), còn test này nằm ở gói `ui.page.home.feed`. Task 2 tạo bản `/tmp/kj7_watch2.sh` nhận tên đầy đủ (có dấu chấm) hoặc tên ngắn; `kj7_mut_v.sh` đổi dòng `TEST=` sang gói mới.
2. **Rig không cần sửa:** đã có `seed(id, url, sourceUrl, sourceType, icon, isNotification, isFullContent)`, `stored`, `createGroup`, `cleanUp`, `realRowCounts`, `probeRowCount`, `close`. Tên nguồn do rig đặt cố định: `"Bluesky · Probe"` (duy nhất trong màn hình).
3. **`FeedItem` không hiện `url`; ngăn kéo hiện cả tên nguồn:** nên bấm giữ dòng nguồn bằng `hasText(feed.name)` **trước khi** ngăn kéo mở (lúc đó ngăn kéo hiện "unknown" vì `feed == null`); bấm giữ địa chỉ bằng `hasText(feed.url)` (chỉ ngăn kéo có chữ này; hộp thoại sau khi mở cũng có chữ này trong ô, nên bấm giữ **trước** khi mở hộp thoại).
4. **`ModalBottomSheetLayout` dựng sẵn nội dung ngăn kéo cả khi `Hidden`:** `FeedOptionView.LaunchedEffect(Unit)` có thể gọi `selectedGroup(...)` khi `groups` đã có dữ liệu và `selectedGroupId` rỗng; lúc đó `feed == null` trong ViewModel nên `selectedGroup` **không ghi Room** (`feed?.let`). Test "mọi trường khác giữ nguyên" (so cả `groupId`) là lưới an toàn cho điều này.
5. **Một instance ViewModel cho mọi `hiltViewModel()`:** `hiltViewModel()` dùng khóa mặc định của lớp, đúng khóa của `ViewModelProvider(store, factory)[FeedOptionViewModel::class.java]`. Test tạo ViewModel trước bằng đúng cách đó trên cùng `owner.viewModelStore`; **nếu `FeedItem` hay `FeedOptionDrawer` dùng instance khác, ngăn kéo sẽ không hiện** (state khác), nên test 1 tự là phép kiểm "cùng instance". Không cần `assertSame` riêng.
6. **`drawerState.isVisible` đọc trực tiếp** (`viewModel.feedOptionUiState.value.drawerState.isVisible`) từ luồng test: đọc state snapshot ngoài luồng chính là hợp lệ; không bọc `runOnUiThread` (tránh khóa chết trong `waitUntil`).
7. **`rssService.get().update` là `true`** cho `AbstractRssRepository` (Local); `false` chỉ với Fever và Google Reader (`FeverRssSv`, `GoogleReaderRssSv`). Test kiểm tài khoản Local ở `@Before`.
8. **Test hủy kiểm thêm ngăn kéo vẫn mở:** `onDismissRequest = hideFeedUrlDialog` không gọi `hideDrawer` (chỉ `onConfirm` gọi). Đó là hành vi thật, đưa vào khẳng định.
9. **`TestOwner` chép từ `FeedItemDeviceTest` (lớp `private` ở đó):** không tách chung để không động tới 02l đã đo; trùng lặp ~10 dòng được chấp nhận.
10. **v4 không đổi chỗ `onClick`/`onLongClick`** (spec ghi 'đổi sang bấm thường'): `onClick` gọi `openURL("bsky:…")` có thể bật ứng dụng ngoài và kéo máy khỏi launcher, nên v4 đổi thành `onLongClick = {}` ở `FeedOptionView` (an toàn, vẫn phân biệt với v3 vì khác tầng).
11. **Bốn mẫu đột biến đã kiểm duy nhất** (mỗi mẫu 1 lần trong tệp tương ứng): `FeedItem.kt` (`showDrawer`), `FeedOptionDrawer.kt` (`changeFeedUrl()` + `hideDrawer(scope)` và khối `onFeedUrlLongClick`), `FeedOptionView.kt` (`combinedClickable` của dòng địa chỉ), `FeedOptionVM.kt` (`newUrl = ...feed?.url`).

## Tập test đỏ mong đợi của đột biến (kiểm tự động ở Task 3)

Tên test viết tắt: **T1** `longPressOnTheSourceRow_opensTheDrawerForThatSource`, **T2** `longPressOnTheAddress_opensTheChangeDialogWithTheCurrentAddress`, **T3** `editingTheAddressAndConfirming_savesBothColumns_andClosesTheDialogAndTheDrawer`, **T4** `cancellingTheEdit_savesNothing`, **T5** `afterCleanUp_nothingOfTheProbeIsLeftAnywhere_andTheRealDataIsUntouched`.

| Đột biến | Đổi gì | Test đỏ mong đợi | Vì sao |
|---|---|---|---|
| base | không | không (`OK (5 tests)`) | |
| v1 | `FeedItem`: bỏ `showDrawer(scope, feed.id)` | T1, T2, T3, T4 | bấm giữ không mở ngăn kéo |
| v2 | `FeedOptionDrawer`: bỏ `hideDrawer(scope)` sau `changeFeedUrl()` | T3 | ngăn kéo không đóng sau Xác nhận |
| v3 | `FeedOptionDrawer`: `onFeedUrlLongClick = { }` | T2, T3, T4 | bấm giữ địa chỉ không mở hộp thoại |
| v4 | `FeedOptionView`: dòng địa chỉ `onLongClick = {}` (tầng giao diện, khác v3 ở tầng drawer) | T2, T3, T4 | bấm giữ dòng địa chỉ không làm gì |
| v5 | `FeedOptionVM.showFeedUrlDialog`: `newUrl = ""` | T2, T3, T4 | ô mở ra trống thay vì địa chỉ cũ |

## Cấu trúc file

| File | Việc |
|---|---|
| Create `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/FeedDrawerLongPressDeviceTest.kt` | 5 test (4 hành vi + dọn dẹp) |
| Tạm, **không commit**: `/tmp/kj7_watch2.sh`, `/tmp/kj7_mut_v.sh`, `/tmp/kj7_v_batch.sh`, `/tmp/kj7_v_rerun.sh`, `/tmp/v_check.py` | đo lượt và đột biến chạy trên máy (từ bản `u`) |
| Modify `doc/feature.md`, spec (mục "Điều chỉnh khi thực thi") | tài liệu |
| Create `doc/task/done/FRONT-02v_feed-drawer-long-press-device-test_DONE.md` | báo cáo |

---

### Task 1: Viết `FeedDrawerLongPressDeviceTest` và biên dịch (chưa chạm Tecno)

**Files:**
- Create: `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/FeedDrawerLongPressDeviceTest.kt`

**Interfaces:**
- Consumes: rig 02r/02t/02u (`BlueskyGuardRig()`: `createGroup`, `seed(id, url, sourceUrl, sourceType, icon, isNotification, isFullContent)`, `stored(id)`, `cleanUp`, `close`, `realRowCounts`, `probeRowCount`); `FeedItem(feed, alpha, badgeAlpha, isEnded, isExpanded, feedOptionViewModel = hiltViewModel(), onClick)`; `FeedOptionDrawer(feedOptionViewModel = hiltViewModel(), content)`; `FeedOptionViewModel(rssService, main, io)` với `feedOptionUiState: StateFlow<FeedOptionUiState>` (`feed`, `drawerState: ModalBottomSheetState`, `changeUrlDialogVisible`, `newUrl`); `LocalRssSvFactory.app()`; `AppTheme(useDarkTheme)`; `R.string.confirm`, `R.string.cancel`.
- Produces: lớp `FeedDrawerLongPressDeviceTest` với 5 test T1 đến T5 (tên đầy đủ ở bảng trên).

> Ghi chú TDD (nói thẳng): mã production đã có, nên không có bước "đỏ trước". Bằng chứng "test bắt được lỗi" là **mutation chạy trên máy ở Task 3** (5 đột biến).

- [ ] **Step 1: Kiểm các tên sẽ chép còn đúng**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
J=app/src/main/java/com/mckimquyen/reader/ui/page/home/feed
grep -n "^fun FeedItem\|feedOptionViewModel.showDrawer" $J/FeedItem.kt
grep -n "^fun FeedOptionDrawer\|hideDrawer(scope)\|onFeedUrlLongClick" $J/drawer/feed/FeedOptionDrawer.kt
grep -n "private val _feedOptionUiState\|fun showDrawer\|fun hideDrawer\|fun showFeedUrlDialog\|var drawerState" $J/drawer/feed/FeedOptionVM.kt
grep -n "suspend fun seed\|suspend fun stored\|suspend fun createGroup\|suspend fun cleanUp\|fun realRowCounts\|fun probeRowCount" app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/BlueskyGuardRig.kt | cut -c1-90
grep -n "fun app()" app/src/androidTest/java/com/mckimquyen/reader/domain/sv/BackgroundSyncRig.kt
grep -n "name=\"confirm\"\|name=\"cancel\"" app/src/main/res/values/strings.xml
git status --short app
```
Expected: mỗi mẫu có dòng; `git status` không có thay đổi trong `app`.

- [ ] **Step 2: Tạo tệp test**

```bash
cat > app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/FeedDrawerLongPressDeviceTest.kt <<'EOF'
package com.mckimquyen.reader.ui.page.home.feed

import androidx.activity.ComponentActivity
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.R
import com.mckimquyen.reader.RApp
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.sv.LocalRssSvFactory
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper
import com.mckimquyen.reader.ui.ext.currentAccountType
import com.mckimquyen.reader.ui.page.home.feed.drawer.feed.FeedOptionDrawer
import com.mckimquyen.reader.ui.page.home.feed.drawer.feed.FeedOptionViewModel
import com.mckimquyen.reader.ui.page.home.feed.subs.BlueskyGuardRig
import com.mckimquyen.reader.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

private const val CLASS_TIMEOUT_SECONDS = 90L
private const val WAIT_MILLIS = 15_000L
private const val NOT_YET_REAL_ROWS = -1L

/** Owns the view models of the probe and hands out the real [FeedOptionViewModel], built from the running app, without Hilt. */
private class DrawerTestOwner(app: RApp) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
    override val viewModelStore = ViewModelStore()
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FeedOptionViewModel(app.rssSv, Dispatchers.Main, app.ioDispatcher) as T
    }
}

/**
 * The whole way a user edits a source address, on the REAL screen pieces: a long press on the real FeedItem opens the real
 * FeedOptionDrawer for that source, a long press on the address line opens the real change-address dialog, and Confirm saves into the
 * phone's real Room and closes both the dialog and the drawer. One FeedOptionViewModel (from a ViewModelStoreOwner) serves every
 * hiltViewModel() call, as it does in the app. Long presses are Compose touch actions on the nodes, never screen coordinates; the text
 * is replaced with ONE action and never retyped; showDrawer() is never called by the test (the long press calls it).
 * NOT covered: FeedsPage (the list of groups and sources), swiping the drawer by hand, the real keyboard, the Delete and Clear buttons,
 * moving a source to another group, the notification switch, web and Nostr sources, Fever and Google Reader.
 */
@OptIn(ExperimentalMaterialApi::class)
@RunWith(AndroidJUnit4::class)
class FeedDrawerLongPressDeviceTest {
    @get:Rule val classTimeout: Timeout = Timeout.seconds(CLASS_TIMEOUT_SECONDS)
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var rig: BlueskyGuardRig
    private var realRowsBefore: Pair<Long, Long> = Pair(NOT_YET_REAL_ROWS, NOT_YET_REAL_ROWS)
    private var owner: DrawerTestOwner? = null
    private var shown: FeedOptionViewModel? = null

    @Before
    fun setUp() {
        check(compose.activity.applicationContext.currentAccountType == AccountType.Local.id) {
            "The address edit only runs for the Local account; this phone is on another account"
        }
        rig = BlueskyGuardRig()
        runBlocking {
            rig.cleanUp()
            realRowsBefore = rig.realRowCounts()
            rig.createGroup()
        }
    }

    /** Runs even when a test failed: the view model first (it collects Flows in its own scope), then the probe rows. */
    @After
    fun tearDown() {
        try {
            owner?.viewModelStore?.clear()
            runBlocking { rig.cleanUp() }
        } finally {
            rig.close()
        }
    }

    private val confirmLabel get() = compose.activity.getString(R.string.confirm)
    private val cancelLabel get() = compose.activity.getString(R.string.cancel)

    /** Numbers and flags only: the screen can show the owner's own text. */
    private fun describe(): String = try {
        val state = shown?.feedOptionUiState?.value
        "feedLoaded=${state?.feed != null} drawerVisible=${state?.drawerState?.isVisible} dialogVisible=${state?.changeUrlDialogVisible} " +
            "newUrlLength=${state?.newUrl?.length} editableFields=${compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size}"
    } catch (e: Throwable) {
        "describe failed: ${e.javaClass.simpleName}"
    }

    private fun waitFor(what: String, condition: () -> Boolean) {
        try {
            compose.waitUntil(WAIT_MILLIS, condition)
        } catch (e: Throwable) {
            throw AssertionError("Never saw: $what | ${describe()}", e)
        }
    }

    private fun state() = requireNotNull(shown) { "the screen was not shown yet" }.feedOptionUiState.value
    private fun drawerIsVisible() = state().drawerState.isVisible
    private fun dialogIsOpen() = state().changeUrlDialogVisible

    private fun fieldText(): String? = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        .singleOrNull()?.config?.getOrNull(SemanticsProperties.EditableText)?.text

    private fun dialogIsGone() = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty()

    private fun stored(id: String): Feed = requireNotNull(runBlocking { rig.stored(id) }) { "the probe source $id is not in Room" }

    /** Seeds a probe source and puts the real drawer with the real row of that source on the screen (the drawer is still hidden). */
    private fun showTheRowOf(id: String): Feed {
        runBlocking {
            rig.seed(
                id,
                url = BlueskyFeedMapper.feedUrl(HANDLE_OLD),
                sourceUrl = BlueskyFeedMapper.feedUrl(DID_OLD),
                isNotification = true,
                isFullContent = true,
            )
        }
        val seeded = stored(id)
        val testOwner = DrawerTestOwner(LocalRssSvFactory.app())
        owner = testOwner
        compose.setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides testOwner) {
                AppTheme(useDarkTheme = false) {
                    FeedOptionDrawer { FeedItem(feed = seeded, isEnded = { true }, isExpanded = { true }) }
                }
            }
        }
        // The same view model hiltViewModel() hands to FeedItem and FeedOptionDrawer: same store, same default key.
        shown = compose.runOnUiThread { ViewModelProvider(testOwner)[FeedOptionViewModel::class.java] }
        return seeded
    }

    private fun longPressTheRow(seeded: Feed) {
        compose.onNode(hasText(seeded.name), useUnmergedTree = true).performTouchInput { longClick() }
    }

    private fun longPressTheAddress(seeded: Feed) {
        compose.onNode(hasText(seeded.url), useUnmergedTree = true).performTouchInput { longClick() }
    }

    /** The whole first half of the user's way: long press the row, wait for the drawer showing THIS source's address. */
    private fun openTheDrawer(seeded: Feed) {
        longPressTheRow(seeded)
        waitFor("the drawer for the source") {
            drawerIsVisible() && state().feed?.id == seeded.id &&
                compose.onAllNodes(hasText(seeded.url), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openTheDialog(seeded: Feed) {
        openTheDrawer(seeded)
        longPressTheAddress(seeded)
        waitFor("the address field of the dialog") { dialogIsOpen() && fieldText() != null }
    }

    /** ONE action replaces the whole text; then the view model must hold it too (the text really travelled through the dialog). */
    private fun replaceText(text: String) {
        compose.onNode(hasSetTextAction()).performTextReplacement(text)
        waitFor("the replaced text in the field and in the view model") { fieldText() == text && state().newUrl == text }
    }

    private fun waitUntilBothAreClosed(what: String, drawerToo: Boolean) {
        waitFor(what) { !dialogIsOpen() && dialogIsGone() && (!drawerToo || !drawerIsVisible()) }
    }

    // ---- the long press on the row

    @Test
    fun longPressOnTheSourceRow_opensTheDrawerForThatSource() {
        val seeded = showTheRowOf("row")
        assertTrue("the drawer starts hidden", !drawerIsVisible())
        openTheDrawer(seeded)
        assertEquals("the drawer shows the source that was pressed", seeded.id, state().feed?.id)
        assertEquals("opening the drawer saves nothing", seeded, stored("row"))
    }

    // ---- the long press on the address line

    @Test
    fun longPressOnTheAddress_opensTheChangeDialogWithTheCurrentAddress() {
        val seeded = showTheRowOf("address")
        openTheDialog(seeded)
        waitFor("the current address in the field") { fieldText() == seeded.url }
        assertEquals("opening the dialog saves nothing", seeded, stored("address"))
    }

    // ---- the whole edit

    @Test
    fun editingTheAddressAndConfirming_savesBothColumns_andClosesTheDialogAndTheDrawer() {
        val seeded = showTheRowOf("confirm")
        openTheDialog(seeded)
        replaceText(NEW_ADDRESS)
        compose.onNode(hasText(confirmLabel)).assertHasClickAction().assertIsEnabled().performClick()
        waitUntilBothAreClosed("the dialog and the drawer to close after Confirm", drawerToo = true)
        val saved = stored("confirm")
        assertEquals("the address typed in the dialog", NEW_ADDRESS, saved.url)
        assertEquals("the address the sync reads must change too", NEW_ADDRESS, saved.sourceUrl)
        assertEquals("only the two address columns may change", seeded, saved.copy(url = seeded.url, sourceUrl = seeded.sourceUrl))
    }

    @Test
    fun cancellingTheEdit_savesNothing() {
        val seeded = showTheRowOf("cancel")
        openTheDialog(seeded)
        replaceText(NEW_ADDRESS)
        compose.onNode(hasText(cancelLabel)).assertHasClickAction().performClick()
        waitUntilBothAreClosed("the dialog to close after Cancel", drawerToo = false)
        assertEquals("Cancel must leave the saved source exactly as it was", seeded, stored("cancel"))
        assertEquals("the unsaved address is forgotten", "", state().newUrl)
        assertTrue("Cancel closes only the dialog: the drawer stays open", drawerIsVisible())
    }

    // ---- cleanliness

    @Test
    fun afterCleanUp_nothingOfTheProbeIsLeftAnywhere_andTheRealDataIsUntouched() {
        runBlocking { rig.seed("tmp", url = BlueskyFeedMapper.feedUrl(HANDLE_OLD), sourceUrl = null) }
        assertTrue(rig.probeRowCount() > 0)
        runBlocking { rig.cleanUp() }
        assertEquals(0L, rig.probeRowCount())
        assertEquals("the owner's own feeds and articles are exactly as before", realRowsBefore, rig.realRowCounts())
    }

    private companion object {
        const val HANDLE_OLD = "alice-front02v.bsky.social"
        const val HANDLE_NEW = "alice-renamed-front02v.bsky.social"
        const val DID_OLD = "did:plc:front02voldoldoldoldoldo"
        val NEW_ADDRESS: String = BlueskyFeedMapper.feedUrl(HANDLE_NEW)
    }
}
EOF
```

- [ ] **Step 3: Biên dịch nền (chờ `.exit`)**

```bash
rm -f /tmp/v-build1.exit; ( ./gradlew assembleDevDebug assembleDevDebugAndroidTest > /tmp/v-build1.log 2>&1; echo $? > /tmp/v-build1.exit ) > /dev/null 2>&1 &
for i in $(seq 1 100); do test -f /tmp/v-build1.exit && break; sleep 5; done; cat /tmp/v-build1.exit; grep -E "^e: |error:" /tmp/v-build1.log | head; tail -3 /tmp/v-build1.log
```
Expected: `0`, `BUILD SUCCESSFUL`. Nếu lỗi: đọc đúng dòng `e:`, sửa tên thật (đừng đoán). Điểm dễ lệch: `BlueskyGuardRig` là lớp công khai ở gói `.subs` (đã import); `ViewModelProvider(owner)` cần owner là `HasDefaultViewModelProviderFactory` (đúng); `compose.setContent` của rule trên `ComponentActivity` (như `FeedItemDeviceTest`); `FeedItem` cùng gói nên không cần import; `isVisible` là thuộc tính của `ModalBottomSheetState` (material 1.6.8).

- [ ] **Step 4: Commit test (chưa chạy trên máy)**

```bash
git add app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/FeedDrawerLongPressDeviceTest.kt
git commit -m "test(front-02v): check on the phone the long press on a source, the options drawer and the address edit

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: Chạy trên Tecno (1 lượt, rồi 20, rồi 40) với watcher

**Files:** không sửa tệp nguồn. Tạo tạm `/tmp/kj7_watch2.sh` (không commit).

**Interfaces:** Consumes: Task 1 đã biên dịch. Produces: số liệu lượt xanh/đỏ cho báo cáo.

- [ ] **Step 1: Kiểm công cụ còn tồn tại (`/tmp` có thể bị dọn) và dựng watcher cho gói mới**

```bash
ls -la /tmp/kj7_gate.sh /tmp/kj7_backup.py /tmp/kj7_restore.py /tmp/kj7_watch.sh /tmp/kj7-root /tmp/kj7_mut_u.sh /tmp/kj7_u_batch.sh /tmp/kj7_u_rerun.sh /tmp/u_check.py
sed -e 's#^CLS=com.mckimquyen.reader.ui.page.home.feed.subs.\$1; N=\${2:-40}#CLS=com.mckimquyen.reader.ui.page.home.feed.$1; N=${2:-40}#' /tmp/kj7_watch.sh > /tmp/kj7_watch2.sh
grep -n "^CLS=" /tmp/kj7_watch2.sh
```
Expected: đủ 9 tệp (thiếu: dừng và báo; không tự dựng lại cổng/sao lưu từ trí nhớ); dòng `CLS=com.mckimquyen.reader.ui.page.home.feed.$1; N=${2:-40}`. Tên lớp truyền vào là `FeedDrawerLongPressDeviceTest` (gói `ui.page.home.feed`).

- [ ] **Step 2: Cổng, trong MỘT LỆNH RIÊNG**

```bash
zsh /tmp/kj7_gate.sh 2>&1 | head -2
```
Expected: dòng đầu `GATE OK: ...`. Khác: **dừng và hỏi owner**.

- [ ] **Step 3: Một lượt thử (nền, chờ ALLDONE)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f /tmp/kj7-v-r1.log; ( zsh /tmp/kj7_watch2.sh FeedDrawerLongPressDeviceTest 1 > /tmp/kj7-v-r1.log 2>&1 ) > /dev/null 2>&1 &
for i in $(seq 1 100); do grep -q ALLDONE /tmp/kj7-v-r1.log 2>/dev/null && break; sleep 5; done; cat /tmp/kj7-v-r1.log
```
Expected: `run1: OK (5 tests) | ... | after-run user=0 accel=1`, `restore_exit=0`, `RESTORE OK`, `datastore: now=9ca1c202… before=9ca1c202…`, `rotation: 1 0`, `GATE OK`, `ALLDONE`.

Nếu đỏ: đọc `/tmp/kj7-watch-run-1.log` (dòng `INSTRUMENTATION_STATUS: stack=`). **Không** thêm `sleep`, không tăng timeout, **không đổi đồng hồ**. Phân loại:
- Lỗi biên dịch/ khẳng định sai do hiểu nhầm API (ví dụ không tìm thấy node theo `hasText`): sửa test, quay lại Task 1 Step 3.
- **`ComposeNotIdleException`/`AppNotIdleException`/hết hạn ở `waitForIdle`** (Compose bận do `ModalBottomSheet`): **dừng, đo (test nào, bước nào), hỏi owner**.
- Hành vi thật khác kỳ vọng (ví dụ ngăn kéo không mở dù bấm giữ): dừng, systematic-debugging, hỏi owner.
- Thông báo "Never saw: the drawer for the source" kèm `feedLoaded=false`: bấm giữ không tới được node (hoặc `showDrawer` không chạy): đó là bằng chứng mới, ghi `describe()`.

- [ ] **Step 4: Cổng riêng, rồi 20 lượt**

```bash
zsh /tmp/kj7_gate.sh 2>&1 | head -1
```
Rồi (lệnh sau, riêng):
```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f /tmp/kj7-v-m1.log; ( zsh /tmp/kj7_watch2.sh FeedDrawerLongPressDeviceTest 20 > /tmp/kj7-v-m1.log 2>&1 ) > /dev/null 2>&1 &
for i in $(seq 1 115); do grep -q ALLDONE /tmp/kj7-v-m1.log 2>/dev/null && break; sleep 5; done
mkdir -p /tmp/kj7-v-m1-runs; cp /tmp/kj7-watch-run-*.log /tmp/kj7-watch-top-*.txt /tmp/kj7-v-m1-runs/
python3 - <<'EOF'
import re
rows = []
for n in range(1, 21):
    r = open(f'/tmp/kj7-v-m1-runs/kj7-watch-run-{n}.log', encoding='utf-8').read()
    top = open(f'/tmp/kj7-v-m1-runs/kj7-watch-top-{n}.txt', encoding='utf-8').read()
    red = 'Failures' in r
    busy = any(k in r for k in ('ComposeNotIdle', 'AppNotIdle'))
    ad = 'No compose hierarchies found' in r and 'AdActivity' in top
    rows.append((red, 'AdActivity' in top, busy, ad))
red = [x for x in rows if x[0]]
print('red', len(red), '| red with ad', sum(x[1] for x in red), '| red BUSY', sum(x[2] for x in red), '| green with ad', sum(x[1] for x in rows if not x[0]))
EOF
grep -E "ROTATION CHANGED|GATE-STOP|restore_exit|datastore|rotation:|ALLDONE" /tmp/kj7-v-m1.log
```
Expected: `red 0` hoặc mọi lượt đỏ có `AdActivity` (ghi số thật), `red BUSY 0`, không `ROTATION CHANGED`, băm DataStore trùng. Quá 115×5 giây mà chưa `ALLDONE`: chạy lại **chỉ** vòng chờ. Mỗi lượt đỏ: đọc nguyên nhân **trước khi** dùng. Quy tắc: đỏ do quảng cáo ghi nhận (owner dặn bỏ qua); đỏ **không** do quảng cáo từ 2 lượt trong 40, hoặc bất kỳ lượt "bận": **dừng và hỏi owner**.

- [ ] **Step 5: Cổng riêng, rồi 20 lượt nữa (cộng thành 40)**

Như Step 4 nhưng log `/tmp/kj7-v-m2.log` và thư mục `/tmp/kj7-v-m2-runs`. Cộng hai vòng, ghi bốn ô (xanh/đỏ × có/không quảng cáo).

- [ ] **Step 6: Kiểm máy sạch sau các vòng (chỉ đọc)**

```bash
S=115333744A005844
adb -s $S shell settings get system user_rotation; adb -s $S shell settings get system accelerometer_rotation
adb -s $S shell pm list packages com.mckimquyen.reader.test | wc -l
zsh /tmp/kj7_gate.sh 2>&1 | head -1
```
Expected: `0`, `1`, `0`, `GATE OK`.

---

### Task 3: Mutation chạy trên máy (5 đột biến)

**Files:** tạm, không commit: `/tmp/kj7_mut_v.sh`, `/tmp/kj7_v_batch.sh`, `/tmp/kj7_v_rerun.sh`, `/tmp/v_check.py`. Đột biến tạm trong bốn tệp production (luôn khôi phục).

**Interfaces:** Consumes: `/tmp/kj7_mut_u.sh` (mẫu: `<label> <file> <old> <new>`, `base` = không đổi), `/tmp/kj7_u_batch.sh`, `/tmp/kj7_u_rerun.sh`, `/tmp/u_check.py`. Produces: bảng đột biến → tập test đỏ đo được.

- [ ] **Step 1: Kiểm các mẫu đột biến duy nhất (mỗi mẫu đúng 1 lần)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
python3 - <<'EOF'
# -*- coding: utf-8 -*-
J = "app/src/main/java/com/mckimquyen/reader/ui/page/home/feed/"
src = {k: open(J + v, encoding="utf-8").read() for k, v in {
    "item": "FeedItem.kt", "drawer": "drawer/feed/FeedOptionDrawer.kt", "view": "FeedOptionView.kt", "vm": "drawer/feed/FeedOptionVM.kt"}.items()}
pats = [
 ("item", "                        feedOptionViewModel.showDrawer(scope, feed.id)\n"),
 ("drawer", "            feedOptionViewModel.changeFeedUrl()\n            feedOptionViewModel.hideDrawer(scope)\n"),
 ("drawer", "                        if (feedOptionViewModel.rssService.get().update) {\n                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)\n                            feedOptionViewModel.showFeedUrlDialog()\n                        }\n"),
 ("view", "                    onLongClick = onLongClick,\n"),
 ("vm", "                newUrl = _feedOptionUiState.value.feed?.url ?: \"\","),
]
print([src[k].count(p) for k, p in pats])
EOF
```
Expected: `[1, 1, 1, 1, 1]`. Khác 1: đọc lại tệp và sửa mẫu (không chạy batch).

- [ ] **Step 2: Dựng script từ mẫu 02u**

```bash
sed -e 's#ui.page.home.feed.subs.ChangeUrlDialogDeviceTest#ui.page.home.feed.FeedDrawerLongPressDeviceTest#' -e 's/kj7-u-mut-/kj7-v-mut-/g' -e 's/kj7-u-mutbackup/kj7-v-mutbackup/' /tmp/kj7_mut_u.sh > /tmp/kj7_mut_v.sh
grep -n "^TEST=\|ok5\|OK (5\|kj7-v-" /tmp/kj7_mut_v.sh | head -6
```
Expected: `TEST=com.mckimquyen.reader.ui.page.home.feed.FeedDrawerLongPressDeviceTest`, `ok5`, `OK (5 tests)` (cùng số test với 02u: 5), các tệp `kj7-v-mut-*`.

Viết `/tmp/kj7_v_batch.sh` và `/tmp/kj7_v_rerun.sh` từ mẫu 02u, đổi tiền tố `u` thành `v`, `M=/tmp/kj7_mut_v.sh`, và nhãn/tệp đột biến như sau (heredoc, **quote 'EOF'**):

```bash
cat > /tmp/kj7_v_batch.sh <<'EOF'
#!/bin/zsh
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou || exit 90
S=115333744A005844; APP=com.mckimquyen.reader
J=app/src/main/java/com/mckimquyen/reader/ui/page/home/feed
IT=$J/FeedItem.kt; DR=$J/drawer/feed/FeedOptionDrawer.kt; VW=$J/FeedOptionView.kt; VM=$J/drawer/feed/FeedOptionVM.kt
cp -p $IT /tmp/kj7-v-orig-it.kt; cp -p $DR /tmp/kj7-v-orig-dr.kt; cp -p $VW /tmp/kj7-v-orig-vw.kt; cp -p $VM /tmp/kj7-v-orig-vm.kt
zsh /tmp/kj7_gate.sh > /tmp/kj7-v-batch-gate0.log 2>&1 || { echo "GATE-STOP at start: $(head -1 /tmp/kj7-v-batch-gate0.log)"; echo ALLDONE; exit 92; }
python3 /tmp/kj7_backup.py > /tmp/kj7-v-batch-backup.log 2>&1 || { echo BACKUP-FAILED; echo ALLDONE; exit 93; }
R=$(cat /tmp/kj7-root); BEFORE=$(shasum $R/kj7/settings.preferences_pb | cut -c1-40)
M=/tmp/kj7_mut_v.sh
zsh $M base
zsh $M v1-no-show-drawer $IT $'                        feedOptionViewModel.showDrawer(scope, feed.id)\n' $'                        // v1: showDrawer removed\n'
zsh $M v2-no-hide-drawer $DR $'            feedOptionViewModel.changeFeedUrl()\n            feedOptionViewModel.hideDrawer(scope)\n' $'            feedOptionViewModel.changeFeedUrl()\n'
zsh $M v3-drawer-long-click-unwired $DR $'                        if (feedOptionViewModel.rssService.get().update) {\n                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)\n                            feedOptionViewModel.showFeedUrlDialog()\n                        }\n' $'                        // v3: long click unwired\n'
zsh $M v4-view-long-click-dead $VW $'                    onLongClick = onLongClick,\n' $'                    onLongClick = {},\n'
zsh $M v5-dialog-opens-empty $VM $'                newUrl = _feedOptionUiState.value.feed?.url ?: "",' $'                newUrl = "",'
zsh $M base
for p in "$IT:/tmp/kj7-v-orig-it.kt" "$DR:/tmp/kj7-v-orig-dr.kt" "$VW:/tmp/kj7-v-orig-vw.kt" "$VM:/tmp/kj7-v-orig-vm.kt"; do
  cmp -s "${p#*:}" "${p%%:*}" && echo "final ${p%%:*}: identical" || echo "final ${p%%:*}: DIFFERENT"
done
echo "git diff main: $(git diff --stat -- app/src/main | wc -l) lines"
python3 /tmp/kj7_restore.py > /tmp/kj7-v-batch-restore.log 2>&1; echo "restore_exit=$?"; grep -E "DIFFERENT|RESTORE" /tmp/kj7-v-batch-restore.log
echo "datastore: now=$(adb -s $S exec-out run-as $APP cat files/datastore/settings.preferences_pb | shasum | cut -c1-40) before=$BEFORE"
adb -s $S uninstall $APP.test
echo "rotation: $(adb -s $S shell settings get system accelerometer_rotation | tr -d '\r') $(adb -s $S shell settings get system user_rotation | tr -d '\r')"
zsh /tmp/kj7_gate.sh | head -1
echo ALLDONE
EOF
```

`/tmp/kj7_v_rerun.sh`: lấy `/tmp/kj7_u_rerun.sh`, đổi `u` thành `v` ở tên tệp, `M=/tmp/kj7_mut_v.sh`, sao lưu bốn tệp như batch, hàm `try` giữ nguyên (chạy lại tối đa 3 lần khi log có chữ ký quảng cáo `Never saw: ... | describe failed`; **chữ ký quảng cáo của test này** là thông báo bắt đầu `Never saw:` kèm `describe failed: IllegalStateException`), và chỉ chạy lại **các nhãn bị quảng cáo đè** (mặc định liệt kê cả sáu nhãn; xóa dòng của nhãn đã sạch trước khi chạy).

Tập đỏ mong đợi (kiểm **tự động**) viết thành `/tmp/v_check.py`:

```bash
cat > /tmp/v_check.py <<'EOF'
# -*- coding: utf-8 -*-
import re, sys
T1 = "longPressOnTheSourceRow_opensTheDrawerForThatSource"
T2 = "longPressOnTheAddress_opensTheChangeDialogWithTheCurrentAddress"
T3 = "editingTheAddressAndConfirming_savesBothColumns_andClosesTheDialogAndTheDrawer"
T4 = "cancellingTheEdit_savesNothing"
EXPECTED = {
    "v1-no-show-drawer": {T1, T2, T3, T4},
    "v2-no-hide-drawer": {T3},
    "v3-drawer-long-click-unwired": {T2, T3, T4},
    "v4-view-long-click-dead": {T2, T3, T4},
    "v5-dialog-opens-empty": {T2, T3, T4},
}
bad = 0
for label, want in EXPECTED.items():
    t = open(f"/tmp/kj7-v-mut-{label}-run.log", encoding="utf-8").read()
    failing = set(re.findall(r"^\d+\) (\w+)\(", t, re.M))
    ad = "describe failed: IllegalStateException" in t
    ok = (failing == want) and not ad
    bad += 0 if ok else 1
    print(("OK   " if ok else "BAD  ") + label, "expected", sorted(want), "got", sorted(failing), "AD-SPOILED" if ad else "clean")
base = open("/tmp/kj7-v-mut-base-run.log", encoding="utf-8").read()
print("base:", "OK (5 tests)" if "OK (5 tests)" in base else "NOT GREEN")
bad += 0 if "OK (5 tests)" in base else 1
print("ALL-MATCH" if bad == 0 else "MISMATCHES=%d" % bad)
EOF
```

- [ ] **Step 3: Cổng riêng, rồi chạy batch nền, chờ ALLDONE**

```bash
zsh /tmp/kj7_gate.sh 2>&1 | head -1
```
Rồi:
```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f /tmp/kj7-v-batch.log; ( zsh /tmp/kj7_v_batch.sh > /tmp/kj7-v-batch.log 2>&1 ) > /dev/null 2>&1 &
for i in $(seq 1 115); do grep -q ALLDONE /tmp/kj7-v-batch.log 2>/dev/null && break; sleep 5; done; cut -c1-300 /tmp/kj7-v-batch.log
```
Không sửa bốn tệp production cho tới `ALLDONE`. Chưa xong sau 115×5 giây: chạy lại **chỉ** vòng chờ. Mỗi đột biến mất vài phút (build + chạy).

- [ ] **Step 4: Đối chiếu, chạy lại nhãn bị quảng cáo đè, khôi phục**

```bash
python3 /tmp/v_check.py
```
Expected cuối cùng: `ALL-MATCH` với mọi dòng `clean`. Nhãn nào `AD-SPOILED` (hoặc `base` không xanh): chạy `/tmp/kj7_v_rerun.sh` cho **đúng nhãn đó** (tối đa 3 lần mỗi nhãn, như 02u), rồi chạy lại `v_check.py` trên bản ghi mới. Kiểm cuối batch: bốn dòng `final … identical`, `git diff main: 0 lines`, `restore_exit=0`, băm DataStore trùng, `rotation: 1 0`, `GATE OK`. Mutant sống sót (test không đỏ): test yếu, thêm khẳng định (quay lại Task 1) rồi chạy lại batch. Đột biến làm thêm test khác đỏ ngoài dự kiến: đọc lý do, sửa bảng mong đợi hoặc test, ghi trong báo cáo. **Nếu một test "bận" (Compose không rảnh) xuất hiện trong đợt đột biến mà không có ở `base`: ghi và hỏi owner.**

---

### Task 4: JVM, kiểm phạm vi, tài liệu, commit

**Files:**
- Modify: `doc/feature.md` (dòng 02p, 02t, 02u và dòng 02v mới), `docs/superpowers/specs/2026-11-18-front-02v-feed-drawer-long-press-device-test-design.md` (mục "Điều chỉnh khi thực thi")
- Create: `doc/task/done/FRONT-02v_feed-drawer-long-press-device-test_DONE.md`

- [ ] **Step 1: JVM full suite đo lại (nền, chờ `.exit`)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f /tmp/v-jvm.exit; ( ./gradlew testDevDebugUnitTest --rerun-tasks assembleDevDebug assembleDevDebugAndroidTest > /tmp/v-jvm.log 2>&1; echo $? > /tmp/v-jvm.exit ) > /dev/null 2>&1 &
for i in $(seq 1 115); do test -f /tmp/v-jvm.exit && break; sleep 5; done; cat /tmp/v-jvm.exit; grep -E "FAILED|tests completed" /tmp/v-jvm.log | head -5; tail -3 /tmp/v-jvm.log
python3 - <<'EOF'
import glob, re
t = f = e = s = 0
for p in glob.glob('app/build/test-results/testDevDebugUnitTest/*.xml'):
    h = open(p, encoding='utf-8').read(2000)
    m = re.search(r'tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"', h)
    a, b, c, d = map(int, m.groups()); t += a; s += b; f += c; e += d
print('tests', t, 'skipped', s, 'failures', f, 'errors', e)
EOF
```
Expected: `0`, `BUILD SUCCESSFUL`, 0 lỗi (ghi đúng số đo được). Nếu có test đỏ **không liên quan** (chỉ `androidTest` thay đổi): chạy riêng lớp đó 3 lần và chạy lại cả bộ một lần; ghi thẳng cả hai kết quả (không bỏ qua, không giấu).

- [ ] **Step 2: Kiểm phạm vi diff (không production)**

```bash
git status --short; git diff --stat -- app/src/main app/build.gradle app/schemas | tail -2
git diff --name-only HEAD~1 HEAD
```
Expected: không có tệp trong `app/src/main`, `app/build.gradle`, `app/schemas`, manifest; commit test chỉ thêm `FeedDrawerLongPressDeviceTest.kt`.

- [ ] **Step 3: Viết báo cáo, cập nhật `doc/feature.md` và spec**

Báo cáo `doc/task/done/FRONT-02v_feed-drawer-long-press-device-test_DONE.md` (tiếng Việt đơn giản) gồm: điều test chứng minh (5 test); bảng lượt (1 + 20 + 20: xanh/đỏ, có/không quảng cáo, nguyên nhân từng lượt đỏ, và **có lượt nào Compose bận không**); bảng mutation 5 hàng với tập test đỏ **đo được** (kèm kết quả `v_check.py`, ghi nhãn nào phải chạy lại vì quảng cáo); băm DataStore trước/sau; `user_rotation` sau các vòng; JVM số **đo lại** (kèm lần đỏ chập chờn nếu có); không kiểm gì (`FeedsPage`, vuốt tay, bàn phím thật, Xóa nguồn/Xóa bài, đổi nhóm, thông báo, WEB/NOSTR, lint không quét `androidTest`); quảng cáo: owner dặn bỏ qua, ghi số thật.

`doc/feature.md`: dòng 02p, 02t, 02u thêm "cả chuỗi bấm giữ và ngăn kéo đã kiểm trên máy bằng FRONT-02v"; thêm dòng FRONT-02v sau dòng FRONT-02u (✅ ngày, đường dẫn báo cáo, số liệu thật, nói thẳng phần chưa kiểm).

Spec: nối mục "Điều chỉnh khi thực thi" (bảng "Điều chỉnh so với spec" của plan, cộng mọi điều chỉnh phát sinh khi chạy).

- [ ] **Step 4: Commit tài liệu**

```bash
git add doc docs
git commit -m "docs(front-02v): report that the long press, the options drawer and the address edit were checked on the phone

Co-Authored-By: Claude Code <noreply@anthropic.com>"
git log --oneline | head -3; git status --short
```
Expected: chỉ `.idea/workspace.xml` còn sửa (không commit).

---

## Self-review

1. **Spec coverage:** bấm giữ `FeedItem` → ngăn kéo đúng nguồn (T1, Task 1); bấm giữ dòng địa chỉ → hộp thoại với địa chỉ cũ (T2); sửa + Xác nhận → hai cột + đóng hộp thoại **và ngăn kéo** (T3); Hủy không lưu, ngăn kéo vẫn mở (T4); dọn dẹp (T5); một ViewModel cho mọi `hiltViewModel()` (điều chỉnh 5, T1 tự kiểm); không gọi `showDrawer` trực tiếp, không tự đổi đồng hồ → Global Constraints + Task 2 Step 3/4; an toàn thiết bị, 1+20+20 lượt, quy tắc quảng cáo/bận → Task 2; đột biến v1 đến v5 và so sánh tự động → Task 3 (v4 đổi thành `onLongClick = {}` vì `onClick` của địa chỉ có thể bật ứng dụng ngoài, ghi ở điều chỉnh 10); JVM đo lại, báo cáo, `feature.md` (02p, 02t, 02u) → Task 4; nói thẳng phần không kiểm → Global Constraints và Task 4.
2. **Placeholder scan:** không "TBD"/"TODO"; mọi bước mã có mã đầy đủ (test, batch, `v_check.py`). Một chỗ có ý thức: `/tmp/kj7_v_rerun.sh` mô tả là "từ mẫu `kj7_u_rerun.sh` đổi `u` thành `v`" kèm các thay đổi cụ thể, vì khung dài và đã có; Task 3 Step 2 yêu cầu `cat` mẫu trước.
3. **Nhất quán tên:** `showTheRowOf`, `openTheDrawer`, `openTheDialog`, `longPressTheRow`, `longPressTheAddress`, `replaceText`, `waitUntilBothAreClosed`, `drawerIsVisible`, `dialogIsOpen`, `fieldText`, `dialogIsGone`, `stored`, `state`, `DrawerTestOwner`, `HANDLE_OLD/NEW`, `DID_OLD`, `NEW_ADDRESS` khớp giữa Task 1 và các tập đột biến; tên 5 test khớp bảng ở đầu, `v_check.py` (T1 đến T4; T5 là dọn dẹp, không nằm trong tập đột biến). Mẫu đột biến v1 đến v5 khớp văn bản đã kiểm ở Task 3 Step 1.
4. **Rủi ro còn lại (nói thẳng):** (a) Compose có thể bận do `ModalBottomSheet`: dừng và hỏi, không tự đổi; (b) `longClick()` có thể không tới node trong `ModalBottomSheetLayout`/`combinedClickable` (thời gian `longClick()` mặc định của Compose test bằng ngưỡng bấm giữ của hệ thống cộng 100 ms, nên về lý thuyết đủ; nếu không, đó là bằng chứng mới, không tự đổi thời gian); (c) node `hasText(url)` chỉ có ở ngăn kéo nhưng sau khi hộp thoại mở có thêm một node trong ô nhập; test bấm trước khi hộp thoại mở nên không nhập nhằng, nhưng `onNode` với hai node cùng chữ sẽ ném lỗi nếu bấm lần hai; (d) quảng cáo toàn màn hình có thể cao hơn 29% vì test dài hơn; (e) `ModalBottomSheetState.isVisible` được đọc ngoài luồng chính (hợp lệ cho state snapshot, nhưng nếu thấy chập chờn thì ghi); (f) tên nguồn do rig cố định (`Bluesky · Probe`), `hasText` tìm theo chuỗi con nên cần bảo đảm không trùng với chữ khác trên màn hình (kiểm ở lượt thử đầu).
