package com.k410sh4.budsia.data.local.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "adaptive_audio_profiles",
    indices = [
        Index(value = ["isActive"])
    ]
)
data class AdaptiveProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val version: Int,
    val neuralMix: Float,
    val autoAdaptEnabled: Boolean,
    val feedbackCount: Int,
    val positiveCount: Int,
    val negativeCount: Int,
    val tooAggressiveCount: Int,
    val tooWeakCount: Int,
    val updatedAtEpochMs: Long,
    val isActive: Boolean
)
