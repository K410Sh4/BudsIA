package com.k410sh4.budsia.core.validation

import com.k410sh4.budsia.core.ai.enhancement.NeuralPipelineState
import com.k410sh4.budsia.core.audio.realtime.RealtimeEngineState
import com.k410sh4.budsia.core.audio.realtime.RealtimeProcessingMode
import com.k410sh4.budsia.core.performance.AiPerformanceTier
import com.k410sh4.budsia.core.performance.ThermalLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceValidationEvaluatorTest {

    private val evaluator = DeviceValidationEvaluator()

    @Test
    fun stableDspSessionPassesCoreChecks() {
        val samples = listOf(
            sample(
                elapsedMs = 0,
                inputFrames = 0,
                outputFrames = 0
            ),
            sample(
                elapsedMs = 30_000,
                inputFrames = 1_440_000,
                outputFrames = 0
            )
        )

        val report = evaluator.evaluate(
            requestedMode = RealtimeProcessingMode.DSP,
            samples = samples
        )

        assertEquals(ValidationStatus.PASS, report.overallStatus)
        assertEquals(48_000, report.inputSampleRateHz)
        assertEquals(0L, report.inputDroppedSamplesDelta)
    }

    @Test
    fun healthyAiSessionPassesNeuralChecks() {
        val samples = listOf(
            sample(
                elapsedMs = 0,
                inputFrames = 0,
                processingMode = RealtimeProcessingMode.AI,
                neuralState = NeuralPipelineState.RUNNING,
                neuralRate = 48_000,
                rtf = 0.55
            ),
            sample(
                elapsedMs = 30_000,
                inputFrames = 1_440_000,
                processingMode = RealtimeProcessingMode.AI,
                neuralState = NeuralPipelineState.RUNNING,
                neuralRate = 48_000,
                rtf = 0.70
            )
        )

        val report = evaluator.evaluate(
            requestedMode = RealtimeProcessingMode.AI,
            samples = samples
        )

        assertEquals(ValidationStatus.PASS, report.overallStatus)
    }

    @Test
    fun aiSessionFailsWhenRealtimeFactorExceedsOne() {
        val samples = listOf(
            sample(
                elapsedMs = 0,
                inputFrames = 0,
                processingMode = RealtimeProcessingMode.AI,
                neuralState = NeuralPipelineState.RUNNING,
                neuralRate = 48_000,
                rtf = 0.70
            ),
            sample(
                elapsedMs = 30_000,
                inputFrames = 1_440_000,
                processingMode = RealtimeProcessingMode.AI,
                neuralState = NeuralPipelineState.RUNNING,
                neuralRate = 48_000,
                rtf = 1.05
            )
        )

        val report = evaluator.evaluate(
            requestedMode = RealtimeProcessingMode.AI,
            samples = samples
        )

        assertEquals(ValidationStatus.FAIL, report.overallStatus)
        assertTrue(
            report.checks.any {
                it.id == "neural_rtf" &&
                    it.status == ValidationStatus.FAIL
            }
        )
    }

    @Test
    fun severeThermalStateFailsValidation() {
        val samples = listOf(
            sample(
                elapsedMs = 0,
                inputFrames = 0
            ),
            sample(
                elapsedMs = 10_000,
                inputFrames = 480_000,
                thermal = ThermalLevel.SEVERE
            )
        )

        val report = evaluator.evaluate(
            requestedMode = RealtimeProcessingMode.DSP,
            samples = samples
        )

        assertEquals(ValidationStatus.FAIL, report.overallStatus)
        assertEquals(ThermalLevel.SEVERE, report.maximumThermalLevel)
    }

    @Test
    fun aiValidationFailsWhenGovernorRequestsFallback() {
        val samples = listOf(
            sample(
                elapsedMs = 0,
                inputFrames = 0,
                processingMode = RealtimeProcessingMode.AI,
                neuralState = NeuralPipelineState.RUNNING,
                neuralRate = 48_000,
                rtf = 0.6
            ),
            sample(
                elapsedMs = 30_000,
                inputFrames = 1_440_000,
                processingMode = RealtimeProcessingMode.AI,
                neuralState = NeuralPipelineState.RUNNING,
                neuralRate = 48_000,
                rtf = 0.7,
                performanceTier =
                    AiPerformanceTier.DSP_ONLY,
                performanceForceFallback = true
            )
        )

        val report = evaluator.evaluate(
            requestedMode = RealtimeProcessingMode.AI,
            samples = samples
        )

        assertEquals(ValidationStatus.FAIL, report.overallStatus)
        assertTrue(report.performanceForceFallbackObserved)
        assertTrue(
            report.checks.any {
                it.id == "performance_policy" &&
                    it.status == ValidationStatus.FAIL
            }
        )
    }

    @Test
    fun reportCapturesAdaptiveCandidateRange() {
        val report = evaluator.evaluate(
            requestedMode = RealtimeProcessingMode.AI,
            samples = listOf(
                sample(
                    elapsedMs = 0,
                    inputFrames = 0,
                    processingMode = RealtimeProcessingMode.AI,
                    neuralState = NeuralPipelineState.RUNNING,
                    neuralRate = 48_000,
                    rtf = 0.5,
                    adaptiveControlActive = true,
                    adaptiveStrength = 0.60f
                ),
                sample(
                    elapsedMs = 30_000,
                    inputFrames = 1_440_000,
                    processingMode = RealtimeProcessingMode.AI,
                    neuralState = NeuralPipelineState.RUNNING,
                    neuralRate = 48_000,
                    rtf = 0.6,
                    adaptiveControlActive = true,
                    adaptiveStrength = 0.80f
                )
            )
        )

        assertTrue(report.adaptiveControlObserved)
        assertEquals(
            0.60f,
            report.adaptiveStrengthRange?.start ?: -1f,
            0.0001f
        )
        assertEquals(
            0.80f,
            report.adaptiveStrengthRange?.endInclusive ?: -1f,
            0.0001f
        )
    }

    @Test
    fun outputUnderrunsAreUnknownWhenMonitorIsOff() {
        val report = evaluator.evaluate(
            requestedMode = RealtimeProcessingMode.DSP,
            samples = listOf(
                sample(elapsedMs = 0, inputFrames = 0),
                sample(
                    elapsedMs = 10_000,
                    inputFrames = 480_000,
                    outputUnderruns = 500
                )
            )
        )

        assertTrue(
            report.checks.any {
                it.id == "output_underruns" &&
                    it.status == ValidationStatus.UNKNOWN
            }
        )
    }

    private fun sample(
        elapsedMs: Long,
        inputFrames: Long,
        outputFrames: Long = 0L,
        processingMode: RealtimeProcessingMode =
            RealtimeProcessingMode.DSP,
        inputDrops: Long = 0L,
        outputUnderruns: Long = 0L,
        monitoring: Boolean = false,
        neuralState: NeuralPipelineState =
            NeuralPipelineState.IDLE,
        neuralRate: Int? = null,
        rtf: Double? = null,
        thermal: ThermalLevel = ThermalLevel.NONE,
        thermalForecast: Float? = 0.30f,
        cpuHeadroom: Float? = 60f,
        powerSave: Boolean? = false,
        performanceTier: AiPerformanceTier? =
            AiPerformanceTier.BALANCED,
        performanceForceFallback: Boolean = false,
        adaptiveControlActive: Boolean = false,
        adaptiveStrength: Float = 1f
    ): DeviceValidationSample =
        DeviceValidationSample(
            elapsedMs = elapsedMs,
            engineState = RealtimeEngineState.RUNNING,
            processingMode = processingMode,
            inputSampleRateHz = 48_000,
            outputSampleRateHz = 48_000,
            inputFrames = inputFrames,
            outputFrames = outputFrames,
            inputDroppedSamples = inputDrops,
            outputUnderrunSamples = outputUnderruns,
            inputXruns = 0,
            outputXruns = 0,
            monitoringEnabled = monitoring,
            neuralState = neuralState,
            neuralModelId = if (neuralRate != null) {
                "test-model"
            } else {
                null
            },
            neuralRequiredSampleRateHz = neuralRate,
            neuralRealtimeFactor = rtf,
            thermalLevel = thermal,
            thermalHeadroomForecast10s = thermalForecast,
            cpuHeadroomPercent = cpuHeadroom,
            batteryPercent = 80,
            powerSaveMode = powerSave,
            processCpuPercent = 20.0,
            performanceTier = performanceTier,
            performanceForceFallback =
                performanceForceFallback,
            adaptiveControlActive = adaptiveControlActive,
            adaptiveStrength = adaptiveStrength
        )
}
