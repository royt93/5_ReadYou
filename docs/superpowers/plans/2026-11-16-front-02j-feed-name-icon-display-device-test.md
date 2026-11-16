# FRONT-02j — Test máy thật: tên và icon nguồn Bluesky/Nostr hiện đúng trên màn hình Pixel (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (hoặc subagent-driven-development) để thực thi plan này từng task. Các bước dùng checkbox `- [ ]`.

**Goal:** Có bằng chứng trên **Pixel 7 Pro**, bằng một Activity test thật, rằng `FeedIcon` thật và một `Text` cùng kiểu với `FeedItem` hiện đúng tên `Bluesky · …` / `Nostr · …`, cắt tên dài, tải và vẽ icon từ máy chủ giả, và hiện vòng tròn chữ cái đầu khi không có icon.

**Architecture:** Một lớp androidTest dùng `createAndroidComposeRule<ComponentActivity>()` (không `MainActivity`, không Hilt), một `MockWebServer` phục vụ ảnh PNG đỏ 64×64 sinh bằng `Bitmap`, một composable thử nhỏ `AppTheme { Row { Box(tag) { FeedIcon } ; Text } }`. Kiểm bằng Compose semantics, `TextLayoutResult.hasVisualOverflow` và `captureToImage()` (điểm giữa ô icon).

**Tech Stack:** `androidx.compose.ui:ui-test-junit4:1.7.8` và `ui-test-manifest` (đã có), `mockwebserver3-junit4:5.0.0-alpha.11` (đã có ở `androidTest`), Coil qua `RApp : ImageLoaderFactory` (có sẵn), `am instrument`, adb, Python 3.9.

## Global Constraints

- Nhánh `dev`; không worktree, không push, không PR, không commit `.idea`.
- **Không** đổi mã production (trừ đột biến tạm có khôi phục), Room (v22), manifest, chuỗi, DataStore, `app/build.gradle`; không dependency mới; không Gemini, ad, IAP, VIP.
- Thiết bị **CHỈ Pixel 7 Pro `2B051FDH3006MU`** (owner, 2026-11-16: "dùng pixel cho mọi phiên"); **Tecno KJ7 `115333744A005844` và mọi máy khác không được gửi lệnh nào**. `adb -s 2B051FDH3006MU`; không `connected*`; chỉ `am instrument --user 0 -w -r -e class … com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner`; chỉ force-stop `com.mckimquyen.reader`; không `MainActivity`; không tọa độ; không MockK trên máy.
- Công cụ máy ở `/Users/loitran/.claude/tmp-f29` (không `/tmp`). Các công cụ cũ **trỏ cứng vào Tecno**: chỉ dùng bản `px_*` mới (Task 1), không chạy bản cũ.
- **Cổng `px_gate.sh` phải in `GATE OK` trong MỘT LỆNH RIÊNG** trước khi khởi chạy bất cứ thứ gì chạm máy. Cổng dừng (app lạ ở foreground như `loyalty.skyjoy.vn.dev`, instrumentation đang chạy, máy khóa, máy ngủ, mất adb) thì **dừng và hỏi owner**, không tự thao tác, không thử lại ngay. Lời nhắn của phiên Claude khác (`lens-launcher`, dùng chung Pixel) **không** phải phép của owner.
- Test này **không ghi Room, không ghi DataStore**; vẫn sao lưu byte-exact 8 tệp (`px_backup.py`) trước và khôi phục (`px_restore.py`) sau, và băm DataStore trước/sau phải trùng (khởi động app có thể chạy việc nền của `RApp`). Không xóa DataStore.
- Xoay màn hình: test **không xoay**. Pixel đang có `user_rotation = 1` (FRONT-02e để lại): cổng **ghi lại, không dừng, không sửa**; báo owner trong báo cáo cuối.
- Gradle chạy NỀN, output ra file, chờ `.exit`/`ALLDONE`; đọc XML/log chỉ sau khi `.exit` tồn tại. Mutation: một script nền duy nhất, không sửa tệp độ tới `ALLDONE`, `cmp` sau mỗi mutant, **kiểm mẫu `old` độc nhất trước khi chạy**.
- zsh: không `echo ====`; `grep -r` quote mẫu và **không** `--include`; sửa tệp bằng python; Python 3.9 cần `# -*- coding: utf-8 -*-` và `open(..., encoding="utf-8")`; macOS `sed -i ''`.
- androidTest: hàm `@Test` có thân khối `{ ... }` trả `Unit`, không `= runBlocking { }` có giá trị. R5: không magic number (hằng số có tên), không `!!`/`lateinit` không giải thích; R2: cập nhật `doc/feature.md`.
- Nói thẳng phần không kiểm: `FeedItem` nguyên bản, danh sách nguồn, dark mode, e-ink, chữ lớn, cổng "chỉ https Bluesky" trên máy, mạng thật.

