package com.k410sh4.budsia.core.validation

import com.k410sh4.budsia.core.ai.enhancement.NeuralPipelineState
import com.k410sh4.budsia.core.audio.realtime.RealtimeEngineState
import com.k410sh4.budsia.core.audio.realtime.RealtimeProcessingMode
import com.k410sh4.budsia.core.performance.ThermalLevel
import java.util.Locale
import javax.inject.Inject

class DeviceValidationEvaluator @Inject constructor() {

    fun evaluate(
        requestedMode: RealtimeProcessingMode,
        samples: List<DeviceValidationSample>
    ): DeviceValidationReport {
        require(samples.isNotEmpty()) {
            "Validation requires at least one sample."
        }

        val first = samples.first()
        val last = samples.last()
        val durationMs =
            (last.elapsedMs - first.elapsedMs).coerceAtLeast(0L)

        val inputDropsDelta = monotonicDelta(
            first.inputDroppedSamples,
            last.inputDroppedSamples
        )
        val outputUnderrunsDelta = monotonicDelta(
            first.outputUnderrunSamples,
            last.outputUnderrunSamples
        )
        val inputXrunsDelta = nullableMonotonicDelta(
            first.inputXruns,
            last.inputXruns
        )
        val outputXrunsDelta = nullableMonotonicDelta(
            first.outputXruns,
            last.outputXruns
        )

        val checks = buildList {
            add(engineCheck(samples))
            add(sampleRateCheck(samples))
            add(inputDropCheck(samples, inputDropsDelta))
            add(outputUnderrunCheck(samples, outputUnderrunsDelta))
            add(xrunCheck(inputXrunsDelta, outputXrunsDelta))
            add(thermalCheck(samples))

            if (requestedMode == RealtimeProcessingMode.AI) {
                add(neuralStateCheck(samples))
                add(neuralRateCheck(samples))
                add(neuralRealtimeFactorCheck(samples))
            }
        }

        return DeviceValidationReport(
            durationMs = durationMs,
            requestedMode = requestedMode,
            inputSampleRateHz =
                samples.map { it.inputSampleRateHz }
                    .filter { it > 0 }
                    .distinct()
                    .singleOrNull(),
            neuralModelId =
                samples.mapNotNull { it.neuralModelId }
                    .lastOrNull(),
            checks = checks,
            inputDroppedSamplesDelta = inputDropsDelta,
            outputUnderrunSamplesDelta =
                if (samples.any { it.monitoringEnabled }) {
                    outputUnderrunsDelta
                } else {
                    null
                },
            inputXrunsDelta = inputXrunsDelta,
            outputXrunsDelta = outputXrunsDelta,
            maxRealtimeFactor =
                samples.mapNotNull { it.neuralRealtimeFactor }
                    .maxOrNull(),
            peakEstimatedProcessCpuPercent =
                samples.mapNotNull { it.processCpuPercent }
                    .maxOrNull(),
            startBatteryPercent = first.batteryPercent,
            endBatteryPercent = last.batteryPercent,
            maximumThermalLevel =
                samples.map { it.thermalLevel }
                    .maxBy { it.severity }
        )
    }

    private fun engineCheck(
        samples: List<DeviceValidationSample>
    ): ValidationCheck {
        val running = samples.count {
            it.engineState == RealtimeEngineState.RUNNING
        }
        val ratio = running.toDouble() / samples.size.toDouble()

        return when {
            ratio == 1.0 -> ValidationCheck(
                id = "engine",
                title = "Núcleo realtime",
                status = ValidationStatus.PASS,
                detail = "RUNNING em todas as amostras."
            )

            ratio >= 0.90 -> ValidationCheck(
                id = "engine",
                title = "Núcleo realtime",
                status = ValidationStatus.WARN,
                detail = "RUNNING em " +
                    (ratio * 100).toInt() +
                    "% das amostras."
            )

            else -> ValidationCheck(
                id = "engine",
                title = "Núcleo realtime",
                status = ValidationStatus.FAIL,
                detail = "Estado realtime instável durante o teste."
            )
        }
    }

