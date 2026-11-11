package com.mckimquyen.reader.ui.page.rpg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the privacy boundary where it is wired: Content.kt must hand the quiz the article's short description, never a
 * slice of its body, and must not call the removed header-quiz generator. Reads the source because Content is too heavy
 * to compose in a JVM test.
 */
class QuizWiringSourceTest {

    private val root = "src/main/java/com/mckimquyen/reader"
    private val content = File("$root/ui/page/home/read/Content.kt").readText()
    private val readingPage = File("$root/ui/page/home/read/ReadingPage.kt").readText()
    private val homeEntry = File("$root/ui/page/common/HomeEntry.kt").readText()

    @Test
    fun contentTakesAShortDescriptionParameter() {
        assertTrue(Regex("""shortDescription:\s*String\s*=\s*""""").containsMatchIn(content))
    }

    @Test
    fun theQuizIsAskedWithTheShortDescription_notWithTheBodyAsThePreview() {
        val call = Regex("""onQuizCardShown\(([^)]*)\)""").find(content)?.groupValues?.get(1).orEmpty()

        assertTrue("onQuizCardShown must be called from Content", call.isNotBlank())
        val args = call.split(",").map { it.trim() }
        assertEquals("articleId", args[0])
        assertEquals("title", args[1])
        assertEquals("the third argument is the preview and must be the short description", "shortDescription", args[2])
        assertEquals("the body is only for choosing the XP category on the device", "content", args[3])
    }

    @Test
    fun readingPagePassesTheArticlesShortDescriptionDown() {
        assertTrue(readingPage.contains("shortDescription = readingUiState.articleWithFeed.article.shortDescription"))
    }

    @Test
    fun theOldHeadlineQuizPathIsGoneFromContent() {
        assertFalse(content.contains("buildQuiz"))
        assertFalse(content.contains("generateQuiz"))
        assertFalse(content.contains("BrainQuizCard("))
    }

    @Test
    fun contentUsesTheHostForEveryQuizState() {
        assertTrue(content.contains("BrainQuizHost("))
        assertTrue(content.contains("quizState"))
    }

    @Test
    fun theCallIsInsideTheLazyItemSoItOnlyRunsWhenTheCardIsComposed() {
        val hostAt = content.indexOf("BrainQuizHost(")
        assertTrue("the host must be used in Content", hostAt > 0)
        // Everything between the opening of the item that holds the host and the host itself.
        val sameItem = content.substring(0, hostAt).substringAfterLast("item {")
        assertTrue("LaunchedEffect with the article id must sit in the same item as the host", sameItem.contains("LaunchedEffect(articleId"))
    }

    @Test
    fun privacySettingsCanBeOpenedFromTheCard() {
        assertTrue(content.contains("LocalOpenPrivacySettings.current"))
        assertTrue(homeEntry.contains("LocalOpenPrivacySettings provides"))
        assertTrue(homeEntry.contains("RouteName.PRIVACY_SETTINGS"))
    }
}
