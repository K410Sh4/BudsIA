package com.k410sh4.budsia.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.k410sh4.budsia.core.audio.model.PipelineState
import com.k410sh4.budsia.core.audio.realtime.RealtimeAudioConfig
import com.k410sh4.budsia.core.audio.realtime.RealtimeAudioEngine
import com.k410sh4.budsia.core.audio.realtime.RealtimeEngineState
import com.k410sh4.budsia.core.audio.realtime.RealtimeProcessingMode
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@HiltViewModel
class AudioFocusViewModel @Inject constructor(
    private val realtimeAudioEngine: RealtimeAudioEngine,
    private val routeMonitor: AudioRouteMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow(AudioFocusUiState())
    val uiState: StateFlow<AudioFocusUiState> = _uiState.asStateFlow()

    private var sessionJob: Job? = null

    fun start() {
        if (sessionJob?.isActive == true) return

        val mode = _uiState.value.selectedMode
        _uiState.value = _uiState.value.copy(
            pipelineState = PipelineState.STARTING,
            errorMessage = null
        )

        sessionJob = viewModelScope.launch(Dispatchers.Default) {
            val startResult = realtimeAudioEngine.start(
                RealtimeAudioConfig(processingMode = mode)
            )

            if (!startResult.success) {
                _uiState.value = _uiState.value.copy(
                    pipelineState = PipelineState.ERROR,
                    errorMessage = startResult.message
                        ?: "Falha ao iniciar o núcleo de áudio."
                )
                return@launch
            }

            try {
                while (isActive) {
                    val snapshot = realtimeAudioEngine.snapshot()

                    if (snapshot.state == RealtimeEngineState.ERROR) {
                        _uiState.value = _uiState.value.copy(
                            pipelineState = PipelineState.ERROR,
                            snapshot = snapshot,
                            errorMessage = realtimeAudioEngine.lastError()
                                ?: "A rota de áudio foi interrompida."
                        )
                        break
                    }

                    val ratesMatch =
                        snapshot.outputSampleRateHz > 0 &&
                            snapshot.outputSampleRateHz == snapshot.inputSampleRateHz

                    val canMonitor =
                        snapshot.outputAvailable &&
                            ratesMatch &&
                            routeMonitor.isPrivateOutput(snapshot.outputDeviceId)

                    _uiState.value = _uiState.value.copy(
                        pipelineState = PipelineState.LISTENING,
                        snapshot = snapshot,
                        inputRouteLabel = routeMonitor.inputLabel(
                            snapshot.inputDeviceId
                        ),
                        outputRouteLabel = if (snapshot.outputAvailable) {
                            routeMonitor.outputLabel(snapshot.outputDeviceId)
                        } else {
                            "Saída indisponível"
                        },
                        canMonitorOutput = canMonitor,
                        errorMessage = null
                    )

                    delay(200)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.value = _uiState.value.copy(
                    pipelineState = PipelineState.ERROR,
                    errorMessage = error.message
                        ?: error::class.java.simpleName
                )
            } finally {
                realtimeAudioEngine.stop()
                if (_uiState.value.pipelineState != PipelineState.ERROR) {
                    _uiState.value = _uiState.value.copy(
                        pipelineState = PipelineState.IDLE,
                        snapshot = null,
                        canMonitorOutput = false
                    )
                }
            }
        }
    }

    fun stop() {
        if (sessionJob?.isActive != true) return

        _uiState.value = _uiState.value.copy(
            pipelineState = PipelineState.STOPPING
        )
        sessionJob?.cancel()
        sessionJob = null
    }

    fun setProcessingMode(mode: RealtimeProcessingMode) {
        _uiState.value = _uiState.value.copy(
            selectedMode = mode,
            errorMessage = null
        )

        if (_uiState.value.pipelineState == PipelineState.LISTENING) {
            realtimeAudioEngine.setProcessingMode(mode)
        }
    }

    fun setMonitoring(enabled: Boolean) {
        if (enabled && !_uiState.value.canMonitorOutput) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Monitoramento ao vivo requer uma saída privada compatível (fones/USB/Bluetooth) com a mesma taxa de amostragem."
            )
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            val result = realtimeAudioEngine.setMonitoring(enabled)
            if (!result.success) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = result.message
                        ?: "Não foi possível alterar o monitoramento."
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = null
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    override fun onCleared() {
        sessionJob?.cancel()
        realtimeAudioEngine.stop()
        super.onCleared()
    }
}