    private fun sampleRateCheck(
        samples: List<DeviceValidationSample>
    ): ValidationCheck {
        val rates = samples
            .map { it.inputSampleRateHz }
            .filter { it > 0 }
            .distinct()

        return when {
            rates.isEmpty() -> ValidationCheck(
                id = "sample_rate",
                title = "Taxa de entrada",
                status = ValidationStatus.FAIL,
                detail = "Nenhuma taxa de entrada válida foi reportada."
            )

            rates.size == 1 -> ValidationCheck(
                id = "sample_rate",
                title = "Taxa de entrada",
                status = ValidationStatus.PASS,
                detail = rates.single().toString() + " Hz estáveis."
            )

            else -> ValidationCheck(
                id = "sample_rate",
                title = "Taxa de entrada",
                status = ValidationStatus.FAIL,
                detail = "A taxa mudou durante o teste: " +
                    rates.joinToString() +
                    " Hz."
            )
        }
    }

    private fun inputDropCheck(
        samples: List<DeviceValidationSample>,
        dropDelta: Long
    ): ValidationCheck {
        val frameDelta = monotonicDelta(
            samples.first().inputFrames,
            samples.last().inputFrames
        )

        if (frameDelta <= 0L) {
            return ValidationCheck(
                id = "input_drops",
                title = "Perdas de entrada",
                status = ValidationStatus.UNKNOWN,
                detail = "Sem frames suficientes para calcular."
            )
        }

        val ratio = dropDelta.toDouble() / frameDelta.toDouble()

        return when {
            dropDelta == 0L -> ValidationCheck(
                id = "input_drops",
                title = "Perdas de entrada",
                status = ValidationStatus.PASS,
                detail = "0 samples perdidos."
            )

            ratio <= 0.001 -> ValidationCheck(
                id = "input_drops",
                title = "Perdas de entrada",
                status = ValidationStatus.WARN,
                detail = dropDelta.toString() +
                    " samples perdidos (" +
                    formatPercent(ratio) +
                    ")."
            )

            else -> ValidationCheck(
                id = "input_drops",
                title = "Perdas de entrada",
                status = ValidationStatus.FAIL,
                detail = dropDelta.toString() +
                    " samples perdidos (" +
                    formatPercent(ratio) +
                    ")."
            )
        }
    }

    private fun outputUnderrunCheck(
        samples: List<DeviceValidationSample>,
        underrunDelta: Long
    ): ValidationCheck {
        if (samples.none { it.monitoringEnabled }) {
            return ValidationCheck(
                id = "output_underruns",
                title = "Underruns de saída",
                status = ValidationStatus.UNKNOWN,
                detail = "Monitoramento de saída não estava ativo."
            )
        }

        return if (underrunDelta == 0L) {
            ValidationCheck(
                id = "output_underruns",
                title = "Underruns de saída",
                status = ValidationStatus.PASS,
                detail = "0 samples de underrun."
            )
        } else {
            ValidationCheck(
                id = "output_underruns",
                title = "Underruns de saída",
                status = ValidationStatus.WARN,
                detail = underrunDelta.toString() +
                    " samples sem áudio processado."
            )
        }
    }

    private fun xrunCheck(
        inputDelta: Long?,
        outputDelta: Long?
    ): ValidationCheck {
        if (inputDelta == null && outputDelta == null) {
            return ValidationCheck(
                id = "xruns",
                title = "XRuns",
                status = ValidationStatus.UNKNOWN,
                detail = "A rota/API não expôs contador de XRuns."
            )
        }

        val total = (inputDelta ?: 0L) + (outputDelta ?: 0L)
        return if (total == 0L) {
            ValidationCheck(
                id = "xruns",
                title = "XRuns",
                status = ValidationStatus.PASS,
                detail = "Nenhum XRun observado."
            )
        } else {
            ValidationCheck(
                id = "xruns",
                title = "XRuns",
                status = ValidationStatus.WARN,
                detail = "Input=" +
                    (inputDelta?.toString() ?: "UNKNOWN") +
                    ", output=" +
                    (outputDelta?.toString() ?: "UNKNOWN") +
                    "."
            )
        }
    }

    private fun thermalCheck(
        samples: List<DeviceValidationSample>
    ): ValidationCheck {
        val maximum = samples
            .map { it.thermalLevel }
            .maxBy { it.severity }

        return when {
            maximum.severity >= ThermalLevel.SEVERE.severity ->
                ValidationCheck(
                    id = "thermal",
                    title = "Térmico",
                    status = ValidationStatus.FAIL,
                    detail = "Pico térmico: " + maximum.name + "."
                )

            maximum == ThermalLevel.MODERATE ->
                ValidationCheck(
                    id = "thermal",
                    title = "Térmico",
                    status = ValidationStatus.WARN,
                    detail = "Pico térmico: MODERATE."
                )

            maximum == ThermalLevel.UNKNOWN ->
                ValidationCheck(
                    id = "thermal",
                    title = "Térmico",
                    status = ValidationStatus.UNKNOWN,
                    detail = "Status térmico indisponível."
                )

            else -> ValidationCheck(
                id = "thermal",
                title = "Térmico",
                status = ValidationStatus.PASS,
                detail = "Pico térmico: " + maximum.name + "."
            )
        }
    }

