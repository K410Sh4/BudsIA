package com.k410sh4.budsia.core.ai.adaptation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveTuningEngineTest {

    private val engine = AdaptiveTuningEngine()

    @Test
    fun tooAggressiveReducesNeuralMixWithoutLeavingBounds() {
        val updated = engine.applyFeedback(
            profile = AdaptiveProfileDefaults.GENERAL,
            feedback = AdaptiveFeedback.TOO_AGGRESSIVE,
            nowEpochMillis = 1234L
        )

        assertTrue(updated.neuralMix < AdaptiveProfileDefaults.GENERAL.neuralMix)
        assertTrue(updated.neuralMix in 0f..1f)
        assertEquals(1, updated.feedbackCount)
        assertEquals(1, updated.tooAggressiveCount)
        assertEquals(1234L, updated.updatedAtEpochMs)
    }

    @Test
    fun tooWeakIncreasesNeuralMixButClampsAtOne() {
        var profile = AdaptiveProfileDefaults.GENERAL.copy(
            neuralMix = 0.99f
        )

        repeat(20) {
            profile = engine.applyFeedback(
                profile = profile,
                feedback = AdaptiveFeedback.TOO_WEAK,
                nowEpochMillis = it.toLong()
            )
        }

        assertEquals(1f, profile.neuralMix, 0.000001f)
        assertEquals(20, profile.feedbackCount)
        assertEquals(20, profile.tooWeakCount)
    }

    @Test
    fun repeatedFeedbackUsesDiminishingStep() {
        val first = engine.applyFeedback(
            profile = AdaptiveProfileDefaults.GENERAL,
            feedback = AdaptiveFeedback.WORSE,
            nowEpochMillis = 1L
        )

        val second = engine.applyFeedback(
            profile = first,
            feedback = AdaptiveFeedback.WORSE,
            nowEpochMillis = 2L
        )

        val firstDelta =
            AdaptiveProfileDefaults.GENERAL.neuralMix - first.neuralMix
        val secondDelta = first.neuralMix - second.neuralMix

        assertTrue(secondDelta < firstDelta)
    }
}
