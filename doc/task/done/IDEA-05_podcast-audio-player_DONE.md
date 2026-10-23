# [IDEA-05] Podcast & Audio RSS Player — Completion Report

- **Status:** Implemented and verified; pushed to `origin/dev`. Sound through the phone speaker was heard by the owner on 2026-10-23 (Pixel 7 Pro). **Not verified:** Bluetooth/headset routing and TalkBack (see Limits).
- **Completed:** 2026-10-21
- **Audit:** 8/10. Parsing, Room v18, controller, service, player bar, TTS exclusion, sleep timer and 38-locale strings are covered by unit, Compose and instrumentation tests and by a real run on the Tecno, the Pixel 7 Pro and an emulator. A code review found ten issues; the behavioural ones were reproduced with failing tests and fixed. Audible phone-speaker sound was confirmed by the owner on 2026-10-23. Not covered: Bluetooth/headset, TalkBack, a service killed by the OS mid-playback, and a feed whose enclosure redirects HTTPS→HTTP.
- **Commits:**
  - `09d6a6c3` — design spec; `008ed162` — implementation plan
  - `7d0d728e` — `AudioDuration` / `AudioEnclosure` parsers (iTunes duration through Rome foreign markup, https only)
  - `8e84ca3d` — Room v18 (`audioUrl`, `audioMime`, `audioDurationSec`), `MIGRATION_17_18`, four explicit-column `ArticleDao` queries, `RssHelper` integration
  - `a6c40ea2` — `SleepTimer`, `PodcastPositionStore`
  - `9f3dff4e` — `PodcastPlaybackController`, engine/TTS ports, `TtsManager.onPlayRequested`
  - `d1017ef8` — `PodcastPlaybackService` (ExoPlayer + `MediaSessionCompat` + notification)
  - `6ee239ea`, `25705007` — view model, `PodcastPlayerBar`, Reader wiring
  - `7bac1bec` — localization (38 locales)
  - `7139c4c6` — instrumentation tests
  - `5c57e84b`, `6458d55a`, `184b3f4d`, `f327c216` — UI iterations after on-device review (see "UI changes found on the device")
  - `380cc06d` — fixes from the code review (see "Code review")

## Implementation

- **Data.** `RssHelper.article()` reads the first `audio/*` enclosure (or one with an `.mp3/.m4a/.aac/.ogg` path when the type is missing) and keeps it only when the URL is `https`. `<itunes:duration>` (`SS`, `MM:SS`, `HH:MM:SS`) is read from Rome's foreign markup. Three nullable columns on `article`; old rows stay NULL.
- **Controller.** `PodcastPlaybackController` (`@Singleton`) owns a `StateFlow<PodcastPlaybackState>` (`IDLE`, `PREPARING`, `PLAYING`, `PAUSED`, `ERROR`) and talks to an engine port, so it is unit-tested with a fake. Skip is 15 s (`SKIP_INTERVAL_SEC`), speeds are `1.0, 1.25, 1.5, 1.75, 2.0`, the sleep timer offers 15/30/45 minutes, and the position is saved every `SAVE_EVERY_TICKS` ticks and on pause/detach.
- **Service.** `PodcastPlaybackService` (`foregroundServiceType="mediaPlayback"`, not exported) owns one `ExoPlayer` (audio focus on, becoming-noisy handled, `DefaultDataSource` over `DefaultHttpDataSource` so a local `file://` URI also works in tests), a `MediaSessionCompat` and a media notification with rewind, play/pause, forward and stop. It stops itself when the state returns to `IDLE`.
- **TTS exclusion.** `TtsManager.onPlayRequested` runs at the top of `play()`; the controller registers a callback that pauses the podcast, and calling `play()` on the podcast stops a speaking TTS. `TtsManager` does not depend on the player (a small `TtsGate` port is bound in Hilt).
- **UI.** `PodcastPlayerBar` sits in the Reader between the content and the bottom bar, only for articles with audio. Collapsed it is one 56 dp row (play/pause, elapsed, seek, total, expand) with the episode title above it; expanded it adds speed, rewind 15, forward 15 and the sleep timer. The play button never moves between states. 13 strings and one plural × 38 locales.
- **Position store.** `PodcastPositionStore` keeps the last position per article (`SharedPreferences`, capped at `MAX_REMEMBERED_POSITIONS = 50`); a position within `COMPLETED_TAIL_SEC = 15` of the end is stored as 0.

## UI changes found on the device

The first bar (a stack of three rows, about 150 dp tall) looked wrong on the Tecno: the controls took a fifth of the screen, the speed and sleep controls used two different styles, and the sleep chip wrapped ("14:5" / "8"). Three iterations followed, each checked with a screenshot of the real device:

