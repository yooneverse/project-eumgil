package com.ssafy.e102.eumgil.feature.onboarding

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class MobilitySubtypeButtonStyleTest {
    @Test
    fun `selected mobility subtype button uses onboarding primary palette`() {
        val style = mobilitySubtypeButtonStyleFor(selected = true)

        assertEquals(Color.White, style.containerColor)
        assertEquals(Color(0xFFDBEAFE), style.selectedContainerColor)
        assertEquals(Color(0xFF2563EB), style.borderColor)
        assertEquals(Color(0xFF2563EB), style.titleColor)
        assertEquals(Color(0xFF374151), style.descriptionColor)
        assertEquals(Color(0xFF2563EB), style.iconTint)
        assertEquals(Color(0xFF2563EB), style.trailingTint)
    }

    @Test
    fun `unselected mobility subtype button keeps service primary title with neutral border`() {
        val style = mobilitySubtypeButtonStyleFor(selected = false)

        assertEquals(Color.White, style.containerColor)
        assertEquals(Color(0xFFDBEAFE), style.selectedContainerColor)
        assertEquals(Color(0xFFD1D5DB), style.borderColor)
        assertEquals(Color(0xFF2563EB), style.titleColor)
        assertEquals(Color(0xFF374151), style.descriptionColor)
        assertEquals(Color(0xFF2563EB), style.iconTint)
        assertEquals(Color(0xFF6B7280), style.trailingTint)
    }
}
