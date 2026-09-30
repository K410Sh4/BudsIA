package com.k410sh4.budsia.core.performance

import kotlinx.coroutines.flow.StateFlow

enum class ThermalSeverity {
    NONE,
    LIGHT,
    MODERATE,
    SEVERE,
    CRITICAL,
    EMERGENCY,
    SHUTDOWN,
    UNKNOWN
}

data class DeviceHealthSnapshot(
    val thermalSeverity: ThermalSeverity = ThermalSeverity.UNKNOWN,
    val thermalHeadroomNow: Float? = null,
    val thermalHeadroomForecast10s: Float? = null,
    val cpuHeadroomPercent: Float? = null,
    val batteryPercent: Int? = null,
    val charging: Boolean? = null,
    val powerSaveMode: Boolean = false,
    val availableMemoryBytes: Long? = null,
    val totalMemoryBytes: Long? = null,
    val lowMemory: Boolean = false,
    val sampledAtElapsedRealtimeNanos: Long = 0L
) {
    val availableMemoryRatio: Float?
        get() {
            val available = availableMemoryBytes ?: return null
            val total = totalMemoryBytes ?: return null
            if (total <= 0L) return null
            return (available.toDouble() / total.toDouble())
                .coerceIn(0.0, 1.0)
                .toFloat()
        }
}

interface DeviceHealthMonitor {
    val health: StateFlow<DeviceHealthSnapshot>
}
