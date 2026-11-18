# FRONT-02t — Test máy thật: sửa địa chỉ nguồn rồi đồng bộ (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (hoặc subagent-driven-development) để thực thi plan này từng task. Các bước dùng checkbox `- [ ]`.

**Goal:** Chứng minh trên Tecno KJ7, với Room thật và `FeedOptionViewModel` thật, rằng `changeFeedUrl()` (FRONT-02p) ghi đúng hai cột `url` và `sourceUrl`, giữ nguyên mọi trường khác, và lần đồng bộ kế tiếp hỏi máy chủ về địa chỉ **mới** rồi nâng `sourceUrl` lên DID mới (FRONT-02q).

**Architecture:** Một tệp test mới `FeedAddressEditDeviceTest.kt` chạy trên `ComponentActivity` (không `MainActivity`, không gõ giao diện, không `showDrawer`). Dùng lại `BlueskyGuardRig` (02r): máy chủ Bluesky giả trên `127.0.0.1`, nhóm tạm `front02r_probe_`, dọn dẹp. Rig được mở rộng nhẹ (chỉ trong `androidTest`): ghi lại `actor` của từng yêu cầu `getProfile`, và `seed` nhận thêm ba tham số có giá trị mặc định.

**Tech Stack:** Compose UI test (`createAndroidComposeRule<ComponentActivity>` chỉ để có Activity/`ViewModelStore`), `ViewModelProvider`, Room thật của app, `mockwebserver3` 5.0.0-alpha.11 (đã có), `am instrument`, adb, Python 3.9.

## Global Constraints

- Nhánh `dev`; không worktree, không push, không PR, không commit `.idea`.
- Không đổi mã production, manifest, chuỗi, Room (v22), DataStore, `app/build.gradle`; không dependency mới. Chỉ thêm/sửa trong `app/src/androidTest` (`FeedAddressEditDeviceTest.kt` mới; `BlueskyGuardRig.kt` sửa nhẹ). **Mutation tạm** trong `FeedOptionVM.kt` và `LocalRssSv.kt` luôn khôi phục + `cmp` + `git diff app/src/main` rỗng.
- Thiết bị **CHỈ Tecno KJ7 `115333744A005844`**; chỉ `adb -s 115333744A005844`; không `connected*`; chỉ `am instrument --user 0 -w -r -e class … com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner`; không MockK trên máy; không tọa độ; không mạng thật; **không `setRotation`**; **không gọi `showDrawer`** (báo cáo 02p: làm đồng hồ Compose bận).
- Cổng `/tmp/kj7_gate.sh` phải in `GATE OK` **trong một lệnh RIÊNG** trước mỗi lần khởi chạy. Cổng dừng (app lạ ở foreground, khóa màn, xoay lệch): **dừng và hỏi owner**.
- Sao lưu/khôi phục **byte-exact 6 tệp** (`/tmp/kj7_backup.py`, `/tmp/kj7_restore.py`); băm DataStore `9ca1c202050c258b4aac9a063e27def350d737fb` trùng sau mỗi vòng; xoay `1 0` trước và sau mỗi lượt; không xóa DataStore; gỡ `com.mckimquyen.reader.test` cuối vòng.
- Test ghi tạm vào Room thật chỉ bằng dòng có tiền tố `front02r_probe_`; `@After` luôn `cleanUp()`.
- Gradle chạy NỀN, output ra file, chờ `.exit`/`ALLDONE`; "completed" của lệnh nền chỉ là lệnh khởi chạy.
- **Không gõ lại, không tăng timeout, không `sleep` để chữa lỗi.** Quy tắc lượt đỏ: có `AdActivity` trên cùng (watcher `/tmp/kj7_watch.sh`) thì chấp nhận và ghi; **không có `AdActivity` thì dừng, điều tra theo systematic-debugging, hỏi owner.**
- Mutation: một script nền duy nhất, không sửa tệp đột biến cho tới `ALLDONE`; mỗi đột biến phải làm **đúng** tập test dự định đỏ (so sánh tự động).
- zsh: không `echo ====`; `grep -r` quote mẫu, không dùng `--include`; sửa tệp bằng python; Python 3.9 cần `# -*- coding: utf-8 -*-` và `open(..., encoding="utf-8")`; macOS `sed -i ''`.
- R5: không magic number (hằng số có tên); test bắt buộc kèm; không báo "done" khi chưa dọn. R2: cập nhật `doc/feature.md`.
- Không dùng số JVM trong plan làm bằng chứng: đo lại lúc thực thi.
- Nói thẳng phần không kiểm: bấm giữ, `FeedOptionDrawer`, `ChangeUrlDialog`, nguồn WEB và NOSTR, Fever/Google Reader, chặn địa chỉ trùng hay sai; lint không quét `androidTest`.

## Điều chỉnh so với spec (đã đọc code thật, 2026-11-18)

