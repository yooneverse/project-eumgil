package com.ssafy.e102.eumgil.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

private const val INIT_SETTINGS_DATASTORE_NAME: String = "init_settings"

val Context.initSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = INIT_SETTINGS_DATASTORE_NAME,
)

object InitSettingsPreferences {
    fun selectedPrimaryUserType(scopeKey: String) =
        stringPreferencesKey("${scopeKey}:selected_primary_user_type")

    fun selectedMobilitySubtype(scopeKey: String) =
        stringPreferencesKey("${scopeKey}:selected_mobility_subtype")

    fun isLowVisionFollowUpCompleted(scopeKey: String) =
        booleanPreferencesKey("${scopeKey}:low_vision_follow_up_completed")

    fun isLocationTermsAgreed(scopeKey: String) =
        booleanPreferencesKey("${scopeKey}:location_terms_agreed")

    fun isPrivacyPolicyAgreed(scopeKey: String) =
        booleanPreferencesKey("${scopeKey}:privacy_policy_agreed")
}
