package com.mckimquyen.reader.ui.widget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.mckimquyen.reader.infrastructure.android.MainActivity
import com.mckimquyen.reader.ui.page.common.ExtraName
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Trampoline activity trong suốt xử lý chạm từ widget.
 *
 * RemoteViews collection chỉ hỗ trợ 1 PendingIntent template cho toàn bộ danh sách,
 * nên các phần tử con (chạm mở bài / nút "Đã đọc") đều gửi qua trampoline này bằng fillInIntent.
 *
 * Quyết định owner (2026-10-23): mở bài dùng FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK
 * để buộc MainActivity khởi tạo lại, bảo đảm HomeEntry đọc ExtraName.ARTICLE_ID.
 */
class ArticleWidgetActionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent?.getStringExtra(ArticleWidgetActionHandler.EXTRA_ACTION)
        val articleId = intent?.getStringExtra(ArticleWidgetActionHandler.EXTRA_ARTICLE_ID)
        val accountId = intent?.getIntExtra(ArticleWidgetActionHandler.EXTRA_ACCOUNT_ID, -1) ?: -1

        val widgetAction = ArticleWidgetActionHandler.parse(action, articleId, accountId)
        when (widgetAction) {
            is WidgetAction.OpenArticle -> {
                val openIntent = Intent(this, MainActivity::class.java).apply {
                    // Cờ CLEAR_TASK tránh giới hạn HomeEntry chỉ đọc extras ở lần khởi tạo đầu
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra(ExtraName.ARTICLE_ID, widgetAction.articleId)
                }
                startActivity(openIntent)
                finish()
            }
            is WidgetAction.MarkRead -> {
                val entry = EntryPointAccessors.fromApplication(
                    applicationContext,
                    WidgetEntryPoint::class.java,
                )
                val handler = ArticleWidgetActionHandler(entry.articleDao(), entry.widgetRefreshNotifier()::refresh)
                entry.applicationScope().launch(Dispatchers.IO) {
                    runCatching {
                        handler.markRead(widgetAction.accountId, widgetAction.articleId)
                    }
                }
                finish()
            }
            null -> finish()
        }
    }
}
