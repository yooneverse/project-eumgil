package com.ssafy.e102.eumgil.core.model

data class RouteWaypoint(
    val name: String? = null,
    val placeId: String? = null,
    val address: String? = null,
    val coordinate: GeoCoordinate,
    val category: PlaceCategory? = null,
    val serverPlaceId: Long? = null,
    val provider: String? = null,
    val providerPlaceId: String? = null,
    val providerCategory: String? = null,
)

data class RouteSearchQuery(
    val origin: RouteWaypoint,
    val destination: RouteWaypoint,
    val requestedOptions: List<RouteOption> = RouteOption.defaultSearchOptions,
) {
    init {
        require(requestedOptions.isNotEmpty()) { "Route search query requires at least one route option." }
        require(requestedOptions.distinct().size == requestedOptions.size) {
            "Route search query options must be unique."
        }
    }
}

enum class RouteOption {
    SAFE,
    SHORTEST,
    RECOMMENDED,
    MIN_TRANSFER,
    MIN_WALK,
    ;

    companion object {
        val defaultSearchOptions: List<RouteOption> = listOf(SAFE, SHORTEST)

        fun fromValue(value: String?): RouteOption? =
            entries.firstOrNull { option ->
                option.name.equals(value?.trim(), ignoreCase = true)
            }
    }
}

enum class RouteTransportMode {
    WALK,
    PUBLIC_TRANSIT,
    ;

    companion object {
        fun fromValue(
            value: String?,
            fallback: RouteTransportMode = WALK,
        ): RouteTransportMode =
            entries.firstOrNull { mode ->
                mode.name.equals(value?.trim(), ignoreCase = true)
            } ?: fallback
    }
}

enum class RouteRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    ;

    companion object {
        fun fromValue(
            value: String?,
            fallback: RouteRiskLevel = MEDIUM,
        ): RouteRiskLevel =
            entries.firstOrNull { level ->
                level.name.equals(value?.trim(), ignoreCase = true)
            } ?: fallback
    }
}

enum class RouteBadge {
    LOW_SLOPE,
    MIDDLE_SLOPE,
    STAIR,
    CROSSWALK,
    ELEVATOR,
    NARROW_SIDEWALK,
    UNPAVED,
    ;

    companion object {
        fun fromCodes(codes: List<String>): List<RouteBadge> =
            codes.mapNotNull(::fromValue)

        fun fromValue(value: String?): RouteBadge? =
            entries.firstOrNull { badge ->
                badge.name.equals(value?.trim(), ignoreCase = true)
            }
    }
}

enum class RouteGuidanceType {
    STRAIGHT,
    CROSSWALK,
    LOW_SLOPE,
    MIDDLE_SLOPE,
    STAIR,
    NARROW_SIDEWALK,
    UNPAVED,
    BUS_STOP,
    SUBWAY_ELEVATOR,
    ARRIVING_POINT,
    DESTINATION,
    ;

    companion object {
        fun fromValue(value: String?): RouteGuidanceType? =
            entries.firstOrNull { type ->
                type.name.equals(value?.trim(), ignoreCase = true)
            }
    }
}

enum class RouteGuidanceDirection {
    STRAIGHT,
    TURN_LEFT,
    TURN_RIGHT,
    ;

    companion object {
        fun fromValue(value: String?): RouteGuidanceDirection? =
            entries.firstOrNull { direction ->
                direction.name.equals(value?.trim(), ignoreCase = true)
            }
    }
}

enum class RouteGuidanceFeature {
    SIGNAL,
    AUDIO_SIGNAL,
    ;

    companion object {
        fun fromCodes(codes: List<String>): List<RouteGuidanceFeature> =
            codes.mapNotNull(::fromValue)

        fun fromValue(value: String?): RouteGuidanceFeature? =
            entries.firstOrNull { feature ->
                feature.name.equals(value?.trim(), ignoreCase = true)
            }
    }
}

enum class RouteAlertType {
    CROSSWALK,
    MIDDLE_SLOPE,
    STAIR,
    CURB,
    NARROW_SIDEWALK,
    UNPAVED,
    ELEVATOR,
    BUS_STOP,
    SUBWAY_ELEVATOR,
    ALIGHTING_POINT,
    ;

    companion object {
        fun fromValue(value: String?): RouteAlertType? =
            entries.firstOrNull { type ->
                type.name.equals(value?.trim(), ignoreCase = true)
            }
    }
}

enum class RouteLegType {
    WALK,
    BUS,
    SUBWAY,
    ;

