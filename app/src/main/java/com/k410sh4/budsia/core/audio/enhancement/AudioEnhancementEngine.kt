package com.k410sh4.budsia.core.audio.enhancement

import com.k410sh4.budsia.core.audio.model.AudioFrame

data class EnhancementResult(
    val frame: AudioFrame,
    val applied: Boolean,
    val engineId: String
)

interface AudioEnhancementEngine {
    val engineId: String
    val isReady: Boolean

    fun reset()
    suspend fun process(frame: AudioFrame): EnhancementResult
}
