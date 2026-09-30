package com.k410sh4.budsia.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.k410sh4.budsia.core.audio.model.PipelineState
import com.k410sh4.budsia.core.audio.pipeline.AudioFocusPipeline
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

@HiltViewModel
class AudioFocusViewModel @Inject constructor(
    private val pipeline: AudioFocusPipeline
) : ViewModel() {

    private val _uiState = MutableStateFlow(AudioFocusUiState())
    val uiState: StateFlow<AudioFocusUiState> = _uiState.asStateFlow()

    private var captureJob: Job? = null

    fun start() {
        if (captureJob?.isActive == true) return

        captureJob = viewModelScope.launch {
            pipeline.snapshots()
                .onStart {
                    _uiState.value = AudioFocusUiState(
                        pipelineState = PipelineState.STARTING
                    )
                }
                .catch { error ->
                    if (error is CancellationException) throw error
                    _uiState.value = _uiState.value.copy(
                        pipelineState = PipelineState.ERROR,
                        errorMessage = error.message ?: error::class.java.simpleName
                    )
                }
                .onCompletion {
                    if (_uiState.value.pipelineState != PipelineState.ERROR) {
                        _uiState.value = _uiState.value.copy(
                            pipelineState = PipelineState.IDLE
                        )
                    }
                }
                .collect { snapshot ->
                    _uiState.value = AudioFocusUiState(
                        pipelineState = PipelineState.LISTENING,
                        snapshot = snapshot
                    )
                }
        }
    }

    fun stop() {
        if (captureJob?.isActive != true) return
        _uiState.value = _uiState.value.copy(
            pipelineState = PipelineState.STOPPING
        )
        captureJob?.cancel()
        captureJob = null
    }

    override fun onCleared() {
        captureJob?.cancel()
        super.onCleared()
    }
}
