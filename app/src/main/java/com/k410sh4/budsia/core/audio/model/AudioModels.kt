package com.k410sh4.budsia.core.audio.model

data class AudioCaptureConfig(
    val sampleRateHz: Int = 48_000,
    val channelCount: Int = 1,
    val frameDurationMs: Int = 20
) {
    val frameSamples: Int
        get() = (sampleRateHz * frameDurationMs / 1_000).coerceAtLeast(1)
}

data class AudioFrame(
    val samples: FloatArray,
    val sampleRateHz: Int,
    val capturedAtElapsedRealtimeNanos: Long,
    val sequence: Long
)

data class AudioSignalMetrics(
    val rms: Float,
    val peak: Float,
    val dcOffset: Float,
    val clippingRatio: Float
)

enum class ProcessingMode {
    RAW,
    DSP_ONLY,
    AI
}

enum class PipelineState {
    IDLE,
    STARTING,
    LISTENING,
    STOPPING,
    ERROR
}

data class StageLatency(
    val name: String,
    val durationNanos: Long
) {
    val durationMs: Double
        get() = durationNanos / 1_000_000.0
}

data class AudioPipelineSnapshot(
    val sequence: Long,
    val waveform: List<Float>,
    val rawMetrics: AudioSignalMetrics,
    val processedMetrics: AudioSignalMetrics,
    val latencies: List<StageLatency>,
    val mode: ProcessingMode,
    val enhancementEngineId: String,
    val enhancementApplied: Boolean
)
