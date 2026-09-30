package com.k410sh4.budsia.core.ai.adaptation

import kotlin.math.sqrt

/**
 * Bounded personalization layer.
 *
 * This does not mutate neural-model weights. It adapts the wet/dry neural mix
 * from explicit user feedback and keeps every change reversible/versioned.
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
                current.enhancementMix + step

            AudioFeedback.MORE_NATURAL ->
                current.enhancementMix - step

            AudioFeedback.GOOD_AS_IS ->
                current.enhancementMix
        }

        return current.copy(
            revision = current.revision + 1L,
            enhancementMix = requestedMix.coerceIn(
                AdaptiveAudioProfile.MIN_ENHANCEMENT_MIX,
                AdaptiveAudioProfile.MAX_ENHANCEMENT_MIX
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
