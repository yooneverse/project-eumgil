package com.ssafy.e102.eumgil.feature.lowvision

import com.ssafy.e102.eumgil.core.location.LocationSnapshot
import com.ssafy.e102.eumgil.core.model.GeoCoordinate

data class LowVisionCurrentLocationDisplay(
    val title: String,
    val supportingText: String,
    val talkBackText: String,
)

internal fun lowVisionCurrentLocationDisplay(snapshot: LocationSnapshot?): LowVisionCurrentLocationDisplay =
    lowVisionCurrentLocationDisplay(
        snapshot = snapshot,
        address = null,
    )

internal fun lowVisionCurrentLocationDisplay(
    snapshot: LocationSnapshot?,
    address: String?,
): LowVisionCurrentLocationDisplay =
    lowVisionCurrentLocationDisplay(
        latitude = snapshot?.latitude,
        longitude = snapshot?.longitude,
        address = address,
    )

internal fun lowVisionCurrentLocationDisplay(coordinate: GeoCoordinate?): LowVisionCurrentLocationDisplay =
    lowVisionCurrentLocationDisplay(
        coordinate = coordinate,
        address = null,
    )

internal fun lowVisionCurrentLocationDisplay(
    coordinate: GeoCoordinate?,
    address: String?,
): LowVisionCurrentLocationDisplay =
    lowVisionCurrentLocationDisplay(
        latitude = coordinate?.latitude,
        longitude = coordinate?.longitude,
        address = address,
    )

@Suppress("UNUSED_PARAMETER")
internal fun lowVisionCurrentLocationDisplay(
    latitude: Double?,
    longitude: Double?,
    address: String? = null,
): LowVisionCurrentLocationDisplay {
    val addressText = address?.trim().orEmpty()
    return LowVisionCurrentLocationDisplay(
        title = LOW_VISION_CURRENT_LOCATION_TITLE,
        supportingText = "",
        talkBackText =
            if (addressText.isNotEmpty()) {
                "$LOW_VISION_CURRENT_LOCATION_TITLE $addressText"
            } else {
                LOW_VISION_CURRENT_LOCATION_TITLE
            },
    )
}

private const val LOW_VISION_CURRENT_LOCATION_TITLE = "\uD604\uC7AC \uC704\uCE58"
