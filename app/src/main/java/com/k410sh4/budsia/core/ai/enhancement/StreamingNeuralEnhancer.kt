package com.k410sh4.budsia.core.ai.enhancement

import java.io.File

data class NeuralEnhancerCapabilities(
    val engineId: String,
    val modelId: String,
    val requiredSampleRateHz: Int,
    val recommendedFrameSamples: Int,
    val provider: String,
    val inferenceThreads: Int
)

data class NeuralAudioChunk(
    val samples: FloatArray,
    val sampleRateHz: Int
)

interface StreamingNeuralEnhancer {
    val isPrepared: Boolean

    fun prepare(
        modelId: String,
        modelFile: File
    ): Result<NeuralEnhancerCapabilities>

    fun process(
        samples: FloatArray,
        sampleRateHz: Int
    ): Result<NeuralAudioChunk>

    fun reset(): Result<Unit>
    fun release()
}
