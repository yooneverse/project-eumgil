package com.ssafy.e102.eumgil.feature.map

import androidx.activity.ComponentActivity
import com.ssafy.e102.eumgil.core.location.CurrentLocationManager
import com.ssafy.e102.eumgil.core.location.LocationPermissionManager
import com.ssafy.e102.eumgil.core.location.LocationPermissionState
import com.ssafy.e102.eumgil.core.location.LocationSnapshot
import com.ssafy.e102.eumgil.core.model.FacilityBrowseData
import com.ssafy.e102.eumgil.core.model.FacilitySeedCatalog
import com.ssafy.e102.eumgil.core.model.FacilitySeedQuery
import com.ssafy.e102.eumgil.core.model.FacilityMarkerSeed
import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.data.repository.ApprovedReportMapEntry
import com.ssafy.e102.eumgil.data.repository.ApprovedReportMapQuery
import com.ssafy.e102.eumgil.data.repository.ApprovedReportMapRepository
import com.ssafy.e102.eumgil.data.repository.BookmarkData
import com.ssafy.e102.eumgil.data.repository.BookmarkRepository
import com.ssafy.e102.eumgil.data.repository.EmptyApprovedReportMapRepository
import com.ssafy.e102.eumgil.data.repository.FacilitySeedRepository
import com.ssafy.e102.eumgil.data.repository.InMemoryDestinationSelectionRepository
import com.ssafy.e102.eumgil.feature.map.model.MapCoordinate
import com.ssafy.e102.eumgil.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapApprovedReportScaffoldViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `approved report scaffold starts empty before backend api is wired`() =
        runTest {
            val viewModel =
                createMapViewModel(
                    approvedReportMapRepository = EmptyApprovedReportMapRepository,
                )

            viewModel.onRouteStarted()
            viewModel.onAction(
                MapUiAction.ViewportCameraChanged(
                    center = MapCoordinate(latitude = 35.1796, longitude = 129.0756),
                    zoomLevel = 15,
                ),
            )
            advanceUntilIdle()

            assertEquals(0, viewModel.uiState.value.approvedReportMarkerState.reports.size)
            assertEquals(0, viewModel.uiState.value.approvedReportMarkerState.visibleReports.size)
            assertFalse(viewModel.uiState.value.approvedReportSheetState.isVisible)
        }

    @Test
    fun `approved report load keeps only approved entries and hides them above zoom threshold`() =
        runTest {
            val repository =
                FakeApprovedReportMapRepository(
                    reports =
                        listOf(
                            approvedReport(reportId = 42L, statusApiValue = "APPROVED"),
                            approvedReport(reportId = 43L, statusApiValue = "PENDING"),
                        ),
                )
            val viewModel = createMapViewModel(approvedReportMapRepository = repository)
            val viewportCenter = MapCoordinate(latitude = 35.1810, longitude = 129.0770)

            viewModel.onRouteStarted()
            viewModel.onAction(
                MapUiAction.ViewportCameraChanged(
                    center = viewportCenter,
                    zoomLevel = 15,
                    isUserGesture = true,
                ),
            )
            advanceUntilIdle()

            assertEquals(viewportCenter.latitude, repository.queries.last().center.latitude, 0.0)
            assertEquals(viewportCenter.longitude, repository.queries.last().center.longitude, 0.0)
            assertEquals(5_000, repository.queries.last().radiusMeters)
            assertEquals(listOf(42L), viewModel.uiState.value.approvedReportMarkerState.reports.map { it.reportId })
            assertEquals(listOf(42L), viewModel.uiState.value.approvedReportMarkerState.visibleReports.map { it.reportId })

            viewModel.onAction(
                MapUiAction.ViewportCameraChanged(
                    center = viewportCenter,
                    zoomLevel = 16,
                    isUserGesture = true,
                ),
            )
            advanceUntilIdle()

            assertEquals(listOf(42L), viewModel.uiState.value.approvedReportMarkerState.reports.map { it.reportId })
            assertEquals(emptyList<Long>(), viewModel.uiState.value.approvedReportMarkerState.visibleReports.map { it.reportId })
            assertFalse(viewModel.uiState.value.approvedReportSheetState.isVisible)
        }

    @Test
    fun `approved report marker tap toggles sheet and dismiss clears selection`() =
        runTest {
            val repository =
                FakeApprovedReportMapRepository(
                    reports = listOf(approvedReport(reportId = 42L, statusApiValue = "APPROVED")),
                )
            val viewModel = createMapViewModel(approvedReportMapRepository = repository)

            viewModel.onRouteStarted()
            advanceUntilIdle()

            viewModel.onAction(MapUiAction.ApprovedReportMarkerTapped(reportId = 42L))
            advanceUntilIdle()

            assertEquals(42L, viewModel.uiState.value.approvedReportMarkerState.selectedReportId)
            assertTrue(viewModel.uiState.value.approvedReportSheetState.isVisible)
            assertEquals(42L, viewModel.uiState.value.approvedReportSheetState.report?.reportId)

            viewModel.onAction(MapUiAction.ApprovedReportSheetDismissed)
            advanceUntilIdle()

            assertEquals(null, viewModel.uiState.value.approvedReportMarkerState.selectedReportId)
            assertFalse(viewModel.uiState.value.approvedReportSheetState.isVisible)
        }
}

