package com.k410sh4.budsia.core.ai.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiModelCatalogTest {

    @Test
    fun productionModelPinsIntegrityAndFullBandRate() {
        val model = AiModelCatalog.DPDFNET2_48K_HR

        assertEquals(48_000, model.sampleRateHz)
        assertEquals(10_596_848L, model.sizeBytes)
        assertEquals(64, model.sha256.length)
        assertTrue(model.downloadUrl.startsWith("https://"))
        assertEquals(QualityTier.MAX_QUALITY, model.qualityTier)
    }
}
