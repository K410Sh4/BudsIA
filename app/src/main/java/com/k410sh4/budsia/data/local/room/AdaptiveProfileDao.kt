package com.k410sh4.budsia.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AdaptiveProfileDao {

    @Query(
        "SELECT * FROM adaptive_audio_profiles " +
            "ORDER BY CASE id " +
            "WHEN 'general' THEN 0 " +
            "WHEN 'home' THEN 1 " +
            "WHEN 'street' THEN 2 " +
            "WHEN 'car' THEN 3 " +
            "WHEN 'work' THEN 4 " +
            "ELSE 100 END, name"
    )
    abstract fun observeAll(): Flow<List<AdaptiveProfileEntity>>

    @Query(
        "SELECT * FROM adaptive_audio_profiles " +
            "WHERE isActive = 1 LIMIT 1"
    )
    abstract fun observeActive(): Flow<AdaptiveProfileEntity?>

    @Query(
        "SELECT * FROM adaptive_audio_profiles " +
            "WHERE isActive = 1 LIMIT 1"
    )
    abstract suspend fun getActiveOnce(): AdaptiveProfileEntity?

    @Query(
        "SELECT * FROM adaptive_audio_profiles WHERE id = :id LIMIT 1"
    )
    abstract suspend fun getById(id: String): AdaptiveProfileEntity?

    @Query("SELECT COUNT(*) FROM adaptive_audio_profiles")
    abstract suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAll(
        profiles: List<AdaptiveProfileEntity>
    )

    @Update
    abstract suspend fun update(profile: AdaptiveProfileEntity)

    @Query(
        "UPDATE adaptive_audio_profiles SET isActive = 0 " +
            "WHERE isActive = 1"
    )
    protected abstract suspend fun clearActive()

    @Query(
        "UPDATE adaptive_audio_profiles SET isActive = 1 " +
            "WHERE id = :id"
    )
    protected abstract suspend fun activateInternal(id: String): Int

    @Transaction
    open suspend fun activate(id: String): Boolean {
        clearActive()
        return activateInternal(id) == 1
    }
}
