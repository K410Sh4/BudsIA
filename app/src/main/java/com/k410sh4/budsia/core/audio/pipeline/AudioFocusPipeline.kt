package com.k410sh4.budsia.core.audio.pipeline

import com.k410sh4.budsia.core.audio.analysis.AudioMetricsAnalyzer
import com.k410sh4.budsia.core.audio.capture.AudioCaptureEngine
import com.k410sh4.budsia.core.audio.dsp.AudioPreprocessor
import com.k410sh4.budsia.core.audio.enhancement.AudioEnhancementEngine
import com.k410sh4.budsia.core.audio.model.AudioCaptureConfig
import com.k410sh4.budsia.core.audio.model.AudioPipelineSnapshot
import com.k410sh4.budsia.core.audio.model.ProcessingMode
import com.k410sh4.budsia.core.audio.model.StageLatency
import com.k410sh4.budsia.core.diagnostics.MonotonicClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class AudioFocusPipeline(
    private val captureEngine: AudioCaptureEngine,
    private val preprocessor: AudioPreprocessor,
    private val enhancementEngine: AudioEnhancementEngine,
    private val metricsAnalyzer: AudioMetricsAnalyzer,
    private val clock: MonotonicClock
) {
    fun snapshots(
        config: AudioCaptureConfig = AudioCaptureConfig()
    ): Flow<AudioPipelineSnapshot> {
        return captureEngine.stream(config)
            .onStart {
                preprocessor.reset()
                enhancementEngine.reset()
            }
            .map { raw ->
                val rawMetrics = metricsAnalyzer.analyze(raw.samples)

                val dspStart = clock.nowNanos()
                val preprocessed = preprocessor.process(raw)
                val dspEnd = clock.nowNanos()

                val enhancementStart = clock.nowNanos()
                val enhancement = enhancementEngine.process(preprocessed)
                val enhancementEnd = clock.nowNanos()

                val analysisStart = clock.nowNanos()
                val processedMetrics = metricsAnalyzer.analyze(enhancement.frame.samples)
                val waveform = metricsAnalyzer.waveform(enhancement.frame.samples)
                val analysisEnd = clock.nowNanos()

                AudioPipelineSnapshot(
                    sequence = raw.sequence,
                    waveform = waveform,
                    rawMetrics = rawMetrics,
                    processedMetrics = processedMetrics,
                    latencies = listOf(
                        StageLatency("DSP", dspEnd - dspStart),
                        StageLatency("ENHANCEMENT", enhancementEnd - enhancementStart),
                        StageLatency("ANALYSIS", analysisEnd - analysisStart),
                        StageLatency("TOTAL_PROCESSING", analysisEnd - dspStart)
                    ),
                    mode = if (enhancement.applied) ProcessingMode.AI else ProcessingMode.DSP_ONLY,
                    enhancementEngineId = enhancement.engineId,
                    enhancementApplied = enhancement.applied
                )
            }
    }
}
