# [IDEA-05] Podcast & Audio RSS Player Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Store the audio enclosure of each RSS item and add a player bar to the Reader with background playback, 15-second skips, speed 1.0x–2.0x, a sleep timer and a remembered position.

**Architecture:** `RssHelper` extracts an `AudioInfo` from each entry into three nullable `article` columns (Room v18). A Hilt singleton `PodcastPlaybackController` owns the playback state (`StateFlow`) and talks to an engine port; `PodcastPlaybackService` (foreground, `mediaPlayback`) creates the real `ExoPlayer` engine, attaches it to the controller and exposes a `MediaSessionCompat` + notification. A Compose `PodcastPlayerBar` in the Reader reads the state through `PodcastPlayerViewModel`. Podcast and TTS pause each other.

**Tech Stack:** Kotlin 2.1.0, Room (KAPT), Hilt, Rome 2.1.0 (foreign markup for `<itunes:duration>`), media3 1.4.1 (`media3-exoplayer`, `media3-datasource`, already present), `MediaSessionCompat`, `NotificationCompat`, Jetpack Compose, JUnit4, MockK, Robolectric 4.14.1.

## Global Constraints

- Store the audio reference as three nullable columns on `article` (`audioUrl`, `audioMime`, `audioDurationSec`), Room v18 via `MIGRATION_17_18`.
- Only `https` enclosure URLs are accepted.
- Play with an `ExoPlayer` (media3 1.4.1) hosted by a new `PodcastPlaybackService` with a `MediaSessionCompat`; do not change `TtsForegroundService` behaviour or `CommuteMediaSessionService`.
- The player bar lives inside the Reader, between the content and the `BottomBar`; it does not touch the banner ad.
- The listening position is remembered per article.
- Podcast and TTS never play at once: starting one pauses the other.
- Verification feed is NPR (`https://feeds.npr.org/510289/podcast.xml`), not BBC (BBC enclosures are `http://` on an unreachable host).
- Speeds `1.0, 1.25, 1.5, 1.75, 2.0`; skip interval `SKIP_INTERVAL_SEC = 15`; sleep timer options 15, 30, 45 minutes.
- No magic numbers: intervals, caps, speeds and thresholds are named constants.
- Controller methods are called on the main thread only (ExoPlayer is single-threaded); ViewModels and the controller never hold an `Activity`.
- Localize every new string across all 38 maintained locale files (`values` + 37 `values-*`); keep `StringResourceParityTest` green; Vietnamese keeps full diacritics.
- Live Android work only on the Tecno: `adb -s 115333744A005844`; never run Gradle `connected*` tasks. Check the foreground window with `dumpsys activity activities | grep topResumedActivity` before every action, take a screenshot after every step, never chain several taps in one command, and stop immediately if a full-screen ad (`com.google.android.gms.ads.AdActivity`) covers the UI, then wait for the user's "done". Banner ads that do not cover the UI are skipped. If adb reports `device not found`, that is a connection problem to report, not a code failure.
- Keep `.idea/**` untouched. No ad, VIP or IAP behaviour is changed (deferred by the owner).

## Known limits (record in the completion report)

- `ArticleDao.insert` uses `REPLACE`, so a Fever re-fetch can reset the new audio columns to NULL (same limitation as `readAt`).
- Articles stored before the upgrade have no audio until their feed is fetched again and the article is re-inserted; the existing `insertListIfNotExist` skips articles that already exist, so old items stay without a player.
- `DefaultHttpDataSource` does not follow an HTTPS→HTTP redirect; a feed whose redirect chain drops to HTTP will show the error state. This matches the HTTPS-only rule.
- Sound quality, Bluetooth and headset output cannot be verified from adb; evidence is player state, `dumpsys media_session` and logcat.

---

### Task 1: `AudioDuration` and `AudioEnclosure` (pure, TDD)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/article/AudioInfo.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/article/AudioDuration.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/article/AudioEnclosure.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/domain/model/article/AudioDurationTest.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/domain/model/article/AudioEnclosureTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class AudioInfo(val url: String, val mime: String?, val durationSec: Int?)

  object AudioDuration { fun parse(text: String?): Int? }   // "SS", "MM:SS", "HH:MM:SS"; null when invalid/blank/negative

  object AudioEnclosure {
      fun from(entry: com.rometools.rome.feed.synd.SyndEntry): AudioInfo?
  }
  ```
  `AudioEnclosure.from` picks the first enclosure whose `type` starts with `audio/` (case-insensitive), or — when `type` is null/blank — whose URL path ends with `.mp3`, `.m4a`, `.aac` or `.ogg`; accepts it only when the URL scheme is `https`. The duration comes from the entry's foreign markup: the first JDOM `Element` with `name == "duration"` and `namespacePrefix == "itunes"` (Rome 2.1.0 has no iTunes module in `rome-2.1.0.jar`; verified with `unzip -l`, and `SyndEntry.getForeignMarkup()` exists).

- [ ] **Step 1: Write tests (RED)**
  - `AudioDurationTest`: `"45"→45`, `"3:05"→185`, `"01:02:03"→3723`, `" 12:30 "→750` (trimmed), `null→null`, `""→null`, `"abc"→null`, `"1:2:3:4"→null`, `"-5"→null`, `"00:00"→0`, `"1913"→1913` (BBC/NPR style plain seconds), `"90:00"→5400`.
  - `AudioEnclosureTest` builds real entries with `SyndFeedInput().build(StringReader(rssXml)).entries.first()` (XML with `xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd"`), so the foreign-markup path is exercised for real:
    - `audio/mpeg` enclosure + `<itunes:duration>1913</itunes:duration>` → `AudioInfo("https://x/e.mp3", "audio/mpeg", 1913)`.
    - missing `type` with `.m4a` URL → accepted, `mime = null`.
    - image enclosure only → null.
    - `http://` audio URL → null.
    - two enclosures (image first, audio second) → the audio one; two audio enclosures → the first.
    - no enclosure → null.
    - audio enclosure without `<itunes:duration>` → `durationSec = null`.
    - `<itunes:duration>12:34</itunes:duration>` → 754.
    - uppercase type `AUDIO/MPEG` accepted.

