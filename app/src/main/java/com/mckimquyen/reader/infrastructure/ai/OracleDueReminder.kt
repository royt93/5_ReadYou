package com.mckimquyen.reader.infrastructure.ai

import android.content.Context
import android.util.Log
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.oracle.OracleRemindedCodec
import com.mckimquyen.reader.domain.repository.OracleWalletDao
import com.mckimquyen.reader.infrastructure.android.NotificationHelper
import com.mckimquyen.reader.ui.ext.oracleRemindedBets
import com.mckimquyen.reader.ui.ext.setOracleRemindedBets
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import java.text.NumberFormat
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tells the user, once, that bets have reached their date (ORACLE-03). Purely local: it reads the user's own open bets
 * and posts at most one grouped notification per run. It never decides an outcome and never mentions the points at stake.
 * A bet is marked as reminded only AFTER its notification was posted, so a missing permission or a crash retries later.
 */
@Singleton
open class OracleDueReminder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val walletDao: OracleWalletDao,
    private val notificationHelper: NotificationHelper,
) {

    protected open fun nowMillis(): Long = System.currentTimeMillis()

    protected open suspend fun readReminded(): List<String> = OracleRemindedCodec.decode(context.oracleRemindedBets)

    protected open suspend fun writeReminded(ids: List<String>) {
        context.setOracleRemindedBets(OracleRemindedCodec.encode(ids))
    }

    /** Returns how many notifications were posted: 0 or 1. */
    suspend fun remindDue(): Int = try {
        val due = walletDao.dueOpenBets(nowMillis())
        val reminded = readReminded()
        val dueIds = due.map { it.articleId }.toSet()
        val pruned = reminded.filter { it in dueIds }
        val fresh = due.filter { it.articleId !in reminded }
        when {
            fresh.isEmpty() -> {
                if (pruned != reminded) writeReminded(pruned)
                0
            }
            !notificationHelper.canPostNotifications() -> 0
            else -> {
                if (fresh.size == 1) {
                    notificationHelper.notifyOracleDue(context.getString(R.string.oracle_due_notification_title), fresh.first().question)
                } else {
                    val count = NumberFormat.getIntegerInstance().format(fresh.size)
                    notificationHelper.notifyOracleDue(
                        context.getString(R.string.oracle_due_notification_title_many),
                        context.getString(R.string.oracle_due_notification_body_many, count),
                    )
                }
                writeReminded(OracleRemindedCodec.append(pruned, fresh.map { it.articleId }))
                1
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Only the exception type: a message could carry a question.
        Log.w(TAG, "Due reminder failed: ${e.javaClass.simpleName}")
        0
    }

    private companion object {
        const val TAG = "OracleDueReminder"
    }
}
