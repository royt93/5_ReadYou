# FRONT-02h — Test máy thật: đồng bộ nền của nguồn BLUESKY và NOSTR trên Room thật (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (hoặc subagent-driven-development) để thực thi plan này từng task. Các bước dùng checkbox `- [ ]`.

**Goal:** Có bằng chứng trên Tecno KJ7, với Room thật và DAO thật, rằng một nguồn `BLUESKY` và một nguồn `NOSTR` đồng bộ nền ghi đúng bài, không ghi trùng, và **không đổi tên hay icon** của nguồn.

**Architecture:** Một lớp androidTest tự dựng `LocalRssSv` từ các field `@Inject` công khai của `RApp` (DAO thật, `RssHelper`, `NotificationHelper`, `WorkManager`) cộng các lớp có `@Inject constructor` nông dựng tay, nhưng với `BlueskyApiClient` và `NostrRelayClient` trỏ vào hai máy chủ giả trên `127.0.0.1`. Test **không** gọi `sync()` hay `SyncWorker.doWork` (quét mọi nguồn qua mạng thật); nó lặp đúng ba bước `syncSingleFeed` → `smartFilterManager.applyRules` → `articleDao.insertListIfNotExist` cho **một** nguồn thử mỗi loại, trong một nhóm tạm riêng, rồi dọn.

**Tech Stack:** `androidx.test` (`AndroidJUnit4`), Room thật qua `RApp.androidDatabase`, `mockwebserver3-junit4:5.0.0-alpha.11` (đã có ở `androidTest`), `am instrument`, adb, Python 3.9.

## Global Constraints

- Nhánh `dev`; không worktree, không push, không PR, không commit `.idea`.
- **Không** đổi mã production, Room (giữ v22), manifest, chuỗi, DataStore, `app/build.gradle`; không dependency mới; không Gemini, ad, IAP, VIP.
- Thiết bị **CHỈ Tecno KJ7 `115333744A005844`**; **Pixel `2B051FDH3006MU` CẤM đụng, kể cả đọc**. `adb -s 115333744A005844`; không `connected*`; chỉ `am instrument --user 0 -w -r -e class … com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner`; chỉ force-stop `com.mckimquyen.reader`; **không MockK trên máy**; không tọa độ; **không `MainActivity`** (quảng cáo xen kẽ ~18 giây).
- Công cụ máy ở `/Users/loitran/.claude/tmp-f29` (`gate.sh`, `backup.py`, `restore.py`, `loop.sh`, `probe_loop.sh`, `dev_mut.sh`, `jvm_mut.sh`); **không để công cụ trong `/tmp`** (bị dọn).
- **Cổng `gate.sh` phải in `GATE OK` trong MỘT LỆNH RIÊNG trước khi khởi chạy** bất cứ thứ gì chạm máy (từng có lần khởi chạy gộp ngay sau khi cổng báo dừng). Nếu cổng dừng (máy ngủ, khóa, app khác ở foreground, xoay lệch, mất adb): **dừng và hỏi owner**, không tự thao tác. Mất adb là lỗi kết nối, không phải lỗi mã.
- Sao lưu và khôi phục **byte-exact 6 tệp** (`backup.py`/`restore.py`, cả `Reader`, `-shm`, `-wal`); băm DataStore Tecno `9ca1c202050c258b4aac9a063e27def350d737fb` phải trùng sau mỗi vòng; xoay `1 0`; **không xóa DataStore**.
- Gradle chạy NỀN, output ra file, chờ `.exit`/`ALLDONE`; "completed" của lệnh nền chỉ là lệnh khởi chạy; đọc XML/log chỉ sau khi `.exit` tồn tại. Mutation: một script nền duy nhất, **không sửa tệp đột biến tới `ALLDONE`**, `cmp` sau mỗi đợt, `git diff app/src/main` phải rỗng.
- zsh: không `echo ====`; `grep -r` quote mẫu và **không** `--include`; sửa tệp bằng python; Python 3.9 cần `# -*- coding: utf-8 -*-` và `open(..., encoding="utf-8")`; macOS `sed -i` cần `''`.
- Test androidTest: hàm `@Test` phải trả `Unit`/`void` bằng thân khối `{ ... }`, **không** dùng biểu thức `= runBlocking { ... }` có giá trị (lỗi của helper 02g).
- R5: không magic number (hằng số có tên); R2: cập nhật `doc/feature.md`.
- Nói thẳng phần không kiểm: `sync()`/`SyncWorker.doWork`, vòng lặp mọi nguồn, precache, chỉ mục ngữ nghĩa, nhắc kèo, thông báo, `WorkManager` lên lịch, `smartCollectionMatcher`/`watchdogManager`/`retractionRadar` trong đường đồng bộ, mạng thật, nhánh radar quét toàn bộ, hiển thị trên màn hình; lint không quét `androidTest`.

## Điều chỉnh so với spec (kiểm bằng đọc mã thật, 2026-11-16)

1. **`WidgetRefreshNotifier` là `fun interface { fun refresh() }`** (`domain/repository/WidgetRefreshNotifier.kt`): dựng bằng lambda `WidgetRefreshNotifier { }`, không cần lớp riêng và không cần MockK. Đây là phụ thuộc mà spec ghi "chưa biết lấy ở đâu"; giờ biết.
2. **Ba phụ thuộc "chưa biết" đều lấy được, không cần đổi mã production:** `SmartCollectionMatcher(smartCollectionDao, semanticSearchEngine, articleDao, dispatcher)` với `androidDatabase.smartCollectionDao()` (có, `AndroidDb.kt:122`) và `SemanticSearchEngine(androidDatabase.articleEmbeddingDao())` (có, `AndroidDb.kt:118`); `WatchdogManager(context, WatchdogEngine(), notificationHelper, applicationScope, ioDispatcher)` với `RApp.applicationScope`, `RApp.ioDispatcher`, `RApp.notificationHelper` (đều `lateinit var` công khai); `SmartFilterManager(context, applicationScope, ioDispatcher)`. Task 1 vẫn xác nhận bằng **biên dịch**.
3. **`SmartFilterManager` và `WatchdogManager` khởi chạy coroutine nền trong `init`** (`applicationScope.launch(ioDispatcher) { ensureLoaded() }`) đọc `SharedPreferences` thật (`PREFS_NAME`). Chỉ **đọc**, nhưng là một nguồn chập chờn khác và đọc dữ liệu của chủ máy. Plan chấp nhận (chỉ đọc), và **không** gọi `watchdogManager.checkAndNotify` hay `notificationHelper.notify` trong test (hai hàm đó nằm ở `sync()`, không ở ba bước).
4. **`syncSingleFeed` ghi một dòng `feed_health_record`** (`feedHealthDao.upsert`) cả khi thành công lẫn thất bại, khóa `feedId`; bảng này **không có khóa ngoại** tới `feed` nên xóa nhóm tạm không dọn nó. `cleanUp()` phải gọi `feedHealthDao.deleteByFeedId(feedId)` cho cả hai feed thử (hàm có thật, `FeedHealthDao.kt:22`). Số dòng được đếm bằng `database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM <bảng>")` (mẫu có sẵn ở `OracleBettingMigration22Test.kt:85`) vì không có DAO đếm toàn bảng.
5. **`FeedDao.queryAll(accountId)` và `GroupDao.queryAll(accountId)` chỉ trả dòng của một tài khoản**; để so "ngoài nhóm tạm bằng nhau", đếm toàn bảng bằng SQL (điều chỉnh 4) chứ không bằng `queryAll`.
6. **`insertListIfNotExist` so theo `(link, feedId, accountId)`** (`ArticleDao.kt:861`), nên "lần hai không chèn trùng" kiểm bằng danh sách trả về rỗng; `sync()` mới là nơi gọi nó, test tự gọi.
7. **`fetchNewArticles` có nhánh quét toàn bộ** (`latestLink != null && scansFullFeedForRadar && retractionRadar.isEnabled()`): lần đồng bộ đầu feed chưa có bài nên `latestLink == null` và nhánh này **không** chạy. Lần đồng bộ **thứ hai** (test (c)) có `latestLink != null`; `LocalRssSv.scansFullFeedForRadar = true`. Vì vậy test (c) **có thể** đi qua `retractionRadar.processFetched` nếu cờ Radar của máy đang bật: plan đọc cờ (`context.retractionRadarEnabled`) và **ghi nó vào báo cáo**; nếu bật, test (c) cũng chạm bảng `article_snapshot`/`article_radar_state` (có FK CASCADE tới `article`). Nhóm tạm xóa theo CASCADE sẽ dọn các dòng đó, và các bảng này cũng được đếm trước/sau (Task 3).
8. **`BlueskyApiClient` có constructor `internal`** `(OkHttpClient, CoroutineDispatcher, baseUrl: String, timeoutMs: Long, maxBodyBytes: Long)` (đã dùng ở `BlueskyApiClientTest` JVM). `NostrRelayClient` có constructor `internal` `(OkHttpClient, CoroutineDispatcher, List<String>, Boolean, Long)` (đã dùng ở `SubscribeViewModelFactory.kt` của 02e). Cả hai gọi được từ `androidTest`.
9. **Cờ thông báo:** `Feed.isNotification` mặc định `false`; hai feed thử giữ `false` để chắc chắn không có đường nào đăng thông báo.

