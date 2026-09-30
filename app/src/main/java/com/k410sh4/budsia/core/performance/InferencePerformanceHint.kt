package com.k410sh4.budsia.core.performance

interface InferencePerformanceHintSession : AutoCloseable {
    val supported: Boolean

    fun reportActualWorkDuration(
        actualDurationNanos: Long
    )

    fun setPreferPowerEfficiency(
        enabled: Boolean
    )

    override fun close()
}

interface InferencePerformanceHintFactory {
    fun open(
        targetWorkDurationNanos: Long
    ): InferencePerformanceHintSession
}

/**
 * Explicit fallback when ADPF PerformanceHintManager is unavailable.
 *
 * Keeping a concrete no-op object avoids nullable hint sessions inside the
 * neural loop and makes unsupported Android/API paths deterministic.
 */
object NoOpInferencePerformanceHintSession :
    InferencePerformanceHintSession {

    override val supported: Boolean = false

    override fun reportActualWorkDuration(
        actualDurationNanos: Long
    ) = Unit

    override fun setPreferPowerEfficiency(
        enabled: Boolean
    ) = Unit

    override fun close() = Unit
}
