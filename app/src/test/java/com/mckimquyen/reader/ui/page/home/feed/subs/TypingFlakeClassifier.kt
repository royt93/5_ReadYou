package com.mckimquyen.reader.ui.page.home.feed.subs

/** Where one typing attempt into the Add Source box ended. Every label except GREEN and INVALID is a lost-text case. */
enum class TypingOutcome { GREEN, INVALID, NEVER_DELIVERED, NOT_RENDERED, ERASED, UNFOCUSED_AT_TYPING, DISABLED_AT_TYPING }

/** One frame: the length of the text the view model holds, and the length of the text on screen (semantics). */
data class TypingSample(val viewModelLength: Int, val screenLength: Int)

data class TypingTrace(
    val windowFocusedBeforeTyping: Boolean,
    val fieldFocusedBeforeTyping: Boolean,
    val fieldEnabledBeforeTyping: Boolean,
    val expectedLength: Int,
    /** Taken after the typing call returned, one per frame, oldest first. */
    val samples: List<TypingSample>,
)

/** Pure and ordered: the first rule that matches wins (see the tests for why the order matters). */
object TypingFlakeClassifier {
    fun classify(trace: TypingTrace): TypingOutcome {
        if (!trace.windowFocusedBeforeTyping) return TypingOutcome.INVALID
        val last = trace.samples.lastOrNull()
        if (last != null && last.screenLength >= trace.expectedLength) return TypingOutcome.GREEN
        val viewModelPeak = trace.samples.maxOfOrNull { it.viewModelLength } ?: 0
        if (viewModelPeak >= trace.expectedLength) {
            return if (last != null && last.viewModelLength < viewModelPeak) TypingOutcome.ERASED else TypingOutcome.NOT_RENDERED
        }
        if (!trace.fieldEnabledBeforeTyping) return TypingOutcome.DISABLED_AT_TYPING
        if (!trace.fieldFocusedBeforeTyping) return TypingOutcome.UNFOCUSED_AT_TYPING
        return TypingOutcome.NEVER_DELIVERED
    }
}
