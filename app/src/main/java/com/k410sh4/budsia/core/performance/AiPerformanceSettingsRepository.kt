package com.k410sh4.budsia.core.performance

import kotlinx.coroutines.flow.StateFlow

interface AiPerformanceSettingsRepository {
    val settings: StateFlow<AiPerformanceSettings>

    suspend fun setLowBatteryAutoFallbackEnabled(
        enabled: Boolean
    )

    suspend fun setStopAiBelowBatteryPercent(
        percent: Int
    )
}
