package com.k410sh4.budsia.core.ai.adaptation

import kotlin.math.min

/**
 * Deterministic wet/dry mixer for candidate adaptive control.
 *
 * dry = original microphone frame
 * wet = neural-enhanced frame
 *
 * The mixer mutates [wet] in place to avoid allocating another realtime-adjacent buffer.
 * It never amplifies above the linear combination of the two sources and clamps the final
 * signal to PCM float bounds.
 */
class AdaptiveNeuralMixer {

    fun mixInPlace(
        dry: FloatArray,
        wet: FloatArray,
        strength: Float
    ) {
        if (wet.isEmpty()) return

        val safeStrength = strength.coerceIn(
            AdaptiveAudioProfile.MIN_PREFERRED_STRENGTH,
            AdaptiveAudioProfile.MAX_PREFERRED_STRENGTH
        )

        if (safeStrength >= 0.9999f) return

        val dryWeight = 1f - safeStrength
        val overlap = min(dry.size, wet.size)

        for (index in 0 until overlap) {
            wet[index] = (
                dry[index] * dryWeight +
                    wet[index] * safeStrength
                ).coerceIn(-1f, 1f)
        }
    }
}
