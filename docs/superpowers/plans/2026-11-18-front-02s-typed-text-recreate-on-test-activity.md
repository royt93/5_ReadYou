# FRONT-02s — Chữ đã gõ trong ô Thêm nguồn khi Activity tạo lại, kiểm trên Activity test (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (hoặc subagent-driven-development) để thực thi plan này từng task. Các bước dùng checkbox `- [ ]`.

**Goal:** Chứng minh trên Tecno KJ7 rằng chữ người dùng đã gõ vào ô Thêm nguồn và hộp thoại còn nguyên sau `recreate()` (một và hai lần), mà không dùng `MainActivity` (tránh quảng cáo toàn màn hình).

**Architecture:** Thay **nội dung** `SubscribeDialogRecreateDeviceTest.kt` (giữ tên lớp và đường dẫn): một `ComponentActivity` của test lấy `SubscribeViewModel` qua `ViewModelProvider` (ViewModelStore thật), dựng `SubscribeDialog` bằng `setContent`, gõ chuỗi thử **một lần** qua giao diện bằng `performTextInput`, rồi `recreate()` và dựng lại hộp thoại từ cùng ViewModel. Không gõ lại, không xoay, không mạng, không ghi Room. Không đổi mã production.

**Tech Stack:** Compose UI test 1.7.8 (`createAndroidComposeRule<ComponentActivity>`), `ViewModelProvider`, `ActivityScenario.recreate()`, `am instrument`, adb, Python 3.9.

## Global Constraints

- Nhánh `dev`; không worktree, không push, không PR, không commit `.idea`.
- Không đổi mã production, manifest, chuỗi, Room (v22), DataStore, `app/build.gradle`; không dependency mới; **không thêm tệp test mới** (chỉ thay nội dung `SubscribeDialogRecreateDeviceTest.kt`). Chỉ **mutation tạm** trên `SubscribeViewModel.kt`, luôn khôi phục + `cmp`; `git diff app/src/main` phải rỗng khi kết thúc.
- Thiết bị **CHỈ Tecno KJ7 `115333744A005844`**; chỉ `adb -s 115333744A005844`; không `connected*`; chỉ `am instrument --user 0 -w -r -e class … com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner`; không động tới Pixel. Không MockK, không tọa độ, **không mạng thật**, **không `UiAutomation.setRotation`**, không ghi Room (không bấm Tìm hay Đăng ký).
- Cổng `/tmp/kj7_gate.sh` phải in `GATE OK` **trong một lệnh RIÊNG** trước mỗi lần khởi chạy. Nếu cổng dừng (app lạ ở foreground, màn khóa, xoay lệch): **dừng và hỏi owner**.
- Sao lưu và khôi phục **byte-exact 6 tệp** (`/tmp/kj7_backup.py`, `/tmp/kj7_restore.py`); băm DataStore `9ca1c202050c258b4aac9a063e27def350d737fb` phải trùng sau mỗi vòng; xoay `1 0` trước và sau mỗi lượt; không xóa DataStore.
- Gradle chạy NỀN, output ra file, chờ `.exit`/`ALLDONE`; "completed" của lệnh nền chỉ là lệnh khởi chạy. Đọc log chỉ sau khi `.exit` tồn tại.
- **Không gõ lại, không tăng timeout, không `sleep` để chữa lỗi.** 1 lượt đỏ do lỗi gõ chữ (02m, ~1%, gốc chưa rõ) trong 40 là kết quả chấp nhận được và phải được ghi; **từ 2 lượt đỏ trong 40: dừng, điều tra theo systematic-debugging, hỏi owner.**
- Mutation chạy trên máy bằng một script nền duy nhất, **không sửa tệp đột biến cho tới `ALLDONE`**, `cmp` sau mỗi đợt.
- zsh: không `echo ====`; `grep -r` phải quote mẫu và không dùng `--include`; sửa tệp bằng python; Python 3.9 cần `# -*- coding: utf-8 -*-` và `open(..., encoding="utf-8")`.
- R5: không magic number (hằng số có tên); test bắt buộc kèm; không báo "done" khi chưa dọn. R2: cập nhật `doc/feature.md`.
- Không dùng số test JVM trong plan làm bằng chứng: đo lại lúc thực thi.
- Nói thẳng phần không kiểm: nút `+` thật của `FeedsPage`, `hiltViewModel()`/`HomeEntry`, xoay thật, xuống nền, process bị giết, lint không quét `androidTest`.

## Điều chỉnh so với spec (đã đọc code thật, 2026-11-18)

