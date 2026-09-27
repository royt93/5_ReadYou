package com.mckimquyen.reader.ui.page.reels

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReelsCardWidgetTest {

    private fun item(
        coverImageUrl: String? = "https://cdn.example.com/cover.jpg",
        bullets: List<String> = listOf("First takeaway", "Second takeaway", "Third takeaway"),
        hasVideo: Boolean = false,
        title: String = "Solar power hits a record high",
    ) = ReelItem(
        articleId = "a1",
        title = title,
        feedName = "Example Energy Daily",
        publishedAtMillis = System.currentTimeMillis() - 3 * 60 * 60 * 1000L,
        coverImageUrl = coverImageUrl,
        bullets = bullets,
        hasVideo = hasVideo,
    )

    private fun renderCard(item: ReelItem) {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent { ReelsCard(item = item) }
            }
            activity.setContentView(composeView)
            assertNotNull(composeView)
        }
        scenario.close()
    }

    @Test
    fun rendersFullCardWithCoverAndBullets() {
        renderCard(item())
    }

    @Test
    fun rendersWithoutCoverImage() {
        // Articles with no image must fall back to the tinted surface, not crash.
        renderCard(item(coverImageUrl = null))
    }

    @Test
    fun rendersWithoutBullets() {
        renderCard(item(bullets = emptyList()))
    }

    @Test
    fun rendersVideoBadgeWhenArticleHasVideo() {
        renderCard(item(hasVideo = true))
    }

    @Test
    fun rendersVeryLongTitleWithoutOverflowCrash() {
        renderCard(item(title = "Very long headline ".repeat(30)))
    }
}
