package com.k410sh4.budsia.di

import android.content.Context
import com.k410sh4.budsia.core.ai.evaluation.AdaptiveAbEvaluationRepository
import com.k410sh4.budsia.core.ai.evaluation.AdaptiveAbEvaluator
import com.k410sh4.budsia.core.ai.evaluation.PreferencesAdaptiveAbEvaluationRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@Module
@InstallIn(SingletonComponent::class)
object EvaluationModule {

    @Provides
    @Singleton
    fun provideAdaptiveAbEvaluator(): AdaptiveAbEvaluator =
        AdaptiveAbEvaluator()

    @Provides
    @Singleton
    fun provideAdaptiveAbEvaluationRepository(
        @ApplicationContext context: Context,
        @ApplicationScope applicationScope: CoroutineScope
    ): AdaptiveAbEvaluationRepository =
        PreferencesAdaptiveAbEvaluationRepository(
            context = context,
            applicationScope = applicationScope
        )
}