    private fun neuralStateCheck(
        samples: List<DeviceValidationSample>
    ): ValidationCheck {
        val fallbackObserved = samples.any {
            it.neuralState == NeuralPipelineState.FALLBACK
        }
        val runningObserved = samples.any {
            it.neuralState == NeuralPipelineState.RUNNING
        }

        return when {
            fallbackObserved -> ValidationCheck(
                id = "neural_state",
                title = "IA streaming",
                status = ValidationStatus.FAIL,
                detail = "Fallback para DSP ocorreu durante o teste."
            )

            runningObserved -> ValidationCheck(
                id = "neural_state",
                title = "IA streaming",
                status = ValidationStatus.PASS,
                detail = "Runtime neural entrou em RUNNING."
            )

            else -> ValidationCheck(
                id = "neural_state",
                title = "IA streaming",
                status = ValidationStatus.FAIL,
                detail = "Runtime neural não entrou em RUNNING."
            )
        }
    }

    private fun neuralRateCheck(
        samples: List<DeviceValidationSample>
    ): ValidationCheck {
        val mismatches = samples.filter {
            it.neuralRequiredSampleRateHz != null &&
                it.inputSampleRateHz > 0 &&
                it.neuralRequiredSampleRateHz !=
                    it.inputSampleRateHz
        }

        return if (mismatches.isEmpty()) {
            ValidationCheck(
                id = "neural_rate",
                title = "Compatibilidade do modelo",
                status = ValidationStatus.PASS,
                detail = "Nenhuma incompatibilidade de sample rate observada."
            )
        } else {
            ValidationCheck(
                id = "neural_rate",
                title = "Compatibilidade do modelo",
                status = ValidationStatus.FAIL,
                detail = "Modelo e rota divergiram em " +
                    mismatches.size +
                    " amostras."
            )
        }
    }

    private fun neuralRealtimeFactorCheck(
        samples: List<DeviceValidationSample>
    ): ValidationCheck {
        val maxRtf = samples
            .mapNotNull { it.neuralRealtimeFactor }
            .maxOrNull()
            ?: return ValidationCheck(
                id = "neural_rtf",
                title = "Realtime factor",
                status = ValidationStatus.UNKNOWN,
                detail = "RTF neural ainda não estava disponível."
            )

        return when {
            maxRtf <= 0.90 -> ValidationCheck(
                id = "neural_rtf",
                title = "Realtime factor",
                status = ValidationStatus.PASS,
                detail = "RTF máximo " +
                    formatNumber(maxRtf) +
                    "×."
            )

            maxRtf <= 1.0 -> ValidationCheck(
                id = "neural_rtf",
                title = "Realtime factor",
                status = ValidationStatus.WARN,
                detail = "RTF máximo " +
                    formatNumber(maxRtf) +
                    "×; margem pequena."
            )

            else -> ValidationCheck(
                id = "neural_rtf",
                title = "Realtime factor",
                status = ValidationStatus.FAIL,
                detail = "RTF máximo " +
                    formatNumber(maxRtf) +
                    "×; inferência ficou mais lenta que tempo real."
            )
        }
    }

    private fun monotonicDelta(
        start: Long,
        end: Long
    ): Long = (end - start).coerceAtLeast(0L)

    private fun nullableMonotonicDelta(
        start: Long?,
        end: Long?
    ): Long? {
        if (start == null || end == null) return null
        return monotonicDelta(start, end)
    }

    private fun formatPercent(value: Double): String =
        String.format(Locale.US, "%.3f%%", value * 100.0)

    private fun formatNumber(value: Double): String =
        String.format(Locale.US, "%.2f", value)

    private val ThermalLevel.severity: Int
        get() = when (this) {
            ThermalLevel.NONE -> 0
            ThermalLevel.LIGHT -> 1
            ThermalLevel.MODERATE -> 2
            ThermalLevel.SEVERE -> 3
            ThermalLevel.CRITICAL -> 4
            ThermalLevel.EMERGENCY -> 5
            ThermalLevel.SHUTDOWN -> 6
            ThermalLevel.UNKNOWN -> -1
        }
}
