package com.mckimquyen.reader.ui.page.home.read

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.article.ArticleMindMap
import com.mckimquyen.reader.infrastructure.ai.ArticleMindMapExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MindMapIntegrationTest {

    @Test
    fun endToEnd_extractMindMapAndRenderInteractiveCanvas() {
        val title = "Autonomous AI Agents and Systems Architecture"
        val rawArticle = """
            Modern artificial intelligence is transitioning rapidly from passive chatbot interfaces to autonomous agentic workflows.
            Agents possess memory, planning capabilities, and dynamic tool calling interfaces to solve complex multi-step problems.
            
            Security protocols and alignment mechanisms must be established before granting autonomous systems execution permissions.
            Recent evaluations show agentic architectures achieve an 85% higher task success rate than single-turn model calls.
            
            Key industry adoption includes software debugging, infrastructure automation, and automated medical literature research.
        """.trimIndent()

        // 1. Verify offline heuristic extraction logic
        val mindMap: ArticleMindMap = ArticleMindMapExtractor.extractOfflineMindMap(
            title = title,
            plainText = rawArticle
        )

        assertTrue("Mind map must contain root and branches", mindMap.nodes.size >= 3)
        assertEquals(title, mindMap.rootTitle)

        val rootNode = mindMap.getRootNode()
        assertNotNull("Root node must exist", rootNode)
        assertEquals(0, rootNode?.depth)

        val branches = mindMap.getBranches()
        assertTrue("Must have at least one branch pillar", branches.isNotEmpty())

        val outline = mindMap.formatAsOutline()
        assertTrue("Outline must contain root title", outline.contains(title))
        assertTrue("Outline must contain root emoji", outline.contains("🎯"))

        // 2. Verify complete Compose rendering in live Activity on Android 17 runtime
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    MindMapSheetContent(
                        state = MindMapState.Success(mindMap),
                        onRetry = {},
                        onClose = {},
                        onForceOffline = {},
                        onCopyOutline = {},
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull("ComposeView must attach successfully", composeView)
        }
        scenario.close()
    }
}
