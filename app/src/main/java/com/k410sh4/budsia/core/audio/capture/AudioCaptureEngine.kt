package com.k410sh4.budsia.core.audio.capture

import com.k410sh4.budsia.core.audio.model.AudioCaptureConfig
import com.k410sh4.budsia.core.audio.model.AudioFrame
import kotlinx.coroutines.flow.Flow

interface AudioCaptureEngine {
    fun stream(config: AudioCaptureConfig): Flow<AudioFrame>
}
