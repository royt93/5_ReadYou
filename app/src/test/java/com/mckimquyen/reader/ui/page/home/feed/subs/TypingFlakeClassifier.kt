package com.mckimquyen.reader.ui.page.home.feed.subs

/** Where one typing attempt into the Add Source box ended. Every label except GREEN and INVALID is a lost-text case. */
enum class TypingOutcome { GREEN, INVALID, NEVER_DELIVERED, NEVER_DELIVERED_FOCUS_LATE, NOT_RENDERED, ERASED, UNFOCUSED_AT_TYPING, DISABLED_AT_TYPING }

/** One frame: the text length in the view model, the text length on screen (semantics), and whether the field is focused in that frame. */
data class TypingSample(val viewModelLength: Int, val screenLength: Int, val fieldFocused: Boolean = false)

data class TypingTrace(
    val windowFocusedBeforeTyping: Boolean,
    val fieldFocusedBeforeTyping: Boolean,
    val fieldEnabledBeforeTyping: Boolean,
    val expectedLength: Int,
    /** Taken after the typing call returned, one per frame, oldest first. */
    val samples: List<TypingSample>,
    /** The dialog window still had window focus when sampling ended; false means another app took the screen meanwhile. */
    val windowFocusedAfterSampling: Boolean = true,
)

/** Pure and ordered: the first rule that matches wins (see the tests for why the order matters). */
object TypingFlakeClassifier {
    fun classify(trace: TypingTrace): TypingOutcome {
        if (!trace.windowFocusedBeforeTyping || !trace.windowFocusedAfterSampling) return TypingOutcome.INVALID
        val last = trace.samples.lastOrNull()
        if (last != null && last.screenLength >= trace.expectedLength) return TypingOutcome.GREEN
        val viewModelPeak = trace.samples.maxOfOrNull { it.viewModelLength } ?: 0
        if (viewModelPeak >= trace.expectedLength) {
            return if (last != null && last.viewModelLength < viewModelPeak) TypingOutcome.ERASED else TypingOutcome.NOT_RENDERED
        }
        if (!trace.fieldEnabledBeforeTyping) return TypingOutcome.DISABLED_AT_TYPING
        if (!trace.fieldFocusedBeforeTyping) {
            // The text never arrived. If the field got focus only AFTER the typing call, the typing came before focus (a race), otherwise focus is not the explanation.
            return if (trace.samples.any { it.fieldFocused }) TypingOutcome.NEVER_DELIVERED_FOCUS_LATE else TypingOutcome.UNFOCUSED_AT_TYPING
        }
        return TypingOutcome.NEVER_DELIVERED
    }
}
