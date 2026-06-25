package com.ssafy.e102.eumgil.feature.map.component

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KakaoMapViewportConfigurationTest {
    @Test
    fun `renderer fallback uses dedicated overlay instead of mock map surface`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/KakaoMapViewport.kt")
                .readText()

        assertTrue(
            "Kakao map renderer fallback should use a dedicated centered overlay instead of reusing the mock map surface.",
            source.contains("MapRendererFallbackOverlay("),
        )
        assertFalse(
            "Kakao map renderer fallback should not reuse the mock/fake map surface UI.",
            source.contains("MapFallbackSurface("),
        )
    }

    @Test
    fun `renderer fallback exposes explicit retry call to action`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/KakaoMapViewport.kt")
                .readText()

        assertTrue(
            "Kakao map renderer fallback should show a dedicated retry CTA for reloading the map renderer.",
            source.contains("map_viewport_retry"),
        )
        assertTrue(
            "Kakao map renderer fallback retry CTA should trigger a local renderer reload.",
            source.contains("reloadGeneration += 1"),
        )
        assertTrue(
            "Kakao map renderer retry should tear down the current renderer session before requesting a new one.",
            source.contains("controller.finish()"),
        )
        assertTrue(
            "Kakao map renderer retry should keep the map subtree unmounted briefly so restart behaves closer to a real pause/resume cycle.",
            source.contains("delay(KAKAO_RENDERER_RESTART_DELAY_MILLIS)"),
        )
    }

    @Test
    fun `renderer startup timeout returns endless loading back to error state`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/KakaoMapViewport.kt")
                .readText()

        assertTrue(
            "Kakao map renderer should convert endless initializing state back to an error state after a startup timeout.",
            source.contains("markRendererTimedOut()"),
        )
        assertTrue(
            "Kakao map renderer timeout should wait a bounded amount of time before surfacing the error state.",
            source.contains("delay(KAKAO_RENDERER_READY_TIMEOUT_MILLIS)"),
        )
    }

    @Test
    fun `initial renderer loading overlay waits through a short grace period before showing`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/KakaoMapViewport.kt")
                .readText()

        assertTrue(
            "The initial map loading overlay should wait through a short grace period so fast renderer startups do not flash a centered loading card.",
            source.contains("delay(KAKAO_RENDERER_LOADING_OVERLAY_DELAY_MILLIS)"),
        )
        assertTrue(
            "Kakao map viewport should route fallback overlay visibility through the dedicated loading-overlay policy helper.",
            source.contains("shouldShowKakaoRendererFallbackOverlay("),
        )
    }

    @Test
    fun `special map markers keep a compose overlay backup anchored by screen point`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/KakaoMapViewport.kt")
                .readText()

        assertTrue(
            "Special markers should have a Compose overlay path so selected pins, current location, and bookmarked destinations stay visible even when the Kakao label layer is unreliable.",
            source.contains("MapProjectedMarkerOverlay("),
        )
        assertTrue(
            "Projected marker overlays should be anchored from the map screen-point projection.",
            source.contains("projectedMarkerOverlays"),
        )
        assertTrue(
            "Projected marker overlays should include the selected destination marker so bookmarked places are not camera-only state.",
            source.contains("selectedDestinationCoordinate"),
        )
    }

    @Test
    fun `projected overlay markers are clipped to the viewport bounds`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/KakaoMapViewport.kt")
                .readText()

        assertTrue(
            "Projected marker overlays should be clipped to the map viewport so current-location or origin pins cannot bleed over the navigation info sheet.",
            source.contains("clipToBounds()"),
        )
    }

    @Test
    fun `route arrow camera bearing logs keep both raw radian and converted degree values`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/KakaoMapViewport.kt")
                .readText()

        assertTrue(
            "Kakao route-arrow debugging should keep the SDK raw rotationAngle value and the converted degree value side by side.",
            source.contains("bearingRadians = rotationAngle") &&
                source.contains("bearingDegrees = Math.toDegrees(rotationAngle)") &&
                source.contains("bearingRad=") &&
                source.contains("bearingDeg="),
        )
    }

    @Test
    fun `camera move tracking loop re-syncs native overlay markers during rotation gestures`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/KakaoMapViewport.kt")
                .readText()

        val trackingLoop =
            source
                .substringAfter("private fun startProjectedMarkerTracking()")
                .substringBefore("private fun stopProjectedMarkerTracking()")

        assertTrue(
            "Route arrows should be re-synchronized inside the animation-frame tracking loop so they do not wait until camera move end during rotation gestures.",
            trackingLoop.contains("syncMarkers(") &&
                trackingLoop.contains("reason = \"camera-move-tracking\"") &&
                trackingLoop.contains("updateProjectedMarkerOverlays(readyMap = readyMap, state = latestState)"),
        )
    }

    @Test
    fun `background single taps are routed to background callback before detail dispatch chain`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/KakaoMapViewport.kt")
                .readText()

        assertTrue(
            "Terrain taps on the bare map should be routed to the background callback instead of opening the place detail flow.",
            Regex("""setOnTerrainClickListener\s*\{\s*_,\s*position,\s*_\s*->\s*dispatchBackgroundMapTap\(\s*source = "terrain",\s*position = position,\s*\)""")
                .containsMatchIn(source),
        )
        assertTrue(
            "Generic map clicks with no POI payload should also route to the background callback instead of flowing into MapTapped detail lookup.",
            Regex("""else if \(poi == null\)\s*\{\s*dispatchBackgroundMapTap\(\s*source = "map",\s*position = position,\s*\)""")
                .containsMatchIn(source),
        )
        assertTrue(
            "Kakao background taps should invoke the dedicated background click handler so the ViewModel can clear preview state.",
            source.contains("backgroundClickHandler?.invoke()"),
        )
        assertFalse(
            "Terrain taps should no longer dispatch ADDRESS map taps from the viewport.",
            source.contains(
                """
                dispatchMapTap(
                            source = "terrain",
                """.trimIndent(),
            ),
        )
        assertFalse(
            "Null-POI map clicks should no longer dispatch ADDRESS map taps from the viewport.",
            source.contains(
                """
                } else if (poi == null) {
                            dispatchMapTap(
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `facility markers render through native kakao label layers with runtime bitmap styles`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/KakaoMapViewport.kt")
                .readText()

        assertTrue(
            "Facility markers should recreate a dedicated Kakao label layer instead of projecting every facility marker through Compose.",
            source.contains("LabelLayerOptions") &&
                source.contains("getOrCreateLabelLayer(KAKAO_MARKER_LAYER_ID") &&
                source.contains("layer.addLabel("),
        )
        assertTrue(
            "Facility labels should use runtime-generated bitmap styles so vector drawables are not handed to Kakao labels directly.",
            source.contains("KakaoFacilityMarkerStyleCache") &&
                source.contains("LabelStyle") &&
                source.contains("createFacilityMarkerBitmap("),
        )
        assertTrue(
            "The renderer should still clear old label layers before re-adding the current facility labels.",
            source.contains("layer.removeAll()"),
        )
        assertFalse(
            "Facility markers should no longer use the dedicated Compose projection overlay path.",
            source.contains("MapProjectedFacilityMarkerOverlay("),
        )
        assertFalse(
            "Facility markers should no longer keep a projected facility overlay list in controller state.",
            source.contains("projectedFacilityMarkerOverlays"),
        )
        assertFalse(
            "Facility markers should no longer be derived from createKakaoFacilityMarkerOverlays.",
            source.contains("createKakaoFacilityMarkerOverlays("),
        )
    }

    @Test
    fun `route overlays render through native kakao route line layer`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/KakaoMapViewport.kt")
                .readText()

        assertTrue(
            "Route preview and navigation polylines should be rendered by Kakao's native route line layer.",
            source.contains("syncRouteLines(") &&
                source.contains("RouteLineOptions") &&
                source.contains("RouteLineSegment") &&
                source.contains("routeLineManager.getLayer(") &&
                source.contains("routeLineLayer.removeAll()") &&
                source.contains("routeLineManager.addLayer("),
        )
        assertTrue(
            "Route map camera should animate when fitting route geometry instead of snapping from the default map center.",
            source.contains("CameraUpdateFactory.fitMapPoints") &&
                source.contains("CameraAnimation.from(KAKAO_ZOOM_CAMERA_ANIMATION_DURATION_MILLIS)") &&
                source.contains("createKakaoRouteCameraRenderState"),
        )
        assertTrue(
            "Route origin and destination markers should stay projected over the Kakao map viewport.",
            source.contains("overlayPoints = state?.overlayState?.points.orEmpty()"),
        )
    }

    @Test
    fun `route line cache is invalidated across kakao renderer lifecycle changes`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/KakaoMapViewport.kt")
                .readText()

        assertTrue(
            "Kakao can clear native route line layers on resume or renderer destroy, so the FE cache must be invalidated.",
            source.contains("invalidateRenderedRouteLines()") &&
                source.contains("override fun onMapDestroy()") &&
                source.contains("override fun onMapResumed()") &&
                source.contains("renderIntoMapIfReady()"),
        )
    }

    @Test
    fun `external kakao poi taps are forwarded as provider map tap payloads`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/KakaoMapViewport.kt")
                .readText()

        assertTrue(
            "Kakao POI taps should be converted into POI map tap payloads instead of being treated as blank terrain taps.",
            source.contains("dispatchExternalPoiTap(") &&
                source.contains("MapTapClickType.POI") &&
                source.contains("KAKAO_PROVIDER_NAME"),
        )
        assertTrue(
            "The generic map click callback should pass Kakao's POI name when the SDK exposes it.",
            source.contains("nameHint = poi.name"),
        )
        assertTrue(
            "The POI callback should only handle app markers so Kakao POIs can flow through the map click callback with a name hint.",
            Regex("""readyMap\.setOnPoiClickListener\s*\{\s*_,\s*position,\s*layerId,\s*poiId\s*->\s*if \(layerId == KAKAO_MARKER_LAYER_ID && poiId\.isNotBlank\(\)\)""")
                .containsMatchIn(source),
        )
        assertFalse(
            "Kakao POI taps must not dispatch a name-less detail request before the map click callback can provide poi.name.",
            source.contains(
                """
                providerPlaceId = poiId,
                                nameHint = null,
                """.trimIndent(),
            ),
        )
    }
}
