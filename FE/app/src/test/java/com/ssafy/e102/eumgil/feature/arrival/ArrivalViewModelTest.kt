package com.ssafy.e102.eumgil.feature.arrival

import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.core.model.RouteBookmark
import com.ssafy.e102.eumgil.core.model.RouteBookmarkDetail
import com.ssafy.e102.eumgil.core.model.RouteBookmarkDraft
import com.ssafy.e102.eumgil.core.model.RouteBookmarkSaveRequest
import com.ssafy.e102.eumgil.core.model.RouteOption
import com.ssafy.e102.eumgil.core.model.RouteSearchData
import com.ssafy.e102.eumgil.core.model.RouteSearchQuery
import com.ssafy.e102.eumgil.core.model.RouteSearchResult
import com.ssafy.e102.eumgil.core.model.RouteSearchSource
import com.ssafy.e102.eumgil.data.repository.RouteBookmarkRepository
import com.ssafy.e102.eumgil.data.repository.RouteRatingData
import com.ssafy.e102.eumgil.data.repository.RouteRerouteData
import com.ssafy.e102.eumgil.data.repository.RouteRepository
import com.ssafy.e102.eumgil.data.repository.RouteSessionData
import com.ssafy.e102.eumgil.data.repository.RouteTransitRefreshData
import com.ssafy.e102.eumgil.testing.MainDispatcherRule
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArrivalViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `home click emits map navigation event`() =
        runTest {
            val viewModel = createViewModel()
            val eventsDeferred = async(start = CoroutineStart.UNDISPATCHED) { viewModel.uiEvent.take(1).toList() }

            viewModel.onAction(ArrivalUiAction.HomeClicked)

            assertEquals(listOf(ArrivalUiEvent.NavigateToMap), eventsDeferred.await())
        }

    @Test
    fun `rating session enables evaluation submit after selection`() {
        val viewModel = createViewModel()

        assertTrue(viewModel.uiState.value.hasRatingSession)
        assertFalse(viewModel.uiState.value.isEvaluationSubmitEnabled)

        viewModel.onAction(ArrivalUiAction.RatingSelected(4))

        assertEquals(4, viewModel.uiState.value.selectedRating)
        assertEquals(ArrivalEvaluationLabel.Satisfied, viewModel.uiState.value.selectedRatingLabel)
        assertTrue(viewModel.uiState.value.isEvaluationSubmitEnabled)
    }

    @Test
    fun `bookmarked route hides evaluation sheet after initial sync`() =
        runTest {
            val bookmarkedRoute = testRouteBookmark(testRouteBookmarkDraft())
            val routeBookmarkRepository = FakeRouteBookmarkRepository(savedBookmarks = listOf(bookmarkedRoute))

            val viewModel = createViewModel(routeBookmarkRepository = routeBookmarkRepository)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isRouteSaveSelected)
            assertFalse(viewModel.uiState.value.isEvaluationSheetVisible)
        }

    @Test
    fun `unbookmarked route shows evaluation sheet after initial sync`() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isRouteSaveSelected)
            assertTrue(viewModel.uiState.value.isEvaluationSheetVisible)
        }

    @Test
    fun `submit evaluation calls rating api and hides sheet on success`() =
        runTest {
            val routeRepository = FakeArrivalRouteRepository()
            val viewModel = createViewModel(routeRepository = routeRepository)
            val eventDeferred = async(start = CoroutineStart.UNDISPATCHED) { viewModel.uiEvent.collect {} }

            viewModel.onAction(ArrivalUiAction.RatingSelected(5))
            viewModel.onAction(ArrivalUiAction.SubmitEvaluationClicked)
            advanceUntilIdle()

            assertEquals(listOf("session-1" to 5), routeRepository.ratingCalls)
            assertFalse(viewModel.uiState.value.isEvaluationSheetVisible)
            assertFalse(viewModel.uiState.value.isEvaluationSubmitting)
            assertTrue(eventDeferred.isActive)
            eventDeferred.cancel()
        }

    @Test
    fun `submit evaluation keeps sheet open on rating failure`() =
        runTest {
            val routeRepository =
                FakeArrivalRouteRepository(
                    ratingFailure = IllegalStateException("rating failed"),
                )
            val viewModel = createViewModel(routeRepository = routeRepository)
            val eventDeferred = async(start = CoroutineStart.UNDISPATCHED) { viewModel.uiEvent.collect {} }

            viewModel.onAction(ArrivalUiAction.RatingSelected(3))
            viewModel.onAction(ArrivalUiAction.SubmitEvaluationClicked)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isEvaluationSheetVisible)
            assertFalse(viewModel.uiState.value.isEvaluationSubmitting)
            assertTrue(eventDeferred.isActive)
            eventDeferred.cancel()
        }

    @Test
    fun `save route click stores route bookmark immediately and keeps sheet open`() =
        runTest {
            val routeBookmarkRepository = FakeRouteBookmarkRepository()
            val viewModel = createViewModel(routeBookmarkRepository = routeBookmarkRepository)
            advanceUntilIdle()
            val eventDeferred = async(start = CoroutineStart.UNDISPATCHED) { viewModel.uiEvent.collect {} }

            viewModel.onAction(ArrivalUiAction.SaveRouteClicked)
            advanceUntilIdle()

            assertEquals(testRouteBookmarkDraft().defaultRouteName, routeBookmarkRepository.savedBookmarks.value.single().routeName)
            assertTrue(viewModel.uiState.value.isRouteSaveSelected)
            assertEquals("route-bookmark:test", viewModel.uiState.value.routeSaveBookmarkId)
            assertTrue(viewModel.uiState.value.isEvaluationSheetVisible)
            assertTrue(viewModel.uiState.value.isRouteSaveEnabled)
            assertFalse(viewModel.uiState.value.isRouteSaveUpdating)
            assertTrue(eventDeferred.isActive)
            eventDeferred.cancel()
        }

    @Test
    fun `route save is disabled when ended route id is missing`() =
        runTest {
            val viewModel = createViewModel(routeBookmarkDraft = testUnsavableRouteBookmarkDraft())
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isRouteSaveEnabled)
            assertFalse(viewModel.uiState.value.routeSaveDraft?.canSaveToServer ?: true)
            assertTrue(viewModel.uiState.value.isEvaluationSheetVisible)
        }

    @Test
    fun `save route click is ignored when ended route id is missing`() =
        runTest {
            val routeBookmarkRepository = FakeRouteBookmarkRepository()
            val viewModel =
                createViewModel(
                    routeBookmarkRepository = routeBookmarkRepository,
                    routeBookmarkDraft = testUnsavableRouteBookmarkDraft(),
                )
            advanceUntilIdle()

            viewModel.onAction(ArrivalUiAction.SaveRouteClicked)
            advanceUntilIdle()

            assertTrue(routeBookmarkRepository.savedRequests.isEmpty())
            assertFalse(viewModel.uiState.value.isRouteSaveSelected)
            assertEquals(null, viewModel.uiState.value.routeSaveBookmarkId)
            assertFalse(viewModel.uiState.value.isRouteSaveUpdating)
        }

    @Test
    fun `save route click toggles saved route off on second tap`() =
        runTest {
            val routeBookmarkRepository = FakeRouteBookmarkRepository()
            val viewModel = createViewModel(routeBookmarkRepository = routeBookmarkRepository)
            advanceUntilIdle()

            viewModel.onAction(ArrivalUiAction.SaveRouteClicked)
            advanceUntilIdle()
            viewModel.onAction(ArrivalUiAction.SaveRouteClicked)
            advanceUntilIdle()

            assertTrue(routeBookmarkRepository.deletedBookmarkIds.contains("route-bookmark:test"))
            assertTrue(routeBookmarkRepository.savedBookmarks.value.isEmpty())
            assertFalse(viewModel.uiState.value.isRouteSaveSelected)
            assertEquals(null, viewModel.uiState.value.routeSaveBookmarkId)
            assertTrue(viewModel.uiState.value.isRouteSaveEnabled)
            assertFalse(viewModel.uiState.value.isRouteSaveUpdating)
        }

    @Test
    fun `save route failure keeps route unsaved and emits toast`() =
        runTest {
            val routeBookmarkRepository =
                FakeRouteBookmarkRepository(
                    saveFailure = IllegalStateException("save failed"),
                )
            val viewModel = createViewModel(routeBookmarkRepository = routeBookmarkRepository)
            advanceUntilIdle()
            val eventsDeferred = async(start = CoroutineStart.UNDISPATCHED) { viewModel.uiEvent.take(1).toList() }

            viewModel.onAction(ArrivalUiAction.SaveRouteClicked)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isRouteSaveSelected)
            assertEquals(null, viewModel.uiState.value.routeSaveBookmarkId)
            assertFalse(viewModel.uiState.value.isRouteSaveUpdating)
            assertEquals(
                listOf(ArrivalUiEvent.ShowToast("경로 북마크를 저장하지 못했습니다. 다시 시도해 주세요.")),
                eventsDeferred.await(),
            )
        }
}

