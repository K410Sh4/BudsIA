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

    LaunchedEffect(Unit) {
        vm.events.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        containerColor = Ink,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = Panel.copy(alpha = .96f)) {
                val entry by nav.currentBackStackEntryAsState()
                val route = entry?.destination?.route
                listOf(
                    Triple(Routes.HOME, "Home", Icons.Rounded.Home),
                    Triple(Routes.LIVE, "Live", Icons.Rounded.GraphicEq),
                    Triple(Routes.TIMELINE, "Timeline", Icons.Rounded.History),
                    Triple(Routes.MODELS, "Models", Icons.Rounded.Memory),
                    Triple(Routes.PRIVACY, "Privacy", Icons.Rounded.Shield)
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
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding)
        ) {
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

    ScreenList("BudsIA", "Local conversation intelligence") {
        item {
            HeroCard(
                title = if (strictOffline) "LOCAL-FIRST MODE" else "LOCAL MODE",
                subtitle = "Speech → language → translation → explainable analysis"
            )
        }
        item {
            GlassCard {
                ValueRow("Session", if (pipeline.running) "LIVE" else "IDLE")
                ValueRow("Strict offline", if (strictOffline) "ON" else "OFF")
                ValueRow("Stage", pipeline.stage)
                ValueRow("Target language", pipeline.translationTarget.uppercase())
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
                Text("OPEN LIVE CONVERSATION", fontWeight = FontWeight.Bold)
            }
        }
        item { SectionTitle("AI capability status") }
        items(capabilities.asList()) { capability ->
            CapabilityCard(capability)
        }
        item {
            GlassCard {
                Text("V0.1 transparency", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Fallacy and pressure analysis currently uses explicit local rules with visible evidence. Speaker diarization, voice identity and the local LLM are not falsely presented as finished.",
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
    val target by vm.targetLanguage.collectAsState()
    val speechModels by vm.speechLanguageModels.collectAsState()
    var inputLanguage by remember { mutableStateOf("system") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.startLive(inputLanguage.takeUnless { it == "system" })
    }

    ScreenList("Live Conversation", "On-device speech when the phone supports it") {
        item {
            GlassCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(if (pipeline.running) "LIVE" else "STOPPED", fontWeight = FontWeight.Bold, color = if (pipeline.running) Green else TextMuted)
                        Text(pipeline.stage, color = TextMuted)
                    }
                    CapabilityStatePill(
                        AiCapability(
                            "Speech",
                            if (speech.available) AiCapabilityState.READY else AiCapabilityState.UNAVAILABLE,
                            ""
                        )
                    )
                }
            }
        }
        item {
            GlassCard {
                Text("Input language", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to "System", "pt-BR" to "PT", "en-US" to "EN", "es-ES" to "ES").forEach { (tag, label) ->
                        FilterChip(
                            selected = inputLanguage == tag,
                            onClick = { inputLanguage = tag },
                            label = { Text(label) }
                        )
                    }
                }
                val selectedTag = inputLanguage.takeUnless { it == "system" }
                val model = selectedTag?.let { speechModels[it] }
                Spacer(Modifier.height(4.dp))
                if (selectedTag != null && model != null) {
                    SpeechModelStatusRow(model) {
                        vm.downloadSpeechModel(selectedTag)
                    }
                }
                Text(
                    "Language ID now uses a confidence threshold after transcription. Explicit PT/EN/ES selection also tells Android which offline speech model to use.",
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
                    Text("START LOCAL SESSION")
                }
            } else {
                Button(
                    onClick = vm::stopLive,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Red)
                ) {
                    Icon(Icons.Rounded.Stop, null)
                    Spacer(Modifier.width(8.dp))
                    Text("STOP")
                }
            }
        }
        if (speech.partialText.isNotBlank()) {
            item {
                GlassCard {
                    Text("Listening…", color = Cyan, fontWeight = FontWeight.Bold)
                    Text(speech.partialText, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        if (speech.statusMessage != null || speech.error != null) {
            item {
                GlassCard {
                    Text(
                        if (speech.error != null) "Speech status" else "Speech engine",
                        color = if (speech.error != null) Red else Cyan,
                        fontWeight = FontWeight.Bold
                    )
                    speech.statusMessage?.let { Text(it, color = TextMuted) }
                    speech.error?.let { Text(it, color = TextMuted) }
                    if (speech.modelDownloadRequired && speech.requestedLanguage != null) {
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { vm.downloadSpeechModel(speech.requestedLanguage!!) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Download, null)
                            Spacer(Modifier.width(8.dp))
                            Text("INSTALL OFFLINE SPEECH MODEL")
                        }
                    }
                    speech.modelDownloadProgress?.let { progress ->
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        pipeline.lastItem?.let { item ->
            item { ConversationCard(item, target) }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Session timeline", fontWeight = FontWeight.Bold)
                TextButton(onClick = vm::clearSession) { Text("Clear session") }
            }
        }
        items(session.asReversed().take(30)) { item ->
            ConversationCard(item, target, compact = true)
        }
    }
}

@Composable
private fun TimelineScreen(vm: BudsIAViewModel) {
    val timeline by vm.timeline.collectAsState(initial = emptyList())

    ScreenList("Timeline", "Saved locally in Room") {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(timeline.size.toString() + " saved segments", color = TextMuted)
                TextButton(onClick = vm::clearSavedTimeline) {
                    Icon(Icons.Rounded.Delete, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Clear")
                }
            }
        }
        if (timeline.isEmpty()) {
            item {
                GlassCard {
                    Text("No saved conversation yet.", color = TextMuted)
                }
            }
        } else {
            items(timeline) { item -> ConversationCard(item, "pt", compact = true) }
        }
    }
}

@Composable
private fun ModelsScreen(vm: BudsIAViewModel) {
    val downloaded by vm.downloadedLanguages.collectAsState()
    val capabilities by vm.capabilities.collectAsState()
    val speechModels by vm.speechLanguageModels.collectAsState()

    ScreenList("Offline Models", "Downloads only happen when you explicitly request them") {
        item { CapabilityCard(capabilities.onDeviceSpeech) }
        item {
            GlassCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Offline speech models", fontWeight = FontWeight.Bold)
                    TextButton(onClick = vm::refreshSpeechModels) {
                        Icon(Icons.Rounded.Refresh, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Refresh")
                    }
                }
                listOf(
                    "pt-BR" to "Português (Brasil)",
                    "en-US" to "English (US)",
                    "es-ES" to "Español"
                ).forEach { (tag, name) ->
                    Spacer(Modifier.height(6.dp))
                    Text(name, fontWeight = FontWeight.Medium)
                    SpeechModelStatusRow(
                        speechModels[tag] ?: SpeechLanguageModelState(tag)
                    ) {
                        vm.downloadSpeechModel(tag)
                    }
                }
            }
        }
        item { CapabilityCard(capabilities.languageId) }
        item { CapabilityCard(capabilities.translation) }
        item {
            GlassCard {
                Text("Translation models", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                listOf("pt" to "Portuguese", "en" to "English", "es" to "Spanish").forEach { (tag, name) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(name)
                            Text(if (tag in downloaded) "INSTALLED" else "NOT INSTALLED", color = if (tag in downloaded) Green else TextMuted, style = MaterialTheme.typography.labelSmall)
                        }
                        if (tag !in downloaded) {
                            OutlinedButton(onClick = { vm.downloadModel(tag) }) {
                                Text("Download")
                            }
                        }
                    }
                }
            }
        }
        item { CapabilityCard(capabilities.speakerDiarization) }
        item { CapabilityCard(capabilities.speakerIdentification) }
        item { CapabilityCard(capabilities.localLlm) }
        item {
            GlassCard {
                Text("Planned local AI stack", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("• Speaker diarization: sherpa-onnx", color = TextMuted)
                Text("• Speaker embeddings: local model", color = TextMuted)
                Text("• LLM: Gemini Nano capability probe", color = TextMuted)
                Text("• LLM fallback: LiteRT-LM + quantized Gemma", color = TextMuted)
            }
        }
    }
}

@Composable
private fun PrivacyScreen(vm: BudsIAViewModel) {
    val strict by vm.strictOffline.collectAsState()
    val save by vm.saveTranscript.collectAsState()
    val target by vm.targetLanguage.collectAsState()

    ScreenList("Privacy", "Local-first controls") {
        item {
            GlassCard {
                SettingSwitch(
                    "Strict offline sessions",
                    "Live sessions never trigger a model download or cloud fallback.",
                    strict,
                    vm::setStrictOffline
                )
                HorizontalDivider(color = Color.White.copy(alpha = .08f))
                SettingSwitch(
                    "Save transcript locally",
                    "If disabled, new segments remain only in the active in-memory session.",
                    save,
                    vm::setSaveTranscript
                )
            }
        }
        item {
            GlassCard {
                Text("Translation target", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("pt" to "Português", "en" to "English", "es" to "Español").forEach { (tag, label) ->
                        FilterChip(
                            selected = target == tag,
                            onClick = { vm.setTargetLanguage(tag) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
        item {
            GlassCard {
                Text("Data policy in V0.1", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("• Raw microphone audio is not stored.", color = TextMuted)
                Text("• No hidden background recording.", color = TextMuted)
                Text("• No claim of reading thoughts or hidden intent.", color = TextMuted)
                Text("• Translation model downloads are explicit.", color = TextMuted)
                Text("• Speaker identity is not implemented yet.", color = TextMuted)
            }
        }
    }
}

@Composable
private fun ConversationCard(item: ConversationItem, target: String, compact: Boolean = false) {
    GlassCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(item.speakerLabel, fontWeight = FontWeight.Bold)
            Text(
                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp)),
                color = TextMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(item.originalText, style = if (compact) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium)
        item.languageTag?.let {
            Text("Language: $it", color = TextMuted, style = MaterialTheme.typography.labelSmall)
        }
        item.translatedText?.takeIf { it != item.originalText }?.let {
            Spacer(Modifier.height(10.dp))
            Surface(
                color = Cyan.copy(alpha = .08f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Translation → " + target.uppercase(), color = Cyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(it)
                }
            }
        }
        if (item.signals.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            item.signals.forEach { signal ->
                Surface(
                    color = Amber.copy(alpha = .09f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(signal.type.name.replace("_", " "), color = Amber, fontWeight = FontWeight.Bold)
                        Text("Confidence: " + (signal.confidence * 100).toInt() + "%", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text("Evidence: “" + signal.evidence + "”", style = MaterialTheme.typography.bodySmall)
                        Text(signal.explanation, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
            }
        }
    }
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
        AiCapabilityState.READY -> "READY" to Green
        AiCapabilityState.DOWNLOAD_REQUIRED -> "DOWNLOAD" to Amber
        AiCapabilityState.UNAVAILABLE -> "UNAVAILABLE" to Red
        AiCapabilityState.EXPERIMENTAL -> "EXPERIMENTAL" to Violet
        AiCapabilityState.PLANNED -> "PLANNED" to TextMuted
        AiCapabilityState.REQUIRES_PERMISSION -> "PERMISSION" to Cyan
    }
    Surface(
        color = color.copy(alpha = .12f),
        contentColor = color,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, color.copy(alpha = .25f))
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HeroCard(title: String, subtitle: String) {
    Surface(
        color = Color.White.copy(alpha = .055f),
        border = BorderStroke(1.dp, Cyan.copy(alpha = .16f)),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color(0xFF071018),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Cyan.copy(alpha = .16f))
            ) {
                Image(
                    painter = painterResource(R.drawable.budsia_logo),
                    contentDescription = "BudsIA logo",
                    modifier = Modifier.padding(4.dp).size(88.dp)
                )
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
private fun SpeechModelStatusRow(
    model: SpeechLanguageModelState,
    onDownload: () -> Unit
) {
    val (label, color) = when (model.status) {
        SpeechLanguageStatus.INSTALLED -> "INSTALLED" to Green
        SpeechLanguageStatus.DOWNLOAD_REQUIRED -> "DOWNLOAD" to Amber
        SpeechLanguageStatus.PENDING -> "PENDING" to Cyan
        SpeechLanguageStatus.UNSUPPORTED -> "UNSUPPORTED" to Red
        SpeechLanguageStatus.UNKNOWN -> "UNKNOWN" to TextMuted
    }

    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            if (model.detail.isNotBlank()) {
                Text(model.detail, color = TextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (model.status == SpeechLanguageStatus.DOWNLOAD_REQUIRED ||
            model.status == SpeechLanguageStatus.UNKNOWN) {
            OutlinedButton(onClick = onDownload) {
                Icon(Icons.Rounded.Download, null)
                Spacer(Modifier.width(4.dp))
                Text("Install")
            }
        }
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
private fun ScreenList(
    title: String,
    subtitle: String,
    content: LazyListScope.() -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Ink, Color(0xFF080C12), Ink))
        ),
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

private fun AiCapabilities.asList(): List<AiCapability> = listOf(
    onDeviceSpeech,
    languageId,
    translation,
    discourseAnalysis,
    speakerDiarization,
    speakerIdentification,
    localLlm
)
