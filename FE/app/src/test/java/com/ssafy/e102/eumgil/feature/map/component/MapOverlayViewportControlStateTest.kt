package com.ssafy.e102.eumgil.feature.map.component

import com.ssafy.e102.eumgil.feature.map.model.MapCameraSource
import com.ssafy.e102.eumgil.feature.map.model.MapCameraTarget
import com.ssafy.e102.eumgil.feature.map.model.MapCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapOverlayViewportControlStateTest {
    @Test
    fun `programmatic camera callback aligned with requested target does not count as user gesture`() {
        val requestedTarget =
            MapCameraTarget(
                center = MapCoordinate(latitude = 35.1796, longitude = 129.0756),
                source = MapCameraSource.CURRENT_LOCATION,
                requestId = 42L,
                zoomLevel = 17,
            )

        assertFalse(
            shouldTreatViewportCameraMoveAsUserGesture(
                requestedTarget = requestedTarget,
                center = MapCoordinate(latitude = 35.1796, longitude = 129.0756),
                zoomLevel = 17,
                reportedUserGesture = true,
            ),
        )
    }

    @Test
    fun `camera callback away from requested target still counts as user gesture`() {
        val requestedTarget =
            MapCameraTarget(
                center = MapCoordinate(latitude = 35.1796, longitude = 129.0756),
                source = MapCameraSource.CURRENT_LOCATION,
                requestId = 42L,
                zoomLevel = 17,
            )

        assertTrue(
            shouldTreatViewportCameraMoveAsUserGesture(
                requestedTarget = requestedTarget,
                center = MapCoordinate(latitude = 35.1816, longitude = 129.0796),
                zoomLevel = 17,
                reportedUserGesture = true,
            ),
        )
    }

    @Test
    fun `manual camera survives active guidance base target changes`() {
        val controlState = MapOverlayViewportControlState()
        val focusedPreviewTarget =
            MapCameraTarget(
                center = MapCoordinate(latitude = 35.1810, longitude = 129.0820),
                source = MapCameraSource.SEARCH_RESULT,
                requestId = 10L,
                zoomLevel = 17,
            )
        val manualCameraCenter = MapCoordinate(latitude = 35.1777, longitude = 129.0712)
        val activeFallbackTarget =
            MapCameraTarget(
                center = MapCoordinate(latitude = 35.1796, longitude = 129.0756),
                source = MapCameraSource.SEARCH_RESULT,
                requestId = 11L,
                zoomLevel = 18,
            )

        controlState.updateBaseCameraTarget(focusedPreviewTarget)
        controlState.onCameraMoveEnd(
            center = manualCameraCenter,
            zoomLevel = 16,
            isUserGesture = true,
        )
        controlState.updateBaseCameraTarget(activeFallbackTarget)

        val cameraTarget = controlState.cameraTargetFor(activeFallbackTarget)
        assertEquals(manualCameraCenter, cameraTarget.center)
        assertEquals(16, cameraTarget.zoomLevel)
        assertFalse(controlState.shouldFitProjection)
    }

    @Test
    fun `clearing manual camera lets focused segment base target take over`() {
        val controlState = MapOverlayViewportControlState()
        val activeTarget =
            MapCameraTarget(
                center = MapCoordinate(latitude = 35.1796, longitude = 129.0756),
                source = MapCameraSource.CURRENT_LOCATION,
                requestId = 20L,
                zoomLevel = 17,
            )
        val focusedSegmentTarget =
            MapCameraTarget(
                center = MapCoordinate(latitude = 35.1810, longitude = 129.0820),
                source = MapCameraSource.SEARCH_RESULT,
                requestId = 21L,
                zoomLevel = 16,
            )

        controlState.updateBaseCameraTarget(activeTarget)
        controlState.zoomOut()
        assertFalse(controlState.shouldFitProjection)

        controlState.updateBaseCameraTarget(focusedSegmentTarget)
        controlState.clearManualCamera()

        val cameraTarget = controlState.cameraTargetFor(focusedSegmentTarget)
        assertEquals(focusedSegmentTarget.center, cameraTarget.center)
        assertEquals(16, cameraTarget.zoomLevel)
        assertTrue(controlState.shouldFitProjection)
    }

    @Test
    fun `repeated zoom controls build from the pending camera target`() {
        val controlState = MapOverlayViewportControlState()
        val baseTarget =
            MapCameraTarget(
                center = MapCoordinate(latitude = 35.1796, longitude = 129.0756),
                source = MapCameraSource.SEARCH_RESULT,
                requestId = 30L,
                zoomLevel = 16,
            )

        controlState.updateBaseCameraTarget(baseTarget)
        controlState.zoomIn()
        controlState.zoomIn()

        val cameraTarget = controlState.cameraTargetFor(baseTarget)
        assertEquals(baseTarget.center, cameraTarget.center)
        assertEquals(18, cameraTarget.zoomLevel)
        assertFalse(controlState.shouldFitProjection)
    }

    @Test
    fun `stale programmatic camera callback does not override a pending zoom target`() {
        val controlState = MapOverlayViewportControlState()
        val baseTarget =
            MapCameraTarget(
                center = MapCoordinate(latitude = 35.1796, longitude = 129.0756),
                source = MapCameraSource.SEARCH_RESULT,
                requestId = 40L,
                zoomLevel = 16,
            )

        controlState.updateBaseCameraTarget(baseTarget)
        controlState.zoomIn()
        controlState.onCameraMoveEnd(
            center = MapCoordinate(latitude = 35.1900, longitude = 129.0900),
            zoomLevel = 16,
            isUserGesture = false,
        )
        controlState.zoomIn()

        val cameraTarget = controlState.cameraTargetFor(baseTarget)
        assertEquals(baseTarget.center, cameraTarget.center)
        assertEquals(18, cameraTarget.zoomLevel)
    }
}
