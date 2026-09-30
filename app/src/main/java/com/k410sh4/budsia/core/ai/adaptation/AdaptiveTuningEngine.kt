package com.k410sh4.budsia.core.ai.adaptation

import kotlin.math.sqrt

/**
 * Bounded personalization layer.
 *
 * This does not mutate neural-model weights. It learns a bounded preferred
 * enhancement strength from explicit feedback. The preference is versioned
 * separately from live DSP so it can be evaluated before activation.
 */
class AdaptiveTuningEngine {

    fun applyFeedback(
        current: AdaptiveAudioProfile,
        feedback: AudioFeedback
    ): AdaptiveAudioProfile {
        val nextFeedbackCount = current.feedbackCount + 1L

        val step = (
            BASE_STEP /
                sqrt(1.0 + current.feedbackCount.toDouble() / DECAY_WINDOW)
            )
            .toFloat()
            .coerceAtLeast(MIN_STEP)

        val requestedMix = when (feedback) {
            AudioFeedback.MORE_FILTER ->
                current.preferredEnhancementStrength + step

            AudioFeedback.MORE_NATURAL ->
                current.preferredEnhancementStrength - step

            AudioFeedback.GOOD_AS_IS ->
                current.preferredEnhancementStrength
        }

        return current.copy(
            revision = current.revision + 1L,
            preferredEnhancementStrength = requestedMix.coerceIn(
                AdaptiveAudioProfile.MIN_PREFERRED_STRENGTH,
                AdaptiveAudioProfile.MAX_PREFERRED_STRENGTH
            ),
            feedbackCount = nextFeedbackCount,
            positiveFeedbackCount =
                current.positiveFeedbackCount +
                    if (feedback == AudioFeedback.GOOD_AS_IS) 1L else 0L
        )
    }

    fun reset(
        environment: AcousticEnvironment
    ): AdaptiveAudioProfile =
        AdaptiveAudioProfile.factory(environment)

    companion object {
        private const val BASE_STEP = 0.06
        private const val MIN_STEP = 0.01f
        private const val DECAY_WINDOW = 10.0
    }
}
