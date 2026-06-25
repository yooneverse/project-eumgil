package com.ssafy.e102.eumgil.feature.map.component

import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.data.repository.ApprovedHazardMarker
import com.ssafy.e102.eumgil.data.repository.ApprovedHazardMarkerBounds
import com.ssafy.e102.eumgil.data.repository.ReportDraftData
import com.ssafy.e102.eumgil.data.repository.ReportHistoryDetailData
import com.ssafy.e102.eumgil.data.repository.ReportOutboxData
import com.ssafy.e102.eumgil.data.repository.ReportRepository
import com.ssafy.e102.eumgil.data.repository.ReportSubmitResult
import com.ssafy.e102.eumgil.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ApprovedHazardMarkerOverlayStateTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    @Test
    fun `viewport within 2km fetches markers after debounce`() = runTest {
        val repository =
            FakeReportRepository(
                markersToReturn =
                    listOf(
                        ApprovedHazardMarker(
                            reportId = 12L,
                            reportType = "RAMP",
                            coordinate = GeoCoordinate(latitude = 35.1, longitude = 129.1),
                            imageUrls = listOf("https://example.com/image.jpg"),
                        ),
                    ),
            )
        val state =
            ApprovedHazardMarkerOverlayState(
                reportRepository = repository,
                coroutineScope = backgroundScope,
            )
        runCurrent()

        state.onViewportBoundsChanged(
            MapViewportBounds(
                swLat = 35.099,
                swLng = 129.099,
                neLat = 35.101,
                neLng = 129.101,
            ),
        )

        runCurrent()
        advanceTimeBy(401L)
        runCurrent()

        assertEquals(1, repository.requestedBounds.size)
        assertEquals(1, state.markers.size)
        assertEquals(12L, state.markers.single().reportId)
        assertEquals(MapViewportPointKind.APPROVED_REPORT, state.overlayPoints.single().kind)
        assertEquals("RAMP", state.overlayPoints.single().reportTypeApiValue)
    }

    @Test
    fun `viewport wider than 2km does not fetch and clears selection`() = runTest {
        val repository = FakeReportRepository()
        val state =
            ApprovedHazardMarkerOverlayState(
                reportRepository = repository,
                coroutineScope = backgroundScope,
            )
        runCurrent()

        state.onViewportBoundsChanged(
            MapViewportBounds(
                swLat = 35.099,
                swLng = 129.099,
                neLat = 35.101,
                neLng = 129.101,
            ),
        )
        runCurrent()
        advanceTimeBy(401L)
        runCurrent()
        state.onMarkerClick("hazard-report-12")

        state.onViewportBoundsChanged(
            MapViewportBounds(
                swLat = 35.09,
                swLng = 129.09,
                neLat = 35.11,
                neLng = 129.11,
            ),
        )

        runCurrent()
        advanceTimeBy(401L)
        runCurrent()

        assertEquals(1, repository.requestedBounds.size)
        assertEquals(0, state.markers.size)
        assertNull(state.selectedMarker)
    }

    @Test
    fun `similar bounds do not refetch`() = runTest {
        val repository = FakeReportRepository()
        val state =
            ApprovedHazardMarkerOverlayState(
                reportRepository = repository,
                coroutineScope = backgroundScope,
            )
        runCurrent()

        state.onViewportBoundsChanged(
            MapViewportBounds(
                swLat = 35.09901,
                swLng = 129.09901,
                neLat = 35.10101,
                neLng = 129.10101,
            ),
        )
        runCurrent()
        advanceTimeBy(401L)
        runCurrent()

        state.onViewportBoundsChanged(
            MapViewportBounds(
                swLat = 35.09904,
                swLng = 129.09904,
                neLat = 35.10104,
                neLng = 129.10104,
            ),
        )
        runCurrent()
        advanceTimeBy(401L)
        runCurrent()

        assertEquals(1, repository.requestedBounds.size)
    }

    @Test
    fun `marker click selects hazard marker`() = runTest {
        val repository = FakeReportRepository()
        val state =
            ApprovedHazardMarkerOverlayState(
                reportRepository = repository,
                coroutineScope = backgroundScope,
            )
        runCurrent()

        state.onViewportBoundsChanged(
            MapViewportBounds(
                swLat = 35.099,
                swLng = 129.099,
                neLat = 35.101,
                neLng = 129.101,
            ),
        )
        runCurrent()
        advanceTimeBy(401L)
        runCurrent()

        assertEquals(true, state.onMarkerClick("hazard-report-12"))
        assertEquals(12L, state.selectedMarker?.reportId)
        assertEquals(true, state.overlayPoints.single().isSelected)
    }

    @Test
    fun `approved hazard overlay point uses click target as kakao marker id`() = runTest {
        val repository = FakeReportRepository()
        val state =
            ApprovedHazardMarkerOverlayState(
                reportRepository = repository,
                coroutineScope = backgroundScope,
            )
        runCurrent()

        state.onViewportBoundsChanged(
            MapViewportBounds(
                swLat = 35.099,
                swLng = 129.099,
                neLat = 35.101,
                neLng = 129.101,
            ),
        )
        runCurrent()
        advanceTimeBy(401L)
        runCurrent()

        val marker = state.overlayPoints.single().toKakaoProjectedPointMarkerState()

        assertEquals("hazard-report-12", marker?.markerId)
        assertEquals("hazard-report-12", marker?.clickTargetId)
    }

    @Test
    fun `unknown report type is still preserved on overlay point for fallback rendering`() = runTest {
        val repository =
            FakeReportRepository(
                markersToReturn =
                    listOf(
                        ApprovedHazardMarker(
                            reportId = 55L,
                            reportType = "UNKNOWN_TYPE",
                            coordinate = GeoCoordinate(latitude = 35.1, longitude = 129.1),
                            imageUrls = emptyList(),
                        ),
                    ),
            )
        val state =
            ApprovedHazardMarkerOverlayState(
                reportRepository = repository,
                coroutineScope = backgroundScope,
            )
        runCurrent()

        state.onViewportBoundsChanged(
            MapViewportBounds(
                swLat = 35.099,
                swLng = 129.099,
                neLat = 35.101,
                neLng = 129.101,
            ),
        )

        runCurrent()
        advanceTimeBy(401L)
        runCurrent()

        assertTrue(state.overlayPoints.isNotEmpty())
        assertEquals("UNKNOWN_TYPE", state.overlayPoints.single().reportTypeApiValue)
    }
}

