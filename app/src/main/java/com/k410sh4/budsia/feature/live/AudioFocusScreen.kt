package com.k410sh4.budsia.feature.live

import android.media.AudioDeviceInfo
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.k410sh4.budsia.core.ai.adaptation.AcousticEnvironment
import com.k410sh4.budsia.core.ai.adaptation.AdaptiveAudioProfile
import com.k410sh4.budsia.core.ai.enhancement.NeuralPipelineState
import com.k410sh4.budsia.core.ai.evaluation.AdaptiveAbChoice
import com.k410sh4.budsia.core.ai.evaluation.AdaptiveAbPreferenceStatus
import com.k410sh4.budsia.core.ai.evaluation.AdaptiveAbVariant
import com.k410sh4.budsia.core.ai.models.ModelInstallState
import com.k410sh4.budsia.core.audio.model.PipelineState
import com.k410sh4.budsia.core.audio.realtime.RealtimeProcessingMode
import com.k410sh4.budsia.core.audio.routing.AudioDeviceDescriptor
import com.k410sh4.budsia.core.performance.AiPerformanceSettings
import com.k410sh4.budsia.core.performance.MeasurementKind
import com.k410sh4.budsia.core.validation.ValidationStatus
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
        onRemoveModel = viewModel::removeAiModel,
        onEnvironmentSelected = viewModel::selectEnvironment,
        onPreferredStrengthChanged = viewModel::setPreferredStrength,
        onMoreFilter = viewModel::teachMoreFilter,
        onMoreNatural = viewModel::teachMoreNatural,
        onGoodAsIs = viewModel::teachGoodAsIs,
        onResetProfile = viewModel::resetAdaptiveProfile,
        onAdaptiveControlCandidateChanged =
            viewModel::setAdaptiveControlCandidateEnabled,
        onLowBatteryAutoFallbackChanged =
            viewModel::setLowBatteryAutoFallbackEnabled,
        onStopAiBelowBatteryPercentChanged =
            viewModel::setStopAiBelowBatteryPercent,
        onAuditionFactory = viewModel::auditionAdaptiveFactory,
        onAuditionCandidate = viewModel::auditionAdaptiveCandidate,
        onRecordAbChoice = viewModel::recordAdaptiveAbChoice,
        onResetAbEvaluation = viewModel::resetAdaptiveAbEvaluation,
        onStartValidation = viewModel::startValidation,
        onCancelValidation = viewModel::cancelValidation
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
    onRemoveModel: () -> Unit,
    onEnvironmentSelected: (AcousticEnvironment) -> Unit,
    onPreferredStrengthChanged: (Float) -> Unit,
    onMoreFilter: () -> Unit,
    onMoreNatural: () -> Unit,
    onGoodAsIs: () -> Unit,
    onResetProfile: () -> Unit,
    onAdaptiveControlCandidateChanged: (Boolean) -> Unit,
    onLowBatteryAutoFallbackChanged: (Boolean) -> Unit,
    onStopAiBelowBatteryPercentChanged: (Int) -> Unit,
    onAuditionFactory: () -> Unit,
    onAuditionCandidate: () -> Unit,
    onRecordAbChoice: (AdaptiveAbChoice) -> Unit,
    onResetAbEvaluation: () -> Unit,
    onStartValidation: () -> Unit,
    onCancelValidation: () -> Unit
) {
    val active =
        state.pipelineState == PipelineState.LISTENING ||
            state.pipelineState == PipelineState.STARTING ||
            state.pipelineState == PipelineState.STOPPING
    val stopping = state.pipelineState == PipelineState.STOPPING
    val modelInstalled =
        state.modelStatuses.any {
            it.state == ModelInstallState.INSTALLED
        }
    val layered = remember(state) {
        state.toLayeredUiState()
    }
    var section by remember {
        androidx.compose.runtime.mutableStateOf(
            AudioFocusSection.LISTENING
        )
    }

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
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "BudsIA",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Áudio adaptativo local",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        AudioFocusSectionNavigation(
            selected = section,
            onSelected = { section = it }
        )

        when (section) {
            AudioFocusSection.LISTENING -> {
                HumanStatusCard(layered.listening)

                WaveformCard(
                    waveform = displayedWaveform,
                    active = active,
                    label = if (layered.listening.aiActive) {
                        "SAÍDA NEURAL"
                    } else {
                        "SINAL"
                    }
                )

                ProcessingModeSelector(
                    selectedMode = state.selectedMode,
                    aiEnabled = modelInstalled,
                    onModeSelected = onModeSelected
                )

                HumanQuickSummaryCard(
                    listening = layered.listening,
                    selectedMode = state.selectedMode
                )

                state.errorMessage?.let {
                    HumanAlertCard(
                        category = "Sistema",
                        title = "Atenção",
                        detail = it,
                        severity = UiNoticeSeverity.ERROR
                    )
                }

                if (!hasMicrophonePermission) {
                    Button(
                        onClick = onRequestMicrophonePermission,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Rounded.Mic,
                            contentDescription = null
                        )
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
                            containerColor =
                                if (active) {
                                    Color(0xFF5A1E28)
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                        )
                    ) {
                        Icon(
                            imageVector =
                                if (active) {
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
                    text =
                        "O áudio bruto não é salvo. A tela principal mostra apenas o necessário; telemetria detalhada fica em Diagnóstico e Dev/IA.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            AudioFocusSection.ROUTES -> {
                SectionIntro(
                    title = "Rotas de áudio",
                    detail =
                        "Escolha entrada e saída com nomes humanos. O identificador técnico continua disponível abaixo de cada dispositivo."
                )

                RouteHumanSummaryCard(layered.routing)

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
            }

            AudioFocusSection.MODELS -> {
                SectionIntro(
                    title = "IA e modelos",
                    detail =
                        "Modelos, perfil adaptativo e comparação A/B ficam juntos sem poluir a experiência de escuta."
                )

                ModelHumanSummaryCard(layered.models)

                AiModelCard(
                    state = state,
                    onInstallModel = onInstallModel,
                    onRemoveModel = onRemoveModel
                )

                AdaptiveProfileCard(
                    state = state,
                    onEnvironmentSelected = onEnvironmentSelected,
                    onPreferredStrengthChanged =
                        onPreferredStrengthChanged,
                    onMoreFilter = onMoreFilter,
                    onMoreNatural = onMoreNatural,
                    onGoodAsIs = onGoodAsIs,
                    onResetProfile = onResetProfile,
                    onAdaptiveControlCandidateChanged =
                        onAdaptiveControlCandidateChanged
                )

                AdaptiveAbEvaluationCard(
                    state = state,
                    onAuditionFactory = onAuditionFactory,
                    onAuditionCandidate = onAuditionCandidate,
                    onRecordChoice = onRecordAbChoice,
                    onReset = onResetAbEvaluation
                )
            }

            AudioFocusSection.DIAGNOSTICS -> {
                SectionIntro(
                    title = "Diagnóstico técnico",
                    detail =
                        "Aqui ficam RTF, XRuns, CPU, térmico, ADPF e motivos de fallback. Esta área é técnica por design."
                )

                HumanNoticesCard(
                    notices = layered.diagnostics.notices
                )

                PerformanceCard(
                    state = state,
                    onLowBatteryAutoFallbackChanged =
                        onLowBatteryAutoFallbackChanged,
                    onStopAiBelowBatteryPercentChanged =
                        onStopAiBelowBatteryPercentChanged
                )

                if (
                    state.neuralTelemetry.state !=
                        NeuralPipelineState.IDLE ||
                    state.selectedMode ==
                        RealtimeProcessingMode.AI
                ) {
                    NeuralDiagnosticsCard(state)
                }

                RealtimeDiagnosticsCard(state)
            }

            AudioFocusSection.LAB -> {
                SectionIntro(
                    title = "Validation Lab",
                    detail =
                        "Testes reproduzíveis de 30 segundos ficam isolados da experiência normal. PASS/WARN/FAIL mede estabilidade técnica, não qualidade acústica."
                )

                LabHumanSummaryCard(layered.lab)

                DeviceValidationCard(
                    state = state,
                    onStart = onStartValidation,
                    onCancel = onCancelValidation
                )
            }

            AudioFocusSection.DEVELOPER -> {
                SectionIntro(
                    title = "Desenvolvedor / IA",
                    detail =
                        "Snapshot estruturado da sessão para auditoria, automação e análise por IA. Nenhum áudio bruto faz parte deste estado."
                )

                DeveloperSnapshotCard(layered.developer)
            }
        }
    }
}

@Composable
private fun AudioFocusSectionNavigation(
    selected: AudioFocusSection,
    onSelected: (AudioFocusSection) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(AudioFocusSection.entries) { section ->
            FilterChip(
                selected = selected == section,
                onClick = { onSelected(section) },
                label = { Text(section.label) }
            )
        }
    }
}

