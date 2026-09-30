package com.k410sh4.budsia.feature.live

import com.k410sh4.budsia.core.ai.enhancement.NeuralPipelineState
import com.k410sh4.budsia.core.ai.models.AiModelCatalog
import com.k410sh4.budsia.core.ai.models.ModelInstallState
import com.k410sh4.budsia.core.audio.model.PipelineState
import com.k410sh4.budsia.core.audio.realtime.RealtimeProcessingMode
import com.k410sh4.budsia.core.audio.routing.AudioDeviceDescriptor

enum class AudioFocusSection(val label: String) {
    LISTENING("Escuta"),
    ROUTES("Rotas"),
    MODELS("IA"),
    DIAGNOSTICS("Diagnóstico"),
    LAB("Lab"),
    DEVELOPER("Dev/IA")
}

enum class HumanStatusTone {
    SUCCESS,
    INFO,
    WARNING,
    ERROR,
    INACTIVE
}

enum class UiNoticeCategory(val displayName: String) {
    ROUTE("Rota"),
    PERFORMANCE("Performance"),
    MODEL("Modelo"),
    TEMPERATURE("Temperatura"),
    COMPATIBILITY("Compatibilidade"),
    MONITORING("Monitoramento"),
    SYSTEM("Sistema")
}

enum class UiNoticeSeverity {
    INFO,
    WARNING,
    ERROR
}

data class UiNotice(
    val category: UiNoticeCategory,
    val severity: UiNoticeSeverity,
    val title: String,
    val detail: String
)

data class ListeningUiState(
    val title: String,
    val detail: String,
    val tone: HumanStatusTone,
    val captureActive: Boolean,
    val aiActive: Boolean,
    val inputLabel: String,
    val outputLabel: String,
    val monitoringLabel: String,
    val monitoringAvailable: Boolean,
    val environmentLabel: String,
    val routeStabilityLabel: String,
    val fallbackReason: String?
)

data class RoutingUiState(
    val inputHumanLabel: String,
    val inputTechnicalLabel: String,
    val outputHumanLabel: String,
    val outputTechnicalLabel: String,
    val communicationMode: Boolean,
    val monitoringAvailable: Boolean,
    val monitoringEnabled: Boolean
)

data class ModelsUiState(
    val installedCount: Int,
    val totalCount: Int,
    val activeModelName: String,
    val selectionReason: String
)

data class DiagnosticsUiState(
    val notices: List<UiNotice>,
    val technicalSnapshot: TechnicalSnapshot
)

data class LabUiState(
    val running: Boolean,
    val statusLabel: String,
    val summary: String
)

data class DeveloperUiState(
    val sessionState: AdaptiveSessionState
) {
    val json: String
        get() = sessionState.toJson()
}

data class LayeredAudioUiState(
    val listening: ListeningUiState,
    val routing: RoutingUiState,
    val models: ModelsUiState,
    val diagnostics: DiagnosticsUiState,
    val lab: LabUiState,
    val developer: DeveloperUiState
)

data class RouteTechnicalSnapshot(
    val selectedInputDeviceId: Int,
    val selectedOutputDeviceId: Int,
    val activeInputLabel: String,
    val activeOutputLabel: String,
    val inputSampleRateHz: Int?,
    val outputSampleRateHz: Int?,
    val communicationMode: Boolean,
    val monitoringAvailable: Boolean,
    val monitoringEnabled: Boolean,
    val inputDrops: Long?,
    val outputUnderruns: Long?,
    val inputXruns: Long?,
    val outputXruns: Long?,
    val disconnects: Long?
)

data class InferenceTechnicalSnapshot(
    val selectedMode: String,
    val state: String,
    val modelId: String?,
    val engineId: String?,
    val provider: String?,
    val frameSamples: Int?,
    val inferenceMs: Double?,
    val averageInferenceMs: Double?,
    val maximumInferenceMs: Double?,
    val realtimeFactor: Double?,
    val chunksProcessed: Long,
    val samplesEnhanced: Long,
    val enhancedRms: Float?,
    val adaptiveControlActive: Boolean,
    val adaptiveStrength: Float,
    val fallbackReason: String?
)

