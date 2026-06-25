package com.ssafy.e102.eumgil.data.repository

import com.ssafy.e102.eumgil.core.model.AuthGateState
import com.ssafy.e102.eumgil.core.model.AuthSession
import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.core.model.RouteCandidate
import com.ssafy.e102.eumgil.core.model.RouteBookmarkDraft
import com.ssafy.e102.eumgil.core.model.RouteBookmarkSaveRequest
import com.ssafy.e102.eumgil.core.model.RouteOption
import com.ssafy.e102.eumgil.core.model.RoutePolyline
import com.ssafy.e102.eumgil.core.model.RoutePreviewModel
import com.ssafy.e102.eumgil.core.model.RouteRiskLevel
import com.ssafy.e102.eumgil.core.model.RouteSegment
import com.ssafy.e102.eumgil.core.model.RouteSummary
import com.ssafy.e102.eumgil.data.local.dao.FavoriteRouteDao
import com.ssafy.e102.eumgil.data.local.entity.FavoriteRouteEntity
import com.ssafy.e102.eumgil.data.remote.HttpJsonClient
import com.ssafy.e102.eumgil.data.remote.datasource.FavoriteRoutesRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.dto.CreateFavoriteRouteResponseDto
import com.ssafy.e102.eumgil.data.remote.dto.FavoriteRouteDetailDto
import com.ssafy.e102.eumgil.data.remote.dto.FavoriteRouteListItemDto
import com.ssafy.e102.eumgil.data.remote.dto.FavoriteRoutePageDto
import com.ssafy.e102.eumgil.data.remote.dto.FavoriteRoutePointDto
import com.ssafy.e102.eumgil.data.route.RouteDto
import com.ssafy.e102.eumgil.data.route.RouteSegmentDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteBookmarkRepositoryTest {
    @Test
    fun `observeRouteBookmarks fetches from server and replaces cache when token is provided`() =
        runBlocking {
            val serverItem =
                FavoriteRouteListItemDto(
                    favRouteId = 7L,
                    routeName = "집에서 병원",
                    startLabel = "부산시민공원",
                    endLabel = "부산역",
                    startPoint = FavoriteRoutePointDto(lat = 35.1686, lng = 129.0576),
                    endPoint = FavoriteRoutePointDto(lat = 35.1152, lng = 129.0422),
                    transportMode = "WALK",
                    routeOption = "SAFE",
                )
            val staleEntity = testFavoriteRouteEntity(favoriteRouteId = 99L, routeName = "stale-cache")
            val fakeDao = FakeFavoriteRouteDao(routes = listOf(staleEntity))
            val fakeDataSource = FakeFavoriteRoutesRemoteDataSource(serverContent = listOf(serverItem))

            val repository =
                DefaultRouteBookmarkRepository(
                    favoriteRouteDao = fakeDao,
                    favoriteRoutesRemoteDataSource = fakeDataSource,
                    accessTokenProvider = { "test-token" },
                )

            val bookmarks = repository.observeRouteBookmarks().first()

            assertEquals(1, bookmarks.size)
            assertEquals("7", bookmarks[0].bookmarkId)
            assertEquals("집에서 병원", bookmarks[0].routeName)
            assertEquals(RouteOption.SAFE, bookmarks[0].routeOption)
            assertEquals("WALK", bookmarks[0].transportMode)
            assertEquals("SAFE", bookmarks[0].routeOptionLabel)
        }

    @Test
    fun `observeRouteBookmarks preserves cached distance and duration after server refresh`() =
        runBlocking {
            val cachedEntity =
                testFavoriteRouteEntity(favoriteRouteId = 7L, routeName = "cached")
                    .copy(summaryDistanceMeters = 3250, summaryDurationSeconds = 1440)
            val serverItem =
                FavoriteRouteListItemDto(
                    favRouteId = 7L,
                    routeName = "renamed",
                    startLabel = "출발",
                    endLabel = "도착",
                    startPoint = FavoriteRoutePointDto(lat = 35.0, lng = 129.0),
                    endPoint = FavoriteRoutePointDto(lat = 35.1, lng = 129.1),
                    transportMode = "WALK",
                    routeOption = "SAFE",
                )
            val fakeDao = FakeFavoriteRouteDao(routes = listOf(cachedEntity))
            val fakeDataSource = FakeFavoriteRoutesRemoteDataSource(serverContent = listOf(serverItem))

            val repository =
                DefaultRouteBookmarkRepository(
                    favoriteRouteDao = fakeDao,
                    favoriteRoutesRemoteDataSource = fakeDataSource,
                    accessTokenProvider = { "test-token" },
                )

            val bookmarks = repository.observeRouteBookmarks().first()

            assertEquals(1, bookmarks.size)
            assertEquals(3250, bookmarks[0].distanceMeters)
            assertEquals(24, bookmarks[0].durationMinutes)
            assertEquals("renamed", bookmarks[0].routeName)
        }

    @Test
    fun `observeRouteBookmarks fetches every server page before replacing cache`() =
        runBlocking {
            val firstPageItem =
                FavoriteRouteListItemDto(
                    favRouteId = 7L,
                    routeName = "first-page",
                    startLabel = "start-a",
                    endLabel = "end-a",
                    startPoint = FavoriteRoutePointDto(lat = 35.0, lng = 129.0),
                    endPoint = FavoriteRoutePointDto(lat = 35.1, lng = 129.1),
                    transportMode = "WALK",
                    routeOption = "SAFE",
                )
            val secondPageItem =
                FavoriteRouteListItemDto(
                    favRouteId = 8L,
                    routeName = "second-page",
                    startLabel = "start-b",
                    endLabel = "end-b",
                    startPoint = FavoriteRoutePointDto(lat = 35.2, lng = 129.2),
                    endPoint = FavoriteRoutePointDto(lat = 35.3, lng = 129.3),
                    transportMode = "WALK",
                    routeOption = "SHORTEST",
                )
            val staleEntity = testFavoriteRouteEntity(favoriteRouteId = 99L, routeName = "stale-cache")
            val fakeDao = FakeFavoriteRouteDao(routes = listOf(staleEntity))
            val fakeDataSource =
                FakeFavoriteRoutesRemoteDataSource(
                    pagesByCursor =
                        mapOf(
                            null to
                                FavoriteRoutePageDto(
                                    content = listOf(firstPageItem),
                                    size = 50,
                                    nextCursor = 50L,
                                    hasNext = true,
                                ),
                            50L to
                                FavoriteRoutePageDto(
                                    content = listOf(secondPageItem),
                                    size = 50,
                                    nextCursor = null,
                                    hasNext = false,
                                ),
                        ),
                )

            val repository =
                DefaultRouteBookmarkRepository(
                    favoriteRouteDao = fakeDao,
                    favoriteRoutesRemoteDataSource = fakeDataSource,
                    accessTokenProvider = { "test-token" },
                )

            val bookmarks = repository.observeRouteBookmarks().first()

            assertEquals(listOf(null, 50L), fakeDataSource.requestedCursors)
            assertEquals(listOf("7", "8"), bookmarks.map { it.bookmarkId })
            assertFalse(bookmarks.any { it.bookmarkId == "99" })
        }

    @Test
    fun `observeRouteBookmarks drops local only routes during server refresh`() =
        runBlocking {
            val localOnlyEntity =
                testFavoriteRouteEntity(favoriteRouteId = -1L, routeName = "local-only")
                    .copy(routeSnapshotJson = "{\"routeId\":\"local\"}")
            val serverItem =
                FavoriteRouteListItemDto(
                    favRouteId = 7L,
                    routeName = "server-route",
                    startLabel = "start-a",
                    endLabel = "end-a",
                    startPoint = FavoriteRoutePointDto(lat = 35.0, lng = 129.0),
                    endPoint = FavoriteRoutePointDto(lat = 35.1, lng = 129.1),
                    transportMode = "WALK",
                    routeOption = "SAFE",
                )
            val fakeDao = FakeFavoriteRouteDao(routes = listOf(localOnlyEntity))
            val fakeDataSource = FakeFavoriteRoutesRemoteDataSource(serverContent = listOf(serverItem))

            val repository =
                DefaultRouteBookmarkRepository(
                    favoriteRouteDao = fakeDao,
                    favoriteRoutesRemoteDataSource = fakeDataSource,
                    accessTokenProvider = { "test-token" },
                )

            val bookmarks = repository.observeRouteBookmarks().first()

            assertEquals(listOf("7"), bookmarks.map { it.bookmarkId })
            assertFalse(fakeDao.routes().any { it.favoriteRouteId == -1L })
        }

    @Test
    fun `observeRouteBookmarks preserves transit transport mode and route option label`() =
        runBlocking {
            val serverItem =
                FavoriteRouteListItemDto(
                    favRouteId = 11L,
                    routeName = "transit-route",
                    startLabel = "출발",
                    endLabel = "도착",
                    startPoint = FavoriteRoutePointDto(lat = 35.0, lng = 129.0),
                    endPoint = FavoriteRoutePointDto(lat = 35.1, lng = 129.1),
                    transportMode = "PUBLIC_TRANSIT",
                    routeOption = "MIN_TRANSFER",
                )
            val fakeDao = FakeFavoriteRouteDao()
            val fakeDataSource = FakeFavoriteRoutesRemoteDataSource(serverContent = listOf(serverItem))

            val repository =
                DefaultRouteBookmarkRepository(
                    favoriteRouteDao = fakeDao,
                    favoriteRoutesRemoteDataSource = fakeDataSource,
                    accessTokenProvider = { "test-token" },
                )

            val bookmarks = repository.observeRouteBookmarks().first()

            assertEquals("PUBLIC_TRANSIT", bookmarks[0].transportMode)
            assertEquals("MIN_TRANSFER", bookmarks[0].routeOptionLabel)
            assertEquals(RouteOption.MIN_TRANSFER, bookmarks[0].routeOption)
        }

    @Test
    fun `observeRouteBookmarks falls back to cache when server fetch fails`() =
        runBlocking {
            val cachedEntity = testFavoriteRouteEntity(favoriteRouteId = 1L, routeName = "cached")
            val fakeDao = FakeFavoriteRouteDao(routes = listOf(cachedEntity))
            val fakeDataSource = FakeFavoriteRoutesRemoteDataSource(throwOnGet = true)

            val repository =
                DefaultRouteBookmarkRepository(
                    favoriteRouteDao = fakeDao,
                    favoriteRoutesRemoteDataSource = fakeDataSource,
                    accessTokenProvider = { "test-token" },
                )

            val bookmarks = repository.observeRouteBookmarks().first()

            assertEquals(1, bookmarks.size)
            assertEquals("cached", bookmarks[0].routeName)
        }

    @Test
    fun `observeRouteBookmarks switches to the new account scope when auth session changes`() =
        runBlocking {
            val authSessionRepository =
                TestAuthSessionRepository(
                    initialState =
                        AuthGateState(
                            authSession = AuthSession(accessToken = "token-a", userId = "user-a"),
                            isProfileCompleted = true,
                        ),
                )
            val fakeDao =
                FakeFavoriteRouteDao(
                    routes =
                        listOf(
                            testFavoriteRouteEntity(
                                favoriteRouteId = 7L,
                                routeName = "route-a",
                                accountScopeKey = "user::user-a",
                            ),
                            testFavoriteRouteEntity(
                                favoriteRouteId = 8L,
                                routeName = "route-b",
                                accountScopeKey = "user::user-b",
                            ),
                        ),
                )
            val repository =
                DefaultRouteBookmarkRepository(
                    favoriteRouteDao = fakeDao,
                    authSessionRepository = authSessionRepository,
                )

            val firstScopeBookmarks = repository.observeRouteBookmarks().first()

            authSessionRepository.updateAuthSession(
                authSession = AuthSession(accessToken = "token-b", userId = "user-b"),
                isProfileCompleted = true,
            )
            val secondScopeBookmarks = repository.observeRouteBookmarks().first()

            assertEquals(listOf("route-a"), firstScopeBookmarks.map { bookmark -> bookmark.routeName })
            assertEquals(listOf("route-b"), secondScopeBookmarks.map { bookmark -> bookmark.routeName })
        }

    @Test
    fun `observeRouteBookmarks emits empty list when auth session is missing`() =
        runBlocking {
            val authSessionRepository =
                TestAuthSessionRepository(
                    initialState = AuthGateState(authSession = null, isProfileCompleted = false),
                )
            val repository =
                DefaultRouteBookmarkRepository(
                    favoriteRouteDao =
                        FakeFavoriteRouteDao(
                            routes =
                                listOf(
                                    testFavoriteRouteEntity(
                                        favoriteRouteId = 7L,
                                        routeName = "route-a",
                                        accountScopeKey = "user::user-a",
                                    ),
                                ),
                        ),
                    authSessionRepository = authSessionRepository,
                )

            val bookmarks = repository.observeRouteBookmarks().first()

            assertTrue(bookmarks.isEmpty())
        }

    @Test
    fun `observeRouteBookmarks skips server fetch when access token is null`() =
        runBlocking {
            val cachedEntity = testFavoriteRouteEntity(favoriteRouteId = 1L, routeName = "cached")
            val fakeDao = FakeFavoriteRouteDao(routes = listOf(cachedEntity))
            val fakeDataSource = FakeFavoriteRoutesRemoteDataSource()

            val repository =
                DefaultRouteBookmarkRepository(
                    favoriteRouteDao = fakeDao,
                    favoriteRoutesRemoteDataSource = fakeDataSource,
                    accessTokenProvider = { null },
                )

            val bookmarks = repository.observeRouteBookmarks().first()

            assertEquals(1, bookmarks.size)
            assertEquals(0, fakeDataSource.getFavoriteRoutesCallCount)
        }

    @Test
    fun `saveRouteBookmark posts to server and returns bookmark with server id`() =
        runBlocking {
            val fakeDao = FakeFavoriteRouteDao()
            val fakeDataSource = FakeFavoriteRoutesRemoteDataSource(createdId = 42L)

            val repository =
                DefaultRouteBookmarkRepository(
                    favoriteRouteDao = fakeDao,
                    favoriteRoutesRemoteDataSource = fakeDataSource,
                    accessTokenProvider = { "test-token" },
                )

            val saved = repository.saveRouteBookmark(testSaveRequest())

            assertEquals("42", saved.bookmarkId)
            assertEquals(1, fakeDataSource.createCallCount)
            assertEquals(listOf("walk_rt_safe_001"), fakeDataSource.createdRouteIds)
            assertEquals(listOf(testSaveRequest().startLabel), fakeDataSource.createdStartLabels)
            assertEquals(listOf(testSaveRequest().endLabel), fakeDataSource.createdEndLabels)
            assertEquals(1, fakeDao.routes().size)
        }

    @Test
    fun `saveRouteBookmark fails when route id is missing and does not cache locally`() =
        runBlocking {
            val fakeDao = FakeFavoriteRouteDao()
            val fakeDataSource = FakeFavoriteRoutesRemoteDataSource(createdId = 42L)

            val repository =
                DefaultRouteBookmarkRepository(
                    favoriteRouteDao = fakeDao,
                    favoriteRoutesRemoteDataSource = fakeDataSource,
                    accessTokenProvider = { "test-token" },
                )

            val result = runCatching { repository.saveRouteBookmark(testSaveRequest(routeId = null)) }

            assertTrue(result.isFailure)
            assertEquals(0, fakeDataSource.createCallCount)
            assertEquals(0, fakeDao.routes().size)
        }

    @Test
    fun `saveRouteBookmark fails without access token and does not cache locally`() =
        runBlocking {
            val fakeDao = FakeFavoriteRouteDao()
            val fakeDataSource = FakeFavoriteRoutesRemoteDataSource()

            val repository =
                DefaultRouteBookmarkRepository(
                    favoriteRouteDao = fakeDao,
                    favoriteRoutesRemoteDataSource = fakeDataSource,
                    accessTokenProvider = { null },
                )

            val result = runCatching { repository.saveRouteBookmark(testSaveRequest()) }

            assertTrue(result.isFailure)
            assertEquals(0, fakeDataSource.createCallCount)
            assertEquals(0, fakeDao.routes().size)
        }

    @Test
    fun `saveRouteBookmark does not create local only cache when auth session is missing`() =
        runBlocking {
            val authSessionRepository =
                TestAuthSessionRepository(
                    initialState = AuthGateState(authSession = null, isProfileCompleted = false),
                )
            val fakeDao = FakeFavoriteRouteDao()
            val repository =
                DefaultRouteBookmarkRepository(
                    favoriteRouteDao = fakeDao,
                    authSessionRepository = authSessionRepository,
                    favoriteRoutesRemoteDataSource = null,
                    accessTokenProvider = { null },
                )

            val result = runCatching { repository.saveRouteBookmark(testSaveRequest()) }

            assertTrue(result.isFailure)
            val bookmarks = repository.observeRouteBookmarks().first()

            assertTrue(bookmarks.isEmpty())
        }

    @Test
    fun `getRouteBookmarkDetail fetches server snapshot and maps it to domain route`() =
        runBlocking {
            val fakeDao = FakeFavoriteRouteDao()
            val fakeDataSource =
                FakeFavoriteRoutesRemoteDataSource(
                    detail =
                        FavoriteRouteDetailDto(
                            favRouteId = 7L,
                            routeName = "상세 경로",
                            startLabel = "부산시청",
                            endLabel = "광안리해변",
                            startPoint = FavoriteRoutePointDto(lat = 35.1798, lng = 129.0750),
                            endPoint = FavoriteRoutePointDto(lat = 35.1532, lng = 129.1186),
                            transportMode = "WALK",
                            routeOption = "SHORTEST",
                            route =
                                RouteDto(
                                    routeId = "bookmark-detail-route-1",
                                    transportMode = "WALK",
                                    routeOption = "SHORTEST",
                                    title = "Stored Route",
                                    distanceMeter = 7600.0,
                                    estimatedTimeMinute = 21,
                                    geometry =
                                        "LINESTRING(129.0750 35.1798, 129.0960 35.1665, 129.1186 35.1532)",
                                    segments =
                                        listOf(
                                            RouteSegmentDto(
                                                sequence = 1,
                                                geometry =
                                                    "LINESTRING(129.0750 35.1798, 129.0960 35.1665, 129.1186 35.1532)",
                                                distanceMeter = 7600,
                                                guidanceMessage = "직진",
                                            ),
                                        ),
                                ),
                        ),
                )

            val repository =
                DefaultRouteBookmarkRepository(
                    favoriteRouteDao = fakeDao,
                    favoriteRoutesRemoteDataSource = fakeDataSource,
                    accessTokenProvider = { "test-token" },
                )

            val detail = repository.getRouteBookmarkDetail("7")

            assertEquals(1, fakeDataSource.getFavoriteRouteDetailCallCount)
            assertEquals("bookmark-detail-route-1", detail?.route?.serverRouteId)
            assertEquals(RouteOption.SHORTEST, detail?.route?.routeOption)
            assertEquals(3, detail?.route?.geometry?.points?.size)
        }

    @Test
    fun `saveRouteBookmark keeps cache empty when server create fails`() =
        runBlocking {
            val fakeDao = FakeFavoriteRouteDao()
            val fakeDataSource = FakeFavoriteRoutesRemoteDataSource(throwOnCreate = true)
            val repository =
                DefaultRouteBookmarkRepository(
                    favoriteRouteDao = fakeDao,
                    favoriteRoutesRemoteDataSource = fakeDataSource,
                    accessTokenProvider = { "test-token" },
                )

            val result = runCatching { repository.saveRouteBookmark(testSaveRequest()) }

            assertTrue(result.isFailure)
            assertEquals(1, fakeDataSource.createCallCount)
            assertTrue(fakeDao.routes().isEmpty())
        }

    @Test
    fun `deleteRouteBookmark calls server and removes cache when bookmarkId is numeric`() =
        runBlocking {
            val cachedEntity = testFavoriteRouteEntity(favoriteRouteId = 7L, routeName = "to-delete")
            val fakeDao = FakeFavoriteRouteDao(routes = listOf(cachedEntity))
            val fakeDataSource = FakeFavoriteRoutesRemoteDataSource()

            val repository =
                DefaultRouteBookmarkRepository(
                    favoriteRouteDao = fakeDao,
                    favoriteRoutesRemoteDataSource = fakeDataSource,
                    accessTokenProvider = { "test-token" },
                )

            repository.deleteRouteBookmark("7")

            assertEquals(listOf(7L), fakeDataSource.deletedFavRouteIds)
            assertEquals(0, fakeDao.routes().size)
        }

    @Test
    fun `deleteRouteBookmark removes cache even when server call fails`() =
        runBlocking {
            val cachedEntity = testFavoriteRouteEntity(favoriteRouteId = 7L, routeName = "to-delete")
            val fakeDao = FakeFavoriteRouteDao(routes = listOf(cachedEntity))
            val fakeDataSource = FakeFavoriteRoutesRemoteDataSource(throwOnDelete = true)

            val repository =
                DefaultRouteBookmarkRepository(
                    favoriteRouteDao = fakeDao,
                    favoriteRoutesRemoteDataSource = fakeDataSource,
                    accessTokenProvider = { "test-token" },
                )

            repository.deleteRouteBookmark("7")

            assertEquals(0, fakeDao.routes().size)
        }

    @Test
    fun `isBookmarked returns true when matching coordinates and option are in cache`() =
        runBlocking {
            val cachedEntity =
                FavoriteRouteEntity(
                    accountScopeKey = TEST_ACCOUNT_SCOPE_KEY,
                    favoriteRouteId = 1L,
                    routeName = "test",
                    originName = "출발",
                    originLatitude = 35.1686,
                    originLongitude = 129.0576,
                    destinationName = "도착",
                    destinationLatitude = 35.1152,
                    destinationLongitude = 129.0422,
                    routeOption = "SAFE",
                )
            val fakeDao = FakeFavoriteRouteDao(routes = listOf(cachedEntity))
            val repository =
                DefaultRouteBookmarkRepository(
                    favoriteRouteDao = fakeDao,
                    favoriteRoutesRemoteDataSource = null,
                    accessTokenProvider = { null },
                )

            val matched =
                repository.isBookmarked(
                    RouteBookmarkDraft(
                        startLabel = "출발",
                        endLabel = "도착",
                        startPoint = GeoCoordinate(latitude = 35.1686, longitude = 129.0576),
                        endPoint = GeoCoordinate(latitude = 35.1152, longitude = 129.0422),
                        routeOption = RouteOption.SAFE,
                    ),
                )

            assertTrue(matched)
        }

    @Test
    fun `isBookmarked returns false when no matching signature in cache`() =
        runBlocking {
            val fakeDao = FakeFavoriteRouteDao()
            val repository =
                DefaultRouteBookmarkRepository(
                    favoriteRouteDao = fakeDao,
                    favoriteRoutesRemoteDataSource = null,
                    accessTokenProvider = { null },
                )

            val matched =
                repository.isBookmarked(
                    RouteBookmarkDraft(
                        startLabel = "출발",
                        endLabel = "도착",
                        startPoint = GeoCoordinate(latitude = 0.0, longitude = 0.0),
                        endPoint = GeoCoordinate(latitude = 0.0, longitude = 0.0),
                        routeOption = RouteOption.SAFE,
                    ),
                )

            assertFalse(matched)
        }
}