1. **`SubscribeDialog` có `LaunchedEffect(visible)`** (`SubscribeDialog.kt:62`): khi `visible` thành `true` nó gọi `init()` (chỉ gán `title` và `groups`, **không** xóa `linkContent`); khi `visible` là `false` nó gọi `reset()` (xóa **mọi** state kể cả chữ). Sau `recreate()` hộp thoại dựng lại với `visible == true` (ViewModel giữ), nên `init()` chạy, chữ sống sót. Nếu một lỗi làm `visible` mất, `reset()` xóa chữ và test phải đỏ đúng ở bước kiểm chữ. Đây là điều test này bảo vệ, và đột biến (a) nhắm đúng chỗ đó.
2. **`init()` chạy lại sau `recreate()`** và gán lại `groups = rssService.get().pullGroups()` (Flow lười, không chạm DB nếu không thu). `SubscribeDialog` có thu `groups` (`collectAsState`) nên **có một lần đọc Room (chỉ đọc)** khi hộp thoại hiện; không ghi.
3. **Không cần máy chủ giả:** test không bấm Tìm. `subscribeViewModelFactory(relayUrl)` của 02e vẫn dựng `BlueskyApiClient` thật nhưng **không bao giờ được gọi**. Dùng `subscribeViewModelFactory(UNUSED_RELAY_URL)` với hằng số trong tệp test (`"ws://127.0.0.1:1"`), không thêm hàm vào factory.
4. **`createAndroidComposeRule<ComponentActivity>()` không có animation vô hạn của `FeedsPage`**, nên **không** `mainClock.autoAdvance = false`; dùng `compose.waitUntil` mặc định như 02e. Nếu lượt thử đầu cho thấy Compose "bận" (`ComposeNotIdleException`), đó là **bằng chứng mới cần điều tra**, không tự động quay lại đồng hồ thủ công.
5. **Ô nhập đọc bằng `hasSetTextAction()`:** khi `lockLinkInput == true` hoặc `readOnly`, ô mất hành động set-text (`enabled = !readOnly`, `BaseTextField.kt:64`), nên `hasSetTextAction() and hasText(PROBE_TEXT)` kiểm luôn "ô còn sửa được".
6. **Chẩn đoán lỗi gõ chữ chỉ ghi số** (không ghi chữ): chiều dài chữ lớn nhất từng thấy trong ô (`peak`), số ô nhập, ô có focus không, ViewModel đang giữ bao nhiêu ký tự (`linkContent.length`), `visible`. Dùng `SemanticsProperties.EditableText` như 02d cũ.
7. **Không nối `TypingFlakeClassifier`** (đúng spec): `peak == 0` nghĩa là chữ chưa từng vào ô (lỗi gõ, hạ tầng), `peak > 0` mà ô trống ở cuối nghĩa là vào rồi mất (lỗi thật).
8. **Test cũ trên `MainActivity` bị thay hẳn:** `git log` giữ bản cũ; `TypingFlakeProbeTest.kt` và `TypingFlakeClassifier.kt` (02m) **không đổi**.

## Cấu trúc file

| File | Việc |
|---|---|
| Modify (thay nội dung) `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeDialogRecreateDeviceTest.kt` | 2 test recreate trên Activity test, gõ một lần |
| Tạm, **không commit**: `/tmp/kj7_mut_s.sh`, `/tmp/kj7_s_batch.sh` | đột biến chạy trên máy (từ `kj7_mut_r02.sh`, đổi lớp và số test) |
| Modify `doc/feature.md`, `doc/task/done/FRONT-02d_add-source-recreate-device-test_DONE.md`, spec (mục "Điều chỉnh khi thực thi") | tài liệu |
| Create `doc/task/done/FRONT-02s_typed-text-recreate-on-test-activity_DONE.md` | báo cáo |

---

### Task 1: Viết lại test 02d trên Activity test và biên dịch (chưa chạm Tecno)

**Files:**
- Modify: `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeDialogRecreateDeviceTest.kt`

**Interfaces:**
- Consumes: `subscribeViewModelFactory(relayUrl: String): ViewModelProvider.Factory` (02e/02r), `SubscribeDialog(subscribeViewModel)`, `SubscribeViewModel.showDrawer()/subscribeUiState`, `R.string.feed_or_site_url`.
- Produces: lớp `SubscribeDialogRecreateDeviceTest` với 2 test.

- [ ] **Step 1: Đọc lại API thật (một lần cuối trước khi chép)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
J=app/src/main/java/com/mckimquyen/reader
grep -n "^fun subscribeViewModelFactory" app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModelFactory.kt
grep -n "fun showDrawer\|val subscribeUiState\|fun inputLink" $J/ui/page/home/feed/subs/SubscribeViewModel.kt
grep -n 'name="feed_or_site_url"' app/src/main/res/values/strings.xml
grep -n "readOnly = subscribeUiState.lockLinkInput\|placeholder = stringResource(R.string.feed_or_site_url)" $J/ui/page/home/feed/subs/SubscribeDialog.kt
git status --short | grep -v idea | wc -l
```
Expected: `subscribeViewModelFactory(relayUrl: String)` có; ba hàm của ViewModel có; chuỗi `Feed or Site URL`; hai dòng của `SubscribeDialog` có; cây git sạch. **Nếu khác: dừng, sửa plan, không đoán.**

- [ ] **Step 2: Thay nội dung tệp test**

```kotlin
package com.mckimquyen.reader.ui.page.home.feed.subs

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.ui.ext.currentAccountType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What survives when the test Activity that shows the Add Source dialog is recreated while the user has typed text in it.
 * The view model comes from a real ViewModelStore (ViewModelProvider on the test Activity), built from the app's real services;
 * the dialog is the real SubscribeDialog. The text is typed ONCE through the screen (performTextInput), never retyped: when it does
 * not arrive, the failure message says whether it never entered the field or entered and then vanished (numbers only, never text).
 * Nothing is searched, saved or fetched: no network, no Room write, and no screen rotation.
 *
 * Replaces the MainActivity version of this test (FRONT-02d): MainActivity shows a full-screen ad on about a quarter of the runs on the
 * Tecno KJ7 and then Compose finds nothing to query (measured 2026-11-18: 10 of 11 red runs had an AdActivity on top, none of 29 green).
 * NOT covered: the "+" button and hiltViewModel() of FeedsPage, a real rotation, the app going to the background, the process being
 * killed (the view model keeps nothing then, the typed text is lost), a real language or dark-mode change.
 */
