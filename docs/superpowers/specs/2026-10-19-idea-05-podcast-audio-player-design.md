# [IDEA-05] Podcast & Audio RSS Player Design Specification

- **Status:** Approved
- **Date:** 2026-10-19
- **Priority:** P2 (Medium) — 5 Story Points
- **Epic:** 04. IDEAS — Ý Tưởng Trải Nghiệm & Tăng Trưởng

---

## 1. Problem & Goal

Many RSS feeds are podcasts: each item carries an audio `<enclosure>`. `RssHelper` ignores it (it only reads image enclosures), so the user can read the show notes but cannot listen. IDEA-05 stores the audio reference of each item and adds a player bar in the Reader with background playback, a 15-second skip, speed 1.0x–2.0x and a sleep timer.

## 2. Accepted Decisions

| Decision | Rationale |
|---|---|
| Store the audio reference as three nullable columns on `article` (`audioUrl`, `audioMime`, `audioDurationSec`), Room v18 via `MIGRATION_17_18`. | Simplest: one audio per article, `ALTER TABLE` like IDEA-01's `readAt`; old articles stay NULL. |
| Only `https` enclosure URLs are accepted. | The app already accepts only public HTTPS sources; plain HTTP audio is not played. |
| Play with an `ExoPlayer` (media3 1.4.1, already a dependency) hosted by a new `PodcastPlaybackService` with a `MediaSessionCompat`. | Separate from the TTS service so the TTS fix from IDEA-08 and `CommuteMediaSessionService` are not touched. |
| The player bar lives inside the Reader, between the content and the `BottomBar`; it does not touch the banner ad. | Keeps edge-to-edge handling in the existing column; no global player. |
| The listening position is remembered per article. | Podcasts are long; losing the place on every exit is a bad experience. |
| Podcast and TTS never play at once: starting one pauses the other. | Avoids two audio sources overlapping; audio focus alone is not relied upon. |
| Verification feed is NPR (`https://feeds.npr.org/510289/podcast.xml`), not BBC. | The BBC feed uses `http://` enclosures on a host that was unreachable from the development machine; NPR enclosures are HTTPS. |

## 3. Data

### 3.1 Parsing

- `AudioEnclosure.from(syndEntry): AudioInfo?` is a pure function in `domain/model/article/AudioEnclosure.kt`. It picks the first enclosure whose `type` starts with `audio/`, or whose URL path ends with `.mp3`, `.m4a`, `.aac` or `.ogg` when `type` is missing, and returns it only if the scheme is `https`.
- `AudioInfo(url: String, mime: String?, durationSec: Int?)`. The duration comes from `<itunes:duration>` and accepts `SS`, `MM:SS` and `HH:MM:SS`; invalid or absent values give null. `AudioDuration.parse(text): Int?` is separate and pure.
- `RssHelper.article()` fills the three fields. The existing thumbnail logic is unchanged.

### 3.2 Schema

- `Article` gains `audioUrl: String? = null`, `audioMime: String? = null`, `audioDurationSec: Int? = null` as the last constructor parameters.
- `MIGRATION_17_18` runs three `ALTER TABLE article ADD COLUMN ... DEFAULT NULL` statements. Schema `18.json` is exported.
- The four `ArticleDao` queries that list columns explicitly get the three new columns appended, as was done for `readAt`.
- Known limit (inherited): `ArticleDao.insert` uses `REPLACE`, so a Fever re-fetch can reset the new columns to NULL.

### 3.3 Position store

- `PodcastPositionStore` keeps the last position in seconds per `articleId` in a small `SharedPreferences` file, capped at `MAX_REMEMBERED_POSITIONS` entries (oldest dropped). A position within `COMPLETED_TAIL_SEC` of the end, or at the end, is stored as 0 so a finished episode restarts from the beginning.

## 4. Playback Service

