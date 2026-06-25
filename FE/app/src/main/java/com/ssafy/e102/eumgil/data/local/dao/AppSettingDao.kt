package com.ssafy.e102.eumgil.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ssafy.e102.eumgil.data.local.entity.AppSettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppSettingDao {
    @Query("SELECT * FROM appSetting ORDER BY settingKey ASC")
    fun observeSettings(): Flow<List<AppSettingEntity>>

    @Query("SELECT * FROM appSetting WHERE settingKey = :settingKey LIMIT 1")
    fun observeSetting(settingKey: String): Flow<AppSettingEntity?>

    @Query("SELECT * FROM appSetting WHERE settingKey = :settingKey LIMIT 1")
    suspend fun getSetting(settingKey: String): AppSettingEntity?

    @Upsert
    suspend fun upsertSetting(setting: AppSettingEntity)

    @Upsert
    suspend fun upsertSettings(settings: List<AppSettingEntity>)

    @Query("DELETE FROM appSetting WHERE settingKey = :settingKey")
    suspend fun deleteSetting(settingKey: String)

    @Query("DELETE FROM appSetting")
    suspend fun clearSettings()
}
