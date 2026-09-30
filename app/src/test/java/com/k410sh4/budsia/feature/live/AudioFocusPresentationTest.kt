package com.k410sh4.budsia.feature.live

import com.k410sh4.budsia.core.ai.enhancement.NeuralPipelineState
import com.k410sh4.budsia.core.ai.enhancement.NeuralRuntimeTelemetry
import com.k410sh4.budsia.core.audio.model.PipelineState
import com.k410sh4.budsia.core.audio.realtime.RealtimeProcessingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFocusPresentationTest {

    @Test
    fun idleStateBecomesHumanFriendlyReadyState() {
        val layered = AudioFocusUiState().toLayeredUiState()

        assertEquals("Pronto para ouvir", layered.listening.title)
        assertEquals(HumanStatusTone.INACTIVE, layered.listening.tone)
        assertEquals("SEM RESULTADO", layered.lab.statusLabel)
        assertTrue(layered.developer.json.contains("\"schemaVersion\": 1"))
    }

    @Test
    fun neuralFallbackBecomesWarningInsteadOfRawTelemetryOnly() {
        val state = AudioFocusUiState(
            pipelineState = PipelineState.LISTENING,
            selectedMode = RealtimeProcessingMode.AI,
            neuralTelemetry = NeuralRuntimeTelemetry(
                state = NeuralPipelineState.FALLBACK,
                fallbackReason =
                    "Performance reduzida para controlar a temperatura do aparelho."
            )
        )

        val layered = state.toLayeredUiState()

        assertEquals(
            HumanStatusTone.WARNING,
            layered.listening.tone
        )
        assertEquals(
            "Proteção automática ativa",
            layered.listening.title
        )
        assertTrue(
            layered.diagnostics.notices.any {
                it.category == UiNoticeCategory.TEMPERATURE
            }
        )
        assertTrue(
            layered.developer.json.contains(
                "Performance reduzida para controlar a temperatura"
            )
        )
    }

    @Test
    fun runningAiHasSimpleHumanStatus() {
        val state = AudioFocusUiState(
            pipelineState = PipelineState.LISTENING,
            selectedMode = RealtimeProcessingMode.AI,
            neuralTelemetry = NeuralRuntimeTelemetry(
                state = NeuralPipelineState.RUNNING,
                realtimeFactor = 0.72
            )
        )

        val layered = state.toLayeredUiState()

        assertEquals("IA local ativa", layered.listening.title)
        assertEquals(HumanStatusTone.SUCCESS, layered.listening.tone)
        assertEquals(
            "Boa margem em tempo real",
            layered.listening.routeStabilityLabel
        )
    }
}
