package com.k410sh4.budsia.core.performance

import kotlinx.coroutines.flow.StateFlow

enum class ThermalLevel {
    NONE,
    LIGHT,
    MODERATE,
    SEVERE,
    CRITICAL,
    EMERGENCY,
    SHUTDOWN,
    UNKNOWN
}

data class AiPerformanceSnapshot(
    val thermalLevel: ThermalLevel = ThermalLevel.UNKNOWN,
    val thermalHeadroom: Float? = null,
    val batteryPercent: Int? = null,
    val isCharging: Boolean? = null,
    val processPssBytes: Long? = null,
    val availableSystemMemoryBytes: Long? = null,
    val lowMemory: Boolean? = null,
    val sampledAtElapsedRealtimeMs: Long = 0L
)

interface AiPerformanceMonitor {
    val snapshot: StateFlow<AiPerformanceSnapshot>
}
