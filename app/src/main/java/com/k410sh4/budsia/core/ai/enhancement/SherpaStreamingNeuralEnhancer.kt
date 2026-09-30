package com.k410sh4.budsia.core.ai.enhancement

import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiserDpdfNetModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiserModelConfig
import com.k2fsa.sherpa.onnx.OnlineSpeechDenoiser
import com.k2fsa.sherpa.onnx.OnlineSpeechDenoiserConfig
import java.io.File

class SherpaStreamingNeuralEnhancer(
    private val provider: String = "cpu",
    private val inferenceThreads: Int = 2
) : StreamingNeuralEnhancer {

    private var denoiser: OnlineSpeechDenoiser? = null
    private var capabilities: NeuralEnhancerCapabilities? = null

    override val isPrepared: Boolean
        get() = denoiser != null && capabilities != null

    @Synchronized
    override fun prepare(
        modelId: String,
        modelFile: File
    ): Result<NeuralEnhancerCapabilities> = runCatching {
        require(modelFile.isFile) {
            "Verified neural model file is missing."
        }

        release()

        val created = OnlineSpeechDenoiser(
            config = OnlineSpeechDenoiserConfig(
                model = OfflineSpeechDenoiserModelConfig(
                    dpdfnet = OfflineSpeechDenoiserDpdfNetModelConfig(
                        model = modelFile.absolutePath
                    ),
                    numThreads = inferenceThreads,
                    debug = false,
                    provider = provider
                )
            )
        )

        val sampleRate = created.sampleRate
        val frameShift = created.frameShiftInSamples

        check(sampleRate > 0) {
            "Neural runtime returned an invalid sample rate."
        }
        check(frameShift > 0) {
            "Neural runtime returned an invalid frame shift."
        }

        val result = NeuralEnhancerCapabilities(
            engineId = "sherpa-onnx-1.13.8-online-denoiser",
            modelId = modelId,
            requiredSampleRateHz = sampleRate,
            recommendedFrameSamples = frameShift,
            provider = provider,
            inferenceThreads = inferenceThreads
        )

        denoiser = created
        capabilities = result
        result
    }

    @Synchronized
    override fun process(
        samples: FloatArray,
        sampleRateHz: Int
    ): Result<NeuralAudioChunk> = runCatching {
        val active = denoiser
            ?: error("Neural enhancer is not prepared.")

        val caps = capabilities
            ?: error("Neural enhancer capabilities are unavailable.")

        require(sampleRateHz == caps.requiredSampleRateHz) {
            "AI model expects ${caps.requiredSampleRateHz} Hz, got $sampleRateHz Hz."
        }
        require(samples.isNotEmpty()) {
            "AI input chunk is empty."
        }

        val output = active.run(samples, sampleRateHz)

        NeuralAudioChunk(
            samples = output.samples,
            sampleRateHz = output.sampleRate
        )
    }

    @Synchronized
    override fun reset(): Result<Unit> = runCatching {
        denoiser?.reset()
    }

    @Synchronized
    override fun release() {
        denoiser?.release()
        denoiser = null
        capabilities = null
    }
}
