# [IDEA-06] E-Ink Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add optional E-Ink mode for whole app with pure white/black UI, grayscale static images, suppressed app animations, Reader tap-zone page-turn and gated volume-key page-turn.

**Architecture:** Store mode in DataStore (`EInkModePref`, default OFF); expose through `Settings` and a CompositionLocal; conditionally override global Material color scheme in `AppTheme`. `BaseAsyncImage` applies grayscale rendering only while mode is active. Reader owns tap zones; volume interception is chosen only after instrumentation verifies hardware-key dispatch and lifecycle restoration. Existing `LazyListState` scrolls instantly by about one viewport with overlap. Theme preferences remain unchanged and reappear after disabling E-Ink mode.

**Tech Stack:** Kotlin 2.1.0, Jetpack Compose, Material 3, DataStore Preferences, Navigation Compose, JUnit4, MockK, Robolectric, AndroidX instrumentation tests.

## Global Constraints

- E-Ink mode default is OFF; no automatic device detection.
- E-Ink theme is light monochrome; do not modify saved dark-theme or palette preferences.
- Grayscale static images/thumbnails; video remains unfiltered.
- Suppress app-owned animations only; never change system animation scale.
- Volume keys turn pages only in Reader while E-Ink is enabled and TTS is stopped; instrument actual hardware dispatch and ensure handler unregisters/restores correctly on navigation and Activity recreation.
- Tap left third = previous viewport, right third = next viewport, center = unchanged touch behavior.
- Localize strings across all maintained `values-*` directories; keep `StringResourceParityTest` green.
- Run live Android tests only against locked `emulator-5554`, always with `adb -s emulator-5554`; never use `connectedDevDebugAndroidTest` without constraining device enumeration, because Gradle previously ran tests on all attached devices.
- Do not modify unrelated `.idea` files or existing local changes.
- No new dependency unless repository-native options demonstrably cannot cover a required test.

---

## Files & Responsibilities

- `app/src/main/java/com/mckimquyen/reader/infrastructure/pref/EInkModePref.kt`: DataStore-backed preference, ON/OFF, default OFF.
- `app/src/main/java/com/mckimquyen/reader/ui/ext/ExtDataStore.kt`: persistent boolean key.
- `app/src/main/java/com/mckimquyen/reader/infrastructure/pref/Settings.kt`: setting field, CompositionLocal and SettingsProvider mapping.
- `app/src/main/java/com/mckimquyen/reader/infrastructure/pref/Pref.kt`: `Preferences.toSettings()` conversion.
- `app/src/main/java/com/mckimquyen/reader/ui/theme/Theme.kt`: choose monochrome light ColorScheme while enabled.
- `app/src/main/java/com/mckimquyen/reader/ui/component/base/BaseAsyncImage.kt`: optional grayscale drawing filter conditioned on E-Ink.
- `app/src/main/java/com/mckimquyen/reader/ui/page/setting/color/ColorAndStylePage.kt`: user-facing toggle in Appearance section.
- `app/src/main/java/com/mckimquyen/reader/ui/ext/ExtNavGraphBuilder.kt`: existing `animatedComposable` definition; suppress navigation transitions when mode enabled.
- `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingPage.kt`: zero-duration content transition, tap zones, volume handling.
- Locale `strings.xml` resources in every maintained `values-*` directory: E-Ink label and description.
- Tests alongside preference/theme/image/page-turn behavior and a targeted `androidTest` edge-to-edge/tap integration test.

---

### Task 1: Preference, DataStore and Settings Propagation (TDD)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/infrastructure/pref/EInkModePref.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/ext/ExtDataStore.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/pref/Settings.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/infrastructure/pref/Pref.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/infrastructure/pref/EInkModePrefTest.kt`

**Interfaces:**
- Produces: `EInkModePref.ON`, `.OFF`, `.default`, `.fromPreferences(preferences)` and `operator fun EInkModePref.not()`.
- Produces: `Settings.eInkMode` and `LocalEInkMode`.