- `PodcastPlaybackService` is an `@AndroidEntryPoint` foreground service (`foregroundServiceType="mediaPlayback"`, exported false) that owns one `ExoPlayer` with audio attributes `USAGE_MEDIA` / `CONTENT_TYPE_SPEECH` and `handleAudioFocus = true`.
- `MediaSessionCompat` actions: play, pause, stop, fast-forward and rewind (15 s each, `SKIP_INTERVAL_SEC = 15`), seek-to. The media notification shows the article title, the feed name, play/pause and the two skips.
- Speed: `setPlaybackParameters(PlaybackParameters(speed))` with speeds `1.0, 1.25, 1.5, 1.75, 2.0` (named list).
- Sleep timer: `SleepTimer` (pure, clock-injected) with the options 15, 30 and 45 minutes; when it fires the service pauses and clears the timer. Starting, changing and cancelling a timer are covered by tests.
- Single owner of state: `PodcastPlaybackController` (`@Singleton`) exposes `StateFlow<PodcastPlaybackState>` (`Idle`, `Preparing`, `Playing`, `Paused`, `Error`) with article id, position, duration, speed and the timer's remaining time. UI and the service talk only through it. Neither holds an `Activity`.
- Network or format errors become `Error` with a retry action; the service never crashes on a bad URL.
- TTS interaction: `PodcastPlaybackController.play()` calls `TtsManager.stop()` when TTS is speaking; `TtsManager.play()` pauses the podcast through a small `PodcastPauser` interface bound in Hilt (so `TtsManager` does not depend on the player). `TtsManager` and `TtsForegroundService` keep their current behaviour otherwise.

## 5. Reader UI

- `PodcastPlayerBar` appears between the article content and `BottomBar` only when the open article has `audioUrl`. It contains play/pause, skip back/forward 15 s, a seek slider, elapsed/total time, a speed chip and a sleep-timer button (menu with Off, 15, 30, 45 minutes).
- `PodcastPlayerViewModel` (Hilt) maps the controller's state to the UI state and forwards intents. Leaving the Reader does not stop playback; reopening the same article reattaches to the running session.
- Error state shows a message and a retry button. Strings for the bar are localized in all 38 maintained locales; Vietnamese keeps full diacritics. Accessibility: every control has a `contentDescription`.

## 6. Testing and Verification

### Unit and instrumentation
- `AudioDuration.parse`: `SS`, `MM:SS`, `HH:MM:SS`, whitespace, empty, garbage, negative.
- `AudioEnclosure.from`: MIME `audio/mpeg`, missing MIME with `.mp3`, image enclosure ignored, `http://` rejected, several enclosures (first audio wins), no enclosure.
- `RssHelper.article()` stores the three fields for an audio item and leaves them null otherwise.
- Room: `MIGRATION_17_18` keeps data and adds NULL columns; DAO round-trips the fields; the four explicit-column queries return them.
- `PodcastPositionStore`: save, load, cap, finished-episode reset.
- `SleepTimer` with a fake clock; `PodcastPlaybackController` state transitions with a fake player port; podcast/TTS mutual pause.
- Compose widget tests for the bar (visible only with audio, controls call the right intents, error retry) and `PodcastPlayerViewModel`.
- Instrumentation on the Tecno (`adb -s 115333744A005844` only, isolated `EInkUiTestRunner`): Room v18 schema, bar clears system insets, controller drives a real `ExoPlayer` against a local `MockWebServer`-style HTTP server only if HTTPS is not required by the player layer (the HTTPS-only rule is enforced at parse time, not in the player).

### Real device (Tecno) — blocked until it reconnects
- Subscribe to `https://feeds.npr.org/510289/podcast.xml`, open an episode, see the bar, play, skip 15 s, change speed, lock the screen and confirm the media session stays `PLAYING` (`dumpsys media_session`), and use the notification controls. Check the foreground window before every action, take a screenshot after every step and stop if a full-screen ad covers the UI until the user says "done". Restore the device database afterwards.
- Sound quality and Bluetooth/headset output cannot be verified; evidence is player state, `dumpsys` and logcat.

### Verification limits
- The development machine could not reach the BBC audio host and, at the time of writing, the Tecno was not visible to adb; the Pixel was visible but is not used (the user locked the session to the Tecno).
- Full unit suite, `assembleDevDebug` and `compileDevDebugAndroidTestKotlin` must pass. Lint is affected by a known unrelated SDK 37 metadata failure; do not claim lint passed unless that changes.

## 7. Not In Scope

- Downloading episodes for offline listening, chapters, multi-episode queues, a global mini player, Android Auto, casting.
- Plain HTTP audio and DRM streams.
- Any ad, VIP or IAP behaviour (deferred by the owner).
