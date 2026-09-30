package com.k410sh4.budsia.core.ai.evaluation

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.k410sh4.budsia.core.ai.adaptation.AcousticEnvironment
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class PreferencesAdaptiveAbEvaluationRepository(
    context: Context,
    applicationScope: CoroutineScope
) : AdaptiveAbEvaluationRepository {

    private val store: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = applicationScope,
            produceFile = {
                context.preferencesDataStoreFile(STORE_NAME)
            }
        )

    private val _stats = MutableStateFlow(
        AcousticEnvironment.entries.associateWith {
            AdaptiveAbStats.empty(it)
        }
    )
    override val stats:
        StateFlow<Map<AcousticEnvironment, AdaptiveAbStats>> =
        _stats.asStateFlow()

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
                    _stats.value =
                        AcousticEnvironment.entries.associateWith {
                            readStats(preferences, it)
                        }
                }
        }
    }

    override suspend fun record(
        environment: AcousticEnvironment,
        choice: AdaptiveAbChoice
    ): AdaptiveAbStats {
        var updated = AdaptiveAbStats.empty(environment)

        store.edit { preferences ->
            val current = readStats(
                preferences,
                environment
            )

            updated = when (choice) {
                AdaptiveAbChoice.FACTORY ->
                    current.copy(
                        factoryWins =
                            current.factoryWins + 1L
                    )

                AdaptiveAbChoice.CANDIDATE ->
                    current.copy(
                        candidateWins =
                            current.candidateWins + 1L
                    )

                AdaptiveAbChoice.NO_DIFFERENCE ->
                    current.copy(
                        noDifference =
                            current.noDifference + 1L
                    )
            }

            writeStats(preferences, updated)
        }

        publish(environment, updated)
        return updated
    }

    override suspend fun reset(
        environment: AcousticEnvironment
    ): AdaptiveAbStats {
        val reset = AdaptiveAbStats.empty(environment)

        store.edit { preferences ->
            writeStats(preferences, reset)
        }

        publish(environment, reset)
        return reset
    }

    private fun publish(
        environment: AcousticEnvironment,
        value: AdaptiveAbStats
    ) {
        _stats.value = _stats.value.toMutableMap().apply {
            put(environment, value)
        }
    }

    private fun readStats(
        preferences: Preferences,
        environment: AcousticEnvironment
    ): AdaptiveAbStats = AdaptiveAbStats(
        environment = environment,
        factoryWins = (
            preferences[factoryKey(environment)] ?: 0L
            ).coerceAtLeast(0L),
        candidateWins = (
            preferences[candidateKey(environment)] ?: 0L
            ).coerceAtLeast(0L),
        noDifference = (
            preferences[noDifferenceKey(environment)] ?: 0L
            ).coerceAtLeast(0L)
    )

    private fun writeStats(
        preferences:
            androidx.datastore.preferences.core.MutablePreferences,
        stats: AdaptiveAbStats
    ) {
        preferences[factoryKey(stats.environment)] =
            stats.factoryWins
        preferences[candidateKey(stats.environment)] =
            stats.candidateWins
        preferences[noDifferenceKey(stats.environment)] =
            stats.noDifference
    }

    private fun factoryKey(
        environment: AcousticEnvironment
    ) = longPreferencesKey(
        "${environment.prefix()}_factory"
    )

    private fun candidateKey(
        environment: AcousticEnvironment
    ) = longPreferencesKey(
        "${environment.prefix()}_candidate"
    )

    private fun noDifferenceKey(
        environment: AcousticEnvironment
    ) = longPreferencesKey(
        "${environment.prefix()}_no_difference"
    )

    private fun AcousticEnvironment.prefix(): String =
        "ab_${name.lowercase()}"

    companion object {
        private const val STORE_NAME =
            "adaptive_ab_evaluation.preferences_pb"
    }
}
