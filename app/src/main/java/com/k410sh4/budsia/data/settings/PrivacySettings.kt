package com.k410sh4.budsia.data.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("budsia_settings")

@Singleton
class PrivacySettings @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val strictOfflineKey = booleanPreferencesKey("strict_offline")
    private val saveTranscriptKey = booleanPreferencesKey("save_transcript")
    private val targetLanguageKey = stringPreferencesKey("target_language")

    val strictOffline: Flow<Boolean> = context.dataStore.data.map { it[strictOfflineKey] ?: true }
    val saveTranscript: Flow<Boolean> = context.dataStore.data.map { it[saveTranscriptKey] ?: true }
    val targetLanguage: Flow<String> = context.dataStore.data.map { it[targetLanguageKey] ?: "pt" }

    suspend fun setStrictOffline(value: Boolean) {
        context.dataStore.edit { it[strictOfflineKey] = value }
    }

    suspend fun setSaveTranscript(value: Boolean) {
        context.dataStore.edit { it[saveTranscriptKey] = value }
    }

    suspend fun setTargetLanguage(value: String) {
        context.dataStore.edit { it[targetLanguageKey] = value }
    }
}