- [ ] **Step 1: Write failing preference tests**
  Test default OFF for empty preferences, ON/OFF round-trip, and `not()` toggling.
- [ ] **Step 2: Run targeted test; confirm RED**
  `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.pref.EInkModePrefTest"`
  Expected: compilation or assertion failure because pref/key/mapping do not exist.
- [ ] **Step 3: Add `EInkModePref` and DataStore key**
  Match `ReadPositionSyncPref`: key name `eInkMode`, sealed Boolean pref with `ON`, `OFF`, default `OFF`, `fromPreferences`, and `not()`.
- [ ] **Step 4: Register in `Settings` and `Pref.toSettings()`**
  Add `eInkMode: EInkModePref = EInkModePref.default`; expose `LocalEInkMode`; provide it in `SettingsProvider`; map using `EInkModePref.fromPreferences(this)`.
- [ ] **Step 5: Run targeted test; confirm GREEN**
  Run the same Gradle test command; expect all EInkModePrefTest cases pass.
- [ ] **Step 6: Commit**
  `git commit -m "feat(pref): add E-Ink mode preference"`

---

### Task 2: Monochrome Theme and Grayscale Static Images (TDD)

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/mckimquyen/reader/ui/theme/EInkColorScheme.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/component/base/BaseAsyncImage.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/ui/theme/EInkColorSchemeTest.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/ui/component/base/EInkImageFilterTest.kt`

**Interfaces:**
- Produces: pure function `fun eInkLightColorScheme(): ColorScheme`; roles used by Material components have explicit black/white/high-contrast gray colors.
- `BaseAsyncImage` continues to accept existing callers unchanged; E-Ink activation adds only a draw `ColorFilter` to static images.

- [ ] **Step 1: Test color-scheme contrast**
  Assert `background/surface == Color.White`, `onBackground/onSurface == Color.Black`, and primary controls use black/white. Verify non-E-Ink scheme stays selected when disabled.
- [ ] **Step 2: Run targeted tests; confirm RED**
  `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.theme.EInkColorSchemeTest"`
- [ ] **Step 3: Create monochrome ColorScheme using named color constants**
  Define colors once in `EInkColorScheme.kt`; do not duplicate magic colors across components. Keep semantic error roles distinguishable with black/white plus text/icon labels, not chromatic red.
- [ ] **Step 4: Select scheme in `AppTheme`**
  Read `LocalEInkMode.current.value`; select monochrome scheme when ON, otherwise use existing `dynamicLightColorScheme` / `dynamicDarkColorScheme` path exactly.
- [ ] **Step 5: Add optional static-image filter to `BaseAsyncImage`**
  `val eInkEnabled = LocalEInkMode.current.value`; pass saturation-zero `ColorMatrix` filter to `Image`. Keep placeholders and errors legible. Do not apply this composable to the video player; leave `ArticleVideoPlayer` unchanged.
- [ ] **Step 6: Test E-Ink filtering is scoped and disabled by default**
  Compose test should verify filter is applied for E-Ink ON and not applied for OFF using an injectable/internal image renderer or semantics tag; avoid snapshotting framework painter internals.
- [ ] **Step 7: Run targeted tests and commit**
  `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.theme.EInkColorSchemeTest" --tests "com.mckimquyen.reader.ui.component.base.EInkImageFilterTest"`
  `git commit -m "feat(ui): apply monochrome E-Ink theme and image filter"`

---

### Task 3: Color & Style Toggle and Complete Localization

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/setting/color/ColorAndStylePage.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: every maintained `app/src/main/res/values-*/strings.xml`
- Create: `app/src/test/java/com/mckimquyen/reader/ui/page/setting/color/EInkModeSettingTest.kt`

**Interfaces:**
- `EInkModePref` is consumed via `LocalEInkMode`; preference writes through `put(context, scope)`.

- [ ] **Step 1: Add `eink_mode_title` and `eink_mode_desc` to base strings**
  Text: “E-Ink Optimisation”; “Pure monochrome display, grayscale images, no app animations, tap page edges or use volume keys to turn pages.”
