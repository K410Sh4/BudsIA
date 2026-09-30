package com.k2fsa.sherpa.onnx

/**
 * Minimal ABI-compatible Kotlin surface for sherpa-onnx 1.13.8.
 *
 * The native implementation is supplied by the verified sherpa-onnx Android runtime.
 */
class DenoisedAudio(
    val samples: FloatArray,
    val sampleRate: Int
)
