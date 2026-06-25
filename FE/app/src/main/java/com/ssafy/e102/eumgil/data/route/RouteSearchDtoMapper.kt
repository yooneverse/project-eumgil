package com.ssafy.e102.eumgil.data.route

import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.core.model.LowFloorBusReservation
import com.ssafy.e102.eumgil.core.model.RouteAlert
import com.ssafy.e102.eumgil.core.model.RouteAlertType
import com.ssafy.e102.eumgil.core.model.RouteBadge
import com.ssafy.e102.eumgil.core.model.RouteCandidate
import com.ssafy.e102.eumgil.core.model.RouteDefaults
import com.ssafy.e102.eumgil.core.model.RouteGuidanceDirection
import com.ssafy.e102.eumgil.core.model.RouteGuidanceFeature
import com.ssafy.e102.eumgil.core.model.RouteGuidanceType
import com.ssafy.e102.eumgil.core.model.RouteLeg
import com.ssafy.e102.eumgil.core.model.RouteLegRole
import com.ssafy.e102.eumgil.core.model.RouteLegType
import com.ssafy.e102.eumgil.core.model.RouteOption
import com.ssafy.e102.eumgil.core.model.RoutePolyline
import com.ssafy.e102.eumgil.core.model.RoutePreviewModel
import com.ssafy.e102.eumgil.core.model.RouteRiskLevel
import com.ssafy.e102.eumgil.core.model.RouteSearchQuery
import com.ssafy.e102.eumgil.core.model.RouteSearchResult
import com.ssafy.e102.eumgil.core.model.RouteSegment
import com.ssafy.e102.eumgil.core.model.RouteSegmentSafetyFlags
import com.ssafy.e102.eumgil.core.model.RouteStep
import com.ssafy.e102.eumgil.core.model.RouteSummary
import com.ssafy.e102.eumgil.core.model.RouteTransitLaneOption
import com.ssafy.e102.eumgil.core.model.RouteTransitStop
import com.ssafy.e102.eumgil.core.model.RouteTransportMode
import kotlin.math.ceil
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

fun RouteSearchQuery.toRequestDto(): RouteSearchRequestDto =
    RouteSearchRequestDto(
        startPoint = origin.coordinate.toPointDto(),
        endPoint = destination.coordinate.toPointDto(),
        routeOptions = requestedOptions.map(RouteOption::name),
    )

fun parseRouteSearchResponseDto(body: String): RouteSearchResponseDto {
    val dataJson = body.requireRouteDataJson("route search response missing data object")
    val routesJson = dataJson.optJSONArray("routes") ?: JSONArray()

    return RouteSearchResponseDto(
        searchId = dataJson.optNullableString("searchId"),
        routes =
            List(routesJson.length()) { index ->
                routesJson.getJSONObject(index).toRouteDto()
            },
    )
}

fun parseRouteSessionResponseDto(body: String): RouteSessionResponseDto {
    val dataJson = body.requireRouteDataJson("route session response missing data object")
    return RouteSessionResponseDto(
        sessionId = dataJson.requireNonBlankString("sessionId"),
    )
}

fun parseRouteSelectResponseDto(body: String): RouteSelectResponseDto {
    val dataJson = body.requireRouteDataJson("route select response missing data object")
    return RouteSelectResponseDto(
        sessionId = dataJson.requireNonBlankString("sessionId"),
        totalDistanceMeter = dataJson.optNullableDouble("totalDistanceMeter"),
        totalDurationSecond = dataJson.optNullableInt("totalDurationSecond"),
    )
}

fun parseRouteTransitRefreshResponseDto(body: String): RouteTransitRefreshResponseDto {
    val dataJson = body.requireRouteDataJson("route transit refresh response missing data object")
    return RouteTransitRefreshResponseDto(
        type = dataJson.requireNonBlankString("type"),
        arrivalStatus = dataJson.requireNonBlankString("arrivalStatus"),
        transits =
            dataJson.optJSONArray("transits")
                ?.toTransitArrivalDtos()
                .orEmpty(),
    )
}

fun parseRouteRerouteResponseDto(body: String): RouteRerouteResponseDto {
    val dataJson = body.requireRouteDataJson("route reroute response missing data object")
    return RouteRerouteResponseDto(
        route = dataJson.optJSONObject("route")?.toRouteDto(),
    )
}

fun parseRouteRatingResponseDto(body: String): RouteRatingResponseDto {
    val dataJson = body.requireRouteDataJson("route rating response missing data object")
    return RouteRatingResponseDto(
        ratingId = dataJson.requireLong("ratingId"),
    )
}

fun RouteSearchResponseDto.toDomain(
    query: RouteSearchQuery,
    geometryParser: RouteGeometryParser,
): RouteSearchResult =
    RouteSearchResult(
        origin = query.origin,
        destination = query.destination,
        searchId = searchId?.trim()?.takeIf(String::isNotEmpty),
        routes =
            routes.mapIndexed { index, route ->
                route.toDomain(
                    defaultOption = query.requestedOptions.getOrElse(index) { RouteOption.SAFE },
                    geometryParser = geometryParser,
                    fallbackIndex = index + 1,
                )
            },
    )

fun RouteRerouteResponseDto.toRouteCandidate(geometryParser: RouteGeometryParser): RouteCandidate? =
    route?.toDomain(
        defaultOption = route.normalizedDeclaredOption(defaultOption = route.defaultRouteOption()),
        geometryParser = geometryParser,
        fallbackIndex = 1,
    )

fun RouteDto.toRouteCandidate(
    geometryParser: RouteGeometryParser,
    defaultOption: RouteOption = normalizedDeclaredOption(defaultOption = defaultRouteOption()),
): RouteCandidate =
    toDomain(
        defaultOption = defaultOption,
        geometryParser = geometryParser,
        fallbackIndex = 1,
    )

