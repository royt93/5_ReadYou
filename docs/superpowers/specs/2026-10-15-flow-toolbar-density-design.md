# Flow Toolbar Density Design

## Context

Tecno screenshot shows the Flow top bar squeezed: seven action slots plus Back compress the Back control to about 16dp and make the Back glyph visually collide with first action. This is a visual and hit-target problem in `ui/page/home/flow/FlowPage.kt`, where all actions are placed in `BaseScaffold`/Material `TopAppBar` actions.

## Design

Keep Back, Daily Digest, Reels, More (`Icons.Rounded.MoreVert`), and Search visible in toolbar. Put four auxiliary actions into one labeled/icon dropdown menu: Mark all as read, CommuteCast, Brain RPG, Watchdog. Preserve each existing action callback and state behavior exactly. Daily Digest/Reels retain their current `launchSingleTop` navigation. Mark-all menu item only appears under the same condition as the old action (`!filterUiState.filter.isStarred()`); all menu/actions retain the current `!filterUiState.filter.isAddSources()` gate.

Menu item tap closes menu before invoking action. Mark-all preserves existing scroll-to-top, mark toggle, and search dismissal. Navigation and sheet actions keep their current `launchSingleTop`/open behavior. Use Material3 `DropdownMenu` and `DropdownMenuItem` already available in project; no dependency.

## Accessibility and layout

- Back, Daily Digest, Reels, Search, More, and all dropdown items have localized content descriptions/visible strings.
- Keep standard Material touch targets; don't shrink `FeedbackIconButton` hit areas.
- Toolbar must show Back/Daily Digest/Reels/More/Search with no overlap at Pixel viewport; menu items must be independently clickable and keyboard/screen-reader labeled.

## Testing

- Add Compose widget test for new action menu: three toolbar controls remain visible, More opens six labeled actions (conditional Mark-all visibility), each selection invokes its expected callback once and closes menu.
- Add test with starred and AddSources filters to confirm old visibility gates.
- Re-run Compose tests, full unit suite, lint and app/androidTest builds.
- Pixel smoke only on `2B051FDH3006MU`; never use S24 Ultra. Capture top bar and menu. If a full-screen ad appears, stop immediately and wait for `done`.

## Non-goals

No change to article list/search navigation, action semantics, global `BaseScaffold`, screen widths, or other toolbars.
