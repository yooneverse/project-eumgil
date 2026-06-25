package com.ssafy.e102.eumgil.data.repository

import com.ssafy.e102.eumgil.core.model.AuthGateState
import com.ssafy.e102.eumgil.core.model.AuthSession
import com.ssafy.e102.eumgil.data.local.dao.BookmarkDao
import com.ssafy.e102.eumgil.data.local.entity.BookmarkEntity
import com.ssafy.e102.eumgil.data.mock.fixture.MockBookmarkFixtures
import com.ssafy.e102.eumgil.data.remote.HttpJsonClient
import com.ssafy.e102.eumgil.data.remote.datasource.BookmarksRemoteDataSource
import com.ssafy.e102.eumgil.data.remote.dto.BookmarkListItemDto
import com.ssafy.e102.eumgil.data.remote.dto.BookmarkPageDto
import com.ssafy.e102.eumgil.data.remote.dto.BookmarkPointDto
import com.ssafy.e102.eumgil.data.remote.dto.CreateBookmarkRequestDto
import com.ssafy.e102.eumgil.data.remote.dto.CreateBookmarkResponseDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkRepositoryTest {
    @Test
    fun `observeBookmarks seeds debug bookmark when local table is empty`() =
        runBlocking {
            val repository =
                DefaultBookmarkRepository(
                    bookmarkDao = FakeBookmarkDao(),
                    initialBookmarks = MockBookmarkFixtures.defaultBookmarks,
                )

            val bookmarks = repository.observeBookmarks().first()

            assertEquals(MockBookmarkFixtures.defaultBookmarks, bookmarks)
        }

    @Test
    fun `observeBookmarks keeps existing local bookmarks instead of adding debug bookmark`() =
        runBlocking {
            val localBookmark = testBookmarkEntity(placeId = "existing-bookmark")
            val repository =
                DefaultBookmarkRepository(
                    bookmarkDao = FakeBookmarkDao(bookmarks = listOf(localBookmark)),
                    initialBookmarks = MockBookmarkFixtures.defaultBookmarks,
                )

            val bookmarks = repository.observeBookmarks().first()

            assertEquals(listOf(localBookmark.toBookmarkData()), bookmarks)
        }

    @Test
    fun `observeBookmarks fetches cursor page from server and preserves target metadata`() =
        runBlocking {
            val serverItem =
                BookmarkListItemDto(
                    bookmarkId = 1L,
                    bookmarkTargetId = "tgt_0123456789abcdef",
                    targetType = "INTERNAL_PLACE",
                    placeId = 42L,
                    provider = "KAKAO",
                    providerPlaceId = "external-42",
                    name = "Busan Citizens Park",
                    category = "TOURIST_SPOT",
                    providerCategory = null,
                    address = "73 Citizen Park-ro, Busan",
                    point = BookmarkPointDto(lat = 35.1686, lng = 129.0576),
                )
            val fakeDao = FakeBookmarkDao(bookmarks = listOf(testBookmarkEntity(placeId = "stale-cache")))
            val fakeDataSource = FakeBookmarksRemoteDataSource(serverContent = listOf(serverItem))

            val repository =
                DefaultBookmarkRepository(
                    bookmarkDao = fakeDao,
                    bookmarksRemoteDataSource = fakeDataSource,
                    accessTokenProvider = { "test-token" },
                )

            val bookmarks = repository.observeBookmarks().first()

            assertEquals(1, bookmarks.size)
            assertEquals("42", bookmarks[0].placeId)
            assertEquals("tgt_0123456789abcdef", bookmarks[0].bookmarkTargetId)
            assertEquals("INTERNAL_PLACE", bookmarks[0].targetType)
            assertEquals(42L, bookmarks[0].serverPlaceId)
            assertEquals("Busan Citizens Park", bookmarks[0].placeName)
            assertEquals(null, fakeDataSource.lastCursor)
            assertEquals(50, fakeDataSource.lastSize)
        }

    @Test
    fun `observeBookmarks falls back to cache when server fetch fails`() =
        runBlocking {
            val cachedBookmark = testBookmarkEntity(placeId = "cached-bookmark")
            val fakeDao = FakeBookmarkDao(bookmarks = listOf(cachedBookmark))
            val fakeDataSource = FakeBookmarksRemoteDataSource(throwOnGet = true)

            val repository =
                DefaultBookmarkRepository(
                    bookmarkDao = fakeDao,
                    bookmarksRemoteDataSource = fakeDataSource,
                    accessTokenProvider = { "test-token" },
                )

            val bookmarks = repository.observeBookmarks().first()

            assertEquals(listOf(cachedBookmark.toBookmarkData()), bookmarks)
        }

    @Test
    fun `observeBookmarks switches to the new account scope when auth session changes`() =
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
                FakeBookmarkDao(
                    bookmarks =
                        listOf(
                            testBookmarkEntity(placeId = "place-a", accountScopeKey = "user::user-a"),
                            testBookmarkEntity(placeId = "place-b", accountScopeKey = "user::user-b"),
                        ),
                )
            val repository =
                DefaultBookmarkRepository(
                    bookmarkDao = fakeDao,
                    authSessionRepository = authSessionRepository,
                )

            val firstScopeBookmarks = repository.observeBookmarks().first()

            authSessionRepository.updateAuthSession(
                authSession = AuthSession(accessToken = "token-b", userId = "user-b"),
                isProfileCompleted = true,
            )
            val secondScopeBookmarks = repository.observeBookmarks().first()

            assertEquals(listOf("place-a"), firstScopeBookmarks.map(BookmarkData::placeId))
            assertEquals(listOf("place-b"), secondScopeBookmarks.map(BookmarkData::placeId))
        }

    @Test
    fun `observeBookmarks emits empty list when auth session is missing`() =
        runBlocking {
            val authSessionRepository =
                TestAuthSessionRepository(
                    initialState = AuthGateState(authSession = null, isProfileCompleted = false),
                )
            val repository =
                DefaultBookmarkRepository(
                    bookmarkDao =
                        FakeBookmarkDao(
                            bookmarks =
                                listOf(
                                    testBookmarkEntity(placeId = "place-a", accountScopeKey = "user::user-a"),
                                ),
                        ),
                    authSessionRepository = authSessionRepository,
                )

            val bookmarks = repository.observeBookmarks().first()

            assertTrue(bookmarks.isEmpty())
        }

    @Test
    fun `observeBookmarks skips server fetch when access token is null`() =
        runBlocking {
            val cachedBookmark = testBookmarkEntity(placeId = "cached-bookmark")
            val fakeDao = FakeBookmarkDao(bookmarks = listOf(cachedBookmark))
            val fakeDataSource = FakeBookmarksRemoteDataSource()

            val repository =
                DefaultBookmarkRepository(
                    bookmarkDao = fakeDao,
                    bookmarksRemoteDataSource = fakeDataSource,
                    accessTokenProvider = { null },
                )

            val bookmarks = repository.observeBookmarks().first()

            assertEquals(listOf(cachedBookmark.toBookmarkData()), bookmarks)
            assertEquals(0, fakeDataSource.getBookmarksCallCount)
        }

    @Test
    fun `saveBookmark posts internal place id and caches server target`() =
        runBlocking {
            val fakeDao = FakeBookmarkDao()
            val fakeDataSource = FakeBookmarksRemoteDataSource()
            val repository =
                DefaultBookmarkRepository(
                    bookmarkDao = fakeDao,
                    bookmarksRemoteDataSource = fakeDataSource,
                    accessTokenProvider = { "test-token" },
                )

            val savedBookmark =
                repository.saveBookmark(
                    BookmarkData(
                        placeId = "42",
                        placeName = "Busan Citizens Park",
                        address = null,
                        latitude = 35.1686,
                        longitude = 129.0576,
                        category = "TOURIST_SPOT",
                    ),
                )

            assertEquals(listOf(42L), fakeDataSource.createdRequests.map { it.placeId })
            assertEquals("42", savedBookmark.placeId)
            assertEquals("tgt_0123456789abcdef", savedBookmark.bookmarkTargetId)
            assertEquals("INTERNAL_PLACE", savedBookmark.targetType)
            assertEquals(
                "tgt_0123456789abcdef",
                fakeDao.getBookmark(TEST_ACCOUNT_SCOPE_KEY, "42")?.bookmarkTargetId,
            )
        }

    @Test
    fun `saveBookmark prefers serverPlaceId over string placeId for server call`() =
        runBlocking {
            val fakeDao = FakeBookmarkDao()
            val fakeDataSource = FakeBookmarksRemoteDataSource()
            val repository =
                DefaultBookmarkRepository(
                    bookmarkDao = fakeDao,
                    bookmarksRemoteDataSource = fakeDataSource,
                    accessTokenProvider = { "test-token" },
                )

            repository.saveBookmark(
                BookmarkData(
                    placeId = "non-numeric-uuid",
                    placeName = "Busan Citizens Park",
                    address = null,
                    latitude = 35.1686,
                    longitude = 129.0576,
                    category = "TOURIST_SPOT",
                    serverPlaceId = 99L,
                ),
            )

            assertEquals(listOf(99L), fakeDataSource.createdRequests.map { it.placeId })
        }

    @Test
    fun `saveBookmark posts external snapshot and caches returned bookmark target id`() =
        runBlocking {
            val fakeDao = FakeBookmarkDao()
            val fakeDataSource =
                FakeBookmarksRemoteDataSource(
                    createResponse =
                        CreateBookmarkResponseDto(
                            bookmarkId = 7L,
                            bookmarkTargetId = "tgt_fedcba9876543210",
                            targetType = "EXTERNAL_POI",
                            placeId = null,
                        ),
                )
            val repository =
                DefaultBookmarkRepository(
                    bookmarkDao = fakeDao,
                    bookmarksRemoteDataSource = fakeDataSource,
                    accessTokenProvider = { "test-token" },
                )

            val savedBookmark =
                repository.saveBookmark(
                    BookmarkData(
                        placeId = "provider:kakao:poi-123",
                        placeName = "External Cafe",
                        address = "Busan external address",
                        latitude = 35.1686,
                        longitude = 129.0576,
                        category = null,
                        targetType = "EXTERNAL_POI",
                        provider = "KAKAO",
                        providerPlaceId = "poi-123",
                        providerCategory = "Cafe",
                    ),
                )

            val request = fakeDataSource.createdRequests.single()
            assertEquals(null, request.placeId)
            assertEquals("KAKAO", request.provider)
            assertEquals("poi-123", request.providerPlaceId)
            assertEquals("External Cafe", request.name)
            assertEquals("Cafe", request.providerCategory)
            assertEquals("provider:kakao:poi-123", savedBookmark.placeId)
            assertEquals("tgt_fedcba9876543210", savedBookmark.bookmarkTargetId)
            assertEquals("EXTERNAL_POI", savedBookmark.targetType)
            assertEquals(
                "tgt_fedcba9876543210",
                fakeDao.getBookmark(TEST_ACCOUNT_SCOPE_KEY, "provider:kakao:poi-123")?.bookmarkTargetId,
            )
        }

    @Test
    fun `saveBookmark fails without access token and does not cache locally`() =
        runBlocking {
            val fakeDao = FakeBookmarkDao()
            val fakeDataSource = FakeBookmarksRemoteDataSource()
            val repository =
                DefaultBookmarkRepository(
                    bookmarkDao = fakeDao,
                    bookmarksRemoteDataSource = fakeDataSource,
                    accessTokenProvider = { null },
                )

            val result =
                runCatching {
                    repository.saveBookmark(
                        BookmarkData(
                            placeId = "42",
                            placeName = "Busan Citizens Park",
                            address = null,
                            latitude = 35.1686,
                            longitude = 129.0576,
                            category = null,
                        ),
                    )
                }

            assertTrue(result.isFailure)
            assertTrue(fakeDataSource.createdRequests.isEmpty())
            assertEquals(0, fakeDao.bookmarkCount())
        }

    @Test
    fun `saveBookmark fails when request cannot be represented and does not cache locally`() =
        runBlocking {
            val fakeDao = FakeBookmarkDao()
            val fakeDataSource = FakeBookmarksRemoteDataSource()
            val repository =
                DefaultBookmarkRepository(
                    bookmarkDao = fakeDao,
                    bookmarksRemoteDataSource = fakeDataSource,
                    accessTokenProvider = { "test-token" },
                )

            val result =
                runCatching {
                    repository.saveBookmark(
                        BookmarkData(
                            placeId = "kakao-only-id",
                            placeName = "External place without provider",
                            address = null,
                            latitude = 0.0,
                            longitude = 0.0,
                            category = null,
                        ),
                    )
                }

            assertTrue(result.isFailure)
            assertTrue(fakeDataSource.createdRequests.isEmpty())
            assertEquals(0, fakeDao.bookmarkCount())
        }

    @Test
    fun `saveBookmark keeps cache empty when server create fails`() =
        runBlocking {
            val fakeDao = FakeBookmarkDao()
            val fakeDataSource = FakeBookmarksRemoteDataSource(throwOnCreate = true)
            val repository =
                DefaultBookmarkRepository(
                    bookmarkDao = fakeDao,
                    bookmarksRemoteDataSource = fakeDataSource,
                    accessTokenProvider = { "test-token" },
                )

            val result =
                runCatching {
                    repository.saveBookmark(
                        BookmarkData(
                            placeId = "42",
                            placeName = "Busan Citizens Park",
                            address = null,
                            latitude = 35.1686,
                            longitude = 129.0576,
                            category = "TOURIST_SPOT",
                        ),
                    )
                }

            assertTrue(result.isFailure)
            assertEquals(1, fakeDataSource.createdRequests.size)
            assertEquals(0, fakeDao.bookmarkCount())
        }

    @Test
    fun `deleteBookmark calls target endpoint before place compatibility endpoint`() =
        runBlocking {
            val cachedBookmark = testBookmarkEntity(placeId = "42", bookmarkTargetId = "tgt_0123456789abcdef")
            val fakeDao = FakeBookmarkDao(bookmarks = listOf(cachedBookmark))
            val fakeDataSource = FakeBookmarksRemoteDataSource()
            val repository =
                DefaultBookmarkRepository(
                    bookmarkDao = fakeDao,
                    bookmarksRemoteDataSource = fakeDataSource,
                    accessTokenProvider = { "test-token" },
                )

            repository.deleteBookmark("42")

            assertEquals(listOf("tgt_0123456789abcdef"), fakeDataSource.deletedTargetIds)
            assertTrue(fakeDataSource.deletedPlaceIds.isEmpty())
            assertEquals(0, fakeDao.bookmarkCount())
        }

    @Test
    fun `deleteBookmark falls back to place endpoint when target id is missing`() =
        runBlocking {
            val cachedBookmark = testBookmarkEntity(placeId = "42")
            val fakeDao = FakeBookmarkDao(bookmarks = listOf(cachedBookmark))
            val fakeDataSource = FakeBookmarksRemoteDataSource()
            val repository =
                DefaultBookmarkRepository(
                    bookmarkDao = fakeDao,
                    bookmarksRemoteDataSource = fakeDataSource,
                    accessTokenProvider = { "test-token" },
                )

            repository.deleteBookmark("42")

            assertEquals(listOf(42L), fakeDataSource.deletedPlaceIds)
            assertEquals(0, fakeDao.bookmarkCount())
        }

    @Test
    fun `deleteBookmark keeps cache and throws when server call fails`() =
        runBlocking {
            val cachedBookmark = testBookmarkEntity(placeId = "42", bookmarkTargetId = "tgt_0123456789abcdef")
            val fakeDao = FakeBookmarkDao(bookmarks = listOf(cachedBookmark))
            val fakeDataSource = FakeBookmarksRemoteDataSource(throwOnDelete = true)
            val repository =
                DefaultBookmarkRepository(
                    bookmarkDao = fakeDao,
                    bookmarksRemoteDataSource = fakeDataSource,
                    accessTokenProvider = { "test-token" },
                )

            val result = runCatching { repository.deleteBookmark("42") }

            assertTrue(result.isFailure)
            assertEquals(1, fakeDao.bookmarkCount())
        }
}

