package com.k410sh4.budsia.data.repository

import com.k410sh4.budsia.data.database.ConversationDao
import com.k410sh4.budsia.data.database.ConversationEntity
import com.k410sh4.budsia.domain.model.AnalysisSignal
import com.k410sh4.budsia.domain.model.ConversationEngine
import com.k410sh4.budsia.domain.model.ConversationItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepository @Inject constructor(
    private val dao: ConversationDao
) {
    val recent: Flow<List<ConversationItem>> = dao.observeRecent().map { rows ->
        rows.map { it.toDomain() }
    }

    suspend fun insert(item: ConversationItem): Long =
        dao.insert(item.toEntity())

    suspend fun clear() = dao.clear()

    private fun ConversationEntity.toDomain() = ConversationItem(
        id = id,
        sessionId = sessionId,
        timestamp = timestamp,
        speakerLabel = speakerLabel,
        speakerSimilarity = speakerSimilarity,
        speakerStable = speakerStable,
        originalText = originalText,
        languageTag = languageTag,
        languageConfidence = languageConfidence,
        languageReason = languageReason,
        translationTargetTag = translationTargetTag,
        translatedText = translatedText,
        translationStatus = translationStatus,
        recognitionConfidence = recognitionConfidence,
        transcriptQuality = transcriptQuality,
        transcriptQualityReason = transcriptQualityReason,
        durationMs = durationMs,
        engine = runCatching { ConversationEngine.valueOf(engine) }
            .getOrDefault(ConversationEngine.ANDROID_COMPAT),
        signals = decodeSignals(signalsJson)
    )

    private fun ConversationItem.toEntity() = ConversationEntity(
        id = id,
        sessionId = sessionId,
        timestamp = timestamp,
        speakerLabel = speakerLabel,
        speakerSimilarity = speakerSimilarity,
        speakerStable = speakerStable,
        originalText = originalText,
        languageTag = languageTag,
        languageConfidence = languageConfidence,
        languageReason = languageReason,
        translationTargetTag = translationTargetTag,
        translatedText = translatedText,
        translationStatus = translationStatus,
        recognitionConfidence = recognitionConfidence,
        transcriptQuality = transcriptQuality,
        transcriptQualityReason = transcriptQualityReason,
        durationMs = durationMs,
        engine = engine.name,
        signalsJson = encodeSignals(signals)
    )

    private fun encodeSignals(signals: List<AnalysisSignal>): String =
        signals.joinToString("\n") {
            listOf(
                it.type.name,
                it.confidence.toString(),
                escape(it.evidence),
                escape(it.explanation)
            ).joinToString("\t")
        }

    private fun decodeSignals(raw: String): List<AnalysisSignal> =
        raw.lines().mapNotNull { line ->
            val parts = line.split("\t")
            if (parts.size < 4) return@mapNotNull null
            runCatching {
                AnalysisSignal(
                    type = com.k410sh4.budsia.domain.model.SignalType.valueOf(parts[0]),
                    confidence = parts[1].toFloat(),
                    evidence = unescape(parts[2]),
                    explanation = unescape(parts[3])
                )
            }.getOrNull()
        }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

    private fun unescape(value: String): String =
        value.replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\")
}
