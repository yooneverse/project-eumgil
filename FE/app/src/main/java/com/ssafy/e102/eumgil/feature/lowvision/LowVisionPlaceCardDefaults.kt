package com.ssafy.e102.eumgil.feature.lowvision

import androidx.annotation.DrawableRes
import com.ssafy.e102.eumgil.R
import java.util.Locale

internal object LowVisionPlaceCardDefaults {
    @DrawableRes
    val saveIconRes: Int = R.drawable.ic_nav_bookmark_selected

    @DrawableRes
    val routeIconRes: Int = R.drawable.ic_route_start_navigation

    val actionOrder: List<LowVisionPlaceCardAction> = listOf(
        LowVisionPlaceCardAction.Navigate,
        LowVisionPlaceCardAction.Bookmark,
    )
}

internal enum class LowVisionPlaceCardAction {
    Navigate,
    Bookmark,
}

internal fun lowVisionBriefAddress(address: String?): String {
    val normalized = address?.trim().orEmpty()
    if (normalized.isEmpty()) return "GPS 기반 위치"

    val segments = normalized.split(Regex("\\s+"))
    return if (segments.size <= 3) {
        normalized
    } else {
        segments.takeLast(3).joinToString(separator = " ")
    }
}

internal fun lowVisionDetailAddress(
    address: String?,
    latitude: Double,
    longitude: Double,
): String =
    "상세 주소: ${address?.trim()?.takeIf { it.isNotEmpty() } ?: "GPS 기반 위치"}\n" +
        "GPS 위치: 위도 ${latitude.toLowVisionCoordinateText()}, 경도 ${longitude.toLowVisionCoordinateText()}"

internal fun lowVisionPlaceInfoA11yLabel(
    name: String,
    address: String?,
): String =
    "${name.trim()}. ${lowVisionBriefAddress(address)}. 탭하면 상세 주소를 음성으로 안내합니다."

internal fun lowVisionPlaceInfoSpeechText(
    name: String,
    address: String?,
    latitude: Double,
    longitude: Double,
): String =
    "${name.trim()}. " +
        lowVisionDetailAddress(
            address = address,
            latitude = latitude,
            longitude = longitude,
        )

private fun Double.toLowVisionCoordinateText(): String =
    String.format(Locale.US, "%.5f", this)
