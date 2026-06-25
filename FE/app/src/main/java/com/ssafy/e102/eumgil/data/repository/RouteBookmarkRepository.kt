package com.ssafy.e102.eumgil.data.repository

import com.ssafy.e102.eumgil.core.model.GeoCoordinate
import com.ssafy.e102.eumgil.core.model.LowFloorBusReservation
import com.ssafy.e102.eumgil.core.model.RouteAlert
import com.ssafy.e102.eumgil.core.model.RouteBookmark
import com.ssafy.e102.eumgil.core.model.RouteBookmarkDetail
import com.ssafy.e102.eumgil.core.model.RouteBookmarkDraft
import com.ssafy.e102.eumgil.core.model.RouteBookmarkSaveRequest
import com.ssafy.e102.eumgil.core.model.RouteCandidate
import com.ssafy.e102.eumgil.core.model.RouteLeg
import com.ssafy.e102.eumgil.core.model.RouteOption
import com.ssafy.e102.eumgil.core.model.RoutePolyline
import com.ssafy.e102.eumgil.core.model.RouteSegment
import com.ssafy.e102.eumgil.core.model.RouteStep
import com.ssafy.e102.eumgil.core.model.RouteTransitLaneOption
import com.ssafy.e102.eumgil.core.model.RouteTransitStop
import com.ssafy.e102.eumgil.data.local.dao.FavoriteRouteDao
import com.ssafy.e102.eumgil.data.local.entity.FavoriteRouteEntity
import com.ssafy.e102.eumgil.data.remote.datasource.FavoriteRoutesRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.dto.FavoriteRouteDetailDto
import com.ssafy.e102.eumgil.data.remote.dto.FavoriteRouteListItemDto
import com.ssafy.e102.eumgil.data.remote.dto.FavoriteRoutePointDto
import com.ssafy.e102.eumgil.data.route.DefaultRouteGeometryParser
import com.ssafy.e102.eumgil.data.route.RouteGeometryParser
import com.ssafy.e102.eumgil.data.route.toRouteDto
import com.ssafy.e102.eumgil.data.route.toRouteCandidate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

interface RouteBookmarkRepository {
    fun observeRouteBookmarks(): Flow<List<RouteBookmark>>

    suspend fun isBookmarked(draft: RouteBookmarkDraft): Boolean

    suspend fun getRouteBookmarkDetail(bookmarkId: String): RouteBookmarkDetail?

    suspend fun saveRouteBookmark(request: RouteBookmarkSaveRequest): RouteBookmark

