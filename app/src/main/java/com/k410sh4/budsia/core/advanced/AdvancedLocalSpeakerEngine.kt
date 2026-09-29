package com.k410sh4.budsia.core.advanced

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import com.k410sh4.budsia.domain.model.AdvancedSpeakerState
import com.k410sh4.budsia.domain.model.AdvancedUtterance
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlin.math.sqrt
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdvancedLocalSpeakerEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val models: AdvancedModelManager
) {
    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val FRAME_SIZE = 512
        private const val SPEAKER_THRESHOLD = 0.64f
        private const val MAX_SPEAKERS = 8
    }

    private val _state = MutableStateFlow(
        AdvancedSpeakerState(
            available = models.isReady(),
            modelState = models.state.value
        )
    )
    val state: StateFlow<AdvancedSpeakerState> = _state.asStateFlow()

    private val _utterances = MutableSharedFlow<AdvancedUtterance>(
        extraBufferCapacity = 16
    )
    val utterances: SharedFlow<AdvancedUtterance> = _utterances.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var captureJob: Job? = null
    private var processingJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var frameChannel: Channel<FloatArray>? = null

    private var vad: Vad? = null
    private var recognizer: OfflineRecognizer? = null
    private var speakerExtractor: SpeakerEmbeddingExtractor? = null

    private data class SpeakerProfile(
        val label: String,
        var centroid: FloatArray,
        var count: Int
    )

    private val profiles = mutableListOf<SpeakerProfile>()

    init {
        scope.launch {
            models.state.collect { modelState ->
                _state.update {
                    it.copy(
                        available = modelState.status.name == "READY",
                        modelState = modelState
                    )
                }
            }
        }
    }

    suspend fun start(): Result<Unit> = withContext(Dispatchers.IO) {
        if (_state.value.running) return@withContext Result.success(Unit)

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext Result.failure(
                SecurityException("Permissão de microfone necessária")
            )
        }

        if (!models.isReady()) {
            return@withContext Result.failure(
                IllegalStateException("Instale os modelos avançados antes de iniciar.")
            )
        }

        runCatching {
            _state.update {
                it.copy(
                    available = true,
                    running = false,
                    stage = "Carregando VAD, Whisper e modelo de voz…",
                    error = null
                )
            }

            initializeNativeModels()
            profiles.clear()

            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            check(minBuffer > 0) { "O Android não forneceu um buffer de microfone válido." }

            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer * 2, SAMPLE_RATE * 2)
            )

            check(record.state == AudioRecord.STATE_INITIALIZED) {
                "Não foi possível inicializar AudioRecord a 16 kHz mono."
            }

            audioRecord = record
            val channel = Channel<FloatArray>(capacity = 256)
            frameChannel = channel

            record.startRecording()
            check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "O microfone não entrou em estado de gravação."
            }

            _state.update {
                it.copy(
                    running = true,
                    stage = "Escutando e separando falantes",
                    error = null,
                    speakerCount = 0
                )
            }

            captureJob = scope.launch { captureLoop(record, channel) }
            processingJob = scope.launch(Dispatchers.Default) { processingLoop(channel) }
        }.onFailure {
            stopInternal()
            _state.update { current ->
                current.copy(
                    running = false,
                    stage = "Erro",
                    error = it.message ?: "Falha no motor avançado"
                )
            }
        }
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        stopInternal()
        _state.update {
            it.copy(
                running = false,
                stage = "Parado",
                speakerCount = profiles.size
            )
        }
    }

    private suspend fun captureLoop(
        record: AudioRecord,
        channel: Channel<FloatArray>
    ) {
        val pcm = ShortArray(FRAME_SIZE)
        try {
            while (currentCoroutineContext().isActive &&
                record.recordingState == AudioRecord.RECORDSTATE_RECORDING
            ) {
                val count = record.read(pcm, 0, pcm.size)
                if (count > 0) {
                    val frame = FloatArray(count)
                    for (i in 0 until count) {
                        frame[i] = pcm[i] / 32768.0f
                    }
                    channel.send(frame)
                } else if (count < 0) {
                    throw IllegalStateException("Falha de leitura do microfone: $count")
                }
            }
        } finally {
            channel.close()
        }
    }

    private suspend fun processingLoop(channel: Channel<FloatArray>) {
        val localVad = vad ?: return
        try {
            for (frame in channel) {
                localVad.acceptWaveform(frame)

                while (!localVad.empty()) {
                    val segment = localVad.front()
                    localVad.pop()

                    val samples = segment.samples
                    if (samples.size < SAMPLE_RATE / 3) continue

                    _state.update {
                        it.copy(stage = "Transcrevendo e reconhecendo voz…")
                    }

                    val utterance = processUtterance(samples)
                    if (utterance != null) {
                        _utterances.emit(utterance)
                    }

                    _state.update {
                        it.copy(
                            stage = "Escutando e separando falantes",
                            speakerCount = profiles.size
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            if (currentCoroutineContext().isActive) {
                _state.update {
                    it.copy(
                        error = t.message ?: "Erro ao processar áudio local",
                        stage = "Erro de processamento"
                    )
                }
            }
        }
    }

    private fun processUtterance(samples: FloatArray): AdvancedUtterance? {
        val localRecognizer = recognizer ?: return null

        val stream = localRecognizer.createStream()
        val result = try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            localRecognizer.decode(stream)
            localRecognizer.getResult(stream)
        } finally {
            stream.release()
        }

        val text = result.text.trim()
        if (text.isBlank()) return null

        val embedding = computeSpeakerEmbedding(samples)
        val speaker = identifySpeaker(embedding)
        val durationMs = samples.size * 1_000L / SAMPLE_RATE

        return AdvancedUtterance(
            speakerLabel = speaker.first,
            text = text,
            languageTag = normalizeLanguage(result.lang),
            speakerSimilarity = speaker.second,
            durationMs = durationMs
        )
    }

    private fun computeSpeakerEmbedding(samples: FloatArray): FloatArray? {
        val extractor = speakerExtractor ?: return null
        return runCatching {
            val stream = extractor.createStream()
            try {
                stream.acceptWaveform(samples, SAMPLE_RATE)
                stream.inputFinished()
                if (extractor.isReady(stream)) extractor.compute(stream) else null
            } finally {
                stream.release()
            }
        }.getOrNull()
    }

    private fun identifySpeaker(embedding: FloatArray?): Pair<String, Float?> {
        if (embedding == null || embedding.isEmpty()) {
            return "Falante ?" to null
        }

        normalizeInPlace(embedding)

        var bestIndex = -1
        var bestScore = -1f

        profiles.forEachIndexed { index, profile ->
            val score = cosine(embedding, profile.centroid)
            if (score > bestScore) {
                bestScore = score
                bestIndex = index
            }
        }

        if (bestIndex >= 0 && bestScore >= SPEAKER_THRESHOLD) {
            val profile = profiles[bestIndex]
            updateCentroid(profile, embedding)
            return profile.label to bestScore
        }

        if (profiles.size >= MAX_SPEAKERS && bestIndex >= 0) {
            return profiles[bestIndex].label to bestScore
        }

        val label = "Falante " + speakerLetter(profiles.size)
        profiles += SpeakerProfile(
            label = label,
            centroid = embedding.copyOf(),
            count = 1
        )

        _state.update { it.copy(speakerCount = profiles.size) }
        return label to null
    }

    private fun updateCentroid(profile: SpeakerProfile, embedding: FloatArray) {
        val newCount = profile.count + 1
        val oldWeight = profile.count.toFloat() / newCount
        val newWeight = 1f / newCount

        for (i in profile.centroid.indices) {
            profile.centroid[i] =
                profile.centroid[i] * oldWeight + embedding[i] * newWeight
        }
        normalizeInPlace(profile.centroid)
        profile.count = newCount
    }

    private fun normalizeInPlace(values: FloatArray) {
        var sum = 0.0
        for (v in values) sum += v * v
        val norm = sqrt(sum).toFloat()
        if (norm <= 1e-8f) return
        for (i in values.indices) values[i] /= norm
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        val n = minOf(a.size, b.size)
        var dot = 0f
        for (i in 0 until n) dot += a[i] * b[i]
        return dot
    }

    private fun speakerLetter(index: Int): String =
        if (index in 0..25) ('A'.code + index).toChar().toString()
        else (index + 1).toString()

    private fun normalizeLanguage(raw: String?): String? {
        val value = raw?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        return value
            .removePrefix("<|")
            .removeSuffix("|>")
            .substringBefore('-')
            .takeIf { it.length in 2..8 }
    }

    private fun initializeNativeModels() {
        releaseNativeModels()

        vad = Vad(
            config = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = models.vadFile().absolutePath,
                    threshold = 0.50f,
                    minSilenceDuration = 0.45f,
                    minSpeechDuration = 0.35f,
                    windowSize = FRAME_SIZE,
                    maxSpeechDuration = 15f
                ),
                sampleRate = SAMPLE_RATE,
                numThreads = 2,
                provider = "cpu",
                debug = false
            )
        )

        recognizer = OfflineRecognizer(
            config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(
                    sampleRate = SAMPLE_RATE,
                    featureDim = 80
                ),
                modelConfig = OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = models.whisperEncoder().absolutePath,
                        decoder = models.whisperDecoder().absolutePath,
                        language = "",
                        task = "transcribe",
                        tailPaddings = 1000
                    ),
                    tokens = models.whisperTokens().absolutePath,
                    numThreads = 2,
                    debug = false,
                    provider = "cpu",
                    modelType = "whisper"
                ),
                decodingMethod = "greedy_search"
            )
        )

        speakerExtractor = SpeakerEmbeddingExtractor(
            config = SpeakerEmbeddingExtractorConfig(
                model = models.speakerFile().absolutePath,
                numThreads = 2,
                debug = false,
                provider = "cpu"
            )
        )
    }

    private fun stopInternal() {
        captureJob?.cancel()
        processingJob?.cancel()
        captureJob = null
        processingJob = null

        runCatching { frameChannel?.close() }
        frameChannel = null

        runCatching {
            audioRecord?.let { record ->
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
                record.release()
            }
        }
        audioRecord = null

        releaseNativeModels()
    }

    private fun releaseNativeModels() {
        runCatching { vad?.release() }
        vad = null
        runCatching { recognizer?.release() }
        recognizer = null
        runCatching { speakerExtractor?.release() }
        speakerExtractor = null
    }
}
