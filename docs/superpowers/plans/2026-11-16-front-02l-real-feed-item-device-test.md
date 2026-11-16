# FRONT-02l — Test máy thật: `FeedItem` nguyên bản hiện đúng dòng nguồn Bluesky/Nostr trên Pixel (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (hoặc subagent-driven-development) để thực thi plan này từng task. Các bước dùng checkbox `- [ ]`.

**Goal:** Có bằng chứng trên **Pixel 7 Pro**, bằng `FeedItem` nguyên bản (không composable thử), rằng dòng nguồn Bluesky/Nostr hiện đúng tên, icon, huy hiệu, ẩn/hiện và bấm, mà **không đổi mã production** và không dùng Hilt.

**Architecture:** Một lớp androidTest dùng `createAndroidComposeRule<ComponentActivity>()`; cung cấp một `ViewModelStoreOwner` tự dựng (`TestOwner : ViewModelStoreOwner, HasDefaultViewModelProviderFactory`) qua `LocalViewModelStoreOwner`, để `hiltViewModel()` ở tham số mặc định của `FeedItem` dựng `FeedOptionViewModel` từ `RApp`. Icon từ `MockWebServer` (PNG đỏ), kiểm bằng Compose semantics và `captureToImage()` như 02j.

**Tech Stack:** `androidx.compose.ui:ui-test-junit4:1.7.8` (đã có), `mockwebserver3-junit4:5.0.0-alpha.11` (đã có), lifecycle **2.9.2** (phiên bản thật được resolve), `hilt-navigation-compose 1.0.0`, `am instrument`, adb, Python 3.9.

## Global Constraints

- Nhánh `dev`; không worktree, không push, không PR, không commit `.idea`.
- **Không** đổi mã production (trừ đột biến tạm có khôi phục), Room (v22), manifest, chuỗi, DataStore, `app/build.gradle`; không dependency mới (không `hilt-android-testing`); không Gemini, ad, IAP, VIP.
- Thiết bị **CHỈ Pixel 7 Pro `2B051FDH3006MU`**; Tecno KJ7 và mọi máy khác không được gửi lệnh nào. `adb -s 2B051FDH3006MU`; không `connected*`; chỉ `am instrument --user 0 -w -r -e class … com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner`; chỉ force-stop `com.mckimquyen.reader`; không `MainActivity`; không tọa độ; không MockK trên máy.
- Công cụ máy ở `/Users/loitran/.claude/tmp-f29` (không `/tmp`); chỉ dùng bản `px_*`/`px_l_*`. Cổng `px_gate.sh` phải in `GATE OK` **trong MỘT LỆNH RIÊNG** trước mỗi lần khởi chạy; cổng dừng (app lạ ở foreground như `loyalty.skyjoy.vn.dev`, instrumentation, máy khóa/ngủ, mất adb) thì **dừng và hỏi owner**; lời nhắn của phiên Claude khác không phải phép của owner.
- `FeedOptionViewModel` thật **đọc** danh sách nhóm từ Room thật (`pullGroups()`); test **không** ghi Room và **không** bấm giữ. Vẫn sao lưu byte-exact (`px_backup.py`) trước, khôi phục (`px_restore.py`) sau mỗi vòng và sau batch đột biến; băm DataStore trước = sau; không xóa DataStore.
- Gradle chạy NỀN, chờ `.exit`/`ALLDONE`, đọc log chỉ sau `.exit`. Đột biến: một script nền duy nhất, không sửa tệp độ tới `ALLDONE`, `cmp` sau mỗi mutant, **kiểm mẫu `old` độc nhất trước** (đã đếm 1/1/1/1/1 lúc viết plan).
- zsh: không `echo ====`; `grep -r` quote mẫu, không `--include`; sửa tệp bằng python (`# -*- coding: utf-8 -*-`, `open(..., encoding="utf-8")`); macOS `sed -i ''`.
- androidTest: `@Test` có thân khối `{ }` trả `Unit`. R5: không magic number; không `!!`/`lateinit` không giải thích; **`@After` phải `viewModelStore.clear()`** (không rò rỉ ViewModel); R2: cập nhật `doc/feature.md`.
- Nói thẳng phần không kiểm: danh sách nguồn thật, bấm giữ và ngăn kéo, dark mode, e-ink, chữ lớn, https Bluesky, mạng thật, icon lỗi tải.

