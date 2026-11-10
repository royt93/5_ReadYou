# FRONT-04 (Android Auto) Cây hai nhánh và điều khiển chung Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Màn hình Android Auto liệt kê hai nhánh phát được (Bản tin sáng = CommuteCast, Podcast mới nhất = 5 tập audio chưa đọc), phát được từng tập, điều khiển đúng bên đang phát, và hai bên nhường nhau.

**Architecture:** `CommuteMediaSessionService` vẫn là cổng duy nhất của Auto, nhưng mỏng lại: phần thuần (cây + tiền tố id, chọn bên hoạt động, ánh xạ trạng thái → `PlaybackStateCompat`, định tuyến lệnh, chặn publish dư) nằm trong các object/class không dính Android để test JVM. Một truy vấn Room chỉ chọn cột nhỏ. Nhường nhau hai chiều qua một interface `CommuteGate` đặt cạnh `TtsGate`, nối ở `PodcastModule`, nên áp dụng cho mọi đường vào chứ không chỉ Auto.

**Tech Stack:** Kotlin, `MediaBrowserServiceCompat`/`MediaSessionCompat`, Room (v20, không đổi), Hilt, Robolectric + JUnit4 + MockK, androidTest chạy bằng `adb am instrument`.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-11-10-front-04-android-auto-media-tree-design.md` (đã duyệt; sửa 2026-11-10: `TtsGate` chỉ bọc `TtsManager`, cả hai chiều podcast↔CommuteCast phải nối bằng `CommuteGate`).
- Chỉ Android Auto. **Ngoài phạm vi:** Wear OS, ReadNews trong cây, cơ chế audio focus mới (và mọi đổi ở cách ExoPlayer/`CommuteAudioPlayer` xin focus), xác minh chữ ký client, cây hai cấp theo feed, ảnh bìa tải từ mạng, xe thật. Task gốc `doc/task/todo/FRONT-04_android-auto-and-wear-os.md` giữ ở todo.
- Nhánh `dev`. Không worktree, không push, không nhắc PR, không commit `.idea`.
- Room giữ **v20**, không migration, không dependency mới, manifest và `automotive_app_desc.xml` không đổi trừ khi test chứng minh cần.
- Cây chỉ trả metadata: không URL audio, không thân bài (FIX-17). Id từ client là dữ liệu không tin cậy: phải đúng tiền tố **và** nằm trong kết quả truy vấn vừa tính.
- Chuỗi mới đủ **38 locale** (EN và VI viết riêng, còn lại English); không `%d` ngay trước danh từ số nhiều; `StringResourceParityTest` phải xanh.
- Code: không `!!`/`late` thiếu lý do, không magic number (dùng `const`), soát mọi coroutine/listener/callback (huỷ ở `onDestroy`).
- TDD nghiêm: test đỏ **đúng lý do** (đọc XML kết quả), code tối thiểu, xanh, mutation cho guard quan trọng (khôi phục bằng `cmp`), commit riêng từng task. Không chain `git commit` sau build nếu chưa kiểm exit code. Ghi output Gradle vào file, đọc XML. Dùng `printf` cho dòng bắt đầu bằng `====`.
- Thiết bị: chỉ Pixel 7 Pro `2B051FDH3006MU` (Android 17, có Android Auto 18.0.664264) và Tecno KJ7 `115333744A005844` (Android 14, **không** có Android Auto, không dùng cho DHU). Cấm BG6, S24U, A50s. Trước mọi thao tác thiết bị: `adb devices`, `dumpsys activity` (instrumentation/`topResumedActivity`), `ps` tiến trình lạ; Pixel luôn có nền `bemytester`, `loyalty.skyjoy.vn.dev/stg` (bình thường); dừng và hỏi nếu có "Active instrumentation" hoặc app lạ ở foreground. Chỉ `force-stop com.mckimquyen.reader`. `run-as com.mckimquyen.reader` (không `--user`). Backup/restore byte-exact Room (+`-shm`/`-wal`), `settings.preferences_pb`, `pairing_store.xml`; không xoá DataStore. **Không** `connected*`: dùng `adb -s <serial> shell am instrument` kèm `timeout`. Quảng cáo toàn màn hình chỉ đóng bằng Back, không bấm "Tiếp tục đến ứng dụng"; màn khoá thì hỏi owner mở khoá. Bàn phím Pixel để English.
- Mọi thao tác thiết bị trước khi plan được duyệt chỉ đọc. Không hứa kết quả DHU trước khi chạy; ghi trung thực bước nào chưa làm được.
- Sau Task cuối: báo cáo `doc/task/done/FRONT-04a_android-auto-media-tree_DONE.md` (tách riêng, nêu rõ phạm vi/giới hạn/việc chưa kiểm), cập nhật `doc/feature.md`. Task gốc FRONT-04 giữ ở todo.

## Cấu trúc file

| File | Việc | Task |
|---|---|---|
| `domain/model/podcast/PodcastBrowseItem.kt` (mới) | Dòng metadata cho danh sách xe, không URL | 1 |
| `domain/repository/ArticleDao.kt` (sửa) | `queryLatestUnreadPodcastEpisodes` | 1 |
| `infrastructure/audio/AutoMediaTree.kt` (mới) | Cây, tiền tố id, kiểm id còn trong kết quả (thuần) | 2 |
| `infrastructure/audio/AutoSession.kt` (mới) | Bên hoạt động, ánh xạ state, `AutoControlRouter`, hai `AutoTransport` (thuần) | 3, 4, 8 |
| `infrastructure/audio/AutoSessionCallback.kt` (mới) | Callback session Auto mỏng | 8 |
| `domain/sv/CommuteGate.kt` (mới), `infrastructure/audio/CommuteAudioPlayerGate.kt` (mới), `PodcastPlaybackController.kt`, `CommuteAudioPlayer.kt`, `PodcastModule.kt` (sửa) | Nhường nhau hai chiều | 5 |
| `infrastructure/podcast/PodcastPlaybackService.kt` (sửa) | `NOTIFICATION_ID` 9184 | 6 |
| `res/values*/strings.xml` ×38 | Hai tiêu đề nhánh | 7 |
| `infrastructure/audio/CommuteMediaSessionService.kt` (sửa), `PlayFromSearchCallback.kt` + test (xoá, không còn dùng) | Nối mỏng: cây, phát podcast, phản ánh state | 10 |
| `androidTest/.../CommuteMediaBrowserIntegrationTest.kt` (sửa) | Duyệt hai nhánh qua `MediaBrowserCompat` thật | 9, 10 |
| `infrastructure/audio/AutoPodcastLauncher.kt` (mới) | Phát podcast theo id đã kiểm (thuần, test JVM) | 10 |
| `doc/...`, DHU | Full suite, lint, build, smoke DHU, báo cáo | 11, 12 |

## Sự thật từ mã (đã đọc, không suy đoán)

- `CommuteMediaSessionService`: `MEDIA_ROOT_ID="commutecast_root"`, `MEDIA_ID_LATEST_EPISODE="commutecast_latest_episode"`, `NOTIFICATION_ID=9183`; `PlayFromSearchCallback(playSingleItem)` (internal open class, ghi đè `onPlay`/`onPlayFromSearch`); `onStartCommand` hiện tự đặt `PlaybackStateCompat` theo intent ACTION; `stopServiceGracefully()` dừng foreground.
- `PodcastPlaybackService.NOTIFICATION_ID=9183` (trùng); `TtsForegroundService.NOTIFICATION_ID=9182`; `NotificationHelper` dùng 9966/9988/9977 (không đổi).
- `PodcastPlaybackController(positions, tts: TtsGate, starter: PodcastServiceStarter, sleepTimer: SleepTimer)`; `state: StateFlow<PodcastPlaybackState>`; `playEpisode(articleId, title, feedName, audio: AudioInfo)`; `play()/pause()/stop()/skip(sec)/seekTo(sec)`; `SKIP_INTERVAL_SEC=15`; `pause()` thoát sớm nếu không active.
- `TtsGate` (`isSpeaking`, `stop()`, `setOnPlayRequested`) được `PodcastModule.provideTtsGate` nối vào **`TtsManager`**, không phải `CommuteAudioPlayer`. Vì vậy hiện **không** có chiều nhường nào giữa podcast và CommuteCast.
- `CommuteAudioPlayer` (`@Singleton`, constructor `(context, ambientLoop)` + phụ `(context)`), `playerState: StateFlow<CommutePlayerState>` (`episode`, `currentDialogueIndex`, `isPlaying`, `isAwaitingPlayback`, `isCompleted`…), `playEpisode/resume/pause/skipNext/skipPrevious/seekToDialogue/stopAndReset`. Mọi đường bắt đầu nói đều qua `private fun speakCurrentDialogue()`.
- `AudioInfo(url: String, mime: String?, durationSec: Int?)`. `ArticleDao.queryById(id): ArticleWithFeed?` (`article`, `feed.name`). `Article.audioUrl/audioMime/audioDurationSec` có sẵn. Tài khoản hiện tại: `Context.currentAccountId` (`ui.ext`, đọc DataStore bằng `runBlocking` ⇒ gọi trong `withContext(Dispatchers.IO)`).
- Test hiện có: `PlayFromSearchCallbackTest`, `CommuteAutoPackageValidatorTest`, `CommuteAudioPlayerTest` (Robolectric, `Config(manifest = NONE)`), `PodcastPlaybackControllerTest` (dòng 61 dựng controller bằng 4 tham số vị trí), `ArticleDaoQuizPeersTest` (mẫu Room in-memory), androidTest `CommuteMediaBrowserIntegrationTest` (đang kỳ vọng `MEDIA_ROOT_ID` có con `MEDIA_ID_LATEST_EPISODE`, **sẽ phải đổi**).

## Lệnh dùng lại

```bash
cd /Users/loitran/AndroidStudioProjects/@mckimquyen/@playstore/@prodution/@ad/260620_ReadYou
# chạy một lớp test, ghi log ra file rồi đọc XML (không đọc stdout Gradle)
./gradlew testDevDebugUnitTest --tests "<FQCN>" > /tmp/front04-<ten>.log 2>&1; echo exit=$?
grep -m1 -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' app/build/test-results/testDevDebugUnitTest/TEST-<FQCN>.xml
```

---

### Task 1: Truy vấn 5 tập podcast chưa đọc (Room, chỉ cột nhỏ)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/podcast/PodcastBrowseItem.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/repository/ArticleDao.kt` (thêm sau `queryQuizPeers`, quanh dòng 115)
- Test: `app/src/test/java/com/mckimquyen/reader/domain/repository/ArticleDaoPodcastBrowseTest.kt`

**Interfaces:**
- Consumes: `Article`, `Feed`, `Group`, `Account` (đã có), `AndroidDatabase`.
- Produces: `data class PodcastBrowseItem(val id: String, val title: String, val feedName: String, val durationSec: Int?)` và `suspend fun ArticleDao.queryLatestUnreadPodcastEpisodes(accountId: Int, limit: Int): List<PodcastBrowseItem>`.

- [ ] **Step 1: Viết test đỏ**

```kotlin
package com.mckimquyen.reader.domain.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ArticleDaoPodcastBrowseTest {

    private lateinit var database: AndroidDatabase

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java
        ).allowMainThreadQueries().build()
        listOf(ACCOUNT to FEED, OTHER_ACCOUNT to OTHER_FEED).forEach { (account, feed) ->
            database.accountDao().insert(Account(id = account, name = "A$account", type = AccountType.Local))
            database.groupDao().insert(Group(id = "g$account", name = "G", accountId = account))
            database.feedDao().insert(Feed(id = feed, name = "Feed $feed", url = "https://e.com/$feed", groupId = "g$account", accountId = account))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun keepsOnlyUnreadAudioEpisodesOfTheAccount_newestFirst() = runBlocking {
        insert("old", 1, "Old")
        insert("new", 5, "New")
        insert("read", 4, "Read", unread = false)
        insert("noAudio", 3, "No audio", audio = null)
        insert("blankAudio", 2, "Blank audio", audio = "")
        insert("foreign", 9, "Foreign", account = OTHER_ACCOUNT, feed = OTHER_FEED)

        val items = database.articleDao().queryLatestUnreadPodcastEpisodes(ACCOUNT, limit = 10)

        assertEquals(listOf("new", "old"), items.map { it.id })
        assertEquals("Feed $FEED", items.first().feedName)
        assertEquals(DURATION, items.first().durationSec)
    }

    @Test
    fun respectsLimit() = runBlocking {
        (1..8).forEach { insert("e$it", it.toLong(), "T$it") }

        val items = database.articleDao().queryLatestUnreadPodcastEpisodes(ACCOUNT, limit = 5)

        assertEquals(listOf("e8", "e7", "e6", "e5", "e4"), items.map { it.id })
    }

    @Test
    fun sameDate_breaksTiesByIdDescending() = runBlocking {
        insert("a", 7, "A")
        insert("b", 7, "B")

        assertEquals(listOf("b", "a"), database.articleDao().queryLatestUnreadPodcastEpisodes(ACCOUNT, 5).map { it.id })
    }

    @Test
    fun noEpisodes_isAnEmptyListNotAnError() = runBlocking {
        assertTrue(database.articleDao().queryLatestUnreadPodcastEpisodes(ACCOUNT, 5).isEmpty())
    }

    @Test
    fun missingDuration_staysNull() = runBlocking {
        insert("n", 1, "N", duration = null)

        assertEquals(null, database.articleDao().queryLatestUnreadPodcastEpisodes(ACCOUNT, 5).single().durationSec)
    }

    @Test
    fun doesNotReadTheBodyColumns() = runBlocking {
        insert("small", 2, "Small")
        insert("huge", 1, "Huge", raw = "x".repeat(2_000_000), full = "y".repeat(2_000_000))

        // A 4 MB body would overflow the CursorWindow if the query selected it (FIX-17).
        val items = database.articleDao().queryLatestUnreadPodcastEpisodes(ACCOUNT, 5)

        assertEquals(listOf("small", "huge"), items.map { it.id })
    }

    private suspend fun insert(
        id: String,
        day: Long,
        title: String,
        unread: Boolean = true,
        audio: String? = "https://cdn.example.com/$id.mp3",
        duration: Int? = DURATION,
        account: Int = ACCOUNT,
        feed: String = FEED,
        raw: String = "",
        full: String? = null,
    ) {
        database.articleDao().insert(
            Article(
                id = id, date = Date(day * DAY_MS), title = title, rawDescription = raw, shortDescription = "",
                fullContent = full, link = "https://example.com/$id", feedId = feed, accountId = account,
                isUnread = unread, audioUrl = audio, audioMime = "audio/mpeg", audioDurationSec = duration,
            )
        )
    }

    private companion object {
        const val ACCOUNT = 7
        const val OTHER_ACCOUNT = 8
        const val FEED = "feed-7"
        const val OTHER_FEED = "feed-8"
        const val DAY_MS = 86_400_000L
        const val DURATION = 1_800
    }
}
```

