package com.k410sh4.budsia.feature.live

import com.k410sh4.budsia.core.ai.adaptation.AcousticEnvironment
import com.k410sh4.budsia.core.ai.adaptation.AdaptiveAudioProfile
import com.k410sh4.budsia.core.ai.enhancement.NeuralRuntimeTelemetry
import com.k410sh4.budsia.core.ai.models.ModelInstallStatus
import com.k410sh4.budsia.core.audio.model.PipelineState
import com.k410sh4.budsia.core.audio.realtime.RealtimeAudioSnapshot
import com.k410sh4.budsia.core.audio.realtime.RealtimeProcessingMode
import com.k410sh4.budsia.core.audio.routing.AudioDeviceDescriptor
import com.k410sh4.budsia.core.performance.AiPerformanceSnapshot

data class AudioFocusUiState(
    val pipelineState: PipelineState = PipelineState.IDLE,
    val snapshot: RealtimeAudioSnapshot? = null,
    val selectedMode: RealtimeProcessingMode = RealtimeProcessingMode.DSP,
    val availableInputs: List<AudioDeviceDescriptor> = emptyList(),
    val availableOutputs: List<AudioDeviceDescriptor> = emptyList(),
    val selectedInputDeviceId: Int = 0,
    val selectedOutputDeviceId: Int = 0,
    val preparedCommunicationMode: Boolean = false,
    val inputRouteLabel: String = "—",
    val outputRouteLabel: String = "—",
    val canMonitorOutput: Boolean = false,
    val modelStatuses: List<ModelInstallStatus> = emptyList(),
    val activeModelId: String? = null,
    val neuralTelemetry: NeuralRuntimeTelemetry = NeuralRuntimeTelemetry(),
    val adaptiveProfile: AdaptiveAudioProfile =
        AdaptiveAudioProfile.factory(AcousticEnvironment.GENERAL),
    val adaptiveControlCandidateEnabled: Boolean = false,
    val performance: AiPerformanceSnapshot =
        AiPerformanceSnapshot(),
    val performanceMessage: String? = null,
    val errorMessage: String? = null
)
