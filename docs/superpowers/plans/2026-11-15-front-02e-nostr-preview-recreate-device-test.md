# FRONT-02e — Test thiết bị cho bản xem trước Nostr khi Activity tạo lại hoặc xoay (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (hoặc subagent-driven-development) để thực thi plan này từng task. Các bước dùng checkbox `- [ ]`.

**Goal:** Chứng minh trên Pixel 7 Pro rằng bản xem trước Nostr (tên nguồn, danh sách ghi chú, ô nhập bị khóa) còn nguyên sau `recreate()`, sau xoay thật, và khi `recreate()` xảy ra lúc relay còn đang trả lời.

**Architecture:** Một `ComponentActivity` của test tự lấy `SubscribeViewModel` qua `ViewModelProvider(activity, factory)` (ViewModelStore thật). Factory dựng ViewModel từ các singleton thật của app (đọc từ field `@Inject` công khai của `RApp`, đang chạy trong tiến trình test) nhưng với một `NostrRelayClient` trỏ vào relay giả chạy trong tiến trình test (`MockWebServer` + WebSocket). Không đổi mã production; chỉ thêm một khai báo scope test cho thư viện đã có.

**Tech Stack:** Compose UI test 1.7.8, `createAndroidComposeRule<ComponentActivity>`, `ViewModelProvider`, `mockwebserver3-junit4:5.0.0-alpha.11` (chỉ androidTest), `UiAutomation.setRotation`, `am instrument`, adb, Python 3.9.

## Global Constraints

- Nhánh `dev`; không worktree, không push, không PR, không commit `.idea`.
- Chỉ thêm **một** dòng Gradle: `androidTestImplementation "com.squareup.okhttp3:mockwebserver3-junit4:5.0.0-alpha.11"` (owner đã cho phép; cùng phiên bản `testImplementation` ở `app/build.gradle:262`). Không thêm `kaptAndroidTest` (không cần: dùng field của `RApp`). Không đổi manifest, chuỗi, Room (v22), DataStore, mã production.
- Thiết bị **CHỈ Pixel 7 Pro `2B051FDH3006MU`**; `adb -s 2B051FDH3006MU`; không `connected*`; `am instrument --user 0 -w -r -e class … com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner`; force-stop chỉ `com.mckimquyen.reader`; **không MockK trên máy**; không tọa độ; **không gọi mạng thật** (relay chỉ là `ws://127.0.0.1`).
- **Phiên khác (lens-launcher) cũng dùng Pixel.** Trước mỗi lần chạm máy: cổng `/tmp/f28_gate.sh` phải qua (launcher `com.google.android.apps.nexuslauncher` ở trên cùng, đã mở khóa, không instrumentation). Nếu cổng dừng vì app khác ở foreground: **dừng, hỏi owner, không khởi chạy gì**. Gửi tin cho phiên kia (`SendMessage`) trước khi chạy vòng dài và báo khi trả máy.
- Sao lưu/khôi phục **byte-exact 6 tệp** (`/tmp/pixel-backup.py`, `/tmp/pixel-restore.py`); băm DataStore `ed9f68879fc775bb5fb1b5f24206a6c1a924bcee` phải trùng sau mỗi vòng; giữ `accelerometer_rotation=1`, `user_rotation=0`; không xóa DataStore.
- Gradle chạy NỀN, output ra file, chờ `.exit`/`ALLDONE`; đọc XML/log chỉ sau khi `.exit` tồn tại; **không** chấp nhận "completed" của lệnh nền làm kết quả.
- Mutation chạy trên máy bằng một script nền duy nhất, **không sửa tệp đột biến cho tới `ALLDONE`**, `cmp` sau mỗi đợt, `git diff app/src/main` phải rỗng.
- zsh: không `echo ====`; `grep -r` phải quote mẫu và không dùng `--include`; sửa tệp bằng python; Python 3.9 cần `# -*- coding: utf-8 -*-` và `open(..., encoding="utf-8")`.
- R5: không magic number (hằng số có tên); test bắt buộc kèm; không báo "done" khi chưa dọn. R2: cập nhật `doc/feature.md`.
- Nói thẳng phần không kiểm: `MainActivity`/`HomeEntry` thật với xem trước Nostr, xuống nền, process bị giết, đổi ngôn ngữ/chế độ tối thật, hai relay, mạng thật, lint không quét `androidTest`.
- **Rủi ro đã biết:** bước gõ chữ ở FRONT-02d chập chờn 2 đến 9% với gốc chưa rõ (giả thuyết "focus muộn" đã bị bác). Test này cũng gõ chữ; không được "gõ lại bừa". Nếu chập chờn tái xuất thì điều tra trước khi dùng làm bằng chứng.

## Điều chỉnh so với spec (phát hiện khi đọc code thật, 2026-11-15)

1. **Không cần `@EntryPoint`, không cần kapt cho androidTest:** `RApp` (`@HiltAndroidApp`, là `Application` của tiến trình test) đã có sẵn field `@Inject` công khai: `opmlService: OpmlSv`, `rssSv: RssSv`, `rssHelper: RssHelper`, `androidStringsHelper: AndroidStringsHelper`, `okHttpClient: OkHttpClient` (`RApp.kt:79-126`). Test đọc thẳng từ `context.applicationContext as RApp`. Hai phụ thuộc còn lại không dùng ở đường Nostr nên dựng tay: `WebFeedScraper(WebFeedHttpClient(), WebFeedParser())` (cả hai là `@Inject constructor()` rỗng) và `NewsletterUrlResolver()` (constructor phụ không đối số, dành cho test). Cách này tránh hẳn rủi ro "Hilt processor không chạy ở androidTest" mà bản spec đã dự đoán.
2. **`SubscribeViewModel` cần `RssSv`, và `RssSv.get()` trả `LocalRssSv` (23 tham số, trong đó có một `NostrRelayClient` THẬT với 2 relay thật).** `search()` gọi `rssService.get().isFeedExist(...)` rồi (tương lai) `subscribe`, nhưng **không** gọi `LocalRssSv.nostrRelayClient` (chỉ dùng cho đồng bộ nền). Vì test không bấm Subscribe và không sync nên không có lượt gọi relay thật nào; test phải khẳng định điều đó bằng cách chỉ nhận tin từ relay giả.
3. **`NostrTestEvents` nằm ở `src/test`**: androidTest không thấy. Sao chép một ghi chú + một hồ sơ đã ký vào tệp androidTest riêng; thêm kiểm "chữ ký còn hợp lệ" (`NostrEvent.parse(...).isValid(AUTHOR)`) trong test để một lần sao chép sai bị phát hiện, không để relay giả im lặng trả sự kiện hỏng.
4. **`SubscribeDialog` không đọc `CompositionLocal` nào ngoài `LocalContext`, `LocalFocusManager`, `LocalClipboardManager`** (đã grep; các `Local*` của `Settings` đều có giá trị mặc định). `SettingsProvider` không cần. Test JVM `SubscribeDialogTypingTest` cũng chỉ `setContent { SubscribeDialog(viewModel) }`. Plan dựng đúng như vậy.
5. **Cổng `/tmp/f28_loop.sh` và `/tmp/f28_dev_mut.sh` hard-code tên lớp test FRONT-02d.** Phải tham số hóa (Task 2) để chạy lớp mới mà không sửa mẫu cũ.
6. **`FeedsPage` không có trong Activity test**, nên animation vô hạn (`FeedsPage.kt:127`) **không** có; `mainClock.autoAdvance = false` có thể không cần và còn có hại (không tự tiến). Test dùng đồng hồ mặc định; chỉ chuyển sang thủ công nếu phép thử chứng minh Compose "bận".