1. **Tên trường state:** `_feedOptionUiState` (`FeedOptionVM.kt`, kiểu `MutableStateFlow<FeedOptionUiState>`); test JVM dùng hằng `UI_STATE_FIELD = "_feedOptionUiState"` và ghi `state.value.copy(feed = feed, selectedGroupId = feed.groupId)` bằng reflection. Test máy làm y hệt.
2. **`FeedOptionViewModel(rssService: RssSv, @MainDispatcher main, @IODispatcher io)`:** dựng như `FeedItemDeviceTest` (`FeedOptionViewModel(app.rssSv, Dispatchers.Main, app.ioDispatcher)`), qua `ViewModelProvider(activity, factory)` trên luồng chính. `RApp.rssSv` và `RApp.ioDispatcher` là `lateinit var` công khai.
3. **Hộp thoại đóng sau khi ghi:** `changeFeedUrl()` gọi `updateFeed(...)` rồi mới đặt `changeUrlDialogVisible = false`; test chờ cờ này `false` thì chắc chắn dòng Room đã ghi xong (không `sleep`). `showFeedUrlDialog()` đặt `newUrl = feed.url` nên test kiểm thêm "ô địa chỉ mở với địa chỉ hiện tại".
4. **`actor` của yêu cầu:** rig đang phân biệt yêu cầu bằng `request.requestLine.contains("getProfile")`; dòng yêu cầu có dạng `GET /xrpc/app.bsky.actor.getProfile?actor=<actor> HTTP/1.1`. Rig lấy `actor` bằng regex `[?&]actor=([^& ]+)` (giải mã URL). Không cần API `requestUrl` của mockwebserver3 alpha (không kiểm được vì chỉ có jar javadoc).
5. **`seed` của rig không đặt `icon`, `isNotification`, `isFullContent`** (mặc định rỗng/false) nên không chứng minh được "mọi trường khác giữ nguyên". Thêm ba tham số có mặc định (`icon`, `isNotification`, `isFullContent`); 12 test của 02r không đổi.
6. **Tập test đỏ mong đợi của đột biến** (xem Task 5): `t1` và `t3` đều làm test Bluesky đỏ **ngay ở bước sau khi sửa** (cột `sourceUrl` không thành địa chỉ mới), nên chúng cùng điểm chết; chấp nhận, ghi trong báo cáo. Thêm `t5` (đổi `name`) để test "mọi trường khác" có đột biến nhắm đúng.
7. **Test "mọi trường khác" không so `url`/`sourceUrl`:** so `edited.copy(url = seeded.url, sourceUrl = seeded.sourceUrl)` với `seeded`, để chỉ đột biến `t5` làm nó đỏ.
8. **Không `@After` kiểm `probeRowCount`:** kiểm trong test dọn dẹp riêng như 02r (một khẳng định trong `@After` sẽ che lỗi thật của test).

## Cấu trúc file

| File | Việc |
|---|---|
| Modify `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/BlueskyGuardRig.kt` | `profileActors()` và ba tham số mặc định của `seed` |
| Create `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/FeedAddressEditDeviceTest.kt` | 4 test |
| Tạm, **không commit**: `/tmp/kj7_mut_t.sh`, `/tmp/kj7_t_batch.sh` | đột biến chạy trên máy (từ `kj7_mut_s.sh`, đổi lớp, số test) |
| Modify `doc/feature.md`, spec (mục "Điều chỉnh khi thực thi") | tài liệu |
| Create `doc/task/done/FRONT-02t_feed-address-edit-device-test_DONE.md` | báo cáo |

---

### Task 1: Mở rộng `BlueskyGuardRig` (chỉ androidTest) và biên dịch (chưa chạm Tecno)

**Files:**
- Modify: `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/BlueskyGuardRig.kt`

**Interfaces:**
- Consumes: rig 02r hiện có (`seed(id, url, sourceUrl, sourceType)`, bộ phân phối của `MockWebServer` có `request.requestLine`).
- Produces: `fun profileActors(): List<String>` (actor của từng yêu cầu `getProfile`, theo thứ tự đến) và `seed(id, url, sourceUrl, sourceType = BLUESKY, icon: String? = null, isNotification: Boolean = false, isFullContent: Boolean = false)`.

- [ ] **Step 1: Kiểm các đoạn cần sửa còn đúng như plan**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
R=app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/BlueskyGuardRig.kt
grep -n "profileCalls.incrementAndGet()\|suspend fun seed\|isNotification = false\|^import java\|const val PROBE_PREFIX" $R
git status --short app
```
Expected: mỗi mẫu đúng 1 dòng (hai dòng `import java`); `git status` không có thay đổi trong `app`.

- [ ] **Step 2: Sửa rig bằng python (mỗi mẫu phải xuất hiện đúng 1 lần)**

```bash
python3 - <<'EOF'
# -*- coding: utf-8 -*-
R = "app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/BlueskyGuardRig.kt"
s = open(R, encoding="utf-8").read()

def sub(old, new):
    global s
    assert s.count(old) == 1, "pattern missing or not unique: " + old[:50]
    s = s.replace(old, new, 1)

sub("import java.io.IOException\nimport java.util.concurrent.atomic.AtomicInteger\n",
    "import java.io.IOException\nimport java.net.URLDecoder\nimport java.util.concurrent.CopyOnWriteArrayList\nimport java.util.concurrent.atomic.AtomicInteger\n")
sub("    private val profileCalls = AtomicInteger(0)\n",
    "    private val profileCalls = AtomicInteger(0)\n    private val profileActorsSeen = CopyOnWriteArrayList<String>()\n")
sub("                    profileCalls.incrementAndGet()\n",
    "                    profileCalls.incrementAndGet()\n                    ACTOR_PARAM.find(request.requestLine)?.let { profileActorsSeen.add(URLDecoder.decode(it.groupValues[1], \"UTF-8\")) }\n")