- [ ] **Step 2: Chạy, xác nhận đỏ đúng lý do**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.repository.ArticleDaoPodcastBrowseTest" > /tmp/front04-t1.log 2>&1; echo exit=$?`
Expected: `exit=1`, lỗi biên dịch `Unresolved reference: queryLatestUnreadPodcastEpisodes` (tính năng thiếu, không phải typo trong test). Đọc `grep -n "Unresolved" /tmp/front04-t1.log`.

- [ ] **Step 3: Cài đặt tối thiểu**

`PodcastBrowseItem.kt`:

```kotlin
package com.mckimquyen.reader.domain.model.podcast

/** Metadata-only row for the car's podcast list. Never carries an audio URL or a body column (FIX-17). */
data class PodcastBrowseItem(
    val id: String,
    val title: String,
    val feedName: String,
    val durationSec: Int?,
)
```

`ArticleDao.kt` (thêm import `com.mckimquyen.reader.domain.model.podcast.PodcastBrowseItem`, đặt cạnh import `QuizPeer`; thêm method sau `queryQuizPeers`):

```kotlin
    /**
     * Android Auto "latest podcasts" list: unread articles of [accountId] that carry audio, newest first.
     * Selects only small columns; the audio URL and every body column stay unread here (FIX-17). The URL is
     * fetched at play time through [queryById].
     */
    @Query(
        """
        SELECT a.id AS id, a.title AS title, f.name AS feedName, a.audioDurationSec AS durationSec
        FROM article a JOIN feed f ON f.id = a.feedId
        WHERE a.accountId = :accountId AND a.isUnread = 1 AND a.audioUrl IS NOT NULL AND a.audioUrl != ''
        ORDER BY a.date DESC, a.id DESC
        LIMIT :limit
        """
    )
    suspend fun queryLatestUnreadPodcastEpisodes(accountId: Int, limit: Int): List<PodcastBrowseItem>
```

- [ ] **Step 4: Chạy, xác nhận xanh**

Run lại lệnh Step 2. Expected: `exit=0`, XML `tests="6" skipped="0" failures="0" errors="0"`.

- [ ] **Step 5: Mutation (mỗi lần một dòng, sao lưu rồi `cmp` khôi phục)**

```bash
F=app/src/main/java/com/mckimquyen/reader/domain/repository/ArticleDao.kt; cp $F /tmp/front04-dao.bak
# M1: bỏ lọc chưa đọc
sed -i '' 's/WHERE a.accountId = :accountId AND a.isUnread = 1 AND a.audioUrl IS NOT NULL/WHERE a.accountId = :accountId AND a.audioUrl IS NOT NULL/' $F
# chạy lại lớp test, kỳ vọng keepsOnlyUnread... đỏ; rồi: cp /tmp/front04-dao.bak $F; cmp /tmp/front04-dao.bak $F
# M2: bỏ lọc tài khoản (xoá "a.accountId = :accountId AND "); kỳ vọng đỏ
# M3: ORDER BY a.date ASC; kỳ vọng đỏ ở thứ tự/limit
# M4: thêm ", a.rawDescription AS raw" vào SELECT không đổi kết quả => bỏ qua; thay a.id DESC bằng a.id ASC; kỳ vọng sameDate_breaksTies đỏ
```
Expected: mỗi mutation bị đúng test nêu trên bắt; sau mỗi lần `cmp` im lặng.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/domain/model/podcast/PodcastBrowseItem.kt app/src/main/java/com/mckimquyen/reader/domain/repository/ArticleDao.kt app/src/test/java/com/mckimquyen/reader/domain/repository/ArticleDaoPodcastBrowseTest.kt
git commit -m "feat(front-04): query the latest unread podcast episodes with small columns only

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```
(Chạy commit riêng sau khi kiểm exit code của build.)

### Task 2: Cây hai nhánh và kiểm id (thuần, JVM)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/audio/AutoMediaTree.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/audio/AutoMediaTreeTest.kt`

**Interfaces:**
- Consumes: `PodcastBrowseItem` (Task 1).
- Produces:
  - `object AutoMediaIds { ROOT, COMMUTE_BRANCH, PODCAST_BRANCH, COMMUTE_EPISODE, PODCAST_PREFIX, MAX_PODCAST_ITEMS; fun podcastId(articleId: String): String; fun podcastArticleId(mediaId: String?): String? }`
  - `data class AutoNode(val mediaId: String, val title: String, val subtitle: String?, val durationSec: Int?, val browsable: Boolean)`
  - `class AutoMediaTree(commuteBranchTitle: String, podcastBranchTitle: String)` với `fun children(parentId: String, commute: CommuteEntry?, podcasts: List<PodcastBrowseItem>): List<AutoNode>?` (`null` khi `parentId` lạ) và `fun resolvePodcast(mediaId: String?, current: List<PodcastBrowseItem>): PodcastBrowseItem?`
  - `data class CommuteEntry(val title: String, val subtitle: String)`

- [ ] **Step 1: Viết test đỏ**

```kotlin
package com.mckimquyen.reader.infrastructure.audio

import com.mckimquyen.reader.domain.model.podcast.PodcastBrowseItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoMediaTreeTest {

    private val tree = AutoMediaTree(commuteBranchTitle = "Morning", podcastBranchTitle = "Podcasts")
    private val commute = CommuteEntry(title = "Edition", subtitle = "Hello")

    private fun episodes(count: Int) = (1..count).map { PodcastBrowseItem("a$it", "Title $it", "Feed", 60 * it) }

    @Test
    fun root_hasTwoBrowsableBranchesInOrder() {
        val children = tree.children(AutoMediaIds.ROOT, commute, episodes(2))!!

        assertEquals(listOf(AutoMediaIds.COMMUTE_BRANCH, AutoMediaIds.PODCAST_BRANCH), children.map { it.mediaId })
        assertEquals(listOf("Morning", "Podcasts"), children.map { it.title })
        assertTrue(children.all { it.browsable })
    }

    @Test
    fun commuteBranch_holdsTheSingleLatestEpisodeKeepingTheOldId() {
        val children = tree.children(AutoMediaIds.COMMUTE_BRANCH, commute, emptyList())!!

        assertEquals(1, children.size)
        assertEquals(AutoMediaIds.COMMUTE_EPISODE, children.single().mediaId)
        assertEquals("commutecast_latest_episode", children.single().mediaId)
        assertFalse(children.single().browsable)
        assertEquals("Edition", children.single().title)
        assertEquals("Hello", children.single().subtitle)
    }

    @Test
    fun commuteBranch_withoutEpisode_isEmptyNotNull() {
        assertEquals(emptyList<AutoNode>(), tree.children(AutoMediaIds.COMMUTE_BRANCH, null, emptyList()))
    }

    @Test
    fun podcastBranch_showsAtMostFiveEpisodesWithPrefixedIds() {
        val children = tree.children(AutoMediaIds.PODCAST_BRANCH, null, episodes(8))!!

        assertEquals(5, children.size)
        assertEquals(listOf("podcast:a1", "podcast:a2", "podcast:a3", "podcast:a4", "podcast:a5"), children.map { it.mediaId })
        assertTrue(children.none { it.browsable })
        assertEquals("Feed", children.first().subtitle)
        assertEquals(60, children.first().durationSec)
    }

    @Test
    fun podcastBranch_empty_isEmptyNotNull() {
        assertEquals(emptyList<AutoNode>(), tree.children(AutoMediaIds.PODCAST_BRANCH, null, emptyList()))
    }

    @Test
    fun unknownParent_isNull() {
        assertNull(tree.children("nope", commute, episodes(1)))
        assertNull(tree.children("podcast:a1", commute, episodes(1)))
    }

    @Test
    fun podcastArticleId_requiresTheExactPrefixAndANonBlankRest() {
        assertEquals("abc", AutoMediaIds.podcastArticleId("podcast:abc"))
        assertNull(AutoMediaIds.podcastArticleId("podcast:"))
        assertNull(AutoMediaIds.podcastArticleId("Podcast:abc"))
        assertNull(AutoMediaIds.podcastArticleId("xpodcast:abc"))
        assertNull(AutoMediaIds.podcastArticleId(AutoMediaIds.COMMUTE_EPISODE))
        assertNull(AutoMediaIds.podcastArticleId(null))
    }

    @Test
    fun resolvePodcast_returnsTheItemOnlyWhenItIsInTheCurrentResult() {
        val current = episodes(3)

        assertEquals("a2", tree.resolvePodcast("podcast:a2", current)?.id)
        assertNull("id valid by prefix but no longer in the result", tree.resolvePodcast("podcast:a9", current))
        assertNull(tree.resolvePodcast("a2", current))
        assertNull(tree.resolvePodcast("podcast:", current))
        assertNull(tree.resolvePodcast(null, current))
    }

    @Test
    fun resolvePodcast_ignoresEpisodesBeyondTheFiveShown() {
        assertNull("a6 was never shown to the car", tree.resolvePodcast("podcast:a6", episodes(8)))
    }

    @Test
    fun idIsNeverUsedAsAPathOrQuery() {
        val hostile = "podcast:../../x' OR 1=1 --"

        assertNull(tree.resolvePodcast(hostile, episodes(3)))
    }
}
```

- [ ] **Step 2: Chạy, xác nhận đỏ đúng lý do**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.audio.AutoMediaTreeTest" > /tmp/front04-t2.log 2>&1; echo exit=$?`
Expected: `exit=1`, `Unresolved reference: AutoMediaTree` / `AutoMediaIds` / `CommuteEntry` / `AutoNode`.

- [ ] **Step 3: Cài đặt tối thiểu**

```kotlin
package com.mckimquyen.reader.infrastructure.audio

import com.mckimquyen.reader.domain.model.podcast.PodcastBrowseItem

/** Fixed ids of the Android Auto content tree. The root id and the CommuteCast leaf id predate this tree. */
object AutoMediaIds {
    const val ROOT = "commutecast_root"
    const val COMMUTE_BRANCH = "commute_root"
    const val PODCAST_BRANCH = "podcast_root"
    const val COMMUTE_EPISODE = "commutecast_latest_episode"
    const val PODCAST_PREFIX = "podcast:"
    const val MAX_PODCAST_ITEMS = 5

    fun podcastId(articleId: String): String = PODCAST_PREFIX + articleId

    /** The article id inside a podcast media id, or null for any other (or malformed) id. */
    fun podcastArticleId(mediaId: String?): String? =
        mediaId?.takeIf { it.startsWith(PODCAST_PREFIX) }?.removePrefix(PODCAST_PREFIX)?.takeIf { it.isNotBlank() }
}

data class AutoNode(
    val mediaId: String,
    val title: String,
    val subtitle: String?,
    val durationSec: Int?,
    val browsable: Boolean,
)

data class CommuteEntry(val title: String, val subtitle: String)

/** Pure description of what Android Auto may list and play. Holds no Android type and no URL. */
class AutoMediaTree(
    private val commuteBranchTitle: String,
    private val podcastBranchTitle: String,
) {
    fun children(parentId: String, commute: CommuteEntry?, podcasts: List<PodcastBrowseItem>): List<AutoNode>? =
        when (parentId) {
            AutoMediaIds.ROOT -> listOf(
                AutoNode(AutoMediaIds.COMMUTE_BRANCH, commuteBranchTitle, null, null, browsable = true),
                AutoNode(AutoMediaIds.PODCAST_BRANCH, podcastBranchTitle, null, null, browsable = true),
            )
            AutoMediaIds.COMMUTE_BRANCH ->
                listOfNotNull(commute?.let { AutoNode(AutoMediaIds.COMMUTE_EPISODE, it.title, it.subtitle, null, false) })
            AutoMediaIds.PODCAST_BRANCH -> shown(podcasts).map {
                AutoNode(AutoMediaIds.podcastId(it.id), it.title, it.feedName, it.durationSec, false)
            }
            else -> null
        }

    /** The item for [mediaId] only if it is exactly one of the episodes currently shown; never trusts the id alone. */
    fun resolvePodcast(mediaId: String?, current: List<PodcastBrowseItem>): PodcastBrowseItem? {
        val articleId = AutoMediaIds.podcastArticleId(mediaId) ?: return null
        return shown(current).firstOrNull { it.id == articleId }
    }

    private fun shown(podcasts: List<PodcastBrowseItem>) = podcasts.take(AutoMediaIds.MAX_PODCAST_ITEMS)
}
```

- [ ] **Step 4: Chạy, xác nhận xanh**

Expected: `exit=0`, XML `tests="9" skipped="0" failures="0" errors="0"`.

- [ ] **Step 5: Mutation (sao lưu `AutoMediaTree.kt`, khôi phục bằng `cmp`)**

- M1 `PODCAST_PREFIX = "podcast:"` → `"pod:"` : `podcastBranch_showsAtMostFive...` và `podcastArticleId_...` đỏ.
- M2 `MAX_PODCAST_ITEMS = 5` → `6`: `podcastBranch_showsAtMostFive...` và `resolvePodcast_ignoresEpisodesBeyondTheFive...` đỏ.
- M3 `resolvePodcast` trả `PodcastBrowseItem(articleId, "", "", null)` khi không tìm thấy (bỏ kiểm "còn trong kết quả"): `resolvePodcast_returnsTheItemOnly...` và `idIsNeverUsedAsAPathOrQuery` đỏ.
- M4 `takeIf { it.isNotBlank() }` bỏ đi: `podcastArticleId_requires...` đỏ.
- M5 `AutoMediaIds.COMMUTE_EPISODE` đổi giá trị: `commuteBranch_holdsTheSingleLatestEpisodeKeepingTheOldId` đỏ.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/infrastructure/audio/AutoMediaTree.kt app/src/test/java/com/mckimquyen/reader/infrastructure/audio/AutoMediaTreeTest.kt
git commit -m "feat(front-04): describe the two-branch Android Auto tree as pure logic

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

### Task 3: Bên hoạt động và ánh xạ trạng thái sang session (thuần, JVM)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/audio/AutoSession.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/audio/AutoSessionTest.kt`

**Interfaces:**
- Consumes: `PodcastPlaybackState`, `PodcastStatus` (domain), `CommutePlayerState` (`CommuteAudioPlayer.kt`), `PlaybackStateCompat` hằng số.
- Produces:
  - `enum class AutoSide { NONE, COMMUTE, PODCAST }`
  - `class AutoSideTracker { fun update(podcast: PodcastPlaybackState, commute: CommutePlayerState): AutoSide }`
  - `data class AutoSnapshot(val state: Int, val positionMs: Long, val speed: Float, val actions: Long, val title: String, val subtitle: String, val durationMs: Long)`
  - `object AutoSessionMapper { fun podcast(s: PodcastPlaybackState): AutoSnapshot; fun commute(s: CommutePlayerState, artist: String): AutoSnapshot; const val UNKNOWN_DURATION_MS = -1L }`

