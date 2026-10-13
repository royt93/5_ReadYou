package com.mckimquyen.reader.domain.sv

import android.content.Context
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.model.rpg.BrainRpgDecayNotificationPolicy
import com.mckimquyen.reader.domain.model.rpg.CategoryDecayState
import com.mckimquyen.reader.domain.repository.BrainRpgDao
import com.mckimquyen.reader.domain.repository.BrainRpgLegacyImporter
import com.mckimquyen.reader.infrastructure.android.NotificationHelper
import com.mckimquyen.reader.infrastructure.di.IODispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Reads category practice state and posts/clears one daily summary; never mutates earned XP. */
@Singleton
class BrainRpgDecayProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: BrainRpgDao,
    private val legacyImporter: BrainRpgLegacyImporter,
    private val notificationHelper: NotificationHelper,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun run(nowMillis: Long = System.currentTimeMillis()): BrainRpgDecayNotificationResult = withContext(ioDispatcher) {
        legacyImporter.ensureImported(nowMillis)
        val categories = dao.getCategories()
        val decision = BrainRpgDecayNotificationPolicy.evaluate(
            categories.map {
                CategoryDecayState(it.categoryId, it.baseXp, it.lastPracticedAtMillis, it.lastDecayNotificationEpochDay)
            },
            nowMillis,
        )
        if (decision.shouldCancel) {
            notificationHelper.cancelRpgDecay()
            return@withContext BrainRpgDecayNotificationResult(decayingCount = 0, notified = false, cancelled = true)
        }
        if (!decision.shouldNotify) {
            return@withContext BrainRpgDecayNotificationResult(decayingCount = decision.decaying.size, notified = false, cancelled = false)
        }

        if (!notificationHelper.canPostNotifications()) {
            return@withContext BrainRpgDecayNotificationResult(decayingCount = decision.decaying.size, notified = false, cancelled = false)
        }

        val labels = decision.decaying.map { item -> context.getString(categoryLabel(item.categoryId)) }
        val body = context.getString(R.string.brain_rpg_decay_notification_body, labels.size, labels.joinToString(", "))
        notificationHelper.notifyRpgDecay(context.getString(R.string.brain_rpg_decay_notification_title), body)
        val today = TimeUnit.MILLISECONDS.toDays(nowMillis)
        decision.decaying.forEach { dao.updateDecayNotificationDay(it.categoryId, today) }
        BrainRpgDecayNotificationResult(decayingCount = decision.decaying.size, notified = true, cancelled = false)
    }

    private fun categoryLabel(categoryId: String): Int = when (categoryId) {
        QuizGeneratorService.CATEGORY_TECH -> R.string.brain_rpg_category_tech
        QuizGeneratorService.CATEGORY_BUSINESS -> R.string.brain_rpg_category_business
        QuizGeneratorService.CATEGORY_SCIENCE -> R.string.brain_rpg_category_science
        QuizGeneratorService.CATEGORY_HEALTH -> R.string.brain_rpg_category_health
        QuizGeneratorService.CATEGORY_PHILOSOPHY -> R.string.brain_rpg_category_philosophy
        QuizGeneratorService.CATEGORY_GENERAL -> R.string.brain_rpg_category_general
        else -> R.string.brain_rpg_category_general
    }
}

data class BrainRpgDecayNotificationResult(
    val decayingCount: Int,
    val notified: Boolean,
    val cancelled: Boolean,
)