- [ ] **Step 2: Run, confirm RED** — `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.model.article.AudioDurationTest" --tests "com.mckimquyen.reader.domain.model.article.AudioEnclosureTest"` → compilation failure.

- [ ] **Step 3: Implement**

`AudioDuration.kt`:
```kotlin
package com.mckimquyen.reader.domain.model.article

object AudioDuration {
    private const val SECONDS_PER_MINUTE = 60
    private const val MAX_PARTS = 3

    fun parse(text: String?): Int? {
        val parts = text?.trim()?.takeIf { it.isNotEmpty() }?.split(':') ?: return null
        if (parts.size > MAX_PARTS) return null
        val numbers = parts.map { it.trim().toIntOrNull()?.takeIf { n -> n >= 0 } ?: return null }
        return numbers.fold(0) { total, part -> total * SECONDS_PER_MINUTE + part }
    }
}
```
`AudioEnclosure.kt`:
```kotlin
package com.mckimquyen.reader.domain.model.article

import com.rometools.rome.feed.synd.SyndEnclosure
import com.rometools.rome.feed.synd.SyndEntry
import java.net.URI

object AudioEnclosure {
    private const val AUDIO_MIME_PREFIX = "audio/"
    private const val HTTPS = "https"
    private const val ITUNES_PREFIX = "itunes"
    private const val DURATION_NAME = "duration"
    private val AUDIO_EXTENSIONS = listOf(".mp3", ".m4a", ".aac", ".ogg")

    fun from(entry: SyndEntry): AudioInfo? {
        val enclosure = entry.enclosures.orEmpty().firstOrNull { isAudio(it) } ?: return null
        val url = enclosure.url?.trim().orEmpty()
        if (!isHttps(url)) return null
        return AudioInfo(url, enclosure.type?.takeIf { it.isNotBlank() }, durationOf(entry))
    }

    private fun isAudio(enclosure: SyndEnclosure): Boolean {
        val type = enclosure.type
        if (!type.isNullOrBlank()) return type.startsWith(AUDIO_MIME_PREFIX, ignoreCase = true)
        val path = runCatching { URI(enclosure.url.orEmpty()).path }.getOrNull().orEmpty().lowercase()
        return AUDIO_EXTENSIONS.any { path.endsWith(it) }
    }

    private fun isHttps(url: String): Boolean =
        runCatching { URI(url).scheme.equals(HTTPS, ignoreCase = true) }.getOrDefault(false)

    private fun durationOf(entry: SyndEntry): Int? =
        entry.foreignMarkup.orEmpty()
            .firstOrNull { it.name == DURATION_NAME && it.namespacePrefix == ITUNES_PREFIX }
            ?.let { AudioDuration.parse(it.text) }
}
```
`AudioInfo.kt` as in Interfaces.

- [ ] **Step 4: Run, confirm GREEN.**
- [ ] **Step 5: Commit** — `feat(podcast): parse audio enclosures and iTunes duration` (+ Co-Authored-By trailer).

---

