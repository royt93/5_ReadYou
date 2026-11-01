# ECHO-02 Contrasting Coverage From Archive Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Surface articles from other feeds in the user's local archive that cover the same topic with contrarian headline wording, displayed in a card at the end of the article reader.

**Architecture:** Pure Kotlin logic (`WholePhraseMatcher`, `ContrarianCueLexicon`, `TopicPhraseExtractor`, `ContrastingCoverageFinder`) extracts topic phrases from the headline, matches whole phrases across candidate articles, and filters for different feeds with contrarian headline cues. `ReadingViewModel` observes the active article, extracts phrases, queries candidate articles from Room via `ArticleDao.queryRelatedArticleListWithFeed` on IO, evaluates contrasting coverage on Default, and exposes `ContrastingCoverageState`. A `ContrastingCoverageCard` composable is placed in `Content.kt` above the quiz card, gated by `LocalHeadlineSignalsEnabled`.

**Tech Stack:** Kotlin, Jetpack Compose Material 3 1.0.1 (`Card`, `FilterChip`), Room v20 (unchanged), Hilt, JUnit4, Robolectric, Android Compose instrumentation. No new dependencies.

## Global Constraints

- Branch `dev` only; no worktrees. Never mention, suggest or remind about pull requests.
- Pixel 7 Pro serial `2B051FDH3006MU` is the only device (`adb -s 2B051FDH3006MU ...`). Never use `connectedDevDebugAndroidTest`.
- Smoke MUST back up BOTH `databases/Reader` (plus `-wal` and `-shm`) AND `files/datastore/settings.preferences_pb` (non-empty, `PRAGMA integrity_check` ok on a copy), and restore BOTH with `adb shell "run-as com.mckimquyen.reader sh -c 'cat > PATH'" < local`. Verify with `cmp` across all three Room files and DataStore. Never delete the datastore on device.
- Standing owner rule: if a full-screen `com.google.android.gms.ads.AdActivity` appears, close it via its labelled control ("Tiếp tục đến ứng dụng"), re-check `topResumedActivity`, never tap ad creative, and report that ads appeared.
- No AI, network, API keys, ads, IAP or Room schema changes (Room stays version 20).
- Exact constants:
  - `MAX_PHRASES = 3`
  - `MAX_RESULTS = 5`
  - `CANDIDATE_LIMIT = 40`
  - `MIN_PHRASE_LENGTH = 4`
- Boundary checking: all cue and topic phrase matching must use `WholePhraseMatcher` (non-letter/digit boundaries on both sides, case-insensitive). Substring matching is forbidden.
- Gate: `ContrastingCoverageCard` is gated by `LocalHeadlineSignalsEnabled.current` (reusing the existing switch from ECHO-01 / EXC-04).
- 8 new `echo_contrast_*` keys across all 38 `values*/strings.xml`. English and Vietnamese hand-written, others English fallback. Added in the task that first uses them; `StringResourceParityTest` must stay 6/6.
- Compose pitfalls: `by` needs `getValue`/`setValue` imports; `stringResource` is `@Composable`, hoist out of lambdas; new composable parameters go last with defaults; read real failing assertions from test XML; find-based APK path (filename has spaces).

---

## File Structure

- Create `app/src/main/java/com/mckimquyen/reader/domain/steelman/WholePhraseMatcher.kt`: pure whole-word/phrase boundary checker.
- Create `app/src/main/java/com/mckimquyen/reader/domain/steelman/ContrarianCueLexicon.kt`: EN & VI contrarian headline cues.
- Create `app/src/main/java/com/mckimquyen/reader/domain/steelman/TopicPhraseExtractor.kt`: headline proper-noun/term extractor.
- Create `app/src/main/java/com/mckimquyen/reader/domain/steelman/ContrastingCoverage.kt`: data models (`ContrastingArticle`, `ContrastingCoverageState`).
- Create `app/src/main/java/com/mckimquyen/reader/domain/steelman/ContrastingCoverageFinder.kt`: candidate filter and ranker.
- Modify `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModel.kt`: manage contrasting coverage state and observer.
- Create `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/steelman/ContrastingCoverageCard.kt`: Compose UI card.
- Modify `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/Content.kt`: host card above quiz card.
- Modify `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingPage.kt`: wire ViewModel to Content.
- Modify all 38 `app/src/main/res/values*/strings.xml`.
- Tests: `WholePhraseMatcherTest`, `ContrarianCueLexiconTest`, `TopicPhraseExtractorTest`, `ContrastingCoverageFinderTest`, `ReadingViewModelContrastTest`, `ContrastingCoverageCardTest`, `ContrastingCoverageDeviceTest`.
- Docs: `doc/task/done/ECHO-02_steelman-rebuttal-box_DONE.md`, `doc/feature.md`, update `doc/task/todo/16_ECHOCHAMBER_AND_BIAS_COMPASS.md`, delete `doc/task/todo/ECHO-02_steelman-rebuttal-box.md`.

## Interfaces

Task 1 produces (`com.mckimquyen.reader.domain.steelman`):

```kotlin
object WholePhraseMatcher {
    fun containsPhrase(text: String, phrase: String): Boolean
}

object ContrarianCueLexicon {
    fun find(title: String): String?
}

object TopicPhraseExtractor {
    const val MAX_PHRASES = 3
    const val MIN_PHRASE_LENGTH = 4
    fun extract(title: String, feedName: String): List<String>
}
```

Task 2 produces (`com.mckimquyen.reader.domain.steelman`):

```kotlin
data class ContrastingArticle(
    val articleId: String,
    val title: String,
    val feedName: String,
    val date: Date,
    val cue: String,
)

sealed interface ContrastingCoverageState {
    object Idle : ContrastingCoverageState
    object Searching : ContrastingCoverageState
    object None : ContrastingCoverageState
    data class Ready(
        val phrases: List<String>,
        val selectedPhrase: String,
        val items: List<ContrastingArticle>,
    ) : ContrastingCoverageState
}

object ContrastingCoverageFinder {
    const val MAX_RESULTS = 5
    const val CANDIDATE_LIMIT = 40
    fun find(
        currentArticleId: String,
        currentFeedId: String,
        phrase: String,
        candidates: List<ArticleListItemWithFeed>,
    ): List<ContrastingArticle>
}
```

Task 4 produces (`ReadingViewModel`):

```kotlin
val contrastingCoverage: StateFlow<ContrastingCoverageState>
fun selectContrastPhrase(phrase: String)
```

Task 5 produces tags: `ECHO_CONTRAST_CARD_TAG`, `ECHO_CONTRAST_CHIP_PREFIX`, `ECHO_CONTRAST_ROW_PREFIX`.

---

### Task 1: Pure Logic: `WholePhraseMatcher`, `ContrarianCueLexicon`, and `TopicPhraseExtractor`

**Files:**
- Create `app/src/main/java/com/mckimquyen/reader/domain/steelman/WholePhraseMatcher.kt`
- Create `app/src/main/java/com/mckimquyen/reader/domain/steelman/ContrarianCueLexicon.kt`
- Create `app/src/main/java/com/mckimquyen/reader/domain/steelman/TopicPhraseExtractor.kt`
- Create `app/src/test/java/com/mckimquyen/reader/domain/steelman/WholePhraseMatcherTest.kt`
- Create `app/src/test/java/com/mckimquyen/reader/domain/steelman/ContrarianCueLexiconTest.kt`
- Create `app/src/test/java/com/mckimquyen/reader/domain/steelman/TopicPhraseExtractorTest.kt`

- [ ] **Step 1: Write failing tests for WholePhraseMatcher**

```kotlin
package com.mckimquyen.reader.domain.steelman

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WholePhraseMatcherTest {

    @Test
    fun matches_exactWord_withNonLetterBoundaries() {
        assertTrue(WholePhraseMatcher.containsPhrase("Tesla faces big risks ahead", "risks"))
        assertTrue(WholePhraseMatcher.containsPhrase("Warning: storm coming", "Warning"))
        assertTrue(WholePhraseMatcher.containsPhrase("Is this a risk?", "risk"))
        assertTrue(WholePhraseMatcher.containsPhrase("Starlink, under pressure", "Starlink"))
    }

    @Test
    fun rejects_substringMatches() {
        // "but" inside "butter", "risk" inside "brisk", "disrupt" inside "disruption"
        assertFalse(WholePhraseMatcher.containsPhrase("Spread the butter", "but"))
        assertFalse(WholePhraseMatcher.containsPhrase("A brisk autumn morning", "risk"))
        assertFalse(WholePhraseMatcher.containsPhrase("Major disruption hits transport", "disrupt"))
    }

    @Test
    fun matches_multiWordPhrases() {
        assertTrue(WholePhraseMatcher.containsPhrase("Meeting at White House today", "White House"))
        assertFalse(WholePhraseMatcher.containsPhrase("White Household goods on sale", "White House"))
    }

    @Test
    fun handles_emptyOrBlank() {
        assertFalse(WholePhraseMatcher.containsPhrase("Some text", ""))
        assertFalse(WholePhraseMatcher.containsPhrase("Some text", "   "))
        assertFalse(WholePhraseMatcher.containsPhrase("", "phrase"))
    }
}
```

- [ ] **Step 2: Write failing tests for ContrarianCueLexicon**

