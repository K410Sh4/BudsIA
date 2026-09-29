package com.k410sh4.budsia.core.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
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

    private var recognizer: SpeechRecognizer? = null

    fun checkAvailability(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun start(languageTag: String? = null) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _state.value = _state.value.copy(
                available = checkAvailability(),
                listening = false,
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

        mainHandler.post {
            runCatching {
                if (recognizer == null) {
                    recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context).also {
                        it.setRecognitionListener(listener)
                    }
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE,
                        languageTag ?: Locale.getDefault().toLanguageTag()
                    )
                }

                _state.value = SpeechState(
                    available = true,
                    listening = true
                )
                recognizer?.startListening(intent)
            }.onFailure {
                _state.value = SpeechState(
                    available = checkAvailability(),
                    listening = false,
                    error = it.message ?: "Unable to start on-device speech recognition"
                )
            }
        }
    }

    fun stop() {
        mainHandler.post {
            runCatching { recognizer?.stopListening() }
            _state.value = _state.value.copy(listening = false)
        }
    }

    fun cancel() {
        mainHandler.post {
            runCatching { recognizer?.cancel() }
            _state.value = _state.value.copy(listening = false)
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = _state.value.copy(listening = true, error = null)
        }

        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            _state.value = _state.value.copy(listening = false)
        }

        override fun onError(error: Int) {
            _state.value = _state.value.copy(
                listening = false,
                error = errorLabel(error)
            )
        }

        override fun onResults(results: Bundle?) {
            val texts = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                .orEmpty()
            val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

            _state.value = _state.value.copy(
                listening = false,
                partialText = "",
                finalText = texts.firstOrNull().orEmpty(),
                confidence = confidences?.firstOrNull()?.takeIf { it >= 0f },
                error = null
            )
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            _state.value = _state.value.copy(partialText = text)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun errorLabel(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio capture error"
        SpeechRecognizer.ERROR_CLIENT -> "Speech client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
        SpeechRecognizer.ERROR_NETWORK -> "Network error (offline recognizer should not require network)"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
        SpeechRecognizer.ERROR_SERVER -> "Recognizer service error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
        else -> "Speech recognition error $code"
    }
}