### Task 2: Room v18 audio columns, migration, DAO and `RssHelper` (TDD)

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/model/article/Article.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/domain/repository/ArticleDao.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/db/AndroidDb.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/rss/RssHelper.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/infrastructure/db/RoomMigration17To18Test.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/domain/repository/ArticleDaoAudioTest.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/infrastructure/rss/RssHelperAudioTest.kt`
- Generated: `app/schemas/**/18.json` (commit it)

**Interfaces:**
- Consumes: `AudioInfo`, `AudioEnclosure.from` (Task 1).
- Produces: `Article` gains the LAST three constructor parameters `var audioUrl: String? = null`, `var audioMime: String? = null`, `var audioDurationSec: Int? = null` (each `@ColumnInfo`); `object MIGRATION_17_18 : Migration(17, 18)`; `AndroidDatabase` `version = 18`; `RssHelper.article(...)` becomes `internal` and fills the three fields.

- [ ] **Step 1: Write tests (RED)**
  - `RoomMigration17To18Test` — same pattern as `RoomMigration16To17Test` (raw `SupportSQLiteOpenHelper` at version 17 with a hand-made `article` table `(id TEXT PRIMARY KEY, accountId INTEGER NOT NULL, isUnread INTEGER NOT NULL)` and one row): after `MIGRATION_17_18.migrate(db)` the row is intact and `audioUrl`, `audioMime`, `audioDurationSec` are all NULL (`cursor.isNull`), and updating `audioUrl='https://x/a.mp3', audioDurationSec=90` reads back.
  - `ArticleDaoAudioTest` (Robolectric in-memory Room, `.addMigrations(MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18)`, account/group/feed rows like `ArticleDaoReadingStatsTest`): an inserted article with the three audio values round-trips through `queryById`; and each of the four explicit-column queries that return `ArticleWithFeed`/`Article` (`queryArticleWithFeedByGroupId...`, the feed-latest query and the other two — find them with `rg -n "a.aiSummary, a.readAt" ArticleDao.kt`) returns the audio fields. Use the public DAO methods those queries belong to and assert `audioUrl` is non-null on the returned article.
  - `RssHelperAudioTest`: read `RssHelperDescriptionTest.kt` first and reuse its way of building an `RssHelper`; build a one-item podcast XML (with `xmlns:itunes`, `<enclosure url="https://cdn.example.com/ep1.mp3" type="audio/mpeg" length="100"/>`, `<itunes:duration>61</itunes:duration>`), obtain the `SyndEntry` via `SyndFeedInput` and call the now-`internal` `RssHelper.article(feed, accountId, entry)`: expect `audioUrl == "https://cdn.example.com/ep1.mp3"`, `audioMime == "audio/mpeg"`, `audioDurationSec == 61`; a plain text item gives all three null; an image-only enclosure still produces the thumbnail in `img` and null audio.

- [ ] **Step 2: Run, confirm RED.**

- [ ] **Step 3: Implement**
  - `Article.kt`: append the three `@ColumnInfo var ... = null` parameters after `readAt`.
  - `AndroidDb.kt`: `version = 18`; add `MIGRATION_17_18` to `allMigrations`; add
    ```kotlin
    @Suppress("ClassName")
    object MIGRATION_17_18 : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE article ADD COLUMN audioUrl TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE article ADD COLUMN audioMime TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE article ADD COLUMN audioDurationSec INTEGER DEFAULT NULL")
        }
    }
    ```
  - `ArticleDao.kt`: in the four explicit column lists change `a.aiSummary, a.readAt` to `a.aiSummary, a.readAt, a.audioUrl, a.audioMime, a.audioDurationSec` (replace all occurrences; assert the count is 4 in the editing script, as was done for `readAt`).
  - `RssHelper.article(...)`: make it `internal`, compute `val audio = AudioEnclosure.from(syndEntry)` and pass `audioUrl = audio?.url`, `audioMime = audio?.mime`, `audioDurationSec = audio?.durationSec` to the `Article` constructor.
  - Fix any in-memory builders in existing tests only if the build breaks (they normally do not list migrations).

- [ ] **Step 4: Run, confirm GREEN**; then `./gradlew assembleDevDebug -q` and confirm `app/schemas/**/18.json` exists with the three columns and there is no `QUERY_MISMATCH` warning (`./gradlew compileDevDebugKotlin 2>&1 | grep -i QUERY_MISMATCH` prints nothing).
- [ ] **Step 5: Commit** — `feat(db): store podcast audio enclosure on article (Room v18)` (+ trailer).

---

### Task 3: `PodcastPositionStore` and `SleepTimer` (pure, TDD)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/podcast/SleepTimer.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/podcast/PodcastPositionStore.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/domain/model/podcast/SleepTimerTest.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/infrastructure/podcast/PodcastPositionStoreTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  class SleepTimer(private val nowMillis: () -> Long = System::currentTimeMillis) {
      fun start(minutes: Int)               // minutes must be one of SleepTimer.OPTIONS_MINUTES, else IllegalArgumentException
      fun cancel()
      val isActive: Boolean
      fun remainingSec(): Int?              // null when not active; never negative
      fun isExpired(): Boolean              // true once remaining reaches 0 (and the timer was started)
      companion object { val OPTIONS_MINUTES = listOf(15, 30, 45) }
  }

  class PodcastPositionStore @Inject constructor(@ApplicationContext context: Context) {
      fun save(articleId: String, positionSec: Int, durationSec: Int?)
      fun load(articleId: String): Int          // 0 when unknown
      companion object {
          const val MAX_REMEMBERED_POSITIONS = 50
          const val COMPLETED_TAIL_SEC = 15
          const val PREFS_NAME = "podcast_positions"
      }
  }
  ```
  A saved position within `COMPLETED_TAIL_SEC` of `durationSec` (or ≥ duration) is stored as 0 so a finished episode restarts. Negative positions are stored as 0. The store keeps at most `MAX_REMEMBERED_POSITIONS` entries; saving the 51st drops the least recently saved one. Persisted as one string in `SharedPreferences` (`"id1:sec,id2:sec"`, most recent last; the article id never contains `:` or `,` — it is `accountId$uuid`; assert this with a test that an id containing neither is round-tripped).

- [ ] **Step 1: Write tests (RED)** — `SleepTimerTest` with a mutable fake clock: inactive by default (`remainingSec() == null`, `isExpired() == false`); `start(15)` → remaining 900; advance 100 s → 800; advance past the end → 0 and `isExpired()`; `cancel()` clears; `start(20)` throws; restarting resets the end time. `PodcastPositionStoreTest` (Robolectric): save/load round trip; unknown id → 0; finished-tail → 0; negative → 0; overwrite keeps one entry per id; 51 saves keep exactly 50 and the first id is dropped; a second `PodcastPositionStore` instance on the same context sees the data (persistence).
- [ ] **Step 2: Run, confirm RED.**
- [ ] **Step 3: Implement** both classes as specified (`SharedPreferences` `apply()` on save; parse defensively: a malformed stored string yields an empty map, never a crash).
- [ ] **Step 4: Run, confirm GREEN.**
- [ ] **Step 5: Commit** — `feat(podcast): add sleep timer and per-article position store` (+ trailer).

