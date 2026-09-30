package com.k410sh4.budsia.core.ai.adaptation

import kotlinx.coroutines.flow.StateFlow

interface AdaptiveProfileRepository {
    val activeProfile: StateFlow<AdaptiveAudioProfile>

    suspend fun selectEnvironment(
        environment: AcousticEnvironment
    )

    suspend fun applyFeedback(
        feedback: AudioFeedback
    ): AdaptiveAudioProfile

    suspend fun setPreferredEnhancementStrength(
        value: Float
    ): AdaptiveAudioProfile

    suspend fun resetActiveProfile(): AdaptiveAudioProfile
}