sub("    fun profileRequests(): Int = profileCalls.get()\n",
    "    fun profileRequests(): Int = profileCalls.get()\n\n    /** The account each getProfile request asked about, in the order they came (FRONT-02t: which address did the sync use?). */\n    fun profileActors(): List<String> = profileActorsSeen.toList()\n")
sub("    suspend fun seed(id: String, url: String, sourceUrl: String?, sourceType: String = Feed.BLUESKY_SOURCE_TYPE) {",
    "    suspend fun seed(\n        id: String,\n        url: String,\n        sourceUrl: String?,\n        sourceType: String = Feed.BLUESKY_SOURCE_TYPE,\n        icon: String? = null,\n        isNotification: Boolean = false,\n        isFullContent: Boolean = false,\n    ) {")
sub('name = "Bluesky · Probe", icon = null,', 'name = "Bluesky · Probe", icon = icon,')
sub("isNotification = false, sourceType = sourceType,", "isNotification = isNotification, isFullContent = isFullContent, sourceType = sourceType,")
sub('        const val PROBE_PREFIX = "front02r_probe_"\n',
    '        const val PROBE_PREFIX = "front02r_probe_"\n        private val ACTOR_PARAM = Regex("[?&]actor=([^& ]+)")\n')
open(R, "w", encoding="utf-8").write(s)
EOF
git diff --stat -- app
```
Expected: 1 tệp, khoảng 14 dòng thêm.

- [ ] **Step 3: Biên dịch nền (chờ `.exit`), kiểm 02r vẫn biên dịch**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f /tmp/t-build1.exit; ( ./gradlew assembleDevDebugAndroidTest > /tmp/t-build1.log 2>&1; echo $? > /tmp/t-build1.exit ) > /dev/null 2>&1 &
for i in $(seq 1 100); do test -f /tmp/t-build1.exit && break; sleep 5; done; cat /tmp/t-build1.exit; tail -3 /tmp/t-build1.log
```
Expected: `0` và `BUILD SUCCESSFUL`. Lỗi biên dịch: đọc log, sửa, chạy lại (không đoán).

- [ ] **Step 4: Commit rig**

```bash
git add app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/BlueskyGuardRig.kt
git commit -m "test(front-02t): let the Bluesky guard rig record which account a sync asked about and seed more fields

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: Viết `FeedAddressEditDeviceTest` và biên dịch (chưa chạm Tecno)

**Files:**
- Create: `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/FeedAddressEditDeviceTest.kt`

**Interfaces:**
- Consumes: Task 1 (`rig.profileActors()`, `rig.seed(..., icon, isNotification, isFullContent)`), rig 02r (`profile`, `stored`, `syncOnce`, `articleCountOf`, `probeRowCount`, `realRowCounts`, `blockedRequests`, `createGroup`, `cleanUp`, `close`), `FeedOptionViewModel.showFeedUrlDialog()/inputNewUrl(String)/changeFeedUrl()/feedOptionUiState`, `BlueskyFeedMapper.feedUrl(actor)`.
- Produces: lớp `FeedAddressEditDeviceTest` với 4 test: `aNewBlueskySource_changesBothColumns_andTheNextSyncAsksForTheNewAddress`, `anRssSource_changesOnlyItsUrl`, `everyOtherField_isKept`, `afterCleanUp_nothingOfTheProbeIsLeftAnywhere_andTheRealDataIsUntouched`.

> Ghi chú TDD (nói thẳng): mã production (02p, 02q) **đã có**, nên test không có bước "đỏ trước". Bằng chứng "test bắt được lỗi" là **mutation chạy trên máy ở Task 4** (5 đột biến, mỗi cái làm đúng test dự định đỏ).

- [ ] **Step 1: Kiểm các tên sẽ chép còn đúng**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
grep -n "private val _feedOptionUiState\|fun showFeedUrlDialog\|fun inputNewUrl\|fun changeFeedUrl" app/src/main/java/com/mckimquyen/reader/ui/page/home/feed/drawer/feed/FeedOptionVM.kt
grep -n "fun profile(\|fun profileActors\|suspend fun stored\|suspend fun syncOnce\|fun articleCountOf\|fun realRowCounts\|fun probeRowCount\|suspend fun createGroup\|suspend fun cleanUp" app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/BlueskyGuardRig.kt
grep -n "lateinit var rssSv\|lateinit var ioDispatcher" app/src/main/java/com/mckimquyen/reader/RApp.kt
```
Expected: mỗi mẫu đúng 1 dòng (`_feedOptionUiState` là `private val`).

- [ ] **Step 2: Tạo tệp test**