```kotlin
package com.mckimquyen.reader.domain.steelman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContrarianCueLexiconTest {

    @Test
    fun finds_englishCues() {
        assertEquals("warns", ContrarianCueLexicon.find("Official warns of high inflation"))
        assertEquals("warn", ContrarianCueLexicon.find("Scientists warn of coral loss"))
        assertEquals("critics", ContrarianCueLexicon.find("Critics slam new tax policy"))
        assertEquals("disputes", ContrarianCueLexicon.find("Company disputes union claim"))
        assertEquals("risks", ContrarianCueLexicon.find("Experts highlight risks in AI race"))
        assertEquals("backlash", ContrarianCueLexicon.find("Outcry follows public backlash"))
    }

    @Test
    fun finds_vietnameseCues() {
        assertEquals("cảnh báo", ContrarianCueLexicon.find("Chuyên gia cảnh báo nguy cơ lũ quét"))
        assertEquals("chỉ trích", ContrarianCueLexicon.find("Dư luận chỉ trích quyết định mới"))
        assertEquals("tuy nhiên", ContrarianCueLexicon.find("Kinh tế tăng trưởng, tuy nhiên còn nhiều rủi ro"))
        assertEquals("tranh cãi", ContrarianCueLexicon.find("Dự luật gây tranh cãi gay gắt"))
        assertEquals("bác bỏ", ContrarianCueLexicon.find("Bộ Ngoại giao bác bỏ thông tin sai lệch"))
    }

    @Test
    fun rejects_substringHits() {
        assertNull(ContrarianCueLexicon.find("A brisk jog in the park")) // not "risk"
        assertNull(ContrarianCueLexicon.find("Fresh butter on hot toast")) // not "but"
        assertNull(ContrarianCueLexicon.find("Warnings were issued")) // "warnings" is not "warning"/"warn" as a whole word
    }

    @Test
    fun returnsNull_whenNoCuePresent() {
        assertNull(ContrarianCueLexicon.find("Sunny weather expected this weekend"))
        assertNull(ContrarianCueLexicon.find("Tech company announces new quarterly results"))
    }
}
```

- [ ] **Step 3: Write failing tests for TopicPhraseExtractor**

```kotlin
package com.mckimquyen.reader.domain.steelman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicPhraseExtractorTest {

    @Test
    fun extracts_properNounRuns_upToThreeWords() {
        val phrases = TopicPhraseExtractor.extract("Sam Altman discusses AI chip strategy with TSMC", "BBC News")
        assertTrue(phrases.contains("Sam Altman"))
        assertTrue(phrases.contains("TSMC"))
    }

    @Test
    fun drops_feedName_andPhrasesContainedInFeedName() {
        val phrases = TopicPhraseExtractor.extract("BBC follows police on patrol in Liverpool", "BBC News")
        assertFalse(phrases.contains("BBC"))
        assertFalse(phrases.contains("BBC News"))
        assertTrue(phrases.contains("Liverpool"))
    }

    @Test
    fun drops_sentenceInitialSingleCommonWords_andDays() {
        val phrases = TopicPhraseExtractor.extract("Monday brings heavy rain as Starlink recovers", "Reuters")
        assertFalse(phrases.contains("Monday"))
        assertTrue(phrases.contains("Starlink"))
    }

    @Test
    fun capsAtThreePhrases_orderedByLengthDescending() {
        val title = "European Space Agency launches Ariane rocket alongside NASA and SpaceX"
        val phrases = TopicPhraseExtractor.extract(title, "Wired")
        assertTrue(phrases.size <= TopicPhraseExtractor.MAX_PHRASES)
        // Longer phrase appears first
        assertEquals("European Space Agency", phrases.first())
    }

    @Test
    fun drops_phrasesShorterThanMinLength() {
        val phrases = TopicPhraseExtractor.extract("US and UK meet on trade", "CNN")
        // "US" and "UK" are 2 letters (< 4), dropped unless combined
        assertTrue(phrases.none { it.length < TopicPhraseExtractor.MIN_PHRASE_LENGTH })
    }

    @Test
    fun returnsEmpty_forLowercaseOrEmpty() {
        assertTrue(TopicPhraseExtractor.extract("all lowercase headline here", "Feed").isEmpty())
        assertTrue(TopicPhraseExtractor.extract("", "Feed").isEmpty())
    }
}
```

- [ ] **Step 4: Run red.** `./gradlew testDevDebugUnitTest --tests "*WholePhraseMatcherTest" --tests "*ContrarianCueLexiconTest" --tests "*TopicPhraseExtractorTest"`. Expected: unresolved references.

- [ ] **Step 5: Implement `WholePhraseMatcher.kt`, `ContrarianCueLexicon.kt`, and `TopicPhraseExtractor.kt`**

Create `app/src/main/java/com/mckimquyen/reader/domain/steelman/WholePhraseMatcher.kt`:

```kotlin
package com.mckimquyen.reader.domain.steelman

object WholePhraseMatcher {

    /** Case-insensitive match requiring non-letter/digit boundaries on both sides. */
    fun containsPhrase(text: String, phrase: String): Boolean {
        val trimmedPhrase = phrase.trim()
        if (trimmedPhrase.isEmpty()) return false
        var from = 0
        while (true) {
            val at = text.indexOf(trimmedPhrase, from, ignoreCase = true)
            if (at < 0) return false
            val before = if (at == 0) ' ' else text[at - 1]
            val afterIndex = at + trimmedPhrase.length
            val after = if (afterIndex >= text.length) ' ' else text[afterIndex]
            if (!before.isLetterOrDigit() && !after.isLetterOrDigit()) return true
            from = at + 1
        }
    }
}
```

Create `app/src/main/java/com/mckimquyen/reader/domain/steelman/ContrarianCueLexicon.kt`:

```kotlin
package com.mckimquyen.reader.domain.steelman

object ContrarianCueLexicon {

    private val EN_CUES = listOf(
        "however", "despite", "although", "critics", "criticised", "criticized",
        "warns", "warn", "warning", "risks", "risk", "backlash", "controversy", "disputes",
        "denies", "fails", "slams", "rejects", "doubts", "but",
    )

    private val VI_CUES = listOf(
        "tuy nhiên", "bất chấp", "chỉ trích", "cảnh báo", "rủi ro", "tranh cãi",
        "phản đối", "bác bỏ", "nghi ngờ", "thất bại", "nhưng",
    )

    private val ALL_CUES = EN_CUES + VI_CUES

    /** Returns the first contrarian cue found as a whole word/phrase in [title], or null. */
    fun find(title: String): String? {
        if (title.isBlank()) return null
        return ALL_CUES.firstOrNull { cue -> WholePhraseMatcher.containsPhrase(title, cue) }
    }
}
```

Create `app/src/main/java/com/mckimquyen/reader/domain/steelman/TopicPhraseExtractor.kt`:

```kotlin
package com.mckimquyen.reader.domain.steelman

object TopicPhraseExtractor {
    const val MAX_PHRASES = 3
    const val MIN_PHRASE_LENGTH = 4
    private const val MAX_RUN_TOKENS = 3

    private val STOP_WORDS = setOf(
        "The", "A", "An", "In", "On", "At", "And", "Of", "For", "To", "With", "By", "From",
        "This", "That", "It", "He", "She", "They", "We", "What", "Why", "How", "When", "Who",
        "Is", "Are", "As", "But", "Or", "If", "After", "Before", "Over", "Under",
        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
        "January", "February", "March", "April", "May", "June", "July", "August", "September",
        "October", "November", "December",
        "Thứ", "Tháng", "Một", "Những", "Các", "Trong", "Ngoài",
    )

    private val WORD_SPLIT = Regex("\\s+")
    private val POSSESSIVE_SUFFIXES = listOf("’s", "'s")

    fun extract(title: String, feedName: String): List<String> {
        if (title.isBlank()) return emptyList()
        val tokens = title.trim().split(WORD_SPLIT).map { cleanToken(it) }
        val candidates = linkedSetOf<String>()

        var index = 0
        while (index < tokens.size) {
            if (!isCapitalized(tokens[index])) {
                index++
                continue
            }
            var end = index
            while (end < tokens.size && isCapitalized(tokens[end])) end++
            collectWindows(tokens.subList(index, end), clauseStart = index == 0, candidates)
            index = end
        }

        return candidates
            .filter { phrase ->
                phrase.length >= MIN_PHRASE_LENGTH &&
                    !feedName.contains(phrase, ignoreCase = true) &&
                    !phrase.contains(feedName, ignoreCase = true)
            }
            .sortedWith(compareByDescending<String> { it.length }.thenBy { title.indexOf(it, ignoreCase = true) })
            .distinctBy { it.lowercase() }
            .take(MAX_PHRASES)
    }

    private fun collectWindows(run: List<String>, clauseStart: Boolean, out: MutableSet<String>) {
        for (from in run.indices) {
            for (to in from until minOf(run.size, from + MAX_RUN_TOKENS)) {
                val window = run.subList(from, to + 1)
                if (window.first() in STOP_WORDS || window.last() in STOP_WORDS) continue
                if (window.size == 1 && clauseStart && from == 0 && !looksLikeAcronymOrName(window.first())) continue
                out += window.joinToString(" ")
            }
        }
    }

    private fun cleanToken(raw: String): String {
        var token = raw
        POSSESSIVE_SUFFIXES.forEach { token = token.removeSuffix(it) }
        return token.trim { !it.isLetterOrDigit() }
    }

    private fun isCapitalized(token: String): Boolean =
        token.isNotEmpty() && token.first().isUpperCase()

    private fun looksLikeAcronymOrName(token: String): Boolean {
        val letters = token.filter { it.isLetter() }
        val allCaps = letters.length >= 2 && letters.all { it.isUpperCase() }
        return allCaps || token.drop(1).any { it.isUpperCase() }
    }
}
```

- [ ] **Step 6: Run green.** `./gradlew testDevDebugUnitTest --tests "*WholePhraseMatcherTest" --tests "*ContrarianCueLexiconTest" --tests "*TopicPhraseExtractorTest" --rerun`; expected all pass.

- [ ] **Step 7: Commit.**

```bash
git add app/src/main/java/com/mckimquyen/reader/domain/steelman app/src/test/java/com/mckimquyen/reader/domain/steelman
git commit -m "feat(echo-02): add WholePhraseMatcher, ContrarianCueLexicon, and TopicPhraseExtractor"
```

---

### Task 2: Pure Logic: `ContrastingCoverageFinder` & Models

**Files:**
- Create `app/src/main/java/com/mckimquyen/reader/domain/steelman/ContrastingCoverage.kt`
- Create `app/src/main/java/com/mckimquyen/reader/domain/steelman/ContrastingCoverageFinder.kt`
- Create `app/src/test/java/com/mckimquyen/reader/domain/steelman/ContrastingCoverageFinderTest.kt`

- [ ] **Step 1: Write failing tests for ContrastingCoverageFinder**

