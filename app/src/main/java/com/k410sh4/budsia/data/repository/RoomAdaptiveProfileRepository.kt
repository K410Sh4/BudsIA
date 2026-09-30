package com.k410sh4.budsia.data.repository

import androidx.room.withTransaction
import com.k410sh4.budsia.core.ai.adaptation.AdaptiveAudioProfile
import com.k410sh4.budsia.core.ai.adaptation.AdaptiveFeedback
import com.k410sh4.budsia.core.ai.adaptation.AdaptiveProfileDefaults
import com.k410sh4.budsia.core.ai.adaptation.AdaptiveProfileRepository
import com.k410sh4.budsia.core.ai.adaptation.AdaptiveTuningEngine
import com.k410sh4.budsia.core.time.WallClock
import com.k410sh4.budsia.data.local.room.AdaptiveFeedbackDao
import com.k410sh4.budsia.data.local.room.AdaptiveFeedbackEventEntity
import com.k410sh4.budsia.data.local.room.AdaptiveProfileDao
import com.k410sh4.budsia.data.local.room.AdaptiveProfileEntity
import com.k410sh4.budsia.data.local.room.BudsIADatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RoomAdaptiveProfileRepository(
    private val database: BudsIADatabase,
    private val profileDao: AdaptiveProfileDao,
    private val feedbackDao: AdaptiveFeedbackDao,
    private val tuningEngine: AdaptiveTuningEngine,
    private val wallClock: WallClock
) : AdaptiveProfileRepository {

    private val mutationMutex = Mutex()

    override fun observeProfiles(): Flow<List<AdaptiveAudioProfile>> =
        profileDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }

    override fun observeActive(): Flow<AdaptiveAudioProfile?> =
        profileDao.observeActive().map { it?.toDomain() }

    override suspend fun ensureDefaults() {
        mutationMutex.withLock {
            val now = wallClock.nowEpochMillis()
            val emptyDatabase = profileDao.count() == 0

            profileDao.insertAll(
                AdaptiveProfileDefaults.presets.map { preset ->
                    preset.copy(
                        isActive = emptyDatabase && preset.id == "general",
                        updatedAtEpochMs = now
                    ).toEntity()
                }
            )

            if (profileDao.getActiveOnce() == null) {
                check(profileDao.activate("general")) {
                    "Unable to recover the default adaptive profile."
                }
            }
        }
    }

    override suspend fun setActive(id: String): Result<Unit> =
        runCatching {
            mutationMutex.withLock {
                require(profileDao.getById(id) != null) {
                    "Unknown adaptive profile: $id"
                }
                check(profileDao.activate(id)) {
                    "Unable to activate adaptive profile: $id"
                }
            }
        }

    override suspend fun applyFeedback(
        profileId: String,
        feedback: AdaptiveFeedback,
        modelId: String
    ): Result<AdaptiveAudioProfile> = runCatching {
        mutationMutex.withLock {
            database.withTransaction {
                val current = profileDao.getById(profileId)
                    ?.toDomain()
                    ?: error("Adaptive profile not found: $profileId")

                val updated = tuningEngine.applyFeedback(
                    profile = current,
                    feedback = feedback,
                    nowEpochMillis = wallClock.nowEpochMillis()
                )

                profileDao.update(updated.toEntity())

                feedbackDao.insert(
                    AdaptiveFeedbackEventEntity(
                        profileId = profileId,
                        modelId = modelId,
                        feedbackType = feedback.name,
                        previousNeuralMix = current.neuralMix,
                        newNeuralMix = updated.neuralMix,
                        createdAtEpochMs = updated.updatedAtEpochMs
                    )
                )

                updated
            }
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