Quy tắc bên hoạt động: bên **vừa chuyển sang đang phát** (podcast `PLAYING`/`PREPARING`; CommuteCast `isPlaying` hoặc `isAwaitingPlayback`) thắng; khi không bên nào đang phát, giữ bên cuối còn nội dung (podcast `articleId != null && status != IDLE`; CommuteCast `episode != null`), hết nội dung thì `NONE`. Nếu cả hai cùng đang phát (không nên xảy ra sau Task 5) giữ bên đã thắng trước đó.

- [ ] **Step 1: Viết test đỏ**

```kotlin
package com.mckimquyen.reader.infrastructure.audio

import android.support.v4.media.session.PlaybackStateCompat
import com.mckimquyen.reader.domain.model.commute.CommuteDialogue
import com.mckimquyen.reader.domain.model.commute.CommuteEpisode
import com.mckimquyen.reader.domain.model.commute.CommuteSpeaker
import com.mckimquyen.reader.domain.model.podcast.PodcastPlaybackState
import com.mckimquyen.reader.domain.model.podcast.PodcastStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class AutoSessionTest {

    private val idlePodcast = PodcastPlaybackState()
    private val idleCommute = CommutePlayerState()
    private val episode = CommuteEpisode("e1", "Edition", Date(0), listOf(CommuteDialogue(CommuteSpeaker.ALEX, "Hi there")))

    private fun podcast(status: PodcastStatus, pos: Int = 30, dur: Int = 600, speed: Float = 1.5f) =
        PodcastPlaybackState("a1", "Ep", "Feed", status, pos, dur, speed)

    private fun commute(playing: Boolean = false, waiting: Boolean = false, withEpisode: Boolean = true) =
        CommutePlayerState(episode = if (withEpisode) episode else null, isPlaying = playing, isAwaitingPlayback = waiting)

    // --- side tracker ---

    @Test
    fun nothingLoaded_isNone() {
        assertEquals(AutoSide.NONE, AutoSideTracker().update(idlePodcast, idleCommute))
    }

    @Test
    fun podcastPlayingWins() {
        assertEquals(AutoSide.PODCAST, AutoSideTracker().update(podcast(PodcastStatus.PLAYING), idleCommute))
        assertEquals(AutoSide.PODCAST, AutoSideTracker().update(podcast(PodcastStatus.PREPARING), idleCommute))
    }

    @Test
    fun commutePlayingOrWaitingWins() {
        assertEquals(AutoSide.COMMUTE, AutoSideTracker().update(idlePodcast, commute(playing = true)))
        assertEquals(AutoSide.COMMUTE, AutoSideTracker().update(idlePodcast, commute(waiting = true)))
    }

    @Test
    fun pausedSideKeepsControlUntilTheOtherStarts() {
        val t = AutoSideTracker()
        assertEquals(AutoSide.PODCAST, t.update(podcast(PodcastStatus.PLAYING), commute()))
        assertEquals("paused podcast still owns the car controls", AutoSide.PODCAST, t.update(podcast(PodcastStatus.PAUSED), commute()))
        assertEquals(AutoSide.COMMUTE, t.update(podcast(PodcastStatus.PAUSED), commute(playing = true)))
        assertEquals("commute paused, podcast paused: last active stays", AutoSide.COMMUTE, t.update(podcast(PodcastStatus.PAUSED), commute()))
        assertEquals(AutoSide.PODCAST, t.update(podcast(PodcastStatus.PLAYING), commute()))
    }

    @Test
    fun lastSideWithoutContentFallsBackToNone() {
        val t = AutoSideTracker()
        t.update(podcast(PodcastStatus.PLAYING), idleCommute)
        assertEquals(AutoSide.NONE, t.update(idlePodcast, idleCommute))
    }

    @Test
    fun bothPlaying_keepsTheEarlierWinner() {
        val t = AutoSideTracker()
        t.update(podcast(PodcastStatus.PLAYING), idleCommute)
        assertEquals(AutoSide.PODCAST, t.update(podcast(PodcastStatus.PLAYING), commute(playing = true)))
    }

    @Test
    fun pausedWithContentOnlyOnTheOtherSide_usesThatSide() {
        assertEquals(AutoSide.COMMUTE, AutoSideTracker().update(idlePodcast, commute()))
    }

    // --- podcast mapping ---

    @Test
    fun podcastStatusesMapToSessionStates() {
        val expected = mapOf(
            PodcastStatus.PLAYING to PlaybackStateCompat.STATE_PLAYING,
            PodcastStatus.PREPARING to PlaybackStateCompat.STATE_BUFFERING,
            PodcastStatus.PAUSED to PlaybackStateCompat.STATE_PAUSED,
            PodcastStatus.ERROR to PlaybackStateCompat.STATE_ERROR,
            PodcastStatus.IDLE to PlaybackStateCompat.STATE_STOPPED,
        )
        expected.forEach { (status, state) -> assertEquals(status.name, state, AutoSessionMapper.podcast(podcast(status)).state) }
    }

    @Test
    fun podcastSnapshot_carriesPositionDurationAndMetadata() {
        val s = AutoSessionMapper.podcast(podcast(PodcastStatus.PLAYING, pos = 30, dur = 600, speed = 1.5f))

        assertEquals(30_000L, s.positionMs)
        assertEquals(600_000L, s.durationMs)
        assertEquals(1.5f, s.speed, 0f)
        assertEquals("Ep", s.title)
        assertEquals("Feed", s.subtitle)
    }

    @Test
    fun podcastSpeedIsZeroUnlessPlaying() {
        assertEquals(0f, AutoSessionMapper.podcast(podcast(PodcastStatus.PAUSED)).speed, 0f)
        assertEquals(0f, AutoSessionMapper.podcast(podcast(PodcastStatus.ERROR)).speed, 0f)
    }

    @Test
    fun podcastActions_includeSeekAndFastForward() {
        val a = AutoSessionMapper.podcast(podcast(PodcastStatus.PAUSED)).actions
        listOf(
            PlaybackStateCompat.ACTION_PLAY, PlaybackStateCompat.ACTION_PAUSE, PlaybackStateCompat.ACTION_STOP,
            PlaybackStateCompat.ACTION_SEEK_TO, PlaybackStateCompat.ACTION_FAST_FORWARD, PlaybackStateCompat.ACTION_REWIND,
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS,
            PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID,
        ).forEach { assertTrue("missing action $it", a and it != 0L) }
    }

    // --- commute mapping ---

    @Test
    fun commuteStatesMapToSessionStates() {
        assertEquals(PlaybackStateCompat.STATE_PLAYING, AutoSessionMapper.commute(commute(playing = true), "A").state)
        assertEquals(PlaybackStateCompat.STATE_BUFFERING, AutoSessionMapper.commute(commute(waiting = true), "A").state)
        assertEquals(PlaybackStateCompat.STATE_PAUSED, AutoSessionMapper.commute(commute(), "A").state)
        assertEquals(PlaybackStateCompat.STATE_STOPPED, AutoSessionMapper.commute(commute(withEpisode = false), "A").state)
    }

    @Test
    fun commuteSnapshot_hasNoTimelineAndNoSeek() {
        val s = AutoSessionMapper.commute(commute(playing = true), "Alex & Sam")

        assertEquals(PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, s.positionMs)
        assertEquals(AutoSessionMapper.UNKNOWN_DURATION_MS, s.durationMs)
        assertEquals("Edition", s.title)
        assertEquals("Alex & Sam", s.subtitle)
        assertEquals(0L, s.actions and PlaybackStateCompat.ACTION_SEEK_TO)
        assertTrue(s.actions and PlaybackStateCompat.ACTION_SKIP_TO_NEXT != 0L)
        assertTrue(s.actions and PlaybackStateCompat.ACTION_FAST_FORWARD != 0L)
    }
}
```

- [ ] **Step 2: Chạy, xác nhận đỏ đúng lý do**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.audio.AutoSessionTest" > /tmp/front04-t3.log 2>&1; echo exit=$?`
Expected: `exit=1`, `Unresolved reference: AutoSideTracker` / `AutoSessionMapper` / `AutoSide`.

- [ ] **Step 3: Cài đặt tối thiểu**

```kotlin
package com.mckimquyen.reader.infrastructure.audio

import android.support.v4.media.session.PlaybackStateCompat
import com.mckimquyen.reader.domain.model.podcast.PodcastPlaybackState
import com.mckimquyen.reader.domain.model.podcast.PodcastStatus

enum class AutoSide { NONE, COMMUTE, PODCAST }

/** What the car session should show right now; plain values so it can be tested without a MediaSession. */
data class AutoSnapshot(
    val state: Int,
    val positionMs: Long,
    val speed: Float,
    val actions: Long,
    val title: String,
    val subtitle: String,
    val durationMs: Long,
)

/** Picks which engine the car controls drive: the one that most recently started, else the last one with content. */
class AutoSideTracker {
    private var last = AutoSide.NONE
    private var podcastWasActive = false
    private var commuteWasActive = false

    fun update(podcast: PodcastPlaybackState, commute: CommutePlayerState): AutoSide {
        val podcastActive = podcast.status == PodcastStatus.PLAYING || podcast.status == PodcastStatus.PREPARING
        val commuteActive = commute.isPlaying || commute.isAwaitingPlayback
        val podcastStarted = podcastActive && !podcastWasActive
        val commuteStarted = commuteActive && !commuteWasActive
        podcastWasActive = podcastActive
        commuteWasActive = commuteActive
        when {
            podcastStarted && !commuteStarted -> last = AutoSide.PODCAST
            commuteStarted && !podcastStarted -> last = AutoSide.COMMUTE
            last == AutoSide.NONE && podcastActive -> last = AutoSide.PODCAST
            last == AutoSide.NONE && commuteActive -> last = AutoSide.COMMUTE
        }
        val hasPodcast = podcast.articleId != null && podcast.status != PodcastStatus.IDLE
        val hasCommute = commute.episode != null
        if (last == AutoSide.PODCAST && !hasPodcast) last = if (hasCommute) AutoSide.COMMUTE else AutoSide.NONE
        if (last == AutoSide.COMMUTE && !hasCommute) last = if (hasPodcast) AutoSide.PODCAST else AutoSide.NONE
        if (last == AutoSide.NONE) last = when {
            hasPodcast -> AutoSide.PODCAST
            hasCommute -> AutoSide.COMMUTE
            else -> AutoSide.NONE
        }
        return last
    }
}

object AutoSessionMapper {
    const val UNKNOWN_DURATION_MS = -1L
    private const val MS_PER_SEC = 1_000L

    private const val BASE_ACTIONS = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
        PlaybackStateCompat.ACTION_STOP or PlaybackStateCompat.ACTION_PLAY_PAUSE or
        PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_FAST_FORWARD or
        PlaybackStateCompat.ACTION_REWIND
    private const val PODCAST_ACTIONS = BASE_ACTIONS or PlaybackStateCompat.ACTION_SEEK_TO

    fun podcast(s: PodcastPlaybackState): AutoSnapshot {
        val state = when (s.status) {
            PodcastStatus.PLAYING -> PlaybackStateCompat.STATE_PLAYING
            PodcastStatus.PREPARING -> PlaybackStateCompat.STATE_BUFFERING
            PodcastStatus.PAUSED -> PlaybackStateCompat.STATE_PAUSED
            PodcastStatus.ERROR -> PlaybackStateCompat.STATE_ERROR
            PodcastStatus.IDLE -> PlaybackStateCompat.STATE_STOPPED
        }
        return AutoSnapshot(
            state = state,
            positionMs = s.positionSec * MS_PER_SEC,
            speed = if (s.status == PodcastStatus.PLAYING) s.speed else 0f,
            actions = PODCAST_ACTIONS,
            title = s.title,
            subtitle = s.feedName,
            durationMs = s.durationSec * MS_PER_SEC,
        )
    }

    fun commute(s: CommutePlayerState, artist: String): AutoSnapshot {
        val state = when {
            s.isPlaying -> PlaybackStateCompat.STATE_PLAYING
            s.isAwaitingPlayback -> PlaybackStateCompat.STATE_BUFFERING
            s.episode != null -> PlaybackStateCompat.STATE_PAUSED
            else -> PlaybackStateCompat.STATE_STOPPED
        }
        return AutoSnapshot(
            state = state,
            positionMs = PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
            speed = if (s.isPlaying) 1.0f else 0f,
            actions = BASE_ACTIONS,
            title = s.episode?.title.orEmpty(),
            subtitle = artist,
            durationMs = UNKNOWN_DURATION_MS,
        )
    }
}
```

- [ ] **Step 4: Chạy, xác nhận xanh**

Expected: `exit=0`, XML `tests="14" skipped="0" failures="0" errors="0"`. Nếu `PlaybackStateCompat` ném `Method ... not mocked` trong JVM thuần, các hằng là compile-time nên không xảy ra; nếu có, thêm `@RunWith(RobolectricTestRunner::class)` + `@Config(manifest = Config.NONE)` như `PlayFromSearchCallbackTest` và ghi vào báo cáo.

- [ ] **Step 5: Mutation (sao lưu, `cmp` khôi phục)**

- M1 `podcastStarted && !commuteStarted` → bỏ nhánh podcast (xoá dòng): `podcastPlayingWins`/`pausedSideKeepsControl...` đỏ.
- M2 bỏ dòng `if (last == AutoSide.PODCAST && !hasPodcast)`: `lastSideWithoutContentFallsBackToNone` đỏ.
- M3 `speed = if (s.status == PLAYING) s.speed else 0f` → `speed = s.speed`: `podcastSpeedIsZeroUnlessPlaying` đỏ.
- M4 `PODCAST_ACTIONS = BASE_ACTIONS` (mất SEEK_TO): `podcastActions_includeSeekAndFastForward` đỏ.
- M5 `commute` thêm `ACTION_SEEK_TO` vào `BASE_ACTIONS`: `commuteSnapshot_hasNoTimeline...` đỏ.
- M6 `PodcastStatus.ERROR -> STATE_PAUSED`: `podcastStatusesMapToSessionStates` đỏ.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/infrastructure/audio/AutoSession.kt app/src/test/java/com/mckimquyen/reader/infrastructure/audio/AutoSessionTest.kt
git commit -m "feat(front-04): choose the active side and map its state for the car session

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

### Task 4: Định tuyến lệnh điều khiển theo bên hoạt động (thuần, JVM)

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/audio/AutoSession.kt` (thêm cuối file)
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/audio/AutoControlRouterTest.kt`

**Interfaces:**
- Consumes: `AutoSide` (Task 3).
- Produces:
  - `interface AutoTransport { fun play(); fun pause(); fun stop(); fun next(); fun previous(); fun fastForward(); fun rewind(); fun seekToMs(positionMs: Long) }`
  - `class AutoControlRouter(private val side: () -> AutoSide, private val commute: AutoTransport, private val podcast: AutoTransport, private val playWhenIdle: () -> Unit)` với cùng tám hàm; `play()` khi `NONE` gọi `playWhenIdle()`, các lệnh khác khi `NONE` không làm gì.

- [ ] **Step 1: Viết test đỏ**

