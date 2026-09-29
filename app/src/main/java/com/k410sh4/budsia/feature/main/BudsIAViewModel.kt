package com.k410sh4.budsia.feature.main

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.k410sh4.budsia.core.analysis.ExplainableDiscourseAnalyzer
import com.k410sh4.budsia.core.language.LocalLanguageEngine
import com.k410sh4.budsia.core.speech.OnDeviceSpeechEngine
import com.k410sh4.budsia.data.repository.ConversationRepository
import com.k410sh4.budsia.data.settings.PrivacySettings
import com.k410sh4.budsia.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
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
    private var lastProcessedText = ""

    init {
        refreshCapabilities()

        viewModelScope.launch {
            speech.state.collect { state ->
                val text = state.finalText.trim()
                if (text.isNotEmpty() && text != lastProcessedText) {
                    lastProcessedText = text
                    processRecognizedText(text, state.confidence)
                }
            }
        }
    }

    fun refreshCapabilities() {
        viewModelScope.launch {
            val downloaded = runCatching { language.downloadedLanguages() }.getOrDefault(emptySet())
            _downloadedLanguages.value = downloaded

            val speechAvailable = speech.checkAvailability()
            _capabilities.value = AiCapabilities(
                onDeviceSpeech = AiCapability(
                    "On-device speech",
                    if (speechAvailable) AiCapabilityState.READY else AiCapabilityState.UNAVAILABLE,
                    if (speechAvailable)
                        "Android on-device SpeechRecognizer available"
                    else
                        "Requires Android 12+ and an installed on-device recognition service"
                ),
                languageId = AiCapability(
                    "Language identification",
                    AiCapabilityState.READY,
                    "ML Kit bundled language-ID model"
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
                    "sherpa-onnx local pipeline planned for V0.2"
                ),
                speakerIdentification = AiCapability(
                    "Speaker identification",
                    AiCapabilityState.PLANNED,
                    "Local voice embeddings planned for V0.2"
                ),
                localLlm = AiCapability(
                    "Local LLM",
                    AiCapabilityState.PLANNED,
                    "Gemini Nano capability probe + LiteRT-LM fallback planned for V0.2"
                ),
                discourseAnalysis = AiCapability(
                    "Discourse analysis",
                    AiCapabilityState.EXPERIMENTAL,
                    "V0.1 uses transparent rules with evidence; no mind-reading claims"
                )
            )
        }
    }

    fun startLive(inputLanguageTag: String? = null) {
        if (!speech.checkAvailability()) {
            _events.tryEmit("On-device speech recognition is not available on this phone yet.")
            return
        }
        continuous = true
        _pipeline.value = _pipeline.value.copy(running = true, stage = "Listening", error = null)
        speech.start(inputLanguageTag)
    }

    fun stopLive() {
        continuous = false
        speech.stop()
        _pipeline.value = _pipeline.value.copy(running = false, stage = "Stopped")
    }

    fun clearSession() {
        _sessionItems.value = emptyList()
        lastProcessedText = ""
    }

    fun clearSavedTimeline() {
        viewModelScope.launch {
            conversations.clear()
            _events.emit("Saved timeline cleared.")
        }
    }

    fun downloadModel(languageTag: String) {
        viewModelScope.launch {
            _events.emit("Downloading $languageTag model over Wi-Fi…")
            language.downloadLanguageModel(languageTag)
                .onSuccess {
                    _events.emit("$languageTag model installed.")
                    refreshCapabilities()
                }
                .onFailure {
                    _events.emit(it.message ?: "Model download failed")
                }
        }
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
        _pipeline.value = _pipeline.value.copy(stage = "Language ID")

        val detected = runCatching { language.identify(text) }.getOrNull()
        val target = targetLanguage.value

        _pipeline.value = _pipeline.value.copy(
            stage = "Translation",
            detectedLanguage = detected
        )

        val translated = if (detected == null || detected == target) {
            text
        } else {
            language.translate(
                text = text,
                sourceTag = detected,
                targetTag = target,
                allowModelDownload = false
            ).getOrElse {
                _events.tryEmit(it.message ?: "Translation unavailable")
                null
            }
        }

        _pipeline.value = _pipeline.value.copy(stage = "Analysis")
        val signals = analyzer.analyze(text)

        val item = ConversationItem(
            speakerLabel = "Speaker A",
            originalText = text,
            languageTag = detected,
            translatedText = translated,
            recognitionConfidence = confidence,
            signals = signals
        )

        _sessionItems.update { (it + item).takeLast(300) }
        if (saveTranscript.value) conversations.insert(item)

        _pipeline.value = _pipeline.value.copy(
            running = continuous,
            stage = if (continuous) "Listening" else "Idle",
            detectedLanguage = detected,
            translationTarget = target,
            lastItem = item,
            error = null
        )

        if (continuous) {
            delay(350)
            speech.start()
        }
    }
}
