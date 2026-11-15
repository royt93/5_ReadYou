# FRONT-02d — Test thiết bị cho ô Thêm nguồn khi Activity tạo lại hoặc xoay (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (hoặc subagent-driven-development) để thực thi plan này từng task. Các bước dùng checkbox `- [ ]`.

**Goal:** Chứng minh trên Pixel 7 Pro rằng hộp thoại **Thêm nguồn** vẫn mở và ô nhập giữ nguyên chữ sau `recreate()`, sau xoay thật, và sau `recreate()` hai lần liên tiếp.

**Architecture:** Một tệp androidTest mới mở `MainActivity` thật bằng `createAndroidComposeRule<MainActivity>()`, bấm nút `+` theo `contentDescription` bằng Compose semantics (trong tiến trình test, không tọa độ), gõ chuỗi thử rồi gây gián đoạn. Không đổi mã production. Vì đây là test "đặc tả hành vi đã có", khả năng bắt lỗi được chứng minh bằng **đột biến chạy trên máy** (đổi tạm mã production, build, cài, chạy, khôi phục).

**Tech Stack:** Compose UI test 1.7.8 (đã có), `ActivityScenario.recreate()`, `UiAutomation.setRotation`, `am instrument`, adb, Python 3.9 (script tạm).

## Global Constraints

- Nhánh `dev`; không worktree, không push, không PR, không commit `.idea`.
- Không dependency mới; không đổi manifest, chuỗi, Room (giữ v22), DataStore; không đổi mã production (chỉ đột biến tạm, luôn khôi phục + `cmp`); không Gemini/ad/IAP/tiền.
- Thiết bị **CHỈ Pixel 7 Pro `2B051FDH3006MU`**. Chỉ `adb -s 2B051FDH3006MU`. Không `connected*`; chỉ `am instrument -w -e class …` với runner `com.mckimquyen.reader.test`. Chỉ force-stop `com.mckimquyen.reader`.
- **Không bấm hay vuốt theo tọa độ** (memory `never-tap-by-stale-coordinates`). Việc bấm nút `+` là Compose semantics trong tiến trình test. Quảng cáo toàn màn hình chắn UI: chỉ phím Back.
- Cổng chỉ đọc trước mỗi lần chạy trên máy (xem Task 2); dừng và hỏi owner nếu `loyalty.skyjoy.vn.dev` ở foreground, màn khóa, hộp sinh trắc, hay có instrumentation lạ.
- Sao lưu và khôi phục **byte-exact** (`/tmp/pixel-backup.py`, `/tmp/pixel-restore.py`); không xóa DataStore; sau cùng băm DataStore phải trùng bản sao lưu (sha1 lúc sao lưu gần nhất là `ed9f68879fc775bb5fb1b5f24206a6c1a924bcee`, đọc lại bản mới ở Task 2).
- Gradle chạy NỀN, output ra file, chờ tệp `.exit`/`ALLDONE`; thông báo "completed" của lệnh nền chỉ là lệnh khởi chạy. Đọc XML chỉ sau khi `.exit` tồn tại.
- Mutation: script nền duy nhất, **không sửa các tệp đột biến cho tới `ALLDONE`**, `cmp` sau mỗi đợt.
- zsh: không `echo ====`; `grep -r` phải quote mẫu và không dùng `--include`; sửa tệp bằng python; Python 3.9 cần `# -*- coding: utf-8 -*-` và `open(..., encoding="utf-8")`.
- R5: không magic number (hằng số có tên); test bắt buộc kèm; không báo "done" khi chưa dọn. R2: cập nhật `doc/feature.md`. R4: quảng cáo → Back.
- Nói thẳng phần không kiểm: xem trước Nostr sau xoay, xuống nền, process bị giết, đổi ngôn ngữ/chế độ tối thật, hai điện thoại.

## Điều chỉnh so với spec (phát hiện khi đọc code thật, 2026-11-15)