private fun RouteDto.toDomain(
    defaultOption: RouteOption,
    geometryParser: RouteGeometryParser,
    fallbackIndex: Int,
): RouteCandidate {
    val routeGeometryParseResult = geometryParser.parse(geometry)
    val resolvedOption = normalizedDeclaredOption(defaultOption = defaultOption)
    val resolvedTransportMode = normalizedTransportMode(resolvedOption)
    val resolvedLegs =
        toDomainLegs(
            geometryParser = geometryParser,
            routeGeometryParseResult = routeGeometryParseResult,
        )
    val resolvedSegments =
        if (resolvedLegs.isNotEmpty()) {
            resolvedLegs.toCompatibilitySegments()
        } else {
            segments.toLegacySegments(geometryParser)
        }
    val previewFromSegments = resolvedSegments.toPreviewModel()
    val resolvedGeometry =
        routeGeometryParseResult.polyline
            .takeIf(RoutePolyline::isRenderable)
            ?: previewFromSegments.polyline
    val distanceMeters =
        normalizedDistance(
            explicitDistanceMeters = distanceMeter.toRoundedMeters(),
            segmentDistanceTotal = resolvedSegments.sumOf(RouteSegment::distanceMeters),
            legDistanceTotal = resolvedLegs.sumOf(RouteLeg::resolvedDistanceMeters),
        )

    return RouteCandidate(
        routeId = normalizedRouteId(resolvedTransportMode, resolvedOption, fallbackIndex),
        serverRouteId = normalizedServerRouteId(),
        transportMode = resolvedTransportMode,
        routeOption = resolvedOption,
        title = normalizedTitle().ifEmpty { defaultTitle(resolvedOption) },
        summary =
            RouteSummary(
                distanceMeters = distanceMeters,
                estimatedTimeMinutes = normalizedEstimatedTime(distanceMeters = distanceMeters),
                riskLevel =
                    RouteRiskLevel.fromValue(
                        riskLevel,
                        fallback =
                            resolvedSegments.maxRiskLevel(
                                routeBadges = RouteBadge.fromCodes(badges),
                            ),
                    ),
                durationSeconds = durationSecond?.takeIf { value -> value >= 0 },
            ),
        transferCount = transferCount?.takeIf { count -> count >= 0 },
        badges = RouteBadge.fromCodes(badges),
        geometry = resolvedGeometry,
        preview =
            previewFromSegments.copy(
                polyline = if (resolvedGeometry.isRenderable) resolvedGeometry else previewFromSegments.polyline,
            ),
        legs = resolvedLegs,
        segments = resolvedSegments,
    )
}

private fun RouteDto.toDomainLegs(
    geometryParser: RouteGeometryParser,
    routeGeometryParseResult: RouteGeometryParseResult,
): List<RouteLeg> =
    when {
        legs.isNotEmpty() ->
            legs
                .mapIndexed { index, legDto ->
                    legDto.toDomain(
                        fallbackSequence = index + 1,
                        geometryParser = geometryParser,
                    )
                }.sortedBy(RouteLeg::sequence)

        segments.isNotEmpty() -> {
            val legacySegments = segments.toLegacySegments(geometryParser)
            listOf(
                RouteLeg(
                    sequence = 1,
                    type = RouteLegType.WALK,
                    role = RouteLegRole.WALK_ONLY,
                    instruction = legacySegments.firstOrNull()?.guidanceMessage ?: RouteDefaults.DEFAULT_GUIDANCE_MESSAGE,
                    distanceMeters = legacySegments.sumOf(RouteSegment::distanceMeters),
                    estimatedTimeMinutes =
                        estimatedTimeMinute?.takeIf { value -> value >= 0 }
                            ?: durationSecond.toEstimatedMinutesOrNull(),
                    polyline =
                        routeGeometryParseResult.polyline
                            .takeIf(RoutePolyline::isRenderable)
                            ?: legacySegments.toPreviewPolyline(),
                    steps =
                        legacySegments.map { segment ->
                            RouteStep(
                                sequence = segment.sequence,
                                instruction = segment.guidanceMessage,
                                distanceMeters = segment.distanceMeters,
                                polyline = segment.polyline,
                                anchorCoordinate = segment.anchorCoordinate,
                                badges = segment.safetyFlags.toSyntheticBadges(),
                            )
                        },
                    badges = RouteBadge.fromCodes(badges),
                ),
            )
        }

        else -> emptyList()
    }

private fun RouteLegDto.toDomain(
    fallbackSequence: Int,
    geometryParser: RouteGeometryParser,
): RouteLeg {
    val geometryParseResult = geometryParser.parse(geometry)
    val resolvedSteps =
        when {
            steps.isNotEmpty() ->
                steps
                    .mapIndexed { index, step ->
                        step.toDomain(
                            fallbackSequence = index + 1,
                            geometryParser = geometryParser,
                        )
                    }

            guidanceEvents.isNotEmpty() ->
                guidanceEvents.toDomainSteps(
                    geometryParser = geometryParser,
                    legDistanceMeters = distanceMeter.toRoundedMeters(),
                )

            else -> emptyList()
        }.sortedBy(RouteStep::sequence)
    val parsedPolyline = geometryParseResult.polyline

    return RouteLeg(
        sequence = normalizedSequence(fallbackSequence),
        type = RouteLegType.fromValue(type),
        role = normalizedRole(),
        instruction = normalizedInstruction(instruction),
        distanceMeters = distanceMeter.toRoundedMeters() ?: resolvedSteps.sumOf(RouteStep::distanceMeters),
        durationSeconds = durationSecond?.takeIf { value -> value >= 0 },
        estimatedTimeMinutes =
            estimatedTimeMinute?.takeIf { value -> value >= 0 }
                ?: durationSecond.toEstimatedMinutesOrNull(),
        polyline =
            if (parsedPolyline.points.isNotEmpty()) {
                parsedPolyline
            } else {
                resolvedSteps.toStepPreviewPolyline()
            },
        steps = resolvedSteps,
        laneOptions = laneOptions.map(RouteTransitLaneOptionDto::toDomain),
        routeNo = routeNo?.trim()?.takeIf(String::isNotEmpty),
        boardingStop = boardingStop?.toDomainOrNull(),
        alightingStop = (arrivingStop ?: alightingStop)?.toDomainOrNull(),
        isLowFloor = isLowFloor,
        badges = RouteBadge.fromCodes(badges),
    )
}

private fun RouteStepDto.toDomain(
    fallbackSequence: Int,
    geometryParser: RouteGeometryParser,
): RouteStep {
    val geometryParseResult = geometryParser.parse(geometry)

    return RouteStep(
        sequence =
            sequence
                ?.takeIf { value -> value > 0 }
                ?: fallbackSequence,
        instruction = normalizedInstruction(instruction),
        distanceMeters = distanceMeter.toRoundedMeters() ?: 0,
        durationSeconds = durationSecond?.takeIf { value -> value >= 0 },
        polyline = geometryParseResult.polyline,
        anchorCoordinate = geometryParseResult.anchorCoordinate,
        badges = RouteBadge.fromCodes(badges),
        alerts =
            alerts.mapNotNull(RouteAlertDto::toDomainOrNull).ifEmpty {
                listOfNotNull(alert?.toDomainOrNull())
            },
        slopePercent = slopePercent,
        widthState = widthState?.trim()?.takeIf(String::isNotEmpty),
    )
}

private fun RouteTransitLaneOptionDto.toDomain(): RouteTransitLaneOption =
    RouteTransitLaneOption(
        routeNo = routeNo?.trim()?.takeIf(String::isNotEmpty),
        remainingMinute = remainingMinute,
        estimatedTimeMinutes = estimatedTimeMinute?.takeIf { value -> value >= 0 },
        durationSeconds = durationSecond?.takeIf { value -> value >= 0 },
        isLowFloor = isLowFloor,
        lowFloorReservation = lowFloorReservation?.toDomainOrNull(),
    )