    suspend fun deleteRouteBookmark(bookmarkId: String)
}

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultRouteBookmarkRepository(
    private val favoriteRouteDao: FavoriteRouteDao,
    private val authSessionRepository: AuthSessionRepository? = null,
    private val favoriteRoutesRemoteDataSource: FavoriteRoutesRemoteDataSource? = null,
    private val accessTokenProvider: suspend () -> String? = { null },
    private val routeGeometryParser: RouteGeometryParser = DefaultRouteGeometryParser(),
    private val clock: () -> Long = { System.currentTimeMillis() },
) : RouteBookmarkRepository {
    override fun observeRouteBookmarks(): Flow<List<RouteBookmark>> =
        observeAccountScope().flatMapLatest { accountScopeKey ->
            favoriteRouteDao
                .observeFavoriteRoutes(accountScopeKey)
                .onStart { refreshFromServerIfPossible(accountScopeKey) }
                .map { entities -> entities.map(FavoriteRouteEntity::toRouteBookmark) }
        }

    override suspend fun isBookmarked(draft: RouteBookmarkDraft): Boolean {
        val accountScopeKey = getCurrentAccountScopeKey()
        val cached = favoriteRouteDao.getFavoriteRoutes(accountScopeKey)
        return cached.any { entity -> entity.matchesSignature(draft) }
    }

    override suspend fun getRouteBookmarkDetail(bookmarkId: String): RouteBookmarkDetail? {
        val favoriteRouteId = bookmarkId.toLongOrNull() ?: return null
        val accountScopeKey = getCurrentAccountScopeKey()
        val localDetail =
            favoriteRouteDao
                .getFavoriteRoute(accountScopeKey, favoriteRouteId)
                ?.toRouteBookmarkDetail(routeGeometryParser)
        if (favoriteRouteId <= 0L) return localDetail

        val datasource = favoriteRoutesRemoteDataSource ?: return localDetail
        val token = resolveAccessToken() ?: return localDetail
        val remoteDetail =
            runCatching {
                datasource.getFavoriteRouteDetail(
                    accessToken = token,
                    favRouteId = favoriteRouteId,
                )
            }.getOrNull()?.toRouteBookmarkDetail(routeGeometryParser)

        return when {
            remoteDetail == null -> localDetail
            remoteDetail.route != null -> remoteDetail
            localDetail?.route != null -> remoteDetail.copy(route = localDetail.route)
            else -> remoteDetail
        }
    }

    override suspend fun saveRouteBookmark(request: RouteBookmarkSaveRequest): RouteBookmark {
        val accountScopeKey = getCurrentAccountScopeKey()
        val now = clock()
        val serverFavRouteId = trySaveOnServer(request)
        val existingEntity = favoriteRouteDao.getFavoriteRoute(accountScopeKey, serverFavRouteId)

        val cachedEntity =
            FavoriteRouteEntity(
                accountScopeKey = accountScopeKey,
                favoriteRouteId = serverFavRouteId,
                routeName = request.routeName.trim().ifBlank { request.fallbackRouteName() },
                originName = request.startLabel,
                originLatitude = request.startPoint.latitude,
                originLongitude = request.startPoint.longitude,
                destinationName = request.endLabel,
                destinationLatitude = request.endPoint.latitude,
                destinationLongitude = request.endPoint.longitude,
                transportMode = request.routeSnapshot?.transportMode?.name ?: WALK_TRANSPORT_MODE,
                routeOption = request.routeOption.name,
                summaryDistanceMeters = request.distanceMeters,
                summaryDurationSeconds = request.durationMinutes?.let { it * 60 },
                routeSnapshotJson = request.routeSnapshot?.toSnapshotJsonString() ?: existingEntity?.routeSnapshotJson,
                createdAt = existingEntity?.createdAt ?: now,
                updatedAt = now,
            )
        favoriteRouteDao.upsertFavoriteRoute(cachedEntity)

        return RouteBookmark(
            bookmarkId = cachedEntity.favoriteRouteId.toString(),
            routeName = cachedEntity.routeName,
            startLabel = cachedEntity.originName,
            endLabel = cachedEntity.destinationName,
            startPoint = GeoCoordinate(latitude = cachedEntity.originLatitude, longitude = cachedEntity.originLongitude),
            endPoint =
                GeoCoordinate(
                    latitude = cachedEntity.destinationLatitude,
                    longitude = cachedEntity.destinationLongitude,
                ),
            routeOption = request.routeOption,
            transportMode = cachedEntity.transportMode,
            routeOptionLabel = cachedEntity.routeOption,
            distanceMeters = request.distanceMeters,
            durationMinutes = request.durationMinutes,
            createdAt = cachedEntity.createdAt,
            updatedAt = cachedEntity.updatedAt,
        )
    }

    override suspend fun deleteRouteBookmark(bookmarkId: String) {
        val accountScopeKey = getCurrentAccountScopeKey()
        val favoriteRouteId = bookmarkId.toLongOrNull() ?: return
        runCatching { tryDeleteOnServer(favoriteRouteId) }
        favoriteRouteDao.deleteFavoriteRoute(accountScopeKey, favoriteRouteId)
    }

    private suspend fun trySaveOnServer(request: RouteBookmarkSaveRequest): Long {
        val datasource =
            favoriteRoutesRemoteDataSource
                ?: throw RouteBookmarkSaveException("경로 북마크 서버 저장을 지원하지 않는 환경입니다.")
        val token =
            resolveAccessToken()
                ?: throw RouteBookmarkSaveException("로그인 세션이 없어 경로 북마크를 서버에 저장할 수 없습니다.")
        val routeId =
            request.routeId?.trim()?.takeIf(String::isNotEmpty)
                ?: throw RouteBookmarkSaveException("안내 종료 경로 ID가 없어 경로 북마크를 저장할 수 없습니다.")

        val response =
            datasource.createFavoriteRoute(
                accessToken = token,
                routeId = routeId,
                startLabel = request.startLabel,
                endLabel = request.endLabel,
            )
        return response.favRouteId
    }

    private suspend fun tryDeleteOnServer(favoriteRouteId: Long) {
        val datasource = favoriteRoutesRemoteDataSource ?: return
        val token = resolveAccessToken() ?: return
        if (favoriteRouteId <= 0L) return

        datasource.deleteFavoriteRoute(accessToken = token, favRouteId = favoriteRouteId)
    }

    private suspend fun refreshFromServerIfPossible(accountScopeKey: String) {
        runCatching {
            val datasource = favoriteRoutesRemoteDataSource ?: return@runCatching
            val token = resolveAccessToken() ?: return@runCatching

            val serverFavoriteRoutes =
                fetchAllFavoriteRoutesFromServer(
                    datasource = datasource,
                    token = token,
                )

            val now = clock()
            val cachedRoutes = favoriteRouteDao.getFavoriteRoutes(accountScopeKey)
            val cachedById = cachedRoutes.associateBy(FavoriteRouteEntity::favoriteRouteId)

            favoriteRouteDao.clearFavoriteRoutes(accountScopeKey)
            favoriteRouteDao.upsertFavoriteRoutes(
                serverFavoriteRoutes.map { item ->
                    val cached = cachedById[item.favRouteId]
                    item.toFavoriteRouteEntity(
                        accountScopeKey = accountScopeKey,
                        createdAt = cached?.createdAt ?: now,
                        updatedAt = now,
                        cachedDistanceMeters = cached?.summaryDistanceMeters,
                        cachedDurationSeconds = cached?.summaryDurationSeconds,
                        cachedRouteSnapshotJson = cached?.routeSnapshotJson,
                    )
                },
            )
        }
    }

    private suspend fun fetchAllFavoriteRoutesFromServer(
        datasource: FavoriteRoutesRemoteDataSource,
        token: String,
    ): List<FavoriteRouteListItemDto> {
        val favoriteRoutes = mutableListOf<FavoriteRouteListItemDto>()
        var cursor: Long? = null

        do {
            val page =
                datasource.getFavoriteRoutes(
                    accessToken = token,
                    cursor = cursor,
                    size = DEFAULT_PAGE_SIZE,
                )
            favoriteRoutes += page.content
            cursor = page.nextCursor
        } while (page.hasNext && cursor != null)

        return favoriteRoutes
    }

    private fun observeAccountScope(): Flow<String> =
        authSessionRepository
            ?.observeAccountScopeKey()
            ?.map { accountScopeKey -> accountScopeKey ?: LOCAL_ONLY_ROUTE_BOOKMARK_SCOPE_KEY }
            ?.distinctUntilChanged()
            ?: flowOf(DEFAULT_TEST_ACCOUNT_SCOPE_KEY)

    private suspend fun getCurrentAccountScopeKey(): String =
        authSessionRepository?.getAccountScopeKey()
            ?: if (authSessionRepository == null) DEFAULT_TEST_ACCOUNT_SCOPE_KEY else LOCAL_ONLY_ROUTE_BOOKMARK_SCOPE_KEY

    private suspend fun resolveAccessToken(): String? =
        authSessionRepository?.getCurrentAuthSession()?.accessToken?.takeIf(String::isNotBlank)
            ?: accessTokenProvider()?.takeIf(String::isNotBlank)

    private companion object {
        private const val DEFAULT_PAGE_SIZE = 50
        private const val WALK_TRANSPORT_MODE = "WALK"
        private const val DEFAULT_TEST_ACCOUNT_SCOPE_KEY = "test-account"
        private const val LOCAL_ONLY_ROUTE_BOOKMARK_SCOPE_KEY = "route-bookmark::local-only"
    }
}

