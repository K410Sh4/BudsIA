package com.k410sh4.budsia.core.performance

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.k410sh4.budsia.di.ApplicationScope
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class PreferencesAiPerformanceSettingsRepository(
    context: Context,
    @ApplicationScope applicationScope: CoroutineScope
) : AiPerformanceSettingsRepository {

    private val store: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = applicationScope,
            produceFile = {
                context.preferencesDataStoreFile(STORE_NAME)
            }
        )

    private val _settings =
        MutableStateFlow(AiPerformanceSettings())
    override val settings: StateFlow<AiPerformanceSettings> =
        _settings.asStateFlow()

    init {
        applicationScope.launch {
            store.data
                .catch { error ->
                    if (error is IOException) {
                        emit(emptyPreferences())
                    } else {
                        throw error
                    }
                }
                .collect { preferences ->
                    _settings.value =
                        readSettings(preferences)
                }
        }
    }

    override suspend fun setLowBatteryAutoFallbackEnabled(
        enabled: Boolean
    ) {
        store.edit { preferences ->
            preferences[
                LOW_BATTERY_FALLBACK_ENABLED
            ] = enabled
        }
    }

    override suspend fun setStopAiBelowBatteryPercent(
        percent: Int
    ) {
        store.edit { preferences ->
            preferences[STOP_AI_BELOW_PERCENT] =
                percent.coerceIn(
                    AiPerformanceSettings.MIN_STOP_PERCENT,
                    AiPerformanceSettings.MAX_STOP_PERCENT
                )
        }
    }

    private fun readSettings(
        preferences: Preferences
    ): AiPerformanceSettings {
        val stopPercent = (
            preferences[STOP_AI_BELOW_PERCENT]
                ?: AiPerformanceSettings.DEFAULT_STOP_PERCENT
            ).coerceIn(
                AiPerformanceSettings.MIN_STOP_PERCENT,
                AiPerformanceSettings.MAX_STOP_PERCENT
            )

        val ecoPercent = maxOf(
            stopPercent,
            AiPerformanceSettings.DEFAULT_ECO_PERCENT
        ).coerceAtMost(
            AiPerformanceSettings.MAX_ECO_PERCENT
        )

        return AiPerformanceSettings(
            lowBatteryAutoFallbackEnabled =
                preferences[
                    LOW_BATTERY_FALLBACK_ENABLED
                ] ?: false,
            stopAiBelowBatteryPercent = stopPercent,
            ecoBelowBatteryPercent = ecoPercent
        )
    }

    companion object {
        private const val STORE_NAME =
            "ai-performance-settings.preferences_pb"

        private val LOW_BATTERY_FALLBACK_ENABLED =
            booleanPreferencesKey(
                "low_battery_fallback_enabled"
            )
        private val STOP_AI_BELOW_PERCENT =
            intPreferencesKey(
                "stop_ai_below_percent"
            )
    }
}
