package com.k410sh4.budsia.core.audio.realtime

import com.k410sh4.budsia.core.audio.nativecore.NativeAudioBridge

class NativeRealtimeAudioEngine internal constructor(
    private val bridge: NativeAudioBridge
) : RealtimeAudioEngine, RealtimeAiTransport {

    constructor() : this(NativeAudioBridge())

    private val handle: Long = bridge.nativeCreate().also {
        check(it != 0L) { "Unable to allocate native audio engine." }
    }

    @Synchronized
    override fun start(config: RealtimeAudioConfig): EngineCommandResult {
        val code = bridge.nativeStart(
            handle = handle,
            inputDeviceId = config.inputDeviceId,
            outputDeviceId = config.outputDeviceId,
            processingMode = config.processingMode.nativeValue,
            communicationMode = config.communicationMode
        )

        return if (code == 0) {
            EngineCommandResult(success = true)
        } else {
            EngineCommandResult(
                success = false,
                code = code,
                message = lastError()
            )
        }
    }

    @Synchronized
    override fun stop() {
        bridge.nativeStop(handle)
    }

    @Synchronized
    override fun setMonitoring(enabled: Boolean): EngineCommandResult {
        val code = bridge.nativeSetMonitoring(handle, enabled)
        return if (code == 0) {
            EngineCommandResult(success = true)
        } else {
            EngineCommandResult(
                success = false,
                code = code,
                message = lastError()
            )
        }
    }

    override fun setProcessingMode(mode: RealtimeProcessingMode) {
        bridge.nativeSetProcessingMode(handle, mode.nativeValue)
    }

    override fun readInput(
        destination: FloatArray,
        requestedCount: Int
    ): Int = bridge.nativeReadAiInput(
        handle = handle,
        destination = destination,
        requestedCount = requestedCount.coerceIn(0, destination.size)
    )

    override fun writeOutput(
        source: FloatArray,
        requestedCount: Int
    ): Int = bridge.nativeWriteAiOutput(
        handle = handle,
        source = source,
        requestedCount = requestedCount.coerceIn(0, source.size)
    )

    override fun clear() {
        bridge.nativeClearAiTransport(handle)
    }

    override fun snapshot(): RealtimeAudioSnapshot =
        RealtimeAudioStatsDecoder.decode(
            stats = bridge.nativeGetStats(handle),
            metrics = bridge.nativeGetSignalMetrics(handle),
            waveform = bridge.nativeGetWaveform(handle)
        )

    override fun lastError(): String? =
        bridge.nativeGetLastError(handle)
            .trim()
            .takeIf { it.isNotEmpty() }
}