```bash
cat > app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/FeedAddressEditDeviceTest.kt <<'EOF'
package com.mckimquyen.reader.ui.page.home.feed.subs

import androidx.activity.ComponentActivity
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.sv.LocalRssSvFactory
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper
import com.mckimquyen.reader.ui.ext.currentAccountType
import com.mckimquyen.reader.ui.page.home.feed.drawer.feed.FeedOptionUiState
import com.mckimquyen.reader.ui.page.home.feed.drawer.feed.FeedOptionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

private const val CLASS_TIMEOUT_SECONDS = 90L
private const val WAIT_MILLIS = 15_000L
private const val POLL_MILLIS = 50L
private const val NOT_YET_REAL_ROWS = -1L
private const val UI_STATE_FIELD = "_feedOptionUiState"

/**
 * Whether the phone's real Room keeps the right columns when a source address is edited (FRONT-02p), and whether the next sync then
 * asks the server about the NEW address and remembers the new account's DID (FRONT-02q).
 * The real FeedOptionViewModel is driven directly (showFeedUrlDialog, inputNewUrl, changeFeedUrl): nothing is typed through the
 * screen, showDrawer() is never called (it keeps the Compose clock busy, see the 02p report) and MainActivity is not started.
 * A fake Bluesky server on 127.0.0.1 answers. NOT covered: the long press on the address row, FeedOptionDrawer, ChangeUrlDialog,
 * web and Nostr sources (JVM tests only), Fever and Google Reader, refusing a wrong or duplicate address (02p does not).
 */
@OptIn(ExperimentalMaterialApi::class)
@RunWith(AndroidJUnit4::class)
class FeedAddressEditDeviceTest {
    @get:Rule val classTimeout: Timeout = Timeout.seconds(CLASS_TIMEOUT_SECONDS)
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var rig: BlueskyGuardRig
    private var realRowsBefore: Pair<Long, Long> = Pair(NOT_YET_REAL_ROWS, NOT_YET_REAL_ROWS)

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

    /** Runs even when a test failed: the probe sources must never stay in the phone's database. */
    @After
    fun tearDown() {
        try {
            runBlocking { rig.cleanUp() }
        } finally {
            rig.close()
        }
    }

    private fun waitFor(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + WAIT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError("Never saw: $what | ${rig.profileRequests()} profile / ${rig.feedRequests()} feed requests")
    }

    /** The real view model, built the way FeedItemDeviceTest builds it: from the running app, without Hilt. */
    private fun newViewModel(): FeedOptionViewModel {
        val app = LocalRssSvFactory.app()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                FeedOptionViewModel(app.rssSv, Dispatchers.Main, app.ioDispatcher) as T
        }
        return compose.runOnUiThread { ViewModelProvider(compose.activity, factory)[FeedOptionViewModel::class.java] }
    }

    /**
     * Puts [feed] into the view model's state as the drawer would after loading it, WITHOUT calling showDrawer().
     * The state is written by reflection because there is no public way to load a feed without opening the drawer.
     */
    private fun open(viewModel: FeedOptionViewModel, feed: Feed) {
        val field = FeedOptionViewModel::class.java.getDeclaredField(UI_STATE_FIELD).apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") // the field is a MutableStateFlow<FeedOptionUiState>; a wrong type fails loudly right here
        val state = field.get(viewModel) as MutableStateFlow<FeedOptionUiState>
        state.value = state.value.copy(feed = feed, selectedGroupId = feed.groupId)
    }

    /** What the user does: open the address box, type [address], confirm. Returns once the view model says the box closed. */
    private fun changeAddress(viewModel: FeedOptionViewModel, feed: Feed, address: String) {
        open(viewModel, feed)
        viewModel.showFeedUrlDialog()
        assertTrue("the box opens", viewModel.feedOptionUiState.value.changeUrlDialogVisible)
        assertEquals("the box opens with the address the source has now", feed.url, viewModel.feedOptionUiState.value.newUrl)
        viewModel.inputNewUrl(address)
        viewModel.changeFeedUrl()
        // changeFeedUrl() closes the box only AFTER updateFeed() returned, so a closed box means the row is written.
        waitFor("the address box to close after saving") { !viewModel.feedOptionUiState.value.changeUrlDialogVisible }
    }

    private fun stored(id: String): Feed = requireNotNull(runBlocking { rig.stored(id) }) { "the probe source $id is not in Room" }

    // ---- 02p: both columns are written; 02q: the next sync uses the new address and remembers the new account

    @Test
    fun aNewBlueskySource_changesBothColumns_andTheNextSyncAsksForTheNewAddress() {
        rig.profile(DID_NEW, HANDLE_NEW)
        runBlocking { rig.seed("edit", url = BlueskyFeedMapper.feedUrl(HANDLE_OLD), sourceUrl = BlueskyFeedMapper.feedUrl(DID_OLD)) }
        val newAddress = BlueskyFeedMapper.feedUrl(HANDLE_NEW)

        changeAddress(newViewModel(), stored("edit"), newAddress)

        val saved = stored("edit")
        assertEquals("the address the user typed", newAddress, saved.url)
        assertEquals("the address the sync reads must change too, or the edit does nothing", newAddress, saved.sourceUrl)

        val result = runBlocking { rig.syncOnce(saved) }
        assertNotNull("the sync of the edited source must succeed", result.outcome)
        assertEquals("the server was asked about the NEW account only", listOf(HANDLE_NEW), rig.profileActors())
        val synced = stored("edit")
        assertEquals("the address the user sees is still the one typed", newAddress, synced.url)
        assertEquals("the sync remembers the new account's DID", BlueskyFeedMapper.feedUrl(DID_NEW), synced.sourceUrl)
        assertEquals(1, rig.articleCountOf("edit"))
        assertEquals("nothing may reach a host other than 127.0.0.1", 0, rig.blockedRequests())
    }

    @Test
    fun anRssSource_changesOnlyItsUrl() {
        runBlocking { rig.seed("rss", url = RSS_OLD, sourceUrl = null, sourceType = RSS_TYPE) }

        changeAddress(newViewModel(), stored("rss"), RSS_NEW)

        val saved = stored("rss")
        assertEquals(RSS_NEW, saved.url)
        assertNull("a plain feed has no second address and must not get one", saved.sourceUrl)
        assertEquals(RSS_TYPE, saved.sourceType)
    }

    @Test
    fun everyOtherField_isKept() {
        runBlocking {
            rig.seed(
                "fields",
                url = BlueskyFeedMapper.feedUrl(HANDLE_OLD),
                sourceUrl = BlueskyFeedMapper.feedUrl(DID_OLD),
                icon = ICON,
                isNotification = true,
                isFullContent = true,
            )
        }
        val seeded = stored("fields")
        assertTrue("the probe must start with every field set, or keeping them proves nothing", seeded.isNotification && seeded.isFullContent && seeded.icon == ICON)

        changeAddress(newViewModel(), seeded, BlueskyFeedMapper.feedUrl(HANDLE_NEW))

        val saved = stored("fields")
        assertEquals("only the two address columns may change", seeded, saved.copy(url = seeded.url, sourceUrl = seeded.sourceUrl))
        assertFalse("the address really changed", saved.url == seeded.url)
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
        const val RSS_TYPE = "RSS"
        const val RSS_OLD = "https://front02t.invalid/old.xml"
        const val RSS_NEW = "https://front02t.invalid/new.xml"
        const val ICON = "https://front02t.invalid/icon.png"
        const val HANDLE_OLD = "alice-front02t.bsky.social"
        const val HANDLE_NEW = "alice-renamed-front02t.bsky.social"
        const val DID_OLD = "did:plc:front02toldoldoldoldoldo"
        const val DID_NEW = "did:plc:front02tnewnewnewnewnewn"
    }
}
EOF
```