data class PerformanceTechnicalSnapshot(
    val thermalLevel: String,
    val thermalHeadroomNow: Float?,
    val thermalHeadroomForecast10s: Float?,
    val cpuHeadroomPercent: Float?,
    val processCpuPercent: Double?,
    val batteryPercent: Int?,
    val charging: Boolean?,
    val powerSaveMode: Boolean?,
    val availableMemoryBytes: Long?,
    val recommendedTier: String?,
    val allowAi: Boolean?,
    val forceFallback: Boolean?,
    val decisionReason: String?
)

data class ValidationTechnicalSnapshot(
    val running: Boolean,
    val status: String?,
    val requestedMode: String?,
    val inputSampleRateHz: Int?,
    val neuralModelId: String?,
    val maximumRealtimeFactor: Double?,
    val maximumThermalLevel: String?
)

data class TechnicalSnapshot(
    val route: RouteTechnicalSnapshot,
    val inference: InferenceTechnicalSnapshot,
    val performance: PerformanceTechnicalSnapshot,
    val validation: ValidationTechnicalSnapshot
)

data class AdaptiveSessionState(
    val schemaVersion: Int = 1,
    val pipelineState: String,
    val route: RouteTechnicalSnapshot,
    val inference: InferenceTechnicalSnapshot,
    val performance: PerformanceTechnicalSnapshot,
    val validation: ValidationTechnicalSnapshot
) {
    fun toJson(): String = buildString {
        append("{\n")
        append("  \"schemaVersion\": ").append(schemaVersion).append(",\n")
        append("  \"pipelineState\": ").appendJson(pipelineState).append(",\n")
        append("  \"route\": {\n")
        append("    \"selectedInputDeviceId\": ").append(route.selectedInputDeviceId).append(",\n")
        append("    \"selectedOutputDeviceId\": ").append(route.selectedOutputDeviceId).append(",\n")
        append("    \"activeInputLabel\": ").appendJson(route.activeInputLabel).append(",\n")
        append("    \"activeOutputLabel\": ").appendJson(route.activeOutputLabel).append(",\n")
        append("    \"inputSampleRateHz\": ").appendNullable(route.inputSampleRateHz).append(",\n")
        append("    \"outputSampleRateHz\": ").appendNullable(route.outputSampleRateHz).append(",\n")
        append("    \"communicationMode\": ").append(route.communicationMode).append(",\n")
        append("    \"monitoringAvailable\": ").append(route.monitoringAvailable).append(",\n")
        append("    \"monitoringEnabled\": ").append(route.monitoringEnabled).append(",\n")
        append("    \"inputDrops\": ").appendNullable(route.inputDrops).append(",\n")
        append("    \"outputUnderruns\": ").appendNullable(route.outputUnderruns).append(",\n")
        append("    \"inputXruns\": ").appendNullable(route.inputXruns).append(",\n")
        append("    \"outputXruns\": ").appendNullable(route.outputXruns).append(",\n")
        append("    \"disconnects\": ").appendNullable(route.disconnects).append("\n")
        append("  },\n")
        append("  \"inference\": {\n")
        append("    \"selectedMode\": ").appendJson(inference.selectedMode).append(",\n")
        append("    \"state\": ").appendJson(inference.state).append(",\n")
        append("    \"modelId\": ").appendJson(inference.modelId).append(",\n")
        append("    \"engineId\": ").appendJson(inference.engineId).append(",\n")
        append("    \"provider\": ").appendJson(inference.provider).append(",\n")
        append("    \"frameSamples\": ").appendNullable(inference.frameSamples).append(",\n")
        append("    \"inferenceMs\": ").appendNullable(inference.inferenceMs).append(",\n")
        append("    \"averageInferenceMs\": ").appendNullable(inference.averageInferenceMs).append(",\n")
        append("    \"maximumInferenceMs\": ").appendNullable(inference.maximumInferenceMs).append(",\n")
        append("    \"realtimeFactor\": ").appendNullable(inference.realtimeFactor).append(",\n")
        append("    \"chunksProcessed\": ").append(inference.chunksProcessed).append(",\n")
        append("    \"samplesEnhanced\": ").append(inference.samplesEnhanced).append(",\n")
        append("    \"enhancedRms\": ").appendNullable(inference.enhancedRms).append(",\n")
        append("    \"adaptiveControlActive\": ").append(inference.adaptiveControlActive).append(",\n")
        append("    \"adaptiveStrength\": ").append(inference.adaptiveStrength).append(",\n")
        append("    \"fallbackReason\": ").appendJson(inference.fallbackReason).append("\n")
        append("  },\n")
        append("  \"performance\": {\n")
        append("    \"thermalLevel\": ").appendJson(performance.thermalLevel).append(",\n")
        append("    \"thermalHeadroomNow\": ").appendNullable(performance.thermalHeadroomNow).append(",\n")
        append("    \"thermalHeadroomForecast10s\": ").appendNullable(performance.thermalHeadroomForecast10s).append(",\n")
        append("    \"cpuHeadroomPercent\": ").appendNullable(performance.cpuHeadroomPercent).append(",\n")
        append("    \"processCpuPercent\": ").appendNullable(performance.processCpuPercent).append(",\n")
        append("    \"batteryPercent\": ").appendNullable(performance.batteryPercent).append(",\n")
        append("    \"charging\": ").appendNullable(performance.charging).append(",\n")
        append("    \"powerSaveMode\": ").appendNullable(performance.powerSaveMode).append(",\n")
        append("    \"availableMemoryBytes\": ").appendNullable(performance.availableMemoryBytes).append(",\n")
        append("    \"recommendedTier\": ").appendJson(performance.recommendedTier).append(",\n")
        append("    \"allowAi\": ").appendNullable(performance.allowAi).append(",\n")
        append("    \"forceFallback\": ").appendNullable(performance.forceFallback).append(",\n")
        append("    \"decisionReason\": ").appendJson(performance.decisionReason).append("\n")
        append("  },\n")
        append("  \"validation\": {\n")
        append("    \"running\": ").append(validation.running).append(",\n")
        append("    \"status\": ").appendJson(validation.status).append(",\n")
        append("    \"requestedMode\": ").appendJson(validation.requestedMode).append(",\n")
        append("    \"inputSampleRateHz\": ").appendNullable(validation.inputSampleRateHz).append(",\n")
        append("    \"neuralModelId\": ").appendJson(validation.neuralModelId).append(",\n")
        append("    \"maximumRealtimeFactor\": ").appendNullable(validation.maximumRealtimeFactor).append(",\n")
        append("    \"maximumThermalLevel\": ").appendJson(validation.maximumThermalLevel).append("\n")
        append("  }\n")
        append("}")
    }
}

