package com.k410sh4.budsia.di

import android.content.Context
import androidx.room.Room
import com.k410sh4.budsia.data.database.BudsIADatabase
import com.k410sh4.budsia.data.database.ConversationDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BudsIADatabase =
        Room.databaseBuilder(context, BudsIADatabase::class.java, "budsia.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideConversationDao(db: BudsIADatabase): ConversationDao = db.conversationDao()
}
