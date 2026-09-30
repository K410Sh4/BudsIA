package com.k410sh4.budsia.core.focus

enum class FocusMode(
    val displayName: String
) {
    AUTO("Auto"),
    VOICE("Voz"),
    NOISE_REDUCTION("Ruído"),
    SPECIFIC_SOUND("Som específico")
}

enum class FocusSupport {
    AVAILABLE,
    DEGRADED,
    UNAVAILABLE
}

data class FocusCapability(
    val mode: FocusMode,
    val support: FocusSupport,
    val reason: String
)

data class FocusPlan(
    val mode: FocusMode,
    val useNeuralEnhancement: Boolean,
    val capability: FocusCapability
)
