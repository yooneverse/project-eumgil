package com.ssafy.e102.eumgil.feature.savedroute

import com.ssafy.e102.eumgil.core.location.CurrentLocationManager
import com.ssafy.e102.eumgil.core.location.LocationSnapshot
import com.ssafy.e102.eumgil.core.model.AuthGateState
import com.ssafy.e102.eumgil.core.model.AuthSession
import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.core.model.PlaceCategory
import com.ssafy.e102.eumgil.core.model.RecentDestination
import com.ssafy.e102.eumgil.core.model.RecentSearch
import com.ssafy.e102.eumgil.core.model.RouteCandidate
import com.ssafy.e102.eumgil.core.model.RouteBookmark
import com.ssafy.e102.eumgil.core.model.RouteBookmarkDetail
import com.ssafy.e102.eumgil.core.model.RouteBookmarkDraft
import com.ssafy.e102.eumgil.core.model.RouteBookmarkSaveRequest
import com.ssafy.e102.eumgil.core.model.RouteOption
import com.ssafy.e102.eumgil.core.model.RoutePolyline
import com.ssafy.e102.eumgil.core.model.RoutePreviewModel
import com.ssafy.e102.eumgil.core.model.RouteRiskLevel
import com.ssafy.e102.eumgil.core.model.RouteSearchSource
import com.ssafy.e102.eumgil.core.model.RouteSummary
import com.ssafy.e102.eumgil.core.model.RouteWaypoint
import com.ssafy.e102.eumgil.core.model.SearchQuery
import com.ssafy.e102.eumgil.core.model.SearchResult
import com.ssafy.e102.eumgil.data.repository.BookmarkData
import com.ssafy.e102.eumgil.data.repository.BookmarkRepository
import com.ssafy.e102.eumgil.data.repository.InMemoryDestinationPreviewRepository
import com.ssafy.e102.eumgil.data.repository.InMemoryDestinationSelectionRepository
import com.ssafy.e102.eumgil.data.repository.RouteBookmarkRepository
import com.ssafy.e102.eumgil.data.repository.RouteEditingTarget
import com.ssafy.e102.eumgil.data.repository.SearchRepository
import com.ssafy.e102.eumgil.data.repository.TestAuthSessionRepository
import com.ssafy.e102.eumgil.feature.route.RouteNavigationRequest
import kotlinx.coroutines.CompletableDeferred
import com.ssafy.e102.eumgil.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SavedRouteViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `place bookmark list renders content state`() =
        runTest {
            val viewModel =
                SavedRouteViewModel(
                    bookmarkRepository = FakeBookmarkRepository(bookmarks = listOf(testPlaceBookmark())),
                    routeBookmarkRepository = FakeRouteBookmarkRepository(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                )

            advanceUntilIdle()

            assertEquals(SavedBookmarkContentState.CONTENT, viewModel.uiState.value.placeContent.screenState)
            assertEquals(1, viewModel.uiState.value.placeContent.places.size)
            assertEquals("부산역 KTX", viewModel.uiState.value.placeContent.places.first().name)
        }

    @Test
    fun `route bookmark list renders content state`() =
        runTest {
            val viewModel =
                SavedRouteViewModel(
                    bookmarkRepository = FakeBookmarkRepository(),
                    routeBookmarkRepository =
                        FakeRouteBookmarkRepository(
                            routeBookmarks = listOf(testRouteBookmark()),
                        ),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                )

            advanceUntilIdle()

            assertEquals(SavedBookmarkContentState.CONTENT, viewModel.uiState.value.routeContent.screenState)
            assertEquals(1, viewModel.uiState.value.routeContent.routes.size)
            assertEquals("부산시청-해운대해수욕장", viewModel.uiState.value.routeContent.routes.first().routeName)
        }

    @Test
    fun `place click requests destination preview and navigates to map`() =
        runTest {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val destinationPreviewRepository = InMemoryDestinationPreviewRepository()
            val viewModel =
                SavedRouteViewModel(
                    bookmarkRepository = FakeBookmarkRepository(bookmarks = listOf(testPlaceBookmark())),
                    routeBookmarkRepository = FakeRouteBookmarkRepository(),
                    destinationSelectionRepository = destinationSelectionRepository,
                    destinationPreviewRepository = destinationPreviewRepository,
                )

            advanceUntilIdle()
            val uiEvent = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiEvent.first() }

            viewModel.onAction(SavedRouteUiAction.PlaceClicked(placeId = "bookmark-place-1"))
            advanceUntilIdle()

            val preview = destinationPreviewRepository.pendingPreview.value

            assertEquals(SavedRouteUiEvent.NavigateToMap, uiEvent.await())
            assertNull(destinationSelectionRepository.selectedDestination.value)
            assertEquals("bookmark-place-1", preview?.destination?.placeId)
            assertEquals(PlaceCategory.ELEVATOR, preview?.destination?.category)
            assertEquals(RouteEditingTarget.DESTINATION, preview?.editingTarget)
        }

    @Test
    fun `place click does not store recent destination for map home sheet`() =
        runTest {
            val searchRepository = FakeSearchRepository()
            val destinationPreviewRepository = InMemoryDestinationPreviewRepository()
            val viewModel =
                SavedRouteViewModel(
                    bookmarkRepository = FakeBookmarkRepository(bookmarks = listOf(testPlaceBookmark())),
                    routeBookmarkRepository = FakeRouteBookmarkRepository(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    destinationPreviewRepository = destinationPreviewRepository,
                    searchRepository = searchRepository,
                )

            advanceUntilIdle()

            viewModel.onAction(SavedRouteUiAction.PlaceClicked(placeId = "bookmark-place-1"))
            advanceUntilIdle()

            assertTrue(searchRepository.savedRecentDestinations.isEmpty())
            assertEquals("bookmark-place-1", destinationPreviewRepository.pendingPreview.value?.destination?.placeId)
            /*
            assertEquals(1, searchRepository.savedRecentDestinations.size)
            assertEquals(
                RecentDestination(
                    placeId = "bookmark-place-1",
                    name = "éºÂ€?ê³—ë¿­ KTX",
                    address = "éºÂ€???ìˆ†ëŽ„ ä»¥ë¬’ë¸°?Â€æ¿¡?206",
                    latitude = 35.1151,
                    longitude = 129.0415,
                    category = PlaceCategory.ELEVATOR,
                    searchedAtMillis = 0L,
                ),
                searchRepository.savedRecentDestinations.single().copy(searchedAtMillis = 0L),
            )
            */
        }

    @Test
    fun `place click does not wait for recent destination save before navigating to map`() =
        runTest {
            val searchRepository = FakeSearchRepository(saveGate = CompletableDeferred())
            val destinationPreviewRepository = InMemoryDestinationPreviewRepository()
            val viewModel =
                SavedRouteViewModel(
                    bookmarkRepository = FakeBookmarkRepository(bookmarks = listOf(testPlaceBookmark())),
                    routeBookmarkRepository = FakeRouteBookmarkRepository(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    destinationPreviewRepository = destinationPreviewRepository,
                    searchRepository = searchRepository,
                )

            advanceUntilIdle()
            val uiEvent = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiEvent.first() }

            viewModel.onAction(SavedRouteUiAction.PlaceClicked(placeId = "bookmark-place-1"))
            runCurrent()

            assertEquals(SavedRouteUiEvent.NavigateToMap, uiEvent.await())
            assertEquals(0, searchRepository.pendingSaveCount)
            assertTrue(searchRepository.savedRecentDestinations.isEmpty())
            assertEquals("bookmark-place-1", destinationPreviewRepository.pendingPreview.value?.destination?.placeId)
        }

    @Test
    fun `place route guide click stores destination and navigates to route setting`() =
        runTest {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val viewModel =
                SavedRouteViewModel(
                    bookmarkRepository = FakeBookmarkRepository(bookmarks = listOf(testPlaceBookmark())),
                    routeBookmarkRepository = FakeRouteBookmarkRepository(),
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()
            val uiEvent = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiEvent.first() }

            viewModel.onAction(SavedRouteUiAction.PlaceRouteGuideClicked(placeId = "bookmark-place-1"))
            advanceUntilIdle()

            val destination = destinationSelectionRepository.selectedDestination.value

            assertEquals(SavedRouteUiEvent.NavigateToRouteSetting(), uiEvent.await())
            assertEquals("bookmark-place-1", destination?.placeId)
            assertEquals(PlaceCategory.ELEVATOR, destination?.category)
        }

    @Test
    fun `place briefing click stores destination and navigates to low vision route briefing`() =
        runTest {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val viewModel =
                SavedRouteViewModel(
                    bookmarkRepository = FakeBookmarkRepository(bookmarks = listOf(testPlaceBookmark())),
                    routeBookmarkRepository = FakeRouteBookmarkRepository(),
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()
            val uiEvent = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiEvent.first() }

            viewModel.onAction(SavedRouteUiAction.PlaceBriefingClicked(placeId = "bookmark-place-1"))
            advanceUntilIdle()

            val destination = destinationSelectionRepository.selectedDestination.value

            assertEquals(SavedRouteUiEvent.NavigateToRouteBriefing, uiEvent.await())
            assertEquals("bookmark-place-1", destination?.placeId)
            assertEquals(PlaceCategory.ELEVATOR, destination?.category)
        }

    @Test
    fun `route guide click stores saved end point and navigates with saved route option`() =
        runTest {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val viewModel =
                SavedRouteViewModel(
                    bookmarkRepository = FakeBookmarkRepository(),
                    routeBookmarkRepository =
                        FakeRouteBookmarkRepository(
                            routeBookmarks = listOf(testRouteBookmark()),
                        ),
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()
            val uiEvent = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiEvent.first() }

            viewModel.onAction(SavedRouteUiAction.RouteGuideClicked(bookmarkId = "route-bookmark-1"))
            advanceUntilIdle()

            val destination = destinationSelectionRepository.selectedDestination.value

            assertEquals(
                SavedRouteUiEvent.NavigateToRouteSetting(initialRouteOption = RouteOption.SHORTEST),
                uiEvent.await(),
            )
            assertEquals("route-bookmark:route-bookmark-1", destination?.placeId)
            assertEquals("광안리해변", destination?.name)
            assertEquals(35.1532, destination?.latitude)
            assertEquals(129.1186, destination?.longitude)

            val origin = destinationSelectionRepository.selectedOrigin.value
            assertEquals("route-bookmark-origin:route-bookmark-1", origin?.placeId)
            assertEquals("부산시청", origin?.name)
            assertEquals(35.1798, origin?.latitude)
            assertEquals(129.0750, origin?.longitude)
            assertEquals(
                RouteEditingTarget.DESTINATION,
                destinationSelectionRepository.editingTarget.value,
            )
        }

    @Test
    fun `route setting click navigates to empty route setting flow`() =
        runTest {
            val viewModel =
                SavedRouteViewModel(
                    bookmarkRepository = FakeBookmarkRepository(),
                    routeBookmarkRepository = FakeRouteBookmarkRepository(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                )

            advanceUntilIdle()
            val uiEvent = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiEvent.first() }

            viewModel.onAction(SavedRouteUiAction.RouteSettingClicked)
            advanceUntilIdle()

            assertEquals(SavedRouteUiEvent.NavigateToRouteSetting(), uiEvent.await())
        }

    @Test
    fun `route guide click uses favorite route detail for direct navigation when detail snapshot is available`() =
        runTest {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val detail = testRouteBookmarkDetail()
            val viewModel =
                SavedRouteViewModel(
                    bookmarkRepository = FakeBookmarkRepository(),
                    routeBookmarkRepository =
                        FakeRouteBookmarkRepository(
                            routeBookmarks = listOf(testRouteBookmark()),
                            routeBookmarkDetail = detail,
                        ),
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()
            val uiEvent = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiEvent.first() }

            viewModel.onAction(SavedRouteUiAction.RouteGuideClicked(bookmarkId = "route-bookmark-1"))
            advanceUntilIdle()

            val event = uiEvent.await()
            assertTrue(event is SavedRouteUiEvent.NavigateToNavigation)
            val request = (event as SavedRouteUiEvent.NavigateToNavigation).request
            assertEquals("bookmark-detail-route-1", request.selectedRoute.serverRouteId)
            assertEquals(RouteOption.SHORTEST, request.selectedRoute.routeOption)
            assertEquals("부산시청", request.origin.name)
            assertEquals("광안리해변", request.destination.name)
            assertNull(destinationSelectionRepository.selectedOrigin.value)
            assertNull(destinationSelectionRepository.selectedDestination.value)
        }

    @Test
    fun `route card click uses favorite route detail for route detail when detail snapshot is available`() =
        runTest {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val detail = testRouteBookmarkDetail()
            val viewModel =
                SavedRouteViewModel(
                    bookmarkRepository = FakeBookmarkRepository(),
                    routeBookmarkRepository =
                        FakeRouteBookmarkRepository(
                            routeBookmarks = listOf(testRouteBookmark()),
                            routeBookmarkDetail = detail,
                        ),
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()
            val uiEvent = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiEvent.first() }

            viewModel.onAction(SavedRouteUiAction.RouteClicked(bookmarkId = "route-bookmark-1"))
            advanceUntilIdle()

            val event = uiEvent.await()
            assertTrue(event is SavedRouteUiEvent.NavigateToRouteDetail)
            val request = (event as SavedRouteUiEvent.NavigateToRouteDetail).request
            assertEquals("bookmark-detail-route-1", request.selectedRoute.serverRouteId)
            assertEquals(RouteOption.SHORTEST, request.selectedRoute.routeOption)
            assertEquals(detail.startLabel, request.origin.name)
            assertEquals(detail.endLabel, request.destination.name)
            assertNull(destinationSelectionRepository.selectedOrigin.value)
            assertNull(destinationSelectionRepository.selectedDestination.value)
        }

    @Test
    fun `route guide click with invalid origin coordinate keeps user on saved route screen`() =
        runTest {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val viewModel =
                SavedRouteViewModel(
                    bookmarkRepository = FakeBookmarkRepository(),
                    routeBookmarkRepository =
                        FakeRouteBookmarkRepository(
                            routeBookmarks =
                                listOf(testRouteBookmark(startPoint = GeoCoordinate(Double.NaN, 129.0750))),
                        ),
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()
            val uiEvent = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiEvent.first() }

            viewModel.onAction(SavedRouteUiAction.RouteGuideClicked(bookmarkId = "route-bookmark-1"))
            advanceUntilIdle()

            assertEquals(
                SavedRouteUiEvent.ShowSnackbar("저장한 경로의 좌표가 올바르지 않습니다."),
                uiEvent.await(),
            )
            assertNull(destinationSelectionRepository.selectedOrigin.value)
            assertNull(destinationSelectionRepository.selectedDestination.value)
            assertEquals(
                "저장한 경로의 좌표가 올바르지 않습니다.",
                viewModel.uiState.value.routeContent.errorMessage,
            )
        }

    @Test
    fun `place click resets editingTarget to DESTINATION on handoff`() =
        runTest {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val destinationPreviewRepository = InMemoryDestinationPreviewRepository()
            destinationSelectionRepository.setEditingTarget(RouteEditingTarget.ORIGIN)
            val viewModel =
                SavedRouteViewModel(
                    bookmarkRepository = FakeBookmarkRepository(bookmarks = listOf(testPlaceBookmark())),
                    routeBookmarkRepository = FakeRouteBookmarkRepository(),
                    destinationSelectionRepository = destinationSelectionRepository,
                    destinationPreviewRepository = destinationPreviewRepository,
                )

            advanceUntilIdle()

            viewModel.onAction(SavedRouteUiAction.PlaceClicked(placeId = "bookmark-place-1"))
            advanceUntilIdle()

            assertEquals(
                RouteEditingTarget.DESTINATION,
                destinationPreviewRepository.pendingPreview.value?.editingTarget,
            )
        }

    @Test
    fun `route guide click with invalid coordinate keeps user on saved route screen`() =
        runTest {
            val destinationSelectionRepository = InMemoryDestinationSelectionRepository()
            val viewModel =
                SavedRouteViewModel(
                    bookmarkRepository = FakeBookmarkRepository(),
                    routeBookmarkRepository =
                        FakeRouteBookmarkRepository(
                            routeBookmarks = listOf(testRouteBookmark(endPoint = GeoCoordinate(Double.NaN, 129.1186))),
                        ),
                    destinationSelectionRepository = destinationSelectionRepository,
                )

            advanceUntilIdle()
            val uiEvent = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiEvent.first() }

            viewModel.onAction(SavedRouteUiAction.RouteGuideClicked(bookmarkId = "route-bookmark-1"))
            advanceUntilIdle()

            assertEquals(
                SavedRouteUiEvent.ShowSnackbar("저장한 경로의 좌표가 올바르지 않습니다."),
                uiEvent.await(),
            )
            assertNull(destinationSelectionRepository.selectedDestination.value)
            assertEquals(
                "저장한 경로의 좌표가 올바르지 않습니다.",
                viewModel.uiState.value.routeContent.errorMessage,
            )
        }

    @Test
    fun `place bookmark list renders empty state when bookmarks list is empty`() =
        runTest {
            val viewModel =
                SavedRouteViewModel(
                    bookmarkRepository = FakeBookmarkRepository(),
                    routeBookmarkRepository = FakeRouteBookmarkRepository(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                )

            advanceUntilIdle()

            assertEquals(SavedBookmarkContentState.EMPTY, viewModel.uiState.value.placeContent.screenState)
            assertEquals(emptyList<SavedPlaceUiModel>(), viewModel.uiState.value.placeContent.places)
        }

    @Test
    fun `place bookmark list renders error state when observe fails`() =
        runTest {
            val viewModel =
                SavedRouteViewModel(
                    bookmarkRepository = FakeBookmarkRepository(failObserve = true),
                    routeBookmarkRepository = FakeRouteBookmarkRepository(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                )

            advanceUntilIdle()

            assertEquals(SavedBookmarkContentState.ERROR, viewModel.uiState.value.placeContent.screenState)
            assertEquals(
                "북마크한 장소를 불러오지 못했습니다.",
                viewModel.uiState.value.placeContent.errorMessage,
            )
        }

    @Test
    fun `edit mode keeps failed bookmarks pending when partial deletion fails`() =
        runTest {
            val firstBookmark = testPlaceBookmark()
            val secondBookmark =
                testPlaceBookmark().copy(
                    placeId = "bookmark-place-2",
                    placeName = "광안리 해수욕장",
                )
            val bookmarkRepository =
                FakeBookmarkRepository(
                    bookmarks = listOf(firstBookmark, secondBookmark),
                    failingPlaceIds = setOf("bookmark-place-2"),
                )
            val viewModel =
                SavedRouteViewModel(
                    bookmarkRepository = bookmarkRepository,
                    routeBookmarkRepository = FakeRouteBookmarkRepository(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                )

            advanceUntilIdle()

            viewModel.onAction(SavedRouteUiAction.EditClicked)
            viewModel.onAction(SavedRouteUiAction.PlaceDeleteClicked(placeId = "bookmark-place-1"))
            viewModel.onAction(SavedRouteUiAction.PlaceDeleteClicked(placeId = "bookmark-place-2"))
            viewModel.onAction(SavedRouteUiAction.DeleteSelectedClicked)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isEditMode)
            assertEquals(
                setOf("bookmark-place-2"),
                viewModel.uiState.value.pendingPlaceRemovalIds,
            )
            assertEquals(1, bookmarkRepository.bookmarks.value.size)
            assertEquals("bookmark-place-2", bookmarkRepository.bookmarks.value.first().placeId)
            assertEquals(
                "북마크한 장소를 삭제하지 못했습니다. 다시 시도해 주세요.",
                viewModel.uiState.value.placeContent.errorMessage,
            )
        }

    @Test
    fun `edit mode defers place bookmark deletion until selected delete`() =
        runTest {
            val bookmarkRepository = FakeBookmarkRepository(bookmarks = listOf(testPlaceBookmark()))
            val viewModel =
                SavedRouteViewModel(
                    bookmarkRepository = bookmarkRepository,
                    routeBookmarkRepository = FakeRouteBookmarkRepository(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                )

            advanceUntilIdle()

            viewModel.onAction(SavedRouteUiAction.EditClicked)
            viewModel.onAction(SavedRouteUiAction.PlaceDeleteClicked(placeId = "bookmark-place-1"))
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isEditMode)
            assertTrue("bookmark-place-1" in viewModel.uiState.value.pendingPlaceRemovalIds)
            assertEquals(1, viewModel.uiState.value.placeContent.places.size)
            assertEquals(1, bookmarkRepository.bookmarks.value.size)

            viewModel.onAction(SavedRouteUiAction.DeleteSelectedClicked)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isEditMode)
            assertEquals(SavedBookmarkContentState.EMPTY, viewModel.uiState.value.placeContent.screenState)
            assertEquals(emptyList<SavedPlaceUiModel>(), viewModel.uiState.value.placeContent.places)
            assertEquals(emptyList<BookmarkData>(), bookmarkRepository.bookmarks.value)
        }

    @Test
    fun `edit mode defers route bookmark deletion until selected delete`() =
        runTest {
            val routeBookmarkRepository =
                FakeRouteBookmarkRepository(
                    routeBookmarks = listOf(testRouteBookmark()),
                )
            val viewModel =
                SavedRouteViewModel(
                    bookmarkRepository = FakeBookmarkRepository(),
                    routeBookmarkRepository = routeBookmarkRepository,
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                )

            advanceUntilIdle()

            viewModel.onAction(SavedRouteUiAction.EditClicked)
            viewModel.onAction(SavedRouteUiAction.RouteDeleteClicked(bookmarkId = "route-bookmark-1"))
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isEditMode)
            assertTrue("route-bookmark-1" in viewModel.uiState.value.pendingRouteRemovalIds)
            assertEquals(1, viewModel.uiState.value.routeContent.routes.size)
            assertEquals(1, routeBookmarkRepository.routeBookmarks.value.size)

            viewModel.onAction(SavedRouteUiAction.DeleteSelectedClicked)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isEditMode)
            assertEquals(SavedBookmarkContentState.EMPTY, viewModel.uiState.value.routeContent.screenState)
            assertEquals(emptyList<SavedRouteBookmarkUiModel>(), viewModel.uiState.value.routeContent.routes)
        }

    @Test
    fun `edit done exits edit mode without deleting selected bookmarks`() =
        runTest {
            val bookmarkRepository = FakeBookmarkRepository(bookmarks = listOf(testPlaceBookmark()))
            val viewModel =
                SavedRouteViewModel(
                    bookmarkRepository = bookmarkRepository,
                    routeBookmarkRepository = FakeRouteBookmarkRepository(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                )

            advanceUntilIdle()

            viewModel.onAction(SavedRouteUiAction.EditClicked)
            viewModel.onAction(SavedRouteUiAction.PlaceDeleteClicked(placeId = "bookmark-place-1"))
            viewModel.onAction(SavedRouteUiAction.EditDoneClicked)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isEditMode)
            assertTrue(viewModel.uiState.value.pendingPlaceRemovalIds.isEmpty())
            assertEquals(1, bookmarkRepository.bookmarks.value.size)
            assertEquals(SavedBookmarkContentState.CONTENT, viewModel.uiState.value.placeContent.screenState)
        }

    @Test
    fun `auth scope change resets stale edit state and reloads saved places`() =
        runTest {
            val authSessionRepository =
                TestAuthSessionRepository(
                    initialState =
                        AuthGateState(
                            authSession = AuthSession(accessToken = "token-a", userId = "user-a"),
                            isProfileCompleted = true,
                        ),
                )
            val bookmarkRepository = FakeBookmarkRepository(bookmarks = listOf(testPlaceBookmark()))
            val viewModel =
                SavedRouteViewModel(
                    authSessionRepository = authSessionRepository,
                    bookmarkRepository = bookmarkRepository,
                    routeBookmarkRepository = FakeRouteBookmarkRepository(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                )

            advanceUntilIdle()
            viewModel.onAction(SavedRouteUiAction.EditClicked)
            viewModel.onAction(SavedRouteUiAction.PlaceDeleteClicked(placeId = "bookmark-place-1"))

            bookmarkRepository.bookmarks.value =
                listOf(
                    testPlaceBookmark().copy(
                        placeId = "bookmark-place-2",
                        placeName = "광안리 해변",
                    ),
                )
            authSessionRepository.updateAuthSession(
                authSession = AuthSession(accessToken = "token-b", userId = "user-b"),
                isProfileCompleted = true,
            )
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isEditMode)
            assertTrue(viewModel.uiState.value.pendingPlaceRemovalIds.isEmpty())
            assertEquals(
                listOf("bookmark-place-2"),
                viewModel.uiState.value.placeContent.places.map(SavedPlaceUiModel::placeId),
            )
        }

    @Test
    fun `low vision place bookmark list reorders by nearest current location when location becomes available`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val bookmarkRepository =
                FakeBookmarkRepository(
                    bookmarks =
                        listOf(
                            testPlaceBookmark(
                                latitude = 35.1151,
                                longitude = 129.0415,
                            ).copy(
                                placeId = "far-place",
                                placeName = "Far Place",
                            ),
                            testPlaceBookmark(
                                latitude = 35.1798,
                                longitude = 129.0750,
                            ).copy(
                                placeId = "near-place",
                                placeName = "Near Place",
                            ),
                        ),
                )
            val viewModel =
                SavedRouteViewModel(
                    bookmarkRepository = bookmarkRepository,
                    routeBookmarkRepository = FakeRouteBookmarkRepository(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    currentLocationManager = locationManager,
                    initialLowVisionMode = true,
                )

            advanceUntilIdle()
            assertEquals(
                listOf("far-place", "near-place"),
                viewModel.uiState.value.placeContent.places.map(SavedPlaceUiModel::placeId),
            )

            locationManager.emitLocation(
                LocationSnapshot(
                    latitude = 35.1796,
                    longitude = 129.0756,
                    accuracyMeters = 5f,
                    recordedAtEpochMillis = 1_000L,
                ),
            )
            advanceUntilIdle()

            assertEquals(
                listOf("near-place", "far-place"),
                viewModel.uiState.value.placeContent.places.map(SavedPlaceUiModel::placeId),
            )
        }

    @Test
    fun `low vision place bookmark list ignores tiny location shifts until movement is meaningful`() =
        runTest {
            val locationManager = FakeCurrentLocationManager()
            val bookmarkRepository =
                FakeBookmarkRepository(
                    bookmarks =
                        listOf(
                            testPlaceBookmark(
                                latitude = 35.1796,
                                longitude = 129.07500,
                            ).copy(
                                placeId = "alpha-place",
                                placeName = "Alpha Place",
                            ),
                            testPlaceBookmark(
                                latitude = 35.1796,
                                longitude = 129.07560,
                            ).copy(
                                placeId = "beta-place",
                                placeName = "Beta Place",
                            ),
                        ),
                )
            val viewModel =
                SavedRouteViewModel(
                    bookmarkRepository = bookmarkRepository,
                    routeBookmarkRepository = FakeRouteBookmarkRepository(),
                    destinationSelectionRepository = InMemoryDestinationSelectionRepository(),
                    currentLocationManager = locationManager,
                    initialLowVisionMode = true,
                )

            advanceUntilIdle()

            locationManager.emitLocation(
                LocationSnapshot(
                    latitude = 35.1796,
                    longitude = 129.07510,
                    accuracyMeters = 5f,
                    recordedAtEpochMillis = 1_000L,
                ),
            )
            advanceUntilIdle()

            val initialOrder = viewModel.uiState.value.placeContent.places.map(SavedPlaceUiModel::placeId)
            assertEquals(
                listOf(2, 2),
                listOf(initialOrder.size, initialOrder.toSet().size),
            )

            locationManager.emitLocation(
                LocationSnapshot(
                    latitude = 35.1796,
                    longitude = 129.07518,
                    accuracyMeters = 5f,
                    recordedAtEpochMillis = 2_000L,
                ),
            )
            advanceUntilIdle()

            assertEquals(
                initialOrder,
                viewModel.uiState.value.placeContent.places.map(SavedPlaceUiModel::placeId),
            )

            locationManager.emitLocation(
                LocationSnapshot(
                    latitude = 35.1796,
                    longitude = 129.07552,
                    accuracyMeters = 5f,
                    recordedAtEpochMillis = 3_000L,
                ),
            )
            advanceUntilIdle()

            assertEquals(
                initialOrder.reversed(),
                viewModel.uiState.value.placeContent.places.map(SavedPlaceUiModel::placeId),
            )
        }
}

