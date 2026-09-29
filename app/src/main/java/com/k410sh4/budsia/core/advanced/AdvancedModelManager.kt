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

        private const val VAD_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"
        private const val WHISPER_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2"
        private const val SPEAKER_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx"

        const val VAD_FILE = "silero_vad.onnx"
        const val WHISPER_ENCODER = "tiny-encoder.int8.onnx"
        const val WHISPER_DECODER = "tiny-decoder.int8.onnx"
        const val WHISPER_TOKENS = "tiny-tokens.txt"
        const val SPEAKER_FILE = "speaker-eres2net.onnx"
    }

    val modelDir: File
        get() = File(context.filesDir, "models/advanced").apply { mkdirs() }

    private val _state = MutableStateFlow(currentState())
    val state: StateFlow<AdvancedModelState> = _state.asStateFlow()

    fun isReady(): Boolean = requiredFiles().all { it.exists() && it.length() > 0 }

    fun vadFile(): File = File(modelDir, VAD_FILE)
    fun whisperEncoder(): File = File(modelDir, WHISPER_ENCODER)
    fun whisperDecoder(): File = File(modelDir, WHISPER_DECODER)
    fun whisperTokens(): File = File(modelDir, WHISPER_TOKENS)
    fun speakerFile(): File = File(modelDir, SPEAKER_FILE)

    fun refresh() {
        _state.value = currentState()
    }

    suspend fun installAll(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            modelDir.mkdirs()
            _state.value = AdvancedModelState(
                AdvancedModelStatus.DOWNLOADING,
                message = "Preparando modelos locais de voz…"
            )

            download(
                url = VAD_URL,
                target = vadFile(),
                label = "Detector de fala (Silero VAD)"
            )

            if (!whisperEncoder().exists() ||
                !whisperDecoder().exists() ||
                !whisperTokens().exists()
            ) {
                val archive = File(modelDir, "whisper-tiny.tar.bz2")
                download(
                    url = WHISPER_URL,
                    target = archive,
                    label = "Whisper Tiny multilíngue"
                )
                extractWhisper(archive)
                archive.delete()
            }

            download(
                url = SPEAKER_URL,
                target = speakerFile(),
                label = "Modelo de voz ERes2Net"
            )

            check(isReady()) { "Algum arquivo do motor avançado não foi instalado corretamente." }

            _state.value = AdvancedModelState(
                status = AdvancedModelStatus.READY,
                progress = 1f,
                message = "Motor avançado pronto para uso offline"
            )
        }.onFailure {
            _state.value = AdvancedModelState(
                status = AdvancedModelStatus.ERROR,
                progress = 0f,
                message = it.message ?: "Falha ao instalar modelos avançados"
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
                message = "Motor avançado pronto para uso offline"
            )
        } else {
            AdvancedModelState(
                status = AdvancedModelStatus.NOT_INSTALLED,
                message = "Instale VAD + Whisper multilíngue + modelo de voz para separar falantes"
            )
        }

    private fun requiredFiles(): List<File> = listOf(
        vadFile(),
        whisperEncoder(),
        whisperDecoder(),
        whisperTokens(),
        speakerFile()
    )

    private fun download(url: String, target: File, label: String) {
        if (target.exists() && target.length() > 0) return

        val partial = File(target.absolutePath + ".part")
        partial.delete()

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 120_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "BudsIA/0.4")
        }

        try {
            connection.connect()
            check(connection.responseCode in 200..299) {
                "Falha HTTP " + connection.responseCode + " ao baixar " + label
            }

            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val progress = if (total > 0) {
                            (downloaded.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
                        } else 0f

                        _state.value = AdvancedModelState(
                            status = AdvancedModelStatus.DOWNLOADING,
                            currentFile = label,
                            progress = progress,
                            message = "Baixando " + label
                        )
                    }
                    output.fd.sync()
                }
            }

            check(partial.length() > 0) { "Download vazio: $label" }
            if (target.exists()) target.delete()
            check(partial.renameTo(target)) { "Não foi possível finalizar o arquivo $label" }
        } finally {
            connection.disconnect()
            if (!target.exists()) partial.delete()
        }
    }

    private fun extractWhisper(archive: File) {
        _state.value = AdvancedModelState(
            status = AdvancedModelStatus.DOWNLOADING,
            currentFile = "Whisper Tiny",
            progress = 1f,
            message = "Extraindo modelo multilíngue…"
        )

        val wanted = mapOf(
            WHISPER_ENCODER to whisperEncoder(),
            WHISPER_DECODER to whisperDecoder(),
            WHISPER_TOKENS to whisperTokens()
        )

        val extracted = mutableSetOf<String>()

        BZip2CompressorInputStream(archive.inputStream().buffered()).use { bz2 ->
            TarArchiveInputStream(bz2).use { tar ->
                while (true) {
                    val entry = tar.nextEntry ?: break
                    if (entry.isDirectory) continue

                    val baseName = entry.name.substringAfterLast('/')
                    val output = wanted[baseName] ?: continue

                    FileOutputStream(output).use { tar.copyTo(it) }
                    check(output.length() > 0) { "Arquivo Whisper extraído vazio: $baseName" }
                    extracted += baseName
                }
            }
        }

        val missing = wanted.keys - extracted
        check(missing.isEmpty()) {
            "Arquivos ausentes no pacote Whisper: " + missing.joinToString()
        }
    }
}
