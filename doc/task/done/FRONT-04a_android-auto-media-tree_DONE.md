# FRONT-04a (Android Auto slice; FRONT-04 stays open)

**Date:** 2026-11-11  
**Branch:** `dev`  
**Scope:** Android Auto only. The media tree now has two browsable branches (**Morning briefing** = CommuteCast, **Latest podcasts** = the 5 newest unread episodes that carry audio), a podcast can be started from the car list, the car controls drive whichever engine started last, and the two engines pause each other on every entry path. **Not done:** Wear OS, a real car or head unit, and the Desktop Head Unit (the owner chose the adb path instead). `doc/task/todo/FRONT-04_android-auto-and-wear-os.md` stays open.  
**Spec / plan:** `docs/superpowers/specs/2026-11-10-front-04-android-auto-media-tree-design.md`, `docs/superpowers/plans/2026-11-10-front-04-android-auto-media-tree.md`.

## Implemented

- `ArticleDao.queryLatestUnreadPodcastEpisodes(accountId, limit)` returns `PodcastBrowseItem(id, title, feedName, durationSec)`: unread, non-empty `audioUrl`, same account, `date DESC, id DESC`. It selects four small columns only (no body, no URL).
- Pure logic, all JVM-tested: `AutoMediaTree`/`AutoMediaIds` (tree, id prefixes, "id must be one of the five shown"), `AutoSideTracker` + `AutoSessionMapper` (which engine is active, state/metadata for the car session), `AutoControlRouter` + `CommuteAutoTransport`/`PodcastAutoTransport` (commands go to the active side; podcast seeks by seconds, CommuteCast keeps skip-line and ignores `onSeekTo`), `AutoPodcastLauncher` (prefix check, then shown-list check, then the audio URL is read for that one article), `AutoSessionCallback`.
- `CommuteMediaSessionService` is thinner and wired to those parts: root `commutecast_root` unchanged, children by branch, unknown parent still `onError`, the session mirrors the active engine (`ERROR` becomes `STATE_ERROR`), voice search still starts the latest CommuteCast episode and never picks a podcast.
- Yielding: `CommuteGate` (next to `TtsGate`) wired in `PodcastModule`. Starting a podcast pauses CommuteCast; starting CommuteCast (`speakCurrentDialogue`, so every entry path: Auto, phone screen, voice) pauses the podcast, including while the TTS engine is still starting. No new audio-focus mechanism.
- `PodcastPlaybackService.NOTIFICATION_ID` 9183 -> 9184 (it collided with CommuteMediaSessionService and the two notifications overwrote each other).
- Two new strings in all 38 locale files (English and Vietnamese written, 36 English).
- No Room change (still v20), no migration, no dependency, manifest and `automotive_app_desc.xml` unchanged.

## Verification (JVM)

