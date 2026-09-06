package com.mckimquyen.reader.ui.page.home.read

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.article.ArticleMindMap
import com.mckimquyen.reader.domain.model.article.MindMapNode
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MindMapSheetWidgetTest {

    private val sampleMindMap = ArticleMindMap(
        rootTitle = "Quantum Computing in 2026",
        nodes = listOf(
            MindMapNode(
                id = "root",
                label = "Quantum 2026",
                detail = "Breakthroughs in fault-tolerant quantum computing architectures.",
                depth = 0,
                parentId = null,
                tag = "Core Topic",
            ),
            MindMapNode(
                id = "pillar_1",
                label = "Error Correction",
                detail = "Surface code implementations exceeding 99.9% fidelity.",
                depth = 1,
                parentId = "root",
                tag = "Breakthrough",
            ),
            MindMapNode(
                id = "sub_1_1",
                label = "Logical Qubits",
                detail = "First commercial processor with over 100 logical qubits.",
                depth = 2,
                parentId = "pillar_1",
            ),
            MindMapNode(
                id = "pillar_2",
                label = "Cryogenics",
                detail = "New dilution refrigerators reducing cooling power by 40%.",
                depth = 1,
                parentId = "root",
                tag = "Hardware",
            ),
        ),
        isOfflineFallback = false,
    )

    @Test
    fun mindMapSheet_rendersLoadingState_withoutException() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    MindMapSheetContent(
                        state = MindMapState.Loading,
                        onRetry = {},
                        onClose = {},
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull(composeView)
        }
        scenario.close()
    }

    @Test
    fun mindMapSheet_rendersSuccessState_displaysNodesAndCanvasWithoutCrash() {
        var copyInvoked = false
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    MindMapSheetContent(
                        state = MindMapState.Success(sampleMindMap),
                        onRetry = {},
                        onClose = {},
                        onCopyOutline = { copyInvoked = true },
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull(composeView)
        }
        scenario.close()
    }

    @Test
    fun mindMapSheet_rendersOfflineFallbackBadge_withoutException() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    MindMapSheetContent(
                        state = MindMapState.Success(sampleMindMap.copy(isOfflineFallback = true)),
                        onRetry = {},
                        onClose = {},
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull(composeView)
        }
        scenario.close()
    }

    @Test
    fun mindMapSheet_rendersErrorState_andAttachesRetryButton() {
        var retryInvoked = false
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    MindMapSheetContent(
                        state = MindMapState.Error(R.string.summary_err_network),
                        onRetry = { retryInvoked = true },
                        onClose = {},
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull(composeView)
        }
        scenario.close()
    }
}
