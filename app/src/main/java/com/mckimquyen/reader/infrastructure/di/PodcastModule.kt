package com.mckimquyen.reader.infrastructure.di

import com.mckimquyen.reader.domain.model.podcast.SleepTimer
import com.mckimquyen.reader.domain.sv.PodcastServiceStarter
import com.mckimquyen.reader.domain.sv.TtsGate
import com.mckimquyen.reader.infrastructure.audio.TtsManager
import com.mckimquyen.reader.infrastructure.audio.TtsState
import android.content.Context
import com.mckimquyen.reader.infrastructure.podcast.PodcastPlaybackService
import dagger.Module
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PodcastModule {

    @Provides
    @Singleton
    fun provideTtsGate(ttsManager: TtsManager): TtsGate = object : TtsGate {
        override val isSpeaking: Boolean get() = ttsManager.ttsState.value == TtsState.PLAYING
        override fun stop() = ttsManager.stop()
        override fun setOnPlayRequested(callback: (() -> Unit)?) {
            ttsManager.onPlayRequested = callback
        }
    }

    @Provides
    fun provideSleepTimer(): SleepTimer = SleepTimer()

    @Provides
    @Singleton
    fun providePodcastServiceStarter(@ApplicationContext context: Context): PodcastServiceStarter =
        PodcastServiceStarter { PodcastPlaybackService.start(context) }
}
