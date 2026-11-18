# FRONT-02u — Test máy thật: hộp thoại "Đổi địa chỉ" nguồn (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (hoặc subagent-driven-development) để thực thi plan này từng task. Các bước dùng checkbox `- [ ]`.

**Goal:** Chứng minh trên Tecno KJ7 rằng hộp thoại thật `ChangeUrlDialog`, nối vào `FeedOptionViewModel` thật đúng như `FeedOptionDrawer` nối, hiện đúng địa chỉ cũ, đưa chữ mới vào ViewModel, "Xác nhận" lưu đúng hai cột vào Room thật, "Hủy" không lưu, và ô trống khóa nút "Xác nhận".

**Architecture:** Một tệp test mới `ChangeUrlDialogDeviceTest.kt` trên `ComponentActivity` (không `MainActivity`, không ngăn kéo, không `showDrawer`). `setContent` dựng `ChangeUrlDialog` thật với ba dây giống drawer (`inputNewUrl`, `hideFeedUrlDialog`, `changeFeedUrl`). Chữ thay vào ô bằng **một** lệnh `performTextReplacement`, nút bấm tìm bằng chữ (`activity.getString`), không tọa độ. Dùng lại `BlueskyGuardRig` (02r/02t) để gieo và đọc Room thật; **không sửa rig**.

**Tech Stack:** Compose UI test (`createAndroidComposeRule<ComponentActivity>`), `ViewModelProvider`, Room thật của app, `mockwebserver3` (qua rig, chỉ để dựng rig; test này không đồng bộ), `am instrument`, adb, Python 3.9.

## Global Constraints

- Nhánh `dev`; không worktree, không push, không PR, không commit `.idea`.
- Không đổi mã production, manifest, chuỗi, Room (v22), DataStore, `app/build.gradle`; không dependency mới. Chỉ thêm **một tệp** trong `app/src/androidTest` (`ChangeUrlDialogDeviceTest.kt`). **Mutation tạm** trong `TextFieldDlg.kt` và trong test luôn khôi phục + `cmp` + `git diff app/src/main` rỗng.
- Thiết bị **CHỈ Tecno KJ7 `115333744A005844`**; chỉ `adb -s 115333744A005844`; không `connected*`; chỉ `am instrument --user 0 -w -r -e class … com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner`; không MockK trên máy; không tọa độ; không mạng thật; **không `setRotation`**; **không gọi `showDrawer`**.
- Cổng `/tmp/kj7_gate.sh` phải in `GATE OK` **trong một lệnh RIÊNG** trước mỗi lần khởi chạy. Cổng dừng (app lạ ở foreground, khóa màn, xoay lệch): **dừng và hỏi owner**.
- Sao lưu/khôi phục **byte-exact 6 tệp** (`/tmp/kj7_backup.py`, `/tmp/kj7_restore.py`); băm DataStore `9ca1c202050c258b4aac9a063e27def350d737fb` trùng sau mỗi vòng; xoay `1 0` trước và sau mỗi lượt; không xóa DataStore; gỡ `com.mckimquyen.reader.test` cuối vòng.
- Test ghi tạm vào Room thật chỉ bằng dòng có tiền tố `front02r_probe_`; `@After` luôn `cleanUp()`.
- Gradle chạy NỀN, output ra file, chờ `.exit`/`ALLDONE`; "completed" của lệnh nền chỉ là lệnh khởi chạy.
- **Không gõ lại, không tăng timeout, không `sleep` để chữa lỗi.** Owner dặn **bỏ qua mọi việc liên quan quảng cáo** (không điều tra, không đổi cấu hình). Quy tắc lượt đỏ: đọc nguyên nhân từng lượt; đỏ do lỗi gõ chữ (02m) 1 trong 40 chấp nhận và ghi; **từ 2 lượt đỏ trong 40: dừng, điều tra theo systematic-debugging, hỏi owner**.
- Mutation: một script nền duy nhất, không sửa tệp đột biến cho tới `ALLDONE`; mỗi đột biến phải làm **đúng** tập test dự định đỏ (so sánh tự động bằng script python ở Task 3).
- zsh: không `echo ====`; `grep -r` quote mẫu, không dùng `--include`; sửa tệp bằng python; Python 3.9 cần `# -*- coding: utf-8 -*-` và `open(..., encoding="utf-8")`; macOS `sed -i ''`.
- R5: không magic number (hằng số có tên); test bắt buộc kèm; không báo "done" khi chưa dọn. R2: cập nhật `doc/feature.md`.
- Không dùng số JVM trong plan làm bằng chứng: đo lại lúc thực thi.
- Nói thẳng phần không kiểm: bấm giữ, `FeedOptionDrawer`/`hideDrawer`, bàn phím thật, nguồn WEB/NOSTR, Fever/Google Reader; lint không quét `androidTest`.

## Điều chỉnh so với spec (đã đọc code thật, 2026-11-18)

