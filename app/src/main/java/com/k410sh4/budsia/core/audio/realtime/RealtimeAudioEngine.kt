package com.k410sh4.budsia.core.audio.realtime

import com.k410sh4.budsia.core.audio.model.AudioSignalMetrics

enum class RealtimeProcessingMode(val nativeValue: Int) {
    RAW(0),
    DSP(1),
    AI(2)
}

enum class RealtimeEngineState {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    ERROR,
    UNKNOWN
}

enum class StreamSharingMode {
    UNKNOWN,
    EXCLUSIVE,
    SHARED
}

data class RealtimeAudioConfig(
    val inputDeviceId: Int = 0,
    val outputDeviceId: Int = 0,
    val processingMode: RealtimeProcessingMode = RealtimeProcessingMode.DSP
)

data class EngineCommandResult(
    val success: Boolean,
    val code: Int = 0,
    val message: String? = null
)

data class RealtimeAudioSnapshot(
    val state: RealtimeEngineState,
    val inputSampleRateHz: Int,
    val outputSampleRateHz: Int,
    val inputDeviceId: Int,
    val outputDeviceId: Int,
    val inputFrames: Long,
    val processedFrames: Long,
    val outputFrames: Long,
    val droppedInputSamples: Long,
    val outputOverrunSamples: Long,
    val outputUnderrunSamples: Long,
    val inputCallbacks: Long,
    val outputCallbacks: Long,
    val inputXruns: Long?,
    val outputXruns: Long?,
    val maxInputCallbackNanos: Long,
    val maxOutputCallbackNanos: Long,
    val lastProcessorNanos: Long,
    val maxProcessorNanos: Long,
    val inputRingHighWatermark: Long,
    val outputRingHighWatermark: Long,
    val disconnectCount: Long,
    val monitoringEnabled: Boolean,
    val processingMode: RealtimeProcessingMode,
    val inputSharingMode: StreamSharingMode,
    val outputSharingMode: StreamSharingMode,
    val outputAvailable: Boolean,
    val lastErrorCode: Int,
    val aiInputDroppedSamples: Long,
    val aiEnhancedSamples: Long,
    val rawMetrics: AudioSignalMetrics,
    val processedMetrics: AudioSignalMetrics,
    val waveform: List<Float>
) {
    val lastProcessorMs: Double
        get() = lastProcessorNanos / 1_000_000.0

    val maxProcessorMs: Double
        get() = maxProcessorNanos / 1_000_000.0
}

interface RealtimeAudioEngine {
    fun start(config: RealtimeAudioConfig = RealtimeAudioConfig()): EngineCommandResult
    fun stop()
    fun setMonitoring(enabled: Boolean): EngineCommandResult
    fun setProcessingMode(mode: RealtimeProcessingMode)
    fun snapshot(): RealtimeAudioSnapshot
    fun lastError(): String?
}


/**
 * Low-level bounded transport between the native realtime engine and the
 * non-realtime neural worker. Never call these methods from an Oboe callback.
 */
interface RealtimeAiTransport {
    fun readInput(destination: FloatArray, requestedCount: Int): Int
    fun writeOutput(source: FloatArray, requestedCount: Int): Int
    fun clear()
}
