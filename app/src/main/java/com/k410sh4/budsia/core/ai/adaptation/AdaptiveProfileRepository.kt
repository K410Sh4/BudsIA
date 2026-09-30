package com.k410sh4.budsia.core.ai.adaptation

import kotlinx.coroutines.flow.StateFlow

interface AdaptiveProfileRepository {
    val activeProfile: StateFlow<AdaptiveAudioProfile>

    /**
     * Disabled by default. When enabled, the verified neural output may be
     * blended with the dry input according to the active profile.
     */
    val runtimeControlEnabled: StateFlow<Boolean>

    suspend fun selectEnvironment(
        environment: AcousticEnvironment
    )

    suspend fun applyFeedback(
        feedback: AudioFeedback
    ): AdaptiveAudioProfile

    suspend fun setPreferredEnhancementStrength(
        value: Float
    ): AdaptiveAudioProfile

    suspend fun setRuntimeControlEnabled(
        enabled: Boolean
    )

    suspend fun resetActiveProfile(): AdaptiveAudioProfile
}
