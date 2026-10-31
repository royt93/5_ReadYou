# EXC-04 Headline Signals Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add offline, deterministic headline clickbait scoring and lexical tone labels to article lists and Reader, with a default-on privacy setting.

**Architecture:** Pure Kotlin analyzer and bounded per-language lexicons live in `domain/headline/`; Compose callers use `remember` on article ID/title and honor a DataStore-backed preference. Clickbait badge appears only above 70%; supported-language articles always receive positive/unclear/negative tone, while unsupported titles receive no analysis. ECHO-01 may reuse this API later.

**Tech Stack:** Kotlin/JVM, Jetpack Compose, Room list projections unchanged, AndroidX DataStore Preferences, JUnit4/Robolectric/Compose UI tests. No new dependencies, database schema changes, network, Gemini, ML Kit, or article-body reads.

## Global Constraints

- Branch: `dev` only; no worktrees.
- Analyzer is pure Kotlin, deterministic, offline, and bounded to the first 240 title characters.
- Supported languages: English, Vietnamese, Spanish, French, German; ES/FR/DE are experimental and not native-reviewed.
- Unknown/unsupported language returns `null`; never guess a language or show a guessed signal.
- Tone `UNCLEAR` means insufficient lexical evidence, not objective neutrality; UI must pair color with text/icon/accessibility label.
- Clickbait badge threshold is strictly `score > 70`.
- Analyze title only; preserve FIX-17 and never add body columns to list queries.
- DataStore toggle defaults ON and disables both clickbait and tone signals when OFF.
- Add localizable strings to all 38 maintained resource folders. EN/VI are manual; ES/FR/DE copy is experimental, not native-reviewed.
- Do not claim truth, misinformation, political bias, ideology, author intent, or article stance.

---

## File Structure

- Create `app/src/main/java/com/mckimquyen/reader/domain/headline/HeadlineAnalyzer.kt`: public enums/result/analysis contract, bounded scoring and language identification.
- Create `app/src/main/java/com/mckimquyen/reader/domain/headline/HeadlineLexicon.kt`: token/phrase sets and weights for EN/VI/ES/FR/DE; one file, language-specific sections.
- Create `app/src/test/java/com/mckimquyen/reader/domain/headline/HeadlineAnalyzerTest.kt`: deterministic tests, language and signal coverage, adversarial/no-signal cases.
- Create `app/src/test/java/com/mckimquyen/reader/domain/headline/HeadlineAnalyzerPerformanceTest.kt`: warmed repeated analysis of 150 representative titles, median threshold.
- Create `app/src/main/java/com/mckimquyen/reader/infrastructure/pref/HeadlineSignalsPref.kt`.
- Modify `app/src/main/java/com/mckimquyen/reader/ui/ext/ExtDataStore.kt`, `infrastructure/pref/Settings.kt`, `infrastructure/pref/Pref.kt`, `ui/page/setting/privacy/PrivacySettingsViewModel.kt`, and `PrivacySettingsPage.kt`.
- Create `app/src/main/java/com/mckimquyen/reader/ui/page/headline/HeadlineSignals.kt`: flags, composition locals if needed, and small reusable signal badge composables.
- Modify `ui/page/home/flow/ArticleItem.kt`, `ui/page/home/flow/FlowPage.kt`, and `ui/page/home/read/Content.kt` only at the existing badge/metadata extension points.
- Add 11–14 concise keys to `strings.xml` in all 38 maintained locale folders; keep resource qualifier set unchanged.
- Add `HeadlineSignalsViewModelTest` (or extend existing privacy settings VM test) and `HeadlineSignalBadgeTest` / `ArticleItemTest` / Reader compose tests under `app/src/androidTest` using established Compose test patterns.

## Interfaces

Task 1 produces:

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

Task 2 produces `HeadlineSignalsPref.ON/OFF`, `.default`, `.fromPreferences()`, and `Context.headlineSignalsEnabled`. Task 3 exposes a map of article ID to signal only while enabled. UI consumes `HeadlineSignal?` and never infers sentiment from color alone.

---

### Task 1: Pure Kotlin analyzer and lexicons

**Files:** Create `HeadlineAnalyzer.kt`, `HeadlineLexicon.kt`, and `HeadlineAnalyzerTest.kt`.