## Cấu trúc file

| File | Việc |
|---|---|
| Modify `app/build.gradle` (một dòng) | `androidTestImplementation "com.squareup.okhttp3:mockwebserver3-junit4:5.0.0-alpha.11"` |
| Create `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/NostrDeviceFixtures.kt` | hằng số `AUTHOR_HEX`, `NOTE_JSON`, `PROFILE_JSON` (sao chép từ `NostrTestEvents`), `FAKE_PROFILE_NAME` |
| Create `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/NostrFakeRelay.kt` | `MockWebServer` WebSocket: trả `EVENT`, `EVENT`, `EOSE`; tuỳ chọn giữ phản hồi bằng cổng |
| Create `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModelFactory.kt` | hàm `subscribeViewModelFactory(relayUrl)`: dựng `SubscribeViewModel` thật từ field của `RApp`, relay trỏ vào relay giả |
| Create `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeNostrPreviewRecreateDeviceTest.kt` | 3 test thiết bị |
| Tạm, **không commit**: `/tmp/f29_gate.sh` (= `f28_gate.sh`), `/tmp/f29_loop.sh`, `/tmp/f29_dev_mut.sh`, `/tmp/f29_mut_batch.sh` | cổng, vòng lặp, đột biến chạy trên máy (tham số hoá lớp test) |
| Modify `doc/feature.md`, spec (mục "Điều chỉnh khi thực thi") | tài liệu |
| Create `doc/task/done/FRONT-02e_nostr-preview-recreate-device-test_DONE.md` | báo cáo |

---

### Task 1: Phép thử đọc `RApp` từ androidTest (kiểm trên máy tính, chưa chạm Pixel)

**Mục đích:** xác nhận bằng biên dịch rằng androidTest đọc được các field `@Inject` của `RApp` và constructor `internal` của `NostrRelayClient`, trước khi viết test.

**Files:**
- Create: `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModelFactory.kt`

**Interfaces:**
- Produces: `fun subscribeViewModelFactory(relayUrl: String): ViewModelProvider.Factory`

- [ ] **Step 1: Đọc lại API thật**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
J=app/src/main/java/com/mckimquyen/reader
sed -n 76,128p $J/RApp.kt | grep -E "lateinit var (opmlService|rssSv|rssHelper|androidStringsHelper|okHttpClient)"
grep -n "internal constructor" -A6 $J/infrastructure/social/nostr/NostrRelayClient.kt | head -8
grep -n "class WebFeedHttpClient\|class WebFeedParser" -r $J | cut -c1-120
grep -n "constructor()" $J/infrastructure/newsletter/NewsletterUrlResolver.kt
```
Expected: năm field `lateinit var` công khai; constructor `internal` nhận `(OkHttpClient, CoroutineDispatcher, List<String>, Boolean, Long)`; `WebFeedHttpClient` và `WebFeedParser` có `@Inject constructor()` rỗng; `NewsletterUrlResolver` có `constructor()`. Nếu khác: dừng, sửa plan.

- [ ] **Step 2: Viết factory**

```kotlin
package com.mckimquyen.reader.ui.page.home.feed.subs

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.reader.RApp
import com.mckimquyen.reader.infrastructure.newsletter.NewsletterUrlResolver
import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedHttpClient
import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedParser
import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedScraper
import com.mckimquyen.reader.infrastructure.social.nostr.NostrRelayClient
import kotlinx.coroutines.Dispatchers

private const val FAKE_RELAY_TIMEOUT_MS = 10_000L

/**
 * Builds the real SubscribeViewModel from the app's real singletons (read from the running RApp, there is no Hilt test
 * runner here), but reads Nostr from the fake relay at [relayUrl]. The web/newsletter helpers are never used by the Nostr path.
 */
