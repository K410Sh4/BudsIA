package com.k410sh4.budsia.core.performance

enum class AiPerformanceLevel {
    MAX,
    BALANCED,
    ECO,
    DSP_ONLY
}

data class AiPerformanceDecision(
    val level: AiPerformanceLevel,
    val preferPowerEfficiency: Boolean,
    val reason: String
)

class AiPerformancePolicy {

    fun decide(
        health: DeviceHealthSnapshot
    ): AiPerformanceDecision {
        if (
            health.thermalSeverity >= ThermalSeverity.SEVERE ||
            (health.thermalHeadroomForecast10s ?: 0f) >= 0.98f
        ) {
            return AiPerformanceDecision(
                level = AiPerformanceLevel.DSP_ONLY,
                preferPowerEfficiency = true,
                reason = "Proteção térmica: IA suspensa antes de throttling severo."
            )
        }

        if (
            health.lowMemory ||
            health.thermalSeverity == ThermalSeverity.MODERATE ||
            (health.thermalHeadroomForecast10s ?: 0f) >= 0.88f ||
            (
                health.batteryPercent != null &&
                    health.batteryPercent <= 10 &&
                    health.charging != true
                )
        ) {
            return AiPerformanceDecision(
                level = AiPerformanceLevel.ECO,
                preferPowerEfficiency = true,
                reason = "Carga reduzida para preservar estabilidade, memória, bateria ou margem térmica."
            )
        }

        if (
            health.powerSaveMode ||
            health.thermalSeverity == ThermalSeverity.LIGHT ||
            (health.thermalHeadroomForecast10s ?: 0f) >= 0.72f ||
            (
                health.batteryPercent != null &&
                    health.batteryPercent <= 20 &&
                    health.charging != true
                )
        ) {
            return AiPerformanceDecision(
                level = AiPerformanceLevel.BALANCED,
                preferPowerEfficiency = true,
                reason = "Modo balanceado para operação sustentável."
            )
        }

        return AiPerformanceDecision(
            level = AiPerformanceLevel.MAX,
            preferPowerEfficiency = false,
            reason = "Margem térmica e energética adequada."
        )
    }
}
