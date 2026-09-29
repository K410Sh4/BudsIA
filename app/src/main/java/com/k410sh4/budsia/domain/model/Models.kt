package com.k410sh4.budsia.domain.model

enum class AiCapabilityState {
    READY,
    DOWNLOAD_REQUIRED,
    UNAVAILABLE,
    EXPERIMENTAL,
    PLANNED,
    REQUIRES_PERMISSION
}

data class AiCapability(
    val name: String,
    val state: AiCapabilityState,
    val detail: String
)

data class AiCapabilities(
    val onDeviceSpeech: AiCapability = AiCapability(
        "On-device speech",
        AiCapabilityState.UNAVAILABLE,
        "Not checked yet"
    ),
    val languageId: AiCapability = AiCapability(
        "Language identification",
        AiCapabilityState.READY,
        "Bundled ML Kit model"
    ),
    val translation: AiCapability = AiCapability(
        "Offline translation",
        AiCapabilityState.DOWNLOAD_REQUIRED,
        "Language models are downloaded on demand"
    ),
    val speakerDiarization: AiCapability = AiCapability(
        "Speaker diarization",
        AiCapabilityState.PLANNED,
        "Local sherpa-onnx pipeline planned"
    ),
    val speakerIdentification: AiCapability = AiCapability(
        "Speaker identification",
        AiCapabilityState.PLANNED,
        "Local voice embeddings planned"
    ),
    val localLlm: AiCapability = AiCapability(
        "Local LLM",
        AiCapabilityState.PLANNED,
        "Gemini Nano when available; LiteRT-LM fallback planned"
    ),
    val discourseAnalysis: AiCapability = AiCapability(
        "Discourse analysis",
        AiCapabilityState.EXPERIMENTAL,
        "V0.1 uses transparent local rules, not hidden-intent claims"
    )
)

enum class SignalType {
    ARTIFICIAL_URGENCY,
    GUILT_PRESSURE,
    FALSE_DILEMMA,
    AD_HOMINEM,
    APPEAL_TO_POPULARITY,
    THREAT_OR_COERCION,
    SCARCITY_PRESSURE,
    IMPLIED_REQUEST
}

data class AnalysisSignal(
    val type: SignalType,
    val confidence: Float,
    val evidence: String,
    val explanation: String
)

data class LanguageGuess(
    val languageTag: String?,
    val confidence: Float
)

enum class TranslationMode {
    AUTO_PT_EN,
    PT_TO_EN,
    EN_TO_PT
}

data class TranslationRoute(
    val sourceTag: String?,
    val targetTag: String?,
    val shouldTranslate: Boolean
)

object TranslationRouter {
    fun route(mode: TranslationMode, detectedLanguage: String?): TranslationRoute {
        val lang = detectedLanguage?.substringBefore('-')?.lowercase()
        return when (mode) {
            TranslationMode.PT_TO_EN -> TranslationRoute("pt", "en", lang == null || lang == "pt")
            TranslationMode.EN_TO_PT -> TranslationRoute("en", "pt", lang == null || lang == "en")
            TranslationMode.AUTO_PT_EN -> when (lang) {
                "pt" -> TranslationRoute("pt", "en", true)
                "en" -> TranslationRoute("en", "pt", true)
                null -> TranslationRoute(null, null, false)
                else -> TranslationRoute(lang, "pt", true)
            }
        }
    }
}

data class ConversationItem(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val speakerLabel: String = "Falante A",
    val originalText: String,
    val languageTag: String? = null,
    val languageConfidence: Float? = null,
    val translationTargetTag: String? = null,
    val translatedText: String? = null,
    val recognitionConfidence: Float? = null,
    val signals: List<AnalysisSignal> = emptyList()
)

enum class SpeechLanguageStatus {
    INSTALLED,
    DOWNLOAD_REQUIRED,
    PENDING,
    UNSUPPORTED,
    UNKNOWN
}

data class SpeechLanguageModelState(
    val languageTag: String,
    val status: SpeechLanguageStatus = SpeechLanguageStatus.UNKNOWN,
    val detail: String = ""
)

data class SpeechState(
    val available: Boolean = false,
    val listening: Boolean = false,
    val processing: Boolean = false,
    val partialText: String = "",
    val finalText: String = "",
    val confidence: Float? = null,
    val resultId: Long = 0,
    val requestedLanguage: String? = null,
    val errorCode: Int? = null,
    val error: String? = null,
    val statusMessage: String? = null,
    val modelDownloadRequired: Boolean = false,
    val modelDownloadProgress: Int? = null
)

data class LivePipelineState(
    val running: Boolean = false,
    val stage: String = "Idle",
    val detectedLanguage: String? = null,
    val translationTarget: String = "pt",
    val lastItem: ConversationItem? = null,
    val error: String? = null
)
