package com.ssafy.e102.eumgil.data.remote.mapper

import com.ssafy.e102.eumgil.core.model.PlaceCategory
import com.ssafy.e102.eumgil.core.model.PlaceDetail
import com.ssafy.e102.eumgil.core.model.PlaceFeatureAvailability
import com.ssafy.e102.eumgil.core.model.PlaceFeatureType
import com.ssafy.e102.eumgil.core.model.PlaceSummary
import com.ssafy.e102.eumgil.core.model.PlaceTransitArrival
import com.ssafy.e102.eumgil.core.model.MapPlaceDetailType
import com.ssafy.e102.eumgil.core.model.MapTappedPlaceDetail
import com.ssafy.e102.eumgil.data.remote.dto.PlaceAccessibilityFeatureDto
import com.ssafy.e102.eumgil.data.remote.dto.PlaceDetailDto
import com.ssafy.e102.eumgil.data.remote.dto.PlacePointDto
import com.ssafy.e102.eumgil.data.remote.dto.PlaceSummaryDto
import com.ssafy.e102.eumgil.data.remote.dto.PlaceTransitArrivalDto
import com.ssafy.e102.eumgil.data.remote.dto.PlacesBrowseDto
import com.ssafy.e102.eumgil.data.remote.dto.MapPlaceDetailDto
import org.json.JSONArray
import org.json.JSONObject

internal object PlaceDtoMapper {
    fun parsePlacesBrowseDto(body: String): PlacesBrowseDto {
        val responseJson = JSONObject(body)
        val dataJson = responseJson.optJSONObject("data") ?: error("places response missing data object")
        val placesJson = dataJson.optJSONArray("places") ?: JSONArray()

        return PlacesBrowseDto(
            places =
                List(placesJson.length()) { index ->
                    placesJson.getJSONObject(index).toPlaceSummaryDto()
                },
        )
    }

    fun toPlaceSummaries(dto: PlacesBrowseDto): List<PlaceSummary> =
        dto.places.map { placeDto ->
            val features = PlaceApiFieldMapper.toPlaceFeatureAvailabilities(placeDto.accessibilityFeatures)
            PlaceSummary(
                placeId = placeDto.placeId.toString(),
                name = placeDto.name,
                address = placeDto.address.orEmpty(),
                latitude = placeDto.point.lat,
                longitude = placeDto.point.lng,
                category = PlaceApiFieldMapper.toPlaceCategory(placeDto.category),
                markerKind = PlaceApiFieldMapper.toPlaceMarkerKind(placeDto.markerKind),
                features = features,
                isBookmarked = placeDto.isBookmarked,
                accessibilityTags = PlaceApiFieldMapper.toAccessibilityTagKeys(features),
            )
        }

    fun parsePlaceDetailDto(body: String): PlaceDetailDto {
        val responseJson = JSONObject(body)
        val dataJson = responseJson.optJSONObject("data") ?: error("place detail response missing data object")
        return dataJson.toPlaceDetailDto()
    }

    fun parseMapPlaceDetailDto(body: String): MapPlaceDetailDto {
        val responseJson = JSONObject(body)
        val dataJson = responseJson.optJSONObject("data") ?: error("map place detail response missing data object")
        return dataJson.toMapPlaceDetailDto()
    }

    fun toPlaceDetail(dto: PlaceDetailDto): PlaceDetail {
        val features = PlaceApiFieldMapper.toPlaceFeatureAvailabilities(dto.accessibilityFeatures)
        return PlaceDetail(
            placeId = dto.placeId.toString(),
            name = dto.name,
            address = dto.address.orEmpty(),
            latitude = dto.point.lat,
            longitude = dto.point.lng,
            category = PlaceApiFieldMapper.toPlaceCategory(dto.category),
            features = features,
            isBookmarked = dto.isBookmarked,
            accessibilityTags = PlaceApiFieldMapper.toAccessibilityTagKeys(features),
            providerPlaceId = dto.providerPlaceId?.takeIf { providerPlaceId -> providerPlaceId.isNotBlank() },
            phoneNumber = dto.phone?.takeIf { phone -> phone.isNotBlank() },
            description = dto.description?.takeIf { description -> description.isNotBlank() },
        )
    }