private fun testSaveRequest(): RouteBookmarkSaveRequest =
    testSaveRequest(routeId = "walk_rt_safe_001")

private fun testSaveRequest(
    routeId: String?,
    routeSnapshot: RouteCandidate? = null,
): RouteBookmarkSaveRequest =
    RouteBookmarkSaveRequest(
        routeId = routeId,
        routeName = "집에서 병원",
        startLabel = "부산시민공원",
        endLabel = "부산역",
        startPoint = GeoCoordinate(latitude = 35.1686, longitude = 129.0576),
        endPoint = GeoCoordinate(latitude = 35.1152, longitude = 129.0422),
        routeOption = RouteOption.SAFE,
        routeSnapshot = routeSnapshot,
    )

private fun testRouteCandidateSnapshot(): RouteCandidate =
    RouteCandidate(
        routeId = "local-snapshot-route-1",
        serverRouteId = "local-snapshot-route-1",
        routeOption = RouteOption.SAFE,
        title = "Local Snapshot Route",
        summary =
            RouteSummary(
                distanceMeters = 3_250,
                estimatedTimeMinutes = 14,
                riskLevel = RouteRiskLevel.LOW,
            ),
        geometry =
            RoutePolyline(
                points =
                    listOf(
                        GeoCoordinate(latitude = 35.1686, longitude = 129.0576),
                        GeoCoordinate(latitude = 35.1420, longitude = 129.0499),
                        GeoCoordinate(latitude = 35.1152, longitude = 129.0422),
                    ),
            ),
        preview =
            RoutePreviewModel(
                polyline =
                    RoutePolyline(
                        points =
                            listOf(
                                GeoCoordinate(latitude = 35.1686, longitude = 129.0576),
                                GeoCoordinate(latitude = 35.1420, longitude = 129.0499),
                                GeoCoordinate(latitude = 35.1152, longitude = 129.0422),
                            ),
                    ),
                segmentCount = 1,
                renderableSegmentCount = 1,
            ),
        segments =
            listOf(
                RouteSegment(
                    sequence = 1,
                    polyline =
                        RoutePolyline(
                            points =
                                listOf(
                                    GeoCoordinate(latitude = 35.1686, longitude = 129.0576),
                                    GeoCoordinate(latitude = 35.1420, longitude = 129.0499),
                                    GeoCoordinate(latitude = 35.1152, longitude = 129.0422),
                                ),
                        ),
                    distanceMeters = 3_250,
                    riskLevel = RouteRiskLevel.LOW,
                    guidanceMessage = "Continue on the suggested route.",
                ),
            ),
    )

