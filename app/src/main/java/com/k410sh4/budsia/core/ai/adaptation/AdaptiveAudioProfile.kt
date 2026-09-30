package com.k410sh4.budsia.core.ai.adaptation

data class AdaptiveAudioProfile(
    val id: String,
    val name: String,
    val version: Int,
    val neuralMix: Float,
    val autoAdaptEnabled: Boolean,
    val feedbackCount: Int,
    val positiveCount: Int,
    val negativeCount: Int,
    val tooAggressiveCount: Int,
    val tooWeakCount: Int,
    val updatedAtEpochMs: Long,
    val isActive: Boolean
) {
    init {
        require(neuralMix in 0f..1f) {
            "neuralMix must be between 0 and 1."
        }
    }
}

enum class AdaptiveFeedback {
    BETTER,
    WORSE,
    TOO_AGGRESSIVE,
    TOO_WEAK
}

object AdaptiveProfileDefaults {
    val GENERAL = AdaptiveAudioProfile(
        id = "general",
        name = "Geral",
        version = 1,
        neuralMix = 0.90f,
        autoAdaptEnabled = true,
        feedbackCount = 0,
        positiveCount = 0,
        negativeCount = 0,
        tooAggressiveCount = 0,
        tooWeakCount = 0,
        updatedAtEpochMs = 0L,
        isActive = true
    )

    val presets: List<AdaptiveAudioProfile> = listOf(
        GENERAL,
        GENERAL.copy(id = "home", name = "Casa", isActive = false),
        GENERAL.copy(id = "street", name = "Rua", isActive = false),
        GENERAL.copy(id = "car", name = "Carro", isActive = false),
        GENERAL.copy(id = "work", name = "Trabalho", isActive = false)
    )
}
