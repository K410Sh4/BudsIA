package com.k410sh4.budsia.core.focus

import com.k410sh4.budsia.core.ai.models.AiModelCatalog

/**
 * Resolves what BudsIA can truthfully execute with the currently installed
 * model set and the sample rate actually opened by Android/Oboe.
 *
 * SPECIFIC_SOUND intentionally remains unavailable until a verified realtime
 * semantic source-isolation engine is integrated. Offline source separation is
 * not presented as a live capability.
 */
class FocusModeResolver {

    fun capability(
        mode: FocusMode,
        inputSampleRateHz: Int?,
        installedModelIds: Set<String>
    ): FocusCapability {
        val compatibleModel =
            inputSampleRateHz
                ?.takeIf { it > 0 }
                ?.let(AiModelCatalog::bestForSampleRate)
                ?.takeIf {
                    installedModelIds.contains(it.id)
                }

        val anyInstalled =
            installedModelIds.isNotEmpty()

        return when (mode) {
            FocusMode.AUTO -> {
                if (compatibleModel != null) {
                    FocusCapability(
                        mode = mode,
                        support = FocusSupport.AVAILABLE,
                        reason =
                            "IA local compatível com a rota atual."
                    )
                } else if (
                    inputSampleRateHz == null ||
                    inputSampleRateHz <= 0
                ) {
                    FocusCapability(
                        mode = mode,
                        support = if (anyInstalled) {
                            FocusSupport.AVAILABLE
                        } else {
                            FocusSupport.DEGRADED
                        },
                        reason =
                            "A rota será avaliada ao iniciar; DSP é o fallback."
                    )
                } else {
                    FocusCapability(
                        mode = mode,
                        support = FocusSupport.DEGRADED,
                        reason =
                            "Sem modelo exato para $inputSampleRateHz Hz; Auto usa DSP."
                    )
                }
            }

            FocusMode.VOICE,
            FocusMode.NOISE_REDUCTION -> {
                if (compatibleModel != null) {
                    FocusCapability(
                        mode = mode,
                        support = FocusSupport.AVAILABLE,
                        reason =
                            "Speech enhancement local: ${compatibleModel.displayName}."
                    )
                } else if (
                    inputSampleRateHz == null ||
                    inputSampleRateHz <= 0
                ) {
                    FocusCapability(
                        mode = mode,
                        support = if (anyInstalled) {
                            FocusSupport.AVAILABLE
                        } else {
                            FocusSupport.UNAVAILABLE
                        },
                        reason = if (anyInstalled) {
                            "Compatibilidade será confirmada após abrir a rota."
                        } else {
                            "Instale o pacote neural local."
                        }
                    )
                } else {
                    FocusCapability(
                        mode = mode,
                        support = FocusSupport.UNAVAILABLE,
                        reason =
                            "Nenhum modelo verificado instalado aceita $inputSampleRateHz Hz."
                    )
                }
            }

            FocusMode.SPECIFIC_SOUND ->
                FocusCapability(
                    mode = mode,
                    support = FocusSupport.UNAVAILABLE,
                    reason =
                        "Separação semântica ao vivo ainda não possui um extrator realtime verificado."
                )
        }
    }

    fun plan(
        mode: FocusMode,
        inputSampleRateHz: Int?,
        installedModelIds: Set<String>
    ): FocusPlan {
        val capability = capability(
            mode = mode,
            inputSampleRateHz = inputSampleRateHz,
            installedModelIds = installedModelIds
        )

        val neural = when (mode) {
            FocusMode.AUTO ->
                capability.support == FocusSupport.AVAILABLE &&
                    inputSampleRateHz != null &&
                    inputSampleRateHz > 0

            FocusMode.VOICE,
            FocusMode.NOISE_REDUCTION ->
                capability.support == FocusSupport.AVAILABLE

            FocusMode.SPECIFIC_SOUND -> false
        }

        return FocusPlan(
            mode = mode,
            useNeuralEnhancement = neural,
            capability = capability
        )
    }
}
