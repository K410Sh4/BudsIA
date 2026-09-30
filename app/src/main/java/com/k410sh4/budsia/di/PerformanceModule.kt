package com.k410sh4.budsia.di

import android.content.Context
import com.k410sh4.budsia.core.performance.AiPerformanceGovernor
import com.k410sh4.budsia.core.performance.AiPerformanceMonitor
import com.k410sh4.budsia.core.performance.AiPerformanceSettingsRepository
import com.k410sh4.budsia.core.performance.PreferencesAiPerformanceSettingsRepository
import com.k410sh4.budsia.core.performance.AndroidAiPerformanceMonitor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@Module
@InstallIn(SingletonComponent::class)
object PerformanceModule {

    @Provides
    @Singleton
    fun provideAiPerformanceMonitor(
        @ApplicationContext context: Context,
        @ApplicationScope applicationScope: CoroutineScope
    ): AiPerformanceMonitor =
        AndroidAiPerformanceMonitor(
            context = context,
            applicationScope = applicationScope
        )

    @Provides
    @Singleton
    fun provideAiPerformanceSettingsRepository(
        @ApplicationContext context: Context,
        @ApplicationScope applicationScope: CoroutineScope
    ): AiPerformanceSettingsRepository =
        PreferencesAiPerformanceSettingsRepository(
            context = context,
            applicationScope = applicationScope
        )

    @Provides
    @Singleton
    fun provideAiPerformanceGovernor(): AiPerformanceGovernor =
        AiPerformanceGovernor()
}
