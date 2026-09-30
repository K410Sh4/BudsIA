package com.k410sh4.budsia.core.ai.enhancement

import com.k410sh4.budsia.core.ai.models.ModelManager
import com.k410sh4.budsia.core.audio.realtime.RealtimeAiTransport
import com.k410sh4.budsia.core.audio.realtime.RealtimeAudioEngine
import com.k410sh4.budsia.core.audio.realtime.RealtimeEngineState
import com.k410sh4.budsia.core.audio.realtime.RealtimeProcessingMode
import com.k410sh4.budsia.core.diagnostics.MonotonicClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
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
    val fallbackReason: String? = null,
    val errorMessage: String? = null
)

/**
 * Coordinates the non-realtime neural worker.
 *
 * Oboe callbacks never invoke this class. The native input callback copies PCM
 * into a dedicated SPSC ring; this coordinator consumes complete model frames,
 * runs inference, and submits enhanced PCM to the native output ring.
 */
class StreamingAiCoordinator(
    private val modelManager: ModelManager,
    private val enhancer: StreamingNeuralEnhancer,
    private val audioEngine: RealtimeAudioEngine,
    private val transport: RealtimeAiTransport,
    private val clock: MonotonicClock
) {
    private val _telemetry = MutableStateFlow(NeuralRuntimeTelemetry())
    val telemetry: StateFlow<NeuralRuntimeTelemetry> = _telemetry.asStateFlow()

    suspend fun run(modelId: String) {
        _telemetry.value = NeuralRuntimeTelemetry(
            state = NeuralPipelineState.PREPARING,
            modelId = modelId
        )

        var shouldPreserveTerminalState = false

        try {
            val modelFile = modelManager.verifiedFile(modelId)
                ?: error("O modelo de IA não está instalado ou falhou na verificação.")

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

            transport.clear()
            audioEngine.setProcessingMode(RealtimeProcessingMode.AI)

            val frame = FloatArray(
                capabilities.recommendedFrameSamples
            )
            val frameDurationNanos =
                capabilities.recommendedFrameSamples.toDouble() /
                    capabilities.requiredSampleRateHz.toDouble() *
                    1_000_000_000.0

            var chunks = 0L
            var enhancedSamples = 0L
            var totalInferenceNanos = 0L
            var maxInferenceNanos = 0L
            var realtimeFactorEma = 0.0
            var lastPublishNanos = 0L

            _telemetry.value = NeuralRuntimeTelemetry(
                state = NeuralPipelineState.RUNNING,
                modelId = modelId,
                engineId = capabilities.engineId,
                provider = capabilities.provider,
                requiredSampleRateHz = capabilities.requiredSampleRateHz,
                frameSamples = capabilities.recommendedFrameSamples
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
                        sampleRateHz = capabilities.requiredSampleRateHz
                    )
                    .getOrThrow()
                val inferenceNanos =
                    clock.nowNanos() - startNanos

                check(
                    output.sampleRateHz ==
                        capabilities.requiredSampleRateHz
                ) {
                    "O runtime neural alterou a taxa de amostragem inesperadamente."
                }

                if (output.samples.isNotEmpty()) {
                    transport.writeOutput(
                        source = output.samples,
                        requestedCount = output.samples.size
                    )
                    enhancedSamples += output.samples.size
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

                // A sustained RTF above 1 means the worker is slower than the
                // incoming stream. Fail safely before the input ring grows into
                // audible multi-second latency.
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
                if (now - lastPublishNanos >= 200_000_000L) {
                    _telemetry.value = NeuralRuntimeTelemetry(
                        state = NeuralPipelineState.RUNNING,
                        modelId = modelId,
                        engineId = capabilities.engineId,
                        provider = capabilities.provider,
                        requiredSampleRateHz =
                            capabilities.requiredSampleRateHz,
                        frameSamples =
                            capabilities.recommendedFrameSamples,
                        inferenceMs =
                            inferenceNanos / 1_000_000.0,
                        maxInferenceMs =
                            maxInferenceNanos / 1_000_000.0,
                        averageInferenceMs =
                            totalInferenceNanos.toDouble() /
                                chunks.toDouble() /
                                1_000_000.0,
                        realtimeFactor = realtimeFactorEma,
                        chunksProcessed = chunks,
                        samplesEnhanced = enhancedSamples,
                        enhancedRms = rms(output.samples),
                        enhancedPeak = peak(output.samples),
                        enhancedWaveform = waveform(
                            output.samples,
                            points = 72
                        )
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

    private fun fallbackToDsp(reason: String) {
        audioEngine.setProcessingMode(RealtimeProcessingMode.DSP)
        _telemetry.value = _telemetry.value.copy(
            state = NeuralPipelineState.FALLBACK,
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

        while (start < samples.size && result.size < points) {
            val end = minOf(samples.size, start + bucket)
            var value = 0f
            for (index in start until end) {
                value = maxOf(value, abs(samples[index]))
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
