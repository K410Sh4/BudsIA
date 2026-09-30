package com.k410sh4.budsia.core.audio.realtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeAudioStatsDecoderTest {

    @Test
    fun decodesNativeSchemaWithoutInventingUnavailableMetrics() {
        val stats = LongArray(RealtimeAudioStatsDecoder.STAT_COUNT)
        stats[0] = 2
        stats[1] = 48_000
        stats[2] = 48_000
        stats[3] = 11
        stats[4] = 22
        stats[5] = 4_800
        stats[6] = 4_800
        stats[7] = 0
        stats[8] = 7
        stats[13] = -1
        stats[14] = 3
        stats[17] = 1_250_000
        stats[18] = 2_500_000
        stats[22] = 0
        stats[23] = 1
        stats[24] = 1
        stats[25] = 2
        stats[26] = 1

        val metrics = floatArrayOf(
            0.10f, 0.40f, 0.01f, 0.00f,
            0.08f, 0.30f, 0.00f, 0.00f
        )

        val snapshot = RealtimeAudioStatsDecoder.decode(
            stats = stats,
            metrics = metrics,
            waveform = floatArrayOf(-2f, 0.5f, 3f)
        )

        assertEquals(RealtimeEngineState.RUNNING, snapshot.state)
        assertEquals(48_000, snapshot.inputSampleRateHz)
        assertEquals(11, snapshot.inputDeviceId)
        assertNull(snapshot.inputXruns)
        assertEquals(3L, snapshot.outputXruns)
        assertEquals(RealtimeProcessingMode.DSP, snapshot.processingMode)
        assertEquals(StreamSharingMode.EXCLUSIVE, snapshot.inputSharingMode)
        assertEquals(StreamSharingMode.SHARED, snapshot.outputSharingMode)
        assertTrue(snapshot.outputAvailable)
        assertFalse(snapshot.monitoringEnabled)
        assertEquals(1.25, snapshot.lastProcessorMs, 0.0001)
        assertEquals(listOf(0f, 0.5f, 1f), snapshot.waveform)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsShortNativeStatsArray() {
        RealtimeAudioStatsDecoder.decode(
            stats = LongArray(2),
            metrics = FloatArray(8),
            waveform = FloatArray(0)
        )
    }
}