@Composable
private fun SectionIntro(
    title: String,
    detail: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = detail,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun HumanStatusCard(
    listening: ListeningUiState
) {
    val accent = humanToneColor(listening.tone)
    Card(
        colors = CardDefaults.cardColors(
            containerColor =
                when (listening.tone) {
                    HumanStatusTone.ERROR ->
                        MaterialTheme.colorScheme.errorContainer
                    else ->
                        MaterialTheme.colorScheme.surface
                }
        ),
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = listening.title,
                        style =
                            MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = listening.detail,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                        style =
                            MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    text = statusSymbol(listening.tone),
                    color = accent,
                    fontSize = 28.sp
                )
            }

            HorizontalDivider()

            MetricRow("Entrada", listening.inputLabel)
            MetricRow("Saída", listening.outputLabel)
            MetricRow(
                "Áudio processado",
                listening.monitoringLabel
            )
            MetricRow(
                "Estabilidade",
                listening.routeStabilityLabel
            )
        }
    }
}

@Composable
private fun HumanQuickSummaryCard(
    listening: ListeningUiState,
    selectedMode: RealtimeProcessingMode
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "RESUMO",
                style = MaterialTheme.typography.labelLarge
            )
            MetricRow(
                "IA",
                if (listening.aiActive) {
                    "Ativa localmente"
                } else if (
                    selectedMode == RealtimeProcessingMode.AI
                ) {
                    "Selecionada · aguardando"
                } else {
                    "Desativada"
                }
            )
            MetricRow(
                "Captura",
                if (listening.captureActive) {
                    "Ativa"
                } else {
                    "Parada"
                }
            )
            MetricRow(
                "Ambiente",
                listening.environmentLabel
            )
            listening.fallbackReason?.let {
                Text(
                    text = "Proteção: $it",
                    color = Color(0xFFFFC857),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun RouteHumanSummaryCard(
    routing: RoutingUiState
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "ROTA SELECIONADA",
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = routing.inputHumanLabel,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = routing.inputTechnicalLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            HorizontalDivider()
            Text(
                text = routing.outputHumanLabel,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = routing.outputTechnicalLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            if (routing.communicationMode) {
                Text(
                    text =
                        "Rota bidirecional de comunicação controlada pelo Android.",
                    color = Color(0xFF65F0A9),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ModelHumanSummaryCard(
    models: ModelsUiState
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = models.activeModelName,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = models.selectionReason,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            MetricRow(
                "Pacote neural",
                "${models.installedCount}/${models.totalCount} instalados"
            )
        }
    }
}

@Composable
private fun LabHumanSummaryCard(
    lab: LabUiState
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MetricRow("Estado", lab.statusLabel)
            Text(
                text = lab.summary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun HumanNoticesCard(
    notices: List<UiNotice>
) {
    if (notices.isEmpty()) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor =
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Nenhum alerta técnico ativo.",
                modifier = Modifier.padding(16.dp),
                color = Color(0xFF65F0A9)
            )
        }
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        notices.forEach { notice ->
            HumanAlertCard(
                category = notice.category.displayName,
                title = notice.title,
                detail = notice.detail,
                severity = notice.severity
            )
        }
    }
}

@Composable
private fun HumanAlertCard(
    category: String,
    title: String,
    detail: String,
    severity: UiNoticeSeverity
) {
    val accent = when (severity) {
        UiNoticeSeverity.INFO ->
            MaterialTheme.colorScheme.primary
        UiNoticeSeverity.WARNING ->
            Color(0xFFFFC857)
        UiNoticeSeverity.ERROR ->
            MaterialTheme.colorScheme.error
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = category.uppercase(),
                color = accent,
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = detail,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun DeveloperSnapshotCard(
    developer: DeveloperUiState
) {
    val clipboard =
        androidx.compose.ui.platform.LocalClipboardManager.current

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
                text = "ADAPTIVE SESSION STATE · SCHEMA V1",
                style = MaterialTheme.typography.labelLarge
            )

            Text(
                text =
                    "Estado agregado para diagnóstico por IA. Contém rota, inferência, performance e validação; não contém áudio bruto.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )

            OutlinedButton(
                onClick = {
                    clipboard.setText(
                        androidx.compose.ui.text.AnnotatedString(
                            developer.json
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Copiar snapshot JSON")
            }

            androidx.compose.foundation.text.selection.SelectionContainer {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor =
                            MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = developer.json,
                        modifier = Modifier.padding(12.dp),
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily =
                            androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

private fun humanToneColor(
    tone: HumanStatusTone
): Color = when (tone) {
    HumanStatusTone.SUCCESS -> Color(0xFF65F0A9)
    HumanStatusTone.INFO -> Color(0xFF55E6FF)
    HumanStatusTone.WARNING -> Color(0xFFFFC857)
    HumanStatusTone.ERROR -> Color(0xFFFF6B7C)
    HumanStatusTone.INACTIVE -> Color(0xFFAAB7C4)
}

private fun statusSymbol(
    tone: HumanStatusTone
): String = when (tone) {
    HumanStatusTone.SUCCESS -> "●"
    HumanStatusTone.INFO -> "●"
    HumanStatusTone.WARNING -> "!"
    HumanStatusTone.ERROR -> "×"
    HumanStatusTone.INACTIVE -> "○"
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
    val statuses = state.modelStatuses
    val allInstalled = statuses.isNotEmpty() &&
        statuses.all { it.state == ModelInstallState.INSTALLED }
    val busy = statuses.any {
        it.state == ModelInstallState.DOWNLOADING ||
            it.state == ModelInstallState.VERIFYING
    }

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
                text = "PACOTE NEURAL ADAPTATIVO",
                style = MaterialTheme.typography.labelLarge
            )

            Text(
                text = "O BudsIA escolhe o modelo pela taxa real aberta pela rota: 48 kHz para alta qualidade e 16 kHz para voz Bluetooth/HFP compatível.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )

            statuses.forEach { status ->
                val active =
                    state.activeModelId == status.descriptor.id

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor =
                            MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(6.dp)
                    ) {
                        MetricRow(
                            "Modelo",
                            status.descriptor.displayName
                        )
                        MetricRow(
                            "Rota",
                            "${status.descriptor.sampleRateHz} Hz • ${status.descriptor.qualityTier.name}"
                        )
                        MetricRow(
                            "Estado",
                            if (active) {
                                "${status.state.name} • ATIVO"
                            } else {
                                status.state.name
                            }
                        )
                        MetricRow(
                            "Tamanho",
                            formatBytes(
                                status.descriptor.sizeBytes
                            )
                        )

                        if (
                            status.state ==
                                ModelInstallState.DOWNLOADING ||
                            status.state ==
                                ModelInstallState.VERIFYING
                        ) {
                            LinearProgressIndicator(
                                progress = { status.progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        status.errorMessage?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            if (statuses.isEmpty()) {
                Text(
                    text = "Verificando modelos locais...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            if (!allInstalled) {
                Button(
                    onClick = onInstallModel,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Rounded.Download,
                        contentDescription = null
                    )
                    androidx.compose.foundation.layout.Spacer(
                        modifier = Modifier.size(8.dp)
                    )
                    Text(
                        if (busy) {
                            "Instalando..."
                        } else {
                            "Instalar pacote IA"
                        }
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onRemoveModel,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = null
                    )
                    androidx.compose.foundation.layout.Spacer(
                        modifier = Modifier.size(8.dp)
                    )
                    Text("Remover pacote IA")
                }
            }
        }
    }
}

@Composable
private fun AdaptiveProfileCard(
    state: AudioFocusUiState,
    onEnvironmentSelected: (AcousticEnvironment) -> Unit,
    onPreferredStrengthChanged: (Float) -> Unit,
    onMoreFilter: () -> Unit,
    onMoreNatural: () -> Unit,
    onGoodAsIs: () -> Unit,
    onResetProfile: () -> Unit,
    onAdaptiveControlCandidateChanged: (Boolean) -> Unit
) {
    val profile = state.adaptiveProfile
    val hasInstalledAi = state.modelStatuses.any {
        it.state == ModelInstallState.INSTALLED
    }

    var sliderValue by remember(
        profile.environment,
        profile.revision
    ) {
        mutableFloatStateOf(
            profile.preferredEnhancementStrength
        )
    }

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
                text = "PERFIL ADAPTATIVO LOCAL",
                style = MaterialTheme.typography.labelLarge
            )

            Text(
                text = "Aprende apenas sua preferência por ambiente. Não altera pesos do modelo nem envia áudio para a nuvem.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Aplicar perfil ao áudio")
                    Text(
                        text = if (
                            state.adaptiveControlCandidateEnabled
                        ) {
                            "CANDIDATO EXPERIMENTAL • reversível"
                        } else {
                            "Desativado por padrão"
                        },
                        color = if (
                            state.adaptiveControlCandidateEnabled
                        ) {
                            Color(0xFFFFC857)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontSize = 11.sp
                    )
                }

                Switch(
                    checked =
                        state.adaptiveControlCandidateEnabled,
                    onCheckedChange =
                        onAdaptiveControlCandidateChanged,
                    enabled = hasInstalledAi
                )
            }

            Text(
                text = "Quando ativado, o perfil controla somente a mistura ORIGINAL ⇄ IA. O modelo continua imutável e o fallback para DSP permanece disponível.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(AcousticEnvironment.entries) { environment ->
                    FilterChip(
                        selected =
                            profile.environment == environment,
                        onClick = {
                            onEnvironmentSelected(environment)
                        },
                        label = {
                            Text(environment.displayName)
                        }
                    )
                }
            }

            MetricRow(
                "Preferência de filtragem",
                "${(profile.preferredEnhancementStrength * 100f).toInt()}%"
            )
            MetricRow(
                "Revisão local",
                profile.revision.toString()
            )
            MetricRow(
                "Feedbacks",
                profile.feedbackCount.toString()
            )
            MetricRow(
                "Aprovados",
                profile.positiveFeedbackCount.toString()
            )

            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = {
                    onPreferredStrengthChanged(sliderValue)
                },
                valueRange =
                    AdaptiveAudioProfile.MIN_PREFERRED_STRENGTH..
                        AdaptiveAudioProfile.MAX_PREFERRED_STRENGTH
            )

            Text(
                text = "Ensinar preferência",
                style = MaterialTheme.typography.labelMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onMoreNatural,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Mais natural")
                }
                OutlinedButton(
                    onClick = onMoreFilter,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Mais filtro")
                }
            }

            Button(
                onClick = onGoodAsIs,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Está bom assim")
            }

            OutlinedButton(
                onClick = onResetProfile,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Restaurar perfil deste ambiente")
            }

            Text(
                text = "O controle candidato nunca é ativado sozinho ao abrir o app. A eficácia acústica ainda exige validação A/B no aparelho físico.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun AdaptiveAbEvaluationCard(
    state: AudioFocusUiState,
    onAuditionFactory: () -> Unit,
    onAuditionCandidate: () -> Unit,
    onRecordChoice: (AdaptiveAbChoice) -> Unit,
    onReset: () -> Unit
) {
    val aiRunning =
        state.neuralTelemetry.state ==
            NeuralPipelineState.RUNNING &&
            state.selectedMode == RealtimeProcessingMode.AI
    val bothAuditioned =
        state.adaptiveAbFactoryAuditioned &&
            state.adaptiveAbCandidateAuditioned
    val stats = state.adaptiveAbStats
    val assessment = state.adaptiveAbAssessment

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
                text = "TESTE A/B LOCAL",
                style = MaterialTheme.typography.labelLarge
            )

            Text(
                text = "Compare IA Factory (100% saída neural) com seu Perfil Candidato. Nenhum áudio deste teste é salvo.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )

            MetricRow(
                "Ambiente",
                state.adaptiveProfile.environment.displayName
            )
            MetricRow(
                "Ouvindo agora",
                when (state.adaptiveAbAuditionVariant) {
                    AdaptiveAbVariant.FACTORY -> "IA FACTORY"
                    AdaptiveAbVariant.CANDIDATE -> "CANDIDATO"
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onAuditionFactory,
                    enabled = aiRunning,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (state.adaptiveAbFactoryAuditioned) {
                            "✓ Factory"
                        } else {
                            "Ouvir Factory"
                        }
                    )
                }

                OutlinedButton(
                    onClick = onAuditionCandidate,
                    enabled = aiRunning,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (state.adaptiveAbCandidateAuditioned) {
                            "✓ Candidato"
                        } else {
                            "Ouvir Candidato"
                        }
                    )
                }
            }

            if (!aiRunning) {
                Text(
                    text = "Inicie o modo IA para liberar a comparação.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            } else if (!bothAuditioned) {
                Text(
                    text = "Ouça as duas variantes nesta rodada antes de votar.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            Button(
                onClick = {
                    onRecordChoice(AdaptiveAbChoice.CANDIDATE)
                },
                enabled = bothAuditioned,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Prefiro Candidato")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onRecordChoice(AdaptiveAbChoice.FACTORY)
                    },
                    enabled = bothAuditioned,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Prefiro Factory")
                }

                OutlinedButton(
                    onClick = {
                        onRecordChoice(
                            AdaptiveAbChoice.NO_DIFFERENCE
                        )
                    },
                    enabled = bothAuditioned,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Sem diferença")
                }
            }

            HorizontalDivider()

            MetricRow(
                "Comparações",
                stats.totalComparisons.toString()
            )
            MetricRow(
                "Decisivas",
                stats.decisiveComparisons.toString()
            )
            MetricRow(
                "Preferência explícita candidato",
                stats.candidatePreferenceRate?.let {
                    "%.0f%%".format(
                        Locale.US,
                        it * 100f
                    )
                } ?: "—"
            )
            MetricRow(
                "Leitura local",
                when (assessment?.status) {
                    AdaptiveAbPreferenceStatus.CANDIDATE_PREFERRED ->
                        "CANDIDATO PREFERIDO"
                    AdaptiveAbPreferenceStatus.FACTORY_PREFERRED ->
                        "FACTORY PREFERIDO"
                    AdaptiveAbPreferenceStatus.MIXED ->
                        "PREFERÊNCIA MISTA"
                    AdaptiveAbPreferenceStatus.INSUFFICIENT_DATA,
                    null ->
                        "DADOS INSUFICIENTES"
                }
            )

            Text(
                text = "Esta leitura resume suas escolhas explícitas neste ambiente; não é uma nota objetiva de qualidade e nunca promove o candidato automaticamente.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )

            OutlinedButton(
                onClick = onReset,
                enabled = stats.totalComparisons > 0L,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Zerar avaliação deste ambiente")
            }
        }
    }
}

@Composable
private fun PerformanceCard(
    state: AudioFocusUiState,
    onLowBatteryAutoFallbackChanged: (Boolean) -> Unit,
    onStopAiBelowBatteryPercentChanged: (Int) -> Unit
) {
    val snapshot = state.performanceSnapshot
    val settings = state.performanceSettings
    val decision = state.performanceDecision
    val ai = state.neuralTelemetry

    var batteryLimit by remember(
        settings.stopAiBelowBatteryPercent
    ) {
        mutableFloatStateOf(
            settings.stopAiBelowBatteryPercent.toFloat()
        )
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(
                text = "AI PERFORMANCE • ADPF",
                style = MaterialTheme.typography.labelLarge
            )

            MetricRow(
                "Tier recomendado",
                decision?.tier?.name ?: "ANALISANDO"
            )
            MetricRow(
                "Performance Hint",
                when {
                    ai.state != NeuralPipelineState.RUNNING ->
                        "INATIVO"
                    ai.performanceHintSupported ->
                        "ATIVO"
                    else ->
                        "NÃO SUPORTADO"
                }
            )
            MetricRow(
                "Hint eficiência",
                if (
                    ai.state == NeuralPipelineState.RUNNING &&
                    ai.powerEfficiencyHintActive
                ) {
                    "ATIVO"
                } else {
                    "OFF"
                }
            )
            MetricRow(
                "Térmico",
                "${snapshot.thermalLevel.name} • MEASURED"
            )
            MetricRow(
                "Headroom térmico agora",
                snapshot.thermalHeadroomNow.value?.let {
                    "%.2f • %s".format(
                        Locale.US,
                        it,
                        snapshot.thermalHeadroomNow.kind.name
                    )
                } ?: "UNKNOWN"
            )
            MetricRow(
                "Headroom térmico +10s",
                snapshot.thermalHeadroomForecast10s.value?.let {
                    "%.2f • %s".format(
                        Locale.US,
                        it,
                        snapshot.thermalHeadroomForecast10s.kind.name
                    )
                } ?: "UNKNOWN"
            )
            MetricRow(
                "CPU headroom",
                snapshot.cpuHeadroomPercent.value?.let {
                    "%.1f%% • %s".format(
                        Locale.US,
                        it,
                        snapshot.cpuHeadroomPercent.kind.name
                    )
                } ?: "UNKNOWN"
            )
            MetricRow(
                "CPU BudsIA",
                snapshot.processCpuPercent.value?.let {
                    "%.1f%% • %s".format(
                        Locale.US,
                        it,
                        snapshot.processCpuPercent.kind.name
                    )
                } ?: "UNKNOWN"
            )
            MetricRow(
                "Bateria",
                snapshot.batteryPercent.value?.let {
                    "$it% • ${snapshot.batteryPercent.kind.name}"
                } ?: "UNKNOWN"
            )
            MetricRow(
                "Carregando",
                snapshot.isCharging.value?.let {
                    "${if (it) "SIM" else "NÃO"} • ${snapshot.isCharging.kind.name}"
                } ?: "UNKNOWN"
            )
            MetricRow(
                "Economia energia",
                snapshot.powerSaveMode.value?.let {
                    "${if (it) "ATIVA" else "OFF"} • ${snapshot.powerSaveMode.kind.name}"
                } ?: "UNKNOWN"
            )
            MetricRow(
                "Memória livre",
                snapshot.availableMemoryBytes.value?.let {
                    "${formatBytes(it)} • ${snapshot.availableMemoryBytes.kind.name}"
                } ?: "UNKNOWN"
            )

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Parar IA com bateria baixa")
                    Text(
                        text =
                            "Opcional • proteção térmica e de memória continua obrigatória",
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }

                Switch(
                    checked =
                        settings.lowBatteryAutoFallbackEnabled,
                    onCheckedChange =
                        onLowBatteryAutoFallbackChanged
                )
            }

            if (settings.lowBatteryAutoFallbackEnabled) {
                MetricRow(
                    "Limite da bateria",
                    "${batteryLimit.toInt()}%"
                )

                Slider(
                    value = batteryLimit,
                    onValueChange = {
                        batteryLimit = it
                    },
                    onValueChangeFinished = {
                        onStopAiBelowBatteryPercentChanged(
                            batteryLimit
                                .toInt()
                                .coerceIn(
                                    AiPerformanceSettings
                                        .MIN_STOP_PERCENT,
                                    AiPerformanceSettings
                                        .MAX_STOP_PERCENT
                                )
                        )
                    },
                    valueRange =
                        AiPerformanceSettings
                            .MIN_STOP_PERCENT.toFloat()..
                            AiPerformanceSettings
                                .MAX_STOP_PERCENT.toFloat()
                )
            }

            decision?.let {
                Text(
                    text = it.reason,
                    color = if (it.forceFallback) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = 12.sp
                )
            }

            Text(
                text = "Headroom térmico e CPU headroom são estimativas do Android. CPU BudsIA é uma estimativa do processo. Nenhuma temperatura, autonomia ou ganho de NPU é inventado.",
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}
@Composable
private fun DeviceValidationCard(
    state: AudioFocusUiState,
    onStart: () -> Unit,
    onCancel: () -> Unit
) {
    val validation = state.validation
    val report = validation.report
    val canStart =
        state.pipelineState == PipelineState.LISTENING &&
            !validation.running &&
            (
                state.selectedMode != RealtimeProcessingMode.AI ||
                    state.neuralTelemetry.state ==
                        NeuralPipelineState.RUNNING
                )

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
                text = "DEVICE VALIDATION LAB",
                style = MaterialTheme.typography.labelLarge
            )

            Text(
                text = "Teste técnico de 30 s usando somente telemetria local. Nenhum áudio é salvo.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )

            if (validation.running) {
                LinearProgressIndicator(
                    progress = {
                        validation.progress.coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                MetricRow(
                    "Progresso",
                    "${validation.elapsedSeconds}/30 s"
                )
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancelar teste")
                }
            } else {
                Button(
                    onClick = onStart,
                    enabled = canStart,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Executar validação de 30 s")
                }
            }

            validation.message?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            report?.let { result ->
                HorizontalDivider()

                MetricRow(
                    "Resultado técnico",
                    result.overallStatus.name
                )
                MetricRow(
                    "Modo testado",
                    result.requestedMode.name
                )
                MetricRow(
                    "Input",
                    result.inputSampleRateHz?.let {
                        "$it Hz"
                    } ?: "UNKNOWN"
                )
                MetricRow(
                    "Modelo",
                    result.neuralModelId ?: "N/A"
                )
                MetricRow(
                    "Drops input",
                    result.inputDroppedSamplesDelta
                        ?.toString() ?: "UNKNOWN"
                )
                MetricRow(
                    "Drops entrada IA",
                    result.aiInputDroppedSamplesDelta
                        ?.toString() ?: "N/A"
                )
                MetricRow(
                    "Overruns output",
                    result.outputOverrunSamplesDelta
                        ?.toString() ?: "N/A"
                )
                MetricRow(
                    "Underruns output",
                    result.outputUnderrunSamplesDelta
                        ?.toString() ?: "N/A"
                )
                MetricRow(
                    "Desconexões",
                    result.disconnectCountDelta
                        ?.toString() ?: "UNKNOWN"
                )
                MetricRow(
                    "RTF máximo",
                    result.maxRealtimeFactor?.let {
                        "%.2f×".format(Locale.US, it)
                    } ?: "N/A"
                )
                MetricRow(
                    "CPU BudsIA pico",
                    result.peakEstimatedProcessCpuPercent
                        ?.let {
                            "%.1f%% ESTIMATED".format(
                                Locale.US,
                                it
                            )
                        } ?: "UNKNOWN"
                )
                MetricRow(
                    "Headroom térmico +10s máx.",
                    result.maximumThermalHeadroomForecast10s
                        ?.let {
                            "%.2f ESTIMATED".format(
                                Locale.US,
                                it
                            )
                        } ?: "UNKNOWN"
                )
                MetricRow(
                    "CPU headroom mínimo",
                    result.minimumCpuHeadroomPercent
                        ?.let {
                            "%.1f%%".format(
                                Locale.US,
                                it
                            )
                        } ?: "UNKNOWN"
                )
                MetricRow(
                    "Térmico máximo",
                    result.maximumThermalLevel.name
                )
                MetricRow(
                    "Economia de energia",
                    if (result.powerSaveObserved) {
                        "OBSERVADA"
                    } else {
                        "NÃO OBSERVADA"
                    }
                )
                MetricRow(
                    "Controle adaptativo",
                    if (result.adaptiveControlObserved) {
                        result.adaptiveStrengthRange?.let {
                            "%.0f–%.0f%% CANDIDATO".format(
                                Locale.US,
                                it.start * 100f,
                                it.endInclusive * 100f
                            )
                        } ?: "CANDIDATO"
                    } else {
                        "FACTORY"
                    }
                )

                Text(
                    text = "PASS/WARN/FAIL abaixo avalia estabilidade técnica mensurável; não é uma nota de qualidade acústica.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )

                result.checks.forEach { check ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor =
                                MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text =
                                    "${validationSymbol(check.status)} ${check.title}",
                                color =
                                    validationColor(check.status),
                                style =
                                    MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = check.detail,
                                color =
                                    MaterialTheme.colorScheme
                                        .onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun validationColor(
    status: ValidationStatus
): Color = when (status) {
    ValidationStatus.PASS -> Color(0xFF65F0A9)
    ValidationStatus.WARN -> Color(0xFFFFC857)
    ValidationStatus.FAIL -> MaterialTheme.colorScheme.error
    ValidationStatus.UNKNOWN ->
        MaterialTheme.colorScheme.onSurfaceVariant
}

private fun validationSymbol(
    status: ValidationStatus
): String = when (status) {
    ValidationStatus.PASS -> "✓"
    ValidationStatus.WARN -> "!"
    ValidationStatus.FAIL -> "×"
    ValidationStatus.UNKNOWN -> "?"
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
                "Tier recomendado",
                ai.recommendedPerformanceTier?.name ?: "—"
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
            MetricRow(
                "Controle adaptativo",
                if (ai.adaptiveControlActive) {
                    "CANDIDATO ATIVO"
                } else {
                    "FACTORY"
                }
            )
            MetricRow(
                "Ambiente",
                ai.adaptiveEnvironmentLabel
            )
            MetricRow(
                "Mix IA",
                "${(ai.adaptiveStrength * 100f).toInt()}%"
            )
            MetricRow(
                "Perfil rev.",
                ai.adaptiveProfileRevision.toString()
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
    val selectedOutput = state.availableOutputs
        .firstOrNull { it.id == state.selectedOutputDeviceId }
    val bluetoothInputSelected = selectedInput?.isBluetooth == true
    val a2dpOutputWithPhoneInput =
        selectedOutput?.type ==
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP &&
            !bluetoothInputSelected

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

            if (a2dpOutputWithPhoneInput) {
                Text(
                    text =
                        "Os Buds estão selecionados apenas como saída A2DP; a entrada continua no telefone. Para testar o microfone dos Buds, pare o áudio e selecione BLUETOOTH HFP/SCO em Entrada. Ao usar o microfone Bluetooth, o Android assume a rota de comunicação e a saída A2DP deixa de ser a rota simultânea.",
                    color = Color(0xFFFFC857),
                    fontSize = 12.sp
                )
            }

            if (bluetoothInputSelected) {
                Text(
                    text = "Com microfone Bluetooth, o Android controla a rota de comunicação de entrada e saída; a saída ativa real aparece abaixo.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }

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
