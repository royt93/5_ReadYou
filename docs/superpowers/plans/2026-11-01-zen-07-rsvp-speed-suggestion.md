# ZEN-07 Adaptive RSVP Speed Suggestion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Suggest an RSVP reading speed and punctuation-delay scale from three short calibration rounds (300 / 450 / 600 WPM, 2 questions each) and save the choice as the user's default speed.

**Architecture:** Pure logic (`RsvpCalibrationPlan`, `RsvpCalibrationScorer`, `RsvpCalibrationContent`, `RsvpTiming`) handles scoring, content and per-token delay calculation on the JVM without Android dependencies. `RsvpTokenizer` accepts an optional `punctuationDelayScale`. DataStore persists `RsvpSuggestedWpm` and `RsvpPunctuationDelayScale` through an injectable `RsvpCalibrationStore` interface. `RsvpCalibrationViewModel` runs the multi-round calibration dialog, which opens from both `RsvpReaderDialog` and `ZenSettingsPage`.

**Tech Stack:** Kotlin, Jetpack Compose Material 3 1.0.1, DataStore, Hilt, JUnit4, Robolectric, Android Compose instrumentation. No new dependencies.

## Global Constraints

- Branch `dev` only; no worktrees. Never mention, suggest or remind about pull requests.
- Pixel 7 Pro serial `2B051FDH3006MU` is the only device (`adb -s 2B051FDH3006MU ...`). Never use `connectedDevDebugAndroidTest`.
- Smoke MUST back up BOTH `databases/Reader` (plus `-wal` and `-shm`) AND `files/datastore/settings.preferences_pb` (non-empty, `PRAGMA integrity_check` ok on a copy), and restore BOTH with `adb shell "run-as com.mckimquyen.reader sh -c 'cat > PATH'" < local`. Verify with `cmp` across all files; never delete the datastore on device.
- Standing owner rule: if a full-screen `com.google.android.gms.ads.AdActivity` appears, close it via its labelled control ("Tiếp tục đến ứng dụng"), re-check `topResumedActivity`, never tap ad creative, and report that ads appeared.
- No ads, purchases or rewarded video in this feature.
- Room DB stays at version 20; no migration, no table change.
- Exact constants:
  - `ROUND_WPMS = listOf(300, 450, 600)`
  - `QUESTIONS_PER_ROUND = 2`
  - `PASS_CORRECT = 2`
  - `FALLBACK_WPM = 250`
  - `DEFAULT_WPM = 350`
  - `MIN_WPM = 200`, `MAX_WPM = 900`
  - `MIN_DELAY_SCALE = 0.5f`, `MAX_DELAY_SCALE = 2.0f`, `DEFAULT_DELAY_SCALE = 1.0f`
- Punctuation delay scale mapping (from total correct answers `c` out of all questions asked):
  - `c == total` -> `0.85f`
  - `c.toFloat() / total >= 2f / 3f` -> `1.0f`
  - `c.toFloat() / total >= 0.5f` -> `1.25f`
  - otherwise `1.5f`
- Confidence: `MEDIUM` only when at least one round passed AND the pass pattern is monotonic (every round slower than the highest passed round was also passed); otherwise `LOW`. Never higher than `MEDIUM`.
- Tokenizer: punctuation delay scale multiplies only paragraph-end (250 ms), sentence-end (200 ms) and clause-end (80 ms) delays, never the long-word delay (40 ms). Default scale is `1.0f` so existing behaviour is identical.
- Passages: static bundled content in `RsvpCalibrationContent` for `en` and `vi`, other languages use `en`. 3 passages x 2 questions x 4 choices each.
- Compose pitfalls: `by` needs `getValue`/`setValue` imports; `stringResource` is `@Composable`, hoist out of lambdas; new composable parameters go last with defaults; read real failing assertions from test XML; find-based APK path (filename has spaces).
- 14 new `zen_calibration_*` keys across all 38 `values*/strings.xml`. English and Vietnamese hand-written, others English fallback. Added in the task that first uses them; `StringResourceParityTest` must stay 6/6.
- Honest limits: a coarse estimate from 6 questions, not a test of deep comprehension; static passages can be memorized on repeated runs.

---

## File Structure

- Create `app/src/main/java/com/mckimquyen/reader/domain/rsvp/RsvpCalibrationPlan.kt`: constants, data classes (`RoundResult`, `RsvpSuggestion`, `Confidence`).
- Create `app/src/main/java/com/mckimquyen/reader/domain/rsvp/RsvpCalibrationScorer.kt`: pure scoring and confidence calculation.
- Create `app/src/main/java/com/mckimquyen/reader/domain/rsvp/RsvpCalibrationContent.kt`: bundled reading passages and questions for EN and VI.
- Create `app/src/main/java/com/mckimquyen/reader/domain/rsvp/RsvpTiming.kt`: pure per-token delay calculation.
- Modify `app/src/main/java/com/mckimquyen/reader/ui/page/rsvp/RsvpTokenizer.kt`: accept optional `punctuationDelayScale`.
- Modify `app/src/main/java/com/mckimquyen/reader/ui/ext/ExtDataStore.kt`: add `RsvpSuggestedWpm` and `RsvpPunctuationDelayScale`.
- Create `app/src/main/java/com/mckimquyen/reader/ui/page/rsvp/RsvpCalibrationStore.kt` and `app/src/main/java/com/mckimquyen/reader/infrastructure/di/RsvpCalibrationModule.kt`: interface, DataStore implementation, Hilt binding.
- Modify `app/src/main/java/com/mckimquyen/reader/ui/page/rsvp/RsvpViewModel.kt`: use `RsvpTiming`, inject optional `RsvpCalibrationStore`, apply saved speed on load, tokenize with saved scale.
- Create `app/src/main/java/com/mckimquyen/reader/ui/page/rsvp/RsvpCalibrationViewModel.kt`: state machine (`Intro`, `Reading`, `Questions`, `Result`).
- Create `app/src/main/java/com/mckimquyen/reader/ui/page/rsvp/RsvpCalibrationDialog.kt`: Compose dialog for the calibration flow.
- Modify `app/src/main/java/com/mckimquyen/reader/ui/page/rsvp/RsvpReaderDialog.kt`: add "Train speed" button.
- Modify `app/src/main/java/com/mckimquyen/reader/ui/page/setting/zen/ZenSettingsPage.kt`: add calibration entry row.
- Modify all 38 `app/src/main/res/values*/strings.xml`.
- Tests: `RsvpCalibrationScorerTest`, `RsvpCalibrationContentTest`, `RsvpTimingTest`, additions to `RsvpTokenizerTest`, `RsvpCalibrationStoreTest`, `RsvpCalibrationViewModelTest`, additions to `RsvpViewModelTest`, `RsvpCalibrationDialogTest`, `RsvpCalibrationDeviceTest`.
- Docs: `doc/task/done/ZEN-07_adaptive-rsvp-trainer_DONE.md`, `doc/feature.md`, update `doc/task/todo/08_ZEN_FOCUS_AND_SPEED_READING.md`, delete `doc/task/todo/ZEN-07_adaptive-rsvp-trainer.md`.

## Interfaces

Task 1 produces (`com.mckimquyen.reader.domain.rsvp`):

```kotlin
enum class Confidence { LOW, MEDIUM }

data class RoundResult(val wpm: Int, val correct: Int, val total: Int = 2)

data class RsvpSuggestion(
    val wpm: Int,
    val punctuationDelayScale: Float,
    val confidence: Confidence,
    val passedRounds: List<Int>,
)

object RsvpCalibrationPlan {
    val ROUND_WPMS = listOf(300, 450, 600)
    const val QUESTIONS_PER_ROUND = 2
    const val PASS_CORRECT = 2
    const val FALLBACK_WPM = 250
    const val DEFAULT_WPM = 350
    const val MIN_WPM = 200
    const val MAX_WPM = 900
    const val MIN_DELAY_SCALE = 0.5f
    const val MAX_DELAY_SCALE = 2.0f
    const val DEFAULT_DELAY_SCALE = 1.0f
}

object RsvpCalibrationScorer {
    fun score(results: List<RoundResult>): RsvpSuggestion
}

object RsvpTiming {
    fun baseDelayMs(wpm: Int): Long
    fun tokenDelayMs(wpm: Int, extraDelayMs: Long): Long
}
```

Task 2 produces (`com.mckimquyen.reader.domain.rsvp`):

```kotlin
data class CalibrationQuestion(val prompt: String, val options: List<String>, val correctIndex: Int)
data class CalibrationPassage(val text: String, val questions: List<CalibrationQuestion>)

object RsvpCalibrationContent {
    fun forLanguage(languageTag: String): List<CalibrationPassage>
}
```

Task 3 produces (`com.mckimquyen.reader.ui.page.rsvp`):

```kotlin
data class SavedRsvpSettings(val wpm: Int, val punctuationDelayScale: Float)

interface RsvpCalibrationStore {
    suspend fun read(): SavedRsvpSettings?
    suspend fun write(wpm: Int, punctuationDelayScale: Float)
}
```

Task 4 produces (`com.mckimquyen.reader.ui.page.rsvp`):