- Full unit suite on the final code (`--rerun-tasks`, HEAD `6c0de0f3`, clean tree): **2,733 tests, 0 failures, 0 errors, 0 skipped**, 383 result files, none stale. (+60 over EXC-05c's 2,673.) The first full run had **1 failure** in `MirrorServerTest.aClosedStreamFreesItsSlotSoAFourthBrowserCanJoinLater` ("condition not met"); that file is untouched by this work, it passed 3 of 3 alone with `--rerun-tasks`, and the second full run was clean. I treat it as a timing flake in an unrelated socket test, not as proven.
- Lint (`--rerun-tasks`): **0 errors, 2,463 warnings** (baseline), 0 hits in the new files. `assembleDevDebug` and `assembleDevDebugAndroidTest` succeeded.
- Mutation checks, each caught by the intended test and restored with `cmp`: DAO (unread filter, account filter, order, id tie-break, blank audio, selecting a body column); tree (id prefix, limit 5, "still in result" check, blank id, leaf id value, no cut at 5); session (3 side-selection rules, speed when not playing, seek action present/absent, ERROR state); router (wrong side, idle play, idle pause, side read once); yielding (callback not invoked, podcast not pausing CommuteCast, CommuteCast not pausing podcast, queued start not counted, gate pause empty); notification ids (collision in all three services); locale key missing; transports/callback (6); launcher (3).
- On a real phone, 4 mutations of the service wiring (wrong root id, branches playable, root children returned for every parent, unknown parent turned into an empty list) were each caught by `CommuteMediaBrowserIntegrationTest` after a rebuild and install.

## Verification (Pixel 7 Pro `2B051FDH3006MU`, Android 17, 2026-11-11, no DHU)

Before touching it: `adb devices`, `dumpsys activity` (no active instrumentation, launcher in front), foreign processes were only the usual `bemytester` and `loyalty.skyjoy.vn.stg`. Once the owner confirmed they were done with `loyalty.skyjoy.vn.dev` (it had been in front), only `com.mckimquyen.reader` was opened. Byte-exact backup of Room, sidecars, DataStore and `pairing_store.xml` was taken first.

- **androidTest `CommuteMediaBrowserIntegrationTest` (run with `am instrument`, never `connected*`):** written first and run **red** (4 of 8 failed: root returned `[commutecast_latest_episode]`, the branches did not exist); **8 of 8 green** after the wiring. The 5 new tests check: root has the two browsable branches in order with titles, the CommuteCast branch holds at most one playable leaf with the old id, the podcast branch has at most 5 playable leaves with the `podcast:` prefix and no audio URI or URL-like extra, an unknown parent reports `onError`, and unknown/hostile ids (`podcast:front04-does-not-exist`, `podcast:`, `podcast:../../x' OR 1=1 --`, `something-else`) start nothing.
- **Fixture:** 7 unread audio episodes, 1 read episode with audio and 1 unread without audio, in a new feed, on top of the backup copy (integrity and foreign-key checks passed, copy verified byte for byte). Audio was a spoken sample served from the Mac.
- **Real browse and play through `MediaBrowserCompat`/`MediaController`:** the podcast branch returned exactly `ep1..ep5` (the read and no-audio rows and ep6/ep7 absent). `playFromMediaId(ep1)` ended in `PLAYING` and the session metadata was `Front04 Episode 1`. Pause gave `PAUSED`; fast-forward kept a non-negative position.
- **Yielding on the phone:** after `playFromMediaId(commutecast_latest_episode)` the car session switched to the CommuteCast metadata (`Front04 Smoke Bulletin`) and `dumpsys media_session` showed the podcast session `PAUSED` at position 6000 ms (it was `PLAYING` just before). Starting the podcast again switched the session back to the podcast metadata.
- **Car session mirrors the active engine:** while the podcast played, the `CommuteMediaSession` showed `PLAYING` with the podcast metadata and the podcast action set including seek (`actions=1919`), while `PodcastPlaybackService` kept its own session for its notification.
- **Restore:** at the end Room, `-shm`, `-wal`, DataStore and `pairing_store.xml` compared **byte-identical** to the backup. The temporary local-network permission was revoked, the audio server stopped, the test APK uninstalled and the app force-stopped. `commute_cast_prefs.xml` (changed by the smoke) was put back from its own backup key and `podcast_positions.xml` was removed; I wrote that file back byte for byte but, unlike the database, I did not have a byte-exact backup of it, so it is restored from the app's own backup copy, not from a pre-smoke snapshot. The app now on the phone is the dev debug build of this code.

## Second Pixel run: podcast started while CommuteCast was speaking (2026-11-11)

Same gates and a fresh byte-exact backup (this time also `commute_cast_prefs.xml`, which the first run had not backed up). A longer 24 s podcast sample and an 8-line CommuteCast bulletin were used. Started the bulletin through the car session id, let the speech engine speak, then `playFromMediaId(podcast:...)`, and sampled `dumpsys media_session` and `dumpsys audio` at fixed points:

- While CommuteCast spoke: its session was `PLAYING`, metadata `Front04 Reverse Probe`; the Google TTS player was started.
- Just after the podcast started: the audio dump shows the Google TTS player `stopped` and released, the CommuteCast player `paused`, **CommuteAudioPlayer abandoned its audio focus**, and ExoPlayer then requested focus (about 170 ms later) and a new `AudioTrack` for the podcast started. The car session switched to `Front04 Episode 1` with `PLAYING`; the podcast service session was `PLAYING` too.
- So the reverse direction (podcast starts -> CommuteCast stops) holds on a real phone with real audio output, in this one run on one device. I did not listen to the speaker, so "audible" rests on the audio dump (player events and focus calls), not on hearing it.
- Restore afterwards: Room, `-shm`, `-wal`, `commute_cast_prefs.xml`, `pairing_store.xml` and DataStore all byte-identical to this run's backup; permission revoked, server stopped, test APK uninstalled, app stopped. Note that this restore is to the state at the **start of the second run**, which is the state left by the first run's repair of `commute_cast_prefs.xml` (see above), not to the original pre-FRONT-04 state of that one file.

## DHU attempt on Tecno KJ7 `115333744A005844` (Android 14, 2026-11-11), not successful

The owner asked me to use the Tecno for the Desktop Head Unit and said to drive it through adb. KJ7 has Android Auto 18.0.664264. State checked first (no instrumentation, launcher in front, no foreign processes), a byte-exact backup taken, the current dev debug build installed (no test APK was ever installed on KJ7) and the same 7-episode fixture loaded.

- Opening the Android Auto settings and tapping **Version** ten times produced the dialog "Allow development settings?". I did not press OK; the owner did. After that I started the head unit server from the menu, forwarded `tcp:5277` and ran DHU 2.0 (`desktop-head-unit`, mac-arm64, build 2022-03-30). The phone listened on 5277 and the DHU log said `connected.`.
- **DHU window: "Waiting for phone..."** and it never showed a car UI. The first DHU run also exited by itself when run from a background shell (its stdin was closed); I fixed that by holding stdin open with a fifo. A later full-screen capture showed another app, so only the earlier crop of the DHU window is evidence of its state.
- Phone log (`GH.ConnLoggerV2`, `CAR.*`): the session went through handoff, `VERSION_NEGOTIATION_SUCCESS` (protocol 1.7), `SSL_SUCCESS`, `SDP_RESPONSE_RECEIVED`, `GEARHEAD_PROJECTION_ENABLED` and `AUTHORIZATION_COMPLETE`, then **`CAR.AUTH: Call from UID 10095 is not allowed.`** (`isGoogleSigned=true`, `isPackageAllowed=false`), followed by `java.lang.IllegalStateException: Already connected` in Android Auto's own `DeveloperHeadUnitNetworkService` and `GEARHEAD_ENDPOINTS_FAILED`; about 5 s later `USB_ISSUE_PROJECTION_NOT_STARTED`. Two attempts, same result (the first stopped at `FIRST_ACTIVITY_LAUNCHED` and timed out).
- I read that as Google Play services refusing to let this Android Auto package act as a car-service client on this device (the Pixel's Play services is 26.41.31; the Tecno's is 26.43.30, also newer than the old DHU build). **That is my reading of one log line, not a verified cause.** I did not try another phone, another DHU build or a different Play services version, and I did not touch the app (`CommuteMediaSessionService` was never reached: no browse request from Android Auto appeared in this run, so nothing here says my service is or is not accepted by Android Auto).
- Cleanup done: DHU and the audio server stopped, the fifo and its helpers killed, `adb forward` removed, head unit server stopped and **Quit developer mode** tapped (menu back to "Help & feedback"/"Send feedback" only), no local-network permission had been granted on KJ7. KJ7 Room, `-shm`, `-wal`, DataStore and `commute_cast_prefs.xml`/`podcast_positions.xml` compared **byte-identical** to the backup; no `pairing_store.xml` existed before or after. The app is the current dev debug build; it was force-stopped.
- Not tried: the Pixel for DHU (it was the device the owner wanted to avoid bothering with manual steps), and a Desktop Head Unit run against an emulator.

## Found on the phone (not by the JVM tests)

1. My first smoke showed the podcast stuck in `BUFFERING` and no request reached the Mac. Cause: on Android 17 the app did not hold `ACCESS_LOCAL_NETWORK`, and the fixture audio lived on the LAN. After granting it temporarily the podcast reached `PLAYING`. This is an artifact of using a LAN fixture; real episodes are on the internet. Not a product change.
2. A wrong first reading of my own logs: I filtered by a process id that turned out to be the Google TTS process, then by the instrumentation process; the app and the instrumentation share a process. I redid the check with the session dump, which is what the figures above come from.
3. Plan error caught by the build: the plan said to delete `PlayFromSearchCallback`; `TtsForegroundService` still uses it, so it stays.

## Not verified / limits

- **The Desktop Head Unit did not reach a car screen, so I cannot say how the two branches look or behave on it.** I tried it on the Tecno KJ7 (see the DHU attempt below). It connected, negotiated and authenticated, then Android Auto on the phone refused the session, so the DHU window stayed on "Waiting for phone...". Nothing here shows how long titles are cut or how the steering-wheel controls behave. No real car or head unit either.
- **CommuteCast speaking, then a podcast started, was measured in a second Pixel run (below).** In the first run the CommuteCast bulletin had two very short lines and was already `PAUSED` when I sampled, so that run proved only the CommuteCast -> podcast direction at the session level; I did not claim more.
- **Audio focus: only the podcast-after-CommuteCast switch was observed** (abandon, then ExoPlayer request, no overlap seen in that run). The reverse switch, navigation, phone calls and other media taking focus were not tried, and one clean switch does not show that overlap cannot happen.
- **Voice search** ("play ...") was not exercised on the phone; only its unit behaviour (always the latest CommuteCast episode).
- "Unread" means the article's unread flag, not a saved listening position (an episode half-listened but still unread stays in the list).
- The client check is still by package name only (no signature check), as before.
- Artwork is the app icon, not per-episode images. The two session objects still coexist.
- `CommuteAudioPlayer` (and its `TextToSpeech`) is now created when the podcast controller is first injected, i.e. earlier than before. I did not measure the cost.
- The `renderSession` metadata is the short title/subtitle; the longer CommuteCast dialogue text set by `publishMetadata` can be replaced by it when the side changes. Not looked at on a car screen.
- New strings are English in 36 locales.
- The on-device smoke test class I used (`AutoPodcastSmokeDeviceTest`, needs a fixture in the database) is **not committed**; it was a throwaway probe and a copy is only in `/tmp`.

## Commits

`ArticleDao` query, `AutoMediaTree`, `AutoSession` (tracker/mapper), `AutoControlRouter`, `CommuteGate`, notification id, 38-locale strings, transports and callback, the red androidTest, `AutoPodcastLauncher`, and the service wiring (`6c0de0f3`), plus the spec correction commits (`TtsGate` wraps `TtsManager`, not CommuteCast, so neither direction was wired before this work).