class FakeRouteBookmarkRepository(
    private val clock: () -> Long = { System.currentTimeMillis() },
) : RouteBookmarkRepository {
    private val routeBookmarks = MutableStateFlow(emptyList<RouteBookmark>())

    override fun observeRouteBookmarks(): Flow<List<RouteBookmark>> = routeBookmarks

    override suspend fun isBookmarked(draft: RouteBookmarkDraft): Boolean =
        routeBookmarks.value.any { bookmark ->
            bookmark.routeSignature() == draft.routeSignature()
        }

    override suspend fun getRouteBookmarkDetail(bookmarkId: String): RouteBookmarkDetail? = null

    override suspend fun saveRouteBookmark(request: RouteBookmarkSaveRequest): RouteBookmark {
        val now = clock()
        val bookmarkId = request.bookmarkId()
        val existingBookmark =
            routeBookmarks.value.firstOrNull { bookmark ->
                bookmark.bookmarkId == bookmarkId
            }
        val savedBookmark =
            RouteBookmark(
                bookmarkId = bookmarkId,
                routeName = request.routeName.trim().ifBlank { "${request.startLabel}-${request.endLabel}" },
                startLabel = request.startLabel,
                endLabel = request.endLabel,
                startPoint = request.startPoint,
                endPoint = request.endPoint,
                routeOption = request.routeOption,
                distanceMeters = request.distanceMeters,
                durationMinutes = request.durationMinutes,
                createdAt = existingBookmark?.createdAt ?: now,
                updatedAt = now,
            )

        routeBookmarks.update { currentBookmarks ->
            (currentBookmarks.filterNot { bookmark -> bookmark.bookmarkId == bookmarkId } + savedBookmark)
                .sortedByDescending(RouteBookmark::updatedAt)
        }

        return savedBookmark
    }

    override suspend fun deleteRouteBookmark(bookmarkId: String) {
        routeBookmarks.update { currentBookmarks ->
            currentBookmarks.filterNot { bookmark -> bookmark.bookmarkId == bookmarkId }
        }
    }
}

