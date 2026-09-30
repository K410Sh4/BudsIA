package com.k410sh4.budsia.di

import android.content.Context
import com.k410sh4.budsia.core.performance.AiPerformancePolicy
import com.k410sh4.budsia.core.performance.AndroidDeviceHealthMonitor
import com.k410sh4.budsia.core.performance.AndroidInferencePerformanceHintFactory
import com.k410sh4.budsia.core.performance.DeviceHealthMonitor
import com.k410sh4.budsia.core.performance.InferencePerformanceHintFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.Executors
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AiInferenceDispatcher

@Module
@InstallIn(SingletonComponent::class)
object PerformanceModule {

    @Provides
    @Singleton
    fun provideDeviceHealthMonitor(
        @ApplicationContext context: Context,
        @ApplicationScope applicationScope: kotlinx.coroutines.CoroutineScope
    ): DeviceHealthMonitor =
        AndroidDeviceHealthMonitor(
            context = context,
            applicationScope = applicationScope
        )

    @Provides
    @Singleton
    fun provideAiPerformancePolicy(): AiPerformancePolicy =
        AiPerformancePolicy()

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
