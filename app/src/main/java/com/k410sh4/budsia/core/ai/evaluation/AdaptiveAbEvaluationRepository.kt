package com.k410sh4.budsia.core.ai.evaluation

import com.k410sh4.budsia.core.ai.adaptation.AcousticEnvironment
import kotlinx.coroutines.flow.StateFlow

interface AdaptiveAbEvaluationRepository {
    val stats: StateFlow<Map<AcousticEnvironment, AdaptiveAbStats>>

    suspend fun record(
        environment: AcousticEnvironment,
        choice: AdaptiveAbChoice
    ): AdaptiveAbStats

    suspend fun reset(
        environment: AcousticEnvironment
    ): AdaptiveAbStats
}
