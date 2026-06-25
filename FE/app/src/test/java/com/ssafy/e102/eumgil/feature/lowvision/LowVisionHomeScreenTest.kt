package com.ssafy.e102.eumgil.feature.lowvision

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.e102.eumgil.core.location.LocationSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LowVisionHomeScreenTest {
    @Test
    fun `home screen shows the low vision brand title in the reserved header slot`() {
        assertEquals("\uBD80\uC0B0\uC774\uC74C\uAE38", LowVisionHomeLayoutDefaults.headerTitle)
        assertEquals(48.dp, LowVisionHomeLayoutDefaults.headerSlotHeight)
    }

    @Test
    fun `home screen gives voice action twice the remaining area of current location`() {
        assertEquals(2f, LowVisionHomeLayoutDefaults.voiceActionCardWeight)
        assertEquals(1f, LowVisionHomeLayoutDefaults.currentLocationCardWeight)
        assertEquals(
            2f,
            LowVisionHomeLayoutDefaults.voiceActionCardWeight /
                LowVisionHomeLayoutDefaults.currentLocationCardWeight,
        )
    }

    @Test
    fun `home screen hides status guide while voice output is not wired`() {
        assertFalse(LowVisionHomeLayoutDefaults.showsStatusGuide)
    }

    @Test
    fun `home action labels match terms guide card typography`() {
        assertEquals(44.sp, LowVisionHomeLayoutDefaults.actionLabelFontSize)
        assertEquals(FontWeight.Black, LowVisionHomeLayoutDefaults.actionLabelFontWeight)
    }

    @Test
    fun `current location display does not announce gps coordinates when address is unresolved`() {
        val display =
            lowVisionCurrentLocationDisplay(
                LocationSnapshot(
                    latitude = 35.179612,
                    longitude = 129.075634,
                    accuracyMeters = 4.8f,
                    recordedAtEpochMillis = 1_000L,
                ),
            )

        assertEquals("\uD604\uC7AC \uC704\uCE58", display.title)
        assertEquals("", display.supportingText)
        assertEquals("\uD604\uC7AC \uC704\uCE58", display.talkBackText)
    }

    @Test
    fun `current location display announces resolved address when available`() {
        val display =
            lowVisionCurrentLocationDisplay(
                snapshot =
                    LocationSnapshot(
                        latitude = 35.179612,
                        longitude = 129.075634,
                        accuracyMeters = 4.8f,
                        recordedAtEpochMillis = 1_000L,
                    ),
                address = "\uBD80\uC0B0\uAD11\uC5ED\uC2DC \uBD80\uC0B0\uC9C4\uAD6C \uC11C\uBA74\uC5ED \uC778\uADFC",
            )

        assertEquals("\uD604\uC7AC \uC704\uCE58", display.title)
        assertEquals("", display.supportingText)
        assertEquals(
            "\uD604\uC7AC \uC704\uCE58 \uBD80\uC0B0\uAD11\uC5ED\uC2DC \uBD80\uC0B0\uC9C4\uAD6C \uC11C\uBA74\uC5ED \uC778\uADFC",
            display.talkBackText,
        )
    }

    @Test
    fun `current location display announces gps loading before first fix`() {
        val display = lowVisionCurrentLocationDisplay(latitude = null, longitude = null)

        assertEquals("\uD604\uC7AC \uC704\uCE58", display.title)
        assertEquals("", display.supportingText)
        assertEquals("\uD604\uC7AC \uC704\uCE58", display.talkBackText)
    }

    @Test
    fun `current location card does not announce itself as a button`() {
        assertFalse(LowVisionHomeLayoutDefaults.currentLocationAnnouncesButtonRole)
    }
}
