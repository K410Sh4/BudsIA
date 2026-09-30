package com.k410sh4.budsia.core.ai.adaptation

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveNeuralMixerTest {

    private val mixer = AdaptiveNeuralMixer()

    @Test
    fun fullStrengthKeepsNeuralOutputUnchanged() {
        val dry = floatArrayOf(1f, -1f, 0.5f)
        val wet = floatArrayOf(0.2f, -0.2f, 0.1f)
        val originalWet = wet.copyOf()

        mixer.mixInPlace(
            dry = dry,
            wet = wet,
            strength = 1f
        )

        assertArrayEquals(originalWet, wet, 0.000001f)
    }

    @Test
    fun candidateStrengthBlendsDryAndWetDeterministically() {
        val dry = floatArrayOf(1f, -1f)
        val wet = floatArrayOf(0f, 0f)

        mixer.mixInPlace(
            dry = dry,
            wet = wet,
            strength = 0.75f
        )

        assertEquals(0.25f, wet[0], 0.000001f)
        assertEquals(-0.25f, wet[1], 0.000001f)
    }

    @Test
    fun strengthIsClampedToProfileSafetyRange() {
        val dry = floatArrayOf(1f)
        val wet = floatArrayOf(0f)

        mixer.mixInPlace(
            dry = dry,
            wet = wet,
            strength = -10f
        )

        assertEquals(0.75f, wet[0], 0.000001f)
    }

    @Test
    fun shorterDryBufferDoesNotCorruptWetTail() {
        val dry = floatArrayOf(1f)
        val wet = floatArrayOf(0f, 0.4f)

        mixer.mixInPlace(
            dry = dry,
            wet = wet,
            strength = 0.5f
        )

        assertTrue(wet[0] > 0f)
        assertEquals(0.4f, wet[1], 0.000001f)
    }
}
