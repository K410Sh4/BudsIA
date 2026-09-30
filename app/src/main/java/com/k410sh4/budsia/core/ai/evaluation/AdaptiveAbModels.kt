package com.k410sh4.budsia.core.ai.evaluation

import com.k410sh4.budsia.core.ai.adaptation.AcousticEnvironment

enum class AdaptiveAbVariant {
    FACTORY,
    CANDIDATE
}

enum class AdaptiveAbChoice {
    FACTORY,
    CANDIDATE,
    NO_DIFFERENCE
}

enum class AdaptiveAbPreferenceStatus {
    INSUFFICIENT_DATA,
    CANDIDATE_PREFERRED,
    FACTORY_PREFERRED,
    MIXED
}

data class AdaptiveAbStats(
    val environment: AcousticEnvironment,
    val factoryWins: Long = 0L,
    val candidateWins: Long = 0L,
    val noDifference: Long = 0L
) {
    init {
        require(factoryWins >= 0L)
        require(candidateWins >= 0L)
        require(noDifference >= 0L)
    }

    val totalComparisons: Long
        get() = factoryWins + candidateWins + noDifference

    val decisiveComparisons: Long
        get() = factoryWins + candidateWins

    val candidatePreferenceRate: Float?
        get() = if (decisiveComparisons > 0L) {
            candidateWins.toFloat() /
                decisiveComparisons.toFloat()
        } else {
            null
        }

    companion object {
        fun empty(
            environment: AcousticEnvironment
        ): AdaptiveAbStats = AdaptiveAbStats(
            environment = environment
        )
    }
}

data class AdaptiveAbAssessment(
    val status: AdaptiveAbPreferenceStatus,
    val totalComparisons: Long,
    val decisiveComparisons: Long,
    val candidatePreferenceRate: Float?,
    val minimumComparisonsRequired: Int,
    val minimumDecisiveRequired: Int
)
