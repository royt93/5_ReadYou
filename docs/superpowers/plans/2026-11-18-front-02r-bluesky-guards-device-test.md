# FRONT-02r — Test máy thật: chặn trùng nguồn Bluesky và nâng cấp DID khi đồng bộ (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (hoặc subagent-driven-development) để thực thi plan này từng task. Các bước dùng checkbox `- [ ]`.

**Goal:** Chứng minh trên Tecno KJ7 (Room thật của Android, DAO thật, `LocalRssSv` và `SubscribeViewModel` thật) rằng 02i, 02n, 02o chặn đúng đăng ký trùng một tài khoản Bluesky, và 02q nâng cấp nguồn cũ lên lưu DID khi đồng bộ thành công.

**Architecture:** Một `ComponentActivity` của test lấy `SubscribeViewModel` qua `ViewModelProvider` (dịch vụ thật đọc từ `RApp`), nhưng `BlueskyApiClient` trỏ vào máy chủ Bluesky giả trên `127.0.0.1` (`MockWebServer`). Nguồn thử gieo vào Room thật, nhóm tạm `front02r_probe_group`, dọn sau mỗi test. Test **gọi `inputLink(...)` và `search()` trực tiếp**, không gõ qua giao diện, không `MainActivity`. Đồng bộ dùng `LocalRssSvFactory.build` của 02h. Không đổi mã production.

**Tech Stack:** Compose UI test không cần (chỉ `ViewModelProvider` và `runBlocking`), `mockwebserver3` (đã có ở androidTest từ 02e), Room thật, `am instrument`, adb, Python 3.9.

## Global Constraints

- Nhánh `dev`; không worktree, không push, không PR, không commit `.idea`.
- Không đổi mã production, manifest, chuỗi, Room (v22), DataStore, `app/build.gradle`; không dependency mới (`mockwebserver3-junit4` đã có ở androidTest). Chỉ **mutation tạm** trên `SubscribeViewModel.kt` và `LocalRssSv.kt` (và `AbstractRssRepository.kt`), luôn khôi phục + `cmp`; `git diff app/src/main` phải rỗng khi kết thúc.
- Thiết bị **CHỈ Tecno KJ7 `115333744A005844`** (owner: "dùng tecno"). Chỉ `adb -s 115333744A005844`; không `connected*`; chỉ `am instrument --user 0 -w -r -e class … com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner`; force-stop chỉ `com.mckimquyen.reader`; **không** động tới Pixel. Không MockK, không tọa độ, **không mạng thật** (chỉ `http://127.0.0.1`), không xoay màn hình (không `UiAutomation.setRotation`).
- Cổng `/tmp/kj7_gate.sh` phải in `GATE OK` **trong một lệnh RIÊNG** trước mỗi lần khởi chạy (bài học: gộp cổng và khởi chạy trong cùng lệnh từng chạy sau khi cổng báo dừng). Nếu cổng dừng vì app lạ ở foreground, màn khóa hoặc `user_rotation` lệch: **dừng và hỏi owner**.
- Sao lưu và khôi phục **byte-exact 6 tệp** (`/tmp/kj7_backup.py`, `/tmp/kj7_restore.py`); băm DataStore `9ca1c202050c258b4aac9a063e27def350d737fb` phải trùng sau mỗi vòng; xoay `1 0` trước và sau; không xóa DataStore. Test ghi tạm vào Room thật của Tecno (212 bài, 1 nguồn thật) **chỉ** bằng dòng có tiền tố `front02r_probe_`.
- Gradle chạy NỀN, output ra file, chờ `.exit`/`ALLDONE`; "completed" của lệnh nền chỉ là lệnh khởi chạy. Đọc XML/log chỉ sau khi `.exit` tồn tại.
- Mutation chạy trên máy bằng một script nền duy nhất, **không sửa tệp đột biến cho tới `ALLDONE`**, `cmp` sau mỗi đợt.
- zsh: không `echo ====`; `grep -r` phải quote mẫu và không dùng `--include`; sửa tệp bằng python; Python 3.9 cần `# -*- coding: utf-8 -*-` và `open(..., encoding="utf-8")`.
- R5: không magic number (hằng số có tên); test bắt buộc kèm; không báo "done" khi chưa dọn. R2: cập nhật `doc/feature.md`.
- Không dùng số test JVM trong plan làm bằng chứng: đo lại lúc thực thi.
- Nói thẳng phần không kiểm: 02p, mạng thật Bluesky, `MainActivity`/`HomeEntry` và nút "Đăng ký" thật, giao diện hộp thoại (chỉ state ViewModel và dữ liệu Room), `SyncWorker` thật, nguồn đã đổi handle từ trước 02q, nguồn OPML, lint không quét `androidTest`.

## Điều chỉnh so với spec (đã đọc code thật, 2026-11-18)

1. **`BackgroundSyncRig` (02h) cố định hồ sơ giả** (`BlueskyDeviceFixtures.PROFILE_BODY` với một `DID` và một `HANDLE`) và gieo hai nguồn `front02h_probe_*`. Test 02r cần hồ sơ giả **thay đổi theo test** (cùng một DID với handle mới, hoặc DID khác), nên **không tái dùng rig 02h nguyên vẹn**: viết `BlueskyGuardRig` mới, nhỏ, **tái dùng** `LocalRssSvFactory.build`, `BlueskyDeviceFixtures.post/feedBody` và cách dọn nhóm của 02h. Đổi tiền tố thành `front02r_probe_` để hai bộ không đụng nhau.
2. **`subscribeViewModelFactory(relayUrl)` (02e) dựng `BlueskyApiClient` thật** (`API_BASE` công khai). Test 02r cần máy chủ giả. Thêm một **hàm mới** `subscribeViewModelFactoryWith(blueskyApi: BlueskyApiClient)` trong tệp `SubscribeViewModelFactory.kt` (androidTest) và để hàm cũ **không đổi** (nó gọi hàm mới với `BlueskyApiClient(app.okHttpClient, Dispatchers.IO)` và relay như cũ). Nhờ vậy `SubscribeNostrPreviewRecreateDeviceTest` không bị đụng. `NostrRelayClient` vẫn cần (tham số bắt buộc của ViewModel): trỏ vào relay không dùng (`listOf("ws://127.0.0.1:1")`, `requireSecure=false`), không bao giờ được gọi vì nhánh Bluesky không chạm nó.
2b. **Chặn mạng ngoài 127.0.0.1 ở mọi đường của `search()` (điều chỉnh sau khi đọc dòng 301 đến 340):** khi API không dùng được, `search()` rơi về `rssHelper.searchFeed(...)` và `newsletterUrlResolver.resolveToRssUrl(...)`, cả hai gọi **mạng thật** (`bsky.app`). `subscribeViewModelFactoryWith` nhận thêm `rssHelper` và `newsletterUrlResolver` tùy chọn; rig truyền một `RssHelper(app, Dispatchers.IO, guardedClient)` và `NewsletterUrlResolver(guardedClient)` với `guardedClient = app.okHttpClient.newBuilder().addInterceptor(RefuseOutsideLoopback).build()`. Interceptor **ném `IOException`** cho mọi host khác `127.0.0.1` và đếm số lần bị chặn (`blockedRequests()`); mọi test khẳng định `blockedRequests() == 0` trừ test có ý chạm đường đó (không có test như vậy trong plan này). Như vậy một chạy sai kịch bản **không bao giờ ra mạng thật**, chỉ làm test đỏ có lý do rõ.
3. **Máy chủ giả phải trả hai loại yêu cầu** (`getProfile` rồi `getAuthorFeed`; xem `BlueskyApiClient.read`). Rig 02h phân biệt bằng `request.requestLine.contains("getProfile")`; plan dùng đúng cách đó và thêm **đếm yêu cầu** (số lần hỏi `getProfile`) để chứng minh "không hỏi máy chủ" ở 02i (a).
4. **`getProfile` phải trả `did` và `handle`** vì `BlueskyApiClient` đọc `did` (bắt buộc bắt đầu `did:`) và `handle`; `otherNameAddresses` của 02n dùng cả hai. Hồ sơ giả của mỗi test do test đặt (`rig.profile(did, handle)`).
5. **02i (a) "không hỏi máy chủ" chỉ đúng với nguồn dạng API**: `search()` kiểm `isFeedExist(bsky:<tên gõ>)` và `isFeedExist(<RSS cũ>)` **trước** khi gọi API (dòng 217), nên gõ lại đúng tên đã lưu trả "Đã đăng ký" với 0 lần hỏi. Các ca 02n/02o chỉ biết tên thứ hai **sau** `getProfile` nên có **1 lần** `getProfile` (và **không** `getAuthorFeed`? Không: `read` gọi `getAuthorFeed` ngay sau `getProfile` trước khi trả `Found`, nên mỗi ca đó có 1 `getProfile` và 1 `getAuthorFeed`). Plan khẳng định đúng số đó, đọc từ mã chứ không đoán.
6. **`updateSourceUrlIfUnchanged` trả `Int`** (0 hoặc 1) nhưng `LocalRssSv.queryBlueskyArticles` **không lộ** giá trị đó (nó nằm trong `runCatching`). Test 02q (c) đọc lại cột `sourceUrl` qua `feedDao.queryById` và kiểm không đổi, không đếm số dòng trả về.
7. **Tài khoản hiện hành phải là Local** để nhánh Bluesky của `search()` chạy (`context.currentAccountType == AccountType.Local.id`). Máy Tecno đã dùng tài khoản Local (id 1) ở mọi đợt trước; `@Before` kiểm và báo lỗi rõ nếu không.
9. **Cách gõ một DID (đọc `SocialFeedUrlResolver.resolve`):** `@did:plc:...` **không** được nhận; chỉ **link** `https://bsky.app/profile/did:plc:.../rss` hoặc `.../profile/did:plc:...` (DID giữ đúng chữ hoa thường). Test 02n gõ DID bằng đúng dạng link đó (hằng số `profileLinkOf(did)`), không bằng `@did:...`.
10. **`toArticles` chỉ giữ bài có `author.did == profileDid`**, nên máy chủ giả phải trả bài có `author.did` **trùng DID của hồ sơ giả**. `BlueskyDeviceFixtures.post(...)` cố định tác giả là `BlueskyDeviceFixtures.DID`; test dùng DID khác (ca "DID khác") phải tự dựng bài với tác giả đúng, nếu không `articles` rỗng và `search()` rơi về luồng RSS cũ (gọi mạng thật!). **Rủi ro mạng thật:** nếu `articles` rỗng, `search()` chạy tiếp `rssHelper.searchFeed(...)` tới `bsky.app` thật. Rig tránh bằng cách (i) luôn trả ít nhất một bài đúng tác giả, (ii) mọi test đọc `rig.feedRequests()` để chắc đã đi đường API, và (iii) chạy trên máy với mạng của Tecno nhưng **bất kỳ yêu cầu ra ngoài `127.0.0.1` là lỗi thiết kế** (xem Self-review).
8. **`search()` chạy trong `viewModelScope`** (`viewModelScope.launch`), nên test phải **chờ state** (`subscribeUiState`), không kỳ vọng đồng bộ. Trạng thái "xong" là `feed != null` (xem trước) hoặc `errorMessage` khác rỗng (báo trùng/lỗi), và `lockLinkInput == false` khi lỗi.

