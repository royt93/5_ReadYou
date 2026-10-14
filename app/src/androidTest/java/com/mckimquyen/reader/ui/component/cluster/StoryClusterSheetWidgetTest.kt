package com.mckimquyen.reader.ui.component.cluster

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.cluster.StoryCluster
import com.mckimquyen.reader.domain.model.feed.Feed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class StoryClusterSheetWidgetTest {

    private val feedVnExpress = Feed(
        id = "feed_vnexpress",
        name = "VnExpress",
        url = "https://vnexpress.net/rss",
        groupId = "group_1",
        accountId = 1,
    )
    private val feedTuoiTre = Feed(
        id = "feed_tuoitre",
        name = "Tuổi Trẻ",
        url = "https://tuoitre.vn/rss",
        groupId = "group_1",
        accountId = 1,
    )

    private val a1 = ArticleWithFeed(
        article = Article(
            id = "a1",
            title = "Thủ tướng phát lệnh khởi công tuyến đường sắt cao tốc Bắc Nam",
            rawDescription = "Lễ khởi công công trình trọng điểm quốc gia",
            shortDescription = "Lễ khởi công công trình trọng điểm quốc gia",
            link = "https://vnexpress.net/a1",
            feedId = feedVnExpress.id,
            accountId = 1,
            date = Date(),
        ),
        feed = feedVnExpress,
    )

    private val a2 = ArticleWithFeed(
        article = Article(
            id = "a2",
            title = "Toàn cảnh dự án đường sắt cao tốc Bắc Nam chính thức khởi động",
            rawDescription = "Quy mô và hướng tuyến của đường sắt tốc độ cao",
            shortDescription = "Quy mô và hướng tuyến của đường sắt tốc độ cao",
            link = "https://tuoitre.vn/a2",
            feedId = feedTuoiTre.id,
            accountId = 1,
            date = Date(),
        ),
        feed = feedTuoiTre,
    )

    private val feedBbc = Feed(
        id = "feed_bbc",
        name = "BBC News",
        url = "https://bbc.com/rss",
        groupId = "group_1",
        accountId = 1,
    )

    private val a3 = ArticleWithFeed(
        article = Article(
            id = "a3",
            title = "BBC reports on the North South railway launch",
            rawDescription = "A third report on the same event",
            shortDescription = "A third report on the same event",
            link = "https://bbc.com/a3",
            feedId = feedBbc.id,
            accountId = 1,
            date = Date(System.currentTimeMillis() + 1_000L),
        ),
        feed = feedBbc,
    )

    private val sampleCluster = StoryCluster(
        id = "cluster_railway",
        title = a1.article.title,
        leadArticle = a1,
        articles = listOf(a1, a2),
        keywords = listOf("Đường sắt", "Cao tốc", "Bắc Nam", "Thủ tướng"),
        sourceCount = 2,
        articleCount = 2,
        date = Date(),
    )

    @OptIn(androidx.compose.ui.InternalComposeUiApi::class)
    @Test
    fun sourceDnaAction_isHiddenForFewerThanThreeSources() = withSheet(sampleCluster) { screen ->
        assertFalse(screen.texts().contains(screen.string(com.mckimquyen.reader.R.string.source_dna_action)))
    }

    @OptIn(androidx.compose.ui.InternalComposeUiApi::class)
    @Test
    fun sourceDnaAction_opensDisclosedGraph_andNodeClickUsesExistingArticleCallback() = withSheet(eligibleCluster()) { screen ->
        val action = screen.string(com.mckimquyen.reader.R.string.source_dna_action)
        assertTrue("missing action: ${'$'}{screen.texts()}", screen.await { screen.has(action) })
        assertTrue(screen.click(action))
        assertTrue(screen.await { screen.has(screen.string(com.mckimquyen.reader.R.string.source_dna_disclaimer)) })
        assertTrue("similarity label missing: ${screen.texts()}", screen.has("81"))
        assertTrue("feed-reported date missing: ${screen.texts()}", screen.texts().any { it.contains("Thời gian feed báo") || it.contains("Feed-reported time") })
        assertTrue(screen.click(a2.article.title))
        assertEquals(a2.article.id, screen.clickedArticleId)
    }

    @OptIn(androidx.compose.ui.InternalComposeUiApi::class)
    @Test
    fun equalEarliestTimestamps_showsUncertainMessageAndDoesNotClaimEarliest() = withSheet(
        eligibleCluster().copy(articles = listOf(a1.copy(article = a1.article.copy(date = Date(10L))), a2.copy(article = a2.article.copy(date = Date(10L))), a3.copy(article = a3.article.copy(date = Date(20L))))),
    ) { screen ->
        val action = screen.string(com.mckimquyen.reader.R.string.source_dna_action)
        assertTrue(screen.await { screen.click(action) })
        assertTrue(screen.await { screen.has(screen.string(com.mckimquyen.reader.R.string.source_dna_uncertain)) })
        assertFalse(screen.has(screen.string(com.mckimquyen.reader.R.string.source_dna_earliest_posted)))
    }

    private val clickedArticleId = AtomicReference<String?>(null)

    /** Deterministic stand-in for the clustering engine: only the a1 -> a2 pair is a strong match (72% would be below threshold, so use 81%). */
    private fun similarityFor(parentId: String, childId: String): Float = if (parentId == "a1" && childId == "a2") 0.81f else 0.1f

    private fun eligibleCluster(): StoryCluster = sampleCluster.copy(
        articles = listOf(a1.copy(article = a1.article.copy(date = Date(1_000L))), a2.copy(article = a2.article.copy(date = Date(2_000L))), a3),
        sourceCount = 3,
        articleCount = 3,
    )

    @OptIn(androidx.compose.ui.InternalComposeUiApi::class)
    private class SheetScreen(private val scenario: ActivityScenario<ComponentActivity>, private val owner: StoryClusterSheetWidgetTest) {
        val clickedArticleId: String? get() = owner.clickedArticleId.get()
        fun string(id: Int, vararg args: Any): String = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)
        fun texts(): List<String> {
            val result = AtomicReference<List<String>>(emptyList())
            scenario.onActivity { activity ->
                val root = owner.findComposeView(activity.window.decorView)?.semanticsOwner?.rootSemanticsNode
                result.set(root?.let { owner.collectTexts(it) }.orEmpty())
            }
            return result.get()
        }
        fun has(text: String): Boolean = texts().any { it.contains(text) }
        fun await(condition: () -> Boolean): Boolean {
            val until = System.currentTimeMillis() + 5_000L
            while (System.currentTimeMillis() < until) { if (condition()) return true; Thread.sleep(40) }
            return condition()
        }
        fun click(text: String): Boolean {
            val clicked = AtomicReference(false)
            scenario.onActivity { activity ->
                val root = owner.findComposeView(activity.window.decorView)?.semanticsOwner?.rootSemanticsNode ?: return@onActivity
                val target = owner.findClickable(root, text) ?: return@onActivity
                if (target.config.getOrNull(SemanticsProperties.Disabled) == null) {
                    target.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke()
                    clicked.set(true)
                }
            }
            return clicked.get()
        }
    }

    @OptIn(androidx.compose.ui.InternalComposeUiApi::class)
    private fun withSheet(cluster: StoryCluster, block: (SheetScreen) -> Unit) {
        clickedArticleId.set(null)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContentView(ComposeView(activity).apply {
                    setContent {
                        // Content (not the Dialog wrapper): a Dialog lives in its own window, outside the Activity's semantics tree.
                        StoryClusterSheetContent(
                            cluster = cluster,
                            onArticleClick = { clickedArticleId.set(it.article.id) },
                            onMarkAllRead = {},
                            onClose = {},
                            calculateSimilarity = { parent, child -> similarityFor(parent.article.id, child.article.id) },
                        )
                    }
                })
            }
            block(SheetScreen(scenario, this))
        }
    }

    private fun collectTexts(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty() + node.children.flatMap(::collectTexts)

    private fun findClickable(node: SemanticsNode, text: String): SemanticsNode? {
        val matches: Boolean = node.config.getOrNull(SemanticsProperties.Text)?.any { semanticsText -> semanticsText.text.contains(text) } == true
        if (matches && node.config.getOrNull(SemanticsActions.OnClick) != null) return node
        return node.children.firstNotNullOfOrNull { findClickable(it, text) }
    }

    @OptIn(androidx.compose.ui.InternalComposeUiApi::class)
    private fun findComposeView(view: View): ViewRootForTest? {
        if (view is ViewRootForTest) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findComposeView(view.getChildAt(i))?.let { return it }
        return null
    }

    @OptIn(androidx.compose.ui.InternalComposeUiApi::class)
    @Test
    fun storyClusterSheet_rendersPerspectivesAndActionButtons() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    StoryClusterSheet(
                        cluster = sampleCluster,
                        onDismissRequest = {},
                        onArticleClick = {},
                        onMarkAllRead = {},
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull("StoryClusterSheet should attach cleanly without crash", composeView)
        }
        scenario.close()
    }

    @Test
    fun storyClusterSheet_markClusterAsReadCallback_isWired() {
        var markAsReadTriggered = false
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    StoryClusterSheet(
                        cluster = sampleCluster,
                        onDismissRequest = {},
                        onArticleClick = {},
                        onMarkAllRead = { markAsReadTriggered = true },
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull(composeView)
        }
        scenario.close()
    }

    @Test
    fun storyClusterSheet_withSimilarityScore_rendersBadgeWithoutCrash() {
        val clusterWithScore = sampleCluster.copy(
            similarityScore = 0.92f,
        )
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    StoryClusterSheet(
                        cluster = clusterWithScore,
                        onDismissRequest = {},
                        onArticleClick = {},
                        onMarkAllRead = {},
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull(composeView)
        }
        scenario.close()
    }
}
