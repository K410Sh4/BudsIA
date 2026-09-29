package com.k410sh4.budsia.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.k410sh4.budsia.core.analysis.ExplainableDiscourseAnalyzer
import com.k410sh4.budsia.core.language.LocalLanguageEngine
import com.k410sh4.budsia.core.speech.OnDeviceSpeechEngine
import com.k410sh4.budsia.data.repository.ConversationRepository
import com.k410sh4.budsia.data.settings.PrivacySettings
import com.k410sh4.budsia.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BudsIAViewModel @Inject constructor(
    private val speech: OnDeviceSpeechEngine,
    private val language: LocalLanguageEngine,
    private val analyzer: ExplainableDiscourseAnalyzer,
    private val conversations: ConversationRepository,
    private val settings: PrivacySettings
) : ViewModel() {
    val speechState = speech.state
    val speechLanguageModels = speech.languageModels
    val timeline = conversations.recent

    val strictOffline = settings.strictOffline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val saveTranscript = settings.saveTranscript
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val targetLanguage = settings.targetLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "pt")

    private val _pipeline = MutableStateFlow(LivePipelineState())
    val pipeline: StateFlow<LivePipelineState> = _pipeline.asStateFlow()

    private val _sessionItems = MutableStateFlow<List<ConversationItem>>(emptyList())
    val sessionItems: StateFlow<List<ConversationItem>> = _sessionItems.asStateFlow()

    private val _capabilities = MutableStateFlow(AiCapabilities())
    val capabilities: StateFlow<AiCapabilities> = _capabilities.asStateFlow()

    private val _downloadedLanguages = MutableStateFlow<Set<String>>(emptySet())
    val downloadedLanguages: StateFlow<Set<String>> = _downloadedLanguages.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    private var continuous = false
    private var lastProcessedResultId = 0L
    private var activeInputLanguageTag: String? = null

    private val speechTags = listOf("pt-BR", "en-US", "es-ES")

    init {
        refreshCapabilities()

        viewModelScope.launch {
            speech.state.collect { state ->
                if (state.modelDownloadRequired) {
                    continuous = false
                    _pipeline.update {
                        it.copy(
                            running = false,
                            stage = "Speech model required",
                            error = state.error
                        )
                    }
                } else if (continuous) {
                    _pipeline.update {
                        it.copy(
                            running = true,
                            stage = when {
                                state.processing -> "Processing locally"
                                state.listening -> "Listening"
                                else -> state.statusMessage ?: it.stage
                            },
                            error = state.error
                        )
                    }
                }

                if (state.resultId > lastProcessedResultId && state.finalText.isNotBlank()) {
                    lastProcessedResultId = state.resultId
                    processRecognizedText(state.finalText.trim(), state.confidence)
                }
            }
        }
    }

    fun refreshCapabilities() {
        viewModelScope.launch {
            speech.refreshLanguageSupport(speechTags)

            val downloaded = runCatching { language.downloadedLanguages() }.getOrDefault(emptySet())
            _downloadedLanguages.value = downloaded

            val speechAvailable = speech.checkAvailability()
            _capabilities.value = AiCapabilities(
                onDeviceSpeech = AiCapability(
                    "On-device speech",
                    if (speechAvailable) AiCapabilityState.READY else AiCapabilityState.UNAVAILABLE,
                    if (speechAvailable)
                        "Android on-device SpeechRecognizer available; language models are checked separately"
                    else
                        "Requires Android 12+ and an installed on-device recognition service"
                ),
                languageId = AiCapability(
                    "Language identification",
                    AiCapabilityState.READY,
                    "ML Kit bundled model with confidence threshold"
                ),
                translation = AiCapability(
                    "Offline translation",
                    if (downloaded.isEmpty()) AiCapabilityState.DOWNLOAD_REQUIRED else AiCapabilityState.READY,
                    if (downloaded.isEmpty())
                        "Install translation models in Models"
                    else
                        "Installed: " + downloaded.sorted().joinToString()
                ),
                speakerDiarization = AiCapability(
                    "Speaker diarization",
                    AiCapabilityState.PLANNED,
                    "sherpa-onnx local pipeline planned for the next phase"
                ),
                speakerIdentification = AiCapability(
                    "Speaker identification",
                    AiCapabilityState.PLANNED,
                    "Local voice embeddings planned after diarization"
                ),
                localLlm = AiCapability(
                    "Local LLM",
                    AiCapabilityState.PLANNED,
                    "Gemini Nano capability probe + LiteRT-LM fallback planned"
                ),
                discourseAnalysis = AiCapability(
                    "Discourse analysis",
                    AiCapabilityState.EXPERIMENTAL,
                    "Transparent local rules with evidence; no hidden-intent claims"
                )
            )
        }
    }

    fun startLive(inputLanguageTag: String? = null) {
        if (!speech.checkAvailability()) {
            _events.tryEmit("On-device speech recognition is not available on this phone.")
            return
        }

        activeInputLanguageTag = inputLanguageTag
        continuous = true
        _pipeline.value = _pipeline.value.copy(
            running = true,
            stage = "Preparing offline speech",
            error = null
        )
        speech.startContinuous(inputLanguageTag)
    }

    fun stopLive() {
        continuous = false
        speech.stopContinuous()
        _pipeline.value = _pipeline.value.copy(running = false, stage = "Stopped", error = null)
    }

    fun clearSession() {
        _sessionItems.value = emptyList()
        lastProcessedResultId = speech.state.value.resultId
    }

    fun clearSavedTimeline() {
        viewModelScope.launch {
            conversations.clear()
            _events.emit("Saved timeline cleared.")
        }
    }

    fun downloadModel(languageTag: String) {
        viewModelScope.launch {
            _events.emit("Downloading translation model for $languageTag over Wi-Fi…")
            language.downloadLanguageModel(languageTag)
                .onSuccess {
                    _events.emit("$languageTag translation model installed.")
                    refreshCapabilities()
                }
                .onFailure {
                    _events.emit(it.message ?: "Translation model download failed")
                }
        }
    }

    fun downloadSpeechModel(languageTag: String) {
        _events.tryEmit("Requesting Android offline speech model for $languageTag…")
        speech.downloadLanguageModel(languageTag)
    }

    fun refreshSpeechModels() {
        speech.refreshLanguageSupport(speechTags)
    }

    fun setStrictOffline(value: Boolean) {
        viewModelScope.launch { settings.setStrictOffline(value) }
    }

    fun setSaveTranscript(value: Boolean) {
        viewModelScope.launch { settings.setSaveTranscript(value) }
    }

    fun setTargetLanguage(tag: String) {
        viewModelScope.launch { settings.setTargetLanguage(tag) }
    }

    private suspend fun processRecognizedText(text: String, confidence: Float?) {
        _pipeline.value = _pipeline.value.copy(stage = "Language ID", error = null)

        val guess = runCatching { language.identifyWithConfidence(text) }
            .getOrDefault(LanguageGuess(null, 0f))

        val configuredSource = activeInputLanguageTag?.substringBefore('-')
        val highConfidenceDetected = guess.languageTag?.takeIf { guess.confidence >= 0.80f }

        val sourceForTranslation = highConfidenceDetected
            ?: configuredSource
            ?: guess.languageTag

        val displayedLanguage = guess.languageTag
            ?: configuredSource

        val target = targetLanguage.value

        _pipeline.value = _pipeline.value.copy(
            stage = "Translation",
            detectedLanguage = displayedLanguage
        )

        val translated = when {
            sourceForTranslation == null -> {
                _events.tryEmit("Language confidence too low; translation skipped for this segment.")
                null
            }
            sourceForTranslation == target -> text
            else -> {
                language.translate(
                    text = text,
                    sourceTag = sourceForTranslation,
                    targetTag = target,
                    allowModelDownload = false
                ).getOrElse {
                    _events.tryEmit(it.message ?: "Translation unavailable")
                    null
                }
            }
        }

        _pipeline.value = _pipeline.value.copy(stage = "Analysis")
        val signals = analyzer.analyze(text)

        val item = ConversationItem(
            speakerLabel = "Speaker A",
            originalText = text,
            languageTag = displayedLanguage,
            translatedText = translated,
            recognitionConfidence = confidence,
            signals = signals
        )

        _sessionItems.update { (it + item).takeLast(300) }
        if (saveTranscript.value) conversations.insert(item)

        _pipeline.value = _pipeline.value.copy(
            running = continuous,
            stage = if (continuous) "Listening" else "Idle",
            detectedLanguage = displayedLanguage,
            translationTarget = target,
            lastItem = item,
            error = null
        )
    }

    override fun onCleared() {
        speech.stopContinuous()
        super.onCleared()
    }
}