1. Two compact rows, a visible seek track, a replay-style skip icon with "15" inside.
2. A collapsible mini bar (one 56 dp row; expand for speed, skips and sleep timer), chosen by the owner over a two-row and an in-article variant.
3. Play always in the top row (it jumped between states before), the two skips grouped in the centre, an animated size change, the episode title above the collapsed row (tapping it also toggles), and a tonal circle around the expand button (the first circle was clipped at the right edge and was fixed after a screenshot).

Tests added for the layout: the play button keeps its left edge and width when expanding, the skips are adjacent and centred, and the title toggles expansion.

## Verification

- `./gradlew testDevDebugUnitTest assembleDevDebug compileDevDebugAndroidTestKotlin`: **BUILD SUCCESSFUL**, **1217 unit tests**, 0 failures/errors/skipped (1094 before this feature), summed from the fresh result XML (oldest file under a minute old). One earlier run of the same suite failed `BrainRpgRepositoryTest.firstProgressRead_importsLegacyPrefsBeforeExposingRoomState` (expected 640, was 0); that class passed when run alone and the whole suite passed on the immediate rerun, so it looks flaky, but the cause was not investigated. `StringResourceParityTest` 6/6.
- New tests are spread over the parsers, migration/DAO/`RssHelper`, sleep timer, position store (JSON, legacy and delimiter ids), controller (TTS exclusion, Stop, episode switching, deferred timer, engine-state follow), idle and session-sync policies, `TtsManager` hook, notification/manifest, view model, the episode action and the bar; 123 more in total than before the feature.
- `PodcastInstrumentationTest` (isolated `EInkUiTestRunner`) **3/3 PASS** on the Tecno and on the Pixel; it was not rerun after `380cc06d`: real Room v18 round trip, the bar clears the system bars on an edge-to-edge window, and a real `ExoPlayer` plays a generated 20 s WAV (duration known, position advances, pause stops it).
- Live on the **Tecno** (`115333744A005844`), NPR "Planet Money" (`https://feeds.npr.org/510289/podcast.xml`):
  - Database v18; **355 of 355** episodes have an https `audioUrl`, `audio/mpeg` and a duration of 121–3290 s; BBC News has 0 of 146 and shows no bar.
  - Play: media session `PLAYING`, `PodcastPlaybackService` foreground with type `mediaPlayback`, notification `MediaStyle`.
  - Skip forward 13 s → 29 s, skip back 36 s → 21 s; speed 1.5× reflected as `speed=1.5` in `dumpsys media_session`; the sleep-timer menu and the Vietnamese strings render.
  - Screen off (`Dozing`): playback continues, position 112 → 150 s (the Pixel run showed the same).
  - Leaving the Reader with the close button and with Back does not stop playback; reopening the episode shows the running session and the position keeps counting.
  - The notification-shade media card shows the title, feed, seek bar and a pause button; pausing there made the session `PAUSED` at 357 s. `KEYCODE_MEDIA_STOP` removed the session and the service; the saved position was 357 s and reopening the episode showed `5:57`.
  - **TTS ↔ podcast, both directions:** with the podcast `PLAYING`, "Read aloud" made `TtsForegroundService` `PLAYING` and the podcast `PAUSED`; with TTS speaking, pressing Play made the podcast `PLAYING` and the TTS session disappear.
