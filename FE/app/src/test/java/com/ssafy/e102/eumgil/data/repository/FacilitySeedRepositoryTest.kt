package com.ssafy.e102.eumgil.data.repository

import com.ssafy.e102.eumgil.core.model.BrailleBlockType
import com.ssafy.e102.eumgil.core.model.FacilityCategory
import com.ssafy.e102.eumgil.core.model.FacilitySeedQuery
import com.ssafy.e102.eumgil.data.local.datasource.FacilitySeedLocalDataSource
import com.ssafy.e102.eumgil.data.mock.datasource.FacilitySeedMockDataSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FacilitySeedRepositoryTest {
    private val repository: FacilitySeedRepository =
        DefaultFacilitySeedRepository(
            localDataSource = FacilitySeedLocalDataSource(),
            mockDataSource = FacilitySeedMockDataSource(),
        )

    @Test
    fun `getSeedCatalog separates facilities and braille blocks for later repository reads`() =
        runBlocking {
            val catalog = repository.getSeedCatalog()

            assertEquals(13, catalog.facilities.size)
            assertEquals(4, catalog.brailleBlocks.size)
            assertEquals(17, catalog.allSeeds.size)
            assertTrue(catalog.facilities.all { seed -> seed.category != FacilityCategory.BRAILLE_BLOCK })
            assertTrue(catalog.brailleBlocks.all { seed -> seed.category == FacilityCategory.BRAILLE_BLOCK })
        }

    @Test
    fun `getSeedCatalog covers marker categories and braille block types`() =
        runBlocking {
            val catalog = repository.getSeedCatalog()

            assertEquals(
                setOf(
                    FacilityCategory.FOOD_CAFE,
                    FacilityCategory.TOURIST_SPOT,
                    FacilityCategory.TOILET,
                    FacilityCategory.ELEVATOR,
                    FacilityCategory.CHARGING_STATION,
                    FacilityCategory.ACCOMMODATION,
                    FacilityCategory.HEALTHCARE,
                    FacilityCategory.WELFARE,
                    FacilityCategory.PUBLIC_OFFICE,
                ),
                catalog.facilities.map { seed -> seed.category }.toSet(),
            )
            assertEquals(
                setOf(
                    BrailleBlockType.GUIDING_LINE,
                    BrailleBlockType.WARNING_SURFACE,
                    BrailleBlockType.CROSSWALK_APPROACH,
                ),
                catalog.brailleBlocks.mapNotNull { seed -> seed.brailleBlockType }.toSet(),
            )
        }

    @Test
    fun `getFacilityBrowseData returns separated markers and reusable detail lookup`() =
        runBlocking {
            val browseData = repository.getFacilityBrowseData()

            assertEquals(13, browseData.facilityMarkers.size)
            assertEquals(4, browseData.brailleBlockMarkers.size)
            assertEquals(17, browseData.allMarkers.size)
            assertEquals(17, browseData.detailsById.size)
            assertEquals(
                listOf(
                    FacilityCategory.TOILET,
                    FacilityCategory.ELEVATOR,
                    FacilityCategory.CHARGING_STATION,
                    FacilityCategory.FOOD_CAFE,
                    FacilityCategory.TOURIST_SPOT,
                    FacilityCategory.ACCOMMODATION,
                    FacilityCategory.HEALTHCARE,
                    FacilityCategory.WELFARE,
                    FacilityCategory.PUBLIC_OFFICE,
                    FacilityCategory.BRAILLE_BLOCK,
                ),
                browseData.availableCategories,
            )
            assertEquals(
                listOf(
                    BrailleBlockType.GUIDING_LINE,
                    BrailleBlockType.WARNING_SURFACE,
                    BrailleBlockType.CROSSWALK_APPROACH,
                ),
                browseData.availableBrailleBlockTypes,
            )
            assertNotNull(browseData.detailFor("facility-elevator-haeundae-exit1-1"))
        }

    @Test
    fun `getFacilityBrowseData caches browse payload and detail lookup for follow-up consumers`() =
        runBlocking {
            val localDataSource = FacilitySeedLocalDataSource()
            val repository =
                DefaultFacilitySeedRepository(
                    localDataSource = localDataSource,
                    mockDataSource = FacilitySeedMockDataSource(),
                )
            val query = FacilitySeedQuery(categories = setOf(FacilityCategory.TOILET))

            val browseData = repository.getFacilityBrowseData(query)
            val cachedBrowseData = localDataSource.getCachedBrowseData(query)
            val cachedDetail = localDataSource.getCachedFacilityDetail("facility-toilet-haeundae-station-1")

            assertEquals(browseData, cachedBrowseData)
            assertEquals(browseData.detailFor("facility-toilet-haeundae-station-1"), cachedDetail)
            assertEquals(cachedDetail, repository.getFacilityDetail("facility-toilet-haeundae-station-1"))
        }

    @Test
    fun `getFacilityBrowseData keeps marker ids aligned with detail ids for downstream selection flow`() =
        runBlocking {
            val browseData =
                repository.getFacilityBrowseData(
                    FacilitySeedQuery(
                        categories = setOf(FacilityCategory.TOILET, FacilityCategory.BRAILLE_BLOCK),
                    ),
                )

            assertEquals(
                browseData.allMarkers.map { marker -> marker.facilityId }.toSet(),
                browseData.detailsById.keys,
            )
            assertEquals(2, browseData.facilityMarkers.size)
            assertEquals(4, browseData.brailleBlockMarkers.size)
        }

    @Test
    fun `getFacilityMarkers filters by category and braille block type`() =
        runBlocking {
            val markers =
                repository.getFacilityMarkers(
                    FacilitySeedQuery(
                        categories = setOf(FacilityCategory.BRAILLE_BLOCK),
                        brailleBlockTypes = setOf(BrailleBlockType.CROSSWALK_APPROACH),
                    ),
                )

            assertEquals(1, markers.size)
            assertEquals("braille-crosswalk-gunamro-1", markers.single().facilityId)
        }

    @Test
    fun `getFacilityDetail returns detail seed for bottom sheet consumers`() =
        runBlocking {
            val detail = repository.getFacilityDetail("facility-elevator-haeundae-exit1-1")

            assertNotNull(detail)
            assertEquals(FacilityCategory.ELEVATOR, detail?.category)
        }
}
