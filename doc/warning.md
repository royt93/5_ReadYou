# Kotlin Compiler Warnings — Final Report

> Command: `./gradlew clean :app:compileProdDebugKotlin --warning-mode all`
> Self-test: **3 builds completed (clean → incremental → incremental)**
> **Total warnings: 0 ✅ | Errors: 0 ✅**

---

## Before vs After

| Metric | Before | After |
|--------|--------|-------|
| Kotlin compiler warnings | **~60+** (incremental), **15+** (clean) | **0** |
| Compiler errors | 0 | 0 |
| Build status | SUCCESSFUL | ✅ SUCCESSFUL |

---

## Self-Test Results

| Run | Type | Result | Warnings | Errors |
|-----|------|--------|----------|--------|
| 1 | `clean` build | ✅ BUILD SUCCESSFUL (21s) | 0 | 0 |
| 2 | incremental | ✅ BUILD SUCCESSFUL (1s) | 0 | 0 |
| 3 | incremental | ✅ BUILD SUCCESSFUL (558ms) | 0 | 0 |

---

## Fix Summary

### Group 1: Unused Variables / Parameters (26 warnings)
**Strategy**: Remove unused vars; for params still passed by callers, add `@Suppress("UNUSED_PARAMETER")`

| File | Fix |
|------|-----|
| `ui/page/home/feed/FeedsPage.kt` | Removed `accountTabVisible`, `scope`, `accounts`, `owner`; restored missing imports |
| `ui/page/home/feed/FeedsViewModel.kt` | Added `@OptIn(ExperimentalCoroutinesApi::class)` on class |
| `ui/page/home/read/Content.kt` | Removed `isShowToolBar` param; updated `ReadingPage.kt` caller |
| `ui/page/home/read/TopBar.kt` | Removed `tonalElevation` variable |
| `ui/page/home/read/ReadingPage.kt` | Removed `isShowToolBar` named arg from `Content()` call |
| `ui/page/home/read/drawer/FeedOptionDrawer.kt` | Removed `context`, `view`, `feed`, `toastString` |
| `ui/page/setting/acc/AccountsPage.kt` | Removed `uiState` |
| `ui/page/setting/acc/AddAccountsPage.kt` | Removed `viewModel` param and `context` var |
| `ui/page/setting/color/feed/FeedsPagePreview.kt` | Removed `topBarTonalElevation` param; updated `FeedsPageStylePage.kt` caller |
| `ui/page/setting/color/flow/FlowPagePreview.kt` | Removed `topBarTonalElevation` param; updated `FlowPageStylePage.kt` caller |
| `ui/page/setting/color/reading/ReadingImagePage.kt` | Removed `readingTheme` |
| `ui/page/setting/color/reading/ReadingTextPage.kt` | Removed `readingTheme` |
| `ui/page/setting/color/reading/ReadingVideoPage.kt` | Removed `context`, `scope` |
| `ui/page/setting/color/reading/TitleAndTextPreview.kt` | Removed `subtitleBold`, `subtitleAlign` |
| `ui/component/base/BaseAsyncImage.kt` | Added `@Suppress("UNUSED_PARAMETER")` — `size`, `scale`, `precision` kept because callers pass them |
| `ui/component/reader/Reader.kt` | Removed `context` param; updated `Content.kt` caller |
| `infrastructure/pref/ReadingThemePref.kt` | Added `@Suppress("UNUSED_PARAMETER")` on `toDesc(context)` |
| `infrastructure/pref/*Pref.kt` (27 files) | Batch added `@Suppress("UNUSED_PARAMETER")` to all `toDesc(context)` functions via `sed` |

### Group 2: ExperimentalCoroutinesApi Opt-In (2 + 1 warnings)
| File | Fix |
|------|-----|
| `ui/page/home/feed/FeedsViewModel.kt` | `@OptIn(ExperimentalCoroutinesApi::class)` on class |
| `domain/sv/AbstractRssRepository.kt` | `@OptIn(ExperimentalCoroutinesApi::class)` on `pullImportant()` + `@Suppress("UNUSED_VARIABLE")` on `preTime` |

### Group 3: Deprecated Java APIs — systemUiVisibility (27 warnings)
**Strategy**: Move `@Suppress("DEPRECATION")` from local `val` to **function level** (required to cover lambda closures)

| File | Fix |
|------|-----|
| `ui/ext/Activity.kt` | Added `@file:Suppress("DEPRECATION")` + `@Suppress("DEPRECATION")` on each function: `setChangeStatusBarTintToDark`, `showStatusBar`, `hideStatusBar`, `toggleFullscreen` (both overloads), `hideNavigationBar`, `showNavigationBar`, `hideDefaultControls`, `showDefaultControls`; modernized `getScreenHeightIncludeNavigationBar()` to use `WindowMetrics` on API 30+ |

### Group 4: Deprecated WebView APIs (3 warnings)
| File | Fix |
|------|-----|
| `ui/component/base/WebView.kt` | Added `@file:Suppress("DEPRECATION", "OverridingDeprecatedMember")` + `@Deprecated("Deprecated in Java")` on `shouldInterceptRequest` override |

### Group 5: Deprecated Network API (1 warning)
| File | Fix |
|------|-----|
| `sdkadbmob/AdMobManager.kt` | Moved `@Suppress("DEPRECATION")` from local `val` to `isDeviceConnected()` function level |

### Group 6: Room Migration Parameter Name Mismatch (5 warnings)
| File | Fix |
|------|-----|
| `infrastructure/db/AndroidDb.kt` | Renamed all `migrate(database: ...)` to `migrate(db: ...)` to match Room's `Migration` supertype |

### Group 7: Gradle Plugin Deprecation (not fixed — not Kotlin source)
> `org.gradle.api.plugins.Convention` — Gradle 9.0 scheduled removal. Requires Gradle plugin upgrade.

---

## Notes

- **`@file:Suppress` alone is insufficient** for Java-originated deprecated flags (e.g. `SYSTEM_UI_FLAG_*`). Function-level `@Suppress` is required.
- **Incremental builds** can hide warnings from unchanged files. Always verify with `./gradlew clean` for a true count.
- **Batch sed** was used to fix 27 Pref files in one pass. Only `ReadingThemePref.kt` had a duplicate annotation (previously manually fixed) which was cleaned up.
