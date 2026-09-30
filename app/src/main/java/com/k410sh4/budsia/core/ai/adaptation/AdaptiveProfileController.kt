package com.k410sh4.budsia.core.ai.adaptation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AdaptiveProfileController(
    private val repository: AdaptiveProfileRepository,
    applicationScope: CoroutineScope
) {
    val profiles: StateFlow<List<AdaptiveAudioProfile>> =
        repository.observeProfiles().stateIn(
            scope = applicationScope,
            started = SharingStarted.Eagerly,
            initialValue = AdaptiveProfileDefaults.presets
        )

    val activeProfile: StateFlow<AdaptiveAudioProfile?> =
        repository.observeActive().stateIn(
            scope = applicationScope,
            started = SharingStarted.Eagerly,
            initialValue = AdaptiveProfileDefaults.GENERAL
        )

    init {
        applicationScope.launch {
            repository.ensureDefaults()
        }
    }

    suspend fun activate(profileId: String): Result<Unit> =
        repository.setActive(profileId)

    suspend fun teach(
        feedback: AdaptiveFeedback
    ): Result<AdaptiveAudioProfile> {
        val active = activeProfile.value
            ?: return Result.failure(
                IllegalStateException("No active adaptive profile.")
            )

        return repository.applyFeedback(
            profileId = active.id,
            feedback = feedback
        )
    }
}
