package com.k410sh4.budsia.core.audio.nativecore

internal class NativeAudioBridge {
    companion object {
        init {
            System.loadLibrary("budsia_audio")
        }
    }

    external fun nativeCreate(): Long
    external fun nativeDestroy(handle: Long)
    external fun nativeStart(
        handle: Long,
        inputDeviceId: Int,
        outputDeviceId: Int,
        processingMode: Int
    ): Int
    external fun nativeStop(handle: Long)
    external fun nativeSetMonitoring(handle: Long, enabled: Boolean): Int
    external fun nativeSetProcessingMode(handle: Long, processingMode: Int)
    external fun nativeGetStats(handle: Long): LongArray
    external fun nativeGetSignalMetrics(handle: Long): FloatArray
    external fun nativeGetWaveform(handle: Long): FloatArray
    external fun nativeGetLastError(handle: Long): String
}
