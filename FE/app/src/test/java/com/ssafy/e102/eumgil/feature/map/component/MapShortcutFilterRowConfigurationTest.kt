package com.ssafy.e102.eumgil.feature.map.component

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.ssafy.e102.eumgil.feature.map.model.MapShortcutFilterKey

class MapShortcutFilterRowConfigurationTest {
    @Test
    fun `map shortcut filter row uses compact wheelchair asset for toilet chip`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/MapShortcutFilterRow.kt")
                .readText()

        assertTrue(
            "Top shortcut toilet chip should use the provided accessibility tag drawable.",
            source.contains("MapShortcutFilterKey.TOILET -> R.drawable.ic_accessibility_tag_accessible_toilet"),
        )
        assertTrue(
            "Accessibility toilet icon resource should exist for the map shortcut chip.",
            File("src/main/res/drawable/ic_accessibility_tag_accessible_toilet.png").exists(),
        )
    }

    @Test
    fun `map shortcut filter row uses provided elevator asset for top chip`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/MapShortcutFilterRow.kt")
                .readText()

        assertTrue(
            "Top shortcut elevator chip should use the provided accessibility tag drawable resource.",
            source.contains("MapShortcutFilterKey.ELEVATOR -> R.drawable.ic_accessibility_tag_elevator"),
        )
        assertTrue(
            "Provided elevator icon resource should exist.",
            File("src/main/res/drawable/ic_accessibility_tag_elevator.png").exists(),
        )
    }

    @Test
    fun `map shortcut filter row uses icon sizes tuned to asset weights`() {
        assertEquals(18, shortcutFilterIconSizeDp(MapShortcutFilterKey.ELEVATOR))
        assertEquals(20, shortcutFilterIconSizeDp(MapShortcutFilterKey.TOURIST_SPOT))
        assertEquals(20, shortcutFilterIconSizeDp(MapShortcutFilterKey.ACCOMMODATION))
        assertEquals(20, shortcutFilterIconSizeDp(MapShortcutFilterKey.WELFARE))
        assertEquals(20, shortcutFilterIconSizeDp(MapShortcutFilterKey.PUBLIC_OFFICE))
        assertEquals(19, shortcutFilterIconSizeDp(MapShortcutFilterKey.TOILET))
        assertEquals(16, shortcutFilterIconSizeDp(MapShortcutFilterKey.FOOD_CAFE))
    }

    @Test
    fun `map shortcut filter row uses scale s radius and restored elevation for chips`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/MapShortcutFilterRow.kt")
                .readText()

        assertTrue(
            "MAP top shortcut filters should use the 8dp chip radius token.",
            source.contains("shape = RoundedCornerShape(EumRadius.scaleS)"),
        )
        assertTrue(
            "MAP top shortcut filter chips should restore the previous shadow separation.",
            source.contains("shadowElevation = if (selected) 4.dp else 2.dp"),
        )
    }

    @Test
    fun `map shortcut filter row keeps enabled icons in service main blue and mutes disabled icons`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/MapShortcutFilterRow.kt")
                .readText()

        assertTrue(
            "MAP top shortcut filter icons should keep enabled chips in Primary 600.",
            source.contains("val iconTint = if (enabled) EumPrimary600 else MaterialTheme.colorScheme.onSurfaceVariant"),
        )
    }

    @Test
    fun `map shortcut filter row uses the new charging station asset`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/MapShortcutFilterRow.kt")
                .readText()

        assertTrue(
            "Top shortcut charging station chip should use the provided accessibility tag drawable resource.",
            source.contains("MapShortcutFilterKey.CHARGING_STATION -> R.drawable.ic_accessibility_tag_charging_station"),
        )
        assertTrue(
            "Provided charging station icon resource should exist.",
            File("src/main/res/drawable/ic_accessibility_tag_charging_station.png").exists(),
        )
    }

    @Test
    fun `map shortcut filter row exposes selected state semantics`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/MapShortcutFilterRow.kt")
                .readText()

        assertTrue(
            "MAP top shortcut filters should announce selected state for accessibility.",
            source.contains("R.string.a11y_option_selected"),
        )
        assertTrue(
            "MAP top shortcut filters should announce unselected state for accessibility.",
            source.contains("R.string.a11y_option_unselected"),
        )
    }

    @Test
    fun `map shortcut filter labels avoid wrapping without ellipsis`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/MapShortcutFilterRow.kt")
                .readText()
        val labelSection =
            source
                .substringAfter("text = shortcutFilterLabel(chip.key)")
                .substringBefore("        }")

        assertTrue(
            "Shortcut filter labels should stay on one line to keep chip height stable.",
            labelSection.contains("maxLines = 1") &&
                labelSection.contains("softWrap = false"),
        )
        assertFalse(
            "Shortcut filter labels should not ellipsize; the row can scroll horizontally instead.",
            labelSection.contains("TextOverflow.Ellipsis"),
        )
    }

    @Test
    fun `map shortcut filter row keeps disabled styling without lowering whole chip opacity`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/MapShortcutFilterRow.kt")
                .readText()

        assertFalse(
            "Shortcut filter chips should not lower the entire chip alpha because it leaves a ghosted shell over the map.",
            source.contains(".alpha(if (chip.isEnabled) 1f else 0.52f)"),
        )
    }

    @Test
    fun `map shortcut filter row lowers only label weight without shrinking the chip text`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/MapShortcutFilterRow.kt")
                .readText()

        assertTrue(
            "MAP top shortcut filter labels should keep the labelLarge size and lower only the font weight.",
            source.contains("style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)"),
        )
        assertFalse(
            "MAP top shortcut filter labels should not switch to labelMedium because that also reduces text size.",
            source.contains("style = MaterialTheme.typography.labelMedium"),
        )
    }

    @Test
    fun `map shortcut filter row uses dedicated tourist asset for tourist chip`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/MapShortcutFilterRow.kt")
                .readText()

        assertTrue(
            "Top shortcut tourist chip should use the dedicated tourist drawable resource.",
            source.contains("MapShortcutFilterKey.TOURIST_SPOT -> R.drawable.ic_place_tourist_spot"),
        )
        assertTrue(
            "Dedicated tourist place icon resource should exist.",
            File("src/main/res/drawable/ic_place_tourist_spot.png").exists(),
        )
    }

    @Test
    fun `map shortcut filter row uses dedicated accommodation asset for accommodation chip`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/MapShortcutFilterRow.kt")
                .readText()

        assertTrue(
            "Top shortcut accommodation chip should use the provided accessible room drawable resource.",
            source.contains("MapShortcutFilterKey.ACCOMMODATION -> R.drawable.ic_accessibility_tag_accessible_room"),
        )
        assertTrue(
            "Accessible room icon resource should exist.",
            File("src/main/res/drawable/ic_accessibility_tag_accessible_room.png").exists(),
        )
    }

    @Test
    fun `map shortcut filter row uses dedicated healthcare asset for healthcare chip`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/MapShortcutFilterRow.kt")
                .readText()

        assertTrue(
            "Top shortcut healthcare chip should use the dedicated healthcare drawable resource.",
            source.contains("MapShortcutFilterKey.HEALTHCARE -> R.drawable.ic_place_healthcare"),
        )
        assertTrue(
            "Dedicated healthcare place icon resource should exist.",
            File("src/main/res/drawable/ic_place_healthcare.xml").exists(),
        )
    }

    @Test
    fun `map shortcut filter row uses dedicated welfare asset for welfare chip`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/MapShortcutFilterRow.kt")
                .readText()

        assertTrue(
            "Top shortcut welfare chip should use the dedicated welfare drawable resource.",
            source.contains("MapShortcutFilterKey.WELFARE -> R.drawable.ic_place_welfare"),
        )
        assertTrue(
            "Dedicated welfare place icon resource should exist.",
            File("src/main/res/drawable/ic_place_welfare.png").exists(),
        )
    }

    @Test
    fun `map shortcut filter row uses dedicated public office asset for public office chip`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/MapShortcutFilterRow.kt")
                .readText()

        assertTrue(
            "Top shortcut public office chip should use the dedicated public office drawable resource.",
            source.contains("MapShortcutFilterKey.PUBLIC_OFFICE -> R.drawable.ic_place_public_office"),
        )
        assertTrue(
            "Dedicated public office place icon resource should exist.",
            File("src/main/res/drawable/ic_place_public_office.png").exists(),
        )
    }

    @Test
    fun `map shortcut filter row uses dedicated food cafe asset for food cafe chip`() {
        val source =
            File("src/main/java/com/ssafy/e102/eumgil/feature/map/component/MapShortcutFilterRow.kt")
                .readText()

        assertTrue(
            "Top shortcut food cafe chip should use the dedicated food cafe drawable resource.",
            source.contains("MapShortcutFilterKey.FOOD_CAFE -> R.drawable.ic_place_food_cafe"),
        )
        assertTrue(
            "Dedicated food cafe place icon resource should exist.",
            File("src/main/res/drawable/ic_place_food_cafe.png").exists(),
        )
    }
}
