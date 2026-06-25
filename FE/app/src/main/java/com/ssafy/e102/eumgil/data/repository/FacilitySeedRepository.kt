package com.ssafy.e102.eumgil.data.repository

import com.ssafy.e102.eumgil.core.model.FacilityBrowseData
import com.ssafy.e102.eumgil.core.model.FacilityDetailSeed
import com.ssafy.e102.eumgil.core.model.FacilityMarkerSeed
import com.ssafy.e102.eumgil.core.model.FacilitySeedCatalog
import com.ssafy.e102.eumgil.core.model.FacilitySeedQuery
import com.ssafy.e102.eumgil.data.local.datasource.FacilitySeedLocalDataSource
import com.ssafy.e102.eumgil.data.mock.datasource.FacilitySeedMockDataSource

interface FacilitySeedRepository {
    // Raw seed source of truth for follow-up mapping or future source replacement.
    suspend fun getSeedCatalog(): FacilitySeedCatalog

    // Primary browse entry point for 118 marker/filter work and 119 marker-selection handoff.
    suspend fun getFacilityBrowseData(query: FacilitySeedQuery = FacilitySeedQuery()): FacilityBrowseData

    suspend fun getFacilityMarkers(query: FacilitySeedQuery = FacilitySeedQuery()): List<FacilityMarkerSeed>

    suspend fun getFacilityDetail(facilityId: String): FacilityDetailSeed?
}

class DefaultFacilitySeedRepository(
    private val localDataSource: FacilitySeedLocalDataSource,
    private val mockDataSource: FacilitySeedMockDataSource,
) : FacilitySeedRepository {
    override suspend fun getSeedCatalog(): FacilitySeedCatalog {
        localDataSource.getCachedSeedCatalog()?.let { cachedCatalog ->
            return cachedCatalog
        }

        val seedCatalog = mockDataSource.getSeedCatalog()
        localDataSource.updateCachedSeedCatalog(seedCatalog)
        return seedCatalog
    }

    override suspend fun getFacilityBrowseData(query: FacilitySeedQuery): FacilityBrowseData {
        localDataSource.getCachedBrowseData(query)?.let { cachedBrowseData ->
            return cachedBrowseData
        }

        val browseData =
            FacilitySeedReadModelMapper.toBrowseData(
                catalog = getSeedCatalog(),
                query = query,
            )
        localDataSource.updateCachedBrowseData(query = query, browseData = browseData)
        localDataSource.updateCachedFacilityDetails(browseData.detailsById.values)
        return browseData
    }

    override suspend fun getFacilityMarkers(query: FacilitySeedQuery): List<FacilityMarkerSeed> =
        getFacilityBrowseData(query).allMarkers

    override suspend fun getFacilityDetail(facilityId: String): FacilityDetailSeed? {
        localDataSource.getCachedFacilityDetail(facilityId)?.let { cachedDetail ->
            return cachedDetail
        }

        val detail =
            FacilitySeedReadModelMapper.toDetail(
                catalog = getSeedCatalog(),
                facilityId = facilityId,
            )
        if (detail != null) {
            localDataSource.updateCachedFacilityDetail(detail)
        }
        return detail
    }
}