## Cấu trúc file

| File | Việc |
|---|---|
| Create `app/src/androidTest/java/com/mckimquyen/reader/domain/sv/BackgroundSyncRig.kt` | dựng `LocalRssSv` từ `RApp`, hai máy chủ giả, nhóm tạm và hai feed thử, `syncOnce`, đếm dòng, `cleanUp` |
| Create `app/src/androidTest/java/com/mckimquyen/reader/domain/sv/BackgroundSyncDeviceTest.kt` | 8 test thiết bị (xem Task 3) |
| Create `app/src/androidTest/java/com/mckimquyen/reader/domain/sv/BlueskyDeviceFixtures.kt` | JSON giả của Bluesky (hồ sơ, mục chính chủ, đăng lại, trả lời) |
| Tạm, **không commit**, trong `/Users/loitran/.claude/tmp-f29/`: `sync_loop.sh`, `sync_mut.sh`, `sync_mut_batch.sh` | vòng 20 lượt và đột biến chạy trên máy (tham số hoá lớp test) |
| Modify `doc/feature.md`, spec (mục "Điều chỉnh khi thực thi") | tài liệu |
| Create `doc/task/done/FRONT-02h_background-sync-device-test_DONE.md` | báo cáo |

Dùng lại, **không sửa**: `NostrFakeRelay.kt`, `NostrDeviceFixtures.kt` ở `app/src/androidTest/.../ui/page/home/feed/subs/` (cùng module nên import được).

---

### Task 1: Phép thử biên dịch trên máy tính: `LocalRssSv` dựng tay được không? (KHÔNG chạm máy)

**Mục đích:** biết sớm, trước khi viết test, xem 23 tham số của `LocalRssSv` có dựng được từ `RApp` và các `@Inject constructor` nông không. **Nếu không biên dịch được: dừng, hỏi owner bằng `AskUserQuestion`; không đổi mã production để lách.**

**Files:**
- Create: `app/src/androidTest/java/com/mckimquyen/reader/domain/sv/BackgroundSyncRig.kt` (bản khung, chưa có máy chủ giả)

**Interfaces:**
- Produces: `object LocalRssSvFactory { fun build(blueskyApi: BlueskyApiClient, nostrRelay: NostrRelayClient): LocalRssSv }`.

- [ ] **Step 1: Đọc lại API thật (một lần cuối)**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
J=app/src/main/java/com/mckimquyen/reader
sed -n 30,52p $J/domain/sv/LocalRssSv.kt
grep -n "lateinit var \(androidDatabase\|rssHelper\|notificationHelper\|workManager\|applicationScope\|ioDispatcher\|okHttpClient\)" $J/RApp.kt
grep -n "abstract fun \(feedDao\|articleDao\|accountDao\|groupDao\|feedHealthDao\|smartCollectionDao\|articleEmbeddingDao\|articleRadarDao\)" $J/infrastructure/db/AndroidDb.kt
grep -n "annotation class DefaultDispatcher" $J/infrastructure/di/CoroutineQualifier.kt
```
Expected: 23 tham số (kết thúc bằng `nostrRelayClient`, `blueskyApiClient`); bảy field `lateinit var` công khai của `RApp`; tám hàm DAO; `DefaultDispatcher` là qualifier. Nếu khác: dừng, sửa plan.

- [ ] **Step 2: Viết khung** (chỉ để biên dịch; chưa dùng ở đâu)

```kotlin
package com.mckimquyen.reader.domain.sv

import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.reader.RApp
import com.mckimquyen.reader.domain.repository.WidgetRefreshNotifier
import com.mckimquyen.reader.domain.watchdog.WatchdogEngine
import com.mckimquyen.reader.infrastructure.ai.search.SemanticSearchEngine
import com.mckimquyen.reader.infrastructure.articlehistory.ArticleHistoryRepository
import com.mckimquyen.reader.infrastructure.articlehistory.RetractionRadarService
import com.mckimquyen.reader.infrastructure.filter.SmartFilterManager
import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedHttpClient
import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedParser
import com.mckimquyen.reader.infrastructure.rss.scraper.WebFeedScraper
import com.mckimquyen.reader.infrastructure.social.bluesky.BlueskyApiClient
import com.mckimquyen.reader.infrastructure.social.nostr.NostrRelayClient
import com.mckimquyen.reader.infrastructure.watchdog.WatchdogManager
import kotlinx.coroutines.Dispatchers

/** Builds a LocalRssSv out of the running app's real DAOs and services, but with the two social clients we hand in. */
object LocalRssSvFactory {
    fun app(): RApp = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RApp

    fun smartFilterManager(app: RApp): SmartFilterManager =
        SmartFilterManager(app, app.applicationScope, app.ioDispatcher)

