package com.mckimquyen.reader.infrastructure.di

import com.mckimquyen.reader.ui.page.rpg.DataStoreStreakRescuePromptDayStore
import com.mckimquyen.reader.ui.page.rpg.StreakRescuePromptDayStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class StreakRescueModule {
    @Binds
    abstract fun bindPromptDayStore(impl: DataStoreStreakRescuePromptDayStore): StreakRescuePromptDayStore
}
