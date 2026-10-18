# [IDEA-06] E-Ink Mode Design Specification

- **Status:** Approved
- **Date:** 2026-10-18
- **Priority:** P3 (Low) — 3 Story Points
- **Epic:** 04. IDEAS — Ý Tưởng Trải Nghiệm & Tăng Trưởng

---

## 1. Problem & Goal

E-Ink displays (Onyx Boox, Meebook, Xiaomi InkPalm) have low refresh rates, grey halftones and ghosting artifacts when smooth scrolling occurs. This feature provides a dedicated E-Ink optimisation mode that:

1. Forces a **high-contrast monochrome UI**: large surfaces/backgrounds map to pure white, primary text/buttons map to pure black/white; secondary text and dividers may use neutral grays for legibility (not every Material color role is forced to binary black/white).
2. Applies a **grayscale color filter** to static images and thumbnails (video keeps color).
3. Disables **app-level animations** (navigation, content transitions, animated reveals) to reduce refresh ghosting.
4. Enables **tap-zone page turning** (left-third = previous, right-third = next, center = default scroll/select) and **volume key page turning** (Down = prev, Up = next) in the Reader screen when TTS is not active.

---

## 2. Accepted Constraints

| Decision | Rationale |
|---|---|
| Default **OFF** | No unintended behavior change for non-E-Ink users. |
| Entry point: **Color & Style** settings only | No auto-detection (no reliable Android API). |
| Theme: **force light monochrome** | Pure white background / black text; user's dark theme and palette preference are temporarily bypassed, not overwritten. Restored when mode is disabled. |
| Image filter: **grayscale for static images and thumbnails; video unchanged** | Avoids codec-level rendering overhead and frame jank on video. |
| Animation: **app-authored animations disabled; system animation scale untouched** | Avoids accessibility side-effects for other users. |
| Page-turn volume: **Reader only, when TTS is not active** | Prevents conflict with TTS and media volume controls. |
| Page-turn center zone: **default scroll/select** | Prevents accidental page turns when tapping content. |

---

## 3. Preference

**`EInkModePref`** — sealed class following existing pattern (e.g. `ReadPositionSyncPref`).

- `object ON : EInkModePref(true)`
- `object OFF : EInkModePref(false)` ← **default**
- DataStore key: `booleanPreferencesKey("eInkMode")`
- `not()` operator for toggle.

Registered in:
- `DataStoreKeys` (`ExtDataStore.kt`)
- `Settings` data class
- `SettingsProvider` CompositionLocalProvider
- `LocalEInkMode` compositionLocalOf in `Settings.kt`
- `Settings.toSettings()` mapping

---

## 4. Theme Override

When `LocalEInkMode.current.value == true`, `AppTheme` (in `Theme.kt`) overrides the `colorScheme` argument with a statically-built monochrome light scheme:

```kotlin
val eInkColorScheme = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E0E0),
    onPrimaryContainer = Color.Black,
    inversePrimary = Color(0xFF404040),
    secondary = Color(0xFF404040),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDDDDD),
    onSecondaryContainer = Color.Black,
    tertiary = Color(0xFF606060),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE8E8E8),
    onTertiaryContainer = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF0F0F0),
    onSurfaceVariant = Color(0xFF303030),
    surfaceTint = Color.Black,
    inverseSurface = Color.Black,
    inverseOnSurface = Color.White,
    outline = Color(0xFF505050),
    outlineVariant = Color(0xFF707070),
    error = Color.Black,
    onError = Color.White,
    errorContainer = Color(0xFFEEEEEE),
    onErrorContainer = Color.Black,
)
```

This palette is passed instead of `dynamicLightColorScheme()` / `dynamicDarkColorScheme()` when E-Ink mode is active. User preferences for dark theme and color palette are **not overwritten**; the override is purely a runtime Compose composition branch.

---

## 5. Grayscale Image Filter

`BaseAsyncImage.kt` gains an optional `grayscale: Boolean = false` parameter. When `true`, the Image composable applies a `ColorFilter` built from a `ColorMatrix` with saturation 0:

```kotlin
val grayscaleFilter = ColorFilter.colorMatrix(
    ColorMatrix().apply { setToSaturation(0f) }
)
```

`LocalEInkMode.current.value` is read in `BaseAsyncImage` to set `grayscale` automatically. Video players are excluded (the `ArticleVideoPlayer` and `iframe`/video composables in `HtmlToComposable.kt` do not receive this filter).

---

## 6. App-Authored Animation Suppression

A `LocalEInkMode` guard wraps:

1. **Navigation transition** — `animatedComposable` defined in `app/src/main/java/com/mckimquyen/reader/ui/ext/ExtNavGraphBuilder.kt` uses `EnterTransition.None` / `ExitTransition.None` for enter/exit and pop enter/exit when mode is on. The existing route/content builder API stays unchanged.
2. **`AnimatedContent` in `ReadingPage.kt`** — select no-op `EnterTransition.None togetherWith ExitTransition.None` when mode is on; keep existing spring/slide/fade transition when off.
3. For app-owned `AnimatedVisibility` encountered in these navigation/Reader flows, use no-op enter/exit when mode is on. Do not perform broad unrelated rewrites of settings/card animations; record remaining app animations as a known limitation unless they are on a common navigation path.