- Also on the **Pixel 7 Pro** (`2B051FDH3006MU`, used with the owner's permission while the Tecno was not visible to adb): instrumentation 3/3, the same subscribe → play → skip → speed → sleep → lock-screen flow.
- `lintDevDebug` was not run (known local SDK 37 metadata issue); no lint pass is claimed. `connectedDevDebugAndroidTest` was not run.

## Additional audible smoke test — 2026-10-23

- Pixel 7 Pro (`2B051FDH3006MU`), APK `2c995f76`, DB backed up before install. Through the app UI, temporarily subscribed to BBC Global News Podcast and NPR Planet Money; used Planet Money because a metadata-logged BBC enclosure returned `SocketException: Connection reset` when ExoPlayer attempted playback (HTTP/HTTPS stream was also unreachable from the host). A small NPR enclosure range request from the host returned HTTP 206/audio-mpeg; no body was saved.
- Owner confirmed hearing the Planet Money episode **“We try to fix election polling (update)”** clearly through the phone speaker. ExoPlayer reached `PLAYING`; `dumpsys media_session` reported `speed=1.5` after selecting 1.5×.
- Paused seeking verified **9:05 → 8:50 → 9:05** with the two ±15 s buttons. Expanded player showed speed, both skip buttons and sleep control.
- Headset/Bluetooth was unavailable and not tested; TalkBack not tested. Full-screen ad did not appear during the smoke; only test banner ads appeared and were skipped.
- Both temporary feeds unsubscribed through the UI. Final DB snapshot `/Users/loitran/podcast_smoke_pixel_clean_20261023_1128`: Room v18, `PRAGMA integrity_check=ok`, BBC News only, 211 articles, no audio entries. Snapshot included Reader/WAL/SHM as present and was read-only inspected; no DB restore/manual edit.

## Sleep timer expiry

Verified twice. Tecno: started at 13:39:47 with a 15 minute timer (chip `14:59`); at 13:55 the media session was `PAUSED` (`speed=0.0`, position 2122 s), the chip was back to "Off" and the saved position was written. Emulator (`emulator-5554`): started at 21:17:53; at 21:35 the session was `PAUSED` (`speed=0.0`, position 1459 s), the chip showed "Off" and the bar showed `24:19` with a play button.

## Code review

A review of the whole feature found ten issues. After the owner approved fixing four groups, each behavioural issue was reproduced by a failing test first, then fixed in `380cc06d`:

- **Stop dropped the episode:** after Stop the controller forgot the audio, so the bar vanished until the article was reopened. It now returns to `IDLE` but keeps the selected episode, so Play works again.
- **Opening another episode stopped the playing one:** now it does not. The bar keeps controlling the playing episode; the open article gets a "Play: <title>" card (`PodcastEpisodeAction`) that switches explicitly (`playEpisode`). Owner's decision.
- **Sleep timer set before the service existed fired on the first Play:** it is now kept as a pending duration and starts counting only when Play is requested.
- **Controller status followed its own guess instead of ExoPlayer:** it forced `PLAYING` on the first tick even while buffering and never noticed an audio-focus pause or an unplugged headset. `PodcastEngine.status()` now reports the real player state and the tick follows it. On the emulator the first tick after Play showed `BUFFERING`, then `PLAYING`.
- **The service never stopped after pause or the end of an episode:** `PodcastIdlePolicy` stops it after 10 minutes of pause/error (immediately when idle).
- **The media session was rebuilt every second:** `SessionSyncPolicy` refreshes the playback state only on a status/speed change or a seek, and metadata only when it changes.
- **Position store broke ids that contain `,` or `:`:** positions are now one JSON array; the first version's `id:sec,id:sec` string is still read and migrated on the next save.
- **`http://` enclosures were dropped:** accepted now (other schemes are still rejected); the player tries `https://` first and falls back to the published `http://` URL (`AudioUrlCandidates`). Owner's decision. The cleartext fallback is possible because the app already sets `usesCleartextTraffic="true"`.
- **Cleanups (R5):** the unexplained `!!` in `ReadingPage` is gone, `actionNames` lost its unused parameter, and `PodcastPlayerViewModel.bind` no longer repeats the controller's guard.

The unit suite grew from 1191 to 1217 tests with these fixes. On the emulator after the fixes: Play showed `BUFFERING` then `PLAYING` with the bar advancing 24:59 → 25:06 → 25:12; opening another episode left the playing one running and showed the play card; pressing it switched the session metadata to the new episode at 0:00; Stop removed the session and the service while the bar stayed with a play button at the saved position.

## Limits and known issues

- **Phone-speaker sound was confirmed by the owner on 2026-10-23**, alongside advancing UI position and MediaSession evidence. Bluetooth and headset routing remain untested because no accessory was available; TalkBack was not run.
- `ArticleDao.insert` uses `REPLACE`, so a Fever re-fetch can reset the new audio columns to NULL (same limit as `readAt`). Articles stored before the upgrade have no audio until their feed's items are inserted again; `insertListIfNotExist` skips items that already exist, so old episodes keep no player.
- `DefaultHttpDataSource` does not follow an HTTPS→HTTP redirect, so an episode whose redirect chain drops to HTTP shows the error state. This matches the https-only rule. The BBC feed was dropped for verification because its enclosures are `http://` on a host that was unreachable.
- Reinstalling the app stops the playback service and cancels a running sleep timer; the timer is not persisted across a process death.
- Two on-device mishaps worth knowing: a third-party app (`lenslauncher`, debug build on the same phone) kept opening full-screen ads and its settings over the Reader, and one tap during the test landed on a Messenger heads-up notification and opened a chat (nothing was typed there). A foreground-activity guard cannot see heads-up notifications; screenshots are taken before each tap since then.
- The expand control is a small arrow in a tonal circle; new users may not discover it (tapping the title toggles it too). Accessibility was only checked through content descriptions in unit tests, not with TalkBack on the device.
- Not covered: golden screenshots, Macrobenchmark/Perfetto profiling, network conditioning, offline download, queues, a global mini player, Android Auto, and ad/IAP behaviour (deferred by the owner).