```kotlin
package com.mckimquyen.reader.infrastructure.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoControlRouterTest {

    private class Recorder(private val name: String, private val log: MutableList<String>) : AutoTransport {
        override fun play() { log += "$name.play" }
        override fun pause() { log += "$name.pause" }
        override fun stop() { log += "$name.stop" }
        override fun next() { log += "$name.next" }
        override fun previous() { log += "$name.previous" }
        override fun fastForward() { log += "$name.fastForward" }
        override fun rewind() { log += "$name.rewind" }
        override fun seekToMs(positionMs: Long) { log += "$name.seek:$positionMs" }
    }

    private val log = mutableListOf<String>()
    private var side = AutoSide.NONE
    private val router = AutoControlRouter({ side }, Recorder("commute", log), Recorder("podcast", log)) { log += "idle.play" }

    private fun driveAll() {
        router.play(); router.pause(); router.stop(); router.next(); router.previous()
        router.fastForward(); router.rewind(); router.seekToMs(42_000L)
    }

    @Test
    fun podcastSide_sendsEveryCommandToPodcastOnly() {
        side = AutoSide.PODCAST
        driveAll()

        assertEquals(
            listOf("play", "pause", "stop", "next", "previous", "fastForward", "rewind", "seek:42000").map { "podcast.$it" },
            log,
        )
    }

    @Test
    fun commuteSide_sendsEveryCommandToCommuteOnly() {
        side = AutoSide.COMMUTE
        driveAll()

        assertEquals(
            listOf("play", "pause", "stop", "next", "previous", "fastForward", "rewind", "seek:42000").map { "commute.$it" },
            log,
        )
    }

    @Test
    fun noSide_playStartsTheLatestCommuteEpisodeAndEverythingElseIsIgnored() {
        side = AutoSide.NONE
        driveAll()

        assertEquals(listOf("idle.play"), log)
    }

    @Test
    fun sideIsReadAtCallTime() {
        side = AutoSide.PODCAST
        router.pause()
        side = AutoSide.COMMUTE
        router.pause()

        assertEquals(listOf("podcast.pause", "commute.pause"), log)
    }
}
```

- [ ] **Step 2: Chạy, xác nhận đỏ**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.audio.AutoControlRouterTest" > /tmp/front04-t4.log 2>&1; echo exit=$?`
Expected: `exit=1`, `Unresolved reference: AutoTransport` / `AutoControlRouter`.

- [ ] **Step 3: Cài đặt tối thiểu** (thêm cuối `AutoSession.kt`)

```kotlin
/** The transport commands a car can send, as one engine understands them. */
interface AutoTransport {
    fun play()
    fun pause()
    fun stop()
    fun next()
    fun previous()
    fun fastForward()
    fun rewind()
    fun seekToMs(positionMs: Long)
}

/** Sends each car command to the side [side] reports at that moment; with nothing loaded only Play does anything. */
class AutoControlRouter(
    private val side: () -> AutoSide,
    private val commute: AutoTransport,
    private val podcast: AutoTransport,
    private val playWhenIdle: () -> Unit,
) {
    private fun target(): AutoTransport? = when (side()) {
        AutoSide.COMMUTE -> commute
        AutoSide.PODCAST -> podcast
        AutoSide.NONE -> null
    }

    fun play() { target()?.play() ?: playWhenIdle() }
    fun pause() { target()?.pause() }
    fun stop() { target()?.stop() }
    fun next() { target()?.next() }
    fun previous() { target()?.previous() }
    fun fastForward() { target()?.fastForward() }
    fun rewind() { target()?.rewind() }
    fun seekToMs(positionMs: Long) { target()?.seekToMs(positionMs) }
}
```

- [ ] **Step 4: Chạy, xác nhận xanh**

Expected: `exit=0`, `tests="4" ... failures="0" errors="0"`.

- [ ] **Step 5: Mutation**

- M1 `AutoSide.PODCAST -> podcast` → `-> commute`: hai test đầu đỏ.
- M2 `play()` khi NONE không gọi `playWhenIdle()`: `noSide_play...` đỏ.
- M3 `pause()` khi NONE gọi `commute.pause()` (`(target() ?: commute).pause()`): `noSide_...` đỏ.
- M4 chốt `side()` ở constructor (`private val fixed = side()`): `sideIsReadAtCallTime` đỏ.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/infrastructure/audio/AutoSession.kt app/src/test/java/com/mckimquyen/reader/infrastructure/audio/AutoControlRouterTest.kt
git commit -m "feat(front-04): route car commands to the active side

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

### Task 5: Nhường nhau hai chiều qua `CommuteGate` (mọi đường vào)

Hiện `TtsGate` chỉ nối `TtsManager` (đọc bài), nên podcast ↔ CommuteCast chưa nhường nhau theo chiều nào. Task này nối cả hai chiều ở lớp domain/audio nên đúng cho Auto, màn hình điện thoại (`CommuteCastViewModel`, `PodcastStudioViewModel`), thông báo và lệnh giọng nói. Không đụng audio focus, không đổi engine.

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/sv/CommuteGate.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/audio/CommuteAudioPlayerGate.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/audio/CommuteAudioPlayer.kt` (thêm `onPlayRequested`, gọi ở đầu `speakCurrentDialogue`)
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/sv/PodcastPlaybackController.kt` (tham số thứ 5 `commute: CommuteGate`)
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/di/PodcastModule.kt` (`provideCommuteGate`)
- Test: `app/src/test/java/com/mckimquyen/reader/domain/sv/PodcastPlaybackControllerTest.kt` (sửa `setUp` + 4 test mới), `app/src/test/java/com/mckimquyen/reader/infrastructure/audio/CommuteAudioPlayerTest.kt` (3 test mới), `app/src/test/java/com/mckimquyen/reader/infrastructure/audio/CommuteAudioPlayerGateTest.kt` (mới)

**Interfaces:**
- Produces: `interface CommuteGate { val isPlaying: Boolean; fun pause(); fun setOnPlayRequested(callback: (() -> Unit)?) }`; `class CommuteAudioPlayerGate(player: CommuteAudioPlayer) : CommuteGate`; `CommuteAudioPlayer.onPlayRequested: (() -> Unit)?`; `PodcastPlaybackController(positions, tts, commute, starter, sleepTimer)` — **thứ tự tham số: `positions, tts, commute, starter, sleepTimer`**.
- Ghi chú: `CommuteAudioPlayer` (kéo theo `TextToSpeech`) đã được tạo bất cứ khi nào `ReadingPage` mở (`PodcastStudioViewModel`); gắn nó vào controller không thêm đường khởi tạo mới đáng kể, ghi vào báo cáo.

- [ ] **Step 1: Viết test đỏ**

`CommuteAudioPlayerTest.kt` thêm (cuối lớp):

```kotlin
    @Test
    fun playEpisode_asksOtherSourcesToYield() {
        var asked = 0
        player.onPlayRequested = { asked++ }

        player.playEpisode(sampleEpisode, startFromIndex = 0)

        assertEquals(1, asked)
    }

    @Test
    fun resumeWithAnEpisode_asksOtherSourcesToYield_butPauseAndResumeWithoutOneDoNot() {
        var asked = 0
        player.onPlayRequested = { asked++ }

        player.resume()
        player.pause()
        assertEquals("nothing loaded, nothing to speak", 0, asked)

        player.prepareEpisode(sampleEpisode)
        player.resume()
        assertEquals(1, asked)
    }

    @Test
    fun yieldRequestIsMadeEvenWhileTheEngineIsStillStarting() {
        var asked = 0
        player.onPlayRequested = { asked++ }

        // Robolectric's TTS never finishes onInit: the request is queued, yet other audio must already stop.
        player.playEpisode(sampleEpisode, startFromIndex = 0)

        assertTrue(player.playerState.value.isAwaitingPlayback)
        assertEquals(1, asked)
    }
```

`CommuteAudioPlayerGateTest.kt` (mới):

```kotlin
package com.mckimquyen.reader.infrastructure.audio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.commute.CommuteDialogue
import com.mckimquyen.reader.domain.model.commute.CommuteEpisode
import com.mckimquyen.reader.domain.model.commute.CommuteSpeaker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CommuteAudioPlayerGateTest {

    private lateinit var player: CommuteAudioPlayer
    private lateinit var gate: CommuteAudioPlayerGate
    private val episode = CommuteEpisode("e", "T", Date(0), listOf(CommuteDialogue(CommuteSpeaker.ALEX, "Hi")))

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        player = CommuteAudioPlayer(context)
        gate = CommuteAudioPlayerGate(player)
    }

    @Test
    fun isPlaying_isFalseWhenIdle_andTrueWhileAStartIsPending() {
        assertFalse(gate.isPlaying)

        player.playEpisode(episode, 0) // engine not ready in Robolectric: queued, isAwaitingPlayback = true

        assertTrue("a queued start must still count, or podcast would not yield", gate.isPlaying)
    }

    @Test
    fun pause_cancelsAQueuedStart() {
        player.playEpisode(episode, 0)

        gate.pause()

        assertFalse(gate.isPlaying)
    }

    @Test
    fun setOnPlayRequested_isTheCallbackThePlayerInvokes() {
        var asked = 0
        gate.setOnPlayRequested { asked++ }
        player.playEpisode(episode, 0)
        assertEquals(1, asked)

        gate.setOnPlayRequested(null)
        assertNull(player.onPlayRequested)
    }
}
```

`PodcastPlaybackControllerTest.kt`: thêm sau `FakeTts`:

```kotlin
    private class FakeCommute : CommuteGate {
        override var isPlaying = false
        var pauses = 0
        var callback: (() -> Unit)? = null
        override fun pause() { pauses++; isPlaying = false }
        override fun setOnPlayRequested(callback: (() -> Unit)?) { this.callback = callback }
    }
```
thêm `private lateinit var commute: FakeCommute`; trong `setUp()` sau `tts = FakeTts()`: `commute = FakeCommute()`; đổi dòng 61 thành
`controller = PodcastPlaybackController(positions, tts, commute, { starts++ }, SleepTimer { now })`.
Thêm test:

```kotlin
    @Test fun play_whileCommuteCastPlaying_pausesCommuteCastFirst() {
        attached()
        commute.isPlaying = true
        controller.open("a1", "T", "F", audio)

        controller.play()

        assertEquals(1, commute.pauses)
    }

    @Test fun play_whenCommuteCastIsNotPlaying_leavesItAlone() {
        attached()
        controller.open("a1", "T", "F", audio)

        controller.play()

        assertEquals(0, commute.pauses)
    }

    @Test fun commuteCastStarting_pausesAPlayingPodcast() {
        val engine = attached()
        controller.playEpisode("a1", "T", "F", audio)
        // First start prepares the engine, so the controller reports PREPARING until the first tick; still active.
        assertEquals(PodcastStatus.PREPARING, controller.state.value.status)

        commute.callback?.invoke()

        assertEquals(PodcastStatus.PAUSED, controller.state.value.status)
        assertTrue(engine.calls.contains("pause"))
    }

    @Test fun commuteCastStarting_whilePodcastIsIdle_changesNothing() {
        controller.open("a1", "T", "F", audio)

        commute.callback?.invoke()

        assertEquals(PodcastStatus.PAUSED, controller.state.value.status)
        assertEquals(0, starts)
    }
```

- [ ] **Step 2: Chạy, xác nhận đỏ đúng lý do**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.audio.CommuteAudioPlayerTest" --tests "com.mckimquyen.reader.infrastructure.audio.CommuteAudioPlayerGateTest" --tests "com.mckimquyen.reader.domain.sv.PodcastPlaybackControllerTest" > /tmp/front04-t5.log 2>&1; echo exit=$?`
Expected: `exit=1`, `Unresolved reference: CommuteGate` / `onPlayRequested` / `CommuteAudioPlayerGate`, và `Too many arguments` ở dòng dựng controller. (Biên dịch đỏ trước; sau khi thêm chỉ phần khai báo rỗng, các test hành vi phải đỏ vì chưa gọi callback — thêm khai báo trước, chạy lại, đọc XML đỏ do `expected:<1> but was:<0>` rồi mới làm Step 3.)

- [ ] **Step 3: Cài đặt tối thiểu**

`CommuteGate.kt`:

```kotlin
package com.mckimquyen.reader.domain.sv

/** What the podcast controller needs from CommuteCast, so the two engines yield to each other without a direct dependency. */
interface CommuteGate {
    /** True while CommuteCast is speaking or a start is queued behind the TTS engine. */
    val isPlaying: Boolean
    fun pause()
    fun setOnPlayRequested(callback: (() -> Unit)?)
}
```

`CommuteAudioPlayerGate.kt`:

```kotlin
package com.mckimquyen.reader.infrastructure.audio

import com.mckimquyen.reader.domain.sv.CommuteGate

class CommuteAudioPlayerGate(private val player: CommuteAudioPlayer) : CommuteGate {
    override val isPlaying: Boolean
        get() = player.playerState.value.let { it.isPlaying || it.isAwaitingPlayback }

    override fun pause() = player.pause()

    override fun setOnPlayRequested(callback: (() -> Unit)?) {
        player.onPlayRequested = callback
    }
}
```

`CommuteAudioPlayer.kt`: thêm cạnh `_playerState` (sau `val playerState`):

```kotlin
    /** Called whenever CommuteCast is about to speak, so other audio (podcast) pauses first. */
    var onPlayRequested: (() -> Unit)? = null
```
và đầu `speakCurrentDialogue()` ngay sau dòng `val currentDialogue = ... ?: return`:

```kotlin
        onPlayRequested?.invoke()
```

`PodcastPlaybackController.kt`: constructor thành
`private val positions: PodcastPositionStore, private val tts: TtsGate, private val commute: CommuteGate, private val starter: PodcastServiceStarter, private val sleepTimer: SleepTimer`; trong `init` thêm `commute.setOnPlayRequested { pause() }`; trong `play()` ngay sau dòng `if (tts.isSpeaking) tts.stop()` thêm `if (commute.isPlaying) commute.pause()`.

`PodcastModule.kt` (import `CommuteGate`, `CommuteAudioPlayer`, `CommuteAudioPlayerGate`):

```kotlin
    @Provides
    @Singleton
    fun provideCommuteGate(player: CommuteAudioPlayer): CommuteGate = CommuteAudioPlayerGate(player)
```

- [ ] **Step 4: Chạy, xác nhận xanh**

Cùng lệnh Step 2. Expected `exit=0`; XML ba lớp `failures="0" errors="0"` (số test: `CommuteAudioPlayerTest` +3, `CommuteAudioPlayerGateTest` 3, `PodcastPlaybackControllerTest` +4). Chạy thêm `--tests "com.mckimquyen.reader.ui.page.home.read.podcast.PodcastPlayerViewModelTest"` để chắc không vỡ.

- [ ] **Step 5: Mutation (sao lưu từng file, `cmp`)**

