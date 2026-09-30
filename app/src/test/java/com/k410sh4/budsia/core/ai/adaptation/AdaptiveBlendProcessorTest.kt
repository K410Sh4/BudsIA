package com.k410sh4.budsia.core.ai.adaptation

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveBlendProcessorTest {

    private val processor = AdaptiveBlendProcessor()

    @Test
    fun disabledReturnsVerifiedNeuralOutputUntouched() {
        val dry = floatArrayOf(1f, -1f)
        val wet = floatArrayOf(0.2f, -0.4f)

        val result = processor.blend(
            dry = dry,
            wet = wet,
            requestedStrength = 0.5f,
            enabled = false
        )

        assertFalse(result.applied)
        assertArrayEquals(wet, result.samples, 0.0001f)
        assertEquals("disabled", result.reason)
    }

    @Test
    fun enabledUsesConvexDryWetMix() {
        val result = processor.blend(
            dry = floatArrayOf(1f, -1f),
            wet = floatArrayOf(0f, 0f),
            requestedStrength = 0.75f,
            enabled = true
        )

        assertTrue(result.applied)
        assertArrayEquals(
            floatArrayOf(0.25f, -0.25f),
            result.samples,
            0.0001f
        )
    }

    @Test
    fun mismatchedFramesNeverPretendAdaptiveControlWasApplied() {
        val wet = floatArrayOf(0.1f, 0.2f, 0.3f)

        val result = processor.blend(
            dry = floatArrayOf(1f),
            wet = wet,
            requestedStrength = 0.85f,
            enabled = true
        )

        assertFalse(result.applied)
        assertEquals("frame-size-mismatch", result.reason)
        assertArrayEquals(wet, result.samples, 0.0001f)
    }

    @Test
    fun outOfRangeStrengthIsClampedToSafetyBounds() {
        val result = processor.blend(
            dry = floatArrayOf(1f),
            wet = floatArrayOf(0f),
            requestedStrength = -5f,
            enabled = true
        )

        assertEquals(
            AdaptiveAudioProfile.MIN_PREFERRED_STRENGTH,
            result.requestedStrength,
            0.0001f
        )
        assertEquals(0.75f, result.samples[0], 0.0001f)
    }
}