private fun LowFloorBusReservationDto.toDomainOrNull(): LowFloorBusReservation? {
    val resolvedStopName = stopName?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val resolvedArsNo = arsNo?.filter(Char::isDigit)?.takeIf(String::isNotEmpty) ?: return null
    val resolvedRouteNo = routeNo?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val resolvedVehicleNo = vehicleNo?.filter(Char::isDigit)?.takeIf(String::isNotEmpty) ?: return null
    val resolvedRemainingMinute = remainingMinute?.takeIf { value -> value >= 0 } ?: return null

    return LowFloorBusReservation(
        stopName = resolvedStopName,
        arsNo = resolvedArsNo,
        routeNo = resolvedRouteNo,
        vehicleNo = resolvedVehicleNo,
        remainingMinute = resolvedRemainingMinute,
        remainingStopCount = remainingStopCount?.takeIf { value -> value >= 0 },
    )
}

private fun List<RouteGuidanceEventDto>.toDomainSteps(
    geometryParser: RouteGeometryParser,
    legDistanceMeters: Int?,
): List<RouteStep> {
    var previousDistanceMeters = 0
    val sortedEvents =
        sortedWith(
            compareBy<RouteGuidanceEventDto> { event ->
                event.sequence ?: Int.MAX_VALUE
            }.thenBy { event ->
                event.distanceFromLegStartMeter?.toRoundedMeters() ?: Int.MAX_VALUE
            },
        )

    return sortedEvents.mapIndexed { index, event ->
        val cumulativeDistance = event.distanceFromLegStartMeter.toRoundedMeters() ?: previousDistanceMeters
        val stepDistance = (cumulativeDistance - previousDistanceMeters).coerceAtLeast(0)
        val nextEventDistance =
            sortedEvents
                .drop(index + 1)
                .firstNotNullOfOrNull { nextEvent -> nextEvent.distanceFromLegStartMeter.toRoundedMeters() }
        val guidanceDistance =
            event.guidanceDisplayDistanceMeters(
                currentDistanceMeters = cumulativeDistance,
                distanceFromPreviousEventMeters = stepDistance,
                nextEventDistanceMeters = nextEventDistance,
                legDistanceMeters = legDistanceMeters,
            )
        previousDistanceMeters = maxOf(previousDistanceMeters, cumulativeDistance)

        event.toDomain(
            fallbackSequence = index + 1,
            distanceMeters = stepDistance,
            guidanceDistanceMeters = guidanceDistance,
            geometryParser = geometryParser,
        )
    }
}

private fun RouteGuidanceEventDto.toDomain(
    fallbackSequence: Int,
    distanceMeters: Int,
    guidanceDistanceMeters: Int,
    geometryParser: RouteGeometryParser,
): RouteStep {
    val eventType = RouteGuidanceEventType.fromValue(type)
    val guidanceType = resolveGuidanceType(legacyEventType = eventType)
    val guidanceDirection = resolveGuidanceDirection(legacyEventType = eventType)
    val guidanceFeatures = resolveGuidanceFeatures(legacyEventType = eventType)
    val resolvedBadges = eventType?.badges(guidanceFeatures).orEmpty().ifEmpty { guidanceType.toRouteBadges() }
    val geometryParseResult = geometryParser.parse(geometry)

    return RouteStep(
        sequence =
            sequence
                ?.takeIf { value -> value > 0 }
                ?: fallbackSequence,
        instruction =
            eventType?.instruction(guidanceFeatures)
                ?: guidanceDirection?.instruction
                ?: normalizedInstruction(type ?: direction),
        distanceMeters = distanceMeters,
        polyline = geometryParseResult.polyline,
        anchorCoordinate = geometryParseResult.anchorCoordinate,
        badges = resolvedBadges,
        alerts = listOfNotNull(eventType?.toAlert(distanceMeters = guidanceDistanceMeters)),
        slopePercent = null,
        guidanceType = guidanceType,
        guidanceDirection = guidanceDirection,
        guidanceFeatures = guidanceFeatures,
        guidanceDistanceMeters = guidanceDistanceMeters,
        distanceFromLegStartMeters = distanceFromLegStartMeter.toRoundedMeters(),
        durationFromRouteStartSeconds = durationFromRouteStartSecond?.takeIf { value -> value >= 0 },
    )
}

private fun RouteGuidanceEventDto.guidanceDisplayDistanceMeters(
    currentDistanceMeters: Int,
    distanceFromPreviousEventMeters: Int,
    nextEventDistanceMeters: Int?,
    legDistanceMeters: Int?,
): Int {
    val guidanceType = resolveGuidanceType(legacyEventType = RouteGuidanceEventType.fromValue(type))
    val guidanceDirection = resolveGuidanceDirection(legacyEventType = RouteGuidanceEventType.fromValue(type))

    return when {
        guidanceType == RouteGuidanceType.DESTINATION -> 0
        guidanceType == RouteGuidanceType.STRAIGHT ||
            (guidanceType == null && guidanceDirection == RouteGuidanceDirection.STRAIGHT) -> {
            val endDistance = nextEventDistanceMeters ?: legDistanceMeters ?: currentDistanceMeters
            (endDistance - currentDistanceMeters).coerceAtLeast(0)
        }
        guidanceDirection == RouteGuidanceDirection.TURN_LEFT ||
            guidanceDirection == RouteGuidanceDirection.TURN_RIGHT ->
            distanceFromPreviousEventMeters.coerceAtLeast(0)
        else -> currentDistanceMeters.coerceAtLeast(0)
    }
}

private fun RouteGuidanceEventDto.resolveGuidanceType(legacyEventType: RouteGuidanceEventType?): RouteGuidanceType? =
    legacyEventType?.guidanceType ?: RouteGuidanceType.fromValue(type)

private fun RouteGuidanceEventDto.resolveGuidanceDirection(
    legacyEventType: RouteGuidanceEventType?,
): RouteGuidanceDirection? =
    RouteGuidanceDirection.fromValue(direction)
        ?: legacyEventType?.guidanceDirection
        ?: if (RouteGuidanceType.fromValue(type) == RouteGuidanceType.STRAIGHT) {
            RouteGuidanceDirection.STRAIGHT
        } else {
            null
        }

private fun RouteGuidanceEventDto.resolveGuidanceFeatures(
    legacyEventType: RouteGuidanceEventType?,
): List<RouteGuidanceFeature> =
    (RouteGuidanceFeature.fromCodes(features) + legacyEventType?.guidanceFeatures.orEmpty()).distinct()