- M1 xoá `onPlayRequested?.invoke()` trong `speakCurrentDialogue`: 3 test `CommuteAudioPlayerTest` mới + `setOnPlayRequested_isTheCallback...` đỏ.
- M2 xoá `if (commute.isPlaying) commute.pause()` trong `play()`: `play_whileCommuteCastPlaying...` đỏ.
- M3 xoá `commute.setOnPlayRequested { pause() }` trong `init`: `commuteCastStarting_pausesAPlayingPodcast` đỏ.
- M4 `isPlaying` bỏ `|| it.isAwaitingPlayback`: `isPlaying_isFalseWhenIdle_andTrueWhilePending` đỏ.
- M5 `pause()` của gate thành rỗng: `pause_cancelsAQueuedStart` đỏ.

- [ ] **Step 6: Commit** (sau khi kiểm exit code build)

```bash
git add app/src/main/java/com/mckimquyen/reader/domain/sv/CommuteGate.kt app/src/main/java/com/mckimquyen/reader/infrastructure/audio/CommuteAudioPlayerGate.kt app/src/main/java/com/mckimquyen/reader/infrastructure/audio/CommuteAudioPlayer.kt app/src/main/java/com/mckimquyen/reader/domain/sv/PodcastPlaybackController.kt app/src/main/java/com/mckimquyen/reader/infrastructure/di/PodcastModule.kt app/src/test/java/com/mckimquyen/reader/domain/sv/PodcastPlaybackControllerTest.kt app/src/test/java/com/mckimquyen/reader/infrastructure/audio/CommuteAudioPlayerTest.kt app/src/test/java/com/mckimquyen/reader/infrastructure/audio/CommuteAudioPlayerGateTest.kt
git commit -m "feat(front-04): make podcast and CommuteCast yield to each other on every path

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

### Task 6: `NOTIFICATION_ID` không trùng

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/podcast/PodcastPlaybackService.kt:203`
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/podcast/ForegroundNotificationIdsTest.kt`

**Interfaces:**
- Consumes: `CommuteMediaSessionService.NOTIFICATION_ID` (9183, giữ), `TtsForegroundService.NOTIFICATION_ID` (9182, giữ), `PodcastPlaybackService.NOTIFICATION_ID`.
- Produces: `PodcastPlaybackService.NOTIFICATION_ID = 9184` (các id foreground 9182–9184 khác nhau; 9184 đã grep, chưa dùng).

- [ ] **Step 1: Viết test đỏ**

```kotlin
package com.mckimquyen.reader.infrastructure.podcast

import com.mckimquyen.reader.infrastructure.audio.CommuteMediaSessionService
import com.mckimquyen.reader.infrastructure.audio.TtsForegroundService
import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundNotificationIdsTest {

    @Test
    fun everyForegroundMediaServiceHasItsOwnNotificationId() {
        val ids = listOf(
            TtsForegroundService.NOTIFICATION_ID,
            CommuteMediaSessionService.NOTIFICATION_ID,
            PodcastPlaybackService.NOTIFICATION_ID,
        )

        assertEquals("two services share a notification id and overwrite each other: $ids", ids.size, ids.toSet().size)
    }

    @Test
    fun existingIdsAreUnchanged() {
        assertEquals(9182, TtsForegroundService.NOTIFICATION_ID)
        assertEquals(9183, CommuteMediaSessionService.NOTIFICATION_ID)
    }
}
```

- [ ] **Step 2: Chạy, đỏ đúng lý do**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.podcast.ForegroundNotificationIdsTest" > /tmp/front04-t6.log 2>&1; echo exit=$?`
Expected: `exit=1`; XML: `everyForegroundMediaServiceHasItsOwnNotificationId` fail với `[9182, 9183, 9183]`; `existingIdsAreUnchanged` pass.

- [ ] **Step 3: Cài đặt tối thiểu**

Dòng 203: `const val NOTIFICATION_ID = 9183` → `const val NOTIFICATION_ID = 9184`.

- [ ] **Step 4: Xanh** — `tests="2" failures="0" errors="0"`; chạy thêm `PodcastServiceManifestTest`.

- [ ] **Step 5: Mutation** — đổi lại thành 9183: test đầu đỏ; đổi `CommuteMediaSessionService.NOTIFICATION_ID` thành 9184: test đầu đỏ và `existingIdsAreUnchanged` đỏ. `cmp` khôi phục.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/infrastructure/podcast/PodcastPlaybackService.kt app/src/test/java/com/mckimquyen/reader/infrastructure/podcast/ForegroundNotificationIdsTest.kt
git commit -m "fix(front-04): give the podcast service its own notification id

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 7: Hai tiêu đề nhánh trong 38 locale

**Files:**
- Modify: `app/src/main/res/values/strings.xml`, `values-vi/strings.xml` và 36 `values-*/strings.xml` còn lại (chèn ngay sau `commute_media_album`)
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/pref/StringResourceParityTest.java` (có sẵn, không sửa)

**Interfaces:**
- Produces: `R.string.auto_branch_commute`, `R.string.auto_branch_podcasts`.
- EN: `Morning briefing`, `Latest podcasts`. VI: `Bản tin sáng`, `Podcast mới nhất`. 36 locale còn lại: bản English (như các lát trước).

- [ ] **Step 1: Test đỏ.** Thêm hai khoá chỉ vào `values/strings.xml` (sau `commute_media_album`), chạy `StringResourceParityTest`. Expected: `exit=1`, XML báo thiếu `auto_branch_commute`/`auto_branch_podcasts` ở 37 locale (đúng lý do: locale thiếu khoá).

```bash
python3 - <<'PY'
import pathlib
p = pathlib.Path('app/src/main/res/values/strings.xml'); s = p.read_text()
anchor = '    <string name="commute_media_album">CommuteCast AI Radio</string>\n'
assert s.count(anchor) == 1
p.write_text(s.replace(anchor, anchor + '    <string name="auto_branch_commute">Morning briefing</string>\n    <string name="auto_branch_podcasts">Latest podcasts</string>\n'))
PY
./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.pref.StringResourceParityTest" > /tmp/front04-t7.log 2>&1; echo exit=$?
```

- [ ] **Step 2: Cài đặt** — chèn vào 37 file còn lại bằng script (chỉ khi chưa có khoá; neo `commute_media_album` bằng regex một dòng; VI dùng bản tiếng Việt, 36 file khác dùng English; không động tới file khác):

```bash
python3 - <<'PY'
import pathlib, re
res = pathlib.Path('app/src/main/res')
VI = ('Bản tin sáng', 'Podcast mới nhất'); EN = ('Morning briefing', 'Latest podcasts')
done = 0
for d in sorted(res.glob('values-*')):
    f = d / 'strings.xml'
    if not f.exists(): continue
    s = f.read_text(encoding='utf-8')
    if 'auto_branch_commute' in s: continue
    m = re.search(r'^[ \t]*<string name="commute_media_album">.*</string>[ \t]*\n', s, re.M)
    assert m, f
    a, b = VI if d.name == 'values-vi' else EN
    add = f'    <string name="auto_branch_commute">{a}</string>\n    <string name="auto_branch_podcasts">{b}</string>\n'
    f.write_text(s[:m.end()] + add + s[m.end():], encoding='utf-8'); done += 1
print('files changed', done)
PY
grep -L "auto_branch_commute" app/src/main/res/values*/strings.xml; grep -c "auto_branch" app/src/main/res/values/strings.xml
for f in app/src/main/res/values*/strings.xml; do xmllint --noout "$f" || echo BAD $f; done
```
Expected: `files changed 37`; `grep -L` rỗng (đủ 38); không `BAD`.

- [ ] **Step 3: Xanh** — chạy lại `StringResourceParityTest`: `exit=0`, `failures="0"`.

- [ ] **Step 4: Mutation** — xoá `auto_branch_podcasts` khỏi một locale ngẫu nhiên (`values-ja`): parity đỏ; khôi phục `cmp`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values*/strings.xml
git commit -m "feat(front-04): add the Android Auto branch titles in all 38 locales

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

### Task 8: Transport của hai engine và callback của session Auto (thuần)

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/audio/AutoSession.kt` (thêm `CommuteAutoTransport`, `PodcastAutoTransport`)
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/audio/AutoSessionCallback.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/audio/AutoTransportsTest.kt`, `app/src/test/java/com/mckimquyen/reader/infrastructure/audio/AutoSessionCallbackTest.kt`

**Interfaces:**
- Consumes: `AutoTransport`, `AutoControlRouter`, `AutoSide` (Task 4).
- Produces:
  - `class CommuteAutoTransport(playLatest: () -> Unit, pauseAction: () -> Unit, stopAction: () -> Unit, skipNext: () -> Unit, skipPrevious: () -> Unit) : AutoTransport` — tua = chuyển câu, `seekToMs` không làm gì.
  - `class PodcastAutoTransport(playAction: () -> Unit, pauseAction: () -> Unit, stopAction: () -> Unit, skipSec: (Int) -> Unit, seekToSec: (Int) -> Unit, skipIntervalSec: Int) : AutoTransport` — next/fastForward = `skipSec(+interval)`, previous/rewind = `skipSec(-interval)`, `seekToMs` = `seekToSec(ms / 1000)`.
  - `internal class AutoSessionCallback(router: AutoControlRouter, playLatestCommute: () -> Unit, playMediaId: (String?) -> Unit, onServiceStop: () -> Unit) : MediaSessionCompat.Callback()`; `onPlayFromSearch` **luôn** gọi `playLatestCommute()` (giữ hành vi hiện có, không tự chọn podcast khi lệnh giọng nói mơ hồ).

- [ ] **Step 1: Viết test đỏ**

`AutoTransportsTest.kt`:

```kotlin
package com.mckimquyen.reader.infrastructure.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoTransportsTest {

    private val log = mutableListOf<String>()

    private val commute = CommuteAutoTransport(
        playLatest = { log += "playLatest" }, pauseAction = { log += "pause" }, stopAction = { log += "stop" },
        skipNext = { log += "skipNext" }, skipPrevious = { log += "skipPrevious" },
    )
    private val podcast = PodcastAutoTransport(
        playAction = { log += "play" }, pauseAction = { log += "pause" }, stopAction = { log += "stop" },
        skipSec = { log += "skip:$it" }, seekToSec = { log += "seek:$it" }, skipIntervalSec = 15,
    )

    @Test
    fun commute_mapsSeekingToAdjacentLinesAndIgnoresSeekTo() {
        commute.play(); commute.pause(); commute.stop()
        commute.next(); commute.previous(); commute.fastForward(); commute.rewind(); commute.seekToMs(5_000L)

        assertEquals(
            listOf("playLatest", "pause", "stop", "skipNext", "skipPrevious", "skipNext", "skipPrevious"),
            log,
        )
    }

    @Test
    fun podcast_nextAndFastForwardSkipForwardByTheInterval_previousAndRewindSkipBack() {
        podcast.next(); podcast.fastForward(); podcast.previous(); podcast.rewind()

        assertEquals(listOf("skip:15", "skip:15", "skip:-15", "skip:-15"), log)
    }

    @Test
    fun podcast_seekConvertsMillisecondsToWholeSeconds() {
        podcast.seekToMs(42_999L)
        podcast.seekToMs(0L)

        assertEquals(listOf("seek:42", "seek:0"), log)
    }

    @Test
    fun podcast_playPauseStopPassThrough() {
        podcast.play(); podcast.pause(); podcast.stop()

        assertEquals(listOf("play", "pause", "stop"), log)
    }
}
```

`AutoSessionCallbackTest.kt`:

```kotlin
package com.mckimquyen.reader.infrastructure.audio

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AutoSessionCallbackTest {

    private val log = mutableListOf<String>()
    private var side = AutoSide.PODCAST

    private fun transport(name: String) = object : AutoTransport {
        override fun play() { log += "$name.play" }
        override fun pause() { log += "$name.pause" }
        override fun stop() { log += "$name.stop" }
        override fun next() { log += "$name.next" }
        override fun previous() { log += "$name.previous" }
        override fun fastForward() { log += "$name.ff" }
        override fun rewind() { log += "$name.rew" }
        override fun seekToMs(positionMs: Long) { log += "$name.seek:$positionMs" }
    }

    private val callback = AutoSessionCallback(
        router = AutoControlRouter({ side }, transport("commute"), transport("podcast")) { log += "idle.play" },
        playLatestCommute = { log += "latestCommute" },
        playMediaId = { log += "mediaId:$it" },
        onServiceStop = { log += "serviceStop" },
    )

    @Test
    fun transportCommandsFollowTheActiveSide() {
        callback.onPlay(); callback.onPause(); callback.onSkipToNext(); callback.onSkipToPrevious()
        callback.onFastForward(); callback.onRewind(); callback.onSeekTo(9_000L)

        assertEquals(
            listOf("play", "pause", "next", "previous", "ff", "rew", "seek:9000").map { "podcast.$it" },
            log,
        )
    }

    @Test
    fun voiceSearchAlwaysStartsTheLatestCommuteEpisode_evenWhilePodcastIsActive() {
        callback.onPlayFromSearch("anything", null)
        callback.onPlayFromSearch(null, null)

        assertEquals(listOf("latestCommute", "latestCommute"), log)
    }

    @Test
    fun playFromMediaId_passesTheIdThroughUntouched() {
        callback.onPlayFromMediaId("podcast:a1", null)
        callback.onPlayFromMediaId(null, null)

        assertEquals(listOf("mediaId:podcast:a1", "mediaId:null"), log)
    }

    @Test
    fun stop_stopsTheActiveSideThenTheService() {
        side = AutoSide.COMMUTE
        callback.onStop()

        assertEquals(listOf("commute.stop", "serviceStop"), log)
    }

    @Test
    fun plainPlayWithNothingLoaded_startsTheLatestCommuteEpisode() {
        side = AutoSide.NONE
        callback.onPlay()

        assertEquals(listOf("idle.play"), log)
    }
}
```

- [ ] **Step 2: Chạy, đỏ đúng lý do**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.audio.AutoTransportsTest" --tests "com.mckimquyen.reader.infrastructure.audio.AutoSessionCallbackTest" > /tmp/front04-t8.log 2>&1; echo exit=$?`
Expected: `exit=1`, `Unresolved reference: CommuteAutoTransport` / `PodcastAutoTransport` / `AutoSessionCallback`.

- [ ] **Step 3: Cài đặt tối thiểu**

Thêm cuối `AutoSession.kt`:

