package com.k410sh4.budsia.data.repository

import com.k410sh4.budsia.core.ai.adaptation.AdaptiveAudioProfile
import com.k410sh4.budsia.core.ai.adaptation.AdaptiveFeedback
import com.k410sh4.budsia.core.ai.adaptation.AdaptiveProfileDefaults
import com.k410sh4.budsia.core.ai.adaptation.AdaptiveProfileRepository
import com.k410sh4.budsia.core.ai.adaptation.AdaptiveTuningEngine
import com.k410sh4.budsia.core.time.WallClock
import com.k410sh4.budsia.data.local.room.AdaptiveProfileDao
import com.k410sh4.budsia.data.local.room.AdaptiveProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RoomAdaptiveProfileRepository(
    private val dao: AdaptiveProfileDao,
    private val tuningEngine: AdaptiveTuningEngine,
    private val wallClock: WallClock
) : AdaptiveProfileRepository {

    private val mutationMutex = Mutex()

    override fun observeProfiles(): Flow<List<AdaptiveAudioProfile>> =
        dao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }

    override fun observeActive(): Flow<AdaptiveAudioProfile?> =
        dao.observeActive().map { it?.toDomain() }

    override suspend fun ensureDefaults() {
        mutationMutex.withLock {
            if (dao.count() == 0) {
                val now = wallClock.nowEpochMillis()
                dao.insertAll(
                    AdaptiveProfileDefaults.presets.map { profile ->
                        profile.copy(
                            updatedAtEpochMs = now
                        ).toEntity()
                    }
                )
                return
            }

            dao.insertAll(
                AdaptiveProfileDefaults.presets.map { preset ->
                    preset.copy(
                        isActive = false,
                        updatedAtEpochMs = wallClock.nowEpochMillis()
                    ).toEntity()
                }
            )
        }
    }

    override suspend fun setActive(id: String): Result<Unit> =
        runCatching {
            mutationMutex.withLock {
                require(dao.getById(id) != null) {
                    "Unknown adaptive profile: $id"
                }
                check(dao.activate(id)) {
                    "Unable to activate adaptive profile: $id"
                }
            }
        }

    override suspend fun applyFeedback(
        profileId: String,
        feedback: AdaptiveFeedback
    ): Result<AdaptiveAudioProfile> = runCatching {
        mutationMutex.withLock {
            val current = dao.getById(profileId)
                ?.toDomain()
                ?: error("Adaptive profile not found: $profileId")

            val updated = tuningEngine.applyFeedback(
                profile = current,
                feedback = feedback,
                nowEpochMillis = wallClock.nowEpochMillis()
            )

            dao.update(updated.toEntity())
            updated
        }
    }

    private fun AdaptiveProfileEntity.toDomain(): AdaptiveAudioProfile =
        AdaptiveAudioProfile(
            id = id,
            name = name,
            version = version,
            neuralMix = neuralMix.coerceIn(0f, 1f),
            autoAdaptEnabled = autoAdaptEnabled,
            feedbackCount = feedbackCount,
            positiveCount = positiveCount,
            negativeCount = negativeCount,
            tooAggressiveCount = tooAggressiveCount,
            tooWeakCount = tooWeakCount,
            updatedAtEpochMs = updatedAtEpochMs,
            isActive = isActive
        )

    private fun AdaptiveAudioProfile.toEntity(): AdaptiveProfileEntity =
        AdaptiveProfileEntity(
            id = id,
            name = name,
            version = version,
            neuralMix = neuralMix.coerceIn(0f, 1f),
            autoAdaptEnabled = autoAdaptEnabled,
            feedbackCount = feedbackCount,
            positiveCount = positiveCount,
            negativeCount = negativeCount,
            tooAggressiveCount = tooAggressiveCount,
            tooWeakCount = tooWeakCount,
            updatedAtEpochMs = updatedAtEpochMs,
            isActive = isActive
        )
}
