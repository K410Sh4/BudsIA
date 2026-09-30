package com.k410sh4.budsia.core.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPerformanceGovernorTest {

    @Test
    fun severeThermalForcesDspFallback() {
        val governor = AiPerformanceGovernor()

        val decision = governor.decide(
            DevicePerformanceSnapshot(
                thermalLevel = ThermalLevel.SEVERE
            )
        )

        assertEquals(AiPerformanceTier.DSP_ONLY, decision.tier)
        assertFalse(decision.allowAi)
        assertTrue(decision.forceFallback)
        assertTrue(decision.preferPowerEfficiency)
    }

    @Test
    fun nearSevereThermalForecastFallsBackBeforeStatusTurnsSevere() {
        val governor = AiPerformanceGovernor()

        val decision = governor.decide(
            DevicePerformanceSnapshot(
                thermalLevel = ThermalLevel.LIGHT,
                thermalHeadroomForecast10s = MetricValue(
                    0.99f,
                    MeasurementKind.ESTIMATED
                )
            )
        )

        assertEquals(AiPerformanceTier.DSP_ONLY, decision.tier)
        assertTrue(decision.forceFallback)
    }

    @Test
    fun batteryStopIsDisabledByDefault() {
        val governor = AiPerformanceGovernor()

        val decision = governor.decide(
            snapshot(
                battery = 5,
                charging = false
            )
        )

        assertTrue(decision.allowAi)
        assertFalse(decision.forceFallback)
    }

    @Test
    fun userEnabledLowBatteryLimitForcesFallback() {
        val governor = AiPerformanceGovernor(
            AiPerformancePolicy(
                stopAiBelowBatteryPercent = 15,
                ecoBelowBatteryPercent = 25
            )
        )

        val decision = governor.decide(
            snapshot(
                battery = 15,
                charging = false
            )
        )

        assertEquals(AiPerformanceTier.DSP_ONLY, decision.tier)
        assertFalse(decision.allowAi)
        assertTrue(decision.forceFallback)
    }

    @Test
    fun policyCanBeUpdatedWithoutRecreatingGovernor() {
        val governor = AiPerformanceGovernor()

        assertTrue(
            governor.decide(
                snapshot(
                    battery = 10,
                    charging = false
                )
            ).allowAi
        )

        governor.updatePolicy(
            AiPerformancePolicy(
                stopAiBelowBatteryPercent = 15,
                ecoBelowBatteryPercent = 25
            )
        )

        assertFalse(
            governor.decide(
                snapshot(
                    battery = 10,
                    charging = false
                )
            ).allowAi
        )
    }

    @Test
    fun moderateThermalRecommendsEcoAndPowerEfficiency() {
        val governor = AiPerformanceGovernor()

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
        assertTrue(decision.preferPowerEfficiency)
    }

    @Test
    fun lowCpuHeadroomKeepsPerformanceSchedulingPriority() {
        val governor = AiPerformanceGovernor()

        val decision = governor.decide(
            DevicePerformanceSnapshot(
                thermalLevel = ThermalLevel.NONE,
                cpuHeadroomPercent = MetricValue(
                    5f,
                    MeasurementKind.ESTIMATED
                ),
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

        assertEquals(AiPerformanceTier.BALANCED, decision.tier)
        assertFalse(decision.preferPowerEfficiency)
    }

    @Test
    fun powerSaveModePrefersEfficientScheduling() {
        val governor = AiPerformanceGovernor()

        val decision = governor.decide(
            DevicePerformanceSnapshot(
                thermalLevel = ThermalLevel.NONE,
                powerSaveMode = MetricValue(
                    true,
                    MeasurementKind.MEASURED
                )
            )
        )

        assertEquals(AiPerformanceTier.BALANCED, decision.tier)
        assertTrue(decision.preferPowerEfficiency)
    }

    @Test
    fun stableChargingDeviceRecommendsMaxQuality() {
        val governor = AiPerformanceGovernor()

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
        assertFalse(decision.preferPowerEfficiency)
    }

    @Test
    fun androidLowMemoryFlagHasPriority() {
        val governor = AiPerformanceGovernor()

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

    private fun snapshot(
        battery: Int,
        charging: Boolean
    ): DevicePerformanceSnapshot =
        DevicePerformanceSnapshot(
            thermalLevel = ThermalLevel.NONE,
            batteryPercent = MetricValue(
                battery,
                MeasurementKind.MEASURED
            ),
            isCharging = MetricValue(
                charging,
                MeasurementKind.MEASURED
            ),
            lowMemory = MetricValue(
                false,
                MeasurementKind.MEASURED
            )
        )
}