## Cấu trúc file

| File | Việc |
|---|---|
| Modify `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModelFactory.kt` | thêm `subscribeViewModelFactoryWith(blueskyApi)`; hàm cũ giữ nguyên hành vi |
| Create `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/BlueskyGuardRig.kt` | máy chủ Bluesky giả (đếm yêu cầu, hồ sơ do test đặt), gieo/đọc/dọn nguồn thử, dựng `LocalRssSv` và `SubscribeViewModel` |
| Create `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/BlueskyGuardsDeviceTest.kt` | 12 test (02i x2, 02n x2, 02o x3, 02q x4) + dọn dẹp |
| Tạm, **không commit**: `/tmp/kj7_run.sh` (sẵn có), `/tmp/kj7_mut.sh`, `/tmp/kj7_r02_batch.sh` | vòng chạy và đột biến trên máy (tham số hóa lớp test) |
| Modify `doc/feature.md`, spec (mục "Điều chỉnh khi thực thi") | tài liệu |
| Create `doc/task/done/FRONT-02r_bluesky-guards-device-test_DONE.md` | báo cáo |

---

### Task 1: Factory có tham số Bluesky và `BlueskyGuardRig` (biên dịch trên máy tính, chưa chạm Tecno)

**Files:**
- Modify: `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModelFactory.kt`
- Create: `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/BlueskyGuardRig.kt`

**Interfaces:**
- Produces: `fun subscribeViewModelFactoryWith(relayUrl: String = UNUSED_RELAY_URL, blueskyApi: BlueskyApiClient?, rssHelper: RssHelper? = null, newsletterUrlResolver: NewsletterUrlResolver? = null): ViewModelProvider.Factory`; `class BlueskyGuardRig : AutoCloseable` với `fun profile(did: String, handle: String)`, `fun failProfileWith500()`, `fun profileRequests(): Int`, `fun feedRequests(): Int`, `fun blockedRequests(): Int`, `fun realRowCounts(): Pair<Long, Long>`, `val groupId: String`, `val viewModelFactory: ViewModelProvider.Factory`, `suspend fun seed(id: String, url: String, sourceUrl: String?)`, `suspend fun stored(id: String): Feed?`, `suspend fun syncOnce(feedId: String): SyncResult`, `suspend fun cleanUp()`, `fun probeRowCount(): Long`.

- [ ] **Step 1: Đọc lại API thật (một lần cuối trước khi chép)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
J=app/src/main/java/com/mckimquyen/reader
grep -n "internal constructor" -A6 $J/infrastructure/social/bluesky/BlueskyApiClient.kt | head -8
grep -n "fun build(blueskyApi\|class LocalRssSvFactory\|data class SyncResult\|fun syncOnce\|fun cleanUp\|fun counts\|fun probeRowCount" app/src/androidTest/java/com/mckimquyen/reader/domain/sv/BackgroundSyncRig.kt
grep -n "fun queryById\|fun deleteByFeedId\|fun queryByLink\|fun queryBySourceUrl" $J/domain/repository/FeedDao.kt $J/domain/repository/FeedHealthDao.kt $J/domain/repository/GroupDao.kt
grep -n "val subscribeUiState\|fun inputLink\|fun search\|fun init" $J/ui/page/home/feed/subs/SubscribeViewModel.kt
```
Expected: constructor `internal` nhận `(OkHttpClient, CoroutineDispatcher, String baseUrl, Long timeoutMs, Long maxBodyBytes)`; `LocalRssSvFactory.build(blueskyApi, nostrRelay)`, `SyncResult`, `syncOnce` có trong rig 02h; `queryById` ở `FeedDao`. **Nếu một dòng nào không khớp: dừng, sửa plan, không đoán.**

- [ ] **Step 2: Thêm hàm factory mới, giữ hàm cũ nguyên hành vi**

Dùng python (không sửa tay):

```python
# -*- coding: utf-8 -*-
p = 'app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModelFactory.kt'
s = open(p, encoding='utf-8').read()
old_head = 'fun subscribeViewModelFactory(relayUrl: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {'
assert s.count(old_head) == 1
old_body = 'blueskyApiClient = BlueskyApiClient(app.okHttpClient, Dispatchers.IO),'
assert s.count(old_body) == 1
s = s.replace(old_head, '''fun subscribeViewModelFactory(relayUrl: String): ViewModelProvider.Factory =
    subscribeViewModelFactoryWith(relayUrl = relayUrl, blueskyApi = null)

/**
 * The same, but the Bluesky client is the one you hand in (a fake server on 127.0.0.1); null keeps the real public API client.
 * The Nostr relay is never called by the Bluesky path, so [relayUrl] may point anywhere unused.
 */
fun subscribeViewModelFactoryWith(
    relayUrl: String = UNUSED_RELAY_URL,
    blueskyApi: BlueskyApiClient?,
    rssHelper: RssHelper? = null,
    newsletterUrlResolver: NewsletterUrlResolver? = null,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {''', 1)