fun AudioFocusUiState.toLayeredUiState(): LayeredAudioUiState {
    val captureActive =
        pipelineState == PipelineState.LISTENING ||
            pipelineState == PipelineState.STARTING ||
            pipelineState == PipelineState.STOPPING
    val aiActive =
        selectedMode == RealtimeProcessingMode.AI &&
            neuralTelemetry.state == NeuralPipelineState.RUNNING
    val fallbackReason = neuralTelemetry.fallbackReason
    val selectedInput =
        availableInputs.firstOrNull { it.id == selectedInputDeviceId }
    val selectedOutput =
        availableOutputs.firstOrNull { it.id == selectedOutputDeviceId }
    val liveInputLabel =
        if (captureActive && inputRouteLabel != "—") {
            friendlyRouteLabel(inputRouteLabel)
        } else {
            selectedInput?.humanDisplayName() ?: "Automática"
        }
    val liveOutputLabel =
        if (captureActive && outputRouteLabel != "—") {
            friendlyRouteLabel(outputRouteLabel)
        } else {
            selectedOutput?.humanDisplayName() ?: "Automática"
        }

    val tone = when {
        errorMessage != null -> HumanStatusTone.ERROR
        fallbackReason != null ||
            neuralTelemetry.state == NeuralPipelineState.FALLBACK ->
            HumanStatusTone.WARNING
        aiActive -> HumanStatusTone.SUCCESS
        captureActive -> HumanStatusTone.INFO
        else -> HumanStatusTone.INACTIVE
    }

    val title = when (tone) {
        HumanStatusTone.ERROR -> "Atenção necessária"
        HumanStatusTone.WARNING -> "Proteção automática ativa"
        HumanStatusTone.SUCCESS -> "IA local ativa"
        HumanStatusTone.INFO -> "Áudio local ativo"
        HumanStatusTone.INACTIVE -> "Pronto para ouvir"
    }

    val detail = when {
        errorMessage != null -> errorMessage
        fallbackReason != null -> "A IA recuou para DSP para manter a sessão segura e estável."
        aiActive -> "O áudio está sendo processado localmente pela IA."
        captureActive -> "O núcleo de áudio está captando a rota selecionada."
        else -> "Escolha o modo e inicie o áudio quando estiver pronto."
    }

    val stability = routeStabilityLabel()
    val monitoringLabel = when {
        snapshot?.monitoringEnabled == true -> "Ativo"
        canMonitorOutput -> "Disponível"
        captureActive -> "Indisponível nesta rota"
        else -> "Será verificado ao iniciar"
    }

    val routeSnapshot = RouteTechnicalSnapshot(
        selectedInputDeviceId = selectedInputDeviceId,
        selectedOutputDeviceId = selectedOutputDeviceId,
        activeInputLabel = inputRouteLabel,
        activeOutputLabel = outputRouteLabel,
        inputSampleRateHz =
            snapshot?.inputSampleRateHz?.takeIf { it > 0 },
        outputSampleRateHz =
            snapshot?.outputSampleRateHz?.takeIf { it > 0 },
        communicationMode = preparedCommunicationMode,
        monitoringAvailable = canMonitorOutput,
        monitoringEnabled = snapshot?.monitoringEnabled == true,
        inputDrops = snapshot?.droppedInputSamples,
        outputUnderruns = snapshot?.outputUnderrunSamples,
        inputXruns = snapshot?.inputXruns,
        outputXruns = snapshot?.outputXruns,
        disconnects = snapshot?.disconnectCount
    )

    val inferenceSnapshot = InferenceTechnicalSnapshot(
        selectedMode = selectedMode.name,
        state = neuralTelemetry.state.name,
        modelId = neuralTelemetry.modelId,
        engineId = neuralTelemetry.engineId,
        provider = neuralTelemetry.provider,
        frameSamples = neuralTelemetry.frameSamples,
        inferenceMs = neuralTelemetry.inferenceMs,
        averageInferenceMs = neuralTelemetry.averageInferenceMs,
        maximumInferenceMs = neuralTelemetry.maxInferenceMs,
        realtimeFactor = neuralTelemetry.realtimeFactor,
        chunksProcessed = neuralTelemetry.chunksProcessed,
        samplesEnhanced = neuralTelemetry.samplesEnhanced,
        enhancedRms = neuralTelemetry.enhancedRms,
        adaptiveControlActive = neuralTelemetry.adaptiveControlActive,
        adaptiveStrength = neuralTelemetry.adaptiveStrength,
        fallbackReason = neuralTelemetry.fallbackReason
    )

    val performanceSnapshotTechnical = PerformanceTechnicalSnapshot(
        thermalLevel = performanceSnapshot.thermalLevel.name,
        thermalHeadroomNow =
            performanceSnapshot.thermalHeadroomNow.value,
        thermalHeadroomForecast10s =
            performanceSnapshot.thermalHeadroomForecast10s.value,
        cpuHeadroomPercent =
            performanceSnapshot.cpuHeadroomPercent.value,
        processCpuPercent =
            performanceSnapshot.processCpuPercent.value,
        batteryPercent =
            performanceSnapshot.batteryPercent.value,
        charging = performanceSnapshot.isCharging.value,
        powerSaveMode = performanceSnapshot.powerSaveMode.value,
        availableMemoryBytes =
            performanceSnapshot.availableMemoryBytes.value,
        recommendedTier = performanceDecision?.tier?.name,
        allowAi = performanceDecision?.allowAi,
        forceFallback = performanceDecision?.forceFallback,
        decisionReason = performanceDecision?.reason
    )

    val report = validation.report
    val validationSnapshot = ValidationTechnicalSnapshot(
        running = validation.running,
        status = report?.overallStatus?.name,
        requestedMode = report?.requestedMode?.name,
        inputSampleRateHz = report?.inputSampleRateHz,
        neuralModelId = report?.neuralModelId,
        maximumRealtimeFactor = report?.maxRealtimeFactor,
        maximumThermalLevel = report?.maximumThermalLevel?.name
    )

    val technical = TechnicalSnapshot(
        route = routeSnapshot,
        inference = inferenceSnapshot,
        performance = performanceSnapshotTechnical,
        validation = validationSnapshot
    )
    val session = AdaptiveSessionState(
        pipelineState = pipelineState.name,
        route = routeSnapshot,
        inference = inferenceSnapshot,
        performance = performanceSnapshotTechnical,
        validation = validationSnapshot
    )

    val activeDescriptor =
        activeModelId?.let(AiModelCatalog::byId)
    val models = ModelsUiState(
        installedCount =
            modelStatuses.count {
                it.state == ModelInstallState.INSTALLED
            },
        totalCount = modelStatuses.size,
        activeModelName =
            activeDescriptor?.displayName ?: "Nenhum modelo ativo",
        selectionReason =
            snapshot?.inputSampleRateHz
                ?.takeIf { it > 0 }
                ?.let { rate ->
                    if (activeDescriptor != null) {
                        "Selecionado automaticamente porque a rota abriu em $rate Hz."
                    } else {
                        "A rota abriu em $rate Hz; o BudsIA procura um modelo instalado exatamente compatível."
                    }
                }
                ?: "O modelo é escolhido automaticamente pela taxa real da rota."
    )

    val labSummary = when {
        validation.running ->
            "Validação técnica em andamento: ${validation.elapsedSeconds}/30 s."
        report != null ->
            "Último resultado: ${report.overallStatus.name}. Mede estabilidade técnica, não qualidade acústica."
        else ->
            "Laboratório separado da experiência principal; nenhum áudio é salvo."
    }

    return LayeredAudioUiState(
        listening = ListeningUiState(
            title = title,
            detail = detail,
            tone = tone,
            captureActive = captureActive,
            aiActive = aiActive,
            inputLabel = liveInputLabel,
            outputLabel = liveOutputLabel,
            monitoringLabel = monitoringLabel,
            monitoringAvailable = canMonitorOutput,
            environmentLabel = adaptiveProfile.environment.displayName,
            routeStabilityLabel = stability,
            fallbackReason = fallbackReason
        ),
        routing = RoutingUiState(
            inputHumanLabel =
                selectedInput?.humanDisplayName() ?: "Automática",
            inputTechnicalLabel =
                selectedInput?.technicalDisplayName() ?: "Seleção automática",
            outputHumanLabel =
                selectedOutput?.humanDisplayName() ?: "Automática",
            outputTechnicalLabel =
                selectedOutput?.technicalDisplayName() ?: "Seleção automática",
            communicationMode = preparedCommunicationMode,
            monitoringAvailable = canMonitorOutput,
            monitoringEnabled = snapshot?.monitoringEnabled == true
        ),
        models = models,
        diagnostics = DiagnosticsUiState(
            notices = buildNotices(captureActive),
            technicalSnapshot = technical
        ),
        lab = LabUiState(
            running = validation.running,
            statusLabel = report?.overallStatus?.name ?: "SEM RESULTADO",
            summary = labSummary
        ),
        developer = DeveloperUiState(
            sessionState = session
        )
    )
}

