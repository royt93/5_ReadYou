package com.mckimquyen.reader.ui.page.home.read.deepdive

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
import com.mckimquyen.reader.domain.ai.agents.DeepDiveImpact
import com.mckimquyen.reader.domain.ai.agents.DeepDivePdfLayout
import com.mckimquyen.reader.domain.ai.agents.DeepDivePdfTexts
import com.mckimquyen.reader.domain.ai.agents.DeepDivePerspective
import com.mckimquyen.reader.domain.ai.agents.DeepDiveReport
import com.mckimquyen.reader.domain.sv.AiDeepDiveFailure
import com.mckimquyen.reader.infrastructure.ai.DeepDivePdfRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

/**
 * No ComposeTestRule on purpose (it intermittently reports "No compose hierarchies found" on a real device): the
 * Activity is owned through ActivityScenario and the sheet body is driven through its semantics tree, the approach
 * BrainQuizCardWidgetTest proved stable. The Dialog wrapper is not used here because a Dialog is a separate window.
 */
@RunWith(AndroidJUnit4::class)
class DeepDiveSheetWidgetTest {

    private val report = DeepDiveReport(
        "a", listOf("Rates were low", "Inflation rose", "The bank pivoted"),
        listOf(DeepDivePerspective("Monetarist", "Overdue"), DeepDivePerspective("Keynesian", "Risky")),
        listOf(DeepDiveImpact("Housing", "Costs rise", "Prices cool"), DeepDiveImpact("Exports", "Currency firms", "Margins shrink")),
    )

    private fun str(id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

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

    private fun <T> withSheet(
        state: DeepDiveUiState,
        calls: MutableList<String>,
        awaitText: String,
        block: (Screen) -> T,
    ): T = ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
        scenario.onActivity { activity ->
            activity.setContentView(
                ComposeView(activity).apply {
                    setContent {
                        MaterialTheme {
                            DeepDiveSheetContent(
                                state = state, title = "Rates rise",
                                onDismiss = { calls += "dismiss" }, onConsent = { calls += "consent:$it" },
                                onConsentLater = { calls += "later" }, onRetry = { calls += "retry" },
                                onExportPdf = { calls += "export" },
                            )
                        }
                    }
                }
            )
        }
        val screen = Screen(scenario)
        assertTrue("the sheet never rendered", screen.await { screen.has(awaitText) })
        block(screen)
    }

    @Test fun consent_agreeAndNotNowReportTheRightAnswers() {
        val calls = mutableListOf<String>()
        withSheet(DeepDiveUiState.AwaitingConsent, calls, str(R.string.deep_dive_consent_message)) { screen ->
            assertTrue(screen.click(str(R.string.deep_dive_consent_agree)))
            assertTrue(screen.click(str(R.string.deep_dive_consent_later)))
        }
        assertEquals(listOf("consent:true", "later"), calls)
    }

    @Test fun consent_messageSaysTheTextIsSent() {
        withSheet(DeepDiveUiState.AwaitingConsent, mutableListOf(), str(R.string.deep_dive_consent_message)) { screen ->
            assertTrue(screen.has("6,000") || screen.has("6.000"))
        }
    }

    @Test fun loading_showsTheProgressText() {
        withSheet(DeepDiveUiState.Loading, mutableListOf(), str(R.string.deep_dive_loading)) { screen ->
            assertTrue(screen.has(str(R.string.deep_dive_loading)))
        }
    }

    @Test fun failed_showsTheMessageAndRetryReportsOnce() {
        val calls = mutableListOf<String>()
        withSheet(DeepDiveUiState.Failed(AiDeepDiveFailure.REJECTED), calls, str(R.string.deep_dive_failed)) { screen ->
            assertTrue(screen.click(str(R.string.deep_dive_retry)))
        }
        assertEquals(listOf("retry"), calls)
    }

    @Test fun ready_showsTheLabelTheSectionsAndTheMatrix() {
        withSheet(DeepDiveUiState.Ready(report), mutableListOf(), "Rates were low") { screen ->
            listOf(
                str(R.string.deep_dive_label), str(R.string.deep_dive_section_context), str(R.string.deep_dive_section_critical),
                str(R.string.deep_dive_section_impact), str(R.string.deep_dive_col_short), "Prices cool", "Monetarist",
            ).forEach { assertTrue("missing: $it", screen.has(it)) }
        }
    }

    @Test fun ready_exportReportsOnce() {
        val calls = mutableListOf<String>()
        withSheet(DeepDiveUiState.Ready(report), calls, "Rates were low") { screen ->
            assertTrue(screen.click(str(R.string.deep_dive_export_pdf)))
        }
        assertEquals(listOf("export"), calls)
    }

    // The JVM cannot run PdfDocument (Robolectric closes it on creation), so the real PDF is checked here: the bytes
    // are reopened with the platform PdfRenderer and must be exactly one A4 page.
    @Test fun pdfRenderer_producesAOnePagePdfOnARealDevice() {
        val texts = DeepDivePdfTexts("AI", "Context", "Critical", "Impact", "Area", "Short", "Long", "More", "2026-11-11")
        val plan = com.mckimquyen.reader.domain.ai.agents.DeepDivePdfLayout.plan(report, "Rates rise", texts, DeepDivePdfRenderer.measurer)
        val bytes = DeepDivePdfRenderer.render(plan, "Rates rise", texts)
        assertEquals("%PDF-", String(bytes, 0, 5, Charsets.US_ASCII))
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val file = java.io.File.createTempFile("deepdive_test", ".pdf", cache)
        file.writeBytes(bytes)
        val fd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = android.graphics.pdf.PdfRenderer(fd)
        try {
            assertEquals("exactly one page", 1, renderer.pageCount)
            val page = renderer.openPage(0)
            try {
                assertEquals(DeepDivePdfLayout.PAGE_WIDTH, page.width)
                assertEquals(DeepDivePdfLayout.PAGE_HEIGHT, page.height)
            } finally {
                page.close()
            }
        } finally {
            renderer.close()
            fd.close()
            file.delete()
        }
    }
}
