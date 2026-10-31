# EXC-04 — Clickbait & Sentiment Signals Design

- **Feature:** EXC-04 AI Clickbait Buster & Bias/Sentiment Indicator
- **Branch:** `dev`
- **Date:** 2026-10-31
- **Status:** Design approved by owner in conversation; implementation not started.

## Context
Article lists currently show headlines without signals for sensational wording or emotional tone. Existing EXC-04 spec asks for a clickbait score, a one-sentence "truth" explanation, and a three-color sentiment scale. The owner chose on-device heuristics, no AI/API, and both clickbait plus sentiment in this task. A local lexical heuristic cannot reliably explain the factual content of a headline; the one-sentence "truth" explanation is therefore explicitly excluded. ECHO-01 can reuse the sentiment analyzer later rather than duplicate it.

## Owner decisions
- Analyzer: deterministic, on-device, rule-based heuristic; no Gemini, network, API keys, or cloud calls.
- Scope: implement both clickbait score and sentiment/tone signals in EXC-04; ECHO-01 will reuse or extend this analyzer.
- Initial language support: English, Vietnamese, Spanish, French, German. The latter three are experimental; no native-speaker or corpus validation is available for them. Unknown/unsupported languages receive no analysis badge, not a guessed result.
- Tone presentation: one of three states for supported languages: positive (green), negative (red), unclear (gray). Gray means insufficient lexical evidence, not a verified neutral assessment. Always use icon/text/accessibility description as well as color.
- Settings toggle: default ON; toggle disables both clickbait and tone signals.

## User-visible behavior
- In `ArticleItem`, show a compact clickbait badge only when score is strictly greater than 70%; display score with a localized label, not a factual rewrite.
- Show a tone badge for supported languages, always one of positive/unclear/negative; when no supported language is recognized, show no tone badge.
- In Reader, show the same tone label near article metadata. Keep clickbait badge in the list; do not add an unsupported "truth" sentence.
- Disclaimer explains scores are lexical heuristics, not evidence of misinformation, political bias, author intent, or article factuality. Clickbait score only describes headline language; tone only describes lexical emotional cues, not the article's stance or the user's likely emotion.
- The analyzer sees title text only. No DB schema changes and no article body/list-query changes (FIX-17).

## Analyzer contract
Pure Kotlin under `domain/headline/`:

```kotlin
enum class HeadlineLanguage { ENGLISH, VIETNAMESE, SPANISH, FRENCH, GERMAN, UNSUPPORTED }
enum class HeadlineTone { POSITIVE, UNCLEAR, NEGATIVE }
data class HeadlineSignal(
    val language: HeadlineLanguage,
    val clickbaitScore: Int,
    val tone: HeadlineTone,
)
object HeadlineAnalyzer {
    fun analyze(title: String): HeadlineSignal?
}
```

`analyze()` returns `null` for blank/unsupported/unrecognized text. Supported-language detection uses Unicode script/diacritic and high-frequency function-word clues; it does not use ML Kit. EN/VI lexicons are the validated baseline; ES/FR/DE lexicons are experimental. The analysis result is pure/deterministic and has no Android dependency.

Clickbait score combines bounded weighted signals: known bait phrases, direct curiosity-gap questions, repeated `!`/`?`, excessive uppercase words, leading numbered-list patterns, and intensifier terms. Score clamps to 0..100; duplicate signals do not stack without bound. Tone is selected from positive/negative lexicon counts with a margin; ties/weak evidence produce `UNCLEAR`. Language-specific weights/lexicons live in one focused source file, not a new dependency.

## Performance and lifecycle
- `ArticleItem` computes once per `(articleId, title, analyzer version)` with `remember`; no work in `draw` or animation lambdas.
- Short input bound: analyze at most 240 characters of title; do not scan article body.
- A warm list of 150 articles must have a median analysis time below 50 ms on a development host; algorithm is linear in capped title length.
- No persistent cache is needed; no subscriptions/controllers are introduced.
- Reader uses the same pure analyzer on its title, with composition-level memoization.

## Preferences and strings
Add `HeadlineSignalsPref` / DataStore key and a Privacy & Security setting, default ON. Follow existing pattern in `RetractionRadarPref`, `PrivacySettingsViewModel`, `PrivacySettingsPage`, `Settings.kt`, and `Pref.kt`. Add localized labels, descriptions, unsupported/uncertain disclaimer and accessibility text to all 38 maintained resource folders. Translate manually EN/VI; ES/FR/DE are initial English-source translations, explicitly marked experimental in completion report and not represented as native-reviewed.

## Tests
- JVM tests for each language and unknown detection; deterministic same-input result; blank/long input; score clamping; exact threshold boundary (>70 only); each feature signal; repeated punctuation/caps; positive/negative/tie tone; unsupported language returns null.
- Include real BBC title examples from repository fixtures and Vietnamese examples authored for tests; ensure tragic factual headlines without emotion words do not automatically imply author sentiment.
- Test content descriptions and color-independent tone labels.
- Compose tests for ArticleItem badge threshold, all three tone states, unsupported language, setting OFF, Reader tone label.
- `StringResourceParityTest`, preference/ViewModel tests.
- Full `testDevDebugUnitTest --rerun`, `lintDevDebug`, `assembleDevDebug`.
- Pixel 7 Pro smoke (`2B051FDH3006MU`) only after checking `adb devices`, backing up Room before any fixture DB edit, checking foreground before each UI operation, and stopping on interstitial per R4. Use English/Vietnamese headlines, verify list and Reader badges, then restore Room. No synthetic data may be left on the device.

## Explicit non-goals and limits
- No AI/cloud calls, summary/explanation sentence, verified "truth", political bias score, ideology classification, or misinformation claim.
- No sentiment analysis of article body; headline wording only.
- No language support claim beyond EN/VI/ES/FR/DE. ES/FR/DE are experimental heuristics pending native review.
- No database schema changes, network access, or new third-party dependencies.
- Do not label `UNCLEAR` as objectively neutral; UI copy says "Chưa rõ" / equivalent.
