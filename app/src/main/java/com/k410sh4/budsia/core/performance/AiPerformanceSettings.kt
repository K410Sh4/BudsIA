package com.k410sh4.budsia.core.performance

data class AiPerformanceSettings(
    val lowBatteryAutoFallbackEnabled: Boolean = true,
    val stopAiBelowBatteryPercent: Int = DEFAULT_STOP_PERCENT,
    val ecoBelowBatteryPercent: Int = DEFAULT_ECO_PERCENT
) {
    init {
        require(stopAiBelowBatteryPercent in MIN_STOP_PERCENT..MAX_STOP_PERCENT)
        require(ecoBelowBatteryPercent in stopAiBelowBatteryPercent..MAX_ECO_PERCENT)
    }

    companion object {
        const val DEFAULT_STOP_PERCENT = 15
        const val DEFAULT_ECO_PERCENT = 25
        const val MIN_STOP_PERCENT = 5
        const val MAX_STOP_PERCENT = 30
        const val MAX_ECO_PERCENT = 50
    }
}