private class FakeBookmarkRepository(
    bookmarks: List<BookmarkData> = emptyList(),
    private val failObserve: Boolean = false,
    private val failDelete: Boolean = false,
    private val failingPlaceIds: Set<String> = emptySet(),
) : BookmarkRepository {
    val bookmarks = MutableStateFlow(bookmarks)

    override fun observeBookmarks(): Flow<List<BookmarkData>> =
        if (failObserve) {
            flow { error("bookmark load failed") }
        } else {
            bookmarks
        }

    override suspend fun isBookmarked(placeId: String): Boolean =
        bookmarks.value.any { bookmark -> bookmark.placeId == placeId }

    override suspend fun saveBookmark(bookmark: BookmarkData): BookmarkData {
        bookmarks.value = bookmarks.value.filterNot { it.placeId == bookmark.placeId } + bookmark
        return bookmark
    }

    override suspend fun deleteBookmark(placeId: String) {
        if (failDelete || placeId in failingPlaceIds) error("bookmark delete failed")
        bookmarks.value = bookmarks.value.filterNot { bookmark -> bookmark.placeId == placeId }
    }
}

private class FakeCurrentLocationManager : CurrentLocationManager {
    private val mutableLatestLocation = MutableStateFlow<LocationSnapshot?>(null)

    override val latestLocation: StateFlow<LocationSnapshot?> = mutableLatestLocation

