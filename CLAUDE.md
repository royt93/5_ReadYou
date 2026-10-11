# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

RSS Cat Hub (`com.mckimquyen.reader`) is a single-module Android RSS reader forked from Read You. UI uses Jetpack Compose and Material You; background work uses WorkManager. Android app module is `:app`; Gradle root project is `RSS_Cat_Hub`.

## Build and test

Variants use `dev`/`prod` product flavors and `debug`/`release` build types.

```bash
./gradlew assembleDevDebug
./gradlew assembleProdRelease       # Signing requires keystore.properties
./gradlew lintDevDebug
./gradlew testDevDebugUnitTest
./gradlew testDevDebugUnitTest --tests "com.mckimquyen.reader.domain.filter.SmartFilterEngineTest"
./gradlew connectedDevDebugAndroidTest  # Requires connected Android device/emulator
./gradlew clean
```

Unit tests are under `app/src/test` (JUnit4, MockK, coroutine test, Robolectric); device/UI integration tests are under `app/src/androidTest` (AndroidX test and Compose UI test). Unit tests include Android resources for Robolectric. APK output uses `RSS Cat Hub-<versionName>-<gitCommitHash>.apk`.

Toolchain versions are declared in `build.gradle` and `app/build.gradle`: AGP 8.6.1, Kotlin 2.1.0, Java 17, compile/target SDK 37, min SDK 26. Room and Hilt annotation processing use KAPT (not KSP); keep the KAPT JVM exports in `gradle.properties`. Room schema snapshots live in `app/schemas`. Release signing reads `keystore.properties`; missing file allows Gradle configuration but prevents signing.

## Architecture

Source root: `app/src/main/java/com/mckimquyen/reader/`.

- `domain/` contains persisted models, Room DAOs, and application services. `domain/sv/` services are use cases and orchestration, not UI-facing network providers. `domain/zen/` and `domain/watchdog/` hold reusable feature logic.
- `infrastructure/` implements platform/data integrations: Hilt DI, Room database, DataStore, networking/RSS parsing, audio, Android services, and on-device AI. `ui/` contains the single-activity Compose screens, components, and theme.
- `RssSv.get(accountTypeId)` selects an `AbstractRssRepository` implementation. Backend changes belong behind this contract; UI must not call provider classes directly. `SyncWorker` performs scheduled/background RSS sync through WorkManager and Hilt worker injection.
- `RApp` configures ads, crash handling, WorkManager, and startup tasks (default account, initial sync, update check). WorkManager's default initializer is disabled; `RApp` supplies the Hilt worker factory.
- Preferences are individual DataStore-backed `*Pref` definitions aggregated by `infrastructure/pref/Settings.kt` and exposed to Compose through `SettingsProvider` / `LocalSettings`.
- Locale is a startup constraint: `RApp` and `MainActivity` apply locale in `attachBaseContext()` using `SharedPreferences("locale_prefs")`, key `"languages"`; `LanguagesPref.put()` mirrors DataStore updates there. Preserve this mirror because DataStore context is unavailable during `attachBaseContext()`.
- Ad configuration comes from `BuildConfig` fields in `app/build.gradle`; `RApp.setupAdmob()` selects AdMob or AppLovin through the AdmobWrapper SDK. `sdkadbmob/ComposeBannerAd.kt` wraps the Compose banner.

## Project-specific constraints

- `resourceConfigurations` in `app/build.gradle` must match maintained `app/src/main/res/values-*` locale qualifiers, or translations can be stripped from the APK. Update both when changing shipped locales; `translate_strings.py` helps with string translation.
- See `doc/AD.MD` for ad migration context and `doc/task/` for feature/change notes. `README.md` describes the upstream fork and user-facing RSS features.