private class FakeReportRepository(
    private val markersToReturn: List<ApprovedHazardMarker> =
        listOf(
            ApprovedHazardMarker(
                reportId = 12L,
                reportType = "RAMP",
                coordinate = GeoCoordinate(latitude = 35.1, longitude = 129.1),
                imageUrls = emptyList(),
            ),
        ),
) : ReportRepository {
    val requestedBounds = mutableListOf<ApprovedHazardMarkerBounds>()

    override fun observeReportHistory(): Flow<List<ReportOutboxData>> = emptyFlow()

    override suspend fun getApprovedHazardMarkers(bounds: ApprovedHazardMarkerBounds): List<ApprovedHazardMarker> {
        requestedBounds += bounds
        return markersToReturn
    }

    override suspend fun getReportHistoryDetail(historyId: String): ReportHistoryDetailData? = null

    override suspend fun getLatestDraft(): ReportDraftData? = null

    override suspend fun saveDraft(draft: ReportDraftData): ReportDraftData = draft

    override suspend fun deleteDraft(draftId: String) = Unit

    override suspend fun saveOutbox(outbox: ReportOutboxData): ReportOutboxData = outbox

    override suspend fun submitOutboxToServer(outboxId: String): ReportSubmitResult =
        ReportSubmitResult.Success(
            outboxId = outboxId,
            serverReportId = 1L,
        )
}
