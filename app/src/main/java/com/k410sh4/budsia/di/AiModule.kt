package com.k410sh4.budsia.di

import android.content.Context
import com.k410sh4.budsia.core.ai.enhancement.SherpaStreamingNeuralEnhancer
import com.k410sh4.budsia.core.ai.enhancement.StreamingAiCoordinator\nimport com.k410sh4.budsia.core.ai.enhancement.StreamingNeuralEnhancer
import com.k410sh4.budsia.core.ai.models.ModelManager
import com.k410sh4.budsia.core.ai.models.VerifiedModelManager\nimport com.k410sh4.budsia.core.audio.realtime.RealtimeAiTransport\nimport com.k410sh4.budsia.core.audio.realtime.RealtimeAudioEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    @Provides
    @Singleton
    fun provideModelManager(
        @ApplicationContext context: Context
    ): ModelManager = VerifiedModelManager(context)

    @Provides
    @Singleton
    fun provideStreamingNeuralEnhancer(): StreamingNeuralEnhancer =
        SherpaStreamingNeuralEnhancer(
            provider = "cpu",
            inferenceThreads = 2
        )

    @Provides
    @Singleton
    fun provideStreamingAiCoordinator(
        modelManager: ModelManager,
        enhancer: StreamingNeuralEnhancer,
        audioEngine: RealtimeAudioEngine,
        transport: RealtimeAiTransport
    ): StreamingAiCoordinator = StreamingAiCoordinator(
        modelManager = modelManager,
        enhancer = enhancer,
        audioEngine = audioEngine,
        transport = transport
    )
}