fun AudioDeviceDescriptor.humanDisplayName(): String {
    val product = productName.trim().ifBlank { "Dispositivo de áudio" }
    return when {
        isBluetooth && isInput && isCommunicationCapable ->
            "$product · microfone Bluetooth"
        isBluetooth && isOutput && isCommunicationCapable ->
            "$product · áudio de comunicação"
        isBluetooth && isOutput ->
            "$product · áudio Bluetooth"
        isInput && typeLabel.contains("MIC", ignoreCase = true) ->
            "Microfone do celular"
        isOutput && (
            typeLabel.contains("SPEAKER", ignoreCase = true) ||
                typeLabel == "TYPE 1"
            ) ->
            "Alto-falante do celular"
        else -> product
    }
}

fun AudioDeviceDescriptor.technicalDisplayName(): String =
    buildString {
        append(productName)
        append(" · ")
        append(typeLabel)
        append(" · id ")
        append(id)
    }

private fun AudioFocusUiState.routeStabilityLabel(): String {
    val thermal = performanceSnapshot.thermalLevel.name
    if (
        thermal == "SEVERE" ||
        thermal == "CRITICAL" ||
        thermal == "EMERGENCY" ||
        thermal == "SHUTDOWN"
    ) {
        return "Protegida por limite térmico"
    }

    val rtf = neuralTelemetry.realtimeFactor
    if (selectedMode == RealtimeProcessingMode.AI && rtf != null) {
        return when {
            rtf <= 0.85 -> "Boa margem em tempo real"
            rtf <= 1.0 -> "Tempo real no limite"
            else -> "IA acima do orçamento de tempo real"
        }
    }

    return when {
        snapshot == null -> "Aguardando sessão"
        snapshot.droppedInputSamples > 0L -> "Perdas de entrada detectadas"
        snapshot.disconnectCount > 0L -> "Desconexão detectada"
        else -> "Estável"
    }
}

