package com.ssafy.e102.eumgil.core.model

data class GeoCoordinate(
    val latitude: Double,
    val longitude: Double,
)

enum class FacilityCategory {
    TOILET,
    ELEVATOR,
    CHARGING_STATION,
    FOOD_CAFE,
    TOURIST_SPOT,
    ACCOMMODATION,
    HEALTHCARE,
    WELFARE,
    PUBLIC_OFFICE,
    BRAILLE_BLOCK,
    RESTAURANT,
    TOURIST_ATTRACTION,
    OTHER,
}

enum class AccessibilityTag {
    RAMP,
    STEP_FREE_ENTRANCE,
    AUTO_DOOR,
    WIDE_ENTRY,
    ACCESSIBLE_TOILET,
    ELEVATOR,
    CHARGING_STATION,
    WHEELCHAIR_TURNING_SPACE,
    TABLE_SPACING,
    ACCESSIBLE_PARKING,
    GUIDANCE_FACILITY,
    ACCESSIBLE_ROOM,
    LOW_HEIGHT_BUTTON,
    REST_AREA,
    OPEN_24_HOURS,
}

enum class BrailleBlockType {
    GUIDING_LINE,
    WARNING_SURFACE,
    CROSSWALK_APPROACH,
}

data class FacilitySeedQuery(
    val keyword: String? = null,
    val categories: Set<FacilityCategory> = emptySet(),
    val brailleBlockTypes: Set<BrailleBlockType> = emptySet(),
)

data class FacilitySeed(
    val facilityId: String,
    val name: String,
    val address: String,
    val coordinate: GeoCoordinate,
    val category: FacilityCategory,
    val accessibilityTags: List<AccessibilityTag> = emptyList(),
    val brailleBlockType: BrailleBlockType? = null,
    val description: String? = null,
) {
    init {
        require(category == FacilityCategory.BRAILLE_BLOCK || brailleBlockType == null) {
            "Braille block type can only be assigned to BRAILLE_BLOCK seeds."
        }
    }
}

data class FacilitySeedCatalog(
    val facilities: List<FacilitySeed> = emptyList(),
    val brailleBlocks: List<FacilitySeed> = emptyList(),
) {
    val allSeeds: List<FacilitySeed> = facilities + brailleBlocks

    init {
        require(facilities.none { seed -> seed.category == FacilityCategory.BRAILLE_BLOCK }) {
            "Facility seed list must not contain BRAILLE_BLOCK entries."
        }
        require(brailleBlocks.all { seed -> seed.category == FacilityCategory.BRAILLE_BLOCK }) {
            "Braille block seed list must only contain BRAILLE_BLOCK entries."
        }
        val duplicateIds =
            allSeeds
                .groupingBy { seed -> seed.facilityId }
                .eachCount()
                .filterValues { count -> count > 1 }
                .keys
        require(duplicateIds.isEmpty()) {
            "Facility seed ids must be unique. Duplicates: $duplicateIds"
        }
    }
}

/**
 * 118 consumes the separated marker lists and filter options.
 * 119 reuses detailsById for marker-id to detail handoff without rescanning the seed catalog.
 */
data class FacilityBrowseData(
    val facilityMarkers: List<FacilityMarkerSeed> = emptyList(),
    val brailleBlockMarkers: List<FacilityMarkerSeed> = emptyList(),
    val detailsById: Map<String, FacilityDetailSeed> = emptyMap(),
    val availableCategories: List<FacilityCategory> = emptyList(),
    val availableBrailleBlockTypes: List<BrailleBlockType> = emptyList(),
) {
    val allMarkers: List<FacilityMarkerSeed> = facilityMarkers + brailleBlockMarkers

    fun detailFor(facilityId: String): FacilityDetailSeed? = detailsById[facilityId]
}

data class FacilityMarkerSeed(
    val facilityId: String,
    val name: String,
    val coordinate: GeoCoordinate,
    val category: FacilityCategory,
    val filterCategories: Set<FacilityCategory> = setOf(category),
    val markerKind: PlaceMarkerKind = PlaceMarkerKind.DEFAULT,
    val accessibilityTags: List<AccessibilityTag> = emptyList(),
    val brailleBlockType: BrailleBlockType? = null,
)

data class FacilityDetailSeed(
    val facilityId: String,
    val name: String,
    val address: String,
    val coordinate: GeoCoordinate,
    val category: FacilityCategory,
    val accessibilityTags: List<AccessibilityTag> = emptyList(),
    val brailleBlockType: BrailleBlockType? = null,
    val phoneNumber: String? = null,
    val description: String? = null,
)