@RunWith(AndroidJUnit4::class)
class SubscribeDialogRecreateDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var shown: SubscribeViewModel? = null
    private var peakEditableTextLength = NOT_TYPED_YET

    @Before
    fun requireTheLocalAccount() {
        check(compose.activity.applicationContext.currentAccountType == AccountType.Local.id) {
            "The Add Source dialog only works for the Local account; this phone is on another account"
        }
    }

    private val placeholder get() = compose.activity.getString(R.string.feed_or_site_url)

    private fun waitFor(what: String, condition: () -> Boolean) {
        try {
            compose.waitUntil(WAIT_MILLIS, condition)
        } catch (e: Throwable) {
            throw AssertionError("Never saw: $what | ${describe()}", e)
        }
    }

    /** Numbers and flags only: the screen can show the owner's own text. */
    private fun describe(): String = try {
        val editable = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        val focused = compose.onAllNodes(hasSetTextAction() and isFocused()).fetchSemanticsNodes()
        val onScreen = editable.map { it.config.getOrNull(SemanticsProperties.EditableText)?.text?.length }
        val state = shown?.subscribeUiState?.value
        "editableFields=${editable.size} focusedEditable=${focused.size} lengthsOnScreen=$onScreen peakOnScreen=$peakEditableTextLength " +
            "viewModelTextLength=${state?.linkContent?.length} visible=${state?.visible} locked=${state?.lockLinkInput}"
    } catch (e: Throwable) {
        "describe failed: ${e.javaClass.simpleName}"
    }

    /** The view model of [activity], taken from its ViewModelStore (the same instance across recreate). */
    private fun viewModelOf(activity: ComponentActivity): SubscribeViewModel =
        ViewModelProvider(activity, subscribeViewModelFactory(UNUSED_RELAY_URL))[SubscribeViewModel::class.java]

    /** Puts the real dialog on [activity]. Unlike MainActivity, this test Activity does not rebuild its content after a recreate. */
    private fun showDialogOn(activity: ComponentActivity, viewModel: SubscribeViewModel) {
        compose.runOnUiThread { activity.setContent { SubscribeDialog(viewModel) } }
    }

    private fun dialogTextField() = hasSetTextAction() and hasText(PROBE_TEXT)

    private fun openDialogAndTypeOnce() {
        val viewModel = viewModelOf(compose.activity)
        shown = viewModel
        showDialogOn(compose.activity, viewModel)
        compose.runOnUiThread { viewModel.showDrawer() }
        waitFor("the link field") { compose.onAllNodes(hasText(placeholder)).fetchSemanticsNodes().isNotEmpty() }
        val before = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
            .singleOrNull()?.config?.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
        check(before.isEmpty()) { "The link field was not empty before typing; refusing to append the probe" }
        peakEditableTextLength = 0
        compose.onNodeWithText(placeholder).performTextInput(PROBE_TEXT)
        waitFor("the typed text in an editable field") {
            val longest = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
                .maxOfOrNull { it.config.getOrNull(SemanticsProperties.EditableText)?.text?.length ?: 0 } ?: 0
            if (longest > peakEditableTextLength) peakEditableTextLength = longest
            compose.onAllNodes(dialogTextField()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** One recreate: a really new Activity, the SAME view model from its store, the dialog put back, the text on screen and editable. */
    private fun recreateAndShowAgain() {
        val before = compose.activity
        compose.activityRule.scenario.recreate()
        val after = compose.activity
        assertNotSame("the activity must really have been recreated", before, after)
        val again = viewModelOf(after)
        assertSame("the ViewModelStore must hand back the same view model", shown, again)
        assertEquals("the view model still holds the typed text", PROBE_TEXT, again.subscribeUiState.value.linkContent)
        showDialogOn(after, again)
        waitFor("the typed text back in an editable field after the recreate") {
            compose.onAllNodes(dialogTextField()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun recreate_keepsTheDialogOpenAndTheTypedText() {
        openDialogAndTypeOnce()
        recreateAndShowAgain()
        assertTrue("the dialog is still open", requireNotNull(shown).subscribeUiState.value.visible)
    }

    @Test
    fun recreateTwice_keepsTheDialogOpenAndTheTypedText() {
        openDialogAndTypeOnce()
        recreateAndShowAgain()
        recreateAndShowAgain()
        assertTrue("the dialog is still open", requireNotNull(shown).subscribeUiState.value.visible)
        assertEquals("the field must still be editable, exactly one", 1, compose.onAllNodes(dialogTextField()).fetchSemanticsNodes().size)
    }

    private companion object {
        const val PROBE_TEXT = "abc-rotation-probe"
        const val WAIT_MILLIS = 15_000L
        const val NOT_TYPED_YET = -1
        const val UNUSED_RELAY_URL = "ws://127.0.0.1:1"
    }
}
```
`recreateAndShowAgain` khẳng định cả ViewModel còn giữ chữ (`linkContent == PROBE_TEXT`) **và** chữ hiện trong ô sau khi dựng lại: hai chỗ khác nhau, nên một đột biến phá một trong hai (ví dụ `init()` xóa `linkContent`) bị bắt ở dòng `assertEquals` trước khi tới bước chờ màn hình.

- [ ] **Step 3: Biên dịch (nền, output ra file)**

```bash
rm -f /tmp/f32-build.exit
( ./gradlew assembleDevDebug assembleDevDebugAndroidTest > /tmp/f32-build.txt 2>&1; echo $? > /tmp/f32-build.exit ) > /dev/null 2>&1 &
```
Chờ `/tmp/f32-build.exit`; `cat` mã thoát; `grep -n "^e: " /tmp/f32-build.txt | head`. Expected `0`. Nếu cảnh báo "unused import": gỡ import thừa, không thêm `@Suppress`. Không thêm `sleep`, không tăng `WAIT_MILLIS` để chữa lỗi.

- [ ] **Step 4: Không commit ở đây** (commit cùng Task 4, sau khi có kết quả máy và mutation).

---

### Task 2: Chạy trên Tecno: một lượt thử, rồi 40 lượt (không gõ lại)

**Files:**
- Dùng sẵn, tạm, **không commit**: `/tmp/kj7_gate.sh`, `/tmp/kj7_backup.py`, `/tmp/kj7_restore.py`, `/tmp/kj7_run.sh` (nhận tên lớp và N lượt; đo `user_rotation` và `accelerometer_rotation` sau mỗi lượt).

**Interfaces:**
- Consumes: APK app và androidTest từ Task 1; lớp `SubscribeDialogRecreateDeviceTest` (2 test).
- Produces: `/tmp/kj7-run-SubscribeDialogRecreateDeviceTest-<N>.log`, `/tmp/kj7-s-*.log`, xác nhận khôi phục byte-exact.

- [ ] **Step 1: Kiểm công cụ còn tồn tại (bài học: `/tmp` từng bị dọn)**

```bash
ls /tmp/kj7_gate.sh /tmp/kj7_backup.py /tmp/kj7_restore.py /tmp/kj7_run.sh /tmp/kj7_mut_r02.sh /tmp/kj7-root 2>&1
grep -n "TestClassSimpleName\|-e class" /tmp/kj7_run.sh | cut -c1-140
```
Nếu thiếu tệp nào: **dừng**, dựng lại (sao lưu 8 tệp của app bằng `run-as … cat`; khôi phục bằng `run-as … sh -c 'cat > …'` với stdin là tệp). Không chạy khi thiếu.

- [ ] **Step 2: Cổng chỉ đọc, trong MỘT LỆNH RIÊNG**

```bash
zsh /tmp/kj7_gate.sh > /tmp/kj7-s-gate0.log 2>&1; echo gate_rc=$?; head -1 /tmp/kj7-s-gate0.log
```
Chỉ khi in `GATE OK` mới sang bước sau, **bằng một lệnh khác**. Nếu `STOP`: dừng, đọc lý do và hỏi owner; không khởi chạy gì.

- [ ] **Step 3: Một lượt thử**

```bash
rm -f /tmp/kj7-s-one.exit /tmp/kj7-s-one.log
( zsh /tmp/kj7_run.sh SubscribeDialogRecreateDeviceTest 1 > /tmp/kj7-s-one.log 2>&1; echo $? > /tmp/kj7-s-one.exit ) > /dev/null 2>&1 &
```
Chờ `.exit` và dòng `ALLDONE`; đọc `/tmp/kj7-run-SubscribeDialogRecreateDeviceTest-1.log` đầy đủ. Expected: `OK (2 tests)`, `after-run user=0 accel=1`, `RESTORE OK`, băm DataStore trùng, đã gỡ test package. Kiểm cả **hai tên test** có trong log (`INSTRUMENTATION_STATUS: test=`) để chắc test chạy thật.
**Nếu đỏ:** dừng và điều tra theo systematic-debugging (đọc stack và các số `editableFields/focusedEditable/lengthsOnScreen/peakOnScreen/viewModelTextLength/visible/locked`). Phân biệt: (i) `peakOnScreen=0` và `viewModelTextLength=0` ⇒ chữ chưa từng vào (lỗi gõ 02m, hạ tầng); (ii) `peakOnScreen>0` hoặc `viewModelTextLength>0` rồi mất ⇒ lỗi thật; (iii) `Never saw: the link field` ⇒ hộp thoại không hiện (`visible`? `showDialogOn`?); (iv) `ComposeNotIdleException` ⇒ Compose bận (điều chỉnh 4: bằng chứng mới, điều tra); (v) `check(... Local ...)` ⇒ máy không ở tài khoản Local, báo owner. **Không** gõ lại, **không** đổi `WAIT_MILLIS`, **không** `sleep`.

- [ ] **Step 4: 40 lượt (hai vòng 20, mỗi vòng cổng riêng, sao lưu và khôi phục riêng)**

```bash
zsh /tmp/kj7_gate.sh > /tmp/kj7-s-gate1.log 2>&1; echo gate_rc=$?; head -1 /tmp/kj7-s-gate1.log
```
rồi (lệnh riêng) khởi chạy:
```bash
rm -f /tmp/kj7-s-20a.exit /tmp/kj7-s-20a.log
( zsh /tmp/kj7_run.sh SubscribeDialogRecreateDeviceTest 20 > /tmp/kj7-s-20a.log 2>&1; echo $? > /tmp/kj7-s-20a.exit ) > /dev/null 2>&1 &
```
Lặp lại cho vòng hai (`/tmp/kj7-s-20b.*`). Sau mỗi vòng đọc: số lượt `OK (2 tests)`, lượt đỏ và nguyên nhân từng lượt (đọc `peak` và `viewModelTextLength`), `RESTORE OK`, băm DataStore `9ca1c202050c258b4aac9a063e27def350d737fb` trùng, `rotation: 1 0`, `GATE OK`.
**Tiêu chí:** 0 hoặc 1 lượt đỏ trong 40, và nếu có thì phải là lỗi gõ (`peak=0`, chữ chưa từng vào); 0 lượt xoay lệch; 0 lượt đỏ có `peak>0` (chữ vào rồi mất). **Từ 2 lượt đỏ trong 40, hoặc bất kỳ lượt "vào rồi mất": dừng, điều tra theo systematic-debugging, hỏi owner**; một lượt đỏ không được dùng làm bằng chứng cho đến khi đọc nguyên nhân.
Nếu một lệnh chờ của công cụ không đủ cho vòng 20 lượt, chờ tiếp bằng lệnh `grep -q ALLDONE` riêng; không khởi chạy lại.

- [ ] **Step 5: Không commit ở đây.**

---

### Task 3: Mutation chạy trên Tecno (chứng minh test bắt được lỗi)

**Files:**
- Create tạm, **không commit**: `/tmp/kj7_mut_s.sh` (từ `kj7_mut_r02.sh`), `/tmp/kj7_s_batch.sh`.
- Đột biến tạm, luôn khôi phục: `app/src/main/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModel.kt`; một đột biến **trong test**: `SubscribeDialogRecreateDeviceTest.kt`.

**Interfaces:**
- Consumes: 2 test xanh ở Task 2; cổng và sao lưu của Task 2.
- Produces: `/tmp/kj7-s-mut-<label>-*.log`, `/tmp/kj7-s-batch.log`, `ALLDONE`, nguồn `identical`.

- [ ] **Step 1: Kiểm các đoạn mã đột biến còn độc nhất (đọc lại, không tin plan)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
python3 - <<'PYEOF'
J='app/src/main/java/com/mckimquyen/reader/ui/page/home/feed/subs/'
vm=open(J+'SubscribeViewModel.kt',encoding='utf-8').read()
t=open('app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeDialogRecreateDeviceTest.kt',encoding='utf-8').read()
pats=[(vm,'title = androidStringsHelper.getString(R.string.subscribe),\n                groups = rssService.get().pullGroups(),'),
 (vm,'                linkContent = content,\n                errorMessage = "",'),
 (vm,'    fun showDrawer() {\n        _subscribeUiState.update { it.copy(visible = true) }'),
 (t,'val viewModel = viewModelOf(compose.activity)\n        shown = viewModel')]
print([src.count(p) for src,p in pats])
PYEOF
```
Expected `[1, 1, 1, 1]`. Nếu có số khác: đọc lại mã và đổi mẫu cho đúng; ghi vào "Điều chỉnh khi thực thi".

- [ ] **Step 2: Tạo `/tmp/kj7_mut_s.sh` từ `kj7_mut_r02.sh` (không sửa bản gốc)**

```bash
python3 - <<'PYEOF'
# -*- coding: utf-8 -*-
s = open('/tmp/kj7_mut_r02.sh', encoding='utf-8').read()
assert s.count('BlueskyGuardsDeviceTest') >= 1
s = s.replace('BlueskyGuardsDeviceTest.kt', 'SubscribeDialogRecreateDeviceTest.kt').replace('BlueskyGuardsDeviceTest', 'SubscribeDialogRecreateDeviceTest')
s = s.replace('OK (12 tests)', 'OK (2 tests)').replace('ok12', 'ok2')
s = s.replace('kj7-r02-mut-', 'kj7-s-mut-').replace('kj7-r02-mutbackup', 'kj7-s-mutbackup')
open('/tmp/kj7_mut_s.sh', 'w', encoding='utf-8').write(s)
PYEOF
chmod +x /tmp/kj7_mut_s.sh
grep -n "SubscribeDialogRecreateDeviceTest\|OK (2\|BlueskyGuards\|OK (12\|ok12\|ok2" /tmp/kj7_mut_s.sh | cut -c1-150
```
Expected: `TEST=…SubscribeDialogRecreateDeviceTest`, `OK (2 tests)`, **không còn** `BlueskyGuards` và `OK (12`. Đọc lại toàn bộ script (khôi phục trong `trap` và `cmp`).

- [ ] **Step 3: Batch (một script nền duy nhất)**

| Nhãn | Tệp | Đột biến | Test phải đỏ |
|---|---|---|---|
| `s1-init-clears-text` | `SubscribeViewModel.kt` | `init()` thêm `linkContent = "",` | `recreate_…` và `recreateTwice_…` (ở `assertEquals("the view model still holds the typed text"…)`) |
| `s2-input-ignored` | `SubscribeViewModel.kt` | `inputLink` đặt `linkContent = ""` | cả hai (ở bước gõ, chữ không vào ô) |
| `s3-never-opens` | `SubscribeViewModel.kt` | `showDrawer` đặt `visible = false` | cả hai (ở bước chờ ô nhập) |
| `s4-vm-outside-store` | `SubscribeDialogRecreateDeviceTest.kt` | dựng ViewModel ngoài store: `val viewModel = subscribeViewModelFactory(UNUSED_RELAY_URL).create(SubscribeViewModel::class.java)` | cả hai (ở `assertSame("the ViewModelStore must hand back…")`) |

```bash
cat > /tmp/kj7_s_batch.sh <<'SH'
#!/bin/zsh
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou || exit 90
S=115333744A005844; APP=com.mckimquyen.reader
VM=app/src/main/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModel.kt
T=app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeDialogRecreateDeviceTest.kt
cp -p $VM /tmp/kj7-s-orig-vm.kt; cp -p $T /tmp/kj7-s-orig-test.kt
zsh /tmp/kj7_gate.sh > /tmp/kj7-s-batch-gate0.log 2>&1 || { echo "GATE-STOP at start: $(head -1 /tmp/kj7-s-batch-gate0.log)"; echo ALLDONE; exit 92; }
python3 /tmp/kj7_backup.py > /tmp/kj7-s-batch-backup.log 2>&1 || { echo BACKUP-FAILED; echo ALLDONE; exit 93; }
R=$(cat /tmp/kj7-root); BEFORE=$(shasum $R/kj7/settings.preferences_pb | cut -c1-40)
M=/tmp/kj7_mut_s.sh
zsh $M base
zsh $M s1-init-clears-text $VM 'title = androidStringsHelper.getString(R.string.subscribe),
                groups = rssService.get().pullGroups(),' 'title = androidStringsHelper.getString(R.string.subscribe),
                groups = rssService.get().pullGroups(),
                linkContent = "",'
zsh $M s2-input-ignored $VM '                linkContent = content,
                errorMessage = "",' '                linkContent = "",
                errorMessage = "",'
zsh $M s3-never-opens $VM '    fun showDrawer() {
        _subscribeUiState.update { it.copy(visible = true) }' '    fun showDrawer() {
        _subscribeUiState.update { it.copy(visible = false) }'
zsh $M s4-vm-outside-store $T 'val viewModel = viewModelOf(compose.activity)
        shown = viewModel' 'val viewModel = subscribeViewModelFactory(UNUSED_RELAY_URL).create(SubscribeViewModel::class.java)
        shown = viewModel'
zsh $M base
cmp -s /tmp/kj7-s-orig-vm.kt $VM && echo "final vm: identical" || echo "final vm: DIFFERENT"
cmp -s /tmp/kj7-s-orig-test.kt $T && echo "final test: identical" || echo "final test: DIFFERENT"
echo "git diff main: $(git diff --stat -- app/src/main | wc -l) lines"
python3 /tmp/kj7_restore.py > /tmp/kj7-s-batch-restore.log 2>&1; echo "restore_exit=$?"; grep -E "DIFFERENT|RESTORE" /tmp/kj7-s-batch-restore.log
echo "datastore: now=$(adb -s $S exec-out run-as $APP cat files/datastore/settings.preferences_pb | shasum | cut -c1-40) before=$BEFORE"
adb -s $S uninstall $APP.test
echo "rotation: $(adb -s $S shell settings get system accelerometer_rotation | tr -d '\r') $(adb -s $S shell settings get system user_rotation | tr -d '\r')"
zsh /tmp/kj7_gate.sh | head -1
echo ALLDONE
SH
chmod +x /tmp/kj7_s_batch.sh
grep -c 'M s[1-4]\|M base' /tmp/kj7_s_batch.sh
```
Expected: `6` (base, bốn đột biến, base). Đọc lại toàn bộ batch trước khi chạy.

- [ ] **Step 4: Cổng RIÊNG, rồi khởi chạy batch (nền); không sửa hai tệp đột biến trong lúc chờ**

```bash
zsh /tmp/kj7_gate.sh > /tmp/kj7-s-gate2.log 2>&1; echo gate_rc=$?; head -1 /tmp/kj7-s-gate2.log
```
rồi (lệnh riêng):
```bash
rm -f /tmp/kj7-s-batch.exit /tmp/kj7-s-batch.log
( zsh /tmp/kj7_s_batch.sh > /tmp/kj7-s-batch.log 2>&1; echo $? > /tmp/kj7-s-batch.exit ) > /dev/null 2>&1 &
```
Chờ **cả** `.exit` và `ALLDONE`.

- [ ] **Step 5: Đọc kết quả, so sánh TỰ ĐỘNG tập test đỏ với tập dự định**

```bash
python3 - <<'PYEOF'
import re
both = {'recreate_keepsTheDialogOpenAndTheTypedText', 'recreateTwice_keepsTheDialogOpenAndTheTypedText'}
expected = {'s1-init-clears-text': both, 's2-input-ignored': both, 's3-never-opens': both, 's4-vm-outside-store': both}
log = open('/tmp/kj7-s-batch.log', encoding='utf-8').read()
ok = True
for lab, exp in expected.items():
    m = re.search(re.escape(lab) + r': instrument_exit=\d+ ok2=\d+ \[[^\]]*\] failing=\[([^\]]*)\]', log)
    got = {x for x in m.group(1).split(',') if x}
    ok &= (got == exp)
    print(('EXACT ' if got == exp else 'DIFF  ') + lab, '' if got == exp else f'expected={sorted(exp)} got={sorted(got)}')
print('ALL EXACT' if ok else 'SOME DIFFER')
PYEOF
```
Mong đợi: `base` xanh `OK (2 tests)` hai lần; mỗi đột biến làm **đúng hai test** đỏ; `final vm/test: identical`; `git diff main: 0 lines`; `RESTORE OK`; băm DataStore trùng; `rotation: 1 0`; `GATE OK`.
- **Đột biến sống sót** ⇒ test yếu: thêm khẳng định, chạy lại `base` 20 lượt và đột biến đó.
- **Đỏ đúng test nhưng sai chỗ** (đọc `at=[...]` và thông báo): `s1` phải đỏ ở `assertEquals("the view model still holds the typed text"`; `s4` ở `assertSame`. Nếu `s1` đỏ ở bước chờ màn hình thì khẳng định ViewModel không chạy trước: ghi lại.
- **Đỏ vì hạ tầng** không tính là bắt được; điều tra.
- Lượt `base` cuối đỏ: chập chờn, quay lại điều tra, **không commit**.

- [ ] **Step 6: Trả máy**

Chỉ đọc: không còn script `kj7_*` chạy, launcher trên cùng, `accelerometer_rotation=1`, `user_rotation=0`, đã gỡ `com.mckimquyen.reader.test`, `git status` sạch ngoài tệp 02s.

---

### Task 4: JVM, tài liệu, commit

**Files:**
- Modify: `doc/feature.md`, `doc/task/done/FRONT-02d_add-source-recreate-device-test_DONE.md`, `docs/superpowers/specs/2026-11-18-front-02s-typed-text-recreate-on-test-activity-design.md` (mục "Điều chỉnh khi thực thi").
- Create: `doc/task/done/FRONT-02s_typed-text-recreate-on-test-activity_DONE.md`.

- [ ] **Step 1: Full suite JVM và assemble (nền, output ra file)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f /tmp/f32-full.exit /tmp/f32-assemble.exit
( ./gradlew testDevDebugUnitTest --rerun-tasks > /tmp/f32-full.log 2>&1; echo $? > /tmp/f32-full.exit; ./gradlew assembleDevDebug assembleDevDebugAndroidTest > /tmp/f32-assemble.log 2>&1; echo $? > /tmp/f32-assemble.exit ) > /dev/null 2>&1 &
```
Chờ cả hai `.exit`; đếm từ XML (`app/build/test-results/testDevDebugUnitTest/*.xml`: tests, failures, errors, skipped). Expected: 0 lỗi; **ghi số đo thật**. Nếu đỏ ở lớp Compose chập chờn đã biết (02q), chạy lại và ghi cả hai kết quả.

- [ ] **Step 2: Kiểm phạm vi diff**

```bash
git status --short | grep -v idea
git diff --check
git diff --stat -- app/src/main app/schemas app/build.gradle app/src/main/AndroidManifest.xml
git diff --stat -- app/src/androidTest
```
Expected: `app/src/main`, `app/schemas`, `app/build.gradle`, manifest **không đổi**; `app/src/androidTest` chỉ có `SubscribeDialogRecreateDeviceTest.kt`; không `.idea` trong danh sách commit.

- [ ] **Step 3: Tài liệu**

Báo cáo 02s tiếng Việt với số liệu thật: số lượt, lượt đỏ và nguyên nhân (`peak`, `viewModelTextLength`), bảng đột biến (nhãn, tệp, tập test đỏ **thật**, khớp dự định hay không), băm DataStore trước và sau, xoay sau mỗi lượt, JVM đo lại, và những gì **mất** so với 02d cũ (nút `+`, `hiltViewModel()`, xoay thật, `assertSame` về `configChanges`). **Cập nhật báo cáo 02d:** thêm mục đầu "Đã được thay bằng FRONT-02s (2026-11-18)" nêu lý do (quảng cáo 10/11 lượt đỏ) và rằng các số liệu cũ của 02d là của test `MainActivity` đã bị thay, **không** được làm mới. `doc/feature.md`: sửa dòng 02d ghi "đã thay bằng 02s" và thêm mục FRONT-02s trước `## 🟡 In progress`; sửa dòng FRONT-02f nếu còn nói "FRONT-02d chạy ổn trên Tecno" (đã sai, đo hôm nay 29 và 30 trên 40). Spec thêm mục "Điều chỉnh khi thực thi" (các điều chỉnh 1 đến 8 của plan và mọi chỗ thực tế khác kế hoạch).

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeDialogRecreateDeviceTest.kt \
  doc/feature.md doc/task/done/FRONT-02d_add-source-recreate-device-test_DONE.md \
  doc/task/done/FRONT-02s_typed-text-recreate-on-test-activity_DONE.md \
  docs/superpowers/specs/2026-11-18-front-02s-typed-text-recreate-on-test-activity-design.md
git commit -m "test(front-02s): keep the typed text across recreate on a test Activity, away from the ad-prone MainActivity

Co-Authored-By: Claude Code <noreply@anthropic.com>"
git status --short | grep -v idea
```
Expected: một commit trên `dev`; cây sạch ngoài `.idea`; không push.

---

## Self-review

**Phủ spec.** (1) Activity test thay `MainActivity`, bỏ nút `+`, bỏ `autoAdvance`, bỏ xoay thật, bỏ gõ lại, thay nội dung tệp 02d: Task 1. (2) Gõ **một lần** qua giao diện thật, chẩn đoán số, `check` tài khoản Local: Task 1. (3) 40 lượt Tecno, không gõ lại, tiêu chí 0 đến 1 lượt đỏ và phân loại nguyên nhân: Task 2. (4) Bốn đột biến, so sánh tự động: Task 3. (5) JVM, assemble, báo cáo 02s, cập nhật 02d và `feature.md`, commit: Task 4. (6) Quy tắc an toàn thiết bị: Task 2 và 3. **Lệch so với spec, đã ghi ở "Điều chỉnh":** `LaunchedEffect(visible)` gọi `init()` rồi `reset()` (điều chỉnh 1) và `SubscribeDialog` có đọc Room khi thu `groups` (điều chỉnh 2, chỉ đọc, spec ghi "không ghi Room" vẫn đúng); không thêm hàm vào factory (điều chỉnh 3); thêm khẳng định ViewModel còn giữ chữ để `s1` đỏ ở chỗ rõ.

**Quét placeholder.** Không còn "TBD"/"TODO"/"tương tự Task N". Mọi bước có mã hoặc lệnh đầy đủ; batch của Task 3 viết đủ bốn lệnh `zsh $M`. Một điều kiện thật: nếu lượt thử đầu cho thấy Compose bận, điều chỉnh 4 chỉ đường điều tra, không chỉ đường sửa.

**Nhất quán kiểu và tên.** `SubscribeDialogRecreateDeviceTest` (2 test: `recreate_keepsTheDialogOpenAndTheTypedText`, `recreateTwice_keepsTheDialogOpenAndTheTypedText`) thống nhất ở mọi script (`OK (2 tests)`, `ok2`, tập đỏ dự định). Hàm trong test: `openDialogAndTypeOnce`, `recreateAndShowAgain`, `viewModelOf`, `showDialogOn`, `dialogTextField`, `describe` (giữ tên khi dùng). Đột biến `s4` thay đúng dòng `val viewModel = viewModelOf(compose.activity)\n        shown = viewModel` trong `openDialogAndTypeOnce`.

**Rủi ro còn lại, nói thẳng.**
1. **Lỗi gõ chữ ~1% (02m, gốc chưa rõ)** sẽ xuất hiện, đây chính là phần mà test này vốn chứng minh; chấp nhận có chủ ý (owner chọn không gõ lại). Với 40 lượt, **0 đến 2 lượt đỏ là bình thường**; quy tắc "từ 2 lượt đỏ thì dừng" có thể kích hoạt vì ngẫu nhiên (đã tính, phân phối nhị thức: nếu tỉ lệ thật là 1% thì xác suất thấy ≥ 2 đỏ trong 40 lượt là **6,1%**, với 1,5% là **12,1%**, với 2,5% là **26,4%**; xác suất thấy 0 đỏ là 66,9%, 54,6% và 36,3% tương ứng); khi đó phải đọc nguyên nhân (`peak=0`) rồi hỏi owner, không tự kết luận. **Đọc kết quả đúng cách:** 0 lượt đỏ trong 40 **không** chứng minh tỉ lệ lỗi gõ bằng 0 (cận trên 95% khoảng 7,2%); 1 lượt đỏ vì lỗi gõ là bình thường; ≥ 2 lượt đỏ vì lỗi gõ **vẫn có thể là ngẫu nhiên** (6 đến 26% tùy tỉ lệ thật), nên ≥ 2 lượt đỏ là lý do **đọc và hỏi**, không phải bằng chứng test hỏng.
2. **Mất bằng chứng chỉ `MainActivity` mới có** (nút `+` thật, `hiltViewModel()`, xoay thật, `assertSame` về `configChanges`); báo cáo phải ghi rõ.
3. **Thay một test đã commit:** lịch sử git giữ bản cũ; báo cáo 02d và `feature.md` phải khớp.
4. **`ComponentActivity` không tự dựng lại nội dung sau `recreate()`:** quên `setContent` lại cho lỗi giả "không thấy ô"; `recreateAndShowAgain` đã làm.
5. **Chưa kiểm ô nhập trong `AlertDialog` (cửa sổ riêng) trên Activity test** (02e kiểm trên preview, không phải ô nhập); lượt thử đầu là phép kiểm. Nếu `compose.onAllNodes(hasSetTextAction())` không thấy ô trong cửa sổ hộp thoại, đó là phát hiện mới cần điều tra, không sửa mù.
6. **Lint không quét `androidTest`**; chỉ biên dịch được kiểm. Một máy (Tecno KJ7, Android 14).
