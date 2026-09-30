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
