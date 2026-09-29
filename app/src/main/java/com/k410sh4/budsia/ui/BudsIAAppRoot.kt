package com.k410sh4.budsia.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
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
    const val TIMELINE = "timeline"
    const val MODELS = "models"
    const val PRIVACY = "privacy"
}

@Composable
fun BudsIAAppRoot(vm: BudsIAViewModel) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        containerColor = Ink,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = Panel.copy(alpha = .96f)) {
                val entry by nav.currentBackStackEntryAsState()
                val route = entry?.destination?.route
                listOf(
                    Triple(Routes.HOME, "Início", Icons.Rounded.Home),
                    Triple(Routes.LIVE, "Ao vivo", Icons.Rounded.GraphicEq),
                    Triple(Routes.TIMELINE, "Histórico", Icons.Rounded.History),
                    Triple(Routes.MODELS, "Modelos", Icons.Rounded.Memory),
                    Triple(Routes.PRIVACY, "Privacidade", Icons.Rounded.Shield)
                ).forEach { (r, label, icon) ->
                    NavigationBarItem(
                        selected = route == r,
                        onClick = { nav.navigate(r) { launchSingleTop = true } },
                        icon = { Icon(icon, null) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(navController = nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
            composable(Routes.HOME) { HomeScreen(vm) { nav.navigate(Routes.LIVE) } }
            composable(Routes.LIVE) { LiveScreen(vm) }
            composable(Routes.TIMELINE) { TimelineScreen(vm) }
            composable(Routes.MODELS) { ModelsScreen(vm) }
            composable(Routes.PRIVACY) { PrivacyScreen(vm) }
        }
    }
}

@Composable
private fun HomeScreen(vm: BudsIAViewModel, openLive: () -> Unit) {
    val capabilities by vm.capabilities.collectAsState()
    val strictOffline by vm.strictOffline.collectAsState()
    val pipeline by vm.pipeline.collectAsState()
    val mode by vm.translationMode.collectAsState()

    ScreenList("BudsIA", "Inteligência local para conversas") {
        item {
            HeroCard(
                title = if (strictOffline) "MODO LOCAL OFFLINE" else "MODO LOCAL",
                subtitle = "Voz → idioma → tradução → análise explicável"
            )
        }
        item {
            GlassCard {
                ValueRow("Sessão", if (pipeline.running) "AO VIVO" else "PARADA")
                ValueRow("Offline rígido", if (strictOffline) "ATIVO" else "DESATIVADO")
                ValueRow("Etapa", pipeline.stage)
                ValueRow("Tradução", translationModeLabel(mode))
            }
        }
        item {
            Button(
                onClick = openLive,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Rounded.Mic, null)
                Spacer(Modifier.width(8.dp))
                Text("ABRIR CONVERSA AO VIVO", fontWeight = FontWeight.Bold)
            }
        }
        item { SectionTitle("Capacidades da IA") }
        items(capabilities.asList()) { CapabilityCard(it) }
        item {
            GlassCard {
                Text("Transparência", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "A análise de falácias e pressão ainda usa regras locais explicáveis. A diarização real de falantes exige uma nova captura de áudio local e está sendo preparada separadamente — o app não finge reconhecer pessoas enquanto isso não estiver validado.",
                    color = TextMuted
                )
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
    val speechModels by vm.speechLanguageModels.collectAsState()
    val translationMode by vm.translationMode.collectAsState()
    var inputLanguage by remember { mutableStateOf("system") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.startLive(inputLanguage.takeUnless { it == "system" })
    }

    ScreenList("Conversa ao vivo", "Reconhecimento e tradução locais") {
        item {
            GlassCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(if (pipeline.running) "AO VIVO" else "PARADO", fontWeight = FontWeight.Bold, color = if (pipeline.running) Green else TextMuted)
                        Text(pipeline.stage, color = TextMuted)
                    }
                    CapabilityStatePill(AiCapability("Voz", if (speech.available) AiCapabilityState.READY else AiCapabilityState.UNAVAILABLE, ""))
                }
            }
        }
        item {
            GlassCard {
                Text("Direção da tradução", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                TranslationModeChip("Automático  PT ↔ EN", translationMode == TranslationMode.AUTO_PT_EN) {
                    vm.setTranslationMode(TranslationMode.AUTO_PT_EN)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = translationMode == TranslationMode.PT_TO_EN,
                        onClick = { vm.setTranslationMode(TranslationMode.PT_TO_EN) },
                        label = { Text("PT → EN") }
                    )
                    FilterChip(
                        selected = translationMode == TranslationMode.EN_TO_PT,
                        onClick = { vm.setTranslationMode(TranslationMode.EN_TO_PT) },
                        label = { Text("EN → PT") }
                    )
                }
                Text(
                    "No automático, cada fala é tratada separadamente: português vira inglês e inglês vira português.",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        item {
            GlassCard {
                Text("Idioma esperado da entrada", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to "Sistema", "pt-BR" to "PT", "en-US" to "EN", "es-ES" to "ES").forEach { (tag, label) ->
                        FilterChip(
                            selected = inputLanguage == tag,
                            onClick = { inputLanguage = tag },
                            label = { Text(label) }
                        )
                    }
                }
                val selectedTag = inputLanguage.takeUnless { it == "system" }
                val model = selectedTag?.let { speechModels[it] }
                if (selectedTag != null && model != null) {
                    SpeechModelStatusRow(model) { vm.downloadSpeechModel(selectedTag) }
                }
                Text(
                    "A detecção do idioma usa confiança. Ao escolher PT/EN/ES, o Android também recebe essa dica para o modelo de voz offline.",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        item {
            if (!pipeline.running) {
                Button(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            vm.startLive(inputLanguage.takeUnless { it == "system" })
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    enabled = speech.available
                ) {
                    Icon(Icons.Rounded.Mic, null)
                    Spacer(Modifier.width(8.dp))
                    Text("INICIAR SESSÃO LOCAL")
                }
            } else {
                Button(
                    onClick = vm::stopLive,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Red)
                ) {
                    Icon(Icons.Rounded.Stop, null)
                    Spacer(Modifier.width(8.dp))
                    Text("PARAR")
                }
            }
        }
        if (speech.partialText.isNotBlank()) {
            item {
                GlassCard {
                    Text("Escutando…", color = Cyan, fontWeight = FontWeight.Bold)
                    Text(speech.partialText, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        if (speech.statusMessage != null || speech.error != null) {
            item {
                GlassCard {
                    Text(if (speech.error != null) "Status da voz" else "Motor de voz", color = if (speech.error != null) Red else Cyan, fontWeight = FontWeight.Bold)
                    speech.statusMessage?.let { Text(it, color = TextMuted) }
                    speech.error?.let { Text(it, color = TextMuted) }
                    if (speech.modelDownloadRequired && speech.requestedLanguage != null) {
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { vm.downloadSpeechModel(speech.requestedLanguage!!) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Download, null)
                            Spacer(Modifier.width(8.dp))
                            Text("INSTALAR MODELO DE VOZ OFFLINE")
                        }
                    }
                    speech.modelDownloadProgress?.let { progress ->
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        pipeline.lastItem?.let { current -> item { ConversationCard(current) } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Linha do tempo da sessão", fontWeight = FontWeight.Bold)
                TextButton(onClick = vm::clearSession) { Text("Limpar sessão") }
            }
        }
        items(session.asReversed().take(30)) { conversation -> ConversationCard(conversation, compact = true) }
    }
}

@Composable
private fun TimelineScreen(vm: BudsIAViewModel) {
    val timeline by vm.timeline.collectAsState(initial = emptyList())
    ScreenList("Histórico", "Conversas salvas localmente no aparelho") {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(timeline.size.toString() + " trechos salvos", color = TextMuted)
                TextButton(onClick = vm::clearSavedTimeline) {
                    Icon(Icons.Rounded.Delete, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Limpar")
                }
            }
        }
        if (timeline.isEmpty()) {
            item { GlassCard { Text("Nenhuma conversa salva ainda.", color = TextMuted) } }
        } else {
            items(timeline) { conversation -> ConversationCard(conversation, compact = true) }
        }
    }
}

@Composable
private fun ModelsScreen(vm: BudsIAViewModel) {
    val downloaded by vm.downloadedLanguages.collectAsState()
    val capabilities by vm.capabilities.collectAsState()
    val speechModels by vm.speechLanguageModels.collectAsState()

    ScreenList("Modelos offline", "Downloads só acontecem quando você pedir") {
        item { CapabilityCard(capabilities.onDeviceSpeech) }
        item {
            GlassCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Modelos de reconhecimento de voz", fontWeight = FontWeight.Bold)
                    TextButton(onClick = vm::refreshSpeechModels) {
                        Icon(Icons.Rounded.Refresh, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Atualizar")
                    }
                }
                listOf(
                    "pt-BR" to "Português (Brasil)",
                    "en-US" to "Inglês (EUA)",
                    "es-ES" to "Espanhol"
                ).forEach { (tag, name) ->
                    Spacer(Modifier.height(6.dp))
                    Text(name, fontWeight = FontWeight.Medium)
                    SpeechModelStatusRow(speechModels[tag] ?: SpeechLanguageModelState(tag)) {
                        vm.downloadSpeechModel(tag)
                    }
                }
            }
        }
        item { CapabilityCard(capabilities.languageId) }
        item { CapabilityCard(capabilities.translation) }
        item {
            GlassCard {
                Text("Modelos de tradução", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                listOf("pt" to "Português", "en" to "Inglês", "es" to "Espanhol").forEach { (tag, name) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name)
                            Text(if (tag in downloaded) "INSTALADO" else "NÃO INSTALADO", color = if (tag in downloaded) Green else TextMuted, style = MaterialTheme.typography.labelSmall)
                        }
                        if (tag !in downloaded) {
                            OutlinedButton(onClick = { vm.downloadModel(tag) }) { Text("Baixar") }
                        }
                    }
                }
            }
        }
        item { CapabilityCard(capabilities.speakerDiarization) }
        item {
            GlassCard {
                Text("Diarização de falantes", fontWeight = FontWeight.Bold)
                Text(
                    "A versão atual recebe texto do SpeechRecognizer do Android, que não entrega a voz bruta necessária para separar Falante A/B com segurança. O modo avançado será migrado para AudioRecord + sherpa-onnx para fazer diarização offline real.",
                    color = TextMuted
                )
            }
        }
        item { CapabilityCard(capabilities.speakerIdentification) }
        item { CapabilityCard(capabilities.localLlm) }
    }
}

@Composable
private fun PrivacyScreen(vm: BudsIAViewModel) {
    val strict by vm.strictOffline.collectAsState()
    val save by vm.saveTranscript.collectAsState()
    val mode by vm.translationMode.collectAsState()

    ScreenList("Privacidade", "Controles locais e transparentes") {
        item {
            GlassCard {
                SettingSwitch("Sessões estritamente offline", "Sessões ao vivo não fazem download nem fallback para nuvem.", strict, vm::setStrictOffline)
                HorizontalDivider(color = Color.White.copy(alpha = .08f))
                SettingSwitch("Salvar transcrição localmente", "Desative para manter novos trechos apenas na memória da sessão.", save, vm::setSaveTranscript)
            }
        }
        item {
            GlassCard {
                Text("Tradução padrão", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                TranslationModeChip("Automático PT ↔ EN", mode == TranslationMode.AUTO_PT_EN) { vm.setTranslationMode(TranslationMode.AUTO_PT_EN) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == TranslationMode.PT_TO_EN, onClick = { vm.setTranslationMode(TranslationMode.PT_TO_EN) }, label = { Text("PT → EN") })
                    FilterChip(selected = mode == TranslationMode.EN_TO_PT, onClick = { vm.setTranslationMode(TranslationMode.EN_TO_PT) }, label = { Text("EN → PT") })
                }
            }
        }
        item {
            GlassCard {
                Text("Política de dados", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("• O áudio bruto do microfone não é salvo.", color = TextMuted)
                Text("• Não existe gravação oculta em segundo plano.", color = TextMuted)
                Text("• O app não afirma ler pensamentos ou intenções ocultas.", color = TextMuted)
                Text("• Downloads de modelos são explícitos.", color = TextMuted)
                Text("• Identificação de pessoas por voz ainda não está habilitada.", color = TextMuted)
            }
        }
    }
}

@Composable
private fun ConversationCard(item: ConversationItem, compact: Boolean = false) {
    val source = displayLanguage(item.languageTag)
    val target = displayLanguage(item.translationTargetTag)

    GlassCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(item.speakerLabel, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    LanguageBadge(source)
                    if (item.translationTargetTag != null) {
                        Icon(Icons.Rounded.ArrowForward, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                        LanguageBadge(target)
                    }
                }
            }
            Text(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp)), color = TextMuted, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(8.dp))
        Text(item.originalText, style = if (compact) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium)

        item.languageConfidence?.takeIf { it > 0f }?.let { confidence ->
            Text("Confiança do idioma: " + (confidence * 100).toInt() + "%", color = TextMuted, style = MaterialTheme.typography.labelSmall)
        }

        item.translatedText?.takeIf { it != item.originalText }?.let { translated ->
            Spacer(Modifier.height(10.dp))
            Surface(color = Cyan.copy(alpha = .08f), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(source + "  →  " + target, color = Cyan, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                    Text(translated)
                }
            }
        }

        if (item.signals.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            item.signals.forEach { signal ->
                Surface(color = Amber.copy(alpha = .09f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(signal.type.name.replace("_", " "), color = Amber, fontWeight = FontWeight.Bold)
                        Text("Confiança: " + (signal.confidence * 100).toInt() + "%", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text("Evidência: “" + signal.evidence + "”", style = MaterialTheme.typography.bodySmall)
                        Text(signal.explanation, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageBadge(label: String) {
    Surface(color = Violet.copy(alpha = .12f), contentColor = Violet, shape = RoundedCornerShape(999.dp), border = BorderStroke(1.dp, Violet.copy(alpha = .25f))) {
        Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TranslationModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) }, leadingIcon = {
        Icon(Icons.Rounded.SwapHoriz, null, modifier = Modifier.size(18.dp))
    })
}

@Composable
private fun CapabilityCard(capability: AiCapability) {
    GlassCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(capability.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            CapabilityStatePill(capability)
        }
        Spacer(Modifier.height(6.dp))
        Text(capability.detail, color = TextMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CapabilityStatePill(capability: AiCapability) {
    val (label, color) = when (capability.state) {
        AiCapabilityState.READY -> "PRONTO" to Green
        AiCapabilityState.DOWNLOAD_REQUIRED -> "BAIXAR" to Amber
        AiCapabilityState.UNAVAILABLE -> "INDISPONÍVEL" to Red
        AiCapabilityState.EXPERIMENTAL -> "EXPERIMENTAL" to Violet
        AiCapabilityState.PLANNED -> "PLANEJADO" to TextMuted
        AiCapabilityState.REQUIRES_PERMISSION -> "PERMISSÃO" to Cyan
    }
    Surface(color = color.copy(alpha = .12f), contentColor = color, shape = RoundedCornerShape(999.dp), border = BorderStroke(1.dp, color.copy(alpha = .25f))) {
        Text(label, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HeroCard(title: String, subtitle: String) {
    Surface(color = Color.White.copy(alpha = .055f), border = BorderStroke(1.dp, Cyan.copy(alpha = .16f)), shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Color(0xFF071018), shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, Cyan.copy(alpha = .16f))) {
                Image(painter = painterResource(R.drawable.budsia_logo), contentDescription = "Logo BudsIA", modifier = Modifier.padding(4.dp).size(88.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("BudsIA", color = Cyan, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(subtitle, color = TextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SpeechModelStatusRow(model: SpeechLanguageModelState, onDownload: () -> Unit) {
    val (label, color) = when (model.status) {
        SpeechLanguageStatus.INSTALLED -> "INSTALADO" to Green
        SpeechLanguageStatus.DOWNLOAD_REQUIRED -> "BAIXAR" to Amber
        SpeechLanguageStatus.PENDING -> "PENDENTE" to Cyan
        SpeechLanguageStatus.UNSUPPORTED -> "NÃO SUPORTADO" to Red
        SpeechLanguageStatus.UNKNOWN -> "DESCONHECIDO" to TextMuted
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            if (model.detail.isNotBlank()) Text(model.detail, color = TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        if (model.status == SpeechLanguageStatus.DOWNLOAD_REQUIRED || model.status == SpeechLanguageStatus.UNKNOWN) {
            OutlinedButton(onClick = onDownload) {
                Icon(Icons.Rounded.Download, null)
                Spacer(Modifier.width(4.dp))
                Text("Instalar")
            }
        }
    }
}

@Composable
private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color.White.copy(alpha = .05f), border = BorderStroke(1.dp, Color.White.copy(alpha = .08f)), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(17.dp), content = content)
    }
}

@Composable
private fun ValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextMuted)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(value, onChange)
    }
}

@Composable
private fun ScreenList(title: String, subtitle: String, content: LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Ink, Color(0xFF080C12), Ink))),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text(subtitle, color = TextMuted)
            }
        }
        content()
        item { Spacer(Modifier.height(10.dp)) }
    }
}

private fun displayLanguage(tag: String?): String = when (tag?.substringBefore('-')?.lowercase()) {
    "pt" -> "PT"
    "en" -> "EN"
    "es" -> "ES"
    "fr" -> "FR"
    "de" -> "DE"
    "it" -> "IT"
    null -> "?"
    else -> tag.substringBefore('-').uppercase()
}

private fun translationModeLabel(mode: TranslationMode): String = when (mode) {
    TranslationMode.AUTO_PT_EN -> "Automático PT ↔ EN"
    TranslationMode.PT_TO_EN -> "PT → EN"
    TranslationMode.EN_TO_PT -> "EN → PT"
}

private fun AiCapabilities.asList(): List<AiCapability> = listOf(
    onDeviceSpeech,
    languageId,
    translation,
    discourseAnalysis,
    speakerDiarization,
    speakerIdentification,
    localLlm
)
