package com.k410sh4.budsia.di

import android.content.Context
import com.k410sh4.budsia.core.ai.adaptation.AdaptiveBlendProcessor
import com.k410sh4.budsia.core.ai.adaptation.AdaptiveProfileRepository
import com.k410sh4.budsia.core.ai.adaptation.AdaptiveTuningEngine
import com.k410sh4.budsia.core.ai.adaptation.PreferencesAdaptiveProfileRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@Module
@InstallIn(SingletonComponent::class)
object AdaptationModule {

    @Provides
    @Singleton
    fun provideAdaptiveTuningEngine(): AdaptiveTuningEngine =
        AdaptiveTuningEngine()

    @Provides
    @Singleton
    fun provideAdaptiveBlendProcessor(): AdaptiveBlendProcessor =
        AdaptiveBlendProcessor()

    @Provides
    @Singleton
    fun provideAdaptiveProfileRepository(
        @ApplicationContext context: Context,
        @ApplicationScope applicationScope: CoroutineScope,
        tuner: AdaptiveTuningEngine
    ): AdaptiveProfileRepository =
        PreferencesAdaptiveProfileRepository(
            context = context,
            applicationScope = applicationScope,
            tuner = tuner
        )
}
