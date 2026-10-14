# EXC-09: Original Source DNA

**Status:** Approved design; implementation plan pending.
**Date:** 2026-10-14

## Goal

Add an on-device, inspectable timeline graph to each eligible `StoryCluster`, showing the earliest feed-reported article and later articles with high lexical similarity to a preceding article. UI must present this as a heuristic, never verified provenance or proof of copying.

## User experience

- Add a **“🧬 Source DNA”** action to `StoryClusterSheet` only when `cluster.sourceCount >= 3`.
- Action reveals a vertically scrollable, time-ordered directed graph. Each node contains feed name, article title, feed-reported publish time, heuristic relation label, and similarity percentage where applicable.
- Labels: **“Earliest posted in this cluster”** for an unambiguous earliest item; **“High similarity to [parent feed]”** for a node connected to an earlier item whose similarity meets the threshold; otherwise **“No clear source relationship”**.
- Include a persistent note: **“Heuristic based on feed timestamps and text similarity; not verified source attribution or proof of copying.”** Localize all user-facing strings in maintained locales.
- Tapping a node opens its article through the existing `onArticleClick` path and closes the sheet.
- If no unique earliest item exists, do not mark any node as earliest. If no eligible similarity edge exists, show an explicit no-clear-source state instead of inventing a root.

## Analysis model

Create a pure Kotlin analyzer in the domain clustering model/service boundary. Inputs are `StoryCluster.articles` and an injected similarity function backed by `StoryClusteringEngine.calculateSimilarity` (or an equivalent public API that accepts existing article data). Output is a list of nodes with article identity, parent article ID when linked, relationship enum, similarity, and order.

Rules:

1. Accept at most 20 articles. For more than 20, analyze the 20 oldest entries and surface a localized “showing earliest 20” note; do not silently truncate.
2. Sort by `article.date.time`, then `article.id` for deterministic output.
3. The earliest label is assigned only if the earliest timestamp is unique. Equal earliest timestamps mean no unique earliest article.
4. For every later article, compare against preceding articles only. Pick the preceding article with the highest similarity. Create a directed edge to it only when similarity is `>= 0.75f`; otherwise leave it unlinked and label it “No clear source relationship”. This graph expresses similarity proximity, not copying.
5. Treat non-finite/out-of-range similarity as invalid and clamp valid values to `[0f, 1f]`. Missing/blank article IDs are excluded from parent matching; blank feed names fall back to the article link host when available, otherwise a localized unknown-source label.
6. If all timestamps are equal, or all similarities are below threshold, return an explicit ambiguous/no-clear-source result. Never choose a source based on list order alone.
7. Analysis must be local-only and must not call AI/network APIs. For 20 items, at most 190 similarity evaluations; target under 200 ms on representative device after feature-cache warmup and report measured cold/warm timings in verification notes.

## Integration and UI boundaries

- `StoryCluster` remains unchanged; provenance analysis is on demand from the sheet action, not added to every `HomeViewModel` clustering refresh.
- Add a small state in `StoryClusterSheet` (`hidden` / `shown`) to switch from perspectives to Source DNA and back. Do not place expensive analysis in a composable body; compute on click and pass immutable result into rendering.
- Reuse existing article click callback, Compose sheet, theme tokens, and safe-drawing insets. Keep tree scrollable on narrow displays; no horizontal page scrolling.
- No database/schema changes, background work, provider calls, or permanent caching in this MVP.

## Failure and uncertainty behavior

- Empty cluster, fewer than three distinct feeds, duplicate article IDs, equal timestamps, sparse text, invalid dates/scores, and all-low-similarity cases must not crash or overstate provenance.
- Duplicate article IDs are deduplicated deterministically, keeping the earliest timestamp then the first stable item.
- An analysis exception falls back to the existing perspectives UI and shows a localized non-blocking error; article list remains usable.
- The wording must not call any node “copied”, “plagiarized”, or “original source” as a factual claim. Marketing title may retain “Source DNA”, but UI disclosure is mandatory.

## Testing and acceptance

- **Unit:** deterministic sort; unique/tied earliest; parent selection among earlier nodes; threshold boundary (`0.749f`/`0.75f`); highest-similarity parent; duplicate/blank IDs; blank source label fallback; invalid similarity; empty and single-item input; 20/21 cap and truncation note; no-network dependency; performance benchmark for 20 articles.
- **Widget:** action visible only with at least 3 distinct feeds; enter/exit Source DNA; graph branches and similarity labels; ambiguity/no-edge state; disclaimer; long titles/feed names wrap; node click delegates correct article; edge-to-edge and narrow viewport.
- **Integration:** open existing cluster from Flow, open Source DNA, navigate a selected node to its article, return to Flow. Verify clustering result and existing lead-article behavior remain unchanged.
- **Device smoke:** Tecno only (`115333744A005844`), no `connected*` Gradle task. Stop immediately if any full-screen ad appears and wait for owner’s `done` before continuing.
- **Commands:** `./gradlew testDevDebugUnitTest assembleDevDebug assembleDevDebugAndroidTest lintDevDebug`; run instrumentation per class using explicit `adb -s 115333744A005844 shell am instrument ...`.

## Scope exclusions

- No claim of journalist/source identity verification.
- No cross-article citation extraction or network fact-checking.
- No external AI/cloud calls.
- No persistent provenance cache, schema changes, account sync, or rewriting story-cluster membership.
- No modifying user data or feed timestamps.
