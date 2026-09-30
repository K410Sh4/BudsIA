package com.k410sh4.budsia.core.ai.adaptation

data class AdaptiveBlendResult(
    val samples: FloatArray,
    val applied: Boolean,
    val requestedStrength: Float,
    val reason: String? = null
)

/**
 * Deterministic dry/wet controller.
 *
 * This is intentionally not model training. The factory neural output remains
 * unchanged; the adaptive profile only controls how much verified enhanced
 * signal is mixed with the time-aligned dry frame.
 */
class AdaptiveBlendProcessor {

    fun blend(
        dry: FloatArray,
        wet: FloatArray,
        requestedStrength: Float,
        enabled: Boolean
    ): AdaptiveBlendResult {
        val strength = requestedStrength.coerceIn(
            AdaptiveAudioProfile.MIN_PREFERRED_STRENGTH,
            AdaptiveAudioProfile.MAX_PREFERRED_STRENGTH
        )

        if (!enabled) {
            return AdaptiveBlendResult(
                samples = wet,
                applied = false,
                requestedStrength = strength,
                reason = "disabled"
            )
        }

        if (dry.isEmpty() || wet.isEmpty()) {
            return AdaptiveBlendResult(
                samples = wet,
                applied = false,
                requestedStrength = strength,
                reason = "empty-frame"
            )
        }

        if (dry.size != wet.size) {
            return AdaptiveBlendResult(
                samples = wet,
                applied = false,
                requestedStrength = strength,
                reason = "frame-size-mismatch"
            )
        }

        if (strength >= 0.9999f) {
            return AdaptiveBlendResult(
                samples = wet,
                applied = true,
                requestedStrength = strength
            )
        }

        val dryGain = 1f - strength
        val mixed = FloatArray(wet.size)

        for (index in wet.indices) {
            mixed[index] = (
                dry[index] * dryGain +
                    wet[index] * strength
                ).coerceIn(-1f, 1f)
        }

        return AdaptiveBlendResult(
            samples = mixed,
            applied = true,
            requestedStrength = strength
        )
    }
}
