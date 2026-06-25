package com.ssafy.e102.eumgil.data.mock.fixture

import com.ssafy.e102.eumgil.data.route.RouteAlertDto
import com.ssafy.e102.eumgil.data.route.RouteDto
import com.ssafy.e102.eumgil.data.route.RouteLegDto
import com.ssafy.e102.eumgil.data.route.RoutePointDto
import com.ssafy.e102.eumgil.data.route.RouteSearchRequestDto
import com.ssafy.e102.eumgil.data.route.RouteSearchResponseDto
import com.ssafy.e102.eumgil.data.route.RouteSegmentDto
import com.ssafy.e102.eumgil.data.route.RouteStepDto
import java.util.Locale

object MockRouteFixtureCatalog {
    val defaultFixture: RouteFixtureTemplate =
        RouteFixtureTemplate(
            fixtureId = "busan-cityhall-to-station-demo",
            name = "Busan City Hall to Busan Station demo route",
            searchId = "rs_walk_busan_demo",
            routes =
                listOf(
                    RouteFixtureRouteTemplate(
                        routeId = "walk_rt_safe_demo",
                        routeOption = "SAFE",
                        title = "Safe Route",
                        distanceMeter = 980,
                        estimatedTimeMinute = 16,
                        riskLevel = "LOW",
                        segments =
                            listOf(
                                RouteFixtureSegmentTemplate(
                                    sequence = 1,
                                    geometryPoints =
                                        listOf(
                                            RouteFixtureGeometryPointTemplate(progress = 0.00),
                                            RouteFixtureGeometryPointTemplate(
                                                progress = 0.12,
                                                latOffset = 0.00010,
                                                lngOffset = 0.00008,
                                            ),
                                        ),
                                    distanceMeter = 150,
                                    riskLevel = "LOW",
                                    guidanceMessage = "직진 150m 구간입니다. 넓은 보행 동선을 따라 이동하세요.",
                                ),
                                RouteFixtureSegmentTemplate(
                                    sequence = 2,
                                    geometryPoints =
                                        listOf(
                                            RouteFixtureGeometryPointTemplate(
                                                progress = 0.12,
                                                latOffset = 0.00010,
                                                lngOffset = 0.00008,
                                            ),
                                            RouteFixtureGeometryPointTemplate(
                                                progress = 0.28,
                                                latOffset = 0.00020,
                                                lngOffset = 0.00018,
                                            ),
                                        ),
                                    distanceMeter = 90,
                                    riskLevel = "LOW",
                                    guidanceMessage = "부산시청역 1번 출구 엘리베이터를 이용해 다음 보행 구간으로 이동하세요.",
                                ),
                                RouteFixtureSegmentTemplate(
                                    sequence = 3,
                                    geometryPoints =
                                        listOf(
                                            RouteFixtureGeometryPointTemplate(
                                                progress = 0.28,
                                                latOffset = 0.00020,
                                                lngOffset = 0.00018,
                                            ),
                                            RouteFixtureGeometryPointTemplate(
                                                progress = 0.44,
                                                latOffset = 0.00030,
                                                lngOffset = 0.00028,
                                            ),
                                        ),
                                    distanceMeter = 62,
                                    riskLevel = "MEDIUM",
                                    guidanceMessage = "공사 구간입니다. 길이 좁으니 주변을 확인하고 천천히 지나가세요.",
                                ),
                                RouteFixtureSegmentTemplate(
                                    sequence = 4,
                                    geometryPoints =
                                        listOf(
                                            RouteFixtureGeometryPointTemplate(
                                                progress = 0.44,
                                                latOffset = 0.00030,
                                                lngOffset = 0.00028,
                                            ),
                                            RouteFixtureGeometryPointTemplate(
                                                progress = 0.60,
                                                latOffset = 0.00034,
                                                lngOffset = 0.00030,
                                            ),
                                        ),
                                    distanceMeter = 128,
                                    hasCrosswalk = true,
                                    hasSignal = true,
                                    riskLevel = "MEDIUM",
                                    guidanceMessage = "횡단보도 진입 전 신호를 확인하고 곧바로 건너세요.",
                                ),
                                RouteFixtureSegmentTemplate(
                                    sequence = 5,
                                    geometryPoints =
                                        listOf(
                                            RouteFixtureGeometryPointTemplate(
                                                progress = 0.60,
                                                latOffset = 0.00034,
                                                lngOffset = 0.00030,
                                            ),
                                            RouteFixtureGeometryPointTemplate(
                                                progress = 0.76,
                                                latOffset = 0.00024,
                                                lngOffset = 0.00020,
                                            ),
                                        ),
                                    distanceMeter = 100,
                                    hasCurbGap = true,
                                    riskLevel = "MEDIUM",
                                    guidanceMessage = "턱이 있는 보도 구간입니다. 발판 방향을 맞춰 조심해서 이동하세요.",
                                ),
                                RouteFixtureSegmentTemplate(
                                    sequence = 6,
                                    geometryPoints =
                                        listOf(
                                            RouteFixtureGeometryPointTemplate(
                                                progress = 0.76,
                                                latOffset = 0.00024,
                                                lngOffset = 0.00020,
                                            ),
                                            RouteFixtureGeometryPointTemplate(progress = 1.00),
                                        ),
                                    distanceMeter = 450,
                                    riskLevel = "LOW",
                                    guidanceMessage = "직진 450m 이동 후 목적지 입구로 진입하세요.",
                                ),
                            ),
                    ),
                    RouteFixtureRouteTemplate(
                        routeId = "walk_rt_shortest_demo",
                        routeOption = "SHORTEST",
                        title = "Shortest Route",
                        distanceMeter = 820,
                        estimatedTimeMinute = 13,
                        riskLevel = "MEDIUM",
                        segments =
                            listOf(
                                RouteFixtureSegmentTemplate(
                                    sequence = 1,
                                    geometryPoints =
                                        listOf(
                                            RouteFixtureGeometryPointTemplate(progress = 0.00),
                                            RouteFixtureGeometryPointTemplate(
                                                progress = 0.48,
                                                latOffset = -0.00008,
                                                lngOffset = -0.00012,
                                            ),
                                        ),
                                    distanceMeter = 360,
                                    hasCrosswalk = true,
                                    hasSignal = false,
                                    hasAudioSignal = false,
                                    riskLevel = "MEDIUM",
                                    guidanceMessage = "가장 짧은 횡단 구간이지만 무신호 횡단보도여서 차량을 먼저 확인하세요.",
                                ),
                                RouteFixtureSegmentTemplate(
                                    sequence = 2,
                                    geometryPoints =
                                        listOf(
                                            RouteFixtureGeometryPointTemplate(
                                                progress = 0.48,
                                                latOffset = -0.00008,
                                                lngOffset = -0.00012,
                                            ),
                                            RouteFixtureGeometryPointTemplate(progress = 1.00),
                                        ),
                                    distanceMeter = 460,
                                    hasCurbGap = true,
                                    hasCrosswalk = false,
                                    hasBrailleBlock = false,
                                    riskLevel = "MEDIUM",
                                    guidanceMessage = "턱이 있는 직선 보도 구간이라 속도를 늦추고 이동하세요.",
                                ),
                            ),
                    ),
                ),
        )
}

