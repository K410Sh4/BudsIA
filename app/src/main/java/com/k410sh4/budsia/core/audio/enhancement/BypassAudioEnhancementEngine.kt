package com.k410sh4.budsia.core.audio.enhancement

import com.k410sh4.budsia.core.audio.model.AudioFrame

class BypassAudioEnhancementEngine : AudioEnhancementEngine {
    override val engineId: String = "bypass"
    override val isReady: Boolean = false

    override fun reset() = Unit

    override suspend fun process(frame: AudioFrame): EnhancementResult =
        EnhancementResult(
            frame = frame,
            applied = false,
            engineId = engineId
        )
}
