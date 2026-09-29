package com.k410sh4.budsia.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.k410sh4.budsia.core.advanced.AdvancedLocalSpeakerEngine
import com.k410sh4.budsia.core.advanced.AdvancedModelManager
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
    private val settings: PrivacySettings,
    private val advancedModels: AdvancedModelManager,
    private val advancedEngine: AdvancedLocalSpeakerEngine
) : ViewModel() {
    val speechState = speech.state
    val speechLanguageModels = speech.languageModels
    val timeline = conversations.recent

    val advancedModelState = advancedModels.state
    val advancedSpeakerState = advancedEngine.state

    val strictOffline = settings.strictOffline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val saveTranscript = settings.saveTranscript
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val targetLanguage = settings.targetLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "pt")
    val translationMode = settings.translationMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TranslationMode.AUTO_PT_EN)
    val advancedSpeakerMode = settings.advancedSpeakerMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _pipeline = MutableStateFlow(LivePipelineState())
    val pipeline: StateFlow<LivePipelineState> = _pipeline.asStateFlow()

    private val _sessionItems = MutableStateFlow<List<ConversationItem>>(emptyList())
    val sessionItems: StateFlow<List<ConversationItem>> = _sessionItems.asStateFlow()

    private val _capabilities = MutableStateFlow(AiCapabilities())
    val capabilities: StateFlow<AiCapabilities> = _capabilities.asStateFlow()

    private val _downloadedLanguages = MutableStateFlow<Set<String>>(emptySet())
    val downloadedLanguages: StateFlow<Set<String>> = _downloadedLanguages.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 24)
    val events = _events.asSharedFlow()

    private var standardContinuous = false
    private var lastProcessedResultId = 0L
    private var activeInputLanguageTag: String? = null

    private val speechTags = listOf("pt-BR", "en-US", "es-ES")

    init {
        refreshCapabilities()

        viewModelScope.launch {
            speech.state.collect { state ->
                if (advancedSpeakerMode.value) return@collect

                if (state.modelDownloadRequired) {
                    standardContinuous = false
                    _pipeline.update {
                        it.copy(
                            running = false,
                            stage = "Modelo de voz necessário",
                            error = state.error
                        )
                    }
                } else if (standardContinuous) {
                    _pipeline.update {
                        it.copy(
                            running = true,
                            stage = when {
                                state.processing -> "Processando localmente"
                                state.listening -> "Escutando"
                                else -> state.statusMessage ?: it.stage
                            },
                            error = state.error
                        )
                    }
                }

                if (state.resultId > lastProcessedResultId && state.finalText.isNotBlank()) {
                    lastProcessedResultId = state.resultId
                    processStandardUtterance(state.finalText.trim(), state.confidence)
                }
            }
        }

        viewModelScope.launch {
            advancedEngine.state.collect { state ->
                if (!advancedSpeakerMode.value) return@collect
                _pipeline.update {
                    it.copy(
                        running = state.running,
                        stage = state.stage,
                        error = state.error
                    )
                }
            }
        }

        viewModelScope.launch {
            advancedEngine.utterances.collect { utterance ->
                processAdvancedUtterance(utterance)
            }
        }

        viewModelScope.launch {
            advancedModels.state.collect {
                refreshCapabilities()
            }
        }
    }

    fun refreshCapabilities() {
        viewModelScope.launch {
            speech.refreshLanguageSupport(speechTags)

            val downloaded = runCatching { language.downloadedLanguages() }.getOrDefault(emptySet())
            _downloadedLanguages.value = downloaded

            val speechAvailable = speech.checkAvailability()
            val speakerModelsReady = advancedModels.isReady()

            _capabilities.value = AiCapabilities(
                onDeviceSpeech = AiCapability(
                    "Reconhecimento de voz local",
                    if (speechAvailable) AiCapabilityState.READY else AiCapabilityState.UNAVAILABLE,
                    if (speechAvailable)
                        "SpeechRecognizer local do Android disponível; os idiomas são verificados separadamente"
                    else
                        "O modo avançado pode funcionar independentemente após instalar seus modelos"
                ),
                languageId = AiCapability(
                    "Identificação de idioma",
                    AiCapabilityState.READY,
                    "ML Kit local no modo básico; Whisper multilíngue no modo avançado"
                ),
                translation = AiCapability(
                    "Tradução offline",
                    if (downloaded.isEmpty()) AiCapabilityState.DOWNLOAD_REQUIRED else AiCapabilityState.READY,
                    if (downloaded.isEmpty())
                        "Instale os modelos de tradução em Modelos"
                    else
                        "Instalados: " + downloaded.sorted().joinToString()
                ),
                speakerDiarization = AiCapability(
                    "Separação de falantes",
                    if (speakerModelsReady) AiCapabilityState.EXPERIMENTAL else AiCapabilityState.DOWNLOAD_REQUIRED,
                    if (speakerModelsReady)
                        "AudioRecord + Silero VAD + embeddings ERes2Net prontos para teste"
                    else
                        "Requer o pacote opcional de modelos avançados"
                ),
                speakerIdentification = AiCapability(
                    "Reconhecimento de voz por pessoa",
                    if (speakerModelsReady) AiCapabilityState.EXPERIMENTAL else AiCapabilityState.PLANNED,
                    if (speakerModelsReady)
                        "Agrupa vozes em Falante A/B/C; nomes cadastrados serão a próxima camada"
                    else
                        "Disponível após instalar e validar a separação de falantes"
                ),
                localLlm = AiCapability(
                    "LLM local",
                    AiCapabilityState.PLANNED,
                    "Teste de Gemini Nano + fallback LiteRT-LM planejados"
                ),
                discourseAnalysis = AiCapability(
                    "Análise discursiva",
                    AiCapabilityState.EXPERIMENTAL,
                    "Regras locais transparentes com evidências; sem afirmar intenção oculta"
                )
            )
        }
    }

    fun startLive(inputLanguageTag: String? = null) {
        activeInputLanguageTag = inputLanguageTag

        if (advancedSpeakerMode.value) {
            viewModelScope.launch {
                standardContinuous = false
                speech.stopContinuous()

                _pipeline.update {
                    it.copy(
                        running = false,
                        stage = "Iniciando separação local de falantes…",
                        error = null
                    )
                }

                advancedEngine.start()
                    .onFailure { error ->
                        _events.emit(error.message ?: "Falha ao iniciar o motor avançado")
                        _pipeline.update {
                            it.copy(
                                running = false,
                                stage = "Parado",
                                error = error.message
                            )
                        }
                    }
            }
            return
        }

        if (!speech.checkAvailability()) {
            _events.tryEmit("O reconhecimento de voz local não está disponível neste aparelho.")
            return
        }

        standardContinuous = true
        _pipeline.value = _pipeline.value.copy(
            running = true,
            stage = "Preparando voz offline",
            error = null
        )
        speech.startContinuous(inputLanguageTag)
    }

    fun stopLive() {
        standardContinuous = false
        speech.stopContinuous()
        viewModelScope.launch { advancedEngine.stop() }
        _pipeline.value = _pipeline.value.copy(running = false, stage = "Parado", error = null)
    }

    fun clearSession() {
        _sessionItems.value = emptyList()
        lastProcessedResultId = speech.state.value.resultId
    }

    fun clearSavedTimeline() {
        viewModelScope.launch {
            conversations.clear()
            _events.emit("Histórico salvo apagado.")
        }
    }

    fun downloadModel(languageTag: String) {
        viewModelScope.launch {
            _events.emit("Baixando modelo de tradução para $languageTag por Wi-Fi…")
            language.downloadLanguageModel(languageTag)
                .onSuccess {
                    _events.emit("Modelo de tradução $languageTag instalado.")
                    refreshCapabilities()
                }
                .onFailure {
                    _events.emit(it.message ?: "Falha ao baixar modelo de tradução")
                }
        }
    }

    fun downloadSpeechModel(languageTag: String) {
        _events.tryEmit("Solicitando modelo de voz offline do Android para $languageTag…")
        speech.downloadLanguageModel(languageTag)
    }

    fun refreshSpeechModels() {
        speech.refreshLanguageSupport(speechTags)
    }

    fun installAdvancedModels() {
        viewModelScope.launch {
            _events.emit("Baixando os modelos avançados. O pacote é grande; mantenha o app aberto.")
            advancedModels.installAll()
                .onSuccess {
                    _events.emit("Modelos avançados instalados. Agora a separação de falantes pode ser testada offline.")
                    refreshCapabilities()
                }
                .onFailure {
                    _events.emit(it.message ?: "Falha ao instalar modelos avançados")
                }
        }
    }

    fun removeAdvancedModels() {
        viewModelScope.launch {
            if (advancedSpeakerState.value.running) advancedEngine.stop()
            advancedModels.removeAll()
            settings.setAdvancedSpeakerMode(false)
            refreshCapabilities()
            _events.emit("Modelos avançados removidos.")
        }
    }

    fun setAdvancedSpeakerMode(value: Boolean) {
        if (value && !advancedModels.isReady()) {
            _events.tryEmit("Instale primeiro os modelos avançados na tela Modelos.")
            return
        }
        viewModelScope.launch {
            if (_pipeline.value.running) stopLive()
            settings.setAdvancedSpeakerMode(value)
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

    fun setTranslationMode(mode: TranslationMode) {
        viewModelScope.launch { settings.setTranslationMode(mode) }
    }

    private suspend fun processStandardUtterance(text: String, confidence: Float?) {
        val guess = runCatching { language.identifyWithConfidence(text) }
            .getOrDefault(LanguageGuess(null, 0f))

        val configuredSource = activeInputLanguageTag?.substringBefore('-')
        val source = guess.languageTag?.substringBefore('-') ?: configuredSource

        processConversationItem(
            speakerLabel = "Falante A",
            speakerSimilarity = null,
            text = text,
            sourceLanguage = source,
            languageConfidence = guess.confidence,
            recognitionConfidence = confidence
        )
    }

    private suspend fun processAdvancedUtterance(utterance: AdvancedUtterance) {
        val whisperLanguage = utterance.languageTag?.substringBefore('-')
        val fallbackGuess = if (whisperLanguage == null) {
            runCatching { language.identifyWithConfidence(utterance.text) }
                .getOrDefault(LanguageGuess(null, 0f))
        } else null

        processConversationItem(
            speakerLabel = utterance.speakerLabel,
            speakerSimilarity = utterance.speakerSimilarity,
            text = utterance.text,
            sourceLanguage = whisperLanguage ?: fallbackGuess?.languageTag,
            languageConfidence = fallbackGuess?.confidence,
            recognitionConfidence = null
        )
    }

    private suspend fun processConversationItem(
        speakerLabel: String,
        speakerSimilarity: Float?,
        text: String,
        sourceLanguage: String?,
        languageConfidence: Float?,
        recognitionConfidence: Float?
    ) {
        _pipeline.update {
            it.copy(stage = "Traduzindo", detectedLanguage = sourceLanguage, error = null)
        }

        val route = TranslationRouter.route(translationMode.value, sourceLanguage)

        val translated = when {
            !route.shouldTranslate || route.sourceTag == null || route.targetTag == null -> null
            route.sourceTag == route.targetTag -> text
            else -> {
                language.translate(
                    text = text,
                    sourceTag = route.sourceTag,
                    targetTag = route.targetTag,
                    allowModelDownload = false
                ).getOrElse {
                    _events.tryEmit(it.message ?: "Tradução indisponível")
                    null
                }
            }
        }

        _pipeline.update { it.copy(stage = "Analisando") }
        val signals = analyzer.analyze(text)

        val item = ConversationItem(
            speakerLabel = speakerLabel,
            speakerSimilarity = speakerSimilarity,
            originalText = text,
            languageTag = route.sourceTag ?: sourceLanguage,
            languageConfidence = languageConfidence,
            translationTargetTag = route.targetTag,
            translatedText = translated,
            recognitionConfidence = recognitionConfidence,
            signals = signals
        )

        _sessionItems.update { (it + item).takeLast(300) }
        if (saveTranscript.value) conversations.insert(item)

        val running = standardContinuous || advancedSpeakerState.value.running
        _pipeline.value = _pipeline.value.copy(
            running = running,
            stage = if (running) "Escutando" else "Parado",
            detectedLanguage = item.languageTag,
            translationTarget = route.targetTag ?: "",
            lastItem = item,
            error = null
        )
    }

    override fun onCleared() {
        speech.stopContinuous()
        viewModelScope.launch { advancedEngine.stop() }
        super.onCleared()
    }
}
