package com.k410sh4.budsia.core.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformancePolicyTest {

    private val policy = PerformancePolicy()

    @Test
    fun severeThermalStateForcesAiToDsp() {
        val decision = policy.evaluate(
            snapshot = AiPerformanceSnapshot(
                thermalLevel = ThermalLevel.SEVERE
            ),
            aiRunning = true
        )

        assertEquals(
            PerformanceAction.FORCE_DSP,
            decision.action
        )
        assertTrue(decision.reason.orEmpty().contains("SEVERE"))
    }

    @Test
    fun moderateThermalStateWarnsButDoesNotForceFallback() {
        val decision = policy.evaluate(
            snapshot = AiPerformanceSnapshot(
                thermalLevel = ThermalLevel.MODERATE
            ),
            aiRunning = true
        )

        assertEquals(
            PerformanceAction.WARN,
            decision.action
        )
    }

    @Test
    fun normalThermalStateDoesNothing() {
        val decision = policy.evaluate(
            snapshot = AiPerformanceSnapshot(
                thermalLevel = ThermalLevel.NONE
            ),
            aiRunning = true
        )

        assertEquals(
            PerformanceAction.NONE,
            decision.action
        )
    }

    @Test
    fun severeStateDoesNotPretendAiFallbackWhenAiIsAlreadyOff() {
        val decision = policy.evaluate(
            snapshot = AiPerformanceSnapshot(
                thermalLevel = ThermalLevel.SEVERE
            ),
            aiRunning = false
        )

        assertEquals(
            PerformanceAction.NONE,
            decision.action
        )
    }
}
