package com.k410sh4.budsia.feature.live

import com.k410sh4.budsia.core.audio.model.PipelineState
import com.k410sh4.budsia.core.audio.realtime.RealtimeAudioSnapshot
import com.k410sh4.budsia.core.audio.realtime.RealtimeProcessingMode

data class AudioFocusUiState(
    val pipelineState: PipelineState = PipelineState.IDLE,
    val snapshot: RealtimeAudioSnapshot? = null,
    val selectedMode: RealtimeProcessingMode = RealtimeProcessingMode.DSP,
    val inputRouteLabel: String = "—",
    val outputRouteLabel: String = "—",
    val canMonitorOutput: Boolean = false,
    val errorMessage: String? = null
)