- [ ] **Step 1: Add failing tests for the analyzer contract**

Create test fixture helper and assertions:

```kotlin
private fun analyze(title: String) = requireNotNull(HeadlineAnalyzer.analyze(title))

@Test fun blankOrUnsupportedTitlesReturnNull() {
    assertNull(HeadlineAnalyzer.analyze("  "))
    assertNull(HeadlineAnalyzer.analyze("東京の鉄道ニュース"))
}

@Test fun scoreIsDeterministicAndBounded() {
    val title = "SHOCKING: You won't believe what happened next!!!"
    assertEquals(analyze(title), analyze(title))
    assertTrue(analyze(title).clickbaitScore in 0..100)
}

@Test fun ordinaryBbcHeadlineDoesNotCrossClickbaitThreshold() {
    assertTrue(analyze("Chancellor to cut tariffs on some staple goods to offer cost of living help").clickbaitScore <= CLICKBAIT_THRESHOLD)
}

@Test fun repeatedPunctuationAndBaitPhraseRaiseScore() {
    val plain = analyze("Council approves new tram route")
    val bait = analyze("Shocking! Council reveals the truth about new tram route!!!")
    assertTrue(bait.clickbaitScore > plain.clickbaitScore)
}

@Test fun allFiveSupportedLanguagesAreDetectedAndUnrecognizedTextIsIgnored() {
    assertEquals(HeadlineLanguage.ENGLISH, analyze("Shocking: the truth revealed today").language)
    assertEquals(HeadlineLanguage.VIETNAMESE, analyze("Sự thật chấn động: điều gì vừa xảy ra?").language)
    assertEquals(HeadlineLanguage.SPANISH, analyze("La verdad impactante que nadie esperaba").language)
    assertEquals(HeadlineLanguage.FRENCH, analyze("La vérité incroyable que personne n'attendait").language)
    assertEquals(HeadlineLanguage.GERMAN, analyze("Die unglaubliche Wahrheit, die niemand erwartet hat").language)
}
```

Add further failing cases before implementation for each scoring feature independently (bait phrase, direct unanswered curiosity question, repeated punctuation, uppercase proportion, numbered-list lead, intensifiers), score saturation, short/240+/Unicode titles, positive/negative/unclear tie, tragic fact without emotional wording -> unclear, and `>70` exact boundary predicate `shouldShowClickbait(score) == (score > 70)`.

- [x] **Step 2: Run targeted tests and confirm compilation/red state**

Evidence: unresolved `HeadlineAnalyzer` and model symbols before implementation.

- [x] **Step 3: Implement minimum analyzer**

Implement title normalization and a maximum 240-character input window; use `Locale.ROOT` for case conversion; use deterministic Unicode-aware tokenization. Detect supported language conservatively from script/diacritics plus function-word/token evidence; otherwise return null. Do not use Android APIs, regexes on unbounded input, external libraries, network, or random values. Store curated lexicons/weights in `HeadlineLexicon.kt`; bound each feature's contribution and total score to 0..100. Return `UNCLEAR` when positive/negative evidence is weak or tied.

- [x] **Step 4: Run targeted tests and tune only against explicit fixtures**

Evidence: `HeadlineAnalyzerTest` 17/17 and `HeadlineAnalyzerPerformanceTest` 1/1 pass on rerun.

- [x] **Step 5: Commit**

Commit: `a184beb4`.

```bash
git add app/src/main/java/com/mckimquyen/reader/domain/headline app/src/test/java/com/mckimquyen/reader/domain/headline
git commit -m "feat(exc-04): add deterministic local headline analyzer"
```

### Task 2: Preference and settings toggle

**Files:** Create `HeadlineSignalsPref.kt`; modify `ExtDataStore.kt`, `Settings.kt`, `Pref.kt`, privacy settings VM/page; tests in `PrivacySettingsViewModelTest.kt` and `PrivacyPreferencesTest.kt`.

- [ ] **Step 1: Add failing default, persistence, and state-observation tests**

```kotlin
@Test fun headlineSignalsPref_defaultsOnAndReadsExplicitOff() {
    assertTrue(HeadlineSignalsPref.fromPreferences(mutablePreferencesOf()).value)
    assertFalse(HeadlineSignalsPref.fromPreferences(mutablePreferencesOf(
        booleanPreferencesKey("headline_signals_enabled") to false,
    )).value)
}
```

