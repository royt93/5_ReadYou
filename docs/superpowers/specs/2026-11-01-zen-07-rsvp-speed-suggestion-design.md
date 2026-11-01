# ZEN-07 — Adaptive RSVP Trainer (Speed Suggestion) Design

- **Date:** 2026-11-01
- **Branch:** `dev`
- **Status:** Owner-approved design; implementation not started.
- **Device lock:** Google Pixel 7 Pro, serial `2B051FDH3006MU` only.
- **Builds on:** ZEN-01 RSVP reader (`ui/page/rsvp/`: `RsvpTokenizer`, `RsvpViewModel`, `RsvpReaderDialog`) and `ZenSettingsPage`.

## 1. Goal and Honest Scope

Today the RSVP reader lets the user pick a speed (200-900 WPM) by hand and gives no feedback about whether that speed suits them, and the speed is not remembered between sessions (`RsvpUiState.wpm` defaults to 350 every time).

This task adds a short **calibration** that suggests a speed, and remembers the user's choice.

What it is, stated plainly:
- A **suggestion** from three short rounds, not a measurement of comprehension. Six multiple-choice questions give a very coarse signal: a random guess on one 4-option question is right 25% of the time, and on two questions 6.25% of the time. The UI says so.
- Offline and static: reading passages and questions are bundled with the app. No AI, no network, no API key, no ads, no purchases.
- Fast recognition of words is not deep understanding, and the app does not claim to evaluate that.
- The user can always ignore the suggestion and set the speed by hand, and can re-run the calibration at any time.

Out of scope: adaptive/staircase algorithms, per-article comprehension checks, AI-generated questions, comprehension history or charts, any Room change (DB stays v20), new dependencies.

## 2. Owner Decisions (AskUserQuestion, 2026-11-01)

1. **Honesty:** suggest WPM from several short rounds and call it a "suggestion", not a measurement.
2. **Content:** static passages bundled with the app, English and Vietnamese; other languages use the English set.
3. **Entry points:** both the RSVP reader dialog and the Zen settings page.
4. **Algorithm:** three fixed rounds at 300 / 450 / 600 WPM; the suggestion is the highest round with a perfect score (2 of 2).

## 3. Pure Logic (`domain/rsvp/`, JVM-testable)

### 3.1 Plan and constants (`RsvpCalibrationPlan`)
- `ROUND_WPMS = listOf(300, 450, 600)`, `QUESTIONS_PER_ROUND = 2`, `PASS_CORRECT = 2`.
- `FALLBACK_WPM = 250` (suggested when no round is passed).
- `MIN_WPM = 200`, `MAX_WPM = 900` (same bounds `RsvpViewModel.setWpm` already enforces).
- Delay scale bounds `MIN_DELAY_SCALE = 0.5f`, `MAX_DELAY_SCALE = 2.0f`.

### 3.2 Scoring (`RsvpCalibrationScorer`)
Input: `List<RoundResult(wpm: Int, correct: Int)>` (one per round that was played). Output: `RsvpSuggestion(wpm: Int, punctuationDelayScale: Float, confidence: Confidence)`.
- `wpm` = the highest `wpm` among rounds with `correct >= PASS_CORRECT`; `FALLBACK_WPM` when none passed. Always clamped to `MIN_WPM..MAX_WPM`.
- `punctuationDelayScale` from the total correct answers `c` over all questions asked (6 when all three rounds are played): `c == total` -> `0.85f`; `c / total >= 2/3` -> `1.0f`; `c / total >= 1/2` -> `1.25f`; otherwise `1.5f`. Always clamped to the bounds above.
- `Confidence` is `MEDIUM` only when at least one round passed **and** the pass pattern is monotonic (every round slower than the highest passed round was also passed). Anything else is `LOW`. It is never higher than `MEDIUM`, because six questions cannot justify more.
- An empty or partial list is valid input (the user may quit early); it never throws.

### 3.3 Content (`RsvpCalibrationContent`)
Two bundled sets (`en`, `vi`), each with three passages of about 85-110 words (Vietnamese runs longer because most words are one syllable) on neutral everyday topics and two multiple-choice questions per passage (four options, exactly one correct). `forLanguage(languageTag)` returns the Vietnamese set for `vi*` and the English set otherwise. Content lives in Kotlin (not in the 38-locale string files) because a question is tied to one specific passage; the UI chrome around it is localized.

### 3.4 Timing helper (`RsvpTiming`)
`RsvpViewModel` currently computes the per-token delay inline (`60_000 / wpm.coerceIn(200, 900) + extraDelayMs`). Extract that exact formula to a pure `RsvpTiming.tokenDelayMs(wpm, token)` and use it from both the reader and the calibration, so both play at the same real speed. Behaviour of the reader does not change; existing tests must stay green.

## 4. Tokenizer change