1. **Tên hằng số xoay:** spec ghi `ROTATION_90`; API thật là `UiAutomation.ROTATION_FREEZE_0/90/180/270`, `ROTATION_FREEZE_CURRENT`, `ROTATION_UNFREEZE`.
2. **Xoay làm đổi cài đặt hệ thống.** `UiAutomation.setRotation` đóng băng xoay và có thể đổi `accelerometer_rotation` của máy. Nếu máy đang tắt tự xoay mà dọn bằng `ROTATION_UNFREEZE` thì để lại **tự xoay bật** (đổi cài đặt của owner). Vì vậy test đọc `Settings.System.ACCELEROMETER_ROTATION` trước, và `@After` trả đúng trạng thái đó (`ROTATION_UNFREEZE` nếu ban đầu bật, `ROTATION_FREEZE_0` nếu ban đầu tắt). Cổng đọc và so `settings get system accelerometer_rotation` / `user_rotation` trước và sau.
3. **Placeholder biến mất khi có chữ**, nên sau khi gõ không thể tìm ô bằng placeholder. Tìm ô bằng `hasSetTextAction() and hasText(PROBE_TEXT)`. Vì `readOnly` (tức `lockLinkInput = true`) làm mất hành động `setText`, việc tìm thấy ô bằng `hasSetTextAction()` cũng chứng minh ô **không bị khóa** (thay cho kiểm "nút Tìm vẫn bật", vốn mơ hồ vì chữ "Search" xuất hiện nhiều nơi).
4. **Trùng `contentDescription` "Subscribe":** cả nút `+` lẫn biểu tượng trong hộp thoại (`SubscribeDialog.kt:84`) đều mang nhãn này. Nút `+` được chọn bằng `hasContentDescription(...) and hasClickAction()` và phải khớp **đúng 1 node**; biểu tượng trong hộp thoại không bấm được nên bị loại.
5. **Chuỗi lấy từ `compose.activity.getString(...)`** (không phải `targetContext`): app áp ngôn ngữ riêng trong `attachBaseContext`, nên chỉ Activity mới có đúng ngôn ngữ app.
6. **Thêm kiểm "thực sự đã tạo lại":** test (a) và (c) khẳng định instance Activity **khác** trước/sau (`assertNotSame`); test (b) khẳng định **cùng** instance (`assertSame`). Không có hai khẳng định này thì "recreate" có thể không xảy ra mà test vẫn xanh.
8. **Đột biến thay `hideDrawer`:** spec ghi "`hideDrawer()` gọi trong `init()`"; thay bằng `showDrawer` đặt `visible = false` (hộp thoại không bao giờ mở), vì `hideDrawer` là đường đóng thật và đột biến trực tiếp trên nó trùng với hành vi hợp lệ. `m01` chỉ làm `init()` xóa `linkContent` (giả thuyết thật của test (a)).
7. **`SubscribeDialog` có `LaunchedEffect(visible)`** (`SubscribeDialog.kt:62`): sau `recreate()` composition mới chạy `init()` (nếu `visible`) hoặc `reset()` (nếu không). `init()` hiện không xóa `linkContent`, nên dự đoán là chữ sống sót; đây chính là giả thuyết mà test (a) kiểm.

## Cấu trúc file

| File | Việc |
|---|---|
| Create `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeDialogRecreateDeviceTest.kt` | 3 test thiết bị |
| Tạm, **không commit**: `/tmp/f28_gate.sh`, `/tmp/f28_dev_mut.sh`, `/tmp/f28_mut_batch.sh` | cổng thiết bị, đột biến chạy trên máy |
| Modify `doc/feature.md`, `docs/superpowers/specs/2026-11-15-front-02d-…-design.md` (mục "Điều chỉnh khi thực thi") | tài liệu |
| Create `doc/task/done/FRONT-02d_add-source-recreate-device-test_DONE.md` | báo cáo |

---

### Task 1: Viết tệp test thiết bị và biên dịch

**Files:**
- Create: `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeDialogRecreateDeviceTest.kt`

**Interfaces:**
- Consumes: `MainActivity` (`infrastructure.android`), `R.string.subscribe`, `R.string.feed_or_site_url`.
- Produces: lớp `SubscribeDialogRecreateDeviceTest` với 3 test: `recreate_keepsTheDialogOpenAndTheTypedText`, `realRotation_keepsTheDialogOpenAndTheTypedText_withoutRecreatingTheActivity`, `recreateTwice_keepsTheDialogOpenAndTheTypedText`.

