package com.ssafy.e102.eumgil.feature.map.model

import com.ssafy.e102.eumgil.core.location.LocationSnapshot

data class MapCoordinate(
    val latitude: Double,
    val longitude: Double,
)

enum class MapCameraSource {
    DEFAULT_BUSAN,
    CURRENT_LOCATION,
    SEARCH_RESULT,
}

data class MapCameraTarget(
    val center: MapCoordinate,
    val source: MapCameraSource,
    val requestId: Long = 0L,
    val zoomLevel: Int? = null,
    val bearingDegrees: Double? = null,
    val shouldAnimateTransition: Boolean = true,
) {
    companion object {
        val DefaultBusan =
            MapCameraTarget(
                center = MapDefaults.BUSAN_CENTER,
                source = MapCameraSource.DEFAULT_BUSAN,
            )
    }
}

object MapDefaults {
    val BUSAN_CENTER = MapCoordinate(latitude = 35.1796, longitude = 129.0756)
}

fun MapCameraSource.defaultZoomLevel(): Int =
    when (this) {
        MapCameraSource.CURRENT_LOCATION -> 17
        MapCameraSource.SEARCH_RESULT -> 16
        MapCameraSource.DEFAULT_BUSAN -> 15
    }

fun MapCameraTarget.resolvedZoomLevel(): Int =
    (zoomLevel ?: source.defaultZoomLevel())
        .coerceIn(KAKAO_MAP_MIN_ZOOM_LEVEL, KAKAO_MAP_MAX_ZOOM_LEVEL)

fun LocationSnapshot.toMapCoordinate(): MapCoordinate =
    MapCoordinate(
        latitude = latitude,
        longitude = longitude,
    )

const val KAKAO_MAP_MIN_ZOOM_LEVEL = 6
const val KAKAO_MAP_MAX_ZOOM_LEVEL = 21