```kotlin
// RsvpViewModel keeps a public no-arg constructor (existing tests call RsvpViewModel()) and gets a
// separate @Inject constructor for Hilt, because Hilt cannot satisfy a nullable defaulted parameter:
//   @HiltViewModel
//   class RsvpViewModel internal constructor(private val calibrationStore: RsvpCalibrationStore?) : ViewModel() {
//       @Inject constructor(store: RsvpCalibrationStore) : this(store as RsvpCalibrationStore?)
//       constructor() : this(null as RsvpCalibrationStore?)
//   }
sealed interface CalibrationStep {
    object Intro : CalibrationStep
    data class Reading(val roundIndex: Int, val wpm: Int, val text: String) : CalibrationStep
    data class Questions(val roundIndex: Int, val wpm: Int, val questions: List<CalibrationQuestion>) : CalibrationStep
    data class Result(val suggestion: RsvpSuggestion) : CalibrationStep
}

class RsvpCalibrationViewModel ...
```

Task 5 produces tags: `ZEN_CALIBRATION_START_TAG`, `ZEN_CALIBRATION_APPLY_TAG`, `ZEN_CALIBRATION_SKIP_TAG`, `ZEN_CALIBRATION_TRAIN_BUTTON_TAG`, `ZEN_CALIBRATION_OPTION_TAG_PREFIX`, and the composable `RsvpCalibrationScreen` (the domain object keeps the name `RsvpCalibrationContent`).

---

### Task 1: `RsvpCalibrationPlan`, `RsvpCalibrationScorer`, and `RsvpTiming` (pure JVM)

**Files:**
- Create `app/src/main/java/com/mckimquyen/reader/domain/rsvp/RsvpCalibrationPlan.kt`
- Create `app/src/main/java/com/mckimquyen/reader/domain/rsvp/RsvpCalibrationScorer.kt`
- Create `app/src/main/java/com/mckimquyen/reader/domain/rsvp/RsvpTiming.kt`
- Create `app/src/test/java/com/mckimquyen/reader/domain/rsvp/RsvpCalibrationScorerTest.kt`
- Create `app/src/test/java/com/mckimquyen/reader/domain/rsvp/RsvpTimingTest.kt`

- [ ] **Step 1: Write failing tests for Scorer**

```kotlin
package com.mckimquyen.reader.domain.rsvp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpCalibrationScorerTest {

    @Test
    fun emptyResults_returnsFallbackWpm_defaultScale_lowConfidence() {
        val s = RsvpCalibrationScorer.score(emptyList())
        assertEquals(RsvpCalibrationPlan.FALLBACK_WPM, s.wpm)
        assertEquals(RsvpCalibrationPlan.DEFAULT_DELAY_SCALE, s.punctuationDelayScale, 0.0001f)
        assertEquals(Confidence.LOW, s.confidence)
        assertTrue(s.passedRounds.isEmpty())
    }

    @Test
    fun allRoundsFailed_returnsFallbackWpm_andScale1_5() {
        val results = listOf(
            RoundResult(wpm = 300, correct = 0, total = 2),
            RoundResult(wpm = 450, correct = 1, total = 2),
            RoundResult(wpm = 600, correct = 0, total = 2),
        )
        val s = RsvpCalibrationScorer.score(results)
        assertEquals(RsvpCalibrationPlan.FALLBACK_WPM, s.wpm)
        assertEquals(1.5f, s.punctuationDelayScale, 0.0001f)
        assertEquals(Confidence.LOW, s.confidence)
    }

    @Test
    fun allRoundsPassed_returnsHighestWpm_scale0_85_mediumConfidence() {
        val results = listOf(
            RoundResult(wpm = 300, correct = 2, total = 2),
            RoundResult(wpm = 450, correct = 2, total = 2),
            RoundResult(wpm = 600, correct = 2, total = 2),
        )
        val s = RsvpCalibrationScorer.score(results)
        assertEquals(600, s.wpm)
        assertEquals(0.85f, s.punctuationDelayScale, 0.0001f)
        assertEquals(Confidence.MEDIUM, s.confidence)
        assertEquals(listOf(300, 450, 600), s.passedRounds)
    }

    @Test
    fun passed300And450_failed600_returns450_scale1_0_mediumConfidence() {
        val results = listOf(
            RoundResult(wpm = 300, correct = 2, total = 2),
            RoundResult(wpm = 450, correct = 2, total = 2),
            RoundResult(wpm = 600, correct = 1, total = 2), // 5 of 6 correct = 83%, which is >= 2/3 -> scale 1.0
        )
        val s = RsvpCalibrationScorer.score(results)
        assertEquals(450, s.wpm)
        assertEquals(1.0f, s.punctuationDelayScale, 0.0001f)
        assertEquals(Confidence.MEDIUM, s.confidence)
    }

    @Test
    fun passedOnly300_returns300_scale1_25_mediumConfidence() {
        val results = listOf(
            RoundResult(wpm = 300, correct = 2, total = 2),
            RoundResult(wpm = 450, correct = 1, total = 2),
            RoundResult(wpm = 600, correct = 0, total = 2), // 3 of 6 = 50%, which is >= 0.5 -> scale 1.25
        )
        val s = RsvpCalibrationScorer.score(results)
        assertEquals(300, s.wpm)
        assertEquals(1.25f, s.punctuationDelayScale, 0.0001f)
        assertEquals(Confidence.MEDIUM, s.confidence)
    }

    @Test
    fun nonMonotonicPattern_failed300_passed600_returns600_withLowConfidence() {
        // Lucky guess at 600: user failed the easy round
        val results = listOf(
            RoundResult(wpm = 300, correct = 1, total = 2),
            RoundResult(wpm = 450, correct = 0, total = 2),
            RoundResult(wpm = 600, correct = 2, total = 2),
        )
        val s = RsvpCalibrationScorer.score(results)
        assertEquals(600, s.wpm)
        assertEquals(Confidence.LOW, s.confidence)
    }

    @Test
    fun confidence_isNeverHigherThanMedium() {
        val results = listOf(
            RoundResult(wpm = 300, correct = 2, total = 2),
            RoundResult(wpm = 450, correct = 2, total = 2),
            RoundResult(wpm = 600, correct = 2, total = 2),
        )
        val s = RsvpCalibrationScorer.score(results)
        assertEquals(Confidence.MEDIUM, s.confidence)
    }

    @Test
    fun wpm_isAlwaysClampedBetweenMinAndMax() {
        val s = RsvpCalibrationScorer.score(listOf(RoundResult(wpm = 1000, correct = 2, total = 2)))
        assertEquals(RsvpCalibrationPlan.MAX_WPM, s.wpm)
    }
}
```

- [ ] **Step 2: Write failing tests for Timing**

```kotlin
package com.mckimquyen.reader.domain.rsvp

import org.junit.Assert.assertEquals
import org.junit.Test

class RsvpTimingTest {

    @Test
    fun baseDelay_matchesExistingFormula() {
        // 60_000 / wpm coerced to 200..900
        assertEquals(200L, RsvpTiming.baseDelayMs(300))
        assertEquals(150L, RsvpTiming.baseDelayMs(400))
        assertEquals(100L, RsvpTiming.baseDelayMs(600))
        assertEquals(300L, RsvpTiming.baseDelayMs(100)) // coerced to 200
        assertEquals(66L, RsvpTiming.baseDelayMs(1500)) // coerced to 900 -> 60000/900 = 66
    }

    @Test
    fun tokenDelay_addsExtraDelay() {
        assertEquals(200L + 250L, RsvpTiming.tokenDelayMs(wpm = 300, extraDelayMs = 250L))
        assertEquals(100L + 0L, RsvpTiming.tokenDelayMs(wpm = 600, extraDelayMs = 0L))
    }
}
```

- [ ] **Step 3: Run red.** `./gradlew testDevDebugUnitTest --tests "*RsvpCalibrationScorerTest" --tests "*RsvpTimingTest"`. Expected: unresolved references.

- [ ] **Step 4: Implement `RsvpCalibrationPlan.kt`, `RsvpCalibrationScorer.kt`, `RsvpTiming.kt`**

```kotlin
// app/src/main/java/com/mckimquyen/reader/domain/rsvp/RsvpCalibrationPlan.kt
package com.mckimquyen.reader.domain.rsvp

enum class Confidence { LOW, MEDIUM }

data class RoundResult(val wpm: Int, val correct: Int, val total: Int = 2)

data class RsvpSuggestion(
    val wpm: Int,
    val punctuationDelayScale: Float,
    val confidence: Confidence,
    val passedRounds: List<Int>,
)

object RsvpCalibrationPlan {
    val ROUND_WPMS = listOf(300, 450, 600)
    const val QUESTIONS_PER_ROUND = 2
    const val PASS_CORRECT = 2
    const val FALLBACK_WPM = 250
    const val DEFAULT_WPM = 350
    const val MIN_WPM = 200
    const val MAX_WPM = 900
    const val MIN_DELAY_SCALE = 0.5f
    const val MAX_DELAY_SCALE = 2.0f
    const val DEFAULT_DELAY_SCALE = 1.0f
}
```

