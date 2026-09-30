package com.k410sh4.budsia.core.audio.analysis

import com.k410sh4.budsia.core.audio.model.AudioSignalMetrics
import kotlin.math.abs
import kotlin.math.sqrt

class AudioMetricsAnalyzer {
    fun analyze(samples: FloatArray): AudioSignalMetrics {
        if (samples.isEmpty()) {
            return AudioSignalMetrics(
                rms = 0f,
                peak = 0f,
                dcOffset = 0f,
                clippingRatio = 0f
            )
        }

        var sum = 0.0
        var sumSquares = 0.0
        var peak = 0f
        var clipped = 0

        for (sample in samples) {
            sum += sample
            sumSquares += sample * sample
            peak = maxOf(peak, abs(sample))
            if (abs(sample) >= 0.999f) clipped++
        }

        return AudioSignalMetrics(
            rms = sqrt(sumSquares / samples.size).toFloat(),
            peak = peak,
            dcOffset = (sum / samples.size).toFloat(),
            clippingRatio = clipped.toFloat() / samples.size
        )
    }

    fun waveform(samples: FloatArray, points: Int = 72): List<Float> {
        if (samples.isEmpty() || points <= 0) return emptyList()
        val bucketSize = maxOf(1, samples.size / points)
        val result = ArrayList<Float>(points)

        var start = 0
        while (start < samples.size && result.size < points) {
            val end = minOf(samples.size, start + bucketSize)
            var peak = 0f
            for (i in start until end) {
                peak = maxOf(peak, abs(samples[i]))
            }
            result += peak.coerceIn(0f, 1f)
            start = end
        }

        while (result.size < points) result += 0f
        return result
    }
}
