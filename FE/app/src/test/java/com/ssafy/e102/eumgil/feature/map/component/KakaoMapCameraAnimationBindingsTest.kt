package com.ssafy.e102.eumgil.feature.map.component

import com.ssafy.e102.eumgil.feature.map.model.MapCameraSource
import com.ssafy.e102.eumgil.feature.map.model.MapCameraTarget
import com.ssafy.e102.eumgil.feature.map.model.MapCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KakaoMapCameraAnimationBindingsTest {
    @Test
    fun `zoom-only transition on same viewport animates camera`() {
        val previous =
            MapCameraTarget(
                center = MapCoordinate(latitude = 35.1796, longitude = 129.0756),
                source = MapCameraSource.DEFAULT_BUSAN,
                requestId = 3L,
                zoomLevel = 15,
            )
        val next = previous.copy(requestId = 4L, zoomLevel = 16)

        assertTrue(shouldAnimateKakaoCameraTransition(previousTarget = previous, nextTarget = next))
    }

    @Test
    fun `camera center change animates focused marker movement`() {
        val previous =
            MapCameraTarget(
                center = MapCoordinate(latitude = 35.1796, longitude = 129.0756),
                source = MapCameraSource.DEFAULT_BUSAN,
                requestId = 3L,
                zoomLevel = 15,
            )
        val next =
            previous.copy(
                requestId = 4L,
                center = MapCoordinate(latitude = 35.1801, longitude = 129.0761),
                zoomLevel = 16,
            )

        assertTrue(shouldAnimateKakaoCameraTransition(previousTarget = previous, nextTarget = next))
    }

    @Test
    fun `source change keeps transition immediate`() {
        val previous =
            MapCameraTarget(
                center = MapCoordinate(latitude = 35.1796, longitude = 129.0756),
                source = MapCameraSource.DEFAULT_BUSAN,
                requestId = 3L,
                zoomLevel = 15,
            )
        val next =
            previous.copy(
                requestId = 4L,
                source = MapCameraSource.CURRENT_LOCATION,
                zoomLevel = 16,
            )

        assertFalse(shouldAnimateKakaoCameraTransition(previousTarget = previous, nextTarget = next))
    }

    @Test
    fun `camera target can force long guidance jumps to be immediate`() {
        val previous =
            MapCameraTarget(
                center = MapCoordinate(latitude = 35.1796, longitude = 129.0756),
                source = MapCameraSource.DEFAULT_BUSAN,
                requestId = 3L,
                zoomLevel = 15,
            )
        val next =
            previous.copy(
                requestId = 4L,
                center = MapCoordinate(latitude = 35.1900, longitude = 129.0900),
                shouldAnimateTransition = false,
            )

        assertFalse(shouldAnimateKakaoCameraTransition(previousTarget = previous, nextTarget = next))
    }

    @Test
    fun `camera move end sync updates rendered baseline to moved viewport`() {
        val previous =
            MapCameraTarget(
                center = MapCoordinate(latitude = 35.1796, longitude = 129.0756),
                source = MapCameraSource.DEFAULT_BUSAN,
                requestId = 7L,
                zoomLevel = 15,
            )
        val movedCenter = MapCoordinate(latitude = 35.1812, longitude = 129.0814)

        val synced =
            syncRenderedKakaoCameraTarget(
                previousTarget = previous,
                latestStateTarget = previous,
                center = movedCenter,
                zoomLevel = 15,
            )
        val nextZoom = synced.copy(requestId = 8L, zoomLevel = 16)

        assertEquals(movedCenter, synced.center)
        assertEquals(previous.source, synced.source)
        assertEquals(previous.requestId, synced.requestId)
        assertTrue(shouldAnimateKakaoCameraTransition(previousTarget = synced, nextTarget = nextZoom))
    }

    @Test
    fun `camera sync skips duplicate move when only request id changed after user gesture`() {
        val rendered =
            MapCameraTarget(
                center = MapCoordinate(latitude = 35.1812, longitude = 129.0814),
                source = MapCameraSource.SEARCH_RESULT,
                requestId = 7L,
                zoomLevel = 17,
                shouldAnimateTransition = false,
            )
        val requested = rendered.copy(requestId = 8L)

        assertTrue(
            shouldSkipKakaoCameraSync(
                renderedTarget = rendered,
                requestedTarget = requested,
            ),
        )
    }

    @Test
    fun `camera sync runs animated programmatic focus even when target matches previous render`() {
        val rendered =
            MapCameraTarget(
                center = MapCoordinate(latitude = 35.1812, longitude = 129.0814),
                source = MapCameraSource.SEARCH_RESULT,
                requestId = 7L,
                zoomLevel = 17,
                shouldAnimateTransition = false,
            )
        val requested =
            rendered.copy(
                requestId = 8L,
                shouldAnimateTransition = true,
            )

        assertFalse(
            shouldSkipKakaoCameraSync(
                renderedTarget = rendered,
                requestedTarget = requested,
            ),
        )
    }

    @Test
    fun `camera sync still runs when zoom actually changes`() {
        val rendered =
            MapCameraTarget(
                center = MapCoordinate(latitude = 35.1812, longitude = 129.0814),
                source = MapCameraSource.SEARCH_RESULT,
                requestId = 7L,
                zoomLevel = 17,
                shouldAnimateTransition = false,
            )
        val requested = rendered.copy(requestId = 8L, zoomLevel = 18)

        assertFalse(
            shouldSkipKakaoCameraSync(
                renderedTarget = rendered,
                requestedTarget = requested,
            ),
        )
    }
}
