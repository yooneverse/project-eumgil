package com.ssafy.e102.eumgil.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

private const val AUTH_SESSION_DATASTORE_NAME: String = "auth_session"

val Context.authSessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = AUTH_SESSION_DATASTORE_NAME,
)

object AuthSessionPreferences {
    val accessToken = stringPreferencesKey("access_token")
    val refreshToken = stringPreferencesKey("refresh_token")
    val isProfileCompleted = booleanPreferencesKey("profile_completed")
}
