# [IDEA-06] E-Ink Mode — Completion Report

- **Status:** Implemented and verified on local `dev`; not pushed.
- **Completed:** 2026-10-18
- **Commits:** `b1182cec`, `ee9962b3`, `9899e26d`, `70554fad`, `664ecaa0`, `a410cddc`, `fbdf8286`, `c6937361`, `f03111d1`, `2e3abb7c`, `fd382196`, `2b441a22`, `4185b8d8`, `bc17083d`, `13461018`, `21eb0a32`, `4e7ffdc2`, `7a4285b2`, `41b04429`.

## Implementation

- `EInkModePref` is a DataStore boolean, default OFF, exposed through `Settings`/`LocalEInkMode`, with a localized Color & Style toggle across all 38 maintained locale files.
- E-Ink ON selects a monochrome light `ColorScheme`, neutralizes `TonalPalettes` accessors to pure grays so switches/dividers/SVG icons do not leak color, and scopes `LocalDarkTheme` to light helpers without rewriting the saved dark-theme preference. `BaseAsyncImage` applies a saturation-zero filter for static images. Video components keep color.
- App-owned navigation transitions, the Reader content transition, and toolbar expand/shrink visibility become instant `None` only while E-Ink is ON. System animation scale is not touched.
- Reader taps in the left/right third turn pages through `LazyListState.scrollToItem(index, offset)` using the content at viewport-minus-overlap as the next top, handling tall items and clamping edges. Centre taps stay available to children. Volume Up/Down turn pages only on key-down while Reader has an article, E-Ink is ON, and TTS is not playing. `MainActivity.dispatchKeyEvent` delegates to `ReaderVolumeKeyBridge`; `ReaderVolumeKeys` unregisters on dispose and handles `ActivityScenario.recreate()`.

## Verification

- `./gradlew testDevDebugUnitTest`: **935 tests, 0 failures, 0 errors, 0 skipped**.
- `./gradlew assembleDevDebug compileDevDebugAndroidTestKotlin`: **PASS**.
- `emulator-5554`, isolated `EInkUiTestRunner` (18 instrumentation tests, **all PASS**):
  - Tap zones: left/right advance and retreat, top clamps, centre tap does not turn.
  - Integration: DataStore toggle drives theme white, restores dark preference without overwriting, real settings row persists toggle, Color & Style title clears status bar.
  - Palette: custom palette accessors return neutral gray when ON and restore original colors when OFF.
  - Volume lifecycle: claims keys only while enabled and TTS stopped, unregisters on leaving, no stale handler after `ActivityScenario.recreate()`.
  - Image QA: pixel golden asserts pure gray luminance on red image when ON and red restored when OFF; loopback slow response, HTTP 503, and retry do not break rendering.
  - Reader interaction: centre HTML link opens without page turn, long-press text selection and clipboard copy work, real TopBar/Content/BottomBar insets clear system bars, RSVP speed-reading action remains clickable.
  - Video QA: in-article video plays inside tap-zone wrapper, play button starts playback, PiP marker arms on play and disarms on dispose, centre touch does not page turn.
  - Performance: 80 page turns over 1,000 items (median 99.65 ms, p95 187.76 ms, heap growth 590 KB after GC); 40 mode toggles (1.27 s total, 41 recompositions, 4 KB heap growth).
- `emulator-5554`, standard runner (`EInkVolumeDispatchInstrumentationTest`): **2/2 PASS** for real hardware `MainActivity.dispatchKeyEvent` forwarding and unregistered pass-through.
- Real-app smoke on `emulator-5554`:
  - App launched, navigated to Settings → Color & style, E-Ink toggle switched ON, monochrome theme and neutral palette applied.
  - Subscribed to real BBC News feed, opened article in Reader.
  - Edge tap turned page forward; centre tap kept content; left edge tap turned page back.
  - Volume Up turned page forward; Volume Down turned page back.
  - Exited Reader back to list; Volume Down showed system volume panel (keys handed back to OS).
- Device-lock note: all live device operations used `adb -s emulator-5554`. Gradle `connectedDevDebugAndroidTest` was not used.

## Limits and known issues

- Real-app smoke test encountered an interstitial test ad on back navigation; ad was closed and smoke test completed on app UI.
- No hardware test on a physical E-Ink panel (run on emulator-5554).
- Performance tests are bounded Compose/emulator regression checks, not full Macrobenchmark/Perfetto/LeakCanary traces.
- No dedicated golden screenshot framework (Paparazzi/Roborazzi not installed); pixel golden and insets assertions used instead.
- Network conditioning tested via local HTTP server (slow chunks/503/retry); E-Ink feature itself has no network calls.
- `lintDevDebug` still blocked by pre-existing `SmartCollectionDetailPage.kt:45` and local SDK 37 `api-versions.xml` parse errors. `RestrictedApi` on `dispatchKeyEvent` was annotated with `@SuppressLint`.
- IAP and ad mediation tests not applicable (not invoked by this feature).