```kotlin
// app/src/main/java/com/mckimquyen/reader/domain/rsvp/RsvpCalibrationScorer.kt
package com.mckimquyen.reader.domain.rsvp

/**
 * Pure scoring algorithm for RSVP speed calibration.
 * Translates round results (300/450/600 WPM, 2 questions each) into a suggested speed,
 * a punctuation-delay scale, and a confidence rating (LOW or MEDIUM, never HIGH).
 */
object RsvpCalibrationScorer {

    fun score(results: List<RoundResult>): RsvpSuggestion {
        if (results.isEmpty()) {
            return RsvpSuggestion(
                wpm = RsvpCalibrationPlan.FALLBACK_WPM,
                punctuationDelayScale = RsvpCalibrationPlan.DEFAULT_DELAY_SCALE,
                confidence = Confidence.LOW,
                passedRounds = emptyList(),
            )
        }

        val passedRounds = results
            .filter { it.correct >= RsvpCalibrationPlan.PASS_CORRECT }
            .map { it.wpm }
            .sorted()

        val highestPassedWpm = passedRounds.lastOrNull()
        val suggestedWpm = (highestPassedWpm ?: RsvpCalibrationPlan.FALLBACK_WPM)
            .coerceIn(RsvpCalibrationPlan.MIN_WPM, RsvpCalibrationPlan.MAX_WPM)

        val totalCorrect = results.sumOf { it.correct }
        val totalQuestions = results.sumOf { it.total }.coerceAtLeast(1)
        val accuracy = totalCorrect.toFloat() / totalQuestions

        val rawScale = when {
            totalCorrect == totalQuestions -> 0.85f
            accuracy >= 2f / 3f -> 1.0f
            accuracy >= 0.5f -> 1.25f
            else -> 1.5f
        }
        val scale = rawScale.coerceIn(RsvpCalibrationPlan.MIN_DELAY_SCALE, RsvpCalibrationPlan.MAX_DELAY_SCALE)

        // Monotonic check: every round slower than highestPassedWpm must also have passed
        val isMonotonic = highestPassedWpm != null && results
            .filter { it.wpm < highestPassedWpm }
            .all { it.correct >= RsvpCalibrationPlan.PASS_CORRECT }

        val confidence = if (highestPassedWpm != null && isMonotonic) Confidence.MEDIUM else Confidence.LOW

        return RsvpSuggestion(
            wpm = suggestedWpm,
            punctuationDelayScale = scale,
            confidence = confidence,
            passedRounds = passedRounds,
        )
    }
}
```

```kotlin
// app/src/main/java/com/mckimquyen/reader/domain/rsvp/RsvpTiming.kt
package com.mckimquyen.reader.domain.rsvp

/** Pure per-token RSVP delay calculation matching existing RsvpViewModel playback formula. */
object RsvpTiming {
    fun baseDelayMs(wpm: Int): Long {
        val clamped = wpm.coerceIn(RsvpCalibrationPlan.MIN_WPM, RsvpCalibrationPlan.MAX_WPM)
        return 60_000L / clamped
    }

    fun tokenDelayMs(wpm: Int, extraDelayMs: Long): Long =
        baseDelayMs(wpm) + extraDelayMs
}
```

- [ ] **Step 5: Run green.** `./gradlew testDevDebugUnitTest --tests "*RsvpCalibrationScorerTest" --tests "*RsvpTimingTest" --rerun`; expected all pass.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/com/mckimquyen/reader/domain/rsvp app/src/test/java/com/mckimquyen/reader/domain/rsvp
git commit -m "feat(zen-07): add pure RSVP calibration plan, scorer, and timing"
```

---

### Task 2: `RsvpCalibrationContent` & `RsvpTokenizer` scale support

**Files:**
- Create `app/src/main/java/com/mckimquyen/reader/domain/rsvp/RsvpCalibrationContent.kt`
- Create `app/src/test/java/com/mckimquyen/reader/domain/rsvp/RsvpCalibrationContentTest.kt`
- Modify `app/src/main/java/com/mckimquyen/reader/ui/page/rsvp/RsvpTokenizer.kt`
- Modify `app/src/test/java/com/mckimquyen/reader/ui/page/rsvp/RsvpTokenizerTest.kt`

- [ ] **Step 1: Write failing tests for Content and Tokenizer scale**

In `app/src/test/java/com/mckimquyen/reader/domain/rsvp/RsvpCalibrationContentTest.kt`:

```kotlin
package com.mckimquyen.reader.domain.rsvp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpCalibrationContentTest {

    @Test
    fun enContent_hasThreePassages_eachWithTwoQuestions_fourOptionsEach() {
        val passages = RsvpCalibrationContent.forLanguage("en")
        assertEquals(3, passages.size)
        passages.forEach { p ->
            val words = p.text.split(Regex("\\s+")).filter { it.isNotBlank() }
            assertTrue("word count ${words.size} should be 60..120", words.size in 60..120)
            assertEquals(2, p.questions.size)
            p.questions.forEach { q ->
                assertEquals(4, q.options.size)
                assertTrue("correct index in range", q.correctIndex in 0..3)
                assertEquals("no duplicate options", 4, q.options.distinct().size)
            }
        }
    }

    @Test
    fun viContent_hasThreePassages_eachWithTwoQuestions_fourOptionsEach() {
        val passages = RsvpCalibrationContent.forLanguage("vi")
        assertEquals(3, passages.size)
        passages.forEach { p ->
            val words = p.text.split(Regex("\\s+")).filter { it.isNotBlank() }
            assertTrue("word count ${words.size} should be 60..130", words.size in 60..130)
            assertEquals(2, p.questions.size)
            p.questions.forEach { q ->
                assertEquals(4, q.options.size)
                assertTrue("correct index in range", q.correctIndex in 0..3)
                assertEquals("no duplicate options", 4, q.options.distinct().size)
            }
        }
    }

    @Test
    fun languageFallback_returnsEnForOtherLocales() {
        val de = RsvpCalibrationContent.forLanguage("de")
        val en = RsvpCalibrationContent.forLanguage("en")
        assertEquals(en.first().text, de.first().text)
    }
}
```

Add tests to `app/src/test/java/com/mckimquyen/reader/ui/page/rsvp/RsvpTokenizerTest.kt`:

```kotlin
    @Test
    fun calculateExtraDelayMs_scalesPunctuationOnly_notLongWord() {
        // Base: sentence end = 200 ms. At scale 1.5 -> 300 ms.
        val unscaled = RsvpTokenizer.calculateExtraDelayMs("done.", false, punctuationDelayScale = 1.0f)
        val scaledUp = RsvpTokenizer.calculateExtraDelayMs("done.", false, punctuationDelayScale = 1.5f)
        val scaledDown = RsvpTokenizer.calculateExtraDelayMs("done.", false, punctuationDelayScale = 0.5f)

        assertEquals(200L, unscaled)
        assertEquals(300L, scaledUp)
        assertEquals(100L, scaledDown)

        // Long word delay (40 ms) is NOT scaled
        val longWordUnscaled = RsvpTokenizer.calculateExtraDelayMs("extraordinary", false, punctuationDelayScale = 1.0f)
        val longWordScaled = RsvpTokenizer.calculateExtraDelayMs("extraordinary", false, punctuationDelayScale = 2.0f)
        assertEquals(40L, longWordUnscaled)
        assertEquals(40L, longWordScaled)
    }

    @Test
    fun tokenize_withDefaultScale_matchesExistingDelaysExactly() {
        val tokensDefault = RsvpTokenizer.tokenize("First sentence. Second sentence.")
        val tokensExplicitOne = RsvpTokenizer.tokenize("First sentence. Second sentence.", punctuationDelayScale = 1.0f)

        assertEquals(tokensDefault.map { it.extraDelayMs }, tokensExplicitOne.map { it.extraDelayMs })
    }
```

- [ ] **Step 2: Run red.** `./gradlew testDevDebugUnitTest --tests "*RsvpCalibrationContentTest" --tests "*RsvpTokenizerTest"`.

- [ ] **Step 3: Implement `RsvpCalibrationContent.kt` and update `RsvpTokenizer.kt`**

Create `app/src/main/java/com/mckimquyen/reader/domain/rsvp/RsvpCalibrationContent.kt` (this file also defines `CalibrationQuestion` and `CalibrationPassage`):

```kotlin
package com.mckimquyen.reader.domain.rsvp

data class CalibrationQuestion(val prompt: String, val options: List<String>, val correctIndex: Int)

data class CalibrationPassage(val text: String, val questions: List<CalibrationQuestion>)

/**
 * Static reading passages for the speed calibration. They live in Kotlin rather than strings.xml because every
 * question belongs to one specific passage; only the UI around them is localized. Other languages read the English set.
 */
object RsvpCalibrationContent {

    fun forLanguage(languageTag: String): List<CalibrationPassage> =
        if (languageTag.lowercase().startsWith("vi")) VI else EN

