package com.k410sh4.budsia.core.ai.models

data class AiModelDescriptor(
    val id: String,
    val displayName: String,
    val family: String,
    val version: String,
    val fileName: String,
    val downloadUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val sampleRateHz: Int,
    val qualityTier: QualityTier
)

enum class QualityTier {
    ECO,
    BALANCED,
    MAX_QUALITY
}

object AiModelCatalog {
    val DPDFNET2_48K_HR = AiModelDescriptor(
        id = "dpdfnet2-48k-hr",
        displayName = "DPDFNet2 48 kHz HR",
        family = "DPDFNet",
        version = "speech-enhancement-models-2026-03",
        fileName = "dpdfnet2_48khz_hr.onnx",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/speech-enhancement-models/dpdfnet2_48khz_hr.onnx",
        sha256 = "0b399f8a58dc4d70d8cd97541f5c39869406145193b957d00a03b66070944928",
        sizeBytes = 10_596_848L,
        sampleRateHz = 48_000,
        qualityTier = QualityTier.MAX_QUALITY
    )

    val GTCRN_16K = AiModelDescriptor(
        id = "gtcrn-simple-16k",
        displayName = "GTCRN Simple 16 kHz",
        family = "GTCRN",
        version = "speech-enhancement-models-2025-03",
        fileName = "gtcrn_simple.onnx",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/speech-enhancement-models/gtcrn_simple.onnx",
        sha256 = "e77603ac0c23dac3227dd2d7135b3a585cbee2679048aecfa886657d3ae1b534",
        sizeBytes = 535_638L,
        sampleRateHz = 16_000,
        qualityTier = QualityTier.ECO
    )

    val all: List<AiModelDescriptor> = listOf(
        DPDFNET2_48K_HR,
        GTCRN_16K
    )

    fun byId(id: String): AiModelDescriptor? =
        all.firstOrNull { it.id == id }

    fun bestForSampleRate(sampleRateHz: Int): AiModelDescriptor? =
        all
            .asSequence()
            .filter { it.sampleRateHz == sampleRateHz }
            .maxByOrNull { it.qualityTier.priority }

    private val QualityTier.priority: Int
        get() = when (this) {
            QualityTier.ECO -> 1
            QualityTier.BALANCED -> 2
            QualityTier.MAX_QUALITY -> 3
        }
}