private fun RouteGuidanceType?.toRouteBadges(): List<RouteBadge> =
    when (this) {
        RouteGuidanceType.CROSSWALK -> listOf(RouteBadge.CROSSWALK)
        RouteGuidanceType.LOW_SLOPE -> listOf(RouteBadge.LOW_SLOPE)
        RouteGuidanceType.MIDDLE_SLOPE -> listOf(RouteBadge.MIDDLE_SLOPE)
        RouteGuidanceType.STAIR -> listOf(RouteBadge.STAIR)
        RouteGuidanceType.NARROW_SIDEWALK -> listOf(RouteBadge.NARROW_SIDEWALK)
        RouteGuidanceType.UNPAVED -> listOf(RouteBadge.UNPAVED)
        RouteGuidanceType.SUBWAY_ELEVATOR -> listOf(RouteBadge.ELEVATOR)
        else -> emptyList()
    }

private fun RouteStepAlertDto.toDomainOrNull(): RouteAlert? {
    val resolvedType = RouteAlertType.fromValue(type) ?: return null
    return RouteAlert(
        type = resolvedType,
        distanceMeters = distanceMeter.toRoundedMeters() ?: 0,
    )
}

private fun RouteAlertDto.toDomainOrNull(): RouteAlert? {
    val resolvedType = RouteAlertType.fromValue(type) ?: return null
    return RouteAlert(
        type = resolvedType,
        distanceMeters = distanceMeter.toRoundedMeters() ?: 0,
    )
}

private fun RouteTransitStopDto.toDomainOrNull(): RouteTransitStop? {
    val resolvedName = name?.trim().orEmpty()
    val resolvedLat = lat ?: return null
    val resolvedLng = lng ?: return null
    if (resolvedName.isEmpty()) return null

    return RouteTransitStop(
        name = resolvedName,
        coordinate = GeoCoordinate(latitude = resolvedLat, longitude = resolvedLng),
    )
}

private fun List<RouteLeg>.toCompatibilitySegments(): List<RouteSegment> {
    var nextSequence = 1

    return buildList {
        sortedBy(RouteLeg::sequence).forEach { leg ->
            if (leg.type == RouteLegType.WALK && leg.steps.isNotEmpty()) {
                leg.steps.forEach { step ->
                    add(
                        step.toCompatibilitySegment(
                            sequence = nextSequence++,
                            sourceLegSequence = leg.sequence,
                        ),
                    )
                }
            } else {
                add(
                    leg.toCompatibilitySegment(
                        sequence = nextSequence++,
                    ),
                )
                leg.toAlightingCompatibilitySegment(sequence = nextSequence)?.let { alightingSegment ->
                    add(alightingSegment)
                    nextSequence++
                }
            }
        }
    }
}

private fun RouteStep.toCompatibilitySegment(
    sequence: Int,
    sourceLegSequence: Int,
): RouteSegment {
    val resolvedSafetyFlags =
        buildSafetyFlags(badges = badges, alerts = alerts, guidanceMessage = instruction)
            .withGuidanceFeatures(guidanceFeatures)

    return RouteSegment(
        sequence = sequence,
        polyline = polyline,
        anchorCoordinate = anchorCoordinate,
        distanceMeters = distanceMeters,
        safetyFlags = resolvedSafetyFlags,
        riskLevel = resolveRiskLevel(badges = badges, alerts = alerts, guidanceMessage = instruction),
        guidanceMessage = instruction.ifBlank { RouteDefaults.DEFAULT_GUIDANCE_MESSAGE },
        sourceLegSequence = sourceLegSequence,
        sourceStepSequence = this.sequence,
        guidanceType = guidanceType,
        guidanceDirection = guidanceDirection,
        guidanceFeatures = guidanceFeatures,
        guidanceDistanceMeters = guidanceDistanceMeters,
        distanceFromLegStartMeters = distanceFromLegStartMeters,
        durationFromRouteStartSeconds = durationFromRouteStartSeconds,
    )
}

private fun RouteLeg.toCompatibilitySegment(sequence: Int): RouteSegment =
    RouteSegment(
        sequence = sequence,
        polyline = polyline,
        distanceMeters = resolvedDistanceMeters,
        safetyFlags = buildSafetyFlags(badges = badges, alerts = emptyList(), guidanceMessage = instruction),
        riskLevel = resolveRiskLevel(badges = badges, alerts = emptyList(), guidanceMessage = instruction),
        guidanceMessage = instruction.ifBlank { RouteDefaults.DEFAULT_GUIDANCE_MESSAGE },
        sourceLegSequence = this.sequence,
    )

private fun RouteLeg.toAlightingCompatibilitySegment(sequence: Int): RouteSegment? {
    if (type != RouteLegType.BUS && type != RouteLegType.SUBWAY) return null
    val stop = alightingStop ?: return null
    return RouteSegment(
        sequence = sequence,
        polyline = RoutePolyline(),
        anchorCoordinate = stop.coordinate,
        distanceMeters = 0,
        safetyFlags = RouteSegmentSafetyFlags(),
        riskLevel = RouteRiskLevel.LOW,
        guidanceMessage = "${stop.name} \uD558\uCC28\uC9C0\uC810\uC785\uB2C8\uB2E4.",
        sourceLegSequence = this.sequence,
        guidanceType = RouteGuidanceType.ARRIVING_POINT,
    )
}

private fun List<RouteSegmentDto>.toLegacySegments(geometryParser: RouteGeometryParser): List<RouteSegment> =
    mapIndexed { index, segment ->
        segment.toLegacyDomain(
            fallbackSequence = index + 1,
            geometryParser = geometryParser,
        )
    }.sortedBy(RouteSegment::sequence)

private fun RouteSegmentDto.toLegacyDomain(
    fallbackSequence: Int,
    geometryParser: RouteGeometryParser,
): RouteSegment {
    val geometryParseResult = geometryParser.parse(geometry)

    return RouteSegment(
        sequence = normalizedSequence(fallbackSequence),
        polyline = geometryParseResult.polyline,
        anchorCoordinate = geometryParseResult.anchorCoordinate,
        distanceMeters = distanceMeter?.takeIf { distance -> distance >= 0 } ?: 0,
        safetyFlags =
            RouteSegmentSafetyFlags(
                hasStairs = hasStairs == true,
                hasCurbGap = hasCurbGap == true,
                hasCrosswalk = hasCrosswalk == true,
                hasSignal = hasSignal == true,
                hasAudioSignal = hasAudioSignal == true,
                hasBrailleBlock = hasBrailleBlock == true,
            ),
        riskLevel = RouteRiskLevel.fromValue(riskLevel),
        guidanceMessage = normalizedInstruction(guidanceMessage),
    )
}

