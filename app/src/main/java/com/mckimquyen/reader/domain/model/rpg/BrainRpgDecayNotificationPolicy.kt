package com.mckimquyen.reader.domain.model.rpg

data class CategoryDecayState(
    val categoryId: String,
    val baseXp: Long,
    val lastPracticedAtMillis: Long,
    val lastNotifiedEpochDay: Long,
)

data class DecayingCategory(val categoryId: String, val weeks: Int)

data class BrainRpgDecayNotificationDecision(
    val shouldNotify: Boolean,
    val shouldCancel: Boolean,
    val decaying: List<DecayingCategory>,
)

/** Pure daily-notification policy; persisted XP stays untouched. */
object BrainRpgDecayNotificationPolicy {
    fun evaluate(categories: List<CategoryDecayState>, nowMillis: Long): BrainRpgDecayNotificationDecision {
        val today = Math.floorDiv(nowMillis, CategoryDecay.WEEK_MILLIS / DAYS_PER_WEEK)
        val decaying = categories.asSequence()
            .filter { it.baseXp > 0L && nowMillis >= it.lastPracticedAtMillis }
            .mapNotNull { state ->
                val weeks = ((nowMillis - state.lastPracticedAtMillis) / CategoryDecay.WEEK_MILLIS).toInt()
                if (weeks > 0) DecayingCategory(state.categoryId, weeks) else null
            }
            .sortedBy { it.categoryId }
            .toList()
        val shouldNotify = decaying.any { item ->
            val lastNotified = categories.first { it.categoryId == item.categoryId }.lastNotifiedEpochDay
            lastNotified < today
        }
        return BrainRpgDecayNotificationDecision(
            shouldNotify = shouldNotify,
            shouldCancel = decaying.isEmpty(),
            decaying = decaying,
        )
    }

    private const val DAYS_PER_WEEK = 7L
}
