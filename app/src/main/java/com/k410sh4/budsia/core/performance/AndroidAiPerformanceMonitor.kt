package com.k410sh4.budsia.core.performance

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.os.health.SystemHealthManager
import com.k410sh4.budsia.di.ApplicationScope
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AndroidAiPerformanceMonitor @Inject constructor(
    context: Context,
    @ApplicationScope applicationScope: CoroutineScope
) : AiPerformanceMonitor {

    private val powerManager =
        context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val batteryManager =
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    private val systemHealthManager: SystemHealthManager? =
        if (Build.VERSION.SDK_INT >= 36) {
            context.getSystemService(SystemHealthManager::class.java)
        } else {
            null
        }

    private val sampleIntervalMs: Long =
        resolveSampleIntervalMs()

    private val _snapshot = MutableStateFlow(DevicePerformanceSnapshot())
    override val snapshot: StateFlow<DevicePerformanceSnapshot> =
        _snapshot.asStateFlow()

    private var previousWallMs: Long? = null
    private var previousCpuMs: Long? = null

    init {
        applicationScope.launch {
            while (isActive) {
                _snapshot.value = sample()
                delay(sampleIntervalMs)
            }
        }
    }

    private fun sample(): DevicePerformanceSnapshot {
        val nowMs = SystemClock.elapsedRealtime()
        val cpuMs = Process.getElapsedCpuTime()

        val processCpuMetric = estimateProcessCpuPercent(
            nowMs = nowMs,
            cpuMs = cpuMs
        )

        previousWallMs = nowMs
        previousCpuMs = cpuMs

        val memoryInfo = ActivityManager.MemoryInfo().also {
            activityManager.getMemoryInfo(it)
        }

        val batteryPercent = batteryManager
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .takeIf { it in 0..100 }

        return DevicePerformanceSnapshot(
            thermalLevel = mapThermalLevel(
                powerManager.currentThermalStatus
            ),
            thermalHeadroomNow =
                thermalHeadroom(forecastSeconds = 0),
            thermalHeadroomForecast10s =
                thermalHeadroom(forecastSeconds = 10),
            cpuHeadroomPercent = cpuHeadroom(),
            batteryPercent = metric(
                batteryPercent,
                MeasurementKind.MEASURED
            ),
            isCharging = MetricValue(
                value = batteryManager.isCharging,
                kind = MeasurementKind.MEASURED
            ),
            powerSaveMode = MetricValue(
                value = powerManager.isPowerSaveMode,
                kind = MeasurementKind.MEASURED
            ),
            availableMemoryBytes = MetricValue(
                value = memoryInfo.availMem,
                kind = MeasurementKind.MEASURED
            ),
            totalMemoryBytes = MetricValue(
                value = memoryInfo.totalMem,
                kind = MeasurementKind.MEASURED
            ),
            lowMemory = MetricValue(
                value = memoryInfo.lowMemory,
                kind = MeasurementKind.MEASURED
            ),
            processCpuPercent = processCpuMetric,
            sampledAtElapsedRealtimeMs = nowMs
        )
    }

    private fun thermalHeadroom(
        forecastSeconds: Int
    ): MetricValue<Float> {
        val value = runCatching {
            powerManager
                .getThermalHeadroom(forecastSeconds)
                .takeIf {
                    it.isFinite() && it >= 0f
                }
        }.getOrNull()

        return if (value != null) {
            MetricValue(
                value = value,
                kind = MeasurementKind.ESTIMATED
            )
        } else {
            MetricValue(
                value = null,
                kind = MeasurementKind.UNKNOWN
            )
        }
    }

    private fun cpuHeadroom(): MetricValue<Float> {
        if (Build.VERSION.SDK_INT < 36) {
            return MetricValue(
                value = null,
                kind = MeasurementKind.UNKNOWN
            )
        }

        val manager = systemHealthManager
            ?: return MetricValue(
                value = null,
                kind = MeasurementKind.UNKNOWN
            )

        val value = runCatching {
            manager.getCpuHeadroom(null)
                .takeIf {
                    it.isFinite() && it in 0f..100f
                }
        }.getOrNull()

        return if (value != null) {
            MetricValue(
                value = value,
                // Android defines this API as an estimate of available
                // CPU capacity headroom, not a direct utilization counter.
                kind = MeasurementKind.ESTIMATED
            )
        } else {
            MetricValue(
                value = null,
                kind = MeasurementKind.UNKNOWN
            )
        }
    }

    private fun estimateProcessCpuPercent(
        nowMs: Long,
        cpuMs: Long
    ): MetricValue<Double> {
        val previousWall = previousWallMs
        val previousCpu = previousCpuMs

        if (
            previousWall == null ||
            previousCpu == null ||
            nowMs <= previousWall ||
            cpuMs < previousCpu
        ) {
            return MetricValue(
                value = null,
                kind = MeasurementKind.UNKNOWN
            )
        }

        val wallDeltaMs = nowMs - previousWall
        val cpuDeltaMs = cpuMs - previousCpu
        val processors =
            Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        val normalized =
            cpuDeltaMs.toDouble() /
                wallDeltaMs.toDouble() /
                processors.toDouble() *
                100.0

        return MetricValue(
            value = normalized.coerceIn(0.0, 100.0),
            kind = MeasurementKind.ESTIMATED
        )
    }

    private fun <T> metric(
        value: T?,
        kindWhenPresent: MeasurementKind
    ): MetricValue<T> =
        if (value != null) {
            MetricValue(
                value = value,
                kind = kindWhenPresent
            )
        } else {
            MetricValue(
                value = null,
                kind = MeasurementKind.UNKNOWN
            )
        }

    private fun resolveSampleIntervalMs(): Long {
        if (Build.VERSION.SDK_INT < 36) {
            return BASE_SAMPLE_INTERVAL_MS
        }

        val manager = systemHealthManager
            ?: return BASE_SAMPLE_INTERVAL_MS

        val cpuMinimum = runCatching {
            manager.getCpuHeadroomMinIntervalMillis()
        }.getOrNull()
            ?: return BASE_SAMPLE_INTERVAL_MS

        return maxOf(
            BASE_SAMPLE_INTERVAL_MS,
            cpuMinimum.coerceAtLeast(0L)
        )
    }

    private fun mapThermalLevel(
        status: Int
    ): ThermalLevel = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> ThermalLevel.NONE
        PowerManager.THERMAL_STATUS_LIGHT -> ThermalLevel.LIGHT
        PowerManager.THERMAL_STATUS_MODERATE -> ThermalLevel.MODERATE
        PowerManager.THERMAL_STATUS_SEVERE -> ThermalLevel.SEVERE
        PowerManager.THERMAL_STATUS_CRITICAL -> ThermalLevel.CRITICAL
        PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalLevel.EMERGENCY
        PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalLevel.SHUTDOWN
        else -> ThermalLevel.UNKNOWN
    }

    companion object {
        // Thermal headroom should not be aggressively polled. Two seconds
        // keeps this outside latency-critical audio work and above Android's
        // documented no-benefit sub-second polling range.
        private const val BASE_SAMPLE_INTERVAL_MS = 2_000L
    }
}
