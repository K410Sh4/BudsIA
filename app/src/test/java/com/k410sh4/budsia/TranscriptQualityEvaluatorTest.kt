package com.k410sh4.budsia

import com.k410sh4.budsia.core.quality.TranscriptQualityEvaluator
import com.k410sh4.budsia.core.quality.TranscriptQualityLevel
import org.junit.Assert.*
import org.junit.Test

class TranscriptQualityEvaluatorTest {
    private val evaluator = TranscriptQualityEvaluator()

    @Test
    fun repeatedHallucinationIsNotGoodQuality() {
        val result = evaluator.evaluate(
            text = "iria iria iria iria iria até outra",
            durationMs = 2200,
            rms = 0.03f
        )
        assertTrue(result.level != TranscriptQualityLevel.GOOD)
    }

    @Test
    fun silentSegmentIsRejected() {
        val result = evaluator.evaluate(
            text = "alguma coisa",
            durationMs = 1800,
            rms = 0.0005f
        )
        assertEquals(TranscriptQualityLevel.REJECTED, result.level)
    }

    @Test
    fun normalSentenceIsAccepted() {
        val result = evaluator.evaluate(
            text = "Eu vou trabalhar amanhã de manhã",
            durationMs = 2800,
            rms = 0.03f
        )
        assertEquals(TranscriptQualityLevel.GOOD, result.level)
    }
}