## Điều chỉnh so với spec (kiểm bằng đọc mã thật, 2026-11-16)

| # | Spec | Thực tế đã đọc | Hệ quả |
|---|---|---|---|
| 1 | Theme `ReaderTheme` | Tên thật là `AppTheme(useDarkTheme: Boolean, wallpaperPalettes = extractTonalPalettesFromUserWallpaper(), content)` (`ui/theme/Theme.kt:21`) | Test dùng `AppTheme(useDarkTheme = false)`; nếu thiếu `CompositionLocal` làm đỏ khi biên dịch/chạy, rơi về `MaterialTheme` mặc định và ghi lại |
| 2 | (c) "chữ hiện cuối bằng …" | Semantics giữ **chuỗi đầy đủ**, dấu "…" chỉ là hiệu ứng vẽ | (c) kiểm `hasVisualOverflow` (true cho tên dài, false cho tên ngắn), không kiểm ký tự "…" |
| 3 | Mutant `maxLines` "không hợp lệ" | `Text` thử nằm trong tệp test, nên đổi nó là kiểm **test không rỗng**, không phải kiểm mã production | Có một mutant nằm trong **tệp test** (m3), ghi rõ trong báo cáo là kiểm chất lượng test |
| 4 | Cổng/sao lưu/chạy lặp dùng sẵn | `gate.sh`: serial Tecno, launcher `com.transsion.hilauncher`, 1080x2436, bắt `user_rotation=0`; `backup.py`/`restore.py`: `SERIALS = {'tecno': …}`; `loop.sh`, `dev_mut.sh`: serial Tecno, `dev_mut.sh` cứng tên lớp test Nostr và "OK (4 tests)" | Task 1 tạo bản `px_*` mới, `grep` kiểm không còn serial Tecno; launcher và kích thước Pixel phải **đọc thật bằng lệnh chỉ-đọc**, không đoán |
| 5 | Ca 404 hiện hình ảnh hỏng | Chỉ thêm nếu rẻ | Không thêm; ghi vào nợ |
| 6 | Chờ Coil | `BaseAsyncImage` có `placeholder = null` (trước khi tải chỉ thấy nền `surface`) | Chờ có điều kiện: máy chủ giả đã bị hỏi **và** điểm giữa ô icon là đỏ, trong hạn `LOAD_TIMEOUT_MS`; không `sleep` cố định |
| 7 | Máy chủ ảnh | `RApp.okHttpClient` dùng chung; manifest `usesCleartextTraffic="true"`; 02h đã dùng `MockWebServer` + `app.okHttpClient` thành công 20/20 | Dùng `images.url(...)` y như 02h; ảnh mỗi test có đường dẫn riêng (UUID) để tránh bộ nhớ đệm Coil |

## Cấu trúc file

| File | Việc |
|---|---|
| Create `app/src/androidTest/java/com/mckimquyen/reader/ui/component/FeedNameIconDeviceTest.kt` | 5 test thiết bị (Task 2) |
| Tạm, **không commit**, trong `/Users/loitran/.claude/tmp-f29/`: `px_gate.sh`, `px_backup.py`, `px_restore.py`, `px_loop.sh`, `px_mut.sh`, `px_mut_batch.py` | cổng, sao lưu, chạy lặp 20 lượt, đột biến trên Pixel |
| Modify `doc/feature.md`, `doc/task/done/FRONT-02g…`, `FRONT-02h…` (nợ "hiển thị tên/icon") | tài liệu |
| Create `doc/task/done/FRONT-02j_feed-name-icon-display-device-test_DONE.md` | báo cáo |

