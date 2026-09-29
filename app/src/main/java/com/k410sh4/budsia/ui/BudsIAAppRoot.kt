package com.k410sh4.budsia.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.*
import com.k410sh4.budsia.R
import com.k410sh4.budsia.domain.model.*
import com.k410sh4.budsia.feature.main.BudsIAViewModel
import com.k410sh4.budsia.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

private object Routes {
    const val HOME = "home"
    const val LIVE = "live"
    const val HISTORY = "history"
    const val MODELS = "models"
    const val PRIVACY = "privacy"
}

@Composable
fun BudsIAAppRoot(vm: BudsIAViewModel) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.events.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        containerColor = Ink,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = Panel.copy(alpha = .97f)) {
                val entry by nav.currentBackStackEntryAsState()
                val route = entry?.destination?.route
                listOf(
                    Triple(Routes.HOME, "Início", Icons.Rounded.Home),
                    Triple(Routes.LIVE, "Conversa", Icons.Rounded.GraphicEq),
                    Triple(Routes.HISTORY, "Histórico", Icons.Rounded.History),
                    Triple(Routes.MODELS, "Modelos", Icons.Rounded.Memory),
                    Triple(Routes.PRIVACY, "Privacidade", Icons.Rounded.Shield)
                ).forEach { (target, label, icon) ->
                    NavigationBarItem(
                        selected = route == target,
                        onClick = { nav.navigate(target) { launchSingleTop = true } },
                        icon = { Icon(icon, null) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.HOME) { HomeScreen(vm) { nav.navigate(Routes.LIVE) } }
            composable(Routes.LIVE) { LiveScreen(vm) }
            composable(Routes.HISTORY) { HistoryScreen(vm) }
            composable(Routes.MODELS) { ModelsScreen(vm) }
            composable(Routes.PRIVACY) { PrivacyScreen(vm) }
        }
    }
}

@Composable
private fun HomeScreen(vm: BudsIAViewModel, openLive: () -> Unit) {
    val capabilities by vm.capabilities.collectAsState()
    val model by vm.advancedModelState.collectAsState()
    val advanced by vm.advancedSpeakerMode.collectAsState()
    val translationMode by vm.translationMode.collectAsState()

    Page("BudsIA V2", "Reconstruído com os aprendizados da primeira versão") {
        item {
            HeroCard(
                if (model.status == AdvancedModelStatus.READY)
                    "Conversa local com diarização, idioma e tradução"
                else
                    "Instale o pacote Conversa V2 para liberar a IA completa"
            )
        }
        item {
            GlassCard {
                ValueRow("Motor", if (advanced) "Conversa V2" else "Compatibilidade Android")
                ValueRow("Tradução", translationModeLabel(translationMode))
                ValueRow("Pacote V2", modelStatusLabel(model.status))
            }
        }
        item {
            Button(
                onClick = openLive,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Rounded.Mic, null)
                Spacer(Modifier.width(8.dp))
                Text("ABRIR CONVERSA", fontWeight = FontWeight.Bold)
            }
        }
        item { SectionTitle("Estado dos recursos") }
        items(capabilities.asList()) { CapabilityCard(it) }
        item {
            GlassCard {
                Text("Principais correções da V2", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("• Não cria um novo falante por causa de uma frase curta.", color = TextMuted)
                Text("• Identidade ambígua vira “Falante ?” e não é forçada.", color = TextMuted)
                Text("• Idioma fora de PT/EN não dispara tradução aleatória.", color = TextMuted)
                Text("• Trechos repetitivos, silenciosos ou muito curtos são filtrados.", color = TextMuted)
                Text("• Pyannote separa turnos antes da identidade de voz.", color = TextMuted)
            }
        }
    }
}