private fun createMapViewModel(
    approvedReportMapRepository: ApprovedReportMapRepository,
): MapViewModel =
    MapViewModel(
        locationPermissionManager = ApprovedReportFakeLocationPermissionManager(
            initialState = LocationPermissionState.Denied,
        ),
        currentLocationManager = ApprovedReportFakeCurrentLocationManager(),
        destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
        facilitySeedRepository = ApprovedReportEmptyFacilitySeedRepository(),
        bookmarkRepository = ApprovedReportFakeBookmarkRepository(),
        approvedReportMapRepository = approvedReportMapRepository,
    )

private fun approvedReport(
    reportId: Long,
    statusApiValue: String,
): ApprovedReportMapEntry =
    ApprovedReportMapEntry(
        reportId = reportId,
        reportTypeApiValue = "BROKEN_BLOCK",
        statusApiValue = statusApiValue,
        coordinate = GeoCoordinate(latitude = 35.1796, longitude = 129.0756),
        address = "Busan central road",
        description = "Broken tactile block",
        imageUrls = emptyList(),
        approvedAt = "2026-05-19T09:00:00Z",
    )

private class FakeApprovedReportMapRepository(
    private val reports: List<ApprovedReportMapEntry>,
) : ApprovedReportMapRepository {
    val queries = mutableListOf<ApprovedReportMapQuery>()

    override suspend fun getApprovedReports(query: ApprovedReportMapQuery): List<ApprovedReportMapEntry> {
        queries += query
        return reports
    }
}

private class ApprovedReportFakeLocationPermissionManager(
    initialState: LocationPermissionState,
) : LocationPermissionManager {
    private val mutablePermissionState = MutableStateFlow(initialState)

    override val permissionState: StateFlow<LocationPermissionState> = mutablePermissionState

    override fun refreshPermissionState() = Unit

    override fun requestLocationPermission(activity: ComponentActivity) = Unit
}

private class ApprovedReportFakeCurrentLocationManager : CurrentLocationManager {
    override val latestLocation: StateFlow<LocationSnapshot?> = MutableStateFlow(null)

    override fun refreshLatestLocation() = Unit

    override fun startLocationUpdates() = Unit

    override fun stopLocationUpdates() = Unit
}

private class ApprovedReportEmptyFacilitySeedRepository : FacilitySeedRepository {
    override suspend fun getSeedCatalog(): FacilitySeedCatalog = FacilitySeedCatalog()

    override suspend fun getFacilityBrowseData(query: FacilitySeedQuery): FacilityBrowseData = FacilityBrowseData()

    override suspend fun getFacilityMarkers(query: FacilitySeedQuery): List<FacilityMarkerSeed> = emptyList()

    override suspend fun getFacilityDetail(facilityId: String) = null
}

private class ApprovedReportFakeBookmarkRepository : BookmarkRepository {
    override fun observeBookmarks(): Flow<List<BookmarkData>> = flowOf(emptyList())

    override suspend fun isBookmarked(placeId: String): Boolean = false

    override suspend fun saveBookmark(bookmark: BookmarkData): BookmarkData = bookmark

    override suspend fun deleteBookmark(placeId: String) = Unit
}
