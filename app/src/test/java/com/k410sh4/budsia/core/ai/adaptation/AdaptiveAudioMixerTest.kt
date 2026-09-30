package com.k410sh4.budsia.core.ai.adaptation

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveAudioMixerTest {

    private val mixer = AdaptiveAudioMixer()

    @Test
    fun zeroMixReturnsOriginal() {
        val original = floatArrayOf(0.1f, -0.2f, 0.3f)
        val enhanced = floatArrayOf(0.8f, -0.8f, 0.8f)

        val result = mixer.mix(
            original = original,
            enhanced = enhanced,
            neuralMix = 0f
        )

        assertTrue(result.applied)
        assertArrayEquals(original, result.samples, 0.000001f)
    }

    @Test
    fun fullMixReturnsEnhanced() {
        val original = floatArrayOf(0.1f, -0.2f, 0.3f)
        val enhanced = floatArrayOf(0.8f, -0.8f, 0.8f)

        val result = mixer.mix(
            original = original,
            enhanced = enhanced,
            neuralMix = 1f
        )

        assertTrue(result.applied)
        assertArrayEquals(enhanced, result.samples, 0.000001f)
    }

    @Test
    fun midpointProducesDeterministicBlend() {
        val result = mixer.mix(
            original = floatArrayOf(0f, 1f),
            enhanced = floatArrayOf(1f, 0f),
            neuralMix = 0.5f
        )

        assertArrayEquals(
            floatArrayOf(0.5f, 0.5f),
            result.samples,
            0.000001f
        )
    }

    @Test
    fun mismatchedFramesDoNotPretendAdaptiveBlendWasApplied() {
        val enhanced = floatArrayOf(0.2f, 0.3f)

        val result = mixer.mix(
            original = floatArrayOf(0.1f),
            enhanced = enhanced,
            neuralMix = 0.8f
        )

        assertFalse(result.applied)
        assertArrayEquals(enhanced, result.samples, 0.000001f)
        assertTrue(result.reason?.isNotBlank() == true)
    }
}