```kotlin
package com.mckimquyen.reader.domain.steelman

import com.mckimquyen.reader.domain.model.article.ArticleListItem
import com.mckimquyen.reader.domain.model.article.ArticleListItemWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class ContrastingCoverageFinderTest {

    private fun item(
        id: String,
        title: String,
        feedId: String = "f1",
        feedName: String = "Feed 1",
        short: String = "",
        date: Long = 1_000L,
    ) = ArticleListItemWithFeed(
        article = ArticleListItem(
            id = id, date = Date(date), title = title, author = null, shortDescription = short, img = null,
            link = "https://e.com/$id", feedId = feedId, accountId = 1, isUnread = true, isStarred = false,
            isReadLater = false, updateAt = null, aiSummary = null, readAt = null, audioUrl = null,
            audioMime = null, audioDurationSec = null,
        ),
        feed = Feed(id = feedId, name = feedName, url = "https://e.com", groupId = "g", accountId = 1),
    )

    @Test
    fun returnsContrastingArticles_fromOtherFeeds_withContrarianCue() {
        val candidates = listOf(
            item("1", "Starlink expands rapidly across Europe", feedId = "f2", feedName = "Wired", date = 2_000L), // no cue
            item("2", "Astronomers warn Starlink satellites disrupt night sky observations", feedId = "f2", feedName = "Wired", date = 3_000L), // cue: warn
            item("3", "Government warns of Starlink monopoly risks", feedId = "f3", feedName = "Reuters", date = 4_000L), // cue: warns
            item("4", "Military warns of Starlink reliance", feedId = "f1", feedName = "BBC", date = 5_000L), // same feed: excluded
        )

        val results = ContrastingCoverageFinder.find(
            currentArticleId = "curr",
            currentFeedId = "f1",
            phrase = "Starlink",
            candidates = candidates,
        )

        assertEquals(2, results.size)
        // Newest first
        assertEquals("3", results[0].articleId)
        assertEquals("warns", results[0].cue)
        assertEquals("Reuters", results[0].feedName)

        assertEquals("2", results[1].articleId)
        assertEquals("warn", results[1].cue)
    }

    @Test
    fun excludes_currentArticle_evenIfFromDifferentFeedQuery() {
        val candidates = listOf(
            item("curr", "Critics warn of Starlink flaws", feedId = "f2"),
        )
        val results = ContrastingCoverageFinder.find("curr", "f1", "Starlink", candidates)
        assertTrue(results.isEmpty())
    }

    @Test
    fun excludes_candidateWithoutThePhrase() {
        val candidates = listOf(
            item("1", "Critics warn of satellite internet risks", feedId = "f2"), // no "Starlink"
        )
        val results = ContrastingCoverageFinder.find("curr", "f1", "Starlink", candidates)
        assertTrue(results.isEmpty())
    }

    @Test
    fun capsAtMaxResults() {
        val candidates = (1..10).map { i ->
            item("a$i", "Critics warn about Starlink issue $i", feedId = "feed_$i", feedName = "Source $i", date = i * 1000L)
        }
        val results = ContrastingCoverageFinder.find("curr", "f0", "Starlink", candidates)
        assertEquals(ContrastingCoverageFinder.MAX_RESULTS, results.size)
        assertEquals("a10", results.first().articleId) // newest first
    }

    @Test
    fun phraseInShortDescription_countsIfTitleHasCue() {
        val candidates = listOf(
            item("1", "Experts warn of new orbital danger", feedId = "f2", short = "Concerns grow over Starlink constellation"),
        )
        val results = ContrastingCoverageFinder.find("curr", "f1", "Starlink", candidates)
        assertEquals(1, results.size)
        assertEquals("1", results.first().articleId)
    }
}
```

- [ ] **Step 2: Run red.** `./gradlew testDevDebugUnitTest --tests "*ContrastingCoverageFinderTest"`. Expected: unresolved references.

- [ ] **Step 3: Implement `ContrastingCoverage.kt` and `ContrastingCoverageFinder.kt`**

Create `app/src/main/java/com/mckimquyen/reader/domain/steelman/ContrastingCoverage.kt`:

```kotlin
package com.mckimquyen.reader.domain.steelman

import java.util.Date

data class ContrastingArticle(
    val articleId: String,
    val title: String,
    val feedName: String,
    val date: Date,
    val cue: String,
)

sealed interface ContrastingCoverageState {
    object Idle : ContrastingCoverageState
    object Searching : ContrastingCoverageState
    object None : ContrastingCoverageState
    data class Ready(
        val phrases: List<String>,
        val selectedPhrase: String,
        val items: List<ContrastingArticle>,
    ) : ContrastingCoverageState
}
```

Create `app/src/main/java/com/mckimquyen/reader/domain/steelman/ContrastingCoverageFinder.kt`:

```kotlin
package com.mckimquyen.reader.domain.steelman

import com.mckimquyen.reader.domain.model.article.ArticleListItemWithFeed

object ContrastingCoverageFinder {
    const val MAX_RESULTS = 5
    const val CANDIDATE_LIMIT = 40

    fun find(
        currentArticleId: String,
        currentFeedId: String,
        phrase: String,
        candidates: List<ArticleListItemWithFeed>,
    ): List<ContrastingArticle> {
        if (phrase.isBlank()) return emptyList()

        return candidates
            .filter { item ->
                val article = item.article
                article.id != currentArticleId &&
                    article.feedId != currentFeedId &&
                    (WholePhraseMatcher.containsPhrase(article.title, phrase) ||
                        WholePhraseMatcher.containsPhrase(article.shortDescription, phrase))
            }
            .mapNotNull { item ->
                val cue = ContrarianCueLexicon.find(item.article.title) ?: return@mapNotNull null
                ContrastingArticle(
                    articleId = item.article.id,
                    title = item.article.title,
                    feedName = item.feed.name,
                    date = item.article.date,
                    cue = cue,
                )
            }
            .sortedByDescending { it.date.time }
            .take(MAX_RESULTS)
    }
}
```

- [ ] **Step 4: Run green.** `./gradlew testDevDebugUnitTest --tests "*ContrastingCoverageFinderTest" --rerun`; expected 5/5.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/mckimquyen/reader/domain/steelman/ContrastingCoverage.kt app/src/main/java/com/mckimquyen/reader/domain/steelman/ContrastingCoverageFinder.kt app/src/test/java/com/mckimquyen/reader/domain/steelman/ContrastingCoverageFinderTest.kt
git commit -m "feat(echo-02): add ContrastingCoverageFinder and state models"
```

---

### Task 3: 8 Strings in 38 Locales

**Files:** Modify all 38 `app/src/main/res/values*/strings.xml`.

- [ ] **Step 1: Add the 8 strings to every locale.** Save this as `/tmp/echo02_strings.py` and run `python3 /tmp/echo02_strings.py` from the repo root. It skips keys a file already has, so it is safe to re-run.

```python
# -*- coding: utf-8 -*-
import glob

EN = {
    'echo_contrast_title': 'Other angles in your archive',
    'echo_contrast_phrases_label': 'Topic:',
    'echo_contrast_cue_prefix': 'Contrast: %1$s',
    'echo_contrast_limitation': 'Articles from other saved feeds with contrarian headline wording. Not a fact-check.',
    'echo_contrast_card_desc': 'Contrasting coverage card with %1$d articles',
    'echo_contrast_open_article_desc': 'Open article %1$s from %2$s',
    'echo_contrast_empty_wording': 'No contrasting articles found',
    'echo_contrast_switch_phrase_desc': 'Switch to topic %1$s',
}
VI = {
    'echo_contrast_title': 'Góc nhìn khác trong kho của bạn',
    'echo_contrast_phrases_label': 'Chủ đề:',
    'echo_contrast_cue_prefix': 'Từ đối lập: %1$s',
    'echo_contrast_limitation': 'Các bài đã lưu từ nguồn khác có từ ngữ trái chiều ở tiêu đề. Không phải kiểm chứng sự thật.',
    'echo_contrast_card_desc': 'Thẻ góc nhìn khác với %1$d bài viết',
    'echo_contrast_open_article_desc': 'Mở bài viết %1$s từ %2$s',
    'echo_contrast_empty_wording': 'Không tìm thấy bài viết đối lập nào',
    'echo_contrast_switch_phrase_desc': 'Chuyển sang chủ đề %1$s',
}

def esc(v):
    return v.replace('&', '&amp;').replace("'", "\\'").replace('"', '\\"')

files = glob.glob('app/src/main/res/values*/strings.xml')
for f in files:
    values = VI if f.endswith('values-vi/strings.xml') else EN
    text = open(f, encoding='utf-8').read()
    missing = {k: v for k, v in values.items() if f'<string name="{k}"' not in text}
    if not missing:
        continue
    assert text.count('</resources>') == 1, f
    block = '\n    <!-- ECHO-02 contrasting coverage -->\n' + ''.join(
        f'    <string name="{k}">{esc(v)}</string>\n' for k, v in missing.items())
    open(f, 'w', encoding='utf-8').write(text.replace('</resources>', block + '</resources>'))
print(len(files), 'locale files')
```

Expected output: `38 locale files`. Then `grep -L echo_contrast_switch_phrase_desc app/src/main/res/values*/strings.xml | wc -l` must print `0`.

- [ ] **Step 2: Verify parity.** `./gradlew testDevDebugUnitTest --tests "*StringResourceParityTest" --rerun`; expected 6/6.

- [ ] **Step 3: Commit.**

```bash
git add app/src/main/res
git commit -m "feat(echo-02): add contrasting coverage strings across 38 locales"
```

---

### Task 4: `ReadingViewModel` State & Query Integration

**Files:**
- Modify `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModel.kt`
- Create `app/src/test/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModelContrastTest.kt`

- [ ] **Step 1: Write failing tests for ReadingViewModel contrast coverage**

Create `app/src/test/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModelContrastTest.kt`:

```kotlin
package com.mckimquyen.reader.ui.page.home.read

