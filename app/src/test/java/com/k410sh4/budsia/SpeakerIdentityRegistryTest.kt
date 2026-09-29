package com.k410sh4.budsia

import com.k410sh4.budsia.core.speaker.SpeakerIdentityRegistry
import org.junit.Assert.*
import org.junit.Test

class SpeakerIdentityRegistryTest {
    @Test
    fun shortSegmentCannotCreateSpeaker() {
        val registry = SpeakerIdentityRegistry(maxSpeakers = 2)
        registry.beginWindow()
        val result = registry.resolve(
            localSpeakerId = 0,
            embedding = floatArrayOf(1f, 0f, 0f),
            durationMs = 700,
            diarizationConfidence = 0.9f
        )
        assertEquals("Falante ?", result.label)
        assertEquals(0, registry.speakerCount)
    }

    @Test
    fun sameVoiceAcrossWindowsKeepsSpeaker() {
        val registry = SpeakerIdentityRegistry(maxSpeakers = 2)
        registry.beginWindow()
        val first = registry.resolve(
            0,
            floatArrayOf(1f, 0f, 0f),
            2500,
            0.9f
        )
        assertEquals("Falante A", first.label)

        registry.beginWindow()
        val second = registry.resolve(
            0,
            floatArrayOf(0.99f, 0.08f, 0f),
            2200,
            0.9f
        )
        assertEquals("Falante A", second.label)
    }

    @Test
    fun differentVoiceCreatesSecondSpeaker() {
        val registry = SpeakerIdentityRegistry(maxSpeakers = 2)
        registry.beginWindow()
        registry.resolve(0, floatArrayOf(1f, 0f, 0f), 2500, 0.9f)

        registry.beginWindow()
        val second = registry.resolve(
            1,
            floatArrayOf(0f, 1f, 0f),
            2500,
            0.9f
        )
        assertEquals("Falante B", second.label)
        assertEquals(2, registry.speakerCount)
    }

    @Test
    fun ambiguousVoiceIsRejectedInsteadOfCreatingExtraSpeaker() {
        val registry = SpeakerIdentityRegistry(maxSpeakers = 4)
        registry.beginWindow()
        registry.resolve(0, floatArrayOf(1f, 0f, 0f), 2500, 0.9f)
        registry.beginWindow()
        registry.resolve(1, floatArrayOf(0f, 1f, 0f), 2500, 0.9f)

        registry.beginWindow()
        val ambiguous = registry.resolve(
            2,
            floatArrayOf(0.707f, 0.707f, 0f),
            2500,
            0.9f
        )

        assertEquals("Falante ?", ambiguous.label)
        assertFalse(ambiguous.stable)
        assertEquals(2, registry.speakerCount)
    }

    @Test
    fun maxSpeakerLimitNeverForcesLowSimilarityMatch() {
        val registry = SpeakerIdentityRegistry(maxSpeakers = 2)
        registry.beginWindow()
        registry.resolve(0, floatArrayOf(1f, 0f, 0f), 2500, 0.9f)
        registry.beginWindow()
        registry.resolve(1, floatArrayOf(0f, 1f, 0f), 2500, 0.9f)

        registry.beginWindow()
        val unknown = registry.resolve(
            2,
            floatArrayOf(0f, 0f, 1f),
            2500,
            0.9f
        )

        assertEquals("Falante ?", unknown.label)
        assertEquals(2, registry.speakerCount)
    }
}
