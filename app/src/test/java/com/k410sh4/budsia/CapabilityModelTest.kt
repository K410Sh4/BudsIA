package com.k410sh4.budsia

import com.k410sh4.budsia.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Test

class CapabilityModelTest {
    @Test
    fun unfinishedFeaturesAreNotMarkedReady() {
        val capabilities = AiCapabilities()
        assertEquals(AiCapabilityState.PLANNED, capabilities.speakerDiarization.state)
        assertEquals(AiCapabilityState.PLANNED, capabilities.speakerIdentification.state)
        assertEquals(AiCapabilityState.PLANNED, capabilities.localLlm.state)
        assertEquals(AiCapabilityState.EXPERIMENTAL, capabilities.discourseAnalysis.state)
    }
}