import com.mckimquyen.reader.domain.model.article.ArticleListItem
import com.mckimquyen.reader.domain.model.article.ArticleListItemWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.steelman.ContrastingCoverageState
import com.mckimquyen.reader.domain.sv.AbstractRssRepository
import com.mckimquyen.reader.domain.sv.RssSv
import com.mckimquyen.reader.infrastructure.ai.GeminiSummaryService
import com.mckimquyen.reader.infrastructure.audio.TtsManager
import com.mckimquyen.reader.infrastructure.audio.TtsState
import com.mckimquyen.reader.infrastructure.audio.ambient.ZenAudioManager
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingViewModelContrastTest {
    private val dispatcher = StandardTestDispatcher()
    private val rssService: RssSv = mockk()
    private val repo: AbstractRssRepository = mockk(relaxed = true)
    private val rssHelper: RssHelper = mockk(relaxed = true)
    private val ttsManager: TtsManager = mockk(relaxed = true)
    private val summaryService: GeminiSummaryService = mockk(relaxed = true)
    private val zenAudioManager: ZenAudioManager = mockk(relaxed = true)
    private val articleDao: ArticleDao = mockk()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        io.mockk.mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.w(any(), any<String>(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
        every { rssService.get() } returns repo
        every { ttsManager.ttsState } returns MutableStateFlow(TtsState.IDLE)
        every { zenAudioManager.isPlaying } returns MutableStateFlow(false)
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
        io.mockk.unmockkAll()
    }

    private fun createViewModel() = ReadingViewModel(
        rssService = rssService,
        rssHelper = rssHelper,
        ttsManager = ttsManager,
        summaryService = summaryService,
        zenAudioManager = zenAudioManager,
        defaultDispatcher = dispatcher,
        articleDao = articleDao,
        ioDispatcher = dispatcher,
    )

    private fun item(id: String, title: String, feedId: String = "f1", feedName: String = "Feed 1") =
        ArticleListItemWithFeed(
            article = ArticleListItem(
                id = id, date = Date(1_000L), title = title, author = null, shortDescription = "", img = null,
                link = "https://e.com/$id", feedId = feedId, accountId = 7, isUnread = true, isStarred = false,
                isReadLater = false, updateAt = null, aiSummary = null, readAt = null, audioUrl = null,
                audioMime = null, audioDurationSec = null,
            ),
            feed = Feed(id = feedId, name = feedName, url = "https://e.com", groupId = "g", accountId = 7),
        )

    @Test
    fun loadArticle_withContrastingCandidates_setsReadyState() = runTest(dispatcher) {
        val current = item("cur", "SpaceX expands Starlink coverage rapidly", feedId = "f1", feedName = "TechFeed")
        coEvery { repo.findArticleById("cur") } returns current.toArticleWithFeed()
        coEvery { articleDao.queryRelatedArticleListWithFeed(7, "cur", any(), 40) } returns listOf(
            item("opp1", "Astronomers warn Starlink brightness disrupts observations", feedId = "f2", feedName = "ScienceFeed"),
        )

        val vm = createViewModel()
        vm.initData("cur")
        advanceUntilIdle()

        val state = vm.contrastingCoverage.value as ContrastingCoverageState.Ready
        assertEquals("Starlink", state.selectedPhrase) // longest phrase of "SpaceX" and "Starlink"
        assertEquals(1, state.items.size)
        assertEquals("opp1", state.items.first().articleId)
        assertEquals("warn", state.items.first().cue)
    }

    @Test
    fun loadArticle_withoutContrastingCandidates_setsNoneState() = runTest(dispatcher) {
        val current = item("cur", "SpaceX expands Starlink coverage rapidly", feedId = "f1", feedName = "TechFeed")
        coEvery { repo.findArticleById("cur") } returns current.toArticleWithFeed()
        coEvery { articleDao.queryRelatedArticleListWithFeed(any(), any(), any(), any()) } returns emptyList()

        val vm = createViewModel()
        vm.initData("cur")
        advanceUntilIdle()

        assertEquals(ContrastingCoverageState.None, vm.contrastingCoverage.value)
    }

    @Test
    fun loadArticle_withNoExtractablePhrases_setsNoneState() = runTest(dispatcher) {
        val current = item("cur", "all lowercase headline with no proper nouns", feedId = "f1", feedName = "TechFeed")
        coEvery { repo.findArticleById("cur") } returns current.toArticleWithFeed()

        val vm = createViewModel()
        vm.initData("cur")
        advanceUntilIdle()

        assertEquals(ContrastingCoverageState.None, vm.contrastingCoverage.value)
    }

    @Test
    fun selectContrastPhrase_switchesToAnotherExtractedPhrase() = runTest(dispatcher) {
        val current = item("cur", "Elon Musk announces new Starlink deployment plan", feedId = "f1", feedName = "TechFeed")
        coEvery { repo.findArticleById("cur") } returns current.toArticleWithFeed()
        // Extracted phrases, longest first: "Elon Musk", "Starlink", "Musk". The candidate contains all of them.
        coEvery { articleDao.queryRelatedArticleListWithFeed(7, "cur", any(), 40) } returns listOf(
            item("opp1", "Critics warn Elon Musk Starlink plan poses risks", feedId = "f2", feedName = "OtherFeed"),
        )

        val vm = createViewModel()
        vm.initData("cur")
        advanceUntilIdle()

        val first = vm.contrastingCoverage.value as ContrastingCoverageState.Ready
        assertEquals("Elon Musk", first.selectedPhrase)
        assertTrue(first.phrases.containsAll(listOf("Elon Musk", "Starlink")))

        vm.selectContrastPhrase("Starlink")
        advanceUntilIdle()

        val updated = vm.contrastingCoverage.value as ContrastingCoverageState.Ready
        assertEquals("Starlink", updated.selectedPhrase)
        assertEquals(first.phrases, updated.phrases)
    }

    @Test
    fun switchingArticle_resetsContrastStateToIdleThenSearches() = runTest(dispatcher) {
        val cur1 = item("c1", "Starlink expands", feedId = "f1", feedName = "F1")
        val cur2 = item("c2", "nothing here", feedId = "f1", feedName = "F1")
        coEvery { repo.findArticleById("c1") } returns cur1.toArticleWithFeed()
        coEvery { repo.findArticleById("c2") } returns cur2.toArticleWithFeed()
        coEvery { articleDao.queryRelatedArticleListWithFeed(any(), any(), any(), any()) } returns emptyList()

        val vm = createViewModel()
        vm.initData("c1")
        advanceUntilIdle()

        vm.initData("c2")
        // Stale state from c1 is cleared
        advanceUntilIdle()
        assertEquals(ContrastingCoverageState.None, vm.contrastingCoverage.value)
    }
}
```

- [ ] **Step 2: Run red.** `./gradlew testDevDebugUnitTest --tests "*ReadingViewModelContrastTest"`. Expected: unresolved references.

- [ ] **Step 3: Implement in `ReadingViewModel.kt`**

Add imports:
```kotlin
import com.mckimquyen.reader.domain.steelman.ContrastingCoverageFinder
import com.mckimquyen.reader.domain.steelman.ContrastingCoverageState
import com.mckimquyen.reader.domain.steelman.TopicPhraseExtractor
```

In `ReadingViewModel`:
```kotlin
    private var contrastJob: Job? = null
    private val _contrastingCoverage = MutableStateFlow<ContrastingCoverageState>(ContrastingCoverageState.Idle)
    val contrastingCoverage: StateFlow<ContrastingCoverageState> = _contrastingCoverage.asStateFlow()
    private var currentContrastPhrases: List<String> = emptyList()
```

In `init { ... }` add a second, separate observer right after the existing ECHO-01 `viewModelScope.launch { ... }` block. It keys on the article id only, so a change of body text does not re-run the archive search:

```kotlin
        // ECHO-02: search the local archive for other angles whenever a different article is shown.
        viewModelScope.launch {
            _readingUiState
                .map { it.articleWithFeed }
                .distinctUntilChangedBy { it?.article?.id }
                .collect { loadContrastingCoverage(it) }
        }
```

Add `import kotlinx.coroutines.flow.distinctUntilChangedBy` next to the existing flow imports.

Implementation of `loadContrastingCoverage` and `selectContrastPhrase`:
```kotlin
    private fun loadContrastingCoverage(articleWithFeed: ArticleWithFeed?) {
        contrastJob?.cancel()
        _contrastingCoverage.value = ContrastingCoverageState.Idle
        currentContrastPhrases = emptyList()
        val item = articleWithFeed ?: return
        val article = item.article
        val feed = item.feed

        contrastJob = viewModelScope.launch {
            _contrastingCoverage.value = ContrastingCoverageState.Searching
            val phrases = withContext(defaultDispatcher) {
                TopicPhraseExtractor.extract(article.title, feed.name)
            }
            if (phrases.isEmpty()) {
                _contrastingCoverage.value = ContrastingCoverageState.None
                return@launch
            }
            currentContrastPhrases = phrases
            queryContrastForPhrase(article.id, article.accountId, feed.id, phrases.first(), phrases)
        }
    }

    fun selectContrastPhrase(phrase: String) {
        val article = _readingUiState.value.articleWithFeed?.article ?: return
        val feed = _readingUiState.value.articleWithFeed?.feed ?: return
        if (phrase !in currentContrastPhrases) return
        contrastJob?.cancel()
        contrastJob = viewModelScope.launch {
            _contrastingCoverage.value = ContrastingCoverageState.Searching
            queryContrastForPhrase(article.id, article.accountId, feed.id, phrase, currentContrastPhrases)
        }
    }

    private suspend fun queryContrastForPhrase(
        currentArticleId: String,
        accountId: Int,
        currentFeedId: String,
        phrase: String,
        allPhrases: List<String>,
    ) {
        val dao = articleDao
        if (dao == null) {
            _contrastingCoverage.value = ContrastingCoverageState.None
            return
        }
        try {
            val escaped = BountyQuerySanitizer.escapeLikeLiteral(phrase)
            val candidates = withContext(ioDispatcher) {
                dao.queryRelatedArticleListWithFeed(accountId, currentArticleId, escaped, ContrastingCoverageFinder.CANDIDATE_LIMIT)
            }
            val contrasting = withContext(defaultDispatcher) {
                ContrastingCoverageFinder.find(currentArticleId, currentFeedId, phrase, candidates)
            }
            _contrastingCoverage.value = if (contrasting.isNotEmpty()) {
                ContrastingCoverageState.Ready(allPhrases, phrase, contrasting)
            } else {
                ContrastingCoverageState.None
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("roy93~Contrast", "[queryContrastForPhrase] failed: $e")
            _contrastingCoverage.value = ContrastingCoverageState.None
        }
    }
```

In `initData`, inside the existing `if (_readingUiState.value.articleWithFeed?.article?.id != articleId) { ... }` block (the one that cancels `summaryJob`, `mindMapJob` and `deepReadJob`), add two lines so a stale card never lingers while the next article loads:

```kotlin
            contrastJob?.cancel()
            _contrastingCoverage.value = ContrastingCoverageState.Idle
```

- [ ] **Step 4: Run green.** `./gradlew testDevDebugUnitTest --tests "*ReadingViewModelContrastTest" --tests "*ReadingViewModel*" --rerun`; expected all pass.

- [ ] **Step 4b: Integration test with a real in-memory Room and two feeds.**

Create `app/src/test/java/com/mckimquyen/reader/domain/steelman/ContrastingCoverageRoomIntegrationTest.kt` (mirrors `BountyInvestigationRepositoryTest`):

```kotlin
package com.mckimquyen.reader.domain.steelman

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mckimquyen.reader.domain.model.account.Account
import com.mckimquyen.reader.domain.model.account.AccountType
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.bounty.BountyQuerySanitizer
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.model.group.Group
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ContrastingCoverageRoomIntegrationTest {

    private lateinit var db: AndroidDatabase

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AndroidDatabase::class.java)
            .allowMainThreadQueries().build()
        db.accountDao().insert(Account(id = 1, name = "Local", type = AccountType.Local))
        db.groupDao().insert(Group(id = "g1", name = "Default", accountId = 1))
        db.feedDao().insert(Feed(id = "f_bbc", name = "BBC News", url = "https://e.com/bbc", groupId = "g1", accountId = 1))
        db.feedDao().insert(Feed(id = "f_wired", name = "Wired", url = "https://e.com/wired", groupId = "g1", accountId = 1))
    }

    @After fun tearDown() = db.close()

    private suspend fun insert(id: String, title: String, feedId: String, short: String = "", date: Long = 1_000L) {
        db.articleDao().insert(
            Article(
                id = id, date = Date(date), title = title, author = null, rawDescription = "",
                shortDescription = short, fullContent = "", img = null, link = "https://e.com/$id",
                feedId = feedId, accountId = 1, isUnread = true, isStarred = false, isReadLater = false,
            )
        )
    }

    @Test
    fun queryAndFinder_cooperateToSurfaceOtherSourceWithContrarianCue() = runBlocking {
        insert("cur", "Elon Musk expands Starlink satellite service", "f_bbc", date = 1_000L)
        insert("opp_wired", "Scientists warn Starlink disrupts deep-space research", "f_wired", date = 2_000L)
        insert("opp_same_feed", "Critics warn Starlink costs rise", "f_bbc", date = 3_000L) // same feed: excluded by finder
        insert("unrelated_wired", "Weather service warns of autumn storm", "f_wired", date = 4_000L) // no Starlink

        val candidates = db.articleDao().queryRelatedArticleListWithFeed(
            accountId = 1,
            currentArticleId = "cur",
            escapedKeyword = BountyQuerySanitizer.escapeLikeLiteral("Starlink"),
            limit = ContrastingCoverageFinder.CANDIDATE_LIMIT,
        )
        val contrasting = ContrastingCoverageFinder.find("cur", "f_bbc", "Starlink", candidates)

        assertEquals(1, contrasting.size)
        val match = contrasting.single()
        assertEquals("opp_wired", match.articleId)
        assertEquals("Wired", match.feedName)
        assertEquals("warn", match.cue)
    }

    @Test
    fun queryAndFinder_safelyHandleWildcardCharactersInPhrase() = runBlocking {
        insert("cur", "Company promises 100%_growth this quarter", "f_bbc")
        insert("opp_wired", "Economists warn 100%_growth claim is unrealistic", "f_wired")
        insert("false_hit", "Economists warn 100X_growth claim is unrealistic", "f_wired")

        val candidates = db.articleDao().queryRelatedArticleListWithFeed(
            accountId = 1,
            currentArticleId = "cur",
            escapedKeyword = BountyQuerySanitizer.escapeLikeLiteral("100%_growth"),
            limit = ContrastingCoverageFinder.CANDIDATE_LIMIT,
        )
        val contrasting = ContrastingCoverageFinder.find("cur", "f_bbc", "100%_growth", candidates)

        assertEquals(listOf("opp_wired"), contrasting.map { it.articleId })
    }
}
```

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/mckimquyen/reader/domain/steelman/ContrastingCoverage.kt app/src/main/java/com/mckimquyen/reader/domain/steelman/ContrastingCoverageFinder.kt app/src/test/java/com/mckimquyen/reader/domain/steelman/ContrastingCoverageFinderTest.kt
git commit -m "feat(echo-02): add ContrastingCoverageFinder and state models"
```

