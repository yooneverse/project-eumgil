package com.ssafy.e102.eumgil.data.repository

import com.ssafy.e102.eumgil.core.model.BrailleBlockType
import com.ssafy.e102.eumgil.core.model.FacilityBrowseData
import com.ssafy.e102.eumgil.core.model.FacilityCategory
import com.ssafy.e102.eumgil.core.model.FacilityDetailSeed
import com.ssafy.e102.eumgil.core.model.FacilityMarkerSeed
import com.ssafy.e102.eumgil.core.model.FacilitySeed
import com.ssafy.e102.eumgil.core.model.FacilitySeedCatalog
import com.ssafy.e102.eumgil.core.model.FacilitySeedQuery

internal object FacilitySeedReadModelMapper {
    fun toBrowseData(
        catalog: FacilitySeedCatalog,
        query: FacilitySeedQuery,
    ): FacilityBrowseData {
        val filteredSeeds =
            catalog
                .allSeeds
                .filter { seed -> seed.matches(query) }

        val detailSeeds = filteredSeeds.map { seed -> seed.toDetailSeed() }
        val markerSeeds = filteredSeeds.map { seed -> seed.toMarkerSeed() }

        return FacilityBrowseData(
            facilityMarkers = markerSeeds.filter { marker -> marker.category != FacilityCategory.BRAILLE_BLOCK },
            brailleBlockMarkers = markerSeeds.filter { marker -> marker.category == FacilityCategory.BRAILLE_BLOCK },
            detailsById = detailSeeds.associateBy(FacilityDetailSeed::facilityId),
            availableCategories =
                // Keep filter options stable from the full catalog, not from the current query result only.
                catalog.allSeeds
                    .map(FacilitySeed::category)
                    .distinct()
                    .sortedBy(FacilityCategory::ordinal),
            availableBrailleBlockTypes =
                // Braille type options remain catalog-wide for the same reason as category chips.
                catalog.brailleBlocks
                    .mapNotNull(FacilitySeed::brailleBlockType)
                    .distinct()
                    .sortedBy(BrailleBlockType::ordinal),
        )
    }

    fun toDetail(
        catalog: FacilitySeedCatalog,
        facilityId: String,
    ): FacilityDetailSeed? =
        catalog
            .allSeeds
            .firstOrNull { seed -> seed.facilityId == facilityId }
            ?.toDetailSeed()

    private fun FacilitySeed.matches(query: FacilitySeedQuery): Boolean {
        val normalizedKeyword = query.keyword?.trim()?.lowercase().orEmpty()

        return (normalizedKeyword.isBlank() ||
            name.lowercase().contains(normalizedKeyword) ||
            address.lowercase().contains(normalizedKeyword)) &&
            (query.categories.isEmpty() || category in query.categories) &&
            (query.brailleBlockTypes.isEmpty() || brailleBlockType in query.brailleBlockTypes)
    }

    private fun FacilitySeed.toMarkerSeed(): FacilityMarkerSeed =
        FacilityMarkerSeed(
            facilityId = facilityId,
            name = name,
            coordinate = coordinate,
            category = category,
            accessibilityTags = accessibilityTags,
            brailleBlockType = brailleBlockType,
        )

    private fun FacilitySeed.toDetailSeed(): FacilityDetailSeed =
        FacilityDetailSeed(
            facilityId = facilityId,
            name = name,
            address = address,
            coordinate = coordinate,
            category = category,
            accessibilityTags = accessibilityTags,
            brailleBlockType = brailleBlockType,
            description = description,
        )
}
