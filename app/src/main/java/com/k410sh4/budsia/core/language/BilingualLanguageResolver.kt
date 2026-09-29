package com.k410sh4.budsia.core.language

import com.k410sh4.budsia.domain.model.LanguageGuess

data class LanguageDecision(
    val languageTag: String?,
    val confidence: Float,
    val reason: String
)

class BilingualLanguageResolver {
    fun resolve(
        whisperLanguage: String?,
        textGuess: LanguageGuess,
        speakerPrior: String? = null
    ): LanguageDecision {
        val whisper = normalizeSupported(whisperLanguage)
        val text = normalizeSupported(textGuess.languageTag)
        val prior = normalizeSupported(speakerPrior)

        if (whisper != null && text != null && whisper == text) {
            return LanguageDecision(
                whisper,
                maxOf(0.82f, textGuess.confidence),
                "Whisper e análise textual concordam"
            )
        }

        if (text != null && textGuess.confidence >= 0.72f) {
            return LanguageDecision(text, textGuess.confidence, "texto com alta confiança")
        }

        if (whisper != null && (text == null || textGuess.confidence < 0.58f)) {
            return LanguageDecision(whisper, 0.68f, "idioma do áudio pelo Whisper")
        }

        if (text != null && textGuess.confidence >= 0.55f) {
            return LanguageDecision(text, textGuess.confidence, "texto com confiança moderada")
        }

        if (prior != null && textGuess.confidence < 0.55f) {
            return LanguageDecision(prior, 0.45f, "histórico recente do mesmo falante")
        }

        return LanguageDecision(null, maxOf(textGuess.confidence, 0f), "idioma incerto")
    }

    private fun normalizeSupported(tag: String?): String? = when (
        tag?.removePrefix("<|")?.removeSuffix("|>")?.substringBefore('-')?.lowercase()
    ) {
        "pt" -> "pt"
        "en" -> "en"
        else -> null
    }
}
