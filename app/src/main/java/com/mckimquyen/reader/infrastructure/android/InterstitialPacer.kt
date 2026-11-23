package com.mckimquyen.reader.infrastructure.android

/**
 * Decides which user actions may be followed by an interstitial.
 *
 * AdMob (Disallowed interstitial implementations): an interstitial may follow at most one in two user actions and only at a natural break.
 * The caller reports each qualifying action (the user asked for the next article) and shows an ad only when this returns true.
 * The SDK adds its own time and per-hour limits on top, and skips VIP users; this class only keeps the count.
 * The count lives in memory: a new process starts again at zero, which can only show fewer ads.
 */
class InterstitialPacer(private val everyNthAction: Int = DEFAULT_EVERY_NTH_ACTION) {
    private var actions = 0

    init {
        require(everyNthAction >= MIN_EVERY_NTH_ACTION) { "an interstitial more often than one in $MIN_EVERY_NTH_ACTION actions breaks the AdMob rule" }
    }

    /** True when this action may be followed by an interstitial. */
    @Synchronized
    fun onUserAction(): Boolean {
        actions++
        if (actions < everyNthAction) return false
        actions = 0
        return true
    }

    /** True when the next [onUserAction] will return true; lets the screen preload only an ad it is about to show. */
    @Synchronized
    fun nextActionMayShow(): Boolean = actions + 1 >= everyNthAction

    companion object {
        const val MIN_EVERY_NTH_ACTION = 3
        const val DEFAULT_EVERY_NTH_ACTION = 3

        /** One count for the whole app process. */
        val shared: InterstitialPacer by lazy { InterstitialPacer() }
    }
}
