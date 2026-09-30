package com.k410sh4.budsia.core.ai.adaptation

import kotlinx.coroutines.flow.Flow

interface AdaptiveProfileRepository {
    fun observeProfiles(): Flow<List<AdaptiveAudioProfile>>
    fun observeActive(): Flow<AdaptiveAudioProfile?>

    suspend fun ensureDefaults()
    suspend fun setActive(id: String): Result<Unit>

    suspend fun applyFeedback(
        profileId: String,
        feedback: AdaptiveFeedback,
        modelId: String
    ): Result<AdaptiveAudioProfile>
}
