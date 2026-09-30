package com.k410sh4.budsia.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AdaptiveFeedbackDao {

    @Insert
    suspend fun insert(event: AdaptiveFeedbackEventEntity): Long

    @Query(
        "SELECT * FROM adaptive_feedback_events " +
            "WHERE profileId = :profileId " +
            "ORDER BY createdAtEpochMs DESC, id DESC"
    )
    fun observeForProfile(
        profileId: String
    ): Flow<List<AdaptiveFeedbackEventEntity>>

    @Query(
        "SELECT COUNT(*) FROM adaptive_feedback_events " +
            "WHERE profileId = :profileId"
    )
    suspend fun countForProfile(profileId: String): Int
}
