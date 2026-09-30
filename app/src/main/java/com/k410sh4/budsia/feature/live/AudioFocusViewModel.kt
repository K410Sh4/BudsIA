package com.k410sh4.budsia.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.k410sh4.budsia.core.ai.adaptation.AcousticEnvironment
import com.k410sh4.budsia.core.ai.adaptation.AdaptiveProfileRepository
import com.k410sh4.budsia.core.ai.adaptation.AudioFeedback
import com.k410sh4.budsia.core.ai.enhancement.AdaptiveControlConfig
import com.k410sh4.budsia.core.ai.enhancement.NeuralPipelineState
import com.k410sh4.budsia.core.ai.enhancement.StreamingAiCoordinator
import com.k410sh4.budsia.core.ai.models.AiModelCatalog
import com.k410sh4.budsia.core.ai.models.ModelInstallState
import com.k410sh4.budsia.core.ai.models.ModelManager
import com.k410sh4.budsia.core.audio.model.PipelineState
import com.k410sh4.budsia.core.audio.realtime.RealtimeAudioConfig
import com.k410sh4.budsia.core.audio.realtime.RealtimeAudioEngine
import com.k410sh4.budsia.core.audio.realtime.RealtimeEngineState
import com.k410sh4.budsia.core.audio.realtime.RealtimeProcessingMode
import com.k410sh4.budsia.core.audio.routing.AudioRouteController
import com.k410sh4.budsia.core.audio.routing.AudioRouteMonitor
import com.k410sh4.budsia.core.performance.AiPerformanceGovernor
import com.k410sh4.budsia.core.performance.AiPerformanceMonitor
import com.k410sh4.budsia.core.performance.AiPerformanceSettingsRepository
import com.k410sh4.budsia.core.diagnostics.MonotonicClock
import com.k410sh4.budsia.core.validation.DeviceValidationEvaluator
import com.k410sh4.budsia.core.validation.DeviceValidationSample
import com.k410sh4.budsia.core.validation.DeviceValidationUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@HiltViewModel
class AudioFocusViewModel @Inject constructor(
    private val realtimeAudioEngine: RealtimeAudioEngine,
    private val routeMonitor: AudioRouteMonitor,
    private val routeController: AudioRouteController,
    private val modelManager: ModelManager,
    private val aiCoordinator: StreamingAiCoordinator,
    private val adaptiveProfiles: AdaptiveProfileRepository,
    private val performanceMonitor: AiPerformanceMonitor,
    private val performanceGovernor: AiPerformanceGovernor,
    private val performanceSettings:
        AiPerformanceSettingsRepository,
    private val validationEvaluator: DeviceValidationEvaluator,
    private val clock: MonotonicClock
) : ViewModel() {

    private val _uiState = MutableStateFlow(AudioFocusUiState())
    val uiState: StateFlow<AudioFocusUiState> = _uiState.asStateFlow()

    private var sessionJob: Job? = null
    private var aiJob: Job? = null
    private var modelJob: Job? = null
    private var validationJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            modelManager.refresh()
        }

        viewModelScope.launch {
            routeMonitor.devices.collect { catalog ->
                _uiState.update { current ->
                    val idle = current.pipelineState == PipelineState.IDLE ||
                        current.pipelineState == PipelineState.ERROR

                    val selectedInput = if (
                        current.selectedInputDeviceId == 0 ||
                        catalog.inputs.any {
                            it.id == current.selectedInputDeviceId
                        }
                    ) {
                        current.selectedInputDeviceId
                    } else if (idle) {
                        0
                    } else {
                        current.selectedInputDeviceId
                    }

                    val selectedOutput = if (
                        current.selectedOutputDeviceId == 0 ||
                        catalog.outputs.any {
                            it.id == current.selectedOutputDeviceId
                        }
                    ) {
                        current.selectedOutputDeviceId
                    } else if (idle) {
                        0
                    } else {
                        current.selectedOutputDeviceId
                    }

                    current.copy(
                        availableInputs = catalog.inputs,
                        availableOutputs = catalog.outputs,
                        selectedInputDeviceId = selectedInput,
                        selectedOutputDeviceId = selectedOutput
                    )
                }
            }
        }

        viewModelScope.launch {
            modelManager.statuses.collect { statuses ->
                _uiState.update { current ->
                    current.copy(
                        modelStatuses = AiModelCatalog.all.mapNotNull {
                            statuses[it.id]
                        }
                    )
                }
            }
        }

        viewModelScope.launch {
            performanceSettings.settings.collect { settings ->
                performanceGovernor.updatePolicy(
                    settings.toPolicy()
                )

                _uiState.update { current ->
                    current.copy(
                        performanceSettings = settings,
                        performanceDecision =
                            performanceGovernor.decide(
                                current.performanceSnapshot
                            )
                    )
                }
            }
        }

        viewModelScope.launch {
            performanceMonitor.snapshot.collect { snapshot ->
                _uiState.update { current ->
                    current.copy(
                        performanceSnapshot = snapshot,
                        performanceDecision =
                            performanceGovernor.decide(snapshot)
                    )
                }
            }
        }

        viewModelScope.launch {
            adaptiveProfiles.activeProfile.collect { profile ->
                _uiState.update {
                    it.copy(adaptiveProfile = profile)
                }
                publishAdaptiveControl(
                    enabled =
                        _uiState.value.adaptiveControlCandidateEnabled,
                    profile = profile
                )
            }
        }

        viewModelScope.launch {
            aiCoordinator.telemetry.collect { telemetry ->
                _uiState.update { current ->
                    val fellBack =
                        telemetry.state == NeuralPipelineState.FALLBACK &&
                            current.selectedMode == RealtimeProcessingMode.AI

                    current.copy(
                        selectedMode = if (fellBack) {
                            RealtimeProcessingMode.DSP
                        } else {
                            current.selectedMode
                        },
                        activeModelId = telemetry.modelId,
                        neuralTelemetry = telemetry,
                        errorMessage = telemetry.errorMessage
                            ?: current.errorMessage
                    )
                }
            }
        }
    }

    fun start() {
        val current = _uiState.value

        if (
            current.pipelineState == PipelineState.STARTING ||
            current.pipelineState == PipelineState.LISTENING ||
            current.pipelineState == PipelineState.STOPPING
        ) return

        if (sessionJob?.isActive == true) return

        if (
            current.selectedMode == RealtimeProcessingMode.AI &&
            current.performanceDecision?.allowAi == false
        ) {
            _uiState.update {
                it.copy(
                    selectedMode = RealtimeProcessingMode.DSP,
                    errorMessage =
                        current.performanceDecision.reason
                )
            }
            return
        }

        if (
            current.selectedMode == RealtimeProcessingMode.AI &&
            current.modelStatuses.none {
                it.state == ModelInstallState.INSTALLED
            }
        ) {
            _uiState.update {
                it.copy(
                    selectedMode = RealtimeProcessingMode.DSP,
                    errorMessage =
                        "Instale o pacote neural antes de iniciar o modo IA."
                )
            }
            return
        }

        val initialMode = current.selectedMode
        val requestedInputDeviceId = current.selectedInputDeviceId
        val requestedOutputDeviceId = current.selectedOutputDeviceId

        _uiState.update {
            it.copy(
                pipelineState = PipelineState.STARTING,
                errorMessage = null
            )
        }

        sessionJob = viewModelScope.launch(Dispatchers.Default) {
            val preparedRoute = routeController.prepare(
                inputDeviceId = requestedInputDeviceId,
                outputDeviceId = requestedOutputDeviceId
            )

            if (!preparedRoute.success) {
                _uiState.update {
                    it.copy(
                        pipelineState = PipelineState.ERROR,
                        preparedCommunicationMode = false,
                        errorMessage = preparedRoute.message
                            ?: "Não foi possível preparar a rota de áudio."
                    )
                }
                sessionJob = null
                return@launch
            }

            _uiState.update {
                it.copy(
                    preparedCommunicationMode =
                        preparedRoute.communicationMode
                )
            }

            val bootMode = if (initialMode == RealtimeProcessingMode.AI) {
                RealtimeProcessingMode.DSP
            } else {
                initialMode
            }

            val startResult = realtimeAudioEngine.start(
                RealtimeAudioConfig(
                    inputDeviceId = preparedRoute.inputDeviceId,
                    outputDeviceId = preparedRoute.outputDeviceId,
                    processingMode = bootMode,
                    communicationMode =
                        preparedRoute.communicationMode
                )
            )

            if (!startResult.success) {
                routeController.release()
                _uiState.update {
                    it.copy(
                        pipelineState = PipelineState.ERROR,
                        preparedCommunicationMode = false,
                        errorMessage = startResult.message
                            ?: "Falha ao iniciar o núcleo de áudio."
                    )
                }
                sessionJob = null
                return@launch
            }

            if (initialMode == RealtimeProcessingMode.AI) {
                launchAiWorkerForCurrentRoute()
            }

            try {
                while (isActive) {
                    val snapshot = realtimeAudioEngine.snapshot()

                    if (snapshot.state == RealtimeEngineState.ERROR) {
                        _uiState.update {
                            it.copy(
                                pipelineState = PipelineState.ERROR,
                                snapshot = snapshot,
                                errorMessage =
                                    realtimeAudioEngine.lastError()
                                        ?: "A rota de áudio foi interrompida."
                            )
                        }
                        break
                    }

                    val ratesMatch =
                        snapshot.outputSampleRateHz > 0 &&
                            snapshot.outputSampleRateHz ==
                                snapshot.inputSampleRateHz

                    val canMonitor =
                        snapshot.outputAvailable &&
                            ratesMatch &&
                            routeMonitor.isPrivateOutput(
                                snapshot.outputDeviceId
                            )

                    _uiState.update {
                        it.copy(
                            pipelineState = PipelineState.LISTENING,
                            snapshot = snapshot,
                            inputRouteLabel = routeMonitor.inputLabel(
                                snapshot.inputDeviceId
                            ),
                            outputRouteLabel =
                                if (snapshot.outputAvailable) {
                                    routeMonitor.outputLabel(
                                        snapshot.outputDeviceId
                                    )
                                } else {
                                    "Saída indisponível"
                                },
                            canMonitorOutput = canMonitor
                        )
                    }

                    delay(200)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        pipelineState = PipelineState.ERROR,
                        errorMessage = error.message
                            ?: error::class.java.simpleName
                    )
                }
            } finally {
                aiJob?.cancel()
                aiJob = null
                realtimeAudioEngine.stop()
                routeController.release()

                _uiState.update {
                    val base = it.copy(
                        snapshot = null,
                        canMonitorOutput = false,
                        preparedCommunicationMode = false,
                        activeModelId = null
                    )

                    if (it.pipelineState == PipelineState.ERROR) {
                        base
                    } else {
                        base.copy(pipelineState = PipelineState.IDLE)
                    }
                }
                sessionJob = null
            }
        }
    }

    fun stop() {
        if (sessionJob == null) return

        cancelValidation(
            "Teste interrompido porque a sessão foi encerrada."
        )

        _uiState.update {
            it.copy(pipelineState = PipelineState.STOPPING)
        }

        aiJob?.cancel()
        sessionJob?.cancel()
    }

    fun selectInputDevice(deviceId: Int) {
        if (!routeSelectionAllowed()) return

        val valid = deviceId == 0 ||
            _uiState.value.availableInputs.any { it.id == deviceId }

        if (!valid) {
            _uiState.update {
                it.copy(
                    errorMessage =
                        "A entrada selecionada não está disponível."
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                selectedInputDeviceId = deviceId,
                errorMessage = null
            )
        }
    }

    fun selectOutputDevice(deviceId: Int) {
        if (!routeSelectionAllowed()) return

        val valid = deviceId == 0 ||
            _uiState.value.availableOutputs.any { it.id == deviceId }

        if (!valid) {
            _uiState.update {
                it.copy(
                    errorMessage =
                        "A saída selecionada não está disponível."
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                selectedOutputDeviceId = deviceId,
                errorMessage = null
            )
        }
    }

    fun setProcessingMode(mode: RealtimeProcessingMode) {
        if (!validationMutationAllowed()) return

        if (
            mode == RealtimeProcessingMode.AI &&
            _uiState.value.performanceDecision?.allowAi == false
        ) {
            _uiState.update {
                it.copy(
                    errorMessage =
                        it.performanceDecision?.reason
                            ?: "A IA está temporariamente indisponível pelas condições do aparelho."
                )
            }
            return
        }

        if (mode == RealtimeProcessingMode.AI) {
            val snapshot = _uiState.value.snapshot
            val compatibleInstalled = if (snapshot != null) {
                installedModelIdForRate(snapshot.inputSampleRateHz) != null
            } else {
                _uiState.value.modelStatuses.any {
                    it.state == ModelInstallState.INSTALLED
                }
            }

            if (!compatibleInstalled) {
                _uiState.update {
                    it.copy(
                        errorMessage = if (snapshot != null) {
                            "Nenhum modelo neural instalado é compatível com ${snapshot.inputSampleRateHz} Hz."
                        } else {
                            "Instale o pacote neural antes de usar o modo IA."
                        }
                    )
                }
                return
            }
        }

        _uiState.update {
            it.copy(
                selectedMode = mode,
                errorMessage = null
            )
        }

        if (_uiState.value.pipelineState != PipelineState.LISTENING) {
            return
        }

        if (mode == RealtimeProcessingMode.AI) {
            launchAiWorkerForCurrentRoute()
        } else {
            aiJob?.cancel()
            aiJob = null
            realtimeAudioEngine.setProcessingMode(mode)
        }
    }

    fun setMonitoring(enabled: Boolean) {
        if (!validationMutationAllowed()) return

        if (enabled && !_uiState.value.canMonitorOutput) {
            _uiState.update {
                it.copy(
                    errorMessage =
                        "Monitoramento ao vivo requer uma saída privada compatível com a mesma taxa de amostragem."
                )
            }
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            val result = realtimeAudioEngine.setMonitoring(enabled)
            if (!result.success) {
                _uiState.update {
                    it.copy(
                        errorMessage = result.message
                            ?: "Não foi possível alterar o monitoramento."
                    )
                }
            } else {
                _uiState.update {
                    it.copy(errorMessage = null)
                }
            }
        }
    }

    fun installAiModel() {
        if (!validationMutationAllowed()) return
        if (modelJob?.isActive == true) return

        modelJob = viewModelScope.launch(Dispatchers.IO) {
            for (descriptor in AiModelCatalog.all) {
                val installed = _uiState.value.modelStatuses
                    .firstOrNull {
                        it.descriptor.id == descriptor.id
                    }
                    ?.state == ModelInstallState.INSTALLED

                if (installed) continue

                val result = modelManager.download(descriptor.id)
                if (result.isFailure) {
                    _uiState.update {
                        it.copy(
                            errorMessage =
                                result.exceptionOrNull()?.message
                                    ?: "Falha ao instalar ${descriptor.displayName}."
                        )
                    }
                    return@launch
                }
            }
        }
    }

    fun removeAiModel() {
        if (!validationMutationAllowed()) return
        if (modelJob?.isActive == true) return

        if (
            _uiState.value.selectedMode == RealtimeProcessingMode.AI ||
            aiJob?.isActive == true
        ) {
            _uiState.update {
                it.copy(
                    errorMessage =
                        "Pare o modo IA antes de remover os modelos."
                )
            }
            return
        }

        modelJob = viewModelScope.launch(Dispatchers.IO) {
            for (descriptor in AiModelCatalog.all) {
                val result = modelManager.remove(descriptor.id)
                if (result.isFailure) {
                    _uiState.update {
                        it.copy(
                            errorMessage =
                                result.exceptionOrNull()?.message
                                    ?: "Falha ao remover ${descriptor.displayName}."
                        )
                    }
                    return@launch
                }
            }
        }
    }

    fun selectEnvironment(
        environment: AcousticEnvironment
    ) {
        if (!validationMutationAllowed()) return

        viewModelScope.launch(Dispatchers.IO) {
            adaptiveProfiles.selectEnvironment(environment)
        }
    }

    fun setPreferredStrength(value: Float) {
        if (!validationMutationAllowed()) return

        viewModelScope.launch(Dispatchers.IO) {
            adaptiveProfiles.setPreferredEnhancementStrength(value)
        }
    }

    fun setAdaptiveControlCandidateEnabled(
        enabled: Boolean
    ) {
        if (!validationMutationAllowed()) return

        _uiState.update {
            it.copy(
                adaptiveControlCandidateEnabled = enabled,
                errorMessage = null
            )
        }

        publishAdaptiveControl(
            enabled = enabled,
            profile = _uiState.value.adaptiveProfile
        )
    }

    fun setLowBatteryAutoFallbackEnabled(
        enabled: Boolean
    ) {
        if (!validationMutationAllowed()) return

        viewModelScope.launch(Dispatchers.IO) {
            performanceSettings
                .setLowBatteryAutoFallbackEnabled(enabled)
        }
    }

    fun setStopAiBelowBatteryPercent(
        percent: Int
    ) {
        if (!validationMutationAllowed()) return

        viewModelScope.launch(Dispatchers.IO) {
            performanceSettings
                .setStopAiBelowBatteryPercent(percent)
        }
    }

    fun teachMoreFilter() {
        if (!validationMutationAllowed()) return
        applyAdaptiveFeedback(AudioFeedback.MORE_FILTER)
    }

    fun teachMoreNatural() {
        if (!validationMutationAllowed()) return
        applyAdaptiveFeedback(AudioFeedback.MORE_NATURAL)
    }

    fun teachGoodAsIs() {
        if (!validationMutationAllowed()) return
        applyAdaptiveFeedback(AudioFeedback.GOOD_AS_IS)
    }

    fun resetAdaptiveProfile() {
        if (!validationMutationAllowed()) return

        viewModelScope.launch(Dispatchers.IO) {
            adaptiveProfiles.resetActiveProfile()
        }
    }

    fun startValidation() {
        if (validationJob?.isActive == true) return

        val startState = _uiState.value
        if (
            startState.pipelineState != PipelineState.LISTENING ||
            startState.snapshot == null ||
            (
                startState.selectedMode ==
                    RealtimeProcessingMode.AI &&
                    startState.neuralTelemetry.state !=
                        NeuralPipelineState.RUNNING
                )
        ) {
            _uiState.update {
                it.copy(
                    validation = DeviceValidationUiState(
                        message =
                            "Aguarde o áudio e, no modo IA, o runtime neural entrar em RUNNING antes da validação."
                    )
                )
            }
            return
        }

        val requestedMode = startState.selectedMode

        validationJob =
            viewModelScope.launch(Dispatchers.Default) {
                val startedAtNanos = clock.nowNanos()
                val samples =
                    mutableListOf<DeviceValidationSample>()

                _uiState.update {
                    it.copy(
                        validation = DeviceValidationUiState(
                            running = true,
                            progress = 0f,
                            elapsedSeconds = 0,
                            message =
                                "Coletando telemetria local por 30 segundos."
                        )
                    )
                }

                try {
                    for (
                        second in 0..VALIDATION_DURATION_SECONDS
                    ) {
                        val current = _uiState.value
                        val audio = current.snapshot

                        if (
                            current.pipelineState !=
                                PipelineState.LISTENING ||
                            audio == null
                        ) {
                            throw IllegalStateException(
                                "A sessão de áudio deixou LISTENING durante o teste."
                            )
                        }

                        val elapsedMs =
                            (clock.nowNanos() -
                                startedAtNanos) /
                                1_000_000L

                        samples += DeviceValidationSample(
                            elapsedMs = elapsedMs,
                            engineState = audio.state,
                            processingMode =
                                audio.processingMode,
                            inputSampleRateHz =
                                audio.inputSampleRateHz,
                            outputSampleRateHz =
                                audio.outputSampleRateHz,
                            inputFrames = audio.inputFrames,
                            outputFrames = audio.outputFrames,
                            inputDroppedSamples =
                                audio.droppedInputSamples,
                            outputUnderrunSamples =
                                audio.outputUnderrunSamples,
                            inputXruns = audio.inputXruns,
                            outputXruns = audio.outputXruns,
                            monitoringEnabled =
                                audio.monitoringEnabled,
                            neuralState =
                                current.neuralTelemetry.state,
                            neuralModelId =
                                current.neuralTelemetry.modelId,
                            neuralRequiredSampleRateHz =
                                current.neuralTelemetry
                                    .requiredSampleRateHz,
                            neuralRealtimeFactor =
                                current.neuralTelemetry
                                    .realtimeFactor,
                            thermalLevel =
                                current.performanceSnapshot
                                    .thermalLevel,
                            thermalHeadroomForecast10s =
                                current.performanceSnapshot
                                    .thermalHeadroomForecast10s
                                    .value,
                            cpuHeadroomPercent =
                                current.performanceSnapshot
                                    .cpuHeadroomPercent
                                    .value,
                            batteryPercent =
                                current.performanceSnapshot
                                    .batteryPercent
                                    .value,
                            powerSaveMode =
                                current.performanceSnapshot
                                    .powerSaveMode
                                    .value,
                            processCpuPercent =
                                current.performanceSnapshot
                                    .processCpuPercent
                                    .value,
                            performanceTier =
                                current.performanceDecision
                                    ?.tier,
                            performanceForceFallback =
                                current.performanceDecision
                                    ?.forceFallback == true,
                            adaptiveControlActive =
                                current.neuralTelemetry
                                    .adaptiveControlActive,
                            adaptiveStrength =
                                current.neuralTelemetry
                                    .adaptiveStrength
                        )

                        _uiState.update {
                            it.copy(
                                validation =
                                    it.validation.copy(
                                        running = true,
                                        progress =
                                            second.toFloat() /
                                                VALIDATION_DURATION_SECONDS
                                                    .toFloat(),
                                        elapsedSeconds = second
                                    )
                            )
                        }

                        if (
                            second <
                                VALIDATION_DURATION_SECONDS
                        ) {
                            delay(1_000L)
                        }
                    }

                    val report =
                        validationEvaluator.evaluate(
                            requestedMode =
                                requestedMode,
                            samples = samples
                        )

                    _uiState.update {
                        it.copy(
                            validation =
                                DeviceValidationUiState(
                                    running = false,
                                    progress = 1f,
                                    elapsedSeconds =
                                        VALIDATION_DURATION_SECONDS,
                                    report = report,
                                    message =
                                        "Teste concluído sem salvar áudio."
                                )
                        )
                    }
                } catch (
                    cancelled: CancellationException
                ) {
                    throw cancelled
                } catch (error: Throwable) {
                    _uiState.update {
                        it.copy(
                            validation =
                                DeviceValidationUiState(
                                    running = false,
                                    message =
                                        error.message
                                            ?: "Falha no teste de validação."
                                )
                        )
                    }
                } finally {
                    validationJob = null
                }
            }
    }

    fun cancelValidation() {
        cancelValidation("Teste cancelado.")
    }

    private fun cancelValidation(
        message: String
    ) {
        if (validationJob?.isActive != true) return

        validationJob?.cancel()
        validationJob = null

        _uiState.update {
            it.copy(
                validation = DeviceValidationUiState(
                    running = false,
                    message = message
                )
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
        aiCoordinator.resetTelemetry()
    }

    private fun applyAdaptiveFeedback(
        feedback: AudioFeedback
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            adaptiveProfiles.applyFeedback(feedback)
        }
    }

    private fun publishAdaptiveControl(
        enabled: Boolean,
        profile: com.k410sh4.budsia.core.ai.adaptation.AdaptiveAudioProfile
    ) {
        aiCoordinator.configureAdaptiveControl(
            AdaptiveControlConfig(
                enabled = enabled,
                strength =
                    profile.preferredEnhancementStrength,
                profileRevision = profile.revision,
                environmentLabel =
                    profile.environment.displayName
            )
        )
    }

    private fun validationMutationAllowed(): Boolean {
        if (!_uiState.value.validation.running) return true

        _uiState.update {
            it.copy(
                errorMessage =
                    "Cancele o Device Validation Lab antes de alterar processamento, perfil ou política."
            )
        }
        return false
    }

    private fun routeSelectionAllowed(): Boolean {
        val allowed =
            _uiState.value.pipelineState == PipelineState.IDLE ||
                _uiState.value.pipelineState == PipelineState.ERROR

        if (!allowed) {
            _uiState.update {
                it.copy(
                    errorMessage =
                        "Pare o áudio antes de trocar entrada ou saída."
                )
            }
        }

        return allowed
    }

    private fun installedModelIdForRate(sampleRateHz: Int): String? {
        val preferred = AiModelCatalog.bestForSampleRate(sampleRateHz)
            ?: return null

        val installed = _uiState.value.modelStatuses
            .firstOrNull {
                it.descriptor.id == preferred.id
            }
            ?.state == ModelInstallState.INSTALLED

        return preferred.id.takeIf { installed }
    }

    private fun launchAiWorkerForCurrentRoute() {
        if (aiJob?.isActive == true) return

        val snapshot = realtimeAudioEngine.snapshot()
        val modelId = installedModelIdForRate(
            snapshot.inputSampleRateHz
        )

        if (modelId == null) {
            realtimeAudioEngine.setProcessingMode(
                RealtimeProcessingMode.DSP
            )
            _uiState.update {
                it.copy(
                    selectedMode = RealtimeProcessingMode.DSP,
                    activeModelId = null,
                    errorMessage =
                        "Sem modelo neural verificado para a rota de ${snapshot.inputSampleRateHz} Hz. O BudsIA manteve DSP."
                )
            }
            return
        }

        _uiState.update {
            it.copy(activeModelId = modelId)
        }

        aiJob = viewModelScope.launch(Dispatchers.Default) {
            aiCoordinator.run(modelId)
        }
    }

    companion object {
        private const val VALIDATION_DURATION_SECONDS = 30
    }

    override fun onCleared() {
        validationJob?.cancel()
        aiJob?.cancel()
        sessionJob?.cancel()
        realtimeAudioEngine.stop()
        routeController.release()
        super.onCleared()
    }
}
