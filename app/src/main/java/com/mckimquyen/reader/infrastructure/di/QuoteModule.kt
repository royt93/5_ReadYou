package com.mckimquyen.reader.infrastructure.di

import com.mckimquyen.reader.infrastructure.quote.QuoteBitmapRenderer
import com.mckimquyen.reader.infrastructure.quote.QuoteCardRenderer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object QuoteModule {

    @Provides
    fun provideQuoteBitmapRenderer(): QuoteBitmapRenderer =
        QuoteBitmapRenderer { spec, template -> QuoteCardRenderer.render(spec, template) }
}