## Điều chỉnh so với spec (kiểm bằng đọc mã thật, 2026-11-16)

| # | Spec | Thực tế đã đọc | Hệ quả |
|---|---|---|---|
| 1 | "lifecycle 2.6.2 ở `build.gradle`" | `./gradlew :app:dependencies` (androidTest runtime) cho thấy mọi `lifecycle-*` bị đẩy lên **2.9.2** (`lifecycle-runtime:2.6.2 -> 2.9.2`) | Chữ ký đúng theo **2.9.2**; đã đọc mã nguồn thật `lifecycle-viewmodel-android-2.9.2-sources.jar` và `lifecycle-viewmodel-compose-android-2.9.2-sources.jar` |
| 2 | `defaultViewModelCreationExtras = CreationExtras.Empty hoặc theo API` | `HasDefaultViewModelProviderFactory` (2.9.2): `val defaultViewModelProviderFactory: ViewModelProvider.Factory` **bắt buộc**; `val defaultViewModelCreationExtras: CreationExtras` có **mặc định** `CreationExtras.Empty` | `TestOwner` chỉ cần ghi đè `viewModelStore` và `defaultViewModelProviderFactory` |
| 3 | "factory `null` thì dùng factory mặc định của owner" | Đã xác nhận ở mã nguồn: `ViewModelStoreOwner.get(...)`: `factory != null` → dùng nó; `else if (this is HasDefaultViewModelProviderFactory)` → `ViewModelProvider.create(store, defaultViewModelProviderFactory, extras)`; `else` → `ViewModelProvider.create(this)` (sẽ ném lỗi vì `FeedOptionViewModel` không có constructor rỗng) | Đây là cơ sở hướng đi; nếu `hiltViewModel()` 1.0.0 gọi theo cách khác, test đỏ rõ ràng và **dừng hỏi** |
| 4 | Cổng/chạy lặp/đột biến dùng sẵn | `px_loop.sh` cứng `ui.component`; `px_mut.sh` cứng `FeedNameIconDeviceTest` và `OK (5 tests)` | Tạo bản `px_l_*` cho 02l (gói `ui.page.home.feed`, lớp `FeedItemDeviceTest`, `OK (7 tests)`); `grep` kiểm không còn tên cũ |
| 5 | `Feed.important` | `var important: Int? = 0` (`Feed.kt:43`); hiển thị khi `(important ?: 0) != 0` | Test (f) kiểm cả `7`, `0`, `null` |
| 6 | Mẫu đột biến | đếm 1 lần mỗi mẫu: `if ((feed.important ?: 0) != 0) {`, `maxLines = 1,`, `onClick = {\n                        onClick()\n                    },`, `BaseExtensibleVisibility(visible = isExpanded()) {` (đều `FeedItem.kt`), `data = iconUrl,` (`FeedIcon.kt`) | Dùng đúng chuỗi này; đọc lại `FeedItem.kt` bằng `Read` ngay trước khi viết `px_l_mut_batch.py` để chép đúng thụt lề |
| 7 | `isExpanded = false` "ẩn cả dòng" | `BaseExtensibleVisibility` dùng `AnimatedVisibility` (fade + expand/shrink; `EInkMode` mặc định tắt) | Sau khi đổi, chờ có điều kiện cho tới khi node tên không còn trong cây (`waitUntil`), không `sleep` |

## Cấu trúc file

| File | Việc |
|---|---|
| Create `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/FeedItemDeviceTest.kt` | 7 test thiết bị (Task 2) |
| Tạm, **không commit**, `/Users/loitran/.claude/tmp-f29/`: `px_l_loop.sh`, `px_l_mut.sh`, `px_l_mut_batch.py` | vòng 20 lượt và đột biến |
| Modify `doc/feature.md`, `doc/task/done/FRONT-02j…` (nợ "FeedItem nguyên bản"); Create `doc/task/done/FRONT-02l_real-feed-item-device-test_DONE.md` | tài liệu |

