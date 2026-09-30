package com.k2fsa.sherpa.onnx

import android.content.res.AssetManager

data class OfflineSpeechDenoiserGtcrnModelConfig(
    var model: String = ""
)

data class OfflineSpeechDenoiserDpdfNetModelConfig(
    var model: String = "",
    var attenuationLimitDb: Float = 0.0f
)

data class OfflineSpeechDenoiserModelConfig(
    var gtcrn: OfflineSpeechDenoiserGtcrnModelConfig =
        OfflineSpeechDenoiserGtcrnModelConfig(),
    var dpdfnet: OfflineSpeechDenoiserDpdfNetModelConfig =
        OfflineSpeechDenoiserDpdfNetModelConfig(),
    var numThreads: Int = 1,
    var debug: Boolean = false,
    var provider: String = "cpu"
)

data class OnlineSpeechDenoiserConfig(
    var model: OfflineSpeechDenoiserModelConfig =
        OfflineSpeechDenoiserModelConfig()
)

/**
 * ABI-compatible wrapper for sherpa-onnx 1.13.8 OnlineSpeechDenoiser.
 *
 * Keep this class/package/signature synchronized with the pinned upstream runtime.
 * It is intentionally tiny so the app does not depend on the entire AAR source surface.
 */
class OnlineSpeechDenoiser(
    assetManager: AssetManager? = null,
    config: OnlineSpeechDenoiserConfig
) {
    private var ptr: Long

    init {
        ptr = if (assetManager != null) {
            newFromAsset(assetManager, config)
        } else {
            newFromFile(config)
        }
        require(ptr != 0L) {
            "Failed to create sherpa-onnx OnlineSpeechDenoiser."
        }
    }

    fun release() {
        if (ptr != 0L) {
            delete(ptr)
            ptr = 0L
        }
    }

    fun run(samples: FloatArray, sampleRate: Int): DenoisedAudio =
        run(ptr, samples, sampleRate)

    fun flush(): DenoisedAudio = flush(ptr)

    fun reset() = reset(ptr)

    val sampleRate: Int
        get() = getSampleRate(ptr)

    val frameShiftInSamples: Int
        get() = getFrameShiftInSamples(ptr)

    private external fun newFromAsset(
        assetManager: AssetManager,
        config: OnlineSpeechDenoiserConfig
    ): Long

    private external fun newFromFile(
        config: OnlineSpeechDenoiserConfig
    ): Long

    private external fun delete(ptr: Long)

    private external fun run(
        ptr: Long,
        samples: FloatArray,
        sampleRate: Int
    ): DenoisedAudio

    private external fun flush(ptr: Long): DenoisedAudio
    private external fun reset(ptr: Long)
    private external fun getSampleRate(ptr: Long): Int
    private external fun getFrameShiftInSamples(ptr: Long): Int

    companion object {
        init {
            System.loadLibrary("sherpa-onnx-jni")
        }
    }
}