data class RouteFixtureTemplate(
    val fixtureId: String,
    val name: String,
    val searchId: String,
    val routes: List<RouteFixtureRouteTemplate>,
) {
    init {
        require(fixtureId.isNotBlank()) { "Route fixture id must not be blank." }
        require(name.isNotBlank()) { "Route fixture name must not be blank." }
        require(searchId.isNotBlank()) { "Route fixture search id must not be blank." }
        require(routes.isNotEmpty()) { "Route fixture requires at least one route template." }
        require(routes.map(RouteFixtureRouteTemplate::normalizedRouteOption).distinct().size == routes.size) {
            "Route fixture route options must be unique."
        }
    }

    fun resolve(request: RouteSearchRequestDto): RouteSearchResponseDto {
        val requestedOptions = request.normalizedRouteOptions()

        val resolvedRoutes =
            routes
                .filter { route ->
                    requestedOptions.isEmpty() || route.normalizedRouteOption() in requestedOptions
                }.map { route ->
                    route.toDto(request)
                }

        return RouteSearchResponseDto(
            searchId = searchId,
            routes = resolvedRoutes,
        )
    }
}

data class RouteFixtureRouteTemplate(
    val routeId: String,
    val routeOption: String,
    val title: String,
    val distanceMeter: Int,
    val estimatedTimeMinute: Int,
    val riskLevel: String,
    val segments: List<RouteFixtureSegmentTemplate>,
) {
    init {
        require(routeId.isNotBlank()) { "Route fixture route id must not be blank." }
        require(routeOption.isNotBlank()) { "Route fixture route option must not be blank." }
        require(title.isNotBlank()) { "Route fixture route title must not be blank." }
        require(distanceMeter >= 0) { "Route fixture route distance must be non-negative." }
        require(estimatedTimeMinute >= 0) { "Route fixture route estimated time must be non-negative." }
        require(segments.isNotEmpty()) { "Route fixture route requires at least one segment." }
    }

    fun normalizedRouteOption(): String = routeOption.trim().uppercase(Locale.US)

    fun toDto(request: RouteSearchRequestDto): RouteDto {
        val legacySegments = segments.map { segment -> segment.toLegacySegmentDto(request) }
        val routeGeometry = segments.toLinestring(request)
        val routeBadges = segments.toRouteBadges()

        return RouteDto(
            routeId = routeId,
            transportMode = "WALK",
            routeOption = normalizedRouteOption(),
            title = title,
            distanceMeter = distanceMeter.toDouble(),
            estimatedTimeMinute = estimatedTimeMinute,
            badges = routeBadges,
            geometry = routeGeometry,
            legs =
                listOf(
                    RouteLegDto(
                        sequence = 1,
                        type = "WALK",
                        role = "WALK_ONLY",
                        instruction = segments.first().guidanceMessage,
                        distanceMeter = distanceMeter.toDouble(),
                        estimatedTimeMinute = estimatedTimeMinute,
                        geometry = routeGeometry,
                        steps = segments.map { segment -> segment.toStepDto(request) },
                        badges = routeBadges,
                    ),
                ),
            riskLevel = riskLevel,
            segments = legacySegments,
        )
    }
}