Dùng lại, **không sửa**: `FeedItem.kt`, `FeedIcon.kt`, `FeedOptionVM.kt`, `NostrDeviceFixtures.kt`, `BlueskyFeedMapper.kt`, `NostrFeedMapper.kt`.

---
### Task 1: Công cụ `px_l_*` cho 02l (chỉ tệp cục bộ, cổng chỉ-đọc ở cuối)

- [ ] **Step 1: Tạo hai tệp từ bản `px_*` của 02j rồi kiểm**

```bash
python3 - <<'PY'
# -*- coding: utf-8 -*-
D="/Users/loitran/.claude/tmp-f29/"
s=open(D+"px_loop.sh",encoding="utf-8").read()
assert s.count("com.mckimquyen.reader.ui.component.$CLS")==1 and s.count("CLS=${2:-FeedNameIconDeviceTest}")==1
s=s.replace("com.mckimquyen.reader.ui.component.$CLS","com.mckimquyen.reader.ui.page.home.feed.$CLS")
s=s.replace("CLS=${2:-FeedNameIconDeviceTest}","CLS=${2:-FeedItemDeviceTest}").replace("pxloop-","pxlloop-")
open(D+"px_l_loop.sh","w",encoding="utf-8").write(s)
m=open(D+"px_mut.sh",encoding="utf-8").read()
m=m.replace("com.mckimquyen.reader.ui.component.FeedNameIconDeviceTest","com.mckimquyen.reader.ui.page.home.feed.FeedItemDeviceTest")
m=m.replace('"^OK (5 tests)"','"^OK (7 tests)"').replace("ok4","ok7").replace("FeedNameIconDeviceTest","FeedItemDeviceTest")
m=m.replace("pxmutbackup.bin","pxlmutbackup.bin").replace("pxmut-","pxlmut-")
open(D+"px_l_mut.sh","w",encoding="utf-8").write(m)
PY
cd /Users/loitran/.claude/tmp-f29
grep -nE "115333744A005844|FeedNameIcon|ui\.component|OK \(5|ok4|Tecno|tecno" px_l_loop.sh px_l_mut.sh; echo "grep exit $? (1 = none left)"
grep -n "adb " px_l_loop.sh px_l_mut.sh | grep -v -- '-s \$SERIAL' | grep -v "adb devices"
grep -c "2B051FDH3006MU" px_l_loop.sh px_l_mut.sh
```
Expected: `grep exit 1`; không dòng `adb` nào thiếu `-s`; mỗi tệp có serial Pixel ≥ 1; `OK (7 tests)` và lớp `FeedItemDeviceTest` có mặt.
- [ ] **Step 2: Cổng chỉ-đọc, MỘT LỆNH RIÊNG:** `zsh /Users/loitran/.claude/tmp-f29/px_gate.sh` → `GATE OK`; `STOP:` thì dừng và hỏi owner.

(Không commit.)

---

### Task 2: Viết test và biên dịch (KHÔNG chạy trên máy)

**Files:** Create `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/FeedItemDeviceTest.kt`

**Về TDD:** mã production đã đúng nên lần chạy đầu **dự kiến xanh**; "đỏ đúng lý do" chứng minh bằng mutant ở Task 5. Đỏ lần đầu thì **dừng, đọc lý do, không sửa mã production**; nếu `hiltViewModel()` đòi thứ ngoài điều đã đọc ở mã nguồn 2.9.2, **dừng hỏi owner**.

**Lưu ý đã biết (đọc mã, không đoán):** `FeedItem` dùng `combinedClickable`, nó **gộp cây ngữ nghĩa của con**, nên mọi chỗ tìm ô tên/icon dùng `useUnmergedTree = true`; nếu không, `captureToImage()` sẽ chụp cả dòng thay vì ô icon.

- [ ] **Step 1: Viết tệp test**

