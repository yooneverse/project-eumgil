package com.ssafy.e102.eumgil.data.repository

import com.ssafy.e102.eumgil.core.model.GeoCoordinate

data class ApprovedHazardMarker(
    val reportId: Long,
    val reportType: String,
    val coordinate: GeoCoordinate,
    val description: String? = null,
    val thumbnailUrls: List<String> = emptyList(),
    val imageUrls: List<String>,
)

data class ApprovedHazardMarkerBounds(
    val swLat: Double,
    val swLng: Double,
    val neLat: Double,
    val neLng: Double,
)
