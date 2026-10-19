package com.mckimquyen.reader.infrastructure.di

import com.mckimquyen.reader.domain.sv.ReadNewsSpeaker
import com.mckimquyen.reader.infrastructure.audio.TtsManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReadNewsModule {

    @Provides
    @Singleton
    fun provideReadNewsSpeaker(ttsManager: TtsManager): ReadNewsSpeaker = object : ReadNewsSpeaker {
        override val completions: Flow<Unit> = ttsManager.completions
        override fun play(text: String, title: String, subtitle: String) = ttsManager.play(text, title, subtitle)
        override fun speakNotice(text: String) = ttsManager.speakNotice(text)
        override fun stop() = ttsManager.stop()
    }
}