- [ ] **Step 3: Biên dịch nền (chờ `.exit`)**

```bash
rm -f /tmp/t-build2.exit; ( ./gradlew assembleDevDebug assembleDevDebugAndroidTest > /tmp/t-build2.log 2>&1; echo $? > /tmp/t-build2.exit ) > /dev/null 2>&1 &
for i in $(seq 1 100); do test -f /tmp/t-build2.exit && break; sleep 5; done; cat /tmp/t-build2.exit; grep -E "^e: |error:" /tmp/t-build2.log | head; tail -3 /tmp/t-build2.log
```
Expected: `0`, `BUILD SUCCESSFUL`. Nếu lỗi: đọc đúng dòng `e:`, sửa tên thật (đừng đoán). Điểm dễ lệch: `compose.runOnUiThread` trả giá trị; `Feed.copy` có `important` không nằm trong constructor (hai bên đều 0).

- [ ] **Step 4: Commit test (chưa chạy trên máy)**

```bash
git add app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/FeedAddressEditDeviceTest.kt
git commit -m "test(front-02t): check on the phone's real Room that editing a source address keeps the right columns and the next sync uses it

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: Chạy trên Tecno (1 lượt, rồi 20, rồi 40) với watcher quảng cáo

**Files:** không sửa tệp nguồn. Dùng `/tmp/kj7_watch.sh <Lớp> <N>` (cổng, sao lưu, cài, N lượt, đo xoay + `AdActivity` từng lượt, khôi phục, gỡ gói test).

**Interfaces:** Consumes: Task 2 đã biên dịch (`app/build/outputs/apk/...`). Produces: số liệu lượt xanh/đỏ, có/không `AdActivity`, cho báo cáo.

- [ ] **Step 1: Kiểm công cụ còn tồn tại (`/tmp` có thể bị dọn)**

```bash
ls -la /tmp/kj7_gate.sh /tmp/kj7_backup.py /tmp/kj7_restore.py /tmp/kj7_watch.sh /tmp/kj7-root /tmp/kj7_mut_s.sh /tmp/kj7_s_batch.sh
```
Expected: đủ 7 mục. Thiếu: dừng và báo (không tự dựng lại cổng/sao lưu từ trí nhớ).

- [ ] **Step 2: Cổng, trong MỘT LỆNH RIÊNG**

```bash
zsh /tmp/kj7_gate.sh 2>&1 | head -2
```
Expected: dòng đầu `GATE OK: ...`. Khác: **dừng và hỏi owner**, không chạy bước 3.

- [ ] **Step 3: Một lượt thử (1 lượt, nền, chờ ALLDONE)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f /tmp/kj7-t-r1.log; ( zsh /tmp/kj7_watch.sh FeedAddressEditDeviceTest 1 > /tmp/kj7-t-r1.log 2>&1 ) > /dev/null 2>&1 &
for i in $(seq 1 100); do grep -q ALLDONE /tmp/kj7-t-r1.log 2>/dev/null && break; sleep 5; done; cat /tmp/kj7-t-r1.log
```
Expected: `run1: OK (4 tests) | adActivityOnTop=0 | after-run user=0 accel=1`, `restore_exit=0`, `RESTORE OK`, `datastore: now=9ca1c202… before=9ca1c202…`, `rotation: 1 0`, `GATE OK`, `ALLDONE`.

