package com.k410sh4.budsia.core.validation

import com.k410sh4.budsia.core.ai.enhancement.NeuralPipelineState
import com.k410sh4.budsia.core.audio.realtime.RealtimeEngineState
import com.k410sh4.budsia.core.audio.realtime.RealtimeProcessingMode
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
    fun aiSessionFailsWhenRealtimeFactorExceedsOne() {
        val samples = listOf(
            sample(
                elapsedMs = 0,
                inputFrames = 0,
                neuralState = NeuralPipelineState.RUNNING,
                neuralRate = 48_000,
                rtf = 0.70
            ),
            sample(
                elapsedMs = 30_000,
                inputFrames = 1_440_000,
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
        inputDrops: Long = 0L,
        outputUnderruns: Long = 0L,
        monitoring: Boolean = false,
        neuralState: NeuralPipelineState =
            NeuralPipelineState.IDLE,
        neuralRate: Int? = null,
        rtf: Double? = null,
        thermal: ThermalLevel = ThermalLevel.NONE
    ): DeviceValidationSample =
        DeviceValidationSample(
            elapsedMs = elapsedMs,
            engineState = RealtimeEngineState.RUNNING,
            processingMode = RealtimeProcessingMode.DSP,
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
            batteryPercent = 80,
            processCpuPercent = 20.0
        )
}
