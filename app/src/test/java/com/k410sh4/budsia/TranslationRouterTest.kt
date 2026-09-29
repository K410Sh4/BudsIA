package com.k410sh4.budsia

import com.k410sh4.budsia.domain.model.TranslationMode
import com.k410sh4.budsia.domain.model.TranslationRouter
import org.junit.Assert.*
import org.junit.Test

class TranslationRouterTest {
    @Test
    fun automaticModeTranslatesPortugueseToEnglish() {
        val route = TranslationRouter.route(TranslationMode.AUTO_PT_EN, "pt-BR")
        assertEquals("pt", route.sourceTag)
        assertEquals("en", route.targetTag)
        assertTrue(route.shouldTranslate)
    }

    @Test
    fun automaticModeTranslatesEnglishToPortuguese() {
        val route = TranslationRouter.route(TranslationMode.AUTO_PT_EN, "en-US")
        assertEquals("en", route.sourceTag)
        assertEquals("pt", route.targetTag)
        assertTrue(route.shouldTranslate)
    }

    @Test
    fun automaticModeSendsOtherLanguagesToPortuguese() {
        val route = TranslationRouter.route(TranslationMode.AUTO_PT_EN, "fr")
        assertEquals("fr", route.sourceTag)
        assertEquals("pt", route.targetTag)
        assertTrue(route.shouldTranslate)
    }

    @Test
    fun fixedDirectionDoesNotPretendWrongSourceLanguage() {
        val route = TranslationRouter.route(TranslationMode.PT_TO_EN, "en")
        assertFalse(route.shouldTranslate)
    }
}
