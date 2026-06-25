package com.ssafy.e102.eumgil.data.route

data class RouteSearchRequestDto(
    val startPoint: RoutePointDto,
    val endPoint: RoutePointDto,
    val routeOptions: List<String> = emptyList(),
)

data class RoutePointDto(
    val lat: Double,
    val lng: Double,
)

data class RouteSearchResponseDto(
    val searchId: String? = null,
    val routes: List<RouteDto> = emptyList(),
)

data class RouteDto(
    val routeId: String? = null,
    val transportMode: String? = null,
    val routeOption: String? = null,
    val routeOptions: List<String> = emptyList(),
    val title: String? = null,
    val distanceMeter: Double? = null,
    val durationSecond: Int? = null,
    val estimatedTimeMinute: Int? = null,
    val transferCount: Int? = null,
    val badges: List<String> = emptyList(),
    val geometry: String? = null,
    // Legacy compatibility fields are kept while old segment-based fixtures still exist.
    val riskLevel: String? = null,
    val segments: List<RouteSegmentDto> = emptyList(),
    val legs: List<RouteLegDto> = emptyList(),
)

data class RouteLegDto(
    val sequence: Int? = null,
    val type: String? = null,
    val role: String? = null,
    val instruction: String? = null,
    val distanceMeter: Double? = null,
    val durationSecond: Int? = null,
    val estimatedTimeMinute: Int? = null,
    val geometry: String? = null,
    val steps: List<RouteStepDto> = emptyList(),
    val guidanceEvents: List<RouteGuidanceEventDto> = emptyList(),
    val laneOptions: List<RouteTransitLaneOptionDto> = emptyList(),
    val routeNo: String? = null,
    val boardingStop: RouteTransitStopDto? = null,
    val arrivingStop: RouteTransitStopDto? = null,
    // Legacy compatibility field kept for older fixtures and transitional payloads.
    val alightingStop: RouteTransitStopDto? = null,
    val isLowFloor: Boolean? = null,
    val badges: List<String> = emptyList(),
)

data class RouteGuidanceEventDto(
    val sequence: Int? = null,
    val type: String? = null,
    val direction: String? = null,
    val features: List<String> = emptyList(),
    val distanceFromLegStartMeter: Double? = null,
    val durationFromLegStartSecond: Int? = null,
    val distanceFromRouteStartMeter: Double? = null,
    val durationFromRouteStartSecond: Int? = null,
    val geometry: String? = null,
)

data class RouteTransitLaneOptionDto(
    val routeNo: String? = null,
    val remainingMinute: Int? = null,
    val durationSecond: Int? = null,
    val estimatedTimeMinute: Int? = null,
    val isLowFloor: Boolean? = null,
    val lowFloorReservation: LowFloorBusReservationDto? = null,
)

data class LowFloorBusReservationDto(
    val stopName: String? = null,
    val arsNo: String? = null,
    val routeNo: String? = null,
    val vehicleNo: String? = null,
    val remainingMinute: Int? = null,
    val remainingStopCount: Int? = null,
)

data class RouteStepDto(
    val sequence: Int? = null,
    val instruction: String? = null,
    val geometry: String? = null,
    val distanceMeter: Double? = null,
    val durationSecond: Int? = null,
    // Legacy single-alert payloads still exist in tests and some mock fixtures.
    val alert: RouteStepAlertDto? = null,
    val badges: List<String> = emptyList(),
    val alerts: List<RouteAlertDto> = emptyList(),
    val slopePercent: Double? = null,
    val widthState: String? = null,
)

data class RouteStepAlertDto(
    val type: String? = null,
    val distanceMeter: Double? = null,
)

data class RouteAlertDto(
    val type: String? = null,
    val distanceMeter: Double? = null,
)

data class RouteTransitStopDto(
    val name: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
)

data class RouteSegmentDto(
    val sequence: Int? = null,
    val geometry: String? = null,
    val distanceMeter: Int? = null,
    val hasStairs: Boolean? = null,
    val hasCurbGap: Boolean? = null,
    val hasCrosswalk: Boolean? = null,
    val hasSignal: Boolean? = null,
    val hasAudioSignal: Boolean? = null,
    val hasBrailleBlock: Boolean? = null,
    val riskLevel: String? = null,
    val guidanceMessage: String? = null,
)

data class RouteSelectRequestDto(
    val searchId: String,
)

data class RouteSelectResponseDto(
    val sessionId: String,
    val totalDistanceMeter: Double? = null,
    val totalDurationSecond: Int? = null,
)

data class RouteSessionResponseDto(
    val sessionId: String,
)

data class RouteTransitRefreshRequestDto(
    val legSequence: Int,
)

data class RouteTransitRefreshResponseDto(
    val type: String,
    val arrivalStatus: String,
    val transits: List<RouteTransitArrivalDto> = emptyList(),
)

data class RouteTransitArrivalDto(
    val routeNo: String? = null,
    val remainingMinute: Int? = null,
    val isLowFloor: Boolean? = null,
)

data class RouteRerouteRequestDto(
    val routeId: String,
    val currentPoint: RoutePointDto,
)

data class RouteRerouteResponseDto(
    val route: RouteDto? = null,
)

data class RouteRatingRequestDto(
    val sessionId: String,
    val score: Int,
)

data class RouteRatingResponseDto(
    val ratingId: Long,
)
