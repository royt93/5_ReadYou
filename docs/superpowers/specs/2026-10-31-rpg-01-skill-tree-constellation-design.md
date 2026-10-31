# RPG-01 — Skill Tree Constellation Design

- **Feature:** Brain RPG visual skill-tree refresh
- **Date:** 2026-10-31
- **Branch:** `dev`
- **Status:** Owner-approved design; implementation not started.

## Context and verified current state
`BrainRpgPage` already displays `KnowledgeSkillTreeCard`, which lists the six existing categories as flat XP/progress rows. RPG reward, category decay, Room persistence, and recovery behavior already exist (RPG-05 through RPG-09). The remaining RPG-01 gap is visual: there is no skill-tree graph. Keep all progression/reward rules unchanged and replace only the flat category card.

The RPG-01 original spec is stale about SharedPreferences, XP thresholds, and 75% reading completion; RPG-07 migrated profile/category/reward data to Room and established the actual 80%-of-Reader-items + 30-second reward condition. This design does not change either behavior.

## Owner decisions (2026-10-31)
- Replace the existing six-row `KnowledgeSkillTreeCard` directly; do not add a second screen or a view-mode toggle.
- Use a **constellation graph**, not a hierarchical unlock tree or radial RPG wheel.
- Display six existing category nodes in a fixed asymmetric 3-column/two-row layout. Positions must not move as XP changes.
- Node size and glow/outline emphasis reflect **effective category XP**. A 0-XP node remains visible and tappable at a minimum accessible touch target.
- Thin connector lines link each node to one or two fixed neighboring nodes. Connectors are decorative relationships only; they do not imply prerequisites or unlock dependencies.
- Decayed categories display a dimmed node, small amber/red warning marker, and retained-XP percent. Tapping any node opens a detail bottom sheet.
- Detail sheet shows category icon/name, effective XP, base XP, progress bar, and current existing recovery action for decayed categories. Keep rewarded-ad callback, eligibility, and Room state transitions unchanged.
- No new categories, XP rules, unlocks, skill-node persistence, schema migration, network/API, or third-party dependency.

## UI and data mapping
Use existing IDs and colors/icons for:
- `Tech & AI`
- `Business & Finance`
- `Science & Nature`
- `Health & Lifestyle`
- `Philosophy & Ideas`
- `General Knowledge`

For each category:
- `baseXp = progress.categoryBaseXp[id] ?: 0L`
- `effectiveXp = progress.categoryXp[id] ?: 0L`
- `retainedPercent = if (baseXp > 0) floor(effectiveXp * 100 / baseXp) else 100`
- `isDecayed = baseXp > effectiveXp`

Node visual scale uses a bounded linear mapping from effective XP relative to the current highest effective XP; clamp the minimum rendered node diameter so a 0-XP category remains visible. XP affects only size/brightness, never node position. The highest-XP node receives the strongest outline/glow; do not introduce animation or a new chart library.

The constellation card remains part of the existing vertically scrolling `BrainRpgPage`. A node click opens a Material 3 modal bottom sheet. Existing `recoveringCategoryId` state remains page-owned; only the currently decayed selected node shows the existing `onRecover(categoryId)` action. Un-decayed nodes have no recovery action.

## Accessibility and localization
- Each node is a semantic button with localized category name, effective XP, and retained percent when decayed.
- Do not rely on glow/color alone: decay marker has an icon and localized text/description.
- Sheet provides localized title, effective/base XP values, and recovery action text.
- Add the same key set to all 38 maintained `values*/strings.xml` folders; EN and VI manual, remaining folders use English fallback unless owner requests translation.

## Tests
- Pure layout/model unit tests: exactly six stable category IDs; fixed positions regardless of XP changes; node-size bounds and monotonicity; zero-XP minimum; base/effective XP mapping; retained-percent edge cases (base 0, equal, decayed); fixed neighbor connector edges; connector endpoints reference existing nodes only.
- Compose tests: graph displays six nodes and connectors; a node tap opens its detail sheet; effective/base XP and retained percentage are shown; decay marker appears only for decayed nodes; recovery callback fires once for the selected decayed category and stays absent for non-decayed nodes.
- Preserve existing RPG XP/streak/recovery tests; no changes to `BrainRpgRepository` behavior.
- Run `testDevDebugUnitTest --rerun`, `lintDevDebug`, `assembleDevDebug`, and `assembleDevDebugAndroidTest`.
- Pixel-only smoke on currently authorized target only after `adb devices`; back up Room before device changes, inspect foreground before each UI interaction, stop on interstitial under R4, and restore/verify Room. No fixture data should remain.

## Non-goals and known limitation
This is a **visual constellation of six existing XP categories**, not a branching dependency tree. It does not claim skill prerequisites, unlock states, AI classification, or new game progression. The current quiz category detector remains heuristic and most categories can remain sparse; the visual must show zero/low XP honestly rather than inventing skills.