---

### Task 4: `PodcastPlaybackController`, engine port and TTS exclusion (TDD)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/domain/model/podcast/PodcastPlaybackState.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/domain/sv/PodcastEngine.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/domain/sv/TtsGate.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/domain/sv/PodcastPlaybackController.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/di/PodcastModule.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/audio/TtsManager.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/domain/sv/PodcastPlaybackControllerTest.kt`
- Modify: `app/src/test/java/com/mckimquyen/reader/infrastructure/audio/TtsManagerTest.kt`

**Interfaces:**
- Consumes: `SleepTimer`, `PodcastPositionStore` (Task 3), `AudioInfo` (Task 1).
- Produces:
  ```kotlin
  enum class PodcastStatus { IDLE, PREPARING, PLAYING, PAUSED, ERROR }

  data class PodcastPlaybackState(
      val articleId: String? = null,
      val title: String = "",
      val feedName: String = "",
      val status: PodcastStatus = PodcastStatus.IDLE,
      val positionSec: Int = 0,
      val durationSec: Int = 0,
      val speed: Float = 1.0f,
      val sleepRemainingSec: Int? = null,
  )

  /** Engine thật là ExoPlayer trong service; test dùng bản giả. Gọi trên main thread. */
  interface PodcastEngine {
      fun prepare(url: String, startPositionSec: Int)
      fun play()
      fun pause()
      fun stop()
      fun seekToSec(sec: Int)
      fun setSpeed(speed: Float)
      fun positionSec(): Int
      fun durationSec(): Int
  }

  interface TtsGate {
      val isSpeaking: Boolean
      fun stop()
      fun setOnPlayRequested(callback: (() -> Unit)?)
  }

  @Singleton
  class PodcastPlaybackController @Inject constructor(
      private val positions: PodcastPositionStore,
      private val tts: TtsGate,
      private val starter: PodcastServiceStarter,   // fun interface { fun start() }, production = PodcastPlaybackService.start(context)
  ) {
      val state: StateFlow<PodcastPlaybackState>
      fun open(articleId: String, title: String, feedName: String, audio: AudioInfo)  // select episode, restore position, do NOT autoplay
      fun attachEngine(engine: PodcastEngine)    // called by the service; runs any pending prepare/play
      fun detachEngine(engine: PodcastEngine)
      fun play(); fun pause(); fun toggle(); fun stop()
      fun skip(seconds: Int)                     // clamps to [0, duration]
      fun seekTo(sec: Int)
      fun setSpeed(speed: Float)                 // must be in SPEEDS else ignored
      fun startSleepTimer(minutes: Int); fun cancelSleepTimer()
      fun onEngineTick()                         // called every TICK_MS by the service: updates position/duration/sleep, saves position, pauses when the timer expires
      fun onEngineEnded(); fun onEngineError()
      fun retry()
      companion object {
          const val SKIP_INTERVAL_SEC = 15
          const val TICK_MS = 1_000L
          val SPEEDS = listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
          const val SAVE_EVERY_TICKS = 5
      }
  }
  ```
  `TtsManager` change: a new `var onPlayRequested: (() -> Unit)? = null` invoked at the very top of `play()` (before the not-initialised check), and a production `TtsGate` adapter in `PodcastModule` (`isSpeaking = ttsManager.ttsState.value == TtsState.PLAYING`, `stop = ttsManager.stop()`, `setOnPlayRequested = { ttsManager.onPlayRequested = it }`). The controller registers `tts.setOnPlayRequested { pause() }` in `init`. `PodcastModule` also provides `PodcastServiceStarter`; the real service does not exist until Task 5, so in this task bind `PodcastServiceStarter { }` (empty body, tests use their own fake) and replace the body in Task 5 Step 3.

