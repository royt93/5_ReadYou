package com.mckimquyen.reader.ui.page.rpg

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mckimquyen.reader.domain.model.rpg.UserProgress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class KnowledgeSkillTreeCardWidgetTest {
    @Test fun decayedCategory_displaysEffectiveXpAndBaseXpRetention() = withCard(
        UserProgress(
            categoryXp = mapOf(TECH to 90L),
            categoryBaseXp = mapOf(TECH to 100L),
            categoryLastPracticedAt = mapOf(TECH to 1L),
        ),
    ) { texts ->
        assertTrue(texts.any { it == "90 XP" })
        assertTrue("must disclose base XP and retained percent: $texts", texts.any { it.contains("100 XP") && it.contains("90%") })
    }

    @Test fun nonDecayedCategory_doesNotShowRedundantDecayLabel() = withCard(
        UserProgress(categoryXp = mapOf(TECH to 100L), categoryBaseXp = mapOf(TECH to 100L)),
    ) { texts ->
        assertTrue(texts.any { it == "100 XP" })
        assertFalse(texts.any { it.contains("base ·") })
    }

    @OptIn(androidx.compose.ui.InternalComposeUiApi::class)
    private fun withCard(progress: UserProgress, block: (List<String>) -> Unit) {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContentView(ComposeView(activity).apply { setContent { KnowledgeSkillTreeCard(progress) } })
            }
            val texts = AtomicReference<List<String>>(emptyList())
            val deadline = System.currentTimeMillis() + 5_000
            while (System.currentTimeMillis() < deadline) {
                scenario.onActivity { activity ->
                    val root = findComposeView(activity.window.decorView)?.semanticsOwner?.rootSemanticsNode
                    texts.set(root?.let(::textsOf).orEmpty())
                }
                if (texts.get().isNotEmpty()) break
                Thread.sleep(30)
            }
            block(texts.get())
        }
    }

    private fun textsOf(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty() + node.children.flatMap(::textsOf)

    @androidx.compose.ui.InternalComposeUiApi
    private fun findComposeView(view: View): ViewRootForTest? {
        if (view is ViewRootForTest) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findComposeView(view.getChildAt(i))?.let { return it }
        return null
    }

    private companion object { const val TECH = "Tech & AI" }
}