Dùng lại, **không sửa**: `FeedIcon.kt`, `BaseAsyncImage.kt`, `BlueskyFeedMapper.kt`, `NostrFeedMapper.kt`, `NostrDeviceFixtures.kt` (`AUTHOR_HEX`).

---
### Task 1: Công cụ riêng cho Pixel và hiệu chỉnh cổng bằng lệnh CHỈ-ĐỌC

**Mục đích:** các công cụ cũ trỏ cứng vào Tecno; tạo bản `px_*` cho Pixel, đọc thật những gì cổng cần biết (không đoán), và dừng nếu thấy điều lạ. **Chưa cài, chưa chạy test, chưa ghi gì lên máy.**

**Files (tạm, không commit):** `/Users/loitran/.claude/tmp-f29/px_gate.sh`, `px_backup.py`, `px_restore.py`.

- [ ] **Step 1: Đọc chỉ-đọc từ Pixel (mỗi lệnh có `-s 2B051FDH3006MU`), ghi lại kết quả**

```bash
S=2B051FDH3006MU
adb devices
adb -s $S shell wm size
adb -s $S shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN
adb -s $S shell dumpsys activity activities | grep -E 'topResumedActivity' | head -1
adb -s $S shell dumpsys window | grep -E 'mCurrentFocus|isStatusBarKeyguard|mShowingLockscreen|mDreamingLockscreen' | head -5
adb -s $S shell dumpsys power | grep -E 'mWakefulness=' | head -1
adb -s $S shell dumpsys activity processes | grep -E 'ActiveInstrumentation|instrumentationClass=' | head -3
adb -s $S shell settings get system accelerometer_rotation
adb -s $S shell settings get system user_rotation
adb -s $S shell settings get system font_scale
adb -s $S shell pm list packages com.mckimquyen.reader
adb -s $S shell pm list packages --user 0 | grep -c 'com.mckimquyen.reader.test'
```
Expected: Pixel `device`; kích thước đọc được; gói launcher (ví dụ `…nexuslauncher/…`); `topResumedActivity` là launcher; `mWakefulness=Awake`; không instrumentation; `user_rotation` 1 là **đã biết** (ghi lại, không sửa); `font_scale` 1.0 hoặc khác (ghi lại); `com.mckimquyen.reader` **có cài**.
**Dừng và hỏi owner (AskUserQuestion) nếu:** `adb devices` không thấy Pixel hoặc thấy `unauthorized`; `topResumedActivity` không phải launcher (nhất là `loyalty.skyjoy.vn.*`); có instrumentation; máy ngủ/khóa; app `com.mckimquyen.reader` chưa cài (không tự cài từ nay).

- [ ] **Step 2: Tạo `px_gate.sh`** (launcher lấy từ `resolve-activity`, không hardcode; xoay chỉ **báo**, không dừng)

```zsh
#!/bin/zsh
SERIAL=2B051FDH3006MU
APP=com.mckimquyen.reader
adb devices | grep -E "^${SERIAL}[[:space:]]+device$" >/dev/null || { print "STOP: Pixel missing/offline"; exit 20; }
HOME_PKG=$(adb -s "$SERIAL" shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN | tr -d '\r' | tail -1 | cut -d/ -f1)
{ [ -n "$HOME_PKG" ] && [ "$HOME_PKG" != "android" ]; } || { print "STOP: cannot tell the launcher ($HOME_PKG)"; exit 22; }
TOP=$(adb -s "$SERIAL" shell dumpsys activity activities | grep -E 'topResumedActivity' | head -1)
print -r -- "$TOP" | grep -F "$HOME_PKG" >/dev/null || { print "STOP: foreground is not the launcher: $TOP"; exit 21; }
FOCUS=$(adb -s "$SERIAL" shell dumpsys window | grep -E 'mCurrentFocus' | head -1)
print -r -- "$FOCUS" | grep -F "$HOME_PKG" >/dev/null || { print "STOP: window focus is not the launcher: $FOCUS"; exit 23; }
adb -s "$SERIAL" shell dumpsys power | grep -E 'mWakefulness=Awake' >/dev/null || { print "STOP: screen not awake"; exit 24; }
adb -s "$SERIAL" shell dumpsys window | grep -E 'mDreamingLockscreen=true|isStatusBarKeyguard=true|mShowingLockscreen=true' >/dev/null && { print "STOP: screen locked"; exit 25; }
adb -s "$SERIAL" shell dumpsys activity processes | grep -E 'ActiveInstrumentation|instrumentationClass=' >/dev/null && { print "STOP: some instrumentation is active"; exit 26; }
adb -s "$SERIAL" shell pm list packages "$APP" | grep -Fx "package:$APP" >/dev/null || { print "STOP: $APP is not installed"; exit 27; }
ACC=$(adb -s "$SERIAL" shell settings get system accelerometer_rotation | tr -d '\r')
USR=$(adb -s "$SERIAL" shell settings get system user_rotation | tr -d '\r')
print "GATE OK: launcher $HOME_PKG on top and focused, awake, unlocked, no instrumentation; rotation accel=$ACC user=$USR (reported, not checked: the test does not rotate)"
adb -s "$SERIAL" shell ps -A | grep -E 'mckimquyen.reader' || print "app process: none"
exit 0
```
Nếu Step 1 cho thấy `mCurrentFocus` hoặc `mWakefulness` có dạng khác trên Android 17, **sửa lệnh `grep` theo điều đã đọc** và ghi vào mục "Điều chỉnh khi thực thi" (không bỏ kiểm tra đi).

