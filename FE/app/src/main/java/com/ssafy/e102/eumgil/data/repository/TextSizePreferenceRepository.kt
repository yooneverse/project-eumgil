package com.ssafy.e102.eumgil.data.repository

import com.ssafy.e102.eumgil.core.model.TextSizePreference
import com.ssafy.e102.eumgil.data.local.dao.AppSettingDao
import com.ssafy.e102.eumgil.data.local.entity.AppSettingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface TextSizePreferenceRepository {
    fun observeTextSizePreference(): Flow<TextSizePreference>

    suspend fun getTextSizePreference(): TextSizePreference

    suspend fun saveTextSizePreference(preference: TextSizePreference)
}

class DefaultTextSizePreferenceRepository(
    private val appSettingDao: AppSettingDao,
) : TextSizePreferenceRepository {
    override fun observeTextSizePreference(): Flow<TextSizePreference> =
        appSettingDao
            .observeSetting(TEXT_SIZE_PREFERENCE_SETTING_KEY)
            .map { setting -> setting.toTextSizePreference() }

    override suspend fun getTextSizePreference(): TextSizePreference =
        appSettingDao
            .getSetting(TEXT_SIZE_PREFERENCE_SETTING_KEY)
            .toTextSizePreference()

    override suspend fun saveTextSizePreference(preference: TextSizePreference) {
        appSettingDao.upsertSetting(
            AppSettingEntity(
                settingKey = TEXT_SIZE_PREFERENCE_SETTING_KEY,
                stringValue = preference.name,
            ),
        )
    }
}

private fun AppSettingEntity?.toTextSizePreference(): TextSizePreference =
    TextSizePreference.fromStoredValue(this?.stringValue)

const val TEXT_SIZE_PREFERENCE_SETTING_KEY: String = "text_size_preference"
