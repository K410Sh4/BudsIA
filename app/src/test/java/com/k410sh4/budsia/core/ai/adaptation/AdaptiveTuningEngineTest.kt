package com.k410sh4.budsia.core.ai.adaptation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveTuningEngineTest {

    private val tuner = AdaptiveTuningEngine()

    @Test
    fun moreFilterIncreasesMixButNeverExceedsFactoryBound() {
        var profile = AdaptiveAudioProfile.factory(
            AcousticEnvironment.GENERAL
        )

        repeat(200) {
            profile = tuner.applyFeedback(
                profile,
                AudioFeedback.MORE_FILTER
            )
        }

        assertEquals(
            AdaptiveAudioProfile.MAX_PREFERRED_STRENGTH,
            profile.preferredEnhancementStrength,
            0.0001f
        )
        assertEquals(200L, profile.feedbackCount)
        assertEquals(200L, profile.revision)
    }

    @Test
    fun moreNaturalDecreasesMixButNeverDisablesEnhancementCompletely() {
        var profile = AdaptiveAudioProfile.factory(
            AcousticEnvironment.HOME
        )

        repeat(200) {
            profile = tuner.applyFeedback(
                profile,
                AudioFeedback.MORE_NATURAL
            )
        }

        assertEquals(
            AdaptiveAudioProfile.MIN_PREFERRED_STRENGTH,
            profile.preferredEnhancementStrength,
            0.0001f
        )
        assertTrue(profile.preferredEnhancementStrength > 0f)
    }

    @Test
    fun repeatedFeedbackUsesSmallerBoundedSteps() {
        val initial = AdaptiveAudioProfile.factory(
            AcousticEnvironment.STREET
        )

        val first = tuner.applyFeedback(
            initial,
            AudioFeedback.MORE_FILTER
        )

        var matured = first
        repeat(50) {
            matured = tuner.applyFeedback(
                matured,
                AudioFeedback.GOOD_AS_IS
            )
        }

        val afterMaturedFeedback = tuner.applyFeedback(
            matured,
            AudioFeedback.MORE_FILTER
        )

        val firstDelta =
            first.preferredEnhancementStrength -
                initial.preferredEnhancementStrength
        val laterDelta =
            afterMaturedFeedback.preferredEnhancementStrength -
                matured.preferredEnhancementStrength

        assertTrue(laterDelta > 0f)
        assertTrue(laterDelta < firstDelta)
    }

    @Test
    fun resetReturnsFactoryPreferenceForSameEnvironment() {
        val changed = tuner.applyFeedback(
            AdaptiveAudioProfile.factory(
                AcousticEnvironment.CAR
            ),
            AudioFeedback.MORE_FILTER
        )

        val reset = tuner.reset(changed.environment)

        assertEquals(AcousticEnvironment.CAR, reset.environment)
        assertEquals(
            AdaptiveAudioProfile.DEFAULT_PREFERRED_STRENGTH,
            reset.preferredEnhancementStrength,
            0.0001f
        )
        assertEquals(0L, reset.feedbackCount)
        assertEquals(0L, reset.revision)
    }

    @Test
    fun goodAsIsRecordsPositiveFeedbackWithoutChangingMix() {
        val initial = AdaptiveAudioProfile.factory(
            AcousticEnvironment.WORK
        )

        val updated = tuner.applyFeedback(
            initial,
            AudioFeedback.GOOD_AS_IS
        )

        assertEquals(
            initial.preferredEnhancementStrength,
            updated.preferredEnhancementStrength,
            0.0001f
        )
        assertEquals(1L, updated.feedbackCount)
        assertEquals(1L, updated.positiveFeedbackCount)
        assertEquals(1L, updated.revision)
    }
}