data class RouteFixtureSegmentTemplate(
    val sequence: Int,
    val geometryPoints: List<RouteFixtureGeometryPointTemplate>,
    val distanceMeter: Int,
    val hasStairs: Boolean = false,
    val hasCurbGap: Boolean = false,
    val hasCrosswalk: Boolean = false,
    val hasSignal: Boolean = false,
    val hasAudioSignal: Boolean = false,
    val hasBrailleBlock: Boolean = false,
    val riskLevel: String,
    val guidanceMessage: String,
) {
    init {
        require(sequence > 0) { "Route fixture segment sequence must be positive." }
        require(geometryPoints.size >= 2) { "Route fixture segment requires at least two geometry points." }
        require(distanceMeter >= 0) { "Route fixture segment distance must be non-negative." }
        require(riskLevel.isNotBlank()) { "Route fixture segment risk level must not be blank." }
        require(guidanceMessage.isNotBlank()) { "Route fixture segment guidance must not be blank." }
    }

    fun toLegacySegmentDto(request: RouteSearchRequestDto): RouteSegmentDto =
        RouteSegmentDto(
            sequence = sequence,
            geometry = toLinestring(request),
            distanceMeter = distanceMeter,
            hasStairs = hasStairs,
            hasCurbGap = hasCurbGap,
            hasCrosswalk = hasCrosswalk,
            hasSignal = hasSignal,
            hasAudioSignal = hasAudioSignal,
            hasBrailleBlock = hasBrailleBlock,
            riskLevel = riskLevel,
            guidanceMessage = guidanceMessage,
        )

    fun toStepDto(request: RouteSearchRequestDto): RouteStepDto =
        RouteStepDto(
            sequence = sequence,
            instruction = guidanceMessage,
            distanceMeter = distanceMeter.toDouble(),
            geometry = toLinestring(request),
            badges = toBadgeCodes(),
            alerts = toAlerts(),
        )

    fun toLinestring(request: RouteSearchRequestDto): String = resolvePoints(request).toLinestring()

    fun resolvePoints(request: RouteSearchRequestDto): List<RoutePointDto> =
        geometryPoints.map { point -> point.resolve(start = request.startPoint, end = request.endPoint) }

    private fun toBadgeCodes(): List<String> =
        buildList {
            when (riskLevel.trim().uppercase(Locale.US)) {
                "LOW" -> add("LOW_SLOPE")
                "MEDIUM" -> add("MIDDLE_SLOPE")
            }
            if (hasStairs) add("STAIR")
            if (hasCrosswalk) add("CROSSWALK")
        }.distinct()

    private fun toAlerts(): List<RouteAlertDto> =
        buildList {
            if (hasCrosswalk) {
                add(
                    RouteAlertDto(
                        type = "CROSSWALK",
                        distanceMeter = distanceMeter / 2.0,
                    ),
                )
            }
            if (hasStairs) {
                add(
                    RouteAlertDto(
                        type = "STAIR",
                        distanceMeter = distanceMeter / 2.0,
                    ),
                )
            }
            if (hasCurbGap) {
                add(
                    RouteAlertDto(
                        type = "CURB",
                        distanceMeter = distanceMeter / 2.0,
                    ),
                )
            }
        }
}