@Composable
private fun LiveScreen(vm: BudsIAViewModel) {
    val context = LocalContext.current
    val speech by vm.speechState.collectAsState()
    val pipeline by vm.pipeline.collectAsState()
    val session by vm.sessionItems.collectAsState()
    val advancedMode by vm.advancedSpeakerMode.collectAsState()
    val advancedModels by vm.advancedModelState.collectAsState()
    val advancedState by vm.advancedSpeakerState.collectAsState()
    val expectedSpeakers by vm.expectedSpeakers.collectAsState()
    val translationMode by vm.translationMode.collectAsState()
    val speechModels by vm.speechLanguageModels.collectAsState()
    var compatLanguage by remember { mutableStateOf("system") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            vm.startLive(if (advancedMode) null else compatLanguage.takeUnless { it == "system" })
        }
    }

    Page("Conversa", "Falante + idioma + tradução por trecho") {
        item {
            GlassCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            if (pipeline.running) "AO VIVO" else "PARADO",
                            color = if (pipeline.running) Green else TextMuted,
                            fontWeight = FontWeight.Black
                        )
                        Text(pipeline.stage, color = TextMuted)
                    }
                    StatusPill(if (advancedMode) "V2" else "COMPAT", if (advancedMode) Violet else Cyan)
                }
                pipeline.error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = Red)
                }
            }
        }

        item {
            GlassCard {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Motor Conversa V2", fontWeight = FontWeight.Bold)
                        Text(
                            if (advancedMode)
                                "Pyannote + Whisper Base + ERes2Net"
                            else
                                "Fallback Android • sem separação real de pessoas",
                            color = TextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = advancedMode,
                        onCheckedChange = vm::setAdvancedSpeakerMode,
                        enabled = advancedModels.status == AdvancedModelStatus.READY && !pipeline.running
                    )
                }
                if (advancedModels.status != AdvancedModelStatus.READY) {
                    Text(
                        "Instale o pacote V2 em Modelos para ativar a separação de falantes.",
                        color = Amber,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (advancedMode) {
            item {
                GlassCard {
                    Text("Pessoas esperadas", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "Auto", 2 to "2", 3 to "3", 4 to "4").forEach { pair ->
                            FilterChip(
                                selected = expectedSpeakers == pair.first,
                                onClick = { vm.setExpectedSpeakers(pair.first) },
                                enabled = !pipeline.running,
                                label = { Text(pair.second) }
                            )
                        }
                    }
                    Text(
                        "Para uma conversa entre duas pessoas, selecionar 2 ajuda o diarizador.",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        item {
            TranslationModeCard(translationMode, vm::setTranslationMode)
        }

        if (!advancedMode) {
            item {
                GlassCard {
                    Text("Idioma esperado no fallback", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "system" to "Sistema",
                            "pt-BR" to "PT",
                            "en-US" to "EN",
                            "es-ES" to "ES"
                        ).forEach { pair ->
                            FilterChip(
                                selected = compatLanguage == pair.first,
                                onClick = { compatLanguage = pair.first },
                                label = { Text(pair.second) }
                            )
                        }
                    }
                    compatLanguage.takeUnless { it == "system" }?.let { tag ->
                        speechModels[tag]?.let { SpeechModelRow(it) { vm.downloadSpeechModel(tag) } }
                    }
                }
            }
        }

        item {
            if (!pipeline.running) {
                Button(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            vm.startLive(if (advancedMode) null else compatLanguage.takeUnless { it == "system" })
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    enabled = if (advancedMode)
                        advancedModels.status == AdvancedModelStatus.READY
                    else
                        speech.available
                ) {
                    Icon(Icons.Rounded.Mic, null)
                    Spacer(Modifier.width(8.dp))
                    Text("INICIAR SESSÃO", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = vm::stopLive,
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Red)
                ) {
                    Icon(Icons.Rounded.Stop, null)
                    Spacer(Modifier.width(8.dp))
                    Text("PARAR", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (advancedMode && pipeline.running) {
            item {
                GlassCard {
                    ValueRow("Falantes estáveis", advancedState.speakerCount.toString())
                    ValueRow("Janelas processadas", advancedState.processedWindows.toString())
                    ValueRow("Janelas perdidas", advancedState.droppedWindows.toString())
                    advancedState.lastProcessingMs?.let { ms ->
                        ValueRow("Último processamento", ms.toString() + " ms")
                    }
                    Text(
                        "A V2 usa janelas de 8 segundos para ganhar estabilidade. A tradução pode aparecer com atraso proposital.",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (!advancedMode && (speech.error != null || speech.statusMessage != null)) {
            item {
                GlassCard {
                    Text("Motor de voz", color = if (speech.error != null) Red else Cyan, fontWeight = FontWeight.Bold)
                    speech.statusMessage?.let { Text(it, color = TextMuted) }
                    speech.error?.let { Text(it, color = TextMuted) }
                }
            }
        }

        pipeline.lastItem?.let { latest ->
            item { SectionTitle("Último trecho") }
            item { ConversationCard(latest, true) }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle("Sessão")
                TextButton(onClick = vm::clearSession) { Text("Limpar") }
            }
        }

        if (session.isEmpty()) {
            item {
                GlassCard {
                    Text(
                        "Ainda não há trechos. No motor V2, espere a primeira janela ser processada.",
                        color = TextMuted
                    )
                }
            }
        } else {
            items(session.asReversed().take(60)) { ConversationCard(it, false) }
        }
    }
}

@Composable
private fun HistoryScreen(vm: BudsIAViewModel) {
    val timeline by vm.timeline.collectAsState(initial = emptyList())

    Page("Histórico", "Resultados e telemetria salvos localmente") {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(timeline.size.toString() + " trechos", color = TextMuted)
                TextButton(onClick = vm::clearSavedTimeline) {
                    Icon(Icons.Rounded.Delete, null)
                    Text("Apagar")
                }
            }
        }
        if (timeline.isEmpty()) {
            item { GlassCard { Text("Nenhuma conversa salva.", color = TextMuted) } }
        } else {
            items(timeline) { ConversationCard(it, false) }
        }
    }
}

@Composable
private fun ModelsScreen(vm: BudsIAViewModel) {
    val advanced by vm.advancedModelState.collectAsState()
    val downloaded by vm.downloadedLanguages.collectAsState()
    val speechModels by vm.speechLanguageModels.collectAsState()
    val advancedMode by vm.advancedSpeakerMode.collectAsState()

    Page("Modelos", "Componentes locais da IA") {
        item {
            GlassCard {
                Text("Pacote Conversa V2", fontWeight = FontWeight.Black)
                Text(
                    when (advanced.status) {
                        AdvancedModelStatus.READY ->
                            "PRONTO • " + (advanced.installedVariant ?: "V2")
                        AdvancedModelStatus.DOWNLOADING -> advanced.message
                        AdvancedModelStatus.ERROR -> "ERRO • " + advanced.message
                        AdvancedModelStatus.NOT_INSTALLED -> "NÃO INSTALADO"
                    },
                    color = when (advanced.status) {
                        AdvancedModelStatus.READY -> Green
                        AdvancedModelStatus.DOWNLOADING -> Cyan
                        AdvancedModelStatus.ERROR -> Red
                        AdvancedModelStatus.NOT_INSTALLED -> Amber
                    },
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Whisper Base multilíngue + Pyannote Segmentation 3.0 + ERes2Net. O download é grande; depois a inferência funciona localmente.",
                    color = TextMuted
                )

                if (advanced.status == AdvancedModelStatus.DOWNLOADING) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { advanced.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    advanced.currentFile?.let {
                        Text(it, color = TextMuted, style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(Modifier.height(12.dp))
                when (advanced.status) {
                    AdvancedModelStatus.NOT_INSTALLED,
                    AdvancedModelStatus.ERROR -> {
                        Button(
                            onClick = vm::installAdvancedModels,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Download, null)
                            Text("INSTALAR PACOTE V2")
                        }
                    }
                    AdvancedModelStatus.READY -> {
                        if (!advancedMode) {
                            TextButton(onClick = vm::removeAdvancedModels) {
                                Text("Remover pacote V2")
                            }
                        }
                    }
                    AdvancedModelStatus.DOWNLOADING -> Unit
                }
            }
        }

        item {
            GlassCard {
                Text("Tradução offline", fontWeight = FontWeight.Bold)
                Text("PT e EN são obrigatórios para o modo automático.", color = TextMuted)
                listOf("pt" to "Português", "en" to "Inglês", "es" to "Espanhol").forEach { pair ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(pair.second)
                            Text(
                                if (pair.first in downloaded) "INSTALADO" else "NÃO INSTALADO",
                                color = if (pair.first in downloaded) Green else Amber,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        if (pair.first !in downloaded) {
                            OutlinedButton(onClick = { vm.downloadModel(pair.first) }) {
                                Text("Baixar")
                            }
                        }
                    }
                }
            }
        }

        item {
            GlassCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Fallback de voz do Android", fontWeight = FontWeight.Bold)
                    TextButton(onClick = vm::refreshSpeechModels) { Text("Atualizar") }
                }
                listOf("pt-BR" to "Português", "en-US" to "Inglês", "es-ES" to "Espanhol").forEach { pair ->
                    Text(pair.second)
                    SpeechModelRow(
                        speechModels[pair.first] ?: SpeechLanguageModelState(pair.first)
                    ) {
                        vm.downloadSpeechModel(pair.first)
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyScreen(vm: BudsIAViewModel) {
    val strict by vm.strictOffline.collectAsState()
    val save by vm.saveTranscript.collectAsState()
    val mode by vm.translationMode.collectAsState()

    Page("Privacidade", "Controles locais e explícitos") {
        item {
            GlassCard {
                SettingSwitch(
                    "Sessões offline",
                    "A conversa não baixa modelos nem usa fallback remoto enquanto está rodando.",
                    strict,
                    vm::setStrictOffline
                )
                HorizontalDivider(color = Color.White.copy(alpha = .08f))
                SettingSwitch(
                    "Salvar histórico",
                    "Desative para manter novos trechos apenas na sessão em memória.",
                    save,
                    vm::setSaveTranscript
                )
            }
        }
        item { TranslationModeCard(mode, vm::setTranslationMode) }
        item {
            GlassCard {
                Text("Regras", fontWeight = FontWeight.Bold)
                Text("• Áudio bruto não é salvo no histórico.", color = TextMuted)
                Text("• O microfone só funciona em sessão iniciada por você.", color = TextMuted)
                Text("• Perfis A/B/C existem só na sessão atual.", color = TextMuted)
                Text("• Incerteza é exibida, não escondida.", color = TextMuted)
                Text("• O app não afirma ler pensamentos ou intenção mental.", color = TextMuted)
            }
        }
    }
}

@Composable
private fun ConversationCard(item: ConversationItem, large: Boolean) {
    val source = languageLabel(item.languageTag)
    val target = languageLabel(item.translationTargetTag)
    val identityColor = when {
        item.speakerLabel == "Falante ?" -> Amber
        item.speakerStable -> Green
        else -> Cyan
    }

    GlassCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.speakerLabel,
                        fontWeight = FontWeight.Black,
                        style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.width(8.dp))
                    StatusPill(
                        when {
                            item.speakerLabel == "Falante ?" -> "INCERTO"
                            item.speakerStable -> "ESTÁVEL"
                            else -> "APRENDENDO"
                        },
                        identityColor
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LanguagePill(source, item.languageConfidence)
                    if (item.translationTargetTag != null) {
                        Icon(Icons.Rounded.ArrowForward, null, tint = TextMuted, modifier = Modifier.size(15.dp))
                        LanguagePill(target, null)
                    }
                }
            }
            Text(
                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp)),
                color = TextMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }

        item.speakerSimilarity?.let { similarity ->
            Text(
                "Similaridade de voz: " + (similarity * 100).toInt() + "%",
                color = identityColor,
                style = MaterialTheme.typography.labelSmall
            )
        }

        item.languageReason?.let {
            Text(it, color = TextMuted, style = MaterialTheme.typography.labelSmall)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            item.originalText,
            style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge
        )

        item.transcriptQuality?.let { quality ->
            val qColor = when {
                quality >= 0.68f -> Green
                quality >= 0.50f -> Amber
                else -> Red
            }
            Text(
                "Qualidade: " + (quality * 100).toInt() + "% • " + (item.transcriptQualityReason ?: ""),
                color = qColor,
                style = MaterialTheme.typography.labelSmall
            )
        }

        when (item.translationStatus) {
            "OK" -> item.translatedText?.let { translated ->
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = Cyan.copy(alpha = .08f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(source + " → " + target, color = Cyan, fontWeight = FontWeight.Black)
                        Text(translated, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            "LANGUAGE_UNCERTAIN" -> WarningText("Idioma incerto • sem tradução")
            "TRANSCRIPT_UNCERTAIN" -> WarningText("Transcrição incerta • tradução suspensa")
            "MODEL_MISSING" -> WarningText("Modelo de tradução ausente")
        }

        if (item.signals.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            item.signals.forEach { signal ->
                Surface(
                    color = Amber.copy(alpha = .08f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(signal.type.name.replace("_", " "), color = Amber, fontWeight = FontWeight.Bold)
                        Text(
                            "Confiança: " + (signal.confidence * 100).toInt() + "%",
                            color = TextMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text("Evidência: “" + signal.evidence + "”", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Text(
            if (item.engine == ConversationEngine.LOCAL_V2) "Conversa V2" else "Compatibilidade Android",
            color = TextMuted,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun TranslationModeCard(
    mode: TranslationMode,
    onMode: (TranslationMode) -> Unit
) {
    GlassCard {
        Text("Direção da tradução", fontWeight = FontWeight.Bold)
        FilterChip(
            selected = mode == TranslationMode.AUTO_PT_EN,
            onClick = { onMode(TranslationMode.AUTO_PT_EN) },
            label = { Text("Automático PT ↔ EN") },
            leadingIcon = { Icon(Icons.Rounded.SwapHoriz, null) }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == TranslationMode.PT_TO_EN,
                onClick = { onMode(TranslationMode.PT_TO_EN) },
                label = { Text("PT → EN") }
            )
            FilterChip(
                selected = mode == TranslationMode.EN_TO_PT,
                onClick = { onMode(TranslationMode.EN_TO_PT) },
                label = { Text("EN → PT") }
            )
        }
        Text(
            "No automático, cada trecho decide sua rota. Idioma incerto não é traduzido à força.",
            color = TextMuted,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun WarningText(text: String) {
    Spacer(Modifier.height(8.dp))
    Surface(color = Amber.copy(alpha = .08f), shape = RoundedCornerShape(12.dp)) {
        Text(text, modifier = Modifier.padding(10.dp), color = Amber)
    }
}

@Composable
private fun LanguagePill(label: String, confidence: Float?) {
    Surface(
        color = Violet.copy(alpha = .12f),
        contentColor = Violet,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, Violet.copy(alpha = .25f))
    ) {
        Text(
            if (confidence != null && label != "?")
                label + " " + (confidence * 100).toInt() + "%"
            else
                label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CapabilityCard(capability: AiCapability) {
    GlassCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(capability.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            val state = capabilityState(capability.state)
            StatusPill(state.first, state.second)
        }
        Text(capability.detail, color = TextMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SpeechModelRow(model: SpeechLanguageModelState, onDownload: () -> Unit) {
    val state = when (model.status) {
        SpeechLanguageStatus.INSTALLED -> "INSTALADO" to Green
        SpeechLanguageStatus.DOWNLOAD_REQUIRED -> "BAIXAR" to Amber
        SpeechLanguageStatus.PENDING -> "PENDENTE" to Cyan
        SpeechLanguageStatus.UNSUPPORTED -> "NÃO SUPORTADO" to Red
        SpeechLanguageStatus.UNKNOWN -> "DESCONHECIDO" to TextMuted
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(state.first, color = state.second, style = MaterialTheme.typography.labelSmall)
            if (model.detail.isNotBlank()) {
                Text(model.detail, color = TextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (
            model.status == SpeechLanguageStatus.DOWNLOAD_REQUIRED ||
            model.status == SpeechLanguageStatus.UNKNOWN
        ) {
            OutlinedButton(onClick = onDownload) { Text("Instalar") }
        }
    }
}

@Composable
private fun HeroCard(subtitle: String) {
    Surface(
        color = Color.White.copy(alpha = .055f),
        border = BorderStroke(1.dp, Cyan.copy(alpha = .16f)),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = Color(0xFF071018),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Cyan.copy(alpha = .16f))
            ) {
                Image(
                    painter = painterResource(R.drawable.budsia_logo),
                    contentDescription = "BudsIA",
                    modifier = Modifier.padding(4.dp).size(88.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("BudsIA", color = Cyan, fontWeight = FontWeight.Black)
                Text("CONVERSATION AI V2", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(subtitle, color = TextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StatusPill(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = .12f),
        contentColor = color,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, color.copy(alpha = .25f))
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = Color.White.copy(alpha = .05f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .08f)),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(17.dp), content = content)
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    value: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(value, onChange)
    }
}

@Composable
private fun ValueRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextMuted)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun Page(
    title: String,
    subtitle: String,
    content: LazyListScope.() -> Unit
) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Ink, Color(0xFF080C12), Ink))),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text(subtitle, color = TextMuted)
        }
        content()
        item { Spacer(Modifier.height(10.dp)) }
    }
}

private fun modelStatusLabel(status: AdvancedModelStatus): String = when (status) {
    AdvancedModelStatus.READY -> "PRONTO"
    AdvancedModelStatus.DOWNLOADING -> "BAIXANDO"
    AdvancedModelStatus.ERROR -> "ERRO"
    AdvancedModelStatus.NOT_INSTALLED -> "NÃO INSTALADO"
}

private fun languageLabel(tag: String?): String = when (tag?.substringBefore('-')?.lowercase()) {
    "pt" -> "PT"
    "en" -> "EN"
    else -> "?"
}

private fun translationModeLabel(mode: TranslationMode): String = when (mode) {
    TranslationMode.AUTO_PT_EN -> "Automático PT ↔ EN"
    TranslationMode.PT_TO_EN -> "PT → EN"
    TranslationMode.EN_TO_PT -> "EN → PT"
}

private fun capabilityState(state: AiCapabilityState): Pair<String, Color> = when (state) {
    AiCapabilityState.READY -> "PRONTO" to Green
    AiCapabilityState.DOWNLOAD_REQUIRED -> "BAIXAR" to Amber
    AiCapabilityState.UNAVAILABLE -> "INDISPONÍVEL" to Red
    AiCapabilityState.EXPERIMENTAL -> "EXPERIMENTAL" to Violet
    AiCapabilityState.PLANNED -> "PLANEJADO" to TextMuted
    AiCapabilityState.REQUIRES_PERMISSION -> "PERMISSÃO" to Cyan
}

private fun AiCapabilities.asList(): List<AiCapability> = listOf(
    onDeviceSpeech,
    languageId,
    translation,
    speakerDiarization,
    speakerIdentification,
    discourseAnalysis,
    localLlm
)