s = s.replace(old_body, 'blueskyApiClient = blueskyApi ?: BlueskyApiClient(app.okHttpClient, Dispatchers.IO),', 1)
old_rss = 'rssHelper = app.rssHelper,'
assert s.count(old_rss) == 1
s = s.replace(old_rss, 'rssHelper = rssHelper ?: app.rssHelper,', 1)
old_nl = 'newsletterUrlResolver = NewsletterUrlResolver(),'
assert s.count(old_nl) == 1
s = s.replace(old_nl, 'newsletterUrlResolver = newsletterUrlResolver ?: NewsletterUrlResolver(),', 1)
imp = 'import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedHttpClient\n'
assert s.count(imp) == 1
s = s.replace(imp, 'import com.mckimquyen.reader.infrastructure.rss.RssHelper\n' + imp, 1)
s = s.replace('private const val FAKE_RELAY_TIMEOUT_MS = 10_000L\n', 'private const val FAKE_RELAY_TIMEOUT_MS = 10_000L\nprivate const val UNUSED_RELAY_URL = "ws://127.0.0.1:1"\n', 1)
open(p, 'w', encoding='utf-8').write(s)
```
Kiểm: `grep -n "subscribeViewModelFactory\|UNUSED_RELAY_URL\|blueskyApi" $FILE`. Hàm cũ `subscribeViewModelFactory(relayUrl)` vẫn tồn tại với cùng chữ ký, và gọi hàm mới với `blueskyApi = null` ⇒ dựng `BlueskyApiClient` thật như trước (`SubscribeNostrPreviewRecreateDeviceTest` không đổi hành vi).

- [ ] **Step 3: Viết `BlueskyGuardRig.kt`**

```kotlin
package com.mckimquyen.reader.ui.page.home.feed.subs

import androidx.lifecycle.ViewModelProvider
import com.mckimquyen.reader.RApp
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.domain.sv.BlueskyDeviceFixtures
import com.mckimquyen.reader.domain.sv.LocalRssSv
import com.mckimquyen.reader.domain.sv.LocalRssSvFactory
import com.mckimquyen.reader.domain.sv.SyncResult
import com.mckimquyen.reader.infrastructure.newsletter.NewsletterUrlResolver
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyApiClient
import com.mckimquyen.reader.infrastructure.social.nostr.NostrRelayClient
import com.mckimquyen.reader.ui.ext.currentAccountId
import kotlinx.coroutines.Dispatchers
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

private const val GUARD_TIMEOUT_MS = 5_000L
private const val GUARD_MAX_BODY_BYTES = 512 * 1024L
private const val HTTP_SERVER_ERROR = 500

/**
 * A fake Bluesky server on 127.0.0.1, the phone's real Room, and the real SubscribeViewModel and LocalRssSv wired to them.
 * Every row this rig writes has an id starting with [PROBE_PREFIX]; [cleanUp] deletes them all and [probeRowCount] proves it.
 */
class BlueskyGuardRig : AutoCloseable {
    private val app: RApp = LocalRssSvFactory.app()
    private val db = app.androidDatabase
    val accountId: Int = app.currentAccountId

    private val profileCalls = AtomicInteger(0)
    private val feedCalls = AtomicInteger(0)
    @Volatile private var profileBody: String = BlueskyDeviceFixtures.PROFILE_BODY
    @Volatile private var feedBodyText: String = authorFeedBody(BlueskyDeviceFixtures.DID, BlueskyDeviceFixtures.HANDLE)
    @Volatile private var profileFails = false

    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.requestLine.contains("getProfile")) {
                    profileCalls.incrementAndGet()
                    if (profileFails) MockResponse().setResponseCode(HTTP_SERVER_ERROR) else json(profileBody)
                } else {
                    feedCalls.incrementAndGet()
                    json(feedBodyText)
                }
        }
        start()
    }

    private val blueskyApi = BlueskyApiClient(
        app.okHttpClient, Dispatchers.IO, server.url("/").toString().trimEnd('/'), GUARD_TIMEOUT_MS, GUARD_MAX_BODY_BYTES,
    )
    private val blocked = AtomicInteger(0)

    /** The app's own client, except that any host other than 127.0.0.1 is refused and counted: nothing here may reach the network. */
    private val guardedClient: OkHttpClient = app.okHttpClient.newBuilder().addInterceptor(
        Interceptor { chain ->
            if (chain.request().url.host == LOOPBACK_HOST) {
                chain.proceed(chain.request())
            } else {
                blocked.incrementAndGet()
                throw IOException("front02r: a request to a host other than 127.0.0.1 was refused")
            }
        },
    ).build()

    val viewModelFactory: ViewModelProvider.Factory = subscribeViewModelFactoryWith(
        blueskyApi = blueskyApi,
        rssHelper = RssHelper(app, Dispatchers.IO, guardedClient),
        newsletterUrlResolver = NewsletterUrlResolver(guardedClient),
    )
    private val service: LocalRssSv = LocalRssSvFactory.build(
        blueskyApi, NostrRelayClient(app.okHttpClient, Dispatchers.IO, listOf("ws://127.0.0.1:1"), false, GUARD_TIMEOUT_MS),
    )
    private val filters = LocalRssSvFactory.smartFilterManager(app)
    val groupId = "${PROBE_PREFIX}group"

    /** What the fake profile endpoint answers next: this account's DID and the handle the server names. */
    fun profile(did: String, handle: String) {
        profileFails = false
        profileBody = """{"did":"$did","handle":"$handle","displayName":"Probe","avatar":"https://cdn.bsky.app/img/avatar/probe@jpeg"}"""
        // The app keeps only posts whose author DID is the profile's DID, so the fake feed must be written by that DID.
        feedBodyText = authorFeedBody(did, handle)
    }

    fun failProfileWith500() {
        profileFails = true
    }

    fun profileRequests(): Int = profileCalls.get()
    fun feedRequests(): Int = feedCalls.get()

    /** Requests the guarded client refused because they were not for 127.0.0.1; must stay 0. */
    fun blockedRequests(): Int = blocked.get()

    suspend fun createGroup() {
        db.groupDao().insert(Group(id = groupId, name = "front02r probe", accountId = accountId))
    }

    /** One stored Bluesky source of the probe group; [sourceUrl] null means "same as url" like a source saved before FRONT-02o. */
    suspend fun seed(id: String, url: String, sourceUrl: String?, sourceType: String = Feed.BLUESKY_SOURCE_TYPE) {
        db.feedDao().insert(
            Feed(
                id = "$PROBE_PREFIX$id", name = "Bluesky · Probe", icon = null, url = url, sourceUrl = sourceUrl,
                groupId = groupId, accountId = accountId, isNotification = false, sourceType = sourceType,
            ),
        )
    }

    suspend fun stored(id: String): Feed? = db.feedDao().queryById("$PROBE_PREFIX$id")

    /** The same three steps one background sync round makes for ONE feed (copied from the 02h rig, not shared). */
    suspend fun syncOnce(feed: Feed): SyncResult {
        val outcome = service.syncSingleFeed(feed)
        val inserted = if (outcome == null) emptyList() else db.articleDao().insertListIfNotExist(filters.applyRules(outcome.articles))
        return SyncResult(outcome, inserted)
    }

    fun articleCountOf(feedId: String): Int = scalar("SELECT COUNT(*) FROM article WHERE feedId = '$PROBE_PREFIX$feedId'").toInt()

    /** Any row anywhere whose id starts with the probe prefix; must be 0 after cleanUp. */
    fun probeRowCount(): Long = listOf(
        "SELECT COUNT(*) FROM feed WHERE id LIKE '$PROBE_PREFIX%'",
        "SELECT COUNT(*) FROM \"group\" WHERE id LIKE '$PROBE_PREFIX%'",
        "SELECT COUNT(*) FROM feed_health_record WHERE feedId LIKE '$PROBE_PREFIX%'",
        "SELECT COUNT(*) FROM article WHERE feedId LIKE '$PROBE_PREFIX%'",
    ).sumOf { scalar(it) }

    /** Feed and article rows that are NOT probe rows: the owner's real data; must be the same before and after every test. */
    fun realRowCounts(): Pair<Long, Long> = Pair(
        scalar("SELECT COUNT(*) FROM feed WHERE id NOT LIKE '$PROBE_PREFIX%'"),
        scalar("SELECT COUNT(*) FROM article WHERE feedId NOT LIKE '$PROBE_PREFIX%'"),
    )

    private fun scalar(sql: String): Long = db.openHelper.writableDatabase.query(sql).use { c ->
        c.moveToFirst()
        c.getLong(0)
    }

    /** Deleting the group cascades to its feeds and their articles; the health rows have no foreign key, so they go first. */
    suspend fun cleanUp() {
        val feeds = db.openHelper.writableDatabase.query("SELECT id FROM feed WHERE id LIKE '$PROBE_PREFIX%'").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }
        feeds.forEach { db.feedHealthDao().deleteByFeedId(it) }
        db.groupDao().queryById(groupId)?.let { db.groupDao().delete(it) }
    }

    override fun close() {
        server.shutdown()
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    companion object {
        const val PROBE_PREFIX = "front02r_probe_"
        private const val LOOPBACK_HOST = "127.0.0.1"

        /** One own post written by [did]; the fixtures' own post is fixed to another DID, which would be dropped here. */
        fun authorFeedBody(did: String, handle: String): String =
            """{"feed":[{"post":{"uri":"at://$did/app.bsky.feed.post/$RKEY","cid":"c$RKEY","author":{"did":"$did","handle":"$handle"},""" +
                """"record":{"${'$'}type":"app.bsky.feed.post","text":"front02r own post","createdAt":"2026-11-10T08:00:00.000Z"}}}]}"""
        private const val RKEY = "3front02r"
    }
}
```
Lưu ý khi chép: `LocalRssSvFactory`, `SyncResult` nằm ở gói `com.mckimquyen.reader.domain.sv` và **công khai** (kiểm ở Step 1); `ViewModelProvider.Factory` của rig đã trỏ máy chủ giả.

- [ ] **Step 4: Biên dịch (nền, output ra file)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f /tmp/f30-build.exit
( ./gradlew assembleDevDebug assembleDevDebugAndroidTest > /tmp/f30-build.txt 2>&1; echo $? > /tmp/f30-build.exit ) > /dev/null 2>&1 &
```
Chờ `/tmp/f30-build.exit`; `cat` mã thoát; `grep -n "^e: " /tmp/f30-build.txt | head`. Expected `0`. Lỗi thường gặp: `queryById`/`deleteByFeedId` có chữ ký khác (đọc lại ở Step 1), `LocalRssSvFactory` không công khai (đọc rồi báo, **không** đổi mã production). Không thêm `sleep`, không tăng timeout để chữa lỗi.

