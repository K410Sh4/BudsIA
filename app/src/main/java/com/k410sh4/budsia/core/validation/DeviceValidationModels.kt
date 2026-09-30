package com.k410sh4.budsia.core.validation

import com.k410sh4.budsia.core.ai.enhancement.NeuralPipelineState
import com.k410sh4.budsia.core.audio.realtime.RealtimeEngineState
import com.k410sh4.budsia.core.audio.realtime.RealtimeProcessingMode
import com.k410sh4.budsia.core.performance.AiPerformanceTier
import com.k410sh4.budsia.core.performance.ThermalLevel

enum class ValidationStatus {
    PASS,
    WARN,
    FAIL,
    UNKNOWN
}

data class ValidationCheck(
    val id: String,
    val title: String,
    val status: ValidationStatus,
    val detail: String
)

data class DeviceValidationSample(
    val elapsedMs: Long,
    val engineState: RealtimeEngineState,
    val processingMode: RealtimeProcessingMode,
    val inputSampleRateHz: Int,
    val outputSampleRateHz: Int,
    val inputFrames: Long,
    val outputFrames: Long,
    val inputDroppedSamples: Long,
    val outputUnderrunSamples: Long,
    val inputXruns: Long?,
    val outputXruns: Long?,
    val monitoringEnabled: Boolean,
    val neuralState: NeuralPipelineState,
    val neuralModelId: String?,
    val neuralRequiredSampleRateHz: Int?,
    val neuralRealtimeFactor: Double?,
    val thermalLevel: ThermalLevel,
    val thermalHeadroomForecast10s: Float?,
    val cpuHeadroomPercent: Float?,
    val batteryPercent: Int?,
    val powerSaveMode: Boolean?,
    val processCpuPercent: Double?,
    val performanceTier: AiPerformanceTier?,
    val performanceForceFallback: Boolean,
    val adaptiveControlActive: Boolean,
    val adaptiveStrength: Float
)

data class DeviceValidationReport(
    val durationMs: Long,
    val requestedMode: RealtimeProcessingMode,
    val inputSampleRateHz: Int?,
    val neuralModelId: String?,
    val checks: List<ValidationCheck>,
    val inputDroppedSamplesDelta: Long?,
    val outputUnderrunSamplesDelta: Long?,
    val inputXrunsDelta: Long?,
    val outputXrunsDelta: Long?,
    val maxRealtimeFactor: Double?,
    val peakEstimatedProcessCpuPercent: Double?,
    val maximumThermalHeadroomForecast10s: Float?,
    val minimumCpuHeadroomPercent: Float?,
    val powerSaveObserved: Boolean,
    val performanceForceFallbackObserved: Boolean,
    val adaptiveControlObserved: Boolean,
    val adaptiveStrengthRange: ClosedFloatingPointRange<Float>?,
    val startBatteryPercent: Int?,
    val endBatteryPercent: Int?,
    val maximumThermalLevel: ThermalLevel
) {
    val overallStatus: ValidationStatus
        get() = when {
            checks.any { it.status == ValidationStatus.FAIL } ->
                ValidationStatus.FAIL
            checks.any { it.status == ValidationStatus.WARN } ->
                ValidationStatus.WARN
            checks.any { it.status == ValidationStatus.PASS } ->
                ValidationStatus.PASS
            else -> ValidationStatus.UNKNOWN
        }
}

data class DeviceValidationUiState(
    val running: Boolean = false,
    val progress: Float = 0f,
    val elapsedSeconds: Int = 0,
    val report: DeviceValidationReport? = null,
    val message: String? = null
)
