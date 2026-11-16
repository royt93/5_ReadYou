package com.mckimquyen.reader.ui.page.home.feed.subs

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.reader.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PROBE_TEXT = "abc-rotation-probe"
private const val DEFAULT_ITERATIONS = 5
private const val SAMPLE_FRAMES = 60
private const val FRAME_MILLIS = 16L
private const val WAIT_MILLIS = 15_000L
private const val UNUSED_RELAY_URL = "ws://127.0.0.1:1"
private const val CLOCK_AUTO = "auto"
private const val RED_LINES_KEPT = 20
private const val WINDOW_FOCUS_WAIT_MILLIS = 3_000L

/**
 * Investigation of the flaky typing step of FRONT-02d. Each iteration puts the real Add Source dialog on a fresh Activity, types the
 * probe text ONCE with performTextInput (no retry, as in 02d's first version), then samples every frame the length of the text the view
 * model holds and the length of the text on screen. Nothing is clicked, searched or fetched; no Room write, no network.
 * Arguments: -e iterations N (default 5), -e clock manual|auto (manual = like 02d, auto = like 02e).
 */
@RunWith(AndroidJUnit4::class)
class TypingFlakeProbeTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val placeholder get() = instrumentation.targetContext.getString(R.string.feed_or_site_url)

    private fun waitFor(what: String, manual: Boolean, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + WAIT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (manual) compose.mainClock.advanceTimeByFrame()
            if (runCatching(condition).getOrDefault(false)) return
            Thread.sleep(FRAME_MILLIS)
        }
        throw AssertionError("Never saw: $what")
    }

    private fun fieldNode() = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().single()

    private fun screenLength(): Int = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        .maxOfOrNull { it.config.getOrNull(SemanticsProperties.EditableText)?.text?.length ?: 0 } ?: 0

    /** "0x12,18x48": run-length form of a series, so a RED line stays short and carries no user text. */
    private fun series(values: List<Int>): String {
        val out = ArrayList<String>()
        var i = 0
        while (i < values.size) {
            var j = i
            while (j < values.size && values[j] == values[i]) j++
            out += "${values[i]}x${j - i}"
            i = j
        }
        return out.joinToString(",")
    }

    private class Attempt(val trace: TypingTrace, val outcome: TypingOutcome)

    private fun attempt(manual: Boolean, typeText: Boolean): Attempt {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            compose.mainClock.autoAdvance = !manual
            var viewModel: SubscribeViewModel? = null
            scenario.onActivity { activity ->
                val created = ViewModelProvider(activity, subscribeViewModelFactory(UNUSED_RELAY_URL))[SubscribeViewModel::class.java]
                viewModel = created
                activity.setContent { SubscribeDialog(created) }
                created.showDrawer()
            }
            val vm = checkNotNull(viewModel) { "the view model is set in onActivity above" }
            waitFor("the link field", manual) { compose.onAllNodes(hasText(placeholder)).fetchSemanticsNodes().isNotEmpty() }
            // The dialog window takes window focus a few frames AFTER its field appears (measured: not yet at first sight, yes 30 frames later).
            // Measuring at first sight made every iteration INVALID, so wait (bounded) for the dialog's own window to be focused, then measure.
            var node = fieldNode()
            val focusDeadline = System.currentTimeMillis() + WINDOW_FOCUS_WAIT_MILLIS
            while ((node.root as? View)?.hasWindowFocus() != true && System.currentTimeMillis() < focusDeadline) {
                if (manual) compose.mainClock.advanceTimeByFrame()
                Thread.sleep(FRAME_MILLIS)
                node = fieldNode()
            }
            val windowFocused = (node.root as? View)?.hasWindowFocus() ?: false
            val fieldFocused = node.config.getOrNull(SemanticsProperties.Focused) == true
            val fieldEnabled = !node.config.contains(SemanticsProperties.Disabled)
            check(screenLength() == 0 && vm.subscribeUiState.value.linkContent.isEmpty()) { "the box was not empty before typing" }
            if (typeText) compose.onNodeWithText(placeholder).performTextInput(PROBE_TEXT)
            val samples = ArrayList<TypingSample>(SAMPLE_FRAMES)
            repeat(SAMPLE_FRAMES) {
                if (manual) compose.mainClock.advanceTimeByFrame()
                Thread.sleep(FRAME_MILLIS)
                val focusedNow = runCatching { fieldNode().config.getOrNull(SemanticsProperties.Focused) == true }.getOrDefault(false)
                samples += TypingSample(vm.subscribeUiState.value.linkContent.length, runCatching { screenLength() }.getOrDefault(0), focusedNow)
            }
            val windowFocusedAfter = runCatching { (fieldNode().root as? View)?.hasWindowFocus() == true }.getOrDefault(false)
            val trace = TypingTrace(windowFocused, fieldFocused, fieldEnabled, PROBE_TEXT.length, samples, windowFocusedAfter)
            return Attempt(trace, TypingFlakeClassifier.classify(trace))
        }
    }

    private fun measure(iterations: Int, manual: Boolean, typeText: Boolean): Map<TypingOutcome, Int> {
        val counts = TypingOutcome.values().associateWith { 0 }.toMutableMap()
        val status = Bundle()
        var redLines = 0
        var focusedBefore = 0
        var brokenIterations = 0
        val brokenKinds = ArrayList<String>()
        repeat(iterations) { index ->
            val attempt = try {
                attempt(manual, typeText)
            } catch (e: Throwable) {
                // A broken iteration (probe or Activity trouble, not a typing result) is counted on its own and never loses the batch.
                brokenIterations += 1
                if (brokenKinds.size < RED_LINES_KEPT) brokenKinds += "iter=$index ${e.javaClass.simpleName}"
                return@repeat
            }
            counts[attempt.outcome] = counts.getValue(attempt.outcome) + 1
            if (attempt.trace.fieldFocusedBeforeTyping) focusedBefore += 1
            val isRed = attempt.outcome != TypingOutcome.GREEN && attempt.outcome != TypingOutcome.INVALID
            // The negative control is red on purpose: its lines must not be mixed into the measurement.
            if (typeText && isRed && redLines < RED_LINES_KEPT) {
                val t = attempt.trace
                redLines += 1
                status.putString(
                    "red_$redLines",
                    "RED{outcome=${attempt.outcome} iter=$index winFocus=${t.windowFocusedBeforeTyping} fieldFocus=${t.fieldFocusedBeforeTyping} " +
                        "enabled=${t.fieldEnabledBeforeTyping} focusFrames=${t.samples.count { it.fieldFocused }}/${t.samples.size} vm=${series(t.samples.map { it.viewModelLength })} screen=${series(t.samples.map { it.screenLength })}}",
                )
            }
        }
        status.putString(if (typeText) "broken" else "control_broken", "$brokenIterations${if (brokenKinds.isEmpty()) "" else ":" + brokenKinds.joinToString("|").replace(' ', '_')}")
        status.putString(if (typeText) "typing_summary" else "control_summary", counts.entries.joinToString(",") { "${it.key}=${it.value}" })
        status.putString(if (typeText) "focus_before" else "control_focus_before", "$focusedBefore/$iterations")
        instrumentation.sendStatus(0, status)
        return counts
    }

    private fun iterations(): Int = InstrumentationRegistry.getArguments().getString("iterations")?.toIntOrNull() ?: DEFAULT_ITERATIONS
    private fun manualClock(): Boolean = InstrumentationRegistry.getArguments().getString("clock") != CLOCK_AUTO

    @Test
    fun measure() {
        val total = iterations()
        val counts = measure(total, manualClock(), typeText = true)
        assertTrue("some iteration must be counted", counts.values.sum() > 0)
        assertTrue("no valid iteration at all: the phone window never had focus", counts.getValue(TypingOutcome.INVALID) < total)
    }

    /** Negative control: nothing is typed, so the classifier must NOT call it GREEN. Proves the probe sees an empty box. */
    @Test
    fun controlWithoutTyping_isNeverGreen() {
        val counts = measure(DEFAULT_ITERATIONS, manualClock(), typeText = false)
        assertEquals("nothing typed must never look typed", 0, counts.getValue(TypingOutcome.GREEN))
    }
}
