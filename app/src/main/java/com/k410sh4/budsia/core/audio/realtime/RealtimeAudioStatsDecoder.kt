package com.k410sh4.budsia.core.audio.realtime

import com.k410sh4.budsia.core.audio.model.AudioSignalMetrics

internal object RealtimeAudioStatsDecoder {
    const val STAT_COUNT = 28
    const val SIGNAL_METRIC_COUNT = 8

    fun decode(
        stats: LongArray,
        metrics: FloatArray,
        waveform: FloatArray
    ): RealtimeAudioSnapshot {
        require(stats.size >= STAT_COUNT) {
            "Native stats schema mismatch: expected >= $STAT_COUNT, got ${stats.size}."
        }
        require(metrics.size >= SIGNAL_METRIC_COUNT) {
            "Native signal schema mismatch: expected >= $SIGNAL_METRIC_COUNT, got ${metrics.size}."
        }

        return RealtimeAudioSnapshot(
            state = decodeState(stats[0]),
            inputSampleRateHz = stats[1].toInt(),
            outputSampleRateHz = stats[2].toInt(),
            inputDeviceId = stats[3].toInt(),
            outputDeviceId = stats[4].toInt(),
            inputFrames = stats[5],
            processedFrames = stats[6],
            outputFrames = stats[7],
            droppedInputSamples = stats[8],
            outputOverrunSamples = stats[9],
            outputUnderrunSamples = stats[10],
            inputCallbacks = stats[11],
            outputCallbacks = stats[12],
            inputXruns = stats[13].takeIf { it >= 0 },
            outputXruns = stats[14].takeIf { it >= 0 },
            maxInputCallbackNanos = stats[15],
            maxOutputCallbackNanos = stats[16],
            lastProcessorNanos = stats[17],
            maxProcessorNanos = stats[18],
            inputRingHighWatermark = stats[19],
            outputRingHighWatermark = stats[20],
            disconnectCount = stats[21],
            monitoringEnabled = stats[22] == 1L,
            processingMode = if (stats[23] == 0L) {
                RealtimeProcessingMode.RAW
            } else {
                RealtimeProcessingMode.DSP
            },
            inputSharingMode = decodeSharing(stats[24]),
            outputSharingMode = decodeSharing(stats[25]),
            outputAvailable = stats[26] == 1L,
            lastErrorCode = stats[27].toInt(),
            rawMetrics = AudioSignalMetrics(
                rms = metrics[0],
                peak = metrics[1],
                dcOffset = metrics[2],
                clippingRatio = metrics[3]
            ),
            processedMetrics = AudioSignalMetrics(
                rms = metrics[4],
                peak = metrics[5],
                dcOffset = metrics[6],
                clippingRatio = metrics[7]
            ),
            waveform = waveform.map { it.coerceIn(0f, 1f) }
        )
    }

    private fun decodeState(value: Long): RealtimeEngineState = when (value) {
        0L -> RealtimeEngineState.STOPPED
        1L -> RealtimeEngineState.STARTING
        2L -> RealtimeEngineState.RUNNING
        3L -> RealtimeEngineState.STOPPING
        4L -> RealtimeEngineState.ERROR
        else -> RealtimeEngineState.UNKNOWN
    }

    private fun decodeSharing(value: Long): StreamSharingMode = when (value) {
        1L -> StreamSharingMode.EXCLUSIVE
        2L -> StreamSharingMode.SHARED
        else -> StreamSharingMode.UNKNOWN
    }
}