- [ ] **Step 2: Add translations to all locale files**
  Use `find app/src/main/res -maxdepth 1 -type d -name 'values-*'` and add both key/value entries to every locale resource file. Preserve exact base key set; duplicate/missing keys fail `StringResourceParityTest`.
- [ ] **Step 3: Add `SettingItem` in Appearance section**
  `BaseSwitch(activated = eInkMode.value)`; tap row and switch both toggle via `(!eInkMode).put(context, scope)`.
- [ ] **Step 4: Add widget test**
  Verify setting label and switch initial unchecked; click enables switch; recompose with ON and confirm monochrome description/state. Use localized `R.string` lookups, not hard-coded UI labels.
- [ ] **Step 5: Run resource parity and widget tests**
  `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.pref.StringResourceParityTest" --tests "com.mckimquyen.reader.ui.page.setting.color.EInkModeSettingTest"`
- [ ] **Step 6: Commit**
  `git commit -m "feat(settings): add localized E-Ink mode toggle"`

---

### Task 4: Suppress App-Owned Animations (TDD)

**Files:**
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/ext/ExtNavGraphBuilder.kt`.
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingPage.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/ui/page/common/EInkTransitionTest.kt`

**Interfaces:**
- Shared internal helper selects instant vs default transition; only app-owned transitions, never global system animator scale.

- [ ] **Step 1: Test transition selector**
  Test E-Ink ON returns `EnterTransition.None`/`ExitTransition.None`; OFF preserves current transition behavior.
- [ ] **Step 2: Read `ExtNavGraphBuilder.kt` and assert default transition contract**
  Confirm current enter/exit/pop enter/pop exit transitions are preserved when E-Ink mode is OFF; add regression tests before changing them.
- [ ] **Step 3: Gate navigation/content transitions**
  Set instant `EnterTransition.None` and `ExitTransition.None` in navigation and Reader `AnimatedContent` only when mode ON. Preserve previous animations when OFF. Do not globally rewrite unrelated animation components without a clear owner/scope.
- [ ] **Step 4: Run transition tests and compile**
  `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.page.common.EInkTransitionTest" assembleDevDebug`
- [ ] **Step 5: Commit**
  `git commit -m "feat(reader): disable app transitions in E-Ink mode"`

---

### Task 5: Reader Tap Zones and Volume-Key Page Turning (TDD)

**Files:**
- Create: `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/EInkPageTurn.kt`
- Modify: `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingPage.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/ui/page/home/read/EInkPageTurnTest.kt`
- Create: `app/src/test/java/com/mckimquyen/reader/ui/page/home/read/EInkVolumeKeyHandlerTest.kt`

**Interfaces:**
- Produces pure `enum class TapZone { PREVIOUS, CENTER, NEXT }` and `fun resolveTapZone(x: Float, width: Float): TapZone`.
- Produces pure page-target helper accepting visible indices, offsets and item counts; Compose adapter calls `LazyListState.scrollToItem(targetIndex, targetOffset)` to move about one viewport while preserving overlap and clamping edges.

- [ ] **Step 1: Test zone boundaries and invalid geometry**
  Left `< width / 3`, center `width / 3..2*width / 3`, right `> 2*width / 3`; reject non-positive width as center/no-op. Add exact boundary cases.
- [ ] **Step 2: Test scrolling at list boundaries**
  Robolectric Compose test with LazyColumn and 20 tagged items. Assert next target advances about one viewport while leaving the final visible item as overlap; previous target retreats similarly; both clamp at first/last; center tap does not scroll.
- [ ] **Step 3: Implement gesture only when E-Ink ON**
  Add `pointerInput(eInkEnabled, articleId)` to a parent that preserves child click/selection semantics. Do not swallow center-zone taps; make center test confirm link taps and text selection still work. Use screen width from pointer input `size.width`; compute target from `listState.layoutInfo.visibleItemsInfo`, first index/offset and total count; viewport height informs advance but use `scrollToItem(targetIndex, targetOffset)` (not first-item offset as global scroll).
