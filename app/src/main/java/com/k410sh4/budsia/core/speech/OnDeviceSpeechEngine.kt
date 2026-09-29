package com.k410sh4.budsia.core.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.ModelDownloadListener
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.k410sh4.budsia.domain.model.SpeechLanguageModelState
import com.k410sh4.budsia.domain.model.SpeechLanguageStatus
import com.k410sh4.budsia.domain.model.SpeechState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnDeviceSpeechEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(SpeechState(available = checkAvailability()))
    val state: StateFlow<SpeechState> = _state.asStateFlow()

    private val _languageModels = MutableStateFlow<Map<String, SpeechLanguageModelState>>(emptyMap())
    val languageModels: StateFlow<Map<String, SpeechLanguageModelState>> = _languageModels.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private var continuous = false
    private var busy = false
    private var activeLanguageTag: String = Locale.getDefault().toLanguageTag()
    private var retryCount = 0
    private var pendingRestart: Runnable? = null

    fun checkAvailability(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun startContinuous(languageTag: String? = null) {
        if (!hasMicrophonePermission()) {
            _state.value = _state.value.copy(
                available = checkAvailability(),
                listening = false,
                processing = false,
                errorCode = SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
                error = "Microphone permission required"
            )
            return
        }

        if (!checkAvailability()) {
            _state.value = SpeechState(
                available = false,
                error = "On-device SpeechRecognizer is unavailable on this device"
            )
            return
        }

        activeLanguageTag = languageTag ?: Locale.getDefault().toLanguageTag()
        continuous = true
        retryCount = 0
        cancelPendingRestart()

        _state.value = _state.value.copy(
            available = true,
            requestedLanguage = activeLanguageTag,
            errorCode = null,
            error = null,
            statusMessage = "Preparing offline speech…",
            modelDownloadRequired = false,
            modelDownloadProgress = null
        )

        startNext(0L)
    }

    fun stopContinuous() {
        continuous = false
        retryCount = 0
        cancelPendingRestart()

        mainHandler.post {
            runCatching { recognizer?.cancel() }
            busy = false
            destroyRecognizer()
            _state.value = _state.value.copy(
                listening = false,
                processing = false,
                partialText = "",
                statusMessage = "Stopped",
                errorCode = null,
                error = null
            )
        }
    }

    fun start(languageTag: String? = null) = startContinuous(languageTag)
    fun stop() = stopContinuous()
    fun cancel() = stopContinuous()

    fun refreshLanguageSupport(languageTags: List<String>) {
        if (!checkAvailability()) {
            _languageModels.value = languageTags.associateWith {
                SpeechLanguageModelState(
                    languageTag = it,
                    status = SpeechLanguageStatus.UNSUPPORTED,
                    detail = "On-device recognizer unavailable"
                )
            }
            return
        }

        if (Build.VERSION.SDK_INT < 33) {
            _languageModels.value = languageTags.associateWith {
                SpeechLanguageModelState(
                    languageTag = it,
                    status = SpeechLanguageStatus.UNKNOWN,
                    detail = "Android version cannot query offline model status"
                )
            }
            return
        }

        mainHandler.post {
            val r = ensureRecognizer() ?: return@post
            languageTags.forEachIndexed { index, tag ->
                mainHandler.postDelayed({
                    runCatching {
                        r.checkRecognitionSupport(
                            buildIntent(tag),
                            context.mainExecutor,
                            object : RecognitionSupportCallback {
                                override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                                    val status = when {
                                        matchesLanguage(recognitionSupport.installedOnDeviceLanguages, tag) ->
                                            SpeechLanguageStatus.INSTALLED
                                        matchesLanguage(recognitionSupport.pendingOnDeviceLanguages, tag) ->
                                            SpeechLanguageStatus.PENDING
                                        matchesLanguage(recognitionSupport.supportedOnDeviceLanguages, tag) ->
                                            SpeechLanguageStatus.DOWNLOAD_REQUIRED
                                        else -> SpeechLanguageStatus.UNSUPPORTED
                                    }
                                    updateLanguageModel(
                                        tag,
                                        status,
                                        when (status) {
                                            SpeechLanguageStatus.INSTALLED -> "Offline speech model installed"
                                            SpeechLanguageStatus.PENDING -> "Offline speech model download pending"
                                            SpeechLanguageStatus.DOWNLOAD_REQUIRED -> "Supported; download required"
                                            SpeechLanguageStatus.UNSUPPORTED -> "Not supported by this on-device recognizer"
                                            SpeechLanguageStatus.UNKNOWN -> "Unknown"
                                        }
                                    )
                                }

                                override fun onError(error: Int) {
                                    updateLanguageModel(
                                        tag,
                                        SpeechLanguageStatus.UNKNOWN,
                                        "Support check failed: " + errorLabel(error)
                                    )
                                }
                            }
                        )
                    }.onFailure {
                        updateLanguageModel(
                            tag,
                            SpeechLanguageStatus.UNKNOWN,
                            it.message ?: "Unable to query speech model"
                        )
                    }
                }, index * 120L)
            }
        }
    }

    fun downloadLanguageModel(languageTag: String) {
        if (!checkAvailability()) {
            updateLanguageModel(
                languageTag,
                SpeechLanguageStatus.UNSUPPORTED,
                "On-device recognizer unavailable"
            )
            return
        }

        if (Build.VERSION.SDK_INT < 33) {
            updateLanguageModel(
                languageTag,
                SpeechLanguageStatus.UNKNOWN,
                "Speech model download API requires Android 13+"
            )
            return
        }

        mainHandler.post {
            val r = ensureRecognizer() ?: return@post
            val intent = buildIntent(languageTag)

            updateLanguageModel(
                languageTag,
                SpeechLanguageStatus.PENDING,
                "Requesting offline speech model…"
            )
            _state.value = _state.value.copy(
                requestedLanguage = languageTag,
                modelDownloadRequired = true,
                modelDownloadProgress = null,
                statusMessage = "Downloading offline speech model…",
                errorCode = null,
                error = null
            )

            if (Build.VERSION.SDK_INT >= 34) {
                runCatching {
                    r.triggerModelDownload(
                        intent,
                        context.mainExecutor,
                        object : ModelDownloadListener {
                            override fun onProgress(completedPercent: Int) {
                                updateLanguageModel(
                                    languageTag,
                                    SpeechLanguageStatus.PENDING,
                                    "Downloading: $completedPercent%"
                                )
                                _state.value = _state.value.copy(
                                    modelDownloadProgress = completedPercent,
                                    statusMessage = "Speech model download: $completedPercent%"
                                )
                            }

                            override fun onSuccess() {
                                updateLanguageModel(
                                    languageTag,
                                    SpeechLanguageStatus.INSTALLED,
                                    "Offline speech model installed"
                                )
                                _state.value = _state.value.copy(
                                    modelDownloadRequired = false,
                                    modelDownloadProgress = 100,
                                    statusMessage = "Offline speech model installed",
                                    errorCode = null,
                                    error = null
                                )
                            }

                            override fun onScheduled() {
                                updateLanguageModel(
                                    languageTag,
                                    SpeechLanguageStatus.PENDING,
                                    "Download scheduled by Android"
                                )
                                _state.value = _state.value.copy(
                                    statusMessage = "Speech model download scheduled"
                                )
                            }

                            override fun onError(error: Int) {
                                updateLanguageModel(
                                    languageTag,
                                    SpeechLanguageStatus.UNKNOWN,
                                    "Download failed: " + errorLabel(error)
                                )
                                _state.value = _state.value.copy(
                                    modelDownloadProgress = null,
                                    errorCode = error,
                                    error = "Speech model download failed: " + errorLabel(error)
                                )
                            }
                        }
                    )
                }.onFailure {
                    _state.value = _state.value.copy(
                        error = it.message ?: "Unable to request speech model download"
                    )
                }
            } else {
                runCatching { r.triggerModelDownload(intent) }
                    .onFailure {
                        updateLanguageModel(
                            languageTag,
                            SpeechLanguageStatus.UNKNOWN,
                            it.message ?: "Speech model download request failed"
                        )
                    }
            }
        }
    }

    private fun startNext(delayMs: Long) {
        if (!continuous) return
        cancelPendingRestart()

        val task = Runnable {
            if (!continuous) return@Runnable
            if (busy) {
                startNext(250L)
                return@Runnable
            }

            if (!hasMicrophonePermission()) {
                continuous = false
                _state.value = _state.value.copy(
                    listening = false,
                    processing = false,
                    errorCode = SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
                    error = "Microphone permission required"
                )
                return@Runnable
            }

            val r = ensureRecognizer() ?: return@Runnable
            runCatching {
                busy = true
                _state.value = _state.value.copy(
                    available = true,
                    listening = true,
                    processing = false,
                    partialText = "",
                    requestedLanguage = activeLanguageTag,
                    errorCode = null,
                    error = null,
                    statusMessage = "Listening…",
                    modelDownloadRequired = false
                )
                r.startListening(buildIntent(activeLanguageTag))
            }.onFailure {
                busy = false
                recoverRecognizer(
                    "Speech client reset; retrying…",
                    1_200L,
                    recreate = true
                )
            }
        }

        pendingRestart = task
        mainHandler.postDelayed(task, delayMs)
    }

    private fun ensureRecognizer(): SpeechRecognizer? {
        if (!checkAvailability()) return null
        if (recognizer == null) {
            recognizer = runCatching {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context).also {
                    it.setRecognitionListener(listener)
                }
            }.getOrElse {
                _state.value = _state.value.copy(
                    available = false,
                    error = it.message ?: "Unable to create on-device recognizer"
                )
                null
            }
        }
        return recognizer
    }

    private fun destroyRecognizer() {
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun recoverRecognizer(message: String, baseDelayMs: Long, recreate: Boolean) {
        retryCount = (retryCount + 1).coerceAtMost(6)
        busy = false
        if (recreate) destroyRecognizer()

        val retryDelay = (baseDelayMs * retryCount).coerceAtMost(5_000L)
        _state.value = _state.value.copy(
            listening = false,
            processing = false,
            errorCode = null,
            error = null,
            statusMessage = message + " (" + retryDelay + " ms)"
        )
        if (continuous) startNext(retryDelay)
    }

    private fun cancelPendingRestart() {
        pendingRestart?.let(mainHandler::removeCallbacks)
        pendingRestart = null
    }

    private fun buildIntent(languageTag: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            if (Build.VERSION.SDK_INT >= 33) {
                putExtra(
                    RecognizerIntent.EXTRA_ENABLE_FORMATTING,
                    RecognizerIntent.FORMATTING_OPTIMIZE_LATENCY
                )
            }
        }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            retryCount = 0
            _state.value = _state.value.copy(
                listening = true,
                processing = false,
                errorCode = null,
                error = null,
                statusMessage = "Listening…"
            )
        }

        override fun onBeginningOfSpeech() {
            _state.value = _state.value.copy(statusMessage = "Speech detected")
        }

        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            _state.value = _state.value.copy(
                listening = false,
                processing = true,
                statusMessage = "Processing locally…"
            )
        }

        override fun onError(error: Int) {
            busy = false

            when (error) {
                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                    continuous = false
                    updateLanguageModel(
                        activeLanguageTag,
                        SpeechLanguageStatus.DOWNLOAD_REQUIRED,
                        "Language is supported but its offline speech model is not installed"
                    )
                    _state.value = _state.value.copy(
                        listening = false,
                        processing = false,
                        errorCode = error,
                        error = "Offline speech model missing for $activeLanguageTag",
                        statusMessage = "Install the speech model and start the session again.",
                        modelDownloadRequired = true
                    )
                }

                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> {
                    continuous = false
                    updateLanguageModel(
                        activeLanguageTag,
                        SpeechLanguageStatus.UNSUPPORTED,
                        "Language not supported by this on-device recognizer"
                    )
                    _state.value = _state.value.copy(
                        listening = false,
                        processing = false,
                        errorCode = error,
                        error = "Speech language not supported: $activeLanguageTag",
                        statusMessage = null,
                        modelDownloadRequired = false
                    )
                }

                SpeechRecognizer.ERROR_CLIENT,
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_TOO_MANY_REQUESTS,
                SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> {
                    recoverRecognizer(
                        "Speech service recovered from " + errorLabel(error),
                        if (error == SpeechRecognizer.ERROR_TOO_MANY_REQUESTS) 1_500L else 800L,
                        recreate = true
                    )
                }

                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    retryCount = 0
                    _state.value = _state.value.copy(
                        listening = false,
                        processing = false,
                        errorCode = null,
                        error = null,
                        statusMessage = "No clear speech detected; listening again…"
                    )
                    if (continuous) startNext(500L)
                }

                else -> {
                    _state.value = _state.value.copy(
                        listening = false,
                        processing = false,
                        errorCode = error,
                        error = errorLabel(error),
                        statusMessage = null
                    )
                    if (continuous) recoverRecognizer(
                        "Retrying speech recognizer",
                        1_000L,
                        recreate = error == SpeechRecognizer.ERROR_AUDIO ||
                            error == SpeechRecognizer.ERROR_SERVER
                    )
                }
            }
        }

        override fun onResults(results: Bundle?) {
            busy = false
            retryCount = 0

            val texts = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                .orEmpty()
            val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
            val text = texts.firstOrNull().orEmpty()

            _state.value = _state.value.copy(
                listening = false,
                processing = false,
                partialText = "",
                finalText = text,
                confidence = confidences?.firstOrNull()?.takeIf { it >= 0f },
                resultId = _state.value.resultId + 1,
                errorCode = null,
                error = null,
                statusMessage = if (text.isBlank()) "No final text" else "Result ready"
            )

            if (continuous) startNext(700L)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            _state.value = _state.value.copy(
                partialText = text,
                listening = true,
                processing = false
            )
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun updateLanguageModel(
        tag: String,
        status: SpeechLanguageStatus,
        detail: String
    ) {
        _languageModels.value = _languageModels.value.toMutableMap().apply {
            this[tag] = SpeechLanguageModelState(tag, status, detail)
        }
    }

    private fun matchesLanguage(list: List<String>, requested: String): Boolean {
        val requestedLocale = Locale.forLanguageTag(requested)
        return list.any { candidate ->
            val locale = Locale.forLanguageTag(candidate)
            candidate.equals(requested, ignoreCase = true) ||
                (locale.language.isNotBlank() && locale.language == requestedLocale.language)
        }
    }

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    private fun errorLabel(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio capture error"
        SpeechRecognizer.ERROR_CLIENT -> "client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "recognizer busy"
        SpeechRecognizer.ERROR_SERVER -> "Recognizer service error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "too many requests"
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "speech service disconnected"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "language not supported"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "offline language model not downloaded"
        SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT -> "support check unavailable"
        SpeechRecognizer.ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS -> "download progress unavailable"
        else -> "Speech recognition error $code"
    }
}