private fun testFavoriteRouteEntity(
    favoriteRouteId: Long,
    routeName: String,
    accountScopeKey: String = TEST_ACCOUNT_SCOPE_KEY,
): FavoriteRouteEntity =
    FavoriteRouteEntity(
        accountScopeKey = accountScopeKey,
        favoriteRouteId = favoriteRouteId,
        routeName = routeName,
        originName = "출발지",
        originLatitude = 35.0,
        originLongitude = 129.0,
        destinationName = "도착지",
        destinationLatitude = 35.1,
        destinationLongitude = 129.1,
        routeOption = "SAFE",
    )

private class FakeFavoriteRouteDao(
    routes: List<FavoriteRouteEntity> = emptyList(),
) : FavoriteRouteDao {
    private val state = MutableStateFlow(routes)

    fun routes(accountScopeKey: String = TEST_ACCOUNT_SCOPE_KEY): List<FavoriteRouteEntity> =
        state.value.filterByScope(accountScopeKey)

    override fun observeFavoriteRoutes(accountScopeKey: String): Flow<List<FavoriteRouteEntity>> =
        state.map { routes -> routes.filterByScope(accountScopeKey) }

    override fun observeFavoriteRoute(
        accountScopeKey: String,
        favoriteRouteId: Long,
    ): Flow<FavoriteRouteEntity?> =
        state.map { routes ->
            routes.firstOrNull { route ->
                route.accountScopeKey == accountScopeKey && route.favoriteRouteId == favoriteRouteId
            }
        }

    override suspend fun getFavoriteRoute(
        accountScopeKey: String,
        favoriteRouteId: Long,
    ): FavoriteRouteEntity? =
        state.value.firstOrNull { route ->
            route.accountScopeKey == accountScopeKey && route.favoriteRouteId == favoriteRouteId
        }

    override suspend fun getFavoriteRoutes(accountScopeKey: String): List<FavoriteRouteEntity> =
        state.value.filterByScope(accountScopeKey)

    override suspend fun upsertFavoriteRoute(favoriteRoute: FavoriteRouteEntity) {
        state.value = state.value.upsert(favoriteRoute)
    }

    override suspend fun upsertFavoriteRoutes(favoriteRoutes: List<FavoriteRouteEntity>) {
        favoriteRoutes.forEach { upsertFavoriteRoute(it) }
    }

    override suspend fun deleteFavoriteRoute(
        accountScopeKey: String,
        favoriteRouteId: Long,
    ) {
        state.value =
            state.value.filterNot { route ->
                route.accountScopeKey == accountScopeKey && route.favoriteRouteId == favoriteRouteId
            }
    }

    override suspend fun clearFavoriteRoutes(accountScopeKey: String) {
        state.value = state.value.filterNot { route -> route.accountScopeKey == accountScopeKey }
    }

    private fun List<FavoriteRouteEntity>.filterByScope(accountScopeKey: String): List<FavoriteRouteEntity> =
        filter { route -> route.accountScopeKey == accountScopeKey }
}

