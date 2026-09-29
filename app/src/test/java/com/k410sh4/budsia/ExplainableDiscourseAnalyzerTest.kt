package com.k410sh4.budsia

import com.k410sh4.budsia.core.analysis.ExplainableDiscourseAnalyzer
import com.k410sh4.budsia.domain.model.SignalType
import org.junit.Assert.*
import org.junit.Test

class ExplainableDiscourseAnalyzerTest {
    private val analyzer = ExplainableDiscourseAnalyzer()

    @Test
    fun detectsGuiltPressureWithoutClaimingHiddenIntent() {
        val signals = analyzer.analyze("Se você realmente gostasse de mim, faria isso.")
        val guilt = signals.firstOrNull { it.type == SignalType.GUILT_PRESSURE }
        assertNotNull(guilt)
        assertTrue(guilt!!.confidence in 0f..1f)
        assertTrue(guilt.evidence.isNotBlank())
        assertTrue(guilt.explanation.contains("not proof", ignoreCase = true))
    }

    @Test
    fun detectsPossibleFalseDilemma() {
        val signals = analyzer.analyze("Ou você concorda comigo ou você está contra mim.")
        assertTrue(signals.any { it.type == SignalType.FALSE_DILEMMA })
    }

    @Test
    fun neutralSentenceDoesNotInventSignals() {
        val signals = analyzer.analyze("A reunião começa às três da tarde.")
        assertTrue(signals.isEmpty())
    }
}