- [ ] **Step 1: Write tests (RED)** — `PodcastPlaybackControllerTest` (JVM + Robolectric context for the store; fakes: `FakeEngine` recording calls with settable position/duration, `FakeTtsGate` with `isSpeaking` and a captured callback, `FakeStarter` counting starts; a mutable fake clock for the timer is injected through a second constructor parameter `sleepTimer: SleepTimer = SleepTimer()` — add it to the constructor):
  - `open_setsStateWithRestoredPositionAndStaysPaused`: after `positions.save("a1", 120, 600)`, `open("a1", "T", "F", AudioInfo(url, "audio/mpeg", 600))` → state has `articleId = "a1"`, `positionSec = 120`, `durationSec = 600`, `status == PAUSED`, no engine call, starter not called.
  - `play_withoutEngine_startsServiceAndMarksPreparing`: `play()` → `status == PREPARING`, `starter.starts == 1`; then `attachEngine(fake)` → fake received `prepare(url, 120)` then `play()` and the state becomes `PLAYING` after `onEngineTick()` reports a position.
  - `play_withEngine_callsPrepareOnlyOncePerEpisode`: pause then play again → `play()` only (no second `prepare`).
  - `play_whileTtsSpeaking_stopsTtsFirst`.
  - `ttsPlayRequested_pausesPodcast`: capture the callback registered by the controller, invoke it while `PLAYING` → engine `pause()` and `status == PAUSED`; while already paused → no engine call.
  - `skip_clampsToBounds`: position 10, `skip(-15)` → seeks to 0; position 590 of 600, `skip(15)` → 600.
  - `seekTo_clampsAndUpdatesState`.
  - `setSpeed_acceptsOnlyListedSpeeds`: 1.5 accepted (engine `setSpeed(1.5f)`, state `speed == 1.5f`); 3.0 and 0.5 ignored.
  - `onEngineTick_updatesPositionAndSavesEveryFiveTicks`: five ticks → `positions.load("a1")` equals the fake position; fewer than five → not saved yet (still the old value).
  - `sleepTimer_expiry_pausesAndClearsTimer`: `startSleepTimer(15)`, advance the injected clock past 15 minutes, `onEngineTick()` → engine `pause()`, `status == PAUSED`, `sleepRemainingSec == null`; before expiry `sleepRemainingSec` counts down; `cancelSleepTimer()` clears; invalid minutes (20) are ignored.
  - `onEngineEnded_resetsPositionAndPauses`: `positions.load` returns 0 afterwards and `status == PAUSED`.
  - `onEngineError_setsErrorAndRetryRestartsFromSavedPosition`.
  - `open_otherEpisodeWhilePlaying_stopsCurrentAndSwitches`: engine `stop()` called, new state for the new article, status PAUSED.
  - `stop_detachesPlaybackAndReturnsToIdle`: state `IDLE`, engine `stop()`.
  - `detachEngine_whilePlaying_setsPaused` (service killed): status `PAUSED`, position kept.
  - `TtsManagerTest` additions: `play_invokesOnPlayRequestedFirst` (set `manager.onPlayRequested`, call `play`, assert called once, also when the engine is not initialised); hook is not invoked by `pause()`/`stop()`/`speakNotice`.
- [ ] **Step 2: Run, confirm RED.**
- [ ] **Step 3: Implement** the controller as a plain class holding a `MutableStateFlow<PodcastPlaybackState>`; every public method runs on the caller's thread (main); `play()` when the engine is `null` stores `pendingPlay = true`, sets `PREPARING` and calls `starter.start()` once; `attachEngine` executes `prepare(url, state.positionSec)` followed by `play()` when `pendingPlay`; keep `preparedArticleId` to avoid a second `prepare` for the same episode; `onEngineTick` reads `engine.positionSec()` / `durationSec()`, updates the state to `PLAYING`, increments a tick counter and saves every `SAVE_EVERY_TICKS`, refreshes `sleepRemainingSec` from the timer and pauses when `timer.isExpired()`. Add the `TtsManager` hook and the `PodcastModule` bindings described above.
- [ ] **Step 4: Run, confirm GREEN** (controller + TtsManager tests), then `./gradlew compileDevDebugKotlin -q`.
- [ ] **Step 5: Commit** — `feat(podcast): add playback controller with TTS mutual exclusion` (+ trailer).

---

