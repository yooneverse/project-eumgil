package com.ssafy.e102.eumgil.feature.map.component

import com.ssafy.e102.eumgil.core.model.FacilityCategory
import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.core.model.RouteRiskLevel
import com.ssafy.e102.eumgil.feature.map.model.MapCameraSource
import com.ssafy.e102.eumgil.feature.map.model.MapCameraTarget
import com.ssafy.e102.eumgil.feature.map.model.MapCoordinate
import com.ssafy.e102.eumgil.feature.map.model.MapMarkerCategoryType
import com.ssafy.e102.eumgil.feature.map.model.MapMarkerDisplayState
import com.ssafy.e102.eumgil.feature.map.model.MapMarkerLoadStatus
import com.ssafy.e102.eumgil.feature.map.model.MapMarkerOverlayState
import com.ssafy.e102.eumgil.feature.map.model.MapMarkerUiModel
import com.ssafy.e102.eumgil.feature.navigation.NavigationMapFocusMode
import com.ssafy.e102.eumgil.feature.navigation.NavigationMapOverlayUiState
import com.ssafy.e102.eumgil.feature.navigation.NavigationMapPointUiState
import com.ssafy.e102.eumgil.feature.navigation.NavigationMapSegmentUiState
import com.ssafy.e102.eumgil.feature.navigation.NavigationSegmentTravelKind
import com.ssafy.e102.eumgil.feature.navigation.NavigationTrackingMode
import com.ssafy.e102.eumgil.feature.navigation.navigationSegmentMarkerId
import com.ssafy.e102.eumgil.feature.route.RoutePreviewMapStatus
import com.ssafy.e102.eumgil.feature.route.RoutePreviewMapUiState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapViewportOverlayBindingsTest {
    @Test
    fun `marker overlay binding keeps facility markers and camera focus separate`() {
        val cameraTarget =
            MapCameraTarget(
                center = MapCoordinate(latitude = 35.1796, longitude = 129.0756),
                source = MapCameraSource.CURRENT_LOCATION,
                requestId = 11L,
            )
        val overlayState =
            createMapMarkerViewportOverlayState(
                cameraTarget = cameraTarget,
                markerOverlayState =
                    MapMarkerOverlayState(
                        loadStatus = MapMarkerLoadStatus.READY,
                        markers =
                            listOf(
                                MapMarkerUiModel(
                                    markerId = "hidden",
                                    name = "Hidden",
                                    coordinate = MapCoordinate(latitude = 35.10, longitude = 129.10),
                                    categoryType = MapMarkerCategoryType(category = FacilityCategory.OTHER),
                                    displayState = MapMarkerDisplayState.HIDDEN_BY_FILTER,
                                ),
                                MapMarkerUiModel(
                                    markerId = "toilet",
                                    name = "Accessible toilet",
                                    coordinate = MapCoordinate(latitude = 35.18, longitude = 129.07),
                                    categoryType = MapMarkerCategoryType(category = FacilityCategory.TOILET),
                                ),
                                MapMarkerUiModel(
                                    markerId = "elevator",
                                    name = "Elevator",
                                    coordinate = MapCoordinate(latitude = 35.181, longitude = 129.071),
                                    categoryType = MapMarkerCategoryType(category = FacilityCategory.ELEVATOR),
                                ),
                            ),
                        visibleMarkerCount = 2,
                        totalMarkerCount = 3,
                    ),
                selectedMarkerId = "elevator",
                currentLocation = null,
                currentLocationLabel = null,
            )

        assertEquals(cameraTarget.center, overlayState.fallbackCamera.center)
        assertTrue(overlayState.polylines.isEmpty())
        assertEquals(3, overlayState.points.size)
        assertEquals(MapViewportPointKind.CAMERA_FOCUS, overlayState.points.first().kind)
        assertEquals(listOf("toilet", "elevator"), overlayState.points.drop(1).map { it.overlayId })
        assertEquals(MapViewportPointKind.FACILITY, overlayState.points[1].kind)
        assertEquals(FacilityCategory.TOILET, overlayState.points[1].categoryType?.category)
        assertFalse(overlayState.points[1].isSelected)
        assertFalse(overlayState.points[1].includeInProjection)
        assertTrue(overlayState.points[2].isSelected)
        assertFalse(overlayState.points[2].includeInProjection)
        assertEquals("elevator", overlayState.points[2].clickTargetId)
        assertEquals(null, createKakaoRouteCameraRenderState(overlayState))
    }

    @Test
    fun `focused navigation overlay uses selected focus as camera fallback instead of default`() {
        val focusCoordinate = GeoCoordinate(latitude = 35.184, longitude = 129.091)
        val overlayState =
            createNavigationViewportOverlayState(
                mapOverlay =
                    NavigationMapOverlayUiState(
                        isDisplayable = true,
                        currentLocation =
                            NavigationMapPointUiState(
                                label = "Current",
                                coordinate = GeoCoordinate(latitude = 35.179, longitude = 129.080),
                            ),
                        focusCoordinate = focusCoordinate,
                        mapFocusMode = NavigationMapFocusMode.FOCUSED,
                        routeSegments =
                            listOf(
                                NavigationMapSegmentUiState(
                                    sequence = 1,
                                    polyline =
                                        listOf(
                                            GeoCoordinate(latitude = 35.180, longitude = 129.088),
                                            focusCoordinate,
                                        ),
                                    segmentStartCoordinate = focusCoordinate,
                                    distanceMeters = 100,
                                    riskLevel = RouteRiskLevel.LOW,
                                    guidanceMessage = "Selected step",
                                    travelKind = NavigationSegmentTravelKind.WALK,
                                    isFocused = true,
                                ),
                            ),
                    ),
            )

        assertTrue(overlayState.fitToProjection)
        assertEquals(
            MapCoordinate(latitude = focusCoordinate.latitude, longitude = focusCoordinate.longitude),
            overlayState.fallbackCamera.center,
        )
        assertTrue(
            overlayState.points.any { point ->
                point.kind == MapViewportPointKind.FOCUS_HALO &&
                    point.includeInProjection &&
                    point.coordinate == overlayState.fallbackCamera.center
            },
        )
    }

    @Test
    fun `focused navigation overlay falls back to focused polyline when focus coordinate is missing`() {
        val focusedStart = GeoCoordinate(latitude = 35.186, longitude = 129.093)
        val focusedEnd = GeoCoordinate(latitude = 35.188, longitude = 129.095)
        val overlayState =
            createNavigationViewportOverlayState(
                mapOverlay =
                    NavigationMapOverlayUiState(
                        isDisplayable = true,
                        focusCoordinate = null,
                        focusedSegmentPolyline = listOf(focusedStart, focusedEnd),
                        mapFocusMode = NavigationMapFocusMode.FOCUSED,
                        routeSegments =
                            listOf(
                                NavigationMapSegmentUiState(
                                    sequence = 1,
                                    polyline = listOf(focusedStart, focusedEnd),
                                    distanceMeters = 100,
                                    riskLevel = RouteRiskLevel.LOW,
                                    guidanceMessage = "Selected step",
                                    travelKind = NavigationSegmentTravelKind.WALK,
                                    isFocused = true,
                                ),
                            ),
                    ),
            )

        assertEquals(
            MapCoordinate(latitude = focusedStart.latitude, longitude = focusedStart.longitude),
            overlayState.fallbackCamera.center,
        )
    }

    @Test
    fun `active navigation overlay keeps follow camera north up and current location without heading`() {
        val current = GeoCoordinate(latitude = 35.1796, longitude = 129.0756)
        val overlayState =
            createNavigationViewportOverlayState(
                mapOverlay =
                    NavigationMapOverlayUiState(
                        isDisplayable = true,
                        currentLocation =
                            NavigationMapPointUiState(
                                label = "Current",
                                coordinate = current,
                            ),
                        trackingMode = NavigationTrackingMode.FOLLOW_WITH_HEADING,
                        headingDegrees = 275.0,
                        selectedRoutePolyline =
                            listOf(
                                current,
                                GeoCoordinate(latitude = 35.1796, longitude = 129.0806),
                            ),
                        activeSegmentPolyline =
                            listOf(
                                current,
                                GeoCoordinate(latitude = 35.1796, longitude = 129.0806),
                            ),
                        mapFocusMode = NavigationMapFocusMode.ACTIVE,
                    ),
        )

        assertFalse(overlayState.fitToProjection)
        assertEquals(null, overlayState.fallbackCamera.bearingDegrees)
        assertEquals(null, overlayState.points.single { point -> point.kind == MapViewportPointKind.CURRENT_LOCATION }.headingDegrees)
        assertTrue(overlayState.points.none { point -> point.kind == MapViewportPointKind.CURRENT_LOCATION_HEADING })
    }

    @Test
    fun `navigation overlay renders start preview lines without fitting camera when current location is ready`() {
        val current = GeoCoordinate(latitude = 35.1788, longitude = 129.0750)
        val origin = GeoCoordinate(latitude = 35.1796, longitude = 129.0756)
        val previewRouteStart = GeoCoordinate(latitude = 35.1796, longitude = 129.07565)
        val segmentRouteStart = GeoCoordinate(latitude = 35.1750, longitude = 129.0756)
        val overlayState =
            createNavigationViewportOverlayState(
                mapOverlay =
                    NavigationMapOverlayUiState(
                        isDisplayable = true,
                        currentLocation =
                            NavigationMapPointUiState(
                                label = "현재 위치",
                                coordinate = current,
                            ),
                        origin =
                            NavigationMapPointUiState(
                                label = "출발",
                                coordinate = origin,
                            ),
                        destination =
                            NavigationMapPointUiState(
                                label = "도착",
                                coordinate = GeoCoordinate(latitude = 35.1796, longitude = 129.0790),
                            ),
                        routeSegments =
                            listOf(
                                NavigationMapSegmentUiState(
                                    sequence = 1,
                                    polyline =
                                        listOf(
                                            segmentRouteStart,
                                            GeoCoordinate(latitude = 35.1755, longitude = 129.0756),
                                        ),
                                    segmentStartCoordinate = segmentRouteStart,
                                    distanceMeters = 160,
                                    riskLevel = RouteRiskLevel.LOW,
                                    guidanceMessage = "Start walking",
                                    travelKind = NavigationSegmentTravelKind.WALK,
                                ),
                            ),
                        selectedRoutePolyline =
                            listOf(
                                previewRouteStart,
                                GeoCoordinate(latitude = 35.1796, longitude = 129.0790),
                            ),
                        trackingMode = NavigationTrackingMode.FOLLOW_WITH_HEADING,
                        headingDegrees = 45.0,
                        mapFocusMode = NavigationMapFocusMode.ACTIVE,
                    ),
            )

        val connectorPolylines =
            overlayState.polylines.filter { polyline ->
                polyline.style == MapViewportPolylineStyle.ROUTE_CONNECTOR
            }

        assertTrue(connectorPolylines.isEmpty())
        assertFalse(overlayState.polylines.any { polyline -> polyline.overlayId.startsWith("navigation-start-connector") })
        assertTrue(
            overlayState.polylines
                .filter { polyline -> polyline.style == MapViewportPolylineStyle.ROUTE_PREVIEW }
                .none { polyline -> polyline.includeInProjection },
        )
        assertFalse(overlayState.fitToProjection)
        assertEquals(MapCoordinate(latitude = current.latitude, longitude = current.longitude), overlayState.fallbackCamera.center)
        assertEquals(null, overlayState.fallbackCamera.bearingDegrees)
        assertTrue(overlayState.points.any { point -> point.kind == MapViewportPointKind.CURRENT_LOCATION })
    }

    @Test
    fun `active navigation removes route start connector after route progress begins`() {
        val current = GeoCoordinate(latitude = 35.1792, longitude = 129.0754)
        val origin = GeoCoordinate(latitude = 35.1796, longitude = 129.0756)
        val routeStart = GeoCoordinate(latitude = 35.1750, longitude = 129.0756)
        val routeEnd = GeoCoordinate(latitude = 35.1800, longitude = 129.0756)
        val overlayState =
            createNavigationViewportOverlayState(
                mapOverlay =
                    NavigationMapOverlayUiState(
                        isDisplayable = true,
                        currentLocation = NavigationMapPointUiState(label = "현재 위치", coordinate = current),
                        origin = NavigationMapPointUiState(label = "출발", coordinate = origin),
                        destination = NavigationMapPointUiState(label = "도착", coordinate = routeEnd),
                        routeSegments =
                            listOf(
                                NavigationMapSegmentUiState(
                                    sequence = 1,
                                    polyline = listOf(routeStart, routeEnd),
                                    segmentStartCoordinate = routeStart,
                                    distanceMeters = 160,
                                    riskLevel = RouteRiskLevel.LOW,
                                    guidanceMessage = "Start walking",
                                    travelKind = NavigationSegmentTravelKind.WALK,
                                    isActive = true,
                                    isFocused = true,
                                ),
                            ),
                        selectedRoutePolyline = listOf(routeStart, routeEnd),
                        trackingMode = NavigationTrackingMode.FOLLOW_WITH_HEADING,
                        headingDegrees = 30.0,
                        mapFocusMode = NavigationMapFocusMode.ACTIVE,
                    ),
        )

        assertFalse(overlayState.fitToProjection)
        assertFalse(overlayState.polylines.any { polyline -> polyline.style == MapViewportPolylineStyle.ROUTE_CONNECTOR })
        assertTrue(overlayState.polylines.none { polyline -> polyline.style == MapViewportPolylineStyle.ROUTE_PREVIEW })
        assertTrue(overlayState.polylines.any { polyline -> polyline.style == MapViewportPolylineStyle.ROUTE_BASELINE })
        assertEquals(MapCoordinate(latitude = current.latitude, longitude = current.longitude), overlayState.fallbackCamera.center)
        assertEquals(null, overlayState.fallbackCamera.bearingDegrees)
    }

    @Test
    fun `active navigation overlay does not synthesize route bearing when phone heading is unavailable`() {
        val current = GeoCoordinate(latitude = 35.1796, longitude = 129.0756)
        val overlayState =
            createNavigationViewportOverlayState(
                mapOverlay =
                    NavigationMapOverlayUiState(
                        isDisplayable = true,
                        currentLocation =
                            NavigationMapPointUiState(
                                label = "Current",
                                coordinate = current,
                            ),
                        trackingMode = NavigationTrackingMode.FOLLOW_WITH_HEADING,
                        headingDegrees = null,
                        selectedRoutePolyline =
                            listOf(
                                current,
                                GeoCoordinate(latitude = 35.1846, longitude = 129.0756),
                            ),
                        activeSegmentPolyline =
                            listOf(
                                current,
                                GeoCoordinate(latitude = 35.1846, longitude = 129.0756),
                            ),
                        mapFocusMode = NavigationMapFocusMode.ACTIVE,
                    ),
            )

        assertFalse(overlayState.fitToProjection)
        assertEquals(null, overlayState.fallbackCamera.bearingDegrees)
        assertEquals(null, overlayState.points.single { point -> point.kind == MapViewportPointKind.CURRENT_LOCATION }.headingDegrees)
        assertTrue(overlayState.points.none { point -> point.kind == MapViewportPointKind.CURRENT_LOCATION_HEADING })
    }

    @Test
    fun `manual navigation overlay keeps current location camera instead of route overview`() {
        val current = GeoCoordinate(latitude = 35.1796, longitude = 129.0756)
        val overlayState =
            createNavigationViewportOverlayState(
                mapOverlay =
                    NavigationMapOverlayUiState(
                        isDisplayable = true,
                        currentLocation =
                            NavigationMapPointUiState(
                                label = "Current",
                                coordinate = current,
                            ),
                        trackingMode = NavigationTrackingMode.IDLE,
                        headingDegrees = null,
                        selectedRoutePolyline =
                            listOf(
                                current,
                                GeoCoordinate(latitude = 35.1796, longitude = 129.0806),
                            ),
                        activeSegmentPolyline =
                            listOf(
                                current,
                                GeoCoordinate(latitude = 35.1796, longitude = 129.0806),
                            ),
                        mapFocusMode = NavigationMapFocusMode.ACTIVE,
                    ),
            )

        assertFalse(overlayState.fitToProjection)
        assertEquals(current.latitude, overlayState.fallbackCamera.center.latitude, 0.0)
        assertEquals(current.longitude, overlayState.fallbackCamera.center.longitude, 0.0)
        assertEquals(null, overlayState.points.single { point -> point.kind == MapViewportPointKind.CURRENT_LOCATION }.headingDegrees)
        assertTrue(overlayState.points.none { point -> point.kind == MapViewportPointKind.CURRENT_LOCATION_HEADING })
    }

    @Test
    fun `marker overlay binding adds current location point when location is ready`() {
        val cameraTarget =
            MapCameraTarget(
                center = MapCoordinate(latitude = 35.1796, longitude = 129.0756),
                source = MapCameraSource.CURRENT_LOCATION,
                requestId = 12L,
            )
        val currentLocation = MapCoordinate(latitude = 35.1798, longitude = 129.0762)

        val overlayState =
            createMapMarkerViewportOverlayState(
                cameraTarget = cameraTarget,
                markerOverlayState =
                    MapMarkerOverlayState(
                        loadStatus = MapMarkerLoadStatus.READY,
                        markers =
                            listOf(
                                MapMarkerUiModel(
                                    markerId = "toilet",
                                    name = "Accessible toilet",
                                    coordinate = MapCoordinate(latitude = 35.18, longitude = 129.07),
                                    categoryType = MapMarkerCategoryType(category = FacilityCategory.TOILET),
                                ),
                            ),
                        visibleMarkerCount = 1,
                        totalMarkerCount = 1,
                    ),
                selectedMarkerId = null,
                currentLocation = currentLocation,
                currentLocationLabel = "현",
            )

        assertEquals(
            listOf(
                MapViewportPointKind.CAMERA_FOCUS,
                MapViewportPointKind.CURRENT_LOCATION,
                MapViewportPointKind.FACILITY,
            ),
            overlayState.points.map { it.kind },
        )
        assertEquals(currentLocation, overlayState.points[1].coordinate)
        assertEquals("현", overlayState.points[1].label)
        assertFalse(overlayState.points[1].includeInProjection)
        assertEquals("toilet", overlayState.points[2].overlayId)
        assertFalse(overlayState.points[2].includeInProjection)
        assertEquals(null, createKakaoRouteCameraRenderState(overlayState))
    }

    @Test
    fun `route preview binding exposes origin destination and preview polyline`() {
        val overlayState =
            createRoutePreviewViewportOverlayState(
                previewMap =
                    RoutePreviewMapUiState(
                        status = RoutePreviewMapStatus.READY,
                        originCoordinate = GeoCoordinate(latitude = 35.17, longitude = 129.05),
                        destinationCoordinate = GeoCoordinate(latitude = 35.18, longitude = 129.07),
                        polyline =
                            listOf(
                                GeoCoordinate(latitude = 35.17, longitude = 129.05),
                                GeoCoordinate(latitude = 35.175, longitude = 129.06),
                                GeoCoordinate(latitude = 35.18, longitude = 129.07),
                            ),
                    ),
                routeTone = MapViewportOverlayTone.PRIMARY,
            )

        assertEquals(2, overlayState.points.size)
        assertEquals(
            listOf(MapViewportPointKind.ORIGIN, MapViewportPointKind.DESTINATION),
            overlayState.points.map { it.kind },
        )
        assertEquals(listOf("출발", "도착"), overlayState.points.map { it.label })
        assertEquals(1, overlayState.polylines.size)
        assertEquals(MapViewportPolylineStyle.ROUTE_PREVIEW, overlayState.polylines.first().style)
        assertEquals(MapViewportOverlayTone.PRIMARY, overlayState.polylines.first().tone)
        assertTrue(overlayState.polylines.first().includeInProjection)
        assertTrue(overlayState.polylines.first().showDirectionArrows)
    }

    @Test
    fun `route preview marks current-location origin and draws connector to route start`() {
        val overlayState =
            createRoutePreviewViewportOverlayState(
                previewMap =
                    RoutePreviewMapUiState(
                        status = RoutePreviewMapStatus.READY,
                        originCoordinate = GeoCoordinate(latitude = 35.1700, longitude = 129.0500),
                        destinationCoordinate = GeoCoordinate(latitude = 35.1800, longitude = 129.0700),
                        polyline =
                            listOf(
                                GeoCoordinate(latitude = 35.1710, longitude = 129.0520),
                                GeoCoordinate(latitude = 35.1750, longitude = 129.0600),
                                GeoCoordinate(latitude = 35.1800, longitude = 129.0700),
                            ),
                    ),
                originIsCurrentLocation = true,
            )

        assertEquals(MapViewportPointKind.CURRENT_LOCATION, overlayState.points.first().kind)
        assertEquals("출발", overlayState.points.first().label)
        assertEquals("현재 위치 출발", overlayState.points.first().contentDescription)
        assertEquals("route-origin-connector", overlayState.polylines.first().overlayId)
        assertEquals(MapViewportPolylineStyle.ROUTE_CONNECTOR, overlayState.polylines.first().style)
        assertFalse(overlayState.polylines.first().showDirectionArrows)
        assertEquals(MapViewportPolylineStyle.ROUTE_PREVIEW, overlayState.polylines.last().style)
    }

    @Test
    fun `route preview binding can render transit detail walk and transit polylines separately`() {
        val detailPolylines =
            listOf(
                MapViewportPolylineOverlay(
                    overlayId = "route-detail-walk",
                    points =
                        listOf(
                            MapCoordinate(latitude = 35.1700, longitude = 129.0500),
                            MapCoordinate(latitude = 35.1710, longitude = 129.0510),
                        ),
                    style = MapViewportPolylineStyle.ROUTE_PREVIEW,
                    tone = MapViewportOverlayTone.TRANSIT_WALK,
                ),
                MapViewportPolylineOverlay(
                    overlayId = "route-detail-transit",
                    points =
                        listOf(
                            MapCoordinate(latitude = 35.1710, longitude = 129.0510),
                            MapCoordinate(latitude = 35.1780, longitude = 129.0590),
                        ),
                    style = MapViewportPolylineStyle.ROUTE_PREVIEW,
                    tone = MapViewportOverlayTone.NAVY,
                ),
            )

        val overlayState =
            createRoutePreviewViewportOverlayState(
                previewMap =
                    RoutePreviewMapUiState(
                        status = RoutePreviewMapStatus.READY,
                        originCoordinate = GeoCoordinate(latitude = 35.1700, longitude = 129.0500),
                        destinationCoordinate = GeoCoordinate(latitude = 35.1780, longitude = 129.0590),
                        polyline = emptyList(),
                    ),
                routePolylineOverlays = detailPolylines,
            )

        assertEquals(listOf("route-detail-walk", "route-detail-transit"), overlayState.polylines.map { it.overlayId })
        assertEquals(listOf(MapViewportOverlayTone.TRANSIT_WALK, MapViewportOverlayTone.NAVY), overlayState.polylines.map { it.tone })
    }

    @Test
    fun `manual overlay camera disables projection fit so map controls are not overridden`() {
        val overlayState =
            createRoutePreviewViewportOverlayState(
                previewMap =
                    RoutePreviewMapUiState(
                        status = RoutePreviewMapStatus.READY,
                        originCoordinate = GeoCoordinate(latitude = 35.17, longitude = 129.05),
                        destinationCoordinate = GeoCoordinate(latitude = 35.18, longitude = 129.07),
                        polyline =
                            listOf(
                                GeoCoordinate(latitude = 35.17, longitude = 129.05),
                                GeoCoordinate(latitude = 35.18, longitude = 129.07),
                            ),
                    ),
            ).copy(fitToProjection = false)

        assertEquals(null, createKakaoRouteCameraRenderState(overlayState))
    }

    @Test
    fun `route waypoint markers share side panel pin assets and colors`() {
        val backdropSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/MapViewportOverlayBackdrop.kt")
                .readText()
        val kakaoBindingSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/KakaoMapViewportBindings.kt")
                .readText()

        assertTrue(
            "Compose map fallback markers should use the requested route waypoint colors.",
            backdropSource.contains("containerColor = Color(0xFF4D8FF9)") &&
                backdropSource.contains("containerColor = Color(0xFFF94D4D)"),
        )
        assertTrue(
            "Kakao map markers should reuse the same origin and destination pin assets as the side panel.",
            kakaoBindingSource.contains("iconResId = R.drawable.ic_navigation_rail_origin_pin") &&
                kakaoBindingSource.contains("iconResId = R.drawable.ic_navigation_rail_destination_pin"),
        )
    }

    @Test
    fun `map fallback route palette separates walking and transit line colors`() {
        val backdropSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/MapViewportOverlayBackdrop.kt")
                .readText()

        assertTrue(
            "Compose fallback route lines should use #99B5D1 for walking inside public transit and #005391 for public transit.",
            backdropSource.contains("transitWalk = Color(0xFF99B5D1)") &&
                backdropSource.contains("navy = Color(0xFF005391)"),
        )
    }

    @Test
    fun `navigation walking route color is distinct from public transit walking segment color`() {
        val contractSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/navigation/NavigationContract.kt")
                .readText()
        val overlaySource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/MapViewportOverlay.kt")
                .readText()
        val kakaoBindingSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/KakaoMapViewportBindings.kt")
                .readText()
        val fallbackSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/MapViewportOverlayBackdrop.kt")
                .readText()

        assertTrue(contractSource.contains("TRANSIT_WALK"))
        assertTrue(
            overlaySource.contains("NavigationSegmentTravelKind.WALK -> MapViewportOverlayTone.NAVIGATION_WALK") &&
                overlaySource.contains("NavigationSegmentTravelKind.TRANSIT_WALK -> MapViewportOverlayTone.TRANSIT_WALK"),
        )
        assertTrue(
            kakaoBindingSource.contains("MapViewportOverlayTone.NAVIGATION_WALK") &&
                kakaoBindingSource.contains("lineColor = 0xFF0061FE.toInt()"),
        )
        assertTrue(
            fallbackSource.contains("navigationWalk = Color(0xFF0061FE)") &&
                fallbackSource.contains("transitWalk = Color(0xFF99B5D1)"),
        )
    }

    @Test
    fun `route preview hides detailed guidance markers and arrows until a guidance marker is focused`() {
        val previewMap =
            RoutePreviewMapUiState(
                status = RoutePreviewMapStatus.READY,
                originCoordinate = GeoCoordinate(latitude = 35.17, longitude = 129.05),
                destinationCoordinate = GeoCoordinate(latitude = 35.18, longitude = 129.07),
                polyline =
                    listOf(
                        GeoCoordinate(latitude = 35.17, longitude = 129.05),
                        GeoCoordinate(latitude = 35.18, longitude = 129.07),
                    ),
            )
        val transitMarker =
            MapViewportPointOverlay(
                overlayId = "bus-marker",
                coordinate = MapCoordinate(latitude = 35.175, longitude = 129.06),
                kind = MapViewportPointKind.TRANSIT_BUS_STOP,
                transitMarker =
                    MapViewportTransitMarker(
                        from = MapViewportTransitMarkerLeg(kind = MapViewportTransitMarkerKind.BUS),
                    ),
            )
        val genericMarker =
            transitMarker.copy(
                overlayId = "generic-marker",
                kind = MapViewportPointKind.SEGMENT_JUNCTION,
                transitMarker = null,
            )

        val initialState =
            createRoutePreviewViewportOverlayState(
                previewMap = previewMap,
                guidanceMarkers = listOf(genericMarker, transitMarker),
                showDetailedRouteOverlay = false,
            )
        val focusedState =
            createRoutePreviewViewportOverlayState(
                previewMap = previewMap,
                guidanceMarkers = listOf(genericMarker.copy(isSelected = true), transitMarker.copy(isSelected = true)),
                focusSelectedGuidanceMarker = true,
                showDetailedRouteOverlay = true,
            )

        assertEquals(
            listOf(MapViewportPointKind.ORIGIN, MapViewportPointKind.DESTINATION),
            initialState.points.map { it.kind },
        )
        assertFalse(initialState.polylines.first().showDirectionArrows)

        assertTrue(focusedState.points.any { it.kind == MapViewportPointKind.TRANSIT_BUS_STOP })
        assertTrue(focusedState.points.any { it.kind == MapViewportPointKind.FOCUS_HALO })
        assertFalse(focusedState.points.any { it.overlayId == "generic-marker" })
        assertTrue(focusedState.polylines.first().showDirectionArrows)
    }

    @Test
    fun `navigation binding includes the focused polyline in focused projection`() {
        val overlayState =
            createNavigationViewportOverlayState(
                mapOverlay =
                    NavigationMapOverlayUiState(
                        isDisplayable = true,
                        currentLocation =
                            NavigationMapPointUiState(
                                label = "Current",
                                coordinate = GeoCoordinate(latitude = 35.176, longitude = 129.061),
                            ),
                        origin =
                            NavigationMapPointUiState(
                                label = "Origin",
                                coordinate = GeoCoordinate(latitude = 35.170, longitude = 129.050),
                            ),
                        destination =
                            NavigationMapPointUiState(
                                label = "Destination",
                                coordinate = GeoCoordinate(latitude = 35.190, longitude = 129.080),
                            ),
                        selectedRoutePolyline =
                            listOf(
                                GeoCoordinate(latitude = 35.170, longitude = 129.050),
                                GeoCoordinate(latitude = 35.180, longitude = 129.065),
                                GeoCoordinate(latitude = 35.190, longitude = 129.080),
                            ),
                        activeSegmentPolyline =
                            listOf(
                                GeoCoordinate(latitude = 35.175, longitude = 129.058),
                                GeoCoordinate(latitude = 35.178, longitude = 129.063),
                            ),
                        focusedSegmentPolyline =
                            listOf(
                                GeoCoordinate(latitude = 35.178, longitude = 129.063),
                                GeoCoordinate(latitude = 35.181, longitude = 129.068),
                            ),
                        focusCoordinate = GeoCoordinate(latitude = 35.1795, longitude = 129.0655),
                        mapFocusMode = NavigationMapFocusMode.FOCUSED,
                    ),
            )

        assertEquals(
            listOf(
                MapViewportPolylineStyle.ROUTE_BASELINE,
                MapViewportPolylineStyle.ACTIVE_SEGMENT,
                MapViewportPolylineStyle.FOCUSED_SEGMENT,
            ),
            overlayState.polylines.map { it.style },
        )
        assertFalse(overlayState.polylines.first().includeInProjection)
        assertFalse(overlayState.polylines[1].includeInProjection)
        assertFalse(overlayState.polylines[2].includeInProjection)
        assertFalse(overlayState.polylines[0].showDirectionArrows)
        assertFalse(overlayState.polylines[1].showDirectionArrows)
        assertTrue(overlayState.polylines[2].showDirectionArrows)
        assertEquals(
            listOf(
                MapViewportPointKind.CURRENT_LOCATION,
                MapViewportPointKind.ORIGIN,
                MapViewportPointKind.DESTINATION,
                MapViewportPointKind.FOCUS_HALO,
            ),
            overlayState.points.map { it.kind },
        )
        assertFalse(overlayState.points[0].includeInProjection)
        assertFalse(overlayState.points[1].includeInProjection)
        assertFalse(overlayState.points[2].includeInProjection)
        assertTrue(overlayState.points[3].includeInProjection)
        assertEquals(
            listOf(MapViewportPointKind.FOCUS_HALO),
            overlayState.points
                .filter { point -> point.includeInProjection }
                .map { point -> point.kind },
        )
    }

    @Test
    fun `focused guidance marker halo uses requested translucent blue token`() {
        val backdropSource =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/MapViewportOverlayBackdrop.kt")
                .readText()

        assertTrue(
            "The focused guidance halo should be the requested #4D8FF9 at 50 percent alpha and 26dp diameter.",
            backdropSource.contains("FocusedGuidanceMarkerHaloColor = Color(0x804D8FF9)") &&
                backdropSource.contains("FocusedGuidanceMarkerHaloRadius = 13.dp"),
        )
    }

    @Test
    fun `navigation binding keeps active projection on current location instead of the route overview`() {
        val overlayState =
            createNavigationViewportOverlayState(
                mapOverlay =
                    NavigationMapOverlayUiState(
                        isDisplayable = true,
                        currentLocation =
                            NavigationMapPointUiState(
                                label = "Current",
                                coordinate = GeoCoordinate(latitude = 35.176, longitude = 129.061),
                            ),
                        origin =
                            NavigationMapPointUiState(
                                label = "Origin",
                                coordinate = GeoCoordinate(latitude = 35.170, longitude = 129.050),
                            ),
                        destination =
                            NavigationMapPointUiState(
                                label = "Destination",
                                coordinate = GeoCoordinate(latitude = 35.190, longitude = 129.080),
                            ),
                        selectedRoutePolyline =
                            listOf(
                                GeoCoordinate(latitude = 35.170, longitude = 129.050),
                                GeoCoordinate(latitude = 35.180, longitude = 129.065),
                                GeoCoordinate(latitude = 35.190, longitude = 129.080),
                            ),
                        activeSegmentPolyline =
                            listOf(
                                GeoCoordinate(latitude = 35.175, longitude = 129.058),
                                GeoCoordinate(latitude = 35.178, longitude = 129.063),
                            ),
                        focusedSegmentPolyline =
                            listOf(
                                GeoCoordinate(latitude = 35.175, longitude = 129.058),
                                GeoCoordinate(latitude = 35.178, longitude = 129.063),
                            ),
                        focusCoordinate = GeoCoordinate(latitude = 35.1765, longitude = 129.0605),
                        trackingMode = NavigationTrackingMode.FOLLOW,
                        mapFocusMode = NavigationMapFocusMode.ACTIVE,
                    ),
            )
        val cameraState = createKakaoRouteCameraRenderState(overlayState)

        assertEquals(
            listOf(
                MapViewportPolylineStyle.ROUTE_BASELINE,
                MapViewportPolylineStyle.FOCUSED_SEGMENT,
            ),
            overlayState.polylines.map { it.style },
        )
        assertFalse(overlayState.polylines[0].includeInProjection)
        assertFalse(overlayState.polylines[1].includeInProjection)
        assertFalse(overlayState.polylines[0].showDirectionArrows)
        assertTrue(overlayState.polylines[1].showDirectionArrows)
        assertEquals(
            listOf(
                MapViewportPointKind.CURRENT_LOCATION,
                MapViewportPointKind.ORIGIN,
                MapViewportPointKind.DESTINATION,
                MapViewportPointKind.FOCUS_HALO,
            ),
            overlayState.points.map { it.kind },
        )
        assertEquals(
            listOf(false, false, false, false),
            overlayState.points.map { it.includeInProjection },
        )
        assertEquals(null, cameraState)
    }

    @Test
    fun `navigation binding hides origin marker when it is near current location`() {
        val currentCoordinate = GeoCoordinate(latitude = 35.170, longitude = 129.050)
        val originCoordinate = GeoCoordinate(latitude = 35.17003, longitude = 129.050)
        val destinationCoordinate = GeoCoordinate(latitude = 35.190, longitude = 129.080)

        val originOverlayState =
            createNavigationViewportOverlayState(
                mapOverlay =
                    NavigationMapOverlayUiState(
                        isDisplayable = true,
                        currentLocation =
                            NavigationMapPointUiState(
                                label = "Current",
                                coordinate = currentCoordinate,
                            ),
                        origin =
                            NavigationMapPointUiState(
                                label = "Origin",
                                coordinate = originCoordinate,
                            ),
                        destination =
                            NavigationMapPointUiState(
                                label = "Destination",
                                coordinate = destinationCoordinate,
                            ),
                        mapFocusMode = NavigationMapFocusMode.ACTIVE,
                    ),
            )
        val destinationOverlayState =
            createNavigationViewportOverlayState(
                mapOverlay =
                    NavigationMapOverlayUiState(
                        isDisplayable = true,
                        currentLocation =
                            NavigationMapPointUiState(
                                label = "Current",
                                coordinate = destinationCoordinate,
                            ),
                        origin =
                            NavigationMapPointUiState(
                                label = "Origin",
                                coordinate = originCoordinate,
                            ),
                        destination =
                            NavigationMapPointUiState(
                                label = "Destination",
                                coordinate = destinationCoordinate,
                            ),
                        mapFocusMode = NavigationMapFocusMode.ACTIVE,
                    ),
            )

        assertTrue(originOverlayState.points.any { point -> point.kind == MapViewportPointKind.CURRENT_LOCATION })
        assertEquals(
            listOf(MapViewportPointKind.CURRENT_LOCATION, MapViewportPointKind.DESTINATION),
            originOverlayState.points.map { point -> point.kind },
        )
        assertTrue(destinationOverlayState.points.any { point -> point.kind == MapViewportPointKind.CURRENT_LOCATION })
        assertEquals(
            listOf(MapViewportPointKind.CURRENT_LOCATION, MapViewportPointKind.ORIGIN, MapViewportPointKind.DESTINATION),
            destinationOverlayState.points.map { point -> point.kind },
        )
    }

    @Test
    fun `navigation binding adds segment start markers except the first segment and skips empty polylines`() {
        val overlayState =
            createNavigationViewportOverlayState(
                mapOverlay =
                    NavigationMapOverlayUiState(
                        isDisplayable = true,
                        routeSegments =
                            listOf(
                                NavigationMapSegmentUiState(
                                    sequence = 1,
                                    polyline =
                                        listOf(
                                            GeoCoordinate(latitude = 35.170, longitude = 129.050),
                                            GeoCoordinate(latitude = 35.175, longitude = 129.058),
                                        ),
                                    distanceMeters = 300,
                                    riskLevel = RouteRiskLevel.LOW,
                                    guidanceMessage = "First",
                                ),
                                NavigationMapSegmentUiState(
                                    sequence = 2,
                                    polyline =
                                        listOf(
                                            GeoCoordinate(latitude = 35.175, longitude = 129.058),
                                            GeoCoordinate(latitude = 35.181, longitude = 129.068),
                                        ),
                                    distanceMeters = 320,
                                    riskLevel = RouteRiskLevel.MEDIUM,
                                    guidanceMessage = "Second",
                                    travelKind = NavigationSegmentTravelKind.WALK,
                                ),
                                NavigationMapSegmentUiState(
                                    sequence = 3,
                                    polyline =
                                        listOf(
                                            GeoCoordinate(latitude = 35.181, longitude = 129.068),
                                            GeoCoordinate(latitude = 35.190, longitude = 129.080),
                                        ),
                                    distanceMeters = 400,
                                    riskLevel = RouteRiskLevel.LOW,
                                    guidanceMessage = "Third",
                                    travelKind = NavigationSegmentTravelKind.TRANSIT,
                                ),
                                NavigationMapSegmentUiState(
                                    sequence = 4,
                                    polyline = emptyList(),
                                    distanceMeters = 40,
                                    riskLevel = RouteRiskLevel.LOW,
                                    guidanceMessage = "Ignored",
                                    travelKind = NavigationSegmentTravelKind.WALK,
                                ),
                            ),
                    ),
            )

        val junctionPoints = overlayState.points.filter { it.kind == MapViewportPointKind.SEGMENT_JUNCTION }
        assertEquals(3, junctionPoints.size)
        assertEquals(
            listOf(
                MapCoordinate(latitude = 35.170, longitude = 129.050),
                MapCoordinate(latitude = 35.175, longitude = 129.058),
                MapCoordinate(latitude = 35.190, longitude = 129.080),
            ),
            junctionPoints.map { it.coordinate },
        )
        assertEquals(
            listOf(MapViewportOverlayTone.NEUTRAL, MapViewportOverlayTone.NEUTRAL, MapViewportOverlayTone.NAVY),
            junctionPoints.map { it.tone },
        )
        assertTrue(junctionPoints.none { it.includeInProjection })
    }

    @Test
    fun `navigation binding keeps single coordinate segment start markers when a start point exists`() {
        val overlayState =
            createNavigationViewportOverlayState(
                mapOverlay =
                    NavigationMapOverlayUiState(
                        isDisplayable = true,
                        routeSegments =
                            listOf(
                                NavigationMapSegmentUiState(
                                    sequence = 1,
                                    polyline =
                                        listOf(
                                            GeoCoordinate(latitude = 35.170, longitude = 129.050),
                                            GeoCoordinate(latitude = 35.175, longitude = 129.058),
                                        ),
                                    distanceMeters = 300,
                                    riskLevel = RouteRiskLevel.LOW,
                                    guidanceMessage = "Hidden first",
                                    travelKind = NavigationSegmentTravelKind.WALK,
                                ),
                                NavigationMapSegmentUiState(
                                    sequence = 2,
                                    polyline =
                                        listOf(
                                            GeoCoordinate(latitude = 35.176, longitude = 129.060),
                                        ),
                                    distanceMeters = 120,
                                    riskLevel = RouteRiskLevel.LOW,
                                    guidanceMessage = "Single walk",
                                    travelKind = NavigationSegmentTravelKind.WALK,
                                ),
                                NavigationMapSegmentUiState(
                                    sequence = 3,
                                    polyline =
                                        listOf(
                                            GeoCoordinate(latitude = 35.181, longitude = 129.068),
                                        ),
                                    distanceMeters = 400,
                                    riskLevel = RouteRiskLevel.LOW,
                                    guidanceMessage = "Single transit",
                                    travelKind = NavigationSegmentTravelKind.TRANSIT,
                                ),
                                NavigationMapSegmentUiState(
                                    sequence = 4,
                                    polyline = emptyList(),
                                    distanceMeters = 40,
                                    riskLevel = RouteRiskLevel.LOW,
                                    guidanceMessage = "Empty",
                                    travelKind = NavigationSegmentTravelKind.WALK,
                                ),
                            ),
                    ),
            )

        val junctionPoints = overlayState.points.filter { it.kind == MapViewportPointKind.SEGMENT_JUNCTION }
        assertEquals(
            listOf(
                MapCoordinate(latitude = 35.170, longitude = 129.050),
                MapCoordinate(latitude = 35.176, longitude = 129.060),
                MapCoordinate(latitude = 35.181, longitude = 129.068),
            ),
            junctionPoints.map { it.coordinate },
        )
        assertEquals(
            listOf(MapViewportOverlayTone.NEUTRAL, MapViewportOverlayTone.NEUTRAL, MapViewportOverlayTone.NAVY),
            junctionPoints.map { it.tone },
        )
    }

    @Test
    fun `navigation binding hides origin pin when route start overlaps current location`() {
        val routeStart = GeoCoordinate(latitude = 35.170, longitude = 129.050)
        val overlayState =
            createNavigationViewportOverlayState(
                mapOverlay =
                    NavigationMapOverlayUiState(
                        isDisplayable = true,
                        currentLocation = NavigationMapPointUiState(label = "current", coordinate = routeStart),
                        origin = NavigationMapPointUiState(label = "origin", coordinate = routeStart),
                        routeSegments =
                            listOf(
                                NavigationMapSegmentUiState(
                                    sequence = 1,
                                    polyline =
                                        listOf(
                                            routeStart,
                                            GeoCoordinate(latitude = 35.175, longitude = 129.058),
                                        ),
                                    distanceMeters = 300,
                                    riskLevel = RouteRiskLevel.LOW,
                                    guidanceMessage = "Start walking",
                                    travelKind = NavigationSegmentTravelKind.TRANSIT_WALK,
                                ),
                                NavigationMapSegmentUiState(
                                    sequence = 2,
                                    polyline =
                                        listOf(
                                            GeoCoordinate(latitude = 35.175, longitude = 129.058),
                                            GeoCoordinate(latitude = 35.181, longitude = 129.068),
                                        ),
                                    distanceMeters = 320,
                                    riskLevel = RouteRiskLevel.LOW,
                                    guidanceMessage = "Next crossing",
                                    travelKind = NavigationSegmentTravelKind.TRANSIT_WALK,
                                ),
                            ),
                    ),
            )

        val junctionPoints = overlayState.points.filter { it.kind == MapViewportPointKind.SEGMENT_JUNCTION }
        assertEquals(
            listOf(
                MapCoordinate(latitude = 35.175, longitude = 129.058),
            ),
            junctionPoints.map { it.coordinate },
        )
        assertFalse(overlayState.points.any { it.kind == MapViewportPointKind.ORIGIN })
        assertTrue(overlayState.points.any { it.kind == MapViewportPointKind.CURRENT_LOCATION })
    }

    @Test
    fun `navigation binding keeps transit alighting and following walk polylines without a synthetic connector`() {
        val boarding = GeoCoordinate(latitude = 35.170, longitude = 129.050)
        val strayTransitPolylineEnd = GeoCoordinate(latitude = 35.185, longitude = 129.070)
        val alighting = GeoCoordinate(latitude = 35.180, longitude = 129.060)
        val walkStart = GeoCoordinate(latitude = 35.1804, longitude = 129.0605)
        val walkEnd = GeoCoordinate(latitude = 35.183, longitude = 129.064)

        val overlayState =
            createNavigationViewportOverlayState(
                mapOverlay =
                    NavigationMapOverlayUiState(
                        isDisplayable = true,
                        routeSegments =
                            listOf(
                                NavigationMapSegmentUiState(
                                    sequence = 1,
                                    polyline = listOf(boarding, strayTransitPolylineEnd),
                                    segmentEndCoordinate = alighting,
                                    distanceMeters = 900,
                                    riskLevel = RouteRiskLevel.LOW,
                                    guidanceMessage = "Ride bus",
                                    travelKind = NavigationSegmentTravelKind.TRANSIT,
                                ),
                                NavigationMapSegmentUiState(
                                    sequence = 2,
                                    polyline = listOf(walkStart, walkEnd),
                                    distanceMeters = 200,
                                    riskLevel = RouteRiskLevel.LOW,
                                    guidanceMessage = "Walk from stop",
                                    travelKind = NavigationSegmentTravelKind.TRANSIT_WALK,
                                ),
                            ),
                    ),
            )

        val junctionPoints = overlayState.points.filter { it.kind == MapViewportPointKind.SEGMENT_JUNCTION }
        assertEquals(
            listOf(
                MapCoordinate(latitude = alighting.latitude, longitude = alighting.longitude),
                MapCoordinate(latitude = walkStart.latitude, longitude = walkStart.longitude),
            ),
            junctionPoints.map { it.coordinate },
        )
        assertEquals(MapViewportOverlayTone.NAVY, junctionPoints.first().tone)
        assertEquals(MapViewportOverlayTone.NEUTRAL, junctionPoints.last().tone)
        assertEquals(
            listOf(
                "navigation-route-segment-1",
                "navigation-route-segment-2",
            ),
            overlayState.polylines.map(MapViewportPolylineOverlay::overlayId),
        )
        assertTrue(overlayState.polylines.none { polyline -> polyline.overlayId.startsWith("navigation-transfer-connection") })
    }

    @Test
    fun `navigation binding uses fallback segment start coordinates when later segment polylines are empty`() {
        val overlayState =
            createNavigationViewportOverlayState(
                mapOverlay =
                    NavigationMapOverlayUiState(
                        isDisplayable = true,
                        routeSegments =
                            listOf(
                                NavigationMapSegmentUiState(
                                    sequence = 1,
                                    polyline =
                                        listOf(
                                            GeoCoordinate(latitude = 35.170, longitude = 129.050),
                                            GeoCoordinate(latitude = 35.175, longitude = 129.058),
                                        ),
                                    distanceMeters = 300,
                                    riskLevel = RouteRiskLevel.LOW,
                                    guidanceMessage = "First",
                                    travelKind = NavigationSegmentTravelKind.WALK,
                                ),
                                NavigationMapSegmentUiState(
                                    sequence = 2,
                                    polyline = emptyList(),
                                    segmentStartCoordinate = GeoCoordinate(latitude = 35.176, longitude = 129.060),
                                    distanceMeters = 120,
                                    riskLevel = RouteRiskLevel.LOW,
                                    guidanceMessage = "Sparse walk",
                                    travelKind = NavigationSegmentTravelKind.WALK,
                                ),
                                NavigationMapSegmentUiState(
                                    sequence = 3,
                                    polyline = emptyList(),
                                    distanceMeters = 200,
                                    riskLevel = RouteRiskLevel.LOW,
                                    guidanceMessage = "Still empty",
                                    travelKind = NavigationSegmentTravelKind.TRANSIT,
                                ),
                            ),
                    ),
            )

        val junctionPoints = overlayState.points.filter { it.kind == MapViewportPointKind.SEGMENT_JUNCTION }
        assertEquals(2, junctionPoints.size)
        assertEquals(
            listOf(
                MapCoordinate(latitude = 35.170, longitude = 129.050),
                MapCoordinate(latitude = 35.176, longitude = 129.060),
            ),
            junctionPoints.map { it.coordinate },
        )
        assertEquals(listOf(MapViewportOverlayTone.NEUTRAL, MapViewportOverlayTone.NEUTRAL), junctionPoints.map { it.tone })
    }

    @Test
    fun `navigation binding keeps single coordinate segment junctions outside active camera projection`() {
        val overlayState =
            createNavigationViewportOverlayState(
                mapOverlay =
                    NavigationMapOverlayUiState(
                        isDisplayable = true,
                        routeSegments =
                            listOf(
                                NavigationMapSegmentUiState(
                                    sequence = 1,
                                    polyline =
                                        listOf(
                                            GeoCoordinate(latitude = 35.170, longitude = 129.050),
                                            GeoCoordinate(latitude = 35.175, longitude = 129.058),
                                        ),
                                    distanceMeters = 300,
                                    riskLevel = RouteRiskLevel.LOW,
                                    guidanceMessage = "First",
                                    travelKind = NavigationSegmentTravelKind.WALK,
                                ),
                                NavigationMapSegmentUiState(
                                    sequence = 2,
                                    polyline =
                                        listOf(
                                            GeoCoordinate(latitude = 35.176, longitude = 129.060),
                                        ),
                                    distanceMeters = 120,
                                    riskLevel = RouteRiskLevel.LOW,
                                    guidanceMessage = "Single walk",
                                    travelKind = NavigationSegmentTravelKind.WALK,
                                ),
                            ),
                        mapFocusMode = NavigationMapFocusMode.ACTIVE,
                    ),
            )

        assertTrue(overlayState.points.any { it.kind == MapViewportPointKind.SEGMENT_JUNCTION })
        assertTrue(overlayState.points.none { it.kind == MapViewportPointKind.SEGMENT_JUNCTION && it.includeInProjection })
    }

    @Test
    fun `navigation binding keeps the focus halo as the only projected point in focused mode`() {
        val overlayState =
            createNavigationViewportOverlayState(
                mapOverlay =
                    NavigationMapOverlayUiState(
                        isDisplayable = true,
                        routeSegments =
                            listOf(
                                NavigationMapSegmentUiState(
                                    sequence = 1,
                                    polyline =
                                        listOf(
                                            GeoCoordinate(latitude = 35.170, longitude = 129.050),
                                            GeoCoordinate(latitude = 35.175, longitude = 129.058),
                                        ),
                                    distanceMeters = 300,
                                    riskLevel = RouteRiskLevel.LOW,
                                    guidanceMessage = "First",
                                    travelKind = NavigationSegmentTravelKind.WALK,
                                ),
                                NavigationMapSegmentUiState(
                                    sequence = 2,
                                    polyline =
                                        listOf(
                                            GeoCoordinate(latitude = 35.176, longitude = 129.060),
                                            GeoCoordinate(latitude = 35.181, longitude = 129.068),
                                        ),
                                    distanceMeters = 400,
                                    riskLevel = RouteRiskLevel.LOW,
                                    guidanceMessage = "Focused transit",
                                    travelKind = NavigationSegmentTravelKind.TRANSIT,
                                    isFocused = true,
                                ),
                            ),
                        focusCoordinate = GeoCoordinate(latitude = 35.176, longitude = 129.060),
                        mapFocusMode = NavigationMapFocusMode.FOCUSED,
                    ),
            )

        val projectionPoints = overlayState.points.filter { it.includeInProjection }

        assertEquals(
            listOf(
                MapViewportPointKind.FOCUS_HALO,
            ),
            projectionPoints.map { it.kind },
        )
        assertEquals(MapCoordinate(latitude = 35.176, longitude = 129.060), projectionPoints.first().coordinate)
        assertTrue(overlayState.polylines.none(MapViewportPolylineOverlay::includeInProjection))
        assertTrue(
            overlayState.polylines.any { polyline ->
                polyline.overlayId == "navigation-route-segment-2" && polyline.showDirectionArrows
            },
        )
    }

    @Test
    fun `navigation binding falls back to the active focus halo when current location is unavailable`() {
        val overlayState =
            createNavigationViewportOverlayState(
                mapOverlay =
                    NavigationMapOverlayUiState(
                        isDisplayable = true,
                        routeSegments =
                            listOf(
                                NavigationMapSegmentUiState(
                                    sequence = 1,
                                    polyline =
                                        listOf(
                                            GeoCoordinate(latitude = 35.170, longitude = 129.050),
                                            GeoCoordinate(latitude = 35.175, longitude = 129.058),
                                        ),
                                    distanceMeters = 300,
                                    riskLevel = RouteRiskLevel.LOW,
                                    guidanceMessage = "First",
                                    travelKind = NavigationSegmentTravelKind.WALK,
                                ),
                                NavigationMapSegmentUiState(
                                    sequence = 2,
                                    polyline = emptyList(),
                                    segmentStartCoordinate = GeoCoordinate(latitude = 35.176, longitude = 129.060),
                                    distanceMeters = 120,
                                    riskLevel = RouteRiskLevel.LOW,
                                    guidanceMessage = "Sparse walk",
                                    travelKind = NavigationSegmentTravelKind.WALK,
                                ),
                            ),
                        focusCoordinate = GeoCoordinate(latitude = 35.176, longitude = 129.060),
                        mapFocusMode = NavigationMapFocusMode.ACTIVE,
                    ),
            )

        assertEquals(
            listOf(MapViewportPointKind.FOCUS_HALO),
            overlayState.points
                .filter(MapViewportPointOverlay::includeInProjection)
                .map(MapViewportPointOverlay::kind),
        )
        assertEquals(null, createKakaoRouteCameraRenderState(overlayState))
    }

    @Test
    fun `segment junction debug summary reports generated overlays and focus projection exclusions`() {
        val mapOverlay =
            NavigationMapOverlayUiState(
                isDisplayable = true,
                currentLocation =
                    NavigationMapPointUiState(
                        label = "Current",
                        coordinate = GeoCoordinate(latitude = 35.170, longitude = 129.050),
                    ),
                origin =
                    NavigationMapPointUiState(
                        label = "Origin",
                        coordinate = GeoCoordinate(latitude = 35.170, longitude = 129.050),
                    ),
                destination =
                    NavigationMapPointUiState(
                        label = "Destination",
                        coordinate = GeoCoordinate(latitude = 35.190, longitude = 129.080),
                    ),
                routeSegments =
                    listOf(
                        NavigationMapSegmentUiState(
                            sequence = 1,
                            polyline =
                                listOf(
                                    GeoCoordinate(latitude = 35.170, longitude = 129.050),
                                    GeoCoordinate(latitude = 35.175, longitude = 129.058),
                                ),
                            distanceMeters = 300,
                            riskLevel = RouteRiskLevel.LOW,
                            guidanceMessage = "Hidden first",
                            travelKind = NavigationSegmentTravelKind.WALK,
                        ),
                        NavigationMapSegmentUiState(
                            sequence = 2,
                            polyline =
                                listOf(
                                    GeoCoordinate(latitude = 35.176, longitude = 129.060),
                                ),
                            distanceMeters = 120,
                            riskLevel = RouteRiskLevel.LOW,
                            guidanceMessage = "Single walk",
                            travelKind = NavigationSegmentTravelKind.WALK,
                        ),
                        NavigationMapSegmentUiState(
                            sequence = 3,
                            polyline =
                                listOf(
                                    GeoCoordinate(latitude = 35.181, longitude = 129.068),
                                ),
                            distanceMeters = 400,
                            riskLevel = RouteRiskLevel.LOW,
                            guidanceMessage = "Single transit",
                            travelKind = NavigationSegmentTravelKind.TRANSIT,
                        ),
                    ),
                selectedRoutePolyline =
                    listOf(
                        GeoCoordinate(latitude = 35.170, longitude = 129.050),
                        GeoCoordinate(latitude = 35.190, longitude = 129.080),
                    ),
                activeSegmentPolyline =
                    listOf(
                        GeoCoordinate(latitude = 35.170, longitude = 129.050),
                        GeoCoordinate(latitude = 35.175, longitude = 129.058),
                    ),
                focusedSegmentPolyline =
                    listOf(
                        GeoCoordinate(latitude = 35.176, longitude = 129.060),
                        GeoCoordinate(latitude = 35.181, longitude = 129.068),
                    ),
                focusCoordinate = GeoCoordinate(latitude = 35.176, longitude = 129.060),
                mapFocusMode = NavigationMapFocusMode.FOCUSED,
            )
        val overlayState = createNavigationViewportOverlayState(mapOverlay)

        val summary = createSegmentJunctionOverlayDebugSummary(mapOverlay, overlayState)

        assertTrue(summary.contains("focusMode=FOCUSED"))
        assertTrue(summary.contains("junctions=3"))
        assertTrue(
            summary.contains(
                "id=navigation-junction-0 coord=35.170000,129.050000 tone=NEUTRAL includeInProjection=false",
            ),
        )
        assertTrue(
            summary.contains(
                "id=navigation-junction-1 coord=35.176000,129.060000 tone=NEUTRAL includeInProjection=false",
            ),
        )
        assertTrue(
            summary.contains(
                "id=navigation-junction-2 coord=35.181000,129.068000 tone=NAVY includeInProjection=false",
            ),
        )
        assertTrue(summary.contains("navigation-focus:FOCUS_HALO"))
        assertTrue(summary.contains("projectionPolylines=[]"))
    }

    @Test
    fun `navigation binding colors transit segments differently from walking segments`() {
        val overlayState =
            createNavigationViewportOverlayState(
                mapOverlay =
                    NavigationMapOverlayUiState(
                        isDisplayable = true,
                        routeSegments =
                            listOf(
                                NavigationMapSegmentUiState(
                                    sequence = 1,
                                    polyline =
                                        listOf(
                                            GeoCoordinate(latitude = 35.170, longitude = 129.050),
                                            GeoCoordinate(latitude = 35.175, longitude = 129.058),
                                        ),
                                    distanceMeters = 300,
                                    riskLevel = RouteRiskLevel.LOW,
                                    guidanceMessage = "Walk",
                                    travelKind = NavigationSegmentTravelKind.TRANSIT_WALK,
                                ),
                                NavigationMapSegmentUiState(
                                    sequence = 2,
                                    polyline =
                                        listOf(
                                            GeoCoordinate(latitude = 35.175, longitude = 129.058),
                                            GeoCoordinate(latitude = 35.181, longitude = 129.068),
                                        ),
                                    distanceMeters = 320,
                                    riskLevel = RouteRiskLevel.LOW,
                                    guidanceMessage = "Transit",
                                    travelKind = NavigationSegmentTravelKind.TRANSIT,
                                ),
                            ),
                        activeSegmentPolyline =
                            listOf(
                                GeoCoordinate(latitude = 35.175, longitude = 129.058),
                                GeoCoordinate(latitude = 35.181, longitude = 129.068),
                            ),
                        focusedSegmentPolyline =
                            listOf(
                                GeoCoordinate(latitude = 35.175, longitude = 129.058),
                                GeoCoordinate(latitude = 35.181, longitude = 129.068),
                            ),
                        activeSegmentTravelKind = NavigationSegmentTravelKind.TRANSIT,
                        focusedSegmentTravelKind = NavigationSegmentTravelKind.TRANSIT,
                    ),
            )

        val baselineTones =
            overlayState.polylines
                .filter { it.style == MapViewportPolylineStyle.ROUTE_BASELINE }
                .map { it.tone }

        assertEquals(
            listOf(MapViewportOverlayTone.TRANSIT_WALK, MapViewportOverlayTone.NAVY),
            baselineTones,
        )
        assertEquals(MapViewportOverlayTone.NAVY, overlayState.polylines.last().tone)
    }

    @Test
    fun `navigation binding does not duplicate active transit line when detailed baseline segments exist`() {
        val sharedTransitPolyline =
            listOf(
                GeoCoordinate(latitude = 35.180, longitude = 129.070),
                GeoCoordinate(latitude = 35.185, longitude = 129.075),
            )
        val overlayState =
            createNavigationViewportOverlayState(
                mapOverlay =
                    NavigationMapOverlayUiState(
                        isDisplayable = true,
                        routeSegments =
                            listOf(
                                NavigationMapSegmentUiState(
                                    sequence = 1,
                                    polyline = sharedTransitPolyline,
                                    distanceMeters = 500,
                                    riskLevel = RouteRiskLevel.LOW,
                                    guidanceMessage = "Transit",
                                    travelKind = NavigationSegmentTravelKind.TRANSIT,
                                    isActive = true,
                                ),
                                NavigationMapSegmentUiState(
                                    sequence = 2,
                                    polyline = emptyList(),
                                    segmentStartCoordinate = sharedTransitPolyline.last(),
                                    distanceMeters = 0,
                                    riskLevel = RouteRiskLevel.LOW,
                                    guidanceMessage = "Alight",
                                    travelKind = NavigationSegmentTravelKind.TRANSIT,
                                    isFocused = true,
                                ),
                            ),
                        activeSegmentPolyline = sharedTransitPolyline,
                        focusedSegmentPolyline = emptyList(),
                        activeSegmentTravelKind = NavigationSegmentTravelKind.TRANSIT,
                        focusedSegmentTravelKind = NavigationSegmentTravelKind.TRANSIT,
                        focusCoordinate = sharedTransitPolyline.last(),
                        mapFocusMode = NavigationMapFocusMode.FOCUSED,
                    ),
            )

        assertEquals(
            listOf(MapViewportPolylineStyle.ROUTE_BASELINE),
            overlayState.polylines.map { it.style },
        )
        assertEquals(
            sharedTransitPolyline.map { coordinate ->
                MapCoordinate(latitude = coordinate.latitude, longitude = coordinate.longitude)
            },
            overlayState.polylines.single().points,
        )
        assertTrue(overlayState.polylines.single().showDirectionArrows)
    }

    @Test
    fun `navigation bindings preserve click targets for projected origin and overlay junction markers`() {
        val mapOverlay =
            NavigationMapOverlayUiState(
                isDisplayable = true,
                origin =
                    NavigationMapPointUiState(
                        label = "Origin",
                        coordinate = GeoCoordinate(latitude = 35.170, longitude = 129.050),
                    ),
                routeSegments =
                    listOf(
                        NavigationMapSegmentUiState(
                            sequence = 1,
                            polyline =
                                listOf(
                                    GeoCoordinate(latitude = 35.170, longitude = 129.050),
                                    GeoCoordinate(latitude = 35.175, longitude = 129.058),
                                ),
                            distanceMeters = 300,
                            riskLevel = RouteRiskLevel.LOW,
                            guidanceMessage = "Start walking",
                            travelKind = NavigationSegmentTravelKind.WALK,
                        ),
                        NavigationMapSegmentUiState(
                            sequence = 2,
                            polyline =
                                listOf(
                                    GeoCoordinate(latitude = 35.175, longitude = 129.058),
                                    GeoCoordinate(latitude = 35.181, longitude = 129.068),
                                ),
                            distanceMeters = 320,
                            riskLevel = RouteRiskLevel.LOW,
                            guidanceMessage = "Turn right",
                            travelKind = NavigationSegmentTravelKind.WALK,
                        ),
                    ),
            )
        val overlayState = createNavigationViewportOverlayState(mapOverlay)

        val projectedOriginMarker =
            createKakaoProjectedMarkerRenderStates(
                currentLocation = null,
                selectedDestinationCoordinate = null,
                selectedMapPinCoordinate = null,
                overlayPoints = overlayState.points,
            ).first { marker -> marker.markerId == "overlay-navigation-origin" }
        val overlayJunctionMarker =
            createKakaoOverlayMarkerRenderStates(
                overlayPoints = overlayState.points,
            ).first { marker -> marker.markerId == "overlay-navigation-junction-1" }

        assertEquals(navigationSegmentMarkerId(0), projectedOriginMarker.clickTargetId)
        assertEquals(navigationSegmentMarkerId(1), overlayJunctionMarker.clickTargetId)
    }
}
