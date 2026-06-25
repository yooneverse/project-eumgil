package com.ssafy.e102.eumgil.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "favoriteRoute",
    primaryKeys = ["accountScopeKey", "favoriteRouteId"],
    indices = [Index(value = ["accountScopeKey", "updatedAt"])],
)
data class FavoriteRouteEntity(
    val accountScopeKey: String,
    val favoriteRouteId: Long,
    val routeName: String,
    val originName: String,
    val originPlaceId: String? = null,
    val originLatitude: Double,
    val originLongitude: Double,
    val destinationName: String,
    val destinationPlaceId: String? = null,
    val destinationLatitude: Double,
    val destinationLongitude: Double,
    val transportMode: String? = null,
    val routeOption: String? = null,
    val summaryDistanceMeters: Int? = null,
    val summaryDurationSeconds: Int? = null,
    val routeSnapshotJson: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)
