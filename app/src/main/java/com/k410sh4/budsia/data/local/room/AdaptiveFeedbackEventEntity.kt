package com.k410sh4.budsia.data.local.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "adaptive_feedback_events",
    foreignKeys = [
        ForeignKey(
            entity = AdaptiveProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["profileId"]),
        Index(value = ["createdAtEpochMs"])
    ]
)
data class AdaptiveFeedbackEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val profileId: String,
    val modelId: String,
    val feedbackType: String,
    val previousNeuralMix: Float,
    val newNeuralMix: Float,
    val createdAtEpochMs: Long
)