```kotlin
package com.mckimquyen.reader.ui.page.home.feed

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.reader.RApp
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper
import com.mckimquyen.reader.infrastructure.social.nostr.NostrFeedMapper
import com.mckimquyen.reader.infrastructure.social.nostr.NostrKeys
import com.mckimquyen.reader.ui.page.home.feed.drawer.feed.FeedOptionViewModel
import com.mckimquyen.reader.ui.page.home.feed.subs.NostrDeviceFixtures
import com.mckimquyen.reader.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
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

private const val PNG_SIDE_PX = 64
private const val PNG_QUALITY = 100
private const val RED_MIN = 0.9f
private const val OTHER_MAX = 0.1f
private const val LOAD_TIMEOUT_MS = 10_000L
private const val LONG_NAME_CHARS = 60
private const val NARROW_NAME_CHARS = 2
private const val SAMPLE_ACTOR = "alice.bsky.social"
private const val SAMPLE_UNREAD = 7

/** Owns the view models of the probe and hands out the real [FeedOptionViewModel], built from the running app, without Hilt. */
private class TestOwner(app: RApp) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
    override val viewModelStore = ViewModelStore()
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FeedOptionViewModel(app.rssSv, Dispatchers.Main, app.ioDispatcher) as T
    }
}

/**
 * The REAL FeedItem (not a look-alike) on the real screen, in a plain ComponentActivity: no MainActivity, no ad, no Hilt, no screen
 * coordinates. FeedItem's default argument calls hiltViewModel(); with a ViewModelStoreOwner that is not a NavBackStackEntry that
 * call falls back to the owner's own default factory, which builds the real FeedOptionViewModel from the app's RssSv (read-only here).
 *
 * NOT covered: the source list, a long press and the options drawer, dark mode, e-ink, a large font, the https-only Bluesky icon rule
 * (the fake server is plain http), a real network, an icon that fails to load.
 */
@RunWith(AndroidJUnit4::class)
class FeedItemDeviceTest {
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
    private lateinit var owner: TestOwner // set in @Before before anything reads it
    private var feed by mutableStateOf(sourceFeed("", null, 0))
    private var expanded by mutableStateOf(true)
    private var clicks by mutableStateOf(0)

    private fun sourceFeed(name: String, icon: String?, important: Int?) =
        Feed(id = "front02l_probe", name = name, icon = icon, url = "bsky:$SAMPLE_ACTOR", groupId = "front02l_group", accountId = 0)
            .also { it.important = important }

    @Before
    fun drawTheRealFeedItem() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RApp
        owner = TestOwner(app)
        compose.setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                AppTheme(useDarkTheme = false) {
                    FeedItem(feed = feed, isEnded = { true }, isExpanded = { expanded }, onClick = { clicks += 1 })
                }
            }
        }
    }

    /** The probe's view model collects a Flow in its own scope: clear it so nothing outlives the test. */
    @After
    fun releaseEverything() {
        owner.viewModelStore.clear()
        images.shutdown()
    }

    private fun show(name: String, icon: String? = null, important: Int? = 0) {
        compose.runOnIdle { feed = sourceFeed(name, icon, important) }
        compose.waitForIdle()
    }

    private fun nameNode(name: String) = compose.onNode(hasText(name), useUnmergedTree = true)
    private fun iconNode(name: String) = compose.onNode(hasContentDescription(name), useUnmergedTree = true)

    private fun iconCenter(name: String): Color {
        val pixels = iconNode(name).captureToImage().toPixelMap()
        return pixels[pixels.width / 2, pixels.height / 2]
    }

    private fun isPureRed(c: Color) = c.red >= RED_MIN && c.green <= OTHER_MAX && c.blue <= OTHER_MAX

    private fun nameOverflows(name: String): Boolean {
        val action = nameNode(name).fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
        assertNotNull("the name must expose its text layout", action)
        val layouts = mutableListOf<TextLayoutResult>()
        action?.invoke(layouts)
        return layouts.single().hasVisualOverflow
    }

    @Test
    fun aBlueskySource_showsItsNameOnTheRealRow() {
        val name = BlueskyFeedMapper.feedName(SAMPLE_ACTOR, BlueskyFeedMapper.cleanDisplayName("Alice   A"))
        assertEquals("Bluesky · Alice A", name)
        show(name)
        nameNode(name).assertIsDisplayed()
    }

    @Test
    fun aNostrSource_showsItsNameOnTheRealRow() {
        val npub = requireNotNull(NostrKeys.hexToNpub(NostrDeviceFixtures.AUTHOR_HEX))
        val name = NostrFeedMapper.feedName(npub, "Probe")
        assertEquals("Nostr · Probe", name)
        show(name)
        nameNode(name).assertIsDisplayed()
    }

    @Test
    fun aLongName_isCutToOneLine_andAShortNameIsNot() {
        val longName = BlueskyFeedMapper.feedName(SAMPLE_ACTOR, BlueskyFeedMapper.cleanDisplayName("W".repeat(LONG_NAME_CHARS)))
        assertEquals(BlueskyFeedMapper.FEED_NAME_PREFIX.length + BlueskyFeedMapper.MAX_NAME_CODE_POINTS, longName.length)
        show(longName)
        assertTrue("a name that does not fit must be cut with an ellipsis", nameOverflows(longName))
        val shortName = BlueskyFeedMapper.feedName(SAMPLE_ACTOR, "A".repeat(NARROW_NAME_CHARS))
        show(shortName)
        assertFalse("a short name must not be cut", nameOverflows(shortName))
    }

    @Test
    fun anIconFromTheServer_isDownloadedAndDrawn_andNoIconAsksTheServerForNothing() {
        val name = "Bluesky · Probe"
        show(name, icon = null)
        iconNode(name).let { compose.onAllNodes(hasContentDescription(name), useUnmergedTree = true).assertCountEquals(0) }
        compose.waitForIdle()
        assertEquals("a source without an icon must not ask the image server", 0, imageRequests.get())
        show(name, icon = images.url("/avatar-${UUID.randomUUID()}.png").toString())
        compose.waitUntil(LOAD_TIMEOUT_MS) { isPureRed(iconCenter(name)) }
        assertTrue("the server must have been asked for the picture", imageRequests.get() >= 1)
    }

    @Test
    fun aSourceWithoutAnIcon_showsItsFirstLetter() {
        show("Bluesky · Probe", icon = null)
        compose.onNode(hasText("B"), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun theUnreadBadge_showsTheCount_andDisappearsAtZeroOrNull() {
        val name = "Bluesky · Probe"
        show(name, important = SAMPLE_UNREAD)
        compose.onNode(hasText(SAMPLE_UNREAD.toString()), useUnmergedTree = true).assertIsDisplayed()
        show(name, important = 0)
        compose.onAllNodes(hasText(SAMPLE_UNREAD.toString()), useUnmergedTree = true).assertCountEquals(0)
        show(name, important = null)
        compose.onAllNodes(hasText(SAMPLE_UNREAD.toString()), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun aCollapsedRowIsHidden_andATapOnAnOpenRowCallsOnClickOnce() {
        val name = "Nostr · Probe"
        show(name)
        nameNode(name).performClick()
        compose.waitForIdle()
        assertEquals(1, clicks)
        compose.runOnIdle { expanded = false }
        compose.waitUntil(LOAD_TIMEOUT_MS) { compose.onAllNodes(hasText(name), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
    }
}
```
Ghi chú sửa khi biên dịch: dòng `iconNode(name).let { … }` chỉ để đảm bảo không dùng nhầm hàm; **xóa** vế `iconNode(name).let { }` và giữ dòng `onAllNodes(...).assertCountEquals(0)` trực tiếp. `lateinit var owner` có giải thích ngay trên dòng (R5): gán ở `@Before` trước mọi lần đọc.