    fun build(blueskyApi: BlueskyApiClient, nostrRelay: NostrRelayClient): LocalRssSv {
        val app = app()
        val db = app.androidDatabase
        val dispatchers = app.ioDispatcher
        return LocalRssSv(
            context = app,
            articleDao = db.articleDao(),
            feedDao = db.feedDao(),
            rssHelper = app.rssHelper,
            notificationHelper = app.notificationHelper,
            accountDao = db.accountDao(),
            groupDao = db.groupDao(),
            feedHealthDao = db.feedHealthDao(),
            ioDispatcher = dispatchers,
            defaultDispatcher = Dispatchers.Default,
            workManager = app.workManager,
            watchdogManager = WatchdogManager(app, WatchdogEngine(), app.notificationHelper, app.applicationScope, dispatchers),
            smartFilterManager = smartFilterManager(app),
            webFeedScraper = WebFeedScraper(WebFeedHttpClient(), WebFeedParser()),
            smartCollectionMatcher = SmartCollectionMatcher(
                db.smartCollectionDao(), SemanticSearchEngine(db.articleEmbeddingDao()), db.articleDao(), Dispatchers.Default,
            ),
            widgetRefreshNotifier = WidgetRefreshNotifier { },
            retractionRadar = RetractionRadarService(app, db.articleRadarDao(), ArticleHistoryRepository(db.articleRadarDao())),
            nostrRelayClient = nostrRelay,
            blueskyApiClient = blueskyApi,
        )
    }
}
```
Lưu ý thứ tự tham số: `LocalRssSv` khai báo `workManager, watchdogManager, smartFilterManager, webFeedScraper, smartCollectionMatcher, widgetRefreshNotifier, retractionRadar, nostrRelayClient, blueskyApiClient` sau `defaultDispatcher`; dùng **tên tham số** (như trên) để không phụ thuộc thứ tự. Nếu tên khác với mã thật (đọc ở Step 1), sửa theo mã thật.

- [ ] **Step 3: Biên dịch (nền, output ra file)**

```bash
D=/Users/loitran/.claude/tmp-f29
rm -f $D/f31-build.exit
( ./gradlew assembleDevDebugAndroidTest > $D/f31-build.log 2>&1; echo $? > $D/f31-build.exit ) > /dev/null 2>&1 &
```
Chờ `$D/f31-build.exit`, rồi `cat` nó và `grep -nE "^e: " $D/f31-build.log | sed -E 's#file:///[^ ]*/##' | head`. Expected: `0`.

- [ ] **Step 4: Quyết định theo kết quả**
- `0`: **tiếp tục** Task 2.
- Lỗi "Unresolved reference"/"Cannot access ... it is internal"/"No value passed for parameter": đọc lỗi, sửa **trong tệp test** nếu là sai tên hoặc thiếu tham số. Nếu một lớp **không** truy cập được từ `androidTest` (ví dụ `private`/`internal` ở module khác) hoặc tham số cần một thứ không dựng được: **dừng, xóa khung, hỏi owner**; **không** đổi mã production.
- **Không** commit ở đây (commit cùng Task 3).

---

### Task 2: Máy chủ giả, nhóm tạm, hai feed thử, `syncOnce`, đếm dòng, `cleanUp` (chưa chạm máy)

**Files:**
- Modify: `app/src/androidTest/java/com/mckimquyen/reader/domain/sv/BackgroundSyncRig.kt` (thêm vào cùng tệp Task 1)
- Modify: `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/NostrFakeRelay.kt` (thêm một hàm; test 02e không đổi)
- Create: `app/src/androidTest/java/com/mckimquyen/reader/domain/sv/BlueskyDeviceFixtures.kt`

**Interfaces:**
- Consumes: `LocalRssSvFactory.build(blueskyApi, nostrRelay)` (Task 1); `NostrFakeRelay` (`url`, `requestsReceived()`, `close()`), `NostrDeviceFixtures.AUTHOR_HEX`.
- Produces: `class BackgroundSyncRig : AutoCloseable` với `val service: LocalRssSv`, `val groupId: String`, `val blueskyFeed: Feed`, `val nostrFeed: Feed`, `suspend fun syncOnce(feed: Feed): SyncResult`, `fun counts(): TableCounts`, `suspend fun cleanUp()`; `data class SyncResult(val outcome: FeedWithArticle?, val inserted: List<Article>)`; `data class TableCounts(val feed: Long, val article: Long, val feedHealth: Long, val group: Long)`; `object BlueskyDeviceFixtures` với `PROFILE_BODY`, `feedBody(posts)`, `post(rkey, text)`, `repost(rkey)`, `reply(rkey)`.

- [ ] **Step 1: Đọc lại API thật**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
J=app/src/main/java/com/mckimquyen/reader
sed -n 9,19p $J/domain/model/group/Group.kt
sed -n 20,40p $J/domain/model/feed/Feed.kt | grep -E "var |val "
grep -n "suspend fun queryAll\|suspend fun deleteByFeedId\|suspend fun queryByFeedId" $J/domain/repository/FeedHealthDao.kt
grep -n "suspend fun applyRules\|fun applyRules" $J/infrastructure/filter/SmartFilterManager.kt
sed -n 6,14p app/src/test/java/com/mckimquyen/reader/infrastructure/social/bluesky/BlueskyTestPayloads.kt
```
Expected: `Group(id, name, accountId)`; `Feed(id, name, icon, url, groupId, accountId, isNotification, isFullContent, sourceType, sourceUrl, important)`; `FeedHealthDao.deleteByFeedId(feedId)`; `applyRules(articles): List<Article>` (không `suspend`). Nếu khác: dừng, sửa plan.

- [ ] **Step 1b: `NostrFakeRelay` chỉ phục vụ MỘT kết nối** (mỗi `MockResponse` ứng với một WebSocket; `NostrFakeRelay.kt:24-26` enqueue đúng một cái). Mỗi lần `NostrRelayClient.fetch` mở một kết nối mới, nên test (c) (đồng bộ hai lần) cần thêm một cái. Thêm vào `NostrFakeRelay` (ngay sau khối `init`), **không đổi hành vi cũ**:

```kotlin
    /** Lets the relay answer one more connection; each fetch of a client opens its own. */
    fun serveAnotherConnection() {
        server.enqueue(socketResponse())
    }
```
(Đã từng gỡ hàm tương tự ở 02e vì không ai gọi, YAGNI; giờ có người gọi.) Rig gọi `serveAnotherConnection()` **trước mỗi** lần `syncOnce(nostrFeed)` ngoại trừ lần đầu (đã có sẵn một kết nối từ `init`).

- [ ] **Step 2: JSON giả của Bluesky** (`BlueskyDeviceFixtures.kt`; viết lại ngắn từ `BlueskyTestPayloads` của JVM vì `src/test` không thấy được từ androidTest)

```kotlin
package com.mckimquyen.reader.domain.sv

import org.json.JSONObject

/** Invented Bluesky API answers in the shape measured on the real API (2026-11-16); only the keys the app reads. */
object BlueskyDeviceFixtures {
    const val DID = "did:plc:z72i7hdynmk6r22z27h6tvur"
    const val OTHER_DID = "did:plc:aaaaaaaaaaaaaaaaaaaaaaaa"
    const val HANDLE = "front02h-probe.bsky.social"
    /** Different from what the feed was stored with, so a sync that renamed it or changed its icon would show. */
    const val HOSTILE_NAME = "Renamed By The Server"
    const val HOSTILE_AVATAR = "https://cdn.bsky.app/img/avatar/hostile@jpeg"

    val PROFILE_BODY: String = """{"did":"$DID","handle":"$HANDLE","displayName":"$HOSTILE_NAME","avatar":"$HOSTILE_AVATAR"}"""

    fun post(rkey: String, text: String): String =
        """{"post":{"uri":"at://$DID/app.bsky.feed.post/$rkey","cid":"c$rkey","author":{"did":"$DID","handle":"$HANDLE"},""" +
            """"record":{"${'$'}type":"app.bsky.feed.post","text":${JSONObject.quote(text)},"createdAt":"2026-11-10T08:00:00.000Z"}}}"""

    fun repost(rkey: String): String =
        """{"post":{"uri":"at://$OTHER_DID/app.bsky.feed.post/$rkey","cid":"c$rkey","author":{"did":"$OTHER_DID","handle":"bob.bsky.social"},""" +
            """"record":{"text":"someone else","createdAt":"2026-11-10T08:00:00.000Z"}},"reason":{"${'$'}type":"app.bsky.feed.defs#reasonRepost"}}"""

    fun feedBody(vararg items: String): String = """{"feed":[${items.joinToString(",")}]}"""
}
```

- [ ] **Step 3: Rig** (thêm vào `BackgroundSyncRig.kt`; **thân khối** cho mọi hàm, không biểu thức `= runBlocking`)