    fun toMapTappedPlaceDetail(dto: MapPlaceDetailDto): MapTappedPlaceDetail {
        val features = PlaceApiFieldMapper.toPlaceFeatureAvailabilities(dto.accessibilityFeatures)
        return MapTappedPlaceDetail(
            bookmarkTargetId = dto.bookmarkTargetId,
            detailType = dto.detailType.toMapPlaceDetailType(),
            placeId = dto.placeId?.toString(),
            provider = dto.provider?.takeIf { provider -> provider.isNotBlank() },
            providerPlaceId = dto.providerPlaceId?.takeIf { providerPlaceId -> providerPlaceId.isNotBlank() },
            name = dto.name,
            category = PlaceApiFieldMapper.toPlaceCategoryOrNull(dto.category),
            providerCategory = dto.providerCategory?.takeIf { providerCategory -> providerCategory.isNotBlank() },
            address = dto.address.orEmpty(),
            latitude = dto.point.lat,
            longitude = dto.point.lng,
            features = features,
            isBookmarked = dto.isBookmarked,
            accessibilityTags = PlaceApiFieldMapper.toAccessibilityTagKeys(features),
            phoneNumber = dto.phone?.takeIf { phone -> phone.isNotBlank() },
            transitArrivals = dto.transitArrivals.map { arrival -> arrival.toDomain() },
            description = dto.description?.takeIf { description -> description.isNotBlank() },
        )
    }

    fun PlaceCategory.toServerCategoryApiValueOrNull(): String? =
        when (this) {
            PlaceCategory.TOILET,
            PlaceCategory.ELEVATOR,
            PlaceCategory.CHARGING_STATION,
            PlaceCategory.BRAILLE_BLOCK,
            -> null
            PlaceCategory.RESTAURANT -> "FOOD_CAFE"
            PlaceCategory.TOURIST_ATTRACTION -> "TOURIST_SPOT"
            PlaceCategory.OTHER -> "ETC"
            else -> name
        }

    fun PlaceCategory.toFeatureTypeApiValueOrNull(): String? =
        when (this) {
            PlaceCategory.TOILET -> PlaceFeatureType.ACCESSIBLE_TOILET.toApiValue()
            PlaceCategory.ELEVATOR -> PlaceFeatureType.ELEVATOR.toApiValue()
            PlaceCategory.CHARGING_STATION -> PlaceFeatureType.CHARGING_STATION.toApiValue()
            else -> null
        }

    fun PlaceFeatureType.toApiValue(): String =
        when (this) {
            PlaceFeatureType.ACCESSIBLE_ENTRANCE -> "accessibleEntrance"
            PlaceFeatureType.ELEVATOR -> "elevator"
            PlaceFeatureType.ACCESSIBLE_TOILET -> "accessibleToilet"
            PlaceFeatureType.ACCESSIBLE_PARKING -> "accessibleParking"
            PlaceFeatureType.CHARGING_STATION -> "chargingStation"
            PlaceFeatureType.ACCESSIBLE_ROOM -> "accessibleRoom"
            PlaceFeatureType.GUIDANCE_FACILITY -> "guidanceFacility"
        }

    private fun JSONObject.toPlaceSummaryDto(): PlaceSummaryDto {
        val pointJson = optJSONObject("point") ?: error("place response missing point object")

        return PlaceSummaryDto(
            placeId = optLong("placeId"),
            name = optString("name"),
            category = optString("category"),
            markerKind = optString("markerKind", "DEFAULT"),
            address = optNullableString("address"),
            point =
                PlacePointDto(
                    lat = pointJson.optDouble("lat"),
                    lng = pointJson.optDouble("lng"),
                ),
            accessibilityFeatures =
                optJSONArray("accessibilityFeatures")
                    ?.let(::toAccessibilityFeatureDtos)
                    .orEmpty(),
            isBookmarked = optBoolean("isBookmarked"),
        )
    }

    private fun JSONObject.toPlaceDetailDto(): PlaceDetailDto {
        val pointJson = optJSONObject("point") ?: error("place detail response missing point object")

        return PlaceDetailDto(
            placeId = optLong("placeId"),
            name = optString("name"),
            category = optString("category"),
            address = optNullableString("address"),
            point =
                PlacePointDto(
                    lat = pointJson.optDouble("lat"),
                    lng = pointJson.optDouble("lng"),
                ),
            providerPlaceId = optNullableString("providerPlaceId"),
            accessibilityFeatures =
                optJSONArray("accessibilityFeatures")
                    ?.let(::toAccessibilityFeatureDtos)
                    .orEmpty(),
            isBookmarked = optBoolean("isBookmarked"),
            phone = optNullableString("phone"),
            description = optNullableString("description"),
        )
    }