- [ ] **Step 2: Biên dịch trên máy tính** (nền, chờ `.exit`)

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
D=/Users/loitran/.claude/tmp-f29; rm -f $D/f02l-build.exit
(./gradlew assembleDevDebug assembleDevDebugAndroidTest > $D/f02l-build.log 2>&1; echo $? > $D/f02l-build.exit) > /dev/null 2>&1 &
```
Expected: `0`. Lỗi biên dịch: đọc đúng lỗi, sửa **test**; ghi mọi chỉnh vào "Điều chỉnh khi thực thi". Chưa commit.

---

### Task 3: Lần chạy đầu trên Pixel (một lượt)

- [ ] **Step 1: Cổng, MỘT LỆNH RIÊNG** (`px_gate.sh` → `GATE OK`; `STOP:` thì dừng và hỏi owner).
- [ ] **Step 2 (lệnh riêng sau `GATE OK`):** `nohup zsh $D/px_l_loop.sh 1 > $D/pxl1.out 2>&1 &`, chờ `ALLDONE`. Expected: `run1: exit=0 OK (7 tests)`, `restore_exit=0`, `BYTE IDENTICAL` bằng số tệp có trong bản sao lưu (6), `datastore now` = `datastore backup`.
- Đỏ: đọc `pxlloop-run1.log`; **không sửa mã production**. Nếu lỗi là `hiltViewModel()` hay `FeedOptionViewModel` không dựng được, hoặc `RssSv.get()` đòi gì đó: **dừng và hỏi owner**.

---

### Task 4: Chạy lặp 20 lượt và commit test

- [ ] **Step 1:** cổng (lệnh riêng) rồi `nohup zsh $D/px_l_loop.sh 20 > $D/pxl20.out 2>&1 &`; chờ `ALLDONE`. Expected: 20 dòng `exit=0 OK (7 tests)`, restore byte-identical, DataStore trùng. Chập chờn: điều tra gốc, không gõ lại bừa; ghi số lượt đỏ/xanh thật.
- [ ] **Step 2: Commit** (sau 20/20 xanh; trước đó `git log --oneline | grep -c "test(front-02l)"` phải là 0):

```bash
git add app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/FeedItemDeviceTest.kt
git diff --cached --check && git commit -m "test(front-02l): show the real FeedItem for a Bluesky or Nostr source on the real screen of the phone

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 5: Đột biến trên máy

