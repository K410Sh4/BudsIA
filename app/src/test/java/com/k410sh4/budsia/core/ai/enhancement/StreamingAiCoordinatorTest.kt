package com.k410sh4.budsia.core.ai.enhancement

import com.k410sh4.budsia.core.ai.models.AiModelCatalog
import com.k410sh4.budsia.core.ai.models.ModelInstallStatus
import com.k410sh4.budsia.core.ai.models.ModelManager
import com.k410sh4.budsia.core.audio.model.AudioSignalMetrics
import com.k410sh4.budsia.core.audio.realtime.EngineCommandResult
import com.k410sh4.budsia.core.audio.realtime.RealtimeAiTransport
import com.k410sh4.budsia.core.audio.realtime.RealtimeAudioConfig
import com.k410sh4.budsia.core.audio.realtime.RealtimeAudioEngine
import com.k410sh4.budsia.core.audio.realtime.RealtimeAudioSnapshot
import com.k410sh4.budsia.core.audio.realtime.RealtimeEngineState
import com.k410sh4.budsia.core.audio.realtime.RealtimeProcessingMode
import com.k410sh4.budsia.core.audio.realtime.StreamSharingMode
import com.k410sh4.budsia.core.diagnostics.MonotonicClock
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingAiCoordinatorTest {

    @Test
    fun sampleRateMismatchFallsBackToDspWithoutReadingPcm() = runTest {
        val audio = FakeAudioEngine(sampleRateHz = 44_100)
        val transport = FakeTransport()
        val coordinator = StreamingAiCoordinator(
            modelManager = FakeModelManager(),
            enhancer = FakeEnhancer(requiredSampleRateHz = 48_000),
            audioEngine = audio,
            transport = transport,
            clock = FakeClock()
        )

        coordinator.run(AiModelCatalog.DPDFNET2_48K_HR.id)

        assertEquals(
            NeuralPipelineState.FALLBACK,
            coordinator.telemetry.value.state
        )
        assertEquals(
            RealtimeProcessingMode.DSP,
            audio.mode
        )
        assertEquals(0, transport.readCalls)
        assertTrue(
            coordinator.telemetry.value.fallbackReason
                ?.contains("48000") == true
        )
    }

    @Test
    fun missingVerifiedModelFallsBackBeforePreparingEnhancer() = runTest {
        val audio = FakeAudioEngine(sampleRateHz = 48_000)
        val enhancer = FakeEnhancer(requiredSampleRateHz = 48_000)
        val coordinator = StreamingAiCoordinator(
            modelManager = FakeModelManager(hasVerifiedFile = false),
            enhancer = enhancer,
            audioEngine = audio,
            transport = FakeTransport(),
            clock = FakeClock()
        )

        coordinator.run(AiModelCatalog.DPDFNET2_48K_HR.id)

        assertEquals(
            NeuralPipelineState.FALLBACK,
            coordinator.telemetry.value.state
        )
        assertEquals(0, enhancer.prepareCalls)
        assertEquals(
            RealtimeProcessingMode.DSP,
            audio.mode
        )
    }

    private class FakeClock : MonotonicClock {
        private var now = 0L
        override fun nowNanos(): Long {
            now += 1_000_000L
            return now
        }
    }

    private class FakeModelManager(
        private val hasVerifiedFile: Boolean = true
    ) : ModelManager {
        override val statuses =
            MutableStateFlow<Map<String, ModelInstallStatus>>(emptyMap())

        override suspend fun refresh() = Unit

        override suspend fun download(modelId: String): Result<File> =
            Result.failure(UnsupportedOperationException())

        override suspend fun remove(modelId: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun verifiedFile(modelId: String): File? =
            if (hasVerifiedFile) File("verified-model.onnx") else null
    }

    private class FakeEnhancer(
        private val requiredSampleRateHz: Int
    ) : StreamingNeuralEnhancer {
        var prepareCalls = 0
        override val isPrepared: Boolean
            get() = prepareCalls > 0

        override fun prepare(
            modelId: String,
            modelFile: File
        ): Result<NeuralEnhancerCapabilities> {
            prepareCalls++
            return Result.success(
                NeuralEnhancerCapabilities(
                    engineId = "fake",
                    modelId = modelId,
                    requiredSampleRateHz = requiredSampleRateHz,
                    recommendedFrameSamples = 480,
                    provider = "fake",
                    inferenceThreads = 1
                )
            )
        }

        override fun process(
            samples: FloatArray,
            sampleRateHz: Int
        ): Result<NeuralAudioChunk> =
            Result.success(
                NeuralAudioChunk(
                    samples = samples.copyOf(),
                    sampleRateHz = sampleRateHz
                )
            )

        override fun reset(): Result<Unit> = Result.success(Unit)
        override fun release() = Unit
    }

    private class FakeTransport : RealtimeAiTransport {
        var readCalls = 0
        override fun readInput(
            destination: FloatArray,
            requestedCount: Int
        ): Int {
            readCalls++
            return 0
        }

        override fun writeOutput(
            source: FloatArray,
            requestedCount: Int
        ): Int = requestedCount

        override fun clear() = Unit
    }

    private class FakeAudioEngine(
        private val sampleRateHz: Int
    ) : RealtimeAudioEngine {
        var mode = RealtimeProcessingMode.DSP

        override fun start(config: RealtimeAudioConfig): EngineCommandResult =
            EngineCommandResult(success = true)

        override fun stop() = Unit

        override fun setMonitoring(enabled: Boolean): EngineCommandResult =
            EngineCommandResult(success = true)

        override fun setProcessingMode(mode: RealtimeProcessingMode) {
            this.mode = mode
        }

        override fun snapshot(): RealtimeAudioSnapshot =
            RealtimeAudioSnapshot(
                state = RealtimeEngineState.RUNNING,
                inputSampleRateHz = sampleRateHz,
                outputSampleRateHz = sampleRateHz,
                inputDeviceId = 1,
                outputDeviceId = 2,
                inputFrames = 0,
                processedFrames = 0,
                outputFrames = 0,
                droppedInputSamples = 0,
                outputOverrunSamples = 0,
                outputUnderrunSamples = 0,
                inputCallbacks = 0,
                outputCallbacks = 0,
                inputXruns = null,
                outputXruns = null,
                maxInputCallbackNanos = 0,
                maxOutputCallbackNanos = 0,
                lastProcessorNanos = 0,
                maxProcessorNanos = 0,
                inputRingHighWatermark = 0,
                outputRingHighWatermark = 0,
                disconnectCount = 0,
                monitoringEnabled = false,
                processingMode = mode,
                inputSharingMode = StreamSharingMode.SHARED,
                outputSharingMode = StreamSharingMode.SHARED,
                outputAvailable = true,
                lastErrorCode = 0,
                aiInputDroppedSamples = 0,
                aiEnhancedSamples = 0,
                rawMetrics = AudioSignalMetrics(0f, 0f, 0f, 0f),
                processedMetrics = AudioSignalMetrics(0f, 0f, 0f, 0f),
                waveform = emptyList()
            )

        override fun lastError(): String? = null
    }
}