    private fun JSONObject.toMapPlaceDetailDto(): MapPlaceDetailDto {
        val pointJson = optJSONObject("point") ?: error("map place detail response missing point object")

        return MapPlaceDetailDto(
            bookmarkTargetId = optString("bookmarkTargetId"),
            detailType = optString("detailType"),
            placeId = optNullableLong("placeId"),
            provider = optNullableString("provider"),
            providerPlaceId = optNullableString("providerPlaceId"),
            name = optString("name"),
            category = optNullableString("category"),
            providerCategory = optNullableString("providerCategory"),
            address = optNullableString("address"),
            point =
                PlacePointDto(
                    lat = pointJson.optDouble("lat"),
                    lng = pointJson.optDouble("lng"),
                ),
            accessibilityFeatures =
                optJSONArray("accessibilityFeatures")
                    ?.let(::toAccessibilityFeatureDtos)
                    .orEmpty(),
            transitArrivals =
                optJSONArray("transitArrivals")
                    ?.let(::toTransitArrivalDtos)
                    .orEmpty(),
            isBookmarked = optBoolean("isBookmarked"),
            phone = optNullableString("phone"),
            description = optNullableString("description"),
        )
    }

    private fun toTransitArrivalDtos(arrivalsJson: JSONArray): List<PlaceTransitArrivalDto> =
        List(arrivalsJson.length()) { index ->
            arrivalsJson.getJSONObject(index).let { arrivalJson ->
                PlaceTransitArrivalDto(
                    transitType = arrivalJson.optString("transitType"),
                    routeName = arrivalJson.optString("routeName"),
                    direction = arrivalJson.optNullableString("direction"),
                    remainingMinute = arrivalJson.optNullableInt("remainingMinute"),
                    isLowFloor = arrivalJson.optNullableBoolean("isLowFloor"),
                    source = arrivalJson.optNullableString("source"),
                )
            }
        }

    private fun PlaceTransitArrivalDto.toDomain(): PlaceTransitArrival =
        PlaceTransitArrival(
            transitType = transitType,
            routeName = routeName,
            direction = direction?.takeIf { value -> value.isNotBlank() },
            remainingMinute = remainingMinute,
            isLowFloor = isLowFloor,
            source = source?.takeIf { value -> value.isNotBlank() },
        )

    private fun toAccessibilityFeatureDtos(featuresJson: JSONArray): List<PlaceAccessibilityFeatureDto> =
        List(featuresJson.length()) { index ->
            featuresJson.getJSONObject(index).let { featureJson ->
                PlaceAccessibilityFeatureDto(
                    featureType = featureJson.optString("featureType"),
                    isAvailable = featureJson.optBoolean("isAvailable"),
                )
            }
        }

    private fun JSONObject.optNullableString(name: String): String? =
        if (isNull(name)) {
            null
        } else {
            optString(name).takeIf { value -> value.isNotBlank() }
        }

    private fun JSONObject.optNullableLong(name: String): Long? =
        if (isNull(name) || has(name).not()) {
            null
        } else {
            optLong(name)
        }

    private fun JSONObject.optNullableInt(name: String): Int? =
        if (isNull(name) || has(name).not()) {
            null
        } else {
            optInt(name)
        }

    private fun JSONObject.optNullableBoolean(name: String): Boolean? =
        if (isNull(name) || has(name).not()) {
            null
        } else {
            optBoolean(name)
        }

    private fun String.toMapPlaceDetailType(): MapPlaceDetailType =
        when (trim().uppercase()) {
            "INTERNAL_PLACE" -> MapPlaceDetailType.INTERNAL_PLACE
            "EXTERNAL_POI" -> MapPlaceDetailType.EXTERNAL_POI
            "EXTERNAL_ADDRESS" -> MapPlaceDetailType.EXTERNAL_ADDRESS
            else -> MapPlaceDetailType.EXTERNAL_ADDRESS
        }
}
