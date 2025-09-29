package com.mckimquyen.reader.infrastructure.di

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.mckimquyen.reader.infrastructure.net.NetworkDataSource
import javax.inject.Singleton

/**
 * Provides network requests for Retrofit.
 *
 * - [NetworkDataSource]: For network requests within the application
 * - [Gson]: For JSON serialization/deserialization
 */
@Module
@InstallIn(SingletonComponent::class)
object RetrofitModule {

    @Provides
    @Singleton
    fun provideAppNetworkDataSource(): NetworkDataSource =
        NetworkDataSource.getInstance()

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()
}