fun subscribeViewModelFactory(relayUrl: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val app = context as RApp
        return SubscribeViewModel(
            context = context,
            opmlService = app.opmlService,
            rssService = app.rssSv,
            rssHelper = app.rssHelper,
            androidStringsHelper = app.androidStringsHelper,
            webFeedScraper = WebFeedScraper(WebFeedHttpClient(), WebFeedParser()),
            newsletterUrlResolver = NewsletterUrlResolver(),
            nostrRelayClient = NostrRelayClient(app.okHttpClient, Dispatchers.IO, listOf(relayUrl), false, FAKE_RELAY_TIMEOUT_MS),
        ) as T
    }
}
```
`@Suppress("UNCHECKED_CAST")` giữ vì `create` là API generic của Android và cast là chắc chắn đúng (`modelClass` là `SubscribeViewModel`). Nếu `targetContext.applicationContext` không phải `RApp` (ví dụ test chạy với Application khác), phép `as RApp` ném lỗi rõ ràng: đó là điều mong muốn, không bắt.

- [ ] **Step 3: Biên dịch (nền, output ra file)**

```bash
rm -f /tmp/f29-build.exit
( ./gradlew assembleDevDebugAndroidTest > /tmp/f29-build.txt 2>&1; echo $? > /tmp/f29-build.exit ) > /dev/null 2>&1 &
```
Chờ `/tmp/f29-build.exit`; `cat /tmp/f29-build.exit`; `grep -n "^e: " /tmp/f29-build.txt | head`.
Expected: `0`. Nếu `RApp.opmlService` hay constructor `internal` không truy cập được: **dừng**, báo, không đổi mã production, hỏi owner.

- [ ] **Step 4: Không commit ở đây** (commit cùng Task 4).

---

### Task 2: Dependency test, fixtures, relay giả, factory (chưa chạy máy)

**Files:**
- Modify: `app/build.gradle` (thêm một dòng ngay sau dòng `androidTestImplementation "androidx.test.ext:junit:1.2.1"`, dòng 270)
- Create: `NostrDeviceFixtures.kt`, `NostrFakeRelay.kt` (factory đã có từ Task 1)

**Interfaces:**
- Produces: `object NostrDeviceFixtures { const val AUTHOR_HEX: String; const val NOTE_JSON: String; const val PROFILE_JSON: String; const val PROFILE_NAME: String }`; `class NostrFakeRelay : AutoCloseable { val url: String; fun holdAnswerUntil(open: CompletableDeferred<Unit>); fun requestsReceived(): Int; override fun close() }`; `subscribeViewModelFactory` (đã tạo ở Task 1).

- [ ] **Step 1: Thêm dependency (một dòng)**

Sửa `app/build.gradle` bằng python (không sed):

```python
# -*- coding: utf-8 -*-
p = 'app/build.gradle'
s = open(p, encoding='utf-8').read()
old = '    androidTestImplementation "androidx.test.ext:junit:1.2.1"\n'
assert s.count(old) == 1
new = old + '    // Fake Nostr relay for device tests; same version as testImplementation above to avoid an okhttp clash.\n    androidTestImplementation "com.squareup.okhttp3:mockwebserver3-junit4:5.0.0-alpha.11"\n'
open(p, 'w', encoding='utf-8').write(s.replace(old, new, 1))
```

- [ ] **Step 2: Fixtures (đọc nguyên văn từ nguồn, không gõ tay)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
python3 - <<'PYEOF'
# -*- coding: utf-8 -*-
import re
src = open('app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrTestEvents.kt', encoding='utf-8').read()
def const(name):
    m = re.search(r'const val ' + name + r' = """(.*?)"""', src, re.S)
    assert m, name
    return m.group(1)
def simple(name):
    m = re.search(r'const val ' + name + r' = "([^"]*)"', src)
    assert m, name
    return m.group(1)
author = simple('AUTHOR'); note = const('PLAIN'); profile = const('PROFILE')
out = f'''package com.mckimquyen.reader.ui.page.home.feed.subs

/** One signed note and one signed profile of the same author, copied from the JVM fixtures (src/test is not visible here). */
object NostrDeviceFixtures {{
    const val AUTHOR_HEX = "{author}"
    const val NOTE_JSON = """{note}"""
    const val PROFILE_JSON = """{profile}"""
}}
'''
open('app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/NostrDeviceFixtures.kt', 'w', encoding='utf-8').write(out)
PYEOF
grep -n "PROFILE_NAME\|Alice" app/src/test/java/com/mckimquyen/reader/infrastructure/social/nostr/NostrTestEvents.kt | head -3
```
Expected: tệp được tạo. `NostrProfileName` của `PROFILE` là `Alice Liddell` (đã dùng trong `NostrRelayProfileTest`). Thêm vào object (sau khi đọc lại `NostrTestEvents`/`NostrRelayProfileTest` cho chắc): `const val PROFILE_NAME = "Alice Liddell"`. **Kiểm** bằng test Task 3 (`NostrEvent.parse(...).isValid(AUTHOR_HEX)` và `NostrProfileName.fromContent(...)`), không tin sao chép.

- [ ] **Step 3: Relay giả** (`NostrFakeRelay.kt`)

```kotlin
package com.mckimquyen.reader.ui.page.home.feed.subs

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicInteger

/** Plays the part of a Nostr relay on 127.0.0.1: answers one REQ with the note, the profile, then EOSE. Never touches the network. */
class NostrFakeRelay : AutoCloseable {
    private val server = MockWebServer().apply { start() }
    private val requests = AtomicInteger(0)
    @Volatile private var gate: CompletableDeferred<Unit>? = null
    val url: String = server.url("/").toString().replaceFirst("http://", "ws://")

    /** Holds the answer until [open] is completed, so a test can interrupt the Activity while the relay is still silent. */
    fun holdAnswerUntil(open: CompletableDeferred<Unit>) { gate = open }

    fun requestsReceived(): Int = requests.get()

    init {
        server.enqueue(socketResponse())
    }

    private fun socketResponse() = MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            if (!text.startsWith("[\"REQ\"")) return
            requests.incrementAndGet()
            gate?.let { runBlocking { it.await() } }
            webSocket.send("[\"EVENT\",\"$SUBSCRIPTION_ID\",${NostrDeviceFixtures.NOTE_JSON}]")
            webSocket.send("[\"EVENT\",\"$SUBSCRIPTION_ID\",${NostrDeviceFixtures.PROFILE_JSON}]")
            webSocket.send("[\"EOSE\",\"$SUBSCRIPTION_ID\"]")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onOpen(webSocket: WebSocket, response: Response) = Unit
    })

    override fun close() = server.shutdown()

    private companion object {
        const val SUBSCRIPTION_ID = "f2"
    }
}
```

- [ ] **Step 4: Biên dịch (nền)** như Task 1 Step 3. Expected: exit 0.

- [ ] **Step 5: Không commit ở đây.**

---

### Task 3: Viết 3 test thiết bị và biên dịch

**Files:**
- Create: `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeNostrPreviewRecreateDeviceTest.kt`

**Interfaces:**
- Consumes: `subscribeViewModelFactory(relayUrl)` (Task 1), `NostrDeviceFixtures` và `NostrFakeRelay` (Task 2), `SubscribeDialog(subscribeViewModel)`, `SubscribeViewModel.showDrawer()/subscribeUiState`, `NostrKeys.hexToNpub`, `NostrFeedMapper.FEED_NAME_PREFIX`, `NostrProfileName.fromContent`, `NostrEvent.parse/isValid/isValidProfile`.
- Produces: lớp `SubscribeNostrPreviewRecreateDeviceTest` với 4 test: `fixtures_areSignedAndNameTheProfile` (kiểm chính các hằng số sao chép), `previewSurvivesRecreate`, `previewSurvivesRealRotation`, `recreateWhileTheRelayIsStillAnswering_stillShowsThePreview`.