- [ ] **Step 4: Implement lifecycle-safe volume handler**
  First try Compose `Modifier.onPreviewKeyEvent` only if device instrumentation confirms volume key events reach it without stealing focus. If Android intercepts before Compose, add a narrow Activity dispatcher bridge only for Reader + E-Ink ON + TTS stopped; delegate all other keys and repeat events; unregister/restore on dispose, navigation and recreation. Instrument: key-down turns once, key-up/repeat pass, TTS-active and E-Ink-off keep volume behavior, leaving Reader restores normal handling. Never install a permanent global listener.
- [ ] **Step 5: Add ViewModel/UI state tests for TTS condition**
  Verify keys not consumed when TTS plays, mode off or Reader not composed; volume directions correct when active.
- [ ] **Step 6: Run targeted tests and commit**
  `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.ui.page.home.read.EInkPageTurnTest" --tests "com.mckimquyen.reader.ui.page.home.read.EInkVolumeKeyHandlerTest"`
  `git commit -m "feat(reader): add E-Ink tap and volume page turning"`

---

### Task 6: Integration, Edge-to-Edge, and Release Verification

**Files:**
- Create: `app/src/androidTest/java/com/mckimquyen/reader/ui/page/setting/color/EInkModeIntegrationTest.kt`
- Create: `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/read/EInkPageTurnInstrumentationTest.kt`
- Modify: `doc/feature.md`
- Create on completion: `doc/task/done/IDEA-06_eink-mode_DONE.md`

- [ ] **Step 1: Integration test DataStore→CompositionLocal→theme**
  Toggle the real pref in test DataStore; assert Theme background changes white/black/white across OFF→ON→OFF and stored dark theme remains unchanged.
- [ ] **Step 2: Instrumentation test on `emulator-5554`**
  Verify tap-zone page turn changes visible LazyList item, center tap preserves it, volume keys route only while Reader visible + E-Ink ON + TTS stopped, and top/bottom UI clears system bar insets.
- [ ] **Step 3: Interruption/lifecycle test**
  Press volume while Reader active, navigate away/recreate Activity, then press volume on another screen; ensure no stale handler consumes it. Start TTS while Reader remains visible; verify volume not consumed. This is the lifecycle/no-leak regression test.
- [ ] **Step 4: Test maintained locales**
  `./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.infrastructure.pref.StringResourceParityTest"`
  Expected: no missing, extra or duplicate resource keys in every maintained locale.
- [ ] **Step 5: Run full unit suite and build**
  `./gradlew testDevDebugUnitTest assembleDevDebug`
  Expected: all tests pass and build succeeds.
- [ ] **Step 6: Run live instrumentation only on locked emulator**
  Avoid generic `connectedDevDebugAndroidTest` when other devices are attached. Use adb serial lock:
  ```bash
  adb -s emulator-5554 install -r "app/build/outputs/apk/dev/debug/RSS Cat Hub-<version>-<hash>.apk"
  adb -s emulator-5554 shell am instrument -w -e class com.mckimquyen.reader.ui.page.home.read.EInkPageTurnInstrumentationTest com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner
  ```
  Inspect screenshot after test; there is no ad in target reader/settings flows, but stop if a full-screen ad appears.
- [ ] **Step 7: QA evidence and completion**
  Record exact results for unit/widget/integration/instrumentation tests, edge-to-edge screenshot bounds, live tap/volume tests, app launch/exit lifecycle. Audit diff and resource lifecycle. Update `doc/feature.md`; move task to `doc/task/done/IDEA-06_eink-mode_DONE.md` with outcome and limitations. Commit locally; push only after separately requested.

### Explicitly Not Included

- Golden screenshot framework (none currently installed; add only if requested after design review).
- Dedicated macrobenchmark/profiling dependency, Perfetto traces, or memory leak library.
- IAP/ad mediation tests (E-Ink mode does not invoke these systems).
- Grayscale video; video intentionally stays color to avoid decode/render regression.
