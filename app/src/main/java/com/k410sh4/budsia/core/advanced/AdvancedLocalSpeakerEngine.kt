package com.k410sh4.budsia.core.advanced

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.FastClusteringConfig
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import com.k410sh4.budsia.core.quality.TranscriptQualityEvaluator
import com.k410sh4.budsia.core.quality.TranscriptQualityLevel
import com.k410sh4.budsia.core.speaker.SpeakerIdentityRegistry
import com.k410sh4.budsia.domain.model.AdvancedSpeakerState
import com.k410sh4.budsia.domain.model.AdvancedUtterance
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
        private const val CAPTURE_FRAME = 2_048
        private const val WINDOW_SECONDS = 8
        private const val WINDOW_SAMPLES = SAMPLE_RATE * WINDOW_SECONDS
        private const val MIN_SEGMENT_MS = 450L
    }

    private data class AudioWindow(
        val samples: FloatArray,
        val startedAtMs: Long
    )

    private val _state = MutableStateFlow(
        AdvancedSpeakerState(
            available = models.isReady(),
            modelState = models.state.value
        )
    )
    val state: StateFlow<AdvancedSpeakerState> = _state.asStateFlow()

    private val _utterances = MutableSharedFlow<AdvancedUtterance>(
        extraBufferCapacity = 24
    )
    val utterances: SharedFlow<AdvancedUtterance> = _utterances.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private var processingJob: Job? = null
    private var windowChannel: Channel<AudioWindow>? = null

    private var diarizer: OfflineSpeakerDiarization? = null
    private var recognizer: OfflineRecognizer? = null
    private var speakerExtractor: SpeakerEmbeddingExtractor? = null

    private var expectedSpeakers: Int = 0
    private var registry = SpeakerIdentityRegistry()
    private val qualityEvaluator = TranscriptQualityEvaluator()

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

    suspend fun start(expectedSpeakers: Int = 0): Result<Unit> = withContext(Dispatchers.IO) {
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
                IllegalStateException("Instale o pacote de conversação V2 antes de iniciar.")
            )
        }

        runCatching {
            this@AdvancedLocalSpeakerEngine.expectedSpeakers =
                expectedSpeakers.takeIf { it in 1..4 } ?: 0

            registry = SpeakerIdentityRegistry(
                maxSpeakers = if (this@AdvancedLocalSpeakerEngine.expectedSpeakers > 0) {
                    this@AdvancedLocalSpeakerEngine.expectedSpeakers
                } else {
                    4
                }
            )

            _state.value = _state.value.copy(
                available = true,
                running = false,
                expectedSpeakers = this@AdvancedLocalSpeakerEngine.expectedSpeakers,
                speakerCount = 0,
                processedWindows = 0,
                droppedWindows = 0,
                lastProcessingMs = null,
                stage = "Carregando IA local V2…",
                error = null
            )

            initializeNativeModels()

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
                maxOf(minBuffer * 3, SAMPLE_RATE * 2)
            )

            check(record.state == AudioRecord.STATE_INITIALIZED) {
                "Não foi possível iniciar o microfone em 16 kHz mono."
            }

            val channel = Channel<AudioWindow>(capacity = 2)
            windowChannel = channel
            audioRecord = record

            record.startRecording()
            check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "O microfone não entrou em gravação."
            }

            _state.update {
                it.copy(
                    running = true,
                    stage = "Escutando • janela de 8 s",
                    error = null
                )
            }

            captureJob = scope.launch { captureLoop(record, channel) }
            processingJob = scope.launch(Dispatchers.Default) { processingLoop(channel) }
        }.onFailure {
            stopInternal()
            _state.update { state ->
                state.copy(
                    running = false,
                    stage = "Erro",
                    error = it.message ?: "Falha ao iniciar motor V2"
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
                speakerCount = registry.speakerCount
            )
        }
    }

    private suspend fun captureLoop(
        record: AudioRecord,
        channel: Channel<AudioWindow>
    ) {
        val pcm = ShortArray(CAPTURE_FRAME)
        var window = FloatArray(WINDOW_SAMPLES)
        var windowPos = 0
        var windowStartedAt = System.currentTimeMillis()

        try {
            while (currentCoroutineContext().isActive &&
                record.recordingState == AudioRecord.RECORDSTATE_RECORDING
            ) {
                val count = record.read(pcm, 0, pcm.size)
                if (count < 0) {
                    throw IllegalStateException("Falha de leitura do microfone: $count")
                }
                if (count == 0) continue

                var sourcePos = 0
                while (sourcePos < count) {
                    val copyCount = minOf(count - sourcePos, WINDOW_SAMPLES - windowPos)
                    for (i in 0 until copyCount) {
                        window[windowPos + i] = pcm[sourcePos + i] / 32768.0f
                    }
                    sourcePos += copyCount
                    windowPos += copyCount

                    if (windowPos == WINDOW_SAMPLES) {
                        val result = channel.trySend(
                            AudioWindow(
                                samples = window,
                                startedAtMs = windowStartedAt
                            )
                        )

                        if (result.isFailure) {
                            _state.update { it.copy(droppedWindows = it.droppedWindows + 1) }
                        }

                        window = FloatArray(WINDOW_SAMPLES)
                        windowPos = 0
                        windowStartedAt = System.currentTimeMillis()
                    }
                }
            }
        } finally {
            channel.close()
        }
    }

    private suspend fun processingLoop(channel: Channel<AudioWindow>) {
        try {
            for (window in channel) {
                val started = System.currentTimeMillis()
                _state.update { it.copy(stage = "Separando falantes…") }

                processWindow(window)

                val elapsed = System.currentTimeMillis() - started
                _state.update {
                    it.copy(
                        processedWindows = it.processedWindows + 1,
                        lastProcessingMs = elapsed,
                        speakerCount = registry.speakerCount,
                        stage = "Escutando • janela de 8 s"
                    )
                }
            }
        } catch (t: Throwable) {
            if (currentCoroutineContext().isActive) {
                _state.update {
                    it.copy(
                        stage = "Erro de processamento",
                        error = t.message ?: "Erro no pipeline de conversação V2"
                    )
                }
            }
        }
    }

    private suspend fun processWindow(window: AudioWindow) {
        val localDiarizer = diarizer ?: return
        val segments = localDiarizer.process(window.samples)
            .sortedBy { it.start }

        if (segments.isEmpty()) return

        registry.beginWindow()

        for (segment in segments) {
            val start = (segment.start * SAMPLE_RATE).toInt().coerceIn(0, window.samples.size)
            val end = (segment.end * SAMPLE_RATE).toInt().coerceIn(start, window.samples.size)
            if (end <= start) continue

            val durationMs = (end - start) * 1_000L / SAMPLE_RATE
            if (durationMs < MIN_SEGMENT_MS) continue

            val audio = window.samples.copyOfRange(start, end)
            val rms = calculateRms(audio)
            if (rms < 0.0025f) continue

            _state.update { it.copy(stage = "Transcrevendo trecho…") }

            val transcript = transcribe(audio) ?: continue
            val quality = qualityEvaluator.evaluate(
                text = transcript.first,
                durationMs = durationMs,
                rms = rms
            )

            if (quality.level == TranscriptQualityLevel.REJECTED) continue

            val embedding = if (quality.level == TranscriptQualityLevel.GOOD) {
                computeSpeakerEmbedding(audio)
            } else {
                null
            }

            val diarizationConfidence = normalizeDiarizationConfidence(segment.confidence)
            val assignment = registry.resolve(
                localSpeakerId = segment.speaker,
                embedding = embedding,
                durationMs = durationMs,
                diarizationConfidence = diarizationConfidence
            )

            _utterances.emit(
                AdvancedUtterance(
                    timestamp = window.startedAtMs + (segment.start * 1_000L).toLong(),
                    speakerLabel = assignment.label,
                    speakerConfidence = assignment.confidence,
                    speakerStable = assignment.stable,
                    text = transcript.first,
                    whisperLanguageTag = transcript.second,
                    diarizationConfidence = diarizationConfidence,
                    transcriptQualityScore = quality.score,
                    transcriptQualityReason = quality.reason,
                    durationMs = durationMs,
                    rms = rms
                )
            )
        }
    }

    private fun transcribe(samples: FloatArray): Pair<String, String?>? {
        val localRecognizer = recognizer ?: return null
        val stream = localRecognizer.createStream()

        return try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            localRecognizer.decode(stream)
            val result = localRecognizer.getResult(stream)
            val text = result.text.trim()
            if (text.isBlank()) null
            else text to normalizeWhisperLanguage(result.lang)
        } finally {
            stream.release()
        }
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

    private fun initializeNativeModels() {
        releaseNativeModels()

        val diarizationConfig = OfflineSpeakerDiarizationConfig(
            segmentation = OfflineSpeakerSegmentationModelConfig(
                pyannote = OfflineSpeakerSegmentationPyannoteModelConfig(
                    model = models.segmentationFile().absolutePath,
                    windowShiftRatio = 0.1f
                ),
                numThreads = 2,
                debug = false,
                provider = "cpu"
            ),
            embedding = SpeakerEmbeddingExtractorConfig(
                model = models.speakerFile().absolutePath,
                numThreads = 2,
                debug = false,
                provider = "cpu"
            ),
            clustering = FastClusteringConfig(
                numClusters = expectedSpeakers.takeIf { it > 0 } ?: -1,
                threshold = 0.58f,
                computeConfidence = true
            ),
            minDurationOn = 0.30f,
            minDurationOff = 0.45f
        )

        diarizer = OfflineSpeakerDiarization(config = diarizationConfig)

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
                        tailPaddings = 1_000
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

    private fun normalizeWhisperLanguage(raw: String?): String? {
        val value = raw
            ?.trim()
            ?.lowercase()
            ?.removePrefix("<|")
            ?.removeSuffix("|>")
            ?.substringBefore('-')
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return value.takeIf { it.length in 2..8 }
    }

    private fun normalizeDiarizationConfidence(raw: Float): Float =
        when {
            raw <= -1.5f -> 0.50f
            raw in -1f..1f -> ((raw + 1f) / 2f).coerceIn(0f, 1f)
            else -> raw.coerceIn(0f, 1f)
        }

    private fun calculateRms(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0.0
        for (sample in samples) sum += sample * sample
        return sqrt(sum / samples.size).toFloat()
    }

    private fun stopInternal() {
        captureJob?.cancel()
        processingJob?.cancel()
        captureJob = null
        processingJob = null

        runCatching { windowChannel?.close() }
        windowChannel = null

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
        runCatching { diarizer?.release() }
        diarizer = null
        runCatching { recognizer?.release() }
        recognizer = null
        runCatching { speakerExtractor?.release() }
        speakerExtractor = null
    }
}
