package com.k410sh4.budsia.di

import com.k410sh4.budsia.core.audio.analysis.AudioMetricsAnalyzer
import com.k410sh4.budsia.core.audio.capture.AndroidAudioCaptureEngine
import com.k410sh4.budsia.core.audio.capture.AudioCaptureEngine
import com.k410sh4.budsia.core.audio.dsp.AudioPreprocessor
import com.k410sh4.budsia.core.audio.dsp.HighPassAudioPreprocessor
import com.k410sh4.budsia.core.audio.enhancement.AudioEnhancementEngine
import com.k410sh4.budsia.core.audio.enhancement.BypassAudioEnhancementEngine
import com.k410sh4.budsia.core.audio.pipeline.AudioFocusPipeline
import com.k410sh4.budsia.core.diagnostics.AndroidMonotonicClock
import com.k410sh4.budsia.core.diagnostics.MonotonicClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AudioModule {

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
}
