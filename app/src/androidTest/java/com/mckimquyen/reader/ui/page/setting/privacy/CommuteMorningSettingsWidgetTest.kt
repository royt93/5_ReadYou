package com.mckimquyen.reader.ui.page.setting.privacy

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

/** STRAT-01: the three morning-episode settings rows, driven through their semantics tree on a real device. */
@RunWith(AndroidJUnit4::class)
class CommuteMorningSettingsWidgetTest {

    private fun str(id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private class Screen(private val scenario: ActivityScenario<ComponentActivity>) {

        fun texts(): List<String> {
            val out = AtomicReference<List<String>>(emptyList())
            scenario.onActivity { activity ->
                val root = findComposeView(activity.window.decorView)?.semanticsOwner?.rootSemanticsNode
                out.set(root?.let { collect(it) }.orEmpty())
            }
            return out.get()
        }

        fun has(text: String): Boolean = texts().any { it.contains(text) }

        fun clickableLabels(): List<String> {
            val out = AtomicReference<List<String>>(emptyList())
            scenario.onActivity { activity ->
                val root = findComposeView(activity.window.decorView)?.semanticsOwner?.rootSemanticsNode ?: return@onActivity
                out.set(clickables(root).map { textsOf(it).joinToString(" ") })
            }
            return out.get()
        }

        fun isEnabled(text: String): Boolean {
            val out = AtomicReference(false)
            scenario.onActivity { activity ->
                val root = findComposeView(activity.window.decorView)?.semanticsOwner?.rootSemanticsNode ?: return@onActivity
                val node = firstClickable(root, text) ?: return@onActivity
                out.set(node.config.getOrNull(SemanticsProperties.Disabled) == null)
            }
            return out.get()
        }

        fun click(text: String): Boolean {
            val clicked = AtomicReference(false)
            scenario.onActivity { activity ->
                val root = findComposeView(activity.window.decorView)?.semanticsOwner?.rootSemanticsNode ?: return@onActivity
                val target = firstClickable(root, text) ?: return@onActivity
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
            node.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty() +
                node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()

        private fun collect(node: SemanticsNode): List<String> = textsOf(node) + node.children.flatMap { collect(it) }

        private fun clickables(node: SemanticsNode): List<SemanticsNode> =
            (if (node.config.getOrNull(SemanticsActions.OnClick) != null) listOf(node) else emptyList()) +
                node.children.flatMap { clickables(it) }

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

    private fun <T> withRows(
        consent: Boolean = false,
        autoPlay: Boolean = false,
        denied: Boolean = false,
        consents: MutableList<Boolean> = mutableListOf(),
        autoPlays: MutableList<Boolean> = mutableListOf(),
        block: (Screen) -> T,
    ): T = ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
        scenario.onActivity { activity ->
            activity.setContentView(
                ComposeView(activity).apply {
                    setContent {
                        MaterialTheme {
                            CommuteMorningSettingsRows(
                                consent = consent, listenMinuteOfDay = 7 * 60, autoPlay = autoPlay, permissionDenied = denied,
                                activity = activity,
                                onConsent = { consents += it }, onPickTime = { _, _ -> }, onAutoPlay = { autoPlays += it },
                            )
                        }
                    }
                }
            )
        }
        val screen = Screen(scenario)
        assertTrue("the rows never rendered", screen.await { screen.has(str(R.string.commute_consent_title)) })
        block(screen)
    }

    @Test fun showsTheThreeSettings_andTheDefaultListenTime() {
        withRows { screen ->
            assertTrue(screen.has(str(R.string.commute_consent_title)))
            assertTrue(screen.has(str(R.string.commute_listen_time_title)))
            assertTrue(screen.has(str(R.string.commute_autoplay_title)))
            assertTrue(screen.has("07:00"))
        }
    }

    @Test fun tappingTheConsentRow_reportsTheOppositeOfTheCurrentValue() {
        val consents = mutableListOf<Boolean>()
        withRows(consent = false, consents = consents) { screen ->
            assertTrue(screen.click(str(R.string.commute_consent_title)))
        }
        assertEquals(listOf(true), consents)
    }

    @Test fun tappingTheAutoPlayRow_reportsTheOppositeOfTheCurrentValue() {
        val autoPlays = mutableListOf<Boolean>()
        withRows(autoPlay = false, autoPlays = autoPlays) { screen ->
            assertTrue(screen.click(str(R.string.commute_autoplay_title)))
        }
        assertEquals(listOf(true), autoPlays)
    }

    @Test fun aDeniedPermission_showsTheExplanation_andOtherwiseItIsAbsent() {
        withRows(denied = true) { screen ->
            assertTrue(screen.has(str(R.string.commute_autoplay_permission_denied)))
        }
        withRows(denied = false) { screen ->
            assertFalse(screen.has(str(R.string.commute_autoplay_permission_denied)))
        }
    }
}
