package com.ssafy.e102.eumgil.feature.onboarding

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class PrimaryUserTypeButtonStyleTest {
    @Test
    fun `low vision button uses high contrast palette`() {
        val style = primaryUserTypeButtonStyleFor(PrimaryUserType.LOW_VISION)

        assertEquals(Color.Black, style.containerColor)
        assertEquals(Color(0xFFFFCC00), style.titleColor)
        assertEquals(Color(0xFFFFCC00), style.iconContainerColor)
        assertEquals(Color.Black, style.iconTint)
        assertEquals(Color.White, style.descriptionColor)
        assertEquals(Color(0xFFFFCC00), style.borderColor)
    }

    @Test
    fun `mobility impaired button uses onboarding light palette`() {
        val style = primaryUserTypeButtonStyleFor(PrimaryUserType.MOBILITY_IMPAIRED)

        assertEquals(Color.White, style.containerColor)
        assertEquals(Color(0xFF0047FF), style.titleColor)
        assertEquals(Color(0xFFEFF6FF), style.iconContainerColor)
        assertEquals(Color(0xFF0047FF), style.iconTint)
        assertEquals(Color(0xFF374151), style.descriptionColor)
        assertEquals(Color(0xFFE5E7EB), style.borderColor)
    }
}
