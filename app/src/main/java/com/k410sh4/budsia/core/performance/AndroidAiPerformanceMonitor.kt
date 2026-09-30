package com.k410sh4.budsia.core.performance

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
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

    private val _snapshot = MutableStateFlow(DevicePerformanceSnapshot())
    override val snapshot: StateFlow<DevicePerformanceSnapshot> =
        _snapshot.asStateFlow()

    private var previousWallMs: Long? = null
    private var previousCpuMs: Long? = null

    init {
        applicationScope.launch {
            while (isActive) {
                _snapshot.value = sample()
                delay(SAMPLE_INTERVAL_MS)
            }
        }
    }

    private fun sample(): DevicePerformanceSnapshot {
        val nowMs = SystemClock.elapsedRealtime()
        val cpuMs = Process.getElapsedCpuTime()

        val cpuMetric = estimateProcessCpuPercent(
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
            batteryPercent = MetricValue(
                value = batteryPercent,
                kind = if (batteryPercent != null) {
                    MeasurementKind.MEASURED
                } else {
                    MeasurementKind.UNKNOWN
                }
            ),
            isCharging = MetricValue(
                value = batteryManager.isCharging,
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
            processCpuPercent = cpuMetric,
            sampledAtElapsedRealtimeMs = nowMs
        )
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
        private const val SAMPLE_INTERVAL_MS = 2_000L
    }
}