    private val EN = listOf(
        CalibrationPassage(
            text = "Marta runs the only bakery in the village of Alder Bay. She wakes at four every morning and lights the stone oven before the streets are awake. By six she has baked thirty-six loaves of rye bread and twelve trays of cardamom buns. She loads them onto a blue cart and pulls it to the harbor, where the fishing crews wait. Most customers pay with coins, but the ferry captain always pays with a jar of pickled plums. Marta keeps every jar on a high shelf beside the clock.",
            questions = listOf(
                CalibrationQuestion("What color is the cart Marta uses?", listOf("Red", "Blue", "Green", "Yellow"), 1),
                CalibrationQuestion("How does the ferry captain pay?", listOf("With coins", "With fresh fish", "With a jar of pickled plums", "With a promise"), 2),
            ),
        ),
        CalibrationPassage(
            text = "Tomas repairs old clocks in a narrow shop above a tailor. On Tuesdays he opens at noon, and on all other days at nine. His favorite clock is a brass one with a small silver bird that pops out on the hour. Last winter a student brought it in because the bird had stopped singing. Tomas found a bent spring no wider than a hair and fixed it in forty minutes. The student paid with a hand-knitted scarf, which Tomas still wears when the shop is cold.",
            questions = listOf(
                CalibrationQuestion("When does Tomas open the shop on Tuesdays?", listOf("At nine", "At noon", "At six", "At three"), 1),
                CalibrationQuestion("What did the student give Tomas as payment?", listOf("A scarf", "A book", "A silver coin", "A bird"), 0),
            ),
        ),
        CalibrationPassage(
            text = "A group of seven hikers set out from Pine Ridge at dawn to cross the northern pass. The first hour was easy, following a wide path beside a quiet stream. Then the trail climbed sharply and the wind grew colder. Around noon they stopped at a stone hut and shared a pot of lentil soup. Nobody wanted to leave, but clouds were gathering in the west. They reached the far side at five, tired but pleased, and counted every member twice before pitching their tents beside a small lake.",
            questions = listOf(
                CalibrationQuestion("How many hikers were in the group?", listOf("Five", "Six", "Seven", "Nine"), 2),
                CalibrationQuestion("What did the hikers eat at the stone hut?", listOf("Lentil soup", "Bread and cheese", "Rice", "Fresh berries"), 0),
            ),
        ),
    )