private fun GeoCoordinate.toPointDto(): RoutePointDto =
    RoutePointDto(
        lat = latitude,
        lng = longitude,
    )

fun JSONObject.toRouteDto(): RouteDto =
    RouteDto(
        routeId = optNullableString("routeId"),
        transportMode = optNullableString("transportMode"),
        routeOption = optNullableString("routeOption"),
        routeOptions = optStringList("routeOptions"),
        title = optNullableString("title"),
        distanceMeter = optNullableDouble("distanceMeter"),
        durationSecond = optNullableInt("durationSecond"),
        estimatedTimeMinute = optNullableInt("estimatedTimeMinute"),
        transferCount = optNullableInt("transferCount"),
        badges = optStringList("badges"),
        geometry = optNullableString("geometry"),
        riskLevel = optNullableString("riskLevel"),
        segments =
            optJSONArray("segments")
                ?.toSegmentDtos()
                .orEmpty(),
        legs =
            optJSONArray("legs")
                ?.toLegDtos()
                .orEmpty(),
    )

private fun RouteDto.normalizedTitle(): String = title?.trim().orEmpty()

private fun RouteDto.normalizedServerRouteId(): String? = routeId?.trim()?.takeIf(String::isNotEmpty)

private fun RouteDto.normalizedDeclaredOption(defaultOption: RouteOption): RouteOption =
    RouteOption.fromValue(routeOption)
        ?: routeOptions.firstNotNullOfOrNull(RouteOption::fromValue)
        ?: defaultOption

private fun RouteDto.defaultRouteOption(): RouteOption =
    when (RouteTransportMode.fromValue(transportMode, fallback = RouteTransportMode.WALK)) {
        RouteTransportMode.WALK -> RouteOption.SAFE
        RouteTransportMode.PUBLIC_TRANSIT -> RouteOption.RECOMMENDED
    }

private fun RouteDto.normalizedRouteId(
    transportMode: RouteTransportMode,
    resolvedOption: RouteOption,
    fallbackIndex: Int,
): String =
    routeId
        ?.trim()
        .orEmpty()
        .ifEmpty {
            val routePrefix =
                when (transportMode) {
                    RouteTransportMode.WALK -> "walk"
                    RouteTransportMode.PUBLIC_TRANSIT -> "transit"
                }
            "$routePrefix-${resolvedOption.name.lowercase()}-$fallbackIndex"
        }

private fun RouteDto.normalizedTransportMode(resolvedOption: RouteOption): RouteTransportMode {
    val fallback =
        when (resolvedOption) {
            RouteOption.SAFE,
            RouteOption.SHORTEST,
                -> RouteTransportMode.WALK

            RouteOption.RECOMMENDED,
            RouteOption.MIN_TRANSFER,
            RouteOption.MIN_WALK,
                -> RouteTransportMode.PUBLIC_TRANSIT
        }

    return RouteTransportMode.fromValue(transportMode, fallback = fallback)
}

private fun RouteDto.normalizedDistance(
    explicitDistanceMeters: Int?,
    segmentDistanceTotal: Int,
    legDistanceTotal: Int,
): Int =
    explicitDistanceMeters ?: segmentDistanceTotal.takeIf { total -> total > 0 } ?: legDistanceTotal.coerceAtLeast(0)

private fun RouteDto.normalizedEstimatedTime(distanceMeters: Int): Int =
    estimatedTimeMinute
        ?.takeIf { estimatedTime -> estimatedTime >= 0 }
        ?: durationSecond.toEstimatedMinutesOrNull()
        ?: distanceMeters.toEstimatedMinutes()

private fun RouteLegDto.normalizedSequence(fallbackSequence: Int): Int =
    sequence
        ?.takeIf { candidateSequence -> candidateSequence > 0 }
        ?: fallbackSequence

private fun RouteLegDto.normalizedRole(): RouteLegRole =
    when {
        role.equals("TRANSIT_TO_WALK", ignoreCase = true) -> RouteLegRole.WALK_TO_DESTINATION
        else -> RouteLegRole.fromValue(role)
    }

private fun RouteSegmentDto.normalizedSequence(fallbackSequence: Int): Int =
    sequence
        ?.takeIf { candidateSequence -> candidateSequence > 0 }
        ?: fallbackSequence

private fun normalizedInstruction(value: String?): String =
    value
        ?.trim()
        .orEmpty()
        .ifEmpty { RouteDefaults.DEFAULT_GUIDANCE_MESSAGE }

private fun List<RouteLegDto>.toSegmentDtos(): List<RouteSegmentDto> =
    flatMap { leg -> leg.steps }
        .mapIndexed { index, step -> step.toSegmentDto(fallbackSequence = index + 1) }

private fun RouteStepDto.toSegmentDto(fallbackSequence: Int): RouteSegmentDto {
    val alertType = RouteStepAlertType.fromValue(alert?.type)
    return RouteSegmentDto(
        sequence = sequence?.takeIf { candidateSequence -> candidateSequence > 0 } ?: fallbackSequence,
        geometry = geometry,
        distanceMeter = distanceMeter?.takeIf { distance -> distance >= 0.0 }?.roundToInt(),
        hasStairs = alertType == RouteStepAlertType.STAIR,
        hasCrosswalk = alertType?.isCrosswalk == true,
        hasSignal = alertType == RouteStepAlertType.CROSSWALK_SIGNAL ||
            alertType == RouteStepAlertType.CROSSWALK_AUDIO,
        hasAudioSignal = alertType == RouteStepAlertType.CROSSWALK_AUDIO,
        riskLevel = alertType?.riskLevel,
        guidanceMessage = instruction,
    )
}

private fun defaultTitle(routeOption: RouteOption): String =
    when (routeOption) {
        RouteOption.SAFE -> "Safe Route"
        RouteOption.SHORTEST -> "Shortest Route"
        RouteOption.RECOMMENDED -> "Recommended Route"
        RouteOption.MIN_TRANSFER -> "Minimum Transfer Route"
        RouteOption.MIN_WALK -> "Minimum Walk Route"
    }

fun List<RouteSegmentDto>.toRoutePreviewModel(geometryParser: RouteGeometryParser): RoutePreviewModel =
    toLegacySegments(geometryParser = geometryParser).toPreviewModel()

private fun List<RouteSegment>.toPreviewModel(): RoutePreviewModel {
    val renderableSegmentCount = count(RouteSegment::hasRenderablePolyline)
    return RoutePreviewModel(
        polyline = toPreviewPolyline(),
        segmentCount = size,
        renderableSegmentCount = renderableSegmentCount,
        fallbackSegmentCount = size - renderableSegmentCount,
    )
}

private fun List<RouteSegment>.toPreviewPolyline(): RoutePolyline {
    val previewPoints = mutableListOf<GeoCoordinate>()

    forEach { segment ->
        if (!segment.hasRenderablePolyline) return@forEach

        previewPoints.appendUniquePoints(segment.polyline.points)
    }

    return RoutePolyline(points = previewPoints)
}

