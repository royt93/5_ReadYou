# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**RSS Cat Hub** (`rootProject.name = "RSS_Cat_Hub"`, app namespace `com.mckimquyen.reader`) is an Android RSS reader, a fork of [Read You](https://github.com/ReadYouApp/ReadYou). It is built with Jetpack Compose + Material You, follows a **Single-Activity architecture**, and is monetized via ads (AdMob / AppLovin MAX).

## Build & Run

The project uses two flavor dimensions × build types, producing four variants:
- `channel` dimension: **`dev`** and **`prod`** flavors
- build types: **`debug`** and **`release`**
- → variants: `devDebug`, `devRelease`, `prodDebug`, `prodRelease`

```bash
# Build a debug APK (dev flavor)
./gradlew assembleDevDebug

# Build the production release APK (signed; requires keystore.properties)
./gradlew assembleProdRelease

# Lint
./gradlew lintDevDebug

# Clean
./gradlew clean

# Install onto a connected device/emulator
./gradlew installDevDebug

# Run unit tests (JVM, Robolectric) — dev flavor
./gradlew testDevDebugUnitTest

# Run a single unit test class
./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.sv.SomeClassTest"

# Run instrumented tests (needs connected device/emulator) — dev flavor
./gradlew connectedDevDebugAndroidTest
```

Output APK names are templated as `RSS Cat Hub-<versionName>-<gitCommitHash>.apk`.

Unit tests live under `app/src/test`, instrumented tests under `app/src/androidTest`, mirroring the `domain`/`infrastructure`/`ui` package layout below. Stack: JUnit4, MockK, `kotlinx-coroutines-test`, Robolectric, and Compose `ui-test-junit4` for composable tests.

### Toolchain / important gotchas
- AGP **8.6.1**, Kotlin **2.1.0**, KSP `2.1.0-1.0.29`, Java/JVM target **17**, `compileSdk`/`targetSdk` **36**, `minSdk` **26**.
- `gradle.properties` carries mandatory `--add-exports jdk.compiler/...` JVM args on both `org.gradle.jvmargs` and `kotlin.daemon.jvmargs`. These exist to keep **KAPT** working on newer JDKs — do not remove them or KAPT stub generation will crash. The repo has historically hit KAPT/Kotlin-metadata version conflicts (see `doc/AD.MD`).
- **Annotation processing uses KAPT, not KSP**, for both Room (`room-compiler`) and Hilt (`hilt-android-compiler`, `hilt-compiler`), plus an explicit `kotlin-metadata-jvm` kapt dependency to resolve metadata conflicts pulled in by the ad wrapper.
- Room schemas are exported to `app/schemas`.
- `resourceConfigurations` is pinned to `['en','vi','zh-rCN','ja','fr','de']` to limit APK size — adding a new translation language requires updating this list. `translate_strings.py` (repo root) assists with string i18n.
- Release signing reads `keystore.properties` (gitignored; see `keystore.properties.example`). If the file is absent the build still configures but cannot sign release.

## Architecture

Source lives under `app/src/main/java/com/mckimquyen/reader/`, split into three layers:

- **`domain/`** — business layer.
  - `model/` — data classes / entities (`account`, `feed`, `article`, `group`, `addedsource`, `general`, `constant`, `cluster`, `commute`, `rpg`, `watchdog`).
  - `repository/` — Room **DAOs** (`AccountDao`, `FeedDao`, `ArticleDao`, `GroupDao`, `AddedRssSourceDao`) plus `RssSourceRepository`.
  - `sv/` — **services** (the app's term for use-case/repository logic): `AccountSv`, `AppSv`, `OpmlSv`, `RssSv`, and RSS backend implementations.
  - `zen/`, `watchdog/` — standalone domain logic for the Zen (focus/reading mode) and Watchdog (feed/article monitoring) features.
- **`infrastructure/`** — platform glue.
  - `di/` — Hilt modules (`DbModule`, `OkHttpClientModule`, `RetrofitModule`, `ImageLoaderModule`, `WorkerModule`, dispatcher/scope qualifiers).
  - `db/` — `AndroidDb.kt` (Room `@Database`).
  - `net/`, `rss/` (RSS/OPML parsing, favicons, per-backend providers under `rss/provider/{fever,googleReader}`), `audio/` (read-aloud / TTS), `pref/` (DataStore preferences), `android/` (`MainActivity`, helpers, crash handler), `ai/` (on-device AI: article deduplication/clustering, semantic search, deep-read Q&A, mind map — see recent `feat(ai): ...` commits), `watchdog/` — platform side of the Watchdog feature.
- **`ui/`** — Compose UI: `page/` (feature screens: `home/{feed,flow,read,addsources}`, `setting/{zen,vip,...}`, `startup/`, `about/`, `common/`, `rpg/`, `rsvp/`), `component/` (incl. `cluster/`, `commute/`, `rpg/`), `theme/`, `svg/`, `ext/`.

### RSS backend abstraction
`RssSv` is a runtime **dispatcher**: `RssSv.get(accountTypeId)` returns the concrete `AbstractRssRepository` subclass for the current account (`LocalRssSv`, `FeverRssSv`; Google Reader/Inoreader/Feedly currently fall back to `LocalRssSv`). `AbstractRssRepository` defines the common contract (subscribe/move/delete/update, sync) and capability flags. **To add or change a backend, implement against `AbstractRssRepository` and wire it into `RssSv.get()` — do not call provider classes directly from UI.** Sync runs through `SyncWorker` (WorkManager + Hilt worker factory).

### Application startup (`RApp.kt`)
`@HiltAndroidApp` Application that also implements `WorkConfiguration.Provider` and `ImageLoaderFactory`. `onCreate()` → `setupAdmob()`, install `CrashHandler`, then on `applicationScope`: create a default account if none, run an initial sync, and check for app updates (skipped for F-Droid builds). WorkManager's default initializer is disabled in the manifest; the `HiltWorkerFactory` is provided here instead.

### Locale handling (important pattern)
Locale is applied in `attachBaseContext()` of **both** `RApp` and `MainActivity` by reading `SharedPreferences("locale_prefs")` key `"languages"` — **not** DataStore, because the DataStore delegate touches `applicationContext` which is null during `attachBaseContext`. `LanguagesPref.put()` mirrors the chosen language into that SharedPreferences so both sources stay in sync. When touching language/locale code, preserve this DataStore↔SharedPreferences mirror.

### Settings / preferences
All settings are DataStore-backed. `infrastructure/pref/Settings.kt` aggregates every individual `*Pref.kt` into a single `Settings` data class exposed through a Compose `CompositionLocal` (`SettingsProvider`); read values in composables via `LocalSettings`. Each preference is its own file implementing a sealed/value pattern with a `default` and persistence helpers. Add a new setting by creating a `*Pref.kt` and registering it in `Settings.kt` + `SettingsProvider`.

### Ads
Ad integration goes through the external **AdmobWrapper** SDK (`com.github.royt93:AdmobWrapper`, package `com.roy.sdkadbmob`, exposing `AdManager` / `AdSdkConfig`). `RApp.setupAdmob()` builds `AdSdkConfig` from `BuildConfig` fields and selects the provider via `BuildConfig.IS_ENABLE_ADMOB` (`true` = AdMob, `false` = AppLovin MAX). All ad unit IDs and the enable flag are defined as `buildConfigField`s per build type in `app/build.gradle`. App-open ads are wired through `registerAppOpenAdLifecycle`. The local `sdkadbmob/ComposeBannerAd.kt` is the Compose banner wrapper. The AMOLED dark theme is an ad-gated "unlock" feature (`AmoledUnlockedPref` / `AmoledDarkThemePref`). Background and history of the ad migration is documented in `doc/AD.MD`.

## Notes
- `doc/` holds working notes (in Vietnamese): `AD.MD` / `AD_PROMPT_AOS.MD` (ad SDK migration), `memory_leak.md`, `quick_win.md`, `warning.md`, `init.md`, plus `task/` and `test/` subfolders. The numerous `*_err*.txt` / `compile_err*.txt` / `build_output.log` files at the repo root are stale build-log artifacts, not source.
- App restart uses `ProcessPhoenix`; in-app review uses Play `review-ktx`.
