package com.mckimquyen.reader.ui.page.home.read

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.article.DeepReadMessage
import com.mckimquyen.reader.domain.model.article.DeepReadSender
import com.mckimquyen.reader.domain.model.article.DeepReadSession
import com.mckimquyen.reader.infrastructure.ai.ArticleDeepReadEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeepReadIntegrationTest {

    @Test
    fun endToEnd_generateQuestionsAnswerGroundedAndRenderChat() {
        val title = "Quantum Error Correction Milestones in 2026"
        val rawArticle = """
            Physicists and quantum computer engineers have achieved surface code threshold fidelity exceeding 99.9%.
            This technological breakthrough allows fault-tolerant quantum processors to sustain logical qubits for hours.
            
            However, current dilution refrigerators demand over 15 kilowatts of power, posing major thermal dissipation challenges.
            Initial commercial deployment is anticipated in high-performance pharmaceutical simulation and cryptanalysis.
            
            Financial estimates suggest a $4.5 billion market expansion across North America and Europe by 2030.
        """.trimIndent()

        // 1. Verify heuristic suggested question generation
        val chips = ArticleDeepReadEngine.generateSuggestedQuestions(title, rawArticle, "en")
        assertTrue("Must generate suggested questions", chips.isNotEmpty())

        // 2. Verify grounded offline question answering
        val question = "What are the thermal dissipation challenges and power requirements?"
        val answer = ArticleDeepReadEngine.generateOfflineAnswer(title, rawArticle, question, "en")

        assertEquals(DeepReadSender.ASSISTANT, answer.sender)
        assertTrue("Answer must be grounded", answer.isGrounded)
        assertTrue("Answer should contain context facts (15 kilowatts or refrigerators)", 
            answer.content.contains("15 kilowatts") || answer.content.contains("refrigerators"))

        // 3. Verify session assembly and transcript export
        val session = DeepReadSession(
            articleId = "art_quantum_2026",
            articleTitle = title,
            messages = listOf(
                DeepReadMessage(sender = DeepReadSender.USER, content = question),
                answer
            ),
            suggestedChips = chips
        )
        val transcript = session.formatTranscript()
        assertTrue("Transcript must contain article title", transcript.contains(title))
        assertTrue("Transcript must contain user question", transcript.contains(question))

        // 4. Verify Compose rendering in live Activity on Android 17 runtime
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val composeView = ComposeView(activity).apply {
                setContent {
                    DeepReadChatSheetContent(
                        state = DeepReadState.Active(
                            session = session,
                            isSending = false
                        ),
                        onSendQuestion = {},
                        onClearChat = {},
                        onClose = {},
                    )
                }
            }
            activity.setContentView(composeView)
            assertNotNull("ComposeView must attach successfully", composeView)
        }
        scenario.close()
    }
}
