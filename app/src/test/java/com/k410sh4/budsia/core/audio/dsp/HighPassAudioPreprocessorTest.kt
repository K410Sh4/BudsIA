package com.k410sh4.budsia.core.audio.dsp

import com.k410sh4.budsia.core.audio.model.AudioFrame
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test

class HighPassAudioPreprocessorTest {

    @Test
    fun dcSignalIsAttenuatedAcrossFrames() {
        val filter = HighPassAudioPreprocessor(cutoffHz = 70f)
        var frame = AudioFrame(
            samples = FloatArray(960) { 0.25f },
            sampleRateHz = 48_000,
            capturedAtElapsedRealtimeNanos = 0L,
            sequence = 0L
        )

        repeat(30) { index ->
            frame = filter.process(frame.copy(sequence = index.toLong()))
        }

        val averageAbsolute = frame.samples
            .map { abs(it) }
            .average()

        assertTrue(averageAbsolute < 0.005)
    }
}