```kotlin
/** CommuteCast has no timeline: "tua" moves one dialogue line and an absolute seek does nothing. */
class CommuteAutoTransport(
    private val playLatest: () -> Unit,
    private val pauseAction: () -> Unit,
    private val stopAction: () -> Unit,
    private val skipNext: () -> Unit,
    private val skipPrevious: () -> Unit,
) : AutoTransport {
    override fun play() = playLatest()
    override fun pause() = pauseAction()
    override fun stop() = stopAction()
    override fun next() = skipNext()
    override fun previous() = skipPrevious()
    override fun fastForward() = skipNext()
    override fun rewind() = skipPrevious()
    override fun seekToMs(positionMs: Long) = Unit
}

/** A podcast has a real timeline, so next/previous and tua move by [skipIntervalSec] and seek is honoured. */
class PodcastAutoTransport(
    private val playAction: () -> Unit,
    private val pauseAction: () -> Unit,
    private val stopAction: () -> Unit,
    private val skipSec: (Int) -> Unit,
    private val seekToSec: (Int) -> Unit,
    private val skipIntervalSec: Int,
) : AutoTransport {
    override fun play() = playAction()
    override fun pause() = pauseAction()
    override fun stop() = stopAction()
    override fun next() = skipSec(skipIntervalSec)
    override fun previous() = skipSec(-skipIntervalSec)
    override fun fastForward() = skipSec(skipIntervalSec)
    override fun rewind() = skipSec(-skipIntervalSec)
    override fun seekToMs(positionMs: Long) = seekToSec((positionMs / MS_PER_SEC).toInt())

    private companion object {
        const val MS_PER_SEC = 1_000L
    }
}
```

`AutoSessionCallback.kt`:

```kotlin
package com.mckimquyen.reader.infrastructure.audio

import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat

/** The car's MediaSession callback: transport goes to the active side, voice search keeps its CommuteCast behaviour. */
internal class AutoSessionCallback(
    private val router: AutoControlRouter,
    private val playLatestCommute: () -> Unit,
    private val playMediaId: (String?) -> Unit,
    private val onServiceStop: () -> Unit,
) : MediaSessionCompat.Callback() {
    override fun onPlay() = router.play()
    override fun onPause() = router.pause()
    override fun onStop() {
        router.stop()
        onServiceStop()
    }
    override fun onSkipToNext() = router.next()
    override fun onSkipToPrevious() = router.previous()
    override fun onFastForward() = router.fastForward()
    override fun onRewind() = router.rewind()
    override fun onSeekTo(pos: Long) = router.seekToMs(pos)

    // An ambiguous spoken request must not pick a podcast for the user; it keeps today's CommuteCast behaviour.
    override fun onPlayFromSearch(query: String?, extras: Bundle?) = playLatestCommute()

    override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) = playMediaId(mediaId)
}
```

- [ ] **Step 4: Xanh** — `exit=0`; `AutoTransportsTest tests="4"`, `AutoSessionCallbackTest tests="5"`, 0 failures.

- [ ] **Step 5: Mutation**

- M1 `CommuteAutoTransport.fastForward = skipPrevious`: test commute đỏ.
- M2 `PodcastAutoTransport.previous = skipSec(skipIntervalSec)`: test podcast đỏ.
- M3 `seekToMs` chia 100 thay vì 1000: `podcast_seek...` đỏ.
- M4 `onPlayFromSearch` gọi `router.play()`: `voiceSearchAlways...` đỏ.
- M5 bỏ `onServiceStop()`: `stop_stopsTheActiveSideThenTheService` đỏ.
- M6 `onSeekTo` thành rỗng: `transportCommandsFollowTheActiveSide` đỏ.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/mckimquyen/reader/infrastructure/audio/AutoSession.kt app/src/main/java/com/mckimquyen/reader/infrastructure/audio/AutoSessionCallback.kt app/src/test/java/com/mckimquyen/reader/infrastructure/audio/AutoTransportsTest.kt app/src/test/java/com/mckimquyen/reader/infrastructure/audio/AutoSessionCallbackTest.kt
git commit -m "feat(front-04): transports and session callback for the car

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

### Task 9: androidTest cho cây hai nhánh, chạy ĐỎ trên Pixel (chưa nối service)

Service chưa có test JVM trực tiếp (Hilt `@AndroidEntryPoint` + `MediaBrowserServiceCompat`), nên phần nối được ép bằng test thật trên máy trước khi viết (Task 10). Phần logic đã được test JVM ở Task 2–8.

**Files:**
- Modify: `app/src/androidTest/java/com/mckimquyen/reader/infrastructure/audio/CommuteMediaBrowserIntegrationTest.kt`

**Interfaces:**
- Consumes: `AutoMediaIds` (Task 2), `CommuteMediaSessionService.MEDIA_ROOT_ID`.
- Produces: 5 test mới + 1 test sửa trong cùng lớp; helper `loadChildren(parentId): ChildrenResult`.

- [ ] **Step 1: Viết test (chưa cài đặt service)**

Trong `CommuteMediaBrowserIntegrationTest.kt` thêm import `android.os.Bundle`, `org.junit.Assert.assertFalse`, `org.junit.Assert.assertNull`; sửa `mediaBrowser_subscribesAndReceivesPlayableEpisodeItem` để đăng ký `AutoMediaIds.COMMUTE_BRANCH` thay cho `CommuteMediaSessionService.MEDIA_ROOT_ID` (hai chỗ: `subscribe(...)`; phần assert giữ nguyên). Thêm cuối lớp:

```kotlin
    private class ChildrenResult(val items: List<MediaBrowserCompat.MediaItem>?, val errored: Boolean)

    /** Connects (if needed), subscribes to [parentId] and waits up to 5 s for either children or an error. */
    private fun loadChildren(parentId: String): ChildrenResult {
        val connected = CountDownLatch(1)
        val done = CountDownLatch(1)
        var items: List<MediaBrowserCompat.MediaItem>? = null
        var errored = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val componentName = ComponentName(context, CommuteMediaSessionService::class.java)
            val callback = object : MediaBrowserCompat.ConnectionCallback() {
                override fun onConnected() {
                    connected.countDown()
                    mediaBrowser?.subscribe(
                        parentId,
                        object : MediaBrowserCompat.SubscriptionCallback() {
                            override fun onChildrenLoaded(parentId: String, children: MutableList<MediaBrowserCompat.MediaItem>) {
                                items = children
                                done.countDown()
                            }

                            override fun onError(parentId: String) {
                                errored = true
                                done.countDown()
                            }
                        },
                    )
                }
            }
            mediaBrowser = MediaBrowserCompat(context, componentName, callback, null).apply { connect() }
        }
        assertTrue("MediaBrowser must connect within 5s", connected.await(5, TimeUnit.SECONDS))
        assertTrue("Children of $parentId must arrive within 5s", done.await(5, TimeUnit.SECONDS))
        return ChildrenResult(items, errored)
    }

    @Test
    fun root_listsTheTwoBrowsableBranchesInOrder() {
        val result = loadChildren(CommuteMediaSessionService.MEDIA_ROOT_ID)

        val items = requireNotNull(result.items) { "root must deliver children" }
        assertEquals(listOf(AutoMediaIds.COMMUTE_BRANCH, AutoMediaIds.PODCAST_BRANCH), items.map { it.mediaId })
        assertTrue("both branches must be browsable", items.all { it.isBrowsable })
        assertTrue("branches are not playable themselves", items.none { it.isPlayable })
        assertTrue("titles must not be blank", items.all { !it.description.title.isNullOrBlank() })
    }

    @Test
    fun commuteBranch_hasAtMostOnePlayableLatestEpisode() {
        val items = requireNotNull(loadChildren(AutoMediaIds.COMMUTE_BRANCH).items)

        assertTrue("at most one item", items.size <= 1)
        items.forEach {
            assertTrue(it.isPlayable)
            assertEquals(AutoMediaIds.COMMUTE_EPISODE, it.mediaId)
        }
    }

    @Test
    fun podcastBranch_hasAtMostFivePlayableItemsAndNeverExposesAnAudioUrl() {
        val items = requireNotNull(loadChildren(AutoMediaIds.PODCAST_BRANCH).items)

        assertTrue("at most ${AutoMediaIds.MAX_PODCAST_ITEMS} items, got ${items.size}", items.size <= AutoMediaIds.MAX_PODCAST_ITEMS)
        items.forEach {
            assertTrue("playable leaf", it.isPlayable)
            assertTrue("id must carry the podcast prefix: ${it.mediaId}", it.mediaId.orEmpty().startsWith(AutoMediaIds.PODCAST_PREFIX))
            assertNull("the tree must not carry an audio URI", it.description.mediaUri)
            val extras: Bundle? = it.description.extras
            extras?.keySet().orEmpty().forEach { key ->
                assertFalse("extra $key must not hold a URL", extras?.get(key).toString().contains("://"))
            }
        }
    }

    @Test
    fun unknownParent_isAnErrorNotAnEmptyList() {
        val result = loadChildren("not_a_real_parent")

        assertTrue("unknown parents must report onError, as before", result.errored)
        assertNull(result.items)
    }

    @Test
    fun playFromMediaId_withUnknownOrHostilePodcastIds_startsNothing() {
        val connected = CountDownLatch(1)
        var controller: MediaControllerCompat? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val componentName = ComponentName(context, CommuteMediaSessionService::class.java)
            val callback = object : MediaBrowserCompat.ConnectionCallback() {
                override fun onConnected() {
                    mediaBrowser?.sessionToken?.let { controller = MediaControllerCompat(context, it) }
                    connected.countDown()
                }
            }
            mediaBrowser = MediaBrowserCompat(context, componentName, callback, null).apply { connect() }
        }
        assertTrue(connected.await(5, TimeUnit.SECONDS))
        val transport = requireNotNull(controller).transportControls

        listOf("podcast:front04-does-not-exist", "podcast:", "podcast:../../x' OR 1=1 --", "something-else").forEach {
            transport.playFromMediaId(it, null)
        }
        Thread.sleep(SETTLE_MS)

        val state = requireNotNull(controller).playbackState?.state
        assertTrue(
            "nothing may be playing or buffering after unknown ids, was $state",
            state == null || (state != PlaybackStateCompat.STATE_PLAYING && state != PlaybackStateCompat.STATE_BUFFERING),
        )
    }
```
và thêm hằng `private companion object { const val SETTLE_MS = 1_500L }` cuối lớp.

- [ ] **Step 2: Biên dịch test**

Run: `./gradlew assembleDevDebugAndroidTest > /tmp/front04-t9-build.log 2>&1; echo exit=$?`
Expected: `exit=0` (test dùng `AutoMediaIds` từ Task 2 nên biên dịch được).

- [ ] **Step 3: Cổng thiết bị chỉ đọc (Pixel `2B051FDH3006MU`)**

```bash
S=2B051FDH3006MU
adb devices
adb -s $S shell dumpsys activity activities | grep -E "topResumedActivity|Active instrumentation|mInstrumentation"
adb -s $S shell dumpsys activity | grep -i "instrumentation" | head
adb -s $S shell ps -A | grep -E "bemytester|skyjoy|mckimquyen" 
adb -s $S shell pm list instrumentation | grep mckimquyen
```
Dừng và hỏi owner nếu có "Active instrumentation" hoặc app lạ ở foreground; màn khoá thì xin owner mở khoá. Pixel nền `bemytester`, `loyalty.skyjoy.vn.*` là bình thường.

- [ ] **Step 4: Backup byte-exact (Pixel)** — dùng lại `/tmp/exc05c-backup.py` nhưng **giới hạn một thiết bị**: sao chép script thành `/tmp/front04-backup.py` với `SERIALS = {'pixel': '2B051FDH3006MU'}` và gốc lưu ở `/tmp/front04-root`; chạy, kiểm in `integrity ok` và kích thước khớp.

- [ ] **Step 5: Cài APK dev debug + test APK và chạy ĐỎ**

```bash
S=2B051FDH3006MU
APK=$(ls -t app/build/outputs/apk/dev/debug/*.apk | head -1); TAPK=$(ls -t app/build/outputs/apk/androidTest/dev/debug/*.apk | head -1)
adb -s $S install -r -t "$APK" && adb -s $S install -r -t "$TAPK"
timeout 300 adb -s $S shell am instrument -w -r -e class com.mckimquyen.reader.infrastructure.audio.CommuteMediaBrowserIntegrationTest com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner > /tmp/front04-t9-red.txt 2>&1; echo exit=$?
grep -E "INSTRUMENTATION_STATUS: test=|INSTRUMENTATION_STATUS_CODE|OK \(|FAILURES" /tmp/front04-t9-red.txt
```
(Tên package/runner: xác minh bằng kết quả `pm list instrumentation` ở Step 3; không đoán.)
Expected đỏ đúng lý do: `root_listsTheTwoBrowsableBranchesInOrder` fail (`expected:<[commute_root, podcast_root]> but was:<[commutecast_latest_episode]>` hoặc rỗng), `commuteBranch_...` và `podcastBranch_...` fail vì `onError` (parent lạ ⇒ `requireNotNull` ném), `mediaBrowser_subscribesAndReceivesPlayableEpisodeItem` (đã sửa) fail tương tự. `unknownParent_isAnErrorNotAnEmptyList` và `playFromMediaId_withUnknown...` **xanh ngay** vì mô tả hành vi hiện có; ghi nhận đúng như vậy (giá trị của chúng là mutation ở Task 10).

- [ ] **Step 6: Không commit riêng nếu đỏ làm hỏng cây** — commit test đỏ được chấp nhận vì không chạm CI cục bộ:

```bash
git add app/src/androidTest/java/com/mckimquyen/reader/infrastructure/audio/CommuteMediaBrowserIntegrationTest.kt
git commit -m "test(front-04): specify the two-branch Android Auto tree on a real device

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```
Ghi vào báo cáo: kết quả đỏ nêu ở Step 5 và đường dẫn `/tmp/front04-t9-red.txt`.

### Task 10: Phát podcast theo id đã kiểm, rồi nối service mỏng

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/audio/AutoPodcastLauncher.kt`
- Test: `app/src/test/java/com/mckimquyen/reader/infrastructure/audio/AutoPodcastLauncherTest.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/audio/CommuteMediaSessionService.kt`
- Delete: `app/src/main/java/com/mckimquyen/reader/infrastructure/audio/PlayFromSearchCallback.kt`, `app/src/test/java/com/mckimquyen/reader/infrastructure/audio/PlayFromSearchCallbackTest.kt` (thay bằng `AutoSessionCallback`; hành vi "voice search luôn phát CommuteCast mới nhất" và "play thường phát khi chưa có gì" được `AutoSessionCallbackTest` giữ lại)

**Interfaces:**
- Consumes: `AutoMediaTree`, `AutoMediaIds`, `PodcastBrowseItem`, `AutoSession*`, `AutoControlRouter`, `AutoSessionCallback`, `AutoTransport`s, `AudioInfo`, `ArticleDao.queryLatestUnreadPodcastEpisodes/queryById`, `PodcastPlaybackController`, `SessionSyncPolicy`.
- Produces: `class AutoPodcastLauncher(tree, loadShown: suspend () -> List<PodcastBrowseItem>, loadAudio: suspend (String) -> AudioInfo?, start: (String, String, String, AudioInfo) -> Unit) { suspend fun play(mediaId: String?): Boolean }`.

#### 10a. `AutoPodcastLauncher` (JVM, TDD)

- [ ] **Step 1: Viết test đỏ**

```kotlin
package com.mckimquyen.reader.infrastructure.audio

