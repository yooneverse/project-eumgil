package com.ssafy.e102.eumgil.data.local.datasource

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ssafy.e102.eumgil.core.model.RecentDestination
import com.ssafy.e102.eumgil.core.model.RecentSearch
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SearchLocalDataSourceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `recent destinations persist across data source recreation`() =
        runTest {
            val dataStore = createDataStore()
            val firstDataSource = SearchLocalDataSource(dataStore = dataStore)
            val secondDataSource = SearchLocalDataSource(dataStore = dataStore)

            firstDataSource.saveRecentDestination(
                RecentDestination(
                    placeId = "place-1",
                    name = "서울역",
                    address = "서울특별시 용산구 한강대로 405",
                    latitude = 37.5547,
                    longitude = 126.9706,
                    accessibilityTagKeys = listOf("WHEELCHAIR_TOILET", "ELEVATOR", "ELEVATOR"),
                ),
            )

            val recentDestinations = secondDataSource.getRecentDestinations()

            assertEquals(
                listOf(
                    RecentDestination(
                        placeId = "place-1",
                        name = "서울역",
                        address = "서울특별시 용산구 한강대로 405",
                        latitude = 37.5547,
                        longitude = 126.9706,
                        accessibilityTagKeys = listOf("WHEELCHAIR_TOILET", "ELEVATOR"),
                        searchedAtMillis = recentDestinations.single().searchedAtMillis,
                    ),
                ),
                recentDestinations,
            )
        }

    @Test
    fun `recent searches persist across data source recreation`() =
        runTest {
            val dataStore = createDataStore()
            val firstDataSource = SearchLocalDataSource(dataStore = dataStore)
            val secondDataSource = SearchLocalDataSource(dataStore = dataStore)

            firstDataSource.saveRecentSearch("부산역")

            val recentSearches = secondDataSource.getRecentSearches()

            assertEquals(
                listOf(
                    RecentSearch(
                        keyword = "부산역",
                        searchedAtMillis = recentSearches.single().searchedAtMillis,
                    ),
                ),
                recentSearches,
            )
        }

    @Test
    fun `recent searches are isolated by authenticated account scope`() =
        runTest {
            val dataStore = createDataStore()
            val firstUserDataSource =
                SearchLocalDataSource(
                    dataStore = dataStore,
                    currentUserScopeProvider = { "user-a" },
                )
            val secondUserDataSource =
                SearchLocalDataSource(
                    dataStore = dataStore,
                    currentUserScopeProvider = { "user-b" },
                )

            firstUserDataSource.saveRecentSearch("부산역")
            secondUserDataSource.saveRecentSearch("서울역")

            val firstUserRecentSearches = firstUserDataSource.getRecentSearches()
            val secondUserRecentSearches = secondUserDataSource.getRecentSearches()

            assertEquals(
                listOf(
                    RecentSearch(
                        keyword = "부산역",
                        searchedAtMillis = firstUserRecentSearches.single().searchedAtMillis,
                    ),
                ),
                firstUserRecentSearches,
            )
            assertEquals(
                listOf(
                    RecentSearch(
                        keyword = "서울역",
                        searchedAtMillis = secondUserRecentSearches.single().searchedAtMillis,
                    ),
                ),
                secondUserRecentSearches,
            )
        }

    @Test
    fun `recent destinations are isolated by authenticated account scope`() =
        runTest {
            val dataStore = createDataStore()
            val firstUserDataSource =
                SearchLocalDataSource(
                    dataStore = dataStore,
                    currentUserScopeProvider = { "user-a" },
                )
            val secondUserDataSource =
                SearchLocalDataSource(
                    dataStore = dataStore,
                    currentUserScopeProvider = { "user-b" },
                )

            firstUserDataSource.saveRecentDestination(
                RecentDestination(
                    placeId = "place-a",
                    name = "부산역",
                    address = "부산 동구 중앙대로 206",
                    latitude = 35.1151,
                    longitude = 129.0414,
                ),
            )
            secondUserDataSource.saveRecentDestination(
                RecentDestination(
                    placeId = "place-b",
                    name = "서울역",
                    address = "서울 용산구 한강대로 405",
                    latitude = 37.5547,
                    longitude = 126.9706,
                ),
            )

            val firstUserRecentDestinations = firstUserDataSource.getRecentDestinations()
            val secondUserRecentDestinations = secondUserDataSource.getRecentDestinations()

            assertEquals(
                listOf(
                    RecentDestination(
                        placeId = "place-a",
                        name = "부산역",
                        address = "부산 동구 중앙대로 206",
                        latitude = 35.1151,
                        longitude = 129.0414,
                        searchedAtMillis = firstUserRecentDestinations.single().searchedAtMillis,
                    ),
                ),
                firstUserRecentDestinations,
            )
            assertEquals(
                listOf(
                    RecentDestination(
                        placeId = "place-b",
                        name = "서울역",
                        address = "서울 용산구 한강대로 405",
                        latitude = 37.5547,
                        longitude = 126.9706,
                        searchedAtMillis = secondUserRecentDestinations.single().searchedAtMillis,
                    ),
                ),
                secondUserRecentDestinations,
            )
        }

    private fun TestScope.createDataStore() =
        PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = {
                File(temporaryFolder.newFolder(), "search_local.preferences_pb")
            },
        )
}
