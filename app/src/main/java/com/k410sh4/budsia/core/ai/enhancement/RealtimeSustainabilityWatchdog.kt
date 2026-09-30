package com.k410sh4.budsia.core.ai.enhancement

/**
 * Protects the live pipeline from genuinely unsustainable neural inference
 * without treating short startup/JIT/scheduler spikes as terminal failures.
 *
 * A fallback is allowed only after a warm-up period and only while both:
 * - the moving RTF is materially above realtime; and
 * - the cumulative RTF no longer has enough long-run headroom.
 *
 * The breach must also persist for consecutive processed chunks.
 */
internal class RealtimeSustainabilityWatchdog(
    private val warmupChunks: Long = DEFAULT_WARMUP_CHUNKS,
    private val movingRtfLimit: Double = DEFAULT_MOVING_RTF_LIMIT,
    private val cumulativeRtfLimit: Double =
        DEFAULT_CUMULATIVE_RTF_LIMIT,
    private val requiredConsecutiveBreaches: Int =
        DEFAULT_REQUIRED_CONSECUTIVE_BREACHES
) {
    private var consecutiveBreaches = 0

    init {
        require(warmupChunks >= 0L)
        require(movingRtfLimit > 1.0)
        require(cumulativeRtfLimit > 0.0)
        require(requiredConsecutiveBreaches > 0)
    }

    fun shouldFallback(
        chunksProcessed: Long,
        movingRealtimeFactor: Double,
        cumulativeRealtimeFactor: Double
    ): Boolean {
        if (
            chunksProcessed < warmupChunks ||
            !movingRealtimeFactor.isFinite() ||
            !cumulativeRealtimeFactor.isFinite()
        ) {
            consecutiveBreaches = 0
            return false
        }

        val breached =
            movingRealtimeFactor > movingRtfLimit &&
                cumulativeRealtimeFactor >
                    cumulativeRtfLimit

        consecutiveBreaches = if (breached) {
            consecutiveBreaches + 1
        } else {
            0
        }

        return consecutiveBreaches >=
            requiredConsecutiveBreaches
    }

    companion object {
        internal const val DEFAULT_WARMUP_CHUNKS = 300L
        internal const val DEFAULT_MOVING_RTF_LIMIT = 1.10
        internal const val DEFAULT_CUMULATIVE_RTF_LIMIT = 0.98
        internal const val DEFAULT_REQUIRED_CONSECUTIVE_BREACHES = 100
    }
}
