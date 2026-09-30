package com.k410sh4.budsia.core.ai.evaluation

/**
 * Interprets explicit local A/B choices.
 *
 * This is a user-preference summary, not an objective audio-quality score and
 * never promotes a candidate automatically.
 */
class AdaptiveAbEvaluator(
    private val minimumComparisons: Int = 8,
    private val minimumDecisive: Int = 5,
    private val preferenceThreshold: Float = 0.65f
) {
    init {
        require(minimumComparisons > 0)
        require(minimumDecisive > 0)
        require(preferenceThreshold in 0.5f..1f)
    }

    fun assess(
        stats: AdaptiveAbStats
    ): AdaptiveAbAssessment {
        val rate = stats.candidatePreferenceRate

        val status = when {
            stats.totalComparisons < minimumComparisons ||
                stats.decisiveComparisons < minimumDecisive ||
                rate == null ->
                AdaptiveAbPreferenceStatus.INSUFFICIENT_DATA

            rate >= preferenceThreshold ->
                AdaptiveAbPreferenceStatus.CANDIDATE_PREFERRED

            rate <= (1f - preferenceThreshold) ->
                AdaptiveAbPreferenceStatus.FACTORY_PREFERRED

            else ->
                AdaptiveAbPreferenceStatus.MIXED
        }

        return AdaptiveAbAssessment(
            status = status,
            totalComparisons = stats.totalComparisons,
            decisiveComparisons = stats.decisiveComparisons,
            candidatePreferenceRate = rate,
            minimumComparisonsRequired = minimumComparisons,
            minimumDecisiveRequired = minimumDecisive
        )
    }
}
