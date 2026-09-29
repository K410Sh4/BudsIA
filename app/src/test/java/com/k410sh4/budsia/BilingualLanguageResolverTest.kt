package com.k410sh4.budsia

import com.k410sh4.budsia.core.language.BilingualLanguageResolver
import com.k410sh4.budsia.domain.model.LanguageGuess
import org.junit.Assert.*
import org.junit.Test

class BilingualLanguageResolverTest {
    private val resolver = BilingualLanguageResolver()

    @Test
    fun falseSwedishFromWhisperDoesNotOverrideStrongPortugueseText() {
        val decision = resolver.resolve(
            whisperLanguage = "sv",
            textGuess = LanguageGuess("pt", 0.82f)
        )
        assertEquals("pt", decision.languageTag)
    }

    @Test
    fun unsupportedLanguageWithNoPtEnEvidenceBecomesUnknown() {
        val decision = resolver.resolve(
            whisperLanguage = "sv",
            textGuess = LanguageGuess("sv", 0.90f)
        )
        assertNull(decision.languageTag)
    }

    @Test
    fun whisperEnglishCanResolveWeakTextGuess() {
        val decision = resolver.resolve(
            whisperLanguage = "en",
            textGuess = LanguageGuess(null, 0.20f)
        )
        assertEquals("en", decision.languageTag)
    }

    @Test
    fun speakerPriorOnlyHelpsWhenCurrentEvidenceIsWeak() {
        val decision = resolver.resolve(
            whisperLanguage = "sv",
            textGuess = LanguageGuess(null, 0.20f),
            speakerPrior = "pt"
        )
        assertEquals("pt", decision.languageTag)
        assertTrue(decision.confidence < 0.6f)
    }
}
