package com.k410sh4.budsia.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.k410sh4.budsia.core.advanced.AdvancedLocalSpeakerEngine
import com.k410sh4.budsia.core.advanced.AdvancedModelManager
import com.k410sh4.budsia.core.analysis.ExplainableDiscourseAnalyzer
import com.k410sh4.budsia.core.language.BilingualLanguageResolver
import com.k410sh4.budsia.core.language.LanguageDecision
import com.k410sh4.budsia.core.language.LocalLanguageEngine
import com.k410sh4.budsia.core.speech.OnDeviceSpeechEngine
import com.k410sh4.budsia.data.repository.ConversationRepository
import com.k410sh4.budsia.data.settings.PrivacySettings
import com.k410sh4.budsia.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
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
    val expectedSpeakers = settings.expectedSpeakers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

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

    private val bilingualResolver = BilingualLanguageResolver()
    private val speechTags = listOf("pt-BR", "en-US", "es-ES")

    private data class SpeakerLanguageMemory(
        var ptWeight: Float = 0f,
        var enWeight: Float = 0f
    ) {
        fun add(tag: String, weight: Float) {
            when (tag) {
                "pt" -> ptWeight += weight
                "en" -> enWeight += weight
            }
        }

        fun dominant(): String? {
            val total = ptWeight + enWeight
            if (total < 1.1f) return null
            val difference = kotlin.math.abs(ptWeight - enWeight)
            if (difference < 0.45f) return null
            return if (ptWeight > enWeight) "pt" else "en"
        }
    }

    private val speakerLanguages = mutableMapOf<String, SpeakerLanguageMemory>()
    private val translationWarnings = mutableSetOf<String>()

    private var standardContinuous = false
    private var lastProcessedResultId = 0L
    private var activeInputLanguageTag: String? = null
    private var currentSessionId = ""

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
                    processStandardUtterance(
                        text = state.finalText.trim(),
                        confidence = state.confidence
                    )
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

            val downloaded = runCatching {
                language.downloadedLanguages()
            }.getOrDefault(emptySet())

            _downloadedLanguages.value = downloaded

            val speechAvailable = speech.checkAvailability()
            val v2Ready = advancedModels.isReady()
            val ptEnReady = downloaded.contains("pt") && downloaded.contains("en")

            _capabilities.value = AiCapabilities(
                onDeviceSpeech = AiCapability(
                    "Reconhecimento de voz",
                    if (speechAvailable || v2Ready) AiCapabilityState.READY else AiCapabilityState.UNAVAILABLE,
                    when {
                        v2Ready -> "Motor V2 local disponível"
                        speechAvailable -> "Modo compatível do Android disponível"
                        else -> "Nenhum motor local pronto"
                    }
                ),
                languageId = AiCapability(
                    "Identificação de idioma",
                    if (v2Ready) AiCapabilityState.EXPERIMENTAL else AiCapabilityState.READY,
                    if (v2Ready)
                        "Consenso: Whisper + ML Kit + histórico do falante"
                    else
                        "ML Kit local"
                ),
                translation = AiCapability(
                    "Tradução PT ↔ EN",
                    if (ptEnReady) AiCapabilityState.READY else AiCapabilityState.DOWNLOAD_REQUIRED,
                    if (ptEnReady)
                        "Português e inglês instalados para tradução offline"
                    else
                        "Instale os modelos PT e EN"
                ),
                speakerDiarization = AiCapability(
                    "Separação de falantes V2",
                    if (v2Ready) AiCapabilityState.EXPERIMENTAL else AiCapabilityState.DOWNLOAD_REQUIRED,
                    if (v2Ready)
                        "Pyannote + ERes2Net com rejeição de incerteza"
                    else
                        "Requer o pacote de conversação V2"
                ),
                speakerIdentification = AiCapability(
                    "Identidade por voz",
                    if (v2Ready) AiCapabilityState.EXPERIMENTAL else AiCapabilityState.PLANNED,
                    if (v2Ready)
                        "Perfis temporários A/B/C por sessão; nomes pessoais virão após calibração"
                    else
                        "Disponível após instalar o V2"
                ),
                localLlm = AiCapability(
                    "LLM local",
                    AiCapabilityState.PLANNED,
                    "Será adicionado depois que voz/idioma estiverem estáveis"
                ),
                discourseAnalysis = AiCapability(
                    "Análise discursiva",
                    AiCapabilityState.EXPERIMENTAL,
                    "Regras explicáveis; não afirma intenção oculta como fato"
                )
            )
        }
    }

    fun startLive(inputLanguageTag: String? = null) {
        beginSessionIfNeeded()
        activeInputLanguageTag = inputLanguageTag

        if (advancedSpeakerMode.value) {
            viewModelScope.launch {
                standardContinuous = false
                speech.stopContinuous()

                _pipeline.value = _pipeline.value.copy(
                    running = false,
                    stage = "Iniciando Conversa V2…",
                    error = null
                )

                advancedEngine.start(expectedSpeakers.value)
                    .onFailure { error ->
                        _events.emit(error.message ?: "Falha ao iniciar Conversa V2")
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
            _events.tryEmit("O reconhecimento de voz local do Android não está disponível.")
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
        _pipeline.value = _pipeline.value.copy(
            running = false,
            stage = "Parado",
            error = null
        )
    }

    fun clearSession() {
        _sessionItems.value = emptyList()
        speakerLanguages.clear()
        translationWarnings.clear()
        lastProcessedResultId = speech.state.value.resultId
        currentSessionId = UUID.randomUUID().toString()
    }

    fun clearSavedTimeline() {
        viewModelScope.launch {
            conversations.clear()
            _events.emit("Histórico salvo apagado.")
        }
    }

    fun downloadModel(languageTag: String) {
        viewModelScope.launch {
            _events.emit("Baixando modelo de tradução $languageTag por Wi-Fi…")
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
        _events.tryEmit("Solicitando modelo de voz offline para $languageTag…")
        speech.downloadLanguageModel(languageTag)
    }

    fun refreshSpeechModels() {
        speech.refreshLanguageSupport(speechTags)
    }

    fun installAdvancedModels() {
        viewModelScope.launch {
            _events.emit("Baixando o pacote V2. Mantenha o app aberto até terminar.")
            advancedModels.installAll()
                .onSuccess {
                    _events.emit("Pacote Conversa V2 instalado com sucesso.")
                    refreshCapabilities()
                }
                .onFailure {
                    _events.emit(it.message ?: "Falha ao instalar pacote V2")
                }
        }
    }

    fun removeAdvancedModels() {
        viewModelScope.launch {
            if (advancedSpeakerState.value.running) advancedEngine.stop()
            advancedModels.removeAll()
            settings.setAdvancedSpeakerMode(false)
            refreshCapabilities()
            _events.emit("Pacote V2 removido.")
        }
    }

    fun setAdvancedSpeakerMode(value: Boolean) {
        if (value && !advancedModels.isReady()) {
            _events.tryEmit("Instale primeiro o pacote Conversa V2 em Modelos.")
            return
        }
        viewModelScope.launch {
            if (_pipeline.value.running) stopLive()
            settings.setAdvancedSpeakerMode(value)
        }
    }

    fun setExpectedSpeakers(value: Int) {
        viewModelScope.launch {
            if (_pipeline.value.running) {
                _events.emit("Altere o número de falantes com a sessão parada.")
                return@launch
            }
            settings.setExpectedSpeakers(value)
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

    private fun beginSessionIfNeeded() {
        if (_pipeline.value.running) return
        currentSessionId = UUID.randomUUID().toString()
        speakerLanguages.clear()
        translationWarnings.clear()
        _sessionItems.value = emptyList()
    }

    private suspend fun processStandardUtterance(
        text: String,
        confidence: Float?
    ) {
        val textGuess = runCatching {
            language.identifyWithConfidence(text)
        }.getOrDefault(LanguageGuess(null, 0f))

        val configured = activeInputLanguageTag
            ?.substringBefore('-')
            ?.takeIf { it == "pt" || it == "en" }

        val decision = if (configured != null) {
            LanguageDecision(
                languageTag = configured,
                confidence = maxOf(textGuess.confidence, 0.80f),
                reason = "idioma selecionado na entrada"
            )
        } else {
            bilingualResolver.resolve(
                whisperLanguage = null,
                textGuess = textGuess,
                speakerPrior = null
            )
        }

        persistConversationSegment(
            timestamp = System.currentTimeMillis(),
            speakerLabel = "Falante único",
            speakerConfidence = null,
            speakerStable = false,
            text = text,
            languageDecision = decision,
            recognitionConfidence = confidence,
            transcriptQuality = 1f,
            transcriptQualityReason = "SpeechRecognizer do Android",
            durationMs = null,
            engine = ConversationEngine.ANDROID_COMPAT
        )
    }

    private suspend fun processAdvancedUtterance(
        utterance: AdvancedUtterance
    ) {
        val textGuess = runCatching {
            language.identifyWithConfidence(utterance.text)
        }.getOrDefault(LanguageGuess(null, 0f))

        val prior = if (utterance.speakerStable && utterance.speakerLabel != "Falante ?") {
            speakerLanguages[utterance.speakerLabel]?.dominant()
        } else {
            null
        }

        val decision = bilingualResolver.resolve(
            whisperLanguage = utterance.whisperLanguageTag,
            textGuess = textGuess,
            speakerPrior = prior
        )

        if (
            utterance.speakerStable &&
            utterance.speakerLabel != "Falante ?" &&
            decision.languageTag != null &&
            decision.confidence >= 0.62f
        ) {
            val memory = speakerLanguages.getOrPut(utterance.speakerLabel) {
                SpeakerLanguageMemory()
            }
            memory.add(decision.languageTag, decision.confidence)
        }

        persistConversationSegment(
            timestamp = utterance.timestamp,
            speakerLabel = utterance.speakerLabel,
            speakerConfidence = utterance.speakerConfidence,
            speakerStable = utterance.speakerStable,
            text = utterance.text,
            languageDecision = decision,
            recognitionConfidence = null,
            transcriptQuality = utterance.transcriptQualityScore,
            transcriptQualityReason = utterance.transcriptQualityReason,
            durationMs = utterance.durationMs,
            engine = ConversationEngine.LOCAL_V2
        )
    }

    private suspend fun persistConversationSegment(
        timestamp: Long,
        speakerLabel: String,
        speakerConfidence: Float?,
        speakerStable: Boolean,
        text: String,
        languageDecision: LanguageDecision,
        recognitionConfidence: Float?,
        transcriptQuality: Float,
        transcriptQualityReason: String,
        durationMs: Long?,
        engine: ConversationEngine
    ) {
        _pipeline.update {
            it.copy(
                stage = "Analisando idioma…",
                detectedLanguage = languageDecision.languageTag,
                error = null
            )
        }

        val route = TranslationRouter.route(
            translationMode.value,
            languageDecision.languageTag
        )

        var translated: String? = null
        var translationStatus = when {
            languageDecision.languageTag == null -> "LANGUAGE_UNCERTAIN"
            !route.shouldTranslate -> "NOT_APPLICABLE"
            transcriptQuality < 0.68f -> "TRANSCRIPT_UNCERTAIN"
            else -> "PENDING"
        }

        if (translationStatus == "PENDING") {
            _pipeline.update { it.copy(stage = "Traduzindo…") }

            translated = language.translate(
                text = text,
                sourceTag = route.sourceTag!!,
                targetTag = route.targetTag!!,
                allowModelDownload = false
            ).getOrElse {
                val source = route.sourceTag ?: "?"
                val target = route.targetTag ?: "?"
                val key = source + "->" + target
                if (translationWarnings.add(key)) {
                    _events.tryEmit(
                        "Tradução " + source.uppercase() +
                            " → " + target.uppercase() +
                            " indisponível. Verifique os modelos em Modelos."
                    )
                }
                translationStatus = "MODEL_MISSING"
                null
            }

            if (translated != null) {
                translationStatus = "OK"
            }
        }

        _pipeline.update { it.copy(stage = "Analisando conversa…") }

        val signals = if (transcriptQuality >= 0.68f) {
            analyzer.analyze(text)
        } else {
            emptyList()
        }

        val item = ConversationItem(
            sessionId = currentSessionId,
            timestamp = timestamp,
            speakerLabel = speakerLabel,
            speakerSimilarity = speakerConfidence,
            speakerStable = speakerStable,
            originalText = text,
            languageTag = languageDecision.languageTag,
            languageConfidence = languageDecision.confidence,
            languageReason = languageDecision.reason,
            translationTargetTag = route.targetTag,
            translatedText = translated,
            translationStatus = translationStatus,
            recognitionConfidence = recognitionConfidence,
            transcriptQuality = transcriptQuality,
            transcriptQualityReason = transcriptQualityReason,
            durationMs = durationMs,
            engine = engine,
            signals = signals
        )

        _sessionItems.update { (it + item).takeLast(300) }
        if (saveTranscript.value) {
            conversations.insert(item)
        }

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