- [ ] **Step 3: Tạo `px_backup.py` và `px_restore.py` từ bản Tecno**, chỉ đổi đúng ba chỗ

```bash
D=/Users/loitran/.claude/tmp-f29
python3 - <<'PY'
# -*- coding: utf-8 -*-
D="/Users/loitran/.claude/tmp-f29/"
for src,dst in (("backup.py","px_backup.py"),("restore.py","px_restore.py")):
    s=open(D+src,encoding="utf-8").read()
    s=s.replace("{'tecno': '115333744A005844'}","{'pixel': '2B051FDH3006MU'}")
    s=s.replace("/Users/loitran/.claude/tmp-f29/root","/Users/loitran/.claude/tmp-f29/px_root")
    assert "115333744A005844" not in s and "tecno" not in s, dst
    open(D+dst,"w",encoding="utf-8").write(s)
PY
grep -n "115333744A005844\|tecno" $D/px_*.py $D/px_gate.sh; echo "grep exit $? (1 = none left)"
```
Expected: `grep exit 1` (không còn serial hay chữ Tecno trong ba tệp `px_*`).

- [ ] **Step 4: Chạy cổng MỘT LẦN, trong một lệnh riêng**

```bash
zsh /Users/loitran/.claude/tmp-f29/px_gate.sh
```
Expected: `GATE OK …`. Nếu `STOP:`, **dừng và hỏi owner**, không thử lại ngay, không làm bước nào tiếp theo chạm máy.

(Không commit ở task này; công cụ nằm ngoài kho.)

---

### Task 2: Viết test thiết bị và biên dịch (KHÔNG chạy trên máy)

**Files:**
- Create: `app/src/androidTest/java/com/mckimquyen/reader/ui/component/FeedNameIconDeviceTest.kt`

**Về TDD:** mã production đã có sẵn nên lần chạy đầu **dự kiến xanh**; "đỏ đúng lý do" được chứng minh bằng **mutant ở Task 5** (mỗi mutant phải làm đúng một test đỏ). Nếu lần chạy đầu đỏ, **dừng, đọc lý do, không sửa mã production**; sửa test chỉ khi hiểu vì sao.

- [ ] **Step 1: Viết tệp test**

