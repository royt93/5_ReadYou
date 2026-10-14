package com.mckimquyen.reader.ui.page.home.flow

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.article.Article
import com.mckimquyen.reader.domain.model.article.ArticleWithFeed
import com.mckimquyen.reader.domain.model.cluster.StoryCluster
import com.mckimquyen.reader.domain.model.feed.Feed
import com.mckimquyen.reader.ui.component.cluster.StoryClusterSheetContent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date
import java.util.concurrent.atomic.AtomicReference

/** Exercises the actual cluster sheet callbacks used by Flow -> Reading navigation, without mocking clustering. */
@RunWith(AndroidJUnit4::class)
class SourceDnaFlowIntegrationTest {
    private var scenario: ActivityScenario<ComponentActivity>? = null

    @After fun tearDown() { scenario?.close() }

    @Test fun sourceDnaNodeRoutesToSelectedArticle_andLeadActionStillRoutesToLead() {
        val articles = (0 until 3).map { index ->
            val feed = Feed(id = "feed_$index", name = "Feed $index", url = "https://feed$index.example/rss", groupId = "group", accountId = 1)
            ArticleWithFeed(
                article = Article(
                    id = "article_$index",
                    date = Date(1_000L + index * 1_000L),
                    title = "Shared event report article $index",
                    rawDescription = "Shared event details for report $index",
                    shortDescription = "Shared event details for report $index",
                    link = "https://feed$index.example/article_$index",
                    feedId = feed.id,
                    accountId = 1,
                ),
                feed = feed,
            )
        }
        val cluster = StoryCluster(
            id = "cluster_source_dna",
            title = "Shared event",
            leadArticle = articles.first(),
            articles = articles,
            sourceCount = articles.map { it.feed.id }.distinct().size,
            articleCount = articles.size,
        )
        val selected = AtomicReference<String?>(null)
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario!!.onActivity { activity ->
            activity.setContentView(ComposeView(activity).apply {
                setContent {
                    StoryClusterSheetContent(
                        cluster = cluster,
                        onArticleClick = { selected.set(it.article.id) },
                        onMarkAllRead = {},
                        onClose = {},
                        calculateSimilarity = { parent, child ->
                            if (parent.article.id == "article_0" && child.article.id == "article_1") 0.86f else 0.1f
                        },
                    )
                }
            })
        }
        val action = scenarioText(com.mckimquyen.reader.R.string.source_dna_action)
        assertTrue(await { click(action) })
        assertTrue(await { texts().any { it.contains(scenarioText(com.mckimquyen.reader.R.string.source_dna_disclaimer)) } })
        assertTrue(await { click("Shared event report article 1") })
        assertEquals("article_1", selected.get())

        // New sheet invocation with the same cluster: existing lead-article button remains unchanged.
        selected.set(null)
        scenario!!.onActivity { activity ->
            activity.setContentView(ComposeView(activity).apply {
                setContent {
                    StoryClusterSheetContent(cluster, { selected.set(it.article.id) }, {}, {})
                }
            })
        }
        assertTrue(await { click(scenarioText(com.mckimquyen.reader.R.string.story_cluster_read_main)) })
        assertEquals(cluster.leadArticle.article.id, selected.get())
    }

    private fun scenarioText(id: Int) = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun await(block: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + 5_000L
        while (System.currentTimeMillis() < end) { if (block()) return true; Thread.sleep(30) }
        return block()
    }

    @OptIn(androidx.compose.ui.InternalComposeUiApi::class)
    private fun texts(): List<String> {
        val values = AtomicReference<List<String>>(emptyList())
        scenario?.onActivity { activity ->
            val root = findComposeView(activity.window.decorView)?.semanticsOwner?.rootSemanticsNode
            values.set(root?.let(::collectTexts).orEmpty())
        }
        return values.get()
    }

    @OptIn(androidx.compose.ui.InternalComposeUiApi::class)
    private fun click(text: String): Boolean {
        val result = AtomicReference(false)
        scenario?.onActivity { activity ->
            val root = findComposeView(activity.window.decorView)?.semanticsOwner?.rootSemanticsNode ?: return@onActivity
            val node = findClickable(root, text) ?: return@onActivity
            if (node.config.getOrNull(SemanticsProperties.Disabled) == null) {
                node.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke()
                result.set(true)
            }
        }
        return result.get()
    }

    private fun collectTexts(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty() + node.children.flatMap(::collectTexts)

    private fun findClickable(node: SemanticsNode, text: String): SemanticsNode? {
        val hasText = node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.contains(text) } == true
        if (hasText && node.config.getOrNull(SemanticsActions.OnClick) != null) return node
        return node.children.firstNotNullOfOrNull { findClickable(it, text) }
    }

    @androidx.compose.ui.InternalComposeUiApi
    @OptIn(androidx.compose.ui.InternalComposeUiApi::class)
    private fun findComposeView(view: View): ViewRootForTest? {
        if (view is ViewRootForTest) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findComposeView(view.getChildAt(i))?.let { return it }
        return null
    }
}
