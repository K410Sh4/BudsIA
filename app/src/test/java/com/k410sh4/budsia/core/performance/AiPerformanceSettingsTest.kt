package com.k410sh4.budsia.core.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiPerformanceSettingsTest {

    @Test
    fun lowBatteryFallbackIsOptInByDefault() {
        val settings = AiPerformanceSettings()

        assertEquals(false, settings.lowBatteryAutoFallbackEnabled)
        assertNull(
            settings.toPolicy().stopAiBelowBatteryPercent
        )
    }

    @Test
    fun enabledBatteryFallbackBecomesGovernorPolicy() {
        val settings = AiPerformanceSettings(
            lowBatteryAutoFallbackEnabled = true,
            stopAiBelowBatteryPercent = 18,
            ecoBelowBatteryPercent = 25
        )

        assertEquals(
            18,
            settings.toPolicy().stopAiBelowBatteryPercent
        )
    }
}