System `WindowManager.LayoutParams.ANIMATION_*` and accessibility scale are **not touched**.

---

## 7. Page-Turn in Reader

### 7.1 Tap Zones

In `ReadingPage.kt`, when `LocalEInkMode.current.value && readingUiState.articleWithFeed != null`:

- Add `pointerInput(eInkEnabled, articleId)` handling on the reader content region. Use `PointerInputChange.position.x` and `size.width` to identify left/middle/right thirds.
- Left-third tap → previous page-turn; right-third tap → next page-turn; middle third → consume no action and preserve existing child click/text-selection/scroll behavior.
- Prefer a non-consuming preview handler or a parent tap detector that only consumes edge-zone taps; test that links, text selection, video controls and RSVP interactions in the center remain functional. Only enable edge-zone interception while E-Ink mode is active and an article is loaded.

### 7.2 Scroll Amounts

Implement page turn using measured `LazyListState.layoutInfo` rather than assuming the current item offset is a global scroll coordinate. A robust initial implementation can use `scrollToItem(targetIndex, targetOffset)`:

- Next: target `(firstVisibleItemIndex + visibleItemsInfo.size - 1).coerceAtMost(totalItemsCount - 1)` with current last-visible item offset, so content advances by approximately one viewport and keeps overlap.
- Previous: target `max(firstVisibleItemIndex - max(visibleItemsInfo.size - 1, 1), 0)` with offset `0`.
- Empty/unmeasured list → no-op. Clamp at first/last item. Use immediate `scrollToItem` (no animation). Unit/Compose tests must assert direction, approximate viewport advance, overlap, and boundary behavior.
- Keep page-turn calculation in a pure helper accepting a list layout snapshot/indices so it can be tested without hardware.

Clamp at first and last item; no reposition/alignment to paragraph boundary (YAGNI).

### 7.3 Volume Key Page Turn

Volume keys may bypass Compose dispatch when no Compose node owns focus, and Android may route them to system volume handling. Therefore implementation must be selected by a real instrumentation test, not assumed:

1. First try a focus-safe Reader root key handler only if it receives hardware volume events without stealing focus from article links, text selection or controls.
2. If not, add a narrowly-scoped Activity key-dispatch bridge registered only while Reader + E-Ink mode + TTS stopped; it must delegate every non-volume, repeat, off-mode or non-Reader key to the prior/default dispatcher and restore/unregister on disposal. Do not mutate system animation settings or leave a global listener alive.

When conditions are met, `KEYCODE_VOLUME_DOWN` → previous page; `KEYCODE_VOLUME_UP` → next page. Handle only key-down once (`repeatCount == 0`); let key-up and held repeats pass through. When TTS plays or Reader leaves, all volume keys return to existing Android/TTS/media behavior. Instrumentation must verify physical-key dispatch and restoration after navigate-away / Activity recreation before choosing the Compose-only path.

---

## 8. Settings UI

In `ColorAndStylePage.kt`, inside the "Appearance" section:

```
SettingItem(
    title = stringResource(R.string.eink_mode_title),
    desc = stringResource(R.string.eink_mode_desc),
    separatedActions = true,
    onClick = { eInkModeNot.put(context, scope) },
) {
    BaseSwitch(activated = eInkMode.value) { eInkModeNot.put(context, scope) }
}
```

---

## 9. Localization

Strings required in `values/strings.xml` (and all maintained locale files):

| Key | English value |
|---|---|
| `eink_mode_title` | E-Ink Optimisation |
| `eink_mode_desc` | Monochrome display, no animations, tap sides or volume keys to turn pages |

String parity enforced by existing `StringResourceParityTest`.

---

## 10. Testing Plan

### Unit Tests (`app/src/test/`)

- `EInkModePrefTest` — default value is OFF; `not()` toggles correctly; `fromPreferences` round-trips.
- `EInkPageTurnTest` — scroll amount = viewport height; clamp at 0 and last item; center zone no-ops.
- `EInkVolumeConditionTest` — handler registered when E-Ink on + TTS off; removed when TTS starts or mode disabled.

### Widget/Compose Tests (`app/src/test/` via Robolectric)

- `EInkThemeTest` — when mode ON, `MaterialTheme.colorScheme.background == Color.White && onBackground == Color.Black`.
- `BaseAsyncImageGrayscaleTest` — ColorFilter contains saturation-0 matrix when `grayscale = true`.
- `EInkSettingToggleTest` — toggle row visible in ColorAndStylePage; switch state reflects pref.

### Integration

- `EInkModeEndToEndTest` (Robolectric in-memory Room) — enabling E-Ink pref in DataStore and verifying CompositionLocal propagates. No reader navigation in Robolectric (Hilt ViewModel); scoped to pref read/write and color scheme lookup.

### Instrumentation (`app/src/androidTest/` on emulator-5554)

- `EInkEdgeToEdgeInsetsTest` — verify ColorAndStylePage title clears status bar when E-Ink mode active.
- `EInkPageTurnTapTest` — simulate tap on left-third of Reader; assert `LazyListState.firstVisibleItemIndex` changes.

### Not in scope

- Profiling/memory/jank (no benchmark infrastructure).
- Golden screenshots (no Paparazzi/Shot in project).
- IAP / ad mediation (not invoked by this feature).
