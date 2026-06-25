package com.ssafy.e102.eumgil.data.mock.seed

import com.ssafy.e102.eumgil.core.model.AccessibilityTag
import com.ssafy.e102.eumgil.core.model.BrailleBlockType
import com.ssafy.e102.eumgil.core.model.FacilityCategory
import com.ssafy.e102.eumgil.core.model.FacilitySeed
import com.ssafy.e102.eumgil.core.model.FacilitySeedCatalog
import com.ssafy.e102.eumgil.core.model.GeoCoordinate

object MockFacilitySeedCatalog {
    // Standalone facility markers used by the map browse flow.
    val facilities: List<FacilitySeed> =
        listOf(
            FacilitySeed(
                facilityId = "facility-toilet-haeundae-station-1",
                name = "Haeundae Station Accessible Toilet",
                address = "35 Jungdong 1-ro, Haeundae-gu, Busan",
                coordinate = GeoCoordinate(latitude = 35.16305, longitude = 129.15872),
                category = FacilityCategory.TOILET,
                accessibilityTags =
                    listOf(
                        AccessibilityTag.WHEELCHAIR_TURNING_SPACE,
                        AccessibilityTag.AUTO_DOOR,
                        AccessibilityTag.ELEVATOR,
                    ),
                description = "Accessible restroom near Haeundae Station Exit 1.",
            ),
            FacilitySeed(
                facilityId = "facility-restaurant-cafe-ondo-1",
                name = "Cafe Ondo",
                address = "123 Haeundae-ro, Haeundae-gu, Busan",
                coordinate = GeoCoordinate(latitude = 35.16241, longitude = 129.15994),
                category = FacilityCategory.FOOD_CAFE,
                accessibilityTags =
                    listOf(
                        AccessibilityTag.RAMP,
                        AccessibilityTag.AUTO_DOOR,
                        AccessibilityTag.TABLE_SPACING,
                        AccessibilityTag.ACCESSIBLE_PARKING,
                    ),
                description = "Cafe seed with ramp access and generous table spacing.",
            ),
            FacilitySeed(
                facilityId = "facility-restaurant-haebyeonmaru-1",
                name = "Haebyeonmaru",
                address = "42 Gunam-ro, Haeundae-gu, Busan",
                coordinate = GeoCoordinate(latitude = 35.16094, longitude = 129.15796),
                category = FacilityCategory.FOOD_CAFE,
                accessibilityTags =
                    listOf(
                        AccessibilityTag.STEP_FREE_ENTRANCE,
                        AccessibilityTag.ACCESSIBLE_TOILET,
                        AccessibilityTag.TABLE_SPACING,
                        AccessibilityTag.ELEVATOR,
                    ),
                description = "Restaurant seed kept for category filtering and detail consumption.",
            ),
            FacilitySeed(
                facilityId = "facility-tourist-info-center-1",
                name = "Haeundae Beach Tourist Information Center",
                address = "264 Haeundaehaebyeon-ro, Haeundae-gu, Busan",
                coordinate = GeoCoordinate(latitude = 35.15988, longitude = 129.16012),
                category = FacilityCategory.TOURIST_SPOT,
                accessibilityTags =
                    listOf(
                        AccessibilityTag.RAMP,
                        AccessibilityTag.WIDE_ENTRY,
                        AccessibilityTag.REST_AREA,
                    ),
                description = "Tourist information point near the beach promenade.",
            ),
            FacilitySeed(
                facilityId = "facility-tourist-aquarium-plaza-1",
                name = "SEA LIFE Busan Plaza Entrance",
                address = "266 Haeundaehaebyeon-ro, Haeundae-gu, Busan",
                coordinate = GeoCoordinate(latitude = 35.15935, longitude = 129.16089),
                category = FacilityCategory.TOURIST_SPOT,
                accessibilityTags =
                    listOf(
                        AccessibilityTag.STEP_FREE_ENTRANCE,
                        AccessibilityTag.AUTO_DOOR,
                        AccessibilityTag.WIDE_ENTRY,
                    ),
                description = "Tourist attraction entrance seed for accessible entry scenarios.",
            ),
            FacilitySeed(
                facilityId = "facility-toilet-transit-plaza-1",
                name = "Central Transit Plaza Accessible Toilet",
                address = "539-10 U-dong, Haeundae-gu, Busan",
                coordinate = GeoCoordinate(latitude = 35.16047, longitude = 129.16038),
                category = FacilityCategory.TOILET,
                accessibilityTags =
                    listOf(
                        AccessibilityTag.WIDE_ENTRY,
                        AccessibilityTag.AUTO_DOOR,
                        AccessibilityTag.OPEN_24_HOURS,
                    ),
                description = "Transit plaza restroom kept in the seed set for detail sheet validation.",
            ),
            FacilitySeed(
                facilityId = "facility-elevator-haeundae-exit1-1",
                name = "Haeundae Station Exit 1 Elevator",
                address = "35 Jungdong 1-ro, Haeundae-gu, Busan",
                coordinate = GeoCoordinate(latitude = 35.16312, longitude = 129.16053),
                category = FacilityCategory.ELEVATOR,
                accessibilityTags =
                    listOf(
                        AccessibilityTag.WIDE_ENTRY,
                        AccessibilityTag.LOW_HEIGHT_BUTTON,
                    ),
                description = "Station to street elevator connection used as a standalone facility seed.",
            ),
            FacilitySeed(
                facilityId = "facility-elevator-dalmaji-1",
                name = "Dalmaji Coastal Walk Elevator",
                address = "190 Dalmaji-gil, Haeundae-gu, Busan",
                coordinate = GeoCoordinate(latitude = 35.16127, longitude = 129.16412),
                category = FacilityCategory.ELEVATOR,
                accessibilityTags =
                    listOf(
                        AccessibilityTag.STEP_FREE_ENTRANCE,
                        AccessibilityTag.LOW_HEIGHT_BUTTON,
                        AccessibilityTag.REST_AREA,
                    ),
                description = "Outdoor elevator seed near an accessible sightseeing route.",
            ),
            FacilitySeed(
                facilityId = "facility-accommodation-blueharbor-1",
                name = "Blue Harbor Stay",
                address = "21 Dalmaji-gil 62beon-gil, Haeundae-gu, Busan",
                coordinate = GeoCoordinate(latitude = 35.16062, longitude = 129.16344),
                category = FacilityCategory.ACCOMMODATION,
                accessibilityTags =
                    listOf(
                        AccessibilityTag.RAMP,
                        AccessibilityTag.ELEVATOR,
                        AccessibilityTag.WIDE_ENTRY,
                    ),
                description = "Accessible lodging seed with elevator access near Dalmaji.",
            ),
            FacilitySeed(
                facilityId = "facility-healthcare-haeundae-clinic-1",
                name = "Haeundae Central Clinic",
                address = "18 Gunam-ro, Haeundae-gu, Busan",
                coordinate = GeoCoordinate(latitude = 35.16114, longitude = 129.15827),
                category = FacilityCategory.HEALTHCARE,
                accessibilityTags =
                    listOf(
                        AccessibilityTag.STEP_FREE_ENTRANCE,
                        AccessibilityTag.ELEVATOR,
                        AccessibilityTag.WIDE_ENTRY,
                    ),
                description = "Healthcare seed for accessible outpatient visits.",
            ),
            FacilitySeed(
                facilityId = "facility-welfare-senior-center-1",
                name = "Haeundae Welfare Center",
                address = "77 Jungdong 2-ro, Haeundae-gu, Busan",
                coordinate = GeoCoordinate(latitude = 35.16402, longitude = 129.16074),
                category = FacilityCategory.WELFARE,
                accessibilityTags =
                    listOf(
                        AccessibilityTag.RAMP,
                        AccessibilityTag.ELEVATOR,
                        AccessibilityTag.REST_AREA,
                    ),
                description = "Welfare center seed for category filtering and detail sheet messaging.",
            ),
            FacilitySeed(
                facilityId = "facility-public-office-dong-office-1",
                name = "Jung-dong Administrative Office",
                address = "5 Jungdong 2-ro, Haeundae-gu, Busan",
                coordinate = GeoCoordinate(latitude = 35.16378, longitude = 129.15916),
                category = FacilityCategory.PUBLIC_OFFICE,
                accessibilityTags =
                    listOf(
                        AccessibilityTag.ELEVATOR,
                        AccessibilityTag.WIDE_ENTRY,
                        AccessibilityTag.ACCESSIBLE_TOILET,
                    ),
                description = "Public office seed for civic service visits.",
            ),
            FacilitySeed(
                facilityId = "facility-charging-udong-parking-1",
                name = "U-dong Public Parking Wheelchair Charger",
                address = "541-3 U-dong, Haeundae-gu, Busan",
                coordinate = GeoCoordinate(latitude = 35.16035, longitude = 129.16061),
                category = FacilityCategory.CHARGING_STATION,
                accessibilityTags =
                    listOf(
                        AccessibilityTag.STEP_FREE_ENTRANCE,
                        AccessibilityTag.OPEN_24_HOURS,
                    ),
                description = "Wheelchair charging station mock seed for map marker rendering.",
            ),
        )

