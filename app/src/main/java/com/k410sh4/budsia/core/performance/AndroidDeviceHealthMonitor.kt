package com.k410sh4.budsia.core.performance

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.os.health.SystemHealthManager
import com.k410sh4.budsia.di.ApplicationScope
import java.lang.Float.isFinite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AndroidDeviceHealthMonitor(
    private val context: Context,
    @ApplicationScope applicationScope: CoroutineScope
) : DeviceHealthMonitor {

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

    private val _health = MutableStateFlow(DeviceHealthSnapshot())
    override val health: StateFlow<DeviceHealthSnapshot> =
        _health.asStateFlow()

    init {
        applicationScope.launch {
            while (isActive) {
                _health.value = sample()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun sample(): DeviceHealthSnapshot {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val status = batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        )
        val charging = status?.let {
            it == BatteryManager.BATTERY_STATUS_CHARGING ||
                it == BatteryManager.BATTERY_STATUS_FULL
        }

        val batteryPercent = batteryManager
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .takeIf { it in 0..100 }

        return DeviceHealthSnapshot(
            thermalSeverity = thermalSeverity(
                powerManager.currentThermalStatus
            ),
            thermalHeadroomNow =
                safeThermalHeadroom(forecastSeconds = 0),
            thermalHeadroomForecast10s =
                safeThermalHeadroom(forecastSeconds = 10),
            cpuHeadroomPercent = safeCpuHeadroom(),
            batteryPercent = batteryPercent,
            charging = charging,
            powerSaveMode = powerManager.isPowerSaveMode,
            availableMemoryBytes = memoryInfo.availMem,
            totalMemoryBytes = memoryInfo.totalMem,
            lowMemory = memoryInfo.lowMemory,
            sampledAtElapsedRealtimeNanos =
                SystemClock.elapsedRealtimeNanos()
        )
    }

    private fun safeThermalHeadroom(
        forecastSeconds: Int
    ): Float? = runCatching {
        powerManager
            .getThermalHeadroom(forecastSeconds)
            .takeIf { isFinite(it) && it >= 0f }
    }.getOrNull()

    private fun safeCpuHeadroom(): Float? {
        if (Build.VERSION.SDK_INT < 36) return null

        val manager = systemHealthManager ?: return null
        return runCatching {
            manager.getCpuHeadroom(null)
                .takeIf {
                    isFinite(it) && it in 0f..100f
                }
        }.getOrNull()
    }

    private fun thermalSeverity(status: Int): ThermalSeverity =
        when (status) {
            PowerManager.THERMAL_STATUS_NONE ->
                ThermalSeverity.NONE
            PowerManager.THERMAL_STATUS_LIGHT ->
                ThermalSeverity.LIGHT
            PowerManager.THERMAL_STATUS_MODERATE ->
                ThermalSeverity.MODERATE
            PowerManager.THERMAL_STATUS_SEVERE ->
                ThermalSeverity.SEVERE
            PowerManager.THERMAL_STATUS_CRITICAL ->
                ThermalSeverity.CRITICAL
            PowerManager.THERMAL_STATUS_EMERGENCY ->
                ThermalSeverity.EMERGENCY
            PowerManager.THERMAL_STATUS_SHUTDOWN ->
                ThermalSeverity.SHUTDOWN
            else -> ThermalSeverity.UNKNOWN
        }

    companion object {
        // Android thermal headroom guidance says polling materially faster
        // than about once per second provides no benefit and can return NaN.
        private const val POLL_INTERVAL_MS = 1_000L
    }
}
