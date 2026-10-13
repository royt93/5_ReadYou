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
import org.junit.Assert.assertEquals
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
    @Test fun decayedCategory_offersRecovery_andReportsWhichCategoryWasChosen() {
        val chosen = mutableListOf<String>()
        withCard(
            UserProgress(
                categoryXp = mapOf(TECH to 90L, SCIENCE to 50L),
                categoryBaseXp = mapOf(TECH to 100L, SCIENCE to 50L),
            ),
            onRecover = { chosen += it },
            act = { click ->
                val labels = str(com.mckimquyen.reader.R.string.brain_rpg_recover_action)
                assertTrue("recover button for decayed category", click(labels))
            },
        ) { texts ->
            assertTrue(texts.count { it == str(com.mckimquyen.reader.R.string.brain_rpg_recover_action) } == 1)
        }
        assertEquals("only the decayed category can be chosen", listOf(TECH), chosen)
    }

    @Test fun freshCategories_showNoRecoveryButton() = withCard(
        UserProgress(categoryXp = mapOf(TECH to 100L), categoryBaseXp = mapOf(TECH to 100L)),
        onRecover = {},
    ) { texts ->
        assertFalse(texts.any { it == str(com.mckimquyen.reader.R.string.brain_rpg_recover_action) })
    }

    @Test fun pendingRecovery_disablesEveryRecoveryButton_soNoSecondAdIsStarted() {
        val chosen = mutableListOf<String>()
        withCard(
            UserProgress(
                categoryXp = mapOf(TECH to 90L, SCIENCE to 40L),
                categoryBaseXp = mapOf(TECH to 100L, SCIENCE to 50L),
            ),
            recoveringCategoryId = TECH,
            onRecover = { chosen += it },
            act = { click -> assertFalse("buttons locked while an ad is pending", click(str(com.mckimquyen.reader.R.string.brain_rpg_recover_action))) },
        ) { texts ->
            assertTrue(texts.any { it == str(com.mckimquyen.reader.R.string.brain_rpg_recover_pending) })
        }
        assertTrue(chosen.isEmpty())
    }

    private fun str(id: Int): String = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @OptIn(androidx.compose.ui.InternalComposeUiApi::class)
    private fun withCard(
        progress: UserProgress,
        recoveringCategoryId: String? = null,
        onRecover: (String) -> Unit = {},
        act: (click: (String) -> Boolean) -> Unit = {},
        block: (List<String>) -> Unit,
    ) {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContentView(ComposeView(activity).apply { setContent { KnowledgeSkillTreeCard(progress, recoveringCategoryId, onRecover) } })
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
            act { label -> clickByText(scenario, label) }
            Thread.sleep(300)
            scenario.onActivity { activity ->
                val root = findComposeView(activity.window.decorView)?.semanticsOwner?.rootSemanticsNode
                texts.set(root?.let(::textsOf).orEmpty())
            }
            block(texts.get())
        }
    }

    @OptIn(androidx.compose.ui.InternalComposeUiApi::class)
    private fun clickByText(scenario: ActivityScenario<ComponentActivity>, label: String): Boolean {
        val clicked = AtomicReference(false)
        scenario.onActivity { activity ->
            val root = findComposeView(activity.window.decorView)?.semanticsOwner?.rootSemanticsNode ?: return@onActivity
            fun find(node: SemanticsNode): SemanticsNode? {
                val has = node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true
                if (has && node.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.OnClick) != null) return node
                return node.children.firstNotNullOfOrNull(::find)
            }
            val target = find(root) ?: return@onActivity
            if (target.config.getOrNull(SemanticsProperties.Disabled) == null) {
                target.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.OnClick)?.action?.invoke()
                clicked.set(true)
            }
        }
        return clicked.get()
    }

    private fun textsOf(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty() + node.children.flatMap(::textsOf)

    @androidx.compose.ui.InternalComposeUiApi
    private fun findComposeView(view: View): ViewRootForTest? {
        if (view is ViewRootForTest) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findComposeView(view.getChildAt(i))?.let { return it }
        return null
    }

    private companion object {
        const val TECH = "Tech & AI"
        const val SCIENCE = "Science & Nature"
    }
}
