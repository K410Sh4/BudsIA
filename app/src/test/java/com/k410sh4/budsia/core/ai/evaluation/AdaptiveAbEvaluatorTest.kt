package com.k410sh4.budsia.core.ai.evaluation

import com.k410sh4.budsia.core.ai.adaptation.AcousticEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdaptiveAbEvaluatorTest {

    private val evaluator = AdaptiveAbEvaluator(
        minimumComparisons = 8,
        minimumDecisive = 5,
        preferenceThreshold = 0.65f
    )

    @Test
    fun insufficientDataNeverProducesPreferenceConclusion() {
        val assessment = evaluator.assess(
            AdaptiveAbStats(
                environment = AcousticEnvironment.GENERAL,
                candidateWins = 4,
                factoryWins = 1
            )
        )

        assertEquals(
            AdaptiveAbPreferenceStatus.INSUFFICIENT_DATA,
            assessment.status
        )
    }

    @Test
    fun candidatePreferenceRequiresEnoughExplicitEvidence() {
        val assessment = evaluator.assess(
            AdaptiveAbStats(
                environment = AcousticEnvironment.STREET,
                candidateWins = 7,
                factoryWins = 2,
                noDifference = 1
            )
        )

        assertEquals(
            AdaptiveAbPreferenceStatus.CANDIDATE_PREFERRED,
            assessment.status
        )
        assertEquals(
            7f / 9f,
            assessment.candidatePreferenceRate,
            0.000001f
        )
    }

    @Test
    fun factoryPreferenceIsSymmetric() {
        val assessment = evaluator.assess(
            AdaptiveAbStats(
                environment = AcousticEnvironment.HOME,
                candidateWins = 2,
                factoryWins = 7,
                noDifference = 1
            )
        )

        assertEquals(
            AdaptiveAbPreferenceStatus.FACTORY_PREFERRED,
            assessment.status
        )
    }

    @Test
    fun mixedPreferenceRemainsMixed() {
        val assessment = evaluator.assess(
            AdaptiveAbStats(
                environment = AcousticEnvironment.WORK,
                candidateWins = 5,
                factoryWins = 4
            )
        )

        assertEquals(
            AdaptiveAbPreferenceStatus.MIXED,
            assessment.status
        )
    }

    @Test
    fun noDifferenceCountsTowardTotalButNotDecisiveRate() {
        val stats = AdaptiveAbStats(
            environment = AcousticEnvironment.CAR,
            candidateWins = 3,
            factoryWins = 2,
            noDifference = 8
        )

        assertEquals(13L, stats.totalComparisons)
        assertEquals(5L, stats.decisiveComparisons)
        assertEquals(
            0.6f,
            stats.candidatePreferenceRate,
            0.000001f
        )

        assertEquals(
            AdaptiveAbPreferenceStatus.MIXED,
            evaluator.assess(stats).status
        )
    }

    @Test
    fun onlyNoDifferenceHasNoCandidateRate() {
        val stats = AdaptiveAbStats(
            environment = AcousticEnvironment.GENERAL,
            noDifference = 12
        )

        assertNull(stats.candidatePreferenceRate)
        assertEquals(
            AdaptiveAbPreferenceStatus.INSUFFICIENT_DATA,
            evaluator.assess(stats).status
        )
    }
}