---

### Task 3: 8 Strings in 38 Locales

**Files:** Modify all 38 `app/src/main/res/values*/strings.xml`.

- [ ] **Step 1: Add the 8 strings to every locale.** Save this as `/tmp/echo02_strings.py` and run `python3 /tmp/echo02_strings.py` from the repo root. It skips keys a file already has, so it is safe to re-run.

```python
# -*- coding: utf-8 -*-
import glob

EN = {
    'echo_contrast_title': 'Other angles in your archive',
    'echo_contrast_phrases_label': 'Topic:',
    'echo_contrast_cue_prefix': 'Contrast: %1$s',
    'echo_contrast_limitation': 'Articles from other saved feeds with contrarian headline wording. Not a fact-check.',
    'echo_contrast_card_desc': 'Contrasting coverage card with %1$d articles',
    'echo_contrast_open_article_desc': 'Open article %1$s from %2$s',
    'echo_contrast_empty_wording': 'No contrasting articles found',
    'echo_contrast_switch_phrase_desc': 'Switch to topic %1$s',
}
VI = {
    'echo_contrast_title': 'Góc nhìn khác trong kho của bạn',
    'echo_contrast_phrases_label': 'Chủ đề:',
    'echo_contrast_cue_prefix': 'Từ đối lập: %1$s',
    'echo_contrast_limitation': 'Các bài đã lưu từ nguồn khác có từ ngữ trái chiều ở tiêu đề. Không phải kiểm chứng sự thật.',
    'echo_contrast_card_desc': 'Thẻ góc nhìn khác với %1$d bài viết',
    'echo_contrast_open_article_desc': 'Mở bài viết %1$s từ %2$s',
    'echo_contrast_empty_wording': 'Không tìm thấy bài viết đối lập nào',
    'echo_contrast_switch_phrase_desc': 'Chuyển sang chủ đề %1$s',
}

def esc(v):
    return v.replace('&', '&amp;').replace("'", "\\'").replace('"', '\\"')

files = glob.glob('app/src/main/res/values*/strings.xml')
for f in files:
    values = VI if f.endswith('values-vi/strings.xml') else EN
    text = open(f, encoding='utf-8').read()
    missing = {k: v for k, v in values.items() if f'<string name="{k}"' not in text}
    if not missing:
        continue
    assert text.count('</resources>') == 1, f
    block = '\n    <!-- ECHO-02 contrasting coverage -->\n' + ''.join(
        f'    <string name="{k}">{esc(v)}</string>\n' for k, v in missing.items())
    open(f, 'w', encoding='utf-8').write(text.replace('</resources>', block + '</resources>'))
print(len(files), 'locale files')
```

Expected output: `38 locale files`. Then `grep -L echo_contrast_switch_phrase_desc app/src/main/res/values*/strings.xml | wc -l` must print `0`.

- [ ] **Step 2: Verify parity.** `./gradlew testDevDebugUnitTest --tests "*StringResourceParityTest" --rerun`; expected 6/6.

- [ ] **Step 3: Commit.**

```bash
git add app/src/main/res
git commit -m "feat(echo-02): add contrasting coverage strings across 38 locales"
```

---

### Task 4: `ReadingViewModel` State & Query Integration

**Files:**
- Modify `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModel.kt`
- Create `app/src/test/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModelContrastTest.kt`

- [ ] **Step 1: Write failing tests for ReadingViewModel contrast coverage**

Create `app/src/test/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModelContrastTest.kt`:

```kotlin
package com.mckimquyen.reader.ui.page.home.read

import com.mckimquyen.reader.domain.model.article.ArticleListItem
import com.mckimquyen.reader.domain.model.article.ArticleListItemWithFeed
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.steelman.ContrastingCoverageState
import com.mckimquyen.reader.domain.sv.AbstractRssRepository
import com.mckimquyen.reader.domain.sv.RssSv
import com.mckimquyen.reader.infrastructure.ai.GeminiSummaryService
import com.mckimquyen.reader.infrastructure.audio.TtsManager
import com.mckimquyen.reader.infrastructure.audio.TtsState
import com.mckimquyen.reader.infrastructure.audio.ambient.ZenAudioManager
import com.mckimquyen.reader.infrastructure.rss.RssHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingViewModelContrastTest {
    private val dispatcher = StandardTestDispatcher()
    private val rssService: RssSv = mockk()
    private val repo: AbstractRssRepository = mockk(relaxed = true)
    private val rssHelper: RssHelper = mockk(relaxed = true)
    private val ttsManager: TtsManager = mockk(relaxed = true)
    private val summaryService: GeminiSummaryService = mockk(relaxed = true)
    private val zenAudioManager: ZenAudioManager = mockk(relaxed = true)
    private val articleDao: ArticleDao = mockk()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        io.mockk.mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.w(any(), any<String>(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
        every { rssService.get() } returns repo
        every { ttsManager.ttsState } returns MutableStateFlow(TtsState.IDLE)
        every { zenAudioManager.isPlaying } returns MutableStateFlow(false)
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
        io.mockk.unmockkAll()
    }

    private fun createViewModel() = ReadingViewModel(
        rssService = rssService,
        rssHelper = rssHelper,
        ttsManager = ttsManager,
        summaryService = summaryService,
        zenAudioManager = zenAudioManager,
        defaultDispatcher = dispatcher,
        articleDao = articleDao,
        ioDispatcher = dispatcher,
    )

    private fun item(id: String, title: String, feedId: String = "f1", feedName: String = "Feed 1") =
        ArticleListItemWithFeed(
            article = ArticleListItem(
                id = id, date = Date(1_000L), title = title, author = null, shortDescription = "", img = null,
                link = "https://e.com/$id", feedId = feedId, accountId = 7, isUnread = true, isStarred = false,
                isReadLater = false, updateAt = null, aiSummary = null, readAt = null, audioUrl = null,
                audioMime = null, audioDurationSec = null,
            ),
            feed = Feed(id = feedId, name = feedName, url = "https://e.com", groupId = "g", accountId = 7),
        )

    @Test
    fun loadArticle_withContrastingCandidates_setsReadyState() = runTest(dispatcher) {
        val current = item("cur", "SpaceX expands Starlink coverage rapidly", feedId = "f1", feedName = "TechFeed")
        coEvery { repo.findArticleById("cur") } returns current.toArticleWithFeed()
        coEvery { articleDao.queryRelatedArticleListWithFeed(7, "cur", any(), 40) } returns listOf(
            item("opp1", "Astronomers warn Starlink brightness disrupts observations", feedId = "f2", feedName = "ScienceFeed"),
        )

        val vm = createViewModel()
        vm.initData("cur")
        advanceUntilIdle()

        val state = vm.contrastingCoverage.value as ContrastingCoverageState.Ready
        assertEquals("Starlink", state.selectedPhrase) // longest phrase of "SpaceX" and "Starlink"
        assertEquals(1, state.items.size)
        assertEquals("opp1", state.items.first().articleId)
        assertEquals("warn", state.items.first().cue)
    }

    @Test
    fun loadArticle_withoutContrastingCandidates_setsNoneState() = runTest(dispatcher) {
        val current = item("cur", "SpaceX expands Starlink coverage rapidly", feedId = "f1", feedName = "TechFeed")
        coEvery { repo.findArticleById("cur") } returns current.toArticleWithFeed()
        coEvery { articleDao.queryRelatedArticleListWithFeed(any(), any(), any(), any()) } returns emptyList()

        val vm = createViewModel()
        vm.initData("cur")
        advanceUntilIdle()

        assertEquals(ContrastingCoverageState.None, vm.contrastingCoverage.value)
    }

    @Test
    fun loadArticle_withNoExtractablePhrases_setsNoneState() = runTest(dispatcher) {
        val current = item("cur", "all lowercase headline with no proper nouns", feedId = "f1", feedName = "TechFeed")
        coEvery { repo.findArticleById("cur") } returns current.toArticleWithFeed()

        val vm = createViewModel()
        vm.initData("cur")
        advanceUntilIdle()

        assertEquals(ContrastingCoverageState.None, vm.contrastingCoverage.value)
    }

    @Test
    fun selectContrastPhrase_switchesToAnotherExtractedPhrase() = runTest(dispatcher) {
        val current = item("cur", "Elon Musk announces new Starlink deployment plan", feedId = "f1", feedName = "TechFeed")
        coEvery { repo.findArticleById("cur") } returns current.toArticleWithFeed()
        // Extracted phrases, longest first: "Elon Musk", "Starlink", "Musk". The candidate contains all of them.
        coEvery { articleDao.queryRelatedArticleListWithFeed(7, "cur", any(), 40) } returns listOf(
            item("opp1", "Critics warn Elon Musk Starlink plan poses risks", feedId = "f2", feedName = "OtherFeed"),
        )

        val vm = createViewModel()
        vm.initData("cur")
        advanceUntilIdle()

        val first = vm.contrastingCoverage.value as ContrastingCoverageState.Ready
        assertEquals("Elon Musk", first.selectedPhrase)
        assertTrue(first.phrases.containsAll(listOf("Elon Musk", "Starlink")))

        vm.selectContrastPhrase("Starlink")
        advanceUntilIdle()

        val updated = vm.contrastingCoverage.value as ContrastingCoverageState.Ready
        assertEquals("Starlink", updated.selectedPhrase)
        assertEquals(first.phrases, updated.phrases)
    }

    @Test
    fun switchingArticle_resetsContrastStateToIdleThenSearches() = runTest(dispatcher) {
        val cur1 = item("c1", "Starlink expands", feedId = "f1", feedName = "F1")
        val cur2 = item("c2", "nothing here", feedId = "f1", feedName = "F1")
        coEvery { repo.findArticleById("c1") } returns cur1.toArticleWithFeed()
        coEvery { repo.findArticleById("c2") } returns cur2.toArticleWithFeed()
        coEvery { articleDao.queryRelatedArticleListWithFeed(any(), any(), any(), any()) } returns emptyList()

        val vm = createViewModel()
        vm.initData("c1")
        advanceUntilIdle()

        vm.initData("c2")
        // Stale state from c1 is cleared
        advanceUntilIdle()
        assertEquals(ContrastingCoverageState.None, vm.contrastingCoverage.value)
    }
}
```

- [ ] **Step 2: Run red.** `./gradlew testDevDebugUnitTest --tests "*ReadingViewModelContrastTest"`. Expected: unresolved references.

- [ ] **Step 3: Implement in `ReadingViewModel.kt`**

Add imports:
```kotlin
import com.mckimquyen.reader.domain.steelman.ContrastingCoverageFinder
import com.mckimquyen.reader.domain.steelman.ContrastingCoverageState
import com.mckimquyen.reader.domain.steelman.TopicPhraseExtractor
```

In `ReadingViewModel`:
```kotlin
    private var contrastJob: Job? = null
    private val _contrastingCoverage = MutableStateFlow<ContrastingCoverageState>(ContrastingCoverageState.Idle)
    val contrastingCoverage: StateFlow<ContrastingCoverageState> = _contrastingCoverage.asStateFlow()
    private var currentContrastPhrases: List<String> = emptyList()
```

In `init { ... }` add a second, separate observer right after the existing ECHO-01 `viewModelScope.launch { ... }` block. It keys on the article id only, so a change of body text does not re-run the archive search:

```kotlin
        // ECHO-02: search the local archive for other angles whenever a different article is shown.
        viewModelScope.launch {
            _readingUiState
                .map { it.articleWithFeed }
                .distinctUntilChangedBy { it?.article?.id }
                .collect { loadContrastingCoverage(it) }
        }
```

Add `import kotlinx.coroutines.flow.distinctUntilChangedBy` next to the existing flow imports.

Implementation of `loadContrastingCoverage` and `selectContrastPhrase`:
```kotlin
    private fun loadContrastingCoverage(articleWithFeed: ArticleWithFeed?) {
        contrastJob?.cancel()
        _contrastingCoverage.value = ContrastingCoverageState.Idle
        currentContrastPhrases = emptyList()
        val item = articleWithFeed ?: return
        val article = item.article
        val feed = item.feed

        contrastJob = viewModelScope.launch {
            _contrastingCoverage.value = ContrastingCoverageState.Searching
            val phrases = withContext(defaultDispatcher) {
                TopicPhraseExtractor.extract(article.title, feed.name)
            }
            if (phrases.isEmpty()) {
                _contrastingCoverage.value = ContrastingCoverageState.None
                return@launch
            }
            currentContrastPhrases = phrases
            queryContrastForPhrase(article.id, article.accountId, feed.id, phrases.first(), phrases)
        }
    }

    fun selectContrastPhrase(phrase: String) {
        val article = _readingUiState.value.articleWithFeed?.article ?: return
        val feed = _readingUiState.value.articleWithFeed?.feed ?: return
        if (phrase !in currentContrastPhrases) return
        contrastJob?.cancel()
        contrastJob = viewModelScope.launch {
            _contrastingCoverage.value = ContrastingCoverageState.Searching
            queryContrastForPhrase(article.id, article.accountId, feed.id, phrase, currentContrastPhrases)
        }
    }

    private suspend fun queryContrastForPhrase(
        currentArticleId: String,
        accountId: Int,
        currentFeedId: String,
        phrase: String,
        allPhrases: List<String>,
    ) {
        val dao = articleDao
        if (dao == null) {
            _contrastingCoverage.value = ContrastingCoverageState.None
            return
        }
        try {
            val escaped = BountyQuerySanitizer.escapeLikeLiteral(phrase)
            val candidates = withContext(ioDispatcher) {
                dao.queryRelatedArticleListWithFeed(accountId, currentArticleId, escaped, ContrastingCoverageFinder.CANDIDATE_LIMIT)
            }
            val contrasting = withContext(defaultDispatcher) {
                ContrastingCoverageFinder.find(currentArticleId, currentFeedId, phrase, candidates)
            }
            _contrastingCoverage.value = if (contrasting.isNotEmpty()) {
                ContrastingCoverageState.Ready(allPhrases, phrase, contrasting)
            } else {
                ContrastingCoverageState.None
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("roy93~Contrast", "[queryContrastForPhrase] failed: $e")
            _contrastingCoverage.value = ContrastingCoverageState.None
        }
    }
```

In `initData`, inside the existing `if (_readingUiState.value.articleWithFeed?.article?.id != articleId) { ... }` block (the one that cancels `summaryJob`, `mindMapJob` and `deepReadJob`), add two lines so a stale card never lingers while the next article loads:

```kotlin
            contrastJob?.cancel()
            _contrastingCoverage.value = ContrastingCoverageState.Idle
```

- [ ] **Step 4: Run green.** `./gradlew testDevDebugUnitTest --tests "*ReadingViewModelContrastTest" --tests "*ReadingViewModel*" --rerun`; expected all pass.