    companion object {
        fun fromValue(
            value: String?,
            fallback: RouteLegType = WALK,
        ): RouteLegType =
            entries.firstOrNull { type ->
                type.name.equals(value?.trim(), ignoreCase = true)
            } ?: fallback
    }
}

enum class RouteLegRole {
    WALK_ONLY,
    WALK_TO_TRANSIT,
    TRANSIT,
    WALK_TO_DESTINATION,
    ;

    companion object {
        fun fromValue(
            value: String?,
            fallback: RouteLegRole = WALK_ONLY,
        ): RouteLegRole =
            entries.firstOrNull { role ->
                role.name.equals(value?.trim(), ignoreCase = true)
            } ?: fallback
    }
}

data class RouteSummary(
    val distanceMeters: Int,
    val estimatedTimeMinutes: Int,
    val riskLevel: RouteRiskLevel,
    val durationSeconds: Int? = null,
)

data class RoutePolyline(
    val points: List<GeoCoordinate> = emptyList(),
) {
    val isRenderable: Boolean
        get() = points.size >= 2

    val start: GeoCoordinate?
        get() = points.firstOrNull()

    val end: GeoCoordinate?
        get() = points.lastOrNull()
}

data class RoutePreviewModel(
    val polyline: RoutePolyline = RoutePolyline(),
    val segmentCount: Int = 0,
    val renderableSegmentCount: Int = 0,
    val fallbackSegmentCount: Int = 0,
) {
    val hasRenderableLine: Boolean
        get() = polyline.isRenderable

    val hasFallbackSegments: Boolean
        get() = fallbackSegmentCount > 0

    val skippedSegmentCount: Int
        get() = (segmentCount - renderableSegmentCount).coerceAtLeast(0)
}

data class RouteAlert(
    val type: RouteAlertType,
    val distanceMeters: Int = 0,
)

data class RouteTransitStop(
    val name: String,
    val coordinate: GeoCoordinate,
)

data class RouteTransitLaneOption(
    val routeNo: String? = null,
    val remainingMinute: Int? = null,
    val estimatedTimeMinutes: Int? = null,
    val durationSeconds: Int? = null,
    val isLowFloor: Boolean? = null,
    val lowFloorReservation: LowFloorBusReservation? = null,
)

data class LowFloorBusReservation(
    val stopName: String,
    val arsNo: String,
    val routeNo: String,
    val vehicleNo: String,
    val remainingMinute: Int,
    val remainingStopCount: Int? = null,
)

data class RouteStep(
    val sequence: Int,
    val instruction: String = RouteDefaults.DEFAULT_GUIDANCE_MESSAGE,
    val distanceMeters: Int = 0,
    val durationSeconds: Int? = null,
    val polyline: RoutePolyline = RoutePolyline(),
    val anchorCoordinate: GeoCoordinate? = null,
    val badges: List<RouteBadge> = emptyList(),
    val alerts: List<RouteAlert> = emptyList(),
    val slopePercent: Double? = null,
    val widthState: String? = null,
    val guidanceType: RouteGuidanceType? = null,
    val guidanceDirection: RouteGuidanceDirection? = null,
    val guidanceFeatures: List<RouteGuidanceFeature> = emptyList(),
    val guidanceDistanceMeters: Int? = null,
    val distanceFromLegStartMeters: Int? = null,
    val durationFromRouteStartSeconds: Int? = null,
) {
    val hasRenderablePolyline: Boolean
        get() = polyline.isRenderable
}

data class RouteLeg(
    val sequence: Int,
    val type: RouteLegType = RouteLegType.WALK,
    val role: RouteLegRole = RouteLegRole.WALK_ONLY,
    val instruction: String = RouteDefaults.DEFAULT_GUIDANCE_MESSAGE,
    val distanceMeters: Int? = null,
    val durationSeconds: Int? = null,
    val estimatedTimeMinutes: Int? = null,
    val polyline: RoutePolyline = RoutePolyline(),
    val steps: List<RouteStep> = emptyList(),
    val laneOptions: List<RouteTransitLaneOption> = emptyList(),
    val routeNo: String? = null,
    val boardingStop: RouteTransitStop? = null,
    val alightingStop: RouteTransitStop? = null,
    val isLowFloor: Boolean? = null,
    val badges: List<RouteBadge> = emptyList(),
) {
    val hasRenderablePolyline: Boolean
        get() = polyline.isRenderable
}

data class RouteSegmentSafetyFlags(
    val hasStairs: Boolean = false,
    val hasCurbGap: Boolean = false,
    val hasCrosswalk: Boolean = false,
    val hasSignal: Boolean = false,
    val hasAudioSignal: Boolean = false,
    val hasBrailleBlock: Boolean = false,
)

