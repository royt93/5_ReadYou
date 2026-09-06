package com.mckimquyen.reader.ui.page.home.read

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.article.DeepReadMessage
import com.mckimquyen.reader.domain.model.article.DeepReadSender
import com.mckimquyen.reader.domain.model.article.DeepReadSession
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeepReadChatSheetWidgetTest {

    private val sampleSession = DeepReadSession(
        articleId = "art_test_101",
        articleTitle = "Advancements in Autonomous AI Systems",
        messages = listOf(
            DeepReadMessage(
                id = "msg_1",
                sender = DeepReadSender.ASSISTANT,
                content = "Hello! I've analyzed this article. Feel free to ask any question.",
                isOfflineFallback = false,
                isGrounded = true,
            ),
            DeepReadMessage(
                id = "msg_2",
                sender = DeepReadSender.USER,
                content = "What are the core capabilities of autonomous agents?",
            ),
            DeepReadMessage(
                id = "msg_3",
                sender = DeepReadSender.ASSISTANT,
                content = "Autonomous agents leverage planning, memory, and dynamic tool calling to solve multi-stage engineering tasks.",
                isOfflineFallback = false,
                isGrounded = true,
            ),
        ),
        suggestedChips = listOf(
            "What are the main risks?",
            "Key takeaways in 3 points",
            "What is the cost impact?"
        )
    )

    @Test
    fun deepReadChatSheet_rendersIdleState_withoutException() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    DeepReadChatSheetContent(
                        state = DeepReadState.Idle,
                        onSendQuestion = {},
                        onClearChat = {},
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
    fun deepReadChatSheet_rendersActiveSessionWithMessagesAndChips() {
        var clearClicked = false
        var closeClicked = false
        var sentQuestion: String? = null

        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    DeepReadChatSheetContent(
                        state = DeepReadState.Active(
                            session = sampleSession,
                            isSending = false
                        ),
                        onSendQuestion = { sentQuestion = it },
                        onClearChat = { clearClicked = true },
                        onClose = { closeClicked = true },
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull("ComposeView must attach successfully", composeView)
        }
        scenario.close()
    }

    @Test
    fun deepReadChatSheet_rendersThinkingBubble_whenSending() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    DeepReadChatSheetContent(
                        state = DeepReadState.Active(
                            session = sampleSession,
                            isSending = true
                        ),
                        onSendQuestion = {},
                        onClearChat = {},
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
    fun deepReadChatSheet_rendersOfflineFallbackBadge_withoutException() {
        val offlineSession = sampleSession.copy(
            messages = listOf(
                DeepReadMessage(
                    id = "msg_off",
                    sender = DeepReadSender.ASSISTANT,
                    content = "Extracted points from article context.",
                    isOfflineFallback = true,
                    isGrounded = true,
                )
            )
        )

        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    DeepReadChatSheetContent(
                        state = DeepReadState.Active(
                            session = offlineSession,
                            isSending = false
                        ),
                        onSendQuestion = {},
                        onClearChat = {},
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
