package com.ssafy.e102.eumgil.data.local.datasource

import com.ssafy.e102.eumgil.core.model.BrailleBlockType
import com.ssafy.e102.eumgil.core.model.FacilityBrowseData
import com.ssafy.e102.eumgil.core.model.FacilityCategory
import com.ssafy.e102.eumgil.core.model.FacilityDetailSeed
import com.ssafy.e102.eumgil.core.model.FacilitySeedCatalog
import com.ssafy.e102.eumgil.core.model.FacilitySeedQuery
import java.util.concurrent.ConcurrentHashMap

class FacilitySeedLocalDataSource {
    private var cachedSeedCatalog: FacilitySeedCatalog? = null
    private val browseDataByQuery = ConcurrentHashMap<String, FacilityBrowseData>()
    private val detailById = ConcurrentHashMap<String, FacilityDetailSeed>()

    suspend fun getCachedSeedCatalog(): FacilitySeedCatalog? = cachedSeedCatalog

    suspend fun updateCachedSeedCatalog(catalog: FacilitySeedCatalog) {
        cachedSeedCatalog = catalog
    }

    suspend fun getCachedBrowseData(query: FacilitySeedQuery): FacilityBrowseData? =
        browseDataByQuery[query.cacheKey()]

    suspend fun updateCachedBrowseData(
        query: FacilitySeedQuery,
        browseData: FacilityBrowseData,
    ) {
        browseDataByQuery[query.cacheKey()] = browseData
    }

    suspend fun getCachedFacilityDetail(facilityId: String): FacilityDetailSeed? = detailById[facilityId]

    suspend fun updateCachedFacilityDetail(detail: FacilityDetailSeed) {
        detailById[detail.facilityId] = detail
    }

    suspend fun updateCachedFacilityDetails(details: Collection<FacilityDetailSeed>) {
        details.forEach { detail -> detailById[detail.facilityId] = detail }
    }

    private fun FacilitySeedQuery.cacheKey(): String =
        listOf(
            keyword?.trim()?.lowercase().orEmpty(),
            categories
                .sortedBy(FacilityCategory::name)
                .joinToString(separator = ","),
            brailleBlockTypes
                .sortedBy(BrailleBlockType::name)
                .joinToString(separator = ","),
        ).joinToString(separator = "|")
}