Nếu đỏ: đọc `/tmp/kj7-watch-run-1.log` (dòng `INSTRUMENTATION_STATUS: stack=`). **Không** thêm `sleep`, không tăng timeout. Phân loại: lỗi biên dịch/ khẳng định sai do hiểu nhầm API (sửa test, quay lại Task 2 Step 3) hay hành vi thật (dừng, systematic-debugging, hỏi owner). Sau đó kiểm Room sạch: rig đã `cleanUp`, và khôi phục byte-exact đã chạy.

- [ ] **Step 4: Cổng riêng, rồi 20 lượt**

```bash
zsh /tmp/kj7_gate.sh 2>&1 | head -1
```
Rồi (lệnh sau, riêng):
```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f /tmp/kj7-t-m1.log; ( zsh /tmp/kj7_watch.sh FeedAddressEditDeviceTest 20 > /tmp/kj7-t-m1.log 2>&1 ) > /dev/null 2>&1 &
for i in $(seq 1 115); do grep -q ALLDONE /tmp/kj7-t-m1.log 2>/dev/null && break; sleep 5; done
grep -E "^run[0-9]+:" /tmp/kj7-t-m1.log | awk '{ok=($0 ~ /Failures/)?"RED":"green"; ad=($0 ~ /adActivityOnTop=0/)?"noad":"AD"; c[ok" "ad]++} END{for(k in c) print k, c[k]}'
grep -E "ROTATION CHANGED|GATE-STOP|restore_exit|datastore|rotation:|ALLDONE" /tmp/kj7-t-m1.log
```
Expected: `green noad 20`, không `ROTATION CHANGED`, băm DataStore trùng. Quá 115×5 giây thì chạy lại lệnh chờ (script vẫn chạy nền); chỉ đọc kết quả khi có `ALLDONE`. Mỗi lượt đỏ: đọc nguyên nhân trong `/tmp/kj7-watch-run-<n>.log` và `/tmp/kj7-watch-top-<n>.txt` **trước khi** dùng. Đỏ **có** `AdActivity`: ghi nhận, tiếp tục. Đỏ **không** `AdActivity`: **dừng, hỏi owner**.

- [ ] **Step 5: Cổng riêng, rồi 20 lượt nữa (cộng thành 40)**

Như Step 4 nhưng log `/tmp/kj7-t-m2.log`. Sau đó cộng hai vòng:

```bash
for f in /tmp/kj7-t-m1.log /tmp/kj7-t-m2.log; do grep -E "^run[0-9]+:" $f; done | awk '{ok=($0 ~ /Failures/)?"RED":"green"; ad=($0 ~ /adActivityOnTop=0/)?"noad":"AD"; c[ok" "ad]++} END{for(k in c) print k, c[k]}'
```
Expected: ghi đủ bốn ô (xanh/đỏ × có/không quảng cáo). Chấp nhận: mọi lượt đỏ có `AdActivity`. Quy tắc "từ 2 đỏ trong 40 thì dừng" áp dụng cho đỏ **không** do quảng cáo.

- [ ] **Step 6: Kiểm máy sạch sau các vòng (chỉ đọc)**

```bash
S=115333744A005844
adb -s $S shell settings get system user_rotation; adb -s $S shell settings get system accelerometer_rotation
adb -s $S shell pm list packages com.mckimquyen.reader.test | wc -l
zsh /tmp/kj7_gate.sh 2>&1 | head -1
```
Expected: `0`, `1`, `0`, `GATE OK`. `user_rotation` khác 0 thì đặt lại về 0 (owner đã cho phép trên Tecno) và **nói rõ** trong báo cáo.

---

### Task 4: Mutation chạy trên máy (5 đột biến)

**Files:** tạm, không commit: `/tmp/kj7_mut_t.sh`, `/tmp/kj7_t_batch.sh`. Đột biến tạm trong `FeedOptionVM.kt` và `LocalRssSv.kt` (luôn khôi phục).

**Interfaces:** Consumes: `/tmp/kj7_mut_s.sh` (mẫu: tham số `<label> <file> <old> <new>`, `base` = không đổi) và `/tmp/kj7_s_batch.sh` (mẫu batch). Produces: bảng đột biến → tập test đỏ.

