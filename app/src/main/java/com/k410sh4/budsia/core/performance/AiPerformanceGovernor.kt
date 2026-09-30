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
                forceFallback = true
            )
        }

        if (
            snapshot.thermalLevel == ThermalLevel.SEVERE ||
            snapshot.thermalLevel == ThermalLevel.CRITICAL ||
            snapshot.thermalLevel == ThermalLevel.EMERGENCY ||
            snapshot.thermalLevel == ThermalLevel.SHUTDOWN
        ) {
            return AiPerformanceDecision(
                tier = AiPerformanceTier.DSP_ONLY,
                allowAi = false,
                reason =
                    "Performance reduzida para controlar a temperatura do aparelho.",
                forceFallback = true
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
                forceFallback = true
            )
        }

        if (
            snapshot.thermalLevel == ThermalLevel.MODERATE ||
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
                    "Modo ECO recomendado pelas condições atuais do aparelho."
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
                    "Condições térmicas estáveis e aparelho carregando."
            )
        }

        return AiPerformanceDecision(
            tier = AiPerformanceTier.BALANCED,
            allowAi = true,
            reason = "Condições normais para processamento local."
        )
    }
}
