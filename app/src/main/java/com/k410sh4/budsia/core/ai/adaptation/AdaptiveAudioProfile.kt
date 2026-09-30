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
    val preferredEnhancementStrength: Float = DEFAULT_PREFERRED_STRENGTH,
    val feedbackCount: Long = 0L,
    val positiveFeedbackCount: Long = 0L
) {
    init {
        require(schemaVersion > 0)
        require(revision >= 0L)
        require(preferredEnhancementStrength in MIN_PREFERRED_STRENGTH..MAX_PREFERRED_STRENGTH)
        require(feedbackCount >= 0L)
        require(positiveFeedbackCount >= 0L)
        require(positiveFeedbackCount <= feedbackCount)
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val DEFAULT_PREFERRED_STRENGTH = 0.85f
        const val MIN_PREFERRED_STRENGTH = 0.25f
        const val MAX_PREFERRED_STRENGTH = 1.00f

        fun factory(
            environment: AcousticEnvironment
        ): AdaptiveAudioProfile = AdaptiveAudioProfile(
            environment = environment
        )
    }
}
