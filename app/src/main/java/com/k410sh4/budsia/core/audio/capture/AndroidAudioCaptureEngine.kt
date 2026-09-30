package com.k410sh4.budsia.core.audio.capture

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import com.k410sh4.budsia.core.audio.model.AudioCaptureConfig
import com.k410sh4.budsia.core.audio.model.AudioFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.math.max

class AndroidAudioCaptureEngine : AudioCaptureEngine {
    override fun stream(config: AudioCaptureConfig): Flow<AudioFrame> = flow {
        require(config.channelCount == 1) { "Only mono capture is supported in foundation v1." }

        val channelMask = AudioFormat.CHANNEL_IN_MONO
        val minBufferBytes = AudioRecord.getMinBufferSize(
            config.sampleRateHz,
            channelMask,
            AudioFormat.ENCODING_PCM_16BIT
        )
        require(minBufferBytes > 0) { "Unsupported AudioRecord configuration: $minBufferBytes" }

        val frameBytes = config.frameSamples * Short.SIZE_BYTES
        val bufferBytes = max(minBufferBytes, frameBytes * 4)

        val recorder = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(config.sampleRateHz)
                    .setChannelMask(channelMask)
                    .build()
            )
            .setBufferSizeInBytes(bufferBytes)
            .build()

        check(recorder.state == AudioRecord.STATE_INITIALIZED) {
            "AudioRecord failed to initialize."
        }

        val shortBuffer = ShortArray(config.frameSamples)
        var sequence = 0L

        try {
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                "AudioRecord did not enter RECORDSTATE_RECORDING."
            }

            while (currentCoroutineContext().isActive) {
                val read = recorder.read(
                    shortBuffer,
                    0,
                    shortBuffer.size,
                    AudioRecord.READ_BLOCKING
                )

                if (read < 0) error("AudioRecord.read failed with code $read")
                if (read == 0) continue

                val samples = FloatArray(read)
                for (i in 0 until read) {
                    samples[i] = shortBuffer[i] / 32768f
                }

                emit(
                    AudioFrame(
                        samples = samples,
                        sampleRateHz = config.sampleRateHz,
                        capturedAtElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
                        sequence = sequence++
                    )
                )
            }
        } finally {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                runCatching { recorder.stop() }
            }
            recorder.release()
        }
    }.flowOn(Dispatchers.IO)
}
