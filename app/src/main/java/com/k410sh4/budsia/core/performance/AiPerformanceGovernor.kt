package com.k410sh4.budsia.core.performance

import java.util.concurrent.atomic.AtomicReference

data class AiPerformancePolicy(
    val stopAiBelowBatteryPercent: Int? = null,
    val ecoBelowBatteryPercent: Int = 25
) {
    init {
        require(
            stopAiBelowBatteryPercent == null ||
                stopAiBelowBatteryPercent in 1..100
        )
        require(ecoBelowBatteryPercent in 1..100)
    }
}

/**
 * Deterministic protection policy around the neural worker.
 *
 * Thermal and Android low-memory fallback are always enforced.
 * Battery shutdown is user-configurable and disabled by default.
 *
 * MAX/BALANCED/ECO do not silently swap neural models. They drive Android
 * scheduling hints and telemetry while exact-rate model selection remains the
 * source of truth for the active model.
 */
class AiPerformanceGovernor(
    initialPolicy: AiPerformancePolicy = AiPerformancePolicy()
) {
    private val policyRef = AtomicReference(initialPolicy)

    val policy: AiPerformancePolicy
        get() = policyRef.get()

    fun updatePolicy(policy: AiPerformancePolicy) {
        policyRef.set(policy)
    }

    fun decide(
        snapshot: DevicePerformanceSnapshot
    ): AiPerformanceDecision {
        val policy = policyRef.get()

        if (snapshot.lowMemory.value == true) {
            return AiPerformanceDecision(
                tier = AiPerformanceTier.DSP_ONLY,
                allowAi = false,
                reason = "Memória baixa reportada pelo Android.",
                forceFallback = true,
                preferPowerEfficiency = true
            )
        }

        val severeThermal =
            snapshot.thermalLevel == ThermalLevel.SEVERE ||
                snapshot.thermalLevel == ThermalLevel.CRITICAL ||
                snapshot.thermalLevel == ThermalLevel.EMERGENCY ||
                snapshot.thermalLevel == ThermalLevel.SHUTDOWN

        val thermalForecast =
            snapshot.thermalHeadroomForecast10s.value

        if (
            severeThermal ||
            (thermalForecast != null && thermalForecast >= 0.98f)
        ) {
            return AiPerformanceDecision(
                tier = AiPerformanceTier.DSP_ONLY,
                allowAi = false,
                reason = if (severeThermal) {
                    "Performance reduzida para controlar a temperatura do aparelho."
                } else {
                    "Previsão térmica próxima do limite severo; IA suspensa preventivamente."
                },
                forceFallback = true,
                preferPowerEfficiency = true
            )
        }

        val battery = snapshot.batteryPercent.value
        val charging = snapshot.isCharging.value == true
        val stopThreshold = policy.stopAiBelowBatteryPercent

        if (
            stopThreshold != null &&
            battery != null &&
            battery <= stopThreshold &&
            !charging
        ) {
            return AiPerformanceDecision(
                tier = AiPerformanceTier.DSP_ONLY,
                allowAi = false,
                reason =
                    "Bateria em $battery%; limite configurado para IA é $stopThreshold%.",
                forceFallback = true,
                preferPowerEfficiency = true
            )
        }

        if (
            snapshot.thermalLevel == ThermalLevel.MODERATE ||
            (thermalForecast != null && thermalForecast >= 0.88f) ||
            (
                battery != null &&
                    battery <= policy.ecoBelowBatteryPercent &&
                    !charging
                )
        ) {
            return AiPerformanceDecision(
                tier = AiPerformanceTier.ECO,
                allowAi = true,
                reason =
                    "Modo ECO recomendado pelas condições térmicas ou energéticas.",
                preferPowerEfficiency = true
            )
        }

        val cpuHeadroom = snapshot.cpuHeadroomPercent.value
        if (
            cpuHeadroom != null &&
            cpuHeadroom <= 10f
        ) {
            return AiPerformanceDecision(
                tier = AiPerformanceTier.BALANCED,
                allowAi = true,
                reason =
                    "CPU com pouca margem; prioridade mantida para cumprir o prazo da inferência.",
                preferPowerEfficiency = false
            )
        }

        if (
            snapshot.powerSaveMode.value == true ||
            snapshot.thermalLevel == ThermalLevel.LIGHT ||
            (thermalForecast != null && thermalForecast >= 0.72f)
        ) {
            return AiPerformanceDecision(
                tier = AiPerformanceTier.BALANCED,
                allowAi = true,
                reason =
                    "Modo balanceado para operação sustentável.",
                preferPowerEfficiency = true
            )
        }

        if (
            snapshot.thermalLevel == ThermalLevel.NONE &&
            charging
        ) {
            return AiPerformanceDecision(
                tier = AiPerformanceTier.MAX_QUALITY,
                allowAi = true,
                reason =
                    "Condições térmicas estáveis e aparelho carregando.",
                preferPowerEfficiency = false
            )
        }

        return AiPerformanceDecision(
            tier = AiPerformanceTier.BALANCED,
            allowAi = true,
            reason = "Condições normais para processamento local.",
            preferPowerEfficiency = false
        )
    }
}
