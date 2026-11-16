package com.mckimquyen.reader.ui.page.home.feed.subs

import org.junit.Assert.assertEquals
import org.junit.Test

private const val PROBE_LENGTH = 18

class TypingFlakeClassifierTest {
    private fun trace(
        windowFocused: Boolean = true,
        fieldFocused: Boolean = true,
        fieldEnabled: Boolean = true,
        vararg samples: TypingSample,
    ) = TypingTrace(windowFocused, fieldFocused, fieldEnabled, PROBE_LENGTH, samples.toList())

    private fun s(viewModel: Int, screen: Int) = TypingSample(viewModel, screen)

    @Test
    fun aTraceWhereTheScreenReachesTheExpectedLength_isGreen() {
        val outcome = TypingFlakeClassifier.classify(trace(samples = arrayOf(s(0, 0), s(PROBE_LENGTH, 0), s(PROBE_LENGTH, PROBE_LENGTH))))
        assertEquals(TypingOutcome.GREEN, outcome)
    }

    @Test
    fun anUnfocusedWindowBeforeTyping_isInvalid_whateverHappensAfter() {
        assertEquals(TypingOutcome.INVALID, TypingFlakeClassifier.classify(trace(windowFocused = false)))
        val typedFine = trace(windowFocused = false, samples = arrayOf(s(PROBE_LENGTH, PROBE_LENGTH)))
        assertEquals(TypingOutcome.INVALID, TypingFlakeClassifier.classify(typedFine))
    }

    @Test
    fun aViewModelThatNeverHoldsTheText_isNeverDelivered() {
        assertEquals(TypingOutcome.NEVER_DELIVERED, TypingFlakeClassifier.classify(trace(samples = arrayOf(s(0, 0), s(0, 0), s(0, 0)))))
        assertEquals("no sample at all is still never delivered", TypingOutcome.NEVER_DELIVERED, TypingFlakeClassifier.classify(trace()))
    }

    @Test
    fun aViewModelThatHoldsTheTextWhileTheScreenStaysEmpty_isNotRendered() {
        val outcome = TypingFlakeClassifier.classify(trace(samples = arrayOf(s(0, 0), s(PROBE_LENGTH, 0), s(PROBE_LENGTH, 0))))
        assertEquals(TypingOutcome.NOT_RENDERED, outcome)
    }

    @Test
    fun aViewModelThatHeldTheTextAndThenLostIt_isErased() {
        val screenNeverSawIt = trace(samples = arrayOf(s(0, 0), s(PROBE_LENGTH, 0), s(0, 0)))
        assertEquals(TypingOutcome.ERASED, TypingFlakeClassifier.classify(screenNeverSawIt))
        val screenSawItThenLostIt = trace(samples = arrayOf(s(PROBE_LENGTH, PROBE_LENGTH), s(0, PROBE_LENGTH), s(0, 0)))
        assertEquals(TypingOutcome.ERASED, TypingFlakeClassifier.classify(screenSawItThenLostIt))
    }

    @Test
    fun aFieldNotFocusedBeforeTyping_andNeverDelivered_isUnfocusedAtTyping() {
        val outcome = TypingFlakeClassifier.classify(trace(fieldFocused = false, samples = arrayOf(s(0, 0), s(0, 0))))
        assertEquals(TypingOutcome.UNFOCUSED_AT_TYPING, outcome)
    }

    @Test
    fun aDisabledFieldBeforeTyping_andNeverDelivered_isDisabledAtTyping() {
        val outcome = TypingFlakeClassifier.classify(trace(fieldEnabled = false, samples = arrayOf(s(0, 0), s(0, 0))))
        assertEquals(TypingOutcome.DISABLED_AT_TYPING, outcome)
        val bothFlags = trace(fieldFocused = false, fieldEnabled = false, samples = arrayOf(s(0, 0)))
        assertEquals("a disabled field explains more than a missing focus", TypingOutcome.DISABLED_AT_TYPING, TypingFlakeClassifier.classify(bothFlags))
    }

    @Test
    fun theOrderOfTheRulesIsFixed() {
        val everythingWrong = trace(windowFocused = false, fieldFocused = false, fieldEnabled = false, samples = arrayOf(s(PROBE_LENGTH, 0), s(0, 0)))
        assertEquals("an unfocused window beats every other label", TypingOutcome.INVALID, TypingFlakeClassifier.classify(everythingWrong))
        val erasedAndUnfocused = trace(fieldFocused = false, samples = arrayOf(s(PROBE_LENGTH, 0), s(0, 0)))
        assertEquals("text that got in is not explained by a missing focus", TypingOutcome.ERASED, TypingFlakeClassifier.classify(erasedAndUnfocused))
    }
}