    private val VI = listOf(
        CalibrationPassage(
            text = "Bà Lan bán bánh mì ở chợ nhỏ cuối làng Cồn Sơn. Mỗi sáng bà thức dậy lúc bốn giờ để nhóm lò than và nhào bột. Đến sáu giờ bà đã nướng xong ba mươi sáu ổ bánh mì và mười hai khay bánh bông lan nhỏ. Bà xếp tất cả lên chiếc xe đẩy màu xanh rồi đẩy ra bến sông, nơi các ghe chài đang chờ. Phần lớn khách trả bằng tiền lẻ, nhưng ông lái đò lúc nào cũng trả bằng một hũ mận muối. Bà để mọi hũ lên chiếc kệ cao cạnh đồng hồ.",
            questions = listOf(
                CalibrationQuestion("Chiếc xe đẩy của bà Lan có màu gì?", listOf("Màu đỏ", "Màu xanh", "Màu vàng", "Màu trắng"), 1),
                CalibrationQuestion("Ông lái đò trả tiền bánh bằng gì?", listOf("Bằng tiền lẻ", "Bằng cá tươi", "Bằng một hũ mận muối", "Bằng lời hứa"), 2),
            ),
        ),
        CalibrationPassage(
            text = "Chú Tâm sửa đồng hồ cũ trong một cửa tiệm hẹp nằm trên tiệm may. Thứ ba chú mở cửa lúc mười hai giờ trưa, còn các ngày khác chú mở lúc chín giờ. Chiếc đồng hồ chú thích nhất làm bằng đồng, có một chú chim bạc nhỏ nhảy ra mỗi khi đến giờ. Mùa đông năm ngoái, một cậu sinh viên mang nó đến vì chim đã thôi hót. Chú Tâm tìm thấy một chiếc lò xo cong nhỏ hơn sợi tóc và sửa xong trong bốn mươi phút. Cậu sinh viên trả công bằng một chiếc khăn len tự đan, chú vẫn quàng mỗi khi cửa tiệm lạnh.",
            questions = listOf(
                CalibrationQuestion("Thứ ba chú Tâm mở cửa lúc mấy giờ?", listOf("Chín giờ", "Mười hai giờ trưa", "Sáu giờ", "Ba giờ"), 1),
                CalibrationQuestion("Cậu sinh viên đã trả công cho chú Tâm bằng gì?", listOf("Một chiếc khăn len", "Một quyển sách", "Một đồng xu bạc", "Một chú chim"), 0),
            ),
        ),
        CalibrationPassage(
            text = "Một nhóm bảy người leo núi xuất phát từ Đồi Thông lúc rạng sáng để vượt qua con đèo phía bắc. Giờ đầu tiên rất dễ đi, họ men theo con đường rộng cạnh một dòng suối yên tĩnh. Sau đó đường dốc lên và gió trở nên lạnh hơn. Khoảng giữa trưa họ dừng ở một túp lều đá và chia nhau nồi canh đậu lăng. Không ai muốn rời đi, nhưng mây đang kéo đến từ phía tây. Họ đến được bên kia lúc năm giờ, mệt nhưng vui, và đếm đủ từng người hai lần trước khi dựng lều bên một hồ nhỏ.",
            questions = listOf(
                CalibrationQuestion("Nhóm leo núi có bao nhiêu người?", listOf("Năm người", "Sáu người", "Bảy người", "Chín người"), 2),
                CalibrationQuestion("Nhóm đã ăn gì ở túp lều đá?", listOf("Canh đậu lăng", "Bánh mì với phô mai", "Cơm", "Quả mọng tươi"), 0),
            ),
        ),
    )
}
```

Modify `RsvpTokenizer.kt`:

```kotlin
    fun calculateExtraDelayMs(
        word: String,
        isParagraphEnd: Boolean,
        punctuationDelayScale: Float = 1.0f,
    ): Long {
        var punctuationDelay = 0L
        val trimmed = word.trim()
        if (isParagraphEnd) {
            punctuationDelay += PARAGRAPH_END_DELAY_MS
        }
        if (trimmed.endsWithAny(sentenceEndChars)) {
            punctuationDelay += SENTENCE_END_DELAY_MS
        } else if (trimmed.endsWithAny(clauseEndChars)) {
            punctuationDelay += CLAUSE_END_DELAY_MS
        }
        var delay = (punctuationDelay * punctuationDelayScale).toLong()
        if (trimmed.length > LONG_WORD_THRESHOLD) {
            delay += LONG_WORD_DELAY_MS
        }
        return delay
    }

    fun tokenize(content: String, punctuationDelayScale: Float = 1.0f): List<RsvpToken> {
        // pass punctuationDelayScale through to calculateExtraDelayMs
```

- [ ] **Step 4: Run green.** `./gradlew testDevDebugUnitTest --tests "*RsvpCalibrationContentTest" --tests "*RsvpTokenizerTest" --rerun`; expected all pass.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/mckimquyen/reader/domain/rsvp/RsvpCalibrationContent.kt app/src/main/java/com/mckimquyen/reader/ui/page/rsvp/RsvpTokenizer.kt app/src/test/java/com/mckimquyen/reader/domain/rsvp/RsvpCalibrationContentTest.kt app/src/test/java/com/mckimquyen/reader/ui/page/rsvp/RsvpTokenizerTest.kt
git commit -m "feat(zen-07): add calibration content and punctuation delay scale in tokenizer"
```

---

### Task 3: DataStore keys, `RsvpCalibrationStore`, and 14 strings across 38 locales

**Files:**
- Modify `app/src/main/java/com/mckimquyen/reader/ui/ext/ExtDataStore.kt`
- Create `app/src/main/java/com/mckimquyen/reader/ui/page/rsvp/RsvpCalibrationStore.kt`
- Create `app/src/main/java/com/mckimquyen/reader/infrastructure/di/RsvpCalibrationModule.kt`
- Create `app/src/test/java/com/mckimquyen/reader/ui/page/rsvp/RsvpCalibrationStoreTest.kt`
- Modify all 38 `app/src/main/res/values*/strings.xml`

- [ ] **Step 1: Write failing tests for `RsvpCalibrationStore`**

In `app/src/test/java/com/mckimquyen/reader/ui/page/rsvp/RsvpCalibrationStoreTest.kt`:

```kotlin
package com.mckimquyen.reader.ui.page.rsvp

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RsvpCalibrationStoreTest {
    private lateinit var context: Context
    private lateinit var store: RsvpCalibrationStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = DataStoreRsvpCalibrationStore(context)
    }

    @Test
    fun uncalibrated_returnsNull() = runBlocking {
        // Initial state before calibration
        assertNull(store.read())
    }

    @Test
    fun write_and_read_roundtripsCorrectly() = runBlocking {
        store.write(wpm = 450, punctuationDelayScale = 0.85f)
        val read = store.read()
        assertEquals(450, read?.wpm)
        assertEquals(0.85f, read?.punctuationDelayScale ?: 0f, 0.0001f)
    }
}
```

- [ ] **Step 2: Run red.** `./gradlew testDevDebugUnitTest --tests "*RsvpCalibrationStoreTest"`.

- [ ] **Step 3: Implement DataStore keys, store, and Hilt module**

In `ExtDataStore.kt`:
Import `floatPreferencesKey`. Add:

```kotlin
    object RsvpSuggestedWpm : DataStoreKeys<Int>() {
        override val key: Preferences.Key<Int>
            get() = intPreferencesKey("rsvp_suggested_wpm")
    }

    object RsvpPunctuationDelayScale : DataStoreKeys<Float>() {
        override val key: Preferences.Key<Float>
            get() = floatPreferencesKey("rsvp_punctuation_delay_scale")
    }
```

Create `app/src/main/java/com/mckimquyen/reader/ui/page/rsvp/RsvpCalibrationStore.kt`:

```kotlin
package com.mckimquyen.reader.ui.page.rsvp

import android.content.Context
import com.mckimquyen.reader.ui.ext.DataStoreKeys
import com.mckimquyen.reader.ui.ext.dataStore
import com.mckimquyen.reader.ui.ext.put
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class SavedRsvpSettings(val wpm: Int, val punctuationDelayScale: Float)

interface RsvpCalibrationStore {
    suspend fun read(): SavedRsvpSettings?
    suspend fun write(wpm: Int, punctuationDelayScale: Float)
}

class DataStoreRsvpCalibrationStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : RsvpCalibrationStore {
    override suspend fun read(): SavedRsvpSettings? {
        val prefs = context.dataStore.data.first()
        val wpm = prefs[DataStoreKeys.RsvpSuggestedWpm.key] ?: return null
        val scale = prefs[DataStoreKeys.RsvpPunctuationDelayScale.key] ?: 1.0f
        return SavedRsvpSettings(wpm = wpm, punctuationDelayScale = scale)
    }

    override suspend fun write(wpm: Int, punctuationDelayScale: Float) {
        context.dataStore.put(DataStoreKeys.RsvpSuggestedWpm, wpm)
        context.dataStore.put(DataStoreKeys.RsvpPunctuationDelayScale, punctuationDelayScale)
    }
}
```

Create `app/src/main/java/com/mckimquyen/reader/infrastructure/di/RsvpCalibrationModule.kt`:

```kotlin
package com.mckimquyen.reader.infrastructure.di

import com.mckimquyen.reader.ui.page.rsvp.DataStoreRsvpCalibrationStore
import com.mckimquyen.reader.ui.page.rsvp.RsvpCalibrationStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RsvpCalibrationModule {
    @Binds
    abstract fun bindRsvpCalibrationStore(impl: DataStoreRsvpCalibrationStore): RsvpCalibrationStore
}
```

- [ ] **Step 4: Add 14 strings in all 38 locales** with Python script:
  - English:
    - `zen_calibration_title`: "Reading Speed Calibration"
    - `zen_calibration_train_action`: "Calibrate Speed"
    - `zen_calibration_intro_title`: "Find Your Optimal Speed"
    - `zen_calibration_intro_desc`: "Read 3 short passages at increasing speeds (300, 450, 600 WPM), then answer 2 questions per passage. Takes ~2 minutes."
    - `zen_calibration_start`: "Start Calibration"
    - `zen_calibration_round_header`: "Round %1$d of 3 (%2$d WPM)"
    - `zen_calibration_questions_header`: "Comprehension Check"
    - `zen_calibration_result_title`: "Calibration Results"
    - `zen_calibration_suggested_wpm`: "Suggested speed: ~%1$d WPM"
    - `zen_calibration_limitation`: "This is a rough estimate from 6 questions, not a measure of deep comprehension. You can always adjust speed manually."
    - `zen_calibration_low_confidence_note`: "Your answers varied across speeds; try reading at your own pace and recalibrate later."
    - `zen_calibration_apply`: "Apply"
    - `zen_calibration_skip`: "Skip"
    - `zen_calibration_settings_desc`: "Calibrate your reading speed across 3 short rounds"
  - Vietnamese (`values-vi`):
    - `zen_calibration_title`: "Hiệu chỉnh tốc độ đọc"
    - `zen_calibration_train_action`: "Hiệu chỉnh tốc độ"
    - `zen_calibration_intro_title`: "Tìm tốc độ đọc tối ưu"
    - `zen_calibration_intro_desc`: "Đọc 3 đoạn văn ngắn ở các tốc độ tăng dần (300, 450, 600 WPM), sau đó trả lời 2 câu hỏi mỗi đoạn. Mất khoảng 2 phút."
    - `zen_calibration_start`: "Bắt đầu hiệu chỉnh"
    - `zen_calibration_round_header`: "Vòng %1$d / 3 (%2$d WPM)"
    - `zen_calibration_questions_header`: "Kiểm tra mức độ hiểu"
    - `zen_calibration_result_title`: "Kết quả hiệu chỉnh"
    - `zen_calibration_suggested_wpm`: "Tốc độ đề xuất: ~%1$d WPM"
    - `zen_calibration_limitation`: "Đây là ước lượng thô từ 6 câu hỏi, không phải thước đo hiểu bài sâu. Bạn luôn có thể chỉnh tốc độ thủ công."
    - `zen_calibration_low_confidence_note`: "Kết quả của bạn chưa ổn định qua các mốc tốc độ; hãy đọc theo nhịp tự nhiên và hiệu chỉnh lại sau."
    - `zen_calibration_apply`: "Áp dụng"
    - `zen_calibration_skip`: "Bỏ qua"
    - `zen_calibration_settings_desc`: "Hiệu chỉnh tốc độ đọc qua 3 vòng ngắn"

- [ ] **Step 5: Verify parity.** `./gradlew testDevDebugUnitTest --tests "*StringResourceParityTest" --tests "*RsvpCalibrationStoreTest" --rerun`; expected all pass.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/ext/ExtDataStore.kt app/src/main/java/com/mckimquyen/reader/ui/page/rsvp/RsvpCalibrationStore.kt app/src/main/java/com/mckimquyen/reader/infrastructure/di/RsvpCalibrationModule.kt app/src/test/java/com/mckimquyen/reader/ui/page/rsvp/RsvpCalibrationStoreTest.kt app/src/main/res
git commit -m "feat(zen-07): add RsvpCalibrationStore, DataStore keys, and strings across 38 locales"
```

---

### Task 4: `RsvpViewModel` integration & `RsvpCalibrationViewModel`

**Files:**
- Modify `app/src/main/java/com/mckimquyen/reader/ui/page/rsvp/RsvpViewModel.kt`
- Create `app/src/main/java/com/mckimquyen/reader/ui/page/rsvp/RsvpCalibrationViewModel.kt`
- Modify `app/src/test/java/com/mckimquyen/reader/ui/page/rsvp/RsvpViewModelTest.kt`
- Create `app/src/test/java/com/mckimquyen/reader/ui/page/rsvp/RsvpCalibrationViewModelTest.kt`

- [ ] **Step 1: Write failing tests for `RsvpViewModel` calibration store support**

In `RsvpViewModelTest.kt`:

```kotlin
    @Test
    fun loadContent_withCalibrationStore_appliesSavedWpm() = runTest(testDispatcher) {
        val fakeStore = object : RsvpCalibrationStore {
            override suspend fun read(): SavedRsvpSettings = SavedRsvpSettings(wpm = 500, punctuationDelayScale = 0.85f)
            override suspend fun write(wpm: Int, punctuationDelayScale: Float) {}
        }
        val vm = RsvpViewModel(fakeStore as RsvpCalibrationStore?)
        vm.loadContent("One two three")
        advanceUntilIdle()

        assertEquals(500, vm.uiState.value.wpm)
    }

    @Test
    fun manualSetWpm_overridesSavedWpm() = runTest(testDispatcher) {
        val fakeStore = object : RsvpCalibrationStore {
            override suspend fun read(): SavedRsvpSettings = SavedRsvpSettings(wpm = 500, punctuationDelayScale = 0.85f)
            override suspend fun write(wpm: Int, punctuationDelayScale: Float) {}
        }
        val vm = RsvpViewModel(fakeStore as RsvpCalibrationStore?)
        vm.loadContent("One two three")
        advanceUntilIdle()
        vm.setWpm(400)

        assertEquals(400, vm.uiState.value.wpm)
    }

    @Test
    fun noStore_keepsDefault350Wpm() {
        val vm = RsvpViewModel()
        vm.loadContent("One two three")
        assertEquals(350, vm.uiState.value.wpm)
    }
```

Write `RsvpCalibrationViewModelTest.kt`:

```kotlin
package com.mckimquyen.reader.ui.page.rsvp

import com.mckimquyen.reader.domain.rsvp.RsvpCalibrationPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RsvpCalibrationViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val savedWrites = mutableListOf<SavedRsvpSettings>()
    private val fakeStore = object : RsvpCalibrationStore {
        override suspend fun read(): SavedRsvpSettings? = savedWrites.lastOrNull()
        override suspend fun write(wpm: Int, punctuationDelayScale: Float) {
            savedWrites.add(SavedRsvpSettings(wpm, punctuationDelayScale))
        }
    }

    @Before fun setUp() {
        Dispatchers.setMain(testDispatcher)
        savedWrites.clear()
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(lang: String = "en") = RsvpCalibrationViewModel(
        calibrationStore = fakeStore,
        defaultDispatcher = testDispatcher,
        ioDispatcher = testDispatcher,
        languageTag = { lang },
    )

    @Test fun startsAtIntro() {
        val vm = createViewModel()
        assertEquals(CalibrationStep.Intro, vm.step.value)
    }

    @Test fun startCalibration_movesToReadingRoundZeroAt300Wpm() {
        val vm = createViewModel()
        vm.startCalibration()
        val step = vm.step.value as CalibrationStep.Reading
        assertEquals(0, step.roundIndex)
        assertEquals(300, step.wpm)
    }

    @Test fun passageFinished_movesToQuestionsForThatRound() {
        val vm = createViewModel()
        vm.startCalibration()
        vm.onPassageFinished()
        val step = vm.step.value as CalibrationStep.Questions
        assertEquals(0, step.roundIndex)
        assertEquals(2, step.questions.size)
    }

    @Test fun submittingRoundAnswers_movesToNextReadingRound() {
        val vm = createViewModel()
        vm.startCalibration()
        vm.onPassageFinished()
        vm.submitAnswers(listOf(1, 2)) // answer round 0
        val step = vm.step.value as CalibrationStep.Reading
        assertEquals(1, step.roundIndex)
        assertEquals(450, step.wpm)
    }

    @Test fun fullThreeRounds_movesToResult_andApplySavesToStore() = runTest(testDispatcher) {
        val vm = createViewModel("en")
        vm.startCalibration()

        // Round 0 (300 WPM): answers (1, 2) are correct in EN
        vm.onPassageFinished()
        vm.submitAnswers(listOf(1, 2))

        // Round 1 (450 WPM): answers (1, 0) are correct in EN
        vm.onPassageFinished()
        vm.submitAnswers(listOf(1, 0))

        // Round 2 (600 WPM): answers (2, 0) are correct in EN
        vm.onPassageFinished()
        vm.submitAnswers(listOf(2, 0))

        val step = vm.step.value as CalibrationStep.Result
        assertEquals(600, step.suggestion.wpm)

        vm.applySuggestion()
        advanceUntilIdle()

        assertEquals(1, savedWrites.size)
        assertEquals(600, savedWrites.first().wpm)
    }

    @Test fun skip_savesNothingToStore() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.startCalibration()
        vm.cancel()
        advanceUntilIdle()

        assertTrue(savedWrites.isEmpty())
    }
}
```

- [ ] **Step 2: Run red.** `./gradlew testDevDebugUnitTest --tests "*RsvpCalibrationViewModelTest" --tests "*RsvpViewModelTest"`.

- [ ] **Step 3: Update `RsvpViewModel.kt` and create `RsvpCalibrationViewModel.kt`**

Edit `RsvpViewModel.kt`. Constructors (Hilt cannot inject a nullable defaulted parameter, so there are three):

```kotlin
@HiltViewModel
class RsvpViewModel internal constructor(
    private val calibrationStore: RsvpCalibrationStore?,
) : ViewModel() {
    @Inject constructor(store: RsvpCalibrationStore) : this(store as RsvpCalibrationStore?)

    /** Used by tests and previews that run without persistence. */
    constructor() : this(null as RsvpCalibrationStore?)

    private var punctuationDelayScale = RsvpCalibrationPlan.DEFAULT_DELAY_SCALE
    private var wpmChosenByUser = false
```

Replace `loadContent` and `setWpm`, and use `RsvpTiming` in `play()`:

```kotlin
    fun loadContent(content: String) {
        pause()
        val tokens = RsvpTokenizer.tokenize(content, punctuationDelayScale)
        _uiState.update { it.copy(tokens = tokens, currentIndex = 0, isPlaying = false, isCompleted = false) }
        applySavedSettings(content)
    }

    /** Applies the calibrated speed once per load, unless the user already moved the slider this session. */
    private fun applySavedSettings(content: String) {
        val store = calibrationStore ?: return
        viewModelScope.launch {
            val saved = try {
                store.read()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } ?: return@launch
            punctuationDelayScale = saved.punctuationDelayScale
            val tokens = RsvpTokenizer.tokenize(content, punctuationDelayScale)
            _uiState.update {
                it.copy(
                    tokens = tokens,
                    wpm = if (wpmChosenByUser) it.wpm else saved.wpm.coerceIn(RsvpCalibrationPlan.MIN_WPM, RsvpCalibrationPlan.MAX_WPM),
                )
            }
        }
    }

    fun setWpm(wpm: Int) {
        wpmChosenByUser = true
        _uiState.update { it.copy(wpm = wpm.coerceIn(RsvpCalibrationPlan.MIN_WPM, RsvpCalibrationPlan.MAX_WPM)) }
    }
```

In `play()` replace the two delay lines (`val baseDelay ...` and `val totalDelay ...`) with:

```kotlin
                val totalDelay = RsvpTiming.tokenDelayMs(_uiState.value.wpm, currentToken.extraDelayMs)
```

Add imports: `com.mckimquyen.reader.domain.rsvp.RsvpCalibrationPlan`, `com.mckimquyen.reader.domain.rsvp.RsvpTiming`, `kotlinx.coroutines.CancellationException`.

Create `RsvpCalibrationViewModel.kt`:

```kotlin
package com.mckimquyen.reader.ui.page.rsvp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mckimquyen.reader.domain.rsvp.CalibrationQuestion
import com.mckimquyen.reader.domain.rsvp.RoundResult
import com.mckimquyen.reader.domain.rsvp.RsvpCalibrationContent
import com.mckimquyen.reader.domain.rsvp.RsvpCalibrationPlan
import com.mckimquyen.reader.domain.rsvp.RsvpCalibrationScorer
import com.mckimquyen.reader.domain.rsvp.RsvpSuggestion
import com.mckimquyen.reader.infrastructure.di.DefaultDispatcher
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

sealed interface CalibrationStep {
    object Intro : CalibrationStep
    data class Reading(val roundIndex: Int, val wpm: Int, val text: String) : CalibrationStep
    data class Questions(val roundIndex: Int, val wpm: Int, val questions: List<CalibrationQuestion>) : CalibrationStep
    data class Result(val suggestion: RsvpSuggestion) : CalibrationStep
    object Finished : CalibrationStep
}

/**
 * Three-round speed calibration. Quitting at any point saves nothing; only [applySuggestion] writes to the store.
 */
@HiltViewModel
class RsvpCalibrationViewModel internal constructor(
    private val calibrationStore: RsvpCalibrationStore,
    @Suppress("unused") private val defaultDispatcher: CoroutineDispatcher,
    private val ioDispatcher: CoroutineDispatcher,
    private val languageTag: () -> String,
) : ViewModel() {

    /** Hilt entry point: Hilt cannot provide a function-typed parameter, so the language lookup is defaulted here. */
    @Inject constructor(
        calibrationStore: RsvpCalibrationStore,
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
        @IODispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(calibrationStore, defaultDispatcher, ioDispatcher, { Locale.getDefault().language })

    private val _step = MutableStateFlow<CalibrationStep>(CalibrationStep.Intro)
    val step: StateFlow<CalibrationStep> = _step.asStateFlow()

    private val results = mutableListOf<RoundResult>()
    private var lastSuggestion: RsvpSuggestion? = null

    private val passages get() = RsvpCalibrationContent.forLanguage(languageTag())

    fun startCalibration() {
        results.clear()
        lastSuggestion = null
        showReading(0)
    }

    fun onPassageFinished() {
        val reading = _step.value as? CalibrationStep.Reading ?: return
        val passage = passages[reading.roundIndex]
        _step.value = CalibrationStep.Questions(reading.roundIndex, reading.wpm, passage.questions)
    }

    /** [selectedIndices] holds one chosen option index per question of the current round, in order. */
    fun submitAnswers(selectedIndices: List<Int>) {
        val questions = _step.value as? CalibrationStep.Questions ?: return
        val correct = questions.questions.indices.count { i -> selectedIndices.getOrNull(i) == questions.questions[i].correctIndex }
        results += RoundResult(wpm = questions.wpm, correct = correct, total = questions.questions.size)
        val next = questions.roundIndex + 1
        if (next < RsvpCalibrationPlan.ROUND_WPMS.size) {
            showReading(next)
        } else {
            val suggestion = RsvpCalibrationScorer.score(results.toList())
            lastSuggestion = suggestion
            _step.value = CalibrationStep.Result(suggestion)
        }
    }

    /** Saves the suggestion and reports the speed to apply. Does nothing outside the Result step. */
    fun applySuggestion(onApplied: (RsvpSuggestion) -> Unit = {}) {
        val suggestion = (_step.value as? CalibrationStep.Result)?.suggestion ?: return
        viewModelScope.launch {
            withContext(ioDispatcher) { calibrationStore.write(suggestion.wpm, suggestion.punctuationDelayScale) }
            _step.value = CalibrationStep.Finished
            onApplied(suggestion)
        }
    }

    /** Leaves without saving anything. */
    fun cancel() {
        results.clear()
        lastSuggestion = null
        _step.value = CalibrationStep.Intro
    }

    private fun showReading(roundIndex: Int) {
        val wpm = RsvpCalibrationPlan.ROUND_WPMS[roundIndex]
        _step.value = CalibrationStep.Reading(roundIndex, wpm, passages[roundIndex].text)
    }
}
```

- [ ] **Step 4: Run green.** `./gradlew testDevDebugUnitTest --tests "*RsvpCalibrationViewModelTest" --tests "*RsvpViewModelTest" --rerun`.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/page/rsvp/RsvpViewModel.kt app/src/main/java/com/mckimquyen/reader/ui/page/rsvp/RsvpCalibrationViewModel.kt app/src/test/java/com/mckimquyen/reader/ui/page/rsvp/RsvpViewModelTest.kt app/src/test/java/com/mckimquyen/reader/ui/page/rsvp/RsvpCalibrationViewModelTest.kt
git commit -m "feat(zen-07): integrate calibration store into RsvpViewModel and build RsvpCalibrationViewModel"
```

---

### Task 5: `RsvpCalibrationDialog`, entry points in `RsvpReaderDialog` and `ZenSettingsPage`

**Files:**
- Create `app/src/main/java/com/mckimquyen/reader/ui/page/rsvp/RsvpCalibrationDialog.kt`
- Create `app/src/test/java/com/mckimquyen/reader/ui/page/rsvp/RsvpCalibrationDialogTest.kt`
- Modify `app/src/main/java/com/mckimquyen/reader/ui/page/rsvp/RsvpReaderDialog.kt`
- Modify `app/src/main/java/com/mckimquyen/reader/ui/page/setting/zen/ZenSettingsPage.kt`

- [ ] **Step 1: Write failing Compose tests in `RsvpCalibrationDialogTest.kt`**

```kotlin
package com.mckimquyen.reader.ui.page.rsvp

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mckimquyen.reader.domain.rsvp.CalibrationPassage
import com.mckimquyen.reader.domain.rsvp.CalibrationQuestion
import com.mckimquyen.reader.domain.rsvp.Confidence
import com.mckimquyen.reader.domain.rsvp.RsvpSuggestion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RsvpCalibrationDialogTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun intro_showsExplanationAndStartButton() {
        var started = 0
        compose.setContent {
            RsvpCalibrationScreen(
                step = CalibrationStep.Intro,
                onStart = { started++ },
                onPassageFinished = {},
                onSubmitAnswers = {},
                onApply = {},
                onSkip = {},
                onDismiss = {},
            )
        }
        compose.onNodeWithTag(ZEN_CALIBRATION_START_TAG).assertIsDisplayed()
        compose.onNodeWithTag(ZEN_CALIBRATION_START_TAG).performClick()
        assertEquals(1, started)
    }

    @Test
    fun questions_rendersPromptAndOptions() {
        var answered = false
        val questions = listOf(
            CalibrationQuestion("Question 1", listOf("A", "B", "C", "D"), 0),
            CalibrationQuestion("Question 2", listOf("W", "X", "Y", "Z"), 1),
        )
        compose.setContent {
            RsvpCalibrationScreen(
                step = CalibrationStep.Questions(roundIndex = 0, wpm = 300, questions = questions),
                onStart = {},
                onPassageFinished = {},
                onSubmitAnswers = { answered = true },
                onApply = {},
                onSkip = {},
                onDismiss = {},
            )
        }
        compose.onNodeWithText("Question 1").assertIsDisplayed()
        compose.onNodeWithText("Question 2").assertIsDisplayed()
    }

    @Test
    fun result_showsSuggestedWpm_andLimitation() {
        var applied = 0
        var skipped = 0
        val suggestion = RsvpSuggestion(wpm = 450, punctuationDelayScale = 1.0f, confidence = Confidence.MEDIUM, passedRounds = listOf(300, 450))
        compose.setContent {
            RsvpCalibrationScreen(
                step = CalibrationStep.Result(suggestion),
                onStart = {},
                onPassageFinished = {},
                onSubmitAnswers = {},
                onApply = { applied++ },
                onSkip = { skipped++ },
                onDismiss = {},
            )
        }
        compose.onNodeWithText("450", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(ZEN_CALIBRATION_APPLY_TAG).performClick()
        compose.onNodeWithTag(ZEN_CALIBRATION_SKIP_TAG).performClick()
        assertEquals(1, applied)
        assertEquals(1, skipped)
    }
}
```

- [ ] **Step 2: Run red.** `./gradlew testDevDebugUnitTest --tests "*RsvpCalibrationDialogTest"`.

- [ ] **Step 3: Implement `RsvpCalibrationDialog.kt`**

Create `app/src/main/java/com/mckimquyen/reader/ui/page/rsvp/RsvpCalibrationDialog.kt`:

```kotlin
package com.mckimquyen.reader.ui.page.rsvp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.rsvp.CalibrationQuestion
import com.mckimquyen.reader.domain.rsvp.Confidence
import com.mckimquyen.reader.domain.rsvp.RsvpCalibrationPlan
import com.mckimquyen.reader.domain.rsvp.RsvpSuggestion
import com.mckimquyen.reader.domain.rsvp.RsvpTiming
import com.mckimquyen.reader.ui.ext.DialogEdgeToEdge
import kotlinx.coroutines.delay

const val ZEN_CALIBRATION_START_TAG = "zen_calibration_start_btn"
const val ZEN_CALIBRATION_APPLY_TAG = "zen_calibration_apply_btn"
const val ZEN_CALIBRATION_SKIP_TAG = "zen_calibration_skip_btn"
const val ZEN_CALIBRATION_TRAIN_BUTTON_TAG = "zen_calibration_train_btn"
const val ZEN_CALIBRATION_OPTION_TAG_PREFIX = "zen_calibration_option_"
const val ZEN_CALIBRATION_SUBMIT_TAG = "zen_calibration_submit_btn"

private const val MIN_TOUCH_TARGET_DP = 48

@Composable
fun RsvpCalibrationDialog(
    viewModel: RsvpCalibrationViewModel,
    onApplied: (RsvpSuggestion) -> Unit,
    onDismiss: () -> Unit,
) {
    val step by viewModel.step.collectAsState()

    LaunchedEffect(step) {
        if (step is CalibrationStep.Finished) onDismiss()
    }

    Dialog(
        onDismissRequest = { viewModel.cancel(); onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        DialogEdgeToEdge()
        RsvpCalibrationScreen(
            step = step,
            onStart = viewModel::startCalibration,
            onPassageFinished = viewModel::onPassageFinished,
            onSubmitAnswers = viewModel::submitAnswers,
            onApply = { viewModel.applySuggestion(onApplied) },
            onSkip = { viewModel.cancel(); onDismiss() },
            onDismiss = { viewModel.cancel(); onDismiss() },
        )
    }
}

@Composable
fun RsvpCalibrationScreen(
    step: CalibrationStep,
    onStart: () -> Unit,
    onPassageFinished: () -> Unit,
    onSubmitAnswers: (List<Int>) -> Unit,
    onApply: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize().safeDrawingPadding(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Rounded.Close, contentDescription = stringResource(R.string.close))
                }
                Text(
                    text = stringResource(R.string.zen_calibration_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(MIN_TOUCH_TARGET_DP.dp))
            }

            Spacer(Modifier.height(16.dp))

            when (step) {
                CalibrationStep.Intro -> IntroContent(onStart)
                is CalibrationStep.Reading -> ReadingContent(step, onPassageFinished)
                is CalibrationStep.Questions -> QuestionsContent(step, onSubmitAnswers)
                is CalibrationStep.Result -> ResultContent(step.suggestion, onApply, onSkip)
                CalibrationStep.Finished -> Unit
            }
        }
    }
}

@Composable
private fun IntroContent(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "⚡ " + stringResource(R.string.zen_calibration_intro_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.zen_calibration_intro_desc),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.zen_calibration_limitation),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET_DP.dp).testTag(ZEN_CALIBRATION_START_TAG),
        ) {
            Text(stringResource(R.string.zen_calibration_start))
        }
    }
}

@Composable
private fun ReadingContent(step: CalibrationStep.Reading, onFinished: () -> Unit) {
    val tokens = remember(step.text) { RsvpTokenizer.tokenize(step.text) }
    var currentIndex by remember(step.text) { mutableIntStateOf(0) }

    LaunchedEffect(step.text, step.wpm) {
        if (tokens.isEmpty()) {
            onFinished()
            return@LaunchedEffect
        }
        while (currentIndex < tokens.size) {
            val token = tokens[currentIndex]
            val delay = RsvpTiming.tokenDelayMs(step.wpm, token.extraDelayMs)
            delay(delay)
            currentIndex++
        }
        delay(400)
        onFinished()
    }

    val currentToken = tokens.getOrNull(currentIndex)
    val progress = if (tokens.isEmpty()) 1f else (currentIndex.toFloat() / tokens.size.coerceAtLeast(1))

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(
                text = stringResource(R.string.zen_calibration_round_header, step.roundIndex + 1, step.wpm),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
        }

        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (currentToken != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = currentToken.prefix,
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = currentToken.orpChar.toString(),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = currentToken.suffix,
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        Text(
            text = "${step.wpm} WPM",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun QuestionsContent(
    step: CalibrationStep.Questions,
    onSubmit: (List<Int>) -> Unit,
) {
    val selectedAnswers = remember(step.roundIndex) {
        mutableStateListOf<Int>().apply { repeat(step.questions.size) { add(-1) } }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = stringResource(R.string.zen_calibration_questions_header),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))

            step.questions.forEachIndexed { qIndex, question ->
                Text(
                    text = "${qIndex + 1}. ${question.prompt}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                question.options.forEachIndexed { oIndex, option ->
                    val isSelected = selectedAnswers[qIndex] == oIndex
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .heightIn(min = MIN_TOUCH_TARGET_DP.dp)
                            .testTag("${ZEN_CALIBRATION_OPTION_TAG_PREFIX}${qIndex}_$oIndex")
                            .clickable { selectedAnswers[qIndex] = oIndex },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = isSelected, onClick = { selectedAnswers[qIndex] = oIndex })
                            Text(text = option, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        val allAnswered = selectedAnswers.all { it >= 0 }
        Button(
            onClick = { onSubmit(selectedAnswers.toList()) },
            enabled = allAnswered,
            modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET_DP.dp).testTag(ZEN_CALIBRATION_SUBMIT_TAG),
        ) {
            Text(stringResource(R.string.confirm))
        }
    }
}

@Composable
private fun ResultContent(
    suggestion: RsvpSuggestion,
    onApply: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "🎯 " + stringResource(R.string.zen_calibration_result_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.zen_calibration_suggested_wpm, suggestion.wpm),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.zen_calibration_limitation),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (suggestion.confidence == Confidence.LOW) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.zen_calibration_low_confidence_note),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onApply,
            modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET_DP.dp).testTag(ZEN_CALIBRATION_APPLY_TAG),
        ) {
            Text(stringResource(R.string.zen_calibration_apply))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH_TARGET_DP.dp).testTag(ZEN_CALIBRATION_SKIP_TAG),
        ) {
            Text(stringResource(R.string.zen_calibration_skip))
        }
    }
}
```

- [ ] **Step 4: Wire entry button in `RsvpReaderDialog.kt`**

In `RsvpReaderDialog.kt`:
- Add imports:
  ```kotlin
  import androidx.hilt.navigation.compose.hiltViewModel
  import com.mckimquyen.reader.ui.page.rsvp.RsvpCalibrationDialog
  import com.mckimquyen.reader.ui.page.rsvp.RsvpCalibrationViewModel
  import com.mckimquyen.reader.ui.page.rsvp.ZEN_CALIBRATION_TRAIN_BUTTON_TAG
  ```
- In `RsvpReaderContent`:
  ```kotlin
  var showCalibration by remember { mutableStateOf(false) }
  ```
- Inside the Speed Control Card Column, right above the `Slider`:
  ```kotlin
  Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
  ) {
      Text(
          text = stringResource(R.string.zen_wpm_label, uiState.wpm),
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
      )
      TextButton(
          onClick = {
              viewModel.pause()
              showCalibration = true
          },
          modifier = Modifier.testTag(ZEN_CALIBRATION_TRAIN_BUTTON_TAG),
      ) {
          Text(stringResource(R.string.zen_calibration_train_action))
      }
  }
  ```
  (Replacing the existing lonely `Text(stringResource(R.string.zen_wpm_label, uiState.wpm))`).
- At the bottom of `RsvpReaderContent`:
  ```kotlin
  if (showCalibration) {
      val calibrationVm: RsvpCalibrationViewModel = hiltViewModel()
      RsvpCalibrationDialog(
          viewModel = calibrationVm,
          onApplied = { suggestion ->
              viewModel.setWpm(suggestion.wpm)
              showCalibration = false
          },
          onDismiss = { showCalibration = false },
      )
  }
  ```

- [ ] **Step 5: Wire entry row in `ZenSettingsPage.kt`**

In `ZenSettingsPage.kt`:
- Add imports:
  ```kotlin
  import com.mckimquyen.reader.ui.page.rsvp.RsvpCalibrationDialog
  import com.mckimquyen.reader.ui.page.rsvp.RsvpCalibrationViewModel
  ```
- Inside `ZenSettingsPage`:
  ```kotlin
  var showCalibration by remember { mutableStateOf(false) }
  ```
- Right after the `RSVP Explanation Card` item:
  ```kotlin
  item {
      Spacer(modifier = Modifier.height(12.dp))
      Card(
          modifier = Modifier
              .fillMaxWidth()
              .clickable { showCalibration = true },
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
          ),
      ) {
          Row(
              modifier = Modifier.padding(16.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
              Icon(
                  imageVector = Icons.Outlined.ElectricBolt, // already imported in ZenSettingsPage; avoids guessing an icon name
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(28.dp),
              )
              Spacer(modifier = Modifier.width(16.dp))
              Column {
                  Text(
                      text = stringResource(R.string.zen_calibration_title),
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.onSurface,
                  )
                  Text(
                      text = stringResource(R.string.zen_calibration_settings_desc),
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
              }
          }
      }
  }
  ```
- At the bottom of `ZenSettingsPage`:
  ```kotlin
  if (showCalibration) {
      val calibrationVm: RsvpCalibrationViewModel = hiltViewModel()
      RsvpCalibrationDialog(
          viewModel = calibrationVm,
          onApplied = { showCalibration = false },
          onDismiss = { showCalibration = false },
      )
  }
  ```
- No new icon import: `Icons.Outlined.ElectricBolt` is already imported in this file. Add `androidx.compose.foundation.clickable` and `androidx.compose.runtime.mutableStateOf/remember/setValue` if missing.

- [ ] **Step 6: Run green.** `./gradlew testDevDebugUnitTest --tests "*RsvpCalibration*" --tests "*Rsvp*" --rerun`.

- [ ] **Step 7: Commit.**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/page/rsvp/RsvpCalibrationDialog.kt app/src/main/java/com/mckimquyen/reader/ui/page/rsvp/RsvpReaderDialog.kt app/src/main/java/com/mckimquyen/reader/ui/page/setting/zen/ZenSettingsPage.kt app/src/test/java/com/mckimquyen/reader/ui/page/rsvp/RsvpCalibrationDialogTest.kt
git commit -m "feat(zen-07): add RsvpCalibrationDialog and wire entry points in RSVP reader and Zen settings"
```

---

### Task 6: Full verification, Pixel smoke, documentation

- [ ] **Step 1: Write instrumentation test `RsvpCalibrationDeviceTest.kt`**

In `app/src/androidTest/java/com/mckimquyen/reader/ui/page/rsvp/RsvpCalibrationDeviceTest.kt`:
Render `RsvpCalibrationScreen` on real display, click start button, verify transition to reading.

- [ ] **Step 2: Full verification.**
  - `./gradlew testDevDebugUnitTest --rerun`
  - `./gradlew lintDevDebug --rerun-tasks`
  - `./gradlew assembleDevDebug assembleDevDebugAndroidTest`
  - Record exact unit test counts and lint warnings from reports.

- [ ] **Step 3: Device instrumentation.**
  - Confirm `adb devices -l` lists `2B051FDH3006MU` and model is `Pixel 7 Pro`.
  - Install debug and test APKs.
  - Run: `adb -s 2B051FDH3006MU shell am instrument -w -e class com.mckimquyen.reader.ui.page.rsvp.RsvpCalibrationDeviceTest com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner`.
  - Uninstall test package.

- [ ] **Step 4: Smoke test on the Pixel.**
  - Backup `Reader*` (main + wal + shm) and `settings.preferences_pb`. Check non-empty and `PRAGMA integrity_check` ok on copy.
  - Launch app. Close startup ad via "Tiếp tục đến ứng dụng".
  - Open an article, tap RSVP speed reading icon in top bar.
  - Tap "Hiệu chỉnh tốc độ" button.
  - Calibration dialog appears: Intro -> Start -> Round 1 (300 WPM) plays -> Questions appear -> answer -> Round 2 -> answer -> Round 3 -> answer -> Result screen appears with suggested WPM and limitation copy.
  - Tap "Áp dụng": dialog closes, RSVP reader updates to suggested WPM.
  - Close RSVP reader, reopen it: WPM retains calibrated value.
  - Restore BOTH backups with quoted `run-as` script.
  - Verify with `cmp` and `PRAGMA integrity_check`.

- [ ] **Step 5: Completion docs.**
  - Write `doc/task/done/ZEN-07_adaptive-rsvp-trainer_DONE.md` with honest limits (rough estimate from 6 questions, static passages, no deep comprehension claims), exact commits, test counts.
  - Update `doc/feature.md` with ZEN-07 entry.
  - Update `doc/task/todo/08_ZEN_FOCUS_AND_SPEED_READING.md` row for ZEN-07 to Done.
  - Delete `doc/task/todo/ZEN-07_adaptive-rsvp-trainer.md` with `git rm`.

- [ ] **Step 6: Commit.**

```bash
git add app/src doc docs/superpowers/plans/2026-11-01-zen-07-rsvp-speed-suggestion.md
git commit -m "feat(zen-07): complete adaptive RSVP speed suggestion"
```

## Final Verification Checklist

- [ ] `RsvpCalibrationScorer`: 300/450/600 rounds, fallback 250, punctuation delay scales, confidence MEDIUM only when monotonic, never HIGH.
- [ ] `RsvpCalibrationContent`: 3 passages x 2 questions x 4 options for EN and VI, word counts 60-130.
- [ ] `RsvpTiming`: baseDelayMs and tokenDelayMs match existing formula.
- [ ] `RsvpTokenizer`: punctuationDelayScale scales only punctuation delays, not long words; default 1.0f unchanged.
- [ ] `RsvpCalibrationStore`: DataStore persistence of WPM and scale.
- [ ] `RsvpViewModel`: applies saved speed on load, manual override wins, no-arg constructor works.
- [ ] Dialog: Material 3, shows intro, round reading, questions, result with limitation copy.
- [ ] Entry points: button in `RsvpReaderDialog` and row in `ZenSettingsPage`.
- [ ] 14 strings across 38 locales; `StringResourceParityTest` 6/6.
- [ ] Room stays version 20; no schema change.
- [ ] Full unit suite, lint, builds pass; instrumentation passes on Pixel `2B051FDH3006MU`.
- [ ] Smoke verified calibration flow on device; backups restored and verified with `cmp`.