- [ ] **Step 1: Đọc lại API thật trước khi chép** (bài học: plan trước sai chi tiết API)

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
grep -n "string name=\"subscribe\"\|string name=\"feed_or_site_url\"" app/src/main/res/values/strings.xml
grep -rn "FeedbackIconButton(" app/src/main/java/com/mckimquyen/reader/ui/page/home/feed/FeedsPage.kt | head -3
```
Expected: hai chuỗi `Subscribe` và `Feed or Site URL`; nút `+` dùng `FeedbackIconButton`. Nếu khác: dừng, sửa plan.

- [ ] **Step 2: Tạo tệp test**

```kotlin
package com.mckimquyen.reader.ui.page.home.feed.subs

import android.app.UiAutomation
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.reader.R
import com.mckimquyen.reader.infrastructure.android.MainActivity
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What survives when the real MainActivity is recreated or the screen is rotated while the Add Source dialog is open
 * with text typed in it. Opens the dialog by finding the "+" button with Compose semantics (no screen coordinates),
 * types a probe string that is not a key or an address, never presses Search, never touches the network.
 *
 * NOT covered: the Nostr preview after a rotation (needs a relay), the app going to the background, the process being
 * killed (the view model keeps nothing then, the typed text is lost), a real language or dark-mode change.
 */
