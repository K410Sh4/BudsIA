package com.k410sh4.budsia.core.audio.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioMetricsAnalyzerTest {

    private val analyzer = AudioMetricsAnalyzer()

    @Test
    fun silenceProducesZeroMetrics() {
        val metrics = analyzer.analyze(FloatArray(480))

        assertEquals(0f, metrics.rms, 0.000001f)
        assertEquals(0f, metrics.peak, 0.000001f)
        assertEquals(0f, metrics.dcOffset, 0.000001f)
        assertEquals(0f, metrics.clippingRatio, 0.000001f)
    }

    @Test
    fun clippingRatioCountsOnlyNearFullScaleSamples() {
        val metrics = analyzer.analyze(
            floatArrayOf(1f, -1f, 0.5f, -0.5f)
        )

        assertEquals(0.5f, metrics.clippingRatio, 0.000001f)
        assertTrue(metrics.rms > 0f)
    }

    @Test
    fun waveformIsBoundedAndFixedSize() {
        val waveform = analyzer.waveform(
            FloatArray(960) { index -> if (index % 2 == 0) 0.8f else -0.4f },
            points = 48
        )

        assertEquals(48, waveform.size)
        assertTrue(waveform.all { it in 0f..1f })
    }
}