```kotlin
package com.mckimquyen.reader.ui.component

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper
import com.mckimquyen.reader.infrastructure.social.nostr.NostrFeedMapper
import com.mckimquyen.reader.infrastructure.social.nostr.NostrKeys
import com.mckimquyen.reader.ui.page.home.feed.subs.NostrDeviceFixtures
import com.mckimquyen.reader.ui.theme.AppTheme
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

private const val ICON_TAG = "front02j_icon"
private const val NAME_TAG = "front02j_name"
private const val PNG_SIDE_PX = 64
private const val PNG_QUALITY = 100
private const val RED_MIN = 0.9f
private const val OTHER_MAX = 0.1f
private const val LOAD_TIMEOUT_MS = 10_000L
private const val LONG_NAME_CHARS = 60
private const val SAMPLE_ACTOR = "alice.bsky.social"
private val ROW_WIDTH = 240.dp

/**
 * What a source's name and icon look like on the real screen. Draws the app's real FeedIcon and a Text with the same style,
 * maxLines and overflow as FeedItem, in a plain ComponentActivity (no MainActivity, no ad, no Hilt, no screen coordinates).
 * The icon comes from a fake server on 127.0.0.1 through the app's real image loader.
 *
 * NOT covered: the real FeedItem or the source list, dark mode, e-ink mode, a large font, the https-only Bluesky icon rule
 * (the fake server is plain http; that rule has JVM tests only), a real network, an icon that fails to load.
 */
@RunWith(AndroidJUnit4::class)
class FeedNameIconDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val imageRequests = AtomicInteger()
    private val redPng: ByteArray = ByteArrayOutputStream().also { out ->
        Bitmap.createBitmap(PNG_SIDE_PX, PNG_SIDE_PX, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(AndroidColor.RED) }
            .compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
    }.toByteArray()
    private val images = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                imageRequests.incrementAndGet()
                return MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(redPng))
            }
        }
        start()
    }
    private val shown = mutableStateOf(Shown("", null, ROW_WIDTH))

    private data class Shown(val name: String, val icon: String?, val width: Dp)

    @Before
    fun drawTheProbeRow() {
        compose.setContent {
            val current = shown.value
            AppTheme(useDarkTheme = false) { SourceRow(current) }
        }
    }

    @After
    fun stopImageServer() {
        images.shutdown()
    }

    /** Same shape as FeedItem lines 74-82: an icon, then a one-line name that ends with an ellipsis when it does not fit. */
    @Composable
    private fun SourceRow(source: Shown) {
        Row(modifier = Modifier.width(source.width)) {
            Row(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.testTag(ICON_TAG)) { FeedIcon(source.name, source.icon) }
                Text(
                    modifier = Modifier.testTag(NAME_TAG).padding(start = 12.dp, end = 6.dp),
                    text = source.name,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    private fun show(name: String, icon: String? = null, width: Dp = ROW_WIDTH) {
        compose.runOnIdle { shown.value = Shown(name, icon, width) }
        compose.waitForIdle()
    }

    private fun iconCenter(): Color {
        val pixels = compose.onNodeWithTag(ICON_TAG).captureToImage().toPixelMap()
        return pixels[pixels.width / 2, pixels.height / 2]
    }

    private fun isPureRed(c: Color) = c.red >= RED_MIN && c.green <= OTHER_MAX && c.blue <= OTHER_MAX

    private fun nameOverflows(): Boolean {
        val action = compose.onNodeWithTag(NAME_TAG).fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
        assertNotNull("the name must expose its text layout", action)
        val layouts = mutableListOf<TextLayoutResult>()
        action?.invoke(layouts)
        return layouts.single().hasVisualOverflow
    }

    @Test
    fun aBlueskyName_isShownExactlyAsTheAppBuildsIt() {
        val name = BlueskyFeedMapper.feedName(SAMPLE_ACTOR, BlueskyFeedMapper.cleanDisplayName("Alice   A"))
        show(name)
        compose.onNodeWithTag(NAME_TAG).assertTextEquals("Bluesky · Alice A")
        assertEquals("Bluesky · Alice A", name)
    }

    @Test
    fun aNostrName_isShownExactlyAsTheAppBuildsIt() {
        val npub = requireNotNull(NostrKeys.hexToNpub(NostrDeviceFixtures.AUTHOR_HEX))
        show(NostrFeedMapper.feedName(npub, "Probe"))
        compose.onNodeWithTag(NAME_TAG).assertTextEquals("Nostr · Probe")
    }

    @Test
    fun aLongName_isCutToOneLine_andAShortNameIsNot() {
        val longName = BlueskyFeedMapper.feedName(SAMPLE_ACTOR, BlueskyFeedMapper.cleanDisplayName("W".repeat(LONG_NAME_CHARS)))
        assertEquals(BlueskyFeedMapper.FEED_NAME_PREFIX.length + BlueskyFeedMapper.MAX_NAME_CODE_POINTS, longName.length)
        show(longName)
        compose.onNodeWithTag(NAME_TAG).assertTextEquals(longName)
        assertTrue("a name that does not fit must be cut with an ellipsis", nameOverflows())
        show(BlueskyFeedMapper.feedName(SAMPLE_ACTOR, "Al"))
        assertFalse("a short name must not be cut", nameOverflows())
    }

    @Test
    fun anIconFromTheServer_isDownloadedAndDrawn() {
        show("Bluesky · Probe", icon = null)
        assertFalse("a source without an icon must not look like the server's red picture", isPureRed(iconCenter()))
        show("Bluesky · Probe", icon = images.url("/avatar-${UUID.randomUUID()}.png").toString())
        compose.waitUntil(LOAD_TIMEOUT_MS) { isPureRed(iconCenter()) }
        assertTrue("the server must have been asked for the picture", imageRequests.get() >= 1)
    }

    @Test
    fun aSourceWithoutAnIcon_showsItsFirstLetter_andAsksTheServerForNothing() {
        show("Bluesky · Probe")
        compose.onNode(hasText("B") and hasAnyAncestor(hasTestTag(ICON_TAG))).assertIsDisplayed()
        compose.waitForIdle()
        assertEquals(0, imageRequests.get())
    }
}
```

