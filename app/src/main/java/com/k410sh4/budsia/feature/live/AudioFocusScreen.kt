package com.k410sh4.budsia.feature.live

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.k410sh4.budsia.core.ai.enhancement.NeuralPipelineState
import com.k410sh4.budsia.core.ai.models.ModelInstallState
import com.k410sh4.budsia.core.audio.model.PipelineState
import com.k410sh4.budsia.core.audio.realtime.RealtimeProcessingMode
import com.k410sh4.budsia.core.audio.routing.AudioDeviceDescriptor
import java.util.Locale

@Composable
fun AudioFocusRoute(
    hasMicrophonePermission: Boolean,
    hasBluetoothConnectPermission: Boolean,
    onRequestMicrophonePermission: () -> Unit,
    onRequestBluetoothPermission: () -> Unit,
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
        hasBluetoothConnectPermission = hasBluetoothConnectPermission,
        onRequestMicrophonePermission = onRequestMicrophonePermission,
        onRequestBluetoothPermission = onRequestBluetoothPermission,
        onStart = viewModel::start,
        onStop = viewModel::stop,
        onModeSelected = viewModel::setProcessingMode,
        onInputSelected = viewModel::selectInputDevice,
        onOutputSelected = viewModel::selectOutputDevice,
        onMonitoringChanged = viewModel::setMonitoring,
        onInstallModel = viewModel::installAiModel,
        onRemoveModel = viewModel::removeAiModel
    )
}

