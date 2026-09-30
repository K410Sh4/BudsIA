package com.k410sh4.budsia.core.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPerformanceGovernorTest {

    private val governor = AiPerformanceGovernor(
        AiPerformancePolicy(
            stopAiBelowBatteryPercent = 15,
            ecoBelowBatteryPercent = 25
        )
    )

    @Test
    fun severeThermalForcesDspFallback() {
        val decision = governor.decide(
            DevicePerformanceSnapshot(
                thermalLevel = ThermalLevel.SEVERE
            )
        )

        assertEquals(AiPerformanceTier.DSP_ONLY, decision.tier)
        assertFalse(decision.allowAi)
        assertTrue(decision.forceFallback)
    }

    @Test
    fun lowBatteryWithoutChargingForcesDspFallback() {
        val decision = governor.decide(
            DevicePerformanceSnapshot(
                thermalLevel = ThermalLevel.NONE,
                batteryPercent = MetricValue(
                    15,
                    MeasurementKind.MEASURED
                ),
                isCharging = MetricValue(
                    false,
                    MeasurementKind.MEASURED
                )
            )
        )

        assertEquals(AiPerformanceTier.DSP_ONLY, decision.tier)
        assertFalse(decision.allowAi)
    }

    @Test
    fun moderateThermalAllowsAiInEcoTier() {
        val decision = governor.decide(
            DevicePerformanceSnapshot(
                thermalLevel = ThermalLevel.MODERATE,
                batteryPercent = MetricValue(
                    80,
                    MeasurementKind.MEASURED
                ),
                isCharging = MetricValue(
                    false,
                    MeasurementKind.MEASURED
                )
            )
        )

        assertEquals(AiPerformanceTier.ECO, decision.tier)
        assertTrue(decision.allowAi)
        assertFalse(decision.forceFallback)
    }

    @Test
    fun stableChargingDeviceAllowsMaxQualityTier() {
        val decision = governor.decide(
            DevicePerformanceSnapshot(
                thermalLevel = ThermalLevel.NONE,
                batteryPercent = MetricValue(
                    80,
                    MeasurementKind.MEASURED
                ),
                isCharging = MetricValue(
                    true,
                    MeasurementKind.MEASURED
                ),
                lowMemory = MetricValue(
                    false,
                    MeasurementKind.MEASURED
                )
            )
        )

        assertEquals(
            AiPerformanceTier.MAX_QUALITY,
            decision.tier
        )
        assertTrue(decision.allowAi)
    }

    @Test
    fun androidLowMemoryFlagHasPriority() {
        val decision = governor.decide(
            DevicePerformanceSnapshot(
                thermalLevel = ThermalLevel.NONE,
                batteryPercent = MetricValue(
                    100,
                    MeasurementKind.MEASURED
                ),
                isCharging = MetricValue(
                    true,
                    MeasurementKind.MEASURED
                ),
                lowMemory = MetricValue(
                    true,
                    MeasurementKind.MEASURED
                )
            )
        )

        assertEquals(AiPerformanceTier.DSP_ONLY, decision.tier)
        assertTrue(decision.forceFallback)
    }
}