Add a ViewModel test that clears DataStore, observes initial true, switches false, waits for the DataStore flow, and asserts both `viewModel.headlineSignals` and `context.headlineSignalsEnabled` are false; then switch true and assert true.

- [x] **Step 2: Run targeted tests red**

Evidence: unresolved `HeadlineSignalsPref`, `headlineSignals`, `headlineSignalsEnabled`, and `setHeadlineSignals` symbols before implementation.

- [x] **Step 3: Implement using the `RetractionRadarPref` pattern**

Use sealed `HeadlineSignalsPref.ON/OFF`, default ON, key `headline_signals_enabled`; wire `Settings`, context extension and privacy VM. Add one `SettingItem` in Privacy & Security with clear copy: local lexical heuristics; not fact-checking or political-bias detection; ES/FR/DE experimental. Do not add a new screen.

- [x] **Step 4: Run targeted tests green**

Evidence: `PrivacyPreferencesTest` 5/5 and `PrivacySettingsViewModelTest` 3/3 pass.

- [x] **Step 5: Commit**

Commit: `8f8604c0`.

```bash
git add app/src/main/java/com/mckimquyen/reader/infrastructure/pref app/src/main/java/com/mckimquyen/reader/ui/ext/ExtDataStore.kt app/src/main/java/com/mckimquyen/reader/ui/page/setting/privacy app/src/test/java/com/mckimquyen/reader/infrastructure/pref app/src/test/java/com/mckimquyen/reader/ui/page/setting/privacy
git commit -m "feat(exc-04): add headline signal preference"
```

### Task 3: One-time-per-composition signal flow and badges

**Files:** Create `ui/page/headline/HeadlineSignals.kt`; modify `ArticleItem.kt`, `FlowPage.kt`, and `Content.kt`; create/extend Compose UI tests.

- [x] **Step 1: Add failing pure UI-model and Compose tests**

Test `clickbaitVisible(signal)` is true only when supported and score > 70; tone labels map POSITIVE/UNCLEAR/NEGATIVE to localized labels/icons; null signal shows no badge. In Compose tests, verify accessible text and test tags for all states; verify score 70 hides badge and 71 shows it; verify no color-only distinction by asserting semantic text/contentDescription.

- [ ] **Step 2: Run tests red**

Run: `./gradlew connectedDevDebugAndroidTest` is forbidden because it may target multiple devices. Build test APK only with `./gradlew assembleDevDebugAndroidTest`; run Android tests later with a Pixel-only `adb -s 2B051FDH3006MU shell am instrument` command.
Expected: the targeted test sources fail to compile before implementation.

- [x] **Step 2: Run tests red**

Evidence: tests referenced missing `HeadlineSignalBadges` and failed to compile before implementation.

- [x] **Step 3: Implement badge composables and cache safely**

Use pure `HeadlineAnalyzer.analyze(article.title)` inside `remember(article.id, article.title, headlineSignalsEnabled)` at each composition boundary. Add `HeadlineSignalBadges` with tone text+icon+color and clickbait score badge. The list computes no article body or DB query; reader computes from its already-present title. Keep clickbait badge on list only; show tone badge near Reader metadata. Add short accessible descriptions that say "headline language signal" and never imply verified truth.

- [x] **Step 4: Run JVM tests and compile Android tests**

Evidence: JVM headline tests 28/28 pass (`HeadlineAnalyzerTest` 17, perf 1, badge 7, signal gate 3); `assembleDevDebugAndroidTest` passed. Integration `ArticleItemTest` adds 3 scenarios; compilation included, device execution is reserved for Task 5 Pixel verification.

- [x] **Step 5: Commit**