private fun List<RouteStep>.toStepPreviewPolyline(): RoutePolyline {
    val previewPoints = mutableListOf<GeoCoordinate>()

    forEach { step ->
        if (!step.hasRenderablePolyline) return@forEach

        previewPoints.appendUniquePoints(step.polyline.points)
    }

    return RoutePolyline(points = previewPoints)
}

private fun MutableList<GeoCoordinate>.appendUniquePoints(points: List<GeoCoordinate>) {
    var previousPoint = lastOrNull()

    points.forEach { point ->
        if (previousPoint != point) {
            add(point)
            previousPoint = point
        }
    }
}

private fun List<RouteSegment>.maxRiskLevel(routeBadges: List<RouteBadge> = emptyList()): RouteRiskLevel {
    val segmentLevel =
        maxByOrNull { segment -> segment.riskLevel.severity }
            ?.riskLevel
            ?: RouteRiskLevel.LOW
    val badgeLevel = resolveRiskLevel(badges = routeBadges, alerts = emptyList(), guidanceMessage = null)
    return if (segmentLevel.severity >= badgeLevel.severity) segmentLevel else badgeLevel
}

private val RouteRiskLevel.severity: Int
    get() =
        when (this) {
            RouteRiskLevel.LOW -> 0
            RouteRiskLevel.MEDIUM -> 1
            RouteRiskLevel.HIGH -> 2
        }

private fun Int.toEstimatedMinutes(): Int =
    if (this <= 0) {
        0
    } else {
        ceil(this / DEFAULT_WALKING_SPEED_METERS_PER_MINUTE).toInt()
    }

private fun Double?.toRoundedMeters(): Int? =
    this
        ?.takeIf { value -> value >= 0.0 }
        ?.roundToInt()

private fun Int?.toEstimatedMinutesOrNull(): Int? =
    this
        ?.takeIf { value -> value >= 0 }
        ?.let { durationSeconds ->
            if (durationSeconds == 0) {
                0
            } else {
                ceil(durationSeconds / 60.0).toInt()
            }
        }

private val RouteLeg.resolvedDistanceMeters: Int
    get() = distanceMeters ?: steps.sumOf(RouteStep::distanceMeters)

private fun JSONArray.toLegDtos(): List<RouteLegDto> =
    List(length()) { index ->
        getJSONObject(index).toLegDto()
    }

private fun JSONObject.toLegDto(): RouteLegDto =
    RouteLegDto(
        sequence = optNullableInt("sequence"),
        type = optNullableString("type"),
        role = optNullableString("role"),
        instruction = optNullableString("instruction"),
        distanceMeter = optNullableDouble("distanceMeter"),
        durationSecond = optNullableInt("durationSecond"),
        estimatedTimeMinute = optNullableInt("estimatedTimeMinute"),
        geometry = optNullableString("geometry"),
        steps =
            optJSONArray("steps")
                ?.toStepDtos()
                .orEmpty(),
        guidanceEvents =
            optJSONArray("guidanceEvents")
                ?.toGuidanceEventDtos()
                .orEmpty(),
        laneOptions =
            optJSONArray("laneOptions")
                ?.toTransitLaneOptionDtos()
                .orEmpty(),
        routeNo = optNullableString("routeNo"),
        boardingStop = optJSONObject("boardingStop")?.toTransitStopDto(),
        arrivingStop =
            (
                optJSONObject("arrivingStop")
                    ?: optJSONObject("alightingStop")
            )?.toTransitStopDto(),
        isLowFloor = optNullableBoolean("isLowFloor"),
        badges = optStringList("badges"),
    )

private fun JSONArray.toGuidanceEventDtos(): List<RouteGuidanceEventDto> =
    List(length()) { index ->
        getJSONObject(index).toGuidanceEventDto()
    }

private fun JSONObject.toGuidanceEventDto(): RouteGuidanceEventDto =
    RouteGuidanceEventDto(
        sequence = optNullableInt("sequence"),
        type = optNullableString("type"),
        direction = optNullableString("direction"),
        features = optStringList("features"),
        distanceFromLegStartMeter = optNullableDouble("distanceFromLegStartMeter"),
        durationFromLegStartSecond = optNullableInt("durationFromLegStartSecond"),
        distanceFromRouteStartMeter = optNullableDouble("distanceFromRouteStartMeter"),
        durationFromRouteStartSecond = optNullableInt("durationFromRouteStartSecond"),
        geometry = optNullableString("geometry"),
    )

private fun JSONArray.toTransitLaneOptionDtos(): List<RouteTransitLaneOptionDto> =
    List(length()) { index ->
        getJSONObject(index).toTransitLaneOptionDto()
    }

private fun JSONObject.toTransitLaneOptionDto(): RouteTransitLaneOptionDto =
    RouteTransitLaneOptionDto(
        routeNo = optNullableString("routeNo"),
        remainingMinute = optNullableInt("remainingMinute"),
        durationSecond = optNullableInt("durationSecond"),
        estimatedTimeMinute = optNullableInt("estimatedTimeMinute"),
        isLowFloor = optNullableBoolean("isLowFloor"),
        lowFloorReservation = optJSONObject("lowFloorReservation")?.toLowFloorBusReservationDto(),
    )

private fun JSONObject.toLowFloorBusReservationDto(): LowFloorBusReservationDto =
    LowFloorBusReservationDto(
        stopName = optNullableString("stopName"),
        arsNo = optNullableString("arsNo"),
        routeNo = optNullableString("routeNo"),
        vehicleNo = optNullableString("vehicleNo"),
        remainingMinute = optNullableInt("remainingMinute"),
        remainingStopCount = optNullableInt("remainingStopCount"),
    )

private fun JSONArray.toStepDtos(): List<RouteStepDto> =
    List(length()) { index ->
        getJSONObject(index).toStepDto()
    }

private fun JSONObject.toStepDto(): RouteStepDto =
    RouteStepDto(
        sequence = optNullableInt("sequence"),
        instruction = optNullableString("instruction"),
        geometry = optNullableString("geometry"),
        distanceMeter = optNullableDouble("distanceMeter"),
        durationSecond = optNullableInt("durationSecond"),
        alert = optJSONObject("alert")?.toStepAlertDto(),
        badges = optStringList("badges"),
        alerts =
            optJSONArray("alerts")
                ?.toAlertDtos()
                .orEmpty(),
        slopePercent = optNullableDouble("slopePercent"),
        widthState = optNullableString("widthState"),
    )

private fun JSONObject.toStepAlertDto(): RouteStepAlertDto =
    RouteStepAlertDto(
        type = optNullableString("type"),
        distanceMeter = optNullableDouble("distanceMeter"),
    )

