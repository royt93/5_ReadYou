package com.mckimquyen.reader.infrastructure.di

import com.mckimquyen.reader.ui.page.rsvp.DataStoreRsvpCalibrationStore
import com.mckimquyen.reader.ui.page.rsvp.RsvpCalibrationStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RsvpCalibrationModule {
    @Binds
    abstract fun bindRsvpCalibrationStore(impl: DataStoreRsvpCalibrationStore): RsvpCalibrationStore
}