- [ ] **Step 5: Không commit ở đây** (commit cùng Task 4, sau khi có test xanh và mutation).

---

### Task 2: Mười test thiết bị (biên dịch trên máy tính, chưa chạm Tecno)

**Files:**
- Create: `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/BlueskyGuardsDeviceTest.kt`

**Interfaces:**
- Consumes: `BlueskyGuardRig` (Task 1), `BlueskyDeviceFixtures.DID/OTHER_DID/HANDLE`, `SubscribeViewModel.inputLink/search/subscribeUiState`, `BlueskyFeedMapper.feedUrl/rssUrl`.
- Produces: lớp `BlueskyGuardsDeviceTest` với 12 test (11 hành vi + 1 dọn dẹp).

- [ ] **Step 1: Đọc lại API thật (một lần cuối trước khi chép)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
J=app/src/main/java/com/mckimquyen/reader
grep -n "fun feedUrl\|fun rssUrl" $J/infrastructure/social/bluesky/BlueskyFeedMapper.kt
grep -n "const val FEED_URL_PREFIX\|const val FEED_NAME_PREFIX" $J/infrastructure/social/bluesky/BlueskyFeedMapper.kt
grep -n 'name="already_subscribed"' app/src/main/res/values/strings.xml
grep -n "private val DID\|DID.matches" $J/infrastructure/social/SocialFeedUrlResolver.kt
```
Expected: `feedUrl` và `rssUrl` công khai; `FEED_URL_PREFIX = "bsky:"`, `FEED_NAME_PREFIX = "Bluesky · "`; chuỗi `Already subscribed`; DID chỉ nhận qua link (điều chỉnh 9). Nếu khác: dừng, sửa plan.

- [ ] **Step 2: Viết tệp test**

```kotlin
package com.mckimquyen.reader.ui.page.home.feed.subs

import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.sv.BlueskyDeviceFixtures
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper
import com.mckimquyen.reader.ui.ext.currentAccountType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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

/**
 * Whether the phone's real Room and the real SubscribeViewModel/LocalRssSv do the right thing when the same Bluesky account is added
 * twice (typed as a handle, as a DID, as the old RSS form, or after a handle change) and when an old source is synced.
 * The ViewModel is driven directly (inputLink and search): nothing is typed through the screen, and MainActivity is not started.
 * A fake Bluesky server on 127.0.0.1 answers; any request for another host is refused and counted (blockedRequests, must stay 0).
 * NOT covered: the edit-address menu (FRONT-02p), the real Bluesky network, the real buttons and dialog, SyncWorker, sources that
 * already changed handle before FRONT-02q, OPML sources.
 */
@RunWith(AndroidJUnit4::class)
class BlueskyGuardsDeviceTest {
    @get:Rule val classTimeout: Timeout = Timeout.seconds(CLASS_TIMEOUT_SECONDS)
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var rig: BlueskyGuardRig
    private var realRowsBefore: Pair<Long, Long> = Pair(NOT_YET_REAL_ROWS, NOT_YET_REAL_ROWS)

