package com.ssafy.e102.eumgil.data.repository

import com.ssafy.e102.eumgil.core.model.FacilityDetailSeed
import com.ssafy.e102.eumgil.core.model.PlaceDestination
import com.ssafy.e102.eumgil.core.model.RouteWaypoint
import com.ssafy.e102.eumgil.data.local.dao.BookmarkDao
import com.ssafy.e102.eumgil.data.local.entity.BookmarkEntity
import com.ssafy.e102.eumgil.data.remote.datasource.BookmarksRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.dto.BookmarkListItemDto
import com.ssafy.e102.eumgil.data.remote.dto.BookmarkPointDto
import com.ssafy.e102.eumgil.data.remote.dto.CreateBookmarkRequestDto
import com.ssafy.e102.eumgil.data.remote.dto.CreateBookmarkResponseDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.util.Locale

interface BookmarkRepository {
    fun observeBookmarks(): Flow<List<BookmarkData>>

    suspend fun isBookmarked(placeId: String): Boolean

    suspend fun saveBookmark(bookmark: BookmarkData): BookmarkData

    suspend fun deleteBookmark(placeId: String)
}

data class BookmarkData(
    val placeId: String,
    val placeName: String,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
    val category: String?,
    val bookmarkId: Long? = null,
    val bookmarkTargetId: String? = null,
    val targetType: String? = null,
    val serverPlaceId: Long? = null,
    val provider: String? = null,
    val providerPlaceId: String? = null,
    val providerCategory: String? = null,
)

