package com.k410sh4.budsia.core.audio.dsp

import com.k410sh4.budsia.core.audio.model.AudioFrame

interface AudioPreprocessor {
    fun reset()
    fun process(frame: AudioFrame): AudioFrame
}