### Task 5: `PodcastPlaybackService` with ExoPlayer, MediaSession and notification

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/podcast/ExoPodcastEngine.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/podcast/PodcastPlaybackService.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/podcast/PodcastNotificationActions.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/di/PodcastModule.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/infrastructure/podcast/PodcastNotificationActionsTest.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/infrastructure/podcast/PodcastServiceManifestTest.kt`

**Interfaces:**
- Consumes: `PodcastEngine`, `PodcastPlaybackController`, `PodcastPlaybackState`, `PodcastStatus` (Task 4).
- Produces:
  ```kotlin
  class ExoPodcastEngine(context: Context) : PodcastEngine {  // owns an ExoPlayer built on the main looper
      var onEnded: (() -> Unit)? ; var onError: (() -> Unit)?
      fun release()
  }

  @AndroidEntryPoint
  class PodcastPlaybackService : Service() {
      companion object {
          const val ACTION_PLAY = "com.mckimquyen.reader.podcast.ACTION_PLAY"
          const val ACTION_PAUSE = "com.mckimquyen.reader.podcast.ACTION_PAUSE"
          const val ACTION_REWIND = "com.mckimquyen.reader.podcast.ACTION_REWIND"
          const val ACTION_FORWARD = "com.mckimquyen.reader.podcast.ACTION_FORWARD"
          const val ACTION_STOP = "com.mckimquyen.reader.podcast.ACTION_STOP"
          const val NOTIFICATION_ID = 9183
          const val CHANNEL_ID = "podcast_playback_channel"
          fun start(context: Context)
      }
  }

  object PodcastNotificationActions { fun actionNames(isPlaying: Boolean): List<String> }   // ["rewind", "toggle", "forward", "stop"]
  ```

- [ ] **Step 1: Write tests (RED)**
  - `PodcastNotificationActionsTest`: `actionNames(true) == actionNames(false) == ["rewind", "toggle", "forward", "stop"]`; the four service action strings are pairwise distinct and none equals an action of `TtsForegroundService`.
  - `PodcastServiceManifestTest` (plain JVM, same XML-parsing approach as `ReadNewsManifestTest`): the manifest declares `<service android:name=".infrastructure.podcast.PodcastPlaybackService">` with `android:exported="false"` and `android:foregroundServiceType="mediaPlayback"`; the permission set is unchanged (exactly the seven existing permissions).
- [ ] **Step 2: Run, confirm RED.**
- [ ] **Step 3: Implement**
  - `ExoPodcastEngine`: builds `ExoPlayer.Builder(context).setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true).setHandleAudioBecomingNoisy(true).build()` (audio focus handled); `prepare(url, startSec)` does `setMediaItem(MediaItem.fromUri(url), startSec * 1000L)` + `prepare()`; `setSpeed` uses `setPlaybackParameters(PlaybackParameters(speed))`; a `Player.Listener` calls `onEnded` for `STATE_ENDED` and `onError` for `onPlayerError`; `positionSec()` = `currentPosition / 1000`, `durationSec()` = `duration` if not `C.TIME_UNSET` else 0. The HTTP stack is `DefaultHttpDataSource.Factory()` (from `media3-datasource`, already resolved) with `setConnectTimeoutMs`/`setReadTimeoutMs` named constants and the default cross-protocol redirect behaviour (no HTTPS→HTTP).
  - `PodcastPlaybackService`: `onCreate` creates the notification channel, an `ExoPodcastEngine`, a `MediaSessionCompat` (callbacks map to controller `play`, `pause`, `skip(±15)`, `seekTo`, `stop`), calls `controller.attachEngine(engine)` and launches a ticker `serviceScope.launch { while (isActive) { controller.onEngineTick(); refreshNotification(); delay(PodcastPlaybackController.TICK_MS) } }` on `Dispatchers.Main`; `onStartCommand` handles the five actions and calls `startForeground(NOTIFICATION_ID, notification)` as soon as it is started; `onDestroy` cancels the scope, `controller.detachEngine(engine)`, `engine.release()`, releases the session. The notification (`NotificationCompat` + `MediaStyle`) shows title, feed name, rewind/toggle/forward/stop with `PendingIntent.getService(... FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT ...)` and distinct request codes, `setOngoing(isPlaying)`, `VISIBILITY_PUBLIC`. The service stops itself (`stopForeground(STOP_FOREGROUND_REMOVE)`, `stopSelf()`) when the state returns to `IDLE`. It is injected with `@Inject lateinit var controller: PodcastPlaybackController` like the other `@AndroidEntryPoint` services.
  - `start(context)`: `ContextCompat.startForegroundService(context, Intent(context, PodcastPlaybackService::class.java).setAction(ACTION_PLAY))`.
  - `PodcastModule`: replace the no-op starter with `PodcastServiceStarter { PodcastPlaybackService.start(context) }` (inject `@ApplicationContext`).
  - Manifest: add the service block after `TtsForegroundService` with `exported="false"` and `foregroundServiceType="mediaPlayback"`; add strings `podcast_channel_name` ("Podcast playback") to `values/strings.xml` now.
- [ ] **Step 4: Run, confirm GREEN**, then `./gradlew assembleDevDebug processDevDebugManifest -q` and confirm the merged manifest keeps the service (`rg -n PodcastPlaybackService app/build/intermediates/merged_manifests/devDebug/processDevDebugManifest/AndroidManifest.xml`).
- [ ] **Step 5: Commit** — `feat(podcast): add ExoPlayer playback service with media session` (+ trailer).

---

### Task 6: `PodcastPlayerViewModel`, `PodcastPlayerBar` and Reader wiring (TDD)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/podcast/PodcastPlayerViewModel.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/podcast/PodcastPlayerBar.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingPage.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/ui/page/home/read/podcast/PodcastPlayerViewModelTest.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/ui/page/home/read/podcast/PodcastPlayerBarTest.kt`

**Interfaces:**
- Consumes: `PodcastPlaybackController`, `PodcastPlaybackState`, `PodcastStatus`, `AudioInfo` (Task 4).
- Produces:
  ```kotlin
  @HiltViewModel
  class PodcastPlayerViewModel @Inject constructor(private val controller: PodcastPlaybackController) : ViewModel() {
      val state: StateFlow<PodcastPlaybackState>
      fun bind(articleId: String, title: String, feedName: String, audio: AudioInfo)   // controller.open unless that episode is already the active one
      fun onToggle(); fun onSkip(seconds: Int); fun onSeek(sec: Int)
      fun onSpeed(speed: Float); fun onSleep(minutes: Int?)   // null cancels
      fun onRetry()
  }

  const val PODCAST_BAR_TAG = "podcast_player_bar"
  const val PODCAST_TOGGLE_TAG = "podcast_toggle"
  const val PODCAST_BACK_TAG = "podcast_skip_back"
  const val PODCAST_FORWARD_TAG = "podcast_skip_forward"
  const val PODCAST_SEEK_TAG = "podcast_seek"
  const val PODCAST_SPEED_TAG = "podcast_speed"
  const val PODCAST_SLEEP_TAG = "podcast_sleep"
  const val PODCAST_ERROR_TAG = "podcast_error"
  const val PODCAST_RETRY_TAG = "podcast_retry"

  @Composable
  fun PodcastPlayerBar(
      state: PodcastPlaybackState,
      onToggle: () -> Unit, onSkip: (Int) -> Unit, onSeek: (Int) -> Unit,
      onSpeed: (Float) -> Unit, onSleep: (Int?) -> Unit, onRetry: () -> Unit,
      modifier: Modifier = Modifier,
  )

  internal fun formatPlaybackTime(totalSec: Int): String   // "M:SS" or "H:MM:SS"
  ```