private class FakeBookmarkDao(
    bookmarks: List<BookmarkEntity> = emptyList(),
) : BookmarkDao {
    private val mutableBookmarks = MutableStateFlow(bookmarks)

    override fun observeBookmarks(accountScopeKey: String): Flow<List<BookmarkEntity>> =
        mutableBookmarks.map { bookmarks -> bookmarks.filterByScope(accountScopeKey) }

    override fun observeBookmark(
        accountScopeKey: String,
        placeId: String,
    ): Flow<BookmarkEntity?> =
        mutableBookmarks.map { bookmarks ->
            bookmarks.firstOrNull { bookmark ->
                bookmark.accountScopeKey == accountScopeKey && bookmark.placeId == placeId
            }
        }

    override suspend fun getBookmark(
        accountScopeKey: String,
        placeId: String,
    ): BookmarkEntity? =
        mutableBookmarks.value.firstOrNull { bookmark ->
            bookmark.accountScopeKey == accountScopeKey && bookmark.placeId == placeId
        }

    override suspend fun getBookmarkByTargetId(
        accountScopeKey: String,
        bookmarkTargetId: String,
    ): BookmarkEntity? =
        mutableBookmarks.value.firstOrNull { bookmark ->
            bookmark.accountScopeKey == accountScopeKey && bookmark.bookmarkTargetId == bookmarkTargetId
        }

    override suspend fun getBookmarkCount(accountScopeKey: String): Int =
        mutableBookmarks.value.count { bookmark -> bookmark.accountScopeKey == accountScopeKey }

    override suspend fun upsertBookmark(bookmark: BookmarkEntity) {
        mutableBookmarks.value = mutableBookmarks.value.upsert(bookmark)
    }

    override suspend fun upsertBookmarks(bookmarks: List<BookmarkEntity>) {
        bookmarks.forEach { bookmark -> upsertBookmark(bookmark) }
    }

    override suspend fun deleteBookmark(
        accountScopeKey: String,
        placeId: String,
    ) {
        mutableBookmarks.value =
            mutableBookmarks.value.filterNot { bookmark ->
                bookmark.accountScopeKey == accountScopeKey && bookmark.placeId == placeId
            }
    }

    override suspend fun deleteBookmarkByTargetId(
        accountScopeKey: String,
        bookmarkTargetId: String,
    ) {
        mutableBookmarks.value =
            mutableBookmarks.value.filterNot { bookmark ->
                bookmark.accountScopeKey == accountScopeKey && bookmark.bookmarkTargetId == bookmarkTargetId
            }
    }

    override suspend fun clearBookmarks(accountScopeKey: String) {
        mutableBookmarks.value =
            mutableBookmarks.value.filterNot { bookmark -> bookmark.accountScopeKey == accountScopeKey }
    }

    fun bookmarkCount(accountScopeKey: String = TEST_ACCOUNT_SCOPE_KEY): Int =
        mutableBookmarks.value.count { bookmark -> bookmark.accountScopeKey == accountScopeKey }

    private fun List<BookmarkEntity>.filterByScope(accountScopeKey: String): List<BookmarkEntity> =
        filter { bookmark -> bookmark.accountScopeKey == accountScopeKey }
}

