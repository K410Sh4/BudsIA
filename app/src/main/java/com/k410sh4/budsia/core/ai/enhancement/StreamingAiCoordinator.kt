package com.k410sh4.budsia.core.ai.enhancement

import com.k410sh4.budsia.core.ai.adaptation.AdaptiveBlendProcessor
import com.k410sh4.budsia.core.ai.adaptation.AdaptiveProfileRepository
import com.k410sh4.budsia.core.ai.models.ModelManager
import com.k410sh4.budsia.core.audio.realtime.RealtimeAiTransport
import com.k410sh4.budsia.core.audio.realtime.RealtimeAudioEngine
import com.k410sh4.budsia.core.audio.realtime.RealtimeEngineState
import com.k410sh4.budsia.core.audio.realtime.RealtimeProcessingMode
import com.k410sh4.budsia.core.diagnostics.MonotonicClock
import com.k410sh4.budsia.core.performance.AiPerformanceDecision
import com.k410sh4.budsia.core.performance.AiPerformanceLevel
import com.k410sh4.budsia.core.performance.AiPerformancePolicy
import com.k410sh4.budsia.core.performance.DeviceHealthMonitor
import com.k410sh4.budsia.core.performance.InferencePerformanceHintFactory
import com.k410sh4.budsia.core.performance.ThermalSeverity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sqrt

enum class NeuralPipelineState {
    IDLE,
    PREPARING,
    RUNNING,
    FALLBACK,
    ERROR
}

data class NeuralRuntimeTelemetry(
    val state: NeuralPipelineState = NeuralPipelineState.IDLE,
    val modelId: String? = null,
    val engineId: String? = null,
    val provider: String? = null,
    val requiredSampleRateHz: Int? = null,
    val frameSamples: Int? = null,
    val inferenceMs: Double? = null,
    val maxInferenceMs: Double? = null,
    val averageInferenceMs: Double? = null,
    val realtimeFactor: Double? = null,
    val chunksProcessed: Long = 0L,
    val samplesEnhanced: Long = 0L,
    val enhancedRms: Float? = null,
    val enhancedPeak: Float? = null,
    val enhancedWaveform: List<Float> = emptyList(),
    val adaptiveControlEnabled: Boolean = false,
    val adaptiveBlendApplied: Boolean = false,
    val adaptiveStrength: Float? = null,
    val adaptiveEnvironment: String? = null,
    val adaptiveProfileRevision: Long? = null,
    val adaptiveBypassReason: String? = null,
    val performanceLevel: AiPerformanceLevel = AiPerformanceLevel.MAX,
    val performanceReason: String? = null,
    val performanceHintSupported: Boolean = false,
    val preferPowerEfficiency: Boolean = false,
    val thermalSeverity: ThermalSeverity = ThermalSeverity.UNKNOWN,
    val thermalHeadroomNow: Float? = null,
    val thermalHeadroomForecast10s: Float? = null,
    val cpuHeadroomPercent: Float? = null,
    val batteryPercent: Int? = null,
    val charging: Boolean? = null,
    val powerSaveMode: Boolean = false,
    val lowMemory: Boolean = false,
    val fallbackReason: String? = null,
    val errorMessage: String? = null
)

/**
 * Non-realtime neural worker.
 *
 * Oboe callbacks only move PCM through native lock-free rings. Inference runs
 * on one long-lived dispatcher thread so Android PerformanceHintManager can
 * track a stable TID and optimize the periodic workload without manual CPU
 * affinity.
 */
