package com.k410sh4.budsia.di

import android.content.Context
import com.k410sh4.budsia.core.ai.adaptation.AdaptiveBlendProcessor
import com.k410sh4.budsia.core.ai.adaptation.AdaptiveProfileRepository
import com.k410sh4.budsia.core.ai.enhancement.SherpaStreamingNeuralEnhancer
import com.k410sh4.budsia.core.ai.enhancement.StreamingAiCoordinator
import com.k410sh4.budsia.core.ai.enhancement.StreamingNeuralEnhancer
import com.k410sh4.budsia.core.ai.models.ModelManager
import com.k410sh4.budsia.core.ai.models.VerifiedModelManager
import com.k410sh4.budsia.core.audio.realtime.RealtimeAiTransport
import com.k410sh4.budsia.core.audio.realtime.RealtimeAudioEngine
import com.k410sh4.budsia.core.diagnostics.MonotonicClock
import com.k410sh4.budsia.core.performance.AiPerformancePolicy
import com.k410sh4.budsia.core.performance.DeviceHealthMonitor
import com.k410sh4.budsia.core.performance.InferencePerformanceHintFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

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
        transport: RealtimeAiTransport,
        clock: MonotonicClock,
        adaptiveProfiles: AdaptiveProfileRepository,
        adaptiveBlendProcessor: AdaptiveBlendProcessor,
        deviceHealthMonitor: DeviceHealthMonitor,
        performancePolicy: AiPerformancePolicy,
        performanceHintFactory: InferencePerformanceHintFactory,
        @AiInferenceDispatcher
        inferenceDispatcher: CoroutineDispatcher
    ): StreamingAiCoordinator = StreamingAiCoordinator(
        modelManager = modelManager,
        enhancer = enhancer,
        audioEngine = audioEngine,
        transport = transport,
        clock = clock,
        adaptiveProfiles = adaptiveProfiles,
        adaptiveBlendProcessor = adaptiveBlendProcessor,
        deviceHealthMonitor = deviceHealthMonitor,
        performancePolicy = performancePolicy,
        performanceHintFactory = performanceHintFactory,
        inferenceDispatcher = inferenceDispatcher
    )
}