import com.mckimquyen.reader.domain.model.article.AudioInfo
import com.mckimquyen.reader.domain.model.podcast.PodcastBrowseItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoPodcastLauncherTest {

    private val tree = AutoMediaTree("Morning", "Podcasts")
    private val shown = (1..8).map { PodcastBrowseItem("a$it", "Title $it", "Feed $it", 60) }
    private val audio = AudioInfo("https://cdn.example.com/a2.mp3", "audio/mpeg", 120)

    private var shownLoads = 0
    private val audioLoads = mutableListOf<String>()
    private val started = mutableListOf<List<Any?>>()
    private var audioResult: AudioInfo? = audio

    private val launcher = AutoPodcastLauncher(
        tree = tree,
        loadShown = { shownLoads++; shown },
        loadAudio = { audioLoads += it; audioResult },
        start = { id, title, feed, info -> started += listOf(id, title, feed, info) },
    )

    @Test
    fun validShownId_startsThatEpisodeWithItsMetadataAndAudio() = runTest {
        assertTrue(launcher.play("podcast:a2"))

        assertEquals(listOf("a2"), audioLoads)
        assertEquals(listOf(listOf("a2", "Title 2", "Feed 2", audio)), started)
    }

    @Test
    fun idNotInTheShownList_startsNothingAndNeverLoadsAudio() = runTest {
        assertFalse(launcher.play("podcast:zzz"))
        assertFalse("a6 exists but was never one of the five shown", launcher.play("podcast:a6"))

        assertTrue(audioLoads.isEmpty())
        assertTrue(started.isEmpty())
    }

    @Test
    fun idsWithoutThePodcastPrefix_areRejectedBeforeAnyQuery() = runTest {
        listOf(null, "", "podcast:", "a2", "commutecast_latest_episode").forEach {
            assertFalse(launcher.play(it))
        }

        assertEquals("a bad id must be rejected before the database is touched", 0, shownLoads)
        assertTrue(audioLoads.isEmpty())
        assertTrue(started.isEmpty())
    }

    @Test
    fun hostileIdWithTheRightPrefix_isLookedUpOnlyAsAnOpaqueKeyAndNeverMatches() = runTest {
        assertFalse(launcher.play("podcast:../../x' OR 1=1 --"))

        assertEquals(1, shownLoads)
        assertTrue(audioLoads.isEmpty())
        assertTrue(started.isEmpty())
    }

    @Test
    fun episodeWithoutPlayableAudio_startsNothing() = runTest {
        audioResult = null

        assertFalse(launcher.play("podcast:a2"))
        assertEquals(listOf("a2"), audioLoads)
        assertTrue(started.isEmpty())
    }
}
```

- [ ] **Step 2: Chạy, đỏ** — `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.audio.AutoPodcastLauncherTest" > /tmp/front04-t10a.log 2>&1; echo exit=$?`. Expected `exit=1`, `Unresolved reference: AutoPodcastLauncher`.

- [ ] **Step 3: Cài đặt tối thiểu**

```kotlin
package com.mckimquyen.reader.infrastructure.audio

import com.mckimquyen.reader.domain.model.article.AudioInfo
import com.mckimquyen.reader.domain.model.podcast.PodcastBrowseItem

/**
 * Starts a podcast episode the car asked for. The id is untrusted input: it must carry the podcast prefix and be one
 * of the episodes currently shown, and only then is the audio URL read for that single article.
 */
class AutoPodcastLauncher(
    private val tree: AutoMediaTree,
    private val loadShown: suspend () -> List<PodcastBrowseItem>,
    private val loadAudio: suspend (articleId: String) -> AudioInfo?,
    private val start: (articleId: String, title: String, feedName: String, audio: AudioInfo) -> Unit,
) {
    /** True when an episode was started. */
    suspend fun play(mediaId: String?): Boolean {
        if (AutoMediaIds.podcastArticleId(mediaId) == null) return false
        val item = tree.resolvePodcast(mediaId, loadShown()) ?: return false
        val audio = loadAudio(item.id) ?: return false
        start(item.id, item.title, item.feedName, audio)
        return true
    }
}
```

- [ ] **Step 4: Xanh** — `tests="5" failures="0" errors="0"`.

- [ ] **Step 5: Mutation** — M1 bỏ dòng `if (AutoMediaIds.podcastArticleId(mediaId) == null) return false`: `idsWithoutThePodcastPrefix_...` đỏ (`shownLoads` khác 0). M2 thay `tree.resolvePodcast(...) ?: return false` bằng `PodcastBrowseItem(AutoMediaIds.podcastArticleId(mediaId)!!, "", "", null)`: `idNotInTheShownList...` và `hostileId...` đỏ. M3 `start(...)` gọi trước `loadAudio` null-check: `episodeWithoutPlayableAudio...` đỏ.

- [ ] **Step 6: Commit** `feat(front-04): launch a podcast only for an id that is currently shown` (kèm Co-Authored-By).

#### 10b. Nối `CommuteMediaSessionService` (mỏng)

Không có test JVM trực tiếp cho service (Hilt + `MediaBrowserServiceCompat`); logic nằm ở các lớp đã test. Cổng kiểm là androidTest Task 9 chạy từ đỏ sang xanh trên Pixel và mutation trên máy.

- [ ] **Step 7: Sửa `CommuteMediaSessionService.kt`**

Imports thêm: `android.os.SystemClock`, `com.mckimquyen.reader.domain.model.article.AudioInfo`, `com.mckimquyen.reader.domain.repository.ArticleDao`, `com.mckimquyen.reader.domain.sv.PodcastPlaybackController`, `com.mckimquyen.reader.domain.model.podcast.SessionSyncPolicy`, `com.mckimquyen.reader.domain.model.podcast.PodcastBrowseItem`, `com.mckimquyen.reader.ui.ext.currentAccountId`, `kotlinx.coroutines.CancellationException`, `kotlinx.coroutines.flow.combine`, `kotlinx.coroutines.withContext`. Bỏ import không dùng nếu còn.

Trường thêm (cạnh `episodeStore`):

```kotlin
    @Inject
    lateinit var podcast: PodcastPlaybackController

    @Inject
    lateinit var articleDao: ArticleDao

    private val sideTracker = AutoSideTracker()
    private val podcastSync = SessionSyncPolicy()
    private var activeSide = AutoSide.NONE
    private var renderedSide = AutoSide.NONE
    private var lastMetadataKey: Triple<AutoSide, String, String>? = null
    private val tree by lazy {
        AutoMediaTree(getString(R.string.auto_branch_commute), getString(R.string.auto_branch_podcasts))
    }
    private val podcastLauncher by lazy {
        AutoPodcastLauncher(
            tree = tree,
            loadShown = ::loadPodcastItems,
            loadAudio = ::loadPodcastAudio,
            start = podcast::playEpisode,
        )
    }
```
(`@Inject lateinit var` là mẫu sẵn có của service này — Hilt field injection; giữ comment hiện có.)

Thay khối `setCallback(object : PlayFromSearchCallback(...) {...})` bằng:

```kotlin
            setCallback(
                AutoSessionCallback(
                    router = AutoControlRouter(
                        side = { activeSide },
                        commute = CommuteAutoTransport(
                            playLatest = ::resumeOrPlayLatestEpisode,
                            pauseAction = audioPlayer::pause,
                            stopAction = audioPlayer::stopAndReset,
                            skipNext = audioPlayer::skipNext,
                            skipPrevious = audioPlayer::skipPrevious,
                        ),
                        podcast = PodcastAutoTransport(
                            playAction = podcast::play,
                            pauseAction = podcast::pause,
                            stopAction = podcast::stop,
                            skipSec = podcast::skip,
                            seekToSec = podcast::seekTo,
                            skipIntervalSec = PodcastPlaybackController.SKIP_INTERVAL_SEC,
                        ),
                        playWhenIdle = ::resumeOrPlayLatestEpisode,
                    ),
                    playLatestCommute = ::resumeOrPlayLatestEpisode,
                    playMediaId = ::playFromMediaId,
                    onServiceStop = ::stopServiceGracefully,
                )
            )
```
và ngay sau `sessionToken = mediaSession?.sessionToken` trong `onCreate`:

```kotlin
        scope.launch {
            combine(podcast.state, audioPlayer.playerState) { p, c -> p to c }.collect { (p, c) -> renderSession(p, c) }
        }
```

Thêm các hàm:

```kotlin
    private fun playFromMediaId(mediaId: String?) {
        if (mediaId == AutoMediaIds.COMMUTE_EPISODE) {
            resumeOrPlayLatestEpisode()
            return
        }
        scope.launch {
            val started = try {
                podcastLauncher.play(mediaId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not start the requested podcast", e)
                false
            }
            if (!started) Log.w(TAG, "onPlayFromMediaId: ignored mediaId=$mediaId")
        }
    }

    private suspend fun loadPodcastItems(): List<PodcastBrowseItem> = withContext(Dispatchers.IO) {
        articleDao.queryLatestUnreadPodcastEpisodes(applicationContext.currentAccountId, AutoMediaIds.MAX_PODCAST_ITEMS)
    }

    private suspend fun loadPodcastAudio(articleId: String): AudioInfo? = withContext(Dispatchers.IO) {
        val article = articleDao.queryById(articleId)?.article ?: return@withContext null
        val url = article.audioUrl?.takeIf { it.isNotBlank() } ?: return@withContext null
        AudioInfo(url, article.audioMime, article.audioDurationSec)
    }

    /** Mirrors whichever engine is active into the car session; the podcast tick is throttled like its own service. */
    private fun renderSession(p: PodcastPlaybackState, c: CommutePlayerState) {
        val side = sideTracker.update(p, c)
        activeSide = side
        val snapshot = when (side) {
            AutoSide.PODCAST -> AutoSessionMapper.podcast(p)
            AutoSide.COMMUTE -> AutoSessionMapper.commute(c, getString(R.string.commute_media_artist))
            AutoSide.NONE -> return
        }
        val sideChanged = side != renderedSide
        renderedSide = side
        val needsState = sideChanged || side == AutoSide.COMMUTE ||
            podcastSync.needsUpdate(p.status, p.speed, p.positionSec, SystemClock.elapsedRealtime())
        if (needsState) {
            val builder = PlaybackStateCompat.Builder()
                .setActions(snapshot.actions)
                .setState(snapshot.state, snapshot.positionMs, snapshot.speed)
            if (snapshot.state == PlaybackStateCompat.STATE_ERROR) {
                builder.setErrorMessage(PlaybackStateCompat.ERROR_CODE_APP_ERROR, getString(R.string.podcast_error))
            }
            mediaSession?.setPlaybackState(builder.build())
        }
        val key = Triple(side, snapshot.title, snapshot.subtitle)
        if (key != lastMetadataKey) {
            lastMetadataKey = key
            mediaSession?.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, snapshot.title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, snapshot.subtitle)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, snapshot.durationMs)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, getOrDecodeArtwork())
                    .build()
            )
        }
    }
```
(import `com.mckimquyen.reader.domain.model.podcast.PodcastPlaybackState`.) Nếu `needsUpdate` ghi `last` khi `sideChanged` đã `true` thì `||` ngắn mạch bỏ qua `needsUpdate`, làm `SessionSyncPolicy` giữ snapshot cũ ⇒ sau lần đổi bên có thể gửi dư một lần; chấp nhận, ghi vào báo cáo nếu quan sát thấy.

`onLoadChildren` mới:

```kotlin
    override fun onLoadChildren(parentId: String, result: Result<List<MediaItem>>) {
        result.detach()
        scope.launch {
            val children = try {
                val podcasts = if (parentId == AutoMediaIds.PODCAST_BRANCH) loadPodcastItems() else emptyList()
                val commute = if (parentId == AutoMediaIds.COMMUTE_BRANCH) {
                    (audioPlayer.playerState.value.episode ?: episodeStore.load())
                        ?.let { CommuteEntry(it.title, it.dialogues.firstOrNull()?.text.orEmpty()) }
                } else {
                    null
                }
                tree.children(parentId, commute, podcasts)?.map { it.toMediaItem() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not load children of $parentId", e)
                emptyList()
            }
            result.sendResult(children)
        }
    }

    private fun AutoNode.toMediaItem(): MediaItem {
        val extras = durationSec?.let { Bundle().apply { putLong(MediaMetadataCompat.METADATA_KEY_DURATION, it * MILLIS_PER_SECOND) } }
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(mediaId)
            .setTitle(title)
            .setSubtitle(subtitle)
            .setIconBitmap(getOrDecodeArtwork())
            .setExtras(extras)
            .build()
        return MediaItem(description, if (browsable) MediaItem.FLAG_BROWSABLE else MediaItem.FLAG_PLAYABLE)
    }
```
(`import android.os.Bundle` hoặc giữ `android.os.Bundle` đầy đủ như file hiện tại; trong companion thêm `private const val MILLIS_PER_SECOND = 1_000L`.) Xoá `CommuteEpisode.toBrowsableMediaItem()` cũ và import `CommuteEpisode` nếu không còn dùng. `onGetRoot` trả `BrowserRoot(AutoMediaIds.ROOT, null)` (hằng `MEDIA_ROOT_ID` giữ nguyên, trỏ cùng giá trị; đổi `const val MEDIA_ROOT_ID = AutoMediaIds.ROOT`, `MEDIA_ID_LATEST_EPISODE = AutoMediaIds.COMMUTE_EPISODE` để một nguồn sự thật).
Trong `onStartCommand`: xoá hai dòng `updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)` và `updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)` (state do `renderSession` dẫn); `updatePlaybackState` còn dùng ở `stopServiceGracefully`. `publishMetadata` giữ nguyên (mô tả hiển thị có lời thoại) — ghi chú: `renderSession` có thể ghi đè bằng metadata ngắn gọn hơn khi key đổi; quan sát trên DHU.

Xoá `PlayFromSearchCallback.kt` và `PlayFromSearchCallbackTest.kt` (`git rm`).

- [ ] **Step 8: Biên dịch + toàn bộ test lớp audio**

Run: `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.audio.*" > /tmp/front04-t10b.log 2>&1; echo exit=$?` rồi `./gradlew assembleDevDebug assembleDevDebugAndroidTest > /tmp/front04-t10b-build.log 2>&1; echo exit=$?`. Expected `exit=0` cả hai; XML các lớp `failures="0" errors="0"`.

- [ ] **Step 9: Cổng thiết bị (chỉ đọc) rồi chạy androidTest XANH trên Pixel**

Lặp cổng Step 3 của Task 9; `adb -s 2B051FDH3006MU install -r -t` APK và test APK mới; chạy `timeout 300 adb -s 2B051FDH3006MU shell am instrument -w -r -e class com.mckimquyen.reader.infrastructure.audio.CommuteMediaBrowserIntegrationTest <package>.test/androidx.test.runner.AndroidJUnitRunner > /tmp/front04-t10-green.txt 2>&1`.
Expected: `OK (N tests)`; mọi test lớp xanh, gồm 5 test Task 9 từ đỏ sang xanh.

