package com.k410sh4.budsia.feature.live

import com.k410sh4.budsia.core.audio.model.AudioPipelineSnapshot
import com.k410sh4.budsia.core.audio.model.PipelineState

data class AudioFocusUiState(
    val pipelineState: PipelineState = PipelineState.IDLE,
    val snapshot: AudioPipelineSnapshot? = null,
    val errorMessage: String? = null
)