1. **State đọc bằng `collectAsStateValue()`** (`ui/ext/ExtStateFlow.kt`, `StateFlow<T>`, ngữ cảnh mặc định `Dispatchers.Default`), đúng cách `FeedOptionDrawer` đọc `feedOptionUiState`.
2. **Không bọc `AppTheme`:** 02s dựng `SubscribeDialog` không theme và chạy 40+ lượt; màu không ảnh hưởng hành vi. Drawer thật nằm trong theme của app, đây là khác biệt đã biết (nói trong báo cáo).
3. **Chữ nút lấy bằng `compose.activity.getString(R.string.confirm / R.string.cancel)`**, không cố định chữ. Máy Tecno đang ở `system_locales = en-US,vi-VN` (nút có thể là "Confirm/Cancel" hoặc "Xác nhận/Hủy" tùy locale ứng dụng); `getString` của chính Activity chạy test dùng cùng tài nguyên với `stringResource` trong hộp thoại.
4. **Nút "Xác nhận" là `TextButton(enabled = value.isNotBlank(), onClick = { focusManager.clearFocus(); onConfirm(value) })`**; nút "Hủy" là `TextButton(onClick = onDismissRequest)`. Cả hai tìm bằng `hasText(chữ) and hasClickAction()`.
5. **Test ô trống có hai lớp:** `assertIsNotEnabled()` (tất định, bắt đột biến `u4`) và bấm thử + kiểm Room không đổi (lớp yếu hơn vì ghi Room bất đồng bộ; chỉ bổ trợ).
6. **Test hủy tất định nhờ thứ tự:** đúng hành vi thì hộp thoại đóng **ngay** (`hideFeedUrlDialog`) và Room không bao giờ đổi; nếu "Hủy" gọi nhầm `onConfirm` (đột biến `u3`) thì hộp thoại chỉ đóng **sau khi** `updateFeed` ghi xong. Vì vậy test chờ hộp thoại đóng **rồi mới** đọc Room: đột biến sẽ thấy Room đã đổi.
7. **Đột biến u5 đổi so với spec:** spec ghi "bỏ nối `onConfirm` trong test" nhưng `u2` đã phủ nút Xác nhận; `u5` thành "test nối `onDismissRequest = {}`" (chứng minh test hủy không xanh giả nếu dây Hủy đứt).
8. **Mã dựng ViewModel/reflection/rig chép từ `FeedAddressEditDeviceTest` (02t), không tách chung:** giữ nguyên 02t đã commit và đã đo; trùng lặp ~25 dòng được chấp nhận.
9. **Đột biến `u1`–`u4` đổi `TextFieldDlg.kt`, dùng chung cho mọi hộp thoại nhập chữ:** chỉ tạm thời trên bản build thử, luôn khôi phục + `cmp`.

## Tập test đỏ mong đợi của đột biến (kiểm tự động ở Task 3)

Tên test viết tắt: **T1** `opening_showsTheCurrentAddressInTheField`, **T2** `replacingTheAddressAndConfirming_savesBothColumns_andClosesTheDialog`, **T3** `cancelling_closesTheDialog_andSavesNothing`, **T4** `anEmptyField_locksTheConfirmButton_andSavesNothing`, **T5** `afterCleanUp_nothingOfTheProbeIsLeftAnywhere_andTheRealDataIsUntouched`.

| Đột biến | Đổi gì | Test đỏ mong đợi | Vì sao |
|---|---|---|---|
| base | không | không (`OK (5 tests)`) | |
| u1 | `TextFieldDlg`: `onValueChange = {}` | T2, T3, T4 | chữ thay vào không tới ViewModel, ô vẫn giữ chữ cũ nên bước chờ chữ mới hết hạn |
| u2 | nút Xác nhận không gọi `onConfirm` | T2 | hộp thoại không bao giờ đóng |
| u3 | nút Hủy gọi `onConfirm(value)` | T3 | Room đã bị ghi khi hộp thoại đóng |
| u4 | bỏ `enabled = value.isNotBlank()` | T4 | `assertIsNotEnabled` đỏ |
| u5 | (test) `onDismissRequest = {}` | T3 | hộp thoại không đóng khi Hủy |

## Cấu trúc file

| File | Việc |
|---|---|
| Create `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/ChangeUrlDialogDeviceTest.kt` | 5 test (4 hành vi + dọn dẹp) |
| Tạm, **không commit**: `/tmp/kj7_mut_u.sh`, `/tmp/kj7_u_batch.sh`, `/tmp/u_check.py` | đột biến chạy trên máy (từ `kj7_mut_t.sh`, đổi lớp, số test) |
| Modify `doc/feature.md`, spec (mục "Điều chỉnh khi thực thi") | tài liệu |
| Create `doc/task/done/FRONT-02u_change-url-dialog-device-test_DONE.md` | báo cáo |

---

### Task 1: Viết `ChangeUrlDialogDeviceTest` và biên dịch (chưa chạm Tecno)

**Files:**
- Create: `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/ChangeUrlDialogDeviceTest.kt`

**Interfaces:**
- Consumes: rig 02r/02t (`BlueskyGuardRig()`: `createGroup`, `seed(id, url, sourceUrl, sourceType, icon, isNotification, isFullContent)`, `stored(id)`, `cleanUp`, `close`, `realRowCounts`, `probeRowCount`), `ChangeUrlDialog(visible, value, onValueChange, onDismissRequest, onConfirm)`, `FeedOptionViewModel(rssService, main, io)` với `showFeedUrlDialog()`, `inputNewUrl(String)`, `hideFeedUrlDialog()`, `changeFeedUrl()`, `feedOptionUiState: StateFlow<FeedOptionUiState>` (`changeUrlDialogVisible`, `newUrl`), `StateFlow<T>.collectAsStateValue()`, `R.string.confirm`, `R.string.cancel`.
- Produces: lớp `ChangeUrlDialogDeviceTest` với 5 test T1 đến T5 (tên đầy đủ ở bảng trên).

