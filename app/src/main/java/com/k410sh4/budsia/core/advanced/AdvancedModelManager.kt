package com.k410sh4.budsia.core.advanced

import android.content.Context
import com.k410sh4.budsia.domain.model.AdvancedModelState
import com.k410sh4.budsia.domain.model.AdvancedModelStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdvancedModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val SAMPLE_RATE = 16_000
        const val VARIANT = "Whisper Base + Pyannote 3.0 + ERes2Net"

        private const val WHISPER_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-base.tar.bz2"
        private const val SEGMENTATION_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-segmentation-models/sherpa-onnx-pyannote-segmentation-3-0.tar.bz2"
        private const val SPEAKER_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/3dspeaker_speech_eres2net_base_200k_sv_zh-cn_16k-common.onnx"

        const val WHISPER_ENCODER = "base-encoder.int8.onnx"
        const val WHISPER_DECODER = "base-decoder.int8.onnx"
        const val WHISPER_TOKENS = "base-tokens.txt"
        const val SEGMENTATION_FILE = "segmentation.onnx"
        const val SPEAKER_FILE = "embedding.onnx"
    }

    val modelDir: File
        get() = File(context.filesDir, "models/conversation_v2").apply { mkdirs() }

    private val _state = MutableStateFlow(currentState())
    val state: StateFlow<AdvancedModelState> = _state.asStateFlow()

    fun whisperEncoder(): File = File(modelDir, WHISPER_ENCODER)
    fun whisperDecoder(): File = File(modelDir, WHISPER_DECODER)
    fun whisperTokens(): File = File(modelDir, WHISPER_TOKENS)
    fun segmentationFile(): File = File(modelDir, SEGMENTATION_FILE)
    fun speakerFile(): File = File(modelDir, SPEAKER_FILE)

    fun isReady(): Boolean = requiredFiles().all { it.exists() && it.length() >= minimumSize(it.name) }

    fun refresh() {
        _state.value = currentState()
    }

    suspend fun installAll(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            modelDir.mkdirs()
            _state.value = AdvancedModelState(
                status = AdvancedModelStatus.DOWNLOADING,
                message = "Preparando pacote de conversação V2…"
            )

            installWhisperBase()
            installSegmentation()

            download(
                url = SPEAKER_URL,
                target = speakerFile(),
                label = "Identidade de voz ERes2Net"
            )

            check(isReady()) {
                "O pacote V2 terminou incompleto. Nenhum modelo parcial será marcado como pronto."
            }

            cleanupLegacyFiles()

            _state.value = AdvancedModelState(
                status = AdvancedModelStatus.READY,
                progress = 1f,
                message = "Pacote V2 pronto para funcionar offline",
                installedVariant = VARIANT
            )
        }.onFailure {
            _state.value = AdvancedModelState(
                status = AdvancedModelStatus.ERROR,
                progress = 0f,
                message = it.message ?: "Falha ao instalar pacote V2"
            )
        }
    }

    suspend fun removeAll() = withContext(Dispatchers.IO) {
        runCatching { modelDir.deleteRecursively() }
        modelDir.mkdirs()
        _state.value = currentState()
    }

    private fun currentState(): AdvancedModelState =
        if (isReady()) {
            AdvancedModelState(
                status = AdvancedModelStatus.READY,
                progress = 1f,
                message = "Pacote V2 pronto para funcionar offline",
                installedVariant = VARIANT
            )
        } else {
            AdvancedModelState(
                status = AdvancedModelStatus.NOT_INSTALLED,
                message = "Instale Whisper Base + diarização Pyannote + ERes2Net"
            )
        }

    private fun installWhisperBase() {
        if (whisperEncoder().isValid() && whisperDecoder().isValid() && whisperTokens().isValid()) return

        val archive = File(modelDir, "whisper-base.tar.bz2")
        download(
            url = WHISPER_URL,
            target = archive,
            label = "Whisper Base multilíngue"
        )

        extractSelected(
            archive = archive,
            wanted = mapOf(
                WHISPER_ENCODER to whisperEncoder(),
                WHISPER_DECODER to whisperDecoder(),
                WHISPER_TOKENS to whisperTokens()
            ),
            label = "Whisper Base"
        )
        archive.delete()
    }

    private fun installSegmentation() {
        if (segmentationFile().isValid()) return

        val archive = File(modelDir, "pyannote-segmentation.tar.bz2")
        download(
            url = SEGMENTATION_URL,
            target = archive,
            label = "Pyannote Segmentation 3.0"
        )

        extractSelected(
            archive = archive,
            wanted = mapOf("model.onnx" to segmentationFile()),
            label = "Pyannote Segmentation"
        )
        archive.delete()
    }

    private fun requiredFiles(): List<File> = listOf(
        whisperEncoder(),
        whisperDecoder(),
        whisperTokens(),
        segmentationFile(),
        speakerFile()
    )

    private fun File.isValid(): Boolean = exists() && length() >= minimumSize(name)

    private fun minimumSize(name: String): Long = when (name) {
        WHISPER_ENCODER -> 20L * 1024 * 1024
        WHISPER_DECODER -> 20L * 1024 * 1024
        WHISPER_TOKENS -> 100L * 1024
        SEGMENTATION_FILE -> 4L * 1024 * 1024
        SPEAKER_FILE -> 20L * 1024 * 1024
        else -> 1L
    }

    private fun download(url: String, target: File, label: String) {
        if (target.exists() && target.length() > 0) return

        val partial = File(target.absolutePath + ".part")
        partial.delete()

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 180_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "BudsIA/2.0")
        }

        try {
            connection.connect()
            check(connection.responseCode in 200..299) {
                "Falha HTTP ${connection.responseCode} ao baixar $label"
            }

            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var downloaded = 0L
                    var lastUiUpdate = 0L

                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read

                        if (downloaded - lastUiUpdate >= 1L * 1024 * 1024 || downloaded == total) {
                            lastUiUpdate = downloaded
                            val progress = if (total > 0) {
                                (downloaded.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
                            } else 0f

                            _state.value = AdvancedModelState(
                                status = AdvancedModelStatus.DOWNLOADING,
                                currentFile = label,
                                progress = progress,
                                message = "Baixando $label"
                            )
                        }
                    }
                    output.fd.sync()
                }
            }

            check(partial.length() > 0) { "Download vazio: $label" }
            if (target.exists()) target.delete()
            check(partial.renameTo(target)) { "Não foi possível finalizar $label" }
        } finally {
            connection.disconnect()
            if (!target.exists()) partial.delete()
        }
    }

    private fun extractSelected(
        archive: File,
        wanted: Map<String, File>,
        label: String
    ) {
        _state.value = AdvancedModelState(
            status = AdvancedModelStatus.DOWNLOADING,
            currentFile = label,
            progress = 1f,
            message = "Extraindo $label…"
        )

        val extracted = mutableSetOf<String>()

        BZip2CompressorInputStream(archive.inputStream().buffered()).use { bz2 ->
            TarArchiveInputStream(bz2).use { tar ->
                while (true) {
                    val entry = tar.nextEntry ?: break
                    if (entry.isDirectory) continue

                    val baseName = entry.name.substringAfterLast('/')
                    val output = wanted[baseName] ?: continue
                    val partial = File(output.absolutePath + ".part")

                    FileOutputStream(partial).use { tar.copyTo(it) }
                    check(partial.length() >= minimumSize(output.name)) {
                        "Arquivo extraído inválido: $baseName"
                    }

                    if (output.exists()) output.delete()
                    check(partial.renameTo(output)) {
                        "Não foi possível finalizar arquivo $baseName"
                    }
                    extracted += baseName
                }
            }
        }

        val missing = wanted.keys - extracted
        check(missing.isEmpty()) {
            "Arquivos ausentes no pacote $label: ${missing.joinToString()}"
        }
    }

    private fun cleanupLegacyFiles() {
        val oldDir = File(context.filesDir, "models/advanced")
        if (oldDir.exists()) {
            runCatching { oldDir.deleteRecursively() }
        }
    }
}
