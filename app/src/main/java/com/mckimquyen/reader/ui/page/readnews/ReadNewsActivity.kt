package com.mckimquyen.reader.ui.page.readnews

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import com.mckimquyen.reader.R
import com.mckimquyen.reader.domain.sv.ReadNewsController
import com.mckimquyen.reader.domain.sv.ReadNewsOutcome
import com.mckimquyen.reader.infrastructure.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

object ReadNewsLink {
    const val SCHEME = "rsscathub"
    const val HOST = "read-news"
}

/**
 * Điểm vào đọc tin rảnh tay: shortcut, App Action (GET_THING) và deep link `rsscathub://read-news`.
 * Không dựng UI: giao việc cho [ReadNewsController] trong @ApplicationScope rồi finish() ngay.
 * Intent là exported nên không đọc bất kỳ tham số nào từ nó.
 */
@AndroidEntryPoint
class ReadNewsActivity : ComponentActivity() {

    @Inject
    lateinit var controller: ReadNewsController

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isReadNewsIntent(intent)) {
            finish()
            return
        }
        val appContext = applicationContext
        // Chạy ở @ApplicationScope để việc đọc không bị huỷ khi Activity finish().
        appScope.launch {
            val outcome = controller.start()
            messageFor(outcome)?.let { res ->
                withContext(Dispatchers.Main) { Toast.makeText(appContext, res, Toast.LENGTH_SHORT).show() }
            }
        }
        finish()
    }

    companion object {
        internal fun isReadNewsIntent(intent: Intent?): Boolean {
            val data = intent?.data ?: return false
            return intent.action == Intent.ACTION_VIEW &&
                data.scheme == ReadNewsLink.SCHEME &&
                data.host == ReadNewsLink.HOST
        }

        @StringRes
        internal fun messageFor(outcome: ReadNewsOutcome): Int? = when (outcome) {
            ReadNewsOutcome.NO_UNREAD -> R.string.read_news_no_unread
            ReadNewsOutcome.NO_READABLE_TEXT, ReadNewsOutcome.ERROR -> R.string.read_news_error
            ReadNewsOutcome.STARTED, ReadNewsOutcome.QUEUE_FINISHED -> null
        }
    }
}
