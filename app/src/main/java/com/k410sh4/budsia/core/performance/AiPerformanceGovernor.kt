package com.k410sh4.budsia.core.performance

class AiPerformanceGovernor {

    fun decide(
        snapshot: DevicePerformanceSnapshot,
        settings: AiPerformanceSettings = AiPerformanceSettings()
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
            settings.lowBatteryAutoFallbackEnabled &&
            battery != null &&
            battery <= settings.stopAiBelowBatteryPercent &&
            !charging
        ) {
            return AiPerformanceDecision(
                tier = AiPerformanceTier.DSP_ONLY,
                allowAi = false,
                reason = "Bateria em $battery%; IA pausada abaixo do limite configurado de ${settings.stopAiBelowBatteryPercent}%.",
                forceFallback = true
            )
        }

        if (
            snapshot.thermalLevel == ThermalLevel.MODERATE ||
            (
                battery != null &&
                    battery <= settings.ecoBelowBatteryPercent &&
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
