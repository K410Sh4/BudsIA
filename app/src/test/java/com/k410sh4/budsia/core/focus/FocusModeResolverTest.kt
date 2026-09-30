package com.k410sh4.budsia.core.focus

import com.k410sh4.budsia.core.ai.models.AiModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusModeResolverTest {

    private val resolver = FocusModeResolver()

    @Test
    fun voiceUsesExact48kModelWhenInstalled() {
        val plan = resolver.plan(
            mode = FocusMode.VOICE,
            inputSampleRateHz = 48_000,
            installedModelIds = setOf(
                AiModelCatalog.DPDFNET2_48K_HR.id
            )
        )

        assertEquals(
            FocusSupport.AVAILABLE,
            plan.capability.support
        )
        assertTrue(plan.useNeuralEnhancement)
    }

    @Test
    fun noiseReductionUsesExact16kModelWhenInstalled() {
        val plan = resolver.plan(
            mode = FocusMode.NOISE_REDUCTION,
            inputSampleRateHz = 16_000,
            installedModelIds = setOf(
                AiModelCatalog.GTCRN_16K.id
            )
        )

        assertTrue(plan.useNeuralEnhancement)
    }

    @Test
    fun autoDegradesToDspForUnsupportedRate() {
        val plan = resolver.plan(
            mode = FocusMode.AUTO,
            inputSampleRateHz = 32_000,
            installedModelIds = AiModelCatalog.all
                .map { it.id }
                .toSet()
        )

        assertEquals(
            FocusSupport.DEGRADED,
            plan.capability.support
        )
        assertFalse(plan.useNeuralEnhancement)
    }

    @Test
    fun specificSoundDoesNotPretendOfflineSeparationIsRealtime() {
        val capability = resolver.capability(
            mode = FocusMode.SPECIFIC_SOUND,
            inputSampleRateHz = 48_000,
            installedModelIds = AiModelCatalog.all
                .map { it.id }
                .toSet()
        )

        assertEquals(
            FocusSupport.UNAVAILABLE,
            capability.support
        )
    }
}
