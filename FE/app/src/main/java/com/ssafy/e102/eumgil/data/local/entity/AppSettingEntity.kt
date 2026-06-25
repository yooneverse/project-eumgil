package com.ssafy.e102.eumgil.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "appSetting")
data class AppSettingEntity(
    @PrimaryKey
    val settingKey: String,
    val stringValue: String? = null,
    val booleanValue: Boolean? = null,
    val longValue: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)