- [ ] **Step 1: Write tests (RED)**
  - `PodcastPlayerViewModelTest` (mock controller): `bind` calls `controller.open(...)` for a new episode and does nothing when `controller.state.value.articleId` already equals the id; each `onX` forwards to the matching controller method (`onSleep(null)` → `cancelSleepTimer()`, `onSleep(30)` → `startSleepTimer(30)`); `state` is the controller's flow.
  - `formatPlaybackTime`: `0→"0:00"`, `65→"1:05"`, `3600→"1:00:00"`, `3723→"1:02:03"`, `-3→"0:00"`.
  - `PodcastPlayerBarTest` (Robolectric `createComposeRule`, `@Config(sdk = [34], application = Application::class)`): PAUSED state shows the bar, play action description "Play" and the elapsed/total text "0:00" / "10:00"; PLAYING shows the "Pause" description; clicking toggle/back/forward calls `onToggle()`, `onSkip(-15)`, `onSkip(15)`; the speed chip shows "1.0×" and clicking opens a menu with the five speeds, choosing 1.5 calls `onSpeed(1.5f)`; the sleep button shows "Off" (or the remaining time "14:59" when `sleepRemainingSec = 899`), its menu offers Off/15/30/45 and calls `onSleep(null)` / `onSleep(30)`; ERROR state shows the error tag and a retry button that calls `onRetry`; the slider calls `onSeek` with a value inside `[0, durationSec]`; every interactive node has a non-empty `contentDescription`.
- [ ] **Step 2: Run, confirm RED.**
- [ ] **Step 3: Implement**
  - `PodcastPlayerBar`: a `Surface` (`MaterialTheme.colorScheme.surfaceVariant`) with a `Column`: seek `Slider` (value = position/duration, `onValueChangeFinished` commits, uses a local dragging state so the thumb does not jump while ticking), a row with elapsed and total time, a control row (rewind 15, large play/pause, forward 15), and a row with the speed chip (`DropdownMenu` of `PodcastPlaybackController.SPEEDS`) and the sleep button (`DropdownMenu` with Off and `SleepTimer.OPTIONS_MINUTES`). Material 3 `1.0.1` components only (`Slider`, `IconButton`, `AssistChip` with `@OptIn(ExperimentalMaterial3Api::class)`); icons from `Icons.Rounded`/`Outlined` (replay/forward icons: `Icons.Rounded.Replay10`/`Forward10` do not match 15 s, so draw `Icons.Rounded.FastRewind`/`FastForward` with the text "15" overlaid). Strings come from `R.string.podcast_*` (added in Task 7; add the English lines to `values/strings.xml` in this task so the build compiles).
  - `ReadingPage.kt`: inside the content `Column`, between the content `Box(weight(1f))` and the `BottomBar`/banner rows, add
    ```kotlin
    val audioUrl = readingUiState.articleWithFeed?.article?.audioUrl
    if (readingUiState.articleWithFeed != null && audioUrl != null) {
        val podcastViewModel: PodcastPlayerViewModel = hiltViewModel()
        val podcastState by podcastViewModel.state.collectAsState()
        val article = readingUiState.articleWithFeed.article
        LaunchedEffect(article.id) {
            podcastViewModel.bind(article.id, article.title, readingUiState.articleWithFeed.feed.name,
                AudioInfo(audioUrl, article.audioMime, article.audioDurationSec))
        }
        PodcastPlayerBar(state = podcastState, ...callbacks to podcastViewModel...)
    }
    ```
    The bar shows only when the shown state's `articleId` equals the open article (otherwise show the idle/paused bar built from `bind`'s state).
- [ ] **Step 4: Run, confirm GREEN** (`com.mckimquyen.reader.ui.page.home.read.*`), then `./gradlew compileDevDebugKotlin -q`.
- [ ] **Step 5: Commit** — two commits: `feat(podcast): add player view model and bar` and `feat(reader): show the podcast player bar for articles with audio` (+ trailer).

---

### Task 7: Localization across all 38 locales

**Files:**
- Modify: `app/src/main/res/values/strings.xml` and every `app/src/main/res/values-*/strings.xml` that has a `strings.xml` (37 files).

**Keys (English source; confirm each appears exactly once in `values/strings.xml` after Tasks 5–6):**
- `podcast_channel_name` = "Podcast playback"
- `podcast_play` = "Play"
- `podcast_pause` = "Pause"
- `podcast_skip_back` = "Back 15 seconds"
- `podcast_skip_forward` = "Forward 15 seconds"
- `podcast_seek` = "Playback position"
- `podcast_speed` = "Playback speed"
- `podcast_sleep_timer` = "Sleep timer"
- `podcast_sleep_off` = "Off"
- `podcast_error` = "Could not play this episode."
- plurals `podcast_sleep_minutes`: `one` = "%1$d minute", `other` = "%1$d minutes"
(`retry` already exists and is reused for the retry button.)

- [ ] **Step 1:** Confirm the keys exist exactly once in `values/strings.xml`.
- [ ] **Step 2:** Write a throw-away Python script (`# -*- coding: utf-8 -*-` first, not committed; same approach as the earlier i18n scripts) with real translations for the 37 locales (`ar az bg ca cs-rCZ da de-rDE es-rES eu-rES fa fr-rFR hi-rIN hu in-rID it-rIT iw ja kn lzh ml my nb-rNO nl nn pl-rPL pt-rBR pt ro ru-rRU sl sr sv tr uk vi zh-rCN zh-rTW`), giving every plural category each language needs (`ar`: zero/one/two/few/many/other; `cs`, `pl`, `ru`, `uk`, `sl`, `sr`, `ro`, `iw`: their extra forms), asserting the locale set equals the directories on disk and no key already exists, escaping `'` as `\'`, inserting before `</resources>`. Vietnamese: "Phát podcast", "Phát", "Tạm dừng", "Lùi 15 giây", "Tua tới 15 giây", "Vị trí phát", "Tốc độ phát", "Hẹn giờ tắt", "Tắt", "Không thể phát tập này.".
- [ ] **Step 3:** Validate every file parses and `rg -L "podcast_sleep_minutes" app/src/main/res/values*/strings.xml` prints nothing.
- [ ] **Step 4:** `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.pref.StringResourceParityTest" processDevDebugResources -q` → PASS.
- [ ] **Step 5: Commit** — `feat(i18n): localize podcast player across all 38 maintained locales` (+ trailer).

