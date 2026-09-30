package com.k410sh4.budsia.core.ai.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiModelCatalogTest {

    @Test
    fun productionModelsPinIntegrityAndExpectedRates() {
        val fullBand = AiModelCatalog.DPDFNET2_48K_HR
        val bluetooth = AiModelCatalog.GTCRN_16K

        assertEquals(48_000, fullBand.sampleRateHz)
        assertEquals(10_596_848L, fullBand.sizeBytes)
        assertEquals(64, fullBand.sha256.length)
        assertTrue(fullBand.downloadUrl.startsWith("https://"))
        assertEquals(
            QualityTier.MAX_QUALITY,
            fullBand.qualityTier
        )

        assertEquals(16_000, bluetooth.sampleRateHz)
        assertEquals(535_638L, bluetooth.sizeBytes)
        assertEquals(64, bluetooth.sha256.length)
        assertTrue(bluetooth.downloadUrl.startsWith("https://"))
        assertEquals(QualityTier.ECO, bluetooth.qualityTier)
    }

    @Test
    fun selectsBestModelForActualNativeSampleRate() {
        assertEquals(
            AiModelCatalog.DPDFNET2_48K_HR.id,
            AiModelCatalog.bestForSampleRate(48_000)?.id
        )
        assertEquals(
            AiModelCatalog.GTCRN_16K.id,
            AiModelCatalog.bestForSampleRate(16_000)?.id
        )
        assertNull(AiModelCatalog.bestForSampleRate(32_000))
    }
}
