package com.k410sh4.budsia.di

import com.k410sh4.budsia.core.focus.FocusModeResolver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FocusModule {

    @Provides
    @Singleton
    fun provideFocusModeResolver(): FocusModeResolver =
        FocusModeResolver()
}
