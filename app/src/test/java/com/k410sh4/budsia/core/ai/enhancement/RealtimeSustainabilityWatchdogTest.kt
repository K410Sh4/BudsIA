package com.k410sh4.budsia.core.ai.enhancement

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeSustainabilityWatchdogTest {

    @Test
    fun startupSpikeDoesNotTriggerFallbackDuringWarmup() {
        val watchdog = RealtimeSustainabilityWatchdog(
            warmupChunks = 3,
            requiredConsecutiveBreaches = 2
        )

        assertFalse(
            watchdog.shouldFallback(
                chunksProcessed = 1,
                movingRealtimeFactor = 1.60,
                cumulativeRealtimeFactor = 1.30
            )
        )
        assertFalse(
            watchdog.shouldFallback(
                chunksProcessed = 2,
                movingRealtimeFactor = 1.40,
                cumulativeRealtimeFactor = 1.20
            )
        )
    }

    @Test
    fun highMovingRtfDoesNotFallbackWhenLongRunAverageHasHeadroom() {
        val watchdog = RealtimeSustainabilityWatchdog(
            warmupChunks = 0,
            requiredConsecutiveBreaches = 2
        )

        repeat(10) {
            assertFalse(
                watchdog.shouldFallback(
                    chunksProcessed = it.toLong() + 1L,
                    movingRealtimeFactor = 1.25,
                    cumulativeRealtimeFactor = 0.82
                )
            )
        }
    }

    @Test
    fun persistentMovingAndCumulativeOverloadTriggersFallback() {
        val watchdog = RealtimeSustainabilityWatchdog(
            warmupChunks = 0,
            requiredConsecutiveBreaches = 3
        )

        assertFalse(
            watchdog.shouldFallback(1, 1.20, 1.05)
        )
        assertFalse(
            watchdog.shouldFallback(2, 1.21, 1.06)
        )
        assertTrue(
            watchdog.shouldFallback(3, 1.22, 1.07)
        )
    }

    @Test
    fun oneRecoveredChunkResetsPersistenceCounter() {
        val watchdog = RealtimeSustainabilityWatchdog(
            warmupChunks = 0,
            requiredConsecutiveBreaches = 3
        )

        assertFalse(watchdog.shouldFallback(1, 1.20, 1.05))
        assertFalse(watchdog.shouldFallback(2, 1.21, 1.06))
        assertFalse(watchdog.shouldFallback(3, 0.95, 0.97))
        assertFalse(watchdog.shouldFallback(4, 1.22, 1.05))
        assertFalse(watchdog.shouldFallback(5, 1.23, 1.06))
        assertTrue(watchdog.shouldFallback(6, 1.24, 1.07))
    }
}
