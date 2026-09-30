package com.k410sh4.budsia.core.performance

enum class MeasurementKind {
    MEASURED,
    ESTIMATED,
    UNKNOWN
}

data class MetricValue<T>(
    val value: T?,
    val kind: MeasurementKind
)

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

data class DevicePerformanceSnapshot(
    val thermalLevel: ThermalLevel = ThermalLevel.UNKNOWN,
    val thermalHeadroomNow: MetricValue<Float> =
        MetricValue(null, MeasurementKind.UNKNOWN),
    val thermalHeadroomForecast10s: MetricValue<Float> =
        MetricValue(null, MeasurementKind.UNKNOWN),
    val cpuHeadroomPercent: MetricValue<Float> =
        MetricValue(null, MeasurementKind.UNKNOWN),
    val batteryPercent: MetricValue<Int> =
        MetricValue(null, MeasurementKind.UNKNOWN),
    val isCharging: MetricValue<Boolean> =
        MetricValue(null, MeasurementKind.UNKNOWN),
    val powerSaveMode: MetricValue<Boolean> =
        MetricValue(null, MeasurementKind.UNKNOWN),
    val availableMemoryBytes: MetricValue<Long> =
        MetricValue(null, MeasurementKind.UNKNOWN),
    val totalMemoryBytes: MetricValue<Long> =
        MetricValue(null, MeasurementKind.UNKNOWN),
    val lowMemory: MetricValue<Boolean> =
        MetricValue(null, MeasurementKind.UNKNOWN),
    val processCpuPercent: MetricValue<Double> =
        MetricValue(null, MeasurementKind.UNKNOWN),
    val sampledAtElapsedRealtimeMs: Long = 0L
)

enum class AiPerformanceTier {
    MAX_QUALITY,
    BALANCED,
    ECO,
    DSP_ONLY
}

data class AiPerformanceDecision(
    val tier: AiPerformanceTier,
    val allowAi: Boolean,
    val reason: String,
    val forceFallback: Boolean = false,
    val preferPowerEfficiency: Boolean = false
)