class DefaultBookmarkRepository(
    private val bookmarkDao: BookmarkDao,
    private val authSessionRepository: AuthSessionRepository? = null,
    private val bookmarksRemoteDataSource: BookmarksRemoteDataSource? = null,
    private val accessTokenProvider: suspend () -> String? = { null },
    private val initialBookmarks: List<BookmarkData> = emptyList(),
    private val clock: () -> Long = { System.currentTimeMillis() },
) : BookmarkRepository {
    private val seededAccountScopes = mutableSetOf<String>()

    override fun observeBookmarks(): Flow<List<BookmarkData>> =
        observeAccountScope().flatMapLatest { accountScopeKey ->
            if (accountScopeKey == null) {
                flowOf(emptyList())
            } else {
                bookmarkDao
                    .observeBookmarks(accountScopeKey)
                    .onStart {
                        seedInitialBookmarksIfNeeded(accountScopeKey)
                        refreshFromServerIfPossible(accountScopeKey)
                    }.map { bookmarks ->
                        bookmarks.map(BookmarkEntity::toBookmarkData)
                    }
            }
        }

    override suspend fun isBookmarked(placeId: String): Boolean {
        val accountScopeKey = getCurrentAccountScopeKey() ?: return false
        return bookmarkDao.getBookmark(accountScopeKey, placeId) != null
    }

    override suspend fun saveBookmark(bookmark: BookmarkData): BookmarkData {
        val accountScopeKey =
            getCurrentAccountScopeKey()
                ?: throw BookmarkSaveException("로그인 후에만 북마크를 저장할 수 있습니다.")
        val serverResponse = trySaveOnServer(bookmark)
        val resolvedBookmark = bookmark.withServerResponse(serverResponse)
        cacheBookmark(accountScopeKey, resolvedBookmark)
        return resolvedBookmark
    }

    override suspend fun deleteBookmark(placeId: String) {
        val accountScopeKey = getCurrentAccountScopeKey() ?: return
        val cachedBookmark =
            bookmarkDao.getBookmark(accountScopeKey, placeId)
                ?: bookmarkDao.getBookmarkByTargetId(accountScopeKey, placeId)
        tryDeleteOnServer(placeId = placeId, cachedBookmark = cachedBookmark)
        if (cachedBookmark?.bookmarkTargetId == placeId) {
            bookmarkDao.deleteBookmarkByTargetId(accountScopeKey, placeId)
        } else {
            bookmarkDao.deleteBookmark(accountScopeKey, placeId)
        }
    }

    private suspend fun trySaveOnServer(bookmark: BookmarkData): CreateBookmarkResponseDto {
        val datasource =
            bookmarksRemoteDataSource
                ?: throw BookmarkSaveException("북마크 서버 저장을 지원하지 않는 환경입니다.")
        val token =
            resolveAccessToken()
                ?: throw BookmarkSaveException("로그인 세션이 없어 북마크를 서버에 저장할 수 없습니다.")
        val request =
            bookmark.toCreateBookmarkRequestDto()
                ?: throw BookmarkSaveException("서버에 저장할 수 없는 북마크 데이터입니다.")

        return datasource.createBookmark(accessToken = token, request = request)
    }

    private suspend fun tryDeleteOnServer(
        placeId: String,
        cachedBookmark: BookmarkEntity?,
    ) {
        val datasource = bookmarksRemoteDataSource ?: return
        val token = resolveAccessToken() ?: return
        val bookmarkTargetId = cachedBookmark?.bookmarkTargetId?.takeIf { it.isNotBlank() }
        if (bookmarkTargetId != null) {
            datasource.deleteBookmarkByTargetId(accessToken = token, bookmarkTargetId = bookmarkTargetId)
            return
        }

        val numericPlaceId = cachedBookmark?.serverPlaceId ?: placeId.toLongOrNull() ?: return

        datasource.deleteBookmark(accessToken = token, placeId = numericPlaceId)
    }

    private suspend fun cacheBookmark(
        accountScopeKey: String,
        bookmark: BookmarkData,
    ) {
        val now = clock()
        val existingBookmark = bookmarkDao.getBookmark(accountScopeKey, bookmark.placeId)

        bookmarkDao.upsertBookmark(
            BookmarkEntity(
                bookmarkId = existingBookmark?.bookmarkId ?: 0L,
                accountScopeKey = accountScopeKey,
                placeId = bookmark.placeId,
                serverBookmarkId = bookmark.bookmarkId,
                bookmarkTargetId = bookmark.bookmarkTargetId,
                targetType = bookmark.targetType,
                serverPlaceId = bookmark.serverPlaceId,
                provider = bookmark.provider,
                providerPlaceId = bookmark.providerPlaceId,
                providerCategory = bookmark.providerCategory,
                placeName = bookmark.placeName,
                address = bookmark.address,
                latitude = bookmark.latitude,
                longitude = bookmark.longitude,
                category = bookmark.category,
                createdAt = existingBookmark?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }

    private suspend fun refreshFromServerIfPossible(accountScopeKey: String) {
        runCatching {
            val datasource = bookmarksRemoteDataSource ?: return@runCatching
            val token = resolveAccessToken() ?: return@runCatching

            val serverBookmarks = fetchAllBookmarksFromServer(datasource = datasource, token = token)

            val now = clock()
            bookmarkDao.clearBookmarks(accountScopeKey)
            bookmarkDao.upsertBookmarks(
                serverBookmarks.map { item ->
                    item.toBookmarkEntity(
                        accountScopeKey = accountScopeKey,
                        createdAt = now,
                        updatedAt = now,
                    )
                },
            )
        }
    }

    private suspend fun fetchAllBookmarksFromServer(
        datasource: BookmarksRemoteDataSource,
        token: String,
    ): List<BookmarkListItemDto> {
        val bookmarks = mutableListOf<BookmarkListItemDto>()
        var cursor: Long? = null

        do {
            val page =
                datasource.getBookmarks(
                    accessToken = token,
                    cursor = cursor,
                    size = DEFAULT_PAGE_SIZE,
                )
            bookmarks += page.content
            cursor = page.nextCursor
        } while (page.hasNext && cursor != null)

        return bookmarks
    }

    private suspend fun seedInitialBookmarksIfNeeded(accountScopeKey: String) {
        if (accountScopeKey in seededAccountScopes || initialBookmarks.isEmpty()) return

        seededAccountScopes += accountScopeKey
        if (bookmarkDao.getBookmarkCount(accountScopeKey) > 0) return

        val now = clock()
        bookmarkDao.upsertBookmarks(
            initialBookmarks.map { bookmark ->
                bookmark.toBookmarkEntity(
                    accountScopeKey = accountScopeKey,
                    createdAt = now,
                    updatedAt = now,
                )
            },
        )
    }

    private fun observeAccountScope(): Flow<String?> =
        authSessionRepository?.observeAccountScopeKey() ?: flowOf(DEFAULT_TEST_ACCOUNT_SCOPE_KEY)

    private suspend fun getCurrentAccountScopeKey(): String? =
        authSessionRepository?.getAccountScopeKey() ?: DEFAULT_TEST_ACCOUNT_SCOPE_KEY

    private suspend fun resolveAccessToken(): String? =
        authSessionRepository?.getCurrentAuthSession()?.accessToken?.takeIf(String::isNotBlank)
            ?: accessTokenProvider()?.takeIf(String::isNotBlank)

    private companion object {
        private const val DEFAULT_PAGE_SIZE = 50
        private const val DEFAULT_TEST_ACCOUNT_SCOPE_KEY = "test-account"
    }
}

private fun BookmarkEntity.toBookmarkData(): BookmarkData =
    BookmarkData(
        placeId = placeId,
        placeName = placeName,
        address = address,
        latitude = latitude,
        longitude = longitude,
        category = category,
        bookmarkId = serverBookmarkId,
        bookmarkTargetId = bookmarkTargetId,
        targetType = targetType,
        serverPlaceId = serverPlaceId,
        provider = provider,
        providerPlaceId = providerPlaceId,
        providerCategory = providerCategory,
    )

fun FacilityDetailSeed.toBookmarkData(): BookmarkData =
    BookmarkData(
        placeId = facilityId,
        placeName = name,
        address = address.takeIf { it.isNotBlank() },
        latitude = coordinate.latitude,
        longitude = coordinate.longitude,
        category = category.name,
        serverPlaceId = facilityId.toLongOrNull(),
    )

private fun BookmarkData.toBookmarkEntity(
    accountScopeKey: String,
    createdAt: Long,
    updatedAt: Long,
): BookmarkEntity =
    BookmarkEntity(
        accountScopeKey = accountScopeKey,
        placeId = placeId,
        serverBookmarkId = bookmarkId,
        bookmarkTargetId = bookmarkTargetId,
        targetType = targetType,
        serverPlaceId = serverPlaceId,
        provider = provider,
        providerPlaceId = providerPlaceId,
        providerCategory = providerCategory,
        placeName = placeName,
        address = address,
        latitude = latitude,
        longitude = longitude,
        category = category,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

private fun BookmarkListItemDto.toBookmarkEntity(
    accountScopeKey: String,
    createdAt: Long,
    updatedAt: Long,
): BookmarkEntity =
    BookmarkEntity(
        accountScopeKey = accountScopeKey,
        placeId = localCachePlaceId(),
        serverBookmarkId = bookmarkId,
        bookmarkTargetId = bookmarkTargetId,
        targetType = targetType,
        serverPlaceId = placeId,
        provider = provider,
        providerPlaceId = providerPlaceId,
        providerCategory = providerCategory,
        placeName = name,
        address = address,
        latitude = point.lat,
        longitude = point.lng,
        category = category,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

private fun BookmarkData.toCreateBookmarkRequestDto(): CreateBookmarkRequestDto? {
    val numericPlaceId = serverPlaceId ?: placeId.toLongOrNull()
    if (numericPlaceId != null) {
        return CreateBookmarkRequestDto(placeId = numericPlaceId)
    }

    val snapshotProvider = provider?.takeIf { it.isNotBlank() } ?: return null
    return CreateBookmarkRequestDto(
        provider = snapshotProvider,
        providerPlaceId = providerPlaceId,
        name = placeName,
        providerCategory = providerCategory ?: category,
        address = address,
        point = BookmarkPointDto(lat = latitude, lng = longitude),
    )
}

private fun BookmarkData.withServerResponse(response: CreateBookmarkResponseDto): BookmarkData {
    val resolvedServerPlaceId = response.placeId ?: serverPlaceId

    return copy(
        bookmarkId = response.bookmarkId,
        bookmarkTargetId = response.bookmarkTargetId,
        targetType = response.targetType,
        serverPlaceId = resolvedServerPlaceId,
    )
}

private fun BookmarkListItemDto.localCachePlaceId(): String =
    placeId?.toString()
        ?: providerPlaceId
            ?.takeIf { it.isNotBlank() }
            ?.let { externalPlaceId -> "provider:${provider.orEmpty().trim().lowercase(Locale.US)}:$externalPlaceId" }
        ?: bookmarkTargetId

fun PlaceDestination.canSaveServerBookmark(): Boolean = toBookmarkDataOrNull() != null

fun PlaceDestination.toBookmarkDataOrNull(): BookmarkData? =
    BookmarkData(
        placeId = placeId,
        placeName = name,
        address = address?.takeIf(String::isNotBlank),
        latitude = latitude,
        longitude = longitude,
        category = category?.name,
        serverPlaceId = serverPlaceId,
        provider = provider?.takeIf(String::isNotBlank),
        providerPlaceId = providerPlaceId?.takeIf(String::isNotBlank),
        providerCategory = providerCategory?.takeIf(String::isNotBlank) ?: category?.name,
    ).takeIf(BookmarkData::canCreateServerSaveRequest)

fun RouteWaypoint.toBookmarkDataOrNull(
    fallbackPlaceId: String,
    fallbackPlaceName: String = "목적지",
): BookmarkData? =
    BookmarkData(
        placeId = placeId?.takeIf(String::isNotBlank) ?: fallbackPlaceId,
        placeName = name.orEmpty().ifBlank { fallbackPlaceName },
        address = address?.takeIf(String::isNotBlank),
        latitude = coordinate.latitude,
        longitude = coordinate.longitude,
        category = category?.name,
        serverPlaceId = serverPlaceId,
        provider = provider?.takeIf(String::isNotBlank),
        providerPlaceId = providerPlaceId?.takeIf(String::isNotBlank),
        providerCategory = providerCategory?.takeIf(String::isNotBlank) ?: category?.name,
    ).takeIf(BookmarkData::canCreateServerSaveRequest)

fun BookmarkData.canCreateServerSaveRequest(): Boolean =
    serverPlaceId != null ||
        placeId.toLongOrNull() != null ||
        !provider.isNullOrBlank()

class BookmarkSaveException(
    message: String,
) : IllegalStateException(message)