@Composable
private fun AudioFocusScreen(
    state: AudioFocusUiState,
    hasMicrophonePermission: Boolean,
    hasBluetoothConnectPermission: Boolean,
    onRequestMicrophonePermission: () -> Unit,
    onRequestBluetoothPermission: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onModeSelected: (RealtimeProcessingMode) -> Unit,
    onInputSelected: (Int) -> Unit,
    onOutputSelected: (Int) -> Unit,
    onMonitoringChanged: (Boolean) -> Unit,
    onInstallModel: () -> Unit,
    onRemoveModel: () -> Unit
) {
    val active = state.pipelineState == PipelineState.LISTENING ||
        state.pipelineState == PipelineState.STARTING ||
        state.pipelineState == PipelineState.STOPPING
    val stopping = state.pipelineState == PipelineState.STOPPING
    val modelInstalled =
        state.modelStatus?.state == ModelInstallState.INSTALLED
    val aiRunning =
        state.neuralTelemetry.state == NeuralPipelineState.RUNNING

    val displayedWaveform =
        if (
            state.selectedMode == RealtimeProcessingMode.AI &&
            state.neuralTelemetry.enhancedWaveform.isNotEmpty()
        ) {
            state.neuralTelemetry.enhancedWaveform
        } else {
            state.snapshot?.waveform ?: List(72) { 0f }
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "ADAPTIVE AUDIO FOCUS",
            style = MaterialTheme.typography.titleLarge
        )

        Text(
            text = when {
                aiRunning -> "● MICROFONE ATIVO • IA LOCAL"
                active -> "● MICROFONE ATIVO • NATIVE OBOE"
                else -> "AUDIO CORE • LOCAL"
            },
            color = if (active) {
                Color(0xFF65F0A9)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = MaterialTheme.typography.labelLarge
        )

        WaveformCard(
            waveform = displayedWaveform,
            active = active,
            label = if (aiRunning) "SAÍDA NEURAL" else "SINAL"
        )

        ProcessingModeSelector(
            selectedMode = state.selectedMode,
            aiEnabled = modelInstalled,
            onModeSelected = onModeSelected
        )

        AiModelCard(
            state = state,
            onInstallModel = onInstallModel,
            onRemoveModel = onRemoveModel
        )

        if (
            state.neuralTelemetry.state != NeuralPipelineState.IDLE ||
            state.selectedMode == RealtimeProcessingMode.AI
        ) {
            NeuralDiagnosticsCard(state)
        }

        RealtimeDiagnosticsCard(state)

        RoutingCard(
            state = state,
            active = active,
            hasBluetoothConnectPermission =
                hasBluetoothConnectPermission,
            onRequestBluetoothPermission =
                onRequestBluetoothPermission,
            onInputSelected = onInputSelected,
            onOutputSelected = onOutputSelected,
            onMonitoringChanged = onMonitoringChanged
        )

        state.errorMessage?.let {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = it,
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (!hasMicrophonePermission) {
            Button(
                onClick = onRequestMicrophonePermission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Mic, contentDescription = null)
                androidx.compose.foundation.layout.Spacer(
                    modifier = Modifier.size(8.dp)
                )
                Text("Permitir microfone")
            }
        } else {
            Button(
                onClick = if (active) onStop else onStart,
                modifier = Modifier.fillMaxWidth(),
                enabled = !stopping,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (active) {
                        Color(0xFF5A1E28)
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            ) {
                Icon(
                    imageVector = if (active) {
                        Icons.Rounded.Stop
                    } else {
                        Icons.Rounded.Mic
                    },
                    contentDescription = null
                )
                androidx.compose.foundation.layout.Spacer(
                    modifier = Modifier.size(8.dp)
                )
                Text(
                    when {
                        stopping -> "ENCERRANDO..."
                        active -> "PARAR"
                        else -> "INICIAR ÁUDIO"
                    }
                )
            }
        }

        Text(
            text = "O áudio bruto não é salvo. O modelo é verificado por tamanho e SHA-256 antes de ser usado.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun ProcessingModeSelector(
    selectedMode: RealtimeProcessingMode,
    aiEnabled: Boolean,
    onModeSelected: (RealtimeProcessingMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FilterChip(
            selected = selectedMode == RealtimeProcessingMode.RAW,
            onClick = { onModeSelected(RealtimeProcessingMode.RAW) },
            label = { Text("ORIGINAL") }
        )
        FilterChip(
            selected = selectedMode == RealtimeProcessingMode.DSP,
            onClick = { onModeSelected(RealtimeProcessingMode.DSP) },
            label = { Text("DSP") }
        )
        FilterChip(
            selected = selectedMode == RealtimeProcessingMode.AI,
            onClick = { onModeSelected(RealtimeProcessingMode.AI) },
            enabled = aiEnabled,
            label = { Text("IA") }
        )
    }
}

@Composable
private fun AiModelCard(
    state: AudioFocusUiState,
    onInstallModel: () -> Unit,
    onRemoveModel: () -> Unit
) {
    val status = state.modelStatus

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
                text = "MODELO NEURAL",
                style = MaterialTheme.typography.labelLarge
            )

            MetricRow(
                "Modelo",
                status?.descriptor?.displayName ?: "DPDFNet2 48 kHz HR"
            )
            MetricRow(
                "Perfil",
                status?.descriptor?.qualityTier?.name ?: "MAX_QUALITY"
            )
            MetricRow(
                "Estado",
                status?.state?.name ?: "VERIFICANDO"
            )
            MetricRow(
                "Tamanho",
                status?.descriptor?.sizeBytes?.let(::formatBytes)
                    ?: "10.1 MiB"
            )

            when (status?.state) {
                ModelInstallState.DOWNLOADING,
                ModelInstallState.VERIFYING -> {
                    LinearProgressIndicator(
                        progress = { status.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = if (status.state == ModelInstallState.VERIFYING) {
                            "Verificando integridade..."
                        } else {
                            "${(status.progress * 100).toInt()}%"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }

                ModelInstallState.INSTALLED -> {
                    OutlinedButton(
                        onClick = onRemoveModel,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = null
                        )
                        androidx.compose.foundation.layout.Spacer(
                            modifier = Modifier.size(8.dp)
                        )
                        Text("Remover modelo")
                    }
                }

                else -> {
                    Button(
                        onClick = onInstallModel,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = null
                        )
                        androidx.compose.foundation.layout.Spacer(
                            modifier = Modifier.size(8.dp)
                        )
                        Text("Instalar IA local")
                    }
                }
            }

            status?.errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun NeuralDiagnosticsCard(
    state: AudioFocusUiState
) {
    val ai = state.neuralTelemetry
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
                text = "IA STREAMING",
                style = MaterialTheme.typography.labelLarge
            )
            MetricRow("Estado", ai.state.name)
            MetricRow("Runtime", ai.engineId ?: "—")
            MetricRow("Provider", ai.provider?.uppercase() ?: "—")
            MetricRow(
                "Frame",
                ai.frameSamples?.let { "$it samples" } ?: "—"
            )
            MetricRow(
                "Inferência atual",
                ai.inferenceMs?.let(::formatMs) ?: "—"
            )
            MetricRow(
                "Inferência média",
                ai.averageInferenceMs?.let(::formatMs) ?: "—"
            )
            MetricRow(
                "Inferência máxima",
                ai.maxInferenceMs?.let(::formatMs) ?: "—"
            )
            MetricRow(
                "RTF móvel",
                ai.realtimeFactor?.let {
                    "%.2f×".format(Locale.US, it)
                } ?: "—"
            )
            MetricRow(
                "Frames IA",
                ai.chunksProcessed.toString()
            )
            MetricRow(
                "Drops entrada IA",
                snapshot?.aiInputDroppedSamples?.toString() ?: "—"
            )
            MetricRow(
                "Samples melhorados",
                ai.samplesEnhanced.toString()
            )
            MetricRow(
                "RMS saída",
                ai.enhancedRms?.let {
                    "%.4f".format(Locale.US, it)
                } ?: "—"
            )

            ai.fallbackReason?.let {
                Text(
                    text = "Fallback para DSP: $it",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun RealtimeDiagnosticsCard(
    state: AudioFocusUiState
) {
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
                "Worker atual",
                snapshot?.lastProcessorMs?.let(::formatMs) ?: "—"
            )
            MetricRow(
                "Worker máximo",
                snapshot?.maxProcessorMs?.let(::formatMs) ?: "—"
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
                    ?.let {
                        "%.2f %%".format(Locale.US, it * 100f)
                    }
                    ?: "—"
            )
        }
    }
}

@Composable
private fun RoutingCard(
    state: AudioFocusUiState,
    active: Boolean,
    hasBluetoothConnectPermission: Boolean,
    onRequestBluetoothPermission: () -> Unit,
    onInputSelected: (Int) -> Unit,
    onOutputSelected: (Int) -> Unit,
    onMonitoringChanged: (Boolean) -> Unit
) {
    val snapshot = state.snapshot
    val selectedInput = state.availableInputs
        .firstOrNull { it.id == state.selectedInputDeviceId }
    val bluetoothInputSelected = selectedInput?.isBluetooth == true

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "ROTEAMENTO",
                style = MaterialTheme.typography.labelLarge
            )

            Text(
                text = "Entrada",
                style = MaterialTheme.typography.labelMedium
            )
            RouteSelector(
                devices = state.availableInputs,
                selectedDeviceId = state.selectedInputDeviceId,
                defaultLabel = "Automática",
                enabled = !active,
                onSelected = onInputSelected
            )

            Text(
                text = "Saída",
                style = MaterialTheme.typography.labelMedium
            )
            RouteSelector(
                devices = state.availableOutputs,
                selectedDeviceId = state.selectedOutputDeviceId,
                defaultLabel = "Automática",
                enabled = !active,
                onSelected = onOutputSelected
            )

            if (
                bluetoothInputSelected &&
                !hasBluetoothConnectPermission
            ) {
                Button(
                    onClick = onRequestBluetoothPermission,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Permitir dispositivos próximos")
                }
            }

            if (state.preparedCommunicationMode) {
                Text(
                    text = "Rota de comunicação Bluetooth preparada pelo Android.",
                    color = Color(0xFF65F0A9),
                    fontSize = 12.sp
                )
            }

            HorizontalDivider()

            MetricRow("Entrada ativa", state.inputRouteLabel)
            MetricRow("Saída ativa", state.outputRouteLabel)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Ouvir áudio processado")
                    Text(
                        text = if (state.canMonitorOutput) {
                            "Saída privada compatível detectada."
                        } else {
                            "Bloqueado para evitar feedback ou rota incompatível."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = snapshot?.monitoringEnabled == true,
                    onCheckedChange = onMonitoringChanged,
                    enabled = active &&
                        (
                            state.canMonitorOutput ||
                                snapshot?.monitoringEnabled == true
                            )
                )
            }
        }
    }
}

