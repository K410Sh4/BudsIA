package com.k410sh4.budsia.core.ai.adaptation

data class AdaptiveMixResult(
    val samples: FloatArray,
    val applied: Boolean,
    val neuralMix: Float,
    val reason: String? = null
)

class AdaptiveAudioMixer {

    fun mix(
        original: FloatArray,
        enhanced: FloatArray,
        neuralMix: Float
    ): AdaptiveMixResult {
        val mix = neuralMix.coerceIn(0f, 1f)

        if (enhanced.isEmpty()) {
            return AdaptiveMixResult(
                samples = enhanced,
                applied = false,
                neuralMix = mix,
                reason = "Enhanced output is empty."
            )
        }

        if (original.size != enhanced.size) {
            return AdaptiveMixResult(
                samples = enhanced,
                applied = false,
                neuralMix = mix,
                reason = "Original/enhanced frame lengths differ."
            )
        }

        if (mix >= 0.999f) {
            return AdaptiveMixResult(
                samples = enhanced,
                applied = true,
                neuralMix = 1f
            )
        }

        if (mix <= 0.001f) {
            return AdaptiveMixResult(
                samples = original.copyOf(),
                applied = true,
                neuralMix = 0f
            )
        }

        val rawMix = 1f - mix
        val output = FloatArray(enhanced.size)

        for (index in output.indices) {
            output[index] = (
                original[index] * rawMix +
                    enhanced[index] * mix
                ).coerceIn(-1f, 1f)
        }

        return AdaptiveMixResult(
            samples = output,
            applied = true,
            neuralMix = mix
        )
    }
}