private fun JSONArray.toAlertDtos(): List<RouteAlertDto> =
    List(length()) { index ->
        getJSONObject(index).toAlertDto()
    }

private fun JSONObject.toAlertDto(): RouteAlertDto =
    RouteAlertDto(
        type = optNullableString("type"),
        distanceMeter = optNullableDouble("distanceMeter"),
    )

private fun JSONArray.toTransitArrivalDtos(): List<RouteTransitArrivalDto> =
    List(length()) { index ->
        getJSONObject(index).toTransitArrivalDto()
    }

private fun JSONObject.toTransitArrivalDto(): RouteTransitArrivalDto =
    RouteTransitArrivalDto(
        routeNo = optNullableString("routeNo"),
        remainingMinute = optNullableInt("remainingMinute"),
        isLowFloor = optNullableBoolean("isLowFloor"),
    )

private fun JSONObject.toTransitStopDto(): RouteTransitStopDto =
    RouteTransitStopDto(
        name = optNullableString("name"),
        lat = optNullableDouble("lat"),
        lng = optNullableDouble("lng"),
    )

private fun JSONArray.toSegmentDtos(): List<RouteSegmentDto> =
    List(length()) { index ->
        getJSONObject(index).toSegmentDto()
    }

private fun JSONObject.toSegmentDto(): RouteSegmentDto =
    RouteSegmentDto(
        sequence = optNullableInt("sequence"),
        geometry = optNullableString("geometry"),
        distanceMeter = optNullableInt("distanceMeter"),
        hasStairs = optNullableBoolean("hasStairs"),
        hasCurbGap = optNullableBoolean("hasCurbGap"),
        hasCrosswalk = optNullableBoolean("hasCrosswalk"),
        hasSignal = optNullableBoolean("hasSignal"),
        hasAudioSignal = optNullableBoolean("hasAudioSignal"),
        hasBrailleBlock = optNullableBoolean("hasBrailleBlock"),
        riskLevel = optNullableString("riskLevel"),
        guidanceMessage = optNullableString("guidanceMessage"),
    )

private fun JSONObject.optNullableBoolean(name: String): Boolean? =
    if (isNull(name)) {
        null
    } else {
        optBoolean(name)
    }

private fun JSONObject.optNullableDouble(name: String): Double? =
    if (isNull(name)) {
        null
    } else {
        optDouble(name)
    }

private fun JSONObject.optNullableInt(name: String): Int? =
    if (isNull(name)) {
        null
    } else {
        optInt(name)
    }

private fun JSONObject.optNullableString(name: String): String? =
    if (isNull(name)) {
        null
    } else {
        optString(name).takeIf(String::isNotBlank)
    }

private fun JSONObject.requireLong(name: String): Long =
    if (isNull(name)) {
        error("route response missing $name")
    } else {
        optLong(name)
    }

private fun JSONObject.requireNonBlankString(name: String): String =
    optNullableString(name)
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: error("route response missing $name")

private fun JSONObject.optStringList(name: String): List<String> =
    optJSONArray(name)
        ?.let { jsonArray ->
            List(jsonArray.length()) { index ->
                jsonArray.optString(index)
            }.filter(String::isNotBlank)
        }
        .orEmpty()

private fun String.requireRouteDataJson(missingDataMessage: String): JSONObject {
    val responseJson = JSONObject(this)
    return responseJson.optJSONObject("data") ?: error(missingDataMessage)
}

private fun RouteSegmentSafetyFlags.toSyntheticBadges(): List<RouteBadge> =
    buildList {
        if (hasStairs) add(RouteBadge.STAIR)
        if (hasCrosswalk) add(RouteBadge.CROSSWALK)
    }

private fun buildSafetyFlags(
    badges: List<RouteBadge>,
    alerts: List<RouteAlert>,
    guidanceMessage: String?,
): RouteSegmentSafetyFlags {
    val normalizedMessage = guidanceMessage.orEmpty()
    return RouteSegmentSafetyFlags(
        hasStairs = RouteBadge.STAIR in badges || alerts.any { alert -> alert.type == RouteAlertType.STAIR },
        hasCurbGap = alerts.any { alert -> alert.type == RouteAlertType.CURB } || normalizedMessage.contains("턱", ignoreCase = true),
        hasCrosswalk = RouteBadge.CROSSWALK in badges || alerts.any { alert -> alert.type == RouteAlertType.CROSSWALK },
        hasSignal = normalizedMessage.contains("신호", ignoreCase = true) || normalizedMessage.contains("signal", ignoreCase = true),
        hasAudioSignal = normalizedMessage.contains("음향", ignoreCase = true) || normalizedMessage.contains("audio", ignoreCase = true),
        hasBrailleBlock = normalizedMessage.contains("점자", ignoreCase = true) || normalizedMessage.contains("braille", ignoreCase = true),
    )
}

private fun RouteSegmentSafetyFlags.withGuidanceFeatures(
    features: List<RouteGuidanceFeature>,
): RouteSegmentSafetyFlags =
    copy(
        hasSignal = hasSignal || RouteGuidanceFeature.SIGNAL in features,
        hasAudioSignal = hasAudioSignal || RouteGuidanceFeature.AUDIO_SIGNAL in features,
    )

private fun resolveRiskLevel(
    badges: List<RouteBadge>,
    alerts: List<RouteAlert>,
    guidanceMessage: String?,
): RouteRiskLevel {
    val message = guidanceMessage.orEmpty()
    if (
        RouteBadge.STAIR in badges ||
        RouteBadge.NARROW_SIDEWALK in badges ||
        RouteBadge.UNPAVED in badges ||
        alerts.any { alert ->
            alert.type == RouteAlertType.STAIR ||
                alert.type == RouteAlertType.CURB ||
                alert.type == RouteAlertType.NARROW_SIDEWALK ||
                alert.type == RouteAlertType.UNPAVED
        } ||
        message.contains("공사", ignoreCase = true) ||
        message.contains("construction", ignoreCase = true)
    ) {
        return RouteRiskLevel.HIGH
    }

    if (
        RouteBadge.MIDDLE_SLOPE in badges ||
        RouteBadge.CROSSWALK in badges ||
        RouteBadge.ELEVATOR in badges ||
        alerts.any { alert ->
            alert.type == RouteAlertType.CROSSWALK ||
                alert.type == RouteAlertType.MIDDLE_SLOPE ||
                alert.type == RouteAlertType.ELEVATOR ||
                alert.type == RouteAlertType.BUS_STOP ||
                alert.type == RouteAlertType.SUBWAY_ELEVATOR ||
                alert.type == RouteAlertType.ALIGHTING_POINT
        }
    ) {
        return RouteRiskLevel.MEDIUM
    }

    return RouteRiskLevel.LOW
}

