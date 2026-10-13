package com.mckimquyen.reader.ui.component.rpg

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.rpg.QuizQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

/**
 * No ComposeTestRule on purpose: it intermittently reports "No compose hierarchies found" on a real device when an
 * ActivityScenario-owned Activity is not registered yet (about 1 run in 4 here). We own the Activity through
 * ActivityScenario and drive the card through its semantics tree instead (the approach EdgeToEdgeInsetsTest proved
 * stable), clicking via the real `OnClick` semantics action on the main thread.
 */
@RunWith(AndroidJUnit4::class)
class BrainQuizCardWidgetTest {

    private val quiz = QuizQuestion(
        articleId = "art-1",
        question = "Which one is right?",
        options = listOf("Wrong option", "Right option"),
        correctAnswerIndex = 1,
        explanation = "Because.",
        category = "tech",
    )

    private fun str(id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    /** A running card plus the helpers to read and click it. */
    private class Screen(private val scenario: ActivityScenario<ComponentActivity>) {

        fun texts(): List<String> {
            val out = AtomicReference<List<String>>(emptyList())
            scenario.onActivity { activity ->
                val root = findComposeView(activity.window.decorView)?.semanticsOwner?.rootSemanticsNode
                out.set(root?.let { collect(it) { node -> textsOf(node) } }.orEmpty())
            }
            return out.get()
        }

        fun has(text: String): Boolean = texts().any { it == text || it.contains(text) }

        /** Clicks the first node showing [text] that actually has an OnClick action. */
        fun click(text: String): Boolean {
            val clicked = AtomicReference(false)
            scenario.onActivity { activity ->
                val root = findComposeView(activity.window.decorView)?.semanticsOwner?.rootSemanticsNode ?: return@onActivity
                val target = firstClickable(root, text) ?: return@onActivity
                // Compose may retain a disabled click action in the semantics tree; model real user input by
                // checking the disabled property before invoking the action.
                if (target.config.getOrNull(SemanticsProperties.Disabled) == null) {
                    target.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke()
                    clicked.set(true)
                }
            }
            return clicked.get()
        }

        fun await(timeoutMs: Long = 10_000, condition: () -> Boolean): Boolean {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (condition()) return true
                Thread.sleep(50)
            }
            return condition()
        }

        private fun textsOf(node: SemanticsNode): List<String> =
            node.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()

        private fun <T> collect(node: SemanticsNode, read: (SemanticsNode) -> List<T>): List<T> =
            read(node) + node.children.flatMap { collect(it, read) }

        private fun firstClickable(node: SemanticsNode, text: String): SemanticsNode? {
            val hasText = textsOf(node).any { it.contains(text) }
            if (hasText && node.config.getOrNull(SemanticsActions.OnClick) != null) return node
            for (child in node.children) firstClickable(child, text)?.let { return it }
            return null
        }

        @OptIn(androidx.compose.ui.InternalComposeUiApi::class)
        private fun findComposeView(view: View): ViewRootForTest? {
            if (view is ViewRootForTest) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) findComposeView(view.getChildAt(i))?.let { return it }
            return null
        }
    }

    private fun <T> withCard(
        doubleAlreadyClaimed: Boolean,
        onAnswer: (Boolean) -> Unit = {},
        block: (Screen) -> T,
    ): T = ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
        scenario.onActivity { activity ->
            activity.setContentView(
                ComposeView(activity).apply {
                    setContent {
                        BrainQuizCard(
                            quiz = quiz,
                            doubleAlreadyClaimed = doubleAlreadyClaimed,
                            onAnswerSubmitted = onAnswer,
                            onDoubleXpRequested = { _, _ -> },
                            onRetryQuizRequested = { _, _ -> },
                        )
                    }
                }
            )
        }
        val screen = Screen(scenario)
        assertTrue("the quiz never rendered", screen.await { screen.has("Right option") })
        block(screen)
    }

    @Test
    fun correctAnswer_offersTheDoubleXpButton_whenNotClaimedYet() = withCard(doubleAlreadyClaimed = false) { screen ->
        assertTrue(screen.click("Right option"))

        assertTrue(screen.await { screen.has(str(R.string.brain_rpg_correct)) })
        assertTrue("the double-XP button must be offered", screen.await { screen.has(str(R.string.brain_rpg_double_xp)) })
    }

    @Test
    fun correctAnswer_hidesTheDoubleXpButton_whenTheBonusWasAlreadyTaken() = withCard(doubleAlreadyClaimed = true) { screen ->
        // e.g. the user came back after a restart: the bonus is persisted as claimed.
        assertTrue(screen.click("Right option"))

        assertTrue(screen.await { screen.has(str(R.string.brain_rpg_correct)) })
        assertTrue(screen.await { screen.has("+300 XP") })
        assertFalse("the claimed bonus must not be offered again", screen.has(str(R.string.brain_rpg_double_xp)))
    }

    @Test
    fun theAnswerIsReportedOnce_andLocksTheOptions() {
        val answers = mutableListOf<Boolean>()
        withCard(doubleAlreadyClaimed = false, onAnswer = { answers += it }) { screen ->
            assertTrue(screen.click("Right option"))
            assertTrue(screen.await { screen.has(str(R.string.brain_rpg_correct)) })

            screen.click("Wrong option") // locked after the first answer: the click must not report again
            Thread.sleep(300)
        }
        assertEquals(listOf(true), answers)
    }

    @Test
    fun wrongAnswer_neverShowsTheDoubleXpButton() = withCard(doubleAlreadyClaimed = false) { screen ->
        assertTrue(screen.click("Wrong option"))

        assertTrue(screen.await { screen.has(str(R.string.brain_rpg_wrong)) })
        assertFalse(screen.has(str(R.string.brain_rpg_double_xp)))
    }
}