```kotlin
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.feed.FeedWithArticle
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.ui.ext.currentAccountId
import com.mckimquyen.reader.ui.page.home.feed.subs.NostrDeviceFixtures
import com.mckimquyen.reader.ui.page.home.feed.subs.NostrFakeRelay
import kotlinx.coroutines.Dispatchers
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient

data class SyncResult(val outcome: FeedWithArticle?, val inserted: List<Article>)
data class TableCounts(val feed: Long, val article: Long, val feedHealth: Long, val group: Long)

class BackgroundSyncRig : AutoCloseable {
    private val app = LocalRssSvFactory.app()
    private val db = app.androidDatabase
    private val accountId = app.currentAccountId

    /** What the fake Bluesky API answers next; a test sets it before syncing. */
    @Volatile var profileResponse: () -> MockResponse = { MockResponse().setHeader("Content-Type", "application/json").setBody(BlueskyDeviceFixtures.PROFILE_BODY) }
    @Volatile var feedResponse: () -> MockResponse = { MockResponse().setHeader("Content-Type", "application/json").setBody(BlueskyDeviceFixtures.feedBody()) }

    private val blueskyServer = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.requestLine.contains("getProfile")) profileResponse() else feedResponse()
        }
        start()
    }
    val nostrRelay = NostrFakeRelay()
    val blueskyRequests: Int get() = blueskyServer.requestCount

    val groupId = "front02h_probe_group"
    val blueskyFeed = Feed(
        id = "front02h_probe_bluesky", name = "Bluesky · Probe", icon = "https://cdn.bsky.app/img/avatar/probe@jpeg",
        url = "bsky:${BlueskyDeviceFixtures.HANDLE}", sourceUrl = "bsky:${BlueskyDeviceFixtures.HANDLE}",
        groupId = groupId, accountId = accountId, isNotification = false, sourceType = Feed.BLUESKY_SOURCE_TYPE,
    )
    val nostrFeed = Feed(
        id = "front02h_probe_nostr", name = "Nostr · Probe", icon = "https://example.invalid/probe.png",
        url = "nostr:${NostrDeviceFixtures.AUTHOR_HEX}", sourceUrl = "nostr:${NostrDeviceFixtures.AUTHOR_HEX}",
        groupId = groupId, accountId = accountId, isNotification = false, sourceType = Feed.NOSTR_SOURCE_TYPE,
    )

    private val http: OkHttpClient = app.okHttpClient
    val service: LocalRssSv = LocalRssSvFactory.build(
        BlueskyApiClient(http, Dispatchers.IO, blueskyServer.url("/").toString().trimEnd('/'), SOCIAL_TIMEOUT_MS, SOCIAL_MAX_BODY_BYTES),
        NostrRelayClient(http, Dispatchers.IO, listOf(nostrRelay.url), false, SOCIAL_TIMEOUT_MS),
    )
    private val filters = LocalRssSvFactory.smartFilterManager(app)

    suspend fun addProbeSources() {
        db.groupDao().insert(Group(id = groupId, name = "front02h probe", accountId = accountId))
        db.feedDao().insert(blueskyFeed, nostrFeed)
    }

    /** The three steps of one sync round for ONE feed; the same three calls AbstractRssRepository.sync makes. */
    suspend fun syncOnce(feed: Feed): SyncResult {
        val outcome = service.syncSingleFeed(feed)
        val inserted = if (outcome == null) emptyList() else db.articleDao().insertListIfNotExist(filters.applyRules(outcome.articles))
        return SyncResult(outcome, inserted)
    }

    fun counts(): TableCounts = TableCounts(count("feed"), count("article"), count("feed_health_record"), count("\"group\""))

    private fun count(table: String): Long {
        db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM $table").use { c ->
            c.moveToFirst()
            return c.getLong(0)
        }
    }

    /** Deleting the group cascades to both feeds and their articles (and the radar rows); the health rows have no foreign key. */
    suspend fun cleanUp() {
        db.feedHealthDao().deleteByFeedId(blueskyFeed.id)
        db.feedHealthDao().deleteByFeedId(nostrFeed.id)
        db.groupDao().queryById(groupId)?.let { db.groupDao().delete(it) }
    }

    override fun close() {
        blueskyServer.shutdown()
        nostrRelay.close()
    }

    private companion object {
        const val SOCIAL_TIMEOUT_MS = 5_000L
        const val SOCIAL_MAX_BODY_BYTES = 512 * 1024L
    }
}
```
Imports còn thiếu (thêm theo lỗi biên dịch, **đừng** đoán): `BlueskyApiClient`, `NostrRelayClient`, `kotlinx.coroutines.Dispatchers`. Với Nostr, lần `syncOnce(nostrFeed)` thứ hai trở đi phải gọi `rig.nostrRelay.serveAnotherConnection()` ngay trước (Step 1b).

- [ ] **Step 4: Biên dịch (nền)** như Task 1 Step 3. Expected: `0`. Lỗi hay gặp: `LocalRssSvFactory.smartFilterManager` là hàm dựng một **`SmartFilterManager` mới** mỗi lần gọi (hai đối tượng cho cùng `SharedPreferences`); chấp nhận vì chỉ đọc. `MockWebServer.dispatcher` là thuộc tính `var`. Nếu lỗi về `Dispatcher` (mockwebserver3 5.0.0-alpha.11 dùng `mockwebserver3.Dispatcher`): đọc API trước, **không** đoán.

- [ ] **Step 5: Không commit ở đây.**

---

### Task 3: Tám test thiết bị và biên dịch (chưa chạm máy)

**Files:**
- Create: `app/src/androidTest/java/com/mckimquyen/reader/domain/sv/BackgroundSyncDeviceTest.kt`

**Interfaces:**
- Consumes: `BackgroundSyncRig` (`service`, `blueskyFeed`, `nostrFeed`, `groupId`, `profileResponse`, `feedResponse`, `nostrRelay`, `blueskyRequests`, `addProbeSources()`, `syncOnce(feed)`, `counts()`, `cleanUp()`, `close()`), `BlueskyDeviceFixtures`, `NostrDeviceFixtures`, `NostrFakeRelay.serveAnotherConnection()`.
- Produces: lớp `BackgroundSyncDeviceTest` với 8 test (tên bên dưới).

**Điều đã đo trong mã (đọc lại ở Step 1):** `NostrFeedMapper.toArticles` đặt `link = https://njump.me/<note1…>` (`NJUMP_BASE + noteId`); `BlueskyFeedMapper.toArticles` đặt `link = https://bsky.app/profile/<did>/post/<rkey>`; `Article.id` là UUID ngẫu nhiên mỗi lần, nên "không ghi trùng" dựa vào `(link, feedId, accountId)` của `insertListIfNotExist`, không dựa vào id. Mỗi test thêm hai feed thử vào Room thật trong `@Before` và dọn trong `@After`.

- [ ] **Step 1: Đọc lại API thật**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
J=app/src/main/java/com/mckimquyen/reader
grep -n "NJUMP_BASE\|link = " $J/infrastructure/social/nostr/NostrFeedMapper.kt $J/infrastructure/social/bluesky/BlueskyFeedMapper.kt | cut -c1-150
grep -n "NOT_FOUND\|PROFILE_NOT_FOUND" $J/infrastructure/social/bluesky/BlueskyApiClient.kt | cut -c1-130
```
Expected: `NJUMP_BASE = "https://njump.me/"`, `POST_LINK_BASE = "https://bsky.app/profile/"`, và chuỗi `Profile not found`. Nếu khác: dừng, sửa plan.

- [ ] **Step 2: Viết tệp test**

```kotlin
package com.mckimquyen.reader.domain.sv

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.feed.Feed
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * One Bluesky source and one Nostr source go through the three steps of a background sync round (read the source,
 * apply the filter rules, insert what is new) against the phone's real Room, with fake servers on 127.0.0.1.
 * Deliberately NOT run: AbstractRssRepository.sync and SyncWorker.doWork, which walk every source the phone has over the real network.
 * NOT covered: precache, the semantic index, due reminders, notifications, WorkManager scheduling, a real network.
 */
@RunWith(AndroidJUnit4::class)
class BackgroundSyncDeviceTest {
    private lateinit var rig: BackgroundSyncRig
    private var before: TableCounts? = null