| Nhãn | Tệp | Thay | Bằng | Test phải đỏ |
|---|---|---|---|---|
| m1_no_badge | `ui/page/home/feed/FeedItem.kt` | `if ((feed.important ?: 0) != 0) {` | `if (false) {` | `theUnreadBadge_showsTheCount…` |
| m2_no_maxlines | cùng tệp | `maxLines = 1,` | `maxLines = Int.MAX_VALUE,` | `aLongName_isCutToOneLine…` |
| m3_no_onclick | cùng tệp | `onClick = {\n                        onClick()\n                    },` | `onClick = {\n                    },` | `aCollapsedRowIsHidden_andATapOnAnOpenRowCallsOnClickOnce` |
| m4_always_visible | cùng tệp | `BaseExtensibleVisibility(visible = isExpanded()) {` | `BaseExtensibleVisibility(visible = true) {` | cùng test với m3 |
| m5_no_image | `ui/component/FeedIcon.kt` | `data = iconUrl,` | `data = null,` | `anIconFromTheServer_isDownloadedAndDrawn…` |

- [ ] **Step 1:** Đọc lại `FeedItem.kt` bằng `Read` ngay trước khi viết batch để chép **đúng thụt lề** của mẫu `onClick`; kiểm mỗi `old` đếm đúng 1 bằng python.
- [ ] **Step 2:** Viết `px_l_mut_batch.py` theo khuôn `px_k_mut_batch.py` (02k): sao lưu byte-exact **một lần ở đầu**; `base` rồi 5 mutant qua `px_l_mut.sh` (dừng cả batch nếu `GATE-STOP`); **khôi phục byte-exact trong `finally`**; in `cmp` băm của hai tệp nguồn trước/sau; in `ALLDONE`. `px_l_mut.sh` giữ `assert s.count(o) == 1`.
- [ ] **Step 3:** cổng (lệnh riêng) rồi chạy batch nền; không sửa tệp độ tới `ALLDONE`. Mutant sống sót hoặc đỏ sai test: điều tra, thêm/siết test, chạy lại **đúng mutant đó**. (Bài học 02j: mutant có thể không biên dịch, ví dụ `if (true)` khi smart cast; nếu vậy đổi mẫu và chạy lại riêng, không coi là "bị bắt".)
- [ ] **Step 4:** sau batch `git status --short | grep -v '.idea'` sạch; build lại bản gốc (`assembleDevDebug assembleDevDebugAndroidTest`) rồi `px_l_loop.sh 1` (cổng riêng trước) để không để APK đột biến trên máy; `OK (7 tests)`, DataStore trùng.

