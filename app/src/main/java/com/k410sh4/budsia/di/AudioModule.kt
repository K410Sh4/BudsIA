package com.k410sh4.budsia.di

import android.content.Context
import com.k410sh4.budsia.core.audio.analysis.AudioMetricsAnalyzer
import com.k410sh4.budsia.core.audio.capture.AndroidAudioCaptureEngine
import com.k410sh4.budsia.core.audio.capture.AudioCaptureEngine
import com.k410sh4.budsia.core.audio.dsp.AudioPreprocessor
import com.k410sh4.budsia.core.audio.dsp.HighPassAudioPreprocessor
import com.k410sh4.budsia.core.audio.enhancement.AudioEnhancementEngine
import com.k410sh4.budsia.core.audio.enhancement.BypassAudioEnhancementEngine
import com.k410sh4.budsia.core.audio.pipeline.AudioFocusPipeline
import com.k410sh4.budsia.core.audio.realtime.NativeRealtimeAudioEngine
import com.k410sh4.budsia.core.audio.realtime.RealtimeAiTransport
import com.k410sh4.budsia.core.audio.realtime.RealtimeAudioEngine
import com.k410sh4.budsia.core.audio.routing.AndroidAudioRouteController
import com.k410sh4.budsia.core.audio.routing.AndroidAudioRouteMonitor
import com.k410sh4.budsia.core.audio.routing.AudioRouteController
import com.k410sh4.budsia.core.audio.routing.AudioRouteMonitor
import com.k410sh4.budsia.core.diagnostics.AndroidMonotonicClock
import com.k410sh4.budsia.core.diagnostics.MonotonicClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AudioModule {

    // Pure-Kotlin reference path kept for deterministic tests and A/B diagnostics.
    @Provides
    @Singleton
    fun provideAudioCaptureEngine(): AudioCaptureEngine =
        AndroidAudioCaptureEngine()

    @Provides
    @Singleton
    fun provideAudioPreprocessor(): AudioPreprocessor =
        HighPassAudioPreprocessor()

    @Provides
    @Singleton
    fun provideAudioEnhancementEngine(): AudioEnhancementEngine =
        BypassAudioEnhancementEngine()

    @Provides
    @Singleton
    fun provideAudioMetricsAnalyzer(): AudioMetricsAnalyzer =
        AudioMetricsAnalyzer()

    @Provides
    @Singleton
    fun provideMonotonicClock(): MonotonicClock =
        AndroidMonotonicClock()

    @Provides
    @Singleton
    fun provideAudioFocusPipeline(
        captureEngine: AudioCaptureEngine,
        preprocessor: AudioPreprocessor,
        enhancementEngine: AudioEnhancementEngine,
        metricsAnalyzer: AudioMetricsAnalyzer,
        clock: MonotonicClock
    ): AudioFocusPipeline = AudioFocusPipeline(
        captureEngine = captureEngine,
        preprocessor = preprocessor,
        enhancementEngine = enhancementEngine,
        metricsAnalyzer = metricsAnalyzer,
        clock = clock
    )

    // One native engine instance backs both control/telemetry and the bounded
    // neural-worker PCM transport. This avoids duplicate microphone streams.
    @Provides
    @Singleton
    fun provideNativeRealtimeAudioEngine(): NativeRealtimeAudioEngine =
        NativeRealtimeAudioEngine()

    @Provides
    fun provideRealtimeAudioEngine(
        engine: NativeRealtimeAudioEngine
    ): RealtimeAudioEngine = engine

    @Provides
    fun provideRealtimeAiTransport(
        engine: NativeRealtimeAudioEngine
    ): RealtimeAiTransport = engine

    @Provides
    @Singleton
    fun provideAudioRouteMonitor(
        @ApplicationContext context: Context
    ): AudioRouteMonitor = AndroidAudioRouteMonitor(context)

    @Provides
    @Singleton
    fun provideAudioRouteController(
        @ApplicationContext context: Context
    ): AudioRouteController = AndroidAudioRouteController(context)
}