data class RouteFixtureGeometryPointTemplate(
    val progress: Double,
    val latOffset: Double = 0.0,
    val lngOffset: Double = 0.0,
) {
    init {
        require(progress in 0.0..1.0) { "Route fixture geometry point progress must be between 0.0 and 1.0." }
    }

    fun resolve(
        start: RoutePointDto,
        end: RoutePointDto,
    ): RoutePointDto =
        RoutePointDto(
            lat = start.lat + ((end.lat - start.lat) * progress) + latOffset,
            lng = start.lng + ((end.lng - start.lng) * progress) + lngOffset,
        )
}

private fun RouteSearchRequestDto.normalizedRouteOptions(): Set<String> =
    routeOptions
        .map(String::trim)
        .filter(String::isNotBlank)
        .map { routeOption -> routeOption.uppercase(Locale.US) }
        .toSet()

private fun List<RouteFixtureSegmentTemplate>.toRouteBadges(): List<String> =
    flatMap(RouteFixtureSegmentTemplate::toStepBadgeCodes)
        .distinct()
        .ifEmpty { listOf("LOW_SLOPE") }

private fun RouteFixtureSegmentTemplate.toStepBadgeCodes(): List<String> =
    buildList {
        when (riskLevel.trim().uppercase(Locale.US)) {
            "LOW" -> add("LOW_SLOPE")
            "MEDIUM" -> add("MIDDLE_SLOPE")
        }
        if (hasStairs) add("STAIR")
        if (hasCrosswalk) add("CROSSWALK")
    }.distinct()

private fun List<RouteFixtureSegmentTemplate>.toLinestring(request: RouteSearchRequestDto): String =
    flatMapIndexed { index, segment ->
        segment.resolvePoints(request).drop(if (index == 0) 0 else 1)
    }.toLinestring()

private fun List<RoutePointDto>.toLinestring(): String =
    joinToString(
        prefix = "LINESTRING(",
        postfix = ")",
        separator = ", ",
    ) { point ->
        "${point.lng.toGeometryValue()} ${point.lat.toGeometryValue()}"
    }

private fun Double.toGeometryValue(): String = String.format(Locale.US, "%.6f", this)
