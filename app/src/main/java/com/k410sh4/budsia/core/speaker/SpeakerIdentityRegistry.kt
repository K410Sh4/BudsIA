package com.k410sh4.budsia.core.speaker

import kotlin.math.sqrt

data class SpeakerAssignment(
    val label: String,
    val confidence: Float?,
    val stable: Boolean,
    val reason: String
)

class SpeakerIdentityRegistry(
    private val maxSpeakers: Int = 4,
    private val matchThreshold: Float = 0.56f,
    private val strongMatchThreshold: Float = 0.66f,
    private val ambiguityMargin: Float = 0.07f,
    private val newSpeakerCeiling: Float = 0.48f,
    private val minEnrollmentDurationMs: Long = 1_800L,
    private val minEnrollmentDiarizationConfidence: Float = 0.30f
) {
    private data class Profile(
        val label: String,
        var centroid: FloatArray,
        var observations: Int,
        var totalDurationMs: Long
    )

    private val profiles = mutableListOf<Profile>()
    private val localWindowAssignments = mutableMapOf<Int, String>()

    val speakerCount: Int get() = profiles.size

    fun reset() {
        profiles.clear()
        localWindowAssignments.clear()
    }

    fun beginWindow() {
        localWindowAssignments.clear()
    }

    fun resolve(
        localSpeakerId: Int,
        embedding: FloatArray?,
        durationMs: Long,
        diarizationConfidence: Float
    ): SpeakerAssignment {
        if (embedding == null || embedding.isEmpty()) {
            val mapped = localWindowAssignments[localSpeakerId]
            return if (mapped != null) {
                SpeakerAssignment(mapped, null, stable = true, reason = "mesmo falante no lote")
            } else {
                SpeakerAssignment("Falante ?", null, stable = false, reason = "embedding indisponível")
            }
        }

        val normalized = embedding.copyOf()
        normalizeInPlace(normalized)

        localWindowAssignments[localSpeakerId]?.let { label ->
            val profile = profiles.firstOrNull { it.label == label }
            if (profile != null) {
                val score = cosine(normalized, profile.centroid)
                if (score >= 0.42f || durationMs < minEnrollmentDurationMs) {
                    if (score >= matchThreshold && durationMs >= 1_200L) {
                        updateProfile(profile, normalized, durationMs)
                    }
                    return SpeakerAssignment(
                        label = label,
                        confidence = score,
                        stable = profile.observations >= 2 || score >= strongMatchThreshold,
                        reason = "consistência dentro do mesmo lote"
                    )
                }
            }
        }

        if (profiles.isEmpty()) {
            return if (canEnroll(durationMs, diarizationConfidence)) {
                createProfile(localSpeakerId, normalized, durationMs, "primeiro falante confiável")
            } else {
                SpeakerAssignment("Falante ?", null, false, "trecho curto para cadastrar voz")
            }
        }

        val ranked = profiles
            .map { it to cosine(normalized, it.centroid) }
            .sortedByDescending { it.second }

        val best = ranked.first()
        val secondScore = ranked.getOrNull(1)?.second ?: -1f
        val margin = best.second - secondScore

        val accepted = when {
            profiles.size == 1 -> best.second >= matchThreshold
            best.second >= 0.78f -> true
            best.second >= strongMatchThreshold && margin >= 0.04f -> true
            best.second >= matchThreshold && margin >= ambiguityMargin -> true
            else -> false
        }

        if (accepted) {
            localWindowAssignments[localSpeakerId] = best.first.label
            if (durationMs >= 1_200L && diarizationConfidence >= 0.20f) {
                updateProfile(best.first, normalized, durationMs)
            }
            return SpeakerAssignment(
                label = best.first.label,
                confidence = best.second,
                stable = best.first.observations >= 2 || best.second >= strongMatchThreshold,
                reason = if (best.second >= strongMatchThreshold) "correspondência forte" else "correspondência com margem segura"
            )
        }

        if (best.second >= newSpeakerCeiling && margin < ambiguityMargin) {
            return SpeakerAssignment(
                "Falante ?",
                best.second,
                stable = false,
                reason = "duas vozes possíveis muito próximas"
            )
        }

        if (profiles.size >= maxSpeakers) {
            return SpeakerAssignment(
                "Falante ?",
                best.second,
                stable = false,
                reason = "limite de falantes atingido; identidade não foi forçada"
            )
        }

        if (best.second < newSpeakerCeiling && canEnroll(durationMs, diarizationConfidence)) {
            return createProfile(localSpeakerId, normalized, durationMs, "nova voz consistente")
        }

        return SpeakerAssignment(
            "Falante ?",
            best.second.takeIf { it >= 0f },
            stable = false,
            reason = "evidência insuficiente para identificar ou criar falante"
        )
    }

    private fun canEnroll(durationMs: Long, diarizationConfidence: Float): Boolean =
        durationMs >= minEnrollmentDurationMs &&
            diarizationConfidence >= minEnrollmentDiarizationConfidence

    private fun createProfile(
        localSpeakerId: Int,
        embedding: FloatArray,
        durationMs: Long,
        reason: String
    ): SpeakerAssignment {
        val label = "Falante " + speakerLetter(profiles.size)
        profiles += Profile(
            label = label,
            centroid = embedding.copyOf(),
            observations = 1,
            totalDurationMs = durationMs
        )
        localWindowAssignments[localSpeakerId] = label
        return SpeakerAssignment(label, null, stable = false, reason = reason)
    }

    private fun updateProfile(profile: Profile, embedding: FloatArray, durationMs: Long) {
        val alpha = when {
            profile.observations < 2 -> 0.30f
            profile.observations < 5 -> 0.20f
            else -> 0.12f
        }
        for (i in profile.centroid.indices) {
            profile.centroid[i] = profile.centroid[i] * (1f - alpha) + embedding[i] * alpha
        }
        normalizeInPlace(profile.centroid)
        profile.observations += 1
        profile.totalDurationMs += durationMs
    }

    private fun normalizeInPlace(values: FloatArray) {
        var sum = 0.0
        for (v in values) sum += v * v
        val norm = sqrt(sum).toFloat()
        if (norm <= 1e-8f) return
        for (i in values.indices) values[i] /= norm
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        val n = minOf(a.size, b.size)
        var dot = 0f
        for (i in 0 until n) dot += a[i] * b[i]
        return dot.coerceIn(-1f, 1f)
    }

    private fun speakerLetter(index: Int): String =
        if (index in 0..25) ('A'.code + index).toChar().toString() else (index + 1).toString()
}