data class RouteSegment(
    val sequence: Int,
    val polyline: RoutePolyline = RoutePolyline(),
    val anchorCoordinate: GeoCoordinate? = null,
    val distanceMeters: Int = 0,
    val safetyFlags: RouteSegmentSafetyFlags = RouteSegmentSafetyFlags(),
    val riskLevel: RouteRiskLevel = RouteRiskLevel.MEDIUM,
    val guidanceMessage: String = RouteDefaults.DEFAULT_GUIDANCE_MESSAGE,
    val sourceLegSequence: Int? = null,
    val sourceStepSequence: Int? = null,
    val guidanceType: RouteGuidanceType? = null,
    val guidanceDirection: RouteGuidanceDirection? = null,
    val guidanceFeatures: List<RouteGuidanceFeature> = emptyList(),
    val guidanceDistanceMeters: Int? = null,
    val distanceFromLegStartMeters: Int? = null,
    val durationFromRouteStartSeconds: Int? = null,
) {
    val hasRenderablePolyline: Boolean
        get() = polyline.isRenderable
}

data class RouteCandidate(
    val routeId: String = "",
    val serverRouteId: String? = null,
    val transportMode: RouteTransportMode = RouteTransportMode.WALK,
    val routeOption: RouteOption,
    val title: String,
    val summary: RouteSummary,
    val transferCount: Int? = null,
    val badges: List<RouteBadge> = emptyList(),
    val geometry: RoutePolyline = RoutePolyline(),
    val preview: RoutePreviewModel = RoutePreviewModel(),
    val legs: List<RouteLeg> = emptyList(),
    val segments: List<RouteSegment> = emptyList(),
) {
    val previewPolyline: RoutePolyline
        get() = if (preview.polyline.isRenderable) preview.polyline else geometry

    val hasRenderablePreview: Boolean
        get() = previewPolyline.isRenderable

    val renderableSegments: List<RouteSegment>
        get() = segments.filter(RouteSegment::hasRenderablePolyline)

    val hasFallbackSegments: Boolean
        get() = preview.hasFallbackSegments
}

data class RouteSearchResult(
    val origin: RouteWaypoint,
    val destination: RouteWaypoint,
    val searchId: String? = null,
    val routes: List<RouteCandidate> = emptyList(),
) {
    val primaryRoute: RouteCandidate?
        get() = routes.firstOrNull()

    val availableOptions: List<RouteOption>
        get() = routes.map(RouteCandidate::routeOption)

    val renderableRoutes: List<RouteCandidate>
        get() = routes.filter(RouteCandidate::hasRenderablePreview)

    fun findRoute(routeOption: RouteOption): RouteCandidate? =
        routes.firstOrNull { route -> route.routeOption == routeOption }
}

enum class RouteSearchSourceType {
    SERVER_API,
}

data class RouteSearchSource(
    val type: RouteSearchSourceType,
    val label: String,
    val isFromCache: Boolean = false,
) {
    init {
        require(label.isNotBlank()) { "Route search source label must not be blank." }
    }

    fun asCached(): RouteSearchSource = copy(isFromCache = true)

    companion object {
        fun serverApi(
            label: String = "실시간 경로",
            isFromCache: Boolean = false,
        ): RouteSearchSource =
            RouteSearchSource(
                type = RouteSearchSourceType.SERVER_API,
                label = label,
                isFromCache = isFromCache,
            )
    }
}

data class RouteSearchData(
    val query: RouteSearchQuery,
    val result: RouteSearchResult,
    val source: RouteSearchSource,
) {
    val searchId: String?
        get() = result.searchId

    val routes: List<RouteCandidate>
        get() = result.routes

    val primaryRoute: RouteCandidate?
        get() = result.primaryRoute

    val availableOptions: List<RouteOption>
        get() = result.availableOptions

    val renderableRoutes: List<RouteCandidate>
        get() = result.renderableRoutes

    fun findRoute(routeOption: RouteOption): RouteCandidate? = result.findRoute(routeOption)
}

object RouteDefaults {
    const val DEFAULT_GUIDANCE_MESSAGE: String = "Continue on the suggested route."
}

fun PlaceDestination.toRouteWaypoint(): RouteWaypoint =
    RouteWaypoint(
        name = name,
        placeId = placeId,
        address = address,
        coordinate =
            GeoCoordinate(
                latitude = latitude,
                longitude = longitude,
            ),
        category = category,
        serverPlaceId = serverPlaceId,
        provider = provider,
        providerPlaceId = providerPlaceId,
        providerCategory = providerCategory,
    )
