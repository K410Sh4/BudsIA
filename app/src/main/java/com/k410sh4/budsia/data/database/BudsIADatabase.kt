package com.k410sh4.budsia.data.database

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "conversation_item")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val speakerLabel: String,
    val speakerSimilarity: Float?,
    val originalText: String,
    val languageTag: String?,
    val languageConfidence: Float?,
    val translationTargetTag: String?,
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
    version = 3,
    exportSchema = false
)
abstract class BudsIADatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversation_item ADD COLUMN languageConfidence REAL")
                db.execSQL("ALTER TABLE conversation_item ADD COLUMN translationTargetTag TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversation_item ADD COLUMN speakerSimilarity REAL")
            }
        }
    }
}
