package com.k410sh4.budsia.di

import android.content.Context
import androidx.room.Room
import com.k410sh4.budsia.core.ai.adaptation.AdaptiveAudioMixer
import com.k410sh4.budsia.core.ai.adaptation.AdaptiveProfileController
import com.k410sh4.budsia.core.ai.adaptation.AdaptiveProfileRepository
import com.k410sh4.budsia.core.ai.adaptation.AdaptiveTuningEngine
import com.k410sh4.budsia.core.time.SystemWallClock
import com.k410sh4.budsia.core.time.WallClock
import com.k410sh4.budsia.data.local.room.AdaptiveFeedbackDao
import com.k410sh4.budsia.data.local.room.AdaptiveProfileDao
import com.k410sh4.budsia.data.local.room.BudsIADatabase
import com.k410sh4.budsia.data.repository.RoomAdaptiveProfileRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): BudsIADatabase = Room.databaseBuilder(
        context,
        BudsIADatabase::class.java,
        "budsia.db"
    ).build()

    @Provides
    fun provideAdaptiveProfileDao(
        database: BudsIADatabase
    ): AdaptiveProfileDao = database.adaptiveProfileDao()

    @Provides
    fun provideAdaptiveFeedbackDao(
        database: BudsIADatabase
    ): AdaptiveFeedbackDao = database.adaptiveFeedbackDao()

    @Provides
    @Singleton
    fun provideAdaptiveTuningEngine(): AdaptiveTuningEngine =
        AdaptiveTuningEngine()

    @Provides
    @Singleton
    fun provideAdaptiveAudioMixer(): AdaptiveAudioMixer =
        AdaptiveAudioMixer()

    @Provides
    @Singleton
    fun provideWallClock(): WallClock =
        SystemWallClock()

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideAdaptiveProfileRepository(
        database: BudsIADatabase,
        profileDao: AdaptiveProfileDao,
        feedbackDao: AdaptiveFeedbackDao,
        tuningEngine: AdaptiveTuningEngine,
        wallClock: WallClock
    ): AdaptiveProfileRepository =
        RoomAdaptiveProfileRepository(
            database = database,
            profileDao = profileDao,
            feedbackDao = feedbackDao,
            tuningEngine = tuningEngine,
            wallClock = wallClock
        )

    @Provides
    @Singleton
    fun provideAdaptiveProfileController(
        repository: AdaptiveProfileRepository,
        @ApplicationScope scope: CoroutineScope
    ): AdaptiveProfileController =
        AdaptiveProfileController(
            repository = repository,
            applicationScope = scope
        )
}
