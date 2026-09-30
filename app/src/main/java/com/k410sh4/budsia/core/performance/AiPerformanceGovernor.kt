package com.k410sh4.budsia.core.performance

data class AiPerformancePolicy(
    val stopAiBelowBatteryPercent: Int = 15,
    val ecoBelowBatteryPercent: Int = 25
)

class AiPerformanceGovernor(
    private val policy: AiPerformancePolicy = AiPerformancePolicy()
) {
    fun decide(
        snapshot: DevicePerformanceSnapshot
    ): AiPerformanceDecision {
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
                reason = "Temperatura elevada; IA pausada para proteger estabilidade e bateria.",
                forceFallback = true
            )
        }

        val battery = snapshot.batteryPercent.value
        val charging = snapshot.isCharging.value == true

        if (
            battery != null &&
            battery <= policy.stopAiBelowBatteryPercent &&
            !charging
        ) {
            return AiPerformanceDecision(
                tier = AiPerformanceTier.DSP_ONLY,
                allowAi = false,
                reason = "Bateria em $battery%; IA pausada abaixo do limite de ${policy.stopAiBelowBatteryPercent}%.",
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
                reason = "Modo ECO recomendado pelas condições atuais do aparelho."
            )
        }

        if (
            snapshot.thermalLevel == ThermalLevel.NONE &&
            charging
        ) {
            return AiPerformanceDecision(
                tier = AiPerformanceTier.MAX_QUALITY,
                allowAi = true,
                reason = "Condições térmicas estáveis e aparelho carregando."
            )
        }

        return AiPerformanceDecision(
            tier = AiPerformanceTier.BALANCED,
            allowAi = true,
            reason = "Condições normais para processamento local."
        )
    }
}