- [ ] **Step 1: Đọc lại API thật (một lần cuối trước khi chép)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
J=app/src/main/java/com/mckimquyen/reader
grep -n "FEED_NAME_PREFIX" $J/infrastructure/social/nostr/NostrFeedMapper.kt | head -2
grep -n "fun fromContent\|fun parse\|fun isValid\|fun isValidProfile\|fun hexToNpub" $J/infrastructure/social/nostr/*.kt
grep -n "fun showDrawer\|fun hideDrawer\|val subscribeUiState" $J/ui/page/home/feed/subs/SubscribeViewModel.kt
grep -n 'name="search"\|name="feed_or_site_url"' app/src/main/res/values/strings.xml
```
Expected: `FEED_NAME_PREFIX = "Nostr · "`; các hàm có chữ ký đúng như trên; `search`=`Search`, `feed_or_site_url`=`Feed or Site URL`. Chuỗi lấy từ `activity.getString(...)`, không gõ cứng, để đúng với ngôn ngữ app.

- [ ] **Step 2: Viết tệp test**

```kotlin
package com.mckimquyen.reader.ui.page.home.feed.subs

import android.app.UiAutomation
import android.content.res.Configuration
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.infrastructure.social.nostr.NostrEvent
import com.mckimquyen.reader.infrastructure.social.nostr.NostrFeedMapper
import com.mckimquyen.reader.infrastructure.social.nostr.NostrKeys
import com.mckimquyen.reader.infrastructure.social.nostr.NostrProfileName
import com.mckimquyen.reader.ui.ext.currentAccountType
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What survives, for the Nostr preview of the Add Source dialog, when its Activity is recreated or the screen is rotated.
 * The view model comes from a real ViewModelStore (ViewModelProvider on the test Activity), built from the app's real
 * services but reading a fake relay on 127.0.0.1; nothing here reaches the network.
 *
 * NOT covered: MainActivity/HomeEntry and hiltViewModel() scoping (FRONT-02d covers typed text there), the app going to the
 * background, the process being killed, a real language or dark-mode change, two relays, a real relay.
 */
