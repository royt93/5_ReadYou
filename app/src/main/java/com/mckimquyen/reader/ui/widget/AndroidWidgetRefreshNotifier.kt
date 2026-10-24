package com.mckimquyen.reader.ui.widget

import android.content.Context
import com.mckimquyen.reader.domain.repository.WidgetRefreshNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidWidgetRefreshNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : WidgetRefreshNotifier {
    override fun refresh() = ArticleWidgetUpdater.notifyDataChanged(context)
}