- [ ] **Step 2: Biên dịch trên máy tính (nền, chờ `.exit`)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
D=/Users/loitran/.claude/tmp-f29; rm -f $D/f02j-build.exit
(./gradlew assembleDevDebug assembleDevDebugAndroidTest > $D/f02j-build.log 2>&1; echo $? > $D/f02j-build.exit) > /dev/null 2>&1 &
```
Expected sau `.exit`: `0`. Nếu lỗi biên dịch, đọc đúng lỗi, sửa **test** (ví dụ `setBody(Buffer)`, `AppTheme`, `captureToImage`); nếu `AppTheme` không dùng được ngoài Hilt/Settings, **dừng và hỏi** thay vì đổi mã production. Ghi mọi chỉnh vào "Điều chỉnh khi thực thi".

- [ ] **Step 3: Chưa commit** (commit sau khi chạy máy xanh 20/20 ở Task 4).

---
### Task 3: Lần chạy đầu trên Pixel (một lượt, có cổng, có sao lưu)

**Files (tạm, không commit):** `/Users/loitran/.claude/tmp-f29/px_loop.sh` (tạo từ `loop.sh`).

- [ ] **Step 1: Tạo `px_loop.sh` từ `loop.sh`**, đổi đúng bốn chỗ, rồi kiểm

```bash
python3 - <<'PY'
# -*- coding: utf-8 -*-
D="/Users/loitran/.claude/tmp-f29/"
s=open(D+"loop.sh",encoding="utf-8").read()
s=s.replace("115333744A005844","2B051FDH3006MU")
s=s.replace("gate.sh","px_gate.sh").replace("backup.py","px_backup.py").replace("restore.py","px_restore.py")
s=s.replace("$(cat $D/root)/tecno/","$(cat $D/px_root)/pixel/")
s=s.replace("com.mckimquyen.reader.ui.page.home.feed.subs.$CLS","com.mckimquyen.reader.ui.component.$CLS")
s=s.replace("CLS=${2:-SubscribeNostrPreviewRecreateDeviceTest}","CLS=${2:-FeedNameIconDeviceTest}")
s=s.replace("loop-","pxloop-")
open(D+"px_loop.sh","w",encoding="utf-8").write(s)
PY
grep -n "115333744A005844\|tecno\|Tecno\|subs\." /Users/loitran/.claude/tmp-f29/px_loop.sh; echo "grep exit $? (1 = none left)"
grep -c "2B051FDH3006MU" /Users/loitran/.claude/tmp-f29/px_loop.sh
```
Expected: `grep exit 1`; số dòng có serial Pixel ≥ 5. Đọc cả tệp một lần: **mọi** lệnh `adb` phải có `-s $SERIAL` (trừ `adb devices` trong cổng).

- [ ] **Step 2: Cổng, MỘT LỆNH RIÊNG:** `zsh /Users/loitran/.claude/tmp-f29/px_gate.sh` → phải in `GATE OK`. Nếu `STOP:` thì dừng và hỏi owner.

- [ ] **Step 3 (lệnh riêng, sau khi thấy `GATE OK`): chạy một lượt** (nền)

```bash
D=/Users/loitran/.claude/tmp-f29; rm -f $D/px1.out
nohup zsh $D/px_loop.sh 1 > $D/px1.out 2>&1 &
```
Chờ `ALLDONE` trong `px1.out`. Đọc: `run1: exit=0 OK (5 tests)`, `restore_exit=0`, 8 hoặc ít hơn dòng `BYTE IDENTICAL` **bằng số tệp có trong bản sao lưu**, `datastore now` **bằng** `datastore backup`, dòng `rotation:` (chỉ ghi lại).
- Xanh 5/5: sang Task 4.
- Đỏ: đọc `pxloop-run1.log`, tìm test đỏ và dòng đỏ; **không sửa mã production, không sửa gì tới khi hiểu lý do**; ghi lý do vào "Điều chỉnh khi thực thi". Nếu test sai (ví dụ ngưỡng màu, thời gian chờ), sửa test rồi chạy lại một lượt.
- Cổng dừng giữa chừng: dừng, hỏi owner.

---

### Task 4: Chạy lặp 20 lượt

- [ ] **Step 1: Cổng (lệnh riêng) rồi `nohup zsh $D/px_loop.sh 20 > $D/px20.out 2>&1 &`**, chờ `ALLDONE`.
Expected: 20 dòng `run<i>: exit=0 OK (5 tests)`, restore byte-identical, băm DataStore trước = sau.
- Chập chờn (bất kỳ lượt đỏ): **điều tra gốc** (đọc log lượt đỏ, so thời gian chờ Coil, so cổng); không thêm vòng gõ lại. Ghi số lượt đỏ/xanh thật vào báo cáo, kể cả khi đã sửa.
- [ ] **Step 2: Commit test** (sau 20/20 xanh)

```bash
git add app/src/androidTest/java/com/mckimquyen/reader/ui/component/FeedNameIconDeviceTest.kt
git diff --check && git commit -m "test(front-02j): show a Bluesky or Nostr source's name and icon on the real screen of the phone

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```
Trước commit: `git log --oneline | grep -c "test(front-02j)"` phải là 0 (không hai commit trùng tiêu đề).

---

### Task 5: Đột biến trên máy (mỗi mutant phải làm đúng một nhóm test đỏ)

**Files (tạm, không commit):** `px_mut.sh` (từ `dev_mut.sh`, đổi serial → Pixel, `gate.sh` → `px_gate.sh`, `TEST=com.mckimquyen.reader.ui.component.FeedNameIconDeviceTest`, `ok=$(grep -c "^OK (5 tests)" …)`), `px_mut_batch.py`.

| Nhãn | Tệp | Thay | Bằng | Test phải đỏ |
|---|---|---|---|---|
| m1_no_image | `ui/component/FeedIcon.kt` | `data = iconUrl,` | `data = null,` | `anIconFromTheServer_isDownloadedAndDrawn` |
| m2_no_letter | `ui/component/FeedIcon.kt` | `text = feedName.ifEmpty { " " }.first().toString(),` | `text = "",` | `aSourceWithoutAnIcon_showsItsFirstLetter…` |
| m3_no_cut (trong tệp TEST, kiểm chất lượng test) | `FeedNameIconDeviceTest.kt` | `maxLines = 1,` | `maxLines = Int.MAX_VALUE,` | `aLongName_isCutToOneLine…` |
| m4_always_letter | `ui/component/FeedIcon.kt` | `if (iconUrl == null) {` | `if (true) {` | `anIconFromTheServer…` |

- [ ] **Step 1: Kiểm mẫu `old` độc nhất** (đếm đúng 1 ở mỗi tệp) bằng python trước khi chạy; `dev_mut.sh` cũ có `assert s.count(o) == 1` nên `px_mut.sh` giữ nguyên dòng đó (khác `jvm_mut.sh` chỉ `>= 1`).
- [ ] **Step 2: Một lượt `base` trước** (không đổi gì, phải `OK (5 tests)`), rồi bốn mutant lần lượt trong **một** script nền, không sửa tệp độ tới `ALLDONE`; cổng chạy **trong từng mutant** (đã có trong `px_mut.sh`) và **dừng cả batch** khi cổng dừng, rồi hỏi owner.
- [ ] **Step 3:** sau batch, `cmp` từng tệp với bản sao lưu (`git diff --stat -- app/src/main` phải rỗng; tệp test sạch so với commit Task 4). Sau batch **cài lại bản gốc** bằng một lượt `px_loop.sh 1` để không để APK đột biến trên máy; chạy lại sao lưu/khôi phục như thường.
- Mutant sống sót: thêm hoặc siết test, rồi chạy lại **đúng mutant đó**.

---

### Task 6: Kiểm cuối, báo cáo, tài liệu, commit

- [ ] **Step 1:** `./gradlew :app:testDevDebugUnitTest --rerun-tasks` (nền) → kỳ vọng **3.720**, 0 lỗi (không đổi, vì chỉ thêm androidTest); đếm từ XML cùng cách như 02g/02h/02i. `assembleDevDebug assembleDevDebugAndroidTest` thành công; lint không quét `androidTest` (nói thẳng).
- [ ] **Step 2:** kiểm không đụng gì ngoài phạm vi:

```bash
git diff --stat HEAD~1 -- app/schemas app/src/main app/build.gradle app/src/main/res app/src/main/AndroidManifest.xml
```
Expected: rỗng (commit test chỉ thêm một tệp androidTest).
- [ ] **Step 3:** Viết `doc/task/done/FRONT-02j_feed-name-icon-display-device-test_DONE.md` (số lượt đỏ/xanh thật, kết quả mutant, băm DataStore trước/sau, `user_rotation` Pixel hiện tại **báo owner, không sửa**, điều chỉnh khi thực thi); cập nhật `doc/feature.md` (R2); sửa nợ "hiển thị tên/icon" trong báo cáo 02g và 02h: nay đã kiểm bằng **hai composable thật trong Activity test**, **chưa** kiểm `FeedItem`/danh sách thật, dark mode, e-ink, chữ lớn, icon https Bluesky trên máy.
- [ ] **Step 4:** `git diff --check`; kiểm không có commit trùng tiêu đề; commit tài liệu `docs(front-02j): report …`.

---

## Self-review

1. **Spec coverage:** (a) Bluesky tên → test 1; (b) Nostr tên → test 2; (c) tên dài cắt, tên ngắn không → test 3; (d) icon tải và vẽ → test 4; (e) không icon → test 5; an toàn thiết bị (cổng riêng, sao lưu byte-exact, băm DataStore, không xoay, 20 lượt) → Task 1, 3, 4; mutation → Task 5; tài liệu → Task 6. Ca 404 hiện hình hỏng: **cố ý không làm**, ghi nợ (điều chỉnh 5).
2. **Placeholder scan:** không TBD; mọi bước có lệnh hoặc mã. Chỗ phụ thuộc điều chưa đọc (launcher Pixel, dạng `mCurrentFocus` Android 17, `AppTheme` ngoài Hilt) có bước đọc và điều kiện dừng rõ.
3. **Nhất quán tên:** `ICON_TAG`, `NAME_TAG`, `show`, `iconCenter`, `isPureRed`, `nameOverflows`, `imageRequests`, `Shown`, `SourceRow` dùng đúng một nghĩa; tên test khớp bảng mutant (`aLongName_isCutToOneLine_andAShortNameIsNot`, `anIconFromTheServer_isDownloadedAndDrawn`, `aSourceWithoutAnIcon_showsItsFirstLetter_andAsksTheServerForNothing`).
4. **Rủi ro nhận thấy khi rà:** (i) m4 `if (true)` làm `FeedIcon` luôn vẽ chữ cái nên chỉ test (d) đỏ, đúng như bảng; (ii) test (d) xác nhận nền "không đỏ" khi chưa có icon, nên chỉ đúng nếu `surface`/`primary` của `AppTheme` không phải đỏ tinh khiết (ngưỡng `RED_MIN = 0.9`, `OTHER_MAX = 0.1`); nếu màn hình wallpaper của Pixel cho màu chính gần đỏ, bước đầu sẽ đỏ vì lý do rõ ràng và phải siết ngưỡng trong test; (iii) Pixel có thể bật động-màu theo hình nền: `extractTonalPalettesFromUserWallpaper()` đọc hình nền thật nên màu chính thay đổi giữa các máy.
