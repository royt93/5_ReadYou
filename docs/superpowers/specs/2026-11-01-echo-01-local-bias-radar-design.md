# ECHO-01 — Local Hype & Sentiment Signals Design

- **Date:** 2026-11-01
- **Branch:** `dev`
- **Status:** Owner-approved design; implementation not started.
- **Device lock:** Google Pixel 7 Pro, serial `2B051FDH3006MU` only.

## 1. Goal and Honest Scope

Add two small, local language-signal gauges beneath article metadata in Reader:
- **Hype/PR wording score (0–100):** measures observable promotional and exaggeration cues in the article body.
- **Lexical tone:** `POSITIVE`, `UNCLEAR`, or `NEGATIVE`, reusing EXC-04 tone semantics. `UNCLEAR` means insufficient lexical evidence; it does not mean neutral.

This feature does **not** determine political bias, factual accuracy, trustworthiness, author intent, or whether a publication is propaganda. It does not call Gemini, any API, or network. It runs only on the article text Reader already has, and is gated by EXC-04's existing default-on `Headline signals` preference.

## 2. Owner Decisions (AskUserQuestion, 2026-11-01)

1. Use an **offline heuristic**, not Gemini.
2. Score **Hype/PR cues and lexical sentiment only**. Explicitly omit political-bias scoring because a local lexicon cannot support that claim.
3. UI: two slim horizontal progress bars under metadata; tap either bar to open details.
4. Hide both bars when Hype is below 40 and tone is `UNCLEAR`; show if Hype is at least 40 or tone is `POSITIVE`/`NEGATIVE`.
5. Hype >= 70 switches the Hype bar to orange and adds a gentle warning. The 85% original example is not a fixed threshold.
6. Reuse the existing `Headline signals` toggle; do not add a second preference.

## 3. Architecture and Data Flow

```text
Reader Content (existing HTML String)
        │
        ├─ SnapshotNormalizer.toPlainText(html)
        ├─ cap at MAX_ANALYZED_BODY_CHARS = 12_000, do not split a surrogate pair
        ▼
ArticleSignalAnalyzer (pure Kotlin, domain/headline/)
        ├─ detect language using the existing EXC-04 language classifier
        ├─ count hype/PR phrases, intensifiers, absolute claims, punctuation and upper-case ratio
        ├─ score lexical tone using existing positive/negative lexicons
        └─ retain up to MAX_FLAGGED_PASSAGES = 8, each <= MAX_PASSAGE_CHARS = 240
        ▼
ArticleSignalResult? (null when disabled, empty, unsupported language, or no signal to show)
        ▼
Reader Content under Metadata, within the existing DisableSelection block
        ├─ two compact progress bars, only when showHypeGauge || showToneGauge
        └─ tap -> EchoSignalDetailsSheet in ReadingPage's existing BottomDrawer
```

Analysis runs on `Dispatchers.Default` through a cancellable `LaunchedEffect`/ViewModel coroutine. Changing article ID or content cancels previous analysis. It never blocks the main thread. No result is persisted in Room and no new dependency/schema is introduced.

## 4. Heuristic and Flagged Passages

### 4.1 Hype score

Start at 0 and add named, bounded weights for observable surface cues:
- Exact promotional/PR phrases from per-language lexicons (e.g. "game-changing", "world-leading", "đột phá", "dẫn đầu thị trường").
- Intensifiers and absolute certainty claims (e.g. "best ever", "guaranteed", "chắc chắn", "tốt nhất từ trước đến nay").
- Repeated `!/?` punctuation and excessive uppercase ratio.
- Normalize by token count so a longer article does not score higher solely due to length.
- Clamp final score to 0..100. Keep all weights, token minimums and thresholds as named constants; tune with tests, not ad hoc UI logic.

### 4.2 Tone

Reuse EXC-04's positive/negative lexicon and `UNCLEAR` rules. Hype and tone are separate signals; a strongly promotional article can be positive, negative, or unclear in tone.

### 4.3 Passage selection

Split plain text on paragraph boundaries. Analyze each paragraph using the same local Hype cues; retain only paragraphs above the named passage threshold, sorted by score descending then source order. Return at most 8 passages, each at most 240 characters (surrogate-safe). Each passage stores its original bounded text and Hype score, with reasons represented by localized cue labels, not generated claims. Do not claim that highlighted text is false or biased.

