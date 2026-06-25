package com.ssafy.e102.eumgil.core.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val ANDROID_GEOCODER_PROVIDER: String = "ANDROID_GEOCODER"

data class AddressSearchCandidate(
    val title: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
)

fun interface AddressSearchResolver {
    suspend fun resolve(query: String, limit: Int): List<AddressSearchCandidate>
}

object NoOpAddressSearchResolver : AddressSearchResolver {
    override suspend fun resolve(query: String, limit: Int): List<AddressSearchCandidate> = emptyList()
}

class AndroidAddressSearchResolver(
    context: Context,
    private val locale: Locale = Locale.KOREA,
) : AddressSearchResolver {
    private val appContext = context.applicationContext

    @Suppress("DEPRECATION")
    override suspend fun resolve(query: String, limit: Int): List<AddressSearchCandidate> =
        withContext(Dispatchers.IO) {
            val normalizedQuery = query.trim()
            if (normalizedQuery.isEmpty() || limit <= 0 || !Geocoder.isPresent()) {
                return@withContext emptyList()
            }

            runCatching {
                Geocoder(appContext, locale)
                    .getFromLocationName(normalizedQuery, limit)
                    .orEmpty()
                    .mapNotNull(Address::toAddressSearchCandidate)
                    .distinctBy { candidate ->
                        listOf(candidate.title, candidate.address, candidate.latitude, candidate.longitude)
                    }
            }.getOrDefault(emptyList())
        }
}

private fun Address.toAddressSearchCandidate(): AddressSearchCandidate? {
    if (!hasLatitude() || !hasLongitude()) return null

    val displayAddress = toDisplayAddressLabel() ?: return null
    val title = toAddressSearchTitle() ?: displayAddress

    return AddressSearchCandidate(
        title = title,
        address = displayAddress,
        latitude = latitude,
        longitude = longitude,
    )
}

private fun Address.toAddressSearchTitle(): String? =
    listOfNotNull(
        thoroughfare,
        subThoroughfare,
        premises,
        featureName,
    ).map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .joinToString(separator = " ")
        .takeIf(String::isNotEmpty)

private fun Address.toDisplayAddressLabel(): String? =
    getAddressLine(0)
        ?.trim()
        ?.removePrefix("\uB300\uD55C\uBBFC\uAD6D ")
        ?.takeIf(String::isNotEmpty)
        ?: listOfNotNull(
            adminArea,
            locality,
            subLocality,
            thoroughfare,
            subThoroughfare,
            premises,
            featureName,
        ).map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .joinToString(separator = " ")
            .takeIf(String::isNotEmpty)