Commit: `ffe924b4`.

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/page/headline app/src/main/java/com/mckimquyen/reader/ui/page/home/flow/ArticleItem.kt app/src/main/java/com/mckimquyen/reader/ui/page/home/flow/FlowPage.kt app/src/main/java/com/mckimquyen/reader/ui/page/home/read/Content.kt app/src/androidTest
git commit -m "feat(exc-04): show clickbait and tone signals in article UI"
```

### Task 4: 38-locale strings and parity

**Files:** `app/src/main/res/values*/strings.xml`; `StringResourceParityTest`.

- [ ] **Step 1: Add/verify keys in base and locale XML**

Add keys for clickbait score, tone positive/unclear/negative, setting title/description, heuristic disclaimer, and accessibility labels. Use manually authored EN/VI; add ES/FR/DE text as experimental translations; copy English fallback to other 33 locales. Preserve exact key parity and XML escaping. Tone copy must render `UNCLEAR` as "Chưa rõ", never "Trung lập".

- [ ] **Step 2: Run resource parity test red if any folder is incomplete**

Run: `./gradlew testDevDebugUnitTest --tests "*StringResourceParityTest"`
Expected before additions: failure caused by missing keys (the locale directories were enumerated and all 38 must match).

- [ ] **Step 3: Add the exact same key set to each maintained strings.xml**

Use a script only if it writes complete values per locale and verifies each XML parse; do not mass-insert untranslated strings over EN/VI/ES/FR/DE values.

- [x] **Step 4: Run parity and resource merge**

Evidence: `StringResourceParityTest` 6/6 passed after inserting the 11 keys into all 38 XML files; `processDevDebugResources` passed (Task 2 validation).

Run: `./gradlew testDevDebugUnitTest --tests "*StringResourceParityTest" --rerun processDevDebugResources`
Expected: parity green; Android resource merge succeeds.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res
 git commit -m "i18n: add EXC-04 headline signal strings to 38 locales"
```

### Task 5: Performance, end-to-end tests, smoke, and report

**Files:** `HeadlineAnalyzerPerformanceTest.kt`; Compose tests; `doc/task/todo/EXC-04_ai-clickbait-bias-buster.md`; later DONE report and `doc/feature.md`.

- [ ] **Step 1: Add a repeatable warm performance self-check**

Run 150 representative titles through the analyzer after warm-up, record per-title nanoseconds, compute median, and assert median < 50 ms for the batch-equivalent target stated in design (report actual host JVM and values). Avoid flaky hard assertions on wall-clock variance by measuring repeated batches and asserting the median under a generous 50 ms per 150-title batch.

- [ ] **Step 2: Add setting-OFF and unsupported-language Compose checks**

Verify OFF removes both signals; unsupported titles do not show any fabricated label; Reader and list display the same tone for a supported title.

- [ ] **Step 3: Run comprehensive verification**

Run `./gradlew testDevDebugUnitTest --rerun`, `./gradlew lintDevDebug`, `./gradlew assembleDevDebug`, and `./gradlew assembleDevDebugAndroidTest`. Report exact counts and warning/error totals.

- [ ] **Step 4: Run device UI tests on the locked Pixel only**

Before any device operation run `adb devices`; lock to `2B051FDH3006MU` only. Install/run test APK only with `adb -s 2B051FDH3006MU`. Do not use `connectedDevDebugAndroidTest`. For smoke, back up Room before creating fixtures, verify foreground before every input event, stop immediately on an interstitial (R4), test one sensational EN title and a normal EN title plus VI title, then restore DB and verify integrity. Do not leave synthetic data on device.

- [ ] **Step 5: Write accurate completion report**

Record analyzer limitations, exact supported language list, experimental status of ES/FR/DE, no fact-checking/summary claim, test counts, performance measurement, smoke result, and any language/native-review gaps. Move task to `doc/task/done/EXC-04_ai-clickbait-bias-buster_DONE.md`; update `doc/feature.md` and epic 05 status. Do not claim the excluded one-sentence truth explanation is implemented.

- [ ] **Step 6: Commit the tested feature/docs on `dev`**

```bash
git add app/src doc
 git commit -m "feat(exc-04): add offline headline clickbait and tone signals"
```

## Final verification checklist

- [ ] Source commit contains no new dependency and no API/cloud call.
- [ ] Full unit suite passes; parity includes all 38 locales.
- [ ] Lint has 0 new errors; build and androidTest APK compile.
- [ ] Pixel smoke was conducted on `2B051FDH3006MU` only or explicitly reported blocked by R4/ad availability; Room restored and integrity verified.
- [ ] Docs state experimental languages and heuristic limits accurately.