---

### Task 8: Instrumentation, full suite, device smoke and docs

**Files:**
- Create: `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/read/podcast/PodcastInstrumentationTest.kt`
- Create: `doc/task/done/IDEA-05_podcast-audio-player_DONE.md`
- Modify: `doc/feature.md`
- Delete: `doc/task/todo/IDEA-05_podcast-audio-player.md`

- [ ] **Step 1: Instrumentation tests** (`AndroidJUnit4`, isolated `EInkUiTestRunner`, no Hilt): (a) real in-memory `AndroidDatabase` v18 round-trips an article with audio fields; (b) the bar inside a `ComponentActivity` with `setDecorFitsSystemWindows(false)` clears the navigation bar inset (same polling pattern as `OnThisDayEdgeToEdgeTest`, tags `PODCAST_BAR_TAG` and `PODCAST_TOGGLE_TAG`); (c) a real `ExoPodcastEngine` prepares a tiny local WAV file written to `cacheDir` (`file://` URI, so no network) and reaches `durationSec() > 0` within 10 s, `play()` then `positionSec()` advances, `pause()` stops advancing, and `release()` leaves no crash.
- [ ] **Step 2: Run on the Tecno only** — requires the Tecno to be visible: `adb devices -l` must list `115333744A005844`; if it does not, stop and report the connection problem (do not use the Pixel).
  ```bash
  ./gradlew -PtestRunner=com.mckimquyen.reader.EInkUiTestRunner assembleDevDebug assembleDevDebugAndroidTest
  adb -s 115333744A005844 install -r "app/build/outputs/apk/dev/debug/<apk>.apk"
  adb -s 115333744A005844 install -r app/build/outputs/apk/androidTest/dev/debug/app-dev-debug-androidTest.apk
  adb -s 115333744A005844 shell am instrument -w -e class com.mckimquyen.reader.ui.page.home.read.podcast.PodcastInstrumentationTest com.mckimquyen.reader.test/com.mckimquyen.reader.EInkUiTestRunner
  ```
  Expected `OK (3 tests)`. Do not run `connectedDevDebugAndroidTest`.
- [ ] **Step 3: Full suite and builds** — `./gradlew testDevDebugUnitTest assembleDevDebug compileDevDebugAndroidTestKotlin` and read `BUILD SUCCESSFUL`/`FAILED`; sum the XML results and print the age of the oldest result file so stale XML is not mistaken for a fresh run (1094 before this feature; expect more, 0 failures/errors/skipped). Do not claim lint passed.
- [ ] **Step 4: Live smoke on the Tecno** — back up the app database first (`run-as com.mckimquyen.reader cat databases/Reader*` to `/tmp/podcast_db_backup`), rebuild without `-PtestRunner`, install, then check the foreground window before every action and screenshot after each:
  1. Subscribe to `https://feeds.npr.org/510289/podcast.xml` through the Subscribe dialog (type slowly and verify the field with `uiautomator dump`; the dialog accepts only public HTTPS feeds).
  2. Open the feed, open an episode: the player bar appears above the bottom bar; an article without audio (BBC News feed) shows no bar.
  3. Tap play: the notification "…" with rewind/toggle/forward/stop appears and `dumpsys media_session` shows the app's session `PLAYING`; logcat shows ExoPlayer state `READY` (read the log into a file and filter by `ExoPlayerImpl`/`PodcastPlayback`).
  4. Skip forward 15 s (position increases by ≈15), change the speed to 1.5× (`dumpsys media_session` or the chip label), start a 15-minute sleep timer (chip shows the remaining time).
  5. Lock the screen (`KEYCODE_SLEEP`), wait 10 s, confirm the session is still `PLAYING`, wake with `KEYCODE_WAKEUP`.
  6. Leave the Reader, reopen the episode: the bar reattaches to the running session; stop from the notification and reopen: the saved position is restored.
  7. Start TTS in a text article while a podcast plays: the podcast pauses; start the podcast again: TTS stops.
  8. If a full-screen ad covers the UI, stop, tell the user and wait for "done"; banners are skipped. Restore the database from the backup afterwards and confirm the schema version and unread count.
  Record that sound output was not heard by a person.
- [ ] **Step 5: Docs** — write `doc/task/done/IDEA-05_podcast-audio-player_DONE.md` (status, commit hashes from `git log`, implementation, measured verification numbers, the Known limits above); in `doc/feature.md` add IDEA-05 under "Recently implemented" with the real test counts; `git rm doc/task/todo/IDEA-05_podcast-audio-player.md`.
- [ ] **Step 6: Commit** — `test(podcast): add podcast instrumentation tests` and `docs: finalize IDEA-05 Podcast Audio Player completion report` (+ trailer). Do not push unless the user asks.