private fun AudioFocusUiState.buildNotices(
    captureActive: Boolean
): List<UiNotice> = buildList {
    errorMessage?.let {
        add(
            UiNotice(
                category = UiNoticeCategory.SYSTEM,
                severity = UiNoticeSeverity.ERROR,
                title = "Ação necessária",
                detail = it
            )
        )
    }

    neuralTelemetry.fallbackReason?.let { reason ->
        add(
            UiNotice(
                category =
                    if (reason.contains("temperatura", ignoreCase = true)) {
                        UiNoticeCategory.TEMPERATURE
                    } else {
                        UiNoticeCategory.PERFORMANCE
                    },
                severity = UiNoticeSeverity.WARNING,
                title = "Fallback para DSP",
                detail = reason
            )
        )
    }

    val thermal = performanceSnapshot.thermalLevel.name
    if (
        thermal == "SEVERE" ||
        thermal == "CRITICAL" ||
        thermal == "EMERGENCY" ||
        thermal == "SHUTDOWN"
    ) {
        add(
            UiNotice(
                category = UiNoticeCategory.TEMPERATURE,
                severity = UiNoticeSeverity.ERROR,
                title = "Limite térmico",
                detail =
                    "O Android reportou $thermal; a IA pode ser suspensa para proteger a estabilidade."
            )
        )
    } else if (thermal == "MODERATE") {
        add(
            UiNotice(
                category = UiNoticeCategory.TEMPERATURE,
                severity = UiNoticeSeverity.WARNING,
                title = "Temperatura em elevação",
                detail =
                    "O aparelho está em estado térmico MODERATE; acompanhe a sessão antes de testes longos."
            )
        )
    }

    if (
        selectedMode == RealtimeProcessingMode.AI &&
        activeModelId == null
    ) {
        add(
            UiNotice(
                category = UiNoticeCategory.MODEL,
                severity = UiNoticeSeverity.WARNING,
                title = "IA sem modelo ativo",
                detail =
                    "Instale ou aguarde a seleção de um modelo compatível com a taxa real da rota."
            )
        )
    }

    val inputRate = snapshot?.inputSampleRateHz ?: 0
    val outputRate = snapshot?.outputSampleRateHz ?: 0
    if (
        inputRate > 0 &&
        outputRate > 0 &&
        inputRate != outputRate
    ) {
        add(
            UiNotice(
                category = UiNoticeCategory.COMPATIBILITY,
                severity = UiNoticeSeverity.ERROR,
                title = "Taxas incompatíveis",
                detail =
                    "Entrada em $inputRate Hz e saída em $outputRate Hz; monitoramento ao vivo permanece bloqueado."
            )
        )
    }

    if (
        captureActive &&
        snapshot?.monitoringEnabled != true &&
        !canMonitorOutput
    ) {
        add(
            UiNotice(
                category = UiNoticeCategory.MONITORING,
                severity = UiNoticeSeverity.INFO,
                title = "Áudio processado não está sendo reproduzido",
                detail =
                    "A captura pode continuar normalmente, mas a rota atual não liberou monitoramento privado compatível."
            )
        )
    }

    if (preparedCommunicationMode) {
        add(
            UiNotice(
                category = UiNoticeCategory.ROUTE,
                severity = UiNoticeSeverity.INFO,
                title = "Rota de comunicação Bluetooth",
                detail =
                    "O Android assumiu a rota bidirecional de comunicação; A2DP não representa o microfone ativo nesta sessão."
            )
        )
    }
}

private fun friendlyRouteLabel(label: String): String =
    label
        .replace(" • BLUETOOTH HFP/SCO", " · microfone Bluetooth")
        .replace(" • BLUETOOTH A2DP", " · áudio Bluetooth")
        .replace(" • MIC INTERNO", " · microfone interno")
        .replace(" • SPEAKER", " · alto-falante")
        .replace(" • TYPE 1", " · saída do sistema")

private fun StringBuilder.appendJson(value: String?): StringBuilder =
    if (value == null) {
        append("null")
    } else {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
        append('"')
    }

private fun StringBuilder.appendNullable(value: Any?): StringBuilder =
    if (value == null) append("null") else append(value.toString())