@Composable
private fun RouteSelector(
    devices: List<AudioDeviceDescriptor>,
    selectedDeviceId: Int,
    defaultLabel: String,
    enabled: Boolean,
    onSelected: (Int) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilterChip(
            selected = selectedDeviceId == 0,
            onClick = { onSelected(0) },
            enabled = enabled,
            label = { Text(defaultLabel) }
        )

        devices.forEach { device ->
            FilterChip(
                selected = selectedDeviceId == device.id,
                onClick = { onSelected(device.id) },
                enabled = enabled,
                label = {
                    Text(
                        text = buildString {
                            append(device.productName)
                            append(" • ")
                            append(device.typeLabel)
                            if (device.isBluetooth) {
                                append(" • BT")
                            }
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun WaveformCard(
    waveform: List<Float>,
    active: Boolean,
    label: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
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
                        val half =
                            size.height * 0.42f *
                                amplitude.coerceIn(0f, 1f)
                        val x =
                            spacing * index + spacing / 2f
                        drawLine(
                            color = lineColor,
                            start = Offset(
                                x,
                                size.height / 2f - half
                            ),
                            end = Offset(
                                x,
                                size.height / 2f + half
                            ),
                            strokeWidth =
                                maxOf(2f, spacing * 0.32f),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
}

private fun formatMs(value: Double): String =
    "%.3f ms".format(Locale.US, value)

private fun formatBytes(bytes: Long): String =
    "%.1f MiB".format(
        Locale.US,
        bytes.toDouble() / (1024.0 * 1024.0)
    )

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