- [ ] **Step 10: Mutation trên máy (mỗi lần biên dịch + cài + chạy lớp; khôi phục `cmp`)**

- M1 `onGetRoot` trả `BrowserRoot("other", null)`: `mediaBrowser_connectsAndDeliversRoot` đỏ.
- M2 `AutoNode.toMediaItem` luôn `FLAG_PLAYABLE`: `root_listsTheTwoBrowsableBranches...` đỏ.
- M3 `onLoadChildren` luôn `tree.children(AutoMediaIds.ROOT, ...)`: `commuteBranch...`/`podcastBranch...` đỏ.
- M4 `loadPodcastItems` dùng limit 50: không có test androidTest bắt (DB thật có thể ít tập); ghi nhận trung thực là **không bắt được trên máy**, đã bắt ở `AutoMediaTreeTest` (cắt 5) và `ArticleDaoPodcastBrowseTest` (limit).
- M5 `unknownParent`: thay `tree.children(...)` cuối bằng `?: emptyList()`: `unknownParent_isAnErrorNotAnEmptyList` đỏ.

- [ ] **Step 11: Commit** (sau khi kiểm exit code của mọi build/test)

```bash
git rm -q app/src/main/java/com/mckimquyen/reader/infrastructure/audio/PlayFromSearchCallback.kt app/src/test/java/com/mckimquyen/reader/infrastructure/audio/PlayFromSearchCallbackTest.kt
git add app/src/main/java/com/mckimquyen/reader/infrastructure/audio/CommuteMediaSessionService.kt
git status --short | grep -v idea
git commit -m "feat(front-04): serve the two-branch tree and mirror the active engine in the car session

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

### Task 11: Full suite, lint, build

**Files:** không sửa mã (chỉ sửa nếu một bước tìm ra lỗi thật; khi đó quay về TDD cho lỗi đó).

- [ ] **Step 1: Cây sạch và HEAD**

```bash
git status --short | grep -v idea; git log --oneline -1
```
Expected: không còn thay đổi chưa commit ngoài `.idea`.

- [ ] **Step 2: Full unit suite trên code cuối**

```bash
./gradlew testDevDebugUnitTest --rerun-tasks > /tmp/front04-full.log 2>&1; echo exit=$?
python3 - <<'PY'
import glob, re
t=f=e=s=0; files=glob.glob('app/build/test-results/testDevDebugUnitTest/*.xml')
for x in files:
    m = re.search(r'tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"', open(x, encoding='utf-8').read(2000))
    a,b,c,d = map(int, m.groups()); t+=a; s+=b; f+=c; e+=d
print('files', len(files), 'tests', t, 'skipped', s, 'failures', f, 'errors', e)
PY
```
Expected: `exit=0`, `failures 0 errors 0`. Số test phải ≥ 2,673 + các test mới của FRONT-04 (6 + 9 + 14 + 4 + 3+3+4 + 2 + 4 + 5 + 5 ≈ 59, trừ 2 test `PlayFromSearchCallbackTest` đã xoá); báo số thật, không làm tròn. Kiểm không có file XML cũ (so thời gian sửa với lúc bắt đầu `--rerun-tasks`).

- [ ] **Step 3: Lint**

```bash
./gradlew lintDevDebug --rerun-tasks > /tmp/front04-lint.log 2>&1; echo exit=$?
python3 - <<'PY'
import re
s = open('app/build/reports/lint-results-devDebug.xml', encoding='utf-8').read()
print('errors', len(re.findall(r'severity="Error"', s)), 'warnings', len(re.findall(r'severity="Warning"', s)))
for f in ['AutoMediaTree', 'AutoSession', 'AutoPodcastLauncher', 'CommuteGate', 'CommuteAudioPlayerGate', 'CommuteMediaSessionService']:
    print(f, len(re.findall(f + r'\.kt', s)))
PY
```
Expected: 0 lỗi, cảnh báo ≈ baseline 2,463 (báo số thật), 0 hit ở file mới. Nếu lint báo `MissingPermission`/`ForegroundServiceType` ở service, đọc nguyên văn và xử lý bằng TDD hoặc ghi vào báo cáo.

- [ ] **Step 4: Build APK**

```bash
./gradlew assembleDevDebug assembleDevDebugAndroidTest > /tmp/front04-assemble.log 2>&1; echo exit=$?
```
Expected `exit=0`.

- [ ] **Step 5: Nếu có thay đổi nào ở Step 1–4, commit riêng** (`fix(front-04): ...`); nếu không thì không commit.

---

### Task 12: Smoke DHU + Pixel, báo cáo DONE, cập nhật tài liệu

Android Auto cần **owner thao tác tay**: tôi không đọc được cài đặt Developer Mode hay nút "Start head unit server" qua adb. Không hứa kết quả trước khi chạy; ghi trung thực bước nào chưa làm được. Không có xe thật hay head unit thật; KJ7 không có Android Auto nên **không dùng** cho DHU.

**Files:**
- Create: `doc/task/done/FRONT-04a_android-auto-media-tree_DONE.md`
- Modify: `doc/feature.md`

- [ ] **Step 1: Cổng Pixel chỉ đọc** (như Task 9 Step 3) và backup byte-exact bằng `/tmp/front04-backup.py` (Task 9 Step 4; chạy lại để có bản mới trước smoke). Xác nhận ngày/giờ và `topResumedActivity`.

- [ ] **Step 2: Dữ liệu thử** — Pixel có thể không có tập audio chưa đọc. Dựng fixture trong Room trên **bản đã backup** (cùng cách `/tmp/exc05c-fixture.py`: dừng app, sửa bản sao, ghi lại byte-exact, kiểm hai chiều): thêm một feed `https://front04.invalid/feed.xml` và 7 bài có `audioUrl` (`http://<IP máy Mac>:8099/ep<N>.mp3`, `audioDurationSec` 60–300, `isUnread=1`, ngày khác nhau), thêm 1 bài đã đọc có audio, 1 bài chưa đọc không audio, để kiểm lọc. Trên Mac phát file thật: `say -o /tmp/front04/ep1.aiff "Episode one"; afconvert -f mp4f -d aac /tmp/front04/ep1.aiff /tmp/front04/ep1.m4a` và `python3 -m http.server 8099 --directory /tmp/front04` (đổi `mime` thành `audio/mp4`, đuôi `.m4a`). Pixel và Mac cùng Wi-Fi (kiểm `ipconfig getifaddr en0` và `ping` từ Pixel bằng `adb shell ping -c1 <ip>`; Pixel `192.168.21.185`, Mac `192.168.21.83` lúc lập plan, đọc lại).
  Cẩn thận: `run-as` không `--user`; không xoá DataStore; kiểm `pragma integrity_check` và `foreign_key_check` trước khi ghi lại.

- [ ] **Step 3: Owner chuẩn bị DHU (thao tác tay, tôi chờ báo "xong")**

Owner làm trên Pixel: Cài đặt → Ứng dụng → Android Auto → (mở) → chạm nhiều lần vào "Version" để bật Developer Mode → menu ⋮ → "Start head unit server". Cho Android Auto hiển thị trạng thái server đang chạy. Tôi không đọc được màn đó bằng adb; chỉ báo qua ảnh chụp nếu owner cho phép.

- [ ] **Step 4: Chạy DHU (tôi làm sau khi owner báo xong)**

```bash
adb -s 2B051FDH3006MU forward tcp:5277 tcp:5277
~/Library/Android/sdk/extras/google/auto/desktop-head-unit --usb 2>&1 | head -5 || true
# nếu --usb không dùng được: chạy không tham số sau forward
~/Library/Android/sdk/extras/google/auto/desktop-head-unit > /tmp/front04-dhu.log 2>&1 &
```
Ghi `exit`, nội dung `/tmp/front04-dhu.log`; nếu DHU không kết nối (ví dụ lệch phiên bản, Gatekeeper chặn file arm64) ghi nguyên văn lỗi và **dừng ở đây** — báo cáo "chưa kiểm được trên DHU", không bịa kết quả.

- [ ] **Step 5: Kịch bản DHU** (chụp màn DHU bằng `screencapture -x /tmp/front04-dhu-<n>.png` cửa sổ DHU, đọc bằng công cụ xem ảnh; chạm bằng chuột/lệnh DHU `tap` nếu cần — lệnh trong terminal DHU: `tap x y`; nếu không đọc được ảnh thì ghi như vậy)

1. Mở "RSS Cat Hub" trong danh sách media của DHU. Kỳ vọng: hai mục "Bản tin sáng/Morning briefing" và "Podcast mới nhất/Latest podcasts" (theo ngôn ngữ hệ thống).
2. Mở "Podcast mới nhất": thấy tối đa 5 tập (đúng thứ tự mới → cũ, không có bài đã đọc/không audio).
3. Phát một tập: kiểm `adb -s 2B051FDH3006MU shell dumpsys media_session | grep -A12 "RSS Cat Hub\|mckimquyen"` thấy state PLAYING và metadata đúng; thông báo foreground riêng (id 9184) và không còn ghi đè.
4. Tạm dừng rồi tua tới/lui bằng DHU: vị trí thay đổi đúng ±15 s.
5. Mở "Bản tin sáng" và phát (cần có tập CommuteCast; nếu chưa có thì tạo qua màn hình điện thoại trước, ghi rõ). Kỳ vọng: podcast tự tạm dừng; điều khiển DHU lúc này tua theo câu, không tua theo giây.
6. Từ **màn hình điện thoại** (không qua Auto) bấm phát podcast khi CommuteCast đang phát: CommuteCast phải dừng (kiểm chiều "mọi đường vào").
7. Ghi các điều không kiểm được: Maps/cuộc gọi chiếm focus, hai cơ chế focus chồng nhau khi đổi bên, giọng nói thật, xe thật.
Quảng cáo toàn màn hình trên Pixel: chỉ Back, không chạm "Tiếp tục đến ứng dụng", kiểm lại foreground; nếu ad che UI khi cần chụp, dừng và báo owner theo quy tắc cũ.

- [ ] **Step 6: Dọn và khôi phục**

Dừng DHU (`kill` tiến trình đã mở), `adb -s 2B051FDH3006MU forward --remove tcp:5277`, dừng HTTP server, `force-stop com.mckimquyen.reader`, khôi phục Room/DataStore/pairing bằng `/tmp/front04-root` (cùng cách `/tmp/exc05c-restore.py` giới hạn một thiết bị) và xác minh **byte-identical**; không để trạng thái Developer Mode cho owner: nhắc owner tự tắt "Start head unit server" nếu muốn (tôi không đọc/ghi được).

- [ ] **Step 7: Viết `doc/task/done/FRONT-04a_android-auto-media-tree_DONE.md`** (EN như các báo cáo trước): Scope (chỉ Android Auto, FRONT-04 gốc mở, Wear OS tách), Implemented, Verification JVM (số test thật, lint thật, build), Verification device/DHU (bước đã làm và bước **không** làm được, nguyên văn lỗi), Found on device (nếu có), Not verified/limits: client Auto chỉ được kiểm bằng tên package (không chữ ký); "chưa nghe" = cờ chưa đọc, không phải vị trí phát; hai MediaSession cùng tồn tại; `CommuteAudioPlayer` (TextToSpeech) được khởi tạo sớm hơn khi podcast nhận `CommuteGate`; hai cơ chế audio focus có thể chồng nhau; xe thật/giọng nói/Maps/cuộc gọi chưa kiểm; ảnh bìa là icon app; chuỗi mới là English ở 36 locale.

- [ ] **Step 8: Cập nhật `doc/feature.md`** — mục 2 (Picked): "lát Android Auto đã làm xong, xem `doc/task/done/FRONT-04a_...`, FRONT-04 gốc (Wear OS, kiểm trên xe thật) vẫn mở"; thêm một dòng vào ✅ Implemented (theo mẫu EXC-05c) với số liệu thật; mục 3 RPG-02 thành bước kế tiếp. Không đề nghị PR, worktree hay làm cả ba feature còn lại cùng lúc.

- [ ] **Step 9: Commit tài liệu**

```bash
git add doc/task/done/FRONT-04a_android-auto-media-tree_DONE.md doc/feature.md
git commit -m "docs(front-04): report the Android Auto slice with its verified and unverified parts

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

## Tự soát plan (đối chiếu spec)

| Yêu cầu spec | Task |
|---|---|
| §3 cây hai nhánh, id có tiền tố, id lạ → null/bỏ qua, tối đa 5, rỗng hợp lệ | 2, 10b, 9 |
| §3 truy vấn chỉ cột nhỏ, đúng tài khoản/chưa đọc/có audio, thứ tự `date DESC, id DESC`, không đọc thân bài | 1 |
| §4 `onPlayFromMediaId`: kiểm id còn trong kết quả, lấy `AudioInfo` đúng bài, lỗi chỉ log | 10a, 10b |
| §4 nhường nhau hai chiều, mọi đường vào | 5 (và smoke Task 12 bước 6) |
| §4 session Auto phản ánh bên hoạt động; seek theo giây cho podcast; CommuteCast giữ skip-line, `onSeekTo` no-op; ERROR → `STATE_ERROR`; không bên nào → hành vi cũ | 3, 4, 8, 10b |
| §4 `NOTIFICATION_ID` khác nhau + test | 6 |
| §4 thu thập `state` trong `scope`, huỷ ở `onDestroy` | 10b (`scope.launch` dùng `scope` hiện có bị `scope.cancel()` ở `onDestroy`) |
| §5 không nâng cấp validator, không URL trong cây, id không dùng làm đường dẫn/truy vấn | 2, 9, 10a |
| §5 truy vấn ngoài luồng chính, mọi coroutine thuộc `scope` | 10b (`withContext(Dispatchers.IO)`, `result.detach()`) |
| §6 chuỗi 38 locale + parity | 7 |
| §7 mutation: tiền tố id, giới hạn 5, nhường nhau hai chiều, kiểm id, `NOTIFICATION_ID` | 2, 1/2, 5, 2/10a, 6 |
| §7 androidTest không `connected*` | 9, 10b |
| §7 full suite `--rerun-tasks`, lint, assemble debug + androidTest | 11 |
| §7 DHU thủ công + báo cáo trung thực | 12 |
| §8 ngoài phạm vi nêu rõ, FRONT-04 gốc giữ ở todo | Global Constraints, 12 |

Chưa có chỗ trống placeholder: mọi bước có mã/lệnh thật. Điểm cần chú ý khi thực thi (không phải TODO mà là rủi ro đã biết): (a) `PodcastPlaybackControllerTest` đổi chữ ký constructor ở Task 5; `PodcastPlayerViewModelTest` và mọi nơi dựng `PodcastPlaybackController` bằng tay phải biên dịch lại (đã grep: chỉ dòng 61 của `PodcastPlaybackControllerTest`); (b) Task 10b có thể làm `renderSession` gửi metadata ngắn gọn hơn `publishMetadata`; quan sát trên DHU; (c) fixture Room Task 12 phải chạy trên bản backup đã kiểm, không chạm DataStore.
