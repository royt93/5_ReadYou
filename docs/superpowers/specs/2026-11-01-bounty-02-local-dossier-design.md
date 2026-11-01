# BOUNTY-02 — Local Intelligence Dossier Design

- **Date:** 2026-11-01
- **Branch:** `dev`
- **Status:** Owner-approved design; implementation not started.
- **Device lock:** Google Pixel 7 Pro, serial `2B051FDH3006MU` only.
- **Depends on:** BOUNTY-01 (`BountyInvestigationResult`).

## 1. Goal and Honest Scope

From an investigation result (BOUNTY-01), build a structured, citation-backed **dossier** of what the user's own saved archive says about an entity, show it in four Material 3 tabs, and let the user export it as Markdown through the standard Android share sheet.

This is a **mechanical digest of the local archive**. It does not create information, verify facts, rank sources, or judge who is right. It is offline: no Gemini, no API key, no network, no Reddit/Hacker News. It is limited to what the user has already saved, so it can miss many viewpoints; the UI says so.

## 2. Owner Decisions (AskUserQuestion, 2026-11-01)

1. **Data source:** saved archive only. The original "community debate from Reddit/Hacker News" section is replaced by **"Different framings across sources"**; no network client is added.
2. **Executive Summary:** extract up to three **verbatim** sentences from the related articles, quoted with source and date. No paraphrasing and no LLM.
3. **Persistence/export:** export Markdown via the Android share intent. **No Room table, no migration** (database stays at version 20). PDF is out of scope.

## 3. Input and Constraints

- Input is the `BountyInvestigationResult` already produced by BOUNTY-01: at most 20 articles, each with `title`, `shortDescription`, feed, date, article id. Same-account only.
- FIX-17: only list metadata is used; article bodies are never read or loaded for the dossier.
- Entry point: a `Create dossier` button in the BOUNTY-01 sheet, shown only when the result has at least 2 articles.

## 4. Dossier Content (pure Kotlin, `domain/model/bounty/`)

`DossierBuilder.build(result): Dossier?` returns `null` when there are fewer than 2 articles.

### 4.1 Executive Summary (verbatim quotes)
- Split each article's `title` and `shortDescription` into sentences.
- Keep sentences that contain the query (case-insensitive, whole-phrase boundary, same matcher rules as BOUNTY-01).
- Prefer newer articles; drop near-duplicates when Jaccard similarity of lowercase word sets is `>= SUMMARY_DEDUPE_JACCARD_THRESHOLD` (`0.8`). This is a named constant and a heuristic, not a semantic-similarity claim.
- Return at most **3** `QuotedSentence(text, feedName, date, articleId)`. Each sentence is capped at 240 characters, surrogate-safe. If none qualify, the section shows an honest empty state.

### 4.2 Timeline
- One entry per calendar day (device time zone), newest first. Each entry lists that day's articles with title, feed name and article id. Capped at the 20 input articles.

### 4.3 Key Players ("phrases that recur")
- Candidate phrases are runs of 1–4 consecutive capitalized tokens in titles and short descriptions, excluding the query itself, a sentence-initial single common word, and a small stop list (days, months, "The", "A", ...).
- A phrase is listed when it appears in at least **2 different articles**; show the article count. Sort by count then alphabetically; cap at 8.
- Labelled in the UI as **"Recurring names/phrases"**, not "entities": this counts capitalized phrases and does not identify real people or organizations. Languages without capitalization (e.g. CJK) yield an empty section by design.

### 4.4 Different framings across sources
- Group articles by feed. Requires at least 2 feeds; otherwise show "Your archive has only one source on this topic."
- For each feed show up to 3 of its headlines, newest first, side by side. No conclusion is drawn.

### 4.5 Standing limitations shown in the dossier
"Built only from articles saved in this app. It may omit viewpoints and is not a fact-check."

## 5. Architecture

```text
BountyInvestigationState.RESULTS (articles >= 2)
        │  tap "Create dossier"
        ▼
DossierBuilder.build(result)          [pure Kotlin, Default dispatcher]
        ▼
Dossier(query, summary, timeline, players, framings, generatedAtMillis)
        ├─ DossierSheetContent        [4 tabs in the existing BottomDrawer]
        └─ DossierMarkdownBuilder -> DossierSharer (cacheDir/dossiers, 24h retention, FileProvider share intent)
```

State lives in `ReadingViewModel` (memory only, cleared when the sheet closes). The dossier is rebuilt from the already-loaded result; nothing is persisted except the transient export file.

## 6. UI

- Four tabs: **Summary**, **Timeline**, **Names**, **Sources**. The project uses Material 3 1.0.1; use `TabRow`/`Tab` available in that version. No new dependency.
- A top action row with **Share Markdown** and **Close**. The existing `BackHandler`/drawer arbitration is extended; dismissing returns to the BOUNTY-01 results.
- Tapping any article row opens it (same navigation as BOUNTY-01).
- Accessibility: each tab and row has a content description; touch targets are at least 48dp.

## 7. Markdown Export

`DossierMarkdownBuilder.build(dossier): String` is pure and deterministic:
- Title with the query, generation date, the standing limitation line.
- One section per tab. Quotes use `>`; links use the article link only if the article link is present.
- All user/article text is escaped for Markdown control characters and may not break out of its line.

`DossierSharer` writes `dossier_<slug>_<millis>.md` to `cacheDir/dossiers`, deletes files older than 24 hours, and returns a `ACTION_SEND` intent (`text/markdown`) with `FLAG_GRANT_READ_URI_PERMISSION` via the existing `FileProvider`.

## 8. Localization

About 16 `dossier_*` keys across all 38 maintained `values*/strings.xml` files (English and Vietnamese hand-written; others English fallback). Covers button, tab titles, section headings, empty states, limitation text, share action and chooser title.

## 9. Tests

- **Pure JVM:** sentence selection (verbatim, cap, dedupe, newest-first, no match), timeline (same-day grouping, ordering, time zone), key players (threshold 2 articles, stop list, cap 8, query exclusion, non-capitalized scripts), framings (one feed vs many, per-feed cap), builder returns `null` below 2 articles, Markdown (escaping, determinism, no raw HTML injection).
- **Robolectric:** `DossierSharer` (file written, retention deletes old files, intent flags/MIME), Compose tests for tab switching, empty states and share callback.
- **ViewModel:** build off the main thread, cancellation on dismiss, entry only with >= 2 articles.
- `StringResourceParityTest`; full unit suite, lint, `assembleDevDebug`, `assembleDevDebugAndroidTest`.
- **Device (Pixel `2B051FDH3006MU` only):** back up Room and DataStore, run instrumentation via `am instrument`, smoke the flow (investigate -> create dossier -> switch tabs -> share), restore both backups. Ads are closed automatically per the owner's standing instruction.

## 10. Non-goals and Limitations

- No Reddit/Hacker News, no network, no AI, no API keys.
- No persistence, no library page, no Room migration.
- No PDF export.
- Key Players counts recurring capitalized phrases; it is not named-entity recognition.
- Quality depends on how many related articles the user has saved; with few or short articles most sections are sparse.