private fun createViewModel(
    routeBookmarkRepository: RouteBookmarkRepository = FakeRouteBookmarkRepository(),
    routeRepository: RouteRepository = FakeArrivalRouteRepository(),
    routeBookmarkDraft: RouteBookmarkDraft = testRouteBookmarkDraft(),
    ratingSessionId: String = "session-1",
): ArrivalViewModel =
    ArrivalViewModel(
        routeBookmarkRepository = routeBookmarkRepository,
        routeRepository = routeRepository,
        currentRouteBookmarkDraft = routeBookmarkDraft,
        currentRatingSessionId = ratingSessionId,
    )

private class FakeRouteBookmarkRepository(
    savedBookmarks: List<RouteBookmark> = emptyList(),
    private val saveFailure: Throwable? = null,
) : RouteBookmarkRepository {
    val savedBookmarks = MutableStateFlow(savedBookmarks)
    val savedRequests = mutableListOf<RouteBookmarkSaveRequest>()
    val deletedBookmarkIds = mutableListOf<String>()

    override fun observeRouteBookmarks(): Flow<List<RouteBookmark>> = savedBookmarks

    override suspend fun isBookmarked(draft: RouteBookmarkDraft): Boolean =
        savedBookmarks.value.any { bookmark ->
            bookmark.startPoint == draft.startPoint &&
                bookmark.endPoint == draft.endPoint &&
                bookmark.routeOption == draft.routeOption
        }

    override suspend fun getRouteBookmarkDetail(bookmarkId: String): RouteBookmarkDetail? = null

    override suspend fun saveRouteBookmark(request: RouteBookmarkSaveRequest): RouteBookmark {
        saveFailure?.let { throw it }
        savedRequests.add(request)
        val savedBookmark = testRouteBookmark(request = request)
        savedBookmarks.value = listOf(savedBookmark)
        return savedBookmark
    }

    override suspend fun deleteRouteBookmark(bookmarkId: String) {
        deletedBookmarkIds += bookmarkId
        savedBookmarks.value = savedBookmarks.value.filterNot { bookmark -> bookmark.bookmarkId == bookmarkId }
    }
}