    // Braille blocks stay in a separate collection so later overlay work can treat them as another layer.
    val brailleBlocks: List<FacilitySeed> =
        listOf(
            FacilitySeed(
                facilityId = "braille-guide-haeundae-exit1-1",
                name = "Haeundae Station Exit 1 Braille Guide",
                address = "35 Jungdong 1-ro, Haeundae-gu, Busan",
                coordinate = GeoCoordinate(latitude = 35.16318, longitude = 129.15948),
                category = FacilityCategory.BRAILLE_BLOCK,
                brailleBlockType = BrailleBlockType.GUIDING_LINE,
                accessibilityTags = listOf(AccessibilityTag.STEP_FREE_ENTRANCE),
                description = "Guide line segment connecting station exit and pedestrian path.",
            ),
            FacilitySeed(
                facilityId = "braille-crosswalk-gunamro-1",
                name = "Gunam-ro Crosswalk Braille Block",
                address = "58 Gunam-ro, Haeundae-gu, Busan",
                coordinate = GeoCoordinate(latitude = 35.16184, longitude = 129.15922),
                category = FacilityCategory.BRAILLE_BLOCK,
                brailleBlockType = BrailleBlockType.CROSSWALK_APPROACH,
                accessibilityTags = listOf(AccessibilityTag.REST_AREA),
                description = "Crosswalk approach block kept separate for future overlay styling.",
            ),
            FacilitySeed(
                facilityId = "braille-warning-market-corner-1",
                name = "Haeundae Market Corner Warning Block",
                address = "19 Gunam-ro 41beon-gil, Haeundae-gu, Busan",
                coordinate = GeoCoordinate(latitude = 35.16263, longitude = 129.15844),
                category = FacilityCategory.BRAILLE_BLOCK,
                brailleBlockType = BrailleBlockType.WARNING_SURFACE,
                accessibilityTags = listOf(AccessibilityTag.STEP_FREE_ENTRANCE),
                description = "Warning surface block near a turning corner and shared sidewalk edge.",
            ),
            FacilitySeed(
                facilityId = "braille-guide-beach-square-1",
                name = "Beach Square Braille Guide Line",
                address = "257 Haeundaehaebyeon-ro, Haeundae-gu, Busan",
                coordinate = GeoCoordinate(latitude = 35.15971, longitude = 129.16134),
                category = FacilityCategory.BRAILLE_BLOCK,
                brailleBlockType = BrailleBlockType.GUIDING_LINE,
                accessibilityTags = listOf(AccessibilityTag.REST_AREA),
                description = "Guide line along the beach square rest area and plaza access path.",
            ),
        )

    val catalog: FacilitySeedCatalog =
        FacilitySeedCatalog(
            facilities = facilities,
            brailleBlocks = brailleBlocks,
        )
}
