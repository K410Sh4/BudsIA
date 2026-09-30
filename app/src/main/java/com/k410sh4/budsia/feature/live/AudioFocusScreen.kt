package com.k410sh4.budsia.feature.live

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.k410sh4.budsia.core.audio.model.PipelineState
import com.k410sh4.budsia.core.audio.realtime.RealtimeProcessingMode
import java.util.Locale

@Composable
fun AudioFocusRoute(
    hasMicrophonePermission: Boolean,
    onRequestMicrophonePermission: () -> Unit,
    viewModel: AudioFocusViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.stop()
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    AudioFocusScreen(
        state = state,
        hasMicrophonePermission = hasMicrophonePermission,
        onRequestMicrophonePermission = onRequestMicrophonePermission,
        onStart = viewModel::start,
        onStop = viewModel::stop,
        onModeSelected = viewModel::setProcessingMode,
        onMonitoringChanged = viewModel::setMonitoring
    )
}

@Composable
private fun AudioFocusScreen(
    state: AudioFocusUiState,
    hasMicrophonePermission: Boolean,
    onRequestMicrophonePermission: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onModeSelected: (RealtimeProcessingMode) -> Unit,
    onMonitoringChanged: (Boolean) -> Unit
) {
    val active = state.pipelineState == PipelineState.LISTENING ||
        state.pipelineState == PipelineState.STARTING

    val snapshot = state.snapshot

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "ADAPTIVE AUDIO FOCUS",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = if (active) {
                "● MICROFONE ATIVO • NATIVE OBOE"
            } else {
                "NATIVE AUDIO CORE • LOCAL"
            },
            color = if (active) Color(0xFF65F0A9)
            else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge
        )

        WaveformCard(
            waveform = snapshot?.waveform ?: List(72) { 0f },
            active = active
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilterChip(
                selected = state.selectedMode == RealtimeProcessingMode.RAW,
                onClick = { onModeSelected(RealtimeProcessingMode.RAW) },
                label = { Text("ORIGINAL") }
            )
            FilterChip(
                selected = state.selectedMode == RealtimeProcessingMode.DSP,
                onClick = { onModeSelected(RealtimeProcessingMode.DSP) },
                label = { Text("DSP") }
            )
        }

        DiagnosticsCard(state)

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "ROTEAMENTO",
                    style = MaterialTheme.typography.labelLarge
                )
                MetricRow("Entrada", state.inputRouteLabel)
                MetricRow("Saída", state.outputRouteLabel)
                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Monitorar áudio processado")
                        Text(
                            text = if (state.canMonitorOutput) {
                                "Disponível para saída privada detectada."
                            } else {
                                "Bloqueado para evitar feedback acústico ou rota incompatível."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = snapshot?.monitoringEnabled == true,
                        onCheckedChange = onMonitoringChanged,
                        enabled = active && (state.canMonitorOutput || snapshot?.monitoringEnabled == true)
                    )
                }
            }
        }

        state.errorMessage?.let {
            Text(
                text = "Erro: $it",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        if (!hasMicrophonePermission) {
            Button(
                onClick = onRequestMicrophonePermission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Mic, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Permitir microfone")
            }
        } else {
            Button(
                onClick = if (active) onStop else onStart,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (active) {
                        Color(0xFF5A1E28)
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            ) {
                Icon(
                    imageVector = if (active) Icons.Rounded.Stop else Icons.Rounded.Mic,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(if (active) "PARAR" else "INICIAR ÁUDIO")
            }
        }

        Text(
            text = "O áudio bruto não é salvo. Ao sair da tela, a captura é encerrada.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun DiagnosticsCard(state: AudioFocusUiState) {
    val snapshot = state.snapshot

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "DIAGNÓSTICO REALTIME",
                style = MaterialTheme.typography.labelLarge
            )
            MetricRow(
                "Input",
                snapshot?.inputSampleRateHz
                    ?.takeIf { it > 0 }
                    ?.let { "$it Hz • ${snapshot.inputSharingMode.name}" }
                    ?: "—"
            )
            MetricRow(
                "Output",
                snapshot?.outputSampleRateHz
                    ?.takeIf { it > 0 }
                    ?.let { "$it Hz • ${snapshot.outputSharingMode.name}" }
                    ?: "—"
            )
            MetricRow(
                "DSP atual",
                snapshot?.lastProcessorMs?.let { formatMs(it) } ?: "—"
            )
            MetricRow(
                "DSP máximo",
                snapshot?.maxProcessorMs?.let { formatMs(it) } ?: "—"
            )
            MetricRow(
                "Input xruns",
                snapshot?.inputXruns?.toString() ?: "UNKNOWN"
            )
            MetricRow(
                "Output xruns",
                snapshot?.outputXruns?.toString() ?: "UNKNOWN"
            )
            MetricRow(
                "Input drops",
                snapshot?.droppedInputSamples?.toString() ?: "—"
            )
            MetricRow(
                "Output underruns",
                snapshot?.outputUnderrunSamples?.toString() ?: "—"
            )
            MetricRow(
                "Clipping",
                snapshot?.processedMetrics?.clippingRatio
                    ?.let { "%.2f %%".format(Locale.US, it * 100f) }
                    ?: "—"
            )
        }
    }
}

private fun formatMs(value: Double): String =
    "%.3f ms".format(Locale.US, value)

@Composable
private fun WaveformCard(
    waveform: List<Float>,
    active: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            contentAlignment = Alignment.Center
        ) {
            val lineColor = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                if (waveform.isEmpty()) return@Canvas
                val spacing = size.width / waveform.size
                waveform.forEachIndexed { index, amplitude ->
                    val half = size.height * 0.42f * amplitude.coerceIn(0f, 1f)
                    val x = spacing * index + spacing / 2f
                    drawLine(
                        color = lineColor,
                        start = Offset(x, size.height / 2f - half),
                        end = Offset(x, size.height / 2f + half),
                        strokeWidth = maxOf(2f, spacing * 0.32f),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
