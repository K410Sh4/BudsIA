package com.k410sh4.budsia.core.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPerformancePolicyTest {

    private val policy = AiPerformancePolicy()

    @Test
    fun healthyDeviceUsesMaxProfile() {
        val decision = policy.decide(
            DeviceHealthSnapshot(
                thermalSeverity = ThermalSeverity.NONE,
                thermalHeadroomForecast10s = 0.3f,
                batteryPercent = 80,
                charging = false,
                lowMemory = false
            )
        )

        assertEquals(AiPerformanceLevel.MAX, decision.level)
        assertFalse(decision.preferPowerEfficiency)
    }

    @Test
    fun moderateThermalsUseEcoWithoutDroppingAiImmediately() {
        val decision = policy.decide(
            DeviceHealthSnapshot(
                thermalSeverity = ThermalSeverity.MODERATE,
                batteryPercent = 70,
                charging = false
            )
        )

        assertEquals(AiPerformanceLevel.ECO, decision.level)
        assertTrue(decision.preferPowerEfficiency)
    }

    @Test
    fun severeThermalsForceDeterministicDspFallback() {
        val decision = policy.decide(
            DeviceHealthSnapshot(
                thermalSeverity = ThermalSeverity.SEVERE,
                batteryPercent = 90,
                charging = true
            )
        )

        assertEquals(
            AiPerformanceLevel.DSP_ONLY,
            decision.level
        )
        assertTrue(decision.preferPowerEfficiency)
    }

    @Test
    fun lowBatteryUsesEcoWhileChargingDoesNot() {
        val lowBattery = policy.decide(
            DeviceHealthSnapshot(
                thermalSeverity = ThermalSeverity.NONE,
                batteryPercent = 8,
                charging = false
            )
        )
        val charging = policy.decide(
            DeviceHealthSnapshot(
                thermalSeverity = ThermalSeverity.NONE,
                batteryPercent = 8,
                charging = true
            )
        )

        assertEquals(AiPerformanceLevel.ECO, lowBattery.level)
        assertEquals(AiPerformanceLevel.MAX, charging.level)
    }
}