- [ ] **Step 1: Kiểm các mẫu đột biến duy nhất trong mã (mỗi mẫu đúng 1 lần)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
python3 - <<'EOF'
# -*- coding: utf-8 -*-
J = "app/src/main/java/com/mckimquyen/reader/"
vm = open(J + "ui/page/home/feed/drawer/feed/FeedOptionVM.kt", encoding="utf-8").read()
ls = open(J + "domain/sv/LocalRssSv.kt", encoding="utf-8").read()
pats = [
 (vm, "rssService.get().updateFeed(it.copy(url = address, sourceUrl = if (it.sourceUrl != null) address else null))"),
 (ls, "if ((feed.sourceUrl ?: feed.url) != didAddress) {"),
]
print([s.count(p) for s, p in pats])
EOF
```
Expected: `[1, 1]`. Khác 1: đọc lại tệp và sửa mẫu (không chạy batch).

- [ ] **Step 2: Dựng script từ mẫu 02s**

```bash
sed -e 's/SubscribeDialogRecreateDeviceTest/FeedAddressEditDeviceTest/g' -e 's/OK (2 tests)/OK (4 tests)/' -e 's/ok2/ok4/g' -e 's/kj7-s-mut-/kj7-t-mut-/g' -e 's/kj7-s-mutbackup/kj7-t-mutbackup/' /tmp/kj7_mut_s.sh > /tmp/kj7_mut_t.sh
grep -n "ok4\|OK (4\|kj7-t-\|FeedAddressEditDeviceTest" /tmp/kj7_mut_t.sh | head
```
Expected: dòng `TEST=...FeedAddressEditDeviceTest`, `ok4`, `OK (4 tests)`, các tệp `kj7-t-mut-*`.

Và `/tmp/kj7_t_batch.sh` (viết bằng heredoc, đúng hình dạng `/tmp/kj7_s_batch.sh`: đọc `cat /tmp/kj7_s_batch.sh` trước rồi đổi tên). Các dòng đột biến:

```
zsh $M base
zsh $M t1-only-url $VM 'rssService.get().updateFeed(it.copy(url = address, sourceUrl = if (it.sourceUrl != null) address else null))' 'rssService.get().updateFeed(it.copy(url = address))'
zsh $M t2-always-source $VM 'sourceUrl = if (it.sourceUrl != null) address else null))' 'sourceUrl = address))'
zsh $M t3-keeps-old-source $VM 'sourceUrl = if (it.sourceUrl != null) address else null))' 'sourceUrl = it.sourceUrl))'
zsh $M t4-no-did-upgrade $LS 'if ((feed.sourceUrl ?: feed.url) != didAddress) {' 'if (false) {'
zsh $M t5-renames-feed $VM 'it.copy(url = address, sourceUrl = if (it.sourceUrl != null) address else null)' 'it.copy(name = address, url = address, sourceUrl = if (it.sourceUrl != null) address else null)'
zsh $M base
```
với `VM=$J/ui/page/home/feed/drawer/feed/FeedOptionVM.kt` và `LS=$J/domain/sv/LocalRssSv.kt`; sao lưu `cp -p` hai tệp ở đầu và `cmp` ở cuối; kiểm `git diff --stat -- app/src/main`; khôi phục + băm DataStore + gỡ gói test + `ALLDONE` như mẫu.

Tập test đỏ mong đợi (kiểm **tự động**, so khớp chính xác tên test):

| Đột biến | Test đỏ mong đợi | Vì sao |
|---|---|---|
| t1 chỉ ghi `url` | `aNewBlueskySource_changesBothColumns_andTheNextSyncAsksForTheNewAddress` | `sourceUrl` vẫn là DID cũ |
| t2 luôn ghi `sourceUrl` | `anRssSource_changesOnlyItsUrl` | RSS thường bị gắn `sourceUrl` |
| t3 giữ `sourceUrl` cũ | `aNewBlueskySource_changesBothColumns_andTheNextSyncAsksForTheNewAddress` | giống t1 (cùng điểm chết, ghi rõ) |
| t4 bỏ nâng cấp DID (`LocalRssSv`) | `aNewBlueskySource_changesBothColumns_andTheNextSyncAsksForTheNewAddress` (đỏ ở `synced.sourceUrl`, **sau** lần đồng bộ) | sau đồng bộ `sourceUrl` vẫn là handle |
| t5 đổi `name` | `everyOtherField_isKept` (chỉ test này) | chỉ test này so mọi trường |

- [ ] **Step 3: Cổng riêng, rồi chạy batch nền, chờ ALLDONE**

```bash
zsh /tmp/kj7_gate.sh 2>&1 | head -1
```
Rồi:
```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f /tmp/kj7-t-batch.log; ( zsh /tmp/kj7_t_batch.sh > /tmp/kj7-t-batch.log 2>&1 ) > /dev/null 2>&1 &
for i in $(seq 1 115); do grep -q ALLDONE /tmp/kj7-t-batch.log 2>/dev/null && break; sleep 5; done; cat /tmp/kj7-t-batch.log
```
Không sửa `FeedOptionVM.kt`/`LocalRssSv.kt` cho tới `ALLDONE`. Mỗi đột biến mất vài phút (build + chạy); nếu hết 115×5 giây mà chưa `ALLDONE`, chạy lại **chỉ** vòng chờ.

- [ ] **Step 4: Đối chiếu tự động và kiểm khôi phục**

Mỗi dòng `tN-...: ... failing=[...]` phải trùng **đúng** tập mong đợi ở bảng; `base` hai lần `ok4=1`. Cuối batch: `final vm: identical`, `final ls: identical`, `git diff main: 0 lines`, `restore_exit=0`, băm DataStore trùng, `rotation: 1 0`, `GATE OK`. **Mutant sống sót** (test không đỏ): test yếu, thêm khẳng định (quay lại Task 2) rồi chạy lại batch. Một đột biến làm thêm test khác đỏ ngoài dự kiến: đọc lý do và sửa bảng mong đợi hoặc test, ghi trong báo cáo.

Kiểm thêm: t4 đỏ phải **ở dòng khẳng định `synced.sourceUrl`** (không ở bước sau lưu), xem `at=[...FeedAddressEditDeviceTest.kt:<dòng>]` và đối chiếu với dòng tệp.

---

### Task 5: JVM, kiểm phạm vi, tài liệu, commit

**Files:**
- Modify: `doc/feature.md` (dòng 02p và dòng 02t mới), `docs/superpowers/specs/2026-11-18-front-02t-feed-address-edit-device-test-design.md` (mục "Điều chỉnh khi thực thi")
- Create: `doc/task/done/FRONT-02t_feed-address-edit-device-test_DONE.md`

- [ ] **Step 1: JVM full suite đo lại (nền, chờ `.exit`)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f /tmp/t-jvm.exit; ( ./gradlew testDevDebugUnitTest --rerun-tasks assembleDevDebug assembleDevDebugAndroidTest > /tmp/t-jvm.log 2>&1; echo $? > /tmp/t-jvm.exit ) > /dev/null 2>&1 &
for i in $(seq 1 115); do test -f /tmp/t-jvm.exit && break; sleep 5; done; cat /tmp/t-jvm.exit; tail -3 /tmp/t-jvm.log
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
Expected: `0`, `BUILD SUCCESSFUL`, 0 lỗi (số test đo lúc chạy, ghi đúng số đó). Chưa xong sau 115×5 giây: chạy lại chỉ vòng chờ.

- [ ] **Step 2: Kiểm phạm vi diff (không production)**

```bash
git status --short; git diff --stat -- app/src/main app/build.gradle app/schemas | tail -2
git diff --name-only HEAD~2 HEAD
```
Expected: không có tệp trong `app/src/main`, `app/build.gradle`, `app/schemas`, manifest; chỉ hai tệp androidTest (rig và test mới) trong hai commit trước.

- [ ] **Step 3: Viết báo cáo, cập nhật `doc/feature.md` và spec**

Báo cáo `doc/task/done/FRONT-02t_feed-address-edit-device-test_DONE.md` (tiếng Việt đơn giản) gồm: điều test chứng minh; bảng lượt (1 + 20 + 20: xanh/đỏ × có/không `AdActivity`, nguyên nhân từng lượt đỏ); bảng mutation 5 hàng với tập test đỏ **đo được**; băm DataStore trước/sau; `user_rotation` sau các vòng (nói rõ nếu phải đặt lại về 0); JVM số **đo lại**; không kiểm gì (bấm giữ, `FeedOptionDrawer`, `ChangeUrlDialog`, WEB/NOSTR, Fever/Google Reader, chặn địa chỉ sai/trùng, lint không quét `androidTest`); phát hiện mới nếu có.

`doc/feature.md`: dòng 02p thêm "đã kiểm trên máy (ViewModel + Room + đồng bộ giả) bằng FRONT-02t; **chưa** kiểm giao diện bấm giữ"; thêm dòng FRONT-02t sau dòng FRONT-02s (✅ ngày, đường dẫn báo cáo, số liệu thật, nói thẳng phần chưa kiểm).

Spec: nối mục "Điều chỉnh khi thực thi" (bảng "Điều chỉnh so với spec" của plan, cộng mọi điều chỉnh phát sinh khi chạy).

- [ ] **Step 4: Commit tài liệu**

```bash
git add doc docs
git commit -m "docs(front-02t): report that editing a source address was checked on the phone's real Room and sync

