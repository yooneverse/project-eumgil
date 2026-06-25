package com.ssafy.e102.eumgil.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bookmark",
    indices = [
        Index(value = ["accountScopeKey", "placeId"], unique = true),
        Index(value = ["accountScopeKey", "bookmarkTargetId"], unique = true),
        Index(value = ["accountScopeKey", "updatedAt"]),
    ],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val bookmarkId: Long = 0L,
    val accountScopeKey: String,
    val placeId: String,
    val serverBookmarkId: Long? = null,
    val bookmarkTargetId: String? = null,
    val targetType: String? = null,
    val serverPlaceId: Long? = null,
    val provider: String? = null,
    val providerPlaceId: String? = null,
    val providerCategory: String? = null,
    val placeName: String,
    val address: String? = null,
    val latitude: Double,
    val longitude: Double,
    val category: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)