@RunWith(AndroidJUnit4::class)
class SubscribeNostrPreviewRecreateDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val relay = NostrFakeRelay()
    private val automation: UiAutomation get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    private var initialAutoRotate = AUTO_ROTATE_ON
    private var shown: SubscribeViewModel? = null
    private val npub: String = requireNotNull(NostrKeys.hexToNpub(NostrDeviceFixtures.AUTHOR_HEX))

    @Before
    fun rememberAutoRotate() {
        initialAutoRotate = Settings.System.getInt(
            InstrumentationRegistry.getInstrumentation().targetContext.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            AUTO_ROTATE_ON,
        )
    }

    /** Gives the phone back the rotation setting the owner had, and stops the fake relay. */
    @After
    fun cleanUp() {
        automation.setRotation(
            if (initialAutoRotate == AUTO_ROTATE_ON) UiAutomation.ROTATION_UNFREEZE else UiAutomation.ROTATION_FREEZE_0,
        )
        relay.close()
    }

    private val placeholder get() = compose.activity.getString(R.string.feed_or_site_url)
    private val searchLabel get() = compose.activity.getString(R.string.search)
    private val expectedName = NostrFeedMapper.FEED_NAME_PREFIX + NostrDeviceFixtures.PROFILE_NAME

    private fun waitFor(what: String, condition: () -> Boolean) {
        try {
            compose.waitUntil(WAIT_MILLIS, condition)
        } catch (e: Throwable) {
            throw AssertionError("Never saw: $what | ${describe()}", e)
        }
    }

    /** Counts and flags only: the screen can show the owner's own text. */
    private fun describe(): String {
        val state = shown?.subscribeUiState?.value
        return "hasViewModel=${shown != null} feed=${state?.feed != null} articles=${state?.articles?.size} " +
            "locked=${state?.lockLinkInput} error=${state?.errorMessage?.length} visible=${state?.visible} " +
            "relayRequests=${relay.requestsReceived()}"
    }

    /** The view model of [activity], taken from its ViewModelStore (the same instance across recreate). */
    private fun viewModelOf(activity: ComponentActivity): SubscribeViewModel =
        ViewModelProvider(activity, subscribeViewModelFactory(relay.url))[SubscribeViewModel::class.java]

    /** Puts the real dialog on [activity]. Unlike MainActivity, this test Activity does not rebuild its content after recreate. */
    private fun showDialogOn(activity: ComponentActivity, viewModel: SubscribeViewModel) {
        compose.runOnUiThread { activity.setContent { SubscribeDialog(viewModel) } }
    }

    private fun openDialog() {
        check(compose.activity.applicationContext.currentAccountType == AccountType.Local.id) {
            "The Add Source dialog only searches Nostr for the Local account; this phone is on another account"
        }
        val viewModel = viewModelOf(compose.activity)
        shown = viewModel
        showDialogOn(compose.activity, viewModel)
        compose.runOnUiThread { viewModel.showDrawer() }
        waitFor("the link field") { compose.onAllNodes(hasText(placeholder)).fetchSemanticsNodes().isNotEmpty() }
    }

    /** After a recreate: same view model from the new Activity's store, dialog put back, and the preview title on screen. */
    private fun recreateAndShowAgain(): SubscribeViewModel {
        val before = compose.activity
        compose.activityRule.scenario.recreate()
        val after = compose.activity
        assertNotSame("the activity must really have been recreated", before, after)
        val again = viewModelOf(after)
        assertSame("the ViewModelStore must hand back the same view model", shown, again)
        showDialogOn(after, again)
        waitFor("the preview title back in the dialog") {
            compose.onAllNodes(hasText(expectedName)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(expectedName).assertIsDisplayed()
        return again
    }

    private fun typeNpubAndSearch() {
        compose.onNodeWithText(placeholder).performTextInput(npub)
        waitFor("the npub in the field") { compose.onAllNodes(hasSetTextAction() and hasText(npub)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(searchLabel).performClick()
    }

    private fun waitForPreview() = waitFor("the Nostr preview") { requireNotNull(shown).subscribeUiState.value.feed != null }

    private fun assertPreviewIsComplete(viewModel: SubscribeViewModel) {
        val state = viewModel.subscribeUiState.value
        assertEquals("the preview must be named after the signed profile", expectedName, state.feed?.name)
        assertEquals("the one signed note must be in the preview", 1, state.articles.size)
        assertTrue("the box must stay locked while a preview is shown", state.lockLinkInput)
        assertEquals("a preview is not an error", "", state.errorMessage)
        assertFalse("a Nostr preview is not a web preview", state.webPreview)
    }

    @Test
    fun fixtures_areSignedAndNameTheProfile() {
        val note = requireNotNull(NostrEvent.parse(NostrDeviceFixtures.NOTE_JSON))
        val profile = requireNotNull(NostrEvent.parse(NostrDeviceFixtures.PROFILE_JSON))
        assertTrue(note.isValid(NostrDeviceFixtures.AUTHOR_HEX))
        assertTrue(profile.isValidProfile(NostrDeviceFixtures.AUTHOR_HEX))
        assertEquals(NostrDeviceFixtures.PROFILE_NAME, NostrProfileName.fromContent(profile.content))
        assertNotNull(NostrKeys.hexToNpub(NostrDeviceFixtures.AUTHOR_HEX))
    }

    @Test
    fun previewSurvivesRecreate() {
        openDialog()
        typeNpubAndSearch()
        waitForPreview()
        assertPreviewIsComplete(requireNotNull(shown))

        val again = recreateAndShowAgain()
        assertPreviewIsComplete(again)
        assertEquals("one search, one request to the fake relay", 1, relay.requestsReceived())
    }

    @Test
    fun previewSurvivesRealRotation() {
        openDialog()
        typeNpubAndSearch()
        waitForPreview()
        val viewModel = requireNotNull(shown)
        val activityBefore = compose.activity

        automation.setRotation(UiAutomation.ROTATION_FREEZE_90)
        waitFor("landscape") { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
        assertPreviewIsComplete(viewModel)
        automation.setRotation(UiAutomation.ROTATION_FREEZE_0)
        waitFor("portrait") { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
        assertPreviewIsComplete(viewModel)
        // The plain test Activity has no configChanges, so a rotation recreates it, unlike MainActivity (FRONT-02d).
        // Either outcome keeps the same view model; we record which one happened instead of assuming.
        val recreated = compose.activity !== activityBefore
        val again = viewModelOf(compose.activity)
        assertSame("the view model must survive the rotation (recreated=$recreated)", viewModel, again)
        assertEquals(1, relay.requestsReceived())
    }

    @Test
    fun recreateWhileTheRelayIsStillAnswering_stillShowsThePreview() {
        val gate = CompletableDeferred<Unit>()
        relay.holdAnswerUntil(gate)
        openDialog()
        typeNpubAndSearch()
        waitFor("the search to reach the relay") { relay.requestsReceived() == 1 }
        assertTrue("the box is locked while searching", requireNotNull(shown).subscribeUiState.value.lockLinkInput)
        assertEquals(null, requireNotNull(shown).subscribeUiState.value.feed)

        val before = compose.activity
        compose.activityRule.scenario.recreate()
        assertNotSame("the activity must really have been recreated", before, compose.activity)
        val again = viewModelOf(compose.activity)
        assertSame("the same view model must still be waiting for the relay", shown, again)
        showDialogOn(compose.activity, again)
        gate.complete(Unit)
        waitForPreview()
        waitFor("the preview title in the new dialog") { compose.onAllNodes(hasText(expectedName)).fetchSemanticsNodes().isNotEmpty() }
        assertPreviewIsComplete(again)
        assertEquals("recreate must not start a second search", 1, relay.requestsReceived())
    }

    private companion object {
        const val WAIT_MILLIS = 15_000L
        const val AUTO_ROTATE_ON = 1
    }
}
```
Điểm cần xác nhận bằng chính lần chạy đầu (không đoán trước): (1) `PROFILE_NAME` đã được thêm vào `NostrDeviceFixtures` (Task 2 Step 2); (2) `ComponentActivity` thuần **không** có `configChanges` nên xoay có thể tạo lại Activity: test (b) khẳng định ViewModel còn bất kể chuyện đó, và ghi lại `recreated`; (3) `ComponentActivity` thuần **không** tự dựng lại nội dung sau `recreate()` (khác `MainActivity`, vốn gọi `setContent` trong `onCreate`), nên test gọi `showDialogOn(...)` lại trên Activity mới với **cùng ViewModel lấy từ ViewModelStore**; hộp thoại dựng lại từ state còn nguyên, và tiêu đề `Nostr · Alice Liddell` phải hiện trên màn hình. Nghĩa là test chứng minh (i) ViewModelStore giữ ViewModel và (ii) dialog dựng lại đúng từ state đó, không chứng minh `onCreate` của `MainActivity`.

- [ ] **Step 3: Biên dịch (nền, output ra file)** như Task 1 Step 3. Expected: exit 0. Lỗi hay gặp cần sửa đúng chỗ: `hasText(npub)` trùng nhiều node (dùng `hasSetTextAction() and hasText(npub)` như đã viết), `NostrEvent.content` không phải tên trường (đọc `NostrEvent.kt`), `ComponentActivity.setContent` thiếu import `androidx.activity.compose.setContent` (thêm import này nếu trình biên dịch báo). **Không** thêm `Thread.sleep`, không tăng `WAIT_MILLIS` để chữa lỗi.

- [ ] **Step 4: Không commit ở đây** (test chưa chạy trên máy).

---

### Task 4: Tham số hoá script, rồi chạy trên Pixel (cổng, sao lưu, 40 lượt, khôi phục)

**Files:**
- Create tạm, **không commit**: `/tmp/f29_loop.sh`, đọc `/tmp/f28_gate.sh` (giữ nguyên, dùng chung).

**Interfaces:**
- Consumes: APK app + androidTest từ Task 3; lớp `com.mckimquyen.reader.ui.page.home.feed.subs.SubscribeNostrPreviewRecreateDeviceTest`.
- Produces: `/tmp/f29-loop-run<N>.log`, `/tmp/f29-loop.log`, `ALLDONE`.

- [ ] **Step 1: Tạo `/tmp/f29_loop.sh` từ `f28_loop.sh` bằng python (không sửa bản gốc)**

```bash
python3 - <<'PYEOF'
# -*- coding: utf-8 -*-
s = open('/tmp/f28_loop.sh', encoding='utf-8').read()
assert s.count('SubscribeDialogRecreateDeviceTest') == 1
s = s.replace('SubscribeDialogRecreateDeviceTest', '${TEST_CLASS_NAME}', 1)
s = s.replace('N=${1:-6}\n', 'N=${1:-6}\nTEST_CLASS_NAME=${2:-SubscribeDialogRecreateDeviceTest}\n', 1)
s = s.replace('f28-loop', 'f29-loop')
open('/tmp/f29_loop.sh', 'w', encoding='utf-8').write(s)
PYEOF
grep -n "TEST_CLASS_NAME\|f29-loop\|\-e class" /tmp/f29_loop.sh | head
```
Expected: `-e class com.mckimquyen.reader.ui.page.home.feed.subs.${TEST_CLASS_NAME}` (trong dấu ngoặc kép của zsh, biến được nội suy); mặc định giữ lớp FRONT-02d. Đọc lại toàn bộ script trước khi dùng. Nếu thay không khớp: dừng, sửa tay, không chạy.

- [ ] **Step 2: Báo phiên kia, kiểm cổng, KHÔNG khởi chạy nếu cổng dừng**

1. `SendMessage` tới phiên `t9-keypad-landscape-settings`: "ReadYou cần Pixel 2B051FDH3006MU khoảng 25 phút để chạy test thiết bị; có đang dùng không? Trả lời 'đang dùng' thì tôi chờ."; **chờ trả lời** (nếu phiên kia im lặng thì cổng bên dưới vẫn là hàng rào thật).
2. `zsh /tmp/f28_gate.sh > /tmp/f29-gate0.log 2>&1; echo gate_rc=$?; head -1 /tmp/f29-gate0.log`. Chỉ khi in `GATE OK` mới sang bước 3. Nếu `STOP: foreground is not the launcher` hay bất kỳ `STOP`: **dừng và hỏi owner** bằng `AskUserQuestion`; không khởi chạy vòng lặp trong cùng một lệnh với cổng (bài học: lệnh gộp từng khởi chạy sau khi cổng báo dừng).

- [ ] **Step 3: Một lượt thử trước (không phải 40)**

```bash
zsh /tmp/f29_loop.sh 1 SubscribeNostrPreviewRecreateDeviceTest > /tmp/f29-one.log 2>&1; cat /tmp/f29-one.log
```
(chạy nền như quy ước: `( ...; echo $? > /tmp/f29-one.exit ) > /dev/null 2>&1 &` rồi chờ `.exit` và dòng `ALLDONE`). Đọc `/tmp/f29-loop-run1.log` đầy đủ.
- Nếu `OK (4 tests)`: sang Step 4.
- Nếu đỏ: **dừng và điều tra theo systematic-debugging** (đọc stack, đọc `describe()` trong thông báo lỗi). Không đổi thời gian chờ, không thêm sleep, không gõ lại. Các nguyên nhân có thể và cách phân biệt: (i) `check(... currentAccountType == Local)` ném lỗi: máy không ở tài khoản Local, báo owner; (ii) `Never saw: the link field` hay `the Nostr preview`: xem `relayRequests`, `locked`, `error` trong `describe()`; (iii) `ClassCastException ... RApp`: `applicationContext` không phải `RApp`, báo; (iv) ViewModel khác nhau sau recreate: **đó là kết quả thật** chứ không phải lỗi test: dừng, ghi nhận và hỏi owner.

- [ ] **Step 4: Chạy 40 lượt (nền), đọc kết quả khi có `.exit` và `ALLDONE`**

```bash
rm -f /tmp/f29-loop.exit /tmp/f29-loop.log
( zsh /tmp/f29_loop.sh 40 SubscribeNostrPreviewRecreateDeviceTest > /tmp/f29-loop.log 2>&1; echo $? > /tmp/f29-loop.exit ) > /dev/null 2>&1 &
```
Chờ (có thể phải chờ hai lần vì giới hạn chờ của công cụ). Đọc: số lượt `OK (4 tests)`, lượt đỏ, `RESTORE OK`, băm DataStore `ed9f6887…` trùng, `rotation: 1 0`, `GATE OK`.
**Tiêu chí:** 40/40 xanh. Nếu có lượt đỏ: **không** dùng làm bằng chứng. Thu `describe()` của mọi lượt đỏ (`/tmp/f29-loop-run<N>.log`), tìm điểm chung (bước gõ? bước chờ preview?), điều tra theo systematic-debugging. Không gõ lại, không tăng timeout. Quyết định tiếp (chấp nhận hay không) phải hỏi owner.

- [ ] **Step 5: Trả máy**

Sau khi vòng kết thúc, kiểm chỉ đọc: `pidof` các script `f29_*`, launcher trên cùng, `accelerometer_rotation=1`, `user_rotation=0`, `wm size 1440x3120`, `pm list packages | grep mckimquyen.reader.test` rỗng. Rồi `SendMessage` cho phiên kia: "Xong, trả Pixel."

---

### Task 5: Mutation chạy trên máy (chứng minh test bắt được lỗi)

**Files:**
- Create tạm, **không commit**: `/tmp/f29_dev_mut.sh` (từ `f28_dev_mut.sh`), `/tmp/f29_mut_batch.sh`.
- Đột biến tạm (luôn khôi phục + `cmp`): `app/src/main/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModel.kt`; một đột biến **test-only** trên `SubscribeNostrPreviewRecreateDeviceTest.kt`.

**Interfaces:**
- Consumes: 4 test của Task 3 (xanh 40/40 ở Task 4), cổng và sao lưu của Task 4.
- Produces: `/tmp/f29-mut-<label>-*.log`, `/tmp/f29-mut-batch.log`, `ALLDONE`, nguồn `identical`.

- [ ] **Step 1: Tham số hoá `f28_dev_mut.sh`**

```bash
python3 - <<'PYEOF'
# -*- coding: utf-8 -*-
s = open('/tmp/f28_dev_mut.sh', encoding='utf-8').read()
assert s.count('SubscribeDialogRecreateDeviceTest') == 1
s = s.replace('TEST=com.mckimquyen.reader.ui.page.home.feed.subs.SubscribeDialogRecreateDeviceTest',
              'TEST=com.mckimquyen.reader.ui.page.home.feed.subs.SubscribeNostrPreviewRecreateDeviceTest', 1)
s = s.replace('^OK (3 tests)', '^OK (4 tests)').replace('OK (3 tests)', 'OK (4 tests)')
s = s.replace('f28-mut', 'f29-mut')
open('/tmp/f29_dev_mut.sh', 'w', encoding='utf-8').write(s)
PYEOF
grep -n "TEST=\|OK (4\|f29-mut" /tmp/f29_dev_mut.sh | head -5; grep -c "(3 tests)" /tmp/f29_dev_mut.sh
```
Expected: `TEST=…SubscribeNostrPreviewRecreateDeviceTest`, `OK (4 tests)`, `0` còn `(3 tests)`. Đọc toàn bộ script trước khi dùng; script phải khôi phục trong `trap` (đã có ở bản gốc).

- [ ] **Step 2: Batch (một script nền duy nhất)**

Đoạn mã dưới đã được kiểm độc nhất trên `SubscribeViewModel.kt` ngày 2026-11-15 (đếm đúng 1 lần mỗi đoạn). Nếu `count != 1` tại thời điểm chạy thì script dừng (`PATTERN-PROBLEM`), không đoán.

```bash
cat > /tmp/f29_mut_batch.sh <<'SH'
#!/bin/zsh
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou || exit 90
SERIAL=2B051FDH3006MU
VM=app/src/main/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModel.kt
T=app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeNostrPreviewRecreateDeviceTest.kt
cp -p $VM /tmp/f29-orig-vm.kt; cp -p $T /tmp/f29-orig-test.kt
zsh /tmp/f28_gate.sh > /tmp/f29-batch-gate0.log 2>&1 || { echo "GATE-STOP at start: $(head -1 /tmp/f29-batch-gate0.log)"; echo ALLDONE; exit 92; }
python3 /tmp/pixel-backup.py > /tmp/f29-batch-backup.log 2>&1 || { echo BACKUP-FAILED; echo ALLDONE; exit 93; }
M=/tmp/f29_dev_mut.sh
zsh $M base
zsh $M m01-init-resets $VM 'title = androidStringsHelper.getString(R.string.subscribe),
                groups = rssService.get().pullGroups(),' 'title = androidStringsHelper.getString(R.string.subscribe),
                groups = rssService.get().pullGroups(),
                feed = null, articles = emptyList(), linkContent = "",'
zsh $M m02-no-feed $VM '                                linkContent = feedUrl,
                                feed = feed,
                                articles = articles,' '                                linkContent = feedUrl,
                                articles = articles,'
zsh $M m03-unlocked $VM '                                errorMessage = "",
                                lockLinkInput = true,
                                webPreview = false,
                                isSearchPage = false,' '                                errorMessage = "",
                                lockLinkInput = false,
                                webPreview = false,
                                isSearchPage = false,'
zsh $M m04-name-ignores-profile $VM 'NostrFeedMapper.feedName(npub, fetched?.profileName),' 'NostrFeedMapper.feedName(npub),'
zsh $M m05-drop-notes $VM 'events = it.notes,' 'events = it.notes.take(0),'
zsh $M m06-test-builds-vm-outside-store $T 'val viewModel = viewModelOf(compose.activity)
        shown = viewModel' 'val viewModel = com.mckimquyen.reader.ui.page.home.feed.subs.subscribeViewModelFactory(relay.url).create(SubscribeViewModel::class.java)
        shown = viewModel'
zsh $M base
cmp -s /tmp/f29-orig-vm.kt $VM && echo "final vm: identical" || echo "final vm: DIFFERENT"
cmp -s /tmp/f29-orig-test.kt $T && echo "final test: identical" || echo "final test: DIFFERENT"
echo "git diff main: $(git diff --stat -- app/src/main | wc -l) lines"
python3 /tmp/pixel-restore.py > /tmp/f29-batch-restore.log 2>&1; echo "restore_exit=$?"
grep -E "DIFFERENT|RESTORE" /tmp/f29-batch-restore.log
R=$(cat /tmp/pixel-root)
echo "datastore: $(adb -s $SERIAL exec-out run-as com.mckimquyen.reader cat files/datastore/settings.preferences_pb | shasum | cut -c1-40) vs $(shasum $R/pixel/settings.preferences_pb | cut -c1-40)"
adb -s $SERIAL uninstall com.mckimquyen.reader.test
echo "rotation: $(adb -s $SERIAL shell settings get system accelerometer_rotation) $(adb -s $SERIAL shell settings get system user_rotation)"
zsh /tmp/f28_gate.sh | head -1
echo ALLDONE
SH
```
`m06` là đột biến **trong test** (dựng ViewModel bằng tay, ngoài `ViewModelStore`): nó phải làm `assertSame(... same view model ...)` **đỏ** sau `recreate()` (vì `viewModelOf(after)` trả instance khác với `shown`), chứng minh khẳng định cùng-instance không thừa. Nếu `m06` xanh: khẳng định `assertSame` vô nghĩa, dừng và sửa test.

- [ ] **Step 3: Kiểm cổng, báo phiên kia, rồi khởi chạy (hai lệnh tách rời)**

Như Task 4 Step 2: báo phiên kia, `zsh /tmp/f28_gate.sh` phải in `GATE OK` **trong một lệnh riêng**, rồi mới
```bash
rm -f /tmp/f29-mut-batch.exit /tmp/f29-mut-batch.log
( zsh /tmp/f29_mut_batch.sh > /tmp/f29-mut-batch.log 2>&1; echo $? > /tmp/f29-mut-batch.exit ) > /dev/null 2>&1 &
```
Chờ **cả** `/tmp/f29-mut-batch.exit` **và** dòng `ALLDONE`. **Trong lúc chờ không sửa `SubscribeViewModel.kt` hay tệp test.**

- [ ] **Step 4: Đọc kết quả**

Mong đợi: `base` xanh `OK (4 tests)` hai lần (đầu và cuối); `m01`..`m05` đỏ ở test liên quan; `m06` đỏ ở `assertSame`; `final vm: identical`, `final test: identical`, `git diff main: 0 lines`, `RESTORE OK`, băm DataStore trùng, `rotation: 1 0`, `GATE OK`.
- Đột biến sống sót ⇒ test yếu: thêm khẳng định, chạy lại `base` 40 lượt rồi chạy lại đột biến đó.
- Đỏ vì lý do hạ tầng (không khởi động được, relay, cổng) không tính là "bắt được"; điều tra, không tính.
- Lượt `base` cuối đỏ: chập chờn, quay lại điều tra (Task 4 Step 4), **không commit**.

- [ ] **Step 5: Trả máy** như Task 4 Step 5.

---

### Task 6: JVM, tài liệu, commit

**Files:**
- Modify: `doc/feature.md`, `docs/superpowers/specs/2026-11-15-front-02e-nostr-preview-recreate-device-test-design.md` (mục "Điều chỉnh khi thực thi").
- Create: `doc/task/done/FRONT-02e_nostr-preview-recreate-device-test_DONE.md`.

- [ ] **Step 1: Full suite JVM (nền, output ra file) và assemble**

```bash
rm -f /tmp/f29-full.exit
( ./gradlew testDevDebugUnitTest --rerun-tasks > /tmp/f29-full.log 2>&1; echo $? > /tmp/f29-full.exit; ./gradlew assembleDevDebug assembleDevDebugAndroidTest > /tmp/f29-assemble.log 2>&1; echo $? > /tmp/f29-assemble.exit ) > /dev/null 2>&1 &
```
Chờ cả hai `.exit`. Đếm từ XML: `app/build/test-results/testDevDebugUnitTest/*.xml` (tests, failures, errors, skipped). Expected: 3.627, 0, 0, 0 (thay đổi chỉ ở `androidTest` nên số JVM không đổi); cả hai `.exit` bằng `0`. Lint **không** quét `androidTest` (xác nhận bằng báo cáo FRONT-02d); nếu chạy lại `lintDevDebug` chỉ để chứng minh không có lỗi mới ở `main`, ghi đúng như vậy.

- [ ] **Step 2: Kiểm phạm vi diff**

```bash
git status --short | grep -v idea
git diff --check
git diff --stat -- app/src/main app/schemas app/src/main/AndroidManifest.xml
git diff -- app/build.gradle
```
Expected: `app/src/main`, `app/schemas`, manifest **không đổi**; `app/build.gradle` chỉ thêm đúng hai dòng (một dòng chú thích, một dòng `androidTestImplementation`).

- [ ] **Step 3: Tài liệu**

Báo cáo tiếng Việt, số liệu thật: số lượt (kể cả mọi lượt đỏ và nguyên nhân), bảng đột biến, máy và phiên bản Android, băm DataStore, ghi rõ: test dùng Activity test chứ không phải `MainActivity`; ViewModelStore thật nên (i) giữ ViewModel, (ii) dialog dựng lại từ state; **không** chứng minh scope `hiltViewModel()` của `FeedsPage`; xoay không có `configChanges` trên Activity test nên có thể tạo lại Activity (ghi lại cái nào xảy ra); một relay giả, không mạng thật; lint không quét `androidTest`; chập chờn bước gõ chưa rõ gốc (FRONT-02d) và có tái xuất hay không ở đây. `doc/feature.md` thêm mục FRONT-02e trước `## 🟡 In progress`. Spec thêm mục "Điều chỉnh khi thực thi" (không `EntryPoint`, đọc field của `RApp`; dialog được dựng lại bằng `setContent` trên Activity mới; những gì đã đo khác kế hoạch).

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle \
  app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/NostrDeviceFixtures.kt \
  app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/NostrFakeRelay.kt \
  app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModelFactory.kt \
  app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeNostrPreviewRecreateDeviceTest.kt \
  doc/feature.md doc/task/done/FRONT-02e_nostr-preview-recreate-device-test_DONE.md \
  docs/superpowers/specs/2026-11-15-front-02e-nostr-preview-recreate-device-test-design.md
git commit -m "test(front-02e): keep the Nostr preview across recreate and rotation, read from a fake relay on a real phone

Co-Authored-By: Claude Code <noreply@anthropic.com>"
git status --short | grep -v idea
```
Expected: một commit trên `dev`; cây sạch ngoài `.idea`; không push.

---

## Self-review

**Phủ spec.** (1) ViewModel qua `ViewModelProvider` với ViewModelStore thật: Task 1 (factory) + Task 3 (`viewModelOf`, `assertSame`). (2) Relay giả trong tiến trình, không mạng thật, một dependency test: Task 2. (3) Ba test (a)(b)(c) + một test kiểm fixture: Task 3. (4) Quy tắc thiết bị (cổng, phiên khác, sao lưu/khôi phục, giữ cài đặt xoay, không MockK, không tọa độ): Task 4 và 5. (5) Mutation, gồm đột biến test-only `m06` thay cho mục (4) của spec: Task 5. (6) JVM, assemble, báo cáo, `feature.md`: Task 6. **Lệch so với spec, đã ghi ở "Điều chỉnh":** không dùng `@EntryPoint` (đọc field của `RApp`); `SettingsProvider` không cần; `FeedsPage` không có nên không cần `autoAdvance = false`; dialog phải dựng lại bằng `setContent` trên Activity mới.

**Quét placeholder.** Không còn "TBD"/"TODO"/"tương tự Task N". Hai chỗ có điều kiện thật: (i) `PROFILE_NAME` phải khớp `NostrTestEvents`; kiểm bằng test `fixtures_areSignedAndNameTheProfile` chứ không tin sao chép; (ii) nếu `check(currentAccountType == Local)` ném lỗi thì phải hỏi owner. Cả hai có hướng xử lý.

**Nhất quán kiểu và tên.** `subscribeViewModelFactory(relayUrl: String): ViewModelProvider.Factory` (Task 1) được dùng ở `viewModelOf` (Task 3) và `m06` (Task 5). `NostrFakeRelay.url`, `.holdAnswerUntil(CompletableDeferred<Unit>)`, `.requestsReceived()`, `.close()` (Task 2) khớp cách dùng ở Task 3. `NostrDeviceFixtures.AUTHOR_HEX/NOTE_JSON/PROFILE_JSON/PROFILE_NAME` khớp. Test có **4** hàm, các script mong đợi `OK (4 tests)`. Đã bỏ hàm thừa `expectAnotherConnection()` khỏi `NostrFakeRelay` (không test nào gọi, YAGNI).

**Rủi ro còn lại, nói thẳng.** (1) `MockWebServer.start()` trên máy thật ở Android 17 chưa kiểm; Task 4 Step 3 (một lượt thử) bắt sớm. (2) Một `MockResponse` chỉ phục vụ một WebSocket; mỗi test chỉ tìm **một** lần nên đủ, nhưng nếu `search()` thử lại thì `relayRequests` sẽ khác 1 và test đỏ có lý do rõ. (3) `NostrFakeRelay.onMessage` dùng `runBlocking` để giữ cổng: chặn luồng của OkHttp (chấp nhận trong test). (4) Test xoay trên Activity không có `configChanges` có thể tạo lại Activity; Task 3 ghi lại cả hai trường hợp, nhưng `assertPreviewIsComplete` sau xoay chỉ đọc state ViewModel, **không** đọc màn hình (nếu Activity bị tạo lại thì nội dung không được dựng lại ở bước đó): đã nêu trong spec là phạm vi. (5) Chập chờn bước gõ của FRONT-02d chưa rõ gốc; test này gõ một `npub` dài (63 ký tự) nên có thể chạm cùng lỗi; không được gõ lại bừa. (6) Lint không quét `androidTest`.
