package com.ssafy.e102.eumgil.data.repository

import com.ssafy.e102.eumgil.core.model.TextSizePreference
import com.ssafy.e102.eumgil.data.local.dao.AppSettingDao
import com.ssafy.e102.eumgil.data.local.entity.AppSettingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TextSizePreferenceRepositoryTest {
    @Test
    fun `repository falls back to default when stored value is missing`() =
        runTest {
            val repository = DefaultTextSizePreferenceRepository(FakeAppSettingDao())

            assertEquals(TextSizePreference.DEFAULT, repository.getTextSizePreference())
            assertEquals(TextSizePreference.DEFAULT, repository.observeTextSizePreference().first())
        }

    @Test
    fun `repository falls back to default when stored value is invalid`() =
        runTest {
            val repository =
                DefaultTextSizePreferenceRepository(
                    FakeAppSettingDao(
                        initialSettings =
                            listOf(
                                AppSettingEntity(
                                    settingKey = TEXT_SIZE_PREFERENCE_SETTING_KEY,
                                    stringValue = "HUGE",
                                ),
                            ),
                    ),
                )

            assertEquals(TextSizePreference.DEFAULT, repository.getTextSizePreference())
            assertEquals(TextSizePreference.DEFAULT, repository.observeTextSizePreference().first())
        }

    @Test
    fun `repository stores selected preference in app setting string value`() =
        runTest {
            val dao = FakeAppSettingDao()
            val repository = DefaultTextSizePreferenceRepository(dao)

            repository.saveTextSizePreference(TextSizePreference.EXTRA_LARGE)

            assertEquals(TextSizePreference.EXTRA_LARGE, repository.getTextSizePreference())
            assertEquals(
                "EXTRA_LARGE",
                dao.getSetting(TEXT_SIZE_PREFERENCE_SETTING_KEY)?.stringValue,
            )
        }

    @Test
    fun `repository observer emits saved preference updates`() =
        runTest {
            val dao = FakeAppSettingDao()
            val repository = DefaultTextSizePreferenceRepository(dao)

            repository.saveTextSizePreference(TextSizePreference.LARGE)

            assertEquals(TextSizePreference.LARGE, repository.observeTextSizePreference().first())
        }
}

private class FakeAppSettingDao(
    initialSettings: List<AppSettingEntity> = emptyList(),
) : AppSettingDao {
    private val settingsByKey =
        MutableStateFlow(initialSettings.associateBy { setting -> setting.settingKey })

    override fun observeSettings(): Flow<List<AppSettingEntity>> =
        settingsByKey.map { settings -> settings.values.sortedBy(AppSettingEntity::settingKey) }

    override fun observeSetting(settingKey: String): Flow<AppSettingEntity?> =
        settingsByKey.map { settings -> settings[settingKey] }

    override suspend fun getSetting(settingKey: String): AppSettingEntity? =
        settingsByKey.value[settingKey]

    override suspend fun upsertSetting(setting: AppSettingEntity) {
        settingsByKey.value = settingsByKey.value + (setting.settingKey to setting)
    }

    override suspend fun upsertSettings(settings: List<AppSettingEntity>) {
        settingsByKey.value =
            settingsByKey.value + settings.associateBy { setting -> setting.settingKey }
    }

    override suspend fun deleteSetting(settingKey: String) {
        settingsByKey.value = settingsByKey.value - settingKey
    }

    override suspend fun clearSettings() {
        settingsByKey.value = emptyMap()
    }
}