private class FakeArrivalRouteRepository(
    private val ratingFailure: Throwable? = null,
) : RouteRepository {
    val ratingCalls = mutableListOf<Pair<String, Int>>()

    override suspend fun getRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        RouteSearchData(query, RouteSearchResult(query.origin, query.destination), RouteSearchSource.serverApi())

    override suspend fun getTransitRouteSearchData(query: RouteSearchQuery): RouteSearchData =
        RouteSearchData(query, RouteSearchResult(query.origin, query.destination), RouteSearchSource.serverApi())

    override suspend fun selectRoute(
        routeId: String,
        searchId: String,
    ): RouteSessionData = RouteSessionData(sessionId = "selected-session")

    override suspend fun refreshTransit(
        routeId: String,
        legSequence: Int,
    ): RouteTransitRefreshData = error("Not used")

    override suspend fun reroute(
        routeId: String,
        currentPoint: GeoCoordinate,
    ): RouteRerouteData = error("Not used")

    override suspend fun endRoute(routeId: String): RouteSessionData = RouteSessionData(sessionId = "ended-session")

    override suspend fun rateRoute(
        sessionId: String,
        score: Int,
    ): RouteRatingData {
        ratingFailure?.let { throw it }
        ratingCalls += sessionId to score
        return RouteRatingData(ratingId = 1L)
    }
}

private fun testRouteBookmarkDraft(): RouteBookmarkDraft =
    RouteBookmarkDraft(
        routeId = "walk_rt_safe_001",
        startLabel = "Busan City Hall",
        endLabel = "Haeundae Park",
        startPoint = GeoCoordinate(latitude = 35.1798, longitude = 129.0750),
        endPoint = GeoCoordinate(latitude = 35.1587, longitude = 129.1604),
        routeOption = RouteOption.SAFE,
        distanceMeters = 11_200,
        durationMinutes = 28,
    )

private fun testUnsavableRouteBookmarkDraft(): RouteBookmarkDraft =
    testRouteBookmarkDraft().copy(routeId = null)

private fun testRouteBookmark(
    draft: RouteBookmarkDraft,
): RouteBookmark =
    RouteBookmark(
        bookmarkId = "route-bookmark:${draft.routeId}",
        routeName = draft.defaultRouteName,
        startLabel = draft.startLabel,
        endLabel = draft.endLabel,
        startPoint = draft.startPoint,
        endPoint = draft.endPoint,
        routeOption = draft.routeOption,
        distanceMeters = draft.distanceMeters,
        durationMinutes = draft.durationMinutes,
        createdAt = 1L,
        updatedAt = 1L,
    )

private fun testRouteBookmark(
    request: RouteBookmarkSaveRequest,
): RouteBookmark =
    RouteBookmark(
        bookmarkId = "route-bookmark:test",
        routeName = request.routeName,
        startLabel = request.startLabel,
        endLabel = request.endLabel,
        startPoint = request.startPoint,
        endPoint = request.endPoint,
        routeOption = request.routeOption,
        distanceMeters = request.distanceMeters,
        durationMinutes = request.durationMinutes,
        createdAt = 1L,
        updatedAt = 1L,
    )