private fun RouteBookmarkDraft.routeSignature(): String =
    "${startPoint.latitude},${startPoint.longitude}|${endPoint.latitude},${endPoint.longitude}|${routeOption.name}"

private fun RouteBookmark.routeSignature(): String =
    "${startPoint.latitude},${startPoint.longitude}|${endPoint.latitude},${endPoint.longitude}|${routeOption.name}"

private fun RouteBookmarkSaveRequest.bookmarkId(): String =
    routeId
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let { "route-bookmark:$it" }
        ?: "route-bookmark:${startPoint.latitude},${startPoint.longitude}|${endPoint.latitude},${endPoint.longitude}|${routeOption.name}"

private fun RouteBookmarkSaveRequest.fallbackRouteName(): String = "$startLabel-$endLabel"

private fun FavoriteRouteEntity.matchesSignature(draft: RouteBookmarkDraft): Boolean =
    originLatitude == draft.startPoint.latitude &&
        originLongitude == draft.startPoint.longitude &&
        destinationLatitude == draft.endPoint.latitude &&
        destinationLongitude == draft.endPoint.longitude &&
        routeOption == draft.routeOption.name

private fun FavoriteRouteEntity.toRouteBookmark(): RouteBookmark =
    RouteBookmark(
        bookmarkId = favoriteRouteId.toString(),
        routeName = routeName,
        startLabel = originName,
        endLabel = destinationName,
        startPoint = GeoCoordinate(latitude = originLatitude, longitude = originLongitude),
        endPoint = GeoCoordinate(latitude = destinationLatitude, longitude = destinationLongitude),
        routeOption = routeOption.toRouteOptionOrDefault(),
        transportMode = transportMode,
        routeOptionLabel = routeOption,
        distanceMeters = summaryDistanceMeters,
        durationMinutes = summaryDurationSeconds?.let { it / 60 },
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

private fun FavoriteRouteListItemDto.toFavoriteRouteEntity(
    accountScopeKey: String,
    createdAt: Long,
    updatedAt: Long,
    cachedDistanceMeters: Int? = null,
    cachedDurationSeconds: Int? = null,
    cachedRouteSnapshotJson: String? = null,
): FavoriteRouteEntity =
    FavoriteRouteEntity(
        accountScopeKey = accountScopeKey,
        favoriteRouteId = favRouteId,
        routeName = routeName,
        originName = startLabel,
        originLatitude = startPoint.lat,
        originLongitude = startPoint.lng,
        destinationName = endLabel,
        destinationLatitude = endPoint.lat,
        destinationLongitude = endPoint.lng,
        transportMode = transportMode,
        routeOption = routeOption,
        summaryDistanceMeters = cachedDistanceMeters,
        summaryDurationSeconds = cachedDurationSeconds,
        routeSnapshotJson = cachedRouteSnapshotJson,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

private fun FavoriteRouteDetailDto.toRouteBookmarkDetail(geometryParser: RouteGeometryParser): RouteBookmarkDetail =
    RouteBookmarkDetail(
        bookmarkId = favRouteId.toString(),
        routeName = routeName,
        startLabel = startLabel,
        endLabel = endLabel,
        startPoint = startPoint.toGeoCoordinate(),
        endPoint = endPoint.toGeoCoordinate(),
        transportMode = transportMode,
        routeOptionLabel = routeOption,
        route = route?.toRouteCandidate(geometryParser = geometryParser, defaultOption = routeOption.toRouteOptionOrDefault()),
    )

private fun FavoriteRouteEntity.toRouteBookmarkDetail(geometryParser: RouteGeometryParser): RouteBookmarkDetail =
    RouteBookmarkDetail(
        bookmarkId = favoriteRouteId.toString(),
        routeName = routeName,
        startLabel = originName,
        endLabel = destinationName,
        startPoint = GeoCoordinate(latitude = originLatitude, longitude = originLongitude),
        endPoint = GeoCoordinate(latitude = destinationLatitude, longitude = destinationLongitude),
        transportMode = transportMode,
        routeOptionLabel = routeOption,
        route =
            routeSnapshotJson.toRouteCandidateOrNull(
                geometryParser = geometryParser,
                defaultOption = routeOption.toRouteOptionOrDefault(),
            ),
    )

private fun FavoriteRoutePointDto.toGeoCoordinate(): GeoCoordinate =
    GeoCoordinate(
        latitude = lat,
        longitude = lng,
    )

private fun String?.toRouteOptionOrDefault(): RouteOption =
    this?.let { value ->
        RouteOption.values().firstOrNull { it.name == value }
    } ?: RouteOption.SAFE

private fun String?.toRouteCandidateOrNull(
    geometryParser: RouteGeometryParser,
    defaultOption: RouteOption,
): RouteCandidate? =
    this
        ?.takeIf(String::isNotBlank)
        ?.let { snapshotJson ->
            runCatching {
                JSONObject(snapshotJson)
                    .toRouteDto()
                    .toRouteCandidate(
                        geometryParser = geometryParser,
                        defaultOption = defaultOption,
                    )
            }.getOrNull()
        }

private fun RouteCandidate.toSnapshotJsonString(): String =
    JSONObject()
        .apply {
            if (serverRouteId.orEmpty().isNotBlank()) {
                put("routeId", serverRouteId)
            } else if (routeId.isNotBlank()) {
                put("routeId", routeId)
            }
            put("transportMode", transportMode.name)
            put("routeOption", routeOption.name)
            put("title", title)
            put("distanceMeter", summary.distanceMeters)
            summary.durationSeconds?.let { durationSeconds ->
                put("durationSecond", durationSeconds)
            }
            put("estimatedTimeMinute", summary.estimatedTimeMinutes)
            transferCount?.let { transferCount ->
                put("transferCount", transferCount)
            }
            if (badges.isNotEmpty()) {
                put(
                    "badges",
                    JSONArray().apply {
                        badges.forEach { badge -> put(badge.name) }
                    },
                )
            }
            geometry.toLineStringOrNull()?.let { geometryLineString ->
                put("geometry", geometryLineString)
            }
            put("riskLevel", summary.riskLevel.name)
            if (segments.isNotEmpty()) {
                put(
                    "segments",
                    JSONArray().apply {
                        segments.forEach { segment -> put(segment.toSnapshotJsonObject()) }
                    },
                )
            }
            if (legs.isNotEmpty()) {
                put(
                    "legs",
                    JSONArray().apply {
                        legs.forEach { leg -> put(leg.toSnapshotJsonObject()) }
                    },
                )
            }
        }.toString()

private fun RouteLeg.toSnapshotJsonObject(): JSONObject =
    JSONObject().apply {
        put("sequence", sequence)
        put("type", type.name)
        put("role", role.name)
        put("instruction", instruction)
        distanceMeters?.let { distanceMeters ->
            put("distanceMeter", distanceMeters)
        }
        durationSeconds?.let { durationSeconds ->
            put("durationSecond", durationSeconds)
        }
        estimatedTimeMinutes?.let { estimatedTimeMinutes ->
            put("estimatedTimeMinute", estimatedTimeMinutes)
        }
        polyline.toLineStringOrNull()?.let { geometryLineString ->
            put("geometry", geometryLineString)
        }
        if (steps.isNotEmpty()) {
            put(
                "steps",
                JSONArray().apply {
                    steps.forEach { step -> put(step.toSnapshotJsonObject()) }
                },
            )
        }
        if (laneOptions.isNotEmpty()) {
            put(
                "laneOptions",
                JSONArray().apply {
                    laneOptions.forEach { laneOption -> put(laneOption.toSnapshotJsonObject()) }
                },
            )
        }
        routeNo?.takeIf(String::isNotBlank)?.let { routeNo ->
            put("routeNo", routeNo)
        }
        boardingStop?.let { boardingStop ->
            put("boardingStop", boardingStop.toSnapshotJsonObject())
        }
        alightingStop?.let { alightingStop ->
            put("alightingStop", alightingStop.toSnapshotJsonObject())
        }
        isLowFloor?.let { isLowFloor ->
            put("isLowFloor", isLowFloor)
        }
        if (badges.isNotEmpty()) {
            put(
                "badges",
                JSONArray().apply {
                    badges.forEach { badge -> put(badge.name) }
                },
            )
        }
    }

private fun RouteStep.toSnapshotJsonObject(): JSONObject =
    JSONObject().apply {
        put("sequence", sequence)
        put("instruction", instruction)
        polyline.toLineStringOrNull()?.let { geometryLineString ->
            put("geometry", geometryLineString)
        }
        put("distanceMeter", distanceMeters)
        durationSeconds?.let { durationSeconds ->
            put("durationSecond", durationSeconds)
        }
        if (badges.isNotEmpty()) {
            put(
                "badges",
                JSONArray().apply {
                    badges.forEach { badge -> put(badge.name) }
                },
            )
        }
        if (alerts.isNotEmpty()) {
            put(
                "alerts",
                JSONArray().apply {
                    alerts.forEach { alert -> put(alert.toSnapshotJsonObject()) }
                },
            )
        }
        slopePercent?.let { slopePercent ->
            put("slopePercent", slopePercent)
        }
        widthState?.takeIf(String::isNotBlank)?.let { widthState ->
            put("widthState", widthState)
        }
    }

private fun RouteAlert.toSnapshotJsonObject(): JSONObject =
    JSONObject().apply {
        put("type", type.name)
        put("distanceMeter", distanceMeters)
    }

private fun RouteTransitLaneOption.toSnapshotJsonObject(): JSONObject =
    JSONObject().apply {
        routeNo?.takeIf(String::isNotBlank)?.let { routeNo ->
            put("routeNo", routeNo)
        }
        remainingMinute?.let { remainingMinute ->
            put("remainingMinute", remainingMinute)
        }
        durationSeconds?.let { durationSeconds ->
            put("durationSecond", durationSeconds)
        }
        estimatedTimeMinutes?.let { estimatedTimeMinutes ->
            put("estimatedTimeMinute", estimatedTimeMinutes)
        }
        isLowFloor?.let { isLowFloor ->
            put("isLowFloor", isLowFloor)
        }
        lowFloorReservation?.let { reservation ->
            put("lowFloorReservation", reservation.toSnapshotJsonObject())
        }
    }

private fun LowFloorBusReservation.toSnapshotJsonObject(): JSONObject =
    JSONObject().apply {
        put("stopName", stopName)
        put("arsNo", arsNo)
        put("routeNo", routeNo)
        put("vehicleNo", vehicleNo)
        put("remainingMinute", remainingMinute)
        remainingStopCount?.let { remainingStopCount ->
            put("remainingStopCount", remainingStopCount)
        }
    }

private fun RouteTransitStop.toSnapshotJsonObject(): JSONObject =
    JSONObject().apply {
        put("name", name)
        put("lat", coordinate.latitude)
        put("lng", coordinate.longitude)
    }

private fun RouteSegment.toSnapshotJsonObject(): JSONObject =
    JSONObject().apply {
        put("sequence", sequence)
        polyline.toLineStringOrNull()?.let { geometryLineString ->
            put("geometry", geometryLineString)
        }
        put("distanceMeter", distanceMeters)
        put("hasStairs", safetyFlags.hasStairs)
        put("hasCurbGap", safetyFlags.hasCurbGap)
        put("hasCrosswalk", safetyFlags.hasCrosswalk)
        put("hasSignal", safetyFlags.hasSignal)
        put("hasAudioSignal", safetyFlags.hasAudioSignal)
        put("hasBrailleBlock", safetyFlags.hasBrailleBlock)
        put("riskLevel", riskLevel.name)
        put("guidanceMessage", guidanceMessage)
    }

private fun RoutePolyline.toLineStringOrNull(): String? =
    points
        .takeIf { coordinates -> coordinates.size >= 2 }
        ?.joinToString(
            prefix = "LINESTRING(",
            postfix = ")",
            separator = ", ",
        ) { coordinate ->
            "${coordinate.longitude} ${coordinate.latitude}"
        }

class RouteBookmarkSaveException(
    message: String,
) : IllegalStateException(message)