    @Before
    fun setUp() {
        check(compose.activity.applicationContext.currentAccountType == AccountType.Local.id) {
            "The Bluesky search only runs for the Local account; this phone is on another account"
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

    private val alreadySubscribed get() = compose.activity.getString(R.string.already_subscribed)

    private fun newViewModel(): SubscribeViewModel =
        ViewModelProvider(compose.activity, rig.viewModelFactory)[SubscribeViewModel::class.java]

    /** The address of a Bluesky profile page, the form in which a DID can be typed (see SocialFeedUrlResolver). */
    private fun profileLinkOf(actor: String) = "https://bsky.app/profile/$actor"

    private fun waitFor(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + WAIT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError("Never saw: $what | ${rig.profileRequests()} profile / ${rig.feedRequests()} feed / ${rig.blockedRequests()} blocked requests")
    }

    /** Types [text] by calling the ViewModel and waits until the search is over: a preview, or a message. */
    private fun search(viewModel: SubscribeViewModel, text: String): SubscribeUiState {
        viewModel.init()
        viewModel.inputLink(text)
        viewModel.search()
        waitFor("the search to finish") {
            val state = viewModel.subscribeUiState.value
            state.feed != null || state.errorMessage.isNotEmpty()
        }
        return viewModel.subscribeUiState.value
    }

    private fun assertBlockedAsDuplicate(state: SubscribeUiState) {
        assertEquals(alreadySubscribed, state.errorMessage)
        assertNull("a duplicate must not build a preview", state.feed)
        assertTrue("the box must be editable again", !state.lockLinkInput)
        assertEquals("nothing may reach a host other than 127.0.0.1", 0, rig.blockedRequests())
    }

    // ---- 02i: the same account, typed the same way it was stored, or stored in the old RSS form

    @Test
    fun sameAccountAsApiSource_isAlreadySubscribed_withoutAskingTheServer() {
        runBlocking { rig.seed("api", url = BlueskyFeedMapper.feedUrl(HANDLE_A), sourceUrl = null) }
        val state = search(newViewModel(), "@$HANDLE_A")
        assertBlockedAsDuplicate(state)
        assertEquals("the duplicate is found before any call to the server", 0, rig.profileRequests())
    }

    @Test
    fun sameAccountAsOldRssSource_isAlreadySubscribed() {
        runBlocking { rig.seed("rss", url = BlueskyFeedMapper.rssUrl(HANDLE_A), sourceUrl = null, sourceType = RSS_TYPE) }
        val state = search(newViewModel(), "@$HANDLE_A")
        assertBlockedAsDuplicate(state)
        assertEquals(0, rig.profileRequests())
    }

    // ---- 02n: one account named once by handle and once by DID

    @Test
    fun typedAsDid_whenSavedAsHandle_isAlreadySubscribed() {
        rig.profile(DID_A, HANDLE_A)
        runBlocking { rig.seed("handle", url = BlueskyFeedMapper.feedUrl(HANDLE_A), sourceUrl = null) }
        val state = search(newViewModel(), profileLinkOf(DID_A))
        assertBlockedAsDuplicate(state)
        assertEquals("the server named the other name of the account: one profile call, then the feed call", 1, rig.profileRequests())
        assertEquals(1, rig.feedRequests())
    }

    @Test
    fun typedAsHandle_whenSavedAsDid_isAlreadySubscribed() {
        rig.profile(DID_A, HANDLE_A)
        runBlocking { rig.seed("did", url = BlueskyFeedMapper.feedUrl(DID_A), sourceUrl = null) }
        val state = search(newViewModel(), "@$HANDLE_A")
        assertBlockedAsDuplicate(state)
        assertEquals(1, rig.profileRequests())
    }

    // ---- 02o: the DID is remembered, so a renamed handle is still recognised

    @Test
    fun newSource_isStoredWithTheDidInSourceUrl_andTheTypedNameInUrl() {
        rig.profile(DID_A, HANDLE_A)
        val state = search(newViewModel(), "@$HANDLE_A")
        val feed = requireNotNull(state.feed) { "a new account must give a preview, got: ${state.errorMessage}" }
        assertEquals(BlueskyFeedMapper.feedUrl(HANDLE_A), feed.url)
        assertEquals("the DID outlives a handle change, so it is what the sync reads", BlueskyFeedMapper.feedUrl(DID_A), feed.sourceUrl)
        assertEquals(0, rig.blockedRequests())
        assertEquals("a preview is not saved: search() adds no feed row at all", 0L, rig.probeRowCount().let { it - PROBE_GROUP_ROWS })
    }

    @Test
    fun renamedHandle_isCaughtThroughTheStoredDid() {
        rig.profile(DID_A, HANDLE_NEW)
        runBlocking { rig.seed("renamed", url = BlueskyFeedMapper.feedUrl(HANDLE_A), sourceUrl = BlueskyFeedMapper.feedUrl(DID_A)) }
        val state = search(newViewModel(), "@$HANDLE_NEW")
        assertBlockedAsDuplicate(state)
        assertEquals("only the DID in sourceUrl can tell: one profile call", 1, rig.profileRequests())
    }

    @Test
    fun aDifferentAccount_isNotBlocked() {
        rig.profile(DID_B, HANDLE_B)
        runBlocking { rig.seed("other", url = BlueskyFeedMapper.feedUrl(HANDLE_A), sourceUrl = BlueskyFeedMapper.feedUrl(DID_A)) }
        val state = search(newViewModel(), "@$HANDLE_B")
        assertNotNull("another account must not be refused", state.feed)
        assertEquals("", state.errorMessage)
        assertEquals(0, rig.blockedRequests())
    }

    // ---- 02q: an old source is remembered by its DID once a sync succeeds

    @Test
    fun oldSource_isUpgradedToItsDid_whenASyncSucceeds_andKeepsItsUrlAndItsPosts() {
        rig.profile(DID_A, HANDLE_A)
        runBlocking { rig.seed("old", url = BlueskyFeedMapper.feedUrl(HANDLE_A), sourceUrl = BlueskyFeedMapper.feedUrl(HANDLE_A)) }
        val before = requireNotNull(runBlocking { rig.stored("old") })
        val result = runBlocking { rig.syncOnce(before) }
        assertNotNull("the sync must succeed", result.outcome)
        val after = requireNotNull(runBlocking { rig.stored("old") })
        assertEquals(BlueskyFeedMapper.feedUrl(DID_A), after.sourceUrl)
        assertEquals("the address the user sees never changes", BlueskyFeedMapper.feedUrl(HANDLE_A), after.url)
        assertEquals("the posts are not lost by the upgrade", 1, rig.articleCountOf("old"))
    }

    @Test
    fun aFailedSync_leavesTheSourceUrlAlone() {
        rig.failProfileWith500()
        runBlocking { rig.seed("failing", url = BlueskyFeedMapper.feedUrl(HANDLE_A), sourceUrl = BlueskyFeedMapper.feedUrl(HANDLE_A)) }
        val before = requireNotNull(runBlocking { rig.stored("failing") })
        val result = runBlocking { rig.syncOnce(before) }
        assertNull("a failing server must be a failed sync", result.outcome)
        assertEquals(BlueskyFeedMapper.feedUrl(HANDLE_A), requireNotNull(runBlocking { rig.stored("failing") }).sourceUrl)
        assertEquals(0, rig.articleCountOf("failing"))
    }

    @Test
    fun aSecondSync_doesNotRewriteAnUpgradedSource() {
        rig.profile(DID_A, HANDLE_A)
        runBlocking { rig.seed("twice", url = BlueskyFeedMapper.feedUrl(HANDLE_A), sourceUrl = BlueskyFeedMapper.feedUrl(DID_A)) }
        val before = requireNotNull(runBlocking { rig.stored("twice") })
        val result = runBlocking { rig.syncOnce(before) }
        assertNotNull(result.outcome)
        val after = requireNotNull(runBlocking { rig.stored("twice") })
        assertEquals(before.sourceUrl, after.sourceUrl)
        assertEquals(before.url, after.url)
    }

    // ---- cleanliness

    @Test
    fun afterCleanUp_nothingOfTheProbeIsLeftAnywhere_andTheRealDataIsUntouched() {
        runBlocking { rig.seed("tmp", url = BlueskyFeedMapper.feedUrl(HANDLE_A), sourceUrl = null) }
        assertTrue(rig.probeRowCount() > 0)
        runBlocking { rig.cleanUp() }
        assertEquals(0L, rig.probeRowCount())
        assertEquals("the owner's own feeds and articles are exactly as before", realRowsBefore, rig.realRowCounts())
    }

    private companion object {
        const val RSS_TYPE = "RSS"

        /** The probe group created in setUp is the only probe row a test that seeds nothing may find. */
        const val PROBE_GROUP_ROWS = 1L
        const val HANDLE_A = "alice-front02r.bsky.social"
        const val HANDLE_NEW = "alice-renamed-front02r.bsky.social"
        const val HANDLE_B = "bob-front02r.bsky.social"
        const val DID_A = "did:plc:front02raaaaaaaaaaaaaaaaaa"
        const val DID_B = "did:plc:front02rbbbbbbbbbbbbbbbbbb"
    }
}
```
Chú thích đã kiểm với mã thật: `search()` trả "Đã đăng ký" trước khi gọi API cho ca 02i (nên `profileRequests()==0`); ca 02n/02o chỉ biết tên thứ hai sau `getProfile` (1 yêu cầu, và `read()` gọi `getAuthorFeed` ngay sau nên `feedRequests()==1` ở ca có `Found`). Ba lần `rig.profile(...)` đặt DID khác nhau cho các tài khoản A và B; **handle chỉ chứa chữ thường, số, dấu gạch** (khớp `isActor`).

- [ ] **Step 2b: Rủi ro đã biết, chưa kiểm:** **chưa test nào trong repo kết hợp `Timeout` với `createAndroidComposeRule` trong cùng một lớp** (02h chỉ dùng `Timeout`, 02e chỉ dùng compose rule; đã grep). `Timeout` chạy thân test trên luồng riêng và thứ tự hai rule không được đảm bảo. Test này **không dùng API Compose nào**, chỉ lấy `compose.activity` làm `ViewModelStoreOwner`, nên khả năng ổn cao nhưng **chưa chứng minh**. Hướng dự phòng, chỉ khi lượt thử đầu (Task 3 Step 3) lỗi vì hai rule: bỏ `createAndroidComposeRule`, dùng `ActivityScenario.launch(ComponentActivity::class.java)` và lấy activity bằng `scenario.onActivity { ... }`, đóng ở `@After`. Không sửa chữa mù: đọc stack trước.

- [ ] **Step 3: Biên dịch (nền, output ra file)** như Task 1 Step 4. Expected exit 0. Lỗi hay gặp: `SubscribeUiState` cần import (cùng gói `…feed.subs` nên không); `createAndroidComposeRule` trong khi không dùng Compose (rule này chỉ để có `compose.activity` làm `ViewModelStoreOwner`; có thể thay bằng `ActivityScenario` nếu gây phiền, nhưng giữ như 02e để nhất quán); `AccountType` nằm ở `com.mckimquyen.reader.domain.model.account`. **Không** thêm `sleep` ngoài `POLL_MILLIS`, không tăng `WAIT_MILLIS` để chữa lỗi.

- [ ] **Step 4: Không commit ở đây.**

---

### Task 3: Chạy trên Tecno: một lượt thử, rồi 20 lượt, rồi 40 lượt

**Files:**
- Dùng sẵn, tạm, **không commit**: `/tmp/kj7_gate.sh`, `/tmp/kj7_backup.py`, `/tmp/kj7_restore.py`, `/tmp/kj7_run.sh` (nhận tên lớp, N lượt, đo `user_rotation` và `accelerometer_rotation` sau mỗi lượt).

**Interfaces:**
- Consumes: APK app và androidTest từ Task 2; lớp `BlueskyGuardsDeviceTest`.
- Produces: `/tmp/kj7-run-BlueskyGuardsDeviceTest-<N>.log`, `/tmp/kj7-r02-*.log`, xác nhận khôi phục byte-exact.

- [ ] **Step 1: Kiểm công cụ còn tồn tại (bài học: `/tmp` từng bị dọn)**

```bash
ls /tmp/kj7_gate.sh /tmp/kj7_backup.py /tmp/kj7_restore.py /tmp/kj7_run.sh /tmp/kj7_mut.sh /tmp/kj7-root 2>&1
grep -n "kj7-root\|115333744A005844" /tmp/kj7_backup.py /tmp/kj7_restore.py | head -4
```
Nếu thiếu tệp nào: **dừng**, dựng lại từ mẫu (đã dựng một lần ngày 2026-11-18; nội dung: sao lưu 8 tệp của app bằng `run-as … cat`, ghi vào `/tmp/kj7-root`; khôi phục bằng `run-as … sh -c 'cat > …'` với stdin là tệp). Không chạy khi thiếu.

- [ ] **Step 2: Cổng chỉ đọc, trong MỘT LỆNH RIÊNG**

```bash
zsh /tmp/kj7_gate.sh > /tmp/kj7-r02-gate0.log 2>&1; echo gate_rc=$?; head -1 /tmp/kj7-r02-gate0.log
```
Chỉ khi in `GATE OK` mới sang bước sau, **bằng một lệnh khác**. Nếu `STOP`: dừng, đọc lý do (launcher không ở trên, khóa, xoay lệch, kích thước lạ) và hỏi owner; không khởi chạy gì.

- [ ] **Step 3: Một lượt thử**

```bash
rm -f /tmp/kj7-r02-one.exit /tmp/kj7-r02-one.log
( zsh /tmp/kj7_run.sh BlueskyGuardsDeviceTest 1 > /tmp/kj7-r02-one.log 2>&1; echo $? > /tmp/kj7-r02-one.exit ) > /dev/null 2>&1 &
```
Chờ `.exit` và dòng `ALLDONE`; đọc `/tmp/kj7-run-BlueskyGuardsDeviceTest-1.log` đầy đủ. Expected: `OK (12 tests)`, `after-run user=0 accel=1`, `RESTORE OK`, băm DataStore trùng, đã gỡ test package.
**Nếu đỏ:** dừng và điều tra theo systematic-debugging (đọc stack; đọc số `profile/feed/blocked requests` trong thông báo `Never saw`). Các nguyên nhân có thể và cách phân biệt: (i) `check(... Local ...)` ném lỗi ⇒ máy không ở tài khoản Local, báo owner; (ii) `Never saw: the search to finish` với `blocked requests > 0` ⇒ `search()` rơi ra đường RSS (kiểm điều chỉnh 10); (iii) lỗi hai rule `Timeout` + compose ⇒ làm theo Step 2b của Task 2, **không** sửa mù; (iv) `ClassCastException ... RApp` ⇒ `applicationContext` không phải `RApp`, báo; (v) `probeRowCount()` khác 0 sau test ⇒ `cleanUp` sót, **dừng ngay**, khôi phục byte-exact và báo. Không đổi `WAIT_MILLIS`, không `sleep`, không gõ lại.

- [ ] **Step 4: 20 lượt, rồi 40 lượt (mỗi vòng: cổng riêng, rồi khởi chạy)**

Sau mỗi vòng đọc: số lượt `OK (12 tests)`, lượt đỏ và nguyên nhân từng lượt, `RESTORE OK`, băm DataStore `9ca1c202050c258b4aac9a063e27def350d737fb` trùng, `rotation: 1 0`, `GATE OK`. **Tiêu chí:** mọi lượt xanh, 0 lượt `user_rotation` lệch, 0 dòng `front02r_probe_` sót. **Một lượt đỏ không được dùng làm bằng chứng**: thu mọi thông báo lỗi, tìm điểm chung, điều tra; không gõ lại, không tăng timeout; quyết định chấp nhận hay không phải hỏi owner.

Vì lệnh chờ của công cụ giới hạn khoảng 10 phút, chia 40 lượt thành hai vòng 20 (mỗi vòng có sao lưu và khôi phục riêng) nếu một lệnh chờ không đủ.

- [ ] **Step 5: Không commit ở đây** (commit cùng Task 5).

---

### Task 4: Mutation chạy trên Tecno (chứng minh test bắt được lỗi)

**Files:**
- Create tạm, **không commit**: `/tmp/kj7_mut_r02.sh` (từ `kj7_mut.sh`), `/tmp/kj7_r02_batch.sh`.
- Đột biến tạm, luôn khôi phục: `app/src/main/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModel.kt`, `.../domain/sv/LocalRssSv.kt`, `.../domain/sv/AbstractRssRepository.kt`.

**Interfaces:**
- Consumes: 12 test xanh 40/40 ở Task 3; cổng và sao lưu của Task 3.
- Produces: `/tmp/kj7-mut-<label>-*.log`, `/tmp/kj7-r02-batch.log`, `ALLDONE`, nguồn `identical`.

- [ ] **Step 1: Kiểm các đoạn mã đột biến còn độc nhất (đọc lại, không tin plan)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
python3 - <<'PYEOF'
J='app/src/main/java/com/mckimquyen/reader/'
vm=open(J+'ui/page/home/feed/subs/SubscribeViewModel.kt',encoding='utf-8').read()
ls=open(J+'domain/sv/LocalRssSv.kt',encoding='utf-8').read()
ar=open(J+'domain/sv/AbstractRssRepository.kt',encoding='utf-8').read()
pats=[(vm,'if (rssService.get().isFeedExist(feedUrl) || rssService.get().isFeedExist(oldRssUrl)) {'),
 (vm,'if (otherNames.any { rssService.get().isFeedExist(it) } ||'),
 (vm,'rssService.get().isFeedSourceExist(\n                                com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper.feedUrl(found.did),\n                            )'),
 (vm,'sourceUrl = com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper.feedUrl(found.did),'),
 (ls,'runCatching { feedDao.updateSourceUrlIfUnchanged(feed.id, feed.sourceUrl, didAddress) }'),
 (ls,'if ((feed.sourceUrl ?: feed.url) != didAddress) {'),
 (ar,'suspend fun isFeedSourceExist(sourceUrl: String): Boolean = feedDao.queryBySourceUrl(context.currentAccountId, sourceUrl).isNotEmpty()')]
print([src.count(p) for src,p in pats])
PYEOF
```
Expected `[1, 1, 1, 1, 1, 1, 1]`. Nếu có số khác: đọc lại mã, đổi mẫu cho đúng và ghi vào "Điều chỉnh khi thực thi" (không đoán).

- [ ] **Step 2: Tạo `/tmp/kj7_mut_r02.sh` từ `kj7_mut.sh` (không sửa bản gốc)**

```bash
python3 - <<'PYEOF'
# -*- coding: utf-8 -*-
s = open('/tmp/kj7_mut.sh', encoding='utf-8').read()
assert s.count('SubscribeNostrPreviewRecreateDeviceTest') >= 1
s = s.replace('SubscribeNostrPreviewRecreateDeviceTest.kt', 'BlueskyGuardsDeviceTest.kt')
s = s.replace('SubscribeNostrPreviewRecreateDeviceTest', 'BlueskyGuardsDeviceTest')
s = s.replace('OK (4 tests)', 'OK (12 tests)').replace('ok4', 'ok12')
s = s.replace('kj7-mut-', 'kj7-r02-mut-').replace('kj7-mutbackup', 'kj7-r02-mutbackup')
open('/tmp/kj7_mut_r02.sh', 'w', encoding='utf-8').write(s)
PYEOF
grep -n "BlueskyGuardsDeviceTest\|OK (12\|SubscribeNostr\|OK (4" /tmp/kj7_mut_r02.sh | cut -c1-150
```
Expected: `TEST=…BlueskyGuardsDeviceTest`, `OK (12 tests)`, **không còn** `SubscribeNostr` và `OK (4`. Đọc lại toàn bộ script (nó khôi phục tệp đột biến trong `trap` và `cmp`).

- [ ] **Step 3: Batch (một script nền duy nhất) và các đột biến, mỗi cái nhắm đúng test**

| Nhãn | Tệp | Đột biến | Test phải đỏ |
|---|---|---|---|
| `r01-no-api-dup-check` | `SubscribeViewModel.kt` | `isFeedExist(feedUrl) \|\| isFeedExist(oldRssUrl)` thành `false` | `sameAccountAsApiSource…`, `sameAccountAsOldRssSource…` |
| `r02-no-old-rss-check` | `SubscribeViewModel.kt` | bỏ `\|\| rssService.get().isFeedExist(oldRssUrl)` | `sameAccountAsOldRssSource…` |
| `r03-no-other-names` | `SubscribeViewModel.kt` | `otherNames.any {...} \|\|` thành `false \|\|` | `typedAsDid_whenSavedAsHandle…`, `typedAsHandle_whenSavedAsDid…` |
| `r04-no-did-check` | `SubscribeViewModel.kt` | `isFeedSourceExist(...)` thành `false` | `renamedHandle_isCaughtThroughTheStoredDid` |
| `r05-store-typed-name` | `SubscribeViewModel.kt` | `sourceUrl = feedUrl(found.did)` thành `sourceUrl = feedUrl` | `newSource_isStoredWithTheDid…` |
| `r06-no-upgrade` | `LocalRssSv.kt` | `if ((feed.sourceUrl ?: feed.url) != didAddress) {` thành `if (false) {` | `oldSource_isUpgradedToItsDid…` |
| `r07-upgrade-before-empty-check` | `LocalRssSv.kt` | nâng cấp trước khi kiểm bài rỗng (cặp bên dưới) | `aSyncThatFindsNoOwnPosts_isAFailure…` |
| `r08-source-exist-reads-url` | `AbstractRssRepository.kt` | `queryBySourceUrl(` thành `queryByLink(` trong `isFeedSourceExist` | `renamedHandle_isCaughtThroughTheStoredDid` |

`r07` (đã đọc `LocalRssSv.kt:119-150`, 2026-11-18): thân hàm có hai điểm ném khi đồng bộ lỗi, `?: throw IOException("Bluesky API returned no account")` (dòng 131, lỗi **trước** khi biết DID nên không thể đột biến "nâng cấp khi lỗi") và `if (articles.isEmpty()) throw IOException("Bluesky API returned no posts")` (dòng 139, **sau** khi biết DID). Đột biến dùng điểm thứ hai, an toàn và có ý nghĩa: **nâng cấp trước khi kiểm bài rỗng**. Cặp old/new (độc nhất, đã đếm 1):
- old: `if (articles.isEmpty()) throw java.io.IOException("Bluesky API returned no posts")`
- new: `runCatching { feedDao.updateSourceUrlIfUnchanged(feed.id, feed.sourceUrl, com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyFeedMapper.feedUrl(found.result.did)) }; if (articles.isEmpty()) throw java.io.IOException("Bluesky API returned no posts")`
Test phải đỏ ở đây là một test **mới** `aSyncThatFindsNoPosts_leavesTheSourceUrlAlone` (xem Step 3b), không phải `aFailedSync…` (máy chủ trả 500 ở `getProfile` thì lỗi trước khi biết DID, nên đột biến này không làm nó đỏ). Hai test khác nhau, hai tình huống khác nhau.

- [ ] **Step 3b: Thêm test thứ 12 vào `BlueskyGuardsDeviceTest` (Task 2) để `r07` có test nhắm đúng**

Máy chủ giả trả hồ sơ hợp lệ (có DID) nhưng **không có bài chính chủ** (chỉ đăng lại): đồng bộ báo lỗi "no posts", `sourceUrl` phải **không đổi**. Cần `BlueskyGuardRig.feedWithOnlyReposts()` để `feedBodyText` thành một mảng chỉ có bài đăng lại. Thêm vào rig (Task 1) một hàm công khai `fun onlyRepostsInTheFeed() { feedBodyText = BlueskyDeviceFixtures.feedBody(BlueskyDeviceFixtures.repost("3front02rrp")) }` và vào lớp test:

```kotlin
    @Test
    fun aSyncThatFindsNoOwnPosts_isAFailure_andLeavesTheSourceUrlAlone() {
        rig.profile(DID_A, HANDLE_A)
        rig.onlyRepostsInTheFeed()
        runBlocking { rig.seed("noposts", url = BlueskyFeedMapper.feedUrl(HANDLE_A), sourceUrl = BlueskyFeedMapper.feedUrl(HANDLE_A)) }
        val before = requireNotNull(runBlocking { rig.stored("noposts") })
        val result = runBlocking { rig.syncOnce(before) }
        assertNull("no own post means a failed sync, not an empty feed", result.outcome)
        assertEquals(BlueskyFeedMapper.feedUrl(HANDLE_A), requireNotNull(runBlocking { rig.stored("noposts") }).sourceUrl)
        assertEquals(0, rig.articleCountOf("noposts"))
    }
```
Cập nhật mọi chỗ đã ghi "11 test" thành **12** (`OK (12 tests)`, `ok12`, script mong đợi). Kiểm: `grep -c "^    @Test$"` trên tệp test bằng 12.

```bash
cat > /tmp/kj7_r02_batch.sh <<'SH'
#!/bin/zsh
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou || exit 90
S=115333744A005844; APP=com.mckimquyen.reader
J=app/src/main/java/com/mckimquyen/reader
VM=$J/ui/page/home/feed/subs/SubscribeViewModel.kt; LS=$J/domain/sv/LocalRssSv.kt; AR=$J/domain/sv/AbstractRssRepository.kt
cp -p $VM /tmp/kj7-r02-orig-vm.kt; cp -p $LS /tmp/kj7-r02-orig-ls.kt; cp -p $AR /tmp/kj7-r02-orig-ar.kt
zsh /tmp/kj7_gate.sh > /tmp/kj7-r02-batch-gate0.log 2>&1 || { echo "GATE-STOP at start: $(head -1 /tmp/kj7-r02-batch-gate0.log)"; echo ALLDONE; exit 92; }
python3 /tmp/kj7_backup.py > /tmp/kj7-r02-batch-backup.log 2>&1 || { echo BACKUP-FAILED; echo ALLDONE; exit 93; }
R=$(cat /tmp/kj7-root); BEFORE=$(shasum $R/kj7/settings.preferences_pb | cut -c1-40)
M=/tmp/kj7_mut_r02.sh
zsh $M base
# --- the r01..r08 lines go here, one `zsh $M <label> <file> '<old>' '<new>'` each, written from Step 1's verified patterns ---
zsh $M base
cmp -s /tmp/kj7-r02-orig-vm.kt $VM && echo "final vm: identical" || echo "final vm: DIFFERENT"
cmp -s /tmp/kj7-r02-orig-ls.kt $LS && echo "final ls: identical" || echo "final ls: DIFFERENT"
cmp -s /tmp/kj7-r02-orig-ar.kt $AR && echo "final ar: identical" || echo "final ar: DIFFERENT"
echo "git diff main: $(git diff --stat -- app/src/main | wc -l) lines"
python3 /tmp/kj7_restore.py > /tmp/kj7-r02-batch-restore.log 2>&1; echo "restore_exit=$?"; grep -E "DIFFERENT|RESTORE" /tmp/kj7-r02-batch-restore.log
echo "datastore: now=$(adb -s $S exec-out run-as $APP cat files/datastore/settings.preferences_pb | shasum | cut -c1-40) before=$BEFORE"
adb -s $S uninstall $APP.test
echo "rotation: $(adb -s $S shell settings get system accelerometer_rotation | tr -d '\r') $(adb -s $S shell settings get system user_rotation | tr -d '\r')"
zsh /tmp/kj7_gate.sh | head -1
echo ALLDONE
SH
```
**Quan trọng:** dòng chú thích `# --- the r01..r08 lines go here ---` **không được để nguyên**; khi thực thi, thay bằng đúng các lệnh `zsh $M ...` với cặp old/new đã kiểm ở Step 1 (một lệnh một đột biến, cặp có dấu nháy đơn, xuống dòng giữ nguyên). Đọc lại toàn bộ batch trước khi chạy; nếu còn chú thích đó, **dừng**.

- [ ] **Step 4: Cổng RIÊNG, rồi khởi chạy batch (nền), chờ cả `.exit` và `ALLDONE`; không sửa ba tệp đột biến trong lúc chờ**

```bash
zsh /tmp/kj7_gate.sh > /tmp/kj7-r02-gate1.log 2>&1; echo gate_rc=$?; head -1 /tmp/kj7-r02-gate1.log
```
rồi (lệnh riêng):
```bash
rm -f /tmp/kj7-r02-batch.exit /tmp/kj7-r02-batch.log
( zsh /tmp/kj7_r02_batch.sh > /tmp/kj7-r02-batch.log 2>&1; echo $? > /tmp/kj7-r02-batch.exit ) > /dev/null 2>&1 &
```

- [ ] **Step 5: Đọc kết quả**

Mong đợi: `base` xanh `OK (12 tests)` hai lần (đầu và cuối); mỗi đột biến đỏ **đúng test dự định ở bảng trên** (đọc `failing=[...]`, không chỉ "có đỏ"); `final vm/ls/ar: identical`; `git diff main: 0 lines`; `RESTORE OK`; băm DataStore trùng; `rotation: 1 0`; `GATE OK`.
- **Đột biến sống sót** ⇒ test yếu: thêm khẳng định (ví dụ đếm yêu cầu) rồi chạy lại **cả** `base` 20 lượt và đột biến đó.
- **Đột biến đỏ nhưng sai test** (ví dụ `r03` làm `renamedHandle…` đỏ) ⇒ ghi lại, vì nó nghĩa là hai test phụ thuộc nhau; không coi là "bắt được đúng".
- **Đỏ vì hạ tầng** (không khởi động, cổng dừng) **không** tính là bắt được; điều tra.
- Lượt `base` cuối đỏ: chập chờn, quay lại điều tra, **không commit**.

- [ ] **Step 6: Trả máy**

Chỉ đọc: không còn script `kj7_*` chạy, launcher trên cùng, `accelerometer_rotation=1`, `user_rotation=0`, đã gỡ `com.mckimquyen.reader.test`, `git status` sạch ngoài tệp 02r.

---

### Task 5: JVM, tài liệu, commit

**Files:**
- Modify: `doc/feature.md`, `docs/superpowers/specs/2026-11-18-front-02r-bluesky-guards-device-test-design.md` (mục "Điều chỉnh khi thực thi").
- Create: `doc/task/done/FRONT-02r_bluesky-guards-device-test_DONE.md`.

- [ ] **Step 1: Full suite JVM và assemble (nền, output ra file)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f /tmp/f30-full.exit /tmp/f30-assemble.exit
( ./gradlew testDevDebugUnitTest --rerun-tasks > /tmp/f30-full.log 2>&1; echo $? > /tmp/f30-full.exit; ./gradlew assembleDevDebug assembleDevDebugAndroidTest > /tmp/f30-assemble.log 2>&1; echo $? > /tmp/f30-assemble.exit ) > /dev/null 2>&1 &
```
Chờ cả hai `.exit`; đếm từ XML (`app/build/test-results/testDevDebugUnitTest/*.xml`: tests, failures, errors, skipped). Expected: 0 lỗi, không đổi so với trước 02r (chỉ đổi androidTest); **ghi số đo thật**, không chép số cũ. Nếu đỏ ở lớp Compose chập chờn đã biết (02q: `AppNotIdleException`, `ReelsViewModelTest`), chạy lại và ghi cả hai kết quả, không giấu.

- [ ] **Step 2: Kiểm phạm vi diff**

```bash
git status --short | grep -v idea
git diff --check
git diff --stat -- app/src/main app/schemas app/src/main/AndroidManifest.xml app/build.gradle
```
Expected: `app/src/main`, `app/schemas`, manifest, `app/build.gradle` **không đổi**; các tệp mới và sửa chỉ ở `app/src/androidTest` và tài liệu; không có `.idea` trong danh sách commit.

- [ ] **Step 3: Tài liệu**

Báo cáo tiếng Việt, số liệu thật: số lượt từng vòng (kể cả lượt đỏ và nguyên nhân), bảng đột biến (nhãn, tệp, test đỏ **thật**, khớp dự định hay không), máy và phiên bản Android, băm DataStore trước và sau, `user_rotation` sau mỗi vòng, `probeRowCount()` sau mỗi test, `blockedRequests()` (phải 0), và ghi rõ: test chạy trên SQLite thật của Android nhưng đọc state ViewModel, không phải màn hình; không phải `MainActivity`; không chạm 02p; một máy. `doc/feature.md` thêm mục FRONT-02r trước `## 🟡 In progress`. Spec thêm mục "Điều chỉnh khi thực thi" liệt kê các điều chỉnh 1 đến 10 của plan và mọi chỗ thực tế khác kế hoạch.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/SubscribeViewModelFactory.kt \
  app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/BlueskyGuardRig.kt \
  app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/BlueskyGuardsDeviceTest.kt \
  doc/feature.md doc/task/done/FRONT-02r_bluesky-guards-device-test_DONE.md \
  docs/superpowers/specs/2026-11-18-front-02r-bluesky-guards-device-test-design.md
git commit -m "test(front-02r): check the Bluesky duplicate guards and the DID upgrade on the phone's real Room

Co-Authored-By: Claude Code <noreply@anthropic.com>"
git status --short | grep -v idea
```
Expected: một commit trên `dev`; cây sạch ngoài `.idea`; không push.

---

## Self-review

**Phủ spec.** (1) Phạm vi 02i, 02n, 02o, 02q, không 02p: Task 2 (12 test). (2) Room thật, nhóm tạm `front02r_probe_`, dọn sau mỗi test, kiểm `probeRowCount()==0` và dữ liệu thật không đổi: Task 1 (rig) và test dọn dẹp ở Task 2. (3) Gọi `inputLink` và `search` trực tiếp, không gõ giao diện, không `MainActivity`: Task 2. (4) Chỉ Tecno, cổng riêng, sao lưu byte-exact, đo xoay: Task 3 và 4. (5) Mutation trên máy, mỗi đột biến nhắm đúng test: Task 4 (bảng r01 đến r08). (6) JVM, assemble, báo cáo, `feature.md`, commit: Task 5. **Lệch so với spec, đã ghi ở "Điều chỉnh":** không tái dùng `BackgroundSyncRig` nguyên vẹn (hồ sơ giả cố định); thêm client chặn mạng ngoài 127.0.0.1 (2b); gõ DID bằng link, không `@did:`; bài giả phải đúng tác giả; thêm test thứ 12 để `r07` nhắm đúng; 12 test thay vì 10.

**Quét placeholder.** Một chỗ **cố ý** còn để trống khi thực thi (đã đếm: 11 `@Test` ở khối Task 2 + 1 ở Step 3b = 12): dòng `# --- the r01..r08 lines go here ---` trong batch của Task 4 Step 3 (cặp old/new phải chép từ kết quả kiểm Step 1 ngay lúc chạy, vì chúng phụ thuộc mã hiện tại); plan ghi rõ **không được để nguyên**. Ngoài ra không còn "TBD"/"TODO". Hai chỗ có điều kiện thật: (i) rủi ro hai rule `Timeout` + compose (Task 2 Step 2b, có hướng dự phòng); (ii) tài khoản hiện hành phải Local (kiểm ở `@Before`).

**Nhất quán kiểu và tên.** `BlueskyGuardRig` (Task 1) cung cấp `profile`, `failProfileWith500`, `onlyRepostsInTheFeed` (thêm ở Task 2 Step 3b), `profileRequests`, `feedRequests`, `blockedRequests`, `createGroup`, `seed(id, url, sourceUrl, sourceType)`, `stored`, `syncOnce(feed)`, `articleCountOf(id)`, `probeRowCount`, `realRowCounts`, `cleanUp`, `viewModelFactory`; các test ở Task 2 chỉ dùng các tên đó (`rig.stored("old")` dùng id không tiền tố, rig tự thêm `PROBE_PREFIX`). `SyncResult(outcome, inserted)` lấy từ `BackgroundSyncRig.kt`. Số test **12** thống nhất ở mọi script (`OK (12 tests)`, `ok12`).

**Rủi ro còn lại, nói thẳng.**
1. **Ghi tạm vào Room thật của Tecno** (212 bài, 1 nguồn thật): chỉ dòng `front02r_probe_`, nhưng một lượt bị cắt giữa chừng có thể để sót; khôi phục byte-exact cuối vòng xóa; giữa chừng dữ liệu owner có thêm dòng thử.
2. **`search()` có đường rơi ra mạng thật** khi API không dùng được (RSS và newsletter): đã chặn bằng client từ chối host khác 127.0.0.1 và đếm; nếu bị chặn ≥ 1 thì test đỏ có lý do rõ, nhưng một lỗi bản thân chặn (ví dụ dùng sai client) có thể vẫn ra mạng ở đường chưa ngờ tới; đọc `blockedRequests()` ở mọi test là hàng rào duy nhất.
3. **Hai rule `Timeout` và compose** chưa từng chạy chung ở repo (Task 2 Step 2b).
4. **Chập chờn không rõ nguồn** (như 02m) vẫn có thể xảy ra ở bước nào đó trên máy; test không gõ chữ nên tránh nguồn đã biết, nhưng không loại trừ nguồn khác.
5. **`SubscribeViewModel.init()`** (đã đọc, 2026-11-18) chỉ gán `groups = rssService.get().pullGroups()`, một `Flow` lười (`groupDao.queryAllGroup(...).flowOn(...)`), **không chạm DB** cho tới khi có người thu; test không thu `groups`, nên `init()` vô hại. Chỉ `subscribe()` cần nhóm, và test không gọi nó.
6. **Lint không quét `androidTest`**, nên lint không kiểm các tệp test mới; chỉ biên dịch được kiểm.
7. **Một máy** (Tecno KJ7, Android 14); không so với Pixel.
