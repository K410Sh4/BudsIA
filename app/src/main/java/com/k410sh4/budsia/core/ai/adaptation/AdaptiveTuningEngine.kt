package com.k410sh4.budsia.core.ai.adaptation

import kotlin.math.sqrt

class AdaptiveTuningEngine {

    fun applyFeedback(
        profile: AdaptiveAudioProfile,
        feedback: AdaptiveFeedback,
        nowEpochMillis: Long
    ): AdaptiveAudioProfile {
        val learningScale = (
            1.0 / sqrt(1.0 + profile.feedbackCount * 0.20)
        ).toFloat()

        val delta = when (feedback) {
            AdaptiveFeedback.BETTER -> 0.025f
            AdaptiveFeedback.WORSE -> -0.080f
            AdaptiveFeedback.TOO_AGGRESSIVE -> -0.060f
            AdaptiveFeedback.TOO_WEAK -> 0.060f
        } * learningScale

        return profile.copy(
            neuralMix = (profile.neuralMix + delta).coerceIn(0f, 1f),
            feedbackCount = profile.feedbackCount + 1,
            positiveCount = profile.positiveCount +
                if (feedback == AdaptiveFeedback.BETTER) 1 else 0,
            negativeCount = profile.negativeCount +
                if (feedback == AdaptiveFeedback.WORSE) 1 else 0,
            tooAggressiveCount = profile.tooAggressiveCount +
                if (feedback == AdaptiveFeedback.TOO_AGGRESSIVE) 1 else 0,
            tooWeakCount = profile.tooWeakCount +
                if (feedback == AdaptiveFeedback.TOO_WEAK) 1 else 0,
            updatedAtEpochMs = nowEpochMillis
        )
    }
}