private const val DEFAULT_WALKING_SPEED_METERS_PER_MINUTE: Double = 60.0

private enum class RouteStepAlertType(
    val isCrosswalk: Boolean = false,
    val riskLevel: String? = null,
) {
    CROSSWALK(isCrosswalk = true),
    CROSSWALK_SIGNAL(isCrosswalk = true),
    CROSSWALK_AUDIO(isCrosswalk = true),
    STAIR(riskLevel = "HIGH"),
    NARROW_SIDEWALK(riskLevel = "HIGH"),
    UNPAVED(riskLevel = "MEDIUM"),
    MIDDLE_SLOPE(riskLevel = "MEDIUM"),
    ELEVATOR,
    BUS_STOP,
    SUBWAY_ELEVATOR,
    ALIGHTING_POINT,
    ;

    companion object {
        fun fromValue(value: String?): RouteStepAlertType? =
            entries.firstOrNull { type ->
                type.name.equals(value?.trim(), ignoreCase = true)
            }
    }
}

private enum class RouteGuidanceEventType(
    private val defaultInstruction: String,
    private val defaultBadges: List<RouteBadge> = emptyList(),
    val alertType: RouteAlertType? = null,
    val guidanceType: RouteGuidanceType? = null,
    val guidanceDirection: RouteGuidanceDirection? = null,
    val guidanceFeatures: List<RouteGuidanceFeature> = emptyList(),
) {
    TURN_LEFT(
        defaultInstruction = "Turn left.",
        guidanceDirection = RouteGuidanceDirection.TURN_LEFT,
    ),
    TURN_RIGHT(
        defaultInstruction = "Turn right.",
        guidanceDirection = RouteGuidanceDirection.TURN_RIGHT,
    ),
    STRAIGHT(
        defaultInstruction = "Continue straight.",
        guidanceType = RouteGuidanceType.STRAIGHT,
        guidanceDirection = RouteGuidanceDirection.STRAIGHT,
    ),
    CROSSWALK(
        defaultInstruction = "Crosswalk ahead.",
        defaultBadges = listOf(RouteBadge.CROSSWALK),
        alertType = RouteAlertType.CROSSWALK,
        guidanceType = RouteGuidanceType.CROSSWALK,
    ),
    CROSSWALK_SIGNAL(
        defaultInstruction = "Signalized crosswalk ahead.",
        defaultBadges = listOf(RouteBadge.CROSSWALK),
        alertType = RouteAlertType.CROSSWALK,
        guidanceType = RouteGuidanceType.CROSSWALK,
        guidanceFeatures = listOf(RouteGuidanceFeature.SIGNAL),
    ),
    CROSSWALK_AUDIO(
        defaultInstruction = "Audio signal crosswalk ahead.",
        defaultBadges = listOf(RouteBadge.CROSSWALK),
        alertType = RouteAlertType.CROSSWALK,
        guidanceType = RouteGuidanceType.CROSSWALK,
        guidanceFeatures = listOf(RouteGuidanceFeature.AUDIO_SIGNAL),
    ),
    LOW_SLOPE(
        defaultInstruction = "Low slope ahead.",
        defaultBadges = listOf(RouteBadge.LOW_SLOPE),
        guidanceType = RouteGuidanceType.LOW_SLOPE,
    ),
    MIDDLE_SLOPE(
        defaultInstruction = "Slope ahead.",
        defaultBadges = listOf(RouteBadge.MIDDLE_SLOPE),
        alertType = RouteAlertType.MIDDLE_SLOPE,
        guidanceType = RouteGuidanceType.MIDDLE_SLOPE,
    ),
    STAIR(
        defaultInstruction = "Stairs ahead.",
        defaultBadges = listOf(RouteBadge.STAIR),
        alertType = RouteAlertType.STAIR,
        guidanceType = RouteGuidanceType.STAIR,
    ),
    NARROW_SIDEWALK(
        defaultInstruction = "Narrow sidewalk ahead.",
        defaultBadges = listOf(RouteBadge.NARROW_SIDEWALK),
        alertType = RouteAlertType.NARROW_SIDEWALK,
        guidanceType = RouteGuidanceType.NARROW_SIDEWALK,
    ),
    UNPAVED(
        defaultInstruction = "Unpaved path ahead.",
        defaultBadges = listOf(RouteBadge.UNPAVED),
        alertType = RouteAlertType.UNPAVED,
        guidanceType = RouteGuidanceType.UNPAVED,
    ),
    BUS_STOP(
        defaultInstruction = "Bus stop ahead.",
        alertType = RouteAlertType.BUS_STOP,
        guidanceType = RouteGuidanceType.BUS_STOP,
    ),
    SUBWAY_ELEVATOR(
        defaultInstruction = "Subway elevator ahead.",
        defaultBadges = listOf(RouteBadge.ELEVATOR),
        alertType = RouteAlertType.SUBWAY_ELEVATOR,
        guidanceType = RouteGuidanceType.SUBWAY_ELEVATOR,
    ),
    ARRIVING_POINT(
        defaultInstruction = "Prepare to get off.",
        alertType = RouteAlertType.ALIGHTING_POINT,
        guidanceType = RouteGuidanceType.ARRIVING_POINT,
    ),
    DESTINATION(
        defaultInstruction = "Arrive at destination.",
        guidanceType = RouteGuidanceType.DESTINATION,
    ),
    ;

    fun instruction(features: List<RouteGuidanceFeature>): String =
        when {
            this == CROSSWALK && RouteGuidanceFeature.AUDIO_SIGNAL in features -> CROSSWALK_AUDIO.defaultInstruction
            this == CROSSWALK && RouteGuidanceFeature.SIGNAL in features -> CROSSWALK_SIGNAL.defaultInstruction
            else -> defaultInstruction
        }

    fun badges(features: List<RouteGuidanceFeature>): List<RouteBadge> =
        when {
            this == CROSSWALK && features.isNotEmpty() -> listOf(RouteBadge.CROSSWALK)
            else -> defaultBadges
        }

    fun toAlert(distanceMeters: Int): RouteAlert? =
        alertType?.let { type ->
            RouteAlert(
                type = type,
                distanceMeters = distanceMeters,
            )
        }

    companion object {
        fun fromValue(value: String?): RouteGuidanceEventType? =
            entries.firstOrNull { type ->
                type.name.equals(value?.trim(), ignoreCase = true)
            }
    }
}

private val RouteGuidanceDirection.instruction: String
    get() =
        when (this) {
            RouteGuidanceDirection.STRAIGHT -> "Continue straight."
            RouteGuidanceDirection.TURN_LEFT -> "Turn left."
            RouteGuidanceDirection.TURN_RIGHT -> "Turn right."
        }
