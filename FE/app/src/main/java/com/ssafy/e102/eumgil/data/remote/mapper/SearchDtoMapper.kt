package com.ssafy.e102.eumgil.data.remote.mapper

import com.ssafy.e102.eumgil.core.model.PlaceCategory
import com.ssafy.e102.eumgil.core.model.SearchPage
import com.ssafy.e102.eumgil.core.model.SearchResult
import com.ssafy.e102.eumgil.core.model.SearchVoiceAnalysis
import com.ssafy.e102.eumgil.core.model.SearchVoiceIntent
import com.ssafy.e102.eumgil.data.remote.dto.PlaceAccessibilityFeatureDto
import com.ssafy.e102.eumgil.data.remote.dto.PlacePointDto
import com.ssafy.e102.eumgil.data.remote.dto.PlacesSearchDto
import com.ssafy.e102.eumgil.data.remote.dto.SearchPlaceDto
import com.ssafy.e102.eumgil.data.remote.dto.VoiceSearchAnalysisDto
import org.json.JSONArray
import org.json.JSONObject

internal object SearchDtoMapper {
    fun parsePlacesSearchDto(body: String): PlacesSearchDto {
        val responseJson = JSONObject(body)
        val dataJson = responseJson.optJSONObject("data") ?: error("places search response missing data object")
        val placesJson = dataJson.optJSONArray("places") ?: JSONArray()

        return PlacesSearchDto(
            places =
                List(placesJson.length()) { index ->
                    placesJson.getJSONObject(index).toSearchPlaceDto()
                },
            nextCursor = dataJson.optNullableString("nextCursor"),
            size = dataJson.optInt("size", placesJson.length()),
            totalElements = dataJson.optLong("totalElements", placesJson.length().toLong()),
            hasNext = dataJson.optBoolean("hasNext"),
        )
    }

    fun toSearchResults(dto: PlacesSearchDto): List<SearchResult> =
        toSearchPage(dto).results

    fun toSearchPage(dto: PlacesSearchDto): SearchPage =
        SearchPage(
            results =
                dto.places.map { placeDto ->
                    val serverPlaceId = placeDto.placeId?.toString()
                    val providerPlaceId = placeDto.providerPlaceId?.takeIf { providerPlaceId -> providerPlaceId.isNotBlank() }
                    val isVerifiedPlace = placeDto.matched && !serverPlaceId.isNullOrBlank()
                    val displayTitle = placeDto.name.ifBlank { placeDto.address.orEmpty() }
                    val accessibilityTagKeys =
                        PlaceApiFieldMapper.toAccessibilityTagKeys(
                            PlaceApiFieldMapper.toPlaceFeatureAvailabilities(placeDto.accessibilityFeatures),
                        )
                    SearchResult(
                        placeId =
                            serverPlaceId
                                ?: synthesizeExternalPlaceId(
                                    provider = placeDto.provider,
                                    providerPlaceId = providerPlaceId,
                                    name = displayTitle,
                                    point = placeDto.point,
                                ),
                        title = displayTitle,
                        subtitle = placeDto.address.orEmpty(),
                        latitude = placeDto.point.lat,
                        longitude = placeDto.point.lng,
                        category = PlaceApiFieldMapper.toPlaceCategoryOrNull(placeDto.category).takeIf { isVerifiedPlace },
                        serverPlaceId = serverPlaceId,
                        provider = placeDto.provider.takeIf { provider -> provider.isNotBlank() },
                        providerPlaceId = providerPlaceId,
                        accessibilityTagKeys = accessibilityTagKeys.takeIf { isVerifiedPlace }.orEmpty(),
                        matched = isVerifiedPlace,
                        distanceMeters = placeDto.distanceMeter?.takeIf { distanceMeters -> distanceMeters >= 0 },
                    )
                },
            nextCursor = dto.nextCursor,
            hasNext = dto.hasNext,
            size = dto.size,
            totalElements = dto.totalElements,
        )

    fun parseVoiceSearchAnalysisDto(body: String): VoiceSearchAnalysisDto {
        val responseJson = JSONObject(body)
        val dataJson = responseJson.optJSONObject("data") ?: error("voice analysis response missing data object")

        return VoiceSearchAnalysisDto(
            intent = dataJson.optString("intent"),
            placeName = dataJson.optNullableString("placeName"),
            confirmed = dataJson.optNullableBoolean("confirmed"),
            confirmationMessage = dataJson.optNullableString("confirmationMessage"),
        )
    }

    fun toSearchVoiceAnalysis(dto: VoiceSearchAnalysisDto): SearchVoiceAnalysis =
        SearchVoiceAnalysis(
            intent = dto.intent.toSearchVoiceIntent(),
            placeName = dto.placeName?.takeIf { placeName -> placeName.isNotBlank() },
            confirmed = dto.confirmed,
            confirmationMessage = dto.confirmationMessage?.takeIf { message -> message.isNotBlank() },
        )

    private fun JSONObject.toSearchPlaceDto(): SearchPlaceDto {
        val pointJson = optJSONObject("point") ?: error("places search response missing point object")

        return SearchPlaceDto(
            placeId = optNullableLong("placeId"),
            provider = optString("provider"),
            providerPlaceId = optNullableString("providerPlaceId"),
            name = optString("name"),
            category = optNullableString("category"),
            address = optNullableString("address"),
            distanceMeter = optNullableInt("distanceMeter"),
            point =
                PlacePointDto(
                    lat = pointJson.optDouble("lat"),
                    lng = pointJson.optDouble("lng"),
                ),
            accessibilityFeatures =
                optJSONArray("accessibilityFeatures")
                    ?.let(::toAccessibilityFeatureDtos)
                    .orEmpty(),
            matched = optBoolean("matched"),
        )
    }

    private fun toAccessibilityFeatureDtos(featuresJson: JSONArray): List<PlaceAccessibilityFeatureDto> =
        List(featuresJson.length()) { index ->
            featuresJson.getJSONObject(index).let { featureJson ->
                PlaceAccessibilityFeatureDto(
                    featureType = featureJson.optString("featureType"),
                    isAvailable = featureJson.optBoolean("isAvailable"),
                )
            }
        }

    private fun synthesizeExternalPlaceId(
        provider: String,
        providerPlaceId: String?,
        name: String,
        point: PlacePointDto,
    ): String =
        if (!providerPlaceId.isNullOrBlank()) {
            "provider:${provider.trim().lowercase()}:$providerPlaceId"
        } else {
            listOf(
                "external",
                provider.trim().lowercase().ifBlank { "unknown" },
                name.trim(),
                point.lat.toString(),
                point.lng.toString(),
            ).joinToString(separator = ":")
        }

    private fun String.toSearchVoiceIntent(): SearchVoiceIntent =
        when (trim().uppercase()) {
            "PLACE_SEARCH" -> SearchVoiceIntent.PLACE_SEARCH
            else -> SearchVoiceIntent.UNKNOWN
        }

    private fun JSONObject.optNullableBoolean(name: String): Boolean? =
        if (isNull(name)) {
            null
        } else {
            optBoolean(name)
        }

    private fun JSONObject.optNullableInt(name: String): Int? =
        if (isNull(name)) {
            null
        } else {
            optInt(name)
        }

    private fun JSONObject.optNullableLong(name: String): Long? =
        if (isNull(name)) {
            null
        } else {
            optLong(name)
        }

    private fun JSONObject.optNullableString(name: String): String? =
        if (isNull(name)) {
            null
        } else {
            optString(name).takeIf { value -> value.isNotBlank() }
        }
}
