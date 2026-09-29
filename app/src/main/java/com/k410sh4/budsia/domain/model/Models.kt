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
        "Reconhecimento de voz local",
        AiCapabilityState.UNAVAILABLE,
        "Ainda não verificado"
    ),
    val languageId: AiCapability = AiCapability(
        "Identificação de idioma",
        AiCapabilityState.READY,
        "ML Kit local"
    ),
    val translation: AiCapability = AiCapability(
        "Tradução offline",
        AiCapabilityState.DOWNLOAD_REQUIRED,
        "Modelos baixados sob demanda"
    ),
    val speakerDiarization: AiCapability = AiCapability(
        "Diarização de falantes",
        AiCapabilityState.DOWNLOAD_REQUIRED,
        "Motor V2 opcional"
    ),
    val speakerIdentification: AiCapability = AiCapability(
        "Identidade de voz",
        AiCapabilityState.EXPERIMENTAL,
        "Perfis temporários por sessão"
    ),
    val localLlm: AiCapability = AiCapability(
        "LLM local",
        AiCapabilityState.PLANNED,
        "Camada futura"
    ),
    val discourseAnalysis: AiCapability = AiCapability(
        "Análise discursiva",
        AiCapabilityState.EXPERIMENTAL,
        "Regras explicáveis"
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
            TranslationMode.PT_TO_EN -> TranslationRoute("pt", "en", lang == "pt")
            TranslationMode.EN_TO_PT -> TranslationRoute("en", "pt", lang == "en")
            TranslationMode.AUTO_PT_EN -> when (lang) {
                "pt" -> TranslationRoute("pt", "en", true)
                "en" -> TranslationRoute("en", "pt", true)
                else -> TranslationRoute(null, null, false)
            }
        }
    }
}

enum class ConversationEngine {
    ANDROID_COMPAT,
    LOCAL_V2
}

data class ConversationItem(
    val id: Long = 0,
    val sessionId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val speakerLabel: String = "Falante ?",
    val speakerSimilarity: Float? = null,
    val speakerStable: Boolean = false,
    val originalText: String,
    val languageTag: String? = null,
    val languageConfidence: Float? = null,
    val languageReason: String? = null,
    val translationTargetTag: String? = null,
    val translatedText: String? = null,
    val translationStatus: String = "NONE",
    val recognitionConfidence: Float? = null,
    val transcriptQuality: Float? = null,
    val transcriptQualityReason: String? = null,
    val durationMs: Long? = null,
    val engine: ConversationEngine = ConversationEngine.ANDROID_COMPAT,
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
    val stage: String = "Parado",
    val detectedLanguage: String? = null,
    val translationTarget: String = "",
    val lastItem: ConversationItem? = null,
    val error: String? = null
)

enum class AdvancedModelStatus {
    NOT_INSTALLED,
    DOWNLOADING,
    READY,
    ERROR
}

data class AdvancedModelState(
    val status: AdvancedModelStatus = AdvancedModelStatus.NOT_INSTALLED,
    val currentFile: String? = null,
    val progress: Float = 0f,
    val message: String = "Pacote de conversação V2 não instalado",
    val installedVariant: String? = null
)

data class AdvancedSpeakerState(
    val available: Boolean = false,
    val running: Boolean = false,
    val modelState: AdvancedModelState = AdvancedModelState(),
    val speakerCount: Int = 0,
    val expectedSpeakers: Int = 0,
    val processedWindows: Int = 0,
    val droppedWindows: Int = 0,
    val lastProcessingMs: Long? = null,
    val stage: String = "Parado",
    val error: String? = null
)

data class AdvancedUtterance(
    val timestamp: Long,
    val speakerLabel: String,
    val speakerConfidence: Float?,
    val speakerStable: Boolean,
    val text: String,
    val whisperLanguageTag: String?,
    val diarizationConfidence: Float,
    val transcriptQualityScore: Float,
    val transcriptQualityReason: String,
    val durationMs: Long,
    val rms: Float
)
