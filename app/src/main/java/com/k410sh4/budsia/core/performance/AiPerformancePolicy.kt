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
        val thermalCritical = health.thermalSeverity in setOf(
            ThermalSeverity.SEVERE,
            ThermalSeverity.CRITICAL,
            ThermalSeverity.EMERGENCY,
            ThermalSeverity.SHUTDOWN
        )

        if (
            thermalCritical ||
            health.lowMemory ||
            (health.thermalHeadroomForecast10s ?: 0f) >= 0.98f
        ) {
            return AiPerformanceDecision(
                level = AiPerformanceLevel.DSP_ONLY,
                preferPowerEfficiency = true,
                reason = when {
                    health.lowMemory ->
                        "Proteção de memória: IA suspensa para liberar o modelo."
                    thermalCritical ->
                        "Proteção térmica: IA suspensa antes de throttling severo."
                    else ->
                        "Proteção térmica preventiva: previsão próxima do limite severo."
                }
            )
        }

        if (
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
                reason = "Carga reduzida para preservar estabilidade, temperatura ou bateria."
            )
        }

        val cpuConstrained =
            health.cpuHeadroomPercent != null &&
                health.cpuHeadroomPercent <= 10f

        if (cpuConstrained) {
            return AiPerformanceDecision(
                level = AiPerformanceLevel.BALANCED,
                // Low CPU headroom means the current periodic inference needs
                // performance more than power-efficient core placement.
                preferPowerEfficiency = false,
                reason = "CPU com pouca margem; prioridade mantida para cumprir o prazo da inferência."
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
            reason = "Margem térmica, energética e de CPU adequada."
        )
    }
}
