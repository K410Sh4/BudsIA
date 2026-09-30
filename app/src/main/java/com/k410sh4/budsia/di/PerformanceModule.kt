package com.k410sh4.budsia.di

import android.content.Context
import com.k410sh4.budsia.core.performance.AiPerformanceGovernor
import com.k410sh4.budsia.core.performance.AiPerformanceMonitor
import com.k410sh4.budsia.core.performance.AiPerformanceSettingsRepository
import com.k410sh4.budsia.core.performance.AndroidAiPerformanceMonitor
import com.k410sh4.budsia.core.performance.AndroidInferencePerformanceHintFactory
import com.k410sh4.budsia.core.performance.InferencePerformanceHintFactory
import com.k410sh4.budsia.core.performance.PreferencesAiPerformanceSettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.Executors
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AiInferenceDispatcher

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

    @Provides
    @Singleton
    fun provideInferencePerformanceHintFactory(
        @ApplicationContext context: Context
    ): InferencePerformanceHintFactory =
        AndroidInferencePerformanceHintFactory(context)

    @Provides
    @Singleton
    @AiInferenceDispatcher
    fun provideAiInferenceDispatcher(): CoroutineDispatcher =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(
                runnable,
                "BudsIA-AI-Inference"
            ).apply {
                priority = Thread.NORM_PRIORITY
            }
        }.asCoroutineDispatcher()
}