`RsvpTokenizer.calculateExtraDelayMs(word, isParagraphEnd, punctuationDelayScale: Float = 1f)` and `tokenize(content, punctuationDelayScale: Float = 1f)`: the scale multiplies only the paragraph-end, sentence-end and clause-end delays, not the long-word delay. New parameters are last and defaulted, so every existing call and test is unchanged; at `1f` the output is identical to today's.

## 5. Persistence

Two new DataStore keys in `DataStoreKeys` (`ui/ext/ExtDataStore.kt`): `RsvpSuggestedWpm` (Int) and `RsvpPunctuationDelayScale` (Float). Access goes through an `RsvpCalibrationStore` interface (`read(): SavedRsvpSettings?`, `write(wpm, scale)`) with a DataStore implementation bound by Hilt and a fake in tests. An unset store means "no calibration": the reader keeps today's defaults (350 WPM, scale 1.0), so existing users see no change.

`RsvpViewModel` receives the store as an optional last constructor parameter (`RsvpCalibrationStore? = null`, the pattern `ReadingViewModel` uses for `DossierSharer`), so `RsvpViewModel()` in existing tests still compiles. On `loadContent` it applies the saved WPM and scale once, and tokenizes with the saved scale. A manual `setWpm` always overrides the applied value for that session.

## 6. Calibration flow and UI

`RsvpCalibrationViewModel` (Hilt; injectable clock/dispatchers and `RsvpCalibrationStore`) state machine: `Intro -> Reading(round) -> Questions(round) -> Result -> Done`, with `Cancelled` from anywhere (nothing is saved). Each reading round plays the passage with the same tokens and timing as the reader. Questions show after the passage ends; answers are recorded per round.

`RsvpCalibrationDialog` (Compose, Material 3 1.0.1):
- Intro states what will happen (3 short rounds, about 2-3 minutes) and the honest limit.
- Result shows "Suggested: about X WPM" with the limitation line ("a rough estimate from 6 questions, not a test of how well you understand") and, for `LOW` confidence, a note that results were inconsistent. Two actions: **Apply** (saves WPM and delay scale, sets the reader's current speed) and **Skip** (saves nothing).
- Touch targets at least 48dp, TalkBack content descriptions on the progress and options.

Entry points: a "Train my speed" button in `RsvpReaderDialog` next to the speed slider, and a row in `ZenSettingsPage` below the speed-reading card. From settings the dialog runs standalone and Apply only saves.

## 7. Localization

About 14 `zen_calibration_*` keys in all 38 `values*/strings.xml` (intro, round progress, question header, result text, limitation line, low-confidence note, apply, skip, entry labels): English and Vietnamese hand-written, the rest English fallback. `StringResourceParityTest` must stay 6/6.

## 8. Tests

- **JVM:** `RsvpCalibrationScorerTest` (every score combination per round, no round passed, only the fastest round passed -> non-monotonic -> `LOW`, all perfect -> `MEDIUM` and scale 0.85, partial/empty input, clamping, `confidence` never above `MEDIUM`); `RsvpCalibrationContentTest` (3 passages and 2 questions each, four options each, correct index in range, word counts 60-120, `vi` and fallback selection, no duplicate options in a question); `RsvpTimingTest` (formula matches the old inline one); `RsvpTokenizerTest` additions (scale only touches punctuation delays, `1f` identical to current).
- **Robolectric:** `RsvpCalibrationViewModelTest` (fake clock and store; round progression, quitting early saves nothing, apply saves exactly the scorer's output); `RsvpViewModelTest` additions (saved settings applied once on load, manual `setWpm` wins, no store means defaults) with every existing test unchanged.
- **Compose:** dialog states (intro, question selection, result with and without the low-confidence note, apply/skip callbacks, option touch targets).
- **Instrumentation (Pixel only, `am instrument`):** dialog renders and answers on the real renderer.
- **Device smoke (Pixel `2B051FDH3006MU`):** back up Room and DataStore (non-empty, `integrity_check` ok), run the calibration end to end from the reader, apply, reopen the reader and confirm the saved speed is used, then restore both backups and verify with `cmp`. Ads are closed automatically per the owner's standing instruction.

## 9. Risks and Limits

- Six questions are statistically weak; a lucky pass at a fast round raises the suggestion (6.25% per round by chance alone). Mitigated by the `LOW` confidence label, the honest copy, and easy manual override, not eliminated.
- Static passages are memorized on repeated runs, which inflates later scores. Documented, not solved.
- English and Vietnamese only are tuned; other locales read English passages.
- Reading with a large speed jump between rounds may confuse a user who is already a fast reader; the suggestion never exceeds 600 WPM because that is the fastest round.

## 10. Non-goals

No AI questions, no per-article comprehension checks, no history, no adaptive staircase, no ads or purchases, no Room change, no new dependency.