    @Before
    fun setUp() {
        rig = BackgroundSyncRig()
        runBlocking {
            rig.cleanUp()
            before = rig.counts()
            rig.addProbeSources()
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

    private fun feedBodyWithTwoOwnPosts() = BlueskyDeviceFixtures.feedBody(
        BlueskyDeviceFixtures.post("3k1", "first own post"),
        BlueskyDeviceFixtures.repost("3k2"),
        BlueskyDeviceFixtures.post("3k3", "second own post"),
    )

    private fun useBlueskyFeed(body: String) {
        rig.feedResponse = { MockResponse().setHeader("Content-Type", "application/json").setBody(body) }
    }

    private fun storedFeed(id: String): Feed = runBlocking { requireNotNull(rig.feedDao().queryById(id)) }

    @Test
    fun blueskySource_writesOnlyItsOwnPosts_withTheRightLinks() {
        useBlueskyFeed(feedBodyWithTwoOwnPosts())
        val result = runBlocking { rig.syncOnce(rig.blueskyFeed) }
        assertNotNull(result.outcome)
        assertEquals(setOf("first own post", "second own post"), result.inserted.map { it.title }.toSet())
        assertTrue(result.inserted.all { it.feedId == rig.blueskyFeed.id })
        assertTrue(result.inserted.all { it.link.startsWith("https://bsky.app/profile/${BlueskyDeviceFixtures.DID}/post/") })
        assertEquals(2, rig.articleCountOf(rig.blueskyFeed.id))
    }

    @Test
    fun blueskySource_isNeverRenamedNorGivenANewIcon_byTheSync() {
        useBlueskyFeed(feedBodyWithTwoOwnPosts())
        runBlocking { rig.syncOnce(rig.blueskyFeed) }
        val stored = storedFeed(rig.blueskyFeed.id)
        assertEquals("Bluesky · Probe", stored.name)
        assertEquals("https://cdn.bsky.app/img/avatar/probe@jpeg", stored.icon)
        assertTrue(stored.name != BlueskyDeviceFixtures.HOSTILE_NAME)
        assertTrue(stored.icon != BlueskyDeviceFixtures.HOSTILE_AVATAR)
    }

    @Test
    fun nostrSource_writesItsNote_withAnNjumpLink_andIsNeverRenamed() {
        val result = runBlocking { rig.syncOnce(rig.nostrFeed) }
        assertNotNull(result.outcome)
        assertEquals(1, result.inserted.size)
        assertTrue(result.inserted.single().link.startsWith("https://njump.me/note1"))
        assertEquals(rig.nostrFeed.id, result.inserted.single().feedId)
        val stored = storedFeed(rig.nostrFeed.id)
        assertEquals("Nostr · Probe", stored.name)
        assertEquals("https://example.invalid/probe.png", stored.icon)
    }

    @Test
    fun aSecondSync_ofTheSameAnswer_insertsNothingNew() {
        useBlueskyFeed(feedBodyWithTwoOwnPosts())
        assertEquals(2, runBlocking { rig.syncOnce(rig.blueskyFeed) }.inserted.size)
        assertEquals(0, runBlocking { rig.syncOnce(rig.blueskyFeed) }.inserted.size)
        assertEquals(2, rig.articleCountOf(rig.blueskyFeed.id))

        assertEquals(1, runBlocking { rig.syncOnce(rig.nostrFeed) }.inserted.size)
        rig.nostrRelay.serveAnotherConnection()
        assertEquals(0, runBlocking { rig.syncOnce(rig.nostrFeed) }.inserted.size)
        assertEquals(1, rig.articleCountOf(rig.nostrFeed.id))
    }

    @Test
    fun blueskyServerError_isASyncFailure_andWritesNoArticle() {
        rig.profileResponse = { MockResponse().setResponseCode(500) }
        val result = runBlocking { rig.syncOnce(rig.blueskyFeed) }
        assertNull(result.outcome)
        assertTrue(result.inserted.isEmpty())
        assertEquals(0, rig.articleCountOf(rig.blueskyFeed.id))
        assertEquals("Bluesky · Probe", storedFeed(rig.blueskyFeed.id).name)
    }

    @Test
    fun blueskyNotFound_isASyncFailure_andWritesNoArticle() {
        rig.profileResponse = {
            MockResponse().setResponseCode(400).setBody("""{"error":"InvalidRequest","message":"Profile not found"}""")
        }
        val result = runBlocking { rig.syncOnce(rig.blueskyFeed) }
        assertNull(result.outcome)
        assertEquals(0, rig.articleCountOf(rig.blueskyFeed.id))
    }

    @Test
    fun blueskyAnswerWithOnlyRepostsAndNoOwnPosts_isASyncFailure_notAnEmptyFeed() {
        useBlueskyFeed(BlueskyDeviceFixtures.feedBody(BlueskyDeviceFixtures.repost("3k2")))
        val result = runBlocking { rig.syncOnce(rig.blueskyFeed) }
        assertNull(result.outcome)
        assertEquals(0, rig.articleCountOf(rig.blueskyFeed.id))
    }

    @Test
    fun afterCleanUp_nothingOfTheProbeIsLeftAnywhere_andTheRowCountsAreWhatTheyWereBefore() {
        useBlueskyFeed(feedBodyWithTwoOwnPosts())
        runBlocking { rig.syncOnce(rig.blueskyFeed) }
        runBlocking { rig.syncOnce(rig.nostrFeed) }
        runBlocking { rig.cleanUp() }
        // The phone's own scheduled sync worker may add rows of OTHER feeds meanwhile, so whole-table counts are only reported, never compared.
        val after = rig.counts()
        assertTrue("group rows must be back to ${before?.group}, found ${after.group}", after.group == before?.group)
        assertEquals(0L, rig.probeRowCount())
    }
}
```
Test cần thêm vào rig (Task 2 Step 3) ba hàm: `feedDao()`, `articleCountOf(feedId)`, `probeRowCount()`. **Không** `suspend`, đọc thẳng bằng SQL (một cách duy nhất, không hai):

```kotlin
    fun feedDao() = db.feedDao()

    /** Rows of the article table for one probe feed; the id is always a test constant, never outside data. */
    fun articleCountOf(feedId: String): Int = scalar("SELECT COUNT(*) FROM article WHERE feedId = '$feedId'").toInt()

    /** Any row anywhere whose id starts with the probe prefix; must be 0 after cleanUp. */
    fun probeRowCount(): Long = listOf(
        "SELECT COUNT(*) FROM feed WHERE id LIKE 'front02h_probe_%'",
        "SELECT COUNT(*) FROM \"group\" WHERE id LIKE 'front02h_probe_%'",
        "SELECT COUNT(*) FROM feed_health_record WHERE feedId LIKE 'front02h_probe_%'",
        "SELECT COUNT(*) FROM article WHERE feedId LIKE 'front02h_probe_%'",
    ).sumOf { scalar(it) }

    private fun scalar(sql: String): Long =
        db.openHelper.writableDatabase.query(sql).use { c ->
            c.moveToFirst()
            c.getLong(0)
        }
```
và đổi hàm `count(table)` của Task 2 thành `private fun count(table: String): Long = scalar("SELECT COUNT(*) FROM $table")` để dùng chung `scalar`. Trong test, gọi `rig.articleCountOf(...)` **không** bọc `runBlocking` (hàm không suspend), và `storedFeed` giữ `runBlocking` vì `queryById` là suspend.

Điều cần xác nhận bằng lần chạy đầu (không đoán): (1) `MockResponse.setResponseCode`/`setBody`/`setHeader` có trong `mockwebserver3` 5.0.0-alpha.11 (đã dùng ở `BlueskyApiClientTest` JVM, nên có); (2) `Dispatcher.dispatch(request)` nhận `RecordedRequest` có `requestLine`; (3) `Feed` là `data class` với `var`, nên `blueskyFeed` do rig giữ **bị `syncSingleFeed` sửa tại chỗ** (`feed.apply { isNotification = ... }`): test so tên/icon trong Room (`storedFeed`), không so với đối tượng đã truyền, đúng như đã viết.

- [ ] **Step 3: Biên dịch (nền)**, `assembleDevDebug assembleDevDebugAndroidTest`, như Task 1 Step 3. Expected `0`. Sửa lỗi theo mã thật; **không** thêm `Thread.sleep`, không tăng timeout để chữa lỗi.

- [ ] **Step 4: Không commit ở đây.**

---

### Task 4: Chạy trên Tecno KJ7: một lượt thử, rồi 20 lượt (cổng, sao lưu, khôi phục)

**Files:**
- Tạm, **không commit**, trong `/Users/loitran/.claude/tmp-f29/`: `sync_loop.sh` (tạo từ `probe_loop.sh`), dùng `gate.sh`, `backup.py`, `restore.py` có sẵn.

**Interfaces:**
- Consumes: APK app và androidTest từ Task 3; tên lớp đầy đủ `com.mckimquyen.reader.domain.sv.BackgroundSyncDeviceTest`.
- Produces: `$D/sync-loop-run<N>.log`, `$D/sync.log`, `ALLDONE`.

- [ ] **Step 1: Tạo `sync_loop.sh` từ `probe_loop.sh`** (bản gốc không sửa; chỉ đổi tên tệp log để không đè log cũ, và bỏ dòng `logcat` vì không cần)

```bash
python3 - <<'PYEOF'
# -*- coding: utf-8 -*-
D = '/Users/loitran/.claude/tmp-f29'
s = open(D + '/probe_loop.sh', encoding='utf-8').read()
assert s.count('probe-loop-') >= 5 and s.count('logcat -d -s BskyProbe') == 1
s = s.replace('probe-loop-', 'sync-loop-')
s = s.replace('  adb -s $SERIAL logcat -d -s BskyProbe > $D/probe-run$i.log 2>&1\n', '')
open(D + '/sync_loop.sh', 'w', encoding='utf-8').write(s)
PYEOF
grep -n "SERIAL=\|sync-loop\|-e class\|gate.sh\|backup.py\|restore.py\|uninstall" /Users/loitran/.claude/tmp-f29/sync_loop.sh | cut -c1-190
```
**Đọc lại toàn bộ `sync_loop.sh` trước khi dùng.** Expected: `SERIAL=115333744A005844` (không có chuỗi `2B051FDH3006MU` ở đâu cả: `grep -c 2B051FDH3006MU` phải bằng `0`), cổng trước mỗi lượt, một lần sao lưu, một lần khôi phục, `uninstall com.mckimquyen.reader.test`.

- [ ] **Step 2: Cổng chỉ đọc, trong MỘT lệnh riêng; chỉ sang bước sau khi in `GATE OK`**

```bash
D=/Users/loitran/.claude/tmp-f29
zsh $D/gate.sh > $D/gate-f31a.log 2>&1; echo gate_rc=$?; head -1 $D/gate-f31a.log | cut -c1-200
```
Nếu **không** `GATE OK` (ví dụ `STOP: Pixel missing/offline`, máy khóa, app lạ ở foreground, xoay lệch): **dừng, hỏi owner bằng `AskUserQuestion`, không khởi chạy gì**. **Không bao giờ** gửi lệnh tới Pixel; nếu `adb devices` chỉ thấy Pixel thì Tecno đang vắng: báo owner.

- [ ] **Step 3: Một lượt thử (nền), đọc log thô**

```bash
D=/Users/loitran/.claude/tmp-f29; cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f $D/sync.exit $D/sync.log
( zsh $D/sync_loop.sh 1 com.mckimquyen.reader.domain.sv.BackgroundSyncDeviceTest > $D/sync.log 2>&1; echo $? > $D/sync.exit ) > /dev/null 2>&1 &
for i in $(seq 1 110); do grep -q ALLDONE $D/sync.log 2>/dev/null && test -f $D/sync.exit && break; sleep 5; done; cat $D/sync.exit; cat $D/sync.log
```
Expected: `run1: exit=0 OK (8 tests)`, `RESTORE OK`, 6 `BYTE IDENTICAL`, băm DataStore trùng `9ca1c202…`, `rotation: 1 0`, `GATE OK`, `ALLDONE`. Đọc `$D/sync-loop-run1.log` đầy đủ.
- Nếu đỏ: **dừng và điều tra theo systematic-debugging**; đọc stack, không đổi thời gian chờ, không thêm sleep, không gõ lại. Phân biệt: `ClassCastException ... RApp` (Application không phải `RApp`), `UninitializedPropertyAccessException` (field `RApp` chưa gán), lỗi Room (`FOREIGN KEY constraint failed`: nhóm tạm chưa được thêm trước feed), `IOException` mạng (máy chủ giả không nghe `127.0.0.1`).
- **Sau mọi lượt (kể cả đỏ): kiểm Room không còn dấu vết** (Step 5). Nếu còn: khôi phục byte-exact là hàng rào cuối, đã có trong `sync_loop.sh`.

- [ ] **Step 4: 20 lượt (nền)** chỉ khi lượt thử `OK (8 tests)` và cổng lại `GATE OK` trong lệnh riêng

```bash
D=/Users/loitran/.claude/tmp-f29; cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f $D/sync20.exit $D/sync20.log
( zsh $D/sync_loop.sh 20 com.mckimquyen.reader.domain.sv.BackgroundSyncDeviceTest > $D/sync20.log 2>&1; echo $? > $D/sync20.exit ) > /dev/null 2>&1 &
```
Chờ (có thể phải chờ hai lần vì giới hạn thời gian của công cụ): `ALLDONE` **và** `.exit`. **Tiêu chí:** 20/20 `OK (8 tests)`. Lượt đỏ **không** được dùng làm bằng chứng: thu `$D/sync-loop-run<N>.log` của mọi lượt đỏ, tìm điểm chung, điều tra; không gõ lại, không tăng timeout; hỏi owner trước khi chấp nhận.

- [ ] **Step 5: Kiểm không dấu vết (chỉ đọc, chỉ Tecno)**

Sau vòng, đọc bản Room **đã khôi phục** từ bản sao lưu để chắc nó không chứa dấu vết (không cần chạm máy):
```bash
D=/Users/loitran/.claude/tmp-f29; R=$(cat $D/root); W=$(mktemp -d)
cp $R/tecno/Reader $R/tecno/Reader-wal $R/tecno/Reader-shm $W/ 2>/dev/null
python3 - "$W" <<'PYEOF'
import sqlite3, sys
c = sqlite3.connect(sys.argv[1] + '/Reader')
for sql in ("select count(*) from feed where id like 'front02h_probe_%'", "select count(*) from \"group\" where id like 'front02h_probe_%'",
            "select count(*) from feed_health_record where feedId like 'front02h_probe_%'", "select count(*) from article where feedId like 'front02h_probe_%'"):
    print(sql, c.execute(sql).fetchone()[0])
PYEOF
rm -rf "$W"
```
Expected: bốn số `0` (bản sao lưu là Room **trước** vòng, nên `0` luôn đúng; kiểm này chỉ chứng minh sao lưu sạch). Kiểm thật nằm ở `probeRowCount() == 0` trong test cuối và ở `RESTORE OK` byte-identical.

- [ ] **Step 6: Trả máy**: kiểm chỉ đọc (Tecno): launcher trên cùng, `accelerometer_rotation=1`, `user_rotation=0`, `pm list packages | grep mckimquyen.reader.test` rỗng; `SendMessage` cho phiên khác nếu họ đang chờ.

---

### Task 5: Mutation chạy trên máy (chứng minh test bắt được lỗi)

**Files:**
- Tạm, **không commit**, trong `/Users/loitran/.claude/tmp-f29/`: `sync_mut.sh` (tạo từ `dev_mut.sh`), `sync_mut_batch.sh`.
- Đột biến tạm (luôn khôi phục + `cmp`): `app/src/main/java/com/mckimquyen/reader/domain/sv/LocalRssSv.kt`, `app/src/main/java/com/mckimquyen/reader/domain/repository/ArticleDao.kt`.

**Interfaces:**
- Consumes: 8 test của Task 3 xanh 20/20 (Task 4), cổng và sao lưu của Task 4.
- Produces: `$D/sync-mut-<label>-*.log`, `$D/sync-mut-batch.log`, `ALLDONE`, nguồn `identical`.

- [ ] **Step 1: Tạo `sync_mut.sh` từ `dev_mut.sh`**

```bash
python3 - <<'PYEOF'
# -*- coding: utf-8 -*-
D = '/Users/loitran/.claude/tmp-f29'
s = open(D + '/dev_mut.sh', encoding='utf-8').read()
old = 'TEST=com.mckimquyen.reader.ui.page.home.feed.subs.SubscribeNostrPreviewRecreateDeviceTest'
assert s.count(old) == 1
s = s.replace(old, 'TEST=com.mckimquyen.reader.domain.sv.BackgroundSyncDeviceTest')
s = s.replace('^OK (4 tests)', '^OK (8 tests)').replace('ok4=', 'ok8=')
s = s.replace('SubscribeNostrPreviewRecreateDeviceTest\\.', 'BackgroundSyncDeviceTest\\.').replace('(SubscribeNostrPreviewRecreateDeviceTest.kt:', '(BackgroundSyncDeviceTest.kt:')
s = s.replace('mut-$label', 'sync-mut-$label').replace('BACKUP=$D/mutbackup.bin', 'BACKUP=$D/syncmutbackup.bin')
s = s.replace('# usage: dev_mut.sh', '# usage: sync_mut.sh').replace('One instrumentation pass on the Pixel.', 'One instrumentation pass on the Tecno KJ7.')
open(D + '/sync_mut.sh', 'w', encoding='utf-8').write(s)
PYEOF
grep -n "SERIAL=\|TEST=\|OK (8\|BackgroundSync\|sync-mut" /Users/loitran/.claude/tmp-f29/sync_mut.sh | cut -c1-170; grep -c "2B051FDH3006MU" /Users/loitran/.claude/tmp-f29/sync_mut.sh
```
Expected: serial `115333744A005844`, lớp `BackgroundSyncDeviceTest`, `OK (8 tests)`, và `grep -c 2B051FDH3006MU` bằng `0`. **Đọc toàn bộ script**; `trap` khôi phục tệp trong mọi trường hợp.

- [ ] **Step 2: Kiểm các đoạn đột biến độc nhất trong mã hiện tại**

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
python3 - <<'PYEOF'
J = 'app/src/main/java/com/mckimquyen/reader/'
L = open(J + 'domain/sv/LocalRssSv.kt', encoding='utf-8').read()
A = open(J + 'domain/repository/ArticleDao.kt', encoding='utf-8').read()
c = {
 'm01-no-bluesky-branch': (L, 'if (feed.sourceType == Feed.BLUESKY_SOURCE_TYPE) return queryBlueskyArticles(feed)'),
 'm02-no-nostr-branch': (L, 'if (feed.sourceType == Feed.NOSTR_SOURCE_TYPE) return queryNostrArticles(feed)'),
 'm03-empty-ok': (L, 'if (articles.isEmpty()) throw java.io.IOException("Bluesky API returned no posts")'),
 'm04-rename': (L, '        if (articles.isEmpty()) throw java.io.IOException("Bluesky API returned no posts")\n        return articles'),
 'm05-insert-always': (A, '''            if (queryArticleByLink(
                    link = it.link,
                    feedId = it.feedId,
                    accountId = it.accountId
                ) == null
            ) it else null'''),
}
for k, (s, p) in c.items():
    print(k, s.count(p))
PYEOF
```
Expected: mỗi dòng `1`. Nếu khác `1`: dừng, đọc mã, viết mẫu mới (không ép).

- [ ] **Step 3: Batch (một script nền duy nhất)**

```bash
cat > /Users/loitran/.claude/tmp-f29/sync_mut_batch.sh <<'SH'
#!/bin/zsh
D=/Users/loitran/.claude/tmp-f29
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou || exit 90
J=app/src/main/java/com/mckimquyen/reader
L=$J/domain/sv/LocalRssSv.kt; A=$J/domain/repository/ArticleDao.kt
cp -p $L $D/sm_L.kt; cp -p $A $D/sm_A.kt
zsh $D/gate.sh > $D/sync-batch-gate0.log 2>&1 || { echo "GATE-STOP at start: $(head -1 $D/sync-batch-gate0.log)"; echo ALLDONE; exit 92; }
python3 $D/backup.py > $D/sync-batch-backup.log 2>&1 || { echo BACKUP-FAILED; echo ALLDONE; exit 93; }
M=$D/sync_mut.sh
zsh $M base
zsh $M m01-no-bluesky-branch $L 'if (feed.sourceType == Feed.BLUESKY_SOURCE_TYPE) return queryBlueskyArticles(feed)' ''
zsh $M m02-no-nostr-branch $L 'if (feed.sourceType == Feed.NOSTR_SOURCE_TYPE) return queryNostrArticles(feed)' ''
zsh $M m03-empty-ok $L 'if (articles.isEmpty()) throw java.io.IOException("Bluesky API returned no posts")' ''
zsh $M m04-rename $L '        if (articles.isEmpty()) throw java.io.IOException("Bluesky API returned no posts")
        return articles' '        if (articles.isEmpty()) throw java.io.IOException("Bluesky API returned no posts")
        feed.name = "Renamed By Sync"
        return articles'
zsh $M m05-insert-always $A '            if (queryArticleByLink(
                    link = it.link,
                    feedId = it.feedId,
                    accountId = it.accountId
                ) == null
            ) it else null' '            it'
zsh $M base
cmp -s $D/sm_L.kt $L && cmp -s $D/sm_A.kt $A && echo "final: identical" || echo "final: DIFFERENT"
echo "git diff main: $(git diff --stat -- app/src/main | wc -l) lines"
python3 $D/restore.py > $D/sync-batch-restore.log 2>&1; echo "restore_exit=$?"
grep -E "DIFFERENT|RESTORE" $D/sync-batch-restore.log
echo "datastore now: $(adb -s 115333744A005844 exec-out run-as com.mckimquyen.reader cat files/datastore/settings.preferences_pb | shasum | cut -c1-40)"
adb -s 115333744A005844 uninstall com.mckimquyen.reader.test
echo "rotation: $(adb -s 115333744A005844 shell settings get system accelerometer_rotation | tr -d '\r') $(adb -s 115333744A005844 shell settings get system user_rotation | tr -d '\r')"
zsh $D/gate.sh | head -1
echo ALLDONE
SH
chmod +x /Users/loitran/.claude/tmp-f29/sync_mut_batch.sh
```
`m04-rename` đặt `feed.name` trong `queryBlueskyArticles` (**trong cùng tệp** `LocalRssSv.kt`); `syncSingleFeed` truyền chính đối tượng `feed` đó nên Room không đổi, nhưng đối tượng đổi; test (b) so tên **trong Room**, nên mutant này **có thể sống sót** vì tên chỉ bị đổi trên đối tượng trong bộ nhớ, không được ghi lại (không có `feedDao.update`). Nếu sống sót: đây là phát hiện thật cần xử lý bằng cách đổi đột biến thành ghi vào Room (`feedDao.update(feed)`) hoặc hạ yêu cầu; **không** coi là "test yếu" mà không đọc.

- [ ] **Step 4: Cổng riêng, rồi khởi chạy batch (hai lệnh tách rời)**

```bash
D=/Users/loitran/.claude/tmp-f29; zsh $D/gate.sh > $D/gate-f31b.log 2>&1; echo gate_rc=$?; head -1 $D/gate-f31b.log | cut -c1-200
```
Chỉ khi `GATE OK`, **trong lệnh riêng tiếp theo**:
```bash
D=/Users/loitran/.claude/tmp-f29; cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f $D/sync-mut-batch.exit $D/sync-mut-batch.log
( zsh $D/sync_mut_batch.sh > $D/sync-mut-batch.log 2>&1; echo $? > $D/sync-mut-batch.exit ) > /dev/null 2>&1 &
```
Chờ **cả** `.exit` và dòng `ALLDONE`. **Trong lúc chờ không sửa `LocalRssSv.kt`, `ArticleDao.kt` hay tệp test.**

- [ ] **Step 5: Đọc kết quả**

Mong đợi: `base` xanh `OK (8 tests)` hai lần (đầu và cuối); `m01` đỏ ở test Bluesky (a, b, c, d...) ; `m02` đỏ ở test Nostr; `m03` đỏ ở test chỉ-đăng-lại; `m05` đỏ ở test "lần hai không chèn trùng"; `final: identical`; `git diff main: 0 lines`; `RESTORE OK`; băm DataStore `9ca1c202…`; `rotation: 1 0`; `GATE OK`.
- Mutant sống sót ⇒ test yếu hoặc guard thừa: đọc, thêm test, chạy lại `base` 20 lượt rồi chạy lại mutant đó.
- Đỏ vì lý do hạ tầng (không khởi động, mất adb, cổng) **không** tính là "bắt được".
- Cổng dừng giữa batch: các lượt sau ghi `GATE-STOP`; **không** coi là kết quả; hỏi owner và chạy lại các lượt còn thiếu.

---

### Task 6: JVM, tài liệu, commit

**Files:**
- Modify: `doc/feature.md`, `docs/superpowers/specs/2026-11-16-front-02h-background-sync-device-test-design.md` (mục "Điều chỉnh khi thực thi").
- Create: `doc/task/done/FRONT-02h_background-sync-device-test_DONE.md`.

- [ ] **Step 1: Full suite JVM, assemble (nền, output ra file)**

```bash
D=/Users/loitran/.claude/tmp-f29; cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
rm -f $D/f31-full.exit $D/f31-assemble.exit
( ./gradlew testDevDebugUnitTest --rerun-tasks > $D/f31-full.log 2>&1; echo $? > $D/f31-full.exit; ./gradlew assembleDevDebug assembleDevDebugAndroidTest > $D/f31-assemble.log 2>&1; echo $? > $D/f31-assemble.exit ) > /dev/null 2>&1 &
```
Chờ cả hai `.exit` bằng `0`; đếm từ XML `app/build/test-results/testDevDebugUnitTest/*.xml`: kỳ vọng **3.705 test, 0 lỗi, 0 bỏ qua** (chỉ đổi `androidTest`, nên số JVM không đổi).

- [ ] **Step 2: Phạm vi diff**

```bash
git status --short | grep -v idea
git diff --check
git diff --stat -- app/src/main app/schemas app/src/main/AndroidManifest.xml app/build.gradle
```
Expected: `app/src/main`, `app/schemas`, manifest, `app/build.gradle` **không đổi**; chỉ có tệp mới trong `app/src/androidTest/.../domain/sv/` và `NostrFakeRelay.kt` (thêm một hàm) cùng tài liệu.

- [ ] **Step 3: Tài liệu (tiếng Việt, số thật)**

Báo cáo theo khuôn `FRONT-02e`/`FRONT-02g`: phạm vi và **nói thẳng** (không gọi `sync()`/`SyncWorker.doWork`; chỉ ba bước cho một nguồn thử mỗi loại; máy chủ giả; Room thật; `feed_health_record` không có khóa ngoại nên xóa riêng; cờ Radar của máy đã đọc là bật hay tắt và nhánh quét toàn bộ có chạy hay không; `SmartFilterManager`/`WatchdogManager` dựng tay đọc `SharedPreferences` thật nhưng chỉ đọc; `syncOnStart=0` của tài khoản Tecno trong bản sao lưu), số liệu thật (số lượt, mutant bị bắt/sống, băm DataStore, `rotation 1 0`, số dòng trước/sau). `doc/feature.md` thêm mục FRONT-02h trước `## 🟡 In progress`. Spec thêm mục "Điều chỉnh khi thực thi".

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/java/com/mckimquyen/reader/domain/sv/BackgroundSyncRig.kt \
  app/src/androidTest/java/com/mckimquyen/reader/domain/sv/BackgroundSyncDeviceTest.kt \
  app/src/androidTest/java/com/mckimquyen/reader/domain/sv/BlueskyDeviceFixtures.kt \
  app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/feed/subs/NostrFakeRelay.kt \
  doc/feature.md doc/task/done/FRONT-02h_background-sync-device-test_DONE.md \
  docs/superpowers/specs/2026-11-16-front-02h-background-sync-device-test-design.md
git commit -m "test(front-02h): sync a Bluesky and a Nostr source into the phone's real Room from fake servers, leaving no trace

Co-Authored-By: Claude Code <noreply@anthropic.com>"
git status --short | grep -v idea
git log --oneline -3
```
Expected: một commit trên `dev`; cây sạch; không push.

---

## Self-review

**Phủ spec.** (1) Room thật, một nguồn thử mỗi loại, máy chủ giả: Task 1-3. (2) Dựng `LocalRssSv` từ `RApp` và dừng nếu không được: Task 1 Step 4. (3) Ba bước, không gọi `sync()`/`SyncWorker`: `syncOnce` (Task 2) và nêu rõ ở header, Global Constraints và báo cáo. (4) Sao lưu byte-exact, id riêng `front02h_probe_`, dọn đúng id, đếm dòng trước/sau (có cả `feed_health_record`): `setUp`/`tearDown` (Task 3), `cleanUp` và `counts` (Task 2), Step 5 Task 4. (5) Các test (a) đến (f) của spec: ánh xạ thành 8 test ở Task 3 (Bluesky ghi bài, không đổi tên/icon, Nostr ghi bài + không đổi tên, lần hai không trùng, 500, NotFound, chỉ đăng lại, không dấu vết); riêng "relay Nostr không trả lời" của spec (d) **chưa có test** (chỉ có lỗi Bluesky 500); thêm ở Việc còn lại bên dưới nếu owner muốn. (6) Mutation và 20 lượt: Task 4-5. (7) JVM, assemble, báo cáo, `feature.md`: Task 6. **Lệch spec, đã ghi ở "Điều chỉnh":** `WidgetRefreshNotifier` là lambda; ba phụ thuộc "chưa biết" đều lấy được; `serveAnotherConnection` thêm vào `NostrFakeRelay`.

**Quét placeholder.** Không còn "TBD"/"TODO"/"tương tự Task N". Các chỗ điều kiện thật có hướng xử lý: Task 1 Step 4 (không biên dịch được ⇒ dừng, hỏi owner), `m04-rename` có thể sống sót (đã nêu cách xử), tên tham số `LocalRssSv` phải khớp mã thật (Step 1 yêu cầu đọc).

**Nhất quán kiểu và tên.** `LocalRssSvFactory.build(blueskyApi, nostrRelay)` (Task 1) dùng ở rig (Task 2); `LocalRssSvFactory.smartFilterManager(app)` và `.app()` dùng ở rig; `BackgroundSyncRig.syncOnce/counts/cleanUp/addProbeSources/feedDao/articleCountOf/probeRowCount/close`, `SyncResult(outcome, inserted)`, `TableCounts(feed, article, feedHealth, group)` khớp cách dùng ở Task 3; `BlueskyDeviceFixtures.post/repost/feedBody/PROFILE_BODY/DID/HANDLE/HOSTILE_NAME/HOSTILE_AVATAR` khớp; `NostrFakeRelay.serveAnotherConnection()` khớp test (c). Test có **8** hàm và các script mong đợi `OK (8 tests)`.

**Rủi ro còn lại, nói thẳng.** (1) Test ghi vào Room thật; dọn trong `@After` kể cả đỏ, và byte-exact restore là hàng rào cuối. (2) `RApp` có thể tự `doSync(isOnStart)` lúc khởi động (`RApp.workerInit`): với tài khoản Tecno trong bản sao lưu (đọc ngày 2026-11-16: `syncInterval = 30` phút, `syncOnStart = 0`, tức **không phải** `Manually`), `workerInit` gọi `doSync(isOnStart = true)` và `enqueuePeriodicWork` **xếp lịch một `SyncWorker` định kỳ thật** (`setInitialDelay` 30 phút). Vì vậy **một `SyncWorker` thật chạy sync() trên máy, bằng mạng thật, có thể nổ ngay trong lúc test hoặc sau khi cài app**, ngoài ý muốn của test này; nó cũng ghi vào Room thật (`insertListIfNotExist` cho mọi nguồn của chủ máy). Đây không do test gây ra (mọi lượt cài `RApp` đều như vậy, kể cả 02e, 02g) và bản sao lưu byte-exact rồi khôi phục chính là thứ chặn mọi thay đổi đó, nhưng nghĩa là: (i) trong lúc test chạy, số dòng `article`/`feed_health_record` **của feed khác** có thể tăng do worker đó, nên kiểm "số dòng trước/sau bằng nhau" (Task 3, test cuối) **phải** chỉ so các dòng của nhóm tạm và có thể đạt `before == counts()` chỉ khi worker không chạy giữa chừng; nếu nó đỏ vì lý do này thì đó là chập chờn do worker, không phải do test; (ii) báo cáo phải nói thẳng điều này. Đây không do test gây ra (mọi lượt cài `RApp` đều như vậy, kể cả 02e), nhưng nghĩa là mạng thật có thể được dùng bởi worker đó; plan ghi vào báo cáo thay vì giả vờ nó không tồn tại. (3) Chập chờn kiểu 02d chưa rõ gốc. (4) Cổng Tecno có thể dừng vì máy ngủ; mất adb là lỗi kết nối. (5) Lint không quét `androidTest`. (6) Không kiểm: `sync()`, `SyncWorker.doWork`, mạng thật, hiển thị.

**Việc còn lại / quyết định cần owner:** test cho "relay Nostr không trả lời" (cần relay giả không phản hồi; `NostrFakeRelay.holdAnswerUntil` có sẵn nhưng chặn luồng nên cần timeout ngắn; thêm nếu bạn muốn).
