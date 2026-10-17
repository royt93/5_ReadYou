package com.mckimquyen.reader.infrastructure.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.mckimquyen.reader.domain.repository.AccountDao
import com.mckimquyen.reader.domain.repository.AddedRssSourceDao
import com.mckimquyen.reader.domain.repository.ArticleDao
import com.mckimquyen.reader.domain.repository.ArticleReadPositionDao
import com.mckimquyen.reader.domain.repository.ArticleEmbeddingDao
import com.mckimquyen.reader.domain.repository.DailyDigestDao
import com.mckimquyen.reader.domain.repository.ArticleHighlightDao
import com.mckimquyen.reader.domain.repository.FeedDao
import com.mckimquyen.reader.domain.repository.GroupDao
import com.mckimquyen.reader.domain.model.rpg.BrainRpgLegacyPreferences
import com.mckimquyen.reader.domain.repository.BrainRpgDao
import com.mckimquyen.reader.domain.repository.BrainRpgLegacyImporter
import com.mckimquyen.reader.infrastructure.db.AndroidDatabase
import javax.inject.Singleton

/**
 * Provides Data Access Objects for database.
 *
 * - [ArticleDao]
 * - [FeedDao]
 * - [GroupDao]
 * - [AccountDao]
 * - [AddedRssSourceDao]
 * - [ArticleHighlightDao]
 */
@Module
@InstallIn(SingletonComponent::class)
object DbModule {

    @Provides
    @Singleton
    fun provideArticleDao(androidDatabase: AndroidDatabase): ArticleDao =
        androidDatabase.articleDao()

    @Provides
    @Singleton
    fun provideArticleReadPositionDao(androidDatabase: AndroidDatabase): ArticleReadPositionDao =
        androidDatabase.articleReadPositionDao()

    @Provides
    @Singleton
    fun provideFeedDao(androidDatabase: AndroidDatabase): FeedDao =
        androidDatabase.feedDao()

    @Provides
    @Singleton
    fun provideGroupDao(androidDatabase: AndroidDatabase): GroupDao =
        androidDatabase.groupDao()

    @Provides
    @Singleton
    fun provideAccountDao(androidDatabase: AndroidDatabase): AccountDao =
        androidDatabase.accountDao()

    @Provides
    @Singleton
    fun provideAddedRssSourceDao(androidDatabase: AndroidDatabase): AddedRssSourceDao =
        androidDatabase.addedRssSourceDao()

    @Provides
    @Singleton
    fun provideArticleHighlightDao(androidDatabase: AndroidDatabase): ArticleHighlightDao =
        androidDatabase.articleHighlightDao()

    @Provides
    @Singleton
    fun provideFeedHealthDao(androidDatabase: AndroidDatabase): com.mckimquyen.reader.domain.repository.FeedHealthDao =
        androidDatabase.feedHealthDao()

    @Provides
    @Singleton
    fun provideArticleEmbeddingDao(androidDatabase: AndroidDatabase): ArticleEmbeddingDao =
        androidDatabase.articleEmbeddingDao()

    @Provides
    @Singleton
    fun provideDailyDigestDao(androidDatabase: AndroidDatabase): DailyDigestDao =
        androidDatabase.dailyDigestDao()

    @Provides
    @Singleton
    fun provideBrainRpgDao(androidDatabase: AndroidDatabase): BrainRpgDao =
        androidDatabase.brainRpgDao()

    @Provides
    @Singleton
    fun provideBrainRpgLegacyImporter(
        @ApplicationContext context: Context,
        androidDatabase: AndroidDatabase,
        brainRpgDao: BrainRpgDao,
    ): BrainRpgLegacyImporter = BrainRpgLegacyImporter(
        androidDatabase,
        context.getSharedPreferences(BrainRpgLegacyPreferences.PREFS_NAME, Context.MODE_PRIVATE),
        brainRpgDao,
    )

    @Provides
    @Singleton
    fun provideReaderDatabase(@ApplicationContext context: Context): AndroidDatabase =
        AndroidDatabase.getInstance(context)
}