Co-Authored-By: Claude Code <noreply@anthropic.com>"
git log --oneline | head -4; git status --short
```
Expected: chỉ `.idea/workspace.xml` còn sửa (không commit).

---

## Self-review

1. **Spec coverage:** mục tiêu và 4 test → Task 2; rig `profileActors` (spec mục 1) → Task 1; ViewModel không `showDrawer`, reflection (mục 2) → Task 2; gieo Room thật + `waitUntil` (mục 3) → Task 2 (`changeAddress`, `waitFor`); `@Before` kiểm tài khoản Local, `@After` dọn (mục 5, 6) → Task 2; an toàn thiết bị (cổng, sao lưu, DataStore, xoay) → Global Constraints + Task 3; 20 rồi 40 lượt + quảng cáo → Task 3; đột biến (t1 đến t4 của spec, thêm t5) → Task 4; JVM đo lại, báo cáo, `feature.md` → Task 5; nói thẳng phần không kiểm → Global Constraints + Task 5.
2. **Placeholder scan:** không còn "TBD"/"TODO"; mọi bước mã có mã đầy đủ. Ngoại lệ có ý thức: `/tmp/kj7_t_batch.sh` được mô tả là "đúng hình dạng `/tmp/kj7_s_batch.sh` đổi tên" kèm các dòng đột biến đầy đủ, vì khung batch dài và đã tồn tại; bước đầu của Task 4 Step 2 yêu cầu `cat` mẫu trước.
3. **Nhất quán tên:** `profileActors()`, `seed(..., icon, isNotification, isFullContent)`, `changeAddress`, `open`, `newViewModel`, `stored`, `UI_STATE_FIELD = "_feedOptionUiState"`, `HANDLE_OLD/NEW`, `DID_OLD/NEW` khớp giữa Task 1, 2 và 4. Test: tên 4 test khớp bảng ở Task 4. Rủi ro đã biết: `Feed.equals` so cả `important` (không nằm trong constructor, mặc định 0 ở cả hai phía); `ViewModelProvider` phải tạo trên luồng chính (đã bọc `runOnUiThread`).
4. **Rủi ro còn lại (nói thẳng):** t1 và t3 có cùng điểm chết nên không phân biệt được hai đột biến đó; khẳng định `profileActors() == listOf(HANDLE_NEW)` giả định handle viết thường (đúng với hằng số dùng); quảng cáo ~7% ở 02s (02e, 02r đo 0/20) cần đo lại cho test này.