## 5. UI and State

### 5.1 Inline gauges

Render `EchoSignalGauges` after `Metadata` in Reader's existing `DisableSelection` block, alongside EXC-04 signals:
- `Hype 72%` slim `LinearProgressIndicator`; use orange/warning color only at score >= 70.
- Tone bar with localized label `Positive`, `Unclear`, or `Negative` and a restrained semantic color.
- Show warning copy for Hype >= 70: "This article uses strong promotional or exaggerated wording" (localized).
- Show inline UI only when Hype >= 40 or tone != `UNCLEAR`; do not show an empty/"low hype" block on ordinary articles.
- Both gauges are accessible clickable controls with `contentDescription`, minimum 48dp interaction target and stable tags.

### 5.2 Detail sheet

Use the existing Material `BottomDrawer` in `ReadingPage` (the project is on Material 3 1.0.1; do not add a dependency for a newer `ModalBottomSheet`). On tapping either gauge, show:
- Hype score and tone classification.
- A concise disclaimer: lexical heuristic only; not a fact-check, credibility score, or political-bias analysis; `UNCLEAR` is not neutral.
- Up to 8 flagged passages, each with score and localized cue labels.
- Empty passage state if a tone signal exists but no paragraph crosses the Hype threshold.
- Close via button, back, or sheet swipe; preserve existing Reader drawer arbitration and dismissal behavior.

State is per displayed article in Reader, in memory only. Reuse the existing `headlineSignalsEnabled` preference from `LocalHeadlineSignalsEnabled`; when disabled, cancel analysis and render neither EXC-04 nor ECHO-01 signals.

## 6. Languages and unsupported cases

- EN and VI are supported with curated phrase lists.
- ES/FR/DE reuse EXC-04's experimental language classification and add explicitly experimental Hype phrases.
- Other languages: return `null`, show no gauge.
- Empty/plain-text-less content: return `null`; do not infer from title only in this body feature.
- Analysis is limited to the first 12,000 plain-text characters; document this in accessible/detail copy if shown.

## 7. Localization

Add a small consistent set of `echo_*` keys to all 38 maintained `values*/strings.xml` files. English and Vietnamese use human-reviewed copy; other locales follow the repository's English fallback convention. Keys cover Hype, tone labels, warning, details title, flagged passages, cue labels, close, unsupported/empty state, and disclaimer. Use `StringResourceParityTest` to validate exact parity.

## 8. Tests

### Pure JVM
- `ArticleSignalAnalyzerTest`: Hype grows with promotional cues; ordinary BBC-style prose remains below 40; score clamps to 0..100; length normalization; punctuation/case; positive/negative/UNCLEAR separation; hype independent from tone; language supported/unsupported; HTML conversion; empty input; 12k bound; surrogate-safe passage cap; maximum 8 flagged passages; passage order ties.
- Include adversarial examples: factual reports mentioning a company but no promotional language should not score high; quotations about hype should be reported as wording cues, not intent or falsehood.

### Compose/Robolectric
- Inline bars hidden for Hype <40 + UNCLEAR.
- Bars visible for Hype >=40 or explicit tone.
- Hype >=70 uses warning color/copy; below threshold does not.
- Tap opens details with disclaimer and flagged passages; close calls callback.
- Toggle OFF and unsupported language produce no gauges.

### Integration/device
- `Content` passes HTML body from Reader to analyzer, not a title or full entity from another screen.
- Full suite, lint, `assembleDevDebug`, `assembleDevDebugAndroidTest`.
- Smoke only on Pixel 7 Pro `2B051FDH3006MU`: open ordinary article (gauges hidden), then an article with obvious PR wording (Hype shown), tap for passage details, disable shared Headline signals toggle and verify both ECHO-01/EXC-04 disappear. Back up Room before tests and restore/verify afterward. Stop on interstitial per R4.

## 9. Non-goals / Limitations

- No political-bias score, outlet lean, factuality, author intent or credibility score.
- No Gemini/API/network or API key access.
- No article-body persistence or database schema change.
- No analysis outside the first 12,000 plain-text characters.
- ES/FR/DE remain experimental until language experts validate the lexicons.