- [ ] **Step 4b: Integration test with a real in-memory Room and two feeds.** The mocked-DAO tests above cannot prove that the candidate query and the finder cooperate on real rows (LIKE escaping, `ARTICLE_LIST_COLUMNS`, the feed join). Before writing it, read the existing `app/src/test/java/com/mckimquyen/reader/domain/repository/` Room tests (for example `BrainRpgRepositoryTest.setUp`) and reuse their `Room.inMemoryDatabaseBuilder(context, AndroidDatabase::class.java).allowMainThreadQueries()` set-up and their way of inserting `Feed` and `Article` rows through `database.feedDao()` and `database.articleDao()`. Create `app/src/test/java/com/mckimquyen/reader/domain/steelman/ContrastingCoverageRoomIntegrationTest.kt` (Robolectric, `@Config(sdk = [34], application = Application::class)`) that inserts two feeds and these articles: a current article from feed A ("Musk expands Starlink coverage"), a contrasting one from feed B ("Scientists warn Starlink disrupts research"), a same-feed contrasting one from feed A ("Insiders warn Starlink costs rise"), and an unrelated one from feed B ("Weather warns of storm"). Call `articleDao.queryRelatedArticleListWithFeed(accountId, currentId, BountyQuerySanitizer.escapeLikeLiteral("Starlink"), ContrastingCoverageFinder.CANDIDATE_LIMIT)` and pass the rows to `ContrastingCoverageFinder.find(...)`. Assert that exactly the feed-B Starlink article is returned with cue `warn`, and add a second test with a phrase containing `%` and `_` (for example "100%_off") that asserts no unrelated row matches. Use exactly the `Feed(...)` and `Article(...)` constructors that compile in the repository tests; if a required constructor argument is unknown, read the entity file rather than guessing.

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModel.kt app/src/test/java/com/mckimquyen/reader/ui/page/home/read/ReadingViewModelContrastTest.kt
git commit -m "feat(echo-02): manage contrasting coverage state in ReadingViewModel"
```

---

### Task 5: UI: `ContrastingCoverageCard` & `Content.kt` / `ReadingPage.kt` Wiring

**Files:**
- Create `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/steelman/ContrastingCoverageCard.kt`
- Create `app/src/test/java/com/mckimquyen/reader/ui/page/home/read/steelman/ContrastingCoverageCardTest.kt`
- Modify `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/Content.kt`
- Modify `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingPage.kt`

- [ ] **Step 1: Write failing Compose tests for `ContrastingCoverageCard`**

Create `app/src/test/java/com/mckimquyen/reader/ui/page/home/read/steelman/ContrastingCoverageCardTest.kt`:

```kotlin
package com.mckimquyen.reader.ui.page.home.read.steelman

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mckimquyen.reader.domain.steelman.ContrastingArticle
import com.mckimquyen.reader.domain.steelman.ContrastingCoverageState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContrastingCoverageCardTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val readyState = ContrastingCoverageState.Ready(
        phrases = listOf("Starlink", "SpaceX"),
        selectedPhrase = "Starlink",
        items = listOf(
            ContrastingArticle("a1", "Astronomers warn Starlink disrupts night sky", "ScienceFeed", Date(1000L), "warn"),
            ContrastingArticle("a2", "Regulator disputes satellite coverage claim", "Wired", Date(2000L), "disputes"),
        ),
    )

    @Test
    fun card_displaysTitle_chips_articles_andLimitation() {
        compose.setContent {
            ContrastingCoverageCard(
                state = readyState,
                onSelectPhrase = {},
                onOpenArticle = {},
            )
        }

        compose.onNodeWithTag(ECHO_CONTRAST_CARD_TAG).assertIsDisplayed()
        compose.onNodeWithText("Astronomers warn Starlink disrupts night sky", substring = true).assertIsDisplayed()
        compose.onNodeWithText("ScienceFeed", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Not a fact-check", substring = true).assertIsDisplayed()
    }

    @Test
    fun clickingChip_firesPhraseCallback() {
        var selected = ""
        compose.setContent {
            ContrastingCoverageCard(
                state = readyState,
                onSelectPhrase = { selected = it },
                onOpenArticle = {},
            )
        }

        compose.onNodeWithTag("${ECHO_CONTRAST_CHIP_PREFIX}SpaceX").performClick()
        assertEquals("SpaceX", selected)
    }

    @Test
    fun clickingArticleRow_firesOpenCallback() {
        var opened = ""
        compose.setContent {
            ContrastingCoverageCard(
                state = readyState,
                onSelectPhrase = {},
                onOpenArticle = { opened = it },
            )
        }

        compose.onNodeWithTag("${ECHO_CONTRAST_ROW_PREFIX}a1").performClick()
        assertEquals("a1", opened)
    }

    @Test
    fun card_notDrawn_whenStateIsNotReady() {
        compose.setContent {
            ContrastingCoverageSection(
                state = ContrastingCoverageState.None,
                enabled = true,
                onSelectPhrase = {},
                onOpenArticle = {},
            )
        }
        compose.onAllNodesWithTag(ECHO_CONTRAST_CARD_TAG).assertCountEquals(0)
    }

    @Test
    fun card_notDrawn_whenDisabled() {
        compose.setContent {
            ContrastingCoverageSection(
                state = readyState,
                enabled = false,
                onSelectPhrase = {},
                onOpenArticle = {},
            )
        }
        compose.onAllNodesWithTag(ECHO_CONTRAST_CARD_TAG).assertCountEquals(0)
    }
}
```

- [ ] **Step 2: Run red.** `./gradlew testDevDebugUnitTest --tests "*ContrastingCoverageCardTest"`. Expected: unresolved references.

- [ ] **Step 3: Implement `ContrastingCoverageCard.kt`**

Create `app/src/main/java/com/mckimquyen/reader/ui/page/home/read/steelman/ContrastingCoverageCard.kt`:

```kotlin
package com.mckimquyen.reader.ui.page.home.read.steelman

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.steelman.ContrastingArticle
import com.mckimquyen.reader.domain.steelman.ContrastingCoverageState
import java.text.DateFormat

const val ECHO_CONTRAST_CARD_TAG = "echo_contrast_card"
const val ECHO_CONTRAST_CHIP_PREFIX = "echo_contrast_chip_"
const val ECHO_CONTRAST_ROW_PREFIX = "echo_contrast_row_"

private const val MIN_TOUCH_TARGET_DP = 48

@Composable
fun ContrastingCoverageSection(
    state: ContrastingCoverageState,
    enabled: Boolean,
    onSelectPhrase: (String) -> Unit,
    onOpenArticle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!enabled) return
    val ready = state as? ContrastingCoverageState.Ready ?: return
    ContrastingCoverageCard(
        state = ready,
        onSelectPhrase = onSelectPhrase,
        onOpenArticle = onOpenArticle,
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ContrastingCoverageCard(
    state: ContrastingCoverageState.Ready,
    onSelectPhrase: (String) -> Unit,
    onOpenArticle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardDesc = stringResource(R.string.echo_contrast_card_desc, state.items.size)
    val context = LocalContext.current
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM, context.resources.configuration.locales[0]) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = cardDesc }
            .testTag(ECHO_CONTRAST_CARD_TAG),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "⚖️ " + stringResource(R.string.echo_contrast_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.echo_contrast_limitation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            if (state.phrases.size > 1) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.phrases.forEach { phrase ->
                        val chipDesc = stringResource(R.string.echo_contrast_switch_phrase_desc, phrase)
                        FilterChip(
                            selected = phrase == state.selectedPhrase,
                            onClick = { onSelectPhrase(phrase) },
                            label = { Text(phrase) },
                            modifier = Modifier
                                .semantics { contentDescription = chipDesc }
                                .testTag("$ECHO_CONTRAST_CHIP_PREFIX$phrase"),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            state.items.forEach { item ->
                ContrastingArticleRow(
                    item = item,
                    dateFormat = dateFormat,
                    onOpenArticle = onOpenArticle,
                )
            }
        }
    }
}

