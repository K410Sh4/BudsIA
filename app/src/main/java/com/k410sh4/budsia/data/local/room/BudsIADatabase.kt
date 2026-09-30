package com.k410sh4.budsia.data.local.room

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        AdaptiveProfileEntity::class,
        AdaptiveFeedbackEventEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class BudsIADatabase : RoomDatabase() {
    abstract fun adaptiveProfileDao(): AdaptiveProfileDao
    abstract fun adaptiveFeedbackDao(): AdaptiveFeedbackDao
}