> Ghi chú TDD (nói thẳng): mã production đã có, nên test không có bước "đỏ trước". Bằng chứng "test bắt được lỗi" là **mutation chạy trên máy ở Task 3** (5 đột biến).

- [ ] **Step 1: Kiểm các tên sẽ chép còn đúng**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
grep -n "private val _feedOptionUiState\|fun showFeedUrlDialog\|fun inputNewUrl\|fun hideFeedUrlDialog\|fun changeFeedUrl" app/src/main/java/com/mckimquyen/reader/ui/page/home/feed/drawer/feed/FeedOptionVM.kt
grep -n "suspend fun seed\|suspend fun stored\|suspend fun createGroup\|suspend fun cleanUp\|fun realRowCounts\|fun probeRowCount" app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/BlueskyGuardRig.kt
grep -n "fun ChangeUrlDialog\|onDismissRequest\|onConfirm" app/src/main/java/com/mckimquyen/reader/ui/component/ChangeUrlDlg.kt
grep -n "fun <T> StateFlow<T>.collectAsStateValue" app/src/main/java/com/mckimquyen/reader/ui/ext/ExtStateFlow.kt
grep -n "name=\"confirm\"\|name=\"cancel\"" app/src/main/res/values/strings.xml
git status --short app
```
Expected: mỗi mẫu có dòng; `git status` không có thay đổi trong `app`.

- [ ] **Step 2: Tạo tệp test**

```bash
cat > app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/ChangeUrlDialogDeviceTest.kt <<'EOF'
package com.mckimquyen.reader.ui.page.home.feed.subs

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.sv.LocalRssSvFactory
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper
import com.mckimquyen.reader.ui.component.ChangeUrlDialog
import com.mckimquyen.reader.ui.ext.collectAsStateValue
import com.mckimquyen.reader.ui.ext.currentAccountType
import com.mckimquyen.reader.ui.page.home.feed.drawer.feed.FeedOptionUiState
import com.mckimquyen.reader.ui.page.home.feed.drawer.feed.FeedOptionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
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
private const val UI_STATE_FIELD = "_feedOptionUiState"
private const val EMPTY_TEXT = ""

/**
 * Whether the REAL change-address dialog (ChangeUrlDialog), wired to the real FeedOptionViewModel the way FeedOptionDrawer wires it,
 * does the right thing on the phone: it opens with the current address, the text replaced in its field reaches the view model,
 * "Confirm" saves both columns into the phone's real Room and closes, "Cancel" closes and saves nothing, and an empty field locks "Confirm".
 * The text is replaced with ONE action (performTextReplacement), never retyped. showDrawer() is never called and MainActivity is not started.
 * NOT covered: the long press that opens the dialog, FeedOptionDrawer and hideDrawer (the second wire of onConfirm), the real keyboard,
 * the sync after the edit (FRONT-02t), web and Nostr sources, Fever and Google Reader.
 */
@OptIn(ExperimentalMaterialApi::class)
@RunWith(AndroidJUnit4::class)
class ChangeUrlDialogDeviceTest {
    @get:Rule val classTimeout: Timeout = Timeout.seconds(CLASS_TIMEOUT_SECONDS)
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var rig: BlueskyGuardRig
    private var realRowsBefore: Pair<Long, Long> = Pair(NOT_YET_REAL_ROWS, NOT_YET_REAL_ROWS)
    private var shown: FeedOptionViewModel? = null

    @Before
    fun setUp() {
        check(compose.activity.applicationContext.currentAccountType == AccountType.Local.id) {
            "The change-address dialog only runs for the Local account; this phone is on another account"
        }
        rig = BlueskyGuardRig()
        runBlocking {
            rig.cleanUp()
            realRowsBefore = rig.realRowCounts()
            rig.createGroup()
        }
    }

