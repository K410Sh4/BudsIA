package com.k410sh4.budsia.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "conversation_item")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val speakerLabel: String,
    val originalText: String,
    val languageTag: String?,
    val translatedText: String?,
    val recognitionConfidence: Float?,
    val signalsJson: String
)

@Dao
interface ConversationDao {
    @Insert
    suspend fun insert(item: ConversationEntity): Long

    @Query("SELECT * FROM conversation_item ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 300): Flow<List<ConversationEntity>>

    @Query("DELETE FROM conversation_item")
    suspend fun clear()
}

@Database(
    entities = [ConversationEntity::class],
    version = 1,
    exportSchema = false
)
abstract class BudsIADatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
}