private fun List<FavoriteRouteEntity>.upsert(entity: FavoriteRouteEntity): List<FavoriteRouteEntity> =
    filterNot { existing ->
        existing.accountScopeKey == entity.accountScopeKey && existing.favoriteRouteId == entity.favoriteRouteId
    } + entity

private class FakeFavoriteRoutesRemoteDataSource(
    private val serverContent: List<FavoriteRouteListItemDto> = emptyList(),
    private val pagesByCursor: Map<Long?, FavoriteRoutePageDto> = emptyMap(),
    private val createdId: Long = 1L,
    private val detail: FavoriteRouteDetailDto? = null,
    private val throwOnGet: Boolean = false,
    private val throwOnCreate: Boolean = false,
    private val throwOnDelete: Boolean = false,
) : FavoriteRoutesRemoteDataSource(httpJsonClient = HttpJsonClient(baseUrl = "http://test.invalid")) {
    val deletedFavRouteIds = mutableListOf<Long>()
    val requestedCursors = mutableListOf<Long?>()
    var getFavoriteRoutesCallCount: Int = 0
        private set
    var getFavoriteRouteDetailCallCount: Int = 0
        private set
    var createCallCount: Int = 0
        private set
    val createdRouteIds = mutableListOf<String>()
    val createdStartLabels = mutableListOf<String>()
    val createdEndLabels = mutableListOf<String>()

    override suspend fun getFavoriteRoutes(
        accessToken: String,
        cursor: Long?,
        size: Int?,
    ): FavoriteRoutePageDto {
        getFavoriteRoutesCallCount++
        requestedCursors += cursor
        if (throwOnGet) throw RuntimeException("server get failure")
        pagesByCursor[cursor]?.let { return it }
        return FavoriteRoutePageDto(
            content = serverContent,
            size = size ?: serverContent.size,
            nextCursor = null,
            hasNext = false,
        )
    }

    override suspend fun getFavoriteRouteDetail(
        accessToken: String,
        favRouteId: Long,
    ): FavoriteRouteDetailDto {
        getFavoriteRouteDetailCallCount++
        return requireNotNull(detail) { "detail must be set for this test" }
    }

    override suspend fun createFavoriteRoute(
        accessToken: String,
        routeId: String,
        startLabel: String,
        endLabel: String,
    ): CreateFavoriteRouteResponseDto {
        createCallCount++
        createdRouteIds.add(routeId)
        createdStartLabels.add(startLabel)
        createdEndLabels.add(endLabel)
        if (throwOnCreate) throw RuntimeException("server create failure")
        return CreateFavoriteRouteResponseDto(favRouteId = createdId)
    }

    override suspend fun updateFavoriteRoute(
        accessToken: String,
        favRouteId: Long,
        startLabel: String?,
        endLabel: String?,
    ) {
        // no-op for test
    }

    override suspend fun deleteFavoriteRoute(
        accessToken: String,
        favRouteId: Long,
    ) {
        if (throwOnDelete) throw RuntimeException("server delete failure")
        deletedFavRouteIds.add(favRouteId)
    }
}

private const val TEST_ACCOUNT_SCOPE_KEY: String = "test-account"
