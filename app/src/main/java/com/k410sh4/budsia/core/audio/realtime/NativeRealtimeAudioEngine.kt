package com.k410sh4.budsia.core.audio.realtime

import com.k410sh4.budsia.core.audio.nativecore.NativeAudioBridge

class NativeRealtimeAudioEngine(
    private val bridge: NativeAudioBridge = NativeAudioBridge()
) : RealtimeAudioEngine {

    private val handle: Long = bridge.nativeCreate().also {
        check(it != 0L) { "Unable to allocate native audio engine." }
    }

    @Synchronized
    override fun start(config: RealtimeAudioConfig): EngineCommandResult {
        val code = bridge.nativeStart(
            handle = handle,
            inputDeviceId = config.inputDeviceId,
            outputDeviceId = config.outputDeviceId,
            processingMode = config.processingMode.nativeValue
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