    override fun refreshLatestLocation() = Unit

    override fun startLocationUpdates() = Unit

    override fun stopLocationUpdates() = Unit

    fun emitLocation(snapshot: LocationSnapshot?) {
        mutableLatestLocation.value = snapshot
    }
}

private class FakeRouteBookmarkRepository(
    routeBookmarks: List<RouteBookmark> = emptyList(),
    private val routeBookmarkDetail: RouteBookmarkDetail? = null,
) : RouteBookmarkRepository {
    val routeBookmarks = MutableStateFlow(routeBookmarks)

    override fun observeRouteBookmarks(): Flow<List<RouteBookmark>> = routeBookmarks

    override suspend fun isBookmarked(draft: RouteBookmarkDraft): Boolean =
        routeBookmarks.value.any { bookmark ->
            bookmark.startPoint == draft.startPoint &&
                bookmark.endPoint == draft.endPoint &&
                bookmark.routeOption == draft.routeOption
        }

    override suspend fun saveRouteBookmark(request: RouteBookmarkSaveRequest): RouteBookmark {
        val savedBookmark =
            RouteBookmark(
                bookmarkId = "route-bookmark-1",
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
        routeBookmarks.value = listOf(savedBookmark)
        return savedBookmark
    }

    override suspend fun getRouteBookmarkDetail(bookmarkId: String): RouteBookmarkDetail? = routeBookmarkDetail

    override suspend fun deleteRouteBookmark(bookmarkId: String) {
        routeBookmarks.value =
            routeBookmarks.value.filterNot { bookmark ->
                bookmark.bookmarkId == bookmarkId
            }
    }
}

private class FakeSearchRepository(
    private val saveGate: CompletableDeferred<Unit>? = null,
) : SearchRepository {
    val savedRecentDestinations = mutableListOf<RecentDestination>()
    var pendingSaveCount: Int = 0
        private set

    override suspend fun search(query: SearchQuery): List<SearchResult> = emptyList()

    override suspend fun getRecentSearches(): List<RecentSearch> = emptyList()

    override suspend fun saveRecentSearch(keyword: String) = Unit

    override suspend fun getRecentDestinations(): List<RecentDestination> = savedRecentDestinations

    override suspend fun saveRecentDestination(destination: RecentDestination) {
        pendingSaveCount += 1
        saveGate?.await()
        savedRecentDestinations += destination
    }

    fun allowPendingSave() {
        saveGate?.complete(Unit)
    }
}

private fun testPlaceBookmark(
    latitude: Double = 35.1151,
    longitude: Double = 129.0415,
): BookmarkData =
    BookmarkData(
        placeId = "bookmark-place-1",
        placeName = "부산역 KTX",
        address = "부산 동구 중앙대로 206",
        latitude = latitude,
        longitude = longitude,
        category = "ELEVATOR",
    )

private fun testRouteBookmark(
    startPoint: GeoCoordinate = GeoCoordinate(latitude = 35.1798, longitude = 129.0750),
    endPoint: GeoCoordinate = GeoCoordinate(latitude = 35.1532, longitude = 129.1186),
): RouteBookmark =
    RouteBookmark(
        bookmarkId = "route-bookmark-1",
        routeName = "부산시청-해운대해수욕장",
        startLabel = "부산시청",
        endLabel = "광안리해변",
        startPoint = startPoint,
        endPoint = endPoint,
        routeOption = RouteOption.SHORTEST,
        distanceMeters = 7_600,
        durationMinutes = 21,
        createdAt = 1L,
        updatedAt = 1L,
    )

private fun testRouteBookmarkDetail(): RouteBookmarkDetail =
    RouteBookmarkDetail(
        bookmarkId = "route-bookmark-1",
        routeName = "부산시청-광안리해변",
        startLabel = "부산시청",
        endLabel = "광안리해변",
        startPoint = GeoCoordinate(latitude = 35.1798, longitude = 129.0750),
        endPoint = GeoCoordinate(latitude = 35.1532, longitude = 129.1186),
        transportMode = "WALK",
        routeOptionLabel = "SHORTEST",
        route =
            RouteCandidate(
                routeId = "bookmark-detail-route-1",
                serverRouteId = "bookmark-detail-route-1",
                routeOption = RouteOption.SHORTEST,
                title = "Stored Route",
                summary =
                    RouteSummary(
                        distanceMeters = 7_600,
                        estimatedTimeMinutes = 21,
                        riskLevel = RouteRiskLevel.LOW,
                    ),
                geometry =
                    RoutePolyline(
                        points =
                            listOf(
                                GeoCoordinate(latitude = 35.1798, longitude = 129.0750),
                                GeoCoordinate(latitude = 35.1665, longitude = 129.0960),
                                GeoCoordinate(latitude = 35.1532, longitude = 129.1186),
                            ),
                    ),
                preview =
                    RoutePreviewModel(
                        polyline =
                            RoutePolyline(
                                points =
                                    listOf(
                                        GeoCoordinate(latitude = 35.1798, longitude = 129.0750),
                                        GeoCoordinate(latitude = 35.1665, longitude = 129.0960),
                                        GeoCoordinate(latitude = 35.1532, longitude = 129.1186),
                                    ),
                            ),
                        segmentCount = 1,
                        renderableSegmentCount = 1,
                    ),
            ),
    )
