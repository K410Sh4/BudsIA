package com.k410sh4.budsia.core.ai.enhancement

import com.k410sh4.budsia.core.ai.adaptation.AdaptiveAudioProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveControlConfigTest {

    @Test
    fun sanitizesStrengthAndRevision() {
        val sanitized = AdaptiveControlConfig(
            enabled = true,
            strength = -5f,
            profileRevision = -20L,
            environmentLabel = "Rua"
        ).sanitized()

        assertTrue(sanitized.enabled)
        assertEquals(
            AdaptiveAudioProfile.MIN_PREFERRED_STRENGTH,
            sanitized.strength,
            0.000001f
        )
        assertEquals(0L, sanitized.profileRevision)
        assertEquals("Rua", sanitized.environmentLabel)
    }

    @Test
    fun keepsValidCandidateConfiguration() {
        val sanitized = AdaptiveControlConfig(
            enabled = true,
            strength = 0.72f,
            profileRevision = 42L,
            environmentLabel = "Trabalho"
        ).sanitized()

        assertEquals(0.72f, sanitized.strength, 0.000001f)
        assertEquals(42L, sanitized.profileRevision)
        assertEquals("Trabalho", sanitized.environmentLabel)
    }
}
