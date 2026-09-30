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
            AdaptiveAudioProfile.MAX_ENHANCEMENT_MIX,
            profile.enhancementMix,
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
            AdaptiveAudioProfile.MIN_ENHANCEMENT_MIX,
            profile.enhancementMix,
            0.0001f
        )
        assertTrue(profile.enhancementMix > 0f)
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
            initial.enhancementMix,
            updated.enhancementMix,
            0.0001f
        )
        assertEquals(1L, updated.feedbackCount)
        assertEquals(1L, updated.positiveFeedbackCount)
        assertEquals(1L, updated.revision)
    }
}
