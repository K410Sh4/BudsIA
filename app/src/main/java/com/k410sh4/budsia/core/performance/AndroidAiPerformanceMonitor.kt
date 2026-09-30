package com.k410sh4.budsia.core.performance

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AndroidAiPerformanceMonitor(
    context: Context,
    applicationScope: CoroutineScope
) : AiPerformanceMonitor {

    private val appContext = context.applicationContext
    private val powerManager =
        appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val batteryManager =
        appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val activityManager =
        appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    private val _snapshot = MutableStateFlow(
        sample(
            thermalLevel = mapThermal(
                powerManager.currentThermalStatus
            )
        )
    )
    override val snapshot: StateFlow<AiPerformanceSnapshot> =
        _snapshot.asStateFlow()

    private val thermalExecutor = Executor { command ->
        applicationScope.launch {
            command.run()
        }
    }

    private val thermalListener =
        PowerManager.OnThermalStatusChangedListener { status ->
            _snapshot.value = sample(
                thermalLevel = mapThermal(status)
            )
        }

    init {
        powerManager.addThermalStatusListener(
            thermalExecutor,
            thermalListener
        )

        applicationScope.launch {
            while (isActive) {
                delay(SAMPLE_INTERVAL_MS)
                _snapshot.value = sample(
                    thermalLevel = mapThermal(
                        powerManager.currentThermalStatus
                    )
                )
            }
        }
    }

    private fun sample(
        thermalLevel: ThermalLevel
    ): AiPerformanceSnapshot {
        val memory = ActivityManager.MemoryInfo().also {
            activityManager.getMemoryInfo(it)
        }

        val batteryPercent = batteryManager
            .getIntProperty(
                BatteryManager.BATTERY_PROPERTY_CAPACITY
            )
            .takeIf { it in 0..100 }

        val thermalHeadroom = runCatching {
            powerManager.getThermalHeadroom(
                THERMAL_HEADROOM_FORECAST_SECONDS
            )
        }.getOrNull()
            ?.takeUnless { it.isNaN() || it.isInfinite() }

        val processPssBytes = runCatching {
            Debug.getPss()
                .toLong()
                .times(1024L)
        }.getOrNull()
            ?.takeIf { it >= 0L }

        return AiPerformanceSnapshot(
            thermalLevel = thermalLevel,
            thermalHeadroom = thermalHeadroom,
            batteryPercent = batteryPercent,
            isCharging = runCatching {
                batteryManager.isCharging
            }.getOrNull(),
            processPssBytes = processPssBytes,
            availableSystemMemoryBytes = memory.availMem,
            lowMemory = memory.lowMemory,
            sampledAtElapsedRealtimeMs =
                SystemClock.elapsedRealtime()
        )
    }

    private fun mapThermal(status: Int): ThermalLevel = when (status) {
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
        private const val SAMPLE_INTERVAL_MS = 5_000L
        private const val THERMAL_HEADROOM_FORECAST_SECONDS = 10
    }
}
