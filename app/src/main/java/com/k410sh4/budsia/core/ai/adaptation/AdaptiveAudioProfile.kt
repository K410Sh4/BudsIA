package com.k410sh4.budsia.core.ai.adaptation

enum class AcousticEnvironment(
    val displayName: String
) {
    GENERAL("Geral"),
    HOME("Casa"),
    STREET("Rua"),
    WORK("Trabalho"),
    CAR("Carro")
}

enum class AudioFeedback {
    MORE_FILTER,
    MORE_NATURAL,
    GOOD_AS_IS
}

data class AdaptiveAudioProfile(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val environment: AcousticEnvironment = AcousticEnvironment.GENERAL,
    val revision: Long = 0L,
    val enhancementMix: Float = DEFAULT_ENHANCEMENT_MIX,
    val feedbackCount: Long = 0L,
    val positiveFeedbackCount: Long = 0L
) {
    init {
        require(schemaVersion > 0)
        require(revision >= 0L)
        require(enhancementMix in MIN_ENHANCEMENT_MIX..MAX_ENHANCEMENT_MIX)
        require(feedbackCount >= 0L)
        require(positiveFeedbackCount >= 0L)
        require(positiveFeedbackCount <= feedbackCount)
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val DEFAULT_ENHANCEMENT_MIX = 0.85f
        const val MIN_ENHANCEMENT_MIX = 0.25f
        const val MAX_ENHANCEMENT_MIX = 1.00f

        fun factory(
            environment: AcousticEnvironment
        ): AdaptiveAudioProfile = AdaptiveAudioProfile(
            environment = environment
        )
    }
}