class StreamingAiCoordinator(
    private val modelManager: ModelManager,
    private val enhancer: StreamingNeuralEnhancer,
    private val audioEngine: RealtimeAudioEngine,
    private val transport: RealtimeAiTransport,
    private val clock: MonotonicClock,
    private val adaptiveProfiles: AdaptiveProfileRepository,
    private val adaptiveBlendProcessor: AdaptiveBlendProcessor,
    private val deviceHealthMonitor: DeviceHealthMonitor,
    private val performancePolicy: AiPerformancePolicy,
    private val performanceHintFactory: InferencePerformanceHintFactory,
    private val inferenceDispatcher: CoroutineDispatcher
) {
    private val _telemetry = MutableStateFlow(NeuralRuntimeTelemetry())
    val telemetry: StateFlow<NeuralRuntimeTelemetry> = _telemetry.asStateFlow()

    suspend fun run(modelId: String) = withContext(inferenceDispatcher) {
        runOnInferenceThread(modelId)
    }

    private suspend fun runOnInferenceThread(modelId: String) {
        _telemetry.value = NeuralRuntimeTelemetry(
            state = NeuralPipelineState.PREPARING,
            modelId = modelId
        )

        var shouldPreserveTerminalState = false
        var hintSession: com.k410sh4.budsia.core.performance.InferencePerformanceHintSession? =
            null

        try {
            val modelFile = modelManager.verifiedFile(modelId)
                ?: error(
                    "O modelo de IA não está instalado ou falhou na verificação."
                )

            val capabilities = enhancer
                .prepare(
                    modelId = modelId,
                    modelFile = modelFile
                )
                .getOrThrow()

            val initialAudio = audioEngine.snapshot()
            check(initialAudio.state == RealtimeEngineState.RUNNING) {
                "O núcleo de áudio precisa estar ativo antes da IA."
            }
            check(
                initialAudio.inputSampleRateHz ==
                    capabilities.requiredSampleRateHz
            ) {
                "A rota atual usa ${initialAudio.inputSampleRateHz} Hz, mas o modelo exige ${capabilities.requiredSampleRateHz} Hz."
            }

            val preflightHealth = deviceHealthMonitor.health.value
            val preflightDecision =
                performancePolicy.decide(preflightHealth)

            if (
                preflightDecision.level ==
                    AiPerformanceLevel.DSP_ONLY
            ) {
                fallbackToDsp(preflightDecision.reason)
                shouldPreserveTerminalState = true
                return
            }

            val frameDurationNanos =
                capabilities.recommendedFrameSamples.toDouble() /
                    capabilities.requiredSampleRateHz.toDouble() *
                    1_000_000_000.0

            hintSession = performanceHintFactory.open(
                targetWorkDurationNanos =
                    frameDurationNanos.toLong().coerceAtLeast(1L)
            )
            hintSession.setPreferPowerEfficiency(
                preflightDecision.preferPowerEfficiency
            )

            transport.clear()
            audioEngine.setProcessingMode(RealtimeProcessingMode.AI)

            val frame = FloatArray(
                capabilities.recommendedFrameSamples
            )
            val adaptiveScratch = FloatArray(frame.size)

            var chunks = 0L
            var enhancedSamples = 0L
            var totalInferenceNanos = 0L
            var maxInferenceNanos = 0L
            var realtimeFactorEma = 0.0
            var lastPublishNanos = 0L
            var lastPowerEfficiencyPreference =
                preflightDecision.preferPowerEfficiency
            var currentDecision = preflightDecision

            _telemetry.value = telemetrySnapshot(
                modelId = modelId,
                capabilities = capabilities,
                decision = preflightDecision,
                hintSupported = hintSession.supported
            )

            while (currentCoroutineContext().isActive) {
                val engineSnapshot = audioEngine.snapshot()
                check(engineSnapshot.state == RealtimeEngineState.RUNNING) {
                    "O núcleo de áudio deixou o estado RUNNING."
                }
                check(
                    engineSnapshot.inputSampleRateHz ==
                        capabilities.requiredSampleRateHz
                ) {
                    "A taxa de amostragem da rota mudou durante a sessão."
                }

                val health = deviceHealthMonitor.health.value
                currentDecision = performancePolicy.decide(health)

                if (
                    currentDecision.level ==
                        AiPerformanceLevel.DSP_ONLY
                ) {
                    fallbackToDsp(currentDecision.reason)
                    shouldPreserveTerminalState = true
                    return
                }

                if (
                    currentDecision.preferPowerEfficiency !=
                        lastPowerEfficiencyPreference
                ) {
                    hintSession.setPreferPowerEfficiency(
                        currentDecision.preferPowerEfficiency
                    )
                    lastPowerEfficiencyPreference =
                        currentDecision.preferPowerEfficiency
                }

                val read = transport.readInput(
                    destination = frame,
                    requestedCount = frame.size
                )

                if (read == 0) {
                    delay(1)
                    continue
                }

                check(read == frame.size) {
                    "O transporte neural entregou um frame parcial."
                }

                val startNanos = clock.nowNanos()
                val output = enhancer
                    .process(
                        samples = frame,
                        sampleRateHz =
                            capabilities.requiredSampleRateHz
                    )
                    .getOrThrow()
                val inferenceNanos =
                    clock.nowNanos() - startNanos

                hintSession.reportActualWorkDuration(
                    inferenceNanos.coerceAtLeast(1L)
                )

                check(
                    output.sampleRateHz ==
                        capabilities.requiredSampleRateHz
                ) {
                    "O runtime neural alterou a taxa de amostragem inesperadamente."
                }

                val profile = adaptiveProfiles.activeProfile.value
                val controlEnabled =
                    adaptiveProfiles.runtimeControlEnabled.value
                val blend = adaptiveBlendProcessor.blendInto(
                    dry = frame,
                    wet = output.samples,
                    destination = adaptiveScratch,
                    requestedStrength =
                        profile.preferredEnhancementStrength,
                    enabled = controlEnabled
                )
                val finalSamples = blend.samples

                if (finalSamples.isNotEmpty()) {
                    transport.writeOutput(
                        source = finalSamples,
                        requestedCount = finalSamples.size
                    )
                    enhancedSamples += finalSamples.size
                }

                chunks++
                totalInferenceNanos += inferenceNanos
                maxInferenceNanos =
                    maxOf(maxInferenceNanos, inferenceNanos)

                val currentRtf =
                    inferenceNanos.toDouble() / frameDurationNanos
                realtimeFactorEma = if (chunks == 1L) {
                    currentRtf
                } else {
                    (0.90 * realtimeFactorEma) +
                        (0.10 * currentRtf)
                }

                if (
                    chunks >= 100L &&
                    realtimeFactorEma > 1.10
                ) {
                    val reason =
                        "A IA não sustentou tempo real nesta rota (RTF médio móvel %.2f).".format(
                            realtimeFactorEma
                        )
                    fallbackToDsp(reason)
                    shouldPreserveTerminalState = true
                    return
                }

                val now = clock.nowNanos()
                val publishIntervalNanos =
                    telemetryIntervalNanos(currentDecision.level)

                if (
                    now - lastPublishNanos >=
                        publishIntervalNanos
                ) {
                    _telemetry.value = telemetrySnapshot(
                        modelId = modelId,
                        capabilities = capabilities,
                        decision = currentDecision,
                        hintSupported = hintSession.supported,
                        inferenceNanos = inferenceNanos,
                        maxInferenceNanos = maxInferenceNanos,
                        averageInferenceNanos =
                            if (chunks > 0L) {
                                totalInferenceNanos.toDouble() /
                                    chunks.toDouble()
                            } else {
                                null
                            },
                        realtimeFactor = realtimeFactorEma,
                        chunksProcessed = chunks,
                        samplesEnhanced = enhancedSamples,
                        finalSamples = finalSamples,
                        adaptiveControlEnabled = controlEnabled,
                        adaptiveBlendApplied = blend.applied,
                        adaptiveStrength = blend.requestedStrength,
                        adaptiveEnvironment =
                            profile.environment.displayName,
                        adaptiveProfileRevision = profile.revision,
                        adaptiveBypassReason =
                            blend.reason?.takeUnless {
                                it == "disabled"
                            }
                    )
                    lastPublishNanos = now
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            fallbackToDsp(
                error.message ?: error::class.java.simpleName
            )
            shouldPreserveTerminalState = true
        } finally {
            hintSession?.close()
            enhancer.release()
            transport.clear()

            if (
                audioEngine.snapshot().processingMode ==
                    RealtimeProcessingMode.AI
            ) {
                audioEngine.setProcessingMode(
                    RealtimeProcessingMode.DSP
                )
            }

            if (!shouldPreserveTerminalState) {
                _telemetry.value = NeuralRuntimeTelemetry(
                    state = NeuralPipelineState.IDLE
                )
            }
        }
    }

    fun resetTelemetry() {
        if (_telemetry.value.state != NeuralPipelineState.RUNNING) {
            _telemetry.value = NeuralRuntimeTelemetry()
        }
    }

    private fun telemetrySnapshot(
        modelId: String,
        capabilities: NeuralEnhancerCapabilities,
        decision: AiPerformanceDecision,
        hintSupported: Boolean,
        inferenceNanos: Long? = null,
        maxInferenceNanos: Long? = null,
        averageInferenceNanos: Double? = null,
        realtimeFactor: Double? = null,
        chunksProcessed: Long = 0L,
        samplesEnhanced: Long = 0L,
        finalSamples: FloatArray = FloatArray(0),
        adaptiveControlEnabled: Boolean = false,
        adaptiveBlendApplied: Boolean = false,
        adaptiveStrength: Float? = null,
        adaptiveEnvironment: String? = null,
        adaptiveProfileRevision: Long? = null,
        adaptiveBypassReason: String? = null
    ): NeuralRuntimeTelemetry {
        val health = deviceHealthMonitor.health.value

        return NeuralRuntimeTelemetry(
            state = NeuralPipelineState.RUNNING,
            modelId = modelId,
            engineId = capabilities.engineId,
            provider = capabilities.provider,
            requiredSampleRateHz =
                capabilities.requiredSampleRateHz,
            frameSamples =
                capabilities.recommendedFrameSamples,
            inferenceMs =
                inferenceNanos?.div(1_000_000.0),
            maxInferenceMs =
                maxInferenceNanos?.div(1_000_000.0),
            averageInferenceMs =
                averageInferenceNanos?.div(1_000_000.0),
            realtimeFactor = realtimeFactor,
            chunksProcessed = chunksProcessed,
            samplesEnhanced = samplesEnhanced,
            enhancedRms = rms(finalSamples),
            enhancedPeak = peak(finalSamples),
            enhancedWaveform = waveform(
                finalSamples,
                points = 72
            ),
            adaptiveControlEnabled =
                adaptiveControlEnabled,
            adaptiveBlendApplied =
                adaptiveBlendApplied,
            adaptiveStrength = adaptiveStrength,
            adaptiveEnvironment = adaptiveEnvironment,
            adaptiveProfileRevision =
                adaptiveProfileRevision,
            adaptiveBypassReason =
                adaptiveBypassReason,
            performanceLevel = decision.level,
            performanceReason = decision.reason,
            performanceHintSupported = hintSupported,
            preferPowerEfficiency =
                decision.preferPowerEfficiency,
            thermalSeverity = health.thermalSeverity,
            thermalHeadroomNow =
                health.thermalHeadroomNow,
            thermalHeadroomForecast10s =
                health.thermalHeadroomForecast10s,
            cpuHeadroomPercent =
                health.cpuHeadroomPercent,
            batteryPercent = health.batteryPercent,
            charging = health.charging,
            powerSaveMode = health.powerSaveMode,
            lowMemory = health.lowMemory
        )
    }

    private fun telemetryIntervalNanos(
        level: AiPerformanceLevel
    ): Long = when (level) {
        AiPerformanceLevel.MAX -> 200_000_000L
        AiPerformanceLevel.BALANCED -> 350_000_000L
        AiPerformanceLevel.ECO -> 500_000_000L
        AiPerformanceLevel.DSP_ONLY -> 1_000_000_000L
    }

    private fun fallbackToDsp(reason: String) {
        audioEngine.setProcessingMode(RealtimeProcessingMode.DSP)

        val health = deviceHealthMonitor.health.value
        val decision = performancePolicy.decide(health)

        _telemetry.value = _telemetry.value.copy(
            state = NeuralPipelineState.FALLBACK,
            performanceLevel = decision.level,
            performanceReason = decision.reason,
            thermalSeverity = health.thermalSeverity,
            thermalHeadroomNow = health.thermalHeadroomNow,
            thermalHeadroomForecast10s =
                health.thermalHeadroomForecast10s,
            cpuHeadroomPercent =
                health.cpuHeadroomPercent,
            batteryPercent = health.batteryPercent,
            charging = health.charging,
            powerSaveMode = health.powerSaveMode,
            lowMemory = health.lowMemory,
            fallbackReason = reason,
            errorMessage = reason
        )
    }

    private fun rms(samples: FloatArray): Float? {
        if (samples.isEmpty()) return null
        var sum = 0.0
        for (sample in samples) {
            sum += sample.toDouble() * sample.toDouble()
        }
        return sqrt(sum / samples.size).toFloat()
    }

    private fun peak(samples: FloatArray): Float? {
        if (samples.isEmpty()) return null
        var result = 0f
        for (sample in samples) {
            result = maxOf(result, abs(sample))
        }
        return result
    }

    private fun waveform(
        samples: FloatArray,
        points: Int
    ): List<Float> {
        if (samples.isEmpty() || points <= 0) return emptyList()

        val bucket = maxOf(1, samples.size / points)
        val result = ArrayList<Float>(points)
        var start = 0

        while (
            start < samples.size &&
            result.size < points
        ) {
            val end = minOf(
                samples.size,
                start + bucket
            )
            var value = 0f
            for (index in start until end) {
                value = maxOf(
                    value,
                    abs(samples[index])
                )
            }
            result += value.coerceIn(0f, 1f)
            start = end
        }

        while (result.size < points) {
            result += 0f
        }

        return result
    }
}
