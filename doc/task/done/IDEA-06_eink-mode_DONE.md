# [IDEA-06] E-Ink Mode — Completion Report

- **Status:** Implemented on local `dev`; not pushed. Independent subagent review had not returned when this report was written.
- **Completed:** 2026-10-18
- **Commits:** `b1182cec`, `ee9962b3`, `9899e26d`, `70554fad`, `664ecaa0`, `a410cddc`, `fbdf8286`, `c6937361`, `f03111d1`, `2e3abb7c`.

## Implementation

- `EInkModePref` is a DataStore boolean, default OFF, exposed through `Settings`/`LocalEInkMode`, with a localized Color & Style toggle in all 38 maintained locale files.
- E-Ink ON selects a monochrome light `ColorScheme` without rewriting the saved dark-theme preference; `BaseAsyncImage` applies a saturation-zero filter for static images. Video components are unchanged.
- App-owned navigation transitions and the Reader content transition become `None` only while E-Ink is ON. System animation scale is not touched.
- Reader taps in the left/right third turn pages through `LazyListState.scrollToItem(index, offset)`; centre taps stay available to children. Volume Up/Down turn pages only for the first key-down while Reader has an article, E-Ink is ON and TTS is not playing. `MainActivity.dispatchKeyEvent` delegates to `ReaderVolumeKeyBridge`; the Compose effect unregisters on dispose.

## Verification

- `./gradlew testDevDebugUnitTest`: **929 tests, 0 failures, 0 errors, 0 skipped**, including `EInkModePrefTest`, colour/filter/transition tests, `EInkModeSettingTest`, `EInkPageTurnTest`, `EInkVolumeKeyHandlerTest` and `StringResourceParityTest`.
- `./gradlew assembleDevDebug compileDevDebugAndroidTestKotlin`: **PASS**.
- `emulator-5554`, isolated `EInkUiTestRunner`: `EInkPageTurnInstrumentationTest` + `EInkModeIntegrationTest` **6/6 PASS** (side taps, centre child click, disabled mode, DataStore→theme, real settings-row toggle, title below status bar). Three volume lifecycle tests also passed with this runner.
- `emulator-5554`, standard runner: `EInkVolumeDispatchInstrumentationTest` **5/5 PASS** earlier, including real `MainActivity` key dispatch and unregistered no-op.
- Device-lock note: Gradle `connectedDevDebugAndroidTest` was not used.

## Limits and known issues

- No claim of a full manual smoke test of Reader/Settings navigation on a physical E-Ink panel.
- Standard app runner starts `RApp` and the ad SDK; one combined run of all three E-Ink classes failed with “No compose hierarchies”. Logs showed app-open `AdActivity` launches, but I did not capture a screenshot of an ad at that moment. UI tests use `EInkUiTestRunner` (`-PtestRunner=com.mckimquyen.reader.EInkUiTestRunner`) with plain `Application`.
- `lintDevDebug` still fails on existing `SmartCollectionDetailPage.kt:45` (`ProduceStateDoesNotAssignValue`) and local SDK 37 `api-versions.xml` parse errors. The new `RestrictedApi` finding for `dispatchKeyEvent` was addressed.
- Not implemented/run: golden screenshots, profiling/memory-leak/jank benchmarks, network-conditioning tests. IAP and ad mediation are not applicable to this feature.
