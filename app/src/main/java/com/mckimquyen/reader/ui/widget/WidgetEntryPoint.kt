package com.mckimquyen.reader.ui.widget

import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.FeedDao
import com.mckimquyen.reader.domain.repository.WidgetRefreshNotifier
import com.mckimquyen.reader.infrastructure.di.ApplicationScope
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun articleDao(): ArticleDao
    fun feedDao(): FeedDao
    fun widgetRefreshNotifier(): WidgetRefreshNotifier
    @ApplicationScope fun applicationScope(): CoroutineScope
}
