package com.k410sh4.budsia.core.quality

enum class TranscriptQualityLevel {
    GOOD,
    UNCERTAIN,
    REJECTED
}

data class TranscriptQuality(
    val level: TranscriptQualityLevel,
    val score: Float,
    val reason: String
)

class TranscriptQualityEvaluator {
    fun evaluate(
        text: String,
        durationMs: Long,
        rms: Float
    ): TranscriptQuality {
        val clean = text.trim()
        if (clean.isBlank()) {
            return TranscriptQuality(TranscriptQualityLevel.REJECTED, 0f, "sem texto")
        }
        if (durationMs < 450L) {
            return TranscriptQuality(TranscriptQualityLevel.REJECTED, 0.1f, "fala curta demais")
        }
        if (rms < 0.0025f) {
            return TranscriptQuality(TranscriptQualityLevel.REJECTED, 0.15f, "áudio muito baixo")
        }

        var score = 1f
        val reasons = mutableListOf<String>()
        val words = clean.lowercase()
            .replace(Regex("[^\p{L}\p{N}\s']"), " ")
            .split(Regex("""\s+"""))
            .filter { it.isNotBlank() }

        if (durationMs < 900L) {
            score -= 0.20f
            reasons += "trecho curto"
        }

        if (words.size <= 1) {
            score -= 0.25f
            reasons += "poucas palavras"
        }

        if (words.size >= 4) {
            val maxRun = longestRepeatedRun(words)
            if (maxRun >= 4) {
                score -= 0.45f
                reasons += "repetição anormal"
            } else if (maxRun == 3) {
                score -= 0.25f
                reasons += "repetição elevada"
            }
        }

        val alphaCount = clean.count { it.isLetter() }
        if (alphaCount < 2) {
            score -= 0.35f
            reasons += "texto pouco informativo"
        }

        score = score.coerceIn(0f, 1f)
        val level = when {
            score < 0.35f -> TranscriptQualityLevel.REJECTED
            score < 0.68f -> TranscriptQualityLevel.UNCERTAIN
            else -> TranscriptQualityLevel.GOOD
        }

        return TranscriptQuality(
            level = level,
            score = score,
            reason = if (reasons.isEmpty()) "trecho consistente" else reasons.joinToString(", ")
        )
    }

    private fun longestRepeatedRun(words: List<String>): Int {
        var best = 1
        var run = 1
        for (i in 1 until words.size) {
            if (words[i] == words[i - 1]) {
                run += 1
                best = maxOf(best, run)
            } else {
                run = 1
            }
        }
        return best
    }
}