@Composable
private fun ContrastingArticleRow(
    item: ContrastingArticle,
    dateFormat: DateFormat,
    onOpenArticle: (String) -> Unit,
) {
    val openDesc = stringResource(R.string.echo_contrast_open_article_desc, item.title, item.feedName)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MIN_TOUCH_TARGET_DP.dp)
            .clickable { onOpenArticle(item.articleId) }
            .padding(vertical = 8.dp)
            .semantics { contentDescription = openDesc }
            .testTag("$ECHO_CONTRAST_ROW_PREFIX${item.articleId}"),
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${item.feedName} • ${dateFormat.format(item.date)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Text(
                    text = item.cue,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}
```

- [ ] **Step 4: Wire into `Content.kt` and `ReadingPage.kt`**

In `Content.kt`:
- Add imports:
  ```kotlin
  import com.mckimquyen.reader.domain.steelman.ContrastingCoverageState
  import com.mckimquyen.reader.ui.page.home.read.steelman.ContrastingCoverageSection
  ```
- Add parameters to `fun Content(...)` with defaults at the end:
  ```kotlin
      contrastingCoverageState: ContrastingCoverageState = ContrastingCoverageState.Idle,
      onSelectContrastPhrase: (String) -> Unit = {},
      onOpenContrastArticle: (String) -> Unit = {},
  ```
- In `LazyColumn`: right above the `BrainQuizCard` item (around line 230), add:
  ```kotlin
              item {
                  ContrastingCoverageSection(
                      state = contrastingCoverageState,
                      enabled = headlineSignalsEnabled,
                      onSelectPhrase = onSelectContrastPhrase,
                      onOpenArticle = onOpenContrastArticle,
                      modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                  )
              }
  ```

In `ReadingPage.kt`:
- Collect state from `readingViewModel`:
  ```kotlin
  val contrastingCoverage by readingViewModel.contrastingCoverage.collectAsState()
  ```
- Pass into `Content(...)`:
  ```kotlin
      contrastingCoverageState = contrastingCoverage,
      onSelectContrastPhrase = { readingViewModel.selectContrastPhrase(it) },
      onOpenContrastArticle = { targetId -> navController.navigate("${RouteName.READING}/$targetId") },
  ```

- [ ] **Step 5: Run green.** `./gradlew testDevDebugUnitTest --tests "*ContrastingCoverageCardTest" --tests "*Content*" --rerun`. Then `./gradlew compileDevDebugKotlin`.

- [ ] **Step 6: Commit.**

```bash
git add app/src/main/java/com/mckimquyen/reader/ui/page/home/read/steelman app/src/main/java/com/mckimquyen/reader/ui/page/home/read/Content.kt app/src/main/java/com/mckimquyen/reader/ui/page/home/read/ReadingPage.kt app/src/test/java/com/mckimquyen/reader/ui/page/home/read/steelman
git commit -m "feat(echo-02): build ContrastingCoverageCard and wire into reader content"
```

---

### Task 6: Real-data Probe, Device Instrumentation, Pixel Smoke, Documentation

- [ ] **Step 1: Write instrumentation test `ContrastingCoverageDeviceTest.kt`**

Create `app/src/androidTest/java/com/mckimquyen/reader/ui/page/home/read/steelman/ContrastingCoverageDeviceTest.kt`:

```kotlin
package com.mckimquyen.reader.ui.page.home.read.steelman

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.steelman.ContrastingArticle
import com.mckimquyen.reader.domain.steelman.ContrastingCoverageState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class ContrastingCoverageDeviceTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val state = ContrastingCoverageState.Ready(
        phrases = listOf("Starlink", "SpaceX"),
        selectedPhrase = "Starlink",
        items = listOf(
            ContrastingArticle("a1", "Scientists warn Starlink disrupts research", "Science Wire", Date(1_000L), "warn"),
        ),
    )

    @Test
    fun card_rendersOnRealRenderer_andChipAndRowFireCallbacks() {
        var chosenPhrase = ""
        var openedId = ""
        compose.setContent {
            ContrastingCoverageCard(
                state = state,
                onSelectPhrase = { chosenPhrase = it },
                onOpenArticle = { openedId = it },
            )
        }

        compose.onNodeWithTag(ECHO_CONTRAST_CARD_TAG).assertIsDisplayed()
        compose.onNodeWithTag("${ECHO_CONTRAST_CHIP_PREFIX}SpaceX").performClick()
        compose.onNodeWithTag("${ECHO_CONTRAST_ROW_PREFIX}a1").performClick()

        assertEquals("SpaceX", chosenPhrase)
        assertEquals("a1", openedId)
    }
}
```

- [ ] **Step 2: Temporary real-data probe against a copy of the Pixel archive (never committed)**

Export the backup to JSON on the host (`/tmp/echo02-real/articles.json`) with `sqlite3 -json Reader.db "select a.id as id, a.title as title, a.shortDescription as short, a.date as date, a.feedId as feedId, f.name as feedName from article a join feed f on f.id=a.feedId"` (fold the WAL first with `PRAGMA wal_checkpoint(TRUNCATE)` on a copy). Then create `app/src/test/java/com/mckimquyen/reader/domain/steelman/Echo02RealDataProbeTest.kt`:

```kotlin
package com.mckimquyen.reader.domain.steelman

import org.json.JSONArray
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Echo02RealDataProbeTest {
    @Test fun probe() {
        val rows = JSONArray(File("/tmp/echo02-real/articles.json").readText())
        var withPhrase = 0
        var withCue = 0
        val phraseCounts = HashMap<String, Int>()
        val cues = HashMap<String, Int>()
        val feeds = HashSet<String>()
        for (i in 0 until rows.length()) {
            val r = rows.getJSONObject(i)
            val title = r.optString("title")
            feeds += r.optString("feedId")
            val phrases = TopicPhraseExtractor.extract(title, r.optString("feedName"))
            if (phrases.isNotEmpty()) withPhrase++
            phrases.forEach { phraseCounts.merge(it, 1, Int::plus) }
            ContrarianCueLexicon.find(title)?.let { withCue++; cues.merge(it, 1, Int::plus) }
        }
        println("PROBE articles=${rows.length()} feeds=${feeds.size} withPhrase=$withPhrase withCue=$withCue")
        println("PROBE topPhrases=" + phraseCounts.entries.sortedByDescending { it.value }.take(25).joinToString { "${it.key}x${it.value}" })
        println("PROBE cues=$cues")
    }
}
```

Run `./gradlew testDevDebugUnitTest --tests "*Echo02RealDataProbeTest"`, read stdout from `app/build/test-results/testDevDebugUnitTest/TEST-com.mckimquyen.reader.domain.steelman.Echo02RealDataProbeTest.xml` (Gradle swallows stdout), and inspect the phrase list for junk (stop words, feed names, one-word generic titles like "Watch"). Fix `TopicPhraseExtractor` (not the probe) when junk appears and add a regression test per fix. Record the real numbers. With a single feed the finder cannot return anything on this data, so report that instead of claiming end-to-end success. Delete the probe file and `/tmp/echo02-real` before committing.

- [ ] **Step 3: Full verification**
  - `./gradlew testDevDebugUnitTest --rerun`
  - `./gradlew lintDevDebug --rerun-tasks`
  - `./gradlew assembleDevDebug assembleDevDebugAndroidTest`
  - Record exact unit test counts and lint warnings.

- [ ] **Step 4: Device instrumentation**
  - Confirm `adb devices -l` lists `2B051FDH3006MU` and model is `Pixel 7 Pro`.
  - Install debug and test APKs.
  - Run: `adb -s 2B051FDH3006MU shell am instrument -w -e class com.mckimquyen.reader.ui.page.home.read.steelman.ContrastingCoverageDeviceTest com.mckimquyen.reader.test/androidx.test.runner.AndroidJUnitRunner`.
  - Uninstall test package.

- [ ] **Step 5: Smoke test on the Pixel with seeded second feed**
  - Back up `Reader` (all three files: main + wal + shm) and `settings.preferences_pb` with `adb exec-out run-as ... cat`. Require non-empty files and `PRAGMA integrity_check` = ok on a copy.
  - In a temporary working directory on the host, copy the three backed-up Room files, fold the WAL with `sqlite3 Reader "PRAGMA wal_checkpoint(TRUNCATE);"` and run the SQL below to add a second feed and one contrasting article:
    ```sql
    INSERT OR REPLACE INTO feed (id, name, url, groupId, accountId, isNotification, isFullContent)
    VALUES ('f_test', 'Science Wire', 'https://example.com/rss', '', 1, 0, 0);

    INSERT OR REPLACE INTO article (
        id, date, title, author, rawDescription, shortDescription, fullContent,
        img, link, feedId, accountId, isUnread, isStarred, isReadLater
    ) VALUES (
        'a_contrast_smoke',
        strftime('%s', 'now') * 1000,
        'Scientists warn Starlink constellation disrupts astronomical research',
        'Staff', '', 'New satellite launches raise concerns among global observatories.', '',
        NULL, 'https://example.com/starlink-warn', 'f_test', 1, 1, 0, 0
    );
    ```
    Also verify there is an existing BBC article on the same topic ("Starlink" or whatever topic the seeded article shares). If the backup BBC archive has no Starlink article, check titles with `sqlite3 Reader "SELECT id, title FROM article WHERE title LIKE '%warn%' OR title LIKE '%critics%';"`, pick a topic from the existing articles, and seed the opposing headline around that topic instead.
  - Push the seeded `Reader` file to `databases/Reader`, truncate `-wal` and `-shm` to zero size, and verify with a read-back that the two feeds exist.
  - Launch the app with `monkey`. Check `topResumedActivity`; close the startup ad via "Tiếp tục đến ứng dụng".
  - Open the BBC article that shares the topic with the seeded article.
  - Scroll down to the end of the article, right above the AI quiz card.
  - Confirm `ContrastingCoverageCard` appears, shows the topic chip, shows the "Science Wire" article, and shows the small badge with the cue (`warn`).
  - Tap the article row: confirm it navigates to the contrasting article (`a_contrast_smoke`).
  - Go to Settings -> Headline Signals: toggle off. Reopen the article: confirm the card is hidden. Turn the toggle back on.
  - Force-stop the app.
  - Restore BOTH backups with the quoted `run-as sh -c 'cat > ...'` form for all three Room files and `settings.preferences_pb`.
  - Verify with `cmp` across all files, and verify `PRAGMA integrity_check` on `databases/Reader`.
  - Confirm the test package is not installed.
- [ ] **Step 6: Completion docs**
  - Write `doc/task/done/ECHO-02_steelman-rebuttal-box_DONE.md` with honest limits (lexical matching, archive-only, single-feed archive naturally shows nothing without second source, no AI/rebuttal generation), exact commits, test counts.
  - Update `doc/feature.md` with ECHO-02 entry.
  - Update `doc/task/todo/16_ECHOCHAMBER_AND_BIAS_COMPASS.md` row for ECHO-02 to Done.
  - Delete `doc/task/todo/ECHO-02_steelman-rebuttal-box.md` with `git rm`.

- [ ] **Step 7: Commit.**

```bash
git add app/src doc docs/superpowers/plans/2026-11-01-echo-02-contrasting-coverage.md
git commit -m "feat(echo-02): complete contrasting coverage from local archive"
```

## Final Verification Checklist

- [ ] `WholePhraseMatcher`: word boundary non-letter/digit, case-insensitive, no substring false positives.
- [ ] `ContrarianCueLexicon`: EN & VI cues detected accurately, no substring false positives.
- [ ] `TopicPhraseExtractor`: runs of 1-3 capitalized words, min length 4, excludes feed name & stop words, max 3 phrases.
- [ ] `ContrastingCoverageFinder`: requires different feedId, different articleId, whole-phrase match, contrarian cue, newest first, max 5.
- [ ] `ReadingViewModel`: observes article changes, queries on IO, filters on Default, exposes StateFlow.
- [ ] `ContrastingCoverageCard`: Compose UI with chips and rows, gated by `LocalHeadlineSignalsEnabled`.
- [ ] 8 strings across 38 locales; `StringResourceParityTest` 6/6.
- [ ] Room stays version 20; no schema change.
- [ ] Full unit suite, lint, builds pass; instrumentation passes on Pixel `2B051FDH3006MU`.
- [ ] Smoke verified card with seeded second feed on device copy; original backups restored and verified with `cmp`.