private class FakeBookmarksRemoteDataSource(
    private val serverContent: List<BookmarkListItemDto> = emptyList(),
    private val createResponse: CreateBookmarkResponseDto? = null,
    private val throwOnGet: Boolean = false,
    private val throwOnCreate: Boolean = false,
    private val throwOnDelete: Boolean = false,
) : BookmarksRemoteDataSource(httpJsonClient = HttpJsonClient(baseUrl = "http://test.invalid")) {
    val createdRequests = mutableListOf<CreateBookmarkRequestDto>()
    val deletedPlaceIds = mutableListOf<Long>()
    val deletedTargetIds = mutableListOf<String>()
    var getBookmarksCallCount: Int = 0
        private set
    var lastCursor: Long? = null
        private set
    var lastSize: Int? = null
        private set

    override suspend fun getBookmarks(
        accessToken: String,
        cursor: Long?,
        size: Int?,
    ): BookmarkPageDto {
        getBookmarksCallCount++
        lastCursor = cursor
        lastSize = size
        if (throwOnGet) throw RuntimeException("server get failure")
        return BookmarkPageDto(
            content = serverContent,
            size = size ?: serverContent.size,
            nextCursor = null,
            hasNext = false,
        )
    }

    override suspend fun createBookmark(
        accessToken: String,
        request: CreateBookmarkRequestDto,
    ): CreateBookmarkResponseDto {
        createdRequests.add(request)
        if (throwOnCreate) throw RuntimeException("server create failure")
        return createResponse
            ?: CreateBookmarkResponseDto(
                bookmarkId = 1L,
                bookmarkTargetId = "tgt_0123456789abcdef",
                targetType = if (request.placeId != null) "INTERNAL_PLACE" else "EXTERNAL_POI",
                placeId = request.placeId,
            )
    }

    override suspend fun deleteBookmarkByTargetId(
        accessToken: String,
        bookmarkTargetId: String,
    ) {
        if (throwOnDelete) throw RuntimeException("server delete failure")
        deletedTargetIds.add(bookmarkTargetId)
    }

    override suspend fun deleteBookmark(
        accessToken: String,
        placeId: Long,
    ) {
        if (throwOnDelete) throw RuntimeException("server delete failure")
        deletedPlaceIds.add(placeId)
    }
}

private fun List<BookmarkEntity>.upsert(bookmark: BookmarkEntity): List<BookmarkEntity> =
    filterNot { existing ->
        existing.accountScopeKey == bookmark.accountScopeKey && existing.placeId == bookmark.placeId
    } + bookmark

private fun testBookmarkEntity(
    placeId: String,
    bookmarkTargetId: String? = null,
    accountScopeKey: String = TEST_ACCOUNT_SCOPE_KEY,
): BookmarkEntity =
    BookmarkEntity(
        accountScopeKey = accountScopeKey,
        placeId = placeId,
        serverBookmarkId = 1L,
        bookmarkTargetId = bookmarkTargetId,
        targetType = "INTERNAL_PLACE",
        serverPlaceId = placeId.toLongOrNull(),
        placeName = "Cached Bookmark",
        address = "206 Jungang-daero, Busan",
        latitude = 35.1151,
        longitude = 129.0415,
        category = "ELEVATOR",
    )

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

private const val TEST_ACCOUNT_SCOPE_KEY: String = "test-account"
