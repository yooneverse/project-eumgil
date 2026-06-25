package com.ssafy.e102.eumgil.data.local.datasource

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.ssafy.e102.eumgil.core.model.InitSettings
import com.ssafy.e102.eumgil.data.local.datastore.InitSettingsPreferences
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class InitSettingsLocalDataSource(
    private val dataStore: DataStore<Preferences>,
) {
    fun observeInitSettings(scopeKey: String): Flow<InitSettings> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }.map { preferences ->
                val selectedPrimaryUserTypeKey = InitSettingsPreferences.selectedPrimaryUserType(scopeKey)
                val selectedMobilitySubtypeKey = InitSettingsPreferences.selectedMobilitySubtype(scopeKey)
                val lowVisionFollowUpCompletedKey =
                    InitSettingsPreferences.isLowVisionFollowUpCompleted(scopeKey)
                val locationTermsAgreedKey = InitSettingsPreferences.isLocationTermsAgreed(scopeKey)
                val privacyPolicyAgreedKey = InitSettingsPreferences.isPrivacyPolicyAgreed(scopeKey)
                InitSettings(
                    selectedPrimaryUserType = preferences[selectedPrimaryUserTypeKey],
                    selectedMobilitySubtype = preferences[selectedMobilitySubtypeKey],
                    isLowVisionFollowUpCompleted = preferences[lowVisionFollowUpCompletedKey] ?: false,
                    isLocationTermsAgreed = preferences[locationTermsAgreedKey] ?: false,
                    isPrivacyPolicyAgreed = preferences[privacyPolicyAgreedKey] ?: false,
                )
            }

    suspend fun getInitSettings(scopeKey: String): InitSettings = observeInitSettings(scopeKey).first()

    suspend fun savePrimaryUserType(
        scopeKey: String,
        selectedPrimaryUserType: String,
    ) {
        dataStore.edit { preferences ->
            val selectedPrimaryUserTypeKey = InitSettingsPreferences.selectedPrimaryUserType(scopeKey)
            val selectedMobilitySubtypeKey = InitSettingsPreferences.selectedMobilitySubtype(scopeKey)
            val lowVisionFollowUpCompletedKey =
                InitSettingsPreferences.isLowVisionFollowUpCompleted(scopeKey)
            val currentType = preferences[selectedPrimaryUserTypeKey]

            preferences[selectedPrimaryUserTypeKey] = selectedPrimaryUserType
            if (currentType != selectedPrimaryUserType || selectedPrimaryUserType == LOW_VISION_ROUTE_VALUE) {
                preferences.remove(selectedMobilitySubtypeKey)
            }
            if (selectedPrimaryUserType != LOW_VISION_ROUTE_VALUE || currentType != selectedPrimaryUserType) {
                preferences.remove(lowVisionFollowUpCompletedKey)
            }
        }
    }

    suspend fun saveMobilitySubtype(
        scopeKey: String,
        selectedMobilitySubtype: String,
    ) {
        dataStore.edit { preferences ->
            preferences[InitSettingsPreferences.selectedMobilitySubtype(scopeKey)] = selectedMobilitySubtype
        }
    }

    suspend fun saveLowVisionFollowUpCompleted(
        scopeKey: String,
        isCompleted: Boolean,
    ) {
        dataStore.edit { preferences ->
            preferences[InitSettingsPreferences.isLowVisionFollowUpCompleted(scopeKey)] = isCompleted
        }
    }

    suspend fun saveLocationTermsAgreement(
        scopeKey: String,
        isLocationTermsAgreed: Boolean,
        isPrivacyPolicyAgreed: Boolean,
    ) {
        dataStore.edit { preferences ->
            preferences[InitSettingsPreferences.isLocationTermsAgreed(scopeKey)] = isLocationTermsAgreed
            preferences[InitSettingsPreferences.isPrivacyPolicyAgreed(scopeKey)] = isPrivacyPolicyAgreed
        }
    }

    suspend fun clearInitSettings(scopeKey: String) {
        dataStore.edit { preferences ->
            preferences.remove(InitSettingsPreferences.selectedPrimaryUserType(scopeKey))
            preferences.remove(InitSettingsPreferences.selectedMobilitySubtype(scopeKey))
            preferences.remove(InitSettingsPreferences.isLowVisionFollowUpCompleted(scopeKey))
            preferences.remove(InitSettingsPreferences.isLocationTermsAgreed(scopeKey))
            preferences.remove(InitSettingsPreferences.isPrivacyPolicyAgreed(scopeKey))
        }
    }

    private companion object {
        private const val LOW_VISION_ROUTE_VALUE = "low_vision"
    }
}
