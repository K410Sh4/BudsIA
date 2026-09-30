package com.k410sh4.budsia.feature.live

import com.k410sh4.budsia.core.ai.adaptation.AdaptiveAudioProfile
import com.k410sh4.budsia.core.ai.enhancement.NeuralRuntimeTelemetry
import com.k410sh4.budsia.core.ai.models.ModelInstallStatus
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
    val modelStatus: ModelInstallStatus? = null,
    val neuralTelemetry: NeuralRuntimeTelemetry = NeuralRuntimeTelemetry(),
    val adaptiveProfiles: List<AdaptiveAudioProfile> = emptyList(),
    val activeAdaptiveProfile: AdaptiveAudioProfile? = null,
    val learningMessage: String? = null,
    val errorMessage: String? = null
)
