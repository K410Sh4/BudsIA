package com.k410sh4.budsia.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.k410sh4.budsia.core.ai.adaptation.AdaptiveFeedback
import com.k410sh4.budsia.core.ai.adaptation.AdaptiveProfileController
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
    private val adaptiveProfileController: AdaptiveProfileController
) : ViewModel() {

    private val modelId = AiModelCatalog.DPDFNET2_48K_HR.id

    private val _uiState = MutableStateFlow(AudioFocusUiState())
    val uiState: StateFlow<AudioFocusUiState> = _uiState.asStateFlow()

    private var sessionJob: Job? = null
    private var aiJob: Job? = null
    private var modelJob: Job? = null
    private var learningJob: Job? = null

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
                _uiState.update {
                    it.copy(modelStatus = statuses[modelId])
                }
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
                        neuralTelemetry = telemetry,
                        errorMessage = telemetry.errorMessage
                            ?: current.errorMessage
                    )
                }
            }
        }

        viewModelScope.launch {
            adaptiveProfileController.profiles.collect { profiles ->
                _uiState.update {
                    it.copy(adaptiveProfiles = profiles)
                }
            }
        }

        viewModelScope.launch {
            adaptiveProfileController.activeProfile.collect { profile ->
                _uiState.update {
                    it.copy(activeAdaptiveProfile = profile)
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
            current.modelStatus?.state != ModelInstallState.INSTALLED
        ) {
            _uiState.update {
                it.copy(
                    selectedMode = RealtimeProcessingMode.DSP,
                    errorMessage =
                        "Instale e verifique o modelo neural antes de iniciar o modo IA."
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
                errorMessage = null,
                learningMessage = null
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

            // Model loading/checks can take longer than the realtime buffer.
            // Start deterministically in DSP and switch to AI only after the
            // verified model is fully prepared by StreamingAiCoordinator.
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
                launchAiWorker()
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
                    if (it.pipelineState == PipelineState.ERROR) {
                        it.copy(
                            snapshot = null,
                            canMonitorOutput = false,
                            preparedCommunicationMode = false
                        )
                    } else {
                        it.copy(
                            pipelineState = PipelineState.IDLE,
                            snapshot = null,
                            canMonitorOutput = false,
                            preparedCommunicationMode = false
                        )
                    }
                }
                sessionJob = null
            }
        }
    }

    fun stop() {
        if (sessionJob == null) return

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
        if (
            mode == RealtimeProcessingMode.AI &&
            _uiState.value.modelStatus?.state != ModelInstallState.INSTALLED
        ) {
            _uiState.update {
                it.copy(
                    errorMessage =
                        "O modo IA exige o modelo DPDFNet2 verificado."
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                selectedMode = mode,
                errorMessage = null,
                learningMessage = null
            )
        }

        if (_uiState.value.pipelineState != PipelineState.LISTENING) {
            return
        }

        if (mode == RealtimeProcessingMode.AI) {
            launchAiWorker()
        } else {
            aiJob?.cancel()
            aiJob = null
            realtimeAudioEngine.setProcessingMode(mode)
        }
    }

    fun selectAdaptiveProfile(profileId: String) {
        if (learningJob?.isActive == true) return

        learningJob = viewModelScope.launch(Dispatchers.IO) {
            val result = adaptiveProfileController.activate(profileId)
            _uiState.update {
                it.copy(
                    learningMessage = if (result.isSuccess) {
                        "Perfil acústico atualizado."
                    } else {
                        null
                    },
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun teachAi(feedback: AdaptiveFeedback) {
        if (
            _uiState.value.selectedMode != RealtimeProcessingMode.AI ||
            _uiState.value.neuralTelemetry.state != NeuralPipelineState.RUNNING
        ) {
            _uiState.update {
                it.copy(
                    errorMessage =
                        "Use o modo IA em execução antes de ensinar o perfil."
                )
            }
            return
        }

        if (learningJob?.isActive == true) return

        learningJob = viewModelScope.launch(Dispatchers.IO) {
            val result = adaptiveProfileController.teach(
                feedback = feedback,
                modelId = modelId
            )

            _uiState.update {
                it.copy(
                    learningMessage = result.getOrNull()?.let { profile ->
                        "Aprendido em ${profile.name}: intensidade neural ${(profile.neuralMix * 100f).toInt()}%."
                    },
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun setMonitoring(enabled: Boolean) {
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
        if (modelJob?.isActive == true) return

        modelJob = viewModelScope.launch(Dispatchers.IO) {
            val result = modelManager.download(modelId)
            if (result.isFailure) {
                _uiState.update {
                    it.copy(
                        errorMessage =
                            result.exceptionOrNull()?.message
                                ?: "Falha ao instalar o modelo neural."
                    )
                }
            }
        }
    }

    fun removeAiModel() {
        if (modelJob?.isActive == true) return

        if (
            _uiState.value.selectedMode == RealtimeProcessingMode.AI ||
            aiJob?.isActive == true
        ) {
            _uiState.update {
                it.copy(
                    errorMessage =
                        "Pare o modo IA antes de remover o modelo."
                )
            }
            return
        }

        modelJob = viewModelScope.launch(Dispatchers.IO) {
            modelManager.remove(modelId)
        }
    }

    fun clearError() {
        _uiState.update {
            it.copy(
                errorMessage = null,
                learningMessage = null
            )
        }
        aiCoordinator.resetTelemetry()
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

    private fun launchAiWorker() {
        if (aiJob?.isActive == true) return

        aiJob = viewModelScope.launch(Dispatchers.Default) {
            aiCoordinator.run(modelId)
        }
    }

    override fun onCleared() {
        learningJob?.cancel()
        aiJob?.cancel()
        sessionJob?.cancel()
        realtimeAudioEngine.stop()
        routeController.release()
        super.onCleared()
    }
}
