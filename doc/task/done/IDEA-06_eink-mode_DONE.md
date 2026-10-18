# [IDEA-06] E-Ink Mode — Completion Report

- **Status:** Implementation and targeted QA verified on local `dev`; not pushed. Independent review findings were fixed. Full real-app manual navigation smoke and dedicated profiling/golden framework remain outstanding, so backlog stays In progress.
- **Completed:** 2026-10-18
- **Commits:** `b1182cec`, `ee9962b3`, `9899e26d`, `70554fad`, `664ecaa0`, `a410cddc`, `fbdf8286`, `c6937361`, `f03111d1`, `2e3abb7c`, `fd382196`, `2b441a22`, `4185b8d8`, `bc17083d`, `13461018`, plus the review-fix commit that follows this report.

## Implementation

- `EInkModePref` is a DataStore boolean, default OFF, exposed through `Settings`/`LocalEInkMode`, with a localized Color & Style toggle in all 38 maintained locale files.
- E-Ink ON selects a monochrome light `ColorScheme`, and `TonalPalettes` accessors return neutral gray so direct palette consumers no longer leak colour, without rewriting the saved dark-theme preference; `BaseAsyncImage` applies a saturation-zero filter for static images. Video components are unchanged.
- App-owned navigation transitions and the Reader content transition become `None` only while E-Ink is ON. System animation scale is not touched.
- Reader taps in the left/right third turn pages through `LazyListState.scrollToItem(index, offset)` using the content at viewport-minus-overlap as the next top, including tall items; centre taps stay available to children. Volume Up/Down turn pages only for the first key-down while Reader has an article, E-Ink is ON and TTS is not playing. `MainActivity.dispatchKeyEvent` delegates to `ReaderVolumeKeyBridge`; the Compose effect unregisters on dispose.

## Verification

- `./gradlew testDevDebugUnitTest`: **935 tests, 0 failures, 0 errors, 0 skipped**, including `EInkModePrefTest`, colour/filter/transition tests, `EInkModeSettingTest`, `EInkPageTurnTest`, `EInkVolumeKeyHandlerTest` and `StringResourceParityTest`.
- `./gradlew assembleDevDebug compileDevDebugAndroidTestKotlin`: **PASS**.
- `emulator-5554`, isolated `EInkUiTestRunner`: 10-test combined run **PASS** (side taps, centre child click, disabled mode, DataStore→theme, real settings-row toggle, title below status bar, custom palette accessors neutral while ON and restored when OFF, volume composition/TTS/off/recreate lifecycle). Three volume lifecycle tests also passed with this runner; combined run of 9 UI/lifecycle tests passed including actual `ActivityScenario.recreate()`.
- `emulator-5554`, standard runner: `EInkVolumeDispatchInstrumentationTest` **5/5 PASS** earlier; final separate hardware `MainActivity` key-dispatch/unregistered no-op run **2/2 PASS**. A first attempt used the standard runner component while the isolated APK was still installed; it failed to locate that runner before running tests. Rebuilding/reinstalling the standard test APK resolved this.
- Added QA on `emulator-5554` with `EInkUiTestRunner` (final combined 18-test run **PASS**): `EInkImageQaTest` renders real pixels (red image becomes gray luminance while ON, returns to red when OFF) and uses a loopback fixture for slow image, HTTP 503 and retry; `EInkReaderInteractionQaTest` covers real `Reader` HTML link tap in centre, long-press text selection/copy, real TopBar/Content/BottomBar insets and clickable RSVP action; `EInkVideoQaTest` loads a local MP4, verifies centre touch/control availability does not page-turn and PlayerView releases its player after leaving; `EInkPerformanceQaTest` measures page turns (80 taps over 1,000 items: median 99.65 ms, p95 187.76 ms, heap growth 590 KB after GC) and mode toggles (40 toggles: 1.27 s total, 41 recompositions, 4 KB heap growth).
- Device-lock note: Gradle `connectedDevDebugAndroidTest` was not used.

## Limits and known issues

- No claim of a full manual smoke test of Reader/Settings navigation on a physical E-Ink panel.
- Standard app runner starts `RApp` and the ad SDK; one combined run of all three E-Ink classes failed with “No compose hierarchies”. Logs showed app-open `AdActivity` launches, but I did not capture a screenshot of an ad at that moment. UI tests use `EInkUiTestRunner` (`-PtestRunner=com.mckimquyen.reader.EInkUiTestRunner`) with plain `Application`.
- `lintDevDebug` still fails on existing `SmartCollectionDetailPage.kt:45` (`ProduceStateDoesNotAssignValue`) and local SDK 37 `api-versions.xml` parse errors. The new `RestrictedApi` finding for `dispatchKeyEvent` was addressed.
- Video test does not prove ExoPlayer starts playback: direct `player.play()` was reverted to paused by existing `ArticleVideoPlayer` lifecycle behaviour during Compose test, so I intentionally removed that claim and limited the test to centre-touch/no-page-turn plus dispose cleanup.
- The performance tests are bounded Compose/emulator regression checks, not Macrobenchmark, Perfetto or LeakCanary results; no true jank/frame-timeline run was performed. There is no screenshot-golden framework, only direct pixel assertions.
- Network conditioning is limited to loopback slow response/503/retry for image loading; E-Ink itself has no network path. No physical network shaping or real RSS sync was run.
- Not implemented/run: golden screenshot framework and full real-app manual smoke. IAP and ad mediation are not applicable to this feature.