    /** Runs even when a test failed: the probe sources must never stay in the phone's database. */
    @After
    fun tearDown() {
        try {
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
        "editableFields=${compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size} " +
            "dialogVisible=${state?.changeUrlDialogVisible} newUrlLength=${state?.newUrl?.length}"
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

    /** The real view model, built the way FeedItemDeviceTest and FeedAddressEditDeviceTest build it: from the running app, without Hilt. */
    private fun newViewModel(): FeedOptionViewModel {
        val app = LocalRssSvFactory.app()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                FeedOptionViewModel(app.rssSv, Dispatchers.Main, app.ioDispatcher) as T
        }
        return compose.runOnUiThread { ViewModelProvider(compose.activity, factory)[FeedOptionViewModel::class.java] }
    }

    /** Puts [feed] into the view model's state as the drawer would after loading it, WITHOUT calling showDrawer(). */
    private fun load(viewModel: FeedOptionViewModel, feed: Feed) {
        val field = FeedOptionViewModel::class.java.getDeclaredField(UI_STATE_FIELD).apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") // the field is a MutableStateFlow<FeedOptionUiState>; a wrong type fails loudly right here
        val state = field.get(viewModel) as MutableStateFlow<FeedOptionUiState>
        state.value = state.value.copy(feed = feed, selectedGroupId = feed.groupId)
    }

    /** The real dialog, wired exactly as FeedOptionDrawer wires it, except hideDrawer (there is no drawer in this test). */
    private fun showDialogFor(viewModel: FeedOptionViewModel) {
        compose.runOnUiThread {
            compose.activity.setContent {
                val state = viewModel.feedOptionUiState.collectAsStateValue()
                ChangeUrlDialog(
                    visible = state.changeUrlDialogVisible,
                    value = state.newUrl,
                    onValueChange = { viewModel.inputNewUrl(it) },
                    onDismissRequest = { viewModel.hideFeedUrlDialog() },
                    onConfirm = { viewModel.changeFeedUrl() },
                )
            }
        }
    }

    private fun fieldText(): String? = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        .singleOrNull()?.config?.getOrNull(SemanticsProperties.EditableText)?.text

    private fun dialogIsGone() = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty()

    /** Seeds a probe source, loads it into a new view model, opens the dialog and waits for its field. */
    private fun openDialogOn(id: String, icon: String? = null, isNotification: Boolean = false, isFullContent: Boolean = false): Feed {
        runBlocking {
            rig.seed(
                id,
                url = BlueskyFeedMapper.feedUrl(HANDLE_OLD),
                sourceUrl = BlueskyFeedMapper.feedUrl(DID_OLD),
                icon = icon,
                isNotification = isNotification,
                isFullContent = isFullContent,
            )
        }
        val seeded = stored(id)
        val viewModel = newViewModel()
        shown = viewModel
        load(viewModel, seeded)
        showDialogFor(viewModel)
        viewModel.showFeedUrlDialog()
        waitFor("the address field of the dialog") { fieldText() != null }
        return seeded
    }

    /** ONE action replaces the whole text; then the view model must hold it too (the text really travelled through the dialog). */
    private fun replaceText(text: String) {
        compose.onNode(hasSetTextAction()).performTextReplacement(text)
        waitFor("the replaced text in the field and in the view model") {
            fieldText() == text && requireNotNull(shown).feedOptionUiState.value.newUrl == text
        }
    }

    private fun stored(id: String): Feed = requireNotNull(runBlocking { rig.stored(id) }) { "the probe source $id is not in Room" }

    // ---- the dialog as the user meets it

    @Test
    fun opening_showsTheCurrentAddressInTheField() {
        val seeded = openDialogOn("open")
        waitFor("the current address in the field") { fieldText() == seeded.url }
        assertEquals("opening the box saves nothing", seeded, stored("open"))
    }

    @Test
    fun replacingTheAddressAndConfirming_savesBothColumns_andClosesTheDialog() {
        val seeded = openDialogOn("confirm", icon = ICON, isNotification = true, isFullContent = true)
        replaceText(NEW_ADDRESS)
        compose.onNode(hasText(confirmLabel)).assertHasClickAction().assertIsEnabled().performClick()
        waitFor("the dialog to close after Confirm") { !requireNotNull(shown).feedOptionUiState.value.changeUrlDialogVisible && dialogIsGone() }
        val saved = stored("confirm")
        assertEquals("the address typed in the dialog", NEW_ADDRESS, saved.url)
        assertEquals("the address the sync reads must change too", NEW_ADDRESS, saved.sourceUrl)
        assertEquals("only the two address columns may change", seeded, saved.copy(url = seeded.url, sourceUrl = seeded.sourceUrl))
    }

    @Test
    fun cancelling_closesTheDialog_andSavesNothing() {
        val seeded = openDialogOn("cancel")
        replaceText(NEW_ADDRESS)
        compose.onNode(hasText(cancelLabel)).assertHasClickAction().performClick()
        // A correct Cancel closes the box at once and never writes; a Cancel wired to the save would close it only AFTER the write.
        waitFor("the dialog to close after Cancel") { !requireNotNull(shown).feedOptionUiState.value.changeUrlDialogVisible && dialogIsGone() }
        assertEquals("Cancel must leave the saved source exactly as it was", seeded, stored("cancel"))
        assertEquals("the unsaved address is forgotten", "", requireNotNull(shown).feedOptionUiState.value.newUrl)
    }

    @Test
    fun anEmptyField_locksTheConfirmButton_andSavesNothing() {
        val seeded = openDialogOn("empty")
        replaceText(EMPTY_TEXT)
        compose.onNode(hasText(confirmLabel)).assertIsNotEnabled()
        // A click on a locked button does nothing; Room is written asynchronously, so this part only backs up assertIsNotEnabled.
        compose.onNode(hasText(confirmLabel)).performClick()
        compose.waitForIdle()
        assertTrue("the dialog is still open", requireNotNull(shown).feedOptionUiState.value.changeUrlDialogVisible)
        assertEquals("nothing may be saved from an empty field", seeded, stored("empty"))
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
        const val ICON = "https://front02u.invalid/icon.png"
        const val HANDLE_OLD = "alice-front02u.bsky.social"
        const val HANDLE_NEW = "alice-renamed-front02u.bsky.social"
        const val DID_OLD = "did:plc:front02uoldoldoldoldoldo"
        val NEW_ADDRESS: String = BlueskyFeedMapper.feedUrl(HANDLE_NEW)
    }
}
EOF
```

- [ ] **Step 3: Biên dịch nền (chờ `.exit`)**

```bash
rm -f /tmp/u-build1.exit; ( ./gradlew assembleDevDebug assembleDevDebugAndroidTest > /tmp/u-build1.log 2>&1; echo $? > /tmp/u-build1.exit ) > /dev/null 2>&1 &
for i in $(seq 1 100); do test -f /tmp/u-build1.exit && break; sleep 5; done; cat /tmp/u-build1.exit; grep -E "^e: |error:" /tmp/u-build1.log | head; tail -3 /tmp/u-build1.log
```
Expected: `0`, `BUILD SUCCESSFUL`. Nếu lỗi: đọc đúng dòng `e:`, sửa tên thật (đừng đoán). Điểm dễ lệch: `assertHasClickAction()` trả `SemanticsNodeInteraction` (chuỗi được); `compose.waitForIdle()` có trong `ComposeTestRule`; `activity.setContent` cần import `androidx.activity.compose.setContent`.

- [ ] **Step 4: Commit test (chưa chạy trên máy)**

```bash
git add app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/ChangeUrlDialogDeviceTest.kt
git commit -m "test(front-02u): check on the phone that the real change-address dialog saves, cancels and locks Confirm on an empty field

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: Chạy trên Tecno (1 lượt, rồi 20, rồi 40) với watcher

**Files:** không sửa tệp nguồn. Dùng `/tmp/kj7_watch.sh <Lớp> <N>` (cổng, sao lưu, cài, N lượt, đo xoay từng lượt, khôi phục, gỡ gói test).

**Interfaces:** Consumes: Task 1 đã biên dịch. Produces: số liệu lượt xanh/đỏ cho báo cáo.

- [ ] **Step 1: Kiểm công cụ còn tồn tại (`/tmp` có thể bị dọn)**

```bash
ls -la /tmp/kj7_gate.sh /tmp/kj7_backup.py /tmp/kj7_restore.py /tmp/kj7_watch.sh /tmp/kj7-root /tmp/kj7_mut_t.sh /tmp/kj7_t_batch.sh
```
Expected: đủ 7 mục. Thiếu: dừng và báo (không tự dựng lại cổng/sao lưu từ trí nhớ).

- [ ] **Step 2: Cổng, trong MỘT LỆNH RIÊNG**

```bash
zsh /tmp/kj7_gate.sh 2>&1 | head -2
```
Expected: dòng đầu `GATE OK: ...`. Khác: **dừng và hỏi owner**.

- [ ] **Step 3: Một lượt thử (nền, chờ ALLDONE)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f /tmp/kj7-u-r1.log; ( zsh /tmp/kj7_watch.sh ChangeUrlDialogDeviceTest 1 > /tmp/kj7-u-r1.log 2>&1 ) > /dev/null 2>&1 &
for i in $(seq 1 100); do grep -q ALLDONE /tmp/kj7-u-r1.log 2>/dev/null && break; sleep 5; done; cat /tmp/kj7-u-r1.log
```
Lưu ý: `kj7_watch.sh` ghép tên lớp với gói `com.mckimquyen.reader.ui.page.home.feed.subs.` (đúng chỗ tệp này nằm).
Expected: `run1: OK (5 tests) | ... | after-run user=0 accel=1`, `restore_exit=0`, `RESTORE OK`, `datastore: now=9ca1c202… before=9ca1c202…`, `rotation: 1 0`, `GATE OK`, `ALLDONE`.

Nếu đỏ: đọc `/tmp/kj7-watch-run-1.log` (dòng `INSTRUMENTATION_STATUS: stack=`). **Không** thêm `sleep`, không tăng timeout. Phân loại: sai hiểu API (sửa test, quay lại Task 1 Step 3) hay hành vi thật (dừng, systematic-debugging, hỏi owner). Điểm đáng nghi nhất ở lượt đầu: nút trong `AlertDialog` (cửa sổ riêng) không tìm/bấm được qua `compose.onNode`; đó là **bằng chứng mới**, không tự đổi cách bấm.

- [ ] **Step 4: Cổng riêng, rồi 20 lượt**

```bash
zsh /tmp/kj7_gate.sh 2>&1 | head -1
```
Rồi (lệnh sau, riêng):
```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f /tmp/kj7-u-m1.log; ( zsh /tmp/kj7_watch.sh ChangeUrlDialogDeviceTest 20 > /tmp/kj7-u-m1.log 2>&1 ) > /dev/null 2>&1 &
for i in $(seq 1 115); do grep -q ALLDONE /tmp/kj7-u-m1.log 2>/dev/null && break; sleep 5; done
grep -E "^run[0-9]+:" /tmp/kj7-u-m1.log | awk '{ok=($0 ~ /Failures/)?"RED":"green"; c[ok]++} END{for(k in c) print k, c[k]}'
grep -E "ROTATION CHANGED|GATE-STOP|restore_exit|datastore|rotation:|ALLDONE" /tmp/kj7-u-m1.log
```
Expected: `green 20`, không `ROTATION CHANGED`, băm DataStore trùng. Quá 115×5 giây mà chưa `ALLDONE`: chạy lại **chỉ** vòng chờ. Mỗi lượt đỏ: đọc nguyên nhân trong `/tmp/kj7-watch-run-<n>.log` **trước khi** dùng. Phân loại: lỗi gõ chữ (chữ không vào ô, hoặc vào rồi mất: `Never saw: the replaced text…` với `newUrlLength` nhỏ hơn mong đợi) ghi nhận; lỗi khác dừng và hỏi.

- [ ] **Step 5: Cổng riêng, rồi 20 lượt nữa (cộng thành 40)**

Như Step 4 nhưng log `/tmp/kj7-u-m2.log`. Cộng hai vòng:

```bash
for f in /tmp/kj7-u-m1.log /tmp/kj7-u-m2.log; do grep -E "^run[0-9]+:" $f; done | awk '{ok=($0 ~ /Failures/)?"RED":"green"; c[ok]++} END{for(k in c) print k, c[k]}'
```
Quy tắc: 0 hoặc 1 đỏ do lỗi gõ chữ trong 40: ghi và tiếp tục; **từ 2 đỏ: dừng, điều tra, hỏi owner**. Quảng cáo: bỏ qua theo lời owner (watcher vẫn ghi `adActivityOnTop`, không điều tra).

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

**Files:** tạm, không commit: `/tmp/kj7_mut_u.sh`, `/tmp/kj7_u_batch.sh`, `/tmp/u_check.py`. Đột biến tạm trong `TextFieldDlg.kt` và `ChangeUrlDialogDeviceTest.kt` (luôn khôi phục).

**Interfaces:** Consumes: `/tmp/kj7_mut_t.sh` (mẫu: `<label> <file> <old> <new>`, `base` = không đổi). Produces: bảng đột biến → tập test đỏ đo được.

- [ ] **Step 1: Kiểm các mẫu đột biến duy nhất (mỗi mẫu đúng 1 lần)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
python3 - <<'EOF'
# -*- coding: utf-8 -*-
D = open("app/src/main/java/com/mckimquyen/reader/ui/component/base/TextFieldDlg.kt", encoding="utf-8").read()
T = open("app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/ChangeUrlDialogDeviceTest.kt", encoding="utf-8").read()
pats = [
 (D, "                onValueChange = onValueChange,\n                placeholder = placeholder,"),
 (D, "                    focusManager.clearFocus()\n                    onConfirm(value)\n"),
 (D, "            TextButton(onClick = onDismissRequest) {"),
 (D, "                enabled = value.isNotBlank(),"),
 (T, "                    onDismissRequest = { viewModel.hideFeedUrlDialog() },"),
]
print([s.count(p) for s, p in pats])
EOF
```
Expected: `[1, 1, 1, 1, 1]`. Khác 1: đọc lại tệp và sửa mẫu (không chạy batch).

- [ ] **Step 2: Dựng script từ mẫu 02t**

```bash
sed -e 's/FeedAddressEditDeviceTest/ChangeUrlDialogDeviceTest/g' -e 's/OK (4 tests)/OK (5 tests)/' -e 's/ok4/ok5/g' -e 's/kj7-t-mut-/kj7-u-mut-/g' -e 's/kj7-t-mutbackup/kj7-u-mutbackup/' /tmp/kj7_mut_t.sh > /tmp/kj7_mut_u.sh
grep -n "ok5\|OK (5\|kj7-u-\|ChangeUrlDialogDeviceTest" /tmp/kj7_mut_u.sh | head -6
```
Expected: `TEST=...ChangeUrlDialogDeviceTest`, `ok5`, `OK (5 tests)`, các tệp `kj7-u-mut-*`.

Viết `/tmp/kj7_u_batch.sh` bằng heredoc, đúng hình dạng `/tmp/kj7_t_batch.sh` (đọc `cat /tmp/kj7_t_batch.sh` trước, đổi tên `kj7-t-` thành `kj7-u-`, `M=/tmp/kj7_mut_u.sh`, hai tệp sao lưu là `TextFieldDlg.kt` và tệp test):

```bash
cat > /tmp/kj7_u_batch.sh <<'EOF'
#!/bin/zsh
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou || exit 90
S=115333744A005844; APP=com.mckimquyen.reader
D=app/src/main/java/com/mckimquyen/reader/ui/component/base/TextFieldDlg.kt
T=app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/ChangeUrlDialogDeviceTest.kt
cp -p $D /tmp/kj7-u-orig-dlg.kt; cp -p $T /tmp/kj7-u-orig-test.kt
zsh /tmp/kj7_gate.sh > /tmp/kj7-u-batch-gate0.log 2>&1 || { echo "GATE-STOP at start: $(head -1 /tmp/kj7-u-batch-gate0.log)"; echo ALLDONE; exit 92; }
python3 /tmp/kj7_backup.py > /tmp/kj7-u-batch-backup.log 2>&1 || { echo BACKUP-FAILED; echo ALLDONE; exit 93; }
R=$(cat /tmp/kj7-root); BEFORE=$(shasum $R/kj7/settings.preferences_pb | cut -c1-40)
M=/tmp/kj7_mut_u.sh
zsh $M base
zsh $M u1-no-value-change $D $'                onValueChange = onValueChange,\n                placeholder = placeholder,' $'                onValueChange = {},\n                placeholder = placeholder,'
zsh $M u2-confirm-does-nothing $D $'                    focusManager.clearFocus()\n                    onConfirm(value)\n' $'                    focusManager.clearFocus()\n'
zsh $M u3-cancel-saves $D '            TextButton(onClick = onDismissRequest) {' '            TextButton(onClick = { onConfirm(value) }) {'
zsh $M u4-confirm-never-locked $D '                enabled = value.isNotBlank(),' '                enabled = true,'
zsh $M u5-test-cancel-wire-cut $T '                    onDismissRequest = { viewModel.hideFeedUrlDialog() },' '                    onDismissRequest = {},'
zsh $M base
cmp -s /tmp/kj7-u-orig-dlg.kt $D && echo "final dlg: identical" || echo "final dlg: DIFFERENT"
cmp -s /tmp/kj7-u-orig-test.kt $T && echo "final test: identical" || echo "final test: DIFFERENT"
echo "git diff main: $(git diff --stat -- app/src/main | wc -l) lines"
python3 /tmp/kj7_restore.py > /tmp/kj7-u-batch-restore.log 2>&1; echo "restore_exit=$?"; grep -E "DIFFERENT|RESTORE" /tmp/kj7-u-batch-restore.log
echo "datastore: now=$(adb -s $S exec-out run-as $APP cat files/datastore/settings.preferences_pb | shasum | cut -c1-40) before=$BEFORE"
adb -s $S uninstall $APP.test
echo "rotation: $(adb -s $S shell settings get system accelerometer_rotation | tr -d '\r') $(adb -s $S shell settings get system user_rotation | tr -d '\r')"
zsh /tmp/kj7_gate.sh | head -1
echo ALLDONE
EOF
```
Lưu ý: `$'...\n...'` của zsh cho ký tự xuống dòng thật; mẫu một dòng dùng nháy đơn thường. Script `kj7_mut_u.sh` nhận `$3`/`$4` nguyên văn nên không cần thoát thêm.

Tập đỏ mong đợi (kiểm tự động) viết thành `/tmp/u_check.py`:

```bash
cat > /tmp/u_check.py <<'EOF'
# -*- coding: utf-8 -*-
import re, sys
T1 = "opening_showsTheCurrentAddressInTheField"
T2 = "replacingTheAddressAndConfirming_savesBothColumns_andClosesTheDialog"
T3 = "cancelling_closesTheDialog_andSavesNothing"
T4 = "anEmptyField_locksTheConfirmButton_andSavesNothing"
EXPECTED = {
    "base": set(),
    "u1-no-value-change": {T2, T3, T4},
    "u2-confirm-does-nothing": {T2},
    "u3-cancel-saves": {T3},
    "u4-confirm-never-locked": {T4},
    "u5-test-cancel-wire-cut": {T3},
}
seen = {}
for line in open(sys.argv[1], encoding="utf-8"):
    m = re.match(r"^([A-Za-z0-9-]+): instrument_exit=\d+ ok5=(\d) .*failing=\[([^\]]*)\]", line)
    if m:
        label, ok5, failing = m.groups()
        got = {x for x in failing.split(",") if x}
        if label == "base":
            seen.setdefault("base", []).append((int(ok5), got))
        else:
            seen[label] = [(int(ok5), got)]
bad = 0
for label, want in EXPECTED.items():
    for ok5, got in seen.get(label, [(None, None)]):
        good = (got == want) and (ok5 == 1 if label == "base" else ok5 == 0)
        bad += 0 if good else 1
        print(("OK   " if good else "BAD  ") + label, "expected", sorted(want), "got", None if got is None else sorted(got))
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
rm -f /tmp/kj7-u-batch.log; ( zsh /tmp/kj7_u_batch.sh > /tmp/kj7-u-batch.log 2>&1 ) > /dev/null 2>&1 &
for i in $(seq 1 115); do grep -q ALLDONE /tmp/kj7-u-batch.log 2>/dev/null && break; sleep 5; done; cut -c1-300 /tmp/kj7-u-batch.log
```
Không sửa `TextFieldDlg.kt`/test cho tới `ALLDONE`. Chưa xong sau 115×5 giây: chạy lại **chỉ** vòng chờ.

- [ ] **Step 4: Đối chiếu tự động và kiểm khôi phục**

```bash
python3 /tmp/u_check.py /tmp/kj7-u-batch.log
```
Expected: sáu dòng `OK` (base hai lần) và `ALL-MATCH`. Cuối batch: `final dlg: identical`, `final test: identical`, `git diff main: 0 lines`, `restore_exit=0`, băm DataStore trùng, `rotation: 1 0`, `GATE OK`. Mutant sống sót (test không đỏ): test yếu, thêm khẳng định (quay lại Task 1) rồi chạy lại batch. Đột biến làm thêm test khác đỏ ngoài dự kiến: đọc lý do, sửa bảng mong đợi hoặc test, ghi trong báo cáo. Một lượt đỏ trong đợt đột biến có thể là lỗi gõ chữ (02m) chứ không do đột biến: kiểm dòng đỏ trong `/tmp/kj7-u-mut-<nhãn>-run.log`; nếu nó là "Never saw: the replaced text…" ở một đột biến không phải `u1`, chạy lại **chỉ** đột biến đó một lần và ghi.

Kiểm thêm: `u3` đỏ phải ở dòng khẳng định so Room (`Cancel must leave the saved source…`), không ở bước chờ đóng.

---

### Task 4: JVM, kiểm phạm vi, tài liệu, commit

**Files:**
- Modify: `doc/feature.md` (dòng 02p, 02t và dòng 02u mới), `docs/superpowers/specs/2026-11-18-front-02u-change-url-dialog-device-test-design.md` (mục "Điều chỉnh khi thực thi")
- Create: `doc/task/done/FRONT-02u_change-url-dialog-device-test_DONE.md`

- [ ] **Step 1: JVM full suite đo lại (nền, chờ `.exit`)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f /tmp/u-jvm.exit; ( ./gradlew testDevDebugUnitTest --rerun-tasks assembleDevDebug assembleDevDebugAndroidTest > /tmp/u-jvm.log 2>&1; echo $? > /tmp/u-jvm.exit ) > /dev/null 2>&1 &
for i in $(seq 1 115); do test -f /tmp/u-jvm.exit && break; sleep 5; done; cat /tmp/u-jvm.exit; grep -E "FAILED|tests completed" /tmp/u-jvm.log | head -5; tail -3 /tmp/u-jvm.log
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
Expected: `0`, `BUILD SUCCESSFUL`, 0 lỗi (số test đo lúc chạy, ghi đúng số đó). Nếu có test đỏ **không liên quan** (chỉ `androidTest` thay đổi): chạy riêng lớp đó 3 lần và chạy lại cả bộ một lần; ghi thẳng cả hai kết quả (không bỏ qua, không giấu).

- [ ] **Step 2: Kiểm phạm vi diff (không production)**

```bash
git status --short; git diff --stat -- app/src/main app/build.gradle app/schemas | tail -2
git diff --name-only HEAD~1 HEAD
```
Expected: không có tệp trong `app/src/main`, `app/build.gradle`, `app/schemas`, manifest; commit test chỉ thêm `ChangeUrlDialogDeviceTest.kt`.

- [ ] **Step 3: Viết báo cáo, cập nhật `doc/feature.md` và spec**

Báo cáo `doc/task/done/FRONT-02u_change-url-dialog-device-test_DONE.md` (tiếng Việt đơn giản) gồm: điều test chứng minh (5 test); bảng lượt (1 + 20 + 20: xanh/đỏ, nguyên nhân từng lượt đỏ); bảng mutation 5 hàng với tập test đỏ **đo được** (kèm kết quả `u_check.py`); băm DataStore trước/sau; `user_rotation` sau các vòng; JVM số **đo lại** (kèm lần đỏ chập chờn nếu có); không kiểm gì (bấm giữ, `FeedOptionDrawer`/`hideDrawer`, bàn phím thật, WEB/NOSTR, Fever/Google Reader, không bọc `AppTheme`, lint không quét `androidTest`); quảng cáo: owner dặn bỏ qua.

`doc/feature.md`: dòng 02p và 02t thêm "hộp thoại thật đã kiểm trên máy bằng FRONT-02u; chưa kiểm bấm giữ và ngăn kéo"; thêm dòng FRONT-02u sau dòng FRONT-02t (✅ ngày, đường dẫn báo cáo, số liệu thật, nói thẳng phần chưa kiểm).

Spec: nối mục "Điều chỉnh khi thực thi" (bảng "Điều chỉnh so với spec" của plan, cộng mọi điều chỉnh phát sinh khi chạy).

- [ ] **Step 4: Commit tài liệu**

```bash
git add doc docs
git commit -m "docs(front-02u): report that the change-address dialog was checked on the phone

Co-Authored-By: Claude Code <noreply@anthropic.com>"
git log --oneline | head -3; git status --short
```
Expected: chỉ `.idea/workspace.xml` còn sửa (không commit).

---

## Self-review

1. **Spec coverage:** mục tiêu và 4 hành vi (mở, xác nhận, hủy, ô trống) cộng dọn dẹp → Task 1 (5 test); dựng dây giống drawer, `collectAsStateValue`, reflection, không `showDrawer` → Task 1 Step 2 (`showDialogFor`, `load`); thay chữ một thao tác, tìm nút bằng chữ không tọa độ → `replaceText`, `confirmLabel`/`cancelLabel`; an toàn thiết bị (cổng, sao lưu, DataStore, xoay) → Global Constraints + Task 2; 1+20+20 lượt và quy tắc lượt đỏ (bỏ qua quảng cáo, lỗi gõ chữ 1/40) → Task 2; đột biến u1 đến u4 của spec và u5 (đổi nghĩa, nêu ở mục 7) → Task 3; JVM đo lại, báo cáo, `feature.md` (02p, 02t) → Task 4; nói thẳng phần không kiểm → Global Constraints và Task 4.
2. **Placeholder scan:** không "TBD"/"TODO"; mọi bước mã có mã đầy đủ (test, batch, `u_check.py`). Một chỗ có ý thức: Task 3 Step 2 batch được viết đầy đủ (không chỉ "đổi tên từ mẫu").
3. **Nhất quán tên:** `openDialogOn`, `replaceText`, `showDialogFor`, `load`, `newViewModel`, `fieldText`, `dialogIsGone`, `stored`, `confirmLabel`, `cancelLabel`, `HANDLE_OLD/NEW`, `DID_OLD`, `NEW_ADDRESS`, `ICON` khớp giữa Task 1 và bảng đột biến; tên 5 test trong bảng ở đầu khớp tên hàm trong Task 1 và trong `u_check.py` (T1 đến T4; T5 là dọn dẹp, không nằm trong tập đột biến). Mẫu đột biến u5 khớp dòng `onDismissRequest = { viewModel.hideFeedUrlDialog() },` (20 khoảng trắng đầu dòng) trong `showDialogFor`; Task 3 Step 1 kiểm lại trước khi chạy.
4. **Rủi ro còn lại (nói thẳng):** (a) `AlertDialog` là cửa sổ riêng, chưa có test nào bấm nút trong đó: nếu `performClick` không tới được, đó là bằng chứng mới; (b) `u1` làm cả T2, T3, T4 đỏ vì cùng chờ chữ mới (mong đợi tập ba test); (c) kiểm "ô trống" lớp thứ hai (Room không đổi) yếu vì ghi bất đồng bộ, lớp chính là `assertIsNotEnabled`; (d) `u3` phụ thuộc thứ tự "đóng sau khi ghi" của `changeFeedUrl()`; (e) lỗi gõ chữ 02m ~1% có thể xuất hiện ở `replaceText`; (f) không bọc `AppTheme` (khác drawer thật).
