package com.k410sh4.budsia.core.audio.dsp

import com.k410sh4.budsia.core.audio.model.AudioFrame
import kotlin.math.PI

class HighPassAudioPreprocessor(
    private val cutoffHz: Float = 70f
) : AudioPreprocessor {

    private var previousInput = 0f
    private var previousOutput = 0f
    private var lastSampleRate = 0

    override fun reset() {
        previousInput = 0f
        previousOutput = 0f
        lastSampleRate = 0
    }

    override fun process(frame: AudioFrame): AudioFrame {
        if (frame.samples.isEmpty()) return frame
        require(cutoffHz > 0f && cutoffHz < frame.sampleRateHz / 2f) {
            "Invalid high-pass cutoff."
        }

        if (lastSampleRate != frame.sampleRateHz) {
            reset()
            lastSampleRate = frame.sampleRateHz
        }

        val dt = 1.0 / frame.sampleRateHz
        val rc = 1.0 / (2.0 * PI * cutoffHz)
        val alpha = (rc / (rc + dt)).toFloat()
        val output = FloatArray(frame.samples.size)

        for (i in frame.samples.indices) {
            val input = frame.samples[i]
            val filtered = alpha * (previousOutput + input - previousInput)
            previousInput = input
            previousOutput = filtered
            output[i] = filtered.coerceIn(-1f, 1f)
        }

        return frame.copy(samples = output)
    }
}