@RunWith(AndroidJUnit4::class)
class SubscribeDialogRecreateDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val automation: UiAutomation get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    private var initialAutoRotate = AUTO_ROTATE_ON

    @Before
    fun rememberAutoRotate() {
        initialAutoRotate = Settings.System.getInt(
            InstrumentationRegistry.getInstrumentation().targetContext.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            AUTO_ROTATE_ON,
        )
    }

    /** Gives the phone back the rotation setting the owner had, not the one `setRotation` leaves behind. */
    @After
    fun restoreRotation() {
        automation.setRotation(
            if (initialAutoRotate == AUTO_ROTATE_ON) UiAutomation.ROTATION_UNFREEZE else UiAutomation.ROTATION_FREEZE_0,
        )
    }

    private val subscribeLabel get() = compose.activity.getString(R.string.subscribe)
    private val placeholder get() = compose.activity.getString(R.string.feed_or_site_url)

    private fun waitFor(what: String, condition: () -> Boolean) {
        try {
            compose.waitUntil(WAIT_MILLIS, condition)
        } catch (e: Throwable) {
            throw AssertionError("Never saw: $what (first launch screen, a non-Local account, or an ad on top?)", e)
        }
    }

    private fun dialogTextField() = hasSetTextAction() and hasText(PROBE_TEXT)

    private fun openDialogAndType() {
        val plusButton = hasContentDescription(subscribeLabel) and hasClickAction()
        waitFor("the + button") { compose.onAllNodes(plusButton).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(plusButton).assertCountEquals(1)
        compose.onNode(plusButton).performClick()
        waitFor("the link field") { compose.onAllNodes(hasText(placeholder)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(placeholder).performTextInput(PROBE_TEXT)
        waitFor("the typed text in an editable field") { compose.onAllNodes(dialogTextField()).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Open dialog + the same text + an editable (not locked) field: `readOnly` would remove the set-text action. */
    private fun assertDialogStillOpenWithText() {
        waitFor("the dialog with the typed text after the interruption") {
            compose.onAllNodes(dialogTextField()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun recreate_keepsTheDialogOpenAndTheTypedText() {
        openDialogAndType()
        val before = compose.activity
        compose.activityRule.scenario.recreate()
        assertNotSame("the activity must really have been recreated", before, compose.activity)
        assertDialogStillOpenWithText()
    }

    @Test
    fun realRotation_keepsTheDialogOpenAndTheTypedText_withoutRecreatingTheActivity() {
        openDialogAndType()
        val before = compose.activity
        automation.setRotation(UiAutomation.ROTATION_FREEZE_90)
        waitFor("landscape") { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        assertDialogStillOpenWithText()
        automation.setRotation(UiAutomation.ROTATION_FREEZE_0)
        waitFor("portrait") { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
        assertDialogStillOpenWithText()
        // MainActivity declares configChanges for orientation/screenSize, so a rotation must not recreate it.
        // If someone removes that, this line fails on purpose and the test needs a deliberate review.
        assertSame("rotation recreated the activity: configChanges changed?", before, compose.activity)
    }

    @Test
    fun recreateTwice_keepsTheDialogOpenAndTheTypedText() {
        openDialogAndType()
        val first = compose.activity
        compose.activityRule.scenario.recreate()
        val second = compose.activity
        assertNotSame(first, second)
        assertDialogStillOpenWithText()
        compose.activityRule.scenario.recreate()
        assertNotSame(second, compose.activity)
        assertDialogStillOpenWithText()
        assertTrue("the field must still be editable", compose.onAllNodes(dialogTextField()).fetchSemanticsNodes().size == 1)
    }

    private companion object {
        const val PROBE_TEXT = "abc-rotation-probe"
        const val WAIT_MILLIS = 15_000L
        const val AUTO_ROTATE_ON = 1
    }
}
```

- [ ] **Step 3: Biên dịch (nền, output ra file)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f /tmp/f28-build.exit
( ./gradlew assembleDevDebug assembleDevDebugAndroidTest > /tmp/f28-build.txt 2>&1; echo $? > /tmp/f28-build.exit ) &
```
Chờ `/tmp/f28-build.exit` tồn tại, rồi: `cat /tmp/f28-build.exit; tail -5 /tmp/f28-build.txt`
Expected: `0` và `BUILD SUCCESSFUL`. Lỗi biên dịch thường gặp cần sửa: import thừa/thiếu (`onAllNodesWithContentDescription` có thể không dùng: gỡ nếu cảnh báo), `compose.activity` trả `MainActivity`. **Không thêm sleep, không đổi timeout** để chữa lỗi.

- [ ] **Step 4: Không commit ở đây.** Test chưa chạy trên máy; commit sau khi Task 2 xanh và Task 3 chứng minh khả năng bắt lỗi.

---

### Task 2: Cổng an toàn + chạy test thiết bị ba lượt

**Files:**
- Create tạm, **không commit**: `/tmp/f28_gate.sh`
- Use existing: `/tmp/pixel-backup.py`, `/tmp/pixel-restore.py` (đều phải trỏ Pixel `2B051FDH3006MU`)

**Interfaces:**
- Consumes: APK `app/build/outputs/apk/dev/debug/RSS Cat Hub-*.apk`, `app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk`; test FQCN `com.mckimquyen.reader.ui.page.home.feed.subs.SubscribeDialogRecreateDeviceTest`.
- Produces: `/tmp/f28-{gate,backup,install,run-N,restore}.log`, `/tmp/f28-run-N.exit`; xác nhận thiết bị đã phục hồi byte-exact.

- [ ] **Step 1: Viết cổng chỉ đọc**

Tạo `/tmp/f28_gate.sh` bằng Python 3 (`open(..., encoding="utf-8")`) với nội dung shell dưới đây; nếu bất kỳ điều kiện nào sai, in `STOP: <lý do>` và thoát lỗi, không chạy bước khác:

```bash
#!/bin/zsh
set -e
SERIAL=2B051FDH3006MU
APP=com.mckimquyen.reader
adb devices | grep -F "$SERIAL" | grep -F "device$" >/dev/null || { print "STOP: Pixel missing/offline"; exit 20; }
ACT=$(adb -s "$SERIAL" shell dumpsys activity activities)
print -r -- "$ACT" | grep -E 'topResumedActivity|mResumedActivity' | grep -F 'com.android.launcher' >/dev/null || { print "STOP: foreground is not launcher"; exit 21; }
print -r -- "$ACT" | grep -F 'loyalty.skyjoy.vn.dev' >/dev/null && { print "STOP: owner app foreground"; exit 22; }
adb -s "$SERIAL" shell dumpsys window | grep -E 'mDreamingLockscreen=true|isStatusBarKeyguard=true' >/dev/null && { print "STOP: screen locked"; exit 23; }
adb -s "$SERIAL" shell pidof "$APP.test" >/dev/null 2>&1 && { print "STOP: instrumentation already running"; exit 24; }
adb -s "$SERIAL" shell settings get system accelerometer_rotation
adb -s "$SERIAL" shell settings get system user_rotation
adb -s "$SERIAL" shell ps -A | grep -E 'instrumentation|mckimquyen.reader' || true
```

Run only after reading the script: `zsh /tmp/f28_gate.sh > /tmp/f28-gate.log 2>&1`; inspect full log and exit. If launcher check differs on Android 17, stop and inspect `dumpsys`; do **not** relax check based on guess. `dumpsys activity` must say launcher is `topResumedActivity`; if owner app appears anywhere in top-resumed output, stop. Check no biometric UI in the visible window before continuing.

- [ ] **Step 2: Byte-exact backup**

Run `python3 /tmp/pixel-backup.py > /tmp/f28-backup.log 2>&1`; only proceed when exit is 0, backup path is recorded, Room integrity says `ok`, and DataStore hash is recorded. Confirm `SERIALS = {'pixel': '2B051FDH3006MU'}` and file list covers `Reader`, `Reader-wal`, `Reader-shm`, `Reader-journal`, `settings.preferences_pb`, and shared prefs. Never delete DataStore. If script/API fails, stop and repair backup procedure before any install/test.

- [ ] **Step 3: Build/install by serial only**

Build from Task 1 must have `.exit=0`. Install the two APKs with:

```bash
adb -s 2B051FDH3006MU install -r app/build/outputs/apk/dev/debug/RSS\ Cat\ Hub-2026.09.06-e1ce315f.apk
adb -s 2B051FDH3006MU install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
```

Use actual APK path from `ls app/build/outputs/apk/dev/debug/`; do not assume version/hash (read it). No install to another serial. Do not `am force-stop` unrelated apps.

- [ ] **Step 4: Run one instrumentation test per invocation; record `.exit` before XML**

For N = 1, 2, 3, run as one shell invocation each:

```bash
rm -f /tmp/f28-run-1.exit
(adb -s 2B051FDH3006MU shell am instrument -w -e class com.mckimquyen.reader.ui.page.home.feed.subs.SubscribeDialogRecreateDeviceTest com.mckimquyen.reader.test > /tmp/f28-run-1.log 2>&1; print $? > /tmp/f28-run-1.exit) &
```

Wait for `/tmp/f28-run-1.exit`, then read `.exit` and the complete log. Repeat for 2 and 3 only if run 1 exits 0. Expected each: three tests pass, instrumentation reports `OK (3 tests)`. `am instrument` can return shell exit 0 despite test failures; also require log contains `OK (3 tests)` and does **not** contain `FAILURES!!!`/`INSTRUMENTATION_FAILED`.

If app stops at STARTUP, account is not Local, the `+` button is absent, the app is not in expected foreground, UI is blocked by a non-ad system dialog, or any test is red: stop; no retries with guessed taps or coordinates. If an ad covers UI, send Back only, then assess from a fresh run (do not count the interrupted run as pass).

- [ ] **Step 5: Read instrumentation XML only after run `.exit` exists**

Only after each `/tmp/f28-run-N.exit` exists:

```bash
find app/build/outputs/androidTest-results/connected -type f -name '*.xml' -print
```

If XML is emitted, read it; otherwise use the `am instrument` log as source of truth and write the absence into report. Never infer pass from a notification saying "completed".

- [ ] **Step 6: Restore data and rotation, verify**

Always run `python3 /tmp/pixel-restore.py > /tmp/f28-restore.log 2>&1`, including when instrumentation fails. Require `cmp` identical for every saved file and DataStore hash identical to backup; require `accelerometer_rotation` and `user_rotation` equal values recorded by gate. Uninstall only the test APK (`adb -s 2B051FDH3006MU uninstall com.mckimquyen.reader.test`); leave app APK installed. Run cổng read-only again; report foreground/lock/test package accurately. If restore is not byte-identical, stop and report; do not claim completion.

- [ ] **Step 7: Test Task 2 output**

Expected: all 3 tests pass 3/3 runs, phone settings and app data restored. If `UiAutomation.setRotation` is restricted on Pixel Android 17, mark test (b) not verified; do not substitute coordinate taps or alter settings outside tested API.

---

### Task 3: Mutation trên Pixel chứng minh test bắt lỗi

**Files:**
- Create tạm, **không commit**: `/tmp/f28_dev_mut.sh`, `/tmp/f28_mut_batch.sh`
- Temporary production mutations, always restore: `app/src/main/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModel.kt`

**Interfaces:**
- Consumes: 3 instrumentation tests from Task 1; backup + gate from Task 2.
- Produces: `/tmp/f28-mut-{label}.log`, `/tmp/f28-mut-batch.log`, `.exit`, `ALLDONE`, byte-identical source restore.

- [ ] **Step 1: Check working tree and mutation runner**

```bash
git status --short
grep -n "fun init()\|fun inputLink(content" app/src/main/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModel.kt
```

Require no unrelated edits besides the new test and expected docs. Read `/tmp/s01_mut.sh` before relying on it: it only runs JVM tests, so **it cannot prove an androidTest mutation**. This task uses a separate serial-pinned build/install/instrument/restore script; do not call `s01_mut.sh` on device.

- [ ] **Step 2: Ghi script đột biến chạy trên máy (`/tmp/f28_dev_mut.sh`, tạm, không commit)**

Dùng: `/tmp/f28_dev_mut.sh <label> '<old>' '<new>'`; `<label>` = `base` nghĩa là không đổi mã (chạy đường cơ sở). Script tự khôi phục và `cmp` trong `trap`, kể cả khi build/cài/chạy lỗi. Tạo bằng `cat > /tmp/f28_dev_mut.sh <<'SH'` rồi `chmod +x`:

```bash
#!/bin/zsh
label="$1"; old="${2-}"; new="${3-}"
FILE=app/src/main/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModel.kt
BACKUP=/tmp/f28-SubscribeViewModel.kt
SERIAL=2B051FDH3006MU
APP=com.mckimquyen.reader
TEST=com.mckimquyen.reader.ui.page.home.feed.subs.SubscribeDialogRecreateDeviceTest
TEST_APK=app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou || exit 90
cp -p "$FILE" "$BACKUP"
restore() { cp -p "$BACKUP" "$FILE"; cmp -s "$BACKUP" "$FILE" && rest=restored || rest=NOT-RESTORED; }
trap 'restore' EXIT INT TERM
if [ "$label" != base ]; then
  python3 - "$FILE" "$old" "$new" <<'PY'
import sys
f, o, n = sys.argv[1:4]
s = open(f, encoding="utf-8").read()
assert s.count(o) == 1, "pattern missing or not unique: " + o
open(f, "w", encoding="utf-8").write(s.replace(o, n, 1))
PY
  [ $? -eq 0 ] || { echo "$label: PATTERN-PROBLEM"; exit 91; }
fi
zsh /tmp/f28_gate.sh > "/tmp/f28-mut-$label-gate.log" 2>&1 || { echo "$label: GATE-STOP (see log)"; exit 92; }
./gradlew assembleDevDebug assembleDevDebugAndroidTest > "/tmp/f28-mut-$label-build.log" 2>&1
build=$?
[ $build -eq 0 ] || { echo "$label: build=$build"; exit 93; }
apks=(app/build/outputs/apk/dev/debug/*.apk)
[ ${#apks[@]} -eq 1 ] || { echo "$label: expected one app apk, got ${#apks[@]}"; exit 94; }
adb -s "$SERIAL" install -r "${apks[1]}" > "/tmp/f28-mut-$label-install.log" 2>&1 || { echo "$label: install-app-failed"; exit 95; }
adb -s "$SERIAL" install -r "$TEST_APK" >> "/tmp/f28-mut-$label-install.log" 2>&1 || { echo "$label: install-test-failed"; exit 96; }
adb -s "$SERIAL" shell am instrument -w -e class "$TEST" "$APP.test" > "/tmp/f28-mut-$label-run.log" 2>&1
run=$?
echo "$run" > "/tmp/f28-mut-$label-run.exit"
adb -s "$SERIAL" shell am force-stop "$APP"
failed=$(grep -c "^FAILURES\|INSTRUMENTATION_FAILED" "/tmp/f28-mut-$label-run.log")
ok=$(grep -c "^OK (3 tests)" "/tmp/f28-mut-$label-run.log")
restore
echo "$label: build=$build instrument_exit=$run ok3=$ok failure_marks=$failed $rest"
```

Ghi chú: `trap` chạy `restore` lần nữa khi thoát (vô hại, cùng một `cp`). Sau mỗi đột biến, **bản dựng trên máy là bản đột biến**; Task 3 Step 3 phải kết thúc bằng một lần `base` (dựng lại từ mã đã khôi phục và cài lại) để máy không giữ APK đột biến. Không ghi chữ thử ra logcat.

- [ ] **Step 3: Một script nền duy nhất cho cả đợt (`/tmp/f28_mut_batch.sh`)**

Đọc đúng đoạn mã hiện tại trước khi chép: `grep -n "fun init()" -A7`, `grep -n "fun inputLink" -A7`, `grep -n "fun hideDrawer" -A3` trong `SubscribeViewModel.kt`. Các cặp thay thế (độc nhất, đã kiểm trên mã ở commit `e1ce315f`; nếu `s.count != 1` thì script dừng, không đoán):

```bash
#!/bin/zsh
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou || exit 90
M=/tmp/f28_dev_mut.sh
$M base                                                                       # đường cơ sở phải xanh
$M m01-init-resets 'title = androidStringsHelper.getString(R.string.subscribe),
                groups = rssService.get().pullGroups(),' 'title = androidStringsHelper.getString(R.string.subscribe),
                groups = rssService.get().pullGroups(),
                linkContent = "",'
$M m02-input-ignored 'linkContent = content,
                errorMessage = "",' 'linkContent = "",
                errorMessage = "",'
$M m03-hide-on-init 'fun showDrawer() {
        _subscribeUiState.update { it.copy(visible = true) }' 'fun showDrawer() {
        _subscribeUiState.update { it.copy(visible = false) }'
$M base                                                                       # dựng lại từ mã sạch, máy không giữ APK đột biến
cmp -s /tmp/f28-SubscribeViewModel.kt app/src/main/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModel.kt && echo final: identical || echo final: DIFFERENT
git diff --stat -- app/src/main
echo ALLDONE
```

Chạy: `( zsh /tmp/f28_mut_batch.sh > /tmp/f28-mut-batch.log 2>&1; echo $? > /tmp/f28-mut-batch.exit ) &`. Chờ `/tmp/f28-mut-batch.exit` **và** dòng `ALLDONE`; trong lúc chờ **không sửa `SubscribeViewModel.kt`**, không chạm máy. Sau cùng: `git diff --stat -- app/src/main` phải rỗng. Quyết định `m03` (đổi `showDrawer` thành `visible = false`) làm hộp thoại không bao giờ mở nên chỉ bắt được ở bước mở; đó là đột biến "hộp thoại biến mất", chấp nhận.

- [ ] **Step 4: Interpret mutation results**

Expected: all three mutants fail for the asserted UI state, base remains green. If mutant survives, test is weak: add the missing assertion first, rerun baseline, then rerun that mutant. If a test fails for infrastructure reason (app failed to start, relay, instrumentation disconnect) it is **not** a caught mutant; diagnose, do not count it. Restore and `cmp` after every individual mutant and at batch end.

---

### Task 4: JVM, lint, build and documentation

**Files:**
- Modify: `docs/superpowers/specs/2026-11-15-front-02d-add-source-recreate-device-test-design.md` (append `## Điều chỉnh khi thực thi` if API/testing changed)
- Modify: `doc/feature.md` (add FRONT-02d outcome before in-progress section)
- Create: `doc/task/done/FRONT-02d_add-source-recreate-device-test_DONE.md`

**Interfaces:**
- Consumes: Task 1–3 results, actual test logs and device restore evidence.
- Produces: truthful report and committed docs + test on `dev`.

- [ ] **Step 1: Full JVM suite (background, output file)**

```bash
rm -f /tmp/f28-full.exit
( ./gradlew testDevDebugUnitTest --rerun-tasks > /tmp/f28-full.log 2>&1; echo $? > /tmp/f28-full.exit ) &
```
Wait `.exit`; expected exit `0`, 3.627 tests, 0 failures/errors (unless other branch changes legitimately alter count; derive actual totals from XML only now).

- [ ] **Step 2: Lint (background, output file)**

```bash
rm -f /tmp/f28-lint.exit
( ./gradlew lintDevDebug > /tmp/f28-lint.log 2>&1; echo $? > /tmp/f28-lint.exit ) &
```
Wait `.exit`; expected exit `0`, baseline around 2.465 warnings, zero new findings in touched files. Do not hide warnings.

- [ ] **Step 3: Final assemble and scope check**

```bash
rm -f /tmp/f28-assemble.exit
( ./gradlew assembleDevDebug assembleDevDebugAndroidTest > /tmp/f28-assemble.log 2>&1; echo $? > /tmp/f28-assemble.exit ) &
```
Wait `.exit`; expected exit `0`. Then `git diff --check`, inspect full `git diff`, check `git diff -- app/schemas app/src/main/res app/src/main/AndroidManifest.xml app/build.gradle` is empty (except no new changes), and `git status --short` contains no `.idea` or accidental temp files. Do not use `grep --include`.

- [ ] **Step 4: Update spec, feature log and DONE report**

Report in Vietnamese; include exact commit, full suite actual counts, lint actual warnings, 3 tests × actual runs, mutation outcomes, device `Pixel 7 Pro 2B051FDH3006MU` + Android version, backup/restore byte identity and DataStore hash, rotation setting before/after, and honest unverified items. Explicitly say no source production behavior changed. `doc/feature.md` notes test only (not a feature shipped to users). Spec `Điều chỉnh khi thực thi` records actual UiAutomation API and any differences.

- [ ] **Step 5: Commit**

```bash
git add app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeDialogRecreateDeviceTest.kt doc/feature.md doc/task/done/FRONT-02d_add-source-recreate-device-test_DONE.md docs/superpowers/specs/2026-11-15-front-02d-add-source-recreate-device-test-design.md
git commit -m "test(front-02d): verify Add Source dialog across activity recreation and rotation

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

- [ ] **Step 6: Final checks**

```bash
git status --short
git log --oneline -3
git show --stat --oneline HEAD
```
Expected: clean tree except pre-existing ignored `.idea` modifications; no `.idea` file staged; one commit on `dev`; test and docs present; no push.


---

## Self-review

**Phủ spec:** ba test (a)(b)(c) ở Task 1; cổng, sao lưu, cấm tọa độ, cấm `connected*`, khôi phục ở Task 2; đột biến "init reset / linkContent / hideDrawer" ở Task 3 (spec ghi `hideDrawer` trong `init()`; thay bằng `showDrawer → visible=false` vì `hideDrawer` đã được gọi bởi luồng đóng thật và đột biến trực tiếp trên nó làm `reset()`/dọn dẹp lệch, nên đổi sang điểm mở hộp thoại, ghi ở Điều chỉnh); JVM, lint, assemble, báo cáo, feature.md ở Task 4. Thêm so với spec: giữ cài đặt tự xoay của owner (Điều chỉnh 2), kiểm `assertNotSame`/`assertSame` (Điều chỉnh 6).

**Quét placeholder:** Task 2 Step 1 chỉ mô tả cổng bằng script đầy đủ; Task 3 script đã viết đủ lệnh; không còn "outline", "TBD".

**Nhất quán:** tên lớp/test, `PROBE_TEXT`, `WAIT_MILLIS`, `AUTO_ROTATE_ON`, serial `2B051FDH3006MU` giống nhau mọi nơi; `UiAutomation.ROTATION_FREEZE_0/90`, `ROTATION_UNFREEZE` là hằng số có thật của API (kiểm lại khi biên dịch ở Task 1 Step 3).

**Rủi ro còn lại, nói thẳng:** (1) `createAndroidComposeRule<MainActivity>` có thể khởi động Splash/quảng cáo hoặc sync chen vào; nếu lỗi "No compose hierarchies found" thì chuyển cách của `EdgeToEdgeInsetsTest` bằng điều chỉnh có chủ đích, không thêm sleep. (2) Hộp thoại là `Dialog` (cửa sổ riêng): `compose.onNode...` có thể không thấy nút/ô trong cửa sổ đó trên mọi phiên bản; Compose test 1.7 hỗ trợ nhưng nếu không thấy, dừng và điều tra, không tăng timeout. (3) Pixel đang là Android 17: `setRotation` có thể bị hạn chế.