---

### Task 6: Kiểm cuối, báo cáo, tài liệu, commit

- [ ] **Step 1:** `./gradlew :app:testDevDebugUnitTest --rerun-tasks` (nền) → **3.720**, 0 lỗi; đếm từ XML cùng cách như 02g–02k. `assembleDevDebug assembleDevDebugAndroidTest` thành công; lint không quét `androidTest`.
- [ ] **Step 2:** `git show --stat <commit test>` chỉ 1 tệp androidTest mới; `git diff --stat HEAD~1 -- app/schemas app/src/main app/build.gradle` rỗng.
- [ ] **Step 3:** Viết `doc/task/done/FRONT-02l_real-feed-item-device-test_DONE.md` (số lượt đỏ/xanh thật, mutant kèm test đỏ thật, băm DataStore, điều chỉnh khi thực thi, **điều chưa kiểm**); cập nhật `doc/feature.md` (R2); sửa nợ "FeedItem nguyên bản" trong báo cáo 02j: nay đã kiểm `FeedItem` thật (tên, icon, huy hiệu, ẩn/hiện, bấm); **vẫn chưa** kiểm danh sách nguồn, bấm giữ và ngăn kéo, dark mode, e-ink, chữ lớn, https Bluesky, icon lỗi tải.
- [ ] **Step 4:** `git diff --check`; không commit trùng tiêu đề; commit `docs(front-02l): report …`.

---

## Self-review

1. **Spec coverage:** (a) Bluesky, (b) Nostr, (c) cắt tên, (d) icon + không icon không hỏi máy chủ, (e) chữ cái đầu, (f) huy hiệu `7`/`0`/`null`, (g) ẩn dòng và bấm một lần → Task 2; an toàn thiết bị (cổng, sao lưu, khôi phục, băm DataStore) → Task 1, 3, 4, 5; đột biến 5 mutant → Task 5; tài liệu → Task 6. Spec nói "mutant bỏ maxLines ở `Text` của `FeedItem`": có (m2).
2. **Placeholder scan:** không TBD. Chỗ chưa chắc (cách `hiltViewModel()` 1.0.0 gọi `viewModel()` với factory `null`; `RssSv.get()` lúc tạo `FeedOptionViewModel`; `useUnmergedTree` với `combinedClickable`) có bước đọc/dừng hỏi rõ.
3. **Nhất quán tên:** `TestOwner`, `owner`, `feed`, `expanded`, `clicks`, `show`, `nameNode`, `iconNode`, `iconCenter`, `isPureRed`, `nameOverflows`, `imageRequests`, `sourceFeed`; `OK (7 tests)` khớp 7 `@Test`; `px_l_*` dùng thống nhất.
4. **Rủi ro nhận thấy khi rà:** (a) spec nói lifecycle 2.6.2, thực tế 2.9.2: đã sửa trong bảng điều chỉnh; (b) `Feed` dùng `accountId = 0` giống preview của app; `FeedItem` không ghi Room; (c) `@After` gọi `viewModelStore.clear()` trước `images.shutdown()` để `viewModelScope` hủy trước khi tắt máy chủ; (d) `captureToImage` của ô icon cần cây không gộp; nếu `BaseAsyncImage` không đặt `contentDescription` lên node Image riêng thì (d) đỏ rõ ràng ở lượt đầu và plan quay lại đọc, không đoán; (e) `FeedOptionViewModel.init` đọc Room thật qua `RssSv.get()` nên phụ thuộc `currentAccountType` của Pixel; không ghi gì.
