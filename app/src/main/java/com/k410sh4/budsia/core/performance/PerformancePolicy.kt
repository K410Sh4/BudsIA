package com.k410sh4.budsia.core.performance

enum class PerformanceAction {
    NONE,
    WARN,
    FORCE_DSP
}

data class PerformanceDecision(
    val action: PerformanceAction,
    val reason: String? = null
)

class PerformancePolicy {

    fun evaluate(
        snapshot: AiPerformanceSnapshot,
        aiRunning: Boolean
    ): PerformanceDecision {
        if (!aiRunning) {
            return when (snapshot.thermalLevel) {
                ThermalLevel.CRITICAL,
                ThermalLevel.EMERGENCY,
                ThermalLevel.SHUTDOWN -> PerformanceDecision(
                    action = PerformanceAction.WARN,
                    reason = "O dispositivo está sob estresse térmico elevado."
                )

                else -> PerformanceDecision(PerformanceAction.NONE)
            }
        }

        return when (snapshot.thermalLevel) {
            ThermalLevel.SEVERE -> PerformanceDecision(
                action = PerformanceAction.FORCE_DSP,
                reason = "IA reduzida para DSP: Android reportou estado térmico SEVERE."
            )

            ThermalLevel.CRITICAL -> PerformanceDecision(
                action = PerformanceAction.FORCE_DSP,
                reason = "IA reduzida para DSP: Android reportou estado térmico CRITICAL."
            )

            ThermalLevel.EMERGENCY -> PerformanceDecision(
                action = PerformanceAction.FORCE_DSP,
                reason = "IA reduzida para DSP: Android reportou estado térmico EMERGENCY."
            )

            ThermalLevel.SHUTDOWN -> PerformanceDecision(
                action = PerformanceAction.FORCE_DSP,
                reason = "IA reduzida para DSP: Android reportou estado térmico SHUTDOWN."
            )

            ThermalLevel.MODERATE -> PerformanceDecision(
                action = PerformanceAction.WARN,
                reason = "O dispositivo está aquecendo; a IA continua sob observação."
            )

            else -> PerformanceDecision(PerformanceAction.NONE)
        }
    }
}
