package com.ssafy.e102.eumgil.feature.map.component

import com.kakao.vectormap.label.TransformMethod
import com.ssafy.e102.eumgil.R
import com.ssafy.e102.eumgil.core.model.FacilityCategory
import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.core.model.PlaceMarkerKind
import com.ssafy.e102.eumgil.feature.map.model.MapCameraSource
import com.ssafy.e102.eumgil.feature.map.model.MapCameraTarget
import com.ssafy.e102.eumgil.feature.map.model.MapCoordinate
import com.ssafy.e102.eumgil.feature.map.model.MapMarkerCategoryType
import com.ssafy.e102.eumgil.feature.map.model.MapMarkerDisplayState
import com.ssafy.e102.eumgil.feature.map.model.MapMarkerOverlayState
import com.ssafy.e102.eumgil.feature.map.model.MapMarkerUiModel
import com.ssafy.e102.eumgil.feature.navigation.NavigationMapFocusMode
import com.ssafy.e102.eumgil.feature.navigation.NavigationMapOverlayUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KakaoMapViewportBindingsTest {
    @Test
    fun `integration state binds only when kakao key is configured outside inspection`() {
        assertEquals(
            MapIntegrationState.Unbound,
            resolveMapIntegrationState(
                hasNativeAppKey = false,
                isInspectionMode = false,
            ),
        )
        assertEquals(
            MapIntegrationState.Unbound,
            resolveMapIntegrationState(
                hasNativeAppKey = true,
                isInspectionMode = true,
            ),
        )
        assertEquals(
            MapIntegrationState.Bound(providerName = KAKAO_MAP_PROVIDER_NAME),
            resolveMapIntegrationState(
                hasNativeAppKey = true,
                isInspectionMode = false,
            ),
        )
    }

    @Test
    fun `camera render state keeps coordinates and uses source aligned zoom level`() {
        val currentLocationCamera =
            createKakaoCameraRenderState(
                MapCameraTarget(
                    center = MapCoordinate(latitude = 35.1796, longitude = 129.0756),
                    source = MapCameraSource.CURRENT_LOCATION,
                    requestId = 7L,
                ),
            )
        val destinationCamera =
            createKakaoCameraRenderState(
                MapCameraTarget(
                    center = MapCoordinate(latitude = 35.1544, longitude = 129.0596),
                    source = MapCameraSource.SEARCH_RESULT,
                    requestId = 8L,
                ),
            )
        val defaultCamera =
            createKakaoCameraRenderState(
                MapCameraTarget(
                    center = MapCoordinate(latitude = 35.1796, longitude = 129.0756),
                    source = MapCameraSource.DEFAULT_BUSAN,
                    requestId = 9L,
                ),
            )

        assertEquals(35.1796, currentLocationCamera.latitude, 0.0)
        assertEquals(129.0756, currentLocationCamera.longitude, 0.0)
        assertEquals(17, currentLocationCamera.zoomLevel)
        assertEquals(7L, currentLocationCamera.requestId)

        assertEquals(16, destinationCamera.zoomLevel)
        assertEquals(8L, destinationCamera.requestId)

        assertEquals(15, defaultCamera.zoomLevel)
        assertEquals(9L, defaultCamera.requestId)
    }

    @Test
    fun `camera debug summary keeps request source and formatted coordinate details`() {
        val summary =
            createKakaoCameraDebugSummary(
                MapCameraTarget(
                    center = MapCoordinate(latitude = 35.1796123, longitude = 129.0756412),
                    source = MapCameraSource.SEARCH_RESULT,
                    requestId = 11L,
                ),
            )

        assertEquals(
            "requestId=11 source=SEARCH_RESULT lat=35.179612 lng=129.075641 zoom=16",
            summary,
        )
    }

    @Test
    fun `explicit zoom level overrides source default zoom`() {
        val cameraState =
            createKakaoCameraRenderState(
                MapCameraTarget(
                    center = MapCoordinate(latitude = 35.1796, longitude = 129.0756),
                    source = MapCameraSource.DEFAULT_BUSAN,
                    requestId = 12L,
                    zoomLevel = 19,
                ),
            )

        assertEquals(19, cameraState.zoomLevel)
        assertEquals(12L, cameraState.requestId)
    }

    @Test
    fun `kakao camera rotation converts heading degrees to radians`() {
        assertEquals(Math.PI / 2.0, kakaoCameraRotationRadians(90.0), 0.000001)
        assertEquals(Math.PI, kakaoCameraRotationRadians(180.0), 0.000001)
        assertEquals(0.0, kakaoCameraRotationRadians(null), 0.0)
    }

    @Test
    fun `lifecycle command resumes immediately after start when lifecycle is resumed`() {
        assertEquals(
            KakaoMapLifecycleCommand.NONE,
            resolveKakaoMapLifecycleCommand(
                isLifecycleResumed = true,
                hasMapView = true,
                isAttachedToWindow = true,
                isStarted = true,
                isFinished = false,
                hasResumedLifecycle = true,
            ),
        )
        assertEquals(
            KakaoMapLifecycleCommand.RESUME,
            resolveKakaoMapLifecycleCommand(
                isLifecycleResumed = true,
                hasMapView = true,
                isAttachedToWindow = true,
                isStarted = true,
                isFinished = false,
                hasResumedLifecycle = false,
            ),
        )
        assertEquals(
            KakaoMapLifecycleCommand.PAUSE,
            resolveKakaoMapLifecycleCommand(
                isLifecycleResumed = false,
                hasMapView = true,
                isAttachedToWindow = true,
                isStarted = true,
                isFinished = false,
                hasResumedLifecycle = true,
            ),
        )
        assertEquals(
            KakaoMapLifecycleCommand.NONE,
            resolveKakaoMapLifecycleCommand(
                isLifecycleResumed = false,
                hasMapView = true,
                isAttachedToWindow = true,
                isStarted = true,
                isFinished = false,
                hasResumedLifecycle = false,
            ),
        )
    }

    @Test
    fun `marker render state keeps visible marker size stable and lifts selected marker rank`() {
        val hiddenMarker =
            MapMarkerUiModel(
                markerId = "hidden",
                name = "Hidden marker",
                coordinate = MapCoordinate(latitude = 35.1, longitude = 129.1),
                categoryType = MapMarkerCategoryType(category = FacilityCategory.OTHER),
                displayState = MapMarkerDisplayState.HIDDEN_BY_FILTER,
            )
        val visibleToilet =
            MapMarkerUiModel(
                markerId = "toilet",
                name = "Accessible toilet",
                coordinate = MapCoordinate(latitude = 35.2, longitude = 129.2),
                categoryType = MapMarkerCategoryType(category = FacilityCategory.TOILET),
            )
        val visibleElevator =
            MapMarkerUiModel(
                markerId = "elevator",
                name = "Elevator",
                coordinate = MapCoordinate(latitude = 35.3, longitude = 129.3),
                categoryType = MapMarkerCategoryType(category = FacilityCategory.ELEVATOR),
            )

        val markerStates =
            createKakaoMarkerRenderStates(
                markerOverlayState =
                    MapMarkerOverlayState(
                        loadStatus = com.ssafy.e102.eumgil.feature.map.model.MapMarkerLoadStatus.READY,
                        markers = listOf(hiddenMarker, visibleToilet, visibleElevator),
                        visibleMarkerCount = 2,
                        totalMarkerCount = 3,
                    ),
                selectedMarkerId = "elevator",
            )

        assertEquals(listOf("toilet", "elevator"), markerStates.map { it.markerId })
        assertEquals(FacilityCategory.TOILET, markerStates.first().category)
        assertEquals(FacilityCategory.ELEVATOR, markerStates.last().category)
        assertEquals(R.drawable.ic_place_restroom, markerStates.first().glyphResId)
        assertEquals(R.drawable.ic_lowvision_category_elevator, markerStates.last().glyphResId)
        assertEquals(0L, markerStates.first().rank)
        assertTrue(markerStates.last().rank > markerStates.first().rank)
        assertEquals(28, markerStates.first().sizeDp)
        assertEquals(28, markerStates.last().sizeDp)
        assertFalse(markerStates.first().isSelected)
        assertTrue(markerStates.last().isSelected)
        assertTrue(markerStates.none { it.markerId == "hidden" })
    }

    @Test
    fun `projected marker render state adds current location overlay when location is ready`() {
        val markerStates =
            createKakaoProjectedMarkerRenderStates(
                currentLocation = MapCoordinate(latitude = 35.1798, longitude = 129.0762),
                selectedDestinationCoordinate = null,
                selectedMapPinCoordinate = null,
            )

        assertEquals(listOf("current-location"), markerStates.map { it.markerId })
        assertEquals(KakaoProjectedMarkerKind.CURRENT_LOCATION, markerStates.first().kind)
        assertEquals(R.drawable.ic_map_current_location, markerStates.first().iconResId)
        assertEquals(35.1798, markerStates.first().coordinate.latitude, 0.0)
        assertEquals(129.0762, markerStates.first().coordinate.longitude, 0.0)
        assertEquals(0.5f, markerStates.first().anchorPointX)
        assertEquals(0.5f, markerStates.first().anchorPointY)
    }

    @Test
    fun `overlay current location ignores heading and renders round marker`() {
        val markerStates =
            createKakaoProjectedMarkerRenderStates(
                currentLocation = null,
                selectedDestinationCoordinate = null,
                selectedMapPinCoordinate = null,
                overlayPoints =
                    listOf(
                        MapViewportPointOverlay(
                            overlayId = "navigation-current",
                            coordinate = MapCoordinate(latitude = 35.1798, longitude = 129.0762),
                            kind = MapViewportPointKind.CURRENT_LOCATION,
                            headingDegrees = 135.0,
                        ),
                    ),
            )

        assertEquals(1, markerStates.size)
        assertEquals(R.drawable.ic_map_current_location, markerStates.first().iconResId)
        assertEquals(28, markerStates.first().sizeDp)
        assertEquals(6f, markerStates.first().zIndex)
        assertEquals(0f, markerStates.first().rotationDegrees)
        assertEquals(0, markerStates.first().translationDistanceDp)
    }

    @Test
    fun `overlay current location remains round for north heading`() {
        val markerStates =
            createKakaoProjectedMarkerRenderStates(
                currentLocation = null,
                selectedDestinationCoordinate = null,
                selectedMapPinCoordinate = null,
                overlayPoints =
                    listOf(
                        MapViewportPointOverlay(
                            overlayId = "navigation-current",
                            coordinate = MapCoordinate(latitude = 35.1798, longitude = 129.0762),
                            kind = MapViewportPointKind.CURRENT_LOCATION,
                            headingDegrees = 0.0,
                        ),
                    ),
                cameraBearingDegrees = 0.0,
            )

        assertEquals(1, markerStates.size)
        assertEquals(R.drawable.ic_map_current_location, markerStates.first().iconResId)
        assertEquals(0f, markerStates.first().rotationDegrees)
    }

    @Test
    fun `overlay current location ignores camera bearing for marker rotation`() {
        val markerStates =
            createKakaoProjectedMarkerRenderStates(
                currentLocation = null,
                selectedDestinationCoordinate = null,
                selectedMapPinCoordinate = null,
                overlayPoints =
                    listOf(
                        MapViewportPointOverlay(
                            overlayId = "navigation-current",
                            coordinate = MapCoordinate(latitude = 35.1798, longitude = 129.0762),
                            kind = MapViewportPointKind.CURRENT_LOCATION,
                            headingDegrees = 135.0,
                        ),
                    ),
                cameraBearingDegrees = 90.0,
            )

        assertEquals(1, markerStates.size)
        assertEquals(R.drawable.ic_map_current_location, markerStates.first().iconResId)
        assertEquals(0f, markerStates.first().rotationDegrees)
    }

    @Test
    fun `projected marker render state adds route origin and destination overlay points`() {
        val markerStates =
            createKakaoProjectedMarkerRenderStates(
                currentLocation = null,
                selectedDestinationCoordinate = null,
                selectedMapPinCoordinate = null,
                overlayPoints =
                    listOf(
                        MapViewportPointOverlay(
                            overlayId = "origin",
                            coordinate = MapCoordinate(latitude = 35.1798, longitude = 129.0762),
                            kind = MapViewportPointKind.ORIGIN,
                        ),
                        MapViewportPointOverlay(
                            overlayId = "destination",
                            coordinate = MapCoordinate(latitude = 35.1802, longitude = 129.0770),
                            kind = MapViewportPointKind.DESTINATION,
                        ),
                    ),
            )

        assertEquals(listOf("overlay-origin", "overlay-destination"), markerStates.map { it.markerId })
        assertEquals(
            listOf(KakaoProjectedMarkerKind.ROUTE_ORIGIN, KakaoProjectedMarkerKind.ROUTE_DESTINATION),
            markerStates.map { it.kind },
        )
        assertEquals(R.drawable.ic_navigation_rail_origin_pin, markerStates.first().iconResId)
        assertEquals(R.drawable.ic_navigation_rail_destination_pin, markerStates.last().iconResId)
    }

    @Test
    fun `projected marker render state excludes segment junction overlay points`() {
        val markerStates =
            createKakaoProjectedMarkerRenderStates(
                currentLocation = null,
                selectedDestinationCoordinate = null,
                selectedMapPinCoordinate = null,
                overlayPoints =
                    listOf(
                        MapViewportPointOverlay(
                            overlayId = "junction-walk",
                            coordinate = MapCoordinate(latitude = 35.1802, longitude = 129.0770),
                            kind = MapViewportPointKind.SEGMENT_JUNCTION,
                            tone = MapViewportOverlayTone.PRIMARY,
                        ),
                    ),
            )

        assertTrue(markerStates.isEmpty())
    }

    @Test
    fun `native overlay marker render state keeps guidance type marker token`() {
        val markerStates =
            createKakaoOverlayMarkerRenderStates(
                listOf(
                    MapViewportPointOverlay(
                        overlayId = "junction-walk",
                        coordinate = MapCoordinate(latitude = 35.1802, longitude = 129.0770),
                        kind = MapViewportPointKind.SEGMENT_JUNCTION,
                        tone = MapViewportOverlayTone.PRIMARY,
                    ),
                    MapViewportPointOverlay(
                        overlayId = "junction-transit",
                        coordinate = MapCoordinate(latitude = 35.1810, longitude = 129.0785),
                        kind = MapViewportPointKind.SEGMENT_JUNCTION,
                        tone = MapViewportOverlayTone.TERTIARY,
                    ),
                ),
            )

        assertEquals(
            listOf("overlay-junction-walk", "overlay-junction-transit"),
            markerStates.map { it.markerId },
        )
        assertTrue(markerStates.all { it.kind == KakaoOverlayMarkerKind.ROUTE_SEGMENT_JUNCTION })
        assertTrue(markerStates.all { it.sizeDp == 18 })
        assertTrue(markerStates.all { it.anchorPointX == 0.5f })
        assertTrue(markerStates.all { it.anchorPointY == 0.5f })
        assertTrue(markerStates.all { it.fillColorArgb == 0xFFFFFFFF.toInt() })
        assertTrue(markerStates.all { it.strokeColorArgb == 0xFF8C8C8E.toInt() })
    }

    @Test
    fun `native overlay marker render state adds focused guidance halo token`() {
        val markerStates =
            createKakaoOverlayMarkerRenderStates(
                listOf(
                    MapViewportPointOverlay(
                        overlayId = "navigation-focus",
                        coordinate = MapCoordinate(latitude = 35.1802, longitude = 129.0770),
                        kind = MapViewportPointKind.FOCUS_HALO,
                    ),
                ),
            )

        assertEquals(listOf("overlay-navigation-focus"), markerStates.map { it.markerId })
        assertEquals(KakaoOverlayMarkerKind.FOCUS_HALO, markerStates.single().kind)
        assertEquals(26, markerStates.single().sizeDp)
        assertEquals(0x804D8FF9.toInt(), markerStates.single().fillColorArgb)
        assertEquals(0x004D8FF9, markerStates.single().strokeColorArgb)
    }

    @Test
    fun `native overlay marker render state hides segment junction markers below route detail zoom`() {
        val markerStates =
            createKakaoOverlayMarkerRenderStates(
                overlayPoints =
                    listOf(
                        MapViewportPointOverlay(
                            overlayId = "junction-walk",
                            coordinate = MapCoordinate(latitude = 35.1802, longitude = 129.0770),
                            kind = MapViewportPointKind.SEGMENT_JUNCTION,
                            tone = MapViewportOverlayTone.PRIMARY,
                        ),
                        MapViewportPointOverlay(
                            overlayId = "bus-stop",
                            coordinate = MapCoordinate(latitude = 35.1810, longitude = 129.0785),
                            kind = MapViewportPointKind.TRANSIT_BUS_STOP,
                            transitMarker =
                                MapViewportTransitMarker(
                                    from = MapViewportTransitMarkerLeg(MapViewportTransitMarkerKind.BUS, "58-2"),
                                ),
                        ),
                    ),
                zoomLevel = ROUTE_DETAIL_OVERLAY_MIN_ZOOM_LEVEL - 1,
            )

        assertEquals(listOf("overlay-bus-stop"), markerStates.map { it.markerId })
        assertEquals(KakaoOverlayMarkerKind.TRANSIT_STOP, markerStates.single().kind)
    }

    @Test
    fun `native overlay marker render state uses transit stop and transfer marker tokens`() {
        val markerStates =
            createKakaoOverlayMarkerRenderStates(
                listOf(
                    MapViewportPointOverlay(
                        overlayId = "bus-stop",
                        coordinate = MapCoordinate(latitude = 35.1802, longitude = 129.0770),
                        kind = MapViewportPointKind.TRANSIT_BUS_STOP,
                        transitMarker =
                            MapViewportTransitMarker(
                                from = MapViewportTransitMarkerLeg(MapViewportTransitMarkerKind.BUS, "58-2"),
                            ),
                    ),
                    MapViewportPointOverlay(
                        overlayId = "subway-stop",
                        coordinate = MapCoordinate(latitude = 35.1810, longitude = 129.0785),
                        kind = MapViewportPointKind.TRANSIT_SUBWAY_STATION,
                        transitMarker =
                            MapViewportTransitMarker(
                                from = MapViewportTransitMarkerLeg(MapViewportTransitMarkerKind.SUBWAY, "1호선"),
                            ),
                    ),
                    MapViewportPointOverlay(
                        overlayId = "transfer",
                        coordinate = MapCoordinate(latitude = 35.1820, longitude = 129.0795),
                        kind = MapViewportPointKind.TRANSIT_TRANSFER,
                        transitMarker =
                            MapViewportTransitMarker(
                                from = MapViewportTransitMarkerLeg(MapViewportTransitMarkerKind.BUS, "58-2"),
                                to = MapViewportTransitMarkerLeg(MapViewportTransitMarkerKind.SUBWAY, "1호선"),
                            ),
                    ),
                ),
            )

        assertEquals(
            listOf(KakaoOverlayMarkerKind.TRANSIT_STOP, KakaoOverlayMarkerKind.TRANSIT_STOP, KakaoOverlayMarkerKind.TRANSIT_TRANSFER),
            markerStates.map { it.kind },
        )
        assertEquals(0xFF304583.toInt(), markerStates[0].fillColorArgb)
        assertEquals("BUS", markerStates[0].label)
        assertEquals(0xFFFF7F00.toInt(), markerStates[1].fillColorArgb)
        assertEquals("1", markerStates[1].label)
        assertEquals("BUS", markerStates[2].label)
        assertEquals("1", markerStates[2].secondaryLabel)
        assertEquals(0xFFFF7F00.toInt(), markerStates[2].secondaryFillColorArgb)
    }

    @Test
    fun `projected render path summary clearly identifies projected marker pipeline`() {
        val summary =
            createProjectedSegmentRenderPathDebugSummary(
                listOf(
                    KakaoProjectedMarkerOverlay(
                        markerId = "current-location",
                        kind = KakaoProjectedMarkerKind.CURRENT_LOCATION,
                        iconResId = R.drawable.ic_map_current_location,
                        screenPoint = KakaoMapScreenPoint(x = 320, y = 640),
                        anchorPointX = 0.5f,
                        anchorPointY = 0.5f,
                        sizeDp = 28,
                        zIndex = 2f,
                    ),
                ),
            )

        assertEquals(
            "renderPath=projected overlayCount=1 segmentCount=0 details=[]",
            summary,
        )
    }

    @Test
    fun `native render path summary clearly identifies native label marker pipeline`() {
        val summary =
            createNativeSegmentRenderPathDebugSummary(
                layerId = "eumgil-overlay-markers",
                markers =
                    listOf(
                        KakaoOverlayMarkerRenderState(
                            markerId = "overlay-junction-16",
                            coordinate = MapCoordinate(latitude = 35.093359, longitude = 128.854551),
                            kind = KakaoOverlayMarkerKind.ROUTE_SEGMENT_JUNCTION,
                            anchorPointX = 0.5f,
                            anchorPointY = 0.5f,
                            sizeDp = 16,
                            zIndex = 3.6f,
                            fillColorArgb = 0xFFE7832F.toInt(),
                            strokeColorArgb = 0xFFB85B16.toInt(),
                        ),
                    ),
            )

        assertEquals(
            "renderPath=native-label layer=eumgil-overlay-markers markerCount=1 segmentCount=1 details=[id=overlay-junction-16 coord=35.093359,128.854551 sizeDp=16 z=3.6 fill=0xFFE7832F stroke=0xFFB85B16]",
            summary,
        )
    }

    @Test
    fun `projected marker projection result retries when markers exist but screen projection is not ready`() {
        val projectedMarkers =
            createKakaoProjectedMarkerRenderStates(
                currentLocation = MapCoordinate(latitude = 35.1798, longitude = 129.0762),
                selectedDestinationCoordinate = null,
                selectedMapPinCoordinate = null,
            )

        val projectionResult =
            createKakaoProjectedMarkerProjectionResult(projectedMarkers) { null }

        assertTrue(projectionResult.overlays.isEmpty())
        assertTrue(projectionResult.shouldRetry)
    }

    @Test
    fun `projected marker projection result stops retrying once at least one screen point resolves`() {
        val projectedMarkers =
            createKakaoProjectedMarkerRenderStates(
                currentLocation = MapCoordinate(latitude = 35.1798, longitude = 129.0762),
                selectedDestinationCoordinate = null,
                selectedMapPinCoordinate = null,
            )

        val projectionResult =
            createKakaoProjectedMarkerProjectionResult(projectedMarkers) { coordinate ->
                KakaoMapScreenPoint(
                    x = (coordinate.latitude * 10).toInt(),
                    y = (coordinate.longitude * 10).toInt(),
                )
            }

        assertEquals(1, projectionResult.overlays.size)
        assertFalse(projectionResult.shouldRetry)
    }

    @Test
    fun `selected map pin visibility resolves true only when projected point stays inside viewport bounds`() {
        val selectedPin = MapCoordinate(latitude = 35.1798, longitude = 129.0762)

        assertEquals(
            true,
            resolveSelectedMapPinViewportVisibility(
                selectedMapPinCoordinate = selectedPin,
                viewportWidth = 1080,
                viewportHeight = 1920,
            ) { KakaoMapScreenPoint(x = 540, y = 960) },
        )
        assertEquals(
            false,
            resolveSelectedMapPinViewportVisibility(
                selectedMapPinCoordinate = selectedPin,
                viewportWidth = 1080,
                viewportHeight = 1920,
            ) { KakaoMapScreenPoint(x = 1200, y = 960) },
        )
        assertEquals(
            false,
            resolveSelectedMapPinViewportVisibility(
                selectedMapPinCoordinate = selectedPin,
                viewportWidth = 1080,
                viewportHeight = 1920,
            ) { null },
        )
        assertNull(
            resolveSelectedMapPinViewportVisibility(
                selectedMapPinCoordinate = null,
                viewportWidth = 1080,
                viewportHeight = 1920,
            ) { KakaoMapScreenPoint(x = 540, y = 960) },
        )
    }

    @Test
    fun `projected marker projection result does not wait for native segment junction markers`() {
        val currentLocation = MapCoordinate(latitude = 35.1798, longitude = 129.0762)
        val projectedMarkers =
            createKakaoProjectedMarkerRenderStates(
                currentLocation = currentLocation,
                selectedDestinationCoordinate = null,
                selectedMapPinCoordinate = null,
                overlayPoints =
                    listOf(
                        MapViewportPointOverlay(
                            overlayId = "junction",
                            coordinate = MapCoordinate(latitude = 35.1802, longitude = 129.0770),
                            kind = MapViewportPointKind.SEGMENT_JUNCTION,
                            tone = MapViewportOverlayTone.PRIMARY,
                        ),
                    ),
            )

        val projectionResult =
            createKakaoProjectedMarkerProjectionResult(projectedMarkers) { coordinate ->
                if (coordinate == currentLocation) {
                    KakaoMapScreenPoint(x = 320, y = 640)
                } else {
                    null
                }
            }

        assertEquals(1, projectionResult.overlays.size)
        assertEquals(KakaoProjectedMarkerKind.CURRENT_LOCATION, projectionResult.overlays.single().kind)
        assertFalse(projectionResult.shouldRetry)
    }

    @Test
    fun `route line render state keeps route polyline style for kakao route line layer`() {
        val routeLineStates =
            createKakaoRouteLineRenderStates(
                listOf(
                    MapViewportPolylineOverlay(
                        overlayId = "route-preview",
                        points =
                            listOf(
                                MapCoordinate(latitude = 35.1798, longitude = 129.0762),
                                MapCoordinate(latitude = 35.1802, longitude = 129.0770),
                            ),
                        style = MapViewportPolylineStyle.ROUTE_PREVIEW,
                        tone = MapViewportOverlayTone.PRIMARY,
                    ),
                ),
            )

        assertEquals(listOf("route-preview"), routeLineStates.map { it.routeLineId })
        assertEquals(2, routeLineStates.first().points.size)
        assertEquals(18f, routeLineStates.first().lineWidth, 0f)
        assertEquals(0f, routeLineStates.first().strokeWidth, 0f)
        assertEquals(0xFF006BE0.toInt(), routeLineStates.first().lineColor)
        assertEquals(0xFF006BE0.toInt(), routeLineStates.first().strokeColor)
    }

    @Test
    fun `transit detail walk route line uses the confirmed transit walk token`() {
        val routeLineStates =
            createKakaoRouteLineRenderStates(
                listOf(
                    MapViewportPolylineOverlay(
                        overlayId = "route-detail-walk",
                        points =
                            listOf(
                                MapCoordinate(latitude = 35.1798, longitude = 129.0762),
                                MapCoordinate(latitude = 35.1802, longitude = 129.0770),
                            ),
                        style = MapViewportPolylineStyle.ROUTE_PREVIEW,
                        tone = MapViewportOverlayTone.TRANSIT_WALK,
                    ),
                ),
            )

        assertEquals(0xFF99B5D1.toInt(), routeLineStates.single().lineColor)
        assertEquals(0xFF99B5D1.toInt(), routeLineStates.single().strokeColor)
    }

    @Test
    fun `transit route line uses the confirmed transit navy token`() {
        val routeLineStates =
            createKakaoRouteLineRenderStates(
                listOf(
                    MapViewportPolylineOverlay(
                        overlayId = "route-detail-transit",
                        points =
                            listOf(
                                MapCoordinate(latitude = 35.1798, longitude = 129.0762),
                                MapCoordinate(latitude = 35.1802, longitude = 129.0770),
                            ),
                        style = MapViewportPolylineStyle.ROUTE_PREVIEW,
                        tone = MapViewportOverlayTone.NAVY,
                    ),
                ),
            )

        assertEquals(0xFF005391.toInt(), routeLineStates.single().lineColor)
        assertEquals(0xFF005391.toInt(), routeLineStates.single().strokeColor)
    }

    @Test
    fun `route camera render state fits only projection-included route geometry`() {
        val cameraState =
            createKakaoRouteCameraRenderState(
                MapViewportOverlayState(
                    points =
                        listOf(
                            MapViewportPointOverlay(
                                overlayId = "origin",
                                coordinate = MapCoordinate(latitude = 35.0, longitude = 129.0),
                                kind = MapViewportPointKind.ORIGIN,
                                includeInProjection = false,
                            ),
                        ),
                    polylines =
                        listOf(
                            MapViewportPolylineOverlay(
                                overlayId = "route",
                                points =
                                    listOf(
                                        MapCoordinate(latitude = 35.1, longitude = 129.1),
                                        MapCoordinate(latitude = 35.2, longitude = 129.2),
                                    ),
                                style = MapViewportPolylineStyle.ROUTE_BASELINE,
                                tone = MapViewportOverlayTone.PRIMARY,
                            ),
                        ),
                ),
            )

        requireNotNull(cameraState)
        assertEquals(
            listOf(
                MapCoordinate(latitude = 35.1, longitude = 129.1),
                MapCoordinate(latitude = 35.2, longitude = 129.2),
            ),
            cameraState.points,
        )
    }

    @Test
    fun `route camera render state is null when projection includes only a single focus coordinate`() {
        val cameraState =
            createKakaoRouteCameraRenderState(
                MapViewportOverlayState(
                    points =
                        listOf(
                            MapViewportPointOverlay(
                                overlayId = "focus",
                                coordinate = MapCoordinate(latitude = 35.1798, longitude = 129.0762),
                                kind = MapViewportPointKind.FOCUS_HALO,
                            ),
                        ),
                    polylines =
                        listOf(
                            MapViewportPolylineOverlay(
                                overlayId = "focused",
                                points =
                                    listOf(
                                        MapCoordinate(latitude = 35.1798, longitude = 129.0762),
                                        MapCoordinate(latitude = 35.1802, longitude = 129.0770),
                                    ),
                                style = MapViewportPolylineStyle.FOCUSED_SEGMENT,
                                tone = MapViewportOverlayTone.PRIMARY,
                                includeInProjection = false,
                            ),
                        ),
                ),
            )

        assertNull(cameraState)
    }

    @Test
    fun `focused navigation route camera uses projection target without route fit camera`() {
        val overlayState =
            createNavigationViewportOverlayState(
                NavigationMapOverlayUiState(
                    isDisplayable = true,
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

        assertTrue(overlayState.fitToProjection)
        assertNull(createKakaoRouteCameraRenderState(overlayState))
    }

    @Test
    fun `marker render state keeps only kakao facility markers`() {
        val markerStates =
            createKakaoMarkerRenderStates(
                markerOverlayState =
                    MapMarkerOverlayState(
                        loadStatus = com.ssafy.e102.eumgil.feature.map.model.MapMarkerLoadStatus.READY,
                        markers =
                            listOf(
                                MapMarkerUiModel(
                                    markerId = "toilet",
                                    name = "Accessible toilet",
                                    coordinate = MapCoordinate(latitude = 35.2, longitude = 129.2),
                                    categoryType = MapMarkerCategoryType(category = FacilityCategory.TOILET),
                                ),
                            ),
                        visibleMarkerCount = 1,
                        totalMarkerCount = 1,
                    ),
                selectedMarkerId = null,
            )

        assertEquals(listOf("toilet"), markerStates.map { it.markerId })
        assertEquals(R.drawable.ic_place_restroom, markerStates.last().glyphResId)
        assertEquals(0L, markerStates.last().rank)
        assertEquals("toilet", markerStates.last().clickTargetId)
        assertEquals(0.5f, markerStates.last().anchorPointX)
        assertEquals(0.5f, markerStates.last().anchorPointY)
    }

    @Test
    fun `marker render state keeps native label sizing and anchor metadata for facilities`() {
        val markerStates =
            createKakaoMarkerRenderStates(
                markerOverlayState =
                    MapMarkerOverlayState(
                        loadStatus = com.ssafy.e102.eumgil.feature.map.model.MapMarkerLoadStatus.READY,
                        markers =
                            listOf(
                                MapMarkerUiModel(
                                    markerId = "hidden",
                                    name = "Hidden marker",
                                    coordinate = MapCoordinate(latitude = 35.18, longitude = 129.07),
                                    categoryType = MapMarkerCategoryType(category = FacilityCategory.OTHER),
                                    displayState = MapMarkerDisplayState.HIDDEN_BY_FILTER,
                                ),
                                MapMarkerUiModel(
                                    markerId = "toilet",
                                    name = "Accessible toilet",
                                    coordinate = MapCoordinate(latitude = 35.19, longitude = 129.08),
                                    categoryType = MapMarkerCategoryType(category = FacilityCategory.TOILET),
                                ),
                                MapMarkerUiModel(
                                    markerId = "braille",
                                    name = "Braille blocks",
                                    coordinate = MapCoordinate(latitude = 35.2, longitude = 129.09),
                                    categoryType = MapMarkerCategoryType(category = FacilityCategory.BRAILLE_BLOCK),
                                ),
                            ),
                        visibleMarkerCount = 2,
                        totalMarkerCount = 3,
                    ),
                selectedMarkerId = "braille",
            )

        assertEquals(listOf("toilet", "braille"), markerStates.map { it.markerId })
        assertEquals(28, markerStates.first().sizeDp)
        assertEquals(30, markerStates.last().sizeDp)
        assertEquals(0.5f, markerStates.first().anchorPointX)
        assertEquals(0.5f, markerStates.first().anchorPointY)
        assertEquals(0.5f, markerStates.last().anchorPointX)
        assertEquals(0.5f, markerStates.last().anchorPointY)
        assertFalse(markerStates.first().isSelected)
        assertTrue(markerStates.last().isSelected)
        assertEquals(FacilityCategory.BRAILLE_BLOCK, markerStates.last().category)
        assertTrue(markerStates.none { it.markerId == "hidden" })
    }

    @Test
    fun `facility glyph mapping uses dedicated tourist icon for tourist categories`() {
        assertEquals(
            listOf(
                R.drawable.ic_place_restaurant,
                R.drawable.ic_place_charging,
                R.drawable.ic_place_healthcare,
                R.drawable.ic_place_tourist_spot,
                R.drawable.ic_place_tourist_spot,
                R.drawable.ic_map_selected_pin_blue,
            ),
            listOf(
                facilityMarkerGlyphResId(FacilityCategory.RESTAURANT),
                facilityMarkerGlyphResId(FacilityCategory.CHARGING_STATION),
                facilityMarkerGlyphResId(FacilityCategory.HEALTHCARE),
                facilityMarkerGlyphResId(FacilityCategory.TOURIST_SPOT),
                facilityMarkerGlyphResId(FacilityCategory.TOURIST_ATTRACTION),
                facilityMarkerGlyphResId(FacilityCategory.OTHER),
            ),
        )
    }

    @Test
    fun `facility glyph mapping uses accessibility icon and default pin when marker context requires it`() {
        assertEquals(
            R.drawable.ic_accessibility_tag_accessible_toilet,
            facilityMarkerGlyphResId(
                category = FacilityCategory.OTHER,
                selectedFilterCategory = FacilityCategory.TOILET,
            ),
        )
        assertEquals(
            R.drawable.ic_accessibility_tag_elevator,
            facilityMarkerGlyphResId(
                category = FacilityCategory.OTHER,
                selectedFilterCategory = FacilityCategory.ELEVATOR,
            ),
        )
        assertEquals(
            R.drawable.ic_accessibility_tag_charging_station,
            facilityMarkerGlyphResId(
                category = FacilityCategory.OTHER,
                selectedFilterCategory = FacilityCategory.CHARGING_STATION,
            ),
        )
        assertEquals(
            R.drawable.ic_map_selected_pin_blue,
            facilityMarkerGlyphResId(category = FacilityCategory.OTHER),
        )
        assertEquals(
            R.drawable.ic_place_bus,
            facilityMarkerGlyphResId(
                category = FacilityCategory.OTHER,
                markerKind = PlaceMarkerKind.BUS_STOP,
            ),
        )
        assertEquals(
            R.drawable.ic_place_subway,
            facilityMarkerGlyphResId(
                category = FacilityCategory.OTHER,
                markerKind = PlaceMarkerKind.SUBWAY_STATION,
            ),
        )
    }

    @Test
    fun `renderer failure keeps exception type and sanitized message for fallback`() {
        val failure =
            createKakaoRendererFailure(
                IllegalStateException("native engine unavailable"),
            )

        assertEquals("IllegalStateException", failure.reasonLabel)
        assertEquals("native engine unavailable", failure.detailMessage)
        assertEquals(
            "IllegalStateException: native engine unavailable",
            failure.debugSummary,
        )
    }

    @Test
    fun `renderer failure falls back when sdk error message is blank`() {
        val failure =
            createKakaoRendererFailure(
                IllegalArgumentException("   "),
            )

        assertEquals("IllegalArgumentException", failure.reasonLabel)
        assertEquals(KAKAO_RENDERER_ERROR_DETAIL_FALLBACK, failure.detailMessage)
        assertEquals(
            "IllegalArgumentException: $KAKAO_RENDERER_ERROR_DETAIL_FALLBACK",
            failure.debugSummary,
        )
    }

    @Test
    fun `renderer recovery policy allows one automatic restart for retryable startup failures only`() {
        val policyMethod =
            Class
                .forName("com.ssafy.e102.eumgil.feature.map.component.KakaoMapViewportBindingsKt")
                .getDeclaredMethod(
                    "shouldAutoRestartKakaoRenderer",
                    KakaoRendererFailure::class.java,
                    Int::class.javaPrimitiveType,
                )

        val timeoutFailure = createKakaoRendererTimeoutFailure()
        val destroyedFailure = createKakaoRendererDestroyedFailure()
        val genericFailure =
            createKakaoRendererFailure(
                IllegalStateException("renderer resume failed"),
            )

        assertTrue(policyMethod.invoke(null, timeoutFailure, 0) as Boolean)
        assertTrue(policyMethod.invoke(null, destroyedFailure, 0) as Boolean)
        assertFalse(policyMethod.invoke(null, timeoutFailure, 1) as Boolean)
        assertFalse(policyMethod.invoke(null, genericFailure, 0) as Boolean)
    }

    @Test
    fun `renderer loading phase switches to retry state after automatic recovery starts`() {
        assertEquals(
            KakaoRendererLoadingPhase.INITIALIZING,
            resolveKakaoRendererLoadingPhase(
                attemptedAutomaticRecoveryCount = 0,
            ),
        )
        assertEquals(
            KakaoRendererLoadingPhase.AUTOMATIC_RETRY,
            resolveKakaoRendererLoadingPhase(
                attemptedAutomaticRecoveryCount = 1,
            ),
        )
    }

    @Test
    fun `unexpected renderer destroy preserves existing retryable failure`() {
        val timeoutFailure = createKakaoRendererTimeoutFailure()

        val resolvedFailure =
            resolveKakaoRendererFailureAfterUnexpectedDestroy(
                existingFailure = timeoutFailure,
            )

        assertEquals(timeoutFailure, resolvedFailure)
    }

    @Test
    fun `unexpected renderer destroy creates retryable failure when no failure exists yet`() {
        val resolvedFailure =
            resolveKakaoRendererFailureAfterUnexpectedDestroy(
                existingFailure = null,
            )

        assertEquals(KAKAO_RENDERER_DESTROYED_REASON_LABEL, resolvedFailure.reasonLabel)
        assertEquals(KAKAO_RENDERER_DESTROYED_DETAIL_FALLBACK, resolvedFailure.detailMessage)
    }

    @Test
    fun `marker debug summary keeps overlay counts and current selection`() {
        val markerStates =
            createKakaoMarkerRenderStates(
                markerOverlayState =
                    MapMarkerOverlayState(
                        loadStatus = com.ssafy.e102.eumgil.feature.map.model.MapMarkerLoadStatus.READY,
                        markers =
                            listOf(
                                MapMarkerUiModel(
                                    markerId = "toilet",
                                    name = "Accessible toilet",
                                    coordinate = MapCoordinate(latitude = 35.2, longitude = 129.2),
                                    categoryType = MapMarkerCategoryType(category = FacilityCategory.TOILET),
                                ),
                                MapMarkerUiModel(
                                    markerId = "elevator",
                                    name = "Elevator",
                                    coordinate = MapCoordinate(latitude = 35.3, longitude = 129.3),
                                    categoryType = MapMarkerCategoryType(category = FacilityCategory.ELEVATOR),
                                    displayState = MapMarkerDisplayState.HIDDEN_BY_FILTER,
                                ),
                            ),
                        visibleMarkerCount = 1,
                        totalMarkerCount = 2,
                    ),
                selectedMarkerId = "toilet",
            )

        val summary =
            createKakaoMarkerDebugSummary(
                markerOverlayState =
                    MapMarkerOverlayState(
                        loadStatus = com.ssafy.e102.eumgil.feature.map.model.MapMarkerLoadStatus.READY,
                        markers =
                            listOf(
                                MapMarkerUiModel(
                                    markerId = "toilet",
                                    name = "Accessible toilet",
                                    coordinate = MapCoordinate(latitude = 35.2, longitude = 129.2),
                                    categoryType = MapMarkerCategoryType(category = FacilityCategory.TOILET),
                                ),
                                MapMarkerUiModel(
                                    markerId = "elevator",
                                    name = "Elevator",
                                    coordinate = MapCoordinate(latitude = 35.3, longitude = 129.3),
                                    categoryType = MapMarkerCategoryType(category = FacilityCategory.ELEVATOR),
                                    displayState = MapMarkerDisplayState.HIDDEN_BY_FILTER,
                                ),
                            ),
                        visibleMarkerCount = 1,
                        totalMarkerCount = 2,
                    ),
                renderedMarkers = markerStates,
                selectedMarkerId = "toilet",
            )

        assertEquals("total=2 visible=1 rendered=1 selected=toilet", summary)
    }

    @Test
    fun `selected map pin is rendered as a projected marker with dedicated icon and highest z index`() {
        val markerStates =
            createKakaoProjectedMarkerRenderStates(
                currentLocation = null,
                selectedDestinationCoordinate = null,
                selectedMapPinCoordinate = MapCoordinate(latitude = 35.1775, longitude = 129.0771),
            )

        assertEquals(listOf("selected-map-pin"), markerStates.map { it.markerId })
        assertEquals(R.drawable.ic_map_selected_pin_blue, markerStates.first().iconResId)
        assertEquals(KakaoProjectedMarkerKind.SELECTED_MAP_PIN, markerStates.first().kind)
        assertEquals(0.5f, markerStates.first().anchorPointX)
        assertEquals(1.0f, markerStates.first().anchorPointY)
        assertEquals(4f, markerStates.first().zIndex)
    }

    @Test
    fun `selected destination bookmark marker is rendered when no dropped pin exists`() {
        val markerStates =
            createKakaoProjectedMarkerRenderStates(
                currentLocation = null,
                selectedDestinationCoordinate = MapCoordinate(latitude = 35.1801, longitude = 129.0822),
                selectedMapPinCoordinate = null,
            )

        assertEquals(listOf("selected-destination"), markerStates.map { it.markerId })
        assertEquals(KakaoProjectedMarkerKind.ROUTE_DESTINATION, markerStates.first().kind)
        assertEquals(R.drawable.ic_navigation_rail_destination_pin, markerStates.first().iconResId)
    }

    @Test
    fun `selected route endpoints render as distinct origin and destination projected markers`() {
        val markerStates =
            createKakaoProjectedMarkerRenderStates(
                currentLocation = null,
                selectedOriginCoordinate = MapCoordinate(latitude = 35.1798, longitude = 129.0762),
                selectedDestinationCoordinate = MapCoordinate(latitude = 35.1801, longitude = 129.0822),
                selectedMapPinCoordinate = null,
            )

        assertEquals(listOf("selected-origin", "selected-destination"), markerStates.map { it.markerId })
        assertEquals(
            listOf(KakaoProjectedMarkerKind.ROUTE_ORIGIN, KakaoProjectedMarkerKind.ROUTE_DESTINATION),
            markerStates.map { it.kind },
        )
        assertEquals(R.drawable.ic_navigation_rail_origin_pin, markerStates.first().iconResId)
        assertEquals(R.drawable.ic_navigation_rail_destination_pin, markerStates.last().iconResId)
    }

    @Test
    fun `dropped pin suppresses selected destination marker so only the newest explicit pin remains`() {
        val markerStates =
            createKakaoProjectedMarkerRenderStates(
                currentLocation = null,
                selectedDestinationCoordinate = MapCoordinate(latitude = 35.1801, longitude = 129.0822),
                selectedMapPinCoordinate = MapCoordinate(latitude = 35.1775, longitude = 129.0771),
            )

        assertEquals(listOf("selected-map-pin"), markerStates.map { it.markerId })
    }

    @Test
    fun `route polylines generate kakao direction arrow overlay markers`() {
        val markerStates =
            createKakaoOverlayMarkerRenderStates(
                overlayPoints = emptyList(),
                polylines =
                    listOf(
                        MapViewportPolylineOverlay(
                            overlayId = "route-preview",
                            points =
                                listOf(
                                    MapCoordinate(latitude = 35.1700, longitude = 129.0500),
                                    MapCoordinate(latitude = 35.1700, longitude = 129.0520),
                                ),
                            style = MapViewportPolylineStyle.ROUTE_PREVIEW,
                            tone = MapViewportOverlayTone.PRIMARY,
                        ),
                    ),
                cameraLatitude = 35.1700,
                zoomLevel = 18,
                screenDensity = 3f,
            )

        assertEquals(3, markerStates.size)
        assertTrue(markerStates.all { it.kind == KakaoOverlayMarkerKind.ROUTE_DIRECTION_ARROW })
        assertEquals("arrow-route-preview-0", markerStates.first().markerId)
        assertEquals(35.1700, markerStates.first().coordinate.latitude, 0.000001)
        assertTrue(markerStates.first().coordinate.longitude > 129.0500)
        assertTrue(markerStates.last().coordinate.longitude < 129.0520)
        assertEquals(0f, markerStates.first().rotationDegrees, 0.01f)
    }

    @Test
    fun `route polylines reduce kakao direction arrow count when the map zooms out`() {
        val polyline =
            listOf(
                MapViewportPolylineOverlay(
                    overlayId = "route-preview",
                    points =
                        listOf(
                            MapCoordinate(latitude = 35.1700, longitude = 129.0500),
                            MapCoordinate(latitude = 35.1700, longitude = 129.0520),
                        ),
                    style = MapViewportPolylineStyle.ROUTE_PREVIEW,
                    tone = MapViewportOverlayTone.PRIMARY,
                ),
            )

        val zoomedIn =
            createKakaoOverlayMarkerRenderStates(
                overlayPoints = emptyList(),
                polylines = polyline,
                cameraLatitude = 35.1700,
                zoomLevel = 18,
                screenDensity = 3f,
            )
        val zoomedOut =
            createKakaoOverlayMarkerRenderStates(
                overlayPoints = emptyList(),
                polylines = polyline,
                cameraLatitude = 35.1700,
                zoomLevel = 17,
                screenDensity = 3f,
            )

        assertTrue(zoomedIn.size > zoomedOut.size)
        assertEquals(3, zoomedIn.size)
        assertEquals(1, zoomedOut.size)
    }

    @Test
    fun `route polylines hide kakao direction arrows below route detail zoom`() {
        val markerStates =
            createKakaoOverlayMarkerRenderStates(
                overlayPoints = emptyList(),
                polylines =
                    listOf(
                        MapViewportPolylineOverlay(
                            overlayId = "route-preview",
                            points =
                                listOf(
                                    MapCoordinate(latitude = 35.1700, longitude = 129.0500),
                                    MapCoordinate(latitude = 35.1700, longitude = 129.0520),
                                ),
                            style = MapViewportPolylineStyle.ROUTE_PREVIEW,
                            tone = MapViewportOverlayTone.PRIMARY,
                        ),
                    ),
                cameraLatitude = 35.1700,
                zoomLevel = ROUTE_DETAIL_OVERLAY_MIN_ZOOM_LEVEL - 1,
                screenDensity = 3f,
            )

        assertTrue(markerStates.isEmpty())
    }

    @Test
    fun `route polylines keep a single kakao direction arrow for short routes when zoomed out`() {
        val markerStates =
            createKakaoOverlayMarkerRenderStates(
                overlayPoints = emptyList(),
                polylines =
                    listOf(
                        MapViewportPolylineOverlay(
                            overlayId = "route-preview",
                            points =
                                listOf(
                                    MapCoordinate(latitude = 35.1700, longitude = 129.0500),
                                    MapCoordinate(latitude = 35.1700, longitude = 129.0510),
                                ),
                            style = MapViewportPolylineStyle.ROUTE_PREVIEW,
                            tone = MapViewportOverlayTone.PRIMARY,
                        ),
                    ),
                cameraLatitude = 35.1700,
                zoomLevel = 17,
                screenDensity = 3f,
            )

        assertEquals(1, markerStates.size)
        assertEquals(KakaoOverlayMarkerKind.ROUTE_DIRECTION_ARROW, markerStates.single().kind)
        assertEquals(35.1700, markerStates.single().coordinate.latitude, 0.000001)
        assertTrue(markerStates.single().coordinate.longitude > 129.0500)
        assertTrue(markerStates.single().coordinate.longitude < 129.0510)
    }

    @Test
    fun `route polylines keep kakao direction arrow rotation in map absolute space`() {
        val markerStates =
            createKakaoOverlayMarkerRenderStates(
                overlayPoints = emptyList(),
                polylines =
                    listOf(
                        MapViewportPolylineOverlay(
                            overlayId = "route-preview",
                            points =
                                listOf(
                                    MapCoordinate(latitude = 35.1700, longitude = 129.0500),
                                    MapCoordinate(latitude = 35.1700, longitude = 129.0520),
                                ),
                            style = MapViewportPolylineStyle.ROUTE_PREVIEW,
                            tone = MapViewportOverlayTone.PRIMARY,
                        ),
                    ),
                cameraLatitude = 35.1700,
                zoomLevel = 18,
                cameraBearingDegrees = 90.0,
                screenDensity = 3f,
            )

        assertEquals(3, markerStates.size)
        assertEquals(0f, markerStates.first().rotationDegrees, 0.01f)
        assertEquals(0f, markerStates.last().rotationDegrees, 0.01f)
    }

    @Test
    fun `route polyline debug state keeps raw radian and converted degree camera bearing`() {
        val computation =
            createKakaoOverlayMarkerRenderComputation(
                overlayPoints = emptyList(),
                polylines =
                    listOf(
                        MapViewportPolylineOverlay(
                            overlayId = "route-preview",
                            points =
                                listOf(
                                    MapCoordinate(latitude = 35.1700, longitude = 129.0500),
                                    MapCoordinate(latitude = 35.1700, longitude = 129.0520),
                                ),
                            style = MapViewportPolylineStyle.ROUTE_PREVIEW,
                            tone = MapViewportOverlayTone.PRIMARY,
                        ),
                    ),
                cameraLatitude = 35.1700,
                zoomLevel = 18,
                cameraBearingRadians = Math.PI / 2.0,
                cameraBearingDegrees = 90.0,
                screenDensity = 3f,
            )

        assertEquals(3, computation.routeDirectionArrowDebugStates.size)
        val firstDebugState = computation.routeDirectionArrowDebugStates.first()
        assertEquals(0f, firstDebugState.segmentHeadingDegrees, 0.01f)
        assertEquals(Math.PI / 2.0, firstDebugState.cameraBearingRadians, 0.000001)
        assertEquals(90.0, firstDebugState.cameraBearingDegrees, 0.0)
        assertEquals("map-absolute", firstDebugState.rotationModel)
        assertEquals("AbsoluteRotation", firstDebugState.transformMethodName)
        assertEquals(0f, firstDebugState.finalRotationDegrees, 0.01f)
        assertTrue(
            createKakaoRouteDirectionArrowDebugSummary(firstDebugState).contains("cameraBearingRad=1.5708"),
        )
        assertTrue(
            createKakaoRouteDirectionArrowDebugSummary(firstDebugState).contains("cameraBearingDeg=90.00"),
        )
    }

    @Test
    fun `route direction arrow labels keep explicit map absolute transform mode`() {
        assertEquals(
            TransformMethod.AbsoluteRotation,
            resolveKakaoOverlayMarkerTransformMethod(KakaoOverlayMarkerKind.ROUTE_DIRECTION_ARROW),
        )
        assertNull(
            resolveKakaoOverlayMarkerTransformMethod(KakaoOverlayMarkerKind.ROUTE_SEGMENT_JUNCTION),
        )
    }

    @Test
    fun `route polylines suppress kakao direction arrows when detailed overlay is disabled`() {
        val markerStates =
            createKakaoOverlayMarkerRenderStates(
                overlayPoints = emptyList(),
                polylines =
                    listOf(
                        MapViewportPolylineOverlay(
                            overlayId = "route-preview",
                            points =
                                listOf(
                                    MapCoordinate(latitude = 35.1700, longitude = 129.0500),
                                    MapCoordinate(latitude = 35.1700, longitude = 129.0520),
                                ),
                            style = MapViewportPolylineStyle.ROUTE_PREVIEW,
                            tone = MapViewportOverlayTone.PRIMARY,
                            showDirectionArrows = false,
                        ),
                    ),
            )

        assertTrue(markerStates.isEmpty())
    }

    @Test
    fun `overlay marker partition separates direction arrows from point overlays`() {
        val partitioned =
            partitionKakaoOverlayMarkerRenderStates(
                createKakaoOverlayMarkerRenderStates(
                    overlayPoints =
                        listOf(
                            MapViewportPointOverlay(
                                overlayId = "junction",
                                coordinate = MapCoordinate(latitude = 35.1802, longitude = 129.0770),
                                kind = MapViewportPointKind.SEGMENT_JUNCTION,
                                tone = MapViewportOverlayTone.PRIMARY,
                            ),
                        ),
                    polylines =
                        listOf(
                            MapViewportPolylineOverlay(
                                overlayId = "route-preview",
                                points =
                                    listOf(
                                        MapCoordinate(latitude = 35.1700, longitude = 129.0500),
                                        MapCoordinate(latitude = 35.1700, longitude = 129.0520),
                                    ),
                                style = MapViewportPolylineStyle.ROUTE_PREVIEW,
                                tone = MapViewportOverlayTone.PRIMARY,
                            ),
                        ),
                    cameraLatitude = 35.1700,
                    zoomLevel = 18,
                    screenDensity = 3f,
                ),
            )

        assertEquals(1, partitioned.pointMarkers.size)
        assertEquals(KakaoOverlayMarkerKind.ROUTE_SEGMENT_JUNCTION, partitioned.pointMarkers.single().kind)
        assertEquals(3, partitioned.directionArrowMarkers.size)
        assertTrue(partitioned.directionArrowMarkers.all { it.kind == KakaoOverlayMarkerKind.ROUTE_DIRECTION_ARROW })
    }
}
