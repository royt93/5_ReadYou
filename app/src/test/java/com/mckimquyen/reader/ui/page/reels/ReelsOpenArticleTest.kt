package com.mckimquyen.reader.ui.page.reels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * On the Pixel a right-swipe that started at the left edge (x=20) was taken by the system Back gesture and sent the user Home instead of opening the story,
 * while a swipe from further in worked. The card now also has a Read button, and the edge band is withheld from Back.
 */
class ReelsOpenArticleTest {
    private val dir: File = listOf(File("src/main/java/com/mckimquyen/reader/ui/page/reels"), File("app/src/main/java/com/mckimquyen/reader/ui/page/reels")).first { it.exists() }
    private val page = File(dir, "ReelsPage.kt").readText()
    private val card = File(dir, "ReelsCard.kt").readText()

    @Test
    fun cardHasAnOpenButtonThatCallsTheSameOpenAction() {
        assertTrue("ReelsCard must take onOpen", "onOpen: (() -> Unit)? = null" in card)
        assertTrue("the button needs a stable test tag", "REELS_OPEN_BUTTON_TAG" in card)
        assertTrue("the page must pass onOpen = { ... onOpenArticle(item.articleId) }", Regex("""onOpen = \{[^}]*onOpenArticle\(item\.articleId\)""").containsMatchIn(page))
    }

    @Test
    fun theSwipeStillOpensAndBothWaysAreLogged() {
        assertTrue("swipe path must still call onOpenArticle", "if (totalDrag >= openThresholdPx)" in page)
        assertTrue("swipe open must be traceable", "\"via\" to \"swipe\"" in page)
        assertTrue("button open must be traceable", "\"via\" to \"button\"" in page)
        assertTrue("too-short swipes must be traceable", "swipe-too-short" in page)
    }

    @Test
    fun theLeftEdgeBandIsExcludedWithinAndroidsLimit() {
        assertTrue("pager must call leftEdgeGestureExclusion()", "leftEdgeGestureExclusion()" in page)
        assertTrue("systemGestureExclusionRects must be set", "systemGestureExclusionRects" in page)
        val height = Regex("""LEFT_EDGE_EXCLUSION_HEIGHT = (\d+)""").find(page)!!.groupValues[1].toInt()
        assertEquals("Android honours at most 200dp per edge", true, height <= 200)
    }

    @Test
    fun theHintTellsTheUserAboutTheButton() {
        val en = listOf(File("src/main/res/values/strings.xml"), File("app/src/main/res/values/strings.xml")).first { it.exists() }
        val text = en.readText()
        assertTrue(text.contains("name=\"reels_open_article\""))
        assertTrue(Regex("""name="reels_swipe_hint">[^<]*tap Read""").containsMatchIn(text))
    }
}
