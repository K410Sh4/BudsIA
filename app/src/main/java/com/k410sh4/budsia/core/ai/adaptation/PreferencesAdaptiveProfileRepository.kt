package com.k410sh4.budsia.core.ai.adaptation

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class PreferencesAdaptiveProfileRepository(
    context: Context,
    applicationScope: CoroutineScope,
    private val tuner: AdaptiveTuningEngine
) : AdaptiveProfileRepository {

    private val store: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = applicationScope,
            produceFile = {
                context.preferencesDataStoreFile(STORE_NAME)
            }
        )

    private val _activeProfile = MutableStateFlow(
        AdaptiveAudioProfile.factory(
            AcousticEnvironment.GENERAL
        )
    )
    override val activeProfile: StateFlow<AdaptiveAudioProfile> =
        _activeProfile.asStateFlow()

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
                    val environment = readEnvironment(preferences)
                    _activeProfile.value = readProfile(
                        preferences = preferences,
                        environment = environment
                    )
                }
        }
    }

    override suspend fun selectEnvironment(
        environment: AcousticEnvironment
    ) {
        store.edit { preferences ->
            preferences[ACTIVE_ENVIRONMENT] = environment.name
        }
    }

    override suspend fun applyFeedback(
        feedback: AudioFeedback
    ): AdaptiveAudioProfile {
        var updated = _activeProfile.value

        store.edit { preferences ->
            val environment = readEnvironment(preferences)
            val current = readProfile(preferences, environment)
            updated = tuner.applyFeedback(current, feedback)
            writeProfile(preferences, updated)
        }

        _activeProfile.value = updated
        return updated
    }

    override suspend fun setPreferredEnhancementStrength(
        value: Float
    ): AdaptiveAudioProfile {
        var updated = _activeProfile.value

        store.edit { preferences ->
            val environment = readEnvironment(preferences)
            val current = readProfile(preferences, environment)
            updated = current.copy(
                revision = current.revision + 1L,
                preferredEnhancementStrength = value.coerceIn(
                    AdaptiveAudioProfile.MIN_PREFERRED_STRENGTH,
                    AdaptiveAudioProfile.MAX_PREFERRED_STRENGTH
                )
            )
            writeProfile(preferences, updated)
        }

        _activeProfile.value = updated
        return updated
    }

    override suspend fun resetActiveProfile(): AdaptiveAudioProfile {
        var updated = _activeProfile.value

        store.edit { preferences ->
            val environment = readEnvironment(preferences)
            val current = readProfile(preferences, environment)
            updated = tuner.reset(environment).copy(
                revision = current.revision + 1L
            )
            writeProfile(preferences, updated)
        }

        _activeProfile.value = updated
        return updated
    }

    private fun readEnvironment(
        preferences: Preferences
    ): AcousticEnvironment {
        val stored = preferences[ACTIVE_ENVIRONMENT]
        return runCatching {
            stored?.let(AcousticEnvironment::valueOf)
        }.getOrNull() ?: AcousticEnvironment.GENERAL
    }

    private fun readProfile(
        preferences: Preferences,
        environment: AcousticEnvironment
    ): AdaptiveAudioProfile {
        val schema = preferences[schemaKey(environment)]
            ?: AdaptiveAudioProfile.CURRENT_SCHEMA_VERSION

        if (schema != AdaptiveAudioProfile.CURRENT_SCHEMA_VERSION) {
            return AdaptiveAudioProfile.factory(environment)
        }

        val strength = (
            preferences[strengthKey(environment)]
                ?: AdaptiveAudioProfile.DEFAULT_PREFERRED_STRENGTH
            ).coerceIn(
                AdaptiveAudioProfile.MIN_PREFERRED_STRENGTH,
                AdaptiveAudioProfile.MAX_PREFERRED_STRENGTH
            )

        val feedbackCount =
            (preferences[feedbackCountKey(environment)] ?: 0L)
                .coerceAtLeast(0L)

        val positiveCount =
            (preferences[positiveCountKey(environment)] ?: 0L)
                .coerceIn(0L, feedbackCount)

        return AdaptiveAudioProfile(
            schemaVersion = schema,
            environment = environment,
            revision = (
                preferences[revisionKey(environment)] ?: 0L
                ).coerceAtLeast(0L),
            preferredEnhancementStrength = strength,
            feedbackCount = feedbackCount,
            positiveFeedbackCount = positiveCount
        )
    }

    private fun writeProfile(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        profile: AdaptiveAudioProfile
    ) {
        preferences[schemaKey(profile.environment)] =
            profile.schemaVersion
        preferences[strengthKey(profile.environment)] =
            profile.preferredEnhancementStrength
        preferences[revisionKey(profile.environment)] =
            profile.revision
        preferences[feedbackCountKey(profile.environment)] =
            profile.feedbackCount
        preferences[positiveCountKey(profile.environment)] =
            profile.positiveFeedbackCount
    }

    private fun schemaKey(environment: AcousticEnvironment) =
        intPreferencesKey(
            "${environment.keyPrefix()}_schema"
        )

    private fun strengthKey(environment: AcousticEnvironment) =
        floatPreferencesKey(
            "${environment.keyPrefix()}_preferred_strength"
        )

    private fun revisionKey(environment: AcousticEnvironment) =
        longPreferencesKey(
            "${environment.keyPrefix()}_revision"
        )

    private fun feedbackCountKey(environment: AcousticEnvironment) =
        longPreferencesKey(
            "${environment.keyPrefix()}_feedback_count"
        )

    private fun positiveCountKey(environment: AcousticEnvironment) =
        longPreferencesKey(
            "${environment.keyPrefix()}_positive_count"
        )

    private fun AcousticEnvironment.keyPrefix(): String =
        "profile_${name.lowercase()}"

    companion object {
        private const val STORE_NAME =
            "adaptive_audio_profiles.preferences_pb"

        private val ACTIVE_ENVIRONMENT =
            stringPreferencesKey("active_environment")
    }
}
