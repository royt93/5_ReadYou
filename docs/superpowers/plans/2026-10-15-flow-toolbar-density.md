# Flow Toolbar Density Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce perceived action-icon crowding in Flow top bar while preserving visible Daily Digest and Reels shortcuts and all current actions.

**Architecture:** Keep Back in `navigationIcon`; keep Daily Digest, Reels, Search and More visible in `actions`. Move Mark-all, CommuteCast, Brain RPG and Watchdog into a single labeled Material3 dropdown. Preserve callbacks and visibility gates.

**Tech Stack:** Kotlin, Jetpack Compose Material3 `DropdownMenu`/`DropdownMenuItem`, `FeedbackIconButton`, Robolectric Compose tests, Android instrumentation.

## Global Constraints

- Keep Back, Daily Digest, Reels, More (`Icons.Rounded.MoreVert`), and Search visible in toolbar.
- Put four auxiliary actions into one labeled/icon dropdown menu: Mark all as read, CommuteCast, Brain RPG, Watchdog.
- Preserve existing callback behavior and `launchSingleTop` navigation for moved actions.
- Mark-all is available only when `!filterUiState.filter.isStarred()`; toolbar actions remain hidden when `filterUiState.filter.isAddSources()`.
- Menu item tap closes menu before invoking its callback.
- Keep default `FeedbackIconButton` touch targets; do not shrink icon hit areas.
- Pixel smoke target is `2B051FDH3006MU`; never use S24 Ultra `R5CX613VZBR`; no `connected*`; on fullscreen ad stop and wait for `done`.
- Preserve uncommitted INGEST-01 changes; do not stage unrelated files or push unless this toolbar task separately scores >9/10 and all gates pass.

## Current Code Map

- Toolbar and callbacks: `app/src/main/java/com/mckimquyen/reader/ui/page/home/flow/FlowPage.kt:128-191`.
- Top-level layout: `app/src/main/java/com/mckimquyen/reader/ui/component/base/BaseScaffold.kt:24-56`; do not change.
- Touch target wrapper: `app/src/main/java/com/mckimquyen/reader/ui/component/base/FeedbackIconButton.kt:24-70`; default target 48dp.
- Existing M3 menu pattern: `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/TopBar.kt:135-232`.
- Robolectric Compose test pattern: `app/src/test/java/com/mckimquyen/reader/ui/page/home/feed/FeedOptionViewNotificationWidgetTest.kt`.

## Files

- Create `app/src/main/java/com/mckimquyen/reader/ui/page/home/flow/FlowActionsMenu.kt`: overflow menu only, externally controlled `expanded`, nullable Mark-all callback, four item callbacks.
- Modify `app/src/main/java/com/mckimquyen/reader/ui/page/home/flow/FlowPage.kt`: More expanded state, direct Digest/Reels/Search, moved action callbacks.
- Create `app/src/test/java/com/mckimquyen/reader/ui/page/home/flow/FlowActionsMenuWidgetTest.kt`: menu item visibility, callback dispatch and dismissal.
- No strings required: existing action labels and `more` exist in resources.

## Task 1: Overflow Menu Component

**Interface:**
```kotlin
@Composable
internal fun FlowActionsMenu(
    expanded: Boolean,
    showMarkAllAsRead: Boolean,
    onDismiss: () -> Unit,
    onMarkAllAsRead: (() -> Unit)?,
    onCommuteCast: () -> Unit,
    onBrainRpg: () -> Unit,
    onWatchdog: () -> Unit,
)
```

- [ ] Write widget test for More trigger opening four overflow items and optional Mark-all visibility.
- [ ] Write widget test that each item invokes exactly one callback and closes menu.
- [ ] RED: `./gradlew --no-daemon --max-workers=1 testDevDebugUnitTest --tests "*FlowActionsMenuWidgetTest"`; failure is missing component/API.
- [ ] Implement Material3 `DropdownMenu`/`DropdownMenuItem`, labels from existing `R.string.*`, icons with null decorative descriptions; `onClick` invokes `onDismiss()` before the action callback.
- [ ] GREEN targeted widget test.

## Task 2: Flow Toolbar Wiring

- [ ] Add a focused widget test asserting More and Search coexist and Back remains visible when represented by a narrow toolbar harness; if full `FlowPage` Hilt/nav wiring makes this impractical, test the action row extracted as private/internal composable with a fixed 320dp width.
- [ ] RED before production wiring.
- [ ] In `FlowPage.kt`, preserve Back callback unchanged; keep Daily Digest, Reels and Search direct; put Mark-all, CommuteCast, Brain RPG, Watchdog callbacks in menu; More toggles `actionsExpanded`.
- [ ] For starred filter, pass null Mark-all callback. For AddSources filter, render neither More nor Search, matching prior gate.
- [ ] Mark-all callback: scroll to top if needed, toggle `markAsRead`, set `onSearch=false`. Digest/Reels/RPG navigation retains `launchSingleTop`; CommuteCast/Watchdog keep existing calls.
- [ ] GREEN focused toolbar tests.

## Task 3: Verification and Pixel Smoke

- [ ] Run `./gradlew --no-daemon --max-workers=1 testDevDebugUnitTest --tests "*FlowActionsMenuWidgetTest"`.
- [ ] Run full `./gradlew --no-daemon --max-workers=1 testDevDebugUnitTest`.
- [ ] Run `./gradlew lintDevDebug assembleDevDebug assembleDevDebugAndroidTest`.
- [ ] Confirm devices with `adb devices`; target only Pixel `2B051FDH3006MU`, never S24 Ultra `R5CX613VZBR`; use explicit `adb -s 2B051FDH3006MU`; no `connected*`.
- [ ] Install latest dev APK on Pixel; open Flow, capture toolbar, open More, capture menu; select one overflow action (e.g. CommuteCast/Watchdog) and confirm existing destination/sheet opens; verify Digest/Reels direct shortcuts still visible.
- [ ] If full-screen ad appears, stop immediately and wait for user's `done`.
- [ ] `git diff --check`; inspect changed-path status and preserve all INGEST-01 files. Commit toolbar-only paths after all gates; no push unless score >9/10 and all gates pass.
