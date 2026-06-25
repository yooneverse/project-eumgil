package com.ssafy.e102.eumgil.core.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun interface CurrentLocationAddressResolver {
    suspend fun resolveAddress(coordinate: GeoCoordinate): String?
}

object NoOpCurrentLocationAddressResolver : CurrentLocationAddressResolver {
    override suspend fun resolveAddress(coordinate: GeoCoordinate): String? = null
}

class AndroidCurrentLocationAddressResolver(
    context: Context,
    private val locale: Locale = Locale.KOREA,
) : CurrentLocationAddressResolver {
    private val appContext = context.applicationContext

    @Suppress("DEPRECATION")
    override suspend fun resolveAddress(coordinate: GeoCoordinate): String? =
        withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext null

            runCatching {
                Geocoder(appContext, locale)
                    .getFromLocation(coordinate.latitude, coordinate.longitude, 1)
                    ?.firstOrNull()
                    ?.toLowVisionAddressLabel()
            }.getOrNull()
        }
}

private fun Address.toLowVisionAddressLabel(): String? =
    lowVisionCurrentLocationAddressLabel(
        addressLine = getAddressLine(0),
        adminArea = adminArea,
        locality = locality,
        subLocality = subLocality,
        thoroughfare = thoroughfare,
        subThoroughfare = subThoroughfare,
        premises = premises,
        featureName = featureName,
    )

internal fun lowVisionCurrentLocationAddressLabel(
    addressLine: String?,
    adminArea: String?,
    locality: String?,
    subLocality: String?,
    thoroughfare: String?,
    subThoroughfare: String?,
    premises: String?,
    featureName: String?,
): String? {
    val roadAddress =
        listOfNotNull(
            adminArea,
            locality,
            subLocality,
            thoroughfare,
            subThoroughfare,
            premises,
        ).map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .joinToString(" ")
            .takeIf(String::isNotEmpty)

    return roadAddress
        ?: addressLine
            ?.trim()
            ?.removePrefix("\uB300\uD55C\uBBFC\uAD6D ")
            ?.takeIf(String::isNotEmpty)
        ?: featureName?.trim()?.takeIf(String::isNotEmpty)
}
